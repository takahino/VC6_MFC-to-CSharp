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

package io.github.takahino.cpp2csharp.discovery;

import io.github.takahino.cpp2csharp.grammar.CPP14Lexer;
import io.github.takahino.cpp2csharp.rule.ConversionRule;
import io.github.takahino.cpp2csharp.rule.ConversionRuleLoader.ThreePassRuleSet;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * C++ ソースディレクトリを解析し、変換ルール追加候補パターンを発見するエンジン。
 *
 * <p>
 * 処理フロー:
 * <ol>
 * <li>{@link #buildRuleIndex(ThreePassRuleSet)} で既存ルールの識別子インデックスを構築</li>
 * <li>{@code Files.walk} で {@code .cpp/.c/.h} を列挙</li>
 * <li>各ファイルを {@link #scanFile(Path)} でスキャン → {@link RawOccurrence} リスト</li>
 * <li>{@link #aggregate} で集約 → {@link CandidatePattern} リスト（出現回数降順）</li>
 * <li>{@link PatternDiscoveryResult} を構築して返す</li>
 * </ol>
 * </p>
 */
public class PatternDiscoveryEngine {

	private static final Logger LOG = LoggerFactory.getLogger(PatternDiscoveryEngine.class);

	/** 既知 MFC 型名リスト。TYPE_NAME パターンの検出対象。 */
	static final Set<String> KNOWN_MFC_TYPES = Set.of("CString", "BOOL", "DWORD", "UINT", "LONG", "LPCTSTR", "NULL",
			"TRUE", "FALSE", "LPCSTR", "LPSTR", "CTime", "CArray", "CBitmap", "CPen", "CBrush", "CFont", "CDC",
			"CDialog", "CWnd", "CStatic", "CEdit", "CButton", "CComboBox", "CListBox", "CListCtrl", "CTreeCtrl",
			"CMenu", "CFile", "COLORREF", "HBRUSH", "HPEN", "HFONT", "HDC", "HWND", "HANDLE", "HINSTANCE", "HRESULT",
			"WPARAM", "LPARAM");

	/**
	 * 内部集約前の生出現データ。
	 */
	record RawOccurrence(PatternType type, String identifierName, String accessOperator, int argCount,
			String fileName) {
	}

	/**
	 * 指定ディレクトリ内の C++ ソースを解析し、パターン発見結果を返す。
	 *
	 * @param inputDir
	 *            スキャン対象ディレクトリ
	 * @param ruleSet
	 *            既存ルールセット（カバー判定に使用）
	 * @return パターン発見結果
	 * @throws IOException
	 *             ファイル走査に失敗した場合
	 */
	public PatternDiscoveryResult discover(Path inputDir, ThreePassRuleSet ruleSet) throws IOException {
		Map<String, List<ConversionRule>> ruleIndex = buildRuleIndex(ruleSet);

		List<Path> cppFiles;
		try (var stream = Files.walk(inputDir)) {
			cppFiles = stream.filter(Files::isRegularFile).filter(p -> p.toString().matches(".*\\.(cpp|c|h)$")).sorted()
					.toList();
		}

		LOG.info("パターン発見: スキャン対象ファイル数={}", cppFiles.size());

		List<RawOccurrence> allRaw = new ArrayList<>();
		for (Path path : cppFiles) {
			try {
				allRaw.addAll(scanFile(path));
			} catch (IOException e) {
				LOG.warn("スキャン失敗: {} — {}", path, e.getMessage());
			}
		}

		List<CandidatePattern> candidates = aggregate(allRaw, ruleIndex);
		List<CandidatePattern> uncovered = candidates.stream().filter(c -> !c.hasRule()).toList();
		List<CandidatePattern> covered = candidates.stream().filter(CandidatePattern::hasRule).toList();
		int totalOccurrences = allRaw.size();

		LOG.info("パターン発見完了: パターン数={}, カバー済み={}, 未カバー={}", candidates.size(), covered.size(), uncovered.size());

		return new PatternDiscoveryResult(candidates, uncovered, covered, cppFiles.size(), totalOccurrences);
	}

	/**
	 * 1ファイルをトークン走査して生出現リストを返す。
	 *
	 * @param path
	 *            スキャン対象ファイル
	 * @return 生出現リスト
	 * @throws IOException
	 *             ファイル読み込みに失敗した場合
	 */
	List<RawOccurrence> scanFile(Path path) throws IOException {
		String source = Files.readString(path, StandardCharsets.UTF_8);
		String fileName = path.getFileName().toString();

		CPP14Lexer lexer = new CPP14Lexer(CharStreams.fromString(source));
		// エラー出力を抑制
		lexer.removeErrorListeners();
		CommonTokenStream stream = new CommonTokenStream(lexer);
		stream.fill();

		List<Token> tokens = stream.getTokens().stream()
				.filter(t -> t.getChannel() == Token.DEFAULT_CHANNEL && t.getType() != Token.EOF).toList();

		List<RawOccurrence> occurrences = new ArrayList<>();
		int size = tokens.size();

		for (int i = 0; i < size; i++) {
			Token prev = (i > 0) ? tokens.get(i - 1) : null;
			Token cur = tokens.get(i);
			Token next = (i + 1 < size) ? tokens.get(i + 1) : null;

			// GLOBAL_FUNC: Identifier ( — 前トークンが . / -> / :: でない（自クラス定義 ClassName::Func(
			// を除外）
			if (cur.getType() == CPP14Lexer.Identifier && next != null && next.getText().equals("(")
					&& (prev == null || (!prev.getText().equals(".") && prev.getType() != CPP14Lexer.Arrow
							&& prev.getType() != CPP14Lexer.Doublecolon))) {
				int argc = countArgs(tokens, i + 1);
				occurrences.add(new RawOccurrence(PatternType.GLOBAL_FUNC, cur.getText(), null, argc, fileName));
			}

			// METHOD_CALL: . Identifier ( または -> Identifier (
			if ((cur.getText().equals(".") || cur.getType() == CPP14Lexer.Arrow) && next != null
					&& next.getType() == CPP14Lexer.Identifier && i + 2 < size
					&& tokens.get(i + 2).getText().equals("(")) {
				int argc = countArgs(tokens, i + 2);
				occurrences
						.add(new RawOccurrence(PatternType.METHOD_CALL, next.getText(), cur.getText(), argc, fileName));
			}

			// TYPE_NAME: KNOWN_MFC_TYPES に含まれる Identifier
			if (cur.getType() == CPP14Lexer.Identifier && KNOWN_MFC_TYPES.contains(cur.getText())) {
				occurrences.add(new RawOccurrence(PatternType.TYPE_NAME, cur.getText(), null, -1, fileName));
			}
		}

		return occurrences;
	}

	/**
	 * 開き括弧インデックスから引数数を数える（深さ0のカンマ数）。
	 *
	 * @param tokens
	 *            トークンリスト
	 * @param openParenIdx
	 *            {@code (} のインデックス
	 * @return 引数数（空引数リストは0）
	 */
	int countArgs(List<Token> tokens, int openParenIdx) {
		int size = tokens.size();
		if (openParenIdx >= size)
			return 0;
		// openParenIdx は ( のインデックス
		int depth = 0;
		boolean hasContent = false;
		int commaCount = 0;

		for (int i = openParenIdx; i < size; i++) {
			String text = tokens.get(i).getText();
			if (text.equals("(") || text.equals("[") || text.equals("{")) {
				depth++;
				if (depth == 1)
					continue; // 開き括弧自体はスキップ
				hasContent = true;
			} else if (text.equals(")") || text.equals("]") || text.equals("}")) {
				depth--;
				if (depth == 0) {
					// 閉じ括弧で終了
					return hasContent ? commaCount + 1 : 0;
				}
			} else if (text.equals(",") && depth == 1) {
				commaCount++;
				hasContent = true;
			} else {
				if (depth >= 1)
					hasContent = true;
			}
		}
		return hasContent ? commaCount + 1 : 0;
	}

	/**
	 * 既存ルールの識別子→ルール対応インデックスを構築する。
	 *
	 * @param ruleSet
	 *            ルールセット
	 * @return 識別子 → ルールリストのマップ
	 */
	Map<String, List<ConversionRule>> buildRuleIndex(ThreePassRuleSet ruleSet) {
		Map<String, List<ConversionRule>> index = new HashMap<>();
		ruleSet.mainPhases().stream().flatMap(List::stream).forEach(rule -> {
			rule.getFromTokens().stream()
					.filter(t -> !t.isAbstractParam() && !t.isReceiverParam() && !t.isRegexParam()
							&& !t.isLexerTypeParam())
					.map(io.github.takahino.cpp2csharp.rule.ConversionToken::getValue)
					.filter(v -> v != null && v.matches("[A-Za-z_][A-Za-z0-9_]*"))
					.forEach(id -> index.computeIfAbsent(id, k -> new ArrayList<>()).add(rule));
		});
		return index;
	}

	/**
	 * 生出現リストを集約して {@link CandidatePattern} リストを返す（出現回数降順）。
	 *
	 * @param rawList
	 *            生出現リスト
	 * @param ruleIndex
	 *            識別子→ルール対応インデックス
	 * @return 集約済みパターンリスト
	 */
	private List<CandidatePattern> aggregate(List<RawOccurrence> rawList, Map<String, List<ConversionRule>> ruleIndex) {
		// 集約キー: (type, identifierName, accessOperator, argCount)
		// argCount 込みにすることで、同じメソッド名でも引数数ごとに別パターンとして扱う
		record AggKey(PatternType type, String name, String op, int argCount) {
		}

		// キーごとに集約
		Map<AggKey, List<RawOccurrence>> grouped = rawList.stream().collect(
				Collectors.groupingBy(r -> new AggKey(r.type(), r.identifierName(), r.accessOperator(), r.argCount())));

		List<CandidatePattern> result = new ArrayList<>();
		for (var entry : grouped.entrySet()) {
			AggKey key = entry.getKey();
			List<RawOccurrence> occList = entry.getValue();

			int count = occList.size();
			Set<String> files = occList.stream().map(RawOccurrence::fileName)
					.collect(Collectors.toCollection(TreeSet::new));

			// ルールカバー判定
			List<ConversionRule> matchingRules = ruleIndex.getOrDefault(key.name(), List.of());
			boolean hasRule = !matchingRules.isEmpty();
			String ruleFile = matchingRules.stream().map(r -> r.getSourceFile() != null ? r.getSourceFile() : "")
					.filter(s -> !s.isEmpty()).distinct().collect(Collectors.joining(", "));

			result.add(new CandidatePattern(key.type(), key.name(), key.op(), key.argCount(), count, hasRule, ruleFile,
					files));
		}

		// 出現回数降順でソート
		result.sort(Comparator.comparingInt(CandidatePattern::occurrenceCount).reversed());
		return result;
	}
}
