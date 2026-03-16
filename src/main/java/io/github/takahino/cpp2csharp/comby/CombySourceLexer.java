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

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;

import java.util.BitSet;

/**
 * {@link CombyLexer}（ANTLR 生成）を用いて、C/C++/C# ファミリー言語のソーステキストから
 * COMBY マッチングで除外すべき領域（コメント・文字列リテラル）を {@link BitSet} にマークする。
 *
 * <p>マークされた位置を {@link CombyMatcher#findAll} の commentMask として渡すことで、
 * コメントやリテラル内部からマッチングが開始されるのを防ぐ。
 * マッチ領域の内部（ホールがキャプチャするボディ等）は制限しない。</p>
 */
public final class CombySourceLexer {

    private CombySourceLexer() {}

    /**
     * ソーステキスト内のコメント・文字列リテラル領域を BitSet にマークして返す。
     *
     * @param source 対象テキスト
     * @return コメント／リテラル領域の文字インデックスがセットされた BitSet
     */
    public static BitSet buildExcludedMask(String source) {
        BitSet mask = new BitSet(source.length());
        CombyLexer lexer = new CombyLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();

        Token token;
        while ((token = lexer.nextToken()).getType() != Token.EOF) {
            int type = token.getType();
            if (type == CombyLexer.BlockComment
                    || type == CombyLexer.LineComment
                    || type == CombyLexer.VerbatimStringLit
                    || type == CombyLexer.StringLit
                    || type == CombyLexer.CharLit) {
                int stop = Math.min(token.getStopIndex() + 1, source.length());
                mask.set(token.getStartIndex(), stop);
            }
        }
        return mask;
    }
}
