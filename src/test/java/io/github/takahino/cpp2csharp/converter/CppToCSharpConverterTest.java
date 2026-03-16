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

package io.github.takahino.cpp2csharp.converter;

import io.github.takahino.cpp2csharp.rule.ConversionRule;
import io.github.takahino.cpp2csharp.rule.ConversionRuleLoader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link CppToCSharpConverter} の統合テスト。
 * 実際の C++ コードをパースして変換ルールを適用するシナリオをテストする。
 */
@DisplayName("CppToCSharpConverter 統合テスト")
class CppToCSharpConverterTest {

    private CppToCSharpConverter converter;
    private ConversionRuleLoader loader;

    @BeforeEach
    void setUp() {
        // 各テストで 1 converter を使用。マルチスレッド化時は converter を共有しないこと。
        converter = new CppToCSharpConverter();
        loader = new ConversionRuleLoader();
    }

    /**
     * ルール文字列からルールリストを生成するヘルパー。
     */
    private List<ConversionRule> rules(String... fromToPairs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fromToPairs.length; i += 2) {
            sb.append("from: ").append(fromToPairs[i]).append("\n");
            sb.append("to: ").append(fromToPairs[i + 1]).append("\n\n");
        }
        return loader.loadFromString(sb.toString(), "test.rule");
    }

    @Test
    @DisplayName("型変換: CString → string")
    void testTypeConversionCString() {
        // C++ の変数宣言を含む最小コード (翻訳単位として認識できる形)
        String cpp = "CString s;";
        List<ConversionRule> rules = rules("CString", "string");

        ConversionResult result = converter.convertSource(cpp, rules);
        assertThat(result.getCsCode()).contains("string");
        assertThat(result.getCsCode()).doesNotContain("CString");
    }

    @Test
    @DisplayName("sin 関数の変換")
    void testSinFunctionConversion() {
        String cpp = "double y = sin(x);";
        List<ConversionRule> rules = rules(
                "sin ( ABSTRACT_PARAM00 )", "Math.Sin(ABSTRACT_PARAM00)"
        );

        ConversionResult result = converter.convertSource(cpp, rules);
        assertThat(result.getCsCode()).contains("Math.Sin");
        assertThat(result.getCsCode()).doesNotContain("sin (");
    }

    @Test
    @DisplayName("pow 関数の変換 (2つの ABSTRACT_PARAM)")
    void testPowFunctionConversion() {
        String cpp = "double z = pow(x, 2.0);";
        List<ConversionRule> rules = rules(
                "pow ( ABSTRACT_PARAM00 , ABSTRACT_PARAM01 )",
                "Math.Pow(ABSTRACT_PARAM00, ABSTRACT_PARAM01)"
        );

        ConversionResult result = converter.convertSource(cpp, rules);
        assertThat(result.getCsCode()).contains("Math.Pow");
    }

    @Test
    @DisplayName("AfxMessageBox の変換 (ABSTRACT_PARAM に単純文字列リテラル)")
    void testAfxMessageBoxSimple() {
        String cpp = """
                void f() {
                    AfxMessageBox("エラーです", MB_OK | MB_ICONERROR);
                }
                """;
        List<ConversionRule> rules = rules(
                "AfxMessageBox ( ABSTRACT_PARAM00 , MB_OK | MB_ICONERROR ) ;",
                "MessageBox.Show(ABSTRACT_PARAM00, \"\", MessageBoxButtons.OK, MessageBoxIcon.Error);"
        );

        ConversionResult result = converter.convertSource(cpp, rules);
        assertThat(result.getCsCode()).contains("MessageBox.Show");
        assertThat(result.getCsCode()).contains("MessageBoxButtons.OK");
        assertThat(result.getCsCode()).contains("MessageBoxIcon.Error");
    }

    @Test
    @DisplayName("NULL → null の変換")
    void testNullConversion() {
        String cpp = "void* p = NULL;";
        List<ConversionRule> rules = rules("NULL", "null");

        ConversionResult result = converter.convertSource(cpp, rules);
        assertThat(result.getCsCode()).contains("null");
        assertThat(result.getCsCode()).doesNotContain("NULL");
    }

    @Test
    @DisplayName("変換ルールがない場合は元トークンをそのまま出力する")
    void testNoRulesPassthrough() {
        String cpp = "int x = 42;";
        List<ConversionRule> rules = List.of();

        ConversionResult result = converter.convertSource(cpp, rules);
        assertThat(result.getCsCode()).contains("int");
        assertThat(result.getCsCode()).contains("x");
        assertThat(result.getCsCode()).contains("42");
    }

    @Test
    @DisplayName("複数のルールが順次適用される")
    void testMultipleRulesApplied() {
        String cpp = "CString s = NULL;";
        List<ConversionRule> rules = rules(
                "CString", "string",
                "NULL", "null"
        );

        ConversionResult result = converter.convertSource(cpp, rules);
        assertThat(result.getCsCode()).contains("string");
        assertThat(result.getCsCode()).contains("null");
        assertThat(result.getCsCode()).doesNotContain("CString");
        assertThat(result.getCsCode()).doesNotContain("NULL");
    }

    @Test
    @DisplayName("タスク2: #include / #define が出力に書き戻される")
    void testPreprocessorDirectivesPreserved() {
        String cpp = """
                #include "stdafx.h"
                #define MAX 100
                int x = 42;
                """;
        ConversionResult result = converter.convertSource(cpp, List.of());

        assertThat(result.getCsCode()).contains("#include \"stdafx.h\"");
        assertThat(result.getCsCode()).contains("#define MAX 100");
    }

    @Test
    @DisplayName("タスク3: 変換結果の末尾に <EOF> が含まれない")
    void testEofNotInOutput() {
        String cpp = "int x = 42;";
        ConversionResult result = converter.convertSource(cpp, List.of());

        assertThat(result.getCsCode()).doesNotContain("<EOF>");
    }

    @Test
    @DisplayName("タスク4: 改行が出力に保持される")
    void testNewlinesPreserved() {
        String cpp = """
                void f() {
                    int x = 1;
                }
                """;
        ConversionResult result = converter.convertSource(cpp, List.of());

        assertThat(result.getCsCode()).contains("\n");
    }
}
