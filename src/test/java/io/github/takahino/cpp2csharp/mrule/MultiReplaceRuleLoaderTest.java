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

package io.github.takahino.cpp2csharp.mrule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link MultiReplaceRuleLoader} のユニットテスト。
 */
@DisplayName("MultiReplaceRuleLoader テスト")
class MultiReplaceRuleLoaderTest {

    private final MultiReplaceRuleLoader loader = new MultiReplaceRuleLoader();

    @Test
    @DisplayName("単純な find/replace ペアをパースできる")
    void testSimpleFindReplace() {
        String content = """
                find: BOOL
                replace: bool
                """;
        List<MultiReplaceRule> rules = loader.loadFromString(content, "test.mrule");
        assertThat(rules).hasSize(1);
        MultiReplaceRule rule = rules.get(0);
        assertThat(rule.getScope()).isEqualTo(MRuleScope.NONE);
        assertThat(rule.getFindSpecs()).hasSize(1);
        MRuleFindSpec spec = rule.getFindSpecs().get(0);
        assertThat(spec.pattern()).hasSize(1);
        assertThat(spec.pattern().get(0).getValue()).isEqualTo("BOOL");
        assertThat(spec.replacement()).isEqualTo("bool");
        assertThat(spec.skipBefore()).isFalse();
    }

    @Test
    @DisplayName("複数の find/replace ペアをパースできる（空行区切り）")
    void testMultipleRules() {
        String content = """
                find: BOOL
                replace: bool

                find: NULL
                replace: null
                """;
        List<MultiReplaceRule> rules = loader.loadFromString(content, "test.mrule");
        assertThat(rules).hasSize(2);
        assertThat(rules.get(0).getFindSpecs().get(0).replacement()).isEqualTo("bool");
        assertThat(rules.get(1).getFindSpecs().get(0).replacement()).isEqualTo("null");
    }

    @Test
    @DisplayName("scope: block が正しくパースされる")
    void testScopeBlock() {
        String content = """
                scope: block
                find: BOOL
                replace: bool
                """;
        List<MultiReplaceRule> rules = loader.loadFromString(content, "test.mrule");
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getScope()).isEqualTo(MRuleScope.BLOCK);
    }

    @Test
    @DisplayName("skip: が次の find の skipBefore=true を設定する")
    void testSkipBeforeFlag() {
        String content = """
                find: BOOL ( ABSTRACT_PARAM00 )
                replace: bool(ABSTRACT_PARAM00)
                skip:
                find: return ABSTRACT_PARAM00
                replace: return ABSTRACT_PARAM00
                """;
        List<MultiReplaceRule> rules = loader.loadFromString(content, "test.mrule");
        assertThat(rules).hasSize(1);
        MultiReplaceRule rule = rules.get(0);
        assertThat(rule.getFindSpecs()).hasSize(2);
        assertThat(rule.getFindSpecs().get(0).skipBefore()).isFalse();
        assertThat(rule.getFindSpecs().get(1).skipBefore()).isTrue();
    }

    @Test
    @DisplayName("コメント行はスキップされる")
    void testCommentLines() {
        String content = """
                # This is a comment
                find: BOOL
                # Another comment
                replace: bool
                """;
        List<MultiReplaceRule> rules = loader.loadFromString(content, "test.mrule");
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getFindSpecs().get(0).replacement()).isEqualTo("bool");
    }

    @Test
    @DisplayName("空のコンテンツでルールが空リストになる")
    void testEmptyContent() {
        List<MultiReplaceRule> rules = loader.loadFromString("", "test.mrule");
        assertThat(rules).isEmpty();
    }

    @Test
    @DisplayName("コメントのみのコンテンツでルールが空リストになる")
    void testOnlyComments() {
        String content = """
                # comment 1
                # comment 2
                """;
        List<MultiReplaceRule> rules = loader.loadFromString(content, "test.mrule");
        assertThat(rules).isEmpty();
    }

    @Test
    @DisplayName("複数 find spec を持つルールをパースできる（連続マッチ）")
    void testMultipleSpecsSingleRule() {
        String content = """
                find: BOOL ( ABSTRACT_PARAM00 )
                replace: bool(ABSTRACT_PARAM00)
                find: return ABSTRACT_PARAM00
                replace: return ABSTRACT_PARAM00
                """;
        List<MultiReplaceRule> rules = loader.loadFromString(content, "test.mrule");
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getFindSpecs()).hasSize(2);
        assertThat(rules.get(0).getFindSpecs().get(0).skipBefore()).isFalse();
        assertThat(rules.get(0).getFindSpecs().get(1).skipBefore()).isFalse();
    }

    @Test
    @DisplayName("ルール ID が sourceFile:ruleIndex 形式で生成される")
    void testRuleIdFormat() {
        String content = """
                find: BOOL
                replace: bool

                find: NULL
                replace: null
                """;
        List<MultiReplaceRule> rules = loader.loadFromString(content, "test.mrule");
        assertThat(rules.get(0).getRuleId()).isEqualTo("test.mrule:0");
        assertThat(rules.get(1).getRuleId()).isEqualTo("test.mrule:1");
    }

    @Test
    @DisplayName("scope: none が正しくパースされる")
    void testScopeNone() {
        String content = """
                scope: none
                find: BOOL
                replace: bool
                """;
        List<MultiReplaceRule> rules = loader.loadFromString(content, "test.mrule");
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getScope()).isEqualTo(MRuleScope.NONE);
    }

    @Test
    @DisplayName("複数 skip: が並ぶとき各 find spec の skipBefore が正しく設定される")
    void testMultipleSkipFlags() {
        String content = """
                find: A
                replace: a
                skip:
                find: B
                replace: b
                skip:
                find: C
                replace: c
                """;
        List<MultiReplaceRule> rules = loader.loadFromString(content, "test.mrule");
        assertThat(rules).hasSize(1);
        MultiReplaceRule rule = rules.get(0);
        assertThat(rule.getFindSpecs()).hasSize(3);
        assertThat(rule.getFindSpecs().get(0).skipBefore()).isFalse();
        assertThat(rule.getFindSpecs().get(1).skipBefore()).isTrue();
        assertThat(rule.getFindSpecs().get(2).skipBefore()).isTrue();
    }

    @Test
    @DisplayName("連続後 skip: のパターンで skipBefore フラグが正しく設定される")
    void testMixedConsecutiveAndSkip() {
        String content = """
                find: A
                replace: a
                find: B
                replace: b
                skip:
                find: C
                replace: c
                """;
        List<MultiReplaceRule> rules = loader.loadFromString(content, "test.mrule");
        assertThat(rules).hasSize(1);
        MultiReplaceRule rule = rules.get(0);
        assertThat(rule.getFindSpecs()).hasSize(3);
        assertThat(rule.getFindSpecs().get(0).skipBefore()).isFalse();
        assertThat(rule.getFindSpecs().get(1).skipBefore()).isFalse();
        assertThat(rule.getFindSpecs().get(2).skipBefore()).isTrue();
    }

    @Test
    @DisplayName("ABSTRACT_PARAM を含む find パターンが正しくトークン化される")
    void testAbstractParamInPattern() {
        String content = """
                find: AfxMessageBox ( ABSTRACT_PARAM00 )
                replace: MessageBox.Show(ABSTRACT_PARAM00)
                """;
        List<MultiReplaceRule> rules = loader.loadFromString(content, "test.mrule");
        assertThat(rules).hasSize(1);
        MRuleFindSpec spec = rules.get(0).getFindSpecs().get(0);
        assertThat(spec.pattern()).hasSize(4); // AfxMessageBox ( ABSTRACT_PARAM00 )
        assertThat(spec.pattern().get(2).isAbstractParam()).isTrue();
        assertThat(spec.pattern().get(2).getParamIndex()).isEqualTo(0);
    }
}
