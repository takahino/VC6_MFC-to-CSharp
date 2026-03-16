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
import io.github.takahino.cpp2csharp.rule.ConversionRule;
import io.github.takahino.cpp2csharp.rule.ConversionRuleLoader;
import io.github.takahino.cpp2csharp.transform.Transformer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * 実際の C++ ファイルを使った統合テスト。
 *
 * <p>{@code src/test/resources/cpp/} 配下の C++ ファイルを読み込み、
 * 変換ルールを適用した結果を検証する。</p>
 *
 * <h2>テスト対象ファイル</h2>
 * <ul>
 *   <li>{@code test_messages.cpp} — AfxMessageBox → MessageBox.Show</li>
 *   <li>{@code test_math.cpp}     — 数学関数 → System.Math</li>
 *   <li>{@code test_types.cpp}    — MFC型 → C# 型</li>
 *   <li>{@code test_compound.cpp} — 複合パターン (複数ルールの混在)</li>
 * </ul>
 */
@DisplayName("統合テスト: 実際の C++ ファイルを使った変換検証")
class IntegrationTest {

    private CppToCSharpConverter converter;
    private ConversionRuleLoader loader;

    @BeforeEach
    void setUp() {
        // 各テストで 1 converter を使用。マルチスレッド化時は converter を共有しないこと。
        converter = new CppToCSharpConverter();
        loader = new ConversionRuleLoader();
    }

    // =========================================================================
    // ヘルパーメソッド
    // =========================================================================

    /**
     * テストリソースの C++ ファイルを文字列として読み込む。
     *
     * @param fileName cpp/ 配下のファイル名
     * @return ファイル内容文字列
     * @throws IOException ファイルが見つからない場合
     */
    private String loadCppFile(String fileName) throws IOException {
        String path = "cpp/" + fileName;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("テストリソースが見つかりません: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * クラスパス上の .rule ファイルを読み込む。
     *
     * @param ruleFileName rules/ 配下のファイル名
     * @return ルールリスト
     * @throws IOException 読み込み失敗時
     */
    /** フェーズ別ルールのベースパス（[02]_標準置き換え） */
    private static final String RULES_PHASE_PATH = "rules/main/[02]_標準置き換え/";

    private List<ConversionRule> loadRuleFile(String ruleFileName) throws IOException {
        return loader.loadFromResource(RULES_PHASE_PATH + ruleFileName);
    }

    /**
     * 変換後コードからコメント部分を除去した文字列を返す。
     *
     * <p>コメント保持機能により出力にコメントが含まれる場合でも、
     * コード本体部分のみに対してアサーションを行うために使用する。</p>
     *
     * @param code 変換後の C# コード文字列
     * @return 行コメント・ブロックコメントを除去した文字列
     */
    private String stripComments(String code) {
        // ブロックコメントを除去
        String noBlock = code.replaceAll("/\\*.*?\\*/", "");
        // 行コメントを除去
        return noBlock.replaceAll("//[^\n]*", "");
    }

    @Test
    @DisplayName("パス上限: maxPasses=10 で 15 個の INT は 10 個のみ変換され、上限到達エラーとなる")
    void testPassLimitReached() throws IOException {
        String cpp = "void f() { INT a0; INT a1; INT a2; INT a3; INT a4; INT a5; INT a6; INT a7; INT a8; INT a9; INT a10; INT a11; INT a12; INT a13; INT a14; }";

        // Transformer 注入のテスト。各 converter は専用 Transformer を持つ。
        Transformer transformer = new Transformer(10);
        CppToCSharpConverter customConverter = new CppToCSharpConverter(transformer);
        List<ConversionRule> rules = loadRuleFile("type_conversions.rule");

        ConversionResult result = customConverter.convertSource(cpp, rules);

        assertThat(result.getAppliedTransforms())
                .as("10 パスで 10 件のみ変換されること")
                .hasSize(10);
        assertThat(result.getTransformErrors())
                .as("パス上限到達エラーが記録されること")
                .singleElement()
                .satisfies(e -> assertThat(e.getErrorType()).isEqualTo(Transformer.TransformError.ErrorType.PASS_LIMIT_REACHED));
        assertThat(result.getCsCode())
                .as("未変換の INT が残ること")
                .contains("INT ");
    }

    // =========================================================================
    // test_messages.cpp の統合テスト
    // =========================================================================

    @Nested
    @DisplayName("test_messages.cpp: AfxMessageBox → MessageBox.Show 変換")
    class MessagesFileTests {

        @Test
        @DisplayName("ケース1: 単純文字列リテラルの AfxMessageBox (MB_OK | MB_ICONERROR)")
        void testSimpleError() throws IOException {
            String cpp = loadCppFile("test_messages.cpp");
            List<ConversionRule> rules = loadRuleFile("AfxMessageBox.rule");

            ConversionResult result = converter.convertSource(cpp, rules);
            String csCode = result.getCsCode();

            assertThat(csCode)
                    .as("AfxMessageBox が MessageBox.Show に変換されること")
                    .contains("MessageBox.Show")
                    .as("MB_ICONERROR が MessageBoxIcon.Error に変換されること")
                    .contains("MessageBoxIcon.Error")
                    .as("MB_OK が MessageBoxButtons.OK に変換されること")
                    .contains("MessageBoxButtons.OK");

            assertThat(stripComments(csCode))
                    .as("AfxMessageBox というトークンが残らないこと (コメント除く)")
                    .doesNotContain("AfxMessageBox");
        }

        @Test
        @DisplayName("ケース2: 変数引数の AfxMessageBox (MB_OK | MB_ICONWARNING)")
        void testVariableWarning() throws IOException {
            String cpp = "void f(const char* msg) { AfxMessageBox(msg, MB_OK | MB_ICONWARNING); }";
            List<ConversionRule> rules = loadRuleFile("AfxMessageBox.rule");

            ConversionResult result = converter.convertSource(cpp, rules);
            String csCode = result.getCsCode();

            assertThat(csCode).contains("MessageBox.Show");
            assertThat(csCode).contains("MessageBoxIcon.Warning");
            assertThat(csCode).contains("msg");
        }

        @Test
        @DisplayName("ケース3: 関数呼び出し式を引数にした AfxMessageBox")
        void testFunctionCallArgument() throws IOException {
            String cpp = "void f() { AfxMessageBox(BuildMessage(file, line) + \" error\", MB_OK | MB_ICONERROR); }";
            List<ConversionRule> rules = loadRuleFile("AfxMessageBox.rule");

            ConversionResult result = converter.convertSource(cpp, rules);
            String csCode = result.getCsCode();

            assertThat(csCode).contains("MessageBox.Show");
            assertThat(csCode).contains("BuildMessage");
            // キャプチャされた式全体が to テンプレートに展開されること
            assertThat(csCode).contains("MessageBoxIcon.Error");
        }

        @Test
        @DisplayName("ケース4: 引数1つ (デフォルト) の AfxMessageBox")
        void testDefaultMessage() throws IOException {
            String cpp = "void f() { AfxMessageBox(\"完了しました\"); }";
            List<ConversionRule> rules = loadRuleFile("AfxMessageBox.rule");

            ConversionResult result = converter.convertSource(cpp, rules);
            String csCode = result.getCsCode();

            assertThat(csCode).contains("MessageBox.Show");
            assertThat(csCode).doesNotContain("AfxMessageBox");
        }

        @Test
        @DisplayName("ケース5: MB_OK | MB_ICONINFORMATION の変換")
        void testInfoMessage() throws IOException {
            String cpp = "void f(const char* d) { AfxMessageBox(d, MB_OK | MB_ICONINFORMATION); }";
            List<ConversionRule> rules = loadRuleFile("AfxMessageBox.rule");

            ConversionResult result = converter.convertSource(cpp, rules);

            assertThat(result.getCsCode()).contains("MessageBoxIcon.Information");
        }

        @Test
        @DisplayName("ケース8: 深くネストした関数呼び出しを引数にした AfxMessageBox")
        void testDeepNestArgument() throws IOException {
            String cpp = "void f(int a, int b) { AfxMessageBox(Outer(Inner(a, b)), MB_OK | MB_ICONERROR); }";
            List<ConversionRule> rules = loadRuleFile("AfxMessageBox.rule");

            ConversionResult result = converter.convertSource(cpp, rules);
            String csCode = result.getCsCode();

            assertThat(csCode).contains("MessageBox.Show");
            assertThat(csCode).contains("Outer");
            assertThat(csCode).contains("Inner");
        }

        @Test
        @DisplayName("複数の AfxMessageBox が同一ファイルに存在する場合に全て変換される")
        void testMultipleAfxMessageBoxInFile() throws IOException {
            String cpp = loadCppFile("test_messages.cpp");
            List<ConversionRule> rules = loadRuleFile("AfxMessageBox.rule");

            ConversionResult result = converter.convertSource(cpp, rules);
            String csCode = result.getCsCode();

            // AfxMessageBox が全て変換されていること (コメント除く)
            assertThat(stripComments(csCode)).doesNotContain("AfxMessageBox");
            assertThat(csCode).contains("MessageBox.Show");
        }
    }

    // =========================================================================
    // test_math.cpp の統合テスト
    // =========================================================================

    @Nested
    @DisplayName("test_math.cpp: 数学関数 → System.Math 変換")
    class MathFileTests {

        @Test
        @DisplayName("ケース1: sin / cos / tan の変換")
        void testTrigFunctions() throws IOException {
            String cpp = "double f(double a) { double s = sin(a); double c = cos(a); double t = tan(a); return s+c+t; }";
            List<ConversionRule> rules = loadRuleFile("math_functions.rule");

            ConversionResult result = converter.convertSource(cpp, rules);
            String csCode = result.getCsCode();

            assertThat(csCode).contains("Math.Sin");
            assertThat(csCode).contains("Math.Cos");
            assertThat(csCode).contains("Math.Tan");
            assertThat(csCode).doesNotContain("sin (");
            assertThat(csCode).doesNotContain("cos (");
            assertThat(csCode).doesNotContain("tan (");
        }

        @Test
        @DisplayName("ケース2: sqrt の変換")
        void testSqrt() throws IOException {
            String cpp = "double f(double x) { return sqrt(x); }";
            List<ConversionRule> rules = loadRuleFile("math_functions.rule");

            ConversionResult result = converter.convertSource(cpp, rules);

            assertThat(result.getCsCode()).contains("Math.Sqrt");
        }

        @Test
        @DisplayName("ケース2: pow の変換 (2引数)")
        void testPow() throws IOException {
            String cpp = "double f(double b, double e) { return pow(b, e); }";
            List<ConversionRule> rules = loadRuleFile("math_functions.rule");

            ConversionResult result = converter.convertSource(cpp, rules);
            String csCode = result.getCsCode();

            assertThat(csCode).contains("Math.Pow");
            assertThat(csCode).contains("b");
            assertThat(csCode).contains("e");
        }

        @Test
        @DisplayName("ケース3: fabs の変換")
        void testFabs() throws IOException {
            String cpp = "double f(double x) { return fabs(x); }";
            List<ConversionRule> rules = loadRuleFile("math_functions.rule");

            ConversionResult result = converter.convertSource(cpp, rules);

            assertThat(result.getCsCode()).contains("Math.Abs");
        }

        @Test
        @DisplayName("ケース4: log / log10 / floor / ceil の変換")
        void testLogFloorCeil() throws IOException {
            String cpp = "double f(double v) { double a = log(v); double b = log10(v); double c = floor(v); double d = ceil(v); return a+b+c+d; }";
            List<ConversionRule> rules = loadRuleFile("math_functions.rule");

            ConversionResult result = converter.convertSource(cpp, rules);
            String csCode = result.getCsCode();

            assertThat(csCode).contains("Math.Log(");
            assertThat(csCode).contains("Math.Log10(");
            assertThat(csCode).contains("Math.Floor(");
            assertThat(csCode).contains("Math.Ceiling(");
        }

        @Test
        @DisplayName("ケース5: pow のネスト引数 (式を引数にした pow)")
        void testPowWithExpressionArgs() throws IOException {
            String cpp = "double f(double x, double y) { return sqrt(pow(x, 2.0) + pow(y, 2.0)); }";
            List<ConversionRule> rules = loadRuleFile("math_functions.rule");

            ConversionResult result = converter.convertSource(cpp, rules);
            String csCode = result.getCsCode();

            assertThat(csCode).contains("Math.Sqrt");
            assertThat(csCode).contains("Math.Pow");
        }

        @Test
        @DisplayName("test_math.cpp 全体: 全数学関数が変換される")
        void testFullMathFile() throws IOException {
            String cpp = loadCppFile("test_math.cpp");
            List<ConversionRule> rules = loadRuleFile("math_functions.rule");

            ConversionResult result = converter.convertSource(cpp, rules);
            String csCode = result.getCsCode();

            assertThat(csCode).contains("Math.Sin");
            assertThat(csCode).contains("Math.Cos");
            assertThat(csCode).contains("Math.Tan");
            assertThat(csCode).contains("Math.Sqrt");
            assertThat(csCode).contains("Math.Pow");
            assertThat(csCode).contains("Math.Abs");
            assertThat(csCode).contains("Math.Log");
            assertThat(csCode).contains("Math.Floor");
            assertThat(csCode).contains("Math.Ceiling");
        }
    }

    // =========================================================================
    // test_types.cpp の統合テスト
    // =========================================================================

    @Nested
    @DisplayName("test_types.cpp: MFC / Win32 型 → C# 型変換")
    class TypesFileTests {

        @Test
        @DisplayName("ケース1: CString → string")
        void testCString() throws IOException {
            String cpp = "void f() { CString s = \"hello\"; }";
            List<ConversionRule> rules = loadRuleFile("type_conversions.rule");

            ConversionResult result = converter.convertSource(cpp, rules);

            assertThat(result.getCsCode()).contains("string");
            assertThat(result.getCsCode()).doesNotContain("CString");
        }

        @Test
        @DisplayName("ケース2: BOOL / TRUE / FALSE → bool / true / false")
        void testBoolTypes() throws IOException {
            String cpp = "void f() { BOOL b = FALSE; b = TRUE; }";
            List<ConversionRule> rules = loadRuleFile("type_conversions.rule");

            ConversionResult result = converter.convertSource(cpp, rules);
            String csCode = result.getCsCode();

            assertThat(csCode).contains("bool");
            assertThat(csCode).contains("true");
            assertThat(csCode).contains("false");
            assertThat(csCode).doesNotContain("BOOL");
            assertThat(csCode).doesNotContain("TRUE");
            assertThat(csCode).doesNotContain("FALSE");
        }

        @Test
        @DisplayName("ケース3: DWORD / UINT / WORD / BYTE の変換")
        void testUnsignedTypes() throws IOException {
            String cpp = "void f() { DWORD d = 1024; UINT n = 100; WORD w = 255; BYTE b = 10; }";
            List<ConversionRule> rules = loadRuleFile("type_conversions.rule");

            ConversionResult result = converter.convertSource(cpp, rules);
            String csCode = result.getCsCode();

            assertThat(csCode).contains("uint");
            assertThat(csCode).contains("ushort");
            assertThat(csCode).contains("byte");
            assertThat(csCode).doesNotContain("DWORD");
            assertThat(csCode).doesNotContain("UINT");
            assertThat(csCode).doesNotContain("WORD");
            assertThat(csCode).doesNotContain("BYTE");
        }

        @Test
        @DisplayName("ケース4: LONG / INT → long / int")
        void testSignedTypes() throws IOException {
            String cpp = "void f() { LONG l = -512; INT i = 42; }";
            List<ConversionRule> rules = loadRuleFile("type_conversions.rule");

            ConversionResult result = converter.convertSource(cpp, rules);
            String csCode = result.getCsCode();

            assertThat(csCode).contains("long");
            assertThat(csCode).doesNotContain("LONG");
            assertThat(csCode).doesNotContain("INT");
        }

        @Test
        @DisplayName("ケース5: LPCTSTR / LPCSTR / LPSTR → string")
        void testStringPointerTypes() throws IOException {
            String cpp = "void f(LPCTSTR t, LPCSTR a) { LPSTR b = NULL; }";
            List<ConversionRule> rules = loadRuleFile("type_conversions.rule");

            ConversionResult result = converter.convertSource(cpp, rules);
            String csCode = result.getCsCode();

            assertThat(csCode).doesNotContain("LPCTSTR");
            assertThat(csCode).doesNotContain("LPCSTR");
            assertThat(csCode).doesNotContain("LPSTR");
        }

        @Test
        @DisplayName("ケース6: LPVOID → object")
        void testLpvoid() throws IOException {
            String cpp = "void f() { LPVOID p = NULL; }";
            List<ConversionRule> rules = loadRuleFile("type_conversions.rule");

            ConversionResult result = converter.convertSource(cpp, rules);
            String csCode = result.getCsCode();

            assertThat(csCode).contains("object");
            assertThat(csCode).doesNotContain("LPVOID");
        }

        @Test
        @DisplayName("ケース7: NULL → null")
        void testNull() throws IOException {
            String cpp = "void f() { int* p = NULL; if (p == NULL) { p = NULL; } }";
            List<ConversionRule> rules = loadRuleFile("type_conversions.rule");

            ConversionResult result = converter.convertSource(cpp, rules);
            String csCode = result.getCsCode();

            assertThat(csCode).contains("null");
            assertThat(csCode).doesNotContain("NULL");
        }

        @Test
        @DisplayName("test_types.cpp 全体: 全 MFC 型が C# 型に変換される")
        void testFullTypesFile() throws IOException {
            String cpp = loadCppFile("test_types.cpp");
            List<ConversionRule> rules = loadRuleFile("type_conversions.rule");

            ConversionResult result = converter.convertSource(cpp, rules);
            String csCode = result.getCsCode();

            // 主要な MFC 型が C# 型に置き換わっていること (コメント除く)
            assertThat(stripComments(csCode)).doesNotContain("CString");
            assertThat(stripComments(csCode)).doesNotContain("BOOL");
            assertThat(stripComments(csCode)).doesNotContain("DWORD");
            assertThat(stripComments(csCode)).doesNotContain("NULL");
        }
    }

    // =========================================================================
    // test_compound.cpp の統合テスト
    // =========================================================================

    @Nested
    @DisplayName("test_compound.cpp: 複合パターン (複数ルール混在)")
    class CompoundFileTests {

        /**
         * 全ルールファイルを結合して返す。
         */
        private List<ConversionRule> loadAllRules() throws IOException {
            List<ConversionRule> rules = new java.util.ArrayList<>();
            rules.addAll(loadRuleFile("AfxMessageBox.rule"));
            rules.addAll(loadRuleFile("math_functions.rule"));
            rules.addAll(loadRuleFile("type_conversions.rule"));
            rules.addAll(loadRuleFile("printf_functions.rule"));
            rules.addAll(loadRuleFile("format_migration.rule"));
            rules.addAll(loadRuleFile("migration_helper.rule"));
            rules.addAll(loadRuleFile("cstring_methods.rule"));
            return rules;
        }

        @Test
        @DisplayName("ケース1: CString と AfxMessageBox の組み合わせ変換")
        void testStringAndMessageBox() throws IOException {
            String cpp = "void f(CString input) { if (input.IsEmpty()) { AfxMessageBox(\"空です\", MB_OK | MB_ICONWARNING); } }";
            List<ConversionRule> rules = loadAllRules();

            ConversionResult result = converter.convertSource(cpp, rules);
            String csCode = result.getCsCode();

            assertThat(csCode).contains("string");
            assertThat(csCode).contains("MessageBox.Show");
            assertThat(csCode).contains("MessageBoxIcon.Warning");
        }

        @Test
        @DisplayName("ケース2: BOOL と AfxMessageBox の組み合わせ")
        void testBoolAndMessageBox() throws IOException {
            String cpp = "BOOL f(CString name) { int r = AfxMessageBox(name, MB_YESNO | MB_ICONQUESTION); if (r == IDYES) { return TRUE; } return FALSE; }";
            List<ConversionRule> rules = loadAllRules();

            ConversionResult result = converter.convertSource(cpp, rules);
            String csCode = result.getCsCode();

            assertThat(csCode).contains("MessageBox.Show");
            assertThat(csCode).contains("MessageBoxButtons.YesNo");
            assertThat(csCode).contains("MessageBoxIcon.Question");
            assertThat(csCode).contains("DialogResult.Yes");
            assertThat(csCode).contains("true");
            assertThat(csCode).contains("false");
        }

        @Test
        @DisplayName("引数個数フィルタ: 2引数 AfxMessageBox に汎用1引数ルールのみの場合は変換されない")
        void testArgumentCountFilterPreventsGenericRule() throws IOException {
            // MB_YESNO 等の2引数用ルールを除外し、汎用1引数ルールのみで変換
            List<ConversionRule> rules = new ArrayList<>();
            rules.addAll(loadRuleFile("type_conversions.rule"));
            rules.addAll(loadRuleFile("math_functions.rule"));
            rules.add(loader.loadFromString(
                    "from: AfxMessageBox ( ABSTRACT_PARAM00 ) ;\nto: MessageBox.Show(ABSTRACT_PARAM00);",
                    "generic_only.rule").get(0));

            String cpp = "void f() { AfxMessageBox(\"確認\", MB_YESNO | MB_ICONQUESTION); }";
            ConversionResult result = converter.convertSource(cpp, rules);
            String csCode = result.getCsCode();

            // 汎用ルールは引数個数不一致で除外され、変換されない
            assertThat(csCode).contains("AfxMessageBox");
            assertThat(csCode).contains("MB_YESNO");
            assertThat(csCode).doesNotContain("MessageBox.Show");
        }

        @Test
        @DisplayName("ケース3: 数学関数と BOOL 型の組み合わせ")
        void testMathAndBool() throws IOException {
            String cpp = "BOOL f(double theta) { double s = sin(theta); double c = cos(theta); if (fabs(s) < 0.01) { return TRUE; } return FALSE; }";
            List<ConversionRule> rules = loadAllRules();

            ConversionResult result = converter.convertSource(cpp, rules);
            String csCode = result.getCsCode();

            assertThat(csCode).contains("bool");
            assertThat(csCode).contains("Math.Sin");
            assertThat(csCode).contains("Math.Cos");
            assertThat(csCode).contains("Math.Abs");
            assertThat(csCode).contains("true");
            assertThat(csCode).contains("false");
        }

        @Test
        @DisplayName("ケース4: pow の複合引数 (x+y を ABSTRACT_PARAM でキャプチャ)")
        void testPowWithComplexArgs() throws IOException {
            String cpp = "double f(double x, double y, double z) { return sqrt(pow(x, 2.0) + pow(y, 2.0) + pow(z, 2.0)); }";
            List<ConversionRule> rules = loadAllRules();

            ConversionResult result = converter.convertSource(cpp, rules);
            String csCode = result.getCsCode();

            assertThat(csCode).contains("Math.Sqrt");
            assertThat(csCode).contains("Math.Pow");
        }

        @Test
        @DisplayName("ケース5: AfxMessageBox に数学計算結果を渡すパターン")
        void testMathResultToMessageBox() throws IOException {
            String cpp = "void f(double b, double e) { double r = pow(b, e); if (r < 0.0) { AfxMessageBox(FormatResult(r), MB_OK | MB_ICONERROR); } }";
            List<ConversionRule> rules = loadAllRules();

            ConversionResult result = converter.convertSource(cpp, rules);
            String csCode = result.getCsCode();

            assertThat(csCode).contains("Math.Pow");
            assertThat(csCode).contains("MessageBox.Show");
            assertThat(csCode).contains("MessageBoxIcon.Error");
        }

        @Test
        @DisplayName("ケース6: NULL と型変換の組み合わせ")
        void testNullAndTypes() throws IOException {
            String cpp = "BOOL f(LPVOID* p, DWORD size) { if (p == NULL) { return FALSE; } return TRUE; }";
            List<ConversionRule> rules = loadAllRules();

            ConversionResult result = converter.convertSource(cpp, rules);
            String csCode = result.getCsCode();

            assertThat(csCode).contains("object");
            assertThat(csCode).contains("uint");
            assertThat(csCode).contains("null");
            assertThat(csCode).contains("false");
            assertThat(csCode).contains("true");
        }


        @Test
        @DisplayName("ケース7: 同一関数内の複数 AfxMessageBox が全て変換される")
        void testMultipleMessageBoxInFunction() throws IOException {
            String cpp = "void f(BOOL bA, BOOL bB) { if (bA == TRUE) { AfxMessageBox(\"エラーA\", MB_OK | MB_ICONERROR); } if (bB == TRUE) { AfxMessageBox(\"エラーB\", MB_OK | MB_ICONERROR); } }";
            List<ConversionRule> rules = loadAllRules();

            ConversionResult result = converter.convertSource(cpp, rules);
            String csCode = result.getCsCode();

            assertThat(csCode).doesNotContain("AfxMessageBox");
            assertThat(csCode).contains("MessageBox.Show");
        }

        @Test
        @DisplayName("ケース9: CString/CTime Format 4重ネスト → MigrationHelper.Format (末端 L1→L4 順)")
        void testFormatNestedFourLevels() throws IOException {
            String cpp = loadCppFile("test_format_nested.cpp");
            List<ConversionRule> rules = loadAllRules();

            ConversionResult result = converter.convertSource(cpp, rules);
            String csCode = result.getCsCode();

            assertThat(result.getTransformErrors()).isEmpty();
            assertThat(csCode).contains("MigrationHelper.Format");
            // L1: time.Format("%Y/%m/%d")
            assertThat(csCode).contains("MigrationHelper.Format(time, \"%Y/%m/%d\")");
            // L2: str1.Format("[%s]", ...)
            assertThat(csCode).contains("MigrationHelper.Format(str1, \"[%s]\"");
            // L3: str2.Format(">> %s", ...)
            assertThat(csCode).contains("MigrationHelper.Format(str2, \">> %s\"");
            // L4: result_str.Format("LOG: %s", ...)
            assertThat(csCode).contains("MigrationHelper.Format(result_str, \"LOG: %s\"");
        }

        @Test
        @DisplayName("ケース10: Pattern 2 異種4重ネスト (Format→Find→GetAt→Format)")
        void testFormatPattern2() throws IOException {
            String cpp = loadCppFile("test_format_pattern2.cpp");
            List<ConversionRule> rules = loadAllRules();

            ConversionResult result = converter.convertSource(cpp, rules);
            String csCode = result.getCsCode();

            assertThat(result.getTransformErrors()).isEmpty();
            assertThat(csCode).contains("MigrationHelper.Format(msg, \"[%c]\"");
            assertThat(csCode).contains("MigrationHelper.GetAt(str,");
            assertThat(csCode).contains("MigrationHelper.Find(str,");
        }

        @Test
        @DisplayName("ケース11: Pattern 3 複数引数4重ネスト (2本の L1 ブランチ)")
        void testFormatPattern3() throws IOException {
            String cpp = loadCppFile("test_format_pattern3.cpp");
            List<ConversionRule> rules = loadAllRules();

            ConversionResult result = converter.convertSource(cpp, rules);
            String csCode = result.getCsCode();

            assertThat(result.getTransformErrors()).isEmpty();
            assertThat(csCode).contains("MigrationHelper.Format(time, \"%Y/%m/%d\")");
            assertThat(csCode).contains("MigrationHelper.Format(dt, \"%H:%M:%S\")");
        }

        @Test
        @DisplayName("ケース12: Pattern D1 純粋Formatドットチェーン")
        void testFormatPatternD1() throws IOException {
            String cpp = loadCppFile("test_format_pattern_d1.cpp");
            List<ConversionRule> rules = loadAllRules();

            ConversionResult result = converter.convertSource(cpp, rules);
            String csCode = result.getCsCode();

            assertThat(result.getTransformErrors()).isEmpty();
            assertThat(csCode).contains("MigrationHelper.Format");
        }

        @Test
        @DisplayName("ケース13: Pattern D2 混在ドットチェーン (Format→TrimRight→Left→Find)")
        void testFormatPatternD2() throws IOException {
            String cpp = loadCppFile("test_format_pattern_d2.cpp");
            List<ConversionRule> rules = loadAllRules();

            ConversionResult result = converter.convertSource(cpp, rules);
            String csCode = result.getCsCode();

            assertThat(result.getTransformErrors()).isEmpty();
            assertThat(csCode).contains("MigrationHelper.Format");
            assertThat(csCode).contains("MigrationHelper.Find");
        }

        @Test
        @DisplayName("ケース14: Pattern D3 混在ドットチェーン (Format→TrimRight→Left→IsEmpty)")
        void testFormatPatternD3() throws IOException {
            String cpp = loadCppFile("test_format_pattern_d3.cpp");
            List<ConversionRule> rules = loadAllRules();

            ConversionResult result = converter.convertSource(cpp, rules);
            String csCode = result.getCsCode();

            assertThat(result.getTransformErrors()).isEmpty();
            assertThat(csCode).contains("MigrationHelper.Format");
            assertThat(csCode).contains("Substring");
        }

        @Test
        @DisplayName("ケース15: Pattern D4 ドットチェーン vs 引数ネスト")
        void testFormatPatternD4() throws IOException {
            String cpp = loadCppFile("test_format_pattern_d4.cpp");
            List<ConversionRule> rules = loadAllRules();

            ConversionResult result = converter.convertSource(cpp, rules);
            String csCode = result.getCsCode();

            assertThat(result.getTransformErrors()).isEmpty();
            assertThat(csCode).contains("MigrationHelper.Find(str,");
        }

        @Test
        @DisplayName("test_compound.cpp 全体: 変換エラーなしで処理完了")
        void testFullCompoundFile() throws IOException {
            String cpp = loadCppFile("test_compound.cpp");
            List<ConversionRule> rules = loadAllRules();

            ConversionResult result = converter.convertSource(cpp, rules);

            // 曖昧マッチ (2件以上) のエラーがないこと
            assertThat(result.getTransformErrors())
                    .as("曖昧マッチエラーが発生しないこと")
                    .isEmpty();

            // 主要な変換が実施されていること
            String csCode = result.getCsCode();
            assertThat(csCode).contains("MessageBox.Show");
            assertThat(csCode).contains("Math.");
        }
    }

    // =========================================================================
    // エラーケースのテスト
    // =========================================================================

    @Nested
    @DisplayName("エラーケース: 変換定義の境界条件")
    class ErrorCaseTests {

        @Test
        @DisplayName("変換ルールにマッチしないコードは変換されずそのまま出力される")
        void testUnmatchedCodePassthrough() throws IOException {
            String cpp = "int add(int a, int b) { return a + b; }";
            List<ConversionRule> rules = loader.loadFromString(
                    "from: AfxMessageBox ( ABSTRACT_PARAM00 )\nto: MessageBox.Show(ABSTRACT_PARAM00)",
                    "test.rule");

            ConversionResult result = converter.convertSource(cpp, rules);
            String csCode = result.getCsCode();

            assertThat(csCode)
                    .as("AfxMessageBox がないコードは変換されないこと")
                    .doesNotContain("MessageBox.Show");
            assertThat(csCode)
                    .as("変換対象外のトークンはそのまま残ること")
                    .contains("add")
                    .contains("return");
        }

        @Test
        @DisplayName("ルールなしでも C++ ファイル全体が正常に処理される")
        void testNoRulesProcessesSuccessfully() throws IOException {
            String cpp = loadCppFile("test_messages.cpp");

            ConversionResult result = converter.convertSource(cpp, List.of());

            assertThat(result.getTransformErrors()).isEmpty();
            assertThat(result.getCsCode()).isNotBlank();
        }

        @Test
        @DisplayName("空の C++ ソースを処理してもエラーが発生しない")
        void testEmptySourceNoError() throws IOException {
            String cpp = "";
            ConversionResult result = converter.convertSource(cpp, List.of());

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("変換結果のパースエラー件数を確認できる")
        void testParseErrorsAreCollected() throws IOException {
            // ANTLR が認識できない構文を含むコード
            // (#define などはプリプロセッサとして hidden channel へ送られる)
            String cpp = "void f() { int x = 42; }";
            ConversionResult result = converter.convertSource(cpp, List.of());

            // エラーなしで処理されること
            assertThat(result.getParseErrors()).isEmpty();
        }
    }
}
