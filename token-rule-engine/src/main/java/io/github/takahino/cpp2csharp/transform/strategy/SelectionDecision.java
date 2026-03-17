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

package io.github.takahino.cpp2csharp.transform.strategy;

import io.github.takahino.cpp2csharp.matcher.MatchResult;

/**
 * マッチ選択戦略の結果を保持するレコード。
 *
 * <p>
 * 適用するマッチと、どの戦略で選ばれたか・フォールバック有無を記録する。
 * </p>
 *
 * @param match
 *            適用するマッチ（null の場合は候補なし・曖昧）
 * @param selectedStrategy
 *            選択に使用した戦略名
 * @param fallbackUsed
 *            フォールバックで選ばれた場合 true
 * @param fallbackFromStrategy
 *            フォールバック元の戦略名（fallbackUsed 時のみ）
 * @param reasonSummary
 *            選択理由の要約（ログ・レポート用）
 * @param selectionDetails
 *            選択の詳細（スコア導出・同点時情報・対決相手など、戦略が出力する場合）
 */
public record SelectionDecision(MatchResult match, String selectedStrategy, boolean fallbackUsed,
		String fallbackFromStrategy, String reasonSummary, String selectionDetails) {

	/**
	 * マッチが選択された場合のファクトリ。
	 */
	public static SelectionDecision selected(MatchResult match, String strategy, String reason) {
		return new SelectionDecision(match, strategy, false, null, reason, null);
	}

	/**
	 * マッチが選択された場合のファクトリ（詳細情報付き）。
	 */
	public static SelectionDecision selected(MatchResult match, String strategy, String reason,
			String selectionDetails) {
		return new SelectionDecision(match, strategy, false, null, reason, selectionDetails);
	}

	/**
	 * フォールバックでマッチが選択された場合のファクトリ。
	 */
	public static SelectionDecision fallback(MatchResult match, String fallbackStrategy, String fromStrategy,
			String reason) {
		return new SelectionDecision(match, fallbackStrategy, true, fromStrategy, reason, null);
	}

	/**
	 * 候補なし・曖昧マッチの場合のファクトリ。
	 */
	public static SelectionDecision none(String strategy, String reason) {
		return new SelectionDecision(null, strategy, false, null, reason, null);
	}

	/**
	 * マッチが存在するか。
	 */
	public boolean hasMatch() {
		return match != null;
	}
}
