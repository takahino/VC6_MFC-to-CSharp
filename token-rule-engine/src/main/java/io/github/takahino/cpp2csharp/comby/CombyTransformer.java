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

import io.github.takahino.comby.Comby;
import io.github.takahino.comby.core.model.CapturedValue;
import io.github.takahino.comby.core.model.Match;
import io.github.takahino.comby.core.model.MatchEnvironment;
import io.github.takahino.cpp2csharp.converter.PhaseTransformLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * COMBYルール群をソーステキストに反復適用するトランスフォーマー。
 *
 * <p>
 * 各フェーズ内で全ルールの全マッチを収集し、最も右端（start 位置が最大）の
 * マッチを1件適用する。変換が収束（マッチなし）するまで最大100回繰り返す。
 * </p>
 *
 * <p>
 * structural-rewriter ライブラリの {@code Comby.matches()} API を使用した実装。
 * </p>
 */
public class CombyTransformer implements CombyEngine {

	private static final Logger LOGGER = LoggerFactory.getLogger(CombyTransformer.class);
	private static final int MAX_ITERATIONS = 100;
	private static final String LANGUAGE = "generic";
	private static final Pattern TO_HOLE_PATTERN = Pattern.compile(":\\[([a-zA-Z_][a-zA-Z0-9_]*)\\]");

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
			RuleMatch best = findRightmostMatch(current, rules);
			if (best == null)
				break;
			Match m = best.match();
			String matchedText = m.matchedText();
			String expanded = expandTemplate(best.rule().getToTemplate(), m.environment());
			current = current.substring(0, m.range().start().offset()) + expanded
					+ current.substring(m.range().end().offset());
			LOGGER.debug("COMBY 適用: [{}..{}] → '{}'", m.range().start().offset(), m.range().end().offset(), expanded);
			logs.add(new PhaseTransformLog("COMBY", phaseIndex, best.rule().getSourceFile(),
					best.rule().getFromPattern(), best.rule().getToTemplate(), matchedText, expanded));
		}
		return current;
	}

	/**
	 * 全ルール・全マッチの中から最右端マッチとその適用ルールを返す。
	 *
	 * <p>
	 * リテラル開始パターン（ホール以外で始まるパターン）の場合、識別子の途中から マッチする候補をスキップする。これにより {@code List<:[t]>}
	 * → {@code IList<:[t]>} 変換後に {@code IList} 内の {@code List} が再マッチして収束しない問題を防ぐ。
	 * </p>
	 *
	 * @return 最右端 {@link RuleMatch}、マッチなしの場合は {@code null}
	 */
	private RuleMatch findRightmostMatch(String text, List<CombyRule> rules) {
		RuleMatch best = null;
		for (CombyRule rule : rules) {
			boolean literalStart = !rule.getFromPattern().startsWith(":[");
			for (Match m : Comby.matches(text, rule.getFromPattern(), LANGUAGE)) {
				int start = m.range().start().offset();
				// リテラル開始パターンで識別子途中マッチならスキップ
				if (literalStart && start > 0 && isIdentChar(text.charAt(start - 1))
						&& isIdentChar(text.charAt(start))) {
					continue;
				}
				if (best == null || start > best.match().range().start().offset()
						|| (start == best.match().range().start().offset()
								&& m.range().end().offset() < best.match().range().end().offset())) {
					best = new RuleMatch(m, rule);
				}
			}
		}
		return best;
	}

	private static boolean isIdentChar(char c) {
		return Character.isLetterOrDigit(c) || c == '_';
	}

	private String expandTemplate(String toTemplate, MatchEnvironment env) {
		Matcher hm = TO_HOLE_PATTERN.matcher(toTemplate);
		StringBuffer sb = new StringBuffer();
		while (hm.find()) {
			String name = hm.group(1);
			String value = env.get(name).map(CapturedValue::value).orElse(hm.group(0));
			hm.appendReplacement(sb, Matcher.quoteReplacement(value));
		}
		hm.appendTail(sb);
		return sb.toString();
	}

	private record RuleMatch(Match match, CombyRule rule) {
	}
}
