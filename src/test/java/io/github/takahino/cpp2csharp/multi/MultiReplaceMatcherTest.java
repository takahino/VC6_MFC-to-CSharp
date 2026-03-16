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

package io.github.takahino.cpp2csharp.multi;

import io.github.takahino.cpp2csharp.mrule.MultiReplaceRule;
import io.github.takahino.cpp2csharp.mrule.MultiReplaceRuleLoader;
import io.github.takahino.cpp2csharp.tree.AstNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link MultiReplaceMatcher} のユニットテスト。
 */
@DisplayName("MultiReplaceMatcher テスト")
class MultiReplaceMatcherTest {

	private final MultiReplaceMatcher matcher = new MultiReplaceMatcher();
	private final MultiReplaceRuleLoader loader = new MultiReplaceRuleLoader();

	/** トークン文字列リストから AstNode リストを生成するヘルパー */
	private List<AstNode> nodes(String... tokens) {
		List<AstNode> result = new java.util.ArrayList<>();
		for (int i = 0; i < tokens.length; i++) {
			result.add(AstNode.tokenNode(tokens[i], 1, i, i));
		}
		return result;
	}

	@Test
	@DisplayName("単純な1つの find spec にマッチする")
	void testSingleFindSpec() {
		String content = """
				find: BOOL
				replace: bool
				""";
		List<MultiReplaceRule> rules = loader.loadFromString(content, "test.mrule");
		List<AstNode> tokenNodes = nodes("int", "x", ";", "BOOL", "y", ";");

		List<MultiReplaceMatchResult> results = matcher.matchAll(rules, tokenNodes);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).stepMatches()).hasSize(1);
		assertThat(results.get(0).stepMatches().get(0).getStartIndex()).isEqualTo(3);
	}

	@Test
	@DisplayName("連続する2つの find spec にマッチする")
	void testConsecutiveSpecs() {
		String content = """
				find: BOOL
				replace: bool
				find: x
				replace: y
				""";
		List<MultiReplaceRule> rules = loader.loadFromString(content, "test.mrule");
		// "BOOL x" at positions 0-1 should match
		List<AstNode> tokenNodes = nodes("BOOL", "x", ";");

		List<MultiReplaceMatchResult> results = matcher.matchAll(rules, tokenNodes);

		assertThat(results).hasSize(1);
		MultiReplaceMatchResult result = results.get(0);
		assertThat(result.stepMatches()).hasSize(2);
		assertThat(result.stepMatches().get(0).getStartIndex()).isEqualTo(0);
		assertThat(result.stepMatches().get(1).getStartIndex()).isEqualTo(1);
	}

	@Test
	@DisplayName("連続する2つの find spec が連続していない場合はマッチしない")
	void testConsecutiveSpecsNoMatch() {
		String content = """
				find: BOOL
				replace: bool
				find: z
				replace: y
				""";
		List<MultiReplaceRule> rules = loader.loadFromString(content, "test.mrule");
		// "BOOL" at 0, "z" at 2 → not consecutive
		List<AstNode> tokenNodes = nodes("BOOL", "x", "z", ";");

		List<MultiReplaceMatchResult> results = matcher.matchAll(rules, tokenNodes);
		assertThat(results).isEmpty();
	}

	@Test
	@DisplayName("skip: を使ったスキップマッチ")
	void testSkipMatch() {
		String content = """
				find: BOOL
				replace: bool
				skip:
				find: x
				replace: y
				""";
		List<MultiReplaceRule> rules = loader.loadFromString(content, "test.mrule");
		// "BOOL" at 0, "x" at 2 (with skip)
		List<AstNode> tokenNodes = nodes("BOOL", "z", "x", ";");

		List<MultiReplaceMatchResult> results = matcher.matchAll(rules, tokenNodes);

		assertThat(results).hasSize(1);
		MultiReplaceMatchResult result = results.get(0);
		assertThat(result.stepMatches()).hasSize(2);
		assertThat(result.stepMatches().get(0).getStartIndex()).isEqualTo(0);
		assertThat(result.stepMatches().get(1).getStartIndex()).isEqualTo(2);
	}

	@Test
	@DisplayName("マッチがない場合は空のリストを返す")
	void testNoMatch() {
		String content = """
				find: BOOL
				replace: bool
				""";
		List<MultiReplaceRule> rules = loader.loadFromString(content, "test.mrule");
		List<AstNode> tokenNodes = nodes("int", "x", ";");

		List<MultiReplaceMatchResult> results = matcher.matchAll(rules, tokenNodes);
		assertThat(results).isEmpty();
	}

	@Test
	@DisplayName("空のルールリストでは空の結果を返す")
	void testEmptyRules() {
		List<AstNode> tokenNodes = nodes("int", "x", ";");
		List<MultiReplaceMatchResult> results = matcher.matchAll(List.of(), tokenNodes);
		assertThat(results).isEmpty();
	}

	@Test
	@DisplayName("空のトークンリストでは空の結果を返す")
	void testEmptyTokenList() {
		String content = """
				find: BOOL
				replace: bool
				""";
		List<MultiReplaceRule> rules = loader.loadFromString(content, "test.mrule");
		List<MultiReplaceMatchResult> results = matcher.matchAll(rules, List.of());
		assertThat(results).isEmpty();
	}

	@Test
	@DisplayName("ABSTRACT_PARAM を含む find spec にマッチする")
	void testAbstractParamMatch() {
		String content = """
				find: AfxMessageBox ( ABSTRACT_PARAM00 )
				replace: MessageBox.Show(ABSTRACT_PARAM00)
				""";
		List<MultiReplaceRule> rules = loader.loadFromString(content, "test.mrule");
		List<AstNode> tokenNodes = nodes("AfxMessageBox", "(", "\"hello\"", ")");

		List<MultiReplaceMatchResult> results = matcher.matchAll(rules, tokenNodes);
		assertThat(results).hasSize(1);
		assertThat(results.get(0).captures()).containsKey(0);
		assertThat(results.get(0).captures().get(0)).containsExactly("\"hello\"");
	}

	@Test
	@DisplayName("2 つの skip: で 3 find spec が離れた位置にマッチする")
	void testDoubleSkipMatch() {
		String content = """
				find: A
				replace: a
				skip:
				find: B
				replace: b
				skip:
				find: C
				replace: c
				""";
		List<MultiReplaceRule> rules = loader.loadFromString(content, "test.mrule");
		// A at 0, B at 2, C at 4 (tokens between are skipped)
		List<AstNode> tokenNodes = nodes("A", "x", "B", "y", "C", ";");

		List<MultiReplaceMatchResult> results = matcher.matchAll(rules, tokenNodes);

		assertThat(results).hasSize(1);
		MultiReplaceMatchResult result = results.get(0);
		assertThat(result.stepMatches()).hasSize(3);
		assertThat(result.stepMatches().get(0).getStartIndex()).isEqualTo(0);
		assertThat(result.stepMatches().get(1).getStartIndex()).isEqualTo(2);
		assertThat(result.stepMatches().get(2).getStartIndex()).isEqualTo(4);
	}

	@Test
	@DisplayName("2 つの skip: で最後の find spec が存在しない場合はマッチしない")
	void testDoubleSkipNoMatchWhenLastMissing() {
		String content = """
				find: A
				replace: a
				skip:
				find: B
				replace: b
				skip:
				find: C
				replace: c
				""";
		List<MultiReplaceRule> rules = loader.loadFromString(content, "test.mrule");
		// A at 0, B at 2, but C is absent
		List<AstNode> tokenNodes = nodes("A", "x", "B", "y", "D", ";");

		List<MultiReplaceMatchResult> results = matcher.matchAll(rules, tokenNodes);
		assertThat(results).isEmpty();
	}

	@Test
	@DisplayName("2 つの skip: で中間の find spec が存在しない場合はマッチしない")
	void testDoubleSkipNoMatchWhenMiddleMissing() {
		String content = """
				find: A
				replace: a
				skip:
				find: B
				replace: b
				skip:
				find: C
				replace: c
				""";
		List<MultiReplaceRule> rules = loader.loadFromString(content, "test.mrule");
		// A at 0, B absent, C at 3
		List<AstNode> tokenNodes = nodes("A", "x", "D", "C", ";");

		List<MultiReplaceMatchResult> results = matcher.matchAll(rules, tokenNodes);
		assertThat(results).isEmpty();
	}

	@Test
	@DisplayName("2 つの skip: を持つ 3 spec 間で同じ ABSTRACT_PARAM が一致するときだけマッチする")
	void testDoubleSkipSharedAbstractParam() {
		String content = """
				scope: block
				find: TYPE ABSTRACT_PARAM00 ;
				replace: string ABSTRACT_PARAM00 ;
				skip:
				find: ABSTRACT_PARAM00 = init ( ) ;
				replace: ABSTRACT_PARAM00 = "" ;
				skip:
				find: use ( ABSTRACT_PARAM00 ) ;
				replace: consume(ABSTRACT_PARAM00);
				""";
		List<MultiReplaceRule> rules = loader.loadFromString(content, "test.mrule");
		List<AstNode> tokenNodes = nodes("{", "TYPE", "pszX", ";", // spec 0
				"noop", ";", // skipped
				"pszX", "=", "init", "(", ")", ";", // spec 1
				"log", ";", // skipped
				"use", "(", "pszX", ")", ";", // spec 2
				"}");

		List<MultiReplaceMatchResult> results = matcher.matchAll(rules, tokenNodes);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).captures().get(0)).containsExactly("pszX");
		assertThat(results.get(0).stepMatches()).hasSize(3);
	}

	@Test
	@DisplayName("2 つの skip: を持つ 3 spec 間で ABSTRACT_PARAM の値が異なるとマッチしない")
	void testDoubleSkipSharedAbstractParamMismatch() {
		String content = """
				scope: block
				find: TYPE ABSTRACT_PARAM00 ;
				replace: string ABSTRACT_PARAM00 ;
				skip:
				find: ABSTRACT_PARAM00 = init ( ) ;
				replace: ABSTRACT_PARAM00 = "" ;
				skip:
				find: use ( ABSTRACT_PARAM00 ) ;
				replace: consume(ABSTRACT_PARAM00);
				""";
		List<MultiReplaceRule> rules = loader.loadFromString(content, "test.mrule");
		// spec 0 captures pszX, spec 2 uses pszY → mismatch
		List<AstNode> tokenNodes = nodes("{", "TYPE", "pszX", ";", "pszX", "=", "init", "(", ")", ";", "use", "(",
				"pszY", ")", ";", "}");

		List<MultiReplaceMatchResult> results = matcher.matchAll(rules, tokenNodes);
		assertThat(results).isEmpty();
	}

	@Test
	@DisplayName("連続 + skip: の混在パターン（spec0-spec1 連続、spec1-spec2 skip）でマッチする")
	void testMixedConsecutiveAndSkipMatch() {
		String content = """
				find: A
				replace: a
				find: B
				replace: b
				skip:
				find: C
				replace: c
				""";
		List<MultiReplaceRule> rules = loader.loadFromString(content, "test.mrule");
		// A at 0, B at 1 (consecutive), C at 3 (with skip)
		List<AstNode> tokenNodes = nodes("A", "B", "x", "C", ";");

		List<MultiReplaceMatchResult> results = matcher.matchAll(rules, tokenNodes);

		assertThat(results).hasSize(1);
		assertThat(result(results, 0).stepMatches().get(0).getStartIndex()).isEqualTo(0);
		assertThat(result(results, 0).stepMatches().get(1).getStartIndex()).isEqualTo(1);
		assertThat(result(results, 0).stepMatches().get(2).getStartIndex()).isEqualTo(3);
	}

	@Test
	@DisplayName("連続 + skip: の混在で spec1 が連続していない場合はマッチしない")
	void testMixedConsecutiveAndSkipNoMatch() {
		String content = """
				find: A
				replace: a
				find: B
				replace: b
				skip:
				find: C
				replace: c
				""";
		List<MultiReplaceRule> rules = loader.loadFromString(content, "test.mrule");
		// A at 0, B at 2 (not consecutive with A), C at 4
		List<AstNode> tokenNodes = nodes("A", "x", "B", "x", "C", ";");

		List<MultiReplaceMatchResult> results = matcher.matchAll(rules, tokenNodes);
		assertThat(results).isEmpty();
	}

	/** ヘルパー: results.get(index) */
	private MultiReplaceMatchResult result(List<MultiReplaceMatchResult> results, int index) {
		return results.get(index);
	}

	@Test
	@DisplayName("複数 spec 間で同じ ABSTRACT_PARAM の値が一致するときだけマッチする")
	void testSharedAbstractParamAcrossSpecs() {
		String content = """
				scope: block
				find: LPSTR ABSTRACT_PARAM00 ;
				replace: string ABSTRACT_PARAM00 = "" ;
				skip:
				find: ABSTRACT_PARAM00 = ( char * ) malloc ( ABSTRACT_PARAM01 ) ;
				replace:
				""";
		List<MultiReplaceRule> rules = loader.loadFromString(content, "test.mrule");
		List<AstNode> tokenNodes = nodes("{", "LPSTR", "pszName", ";", "pszName", "=", "(", "char", "*", ")", "malloc",
				"(", "256", ")", ";", "}");

		List<MultiReplaceMatchResult> results = matcher.matchAll(rules, tokenNodes);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).captures().get(0)).containsExactly("pszName");
		assertThat(results.get(0).stepMatches()).hasSize(2);
	}

	@Test
	@DisplayName("複数 spec 間で同じ ABSTRACT_PARAM の値が異なるとマッチしない")
	void testSharedAbstractParamMismatchAcrossSpecs() {
		String content = """
				scope: block
				find: LPSTR ABSTRACT_PARAM00 ;
				replace: string ABSTRACT_PARAM00 = "" ;
				skip:
				find: ABSTRACT_PARAM00 = ( char * ) malloc ( ABSTRACT_PARAM01 ) ;
				replace:
				""";
		List<MultiReplaceRule> rules = loader.loadFromString(content, "test.mrule");
		List<AstNode> tokenNodes = nodes("{", "LPSTR", "pszName", ";", "pszOther", "=", "(", "char", "*", ")", "malloc",
				"(", "256", ")", ";", "}");

		List<MultiReplaceMatchResult> results = matcher.matchAll(rules, tokenNodes);

		assertThat(results).isEmpty();
	}
}
