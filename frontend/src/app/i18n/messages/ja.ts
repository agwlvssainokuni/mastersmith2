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
  'adminForbidden.heading': '管理',
  'adminForbidden.message': 'この画面を使う権限がありません',
  'adminForbidden.homeLink': 'ホームへ戻る',
  'nav.home': 'ホーム',
  'nav.navigationLabel': 'メインメニュー',
  'nav.expandGroup': '{{label}}を開く',
  'nav.collapseGroup': '{{label}}を閉じる',
  'nav.containsCurrent': '{{label}}（今のページを含む）',
  'nav.section.business': '業務',
  'nav.section.admin': '管理',
  'nav.businessLoadError': '業務のメニューを読み込めませんでした',
  'nav.untitled': '（名前なし）',
  'topbar.expandSidebar': 'サイドバーを開く',
  'topbar.collapseSidebar': 'サイドバーを折り畳む',
  'topbar.userMenu': '{{initials}}: {{name}}のメニュー',
  'topbar.userMenuWithoutInitials': '{{name}}のメニュー',
  'workRole.prefix': '作業ロール:',
  'workRole.label': '作業ロール: {{name}}',
  'workRole.loading': '作業ロール: 読み込んでいます',
  'workRole.loadError': '作業ロール: 読み込めませんでした',
  'workRole.noneName': 'なし',
  'workRole.inUse': '{{name}}（使用中）',
  'workRole.singleItem': '切り替える先はありません',
  'workRole.singleHint': '割り当てられたロールは{{name}}だけのため、切り替える先はありません',
  'workRole.noRoleItem': '作業ロールを選べません',
  'workRole.noRoleHint': '割り当てられたロールがありません。管理者に依頼してください。',
  'workRole.myPermissions': '自分の権限を見る',
  'workRole.switched': '作業ロールを{{name}}に切り替えました',
  'workRole.switchFailed':
    '作業ロールを切り替えられませんでした。割り当てが変わった可能性があります。',
  'home.notConfigured': '業務のメニューが設定されていません。',
  'home.noRole': 'ロールが割り当てられていません。管理者に依頼してください。',
  'home.nothingVisible':
    '表示できる業務のメニューがありません。作業ロールに見てよいテーブルがありません。',
  'home.loadError': '業務のメニューを読み込めませんでした。',
  'home.retry': 'もう一度',
  'home.myPermissions': '自分の権限を見る',
  'startupError.heading': '画面を起動できませんでした',
  'startupError.description': '機能の登録に問題があります。次の内容を確認してください。',
  'display.theme.system': 'OS に合わせる',
  'display.theme.light': 'ライト',
  'display.theme.dark': 'ダーク',
  'display.fontSize.sm': '小',
  'display.fontSize.md': '標準',
  'display.fontSize.lg': '大',
} as const satisfies Record<string, string>

/** 骨組みの文言の鍵 */
export type MessageKey = keyof typeof ja
