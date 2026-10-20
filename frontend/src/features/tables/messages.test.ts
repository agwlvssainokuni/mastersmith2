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
// 業務データの画面の文言のテスト（Intent 261016-business-data-views の U3、frontend-components.md の 5節、U3-NFR6.3）。
import { describe, expect, it } from 'vitest'
import { tablesMessages } from './messages'
import { FIELD_ERROR_REASONS, readProblemMessageKey, type ReadProblemKind } from './problemMessage'

const KINDS: ReadProblemKind[] = [
  'forbidden',
  'table-not-found',
  'row-not-found',
  'dsl-not-applied',
  'unavailable',
  'mismatch',
  'not-unique',
  'payload-too-large',
  'bad-request',
  'unknown-client',
  'unknown-server',
  'network',
]

describe('tablesMessages', () => {
  it('has the same keys in Japanese and English without the removed placeholder text', () => {
    expect(Object.keys(tablesMessages.ja).sort()).toEqual(Object.keys(tablesMessages.en).sort())
    expect(tablesMessages.ja).not.toHaveProperty('tables.preparing')
    for (const [key, text] of Object.entries(tablesMessages.ja)) {
      expect(text.trim(), key).not.toBe('')
      expect(tablesMessages.en[key]?.trim(), key).not.toBe('')
    }
  })

  it('has a text for every problem kind and every field error reason', () => {
    for (const kind of KINDS) {
      expect(tablesMessages.ja).toHaveProperty([readProblemMessageKey(kind)])
    }
    for (const reason of FIELD_ERROR_REASONS) {
      expect(tablesMessages.ja).toHaveProperty([`tables.error.${reason}`])
      expect(tablesMessages.en).toHaveProperty([`tables.error.${reason}`])
    }
    expect(tablesMessages.ja['tables.forbidden']).toBe(
      'このテーブルを見る権限がありません。作業ロールを確かめてください。',
    )
  })
})
