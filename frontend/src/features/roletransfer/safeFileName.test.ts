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
// 書き出しの保存の名前のテスト（Intent 261004-role-menu の U6、security-design.md 3節、NFR1.10）。
import { describe, expect, it } from 'vitest'
import { DEFAULT_EXPORT_FILE_NAME, safeFileName } from './safeFileName'

describe('safeFileName', () => {
  it('uses a safe yaml name from the Content-Disposition', () => {
    expect(safeFileName('attachment; filename="roles.yaml"')).toBe('roles.yaml')
    expect(safeFileName('attachment; filename=roles-2026_10.yml')).toBe('roles-2026_10.yml')
  })

  it('falls back to roles.yaml for path separators and parent directories', () => {
    expect(safeFileName('attachment; filename="../x.yaml"')).toBe(DEFAULT_EXPORT_FILE_NAME)
    expect(safeFileName('attachment; filename="a/b.yaml"')).toBe(DEFAULT_EXPORT_FILE_NAME)
    expect(safeFileName('attachment; filename="a\\\\b.yaml"')).toBe(DEFAULT_EXPORT_FILE_NAME)
  })

  it('falls back to roles.yaml for control characters, an empty name and other extensions', () => {
    expect(safeFileName('attachment; filename="ro\u0001les.yaml"')).toBe(DEFAULT_EXPORT_FILE_NAME)
    expect(safeFileName('attachment; filename=""')).toBe(DEFAULT_EXPORT_FILE_NAME)
    expect(safeFileName('attachment; filename="x.txt"')).toBe(DEFAULT_EXPORT_FILE_NAME)
    expect(safeFileName('attachment; filename=".yaml"')).toBe(DEFAULT_EXPORT_FILE_NAME)
  })

  it('falls back to roles.yaml when there is no header or no file name', () => {
    expect(safeFileName(null)).toBe(DEFAULT_EXPORT_FILE_NAME)
    expect(safeFileName('attachment')).toBe(DEFAULT_EXPORT_FILE_NAME)
  })
})
