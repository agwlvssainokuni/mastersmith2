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
// グループの管理の画面の失敗の文言の鍵のテスト（Intent 261004-role-menu の U6 の D3・D9、NFR1.8）。
import { describe, expect, it } from 'vitest'
import { networkError, type ApiResponseError } from '../../shared/api-client/apiError'
import { failureMessageKey, GENERAL_FAILURE_KEY, inUseCounts } from './failureMessage'

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

describe('groupadmin failureMessage', () => {
  it('chooses the message by the code and the scene', () => {
    expect(failureMessageKey(responseError(409, 'GROUP_NAME_DUPLICATE'), 'name')).toBe(
      'groupadmin.name.duplicate',
    )
    expect(failureMessageKey(responseError(400, 'VALIDATION_FAILED'), 'name')).toBe(
      'groupadmin.name.invalid',
    )
    expect(failureMessageKey(responseError(400, 'VALIDATION_FAILED'), 'addMember')).toBe(
      GENERAL_FAILURE_KEY,
    )
    expect(failureMessageKey(responseError(409, 'GROUP_BUSY'), 'removeMember')).toBe(
      'groupadmin.busy',
    )
    expect(failureMessageKey(responseError(404, 'GROUP_NOT_FOUND'), 'delete')).toBe(
      'groupadmin.group.missing',
    )
    expect(failureMessageKey(responseError(404, 'USER_NOT_FOUND'), 'addMember')).toBe(
      'groupadmin.user.missing',
    )
  })

  it('chooses the no-change message by the scene', () => {
    const noChange = responseError(409, 'GROUP_NO_CHANGE')
    expect(failureMessageKey(noChange, 'name')).toBe('groupadmin.name.noChange')
    expect(failureMessageKey(noChange, 'addMember')).toBe('groupadmin.member.already')
    expect(failureMessageKey(noChange, 'removeMember')).toBe('groupadmin.member.alreadyRemoved')
  })

  it('does not change the message by the detail or the title of the server', () => {
    const marked = responseError(409, 'GROUP_BUSY', { detail: 'MARK-DETAIL', title: 'MARK-TITLE' })
    expect(failureMessageKey(marked, 'name')).toBe('groupadmin.busy')
  })

  it('uses the general message for unknown codes, responses without a code and network errors', () => {
    expect(failureMessageKey(responseError(409, 'ROLE_BUSY'), 'name')).toBe(GENERAL_FAILURE_KEY)
    expect(failureMessageKey(responseError(500), 'delete')).toBe(GENERAL_FAILURE_KEY)
    expect(failureMessageKey(networkError(), 'addMember')).toBe(GENERAL_FAILURE_KEY)
  })

  it('reads the counts of GROUP_IN_USE only when both are integers', () => {
    expect(
      inUseCounts(responseError(409, 'GROUP_IN_USE', { members: 3, assignedRoles: 1 })),
    ).toEqual({
      members: 3,
      roles: 1,
    })
    expect(
      inUseCounts(responseError(409, 'GROUP_IN_USE', { members: -1, assignedRoles: 1 })),
    ).toBeUndefined()
    expect(inUseCounts(responseError(409, 'GROUP_IN_USE'))).toBeUndefined()
    expect(failureMessageKey(responseError(409, 'GROUP_IN_USE'), 'delete')).toBe(
      'groupadmin.inUse.noCount',
    )
  })
})
