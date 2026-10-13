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
// 権限の受け渡しの画面のテストの値と描き方（テストからだけ使う。画面からは読み込まない）。値はテストごとに関数で作る。
// ファイルは大きさだけを持つ見本（Object.defineProperty で size を与える）で上限（5 MiB）の境界を作り、本文を実際に
// 上限の大きさで作らない（計画 unit-test-instructions.md の 6節）。ロールの名前に HTML に見える記号を含むものを入れる。
import type { RenderResult } from '@testing-library/react'
import { ModalStackProvider, ToastProvider } from 'make-you-chic-ui'
import type { ReactElement } from 'react'
import { vi, type Mock } from 'vitest'
import { ShellLayout } from '../../../app/layout/ShellLayout'
import type { FeatureRegistration } from '../../../app/registry/types'
import { fakeProvider, renderWithProviders } from '../../../app/testing/renderWithProviders'
import type { ApiError } from '../../../shared/api-client/apiError'
import type { RoleTransferApi } from '../api/roleTransferApi'
import type { TransferChange, TransferCheck, TransferRolePlan } from '../api/types'
import { roleTransferMessages } from '../messages'
import { ROLE_TRANSFER_PATH } from '../registration'

/** 画面に出てはいけない目印（応答の detail・title に入れる） */
export const DETAIL_MARKER = 'role-transfer-detail-marker'

/** スクリプトのタグに見える名前 */
export const SCRIPT_NAME = '<script>alert(1)</script>'

/** 失敗の値（detail と title に目印を入れる） */
export function apiError(
  status: number,
  code?: string,
  extra: Readonly<Record<string, unknown>> = {},
): ApiError {
  const error: ApiError =
    code !== undefined ? { kind: 'response', status, code } : { kind: 'response', status }
  Object.defineProperty(error, 'problem', {
    value: { status, code, detail: DETAIL_MARKER, title: DETAIL_MARKER, ...extra },
    enumerable: false,
  })
  return error
}

/** 変わる点の1件 */
export function changeOf(overrides: Partial<TransferChange> = {}): TransferChange {
  return {
    target: { schemaName: 'SALES', tableName: 'ORDER_LINE', columnName: null },
    before: { main: 'READ', create: null, delete: null },
    after: { main: null, create: null, delete: null },
    reason: 'CLEARED',
    inCurrentDsl: true,
    ...overrides,
  }
}

/** ロールの計画 */
export function planOf(overrides: Partial<TransferRolePlan> = {}): TransferRolePlan {
  return {
    name: '営業',
    roleId: 5,
    action: 'REPLACE',
    added: 1,
    changed: 0,
    cleared: 1,
    notInDsl: 0,
    changes: [
      changeOf({
        reason: 'ADDED',
        before: { main: null, create: null, delete: null },
        after: { main: 'FULL', create: true, delete: null },
      }),
      changeOf(),
    ],
    ...overrides,
  }
}

/** 確かめの結果 */
export function checkOf(overrides: Partial<TransferCheck> = {}): TransferCheck {
  return {
    fingerprint: 'fp-1',
    roles: [
      planOf(),
      planOf({ name: '総務', roleId: null, action: 'CREATE', added: 2, cleared: 0, changes: [] }),
    ],
    untouchedRoles: ['経理'],
    ...overrides,
  }
}

/** 本文と大きさを持つファイル（size を与えると本文の長さによらずその大きさにする） */
export function fileOf(text = 'version: 1\nroles: []\n', name = 'roles.yaml', size?: number): File {
  const file = new File([text], name, { type: 'application/yaml' })
  if (size !== undefined) {
    Object.defineProperty(file, 'size', { value: size })
  }
  Object.defineProperty(file, 'text', { value: () => Promise.resolve(text) })
  return file
}

/** 差し替えた API */
export type FakeRoleTransferApi = { [K in keyof RoleTransferApi]: Mock<RoleTransferApi[K]> }

/** 差し替えた API を作る。決めていない関数が呼ばれたら通信の失敗にする。 */
export function fakeRoleTransferApi(overrides: Partial<RoleTransferApi> = {}): FakeRoleTransferApi {
  const fail = () => Promise.reject({ kind: 'network' })
  return {
    exportRoles: vi.fn(overrides.exportRoles ?? fail),
    checkTransfer: vi.fn(overrides.checkTransfer ?? fail),
    applyTransfer: vi.fn(overrides.applyTransfer ?? fail),
  } as FakeRoleTransferApi
}

/** 文言だけを持つ登録 */
export const roleTransferMessagesRegistration: FeatureRegistration = {
  featureId: 'roletransfer',
  messages: roleTransferMessages,
}

/** 描き方の選択 */
export interface RenderRoleTransferOptions {
  languages?: readonly string[]
  registrations?: readonly FeatureRegistration[]
  withShell?: boolean
}

/** 部品を描く（MemoryRouter）。 */
export function renderRoleTransfer(
  ui: ReactElement,
  options: RenderRoleTransferOptions = {},
): RenderResult {
  const {
    languages = ['ja-JP'],
    registrations = [roleTransferMessagesRegistration],
    withShell = false,
  } = options
  const content = withShell ? <ShellLayout>{ui}</ShellLayout> : ui
  return renderWithProviders(
    <ToastProvider>
      <ModalStackProvider>{content}</ModalStackProvider>
    </ToastProvider>,
    {
      route: ROLE_TRANSFER_PATH,
      registrations,
      languages,
      provider: fakeProvider({ loggedIn: true, admin: true, displayName: '管理 一郎' }),
    },
  )
}
