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

package io.github.takahino.cpp2csharp.mrule;

import java.util.List;

/**
 * マルチ置換ルールを表すクラス。
 *
 * <p>
 * 1つのルールは複数の {@link MRuleFindSpec}（find/replace ペア）を持つ。 find spec は順番に適用され、各
 * spec のマッチ結果を統合してキャプチャを共有する。
 * </p>
 */
public final class MultiReplaceRule {

	private final String ruleId;
	private final String sourceFile;
	private final MRuleScope scope;
	private final List<MRuleFindSpec> findSpecs;

	/**
	 * コンストラクタ。
	 *
	 * @param ruleId
	 *            ルールの一意識別子
	 * @param sourceFile
	 *            定義元ファイル名
	 * @param scope
	 *            ルールのスコープ
	 * @param findSpecs
	 *            find/replace ペアのリスト
	 */
	public MultiReplaceRule(String ruleId, String sourceFile, MRuleScope scope, List<MRuleFindSpec> findSpecs) {
		this.ruleId = ruleId;
		this.sourceFile = sourceFile;
		this.scope = scope;
		this.findSpecs = List.copyOf(findSpecs);
	}

	/**
	 * ルールの一意識別子を返す。
	 *
	 * @return ルール ID
	 */
	public String getRuleId() {
		return ruleId;
	}

	/**
	 * 定義元ファイル名を返す。
	 *
	 * @return ファイル名
	 */
	public String getSourceFile() {
		return sourceFile;
	}

	/**
	 * ルールのスコープを返す。
	 *
	 * @return スコープ
	 */
	public MRuleScope getScope() {
		return scope;
	}

	/**
	 * find/replace ペアのリストを返す。
	 *
	 * @return 不変の find spec リスト
	 */
	public List<MRuleFindSpec> getFindSpecs() {
		return findSpecs;
	}

	@Override
	public String toString() {
		return "MultiReplaceRule{id=" + ruleId + ", scope=" + scope + ", specs=" + findSpecs.size() + "}";
	}
}
