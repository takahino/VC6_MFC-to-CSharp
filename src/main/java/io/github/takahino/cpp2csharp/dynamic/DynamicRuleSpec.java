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

package io.github.takahino.cpp2csharp.dynamic;

import io.github.takahino.cpp2csharp.rule.ConversionToken;

import java.util.List;

/**
 * 動的ルール仕様を表すレコード。
 *
 * <p>トークンストリームからパターンに一致する値を収集し、
 * その値をテンプレートに埋め込んで ConversionRule を動的に生成する。</p>
 *
 * <h2>使用例: クラス名収集によるメソッド定義変換</h2>
 * <pre>
 * collect: ABSTRACT_PARAM00 ::
 *
 * from: void COLLECTED :: ABSTRACT_PARAM00 ( ABSTRACT_PARAM01 )
 * to: private void ABSTRACT_PARAM00(ABSTRACT_PARAM01)
 * </pre>
 *
 * <p>上記の場合、トークンストリームから `SomeClass ::` パターンを探し、
 * 各クラス名について `void SomeClass :: AP00 ( AP01 )` → `private void AP00(AP01)` の
 * ConversionRule を生成する。</p>
 *
 * @param sourceFile      定義元 .drule ファイル名
 * @param collectPattern  収集パターン（ABSTRACT_PARAM00 が収集対象の値を捕捉する）
 * @param templates       from/to テンプレートのリスト（COLLECTED が収集値に置換される）
 */
public record DynamicRuleSpec(
        String sourceFile,
        List<ConversionToken> collectPattern,
        List<FromToTemplate> templates
) {

    /**
     * from/to テンプレートのペア。
     *
     * @param fromTemplate from パターンのテンプレート文字列（COLLECTED プレースホルダを含む）
     * @param toTemplate   to テンプレート文字列（COLLECTED プレースホルダを含む場合がある）
     */
    public record FromToTemplate(String fromTemplate, String toTemplate) {}
}
