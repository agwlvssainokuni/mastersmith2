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
// 権限の YAML のファイルの上限（Intent 261004-role-menu の U6 の D23、L8、NFR1.5）。契約の値 5,242,880 バイト
// （5 MiB。Build and Test の Q1 で 10 MiB から下げた）で、サーバーの role の
// `RoleTransferLimits.DEFAULT_MAX_BYTES` と設定 `mastersmith.role-transfer.max-bytes` の既定と同じ値にする
// （変えるときは両方を同じ変更で直す。サーバーの境界は role の RoleTransferLimitsApiIT が持つ）。
// 判定の正はサーバーで、画面の確かめは送る前の案内。

/** ファイルの大きさの上限（バイト） */
export const MAX_TRANSFER_BYTES = 5_242_880

/** 文言に差し込む上限の値（MiB） */
export const MAX_TRANSFER_MIB = MAX_TRANSFER_BYTES / (1024 * 1024)

/** ファイルの大きさが上限の中か（上限ちょうどは送る）。 */
export function withinTransferLimit(size: number): boolean {
  return size <= MAX_TRANSFER_BYTES
}
