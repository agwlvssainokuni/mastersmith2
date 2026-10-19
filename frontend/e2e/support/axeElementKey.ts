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
// DOM の要素から、axe の結果の要素を見分ける鍵を作る（Intent 261016-business-data-views の U4 の L3、security-design.md の
// 2節、NFR 設計のレビューの R-01・R-03・R-04、基盤の設計の Q2: A、U4-NFR4.5）。
// - runAxe は、この関数の文字列（axeElementKey.toString()）を page.evaluate で window に置いてから呼ぶ（page.evaluate は
//   関数を直列化するため、外のモジュールの関数を直接呼べない。new Function は画面の CSP に当たるため使わない）。
//   そのため、関数の本体は外の名前・import・補助の関数に頼らない（定数と小さな関数は本体の中に置く）。型の import は
//   変換で消える。
// - 鍵に入れないもの: aria-label・要素の文字・id・属性の値（氏名・メールアドレス・行の ID などを記録に残さないため）。
import type { AxeElementKey } from './axeRuleChecks'

/** 要素の鍵を作る（自己完結の関数。page.evaluate の中でも、jsdom の単体のテストでも動く） */
export function axeElementKey(element: Element): AxeElementKey {
  // アプリの印のクラス（自分か祖先にあれば marks に入れる）。足すときは axeElementKey.test.ts にも見本を足す。
  const appMarks = [
    'work-role-switcher-trigger',
    'useradmin-row-actions',
    'groupadmin-row-menu',
    'roleadmin-row-menu',
  ]
  // Dropdown の部品が付ける data-testid（行や画面を見分けないため owner に使わない）
  const dropdownTestId = /^dropdown-(?:trigger|menu|item-\d+)$/
  // 開いている Dropdown のトリガーが2つ以上あったときの印（axeRuleChecks.ts の MULTIPLE_OPEN_TRIGGERS と同じ値）
  const multipleOpenTriggers = '!multiple-open-triggers'
  const pathDepth = 5

  // 末尾の ID（- の後の数字だけ、または -r<数字>）を '-*' にする（R-03。work-role-switcher などは置き換えない）
  const normalize = (testId: string): string => testId.replace(/-(?:\d+|r\d+)$/, '-*')
  const describe = (el: Element): string => {
    const role = el.getAttribute('role')
    const tag = el.tagName.toLowerCase()
    return role === null || role === '' ? tag : `${tag}[${role}]`
  }
  const ownerOf = (el: Element): string | null => {
    for (let current: Element | null = el; current !== null; current = current.parentElement) {
      const testId = current.getAttribute('data-testid')
      if (testId !== null && testId !== '' && !dropdownTestId.test(testId)) {
        return normalize(testId)
      }
    }
    return null
  }
  const marksOf = (el: Element): string[] => {
    const marks = new Set<string>()
    for (const name of Array.from(el.classList)) {
      if (name.startsWith('mycui-') || appMarks.includes(name)) {
        marks.add(name)
      }
    }
    for (let current = el.parentElement; current !== null; current = current.parentElement) {
      for (const name of Array.from(current.classList)) {
        if (appMarks.includes(name)) {
          marks.add(name)
        }
      }
    }
    return Array.from(marks).sort()
  }
  const menuOwnerOf = (el: Element): string | null => {
    if (el.closest('[data-testid="dropdown-menu"]') === null) {
      return null
    }
    // Dropdown のトリガー（aria-haspopup と aria-expanded="true" の両方を持つ）だけを数える。サイドバーや木の開閉の
    // ボタン（aria-expanded だけを持つ）は数えない（R-01）。
    const open = Array.from(
      el.ownerDocument.querySelectorAll('[aria-haspopup][aria-expanded="true"]'),
    ).filter((candidate) => candidate.getAttribute('aria-haspopup') !== 'false')
    if (open.length >= 2) {
      return multipleOpenTriggers
    }
    const trigger = open[0]
    if (trigger === undefined) {
      return null
    }
    return [ownerOf(trigger) ?? '-', ...marksOf(trigger).map((mark) => `.${mark}`)].join(' ')
  }
  const pathOf = (el: Element): string => {
    const parts: string[] = []
    for (
      let current: Element | null = el;
      current !== null && parts.length < pathDepth;
      current = current.parentElement
    ) {
      parts.push(describe(current))
    }
    return parts.join('>')
  }

  return {
    element: describe(element),
    owner: ownerOf(element),
    marks: marksOf(element),
    menuOwner: menuOwnerOf(element),
    path: pathOf(element),
  }
}
