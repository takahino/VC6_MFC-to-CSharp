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

package io.github.takahino.cpp2csharp.tree;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * パターンマッチング変換で使用するトークンノードクラス。
 *
 * <p>ANTLR の TerminalNode に対応し、{@code text} は実際のトークン文字列を示す。</p>
 */
public final class AstNode {

    private static final AtomicInteger idCounter = new AtomicInteger(0);

    /** ノードの一意識別子 */
    private final int id;

    /** トークンテキスト */
    private final String text;

    /** ソース位置情報 (行番号) */
    private final int line;

    /** ソース位置情報 (カラム番号) */
    private final int column;

    /**
     * ANTLR トークンストリーム上のインデックス。
     * ストリーム情報がない場合（置換由来トークン）は -1。
     */
    private final int streamIndex;

    /**
     * トークンノードを生成するファクトリメソッド (ストリームインデックスなし)。
     *
     * @param text   トークンテキスト
     * @param line   ソース行番号
     * @param column ソースカラム番号
     * @return 生成されたトークンノード
     */
    public static AstNode tokenNode(String text, int line, int column) {
        return new AstNode(text, line, column, -1);
    }

    /**
     * トークンノードを生成するファクトリメソッド (ストリームインデックスあり)。
     *
     * @param text        トークンテキスト
     * @param line        ソース行番号
     * @param column      ソースカラム番号
     * @param streamIndex ANTLR トークンストリーム上のインデックス
     * @return 生成されたトークンノード
     */
    public static AstNode tokenNode(String text, int line, int column, int streamIndex) {
        return new AstNode(text, line, column, streamIndex);
    }

    /**
     * 指定した ID でトークンノードを生成するファクトリメソッド。
     * 木構造の直接修正（サブツリー置換）時に、置換位置を維持するために使用する。
     *
     * @param text   トークンテキスト
     * @param line   ソース行番号
     * @param column ソースカラム番号
     * @param id     ノードの一意識別子（既存ノードとの順序整合性のため指定）
     * @return 生成されたトークンノード
     */
    public static AstNode tokenNodeWithId(String text, int line, int column, int id) {
        return new AstNode(text, line, column, -1, id);
    }

    /**
     * 指定した ID と streamIndex でトークンノードを生成するファクトリメソッド。
     * 置換時に元トークンの streamIndex を継承し、直前の改行・空白を復元するために使用する。
     *
     * @param text        トークンテキスト
     * @param line        ソース行番号
     * @param column      ソースカラム番号
     * @param id          ノードの一意識別子
     * @param streamIndex 継承するストリームインデックス（-1 の場合は継承なし）
     * @return 生成されたトークンノード
     */
    public static AstNode tokenNodeWithId(String text, int line, int column, int id, int streamIndex) {
        return new AstNode(text, line, column, streamIndex, id);
    }

    private AstNode(String text, int line, int column, int streamIndex) {
        this(text, line, column, streamIndex, idCounter.getAndIncrement());
    }

    private AstNode(String text, int line, int column, int streamIndex, int id) {
        this.id = id;
        idCounter.updateAndGet(current -> Math.max(current, id + 1));
        this.text = Objects.requireNonNull(text);
        this.line = line;
        this.column = column;
        this.streamIndex = streamIndex;
    }

    /**
     * ノードの一意識別子を返す。
     *
     * @return ノード ID
     */
    public int getId() {
        return id;
    }

    /**
     * テキスト (トークン文字列) を返す。
     *
     * @return テキスト
     */
    public String getText() {
        return text;
    }

    /**
     * ソース行番号を返す。
     *
     * @return 行番号
     */
    public int getLine() {
        return line;
    }

    /**
     * ソースカラム番号を返す。
     *
     * @return カラム番号
     */
    public int getColumn() {
        return column;
    }

    /**
     * ANTLR トークンストリーム上のインデックスを返す。
     * ストリーム情報がない場合は -1 を返す。
     *
     * @return ストリームインデックス、または -1
     */
    public int getStreamIndex() {
        return streamIndex;
    }

    @Override
    public String toString() {
        return String.format("AstNode{id=%d, text='%s', pos=%d:%d}", id, text, line, column);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AstNode that)) return false;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}
