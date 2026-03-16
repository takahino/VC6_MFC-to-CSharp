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

import io.github.takahino.cpp2csharp.comby.CombyEngine;
import io.github.takahino.cpp2csharp.comby.CombyRule;
import io.github.takahino.cpp2csharp.comby.CombyTransformer;
import io.github.takahino.cpp2csharp.grammar.CPP14Lexer;
import io.github.takahino.cpp2csharp.grammar.CPP14Parser;
import io.github.takahino.cpp2csharp.mrule.MultiReplaceRule;
import io.github.takahino.cpp2csharp.output.ExcelOutputConfig;
import io.github.takahino.cpp2csharp.retokenize.Retokenizer;
import io.github.takahino.cpp2csharp.rule.ConversionRule;
import io.github.takahino.cpp2csharp.rule.ConversionRuleLoader;
import io.github.takahino.cpp2csharp.rule.ConversionRuleLoader.ThreePassRuleSet;
import io.github.takahino.cpp2csharp.transform.Transformer;
import io.github.takahino.cpp2csharp.tree.AstNode;
import io.github.takahino.cpp2csharp.tree.ParseTreeDumper;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * VC++6 MFC C++ コードを C# へ変換するメインコンバータクラス。
 *
 * <h2>変換フロー</h2>
 * <ol>
 *   <li>C++ ソースコードを ANTLR4 CPP14 文法でパース</li>
 *   <li>ParseTree を DFS 走査して初期トークンノード列を構築</li>
 *   <li>変換ルールファイルを読み込む</li>
 *   <li>{@link Transformer} でフラットトークンリスト変換を実施</li>
 *   <li>C# コード文字列を返す</li>
 * </ol>
 *
 * <h2>スレッド安全性</h2>
 * <p>同一インスタンスの {@code convertSource} / {@code convertFile} を複数スレッドから同時に呼んではならない。
 * 複数ファイルを並列変換する場合は、ファイルごとに {@code new CppToCSharpConverter()} すること。</p>
 */
public class CppToCSharpConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(CppToCSharpConverter.class);

    private final ConversionRuleLoader ruleLoader;
    private final Transformer transformer;
    private final boolean excelEnabled;
    private final CombyEngine combyEngine;

    /**
     * コンストラクタ。デフォルト設定で初期化する。
     * Excel 可視化はシステムプロパティ {@code cpp2csharp.excel.enabled} で制御（デフォルト true）。
     * マルチスレッド化時: 1 converter = 1 Transformer。並列時は converter をファイルごとに分離すること。
     */
    public CppToCSharpConverter() {
        this(ExcelOutputConfig.defaultConfig().isEnabled());
    }

    /**
     * コンストラクタ。Excel 可視化の有効/無効を指定可能。
     *
     * @param excelEnabled Excel 可視化出力を有効にする場合 true
     */
    public CppToCSharpConverter(boolean excelEnabled) {
        this.ruleLoader = new ConversionRuleLoader();
        this.transformer = new Transformer();
        this.excelEnabled = excelEnabled;
        this.combyEngine = new CombyTransformer();
    }

    /**
     * コンストラクタ（テスト用）。Transformer を注入可能。
     *
     * @param transformer 使用する Transformer（maxPasses 等を指定済みのもの）
     */
    public CppToCSharpConverter(Transformer transformer) {
        this.ruleLoader = new ConversionRuleLoader();
        this.transformer = Objects.requireNonNull(transformer, "transformer が null です");
        this.excelEnabled = true;
        this.combyEngine = new CombyTransformer();
    }

    /**
     * コンストラクタ（差し替え用）。CombyEngine を外部から注入する。
     * 将来 comby CLI へ切り替える場合はこのコンストラクタを使用する。
     *
     * <pre>{@code
     * // 将来の使用例:
     * var converter = new CppToCSharpConverter(new CliCombyEngine(Path.of("/usr/local/bin/comby")));
     * }</pre>
     *
     * @param combyEngine 使用する CombyEngine 実装
     */
    public CppToCSharpConverter(CombyEngine combyEngine) {
        this.ruleLoader = new ConversionRuleLoader();
        this.transformer = new Transformer();
        this.excelEnabled = ExcelOutputConfig.defaultConfig().isEnabled();
        this.combyEngine = Objects.requireNonNull(combyEngine, "combyEngine が null です");
    }

    /**
     * C++ ソースコード文字列を変換し、C# コードを返す。
     * クラスパス上の rules/ ディレクトリをフェーズ別に読み込み、[01]_*, [02]_* の順で適用する。
     *
     * @param cppSource C++ ソースコード文字列
     * @return 変換後の C# コード文字列
     * @throws IOException ルールファイルの読み込みに失敗した場合
     */
    public ConversionResult convertSource(String cppSource) throws IOException {
        List<List<ConversionRule>> rulesByPhase = ruleLoader.loadAllFromResourcesByPhase();
        return convertSourceWithPhases(cppSource, rulesByPhase);
    }

    /**
     * C++ ソースコード文字列を3パス構成で変換し、C# コードを返す。
     *
     * <p>pre → main → post の3フェーズで変換を行い、各フェーズ境界で再トークン化する。</p>
     *
     * @param cppSource C++ ソースコード文字列
     * @return 変換後の C# コード文字列
     * @throws IOException ルールファイルの読み込みに失敗した場合
     */
    public ConversionResult convertSourceThreePass(String cppSource) throws IOException {
        ThreePassRuleSet ruleSet = ruleLoader.loadThreePassRules();
        return convertSourceThreePassInternal(cppSource, ruleSet);
    }

    /**
     * C++ ソースコード文字列を指定した3パスルールセットで変換する。
     *
     * @param cppSource C++ ソースコード文字列
     * @param ruleSet   3パスルールセット
     * @return 変換結果
     */
    public ConversionResult convertSourceThreePass(String cppSource, ThreePassRuleSet ruleSet) {
        return convertSourceThreePassInternal(cppSource, ruleSet);
    }

    private ConversionResult convertSourceThreePassInternal(String cppSource, ThreePassRuleSet ruleSet) {
        Objects.requireNonNull(cppSource, "cppSource が null です");
        Objects.requireNonNull(ruleSet, "ruleSet が null です");

        LOGGER.info("C++ → C# 3パス変換を開始します (pre={}, main={}, post={} フェーズ, comby={} フェーズ)",
                ruleSet.prePhases().size(), ruleSet.mainPhases().size(), ruleSet.postPhases().size(),
                ruleSet.combyPhases().size());

        // Step 1: ANTLR4 でパース
        CPP14Lexer lexer = new CPP14Lexer(CharStreams.fromString(cppSource));
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        CPP14Parser parser = new CPP14Parser(tokenStream);

        CollectingErrorListener errorListener = new CollectingErrorListener();
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);

        ParseTree parseTree = parser.translationUnit();

        if (errorListener.hasErrors()) {
            LOGGER.warn("パースエラーが発生しました: " + errorListener.getErrors());
        }

        tokenStream.fill();
        Map<Integer, List<String>> commentsBeforeToken = Retokenizer.buildCommentsMap(tokenStream);

        List<AstNode> tokenNodes = extractTokensFromParseTree(parseTree);
        String initialTreeDump = new ParseTreeDumper().dump(parseTree, parser.getRuleNames(), tokenStream);

        LOGGER.info("初期トークン数: {}", tokenNodes.size());

        List<PhaseTransformLog> phaseTransformLogs = new ArrayList<>();
        List<PhaseSnapshot> snapshots = new ArrayList<>();

        // Step 2〜7: パイプライン実行
        snapshots.add(new PhaseSnapshot("元のC++", cppSource));

        List<int[]> functionRanges = new ArrayList<>();
        List<String> functionSignatures = new ArrayList<>();
        collectFunctionInfo(parseTree, tokenStream, functionRanges, functionSignatures);
        List<ConversionPhase> pipeline = buildPipeline(ruleSet, functionRanges);
        PhaseExecutionContext ctx = new PhaseExecutionContext(
                tokenNodes, commentsBeforeToken, cppSource, excelEnabled);

        List<String> unitSourceDumps = List.of();
        List<String> unitOutputDumps = List.of();
        List<UnitLabel> unitLabels = List.of();
        for (ConversionPhase phase : pipeline) {
            if (phase instanceof MainPhase) {
                // PRE フェーズ完了後のトークンノードをユニット分割してデバッグテキストを生成
                unitSourceDumps = buildUnitSourceDumps(ctx.tokenNodes(), functionRanges, ctx.commentsBeforeToken());
            }
            PhaseExecutionResult result = phase.execute(ctx);
            if (phase instanceof MainPhase && !result.unitOutputDumps().isEmpty()) {
                unitOutputDumps = result.unitOutputDumps();
                unitLabels = result.unitLabels();
            }
            snapshots.addAll(result.snapshots());
            phaseTransformLogs.addAll(result.logs());
            ctx = new PhaseExecutionContext(
                    result.tokenNodes() != null ? result.tokenNodes() : ctx.tokenNodes(),
                    result.commentsBeforeToken(),
                    result.code(),
                    excelEnabled);
        }

        String csCode = ctx.currentCode();
        List<FunctionUnitEntry> functionUnitEntries =
                buildFunctionUnitEntries(functionSignatures, unitSourceDumps, unitOutputDumps, unitLabels);

        LOGGER.info("3パス変換完了 (エラー数: {})", transformer.getErrors().size());

        return new ConversionResult(csCode, transformer.getErrors(),
                errorListener.getErrors(), initialTreeDump,
                transformer.getAppliedTransforms(),
                transformer.getDiagnosticCandidates(),
                transformer.getVisualizationTempFile(),
                phaseTransformLogs,
                snapshots,
                unitSourceDumps,
                unitOutputDumps,
                functionUnitEntries);
    }

    /**
     * 変換パイプラインを構築する。
     *
     * <p>新しいフェーズを追加する場合はこのメソッドにのみ変更を加えればよい。
     * {@code convertSourceThreePassInternal()} 本体は変更不要。</p>
     *
     * @param ruleSet 3パスルールセット
     * @return 順序付きフェーズリスト
     */
    /**
     * ParseTree を DFS 走査して関数定義の stream index 範囲とシグネチャ文字列を一括収集する。
     *
     * <p>DoDataExchange を含む関数定義は除外する。
     * 両リストは同一走査順で同一インデックスが対応する。
     * 返す range 配列は {@code [startTokenIndex, stopTokenIndex]}（両端 inclusive）。</p>
     *
     * @param parseTree   ANTLR4 ParseTree のルート
     * @param tokenStream 対応する CommonTokenStream（シグネチャ抽出に使用）
     * @param ranges      収集先: 各関数定義の [startStreamIndex, stopStreamIndex]
     * @param signatures  収集先: 各関数定義のシグネチャ文字列（functionBody 直前まで）
     */
    private void collectFunctionInfo(ParseTree node, CommonTokenStream tokenStream,
                                     List<int[]> ranges, List<String> signatures) {
        if (node instanceof CPP14Parser.FunctionDefinitionContext funcDef) {
            if (!funcDef.getText().contains("DoDataExchange")) {
                ranges.add(new int[]{
                        funcDef.getStart().getTokenIndex(),
                        funcDef.getStop().getTokenIndex()
                });
                int sigStart = funcDef.getStart().getTokenIndex();
                int sigStop  = funcDef.functionBody().getStart().getTokenIndex() - 1;
                StringBuilder sb = new StringBuilder();
                for (int i = sigStart; i <= sigStop; i++) {
                    Token t = tokenStream.get(i);
                    if (t.getChannel() == Token.DEFAULT_CHANNEL && t.getType() != Token.EOF) {
                        if (!sb.isEmpty()) sb.append(' ');
                        sb.append(t.getText());
                    }
                }
                signatures.add(sb.toString().strip());
            }
            // 関数定義の中に入れ子の関数定義はないため return で探索を止める
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectFunctionInfo(node.getChild(i), tokenStream, ranges, signatures);
        }
    }

    /**
     * body ユニットのみを選別し、functionSignatures と index で対応づけて
     * {@link FunctionUnitEntry} リストを構築する。
     *
     * @param sigs        関数シグネチャリスト（{@link #extractFunctionSignatures} の結果）
     * @param sourceDumps 全ユニット変換前テキスト（gap/body 両方、PRE フェーズ後）
     * @param outputDumps 全ユニット変換後テキスト（gap/body 両方）
     * @param unitLabels  各ユニットの種別
     * @return body ユニットのみの FunctionUnitEntry リスト
     */
    private List<FunctionUnitEntry> buildFunctionUnitEntries(
            List<String> sigs, List<String> sourceDumps,
            List<String> outputDumps, List<UnitLabel> unitLabels) {
        List<FunctionUnitEntry> entries = new ArrayList<>();
        int sigIdx = 0;
        for (int i = 0; i < outputDumps.size() && sigIdx < sigs.size(); i++) {
            if (unitLabels.get(i) != UnitLabel.BODY) continue;
            String cppSig = sigs.get(sigIdx++);

            String cppText = i < sourceDumps.size() ? sourceDumps.get(i) : "";
            String csText  = outputDumps.get(i);

            int cppBrace = cppText.indexOf('{');
            String cppBeforeBrace = cppBrace >= 0 ? cppText.substring(0, cppBrace) : cppText;
            String cppBodyStr     = cppBrace >= 0 ? cppText.substring(cppBrace).strip() : "";

            int csBrace = csText.indexOf('{');
            String csBeforeBrace = csBrace >= 0 ? csText.substring(0, csBrace) : csText;
            String csBodyStr     = csBrace >= 0 ? csText.substring(csBrace).strip() : "";

            CommentAndSignature cppParts = splitCommentAndSignature(cppBeforeBrace);
            CommentAndSignature csParts  = splitCommentAndSignature(csBeforeBrace);

            FunctionUnitEntry.LinesDiff comment = new FunctionUnitEntry.LinesDiff(
                    toLines(cppParts.comment()), toLines(csParts.comment()));
            FunctionUnitEntry.LinesDiff body = new FunctionUnitEntry.LinesDiff(
                    toLines(cppBodyStr), toLines(csBodyStr));

            entries.add(new FunctionUnitEntry(cppSig, csParts.signature(), comment, body));
        }
        return List.copyOf(entries);
    }

    /** {@link #splitCommentAndSignature} の戻り値。コメント部とシグネチャ部を明示的に保持する。 */
    private record CommentAndSignature(String comment, String signature) {}

    /** テキストを行リストに変換する。空またはブランクの場合は空リストを返す。 */
    private static List<String> toLines(String text) {
        if (text == null || text.isBlank()) return List.of();
        return text.lines().toList();
    }

    /**
     * {@code '{'} より前のテキストをコメント部とシグネチャ部に分離する。
     *
     * <p>先頭から連続するコメント行（{@code //}・{@code /*}・{@code *} で始まる行）
     * および空白行をコメント部として取り出し、残りをシグネチャ部とする。</p>
     *
     * @param beforeBrace {@code '{'} より前のテキスト
     * @return コメント部とシグネチャ部（各要素は strip 済み）
     */
    private static CommentAndSignature splitCommentAndSignature(String beforeBrace) {
        if (beforeBrace == null || beforeBrace.isBlank()) {
            return new CommentAndSignature("", "");
        }
        String[] lines = beforeBrace.split("\n", -1);
        int sigStart = 0;
        boolean inBlockComment = false;
        for (int i = 0; i < lines.length; i++) {
            String s = lines[i].strip();
            if (inBlockComment) {
                if (s.contains("*/")) inBlockComment = false;
                sigStart = i + 1;
            } else if (s.isEmpty() || s.startsWith("//") || s.startsWith("*")) {
                sigStart = i + 1;
            } else if (s.startsWith("/*")) {
                if (!s.contains("*/")) inBlockComment = true;
                sigStart = i + 1;
            } else {
                break;
            }
        }
        String commentPart = String.join("\n",
                java.util.Arrays.copyOfRange(lines, 0, sigStart)).strip();
        String sigPart = String.join("\n",
                java.util.Arrays.copyOfRange(lines, sigStart, lines.length)).strip();
        return new CommentAndSignature(commentPart, sigPart);
    }

    /**
     * PRE フェーズ完了後のトークンノードをユニット分割し、各ユニットのソーステキストを返す。
     *
     * @param tokenNodes          PRE フェーズ後のトークンノード列
     * @param functionRanges      ParseTree から抽出した関数定義範囲
     * @param commentsBeforeToken コメントマップ
     * @return ユニット別ソーステキストリスト（gap/body 順）
     */
    private List<String> buildUnitSourceDumps(List<AstNode> tokenNodes, List<int[]> functionRanges,
                                               Map<Integer, List<String>> commentsBeforeToken) {
        List<TokenUnit> units = FunctionUnitSplitter.split(tokenNodes, functionRanges);
        return units.stream()
                .map(unit -> transformer.buildOutput(unit.tokens(), commentsBeforeToken))
                .toList();
    }

    private List<ConversionPhase> buildPipeline(ThreePassRuleSet ruleSet, List<int[]> functionRanges) {
        List<ConversionPhase> pipeline = new ArrayList<>();

        // PRE フェーズ
        int preNum = 1;
        for (List<MultiReplaceRule> rules : ruleSet.prePhases()) {
            pipeline.add(new PrePostPhase("PRE", preNum++, rules, false, transformer));
        }

        // MAIN フェーズ（動的ルール込み）
        pipeline.add(new MainPhase(ruleSet.mainPhases(), ruleSet.dynamicSpecs(), transformer, ruleLoader, functionRanges));

        // POST フェーズ（最初の 1 件のみ prependRetokenize=true: MAIN→POST 境界の再トークン化）
        boolean firstPost = true;
        int postNum = 1;
        for (List<MultiReplaceRule> rules : ruleSet.postPhases()) {
            pipeline.add(new PrePostPhase("POST", postNum++, rules, firstPost, transformer));
            firstPost = false;
        }

        // COMBY フェーズ
        int combyNum = 1;
        for (List<CombyRule> rules : ruleSet.combyPhases()) {
            pipeline.add(new CombyPhase(combyNum++, rules, combyEngine));
        }

        return pipeline;
    }

    /**
     * C++ ソースコード文字列を指定したルールで変換し、C# コードを返す。
     *
     * @param cppSource C++ ソースコード文字列
     * @param rules     使用する変換ルールリスト（単一フェーズとして扱う）
     * @return 変換結果
     */
    public ConversionResult convertSource(String cppSource, List<ConversionRule> rules) {
        return convertSourceWithPhases(cppSource, List.of(rules));
    }

    /**
     * C++ ソースコード文字列をフェーズ別ルールで変換し、C# コードを返す。
     *
     * @param cppSource    C++ ソースコード文字列
     * @param rulesByPhase フェーズごとのルールリスト（適用順）
     * @return 変換結果
     */
    public ConversionResult convertSourceWithPhases(String cppSource, List<List<ConversionRule>> rulesByPhase) {
        Objects.requireNonNull(cppSource, "cppSource が null です");
        Objects.requireNonNull(rulesByPhase, "rulesByPhase が null です");

        int totalRules = rulesByPhase.stream().mapToInt(List::size).sum();
        LOGGER.info("C++ → C# 変換を開始します (フェーズ数: " + rulesByPhase.size() + ", ルール数: " + totalRules + ")");

        // Step 1: ANTLR4 でパース
        CPP14Lexer lexer = new CPP14Lexer(CharStreams.fromString(cppSource));
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        CPP14Parser parser = new CPP14Parser(tokenStream);

        // エラーリスナーを追加
        CollectingErrorListener errorListener = new CollectingErrorListener();
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);

        ParseTree parseTree = parser.translationUnit();

        if (errorListener.hasErrors()) {
            LOGGER.warn("パースエラーが発生しました: " + errorListener.getErrors());
        }

        // Step 2: HIDDENチャネルのコメントを収集する
        // fill() で隠しチャネルを含む全トークンをロードする
        tokenStream.fill();
        Map<Integer, List<String>> commentsBeforeToken = Retokenizer.buildCommentsMap(tokenStream);

        // Step 3: ParseTree を DFS 走査して初期トークンノード列を構築
        List<AstNode> initialTokenNodes = extractTokensFromParseTree(parseTree);

        LOGGER.info(String.format("初期トークン数: %d", initialTokenNodes.size()));

        // 変換前の木をダンプ（ルール設計デバッグ用）
        String initialTreeDump = new ParseTreeDumper().dump(parseTree, parser.getRuleNames(), tokenStream);

        // Step 4: 変換実施（フェーズ順に適用、コメントマップを渡す）
        String csCode = transformer.transformWithPhases(initialTokenNodes, rulesByPhase, commentsBeforeToken, excelEnabled);

        LOGGER.info("変換完了 (エラー数: " + transformer.getErrors().size() + ")");

        List<PhaseSnapshot> snapshots = new ArrayList<>();
        snapshots.add(new PhaseSnapshot("元のC++", cppSource));
        List<String> mainSnaps = transformer.getMainPhaseSnapshots();
        for (int mi = 0; mi < mainSnaps.size(); mi++) {
            snapshots.add(new PhaseSnapshot("MAIN-" + (mi + 1), mainSnaps.get(mi)));
        }

        return new ConversionResult(csCode, transformer.getErrors(),
                errorListener.getErrors(), initialTreeDump,
                transformer.getAppliedTransforms(),
                transformer.getDiagnosticCandidates(),
                transformer.getVisualizationTempFile(),
                List.of(),
                snapshots);
    }

    /**
     * ANTLR ParseTree を DFS 走査して DEFAULT_CHANNEL のトークンノード列を構築する。
     *
     * @param parseTree パースツリーのルート
     * @return ソース順のトークンノードリスト
     */
    private List<AstNode> extractTokensFromParseTree(ParseTree parseTree) {
        List<AstNode> result = new ArrayList<>();
        collectTokenNodes(parseTree, result);
        return result;
    }

    private void collectTokenNodes(ParseTree node, List<AstNode> result) {
        // DoDataExchange メソッド定義をスキップ（MFC DDX/DDV マクロブロック、C# 変換不要）
        if (node instanceof CPP14Parser.FunctionDefinitionContext funcDef) {
            if (funcDef.getText().contains("DoDataExchange")) {
                return;
            }
        }
        if (node instanceof TerminalNode terminal) {
            Token token = terminal.getSymbol();
            if (token.getChannel() == Token.DEFAULT_CHANNEL) {
                result.add(AstNode.tokenNode(token.getText(), token.getLine(),
                        token.getCharPositionInLine(), token.getTokenIndex()));
            }
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectTokenNodes(node.getChild(i), result);
        }
    }

    /**
     * C++ ソースファイルを変換し、C# コードを返す。
     *
     * @param cppFile C++ ソースファイルのパス
     * @param rules   使用する変換ルールリスト
     * @return 変換結果
     * @throws IOException ファイル読み込みに失敗した場合
     */
    public ConversionResult convertFile(Path cppFile, List<ConversionRule> rules)
            throws IOException {
        String cppSource = Files.readString(cppFile, StandardCharsets.UTF_8);
        return convertSource(cppSource, rules);
    }
}
