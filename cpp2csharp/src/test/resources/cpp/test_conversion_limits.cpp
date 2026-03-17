// 変換ツールの制約パターン集
// 変換できない・意図しない結果になるパターンを示す。

#include "stdafx.h"

// ====================================================================
// 【制約1】this-> 経由のメンバアクセス後のメソッド呼び出し
//
//   実際の挙動: "->" は "." に変換されるが、"this ." は変換範囲外に残る。
//   理由: maxStart（右端優先）により "obj" のみのマッチが選ばれ、
//         前方の "this ." は変換範囲に含まれないためそのまま出力に残る。
//
//   期待される出力（抜粋）:
//     this.m_str . Substring ( 0 , 5 )          ← -> が . に変換、Left→Substring されるが this. が残る
//     this.MigrationHelper.Format(m_time, ...)  ← Format 誤変換（m_time のみキャプチャ）
//     this.MigrationHelper.Find(m_str, "/")     ← Find 誤変換（m_str のみキャプチャ）
// ====================================================================
class CMyClass
{
    CString m_str;
    CTime   m_time;

    void Constraint1()
    {
        CString s1 = this->m_str.Left(5);
        CString s2 = this->m_time.Format("%Y/%m/%d");
        CString s3 = this->m_str.Find("/");
        CString s4 = ("").Format("%d");
        CString s5 = (1 + 2).Format("%d");
        CString s6 = (this->m_str).Format("%d");
    }
};

// ====================================================================
// 【参考】RECEIVER で関数呼び出し結果をレシーバーにする場合
//
//   RECEIVER は関数呼び出し結果（末尾が ")"）もレシーバーとして許可するため、
//   ルールのない関数の戻り値に対してもメソッド変換が正しく適用される。
//
//   期待される出力:
//     string s1 = GetString ( data ).Substring(0, 5);
//     int pos1 = MigrationHelper.Find(CreateKey ( data ), "/");
//     bool b = BuildPath(data).IsEmpty();   ← IsEmpty は 0 引数フィルタにより変換なし
// ====================================================================
void Constraint2()
{
    CString data;
    CString s1 = GetString(data).Left(5);
    int pos1 = CreateKey(data).Find("/");
    BOOL b = BuildPath(data).IsEmpty();
}

// ====================================================================
// 【参考】同一メソッドの連鎖
//
//   ※ 元の C++ は確からしいコードではない。Format のチェーン挙動を
//     確認するための例であり、CString::Format に可変引数が渡されていない。
//
//   前提: time が 2025/03/08 のような日付を表す場合、
//         time.Format("%Y") は "2025" を返す。
//
//   変換前（C++）:
//     Step1: time.Format("%Y") → "2025" (CTime::Format)
//     Step2: "2025".Format("%m/%d") → CString::Format
//            ※ レシーバー "2025" は使われない。出力は書式と可変引数のみで決まる。
//
//   実際の挙動: 1回目の変換後トークンが単一化してフィルタをすり抜け、
//   2回目以降はネスト形式に変換される。
//   C++ と C# の挙動は同じ: Step1 は CTime::Format / DateTime オーバーロード、
//   Step2 は CString::Format / string オーバーロードで、いずれも 2 回目の
//   レシーバー "2025" は使われず、出力は書式と可変引数のみで決まる。変換は元の挙動を踏襲。
//
//   変換結果:
//     MigrationHelper.Format(MigrationHelper.Format(time, "%Y"), "%m/%d")
// ====================================================================
void Constraint3()
{
    CTime time;
    CString result = time.Format("%Y").Format("%m/%d");
}

// ====================================================================
// 【参考】確からしい CString::Format の使用例
//
//   CString::Format は printf 形式。書式と可変引数を渡す。
//   str の中身は "整数: 10, 小数: 3.14" となる。
// ====================================================================
void CorrectFormatExample()
{
    CString str;
    int n = 10;
    double d = 3.14159;
    str.Format("整数: %d, 小数: %.2f", n, d);
}

// ====================================================================
// 【参考】正しく変換されるパターン
// ====================================================================
void Reference()
{
    CString str;
    CTime time;
    CString arr[3];

    // (A) 引数ネスト型
    int pos1 = str.Find(time.Format("%Y/%m/%d"));

    // (B) 異なるメソッドの2段チェーン
    int pos2 = time.Format("%Y/%m/%d").Find("/");

    // (C) 配列要素のレシーバー
    int pos3 = arr[0].Find("/");

    // (D) 単純なレシーバー識別子
    CString s = str.Left(10);
    int pos4 = str.Find("key");
}
