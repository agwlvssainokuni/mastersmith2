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
// 業務の項目のアイコンの決め方のテスト（Intent 261102-timezone-ui-polish の U5、D10・D13・D29）。
import { describe, expect, it } from 'vitest'
import { resolveItemIcon } from './navIcon'

const SCRIPT_URL = ['javascript', 'alert(1)'].join(':')

describe('resolveItemIcon', () => {
  it('step 1: uses an allowed icon other than list as it is, ahead of the table kind', () => {
    expect(resolveItemIcon({ icon: 'user', tableKind: 'VIEW', navigable: true })).toBe('user')
    expect(resolveItemIcon({ icon: 'home', tableKind: null, navigable: false })).toBe('home')
  })

  it('step 2: uses table or view by the kind, even for an item that has children', () => {
    expect(resolveItemIcon({ icon: 'list', tableKind: 'TABLE', navigable: true })).toBe('table')
    expect(resolveItemIcon({ icon: 'list', tableKind: 'VIEW', navigable: true })).toBe('view')
  })

  it('treats an icon of list as unspecified (D13)', () => {
    expect(resolveItemIcon({ icon: 'list', tableKind: 'VIEW', navigable: true })).toBe('view')
  })

  it('step 3: uses folder for an item that cannot be opened and has no kind', () => {
    expect(resolveItemIcon({ icon: 'list', tableKind: null, navigable: false })).toBe('folder')
  })

  it('step 4: uses list for an openable item whose kind is unknown', () => {
    expect(resolveItemIcon({ icon: 'list', tableKind: null, navigable: true })).toBe('list')
    expect(resolveItemIcon({ icon: '', tableKind: undefined, navigable: true })).toBe('list')
  })

  it('treats unknown, empty, wrongly cased and non-string icons as unspecified', () => {
    for (const icon of ['skull', '', 'HOME', SCRIPT_URL, 'https://example.com/a.svg', null, 3]) {
      expect(resolveItemIcon({ icon, tableKind: 'TABLE', navigable: true })).toBe('table')
    }
  })
})
