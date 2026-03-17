// Pattern 2: 異種4重ネスト (Format → Find → GetAt → Format)
// msg.Format("[%c]", str.GetAt(str.Find(time.Format("%Y/%m/%d"))));

#include "stdafx.h"

void FormatFindGetAtFormatExample()
{
    CString msg;
    CString str;
    CTime time;

    msg.Format("[%c]", str.GetAt(str.Find(time.Format("%Y/%m/%d"))));
}
