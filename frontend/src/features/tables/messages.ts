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
// テーブルの置き場の画面（S2）の文言（Intent 261004-role-menu の U7 の D21〜D23・D29、frontend-components.md の 6節・
// 9節）。鍵は `tables.` で始め、ja・en を対で置く。テーブルの表示名とロールの名前は訳さず、React の文字として描く。
import type { FeatureMessages } from '../../app/registry/types'

const ja: Record<string, string> = {
  'tables.loading': '読み込んでいます',
  'tables.preparing': 'この画面は準備中です。一覧と詳細の画面は、後の更新で追加されます。',
  'tables.forbidden': 'この画面を表示する権限がありません',
  'tables.currentWorkRole': '今の作業ロール: {{name}}',
  'tables.noWorkRole': 'なし',
  'tables.switchWorkRole': '作業ロールを切り替える',
  'tables.goHome': 'ホームへ',
  'tables.loadError': '読み込めませんでした。',
  'tables.retry': 'もう一度',
}

const en: Record<string, string> = {
  'tables.loading': 'Loading',
  'tables.preparing':
    'This screen is under preparation. List and detail screens will be added in a later update.',
  'tables.forbidden': 'You do not have permission to view this screen',
  'tables.currentWorkRole': 'Current work role: {{name}}',
  'tables.noWorkRole': 'none',
  'tables.switchWorkRole': 'Switch work role',
  'tables.goHome': 'Go to home',
  'tables.loadError': 'Could not load.',
  'tables.retry': 'Try again',
}

/** テーブルの置き場の画面の文言 */
export const tablesMessages: FeatureMessages = { ja, en }
