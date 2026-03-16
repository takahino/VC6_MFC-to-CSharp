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

import java.util.*;
import java.util.regex.*;

/**
 * COMBYスタイルのホールマッチングエンジン。
 *
 * <p>
 * サポートするホール構文:
 * </p>
 * <ul>
 * <li>{@code :[name]} — 任意文字列をキャプチャ（括弧バランス考慮・貪欲最長）</li>
 * <li>{@code :[~regex]} — 正規表現にマッチする文字列のみキャプチャ</li>
 * <li>{@code :[_]} — 匿名ホール（マッチするがキャプチャマップに登録しない）</li>
 * </ul>
 *
 * <p>
 * {@code <>} は C++ ではテンプレートと比較演算子が字句レベルで区別できないため追跡しない。
 * </p>
 *
 * <p>
 * リテラルセグメントのマッチングは空白を柔軟に扱う: パターン内の空白は0個以上の空白にマッチし、パターンに空白がなくても
 * ソース側の空白はスキップ可能である（トークン境界に依存しない）。 ただしリテラルが識別子文字で始まる場合、前の文字が識別子継続文字の位置にはマッチしない
 * （単語境界保護）。
 * </p>
 */
public class CombyMatcher {

	/**
	 * ホールパターン: 3種類のホールを1つの正規表現で判別する。
	 * <ul>
	 * <li>group(1): regex 文字列（{@code :[~regex]} — {@code ~} プレフィックスあり）</li>
	 * <li>group(2): {@code _}（匿名ホール {@code :[_]}）</li>
	 * <li>group(3): 通常の名前（{@code :[name]}）</li>
	 * </ul>
	 */
	private static final Pattern HOLE_PATTERN = Pattern.compile(":\\[(?:~([^\\]]+)|(_)|([a-zA-Z_][a-zA-Z0-9_]*))\\]");

	/** マッチ結果: ソーステキスト内の [start, end) とキャプチャマップ */
	public record MatchResult(int start, int end, Map<String, String> captures) {
	}

	/**
	 * 内部マッチ状態: end 位置と確定したキャプチャマップを対で返す。 end < 0 はマッチ失敗を示す。
	 */
	private record MatchState(int end, Map<String, String> captures) {
		static final MatchState FAIL = new MatchState(-1, Map.of());
		boolean succeeded() {
			return end >= 0;
		}
	}

	private sealed interface Segment permits LiteralSegment, HoleSegment, RegexHoleSegment, AnonHoleSegment {
	}
	private record LiteralSegment(String text) implements Segment {
	}
	private record HoleSegment(String name) implements Segment {
	}
	private record RegexHoleSegment(Pattern regex) implements Segment {
	}
	private record AnonHoleSegment() implements Segment {
	}

	/** from パターンをセグメント列に分解する */
	List<Segment> parsePattern(String fromPattern) {
		List<Segment> segments = new ArrayList<>();
		Matcher m = HOLE_PATTERN.matcher(fromPattern);
		int last = 0;
		while (m.find()) {
			if (m.start() > last) {
				segments.add(new LiteralSegment(fromPattern.substring(last, m.start())));
			}
			if (m.group(1) != null) {
				// :[~regex]
				segments.add(new RegexHoleSegment(Pattern.compile(m.group(1))));
			} else if (m.group(2) != null) {
				// :[_]
				segments.add(new AnonHoleSegment());
			} else {
				// :[name]
				segments.add(new HoleSegment(m.group(3)));
			}
			last = m.end();
		}
		if (last < fromPattern.length()) {
			segments.add(new LiteralSegment(fromPattern.substring(last)));
		}
		return segments;
	}

	/**
	 * ソーステキスト内の全マッチを返す。 先頭がホール（名前付き・regex・匿名を含む）の場合は end 位置ごとに 最長キャプチャ（最小
	 * start）のマッチのみを返す。 先頭がリテラルの場合は各 startPos で試みる。
	 *
	 * @param commentMask
	 *            コメント文字位置を示す BitSet。セットされた位置は検索開始点から除外される。
	 */
	public List<MatchResult> findAll(String source, CombyRule rule, java.util.BitSet commentMask) {
		List<Segment> segments = parsePattern(rule.getFromPattern());
		if (segments.isEmpty())
			return List.of();

		List<MatchResult> raw = new ArrayList<>();
		for (int startPos = 0; startPos < source.length(); startPos++) {
			if (commentMask.get(startPos))
				continue;
			tryMatchAt(source, startPos, segments).ifPresent(raw::add);
		}

		// 先頭がホール系（名前付き・regex・匿名）の場合、同一 end に対して最小 start のマッチのみを残す（最長キャプチャ優先）
		if (!(segments.get(0) instanceof LiteralSegment)) {
			return deduplicateByEnd(raw);
		}
		return raw;
	}

	/**
	 * コメント除外なしの後方互換オーバーロード。
	 */
	public List<MatchResult> findAll(String source, CombyRule rule) {
		return findAll(source, rule, new java.util.BitSet(0));
	}

	/**
	 * 同一 end 位置に複数マッチがある場合、最小 start（最長キャプチャ）を残す。
	 */
	private List<MatchResult> deduplicateByEnd(List<MatchResult> matches) {
		Map<Integer, MatchResult> bestByEnd = new LinkedHashMap<>();
		for (MatchResult m : matches) {
			bestByEnd.merge(m.end(), m, (a, b) -> a.start() <= b.start() ? a : b);
		}
		return new ArrayList<>(bestByEnd.values());
	}

	private Optional<MatchResult> tryMatchAt(String source, int startPos, List<Segment> segments) {
		// 先頭がリテラルの場合は簡易プレチェック
		if (segments.get(0) instanceof LiteralSegment first) {
			char firstNonWs = firstNonWhitespace(first.text());
			if (firstNonWs != 0) {
				char srcChar = source.charAt(startPos);
				// 先頭リテラルが識別子文字で始まる場合、前の文字が識別子継続文字ならスキップ
				if (isIdentChar(firstNonWs) && startPos > 0 && isIdentChar(source.charAt(startPos - 1))) {
					return Optional.empty();
				}
				if (!Character.isWhitespace(srcChar) && srcChar != firstNonWs) {
					return Optional.empty();
				}
			}
		}
		MatchState state = matchSegments(source, startPos, segments, 0, new LinkedHashMap<>());
		if (!state.succeeded())
			return Optional.empty();
		return Optional.of(new MatchResult(startPos, state.end(), state.captures()));
	}

	private char firstNonWhitespace(String s) {
		for (int i = 0; i < s.length(); i++) {
			if (!Character.isWhitespace(s.charAt(i)))
				return s.charAt(i);
		}
		return 0;
	}

	private boolean isIdentChar(char c) {
		return Character.isLetterOrDigit(c) || c == '_';
	}

	/**
	 * セグメント列を再帰的にマッチし、成功時は確定した end 位置とキャプチャマップを返す。 captures は各分岐で独立した LinkedHashMap
	 * として伝播される（副作用なし）。
	 */
	private MatchState matchSegments(String source, int pos, List<Segment> segments, int segIdx,
			Map<String, String> captures) {
		if (segIdx == segments.size())
			return new MatchState(pos, captures);
		Segment seg = segments.get(segIdx);

		if (seg instanceof LiteralSegment lit) {
			int newPos = matchLiteral(source, pos, lit.text());
			if (newPos < 0)
				return MatchState.FAIL;
			return matchSegments(source, newPos, segments, segIdx + 1, captures);

		} else if (seg instanceof HoleSegment hole) {
			String nextLiteral = findNextLiteralText(segments, segIdx + 1);
			String existing = captures.get(hole.name());
			return matchHole(source, pos, hole.name(), nextLiteral, existing, segments, segIdx + 1, captures);

		} else if (seg instanceof RegexHoleSegment regexHole) {
			String nextLiteral = findNextLiteralText(segments, segIdx + 1);
			return matchRegexHole(source, pos, regexHole.regex(), nextLiteral, segments, segIdx + 1, captures);

		} else if (seg instanceof AnonHoleSegment) {
			String nextLiteral = findNextLiteralText(segments, segIdx + 1);
			return matchAnonHole(source, pos, nextLiteral, segments, segIdx + 1, captures);
		}
		return MatchState.FAIL;
	}

	private String findNextLiteralText(List<Segment> segments, int fromIdx) {
		for (int i = fromIdx; i < segments.size(); i++) {
			if (segments.get(i) instanceof LiteralSegment lit)
				return lit.text();
		}
		return null;
	}

	private MatchState matchHole(String source, int pos, String holeName, String nextLiteral, String existingCapture,
			List<Segment> segments, int nextSegIdx, Map<String, String> captures) {
		if (nextLiteral == null) {
			// 後続リテラルなし: バランス括弧コンテキストの末尾まで取り込む
			int end = captureToEnd(source, pos);
			String captured = trimCapture(source.substring(pos, end));
			if (existingCapture != null && !existingCapture.equals(captured))
				return MatchState.FAIL;
			Map<String, String> newCaptures = new LinkedHashMap<>(captures);
			newCaptures.put(holeName, captured);
			return matchSegments(source, end, segments, nextSegIdx, newCaptures);
		}

		// depth 0 で nextLiteral が一致する全候補を収集
		List<Integer> candidates = findCandidateEnds(source, pos, nextLiteral);
		// Greedy: 最右（最長キャプチャ）から試みる
		for (int i = candidates.size() - 1; i >= 0; i--) {
			int candidateEnd = candidates.get(i);
			String captured = trimCapture(source.substring(pos, candidateEnd));
			if (existingCapture != null && !existingCapture.equals(captured))
				continue;
			int afterLiteral = matchLiteral(source, candidateEnd, nextLiteral);
			if (afterLiteral < 0)
				continue;
			Map<String, String> newCaptures = new LinkedHashMap<>(captures);
			newCaptures.put(holeName, captured);
			MatchState result = matchSegments(source, afterLiteral, segments, nextSegIdx + 1, newCaptures);
			if (result.succeeded())
				return result;
		}
		return MatchState.FAIL;
	}

	/**
	 * 正規表現ホール {@code :[~regex]} のマッチ処理。 候補テキストが正規表現にマッチする場合のみ採用する。キャプチャマップには登録しない。
	 */
	private MatchState matchRegexHole(String source, int pos, Pattern regex, String nextLiteral, List<Segment> segments,
			int nextSegIdx, Map<String, String> captures) {
		if (nextLiteral == null) {
			int end = captureToEnd(source, pos);
			String captured = trimCapture(source.substring(pos, end));
			if (!regex.matcher(captured).matches())
				return MatchState.FAIL;
			return matchSegments(source, end, segments, nextSegIdx, captures);
		}

		List<Integer> candidates = findCandidateEnds(source, pos, nextLiteral);
		for (int i = candidates.size() - 1; i >= 0; i--) {
			int candidateEnd = candidates.get(i);
			String captured = trimCapture(source.substring(pos, candidateEnd));
			if (!regex.matcher(captured).matches())
				continue;
			int afterLiteral = matchLiteral(source, candidateEnd, nextLiteral);
			if (afterLiteral < 0)
				continue;
			MatchState result = matchSegments(source, afterLiteral, segments, nextSegIdx + 1, captures);
			if (result.succeeded())
				return result;
		}
		return MatchState.FAIL;
	}

	/**
	 * 匿名ホール {@code :[_]} のマッチ処理。 バランス括弧を考慮しながらマッチするが、キャプチャマップには登録しない。 同一パターン内に複数の
	 * {@code :[_]} を記述した場合、各々が独立してマッチする。
	 */
	private MatchState matchAnonHole(String source, int pos, String nextLiteral, List<Segment> segments, int nextSegIdx,
			Map<String, String> captures) {
		if (nextLiteral == null) {
			int end = captureToEnd(source, pos);
			return matchSegments(source, end, segments, nextSegIdx, captures);
		}

		List<Integer> candidates = findCandidateEnds(source, pos, nextLiteral);
		for (int i = candidates.size() - 1; i >= 0; i--) {
			int candidateEnd = candidates.get(i);
			int afterLiteral = matchLiteral(source, candidateEnd, nextLiteral);
			if (afterLiteral < 0)
				continue;
			MatchState result = matchSegments(source, afterLiteral, segments, nextSegIdx + 1, captures);
			if (result.succeeded())
				return result;
		}
		return MatchState.FAIL;
	}

	/**
	 * depth 0 の位置で nextLiteral が一致する全候補インデックスを返す。 depth 0
	 * でアンダーフロー（外側の閉じ括弧）に達した時点で打ち切る。
	 */
	private List<Integer> findCandidateEnds(String source, int pos, String nextLiteral) {
		List<Integer> candidates = new ArrayList<>();
		int depth = 0;
		int i = pos;
		while (i < source.length()) {
			char c = source.charAt(i);
			if (c == '(' || c == '[' || c == '{') {
				depth++;
				i++;
			} else if (c == ')' || c == ']' || c == '}') {
				if (depth == 0) {
					if (matchLiteral(source, i, nextLiteral) >= 0) {
						candidates.add(i);
					}
					break;
				}
				depth--;
				i++;
			} else {
				if (depth == 0 && matchLiteral(source, i, nextLiteral) >= 0) {
					candidates.add(i);
				}
				i++;
			}
		}
		return candidates;
	}

	/**
	 * キャプチャ文字列のトリム方針。 改行を含む場合（複数行ボディ等）はインデントを保持するためトリムしない。
	 * 改行を含まない場合（識別子・式等）は前後の空白をトリムする。
	 */
	private String trimCapture(String raw) {
		return raw.contains("\n") ? raw : raw.trim();
	}

	private int captureToEnd(String source, int pos) {
		int depth = 0;
		int i = pos;
		while (i < source.length()) {
			char c = source.charAt(i);
			if (c == '(' || c == '[' || c == '{')
				depth++;
			else if (c == ')' || c == ']' || c == '}') {
				if (depth == 0)
					break;
				depth--;
			}
			i++;
		}
		return i;
	}

	/**
	 * リテラルを source の pos から照合する。
	 *
	 * <p>
	 * 空白の扱い: パターン内の空白は0個以上の空白にマッチする。 パターンに空白がない場合でも、ソース側の空白はスキップ可能。
	 * ただしリテラルが識別子文字で始まり、かつ照合開始位置（空白スキップ前）の直前の文字が 識別子継続文字の場合はマッチ失敗とする（単語境界保護）。
	 * </p>
	 *
	 * @return マッチ後のソース位置、マッチ失敗なら -1
	 */
	int matchLiteral(String source, int pos, String literal) {
		int srcPos = pos;
		int litPos = 0;

		// 単語境界チェック: リテラルの先頭非空白が識別子文字の場合
		char litFirst = firstNonWhitespace(literal);
		if (isIdentChar(litFirst)) {
			// スキップ前の元位置（pos）の直前が識別子文字ならマッチ拒否
			if (pos > 0 && isIdentChar(source.charAt(pos - 1))) {
				return -1;
			}
		}

		while (litPos < literal.length()) {
			char lc = literal.charAt(litPos);

			if (Character.isWhitespace(lc)) {
				// リテラルの空白ランをスキップ
				while (litPos < literal.length() && Character.isWhitespace(literal.charAt(litPos))) {
					litPos++;
				}
				// ソース側の空白もオプションでスキップ
				while (srcPos < source.length() && Character.isWhitespace(source.charAt(srcPos))) {
					srcPos++;
				}
			} else {
				// 非空白文字の前にソース側の空白をスキップ
				while (srcPos < source.length() && Character.isWhitespace(source.charAt(srcPos))) {
					srcPos++;
				}
				if (srcPos >= source.length())
					return -1;
				if (source.charAt(srcPos) != lc)
					return -1;
				litPos++;
				srcPos++;
			}
		}
		return srcPos;
	}

	/**
	 * to テンプレートの {@code :[name]} をキャプチャマップで展開する。 {@code :[~regex]} や {@code :[_]} は
	 * to: テンプレートに出現しない想定だが、 出現した場合は元のホールテキストをそのまま保持する。
	 */
	public String expand(String toTemplate, Map<String, String> captures) {
		StringBuffer sb = new StringBuffer();
		Matcher m = HOLE_PATTERN.matcher(toTemplate);
		while (m.find()) {
			String name = m.group(3); // group(3) が通常の名前 (:[name])
			String value;
			if (name != null) {
				value = captures.getOrDefault(name, m.group(0));
			} else {
				// :[~regex] や :[_] は to: テンプレートに出現しない想定: 元テキストを保持
				value = m.group(0);
			}
			m.appendReplacement(sb, Matcher.quoteReplacement(value));
		}
		m.appendTail(sb);
		return sb.toString();
	}
}
