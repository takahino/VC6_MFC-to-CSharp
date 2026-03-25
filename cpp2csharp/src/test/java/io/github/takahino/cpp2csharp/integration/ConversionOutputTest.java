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
import io.github.takahino.cpp2csharp.dynamic.DynamicRuleGenerator;
import io.github.takahino.cpp2csharp.dynamic.DynamicRuleSpec;
import io.github.takahino.cpp2csharp.output.ConversionOutputWriter;
import io.github.takahino.cpp2csharp.rule.ConversionRule;
import io.github.takahino.cpp2csharp.matcher.CppParserFactory;
import io.github.takahino.cpp2csharp.rule.ConversionRuleLoader;
import io.github.takahino.cpp2csharp.rule.ConversionRuleLoader.ThreePassRuleSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * 統合テスト: 変換結果を {@code outputs/test/} ディレクトリに出力する。
 *
 * <p>
 * 各テストメソッドは C++ ファイルを変換し、入力と同じパスに拡張子 .cs で出力する:
 * </p>
 * <ul>
 * <li>{@code outputs/test/<name>.cpp} — 変換元 C++ ソース（入力）</li>
 * <li>{@code outputs/test/<name>.cs} — 変換後 C# コード</li>
 * <li>{@code outputs/test/<name>.report.txt} — 変換サマリーレポート</li>
 * <li>{@code outputs/test/<name>.report.html} — diff HTML レポート</li>
 * <li>{@code outputs/test/<name>.treedump.txt} — AST 木ダンプ（ルール設計デバッグ用）</li>
 * <li>{@code outputs/test/<name>.xlsx} — 変換過程の可視化（Excel）</li>
 * </ul>
 */
@DisplayName("変換結果ファイル出力テスト")
class ConversionOutputTest {

	/** outputs/test ディレクトリ（プロジェクトルートからの相対パス） */
	private static final Path OUTPUT_DIR = Paths.get(System.getProperty("user.dir")).resolve("outputs/test");

	private CppToCSharpConverter converter;
	private ConversionRuleLoader loader;
	private ConversionOutputWriter writer;

	@BeforeEach
	void setUp() throws IOException {
		// 各テストで 1 converter を使用。マルチスレッド化時は converter を共有しないこと。
		converter = new CppToCSharpConverter();
		loader = new ConversionRuleLoader(CppParserFactory.asLexerFactory());
		writer = new ConversionOutputWriter();
	}

	// =========================================================================
	// ヘルパーメソッド
	// =========================================================================

	private String loadCppFile(String fileName) throws IOException {
		String path = "cpp/" + fileName;
		try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
			if (is == null)
				throw new IOException("リソースが見つかりません: " + path);
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	/** フェーズ別ルールのベースパス（[02]_標準置き換え） */
	private static final String RULES_PHASE_PATH = "rules/main/[02]_標準置き換え/";

	private List<ConversionRule> loadRuleFile(String ruleFileName) throws IOException {
		return loader.loadFromResource(RULES_PHASE_PATH + ruleFileName);
	}

	/** フェーズ別ルール（[01] pointer → [02] 標準置き換え）。pointer.rule を先に適用する。 */
	private List<List<ConversionRule>> loadAllRulesByPhase() throws IOException {
		List<List<ConversionRule>> phases = new ArrayList<>();
		phases.add(loader.loadFromResource("rules/main/[01]_ブロックコメント/pointer.rule"));
		List<ConversionRule> phase2 = new ArrayList<>();
		phase2.addAll(loadRuleFile("AfxMessageBox.rule"));
		phase2.addAll(loadRuleFile("array_declaration.rule"));
		phase2.addAll(loadRuleFile("math_functions.rule"));
		phase2.addAll(loadRuleFile("type_conversions.rule"));
		phase2.addAll(loadRuleFile("printf_functions.rule"));
		phase2.addAll(loadRuleFile("format_migration.rule"));
		phase2.addAll(loadRuleFile("migration_helper.rule"));
		phase2.addAll(loadRuleFile("cstring_methods.rule"));
		phases.add(phase2);

		phases.add(loader.loadFromResource("rules/main/[03]_パッチ置き換え/ToBool.rule"));
		return phases;
	}

	/**
	 * 変換後コードからコメント部分を除去した文字列を返す。 コメント保持機能により出力にコメントが含まれる場合でも、
	 * コード本体部分のみに対してアサーションを行うために使用する。
	 */
	private String stripComments(String code) {
		String noBlock = code.replaceAll("/\\*.*?\\*/", "");
		return noBlock.replaceAll("//[^\n]*", "");
	}

	// =========================================================================
	// ファイル出力テスト
	// =========================================================================

	@Test
	@DisplayName("test_messages.cpp — AfxMessageBox ルールのみで変換し出力")
	void outputMessages() throws IOException {
		String cpp = loadCppFile("test_messages.cpp");
		List<ConversionRule> rules = loadRuleFile("AfxMessageBox.rule");

		ConversionResult result = converter.convertSource(cpp, rules);
		writeOutput("messages_AfxMessageBox", cpp, result);

		// 変換成功確認
		assertThat(result.getCsCode()).contains("MessageBox.Show");
		assertThat(stripComments(result.getCsCode())).doesNotContain("AfxMessageBox");

		// 出力ファイルが生成されたことを確認
		assertOutputFilesExist("messages_AfxMessageBox");
	}

	@Test
	@DisplayName("test_math.cpp — 数学関数ルールのみで変換し出力")
	void outputMath() throws IOException {
		String cpp = loadCppFile("test_math.cpp");
		List<ConversionRule> rules = loadRuleFile("math_functions.rule");

		ConversionResult result = converter.convertSource(cpp, rules);
		writeOutput("math_functions", cpp, result);

		assertThat(result.getCsCode()).contains("Math.Sin");
		assertThat(result.getCsCode()).contains("Math.Pow");
		assertThat(result.getCsCode()).contains("Math.Sqrt");

		assertOutputFilesExist("math_functions");
	}

	@Test
	@DisplayName("test_types.cpp — 型変換・CString メソッドで変換し出力")
	void outputTypes() throws IOException {
		String cpp = loadCppFile("test_types.cpp");
		List<ConversionRule> rules = new ArrayList<>();
		rules.addAll(loadRuleFile("type_conversions.rule"));
		rules.addAll(loadRuleFile("cstring_methods.rule"));

		ConversionResult result = converter.convertSource(cpp, rules);
		writeOutput("type_conversions", cpp, result);

		assertThat(stripComments(result.getCsCode())).doesNotContain("CString");
		assertThat(stripComments(result.getCsCode())).doesNotContain("BOOL");
		assertThat(stripComments(result.getCsCode())).doesNotContain("NULL");
		assertThat(stripComments(result.getCsCode())).doesNotContain("GetLength");

		assertOutputFilesExist("type_conversions");
	}

	@Test
	@DisplayName("test_compound.cpp — 全ルールを適用して変換し出力")
	void outputCompound() throws IOException {
		String cpp = loadCppFile("test_compound.cpp");
		List<List<ConversionRule>> rulesByPhase = loadAllRulesByPhase();

		ConversionResult result = converter.convertSourceWithPhases(cpp, rulesByPhase);
		writeOutput("compound_all_rules", cpp, result);

		// 曖昧マッチエラーなし
		assertThat(result.getTransformErrors()).isEmpty();
		// 主要変換の確認
		assertThat(result.getCsCode()).contains("MessageBox.Show");
		assertThat(result.getCsCode()).contains("Math.");

		assertOutputFilesExist("compound_all_rules");
	}

	@Test
	@DisplayName("test_messages.cpp — 全ルール適用で変換し出力 (型変換との複合)")
	void outputMessagesWithAllRules() throws IOException {
		String cpp = loadCppFile("test_messages.cpp");
		List<List<ConversionRule>> rulesByPhase = loadAllRulesByPhase();

		ConversionResult result = converter.convertSourceWithPhases(cpp, rulesByPhase);
		writeOutput("messages_all_rules", cpp, result);

		assertThat(result.getCsCode()).contains("MessageBox.Show");
		assertOutputFilesExist("messages_all_rules");
	}

	@Test
	@DisplayName("test_math.cpp — 全ルール適用で変換し出力")
	void outputMathWithAllRules() throws IOException {
		String cpp = loadCppFile("test_math.cpp");
		List<List<ConversionRule>> rulesByPhase = loadAllRulesByPhase();

		ConversionResult result = converter.convertSourceWithPhases(cpp, rulesByPhase);
		writeOutput("math_all_rules", cpp, result);

		assertThat(result.getCsCode()).contains("Math.Sin");
		assertThat(result.getCsCode()).contains("Math.Pow");
		assertOutputFilesExist("math_all_rules");
	}

	// =========================================================================
	// 単発ケースの詳細出力テスト
	// =========================================================================

	@Test
	@DisplayName("単体: sin(CreateMessage(a,b)) — ネスト関数のキャプチャ確認")
	void outputNestedSin() throws IOException {
		String cpp = "double f(double a, double b) { return sin(CreateMessage(a, b)); }";
		List<ConversionRule> rules = loadRuleFile("math_functions.rule");

		ConversionResult result = converter.convertSource(cpp, rules);
		writeOutput("case_nested_sin", cpp, result);

		assertThat(result.getCsCode()).contains("Math.Sin");
		assertThat(result.getCsCode()).contains("CreateMessage");
		assertOutputFilesExist("case_nested_sin");
	}

	@Test
	@DisplayName("単体: sqrt(pow(x,2)+pow(y,2)) — 右端優先で内側から変換")
	void outputNestedSqrtPow() throws IOException {
		String cpp = "double f(double x, double y) { return sqrt(pow(x, 2.0) + pow(y, 2.0)); }";
		List<ConversionRule> rules = loadRuleFile("math_functions.rule");

		ConversionResult result = converter.convertSource(cpp, rules);
		writeOutput("case_nested_sqrt_pow", cpp, result);

		assertThat(result.getCsCode()).contains("Math.Sqrt");
		assertThat(result.getCsCode()).contains("Math.Pow");
		assertOutputFilesExist("case_nested_sqrt_pow");
	}

	@Test
	@DisplayName("単体: AfxMessageBox に複合式を渡すパターン")
	void outputAfxMessageBoxComplexArg() throws IOException {
		String cpp = "void f(const char* f, int l) { AfxMessageBox(BuildMessage(f, l) + \" at error\", MB_OK | MB_ICONERROR); }";
		List<ConversionRule> rules = loadRuleFile("AfxMessageBox.rule");

		ConversionResult result = converter.convertSource(cpp, rules);
		writeOutput("case_afxmsg_complex_arg", cpp, result);

		assertThat(result.getCsCode()).contains("MessageBox.Show");
		assertThat(result.getCsCode()).contains("BuildMessage");
		assertOutputFilesExist("case_afxmsg_complex_arg");
	}

	@Test
	@DisplayName("test_format_nested.cpp — CString/CTime Format 4重ネストを MigrationHelper.Format に変換し出力")
	void outputFormatNested() throws IOException {
		String cpp = loadCppFile("test_format_nested.cpp");
		List<List<ConversionRule>> rulesByPhase = loadAllRulesByPhase();

		ConversionResult result = converter.convertSourceWithPhases(cpp, rulesByPhase);
		writeOutput("format_nested", cpp, result);

		assertThat(result.getTransformErrors()).isEmpty();
		assertThat(result.getCsCode()).contains("MigrationHelper.Format");
		assertThat(result.getCsCode()).contains("MigrationHelper.Format(time, \"%Y/%m/%d\")");
		assertThat(result.getCsCode()).contains("MigrationHelper.Format(result_str, \"LOG: %s\"");

		assertOutputFilesExist("format_nested");
	}

	@Test
	@DisplayName("Pattern 2: 異種4重ネスト (Format→Find→GetAt→Format)")
	void outputFormatPattern2() throws IOException {
		String cpp = loadCppFile("test_format_pattern2.cpp");
		List<List<ConversionRule>> rulesByPhase = loadAllRulesByPhase();

		ConversionResult result = converter.convertSourceWithPhases(cpp, rulesByPhase);
		writeOutput("format_pattern2", cpp, result);

		assertThat(result.getTransformErrors()).isEmpty();
		assertThat(result.getCsCode()).contains("MigrationHelper.Format(msg, \"[%c]\"");
		assertThat(result.getCsCode()).contains("MigrationHelper.GetAt(str,");
		assertThat(result.getCsCode()).contains("MigrationHelper.Find(str,");
		assertThat(result.getCsCode()).contains("MigrationHelper.Format(time, \"%Y/%m/%d\")");

		assertOutputFilesExist("format_pattern2");
	}

	@Test
	@DisplayName("Pattern 3: 複数引数4重ネスト (2本の L1 ブランチ)")
	void outputFormatPattern3() throws IOException {
		String cpp = loadCppFile("test_format_pattern3.cpp");
		List<List<ConversionRule>> rulesByPhase = loadAllRulesByPhase();

		ConversionResult result = converter.convertSourceWithPhases(cpp, rulesByPhase);
		writeOutput("format_pattern3", cpp, result);

		assertThat(result.getTransformErrors()).isEmpty();
		assertThat(result.getCsCode()).contains("MigrationHelper.Format(time, \"%Y/%m/%d\")");
		assertThat(result.getCsCode()).contains("MigrationHelper.Format(dt, \"%H:%M:%S\")");
		assertThat(result.getCsCode()).contains("MigrationHelper.Format(result_str, \"LOG=[%s]\"");

		assertOutputFilesExist("format_pattern3");
	}

	@Test
	@DisplayName("Pattern D1: 純粋Formatドットチェーン")
	void outputFormatPatternD1() throws IOException {
		String cpp = loadCppFile("test_format_pattern_d1.cpp");
		List<List<ConversionRule>> rulesByPhase = loadAllRulesByPhase();

		ConversionResult result = converter.convertSourceWithPhases(cpp, rulesByPhase);
		writeOutput("format_pattern_d1", cpp, result);

		assertThat(result.getTransformErrors()).isEmpty();
		assertThat(result.getCsCode()).contains("MigrationHelper.Format");
		assertThat(result.getCsCode()).doesNotContain("FormatMigrationHelper.Format");
		assertThat(result.getCsCode()).contains("MigrationHelper.Format(time, \"%Y/%m/%d\")");

		assertOutputFilesExist("format_pattern_d1");
	}

	@Test
	@DisplayName("Pattern D2: 混在ドットチェーン (Format→TrimRight→Left→Find)")
	void outputFormatPatternD2() throws IOException {
		String cpp = loadCppFile("test_format_pattern_d2.cpp");
		List<List<ConversionRule>> rulesByPhase = loadAllRulesByPhase();

		ConversionResult result = converter.convertSourceWithPhases(cpp, rulesByPhase);
		writeOutput("format_pattern_d2", cpp, result);

		assertThat(result.getTransformErrors()).isEmpty();
		assertThat(result.getCsCode()).contains("MigrationHelper.Format");
		assertThat(result.getCsCode()).contains("MigrationHelper.Find");

		assertOutputFilesExist("format_pattern_d2");
	}

	@Test
	@DisplayName("Pattern D3: 混在ドットチェーン (Format→TrimRight→Left→IsEmpty)")
	void outputFormatPatternD3() throws IOException {
		String cpp = loadCppFile("test_format_pattern_d3.cpp");
		List<List<ConversionRule>> rulesByPhase = loadAllRulesByPhase();

		ConversionResult result = converter.convertSourceWithPhases(cpp, rulesByPhase);
		writeOutput("format_pattern_d3", cpp, result);

		assertThat(result.getTransformErrors()).isEmpty();
		assertThat(result.getCsCode()).contains("MigrationHelper.Format");
		assertThat(result.getCsCode()).contains("Substring");

		assertOutputFilesExist("format_pattern_d3");
	}

	@Test
	@DisplayName("Pattern D4: ドットチェーン vs 引数ネスト 構造対比")
	void outputFormatPatternD4() throws IOException {
		String cpp = loadCppFile("test_format_pattern_d4.cpp");
		List<List<ConversionRule>> rulesByPhase = loadAllRulesByPhase();

		ConversionResult result = converter.convertSourceWithPhases(cpp, rulesByPhase);
		writeOutput("format_pattern_d4", cpp, result);

		assertThat(result.getTransformErrors()).isEmpty();
		assertThat(result.getCsCode()).contains("MigrationHelper.Format(time, \"%Y/%m/%d\")");
		assertThat(result.getCsCode()).contains("MigrationHelper.Find(str,");

		assertOutputFilesExist("format_pattern_d4");
	}

	@Test
	@DisplayName("変換制約パターン集 — 変換挙動の記録")
	void outputConversionLimits() throws IOException {
		String cpp = loadCppFile("test_conversion_limits.cpp");
		List<List<ConversionRule>> rulesByPhase = loadAllRulesByPhase();

		ConversionResult result = converter.convertSourceWithPhases(cpp, rulesByPhase);
		writeOutput("conversion_limits", cpp, result);

		String cs = result.getCsCode();

		// 【制約1】LCA により this->obj 全体が置換され "this ." が消える
		assertThat(cs).contains("m_str . Substring"); // LCA: this->m_str.Left(5) 全体が m_str.Substring に
		assertThat(cs).contains("MigrationHelper.Format(m_time"); // LCA: this->m_time.Format 全体が MigrationHelper.Format
																	// に
		assertThat(cs).contains("MigrationHelper.Find(m_str"); // LCA: this->m_str.Find 全体が MigrationHelper.Find に

		// 【参考】RECEIVER で関数呼び出し結果をレシーバーにできる
		// GetString(data)/CreateKey(data) の戻り値にメソッドが正しく適用される
		assertThat(cs).contains("GetString ( data ).Substring"); // Left → Substring 正常変換
		assertThat(cs).contains("MigrationHelper.Find(CreateKey ( data )"); // Find 正常変換
		assertThat(cs).contains("BuildPath(data).IsEmpty()"); // 0引数フィルタで変換されない

		// 【参考】同一メソッド連鎖: ネスト形式になる
		assertThat(cs).contains("MigrationHelper.Format(MigrationHelper.Format(time");

		// 【参考】正しく変換されるパターン
		assertThat(cs).contains("MigrationHelper.Find(str, MigrationHelper.Format(time"); // (A) 引数ネスト
		assertThat(cs).contains("MigrationHelper.Find(MigrationHelper.Format(time"); // (B) 異種チェーン
		assertThat(cs).contains("MigrationHelper.Find(arr"); // (C) 配列要素
		assertThat(cs).contains("str . Substring"); // (D) 単純変換

		// 診断候補: BuildPath(data).IsEmpty() 等が 0 引数フィルタで未変換のまま残り、再マッチで検出される
		assertThat(result.getDiagnosticCandidates()).isNotEmpty();
		assertOutputFilesExist("conversion_limits");

		// 診断候補は .cs には埋め込まず report のみに出力する
		String csFile = Files.readString(OUTPUT_DIR.resolve("conversion_limits.cs"), StandardCharsets.UTF_8);
		assertThat(csFile).doesNotContain(ConversionOutputWriter.DIAG_COMMENT_MARKER);

		String reportTxt = Files.readString(OUTPUT_DIR.resolve("conversion_limits.report.txt"), StandardCharsets.UTF_8);
		assertThat(reportTxt).contains("--- 診断候補 ---");
		assertThat(reportTxt).contains("still_matchable_after_all_phases");

		String reportHtml = Files.readString(OUTPUT_DIR.resolve("conversion_limits.report.html"),
				StandardCharsets.UTF_8);
		assertThat(reportHtml).contains("診断レポート");
	}

	@Test
	@DisplayName("test_tobool.cpp — if/while 条件式を .ToBool() に変換し出力")
	void outputTobool() throws IOException {
		String cpp = loadCppFile("test_tobool.cpp");
		List<List<ConversionRule>> rulesByPhase = loadAllRulesByPhase();

		ConversionResult result = converter.convertSourceWithPhases(cpp, rulesByPhase);
		writeOutput("tobool", cpp, result);

		assertThat(result.getTransformErrors()).isEmpty();
		assertThat(result.getCsCode()).contains(".ToBool()");
		assertThat(stripComments(result.getCsCode())).contains("(count ) . ToBool()")
				.contains("while ((n ) . ToBool() )").contains("(j ) . ToBool()").contains("while ((k ) . ToBool() )");
		assertOutputFilesExist("tobool");
	}

	@Test
	@DisplayName("単体: 型変換 (CString, BOOL, DWORD, NULL) の複合")
	void outputTypeConversionMix() throws IOException {
		String cpp = "BOOL f(CString s, DWORD n) { if (s == NULL) { return FALSE; } return TRUE; }";
		List<ConversionRule> rules = loadRuleFile("type_conversions.rule");

		ConversionResult result = converter.convertSource(cpp, rules);
		writeOutput("case_type_mix", cpp, result);

		assertThat(result.getCsCode()).contains("bool");
		assertThat(result.getCsCode()).contains("string");
		assertThat(result.getCsCode()).contains("uint");
		assertThat(result.getCsCode()).contains("null");
		assertThat(result.getCsCode()).contains("false");
		assertThat(result.getCsCode()).contains("true");
		assertOutputFilesExist("case_type_mix");
	}

	@Test
	@DisplayName("test_mrule_malloc_with_logic_between.cpp — 宣言と代入の間に別ロジックがあるパターンで出力")
	void outputMruleMallocWithLogicBetween() throws IOException {
		String cpp = loadCppFile("test_mrule_malloc_with_logic_between.cpp");
		ThreePassRuleSet ruleSet = loader.loadThreePassRules();

		ConversionResult result = converter.convertSourceThreePass(cpp, ruleSet);
		writeOutput("mrule_malloc_with_logic_between", cpp, result);

		String normalized = stripComments(result.getCsCode()).replaceAll("\\s+", "");
		assertThat(normalized).contains("stringpszBuf;");
		assertThat(normalized).contains("pszBuf=\"\";");
		assertThat(normalized).contains("intn=0;");
		assertThat(normalized).doesNotContain("malloc");
		assertThat(result.getTransformErrors()).isEmpty();
		assertOutputFilesExist("mrule_malloc_with_logic_between");
	}

	@Test
	@DisplayName("test_mrule_malloc_string_assignment.cpp — 3パスで mrule 正規化を適用して出力")
	void outputMruleMallocStringAssignment() throws IOException {
		String cpp = loadCppFile("test_mrule_malloc_string_assignment.cpp");
		ThreePassRuleSet ruleSet = loader.loadThreePassRules();

		ConversionResult result = converter.convertSourceThreePass(cpp, ruleSet);
		writeOutput("mrule_malloc_string_assignment", cpp, result);

		String normalized = stripComments(result.getCsCode()).replaceAll("\\s+", "");
		assertThat(normalized).contains("stringpszName;");
		assertThat(normalized).contains("pszName=\"\";");
		assertThat(normalized).doesNotContain("malloc");
		assertThat(result.getTransformErrors()).isEmpty();
		assertOutputFilesExist("mrule_malloc_string_assignment");
	}

	@Test
	@DisplayName("test_mrule_malloc_multiple_vars.cpp — 同一ブロック内複数変数の skip: マッチを出力")
	void outputMruleMallocMultipleVars() throws IOException {
		String cpp = loadCppFile("test_mrule_malloc_multiple_vars.cpp");
		ThreePassRuleSet ruleSet = loader.loadThreePassRules();

		ConversionResult result = converter.convertSourceThreePass(cpp, ruleSet);
		writeOutput("mrule_malloc_multiple_vars", cpp, result);

		assertOutputFilesExist("mrule_malloc_multiple_vars");
	}

	@Test
	@DisplayName("test_mrule_malloc_no_match.cpp — malloc なし LPSTR のネガティブケースを出力")
	void outputMruleMallocNoMatch() throws IOException {
		String cpp = loadCppFile("test_mrule_malloc_no_match.cpp");
		ThreePassRuleSet ruleSet = loader.loadThreePassRules();

		ConversionResult result = converter.convertSourceThreePass(cpp, ruleSet);
		writeOutput("mrule_malloc_no_match", cpp, result);

		assertOutputFilesExist("mrule_malloc_no_match");
	}

	@Test
	@DisplayName("free() 呼び出し削除 — free_call_removal.mrule の適用を出力")
	void outputMruleFreeCallRemoval() throws IOException {
		String cpp = "void f() { LPSTR pszName; pszName = (char*)malloc(256); free(pszName); }";
		ThreePassRuleSet ruleSet = loader.loadThreePassRules();

		ConversionResult result = converter.convertSourceThreePass(cpp, ruleSet);
		writeOutput("mrule_free_call_removal", cpp, result);

		assertOutputFilesExist("mrule_free_call_removal");
	}

	@Test
	@DisplayName("test_mrule_skip_triple.cpp — 3 find spec / 2 skip: で離れた3文を相関付けて出力")
	void outputMruleSkipTriple() throws IOException {
		String cpp = loadCppFile("test_mrule_skip_triple.cpp");
		ThreePassRuleSet ruleSet = loader.loadThreePassRules();

		ConversionResult result = converter.convertSourceThreePass(cpp, ruleSet);
		writeOutput("mrule_skip_triple", cpp, result);

		assertOutputFilesExist("mrule_skip_triple");
	}

	// =========================================================================
	// 動的ルール（外部値）テスト
	// =========================================================================

	@Test
	@DisplayName("test_enum_cast.cpp — 外部提供 enum メンバ名から動的キャストルールを生成して出力")
	void outputEnumCast() throws IOException {
		String cpp = loadCppFile("test_enum_cast.cpp");

		// 別処理（字句解析・AST解析）で収集した enum メンバ名（外部提供）
		List<String> itemKindMembers = List.of("ITEM_NONE", "ITEM_APPLE", "ITEM_BANANA", "ITEM_CHERRY", "ITEM_GRAPE");
		List<String> selectStateMembers = List.of("SEL_NONE", "SEL_SINGLE", "SEL_MULTI");

		DynamicRuleGenerator generator = new DynamicRuleGenerator(loader);

		// ItemKind: int との比較・代入に (ItemKind) キャストを付与
		List<ConversionRule> itemKindRules = generator.generateFromValues(itemKindMembers,
				List.of(new DynamicRuleSpec.FromToTemplate("COLLECTED", "(ItemKind) COLLECTED")), "ItemKind-enum");

		// SelectState: int との比較・代入に (SelectState) キャストを付与
		List<ConversionRule> selectStateRules = generator.generateFromValues(selectStateMembers,
				List.of(new DynamicRuleSpec.FromToTemplate("COLLECTED", "(SelectState) COLLECTED")),
				"SelectState-enum");

		// 既存の本番ルール + 動的生成ルールを組み合わせた ThreePassRuleSet を構築
		ThreePassRuleSet baseRuleSet = loader.loadThreePassRules();
		List<List<ConversionRule>> mainPhases = new ArrayList<>(baseRuleSet.mainPhases());
		mainPhases.add(itemKindRules);
		mainPhases.add(selectStateRules);
		ThreePassRuleSet ruleSet = new ThreePassRuleSet(baseRuleSet.prePhases(), mainPhases, baseRuleSet.postPhases(),
				baseRuleSet.combyPhases(), baseRuleSet.dynamicSpecs());

		ConversionResult result = converter.convertSourceThreePass(cpp, ruleSet);
		writeOutput("enum_cast", cpp, result);

		// enum キャストが付与されること
		assertThat(result.getCsCode()).contains("(ItemKind)");
		assertThat(result.getCsCode()).contains("(SelectState)");
		// エラーなし
		assertThat(result.getTransformErrors()).isEmpty();
		assertOutputFilesExist("enum_cast");
	}

	// =========================================================================
	// A1: ニアミス候補テスト
	// =========================================================================

	@Test
	@DisplayName("A1: if条件内のAfxMessageBoxが「;」欠落でニアミス検出される")
	void nearMissAfxMessageBoxInIfCondition() throws IOException {
		// AfxMessageBox ルールは末尾 ";" が必須。if 条件式では ";" の代わりに
		// ")" が続くためルールが発火せず、ニアミス（depth==patternSize-1）として検出される。
		String cpp = "void f() { if (AfxMessageBox(\"error\", MB_OK | MB_ICONERROR)) { } }";
		List<ConversionRule> rules = loadRuleFile("AfxMessageBox.rule");

		ConversionResult result = converter.convertSource(cpp, rules);
		writeOutputWithRules("near_miss_afxmessagebox", cpp, result, rules);

		// ニアミス候補が検出されること
		assertThat(result.getDiagnosticCandidates()).isNotEmpty().anyMatch(c -> "near_miss".equals(c.reasonCategory()));

		// ニアミス候補には mismatchPatternIndex が記録されていること（A1 フィールド）
		assertThat(result.getDiagnosticCandidates()).filteredOn(c -> "near_miss".equals(c.reasonCategory()))
				.allMatch(c -> c.mismatchPatternIndex() != null);

		// レポートにニアミスセクションが出力されること
		String report = Files.readString(OUTPUT_DIR.resolve("near_miss_afxmessagebox.report.txt"),
				StandardCharsets.UTF_8);
		assertThat(report).contains("--- ニアミス候補");
		assertThat(report).contains("AfxMessageBox.rule");
		assertThat(report).contains("不一致位置");

		assertOutputFilesExist("near_miss_afxmessagebox");
	}

	@Test
	@DisplayName("A1: ニアミスレポートに正しい不一致位置と期待トークンが記載される")
	void nearMissReportContentIsCorrect() throws IOException {
		// "AfxMessageBox ( AP00 , MB_OK | MB_ICONERROR ) ;" は patternSize=9
		// if 条件式中では pattern[8]=";" の位置に ")" が来るため depth=8 でニアミス
		String cpp = "void f() { if (AfxMessageBox(\"critical\", MB_OK | MB_ICONERROR)) { } }";
		List<ConversionRule> rules = loadRuleFile("AfxMessageBox.rule");

		ConversionResult result = converter.convertSource(cpp, rules);
		writeOutputWithRules("near_miss_report_detail", cpp, result, rules);

		// 不一致位置 8 のニアミス候補が存在すること
		boolean hasIndexAt8 = result.getDiagnosticCandidates().stream()
				.filter(c -> "near_miss".equals(c.reasonCategory()))
				.anyMatch(c -> Integer.valueOf(8).equals(c.mismatchPatternIndex()));
		assertThat(hasIndexAt8).isTrue();

		// 期待トークン ";" の候補があること（rule 1 の最終トークン）
		boolean hasExpectedSemicolon = result.getDiagnosticCandidates().stream()
				.filter(c -> "near_miss".equals(c.reasonCategory())).anyMatch(c -> ";".equals(c.expectedToken()));
		assertThat(hasExpectedSemicolon).isTrue();

		// レポートに不一致位置と期待トークンが記載されること
		String report = Files.readString(OUTPUT_DIR.resolve("near_miss_report_detail.report.txt"),
				StandardCharsets.UTF_8);
		assertThat(report).contains("不一致位置: パターントークン [8]");
		assertThat(report).contains("期待トークン: ;");
		// 統計行にニアミス件数が表示されること
		assertThat(report).contains("ニアミス:");

		assertOutputFilesExist("near_miss_report_detail");
	}

	@Test
	@DisplayName("A1: 完全一致するコードではニアミス候補が生成されない")
	void noNearMissWhenCodeFullyMatches() throws IOException {
		// ";" があれば AfxMessageBox ルールが完全一致して変換される
		// 変換後はそのトークンが消えるため、ニアミス候補も生成されない
		String cpp = "void f() { AfxMessageBox(\"ok\", MB_OK | MB_ICONERROR); }";
		List<ConversionRule> rules = loadRuleFile("AfxMessageBox.rule");

		ConversionResult result = converter.convertSource(cpp, rules);
		writeOutputWithRules("near_miss_fully_matched", cpp, result, rules);

		// 変換が適用されていること
		assertThat(result.getCsCode()).contains("MessageBox.Show");
		// ニアミス候補がゼロ件であること
		long nearMissCount = result.getDiagnosticCandidates().stream()
				.filter(c -> "near_miss".equals(c.reasonCategory())).count();
		assertThat(nearMissCount).isEqualTo(0);

		// レポートにニアミスセクションがないこと
		String report = Files.readString(OUTPUT_DIR.resolve("near_miss_fully_matched.report.txt"),
				StandardCharsets.UTF_8);
		assertThat(report).doesNotContain("--- ニアミス候補");

		assertOutputFilesExist("near_miss_fully_matched");
	}

	@Test
	@DisplayName("A1: 無関係なコードではニアミス候補が0件（統計行にも表示されない）")
	void noNearMissForCompletelyUnrelatedCode() throws IOException {
		// AfxMessageBox パターンとは先頭トークンが一切一致しない
		String cpp = "int add(int a, int b) { return a + b; }";
		List<ConversionRule> rules = loadRuleFile("AfxMessageBox.rule");

		ConversionResult result = converter.convertSource(cpp, rules);
		writeOutputWithRules("near_miss_unrelated", cpp, result, rules);

		long nearMissCount = result.getDiagnosticCandidates().stream()
				.filter(c -> "near_miss".equals(c.reasonCategory())).count();
		assertThat(nearMissCount).isEqualTo(0);

		String report = Files.readString(OUTPUT_DIR.resolve("near_miss_unrelated.report.txt"), StandardCharsets.UTF_8);
		assertThat(report).doesNotContain("--- ニアミス候補");
		// 統計行に "(ニアミス: N 件)" が表示されていないこと
		assertThat(report).doesNotContain("ニアミス:");

		assertOutputFilesExist("near_miss_unrelated");
	}

	// =========================================================================
	// ユーティリティ
	// =========================================================================

	/**
	 * 指定ベース名の出力ファイルが存在することを確認する。
	 *
	 * @param basename
	 *            ベース名（拡張子なし）
	 */
	private void assertOutputFilesExist(String basename) {
		assertThat(OUTPUT_DIR.resolve(basename + ".cpp")).exists();
		assertThat(OUTPUT_DIR.resolve(basename + ".cs")).exists();
		assertThat(OUTPUT_DIR.resolve(basename + ".report.txt")).exists();
		assertThat(OUTPUT_DIR.resolve(basename + ".report.html")).exists();
		assertThat(OUTPUT_DIR.resolve(basename + ".treedump.txt")).exists();
		assertThat(OUTPUT_DIR.resolve(basename + ".xlsx")).exists();
	}

	/**
	 * 変換結果を outputs/test に書き出す。 入力 cpp を basename.cpp に保存し、出力は basename.cs に出力する。
	 */
	private void writeOutput(String basename, String cpp, ConversionResult result) throws IOException {
		Files.createDirectories(OUTPUT_DIR);
		Path inputPath = OUTPUT_DIR.resolve(basename + ".cpp");
		Path outputPath = OUTPUT_DIR.resolve(basename + ".cs");
		Files.writeString(inputPath, cpp, StandardCharsets.UTF_8);
		writer.write(inputPath, outputPath, cpp, result);
	}

	/**
	 * 変換結果を outputs/test に書き出す（A2 ルール有効度統計付き）。 allRules
	 * を渡すことでレポートに未使用ルール統計とニアミス候補セクションも出力される。
	 */
	private void writeOutputWithRules(String basename, String cpp, ConversionResult result,
			List<ConversionRule> allRules) throws IOException {
		Files.createDirectories(OUTPUT_DIR);
		Path inputPath = OUTPUT_DIR.resolve(basename + ".cpp");
		Path outputPath = OUTPUT_DIR.resolve(basename + ".cs");
		Files.writeString(inputPath, cpp, StandardCharsets.UTF_8);
		writer.write(inputPath, outputPath, cpp, result, allRules);
	}

	@Test
	@DisplayName(".xlsx は出力されない（Excel 出力機能削除済み）")
	void outputWithoutExcel() throws IOException {
		String cpp = loadCppFile("test_math.cpp");
		List<ConversionRule> rules = loadRuleFile("math_functions.rule");
		ConversionResult result = new CppToCSharpConverter().convertSource(cpp, rules);

		Files.createDirectories(OUTPUT_DIR);
		Path inputPath = OUTPUT_DIR.resolve("no_excel_test.cpp");
		Path outputPath = OUTPUT_DIR.resolve("no_excel_test.cs");
		Files.writeString(inputPath, cpp, StandardCharsets.UTF_8);
		new ConversionOutputWriter().write(inputPath, outputPath, cpp, result);

		assertThat(outputPath).exists();
		assertThat(OUTPUT_DIR.resolve("no_excel_test.xlsx")).doesNotExist();
	}

	// =========================================================================
	// A2: ルール有効度統計テスト
	// =========================================================================

	@Test
	@DisplayName("A2: 発火回数0のルールがレポートの『未使用ルール』に記載される")
	void ruleEffectivenessStatistics_deadRulesAppearInReport() throws Exception {
		// sin ルールのみ発火するコード。AfxMessageBox ルールは発火しない。
		String cpp = "double f(double x) { return sin(x); }";
		List<ConversionRule> sinRules = loadRuleFile("math_functions.rule");
		List<ConversionRule> msgRules = loadRuleFile("AfxMessageBox.rule");
		List<ConversionRule> allRules = new ArrayList<>();
		allRules.addAll(sinRules);
		allRules.addAll(msgRules);

		ConversionResult result = converter.convertSource(cpp, allRules);

		// allRules を渡してレポートに統計を出力
		Files.createDirectories(OUTPUT_DIR);
		Path inputPath = OUTPUT_DIR.resolve("rule_effectiveness_test.cpp");
		Path outputPath = OUTPUT_DIR.resolve("rule_effectiveness_test.cs");
		Files.writeString(inputPath, cpp, java.nio.charset.StandardCharsets.UTF_8);
		writer.write(inputPath, outputPath, cpp, result, allRules);

		String report = Files.readString(OUTPUT_DIR.resolve("rule_effectiveness_test.report.txt"));
		// 統計セクションが存在すること
		assertThat(report).contains("ルール有効度統計");
		assertThat(report).contains("未使用ルール");
		// sin は発火 → AfxMessageBox 系ルールは未使用
		assertThat(report).contains("AfxMessageBox.rule");
	}

	@Test
	@DisplayName("A2: 全ルールが発火した場合は未使用ルール0件と記載される")
	void ruleEffectivenessStatistics_allRulesFired() throws Exception {
		String cpp = "double f(double x) { return sin(x); }";
		// math_functions.rule のみ読み込み（sinルールが発火）
		List<ConversionRule> rules = loadRuleFile("math_functions.rule");
		// sin のみ発火するルールだけ渡す（ただし複数ルールの中の一部が発火しない場合もある）

		ConversionResult result = converter.convertSource(cpp, rules);

		Files.createDirectories(OUTPUT_DIR);
		Path inputPath = OUTPUT_DIR.resolve("rule_effectiveness_all_fired.cpp");
		Path outputPath = OUTPUT_DIR.resolve("rule_effectiveness_all_fired.cs");
		Files.writeString(inputPath, cpp, java.nio.charset.StandardCharsets.UTF_8);
		writer.write(inputPath, outputPath, cpp, result, rules);

		String report = Files.readString(OUTPUT_DIR.resolve("rule_effectiveness_all_fired.report.txt"));
		assertThat(report).contains("ルール有効度統計");
		// 発火ルール数 >= 1
		assertThat(report).contains("発火ルール数");
	}
}
