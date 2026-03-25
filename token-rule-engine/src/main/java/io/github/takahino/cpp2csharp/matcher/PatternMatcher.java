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

import io.github.takahino.cpp2csharp.rule.ConversionRule;
import io.github.takahino.cpp2csharp.rule.ConversionToken;
import io.github.takahino.cpp2csharp.matcher.ReceiverValidator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;

/**
 * 変換ルールの from パターンをフラットなトークン列に対してマッチングするクラス。
 *
 * <h2>マッチングアルゴリズム</h2>
 * <p>
 * from パターンのトークンをソース先頭から順にマッチさせる。 {@code ABSTRACT_PARAM[nn]}
 * トークンに遭遇した場合、<strong>括弧深さを考慮した</strong> アンカー探索を行う。
 * </p>
 *
 * <h3>括弧深さ考慮マッチング</h3>
 * <p>
 * ABSTRACT_PARAM の次に来る具体トークン（アンカー）を探す際、 深さが 0 の位置でのみアンカーと判定する。 これにより
 * {@code sin(CreateMessage(a, b))} のようなネストした式でも、 正しく
 * {@code CreateMessage(a, b)} 全体をキャプチャできる。
 * </p>
 *
 * <ul>
 * <li>{@code (} {@code [} {@code {}: 深さ++</li>
 * <li>{@code )} {@code ]} {@code }}: 深さ==0 かつアンカーなら確定、深さ&gt;0 なら深さ--</li>
 * </ul>
 */
public class PatternMatcher {

	/**
	 * RECEIVER キャプチャの AST 妥当性検証器。null の場合はプリフィルタのみ使用（診断モード相当）。
	 */
	private final ReceiverValidator receiverValidator;

	/**
	 * デフォルトコンストラクタ。ReceiverValidator なし（プリフィルタのみ）。
	 */
	public PatternMatcher() {
		this(null);
	}

	/**
	 * コンストラクタ。ReceiverValidator を注入する。
	 *
	 * @param receiverValidator
	 *            RECEIVER キャプチャの妥当性検証器（null の場合はプリフィルタのみ使用）
	 */
	public PatternMatcher(ReceiverValidator receiverValidator) {
		this.receiverValidator = receiverValidator;
	}

	/**
	 * B1: 先頭トークンインデックス。
	 *
	 * <p>
	 * <strong>なぜ追加したか</strong>: ルールが将来 5000+ に増えると、 旧実装の「全ルール × 全開始位置」走査（O(R × T ×
	 * P)）で性能が劣化する。 ルールを先頭トークン（具体 or 抽象）で分類しておくことで、 各位置で試行するルール数を大幅に絞り込める（O(R×T) →
	 * 実用上 O(T)）。
	 * </p>
	 *
	 * <p>
	 * IdentityHashMap でルールリストの参照同一性をキーにキャッシュするため、 ルールセットが変わらない限り再ビルドコストはかからない。
	 * </p>
	 */
	private record RuleIndex(Map<String, List<ConversionRule>> concreteHead, List<ConversionRule> abstractHead) {
	}

	private final IdentityHashMap<List<ConversionRule>, RuleIndex> indexCache = new IdentityHashMap<>();

	/**
	 * 診断モード: true のとき RECEIVER の isValidReceiverCapture をスキップし、
	 * 括弧始まりの式などもレシーバーとして許可する（診断候補の網羅用）。
	 */
	private boolean diagnosticMode = false;

	/**
	 * 全てのルールをトークンリストに対して試行し、マッチ結果を全て返す。
	 *
	 * <p>
	 * マッチ件数ごとの解釈:
	 * </p>
	 * <ul>
	 * <li>0件: 変換対象外</li>
	 * <li>1件: 変換実施</li>
	 * <li>2件以上: 曖昧マッチとしてエラー記録し変換しない</li>
	 * </ul>
	 *
	 * @param rules
	 *            試行する変換ルールリスト
	 * @param tokens
	 *            フラットなトークン文字列リスト
	 * @return 全マッチ結果のリスト
	 */
	public List<MatchResult> matchAll(List<ConversionRule> rules, List<String> tokens) {
		List<MatchResult> results = new ArrayList<>();
		if (rules.isEmpty() || tokens.isEmpty()) {
			return results;
		}
		RuleIndex index = buildIndex(rules);
		for (int startIdx = 0; startIdx < tokens.size(); startIdx++) {
			String firstToken = tokens.get(startIdx);
			// 具体トークン始まりルール: 先頭トークンが一致するルールのみ試行
			List<ConversionRule> concrete = index.concreteHead().get(firstToken);
			if (concrete != null) {
				for (ConversionRule rule : concrete) {
					tryMatch(rule, rule.getFromTokens(), tokens, startIdx).ifPresent(results::add);
				}
			}
			// 抽象トークン始まりルール: 全位置で試行
			for (ConversionRule rule : index.abstractHead()) {
				tryMatch(rule, rule.getFromTokens(), tokens, startIdx).ifPresent(results::add);
			}
		}
		return results;
	}

	/**
	 * B1: ルールリストに対する先頭トークンインデックスを構築する（IdentityHashMap でキャッシュ）。
	 *
	 * <p>
	 * <strong>なぜ追加したか</strong>: {@link #matchAll} が各開始位置で試行するルールを絞り込むために
	 * 必要。具体トークン始まりルールは {@code concreteHead} に入れ、位置のトークンと一致する場合だけ試行する。
	 * 抽象トークン始まりルール（ABSTRACT_PARAM / RECEIVER）は絞り込み不可なので {@code abstractHead}
	 * として全位置で試行する。
	 * </p>
	 */
	private RuleIndex buildIndex(List<ConversionRule> rules) {
		return indexCache.computeIfAbsent(rules, rl -> {
			Map<String, List<ConversionRule>> concreteHead = new LinkedHashMap<>();
			List<ConversionRule> abstractHead = new ArrayList<>();
			for (ConversionRule rule : rl) {
				if (rule.getFromTokens().isEmpty())
					continue;
				ConversionToken first = rule.getFromTokens().get(0);
				if (first.isAbstractParam() || first.isReceiverParam()) {
					abstractHead.add(rule);
				} else {
					concreteHead.computeIfAbsent(first.getValue(), k -> new ArrayList<>()).add(rule);
				}
			}
			return new RuleIndex(concreteHead, abstractHead);
		});
	}

	/**
	 * 診断用: RECEIVER の isValidReceiverCapture をスキップしてマッチする。 括弧始まりの式 {@code ("")},
	 * {@code (1+2)}, {@code (this.m_str)} 等も 診断候補として検出できる。
	 *
	 * @param rules
	 *            試行する変換ルールリスト
	 * @param tokens
	 *            フラットなトークン文字列リスト
	 * @return 全マッチ結果のリスト
	 */
	public List<MatchResult> matchAllForDiagnostic(List<ConversionRule> rules, List<String> tokens) {
		this.diagnosticMode = true;
		try {
			return matchAll(rules, tokens);
		} finally {
			this.diagnosticMode = false;
		}
	}

	/**
	 * 単一ルールをトークンリストに対してマッチングし、全マッチ位置を返す。
	 *
	 * @param rule
	 *            変換ルール
	 * @param tokens
	 *            フラットなトークン文字列リスト
	 * @return このルールに対するマッチ結果リスト
	 */
	public List<MatchResult> matchRule(ConversionRule rule, List<String> tokens) {
		List<MatchResult> results = new ArrayList<>();
		List<ConversionToken> pattern = rule.getFromTokens();

		if (pattern.isEmpty() || tokens.isEmpty()) {
			return results;
		}

		// トークン列の各開始位置でパターンマッチを試みる
		for (int startIdx = 0; startIdx < tokens.size(); startIdx++) {
			Optional<MatchResult> match = tryMatch(rule, pattern, tokens, startIdx);
			match.ifPresent(results::add);
		}
		return results;
	}

	/**
	 * 指定した開始位置からパターンのマッチを試みる。
	 *
	 * @param rule
	 *            変換ルール
	 * @param pattern
	 *            from パターンのトークンリスト
	 * @param tokens
	 *            フラットなトークン文字列リスト
	 * @param startIdx
	 *            開始インデックス
	 * @return マッチ成功時は {@link MatchResult}、失敗時は空の Optional
	 */
	private Optional<MatchResult> tryMatch(ConversionRule rule, List<ConversionToken> pattern, List<String> tokens,
			int startIdx) {
		Map<Integer, List<String>> captures = new HashMap<>();
		int endIdx = matchRecursive(pattern, 0, tokens, startIdx, captures);
		if (endIdx < 0) {
			return Optional.empty();
		}
		return Optional.of(new MatchResult(rule, captures, startIdx, endIdx));
	}

	/**
	 * 再帰的なパターンマッチング。
	 *
	 * <p>
	 * パターンの {@code patIdx} 番目のトークンから、 トークン列の {@code tokIdx} 番目以降にマッチを試みる。
	 * </p>
	 *
	 * @param pattern
	 *            fromパターン
	 * @param patIdx
	 *            現在のパターンインデックス
	 * @param tokens
	 *            フラットトークンリスト
	 * @param tokIdx
	 *            現在のトークンインデックス
	 * @param captures
	 *            キャプチャ記録 (インデックス → トークンリスト)
	 * @return マッチ成功時はマッチ終了インデックス (exclusive)、失敗時は -1
	 */
	private int matchRecursive(List<ConversionToken> pattern, int patIdx, List<String> tokens, int tokIdx,
			Map<Integer, List<String>> captures) {

		// パターン末尾に達したら成功
		if (patIdx == pattern.size()) {
			return tokIdx;
		}
		// トークン末尾に達してパターンが残っていたら失敗
		if (tokIdx > tokens.size()) {
			return -1;
		}

		ConversionToken patToken = pattern.get(patIdx);

		if (patToken.isAbstractParam()) {
			// ABSTRACT_PARAM[nn]: 括弧深さを考慮したアンカー探索
			return matchAbstractParam(pattern, patIdx, patToken.getCaptureKey(), tokens, tokIdx, captures);
		} else if (patToken.isReceiverParam()) {
			// RECEIVER[nn]: postfix チェーンに限定したアンカー探索
			return matchReceiverParam(pattern, patIdx, patToken.getCaptureKey(), tokens, tokIdx, captures);
		} else {
			// 具体トークン: 完全一致チェック
			if (tokIdx >= tokens.size()) {
				return -1;
			}
			if (!tokens.get(tokIdx).equals(patToken.getValue())) {
				return -1;
			}
			return matchRecursive(pattern, patIdx + 1, tokens, tokIdx + 1, captures);
		}
	}

	/**
	 * ABSTRACT_PARAM のマッチング処理（括弧深さ考慮版）。
	 *
	 * <p>
	 * パターンの次の具体トークン（アンカー）を、括弧深さが 0 の位置でのみ 候補として探索する。これにより:
	 * </p>
	 * <ul>
	 * <li>{@code sin(CreateMessage(a, b))} → ABSTRACT_PARAM が
	 * {@code CreateMessage(a, b)} 全体をキャプチャ</li>
	 * <li>{@code AfxMessageBox(expr, MB_OK)} → {@code ,} の前の {@code expr}
	 * 全体をキャプチャ</li>
	 * </ul>
	 *
	 * <p>
	 * 同じキャプチャキーが複数回登場した場合、 1回目のキャプチャ内容と一致する場合のみ成功とする（グループ参照）。
	 * </p>
	 *
	 * @param pattern
	 *            fromパターン
	 * @param patIdx
	 *            現在の ABSTRACT_PARAM のパターンインデックス
	 * @param captureKey
	 *            captures マップのキー
	 * @param tokens
	 *            フラットトークンリスト
	 * @param tokIdx
	 *            現在のトークンインデックス
	 * @param captures
	 *            キャプチャ記録
	 * @return マッチ成功時はマッチ終了インデックス (exclusive)、失敗時は -1
	 */
	private int matchAbstractParam(List<ConversionToken> pattern, int patIdx, int captureKey, List<String> tokens,
			int tokIdx, Map<Integer, List<String>> captures) {
		OptionalInt groupRef = tryGroupReference(pattern, patIdx, captureKey, tokens, tokIdx, captures);
		if (groupRef.isPresent())
			return groupRef.getAsInt();

		int nextConcretePatIdx = findNextConcretePatIdx(pattern, patIdx + 1);
		if (nextConcretePatIdx >= pattern.size()) {
			return searchWithoutAnchor(pattern, patIdx, captureKey, tokens, tokIdx, captures,
					PatternMatcher::hasNoTopLevelComma);
		}
		return searchByAnchor(pattern, patIdx, captureKey, tokens, tokIdx, captures,
				pattern.get(nextConcretePatIdx).getValue(), PatternMatcher::hasNoTopLevelComma);
	}

	/**
	 * キャプチャ候補に深さ 0 のカンマが含まれないかチェックする。
	 *
	 * <p>
	 * これにより {@code from: f(ABSTRACT_PARAM00)} は引数1個の呼び出しにしかマッチしない。 ネスト式
	 * {@code g(a, b)} の中のカンマは depth > 0 なので影響しない。
	 * </p>
	 */
	private static boolean hasNoTopLevelComma(List<String> tokens) {
		BracketDepthTracker tracker = new BracketDepthTracker();
		for (String t : tokens) {
			tracker.track(t);
			if (tracker.atSurface() && ",".equals(t))
				return false;
		}
		return true;
	}

	/**
	 * RECEIVER[nn] のマッチング処理。
	 *
	 * <p>
	 * ABSTRACT_PARAM と同様のアンカー探索を行うが、キャプチャされたトークン列が 有効な postfix
	 * チェーン（識別子・メンバアクセス・添字・関数呼び出しの連鎖）で あることを追加で検証する。
	 * </p>
	 *
	 * <p>
	 * 有効な postfix チェーンの条件:
	 * </p>
	 * <ul>
	 * <li>空でないこと</li>
	 * <li>先頭が括弧でないこと</li>
	 * <li>深さ 0 のトークンが識別子・{@code .}・{@code ->} のみであること</li>
	 * <li>括弧が均衡していること</li>
	 * </ul>
	 *
	 * <p>
	 * 単一の合成置換トークン（変換後の中間表現）はそのまま有効と判定する。
	 * </p>
	 *
	 * @param pattern
	 *            fromパターン
	 * @param patIdx
	 *            現在の RECEIVER のパターンインデックス
	 * @param captureKey
	 *            captures マップのキー（RECEIVER_CAPTURE_KEY 固定 = 100）
	 * @param tokens
	 *            フラットトークンリスト
	 * @param tokIdx
	 *            現在のトークンインデックス
	 * @param captures
	 *            キャプチャ記録
	 * @return マッチ成功時はマッチ終了インデックス (exclusive)、失敗時は -1
	 */
	private int matchReceiverParam(List<ConversionToken> pattern, int patIdx, int captureKey, List<String> tokens,
			int tokIdx, Map<Integer, List<String>> captures) {
		// RECEIVER は "." や "->" の直後から始まれない。
		// これによりドットチェーン中間のメソッド名が誤ってレシーバーとして捕捉されることを防ぐ。
		if (tokIdx > 0 && isDotOrArrow(tokens.get(tokIdx - 1)))
			return -1;

		OptionalInt groupRef = tryGroupReference(pattern, patIdx, captureKey, tokens, tokIdx, captures);
		if (groupRef.isPresent())
			return groupRef.getAsInt();

		Predicate<List<String>> receiverFilter;
		if (diagnosticMode) {
			receiverFilter = ReceiverCapturePolicy::passesPrefilterForDiagnostic;
		} else if (receiverValidator == null) {
			receiverFilter = ReceiverCapturePolicy::passesPrefilterForDiagnostic;
		} else {
			final ReceiverValidator v = receiverValidator;
			receiverFilter = captured -> {
				if (captured.size() == 1)
					return true;
				if (!ReceiverCapturePolicy.passesPrefilter(captured))
					return false;
				return v.isValid(captured);
			};
		}
		int nextConcretePatIdx = findNextConcretePatIdx(pattern, patIdx + 1);
		if (nextConcretePatIdx >= pattern.size()) {
			return searchWithoutAnchor(pattern, patIdx, captureKey, tokens, tokIdx, captures, receiverFilter);
		}
		return searchByAnchor(pattern, patIdx, captureKey, tokens, tokIdx, captures,
				pattern.get(nextConcretePatIdx).getValue(), receiverFilter);
	}

	/**
	 * トークンが "." または "->" かどうかを返す。
	 */
	private static boolean isDotOrArrow(String token) {
		return ".".equals(token) || "->".equals(token);
	}

	// =========================================================================
	// A1: near-miss 検出
	// なぜ追加したか: ルールが「あと1トークンだけ違う」場合の情報をレポートに出すことで
	// ルール修正コストを大幅に下げるため。通常変換パスには影響しない。
	// =========================================================================

	/**
	 * A1 ニアミス検出用: 指定開始位置からパターンのマッチを試み、 何番目のパターントークンまで一致できたか（深さ）を返す。
	 *
	 * <p>
	 * マッチ成功時は {@code pattern.size()} を返す。 失敗時は失敗したパターンインデックスを返す（0 = 先頭トークン不一致）。
	 * </p>
	 *
	 * <p>
	 * 抽象トークン（ABSTRACT_PARAM / RECEIVER）は、次の具体アンカーを探して
	 * 深さをカウントする簡易実装。通常マッチ（{@link #tryMatch}）より粗いが ルール修正の手掛かりとしては十分な精度を持つ。
	 * </p>
	 *
	 * @param rule
	 *            変換ルール
	 * @param tokens
	 *            フラットトークンリスト
	 * @param startIdx
	 *            マッチ開始インデックス
	 * @return マッチ深さ（0 〜 {@code pattern.size()}）
	 */
	public int tryMatchGetDepth(ConversionRule rule, List<String> tokens, int startIdx) {
		List<ConversionToken> pattern = rule.getFromTokens();
		if (pattern.isEmpty() || startIdx >= tokens.size()) {
			return 0;
		}
		return matchDepthSimple(pattern, 0, tokens, startIdx);
	}

	/**
	 * パターンを先頭から順に照合し、到達した最大パターンインデックスを返す（簡易版）。
	 *
	 * <p>
	 * 抽象トークンに遭遇した場合は次の具体アンカーを探し、 アンカーが見つかれば深さをインクリメントして継続する。
	 * </p>
	 */
	private int matchDepthSimple(List<ConversionToken> pattern, int patIdx, List<String> tokens, int tokIdx) {
		if (patIdx >= pattern.size()) {
			return pattern.size(); // 成功
		}
		if (tokIdx >= tokens.size()) {
			return patIdx;
		}

		ConversionToken pat = pattern.get(patIdx);

		if (!pat.isAbstractParam() && !pat.isReceiverParam()) {
			// 具体トークン: 完全一致チェック
			if (tokens.get(tokIdx).equals(pat.getValue())) {
				return matchDepthSimple(pattern, patIdx + 1, tokens, tokIdx + 1);
			} else {
				return patIdx;
			}
		} else {
			// 抽象トークン: 次の具体アンカーを探して先に進む
			int nextConcretePatIdx = findNextConcretePatIdx(pattern, patIdx + 1);
			if (nextConcretePatIdx >= pattern.size()) {
				// 残りパターンが全て抽象トークン → 到達扱いで success
				return pattern.size();
			}
			String anchor = pattern.get(nextConcretePatIdx).getValue();
			BracketDepthTracker tracker = new BracketDepthTracker();
			for (int i = tokIdx; i < tokens.size(); i++) {
				if (tracker.atSurface() && tokens.get(i).equals(anchor)) {
					// アンカー発見: patIdx+1 から再帰
					return matchDepthSimple(pattern, patIdx + 1, tokens, i);
				}
				tracker.track(tokens.get(i));
			}
			// アンカーが見つからない → 深さ patIdx まで到達
			return patIdx;
		}
	}

	/**
	 * パターンの {@code fromPatIdx} 以降で最初の具体トークン（抽象化トークンでない）のインデックスを返す。 見つからなければ
	 * {@code pattern.size()} を返す。
	 */
	private static int findNextConcretePatIdx(List<ConversionToken> pattern, int fromPatIdx) {
		int idx = fromPatIdx;
		while (idx < pattern.size() && (pattern.get(idx).isAbstractParam() || pattern.get(idx).isReceiverParam())) {
			idx++;
		}
		return idx;
	}

	/**
	 * グループ参照チェック。
	 *
	 * <p>
	 * 同じキャプチャキーが既にキャプチャ済みの場合、その内容と一致するかを検証する。
	 * </p>
	 *
	 * @return {@code OptionalInt.empty()} = 未キャプチャ（探索続行）、 {@code of(-1)} =
	 *         ミスマッチ、{@code of(n>=0)} = 成功（終了インデックス）
	 */
	private OptionalInt tryGroupReference(List<ConversionToken> pattern, int patIdx, int captureKey,
			List<String> tokens, int tokIdx, Map<Integer, List<String>> captures) {
		if (!captures.containsKey(captureKey))
			return OptionalInt.empty();
		List<String> expected = captures.get(captureKey);
		int end = tokIdx + expected.size();
		if (end > tokens.size())
			return OptionalInt.of(-1);
		if (!tokens.subList(tokIdx, end).equals(expected))
			return OptionalInt.of(-1);
		return OptionalInt.of(matchRecursive(pattern, patIdx + 1, tokens, end, captures));
	}

	/**
	 * アンカーなしのバックトラック探索。 パターン末尾まで全部抽象化トークンの場合に使用する。 {@code captureFilter} に
	 * {@code c -> true} を渡すと ABSTRACT_PARAM 版と等価。
	 */
	private int searchWithoutAnchor(List<ConversionToken> pattern, int patIdx, int captureKey, List<String> tokens,
			int tokIdx, Map<Integer, List<String>> captures, Predicate<List<String>> captureFilter) {
		for (int captureEnd = tokIdx; captureEnd <= tokens.size(); captureEnd++) {
			List<String> candidate = new ArrayList<>(tokens.subList(tokIdx, captureEnd));
			if (!captureFilter.test(candidate))
				continue;
			Map<Integer, List<String>> trialCaptures = new HashMap<>(captures);
			trialCaptures.put(captureKey, candidate);
			int result = matchRecursive(pattern, patIdx + 1, tokens, captureEnd, trialCaptures);
			if (result >= 0) {
				captures.putAll(trialCaptures);
				return result;
			}
		}
		return -1;
	}

	/**
	 * 括弧深さ対応のアンカー探索ループ。 深さ 0 でアンカートークンを見つけたときのみキャプチャを試みる。 {@code captureFilter} に
	 * {@code c -> true} を渡すと ABSTRACT_PARAM 版と等価。
	 *
	 * <p>
	 * <strong>早期終了</strong>: depth 0 で {@code ;} または
	 * {@code {}（ブロック開始）に達した場合は探索を打ち切る。 RECEIVER/ABSTRACT_PARAM のキャプチャは文境界を跨げないため、
	 * 余分な線形走査を省いて O(T²) → O(T×K) に改善する。
	 * </p>
	 */
	private int searchByAnchor(List<ConversionToken> pattern, int patIdx, int captureKey, List<String> tokens,
			int tokIdx, Map<Integer, List<String>> captures, String anchorValue,
			Predicate<List<String>> captureFilter) {
		BracketDepthTracker tracker = new BracketDepthTracker();
		for (int i = tokIdx; i < tokens.size(); i++) {
			String t = tokens.get(i);
			if (tracker.atSurface() && t.equals(anchorValue)) {
				List<String> candidate = new ArrayList<>(tokens.subList(tokIdx, i));
				if (captureFilter.test(candidate)) {
					Map<Integer, List<String>> trialCaptures = new HashMap<>(captures);
					trialCaptures.put(captureKey, candidate);
					int result = matchRecursive(pattern, patIdx + 1, tokens, i, trialCaptures);
					if (result >= 0) {
						captures.putAll(trialCaptures);
						return result;
					}
				}
			}
			// depth 0 で文境界（;）またはブロック開始（{）に達したら探索終了。
			// RECEIVER/ABSTRACT_PARAM は `;` を跨げない（ReceiverCapturePolicy が depth 0 の `;`
			// を拒否）。`{` 到達時も既存ルールでは跨ぎが不要なため早期終了で正確性が保たれる。
			if (tracker.atSurface() && (";".equals(t) || "{".equals(t))) {
				break;
			}
			if (!tracker.track(t) && !t.equals(anchorValue)) {
				break;
			}
		}
		return -1;
	}

}
