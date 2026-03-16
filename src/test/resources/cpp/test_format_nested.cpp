// テスト: CString::Format / CTime::Format の 4 重ネスト
// MigrationHelper.Format への変換（末端 L1 → L4 の順に適用）

#include "stdafx.h"

void FormatNestedExample()
{
    CString result_str;
    CString str1;
    CString str2;
    CTime time;

    result_str.Format("LOG: %s",
        str2.Format(">> %s",
            str1.Format("[%s]",
                time.Format("%Y/%m/%d"))));
}
