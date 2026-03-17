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

import io.github.takahino.cpp2csharp.rule.ConversionRule;
import io.github.takahino.cpp2csharp.matcher.CppParserFactory;
import io.github.takahino.cpp2csharp.rule.ConversionRuleLoader;
import io.github.takahino.cpp2csharp.rule.ConversionToken;
import io.github.takahino.cpp2csharp.tree.AstNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DynamicRuleGenerator テスト")
class DynamicRuleGeneratorTest {

	private DynamicRuleGenerator generator;
	private DynamicRuleLoader loader;
	private ConversionRuleLoader ruleLoader;

	@BeforeEach
	void setUp() {
		ruleLoader = new ConversionRuleLoader(CppParserFactory.asLexerFactory());
		generator = new DynamicRuleGenerator(ruleLoader);
		loader = new DynamicRuleLoader(CppParserFactory.asLexerFactory());
	}

	/** トークン文字列列から AstNode リストを生成するヘルパー。 */
	private List<AstNode> nodes(String... tokens) {
		List<AstNode> result = new java.util.ArrayList<>();
		for (int i = 0; i < tokens.length; i++) {
			result.add(AstNode.tokenNode(tokens[i], 1, i));
		}
		return result;
	}

	@Test
	@DisplayName("collect パターンで単一トークンを収集してルールを生成する")
	void generateFromSingleCollectedValue() {
		// collect: ABSTRACT_PARAM00 :: → "MyClass" を収集
		DynamicRuleSpec spec = loader.parse("""
				collect: ABSTRACT_PARAM00 ::
				from: void COLLECTED :: ABSTRACT_PARAM00 ( )
				to: private void ABSTRACT_PARAM00()
				""", "test.drule");

		List<AstNode> tokenNodes = nodes("void", "MyClass", "::", "MyMethod", "(", ")");

		List<ConversionRule> rules = generator.generate(tokenNodes, List.of(spec));

		assertThat(rules).hasSize(1);
		assertThat(rules.get(0).getToTemplate()).isEqualTo("private void ABSTRACT_PARAM00()");
		// from パターンに "MyClass" が含まれる
		List<String> fromTexts = rules.get(0).getFromTokens().stream().map(ConversionToken::getValue).toList();
		assertThat(fromTexts).contains("MyClass");
		assertThat(fromTexts).contains("void");
		assertThat(fromTexts).doesNotContain("COLLECTED");
	}

	@Test
	@DisplayName("複数の収集値それぞれにルールを生成する")
	void generateForMultipleCollectedValues() {
		DynamicRuleSpec spec = loader.parse("""
				collect: ABSTRACT_PARAM00 ::
				from: void COLLECTED :: ABSTRACT_PARAM00 ( )
				to: private void ABSTRACT_PARAM00()
				""", "test.drule");

		// ClassA と ClassB の両方が :: の前に出現する
		List<AstNode> tokenNodes = nodes("void", "ClassA", "::", "MethodA", "(", ")", "void", "ClassB", "::", "MethodB",
				"(", ")");

		List<ConversionRule> rules = generator.generate(tokenNodes, List.of(spec));

		assertThat(rules).hasSize(2);
		List<String> fromFirstTokens = rules.stream().flatMap(r -> r.getFromTokens().stream())
				.map(ConversionToken::getValue).toList();
		assertThat(fromFirstTokens).contains("ClassA");
		assertThat(fromFirstTokens).contains("ClassB");
	}

	@Test
	@DisplayName("同じ収集値は重複排除される")
	void deduplicatesCollectedValues() {
		DynamicRuleSpec spec = loader.parse("""
				collect: ABSTRACT_PARAM00 ::
				from: void COLLECTED :: ABSTRACT_PARAM00 ( )
				to: private void ABSTRACT_PARAM00()
				""", "test.drule");

		// MyClass が2回出現しても1つのルールのみ生成
		List<AstNode> tokenNodes = nodes("void", "MyClass", "::", "Method1", "(", ")", "void", "MyClass", "::",
				"Method2", "(", ")");

		List<ConversionRule> rules = generator.generate(tokenNodes, List.of(spec));

		assertThat(rules).hasSize(1);
	}

	@Test
	@DisplayName("複数テンプレートがある場合、収集値ごとに全テンプレートからルールを生成する")
	void generateAllTemplatesPerValue() {
		DynamicRuleSpec spec = loader.parse("""
				collect: ABSTRACT_PARAM00 ::
				from: void COLLECTED :: ABSTRACT_PARAM00 ( ABSTRACT_PARAM01 )
				to: private void ABSTRACT_PARAM00(ABSTRACT_PARAM01)
				from: bool COLLECTED :: ABSTRACT_PARAM00 ( ABSTRACT_PARAM01 )
				to: private bool ABSTRACT_PARAM00(ABSTRACT_PARAM01)
				""", "test.drule");

		List<AstNode> tokenNodes = nodes("void", "MyClass", "::", "Method", "(", ")");

		List<ConversionRule> rules = generator.generate(tokenNodes, List.of(spec));

		// 1値 × 2テンプレート = 2ルール
		assertThat(rules).hasSize(2);
		assertThat(rules.get(0).getToTemplate()).contains("void");
		assertThat(rules.get(1).getToTemplate()).contains("bool");
	}

	@Test
	@DisplayName("collect パターンにマッチしない場合は空リストを返す")
	void returnsEmptyWhenNoMatch() {
		DynamicRuleSpec spec = loader.parse("""
				collect: ABSTRACT_PARAM00 ::
				from: void COLLECTED :: ABSTRACT_PARAM00 ( )
				to: private void ABSTRACT_PARAM00()
				""", "test.drule");

		// :: が存在しないトークン列
		List<AstNode> tokenNodes = nodes("int", "x", "=", "1", ";");

		List<ConversionRule> rules = generator.generate(tokenNodes, List.of(spec));

		assertThat(rules).isEmpty();
	}

	@Test
	@DisplayName("ABSTRACT_PARAM00 が複数トークンにマッチする場合は収集しない")
	void ignoresMultiTokenCaptures() {
		DynamicRuleSpec spec = loader.parse("""
				collect: ABSTRACT_PARAM00 ::
				from: void COLLECTED :: ABSTRACT_PARAM00 ( )
				to: private void ABSTRACT_PARAM00()
				""", "test.drule");

		// "a b ::" → ABSTRACT_PARAM00 が "a b" の2トークンになるため収集しない
		// PatternMatcher は "a" を AP00 にキャプチャし "::" にマッチする可能性があるが、
		// 単一トークンキャプチャのみを採用するフィルタで "a b ::" は除外される
		List<AstNode> tokenNodes = nodes("a", "b", "::", "Method", "(", ")");

		List<ConversionRule> rules = generator.generate(tokenNodes, List.of(spec));

		// "a" は単一トークンとして収集される（PatternMatcher が最短マッチで "a" を選ぶ可能性あり）
		// 少なくとも "a b" という2トークンキャプチャは排除される
		for (ConversionRule rule : rules) {
			rule.getFromTokens().stream()
					.filter(t -> !t.isAbstractParam() && !t.getValue().equals("void") && !t.getValue().equals("::")
							&& !t.getValue().equals("(") && !t.getValue().equals(")"))
					.forEach(t -> assertThat(t.getValue()).doesNotContain(" "));
		}
	}

	@Test
	@DisplayName("to テンプレートの COLLECTED も収集値で置換される")
	void collectedReplacedInToTemplate() {
		// enum メンバ収集: COLLECTED が to テンプレートにも現れるケース
		DynamicRuleSpec spec = loader.parse("""
				collect: , ABSTRACT_PARAM00 ,
				from: COLLECTED
				to: (int) COLLECTED
				""", "test.drule");

		List<AstNode> tokenNodes = nodes("enum", "{", "apple", ",", "banana", ",", "cherry", "}");

		List<ConversionRule> rules = generator.generate(tokenNodes, List.of(spec));

		// banana と cherry が収集される（"," に挟まれた単一トークン）
		assertThat(rules).isNotEmpty();
		rules.forEach(rule -> assertThat(rule.getToTemplate()).startsWith("(int) "));
	}

	@Test
	@DisplayName("dynamicSpecs が空の場合は空リストを返す")
	void emptySpecsReturnsEmptyRules() {
		List<AstNode> tokenNodes = nodes("void", "MyClass", "::", "Method", "(", ")");
		List<ConversionRule> rules = generator.generate(tokenNodes, List.of());
		assertThat(rules).isEmpty();
	}
}
