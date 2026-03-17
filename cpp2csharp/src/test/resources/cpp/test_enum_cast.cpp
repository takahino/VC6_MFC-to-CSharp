// VC++6 enum キャスト変換テスト
//
// VC++6 では enum は int 固定のため、int との直接比較・代入が可能。
// C# 化すると明示的キャストが必要になる。
//
// このファイルは DynamicRuleGenerator.generateFromValues() を用いて
// 外部から提供した enum メンバ名からキャストルールを動的生成し適用する例。

#include "stdafx.h"

// アイテム種別 enum（VC++6: int 固定）
enum ItemKind {
    ITEM_NONE   = 0,
    ITEM_APPLE  = 1,
    ITEM_BANANA = 2,
    ITEM_CHERRY = 3,
    ITEM_GRAPE  = 4,
};

// 選択状態 enum
enum SelectState {
    SEL_NONE   = 0,
    SEL_SINGLE = 1,
    SEL_MULTI  = 2,
};

// ====================================================================
// 比較での使用: int 変数と enum メンバを直接比較（C# では (EnumType) 必要）
// ====================================================================
void CItemSelectDlg::CheckSelection()
{
    int nSel = GetSelectedItem();
    int nKind = GetItemKind(nSel);
    int nState = GetSelectState();

    // int と enum メンバの直接比較
    if ( nKind == ITEM_APPLE ) {
        AfxMessageBox( "りんごが選択されました" , MB_OK ) ;
    }
    if ( nKind == ITEM_BANANA || nKind == ITEM_CHERRY ) {
        AfxMessageBox( "バナナかさくらんぼです" , MB_OK ) ;
    }
    if ( nKind == ITEM_GRAPE ) {
        AfxMessageBox( "ぶどうです" , MB_OK ) ;
    }

    // 状態の判定
    if ( nState == SEL_SINGLE ) {
        DoSingleSelect() ;
    } else if ( nState == SEL_MULTI ) {
        DoMultiSelect() ;
    }
}

// ====================================================================
// 代入での使用: int 変数に enum メンバを直接代入（C# では (int) 必要）
// ====================================================================
void CItemSelectDlg::LoadItems( int nCategory )
{
    // int 変数に enum メンバを代入
    int nDefaultKind = ITEM_APPLE ;
    int nInitState   = SEL_NONE ;

    for ( int i = 0 ; i < m_nCount ; i++ ) {
        int nKind = GetKindAt( i ) ;
        if ( nKind == ITEM_NONE ) {
            nKind = ITEM_APPLE ;
        }
        if ( nKind == ITEM_GRAPE ) {
            m_nSelectedKind = ITEM_GRAPE ;
        }
    }

    // 状態初期化
    m_nState = SEL_NONE ;
}

// ====================================================================
// スイッチでの使用
// ====================================================================
CString CItemSelectDlg::GetItemLabel( int nKind )
{
    CString strLabel ;
    switch ( nKind ) {
    case ITEM_APPLE  : strLabel = "Apple" ; break ;
    case ITEM_BANANA : strLabel = "Banana" ; break ;
    case ITEM_CHERRY : strLabel = "Cherry" ; break ;
    case ITEM_GRAPE  : strLabel = "Grape" ; break ;
    default          : strLabel = "Unknown" ; break ;
    }
    return strLabel ;
}
