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
// E2E の axe の結果の確かめ（L5）・記録の形（L6）・違反の文の単体のテスト（Intent 261016-business-data-views の U4、
// logical-components.md の 3節、security-design.md の 1節、U4-NFR4.5・5.3・5.4、計画の 10節の D5: A）。
// ブラウザも axe-core も使わず、手で組んだ要約（AxeSummary の形）で確かめる。
import { describe, expect, it } from 'vitest'
import { KNOWN_VIOLATIONS, splitKnownViolations, type AxeSummary } from './axe'
import {
  axeNodesAnnotation,
  categorizeKey,
  describeAxeNode,
  LABEL_CONTENT_NAME_RULE,
  labelContentNameProblems,
  labelContentNameRows,
  MULTIPLE_OPEN_TRIGGERS,
  type AxeElementKey,
  type AxeOutcome,
  type AxeRuleNode,
} from './axeRuleChecks'

const USER_ROW_TRIGGER: AxeElementKey = {
  element: 'button',
  owner: 'useradmin-row-actions-*',
  marks: ['mycui-button', 'useradmin-row-actions'],
  menuOwner: null,
  path: 'button>span>td>tr>tbody',
}
const GROUP_ROW_TRIGGER: AxeElementKey = {
  element: 'button',
  owner: 'groupadmin-row-actions-*',
  marks: ['groupadmin-row-menu', 'mycui-button'],
  menuOwner: null,
  path: 'button>span>span>td>tr',
}
const ROLE_ROW_TRIGGER: AxeElementKey = {
  ...GROUP_ROW_TRIGGER,
  owner: 'roleadmin-row-actions-*',
  marks: ['mycui-button', 'roleadmin-row-menu'],
}
const TOPBAR_TRIGGER: AxeElementKey = {
  element: 'button',
  owner: null,
  marks: ['mycui-topbar-user-trigger'],
  menuOwner: null,
  path: 'button>header>div>div>body',
}
const WORK_ROLE_TRIGGER: AxeElementKey = {
  element: 'button',
  owner: 'work-role-switcher',
  marks: ['work-role-switcher-trigger'],
  menuOwner: null,
  path: 'button>div>header>div>div',
}
const WORK_ROLE_ITEM: AxeElementKey = {
  element: 'button[menuitem]',
  owner: null,
  marks: ['mycui-dropdown-item-control'],
  menuOwner: 'work-role-switcher .work-role-switcher-trigger',
  path: 'button[menuitem]>div>div[menu]>body>html',
}
const TOPBAR_ITEM: AxeElementKey = {
  ...WORK_ROLE_ITEM,
  element: 'a[menuitem]',
  menuOwner: '- .mycui-topbar-user-trigger',
}

const TABLES_ROW_TRIGGER: AxeElementKey = {
  element: 'button',
  owner: 'tables-row-actions-*',
  marks: ['mycui-button', 'tables-row-actions'],
  menuOwner: null,
  path: 'button>span>span>span>td',
}
const TABLES_ROW_LINK: AxeElementKey = {
  element: 'a',
  owner: 'tables-row-link-*',
  marks: ['tables-row-link'],
  menuOwner: null,
  path: 'a>span>span>td>tr',
}

function node(outcome: AxeOutcome, key: AxeElementKey): AxeRuleNode {
  return { outcome, key }
}

function summaryOf(
  labelContentNameNodes: AxeRuleNode[],
  violations: AxeSummary['violations'] = [],
): AxeSummary {
  return { violations, incomplete: [], ruleIds: [], labelContentNameNodes }
}

describe('labelContentNameProblems', () => {
  it('reports nothing when each category is found only in passes', () => {
    const summary = summaryOf([
      node('passes', USER_ROW_TRIGGER),
      node('passes', USER_ROW_TRIGGER),
      node('passes', WORK_ROLE_TRIGGER),
    ])
    expect(labelContentNameProblems(summary, ['C1', 'C5'])).toEqual([])
  })

  it('does not count incomplete as a pass', () => {
    const summary = summaryOf([node('passes', TOPBAR_TRIGGER), node('incomplete', TOPBAR_TRIGGER)])
    const problems = labelContentNameProblems(summary, ['C4'])
    expect(problems).toHaveLength(1)
    expect(problems[0]).toContain('C4')
    expect(problems[0]).toContain('incomplete')
    expect(
      labelContentNameProblems(summaryOf([node('incomplete', TOPBAR_TRIGGER)]), ['C4']),
    ).toEqual([expect.stringContaining('passes が 0 件'), expect.stringContaining('incomplete')])
  })

  it('reports a violation of a category', () => {
    const summary = summaryOf([node('passes', WORK_ROLE_ITEM), node('violations', WORK_ROLE_ITEM)])
    const problems = labelContentNameProblems(summary, ['C6'])
    expect(problems).toEqual([expect.stringContaining('violations')])
    expect(problems[0]).toContain(LABEL_CONTENT_NAME_RULE)
  })

  it('reports a build error when a category has no element or two dropdown triggers are open', () => {
    expect(labelContentNameProblems(summaryOf([node('passes', USER_ROW_TRIGGER)]), ['C7'])).toEqual(
      [expect.stringContaining('確かめの作りの誤り: C7')],
    )
    const broken = { ...WORK_ROLE_ITEM, menuOwner: MULTIPLE_OPEN_TRIGGERS }
    const problems = labelContentNameProblems(
      summaryOf([node('passes', broken), node('passes', USER_ROW_TRIGGER)]),
      ['C1'],
    )
    expect(problems).toEqual([expect.stringContaining('トリガーが2つ以上')])
  })
})

describe('categorizeKey', () => {
  it('assigns the nine categories and leaves other elements out', () => {
    expect(
      [
        USER_ROW_TRIGGER,
        GROUP_ROW_TRIGGER,
        ROLE_ROW_TRIGGER,
        TOPBAR_TRIGGER,
        WORK_ROLE_TRIGGER,
        WORK_ROLE_ITEM,
        TOPBAR_ITEM,
        TABLES_ROW_TRIGGER,
        TABLES_ROW_LINK,
      ].map(categorizeKey),
    ).toEqual(['C1', 'C2', 'C3', 'C4', 'C5', 'C6', 'C7', 'C8', 'C9'])
    // 行の「開く」のリンク・メニューの持ち主の無い項目・印の無い業務データの要素は当てはめない
    expect(categorizeKey({ ...GROUP_ROW_TRIGGER, element: 'a', marks: [] })).toBeNull()
    expect(categorizeKey({ ...WORK_ROLE_ITEM, menuOwner: null })).toBeNull()
    expect(
      categorizeKey({ ...USER_ROW_TRIGGER, owner: 'tables-row-actions-*', marks: [] }),
    ).toBeNull()
    expect(categorizeKey({ ...TABLES_ROW_LINK, marks: [] })).toBeNull()
    // 行のリンクの印を持つボタン・行の操作の印を持つリンクは、取り違えない
    expect(categorizeKey({ ...TABLES_ROW_LINK, element: 'button' })).toBeNull()
    expect(categorizeKey({ ...TABLES_ROW_TRIGGER, element: 'a' })).toBeNull()
  })

  it('checks the business data row actions and row links like the other categories', () => {
    const passing = summaryOf([node('passes', TABLES_ROW_TRIGGER), node('passes', TABLES_ROW_LINK)])
    expect(labelContentNameProblems(passing, ['C8', 'C9'])).toEqual([])
    expect(
      labelContentNameProblems(summaryOf([node('passes', TABLES_ROW_TRIGGER)]), ['C8', 'C9']),
    ).toEqual([expect.stringContaining('確かめの作りの誤り: C9')])
    expect(
      labelContentNameProblems(
        summaryOf([node('passes', TABLES_ROW_LINK), node('violations', TABLES_ROW_LINK)]),
        ['C9'],
      ),
    ).toEqual([expect.stringContaining('C9（業務データの行のリンク）: violations')])
  })
})

describe('records and messages', () => {
  it('builds messages and the axe-nodes annotation from rules, keys and counts only', () => {
    const summary = summaryOf(
      [node('passes', USER_ROW_TRIGGER), node('passes', USER_ROW_TRIGGER)],
      [
        {
          id: 'color-contrast',
          impact: 'serious',
          nodes: [
            { key: GROUP_ROW_TRIGGER, primaryButton: false },
            { key: GROUP_ROW_TRIGGER, primaryButton: false },
          ],
        },
      ],
    )
    expect(describeAxeNode('color-contrast', GROUP_ROW_TRIGGER)).toBe(
      'color-contrast element=button owner=groupadmin-row-actions-* marks=groupadmin-row-menu,mycui-button menuOwner=- path=button>span>span>td>tr',
    )
    const annotation = JSON.parse(axeNodesAnnotation(summary)) as {
      violations: { node: string; count: number }[]
      labelContentName: { node: string; count: number }[]
    }
    expect(annotation.violations).toEqual([
      { node: describeAxeNode('color-contrast', GROUP_ROW_TRIGGER), count: 2 },
    ])
    expect(annotation.labelContentName).toEqual([
      { node: `passes C1 ${describeAxeNode(LABEL_CONTENT_NAME_RULE, USER_ROW_TRIGGER)}`, count: 2 },
    ])
    const text = axeNodesAnnotation(summary)
    for (const forbidden of ['#', '[aria-label', 'nth-child', 'target', 'example.com']) {
      expect(text).not.toContain(forbidden)
    }
  })

  it('builds the trial rows by file, combo, rule, key and outcome', () => {
    const rows = labelContentNameRows(
      '160-app-frame-accessibility.e2e.ts',
      'light-blue',
      'work-role-menu-single',
      summaryOf([node('passes', WORK_ROLE_ITEM), node('violations', TOPBAR_TRIGGER)]),
    )
    expect(rows).toEqual([
      {
        file: '160-app-frame-accessibility.e2e.ts',
        combo: 'light-blue',
        state: 'work-role-menu-single',
        rule: LABEL_CONTENT_NAME_RULE,
        key: describeAxeNode(LABEL_CONTENT_NAME_RULE, WORK_ROLE_ITEM).slice(
          LABEL_CONTENT_NAME_RULE.length + 1,
        ),
        category: 'C6',
        outcome: 'passes',
      },
      expect.objectContaining({ category: 'C4', outcome: 'violations' }),
    ])
  })

  it('treats every violation as unexpected while no known violation is listed', () => {
    expect(KNOWN_VIOLATIONS).toEqual([])
    const summary = summaryOf(
      [],
      [
        {
          id: 'color-contrast',
          impact: 'serious',
          nodes: [{ key: { ...USER_ROW_TRIGGER, owner: 'login-submit' }, primaryButton: true }],
        },
      ],
    )
    const result = splitKnownViolations(summary, 'green')
    expect(result.known).toEqual([])
    expect(result.expectedKnown).toEqual([])
    expect(result.unexpected).toEqual([
      describeAxeNode('color-contrast', { ...USER_ROW_TRIGGER, owner: 'login-submit' }),
    ])
  })
})
