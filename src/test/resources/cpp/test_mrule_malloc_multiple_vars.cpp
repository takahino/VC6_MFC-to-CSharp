// テスト: 同一ブロック内の複数 LPSTR 変数がそれぞれ独立して正規化される
#include "stdafx.h"
#include <stdlib.h>

void MultipleVars()
{
    LPSTR pszName;
    LPSTR pszPath;
    pszName = (char*)malloc(256);
    pszPath = (char*)malloc(512);
}
