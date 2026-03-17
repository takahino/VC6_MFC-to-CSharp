// テスト: AfxMessageBox → MessageBox.Show 変換
// VC++6 MFC のメッセージボックス呼び出しパターン集

#include "stdafx.h"
#include "MyApp.h"

// ケース1: 単純な文字列リテラルによる AfxMessageBox (MB_OK | MB_ICONERROR)
void ShowSimpleError()
{
    AfxMessageBox("ファイルが見つかりません", MB_OK | MB_ICONERROR);
}

// ケース2: 変数を引数にした AfxMessageBox (MB_OK | MB_ICONWARNING)
void ShowVariableWarning(const char* msg)
{
    AfxMessageBox(msg, MB_OK | MB_ICONWARNING);
}

// ケース3: 文字列結合式を引数にした AfxMessageBox (MB_OK | MB_ICONERROR)
void ShowCompositeError(const char* filename, int lineNo)
{
    AfxMessageBox(BuildMessage(filename, lineNo) + " でエラーが発生しました", MB_OK | MB_ICONERROR);
}

// ケース4: 引数1つ (デフォルト MB_OK) の AfxMessageBox
void ShowDefaultMessage()
{
    AfxMessageBox("処理が完了しました");
}

// ケース5: MB_OK | MB_ICONINFORMATION
void ShowInfoMessage(const char* detail)
{
    AfxMessageBox(detail, MB_OK | MB_ICONINFORMATION);
}

// ケース6: MB_OK のみ
void ShowOkMessage(const char* text)
{
    AfxMessageBox(text, MB_OK);
}

// ケース7: MB_YESNO | MB_ICONQUESTION (戻り値あり)
int AskUserConfirm(const char* question)
{
    int result = AfxMessageBox(question, MB_YESNO | MB_ICONQUESTION);
    return result;
}

// ケース8: ネストした関数呼び出しを引数にした AfxMessageBox
void ShowNestedCallError(int code)
{
    AfxMessageBox(FormatErrorCode(GetErrorDescription(code)), MB_OK | MB_ICONERROR);
}
