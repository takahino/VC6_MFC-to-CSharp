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

package io.github.takahino.cpp2csharp.mrule;

import io.github.takahino.cpp2csharp.rule.ConversionRuleLoader;
import io.github.takahino.cpp2csharp.rule.ConversionToken;
import io.github.takahino.cpp2csharp.rule.RuleLoaderConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * {@code .mrule} ファイルを読み込み、{@link MultiReplaceRule} のリストを生成するクラス。
 *
 * <h2>ファイル形式</h2>
 *
 * <pre>
 * # comment
 * scope: block
 *
 * find: BOOL ( ABSTRACT_PARAM00 )
 * replace: bool(ABSTRACT_PARAM00)
 * skip:
 * find: return ABSTRACT_PARAM01
 * replace: return ABSTRACT_PARAM00
 *
 * find: BOOL
 * replace: bool
 * </pre>
 *
 * <ul>
 * <li>{@code scope:} はルールブロック全体に適用される（省略時は NONE）</li>
 * <li>{@code skip:} は次の {@code find:} の前に置き、skipBefore=true を設定する</li>
 * <li>空行はルールブロックの区切りとなる</li>
 * <li>ルール ID は {@code sourceFile:ruleIndex} 形式で生成される</li>
 * </ul>
 */
public class MultiReplaceRuleLoader {

	private static final Logger LOGGER = LoggerFactory.getLogger(MultiReplaceRuleLoader.class);

	private static final Pattern PHASE_DIR_PATTERN = RuleLoaderConstants.PHASE_DIR_PATTERN;

	private static final String SCOPE_PREFIX = "scope:";
	private static final String FIND_PREFIX = "find:";
	private static final String REPLACE_PREFIX = "replace:";
	private static final String SKIP_KEYWORD = "skip:";

	private final ConversionRuleLoader conversionRuleLoader;

	/**
	 * デフォルトコンストラクタ。lexerFactory が null の場合、tokenize 呼び出し時に例外がスローされる。
	 */
	public MultiReplaceRuleLoader() {
		this(null);
	}

	/**
	 * LanguageLexerFactory を注入するコンストラクタ。
	 *
	 * @param lexerFactory
	 *            言語固有の Lexer を生成するファクトリ
	 */
	public MultiReplaceRuleLoader(io.github.takahino.cpp2csharp.rule.LanguageLexerFactory lexerFactory) {
		this.conversionRuleLoader = new ConversionRuleLoader(lexerFactory);
	}

	/**
	 * 指定したベースディレクトリ配下の {@code [NN]_*} サブディレクトリから {@code .mrule} ファイルをフェーズ別に読み込む。
	 *
	 * @param baseDir
	 *            ベースディレクトリ（pre/ または post/ 等）
	 * @return フェーズごとのルールリスト（各要素はそのフェーズのルール）
	 * @throws IOException
	 *             読み込みに失敗した場合
	 */
	public List<List<MultiReplaceRule>> loadFrom(Path baseDir) throws IOException {
		List<List<MultiReplaceRule>> result = new ArrayList<>();

		if (!Files.isDirectory(baseDir)) {
			LOGGER.warn("ベースディレクトリが存在しません: {}", baseDir);
			return result;
		}

		List<Path> phaseDirs = new ArrayList<>();
		try (Stream<Path> entries = Files.list(baseDir)) {
			entries.filter(Files::isDirectory)
					.filter(p -> PHASE_DIR_PATTERN.matcher(p.getFileName().toString()).matches())
					.forEach(phaseDirs::add);
		}

		if (phaseDirs.isEmpty()) {
			// フェーズディレクトリなし → 直下の .mrule を単一フェーズとして読み込む
			List<MultiReplaceRule> flatRules = new ArrayList<>();
			try (Stream<Path> files = Files.list(baseDir)) {
				files.filter(p -> !Files.isDirectory(p) && p.toString().endsWith(".mrule")).sorted()
						.forEach(p -> flatRules.addAll(loadRulesFromFile(p)));
			}
			if (!flatRules.isEmpty()) {
				result.add(flatRules);
			}
		} else {
			phaseDirs.sort(Comparator.comparing(p -> {
				Matcher m = PHASE_DIR_PATTERN.matcher(p.getFileName().toString());
				return m.matches() ? Integer.parseInt(m.group(1)) : 999;
			}));
			for (Path phaseDir : phaseDirs) {
				List<MultiReplaceRule> phaseRules = new ArrayList<>();
				try (Stream<Path> files = Files.list(phaseDir)) {
					files.filter(p -> p.toString().endsWith(".mrule")).sorted()
							.forEach(p -> phaseRules.addAll(loadRulesFromFile(p)));
				}
				if (!phaseRules.isEmpty()) {
					result.add(phaseRules);
					LOGGER.debug("mrule フェーズ {}: {} ルール読み込み", phaseDir.getFileName(), phaseRules.size());
				}
			}
		}

		return result;
	}

	private List<MultiReplaceRule> loadRulesFromFile(Path filePath) {
		try {
			return loadFromFile(filePath);
		} catch (IOException e) {
			LOGGER.warn("mrule ファイルの読み込みに失敗: {} - {}", filePath, e.getMessage());
			return List.of();
		}
	}

	/**
	 * 単一の {@code .mrule} ファイルを読み込み、{@link MultiReplaceRule} のリストを返す。
	 *
	 * @param filePath
	 *            .mrule ファイルのパス
	 * @return 読み込んだルールのリスト
	 * @throws IOException
	 *             ファイル読み込みに失敗した場合
	 */
	public List<MultiReplaceRule> loadFromFile(Path filePath) throws IOException {
		List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
		return parseLines(lines, filePath.getFileName().toString());
	}

	/**
	 * テキストコンテンツから直接ルールをパースする（テスト用）。
	 *
	 * @param content
	 *            ルール定義のテキスト
	 * @param sourceName
	 *            ソース名（デバッグ用）
	 * @return 読み込んだルールのリスト
	 */
	public List<MultiReplaceRule> loadFromString(String content, String sourceName) {
		String[] lines = content.split("\r?\n");
		return parseLines(List.of(lines), sourceName);
	}

	private List<MultiReplaceRule> parseLines(List<String> lines, String sourceName) {
		List<MultiReplaceRule> rules = new ArrayList<>();
		int ruleIndex = 0;

		// State machine: parse rule blocks separated by blank lines
		// A "block" starts when we see the first find: after the previous block ended
		MRuleScope currentScope = MRuleScope.NONE;
		List<MRuleFindSpec> currentSpecs = new ArrayList<>();
		String currentFind = null;
		String currentReplace = null;
		boolean nextSkipBefore = false;
		boolean inBlock = false;

		for (String rawLine : lines) {
			String line = rawLine.trim();

			// Skip comment lines
			if (line.startsWith("#")) {
				continue;
			}

			// Blank line: if we are in a block, flush the current spec and end the block
			if (line.isEmpty()) {
				if (inBlock) {
					flushSpec(currentSpecs, currentFind, currentReplace, nextSkipBefore, sourceName);
					currentFind = null;
					currentReplace = null;
					nextSkipBefore = false;

					if (!currentSpecs.isEmpty()) {
						String ruleId = sourceName + ":" + ruleIndex;
						rules.add(new MultiReplaceRule(ruleId, sourceName, currentScope, currentSpecs));
						ruleIndex++;
					}
					currentScope = MRuleScope.NONE;
					currentSpecs = new ArrayList<>();
					inBlock = false;
				}
				continue;
			}

			if (line.startsWith(SCOPE_PREFIX)) {
				String scopeStr = line.substring(SCOPE_PREFIX.length()).trim().toUpperCase();
				try {
					currentScope = MRuleScope.valueOf(scopeStr);
				} catch (IllegalArgumentException e) {
					LOGGER.warn("不明な scope 値: {} in {}", scopeStr, sourceName);
					currentScope = MRuleScope.NONE;
				}
				continue;
			}

			if (line.equals(SKIP_KEYWORD) || line.startsWith(SKIP_KEYWORD + " ")) {
				// skip: marks the NEXT find as skipBefore=true
				// First flush any pending spec (shouldn't happen normally, but be safe)
				flushSpec(currentSpecs, currentFind, currentReplace, nextSkipBefore, sourceName);
				currentFind = null;
				currentReplace = null;
				nextSkipBefore = true;
				continue;
			}

			if (line.startsWith(FIND_PREFIX)) {
				// Flush previous find/replace pair if any.
				// nextSkipBefore is NOT reset here: skip: propagates to all subsequent find: in
				// the block.
				if (currentFind != null) {
					flushSpec(currentSpecs, currentFind, currentReplace, nextSkipBefore, sourceName);
				}
				currentFind = line.substring(FIND_PREFIX.length()).trim();
				currentReplace = null;
				inBlock = true;
				continue;
			}

			if (line.startsWith(REPLACE_PREFIX)) {
				currentReplace = line.substring(REPLACE_PREFIX.length()).trim();
				continue;
			}

			LOGGER.warn("不明な行形式: {} : {}", sourceName, line);
		}

		// Flush last block
		if (inBlock) {
			flushSpec(currentSpecs, currentFind, currentReplace, nextSkipBefore, sourceName);
			if (!currentSpecs.isEmpty()) {
				String ruleId = sourceName + ":" + ruleIndex;
				rules.add(new MultiReplaceRule(ruleId, sourceName, currentScope, currentSpecs));
			}
		}

		LOGGER.debug("mrule 読み込み完了: {} -> {} ルール", sourceName, rules.size());
		return rules;
	}

	private void flushSpec(List<MRuleFindSpec> specs, String find, String replace, boolean skipBefore,
			String sourceName) {
		if (find == null) {
			return;
		}
		if (replace == null) {
			LOGGER.warn("find: に対応する replace: がありません: {} find={}", sourceName, find);
			return;
		}
		try {
			List<ConversionToken> pattern = conversionRuleLoader.tokenizePattern(find);
			specs.add(new MRuleFindSpec(pattern, replace, skipBefore));
		} catch (IllegalArgumentException e) {
			LOGGER.warn("find パターンのトークン化に失敗: {} pattern={} error={}", sourceName, find, e.getMessage());
		}
	}
}
