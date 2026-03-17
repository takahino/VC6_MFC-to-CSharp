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

package io.github.takahino.cpp2csharp.grammar;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.TokenStream;

/**
 * CPP14Parser の基底クラス。 ANTLR4 grammars-v4 の Java 版実装を移植したもの。
 * 純粋仮想指定子の文脈判定ロジックを提供する。
 */
public abstract class CPP14ParserBase extends Parser {

	/**
	 * コンストラクタ。
	 *
	 * @param input
	 *            トークンストリーム
	 */
	protected CPP14ParserBase(TokenStream input) {
		super(input);
	}

	/**
	 * 現在の文脈が純粋仮想指定子として有効かどうかを判定する。 memberDeclarator 文脈内で parametersAndQualifiers
	 * が存在する場合に true を返す。
	 *
	 * @return 純粋仮想指定子として有効であれば true
	 */
	protected boolean IsPureSpecifierAllowed() {
		try {
			var x = this._ctx; // memberDeclarator
			var c = x.getChild(0).getChild(0);
			var c2 = c.getChild(0);
			var p = c2.getChild(1);
			if (p == null)
				return false;
			return (p instanceof CPP14Parser.ParametersAndQualifiersContext);
		} catch (Exception e) {
			// 文脈が不適切な場合は false を返す
		}
		return false;
	}
}
