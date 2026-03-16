// テスト: 複数行にまたがる LPSTR 宣言と malloc 代入の相関を見て正規化する

#include "stdafx.h"
#include <stdlib.h>

void NormalizeMallocAssignedString()
{
    LPSTR pszName;
    pszName = (char*)malloc(256);
}
