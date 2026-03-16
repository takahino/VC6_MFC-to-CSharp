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
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CombyMatcher ホールマッチングテスト")
class CombyMatcherTest {

    private CombyMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new CombyMatcher();
    }

    @Test
    @DisplayName("単純リテラルマッチ")
    void simpleLiteralMatch() {
        CombyRule rule = new CombyRule("test", "Left(", "Substring(0,", List.of());
        List<CombyMatcher.MatchResult> results = matcher.findAll("str.Left(5)", rule);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).captures()).isEmpty();
    }

    @Test
    @DisplayName("単一ホールのマッチ")
    void singleHoleMatch() {
        CombyRule rule = new CombyRule("test", "Left(:[n])", "Substring(0,:[n])", List.of());
        List<CombyMatcher.MatchResult> results = matcher.findAll("str.Left(5)", rule);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).captures()).containsEntry("n", "5");
    }

    @Test
    @DisplayName("レシーバーとパラメータの2ホールマッチ")
    void receiverAndParamHoles() {
        CombyRule rule = new CombyRule("test", ":[recv].Left(:[n])", ":[recv].Substring(0,:[n])", List.of());
        List<CombyMatcher.MatchResult> results = matcher.findAll("str.Left(5)", rule);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).captures())
                .containsEntry("recv", "str")
                .containsEntry("n", "5");
    }

    @Test
    @DisplayName("スペースを含むソースにマッチ（トークン境界の空白）")
    void matchWithSpacesInSource() {
        CombyRule rule = new CombyRule("test", ":[recv].Left(:[n])", ":[recv].Substring(0,:[n])", List.of());
        List<CombyMatcher.MatchResult> results = matcher.findAll("str . Left ( 5 )", rule);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).captures())
                .containsEntry("recv", "str")
                .containsEntry("n", "5");
    }

    @Test
    @DisplayName("ネストした括弧をキャプチャ")
    void nestedBracketsCapture() {
        CombyRule rule = new CombyRule("test", ":[recv].Left(:[n])", ":[recv].Substring(0,:[n])", List.of());
        List<CombyMatcher.MatchResult> results = matcher.findAll("str.Left(f(a,b))", rule);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).captures()).containsEntry("n", "f(a,b)");
    }

    @Test
    @DisplayName("テンプレート型引数のマッチ（<> は追跡しない）")
    void templateTypeArgument() {
        CombyRule rule = new CombyRule("test", "List<:[type]>", "IList<:[type]>", List.of());
        List<CombyMatcher.MatchResult> results = matcher.findAll("List<string> x;", rule);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).captures()).containsEntry("type", "string");
    }

    @Test
    @DisplayName("同一ホール名の再利用（一致するもののみマッチ）")
    void sameHoleNameConsistency() {
        CombyRule rule = new CombyRule("test", ":[x] == :[x]", "true", List.of());
        // "a == a" should match, "a == b" should not
        List<CombyMatcher.MatchResult> r1 = matcher.findAll("a == a", rule);
        assertThat(r1).hasSize(1);

        List<CombyMatcher.MatchResult> r2 = matcher.findAll("a == b", rule);
        assertThat(r2).isEmpty();
    }

    @Test
    @DisplayName("expand: toテンプレートのホール展開")
    void expandTemplate() {
        Map<String, String> captures = Map.of("recv", "str", "n", "5");
        String result = matcher.expand(":[recv].Substring(0, :[n])", captures);
        assertThat(result).isEqualTo("str.Substring(0, 5)");
    }

    @Test
    @DisplayName("マッチなし")
    void noMatch() {
        CombyRule rule = new CombyRule("test", ":[recv].Left(:[n])", "X", List.of());
        List<CombyMatcher.MatchResult> results = matcher.findAll("Math.Sin(x)", rule);
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("matchLiteral: 空白なしの完全一致")
    void matchLiteralExact() {
        assertThat(matcher.matchLiteral("abc", 0, "abc")).isEqualTo(3);
    }

    @Test
    @DisplayName("matchLiteral: ソース側の空白をスキップ")
    void matchLiteralSkipsSourceSpaces() {
        // ".Left(" should match ". Left (" in source
        assertThat(matcher.matchLiteral(". Left (", 0, ".Left(")).isEqualTo(8);
    }

    // --- :[~regex] ホールのテスト ---

    @Test
    @DisplayName(":[~regex]: 数字列のみマッチ")
    void regexHoleMatchesDigits() {
        CombyRule rule = new CombyRule("test", "foo(:[~\\d+])", "bar(:[~\\d+])", List.of());
        List<CombyMatcher.MatchResult> hit = matcher.findAll("foo(123)", rule);
        assertThat(hit).hasSize(1);
        // :[~regex] はキャプチャマップに登録しない
        assertThat(hit.get(0).captures()).isEmpty();
    }

    @Test
    @DisplayName(":[~regex]: 数字列パターンが英字列にマッチしない")
    void regexHoleRejectsNonDigits() {
        CombyRule rule = new CombyRule("test", "foo(:[~\\d+])", "bar()", List.of());
        List<CombyMatcher.MatchResult> hit = matcher.findAll("foo(abc)", rule);
        assertThat(hit).isEmpty();
    }

    @Test
    @DisplayName(":[~regex]: 識別子パターン（\\w+）")
    void regexHoleMatchesIdentifier() {
        CombyRule rule = new CombyRule("test", "sizeof(:[~\\w+])", "sizeof_t()", List.of());
        List<CombyMatcher.MatchResult> hit = matcher.findAll("sizeof(MyType)", rule);
        assertThat(hit).hasSize(1);
    }

    // --- :[_] 匿名ホールのテスト ---

    @Test
    @DisplayName(":[_]: マッチするがキャプチャマップに登録しない")
    void anonHoleDoesNotCapture() {
        CombyRule rule = new CombyRule("test", "f(:[_])", "g()", List.of());
        List<CombyMatcher.MatchResult> hit = matcher.findAll("f(anything)", rule);
        assertThat(hit).hasSize(1);
        assertThat(hit.get(0).captures()).isEmpty();
    }

    @Test
    @DisplayName(":[_]: 同一パターン内に複数使用可能（各々独立）")
    void multipleAnonHolesAreIndependent() {
        CombyRule rule = new CombyRule("test", "f(:[_], :[_])", "g()", List.of());
        List<CombyMatcher.MatchResult> hit = matcher.findAll("f(a, b)", rule);
        assertThat(hit).hasSize(1);
        assertThat(hit.get(0).captures()).isEmpty();
    }

    @Test
    @DisplayName(":[_]: expand では元のホールテキストを保持（to: に出現しない想定）")
    void anonHoleInExpandPreservesToken() {
        // to: に :[_] が含まれていても、captures にないため元テキストを保持
        String result = matcher.expand("g(:[_])", Map.of());
        assertThat(result).isEqualTo("g(:[_])");
    }
}
