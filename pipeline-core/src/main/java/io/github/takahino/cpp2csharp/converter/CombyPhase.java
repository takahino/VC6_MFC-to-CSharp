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

import io.github.takahino.cpp2csharp.comby.CombyEngine;
import io.github.takahino.cpp2csharp.comby.CombyRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * COMBY フェーズの実装。テキストレベルのパターン置換を行う。
 *
 * <p>
 * {@link CombyEngine} は全 COMBY フェーズ間で共有されるため、 ログは実行前後のサイズ差分で取得する。
 * </p>
 */
public class CombyPhase implements ConversionPhase {

	private static final Logger LOGGER = LoggerFactory.getLogger(CombyPhase.class);

	private final int phaseNum;
	private final List<CombyRule> rules;
	private final CombyEngine combyEngine;

	/**
	 * @param phaseNum
	 *            1 始まりのフェーズ番号
	 * @param rules
	 *            このフェーズで適用する COMBY ルール群
	 * @param combyEngine
	 *            COMBY エンジン（converter と共有）
	 */
	public CombyPhase(int phaseNum, List<CombyRule> rules, CombyEngine combyEngine) {
		this.phaseNum = phaseNum;
		this.rules = rules;
		this.combyEngine = combyEngine;
	}

	@Override
	public String name() {
		return "COMBY-" + phaseNum;
	}

	@Override
	public PhaseExecutionResult execute(PhaseExecutionContext ctx) {
		LOGGER.info("COMBY フェーズ {} 開始", phaseNum);

		// getLogs() は全 COMBY フェーズ累積なのでサイズ差分でこのフェーズ分を取得
		int logSizeBefore = combyEngine.getLogs().size();

		String code = combyEngine.transformPhase(ctx.currentCode(), rules, phaseNum);

		int logSizeAfter = combyEngine.getLogs().size();
		List<PhaseTransformLog> logs = List.copyOf(combyEngine.getLogs().subList(logSizeBefore, logSizeAfter));

		List<PhaseSnapshot> snapshots = List.of(new PhaseSnapshot("COMBY-" + phaseNum, code));

		LOGGER.info("COMBY フェーズ {} 完了", phaseNum);

		// テキスト系フェーズはトークンノードを更新しない（null を返して前フェーズのものを引き継ぐ）
		return new PhaseExecutionResult(null, ctx.commentsBeforeToken(), code, snapshots, logs, List.of(), List.of());
	}
}
