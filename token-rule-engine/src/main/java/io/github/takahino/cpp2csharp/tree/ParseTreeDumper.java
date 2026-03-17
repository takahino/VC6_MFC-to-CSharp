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

package io.github.takahino.cpp2csharp.tree;

import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;

/**
 * ANTLR ParseTree の文字列表現をダンプするユーティリティ。
 *
 * <p>
 * ルール設計のデバッグ用。トークン文字列を中心に、木構造を可読な形式で出力する。 同一文字列がネストして繰り返す場合は圧縮表示する（例:
 * {@code expression [3]}）。
 * </p>
 *
 * <p>
 * ANTLR の {@link ParseTree} と {@link CommonTokenStream} を 直接使用する。
 * </p>
 */
public final class ParseTreeDumper {

	private static final String INDENT = "  ";

	/**
	 * 木の文字列表現を生成する。
	 *
	 * @param tree
	 *            ANTLR パースツリーのルート
	 * @param ruleNames
	 *            パーサーのルール名配列（{@code parser.getRuleNames()}）
	 * @param tokenStream
	 *            全トークンがロードされた CommonTokenStream
	 * @return ダンプ文字列
	 */
	public String dump(ParseTree tree, String[] ruleNames, CommonTokenStream tokenStream) {
		StringBuilder sb = new StringBuilder();
		sb.append("--- フラットトークン列 (パターンマッチ対象) ---\n");
		List<String> tokens = extractDefaultChannelTokens(tokenStream);
		sb.append(String.join(" ", tokens)).append("\n\n");
		sb.append("--- 木構造 (ネスト同一は圧縮) ---\n");
		dumpNode(tree, ruleNames, 0, new ArrayList<>(), false, sb);
		return sb.toString();
	}

	/**
	 * CommonTokenStream から DEFAULT_CHANNEL のトークン文字列を抽出する（EOF を除く）。
	 */
	private List<String> extractDefaultChannelTokens(CommonTokenStream tokenStream) {
		List<String> result = new ArrayList<>();
		for (Token token : tokenStream.getTokens()) {
			if (token.getChannel() == Token.DEFAULT_CHANNEL && token.getType() != Token.EOF) {
				result.add(token.getText());
			}
		}
		return result;
	}

	/**
	 * ParseTree ノードを再帰的にダンプする。
	 *
	 * @param node
	 *            現在のノード
	 * @param ruleNames
	 *            ルール名配列
	 * @param depth
	 *            現在の深さ
	 * @param ancestors
	 *            祖先のルール名リスト（ネスト圧縮用）
	 * @param skipOutput
	 *            このノード自身の出力をスキップするか
	 * @param sb
	 *            出力バッファ
	 */
	private void dumpNode(ParseTree node, String[] ruleNames, int depth, List<String> ancestors, boolean skipOutput,
			StringBuilder sb) {
		if (node instanceof TerminalNode terminal) {
			if (terminal.getSymbol().getType() != Token.EOF) {
				sb.append(INDENT.repeat(depth)).append(terminal.getText()).append("\n");
			}
			return;
		}

		if (!(node instanceof ParserRuleContext ruleCtx)) {
			return;
		}

		String name = ruleNames[ruleCtx.getRuleIndex()];
		String subtreeText = getSubtreeText(node);

		int childCount = node.getChildCount();
		List<String> newAncestors = new ArrayList<>(ancestors);
		newAncestors.add(name);

		// 同一テキスト連鎖: 子が1つかつ文字列が同一なら圧縮
		if (childCount == 1) {
			ParseTree onlyChild = node.getChild(0);
			String childText = getSubtreeText(onlyChild);
			if (subtreeText.equals(childText)) {
				int grandChildCount = onlyChild.getChildCount();
				if (grandChildCount == 1 && !childText.contains(" ")) {
					ParseTree grandChild = onlyChild.getChild(0);
					String grandChildText = getSubtreeText(grandChild);
					if (childText.equals(grandChildText)) {
						if (!skipOutput) {
							sb.append(INDENT.repeat(depth)).append(name).append(" : ").append(subtreeText).append("\n");
						}
						return;
					}
				}
				if (!skipOutput) {
					sb.append(INDENT.repeat(depth)).append(name).append(" : ").append(subtreeText).append("\n");
				}
				boolean childHasBranch = grandChildCount != 1;
				dumpNode(onlyChild, ruleNames, depth + 1, newAncestors, !childHasBranch, sb);
				return;
			}
		}

		// 同一ルール名のネストをカウント
		int run = 1;
		for (int i = ancestors.size() - 1; i >= 0; i--) {
			if (ancestors.get(i).equals(name)) {
				run++;
			} else {
				break;
			}
		}

		if (!skipOutput) {
			String line = name + (run > 1 ? " [" + run + "]" : "") + " : " + subtreeText;
			if (run > 1) {
				int firstInRun = depth - run + 1;
				sb.append(INDENT.repeat(Math.max(0, firstInRun))).append(line).append("\n");
			} else {
				sb.append(INDENT.repeat(depth)).append(line).append("\n");
			}
		}

		for (int i = 0; i < childCount; i++) {
			dumpNode(node.getChild(i), ruleNames, depth + 1, newAncestors, false, sb);
		}
	}

	/**
	 * ノードのサブツリーに含まれる全 TerminalNode のテキストをスペース区切りで返す（EOF を除く）。
	 */
	private String getSubtreeText(ParseTree node) {
		if (node instanceof TerminalNode terminal) {
			int type = terminal.getSymbol().getType();
			return type == Token.EOF ? "" : terminal.getText();
		}
		List<String> tokens = new ArrayList<>();
		collectTerminalTexts(node, tokens);
		return String.join(" ", tokens);
	}

	private void collectTerminalTexts(ParseTree node, List<String> result) {
		if (node instanceof TerminalNode terminal) {
			if (terminal.getSymbol().getType() != Token.EOF) {
				result.add(terminal.getText());
			}
			return;
		}
		for (int i = 0; i < node.getChildCount(); i++) {
			collectTerminalTexts(node.getChild(i), result);
		}
	}
}
