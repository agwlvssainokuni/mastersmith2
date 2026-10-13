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
// 自分の権限の画面のテスト（Intent 261004-role-menu の U7 の D24・D25・W8.1〜W8.3、計画 12節 D-3・D-5）。
// API は差し替え、作業ロールは偽の API の提供元で包む。
import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ToastProvider } from 'make-you-chic-ui'
import { useState } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { useDisplaySettings } from '../../app/display-settings/DisplaySettingsProvider'
import { ACCOUNTING, fakeWorkRoleApi, SALES } from '../../app/testing/frameTestSupport'
import {
  fakeProvider,
  renderWithProviders,
  resetDisplayTestState,
} from '../../app/testing/renderWithProviders'
import { WorkRoleProvider } from '../../app/work-role/WorkRoleProvider'
import { WorkRoleSwitcher } from '../../app/work-role/WorkRoleSwitcher'
import type { WorkRoleState } from '../../app/work-role/workRoleApi'
import type { MyPermissionNode, MyPermissionNodes } from './myPermissionsApi'
import { MyPermissionsPage } from './MyPermissionsPage'
import { registration } from './registration'
import type { MyPermissionsApi } from './useMyPermissions'

afterEach(() => {
  vi.unstubAllGlobals()
  resetDisplayTestState()
})

function item(extra: Partial<MyPermissionNode>): MyPermissionNode {
  return {
    schemaName: 'SALES',
    tableName: null,
    columnName: null,
    displayName: '販売',
    effective: { main: 'READ', create: false, delete: false },
    inMenu: false,
    hasChildren: true,
    ...extra,
  }
}

const SCHEMA = item({})
const TABLE = item({
  tableName: 'ORDER',
  displayName: '受注',
  effective: { main: 'FULL', create: true, delete: false },
  inMenu: true,
  hasChildren: false,
})
const COLUMN = item({
  tableName: 'ORDER',
  columnName: 'AMOUNT',
  displayName: '',
  effective: { main: 'NONE', create: false, delete: false },
  hasChildren: false,
})
const ROLE = { roleId: 1, name: '営業' }

function fakeApi(schemas: (MyPermissionNodes | Error)[]) {
  let index = 0
  const calls = { schemas: [] as string[], tables: [] as string[], columns: [] as string[] }
  const api: MyPermissionsApi = {
    listMySchemas: () => {
      const answer = schemas[Math.min(index, schemas.length - 1)]
      index += 1
      calls.schemas.push(String(index))
      return answer instanceof Error ? Promise.reject(answer) : Promise.resolve(answer)
    },
    listMyTables: (schema) => {
      calls.tables.push(schema)
      return Promise.resolve({ workRole: ROLE, items: [TABLE] })
    },
    listMyColumns: (schema, table) => {
      calls.columns.push(`${schema}.${table}`)
      return Promise.resolve({ workRole: ROLE, items: [COLUMN] })
    },
  }
  return { api, calls }
}

const TWO: WorkRoleState = { roles: [SALES, ACCOUNTING], current: SALES }

/** 表示の言語を替えるボタン */
function LanguageSwitch() {
  const settings = useDisplaySettings()
  const [done, setDone] = useState(false)
  return (
    <button
      type="button"
      onClick={() => {
        settings.applyUserPreferences({
          displayName: '利用 花子',
          language: 'en',
          theme: 'light',
          fontSize: 'md',
        })
        setDone(true)
      }}
    >
      {done ? 'english' : 'to-english'}
    </button>
  )
}

function renderPage(api: MyPermissionsApi, roles: WorkRoleState[] = [TWO]) {
  const role = fakeWorkRoleApi(roles)
  return renderWithProviders(
    <ToastProvider>
      <WorkRoleProvider api={role.api}>
        <header>
          <WorkRoleSwitcher />
          <LanguageSwitch />
        </header>
        <main>
          <MyPermissionsPage api={api} />
        </main>
      </WorkRoleProvider>
    </ToastProvider>,
    {
      route: '/me/permissions',
      registrations: [registration],
      provider: fakeProvider({ loggedIn: true, admin: false }),
    },
  )
}

const ready: MyPermissionNodes = { workRole: ROLE, items: [SCHEMA] }

describe('MyPermissionsPage', () => {
  it('shows the heading, the current work role and the schemas in the tree', async () => {
    const { api } = fakeApi([ready])
    renderPage(api)
    expect(await screen.findByRole('heading', { level: 1, name: '自分の権限' })).toBeInTheDocument()
    expect(await screen.findByText('今の作業ロール: 営業')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '販売' })).toBeInTheDocument()
    expect(screen.getByText('左の木から対象を選んでください。')).toBeInTheDocument()
  })

  it('opens a schema, selects a table and shows its values and the column table with text', async () => {
    const user = userEvent.setup()
    const { api, calls } = fakeApi([ready])
    renderPage(api)
    await user.click(await screen.findByRole('button', { name: '販売を開く' }))
    await user.click(await screen.findByRole('button', { name: '受注' }))
    const target = await screen.findByTestId('mypermissions-target')
    expect(
      within(target)
        .getAllByRole('cell')
        .map((cell) => cell.textContent),
    ).toEqual(['FULL（見られる・直せる）', '可', '不可', '出る'])
    const columns = await screen.findByTestId('mypermissions-columns')
    expect(within(columns).getByRole('rowheader')).toHaveTextContent('AMOUNT')
    expect(within(columns).getByRole('cell')).toHaveTextContent('NONE（見られない）')
    expect(calls.tables).toEqual(['SALES'])
    expect(calls.columns).toEqual(['SALES.ORDER'])
  })

  it('shows the schema values without the business menu column', async () => {
    const user = userEvent.setup()
    const { api } = fakeApi([ready])
    renderPage(api)
    await user.click(await screen.findByRole('button', { name: '販売' }))
    const target = await screen.findByTestId('mypermissions-target')
    expect(
      within(target)
        .getAllByRole('cell')
        .map((cell) => cell.textContent),
    ).toEqual(['READ（見られる・直せない）', '不可', '不可'])
  })

  it('tells when there is no work role and when there is nothing to show', async () => {
    const first = fakeApi([{ workRole: null, items: [SCHEMA] }])
    const { unmount } = renderPage(first.api)
    expect(
      await screen.findByText('作業ロールがありません。すべて見られません（NONE・不可）。'),
    ).toBeInTheDocument()
    unmount()
    const second = fakeApi([{ workRole: ROLE, items: [] }])
    renderPage(second.api)
    expect(await screen.findByText('対象がありません。')).toBeInTheDocument()
  })

  it('shows a load error and reads again with Try again', async () => {
    const user = userEvent.setup()
    const { api } = fakeApi([new Error('down'), ready])
    renderPage(api)
    await user.click(await screen.findByRole('button', { name: 'もう一度' }))
    expect(await screen.findByRole('button', { name: '販売' })).toBeInTheDocument()
  })

  it('reads again from the top and drops the selection after a work role switch', async () => {
    const user = userEvent.setup()
    const { api, calls } = fakeApi([ready])
    renderPage(api, [TWO, { ...TWO, current: ACCOUNTING }])
    await user.click(await screen.findByRole('button', { name: '販売' }))
    await screen.findByTestId('mypermissions-target')
    await user.click(screen.getByRole('button', { name: '作業ロール: 営業' }))
    await user.click(screen.getByRole('menuitem', { name: '経理' }))
    await waitFor(() => expect(calls.schemas).toHaveLength(2))
    expect(await screen.findByText('左の木から対象を選んでください。')).toBeInTheDocument()
    expect(screen.queryByTestId('mypermissions-target')).toBeNull()
  })

  it('reads again from the top when the display language changes', async () => {
    const user = userEvent.setup()
    const { api, calls } = fakeApi([ready])
    renderPage(api)
    await screen.findByRole('button', { name: '販売' })
    await user.click(screen.getByRole('button', { name: 'to-english' }))
    await waitFor(() => expect(calls.schemas).toHaveLength(2))
    expect(
      await screen.findByRole('heading', { level: 1, name: 'My permissions' }),
    ).toBeInTheDocument()
  })

  it('stacks vertically below 768px and moves the focus to the values after a selection', async () => {
    const user = userEvent.setup()
    vi.stubGlobal(
      'matchMedia',
      vi.fn(() => ({ matches: true, addEventListener: () => {}, removeEventListener: () => {} })),
    )
    const { api } = fakeApi([ready])
    renderPage(api)
    expect(await screen.findByTestId('mypermissions-layout')).toHaveClass('mypermissions-layout')
    await user.click(await screen.findByRole('button', { name: '販売' }))
    await waitFor(() =>
      expect(screen.getByRole('heading', { level: 2, name: '選んだ対象の権限' })).toHaveFocus(),
    )
  })

  it('has no accessibility violations with the tree open and the column table shown', async () => {
    const user = userEvent.setup()
    const { api } = fakeApi([ready])
    const { container } = renderPage(api)
    await user.click(await screen.findByRole('button', { name: '販売を開く' }))
    await user.click(await screen.findByRole('button', { name: '受注' }))
    await screen.findByTestId('mypermissions-columns')
    await act(async () => {
      await Promise.resolve()
    })
    expect(await axe(container)).toHaveNoViolations()
  })
})
