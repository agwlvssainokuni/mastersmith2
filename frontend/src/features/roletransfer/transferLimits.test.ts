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
// 権限の YAML のファイルの上限のテスト（Intent 261004-role-menu の U6 の D23、NFR1.5、計画 2.2 の NFR 設計 R-05）。
// 比べる先は契約の値 5,242,880。サーバーの role の `RoleTransferLimits.DEFAULT_MAX_BYTES` と設定
// `mastersmith.role-transfer.max-bytes` の既定と同じ値で、変えるときは両方を同じ変更で直す（サーバーの境界は
// role の RoleTransferLimitsApiIT が持つ）。
import { describe, expect, it } from 'vitest'
import { MAX_TRANSFER_BYTES, MAX_TRANSFER_MIB, withinTransferLimit } from './transferLimits'

describe('transferLimits', () => {
  it('uses the contract value of 5,242,880 bytes (5 MiB)', () => {
    expect(MAX_TRANSFER_BYTES).toBe(5_242_880)
    expect(MAX_TRANSFER_MIB).toBe(5)
  })

  it('sends a file of exactly the limit and stops one byte over the limit', () => {
    expect(withinTransferLimit(5_242_880)).toBe(true)
    expect(withinTransferLimit(5_242_881)).toBe(false)
    expect(withinTransferLimit(0)).toBe(true)
  })
})
