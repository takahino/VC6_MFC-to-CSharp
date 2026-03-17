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

import io.github.takahino.cpp2csharp.matcher.CppParserFactory;
import io.github.takahino.cpp2csharp.rule.ConversionRuleLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * .crule ファイル内の test:/assrt: で定義されたルール内蔵テストを実行する。
 *
 * <p>
 * 各 crule に test:（想定入力コード）と assrt:（想定変換結果）が記載されている場合、 変換結果が assrt と一致することを検証する。
 * </p>
 */
@DisplayName("COMBYルール内蔵テスト検証")
class CombyRuleValidationTest {

	private ConversionRuleLoader loader;
	private CombyTransformer transformer;

	@BeforeEach
	void setUp() {
		loader = new ConversionRuleLoader(CppParserFactory.asLexerFactory());
		transformer = new CombyTransformer();
	}

	@Test
	@DisplayName("全 crule の test/assrt が想定通りに変換される")
	void validateAllCombyRuleTestCases() throws IOException {
		ConversionRuleLoader.ThreePassRuleSet ruleSet = loader.loadThreePassRules();

		List<String> errors = new ArrayList<>();

		for (List<CombyRule> phase : ruleSet.combyPhases()) {
			for (CombyRule rule : phase) {
				for (int i = 0; i < rule.getTestCases().size(); i++) {
					CombyTestCase tc = rule.getTestCases().get(i);
					String actual = transformer.transformPhase(tc.testInput(), List.of(rule));
					if (!normalizeForAssert(actual).equals(normalizeForAssert(tc.expectedOutput()))) {
						errors.add(String.format("[%s ペア #%d] from=%s%n  期待: %s%n  実際: %s", rule.getSourceFile(), i + 1,
								rule.getFromPattern(), tc.expectedOutput(), actual));
					}
				}
			}
		}

		assertThat(errors)
				.as("COMBYルール内蔵テストの不一致 (%d 件):%n%s", errors.size(), String.join(System.lineSeparator(), errors))
				.isEmpty();
	}

	/**
	 * assrt 比較時は半角スペース差異を無視する。
	 */
	private static String normalizeForAssert(String text) {
		return text.replace(" ", "");
	}
}
