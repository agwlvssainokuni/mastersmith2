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
// S4 の右の表のテスト（Intent 261004-role-menu の U6 の D11・D13・D28、NFR4.1・NFR4.3）。Select の名前に対象が入る、
// 補助権限の欄はスキーマとテーブルだけ、実効の値は文字（継承の元の表示名・無ければ物理名）、写しの値が Select に出る、
// 名前は文字として描く、busy で押せない、カラムの読み込みの状態を確かめる。
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { applyChange, EMPTY_DRAFT, type PermissionDraft } from './permissionDraft'
import { PermissionTable, type PermissionTableProps } from './PermissionTable'
import { columnNodeOf, explicitOf, nodeOf, SCRIPT_NAME, tableNodeOf } from './testing/fixtures'
import { renderRoleAdmin } from './testing/renderRoleAdmin'

async function renderTable(props: Partial<PermissionTableProps> = {}) {
  const onChange = vi.fn()
  const onClearOrphan = vi.fn()
  const onRetryColumns = vi.fn()
  const view = renderRoleAdmin(
    <PermissionTable
      node={nodeOf()}
      columns={{ phase: 'idle', items: [] }}
      draft={EMPTY_DRAFT}
      busy={false}
      names={{ schema: '販売DB' }}
      onChange={onChange}
      onClearOrphan={onClearOrphan}
      onRetryColumns={onRetryColumns}
      {...props}
    />,
  )
  await screen.findByTestId('roleadmin-perm-table')
  return { ...view, onChange, onClearOrphan, onRetryColumns }
}

describe('PermissionTable', () => {
  afterEach(() => {
    resetDisplayTestState()
    vi.restoreAllMocks()
  })

  it('names each select after its target and reports a change with the field and value', async () => {
    const user = userEvent.setup()
    const { onChange } = await renderTable()
    await user.selectOptions(screen.getByRole('combobox', { name: '販売DBの CREATE' }), 'false')
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ schemaName: 'SALES' }),
      'create',
      false,
    )
    await user.selectOptions(screen.getByRole('combobox', { name: '販売DBの主権限' }), '')
    expect(onChange).toHaveBeenLastCalledWith(expect.anything(), 'main', null)
    expect(
      within(screen.getByRole('combobox', { name: '販売DBの主権限' }))
        .getAllByRole('option')
        .map((option) => option.textContent),
    ).toEqual(['設定なし', 'NONE', 'READ', 'FULL'])
  })

  it('shows the draft value in the select and marks the row unsaved', async () => {
    const node = nodeOf()
    const draft: PermissionDraft = applyChange(
      EMPTY_DRAFT,
      node.explicit,
      node,
      explicitOf({ main: 'FULL' }),
    )
    await renderTable({ node, draft })
    expect(screen.getByRole('combobox', { name: '販売DBの主権限' })).toHaveValue('FULL')
    expect(screen.getByTestId('roleadmin-node-row')).toHaveTextContent('（未保存）')
  })

  it('uses the physical name when the parent display name is missing, and shows the default source', async () => {
    await renderTable({
      node: tableNodeOf({
        inheritedFrom: 'SCHEMA',
        effective: { main: 'FULL', create: true, delete: true },
      }),
      names: {},
      columns: {
        phase: 'loaded',
        items: [
          columnNodeOf({
            inheritedFrom: 'DEFAULT',
            effective: { main: 'NONE', create: false, delete: false },
          }),
        ],
      },
    })
    expect(screen.getByTestId('roleadmin-node-row')).toHaveTextContent(
      '主権限: FULL（スキーマ SALES から継承）',
    )
    expect(screen.getByTestId('roleadmin-column-ORDER_NO')).toHaveTextContent(
      '主権限: NONE（既定）',
    )
    expect(screen.getByTestId('roleadmin-node-row')).toHaveTextContent('ORDER_LINE')
  })

  it('shows the column loading, empty and failed states, and retries', async () => {
    const user = userEvent.setup()
    const loading = await renderTable({
      node: tableNodeOf(),
      columns: { phase: 'loading', items: [] },
    })
    expect(screen.getByText('カラムを読み込んでいます…')).toBeInTheDocument()
    loading.unmount()
    const empty = await renderTable({
      node: tableNodeOf(),
      columns: { phase: 'loaded', items: [] },
    })
    expect(screen.getByText('カラムがありません。')).toBeInTheDocument()
    empty.unmount()
    const { onRetryColumns } = await renderTable({
      node: tableNodeOf(),
      columns: { phase: 'failed', items: [] },
    })
    await user.click(
      within(screen.getByTestId('roleadmin-columns-error')).getByRole('button', {
        name: 'もう一度',
      }),
    )
    expect(onRetryColumns).toHaveBeenCalledTimes(1)
  })

  it('disables every select while busy', async () => {
    await renderTable({ busy: true })
    for (const select of screen.getAllByRole('combobox')) {
      expect(select).toBeDisabled()
    }
  })

  it('draws names that look like markup as text, and offers clear for an orphan column', async () => {
    const user = userEvent.setup()
    const orphan = columnNodeOf({ displayName: SCRIPT_NAME, inCurrentDsl: false })
    const { onClearOrphan } = await renderTable({
      node: tableNodeOf({ displayName: SCRIPT_NAME }),
      columns: { phase: 'loaded', items: [orphan] },
    })
    expect(
      screen.getByRole('heading', { name: `テーブルの設定: ${SCRIPT_NAME}` }),
    ).toBeInTheDocument()
    expect(document.querySelector('script')).toBeNull()
    const row = screen.getByTestId('roleadmin-column-ORDER_NO')
    expect(row).toHaveTextContent('⚠ 今の DSL に無い')
    await user.click(within(row).getByRole('button', { name: '設定を消す' }))
    expect(onClearOrphan).toHaveBeenCalledWith(orphan)
  })

  it('has no accessibility violations for a table with columns', async () => {
    const view = await renderTable({
      node: tableNodeOf(),
      columns: {
        phase: 'loaded',
        items: [columnNodeOf(), columnNodeOf({ columnName: 'QTY', displayName: '数量' })],
      },
    })
    expect(await axe(view.container)).toHaveNoViolations()
  })
})
