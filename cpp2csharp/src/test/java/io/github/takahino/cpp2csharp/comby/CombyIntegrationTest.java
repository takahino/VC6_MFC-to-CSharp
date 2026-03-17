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

package io.github.takahino.cpp2csharp.comby;

import io.github.takahino.cpp2csharp.converter.ConversionResult;
import io.github.takahino.cpp2csharp.converter.CppToCSharpConverter;
import io.github.takahino.cpp2csharp.output.ConversionOutputWriter;
import io.github.takahino.cpp2csharp.rule.ConversionRuleLoader.ThreePassRuleSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * COMBY フェーズの統合テスト。
 *
 * <p>
 * catch_cexception.crule と null_check.crule を用いて複数行構造の変換を検証し、 結果を
 * {@code outputs/test/} に出力する。
 * </p>
 */
@DisplayName("COMBY フェーズ統合テスト")
class CombyIntegrationTest {

	private static final Path OUTPUT_DIR = Paths.get(System.getProperty("user.dir")).resolve("outputs/test");

	private CppToCSharpConverter converter;
	private ConversionOutputWriter writer;
	private CombyRuleLoader combyRuleLoader;

	@BeforeEach
	void setUp() {
		converter = new CppToCSharpConverter();
		writer = new ConversionOutputWriter();
		combyRuleLoader = new CombyRuleLoader();
	}

	// =========================================================================
	// テストメソッド
	// =========================================================================

	@Test
	@DisplayName("catchCExceptionSingleLine: 単行 catch ブロックの CException → Exception 変換")
	void catchCExceptionSingleLine() throws IOException {
		String cpp = "void f() { try { doWork(); } catch ( CException * e ) { e->Delete(); } }";

		List<CombyRule> catchRules = loadCatchCExceptionRule();
		ThreePassRuleSet ruleSet = new ThreePassRuleSet(List.of(), List.of(), List.of(), List.of(catchRules),
				List.of());

		ConversionResult result = converter.convertSourceThreePass(cpp, ruleSet);
		writeOutput("comby_catch_single", cpp, result);

		assertThat(result.getCsCode()).contains("catch ( Exception e )");
		assertOutputFilesExist("comby_catch_single");
	}

	@Test
	@DisplayName("catchCExceptionMultiLine: 複数行 catch ブロックの CException → Exception 変換")
	void catchCExceptionMultiLine() throws IOException {
		String cpp = "void f() {\n" + "    try {\n" + "        doWork();\n" + "        doMore();\n"
				+ "    } catch ( CException * e ) {\n" + "        e->ReportError();\n" + "        e->Delete();\n"
				+ "        return;\n" + "    }\n" + "}";

		List<CombyRule> catchRules = loadCatchCExceptionRule();
		ThreePassRuleSet ruleSet = new ThreePassRuleSet(List.of(), List.of(), List.of(), List.of(catchRules),
				List.of());

		ConversionResult result = converter.convertSourceThreePass(cpp, ruleSet);
		writeOutput("comby_catch_multiline", cpp, result);

		assertThat(result.getCsCode()).contains("catch ( Exception e )");
		// ボディが保持されていること
		assertThat(result.getCsCode()).contains("e->ReportError()");
		assertThat(result.getCsCode()).contains("e->Delete()");
		assertOutputFilesExist("comby_catch_multiline");
	}

	@Test
	@DisplayName("nullCheckBlockReplacement: NULL → null の if ブロック変換")
	void nullCheckBlockReplacement() throws IOException {
		String cpp = "void f(Obj* pObj) { if ( pObj != NULL ) { pObj->Process(); } }";

		List<CombyRule> nullRules = loadNullCheckRule();
		ThreePassRuleSet ruleSet = new ThreePassRuleSet(List.of(), List.of(), List.of(), List.of(nullRules), List.of());

		ConversionResult result = converter.convertSourceThreePass(cpp, ruleSet);
		writeOutput("comby_null_check", cpp, result);

		assertThat(result.getCsCode()).contains("!= null");
		assertOutputFilesExist("comby_null_check");
	}

	@Test
	@DisplayName("combyPhaseCombined: catch_cexception + null_check の同一フェーズ適用")
	void combyPhaseCombined() throws IOException {
		String cpp = "void f(Obj* pObj) {\n" + "    if ( pObj != NULL ) {\n" + "        try {\n"
				+ "            pObj->DoWork();\n" + "        } catch ( CException * e ) {\n"
				+ "            e->Delete();\n" + "        }\n" + "    }\n" + "}";

		List<CombyRule> catchRules = loadCatchCExceptionRule();
		List<CombyRule> nullRules = loadNullCheckRule();
		// 同一フェーズに両ルールを含める
		List<CombyRule> combinedPhase = new java.util.ArrayList<>();
		combinedPhase.addAll(catchRules);
		combinedPhase.addAll(nullRules);

		ThreePassRuleSet ruleSet = new ThreePassRuleSet(List.of(), List.of(), List.of(), List.of(combinedPhase),
				List.of());

		ConversionResult result = converter.convertSourceThreePass(cpp, ruleSet);
		writeOutput("comby_combined", cpp, result);

		assertThat(result.getCsCode()).contains("catch ( Exception e )");
		assertThat(result.getCsCode()).contains("!= null");
		assertOutputFilesExist("comby_combined");
	}

	// =========================================================================
	// ヘルパー
	// =========================================================================

	private List<CombyRule> loadCatchCExceptionRule() {
		String content = "# CException catch ブロック → Exception catch ブロック（複数行ボディ対応）\n"
				+ "from: catch ( CException * :[var] ) {:[body]}\n" + "to: catch ( Exception :[var] ) {:[body]}\n"
				+ "test: catch ( CException * e ) { e->Delete(); }\n"
				+ "assrt: catch ( Exception e ) { e->Delete(); }\n";
		return combyRuleLoader.parseContent(content, "catch_cexception.crule");
	}

	private List<CombyRule> loadNullCheckRule() {
		String content = "# NULL ポインタチェックブロック（複数行ボディ対応）\n" + "from: if ( :[ptr] != NULL ) {:[body]}\n"
				+ "to: if ( :[ptr] != null ) {:[body]}\n" + "test: if ( p != NULL ) { p->DoWork(); }\n"
				+ "assrt: if ( p != null ) { p->DoWork(); }\n";
		return combyRuleLoader.parseContent(content, "null_check.crule");
	}

	private void writeOutput(String basename, String cpp, ConversionResult result) throws IOException {
		Files.createDirectories(OUTPUT_DIR);
		Path inputPath = OUTPUT_DIR.resolve(basename + ".cpp");
		Path outputPath = OUTPUT_DIR.resolve(basename + ".cs");
		Files.writeString(inputPath, cpp, StandardCharsets.UTF_8);
		writer.write(inputPath, outputPath, cpp, result);
	}

	private void assertOutputFilesExist(String basename) {
		assertThat(OUTPUT_DIR.resolve(basename + ".cpp")).exists();
		assertThat(OUTPUT_DIR.resolve(basename + ".cs")).exists();
		assertThat(OUTPUT_DIR.resolve(basename + ".report.txt")).exists();
		assertThat(OUTPUT_DIR.resolve(basename + ".report.html")).exists();
		assertThat(OUTPUT_DIR.resolve(basename + ".treedump.txt")).exists();
		// xlsx は MAIN フェーズ（Transformer）が実行された場合のみ生成される。
		// comby のみのテストでは MAIN フェーズが空なため xlsx は生成されない。
	}
}
