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

import io.github.takahino.cpp2csharp.tree.AstNode;

import java.util.List;
import java.util.Map;

/**
 * フェーズ実行後の結果。次フェーズの {@link PhaseExecutionContext} 構築に使用される。
 *
 * @param tokenNodes           更新済みトークンノード列。テキスト系フェーズ（COMBY 等）では null を返し、
 *                             前フェーズのトークンノードが引き継がれる。
 * @param commentsBeforeToken  更新済みコメントマップ
 * @param code                 フェーズ適用後のコード文字列。トークン系フェーズは {@code buildOutput()} で生成。
 * @param snapshots            このフェーズが生成したスナップショット（0 件以上）
 * @param logs                 このフェーズが生成した適用ログ（0 件以上）
 * @param unitOutputDumps      MAIN フェーズ全ユニットの変換後テキスト（他フェーズでは空リスト）
 * @param unitLabels           各ユニットの種別（他フェーズでは空リスト）
 */
public record PhaseExecutionResult(
        List<AstNode> tokenNodes,
        Map<Integer, List<String>> commentsBeforeToken,
        String code,
        List<PhaseSnapshot> snapshots,
        List<PhaseTransformLog> logs,
        List<String> unitOutputDumps,
        List<UnitLabel> unitLabels) {
}
