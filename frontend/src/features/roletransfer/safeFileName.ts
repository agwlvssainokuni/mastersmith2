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
// 書き出しの保存の名前（Intent 261004-role-menu の U6、security-design.md 3節、L8、NFR1.10）。`Content-Disposition` の
// filename が英数字・`.`・`_`・`-` だけで `.yaml` か `.yml` で終わるときだけ使い、それ以外（道の区切り・制御文字・
// 空・ほかの拡張子・ヘッダーが無い）は既定の `roles.yaml` にする。純粋な関数。

/** 既定の保存の名前 */
export const DEFAULT_EXPORT_FILE_NAME = 'roles.yaml'

const SAFE_NAME = /^[A-Za-z0-9._-]+\.(?:yaml|yml)$/

/** `Content-Disposition` の filename を読む（引用符つき・無しの両方）。読めなければ undefined。 */
function readFileName(contentDisposition: string): string | undefined {
  const match = /(?:^|;)\s*filename\s*=\s*(?:"([^"]*)"|([^;\s]*))/i.exec(contentDisposition)
  if (match === null) {
    return undefined
  }
  return match[1] ?? match[2]
}

/** 保存の名前を決める。 */
export function safeFileName(contentDisposition: string | null): string {
  if (contentDisposition === null) {
    return DEFAULT_EXPORT_FILE_NAME
  }
  const name = readFileName(contentDisposition)
  return name !== undefined && SAFE_NAME.test(name) && !name.startsWith('.')
    ? name
    : DEFAULT_EXPORT_FILE_NAME
}
