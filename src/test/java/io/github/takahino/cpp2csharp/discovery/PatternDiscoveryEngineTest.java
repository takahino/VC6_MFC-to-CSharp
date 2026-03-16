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

package io.github.takahino.cpp2csharp.discovery;

import io.github.takahino.cpp2csharp.rule.ConversionRuleLoader;
import io.github.takahino.cpp2csharp.rule.ConversionRuleLoader.ThreePassRuleSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PatternDiscoveryEngine} のユニットテスト。
 */
class PatternDiscoveryEngineTest {

    private final PatternDiscoveryEngine engine = new PatternDiscoveryEngine();

    // -------------------------------------------------------------------------
    // GLOBAL_FUNC 検出
    // -------------------------------------------------------------------------

    @Test
    void globalFunc_detectedWhenNotPrecededByDotOrArrow() throws IOException {
        Path file = writeTempFile("test.cpp", "void f() { AfxMessageBox(\"Hello\"); }");
        List<PatternDiscoveryEngine.RawOccurrence> occ = engine.scanFile(file);

        assertThat(occ).anyMatch(o ->
                o.type() == PatternType.GLOBAL_FUNC
                && o.identifierName().equals("AfxMessageBox"));
    }

    @Test
    void globalFunc_notDetectedWhenPrecededByDot() throws IOException {
        Path file = writeTempFile("test.cpp", "obj.Format(\"%d\", x);");
        List<PatternDiscoveryEngine.RawOccurrence> occ = engine.scanFile(file);

        assertThat(occ).noneMatch(o ->
                o.type() == PatternType.GLOBAL_FUNC
                && o.identifierName().equals("Format"));
    }

    @Test
    void globalFunc_notDetectedWhenPrecededByArrow() throws IOException {
        Path file = writeTempFile("test.cpp", "ptr->GetValue(x);");
        List<PatternDiscoveryEngine.RawOccurrence> occ = engine.scanFile(file);

        assertThat(occ).noneMatch(o ->
                o.type() == PatternType.GLOBAL_FUNC
                && o.identifierName().equals("GetValue"));
    }

    @Test
    void globalFunc_notDetectedForClassMethodDefinition() throws IOException {
        // ClassName::MethodName( は自クラス定義のため GLOBAL_FUNC に含めない
        Path file = writeTempFile("test.cpp", "void MyClass::MyMethod(int x) {}");
        List<PatternDiscoveryEngine.RawOccurrence> occ = engine.scanFile(file);

        assertThat(occ).noneMatch(o ->
                o.type() == PatternType.GLOBAL_FUNC
                && o.identifierName().equals("MyMethod"));
    }

    // -------------------------------------------------------------------------
    // METHOD_CALL 検出
    // -------------------------------------------------------------------------

    @Test
    void methodCall_detectedForDotOperator() throws IOException {
        Path file = writeTempFile("test.cpp", "str.Left(5);");
        List<PatternDiscoveryEngine.RawOccurrence> occ = engine.scanFile(file);

        assertThat(occ).anyMatch(o ->
                o.type() == PatternType.METHOD_CALL
                && o.identifierName().equals("Left")
                && ".".equals(o.accessOperator()));
    }

    @Test
    void methodCall_detectedForArrowOperator() throws IOException {
        Path file = writeTempFile("test.cpp", "pWnd->ShowWindow(SW_SHOW);");
        List<PatternDiscoveryEngine.RawOccurrence> occ = engine.scanFile(file);

        assertThat(occ).anyMatch(o ->
                o.type() == PatternType.METHOD_CALL
                && o.identifierName().equals("ShowWindow")
                && "->".equals(o.accessOperator()));
    }

    // -------------------------------------------------------------------------
    // TYPE_NAME 検出
    // -------------------------------------------------------------------------

    @Test
    void typeName_detectedForKnownMfcType() throws IOException {
        Path file = writeTempFile("test.cpp", "CString str;");
        List<PatternDiscoveryEngine.RawOccurrence> occ = engine.scanFile(file);

        assertThat(occ).anyMatch(o ->
                o.type() == PatternType.TYPE_NAME
                && o.identifierName().equals("CString"));
    }

    @Test
    void typeName_notDetectedForUnknownType() throws IOException {
        Path file = writeTempFile("test.cpp", "MyCustomType x;");
        List<PatternDiscoveryEngine.RawOccurrence> occ = engine.scanFile(file);

        assertThat(occ).noneMatch(o -> o.type() == PatternType.TYPE_NAME);
    }

    // -------------------------------------------------------------------------
    // 引数カウント
    // -------------------------------------------------------------------------

    @Test
    void countArgs_zeroForEmptyParens() throws IOException {
        Path file = writeTempFile("test.cpp", "Foo();");
        List<PatternDiscoveryEngine.RawOccurrence> occ = engine.scanFile(file);

        assertThat(occ).anyMatch(o ->
                o.identifierName().equals("Foo") && o.argCount() == 0);
    }

    @Test
    void countArgs_oneForSingleArg() throws IOException {
        Path file = writeTempFile("test.cpp", "Foo(x);");
        List<PatternDiscoveryEngine.RawOccurrence> occ = engine.scanFile(file);

        assertThat(occ).anyMatch(o ->
                o.identifierName().equals("Foo") && o.argCount() == 1);
    }

    @Test
    void countArgs_correctForNestedParens() throws IOException {
        Path file = writeTempFile("test.cpp", "Foo(bar(a, b), c);");
        List<PatternDiscoveryEngine.RawOccurrence> occ = engine.scanFile(file);

        assertThat(occ).anyMatch(o ->
                o.identifierName().equals("Foo") && o.argCount() == 2);
    }

    // -------------------------------------------------------------------------
    // discover 結合テスト
    // -------------------------------------------------------------------------

    @Test
    void discover_aggregatesAcrossFiles(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("a.cpp"), "AfxMessageBox(\"A\");", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("b.cpp"), "AfxMessageBox(\"B\"); AfxMessageBox(\"C\");", StandardCharsets.UTF_8);

        ThreePassRuleSet emptyRuleSet = new ThreePassRuleSet(
                List.of(), List.of(), List.of(), List.of(), List.of());
        PatternDiscoveryResult result = engine.discover(tempDir, emptyRuleSet);

        assertThat(result.allPatterns()).anyMatch(p ->
                p.identifierName().equals("AfxMessageBox")
                && p.occurrenceCount() == 3
                && p.occurrenceFiles().size() == 2);
    }

    @Test
    void discover_classifiesRuleCoverage(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("test.cpp"), "AfxMessageBox(\"Hello\");", StandardCharsets.UTF_8);

        // AfxMessageBox をカバーするルールセットを読み込む
        ThreePassRuleSet ruleSet = new ConversionRuleLoader().loadThreePassRules();
        PatternDiscoveryResult result = engine.discover(tempDir, ruleSet);

        // AfxMessageBox はルールが存在するはず
        assertThat(result.coveredPatterns()).anyMatch(p ->
                p.identifierName().equals("AfxMessageBox"));
    }

    // -------------------------------------------------------------------------
    // 引数数ごとの区別
    // -------------------------------------------------------------------------

    @Test
    void methodCall_distinguishedByArgCount() throws IOException {
        // Format(1引数) と Format(2引数) は別パターンとして集約される
        Path file = writeTempFile("test.cpp",
                "str.Format(\"%d\"); str.Format(\"%d\", x); str.Format(\"%d\", x);");
        List<PatternDiscoveryEngine.RawOccurrence> occ = engine.scanFile(file);

        long arg1count = occ.stream()
                .filter(o -> o.identifierName().equals("Format") && o.argCount() == 1).count();
        long arg2count = occ.stream()
                .filter(o -> o.identifierName().equals("Format") && o.argCount() == 2).count();

        assertThat(arg1count).isEqualTo(1);
        assertThat(arg2count).isEqualTo(2);
    }

    // -------------------------------------------------------------------------
    // ヘルパー
    // -------------------------------------------------------------------------

    private Path writeTempFile(String name, String content) throws IOException {
        Path tmp = Files.createTempFile(name.replace(".cpp", ""), ".cpp");
        tmp.toFile().deleteOnExit();
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        return tmp;
    }
}
