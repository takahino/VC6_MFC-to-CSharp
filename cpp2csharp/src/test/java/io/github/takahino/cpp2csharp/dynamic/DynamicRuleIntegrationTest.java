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

import io.github.takahino.cpp2csharp.converter.ConversionResult;
import io.github.takahino.cpp2csharp.converter.CppToCSharpConverter;
import io.github.takahino.cpp2csharp.matcher.CppParserFactory;
import io.github.takahino.cpp2csharp.rule.ConversionRuleLoader;
import io.github.takahino.cpp2csharp.rule.ConversionRuleLoader.ThreePassRuleSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * 動的ルール生成の統合テスト。
 *
 * <p>
 * 実際の C++ コードを変換し、トークンストリームから収集した値で 動的生成されたルールが正しく適用されることを検証する。
 * </p>
 */
@DisplayName("動的ルール生成 統合テスト")
class DynamicRuleIntegrationTest {

	private CppToCSharpConverter converter;
	private ConversionRuleLoader ruleLoader;
	private DynamicRuleLoader dynamicLoader;

	@BeforeEach
	void setUp() {
		converter = new CppToCSharpConverter(false);
		ruleLoader = new ConversionRuleLoader(CppParserFactory.asLexerFactory());
		dynamicLoader = new DynamicRuleLoader(CppParserFactory.asLexerFactory());
	}

	/** drule 文字列から DynamicRuleSpec を1つ生成するヘルパー。 */
	private DynamicRuleSpec spec(String druleContent) {
		return dynamicLoader.parse(druleContent, "test.drule");
	}

	/** ThreePassRuleSet を dynamic specs だけで構築するヘルパー。 */
	private ThreePassRuleSet ruleSetWithDynamic(DynamicRuleSpec... specs) {
		return new ThreePassRuleSet(List.of(), List.of(), List.of(), List.of(), List.of(specs));
	}

	// =========================================================================
	// メソッド定義変換
	// =========================================================================

	@Test
	@DisplayName("void メソッド定義: ClassName::Method → private void Method")
	void voidMethodDefinition() {
		String cpp = "void MyClass::DoSomething() { }";

		DynamicRuleSpec methodSpec = spec("""
				collect: ABSTRACT_PARAM00 ::
				from: void COLLECTED :: ABSTRACT_PARAM00 ( )
				to: private void ABSTRACT_PARAM00()
				""");

		ConversionResult result = converter.convertSourceThreePass(cpp, ruleSetWithDynamic(methodSpec));

		assertThat(result.getCsCode()).contains("private void DoSomething()");
		assertThat(result.getCsCode()).doesNotContain("MyClass::");
		assertThat(result.getTransformErrors()).isEmpty();
	}

	@Test
	@DisplayName("bool メソッド定義: ClassName::Method → private bool Method")
	void boolMethodDefinition() {
		String cpp = "bool MyClass::IsValid() { return true; }";

		DynamicRuleSpec methodSpec = spec("""
				collect: ABSTRACT_PARAM00 ::
				from: bool COLLECTED :: ABSTRACT_PARAM00 ( )
				to: private bool ABSTRACT_PARAM00()
				""");

		ConversionResult result = converter.convertSourceThreePass(cpp, ruleSetWithDynamic(methodSpec));

		assertThat(result.getCsCode()).contains("private bool IsValid()");
		assertThat(result.getCsCode()).doesNotContain("MyClass::");
	}

	@Test
	@DisplayName("引数ありメソッド定義: void ClassName::Method(int n) → private void Method(int n)")
	void methodDefinitionWithParams() {
		String cpp = "void MyClass::LoadItems(int nCategory) { }";

		DynamicRuleSpec methodSpec = spec("""
				collect: ABSTRACT_PARAM00 ::
				from: void COLLECTED :: ABSTRACT_PARAM00 ( ABSTRACT_PARAM01 )
				to: private void ABSTRACT_PARAM00(ABSTRACT_PARAM01)
				""");

		ConversionResult result = converter.convertSourceThreePass(cpp, ruleSetWithDynamic(methodSpec));

		assertThat(result.getCsCode()).contains("private void LoadItems(");
		assertThat(result.getCsCode()).contains("int");
		assertThat(result.getCsCode()).doesNotContain("MyClass::");
	}

	@Test
	@DisplayName("複数メソッド: クラス名を1度収集して全メソッドに適用")
	void multipleMethodsShareCollectedClassName() {
		String cpp = """
				void MyClass::MethodA() { }
				void MyClass::MethodB() { }
				""";

		DynamicRuleSpec methodSpec = spec("""
				collect: ABSTRACT_PARAM00 ::
				from: void COLLECTED :: ABSTRACT_PARAM00 ( )
				to: private void ABSTRACT_PARAM00()
				""");

		ConversionResult result = converter.convertSourceThreePass(cpp, ruleSetWithDynamic(methodSpec));

		assertThat(result.getCsCode()).contains("private void MethodA()");
		assertThat(result.getCsCode()).contains("private void MethodB()");
		assertThat(result.getCsCode()).doesNotContain("MyClass::");
	}

	@Test
	@DisplayName("複数クラスのメソッドをそれぞれ正しく変換する")
	void multipleClassesCollectedAndConverted() {
		String cpp = """
				void ClassA::MethodA() { }
				void ClassB::MethodB() { }
				""";

		DynamicRuleSpec methodSpec = spec("""
				collect: ABSTRACT_PARAM00 ::
				from: void COLLECTED :: ABSTRACT_PARAM00 ( )
				to: private void ABSTRACT_PARAM00()
				""");

		ConversionResult result = converter.convertSourceThreePass(cpp, ruleSetWithDynamic(methodSpec));

		assertThat(result.getCsCode()).contains("private void MethodA()");
		assertThat(result.getCsCode()).contains("private void MethodB()");
		assertThat(result.getCsCode()).doesNotContain("ClassA::");
		assertThat(result.getCsCode()).doesNotContain("ClassB::");
	}

	@Test
	@DisplayName("コンストラクタ定義（戻り値型なし）は変換しない")
	void constructorNotConverted() {
		String cpp = """
				MyClass::MyClass() { }
				void MyClass::Method() { }
				""";

		DynamicRuleSpec methodSpec = spec("""
				collect: ABSTRACT_PARAM00 ::
				from: void COLLECTED :: ABSTRACT_PARAM00 ( )
				to: private void ABSTRACT_PARAM00()
				""");

		ConversionResult result = converter.convertSourceThreePass(cpp, ruleSetWithDynamic(methodSpec));

		// コンストラクタは戻り値型なしのため動的ルールにマッチしない
		assertThat(result.getCsCode()).contains("MyClass::MyClass");
		// 通常メソッドは変換される
		assertThat(result.getCsCode()).contains("private void Method()");
	}

	@Test
	@DisplayName("動的ルールなしでは ClassName:: がそのまま残る")
	void withoutDynamicRuleMethodNotConverted() {
		String cpp = "void MyClass::Method() { }";

		ConversionResult result = converter.convertSourceThreePass(cpp,
				new ThreePassRuleSet(List.of(), List.of(), List.of(), List.of(), List.of()));

		assertThat(result.getCsCode()).contains("MyClass::");
		assertThat(result.getCsCode()).doesNotContain("private");
	}

	// =========================================================================
	// COLLECTED が to テンプレートに出現するケース
	// =========================================================================

	@Test
	@DisplayName("to テンプレートの COLLECTED も収集値で置換される（enum 風変換）")
	void collectedInToTemplate() {
		// enum { apple, banana } の banana を収集して (int)banana に変換
		String cpp = "void f() { int x = banana; }";

		DynamicRuleSpec enumSpec = spec("""
				collect: , ABSTRACT_PARAM00 ,
				from: COLLECTED
				to: (int) COLLECTED
				""");

		// collect パターン `, banana ,` にマッチさせるために banana を , で挟む入力
		String cppWithCommas = "enum { apple , banana , cherry }; void f() { int x = banana; }";
		ConversionResult result = converter.convertSourceThreePass(cppWithCommas, ruleSetWithDynamic(enumSpec));

		// banana が収集され、(int) banana に変換される
		assertThat(result.getCsCode()).contains("(int) banana");
	}

	// =========================================================================
	// 動的ルールと静的ルールの共存
	// =========================================================================

	@Test
	@DisplayName("静的 MAIN ルールと動的ルールが同一変換で共存できる")
	void staticAndDynamicRulesCoexist() {
		String cpp = "bool MyClass::IsGood() { return sin(x) > 0; }";

		// 静的ルール: sin → Math.Sin
		var sinRule = ruleLoader
				.loadFromString("from: sin ( ABSTRACT_PARAM00 )\nto: Math.Sin(ABSTRACT_PARAM00)", "sin.rule").get(0);

		DynamicRuleSpec methodSpec = spec("""
				collect: ABSTRACT_PARAM00 ::
				from: bool COLLECTED :: ABSTRACT_PARAM00 ( )
				to: private bool ABSTRACT_PARAM00()
				""");

		ThreePassRuleSet ruleSet = new ThreePassRuleSet(List.of(), List.of(List.of(sinRule)), List.of(), List.of(),
				List.of(methodSpec));

		ConversionResult result = converter.convertSourceThreePass(cpp, ruleSet);

		assertThat(result.getCsCode()).contains("private bool IsGood()");
		assertThat(result.getCsCode()).contains("Math.Sin");
		assertThat(result.getCsCode()).doesNotContain("MyClass::");
	}
}
