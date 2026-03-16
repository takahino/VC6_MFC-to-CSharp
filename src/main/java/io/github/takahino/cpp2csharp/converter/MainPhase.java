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

package io.github.takahino.cpp2csharp.converter;

import io.github.takahino.cpp2csharp.dynamic.DynamicRuleGenerator;
import io.github.takahino.cpp2csharp.dynamic.DynamicRuleSpec;
import io.github.takahino.cpp2csharp.rule.ConversionRule;
import io.github.takahino.cpp2csharp.rule.ConversionRuleLoader;
import io.github.takahino.cpp2csharp.transform.Transformer;
import io.github.takahino.cpp2csharp.tree.AstNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * MAIN フェーズの実装。静的ルール（{@code .rule}）と動的ルール（{@code .drule}）を適用する。
 *
 * <h2>関数単位処理</h2>
 * <p>トークン列を {@link FunctionUnitSplitter} で関数定義単位に分割し、
 * 各単位に対して変換を実行する。
 * 単位分割により 1 パスあたりの探索トークン数が減少し処理を高速化する。</p>
 *
 * <h2>state 管理と Transformer の使い方</h2>
 * <p>複数ユニットにわたって appliedTransforms / errors が正しく累積されるよう、
 * {@link Transformer#prepareForNewConversion} を 1 回だけ呼んだ後、
 * ユニットごとに {@link Transformer#processUnitReturnNodes} を呼ぶ。
 * 全ユニット完了後に {@link Transformer#runPostTransformScans} で
 * 診断スキャンとニアミススキャンを実行する。</p>
 *
 * <p>{@link Transformer} インスタンスは {@code CppToCSharpConverter} と共有される。
 * パイプライン終了後も {@code transformer.getErrors()} 等を読み続けるため、
 * converter が所有し続ける。</p>
 */
public class MainPhase implements ConversionPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainPhase.class);

    private final List<List<ConversionRule>> mainPhases;
    private final List<DynamicRuleSpec> dynamicSpecs;
    private final Transformer transformer;
    private final ConversionRuleLoader ruleLoader;
    /**
     * ParseTree から抽出した関数定義の stream index 範囲リスト。
     * 各 {@code int[2]} = [startStreamIndex, stopStreamIndex]。
     * 空リストの場合は {@link FunctionUnitSplitter#split(List)} のブラケット深度方式にフォールバックする。
     */
    private final List<int[]> functionDefinitionRanges;

    /**
     * @param mainPhases              静的 MAIN フェーズルール群
     * @param dynamicSpecs            動的ルール仕様（空リストの場合は動的生成しない）
     * @param transformer             変換エンジン（converter と共有）
     * @param ruleLoader              動的ルール生成に使用するルールローダー
     * @param functionDefinitionRanges ParseTree から抽出した関数定義範囲（空リストでフォールバック）
     */
    public MainPhase(List<List<ConversionRule>> mainPhases, List<DynamicRuleSpec> dynamicSpecs,
                     Transformer transformer, ConversionRuleLoader ruleLoader,
                     List<int[]> functionDefinitionRanges) {
        this.mainPhases = mainPhases;
        this.dynamicSpecs = dynamicSpecs;
        this.transformer = transformer;
        this.ruleLoader = ruleLoader;
        this.functionDefinitionRanges = functionDefinitionRanges;
    }

    @Override
    public String name() {
        return "MAIN";
    }

    @Override
    public PhaseExecutionResult execute(PhaseExecutionContext ctx) {
        List<List<ConversionRule>> effectiveMainPhases = new ArrayList<>(mainPhases);

        // 動的ルール生成（PRE 完了後のトークンストリーム全体から収集）
        // collect: パターンはファイル全体のトークンを対象とするため、単位分割前に実行する
        if (!dynamicSpecs.isEmpty()) {
            DynamicRuleGenerator dynamicGenerator = new DynamicRuleGenerator(ruleLoader);
            List<ConversionRule> dynamicRules = dynamicGenerator.generate(ctx.tokenNodes(), dynamicSpecs);
            if (!dynamicRules.isEmpty()) {
                LOGGER.info("動的ルール追加: {} ルール (MAIN フェーズ末尾に追加)", dynamicRules.size());
                effectiveMainPhases.add(dynamicRules);
            }
        }

        List<AstNode> mainResult;
        List<PhaseSnapshot> snapshots = new ArrayList<>();

        if (!effectiveMainPhases.isEmpty()) {
            List<TokenUnit> units = FunctionUnitSplitter.split(ctx.tokenNodes(), functionDefinitionRanges);
            LOGGER.info("MAIN フェーズ開始: {} フェーズ, {} 単位 (ParseTree範囲={})",
                    effectiveMainPhases.size(), units.size(),
                    functionDefinitionRanges.isEmpty() ? "なし(全体1単位)" : functionDefinitionRanges.size() + "件");

            // state を 1 回だけリセット（ユニットをまたいで appliedTransforms 等を累積させる）
            transformer.prepareForNewConversion(ctx.excelEnabled());
            // ファイル全体の初期状態を STEP 0 として 1 回だけ書き込む
            transformer.writeInitialVizState(ctx.tokenNodes());
            mainResult = new ArrayList<>();
            List<String> unitOutputDumps = new ArrayList<>();
            List<UnitLabel> unitLabels = new ArrayList<>();
            try {
                for (TokenUnit unit : units) {
                    if (unit.tokens().isEmpty()) {
                        continue;
                    }
                    List<AstNode> unitResult = transformer.processUnitReturnNodes(
                            unit.tokens(), effectiveMainPhases, ctx.commentsBeforeToken());
                    mainResult.addAll(unitResult);
                    // 全ユニット（gap/body）の変換後テキストを収集（.cpp.txt と N を揃えるため）
                    unitOutputDumps.add(transformer.buildOutput(unitResult, ctx.commentsBeforeToken()));
                    unitLabels.add(unit.label());
                }
            } finally {
                transformer.closeVizWriter();
            }

            // 全ユニット完了後: 結合結果に対して診断・ニアミススキャンを 1 回実行
            transformer.runPostTransformScans(mainResult, effectiveMainPhases);

            String code = transformer.buildOutput(mainResult, ctx.commentsBeforeToken());
            snapshots.add(new PhaseSnapshot("MAIN-1", code));
            LOGGER.info("MAIN フェーズ完了: {} トークン", mainResult.size());

            // logs は空（appliedTransforms は transformer フィールド経由で converter が収集）
            return new PhaseExecutionResult(
                    mainResult, ctx.commentsBeforeToken(), code, snapshots, List.of(),
                    unitOutputDumps, unitLabels);
        } else {
            // MAIN ルールが空の場合も Transformer の state をリセットする
            transformer.prepareForNewConversion(ctx.excelEnabled());
            mainResult = ctx.tokenNodes();
        }

        String code = transformer.buildOutput(mainResult, ctx.commentsBeforeToken());
        return new PhaseExecutionResult(
                mainResult, ctx.commentsBeforeToken(), code, snapshots, List.of(),
                List.of(), List.of());
    }
}
