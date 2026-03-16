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

package io.github.takahino.cpp2csharp.retokenize;

import io.github.takahino.cpp2csharp.tree.AstNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link Retokenizer} のユニットテスト。
 */
@DisplayName("Retokenizer テスト")
class RetokenizerTest {

    private final Retokenizer retokenizer = new Retokenizer();

    @Test
    @DisplayName("単純なトークン列を再トークン化できる")
    void testSimpleRetokenize() {
        // Create token nodes from scratch
        List<AstNode> nodes = List.of(
                AstNode.tokenNode("int", 1, 0, 0),
                AstNode.tokenNode("x", 1, 4, 1),
                AstNode.tokenNode("=", 1, 6, 2),
                AstNode.tokenNode("42", 1, 8, 3),
                AstNode.tokenNode(";", 1, 10, 4)
        );

        List<AstNode> result = retokenizer.retokenize(nodes, Map.of()).tokenNodes();

        // Should produce the same tokens
        assertThat(result).isNotEmpty();
        List<String> texts = result.stream().map(AstNode::getText).toList();
        assertThat(texts).containsExactly("int", "x", "=", "42", ";");
    }

    @Test
    @DisplayName("合成置換トークンを再トークン化して個別トークンに分解できる")
    void testRetokenizeReplacementToken() {
        // A replacement token (streamIndex = -1) that contains multiple C++ tokens concatenated
        // For example, a replacement node "Math.Sin(x)" should be split into tokens
        List<AstNode> nodes = List.of(
                AstNode.tokenNodeWithId("Math.Sin(x)", 1, 0, 100, -1)
        );

        List<AstNode> result = retokenizer.retokenize(nodes, Map.of()).tokenNodes();

        // Should be split into individual tokens
        assertThat(result).isNotEmpty();
        List<String> texts = result.stream().map(AstNode::getText).toList();
        // Math.Sin(x) contains: Math, ., Sin, (, x, )
        assertThat(texts).containsExactly("Math", ".", "Sin", "(", "x", ")");
    }

    @Test
    @DisplayName("空のトークン列を再トークン化すると空のリストになる")
    void testEmptyTokenList() {
        List<AstNode> nodes = List.of();
        List<AstNode> result = retokenizer.retokenize(nodes, Map.of()).tokenNodes();
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("再トークン化後のノードのストリームインデックスは新しい字句解析インデックス")
    void testStreamIndexAfterRetokenize() {
        List<AstNode> nodes = List.of(
                AstNode.tokenNode("int", 1, 0, 0),
                AstNode.tokenNode("x", 1, 4, 1)
        );

        List<AstNode> result = retokenizer.retokenize(nodes, Map.of()).tokenNodes();

        // All retokenized nodes should have non-negative streamIndex
        for (AstNode node : result) {
            assertThat(node.getStreamIndex()).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    @DisplayName("EOF トークンは結果に含まれない")
    void testNoEofToken() {
        List<AstNode> nodes = List.of(
                AstNode.tokenNode("x", 1, 0, 0)
        );

        List<AstNode> result = retokenizer.retokenize(nodes, Map.of()).tokenNodes();

        assertThat(result).noneMatch(n -> "<EOF>".equals(n.getText()));
    }

    @Test
    @DisplayName("複数の置換トークンをスペース区切りで結合して再トークン化する")
    void testMultipleReplacementTokens() {
        // Multiple replacement tokens (streamIndex = -1)
        List<AstNode> nodes = List.of(
                AstNode.tokenNodeWithId("MessageBox", 1, 0, 1, -1),
                AstNode.tokenNodeWithId(".", 1, 0, 2, -1),
                AstNode.tokenNodeWithId("Show", 1, 0, 3, -1)
        );

        List<AstNode> result = retokenizer.retokenize(nodes, Map.of()).tokenNodes();
        assertThat(result).isNotEmpty();
        List<String> texts = result.stream().map(AstNode::getText).toList();
        // All replacement tokens concatenated → "MessageBox.Show" → retokenized
        assertThat(texts).containsExactly("MessageBox", ".", "Show");
    }
}
