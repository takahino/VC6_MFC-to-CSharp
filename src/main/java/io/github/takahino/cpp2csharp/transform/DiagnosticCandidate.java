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

package io.github.takahino.cpp2csharp.transform;

/**
 * 診断専用再マッチで検出した候補 1 件を表すレコード。
 *
 * <p>全変換フェーズ完了後に、フィルタ無視で再度 matchAll した結果のうち、
 * 通常選択では未採用だった候補をレポート用に保持する。</p>
 *
 * <p><strong>A1 ニアミス検出のため</strong>、{@code reasonCategory} が {@code "near_miss"} の場合に
 * 「どのパターントークンで不一致だったか」を示す3フィールドを追加した。
 * これにより「あと1トークンだけ違うルール」をレポートで可視化でき、ルール修正コストを大幅に削減できる。
 * 非ニアミス時は null を格納することで後方互換を維持する。</p>
 *
 * @param lineNumber           元ソースの行番号（1始まり、不明時は 0）
 * @param ruleSource           ルール定義元ファイル名
 * @param ruleFrom             from パターン（文字列表現）
 * @param expandedTo           想定 to テンプレート展開結果（もし変換されたら何になるか）
 * @param matchedText          最終トークン列上でマッチした範囲の文字列
 * @param lineContent          該当行の内容（スペース区切りトークン列）
 * @param reasonCategory       分類（still_matchable_after_all_phases / near_miss 等）
 * @param mismatchPatternIndex A1 ニアミス時: マッチが失敗したパターンインデックス（null = 非ニアミス）
 * @param expectedToken        A1 ニアミス時: パターンが期待したトークン（null = 非ニアミス）
 * @param actualToken          A1 ニアミス時: 実際に存在したトークン（null = 不明）
 */
public record DiagnosticCandidate(
        int lineNumber,
        String ruleSource,
        String ruleFrom,
        String expandedTo,
        String matchedText,
        String lineContent,
        String reasonCategory,
        Integer mismatchPatternIndex,
        String expectedToken,
        String actualToken) {

    /**
     * 後方互換コンストラクタ。A1 ニアミスフィールドは null で初期化する。
     */
    public DiagnosticCandidate(int lineNumber, String ruleSource, String ruleFrom,
                                String expandedTo, String matchedText, String lineContent,
                                String reasonCategory) {
        this(lineNumber, ruleSource, ruleFrom, expandedTo, matchedText, lineContent,
             reasonCategory, null, null, null);
    }
}
