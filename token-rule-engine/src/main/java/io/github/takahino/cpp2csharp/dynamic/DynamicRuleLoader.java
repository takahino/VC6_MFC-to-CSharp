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

package io.github.takahino.cpp2csharp.dynamic;

import io.github.takahino.cpp2csharp.rule.ConversionRuleLoader;
import io.github.takahino.cpp2csharp.rule.ConversionToken;
import io.github.takahino.cpp2csharp.rule.LanguageLexerFactory;
import io.github.takahino.cpp2csharp.rule.RuleLoaderConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 動的ルール定義ファイル (.drule) を読み込み、{@link DynamicRuleSpec} のリストを生成するクラス。
 *
 * <h2>ファイル形式 (.drule)</h2>
 *
 * <pre>
 * # コメント行
 * collect: ABSTRACT_PARAM00 ::
 *
 * from: void COLLECTED :: ABSTRACT_PARAM00 ( ABSTRACT_PARAM01 )
 * to: private void ABSTRACT_PARAM00(ABSTRACT_PARAM01)
 *
 * from: bool COLLECTED :: ABSTRACT_PARAM00 ( ABSTRACT_PARAM01 )
 * to: private bool ABSTRACT_PARAM00(ABSTRACT_PARAM01)
 * </pre>
 *
 * <ul>
 * <li>collect: は1ファイルに1つ。ABSTRACT_PARAM00 が収集対象の値を捕捉する。</li>
 * <li>from:/to: はペアで複数記述可能。COLLECTED プレースホルダが収集値に置換される。</li>
 * <li>空行・コメント行は無視する。</li>
 * </ul>
 */
public class DynamicRuleLoader {

	private static final Logger LOGGER = LoggerFactory.getLogger(DynamicRuleLoader.class);

	private static final String COLLECT_PREFIX = "collect:";
	private static final String FROM_PREFIX = "from:";
	private static final String TO_PREFIX = "to:";

	private static final Pattern PHASE_DIR_PATTERN = RuleLoaderConstants.PHASE_DIR_PATTERN;

	private final ConversionRuleLoader ruleLoader;

	public DynamicRuleLoader() {
		this(null);
	}

	public DynamicRuleLoader(LanguageLexerFactory lexerFactory) {
		this.ruleLoader = new ConversionRuleLoader(lexerFactory);
	}

	/**
	 * 指定したディレクトリから .drule ファイルを再帰的に読み込む。 {@code [NN]_*} サブディレクトリがある場合は昇順に処理する。
	 *
	 * @param dynamicDir
	 *            動的ルールディレクトリ
	 * @return 読み込んだ DynamicRuleSpec のリスト
	 * @throws IOException
	 *             読み込みに失敗した場合
	 */
	public List<DynamicRuleSpec> loadFrom(Path dynamicDir) throws IOException {
		List<DynamicRuleSpec> specs = new ArrayList<>();
		if (!Files.isDirectory(dynamicDir)) {
			return specs;
		}

		List<Path> phaseDirs = new ArrayList<>();
		List<Path> flatFiles = new ArrayList<>();
		try (Stream<Path> entries = Files.list(dynamicDir)) {
			entries.forEach(p -> {
				if (Files.isDirectory(p) && PHASE_DIR_PATTERN.matcher(p.getFileName().toString()).matches()) {
					phaseDirs.add(p);
				} else if (p.toString().endsWith(".drule")) {
					flatFiles.add(p);
				}
			});
		}

		if (!phaseDirs.isEmpty()) {
			phaseDirs.sort(Comparator.comparing(p -> {
				Matcher m = PHASE_DIR_PATTERN.matcher(p.getFileName().toString());
				return m.matches() ? Integer.parseInt(m.group(1)) : 999;
			}));
			for (Path phaseDir : phaseDirs) {
				try (Stream<Path> files = Files.list(phaseDir)) {
					files.filter(p -> p.toString().endsWith(".drule")).sorted()
							.forEach(p -> loadFromFile(p).ifPresent(specs::add));
				}
			}
		} else {
			flatFiles.stream().sorted().forEach(p -> loadFromFile(p).ifPresent(specs::add));
		}

		return specs;
	}

	/**
	 * 指定した .drule ファイルを読み込む。
	 *
	 * @param filePath
	 *            .drule ファイルのパス
	 * @return 読み込んだ DynamicRuleSpec（ファイルが不正な場合は empty）
	 */
	private java.util.Optional<DynamicRuleSpec> loadFromFile(Path filePath) {
		try {
			String content = Files.readString(filePath, StandardCharsets.UTF_8);
			return java.util.Optional.ofNullable(parse(content, filePath.getFileName().toString()));
		} catch (IOException e) {
			LOGGER.warn("動的ルールファイルの読み込みに失敗: {} - {}", filePath, e.getMessage());
			return java.util.Optional.empty();
		}
	}

	/**
	 * テキストコンテンツをパースして DynamicRuleSpec を生成する。
	 *
	 * @param content
	 *            ファイル内容
	 * @param sourceName
	 *            ソース名（デバッグ用）
	 * @return パース結果、またはエラー時 null
	 */
	DynamicRuleSpec parse(String content, String sourceName) {
		List<String> lines = Arrays.asList(content.split("\r?\n"));

		List<ConversionToken> collectPattern = null;
		List<DynamicRuleSpec.FromToTemplate> templates = new ArrayList<>();
		String currentFrom = null;

		int i = 0;
		while (i < lines.size()) {
			String line = lines.get(i).trim();
			i++;

			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}

			if (line.startsWith(COLLECT_PREFIX)) {
				String collectStr = line.substring(COLLECT_PREFIX.length()).trim();
				try {
					collectPattern = ruleLoader.tokenizePattern(collectStr);
				} catch (IllegalArgumentException e) {
					LOGGER.warn("collect: パターンのトークン化に失敗: {} - {}", sourceName, e.getMessage());
					return null;
				}
			} else if (line.startsWith(FROM_PREFIX)) {
				currentFrom = line.substring(FROM_PREFIX.length()).trim();
			} else if (line.startsWith(TO_PREFIX)) {
				if (currentFrom == null) {
					LOGGER.warn("to: に対応する from: がありません: {}", sourceName);
					continue;
				}
				String toStr = line.substring(TO_PREFIX.length()).trim();
				templates.add(new DynamicRuleSpec.FromToTemplate(currentFrom, toStr));
				currentFrom = null;
			} else {
				LOGGER.warn("不明な行形式: {} : {}", sourceName, line);
			}
		}

		if (collectPattern == null) {
			LOGGER.warn("collect: が見つかりません: {}", sourceName);
			return null;
		}
		if (templates.isEmpty()) {
			LOGGER.warn("from:/to: テンプレートが見つかりません: {}", sourceName);
			return null;
		}

		LOGGER.debug("動的ルール読み込み完了: {} ({} テンプレート)", sourceName, templates.size());
		return new DynamicRuleSpec(sourceName, collectPattern, templates);
	}
}
