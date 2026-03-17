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

import io.github.takahino.cpp2csharp.converter.ConversionResult;
import io.github.takahino.cpp2csharp.converter.FunctionUnitEntry;
import io.github.takahino.cpp2csharp.converter.PhaseSnapshot;
import io.github.takahino.cpp2csharp.converter.PhaseTransformLog;
import io.github.takahino.cpp2csharp.rule.ConversionRule;
import io.github.takahino.cpp2csharp.transform.AppliedTransform;
import io.github.takahino.cpp2csharp.transform.DiagnosticCandidate;
import io.github.takahino.cpp2csharp.transform.TokenSnapshot;
import io.github.takahino.cpp2csharp.transform.Transformer.TransformError;

import com.github.difflib.text.DiffRow;
import com.github.difflib.text.DiffRowGenerator;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Date;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 変換結果を入力ファイルと同じディレクトリに書き出すユーティリティクラス。
 *
 * <h2>出力内容</h2>
 * <ul>
 * <li>{@code <入力と同じパス、拡張子 .cs>} — 変換後 C# コード</li>
 * <li>{@code <basename>.report.txt} — 変換サマリーレポート</li>
 * <li>{@code <basename>.report.html} — 変換前後の diff を可視化した HTML レポート</li>
 * <li>{@code <basename>.treedump.txt} — AST 木の文字列ダンプ（ルール設計デバッグ用）</li>
 * <li>{@code <basename>.xlsx>} — 変換過程の可視化（Excel 有効時）</li>
 * </ul>
 */
public class ConversionOutputWriter {

	private static final Pattern STEP_PATTERN = Pattern.compile("^---STEP (\\d+)---$");

	private final ExcelOutputConfig excelConfig;
	private final String outputExtension;
	private final Pattern basenamePattern;

	/**
	 * コンストラクタ。デフォルト設定（Excel 有効、出力拡張子 .cs）で初期化する。
	 */
	public ConversionOutputWriter() {
		this(ExcelOutputConfig.defaultConfig(), ".cs");
	}

	/**
	 * コンストラクタ。Excel 出力設定を指定可能。出力拡張子はデフォルト .cs。
	 *
	 * @param excelConfig
	 *            Excel 出力の設定
	 */
	public ConversionOutputWriter(ExcelOutputConfig excelConfig) {
		this(excelConfig, ".cs");
	}

	/**
	 * コンストラクタ。Excel 出力設定と出力拡張子を指定可能。
	 *
	 * @param excelConfig
	 *            Excel 出力の設定
	 * @param outputExtension
	 *            出力ファイルの拡張子（例: ".cs"）
	 */
	public ConversionOutputWriter(ExcelOutputConfig excelConfig, String outputExtension) {
		this.excelConfig = excelConfig != null ? excelConfig : ExcelOutputConfig.defaultConfig();
		this.outputExtension = outputExtension != null ? outputExtension : ".cs";
		this.basenamePattern = Pattern.compile(Pattern.quote(this.outputExtension) + "$");
	}

	/**
	 * 変換結果をファイルに書き出す。
	 *
	 * @param inputPath
	 *            入力 C++ ファイルパス
	 * @param outputPath
	 *            出力 C# ファイルパス（入力と同じディレクトリ、拡張子 .cs）
	 * @param cppSource
	 *            変換元 C++ ソース文字列
	 * @param result
	 *            変換結果
	 * @throws IOException
	 *             ファイル書き込みに失敗した場合
	 */
	public void write(Path inputPath, Path outputPath, String cppSource, ConversionResult result) throws IOException {
		write(inputPath, outputPath, cppSource, result, List.of());
	}

	/**
	 * A2 ルール有効度統計付き: 変換結果をファイルに書き出す。
	 *
	 * <p>
	 * <strong>なぜ追加したか</strong>: ルールが 5000+ に増えた際に「一度も発火しないルール（死んだルール）」
	 * を早期に検出するため。{@code allRules} を受け取ってレポートに差分を出力することで、
	 * ルールセット品質管理を自動化する。シグネチャへの侵襲を最小化するため既存の4引数版は後方互換として残す。
	 * </p>
	 *
	 * @param inputPath
	 *            入力 C++ ファイルパス
	 * @param outputPath
	 *            出力 C# ファイルパス（入力と同じディレクトリ、拡張子 .cs）
	 * @param cppSource
	 *            変換元 C++ ソース文字列
	 * @param result
	 *            変換結果
	 * @param allRules
	 *            全変換ルール（有効度統計用。空リストの場合は統計なし）
	 * @throws IOException
	 *             ファイル書き込みに失敗した場合
	 */
	public void write(Path inputPath, Path outputPath, String cppSource, ConversionResult result,
			List<ConversionRule> allRules) throws IOException {
		Files.createDirectories(outputPath.getParent());

		// 変換後 C# コードを保存
		Files.writeString(outputPath, result.getCsCode(), StandardCharsets.UTF_8);

		// 変換レポートを保存（入力と同じディレクトリ）
		String basename = basenamePattern.matcher(outputPath.getFileName().toString()).replaceFirst("");
		Path reportPath = outputPath.getParent().resolve(basename + ".report.txt");
		Files.writeString(reportPath, buildReport(basename, cppSource, result, allRules), StandardCharsets.UTF_8);

		// diff HTML レポートを保存
		Path htmlPath = outputPath.getParent().resolve(basename + ".report.html");
		DiffReportGenerator.generate(inputPath, outputPath, reportPath, htmlPath, result);

		// フェーズ変換ジャーニー HTML を保存
		writePhaseJourneyHtml(outputPath.resolveSibling(basename + ".phases.html"), result);

		// 木ダンプを保存（ルール設計デバッグ用）
		String treeDump = result.getInitialTreeDump();
		if (treeDump != null) {
			Path treedumpPath = outputPath.getParent().resolve(basename + ".treedump.txt");
			Files.writeString(treedumpPath, treeDump, StandardCharsets.UTF_8);
		}

		// _units/ ディレクトリにユニット別ファイルを出力
		// .cpp.txt = MAIN 入力（PRE後）、.cs.txt = MAIN 出力。N 番号共通で diff 可能
		List<String> unitDumps = result.getUnitSourceDumps();
		List<String> unitOutputDumps = result.getUnitOutputDumps();
		if (!unitDumps.isEmpty() || !unitOutputDumps.isEmpty()) {
			Path unitsDir = outputPath.getParent().resolve("_units");
			Files.createDirectories(unitsDir);
			for (int i = 0; i < unitDumps.size(); i++) {
				Files.writeString(unitsDir.resolve(basename + "_" + (i + 1) + ".cpp.txt"), unitDumps.get(i),
						StandardCharsets.UTF_8);
			}
			for (int i = 0; i < unitOutputDumps.size(); i++) {
				Files.writeString(unitsDir.resolve(basename + "_" + (i + 1) + outputExtension + ".txt"),
						unitOutputDumps.get(i), StandardCharsets.UTF_8);
			}
		}

		// 関数単位 JSON を出力（basename.json）
		List<FunctionUnitEntry> entries = result.getFunctionUnitEntries();
		if (!entries.isEmpty()) {
			Path jsonPath = outputPath.getParent().resolve(basename + ".json");
			Files.writeString(jsonPath, buildFunctionUnitJson(entries), StandardCharsets.UTF_8);
		}

		// 変換過程 Excel 可視化を保存
		if (excelConfig.isEnabled() && result.getVisualizationTempFile() != null) {
			Path xlsxPath = outputPath.getParent().resolve(basename + ".xlsx");
			try {
				writeVisualizationExcel(result.getVisualizationTempFile(), result.getAppliedTransforms(), xlsxPath);
			} finally {
				try {
					Files.deleteIfExists(result.getVisualizationTempFile());
				} catch (IOException e) {
					// 一時ファイル削除失敗は無視
				}
			}
		}
	}

	/**
	 * 一時ファイルから変換過程を読み込み、Excel に出力する。
	 */
	private void writeVisualizationExcel(Path tempFile, List<AppliedTransform> appliedTransforms, Path xlsxPath)
			throws IOException {
		List<List<TokenSnapshot>> steps = parseVisualizationTempFile(tempFile);
		if (steps.isEmpty()) {
			return;
		}

		int initialRowCount = steps.get(0).size();

		try (XSSFWorkbook workbook = new XSSFWorkbook()) {
			// タイムスタンプを固定してバイナリ再現性を確保（git diff 安定化）
			Optional<Date> epoch = Optional.of(new Date(0L));
			workbook.getProperties().getCoreProperties().setCreated(epoch);
			workbook.getProperties().getCoreProperties().setModified(epoch);

			Sheet sheet = workbook.createSheet("変換過程");
			CellStyle changedStyle = workbook.createCellStyle();
			changedStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.LIGHT_YELLOW.getIndex());
			changedStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

			// ヘッダー行
			Row headerRow = sheet.createRow(0);
			headerRow.createCell(0).setCellValue("No");
			for (int col = 0; col < steps.size(); col++) {
				headerRow.createCell(col + 1).setCellValue(col == 0 ? "初期" : "変換" + col + "後");
			}

			// 各ステップの行アライン済みトークン配列を事前計算
			TokenSnapshot[][] alignedSteps = new TokenSnapshot[steps.size()][];
			for (int stepIdx = 0; stepIdx < steps.size(); stepIdx++) {
				alignedSteps[stepIdx] = expandToRowAligned(steps.get(stepIdx), stepIdx, appliedTransforms,
						initialRowCount);
			}
			CreationHelper createHelper = workbook.getCreationHelper();
			var drawingPatriarch = sheet.createDrawingPatriarch();

			// データ行（行位置固定で展開）
			for (int rowIdx = 0; rowIdx < initialRowCount; rowIdx++) {
				Row row = sheet.createRow(rowIdx + 1);
				row.createCell(0).setCellValue(rowIdx + 1);

				for (int stepIdx = 0; stepIdx < steps.size(); stepIdx++) {
					TokenSnapshot[] rowAligned = alignedSteps[stepIdx];
					Cell cell = row.createCell(stepIdx + 1);
					boolean isChanged = false;
					String commentRule = null;
					String mergedIdsStr = null;

					if (rowAligned[rowIdx] != null) {
						cell.setCellValue(rowAligned[rowIdx].id() + ": " + rowAligned[rowIdx].text());
						if (stepIdx > 0 && appliedTransforms.size() >= stepIdx) {
							AppliedTransform t = appliedTransforms.get(stepIdx - 1);
							if (t.startIndex() >= 0 && t.endIndex() >= 0) {
								if (rowIdx == t.startIndex() || (rowIdx > t.startIndex() && rowIdx < t.endIndex())) {
									isChanged = true;
									commentRule = t.ruleSource() + ": " + t.ruleFrom() + " -> " + t.ruleTo();
									if (t.mergedIds() != null && !t.mergedIds().isEmpty()) {
										mergedIdsStr = "id " + t.mergedIds().stream().map(String::valueOf)
												.collect(Collectors.joining(",")) + " -> " + t.mergedIds().get(0);
									}
								}
							}
						}
					}

					if (isChanged) {
						cell.setCellStyle(changedStyle);
					}
					if (commentRule != null || mergedIdsStr != null) {
						ClientAnchor anchor = createHelper.createClientAnchor();
						anchor.setCol1(cell.getColumnIndex());
						anchor.setRow1(cell.getRowIndex());
						Comment comment = drawingPatriarch.createCellComment(anchor);
						StringBuilder commentText = new StringBuilder();
						if (commentRule != null)
							commentText.append(commentRule);
						if (mergedIdsStr != null) {
							if (!commentText.isEmpty())
								commentText.append("\n");
							commentText.append(mergedIdsStr);
						}
						comment.setString(createHelper.createRichTextString(commentText.toString()));
						cell.setCellComment(comment);
					}
				}
			}

			for (int i = 0; i <= steps.size(); i++) {
				sheet.autoSizeColumn(i);
			}

			// zip エントリのタイムスタンプも固定してバイナリ再現性を確保
			ByteArrayOutputStream buf = new ByteArrayOutputStream();
			workbook.write(buf);
			try (var os = Files.newOutputStream(xlsxPath);
					ZipOutputStream zout = new ZipOutputStream(os);
					ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(buf.toByteArray()))) {
				ZipEntry entry;
				while ((entry = zin.getNextEntry()) != null) {
					ZipEntry fixed = new ZipEntry(entry.getName());
					fixed.setTime(0L);
					zout.putNextEntry(fixed);
					zin.transferTo(zout);
					zout.closeEntry();
				}
			}
		}
	}

	private static TokenSnapshot[] simpleCopy(List<TokenSnapshot> tokens, int initialRowCount) {
		TokenSnapshot[] result = new TokenSnapshot[initialRowCount];
		for (int i = 0; i < tokens.size() && i < initialRowCount; i++) {
			result[i] = tokens.get(i);
		}
		return result;
	}

	private TokenSnapshot[] expandToRowAligned(List<TokenSnapshot> tokens, int stepIndex,
			List<AppliedTransform> appliedTransforms, int initialRowCount) {
		if (stepIndex == 0 || stepIndex > appliedTransforms.size()) {
			return simpleCopy(tokens, initialRowCount);
		}

		AppliedTransform t = appliedTransforms.get(stepIndex - 1);
		int start = t.startIndex();
		int end = t.endIndex();
		if (start < 0 || end < 0 || start >= initialRowCount || end > initialRowCount) {
			return simpleCopy(tokens, initialRowCount);
		}

		TokenSnapshot[] result = new TokenSnapshot[initialRowCount];
		for (int i = 0; i < start && i < tokens.size(); i++) {
			result[i] = tokens.get(i);
		}
		if (start < tokens.size()) {
			result[start] = tokens.get(start);
		}
		for (int i = start + 1; i < end; i++) {
			result[i] = null;
		}
		for (int i = end; i < initialRowCount; i++) {
			int srcIdx = start + 1 + (i - end);
			if (srcIdx < tokens.size()) {
				result[i] = tokens.get(srcIdx);
			}
		}
		return result;
	}

	private List<List<TokenSnapshot>> parseVisualizationTempFile(Path tempFile) throws IOException {
		List<List<TokenSnapshot>> steps = new ArrayList<>();
		try (var reader = new BufferedReader(
				new InputStreamReader(Files.newInputStream(tempFile), StandardCharsets.UTF_8))) {
			List<TokenSnapshot> currentStep = null;
			String line;
			while ((line = reader.readLine()) != null) {
				Matcher m = STEP_PATTERN.matcher(line.trim());
				if (m.matches()) {
					currentStep = new ArrayList<>();
					steps.add(currentStep);
				} else if (currentStep != null && line.contains("\t")) {
					int tabIdx = line.indexOf('\t');
					int id = Integer.parseInt(line.substring(0, tabIdx));
					String text = unescapeForVisualization(line.substring(tabIdx + 1));
					currentStep.add(new TokenSnapshot(id, text));
				}
			}
		}
		return steps;
	}

	private static String unescapeForVisualization(String s) {
		if (s == null || s.isEmpty())
			return "";
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\\' && i + 1 < s.length()) {
				char next = s.charAt(i + 1);
				switch (next) {
					case '\\' -> sb.append('\\');
					case 't' -> sb.append('\t');
					case 'n' -> sb.append('\n');
					case 'r' -> sb.append('\r');
					default -> sb.append(next);
				}
				i++;
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	/**
	 * 変換サマリーレポートを生成する（ルール有効度統計付き）。
	 *
	 * @param allRules
	 *            全変換ルール（空リストの場合は統計なし）
	 */
	private String buildReport(String basename, String cppSource, ConversionResult result,
			List<ConversionRule> allRules) {
		StringBuilder sb = new StringBuilder();
		sb.append("========================================\n");
		sb.append("  変換レポート: ").append(basename).append("\n");
		sb.append("========================================\n\n");

		sb.append("--- 統計 ---\n");
		sb.append("入力行数    : ").append(cppSource.lines().count()).append(" 行\n");
		sb.append("出力トークン数: ").append(result.getCsCode().split("\\s+").length).append(" トークン\n");
		sb.append("パースエラー  : ").append(result.getParseErrors().size()).append(" 件\n");
		sb.append("変換エラー   : ").append(result.getTransformErrors().size()).append(" 件\n");
		long stillMatchable = result.getDiagnosticCandidates().stream()
				.filter(c -> "still_matchable_after_all_phases".equals(c.reasonCategory())).count();
		long nearMissCount = result.getDiagnosticCandidates().stream()
				.filter(c -> "near_miss".equals(c.reasonCategory())).count();
		sb.append("診断候補数   : ").append(stillMatchable).append(" 件");
		if (nearMissCount > 0) {
			sb.append(" (ニアミス: ").append(nearMissCount).append(" 件)");
		}
		sb.append("\n");
		sb.append("変換成功    : ").append(result.isSuccess() ? "YES" : "NO").append("\n\n");

		if (!result.getParseErrors().isEmpty()) {
			sb.append("--- パースエラー ---\n");
			for (String err : result.getParseErrors()) {
				sb.append("  [PARSE] ").append(err).append("\n");
			}
			sb.append("\n");
		}

		if (!result.getTransformErrors().isEmpty()) {
			sb.append("--- 変換エラー (曖昧マッチ) ---\n");
			for (TransformError err : result.getTransformErrors()) {
				sb.append("  [").append(err.getErrorType()).append("] ").append(err.getMessage()).append("\n");
				for (var match : err.getRelatedMatches()) {
					sb.append("    ルール: ").append(match.getRule().getToTemplate()).append("\n");
				}
			}
			sb.append("\n");
		}

		if (!result.getPhaseTransformLogs().isEmpty()) {
			String phaseNames = result.getPhaseTransformLogs().stream().map(PhaseTransformLog::phase).distinct()
					.collect(Collectors.joining("/"));
			sb.append("--- ").append(phaseNames).append(" フェーズ適用ログ ---\n");
			int seq = 1;
			for (PhaseTransformLog log : result.getPhaseTransformLogs()) {
				sb.append("  #").append(seq++).append(" [").append(log.phase()).append(" フェーズ").append(log.phaseIndex())
						.append("]\n");
				sb.append("    ルール   : ").append(log.ruleSource()).append("\n");
				sb.append("    from    : ").append(truncate(log.ruleFrom(), 80)).append("\n");
				sb.append("    to      : ").append(truncate(log.ruleTo(), 80)).append("\n");
				sb.append("    変換前   : ").append(truncate(log.matchedText(), 80)).append("\n");
				sb.append("    変換後   : ").append(truncate(log.replacedWith(), 80)).append("\n");
			}
			sb.append("\n");
		}

		if (!result.getAppliedTransforms().isEmpty()) {
			sb.append("--- 変換適用ログ (時系列) ---\n");
			for (var t : result.getAppliedTransforms()) {
				sb.append("  #").append(t.sequence()).append(" [フェーズ").append(t.phaseIndex() + 1).append("]\n");
				sb.append("    ルール   : ").append(t.ruleSource()).append("\n");
				sb.append("    from    : ").append(t.ruleFrom()).append("\n");
				sb.append("    to      : ").append(t.ruleTo()).append("\n");
				sb.append("    変換前ノード: ").append(t.matchedNode()).append("\n");
				sb.append("    変換後ノード: ").append(t.transformedTo()).append("\n");
				if (t.selectedStrategy() != null && !t.selectedStrategy().isEmpty()) {
					sb.append("    選択戦略: ").append(t.selectedStrategy()).append("\n");
					sb.append("    フォールバック: ").append(t.fallbackFrom() != null ? t.fallbackFrom() + " から" : "なし")
							.append("\n");
					if (t.selectionReason() != null && !t.selectionReason().isEmpty()) {
						sb.append("    選択理由: ").append(t.selectionReason()).append("\n");
					}
					if (t.selectionDetails() != null && !t.selectionDetails().isEmpty()) {
						for (String line : t.selectionDetails().split("\n")) {
							sb.append("    ").append(line).append("\n");
						}
					}
				}
				if (t.sourceLineNumber() > 0) {
					if (!t.lineBefore().isEmpty() || !t.lineAfter().isEmpty()) {
						sb.append("    行").append(t.sourceLineNumber()).append(" 置き換え前: ").append(t.lineBefore())
								.append("\n");
						sb.append("    行").append(t.sourceLineNumber()).append(" 置き換え後: ").append(t.lineAfter())
								.append("\n");
					} else {
						String lineContent = getLineAt(cppSource, t.sourceLineNumber());
						sb.append("    行").append(t.sourceLineNumber()).append("    : ").append(lineContent)
								.append("\n");
					}
				}
			}
			sb.append("\n");
		}

		List<DiagnosticCandidate> stillMatchableCandidates = result.getDiagnosticCandidates().stream()
				.filter(c -> "still_matchable_after_all_phases".equals(c.reasonCategory())).toList();
		if (!stillMatchableCandidates.isEmpty()) {
			sb.append("--- 診断候補 ---\n");
			int idx = 1;
			for (DiagnosticCandidate c : stillMatchableCandidates) {
				sb.append("  #").append(idx++).append(" [").append(c.reasonCategory()).append("]\n");
				sb.append("    行      : ").append(c.lineNumber()).append("\n");
				sb.append("    ルール   : ").append(c.ruleSource()).append("\n");
				sb.append("    from    : ").append(c.ruleFrom()).append("\n");
				sb.append("    想定to   : ").append(c.expandedTo()).append("\n");
				sb.append("    マッチ   : ").append(c.matchedText()).append("\n");
				if (c.lineContent() != null && !c.lineContent().isEmpty()) {
					sb.append("    行内容   : ").append(c.lineContent()).append("\n");
				}
			}
			sb.append("\n");
		}

		// A1: ニアミス候補 — ルール修正コスト削減のため「あと1トークンで一致するルール」を可視化する
		List<DiagnosticCandidate> nearMissCandidates = result.getDiagnosticCandidates().stream()
				.filter(c -> "near_miss".equals(c.reasonCategory())).toList();
		if (!nearMissCandidates.isEmpty()) {
			sb.append("--- ニアミス候補 (あと1トークンで一致するルール) ---\n");
			int idx = 1;
			for (DiagnosticCandidate c : nearMissCandidates) {
				sb.append("  #").append(idx++).append(" 行").append(c.lineNumber()).append("\n");
				sb.append("    ルール   : ").append(c.ruleSource()).append("\n");
				sb.append("    from    : ").append(c.ruleFrom()).append("\n");
				if (c.mismatchPatternIndex() != null) {
					sb.append("    不一致位置: パターントークン [").append(c.mismatchPatternIndex()).append("]\n");
				}
				if (c.expectedToken() != null && !c.expectedToken().isEmpty()) {
					sb.append("    期待トークン: ").append(c.expectedToken()).append("\n");
				}
			}
			sb.append("\n");
		}

		// A2: ルール有効度統計 — ルール5000+増加前に「死んだルール」を早期検出するための品質管理セクション
		if (!allRules.isEmpty()) {
			List<ConversionRule> deadRules = computeDeadRules(allRules, result.getAppliedTransforms().stream());
			sb.append("--- ルール有効度統計 ---\n");
			sb.append("  総ルール数    : ").append(allRules.size()).append(" 件\n");
			sb.append("  発火ルール数  : ").append(allRules.size() - deadRules.size()).append(" 件\n");
			sb.append("  未使用ルール数: ").append(deadRules.size()).append(" 件\n");
			if (!deadRules.isEmpty()) {
				sb.append("  未使用ルール一覧:\n");
				for (ConversionRule r : deadRules) {
					sb.append("    - [").append(r.getSourceFile()).append("] ")
							.append(r.getFromTokens().stream().map(t -> t.getValue()).collect(Collectors.joining(" ")))
							.append("\n");
				}
			}
			sb.append("\n");
		}

		sb.append("--- 変換後 C# コード ---\n");
		sb.append(result.getCsCode()).append("\n");

		return sb.toString();
	}

	/**
	 * 文字列を指定の最大長で切り詰める。
	 *
	 * @param s
	 *            入力文字列（null 可）
	 * @param max
	 *            最大文字数
	 * @return 切り詰め後の文字列
	 */
	private static String truncate(String s, int max) {
		if (s == null)
			return "";
		if (s.length() <= max)
			return s;
		return s.substring(0, max - 3) + "...";
	}

	/**
	 * 指定された変換ログストリームで一度も発火しなかったルールを返す。
	 */
	private static List<ConversionRule> computeDeadRules(List<ConversionRule> allRules,
			Stream<AppliedTransform> transforms) {
		Set<String> firedKeys = transforms.map(t -> t.ruleSource() + "|" + t.ruleFrom()).collect(Collectors.toSet());
		return allRules.stream().filter(r -> !firedKeys.contains(ruleKey(r))).toList();
	}

	/**
	 * A2 ルール有効度統計用のルールキーを生成する。
	 *
	 * <p>
	 * <strong>なぜ追加したか</strong>: {@link AppliedTransform} に記録された発火ログのキー
	 * ({@code ruleSource + "|" + ruleFrom}) と同じ形式でルールを識別するため。
	 * このキーで全ルールと発火済みルールの差分を取ることで「未使用ルール」を特定する。
	 * </p>
	 */
	private static String ruleKey(ConversionRule r) {
		return r.getSourceFile() + "|"
				+ r.getFromTokens().stream().map(t -> t.getValue()).collect(Collectors.joining(" "));
	}

	/**
	 * バッチ変換全体のルール有効度統計セクションを構築する。
	 *
	 * <p>
	 * 全ファイルの {@link ConversionResult#getAppliedTransforms()} を集計し、
	 * 一度も発火しなかったルール（未使用ルール）を列挙する。 {@code allRules} または {@code allResults}
	 * が空の場合は空文字列を返す。
	 * </p>
	 *
	 * @param allResults
	 *            バッチ変換で得られた全ファイルの変換結果
	 * @param allRules
	 *            全変換ルール（有効度統計用）
	 * @return 統計セクション文字列（{@code batch_conversion_summary.txt} に追記用）
	 */
	public static String buildBatchRuleEffectivenessSection(List<ConversionResult> allResults,
			List<ConversionRule> allRules) {
		if (allRules.isEmpty() || allResults.isEmpty())
			return "";

		List<ConversionRule> deadRules = computeDeadRules(allRules,
				allResults.stream().flatMap(r -> r.getAppliedTransforms().stream()));

		StringBuilder sb = new StringBuilder();
		sb.append("--- ルール有効度統計（全ファイル集計）---\n");
		sb.append("  総ルール数    : ").append(allRules.size()).append(" 件\n");
		sb.append("  発火ルール数  : ").append(allRules.size() - deadRules.size()).append(" 件\n");
		sb.append("  未使用ルール数: ").append(deadRules.size()).append(" 件\n");
		if (!deadRules.isEmpty()) {
			sb.append("  未使用ルール一覧:\n");
			for (ConversionRule r : deadRules) {
				sb.append("    - [").append(r.getSourceFile()).append("] ")
						.append(r.getFromTokens().stream().map(t -> t.getValue()).collect(Collectors.joining(" ")))
						.append("\n");
			}
		}
		sb.append("\n");
		return sb.toString();
	}

	/** grep 用のユニークマーカー（診断コメントの一括検索用） */
	public static final String DIAG_COMMENT_MARKER = "DIAG:";

	/**
	 * 診断候補を変換後 C# ソースに行コメントで埋め込む。
	 *
	 * <p>
	 * 書式は {@code // DIAG: ルール名:from:to}。複数ある場合は同じ書式で繰り返す。
	 * {@link #DIAG_COMMENT_MARKER} で grep すると診断コメントのみ一括検索できる。
	 * </p>
	 * <p>
	 * 行末に追記するため、既存の {@code //} コメントがあってもその後に追加されるだけで影響しない。
	 * </p>
	 *
	 * @param csCode
	 *            変換後 C# コード
	 * @param candidates
	 *            診断候補リスト
	 * @return コメント埋め込み後の C# コード
	 */
	private static String embedDiagnosticComments(String csCode, List<DiagnosticCandidate> candidates) {
		if (candidates == null || candidates.isEmpty()) {
			return csCode;
		}
		Map<Integer, List<DiagnosticCandidate>> byLine = candidates.stream().filter(c -> c.lineNumber() > 0)
				.collect(Collectors.groupingBy(DiagnosticCandidate::lineNumber));

		String lineEnding = csCode.contains("\r\n") ? "\r\n" : "\n";
		List<String> lines = csCode.lines().toList();
		List<String> out = new ArrayList<>();
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			int lineNum = i + 1;
			List<DiagnosticCandidate> lineCandidates = byLine.get(lineNum);
			if (lineCandidates != null) {
				out.add(line + " " + buildDiagnosticCommentSuffix(lineCandidates));
			} else {
				out.add(line);
			}
		}
		return String.join(lineEnding, out);
	}

	private static String buildDiagnosticCommentSuffix(List<DiagnosticCandidate> lineCandidates) {
		return lineCandidates.stream()
				.map(c -> "// " + DIAG_COMMENT_MARKER + " " + safeForLineComment(c.ruleSource()) + ":"
						+ safeForLineComment(c.ruleFrom()) + ":" + safeForLineComment(c.expandedTo()))
				.collect(Collectors.joining(" "));
	}

	private static String safeForLineComment(String s) {
		if (s == null)
			return "";
		return s.replace("\n", " ").replace("\r", "");
	}

	/**
	 * フェーズ変換ジャーニー HTML を生成する。
	 *
	 * <p>
	 * 各フェーズ遷移（prev→curr）を java-diff-utils の LCS ベース diff で行揃えした 2
	 * カラムテーブルとして横並びに表示する。report.html と同じダークテーマ・インライン diff を使用。
	 * </p>
	 */
	private void writePhaseJourneyHtml(Path path, ConversionResult result) throws IOException {
		List<PhaseSnapshot> snapshots = result.getPhaseSnapshots();
		if (snapshots.isEmpty())
			return;

		DiffRowGenerator generator = DiffRowGenerator.create().showInlineDiffs(true).inlineDiffByWord(true)
				.oldTag(f -> "~").newTag(f -> "**").build();

		StringBuilder sb = new StringBuilder();
		sb.append("<!DOCTYPE html><html lang=\"ja\"><head><meta charset=\"UTF-8\">");
		sb.append("<title>フェーズ変換ジャーニー</title>");
		sb.append("<style>").append(phaseJourneyCss()).append("</style></head><body>");
		sb.append("<h2>フェーズ変換ジャーニー</h2>");
		sb.append("<div class=\"sections\">");

		for (int i = 1; i < snapshots.size(); i++) {
			PhaseSnapshot prev = snapshots.get(i - 1);
			PhaseSnapshot curr = snapshots.get(i);

			List<String> prevLines = List.of(prev.code().split("\n", -1));
			List<String> currLines = List.of(curr.code().split("\n", -1));
			List<DiffRow> rows = generator.generateDiffRows(prevLines, currLines);

			sb.append("<div class=\"section\">");
			sb.append("<h3>").append(escapeHtml(prev.label())).append(" → ").append(escapeHtml(curr.label()))
					.append("</h3>");
			sb.append("<table class=\"diff-table\"><thead><tr>");
			sb.append("<th class=\"line-num\">#</th>");
			sb.append("<th>").append(escapeHtml(prev.label())).append("</th>");
			sb.append("<th>").append(escapeHtml(curr.label())).append("</th>");
			sb.append("</tr></thead><tbody>");

			int rowNum = 1;
			for (DiffRow row : rows) {
				String rowClass = switch (row.getTag()) {
					case INSERT -> "diff-add";
					case DELETE -> "diff-remove";
					case CHANGE -> "diff-change";
					default -> "";
				};
				sb.append("<tr class=\"").append(rowClass).append("\">");
				sb.append("<td class=\"line-num\">").append(rowNum++).append("</td>");
				sb.append("<td class=\"old-line\"><code>").append(diffToHtml(row.getOldLine())).append("</code></td>");
				sb.append("<td class=\"new-line\"><code>").append(diffToHtml(row.getNewLine())).append("</code></td>");
				sb.append("</tr>\n");
			}

			sb.append("</tbody></table></div>\n");
		}

		sb.append("</div></body></html>");
		Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
	}

	private static final Pattern DIFF_DEL_PATTERN = Pattern.compile("~([^~]*)~");
	private static final Pattern DIFF_INS_PATTERN = Pattern.compile("\\*\\*(.*?)\\*\\*");

	private static String diffToHtml(String line) {
		if (line == null)
			return "";
		String s = escapeHtml(line);
		s = DIFF_DEL_PATTERN.matcher(s).replaceAll("<span class=\"diff-del\">$1</span>");
		s = DIFF_INS_PATTERN.matcher(s).replaceAll("<span class=\"diff-ins\">$1</span>");
		return s;
	}

	private static String escapeHtml(String s) {
		if (s == null)
			return "";
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static String phaseJourneyCss() {
		return """
				* { box-sizing: border-box; }
				body { font-family: 'Consolas','Monaco',monospace; margin: 1rem; background: #1e1e1e; color: #d4d4d4; }
				h2 { font-size: 1.3rem; margin: 0 0 1rem; }
				.sections { display: flex; overflow-x: auto; gap: 16px; align-items: flex-start; }
				.section { flex-shrink: 0; min-width: 560px; max-width: 900px; }
				.section h3 { font-size: 0.9rem; margin: 0 0 0.2rem; background: #252526; padding: 4px 8px;
				              border-radius: 4px 4px 0 0; white-space: nowrap; color: #9cdcfe; }
				.diff-table { width: 100%; border-collapse: collapse; font-size: 0.85rem; border: 1px solid #3c3c3c; }
				.diff-table th { background: #2d2d30; padding: 0.3rem 0.75rem; text-align: left; font-weight: 600; }
				.diff-table td { padding: 0.15rem 0.75rem; vertical-align: top; border-top: 1px solid #3c3c3c;
				                 white-space: pre-wrap; word-break: break-all; }
				.line-num { color: #858585; width: 3em; text-align: right; user-select: none; }
				.diff-del { background: rgba(255,100,100,0.3); text-decoration: line-through; }
				.diff-ins { background: rgba(100,255,100,0.3); }
				tr.diff-add    .old-line { background: #2d2020; }
				tr.diff-add    .new-line { background: #1e2d1e; }
				tr.diff-remove .old-line { background: #2d2020; }
				tr.diff-remove .new-line { background: #2d2020; }
				tr.diff-change .old-line { background: #2d2020; }
				tr.diff-change .new-line { background: #1e2d1e; }
				""";
	}

	/**
	 * FunctionUnitEntry リストを JSON 配列文字列に変換する（外部ライブラリ不使用）。
	 *
	 * @param entries
	 *            FunctionUnitEntry リスト（空でないこと）
	 * @return JSON 配列文字列
	 */
	private static String buildFunctionUnitJson(List<FunctionUnitEntry> entries) {
		StringBuilder sb = new StringBuilder("[\n");
		for (int i = 0; i < entries.size(); i++) {
			FunctionUnitEntry e = entries.get(i);
			sb.append("  {\n");
			sb.append("    \"cppSignature\": ").append(jsonString(e.cppSignature())).append(",\n");
			sb.append("    \"csSignature\": ").append(jsonString(e.csSignature())).append(",\n");
			sb.append("    \"comment\": ").append(jsonLinesDiff(e.comment())).append(",\n");
			sb.append("    \"body\": ").append(jsonLinesDiff(e.body())).append("\n");
			sb.append("  }");
			if (i < entries.size() - 1)
				sb.append(",");
			sb.append("\n");
		}
		sb.append("]");
		return sb.toString();
	}

	private static String jsonLinesDiff(FunctionUnitEntry.LinesDiff diff) {
		StringBuilder sb = new StringBuilder("{\n");
		sb.append("      \"cppLines\": ").append(jsonStringArray(diff.cppLines())).append(",\n");
		sb.append("      \"csLines\": ").append(jsonStringArray(diff.csLines())).append("\n");
		sb.append("    }");
		return sb.toString();
	}

	private static String jsonStringArray(List<String> lines) {
		if (lines.isEmpty())
			return "[]";
		StringBuilder sb = new StringBuilder("[\n");
		for (int i = 0; i < lines.size(); i++) {
			sb.append("        ").append(jsonString(lines.get(i)));
			if (i < lines.size() - 1)
				sb.append(",");
			sb.append("\n");
		}
		sb.append("      ]");
		return sb.toString();
	}

	/**
	 * 文字列を JSON 文字列リテラルとしてエスケープして返す（ダブルクォート付き）。
	 */
	private static String jsonString(String s) {
		if (s == null)
			return "null";
		StringBuilder sb = new StringBuilder("\"");
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				default -> {
					if (c < 0x20) {
						sb.append(String.format("\\u%04x", (int) c));
					} else {
						sb.append(c);
					}
				}
			}
		}
		sb.append("\"");
		return sb.toString();
	}

	/**
	 * ソース文字列の指定行（1始まり）を返す。
	 *
	 * @param source
	 *            ソース文字列
	 * @param lineNum
	 *            行番号（1始まり）
	 * @return 該当行の内容（trimmed）、存在しない場合は空文字列
	 */
	private static String getLineAt(String source, int lineNum) {
		if (lineNum < 1) {
			return "";
		}
		return source.lines().skip(lineNum - 1L).findFirst().map(String::trim).orElse("");
	}
}
