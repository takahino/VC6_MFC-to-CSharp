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
 * ReceiverCapturePolicy（プリフィルタ）の単体テスト。
 *
 * <p>
 * プリフィルタは明白な不正だけを reject する。本判定は ReceiverAstValidator が担う。
 * </p>
 */
class ReceiverCapturePolicyTest {

	// ---- passesPrefilter (通常モード) ----

	@Test
	void empty_rejected() {
		assertFalse(ReceiverCapturePolicy.passesPrefilter(List.of()));
	}

	@Test
	void singleIdentifier_passes() {
		assertTrue(ReceiverCapturePolicy.passesPrefilter(List.of("str")));
	}

	@Test
	void singleSynthesizedToken_passes() {
		assertTrue(ReceiverCapturePolicy.passesPrefilter(List.of("MigrationHelper.Format(time,\"%Y\")")));
	}

	@Test
	void bracketStart_rejected_in_normal_mode() {
		// 通常モードでは ("...").Format(...) のような誤キャプチャを防ぐため reject
		assertFalse(ReceiverCapturePolicy.passesPrefilter(List.of("(", "a", "+", "b", ")")));
	}

	@Test
	void invalidStart_closeParen_rejected() {
		assertFalse(ReceiverCapturePolicy.passesPrefilter(List.of(")", "field")));
	}

	@Test
	void invalidStart_dot_rejected() {
		assertFalse(ReceiverCapturePolicy.passesPrefilter(List.of(".", "method")));
	}

	@Test
	void invalidStart_arrow_rejected() {
		assertFalse(ReceiverCapturePolicy.passesPrefilter(List.of("->", "field")));
	}

	@Test
	void memberChain_passes() {
		assertTrue(ReceiverCapturePolicy.passesPrefilter(List.of("this", ".", "m_str")));
	}

	@Test
	void arrowChain_passes() {
		assertTrue(ReceiverCapturePolicy.passesPrefilter(List.of("this", "->", "m_str")));
	}

	@Test
	void subscriptReceiver_passes() {
		assertTrue(ReceiverCapturePolicy.passesPrefilter(List.of("arr", "[", "0", "]")));
	}

	@Test
	void functionCallResult_passes() {
		assertTrue(ReceiverCapturePolicy.passesPrefilter(List.of("GetString", "(", "data", ")")));
	}

	@Test
	void multiStepChain_passes() {
		assertTrue(ReceiverCapturePolicy.passesPrefilter(List.of("app", ".", "method", "(", ")", ".", "field")));
	}

	@Test
	void binaryOp_passesToAst() {
		// + 等はプリフィルタでは拒否せず AST に回す
		assertTrue(ReceiverCapturePolicy.passesPrefilter(List.of("a", "+", "b")));
	}

	@Test
	void ternary_passesToAst() {
		assertTrue(ReceiverCapturePolicy.passesPrefilter(List.of("cond", "?", "x", ":", "y")));
	}

	@Test
	void assignment_passesToAst() {
		assertTrue(ReceiverCapturePolicy.passesPrefilter(List.of("x", "=", "y")));
	}

	@Test
	void unbalancedCloseParen_rejected() {
		assertFalse(ReceiverCapturePolicy.passesPrefilter(List.of("a", ")")));
	}

	@Test
	void depth0_semicolon_rejected() {
		assertFalse(ReceiverCapturePolicy.passesPrefilter(List.of("a", ";", "b")));
	}

	@Test
	void depth0_comma_rejected() {
		assertFalse(ReceiverCapturePolicy.passesPrefilter(List.of("a", ",", "b")));
	}

	// ---- passesPrefilterForDiagnostic (診断モード) ----

	@Test
	void diagnostic_bracketStart_passes() {
		assertTrue(ReceiverCapturePolicy.passesPrefilterForDiagnostic(List.of("(", "a", "+", "b", ")")));
	}

	@Test
	void diagnostic_empty_rejected() {
		assertFalse(ReceiverCapturePolicy.passesPrefilterForDiagnostic(List.of()));
	}

	// ---- isIdentifierLike ----

	@Test
	void identifierLike_simpleWord() {
		assertTrue(ReceiverCapturePolicy.isIdentifierLike("myVar"));
	}

	@Test
	void identifierLike_withUnderscore() {
		assertTrue(ReceiverCapturePolicy.isIdentifierLike("_myVar"));
	}

	@Test
	void identifierLike_withDigit() {
		assertTrue(ReceiverCapturePolicy.isIdentifierLike("var123"));
	}

	@Test
	void identifierLike_withOperator_false() {
		assertFalse(ReceiverCapturePolicy.isIdentifierLike("my+var"));
	}

	@Test
	void identifierLike_empty_false() {
		assertFalse(ReceiverCapturePolicy.isIdentifierLike(""));
	}

	@Test
	void identifierLike_dotSeparated_false() {
		assertFalse(ReceiverCapturePolicy.isIdentifierLike("a.b"));
	}
}
