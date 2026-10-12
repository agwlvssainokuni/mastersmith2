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
// ロールの管理の画面の失敗の文言の鍵のテスト（Intent 261004-role-menu の U6 の D3・D9・D14、NFR1.8）。
import { describe, expect, it } from 'vitest'
import { networkError, type ApiResponseError } from '../../shared/api-client/apiError'
import { failureMessageKey, GENERAL_FAILURE_KEY, inUseCounts, knownCode } from './failureMessage'

function responseError(
  status: number,
  code?: string,
  problem?: Readonly<Record<string, unknown>>,
): ApiResponseError {
  const error: ApiResponseError =
    code === undefined ? { kind: 'response', status } : { kind: 'response', status, code }
  if (problem !== undefined) {
    Object.defineProperty(error, 'problem', { value: problem, enumerable: false })
  }
  return error
}

describe('roleadmin failureMessage', () => {
  it('chooses the message by the code and the scene', () => {
    expect(failureMessageKey(responseError(409, 'ROLE_NAME_DUPLICATE'), 'name')).toBe(
      'roleadmin.name.duplicate',
    )
    expect(failureMessageKey(responseError(400, 'VALIDATION_FAILED'), 'name')).toBe(
      'roleadmin.name.invalid',
    )
    expect(failureMessageKey(responseError(400, 'VALIDATION_FAILED'), 'save')).toBe(
      GENERAL_FAILURE_KEY,
    )
    expect(failureMessageKey(responseError(409, 'ROLE_BUSY'), 'save')).toBe('roleadmin.busy')
    expect(failureMessageKey(responseError(409, 'GROUP_BUSY'), 'assign')).toBe('roleadmin.busy')
    expect(failureMessageKey(responseError(404, 'ROLE_NOT_FOUND'), 'delete')).toBe(
      'roleadmin.role.missing',
    )
    expect(failureMessageKey(responseError(404, 'USER_NOT_FOUND'), 'assign')).toBe(
      'roleadmin.user.missing',
    )
    expect(failureMessageKey(responseError(404, 'GROUP_NOT_FOUND'), 'assign')).toBe(
      'roleadmin.group.missing',
    )
    expect(failureMessageKey(responseError(409, 'PERMISSION_TARGET_NOT_IN_DSL'), 'save')).toBe(
      'roleadmin.save.notInDsl',
    )
    expect(failureMessageKey(responseError(409, 'DSL_NOT_APPLIED'), 'save')).toBe('roleadmin.noDsl')
  })

  it('chooses the no-change message by the scene', () => {
    const noChange = responseError(409, 'ROLE_NO_CHANGE')
    expect(failureMessageKey(noChange, 'name')).toBe('roleadmin.name.noChange')
    expect(failureMessageKey(noChange, 'save')).toBe('roleadmin.save.noChange')
    expect(failureMessageKey(noChange, 'clear')).toBe('roleadmin.clear.noChange')
    expect(failureMessageKey(noChange, 'assign')).toBe('roleadmin.assign.already')
    expect(failureMessageKey(noChange, 'unassign')).toBe('roleadmin.unassign.already')
  })

  it('does not change the message by the detail or the title of the server', () => {
    const marked = responseError(409, 'ROLE_BUSY', { detail: 'MARK-DETAIL', title: 'MARK-TITLE' })
    expect(failureMessageKey(marked, 'name')).toBe('roleadmin.busy')
  })

  it('uses the general message for unknown codes, responses without a code and network errors', () => {
    expect(failureMessageKey(responseError(409, 'SOMETHING_ELSE'), 'name')).toBe(
      GENERAL_FAILURE_KEY,
    )
    expect(failureMessageKey(responseError(500), 'save')).toBe(GENERAL_FAILURE_KEY)
    expect(failureMessageKey(networkError(), 'assign')).toBe(GENERAL_FAILURE_KEY)
    expect(failureMessageKey(new Error('boom'), 'delete')).toBe(GENERAL_FAILURE_KEY)
    expect(knownCode(undefined)).toBeUndefined()
  })

  it('reads the counts of ROLE_IN_USE only when both are integers', () => {
    expect(
      inUseCounts(responseError(409, 'ROLE_IN_USE', { assignedUsers: 2, assignedGroups: 1 })),
    ).toEqual({ users: 2, groups: 1 })
    expect(
      inUseCounts(responseError(409, 'ROLE_IN_USE', { assignedUsers: '2', assignedGroups: 1 })),
    ).toBeUndefined()
    expect(
      inUseCounts(responseError(409, 'ROLE_IN_USE', { assignedUsers: 1.5, assignedGroups: 1 })),
    ).toBeUndefined()
    expect(inUseCounts(responseError(409, 'ROLE_IN_USE'))).toBeUndefined()
    expect(
      inUseCounts(responseError(409, 'ROLE_BUSY', { assignedUsers: 2, assignedGroups: 1 })),
    ).toBeUndefined()
    expect(failureMessageKey(responseError(409, 'ROLE_IN_USE'), 'delete')).toBe(
      'roleadmin.inUse.noCount',
    )
  })
})
