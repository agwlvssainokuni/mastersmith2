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
// ロールの管理の画面を骨組みに登録する（Intent 261004-role-menu の U6 の D1、functional-spec.md の 1.1、
// frontend-components.md の 10節、L1、NFR4.7）。道は S3 `/admin/roles`・S4 `/admin/roles/:roleId`・
// S5 `/admin/roles/:roleId/assignments` の3つで、S4 と S5 は同じ RoleDetailPage を描く（タブは道で選ぶ）。
// サイドバーの「ロール」は管理の区画（240、管理者にだけ示す）。隠すのは見せ方だけで、判定はサーバー（NFR1.1）。
// 画面は遅延読み込み（lazy）で、入口の JavaScript に入れない（計画 10節）。このファイルは文言だけを静的に読む。
import { lazy } from 'react'
import type { FeatureRegistration } from '../../app/registry/types'
import { roleAdminMessages } from './messages'
import { ROLE_ASSIGNMENTS_ROUTE, ROLE_DETAIL_ROUTE, ROLE_LIST_PATH } from './paths'

const RoleListPage = lazy(() =>
  import('./RoleListPage').then((module) => ({ default: module.RoleListPage })),
)

const RoleDetailPage = lazy(() =>
  import('./RoleDetailPage').then((module) => ({ default: module.RoleDetailPage })),
)

/** ロールの管理の画面の機能の登録 */
export const registration: FeatureRegistration = {
  featureId: 'roleadmin',
  routes: [
    { path: ROLE_LIST_PATH, screen: RoleListPage, layout: 'SHELL', access: 'ADMIN' },
    { path: ROLE_DETAIL_ROUTE, screen: RoleDetailPage, layout: 'SHELL', access: 'ADMIN' },
    { path: ROLE_ASSIGNMENTS_ROUTE, screen: RoleDetailPage, layout: 'SHELL', access: 'ADMIN' },
  ],
  sidebarItems: [
    {
      id: 'roleadmin',
      labelKey: 'roleadmin.nav.label',
      path: ROLE_LIST_PATH,
      order: 240,
      visibleWhen: 'ADMIN',
      section: 'ADMIN',
    },
  ],
  messages: roleAdminMessages,
}
