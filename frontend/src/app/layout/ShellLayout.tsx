/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// アプリシェルの中の配置（make-you-chic-ui の AppShell。サイドバー・トップバー・コンテンツ）（BR7.6）。
// 今の URL が権限が無い URL のときは、コンテンツの領域の子の代わりに S6 を描く。サイドバー・トップバー・ユーザーメニューは
// そのまま（U4 の D3）。
// Intent 261004-role-menu の U7（D10〜D17、frontend-components.md の 4.5、L8・L15）:
// - サイドバーは navSections（buildNavSections の区画）と開閉の状態（navExpandedIds）・文言（navLabels）で描く。
// - 開閉の状態はこの部品が持ち、sessionStorage に覚える（navExpansion）。業務のメニューの木を読み終えたとき（loaded の
//   応答が変わったとき）だけ今の木に無い biz: の id を捨て、今の項目の祖先の id の並びの中身が変わったときだけ祖先を足す
//   （道が変わったとき・木が後から届いたとき。同じ道で閉じた枝は開き直さない。機能設計の承認の場の直し R-02）。
// - トップバーの右に作業ロールの切り替え（WorkRoleSwitcher）を置く。768px 未満では、トップバーから外して内容の領域の
//   先頭に置く（依頼者の決定 G2: A。機能設計 D9 との差。幅 220px 固定のサイドバーの横のトップバーに収まらないため）。
// - ユーザーメニューの path の項目は、既定の移動を止め、beforeNavigate があれば呼んでから移る（L15）。
import { AppShell, type MenuItem } from 'make-you-chic-ui'
import {
  useEffect,
  useMemo,
  useState,
  type KeyboardEvent,
  type MouseEvent,
  type ReactNode,
} from 'react'
import { useTranslation } from 'react-i18next'
import { useLocation, useNavigate } from 'react-router'
import { useIsAdminForbiddenHere } from '../admin-forbidden/AdminForbiddenProvider'
import { AdminForbiddenView } from '../admin-forbidden/AdminForbiddenView'
import { useDisplaySettings } from '../display-settings/DisplaySettingsProvider'
import { useDisplayLanguage, useMessages } from '../i18n/I18nProvider'
import { useLoginState } from '../login-state/LoginStateGate'
import { useBusinessNavigation } from '../navigation/BusinessNavigationProvider'
import { buildNavSections } from '../navigation/buildNavSections'
import { addAncestors, loadExpanded, pruneExpanded, saveExpanded } from '../navigation/navExpansion'
import type { NavigationResponse } from '../navigation/navigationApi'
import { buildUserMenuItems } from '../navigation/navigationItems'
import { useFeatureRegistry } from '../registry/FeatureRegistryContext'
import { WorkRoleSwitcher } from '../work-role/WorkRoleSwitcher'
import './ShellLayout.css'
import { useNarrowLayout } from './useNarrowLayout'

export interface ShellLayoutProps {
  children: ReactNode
}

/** 開閉の状態と、照らし合わせ・祖先の足し方の前の値 */
interface ExpansionState {
  ids: ReadonlySet<string>
  /** 最後に照らし合わせた業務のメニューの応答 */
  prunedWith: NavigationResponse | null
  /** 最後に足した祖先の id の並び（中身で比べる） */
  ancestorsKey: string
}

/** サイドバーに区画、トップバーに作業ロールの切り替えとユーザーの氏名とユーザーメニューを置く。 */
export function ShellLayout({ children }: ShellLayoutProps) {
  const registrations = useFeatureRegistry()
  const loginState = useLoginState()
  const { displayName } = useDisplaySettings()
  const t = useMessages()
  const { t: format } = useTranslation()
  const language = useDisplayLanguage()
  const navigate = useNavigate()
  const location = useLocation()
  const forbiddenHere = useIsAdminForbiddenHere()
  const business = useBusinessNavigation()
  const narrow = useNarrowLayout()

  const { sections, currentAncestorIds, businessIds } = buildNavSections({
    loginState,
    registrations,
    business,
    location: { pathname: location.pathname, search: location.search },
    language,
    t,
    navigate: (to) => void navigate(to),
  })

  const [expansion, setExpansion] = useState<ExpansionState>(() => ({
    ids: loadExpanded(),
    prunedWith: null,
    ancestorsKey: JSON.stringify([]),
  }))
  // 照らし合わせと祖先の足し方は、前の値と比べて描画の中で直す（React の「値が変わったときに状態を直す」の形）。
  const loadedResponse = business.status === 'loaded' ? business.response : null
  const ancestorsKey = JSON.stringify(currentAncestorIds)
  let nextExpansion = expansion
  if (loadedResponse !== null && expansion.prunedWith !== loadedResponse) {
    nextExpansion = {
      ...nextExpansion,
      ids: pruneExpanded(nextExpansion.ids, businessIds),
      prunedWith: loadedResponse,
    }
  }
  if (expansion.ancestorsKey !== ancestorsKey) {
    nextExpansion = {
      ...nextExpansion,
      ids: addAncestors(nextExpansion.ids, currentAncestorIds),
      ancestorsKey,
    }
  }
  if (nextExpansion !== expansion) {
    setExpansion(nextExpansion)
  }

  // 開閉の状態を覚える（外の値だけを直す）。
  useEffect(() => {
    saveExpanded(expansion.ids)
  }, [expansion.ids])

  const expandedIds = useMemo(() => new Set(nextExpansion.ids), [nextExpansion.ids])

  // 型の注記を付けずに作ってから渡す（make-you-chic-ui の SidebarLabels の containsCurrent は a9309c4 で必須に
  // なった。注記の無いオブジェクトなら、containsCurrent を持たない前の版の型にも余分な項目として咎められずに渡せる）。
  // containsCurrent は、畳んだ状態で今の項目を子孫に持つ1段目のまとまりの読み上げの名前（Intent 261015 の FR2.3）。
  const navLabels = {
    navigationLabel: t('nav.navigationLabel'),
    expandGroup: (label: string) => format('nav.expandGroup', { label }),
    collapseGroup: (label: string) => format('nav.collapseGroup', { label }),
    containsCurrent: (label: string) => format('nav.containsCurrent', { label }),
  }

  // ユーザーメニューの項目は、path を持てば href（<a> で描かれ、リンクとして読み上げられる）と、既定の移動を止めて
  // 読み込み直しなしで移る onClick にする（サイドバーと同じ作り。U7 の機能設計 9.3）。beforeNavigate があれば移る前に
  // 1回呼ぶ（Intent 261004-role-menu の U7 の L15）。action の項目は今までどおり href を渡さず onClick で action を呼ぶ
  // （<button> で描かれる）。
  const userMenuItems: MenuItem[] = buildUserMenuItems(registrations, loginState).map((item) => {
    const label = t(item.labelKey)
    if (item.path !== undefined) {
      const { path, beforeNavigate } = item
      return {
        label,
        href: path,
        onClick: (event: MouseEvent | KeyboardEvent) => {
          event.preventDefault()
          beforeNavigate?.()
          void navigate(path)
        },
      }
    }
    return { label, onClick: item.action }
  })
  // ユーザーメニューの名前は氏名（保存の直後は保存の後の氏名。無ければログイン状態の氏名）（U4 の D8、AC4.1.8）。
  const name = displayName ?? loginState.displayName
  const user = name ? { name } : undefined

  return (
    <AppShell
      navSections={sections}
      navExpandedIds={expandedIds}
      onNavExpandedChange={(ids) => setExpansion((prev) => ({ ...prev, ids: new Set(ids) }))}
      navLabels={navLabels}
      topbarEnd={narrow ? undefined : <WorkRoleSwitcher />}
      user={user}
      userMenuItems={userMenuItems}
    >
      {narrow && (
        <div className="shell-work-role-bar" data-testid="shell-work-role-bar">
          <WorkRoleSwitcher />
        </div>
      )}
      {forbiddenHere ? <AdminForbiddenView /> : children}
    </AppShell>
  )
}
