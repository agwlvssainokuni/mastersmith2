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
// 要素の鍵の関数（L3）の単体のテスト（Intent 261016-business-data-views の U4、security-design.md の 2節、NFR 設計の
// レビューの R-01・R-03・R-04、U4-NFR4.5）。jsdom の document に、make-you-chic-ui と画面の DOM の形をまねた見本を組む
// （Dropdown のトリガーは data-testid="dropdown-trigger"・aria-haspopup・aria-expanded、メニューは body の直下の
// data-testid="dropdown-menu"、トップバーのトリガーは button.mycui-topbar-user-trigger）。値は架空のもの。
import { runInNewContext } from 'node:vm'
import { afterEach, describe, expect, it } from 'vitest'
import { axeElementKey } from './axeElementKey'
import { categorizeKey, MULTIPLE_OPEN_TRIGGERS, type AxeElementKey } from './axeRuleChecks'

const USER_NAME = '佐藤 一郎'
const USER_EMAIL = 'sato.ichiro@example.com'

/** 画面の見本（1つの一覧の行・作業ロール・トップバー・サイドバーの枝） */
function renderFrame(options: { workRoleOpen?: boolean; topbarOpen?: boolean } = {}): void {
  const workRoleOpen = options.workRoleOpen ?? false
  const topbarOpen = options.topbarOpen ?? false
  document.body.innerHTML = `
    <div data-testid="app-shell">
      <nav aria-label="メインメニュー">
        <button type="button" data-testid="sidebar-toggle-biz:0" aria-expanded="true">段1を閉じる</button>
      </nav>
      <header class="mycui-topbar">
        <button type="button" class="mycui-topbar-collapse-button" data-testid="sidebar-toggle"
          aria-label="サイドバーを折り畳む"></button>
        <div class="work-role-switcher" data-testid="work-role-switcher">
          <button type="button" class="work-role-switcher-trigger" data-testid="dropdown-trigger"
            aria-haspopup="true" aria-expanded="${workRoleOpen}" aria-label="作業ロール: 営業">
            <span>作業ロール:</span><span>営業</span>
          </button>
        </div>
        <button type="button" class="mycui-topbar-user-trigger" data-testid="dropdown-trigger"
          aria-haspopup="true" aria-expanded="${topbarOpen}" aria-label="佐一: ${USER_NAME}のメニュー">
          <div class="mycui-avatar size-md">佐一</div>
        </button>
      </header>
      <main>
        <table><tbody>
          <tr>
            <td>${USER_NAME}</td>
            <td><span class="useradmin-row-actions" data-testid="useradmin-row-actions-12">
              <button type="button" class="mycui-button variant-secondary size-sm" data-testid="dropdown-trigger"
                aria-haspopup="true" aria-expanded="false" aria-label="操作: ${USER_NAME}（${USER_EMAIL}）">操作</button>
            </span></td>
          </tr>
          <tr>
            <td><span class="groupadmin-row-actions" data-testid="groupadmin-row-actions-7">
              <a href="/admin/groups/7" class="groupadmin-link" data-testid="groupadmin-open-7"
                aria-label="営業部を開く">開く</a>
              <span class="groupadmin-row-menu">
                <button type="button" class="mycui-button size-sm" data-testid="dropdown-trigger"
                  aria-haspopup="true" aria-expanded="false" aria-label="操作: 営業部">操作</button>
              </span>
            </span></td>
            <td><span class="roleadmin-row-actions" data-testid="roleadmin-row-actions-3">
              <span class="roleadmin-row-menu">
                <button type="button" class="mycui-button size-sm" data-testid="dropdown-trigger"
                  aria-haspopup="true" aria-expanded="false" aria-label="操作: 営業">操作</button>
              </span>
            </span></td>
            <td><span data-testid="tables-row-actions-r3"><button type="button">操作</button></span></td>
            <td><span class="tables-row-actions" data-testid="tables-row-actions-r4">
              <button type="button" class="mycui-button variant-secondary size-sm" data-testid="dropdown-trigger"
                aria-haspopup="true" aria-expanded="false" aria-label="操作: 見本の品 E2E-0001">操作</button>
            </span></td>
            <td><span class="mycui-tooltip"><a class="tables-row-link tables-cell-text" href="/tables/row"
              data-testid="tables-row-link-r4" aria-label="詳細: 見本の品 E2E-0001">E2E-0001</a></span></td>
            <td><span id="cell-without-test-id">${USER_EMAIL}</span></td>
          </tr>
        </tbody></table>
      </main>
    </div>
    ${
      workRoleOpen || topbarOpen
        ? `<div role="menu" class="mycui-dropdown-menu" data-testid="dropdown-menu">
            <div class="mycui-dropdown-item">
              <button type="button" role="menuitem" class="mycui-dropdown-item-control"
                data-testid="dropdown-item-0" aria-describedby="item-0-desc">
                <span class="mycui-dropdown-item-label">切り替えられません</span>
              </button>
              <span class="mycui-dropdown-item-description" id="item-0-desc">${USER_NAME}だけのロールです</span>
            </div>
          </div>`
        : ''
    }`
}

function byLabel(label: string): Element {
  const element = document.querySelector(`[aria-label="${label}"]`)
  if (element === null) {
    throw new Error(`見本に無い要素: ${label}`)
  }
  return element
}

function menuItem(): Element {
  const element = document.querySelector('[data-testid="dropdown-item-0"]')
  if (element === null) {
    throw new Error('見本にメニューが無い')
  }
  return element
}

afterEach(() => {
  document.body.innerHTML = ''
})

describe('axeElementKey', () => {
  it('distinguishes the row action triggers, the work role trigger and the topbar trigger', () => {
    renderFrame()
    const keys = [
      axeElementKey(byLabel(`操作: ${USER_NAME}（${USER_EMAIL}）`)),
      axeElementKey(byLabel('操作: 営業部')),
      axeElementKey(byLabel('操作: 営業')),
      axeElementKey(byLabel(`佐一: ${USER_NAME}のメニュー`)),
      axeElementKey(byLabel('作業ロール: 営業')),
    ]
    expect(keys.map(categorizeKey)).toEqual(['C1', 'C2', 'C3', 'C4', 'C5'])
    expect(keys[0]).toEqual({
      element: 'button',
      owner: 'useradmin-row-actions-*',
      marks: ['mycui-button', 'useradmin-row-actions'],
      menuOwner: null,
      path: 'button>span>td>tr>tbody',
    })
    expect(keys[3]?.marks).toEqual(['mycui-topbar-user-trigger'])
    expect(keys[3]?.owner).toBe('app-shell')
    expect(keys[4]?.owner).toBe('work-role-switcher')
    expect(new Set(keys.map((key) => JSON.stringify(key))).size).toBe(5)
    // グループの行の「開く」のリンクは行の操作のトリガーに数えない
    expect(categorizeKey(axeElementKey(byLabel('営業部を開く')))).toBeNull()
  })

  it('distinguishes the business data row action trigger and row link without values', () => {
    renderFrame()
    const trigger = axeElementKey(byLabel('操作: 見本の品 E2E-0001'))
    const link = axeElementKey(byLabel('詳細: 見本の品 E2E-0001'))
    expect(trigger).toMatchObject({
      element: 'button',
      owner: 'tables-row-actions-*',
      marks: ['mycui-button', 'tables-row-actions'],
    })
    expect(link).toMatchObject({
      element: 'a',
      owner: 'tables-row-link-*',
      marks: ['tables-row-link'],
    })
    expect([trigger, link].map(categorizeKey)).toEqual(['C8', 'C9'])
    const serialized = JSON.stringify([trigger, link])
    for (const value of ['見本の品', 'E2E-0001', '/tables/row']) {
      expect(serialized).not.toContain(value)
    }
  })

  it('gives a portal item the open work role trigger as menuOwner even with a sidebar branch open', () => {
    renderFrame({ workRoleOpen: true })
    const key = axeElementKey(menuItem())
    expect(key.element).toBe('button[menuitem]')
    expect(key.owner).toBeNull()
    expect(key.menuOwner).toBe('work-role-switcher .work-role-switcher-trigger')
    expect(categorizeKey(key)).toBe('C6')
  })

  it('gives a portal item the open topbar trigger as menuOwner', () => {
    renderFrame({ topbarOpen: true })
    const key = axeElementKey(menuItem())
    expect(key.menuOwner).toBe('app-shell .mycui-topbar-user-trigger')
    expect(categorizeKey(key)).toBe('C7')
  })

  it('marks a portal item as a build error when two dropdown triggers are open', () => {
    renderFrame({ workRoleOpen: true, topbarOpen: true })
    const key = axeElementKey(menuItem())
    expect(key.menuOwner).toBe(MULTIPLE_OPEN_TRIGGERS)
    expect(categorizeKey(key)).toBeNull()
  })

  it('normalizes only trailing numeric and r-numeric ids of the owner', () => {
    renderFrame()
    expect(axeElementKey(byLabel('作業ロール: 営業')).owner).toBe('work-role-switcher')
    expect(axeElementKey(byLabel('サイドバーを折り畳む')).owner).toBe('sidebar-toggle')
    expect(axeElementKey(byLabel(`操作: ${USER_NAME}（${USER_EMAIL}）`)).owner).toBe(
      'useradmin-row-actions-*',
    )
    const tablesTrigger = document.querySelector('[data-testid="tables-row-actions-r3"] button')
    expect(tablesTrigger).not.toBeNull()
    expect(axeElementKey(tablesTrigger as Element).owner).toBe('tables-row-actions-*')
    const branch = document.querySelector('[data-testid="sidebar-toggle-biz:0"]') as Element
    expect(axeElementKey(branch).owner).toBe('sidebar-toggle-biz:0')
  })

  it('adds a path of tags and roles to an element without a test id nearby', () => {
    renderFrame()
    const cell = document.getElementById('cell-without-test-id') as Element
    const key = axeElementKey(cell)
    expect(key.owner).toBe('app-shell')
    expect(key.path).toBe('span>td>tr>tbody>table')
  })

  it('never puts names, emails, ids or text into the key', () => {
    renderFrame({ workRoleOpen: true })
    const elements = [
      byLabel(`操作: ${USER_NAME}（${USER_EMAIL}）`),
      byLabel(`佐一: ${USER_NAME}のメニュー`),
      document.getElementById('cell-without-test-id') as Element,
      menuItem(),
    ]
    const serialized = JSON.stringify(elements.map((element) => axeElementKey(element)))
    for (const value of [
      USER_NAME,
      '佐藤',
      USER_EMAIL,
      'example.com',
      '営業',
      '佐一',
      'item-0-desc',
    ]) {
      expect(serialized).not.toContain(value)
    }
    expect(serialized).not.toContain('cell-without-test-id')
    expect(serialized).not.toContain('aria-label')
  })

  it('returns the same key from the source text evaluated in a fresh context', () => {
    renderFrame({ workRoleOpen: true })
    // page.evaluate と同じく、関数の文字列だけを別の文脈で評価し直す。外の名前に頼っていれば ReferenceError になる。
    const rebuilt = runInNewContext(`(${axeElementKey.toString()})`) as (
      element: Element,
    ) => AxeElementKey
    const elements = [
      byLabel(`操作: ${USER_NAME}（${USER_EMAIL}）`),
      byLabel('操作: 営業部'),
      byLabel('作業ロール: 営業'),
      menuItem(),
      document.getElementById('cell-without-test-id') as Element,
    ]
    for (const element of elements) {
      expect(JSON.parse(JSON.stringify(rebuilt(element)))).toEqual(axeElementKey(element))
    }
  })
})
