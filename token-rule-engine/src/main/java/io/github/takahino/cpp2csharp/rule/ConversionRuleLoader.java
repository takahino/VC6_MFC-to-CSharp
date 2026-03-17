// === LICENSE_START ===
// BSD 3-Clause License
//
// Copyright (c) 2026, Takahiro Hino
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice, this
//    list of conditions and the following disclaimer.
//
// 2. Redistributions in binary form must reproduce the above copyright notice,
//    this list of conditions and the following disclaimer in the documentation
//    and/or other materials provided with the distribution.
//
// 3. Neither the name of the copyright holder nor the names of its
//    contributors may be used to endorse or promote products derived from
//    this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
// FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
// DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
// SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
// CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
// OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
// === LICENSE_END ===

package io.github.takahino.cpp2csharp.rule;

import io.github.takahino.cpp2csharp.comby.CombyRule;
import io.github.takahino.cpp2csharp.comby.CombyRuleLoader;
import io.github.takahino.cpp2csharp.dynamic.DynamicRuleLoader;
import io.github.takahino.cpp2csharp.dynamic.DynamicRuleSpec;
import io.github.takahino.cpp2csharp.mrule.MultiReplaceRule;
import io.github.takahino.cpp2csharp.mrule.MultiReplaceRuleLoader;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 変換定義ファイル (.rule) を読み込み、{@link ConversionRule} のリストを生成するクラス。
 *
 * <h2>ファイル形式</h2>
 *
 * <pre>
 * # コメント行は '#' で始まる
 * from: this . AfxMessageBox ( ABSTRACT_PARAM00 , MB_OK | MB_ICONERROR ) ;
 * to: MessageBox.Show(ABSTRACT_PARAM00, "", MessageBoxButtons.OK, MessageBoxIcon.Error);
 * test: AfxMessageBox("Hello", MB_OK | MB_ICONERROR);
 * assrt: MessageBox.Show("Hello", "", MessageBoxButtons.OK, MessageBoxIcon.Error);
 * </pre>
 *
 * <ul>
 * <li>from: と to: はペアで記述する</li>
 * <li>from は ANTLR CPP14 レキサーでトークン化（スペース不要）</li>
 * <li>to はテンプレート文字列としてそのまま使用する</li>
 * <li>test: と assrt: はオプションで複数記載可能（1対1でペアになる）</li>
 * <li>空行・コメント行は無視する</li>
 * </ul>
 */
public class ConversionRuleLoader {

	private static final Logger LOGGER = LoggerFactory.getLogger(ConversionRuleLoader.class);

	private final LanguageLexerFactory lexerFactory;

	/**
	 * デフォルトコンストラクタ。lexerFactory が null の場合、tokenize() で例外がスローされる。 テストや後方互換のために残す。
	 */
	public ConversionRuleLoader() {
		this(null);
	}

	/**
	 * コンストラクタ。LanguageLexerFactory を注入する。
	 *
	 * @param lexerFactory
	 *            言語固有の Lexer を生成するファクトリ（null 不可の運用を推奨）
	 */
	public ConversionRuleLoader(LanguageLexerFactory lexerFactory) {
		this.lexerFactory = lexerFactory;
	}

	private static final String FROM_PREFIX = "from:";
	private static final String TO_PREFIX = "to:";
	private static final String TEST_PREFIX = "test:";
	private static final String ASSRT_PREFIX = "assrt:";

	private static final Pattern PHASE_DIR_PATTERN = RuleLoaderConstants.PHASE_DIR_PATTERN;

	/**
	 * 指定したファイルパスから変換ルールを読み込む。
	 *
	 * @param filePath
	 *            .rule ファイルのパス
	 * @return 読み込んだ変換ルールのリスト
	 * @throws IOException
	 *             ファイル読み込みに失敗した場合
	 */
	public List<ConversionRule> loadFromFile(Path filePath) throws IOException {
		Objects.requireNonNull(filePath, "filePath が null です");
		List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
		return parseLines(lines, filePath.getFileName().toString());
	}

	/**
	 * クラスパス上のリソースから変換ルールを読み込む。
	 *
	 * @param resourcePath
	 *            クラスパス上のリソースパス (例: "rules/AfxMessageBox.rule")
	 * @return 読み込んだ変換ルールのリスト
	 * @throws IOException
	 *             リソース読み込みに失敗した場合
	 */
	public List<ConversionRule> loadFromResource(String resourcePath) throws IOException {
		Objects.requireNonNull(resourcePath, "resourcePath が null です");
		try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
			if (is == null) {
				throw new IOException("リソースが見つかりません: " + resourcePath);
			}
			String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
			String[] lines = content.split("\r?\n");
			String fileName = Path.of(resourcePath).getFileName().toString();
			return parseLines(Arrays.asList(lines), fileName);
		}
	}

	/**
	 * ルールファイルパスとルールリストのペア。
	 */
	public static final class RuleFileEntry {
		private final String relativePath;
		private final List<ConversionRule> rules;

		public RuleFileEntry(String relativePath, List<ConversionRule> rules) {
			this.relativePath = relativePath;
			this.rules = new ArrayList<>(rules);
		}

		public String getRelativePath() {
			return relativePath;
		}

		public List<ConversionRule> getRules() {
			return rules;
		}

		public String getFileName() {
			return Path.of(relativePath).getFileName().toString();
		}
	}

	/**
	 * クラスパス上の rules/ ディレクトリ配下の全 .rule ファイルをパス付きで読み込む。 テスト結果出力用に、rules からの相対パスを保持する。
	 *
	 * @return ルールファイルエントリのリスト（relativePath は "phaseDir/file.rule" 形式）
	 * @throws IOException
	 *             読み込みに失敗した場合
	 */
	public List<RuleFileEntry> loadAllRuleFilesWithPaths() throws IOException {
		Path dirPath = resolveRulesDirectory();
		if (dirPath == null) {
			return new ArrayList<>();
		}
		List<RuleFileEntry> entries = new ArrayList<>();

		List<Path> phaseDirs = new ArrayList<>();
		try (Stream<Path> entriesStream = Files.list(dirPath)) {
			entriesStream.filter(Files::isDirectory)
					.filter(p -> PHASE_DIR_PATTERN.matcher(p.getFileName().toString()).matches())
					.forEach(phaseDirs::add);
		}

		if (phaseDirs.isEmpty()) {
			try (Stream<Path> files = Files.list(dirPath)) {
				files.filter(p -> !Files.isDirectory(p) && p.toString().endsWith(".rule")).sorted().map(p -> {
					var r = loadRulesFromFile(p);
					return r.isEmpty() ? null : new RuleFileEntry(p.getFileName().toString(), r);
				}).filter(Objects::nonNull).forEach(entries::add);
			}
		} else {
			phaseDirs.sort(Comparator.comparing(p -> {
				Matcher m = PHASE_DIR_PATTERN.matcher(p.getFileName().toString());
				return m.matches() ? Integer.parseInt(m.group(1)) : 999;
			}));
			for (Path phaseDir : phaseDirs) {
				String phaseDirName = phaseDir.getFileName().toString();
				try (Stream<Path> files = Files.list(phaseDir)) {
					files.filter(p -> p.toString().endsWith(".rule")).sorted().map(p -> {
						var r = loadRulesFromFile(p);
						return r.isEmpty() ? null : new RuleFileEntry(phaseDirName + "/" + p.getFileName(), r);
					}).filter(Objects::nonNull).forEach(entries::add);
				}
			}
		}
		return entries;
	}

	/**
	 * rules ディレクトリの Path を解決する。 クラスパス上のリソース、または src/main/resources/rules /
	 * src/test/resources/rules を試す。
	 */
	private Path resolveRulesDirectory() throws IOException {
		for (String resourcePath : List.of("rules")) {
			URL rulesDir = getClass().getClassLoader().getResource(resourcePath);
			if (rulesDir != null) {
				try {
					Path path = Path.of(rulesDir.toURI());
					if (Files.isDirectory(path)) {
						return path;
					}
				} catch (URISyntaxException | IllegalArgumentException e) {
					LOGGER.debug("rules リソースの Path 解決に失敗: {}", e.getMessage());
				}
			}
		}
		Path projectRoot = Path.of(System.getProperty("user.dir", "."));
		for (String subPath : List.of("src/main/resources/rules", "target/classes/rules", "src/test/resources/rules",
				"target/test-classes/rules")) {
			Path candidate = projectRoot.resolve(subPath);
			if (Files.isDirectory(candidate)) {
				return candidate;
			}
		}
		LOGGER.warn("rules ディレクトリが見つかりません (user.dir={})", projectRoot);
		return null;
	}

	/**
	 * クラスパス上の rules/ ディレクトリ配下の全 .rule ファイルを読み込む。 フェーズディレクトリ（[01]_名前, [02]_名前
	 * 等）がある場合はそれらをフラット化して返す。 フェーズディレクトリが無い場合は rules/ 直下の .rule を読み込む（後方互換）。
	 *
	 * @return 全変換ルールのリスト
	 * @throws IOException
	 *             読み込みに失敗した場合
	 */
	public List<ConversionRule> loadAllFromResources() throws IOException {
		List<List<ConversionRule>> phases = loadAllFromResourcesByPhase();
		List<ConversionRule> allRules = new ArrayList<>();
		for (List<ConversionRule> phaseRules : phases) {
			allRules.addAll(phaseRules);
		}
		return allRules;
	}

	/**
	 * クラスパス上の rules/ ディレクトリをフェーズ別に読み込む。 [01]_ブロックコメント, [02]_標準置き換え
	 * のようなサブディレクトリの数字プレフィックス順に適用する。
	 *
	 * @return フェーズごとのルールリスト（各要素はそのフェーズのルール、順序は適用順）
	 * @throws IOException
	 *             読み込みに失敗した場合
	 */
	public List<List<ConversionRule>> loadAllFromResourcesByPhase() throws IOException {
		List<List<ConversionRule>> phases = new ArrayList<>();
		URL rulesDir = getClass().getClassLoader().getResource("rules");
		if (rulesDir == null) {
			LOGGER.warn("rules ディレクトリがクラスパス上に見つかりません");
			return phases;
		}
		try {
			URI uri = rulesDir.toURI();
			Path dirPath = Path.of(uri);

			List<Path> phaseDirs = new ArrayList<>();
			try (Stream<Path> entries = Files.list(dirPath)) {
				entries.filter(Files::isDirectory)
						.filter(p -> PHASE_DIR_PATTERN.matcher(p.getFileName().toString()).matches())
						.forEach(phaseDirs::add);
			}

			if (phaseDirs.isEmpty()) {
				// フェーズディレクトリが無い場合は直下の .rule を単一フェーズとして読み込む（後方互換）
				List<ConversionRule> flatRules;
				try (Stream<Path> files = Files.list(dirPath)) {
					flatRules = files.filter(p -> !Files.isDirectory(p) && p.toString().endsWith(".rule")).sorted()
							.flatMap(p -> loadRulesFromFile(p).stream()).toList();
				}
				if (!flatRules.isEmpty()) {
					phases.add(flatRules);
				}
			} else {
				phaseDirs.sort(Comparator.comparing(p -> {
					Matcher m = PHASE_DIR_PATTERN.matcher(p.getFileName().toString());
					return m.matches() ? Integer.parseInt(m.group(1)) : 999;
				}));
				for (Path phaseDir : phaseDirs) {
					List<ConversionRule> phaseRules;
					try (Stream<Path> files = Files.list(phaseDir)) {
						phaseRules = files.filter(p -> p.toString().endsWith(".rule")).sorted()
								.flatMap(p -> loadRulesFromFile(p).stream()).toList();
					}
					if (!phaseRules.isEmpty()) {
						phases.add(phaseRules);
						LOGGER.debug("フェーズ {}: {} ルール読み込み", phaseDir.getFileName(), phaseRules.size());
					}
				}
			}
		} catch (URISyntaxException e) {
			throw new IOException("rules ディレクトリ URI が不正です", e);
		}
		return phases;
	}

	private List<ConversionRule> loadRulesFromFile(Path filePath) {
		try {
			return loadFromFile(filePath);
		} catch (IOException e) {
			LOGGER.warn("ルールファイルの読み込みに失敗: {} - {}", filePath, e.getMessage());
			return List.of();
		}
	}

	/**
	 * テキストコンテンツから直接変換ルールをパースする (テスト用)。
	 *
	 * @param content
	 *            ルール定義のテキスト
	 * @param sourceName
	 *            ソース名 (デバッグ用)
	 * @return 変換ルールのリスト
	 */
	public List<ConversionRule> loadFromString(String content, String sourceName) {
		String[] lines = content.split("\r?\n");
		return parseLines(Arrays.asList(lines), sourceName);
	}

	/**
	 * 行リストをパースして変換ルールリストを生成する。
	 *
	 * @param lines
	 *            ファイル行リスト
	 * @param sourceName
	 *            ソース名
	 * @return 変換ルールのリスト
	 */
	private List<ConversionRule> parseLines(List<String> lines, String sourceName) {
		List<ConversionRule> rules = new ArrayList<>();
		String currentFrom = null;
		String currentTo = null;
		List<String> currentTests = new ArrayList<>();
		List<String> currentAssrts = new ArrayList<>();
		int lineNum = 0;
		int i = 0;

		while (i < lines.size()) {
			String rawLine = lines.get(i);
			lineNum++;
			i++;
			String line = rawLine.trim();

			// コメントと空行をスキップ
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}

			if (line.startsWith(FROM_PREFIX)) {
				flushPendingRule(rules, sourceName, currentFrom, currentTo, currentTests, currentAssrts);
				currentFrom = line.substring(FROM_PREFIX.length()).trim();
				currentTo = null;
				currentTests.clear();
				currentAssrts.clear();

			} else if (line.startsWith(TO_PREFIX)) {
				if (currentFrom == null) {
					LOGGER.warn("to: に対応する from: がありません: {} 行 {}", sourceName, lineNum);
					continue;
				}
				currentTo = line.substring(TO_PREFIX.length()).trim();

			} else if (line.startsWith(TEST_PREFIX)) {
				StringBuilder sb = new StringBuilder(line.substring(TEST_PREFIX.length()).trim());
				while (i < lines.size()) {
					String next = lines.get(i);
					if (next.trim().isEmpty() || next.trim().startsWith("#") || next.trim().startsWith(FROM_PREFIX)
							|| next.trim().startsWith(TO_PREFIX) || next.trim().startsWith(TEST_PREFIX)
							|| next.trim().startsWith(ASSRT_PREFIX)) {
						break;
					}
					sb.append("\n").append(next);
					i++;
					lineNum++;
				}
				currentTests.add(sb.toString().trim());

			} else if (line.startsWith(ASSRT_PREFIX)) {
				StringBuilder sb = new StringBuilder(line.substring(ASSRT_PREFIX.length()).trim());
				while (i < lines.size()) {
					String next = lines.get(i);
					if (next.trim().isEmpty() || next.trim().startsWith("#") || next.trim().startsWith(FROM_PREFIX)
							|| next.trim().startsWith(TO_PREFIX) || next.trim().startsWith(TEST_PREFIX)
							|| next.trim().startsWith(ASSRT_PREFIX)) {
						break;
					}
					sb.append("\n").append(next);
					i++;
					lineNum++;
				}
				currentAssrts.add(sb.toString().trim());

			} else {
				LOGGER.warn("不明な行形式: {} 行 {}: {}", sourceName, lineNum, line);
			}
		}

		flushPendingRule(rules, sourceName, currentFrom, currentTo, currentTests, currentAssrts);

		if (currentFrom != null && currentTo == null) {
			LOGGER.warn("ファイル末尾に未完了の from: があります: {}", sourceName);
		}

		return rules;
	}

	private void flushPendingRule(List<ConversionRule> rules, String sourceName, String currentFrom, String currentTo,
			List<String> currentTests, List<String> currentAssrts) {
		if (currentFrom == null || currentTo == null) {
			return;
		}
		List<RuleTestCase> testCases = new ArrayList<>();
		int pairCount = Math.min(currentTests.size(), currentAssrts.size());
		for (int j = 0; j < pairCount; j++) {
			testCases.add(new RuleTestCase(currentTests.get(j), currentAssrts.get(j)));
		}
		if (currentTests.size() != currentAssrts.size()) {
			LOGGER.warn("test: と assrt: の数が一致しません (test={}, assrt={}): {}", currentTests.size(), currentAssrts.size(),
					sourceName);
		}
		List<ConversionToken> fromTokens = tokenize(currentFrom);
		rules.add(new ConversionRule(sourceName, fromTokens, currentTo, testCases));
		LOGGER.debug("ルール読み込み完了: {} → {} (test={})", currentFrom, currentTo, testCases.size());
	}

	/**
	 * from パターン文字列を ANTLR CPP14 レキサーでトークン化する（公開版）。
	 *
	 * <p>
	 * {@link MultiReplaceRuleLoader} 等の外部クラスが find: パターンをトークン化するために使用する。
	 * </p>
	 *
	 * @param fromPattern
	 *            from パターン文字列
	 * @return トークンのリスト
	 * @throws IllegalArgumentException
	 *             字句解析エラーまたは空パターンの場合
	 */
	public List<ConversionToken> tokenizePattern(String fromPattern) {
		return tokenize(fromPattern);
	}

	/**
	 * from パターン文字列を ANTLR CPP14 レキサーでトークン化する。
	 *
	 * <p>
	 * スペース区切りは不要。C++ 字句規則に従ってトークン境界を判定する。
	 * </p>
	 *
	 * @param fromPattern
	 *            from パターン文字列
	 * @return トークンのリスト
	 * @throws IllegalArgumentException
	 *             字句解析エラーまたは空パターンの場合
	 */
	private List<ConversionToken> tokenize(String fromPattern) {
		if (fromPattern == null || fromPattern.isBlank()) {
			throw new IllegalArgumentException("from パターンが空です");
		}

		if (lexerFactory == null) {
			throw new IllegalStateException(
					"LanguageLexerFactory が設定されていません。ConversionRuleLoader(LanguageLexerFactory) コンストラクタを使用してください。");
		}
		Lexer lexer = lexerFactory.createLexer(CharStreams.fromString(fromPattern));
		CollectingErrorListener errorListener = new CollectingErrorListener();
		lexer.removeErrorListeners();
		lexer.addErrorListener(errorListener);

		CommonTokenStream tokenStream = new CommonTokenStream(lexer);
		tokenStream.fill();

		if (errorListener.hasErrors()) {
			throw new IllegalArgumentException(
					"from パターンの字句解析に失敗しました: " + fromPattern + " - " + errorListener.getErrors());
		}

		List<ConversionToken> result = new ArrayList<>();
		for (Token t : tokenStream.getTokens()) {
			if (t.getChannel() != Token.DEFAULT_CHANNEL) {
				continue;
			}
			if (t.getType() == Token.EOF) {
				break;
			}
			String text = t.getText();
			if (text != null && !text.isBlank()) {
				result.add(ConversionToken.of(text));
			}
		}

		if (result.isEmpty()) {
			throw new IllegalArgumentException("from パターンに有効なトークンがありません: " + fromPattern);
		}

		return result;
	}

	// =========================================================================
	// 3パス構成サポート
	// =========================================================================

	/**
	 * 3パス（pre/main/post）のルールセットを表すレコード。
	 *
	 * @param prePhases
	 *            pre フェーズのルールリスト（フェーズ順）
	 * @param mainPhases
	 *            main フェーズの変換ルールリスト（フェーズ順）
	 * @param postPhases
	 *            post フェーズのルールリスト（フェーズ順）
	 * @param combyPhases
	 *            comby フェーズのルールリスト（フェーズ順）
	 * @param dynamicSpecs
	 *            動的ルール仕様リスト（トークンストリームから値を収集して ConversionRule を生成）
	 */
	public record ThreePassRuleSet(List<List<MultiReplaceRule>> prePhases, List<List<ConversionRule>> mainPhases,
			List<List<MultiReplaceRule>> postPhases, List<List<CombyRule>> combyPhases,
			List<DynamicRuleSpec> dynamicSpecs) {
	}

	/**
	 * 3パス構成のルールセットを読み込む。
	 *
	 * <p>
	 * rules/main/ ディレクトリが存在する場合はそれを main フェーズとして読み込み、 rules/pre/ と rules/post/
	 * が存在する場合はそれぞれ pre/post フェーズとして読み込む。
	 * </p>
	 *
	 * <p>
	 * 3パス構成ディレクトリが存在しない場合は、既存の rules/ 構造をそのまま使用する （後方互換のため pre/post は空のリストとなる）。
	 * </p>
	 *
	 * @return 3パスルールセット
	 * @throws IOException
	 *             読み込みに失敗した場合
	 */
	public ThreePassRuleSet loadThreePassRules() throws IOException {
		Path rulesDir = resolveRulesDirectory();
		if (rulesDir == null) {
			return new ThreePassRuleSet(List.of(), loadAllFromResourcesByPhase(), List.of(), List.of(), List.of());
		}
		return loadThreePassRulesFrom(rulesDir);
	}

	/**
	 * 指定したルールディレクトリから 3パスルールセットを読み込む。
	 *
	 * @param rulesDir
	 *            ルールディレクトリ（main/, pre/, post/, comby/ を含む）
	 * @return 3パスルールセット
	 * @throws IOException
	 *             読み込みに失敗した場合
	 */
	public ThreePassRuleSet loadThreePassRulesFrom(Path rulesDir) throws IOException {
		Path mainDir = rulesDir.resolve("main");
		if (!Files.isDirectory(mainDir)) {
			// Fallback: no three-pass structure. Use existing rules layout ([NN]_* dirs at
			// rules root).
			// Still load comby/ and dynamic/ if present at rules root.
			CombyRuleLoader combyLoader = new CombyRuleLoader();
			Path combyDir = rulesDir.resolve("comby");
			List<List<CombyRule>> combyPhases = Files.isDirectory(combyDir)
					? combyLoader.loadPhasesFrom(combyDir)
					: List.of();

			DynamicRuleLoader dynamicLoader = new DynamicRuleLoader(lexerFactory);
			Path dynamicDir = rulesDir.resolve("dynamic");
			List<DynamicRuleSpec> dynamicSpecs = Files.isDirectory(dynamicDir)
					? dynamicLoader.loadFrom(dynamicDir)
					: List.of();

			MultiReplaceRuleLoader mruleLoader = new MultiReplaceRuleLoader(lexerFactory);
			Path preDir = rulesDir.resolve("pre");
			List<List<MultiReplaceRule>> prePhases = Files.isDirectory(preDir)
					? mruleLoader.loadFrom(preDir)
					: List.of();
			Path postDir = rulesDir.resolve("post");
			List<List<MultiReplaceRule>> postPhases = Files.isDirectory(postDir)
					? mruleLoader.loadFrom(postDir)
					: List.of();

			List<List<ConversionRule>> mainPhases = loadAllFromDirectoryByPhase(rulesDir);
			LOGGER.info("フラット構成: pre={} フェーズ, main={} フェーズ, post={} フェーズ, comby={} フェーズ, dynamic={} スペック",
					prePhases.size(), mainPhases.size(), postPhases.size(), combyPhases.size(), dynamicSpecs.size());
			return new ThreePassRuleSet(prePhases, mainPhases, postPhases, combyPhases, dynamicSpecs);
		}

		MultiReplaceRuleLoader mruleLoader = new MultiReplaceRuleLoader(lexerFactory);

		Path preDir = rulesDir.resolve("pre");
		List<List<MultiReplaceRule>> prePhases = Files.isDirectory(preDir) ? mruleLoader.loadFrom(preDir) : List.of();

		List<List<ConversionRule>> mainPhases = loadAllFromDirectoryByPhase(mainDir);

		Path postDir = rulesDir.resolve("post");
		List<List<MultiReplaceRule>> postPhases = Files.isDirectory(postDir)
				? mruleLoader.loadFrom(postDir)
				: List.of();

		CombyRuleLoader combyLoader = new CombyRuleLoader();
		Path combyDir = rulesDir.resolve("comby");
		List<List<CombyRule>> combyPhases = Files.isDirectory(combyDir)
				? combyLoader.loadPhasesFrom(combyDir)
				: List.of();

		DynamicRuleLoader dynamicLoader = new DynamicRuleLoader(lexerFactory);
		Path dynamicDir = rulesDir.resolve("dynamic");
		List<DynamicRuleSpec> dynamicSpecs = Files.isDirectory(dynamicDir)
				? dynamicLoader.loadFrom(dynamicDir)
				: List.of();

		LOGGER.info("3パス構成: pre={} フェーズ, main={} フェーズ, post={} フェーズ, comby={} フェーズ, dynamic={} スペック", prePhases.size(),
				mainPhases.size(), postPhases.size(), combyPhases.size(), dynamicSpecs.size());

		return new ThreePassRuleSet(prePhases, mainPhases, postPhases, combyPhases, dynamicSpecs);
	}

	/**
	 * 指定したディレクトリをフェーズ別に読み込む。 {@code [NN]_*} サブディレクトリの数字プレフィックス順に適用する。
	 *
	 * @param dirPath
	 *            ルールが格納されたディレクトリ
	 * @return フェーズごとのルールリスト
	 * @throws IOException
	 *             読み込みに失敗した場合
	 */
	private List<List<ConversionRule>> loadAllFromDirectoryByPhase(Path dirPath) throws IOException {
		List<List<ConversionRule>> phases = new ArrayList<>();

		List<Path> phaseDirs = new ArrayList<>();
		try (Stream<Path> entries = Files.list(dirPath)) {
			entries.filter(Files::isDirectory)
					.filter(p -> PHASE_DIR_PATTERN.matcher(p.getFileName().toString()).matches())
					.forEach(phaseDirs::add);
		}

		if (phaseDirs.isEmpty()) {
			// フェーズディレクトリが無い場合は直下の .rule を単一フェーズとして読み込む
			List<ConversionRule> flatRules;
			try (Stream<Path> files = Files.list(dirPath)) {
				flatRules = files.filter(p -> !Files.isDirectory(p) && p.toString().endsWith(".rule")).sorted()
						.flatMap(p -> loadRulesFromFile(p).stream()).toList();
			}
			if (!flatRules.isEmpty()) {
				phases.add(flatRules);
			}
		} else {
			phaseDirs.sort(Comparator.comparing(p -> {
				Matcher m = PHASE_DIR_PATTERN.matcher(p.getFileName().toString());
				return m.matches() ? Integer.parseInt(m.group(1)) : 999;
			}));
			for (Path phaseDir : phaseDirs) {
				List<ConversionRule> phaseRules;
				try (Stream<Path> files = Files.list(phaseDir)) {
					phaseRules = files.filter(p -> p.toString().endsWith(".rule")).sorted()
							.flatMap(p -> loadRulesFromFile(p).stream()).toList();
				}
				if (!phaseRules.isEmpty()) {
					phases.add(phaseRules);
					LOGGER.debug("フェーズ {}: {} ルール読み込み", phaseDir.getFileName(), phaseRules.size());
				}
			}
		}

		return phases;
	}
}
