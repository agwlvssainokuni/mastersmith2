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
// フォーカスの行き先のテスト（frontend-components.md の 3.3、W7〜W9）。
import { describe, expect, it } from 'vitest'
import { resolveFocusTarget, type FocusContext } from './focusTarget'

function context(overrides: Partial<FocusContext> = {}): FocusContext {
  return {
    rowIds: [11, 12],
    total: 2,
    invitationEnabled: true,
    resendingIds: new Set(),
    ...overrides,
  }
}

describe('resolveFocusTarget', () => {
  it('focuses the resend button when the row is there and can be pressed', () => {
    expect(resolveFocusTarget({ kind: 'resend', invitationId: 12 }, context())).toEqual({
      kind: 'resend',
      invitationId: 12,
    })
  })

  it('falls back to the email cell when resend cannot be pressed', () => {
    expect(
      resolveFocusTarget(
        { kind: 'resend', invitationId: 12 },
        context({ invitationEnabled: false }),
      ),
    ).toEqual({ kind: 'email', invitationId: 12 })
    expect(
      resolveFocusTarget(
        { kind: 'resend', invitationId: 11 },
        context({ resendingIds: new Set([11]) }),
      ),
    ).toEqual({ kind: 'email', invitationId: 11 })
    expect(resolveFocusTarget({ kind: 'email', invitationId: 11 }, context())).toEqual({
      kind: 'email',
      invitationId: 11,
    })
  })

  it('falls back to the list heading when the row is gone', () => {
    expect(resolveFocusTarget({ kind: 'resend', invitationId: 99 }, context())).toEqual({
      kind: 'heading',
    })
    expect(resolveFocusTarget({ kind: 'email', invitationId: 99 }, context())).toEqual({
      kind: 'heading',
    })
  })

  it('focuses the heading when asked and there are rows', () => {
    expect(resolveFocusTarget({ kind: 'heading' }, context())).toEqual({ kind: 'heading' })
    expect(resolveFocusTarget({ kind: 'empty' }, context())).toEqual({ kind: 'heading' })
  })

  it('focuses the empty message when the list is empty', () => {
    const empty = context({ rowIds: [], total: 0 })
    expect(resolveFocusTarget({ kind: 'heading' }, empty)).toEqual({ kind: 'empty' })
    expect(resolveFocusTarget({ kind: 'resend', invitationId: 11 }, empty)).toEqual({
      kind: 'empty',
    })
    expect(resolveFocusTarget({ kind: 'email', invitationId: 11 }, empty)).toEqual({
      kind: 'empty',
    })
  })
})
