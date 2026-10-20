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
// 業務データの画面を骨組みに登録する（Intent 261016-business-data-views の U3 の D1・D28、L7、U3-NFR1.10）。
// - 道は一覧 `/tables`・詳細 `/tables/row`・作成 `/tables/new`・更新 `/tables/edit`（作成と更新は同じ画面で、道で決める）。引数は問い合わせの
//   schema・table・item だけ。どれも「ログインだけ」で開ける画面で、見てよいかと許す操作はサーバーが判定する。
// - 画面はそれぞれ別の遅延読み込み（lazy）にし、入口の JavaScript に入れない。このファイルは文言だけを静的に読む。
// - サイドバーの項目は登録しない（業務のメニューは骨組みが API から作る）。
import { lazy } from 'react'
import { TABLE_PATHS } from '../../app/navigation/tablePlaceholderPath'
import type { FeatureRegistration } from '../../app/registry/types'
import { tablesMessages } from './messages'

const BusinessDataListPage = lazy(() =>
  import('./BusinessDataListPage').then((module) => ({ default: module.BusinessDataListPage })),
)

const BusinessDataDetailPage = lazy(() =>
  import('./BusinessDataDetailPage').then((module) => ({ default: module.BusinessDataDetailPage })),
)

const BusinessDataFormPage = lazy(() =>
  import('./BusinessDataFormPage').then((module) => ({ default: module.BusinessDataFormPage })),
)

/** 業務データの画面の機能の登録 */
export const registration: FeatureRegistration = {
  featureId: 'tables',
  routes: [
    { path: TABLE_PATHS.list, screen: BusinessDataListPage, layout: 'SHELL', access: 'LOGGED_IN' },
    { path: TABLE_PATHS.row, screen: BusinessDataDetailPage, layout: 'SHELL', access: 'LOGGED_IN' },
    { path: TABLE_PATHS.new, screen: BusinessDataFormPage, layout: 'SHELL', access: 'LOGGED_IN' },
    { path: TABLE_PATHS.edit, screen: BusinessDataFormPage, layout: 'SHELL', access: 'LOGGED_IN' },
  ],
  messages: tablesMessages,
}
