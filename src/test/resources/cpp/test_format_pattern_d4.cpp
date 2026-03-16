// Pattern D4: ドットチェーン vs 引数ネスト 構造対比
// [ドット] time.Format("%Y/%m/%d").Find("/")
// [ネスト] str.Find(time.Format("%Y/%m/%d"))

#include "stdafx.h"

void FormatDotVsNestDot()
{
    CTime time;

    time.Format("%Y/%m/%d").Find("/");
}

void FormatDotVsNestArg()
{
    CString str;
    CTime time;

    str.Find(time.Format("%Y/%m/%d"));
}
