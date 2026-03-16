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

import io.github.takahino.cpp2csharp.converter.PhaseTransformLog;

import java.util.List;

/**
 * COMBYエンジンの差し替えポイントインターフェイス。
 *
 * <p>
 * 現行実装: {@link CombyTransformer}（インプロセス独自実装）。 将来実装:
 * {@link CliCombyEngine}（comby CLI サブプロセス呼び出し）。
 * </p>
 *
 * <p>
 * {@link io.github.takahino.cpp2csharp.converter.CppToCSharpConverter} は
 * このインターフェイスを通じてエンジンを使用するため、comby CLI への切り替えは コンストラクタ引数を変えるだけで実現できる。
 * </p>
 */
public interface CombyEngine {

	/**
	 * 1フェーズ分のルール群をソーステキストに適用し、変換後テキストを返す。
	 *
	 * @param source
	 *            変換対象テキスト
	 * @param rules
	 *            適用するルールリスト
	 * @param phaseNum
	 *            フェーズ番号（1始まり）
	 * @return 変換後テキスト
	 */
	String transformPhase(String source, List<CombyRule> rules, int phaseNum);

	/**
	 * 複数フェーズを順に適用し、変換後テキストを返す。
	 *
	 * @param source
	 *            変換対象テキスト
	 * @param phases
	 *            フェーズごとのルールリスト
	 * @return 変換後テキスト
	 */
	String transformPhases(String source, List<List<CombyRule>> phases);

	/**
	 * 直近フェーズの適用ログを返す。
	 *
	 * @return 適用ログリスト（読み取り専用コピー）
	 */
	List<PhaseTransformLog> getLogs();
}
