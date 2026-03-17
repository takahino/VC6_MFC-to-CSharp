// Pattern D2: 混在ドットチェーン (Format→TrimRight→Left→Find)
// time.Format("%Y/%m/%d").TrimRight().Left(7).Find("/")

#include "stdafx.h"

void FormatTrimLeftFindExample()
{
    CTime time;

    time.Format("%Y/%m/%d").TrimRight().Left(7).Find("/");
}
