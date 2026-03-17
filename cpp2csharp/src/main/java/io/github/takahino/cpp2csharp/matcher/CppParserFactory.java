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

import io.github.takahino.cpp2csharp.grammar.CPP14Lexer;
import io.github.takahino.cpp2csharp.grammar.CPP14Parser;
import io.github.takahino.cpp2csharp.rule.LanguageLexerFactory;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;

/**
 * CPP14Lexer / CPP14Parser インスタンスの生成を集約するファクトリ。
 *
 * <p>
 * 構文エラー時に {@link org.antlr.v4.runtime.misc.ParseCancellationException} を投げる
 * {@link BailErrorStrategy} を標準構成とする。
 * </p>
 */
public final class CppParserFactory {

	private CppParserFactory() {
		throw new AssertionError("utility class");
	}

	/**
	 * LanguageLexerFactory として CPP14Lexer を生成するファクトリインスタンスを返す。
	 *
	 * @return CPP14Lexer を生成する LanguageLexerFactory
	 */
	public static LanguageLexerFactory asLexerFactory() {
		return input -> new CPP14Lexer(input);
	}

	/**
	 * 文字列をレキシングして fill 済みの {@link CommonTokenStream} を返す。
	 *
	 * @param expr
	 *            レキシング対象の C++ 式文字列
	 * @return 全トークンを読み込み済みのトークンストリーム
	 */
	static CommonTokenStream lex(String expr) {
		CPP14Lexer lexer = new CPP14Lexer(CharStreams.fromString(expr));
		CommonTokenStream stream = new CommonTokenStream(lexer);
		stream.fill();
		return stream;
	}

	/**
	 * トークンストリームから BailErrorStrategy 付きパーサを生成する。 ストリームは先頭（position 0）にシークしてから Parser
	 * に渡す。
	 *
	 * @param tokenStream
	 *            fill 済みのトークンストリーム
	 * @return 設定済みの {@link CPP14Parser}
	 */
	static CPP14Parser createParser(CommonTokenStream tokenStream) {
		tokenStream.seek(0);
		CPP14Parser parser = new CPP14Parser(tokenStream);
		parser.removeErrorListeners();
		parser.setErrorHandler(new BailErrorStrategy());
		return parser;
	}
}
