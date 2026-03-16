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

import io.github.takahino.cpp2csharp.mrule.MultiReplaceRule;
import io.github.takahino.cpp2csharp.multi.MultiReplaceTransformer;
import io.github.takahino.cpp2csharp.retokenize.RetokenizeResult;
import io.github.takahino.cpp2csharp.retokenize.Retokenizer;
import io.github.takahino.cpp2csharp.transform.Transformer;
import io.github.takahino.cpp2csharp.tree.AstNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * PRE / POST フェーズの実装。{@code .mrule} を 1 フェーズ分適用する。
 *
 * <p>
 * {@code prependRetokenize=true} を指定すると、変換前に追加の再トークン化を実行する。 これは MAIN→POST
 * 境界での再トークン化（最初の POST フェーズのみ必要）に使用する。
 * </p>
 */
public class PrePostPhase implements ConversionPhase {

	private static final Logger LOGGER = LoggerFactory.getLogger(PrePostPhase.class);

	private final String phaseName;
	private final int phaseNum;
	private final List<MultiReplaceRule> rules;
	private final boolean prependRetokenize;
	private final Transformer transformer;

	/**
	 * @param phaseName
	 *            "PRE" または "POST"
	 * @param phaseNum
	 *            1 始まりのフェーズ番号
	 * @param rules
	 *            このフェーズで適用するルール群
	 * @param prependRetokenize
	 *            変換前に追加の再トークン化を行う場合 true（最初の POST フェーズのみ）
	 * @param transformer
	 *            {@code buildOutput()} 呼び出し用（状態は変更しない）
	 */
	public PrePostPhase(String phaseName, int phaseNum, List<MultiReplaceRule> rules, boolean prependRetokenize,
			Transformer transformer) {
		this.phaseName = phaseName;
		this.phaseNum = phaseNum;
		this.rules = rules;
		this.prependRetokenize = prependRetokenize;
		this.transformer = transformer;
	}

	@Override
	public String name() {
		return phaseName + "-" + phaseNum;
	}

	@Override
	public PhaseExecutionResult execute(PhaseExecutionContext ctx) {
		LOGGER.info("{} フェーズ {} 開始", phaseName, phaseNum);

		List<AstNode> tokenNodes = ctx.tokenNodes();
		Map<Integer, List<String>> commentsBeforeToken = ctx.commentsBeforeToken();

		Retokenizer retokenizer = new Retokenizer();
		MultiReplaceTransformer mruleTransformer = new MultiReplaceTransformer();

		// MAIN→POST 境界の追加再トークン化（最初の POST フェーズのみ）
		if (prependRetokenize) {
			RetokenizeResult rt = retokenizer.retokenize(tokenNodes, commentsBeforeToken);
			tokenNodes = rt.tokenNodes();
			commentsBeforeToken = rt.commentsBeforeToken();
		}

		tokenNodes = mruleTransformer.transformPhase(tokenNodes, rules, phaseName, phaseNum);
		List<PhaseTransformLog> logs = List.copyOf(mruleTransformer.getLogs());

		RetokenizeResult rt = retokenizer.retokenize(tokenNodes, commentsBeforeToken);
		tokenNodes = rt.tokenNodes();
		commentsBeforeToken = rt.commentsBeforeToken();

		String code = transformer.buildOutput(tokenNodes, commentsBeforeToken);
		List<PhaseSnapshot> snapshots = List.of(new PhaseSnapshot(phaseName + "-" + phaseNum, code));

		LOGGER.info("{} フェーズ {} 完了: {} トークン", phaseName, phaseNum, tokenNodes.size());

		return new PhaseExecutionResult(tokenNodes, commentsBeforeToken, code, snapshots, logs, List.of(), List.of());
	}
}
