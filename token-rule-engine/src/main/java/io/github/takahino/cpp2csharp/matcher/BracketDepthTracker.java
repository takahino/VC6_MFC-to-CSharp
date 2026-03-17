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

import java.util.Set;

/**
 * トークン列の括弧深さを追跡するステートフルヘルパー。
 *
 * <p>
 * 開き括弧 {@code ( [ {} で depth++、閉じ括弧 {@code ) ] }} で depth-- を行う。 depth 0
 * で閉じ括弧に遭遇した場合（アンダーフロー）は {@link #track} が {@code false} を返す。
 * </p>
 *
 * <p>
 * インスタンスは探索ループごとに新規作成して使用する。
 * </p>
 */
final class BracketDepthTracker {

	private static final Set<String> OPEN_BRACKETS = Set.of("(", "[", "{");
	private static final Set<String> CLOSE_BRACKETS = Set.of(")", "]", "}");

	private int depth = 0;

	/**
	 * トークンを処理して深さを更新する。
	 *
	 * @param token
	 *            処理対象トークン
	 * @return {@code false} iff depth 0 で閉じ括弧に遭遇した（アンダーフロー）
	 */
	boolean track(String token) {
		if (OPEN_BRACKETS.contains(token)) {
			depth++;
		} else if (CLOSE_BRACKETS.contains(token)) {
			if (depth > 0) {
				depth--;
			} else {
				return false;
			}
		}
		return true;
	}

	/** 現在の括弧深さを返す。 */
	int depth() {
		return depth;
	}

	/** depth が 0（表面）かどうかを返す。 */
	boolean atSurface() {
		return depth == 0;
	}

	/** 全括弧が均衡している（depth == 0）かどうかを返す。 */
	boolean isBalanced() {
		return depth == 0;
	}
}
