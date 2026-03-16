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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReceiverAstValidator（AST 本判定）の単体テスト。
 */
class ReceiverAstValidatorTest {

	// ---- 正常系 ----

	@Test
	void singleIdentifier_valid() {
		assertTrue(ReceiverAstValidator.isValid(List.of("str")));
	}

	@Test
	void memberChain_valid() {
		assertTrue(ReceiverAstValidator.isValid(List.of("this", ".", "m_str")));
	}

	@Test
	void arrowChain_valid() {
		assertTrue(ReceiverAstValidator.isValid(List.of("this", "->", "m_str")));
	}

	@Test
	void subscriptReceiver_valid() {
		assertTrue(ReceiverAstValidator.isValid(List.of("arr", "[", "0", "]")));
	}

	@Test
	void functionCallResult_valid() {
		assertTrue(ReceiverAstValidator.isValid(List.of("GetString", "(", "data", ")")));
	}

	@Test
	void multiStepChain_valid() {
		assertTrue(ReceiverAstValidator.isValid(List.of("app", ".", "method", "(", ")", ".", "field")));
	}

	@Test
	void timeFormatLeft_valid() {
		assertTrue(ReceiverAstValidator.isValid(List.of("time", ".", "Format", "(", "\"%Y\"", ")")));
	}

	// ---- 拒否系 ----

	@Test
	void binaryOp_rejected() {
		assertFalse(ReceiverAstValidator.isValid(List.of("a", "+", "b")));
	}

	@Test
	void ternary_rejected() {
		assertFalse(ReceiverAstValidator.isValid(List.of("cond", "?", "x", ":", "y")));
	}

	@Test
	void assignment_rejected() {
		assertFalse(ReceiverAstValidator.isValid(List.of("x", "=", "y")));
	}

	@Test
	void bracketBinaryOp_rejected() {
		assertFalse(ReceiverAstValidator.isValid(List.of("(", "a", "+", "b", ")")));
	}

	@Test
	void cast_rejected() {
		assertFalse(ReceiverAstValidator.isValid(List.of("(", "CString", ")", "x")));
	}

	@Test
	void commaExpression_rejected() {
		assertFalse(ReceiverAstValidator.isValid(List.of("a", ",", "b")));
	}

	// ---- combined filter ----

	@Test
	void combinedFilter_singleToken_accepts() {
		assertTrue(ReceiverAstValidator.createFilter().test(List.of("MigrationHelper.Format(x)")));
	}

	@Test
	void combinedFilter_prefilterReject_rejects() {
		assertFalse(ReceiverAstValidator.createFilter().test(List.of(".", "method")));
	}

	@Test
	void combinedFilter_astReject_rejects() {
		assertFalse(ReceiverAstValidator.createFilter().test(List.of("a", "+", "b")));
	}

	@Test
	void combinedFilter_validChain_accepts() {
		assertTrue(ReceiverAstValidator.createFilter().test(List.of("this", "->", "m_str")));
	}

	@Test
	void diagnosticFilter_assignment_passesForReview() {
		assertTrue(ReceiverAstValidator.createFilterForDiagnostic().test(List.of("x", "=", "y")));
	}
}
