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
// 読み直しの後のフォーカスの行き先のテスト（functional-spec.md の W12）。
import { describe, expect, it } from 'vitest'
import { focusAfterReload } from './focusTarget'

describe('focusAfterReload', () => {
  it('keeps the actions of the same row when the row is still on the page', () => {
    expect(focusAfterReload({ kind: 'row', userId: 12 }, [11, 12, 13])).toEqual({
      kind: 'row',
      userId: 12,
    })
  })

  it('moves to the list heading when the row is no longer on the page', () => {
    expect(focusAfterReload({ kind: 'row', userId: 99 }, [11, 12])).toEqual({ kind: 'heading' })
  })

  it('moves to the list heading when the list is empty', () => {
    expect(focusAfterReload({ kind: 'row', userId: 11 }, [])).toEqual({ kind: 'heading' })
  })

  it('keeps the heading and the retry targets as they are', () => {
    expect(focusAfterReload({ kind: 'heading' }, [11])).toEqual({ kind: 'heading' })
    expect(focusAfterReload({ kind: 'retry' }, [])).toEqual({ kind: 'retry' })
  })

  it('gives no target when none was asked for', () => {
    expect(focusAfterReload(null, [11, 12])).toBeNull()
  })
})
