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
// 行の「操作」のトリガーの名前の関数の例のテスト（U4 の D1・D8、U4-NFR4.6）。値は架空のもの。
import { describe, expect, it } from 'vitest'
import { rowActionsName } from './rowActionsName'

describe('rowActionsName', () => {
  it('starts with the Japanese visible label followed by the row label', () => {
    expect(rowActionsName('操作', '佐藤 一郎（sato@example.com）')).toBe(
      '操作: 佐藤 一郎（sato@example.com）',
    )
  })

  it('starts with the English visible label followed by the row label', () => {
    expect(rowActionsName('Actions', '営業')).toBe('Actions: 営業')
  })

  it('returns only the visible label when the row label is empty', () => {
    expect(rowActionsName('操作', '')).toBe('操作')
  })

  it('returns only the visible label when the row label is whitespace only', () => {
    expect(rowActionsName('Actions', ' \t\n　')).toBe('Actions')
  })

  it('trims leading and trailing whitespace from the row label', () => {
    expect(rowActionsName('操作', '  営業  ')).toBe('操作: 営業')
  })

  it('keeps markup-like characters and line breaks as plain text', () => {
    const value = '<script>alert("x")</script> & \'b\'\n次の行'
    expect(rowActionsName('操作', value)).toBe(`操作: ${value}`)
  })
})
