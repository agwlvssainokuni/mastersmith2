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
// 業務のメニューのアイコンの一覧のファイル（backend/src/main/resources/navigation/allowed-icons.txt）と、画面の登録のアイコンの
// 一覧（ALLOWED_ICONS）が集まりとして一致することの確かめ（Intent 261004-role-menu の U5、BR4.4、NFR6.7）。
// make-you-chic-ui の固定先を上げてアイコンが増減したときに、片方だけを直す誤りを止める。
// ファイルの道は実行の場所（cwd）に頼らず、このファイルの場所（import.meta.dirname）から組み立てる（基盤の設計の読み直しの R-04。
// 画面のテストの環境 jsdom では import.meta.url が file の形にならないため、既存の eslintImportRules.test.ts と同じ dirname を使う）。
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { ALLOWED_ICONS } from './allowedIcons'

const ICON_LIST_PATH = resolve(
  import.meta.dirname,
  '../../../../backend/src/main/resources/navigation/allowed-icons.txt',
)

/** 一覧のファイルの中身を、サーバーと同じ読み方（# の行と空行を飛ばし、前後の空白を除く）で名前の一覧にする。 */
function parseIconList(content: string): string[] {
  return content
    .split(/\r\n|\r|\n/)
    .map((line) => line.trim())
    .filter((line) => line !== '' && !line.startsWith('#'))
}

describe('allowed icons of the business menu', () => {
  const names = parseIconList(readFileSync(ICON_LIST_PATH, 'utf8'))

  it('reads the icon list file with the same rules as the server', () => {
    expect(parseIconList('# header\n\n  list  \r\nhome\n\t# comment\n')).toEqual(['list', 'home'])
  })

  it('has the same set of names as ALLOWED_ICONS of the app registry', () => {
    expect(new Set(names)).toEqual(new Set<string>(ALLOWED_ICONS))
    expect(names).toHaveLength(ALLOWED_ICONS.length)
  })

  it('contains the default icon list without duplicates', () => {
    expect(names).toContain('list')
    expect(new Set(names).size).toBe(names.length)
  })
})
