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

import java.util.List;

/**
 * 関数単位の変換前後情報を保持する record。JSON 出力用。
 *
 * <p>
 * {@code comment} と {@code body} は {@link LinesDiff} 形式で保持し、
 * {@code cppLines}（変換前）と {@code csLines}（変換後）を行単位で diff しやすくする。
 * </p>
 *
 * @param cppSignature
 *            変換前の関数宣言部（例: "BOOL CInventoryDlg :: OnInitDialog ( )"）
 * @param csSignature
 *            変換後の関数宣言部（例: "private bool OnInitDialog ( )"）
 * @param comment
 *            関数直上コメントの変換前後行配列（コメントなしは空リスト）
 * @param body
 *            関数ボディ（"{...}" ブロック）の変換前後行配列
 */
public record FunctionUnitEntry(String cppSignature, String csSignature, LinesDiff comment, LinesDiff body) {

	/**
	 * 変換前後の行配列ペア。diff ツールへの入力として使用することを想定する。
	 *
	 * @param cppLines
	 *            変換前（C++）行リスト
	 * @param csLines
	 *            変換後（C#）行リスト
	 */
	public record LinesDiff(List<String> cppLines, List<String> csLines) {
	}
}
