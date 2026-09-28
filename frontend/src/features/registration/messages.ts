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
// 登録の完了の画面の文言（functional-spec.md の 7節、NFR8.1、CR1.4）。鍵は `registration.` で始め、ja・en を対で置く。
// 文言にトークン・メールアドレス・有効期限の長さを含めない。アプリ名は骨組みの `app.name`、言語の選択肢は U4 の
// LANGUAGE_NAMES、テーマ・文字の大きさの選択肢は骨組みの `display.theme.*`・`display.fontSize.*` を使い、ここには持たない。
import type { FeatureMessages } from '../../app/registry/types'

const ja: Record<string, string> = {
  'registration.heading': '登録を完了する',
  'registration.verifying': 'リンクを確かめています',
  'registration.loadFailed': '読み込めませんでした。しばらくしてから、もう一度お試しください。',
  'registration.reload': 'もう一度読み込む',
  'registration.unavailable':
    'このリンクは使えません。いちばん新しい招待メールのリンクを使うか、招待した管理者に招待の送り直しを依頼してください。',
  'registration.toLogin': 'ログインの画面へ',
  'registration.loggedIn.message':
    'ログインしたままです。登録を続けるには、ログアウトしてください。',
  'registration.loggedIn.logout': 'ログアウトして続ける',
  'registration.loggedIn.loggingOut': 'ログアウトしています',
  'registration.loggedIn.home': 'ホームへ戻る',
  'registration.email.label': 'メールアドレス（ログインに使います）',
  'registration.displayName.label': '氏名',
  'registration.displayName.hint': 'そのままでも登録できます',
  'registration.displayName.required': '氏名を入力してください',
  'registration.displayName.tooLong': '氏名は 254 文字以内で入力してください',
  'registration.displayName.invalidCharacter':
    '氏名に使えない文字（改行・タブ・見えない文字など）が含まれています',
  'registration.password.label': 'パスワード',
  'registration.password.hint': '12 文字以上',
  'registration.password.required': 'パスワードを入力してください',
  'registration.password.tooShort': 'パスワードは 12 文字以上で入力してください',
  'registration.password.tooLong': '長すぎます（半角で 72 文字、全角でおよそ 24 文字まで）',
  'registration.passwordConfirmation.label': 'パスワード（確かめ）',
  'registration.passwordConfirmation.required':
    '確かめのため、同じパスワードをもう一度入力してください',
  'registration.passwordConfirmation.mismatch': 'パスワードが一致しません',
  'registration.display.heading': '表示の設定',
  'registration.language.legend': '言語',
  'registration.language.hint': '選ぶとこの画面の言語が切り替わります',
  'registration.theme.legend': 'テーマ',
  'registration.fontSize.legend': '文字の大きさ',
  'registration.appearance.hint': 'テーマと文字の大きさは選ぶと画面に反映されます',
  'registration.submit': '登録を完了する',
  'registration.submitting': '登録しています',
  'registration.validationFailed':
    '入力を確かめてください。直しても登録できないときは、招待した管理者に連絡してください。',
  'registration.submitFailed': '登録できませんでした。しばらくしてから、もう一度お試しください。',
}

const en: Record<string, string> = {
  'registration.heading': 'Complete your registration',
  'registration.verifying': 'Checking your link',
  'registration.loadFailed': 'The page could not be loaded. Please try again later.',
  'registration.reload': 'Reload',
  'registration.unavailable':
    'This link cannot be used. Use the link in the most recent invitation email, or ask the administrator who invited you to send the invitation again.',
  'registration.toLogin': 'Go to the login page',
  'registration.loggedIn.message': 'You are logged in. To continue the registration, log out.',
  'registration.loggedIn.logout': 'Log out and continue',
  'registration.loggedIn.loggingOut': 'Logging out',
  'registration.loggedIn.home': 'Back to home',
  'registration.email.label': 'Email address (used to log in)',
  'registration.displayName.label': 'Name',
  'registration.displayName.hint': 'You can register it as it is',
  'registration.displayName.required': 'Enter your name',
  'registration.displayName.tooLong': 'Enter a name of up to 254 characters',
  'registration.displayName.invalidCharacter':
    'The name contains characters that cannot be used (line breaks, tabs, invisible characters, and so on)',
  'registration.password.label': 'Password',
  'registration.password.hint': 'At least 12 characters',
  'registration.password.required': 'Enter a password',
  'registration.password.tooShort': 'Enter a password of at least 12 characters',
  'registration.password.tooLong':
    'Too long (up to 72 single-byte characters, or about 24 full-width characters)',
  'registration.passwordConfirmation.label': 'Password (confirmation)',
  'registration.passwordConfirmation.required': 'Enter the same password again to confirm it',
  'registration.passwordConfirmation.mismatch': 'The passwords do not match',
  'registration.display.heading': 'Display settings',
  'registration.language.legend': 'Language',
  'registration.language.hint': 'Selecting a language changes the language of this page',
  'registration.theme.legend': 'Theme',
  'registration.fontSize.legend': 'Text size',
  'registration.appearance.hint':
    'The theme and text size are applied to this page when you select them',
  'registration.submit': 'Complete registration',
  'registration.submitting': 'Registering',
  'registration.validationFailed':
    'Check your input. If you still cannot register after correcting it, contact the administrator who invited you.',
  'registration.submitFailed': 'The registration failed. Please try again later.',
}

/** 登録の完了の画面の文言 */
export const registrationMessages: FeatureMessages = { ja, en }
