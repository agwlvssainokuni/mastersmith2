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
// サイドバーの管理の区画の項目と、ユーザーメニューの項目を選ぶ（BR7.6）。ホーム・業務の区画は buildNavSections が作る
// （Intent 261004-role-menu の U7、計画 14節 Q5: A。サイドバーの平らな一覧 buildSidebarEntries・HOME_ENTRY は消した）。
import type {
  FeatureRegistration,
  LoginState,
  SidebarItemRegistration,
  UserMenuItemRegistration,
} from '../registry/types'

/**
 * サイドバーの管理の区画に出す項目を選ぶ（Intent 261004-role-menu の U7 の D12、計画 14節 Q5: A）。ログイン中の管理者
 * だけに、登録の section が ADMIN の項目を order の順に並べる。管理者でない・未ログインでは空。作業ロールでは変わらない。
 * 出し分けは見せ方だけで、管理の API はサーバーの判定で守る。
 */
export function selectAdminSidebarItems(
  registrations: readonly FeatureRegistration[],
  loginState: LoginState,
): SidebarItemRegistration[] {
  if (!loginState.loggedIn || !loginState.admin) {
    return []
  }
  return registrations
    .flatMap((registration) => registration.sidebarItems ?? [])
    .filter((item) => item.section === 'ADMIN')
    .sort((a, b) => a.order - b.order)
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
