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

import io.github.takahino.cpp2csharp.tree.AstNode;

import java.util.ArrayList;
import java.util.List;

/**
 * トークン列を関数定義単位に分割するクラス。
 *
 * <h2>分割方式</h2>
 * <p>
 * ParseTree から取得した関数定義の streamIndex 範囲を用いて、 各関数定義（シグネチャ＋ボディ）を "body" 単位として切り出す。
 * 関数定義外のトークンは "gap" 単位にまとめる。
 * </p>
 *
 * <h2>フォールバック（ParseTree 範囲なし）</h2>
 * <p>
 * {@code functionRanges} が空の場合（パースエラー等で ParseTree 取得不可）は、 トークン列全体を1つの "body"
 * 単位として返す。
 * </p>
 *
 * <h2>PRE フェーズ合成トークンの扱い</h2>
 * <p>
 * PRE フェーズで生成された合成トークン（streamIndex=-1）が gap モード中に現れた場合、 次の body 開始まで保留して body
 * に取り込む。 これにより、{@code CString→string} のように PRE がリターン型を合成トークンに置換した場合でも
 * シグネチャトークンが正しく body unit に含まれる。
 * </p>
 */
public final class FunctionUnitSplitter {

	private FunctionUnitSplitter() {
	}

	/**
	 * ParseTree から得た関数定義範囲を用いてトークン列を {@link TokenUnit} に分割する。
	 *
	 * <p>
	 * 各 {@code int[2]} = [startStreamIndex, stopStreamIndex] が 1 つの関数定義に対応する。
	 * {@code functionRanges} が空の場合はトークン列全体を1つの "body" 単位として返す。
	 * </p>
	 *
	 * @param tokens
	 *            ファイル全体のトークンノード列（PRE フェーズ後のもの）
	 * @param functionRanges
	 *            各関数定義の [startStreamIndex, stopStreamIndex] のリスト
	 * @return 処理単位のリスト（元の順序を保つ）
	 */
	public static List<TokenUnit> split(List<AstNode> tokens, List<int[]> functionRanges) {
		if (tokens.isEmpty()) {
			return List.of();
		}
		if (functionRanges.isEmpty()) {
			// ParseTree が取得できなかった場合: 全体を1ユニットとして処理
			return List.of(new TokenUnit(UnitLabel.BODY, List.copyOf(tokens)));
		}

		// functionRanges は ParseTree DFS 走査（ソース順）で構築されているため、既にソート済み
		List<TokenUnit> units = new ArrayList<>();
		List<AstNode> gap = new ArrayList<>();
		// PRE フェーズの合成トークン（streamIdx=-1）用バッファ:
		// gap モード中に合成トークンが現れた場合、次の body 開始まで保留し body に取り込む。
		List<AstNode> syntheticBuffer = new ArrayList<>();
		List<AstNode> body = null;
		int currentEnd = -1;
		int rangeIdx = 0;

		for (AstNode token : tokens) {
			int streamIdx = token.getStreamIndex();

			if (body != null) {
				// 現在 body 単位に属している
				body.add(token);
				if (streamIdx >= 0 && streamIdx >= currentEnd) {
					// body 単位終了
					units.add(new TokenUnit(UnitLabel.BODY, List.copyOf(body)));
					body = null;
					currentEnd = -1;
					rangeIdx++;
				}
			} else {
				// gap 中: 次の関数定義開始を探す
				if (streamIdx < 0) {
					// 合成トークン: 次の body が始まるまでバッファ
					syntheticBuffer.add(token);
				} else if (rangeIdx < functionRanges.size() && streamIdx >= functionRanges.get(rangeIdx)[0]) {
					// body 開始: gap を flush し、合成トークンバッファを body の先頭に取り込む
					if (!gap.isEmpty()) {
						gap.addAll(syntheticBuffer);
						syntheticBuffer.clear();
						units.add(new TokenUnit(UnitLabel.GAP, List.copyOf(gap)));
						gap.clear();
					}
					body = new ArrayList<>(syntheticBuffer);
					syntheticBuffer.clear();
					body.add(token);
					currentEnd = functionRanges.get(rangeIdx)[1];
					// streamIdx が既に currentEnd 以上なら 1 トークンで body 完了
					if (streamIdx >= currentEnd) {
						units.add(new TokenUnit(UnitLabel.BODY, List.copyOf(body)));
						body = null;
						currentEnd = -1;
						rangeIdx++;
					}
				} else {
					// 通常 gap トークン: 合成バッファを gap に移してから追加
					gap.addAll(syntheticBuffer);
					syntheticBuffer.clear();
					gap.add(token);
				}
			}
		}

		// 終端処理
		if (body != null) {
			// 閉じトークンが token list に含まれなかった場合（パースエラー等）
			gap.addAll(body);
		}
		// 未処理の合成バッファは gap へ
		gap.addAll(syntheticBuffer);
		if (!gap.isEmpty()) {
			units.add(new TokenUnit(UnitLabel.GAP, List.copyOf(gap)));
		}
		return units;
	}
}
