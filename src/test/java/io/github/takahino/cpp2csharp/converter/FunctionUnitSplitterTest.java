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

import io.github.takahino.cpp2csharp.tree.AstNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static io.github.takahino.cpp2csharp.converter.UnitLabel.BODY;
import static io.github.takahino.cpp2csharp.converter.UnitLabel.GAP;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link FunctionUnitSplitter} のユニットテスト。
 */
@DisplayName("FunctionUnitSplitter")
class FunctionUnitSplitterTest {

	/** スペース区切りトークン列を AstNode リストに変換するヘルパー（streamIndex = list index） */
	private static List<AstNode> nodes(String tokens) {
		String[] parts = tokens.trim().split("\\s+");
		List<AstNode> result = new ArrayList<>();
		for (int i = 0; i < parts.length; i++) {
			result.add(AstNode.tokenNode(parts[i], 1, i, i));
		}
		return result;
	}

	private static List<String> texts(List<AstNode> nodes) {
		return nodes.stream().map(AstNode::getText).toList();
	}

	// ---- フォールバック（functionRanges = 空リスト） ----

	@Test
	@DisplayName("空トークンリスト: 空を返す")
	void emptyInput() {
		assertThat(FunctionUnitSplitter.split(List.of(), List.of())).isEmpty();
	}

	@Test
	@DisplayName("functionRanges が空: 全トークンを 1 つの body 単位として返す（フォールバック）")
	void fallbackToSingleUnit() {
		List<AstNode> tokens = nodes("void f ( ) { return ; } int x ;");
		List<TokenUnit> units = FunctionUnitSplitter.split(tokens, List.of());

		assertThat(units).hasSize(1);
		assertThat(units.get(0).label()).isEqualTo(BODY);
		assertThat(texts(units.get(0).tokens())).containsExactly("void", "f", "(", ")", "{", "return", ";", "}", "int",
				"x", ";");
	}

	// ---- ParseTree ベース分割 ----

	@Test
	@DisplayName("1 関数: シグネチャとボディが 1 つの body 単位になる")
	void singleFunction() {
		// tokens: void(0) f(1) ((2) )(3) {(4) return(5) ;(6) }(7)
		// functionRange: [0, 7]
		List<AstNode> tokens = nodes("void f ( ) { return ; }");
		List<int[]> ranges = List.of(new int[]{0, 7});
		List<TokenUnit> units = FunctionUnitSplitter.split(tokens, ranges);

		assertThat(units).hasSize(1);
		assertThat(units.get(0).label()).isEqualTo(BODY);
		assertThat(texts(units.get(0).tokens())).containsExactly("void", "f", "(", ")", "{", "return", ";", "}");
	}

	@Test
	@DisplayName("2 関数: body + body の 2 単位")
	void twoFunctions() {
		// int(0) f(1) ((2) )(3) {(4) return(5) 1(6) ;(7) }(8) int(9) g(10) ((11) )(12)
		// {(13) return(14) 2(15) ;(16) }(17)
		List<AstNode> tokens = nodes("int f ( ) { return 1 ; } int g ( ) { return 2 ; }");
		List<int[]> ranges = List.of(new int[]{0, 8}, new int[]{9, 17});
		List<TokenUnit> units = FunctionUnitSplitter.split(tokens, ranges);

		assertThat(units).hasSize(2);
		assertThat(units.get(0).label()).isEqualTo(BODY);
		assertThat(texts(units.get(0).tokens())).containsExactly("int", "f", "(", ")", "{", "return", "1", ";", "}");
		assertThat(units.get(1).label()).isEqualTo(BODY);
		assertThat(texts(units.get(1).tokens())).containsExactly("int", "g", "(", ")", "{", "return", "2", ";", "}");
	}

	@Test
	@DisplayName("関数の前後にグローバル宣言: gap + body + gap の 3 単位")
	void gapBodyGap() {
		// int(0) g(1) ;(2) void(3) f(4) ((5) )(6) {(7) }(8) int(9) x(10) ;(11)
		List<AstNode> tokens = nodes("int g ; void f ( ) { } int x ;");
		List<int[]> ranges = List.of(new int[]{3, 8});
		List<TokenUnit> units = FunctionUnitSplitter.split(tokens, ranges);

		assertThat(units).hasSize(3);
		assertThat(units.get(0).label()).isEqualTo(GAP);
		assertThat(texts(units.get(0).tokens())).containsExactly("int", "g", ";");
		assertThat(units.get(1).label()).isEqualTo(BODY);
		assertThat(texts(units.get(1).tokens())).containsExactly("void", "f", "(", ")", "{", "}");
		assertThat(units.get(2).label()).isEqualTo(GAP);
		assertThat(texts(units.get(2).tokens())).containsExactly("int", "x", ";");
	}

	@Test
	@DisplayName("全単位を結合すると元のトークン列と一致する")
	void combinedEqualsOriginal() {
		List<AstNode> tokens = nodes("int g ; void f ( ) { sin ( x ) ; } int h ;");
		List<int[]> ranges = List.of(new int[]{3, 13});
		List<TokenUnit> units = FunctionUnitSplitter.split(tokens, ranges);

		List<String> combined = units.stream().flatMap(u -> u.tokens().stream()).map(AstNode::getText).toList();
		List<String> original = tokens.stream().map(AstNode::getText).toList();

		assertThat(combined).isEqualTo(original);
	}
}
