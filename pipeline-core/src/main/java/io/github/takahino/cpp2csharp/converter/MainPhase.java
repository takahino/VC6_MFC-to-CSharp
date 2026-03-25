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
import io.github.takahino.cpp2csharp.matcher.ReceiverValidator;
import io.github.takahino.cpp2csharp.retokenize.RetokenizeResult;
import io.github.takahino.cpp2csharp.retokenize.Retokenizer;
import io.github.takahino.cpp2csharp.rule.ConversionRule;
import io.github.takahino.cpp2csharp.rule.ConversionRuleLoader;
import io.github.takahino.cpp2csharp.rule.LanguageLexerFactory;
import io.github.takahino.cpp2csharp.transform.Transformer;
import io.github.takahino.cpp2csharp.tree.AstNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MAIN フェーズの実装。静的ルール（{@code .rule}）と動的ルール（{@code .drule}）を適用する。
 *
 * <h2>関数単位処理</h2>
 * <p>
 * トークン列を {@link FunctionUnitSplitter} で関数定義単位に分割し、 各単位に対して変換を実行する。 単位分割により 1
 * パスあたりの探索トークン数が減少し処理を高速化する。
 * </p>
 *
 * <h2>state 管理と Transformer の使い方</h2>
 * <p>
 * 複数ユニットにわたって appliedTransforms / errors が正しく累積されるよう、
 * {@link Transformer#prepareForNewConversion} を 1 回だけ呼んだ後、 ユニットごとに
 * {@link Transformer#processUnitReturnNodes} を呼ぶ。 全ユニット完了後に
 * {@link Transformer#runPostTransformScans} で 診断スキャンとニアミススキャンを実行する。
 * </p>
 *
 * <p>
 * {@link Transformer} インスタンスは {@code CppToCSharpConverter} と共有される。 パイプライン終了後も
 * {@code transformer.getErrors()} 等を読み続けるため、 converter が所有し続ける。
 * </p>
 */
public class MainPhase implements ConversionPhase {

	private static final Logger LOGGER = LoggerFactory.getLogger(MainPhase.class);

	private final List<List<ConversionRule>> mainPhases;
	private final List<DynamicRuleSpec> dynamicSpecs;
	private final Transformer transformer;
	private final ConversionRuleLoader ruleLoader;
	/**
	 * ParseTree から抽出した関数定義の stream index 範囲リスト。 各 {@code int[2]} =
	 * [startStreamIndex, stopStreamIndex]。 空リストの場合は
	 * {@link FunctionUnitSplitter#split(List)} のブラケット深度方式にフォールバックする。
	 */
	private final List<int[]> functionDefinitionRanges;
	private final ReceiverValidator receiverValidator;
	private final Retokenizer retokenizer;

	/**
	 * @param mainPhases
	 *            静的 MAIN フェーズルール群
	 * @param dynamicSpecs
	 *            動的ルール仕様（空リストの場合は動的生成しない）
	 * @param transformer
	 *            変換エンジン（converter と共有）
	 * @param ruleLoader
	 *            動的ルール生成に使用するルールローダー
	 * @param functionDefinitionRanges
	 *            ParseTree から抽出した関数定義範囲（空リストでフォールバック）
	 */
	public MainPhase(List<List<ConversionRule>> mainPhases, List<DynamicRuleSpec> dynamicSpecs, Transformer transformer,
			ConversionRuleLoader ruleLoader, List<int[]> functionDefinitionRanges) {
		this(mainPhases, dynamicSpecs, transformer, ruleLoader, functionDefinitionRanges, null, null);
	}

	/**
	 * @param mainPhases
	 *            静的 MAIN フェーズルール群
	 * @param dynamicSpecs
	 *            動的ルール仕様（空リストの場合は動的生成しない）
	 * @param transformer
	 *            変換エンジン（converter と共有）
	 * @param ruleLoader
	 *            動的ルール生成に使用するルールローダー
	 * @param functionDefinitionRanges
	 *            ParseTree から抽出した関数定義範囲（空リストでフォールバック）
	 * @param receiverValidator
	 *            RECEIVER キャプチャの妥当性検証器（null の場合はプリフィルタのみ使用）
	 */
	public MainPhase(List<List<ConversionRule>> mainPhases, List<DynamicRuleSpec> dynamicSpecs, Transformer transformer,
			ConversionRuleLoader ruleLoader, List<int[]> functionDefinitionRanges,
			ReceiverValidator receiverValidator) {
		this(mainPhases, dynamicSpecs, transformer, ruleLoader, functionDefinitionRanges, receiverValidator, null);
	}

	/**
	 * @param mainPhases
	 *            静的 MAIN フェーズルール群
	 * @param dynamicSpecs
	 *            動的ルール仕様（空リストの場合は動的生成しない）
	 * @param transformer
	 *            変換エンジン（converter と共有）
	 * @param ruleLoader
	 *            動的ルール生成に使用するルールローダー
	 * @param functionDefinitionRanges
	 *            ParseTree から抽出した関数定義範囲（空リストでフォールバック）
	 * @param receiverValidator
	 *            RECEIVER キャプチャの妥当性検証器（null の場合はプリフィルタのみ使用）
	 * @param lexerFactory
	 *            MAINサブフェーズ間の再トークン化に使用するファクトリ（null の場合は再トークン化を行わない）
	 */
	public MainPhase(List<List<ConversionRule>> mainPhases, List<DynamicRuleSpec> dynamicSpecs, Transformer transformer,
			ConversionRuleLoader ruleLoader, List<int[]> functionDefinitionRanges, ReceiverValidator receiverValidator,
			LanguageLexerFactory lexerFactory) {
		this.mainPhases = mainPhases;
		this.dynamicSpecs = dynamicSpecs;
		this.transformer = transformer;
		this.ruleLoader = ruleLoader;
		this.functionDefinitionRanges = functionDefinitionRanges;
		this.receiverValidator = receiverValidator;
		this.retokenizer = new Retokenizer(lexerFactory);
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
			DynamicRuleGenerator dynamicGenerator = new DynamicRuleGenerator(ruleLoader, receiverValidator);
			List<ConversionRule> dynamicRules = dynamicGenerator.generate(ctx.tokenNodes(), dynamicSpecs);
			if (!dynamicRules.isEmpty()) {
				LOGGER.info("動的ルール追加: {} ルール (MAIN フェーズ末尾に追加)", dynamicRules.size());
				effectiveMainPhases.add(dynamicRules);
			}
		}

		List<AstNode> mainResult;
		List<PhaseSnapshot> snapshots = new ArrayList<>();

		if (!effectiveMainPhases.isEmpty()) {
			LOGGER.info("MAIN フェーズ開始: {} サブフェーズ (ParseTree範囲={})", effectiveMainPhases.size(),
					functionDefinitionRanges.isEmpty() ? "なし(全体1単位)" : functionDefinitionRanges.size() + "件");

			// state を 1 回だけリセット（ユニットをまたいで appliedTransforms 等を累積させる）
			transformer.prepareForNewConversion();

			List<AstNode> currentTokenNodes = ctx.tokenNodes();
			Map<Integer, List<String>> currentComments = ctx.commentsBeforeToken();
			List<int[]> currentRanges = functionDefinitionRanges;

			List<String> unitOutputDumps = new ArrayList<>();
			List<UnitLabel> unitLabels = new ArrayList<>();

			for (int phaseIdx = 0; phaseIdx < effectiveMainPhases.size(); phaseIdx++) {
				List<ConversionRule> subPhaseRules = effectiveMainPhases.get(phaseIdx);
				boolean isLast = (phaseIdx == effectiveMainPhases.size() - 1);

				List<TokenUnit> units = FunctionUnitSplitter.split(currentTokenNodes, currentRanges);
				LOGGER.info("MAIN サブフェーズ {}/{}: {} 単位", phaseIdx + 1, effectiveMainPhases.size(), units.size());

				List<AstNode> phaseResult = new ArrayList<>();
				List<String> phaseUnitOutputDumps = new ArrayList<>();
				List<UnitLabel> phaseUnitLabels = new ArrayList<>();

				for (TokenUnit unit : units) {
					if (unit.tokens().isEmpty()) {
						continue;
					}
					List<AstNode> unitResult = transformer.processUnitReturnNodes(unit.tokens(), List.of(subPhaseRules),
							currentComments);
					phaseResult.addAll(unitResult);
					// 全ユニット（gap/body）の変換後テキストを収集
					phaseUnitOutputDumps.add(transformer.buildOutput(unitResult, currentComments));
					phaseUnitLabels.add(unit.label());
				}

				currentTokenNodes = phaseResult;
				// 最後のサブフェーズの unitOutputDumps/unitLabels を保持（デバッグファイル出力は最終状態を反映）
				unitOutputDumps = phaseUnitOutputDumps;
				unitLabels = phaseUnitLabels;

				String phaseCode = transformer.buildOutput(currentTokenNodes, currentComments);
				snapshots.add(new PhaseSnapshot("MAIN-" + (phaseIdx + 1), phaseCode));

				if (!isLast) {
					// サブフェーズ切り替え時に再トークン化: 合成トークンを分解し直す
					RetokenizeResult retokenized = retokenizer.retokenize(currentTokenNodes, currentComments);
					LOGGER.info("MAIN サブフェーズ {} 後 再トークン化: {} トークン", phaseIdx + 1, retokenized.tokenNodes().size());
					currentTokenNodes = retokenized.tokenNodes();
					currentComments = retokenized.commentsBeforeToken();
					// 再トークン化後は stream index が再採番されるため元の範囲は無効
					currentRanges = List.of();
				}
			}

			mainResult = currentTokenNodes;

			// 全ユニット完了後: 結合結果に対して診断・ニアミススキャンを 1 回実行
			transformer.runPostTransformScans(mainResult, effectiveMainPhases);

			String code = transformer.buildOutput(mainResult, currentComments);
			LOGGER.info("MAIN フェーズ完了: {} トークン", mainResult.size());

			// logs は空（appliedTransforms は transformer フィールド経由で converter が収集）
			return new PhaseExecutionResult(mainResult, currentComments, code, snapshots, List.of(), unitOutputDumps,
					unitLabels);
		} else {
			// MAIN ルールが空の場合も Transformer の state をリセットする
			transformer.prepareForNewConversion();
			mainResult = ctx.tokenNodes();
		}

		String code = transformer.buildOutput(mainResult, ctx.commentsBeforeToken());
		return new PhaseExecutionResult(mainResult, ctx.commentsBeforeToken(), code, snapshots, List.of(), List.of(),
				List.of());
	}
}
