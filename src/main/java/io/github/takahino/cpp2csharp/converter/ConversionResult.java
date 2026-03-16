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

import io.github.takahino.cpp2csharp.transform.AppliedTransform;
import io.github.takahino.cpp2csharp.transform.DiagnosticCandidate;
import io.github.takahino.cpp2csharp.transform.Transformer.TransformError;

import java.nio.file.Path;
import java.util.List;

/**
 * C++ → C# 変換の結果を保持するクラス。
 *
 * <p>
 * 変換後の C# コード、変換エラー情報、パースエラー情報、診断候補を保持する。
 * </p>
 */
public final class ConversionResult {

	/** 変換後の C# コード文字列 */
	private final String csCode;

	/** 変換エラーリスト (曖昧マッチなど) */
	private final List<TransformError> transformErrors;

	/** ANTLR パースエラーリスト */
	private final List<String> parseErrors;

	/** 変換前 AST の木ダンプ（ルール設計デバッグ用、null の場合は未生成） */
	private final String initialTreeDump;

	/** 適用した変換のログ（時系列、レポート出力用） */
	private final List<AppliedTransform> appliedTransforms;

	/** 診断候補リスト（フィルタ無視再マッチで検出した要確認候補） */
	private final List<DiagnosticCandidate> diagnosticCandidates;

	/** 変換過程可視化用一時ファイルのパス（Excel 無効時は null） */
	private final Path visualizationTempFile;

	/** PRE/POST/COMBY フェーズの適用ログ */
	private final List<PhaseTransformLog> phaseTransformLogs;

	/** フェーズ変換ジャーニースナップショット（phases.html 用） */
	private final List<PhaseSnapshot> phaseSnapshots;

	/** MAIN フェーズ入力のユニット別ソーステキスト（デバッグ用、空リストは出力なし） */
	private final List<String> unitSourceDumps;

	/** MAIN フェーズ全ユニットの変換後テキスト（_units/basename_N.cs.txt 出力用） */
	private final List<String> unitOutputDumps;

	/** body ユニットのみの関数単位エントリ（basename.json 出力用） */
	private final List<FunctionUnitEntry> functionUnitEntries;

	/**
	 * コンストラクタ（後方互換）。診断候補・フェーズログは空リストで初期化する。
	 */
	public ConversionResult(String csCode, List<TransformError> transformErrors, List<String> parseErrors,
			String initialTreeDump, List<AppliedTransform> appliedTransforms) {
		this(csCode, transformErrors, parseErrors, initialTreeDump, appliedTransforms, null, null, List.of(),
				List.of());
	}

	/**
	 * コンストラクタ。
	 *
	 * @param csCode
	 *            変換後の C# コード
	 * @param transformErrors
	 *            変換エラーリスト
	 * @param parseErrors
	 *            パースエラーリスト
	 * @param initialTreeDump
	 *            変換前 AST の木ダンプ（null 可）
	 * @param appliedTransforms
	 *            適用した変換のログ（null の場合は空リスト）
	 * @param diagnosticCandidates
	 *            診断候補リスト（null の場合は空リスト）
	 */
	public ConversionResult(String csCode, List<TransformError> transformErrors, List<String> parseErrors,
			String initialTreeDump, List<AppliedTransform> appliedTransforms,
			List<DiagnosticCandidate> diagnosticCandidates) {
		this(csCode, transformErrors, parseErrors, initialTreeDump, appliedTransforms, diagnosticCandidates, null,
				List.of(), List.of());
	}

	/**
	 * コンストラクタ（可視化用一時ファイル付き）。
	 *
	 * @param visualizationTempFile
	 *            変換過程可視化用一時ファイルのパス（null の場合は Excel 出力しない）
	 */
	public ConversionResult(String csCode, List<TransformError> transformErrors, List<String> parseErrors,
			String initialTreeDump, List<AppliedTransform> appliedTransforms,
			List<DiagnosticCandidate> diagnosticCandidates, Path visualizationTempFile) {
		this(csCode, transformErrors, parseErrors, initialTreeDump, appliedTransforms, diagnosticCandidates,
				visualizationTempFile, List.of(), List.of());
	}

	/**
	 * フルコンストラクタ（フェーズ変換ログ付き）。
	 *
	 * @param phaseTransformLogs
	 *            PRE/POST/COMBY フェーズ適用ログ
	 */
	public ConversionResult(String csCode, List<TransformError> transformErrors, List<String> parseErrors,
			String initialTreeDump, List<AppliedTransform> appliedTransforms,
			List<DiagnosticCandidate> diagnosticCandidates, Path visualizationTempFile,
			List<PhaseTransformLog> phaseTransformLogs) {
		this(csCode, transformErrors, parseErrors, initialTreeDump, appliedTransforms, diagnosticCandidates,
				visualizationTempFile, phaseTransformLogs, List.of());
	}

	/**
	 * フルコンストラクタ（フェーズ変換ジャーニースナップショット付き）。
	 *
	 * @param phaseTransformLogs
	 *            PRE/POST/COMBY フェーズ適用ログ
	 * @param phaseSnapshots
	 *            フェーズ変換ジャーニースナップショット（phases.html 用）
	 */
	public ConversionResult(String csCode, List<TransformError> transformErrors, List<String> parseErrors,
			String initialTreeDump, List<AppliedTransform> appliedTransforms,
			List<DiagnosticCandidate> diagnosticCandidates, Path visualizationTempFile,
			List<PhaseTransformLog> phaseTransformLogs, List<PhaseSnapshot> phaseSnapshots) {
		this(csCode, transformErrors, parseErrors, initialTreeDump, appliedTransforms, diagnosticCandidates,
				visualizationTempFile, phaseTransformLogs, phaseSnapshots, List.of());
	}

	/**
	 * フルコンストラクタ（ユニット分割デバッグダンプ付き）。委譲用。
	 *
	 * @param unitSourceDumps
	 *            MAIN フェーズ入力ユニット別ソーステキスト（デバッグ用）
	 */
	public ConversionResult(String csCode, List<TransformError> transformErrors, List<String> parseErrors,
			String initialTreeDump, List<AppliedTransform> appliedTransforms,
			List<DiagnosticCandidate> diagnosticCandidates, Path visualizationTempFile,
			List<PhaseTransformLog> phaseTransformLogs, List<PhaseSnapshot> phaseSnapshots,
			List<String> unitSourceDumps) {
		this(csCode, transformErrors, parseErrors, initialTreeDump, appliedTransforms, diagnosticCandidates,
				visualizationTempFile, phaseTransformLogs, phaseSnapshots, unitSourceDumps, List.of(), List.of());
	}

	/**
	 * フルコンストラクタ（変換後ユニットダンプ・JSON エントリ付き）。
	 *
	 * @param unitSourceDumps
	 *            MAIN フェーズ入力ユニット別ソーステキスト（デバッグ用）
	 * @param unitOutputDumps
	 *            MAIN フェーズ全ユニット変換後テキスト（_units/basename_N.cs.txt 用）
	 * @param functionUnitEntries
	 *            body ユニットのみの関数単位エントリ（basename.json 用）
	 */
	public ConversionResult(String csCode, List<TransformError> transformErrors, List<String> parseErrors,
			String initialTreeDump, List<AppliedTransform> appliedTransforms,
			List<DiagnosticCandidate> diagnosticCandidates, Path visualizationTempFile,
			List<PhaseTransformLog> phaseTransformLogs, List<PhaseSnapshot> phaseSnapshots,
			List<String> unitSourceDumps, List<String> unitOutputDumps, List<FunctionUnitEntry> functionUnitEntries) {
		this.csCode = csCode;
		this.transformErrors = List.copyOf(transformErrors);
		this.parseErrors = List.copyOf(parseErrors);
		this.initialTreeDump = initialTreeDump;
		this.appliedTransforms = appliedTransforms != null ? List.copyOf(appliedTransforms) : List.of();
		this.diagnosticCandidates = diagnosticCandidates != null ? List.copyOf(diagnosticCandidates) : List.of();
		this.visualizationTempFile = visualizationTempFile;
		this.phaseTransformLogs = phaseTransformLogs != null ? List.copyOf(phaseTransformLogs) : List.of();
		this.phaseSnapshots = phaseSnapshots != null ? List.copyOf(phaseSnapshots) : List.of();
		this.unitSourceDumps = unitSourceDumps != null ? List.copyOf(unitSourceDumps) : List.of();
		this.unitOutputDumps = unitOutputDumps != null ? List.copyOf(unitOutputDumps) : List.of();
		this.functionUnitEntries = functionUnitEntries != null ? List.copyOf(functionUnitEntries) : List.of();
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
	 * PRE/POST/COMBY フェーズの適用ログを返す。
	 *
	 * @return フェーズ適用ログリスト
	 */
	public List<PhaseTransformLog> getPhaseTransformLogs() {
		return phaseTransformLogs;
	}

	/**
	 * フェーズ変換ジャーニースナップショットを返す（phases.html 用）。
	 *
	 * @return スナップショットリスト
	 */
	public List<PhaseSnapshot> getPhaseSnapshots() {
		return phaseSnapshots;
	}

	/**
	 * 変換前 AST の木ダンプを返す。
	 *
	 * @return 木ダンプ文字列。未生成の場合は null
	 */
	public String getInitialTreeDump() {
		return initialTreeDump;
	}

	/**
	 * 適用した変換のログを時系列で返す。
	 *
	 * @return 適用ログ（レポート出力用）
	 */
	public List<AppliedTransform> getAppliedTransforms() {
		return appliedTransforms;
	}

	/**
	 * 診断候補リストを返す。
	 *
	 * @return 診断候補（フィルタ無視再マッチで検出した要確認候補）
	 */
	public List<DiagnosticCandidate> getDiagnosticCandidates() {
		return diagnosticCandidates;
	}

	/**
	 * 変換後の C# コード文字列を返す。
	 *
	 * @return C# コード文字列
	 */
	public String getCsCode() {
		return csCode;
	}

	/**
	 * 変換エラーリストを返す。空の場合はエラーなし。
	 *
	 * @return 変換エラーリスト
	 */
	public List<TransformError> getTransformErrors() {
		return transformErrors;
	}

	/**
	 * ANTLR パースエラーリストを返す。空の場合はパースエラーなし。
	 *
	 * @return パースエラーリスト
	 */
	public List<String> getParseErrors() {
		return parseErrors;
	}

	/**
	 * MAIN フェーズ入力ユニット別ソーステキストを返す（デバッグ用）。 空リストの場合はユニットダンプが生成されていない。
	 *
	 * @return ユニット別ソーステキストリスト（1始まり連番に対応）
	 */
	public List<String> getUnitSourceDumps() {
		return unitSourceDumps;
	}

	/**
	 * MAIN フェーズ全ユニットの変換後テキストを返す（_units/basename_N.cs.txt 出力用）。 空リストの場合は出力なし。
	 *
	 * @return 全ユニット変換後テキストリスト（N 番号は .cpp.txt と同一）
	 */
	public List<String> getUnitOutputDumps() {
		return unitOutputDumps;
	}

	/**
	 * body ユニットのみの関数単位エントリを返す（basename.json 出力用）。 空リストの場合は出力なし。
	 *
	 * @return FunctionUnitEntry リスト
	 */
	public List<FunctionUnitEntry> getFunctionUnitEntries() {
		return functionUnitEntries;
	}

	/**
	 * 変換が成功したかどうかを返す。 パースエラーおよび変換エラーがともに0件の場合に成功とみなす。
	 *
	 * @return エラーがなければ true
	 */
	public boolean isSuccess() {
		return parseErrors.isEmpty() && transformErrors.isEmpty();
	}

	@Override
	public String toString() {
		return String.format("ConversionResult{parseErrors=%d, transformErrors=%d, code='%s'}", parseErrors.size(),
				transformErrors.size(), csCode.length() > 80 ? csCode.substring(0, 80) + "..." : csCode);
	}
}
