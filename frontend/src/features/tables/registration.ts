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
// テーブルの置き場の画面（S2）を骨組みに登録する（Intent 261004-role-menu の U7 の D1・D20、L9）。道は `/tables`
// （引数は問い合わせの schema・table・item）。ログインだけで開ける画面で、見てよいかはサーバーの置き場の問い合わせが
// 判定する（NFR1.1）。サイドバーの項目は登録しない（業務のメニューは骨組みが API から作る）。
// 画面は遅延読み込み（lazy）で、入口の JavaScript に入れない（計画 11節）。このファイルは文言だけを静的に読む。
import { lazy } from 'react'
import { TABLE_PLACEHOLDER_PATH } from '../../app/navigation/tablePlaceholderPath'
import type { FeatureRegistration } from '../../app/registry/types'
import { tablesMessages } from './messages'

const TablePlaceholderPage = lazy(() =>
  import('./TablePlaceholderPage').then((module) => ({ default: module.TablePlaceholderPage })),
)

/** テーブルの置き場の画面の機能の登録 */
export const registration: FeatureRegistration = {
  featureId: 'tables',
  routes: [
    {
      path: TABLE_PLACEHOLDER_PATH,
      screen: TablePlaceholderPage,
      layout: 'SHELL',
      access: 'LOGGED_IN',
    },
  ],
  messages: tablesMessages,
}
