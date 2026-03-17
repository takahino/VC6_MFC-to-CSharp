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

package io.github.takahino.cpp2csharp.matcher;

import io.github.takahino.cpp2csharp.rule.ConversionRule;
import io.github.takahino.cpp2csharp.matcher.CppParserFactory;
import io.github.takahino.cpp2csharp.rule.ConversionRuleLoader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link PatternMatcher} のユニットテスト。
 */
@DisplayName("PatternMatcher テスト")
class PatternMatcherTest {

	private PatternMatcher matcher;
	private ConversionRuleLoader loader;

	@BeforeEach
	void setUp() {
		matcher = new PatternMatcher();
		loader = new ConversionRuleLoader(CppParserFactory.asLexerFactory());
	}

	/**
	 * ルール文字列からルールを生成するヘルパー。
	 */
	private ConversionRule rule(String from, String to) {
		String content = "from: " + from + "\nto: " + to;
		return loader.loadFromString(content, "test.rule").get(0);
	}

	@Test
	@DisplayName("具体トークンのみのパターンがマッチする")
	void testConcreteTokenMatch() {
		ConversionRule r = rule("CString", "string");
		List<String> tokens = List.of("CString");

		List<MatchResult> results = matcher.matchRule(r, tokens);
		assertThat(results).hasSize(1);
		assertThat(results.get(0).getStartIndex()).isEqualTo(0);
		assertThat(results.get(0).getEndIndex()).isEqualTo(1);
	}

	@Test
	@DisplayName("具体トークンが不一致の場合はマッチしない")
	void testConcreteTokenNoMatch() {
		ConversionRule r = rule("CString", "string");
		List<String> tokens = List.of("int");

		List<MatchResult> results = matcher.matchRule(r, tokens);
		assertThat(results).isEmpty();
	}

	@Test
	@DisplayName("ABSTRACT_PARAM を含むパターンが正しくマッチする")
	void testAbstractParamMatch() {
		ConversionRule r = rule("sin ( ABSTRACT_PARAM00 )", "Math.Sin(ABSTRACT_PARAM00)");
		List<String> tokens = List.of("sin", "(", "x", ")");

		List<MatchResult> results = matcher.matchRule(r, tokens);
		assertThat(results).hasSize(1);
		assertThat(results.get(0).getCapturedTokens(0)).isEqualTo(List.of("x"));
	}

	@Test
	@DisplayName("複数トークンの ABSTRACT_PARAM がマッチする")
	void testAbstractParamMultipleTokens() {
		ConversionRule r = rule("sin ( ABSTRACT_PARAM00 )", "Math.Sin(ABSTRACT_PARAM00)");
		// sin ( CreateMessage ( a , b ) )
		List<String> tokens = List.of("sin", "(", "CreateMessage", "(", "a", ",", "b", ")", ")");

		List<MatchResult> results = matcher.matchRule(r, tokens);
		assertThat(results).hasSize(1);
		assertThat(results.get(0).getCapturedTokens(0)).isEqualTo(List.of("CreateMessage", "(", "a", ",", "b", ")"));
	}

	@Test
	@DisplayName("AfxMessageBox パターンが正しくマッチする")
	void testAfxMessageBoxPattern() {
		ConversionRule r = rule("AfxMessageBox ( ABSTRACT_PARAM00 , MB_OK | MB_ICONERROR ) ;",
				"MessageBox.Show(ABSTRACT_PARAM00, \"\", MessageBoxButtons.OK, MessageBoxIcon.Error);");
		List<String> tokens = List.of("AfxMessageBox", "(", "\"エラーが発生しました\"", ",", "MB_OK", "|", "MB_ICONERROR", ")",
				";");

		List<MatchResult> results = matcher.matchRule(r, tokens);
		assertThat(results).hasSize(1);
		assertThat(results.get(0).getCapturedText(0)).isEqualTo("\"エラーが発生しました\"");
	}

	@Test
	@DisplayName("複雑な ABSTRACT_PARAM (ネスト式) がマッチする")
	void testNestedExpressionAbstractParam() {
		ConversionRule r = rule("AfxMessageBox ( ABSTRACT_PARAM00 , MB_OK | MB_ICONERROR ) ;",
				"MessageBox.Show(ABSTRACT_PARAM00, \"\", MessageBoxButtons.OK, MessageBoxIcon.Error);");
		// CreateMessage(a, b) + "abcd" の部分が ABSTRACT_PARAM00 になる
		List<String> tokens = List.of("AfxMessageBox", "(", "CreateMessage", "(", "a", ",", "b", ")", "+", "\"abcd\"",
				",", "MB_OK", "|", "MB_ICONERROR", ")", ";");

		List<MatchResult> results = matcher.matchRule(r, tokens);
		assertThat(results).hasSize(1);

		List<String> captured = results.get(0).getCapturedTokens(0);
		assertThat(captured).containsExactly("CreateMessage", "(", "a", ",", "b", ")", "+", "\"abcd\"");
	}

	@Test
	@DisplayName("2つの ABSTRACT_PARAM を含むパターンがマッチする")
	void testTwoAbstractParams() {
		ConversionRule r = rule("pow ( ABSTRACT_PARAM00 , ABSTRACT_PARAM01 )",
				"Math.Pow(ABSTRACT_PARAM00, ABSTRACT_PARAM01)");
		List<String> tokens = List.of("pow", "(", "x", ",", "2.0", ")");

		List<MatchResult> results = matcher.matchRule(r, tokens);
		assertThat(results).hasSize(1);
		assertThat(results.get(0).getCapturedText(0)).isEqualTo("x");
		assertThat(results.get(0).getCapturedText(1)).isEqualTo("2.0");
	}

	@Test
	@DisplayName("トークン列の途中にマッチを見つけられる")
	void testMatchInMiddle() {
		ConversionRule r = rule("CString", "string");
		List<String> tokens = List.of("void", "func", "(", "CString", "param", ")");

		List<MatchResult> results = matcher.matchRule(r, tokens);
		assertThat(results).hasSize(1);
		assertThat(results.get(0).getStartIndex()).isEqualTo(3);
		assertThat(results.get(0).getEndIndex()).isEqualTo(4);
	}

	@Test
	@DisplayName("matchAll で複数ルールをまとめて検索できる")
	void testMatchAll() {
		List<ConversionRule> rules = List.of(rule("sin ( ABSTRACT_PARAM00 )", "Math.Sin(ABSTRACT_PARAM00)"),
				rule("cos ( ABSTRACT_PARAM00 )", "Math.Cos(ABSTRACT_PARAM00)"));
		List<String> tokens = List.of("sin", "(", "x", ")");

		List<MatchResult> results = matcher.matchAll(rules, tokens);
		assertThat(results).hasSize(1);
		assertThat(results.get(0).getRule().getToTemplate()).isEqualTo("Math.Sin(ABSTRACT_PARAM00)");
	}

	@Test
	@DisplayName("1引数ルールは2引数の呼び出しにマッチしない（引数数保護）")
	void testSingleArgRuleDoesNotMatchTwoArgCall() {
		ConversionRule r = rule("AfxMessageBox ( ABSTRACT_PARAM00 ) ;", "MessageBox.Show(ABSTRACT_PARAM00);");
		// AfxMessageBox("Hello", MB_OK) ; — 引数2個なのでマッチしてはいけない
		List<String> tokens = List.of("AfxMessageBox", "(", "\"Hello\"", ",", "MB_OK", ")", ";");

		List<MatchResult> results = matcher.matchRule(r, tokens);
		assertThat(results).as("引数2個の呼び出しは1引数ルールにマッチしないこと").isEmpty();
	}

	@Test
	@DisplayName("1引数ルールはネスト式（内部カンマあり）にマッチする")
	void testSingleArgRuleMatchesNestedExprWithInternalComma() {
		ConversionRule r = rule("sin ( ABSTRACT_PARAM00 )", "Math.Sin(ABSTRACT_PARAM00)");
		// sin ( CreateMessage ( a , b ) ) — カンマはdepth1なのでマッチする
		List<String> tokens = List.of("sin", "(", "CreateMessage", "(", "a", ",", "b", ")", ")");

		List<MatchResult> results = matcher.matchRule(r, tokens);
		assertThat(results).as("ネスト式（depth>0のカンマ）は1引数ルールにマッチすること").hasSize(1);
		assertThat(results.get(0).getCapturedTokens(0)).isEqualTo(List.of("CreateMessage", "(", "a", ",", "b", ")"));
	}

	@Test
	@DisplayName("マッチなしの場合は空リストを返す")
	void testNoMatchReturnsEmptyList() {
		ConversionRule r = rule("sin ( ABSTRACT_PARAM00 )", "Math.Sin(ABSTRACT_PARAM00)");
		List<String> tokens = List.of("cos", "(", "x", ")");

		List<MatchResult> results = matcher.matchRule(r, tokens);
		assertThat(results).isEmpty();
	}

	@Test
	@DisplayName("MB_YESNO | MB_ICONQUESTION パターンがマッチする")
	void testAfxMessageBoxMbYesNo() {
		ConversionRule r = rule("AfxMessageBox ( ABSTRACT_PARAM00 , MB_YESNO | MB_ICONQUESTION )",
				"MessageBox.Show(ABSTRACT_PARAM00, \"\", MessageBoxButtons.YesNo, MessageBoxIcon.Question)");
		List<String> tokens = List.of("AfxMessageBox", "(", "question", ",", "MB_YESNO", "|", "MB_ICONQUESTION", ")",
				";");

		List<MatchResult> results = matcher.matchRule(r, tokens);
		assertThat(results).as("MB_YESNO ルールがマッチすること").hasSize(1);
		assertThat(results.get(0).getCapturedText(0)).isEqualTo("question");
	}

	@Test
	@DisplayName("getCapturedText で ABSTRACT_PARAM のテキストを取得できる")
	void testGetCapturedText() {
		ConversionRule r = rule("pow ( ABSTRACT_PARAM00 , ABSTRACT_PARAM01 )",
				"Math.Pow(ABSTRACT_PARAM00, ABSTRACT_PARAM01)");
		List<String> tokens = List.of("pow", "(", "a", "+", "b", ",", "c", "*", "d", ")");

		List<MatchResult> results = matcher.matchRule(r, tokens);
		assertThat(results).hasSize(1);
		assertThat(results.get(0).getCapturedText(0)).isEqualTo("a + b");
		assertThat(results.get(0).getCapturedText(1)).isEqualTo("c * d");
	}
}
