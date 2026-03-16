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

package io.github.takahino.cpp2csharp.comby;

import io.github.takahino.cpp2csharp.converter.PhaseTransformLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * COMBYルール群をソーステキストに反復適用するトランスフォーマー。
 *
 * <p>
 * 各フェーズ内で全ルールの全マッチを収集し、最も右端（start 位置が最大）の
 * マッチを1件適用する。変換が収束（マッチなし）するまで最大100回繰り返す。
 * </p>
 */
public class CombyTransformer implements CombyEngine {

	private static final Logger LOGGER = LoggerFactory.getLogger(CombyTransformer.class);
	private static final int MAX_ITERATIONS = 100;

	private final CombyMatcher matcher = new CombyMatcher();

	/** フェーズ適用ログ（フェーズ実行後に getLogs() で取得） */
	private final List<PhaseTransformLog> logs = new ArrayList<>();

	/**
	 * 直近フェーズの適用ログを返す。
	 *
	 * @return 適用ログリスト（読み取り専用コピー）
	 */
	public List<PhaseTransformLog> getLogs() {
		return List.copyOf(logs);
	}

	/**
	 * ログをクリアする。
	 */
	public void clearLogs() {
		logs.clear();
	}

	/**
	 * 複数フェーズを順に適用する。各フェーズは収束まで反復適用される。
	 */
	public String transformPhases(String text, List<List<CombyRule>> phases) {
		String current = text;
		int phaseIndex = 1;
		for (List<CombyRule> phase : phases) {
			current = transformPhase(current, phase, phaseIndex++);
		}
		return current;
	}

	/**
	 * 1フェーズ分のルール群を収束まで適用する（後方互換オーバーロード）。
	 */
	public String transformPhase(String text, List<CombyRule> rules) {
		return transformPhase(text, rules, 1);
	}

	/**
	 * 1フェーズ分のルール群を収束まで適用する。
	 *
	 * @param text
	 *            変換対象テキスト
	 * @param rules
	 *            適用するルールリスト
	 * @param phaseIndex
	 *            フェーズ番号（1始まり）
	 * @return 変換後テキスト
	 */
	public String transformPhase(String text, List<CombyRule> rules, int phaseIndex) {
		String current = text;
		for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
			BitSet commentMask = CombySourceLexer.buildExcludedMask(current);
			RuleMatch best = findRightmostMatchWithRule(current, rules, commentMask);
			if (best == null)
				break;
			String matchedText = current.substring(best.match().start(), best.match().end());
			String expanded = matcher.expand(best.rule().getToTemplate(), best.match().captures());
			current = current.substring(0, best.match().start()) + expanded + current.substring(best.match().end());
			LOGGER.debug("COMBY 適用: [{}..{}] → '{}'", best.match().start(), best.match().end(), expanded);
			logs.add(new PhaseTransformLog("COMBY", phaseIndex, best.rule().getSourceFile(),
					best.rule().getFromPattern(), best.rule().getToTemplate(), matchedText, expanded));
		}
		return current;
	}

	/**
	 * 全ルール・全マッチの中から最右端マッチとその適用ルールを1走査で返す。
	 *
	 * @return 最右端 {@link RuleMatch}、マッチなしの場合は {@code null}
	 */
	private RuleMatch findRightmostMatchWithRule(String text, List<CombyRule> rules, BitSet commentMask) {
		RuleMatch best = null;
		for (CombyRule rule : rules) {
			for (CombyMatcher.MatchResult m : matcher.findAll(text, rule, commentMask)) {
				if (best == null || m.start() > best.match().start()
						|| (m.start() == best.match().start() && m.end() < best.match().end())) {
					best = new RuleMatch(m, rule);
				}
			}
		}
		return best;
	}

	private record RuleMatch(CombyMatcher.MatchResult match, CombyRule rule) {
	}
}
