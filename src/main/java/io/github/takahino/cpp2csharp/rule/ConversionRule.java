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

package io.github.takahino.cpp2csharp.rule;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 単一の変換ルールを表すクラス。
 * from トークン列と to テンプレート文字列で構成される。
 * オプションで test/assrt ペアによるルール内蔵テストを保持できる。
 *
 * <p>例:</p>
 * <pre>
 * from: this . AfxMessageBox ( ABSTRACT_PARAM00 , MB_OK | MB_ICONERROR ) ;
 * to: MessageBox.Show(ABSTRACT_PARAM00, "", MessageBoxButtons.OK, MessageBoxIcon.Error);
 * test: AfxMessageBox("Hello", MB_OK | MB_ICONERROR);
 * assrt: MessageBox.Show("Hello", "", MessageBoxButtons.OK, MessageBoxIcon.Error);
 * </pre>
 */
public final class ConversionRule {

    /** このルールを定義しているファイル名 (デバッグ用) */
    private final String sourceFile;

    /** from パターンのトークン列 */
    private final List<ConversionToken> fromTokens;

    /** to テンプレート文字列 */
    private final String toTemplate;

    /** ルール内蔵テスト (test/assrt ペアのリスト) */
    private final List<RuleTestCase> testCases;

    /**
     * コンストラクタ（テストケースなし）。
     *
     * @param sourceFile  定義元ファイル名
     * @param fromTokens  from パターンのトークン列
     * @param toTemplate  to テンプレート文字列
     */
    public ConversionRule(String sourceFile, List<ConversionToken> fromTokens, String toTemplate) {
        this(sourceFile, fromTokens, toTemplate, List.of());
    }

    /**
     * コンストラクタ。
     *
     * @param sourceFile  定義元ファイル名
     * @param fromTokens  from パターンのトークン列
     * @param toTemplate  to テンプレート文字列
     * @param testCases   ルール内蔵テスト (test/assrt ペア)
     */
    public ConversionRule(String sourceFile, List<ConversionToken> fromTokens, String toTemplate,
                          List<RuleTestCase> testCases) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile が null です");
        this.fromTokens = Collections.unmodifiableList(
                Objects.requireNonNull(fromTokens, "fromTokens が null です"));
        this.toTemplate = Objects.requireNonNull(toTemplate, "toTemplate が null です");
        this.testCases = Collections.unmodifiableList(
                testCases != null ? testCases : List.of());
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
     * from パターンのトークン列を返す。
     *
     * @return 不変のトークンリスト
     */
    public List<ConversionToken> getFromTokens() {
        return fromTokens;
    }

    /**
     * to テンプレート文字列を返す。
     *
     * @return to テンプレート
     */
    public String getToTemplate() {
        return toTemplate;
    }

    /**
     * ルール内蔵テスト (test/assrt ペア) のリストを返す。
     *
     * @return 不変のテストケースリスト（空の場合は空リスト）
     */
    public List<RuleTestCase> getTestCases() {
        return testCases;
    }

    /**
     * from パターンに含まれる ABSTRACT_PARAM の最大インデックス数を返す。
     *
     * @return 使用している ABSTRACT_PARAM の個数
     */
    public long getAbstractParamCount() {
        return fromTokens.stream()
                .filter(ConversionToken::isAbstractParam)
                .map(ConversionToken::getParamIndex)
                .distinct()
                .count();
    }

    /**
     * from パターンから期待する引数個数を導出する。
     *
     * <p>ANTLR CPP14 文法で構文解析し、最初の関数呼び出しの expressionList から
     * initializerClause 数を取得する。文字列操作ではなく構文解析により正確に判定する。</p>
     *
     * <p>括弧を含まないパターン（型変換など）は -1 を返し、引数個数フィルタの対象外とする。</p>
     *
     * @return 期待する引数個数、または -1（フィルタ対象外）
     */
    public int getArgumentCount() {
        return RulePatternParser.parseArgumentCount(fromTokens);
    }

    @Override
    public String toString() {
        return String.format("ConversionRule{from=%s, to='%s', file='%s'}",
                fromTokens, toTemplate, sourceFile);
    }
}
