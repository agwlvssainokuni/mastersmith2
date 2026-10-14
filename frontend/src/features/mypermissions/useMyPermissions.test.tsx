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
// S8 の状態と操作のテスト（Intent 261004-role-menu の Deployment Execution で見つかった不具合の再現）。
// テーブルの節に開閉のボタンを出さないこと（hasChildren を偽にする）と、テーブルの id で子の読み込みを呼ばれても
// 要求を出さないことを確かめる。木はスキーマとテーブルの2段で、カラムは右の表に出す（functional-spec.md の D24）。
import { renderHook, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { MyPermissionNode, MyPermissionNodes } from './myPermissionsApi'
import { useMyPermissions, type MyPermissionsApi } from './useMyPermissions'

const ROLE = { roleId: 1, name: '営業' }

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
// 本物の API と同じく、カラムがあるテーブルの節は hasChildren が真
const TABLE = item({ tableName: 'ORDER', displayName: '受注', hasChildren: true })

function fakeApi(): MyPermissionsApi & {
  listMyTables: ReturnType<typeof vi.fn<(schema: string) => Promise<MyPermissionNodes>>>
} {
  return {
    listMySchemas: vi.fn(() => Promise.resolve({ workRole: ROLE, items: [SCHEMA] })),
    listMyTables: vi.fn((_schema: string) => Promise.resolve({ workRole: ROLE, items: [TABLE] })),
    listMyColumns: vi.fn(() => Promise.resolve({ workRole: ROLE, items: [] })),
  }
}

describe('useMyPermissions', () => {
  it('keeps hasChildren on a schema node and turns it off on a table node', async () => {
    const api = fakeApi()
    const { result } = renderHook(() => useMyPermissions(api))
    await waitFor(() => expect(result.current.view).toBe('ready'))
    expect(result.current.nodes).toEqual([{ id: '["SALES"]', label: '販売', hasChildren: true }])
    const tables = await result.current.loadChildren('["SALES"]')
    expect(tables).toEqual([{ id: '["SALES","ORDER"]', label: '受注', hasChildren: false }])
    expect(api.listMyTables).toHaveBeenCalledExactlyOnceWith('SALES')
  })

  it('does not send a request when asked for the children of a table node', async () => {
    const api = fakeApi()
    const { result } = renderHook(() => useMyPermissions(api))
    await waitFor(() => expect(result.current.view).toBe('ready'))
    await expect(result.current.loadChildren('["SALES","ORDER"]')).resolves.toEqual([])
    expect(api.listMyTables).not.toHaveBeenCalled()
  })
})
