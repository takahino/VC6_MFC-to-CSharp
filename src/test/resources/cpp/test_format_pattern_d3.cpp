// Pattern D3: 混在ドットチェーン (Format→TrimRight→Left→IsEmpty)
// time.Format("%Y/%m/%d").TrimRight().Left(0).IsEmpty()

#include "stdafx.h"

void FormatTrimLeftIsEmptyExample()
{
    CTime time;

    time.Format("%Y/%m/%d").TrimRight().Left(0).IsEmpty();
}
