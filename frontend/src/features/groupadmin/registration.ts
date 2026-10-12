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
// グループの管理の画面を骨組みに登録する（Intent 261004-role-menu の U6 の D1、functional-spec.md の 1.1、
// frontend-components.md の 4節、L1）。道は S6 の一覧 `/admin/groups` と詳細 `/admin/groups/:groupId`。
// サイドバーの「グループ」は管理の区画（250、管理者にだけ示す）。隠すのは見せ方だけで、判定はサーバー（NFR1.1）。
// 画面は遅延読み込み（lazy）で、入口の JavaScript に入れない（計画 10節）。このファイルは文言と道だけを静的に読む。
import { lazy } from 'react'
import type { FeatureRegistration } from '../../app/registry/types'
import { groupAdminMessages } from './messages'
import { GROUP_DETAIL_ROUTE, GROUP_LIST_PATH } from './paths'

const GroupListPage = lazy(() =>
  import('./GroupListPage').then((module) => ({ default: module.GroupListPage })),
)

const GroupDetailPage = lazy(() =>
  import('./GroupDetailPage').then((module) => ({ default: module.GroupDetailPage })),
)

/** グループの管理の画面の機能の登録 */
export const registration: FeatureRegistration = {
  featureId: 'groupadmin',
  routes: [
    { path: GROUP_LIST_PATH, screen: GroupListPage, layout: 'SHELL', access: 'ADMIN' },
    { path: GROUP_DETAIL_ROUTE, screen: GroupDetailPage, layout: 'SHELL', access: 'ADMIN' },
  ],
  sidebarItems: [
    {
      id: 'groupadmin',
      labelKey: 'groupadmin.nav.label',
      path: GROUP_LIST_PATH,
      order: 250,
      visibleWhen: 'ADMIN',
      section: 'ADMIN',
    },
  ],
  messages: groupAdminMessages,
}
