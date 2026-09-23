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
// 確認用 API の結果から、管理者向け領域の表示の状態を決める純粋な関数（BR5.2、reliability-design 2章）。
import type { ApiError } from '../../shared/api-client/apiError'

/** 管理者向け領域の表示の状態 */
export type AdminAreaStatus = 'Checking' | 'Shown' | 'NotFound' | 'Error'

/** 権限不足の状態コード（403 は「ページが見つかりません」を表示する） */
export const FORBIDDEN_STATUS = 403

/**
 * 確認用 API の失敗から表示の状態を決める。403 は `NotFound`、そのほかの応答のエラーと通信の失敗は `Error`。
 *
 * 401 は U2 の ApiClient が更新と送り直しを行い、それでも駄目ならログイン画面へ移すため、ここには 401 として届かない。
 *
 * @param error API の呼び出しの失敗
 * @returns 表示の状態
 */
export function statusFromError(error: unknown): AdminAreaStatus {
  const apiError = error as ApiError
  if (apiError?.kind === 'response' && apiError.status === FORBIDDEN_STATUS) {
    return 'NotFound'
  }
  return 'Error'
}
