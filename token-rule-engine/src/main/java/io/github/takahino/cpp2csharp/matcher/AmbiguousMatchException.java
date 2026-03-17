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

package io.github.takahino.cpp2csharp.matcher;

import java.util.List;

/**
 * パターンマッチングで2件以上のマッチが発生した場合にスローされる例外。
 *
 * <p>
 * 変換定義のルール設計の問題を示す。マッチが曖昧な場合は変換を行わず、 このエラー情報を記録して後の調査に役立てる。
 * </p>
 */
public class AmbiguousMatchException extends RuntimeException {

	/** 曖昧マッチとなったルール結果リスト */
	private final List<MatchResult> ambiguousMatches;

	/**
	 * コンストラクタ。
	 *
	 * @param ambiguousMatches
	 *            曖昧マッチとなった複数のマッチ結果
	 * @param context
	 *            マッチを試みたコンテキスト情報 (デバッグ用)
	 */
	public AmbiguousMatchException(List<MatchResult> ambiguousMatches, String context) {
		super(String.format("曖昧なマッチが %d 件見つかりました (変換を行いません): %s", ambiguousMatches.size(), context));
		this.ambiguousMatches = List.copyOf(ambiguousMatches);
	}

	/**
	 * 曖昧マッチとなったマッチ結果リストを返す。
	 *
	 * @return マッチ結果リスト
	 */
	public List<MatchResult> getAmbiguousMatches() {
		return ambiguousMatches;
	}
}
