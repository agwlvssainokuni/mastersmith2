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
// 招待の管理の画面を画面の骨組みに登録する（frontend-components.md の 6節、NFR9.2）。骨組み（src/app/）のファイルは書き換えない。
// サイドバーの「利用者の招待」は管理者にだけ示す（「管理」200・「DSL」210 の次）。隠すのは表示の切り替えにすぎず、
// 判定はサーバー側で行う（D14）。画面は遅延読み込み（DSL と同じ lazy）で、入口の JavaScript に入れない。
import { lazy } from 'react'
import type { FeatureRegistration } from '../../app/registry/types'
import { invitationMessages } from './messages'

const InvitationAdminPage = lazy(() =>
  import('./InvitationAdminPage').then((module) => ({ default: module.InvitationAdminPage })),
)

/** 招待の管理の画面の URL */
export const INVITATION_ADMIN_PATH = '/admin/invitations'

/** 招待の管理の画面の機能の登録 */
export const registration: FeatureRegistration = {
  featureId: 'invitation',
  routes: [
    {
      path: INVITATION_ADMIN_PATH,
      screen: InvitationAdminPage,
      layout: 'SHELL',
      access: 'ADMIN',
    },
  ],
  sidebarItems: [
    {
      id: 'invitation',
      labelKey: 'invitation.nav.label',
      path: INVITATION_ADMIN_PATH,
      order: 220,
      visibleWhen: 'ADMIN',
    },
  ],
  messages: invitationMessages,
}
