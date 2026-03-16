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

@DisplayName("RulePatternParser テスト")
class RulePatternParserTest {

    @Test
    @DisplayName("AfxMessageBox 1引数がパースできる")
    void testAfxMessageBox1Arg() {
        List<ConversionToken> tokens = List.of(
                ConversionToken.of("AfxMessageBox"),
                ConversionToken.of("("),
                ConversionToken.of("ABSTRACT_PARAM00"),
                ConversionToken.of(")"),
                ConversionToken.of(";")
        );
        int count = RulePatternParser.parseArgumentCount(tokens);
        assertThat(count).as("1引数と判定されること").isEqualTo(1);
    }

    @Test
    @DisplayName("sin 1引数がパースできる")
    void testSin1Arg() {
        List<ConversionToken> tokens = List.of(
                ConversionToken.of("sin"),
                ConversionToken.of("("),
                ConversionToken.of("x"),
                ConversionToken.of(")")
        );
        int count = RulePatternParser.parseArgumentCount(tokens);
        assertThat(count).isEqualTo(1);
    }
}
