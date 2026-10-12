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
// 140・150 が使う U6 の画面の見本の作り方（Intent 261004-role-menu の U6、計画 4.6・9節、tech-stack-decisions.md NFR4.4・
// NFR6.10、performance-design.md 4.1）。見本は関数で作り、画面の側の型（各機能の api/types.ts）を import type で付ける。
// 画面の型と応答の形が違う所（出どころの type、DIRECT の groupId・name は null）は、応答の形（Wire）を型で書く。
// 2語の氏名（実在しそうでない架空の名前）・64 文字のロール名・長いメールアドレス（example.com）・< と & を含む名前を入れる。
// 測りの見本（100・1,000 カラム、25 ロール × 1万の変わる点）も作る。値は名前と数だけで、秘密を持たない。
import type {
  Group,
  GroupDetail,
  GroupPage,
  GroupRoleRef,
} from '../../src/features/groupadmin/api/types'
import type {
  AssignedGroup,
  AssignedUser,
  PermissionNode,
  Role,
  RolePage,
} from '../../src/features/roleadmin/api/types'
import type {
  TransferChange,
  TransferCheck,
  TransferErrorItem,
  TransferRolePlan,
} from '../../src/features/roletransfer/api/types'
import type { AdminUserPageWire, AdminUserWire } from './userAdminFixtures'

/** 出どころの応答の形（role の RoleSourceResponse。DIRECT の groupId・name は null。計画 2.3 の A9） */
export type SourceWire =
  { type: 'DIRECT'; groupId: null; name: null } | { type: 'GROUP'; groupId: number; name: string }

/** 割り当てた利用者の応答の形 */
export type AssignedUserWire = Omit<AssignedUser, 'sources'> & { sources: SourceWire[] }

/** 割り当ての応答の形 */
export interface RoleAssignmentsWire {
  users: AssignedUserWire[]
  groups: AssignedGroup[]
}

/** 利用者のロールの応答の形 */
export interface UserRoleWire {
  roleId: number
  name: string
  sources: SourceWire[]
}

/** 木の応答の形（外側の items。計画 2.3 の A3） */
export interface PermissionNodesWire {
  items: PermissionNode[]
}

/** 見本のロールの ID（S4・S5 を開く） */
export const SAMPLE_ROLE_ID = 7
/** 見本のグループの ID（S6 の詳細を開く） */
export const SAMPLE_GROUP_ID = 3
/** 見本の利用者の ID（直接だけで割り当てた利用者。D19 の確かめが出る） */
export const DIRECT_USER_ID = 21
/** 見本のスキーマとテーブル */
export const SAMPLE_SCHEMA = 'SALES'
export const SAMPLE_TABLE = 'ORDER_LINE'
/** 2語の氏名（架空） */
export const TWO_WORD_NAME = '検査 花子'
/** 64 文字のロール名 */
export const LONG_ROLE_NAME = `検査用の長いロールの名前${'あ'.repeat(52)}`
/** < と & を含む名前 */
export const SYMBOL_NAME = '営業 <見積> & 受注'
/** 長いメールアドレス */
export const LONG_EMAIL = `${'very.long.mail.address.for.wrapping.check'.repeat(3)}@example.com`

const DIRECT: SourceWire = { type: 'DIRECT', groupId: null, name: null }

function groupSource(groupId: number, name: string): SourceWire {
  return { type: 'GROUP', groupId, name }
}

/** ロール1件 */
export function roleWire(roleId = SAMPLE_ROLE_ID, name = '営業'): Role {
  return { roleId, name, createdAt: '2026-10-05T00:00:00Z', updatedAt: '2026-10-05T00:00:00Z' }
}

/** ロールの一覧（64 文字・記号の名前を含む） */
export function roleListWire(): RolePage {
  const items = [
    { roleId: SAMPLE_ROLE_ID, name: '営業', userCount: 2, groupCount: 1 },
    { roleId: 8, name: LONG_ROLE_NAME, userCount: 12, groupCount: 3 },
    { roleId: 9, name: SYMBOL_NAME, userCount: 0, groupCount: 0 },
  ]
  return { items, page: 1, size: 20, total: items.length }
}

function nodeOf(overrides: Partial<PermissionNode>): PermissionNode {
  return {
    schemaName: SAMPLE_SCHEMA,
    tableName: null,
    columnName: null,
    displayName: '販売DB',
    explicit: { main: null, create: null, delete: null },
    effective: { main: 'NONE', create: false, delete: false },
    inheritedFrom: 'DEFAULT',
    inMenu: false,
    inCurrentDsl: true,
    hasChildren: true,
    ...overrides,
  }
}

/** スキーマの段（1つ） */
export function schemaNodesWire(): PermissionNodesWire {
  return {
    items: [
      nodeOf({
        explicit: { main: 'READ', create: null, delete: null },
        effective: { main: 'READ', create: false, delete: false },
        inheritedFrom: 'EXPLICIT',
      }),
    ],
  }
}

/** テーブルの段（今の DSL に無いテーブルを1つ含む） */
export function tableNodesWire(): PermissionNodesWire {
  return {
    items: [
      nodeOf({
        tableName: SAMPLE_TABLE,
        displayName: '受注明細',
        effective: { main: 'READ', create: false, delete: false },
        inheritedFrom: 'SCHEMA',
        inMenu: true,
      }),
      nodeOf({
        tableName: 'OLD_TABLE',
        displayName: 'OLD_TABLE',
        explicit: { main: 'FULL', create: null, delete: null },
        effective: { main: 'FULL', create: false, delete: false },
        inheritedFrom: 'EXPLICIT',
        inCurrentDsl: false,
      }),
    ],
  }
}

/** カラムの名前（C001 から） */
export function columnName(index: number): string {
  return `C${String(index + 1).padStart(4, '0')}`
}

/** カラムの段（count 列。最後の列は今の DSL に無い印を付けるか選べる） */
export function columnNodesWire(count = 5, orphanLast = true): PermissionNodesWire {
  return {
    items: Array.from({ length: count }, (_, index) =>
      nodeOf({
        tableName: SAMPLE_TABLE,
        columnName: columnName(index),
        displayName: index === 0 ? '受注番号' : `カラム${index + 1}`,
        hasChildren: false,
        effective: { main: 'READ', create: false, delete: false },
        inheritedFrom: 'TABLE',
        inCurrentDsl: !(orphanLast && index === count - 1),
      }),
    ),
  }
}

/** 割り当て（直接だけの利用者・直接とグループの利用者・利用停止の利用者・グループ） */
export function assignmentsWire(): RoleAssignmentsWire {
  return {
    users: [
      {
        userId: DIRECT_USER_ID,
        displayName: TWO_WORD_NAME,
        email: LONG_EMAIL,
        suspended: false,
        sources: [DIRECT],
      },
      {
        userId: 22,
        displayName: '検査 次郎',
        email: 'kensa.jiro@example.com',
        suspended: false,
        sources: [DIRECT, groupSource(SAMPLE_GROUP_ID, SYMBOL_NAME)],
      },
      {
        userId: 23,
        displayName: '検査 三郎',
        email: 'kensa.saburo@example.com',
        suspended: true,
        sources: [groupSource(SAMPLE_GROUP_ID, SYMBOL_NAME)],
      },
    ],
    groups: [{ groupId: SAMPLE_GROUP_ID, name: SYMBOL_NAME }],
  }
}

/** 利用者のロール（直接だけ。外すとこのロールが無くなり、残りの最初は 経理） */
export function userRolesWire(): UserRoleWire[] {
  return [
    { roleId: 5, name: '経理', sources: [groupSource(4, '経理部')] },
    { roleId: SAMPLE_ROLE_ID, name: '営業', sources: [DIRECT] },
  ]
}

function candidateOf(overrides: Partial<AdminUserWire>): AdminUserWire {
  return {
    userId: 30,
    email: 'kensa.shiro@example.com',
    displayName: '検査 四郎',
    language: 'ja',
    admin: false,
    suspended: false,
    locked: false,
    lockedUntil: null,
    resettable: false,
    registeredAt: '2026-09-25T03:00:00Z',
    self: false,
    ...overrides,
  }
}

/** 候補の利用者（割り当て済み・利用停止・長いメールアドレス） */
export function candidateUsersWire(): AdminUserPageWire {
  const items = [
    candidateOf({ userId: DIRECT_USER_ID, displayName: TWO_WORD_NAME, email: LONG_EMAIL }),
    candidateOf({
      userId: 31,
      displayName: '検査 五郎',
      email: 'kensa.goro@example.com',
      suspended: true,
    }),
    candidateOf({}),
  ]
  return { items, page: 1, size: 20, total: items.length }
}

/** グループの一覧 */
export function groupListWire(): GroupPage {
  const items = [
    { groupId: SAMPLE_GROUP_ID, name: SYMBOL_NAME, memberCount: 2, assignedRoleCount: 1 },
    { groupId: 4, name: '経理部', memberCount: 0, assignedRoleCount: 0 },
  ]
  return { items, page: 1, size: 20, total: items.length }
}

/** グループ1件（作成の応答） */
export function groupWire(groupId = SAMPLE_GROUP_ID, name = SYMBOL_NAME): Group {
  return { groupId, name, createdAt: '2026-10-05T00:00:00Z', updatedAt: '2026-10-05T00:00:00Z' }
}

/** グループの詳細 */
export function groupDetailWire(): GroupDetail {
  return {
    groupId: SAMPLE_GROUP_ID,
    name: SYMBOL_NAME,
    members: [
      { userId: DIRECT_USER_ID, displayName: TWO_WORD_NAME, email: LONG_EMAIL, suspended: false },
      { userId: 23, displayName: '検査 三郎', email: 'kensa.saburo@example.com', suspended: true },
    ],
  }
}

/** グループに割り当てたロール */
export function groupRolesWire(): GroupRoleRef[] {
  return [{ roleId: SAMPLE_ROLE_ID, name: '営業' }]
}

function changeOf(index: number, reason: TransferChange['reason']): TransferChange {
  return {
    target: {
      schemaName: SAMPLE_SCHEMA,
      tableName: `T${String(index).padStart(5, '0')}`,
      columnName: null,
    },
    before: { main: reason === 'ADDED' ? null : 'READ', create: null, delete: null },
    after: { main: reason === 'CLEARED' ? null : 'FULL', create: null, delete: null },
    reason,
    inCurrentDsl: index % 50 !== 0,
  }
}

function planOf(
  index: number,
  changes: number,
  action: TransferRolePlan['action'],
): TransferRolePlan {
  const list = Array.from({ length: changes }, (_, i) =>
    changeOf(i, i % 3 === 0 ? 'ADDED' : i % 3 === 1 ? 'CHANGED' : 'CLEARED'),
  )
  const count = (reason: TransferChange['reason']) => list.filter((c) => c.reason === reason).length
  return {
    name: index === 0 ? SYMBOL_NAME : `ロール${String(index + 1).padStart(2, '0')}`,
    roleId: action === 'CREATE' ? null : 100 + index,
    action,
    added: count('ADDED'),
    changed: count('CHANGED'),
    cleared: count('CLEARED'),
    notInDsl: list.filter((c) => !c.inCurrentDsl).length,
    changes: list,
  }
}

/** 確かめの応答（roles 個のロール、各 changes 件の変わる点。2つ目は作る） */
export function transferCheckWire(roles = 2, changes = 150): TransferCheck {
  return {
    fingerprint: 'e2e-fingerprint',
    roles: Array.from({ length: roles }, (_, index) =>
      planOf(index, changes, index === 1 ? 'CREATE' : 'REPLACE'),
    ),
    untouchedRoles: ['経理'],
  }
}

/** ROLE_TRANSFER_INVALID の応答（場所が長い誤りと、値の無い誤り） */
export function transferInvalidProblem(): Readonly<Record<string, unknown>> {
  const errors: TransferErrorItem[] = [
    {
      reason: 'UNKNOWN_KEY',
      line: 12,
      column: 7,
      path: `roles.0.permissions.${'LONG_TABLE_NAME_'.repeat(6)}x`,
    },
    { reason: 'SYNTAX', line: null, column: null, path: null },
  ]
  return { status: 400, code: 'ROLE_TRANSFER_INVALID', title: 'Bad Request', errors }
}

/** 409 の拒否の応答（code だけ。detail に値を入れない） */
export function conflictWire(code: string): Readonly<Record<string, unknown>> {
  return { status: 409, code, title: 'Conflict' }
}
