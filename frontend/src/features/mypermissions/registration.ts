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
// 自分の権限の画面（S8、Should）を骨組みに登録する（Intent 261004-role-menu の U7 の D1・D24・D26、L10、計画 14節 Q3: A）。
// 道は `/me/permissions`（ログインだけで開ける画面。値はサーバーが今の作業ロールで返す）。ユーザーメニューに「自分の権限」
// （order 70、プリファレンスの 80 の前）。骨組みは道が登録されているときだけ作業ロールの一覧とホームに入口を出す（D-6）。
// 画面は遅延読み込み（lazy）で、入口の JavaScript に入れない（計画 11節）。このファイルは文言だけを静的に読む。
import { lazy } from 'react'
import type { FeatureRegistration } from '../../app/registry/types'
import { myPermissionsMessages } from './messages'

const MyPermissionsPage = lazy(() =>
  import('./MyPermissionsPage').then((module) => ({ default: module.MyPermissionsPage })),
)

/** 自分の権限の画面の道 */
export const MY_PERMISSIONS_PAGE_PATH = '/me/permissions'

/** 自分の権限の画面の機能の登録 */
export const registration: FeatureRegistration = {
  featureId: 'mypermissions',
  routes: [
    {
      path: MY_PERMISSIONS_PAGE_PATH,
      screen: MyPermissionsPage,
      layout: 'SHELL',
      access: 'LOGGED_IN',
    },
  ],
  userMenuItems: [
    {
      id: 'mypermissions-open',
      labelKey: 'mypermissions.menu',
      path: MY_PERMISSIONS_PAGE_PATH,
      order: 70,
    },
  ],
  messages: myPermissionsMessages,
}
