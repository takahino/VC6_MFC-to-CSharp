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

package io.github.takahino.cpp2csharp.multi;

import io.github.takahino.cpp2csharp.converter.PhaseTransformLog;
import io.github.takahino.cpp2csharp.matcher.MatchResult;
import io.github.takahino.cpp2csharp.mrule.MRuleFindSpec;
import io.github.takahino.cpp2csharp.mrule.MultiReplaceRule;
import io.github.takahino.cpp2csharp.rule.ConversionToken;
import io.github.takahino.cpp2csharp.tree.AstNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * マルチ置換ルールをトークンノード列に適用する変換器クラス。
 *
 * <h2>変換ループ</h2>
 * <p>1フェーズ内で全ルールのマッチがなくなるまで繰り返し適用する。
 * 各パスで1ルールを適用し、適用済みルールIDは記録して再適用を防ぐ。</p>
 */
public class MultiReplaceTransformer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiReplaceTransformer.class);
    private static final int MAX_PASSES = 50_000;

    private final MultiReplaceMatcher matcher = new MultiReplaceMatcher();

    /** フェーズ適用ログ（フェーズ実行後に getLogs() で取得） */
    private final List<PhaseTransformLog> logs = new ArrayList<>();

    /**
     * 直近フェーズの適用ログを返す。
     *
     * @return 適用ログリスト（読み取り専用コピー）
     */
    public List<PhaseTransformLog> getLogs() {
        return List.copyOf(logs);
    }

    /**
     * ログをクリアする。
     */
    public void clearLogs() {
        logs.clear();
    }

    /**
     * マルチ置換ルールの1フェーズ分を適用する（後方互換オーバーロード）。
     *
     * @param tokenNodes 入力トークンノード列
     * @param rules      適用するルールリスト
     * @return 変換後のトークンノード列
     */
    public List<AstNode> transformPhase(List<AstNode> tokenNodes, List<MultiReplaceRule> rules) {
        return transformPhase(tokenNodes, rules, "MRULE", 0);
    }

    /**
     * マルチ置換ルールの1フェーズ分を適用する。
     *
     * @param tokenNodes 入力トークンノード列
     * @param rules      適用するルールリスト
     * @param phaseName  フェーズ名（"PRE", "POST" 等）
     * @param phaseIndex フェーズ番号（1始まり）
     * @return 変換後のトークンノード列
     */
    public List<AstNode> transformPhase(List<AstNode> tokenNodes, List<MultiReplaceRule> rules,
                                        String phaseName, int phaseIndex) {
        List<AstNode> current = new ArrayList<>(tokenNodes);

        for (int pass = 0; pass < MAX_PASSES; pass++) {
            List<MultiReplaceMatchResult> allMatches = matcher.matchAll(rules, current);
            if (allMatches.isEmpty()) break;
            List<AstNode> next = applyAtomically(current, allMatches.get(0), phaseName, phaseIndex);
            if (textsEqual(current, next)) break; // 置換前後が同一 → 無限ループ防止
            current = next;
        }
        return current;
    }

    private static boolean textsEqual(List<AstNode> a, List<AstNode> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).getText().equals(b.get(i).getText())) return false;
        }
        return true;
    }

    private List<AstNode> applyAtomically(List<AstNode> tokenNodes, MultiReplaceMatchResult match,
                                           String phaseName, int phaseIndex) {
        List<AstNode> result = new ArrayList<>(tokenNodes);

        List<MatchResult> stepMatches = match.stepMatches();
        List<MRuleFindSpec> specs = match.rule().getFindSpecs();
        Map<Integer, List<String>> captures = match.captures();

        // Sort steps by startIndex descending for safe in-place deletion
        List<int[]> stepIndices = new ArrayList<>();
        for (int i = 0; i < stepMatches.size(); i++) {
            MatchResult m = stepMatches.get(i);
            stepIndices.add(new int[]{m.getStartIndex(), m.getEndIndex(), i});
        }
        stepIndices.sort((a, b) -> Integer.compare(b[0], a[0]));

        // Collect matched text before any splicing (ascending order for readability)
        StringBuilder matchedTextBuilder = new StringBuilder();
        List<int[]> ascendingIndices = new ArrayList<>(stepIndices);
        ascendingIndices.sort((a, b) -> Integer.compare(a[0], b[0]));
        for (int[] si : ascendingIndices) {
            int s = si[0], e = si[1];
            if (s < tokenNodes.size() && e <= tokenNodes.size()) {
                if (matchedTextBuilder.length() > 0) matchedTextBuilder.append(" ... ");
                matchedTextBuilder.append(
                        tokenNodes.subList(s, e).stream()
                                .map(AstNode::getText)
                                .collect(Collectors.joining(" ")));
            }
        }

        // Collect replacement texts and from/to for logging
        StringBuilder replacedTextBuilder = new StringBuilder();
        StringBuilder fromPatternBuilder = new StringBuilder();
        StringBuilder toPatternBuilder = new StringBuilder();
        for (int[] si : ascendingIndices) {
            int specIndex = si[2];
            MRuleFindSpec spec = specs.get(specIndex);
            String replacement = MatchResult.expandToTemplate(spec.replacement(), captures);
            if (replacedTextBuilder.length() > 0) {
                replacedTextBuilder.append(" ... ");
                fromPatternBuilder.append(" / ");
                toPatternBuilder.append(" / ");
            }
            replacedTextBuilder.append(replacement);
            fromPatternBuilder.append(
                    spec.pattern().stream().map(ConversionToken::getValue).collect(Collectors.joining(" ")));
            toPatternBuilder.append(spec.replacement());
        }

        for (int[] si : stepIndices) {
            int start = si[0];
            int end = si[1];
            int specIndex = si[2];
            MRuleFindSpec spec = specs.get(specIndex);
            String replacement = MatchResult.expandToTemplate(spec.replacement(), captures);

            // Safety check: ensure indices are still valid after prior deletions
            if (start > result.size() || end > result.size()) {
                LOGGER.warn("MultiReplace: インデックス範囲外 [{}..{}) size={}, スキップ",
                        start, end, result.size());
                continue;
            }

            AstNode firstNode = result.get(start);
            List<AstNode> before = new ArrayList<>(result.subList(0, start));
            List<AstNode> after = new ArrayList<>(result.subList(end, result.size()));

            before.addAll(after);
            result = before;

            if (!replacement.isEmpty()) {
                result.add(start, AstNode.tokenNodeWithId(replacement,
                        firstNode.getLine(), firstNode.getColumn(),
                        firstNode.getId(), firstNode.getStreamIndex()));
            }

            LOGGER.info("MultiReplace [{}]: [{}..{}) → [{}]",
                    match.rule().getRuleId(), start, end, replacement);
        }

        // Record log entry for this application
        logs.add(new PhaseTransformLog(
                phaseName,
                phaseIndex,
                match.rule().getSourceFile(),
                fromPatternBuilder.toString(),
                toPatternBuilder.toString(),
                matchedTextBuilder.toString(),
                replacedTextBuilder.toString()));

        return result;
    }

}
