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

import java.util.List;
import java.util.Set;

/**
 * RECEIVER[nn] の軽量プリフィルタ。
 *
 * <p>AST に到達する前でも確実に不正と言える候補だけを落とす。
 * receiver として厳密に正しいかどうかの本判定は {@link ReceiverAstValidator} が担う。
 * false negative を避けるため、迷うケースは AST に回す。</p>
 *
 * <h2>プリフィルタで reject する条件</h2>
 * <ul>
 *   <li>空</li>
 *   <li>先頭が {@code .} / {@code ->} / {@code )} / {@code ]} / {@code }}</li>
 *   <li>括弧不均衡</li>
 *   <li>深さ 0 の {@code ;} または {@code ,}</li>
 * </ul>
 *
 * <p>単一トークン（合成置換トークン含む）は AST 再パース不可のため即 accept とする。</p>
 *
 * @see ReceiverAstValidator
 * <p>関連仕様: {@code docs/receiver_validation_spec.md}</p>
 */
public final class ReceiverCapturePolicy {

    private ReceiverCapturePolicy() {
        throw new AssertionError("utility class");
    }


    /**
     * 先頭に出現を禁止するトークン。
     *
     * <p>{@code .} と {@code ->} はドットチェーン中間のメソッド名誤キャプチャを防ぐ。
     * {@code )} {@code ]} {@code } は閉じ括弧単体でレシーバーにならない。
     * {@code (} は通常モードでは誤キャプチャが多いため reject し、診断モードのみ許可する。</p>
     */
    static final Set<String> INVALID_START_TOKENS =
            Set.of("(", ")", "]", "}", ".", "->");

    /**
     * 深さ 0 で拒否するトークン（文境界）。
     *
     * <p>プリフィルタは最小責務のため、{@code +} {@code =} {@code ?} {@code :} 等は
     * ここでは拒否せず AST に回す。</p>
     */
    static final Set<String> REJECT_DEPTH0_TOKENS = Set.of(";", ",");

    /**
     * プリフィルタ: AST に回す前に明白な不正だけを reject する。
     *
     * <p>本メソッドが true を返しても receiver として有効とは限らない。
     * 呼び出し側で {@link ReceiverAstValidator#isValid} による本判定を行う。</p>
     *
     * @param captured RECEIVER にキャプチャされたトークン列
     * @return 明白に不正でなければ true（AST に回す、または単一トークンで即 accept）
     */
    public static boolean passesPrefilter(List<String> captured) {
        return validate(captured, false);
    }

    /**
     * 診断モード用プリフィルタ: 括弧始まりも AST に回す。
     *
     * @param captured RECEIVER にキャプチャされたトークン列
     * @return 明白に不正でなければ true
     */
    public static boolean passesPrefilterForDiagnostic(List<String> captured) {
        return validate(captured, true);
    }

    /**
     * トークンが識別子（英字・数字・アンダースコアのみで構成）かどうかを返す。
     *
     * <p>合成置換トークン（変換後の中間表現）は複数トークンを結合した文字列になりうるため、
     * 英数字以外の文字を含む場合がある。このメソッドは純粋な識別子判定のみを行い、
     * 合成置換トークンの許可は呼び出し側（単一トークン判定ロジック）が担う。</p>
     *
     * @param token 判定対象トークン文字列
     * @return 識別子として有効であれば true
     */
    public static boolean isIdentifierLike(String token) {
        if (token.isEmpty()) return false;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') return false;
        }
        return true;
    }

    /**
     * 内部検証ロジック。明白な不正だけを reject する。
     *
     * @param captured        検証対象トークン列
     * @param allowBracketStart true のとき先頭の括弧制約を外す（診断モード用）
     * @return 明白に不正でなければ true
     */
    private static boolean validate(List<String> captured, boolean allowBracketStart) {
        if (captured.isEmpty()) return false;

        String first = captured.get(0);
        if (!allowBracketStart) {
            if (INVALID_START_TOKENS.contains(first)) return false;
        }

        // 単一トークン: 合成置換トークンは AST 再パース不可のため即 pass
        if (captured.size() == 1) return true;

        // 複数トークン: 括弧均衡と深さ 0 の文境界のみチェック
        BracketDepthTracker tracker = new BracketDepthTracker();
        for (String t : captured) {
            if (!tracker.track(t)) return false;
            if (tracker.atSurface() && REJECT_DEPTH0_TOKENS.contains(t)) return false;
        }
        return tracker.isBalanced();
    }
}
