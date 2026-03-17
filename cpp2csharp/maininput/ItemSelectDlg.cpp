// ItemSelectDlg.cpp : 実装ファイル
//
// [INCLUDE start]
#include "stdafx.h"
// [INCLUDE ene]
// [INCLUDE start]
#include "SampleApp.h"
// [INCLUDE end]
// [INCLUDE start]
#include "ItemSelectDlg.h"
// [INCLUDE end]

// リリースでプリプロセッサかけるので↓のボイラープレートは消える
// #ifdef _DEBUG
// #define new DEBUG_NEW
// #undef THIS_FILE
// static char THIS_FILE[] = __FILE__;
// #endif

/////////////////////////////////////////////////////////////////////////////
// CItemSelectDlg ダイアログ

CItemSelectDlg::CItemSelectDlg(CWnd* pParent /*=NULL*/)
	: CDialog(CItemSelectDlg::IDD, pParent)
{
	//{{AFX_DATA_INIT(CItemSelectDlg)
	m_nCategoryIndex = -1;
	m_nItemIndex = -1;
	m_strMemo = _T("");
	//}}AFX_DATA_INIT
}

void CItemSelectDlg::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	//{{AFX_DATA_MAP(CItemSelectDlg)
	DDX_CBIndex(pDX, IDC_COMBO_CATEGORY, m_nCategoryIndex);
	DDX_Control(pDX, IDC_COMBO_CATEGORY, m_comboCategory);
	DDX_LBIndex(pDX, IDC_LIST_ITEM, m_nItemIndex);
	DDX_Control(pDX, IDC_LIST_ITEM, m_listItem);
	DDX_Text(pDX, IDC_EDIT_MEMO, m_strMemo);
	DDV_MaxChars(pDX, m_strMemo, 100);
	DDX_Control(pDX, IDC_GRID_DETAIL, m_gridDetail);
	//}}AFX_DATA_MAP
}

BEGIN_MESSAGE_MAP(CItemSelectDlg, CDialog)
	//{{AFX_MSG_MAP(CItemSelectDlg)
	ON_CBN_SELCHANGE(IDC_COMBO_CATEGORY, OnSelchangeComboCategory)
	ON_LBN_SELCHANGE(IDC_LIST_ITEM, OnSelchangeListItem)
	//}}AFX_MSG_MAP
END_MESSAGE_MAP()

/////////////////////////////////////////////////////////////////////////////
// CItemSelectDlg メッセージ ハンドラ

BOOL CItemSelectDlg::OnInitDialog()
{
	CDialog::OnInitDialog();

	// カテゴリコンボボックスの初期化
	m_comboCategory.AddString(_T("食品"));
	m_comboCategory.AddString(_T("電化製品"));
	m_comboCategory.AddString(_T("衣類"));
	m_comboCategory.SetCurSel(0);

	// グリッドの初期化
	m_gridDetail.SetRows(5);
	m_gridDetail.SetCols(3);
	m_gridDetail.SetTextMatrix(0, 0, _T("品番"));
	m_gridDetail.SetTextMatrix(0, 1, _T("品名"));
	m_gridDetail.SetTextMatrix(0, 2, _T("単価"));

	// カテゴリ初期選択に合わせてリストを更新
	LoadItemList(0);

	return TRUE;
}

void CItemSelectDlg::LoadItemList(int nCategory)
{
	m_listItem.ResetContent();

	switch (nCategory)
	{
	case 0: // 食品
		m_listItem.AddString(_T("りんご"));
		m_listItem.AddString(_T("バナナ"));
		m_listItem.AddString(_T("みかん"));
		break;
	case 1: // 電化製品
		m_listItem.AddString(_T("テレビ"));
		m_listItem.AddString(_T("冷蔵庫"));
		m_listItem.AddString(_T("洗濯機"));
		break;
	case 2: // 衣類
		m_listItem.AddString(_T("Tシャツ"));
		m_listItem.AddString(_T("ジャケット"));
		m_listItem.AddString(_T("スニーカー"));
		break;
	}
}

void CItemSelectDlg::OnSelchangeComboCategory()
{
	int nSel = m_comboCategory.GetCurSel();
	if (nSel == CB_ERR)
		return;

	LoadItemList(nSel);

	// グリッドをクリア
	for (int i = 1; i < m_gridDetail.GetRows(); i++)
	{
		m_gridDetail.SetTextMatrix(i, 0, _T(""));
		m_gridDetail.SetTextMatrix(i, 1, _T(""));
		m_gridDetail.SetTextMatrix(i, 2, _T(""));
	}
}

void CItemSelectDlg::OnSelchangeListItem()
{
	int nSel = m_listItem.GetCurSel();
	if (nSel == LB_ERR)
		return;

	CString strItem;
	m_listItem.GetText(nSel, strItem);

	// 選択アイテムの詳細をグリッドに表示（ダミーデータ）
	CString strCode, strPrice;
	strCode.Format(_T("A%03d"), nSel + 1);
	strPrice.Format(_T("%d円"), (nSel + 1) * 100);

	m_gridDetail.SetTextMatrix(1, 0, strCode);
	m_gridDetail.SetTextMatrix(1, 1, strItem);
	m_gridDetail.SetTextMatrix(1, 2, strPrice);
}

void CItemSelectDlg::OnOK()
{
	if (!UpdateData(TRUE))
		return;

	// 選択チェック
	if (m_nItemIndex == LB_ERR)
	{
		AfxMessageBox(_T("アイテムを選択してください。"), MB_OK | MB_ICONWARNING);
		m_listItem.SetFocus();
		return;
	}

	CDialog::OnOK();
}
