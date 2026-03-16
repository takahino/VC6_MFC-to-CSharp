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

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * パターン発見結果を Excel (.xlsx) と HTML (.html) に出力するライター。
 */
public class PatternDiscoveryOutputWriter {

    private static final Logger LOG = LoggerFactory.getLogger(PatternDiscoveryOutputWriter.class);

    private static final String[] COLUMNS = {
            "#", "パターン種別", "識別子名", "アクセス演算子", "引数数",
            "出現回数", "ルール有無", "ルールファイル", "出現ファイル"
    };

    /**
     * パターン発見結果を Excel と HTML に出力する。
     *
     * @param outputDir      出力先ディレクトリ
     * @param result         パターン発見結果
     * @param excelEnabled   Excel 出力の有効/無効
     * @throws IOException ファイル書き込みに失敗した場合
     */
    public void write(Path outputDir, PatternDiscoveryResult result, boolean excelEnabled) throws IOException {
        writeHtml(outputDir, result);
        if (excelEnabled) {
            writeExcel(outputDir, result);
        }
    }

    // -------------------------------------------------------------------------
    // Excel 出力
    // -------------------------------------------------------------------------

    private void writeExcel(Path outputDir, PatternDiscoveryResult result) throws IOException {
        Path xlsxPath = outputDir.resolve("pattern_discovery.xlsx");

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            // スタイル定義
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle uncoveredStyle = createColorStyle(workbook, (short) 0xFFC0, (short) 0x8080, (short) 0x8080); // 薄赤
            CellStyle coveredStyle   = createColorStyle(workbook, (short) 0x8080, (short) 0xFFC0, (short) 0x8080); // 薄緑
            CellStyle normalStyle    = workbook.createCellStyle();

            // Sheet1: ルール候補一覧（全パターン）
            Sheet sheet1 = workbook.createSheet("ルール候補一覧");
            writeSheet(sheet1, result.allPatterns(), headerStyle, uncoveredStyle, coveredStyle, normalStyle);

            // Sheet2: ルール未作成（優先度順）
            Sheet sheet2 = workbook.createSheet("ルール未作成（優先度順）");
            writeSheet(sheet2, result.uncoveredPatterns(), headerStyle, uncoveredStyle, coveredStyle, normalStyle);

            // Sheet3: ルール作成済み
            Sheet sheet3 = workbook.createSheet("ルール作成済み");
            writeSheet(sheet3, result.coveredPatterns(), headerStyle, uncoveredStyle, coveredStyle, normalStyle);

            // Sheet4: ファイル別サマリー
            Sheet sheet4 = workbook.createSheet("ファイル別サマリー");
            writeFileSummarySheet(sheet4, result, headerStyle, normalStyle);

            // zip エントリのタイムスタンプを固定してバイナリ再現性を確保
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
        LOG.info("Excel 出力: {}", xlsxPath);
    }

    private void writeSheet(Sheet sheet, List<CandidatePattern> patterns,
                            CellStyle headerStyle, CellStyle uncoveredStyle,
                            CellStyle coveredStyle, CellStyle normalStyle) {
        // ヘッダ行
        Row headerRow = sheet.createRow(0);
        for (int c = 0; c < COLUMNS.length; c++) {
            Cell cell = headerRow.createCell(c);
            cell.setCellValue(COLUMNS[c]);
            cell.setCellStyle(headerStyle);
        }

        // データ行
        for (int i = 0; i < patterns.size(); i++) {
            CandidatePattern p = patterns.get(i);
            Row row = sheet.createRow(i + 1);
            CellStyle rowStyle = p.hasRule() ? coveredStyle : uncoveredStyle;

            setCell(row, 0, String.valueOf(i + 1), rowStyle);
            setCell(row, 1, p.type().name(), rowStyle);
            setCell(row, 2, p.identifierName(), rowStyle);
            setCell(row, 3, p.accessOperator() != null ? p.accessOperator() : "", rowStyle);
            setCell(row, 4, p.argCount() < 0 ? "-" : String.valueOf(p.argCount()), rowStyle);
            setCell(row, 5, String.valueOf(p.occurrenceCount()), rowStyle);
            setCell(row, 6, p.hasRule() ? "○" : "×", rowStyle);
            setCell(row, 7, p.ruleFile(), rowStyle);
            setCell(row, 8, String.join(", ", p.occurrenceFiles()), rowStyle);
        }

        for (int c = 0; c < COLUMNS.length; c++) {
            sheet.autoSizeColumn(c);
        }
    }

    private void writeFileSummarySheet(Sheet sheet, PatternDiscoveryResult result,
                                       CellStyle headerStyle, CellStyle normalStyle) {
        Row headerRow = sheet.createRow(0);
        String[] cols = {"ファイル名", "パターン数"};
        for (int c = 0; c < cols.length; c++) {
            Cell cell = headerRow.createCell(c);
            cell.setCellValue(cols[c]);
            cell.setCellStyle(headerStyle);
        }

        // ファイルごとのパターン数を集計
        Map<String, Long> filePatternCount = result.allPatterns().stream()
                .flatMap(p -> p.occurrenceFiles().stream())
                .collect(Collectors.groupingBy(f -> f, Collectors.counting()));

        List<Map.Entry<String, Long>> sorted = filePatternCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .toList();

        for (int i = 0; i < sorted.size(); i++) {
            Row row = sheet.createRow(i + 1);
            setCell(row, 0, sorted.get(i).getKey(), normalStyle);
            setCell(row, 1, String.valueOf(sorted.get(i).getValue()), normalStyle);
        }

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void setCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createColorStyle(Workbook workbook, short r, short g, short b) {
        // IndexedColors で近似色を選択（XSSFColor は使用しない）
        CellStyle style = workbook.createCellStyle();
        // 赤系 = ROSE, 緑系 = LIGHT_GREEN
        if (r > g) {
            style.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        } else {
            style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        }
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    // -------------------------------------------------------------------------
    // HTML 出力
    // -------------------------------------------------------------------------

    private void writeHtml(Path outputDir, PatternDiscoveryResult result) throws IOException {
        Path htmlPath = outputDir.resolve("pattern_discovery.html");
        String html = buildHtml(result);
        Files.writeString(htmlPath, html, StandardCharsets.UTF_8);
        LOG.info("HTML 出力: {}", htmlPath);
    }

    private String buildHtml(PatternDiscoveryResult result) {
        int total = result.allPatterns().size();
        int covered = result.coveredPatterns().size();
        int uncovered = result.uncoveredPatterns().size();
        double coverRate = total == 0 ? 0.0 : covered * 100.0 / total;

        StringBuilder sb = new StringBuilder();
        sb.append("""
                <!DOCTYPE html>
                <html lang="ja">
                <head>
                <meta charset="UTF-8">
                <title>パターン発見レポート</title>
                <style>
                  body { background:#1e1e1e; color:#d4d4d4; font-family:monospace; margin:20px; }
                  h1 { color:#4ec9b0; }
                  h2 { color:#9cdcfe; border-bottom:1px solid #444; padding-bottom:4px; }
                  table { border-collapse:collapse; width:100%; margin-bottom:24px; font-size:0.85em; }
                  th { background:#2d2d2d; color:#9cdcfe; padding:6px 8px; text-align:left; border:1px solid #444; }
                  td { padding:5px 8px; border:1px solid #333; vertical-align:top; }
                  tr.uncovered td { background:#3a1a1a; color:#f48771; }
                  tr.covered   td { background:#1a3a2a; color:#4ec9b0; }
                  .stat-table td { width:25%; font-size:1.1em; text-align:center; }
                  .stat-table .num { font-size:2em; font-weight:bold; color:#ce9178; }
                </style>
                </head>
                <body>
                """);

        sb.append("<h1>パターン発見レポート</h1>\n");

        // サマリー統計
        sb.append("<h2>サマリー</h2>\n");
        sb.append("<table class='stat-table'><tr>\n");
        sb.append(String.format("<td>総パターン数<br><span class='num'>%d</span></td>\n", total));
        sb.append(String.format("<td>スキャンファイル数<br><span class='num'>%d</span></td>\n", result.totalFiles()));
        sb.append(String.format("<td>カバー済み<br><span class='num'>%d</span></td>\n", covered));
        sb.append(String.format("<td>未カバー<br><span class='num'>%d</span></td>\n", uncovered));
        sb.append(String.format("<td>カバー率<br><span class='num'>%.1f%%</span></td>\n", coverRate));
        sb.append("</tr></table>\n");

        // ルール未作成テーブル
        sb.append("<h2>ルール未作成（優先度順）</h2>\n");
        appendPatternTable(sb, result.uncoveredPatterns(), "uncovered");

        // ルール作成済みテーブル
        sb.append("<h2>ルール作成済み</h2>\n");
        appendPatternTable(sb, result.coveredPatterns(), "covered");

        sb.append("</body></html>\n");
        return sb.toString();
    }

    private void appendPatternTable(StringBuilder sb, List<CandidatePattern> patterns, String cssClass) {
        if (patterns.isEmpty()) {
            sb.append("<p>（なし）</p>\n");
            return;
        }
        sb.append("<table>\n<tr>");
        for (String col : COLUMNS) {
            sb.append("<th>").append(escapeHtml(col)).append("</th>");
        }
        sb.append("</tr>\n");

        for (int i = 0; i < patterns.size(); i++) {
            CandidatePattern p = patterns.get(i);
            sb.append("<tr class='").append(cssClass).append("'>");
            sb.append("<td>").append(i + 1).append("</td>");
            sb.append("<td>").append(escapeHtml(p.type().name())).append("</td>");
            sb.append("<td>").append(escapeHtml(p.identifierName())).append("</td>");
            sb.append("<td>").append(escapeHtml(p.accessOperator() != null ? p.accessOperator() : "")).append("</td>");
            sb.append("<td>").append(p.argCount() < 0 ? "-" : p.argCount()).append("</td>");
            sb.append("<td>").append(p.occurrenceCount()).append("</td>");
            sb.append("<td>").append(p.hasRule() ? "○" : "×").append("</td>");
            sb.append("<td>").append(escapeHtml(p.ruleFile())).append("</td>");
            sb.append("<td>").append(escapeHtml(String.join(", ", p.occurrenceFiles()))).append("</td>");
            sb.append("</tr>\n");
        }
        sb.append("</table>\n");
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
