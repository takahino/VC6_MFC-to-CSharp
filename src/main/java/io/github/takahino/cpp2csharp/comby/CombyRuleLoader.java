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

package io.github.takahino.cpp2csharp.comby;

import io.github.takahino.cpp2csharp.rule.RuleLoaderConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * {@code .crule} ファイルを読み込み、{@link CombyRule} のリストを生成するクラス。
 *
 * <h2>ファイル形式</h2>
 * <pre>
 * # コメント行
 * from: :[recv].Left(:[n])
 * to: :[recv].Substring(0, :[n])
 * test: str.Left(5)
 * assrt: str.Substring(0, 5)
 * </pre>
 */
public class CombyRuleLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(CombyRuleLoader.class);
    private static final Pattern PHASE_DIR_PATTERN = RuleLoaderConstants.PHASE_DIR_PATTERN;
    private static final String COMBY_RULES_PATH = "rules/comby";
    private static final String CRULE_EXTENSION = ".crule";

    /**
     * クラスパス上の {@code rules/comby/} 配下から全フェーズのルールを読み込む。
     * ディレクトリが存在しない場合は空のリストを返す（後方互換）。
     */
    public List<List<CombyRule>> loadFromClasspath() throws IOException {
        URL url = getClass().getClassLoader().getResource(COMBY_RULES_PATH);
        if (url == null) {
            LOGGER.debug("rules/comby/ ディレクトリが存在しないため COMBY フェーズをスキップ");
            return List.of();
        }
        try {
            URI uri = url.toURI();
            if ("jar".equals(uri.getScheme())) {
                try (FileSystem fs = FileSystems.newFileSystem(uri, Map.of())) {
                    return loadPhasesFrom(fs.getPath(COMBY_RULES_PATH));
                }
            } else {
                return loadPhasesFrom(Path.of(uri));
            }
        } catch (URISyntaxException e) {
            throw new IOException("rules/comby/ URI の解析に失敗: " + e.getMessage(), e);
        }
    }

    /** 指定ディレクトリ配下の [NN]_* サブディレクトリからフェーズ別に読み込む */
    public List<List<CombyRule>> loadPhasesFrom(Path baseDir) throws IOException {
        if (!Files.isDirectory(baseDir)) return List.of();
        List<List<CombyRule>> phases = new ArrayList<>();
        try (Stream<Path> entries = Files.list(baseDir)) {
            List<Path> sortedDirs = entries
                    .filter(Files::isDirectory)
                    .filter(p -> PHASE_DIR_PATTERN.matcher(p.getFileName().toString()).matches())
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
            for (Path phaseDir : sortedDirs) {
                List<CombyRule> phaseRules = new ArrayList<>();
                try (Stream<Path> files = Files.walk(phaseDir)) {
                    files.filter(p -> p.toString().endsWith(CRULE_EXTENSION))
                         .sorted()
                         .forEach(f -> {
                             try {
                                 phaseRules.addAll(loadFromFile(f));
                             } catch (IOException e) {
                                 LOGGER.warn("crule 読み込み失敗: {}", f, e);
                             }
                         });
                }
                if (!phaseRules.isEmpty()) phases.add(phaseRules);
            }
        }
        return phases;
    }

    /** 単一の .crule ファイルを読み込む */
    public List<CombyRule> loadFromFile(Path path) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        return parseContent(content, path.getFileName().toString());
    }

    /** .crule テキストをパースして CombyRule リストを返す */
    public List<CombyRule> parseContent(String content, String sourceFile) {
        List<CombyRule> rules = new ArrayList<>();
        String currentFrom = null;
        String currentTo = null;
        List<String> testInputs = new ArrayList<>();
        List<String> assrtOutputs = new ArrayList<>();

        for (String rawLine : content.split("\r?\n")) {
            String line = rawLine;
            // コメント除去
            int commentIdx = line.indexOf('#');
            if (commentIdx >= 0) line = line.substring(0, commentIdx);
            line = line.stripTrailing();
            if (line.isBlank()) continue;

            if (line.startsWith("from:")) {
                // 前のルールを確定
                if (currentFrom != null && currentTo != null) {
                    rules.add(buildRule(sourceFile, currentFrom, currentTo, testInputs, assrtOutputs));
                }
                currentFrom = line.substring("from:".length()).strip();
                currentTo = null;
                testInputs = new ArrayList<>();
                assrtOutputs = new ArrayList<>();
            } else if (line.startsWith("to:")) {
                currentTo = line.substring("to:".length()).strip();
            } else if (line.startsWith("test:")) {
                testInputs.add(line.substring("test:".length()).strip());
            } else if (line.startsWith("assrt:")) {
                assrtOutputs.add(line.substring("assrt:".length()).strip());
            }
        }
        // 最後のルールを確定
        if (currentFrom != null && currentTo != null) {
            rules.add(buildRule(sourceFile, currentFrom, currentTo, testInputs, assrtOutputs));
        }
        LOGGER.debug("{}: {} ルール読み込み", sourceFile, rules.size());
        return rules;
    }

    private CombyRule buildRule(String sourceFile, String from, String to,
                                 List<String> tests, List<String> assrts) {
        List<CombyTestCase> testCases = new ArrayList<>();
        int count = Math.min(tests.size(), assrts.size());
        for (int i = 0; i < count; i++) {
            testCases.add(new CombyTestCase(tests.get(i), assrts.get(i)));
        }
        return new CombyRule(sourceFile, from, to, testCases);
    }
}
