// テスト: 複合的な変換ケース
// 複数の変換ルールが混在・連鎖するパターン

#include "stdafx.h"
#include <math.h>

// ケース1: 型変換と AfxMessageBox の組み合わせ
// CString → string + AfxMessageBox → MessageBox.Show
void ValidateInput(CString input)
{
    if (input.IsEmpty())
    {
        AfxMessageBox("入力が空です", MB_OK | MB_ICONWARNING);
        return;
    }
    AfxMessageBox(input, MB_OK | MB_ICONINFORMATION);
}

// ケース2: BOOL と AfxMessageBox の組み合わせ
BOOL ConfirmDelete(CString filename)
{
    CString strMsg = "削除しますか: " + filename;
    int nResult = AfxMessageBox(strMsg, MB_YESNO | MB_ICONQUESTION);
    if (nResult == IDYES)
    {
        return TRUE;
    }
    return FALSE;
}

// ケース3: 数学関数と型変換の組み合わせ
// sin/cos → Math.Sin/Cos + BOOL/double
BOOL IsSmallAngle(double theta)
{
    double s = sin(theta);
    double c = cos(theta);
    if (fabs(s) < 0.01 && fabs(c) > 0.99)
    {
        return TRUE;
    }
    return FALSE;
}

// ケース4: pow のネスト引数 (ABSTRACT_PARAM が複合式)
double CalcNormalize(double x, double y, double z)
{
    double len = sqrt(pow(x, 2.0) + pow(y, 2.0) + pow(z, 2.0));
    return len;
}

// ケース5: AfxMessageBox に数学関数の結果を渡す
void ShowCalculationError(double base, double exp)
{
    double result = pow(base, exp);
    if (result < 0.0)
    {
        AfxMessageBox(FormatResult(result), MB_OK | MB_ICONERROR);
    }
}

// ケース6: NULL と型変換の組み合わせ
BOOL InitBuffer(LPVOID* ppBuf, DWORD dwSize)
{
    if (ppBuf == NULL)
    {
        return FALSE;
    }
    *ppBuf = NULL;
    return TRUE;
}

// ケース7: 複数 AfxMessageBox が同一関数内に存在
void HandleMultipleErrors(BOOL bFileError, BOOL bNetError)
{
    if (bFileError == TRUE)
    {
        AfxMessageBox("ファイルエラーが発生しました", MB_OK | MB_ICONERROR);
    }
    if (bNetError == TRUE)
    {
        AfxMessageBox("ネットワークエラーが発生しました", MB_OK | MB_ICONERROR);
    }
}

// ケース8: 深くネストした引数を持つ AfxMessageBox（引数内に TRUE を含む → 右端優先で TRUE が先に変換される）
void ShowDeepNestError(int a, int b, int c)
{
    AfxMessageBox(BuildMsg(CalcSum(Add(a, b), Multiply(b, c)), TRUE), MB_OK | MB_ICONERROR);
}
