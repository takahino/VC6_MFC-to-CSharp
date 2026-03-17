// テスト: malloc 代入のない LPSTR 宣言は mrule でマッチしない（変換対象外）
#include "stdafx.h"

void NoMalloc()
{
    LPSTR pszLiteral = "hello";
}
