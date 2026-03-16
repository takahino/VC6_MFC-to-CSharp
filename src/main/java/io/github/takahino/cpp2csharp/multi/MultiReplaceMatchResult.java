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

package io.github.takahino.cpp2csharp.multi;

import io.github.takahino.cpp2csharp.matcher.MatchResult;
import io.github.takahino.cpp2csharp.mrule.MultiReplaceRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * マルチ置換マッチの結果を保持するレコード。
 *
 * <p>
 * 1つの {@link MultiReplaceRule} に対する全 find spec のマッチ結果と、 共有キャプチャを保持する。
 * </p>
 *
 * @param rule
 *            マッチしたルール
 * @param stepMatches
 *            各 find spec のマッチ結果リスト（順序は spec 順）
 * @param captures
 *            全 step の共有キャプチャ（パラメータインデックス → トークンリスト）
 * @param blockStart
 *            ブロックスコープ時のブロック開始インデックス（-1 = 非ブロック）
 * @param blockEnd
 *            ブロックスコープ時のブロック終了インデックス（-1 = 非ブロック）
 */
public record MultiReplaceMatchResult(MultiReplaceRule rule, List<MatchResult> stepMatches,
		Map<Integer, List<String>> captures, int blockStart, int blockEnd) {

	/**
	 * 最小の開始インデックスを返す（挿入位置として使用）。
	 *
	 * @return 最小開始インデックス
	 */
	public int insertionIndex() {
		return stepMatches.stream().mapToInt(MatchResult::getStartIndex).min().orElse(0);
	}

	/**
	 * 全マッチ範囲を降順（安全な削除順）で返す。
	 *
	 * @return {@code [startIndex, endIndex]} の配列リスト（降順）
	 */
	public List<int[]> allMatchedRanges() {
		List<int[]> ranges = new ArrayList<>();
		for (MatchResult m : stepMatches) {
			ranges.add(new int[]{m.getStartIndex(), m.getEndIndex()});
		}
		ranges.sort((a, b) -> Integer.compare(b[0], a[0]));
		return ranges;
	}

	/**
	 * 最初の step のマッチに対する展開済み置換テキストを返す。
	 *
	 * @return 展開済み置換テキスト
	 */
	public String expandedReplacement() {
		if (stepMatches.isEmpty())
			return "";
		return stepMatches.get(0).getExpandedToTemplate();
	}
}
