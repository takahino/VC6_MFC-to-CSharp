// テスト: ToBool() 拡張メソッド適用
// C++ の if/while 条件式（int, ポインタ等）を C# 互換の .ToBool() に変換
// 前提: CppCompatExtensions.ToBool() が定義済み

#include "stdafx.h"

// ケース1: if (int) - 整数変数を条件に
void TestIfInt()
{
    INT count = 0;
    if (count)
    {
        count = 1;
    }
}

// ケース2: if (関数戻り値) - int を返す関数
INT GetCount();
void TestIfFunction()
{
    if (GetCount())
    {
        // 処理
    }
}

// ケース3: if (ポインタ) - NULL チェック (LPVOID → object)
void TestIfPointer(LPVOID p)
{
    if (p)
    {
        // ポインタ有効
    }
}

// ケース4: if (CString/オブジェクト) - 参照型
void TestIfString(CString str)
{
    if (str)
    {
        // 文字列有効
    }
}

// ケース5: while (int)
void TestWhileInt()
{
    INT n = 10;
    while (n)
    {
        n--;
    }
}

// ケース6: for ( ; int ; )
void TestForCondition()
{
    INT i = 0;
    for (; i < 10; i++)
    {
        // ループ
    }
    INT j = 5;
    for (; j; j--)
    {
        // カウントダウン
    }
}

// ケース7: do { } while (int)
void TestDoWhileInt()
{
    INT k = 3;
    do
    {
        k--;
    } while (k);
}
