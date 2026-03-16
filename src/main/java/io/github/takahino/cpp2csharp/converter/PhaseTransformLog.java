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

package io.github.takahino.cpp2csharp.converter;

/**
 * PRE/POST（mrule）・COMBY フェーズの1適用を表すログレコード。
 *
 * <p>MAIN フェーズの {@code AppliedTransform} に対応する、フェーズ別軽量ログ。
 * トークンインデックスや Excel 可視化情報は持たず、変換内容の人間可読サマリーのみを保持する。</p>
 *
 * @param phase        フェーズ名（"PRE", "POST", "COMBY"）
 * @param phaseIndex   フェーズ番号（1始まり、同一フェーズ種の何番目か）
 * @param ruleSource   ルール定義ファイル名
 * @param ruleFrom     from パターン（テキスト表現）
 * @param ruleTo       to テンプレート（テキスト表現）
 * @param matchedText  マッチした入力テキスト
 * @param replacedWith 変換後テキスト
 */
public record PhaseTransformLog(
        String phase,
        int phaseIndex,
        String ruleSource,
        String ruleFrom,
        String ruleTo,
        String matchedText,
        String replacedWith) {
}
