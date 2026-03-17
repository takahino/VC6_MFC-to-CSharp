// Pattern 3: 複数引数4重ネスト (2本の L1 ブランチ)
// result_str.Format("LOG=[%s]", msg.Format("RESULT=%s", tmp.Format("%s,%s", time.Format(...), dt.Format(...))));

#include "stdafx.h"

void FormatMultiArgExample()
{
    CString result_str;
    CString msg;
    CString tmp;
    CTime time;
    CTime dt;

    result_str.Format("LOG=[%s]",
        msg.Format("RESULT=%s",
            tmp.Format("%s,%s",
                time.Format("%Y/%m/%d"),
                dt.Format("%H:%M:%S"))));
}
