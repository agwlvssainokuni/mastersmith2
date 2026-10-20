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
// S6 開けない・つながらないときの表示のテスト（Intent 261016-business-data-views の U3 の L7、D6・F8、機能設計の 6.1）。
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { ACCOUNTING, SALES } from '../../app/testing/frameTestSupport'
import {
  fakeProvider,
  renderWithProviders,
  resetDisplayTestState,
} from '../../app/testing/renderWithProviders'
import { WorkRoleSwitcher } from '../../app/work-role/WorkRoleSwitcher'
import {
  BusinessDataProblemView,
  type BusinessDataProblemViewProps,
} from './BusinessDataProblemView'
import { registration } from './registration'
import { ONE_ROLE, tableFrame } from './testing/tableProviders'

afterEach(() => resetDisplayTestState())

function renderView(props: BusinessDataProblemViewProps, roles = [ONE_ROLE]) {
  const frame = tableFrame(roles)
  return renderWithProviders(
    <frame.Frame>
      <header>
        <WorkRoleSwitcher />
      </header>
      <main>
        <BusinessDataProblemView {...props} />
      </main>
    </frame.Frame>,
    { registrations: [registration], provider: fakeProvider({ loggedIn: true, admin: false }) },
  )
}

describe('BusinessDataProblemView', () => {
  it('shows the forbidden text with the work role and a way to switch it', async () => {
    const user = userEvent.setup()
    renderView({ kind: 'forbidden' }, [{ roles: [SALES, ACCOUNTING], current: SALES }])
    expect(
      await screen.findByRole('heading', {
        level: 1,
        name: 'このテーブルを見る権限がありません。作業ロールを確かめてください。',
      }),
    ).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText('今の作業ロール: 営業')).toBeInTheDocument())
    await user.click(screen.getByRole('button', { name: '作業ロールを切り替える' }))
    expect(screen.getByRole('button', { name: '作業ロール: 営業' })).toHaveFocus()
    expect(screen.getByRole('link', { name: 'ホームへ' })).toHaveAttribute('href', '/')
  })

  it('offers no switch with a single work role', async () => {
    renderView({ kind: 'forbidden' })
    await waitFor(() => expect(screen.getByText('今の作業ロール: 営業')).toBeInTheDocument())
    expect(screen.queryByRole('button', { name: '作業ロールを切り替える' })).toBeNull()
    expect(screen.getByText('メニューからテーブルを選び直してください。')).toBeInTheDocument()
  })

  it('offers a retry for an unreachable database and a way back to the list for a missing row', async () => {
    const user = userEvent.setup()
    const onRetry = vi.fn()
    const { unmount } = renderView({ kind: 'unavailable', onRetry })
    await user.click(await screen.findByRole('button', { name: 'もう一度' }))
    expect(onRetry).toHaveBeenCalledTimes(1)
    unmount()
    renderView({ kind: 'row-not-found', listPath: '/tables?schema=s&table=t' })
    expect(await screen.findByRole('link', { name: '一覧へ戻る' })).toHaveAttribute(
      'href',
      '/tables?schema=s&table=t',
    )
    expect(screen.queryByRole('button', { name: 'もう一度' })).toBeNull()
  })

  it('shows the home link for a missing table and a missing DSL', async () => {
    const { unmount } = renderView({ kind: 'table-not-found' })
    expect(
      await screen.findByRole('heading', { name: 'テーブルが見つかりません。' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'ホームへ' })).toBeInTheDocument()
    unmount()
    renderView({ kind: 'dsl-not-applied' })
    expect(
      await screen.findByRole('heading', {
        name: 'いまは業務データを使える状態ではありません。管理者に確かめてください。',
      }),
    ).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = renderView({ kind: 'forbidden' })
    await waitFor(() => expect(screen.getByText('今の作業ロール: 営業')).toBeInTheDocument())
    expect(await axe(container)).toHaveNoViolations()
  })
})
