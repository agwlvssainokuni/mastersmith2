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
// 利用者の管理の画面の文言（functional-spec.md の 7節、W11、NFR8.1・NFR8.2）。鍵は `useradmin.` で始め、ja・en を対で置く。
// make-you-chic-ui の Table の labels・Modal の closeLabel・Alert の dismissLabel・読み上げの名前の文言もここに持ち、
// en の画面に部品の日本語の既定の文言を出さない。差し込む値（氏名・メールアドレス・検索の文字・時刻）は React の文字として
// 描き、HTML として解釈しない（NFR3.2）。en の見出しと効き目の文は ja と同じ中身にする。
// 言語の選択肢の名前は U4 の LANGUAGE_NAMES を使い、ここには持たない。
import type { FeatureMessages } from '../../app/registry/types'

const ja: Record<string, string> = {
  'useradmin.nav.label': '利用者の管理',
  'useradmin.title': '利用者の管理',
  'useradmin.list.heading': '利用者の一覧',
  'useradmin.list.loading': '利用者を読み込んでいます…',
  'useradmin.list.reloading': '読み込んでいます…',
  'useradmin.list.loadFailed':
    '利用者の一覧を読み込めませんでした。時間をおいて、もう一度読み込んでください。',
  'useradmin.list.emptyPage': 'このページに利用者はいません。',
  'useradmin.list.range': '{{from}}〜{{to}} 件目 / 全 {{total}} 件',
  'useradmin.column.email': 'メールアドレス',
  'useradmin.column.name': '氏名',
  'useradmin.column.admin': '管理者',
  'useradmin.column.status': '状態',
  'useradmin.column.lock': 'ロック',
  'useradmin.column.registered': '登録した日時',
  'useradmin.column.actions': '操作',
  'useradmin.badge.admin': '管理者',
  'useradmin.badge.active': '有効',
  'useradmin.badge.suspended': '利用停止',
  'useradmin.badge.locked': 'ロック中',
  'useradmin.badge.you': 'あなた',
  'useradmin.none': 'なし',
  'useradmin.lock.until': '{{time}} まで',
  'useradmin.pager.status': '{{page}} / {{pages}}ページ（全{{total}}件）',
  'useradmin.pager.prev': '前へ',
  'useradmin.pager.next': '次へ',
  'useradmin.table.emptyStatus': '0 件',
  'useradmin.table.selectAllRows': 'すべての行を選ぶ',
  'useradmin.table.selectRow': 'この行を選ぶ',
  'useradmin.table.toggleRowDetail': '詳細',
  'useradmin.action.retry': 'もう一度読み込む',
  'useradmin.action.dismiss': '知らせを閉じる',
  'useradmin.action.cancel': 'やめる',
  'useradmin.action.close': '閉じる',
  'useradmin.action.processing': '処理中',
  'useradmin.search.label': '検索',
  'useradmin.search.description': 'メールアドレスまたは氏名の一部。前後の空白を除いて 254 文字まで',
  'useradmin.search.submit': '検索',
  'useradmin.search.clear': '検索を消す',
  'useradmin.search.tooLong': '検索の文字は 254 文字までにしてください。',
  'useradmin.search.invalid': '検索の文字が正しくありません。',
  'useradmin.search.empty':
    '「{{text}}」に当たる利用者はいません。検索の文字を変えるか、「検索を消す」で全体に戻してください。',
  'useradmin.actions.button': '操作',
  'useradmin.actions.name': '{{name}}（{{email}}）の操作',
  'useradmin.actions.busyName': '{{name}}（{{email}}）の操作（処理中）',
  'useradmin.menu.grantAdmin': '管理者の印を付ける',
  'useradmin.menu.revokeAdmin': '管理者の印を外す',
  'useradmin.menu.suspend': '利用を止める',
  'useradmin.menu.resume': '停止を解く',
  'useradmin.menu.resetFailures': 'ロックを解除（失敗回数を戻す）',
  'useradmin.menu.editProfile': '氏名・言語を直す',
  'useradmin.disabled.selfRevoke': '自分自身の印は外せません',
  'useradmin.disabled.selfSuspend': '自分自身は止められません',
  'useradmin.dialog.target': '対象: {{name}}（{{email}}）',
  'useradmin.dialog.slow': '時間がかかっています。そのままお待ちください。',
  'useradmin.confirm.grantAdmin.title': '管理者の印を付けますか？',
  'useradmin.confirm.grantAdmin.body':
    '{{name}}さんは、次の操作から管理の画面を使えるようになります。',
  'useradmin.confirm.grantAdmin.submit': '管理者の印を付ける',
  'useradmin.confirm.revokeAdmin.title': '管理者の印を外しますか？',
  'useradmin.confirm.revokeAdmin.body':
    '{{name}}さんは、次の操作から管理の画面を使えなくなります。ログインは続きます。',
  'useradmin.confirm.revokeAdmin.submit': '管理者の印を外す',
  'useradmin.confirm.suspend.title': '利用を止めますか？',
  'useradmin.confirm.suspend.body':
    '{{name}}さんはすぐにこのアプリを使えなくなります。停止を解いた後も、ログインし直す必要があります。',
  'useradmin.confirm.suspend.submit': '利用を止める',
  'useradmin.confirm.resume.title': '停止を解きますか？',
  'useradmin.confirm.resume.body':
    '{{name}}さんは、新しくログインすればこのアプリを使えるようになります。',
  'useradmin.confirm.resume.submit': '停止を解く',
  'useradmin.confirm.resetFailures.title': 'ロックを解除しますか？',
  'useradmin.confirm.resetFailures.body':
    '{{name}}さんのログインの失敗回数を 0 に戻します。すぐに正しいパスワードでログインできます。',
  'useradmin.confirm.resetFailures.submit': 'ロックを解除',
  'useradmin.toast.grantAdmin': '{{name}}さんに管理者の印を付けました',
  'useradmin.toast.revokeAdmin': '{{name}}さんの管理者の印を外しました',
  'useradmin.toast.suspend': '{{name}}さんの利用を止めました',
  'useradmin.toast.resume': '{{name}}さんの停止を解きました',
  'useradmin.toast.resetFailures': '{{name}}さんのロックを解除しました',
  'useradmin.toast.editProfile': '{{name}}さんの氏名と言語を直しました',
  'useradmin.error.USER_NOT_FOUND': '対象の利用者が見つかりません。一覧を読み直しました。',
  'useradmin.error.USER_ADMIN_SELF_OPERATION':
    '自分自身にはこの操作をできません。ほかの管理者に頼んでください。',
  'useradmin.error.USER_ADMIN_TARGET_SUSPENDED':
    '{{name}}さんは利用停止中です。先に停止を解いてください。',
  'useradmin.error.USER_ADMIN_NO_CHANGE':
    '{{name}}さんはすでにこの状態です。ほかの管理者がすでに変えた可能性があります。一覧を読み直しました。',
  'useradmin.error.USER_ADMIN_LAST_ADMIN':
    'この操作をすると、管理者が1人もいなくなるため受け付けられません。先にほかの利用者に管理者の印を付けてください。',
  'useradmin.error.USER_ADMIN_BUSY':
    'ほかの処理と重なったため、操作できませんでした。少し待ってから、もう一度操作してください。',
  'useradmin.error.general': '操作を完了できませんでした。一覧を読み直して状態を確かめてください。',
  'useradmin.edit.title': '氏名と言語を直す',
  'useradmin.edit.name': '氏名（必須）',
  'useradmin.edit.language': '言語',
  'useradmin.edit.save': '保存',
  'useradmin.edit.nameRequired': '氏名を入れてください。',
  'useradmin.edit.nameTooLong': '氏名は 254 文字までにしてください。',
  'useradmin.edit.nameInvalidCharacter': '氏名に使えない文字が含まれています。',
  'useradmin.edit.languageInvalid': '言語を選んでください。',
  'useradmin.edit.formInvalid': '入力の内容を確かめてください。',
  'useradmin.edit.notFound': '対象の利用者が見つかりません。一覧を読み直してください。',
  'useradmin.edit.failed': '保存できませんでした。時間をおいて、もう一度保存してください。',
}

const en: Record<string, string> = {
  'useradmin.nav.label': 'Users',
  'useradmin.title': 'Users',
  'useradmin.list.heading': 'User list',
  'useradmin.list.loading': 'Loading users…',
  'useradmin.list.reloading': 'Loading…',
  'useradmin.list.loadFailed': 'Could not load the user list. Please try again later.',
  'useradmin.list.emptyPage': 'There are no users on this page.',
  'useradmin.list.range': 'Users {{from}}–{{to}} of {{total}}',
  'useradmin.column.email': 'Email',
  'useradmin.column.name': 'Name',
  'useradmin.column.admin': 'Admin',
  'useradmin.column.status': 'Status',
  'useradmin.column.lock': 'Lock',
  'useradmin.column.registered': 'Registered',
  'useradmin.column.actions': 'Actions',
  'useradmin.badge.admin': 'Admin',
  'useradmin.badge.active': 'Active',
  'useradmin.badge.suspended': 'Suspended',
  'useradmin.badge.locked': 'Locked',
  'useradmin.badge.you': 'You',
  'useradmin.none': 'None',
  'useradmin.lock.until': 'Until {{time}}',
  'useradmin.pager.status': 'Page {{page}} of {{pages}} ({{total}} total)',
  'useradmin.pager.prev': 'Previous',
  'useradmin.pager.next': 'Next',
  'useradmin.table.emptyStatus': '0 users',
  'useradmin.table.selectAllRows': 'Select all rows',
  'useradmin.table.selectRow': 'Select this row',
  'useradmin.table.toggleRowDetail': 'Details',
  'useradmin.action.retry': 'Reload',
  'useradmin.action.dismiss': 'Dismiss',
  'useradmin.action.cancel': 'Cancel',
  'useradmin.action.close': 'Close',
  'useradmin.action.processing': 'Processing…',
  'useradmin.search.label': 'Search',
  'useradmin.search.description':
    'Part of an email address or name. Up to 254 characters, excluding leading and trailing spaces',
  'useradmin.search.submit': 'Search',
  'useradmin.search.clear': 'Clear search',
  'useradmin.search.tooLong': 'Enter up to 254 characters to search.',
  'useradmin.search.invalid': 'The search text is not valid.',
  'useradmin.search.empty':
    'No users match "{{text}}". Change the search text, or choose "Clear search" to see all users.',
  'useradmin.actions.button': 'Actions',
  'useradmin.actions.name': 'Actions for {{name}} ({{email}})',
  'useradmin.actions.busyName': 'Actions for {{name}} ({{email}}) (processing)',
  'useradmin.menu.grantAdmin': 'Grant admin',
  'useradmin.menu.revokeAdmin': 'Revoke admin',
  'useradmin.menu.suspend': 'Suspend',
  'useradmin.menu.resume': 'Resume',
  'useradmin.menu.resetFailures': 'Unlock (reset failed attempts)',
  'useradmin.menu.editProfile': 'Edit name and language',
  'useradmin.disabled.selfRevoke': 'You cannot revoke your own admin role',
  'useradmin.disabled.selfSuspend': 'You cannot suspend yourself',
  'useradmin.dialog.target': 'Target: {{name}} ({{email}})',
  'useradmin.dialog.slow': 'This is taking longer than usual. Please wait.',
  'useradmin.confirm.grantAdmin.title': 'Grant the admin role?',
  'useradmin.confirm.grantAdmin.body':
    '{{name}} will be able to use the administration screens from their next action.',
  'useradmin.confirm.grantAdmin.submit': 'Grant admin',
  'useradmin.confirm.revokeAdmin.title': 'Revoke the admin role?',
  'useradmin.confirm.revokeAdmin.body':
    '{{name}} will no longer be able to use the administration screens from their next action. They stay signed in.',
  'useradmin.confirm.revokeAdmin.submit': 'Revoke admin',
  'useradmin.confirm.suspend.title': 'Suspend this user?',
  'useradmin.confirm.suspend.body':
    '{{name}} will no longer be able to use this app immediately. After the suspension is lifted, they will need to sign in again.',
  'useradmin.confirm.suspend.submit': 'Suspend',
  'useradmin.confirm.resume.title': 'Lift the suspension?',
  'useradmin.confirm.resume.body': '{{name}} will be able to use this app after signing in again.',
  'useradmin.confirm.resume.submit': 'Resume',
  'useradmin.confirm.resetFailures.title': 'Unlock this user?',
  'useradmin.confirm.resetFailures.body':
    'The failed sign-in count of {{name}} will be reset to 0. They can sign in with the correct password right away.',
  'useradmin.confirm.resetFailures.submit': 'Unlock',
  'useradmin.toast.grantAdmin': 'Granted the admin role to {{name}}',
  'useradmin.toast.revokeAdmin': 'Revoked the admin role from {{name}}',
  'useradmin.toast.suspend': 'Suspended {{name}}',
  'useradmin.toast.resume': 'Lifted the suspension of {{name}}',
  'useradmin.toast.resetFailures': 'Unlocked {{name}}',
  'useradmin.toast.editProfile': 'Updated the name and language of {{name}}',
  'useradmin.error.USER_NOT_FOUND': 'The user was not found. The list has been reloaded.',
  'useradmin.error.USER_ADMIN_SELF_OPERATION':
    'You cannot perform this action on yourself. Ask another administrator.',
  'useradmin.error.USER_ADMIN_TARGET_SUSPENDED':
    '{{name}} is suspended. Lift the suspension first.',
  'useradmin.error.USER_ADMIN_NO_CHANGE':
    '{{name}} is already in this state. Another administrator may have changed it. The list has been reloaded.',
  'useradmin.error.USER_ADMIN_LAST_ADMIN':
    'This action would leave no administrators, so it was not accepted. Grant the admin role to another user first.',
  'useradmin.error.USER_ADMIN_BUSY':
    'The action could not be performed because it overlapped with other processing. Wait a moment and try again.',
  'useradmin.error.general':
    'The action could not be completed. Reload the list and check the current state.',
  'useradmin.edit.title': 'Edit name and language',
  'useradmin.edit.name': 'Name (required)',
  'useradmin.edit.language': 'Language',
  'useradmin.edit.save': 'Save',
  'useradmin.edit.nameRequired': 'Enter a name.',
  'useradmin.edit.nameTooLong': 'Enter a name of up to 254 characters.',
  'useradmin.edit.nameInvalidCharacter': 'The name contains characters that cannot be used.',
  'useradmin.edit.languageInvalid': 'Choose a language.',
  'useradmin.edit.formInvalid': 'Check what you entered.',
  'useradmin.edit.notFound': 'The user was not found. Reload the list.',
  'useradmin.edit.failed': 'Could not save. Please try saving again later.',
}

/** 利用者の管理の画面の文言 */
export const userAdminMessages: FeatureMessages = { ja, en }
