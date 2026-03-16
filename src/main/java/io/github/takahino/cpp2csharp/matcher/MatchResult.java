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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * パターンマッチングの結果を保持するクラス。
 *
 * <p>
 * マッチが成功した場合、各 ABSTRACT_PARAM[nn] が対応するトークン列 ({@code List<String>}) にキャプチャされる。
 * </p>
 */
public final class MatchResult {

	private static final Pattern ABSTRACT_PARAM_PATTERN = Pattern.compile("ABSTRACT_PARAM(\\d{2})");

	private static final Pattern RECEIVER_PATTERN = Pattern.compile("\\b" + ConversionToken.RECEIVER_TOKEN + "\\b");

	/** マッチしたルール */
	private final ConversionRule rule;

	/**
	 * ABSTRACT_PARAM のキャプチャ結果。 キー: パラメータインデックス (0〜99)、値: マッチしたトークン文字列リスト
	 */
	private final Map<Integer, List<String>> captures;

	/** マッチ開始位置 (フラットトークンリスト内のインデックス) */
	private final int startIndex;

	/** マッチ終了位置 (フラットトークンリスト内のインデックス、exclusive) */
	private final int endIndex;

	/**
	 * コンストラクタ。
	 *
	 * @param rule
	 *            マッチしたルール
	 * @param captures
	 *            ABSTRACT_PARAM のキャプチャ結果
	 * @param startIndex
	 *            マッチ開始インデックス
	 * @param endIndex
	 *            マッチ終了インデックス (exclusive)
	 */
	public MatchResult(ConversionRule rule, Map<Integer, List<String>> captures, int startIndex, int endIndex) {
		this.rule = Objects.requireNonNull(rule, "rule が null です");
		this.captures = Collections.unmodifiableMap(Objects.requireNonNull(captures, "captures が null です"));
		this.startIndex = startIndex;
		this.endIndex = endIndex;
	}

	/**
	 * マッチしたルールを返す。
	 *
	 * @return マッチルール
	 */
	public ConversionRule getRule() {
		return rule;
	}

	/**
	 * 全キャプチャ結果を返す。
	 *
	 * @return パラメータインデックス → トークンリストのマップ
	 */
	public Map<Integer, List<String>> getCaptures() {
		return captures;
	}

	/**
	 * 指定インデックスの ABSTRACT_PARAM にキャプチャされたトークンリストを返す。
	 *
	 * @param paramIndex
	 *            パラメータインデックス (0〜99)
	 * @return キャプチャされたトークンリスト (キャプチャなしの場合は空リスト)
	 */
	public List<String> getCapturedTokens(int paramIndex) {
		return captures.getOrDefault(paramIndex, List.of());
	}

	/**
	 * 指定インデックスの ABSTRACT_PARAM にキャプチャされたトークンを スペース区切りで結合した文字列を返す。
	 *
	 * @param paramIndex
	 *            パラメータインデックス (0〜99)
	 * @return スペース区切りのトークン文字列
	 */
	public String getCapturedText(int paramIndex) {
		List<String> tokens = getCapturedTokens(paramIndex);
		return String.join(" ", tokens);
	}

	/**
	 * マッチ開始インデックスを返す。
	 *
	 * @return 開始インデックス
	 */
	public int getStartIndex() {
		return startIndex;
	}

	/**
	 * マッチ終了インデックス (exclusive) を返す。
	 *
	 * @return 終了インデックス
	 */
	public int getEndIndex() {
		return endIndex;
	}

	/**
	 * マッチしたトークンの総数を返す。
	 *
	 * @return マッチトークン数
	 */
	public int getMatchLength() {
		return endIndex - startIndex;
	}

	/**
	 * ルールの to テンプレートを ABSTRACT_PARAM・RECEIVER のキャプチャで展開した文字列を返す。
	 *
	 * @return 展開後の置換テキスト
	 */
	public String getExpandedToTemplate() {
		return expandToTemplate(rule.getToTemplate(), captures);
	}

	/**
	 * テンプレート文字列を ABSTRACT_PARAM・RECEIVER のキャプチャで展開する（静的ユーティリティ）。
	 *
	 * @param template
	 *            テンプレート文字列
	 * @param captures
	 *            パラメータインデックス → トークンリストのマップ
	 * @return 展開後の文字列
	 */
	public static String expandToTemplate(String template, Map<Integer, List<String>> captures) {
		if (template == null)
			return "";
		String result = ABSTRACT_PARAM_PATTERN.matcher(template).replaceAll(mr -> Matcher
				.quoteReplacement(String.join(" ", captures.getOrDefault(Integer.parseInt(mr.group(1)), List.of()))));
		List<String> receiverCapture = captures.getOrDefault(ConversionToken.RECEIVER_CAPTURE_KEY, List.of());
		result = RECEIVER_PATTERN.matcher(result)
				.replaceAll(Matcher.quoteReplacement(String.join(" ", receiverCapture)));
		return result;
	}

	@Override
	public String toString() {
		return String.format("MatchResult{rule=%s, start=%d, end=%d, captures=%s}", rule.getSourceFile(), startIndex,
				endIndex, captures);
	}
}
