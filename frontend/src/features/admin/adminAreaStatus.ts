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
// 「権限が無い」（403・ACCESS_DENIED）の判定は骨組みの useAdminForbidden が渡す関数に任せ、ここでは
// 状態コードと code を見ない（U4 の D1・D2、FC 6.2、SD 2.2）。
import { ADMIN_CHECK_PATH } from './adminApi'

/** 管理者向け領域の表示の状態（Forbidden は骨組みが S6 に置き換えているため、画面は何も描かない） */
export type AdminAreaStatus = 'Checking' | 'Shown' | 'Forbidden' | 'Error'

/** API の失敗を骨組みへ渡す関数（useAdminForbidden が返すもの） */
export type ReportAdminForbidden = (error: unknown, apiPath: string) => boolean

/**
 * 確認用 API の失敗から表示の状態を決める。渡した関数が「権限が無い」と判定したら `Forbidden`、
 * そのほかの応答のエラー（code の無い・違う 403 を含む）と通信の失敗は `Error`（FS の G9）。
 *
 * 401 は U2 の ApiClient が更新と送り直しを行い、それでも駄目ならログイン画面へ移すため、ここには 401 として届かない。
 *
 * @param error API の呼び出しの失敗
 * @param report 失敗を骨組みへ渡す関数（確認用 API のパスを添えて呼ぶ）
 * @returns 表示の状態
 */
export function statusFromError(error: unknown, report: ReportAdminForbidden): AdminAreaStatus {
  return report(error, ADMIN_CHECK_PATH) ? 'Forbidden' : 'Error'
}
