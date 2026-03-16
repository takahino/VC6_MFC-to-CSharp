// テスト: 3 find spec / 2 skip: で離れた位置の3文を相関付けて正規化する
// HANDLE 宣言 → CreateFile 代入 → CloseHandle 解放 の3段相関
#include "stdafx.h"

void TripleSkipNormalization()
{
    HANDLE hLog;
    int n = GetLineCount();
    LPCSTR path = GetLogPath();
    hLog = CreateFile(path);
    WriteLog(hLog, "start");
    ProcessLines(hLog, n);
    CloseHandle(hLog);
}
