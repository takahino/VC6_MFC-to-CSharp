// C/C++/C# ファミリー向け汎用軽量レキサー。
// COMBY ホールマッチングの除外領域（コメント・文字列リテラル）を識別する目的のみに使う。
// パーサー不要。Other ルールで残り全文字を1文字ずつ吸収するため、
// どんな言語のソースでも必ずトークン化できる。
lexer grammar CombyLexer;

BlockComment      : '/*' .*? '*/';
LineComment       : '//' ~[\r\n]*;
VerbatimStringLit : '@"' (~'"' | '""')* '"';   // C# verbatim string（改行含む・"" でエスケープ）
StringLit         : '"' ('\\' . | ~[\\"\r\n])* '"';
CharLit           : '\'' ('\\' . | ~[\\'\r\n])* '\'';
Other             : .;
