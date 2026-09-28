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
// 新しいパスワードとその確かめの画面の側の確かめ（U6 の functional-spec.md の 6節、U2 の BR4.1・U3 の BR7.2）。
// 誤りの種類だけを返し、文言は機能ごとに持つ。判定はサーバーが正。U6 の登録の完了と U7 のパスワードの変更が使う。
import { countCodePoints, utf8ByteLength } from './codePoints'
import { PASSWORD_MAX_UTF8_BYTES, PASSWORD_MIN_CODE_POINTS } from './limits'

/** 新しいパスワードの誤りの種類 */
export type NewPasswordProblem = 'required' | 'tooShort' | 'tooLong'

/** パスワードの確かめの誤りの種類 */
export type PasswordConfirmationProblem = 'required' | 'mismatch'

/**
 * 新しいパスワードを確かめる。空は required、12 コードポイント未満は tooShort、UTF-8 で 72 バイトを超えれば tooLong。
 * 12 コードポイント未満の値は多くても 44 バイトのため、tooShort と tooLong は重ならない。誤りが無ければ undefined。
 */
export function validateNewPassword(password: string): NewPasswordProblem | undefined {
  if (password.length === 0) {
    return 'required'
  }
  if (countCodePoints(password) < PASSWORD_MIN_CODE_POINTS) {
    return 'tooShort'
  }
  if (utf8ByteLength(password) > PASSWORD_MAX_UTF8_BYTES) {
    return 'tooLong'
  }
  return undefined
}

/**
 * パスワードの確かめを確かめる。確かめが空は required、文字の並びとして完全に一致しなければ mismatch
 * （正規化・前後の空白の除去をしない）。誤りが無ければ undefined。
 */
export function validatePasswordConfirmation(
  password: string,
  confirmation: string,
): PasswordConfirmationProblem | undefined {
  if (confirmation.length === 0) {
    return 'required'
  }
  if (confirmation !== password) {
    return 'mismatch'
  }
  return undefined
}
