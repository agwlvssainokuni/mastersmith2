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
// 骨組み（U1）の画面の文言（日本語）。鍵は en.ts とそろえる（BR6.2）。
export const ja = {
  'app.name': 'MasterSmith',
  'login.heading': 'ログイン',
  'home.heading': 'ホーム',
  'home.description': 'MasterSmith へようこそ。左のメニューから機能を選んでください。',
  'notFound.heading': 'ページが見つかりません',
  'notFound.description': 'お探しのページは存在しないか、表示する権限がありません。',
  'notFound.homeLink': 'ホームへ',
  'nav.home': 'ホーム',
  'startupError.heading': '画面を起動できませんでした',
  'startupError.description': '機能の登録に問題があります。次の内容を確認してください。',
} as const satisfies Record<string, string>

/** 骨組みの文言の鍵 */
export type MessageKey = keyof typeof ja
