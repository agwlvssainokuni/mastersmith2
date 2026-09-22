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
// ログインの入力の検査（BR8.2）。空の項目だけを見る（パスワードの長さは検査しない）。純粋な関数。

/** ログインの入力 */
export interface LoginInput {
  email: string
  password: string
}

/** 空の項目に対応する文言の鍵（空の対応表なら検査を通った） */
export interface LoginInputProblems {
  email?: string
  password?: string
}

/** メールアドレスとパスワードが空（空白だけを含む）でないことを確かめる。 */
export function validateLoginInput(input: LoginInput): LoginInputProblems {
  const problems: LoginInputProblems = {}
  if (input.email.trim() === '') {
    problems.email = 'auth.login.emailRequired'
  }
  if (input.password === '') {
    problems.password = 'auth.login.passwordRequired'
  }
  return problems
}

/** 検査を通ったかどうか。 */
export function isValidLoginInput(problems: LoginInputProblems): boolean {
  return problems.email === undefined && problems.password === undefined
}
