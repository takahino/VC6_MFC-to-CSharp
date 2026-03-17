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

package io.github.takahino.cpp2csharp.rule;

import java.util.List;

/**
 * 変換ルールの from パターンを解析し、引数個数などの構文情報を抽出するクラス。
 *
 * <p>
 * ブラケット深度追跡（{@link BracketDepthTracker}）を用いてトークン列から関数呼び出しの
 * 引数個数を求める。言語文法への依存なしに動作する。
 * </p>
 */
public final class RulePatternParser {

	private RulePatternParser() {
	}

	/**
	 * from パターンから期待する引数個数をトークン列解析で導出する。
	 *
	 * <p>
	 * 最初の {@code (} から対応する {@code )} までの範囲で、深さ 0 のカンマ数を数えて引数個数を返す。
	 * 引数がある場合は カンマ数 + 1、引数なしの空括弧は 0、括弧なしは -1 を返す。
	 * </p>
	 *
	 * @param fromTokens
	 *            from パターンのトークン列
	 * @return 期待する引数個数、括弧なしまたはパース失敗時は -1
	 */
	public static int parseArgumentCount(List<ConversionToken> fromTokens) {
		List<String> values = fromTokens.stream().map(ConversionToken::getValue).toList();
		if (values.stream().noneMatch("("::equals)) {
			return -1;
		}

		// Find the first '(' and count depth-0 commas until matching ')'
		int parenStart = -1;
		for (int i = 0; i < values.size(); i++) {
			if ("(".equals(values.get(i))) {
				parenStart = i;
				break;
			}
		}
		if (parenStart < 0) {
			return -1;
		}

		int depth = 0;
		int commaCount = 0;
		boolean hasContent = false;
		for (int i = parenStart; i < values.size(); i++) {
			String token = values.get(i);
			if ("(".equals(token) || "[".equals(token) || "{".equals(token)) {
				depth++;
			} else if (")".equals(token) || "]".equals(token) || "}".equals(token)) {
				depth--;
				if (depth == 0) {
					// reached closing paren
					if (!hasContent) {
						return 0;
					}
					return commaCount + 1;
				}
			} else if (",".equals(token) && depth == 1) {
				commaCount++;
				hasContent = true;
			} else if (depth == 1) {
				// any non-comma, non-bracket token inside the parens
				// ignore abstract params like ABSTRACT_PARAM00, RECEIVER
				String v = token;
				if (!v.startsWith("ABSTRACT_PARAM") && !v.startsWith("RECEIVER")) {
					hasContent = true;
				} else {
					hasContent = true; // abstract params also count
				}
			}
		}
		return -1;
	}
}
