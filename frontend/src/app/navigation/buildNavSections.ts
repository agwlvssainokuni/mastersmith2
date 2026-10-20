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
// サイドバーの区画を作る純粋な関数（Intent 261004-role-menu の U7 の L5、functional-spec.md の D10〜D15・D18・D20、
// W3.1・W3.2、NFR1.1・NFR1.7・NFR4.2）。
// - 区画は、見出しの無い先頭の区画（ホーム）、見出し「業務」の区画、見出し「管理」の区画の順（D10）。業務のメニューが
//   空なら業務の区画を置かない。管理者でなければ管理の区画を置かない。見出しの下が空の区画は置かない。
// - 業務の項目は応答の木を順と親子のまま写す（D11）。id は `biz:` を前置く。移れる項目だけに置き場の道を入れ、子も道も
//   無い項目は落とす。初めての読み込みの失敗は押せない項目を1つ置く（D18）。
// - 名前は表示の言語の値 → もう片方の言語 → テーブル名 → 「（名前なし）」（D13）。名前は文字として部品に渡すだけ。
//   アイコンは許した名前の一覧で照らし、無い・空は `list`（D14）。道は toTablePlaceholderPath の1か所で作る（D20）。
// - 管理の項目は登録の section ADMIN の項目（selectAdminSidebarItems）。id は `adm:` を前置く（D12）。
// - 今の項目は多くて1つ（D15・D20）。その祖先の id（前置きつき）を currentAncestorIds で返す（D16）。
// - 画面の側で権限を足さない・項目を増やさない（判定はサーバー、NFR1.1）。
import type { MouseEvent } from 'react'
import type { IconName, SidebarNavItem, SidebarNavSection } from 'make-you-chic-ui'
import type { DisplayLanguage } from '../i18n/resolveLanguage'
import { isAllowedIcon } from '../registry/allowedIcons'
import type { FeatureRegistration, LoginState } from '../registry/types'
import { HOME_PATH } from '../registry/validateRegistrations'
import { BUSINESS_ID_PREFIX } from './navExpansion'
import type { LocalizedLabel, NavigationResponse, NavNode } from './navigationApi'
import { selectAdminSidebarItems } from './navigationItems'
import {
  isTablePath,
  readTablePlaceholderParams,
  toTablePlaceholderPath,
  type TableRef,
} from './tablePlaceholderPath'

/** 業務のメニューの状態（functional-spec 4.2） */
export type BusinessNavStatus = 'idle' | 'loading' | 'loaded' | 'reloading' | 'load-error'

/** 区画を作るのに使う業務のメニューの状態 */
export interface BusinessMenuView {
  status: BusinessNavStatus
  /** 最後に読めた応答（無ければ null） */
  response: NavigationResponse | null
}

/** 区画を作る入力 */
export interface BuildNavInput {
  loginState: LoginState
  registrations: readonly FeatureRegistration[]
  business: BusinessMenuView
  location: { pathname: string; search: string }
  language: DisplayLanguage
  /** 文言の鍵から文言を引く */
  t: (key: string) => string
  /** 読み込み直しなしで移る */
  navigate: (to: string) => void
}

/** 区画を作った結果 */
export interface BuildNavOutput {
  sections: SidebarNavSection[]
  /** 今の項目の祖先の id（前置きつき、根から順）。今の項目が無い・祖先が無ければ空 */
  currentAncestorIds: readonly string[]
  /** 写した業務の項目の id（前置きつき、行きがけの順）。開閉の状態の照らし合わせに使う */
  businessIds: readonly string[]
}

/** ホームの区画と項目の id */
export const HOME_ITEM_ID = 'home'

/** 業務の区画の id */
export const BUSINESS_SECTION_ID = 'business'

/** 管理の区画の id */
export const ADMIN_SECTION_ID = 'admin'

/** 管理の項目の id の前置き */
export const ADMIN_ID_PREFIX = 'adm:'

/** 業務のメニューの読み込みの失敗を示す押せない項目の id（前置き `biz:` を付けず、照らし合わせで捨てない） */
export const BUSINESS_LOAD_ERROR_ID = 'biz-load-error'

/** 既定のアイコン */
const DEFAULT_ICON: IconName = 'list'

/** 写した移れる業務の項目（今の項目を決めるため、行きがけの順に集める） */
interface NavigableEntry {
  item: SidebarNavItem
  sourceId: string
  table: TableRef
  ancestorIds: readonly string[]
}

function isBlank(value: string): boolean {
  return value.trim() === ''
}

/** 業務の項目の表示名（D13） */
export function resolveNavLabel(
  label: LocalizedLabel,
  language: DisplayLanguage,
  table: TableRef | null,
  untitled: string,
): string {
  const primary = language === 'en' ? label.en : label.ja
  const secondary = language === 'en' ? label.ja : label.en
  if (!isBlank(primary)) {
    return primary
  }
  if (!isBlank(secondary)) {
    return secondary
  }
  return table !== null ? table.tableName : untitled
}

/** アイコンの名前を照らす（D14）。一覧に無い・空・大文字違いは既定 */
export function resolveNavIcon(icon: unknown): IconName {
  return isAllowedIcon(icon) ? icon : DEFAULT_ICON
}

/**
 * 修飾キー（Ctrl・Cmd・Shift・Alt）つき、または主ボタン以外のクリックか。ブラウザの既定（新しいタブ・新しい窓など）に任せる
 * （Build and Test の Q3 D、app-frame-ui の R-05）。
 */
function isModifiedClick(event: MouseEvent): boolean {
  return event.button > 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey
}

function linkTo(href: string, navigate: (to: string) => void): (event: MouseEvent) => void {
  return (event) => {
    if (isModifiedClick(event)) {
      return
    }
    event.preventDefault()
    navigate(href)
  }
}

/** 業務の項目を子から先に写す（W3.2）。子も道も無い項目は null。 */
function mapBusinessNode(
  node: NavNode,
  ancestorIds: readonly string[],
  input: BuildNavInput,
  untitled: string,
  navigable: NavigableEntry[],
  ids: string[],
): SidebarNavItem | null {
  const id = `${BUSINESS_ID_PREFIX}${node.id}`
  const table = node.navigable ? node.table : null
  const href = table !== null ? toTablePlaceholderPath(table, node.id) : undefined
  const item: SidebarNavItem = {
    id,
    label: resolveNavLabel(node.label, input.language, node.table, untitled),
    icon: resolveNavIcon(node.icon),
  }
  // 行きがけの順に並べるため、子を写す前に自分を移れる項目として置く（道を持つ項目は落ちない）。
  if (href !== undefined && table !== null) {
    item.href = href
    item.onClick = linkTo(href, input.navigate)
    navigable.push({ item, sourceId: node.id, table, ancestorIds })
  }
  const idIndex = ids.length
  ids.push(id)
  const childAncestors = [...ancestorIds, id]
  const children = node.items
    .map((child) => mapBusinessNode(child, childAncestors, input, untitled, navigable, ids))
    .filter((child): child is SidebarNavItem => child !== null)
  if (children.length > 0) {
    item.children = children
  }
  if (children.length === 0 && href === undefined) {
    // 子も道も無い項目は落とす（子の id はすでに外れている）。
    ids.splice(idIndex, 1)
    return null
  }
  return item
}

/** 業務の今の項目を決める（D20） */
function findBusinessCurrent(
  entries: readonly NavigableEntry[],
  search: string,
): NavigableEntry | undefined {
  const { ref, itemId } = readTablePlaceholderParams(search)
  if (ref === null) {
    return undefined
  }
  const sameTable = (entry: NavigableEntry) =>
    entry.table.schemaName === ref.schemaName && entry.table.tableName === ref.tableName
  return (
    (itemId !== null
      ? entries.find((entry) => entry.sourceId === itemId && sameTable(entry))
      : undefined) ?? entries.find(sameTable)
  )
}

/** 管理の今の項目を決める（D15。道の区切りでの前方一致、最も長い道） */
function findAdminCurrent(
  items: readonly { item: SidebarNavItem; path: string }[],
  pathname: string,
): SidebarNavItem | undefined {
  let best: { item: SidebarNavItem; path: string } | undefined
  for (const candidate of items) {
    const { path } = candidate
    const prefix = path.endsWith('/') ? path : `${path}/`
    const matches = pathname === path || pathname.startsWith(prefix)
    if (matches && (best === undefined || path.length > best.path.length)) {
      best = candidate
    }
  }
  return best?.item
}

/**
 * サイドバーの区画を作る（W3.1）。
 *
 * @param input 区画を作る入力
 * @returns 区画・今の項目の祖先の id・業務の項目の id
 */
export function buildNavSections(input: BuildNavInput): BuildNavOutput {
  const { loginState, registrations, business, location, t, navigate } = input
  if (!loginState.loggedIn) {
    return { sections: [], currentAncestorIds: [], businessIds: [] }
  }

  const homeItem: SidebarNavItem = {
    id: HOME_ITEM_ID,
    label: t('nav.home'),
    icon: 'home',
    href: HOME_PATH,
    onClick: linkTo(HOME_PATH, navigate),
  }
  const sections: SidebarNavSection[] = [{ id: HOME_ITEM_ID, items: [homeItem] }]

  const navigable: NavigableEntry[] = []
  const businessIds: string[] = []
  const response = business.response
  if (response !== null && (business.status === 'loaded' || business.status === 'reloading')) {
    const untitled = t('nav.untitled')
    const items = response.items
      .map((node) => mapBusinessNode(node, [], input, untitled, navigable, businessIds))
      .filter((item): item is SidebarNavItem => item !== null)
    if (items.length > 0) {
      sections.push({ id: BUSINESS_SECTION_ID, heading: t('nav.section.business'), items })
    }
  } else if (business.status === 'load-error') {
    sections.push({
      id: BUSINESS_SECTION_ID,
      heading: t('nav.section.business'),
      items: [{ id: BUSINESS_LOAD_ERROR_ID, label: t('nav.businessLoadError') }],
    })
  }

  const adminItems = selectAdminSidebarItems(registrations, loginState).map((registration) => ({
    path: registration.path,
    item: {
      id: `${ADMIN_ID_PREFIX}${registration.id}`,
      label: t(registration.labelKey),
      icon: resolveNavIcon(registration.icon),
      href: registration.path,
      onClick: linkTo(registration.path, navigate),
    } satisfies SidebarNavItem,
  }))
  if (adminItems.length > 0) {
    sections.push({
      id: ADMIN_SECTION_ID,
      heading: t('nav.section.admin'),
      items: adminItems.map(({ item }) => item),
    })
  }

  let currentAncestorIds: readonly string[] = []
  if (location.pathname === HOME_PATH) {
    homeItem.current = true
  } else if (isTablePath(location.pathname)) {
    // 業務データの一覧・詳細・作成・更新のどの道でも、そのテーブルの項目を今の項目にする（D1・AC1.1.6）。
    const entry = findBusinessCurrent(navigable, location.search)
    if (entry !== undefined) {
      entry.item.current = true
      currentAncestorIds = entry.ancestorIds
    }
  } else {
    const item = findAdminCurrent(adminItems, location.pathname)
    if (item !== undefined) {
      item.current = true
    }
  }

  return { sections, currentAncestorIds, businessIds }
}
