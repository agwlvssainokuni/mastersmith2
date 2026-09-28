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
// プリファレンスとパスワードの変更の画面の文言（functional-spec.md の 7節、NFR8.1、CR1.1・CR1.4）。鍵は `preferences.` で始め、
// ja・en を対で置く。言語の選択肢は U4 の LANGUAGE_NAMES、テーマ・文字の大きさの選択肢は骨組みの
// `display.theme.*`・`display.fontSize.*` を使い、ここには持たない（D14）。文言に氏名・パスワードなどの値を含めない。
import type { FeatureMessages } from '../../app/registry/types'

const ja: Record<string, string> = {
  'preferences.menu.preferences': 'プリファレンス',
  'preferences.menu.password': 'パスワードの変更',
  'preferences.page.title': 'プリファレンス',
  'preferences.loading': '読み込んでいます',
  'preferences.load.failed': 'プリファレンスを読み込めませんでした。',
  'preferences.load.retry': 'もう一度読み込む',
  'preferences.displayName': '氏名',
  'preferences.language': '言語',
  'preferences.language.hint': '保存すると切り替わります',
  'preferences.theme': 'テーマ',
  'preferences.fontSize': '文字の大きさ',
  'preferences.appearance.hint': 'テーマと文字の大きさは選ぶと画面に反映されます',
  'preferences.reset': '元に戻す',
  'preferences.save': '保存する',
  'preferences.saving': '保存しています',
  'preferences.saved': '保存しました',
  'preferences.save.failed': '保存できませんでした。しばらくしてから、もう一度お試しください。',
  'preferences.displayName.required': '氏名を入力してください',
  'preferences.displayName.tooLong': '氏名は 254 文字以内で入力してください',
  'preferences.displayName.invalidCharacter': '氏名に改行・タブや見えない文字は使えません',
  'preferences.field.invalid': '入力を確かめてください',
  'preferences.choice.invalid': '選択を確かめてください',
  'preferences.form.invalid': '入力を確かめてください。',
  'preferences.alert.dismiss': '閉じる',
  'preferences.password.title': 'パスワードの変更',
  'preferences.password.current': '今のパスワード',
  'preferences.password.new': '新しいパスワード',
  'preferences.password.confirm': '新しいパスワード（確かめ）',
  'preferences.password.hint': '12 文字以上',
  'preferences.password.submit': '変更する',
  'preferences.password.submitting': '変更しています',
  'preferences.password.changed': 'パスワードを変更しました',
  'preferences.password.failed':
    'パスワードを変更できませんでした。しばらくしてから、もう一度お試しください。',
  'preferences.password.currentRequired': '今のパスワードを入力してください',
  'preferences.password.currentMismatch': '今のパスワードが正しくありません',
  'preferences.password.newRequired': '新しいパスワードを入力してください',
  'preferences.password.tooShort': '12 文字以上で入力してください',
  'preferences.password.tooLong': '長すぎます（半角で 72 文字、全角でおよそ 24 文字まで）',
  'preferences.password.confirmRequired':
    '確かめのため、新しいパスワードをもう一度入力してください',
  'preferences.password.mismatch': '新しいパスワードと同じ値を入力してください',
}

const en: Record<string, string> = {
  'preferences.menu.preferences': 'Preferences',
  'preferences.menu.password': 'Change password',
  'preferences.page.title': 'Preferences',
  'preferences.loading': 'Loading',
  'preferences.load.failed': 'The preferences could not be loaded.',
  'preferences.load.retry': 'Load again',
  'preferences.displayName': 'Name',
  'preferences.language': 'Language',
  'preferences.language.hint': 'Changes when you save',
  'preferences.theme': 'Theme',
  'preferences.fontSize': 'Text size',
  'preferences.appearance.hint': 'The theme and text size are shown as soon as you choose them',
  'preferences.reset': 'Revert',
  'preferences.save': 'Save',
  'preferences.saving': 'Saving',
  'preferences.saved': 'Saved',
  'preferences.save.failed': 'The preferences could not be saved. Please try again later.',
  'preferences.displayName.required': 'Enter your name',
  'preferences.displayName.tooLong': 'Enter a name of 254 characters or fewer',
  'preferences.displayName.invalidCharacter':
    'The name cannot contain line breaks, tabs, or invisible characters',
  'preferences.field.invalid': 'Check this entry',
  'preferences.choice.invalid': 'Check this choice',
  'preferences.form.invalid': 'Check your entries.',
  'preferences.alert.dismiss': 'Close',
  'preferences.password.title': 'Change password',
  'preferences.password.current': 'Current password',
  'preferences.password.new': 'New password',
  'preferences.password.confirm': 'New password (confirm)',
  'preferences.password.hint': 'At least 12 characters',
  'preferences.password.submit': 'Change',
  'preferences.password.submitting': 'Changing',
  'preferences.password.changed': 'Your password has been changed',
  'preferences.password.failed': 'The password could not be changed. Please try again later.',
  'preferences.password.currentRequired': 'Enter your current password',
  'preferences.password.currentMismatch': 'The current password is not correct',
  'preferences.password.newRequired': 'Enter a new password',
  'preferences.password.tooShort': 'Enter at least 12 characters',
  'preferences.password.tooLong':
    'Too long (up to 72 half-width characters, or about 24 full-width characters)',
  'preferences.password.confirmRequired': 'Enter the new password again to confirm it',
  'preferences.password.mismatch': 'Enter the same value as the new password',
}

/** プリファレンスとパスワードの変更の画面の文言 */
export const preferencesMessages: FeatureMessages = { ja, en }
