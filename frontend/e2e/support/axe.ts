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
// axe-core を検査のページの中だけで動かす（U4 の NFR 設計 security-design.md 6節、NFR7.3・NFR9.4・NFR9.6）。
// 本体は明示の devDependencies から Node の側で読み、page.evaluate で評価する（@axe-core/playwright と同じ読み込み方）。
// bypassCSP・addScriptTag・検査のための道は使わない。画面の成果物（src・dist）からは読み込まない。
// Intent 261016-business-data-views の U4（L3・L4、security-design.md の 1節・2節、U4-NFR4.5・5.3）:
// - 違反の要素は axe の選択子（target）ではなく鍵（axeElementKey.ts）で返す。選択子は page.evaluate の中でだけ使う。
// - 規則 label-content-name-mismatch の passes・incomplete・violations の要素を鍵で返す（labelContentNameNodes）。
// - 毎回、規則・鍵・件数だけを注記 axe-nodes に残す（計画の 10節の D5: A。選択子と値は持たない）。
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { test, type Page } from '@playwright/test'
import { axeElementKey } from './axeElementKey'
import {
  axeNodesAnnotation,
  describeAxeNode,
  LABEL_CONTENT_NAME_RULE,
  UNRESOLVED_KEY,
  type AxeElementKey,
  type AxeRuleNode,
} from './axeRuleChecks'

const require = createRequire(import.meta.url)
const axeSource = readFileSync(require.resolve('axe-core/axe.min.js'), 'utf8')

/** 鍵の関数を window に置く文（axe.min.js と同じく文字列で評価する） */
const axeElementKeySource = `window.__axeElementKey = ${axeElementKey.toString()}`

/** 合否に使う規則のタグ（WCAG 2.0・2.1 の A・AA。NFR 設計の Q2 A） */
export const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] as const

/**
 * 流れたことを確かめる規則（版を上げてタグが変わり、黙って流れなくなることを防ぐ。6.4）。
 * label-content-name-mismatch（WCAG 2.5.3）は axe-core 4.14.0 で既定で流れるようになったため、Intent
 * 261016-business-data-views の B7（U4-NFR5.1）で足した。区分ごとの passes の確かめは labelContentNameProblems。
 */
export const REQUIRED_RULES = [
  'color-contrast',
  'scrollable-region-focusable',
  'label-content-name-mismatch',
] as const

/** 違反の対象の要素（選択子と値は持たない） */
export interface AxeViolationNode {
  /** 要素を見分ける鍵 */
  key: AxeElementKey
  /** make-you-chic-ui の primary の Button か */
  primaryButton: boolean
}

/** 検査の結果の要約（Node の側へ返す形） */
export interface AxeSummary {
  violations: { id: string; impact: string | null; nodes: AxeViolationNode[] }[]
  incomplete: { id: string; count: number }[]
  ruleIds: string[]
  /** 規則 label-content-name-mismatch の結果の要素（区分と鍵。L4） */
  labelContentNameNodes: AxeRuleNode[]
}

interface AxeRuleResult {
  id: string
  impact?: string | null
  nodes: { target: unknown[] }[]
}

interface AxeRunResult {
  violations: AxeRuleResult[]
  incomplete: AxeRuleResult[]
  passes: AxeRuleResult[]
  inapplicable: AxeRuleResult[]
}

declare global {
  interface Window {
    axe: {
      run: (context: Document, options: unknown) => Promise<AxeRunResult>
    }
    __axeElementKey: (element: Element) => AxeElementKey
  }
}

/**
 * ページで axe-core を評価し、WCAG 2.0・2.1 の A・AA の規則で検査する。
 * 要素の鍵は検査と同じ1回の page.evaluate の中で作り、選択子は戻り値に入れない。結果の規則・鍵・件数を注記 axe-nodes に残す。
 */
export async function runAxe(page: Page): Promise<AxeSummary> {
  await page.evaluate(axeSource)
  await page.evaluate(axeElementKeySource)
  const summary = await page.evaluate(
    async ({ tags, rule, unresolved }) => {
      const result = await window.axe.run(document, { runOnly: { type: 'tag', values: tags } })
      const elementOf = (node: { target: unknown[] }): Element | null =>
        node.target.length === 1 ? document.querySelector(node.target.map(String).join(' ')) : null
      const keyOf = (element: Element | null) =>
        element === null ? unresolved : window.__axeElementKey(element)
      const outcomes = ['passes', 'incomplete', 'violations'] as const
      return {
        violations: result.violations.map((violation) => ({
          id: violation.id,
          impact: violation.impact ?? null,
          nodes: violation.nodes.map((node) => {
            const element = elementOf(node)
            return {
              key: keyOf(element),
              primaryButton: element?.matches('.mycui-button.variant-primary') ?? false,
            }
          }),
        })),
        incomplete: result.incomplete.map((item) => ({ id: item.id, count: item.nodes.length })),
        ruleIds: [
          ...result.passes,
          ...result.violations,
          ...result.incomplete,
          ...result.inapplicable,
        ].map((item) => item.id),
        labelContentNameNodes: outcomes.flatMap((outcome) =>
          result[outcome]
            .filter((item) => item.id === rule)
            .flatMap((item) =>
              item.nodes.map((node) => ({ outcome, key: keyOf(elementOf(node)) })),
            ),
        ),
      }
    },
    { tags: [...WCAG_TAGS], rule: LABEL_CONTENT_NAME_RULE, unresolved: UNRESOLVED_KEY },
  )
  test.info().annotations.push({ type: 'axe-nodes', description: axeNodesAnnotation(summary) })
  return summary
}

/**
 * 既知の違反（依頼者が受け入れた制約）の一覧。組（ブランドカラー）・規則・名前（鍵の owner。正規化した data-testid）で
 * 指定した make-you-chic-ui の primary の Button だけを既知として扱い、ほかの規則・ほかの要素・ほかの組の違反は失敗にする。
 * Intent 260925-user-management の U4 で、primary の Button（green 3.30:1・orange 3.56:1）を既知の違反としていたが、
 * make-you-chic-ui の固定先を 310e1ec に上げて（Intent 260928-quality-followup の FR1）、050〜080 の実測で当たらなくなったため
 * 一覧を空にした。既知の違反が無い間は、どの違反も失敗にする。仕組み（splitKnownViolations）は、次に既知の制約を受け入れる
 * ときのために残す。
 */
export const KNOWN_VIOLATIONS: readonly {
  brandColors: readonly string[]
  rule: string
  testIds: readonly string[]
}[] = []

/**
 * 違反を、既知の違反と、それ以外（失敗にするもの）に分ける。
 * knownTestIds を渡すと、KNOWN_VIOLATIONS の組と規則のまま、名前の一覧だけをその画面の状態の一覧に置き換える
 * （B5 の画面は状態ごとに当たる primary の Button が違うため。U5 の計画の9節の決定 4）。省略すると今の一覧のまま（050）。
 */
export function splitKnownViolations(
  summary: AxeSummary,
  brandColor: string,
  knownTestIds?: readonly string[],
): { known: string[]; unexpected: string[]; expectedKnown: string[] } {
  const rules = KNOWN_VIOLATIONS.map((known) =>
    knownTestIds === undefined ? known : { ...known, testIds: knownTestIds },
  ).filter((known) => known.brandColors.includes(brandColor))
  const known: string[] = []
  const unexpected: string[] = []
  for (const violation of summary.violations) {
    for (const node of violation.nodes) {
      const isKnown = rules.some(
        (rule) =>
          rule.rule === violation.id &&
          node.primaryButton &&
          node.key.owner !== null &&
          rule.testIds.includes(node.key.owner),
      )
      const label = describeAxeNode(violation.id, node.key)
      if (isKnown) {
        known.push(label)
      } else {
        unexpected.push(label)
      }
    }
  }
  const expectedKnown = rules.flatMap((rule) => rule.testIds.map((id) => `${rule.rule} ${id}`))
  return { known: known.sort(), unexpected, expectedKnown: expectedKnown.sort() }
}

/**
 * 招待の管理の画面（U5、060）の状態ごとの既知の違反の名前。make-you-chic-ui の固定先を 310e1ec に上げた後の 060 の実測で
 * 当たらなくなったため、すべての状態で空にした（Intent 260928-quality-followup の FR1.2）。
 */
export const INVITATION_KNOWN_VIOLATIONS: Readonly<Record<InvitationAxeState, readonly string[]>> =
  {
    list: [],
    inviteDialog: [],
    revokeDialog: [],
    unavailable: [],
  }

/** 招待の管理の画面の検査の状態 */
export type InvitationAxeState = 'list' | 'inviteDialog' | 'revokeDialog' | 'unavailable'

/**
 * 登録の完了の画面（U6、070）の状態ごとの既知の違反の名前。make-you-chic-ui の固定先を 310e1ec に上げた後の 070 の実測で
 * 当たらなくなったため、すべての状態で空にした（Intent 260928-quality-followup の FR1.2）。
 */
export const REGISTRATION_KNOWN_VIOLATIONS: Readonly<
  Record<RegistrationAxeState, readonly string[]>
> = {
  ready: [],
  unavailable: [],
}

/** 登録の完了の画面の検査の状態 */
export type RegistrationAxeState = 'ready' | 'unavailable'

/**
 * プリファレンスとパスワードの変更の画面（U7、080）の状態ごとの既知の違反の名前。make-you-chic-ui の固定先を 310e1ec に
 * 上げた後の 080 の実測で当たらなくなったため、すべての状態で空にした（Intent 260928-quality-followup の FR1.2）。
 * あわせて、トップバーのアバター（2語の氏名の頭文字）と、dark の組の FormField の誤りの文字の既知の違反の扱いも、
 * 当たらなくなったため外した。
 */
export const PREFERENCES_KNOWN_VIOLATIONS: Readonly<
  Record<PreferencesAxeState, readonly string[]>
> = {
  'preferences-ready': [],
  'preferences-invalid': [],
  'password-ready': [],
  'password-invalid': [],
}

/** プリファレンスとパスワードの変更の画面の検査の状態 */
export type PreferencesAxeState =
  'preferences-ready' | 'preferences-invalid' | 'password-ready' | 'password-invalid'

/** 流れるべき規則のうち、結果に無いものを返す。 */
export function missingRequiredRules(summary: AxeSummary): string[] {
  return REQUIRED_RULES.filter((rule) => !summary.ruleIds.includes(rule))
}
