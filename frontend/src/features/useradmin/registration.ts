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
//
// 利用者の管理の画面を画面の骨組みに登録する（frontend-components.md の 6節、functional-spec.md の 2節）。
// 骨組み（src/app/）のファイルは書き換えない（registrationModules.ts がこのファイルを読み込む）。
// サイドバーの「利用者の管理」は管理者にだけ示す（「管理」200・「DSL」210・「利用者の招待」220 の次）。隠すのは表示の
// 切り替えにすぎず、判定はサーバー側で行う（project.md の Mandated、FR8.2、NFR1.1）。画面は遅延読み込み（lazy）で、
// 入口の JavaScript に入れない（NFR5.6）。
import { lazy } from 'react'
import type { FeatureRegistration } from '../../app/registry/types'
import { userAdminMessages } from './messages'

const UserAdminPage = lazy(() =>
  import('./UserAdminPage').then((module) => ({ default: module.UserAdminPage })),
)

/** 利用者の管理の画面の URL */
export const USER_ADMIN_PATH = '/admin/users'

/** 利用者の管理の画面の機能の登録 */
export const registration: FeatureRegistration = {
  featureId: 'useradmin',
  routes: [
    {
      path: USER_ADMIN_PATH,
      screen: UserAdminPage,
      layout: 'SHELL',
      access: 'ADMIN',
    },
  ],
  sidebarItems: [
    {
      id: 'useradmin',
      labelKey: 'useradmin.nav.label',
      path: USER_ADMIN_PATH,
      order: 230,
      visibleWhen: 'ADMIN',
      section: 'ADMIN',
    },
  ],
  messages: userAdminMessages,
}
