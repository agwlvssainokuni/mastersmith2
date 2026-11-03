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
// 業務の項目のアイコンを決める純粋な関数（Intent 261102-timezone-ui-polish の U5、functional-spec.md の D10・D13・D29）。
// - サイドバーの業務の項目とホームの業務のカードが、この 1 つの関数を使う（同じ規則・同じ値）。
// - 順: 1) icon が許す一覧にあり list でなければその名前、2) tableKind が TABLE なら table・VIEW なら view（子を持っていても
//   種類を先にする）、3) 移れない項目は folder、4) 移れる項目で種類が分からなければ list。
// - icon が list の項目は「指定なし」として扱う（応答の icon の形を変えないための既知の制約。D13）。
// - 信頼できない入力として扱う: 例外を投げず、許す一覧の名前だけを返す。知らない値・型の違う値は指定なしとみなす。
import type { IconName } from 'make-you-chic-ui'
import { isAllowedIcon } from '../registry/allowedIcons'
import type { NavTableKind } from './navigationApi'

/** アイコンを決める入力 */
export interface ItemIconInput {
  /** 応答の icon（型の外から来た値も扱う） */
  icon: unknown
  /** 応答の tableKind（型の外から来た値も扱う） */
  tableKind: NavTableKind | null | undefined
  navigable: boolean
}

/** 指定なしを表す既定のアイコン（D13） */
const UNSPECIFIED_ICON: IconName = 'list'

/**
 * 業務の項目のアイコンを決める（D10）。
 *
 * @param input 項目の icon・tableKind・navigable
 * @returns 許す一覧の名前
 */
export function resolveItemIcon(input: ItemIconInput): IconName {
  if (isAllowedIcon(input.icon) && input.icon !== UNSPECIFIED_ICON) {
    return input.icon
  }
  if (input.tableKind === 'TABLE') {
    return 'table'
  }
  if (input.tableKind === 'VIEW') {
    return 'view'
  }
  return input.navigable ? UNSPECIFIED_ICON : 'folder'
}
