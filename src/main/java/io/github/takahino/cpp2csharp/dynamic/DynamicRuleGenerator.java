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

import io.github.takahino.cpp2csharp.matcher.MatchResult;
import io.github.takahino.cpp2csharp.matcher.PatternMatcher;
import io.github.takahino.cpp2csharp.rule.ConversionRule;
import io.github.takahino.cpp2csharp.rule.ConversionRuleLoader;
import io.github.takahino.cpp2csharp.rule.ConversionToken;
import io.github.takahino.cpp2csharp.tree.AstNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 動的ルール生成クラス。
 *
 * <p>
 * {@link DynamicRuleSpec} の収集パターンをトークンストリームに適用し、 収集した値を {@code COLLECTED}
 * プレースホルダに代入して {@link ConversionRule} を動的に生成する。
 * </p>
 *
 * <h2>処理フロー</h2>
 * <ol>
 * <li>トークンノード列を文字列リストに変換</li>
 * <li>収集パターン ({@code collect:}) でトークンストリームを走査</li>
 * <li>ABSTRACT_PARAM00 キャプチャが単一トークン（識別子）のもののみ採用</li>
 * <li>収集値を重複排除</li>
 * <li>各収集値について、from/to テンプレートの {@code COLLECTED} を置換し ConversionRule を生成</li>
 * </ol>
 */
public class DynamicRuleGenerator {

	private static final Logger LOGGER = LoggerFactory.getLogger(DynamicRuleGenerator.class);

	/** COLLECTED プレースホルダの正規表現（単語境界付き） */
	private static final Pattern COLLECTED_PATTERN = Pattern.compile("\\bCOLLECTED\\b");

	private final ConversionRuleLoader ruleLoader;
	private final PatternMatcher patternMatcher;

	public DynamicRuleGenerator(ConversionRuleLoader ruleLoader) {
		this.ruleLoader = ruleLoader;
		this.patternMatcher = new PatternMatcher();
	}

	/**
	 * 外部から提供された値リストと from/to テンプレートから ConversionRule を生成する。
	 *
	 * <p>
	 * VC++6 の enum メンバ名など、別処理（字句解析・AST解析）で収集した識別子を
	 * トークンストリームのスキャンなしに直接ルール化するためのエントリポイント。
	 * </p>
	 *
	 * <p>
	 * ユースケース: enum { apple, banana } のメンバ名を外部解析で取得し、
	 * {@code apple → (EnumType) apple} のようなキャストルールを一括生成する。
	 * </p>
	 *
	 * @param externalValues
	 *            外部から提供された識別子リスト（重複は除去しない）
	 * @param templates
	 *            from/to テンプレートのリスト（{@code COLLECTED} が各値に置換される）
	 * @param sourceName
	 *            ログ・ルール sourceFile 用の名称
	 * @return 生成された ConversionRule のリスト
	 */
	public List<ConversionRule> generateFromValues(List<String> externalValues,
			List<DynamicRuleSpec.FromToTemplate> templates, String sourceName) {
		if (externalValues.isEmpty() || templates.isEmpty()) {
			LOGGER.debug("外部値またはテンプレートが空のため動的ルールを生成しません: {}", sourceName);
			return List.of();
		}

		List<ConversionRule> rules = new ArrayList<>();
		for (String value : externalValues) {
			for (DynamicRuleSpec.FromToTemplate template : templates) {
				ConversionRule rule = instantiateRule(value, template, sourceName);
				if (rule != null) {
					rules.add(rule);
				}
			}
		}

		LOGGER.info("外部値から動的ルール生成 ({}): {} 値 × {} テンプレート → {} ルール", sourceName, externalValues.size(), templates.size(),
				rules.size());
		return rules;
	}

	/**
	 * トークンノード列と動的ルール仕様リストから ConversionRule を生成する。
	 *
	 * @param tokenNodes
	 *            変換対象のトークンノード列（PRE フェーズ完了後）
	 * @param dynamicSpecs
	 *            動的ルール仕様のリスト
	 * @return 生成された ConversionRule のリスト
	 */
	public List<ConversionRule> generate(List<AstNode> tokenNodes, List<DynamicRuleSpec> dynamicSpecs) {
		List<String> tokens = tokenNodes.stream().map(AstNode::getText).toList();
		List<ConversionRule> generatedRules = new ArrayList<>();

		for (DynamicRuleSpec spec : dynamicSpecs) {
			List<ConversionRule> rules = generateFromSpec(tokens, spec);
			generatedRules.addAll(rules);
		}

		LOGGER.info("動的ルール生成完了: 合計 {} ルール", generatedRules.size());
		return generatedRules;
	}

	/**
	 * 1つの {@link DynamicRuleSpec} から ConversionRule を生成する。
	 */
	private List<ConversionRule> generateFromSpec(List<String> tokens, DynamicRuleSpec spec) {
		// 収集パターンを ConversionRule として実行（to は使わない）
		ConversionRule collectRule = new ConversionRule(spec.sourceFile() + " [collect]", spec.collectPattern(), "" // to
																													// テンプレートは使用しない
		);

		List<MatchResult> matches = patternMatcher.matchAll(List.of(collectRule), tokens);

		// ABSTRACT_PARAM00 (index=0) の単一トークンキャプチャを収集（重複排除、順序保持）
		Set<String> collectedValues = new LinkedHashSet<>();
		for (MatchResult match : matches) {
			List<String> captured = match.getCapturedTokens(0);
			if (captured.size() == 1) {
				collectedValues.add(captured.get(0));
			}
		}

		if (collectedValues.isEmpty()) {
			LOGGER.debug("収集パターンにマッチする値がありません: {}", spec.sourceFile());
			return List.of();
		}

		LOGGER.info("動的ルール収集値 ({}): {}", spec.sourceFile(), collectedValues);

		// 各収集値 × 各テンプレートでルールを生成
		List<ConversionRule> rules = new ArrayList<>();
		for (String value : collectedValues) {
			for (DynamicRuleSpec.FromToTemplate template : spec.templates()) {
				ConversionRule rule = instantiateRule(value, template, spec.sourceFile());
				if (rule != null) {
					rules.add(rule);
				}
			}
		}

		return rules;
	}

	/**
	 * COLLECTED を具体値に置換して ConversionRule を生成する。
	 *
	 * @param value
	 *            収集した具体値（識別子）
	 * @param template
	 *            from/to テンプレート
	 * @param sourceFile
	 *            ソースファイル名
	 * @return 生成された ConversionRule（失敗時は null）
	 */
	private ConversionRule instantiateRule(String value, DynamicRuleSpec.FromToTemplate template, String sourceFile) {
		// from テンプレートの COLLECTED を具体値に置換してトークン化
		String instantiatedFrom = COLLECTED_PATTERN.matcher(template.fromTemplate()).replaceAll(value);
		List<ConversionToken> fromTokens;
		try {
			fromTokens = ruleLoader.tokenizePattern(instantiatedFrom);
		} catch (IllegalArgumentException e) {
			LOGGER.warn("動的ルールの from パターンのトークン化に失敗: '{}' (value={}) - {}", instantiatedFrom, value, e.getMessage());
			return null;
		}

		// to テンプレートの COLLECTED を具体値に置換
		String instantiatedTo = COLLECTED_PATTERN.matcher(template.toTemplate()).replaceAll(value);

		LOGGER.debug("動的ルール生成: '{}' → '{}'", instantiatedFrom, instantiatedTo);
		return new ConversionRule(sourceFile + " [dynamic:" + value + "]", fromTokens, instantiatedTo);
	}
}
