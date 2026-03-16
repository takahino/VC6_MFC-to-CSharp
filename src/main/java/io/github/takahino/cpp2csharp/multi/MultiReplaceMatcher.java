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

package io.github.takahino.cpp2csharp.multi;

import io.github.takahino.cpp2csharp.matcher.MatchResult;
import io.github.takahino.cpp2csharp.matcher.PatternMatcher;
import io.github.takahino.cpp2csharp.mrule.MRuleFindSpec;
import io.github.takahino.cpp2csharp.mrule.MRuleScope;
import io.github.takahino.cpp2csharp.mrule.MultiReplaceRule;
import io.github.takahino.cpp2csharp.rule.ConversionRule;
import io.github.takahino.cpp2csharp.tree.AstNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * マルチ置換ルールをトークンリストに対してマッチングするクラス。
 *
 * <h2>マッチングアルゴリズム</h2>
 * <ol>
 * <li>各ルールの最初の find spec を全開始位置で探索する</li>
 * <li>各後続 find spec は前の spec のマッチ終端から連続/スキップで探索する</li>
 * <li>全 spec のマッチが成功した場合のみ {@link MultiReplaceMatchResult} を追加する</li>
 * </ol>
 */
public class MultiReplaceMatcher {
	private final PatternMatcher patternMatcher = new PatternMatcher();

	/**
	 * 全ルールをトークンリストに対して試行し、マッチ結果を全て返す。
	 *
	 * @param rules
	 *            試行するルールリスト
	 * @param tokenNodes
	 *            フラットなトークンノード列
	 * @return 全マッチ結果のリスト
	 */
	public List<MultiReplaceMatchResult> matchAll(List<MultiReplaceRule> rules, List<AstNode> tokenNodes) {
		List<String> tokens = tokenNodes.stream().map(AstNode::getText).toList();
		List<int[]> blocks = null; // lazy-computed
		List<MultiReplaceMatchResult> results = new ArrayList<>();

		for (MultiReplaceRule rule : rules) {
			if (rule.getFindSpecs().isEmpty())
				continue;

			if (rule.getScope() == MRuleScope.BLOCK && blocks == null) {
				blocks = computeBlocks(tokens);
			}

			List<int[]> effectiveBlocks = rule.getScope() == MRuleScope.BLOCK ? blocks : null;
			List<MultiReplaceMatchResult> ruleResults = matchRule(rule, tokens, effectiveBlocks);
			results.addAll(ruleResults);
		}
		return results;
	}

	private List<MultiReplaceMatchResult> matchRule(MultiReplaceRule rule, List<String> tokens, List<int[]> blocks) {
		List<MultiReplaceMatchResult> results = new ArrayList<>();
		List<MRuleFindSpec> specs = rule.getFindSpecs();

		// Search for the first findSpec over all valid starting positions
		MRuleFindSpec firstSpec = specs.get(0);
		ConversionRule firstRule = toConversionRule(firstSpec, rule.getSourceFile());
		List<MatchResult> firstMatches = patternMatcher.matchAll(List.of(firstRule), tokens);

		for (MatchResult firstMatch : firstMatches) {
			if (blocks != null && !isWithinAnyBlock(firstMatch.getStartIndex(), blocks))
				continue;

			// Try to match subsequent specs
			List<MatchResult> stepMatches = new ArrayList<>();
			stepMatches.add(firstMatch);
			Map<Integer, List<String>> sharedCaptures = new HashMap<>(firstMatch.getCaptures());

			boolean success = true;
			for (int i = 1; i < specs.size(); i++) {
				MRuleFindSpec spec = specs.get(i);
				ConversionRule specRule = toConversionRule(spec, rule.getSourceFile());
				MatchResult prev = stepMatches.get(stepMatches.size() - 1);

				MatchResult nextMatch;
				if (!spec.skipBefore()) {
					// Consecutive: must start exactly at prev.endIndex
					nextMatch = findMatchAt(specRule, tokens, prev.getEndIndex(), sharedCaptures);
				} else {
					// Non-consecutive: search from prev.endIndex onward (within block)
					nextMatch = findMatchFrom(specRule, tokens, prev.getEndIndex(), blocks, firstMatch.getStartIndex(),
							sharedCaptures);
				}

				if (nextMatch == null) {
					success = false;
					break;
				}
				if (!capturesAreCompatible(sharedCaptures, nextMatch.getCaptures())) {
					success = false;
					break;
				}
				stepMatches.add(nextMatch);
				sharedCaptures.putAll(nextMatch.getCaptures());
			}

			if (success) {
				int blockStart = -1, blockEnd = -1;
				if (blocks != null) {
					for (int[] b : blocks) {
						if (b[0] <= firstMatch.getStartIndex() && firstMatch.getStartIndex() < b[1]) {
							blockStart = b[0];
							blockEnd = b[1];
							break;
						}
					}
				}
				results.add(new MultiReplaceMatchResult(rule, stepMatches, sharedCaptures, blockStart, blockEnd));
			}
		}
		return results;
	}

	private MatchResult findMatchAt(ConversionRule rule, List<String> tokens, int startIndex,
			Map<Integer, List<String>> sharedCaptures) {
		List<MatchResult> matches = patternMatcher.matchAll(List.of(rule), tokens);
		for (MatchResult m : matches) {
			if (m.getStartIndex() == startIndex && capturesAreCompatible(sharedCaptures, m.getCaptures())) {
				return m;
			}
		}
		return null;
	}

	private MatchResult findMatchFrom(ConversionRule rule, List<String> tokens, int fromIndex, List<int[]> blocks,
			int blockAnchor, Map<Integer, List<String>> sharedCaptures) {
		List<MatchResult> matches = patternMatcher.matchAll(List.of(rule), tokens);
		for (MatchResult m : matches) {
			if (m.getStartIndex() < fromIndex)
				continue;
			if (blocks != null && !isInSameBlock(blockAnchor, m.getStartIndex(), blocks))
				continue;
			if (capturesAreCompatible(sharedCaptures, m.getCaptures()))
				return m;
		}
		return null;
	}

	private boolean capturesAreCompatible(Map<Integer, List<String>> existing, Map<Integer, List<String>> candidate) {
		for (Map.Entry<Integer, List<String>> entry : candidate.entrySet()) {
			List<String> existingValue = existing.get(entry.getKey());
			if (existingValue != null && !existingValue.equals(entry.getValue())) {
				return false;
			}
		}
		return true;
	}

	private boolean isWithinAnyBlock(int index, List<int[]> blocks) {
		for (int[] b : blocks) {
			if (b[0] <= index && index < b[1])
				return true;
		}
		return false;
	}

	private boolean isInSameBlock(int anchorIndex, int index, List<int[]> blocks) {
		for (int[] b : blocks) {
			if (b[0] <= anchorIndex && anchorIndex < b[1]) {
				return b[0] <= index && index < b[1];
			}
		}
		return false;
	}

	/**
	 * depth 0 の { } ブロック境界を計算する。
	 *
	 * @param tokens
	 *            トークンリスト
	 * @return ブロックの {startIndex, endIndex(exclusive)} 配列リスト
	 */
	private List<int[]> computeBlocks(List<String> tokens) {
		List<int[]> blocks = new ArrayList<>();
		int depth = 0;
		int blockStart = -1;
		for (int i = 0; i < tokens.size(); i++) {
			String t = tokens.get(i);
			if ("{".equals(t)) {
				if (depth == 0) {
					blockStart = i;
				}
				depth++;
			} else if ("}".equals(t)) {
				if (depth > 0) {
					depth--;
					if (depth == 0 && blockStart >= 0) {
						blocks.add(new int[]{blockStart, i + 1});
						blockStart = -1;
					}
				}
			}
		}
		return blocks;
	}

	private ConversionRule toConversionRule(MRuleFindSpec spec, String sourceFile) {
		return new ConversionRule(sourceFile, spec.pattern(), spec.replacement(), List.of());
	}
}
