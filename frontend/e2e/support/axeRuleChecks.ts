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
// E2E の axe の結果の鍵の型・区分・確かめ・記録の形（Intent 261016-business-data-views の U4 の L5・L6、
// logical-components.md の 1節・3節、security-design.md の 1節・2節、U4-NFR4.5・5.3・5.4）。
// - 鍵は要素の選択子と値（aria-label・文字・id・属性の値）を持たない。失敗の文・注記・記録は「規則 + 鍵」だけで作る。
// - Playwright にも Vitest にも Node の部品（node:fs など）にも頼らない純粋な関数だけを置き、単体で確かめる。
// - 鍵を作る関数は axeElementKey.ts（page.evaluate の中で動く自己完結の関数）にある。

/** 要素を見分ける鍵（選択子と値は持たない） */
export interface AxeElementKey {
  /** タグの名前と role（例 'button[menuitem]'） */
  element: string
  /** 自分か近い祖先の data-testid（Dropdown の部品のものを除く。末尾の ID は '-*'） */
  owner: string | null
  /** mycui- で始まるクラスと、アプリの印のクラス（並べ替え済み） */
  marks: string[]
  /** portal のメニューの中の要素だけ、開いているトリガーの owner と marks。2つ以上開いていれば誤りの印 */
  menuOwner: string | null
  /** 自分から上へ最大5段の、タグの名前と role の並び（例 'button>span>td>tr>tbody'） */
  path: string
}

/** 規則の結果の区分 */
export type AxeOutcome = 'passes' | 'incomplete' | 'violations'

/** 名前と見える文字の規則で判定された要素（選択子と値は持たない） */
export interface AxeRuleNode {
  outcome: AxeOutcome
  key: AxeElementKey
}

/** 確かめと記録の入力（axe.ts の AxeSummary がこの形を満たす） */
export interface AxeNodeSummary {
  violations: readonly { id: string; nodes: readonly { key: AxeElementKey }[] }[]
  labelContentNameNodes: readonly AxeRuleNode[]
}

/** 見える文字と名前の食い違いの規則（WCAG 2.5.3） */
export const LABEL_CONTENT_NAME_RULE = 'label-content-name-mismatch'

/** 開いている Dropdown のトリガーが2つ以上あったときの menuOwner の印（axeElementKey.ts と同じ値） */
export const MULTIPLE_OPEN_TRIGGERS = '!multiple-open-triggers'

/** 要素を見つけられなかった（選択子が1つの要素を指さない）ときの鍵 */
export const UNRESOLVED_KEY: AxeElementKey = {
  element: '(unresolved)',
  owner: null,
  marks: [],
  menuOwner: null,
  path: '',
}

/**
 * passes の確かめの対象の候補の区分（U4 の logical-components.md の 3節の C1〜C7）。C8・C9 は U3 の B5・B6 の
 * 4.14 の試しで要素が見つかった業務データの行の操作と行のリンク（U3 の計画の Step 46、D6: A）。
 */
export type LabelContentNameCategory = 'C1' | 'C2' | 'C3' | 'C4' | 'C5' | 'C6' | 'C7' | 'C8' | 'C9'

/** 区分ごとの説明（記録の表の見出しに使う） */
export const LABEL_CONTENT_NAME_CATEGORIES: Readonly<Record<LabelContentNameCategory, string>> = {
  C1: '利用者の行の操作のトリガー',
  C2: 'グループの行の操作のトリガー',
  C3: 'ロールの行の操作のトリガー',
  C4: 'トップバーのトリガー',
  C5: '作業ロールのトリガー',
  C6: '作業ロールの項目',
  C7: 'トップバーの項目',
  C8: '業務データの行の操作のトリガー',
  C9: '業務データの行のリンク',
}

const WORK_ROLE_OWNER = 'work-role-switcher'
const WORK_ROLE_MARK = 'work-role-switcher-trigger'
const TOPBAR_MARK = 'mycui-topbar-user-trigger'

/** role の無い button か（menuitem などの role を持つ要素を除く） */
function isPlainButton(key: AxeElementKey): boolean {
  return key.element === 'button'
}

/** menuitem の要素か */
function isMenuItem(key: AxeElementKey): boolean {
  return key.element.endsWith('[menuitem]')
}

/** menuOwner の文字列を、owner と marks に分ける（axeElementKey.ts の作り方の逆） */
function parseMenuOwner(menuOwner: string): { owner: string | null; marks: string[] } {
  const parts = menuOwner.split(' ')
  const owner = parts[0] === '-' ? null : (parts[0] ?? null)
  const marks = parts.slice(1).map((part) => part.replace(/^\./, ''))
  return { owner, marks }
}

/** 鍵を区分に当てはめる。どれにも当たらなければ null。 */
export function categorizeKey(key: AxeElementKey): LabelContentNameCategory | null {
  if (isMenuItem(key)) {
    if (key.menuOwner === null || key.menuOwner === MULTIPLE_OPEN_TRIGGERS) {
      return null
    }
    const trigger = parseMenuOwner(key.menuOwner)
    if (trigger.owner === WORK_ROLE_OWNER && trigger.marks.includes(WORK_ROLE_MARK)) {
      return 'C6'
    }
    if (trigger.marks.includes(TOPBAR_MARK)) {
      return 'C7'
    }
    return null
  }
  if (key.marks.includes(TOPBAR_MARK)) {
    return 'C4'
  }
  if (key.owner === WORK_ROLE_OWNER && key.marks.includes(WORK_ROLE_MARK)) {
    return 'C5'
  }
  if (
    key.element === 'a' &&
    key.owner === 'tables-row-link-*' &&
    key.marks.includes('tables-row-link')
  ) {
    return 'C9'
  }
  if (!isPlainButton(key)) {
    return null
  }
  if (key.owner === 'useradmin-row-actions-*' && key.marks.includes('useradmin-row-actions')) {
    return 'C1'
  }
  if (key.owner === 'groupadmin-row-actions-*' && key.marks.includes('groupadmin-row-menu')) {
    return 'C2'
  }
  if (key.owner === 'roleadmin-row-actions-*' && key.marks.includes('roleadmin-row-menu')) {
    return 'C3'
  }
  if (key.owner === 'tables-row-actions-*' && key.marks.includes('tables-row-actions')) {
    return 'C8'
  }
  return null
}

/** 鍵を1行の文にする（選択子と値を含まない） */
export function describeKey(key: AxeElementKey): string {
  return [
    `element=${key.element}`,
    `owner=${key.owner ?? '-'}`,
    `marks=${key.marks.length > 0 ? key.marks.join(',') : '-'}`,
    `menuOwner=${key.menuOwner ?? '-'}`,
    `path=${key.path === '' ? '-' : key.path}`,
  ].join(' ')
}

/** 違反・記録の文（規則 + 鍵） */
export function describeAxeNode(ruleId: string, key: AxeElementKey): string {
  return `${ruleId} ${describeKey(key)}`
}

/**
 * 規則 label-content-name-mismatch の passes の確かめ（L5）。区分ごとに、passes が1件以上、incomplete と violations が
 * 0件であることを確かめ、満たさない点の文の一覧を返す（空なら合格）。incomplete は合格に数えない。
 * 区分の要素が1件も無いときと、開いた Dropdown のトリガーが2つ以上あったときは、確かめの作りの誤りの文を返す。
 */
export function labelContentNameProblems(
  summary: Pick<AxeNodeSummary, 'labelContentNameNodes'>,
  categories: readonly LabelContentNameCategory[],
): string[] {
  const problems: string[] = []
  for (const node of summary.labelContentNameNodes) {
    if (node.key.menuOwner === MULTIPLE_OPEN_TRIGGERS) {
      problems.push(
        `確かめの作りの誤り: 開いた Dropdown のトリガーが2つ以上ある（${describeAxeNode(LABEL_CONTENT_NAME_RULE, node.key)}）`,
      )
    }
  }
  for (const category of categories) {
    const nodes = summary.labelContentNameNodes.filter(
      (node) => categorizeKey(node.key) === category,
    )
    const label = `${category}（${LABEL_CONTENT_NAME_CATEGORIES[category]}）`
    if (nodes.length === 0) {
      problems.push(`確かめの作りの誤り: ${label} の要素が見つからない`)
      continue
    }
    if (!nodes.some((node) => node.outcome === 'passes')) {
      problems.push(`${label}: passes が 0 件`)
    }
    for (const node of nodes) {
      if (node.outcome !== 'passes') {
        problems.push(
          `${label}: ${node.outcome} ${describeAxeNode(LABEL_CONTENT_NAME_RULE, node.key)}`,
        )
      }
    }
  }
  return problems
}

/** 試しの記録の行（L6。ファイル × 表示の組 × 規則 × 鍵 × 結果の区分） */
export interface LabelContentNameRow {
  file: string
  combo: string
  state: string
  rule: string
  key: string
  category: LabelContentNameCategory | null
  outcome: AxeOutcome
}

/** 試しの記録の行を作る（L6） */
export function labelContentNameRows(
  file: string,
  combo: string,
  state: string,
  summary: Pick<AxeNodeSummary, 'labelContentNameNodes'>,
): LabelContentNameRow[] {
  return summary.labelContentNameNodes.map((node) => ({
    file,
    combo,
    state,
    rule: LABEL_CONTENT_NAME_RULE,
    key: describeKey(node.key),
    category: categorizeKey(node.key),
    outcome: node.outcome,
  }))
}

/** 同じ文の件数を数えて、文の順に並べる */
function countByText<T extends string>(texts: readonly T[]): { text: T; count: number }[] {
  const counts = new Map<T, number>()
  for (const text of texts) {
    counts.set(text, (counts.get(text) ?? 0) + 1)
  }
  return [...counts.entries()]
    .map(([text, count]) => ({ text, count }))
    .sort((a, b) => (a.text < b.text ? -1 : a.text > b.text ? 1 : 0))
}

/**
 * runAxe がいつも残す注記 axe-nodes の中身（JSON の文字列。計画の 10節の D5: A）。規則・鍵・区分・件数だけを入れ、
 * 選択子と値は入れない。
 */
export function axeNodesAnnotation(summary: AxeNodeSummary): string {
  const violations = countByText(
    summary.violations.flatMap((violation) =>
      violation.nodes.map((node) => describeAxeNode(violation.id, node.key)),
    ),
  ).map(({ text, count }) => ({ node: text, count }))
  const labelContentName = countByText(
    summary.labelContentNameNodes.map(
      (node) =>
        `${node.outcome} ${categorizeKey(node.key) ?? '-'} ${describeAxeNode(LABEL_CONTENT_NAME_RULE, node.key)}`,
    ),
  ).map(({ text, count }) => ({ node: text, count }))
  return JSON.stringify({ violations, labelContentName })
}
