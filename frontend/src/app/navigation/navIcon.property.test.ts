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
// 業務の項目のアイコンの決め方の性質ベースのテスト（Intent 261102-timezone-ui-polish の U5、D10・D29、fast-check）。
// 失敗したときの seed と path は fast-check が出力に示す。
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import { ALLOWED_ICONS } from '../registry/allowedIcons'
import { resolveItemIcon } from './navIcon'
import type { NavTableKind } from './navigationApi'

const SCRIPT_URL = ['javascript', 'alert(1)'].join(':')

const allowed = new Set<string>(ALLOWED_ICONS)

const iconArb = fc.oneof(
  fc.constantFrom(...ALLOWED_ICONS),
  fc.string(),
  fc.constantFrom('', 'HOME', 'skull', SCRIPT_URL, 'list'),
  fc.constantFrom(null, undefined, 0, {}),
)
const kindArb = fc.constantFrom<NavTableKind | null | undefined>('TABLE', 'VIEW', null, undefined)

describe('resolveItemIcon properties', () => {
  it('always returns a name of the allowed list', () => {
    fc.assert(
      fc.property(iconArb, kindArb, fc.boolean(), (icon, tableKind, navigable) => {
        expect(allowed.has(resolveItemIcon({ icon, tableKind, navigable }))).toBe(true)
      }),
    )
  })

  it('puts the table kind ahead of the default whenever the icon is unspecified', () => {
    fc.assert(
      fc.property(
        fc.constantFrom('list', '', 'unknown', 'HOME'),
        fc.constantFrom<NavTableKind>('TABLE', 'VIEW'),
        fc.boolean(),
        (icon, tableKind, navigable) => {
          expect(resolveItemIcon({ icon, tableKind, navigable })).toBe(
            tableKind === 'TABLE' ? 'table' : 'view',
          )
        },
      ),
    )
  })

  it('returns the specified icon as it is when it is allowed and not list', () => {
    fc.assert(
      fc.property(
        fc.constantFrom(...ALLOWED_ICONS.filter((name) => name !== 'list')),
        kindArb,
        fc.boolean(),
        (icon, tableKind, navigable) => {
          expect(resolveItemIcon({ icon, tableKind, navigable })).toBe(icon)
        },
      ),
    )
  })
})
