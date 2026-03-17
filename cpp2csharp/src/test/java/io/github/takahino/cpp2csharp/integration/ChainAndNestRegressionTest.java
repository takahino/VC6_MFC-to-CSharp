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
import io.github.takahino.cpp2csharp.matcher.CppParserFactory;
import io.github.takahino.cpp2csharp.rule.ConversionRuleLoader;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * LR寄せ計画の検証ケース: チェイン・ネスト・拒否系の回帰テスト。
 *
 * <p>
 * 計画の Phase 5 で定義した重点テストケースをカバーする。
 * </p>
 */
@DisplayName("チェイン・ネスト・拒否系 回帰テスト")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChainAndNestRegressionTest {

	private CppToCSharpConverter converter;
	private List<List<ConversionRule>> rulesByPhase;

	@BeforeAll
	void loadRules() throws IOException {
		ConversionRuleLoader loader = new ConversionRuleLoader(CppParserFactory.asLexerFactory());
		List<List<ConversionRule>> phases = new ArrayList<>();
		phases.add(loader.loadFromResource("rules/main/[01]_ブロックコメント/pointer.rule"));
		List<ConversionRule> phase2 = new ArrayList<>();
		phase2.addAll(loader.loadFromResource("rules/main/[02]_標準置き換え/AfxMessageBox.rule"));
		phase2.addAll(loader.loadFromResource("rules/main/[02]_標準置き換え/array_declaration.rule"));
		phase2.addAll(loader.loadFromResource("rules/main/[02]_標準置き換え/math_functions.rule"));
		phase2.addAll(loader.loadFromResource("rules/main/[02]_標準置き換え/type_conversions.rule"));
		phase2.addAll(loader.loadFromResource("rules/main/[02]_標準置き換え/printf_functions.rule"));
		phase2.addAll(loader.loadFromResource("rules/main/[02]_標準置き換え/format_migration.rule"));
		phase2.addAll(loader.loadFromResource("rules/main/[02]_標準置き換え/migration_helper.rule"));
		phase2.addAll(loader.loadFromResource("rules/main/[02]_標準置き換え/cstring_methods.rule"));
		phases.add(phase2);
		phases.add(loader.loadFromResource("rules/main/[03]_パッチ置き換え/ToBool.rule"));
		rulesByPhase = phases;
	}

	@BeforeEach
	void setUp() {
		converter = new CppToCSharpConverter();
	}

	@Test
	@DisplayName("正常系: this->m_str.Left(5)")
	void thisArrowMStrLeft() throws IOException {
		String cpp = "void f() { CString s = this->m_str.Left(5); }";

		ConversionResult result = converter.convertSourceWithPhases(cpp, rulesByPhase);

		assertThat(result.getTransformErrors()).isEmpty();
		assertThat(result.getCsCode()).contains("Substring");
		assertThat(result.getCsCode()).contains("this");
		assertThat(result.getCsCode()).contains("m_str");
	}

	@Test
	@DisplayName("正常系: app.method().field.Left(5)")
	void appMethodFieldLeft() throws IOException {
		String cpp = "void f() { CString s = app.method().field.Left(5); }";

		ConversionResult result = converter.convertSourceWithPhases(cpp, rulesByPhase);

		assertThat(result.getTransformErrors()).isEmpty();
		assertThat(result.getCsCode()).contains("Substring");
		assertThat(result.getCsCode()).contains("app");
	}

	@Test
	@DisplayName("正常系: arr[0].Left(5)")
	void arrSubscriptLeft() throws IOException {
		String cpp = "void f() { CString s = arr[0].Left(5); }";

		ConversionResult result = converter.convertSourceWithPhases(cpp, rulesByPhase);

		assertThat(result.getTransformErrors()).isEmpty();
		assertThat(result.getCsCode()).contains("Substring");
		assertThat(result.getCsCode()).contains("arr");
	}

	@Test
	@DisplayName("正常系: MakeString(data).Left(10)")
	void makeStringLeft() throws IOException {
		String cpp = "void f() { CString s = MakeString(data).Left(10); }";

		ConversionResult result = converter.convertSourceWithPhases(cpp, rulesByPhase);

		assertThat(result.getTransformErrors()).isEmpty();
		assertThat(result.getCsCode()).contains("Substring");
		assertThat(result.getCsCode()).contains("MakeString");
	}

	@Test
	@DisplayName("正常系: time.Format(\"%Y\").Left(5)")
	void timeFormatLeft() throws IOException {
		String cpp = "void f() { CString s = time.Format(\"%Y\").Left(5); }";

		ConversionResult result = converter.convertSourceWithPhases(cpp, rulesByPhase);

		assertThat(result.getTransformErrors()).isEmpty();
		assertThat(result.getCsCode()).contains("MigrationHelper.Format");
		assertThat(result.getCsCode()).contains("Substring");
	}

	@Test
	@DisplayName("回帰系: str.Find(time.Format(\"%Y/%m/%d\")) — 引数ネスト")
	void strFindTimeFormat() throws IOException {
		String cpp = "void f() { int i = str.Find(time.Format(\"%Y/%m/%d\")); }";

		ConversionResult result = converter.convertSourceWithPhases(cpp, rulesByPhase);

		assertThat(result.getTransformErrors()).isEmpty();
		assertThat(result.getCsCode()).contains("MigrationHelper.Find");
		assertThat(result.getCsCode()).contains("MigrationHelper.Format");
	}

	@Test
	@DisplayName("回帰系: time.Format(\"%Y/%m/%d\").Find(\"/\") — ドットチェーン")
	void timeFormatFind() throws IOException {
		String cpp = "void f() { int i = time.Format(\"%Y/%m/%d\").Find(\"/\"); }";

		ConversionResult result = converter.convertSourceWithPhases(cpp, rulesByPhase);

		assertThat(result.getTransformErrors()).isEmpty();
		assertThat(result.getCsCode()).contains("MigrationHelper.Format");
		assertThat(result.getCsCode()).contains("MigrationHelper.Find");
	}

	@Test
	@DisplayName("拒否系: (a+b).Left(5) — 括弧付き二項演算はレシーバーにならないため変換されない")
	void rejectBinaryOpReceiver() throws IOException {
		String cpp = "void f() { CString s = (a + b).Left(5); }";

		ConversionResult result = converter.convertSourceWithPhases(cpp, rulesByPhase);

		assertThat(result.getTransformErrors()).isEmpty();
		assertThat(result.getCsCode()).doesNotContain("Substring");
		assertThat(result.getCsCode()).contains(".Left(5)");
	}

	@Test
	@DisplayName("拒否系: cond?x:y.Left(5) — y のみレシーバー、三項演算全体はレシーバーにならない")
	void rejectTernaryReceiver() throws IOException {
		String cpp = "void f() { CString s = cond ? x : y.Left(5); }";

		ConversionResult result = converter.convertSourceWithPhases(cpp, rulesByPhase);

		assertThat(result.getTransformErrors()).isEmpty();
		assertThat(result.getCsCode()).contains("Substring");
	}

	@Test
	@DisplayName("拒否系: a+b.Left(5) 括弧なし — b.Left(5) のみ変換")
	void rejectBinaryOpNoParens() throws IOException {
		String cpp = "void f() { CString s = a + b.Left(5); }";

		ConversionResult result = converter.convertSourceWithPhases(cpp, rulesByPhase);

		assertThat(result.getTransformErrors()).isEmpty();
		assertThat(result.getCsCode()).contains("Substring");
		assertThat(result.getCsCode()).contains("b");
	}
}
