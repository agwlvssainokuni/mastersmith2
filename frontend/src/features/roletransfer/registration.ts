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
// 権限の受け渡しの画面を骨組みに登録する（Intent 261004-role-menu の U6 の D1、functional-spec.md の 1.1、
// frontend-components.md の 5節、L1）。道は S7 `/admin/role-transfer`。サイドバーの「権限の受け渡し」は管理の区画（260、
// 管理者にだけ示す）。隠すのは見せ方だけで、判定はサーバー（NFR1.1）。画面は遅延読み込み（lazy）で、入口の JavaScript に
// 入れない（計画 10節）。このファイルは文言だけを静的に読む。
import { lazy } from 'react'
import type { FeatureRegistration } from '../../app/registry/types'
import { roleTransferMessages } from './messages'

const RoleTransferPage = lazy(() =>
  import('./RoleTransferPage').then((module) => ({ default: module.RoleTransferPage })),
)

/** 権限の受け渡しの画面の道 */
export const ROLE_TRANSFER_PATH = '/admin/role-transfer'

/** 権限の受け渡しの画面の機能の登録 */
export const registration: FeatureRegistration = {
  featureId: 'roletransfer',
  routes: [
    { path: ROLE_TRANSFER_PATH, screen: RoleTransferPage, layout: 'SHELL', access: 'ADMIN' },
  ],
  sidebarItems: [
    {
      id: 'roletransfer',
      labelKey: 'roletransfer.nav.label',
      path: ROLE_TRANSFER_PATH,
      order: 260,
      visibleWhen: 'ADMIN',
      section: 'ADMIN',
    },
  ],
  messages: roleTransferMessages,
}
