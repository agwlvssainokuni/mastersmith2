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
// 自分の業務のメニューの API（契約 C9 の GET /api/me/navigation）を呼ぶ関数と応答の写し（Intent 261004-role-menu の
// U7 の L2、functional-spec.md の 1.2、D3、NFR1.4・NFR2.2）。
// - ApiClient（apiRequest）を通す。引数を付けない（主体はサーバーが要求の文脈から読む）。
// - 応答は型の項目だけを写した新しい値にし、必要な項目が無い・型が違う・知らない emptyReason の本文は通信の失敗にする。
// - アイコンの名前は照らさずに写す（照らし合わせは buildNavSections の1か所、D14）。
import { apiRequest } from '../../shared/api-client/apiClient'
import { networkError } from '../../shared/api-client/apiError'
import type { TableRef } from './tablePlaceholderPath'

/** 自分の業務のメニューの API の道 */
export const NAVIGATION_API = '/api/me/navigation'

/** 表示名の組（日本語と英語） */
export interface LocalizedLabel {
  ja: string
  en: string
}

/** 業務のメニューの項目（絞った後の木） */
export interface NavNode {
  /** 絞る前の DSL の木での位置の道 */
  id: string
  label: LocalizedLabel
  /** サーバーで照らした後のアイコンの名前（画面でも照らし直す） */
  icon: string
  /** 移れる項目のテーブルの組（移れない項目は null） */
  table: TableRef | null
  navigable: boolean
  items: readonly NavNode[]
}

/** 業務のメニューが空の理由 */
export type NavEmptyReason = 'NOT_CONFIGURED' | 'NOTHING_VISIBLE'

const EMPTY_REASONS: readonly string[] = ['NOT_CONFIGURED', 'NOTHING_VISIBLE']

/** 業務のメニューの応答 */
export interface NavigationResponse {
  items: readonly NavNode[]
  emptyReason: NavEmptyReason | null
}

function isObject(value: unknown): value is Readonly<Record<string, unknown>> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function parseLabel(value: unknown): LocalizedLabel | undefined {
  if (!isObject(value) || typeof value.ja !== 'string' || typeof value.en !== 'string') {
    return undefined
  }
  return { ja: value.ja, en: value.en }
}

function parseTable(value: unknown): TableRef | null | undefined {
  if (value === null) {
    return null
  }
  if (
    !isObject(value) ||
    typeof value.schemaName !== 'string' ||
    typeof value.tableName !== 'string'
  ) {
    return undefined
  }
  return { schemaName: value.schemaName, tableName: value.tableName }
}

function parseItems(value: unknown): NavNode[] | undefined {
  if (!Array.isArray(value)) {
    return undefined
  }
  const nodes: NavNode[] = []
  for (const item of value as unknown[]) {
    const node = parseNode(item)
    if (node === undefined) {
      return undefined
    }
    nodes.push(node)
  }
  return nodes
}

function parseNode(value: unknown): NavNode | undefined {
  if (!isObject(value) || typeof value.id !== 'string' || typeof value.navigable !== 'boolean') {
    return undefined
  }
  const label = parseLabel(value.label)
  const table = parseTable(value.table)
  const items = parseItems(value.items)
  const icon = value.icon === null ? '' : value.icon
  if (label === undefined || table === undefined || items === undefined) {
    return undefined
  }
  if (typeof icon !== 'string') {
    return undefined
  }
  return { id: value.id, label, icon, table, navigable: value.navigable, items }
}

/**
 * 業務のメニューの応答を写す。形が違えば undefined。
 *
 * @param body 応答の本文
 */
export function parseNavigation(body: unknown): NavigationResponse | undefined {
  if (!isObject(body)) {
    return undefined
  }
  const items = parseItems(body.items)
  const { emptyReason } = body
  if (items === undefined) {
    return undefined
  }
  if (emptyReason === null) {
    return { items, emptyReason: null }
  }
  if (typeof emptyReason !== 'string' || !EMPTY_REASONS.includes(emptyReason)) {
    return undefined
  }
  return { items, emptyReason: emptyReason as NavEmptyReason }
}

/** 自分の業務のメニューを読む。 */
export async function getNavigation(): Promise<NavigationResponse> {
  const response = await apiRequest(NAVIGATION_API, { method: 'GET' })
  let body: unknown
  try {
    body = await response.json()
  } catch {
    throw networkError()
  }
  const navigation = parseNavigation(body)
  if (navigation === undefined) {
    throw networkError()
  }
  return navigation
}
