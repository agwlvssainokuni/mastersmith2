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
// サイドバーとユーザーメニューの項目を作る（BR7.6）。
import { HOME_PATH } from '../registry/validateRegistrations'
import type { FeatureRegistration, LoginState, UserMenuItemRegistration } from '../registry/types'

/** サイドバーに表示する項目 */
export interface SidebarEntry {
  id: string
  labelKey: string
  path: string
}

/** 先頭に置く「ホーム」の項目 */
export const HOME_ENTRY: SidebarEntry = { id: 'home', labelKey: 'nav.home', path: HOME_PATH }

/**
 * サイドバーの項目を作る。ログイン中は先頭に「ホーム」、続いて visibleWhen を満たす登録の項目を order の順に並べる。
 * 未ログインでは項目を出さない。
 */
export function buildSidebarEntries(
  registrations: readonly FeatureRegistration[],
  loginState: LoginState,
): SidebarEntry[] {
  if (!loginState.loggedIn) {
    return []
  }
  const items = registrations
    .flatMap((registration) => registration.sidebarItems ?? [])
    .filter((item) => item.visibleWhen === 'LOGGED_IN' || loginState.admin)
    .sort((a, b) => a.order - b.order)
    .map(({ id, labelKey, path }) => ({ id, labelKey, path }))
  return [HOME_ENTRY, ...items]
}

/** ユーザーメニューの項目を order の順に並べる。未ログインでは項目を出さない。 */
export function buildUserMenuItems(
  registrations: readonly FeatureRegistration[],
  loginState: LoginState,
): UserMenuItemRegistration[] {
  if (!loginState.loggedIn) {
    return []
  }
  return registrations
    .flatMap((registration) => registration.userMenuItems ?? [])
    .sort((a, b) => a.order - b.order)
}
