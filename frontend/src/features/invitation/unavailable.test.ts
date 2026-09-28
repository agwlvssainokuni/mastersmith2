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
// 警告の文の形のテスト（W3、AC1.1.6・AC2.2.9）。知らない理由の値は出さない。
import { describe, expect, it } from 'vitest'
import { knownReasons, unavailableNotice } from './unavailable'

describe('unavailableNotice', () => {
  it('uses the single sentence for each known reason', () => {
    expect(unavailableNotice(['SMTP_NOT_CONFIGURED'])).toEqual({
      kind: 'single',
      messageKey: 'invitation.unavailable.SMTP_NOT_CONFIGURED',
    })
    expect(unavailableNotice(['BASE_URL_NOT_CONFIGURED'])).toEqual({
      kind: 'single',
      messageKey: 'invitation.unavailable.BASE_URL_NOT_CONFIGURED',
    })
  })

  it('lists both reasons under the title in the order of the response', () => {
    expect(unavailableNotice(['BASE_URL_NOT_CONFIGURED', 'SMTP_NOT_CONFIGURED'])).toEqual({
      kind: 'multiple',
      titleKey: 'invitation.unavailable.title',
      reasonKeys: [
        'invitation.unavailableReason.BASE_URL_NOT_CONFIGURED',
        'invitation.unavailableReason.SMTP_NOT_CONFIGURED',
      ],
    })
  })

  it('shows the general sentence when only unknown reasons come', () => {
    expect(unavailableNotice(['DATABASE_DOWN', 42, null])).toEqual({
      kind: 'unknown',
      titleKey: 'invitation.unavailable.title',
      messageKey: 'invitation.unavailable.unknown',
    })
  })

  it('drops unknown values and duplicates when mixed with known ones', () => {
    expect(knownReasons(['X', 'SMTP_NOT_CONFIGURED', 'SMTP_NOT_CONFIGURED'])).toEqual([
      'SMTP_NOT_CONFIGURED',
    ])
    expect(unavailableNotice(['X', 'SMTP_NOT_CONFIGURED'])).toEqual({
      kind: 'single',
      messageKey: 'invitation.unavailable.SMTP_NOT_CONFIGURED',
    })
  })

  it('shows the general sentence when no reason is given', () => {
    expect(unavailableNotice([])).toEqual({
      kind: 'unknown',
      titleKey: 'invitation.unavailable.title',
      messageKey: 'invitation.unavailable.unknown',
    })
  })
})
