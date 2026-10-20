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
// 業務データの画面の外の記憶（Intent 261016-business-data-views の U3 の L3、D2・D3、U3-NFR4.2・4.22・2.10、
// NFR 設計 security-design.md 3節、計画 4.3 の R-04・4.8 の P-2・P-10）。
// - 主キーは意味の無い印（crypto.getRandomValues の 16 バイトの16進）で引く。履歴の状態に置くのは印だけで、主キーの値は
//   道・履歴の状態・ブラウザの保存のどこにも置かない。印の表は作った順に最大 100 件で、101 件目で最も古いものを捨てる。
//   読んでも消さず、順も変えない（ブラウザの戻る操作で詳細へ戻れるようにするため。peekRowKey）。
// - テーブルごとに { 定義, 一覧の状態 } を持ち、使った順に最大 100 テーブル。
// - 全部を消すたびに世代を進める。消す前に送った要求の答えは、世代が違えば書き戻さない（R-04 の (1)）。
// - 作業ロールの roleId が、記憶に最後に書いたときの roleId と違えば（null と数の間の変化を含む）、読む・書く口の入口で
//   全部消す（R-04 の (2)）。定義の識別の workRoleId が今の作業ロールと違う定義は使わない（P-10）。
// - ログインの範囲の知らせ（app/login-state/loginScope）で全部消す（ログアウト・更新の失敗・新しいログイン）。
// - ブラウザの保存に写さない。console を呼ばない。
import { subscribeLoginScope } from '../../app/login-state/loginScope'
import type { TableRef } from '../../app/navigation/tablePlaceholderPath'
import { MAX_ROW_TOKENS, MAX_TABLE_ENTRIES } from './businessDataLimits'
import type { ListState, RowKey, TableDefinition } from './businessDataTypes'

/** テーブルごとの記憶 */
export interface TableEntry {
  /** 定義（無いか、今の作業ロールのものでなければ null） */
  definition: TableDefinition | null
  /** 一覧の状態（無ければ null） */
  listState: ListState | null
}

/** まだ書いていない印（roleId の null と区別する） */
const UNSET: unique symbol = Symbol('unset')

const rowKeys = new Map<string, RowKey>()
const tables = new Map<
  string,
  { definition: TableDefinition | null; listState: ListState | null }
>()
let generation = 0
let lastRoleId: number | null | typeof UNSET = UNSET

/** テーブルの鍵（スキーマ名とテーブル名の組） */
function tableKey(ref: TableRef): string {
  return JSON.stringify([ref.schemaName, ref.tableName])
}

/** 記憶を全部消し、世代を進める（ログインの範囲の知らせ・作業ロールの変化）。 */
export function clearBusinessDataStore(): void {
  rowKeys.clear()
  tables.clear()
  generation += 1
  lastRoleId = UNSET
}

/** 今の世代（要求を送る前に読み、答えを書き戻すときに渡す） */
export function currentStoreGeneration(): number {
  return generation
}

/** 作業ロールが最後に書いたときと違えば全部消す（読む・書く口の入口で呼ぶ）。 */
function syncWorkRole(roleId: number | null): void {
  if (lastRoleId !== UNSET && lastRoleId !== roleId) {
    clearBusinessDataStore()
  }
}

function markWritten(roleId: number | null): void {
  lastRoleId = roleId
}

/** テーブルの記憶を取り、使った順の最後へ移す。無ければ作る。上限を超えたら最も古いものを捨てる。 */
function touch(ref: TableRef): { definition: TableDefinition | null; listState: ListState | null } {
  const key = tableKey(ref)
  const entry = tables.get(key) ?? { definition: null, listState: null }
  tables.delete(key)
  tables.set(key, entry)
  while (tables.size > MAX_TABLE_ENTRIES) {
    const oldest = tables.keys().next().value
    if (oldest === undefined) {
      break
    }
    tables.delete(oldest)
  }
  return entry
}

/**
 * テーブルの記憶を読む。
 *
 * @param ref スキーマ名とテーブル名
 * @param roleId 今の作業ロールの ID（無ければ null）
 * @returns 定義と一覧の状態（無いものは null）
 */
export function readTableEntry(ref: TableRef, roleId: number | null): TableEntry {
  syncWorkRole(roleId)
  const entry = tables.get(tableKey(ref))
  if (entry === undefined) {
    return { definition: null, listState: null }
  }
  tables.delete(tableKey(ref))
  tables.set(tableKey(ref), entry)
  const definition =
    entry.definition !== null && entry.definition.definitionId.workRoleId === roleId
      ? entry.definition
      : null
  return { definition, listState: entry.listState }
}

/**
 * 定義を記憶に書く。要求を送った時の世代と今の世代が違えば書かない。
 *
 * @returns 書いたら true
 */
export function saveDefinition(
  ref: TableRef,
  roleId: number | null,
  definition: TableDefinition,
  requestGeneration: number,
): boolean {
  syncWorkRole(roleId)
  if (requestGeneration !== generation) {
    return false
  }
  touch(ref).definition = definition
  markWritten(roleId)
  return true
}

/**
 * 一覧の状態を記憶に書く。
 *
 * @param requestGeneration 状態を作った時の世代（省けば今の世代）
 * @returns 書いたら true
 */
export function saveListState(
  ref: TableRef,
  roleId: number | null,
  state: ListState,
  requestGeneration: number = generation,
): boolean {
  syncWorkRole(roleId)
  if (requestGeneration !== generation) {
    return false
  }
  touch(ref).listState = state
  markWritten(roleId)
  return true
}

/**
 * テーブルの定義を記憶から外す（定義が変わったときの読み直し F6 の前に呼ぶ）。一覧の状態は残す。
 *
 * @param ref スキーマ名とテーブル名
 */
export function forgetDefinition(ref: TableRef): void {
  const entry = tables.get(tableKey(ref))
  if (entry !== undefined) {
    entry.definition = null
  }
}

/** 意味の無い印を作る（16 バイトの乱数の16進。主キーから作らない） */
function newToken(): string {
  const bytes = new Uint8Array(16)
  crypto.getRandomValues(bytes)
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
}

/**
 * 主キーを記憶に置き、印を返す。作った順に最大 100 件で、超えたら最も古いものを捨てる。
 *
 * @param key 行を指す主キー
 * @param roleId 今の作業ロールの ID
 * @returns 履歴の状態に置く印
 */
export function putRowKey(key: RowKey, roleId: number | null): string {
  syncWorkRole(roleId)
  let token = newToken()
  while (rowKeys.has(token)) {
    token = newToken()
  }
  rowKeys.set(token, { ...key })
  while (rowKeys.size > MAX_ROW_TOKENS) {
    const oldest = rowKeys.keys().next().value
    if (oldest === undefined) {
      break
    }
    rowKeys.delete(oldest)
  }
  markWritten(roleId)
  return token
}

/**
 * 印から主キーを引く。読んでも消さず、順も変えない（機能設計の takeRowKey を、NFR 設計の R-07 の答えで peekRowKey にした）。
 *
 * @param token 印（履歴の状態から読んだもの。文字でなければ無いものとする）
 * @param roleId 今の作業ロールの ID
 * @returns 主キー（無ければ undefined）
 */
export function peekRowKey(token: unknown, roleId: number | null): RowKey | undefined {
  syncWorkRole(roleId)
  if (typeof token !== 'string') {
    return undefined
  }
  return rowKeys.get(token)
}

// 記憶のモジュールは、読み込まれた時にログインの範囲の知らせを購読する（読み込まれる前の記憶は空のため、取りこぼしは無い）。
subscribeLoginScope(clearBusinessDataStore)
