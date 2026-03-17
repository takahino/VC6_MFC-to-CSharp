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

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 変換ルールにおける単一トークンを表すクラス。 具体的なリテラルトークン、ABSTRACT_PARAM[nn]、RECEIVER のいずれかである。
 *
 * <p>
 * ABSTRACT_PARAM[nn] は 00〜99 の番号付き抽象化パラメータで、 任意のトークン列にマッチする特殊トークンとして機能する。
 * </p>
 *
 * <p>
 * RECEIVER は postfix チェーン（識別子・メンバアクセス・添字・関数呼び出しの連鎖）
 * にマッチする役割別抽象化トークン。1ルールに1つのみ使用可能。captures マップでは {@link #RECEIVER_CAPTURE_KEY}
 * をキーとして格納される。
 * </p>
 *
 * <p>
 * REGEX トークン ({@code /pattern/} 形式) は正規表現によるマッチを行う特殊トークン。
 * </p>
 *
 * <p>
 * LEXER_TYPE トークン ({@code <TypeName>} 形式) はレキサートークン型名によるマッチを行う特殊トークン。
 * </p>
 */
public final class ConversionToken {

	/** ABSTRACT_PARAM の正規表現パターン (ABSTRACT_PARAM00 ～ ABSTRACT_PARAM99) */
	private static final Pattern ABSTRACT_PARAM_PATTERN = Pattern.compile("ABSTRACT_PARAM(\\d{2})");

	/** REGEX トークンのパターン: /pattern/ 形式 */
	private static final Pattern REGEX_TOKEN_PATTERN = Pattern.compile("^/(.+)/$");

	/** LEXER_TYPE トークンのパターン: <TypeName> 形式 */
	private static final Pattern LEXER_TYPE_TOKEN_PATTERN = Pattern.compile("^<([A-Za-z_][A-Za-z0-9_]*)>$");

	/** RECEIVER 抽象化トークンのキーワード文字列 */
	public static final String RECEIVER_TOKEN = "RECEIVER";

	/**
	 * RECEIVER キャプチャの captures マップ固定キー (値 = 100)。 ABSTRACT_PARAM (key 0-99) と衝突しない。
	 */
	public static final int RECEIVER_CAPTURE_KEY = 100;

	/** トークンの文字列値 */
	private final String value;

	/** このトークンが ABSTRACT_PARAM 抽象化トークンであるか */
	private final boolean abstractParam;

	/** このトークンが RECEIVER 抽象化トークンであるか */
	private final boolean receiverParam;

	/** このトークンが REGEX トークンであるか */
	private final boolean regexParam;

	/** このトークンが LEXER_TYPE トークンであるか */
	private final boolean lexerTypeParam;

	/** 抽象化トークンのインデックス (0〜99)。通常トークンの場合は -1 */
	private final int paramIndex;

	/** REGEX トークンの場合の正規表現パターン文字列。非 REGEX の場合は null */
	private final String regexPattern;

	/** LEXER_TYPE トークンの場合のレキサー型名。非 LEXER_TYPE の場合は null */
	private final String lexerTypeName;

	/**
	 * プライベートコンストラクタ。{@link #of(String)} ファクトリメソッドを使用すること。
	 */
	private ConversionToken(String value, boolean abstractParam, boolean receiverParam, boolean regexParam,
			boolean lexerTypeParam, int paramIndex, String regexPattern, String lexerTypeName) {
		this.value = value;
		this.abstractParam = abstractParam;
		this.receiverParam = receiverParam;
		this.regexParam = regexParam;
		this.lexerTypeParam = lexerTypeParam;
		this.paramIndex = paramIndex;
		this.regexPattern = regexPattern;
		this.lexerTypeName = lexerTypeName;
	}

	/**
	 * 文字列からトークンを生成するファクトリメソッド。 ABSTRACT_PARAM[nn] 形式は抽象化トークン、"RECEIVER"
	 * はレシーバートークンとして生成する。 /pattern/ 形式は REGEX トークン、{@literal <TypeName>} 形式は
	 * LEXER_TYPE トークンとして生成する。
	 *
	 * @param value
	 *            トークン文字列
	 * @return 生成された ConversionToken
	 * @throws IllegalArgumentException
	 *             値が null または空の場合
	 */
	public static ConversionToken of(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("トークン値は空にできません");
		}
		Matcher m = ABSTRACT_PARAM_PATTERN.matcher(value);
		if (m.matches()) {
			int idx = Integer.parseInt(m.group(1));
			return new ConversionToken(value, true, false, false, false, idx, null, null);
		}
		Matcher regexMatcher = REGEX_TOKEN_PATTERN.matcher(value);
		if (regexMatcher.matches()) {
			return new ConversionToken(value, false, false, true, false, -1, regexMatcher.group(1), null);
		}
		Matcher lexerTypeMatcher = LEXER_TYPE_TOKEN_PATTERN.matcher(value);
		if (lexerTypeMatcher.matches()) {
			return new ConversionToken(value, false, false, false, true, -1, null, lexerTypeMatcher.group(1));
		}
		if (RECEIVER_TOKEN.equals(value)) {
			return new ConversionToken(value, false, true, false, false, 0, null, null);
		}
		return new ConversionToken(value, false, false, false, false, -1, null, null);
	}

	/**
	 * トークンの文字列値を返す。
	 *
	 * @return トークン文字列
	 */
	public String getValue() {
		return value;
	}

	/**
	 * このトークンが ABSTRACT_PARAM かどうかを返す。
	 *
	 * @return ABSTRACT_PARAM トークンであれば true
	 */
	public boolean isAbstractParam() {
		return abstractParam;
	}

	/**
	 * このトークンが RECEIVER かどうかを返す。
	 *
	 * @return RECEIVER トークンであれば true
	 */
	public boolean isReceiverParam() {
		return receiverParam;
	}

	/**
	 * このトークンが REGEX トークン ({@code /pattern/} 形式) かどうかを返す。
	 *
	 * @return REGEX トークンであれば true
	 */
	public boolean isRegexParam() {
		return regexParam;
	}

	/**
	 * このトークンが LEXER_TYPE トークン ({@code <TypeName>} 形式) かどうかを返す。
	 *
	 * @return LEXER_TYPE トークンであれば true
	 */
	public boolean isLexerTypeParam() {
		return lexerTypeParam;
	}

	/**
	 * REGEX トークンの正規表現パターン文字列を返す。 非 REGEX トークンの場合は null を返す。
	 *
	 * @return 正規表現パターン文字列、または null
	 */
	public String getRegexPattern() {
		return regexPattern;
	}

	/**
	 * LEXER_TYPE トークンのレキサー型名を返す。 非 LEXER_TYPE トークンの場合は null を返す。
	 *
	 * @return レキサー型名、または null
	 */
	public String getLexerTypeName() {
		return lexerTypeName;
	}

	/**
	 * 抽象化トークンのインデックスを返す。 通常トークンの場合は -1 を返す。
	 *
	 * @return パラメータインデックス (0〜99)、または -1
	 */
	public int getParamIndex() {
		return paramIndex;
	}

	/**
	 * captures マップに使用するキーを返す。 ABSTRACT_PARAM: paramIndex (0-99) RECEIVER:
	 * RECEIVER_CAPTURE_KEY (100、固定) 通常トークン: -1
	 *
	 * @return captures マップキー
	 */
	public int getCaptureKey() {
		if (abstractParam)
			return paramIndex;
		if (receiverParam)
			return RECEIVER_CAPTURE_KEY;
		return -1;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof ConversionToken that))
			return false;
		return abstractParam == that.abstractParam && receiverParam == that.receiverParam
				&& regexParam == that.regexParam && lexerTypeParam == that.lexerTypeParam
				&& paramIndex == that.paramIndex && Objects.equals(value, that.value)
				&& Objects.equals(regexPattern, that.regexPattern) && Objects.equals(lexerTypeName, that.lexerTypeName);
	}

	@Override
	public int hashCode() {
		return Objects.hash(value, abstractParam, receiverParam, regexParam, lexerTypeParam, paramIndex, regexPattern,
				lexerTypeName);
	}

	@Override
	public String toString() {
		if (abstractParam)
			return String.format("ABSTRACT_PARAM[%02d]", paramIndex);
		if (receiverParam)
			return RECEIVER_TOKEN;
		if (regexParam)
			return "REGEX[" + regexPattern + "]";
		if (lexerTypeParam)
			return "LEXER_TYPE[" + lexerTypeName + "]";
		return value;
	}
}
