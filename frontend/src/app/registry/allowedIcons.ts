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
// サイドバーの項目の登録で使えるアイコンの名前の照合の一覧（security-design 5.2、BR5.4）。
// make-you-chic-ui の入口は名前の型 IconName だけを出し、名前の一覧を値として出さないため、ここに写して持つ。
// 両方向を型の検査で守る: 一覧の名前はすべて IconName であり（satisfies）、IconName の名前はすべて一覧にある
// （ALL_ICONS_COVERED）。make-you-chic-ui の固定先を上げてアイコンが増減したら、型の検査が落ちて気づく。
import type { IconName } from 'make-you-chic-ui'

/** 登録で使えるアイコンの名前（make-you-chic-ui の Icon の名前の一覧の写し）。 */
export const ALLOWED_ICONS = [
  'menu',
  'chevron-down',
  'chevron-up',
  'close',
  'check',
  'bell',
  'user',
  'search',
  'edit',
  'trash',
  'download',
  'settings',
  'home',
  'list',
  'info',
  'success',
  'warning',
  'danger',
] as const satisfies readonly IconName[]

type MissingIcons = Exclude<IconName, (typeof ALLOWED_ICONS)[number]>

/** IconName の名前がすべて一覧にあることの型の確かめ（欠けがあると型の検査で落ちる）。 */
export const ALL_ICONS_COVERED: [MissingIcons] extends [never] ? true : never = true

const ALLOWED_ICON_SET: ReadonlySet<string> = new Set(ALLOWED_ICONS)

/**
 * 登録で使えるアイコンの名前かを返す（型の外から来た値も扱う）。
 *
 * @param name アイコンの名前
 * @returns 一覧にある名前なら true
 */
export function isAllowedIcon(name: unknown): name is IconName {
  return typeof name === 'string' && ALLOWED_ICON_SET.has(name)
}
