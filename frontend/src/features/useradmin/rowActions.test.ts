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
// 行の操作の項目と押せなさのテスト（functional-spec.md の W4 の表と 6 の性質、D5・D6、NFR1.1・NFR9.6）。
// W4 の表の全組（admin・suspended・resettable・self の 16 通り）と、fast-check の性質ベースのテスト（既定の 100 回）。
// 失敗したときの再現の仕方: fast-check は失敗の報告に seed と path を出すため、その fc.assert(property) を
// fc.assert(property, { seed: <seed>, path: '<path>' }) に一時的に替えてこのファイルだけを流す（原因を直した後に元に戻し、
// git diff で指定が残っていないことを確かめる）。
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import { rowActions, type RowAction, type RowActionInput, type RowActionKind } from './rowActions'

const inputArbitrary: fc.Arbitrary<RowActionInput> = fc.record({
  admin: fc.boolean(),
  suspended: fc.boolean(),
  resettable: fc.boolean(),
  self: fc.boolean(),
})

function find(actions: RowAction[], kind: RowActionKind): RowAction | undefined {
  return actions.find((action) => action.kind === kind)
}

/** W4 の表から、期待する項目を組み立てる（表の行を1つずつ写した形） */
function expected({ admin, suspended, resettable, self }: RowActionInput): RowAction[] {
  const result: RowAction[] = []
  if (!admin && !suspended) result.push({ kind: 'grantAdmin' })
  if (admin && !suspended) {
    result.push(
      self ? { kind: 'revokeAdmin', disabledReason: 'selfRevoke' } : { kind: 'revokeAdmin' },
    )
  }
  if (!suspended) {
    result.push(self ? { kind: 'suspend', disabledReason: 'selfSuspend' } : { kind: 'suspend' })
  }
  if (suspended) result.push({ kind: 'resume' })
  if (resettable) result.push({ kind: 'resetFailures' })
  result.push({ kind: 'editProfile' })
  return result
}

describe('rowActions', () => {
  it('matches the W4 table for all 16 combinations of the four row values', () => {
    const flags = [false, true]
    let count = 0
    for (const admin of flags) {
      for (const suspended of flags) {
        for (const resettable of flags) {
          for (const self of flags) {
            const input = { admin, suspended, resettable, self }
            expect(rowActions(input)).toEqual(expected(input))
            count += 1
          }
        }
      }
    }
    expect(count).toBe(16)
  })

  it('lists the items in the order of the W4 table', () => {
    expect(
      rowActions({ admin: false, suspended: false, resettable: true, self: false }).map(
        (a) => a.kind,
      ),
    ).toEqual(['grantAdmin', 'suspend', 'resetFailures', 'editProfile'])
    expect(
      rowActions({ admin: true, suspended: true, resettable: true, self: false }).map(
        (a) => a.kind,
      ),
    ).toEqual(['resume', 'resetFailures', 'editProfile'])
  })

  it('always shows revoke and suspend as disabled on the own row', () => {
    fc.assert(
      fc.property(inputArbitrary, (input) => {
        const actions = rowActions({ ...input, self: true })
        const revoke = find(actions, 'revokeAdmin')
        const suspend = find(actions, 'suspend')
        expect(revoke === undefined || revoke.disabledReason === 'selfRevoke').toBe(true)
        expect(suspend === undefined || suspend.disabledReason === 'selfSuspend').toBe(true)
      }),
    )
  })

  it('shows no admin items and no suspend but resume on a suspended row', () => {
    fc.assert(
      fc.property(inputArbitrary, (input) => {
        const actions = rowActions({ ...input, suspended: true })
        expect(find(actions, 'grantAdmin')).toBeUndefined()
        expect(find(actions, 'revokeAdmin')).toBeUndefined()
        expect(find(actions, 'suspend')).toBeUndefined()
        expect(find(actions, 'resume')).toEqual({ kind: 'resume' })
      }),
    )
  })

  it('shows reset failures only when resettable and then always enabled', () => {
    fc.assert(
      fc.property(inputArbitrary, (input) => {
        const reset = find(rowActions(input), 'resetFailures')
        expect(reset).toEqual(input.resettable ? { kind: 'resetFailures' } : undefined)
      }),
    )
  })

  it('always shows an enabled edit item, never both admin items, and exactly one of suspend and resume', () => {
    fc.assert(
      fc.property(inputArbitrary, (input) => {
        const actions = rowActions(input)
        expect(find(actions, 'editProfile')).toEqual({ kind: 'editProfile' })
        const adminItems = actions.filter(
          (a) => a.kind === 'grantAdmin' || a.kind === 'revokeAdmin',
        )
        expect(adminItems.length).toBeLessThanOrEqual(1)
        const stateItems = actions.filter((a) => a.kind === 'suspend' || a.kind === 'resume')
        expect(stateItems).toHaveLength(1)
        // 押せない形になるのは自分の行の2つだけ（ほかの値で押せなさが決まらない）
        for (const action of actions) {
          if (action.disabledReason !== undefined) {
            expect(input.self).toBe(true)
            expect(['revokeAdmin', 'suspend']).toContain(action.kind)
          }
        }
      }),
    )
  })
})
