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

import io.github.takahino.cpp2csharp.grammar.CPP14Lexer;
import io.github.takahino.cpp2csharp.grammar.CPP14Parser;
import io.github.takahino.cpp2csharp.grammar.CPP14ParserBaseVisitor;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.List;

/**
 * 変換ルールの from パターンを ANTLR CPP14 文法でパースし、 引数個数などの構文情報を抽出するクラス。
 *
 * <p>
 * 文字列操作ではなく構文解析により、ネストした括弧や複雑な式を正しく扱う。
 * </p>
 */
public final class RulePatternParser {

	private RulePatternParser() {
	}

	/**
	 * from パターンから期待する引数個数を ANTLR で構文解析して導出する。
	 *
	 * <p>
	 * パターンを C++ の expression として ANTLR CPP14 でパースし、 最初の関数呼び出しの expressionList から
	 * initializerClause 数を取得する。
	 * </p>
	 *
	 * @param fromTokens
	 *            from パターンのトークン列
	 * @return 期待する引数個数、括弧なしまたはパース失敗時は -1
	 */
	public static int parseArgumentCount(List<ConversionToken> fromTokens) {
		List<String> values = fromTokens.stream().map(ConversionToken::getValue).toList();
		if (values.stream().noneMatch("("::equals)) {
			return -1;
		}

		String cpp = toCppStatement(values);
		String expr = cpp.trim().endsWith(";") ? cpp.trim().replaceFirst(";\\s*$", "").trim() : cpp;
		try {
			CPP14Lexer lexer = new CPP14Lexer(CharStreams.fromString(expr));
			CommonTokenStream tokens = new CommonTokenStream(lexer);
			CPP14Parser parser = new CPP14Parser(tokens);
			parser.removeErrorListeners();

			ParseTree tree = parser.expression();
			ArgumentCountVisitor visitor = new ArgumentCountVisitor();
			visitor.visit(tree);
			int result = visitor.getArgumentCount();
			return result >= 0 ? result : -1;
		} catch (Exception e) {
			return -1;
		}
	}

	private static String toCppStatement(List<String> values) {
		return String.join(" ", values);
	}

	/**
	 * 最初の関数呼び出しの expressionList から引数個数を取得するビジター。
	 */
	private static class ArgumentCountVisitor extends CPP14ParserBaseVisitor<Void> {

		private int argumentCount = -1;

		int getArgumentCount() {
			return argumentCount;
		}

		@Override
		public Void visitPostfixExpression(CPP14Parser.PostfixExpressionContext ctx) {
			if (argumentCount >= 0) {
				return null;
			}
			if (ctx.LeftParen() != null && ctx.RightParen() != null) {
				CPP14Parser.ExpressionListContext exprList = ctx.expressionList();
				if (exprList != null && exprList.initializerList() != null) {
					argumentCount = exprList.initializerList().initializerClause().size();
					return null;
				}
				argumentCount = 0;
				return null;
			}
			return visitChildren(ctx);
		}
	}
}
