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
// 利用者のロールの読み取りの Modal のテスト（Intent 261004-role-menu の U6 の D27、W9.1、AC2.2.7）。題に氏名、読み込み中・
// 出どころの並び・空・USER_NOT_FOUND・ほかの失敗、403 は骨組みに渡して文言を出さない、ロールの一覧へのリンク、閉じる、
// 名前が文字のまま出ることを確かめる。
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import type { ApiError } from '../../shared/api-client/apiError'
import type { UserRole } from './api/types'
import { userAdminApi, type UserAdminApi } from './api/userAdminApi'
import { userOf } from './testing/fixtures'
import { renderUserAdmin } from './testing/renderUserAdmin'
import { UserRolesDialog } from './UserRolesDialog'

function apiError(status: number, code?: string): ApiError {
  return code !== undefined ? { kind: 'response', status, code } : { kind: 'response', status }
}

async function renderDialog(answer: Promise<UserRole[]>, reportForbidden = vi.fn(() => false)) {
  const api: UserAdminApi = { ...userAdminApi, getUserRoles: vi.fn(() => answer) }
  const onClose = vi.fn()
  const view = renderUserAdmin(
    <UserRolesDialog
      user={userOf({ userId: 12, displayName: '鈴木 次郎' })}
      api={api}
      reportForbidden={reportForbidden}
      onClose={onClose}
    />,
  )
  await screen.findByTestId('useradmin-roles-dialog')
  return { ...view, api, onClose, reportForbidden }
}

describe('UserRolesDialog', () => {
  afterEach(() => {
    resetDisplayTestState()
    vi.restoreAllMocks()
  })

  it('titles the dialog with the name, shows loading, then the roles with their sources', async () => {
    let resolve!: (value: UserRole[]) => void
    const { api } = await renderDialog(new Promise((done) => (resolve = done)))
    expect(screen.getByRole('dialog', { name: '鈴木 次郎さんのロール' })).toBeInTheDocument()
    expect(screen.getByText('ロールを読み込んでいます…')).toHaveAttribute('role', 'status')
    resolve([
      {
        roleId: 5,
        name: '営業',
        sources: [{ kind: 'DIRECT' }, { kind: 'GROUP', groupId: 3, name: '営業部' }],
      },
      { roleId: 6, name: '経理', sources: [{ kind: 'GROUP', groupId: 4, name: '経理部' }] },
    ])
    const list = await screen.findByRole('list', { name: 'ロールの一覧' })
    expect(list).toHaveTextContent('営業（直接・グループ 営業部）経理（グループ 経理部）')
    expect(api.getUserRoles).toHaveBeenCalledTimes(1)
    expect(api.getUserRoles).toHaveBeenCalledWith(12)
    expect(screen.getByRole('link', { name: 'ロールの一覧へ' })).toHaveAttribute(
      'href',
      '/admin/roles',
    )
  })

  it('says there are no roles when the list is empty', async () => {
    await renderDialog(Promise.resolve([]))
    expect(await screen.findByTestId('useradmin-roles-empty')).toHaveTextContent(
      'ロールはありません。',
    )
  })

  it('shows USER_NOT_FOUND and other failures in text', async () => {
    const missing = await renderDialog(Promise.reject(apiError(404, 'USER_NOT_FOUND')))
    expect(await screen.findByTestId('useradmin-roles-failed')).toHaveTextContent(
      'この利用者はいません。',
    )
    missing.unmount()
    await renderDialog(Promise.reject(apiError(500)))
    expect(await screen.findByTestId('useradmin-roles-failed')).toHaveTextContent(
      '読み込めませんでした。もう一度お試しください。',
    )
  })

  it('passes a 403 to the shell and shows no message of its own', async () => {
    const reportForbidden = vi.fn(() => true)
    await renderDialog(Promise.reject(apiError(403, 'ACCESS_DENIED')), reportForbidden)
    await waitFor(() =>
      expect(reportForbidden).toHaveBeenCalledWith(expect.anything(), '/api/admin/users/12/roles'),
    )
    expect(screen.queryByTestId('useradmin-roles-failed')).toBeNull()
  })

  it('closes from the close button and draws names that look like markup as text', async () => {
    const user = userEvent.setup()
    const { onClose } = await renderDialog(
      Promise.resolve([
        { roleId: 5, name: '<script>alert(1)</script>', sources: [{ kind: 'DIRECT' }] },
      ]),
    )
    expect(await screen.findByText('<script>alert(1)</script>（直接）')).toBeInTheDocument()
    expect(document.querySelector('script')).toBeNull()
    await user.click(screen.getByTestId('useradmin-roles-close'))
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('has no accessibility violations with roles shown', async () => {
    await renderDialog(
      Promise.resolve([{ roleId: 5, name: '営業', sources: [{ kind: 'DIRECT' }] }]),
    )
    await screen.findByRole('list')
    expect(await axe(document.body)).toHaveNoViolations()
  })
})
