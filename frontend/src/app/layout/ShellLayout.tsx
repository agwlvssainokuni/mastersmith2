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
import { AppShell, type AppShellNavItem, type MenuItem } from 'make-you-chic-ui'
import type { KeyboardEvent, MouseEvent, ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { useIsAdminForbiddenHere } from '../admin-forbidden/AdminForbiddenProvider'
import { AdminForbiddenView } from '../admin-forbidden/AdminForbiddenView'
import { useDisplaySettings } from '../display-settings/DisplaySettingsProvider'
import { useMessages } from '../i18n/I18nProvider'
import { useLoginState } from '../login-state/LoginStateGate'
import { buildSidebarEntries, buildUserMenuItems } from '../navigation/navigationItems'
import { useFeatureRegistry } from '../registry/FeatureRegistryContext'

export interface ShellLayoutProps {
  children: ReactNode
}

/** サイドバーにホームと条件を満たす項目、トップバーにユーザーの氏名とユーザーメニューを置く。 */
export function ShellLayout({ children }: ShellLayoutProps) {
  const registrations = useFeatureRegistry()
  const loginState = useLoginState()
  const { displayName } = useDisplaySettings()
  const t = useMessages()
  const navigate = useNavigate()
  const forbiddenHere = useIsAdminForbiddenHere()

  const navItems: AppShellNavItem[] = buildSidebarEntries(registrations, loginState).map(
    (entry) => ({
      label: t(entry.labelKey),
      icon: entry.id === 'home' ? 'home' : 'list',
      href: entry.path,
      onClick: (event: MouseEvent) => {
        event.preventDefault()
        void navigate(entry.path)
      },
    }),
  )
  // ユーザーメニューの項目は、path を持てば href（<a> で描かれ、リンクとして読み上げられる）と、既定の移動を止めて
  // 読み込み直しなしで移る onClick にする（サイドバーと同じ作り。U7 の機能設計 9.3）。action の項目は今までどおり
  // href を渡さず onClick で action を呼ぶ（<button> で描かれる）。
  const userMenuItems: MenuItem[] = buildUserMenuItems(registrations, loginState).map((item) => {
    const label = t(item.labelKey)
    if (item.path !== undefined) {
      const { path } = item
      return {
        label,
        href: path,
        onClick: (event: MouseEvent | KeyboardEvent) => {
          event.preventDefault()
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
    <AppShell navItems={navItems} user={user} userMenuItems={userMenuItems}>
      {forbiddenHere ? <AdminForbiddenView /> : children}
    </AppShell>
  )
}
