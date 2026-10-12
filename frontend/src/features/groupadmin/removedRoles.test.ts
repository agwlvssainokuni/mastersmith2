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
// 無くなったロールを求める関数のテスト（Intent 261004-role-menu の U6 の D20、NFR6.2）。
// 失敗したときの再現の仕方: fast-check の報告の seed と path を fc.assert の2つ目の引数に一時的に渡して流す。
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import type { GroupRoleRef, UserRole } from './api/types'
import { removedRoles } from './removedRoles'

const ref = (roleId: number): GroupRoleRef => ({ roleId, name: `R${roleId}` })

describe('removedRoles', () => {
  it('gives the group roles that the user no longer has', () => {
    const after: UserRole[] = [{ roleId: 2, name: '営業', sources: [{ kind: 'DIRECT' }] }]
    expect(removedRoles([ref(1), ref(2), ref(3)], after)).toEqual([ref(1), ref(3)])
  })

  it('gives nothing when all the group roles remain', () => {
    const after: UserRole[] = [
      { roleId: 1, name: 'R1', sources: [{ kind: 'GROUP', groupId: 9, name: '別の部' }] },
    ]
    expect(removedRoles([ref(1)], after)).toEqual([])
    expect(removedRoles([], after)).toEqual([])
  })

  it('never includes a role that remains and covers every group role that is gone (property)', () => {
    fc.assert(
      fc.property(
        fc.uniqueArray(fc.integer({ min: 1, max: 30 }), { maxLength: 10 }),
        fc.uniqueArray(fc.integer({ min: 1, max: 30 }), { maxLength: 10 }),
        (groupIds, remainingIds) => {
          const after: UserRole[] = remainingIds.map((roleId) => ({
            roleId,
            name: `R${roleId}`,
            sources: [{ kind: 'DIRECT' }],
          }))
          const removed = removedRoles(groupIds.map(ref), after).map((role) => role.roleId)
          for (const roleId of removed) {
            expect(remainingIds).not.toContain(roleId)
            expect(groupIds).toContain(roleId)
          }
          expect(removed).toEqual(groupIds.filter((roleId) => !remainingIds.includes(roleId)))
        },
      ),
    )
  })
})
