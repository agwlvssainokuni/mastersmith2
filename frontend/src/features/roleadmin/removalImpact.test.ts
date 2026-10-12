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
// 外したときの影響の判定のテスト（Intent 261004-role-menu の U6 の D19、AC2.2.9、NFR6.2）。
// 失敗したときの再現の仕方: fast-check の報告の seed と path を fc.assert の2つ目の引数に一時的に渡して流す。
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import type { AssignmentSource, UserRole } from './api/types'
import { removalImpact } from './removalImpact'

const DIRECT: AssignmentSource = { kind: 'DIRECT' }
const viaGroup = (groupId: number, name: string): AssignmentSource => ({
  kind: 'GROUP',
  groupId,
  name,
})

const sourceArbitrary: fc.Arbitrary<AssignmentSource> = fc.oneof(
  fc.constant(DIRECT),
  fc
    .tuple(fc.integer({ min: 1, max: 50 }), fc.string({ maxLength: 4 }))
    .map(([id, name]) => viaGroup(id, name)),
)
const rolesArbitrary: fc.Arbitrary<UserRole[]> = fc
  .uniqueArray(fc.integer({ min: 1, max: 100 }), { maxLength: 8 })
  .chain((ids) =>
    fc.tuple(
      ...ids.map((roleId) =>
        fc
          .array(sourceArbitrary, { minLength: 1, maxLength: 3 })
          .map((sources) => ({ roleId, name: `R${roleId}`, sources })),
      ),
    ),
  )

describe('removalImpact', () => {
  it('loses the role when the only source is direct and names the smallest remaining role', () => {
    const roles: UserRole[] = [
      { roleId: 7, name: '営業', sources: [DIRECT] },
      { roleId: 9, name: '経理', sources: [viaGroup(1, '経理部')] },
      { roleId: 3, name: '総務', sources: [DIRECT] },
    ]
    expect(removalImpact(roles, 7)).toEqual({
      losesRole: true,
      nextWorkRole: { roleId: 3, name: '総務' },
    })
  })

  it('keeps the role when it also comes through a group', () => {
    const roles: UserRole[] = [
      { roleId: 7, name: '営業', sources: [DIRECT, viaGroup(2, '営業部')] },
    ]
    expect(removalImpact(roles, 7)).toEqual({ losesRole: false, nextWorkRole: null })
  })

  it('gives no next role when nothing remains', () => {
    expect(removalImpact([{ roleId: 7, name: '営業', sources: [DIRECT] }], 7)).toEqual({
      losesRole: true,
      nextWorkRole: null,
    })
  })

  it('does not lose a role that the user does not have', () => {
    expect(removalImpact([], 7)).toEqual({ losesRole: false, nextWorkRole: null })
  })

  it('never loses the role when a source other than direct exists (property)', () => {
    fc.assert(
      fc.property(rolesArbitrary, fc.integer({ min: 1, max: 100 }), (roles, roleId) => {
        const target = roles.find((role) => role.roleId === roleId)
        const impact = removalImpact(roles, roleId)
        if (target === undefined || target.sources.some((source) => source.kind !== 'DIRECT')) {
          expect(impact.losesRole).toBe(false)
          expect(impact.nextWorkRole).toBeNull()
        } else {
          expect(impact.losesRole).toBe(true)
        }
      }),
    )
  })

  it('names the remaining role with the smallest ID as the next working role (property)', () => {
    fc.assert(
      fc.property(rolesArbitrary, (roles) => {
        for (const role of roles) {
          const impact = removalImpact(roles, role.roleId)
          if (!impact.losesRole) {
            continue
          }
          const others = roles.filter((other) => other.roleId !== role.roleId)
          const expected =
            others.length === 0 ? null : Math.min(...others.map((other) => other.roleId))
          expect(impact.nextWorkRole?.roleId ?? null).toBe(expected)
        }
      }),
    )
  })
})
