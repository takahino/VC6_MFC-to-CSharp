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

package io.github.takahino.cpp2csharp.retokenize;

import io.github.takahino.cpp2csharp.rule.CollectingErrorListener;
import io.github.takahino.cpp2csharp.rule.LanguageLexerFactory;
import io.github.takahino.cpp2csharp.tree.AstNode;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * トークンノード列を再トークン化するクラス。
 *
 * <p>
 * 変換フェーズ間（pre→main、main→post 等）に使用し、置換由来の合成トークンを 個別の C++ トークンに分解し直す。
 * </p>
 *
 * <p>
 * 再トークン化により、後続フェーズのパターンマッチングが正しく動作するようになる。
 * </p>
 */
public class Retokenizer {

	private static final Logger LOGGER = LoggerFactory.getLogger(Retokenizer.class);

	private final LanguageLexerFactory lexerFactory;

	/**
	 * デフォルトコンストラクタ。lexerFactory が null の場合、retokenize() で例外がスローされる。
	 */
	public Retokenizer() {
		this(null);
	}

	/**
	 * コンストラクタ。LanguageLexerFactory を注入する。
	 *
	 * @param lexerFactory
	 *            言語固有の Lexer を生成するファクトリ
	 */
	public Retokenizer(LanguageLexerFactory lexerFactory) {
		this.lexerFactory = lexerFactory;
	}

	/**
	 * トークンノード列を再トークン化し、新トークンストリームに対応するコメントマップも返す。
	 *
	 * <p>
	 * commentsBeforeToken が空でない場合、コメント・改行・#include 等をソースに含めて構築し、
	 * 再トークン化後も保持する。空の場合はスペース区切りで結合する（テスト用）。
	 * </p>
	 *
	 * @param tokenNodes
	 *            再トークン化対象のトークンノード列
	 * @param commentsBeforeToken
	 *            各トークン直前のコメント・改行・空白のマップ（空可）
	 * @return 再トークン化されたノード列とコメントマップ
	 */
	public RetokenizeResult retokenize(List<AstNode> tokenNodes, Map<Integer, List<String>> commentsBeforeToken) {
		String source = buildSource(tokenNodes, commentsBeforeToken);
		LOGGER.debug("再トークン化: {} chars", source.length());
		return lex(source);
	}

	private String buildSource(List<AstNode> tokenNodes, Map<Integer, List<String>> commentsBeforeToken) {
		StringBuilder sb = new StringBuilder();
		int i = 0;
		while (i < tokenNodes.size()) {
			AstNode node = tokenNodes.get(i);
			int streamIdx = node.getStreamIndex();
			int groupEnd = i + 1;
			if (node.getStreamIndex() < 0) {
				while (groupEnd < tokenNodes.size() && tokenNodes.get(groupEnd).getStreamIndex() < 0) {
					groupEnd++;
				}
			}
			StringBuilder groupText = new StringBuilder();
			for (int j = i; j < groupEnd; j++) {
				groupText.append(tokenNodes.get(j).getText());
			}
			String gText = groupText.toString();
			if ("<EOF>".equals(gText)) {
				i = groupEnd;
				continue;
			}
			// コメント・改行・#include を保持: commentsBeforeToken から取得して出力
			if (streamIdx >= 0 && commentsBeforeToken != null && !commentsBeforeToken.isEmpty()) {
				List<String> comments = commentsBeforeToken.get(streamIdx);
				if (comments != null) {
					for (String item : comments) {
						sb.append(item);
					}
				}
			} else if (sb.length() > 0) {
				sb.append(" ");
			}
			sb.append(gText);
			i = groupEnd;
		}
		return sb.toString();
	}

	private RetokenizeResult lex(String source) {
		if (lexerFactory == null) {
			throw new IllegalStateException(
					"LanguageLexerFactory が設定されていません。Retokenizer(LanguageLexerFactory) コンストラクタを使用してください。");
		}
		Lexer lexer = lexerFactory.createLexer(CharStreams.fromString(source));
		CollectingErrorListener errorListener = new CollectingErrorListener();
		lexer.removeErrorListeners();
		lexer.addErrorListener(errorListener);

		CommonTokenStream tokenStream = new CommonTokenStream(lexer);
		tokenStream.fill();

		if (errorListener.hasErrors()) {
			LOGGER.warn("再トークン化で字句エラー: {}", errorListener.getErrors());
		}

		Map<Integer, List<String>> commentsBeforeToken = buildCommentsMap(tokenStream);

		List<AstNode> result = new ArrayList<>();
		for (Token token : tokenStream.getTokens()) {
			if (token.getChannel() != Token.DEFAULT_CHANNEL)
				continue;
			if (token.getType() == Token.EOF)
				break;
			result.add(AstNode.tokenNode(token.getText(), token.getLine(), token.getCharPositionInLine(),
					token.getTokenIndex()));
		}
		return new RetokenizeResult(result, commentsBeforeToken);
	}

	public static Map<Integer, List<String>> buildCommentsMap(CommonTokenStream tokenStream) {
		Map<Integer, List<String>> result = new LinkedHashMap<>();
		List<String> pending = new ArrayList<>();

		for (Token token : tokenStream.getTokens()) {
			String text = token.getText();
			if (token.getChannel() == Token.HIDDEN_CHANNEL) {
				if (text.startsWith("//") || text.startsWith("/*")) {
					pending.add(text);
				} else if (text.startsWith("#")) {
					pending.add(text);
				} else if (isNewlineToken(text)) {
					pending.add(text);
				} else if (isWhitespaceToken(text)) {
					pending.add(text);
				}
			} else if (token.getChannel() == Token.DEFAULT_CHANNEL) {
				if (!pending.isEmpty()) {
					result.put(token.getTokenIndex(), new ArrayList<>(pending));
					pending.clear();
				}
			}
		}
		return result;
	}

	private static boolean isNewlineToken(String text) {
		return "\n".equals(text) || "\r\n".equals(text) || "\r".equals(text);
	}

	private static boolean isWhitespaceToken(String text) {
		if (text == null || text.isEmpty())
			return false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c != ' ' && c != '\t')
				return false;
		}
		return true;
	}
}
