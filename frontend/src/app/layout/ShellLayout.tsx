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
import { AppShell, type AppShellNavItem, type MenuItem } from 'make-you-chic-ui'
import type { MouseEvent, ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { useMessages } from '../i18n/I18nProvider'
import { useLoginState } from '../login-state/LoginStateGate'
import { buildSidebarEntries, buildUserMenuItems } from '../navigation/navigationItems'
import { useFeatureRegistry } from '../registry/FeatureRegistryContext'

export interface ShellLayoutProps {
  children: ReactNode
}

/** サイドバーにホームと条件を満たす項目、トップバーにユーザーの表示名とユーザーメニューを置く。 */
export function ShellLayout({ children }: ShellLayoutProps) {
  const registrations = useFeatureRegistry()
  const loginState = useLoginState()
  const t = useMessages()
  const navigate = useNavigate()

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
  const userMenuItems: MenuItem[] = buildUserMenuItems(registrations, loginState).map((item) => ({
    label: t(item.labelKey),
    onClick: item.action,
  }))
  const user = loginState.displayName ? { name: loginState.displayName } : undefined

  return (
    <AppShell navItems={navItems} user={user} userMenuItems={userMenuItems}>
      {children}
    </AppShell>
  )
}
