# VC++ MFC to C# Converter

Visual C++ 6.0 MFC で書かれた C++ コードを C# コードへ変換するツール（技術検証実装）。
ANTLR4 による C++ パースと、変換定義ファイル (`.rule`) を用いたトークンレベルのパターンマッチング変換を実現する。

## 特徴

- **シンプルな変換ルール DSL**: `from:` / `to:` の宣言的な記述だけでパターンを追加できる
- **抽象化トークン**: `ABSTRACT_PARAM00`〜`ABSTRACT_PARAM99` / `RECEIVER` で任意のトークン列・レシーバー連鎖をキャプチャ
- **多段パイプライン**: PRE → DYNAMIC → MAIN → POST → COMBY の 5 フェーズ構成
- **インライン単体テスト**: ルールファイルに `test:` / `assrt:` を書くとビルド時に自動検証される
- **変換過程の可視化**: フェーズ遷移 diff HTML (`phases.html`)、変換サマリーレポート、Excel 出力
- **ディレクトリバッチ変換**: ディレクトリを指定すると並列で一括変換
- **パターン発見モード**: 未変換パターンを統計的に発見する `--discover` モード

## 動作要件

| 項目 | バージョン |
|------|-----------|
| Java | 21 以上 |
| Maven | 3.8 以上 |

## ビルド

```bash
mvn package -DskipTests
```

`target/cpp-to-csharp-1.0-SNAPSHOT-shaded.jar` が生成される。

テストを含めてビルドする場合:

```bash
mvn package
```

333 テスト全パスを確認してからリリースすること。

## 使い方

### 単一ファイル変換

```bash
java -jar target/cpp-to-csharp-1.0-SNAPSHOT-shaded.jar <入力C++ファイル> [ルールディレクトリ]
```

```bash
# 例: クラスパス内蔵ルールを使用
java -jar cpp-to-csharp.jar MyDialog.cpp

# 例: 外部ルールディレクトリを指定
java -jar cpp-to-csharp.jar MyDialog.cpp path/to/rules/

# Excel 出力を無効化（.xlsx を生成しない）
java -jar cpp-to-csharp.jar --no-excel MyDialog.cpp
```

### ディレクトリバッチ変換

```bash
java -jar cpp-to-csharp.jar src/dialogs/ path/to/rules/
```

ディレクトリ内の `.cpp` / `.c` / `.h` / `.i` を並列変換し、`batch_conversion_summary.txt` を出力する。

### パターン発見モード

```bash
java -jar cpp-to-csharp.jar --discover src/dialogs/ path/to/rules/
```

既存ルールで変換されないパターンを統計的に収集してレポートを出力する。

## 出力ファイル

変換後、入力ファイルと同じディレクトリに以下が出力される。

| ファイル | 内容 |
|----------|------|
| `basename.cs` | 変換後 C# コード |
| `basename.report.txt` | 変換サマリー（ニアミス候補・ルール有効度統計） |
| `basename.report.html` | 変換前後の diff HTML |
| `basename.phases.html` | フェーズ遷移ジャーニー HTML |
| `basename.treedump.txt` | AST 木ダンプ（デバッグ用） |
| `basename.xlsx` | 変換過程の Excel 可視化（`--no-excel` で無効化可能） |

## 変換ルールファイル

`src/main/resources/rules/` 配下にフェーズ別サブディレクトリを配置する。
ディレクトリ名の数字プレフィックス昇順に適用される。

### `.rule` ファイル（メイン変換）

```
# AfxMessageBox → MessageBox.Show
from: AfxMessageBox ( ABSTRACT_PARAM00 , MB_OK | MB_ICONERROR ) ;
to: MessageBox.Show(ABSTRACT_PARAM00, "", MessageBoxButtons.OK, MessageBoxIcon.Error);
test: void f() { AfxMessageBox("エラー", MB_OK | MB_ICONERROR); }
assrt: void f ( ) { MessageBox.Show("エラー", "", MessageBoxButtons.OK, MessageBoxIcon.Error); }
```

- `ABSTRACT_PARAM00`〜`ABSTRACT_PARAM99`: 括弧深度を考慮して任意のトークン列にマッチ
- `RECEIVER`: `obj.method().field` のような postfix チェーン全体にマッチ
- `test:` / `assrt:` は省略可能。記述するとビルド時に `RuleValidationTest` で自動検証

### `.mrule` ファイル（PRE/POST フェーズ補助変換）

複数の `find:` / `replace:` spec を 1 ルールに持つ多段マッチング用。

### `.crule` ファイル（COMBY フェーズ）

トークン列変換では困難な多行構造・テンプレート型引数などをテキストパターンで変換する。

```
# List<T> → IList<T>
from: List<:[type]>
to: IList<:[type]>
```

### `.drule` ファイル（動的ルール）

PRE フェーズ完了後のトークンから値を収集し、動的にルールを生成する。
クラス名・列挙型メンバ名などコンパイル時に不明な値の変換に有効。

## アーキテクチャ

```
C++ ソース
    │
    ▼ ANTLR4 (CPP14Lexer / CPP14Parser)
ParseTree
    │
    ▼ DFS トークン列構築
フラットトークン列
    │
    ├── PRE フェーズ (.mrule)     ← トークン正規化
    ├── DYNAMIC フェーズ (.drule) ← 動的ルール生成
    ├── MAIN フェーズ (.rule)     ← 主変換（関数単位で並列）
    ├── POST フェーズ (.mrule)    ← 後処理正規化
    └── COMBY フェーズ (.crule)  ← テキストパターン後処理
    │
    ▼
C# コード + レポート
```


## テスト

```bash
mvn test
```

| テストスイート | 内容 |
|---------------|------|
| `RuleValidationTest` | `.rule` ファイルのインライン `test:` / `assrt:` を全件検証 |
| `ChainAndNestRegressionTest` | ドットチェーン・ネスト・拒否系の回帰テスト |
| `CombyRuleValidationTest` | `.crule` ファイルのインラインテスト検証 |
| `IntegrationTest` / `ThreePassIntegrationTest` | パイプライン全体の結合テスト |
| その他ユニットテスト | `PatternMatcher`, `MultiReplaceMatcher`, `CombyMatcher`, `DynamicRuleGenerator` 等 |

## ライセンス

[BSD 3-Clause License](LICENSE)
