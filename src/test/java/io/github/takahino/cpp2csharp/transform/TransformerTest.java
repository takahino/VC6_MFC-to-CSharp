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

package io.github.takahino.cpp2csharp.transform;

import io.github.takahino.cpp2csharp.rule.ConversionRule;
import io.github.takahino.cpp2csharp.rule.ConversionRuleLoader;
import io.github.takahino.cpp2csharp.transform.strategy.RightmostFirstSelectionStrategy;
import io.github.takahino.cpp2csharp.tree.AstNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link Transformer} のユニットテスト。
 * フラットなトークンリストを直接構築して変換処理を検証する。
 */
@DisplayName("Transformer テスト")
class TransformerTest {

    private Transformer transformer;
    private ConversionRuleLoader loader;

    @BeforeEach
    void setUp() {
        transformer = new Transformer();
        loader = new ConversionRuleLoader();
    }

    /**
     * ルール文字列からルールリストを生成するヘルパー。
     */
    private List<ConversionRule> rules(String from, String to) {
        String content = "from: " + from + "\nto: " + to;
        return loader.loadFromString(content, "test.rule");
    }

    /**
     * 複数のルール文字列からルールリストを生成するヘルパー。
     */
    private List<ConversionRule> multiRules(String... fromToAlternating) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fromToAlternating.length - 1; i += 2) {
            sb.append("from: ").append(fromToAlternating[i]).append("\n");
            sb.append("to: ").append(fromToAlternating[i + 1]).append("\n");
        }
        return loader.loadFromString(sb.toString(), "test.rule");
    }

    /**
     * フラットなトークン列から {@code List<AstNode>} を生成するヘルパー。
     */
    private List<AstNode> buildFlatTokenList(String... tokenTexts) {
        List<AstNode> result = new ArrayList<>();
        int col = 0;
        for (String text : tokenTexts) {
            result.add(AstNode.tokenNode(text, 1, col++));
        }
        return result;
    }

    @Test
    @DisplayName("型変換ルールを単純リストに適用できる")
    void testTypeConversionOnFlatGraph() {
        List<AstNode> tokens = buildFlatTokenList("CString", "s", ";");
        List<ConversionRule> r = rules("CString", "string");

        String result = transformer.transform(tokens, r);
        assertThat(result).contains("string");
        assertThat(result).doesNotContain("CString");
    }

    @Test
    @DisplayName("sin 関数変換を単純リストに適用できる")
    void testSinConversionOnFlatGraph() {
        List<AstNode> tokens = buildFlatTokenList("sin", "(", "x", ")");
        List<ConversionRule> r = rules(
                "sin ( ABSTRACT_PARAM00 )",
                "Math.Sin(ABSTRACT_PARAM00)"
        );

        String result = transformer.transform(tokens, r);
        assertThat(result).contains("Math.Sin");
        assertThat(result).contains("x");
    }

    @Test
    @DisplayName("変換後も残りのトークンが保持される")
    void testRemainingTokensPreserved() {
        List<AstNode> tokens = buildFlatTokenList("double", "y", "=", "sin", "(", "x", ")", ";");
        List<ConversionRule> r = rules(
                "sin ( ABSTRACT_PARAM00 )",
                "Math.Sin(ABSTRACT_PARAM00)"
        );

        String result = transformer.transform(tokens, r);
        assertThat(result).contains("double");
        assertThat(result).contains("y");
        assertThat(result).contains("=");
        assertThat(result).contains("Math.Sin");
        assertThat(result).contains(";");
    }

    @Test
    @DisplayName("マッチなしの場合はエラーが記録されない")
    void testNoMatchNoErrors() {
        List<AstNode> tokens = buildFlatTokenList("int", "x", "=", "42", ";");
        List<ConversionRule> r = rules("sin ( ABSTRACT_PARAM00 )", "Math.Sin(ABSTRACT_PARAM00)");

        transformer.transform(tokens, r);
        assertThat(transformer.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("ルールなしの場合はトークンをそのまま結合する")
    void testNoRulesPassthrough() {
        List<AstNode> tokens = buildFlatTokenList("int", "x", "=", "42", ";");

        String result = transformer.transform(tokens, List.of());
        assertThat(result).contains("int");
        assertThat(result).contains("x");
        assertThat(result).contains("42");
        assertThat(result).contains(";");
    }

    @Test
    @DisplayName("ドットチェーン a.Foo().Bar() を内側から正しく変換できる (0引数メソッド)")
    void testDotChainConvertedInsideOut() {
        // トークン列: a . Foo ( ) . Bar ( )
        List<AstNode> tokens = buildFlatTokenList("a", ".", "Foo", "(", ")", ".", "Bar", "(", ")");
        List<ConversionRule> r = multiRules(
                "ABSTRACT_PARAM00 . Foo ( )", "Foo(ABSTRACT_PARAM00)",
                "ABSTRACT_PARAM00 . Bar ( )", "Bar(ABSTRACT_PARAM00)"
        );

        String result = transformer.transform(tokens, r);
        assertThat(result).contains("Bar(Foo(a))");
        assertThat(transformer.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("pow 変換 (2つの ABSTRACT_PARAM) をリストに適用できる")
    void testPowConversionOnFlatGraph() {
        List<AstNode> tokens = buildFlatTokenList("pow", "(", "base", ",", "exp", ")");
        List<ConversionRule> r = rules(
                "pow ( ABSTRACT_PARAM00 , ABSTRACT_PARAM01 )",
                "Math.Pow(ABSTRACT_PARAM00, ABSTRACT_PARAM01)"
        );

        String result = transformer.transform(tokens, r);
        assertThat(result).contains("Math.Pow");
        assertThat(result).contains("base");
        assertThat(result).contains("exp");
    }

    // ── RECEIVER テスト ────────────────────────────────────────────────────

    @Test
    @DisplayName("RECEIVER: 単体識別子レシーバー str.Left(5)")
    void testReceiver00SimpleIdentifier() {
        List<AstNode> tokens = buildFlatTokenList("str", ".", "Left", "(", "5", ")");
        List<ConversionRule> r = rules(
                "RECEIVER . Left ( ABSTRACT_PARAM00 )",
                "RECEIVER . Substring ( 0 , ABSTRACT_PARAM00 )"
        );

        String result = transformer.transform(tokens, r);
        assertThat(result).contains("str");
        assertThat(result).contains("Substring");
        assertThat(result).contains("0");
        assertThat(result).contains("5");
        assertThat(transformer.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("RECEIVER: メンバアクセス連鎖 app.m_str.Left(5)")
    void testReceiver00MemberAccessChain() {
        List<AstNode> tokens = buildFlatTokenList("app", ".", "m_str", ".", "Left", "(", "5", ")");
        List<ConversionRule> r = rules(
                "RECEIVER . Left ( ABSTRACT_PARAM00 )",
                "RECEIVER . Substring ( 0 , ABSTRACT_PARAM00 )"
        );

        String result = transformer.transform(tokens, r);
        assertThat(result).contains("app");
        assertThat(result).contains("m_str");
        assertThat(result).contains("Substring");
        assertThat(transformer.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("RECEIVER: アロー演算子チェーン this->m_str.Left(5)")
    void testReceiver00ArrowChain() {
        List<AstNode> tokens = buildFlatTokenList("this", "->", "m_str", ".", "Left", "(", "5", ")");
        List<ConversionRule> r = rules(
                "RECEIVER . Left ( ABSTRACT_PARAM00 )",
                "RECEIVER . Substring ( 0 , ABSTRACT_PARAM00 )"
        );

        String result = transformer.transform(tokens, r);
        assertThat(result).contains("this");
        assertThat(result).contains("->");
        assertThat(result).contains("m_str");
        assertThat(result).contains("Substring");
        assertThat(transformer.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("RECEIVER: 添字アクセスレシーバー arr[0].Left(5)")
    void testReceiver00SubscriptReceiver() {
        List<AstNode> tokens = buildFlatTokenList("arr", "[", "0", "]", ".", "Left", "(", "5", ")");
        List<ConversionRule> r = rules(
                "RECEIVER . Left ( ABSTRACT_PARAM00 )",
                "RECEIVER . Substring ( 0 , ABSTRACT_PARAM00 )"
        );

        String result = transformer.transform(tokens, r);
        assertThat(result).contains("arr");
        assertThat(result).contains("Substring");
        assertThat(transformer.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("RECEIVER: 変換済み合成トークンをレシーバーにできる（MakeString が単一トークン化された場合）")
    void testReceiver00SyntheticTokenReceiver() {
        List<AstNode> tokens = buildFlatTokenList("MakeString(data)", ".", "Left", "(", "10", ")");
        List<ConversionRule> r = rules(
                "RECEIVER . Left ( ABSTRACT_PARAM00 )",
                "RECEIVER . Substring ( 0 , ABSTRACT_PARAM00 )"
        );

        String result = transformer.transform(tokens, r);
        assertThat(result).contains("MakeString(data)");
        assertThat(result).contains("Substring");
        assertThat(result).contains("10");
        assertThat(transformer.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("RECEIVER: 未変換の関数呼び出し結果もレシーバーとして変換できる")
    void testReceiver00RejectUnconvertedFunctionCallReceiver() {
        List<AstNode> tokens = buildFlatTokenList("MakeString", "(", "data", ")", ".", "Left", "(", "10", ")");
        List<ConversionRule> r = rules(
                "RECEIVER . Left ( ABSTRACT_PARAM00 )",
                "RECEIVER . Substring ( 0 , ABSTRACT_PARAM00 )"
        );

        String result = transformer.transform(tokens, r);
        assertThat(result).contains("Substring");
        assertThat(result).contains("MakeString");
        assertThat(transformer.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("RECEIVER: 関数呼び出し結果に対して Find が変換できる")
    void testReceiver00FunctionCallReceiverWithFind() {
        List<AstNode> tokens = buildFlatTokenList("GetString", "(", "data", ")", ".", "Find", "(", "\"/ \"", ")");
        List<ConversionRule> r = rules(
                "RECEIVER . Find ( ABSTRACT_PARAM00 )",
                "MigrationHelper.Find(RECEIVER, ABSTRACT_PARAM00)"
        );

        String result = transformer.transform(tokens, r);
        assertThat(result).contains("MigrationHelper.Find");
        assertThat(result).contains("GetString");
        assertThat(transformer.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("RECEIVER: 多段メソッドチェーン app.method().field.Left(5)")
    void testReceiver00MultiStepChain() {
        List<AstNode> tokens = buildFlatTokenList("app", ".", "method", "(", ")", ".", "field", ".", "Left", "(", "5", ")");
        List<ConversionRule> r = rules(
                "RECEIVER . Left ( ABSTRACT_PARAM00 )",
                "RECEIVER . Substring ( 0 , ABSTRACT_PARAM00 )"
        );

        String result = transformer.transform(tokens, r);
        assertThat(result).contains("app");
        assertThat(result).contains("Substring");
        assertThat(transformer.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("RECEIVER: 拒否系 - a+b はレシーバーにならない（b.Left のみ変換）")
    void testReceiver00RejectBinaryOpAtDepthZero() {
        List<AstNode> tokens = buildFlatTokenList("a", "+", "b", ".", "Left", "(", "5", ")");
        List<ConversionRule> r = rules(
                "RECEIVER . Left ( ABSTRACT_PARAM00 )",
                "RECEIVER . Substring ( 0 , ABSTRACT_PARAM00 )"
        );

        String result = transformer.transform(tokens, r);
        assertThat(result).contains("Substring");
        assertThat(result).contains("a");
        assertThat(result).contains("+");
        assertThat(transformer.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("RECEIVER: RECEIVER をto側にも展開できる")
    void testReceiver00InToTemplate() {
        List<AstNode> tokens = buildFlatTokenList("str", ".", "Find", "(", "x", ")");
        List<ConversionRule> r = rules(
                "RECEIVER . Find ( ABSTRACT_PARAM00 )",
                "MigrationHelper.Find(RECEIVER, ABSTRACT_PARAM00)"
        );

        String result = transformer.transform(tokens, r);
        assertThat(result).contains("MigrationHelper.Find(str, x)");
        assertThat(transformer.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("RECEIVER: this.m_str.Find(x) が変換される（従来 ABSTRACT_PARAM では不可）")
    void testReceiver00DotChainFindPreviouslyBlocked() {
        List<AstNode> tokens = buildFlatTokenList("this", ".", "m_str", ".", "Find", "(", "x", ")");
        List<ConversionRule> r = rules(
                "RECEIVER . Find ( ABSTRACT_PARAM00 )",
                "MigrationHelper.Find(RECEIVER, ABSTRACT_PARAM00)"
        );

        String result = transformer.transform(tokens, r);
        assertThat(result).contains("MigrationHelper.Find");
        assertThat(result).contains("this");
        assertThat(result).contains("m_str");
        assertThat(transformer.getErrors()).isEmpty();
    }

    // ── 選択戦略・可視化テスト ────────────────────────────────────────────────────

    @Test
    @DisplayName("RightmostFirstSelectionStrategy: 適用ログに選択戦略が記録される")
    void testSelectionStrategyRecordedInAppliedTransforms() {
        Transformer rightmostTransformer = new Transformer(50000, new RightmostFirstSelectionStrategy());
        List<AstNode> tokens = buildFlatTokenList("sin", "(", "x", ")");
        List<ConversionRule> r = rules(
                "sin ( ABSTRACT_PARAM00 )",
                "Math.Sin(ABSTRACT_PARAM00)"
        );

        rightmostTransformer.transform(tokens, r);

        assertThat(rightmostTransformer.getAppliedTransforms()).hasSize(1);
        var t = rightmostTransformer.getAppliedTransforms().get(0);
        assertThat(t.selectedStrategy()).isEqualTo("RightmostFirstSelectionStrategy");
        assertThat(t.fallbackFrom()).isNull();
        assertThat(t.selectionReason()).isNotEmpty();
    }

    @Test
    @DisplayName("RightmostFirstSelectionStrategy: sin(x)+sin(y) で右端の sin(y) が先に変換される")
    void testRightmostFirstTieBreakByPosition() {
        Transformer rmFirst = new Transformer(50000, new RightmostFirstSelectionStrategy());
        List<AstNode> tokens = buildFlatTokenList("sin", "(", "x", ")", "+", "sin", "(", "y", ")");
        List<ConversionRule> r = rules(
                "sin ( ABSTRACT_PARAM00 )",
                "Math.Sin(ABSTRACT_PARAM00)"
        );

        String result = rmFirst.transform(tokens, r);
        assertThat(result).contains("Math.Sin");
        assertThat(result).contains("x");
        assertThat(result).contains("y");
        assertThat(rmFirst.getErrors()).isEmpty();
        assertThat(rmFirst.getAppliedTransforms())
                .allMatch(t -> "RightmostFirstSelectionStrategy".equals(t.selectedStrategy()));
        assertThat(rmFirst.getAppliedTransforms())
                .allMatch(t -> t.fallbackFrom() == null);
    }

    // ── hasDotWithParenAtDepthZero 検証（ABSTRACT_PARAM00 先頭ルール）────────────────────────────
    //
    // 【背景】RightmostFirstSelectionStrategy.passesLeadingAbstractParamFilter は、
    // 先頭トークンが ABSTRACT_PARAM00 のルールにのみ適用される。その中の (b) 条件として
    // hasDotWithParenAtDepthZero があり、「キャプチャ内で深さ0に . と ( の両方がある」場合に
    // そのマッチを拒否する。目的は「time.Format(...).Foo()」のようなドットチェーン内の
    // 関数呼び出しを ABSTRACT_PARAM で丸ごとキャプチャさせず、RECEIVER ルールで内側から
    // 変換させること。「this.m_str」のような単純メンバアクセスは . のみで ( がないため許可。
    //
    // 【ルールファイルを使わない理由】現行ルールセットには先頭が ABSTRACT_PARAM00 のルールが
    // 存在しないため、このフィルタは実質デッドコード。将来用・防御的実装として残しており、
    // 検証には loadFromString でテスト専用ルールを生成する。

    @Test
    @DisplayName("ABSTRACT_PARAM00先頭: 単一トークン obj は許可（hasDotWithParenAtDepthZero=false）")
    void testLeadingAbstractParamAllowsSimpleMemberAccess() {
        // キャプチャ "obj" には . も ( も含まない → hasDotWithParenAtDepthZero=false → 許可。
        // 単一トークンにしたのは、this.m_str.Foo() だと右端優先で m_str.Foo() が選ばれ
        // 本フィルタの検証にならないため。
        List<AstNode> tokens = buildFlatTokenList("obj", ".", "Foo", "(", ")");
        List<ConversionRule> r = rules(
                "ABSTRACT_PARAM00 . Foo ( )",
                "Foo(ABSTRACT_PARAM00)"
        );

        String result = transformer.transform(tokens, r);

        assertThat(result).contains("Foo(obj)");
        assertThat(transformer.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("ABSTRACT_PARAM00先頭: time.Format(...) は拒否し Format が先に変換される（hasDotWithParenAtDepthZero=true）")
    void testLeadingAbstractParamRejectsDotChainWithFunctionCall() {
        // 入力: time.Format("%Y/%m/%d").Foo()
        // Foo ルールの ABSTRACT_PARAM00 が "time.Format("%Y/%m/%d")" をキャプチャしようとするが、
        // 深さ0で . と ( の両方がある → hasDotWithParenAtDepthZero=true → 拒否。
        // その結果 Format ルールが先に適用され、2パス目で Foo が MigrationHelper.Format(...) に適用。
        // 期待: Foo(MigrationHelper.Format(time, "%Y/%m/%d")) のような内側から正しい変換順序。
        List<AstNode> tokens = buildFlatTokenList(
                "time", ".", "Format", "(", "\"%Y/%m/%d\"", ")", ".", "Foo", "(", ")"
        );
        List<ConversionRule> r = multiRules(
                "RECEIVER . Format ( ABSTRACT_PARAM00 )", "MigrationHelper.Format(RECEIVER, ABSTRACT_PARAM00)",
                "ABSTRACT_PARAM00 . Foo ( )", "Foo(ABSTRACT_PARAM00)"
        );

        String result = transformer.transform(tokens, r);

        assertThat(result).contains("MigrationHelper.Format");
        assertThat(result).contains("Foo(");
        assertThat(result).contains("time");
        assertThat(transformer.getErrors()).isEmpty();
    }
}
