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

package io.github.takahino.cpp2csharp.matcher;

import io.github.takahino.cpp2csharp.grammar.CPP14Parser;
import io.github.takahino.cpp2csharp.grammar.CPP14ParserBaseVisitor;

import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * RECEIVER00 のキャプチャ妥当性を ANTLR 再パースで検証するクラス。
 *
 * <p>captured を C++ 式として再パースし、postfix chain（識別子・メンバアクセス・
 * 関数呼び出し・添字アクセスの連鎖）のみを許可する。二項演算・三項演算・代入・
 * cast・prefix unary は拒否する。</p>
 *
 * <p>本クラスはプリフィルタ（{@link ReceiverCapturePolicy}）通過後の本判定として使用する。</p>
 *
 * <p>関連仕様: {@code docs/receiver_validation_spec.md}</p>
 */
public final class ReceiverAstValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiverAstValidator.class);

    /**
     * isValid() 結果のメモ化キャッシュ。
     * キー: CPP14Lexer が出力したトークン型番号の列（例: "99,46,99"）。
     * 具体的な識別子名には依存しないため、"this . m_str" と "this . m_str2" は
     * どちらも同じキーにマップされ、ヒット率が大幅に向上する。
     * ReceiverShapeVisitor は文法構造のみを検査し識別子の値を参照しないため正確。
     * 上限 500 エントリ（LRU 方式）で十分。構造パターンの種類は文字列種類より遥かに少ない。
     */
    private static final Cache<String, Boolean> VALIDITY_CACHE =
            CacheBuilder.newBuilder()
                    .maximumSize(500)
                    .build();

    private ReceiverAstValidator() {
        throw new AssertionError("utility class");
    }

    /**
     * プリフィルタ + AST 判定を組み合わせた RECEIVER フィルタを返す。
     *
     * @return 候補が有効な receiver なら true
     */
    public static Predicate<List<String>> createFilter() {
        return captured -> {
            if (captured.size() == 1) return true;
            if (!ReceiverCapturePolicy.passesPrefilter(captured)) return false;
            return isValid(captured);
        };
    }

    /**
     * 診断モード用の RECEIVER フィルタを返す。
     *
     * @return 候補が診断用に有効なら true
     */
    public static Predicate<List<String>> createFilterForDiagnostic() {
        // 診断モードは「まだ rule に掛かりそうな候補」を広めに拾いたいので、
        // 厳密な AST 判定は掛けず、明白な不正だけをプリフィルタで落とす。
        return ReceiverCapturePolicy::passesPrefilterForDiagnostic;
    }

    /**
     * キャプチャされたトークン列が有効な receiver 式（postfix chain）かどうかを
     * ANTLR で再パースして判定する。
     *
     * @param captured RECEIVER にキャプチャされたトークン列
     * @return postfix chain として有効であれば true
     */
    public static boolean isValid(List<String> captured) {
        if (captured.isEmpty()) return false;

        // Lexer を一度だけ実行してトークン型列を取得する。
        // キャッシュキーをトークン型列にすることで、"this . m_str" と "this . m_str2" など
        // 識別子名だけが異なる構造的に等価なパターンが同一エントリにマップされ、
        // ヒット率が大幅に向上する。
        String expr = String.join(" ", captured);
        CommonTokenStream tokenStream = CppParserFactory.lex(expr);
        String typeKey = buildTypeKey(tokenStream);

        try {
            return VALIDITY_CACHE.get(typeKey, () -> parseAndValidate(tokenStream, expr));
        } catch (ExecutionException e) {
            return false;
        }
    }

    /**
     * Lexer 出力のトークン型番号列をカンマ区切り文字列で返す。
     * デフォルトチャンネルのトークンのみ対象とし、EOF は除く。
     */
    private static String buildTypeKey(CommonTokenStream tokenStream) {
        return tokenStream.getTokens().stream()
                .filter(t -> t.getChannel() == Token.DEFAULT_CHANNEL
                          && t.getType() != Token.EOF)
                .map(t -> String.valueOf(t.getType()))
                .collect(Collectors.joining(","));
    }

    /**
     * キャッシュ callable として切り出した実パース処理。
     * Lexer 済みの tokenStream をそのまま Parser に渡し、二重レキシングを避ける。
     */
    private static boolean parseAndValidate(CommonTokenStream tokenStream, String exprForLog) {
        try {
            CPP14Parser parser = CppParserFactory.createParser(tokenStream);

            ParseTree tree = parser.expression();
            if (parser.getCurrentToken().getType() != Token.EOF) {
                return false;
            }
            return new ReceiverShapeVisitor().visit(tree);
        } catch (ParseCancellationException e) {
            // 正常系: C++ 式として構文エラー → receiver 不正
            return false;
        } catch (Exception e) {
            LOGGER.warn("RECEIVER00 AST validation failed unexpectedly: {}", exprForLog, e);
            return false;
        }
    }

    /**
     * 式が postfix chain のみに還元されるかどうかを判定するビジター。
     * 二項演算・三項・代入・cast・prefix unary を検出したら即 false を返す。
     */
    private static class ReceiverShapeVisitor extends CPP14ParserBaseVisitor<Boolean> {

        /** 除外: カンマ式 {@code a, b} */
        @Override
        public Boolean visitExpression(CPP14Parser.ExpressionContext ctx) {
            if (ctx.assignmentExpression().size() > 1) return false;
            return visit(ctx.assignmentExpression(0));
        }

        /** 除外: 代入式 {@code x = y}、throw 式 */
        @Override
        public Boolean visitAssignmentExpression(CPP14Parser.AssignmentExpressionContext ctx) {
            if (ctx.assignmentOperator() != null) return false;
            if (ctx.throwExpression() != null) return false;
            return visit(ctx.conditionalExpression());
        }

        /** 除外: 三項演算 {@code cond ? x : y} */
        @Override
        public Boolean visitConditionalExpression(CPP14Parser.ConditionalExpressionContext ctx) {
            if (ctx.Question() != null) return false;
            return visit(ctx.logicalOrExpression());
        }

        /** 除外: 論理 OR {@code a || b} */
        @Override
        public Boolean visitLogicalOrExpression(CPP14Parser.LogicalOrExpressionContext ctx) {
            if (ctx.logicalAndExpression().size() > 1) return false;
            return visit(ctx.logicalAndExpression(0));
        }

        /** 除外: 論理 AND {@code a && b} */
        @Override
        public Boolean visitLogicalAndExpression(CPP14Parser.LogicalAndExpressionContext ctx) {
            if (ctx.inclusiveOrExpression().size() > 1) return false;
            return visit(ctx.inclusiveOrExpression(0));
        }

        /** 除外: ビット OR {@code a | b} */
        @Override
        public Boolean visitInclusiveOrExpression(CPP14Parser.InclusiveOrExpressionContext ctx) {
            if (ctx.exclusiveOrExpression().size() > 1) return false;
            return visit(ctx.exclusiveOrExpression(0));
        }

        /** 除外: ビット XOR {@code a ^ b} */
        @Override
        public Boolean visitExclusiveOrExpression(CPP14Parser.ExclusiveOrExpressionContext ctx) {
            if (ctx.andExpression().size() > 1) return false;
            return visit(ctx.andExpression(0));
        }

        /** 除外: ビット AND {@code a & b} */
        @Override
        public Boolean visitAndExpression(CPP14Parser.AndExpressionContext ctx) {
            if (ctx.equalityExpression().size() > 1) return false;
            return visit(ctx.equalityExpression(0));
        }

        /** 除外: 等価 {@code a == b}, {@code a != b} */
        @Override
        public Boolean visitEqualityExpression(CPP14Parser.EqualityExpressionContext ctx) {
            if (ctx.relationalExpression().size() > 1) return false;
            return visit(ctx.relationalExpression(0));
        }

        /** 除外: 比較 {@code a < b}, {@code a > b} 等 */
        @Override
        public Boolean visitRelationalExpression(CPP14Parser.RelationalExpressionContext ctx) {
            if (ctx.shiftExpression().size() > 1) return false;
            return visit(ctx.shiftExpression(0));
        }

        /** 除外: シフト {@code a << b}, {@code a >> b} */
        @Override
        public Boolean visitShiftExpression(CPP14Parser.ShiftExpressionContext ctx) {
            if (ctx.additiveExpression().size() > 1) return false;
            return visit(ctx.additiveExpression(0));
        }

        /** 除外: 加減算 {@code a + b}, {@code a - b} */
        @Override
        public Boolean visitAdditiveExpression(CPP14Parser.AdditiveExpressionContext ctx) {
            if (ctx.multiplicativeExpression().size() > 1) return false;
            return visit(ctx.multiplicativeExpression(0));
        }

        /** 除外: 乗除算 {@code a * b}, {@code a / b}, {@code a % b} */
        @Override
        public Boolean visitMultiplicativeExpression(CPP14Parser.MultiplicativeExpressionContext ctx) {
            if (ctx.pointerMemberExpression().size() > 1) return false;
            return visit(ctx.pointerMemberExpression(0));
        }

        /** 除外: メンバポインタ {@code a .* b}, {@code a ->* b} */
        @Override
        public Boolean visitPointerMemberExpression(CPP14Parser.PointerMemberExpressionContext ctx) {
            if (ctx.castExpression().size() > 1) return false;
            return visit(ctx.castExpression(0));
        }

        /** 除外: C スタイル cast {@code (CString)x} */
        @Override
        public Boolean visitCastExpression(CPP14Parser.CastExpressionContext ctx) {
            if (ctx.theTypeId() != null) return false;
            return visit(ctx.unaryExpression());
        }

        /** 除外: prefix unary {@code *ptr}, {@code &x}, {@code !a}, {@code ++x}, {@code sizeof}, new, delete, noexcept, alignof */
        @Override
        public Boolean visitUnaryExpression(CPP14Parser.UnaryExpressionContext ctx) {
            if (ctx.unaryOperator() != null) return false;
            if (ctx.PlusPlus() != null) return false;
            if (ctx.MinusMinus() != null) return false;
            if (ctx.Sizeof() != null) return false;
            if (ctx.noExceptExpression() != null) return false;
            if (ctx.newExpression_() != null) return false;
            if (ctx.deleteExpression() != null) return false;
            if (ctx.Alignof() != null) return false;
            return visit(ctx.postfixExpression());
        }

        /** 許可: 括弧付き primary は中身を再帰判定し、それ以外の primary は許可 */
        @Override
        public Boolean visitPrimaryExpression(CPP14Parser.PrimaryExpressionContext ctx) {
            if (ctx.expression() != null) {
                return visit(ctx.expression());
            }
            return true;
        }

        /** 許可: postfix chain（識別子・メンバアクセス・関数呼び出し・添字アクセス） */
        @Override
        public Boolean visitPostfixExpression(CPP14Parser.PostfixExpressionContext ctx) {
            if (ctx.PlusPlus() != null || ctx.MinusMinus() != null) {
                return false;
            }
            if (ctx.Dynamic_cast() != null || ctx.Static_cast() != null
                    || ctx.Reinterpret_cast() != null || ctx.Const_cast() != null) {
                return false;
            }
            if (ctx.simpleTypeSpecifier() != null || ctx.typeNameSpecifier() != null) {
                return false;
            }
            if (ctx.primaryExpression() != null) {
                return visit(ctx.primaryExpression());
            }
            if (ctx.postfixExpression() != null) {
                return visit(ctx.postfixExpression());
            }
            return true;
        }
    }
}
