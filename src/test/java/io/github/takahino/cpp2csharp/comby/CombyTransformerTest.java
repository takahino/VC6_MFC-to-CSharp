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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CombyTransformer 変換テスト")
class CombyTransformerTest {

    private CombyTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new CombyTransformer();
    }

    @Test
    @DisplayName("単純なテキスト置換")
    void simpleReplacement() {
        CombyRule rule = new CombyRule("test", ":[recv].Left(:[n])", ":[recv].Substring(0, :[n])", List.of());
        String result = transformer.transformPhase("str.Left(5)", List.of(rule));
        assertThat(result).isEqualTo("str.Substring(0, 5)");
    }

    @Test
    @DisplayName("右端優先: 複数マッチで最も右のものを先に適用")
    void rightmostFirst() {
        CombyRule rule = new CombyRule("test", ":[recv].Left(:[n])", ":[recv].Substring(0,:[n])", List.of());
        String result = transformer.transformPhase("a.Left(1).Left(2)", List.of(rule));
        // Rightmost match first: a.Left(1).Substring(0,2), then a.Substring(0,1).Substring(0,2)
        assertThat(result).isEqualTo("a.Substring(0,1).Substring(0,2)");
    }

    @Test
    @DisplayName("収束するまで反復適用")
    void iteratesUntilConvergence() {
        CombyRule rule = new CombyRule("test", "List<:[type]>", "IList<:[type]>", List.of());
        String result = transformer.transformPhase("List<string> x;", List.of(rule));
        assertThat(result).isEqualTo("IList<string> x;");
    }

    @Test
    @DisplayName("マッチなし: テキスト変更なし")
    void noMatchNoChange() {
        CombyRule rule = new CombyRule("test", ":[recv].Left(:[n])", "X", List.of());
        String input = "Math.Sin(x)";
        String result = transformer.transformPhase(input, List.of(rule));
        assertThat(result).isEqualTo(input);
    }

    @Test
    @DisplayName("複数フェーズを順に適用")
    void multiplePhases() {
        CombyRule rule1 = new CombyRule("test", "List<:[t]>", "IList<:[t]>", List.of());
        CombyRule rule2 = new CombyRule("test", "IList<:[t]>", "Collection<:[t]>", List.of());
        String result = transformer.transformPhases("List<string>", List.of(
                List.of(rule1), List.of(rule2)));
        assertThat(result).isEqualTo("Collection<string>");
    }
}
