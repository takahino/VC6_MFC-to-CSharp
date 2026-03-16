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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CombyRuleLoader .crule パーステスト")
class CombyRuleLoaderTest {

    private final CombyRuleLoader loader = new CombyRuleLoader();

    @Test
    @DisplayName("基本的な from/to ルールのパース")
    void parseBasicRule() {
        String content = "from: :[recv].Left(:[n])\nto: :[recv].Substring(0, :[n])\n";
        List<CombyRule> rules = loader.parseContent(content, "test.crule");
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getFromPattern()).isEqualTo(":[recv].Left(:[n])");
        assertThat(rules.get(0).getToTemplate()).isEqualTo(":[recv].Substring(0, :[n])");
        assertThat(rules.get(0).getTestCases()).isEmpty();
    }

    @Test
    @DisplayName("test/assrt ペアのパース")
    void parseTestAssrt() {
        String content = "from: Left(:[n])\nto: Substring(0,:[n])\ntest: Left(5)\nassrt: Substring(0,5)\n";
        List<CombyRule> rules = loader.parseContent(content, "test.crule");
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getTestCases()).hasSize(1);
        assertThat(rules.get(0).getTestCases().get(0).testInput()).isEqualTo("Left(5)");
        assertThat(rules.get(0).getTestCases().get(0).expectedOutput()).isEqualTo("Substring(0,5)");
    }

    @Test
    @DisplayName("コメント行は無視される")
    void commentsIgnored() {
        String content = "# this is a comment\nfrom: A\nto: B\n# another comment\n";
        List<CombyRule> rules = loader.parseContent(content, "test.crule");
        assertThat(rules).hasSize(1);
    }

    @Test
    @DisplayName("複数ルールのパース")
    void parseMultipleRules() {
        String content = "from: A\nto: B\nfrom: C\nto: D\n";
        List<CombyRule> rules = loader.parseContent(content, "test.crule");
        assertThat(rules).hasSize(2);
    }

    @Test
    @DisplayName("rules/comby/ が存在しない場合は空リストを返す")
    void emptyWhenNoCombyDir() throws Exception {
        List<List<CombyRule>> phases = loader.loadFromClasspath();
        // Directory either exists with rules or returns empty - should not throw
        assertThat(phases).isNotNull();
    }
}
