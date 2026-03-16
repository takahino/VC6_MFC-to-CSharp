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

package io.github.takahino.cpp2csharp.rule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link ConversionRuleLoader} のユニットテスト。
 */
@DisplayName("ConversionRuleLoader テスト")
class ConversionRuleLoaderTest {

    private final ConversionRuleLoader loader = new ConversionRuleLoader();

    @Test
    @DisplayName("基本的なルールを正しく読み込める")
    void testBasicRule() {
        String content = """
                from: this . AfxMessageBox ( ABSTRACT_PARAM00 , MB_OK | MB_ICONERROR ) ;
                to: MessageBox.Show(ABSTRACT_PARAM00, "", MessageBoxButtons.OK, MessageBoxIcon.Error);
                """;
        List<ConversionRule> rules = loader.loadFromString(content, "test.rule");
        assertThat(rules).hasSize(1);

        ConversionRule rule = rules.get(0);
        // this . AfxMessageBox ( ABSTRACT_PARAM00 , MB_OK | MB_ICONERROR ) ; = 11トークン
        assertThat(rule.getFromTokens()).hasSize(11);
        assertThat(rule.getToTemplate())
                .isEqualTo("MessageBox.Show(ABSTRACT_PARAM00, \"\", MessageBoxButtons.OK, MessageBoxIcon.Error);");
        assertThat(rule.getSourceFile()).isEqualTo("test.rule");
    }

    @Test
    @DisplayName("コメント行と空行を無視できる")
    void testIgnoreCommentsAndBlanks() {
        String content = """
                # これはコメント

                # 別のコメント
                from: sin ( ABSTRACT_PARAM00 )
                to: Math.Sin(ABSTRACT_PARAM00)

                """;
        List<ConversionRule> rules = loader.loadFromString(content, "test.rule");
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getFromTokens().get(0).getValue()).isEqualTo("sin");
    }

    @Test
    @DisplayName("複数のルールを正しく読み込める")
    void testMultipleRules() {
        String content = """
                from: sin ( ABSTRACT_PARAM00 )
                to: Math.Sin(ABSTRACT_PARAM00)

                from: cos ( ABSTRACT_PARAM00 )
                to: Math.Cos(ABSTRACT_PARAM00)

                from: pow ( ABSTRACT_PARAM00 , ABSTRACT_PARAM01 )
                to: Math.Pow(ABSTRACT_PARAM00, ABSTRACT_PARAM01)
                """;
        List<ConversionRule> rules = loader.loadFromString(content, "math.rule");
        assertThat(rules).hasSize(3);
        assertThat(rules.get(0).getFromTokens().get(0).getValue()).isEqualTo("sin");
        assertThat(rules.get(1).getFromTokens().get(0).getValue()).isEqualTo("cos");
        assertThat(rules.get(2).getFromTokens().get(0).getValue()).isEqualTo("pow");
    }

    @Test
    @DisplayName("ABSTRACT_PARAM を正しくトークン化できる")
    void testAbstractParamTokenization() {
        String content = """
                from: pow ( ABSTRACT_PARAM00 , ABSTRACT_PARAM01 )
                to: Math.Pow(ABSTRACT_PARAM00, ABSTRACT_PARAM01)
                """;
        List<ConversionRule> rules = loader.loadFromString(content, "test.rule");
        List<ConversionToken> tokens = rules.get(0).getFromTokens();

        // pow ( ABSTRACT_PARAM00 , ABSTRACT_PARAM01 )
        assertThat(tokens).hasSize(6);
        assertThat(tokens.get(0).isAbstractParam()).isFalse();
        assertThat(tokens.get(2).isAbstractParam()).isTrue();
        assertThat(tokens.get(2).getParamIndex()).isEqualTo(0);
        assertThat(tokens.get(4).isAbstractParam()).isTrue();
        assertThat(tokens.get(4).getParamIndex()).isEqualTo(1);
    }

    @Test
    @DisplayName("型変換ルール (具体トークンのみ) を正しく読み込める")
    void testTypeConversionRule() {
        String content = """
                from: CString
                to: string
                """;
        List<ConversionRule> rules = loader.loadFromString(content, "types.rule");
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getFromTokens()).hasSize(1);
        assertThat(rules.get(0).getFromTokens().get(0).getValue()).isEqualTo("CString");
        assertThat(rules.get(0).getToTemplate()).isEqualTo("string");
    }

    @Test
    @DisplayName("getAbstractParamCount が正しいカウントを返す")
    void testAbstractParamCount() {
        String content = """
                from: pow ( ABSTRACT_PARAM00 , ABSTRACT_PARAM01 )
                to: Math.Pow(ABSTRACT_PARAM00, ABSTRACT_PARAM01)
                """;
        List<ConversionRule> rules = loader.loadFromString(content, "test.rule");
        assertThat(rules.get(0).getAbstractParamCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("getArgumentCount が括弧内カンマ数から引数個数を導出する")
    void testGetArgumentCount() {
        // 1引数 (カンマなし)
        ConversionRule r1 = loader.loadFromString(
                "from: AfxMessageBox ( ABSTRACT_PARAM00 ) ;\nto: MessageBox.Show(ABSTRACT_PARAM00);",
                "test.rule").get(0);
        assertThat(r1.getArgumentCount()).isEqualTo(1);

        // 2引数 (カンマ1つ)
        ConversionRule r2 = loader.loadFromString(
                "from: AfxMessageBox ( ABSTRACT_PARAM00 , MB_OK | MB_ICONERROR ) ;\nto: x;",
                "test.rule").get(0);
        assertThat(r2.getArgumentCount()).isEqualTo(2);

        // 2引数 (MB_YESNO)
        ConversionRule r3 = loader.loadFromString(
                "from: AfxMessageBox ( ABSTRACT_PARAM00 , MB_YESNO | MB_ICONQUESTION )\nto: x",
                "test.rule").get(0);
        assertThat(r3.getArgumentCount()).isEqualTo(2);

        // 括弧なし (型変換) → -1
        ConversionRule r4 = loader.loadFromString("from: CString\nto: string", "test.rule").get(0);
        assertThat(r4.getArgumentCount()).isEqualTo(-1);

        // 空括弧 → 0
        ConversionRule r5 = loader.loadFromString(
                "from: func ( ) ;\nto: x;",
                "test.rule").get(0);
        assertThat(r5.getArgumentCount()).isEqualTo(0);

        // ネストした括弧 (f ( a , g ( x , y ) ))
        ConversionRule r6 = loader.loadFromString(
                "from: f ( ABSTRACT_PARAM00 , g ( x , y ) )\nto: x",
                "test.rule").get(0);
        assertThat(r6.getArgumentCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("ANTLR トークン化: スペースなしでも正しく分割される")
    void testAntlrTokenizationWithoutSpaces() {
        String content = """
                from: AfxMessageBox(ABSTRACT_PARAM00,MB_OK|MB_ICONERROR);
                to: MessageBox.Show(ABSTRACT_PARAM00, "", MessageBoxButtons.OK, MessageBoxIcon.Error);
                """;
        List<ConversionRule> rules = loader.loadFromString(content, "test.rule");
        assertThat(rules).hasSize(1);
        List<ConversionToken> tokens = rules.get(0).getFromTokens();
        assertThat(tokens).extracting(ConversionToken::getValue)
                .containsExactly("AfxMessageBox", "(", "ABSTRACT_PARAM00", ",", "MB_OK", "|", "MB_ICONERROR", ")", ";");
    }

    @Test
    @DisplayName("不正な from パターンで字句解析エラー")
    void testInvalidFromPatternThrows() {
        assertThatThrownBy(() -> loader.loadFromString(
                "from: @invalid@\nto: x",
                "test.rule"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("字句解析に失敗");
    }

    @Test
    @DisplayName("test: と assrt: を正しく読み込める")
    void testTestAndAssrtParsing() {
        String content = """
                from: AfxMessageBox ( ABSTRACT_PARAM00 , MB_OK ) ;
                to: MessageBox.Show(ABSTRACT_PARAM00, "", MessageBoxButtons.OK, MessageBoxIcon.None);
                test: void f() { AfxMessageBox("Hello", MB_OK); }
                assrt: void f ( ) { MessageBox.Show("Hello", "", MessageBoxButtons.OK, MessageBoxIcon.None); }
                """;
        List<ConversionRule> rules = loader.loadFromString(content, "test.rule");
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getTestCases()).hasSize(1);
        assertThat(rules.get(0).getTestCases().get(0).testInput())
                .isEqualTo("void f() { AfxMessageBox(\"Hello\", MB_OK); }");
        assertThat(rules.get(0).getTestCases().get(0).expectedOutput())
                .isEqualTo("void f ( ) { MessageBox.Show(\"Hello\", \"\", MessageBoxButtons.OK, MessageBoxIcon.None); }");
    }

    @Test
    @DisplayName("test: と assrt: を複数読み込める")
    void testMultipleTestAssrtPairs() {
        String content = """
                from: sin ( ABSTRACT_PARAM00 )
                to: Math.Sin(ABSTRACT_PARAM00)
                test: void f() { sin(1.0); }
                assrt: void f ( ) { Math.Sin ( 1.0 ) ; }
                test: void g() { sin(x); }
                assrt: void g ( ) { Math.Sin ( x ) ; }
                """;
        List<ConversionRule> rules = loader.loadFromString(content, "math.rule");
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getTestCases()).hasSize(2);
        assertThat(rules.get(0).getTestCases().get(0).testInput()).isEqualTo("void f() { sin(1.0); }");
        assertThat(rules.get(0).getTestCases().get(1).testInput()).isEqualTo("void g() { sin(x); }");
    }
}
