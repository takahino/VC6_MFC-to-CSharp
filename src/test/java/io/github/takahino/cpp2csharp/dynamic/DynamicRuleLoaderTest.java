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

package io.github.takahino.cpp2csharp.dynamic;

import io.github.takahino.cpp2csharp.rule.ConversionToken;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DynamicRuleLoader .drule パーステスト")
class DynamicRuleLoaderTest {

	private final DynamicRuleLoader loader = new DynamicRuleLoader();

	@Test
	@DisplayName("collect/from/to の基本パース")
	void parseBasicSpec() {
		String content = """
				collect: ABSTRACT_PARAM00 ::
				from: void COLLECTED :: ABSTRACT_PARAM00 ( ABSTRACT_PARAM01 )
				to: private void ABSTRACT_PARAM00(ABSTRACT_PARAM01)
				""";
		DynamicRuleSpec spec = loader.parse(content, "test.drule");

		assertThat(spec).isNotNull();
		assertThat(spec.sourceFile()).isEqualTo("test.drule");
		assertThat(spec.collectPattern()).hasSize(2); // ABSTRACT_PARAM00, ::
		assertThat(spec.templates()).hasSize(1);
		assertThat(spec.templates().get(0).fromTemplate())
				.isEqualTo("void COLLECTED :: ABSTRACT_PARAM00 ( ABSTRACT_PARAM01 )");
		assertThat(spec.templates().get(0).toTemplate()).isEqualTo("private void ABSTRACT_PARAM00(ABSTRACT_PARAM01)");
	}

	@Test
	@DisplayName("collect パターンが ABSTRACT_PARAM00 をトークンとして含む")
	void collectPatternContainsAbstractParam() {
		String content = """
				collect: ABSTRACT_PARAM00 ::
				from: void COLLECTED :: ABSTRACT_PARAM00 ( )
				to: private void ABSTRACT_PARAM00()
				""";
		DynamicRuleSpec spec = loader.parse(content, "test.drule");

		assertThat(spec.collectPattern()).isNotEmpty();
		assertThat(spec.collectPattern().get(0).isAbstractParam()).isTrue();
		assertThat(spec.collectPattern().get(0).getParamIndex()).isEqualTo(0);
	}

	@Test
	@DisplayName("コメント行は無視される")
	void commentsIgnored() {
		String content = """
				# クラス名を収集する
				collect: ABSTRACT_PARAM00 ::
				# void メソッド
				from: void COLLECTED :: ABSTRACT_PARAM00 ( )
				to: private void ABSTRACT_PARAM00()
				""";
		DynamicRuleSpec spec = loader.parse(content, "test.drule");

		assertThat(spec).isNotNull();
		assertThat(spec.templates()).hasSize(1);
	}

	@Test
	@DisplayName("複数の from/to テンプレートをパースできる")
	void parseMultipleTemplates() {
		String content = """
				collect: ABSTRACT_PARAM00 ::
				from: void COLLECTED :: ABSTRACT_PARAM00 ( ABSTRACT_PARAM01 )
				to: private void ABSTRACT_PARAM00(ABSTRACT_PARAM01)
				from: bool COLLECTED :: ABSTRACT_PARAM00 ( ABSTRACT_PARAM01 )
				to: private bool ABSTRACT_PARAM00(ABSTRACT_PARAM01)
				from: int COLLECTED :: ABSTRACT_PARAM00 ( ABSTRACT_PARAM01 )
				to: private int ABSTRACT_PARAM00(ABSTRACT_PARAM01)
				""";
		DynamicRuleSpec spec = loader.parse(content, "test.drule");

		assertThat(spec.templates()).hasSize(3);
		assertThat(spec.templates().get(1).fromTemplate()).startsWith("bool COLLECTED");
		assertThat(spec.templates().get(2).fromTemplate()).startsWith("int COLLECTED");
	}

	@Test
	@DisplayName("collect がない場合は null を返す")
	void missingCollectReturnsNull() {
		String content = """
				from: void COLLECTED :: ABSTRACT_PARAM00 ( )
				to: private void ABSTRACT_PARAM00()
				""";
		DynamicRuleSpec spec = loader.parse(content, "test.drule");

		assertThat(spec).isNull();
	}

	@Test
	@DisplayName("from/to テンプレートがない場合は null を返す")
	void missingTemplatesReturnsNull() {
		String content = "collect: ABSTRACT_PARAM00 ::\n";
		DynamicRuleSpec spec = loader.parse(content, "test.drule");

		assertThat(spec).isNull();
	}

	@Test
	@DisplayName("空行は無視される")
	void emptyLinesIgnored() {
		String content = """

				collect: ABSTRACT_PARAM00 ::

				from: void COLLECTED :: ABSTRACT_PARAM00 ( )
				to: private void ABSTRACT_PARAM00()

				""";
		DynamicRuleSpec spec = loader.parse(content, "test.drule");

		assertThat(spec).isNotNull();
		assertThat(spec.templates()).hasSize(1);
	}
}
