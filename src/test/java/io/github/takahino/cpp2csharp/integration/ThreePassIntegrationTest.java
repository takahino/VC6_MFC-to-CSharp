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

package io.github.takahino.cpp2csharp.integration;

import io.github.takahino.cpp2csharp.converter.ConversionResult;
import io.github.takahino.cpp2csharp.converter.CppToCSharpConverter;
import io.github.takahino.cpp2csharp.mrule.MultiReplaceRule;
import io.github.takahino.cpp2csharp.mrule.MultiReplaceRuleLoader;
import io.github.takahino.cpp2csharp.rule.ConversionRule;
import io.github.takahino.cpp2csharp.rule.ConversionRuleLoader;
import io.github.takahino.cpp2csharp.rule.ConversionRuleLoader.ThreePassRuleSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * 3パス構成（pre/main/post）の統合テスト。
 *
 * <p>
 * pre/post フェーズなしの3パス変換が既存の変換と同等の結果を生成することを検証する。
 * </p>
 */
@DisplayName("3パス統合テスト")
class ThreePassIntegrationTest {

	private final CppToCSharpConverter converter = new CppToCSharpConverter(false);
	private final ConversionRuleLoader ruleLoader = new ConversionRuleLoader();
	private final MultiReplaceRuleLoader mruleLoader = new MultiReplaceRuleLoader();

	private String loadCppFile(String fileName) throws IOException {
		String path = "cpp/" + fileName;
		try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
			if (is == null) {
				throw new IOException("リソースが見つかりません: " + path);
			}
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private String normalizeCode(String code) {
		return code.replaceAll("/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "").replaceAll("\\s+", "");
	}

	@Test
	@DisplayName("pre/post フェーズなしの3パス変換は既存変換と同等")
	void testThreePassWithEmptyPrePost() throws Exception {
		String cppSource = "void f() { int x = sin(y); }";

		ConversionRule sinRule = ruleLoader
				.loadFromString("from: sin ( ABSTRACT_PARAM00 )\nto: Math.Sin(ABSTRACT_PARAM00)", "test.rule").get(0);

		ThreePassRuleSet ruleSet = new ThreePassRuleSet(List.of(), // no pre phases
				List.of(List.of(sinRule)), // main phases
				List.of(), // no post phases
				List.of(), // no comby phases
				List.of() // no dynamic specs
		);

		ConversionResult result = converter.convertSourceThreePass(cppSource, ruleSet);

		assertThat(result.getCsCode()).contains("Math.Sin");
		assertThat(result.getTransformErrors()).isEmpty();
	}

	@Test
	@DisplayName("pre フェーズが BOOL を bool に変換し main フェーズが後続処理できる")
	void testPrePhaseSimpleReplacement() throws Exception {
		String cppSource = "void f() { BOOL x = sin(y); }";

		// Pre phase: BOOL → bool using mrule
		String mruleContent = """
				find: BOOL
				replace: bool
				""";
		List<MultiReplaceRule> preRules = mruleLoader.loadFromString(mruleContent, "pre.mrule");

		// Main phase: sin → Math.Sin
		ConversionRule sinRule = ruleLoader
				.loadFromString("from: sin ( ABSTRACT_PARAM00 )\nto: Math.Sin(ABSTRACT_PARAM00)", "test.rule").get(0);

		ThreePassRuleSet ruleSet = new ThreePassRuleSet(List.of(preRules), List.of(List.of(sinRule)), List.of(),
				List.of(), List.of());

		ConversionResult result = converter.convertSourceThreePass(cppSource, ruleSet);

		assertThat(result.getCsCode()).contains("bool");
		assertThat(result.getCsCode()).contains("Math.Sin");
	}

	@Test
	@DisplayName("post フェーズが main 変換後にトークンを処理できる")
	void testPostPhaseSimpleReplacement() throws Exception {
		String cppSource = "void f() { int x = sin(y); }";

		// Main phase: sin → Math.Sin
		ConversionRule sinRule = ruleLoader
				.loadFromString("from: sin ( ABSTRACT_PARAM00 )\nto: Math.Sin(ABSTRACT_PARAM00)", "test.rule").get(0);

		// Post phase: transform some token after main conversion
		// Note: after retokenization, main phase output is re-split into individual
		// tokens
		// so "Math.Sin(y)" becomes "Math", ".", "Sin", "(", "y", ")"
		// Post phase can then match those individual tokens
		String mruleContent = """
				find: int
				replace: int
				""";
		List<MultiReplaceRule> postRules = mruleLoader.loadFromString(mruleContent, "post.mrule");

		ThreePassRuleSet ruleSet = new ThreePassRuleSet(List.of(), List.of(List.of(sinRule)), List.of(postRules),
				List.of(), List.of());

		ConversionResult result = converter.convertSourceThreePass(cppSource, ruleSet);

		// After retokenization in post phase, Math.Sin(y) is split into individual
		// tokens
		// so the output contains "Math" and "Sin" but with spaces between them
		assertThat(result.getCsCode()).contains("Math");
		assertThat(result.getCsCode()).contains("Sin");
		assertThat(result.getTransformErrors()).isEmpty();
	}

	@Test
	@DisplayName("3パス変換は空のルールセットでも正常に動作する")
	void testThreePassWithAllEmptyPhases() {
		String cppSource = "void f() { int x = 1; }";

		ThreePassRuleSet ruleSet = new ThreePassRuleSet(List.of(), List.of(), List.of(), List.of(), List.of());

		ConversionResult result = converter.convertSourceThreePass(cppSource, ruleSet);

		// Should produce output without throwing
		assertThat(result.getCsCode()).isNotNull();
		assertThat(result.getTransformErrors()).isEmpty();
	}

	@Test
	@DisplayName("ThreePassRuleSet のデフォルト読み込みは既存ルールを main として使用する")
	void testDefaultThreePassRuleSetLoading() throws Exception {
		// The default loading should detect rules/main/ and load from there
		ThreePassRuleSet ruleSet = ruleLoader.loadThreePassRules();

		// With the new structure (rules/main/*), main phases should have content
		assertThat(ruleSet).isNotNull();
		assertThat(ruleSet.prePhases()).isNotNull();
		assertThat(ruleSet.mainPhases()).isNotNull();
		assertThat(ruleSet.postPhases()).isNotNull();
		// main phases should have rules since rules/main/ exists
		assertThat(ruleSet.mainPhases()).isNotEmpty();
	}

	@Test
	@DisplayName("pre mrule が複数行の LPSTR 宣言と malloc 代入を相関付けて正規化できる")
	void testPrePhaseMallocStringAssignmentNormalization() throws Exception {
		String cppSource = loadCppFile("test_mrule_malloc_string_assignment.cpp");
		ThreePassRuleSet ruleSet = ruleLoader.loadThreePassRules();

		ConversionResult result = converter.convertSourceThreePass(cppSource, ruleSet);
		String normalized = normalizeCode(result.getCsCode());

		assertThat(normalized).contains("stringpszName;");
		assertThat(normalized).contains("pszName=\"\";");
		assertThat(normalized).doesNotContain("malloc");
		assertThat(result.getTransformErrors()).isEmpty();
	}

	@Test
	@DisplayName("skip: により宣言と代入の間に別ロジックがあっても mrule でマッチして変換される")
	void testMallocWithLogicBetween() throws Exception {
		String cppSource = loadCppFile("test_mrule_malloc_with_logic_between.cpp");
		ThreePassRuleSet ruleSet = ruleLoader.loadThreePassRules();

		ConversionResult result = converter.convertSourceThreePass(cppSource, ruleSet);
		String normalized = normalizeCode(result.getCsCode());

		assertThat(normalized).contains("stringpszBuf;");
		assertThat(normalized).contains("pszBuf=\"\";");
		assertThat(normalized).doesNotContain("malloc");
		assertThat(result.getTransformErrors()).isEmpty();
	}

	@Test
	@DisplayName("同一ブロック内の複数 LPSTR 変数がそれぞれ独立して string に変換される")
	void testMultipleMallocVarsInSameBlock() throws Exception {
		String cppSource = loadCppFile("test_mrule_malloc_multiple_vars.cpp");
		ThreePassRuleSet ruleSet = ruleLoader.loadThreePassRules();

		ConversionResult result = converter.convertSourceThreePass(cppSource, ruleSet);
		String normalized = normalizeCode(result.getCsCode());

		assertThat(normalized).contains("stringpszName;");
		assertThat(normalized).contains("pszName=\"\";");
		assertThat(normalized).contains("stringpszPath;");
		assertThat(normalized).contains("pszPath=\"\";");
		assertThat(normalized).doesNotContain("malloc");
		assertThat(result.getTransformErrors()).isEmpty();
	}

	@Test
	@DisplayName("malloc 代入のない LPSTR 宣言はエラーなく処理される（変換なし）")
	void testLpstrWithoutMallocNotChanged() throws Exception {
		String cppSource = loadCppFile("test_mrule_malloc_no_match.cpp");
		ThreePassRuleSet ruleSet = ruleLoader.loadThreePassRules();

		ConversionResult result = converter.convertSourceThreePass(cppSource, ruleSet);

		assertThat(result.getCsCode()).isNotNull();
		assertThat(result.getTransformErrors()).isEmpty();
	}

	@Test
	@DisplayName("free() 呼び出しが mrule により削除される")
	void testFreeCallRemoval() throws Exception {
		String cppSource = "void f() { LPSTR pszName; pszName = (char*)malloc(256); free(pszName); }";
		ThreePassRuleSet ruleSet = ruleLoader.loadThreePassRules();

		ConversionResult result = converter.convertSourceThreePass(cppSource, ruleSet);
		String normalized = normalizeCode(result.getCsCode());

		assertThat(normalized).doesNotContain("free(");
		assertThat(result.getTransformErrors()).isEmpty();
	}
}
