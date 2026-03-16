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
import io.github.takahino.cpp2csharp.rule.ConversionRule;
import io.github.takahino.cpp2csharp.rule.ConversionRuleLoader;
import io.github.takahino.cpp2csharp.rule.ConversionRuleLoader.ThreePassRuleSet;
import io.github.takahino.cpp2csharp.rule.ConversionToken;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * 外部提供値からの動的ルール生成テスト。
 *
 * <p>VC++6 の enum はすべて int 固定のため、int との直接比較・代入が可能だった。
 * C# 化すると明示的キャストが必要になる。</p>
 *
 * <p>別処理（字句解析・別ファイル解析）で収集した enum メンバ名を
 * {@link DynamicRuleGenerator#generateFromValues} に渡し、
 * キャストルールを動的生成する振る舞いを検証する。</p>
 */
@DisplayName("外部値からの動的ルール生成テスト（enum キャスト変換）")
class DynamicRuleFromExternalValuesTest {

    private CppToCSharpConverter converter;
    private DynamicRuleGenerator generator;

    @BeforeEach
    void setUp() {
        converter = new CppToCSharpConverter(false);
        generator = new DynamicRuleGenerator(new ConversionRuleLoader());
    }

    /** 外部値リスト + テンプレートからルールを生成して 3パス変換を実行するヘルパー。 */
    private ConversionResult convertWithExternalValues(
            String cpp,
            List<String> enumMembers,
            List<DynamicRuleSpec.FromToTemplate> templates) {
        List<ConversionRule> rules = generator.generateFromValues(enumMembers, templates, "enum-cast");
        ThreePassRuleSet ruleSet = new ThreePassRuleSet(
                List.of(),
                List.of(rules),
                List.of(),
                List.of(),
                List.of()
        );
        return converter.convertSourceThreePass(cpp, ruleSet);
    }

    // =========================================================================
    // ルール生成の単体検証
    // =========================================================================

    @Test
    @DisplayName("外部値リストからルールが正しい件数生成される")
    void generateCorrectNumberOfRules() {
        List<String> enumMembers = List.of("apple", "banana", "cherry");
        List<DynamicRuleSpec.FromToTemplate> templates = List.of(
                new DynamicRuleSpec.FromToTemplate("COLLECTED", "(int) COLLECTED")
        );

        List<ConversionRule> rules = generator.generateFromValues(enumMembers, templates, "fruit-enum");

        // 3値 × 1テンプレート = 3ルール
        assertThat(rules).hasSize(3);
    }

    @Test
    @DisplayName("生成ルールの from パターンに具体値が含まれ COLLECTED が除去される")
    void fromPatternContainsConcreteValue() {
        List<String> enumMembers = List.of("apple");
        List<DynamicRuleSpec.FromToTemplate> templates = List.of(
                new DynamicRuleSpec.FromToTemplate("COLLECTED", "(int) COLLECTED")
        );

        List<ConversionRule> rules = generator.generateFromValues(enumMembers, templates, "test");

        assertThat(rules).hasSize(1);
        List<String> fromValues = rules.get(0).getFromTokens().stream()
                .map(ConversionToken::getValue).toList();
        assertThat(fromValues).contains("apple");
        assertThat(fromValues).doesNotContain("COLLECTED");
    }

    @Test
    @DisplayName("生成ルールの to テンプレートに具体値が含まれ COLLECTED が除去される")
    void toTemplateContainsConcreteValue() {
        List<String> enumMembers = List.of("banana");
        List<DynamicRuleSpec.FromToTemplate> templates = List.of(
                new DynamicRuleSpec.FromToTemplate("COLLECTED", "(int) COLLECTED")
        );

        List<ConversionRule> rules = generator.generateFromValues(enumMembers, templates, "test");

        assertThat(rules.get(0).getToTemplate()).isEqualTo("(int) banana");
        assertThat(rules.get(0).getToTemplate()).doesNotContain("COLLECTED");
    }

    @Test
    @DisplayName("複数テンプレートがある場合: 1値 × N テンプレート = N ルール")
    void multipleTemplatesPerValue() {
        List<String> enumMembers = List.of("apple");
        List<DynamicRuleSpec.FromToTemplate> templates = List.of(
                new DynamicRuleSpec.FromToTemplate("COLLECTED", "(FruitKind) COLLECTED"),
                new DynamicRuleSpec.FromToTemplate("= COLLECTED ;", "= (FruitKind) COLLECTED ;")
        );

        List<ConversionRule> rules = generator.generateFromValues(enumMembers, templates, "test");

        assertThat(rules).hasSize(2);
    }

    @Test
    @DisplayName("外部値が空の場合はルールを生成しない")
    void emptyExternalValuesProducesNoRules() {
        List<DynamicRuleSpec.FromToTemplate> templates = List.of(
                new DynamicRuleSpec.FromToTemplate("COLLECTED", "(int) COLLECTED")
        );

        List<ConversionRule> rules = generator.generateFromValues(List.of(), templates, "test");

        assertThat(rules).isEmpty();
    }

    @Test
    @DisplayName("テンプレートが空の場合はルールを生成しない")
    void emptyTemplatesProducesNoRules() {
        List<ConversionRule> rules = generator.generateFromValues(
                List.of("apple", "banana"), List.of(), "test");

        assertThat(rules).isEmpty();
    }

    // =========================================================================
    // 変換パイプライン統合検証
    // =========================================================================

    @Test
    @DisplayName("int との比較で使われる enum メンバがキャストに変換される")
    void enumMemberInComparisonGetsIntCast() {
        String cpp = "void f() { if ( nSel == apple ) { } }";

        // 外部解析で収集した enum メンバ
        List<String> enumMembers = List.of("apple", "banana", "cherry");
        List<DynamicRuleSpec.FromToTemplate> templates = List.of(
                new DynamicRuleSpec.FromToTemplate("COLLECTED", "(int) COLLECTED")
        );

        ConversionResult result = convertWithExternalValues(cpp, enumMembers, templates);

        assertThat(result.getCsCode()).contains("(int) apple");
        // "(int) apple" の中に "apple" は残るが、先頭に "(int) " が付いていること
        assertThat(result.getCsCode()).doesNotContain("== apple");
        assertThat(result.getTransformErrors()).isEmpty();
    }

    @Test
    @DisplayName("int への代入で使われる enum メンバがキャストに変換される")
    void enumMemberInAssignmentGetsIntCast() {
        String cpp = "void f() { int n = banana ; }";

        List<String> enumMembers = List.of("apple", "banana", "cherry");
        List<DynamicRuleSpec.FromToTemplate> templates = List.of(
                new DynamicRuleSpec.FromToTemplate("COLLECTED", "(int) COLLECTED")
        );

        ConversionResult result = convertWithExternalValues(cpp, enumMembers, templates);

        assertThat(result.getCsCode()).contains("(int) banana");
    }

    @Test
    @DisplayName("収集されていない識別子は変換されない")
    void nonEnumIdentifierNotConverted() {
        String cpp = "void f() { int n = pear ; }";

        // pear は列挙に含まれない
        List<String> enumMembers = List.of("apple", "banana", "cherry");
        List<DynamicRuleSpec.FromToTemplate> templates = List.of(
                new DynamicRuleSpec.FromToTemplate("COLLECTED", "(int) COLLECTED")
        );

        ConversionResult result = convertWithExternalValues(cpp, enumMembers, templates);

        assertThat(result.getCsCode()).contains("pear");
        assertThat(result.getCsCode()).doesNotContain("(int) pear");
    }

    @Test
    @DisplayName("複数の enum メンバが同一コード内に存在する場合、それぞれ変換される")
    void multipleEnumMembersInSameCode() {
        String cpp = "void f() { if ( x == apple ) { n = banana ; } }";

        List<String> enumMembers = List.of("apple", "banana", "cherry");
        List<DynamicRuleSpec.FromToTemplate> templates = List.of(
                new DynamicRuleSpec.FromToTemplate("COLLECTED", "(int) COLLECTED")
        );

        ConversionResult result = convertWithExternalValues(cpp, enumMembers, templates);

        assertThat(result.getCsCode()).contains("(int) apple");
        assertThat(result.getCsCode()).contains("(int) banana");
    }

    @Test
    @DisplayName("enum型名付きキャストテンプレート: (FruitKind) に変換される")
    void enumTypeNamedCast() {
        String cpp = "void f() { if ( x == cherry ) { } }";

        List<String> enumMembers = List.of("apple", "banana", "cherry");
        // int ではなく enum 型名付きキャスト
        List<DynamicRuleSpec.FromToTemplate> templates = List.of(
                new DynamicRuleSpec.FromToTemplate("COLLECTED", "(FruitKind) COLLECTED")
        );

        ConversionResult result = convertWithExternalValues(cpp, enumMembers, templates);

        assertThat(result.getCsCode()).contains("(FruitKind) cherry");
    }

    @Test
    @DisplayName("外部値ルールと静的ルールが共存して両方適用される")
    void externalValuesAndStaticRulesCoexist() {
        // sin と apple を別文に分けることで、フェーズ1の sin→Math.Sin 変換後も
        // apple が独立トークンとして残りフェーズ2の enum キャストが適用される
        String cpp = "void f() { double d = sin ( x ) ; int n = apple ; }";

        // 静的ルール: sin → Math.Sin
        ConversionRule sinRule = new ConversionRuleLoader().loadFromString(
                "from: sin ( ABSTRACT_PARAM00 )\nto: Math.Sin(ABSTRACT_PARAM00)", "sin.rule"
        ).get(0);

        // 外部値ルール: apple → (int) apple
        List<ConversionRule> enumRules = generator.generateFromValues(
                List.of("apple", "banana"),
                List.of(new DynamicRuleSpec.FromToTemplate("COLLECTED", "(int) COLLECTED")),
                "fruit-enum"
        );

        ThreePassRuleSet ruleSet = new ThreePassRuleSet(
                List.of(),
                List.of(List.of(sinRule), enumRules),  // 2フェーズ
                List.of(),
                List.of(),
                List.of()
        );

        ConversionResult result = converter.convertSourceThreePass(cpp, ruleSet);

        // sin は Math.Sin に変換、apple は (int) apple に変換
        assertThat(result.getCsCode()).contains("Math.Sin");
        assertThat(result.getCsCode()).contains("(int) apple");
    }
}
