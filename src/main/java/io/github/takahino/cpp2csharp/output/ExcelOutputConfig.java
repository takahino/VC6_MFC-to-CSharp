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

package io.github.takahino.cpp2csharp.output;

/**
 * Excel 変換過程可視化出力の設定を保持するクラス。
 *
 * <p>
 * システムプロパティ {@code cpp2csharp.excel.enabled} で上書き可能。
 * </p>
 */
public final class ExcelOutputConfig {

	private static final String EXCEL_ENABLED_PROPERTY = "cpp2csharp.excel.enabled";

	private final boolean enabled;

	private ExcelOutputConfig(boolean enabled) {
		this.enabled = enabled;
	}

	/**
	 * Excel 出力の有効/無効を指定して設定を生成する。
	 *
	 * @param enabled
	 *            有効にする場合 true
	 * @return 設定インスタンス
	 */
	public static ExcelOutputConfig of(boolean enabled) {
		return new ExcelOutputConfig(enabled);
	}

	/**
	 * デフォルト設定を生成する。 システムプロパティ {@code cpp2csharp.excel.enabled} を参照（デフォルト true）。
	 *
	 * @return 設定インスタンス
	 */
	public static ExcelOutputConfig defaultConfig() {
		String prop = System.getProperty(EXCEL_ENABLED_PROPERTY, "true");
		return new ExcelOutputConfig(!"false".equalsIgnoreCase(prop));
	}

	/**
	 * Excel 出力が有効かどうかを返す。
	 *
	 * @return 有効な場合 true
	 */
	public boolean isEnabled() {
		return enabled;
	}
}
