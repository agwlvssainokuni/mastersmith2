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
// 招待の管理の画面の文言（functional-spec.md の 7節、NFR8.1、CR1.4）。鍵は `invitation.` で始め、ja・en を対で置く。
// サーバーの code ごとは `invitation.error.<code>`、一般の文言は `invitation.errorGeneral.*`（DSL の管理画面と同じ文を
// 複写する。機能どうしで読み込まないため）。有効期限の長さは画面の文言に書かない（U3 がメールに差し込む）。
// 言語の選択肢の名前は U4 の LANGUAGE_NAMES を使い、ここには持たない。
import type { FeatureMessages } from '../../app/registry/types'

const ja: Record<string, string> = {
  'invitation.nav.label': '利用者の招待',
  'invitation.title': '利用者の招待',
  'invitation.action.invite': '招待する',
  'invitation.action.sending': '送信しています',
  'invitation.action.resend': '送り直す',
  'invitation.action.revoke': '取り消す',
  'invitation.action.revoking': '取り消しています',
  'invitation.action.cancel': 'やめる',
  'invitation.action.close': '閉じる',
  'invitation.action.retry': 'もう一度読み込む',
  'invitation.action.showRow': '一覧でこの招待を見る',
  'invitation.action.prev': '前へ',
  'invitation.action.next': '次へ',
  'invitation.action.dismissAlert': '知らせを閉じる',
  'invitation.list.caption': '招待中の人',
  'invitation.list.loading': '招待中の人を読み込んでいます',
  'invitation.list.empty': '招待中の人はいません。『招待する』から招待できます。',
  'invitation.list.failed': '招待中の人を読み込めませんでした。',
  'invitation.column.email': 'メールアドレス',
  'invitation.column.language': '言語',
  'invitation.column.invitedBy': '招待した管理者',
  'invitation.column.invitedAt': '招待した日時',
  'invitation.column.expiresAt': '有効期限',
  'invitation.column.sendResult': '送信の結果',
  'invitation.column.status': '状態',
  'invitation.column.actions': '操作',
  'invitation.sendResult.SENT': '送信済み',
  'invitation.sendResult.FAILED': '送信に失敗',
  'invitation.status.valid': '期限内',
  'invitation.status.expired': '期限切れ',
  'invitation.invitedBy.unknown': '（不明）',
  'invitation.row.resendName': '{{email}} への招待を送り直す',
  'invitation.row.resendingName': '{{email}} への招待を送信しています',
  'invitation.row.revokeName': '{{email}} への招待を取り消す',
  'invitation.pager.range': '{{from}}〜{{to}} 件目 / 全 {{total}} 件',
  'invitation.pager.status':
    '{{from}}〜{{to}} 件目 / 全 {{total}} 件（{{page}} / {{pages}} ページ）',
  'invitation.table.emptyStatus': '0 件',
  'invitation.table.selectAllRows': 'すべての行を選ぶ',
  'invitation.table.selectRow': 'この行を選ぶ',
  'invitation.table.toggleRowDetail': '詳細',
  'invitation.unavailable.title': '招待を使えません',
  'invitation.unavailable.SMTP_NOT_CONFIGURED':
    '招待を使えません: メールの送り先が設定されていません。運用者に設定を依頼してください。',
  'invitation.unavailable.BASE_URL_NOT_CONFIGURED':
    '招待を使えません: 招待のリンクに使うアプリの URL が設定されていません。運用者に設定を依頼してください。',
  'invitation.unavailable.unknown':
    '必要な設定が足りません。運用者に設定を確かめてもらってください。',
  'invitation.unavailableReason.SMTP_NOT_CONFIGURED':
    'メールの送り先が設定されていません。運用者に設定を依頼してください。',
  'invitation.unavailableReason.BASE_URL_NOT_CONFIGURED':
    '招待のリンクに使うアプリの URL が設定されていません。運用者に設定を依頼してください。',
  'invitation.invite.title': '利用者を招待する',
  'invitation.invite.email': 'メールアドレス（必須）',
  'invitation.invite.language': '招待メールの言語',
  'invitation.invite.languageHint': '初期値は自分の言語',
  'invitation.invite.required': 'メールアドレスを入れてください。',
  'invitation.error.VALIDATION_FAILED': 'メールアドレスの形式が正しくありません。',
  'invitation.error.INVITATION_EMAIL_REGISTERED':
    'このメールアドレスの利用者はすでに登録されています。',
  'invitation.error.INVITATION_ALREADY_PENDING': 'すでに招待中です。一覧から送り直してください。',
  'invitation.error.INVITATION_NOT_FOUND':
    'この招待は見つかりません。ほかの管理者が取り消したか、登録が完了した可能性があります。',
  'invitation.result.sendFailed':
    '招待は作りましたが、メールを送れませんでした。一覧から送り直せます。',
  'invitation.result.resendFailed':
    '招待を送り直しましたが、メールを送れませんでした。もう一度送り直せます。',
  'invitation.toast.invited': '招待を送りました',
  'invitation.toast.resent': '招待を送り直しました',
  'invitation.toast.revoked': '招待を取り消しました',
  'invitation.revoke.title': '招待を取り消しますか',
  'invitation.revoke.body':
    '{{email}} への招待を取り消します。取り消すと、招待メールのリンクは使えなくなります。',
  'invitation.errorGeneral.client':
    '操作を受け付けられませんでした。画面を読み込み直してからやり直してください',
  'invitation.errorGeneral.server':
    'サーバーで問題が起きました。しばらくしてからやり直してください',
  'invitation.errorGeneral.network':
    'サーバーにつながりませんでした。通信の状態を確かめてからやり直してください',
}

const en: Record<string, string> = {
  'invitation.nav.label': 'User invitations',
  'invitation.title': 'User invitations',
  'invitation.action.invite': 'Invite',
  'invitation.action.sending': 'Sending',
  'invitation.action.resend': 'Resend',
  'invitation.action.revoke': 'Revoke',
  'invitation.action.revoking': 'Revoking',
  'invitation.action.cancel': 'Cancel',
  'invitation.action.close': 'Close',
  'invitation.action.retry': 'Load again',
  'invitation.action.showRow': 'Show this invitation in the list',
  'invitation.action.prev': 'Previous',
  'invitation.action.next': 'Next',
  'invitation.action.dismissAlert': 'Dismiss the message',
  'invitation.list.caption': 'Pending invitations',
  'invitation.list.loading': 'Loading pending invitations',
  'invitation.list.empty': 'There are no pending invitations. Use "Invite" to invite someone.',
  'invitation.list.failed': 'Could not load the pending invitations.',
  'invitation.column.email': 'Email address',
  'invitation.column.language': 'Language',
  'invitation.column.invitedBy': 'Invited by',
  'invitation.column.invitedAt': 'Invited at',
  'invitation.column.expiresAt': 'Expires at',
  'invitation.column.sendResult': 'Delivery',
  'invitation.column.status': 'Status',
  'invitation.column.actions': 'Actions',
  'invitation.sendResult.SENT': 'Sent',
  'invitation.sendResult.FAILED': 'Failed to send',
  'invitation.status.valid': 'Valid',
  'invitation.status.expired': 'Expired',
  'invitation.invitedBy.unknown': '(Unknown)',
  'invitation.row.resendName': 'Resend the invitation to {{email}}',
  'invitation.row.resendingName': 'Sending the invitation to {{email}}',
  'invitation.row.revokeName': 'Revoke the invitation to {{email}}',
  'invitation.pager.range': '{{from}}–{{to}} of {{total}}',
  'invitation.pager.status': '{{from}}–{{to}} of {{total}} (page {{page}} of {{pages}})',
  'invitation.table.emptyStatus': '0 items',
  'invitation.table.selectAllRows': 'Select all rows',
  'invitation.table.selectRow': 'Select this row',
  'invitation.table.toggleRowDetail': 'Details',
  'invitation.unavailable.title': 'Invitations are unavailable',
  'invitation.unavailable.SMTP_NOT_CONFIGURED':
    'Invitations are unavailable: the mail server is not configured. Ask the operator to configure it.',
  'invitation.unavailable.BASE_URL_NOT_CONFIGURED':
    'Invitations are unavailable: the application URL for invitation links is not configured. Ask the operator to configure it.',
  'invitation.unavailable.unknown':
    'A required setting is missing. Ask the operator to check the configuration.',
  'invitation.unavailableReason.SMTP_NOT_CONFIGURED':
    'The mail server is not configured. Ask the operator to configure it.',
  'invitation.unavailableReason.BASE_URL_NOT_CONFIGURED':
    'The application URL for invitation links is not configured. Ask the operator to configure it.',
  'invitation.invite.title': 'Invite a user',
  'invitation.invite.email': 'Email address (required)',
  'invitation.invite.language': 'Language of the invitation email',
  'invitation.invite.languageHint': 'Defaults to your language',
  'invitation.invite.required': 'Enter an email address.',
  'invitation.error.VALIDATION_FAILED': 'The email address is not in a valid format.',
  'invitation.error.INVITATION_EMAIL_REGISTERED':
    'A user with this email address is already registered.',
  'invitation.error.INVITATION_ALREADY_PENDING':
    'This address already has a pending invitation. Resend it from the list.',
  'invitation.error.INVITATION_NOT_FOUND':
    'This invitation was not found. Another administrator may have revoked it, or registration may have been completed.',
  'invitation.result.sendFailed':
    'The invitation was created, but the email could not be sent. You can resend it from the list.',
  'invitation.result.resendFailed':
    'The invitation was renewed, but the email could not be sent. You can resend it again.',
  'invitation.toast.invited': 'Invitation sent',
  'invitation.toast.resent': 'Invitation resent',
  'invitation.toast.revoked': 'Invitation revoked',
  'invitation.revoke.title': 'Revoke this invitation?',
  'invitation.revoke.body':
    'The invitation to {{email}} will be revoked. The link in the invitation email will no longer work.',
  'invitation.errorGeneral.client':
    'The operation was not accepted. Reload the screen and try again',
  'invitation.errorGeneral.server': 'A problem occurred on the server. Try again later',
  'invitation.errorGeneral.network':
    'The server could not be reached. Check your connection and try again',
}

/** 招待の管理の画面の文言 */
export const invitationMessages: FeatureMessages = { ja, en }
