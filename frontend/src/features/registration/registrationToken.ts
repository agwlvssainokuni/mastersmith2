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
// 招待のリンクのフラグメントからトークンを取り出す純粋な関数（functional-spec.md の D1・W2、NFR1.1）。
// トークンの形（長さ・文字の種類）は確かめない（形の誤りもサーバーが同じ 404 にする）。React・window に触れない。

/**
 * フラグメントの文字列（先頭の `#` はあってもなくてもよい）を `key=value` の並び（`&` 区切り）として読み、
 * `token` の値を URL の符号化を戻して返す。無い・空・符号化を戻せない値のときは undefined。
 */
export function readRegistrationToken(hash: string): string | undefined {
  const fragment = hash.startsWith('#') ? hash.slice(1) : hash
  if (fragment.length === 0) {
    return undefined
  }
  for (const pair of fragment.split('&')) {
    const separator = pair.indexOf('=')
    const key = separator === -1 ? pair : pair.slice(0, separator)
    if (key !== 'token') {
      continue
    }
    const raw = separator === -1 ? '' : pair.slice(separator + 1)
    let value: string
    try {
      value = decodeURIComponent(raw)
    } catch {
      // 壊れた % の並びは使えないリンクとして扱う（値は出さない）。
      return undefined
    }
    return value.length > 0 ? value : undefined
  }
  return undefined
}
