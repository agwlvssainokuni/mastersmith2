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
// 最新の読み込みの見分けのテスト（Intent 261004-role-menu の U6 の L4、NFR2.11）。
import { describe, expect, it } from 'vitest'
import { createLatestLoad } from './latestLoad'

describe('createLatestLoad', () => {
  it('treats the generation just begun as the latest', () => {
    const load = createLatestLoad()
    const first = load.begin()
    expect(load.isLatest(first)).toBe(true)
  })

  it('makes an earlier generation stale once a newer load begins', () => {
    const load = createLatestLoad()
    const first = load.begin()
    const second = load.begin()
    expect(load.isLatest(first)).toBe(false)
    expect(load.isLatest(second)).toBe(true)
  })

  it('makes every begun generation stale after invalidate', () => {
    const load = createLatestLoad()
    const first = load.begin()
    load.invalidate()
    expect(load.isLatest(first)).toBe(false)
  })

  it('keeps separate instances independent', () => {
    const left = createLatestLoad()
    const right = createLatestLoad()
    const generation = left.begin()
    right.begin()
    right.invalidate()
    expect(left.isLatest(generation)).toBe(true)
  })
})
