// テスト: 宣言と代入の間に別ロジックが入るパターン（skip: でスキップマッチ）

#include "stdafx.h"
#include <stdlib.h>

void MallocWithLogicBetween()
{
    LPSTR pszBuf;
    int n = 0;
    pszBuf = (char*)malloc(1024);
}
