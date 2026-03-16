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

import io.github.takahino.cpp2csharp.rule.ConversionRule;

/**
 * 複数マッチ候補から適用する 1 件を選択する戦略のインターフェース。
 *
 * <p>
 * Transformer の核心アルゴリズム（matchAll + selectBest）のうち、 選択ロジックを差し替え可能にするための Strategy
 * パターン。
 * </p>
 *
 * <p>
 * デフォルト実装は {@link RightmostFirstSelectionStrategy}。
 * </p>
 */
public interface MatchSelectionStrategy {

	/**
	 * マッチ候補から適用する 1 件を選択する。
	 *
	 * @param input
	 *            選択に必要な入力（allMatches, tokens, tokenNodes, graph, root, context）
	 * @return 選択結果（マッチ + 戦略名・フォールバック有無・理由）。候補なし・曖昧時は match が null
	 */
	SelectionDecision selectBest(MatchSelectionInput input);

	/**
	 * ルールの具体トークン数を返す（選択時の特異性判定に使用）。 デフォルトは ABSTRACT_PARAM でないトークン数。
	 *
	 * @param rule
	 *            変換ルール
	 * @return 具体トークン数
	 */
	default int countConcreteTokens(ConversionRule rule) {
		return (int) rule.getFromTokens().stream().filter(t -> !t.isAbstractParam()).count();
	}
}
