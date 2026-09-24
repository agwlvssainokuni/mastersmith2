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
// ファイルの保存のテスト（NFR3.10）。一時的な URL を作り、保存の後すぐに捨てることを確かめる。
import { afterEach, describe, expect, it, vi } from 'vitest'
import { saveFile } from './saveFile'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('saveFile', () => {
  it('saves the bytes through a temporary URL and revokes it right away', () => {
    const createObjectURL = vi.fn(() => 'blob:temporary')
    const revokeObjectURL = vi.fn()
    vi.stubGlobal('URL', { createObjectURL, revokeObjectURL })
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      expect(this.download).toBe('dsl-preview-8b02d4aaaaaa.yaml')
      expect(this.getAttribute('href')).toBe('blob:temporary')
    })
    const blob = new Blob(['version: 1\n'])

    saveFile({ blob, fileName: 'dsl-preview-8b02d4aaaaaa.yaml' })

    expect(createObjectURL).toHaveBeenCalledWith(blob)
    expect(click).toHaveBeenCalledTimes(1)
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:temporary')
    expect(document.querySelector('a[download]')).toBeNull()
  })

  it('revokes the temporary URL even when the save fails', () => {
    const revokeObjectURL = vi.fn()
    vi.stubGlobal('URL', { createObjectURL: () => 'blob:temporary', revokeObjectURL })
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {
      throw new Error('blocked')
    })

    expect(() => saveFile({ blob: new Blob(['a']), fileName: 'a.yaml' })).toThrow('blocked')
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:temporary')
    expect(document.querySelector('a[download]')).toBeNull()
  })
})
