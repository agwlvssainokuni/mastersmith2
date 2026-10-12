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
// 権限の YAML の誤りの理由（role の TransferError.Reason の 13 個）を文言の鍵に写す（Intent 261004-role-menu の U6 の
// D26、L3、NFR1.8、計画 2.3 の A16）。サーバーの文言は無い。知らない値・文字列でない値は汎用の文言にし、例外にしない。

/** 理由の値（role の TransferError.Reason と同じ並び） */
export const TRANSFER_ERROR_REASONS = [
  'TOO_DEEP',
  'TOO_MANY_ALIASES',
  'TAG_NOT_ALLOWED',
  'DUPLICATE_KEY',
  'SYNTAX',
  'UNSUPPORTED_VERSION',
  'MISSING_KEY',
  'UNKNOWN_KEY',
  'INVALID_VALUE',
  'INVALID_ROLE_NAME',
  'DUPLICATE_ROLE',
  'INVALID_TARGET_NAME',
  'COLUMN_AUX_NOT_ALLOWED',
] as const

/** 理由の値 */
export type TransferErrorReason = (typeof TRANSFER_ERROR_REASONS)[number]

/** 知らない理由の文言の鍵 */
export const UNKNOWN_REASON_KEY = 'roletransfer.reason.unknown'

const KNOWN: ReadonlySet<string> = new Set(TRANSFER_ERROR_REASONS)

/** 理由の文言の鍵 */
export function reasonMessageKey(reason: unknown): string {
  return typeof reason === 'string' && KNOWN.has(reason)
    ? `roletransfer.reason.${reason}`
    : UNKNOWN_REASON_KEY
}
