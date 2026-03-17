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

import java.util.List;

/**
 * 1 件の変換適用を記録するレコード。
 *
 * <p>
 * レポートで「どのルールが」「どのノードに」「どのように変換されたか」を 時系列で表示するために使用する。
 * </p>
 *
 * @param sequence
 *            適用順序（1から始まる通し番号）
 * @param phaseIndex
 *            フェーズ番号（0始まり）
 * @param ruleSource
 *            ルール定義元ファイル名
 * @param ruleFrom
 *            from パターン（文字列表現）
 * @param ruleTo
 *            to テンプレート（文字列表現）
 * @param matchedNode
 *            マッチしたノードの文字列表現（変換前）
 * @param transformedTo
 *            変換後の文字列
 * @param sourceLineNumber
 *            元ソースの行番号（1始まり、不明時は 0）
 * @param lineBefore
 *            該当行の置き換え前の文字列（空の場合は未設定）
 * @param lineAfter
 *            該当行の置き換え後の文字列（空の場合は未設定）
 * @param selectedStrategy
 *            選択に使用した戦略名
 * @param fallbackFrom
 *            フォールバック元の戦略名（フォールバック時のみ、それ以外は null）
 * @param selectionReason
 *            選択理由の要約
 * @param selectionDetails
 *            選択の詳細（スコア導出・同点時情報・対決相手など、戦略が出力する場合）
 * @param startIndex
 *            マッチ開始位置（フラットトークンリスト内のインデックス、不明時は -1）
 * @param endIndex
 *            マッチ終了位置（exclusive、不明時は -1）
 * @param mergedIds
 *            置換でマージされた id のリスト（Excel 可視化のセルコメント用）
 */
public record AppliedTransform(int sequence, int phaseIndex, String ruleSource, String ruleFrom, String ruleTo,
		String matchedNode, String transformedTo, int sourceLineNumber, String lineBefore, String lineAfter,
		String selectedStrategy, String fallbackFrom, String selectionReason, String selectionDetails, int startIndex,
		int endIndex, List<Integer> mergedIds) {

	/**
	 * 後方互換のためのコンストラクタ（sourceLineNumber 以降なし）。
	 *
	 * @deprecated 新規コードでは lineBefore, lineAfter, selectedStrategy 等を指定すること
	 */
	@Deprecated
	public AppliedTransform(int sequence, int phaseIndex, String ruleSource, String ruleFrom, String matchedNode,
			String transformedTo, int sourceLineNumber) {
		this(sequence, phaseIndex, ruleSource, ruleFrom, "", matchedNode, transformedTo, sourceLineNumber, "", "", "",
				null, "", null, -1, -1, List.of());
	}
}
