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

import io.github.takahino.cpp2csharp.converter.PhaseTransformLog;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * comby CLI をサブプロセス呼び出しで実行するエンジン（将来実装用スタブ）。
 *
 * <p>
 * comby CLI（<a href="https://comby.dev/">comby.dev</a>）を外部プロセスとして呼び出すことで、 原初の
 * comby と完全に同じ動作を実現する。
 * </p>
 *
 * <p>
 * 使用方法（将来）:
 * </p>
 *
 * <pre>{@code
 * var engine = new CliCombyEngine(Path.of("/usr/local/bin/comby"));
 * var converter = new CppToCSharpConverter(engine);
 * }</pre>
 *
 * <p>
 * 現時点では {@link UnsupportedOperationException} を投げる。 実装する際の主な考慮点:
 * </p>
 * <ul>
 * <li>comby CLI の入出力形式（stdin/stdout または一時ファイル）</li>
 * <li>Windows 環境でのパス区切り・改行コード</li>
 * <li>{@code .crule} → comby テンプレートファイル形式への変換</li>
 * <li>プロセス終了コード・stderr のエラーハンドリング</li>
 * </ul>
 */
public class CliCombyEngine implements CombyEngine {

	private final Path combyCli;
	private final List<PhaseTransformLog> logs = new ArrayList<>();

	/**
	 * @param combyCli
	 *            comby 実行ファイルのパス（例: {@code Path.of("/usr/local/bin/comby")}）
	 */
	public CliCombyEngine(Path combyCli) {
		this.combyCli = combyCli;
	}

	@Override
	public String transformPhase(String source, List<CombyRule> rules, int phaseNum) {
		throw new UnsupportedOperationException(
				"CliCombyEngine は未実装です。comby CLI をインストールして実装してください。combyCli=" + combyCli);
	}

	@Override
	public String transformPhases(String source, List<List<CombyRule>> phases) {
		throw new UnsupportedOperationException(
				"CliCombyEngine は未実装です。comby CLI をインストールして実装してください。combyCli=" + combyCli);
	}

	@Override
	public List<PhaseTransformLog> getLogs() {
		return List.copyOf(logs);
	}
}
