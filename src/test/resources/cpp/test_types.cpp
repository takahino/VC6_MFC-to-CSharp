// テスト: MFC / Win32 型 → C# 型変換
// VC++6 特有の型定義から C# の標準型へのマッピング

#include "stdafx.h"

// ケース1: CString → string
void ProcessString()
{
    CString strName = "Taro";
    CString strMessage = "Hello, " + strName;
}

// ケース2: BOOL / TRUE / FALSE → bool / true / false
void ProcessBool()
{
    BOOL bFlag = FALSE;
    BOOL bReady = TRUE;
    if (bFlag == FALSE)
    {
        bFlag = TRUE;
    }
}

// ケース3: DWORD / UINT / WORD / BYTE → uint / uint / ushort / byte
void ProcessUnsignedTypes()
{
    DWORD dwSize = 1024;
    UINT nCount = 100;
    WORD wValue = 0xFF;
    BYTE bData = 0x0A;
}

// ケース4: LONG / INT → long / int
void ProcessSignedTypes()
{
    LONG lOffset = -512;
    INT nIndex = 42;
}

// ケース5: LPCTSTR / LPCSTR / LPSTR → string
void ProcessStringPointers(LPCTSTR lpText, LPCSTR lpszAnsi)
{
    LPSTR lpBuffer = NULL;
}

// ケース6: LPVOID → object
void ProcessVoidPointer()
{
    LPVOID pData = NULL;
}

// ケース7: NULL → null
void ProcessNull()
{
    CString* pStr = NULL;
    LPVOID p = NULL;
    if (p == NULL)
    {
        pStr = NULL;
    }
}

// ケース8: 複合的な型変換 (引数と戻り値)
BOOL CheckLength(CString text, DWORD maxLen)
{
    DWORD dwLen = text.GetLength();
    if (dwLen > maxLen)
    {
        return FALSE;
    }
    return TRUE;
}
