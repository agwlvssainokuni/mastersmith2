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
// 権限の YAML の誤りの理由の文言の鍵のテスト（Intent 261004-role-menu の U6 の D26、NFR1.8）。13 個の理由に ja・en の
// 文言があること、知らない値は汎用であること。
// 失敗したときの再現の仕方: fast-check の報告の seed と path を fc.assert の2つ目の引数に一時的に渡して流す。
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import { roleTransferMessages } from './messages'
import { reasonMessageKey, TRANSFER_ERROR_REASONS, UNKNOWN_REASON_KEY } from './reasonMessage'

describe('reasonMessage', () => {
  it('has the 13 reasons of the server', () => {
    expect(TRANSFER_ERROR_REASONS).toHaveLength(13)
  })

  it('maps every reason to a message that exists in Japanese and English', () => {
    for (const reason of TRANSFER_ERROR_REASONS) {
      const key = reasonMessageKey(reason)
      expect(key).toBe(`roletransfer.reason.${reason}`)
      expect(roleTransferMessages.ja[key]).toBeTruthy()
      expect(roleTransferMessages.en[key]).toBeTruthy()
    }
    expect(roleTransferMessages.ja[UNKNOWN_REASON_KEY]).toBeTruthy()
    expect(roleTransferMessages.en[UNKNOWN_REASON_KEY]).toBeTruthy()
  })

  it('uses the general message for unknown values and values that are not strings', () => {
    expect(reasonMessageKey('NEW_REASON')).toBe(UNKNOWN_REASON_KEY)
    expect(reasonMessageKey('')).toBe(UNKNOWN_REASON_KEY)
    expect(reasonMessageKey(undefined)).toBe(UNKNOWN_REASON_KEY)
    expect(reasonMessageKey(3)).toBe(UNKNOWN_REASON_KEY)
    expect(reasonMessageKey('toString')).toBe(UNKNOWN_REASON_KEY)
  })

  it('never throws and gives a known key for any string (property)', () => {
    fc.assert(
      fc.property(fc.string({ unit: 'binary' }), (reason) => {
        const key = reasonMessageKey(reason)
        expect(
          key === UNKNOWN_REASON_KEY ||
            (TRANSFER_ERROR_REASONS as readonly string[]).includes(reason),
        ).toBe(true)
      }),
    )
  })
})
