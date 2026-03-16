// Pattern D1: 純粋Formatドットチェーン
// time.Format("%Y/%m/%d").Format("[%s]").Format("date: %s").Format("LOG: %s")

#include "stdafx.h"

void FormatDotChainExample()
{
    CTime time;

    time.Format("%Y/%m/%d").Format("[%s]").Format("date: %s").Format("LOG: %s");
}
