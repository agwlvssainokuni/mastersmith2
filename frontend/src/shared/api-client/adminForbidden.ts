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
// 管理の API の「権限が無い」の判定（U4 の D1、frontend-components.md 3.1、security-design.md 2.2）。
// - 「権限が無い」は、要求のパスが `/api/admin/`（末尾の `/` まで含む）で始まり、状態が 403 で、
//   code が `ACCESS_DENIED` のときだけとする。判定はこの関数の1か所だけで行う（NFR1.2）。
// - 失敗の値は `unknown` で受け、中で形を確かめる（FS の G4）。知らない値では例外を出さず false を返す。
// - パスは呼び出し元の定数（各画面の API の根のパス）で、利用者の入力ではないため、正規化しない。
// - ApiClient の要求の流れには入れない（ApiClient は 403 を知らせない、FS の G3）。副作用は無い。

/** 管理の API のパスの接頭辞（末尾の `/` まで含めて比べる） */
export const ADMIN_API_PREFIX = '/api/admin/'

/** 管理の API の「権限が無い」の code */
export const ACCESS_DENIED = 'ACCESS_DENIED'

/** 「権限が無い」の状態コード */
export const FORBIDDEN_STATUS = 403

/**
 * 管理の API の「権限が無い」（D1）かどうか。
 * パスが {@link ADMIN_API_PREFIX} で始まり、失敗が `{ kind: 'response', status: 403, code: 'ACCESS_DENIED' }`
 * の形のときだけ true を返す。
 */
export function isAdminForbidden(path: string, error: unknown): boolean {
  if (typeof path !== 'string' || !path.startsWith(ADMIN_API_PREFIX)) {
    return false
  }
  if (typeof error !== 'object' || error === null) {
    return false
  }
  const candidate = error as { kind?: unknown; status?: unknown; code?: unknown }
  return (
    candidate.kind === 'response' &&
    candidate.status === FORBIDDEN_STATUS &&
    candidate.code === ACCESS_DENIED
  )
}
