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
// DSL の管理画面を画面の骨組みに登録する（BR1.1・BR1.2・BR6.1、NFR9.1）。骨組み（src/app/）のファイルは書き換えない。
// サイドバーの「DSL」は管理者にだけ示す（「管理」の 200 の次）。隠すのは表示の切り替えにすぎず、判定はサーバー側で行う。
import { lazy } from 'react'
import type { FeatureRegistration } from '../../app/registry/types'
import { dslMessages } from './messages'

const DslAdminPage = lazy(() =>
  import('./DslAdminPage').then((module) => ({ default: module.DslAdminPage })),
)

/** DSL の管理画面の URL */
export const DSL_ADMIN_PATH = '/admin/dsl'

/** DSL の管理画面の機能の登録 */
export const registration: FeatureRegistration = {
  featureId: 'dsl',
  routes: [
    {
      path: DSL_ADMIN_PATH,
      screen: DslAdminPage,
      layout: 'SHELL',
      access: 'ADMIN',
    },
  ],
  sidebarItems: [
    {
      id: 'dsl',
      labelKey: 'dsl.nav.label',
      path: DSL_ADMIN_PATH,
      order: 210,
      visibleWhen: 'ADMIN',
    },
  ],
  messages: dslMessages,
}
