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

package io.github.takahino.cpp2csharp.transform;

import io.github.takahino.cpp2csharp.matcher.MatchResult;
import io.github.takahino.cpp2csharp.matcher.PatternMatcher;
import io.github.takahino.cpp2csharp.matcher.ReceiverValidator;
import io.github.takahino.cpp2csharp.rule.ConversionRule;
import io.github.takahino.cpp2csharp.rule.ConversionToken;
import io.github.takahino.cpp2csharp.transform.strategy.MatchSelectionContext;
import io.github.takahino.cpp2csharp.transform.strategy.MatchSelectionInput;
import io.github.takahino.cpp2csharp.transform.strategy.MatchSelectionStrategy;
import io.github.takahino.cpp2csharp.transform.strategy.RightmostFirstSelectionStrategy;
import io.github.takahino.cpp2csharp.transform.strategy.SelectionDecision;
import io.github.takahino.cpp2csharp.tree.AstNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * フラットなトークンリストにパターンマッチ変換を繰り返し適用して C# コードを生成するクラス。
 *
 * <h2>変換フロー（フラットトークンリスト方式）</h2>
 * <ol>
 * <li>呼び出し元から受け取った初期トークンノード列を使用</li>
 * <li>全変換ルールに対してパターンマッチングを実施</li>
 * <li><strong>右端優先・最短範囲優先</strong>で変換を適用</li>
 * <li>マッチしたトークン範囲を置換テキストで差し替え（リスト Splice）</li>
 * <li>変化がなくなるまで繰り返す（最大 50,000 パス）</li>
 * </ol>
 *
 * <h2>右端優先処理の意義</h2>
 * <p>
 * {@code sqrt(pow(x, 2.0) + pow(y, 2.0))} のようなネスト式では、 最右端（最内側）の {@code pow}
 * を先に変換し、その後 {@code sqrt} を変換することで 正しく
 * {@code Math.Sqrt(Math.Pow(x, 2.0) + Math.Pow(y, 2.0))} が得られる。
 * </p>
 *
 * <h2>曖昧性解消（特異性優先）</h2>
 * <p>
 * 同一位置・同一範囲に複数ルールがマッチした場合、 <strong>具体トークン数が最大のルール（最も特異的）</strong>を優先する。
 * 特異性が同じ場合のみ「真の曖昧マッチ」としてエラーを記録し変換しない。
 * </p>
 *
 * <h2>スレッド安全性</h2>
 * <p>
 * 本クラスはスレッドセーフではない。{@code errors}, {@code appliedTransforms},
 * {@code diagnosticCandidates} はインスタンスフィールドであり、同一インスタンスを複数スレッドから同時に
 * {@code transformWithPhases} で 呼ぶとデータが混在する。マルチスレッド化する場合は、変換対象ファイルごとに
 * {@link io.github.takahino.cpp2csharp.converter.CppToCSharpConverter} を new
 * し、converter を共有しないこと。
 * </p>
 */
public class Transformer {

	private static final Logger LOGGER = LoggerFactory.getLogger(Transformer.class);

	/** 最大変換パス数（無限ループ防止）のデフォルト値 */
	private static final int DEFAULT_MAX_PASSES = 50000;

	/** 最大変換パス数（テスト用に指定可能） */
	public static final int DEFAULT_MAX_PASSES_CONST = 50000;

	/** 最大変換パス数（テスト用に指定可能） */
	private final int maxPasses;

	private final PatternMatcher patternMatcher;
	private final MatchSelectionStrategy selectionStrategy;

	/** 変換エラー情報を記録するリスト */
	private final List<TransformError> errors = new ArrayList<>();

	/** 適用した変換のログ（時系列、レポート出力用） */
	private final List<AppliedTransform> appliedTransforms = new ArrayList<>();

	/** 診断候補（フィルタ無視再マッチで検出した要確認候補） */
	private final List<DiagnosticCandidate> diagnosticCandidates = new ArrayList<>();

	/** 変換過程可視化用一時ファイルのパス（Excel 無効時は null） */
	private Path visualizationTempFile;

	/** MAIN フェーズ各フェーズ完了時のコードスナップショット（phases.html 用） */
	private final List<String> mainPhaseSnapshots = new ArrayList<>();

	/**
	 * 変換ステップ通し番号（ユニット間を通じて連番になるようインスタンス変数で管理）。 {@link #prepareForNewConversion}
	 * でリセットする。
	 */
	private int sequence = 0;

	/**
	 * 変換過程可視化用 Writer（複数ユニットの変換を 1 ファイルに追記するためインスタンス変数）。
	 * {@link #prepareForNewConversion} で生成し、{@link #closeVizWriter} で閉じる。
	 */
	private java.io.Writer vizWriter = null;

	/**
	 * コンストラクタ。デフォルトの最大パス数（50,000）と RightmostFirstSelectionStrategy で初期化する。
	 */
	public Transformer() {
		this(DEFAULT_MAX_PASSES);
	}

	/**
	 * コンストラクタ（テスト用）。最大パス数を指定可能。
	 *
	 * @param maxPasses
	 *            フェーズあたりの最大変換パス数
	 */
	public Transformer(int maxPasses) {
		this(maxPasses, new RightmostFirstSelectionStrategy());
	}

	/**
	 * コンストラクタ。最大パス数とマッチ選択戦略を指定可能。
	 *
	 * @param maxPasses
	 *            フェーズあたりの最大変換パス数
	 * @param selectionStrategy
	 *            マッチ選択戦略（差し替え可能）
	 */
	public Transformer(int maxPasses, MatchSelectionStrategy selectionStrategy) {
		this(maxPasses, selectionStrategy, null);
	}

	/**
	 * コンストラクタ。最大パス数、マッチ選択戦略、ReceiverValidator を指定可能。
	 *
	 * @param maxPasses
	 *            フェーズあたりの最大変換パス数
	 * @param selectionStrategy
	 *            マッチ選択戦略（差し替え可能）
	 * @param receiverValidator
	 *            RECEIVER キャプチャの妥当性検証器（null の場合はプリフィルタのみ使用）
	 */
	public Transformer(int maxPasses, MatchSelectionStrategy selectionStrategy, ReceiverValidator receiverValidator) {
		this.maxPasses = maxPasses;
		this.patternMatcher = new PatternMatcher(receiverValidator);
		this.selectionStrategy = selectionStrategy;
	}

	/**
	 * トークンノード列全体に変換ルールを適用し、C# コードを生成する。 コメントは保持しない（後方互換）。
	 *
	 * @param initialTokenNodes
	 *            初期トークンノード列
	 * @param rules
	 *            適用する変換ルールリスト
	 * @return 変換後の C# コード文字列
	 */
	public String transform(List<AstNode> initialTokenNodes, List<ConversionRule> rules) {
		return transform(initialTokenNodes, rules, Map.of());
	}

	/**
	 * トークンノード列全体に変換ルールを適用し、C# コードを生成する。 変換されなかったトークンに付随するコメントを出力に保持する。
	 *
	 * @param initialTokenNodes
	 *            初期トークンノード列
	 * @param rules
	 *            適用する変換ルールリスト
	 * @param commentsBeforeToken
	 *            ストリームインデックス → そのトークン直前のコメントリスト
	 * @return 変換後の C# コード文字列
	 */
	public String transform(List<AstNode> initialTokenNodes, List<ConversionRule> rules,
			Map<Integer, List<String>> commentsBeforeToken) {
		return transformWithPhases(initialTokenNodes, List.of(rules), commentsBeforeToken, true);
	}

	/**
	 * トークンノード列全体にフェーズ別変換ルールを適用し、C# コードを生成する。 各フェーズのルールを順に適用し、フェーズ内ではマッチがなくなるまで反復する。
	 * フェーズをまたぐ適用順を保証する（例: [01]_ブロックコメント → [02]_標準置き換え）。
	 *
	 * <p>
	 * 変換ループはフラットな {@code List<AstNode>} を直接 Splice（subList 置き換え）して管理する。
	 * </p>
	 *
	 * @param initialTokenNodes
	 *            初期トークンノード列
	 * @param rulesByPhase
	 *            フェーズごとのルールリスト（適用順）
	 * @param commentsBeforeToken
	 *            ストリームインデックス → そのトークン直前のコメントリスト
	 * @return 変換後の C# コード文字列
	 */
	public String transformWithPhases(List<AstNode> initialTokenNodes, List<List<ConversionRule>> rulesByPhase,
			Map<Integer, List<String>> commentsBeforeToken) {
		return transformWithPhases(initialTokenNodes, rulesByPhase, commentsBeforeToken, true);
	}

	/**
	 * トークンノード列全体にフェーズ別変換ルールを適用し、C# コードを生成する。
	 *
	 * @param initialTokenNodes
	 *            初期トークンノード列
	 * @param rulesByPhase
	 *            フェーズごとのルールリスト（適用順）
	 * @param commentsBeforeToken
	 *            ストリームインデックス → そのトークン直前のコメントリスト
	 * @param excelEnabled
	 *            Excel 可視化出力を有効にする場合 true
	 * @return 変換後の C# コード文字列
	 */
	public String transformWithPhases(List<AstNode> initialTokenNodes, List<List<ConversionRule>> rulesByPhase,
			Map<Integer, List<String>> commentsBeforeToken, boolean excelEnabled) {
		List<AstNode> nodes = transformWithPhasesReturnNodes(initialTokenNodes, rulesByPhase, commentsBeforeToken,
				excelEnabled);
		return buildOutput(nodes, commentsBeforeToken);
	}

	/**
	 * トークンノード列全体にフェーズ別変換ルールを適用し、変換後のトークンノード列を返す。
	 *
	 * <p>
	 * {@link #transformWithPhases} と同じ変換ロジックだが、文字列ではなく {@link AstNode}
	 * のリストを返す。3パス構成（pre/main/post フェーズ）で、 フェーズ間に再トークン化が必要な場合に使用する。
	 * </p>
	 *
	 * @param initialTokenNodes
	 *            初期トークンノード列
	 * @param rulesByPhase
	 *            フェーズごとのルールリスト（適用順）
	 * @param commentsBeforeToken
	 *            ストリームインデックス → そのトークン直前のコメントリスト
	 * @param excelEnabled
	 *            Excel 可視化出力を有効にする場合 true
	 * @return 変換後のトークンノード列
	 */
	public List<AstNode> transformWithPhasesReturnNodes(List<AstNode> initialTokenNodes,
			List<List<ConversionRule>> rulesByPhase, Map<Integer, List<String>> commentsBeforeToken,
			boolean excelEnabled) {
		prepareForNewConversion(excelEnabled);
		writeInitialVizState(initialTokenNodes);
		try {
			List<AstNode> result = processUnitReturnNodes(initialTokenNodes, rulesByPhase, commentsBeforeToken);
			runPostTransformScans(result, rulesByPhase);
			return result;
		} finally {
			closeVizWriter();
		}
	}

	/**
	 * 変換セッションを初期化する。
	 *
	 * <p>
	 * ユニットごとの変換ループを始める前に 1 回だけ呼ぶこと。 複数ユニットを処理する場合は、このメソッドを 1 回呼んだ後に
	 * {@link #writeInitialVizState} でファイル全体の初期状態を書き込み、
	 * {@link #processUnitReturnNodes} を各ユニットに対して呼ぶ。
	 * </p>
	 *
	 * @param excelEnabled
	 *            Excel 可視化出力を有効にする場合 true
	 */
	public void prepareForNewConversion(boolean excelEnabled) {
		errors.clear();
		appliedTransforms.clear();
		diagnosticCandidates.clear();
		mainPhaseSnapshots.clear();
		sequence = 0;
		closeVizWriter();
		visualizationTempFile = null;

		if (excelEnabled) {
			try {
				visualizationTempFile = Files.createTempFile("cpp2csharp_", ".dat");
				vizWriter = new OutputStreamWriter(
						new BufferedOutputStream(
								Files.newOutputStream(visualizationTempFile, java.nio.file.StandardOpenOption.APPEND)),
						StandardCharsets.UTF_8);
			} catch (Exception e) {
				LOGGER.warn("変換過程可視化用一時ファイルの作成に失敗しました: {}", e.getMessage());
				visualizationTempFile = null;
			}
		}
	}

	/**
	 * 変換開始前の全ファイル初期状態を可視化ファイルに書き込む（STEP 0）。
	 *
	 * <p>
	 * {@link #prepareForNewConversion} 後、最初の {@link #processUnitReturnNodes} 呼び出し前に
	 * 1 回だけ呼ぶこと。ユニット分割時はファイル全体のトークン列を渡すことで、 Excel の初期列が全体を正しく反映する。
	 * </p>
	 *
	 * @param initialTokenNodes
	 *            ファイル全体の初期トークン列（PRE フェーズ後）
	 */
	public void writeInitialVizState(List<AstNode> initialTokenNodes) {
		if (vizWriter != null && !initialTokenNodes.isEmpty()) {
			try {
				writeVisualizationStep(vizWriter, sequence, initialTokenNodes);
			} catch (Exception e) {
				LOGGER.warn("変換過程の書き込みに失敗しました: {}", e.getMessage());
			}
		}
	}

	/**
	 * 1 つの処理単位に対して変換フェーズを実行し、変換後トークン列を返す。
	 *
	 * <p>
	 * state（errors / appliedTransforms 等）をクリアしない。 複数ユニットを連続で処理する場合は state が累積される。
	 * 呼び出し前に {@link #prepareForNewConversion} を 1 回だけ呼ぶこと。
	 * </p>
	 *
	 * <p>
	 * 可視化ファイルが {@link #prepareForNewConversion} で生成されている場合、
	 * 各ユニットの変換ステップを追記する（ユニット間で連番が続く）。
	 * </p>
	 *
	 * @param initialTokenNodes
	 *            処理対象のトークンノード列（1 ユニット分）
	 * @param rulesByPhase
	 *            フェーズごとのルールリスト
	 * @param commentsBeforeToken
	 *            コメントマップ
	 * @return 変換後のトークンノード列
	 */
	public List<AstNode> processUnitReturnNodes(List<AstNode> initialTokenNodes,
			List<List<ConversionRule>> rulesByPhase, Map<Integer, List<String>> commentsBeforeToken) {
		List<AstNode> tokenNodes = new ArrayList<>(initialTokenNodes);

		for (int phaseIndex = 0; phaseIndex < rulesByPhase.size(); phaseIndex++) {
			List<ConversionRule> phaseRules = rulesByPhase.get(phaseIndex);
			if (phaseRules.isEmpty()) {
				continue;
			}

			MatchSelectionContext selectionContext = createSelectionContext();
			boolean exitedNormally = false;
			for (int pass = 0; pass < maxPasses; pass++) {
				List<String> tokens = tokenNodes.stream().map(AstNode::getText).toList();

				List<MatchResult> allMatches = patternMatcher.matchAll(phaseRules, tokens);
				if (allMatches.isEmpty()) {
					exitedNormally = true;
					break;
				}

				MatchSelectionInput selectionInput = new MatchSelectionInput(allMatches, tokens, selectionContext);
				SelectionDecision decision = selectionStrategy.selectBest(selectionInput);
				if (!decision.hasMatch()) {
					exitedNormally = true;
					break;
				}

				MatchResult best = decision.match();
				sequence++;
				String matchedNode = String.join(" ", tokens.subList(best.getStartIndex(), best.getEndIndex()));
				String transformedTo = best.getExpandedToTemplate();
				String ruleFrom = best.getRule().getFromTokens().stream().map(t -> t.getValue())
						.collect(Collectors.joining(" "));
				int sourceLine = 0;
				String lineBefore = "";
				String lineAfter = "";
				if (best.getStartIndex() < tokenNodes.size()) {
					sourceLine = tokenNodes.get(best.getStartIndex()).getLine();
					if (sourceLine > 0) {
						lineBefore = buildLineContent(tokenNodes, sourceLine, -1, -1, null);
						lineAfter = buildLineContent(tokenNodes, sourceLine, best.getStartIndex(), best.getEndIndex(),
								transformedTo);
					}
				}
				String strategyLabel = decision.fallbackUsed()
						? decision.selectedStrategy() + ":fallback_from_" + decision.fallbackFromStrategy()
						: decision.selectedStrategy();
				int startIdx = best.getStartIndex();
				int endIdx = best.getEndIndex();
				List<Integer> mergedIdsList = tokenNodes.subList(startIdx, endIdx).stream().map(AstNode::getId)
						.toList();
				appliedTransforms.add(new AppliedTransform(sequence, phaseIndex, best.getRule().getSourceFile(),
						ruleFrom, best.getRule().getToTemplate(), matchedNode, transformedTo, sourceLine, lineBefore,
						lineAfter, decision.selectedStrategy(), decision.fallbackFromStrategy(),
						decision.reasonSummary(), decision.selectionDetails(), startIdx, endIdx, mergedIdsList));

				tokenNodes = splice(tokenNodes, best);
				if (vizWriter != null) {
					try {
						writeVisualizationStep(vizWriter, sequence, tokenNodes);
					} catch (Exception e) {
						LOGGER.warn("変換過程の書き込みに失敗しました: {}", e.getMessage());
					}
				}

				LOGGER.info(String.format("変換適用 [フェーズ%d][%s]: [%s] → [%s]", phaseIndex + 1, strategyLabel, matchedNode,
						best.getRule().getToTemplate()));
				if (decision.selectionDetails() != null && !decision.selectionDetails().isEmpty()) {
					for (String line : decision.selectionDetails().split("\n")) {
						LOGGER.info("  " + line);
					}
				}
			}

			if (!exitedNormally) {
				List<String> remainingTokens = tokenNodes.stream().map(AstNode::getText).toList();
				List<MatchResult> remainingMatches = patternMatcher.matchAll(phaseRules, remainingTokens);
				if (!remainingMatches.isEmpty()) {
					String msg = String.format("パス上限 (%d) に到達しました。%d 件のマッチが未変換です。", maxPasses, remainingMatches.size());
					errors.add(new TransformError(TransformError.ErrorType.PASS_LIMIT_REACHED, msg, remainingMatches));
					LOGGER.error(msg);
				}
			}
			mainPhaseSnapshots.add(buildOutput(tokenNodes, commentsBeforeToken));
		}

		return tokenNodes;
	}

	/**
	 * 全ユニットの変換完了後に診断スキャンとニアミススキャンを実行する。
	 *
	 * <p>
	 * 引数 {@code finalNodes} は全ユニットの変換結果を結合したトークン列、 {@code allPhases}
	 * は絞り込み前の全ルールセット（絞り込みによる漏れを検出するため）。
	 * </p>
	 *
	 * @param finalNodes
	 *            変換完了後の全トークン列（ユニット結合済み）
	 * @param allPhases
	 *            全フェーズのルールリスト（未絞り込み）
	 */
	public void runPostTransformScans(List<AstNode> finalNodes, List<List<ConversionRule>> allPhases) {
		List<String> tokens = finalNodes.stream().map(AstNode::getText).toList();
		runDiagnosticScan(finalNodes, allPhases, tokens);
		runNearMissScan(finalNodes, allPhases, tokens);
	}

	/**
	 * 可視化 Writer を閉じる。 {@link #prepareForNewConversion} と対になる。MainPhase の
	 * try-finally で呼ぶこと。
	 */
	public void closeVizWriter() {
		if (vizWriter != null) {
			try {
				vizWriter.close();
			} catch (Exception e) {
				LOGGER.warn("変換過程可視化用ファイルのクローズに失敗しました: {}", e.getMessage());
			}
			vizWriter = null;
		}
	}

	/**
	 * 変換過程可視化用一時ファイルのパスを返す。
	 *
	 * @return 一時ファイルのパス。Excel 無効時は null
	 */
	public Path getVisualizationTempFile() {
		return visualizationTempFile;
	}

	/**
	 * MAIN フェーズ各フェーズ完了時のコードスナップショットを返す（phases.html 用）。
	 *
	 * @return フェーズ番号順のコードスナップショットリスト
	 */
	public List<String> getMainPhaseSnapshots() {
		return List.copyOf(mainPhaseSnapshots);
	}

	/**
	 * 1 ステップ分のトークン列を可視化用ファイルに追記する。 フォーマット: ---STEP N--- の後に 1行1トークンで
	 * id\ttext（改行・タブはエスケープ）
	 */
	private void writeVisualizationStep(Writer writer, int stepIndex, List<AstNode> tokenNodes)
			throws java.io.IOException {
		writer.write("---STEP ");
		writer.write(String.valueOf(stepIndex));
		writer.write("---\n");
		for (AstNode node : tokenNodes) {
			writer.write(String.valueOf(node.getId()));
			writer.write("\t");
			writer.write(escapeForVisualization(node.getText()));
			writer.write("\n");
		}
		writer.flush();
	}

	private static String escapeForVisualization(String text) {
		if (text == null)
			return "";
		return text.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r");
	}

	/**
	 * 全フェーズ完了後に、フィルタ無視で全ルールを再マッチし、診断候補を収集する。 変換は行わず、レポート用データのみ記録する。
	 */
	private void runDiagnosticScan(List<AstNode> tokenNodes, List<List<ConversionRule>> rulesByPhase,
			List<String> tokens) {
		List<ConversionRule> allRules = rulesByPhase.stream().flatMap(List::stream).toList();
		if (allRules.isEmpty()) {
			return;
		}
		List<MatchResult> matches = patternMatcher.matchAllForDiagnostic(allRules, tokens);
		Set<String> seen = new LinkedHashSet<>();
		for (MatchResult m : matches) {
			int start = m.getStartIndex();
			int end = m.getEndIndex();
			if (start >= tokenNodes.size()) {
				continue;
			}
			int lineNumber = tokenNodes.get(start).getLine();
			String ruleSource = m.getRule().getSourceFile();
			String ruleFrom = m.getRule().getFromTokens().stream().map(t -> t.getValue())
					.collect(Collectors.joining(" "));
			String expandedTo = m.getExpandedToTemplate();
			String matchedText = String.join(" ", tokens.subList(start, Math.min(end, tokens.size())));
			String lineContent = lineNumber > 0 ? buildLineContent(tokenNodes, lineNumber, -1, -1, null) : "";
			String dedupKey = lineNumber + "|" + start + "|" + end + "|" + ruleSource + "|" + ruleFrom;
			if (seen.contains(dedupKey)) {
				continue;
			}
			seen.add(dedupKey);
			diagnosticCandidates.add(new DiagnosticCandidate(lineNumber, ruleSource, ruleFrom, expandedTo, matchedText,
					lineContent, "still_matchable_after_all_phases"));
		}
	}

	/**
	 * A1 ニアミス候補収集: 全フェーズ完了後に、あと1トークンで一致するルールを収集する。
	 *
	 * <p>
	 * <strong>なぜ追加したか</strong>: ルールを書いたが期待どおりに発火しない場合、
	 * 「パターンのどの位置で不一致だったか」が分かれば修正コストが大幅に下がる。
	 * 通常変換パスへの影響を避けるため、{@link #runDiagnosticScan} 後に呼ぶ。
	 * パターン末尾1トークン手前まで一致したルール・位置をニアミス候補として記録する。
	 * </p>
	 */
	private void runNearMissScan(List<AstNode> tokenNodes, List<List<ConversionRule>> rulesByPhase,
			List<String> tokens) {
		List<ConversionRule> allRules = rulesByPhase.stream().flatMap(List::stream).toList();
		if (allRules.isEmpty()) {
			return;
		}
		Set<String> seen = new LinkedHashSet<>();

		for (ConversionRule rule : allRules) {
			int patternSize = rule.getFromTokens().size();
			if (patternSize < 2) {
				continue; // 1トークンルールはニアミス判定不可
			}
			for (int startIdx = 0; startIdx < tokens.size(); startIdx++) {
				int depth = patternMatcher.tryMatchGetDepth(rule, tokens, startIdx);
				// ニアミス条件: 末尾1トークン手前まで一致 (depth == patternSize - 1)
				// かつ最低2トークン以上一致している
				if (depth < 2 || depth != patternSize - 1) {
					continue;
				}
				int lineNumber = startIdx < tokenNodes.size() ? tokenNodes.get(startIdx).getLine() : 0;
				String ruleSource = rule.getSourceFile();
				String ruleFrom = rule.getFromTokens().stream().map(t -> t.getValue()).collect(Collectors.joining(" "));
				// depth 位置のパターントークンが期待トークン
				ConversionToken patAtDepth = rule.getFromTokens().get(depth);
				String expectedToken = patAtDepth.isAbstractParam() || patAtDepth.isReceiverParam()
						? null
						: patAtDepth.getValue();
				String dedupKey = "nearmiss|" + lineNumber + "|" + startIdx + "|" + ruleSource + "|" + depth;
				if (seen.contains(dedupKey)) {
					continue;
				}
				seen.add(dedupKey);
				diagnosticCandidates.add(new DiagnosticCandidate(lineNumber, ruleSource, ruleFrom, "", "", "",
						"near_miss", depth, expectedToken, null));
			}
		}
	}

	/**
	 * マッチ範囲のトークンを置換テキスト 1 ノードで置き換えた新しいリストを返す（リスト Splice）。
	 *
	 * <p>
	 * 先頭マッチトークンの streamIndex・行・列を継承することで、 コメント復元とレポート行表示を維持する。
	 * </p>
	 */
	private List<AstNode> splice(List<AstNode> tokenNodes, MatchResult match) {
		int start = match.getStartIndex();
		int end = match.getEndIndex();
		AstNode firstMatched = tokenNodes.get(start);
		int streamIndex = firstMatched.getStreamIndex();
		int line = firstMatched.getLine();
		int column = firstMatched.getColumn();
		int id = firstMatched.getId();

		String expanded = match.getExpandedToTemplate();
		List<AstNode> result = new ArrayList<>(tokenNodes.size() - (end - start) + 1);
		result.addAll(tokenNodes.subList(0, start));
		if (expanded != null && !expanded.isEmpty()) {
			result.add(AstNode.tokenNodeWithId(expanded, line, column, id, streamIndex));
		}
		result.addAll(tokenNodes.subList(end, tokenNodes.size()));
		return result;
	}

	/**
	 * マッチ選択戦略が曖昧マッチを報告するためのコンテキストを生成する。
	 */
	private MatchSelectionContext createSelectionContext() {
		return (ambiguousMatches, contextTokens) -> {
			String msg = String.format("曖昧なマッチが %d 件見つかりました (変換を行いません): %s", ambiguousMatches.size(), contextTokens);
			LOGGER.error(msg);
			errors.add(new TransformError(TransformError.ErrorType.AMBIGUOUS_MATCH, msg, ambiguousMatches));
		};
	}

	/**
	 * トークンノード列とコメントマップからコード文字列を構築する。
	 *
	 * <p>
	 * 置換由来のトークン（streamIndex=-1）が連続する場合はスペースを挟まず連結する。 これにより {@code Math.Sin(x)} が
	 * {@code Math . Sin ( x )} ではなく正しく出力される。
	 * </p>
	 *
	 * <p>
	 * {@code <EOF>} トークンは出力に含めない（タスク3）。
	 * </p>
	 *
	 * <p>
	 * このメソッドは {@code CppToCSharpConverter} 等の外部クラスから呼び出し可能。
	 * </p>
	 */
	public String buildOutput(List<AstNode> tokenNodes, Map<Integer, List<String>> commentsBeforeToken) {
		if (commentsBeforeToken.isEmpty()) {
			return joinWithReplacementGrouping(tokenNodes);
		}

		StringBuilder sb = new StringBuilder();
		int i = 0;
		while (i < tokenNodes.size()) {
			AstNode node = tokenNodes.get(i);
			int streamIdx = node.getStreamIndex();
			if (streamIdx >= 0) {
				List<String> comments = commentsBeforeToken.get(streamIdx);
				if (comments != null) {
					for (String item : comments) {
						if (isNewlineToken(item) || isWhitespaceToken(item)) {
							sb.append(item);
						} else {
							if (sb.length() > 0 && !Character.isWhitespace(sb.charAt(sb.length() - 1)))
								sb.append(" ");
							sb.append(item);
						}
					}
				}
			}
			int groupEnd = findReplacementGroupEnd(tokenNodes, i);
			String groupText = joinTokenGroup(tokenNodes, i, groupEnd);
			if (!"<EOF>".equals(groupText)) {
				sb.append(groupText);
			}
			i = groupEnd;
		}
		return sb.toString();
	}

	/**
	 * 置換トークン（streamIndex=-1）の連続区間の終端を返す。 置換でないトークンの場合は自身のみ。
	 */
	private int findReplacementGroupEnd(List<AstNode> tokenNodes, int start) {
		if (tokenNodes.get(start).getStreamIndex() >= 0) {
			return start + 1;
		}
		int i = start + 1;
		while (i < tokenNodes.size() && tokenNodes.get(i).getStreamIndex() < 0) {
			i++;
		}
		return i;
	}

	private String joinTokenGroup(List<AstNode> tokenNodes, int start, int end) {
		if (end - start == 1) {
			return tokenNodes.get(start).getText();
		}
		StringBuilder sb = new StringBuilder();
		for (int i = start; i < end; i++) {
			sb.append(tokenNodes.get(i).getText());
		}
		return sb.toString();
	}

	/**
	 * トークンを、置換グループ単位で結合する。 置換由来の連続トークンはスペースなし、それ以外はスペース区切り。 {@code <EOF>}
	 * トークンは出力に含めない。
	 */
	private String joinWithReplacementGrouping(List<AstNode> tokenNodes) {
		StringBuilder sb = new StringBuilder();
		int i = 0;
		while (i < tokenNodes.size()) {
			int groupEnd = findReplacementGroupEnd(tokenNodes, i);
			String groupText = joinTokenGroup(tokenNodes, i, groupEnd);
			if (!"<EOF>".equals(groupText)) {
				if (sb.length() > 0)
					sb.append(" ");
				sb.append(groupText);
			}
			i = groupEnd;
		}
		return sb.toString();
	}

	/**
	 * 指定行のトークンを結合して行内容を構築する。
	 *
	 * @param tokenNodes
	 *            トークンノード列
	 * @param lineNum
	 *            行番号
	 * @param replaceStart
	 *            置換範囲（開始、-1 の場合は置換なし）
	 * @param replaceEnd
	 *            置換範囲（終了、exclusive）
	 * @param replacement
	 *            置換テキスト
	 * @return 行の文字列（スペース区切り）
	 */
	private String buildLineContent(List<AstNode> tokenNodes, int lineNum, int replaceStart, int replaceEnd,
			String replacement) {
		List<String> parts = new ArrayList<>();
		int i = 0;
		while (i < tokenNodes.size()) {
			AstNode node = tokenNodes.get(i);
			if (node.getLine() != lineNum) {
				i++;
				continue;
			}
			if (replaceStart >= 0 && replaceStart <= i && i < replaceEnd) {
				if (i == replaceStart) {
					parts.add(replacement);
				}
				i++;
				continue;
			}
			String text = node.getText();
			if (!"<EOF>".equals(text)) {
				parts.add(text);
			}
			i++;
		}
		return String.join(" ", parts);
	}

	private static boolean isNewlineToken(String text) {
		return "\n".equals(text) || "\r\n".equals(text) || "\r".equals(text);
	}

	private static boolean endsWithWhitespace(StringBuilder sb) {
		if (sb.length() == 0)
			return false;
		char c = sb.charAt(sb.length() - 1);
		return c == ' ' || c == '\t' || c == '\n' || c == '\r';
	}

	private static boolean isWhitespaceToken(String text) {
		if (text == null || text.isEmpty())
			return false;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) != ' ' && text.charAt(i) != '\t')
				return false;
		}
		return true;
	}

	/**
	 * 変換処理中に記録されたエラー情報のリストを返す。
	 *
	 * @return エラーリスト
	 */
	public List<TransformError> getErrors() {
		return List.copyOf(errors);
	}

	/**
	 * 適用した変換のログを時系列で返す。
	 *
	 * @return 適用ログ（レポート出力用）
	 */
	public List<AppliedTransform> getAppliedTransforms() {
		return List.copyOf(appliedTransforms);
	}

	/**
	 * 診断候補リストを返す。
	 *
	 * @return 診断候補（フィルタ無視再マッチで検出した要確認候補）
	 */
	public List<DiagnosticCandidate> getDiagnosticCandidates() {
		return List.copyOf(diagnosticCandidates);
	}

	/**
	 * 変換エラー情報を保持する内部クラス。
	 */
	public static final class TransformError {

		/** エラー種別 */
		public enum ErrorType {
			/** 真の曖昧マッチ（特異性が同等で2件以上のマッチ） */
			AMBIGUOUS_MATCH,
			/** パス上限到達（変換しきれずに打ち切り） */
			PASS_LIMIT_REACHED
		}

		private final ErrorType errorType;
		private final String message;
		private final List<MatchResult> relatedMatches;

		/**
		 * コンストラクタ。
		 *
		 * @param errorType
		 *            エラー種別
		 * @param message
		 *            エラーメッセージ
		 * @param relatedMatches
		 *            関連するマッチ結果
		 */
		public TransformError(ErrorType errorType, String message, List<MatchResult> relatedMatches) {
			this.errorType = errorType;
			this.message = message;
			this.relatedMatches = List.copyOf(relatedMatches);
		}

		/** エラー種別を返す */
		public ErrorType getErrorType() {
			return errorType;
		}

		/** エラーメッセージを返す */
		public String getMessage() {
			return message;
		}

		/** 関連マッチ結果を返す */
		public List<MatchResult> getRelatedMatches() {
			return relatedMatches;
		}

		@Override
		public String toString() {
			return String.format("TransformError{type=%s, msg='%s'}", errorType, message);
		}
	}
}
