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

import io.github.takahino.cpp2csharp.converter.ConversionResult;
import io.github.takahino.cpp2csharp.converter.CppToCSharpConverter;
import io.github.takahino.cpp2csharp.matcher.CppParserFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * ルールファイル内の test:/assrt: で定義されたルール内蔵テストを実行する。
 *
 * <p>
 * 各ルールに test:（想定入力 C++ コード）と assrt:（想定変換結果 C# コード）が 記載されている場合、変換結果が assrt
 * と一致することを検証する。 全ケースを走査した後にアサーションを行い、複数エラー時はすべて出力する。
 * </p>
 *
 * <p>
 * テスト結果を以下に出力する:
 * </p>
 * <ul>
 * <li>outputs/rules/{相対パス}.testresult.txt — 各ルールファイルの結果</li>
 * <li>outputs/rules/summary.txt — サマリー（テキスト）</li>
 * <li>outputs/rules/summary.html — サマリー（HTML）</li>
 * <li>outputs/rules/summary.xlsx — サマリー（Excel）</li>
 * </ul>
 */
@DisplayName("ルール内蔵テスト検証")
class RuleValidationTest {

	private static final Path OUTPUT_DIR = Paths.get(System.getProperty("user.dir")).resolve("outputs/rules");

	private static final String TABLE_HEADER = String.format("%-4s | %-60s | %6s | %6s | %6s%n", "No", "from", "tests",
			"pass", "error") + "-".repeat(96) + "\n";
	private static final String TABLE_ROW_FMT = "%-4d | %-60s | %6d | %6d | %6d%n";

	private CppToCSharpConverter converter;
	private ConversionRuleLoader loader;

	@BeforeEach
	void setUp() {
		// 各テストで 1 converter を使用。マルチスレッド化時は converter を共有しないこと。
		converter = new CppToCSharpConverter();
		loader = new ConversionRuleLoader(CppParserFactory.asLexerFactory());
	}

	@Test
	@DisplayName("全ルールの test/assrt が想定通りに変換される")
	void validateAllRuleTestCases() throws IOException {
		List<ConversionRuleLoader.RuleFileEntry> entries = loader.loadAllRuleFilesWithPaths();
		List<ConversionRule> allRules = entries.stream().flatMap(e -> e.getRules().stream())
				.collect(Collectors.toList());

		List<String> errors = new ArrayList<>();
		List<RuleFileTestResult> fileResults = new ArrayList<>();

		for (ConversionRuleLoader.RuleFileEntry entry : entries) {
			RuleFileTestResult fileResult = runRuleFileTests(entry, allRules);
			fileResults.add(fileResult);
			errors.addAll(fileResult.errors());
		}

		writeTestResultFiles(entries, fileResults);
		writeSummary(fileResults);

		assertThat(errors).as("ルール内蔵テストの不一致 (%d 件):%n%s", errors.size(), String.join(System.lineSeparator(), errors))
				.isEmpty();
	}

	private RuleFileTestResult runRuleFileTests(ConversionRuleLoader.RuleFileEntry entry,
			List<ConversionRule> allRules) {
		List<String> errors = new ArrayList<>();
		List<RuleSummary> ruleSummaries = new ArrayList<>();
		int passed = 0;
		int failed = 0;

		for (ConversionRule rule : entry.getRules()) {
			List<RuleTestCase> testCases = rule.getTestCases();
			int rulePassed = 0;
			int ruleFailed = 0;

			for (int i = 0; i < testCases.size(); i++) {
				RuleTestCase tc = testCases.get(i);
				ConversionResult result = converter.convertSource(tc.testInput(), allRules);
				String actual = result.getCsCode();
				if (normalizeForAssert(actual).equals(normalizeForAssert(tc.expectedOutput()))) {
					passed++;
					rulePassed++;
				} else {
					failed++;
					ruleFailed++;
					errors.add(String.format("[%s ペア #%d] from=%s%n  期待: %s%n  実際: %s", rule.getSourceFile(), i + 1,
							rule.getFromTokens(), tc.expectedOutput(), actual));
				}
			}

			ruleSummaries
					.add(new RuleSummary(String.join(" ", rule.getFromTokens().stream().map(Object::toString).toList()),
							testCases.size(), rulePassed, ruleFailed));
		}

		return new RuleFileTestResult(entry.getRelativePath(), passed, failed, errors, ruleSummaries);
	}

	private void writeTestResultFiles(List<ConversionRuleLoader.RuleFileEntry> entries,
			List<RuleFileTestResult> fileResults) throws IOException {
		for (int i = 0; i < entries.size(); i++) {
			ConversionRuleLoader.RuleFileEntry entry = entries.get(i);
			RuleFileTestResult result = fileResults.get(i);
			Path outPath = OUTPUT_DIR.resolve(entry.getRelativePath() + ".testresult.txt");
			Files.createDirectories(outPath.getParent());
			Files.writeString(outPath, buildTestResultContent(entry, result), StandardCharsets.UTF_8);
		}
	}

	private String buildTestResultContent(ConversionRuleLoader.RuleFileEntry entry, RuleFileTestResult result) {
		StringBuilder sb = new StringBuilder();
		sb.append("========================================\n");
		sb.append("  ルール内蔵テスト: ").append(entry.getRelativePath()).append("\n");
		sb.append("========================================\n\n");
		sb.append("--- 結果 ---\n");
		sb.append("成功: ").append(result.passed()).append(" 件\n");
		sb.append("失敗: ").append(result.failed()).append(" 件\n");
		sb.append("合計: ").append(result.passed() + result.failed()).append(" 件\n\n");
		sb.append("--- ルール別集計 ---\n");
		sb.append(TABLE_HEADER);
		for (int i = 0; i < result.ruleSummaries().size(); i++) {
			RuleSummary summary = result.ruleSummaries().get(i);
			sb.append(String.format(TABLE_ROW_FMT, i + 1, abbreviate(summary.fromPattern(), 60), summary.testCount(),
					summary.passedCount(), summary.errorCount()));
		}
		sb.append("\n");
		if (!result.errors().isEmpty()) {
			sb.append("--- 失敗詳細 ---\n");
			for (String err : result.errors()) {
				sb.append(err).append("\n\n");
			}
		}
		return sb.toString();
	}

	private void writeSummary(List<RuleFileTestResult> fileResults) throws IOException {
		Files.createDirectories(OUTPUT_DIR);

		int totalPassed = fileResults.stream().mapToInt(RuleFileTestResult::passed).sum();
		int totalFailed = fileResults.stream().mapToInt(RuleFileTestResult::failed).sum();

		String summaryTxt = buildSummaryText(fileResults, totalPassed, totalFailed);
		Files.writeString(OUTPUT_DIR.resolve("summary.txt"), summaryTxt, StandardCharsets.UTF_8);

		String summaryHtml = buildSummaryHtml(fileResults, totalPassed, totalFailed);
		Files.writeString(OUTPUT_DIR.resolve("summary.html"), summaryHtml, StandardCharsets.UTF_8);

	}

	private String buildSummaryText(List<RuleFileTestResult> results, int totalPassed, int totalFailed) {
		StringBuilder sb = new StringBuilder();
		sb.append("========================================\n");
		sb.append("  ルール内蔵テスト サマリー\n");
		sb.append("========================================\n\n");
		sb.append("--- 全体 ---\n");
		sb.append("成功: ").append(totalPassed).append(" 件\n");
		sb.append("失敗: ").append(totalFailed).append(" 件\n");
		sb.append("合計: ").append(totalPassed + totalFailed).append(" 件\n\n");
		sb.append("--- ファイル別 / ルール別 ---\n");
		for (RuleFileTestResult r : results) {
			String status = r.failed() > 0 ? "FAIL" : "PASS";
			sb.append(String.format("[%s] %s%n", status, r.relativePath()));
			sb.append(TABLE_HEADER);
			for (int i = 0; i < r.ruleSummaries().size(); i++) {
				RuleSummary summary = r.ruleSummaries().get(i);
				sb.append(String.format(TABLE_ROW_FMT, i + 1, abbreviate(summary.fromPattern(), 60),
						summary.testCount(), summary.passedCount(), summary.errorCount()));
			}
			sb.append("\n");
		}
		return sb.toString();
	}

	private String buildSummaryHtml(List<RuleFileTestResult> results, int totalPassed, int totalFailed) {
		StringBuilder sb = new StringBuilder();
		sb.append("<!DOCTYPE html>\n<html lang=\"ja\">\n<head>\n");
		sb.append("  <meta charset=\"UTF-8\">\n");
		sb.append("  <title>ルール内蔵テスト サマリー</title>\n");
		sb.append("  <style>\n");
		sb.append("    body { font-family: sans-serif; margin: 2em; }\n");
		sb.append("    table { border-collapse: collapse; }\n");
		sb.append("    th, td { border: 1px solid #ccc; padding: 0.5em 1em; text-align: left; }\n");
		sb.append("    .pass { color: green; }\n");
		sb.append("    .fail { color: red; }\n");
		sb.append("  </style>\n</head>\n<body>\n");
		sb.append("  <h1>ルール内蔵テスト サマリー</h1>\n");
		sb.append("  <p>成功: ").append(totalPassed).append(" 件 / 失敗: ").append(totalFailed).append(" 件 / 合計: ")
				.append(totalPassed + totalFailed).append(" 件</p>\n");
		sb.append("  <table>\n");
		sb.append(
				"    <tr><th>ファイル</th><th>状態</th><th>No</th><th>from</th><th>テスト数</th><th>合格数</th><th>エラー数</th></tr>\n");
		for (RuleFileTestResult r : results) {
			String statusClass = r.failed() > 0 ? "fail" : "pass";
			String status = r.failed() > 0 ? "FAIL" : "PASS";
			for (int i = 0; i < r.ruleSummaries().size(); i++) {
				RuleSummary summary = r.ruleSummaries().get(i);
				sb.append("    <tr><td>").append(escapeHtml(r.relativePath())).append("</td>");
				sb.append("<td class=\"").append(statusClass).append("\">").append(status).append("</td>");
				sb.append("<td>").append(i + 1).append("</td>");
				sb.append("<td>").append(escapeHtml(summary.fromPattern())).append("</td>");
				sb.append("<td>").append(summary.testCount()).append("</td>");
				sb.append("<td>").append(summary.passedCount()).append("</td>");
				sb.append("<td>").append(summary.errorCount()).append("</td></tr>\n");
			}
		}
		sb.append("  </table>\n");
		sb.append("</body>\n</html>\n");
		return sb.toString();
	}

	private static String abbreviate(String text, int maxLength) {
		if (text.length() <= maxLength) {
			return text;
		}
		return text.substring(0, Math.max(0, maxLength - 3)) + "...";
	}

	/**
	 * assrt 比較時は半角スペース差異を無視する。
	 */
	private static String normalizeForAssert(String text) {
		return text.replace(" ", "");
	}

	private static String escapeHtml(String s) {
		if (s == null)
			return "";
		return s.replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
				.replaceAll("&(?!amp;|lt;|gt;|quot;|#\\d+;)", "&amp;");
	}

	private record RuleSummary(String fromPattern, int testCount, int passedCount, int errorCount) {
	}

	private record RuleFileTestResult(String relativePath, int passed, int failed, List<String> errors,
			List<RuleSummary> ruleSummaries) {
	}
}
