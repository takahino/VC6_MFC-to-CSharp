// InventoryDlg.cpp : 在庫管理ダイアログの実装
// Visual C++ 6.0 MFC アプリケーション
// 製造業向け在庫管理システム - メインダイアログ

#include "stdafx.h"
#include "InventoryApp.h"
#include "InventoryDlg.h"
#include "StockItem.h"
#include "CategoryDlg.h"
#include "SearchDlg.h"
#include <math.h>

#ifdef _DEBUG
#define new DEBUG_NEW
#undef THIS_FILE
static char THIS_FILE[] = __FILE__;
#endif

// 在庫アイテム構造体
struct STOCKITEM
{
    int     nItemID;
    char    szItemCode[32];
    char    szItemName[128];
    char    szCategory[64];
    int     nQuantity;
    double  dUnitPrice;
    double  dCostPrice;
    int     nReorderPoint;
    int     nMaxStock;
    char    szLocation[32];
    char    szSupplier[128];
    CTime   tLastIn;
    CTime   tLastOut;
    int     nMonthlyIn;
    int     nMonthlyOut;
    BOOL    bActive;
};

// ソートコールバック用グローバル変数
static int g_nSortColumn = 0;
static BOOL g_bSortAscending = TRUE;

/////////////////////////////////////////////////////////////////////////////
// CInventoryDlg ダイアログ

CInventoryDlg::CInventoryDlg(CWnd* pParent /*=NULL*/)
    : CDialog(CInventoryDlg::IDD, pParent)
{
    //{{AFX_DATA_INIT(CInventoryDlg)
    m_strSearchKeyword = _T("");
    m_nFilterCategory = 0;
    m_strStatusMessage = _T("");
    m_nTotalItems = 0;
    m_dTotalValue = 0.0;
    //}}AFX_DATA_INIT

    m_hIcon = AfxGetApp()->LoadIcon(IDR_MAINFRAME);
    m_bDataModified = FALSE;
    m_nSelectedItemID = -1;
    m_strCurrentFile = _T("");
    m_bShowGraph = TRUE;
}

CInventoryDlg::~CInventoryDlg()
{
    // 在庫アイテム配列のクリア
    m_arrStockItems.RemoveAll();
}

void CInventoryDlg::DoDataExchange(CDataExchange* pDX)
{
    CDialog::DoDataExchange(pDX);
    //{{AFX_DATA_MAP(CInventoryDlg)
    DDX_Control(pDX, IDC_TREE_CATEGORY, m_treeCategory);
    DDX_Control(pDX, IDC_LIST_STOCK, m_listStock);
    DDX_Control(pDX, IDC_EDIT_SEARCH, m_editSearch);
    DDX_Control(pDX, IDC_COMBO_FILTER, m_comboFilter);
    DDX_Control(pDX, IDC_STATIC_GRAPH, m_staticGraph);
    DDX_Text(pDX, IDC_EDIT_SEARCH, m_strSearchKeyword);
    DDX_Text(pDX, IDC_STATIC_STATUS, m_strStatusMessage);
    DDX_Text(pDX, IDC_EDIT_TOTAL_ITEMS, m_nTotalItems);
    DDX_CBIndex(pDX, IDC_COMBO_FILTER, m_nFilterCategory);
    DDV_MinMaxInt(pDX, m_nFilterCategory, 0, 99);
    //}}AFX_DATA_MAP
}

BEGIN_MESSAGE_MAP(CInventoryDlg, CDialog)
    //{{AFX_MSG_MAP(CInventoryDlg)
    ON_WM_PAINT()
    ON_WM_QUERYDRAGICON()
    ON_WM_SIZE()
    ON_WM_DESTROY()
    ON_BN_CLICKED(IDC_BTN_SEARCH, OnBtnSearch)
    ON_BN_CLICKED(IDC_BTN_ADD, OnBtnAdd)
    ON_BN_CLICKED(IDC_BTN_EDIT, OnBtnEdit)
    ON_BN_CLICKED(IDC_BTN_DELETE, OnBtnDelete)
    ON_BN_CLICKED(IDC_BTN_IMPORT, OnBtnImport)
    ON_BN_CLICKED(IDC_BTN_EXPORT, OnBtnExport)
    ON_BN_CLICKED(IDC_BTN_REPORT, OnBtnReport)
    ON_BN_CLICKED(IDC_BTN_REFRESH, OnBtnRefresh)
    ON_NOTIFY(NM_CLICK, IDC_LIST_STOCK, OnClickListStock)
    ON_NOTIFY(LVN_COLUMNCLICK, IDC_LIST_STOCK, OnColumnClickListStock)
    ON_NOTIFY(TVN_SELCHANGED, IDC_TREE_CATEGORY, OnSelchangedTreeCategory)
    ON_NOTIFY(NM_DBLCLK, IDC_LIST_STOCK, OnDblclkListStock)
    ON_CBN_SELCHANGE(IDC_COMBO_FILTER, OnSelchangeComboFilter)
    ON_WM_TIMER()
    //}}AFX_MSG_MAP
END_MESSAGE_MAP()

/////////////////////////////////////////////////////////////////////////////
// CInventoryDlg メッセージ ハンドラ

BOOL CInventoryDlg::OnInitDialog()
{
    CDialog::OnInitDialog();

    // アイコン設定
    SetIcon(m_hIcon, TRUE);
    SetIcon(m_hIcon, FALSE);

    // ウィンドウタイトル設定
    CString strTitle;
    strTitle.Format(_T("在庫管理システム Ver.2.5 - %s"), AfxGetAppName());
    SetWindowText(strTitle);

    // リストコントロールの列設定
    InitListCtrl();

    // ツリーコントロールの初期化
    InitTreeCtrl();

    // コンボボックスの初期化
    InitComboFilter();

    // データ読み込み
    CString strDataFile = GetDefaultDataFilePath();
    if (!strDataFile.IsEmpty())
    {
        LoadStockData(strDataFile);
    }

    // リスト表示更新
    RefreshListCtrl();

    // 統計情報更新
    UpdateStatistics();

    // ステータスバー更新
    m_strStatusMessage = _T("システム起動完了。在庫データを読み込みました。");
    UpdateData(FALSE);

    // タイマー開始（5分ごとに自動保存）
    SetTimer(IDT_AUTOSAVE, 300000, NULL);

    TRACE(_T("OnInitDialog: 初期化完了。アイテム数=%d\n"), m_arrStockItems.GetSize());

    return TRUE;
}

// --------------------------------------------------------------------
// リストコントロール初期化
// --------------------------------------------------------------------
void CInventoryDlg::InitListCtrl()
{
    // 拡張スタイル設定
    m_listStock.SetExtendedStyle(LVS_EX_FULLROWSELECT | LVS_EX_GRIDLINES | LVS_EX_HEADERDRAGDROP);

    // 列の挿入
    m_listStock.InsertColumn(0, _T("品番"),       LVCFMT_LEFT,  80);
    m_listStock.InsertColumn(1, _T("品名"),       LVCFMT_LEFT,  200);
    m_listStock.InsertColumn(2, _T("カテゴリ"),   LVCFMT_LEFT,  100);
    m_listStock.InsertColumn(3, _T("在庫数"),     LVCFMT_RIGHT, 80);
    m_listStock.InsertColumn(4, _T("単価"),       LVCFMT_RIGHT, 100);
    m_listStock.InsertColumn(5, _T("在庫金額"),   LVCFMT_RIGHT, 120);
    m_listStock.InsertColumn(6, _T("発注点"),     LVCFMT_RIGHT, 80);
    m_listStock.InsertColumn(7, _T("保管場所"),   LVCFMT_LEFT,  100);
    m_listStock.InsertColumn(8, _T("最終入庫日"), LVCFMT_LEFT,  120);
    m_listStock.InsertColumn(9, _T("最終出庫日"), LVCFMT_LEFT,  120);
    m_listStock.InsertColumn(10, _T("月間入庫"), LVCFMT_RIGHT, 80);
    m_listStock.InsertColumn(11, _T("月間出庫"), LVCFMT_RIGHT, 80);

    // 列幅の調整
    m_listStock.SetColumnWidth(1, LVSCW_AUTOSIZE_USEHEADER);

    TRACE(_T("InitListCtrl: リストコントロール初期化完了\n"));
}

// --------------------------------------------------------------------
// ツリーコントロール初期化
// --------------------------------------------------------------------
void CInventoryDlg::InitTreeCtrl()
{
    m_treeCategory.DeleteAllItems();

    // ルートノード挿入
    HTREEITEM hRoot = m_treeCategory.InsertItem(_T("全カテゴリ"), TVI_ROOT, TVI_LAST);
    m_treeCategory.SetItemData(hRoot, 0);

    // カテゴリの挿入
    HTREEITEM hElec = m_treeCategory.InsertItem(_T("電子部品"), hRoot, TVI_LAST);
    m_treeCategory.SetItemData(hElec, 1);

    HTREEITEM hMech = m_treeCategory.InsertItem(_T("機械部品"), hRoot, TVI_LAST);
    m_treeCategory.SetItemData(hMech, 2);

    HTREEITEM hChem = m_treeCategory.InsertItem(_T("化学原材料"), hRoot, TVI_LAST);
    m_treeCategory.SetItemData(hChem, 3);

    HTREEITEM hPkg = m_treeCategory.InsertItem(_T("包装材料"), hRoot, TVI_LAST);
    m_treeCategory.SetItemData(hPkg, 4);

    HTREEITEM hTool = m_treeCategory.InsertItem(_T("工具・備品"), hRoot, TVI_LAST);
    m_treeCategory.SetItemData(hTool, 5);

    // サブカテゴリ（電子部品）
    HTREEITEM hIC = m_treeCategory.InsertItem(_T("IC・半導体"), hElec, TVI_LAST);
    m_treeCategory.SetItemData(hIC, 11);
    HTREEITEM hResistor = m_treeCategory.InsertItem(_T("抵抗・コンデンサ"), hElec, TVI_LAST);
    m_treeCategory.SetItemData(hResistor, 12);
    HTREEITEM hConnector = m_treeCategory.InsertItem(_T("コネクタ"), hElec, TVI_LAST);
    m_treeCategory.SetItemData(hConnector, 13);

    // サブカテゴリ（機械部品）
    HTREEITEM hBearing = m_treeCategory.InsertItem(_T("軸受"), hMech, TVI_LAST);
    m_treeCategory.SetItemData(hBearing, 21);
    HTREEITEM hGear = m_treeCategory.InsertItem(_T("歯車・ベルト"), hMech, TVI_LAST);
    m_treeCategory.SetItemData(hGear, 22);
    HTREEITEM hBolt = m_treeCategory.InsertItem(_T("ボルト・ナット"), hMech, TVI_LAST);
    m_treeCategory.SetItemData(hBolt, 23);

    // ルートを展開
    m_treeCategory.Expand(hRoot, TVE_EXPAND);
    m_treeCategory.Expand(hElec, TVE_EXPAND);

    TRACE(_T("InitTreeCtrl: ツリーコントロール初期化完了\n"));
}

// --------------------------------------------------------------------
// コンボボックス初期化
// --------------------------------------------------------------------
void CInventoryDlg::InitComboFilter()
{
    m_comboFilter.ResetContent();
    m_comboFilter.AddString(_T("すべて表示"));
    m_comboFilter.AddString(_T("在庫不足のみ"));
    m_comboFilter.AddString(_T("過剰在庫のみ"));
    m_comboFilter.AddString(_T("発注点以下"));
    m_comboFilter.AddString(_T("今月入庫あり"));
    m_comboFilter.AddString(_T("今月出庫あり"));
    m_comboFilter.AddString(_T("未使用アイテム"));
    m_comboFilter.SetCurSel(0);
}

// --------------------------------------------------------------------
// デフォルトデータファイルパス取得
// --------------------------------------------------------------------
CString CInventoryDlg::GetDefaultDataFilePath()
{
    CString strPath;
    char szPath[MAX_PATH];

    // レジストリから前回のファイルパスを読む
    HKEY hKey;
    if (RegOpenKeyEx(HKEY_CURRENT_USER,
        _T("Software\\InventoryApp\\Settings"),
        0, KEY_READ, &hKey) == ERROR_SUCCESS)
    {
        DWORD dwType = REG_SZ;
        DWORD dwSize = sizeof(szPath);
        if (RegQueryValueEx(hKey, _T("LastDataFile"), NULL, &dwType,
            (LPBYTE)szPath, &dwSize) == ERROR_SUCCESS)
        {
            strPath = szPath;
        }
        RegCloseKey(hKey);
    }

    // ファイルが存在しない場合はデフォルトパス
    if (strPath.IsEmpty() || !PathFileExists(strPath))
    {
        strPath = _T("C:\\InventoryData\\stock.dat");
    }

    return strPath;
}

// --------------------------------------------------------------------
// 在庫データ読み込み
// --------------------------------------------------------------------
BOOL CInventoryDlg::LoadStockData(const CString& strFilePath)
{
    m_arrStockItems.RemoveAll();

    CStdioFile file;
    CFileException fileEx;

    // ファイルオープン
    if (!file.Open(strFilePath, CFile::modeRead | CFile::typeText, &fileEx))
    {
        CString strMsg;
        strMsg.Format(_T("在庫データファイルを開けません。\nファイル: %s\nエラーコード: %d"),
            strFilePath, fileEx.m_cause);
        AfxMessageBox(strMsg, MB_OK | MB_ICONERROR);
        TRACE(_T("LoadStockData: ファイルオープン失敗 - %s\n"), strFilePath);
        return FALSE;
    }

    try
    {
        CString strLine;
        int nLineCount = 0;

        // ヘッダ行スキップ
        file.ReadString(strLine);
        nLineCount++;

        // データ行読み込み
        while (file.ReadString(strLine))
        {
            nLineCount++;
            strLine.TrimRight();
            strLine.TrimLeft();

            if (strLine.IsEmpty() || strLine.Left(1) == _T("#"))
                continue;

            // CSV解析
            STOCKITEM item;
            ZeroMemory(&item, sizeof(item));

            if (ParseCSVLine(strLine, item))
            {
                m_arrStockItems.Add(item);
            }
            else
            {
                TRACE(_T("LoadStockData: CSV解析失敗 行=%d: %s\n"), nLineCount, strLine);
            }
        }

        file.Close();
        m_strCurrentFile = strFilePath;

        // レジストリに保存
        SaveLastFilePath(strFilePath);

        CString strMsg;
        strMsg.Format(_T("%d件の在庫データを読み込みました。"), m_arrStockItems.GetSize());
        m_strStatusMessage = strMsg;
        UpdateData(FALSE);

        TRACE(_T("LoadStockData: 読み込み完了 %d件\n"), m_arrStockItems.GetSize());
        return TRUE;
    }
    catch (CFileException* pEx)
    {
        CString strMsg;
        strMsg.Format(_T("ファイル読み込み中にエラーが発生しました。\nエラーコード: %d"), pEx->m_cause);
        AfxMessageBox(strMsg, MB_OK | MB_ICONERROR);
        pEx->Delete();
        file.Close();
        return FALSE;
    }
    catch (CException* pEx)
    {
        char szError[256];
        pEx->GetErrorMessage(szError, sizeof(szError));
        CString strMsg;
        strMsg.Format(_T("予期しないエラー: %s"), szError);
        AfxMessageBox(strMsg, MB_OK | MB_ICONERROR);
        pEx->Delete();
        file.Close();
        return FALSE;
    }
}

// --------------------------------------------------------------------
// CSV行解析
// --------------------------------------------------------------------
BOOL CInventoryDlg::ParseCSVLine(const CString& strLine, STOCKITEM& item)
{
    CString strWork = strLine;
    int nField = 0;

    while (!strWork.IsEmpty() && nField < 15)
    {
        CString strField;
        int nComma = strWork.Find(_T(','));

        if (nComma == -1)
        {
            strField = strWork;
            strWork.Empty();
        }
        else
        {
            strField = strWork.Left(nComma);
            strWork = strWork.Mid(nComma + 1);
        }

        strField.TrimLeft();
        strField.TrimRight();

        switch (nField)
        {
        case 0:
            item.nItemID = atoi(strField);
            break;
        case 1:
            lstrcpyn(item.szItemCode, strField, sizeof(item.szItemCode));
            break;
        case 2:
            lstrcpyn(item.szItemName, strField, sizeof(item.szItemName));
            break;
        case 3:
            lstrcpyn(item.szCategory, strField, sizeof(item.szCategory));
            break;
        case 4:
            item.nQuantity = atoi(strField);
            break;
        case 5:
            item.dUnitPrice = atof(strField);
            break;
        case 6:
            item.dCostPrice = atof(strField);
            break;
        case 7:
            item.nReorderPoint = atoi(strField);
            break;
        case 8:
            item.nMaxStock = atoi(strField);
            break;
        case 9:
            lstrcpyn(item.szLocation, strField, sizeof(item.szLocation));
            break;
        case 10:
            lstrcpyn(item.szSupplier, strField, sizeof(item.szSupplier));
            break;
        case 11:
            item.nMonthlyIn = atoi(strField);
            break;
        case 12:
            item.nMonthlyOut = atoi(strField);
            break;
        case 13:
            item.bActive = (atoi(strField) != 0);
            break;
        }
        nField++;
    }

    return (nField >= 10);
}

// --------------------------------------------------------------------
// 在庫データ保存
// --------------------------------------------------------------------
BOOL CInventoryDlg::SaveStockData(const CString& strFilePath)
{
    CStdioFile file;
    CFileException fileEx;

    if (!file.Open(strFilePath, CFile::modeCreate | CFile::modeWrite | CFile::typeText, &fileEx))
    {
        CString strMsg;
        strMsg.Format(_T("ファイルを保存できません。\nファイル: %s"), strFilePath);
        AfxMessageBox(strMsg, MB_OK | MB_ICONERROR);
        return FALSE;
    }

    try
    {
        // ヘッダ行書き込み
        file.WriteString(_T("ID,品番,品名,カテゴリ,在庫数,単価,原価,発注点,最大在庫,保管場所,仕入先,月間入庫,月間出庫,有効\n"));

        for (int i = 0; i < m_arrStockItems.GetSize(); i++)
        {
            STOCKITEM& item = m_arrStockItems.GetAt(i);

            CString strLine;
            strLine.Format(_T("%d,%s,%s,%s,%d,%.2f,%.2f,%d,%d,%s,%s,%d,%d,%d\n"),
                item.nItemID,
                item.szItemCode,
                item.szItemName,
                item.szCategory,
                item.nQuantity,
                item.dUnitPrice,
                item.dCostPrice,
                item.nReorderPoint,
                item.nMaxStock,
                item.szLocation,
                item.szSupplier,
                item.nMonthlyIn,
                item.nMonthlyOut,
                item.bActive ? 1 : 0);

            file.WriteString(strLine);
        }

        file.Close();
        m_bDataModified = FALSE;

        CString strMsg;
        strMsg.Format(_T("%d件の在庫データを保存しました。"), m_arrStockItems.GetSize());
        m_strStatusMessage = strMsg;
        UpdateData(FALSE);

        TRACE(_T("SaveStockData: 保存完了 %d件 -> %s\n"), m_arrStockItems.GetSize(), strFilePath);
        return TRUE;
    }
    catch (CFileException* pEx)
    {
        CString strMsg;
        strMsg.Format(_T("ファイル書き込みエラー: %d"), pEx->m_cause);
        AfxMessageBox(strMsg, MB_OK | MB_ICONERROR);
        pEx->Delete();
        file.Close();
        return FALSE;
    }
}

// --------------------------------------------------------------------
// リストコントロール更新
// --------------------------------------------------------------------
void CInventoryDlg::RefreshListCtrl()
{
    m_listStock.DeleteAllItems();

    UpdateData(TRUE);
    CString strKeyword = m_strSearchKeyword;
    strKeyword.MakeLower();

    int nDisplayCount = 0;

    for (int i = 0; i < m_arrStockItems.GetSize(); i++)
    {
        STOCKITEM& item = m_arrStockItems.GetAt(i);

        if (!item.bActive)
            continue;

        // フィルタ適用
        if (!ApplyFilter(item))
            continue;

        // キーワード検索
        if (!strKeyword.IsEmpty())
        {
            CString strCode = item.szItemCode;
            strCode.MakeLower();
            CString strName = item.szItemName;
            strName.MakeLower();
            CString strCat = item.szCategory;
            strCat.MakeLower();

            if (strCode.Find(strKeyword) == -1 &&
                strName.Find(strKeyword) == -1 &&
                strCat.Find(strKeyword) == -1)
            {
                continue;
            }
        }

        // リストへの挿入
        int nRow = m_listStock.InsertItem(nDisplayCount, item.szItemCode);

        m_listStock.SetItemText(nRow, 1, item.szItemName);
        m_listStock.SetItemText(nRow, 2, item.szCategory);

        // 在庫数
        CString strQty;
        strQty.Format(_T("%d"), item.nQuantity);
        m_listStock.SetItemText(nRow, 3, strQty);

        // 単価
        CString strPrice;
        strPrice.Format(_T("¥%,.2f"), item.dUnitPrice);
        m_listStock.SetItemText(nRow, 4, strPrice);

        // 在庫金額
        double dStockValue = item.nQuantity * item.dUnitPrice;
        CString strValue;
        strValue.Format(_T("¥%,.0f"), dStockValue);
        m_listStock.SetItemText(nRow, 5, strValue);

        // 発注点
        CString strReorder;
        strReorder.Format(_T("%d"), item.nReorderPoint);
        m_listStock.SetItemText(nRow, 6, strReorder);

        // 保管場所
        m_listStock.SetItemText(nRow, 7, item.szLocation);

        // 最終入庫日
        CString strLastIn = item.tLastIn.Format(_T("%Y/%m/%d"));
        m_listStock.SetItemText(nRow, 8, strLastIn);

        // 最終出庫日
        CString strLastOut = item.tLastOut.Format(_T("%Y/%m/%d"));
        m_listStock.SetItemText(nRow, 9, strLastOut);

        // 月間入庫
        CString strMonthIn;
        strMonthIn.Format(_T("%d"), item.nMonthlyIn);
        m_listStock.SetItemText(nRow, 10, strMonthIn);

        // 月間出庫
        CString strMonthOut;
        strMonthOut.Format(_T("%d"), item.nMonthlyOut);
        m_listStock.SetItemText(nRow, 11, strMonthOut);

        // アイテムデータにインデックスを保存
        m_listStock.SetItemData(nRow, i);

        // 在庫不足の場合は背景色変更（カスタム描画）
        if (item.nQuantity <= item.nReorderPoint)
        {
            // 発注点以下は赤色で表示
            LVITEM lvItem;
            ZeroMemory(&lvItem, sizeof(lvItem));
            lvItem.mask = LVIF_STATE;
            lvItem.iItem = nRow;
        }

        nDisplayCount++;
    }

    // ステータス更新
    CString strStatus;
    strStatus.Format(_T("表示中: %d件 / 全体: %d件"), nDisplayCount, m_arrStockItems.GetSize());
    m_strStatusMessage = strStatus;
    UpdateData(FALSE);

    TRACE(_T("RefreshListCtrl: %d件表示\n"), nDisplayCount);
}

// --------------------------------------------------------------------
// フィルタ適用
// --------------------------------------------------------------------
BOOL CInventoryDlg::ApplyFilter(const STOCKITEM& item)
{
    UpdateData(TRUE);

    switch (m_nFilterCategory)
    {
    case 0: // すべて表示
        return TRUE;

    case 1: // 在庫不足のみ
        return (item.nQuantity < item.nReorderPoint);

    case 2: // 過剰在庫のみ
        return (item.nQuantity > item.nMaxStock);

    case 3: // 発注点以下
        return (item.nQuantity <= item.nReorderPoint);

    case 4: // 今月入庫あり
        return (item.nMonthlyIn > 0);

    case 5: // 今月出庫あり
        return (item.nMonthlyOut > 0);

    case 6: // 未使用アイテム
        return (item.nMonthlyIn == 0 && item.nMonthlyOut == 0);

    default:
        return TRUE;
    }
}

// --------------------------------------------------------------------
// 統計情報更新
// --------------------------------------------------------------------
void CInventoryDlg::UpdateStatistics()
{
    int nTotalItems = m_arrStockItems.GetSize();
    double dTotalValue = 0.0;
    int nLowStockCount = 0;
    int nOverStockCount = 0;

    // 在庫金額計算
    double* pdValues = new double[nTotalItems];
    int nValidCount = 0;

    for (int i = 0; i < nTotalItems; i++)
    {
        STOCKITEM& item = m_arrStockItems.GetAt(i);
        if (!item.bActive) continue;

        double dValue = item.nQuantity * item.dUnitPrice;
        dTotalValue += dValue;
        pdValues[nValidCount++] = dValue;

        if (item.nQuantity <= item.nReorderPoint)
            nLowStockCount++;
        if (item.nQuantity > item.nMaxStock)
            nOverStockCount++;
    }

    // 平均在庫金額
    double dAverage = 0.0;
    if (nValidCount > 0)
        dAverage = dTotalValue / nValidCount;

    // 標準偏差計算
    double dVariance = 0.0;
    for (int j = 0; j < nValidCount; j++)
    {
        double dDiff = pdValues[j] - dAverage;
        dVariance += dDiff * dDiff;
    }
    if (nValidCount > 1)
        dVariance /= (nValidCount - 1);
    double dStdDev = sqrt(dVariance);

    delete[] pdValues;

    // 最大・最小在庫金額
    double dMaxValue = 0.0;
    double dMinValue = DBL_MAX;
    for (int k = 0; k < nTotalItems; k++)
    {
        STOCKITEM& item = m_arrStockItems.GetAt(k);
        if (!item.bActive) continue;
        double dVal = item.nQuantity * item.dUnitPrice;
        if (dVal > dMaxValue) dMaxValue = dVal;
        if (dVal < dMinValue) dMinValue = dVal;
    }
    if (dMinValue == DBL_MAX) dMinValue = 0.0;

    // UI更新
    m_nTotalItems = nValidCount;
    m_dTotalValue = dTotalValue;
    UpdateData(FALSE);

    // 統計テキスト設定
    CString strStats;
    strStats.Format(
        _T("有効アイテム: %d件\n") +
        _T("総在庫金額: ¥%.0f\n") +
        _T("平均在庫金額: ¥%.0f\n") +
        _T("標準偏差: ¥%.0f\n") +
        _T("在庫不足: %d件\n") +
        _T("過剰在庫: %d件"),
        nValidCount, dTotalValue, dAverage, dStdDev,
        nLowStockCount, nOverStockCount);

    SetDlgItemText(IDC_STATIC_STATS, strStats);

    TRACE(_T("UpdateStatistics: 総金額=%.0f, 平均=%.0f, 標準偏差=%.0f\n"),
        dTotalValue, dAverage, dStdDev);
}

// --------------------------------------------------------------------
// 在庫グラフ描画
// --------------------------------------------------------------------
void CInventoryDlg::DrawInventoryGraph(CDC* pDC, CRect rcGraph)
{
    if (!m_bShowGraph) return;
    if (m_arrStockItems.GetSize() == 0) return;

    // 背景クリア
    CBrush brushBg(RGB(240, 240, 240));
    pDC->FillRect(rcGraph, &brushBg);
    brushBg.DeleteObject();

    // グラフタイトル
    CFont fontTitle;
    fontTitle.CreateFont(16, 0, 0, 0, FW_BOLD, FALSE, FALSE, FALSE,
        SHIFTJIS_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
        DEFAULT_QUALITY, DEFAULT_PITCH | FF_DONTCARE, _T("MS Gothic"));

    CFont* pOldFont = pDC->SelectObject(&fontTitle);
    pDC->SetTextColor(RGB(0, 0, 128));
    pDC->SetBkMode(TRANSPARENT);
    CRect rcTitle = rcGraph;
    rcTitle.bottom = rcTitle.top + 24;
    pDC->DrawText(_T("カテゴリ別在庫金額グラフ"), rcTitle, DT_CENTER | DT_VCENTER);
    pDC->SelectObject(pOldFont);
    fontTitle.DeleteObject();

    // グラフ領域
    CRect rcPlot = rcGraph;
    rcPlot.top += 30;
    rcPlot.left += 50;
    rcPlot.right -= 20;
    rcPlot.bottom -= 40;

    // 最大値取得（グラフスケール用）
    double dMaxCatValue = 0.0;
    CStringArray arrCategories;
    CArray<double, double> arrValues;

    // カテゴリ別集計
    for (int i = 0; i < m_arrStockItems.GetSize(); i++)
    {
        STOCKITEM& item = m_arrStockItems.GetAt(i);
        if (!item.bActive) continue;

        CString strCat = item.szCategory;
        BOOL bFound = FALSE;

        for (int j = 0; j < arrCategories.GetSize(); j++)
        {
            if (arrCategories.GetAt(j) == strCat)
            {
                double dVal = arrValues.GetAt(j);
                dVal += item.nQuantity * item.dUnitPrice;
                arrValues.SetAt(j, dVal);
                if (dVal > dMaxCatValue) dMaxCatValue = dVal;
                bFound = TRUE;
                break;
            }
        }

        if (!bFound)
        {
            arrCategories.Add(strCat);
            double dVal = item.nQuantity * item.dUnitPrice;
            arrValues.Add(dVal);
            if (dVal > dMaxCatValue) dMaxCatValue = dVal;
        }
    }

    if (arrCategories.GetSize() == 0 || dMaxCatValue <= 0.0)
        return;

    // Y軸スケール（切り上げ）
    double dScale = ceil(dMaxCatValue / 100000.0) * 100000.0;

    // Y軸グリッド線描画
    CPen penGrid(PS_DOT, 1, RGB(180, 180, 180));
    CPen* pOldPen = pDC->SelectObject(&penGrid);

    int nGridLines = 5;
    for (int g = 0; g <= nGridLines; g++)
    {
        int nY = rcPlot.bottom - (rcPlot.Height() * g / nGridLines);
        pDC->MoveTo(rcPlot.left, nY);
        pDC->LineTo(rcPlot.right, nY);

        // Y軸ラベル
        double dLabel = dScale * g / nGridLines;
        CString strLabel;
        if (dLabel >= 1000000.0)
            strLabel.Format(_T("%.1fM"), dLabel / 1000000.0);
        else
            strLabel.Format(_T("%.0fK"), dLabel / 1000.0);

        CRect rcLabel(rcPlot.left - 48, nY - 8, rcPlot.left - 2, nY + 8);
        pDC->SetTextColor(RGB(80, 80, 80));
        pDC->DrawText(strLabel, rcLabel, DT_RIGHT | DT_VCENTER | DT_SINGLELINE);
    }

    pDC->SelectObject(pOldPen);
    penGrid.DeleteObject();

    // バー描画
    int nCatCount = (int)arrCategories.GetSize();
    int nBarWidth = (rcPlot.Width() - 20) / nCatCount - 10;
    if (nBarWidth < 10) nBarWidth = 10;
    if (nBarWidth > 60) nBarWidth = 60;

    // バーカラー配列
    COLORREF aColors[] = {
        RGB(70, 130, 180),   // スチールブルー
        RGB(60, 179, 113),   // ミディアムシーグリーン
        RGB(255, 165, 0),    // オレンジ
        RGB(220, 20, 60),    // クリムゾン
        RGB(147, 112, 219),  // ミディアムパープル
        RGB(64, 224, 208),   // ターコイズ
        RGB(255, 215, 0),    // ゴールド
        RGB(255, 99, 71),    // トマト
    };

    for (int c = 0; c < nCatCount; c++)
    {
        double dVal = arrValues.GetAt(c);
        int nBarHeight = (int)(rcPlot.Height() * dVal / dScale);
        int nBarHeight2 = min(nBarHeight, rcPlot.Height());

        int nLeft = rcPlot.left + 10 + c * (rcPlot.Width() - 20) / nCatCount;
        int nTop  = rcPlot.bottom - nBarHeight2;
        int nRight = nLeft + nBarWidth;

        COLORREF clrBar = aColors[c % 8];
        CBrush brushBar(clrBar);
        CPen penBar(PS_SOLID, 1, RGB(0, 0, 0));

        pDC->SelectObject(&brushBar);
        pDC->SelectObject(&penBar);
        pDC->Rectangle(nLeft, nTop, nRight, rcPlot.bottom);

        brushBar.DeleteObject();
        penBar.DeleteObject();

        // バー上に数値表示
        CString strVal;
        if (dVal >= 1000000.0)
            strVal.Format(_T("%.1fM"), dVal / 1000000.0);
        else
            strVal.Format(_T("%.0fK"), dVal / 1000.0);

        pDC->SetTextColor(RGB(0, 0, 0));
        pDC->SetBkMode(TRANSPARENT);
        CRect rcBarLabel(nLeft, nTop - 16, nRight, nTop);
        pDC->DrawText(strVal, rcBarLabel, DT_CENTER | DT_SINGLELINE);

        // X軸ラベル（カテゴリ名）
        CString strCatLabel = arrCategories.GetAt(c);
        if (strCatLabel.GetLength() > 6)
            strCatLabel = strCatLabel.Left(6) + _T("..");

        CRect rcXLabel(nLeft - 5, rcPlot.bottom + 4, nRight + 5, rcPlot.bottom + 20);
        pDC->SetTextColor(RGB(40, 40, 40));
        pDC->DrawText(strCatLabel, rcXLabel, DT_CENTER | DT_SINGLELINE);
    }

    // 枠線
    CPen penBorder(PS_SOLID, 2, RGB(0, 0, 0));
    pDC->SelectObject(&penBorder);
    pDC->MoveTo(rcPlot.left, rcPlot.top);
    pDC->LineTo(rcPlot.left, rcPlot.bottom);
    pDC->LineTo(rcPlot.right, rcPlot.bottom);
    penBorder.DeleteObject();
}

// --------------------------------------------------------------------
// 検索ボタン
// --------------------------------------------------------------------
void CInventoryDlg::OnBtnSearch()
{
    UpdateData(TRUE);

    if (m_strSearchKeyword.IsEmpty())
    {
        AfxMessageBox(_T("検索キーワードを入力してください。"), MB_OK | MB_ICONINFORMATION);
        m_editSearch.SetFocus();
        return;
    }

    // 検索前後のスペースを除去
    m_strSearchKeyword.TrimLeft();
    m_strSearchKeyword.TrimRight();

    TRACE(_T("OnBtnSearch: キーワード='%s'\n"), m_strSearchKeyword);

    RefreshListCtrl();

    // 検索結果件数を取得
    int nFound = m_listStock.GetItemCount();
    CString strMsg;
    strMsg.Format(_T("'%s' の検索結果: %d件"), m_strSearchKeyword, nFound);
    m_strStatusMessage = strMsg;
    UpdateData(FALSE);

    if (nFound == 0)
    {
        AfxMessageBox(_T("検索条件に一致するアイテムが見つかりませんでした。"),
            MB_OK | MB_ICONINFORMATION);
    }
}

// --------------------------------------------------------------------
// 追加ボタン
// --------------------------------------------------------------------
void CInventoryDlg::OnBtnAdd()
{
    CStockEditDlg dlg(this);
    dlg.SetMode(CStockEditDlg::MODE_ADD);

    if (dlg.DoModal() == IDOK)
    {
        STOCKITEM newItem;
        dlg.GetStockItem(newItem);

        // 新しいIDを割り当て
        newItem.nItemID = GetNextItemID();
        newItem.bActive = TRUE;

        // 現在時刻を設定
        newItem.tLastIn = CTime::GetCurrentTime();
        newItem.tLastOut = CTime::GetCurrentTime();

        m_arrStockItems.Add(newItem);
        m_bDataModified = TRUE;

        // リスト更新
        RefreshListCtrl();
        UpdateStatistics();

        CString strMsg;
        strMsg.Format(_T("アイテム '%s' を追加しました。"), newItem.szItemName);
        m_strStatusMessage = strMsg;
        UpdateData(FALSE);

        TRACE(_T("OnBtnAdd: 追加 ID=%d Name=%s\n"), newItem.nItemID, newItem.szItemName);
    }
}

// --------------------------------------------------------------------
// 編集ボタン
// --------------------------------------------------------------------
void CInventoryDlg::OnBtnEdit()
{
    int nSel = m_listStock.GetNextItem(-1, LVNI_SELECTED);
    if (nSel == -1)
    {
        AfxMessageBox(_T("編集するアイテムを選択してください。"), MB_OK | MB_ICONINFORMATION);
        return;
    }

    int nIndex = (int)m_listStock.GetItemData(nSel);
    ASSERT(nIndex >= 0 && nIndex < m_arrStockItems.GetSize());

    STOCKITEM& item = m_arrStockItems.GetAt(nIndex);

    CStockEditDlg dlg(this);
    dlg.SetMode(CStockEditDlg::MODE_EDIT);
    dlg.SetStockItem(item);

    if (dlg.DoModal() == IDOK)
    {
        dlg.GetStockItem(item);
        m_bDataModified = TRUE;

        RefreshListCtrl();
        UpdateStatistics();

        CString strMsg;
        strMsg.Format(_T("アイテム '%s' を更新しました。"), item.szItemName);
        m_strStatusMessage = strMsg;
        UpdateData(FALSE);

        TRACE(_T("OnBtnEdit: 更新 ID=%d\n"), item.nItemID);
    }
}

// --------------------------------------------------------------------
// 削除ボタン
// --------------------------------------------------------------------
void CInventoryDlg::OnBtnDelete()
{
    int nSel = m_listStock.GetNextItem(-1, LVNI_SELECTED);
    if (nSel == -1)
    {
        AfxMessageBox(_T("削除するアイテムを選択してください。"), MB_OK | MB_ICONINFORMATION);
        return;
    }

    int nIndex = (int)m_listStock.GetItemData(nSel);
    STOCKITEM& item = m_arrStockItems.GetAt(nIndex);

    CString strMsg;
    strMsg.Format(_T("'%s' (%s) を削除しますか？\nこの操作は元に戻せません。"),
        item.szItemName, item.szItemCode);

    int nResult = AfxMessageBox(strMsg, MB_YESNO | MB_ICONWARNING);
    if (nResult != IDYES)
        return;

    // 論理削除（フラグをオフにする）
    item.bActive = FALSE;
    m_bDataModified = TRUE;

    RefreshListCtrl();
    UpdateStatistics();

    CString strStatus;
    strStatus.Format(_T("アイテム '%s' を削除しました。"), item.szItemName);
    m_strStatusMessage = strStatus;
    UpdateData(FALSE);

    TRACE(_T("OnBtnDelete: 削除 ID=%d\n"), item.nItemID);
}

// --------------------------------------------------------------------
// インポートボタン
// --------------------------------------------------------------------
void CInventoryDlg::OnBtnImport()
{
    CFileDialog dlg(TRUE, _T("dat"),
        NULL,
        OFN_FILEMUSTEXIST | OFN_HIDEREADONLY,
        _T("在庫データファイル (*.dat)|*.dat|CSVファイル (*.csv)|*.csv|全ファイル (*.*)|*.*||"),
        this);

    dlg.m_ofn.lpstrTitle = _T("在庫データファイルを開く");

    if (dlg.DoModal() != IDOK)
        return;

    CString strFile = dlg.GetPathName();

    if (m_bDataModified)
    {
        int nResult = AfxMessageBox(
            _T("現在のデータは変更されています。\n保存せずにインポートしますか？"),
            MB_YESNOCANCEL | MB_ICONQUESTION);

        if (nResult == IDCANCEL)
            return;

        if (nResult == IDYES)
        {
            if (!m_strCurrentFile.IsEmpty())
                SaveStockData(m_strCurrentFile);
            else
                OnMenuFileSaveAs();
        }
    }

    if (LoadStockData(strFile))
    {
        RefreshListCtrl();
        UpdateStatistics();
        InitTreeCtrl();

        CString strMsg;
        strMsg.Format(_T("ファイル '%s' を読み込みました。"),
            dlg.GetFileName());
        AfxMessageBox(strMsg, MB_OK | MB_ICONINFORMATION);
    }
}

// --------------------------------------------------------------------
// エクスポートボタン
// --------------------------------------------------------------------
void CInventoryDlg::OnBtnExport()
{
    CFileDialog dlg(FALSE, _T("csv"),
        _T("inventory_export"),
        OFN_OVERWRITEPROMPT | OFN_HIDEREADONLY,
        _T("CSVファイル (*.csv)|*.csv|テキストファイル (*.txt)|*.txt||"),
        this);

    dlg.m_ofn.lpstrTitle = _T("在庫データをエクスポート");

    if (dlg.DoModal() != IDOK)
        return;

    CString strFile = dlg.GetPathName();
    CString strExt = dlg.GetFileExt();
    strExt.MakeLower();

    BOOL bResult = FALSE;
    if (strExt == _T("csv"))
        bResult = ExportToCSV(strFile);
    else
        bResult = SaveStockData(strFile);

    if (bResult)
    {
        CString strMsg;
        strMsg.Format(_T("エクスポート完了: %s"), strFile);
        AfxMessageBox(strMsg, MB_OK | MB_ICONINFORMATION);
    }
}

// --------------------------------------------------------------------
// CSVエクスポート
// --------------------------------------------------------------------
BOOL CInventoryDlg::ExportToCSV(const CString& strFilePath)
{
    CStdioFile file;
    if (!file.Open(strFilePath, CFile::modeCreate | CFile::modeWrite | CFile::typeText))
    {
        AfxMessageBox(_T("エクスポートファイルを作成できません。"), MB_OK | MB_ICONERROR);
        return FALSE;
    }

    try
    {
        // BOM出力（Excel対応）
        file.Write("\xEF\xBB\xBF", 3);

        // ヘッダ
        file.WriteString(_T("品番,品名,カテゴリ,在庫数,単価,原価,発注点,最大在庫,") +
                         _T("保管場所,仕入先,月間入庫,月間出庫,状態\n"));

        for (int i = 0; i < m_arrStockItems.GetSize(); i++)
        {
            STOCKITEM& item = m_arrStockItems.GetAt(i);

            CString strStatus = item.bActive ? _T("有効") : _T("無効");

            CString strLine;
            strLine.Format(_T("%s,\"%s\",%s,%d,%.2f,%.2f,%d,%d,%s,\"%s\",%d,%d,%s\n"),
                item.szItemCode,
                item.szItemName,
                item.szCategory,
                item.nQuantity,
                item.dUnitPrice,
                item.dCostPrice,
                item.nReorderPoint,
                item.nMaxStock,
                item.szLocation,
                item.szSupplier,
                item.nMonthlyIn,
                item.nMonthlyOut,
                strStatus);

            file.WriteString(strLine);
        }

        file.Close();
        return TRUE;
    }
    catch (CFileException* pEx)
    {
        pEx->Delete();
        file.Close();
        return FALSE;
    }
}

// --------------------------------------------------------------------
// リストクリックイベント
// --------------------------------------------------------------------
void CInventoryDlg::OnClickListStock(NMHDR* pNMHDR, LRESULT* pResult)
{
    int nSel = m_listStock.GetNextItem(-1, LVNI_SELECTED);
    if (nSel == -1)
    {
        *pResult = 0;
        return;
    }

    int nIndex = (int)m_listStock.GetItemData(nSel);
    if (nIndex < 0 || nIndex >= m_arrStockItems.GetSize())
    {
        *pResult = 0;
        return;
    }

    m_nSelectedItemID = nIndex;
    STOCKITEM& item = m_arrStockItems.GetAt(nIndex);

    // 詳細表示
    CString strDetail;
    strDetail.Format(
        _T("品番: %s\n") +
        _T("品名: %s\n") +
        _T("カテゴリ: %s\n") +
        _T("在庫数: %d\n") +
        _T("単価: ¥%.2f\n") +
        _T("在庫金額: ¥%.0f\n") +
        _T("仕入先: %s\n") +
        _T("保管場所: %s"),
        item.szItemCode,
        item.szItemName,
        item.szCategory,
        item.nQuantity,
        item.dUnitPrice,
        item.nQuantity * item.dUnitPrice,
        item.szSupplier,
        item.szLocation);

    SetDlgItemText(IDC_STATIC_DETAIL, strDetail);

    *pResult = 0;
}

// --------------------------------------------------------------------
// 列クリック（ソート）
// --------------------------------------------------------------------
void CInventoryDlg::OnColumnClickListStock(NMHDR* pNMHDR, LRESULT* pResult)
{
    NM_LISTVIEW* pNMListView = (NM_LISTVIEW*)pNMHDR;
    int nCol = pNMListView->iSubItem;

    if (g_nSortColumn == nCol)
        g_bSortAscending = !g_bSortAscending;
    else
    {
        g_nSortColumn = nCol;
        g_bSortAscending = TRUE;
    }

    m_listStock.SortItems(CompareItemsCallback, (LPARAM)this);

    *pResult = 0;
}

// --------------------------------------------------------------------
// ソートコールバック
// --------------------------------------------------------------------
int CALLBACK CInventoryDlg::CompareItemsCallback(LPARAM lParam1, LPARAM lParam2, LPARAM lParamSort)
{
    CInventoryDlg* pDlg = (CInventoryDlg*)lParamSort;
    ASSERT(pDlg != NULL);

    if (lParam1 < 0 || lParam1 >= pDlg->m_arrStockItems.GetSize()) return 0;
    if (lParam2 < 0 || lParam2 >= pDlg->m_arrStockItems.GetSize()) return 0;

    STOCKITEM& item1 = pDlg->m_arrStockItems.GetAt((int)lParam1);
    STOCKITEM& item2 = pDlg->m_arrStockItems.GetAt((int)lParam2);

    int nResult = 0;

    switch (g_nSortColumn)
    {
    case 0: // 品番
        nResult = lstrcmp(item1.szItemCode, item2.szItemCode);
        break;
    case 1: // 品名
        nResult = lstrcmp(item1.szItemName, item2.szItemName);
        break;
    case 2: // カテゴリ
        nResult = lstrcmp(item1.szCategory, item2.szCategory);
        break;
    case 3: // 在庫数
        nResult = item1.nQuantity - item2.nQuantity;
        break;
    case 4: // 単価
        nResult = (int)(item1.dUnitPrice - item2.dUnitPrice);
        break;
    case 5: // 在庫金額
        {
            double d1 = item1.nQuantity * item1.dUnitPrice;
            double d2 = item2.nQuantity * item2.dUnitPrice;
            nResult = (d1 > d2) ? 1 : (d1 < d2) ? -1 : 0;
        }
        break;
    case 6: // 発注点
        nResult = item1.nReorderPoint - item2.nReorderPoint;
        break;
    case 7: // 保管場所
        nResult = lstrcmp(item1.szLocation, item2.szLocation);
        break;
    default:
        nResult = 0;
        break;
    }

    return g_bSortAscending ? nResult : -nResult;
}

// --------------------------------------------------------------------
// ツリー選択変更
// --------------------------------------------------------------------
void CInventoryDlg::OnSelchangedTreeCategory(NMHDR* pNMHDR, LRESULT* pResult)
{
    NM_TREEVIEW* pNMTreeView = (NM_TREEVIEW*)pNMHDR;
    HTREEITEM hSel = m_treeCategory.GetSelectedItem();

    if (hSel == NULL)
    {
        *pResult = 0;
        return;
    }

    CString strCatName = m_treeCategory.GetItemText(hSel);
    DWORD_PTR dwCatID = m_treeCategory.GetItemData(hSel);

    TRACE(_T("OnSelchangedTreeCategory: カテゴリ='%s' ID=%d\n"), strCatName, dwCatID);

    // カテゴリでフィルタリング
    if (strCatName == _T("全カテゴリ"))
    {
        m_strSearchKeyword = _T("");
    }
    else
    {
        // カテゴリ名で絞り込み
        m_strSearchKeyword = strCatName;
    }

    UpdateData(FALSE);
    RefreshListCtrl();

    *pResult = 0;
}

// --------------------------------------------------------------------
// リストダブルクリック
// --------------------------------------------------------------------
void CInventoryDlg::OnDblclkListStock(NMHDR* pNMHDR, LRESULT* pResult)
{
    OnBtnEdit();
    *pResult = 0;
}

// --------------------------------------------------------------------
// フィルタ変更
// --------------------------------------------------------------------
void CInventoryDlg::OnSelchangeComboFilter()
{
    UpdateData(TRUE);
    RefreshListCtrl();
}

// --------------------------------------------------------------------
// 更新ボタン
// --------------------------------------------------------------------
void CInventoryDlg::OnBtnRefresh()
{
    if (!m_strCurrentFile.IsEmpty())
    {
        int nResult = AfxMessageBox(
            _T("現在のデータを破棄してファイルから再読み込みしますか？"),
            MB_YESNO | MB_ICONQUESTION);

        if (nResult != IDYES)
            return;

        LoadStockData(m_strCurrentFile);
    }

    RefreshListCtrl();
    UpdateStatistics();
}

// --------------------------------------------------------------------
// レポートボタン
// --------------------------------------------------------------------
void CInventoryDlg::OnBtnReport()
{
    // レポートダイアログ表示
    CReportDlg dlg(this);
    dlg.SetStockData(&m_arrStockItems);
    dlg.DoModal();
}

// --------------------------------------------------------------------
// タイマーイベント（自動保存）
// --------------------------------------------------------------------
void CInventoryDlg::OnTimer(UINT nIDEvent)
{
    if (nIDEvent == IDT_AUTOSAVE)
    {
        if (m_bDataModified && !m_strCurrentFile.IsEmpty())
        {
            TRACE(_T("OnTimer: 自動保存実行\n"));
            SaveStockData(m_strCurrentFile);

            m_strStatusMessage = _T("自動保存しました。");
            UpdateData(FALSE);
        }
    }

    CDialog::OnTimer(nIDEvent);
}

// --------------------------------------------------------------------
// 次のアイテムID取得
// --------------------------------------------------------------------
int CInventoryDlg::GetNextItemID()
{
    int nMaxID = 0;
    for (int i = 0; i < m_arrStockItems.GetSize(); i++)
    {
        if (m_arrStockItems.GetAt(i).nItemID > nMaxID)
            nMaxID = m_arrStockItems.GetAt(i).nItemID;
    }
    return nMaxID + 1;
}

// --------------------------------------------------------------------
// 最終ファイルパスをレジストリに保存
// --------------------------------------------------------------------
void CInventoryDlg::SaveLastFilePath(const CString& strPath)
{
    HKEY hKey;
    DWORD dwDisp;

    if (RegCreateKeyEx(HKEY_CURRENT_USER,
        _T("Software\\InventoryApp\\Settings"),
        0, NULL, REG_OPTION_NON_VOLATILE,
        KEY_WRITE, NULL, &hKey, &dwDisp) == ERROR_SUCCESS)
    {
        RegSetValueEx(hKey, _T("LastDataFile"), 0, REG_SZ,
            (LPBYTE)(LPCTSTR)strPath,
            (strPath.GetLength() + 1) * sizeof(TCHAR));
        RegCloseKey(hKey);
    }
}

// --------------------------------------------------------------------
// ウィンドウペイント
// --------------------------------------------------------------------
void CInventoryDlg::OnPaint()
{
    if (IsIconic())
    {
        CPaintDC dc(this);
        SendMessage(WM_ICONERASEBKGND, (WPARAM)dc.GetSafeHdc(), 0);
        int cxIcon = GetSystemMetrics(SM_CXICON);
        int cyIcon = GetSystemMetrics(SM_CYICON);
        CRect rect;
        GetClientRect(&rect);
        int x = (rect.Width() - cxIcon + 1) / 2;
        int y = (rect.Height() - cyIcon + 1) / 2;
        dc.DrawIcon(x, y, m_hIcon);
    }
    else
    {
        CPaintDC dc(this);

        // グラフ描画エリア取得
        CRect rcGraph;
        m_staticGraph.GetWindowRect(&rcGraph);
        ScreenToClient(&rcGraph);
        DrawInventoryGraph(&dc, rcGraph);

        CDialog::OnPaint();
    }
}

HCURSOR CInventoryDlg::OnQueryDragIcon()
{
    return (HCURSOR)m_hIcon;
}

// --------------------------------------------------------------------
// リサイズ処理
// --------------------------------------------------------------------
void CInventoryDlg::OnSize(UINT nType, int cx, int cy)
{
    CDialog::OnSize(nType, cx, cy);

    if (!IsWindow(m_listStock.m_hWnd))
        return;

    // リストを右側2/3に配置
    CRect rcClient;
    GetClientRect(&rcClient);

    int nTreeWidth = rcClient.Width() / 4;
    int nListLeft  = nTreeWidth + 8;
    int nListRight = rcClient.Width() - 8;

    // ツリー位置調整
    m_treeCategory.MoveWindow(4, 60, nTreeWidth, rcClient.Height() - 100);

    // リスト位置調整
    m_listStock.MoveWindow(nListLeft, 60, nListRight - nListLeft, rcClient.Height() - 200);

    // グラフエリア位置調整
    if (IsWindow(m_staticGraph.m_hWnd))
    {
        m_staticGraph.MoveWindow(nListLeft, rcClient.Height() - 190,
            nListRight - nListLeft, 180);
    }

    Invalidate();
}

// --------------------------------------------------------------------
// 終了処理
// --------------------------------------------------------------------
void CInventoryDlg::OnDestroy()
{
    KillTimer(IDT_AUTOSAVE);

    if (m_bDataModified)
    {
        int nResult = AfxMessageBox(
            _T("データが変更されています。保存しますか？"),
            MB_YESNO | MB_ICONQUESTION);

        if (nResult == IDYES && !m_strCurrentFile.IsEmpty())
        {
            SaveStockData(m_strCurrentFile);
        }
    }

    CDialog::OnDestroy();
}

// --------------------------------------------------------------------
// 在庫回転率計算
// --------------------------------------------------------------------
double CInventoryDlg::CalcTurnoverRate(const STOCKITEM& item)
{
    // 在庫回転率 = 月間出庫数 / 平均在庫数
    double dAvgStock = (item.nQuantity + item.nMaxStock) / 2.0;
    if (dAvgStock <= 0.0)
        return 0.0;

    double dTurnover = (double)item.nMonthlyOut / dAvgStock;
    return dTurnover;
}

// --------------------------------------------------------------------
// 在庫健全性スコア計算
// --------------------------------------------------------------------
double CInventoryDlg::CalcHealthScore(const STOCKITEM& item)
{
    double dScore = 100.0;

    // 在庫不足ペナルティ
    if (item.nQuantity <= 0)
        dScore -= 50.0;
    else if (item.nQuantity < item.nReorderPoint)
    {
        double dRatio = (double)item.nQuantity / item.nReorderPoint;
        dScore -= (1.0 - dRatio) * 30.0;
    }

    // 過剰在庫ペナルティ
    if (item.nMaxStock > 0 && item.nQuantity > item.nMaxStock)
    {
        double dOverRatio = (double)(item.nQuantity - item.nMaxStock) / item.nMaxStock;
        dScore -= dOverRatio * 20.0;
    }

    // 回転率ボーナス/ペナルティ
    double dTurnover = CalcTurnoverRate(item);
    if (dTurnover >= 2.0)       // 月2回転以上は優良
        dScore += 10.0;
    else if (dTurnover < 0.5)   // 月0.5回転未満は低迷
        dScore -= 10.0;

    // スコアを0〜100にクリップ
    dScore = max(0.0, min(100.0, dScore));
    return dScore;
}

// --------------------------------------------------------------------
// カテゴリ統計取得
// --------------------------------------------------------------------
void CInventoryDlg::GetCategoryStats(const CString& strCategory,
    int& nCount, double& dTotalValue, double& dAvgTurnover)
{
    nCount = 0;
    dTotalValue = 0.0;
    dAvgTurnover = 0.0;
    double dTurnoverSum = 0.0;

    for (int i = 0; i < m_arrStockItems.GetSize(); i++)
    {
        STOCKITEM& item = m_arrStockItems.GetAt(i);
        if (!item.bActive) continue;

        CString strCat = item.szCategory;
        if (strCategory != _T("") && strCat.CompareNoCase(strCategory) != 0)
            continue;

        nCount++;
        dTotalValue += item.nQuantity * item.dUnitPrice;
        dTurnoverSum += CalcTurnoverRate(item);
    }

    if (nCount > 0)
        dAvgTurnover = dTurnoverSum / nCount;
}

// --------------------------------------------------------------------
// 発注推奨リスト生成
// --------------------------------------------------------------------
void CInventoryDlg::GenerateOrderRecommendation(CStringArray& arrLines)
{
    arrLines.RemoveAll();

    CTime tNow = CTime::GetCurrentTime();

    for (int i = 0; i < m_arrStockItems.GetSize(); i++)
    {
        STOCKITEM& item = m_arrStockItems.GetAt(i);
        if (!item.bActive) continue;

        if (item.nQuantity > item.nReorderPoint)
            continue;

        // 推奨発注数 = 最大在庫 - 現在在庫
        int nOrderQty = item.nMaxStock - item.nQuantity;
        if (nOrderQty <= 0) nOrderQty = item.nReorderPoint * 2;

        // 緊急度評価
        CString strUrgency;
        if (item.nQuantity <= 0)
            strUrgency = _T("【緊急】");
        else if (item.nQuantity < item.nReorderPoint / 2)
            strUrgency = _T("【至急】");
        else
            strUrgency = _T("【要発注】");

        CString strLine;
        strLine.Format(_T("%s %s (%s) 現在庫:%d 発注点:%d 推奨発注:%d 仕入先:%s"),
            strUrgency,
            item.szItemName,
            item.szItemCode,
            item.nQuantity,
            item.nReorderPoint,
            nOrderQty,
            item.szSupplier);

        arrLines.Add(strLine);
    }

    TRACE(_T("GenerateOrderRecommendation: %d件の発注推奨\n"), arrLines.GetSize());
}

// --------------------------------------------------------------------
// ツリーの全カテゴリ取得（ノード走査）
// --------------------------------------------------------------------
void CInventoryDlg::CollectAllTreeItems(HTREEITEM hParent, CStringArray& arrItems)
{
    if (hParent == NULL)
        hParent = m_treeCategory.GetRootItem();

    if (hParent == NULL)
        return;

    arrItems.Add(m_treeCategory.GetItemText(hParent));

    HTREEITEM hChild = m_treeCategory.GetChildItem(hParent);
    while (hChild != NULL)
    {
        CollectAllTreeItems(hChild, arrItems);
        hChild = m_treeCategory.GetNextSiblingItem(hChild);
    }
}

// --------------------------------------------------------------------
// 月次推移データ取得
// --------------------------------------------------------------------
void CInventoryDlg::GetMonthlyTrend(int nItemIndex,
    CArray<int, int>& arrMonthlyIn,
    CArray<int, int>& arrMonthlyOut)
{
    arrMonthlyIn.RemoveAll();
    arrMonthlyOut.RemoveAll();

    if (nItemIndex < 0 || nItemIndex >= m_arrStockItems.GetSize())
        return;

    STOCKITEM& item = m_arrStockItems.GetAt(nItemIndex);

    // 仮データ生成（実際はデータベースから取得）
    CTime tNow = CTime::GetCurrentTime();
    for (int m = 5; m >= 0; m--)
    {
        // 正規分布的なランダム値（デモ用）
        double dBase = item.nMonthlyIn;
        double dRand = ((rand() % 200) - 100) / 100.0;
        int nIn  = (int)max(0.0, dBase * (1.0 + dRand * 0.3));

        dBase = item.nMonthlyOut;
        dRand = ((rand() % 200) - 100) / 100.0;
        int nOut = (int)max(0.0, dBase * (1.0 + dRand * 0.3));

        arrMonthlyIn.Add(nIn);
        arrMonthlyOut.Add(nOut);
    }
}

// --------------------------------------------------------------------
// CString 操作デモ関数
// --------------------------------------------------------------------
CString CInventoryDlg::FormatItemSummary(const STOCKITEM& item)
{
    CString strCode   = item.szItemCode;
    CString strName   = item.szItemName;
    CString strCat    = item.szCategory;
    CString strLoc    = item.szLocation;
    CString strSupplier = item.szSupplier;

    // 前後スペース除去
    strCode.TrimLeft();
    strCode.TrimRight();
    strName.TrimLeft();
    strName.TrimRight();

    // 大文字化（コード）
    strCode.MakeUpper();

    // カテゴリ先頭文字
    CString strCatShort;
    if (!strCat.IsEmpty())
        strCatShort = strCat.Left(3);

    // 品名の文字数チェック
    int nNameLen = strName.GetLength();
    CString strNameDisp;
    if (nNameLen > 20)
        strNameDisp = strName.Left(20) + _T("...");
    else
        strNameDisp = strName;

    // 品名の特殊文字除去
    strNameDisp.Replace(_T(","), _T("_"));
    strNameDisp.Replace(_T("\""), _T("'"));

    // 在庫状態文字列
    CString strStockStatus;
    if (item.nQuantity <= 0)
        strStockStatus = _T("在庫なし");
    else if (item.nQuantity < item.nReorderPoint)
        strStockStatus = _T("要発注");
    else if (item.nQuantity > item.nMaxStock)
        strStockStatus = _T("過剰");
    else
        strStockStatus = _T("正常");

    // サプライヤー名の短縮
    int nDotPos = strSupplier.ReverseFind(_T('.'));
    CString strSupShort;
    if (nDotPos > 0)
        strSupShort = strSupplier.Left(nDotPos);
    else
        strSupShort = strSupplier;

    // 最終入庫日
    CString strLastIn = item.tLastIn.Format(_T("%m/%d"));
    CTimeSpan tSpan = CTime::GetCurrentTime() - item.tLastIn;
    long lDays = (long)tSpan.GetDays();

    // 保管場所の棟-棚番解析
    CString strBuilding;
    CString strShelf;
    int nHyphen = strLoc.Find(_T('-'));
    if (nHyphen != -1)
    {
        strBuilding = strLoc.Left(nHyphen);
        strShelf    = strLoc.Mid(nHyphen + 1);
    }
    else
    {
        strBuilding = strLoc;
        strShelf    = _T("-");
    }

    // 在庫金額計算
    double dValue = item.nQuantity * item.dUnitPrice;

    // サマリー文字列組み立て
    CString strSummary;
    strSummary.Format(
        _T("[%s] %s (%s) 在庫:%d(%s) 金額:¥%.0f 棟:%s 棚:%s 最終入庫:%s(%ld日前) 仕入先:%s"),
        strCode,
        strNameDisp,
        strCatShort,
        item.nQuantity,
        strStockStatus,
        dValue,
        strBuilding,
        strShelf,
        strLastIn,
        lDays,
        strSupShort);

    // Compare / CompareNoCase のデモ
    CString strCheck = strCode;
    strCheck.MakeLower();
    if (strCheck.Compare(strCode) != 0)
    {
        // 大文字小文字が違う場合の処理（通常ここは通らない）
        TRACE(_T("FormatItemSummary: 大文字小文字不一致 '%s' vs '%s'\n"),
            strCheck, strCode);
    }

    // GetBuffer / ReleaseBuffer のデモ
    char szBuf[64];
    sprintf(szBuf, "%s-%d", item.szItemCode, item.nItemID);
    CString strID;
    LPTSTR pBuf = strID.GetBuffer(64);
    _itoa(item.nItemID, szBuf, 10);
    lstrcpy(pBuf, _T("ID:"));
    lstrcat(pBuf, strID);
    strID.ReleaseBuffer();

    return strSummary;
}

// --------------------------------------------------------------------
// 在庫アラートチェック
// --------------------------------------------------------------------
void CInventoryDlg::CheckInventoryAlerts()
{
    CStringArray arrAlerts;
    int nCritical = 0;
    int nWarning  = 0;

    for (int i = 0; i < m_arrStockItems.GetSize(); i++)
    {
        STOCKITEM& item = m_arrStockItems.GetAt(i);
        if (!item.bActive) continue;

        CString strAlert;

        if (item.nQuantity <= 0)
        {
            strAlert.Format(_T("【欠品】%s (%s) 在庫ゼロ"),
                item.szItemName, item.szItemCode);
            arrAlerts.Add(strAlert);
            nCritical++;
        }
        else if (item.nQuantity < item.nReorderPoint)
        {
            strAlert.Format(_T("【要発注】%s (%s) 在庫:%d / 発注点:%d"),
                item.szItemName, item.szItemCode,
                item.nQuantity, item.nReorderPoint);
            arrAlerts.Add(strAlert);
            nWarning++;
        }

        // 長期不動品チェック
        CTimeSpan tSpan = CTime::GetCurrentTime() - item.tLastOut;
        if (tSpan.GetDays() > 180)
        {
            strAlert.Format(_T("【長期不動】%s (%s) 最終出庫から%ld日経過"),
                item.szItemName, item.szItemCode, (long)tSpan.GetDays());
            arrAlerts.Add(strAlert);
        }
    }

    if (arrAlerts.GetSize() > 0)
    {
        CString strMsg;
        strMsg.Format(_T("在庫アラート: 緊急 %d件, 警告 %d件\n\n"), nCritical, nWarning);

        // 最初の10件を表示
        int nShow = min((int)arrAlerts.GetSize(), 10);
        for (int j = 0; j < nShow; j++)
        {
            strMsg += arrAlerts.GetAt(j);
            strMsg += _T("\n");
        }

        if (arrAlerts.GetSize() > 10)
        {
            CString strMore;
            strMore.Format(_T("\n...他 %d件"), arrAlerts.GetSize() - 10);
            strMsg += strMore;
        }

        AfxMessageBox(strMsg, MB_OK | MB_ICONWARNING);
    }

    TRACE(_T("CheckInventoryAlerts: 緊急=%d, 警告=%d\n"), nCritical, nWarning);
}

// --------------------------------------------------------------------
// ABC分析実行
// --------------------------------------------------------------------
void CInventoryDlg::RunABCAnalysis()
{
    if (m_arrStockItems.GetSize() == 0) return;

    // 在庫金額順にソート
    struct SortItem {
        int    nIndex;
        double dValue;
    };

    CArray<SortItem, SortItem> arrSorted;

    double dGrandTotal = 0.0;
    for (int i = 0; i < m_arrStockItems.GetSize(); i++)
    {
        STOCKITEM& item = m_arrStockItems.GetAt(i);
        if (!item.bActive) continue;

        SortItem si;
        si.nIndex = i;
        si.dValue = item.nQuantity * item.dUnitPrice;
        arrSorted.Add(si);
        dGrandTotal += si.dValue;
    }

    // バブルソート（降順）
    for (int a = 0; a < arrSorted.GetSize() - 1; a++)
    {
        for (int b = 0; b < arrSorted.GetSize() - 1 - a; b++)
        {
            if (arrSorted.GetAt(b).dValue < arrSorted.GetAt(b+1).dValue)
            {
                SortItem tmp = arrSorted.GetAt(b);
                arrSorted.SetAt(b, arrSorted.GetAt(b+1));
                arrSorted.SetAt(b+1, tmp);
            }
        }
    }

    // ABC分類
    double dCumulative = 0.0;
    int nCountA = 0, nCountB = 0, nCountC = 0;
    double dValueA = 0.0, dValueB = 0.0, dValueC = 0.0;

    for (int s = 0; s < arrSorted.GetSize(); s++)
    {
        dCumulative += arrSorted.GetAt(s).dValue;
        double dRatio = (dGrandTotal > 0) ? dCumulative / dGrandTotal : 0.0;

        int nIdx = arrSorted.GetAt(s).nIndex;
        STOCKITEM& item = m_arrStockItems.GetAt(nIdx);

        if (dRatio <= 0.70)
        {
            // Aランク: 累積70%まで
            lstrcpy(item.szCategory,
                (CString(item.szCategory) + _T("[A]")).Left(63));
            nCountA++;
            dValueA += arrSorted.GetAt(s).dValue;
        }
        else if (dRatio <= 0.90)
        {
            // Bランク: 累積70〜90%
            nCountB++;
            dValueB += arrSorted.GetAt(s).dValue;
        }
        else
        {
            // Cランク: 累積90%以上
            nCountC++;
            dValueC += arrSorted.GetAt(s).dValue;
        }
    }

    // 結果表示
    CString strResult;
    strResult.Format(
        _T("ABC分析結果\n\n") +
        _T("Aランク: %d品目 (¥%.0f / %.1f%%)\n") +
        _T("Bランク: %d品目 (¥%.0f / %.1f%%)\n") +
        _T("Cランク: %d品目 (¥%.0f / %.1f%%)\n") +
        _T("\n合計: ¥%.0f"),
        nCountA, dValueA, (dGrandTotal > 0 ? dValueA/dGrandTotal*100 : 0),
        nCountB, dValueB, (dGrandTotal > 0 ? dValueB/dGrandTotal*100 : 0),
        nCountC, dValueC, (dGrandTotal > 0 ? dValueC/dGrandTotal*100 : 0),
        dGrandTotal);

    AfxMessageBox(strResult, MB_OK | MB_ICONINFORMATION);

    m_bDataModified = TRUE;
    RefreshListCtrl();
}

// --------------------------------------------------------------------
// 入出荷記録
// --------------------------------------------------------------------
void CInventoryDlg::RecordStockIn(int nItemIndex, int nQty, const CString& strNote)
{
    ASSERT(nItemIndex >= 0 && nItemIndex < m_arrStockItems.GetSize());

    STOCKITEM& item = m_arrStockItems.GetAt(nItemIndex);

    int nOldQty = item.nQuantity;
    item.nQuantity += nQty;
    item.nMonthlyIn += nQty;
    item.tLastIn = CTime::GetCurrentTime();

    m_bDataModified = TRUE;

    // ログファイルへ記録
    CString strLogPath = m_strCurrentFile;
    int nLastDot = strLogPath.ReverseFind(_T('.'));
    if (nLastDot != -1)
        strLogPath = strLogPath.Left(nLastDot) + _T(".log");
    else
        strLogPath += _T(".log");

    CStdioFile logFile;
    if (logFile.Open(strLogPath, CFile::modeCreate | CFile::modeNoTruncate | CFile::modeWrite | CFile::typeText))
    {
        logFile.SeekToEnd();

        CString strLogEntry;
        strLogEntry.Format(_T("[%s] 入庫 品番:%s 品名:%s 数量:%d (前:%d→後:%d) 備考:%s\n"),
            CTime::GetCurrentTime().Format(_T("%Y/%m/%d %H:%M:%S")),
            item.szItemCode,
            item.szItemName,
            nQty,
            nOldQty,
            item.nQuantity,
            strNote);

        logFile.WriteString(strLogEntry);
        logFile.Close();
    }

    CString strMsg;
    strMsg.Format(_T("入庫記録完了: %s +%d個 (合計:%d)"),
        item.szItemName, nQty, item.nQuantity);
    m_strStatusMessage = strMsg;
    UpdateData(FALSE);

    RefreshListCtrl();
    UpdateStatistics();
}

// --------------------------------------------------------------------
// 出庫記録
// --------------------------------------------------------------------
void CInventoryDlg::RecordStockOut(int nItemIndex, int nQty, const CString& strNote)
{
    ASSERT(nItemIndex >= 0 && nItemIndex < m_arrStockItems.GetSize());

    STOCKITEM& item = m_arrStockItems.GetAt(nItemIndex);

    if (item.nQuantity < nQty)
    {
        CString strMsg;
        strMsg.Format(_T("在庫数(%d)が出庫数(%d)を下回ります。\n出庫処理を続行しますか？"),
            item.nQuantity, nQty);
        if (AfxMessageBox(strMsg, MB_YESNO | MB_ICONWARNING) != IDYES)
            return;
    }

    int nOldQty = item.nQuantity;
    item.nQuantity -= nQty;
    if (item.nQuantity < 0) item.nQuantity = 0;
    item.nMonthlyOut += nQty;
    item.tLastOut = CTime::GetCurrentTime();

    m_bDataModified = TRUE;

    // 発注点以下になった場合のアラート
    if (item.nQuantity <= item.nReorderPoint)
    {
        CString strAlert;
        strAlert.Format(_T("【発注アラート】\n%s が発注点(%d)以下になりました。\n現在庫: %d\n\n今すぐ発注しますか？"),
            item.szItemName,
            item.nReorderPoint,
            item.nQuantity);

        if (AfxMessageBox(strAlert, MB_YESNO | MB_ICONEXCLAMATION) == IDYES)
        {
            // 発注処理呼び出し
            CreatePurchaseOrder(nItemIndex);
        }
    }

    RefreshListCtrl();
    UpdateStatistics();
}

// --------------------------------------------------------------------
// 発注書作成
// --------------------------------------------------------------------
void CInventoryDlg::CreatePurchaseOrder(int nItemIndex)
{
    STOCKITEM& item = m_arrStockItems.GetAt(nItemIndex);

    // 発注数計算
    int nOrderQty = item.nMaxStock - item.nQuantity;
    if (nOrderQty <= 0) nOrderQty = item.nReorderPoint * 2;

    // 発注書ファイル名
    CString strPOPath;
    strPOPath.Format(_T("C:\\PurchaseOrders\\PO_%s_%s.txt"),
        item.szItemCode,
        CTime::GetCurrentTime().Format(_T("%Y%m%d%H%M%S")));

    CStdioFile poFile;
    if (!poFile.Open(strPOPath, CFile::modeCreate | CFile::modeWrite | CFile::typeText))
    {
        AfxMessageBox(_T("発注書ファイルを作成できません。"), MB_OK | MB_ICONERROR);
        return;
    }

    CString strPO;
    strPO.Format(
        _T("発注書\n") +
        _T("========================================\n") +
        _T("発注日時: %s\n") +
        _T("品番:     %s\n") +
        _T("品名:     %s\n") +
        _T("発注先:   %s\n") +
        _T("発注数量: %d\n") +
        _T("単価:     ¥%.2f\n") +
        _T("金額:     ¥%.0f\n") +
        _T("現在庫:   %d\n") +
        _T("発注点:   %d\n") +
        _T("========================================\n"),
        CTime::GetCurrentTime().Format(_T("%Y年%m月%d日 %H:%M:%S")),
        item.szItemCode,
        item.szItemName,
        item.szSupplier,
        nOrderQty,
        item.dUnitPrice,
        nOrderQty * item.dUnitPrice,
        item.nQuantity,
        item.nReorderPoint);

    poFile.WriteString(strPO);
    poFile.Close();

    CString strMsg;
    strMsg.Format(_T("発注書を作成しました。\nファイル: %s"), strPOPath);
    AfxMessageBox(strMsg, MB_OK | MB_ICONINFORMATION);
}

// --------------------------------------------------------------------
// 在庫最適化提案
// --------------------------------------------------------------------
void CInventoryDlg::SuggestOptimization()
{
    CString strReport;
    strReport = _T("在庫最適化提案レポート\n");
    strReport += _T("========================================\n\n");

    int nSuggestions = 0;

    for (int i = 0; i < m_arrStockItems.GetSize(); i++)
    {
        STOCKITEM& item = m_arrStockItems.GetAt(i);
        if (!item.bActive) continue;

        double dTurnover = CalcTurnoverRate(item);
        double dScore    = CalcHealthScore(item);

        // 最適発注点計算（安全在庫 + リードタイム需要）
        double dAvgDailyOut = (double)item.nMonthlyOut / 30.0;
        int nLeadTimeDays = 14; // 仮定：リードタイム14日
        int nSafetyStock  = (int)ceil(dAvgDailyOut * 3.0); // 3日分の安全在庫
        int nOptimalReorderPoint = (int)ceil(dAvgDailyOut * nLeadTimeDays) + nSafetyStock;

        // 最適最大在庫（EOQ的考え方）
        double dOrderCost    = 1000.0; // 発注費用（固定）
        double dHoldingRate  = 0.02;   // 在庫維持費用率（月2%）
        double dHoldingCost  = item.dUnitPrice * dHoldingRate;

        int nEOQ = 0;
        if (dHoldingCost > 0 && item.nMonthlyOut > 0)
        {
            double dEOQ = sqrt((2.0 * item.nMonthlyOut * dOrderCost) / dHoldingCost);
            nEOQ = (int)ceil(dEOQ);
        }

        BOOL bNeedSuggestion = FALSE;
        CString strSug;

        // 発注点の見直し提案
        if (abs(nOptimalReorderPoint - item.nReorderPoint) > item.nReorderPoint * 0.2)
        {
            strSug.Format(_T("  ・発注点見直し: 現在%d → 推奨%d\n"),
                item.nReorderPoint, nOptimalReorderPoint);
            bNeedSuggestion = TRUE;
        }

        // EOQによる最大在庫見直し
        if (nEOQ > 0 && abs(nEOQ - item.nMaxStock) > item.nMaxStock * 0.3)
        {
            CString strEOQ;
            strEOQ.Format(_T("  ・最大在庫見直し: 現在%d → 推奨%d (EOQ)\n"),
                item.nMaxStock, nEOQ);
            strSug += strEOQ;
            bNeedSuggestion = TRUE;
        }

        // 健全性スコアが低い場合
        if (dScore < 60.0)
        {
            CString strHealth;
            strHealth.Format(_T("  ・健全性スコア低下: %.0f点 (回転率:%.1f/月)\n"),
                dScore, dTurnover);
            strSug += strHealth;
            bNeedSuggestion = TRUE;
        }

        if (bNeedSuggestion)
        {
            CString strHeader;
            strHeader.Format(_T("【%s】%s\n"), item.szItemCode, item.szItemName);
            strReport += strHeader;
            strReport += strSug;
            strReport += _T("\n");
            nSuggestions++;
        }
    }

    if (nSuggestions == 0)
    {
        strReport += _T("現在、最適化提案はありません。\n在庫状態は良好です。");
    }
    else
    {
        CString strSummary;
        strSummary.Format(_T("\n合計 %d件のアイテムに最適化提案があります。"), nSuggestions);
        strReport += strSummary;
    }

    // 結果をダイアログに表示
    COptimizationReportDlg dlg(this);
    dlg.SetReportText(strReport);
    dlg.DoModal();
}

// --------------------------------------------------------------------
// データ整合性チェック
// --------------------------------------------------------------------
BOOL CInventoryDlg::ValidateDataIntegrity()
{
    CStringArray arrErrors;
    int nErrorCount = 0;

    for (int i = 0; i < m_arrStockItems.GetSize(); i++)
    {
        STOCKITEM& item = m_arrStockItems.GetAt(i);

        CString strError;
        BOOL bHasError = FALSE;

        // 必須フィールドチェック
        if (lstrlen(item.szItemCode) == 0)
        {
            strError.Format(_T("行%d: 品番が空です"), i + 1);
            arrErrors.Add(strError);
            bHasError = TRUE;
        }

        if (lstrlen(item.szItemName) == 0)
        {
            strError.Format(_T("行%d: 品名が空です"), i + 1);
            arrErrors.Add(strError);
            bHasError = TRUE;
        }

        // 数値範囲チェック
        if (item.nQuantity < 0)
        {
            strError.Format(_T("行%d [%s]: 在庫数が負数(%d)"),
                i + 1, item.szItemCode, item.nQuantity);
            arrErrors.Add(strError);
            bHasError = TRUE;
        }

        if (item.dUnitPrice < 0.0)
        {
            strError.Format(_T("行%d [%s]: 単価が負数(%.2f)"),
                i + 1, item.szItemCode, item.dUnitPrice);
            arrErrors.Add(strError);
            bHasError = TRUE;
        }

        if (item.nReorderPoint < 0)
        {
            strError.Format(_T("行%d [%s]: 発注点が負数(%d)"),
                i + 1, item.szItemCode, item.nReorderPoint);
            arrErrors.Add(strError);
            bHasError = TRUE;
        }

        if (item.nMaxStock > 0 && item.nReorderPoint > item.nMaxStock)
        {
            strError.Format(_T("行%d [%s]: 発注点(%d) > 最大在庫(%d)"),
                i + 1, item.szItemCode,
                item.nReorderPoint, item.nMaxStock);
            arrErrors.Add(strError);
            bHasError = TRUE;
        }

        nErrorCount += bHasError ? 1 : 0;
    }

    if (arrErrors.GetSize() > 0)
    {
        CString strMsg;
        strMsg.Format(_T("データ整合性エラー: %d件\n\n"), nErrorCount);

        int nShow = min((int)arrErrors.GetSize(), 15);
        for (int j = 0; j < nShow; j++)
        {
            strMsg += arrErrors.GetAt(j);
            strMsg += _T("\n");
        }

        AfxMessageBox(strMsg, MB_OK | MB_ICONWARNING);
        return FALSE;
    }

    AfxMessageBox(_T("データ整合性チェック完了: エラーなし"), MB_OK | MB_ICONINFORMATION);
    return TRUE;
}

// --------------------------------------------------------------------
// 棚卸集計
// --------------------------------------------------------------------
void CInventoryDlg::RunInventoryCount()
{
    // 棚卸開始確認
    int nResult = AfxMessageBox(
        _T("棚卸処理を開始します。\n実績在庫数の入力画面が表示されます。\n\nよろしいですか？"),
        MB_YESNO | MB_ICONQUESTION);

    if (nResult != IDYES)
        return;

    CString strReportPath;
    strReportPath.Format(_T("C:\\InventoryData\\Count_%s.txt"),
        CTime::GetCurrentTime().Format(_T("%Y%m%d%H%M%S")));

    CStdioFile reportFile;
    if (!reportFile.Open(strReportPath, CFile::modeCreate | CFile::modeWrite | CFile::typeText))
    {
        AfxMessageBox(_T("棚卸レポートファイルを作成できません。"), MB_OK | MB_ICONERROR);
        return;
    }

    reportFile.WriteString(_T("棚卸集計レポート\n"));
    reportFile.WriteString(_T("========================================\n"));
    CString strDate;
    strDate.Format(_T("棚卸日時: %s\n\n"),
        CTime::GetCurrentTime().Format(_T("%Y年%m月%d日 %H:%M")));
    reportFile.WriteString(strDate);

    int nMatchCount    = 0;
    int nMismatchCount = 0;
    double dDiffValue  = 0.0;

    for (int i = 0; i < m_arrStockItems.GetSize(); i++)
    {
        STOCKITEM& item = m_arrStockItems.GetAt(i);
        if (!item.bActive) continue;

        // 実績数入力ダイアログ（省略: item.nQuantity を使用）
        int nActual = item.nQuantity; // 実際は入力ダイアログから取得

        int nDiff = nActual - item.nQuantity;
        double dDiffVal = nDiff * item.dUnitPrice;

        CString strLine;
        if (nDiff == 0)
        {
            nMatchCount++;
            strLine.Format(_T("○ %s %s 帳簿:%d 実績:%d 差異:なし\n"),
                item.szItemCode, item.szItemName,
                item.nQuantity, nActual);
        }
        else
        {
            nMismatchCount++;
            dDiffValue += fabs(dDiffVal);
            strLine.Format(_T("× %s %s 帳簿:%d 実績:%d 差異:%+d (¥%+.0f)\n"),
                item.szItemCode, item.szItemName,
                item.nQuantity, nActual,
                nDiff, dDiffVal);

            // 在庫数修正
            item.nQuantity = nActual;
            m_bDataModified = TRUE;
        }

        reportFile.WriteString(strLine);
    }

    CString strSummary;
    strSummary.Format(
        _T("\n========================================\n") +
        _T("一致: %d件\n") +
        _T("差異: %d件\n") +
        _T("差異金額合計: ¥%.0f\n"),
        nMatchCount, nMismatchCount, dDiffValue);
    reportFile.WriteString(strSummary);
    reportFile.Close();

    // 結果通知
    CString strMsg;
    strMsg.Format(_T("棚卸完了。差異: %d件 (¥%.0f)\nレポート: %s"),
        nMismatchCount, dDiffValue, strReportPath);
    AfxMessageBox(strMsg, MB_OK | MB_ICONINFORMATION);

    RefreshListCtrl();
    UpdateStatistics();
}

// --------------------------------------------------------------------
// ファイルメニュー - 名前を付けて保存
// --------------------------------------------------------------------
void CInventoryDlg::OnMenuFileSaveAs()
{
    CFileDialog dlg(FALSE, _T("dat"),
        _T("stock"),
        OFN_OVERWRITEPROMPT | OFN_HIDEREADONLY,
        _T("在庫データファイル (*.dat)|*.dat||"),
        this);

    if (dlg.DoModal() != IDOK)
        return;

    CString strFile = dlg.GetPathName();
    if (SaveStockData(strFile))
    {
        m_strCurrentFile = strFile;
        SaveLastFilePath(strFile);

        CString strTitle;
        strTitle.Format(_T("在庫管理システム - %s"), dlg.GetFileName());
        SetWindowText(strTitle);
    }
}

// --------------------------------------------------------------------
// 検索ダイアログ起動
// --------------------------------------------------------------------
void CInventoryDlg::OnMenuSearchAdvanced()
{
    CSearchDlg dlg(this);
    if (dlg.DoModal() == IDOK)
    {
        m_strSearchKeyword = dlg.GetKeyword();
        m_nFilterCategory  = dlg.GetFilterType();
        UpdateData(FALSE);
        RefreshListCtrl();
    }
}

// --------------------------------------------------------------------
// カテゴリ管理ダイアログ
// --------------------------------------------------------------------
void CInventoryDlg::OnMenuCategory()
{
    CCategoryDlg dlg(this);
    if (dlg.DoModal() == IDOK)
    {
        InitTreeCtrl();
        RefreshListCtrl();
    }
}

// --------------------------------------------------------------------
// バックアップ
// --------------------------------------------------------------------
void CInventoryDlg::OnMenuBackup()
{
    if (m_strCurrentFile.IsEmpty())
    {
        AfxMessageBox(_T("先にファイルを保存してください。"), MB_OK | MB_ICONINFORMATION);
        return;
    }

    // バックアップファイル名（日付付き）
    CString strBackup = m_strCurrentFile;
    int nLastDot = strBackup.ReverseFind(_T('.'));
    if (nLastDot != -1)
        strBackup = strBackup.Left(nLastDot);

    CString strTimestamp = CTime::GetCurrentTime().Format(_T("%Y%m%d_%H%M%S"));
    strBackup = strBackup + _T("_bak_") + strTimestamp + _T(".dat");

    // ファイルコピー
    if (CopyFile(m_strCurrentFile, strBackup, FALSE))
    {
        CString strMsg;
        strMsg.Format(_T("バックアップ完了: %s"), strBackup);
        AfxMessageBox(strMsg, MB_OK | MB_ICONINFORMATION);
    }
    else
    {
        AfxMessageBox(_T("バックアップに失敗しました。"), MB_OK | MB_ICONERROR);
    }
}

// --------------------------------------------------------------------
// ツールチップ更新
// --------------------------------------------------------------------
void CInventoryDlg::UpdateTooltips()
{
    CToolTipCtrl* pTip = new CToolTipCtrl();
    if (!pTip->Create(this))
    {
        delete pTip;
        return;
    }

    // ボタンへのツールチップ追加
    pTip->AddTool(GetDlgItem(IDC_BTN_ADD),     _T("新規アイテムを追加します"));
    pTip->AddTool(GetDlgItem(IDC_BTN_EDIT),    _T("選択したアイテムを編集します"));
    pTip->AddTool(GetDlgItem(IDC_BTN_DELETE),  _T("選択したアイテムを削除します"));
    pTip->AddTool(GetDlgItem(IDC_BTN_SEARCH),  _T("キーワードで検索します"));
    pTip->AddTool(GetDlgItem(IDC_BTN_IMPORT),  _T("データファイルを読み込みます"));
    pTip->AddTool(GetDlgItem(IDC_BTN_EXPORT),  _T("データをCSVに出力します"));
    pTip->AddTool(GetDlgItem(IDC_BTN_REPORT),  _T("レポートを出力します"));
    pTip->AddTool(GetDlgItem(IDC_BTN_REFRESH), _T("一覧を最新状態に更新します"));

    pTip->Activate(TRUE);

    // ツールチップは破棄しない（ウィンドウ有効期間中保持）
    // 実際のアプリではメンバ変数で保持すること
    delete pTip;
}

// --------------------------------------------------------------------
// グラフエリアのダブルクリック（グラフ詳細ダイアログ）
// --------------------------------------------------------------------
void CInventoryDlg::OnDblclkStaticGraph()
{
    // 詳細グラフダイアログを表示
    CGraphDetailDlg dlg(this);
    dlg.SetStockData(&m_arrStockItems);
    dlg.DoModal();
}

// --------------------------------------------------------------------
// 帳票印刷
// --------------------------------------------------------------------
void CInventoryDlg::OnMenuPrint()
{
    // 印刷確認
    CString strMsg;
    strMsg.Format(_T("在庫一覧を印刷します。\n対象: %d件\n\n印刷しますか？"),
        m_listStock.GetItemCount());

    if (AfxMessageBox(strMsg, MB_YESNO | MB_ICONQUESTION) != IDYES)
        return;

    // 印刷処理（CReportDlgに委譲）
    CReportDlg dlg(this);
    dlg.SetStockData(&m_arrStockItems);
    dlg.SetPrintMode(CReportDlg::PRINT_INVENTORY_LIST);
    dlg.DoModal();
}

// --------------------------------------------------------------------
// ウィンドウ設定保存（終了時）
// --------------------------------------------------------------------
void CInventoryDlg::SaveWindowSettings()
{
    // ウィンドウ位置・サイズを保存
    WINDOWPLACEMENT wp;
    GetWindowPlacement(&wp);

    HKEY hKey;
    DWORD dwDisp;
    if (RegCreateKeyEx(HKEY_CURRENT_USER,
        _T("Software\\InventoryApp\\WindowPos"),
        0, NULL, REG_OPTION_NON_VOLATILE,
        KEY_WRITE, NULL, &hKey, &dwDisp) == ERROR_SUCCESS)
    {
        RegSetValueEx(hKey, _T("Placement"), 0, REG_BINARY,
            (LPBYTE)&wp, sizeof(wp));

        // 列幅保存
        for (int i = 0; i <= 11; i++)
        {
            char szValName[32];
            sprintf(szValName, "ColWidth%d", i);
            int nWidth = m_listStock.GetColumnWidth(i);
            RegSetValueEx(hKey, szValName, 0, REG_DWORD,
                (LPBYTE)&nWidth, sizeof(nWidth));
        }

        RegCloseKey(hKey);
    }

    TRACE(_T("SaveWindowSettings: 設定保存完了\n"));
}

// --------------------------------------------------------------------
// ウィンドウ設定読み込み（起動時）
// --------------------------------------------------------------------
void CInventoryDlg::LoadWindowSettings()
{
    HKEY hKey;
    if (RegOpenKeyEx(HKEY_CURRENT_USER,
        _T("Software\\InventoryApp\\WindowPos"),
        0, KEY_READ, &hKey) != ERROR_SUCCESS)
        return;

    WINDOWPLACEMENT wp;
    DWORD dwSize = sizeof(wp);
    DWORD dwType;
    if (RegQueryValueEx(hKey, _T("Placement"), NULL, &dwType,
        (LPBYTE)&wp, &dwSize) == ERROR_SUCCESS)
    {
        SetWindowPlacement(&wp);
    }

    // 列幅読み込み
    for (int i = 0; i <= 11; i++)
    {
        char szValName[32];
        sprintf(szValName, "ColWidth%d", i);
        int nWidth = 0;
        dwSize = sizeof(nWidth);
        if (RegQueryValueEx(hKey, szValName, NULL, &dwType,
            (LPBYTE)&nWidth, &dwSize) == ERROR_SUCCESS && nWidth > 0)
        {
            m_listStock.SetColumnWidth(i, nWidth);
        }
    }

    RegCloseKey(hKey);

    TRACE(_T("LoadWindowSettings: 設定読み込み完了\n"));
}

// --------------------------------------------------------------------
// 在庫トレンド分析（過去6ヶ月）
// --------------------------------------------------------------------
void CInventoryDlg::AnalyzeTrend()
{
    CString strAnalysis;
    strAnalysis = _T("在庫トレンド分析（過去6ヶ月）\n");
    strAnalysis += _T("========================================\n\n");

    int nIncrease = 0; // 増加傾向
    int nDecrease = 0; // 減少傾向
    int nStable   = 0; // 安定

    for (int i = 0; i < m_arrStockItems.GetSize(); i++)
    {
        STOCKITEM& item = m_arrStockItems.GetAt(i);
        if (!item.bActive) continue;

        CArray<int, int> arrIn, arrOut;
        GetMonthlyTrend(i, arrIn, arrOut);

        if (arrIn.GetSize() < 2) continue;

        // 線形回帰（簡易版）
        double dSumX = 0, dSumY = 0, dSumXY = 0, dSumX2 = 0;
        int nN = (int)arrIn.GetSize();

        for (int m = 0; m < nN; m++)
        {
            dSumX  += m;
            dSumY  += arrIn.GetAt(m) - arrOut.GetAt(m);
            dSumXY += m * (arrIn.GetAt(m) - arrOut.GetAt(m));
            dSumX2 += m * m;
        }

        double dSlope = 0;
        double dDenom = nN * dSumX2 - dSumX * dSumX;
        if (fabs(dDenom) > 0.001)
            dSlope = (nN * dSumXY - dSumX * dSumY) / dDenom;

        CString strTrend;
        if (dSlope > 0.5)
        {
            strTrend = _T("↑増加");
            nIncrease++;
        }
        else if (dSlope < -0.5)
        {
            strTrend = _T("↓減少");
            nDecrease++;
        }
        else
        {
            strTrend = _T("→安定");
            nStable++;
        }

        CString strLine;
        strLine.Format(_T("%-20s %s (傾き:%.2f)\n"),
            item.szItemName, strTrend, dSlope);
        strAnalysis += strLine;
    }

    CString strSummary;
    strSummary.Format(_T("\n増加傾向: %d件 / 減少傾向: %d件 / 安定: %d件"),
        nIncrease, nDecrease, nStable);
    strAnalysis += strSummary;

    // 結果表示
    AfxMessageBox(strAnalysis, MB_OK | MB_ICONINFORMATION);
}

// --------------------------------------------------------------------
// アプリケーション設定
// --------------------------------------------------------------------
void CInventoryDlg::OnMenuSettings()
{
    CSettingsDlg dlg(this);

    // 現在の設定を読み込み
    dlg.SetAutoSaveInterval(5); // 5分
    dlg.SetDefaultDataPath(_T("C:\\InventoryData\\"));
    dlg.SetCompanyName(_T("株式会社サンプル製造"));
    dlg.SetReorderAlertEnabled(TRUE);

    if (dlg.DoModal() == IDOK)
    {
        // タイマー設定更新
        KillTimer(IDT_AUTOSAVE);
        int nInterval = dlg.GetAutoSaveInterval();
        if (nInterval > 0)
            SetTimer(IDT_AUTOSAVE, nInterval * 60000, NULL);

        // レジストリに設定保存
        HKEY hKey;
        DWORD dwDisp;
        if (RegCreateKeyEx(HKEY_CURRENT_USER,
            _T("Software\\InventoryApp\\Settings"),
            0, NULL, REG_OPTION_NON_VOLATILE,
            KEY_WRITE, NULL, &hKey, &dwDisp) == ERROR_SUCCESS)
        {
            int nVal = dlg.GetAutoSaveInterval();
            RegSetValueEx(hKey, _T("AutoSaveMinutes"), 0, REG_DWORD,
                (LPBYTE)&nVal, sizeof(nVal));

            CString strPath = dlg.GetDefaultDataPath();
            RegSetValueEx(hKey, _T("DefaultDataPath"), 0, REG_SZ,
                (LPBYTE)(LPCTSTR)strPath,
                (strPath.GetLength() + 1) * sizeof(TCHAR));

            RegCloseKey(hKey);
        }

        AfxMessageBox(_T("設定を保存しました。"), MB_OK | MB_ICONINFORMATION);
    }
}

// --------------------------------------------------------------------
// バージョン情報
// --------------------------------------------------------------------
void CInventoryDlg::OnMenuAbout()
{
    CString strMsg;
    strMsg.Format(
        _T("在庫管理システム\n\n") +
        _T("バージョン: 2.5.0\n") +
        _T("ビルド日時: %s %s\n") +
        _T("開発: 株式会社サンプル製造 システム部\n\n") +
        _T("Visual C++ 6.0 MFC で開発"),
        __DATE__, __TIME__);

    AfxMessageBox(strMsg, MB_OK | MB_ICONINFORMATION);
}

// --------------------------------------------------------------------
// ヘルプ表示
// --------------------------------------------------------------------
void CInventoryDlg::OnMenuHelp()
{
    // HTMLヘルプ表示
    CString strHelpPath;
    strHelpPath.Format(_T("%s\\help\\inventory_help.chm"),
        AfxGetApp()->m_pszHelpFilePath);

    if (!PathFileExists(strHelpPath))
    {
        AfxMessageBox(_T("ヘルプファイルが見つかりません。"), MB_OK | MB_ICONWARNING);
        return;
    }

    HtmlHelp(NULL, strHelpPath, HH_DISPLAY_TOPIC, 0);
}

// 入力検証付き数値入力ヘルパー
int CInventoryDlg::GetIntFromEdit(UINT nCtrlID, int nMin, int nMax, BOOL& bValid)
{
    CString strVal;
    GetDlgItemText(nCtrlID, strVal);
    strVal.TrimLeft();
    strVal.TrimRight();

    if (strVal.IsEmpty())
    {
        bValid = FALSE;
        return 0;
    }

    int nVal = atoi(strVal);
    bValid = (nVal >= nMin && nVal <= nMax);
    return nVal;
}

double CInventoryDlg::GetDoubleFromEdit(UINT nCtrlID, double dMin, double dMax, BOOL& bValid)
{
    CString strVal;
    GetDlgItemText(nCtrlID, strVal);
    strVal.TrimLeft();
    strVal.TrimRight();

    if (strVal.IsEmpty())
    {
        bValid = FALSE;
        return 0.0;
    }

    double dVal = atof(strVal);
    bValid = (dVal >= dMin && dVal <= dMax);
    return dVal;
}

// --------------------------------------------------------------------
// ステータス更新ヘルパー
// --------------------------------------------------------------------
void CInventoryDlg::SetStatus(const CString& strMsg)
{
    m_strStatusMessage = strMsg;
    UpdateData(FALSE);
    TRACE(_T("Status: %s\n"), strMsg);
}

// --------------------------------------------------------------------
// デバッグ用: 全アイテムをTRACEに出力
// --------------------------------------------------------------------
#ifdef _DEBUG
void CInventoryDlg::DumpAllItems()
{
    TRACE(_T("=== 在庫アイテム一覧 (%d件) ===\n"), m_arrStockItems.GetSize());
    for (int i = 0; i < m_arrStockItems.GetSize(); i++)
    {
        STOCKITEM& item = m_arrStockItems.GetAt(i);
        TRACE(_T("[%3d] %s %s Qty:%d Price:%.2f Active:%d\n"),
            i,
            item.szItemCode,
            item.szItemName,
            item.nQuantity,
            item.dUnitPrice,
            item.bActive);
    }
    TRACE(_T("=================================\n"));
}
#endif

// InventoryDlg.cpp 終端
