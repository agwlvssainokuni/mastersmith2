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
// 報告に残してはならない値のファイルの単体のテスト（Intent 261016-business-data-views の U3 の L10、NFR 設計のレビュー R-07、
// 計画 D5: A）。値は一時のディレクトリにだけ書く。
import { mkdtempSync, rmSync, statSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { afterEach, describe, expect, it } from 'vitest'
import {
  BUSINESS_VALUE_MARK,
  measurementSampleValues,
  readSecretValues,
  recordSecretValues,
  SECRET_FIXED_VALUES,
  SECRET_KINDS,
} from './secretValues'

const dirs: string[] = []
afterEach(() => {
  for (const dir of dirs.splice(0)) {
    rmSync(dir, { recursive: true, force: true })
  }
})

function tempFile(): string {
  const dir = mkdtempSync(path.join(tmpdir(), 'secret-values-test-'))
  dirs.push(dir)
  return path.join(dir, 'values.json')
}

describe('secretValues', () => {
  it('writes and reads back a business value with the owner-only permission', () => {
    const file = tempFile()
    recordSecretValues([{ kind: 'businessValue', value: `${BUSINESS_VALUE_MARK}-run` }], file)
    recordSecretValues([{ kind: 'runTag', value: '' }], file)
    expect(readSecretValues(file)).toEqual([
      { kind: 'businessValue', value: `${BUSINESS_VALUE_MARK}-run` },
    ])
    expect(statSync(file).mode & 0o777).toBe(0o600)
  })

  it('accepts exactly the kinds of the one constant and rejects others', () => {
    expect(SECRET_KINDS).toEqual(['email', 'password', 'displayName', 'runTag', 'businessValue'])
    const file = tempFile()
    recordSecretValues([{ kind: 'email', value: 'a@example.com' }], file)
    expect(() => readSecretValues(path.join(path.dirname(file), 'none.json'))).not.toThrow()
    recordSecretValues([{ kind: 'unknown' as never, value: 'x' }], file)
    expect(() => readSecretValues(file)).toThrow('値のファイルの形が違います')
  })

  it('searches the business value mark and the measurement samples of U4', () => {
    expect(SECRET_FIXED_VALUES).toContainEqual({
      kind: 'businessValue',
      value: BUSINESS_VALUE_MARK,
    })
    const measured = measurementSampleValues()
    expect(measured).toHaveLength(50)
    expect(measured).toContainEqual({ kind: 'displayName', value: '測定 01' })
    expect(measured).toContainEqual({ kind: 'email', value: 'sokutei.25@example.com' })
    for (const entry of measured) {
      expect(SECRET_FIXED_VALUES).toContainEqual(entry)
    }
  })
})
