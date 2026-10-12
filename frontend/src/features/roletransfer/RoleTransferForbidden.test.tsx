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
// 画面と API の組の 403 の表と、英語の表示・漏えいのテスト（Intent 261004-role-menu の U6 の D2・D28・D3、NFR1.1・
// NFR1.6・NFR4.3）。書き出し・確かめ・適用の API が 403 ACCESS_DENIED を返すと useAdminForbidden に要求の道を添えて1回
// 渡し、画面は独自の文言を出さない。骨組みの中では権限なしの表示に置き換わる。en で文言がすべて英語。
import { screen, waitFor } from '@testing-library/react'
import userEvent, { type UserEvent } from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { registration } from './registration'
import { RoleTransferPage } from './RoleTransferPage'
import {
  apiError,
  checkOf,
  fakeRoleTransferApi,
  fileOf,
  renderRoleTransfer,
  type FakeRoleTransferApi,
} from './testing/fixtures'

const recorded = vi.hoisted(() => ({ paths: [] as string[] }))

vi.mock('../../app/admin-forbidden/AdminForbiddenProvider', async (importOriginal) => {
  const actual =
    await importOriginal<typeof import('../../app/admin-forbidden/AdminForbiddenProvider')>()
  return {
    ...actual,
    useAdminForbidden: () => {
      const handle = actual.useAdminForbidden()
      return (error: unknown, apiPath: string) => {
        recorded.paths.push(apiPath)
        return handle(error, apiPath)
      }
    },
  }
})

const FORBIDDEN = apiError(403, 'ACCESS_DENIED')

async function check(user: UserEvent) {
  await user.upload(await screen.findByLabelText(/ファイルを選ぶ|Choose a file/), fileOf())
  await user.click(screen.getByTestId('roletransfer-check'))
}

interface Row {
  name: string
  path: string
  arrange: (api: FakeRoleTransferApi) => void
  act: (user: UserEvent) => Promise<void>
}

const ROWS: Row[] = [
  {
    name: 'export',
    path: '/api/admin/role-transfer/export',
    arrange: (api) => api.exportRoles.mockRejectedValue(FORBIDDEN),
    act: async (user) => {
      await user.click(await screen.findByTestId('roletransfer-export'))
    },
  },
  {
    name: 'check',
    path: '/api/admin/role-transfer/check',
    arrange: (api) => api.checkTransfer.mockRejectedValue(FORBIDDEN),
    act: check,
  },
  {
    name: 'apply',
    path: '/api/admin/role-transfer/apply',
    arrange: (api) => {
      api.checkTransfer.mockResolvedValue(checkOf())
      api.applyTransfer.mockRejectedValue(FORBIDDEN)
    },
    act: async (user) => {
      await check(user)
      await user.click(await screen.findByTestId('roletransfer-apply'))
      await user.click(await screen.findByTestId('roletransfer-apply-submit'))
    },
  },
]

describe('roletransfer 403 table and English', () => {
  beforeEach(() => {
    recorded.paths = []
  })

  afterEach(() => {
    resetDisplayTestState()
    vi.restoreAllMocks()
  })

  it.each(ROWS)('$name passes the 403 with $path once', async (row) => {
    const user = userEvent.setup()
    const api = fakeRoleTransferApi()
    row.arrange(api)
    renderRoleTransfer(<RoleTransferPage api={api} />)
    await row.act(user)
    await waitFor(() => expect(recorded.paths).toContain(row.path))
    expect(recorded.paths.filter((path) => path === row.path)).toHaveLength(1)
    expect(screen.getByTestId('roletransfer-notice')).toBeEmptyDOMElement()
  })

  it('replaces the screen with the forbidden view inside the shell', async () => {
    const user = userEvent.setup()
    const api = fakeRoleTransferApi()
    api.exportRoles.mockRejectedValue(FORBIDDEN)
    renderRoleTransfer(<RoleTransferPage api={api} />, {
      withShell: true,
      registrations: [registration],
    })
    await user.click(await screen.findByTestId('roletransfer-export'))
    expect(await screen.findByTestId('admin-forbidden-view')).toBeInTheDocument()
    expect(screen.getByTestId('admin-forbidden-heading')).toHaveTextContent('権限の受け渡し')
  })

  it('shows the screen, the result table and the error list in English without leaking', async () => {
    const user = userEvent.setup()
    const consoleSpies = (['log', 'info', 'warn', 'error', 'debug'] as const).map((name) =>
      vi.spyOn(console, name),
    )
    const api = fakeRoleTransferApi()
    api.checkTransfer.mockResolvedValueOnce(checkOf()).mockRejectedValueOnce(
      apiError(400, 'ROLE_TRANSFER_INVALID', {
        errors: [{ reason: 'DUPLICATE_ROLE', line: 2, column: 1, path: 'roles.1' }],
      }),
    )
    renderRoleTransfer(<RoleTransferPage api={api} />, { languages: ['en-US'] })
    const setItem = vi.spyOn(Storage.prototype, 'setItem')
    await check(user)
    await user.click(await screen.findByRole('button', { name: 'Open the changes of 営業' }))
    const japanese = /[぀-ヿ㐀-鿿＀-￯]/u
    const strip = (text: string) =>
      ['営業', '総務', '経理'].reduce((acc, name) => acc.split(name).join(''), text)
    expect(strip(document.body.textContent ?? '')).not.toMatch(japanese)
    await user.click(screen.getByTestId('roletransfer-check'))
    expect(await screen.findByTestId('roletransfer-errors-alert')).toHaveTextContent(
      'The file has errors (1).',
    )
    expect(screen.getByTestId('roletransfer-errors')).toHaveTextContent(
      'The same role appears more than once',
    )
    expect(strip(document.body.textContent ?? '')).not.toMatch(japanese)
    for (const spy of consoleSpies) {
      expect(spy).not.toHaveBeenCalled()
    }
    expect(setItem).not.toHaveBeenCalled()
  })
})
