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

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import java.util.ArrayList;
import java.util.List;

/**
 * ANTLR のパースエラーを収集するエラーリスナー。
 *
 * <p>
 * ANTLR のデフォルトエラーリスナー ({@code ConsoleErrorListener}) の代わりに使用し、
 * エラーメッセージをリストに蓄積する。
 * </p>
 */
public class CollectingErrorListener extends BaseErrorListener {

	/** 収集したエラーメッセージのリスト */
	private final List<String> errors = new ArrayList<>();

	/**
	 * 構文エラーをリストに記録する。
	 *
	 * @param recognizer
	 *            認識器 (lexer または parser)
	 * @param offendingSymbol
	 *            エラー発生トークン
	 * @param line
	 *            エラー行番号
	 * @param charPositionInLine
	 *            エラーカラム番号
	 * @param msg
	 *            エラーメッセージ
	 * @param e
	 *            認識例外 (null の場合あり)
	 */
	@Override
	public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine,
			String msg, RecognitionException e) {
		String error = String.format("line %d:%d %s", line, charPositionInLine, msg);
		errors.add(error);
	}

	/**
	 * エラーが1件以上あるかどうかを返す。
	 *
	 * @return エラーありの場合 true
	 */
	public boolean hasErrors() {
		return !errors.isEmpty();
	}

	/**
	 * 収集したエラーメッセージのリストを返す。
	 *
	 * @return エラーメッセージリスト
	 */
	public List<String> getErrors() {
		return List.copyOf(errors);
	}
}
