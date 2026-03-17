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

package io.github.takahino.cpp2csharp.output;

import com.github.difflib.text.DiffRow;
import com.github.difflib.text.DiffRowGenerator;

import io.github.takahino.cpp2csharp.converter.ConversionResult;
import io.github.takahino.cpp2csharp.transform.DiagnosticCandidate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 変換前後のソースコードを diff 表示した HTML レポートを生成するクラス。
 *
 * <p>
 * java-diff-utils を使用して行単位・インラインの差分を可視化する。
 * </p>
 */
public final class DiffReportGenerator {

	private DiffReportGenerator() {
	}

	/**
	 * 変換前後のファイルから diff HTML レポートを生成し、指定パスに保存する。
	 *
	 * @param inputPath
	 *            変換元 C++ ファイル
	 * @param outputPath
	 *            変換後 C# ファイル
	 * @param reportPath
	 *            レポートテキスト（統計情報の表示用、null 可）
	 * @param htmlPath
	 *            出力先 HTML ファイル
	 * @param result
	 *            変換結果（診断候補の表示用、null 可）
	 * @throws IOException
	 *             ファイル読み込み・書き込みに失敗した場合
	 */
	public static void generate(Path inputPath, Path outputPath, Path reportPath, Path htmlPath,
			ConversionResult result) throws IOException {
		String original = Files.readString(inputPath, StandardCharsets.UTF_8);
		String revised = Files.readString(outputPath, StandardCharsets.UTF_8);
		String reportText = reportPath != null && Files.exists(reportPath)
				? Files.readString(reportPath, StandardCharsets.UTF_8)
				: null;
		List<DiagnosticCandidate> diagnosticCandidates = result != null ? result.getDiagnosticCandidates() : List.of();

		String html = buildDiffHtml(inputPath.getFileName().toString(), outputPath.getFileName().toString(), original,
				revised, reportText, diagnosticCandidates);
		Files.writeString(htmlPath, html, StandardCharsets.UTF_8);
	}

	/**
	 * 後方互換のため、result なしで呼び出せるオーバーロード。
	 */
	public static void generate(Path inputPath, Path outputPath, Path reportPath, Path htmlPath) throws IOException {
		generate(inputPath, outputPath, reportPath, htmlPath, null);
	}

	private static String buildDiffHtml(String inputName, String outputName, String original, String revised,
			String reportText, List<DiagnosticCandidate> diagnosticCandidates) {
		List<String> originalLines = original.lines().collect(Collectors.toList());
		List<String> revisedLines = revised.lines().collect(Collectors.toList());

		Map<Integer, List<DiagnosticCandidate>> candidatesByLine = diagnosticCandidates.stream()
				.filter(c -> c.lineNumber() > 0).collect(Collectors.groupingBy(DiagnosticCandidate::lineNumber));

		DiffRowGenerator generator = DiffRowGenerator.create().showInlineDiffs(true).inlineDiffByWord(true)
				.oldTag(f -> "~").newTag(f -> "**").build();

		List<DiffRow> rows = generator.generateDiffRows(originalLines, revisedLines);

		boolean hasDiagnostics = !diagnosticCandidates.isEmpty();
		int oldLineNum = 1;
		int newLineNum = 1;

		StringBuilder sb = new StringBuilder();
		sb.append("<!DOCTYPE html>\n");
		sb.append("<html lang=\"ja\">\n<head>\n");
		sb.append("  <meta charset=\"UTF-8\">\n");
		sb.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
		sb.append("  <title>変換 diff レポート</title>\n");
		sb.append("  <style>\n");
		sb.append(css());
		sb.append("  </style>\n</head>\n<body>\n");

		sb.append("  <header>\n");
		sb.append("    <h1>変換 diff レポート</h1>\n");
		sb.append("    <p class=\"file-info\">").append(escapeHtml(inputName)).append(" → ")
				.append(escapeHtml(outputName)).append("</p>\n");
		if (reportText != null) {
			sb.append("    <pre class=\"report-summary\">").append(escapeHtml(reportText)).append("</pre>\n");
		}
		sb.append("  </header>\n");

		sb.append("  <div class=\"diff-container\">\n");
		sb.append("    <table class=\"diff-table\">\n");
		sb.append("      <thead><tr><th>#</th><th>C++ (変換前)</th><th>C# (変換後)");
		if (hasDiagnostics) {
			sb.append("</th><th>診断レポート</th>");
		}
		sb.append("</tr></thead>\n");
		sb.append("      <tbody>\n");

		int rowNum = 1;
		for (DiffRow row : rows) {
			String rowClass = switch (row.getTag()) {
				case INSERT -> "diff-add";
				case DELETE -> "diff-remove";
				case CHANGE -> "diff-change";
				default -> "";
			};
			sb.append("        <tr class=\"").append(rowClass).append("\">\n");
			sb.append("          <td class=\"line-num\">").append(rowNum).append("</td>\n");
			sb.append("          <td class=\"old-line\"><code>").append(toHtmlWithSpans(row.getOldLine()))
					.append("</code></td>\n");
			sb.append("          <td class=\"new-line\"><code>").append(toHtmlWithSpans(row.getNewLine()))
					.append("</code></td>\n");
			if (hasDiagnostics) {
				List<DiagnosticCandidate> forLine = candidatesByLine.getOrDefault(oldLineNum, List.of());
				sb.append("          <td class=\"diagnostic-cell\">");
				if (forLine.isEmpty()) {
					sb.append("&nbsp;");
				} else {
					sb.append("<div class=\"diagnostic-entry\">");
					int idx = 1;
					for (DiagnosticCandidate c : forLine) {
						sb.append("<div class=\"diagnostic-item\">");
						sb.append("#").append(idx++).append(" [").append(escapeHtml(c.reasonCategory()))
								.append("]<br>");
						sb.append("行: ").append(c.lineNumber()).append(" | ");
						sb.append(escapeHtml(c.ruleSource())).append("<br>");
						sb.append("マッチ: ").append(escapeHtml(c.matchedText()));
						sb.append("</div>");
					}
					sb.append("</div>");
				}
				sb.append("</td>\n");
			}
			sb.append("        </tr>\n");

			switch (row.getTag()) {
				case INSERT -> newLineNum++;
				case DELETE -> oldLineNum++;
				default -> {
					oldLineNum++;
					newLineNum++;
				}
			}
			rowNum++;
		}

		sb.append("      </tbody>\n");
		sb.append("    </table>\n");
		sb.append("  </div>\n");
		sb.append("</body>\n</html>");
		return sb.toString();
	}

	private static final Pattern TILDE_PATTERN = Pattern.compile("~([^~]*)~");
	private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.*?)\\*\\*");

	private static String toHtmlWithSpans(String line) {
		if (line == null)
			return "";
		String escaped = escapeHtml(line);
		escaped = TILDE_PATTERN.matcher(escaped).replaceAll("<span class=\"diff-del\">$1</span>");
		escaped = BOLD_PATTERN.matcher(escaped).replaceAll("<span class=\"diff-ins\">$1</span>");
		return escaped;
	}

	private static String escapeHtml(String s) {
		if (s == null)
			return "";
		return s.replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
				.replaceAll("&(?!amp;|lt;|gt;|quot;|#\\d+;)", "&amp;");
	}

	private static String css() {
		return """
				* { box-sizing: border-box; }
				body { font-family: 'Consolas', 'Monaco', monospace; margin: 1rem; background: #1e1e1e; color: #d4d4d4; }
				header { margin-bottom: 1.5rem; }
				h1 { font-size: 1.5rem; margin: 0 0 0.5rem 0; }
				.file-info { color: #9cdcfe; margin: 0; }
				.report-summary { background: #252526; padding: 1rem; border-radius: 4px; font-size: 0.85rem; overflow-x: auto; white-space: pre-wrap; margin-top: 0.5rem; }
				.diff-container { overflow-x: auto; border: 1px solid #3c3c3c; border-radius: 4px; }
				.diff-table { width: 100%; border-collapse: collapse; font-size: 0.9rem; }
				.diff-table th { background: #2d2d30; padding: 0.5rem 1rem; text-align: left; font-weight: 600; }
				.diff-table td { padding: 0.25rem 1rem; vertical-align: top; border-top: 1px solid #3c3c3c; }
				.line-num { color: #858585; width: 3em; text-align: right; user-select: none; }
				.old-line, .new-line { white-space: pre-wrap; word-break: break-all; }
				.diff-del { background: rgba(255, 100, 100, 0.3); text-decoration: line-through; }
				.diff-ins { background: rgba(100, 255, 100, 0.3); }
				tr.diff-add .old-line { background: #2d2020; }
				tr.diff-add .new-line { background: #1e2d1e; }
				tr.diff-remove .old-line { background: #2d2020; }
				tr.diff-remove .new-line { background: #1e2d1e; }
				tr.diff-change .old-line { background: #2d2020; }
				tr.diff-change .new-line { background: #1e2d1e; }
				.diagnostic-cell { font-size: 0.8rem; max-width: 20em; }
				.diagnostic-entry { display: flex; flex-direction: column; gap: 0.25rem; }
				.diagnostic-item { background: #252526; padding: 0.25rem 0.5rem; border-radius: 2px; }
				""";
	}
}
