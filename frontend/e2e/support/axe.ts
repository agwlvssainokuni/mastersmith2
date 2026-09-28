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
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import type { Page } from '@playwright/test'

const require = createRequire(import.meta.url)
const axeSource = readFileSync(require.resolve('axe-core/axe.min.js'), 'utf8')

/** 合否に使う規則のタグ（WCAG 2.0・2.1 の A・AA。NFR 設計の Q2 A） */
export const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] as const

/** 流れたことを確かめる規則（版を上げてタグが変わり、黙って流れなくなることを防ぐ。6.4） */
export const REQUIRED_RULES = ['color-contrast', 'scrollable-region-focusable'] as const

/** 違反の対象の要素 */
export interface AxeViolationNode {
  /** axe の選択子 */
  target: string
  /** 要素の data-testid（無ければ null） */
  testId: string | null
  /** make-you-chic-ui の primary の Button か */
  primaryButton: boolean
}

/** 検査の結果の要約（Node の側へ返す形） */
export interface AxeSummary {
  violations: { id: string; impact: string | null; nodes: AxeViolationNode[] }[]
  incomplete: { id: string; count: number }[]
  ruleIds: string[]
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
  }
}

/** ページで axe-core を評価し、WCAG 2.0・2.1 の A・AA の規則で検査する。 */
export async function runAxe(page: Page): Promise<AxeSummary> {
  await page.evaluate(axeSource)
  return page.evaluate(
    async (tags) => {
      const result = await window.axe.run(document, { runOnly: { type: 'tag', values: tags } })
      return {
        violations: result.violations.map((rule) => ({
          id: rule.id,
          impact: rule.impact ?? null,
          nodes: rule.nodes.map((node) => {
            const target = node.target.map(String).join(' ')
            const element = node.target.length === 1 ? document.querySelector(target) : null
            return {
              target,
              testId: element?.getAttribute('data-testid') ?? null,
              primaryButton: element?.matches('.mycui-button.variant-primary') ?? false,
            }
          }),
        })),
        incomplete: result.incomplete.map((rule) => ({ id: rule.id, count: rule.nodes.length })),
        ruleIds: [
          ...result.passes,
          ...result.violations,
          ...result.incomplete,
          ...result.inapplicable,
        ].map((rule) => rule.id),
      }
    },
    [...WCAG_TAGS],
  )
}

/**
 * 既知の違反（依頼者が受け入れた制約）。make-you-chic-ui の primary の Button は brand-500 の背景に白い文字で、
 * ブランドカラー green（3.30:1）・orange（3.56:1）では WCAG AA の 4.5:1 に届かない（Intent 260925-user-management の U4、
 * 依頼者の決定。README の「画面の表示の設定（U4）」）。対象は、その組の color-contrast の違反のうち、
 * 名前（data-testid）で指定した primary の Button だけ。ほかの規則・ほかの要素・ほかの組の違反は今までどおり失敗にする。
 */
export const KNOWN_VIOLATIONS: readonly {
  brandColors: readonly string[]
  rule: string
  testIds: readonly string[]
}[] = [
  {
    brandColors: ['green', 'orange'],
    rule: 'color-contrast',
    // 選択中の言語のボタン（既定のロケールは ja）とログインのボタン
    testIds: ['login-language-switch-ja', 'login-form-submit-button'],
  },
]

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
          node.testId !== null &&
          rule.testIds.includes(node.testId),
      )
      const label = `${violation.id} ${node.testId ?? node.target}`
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
 * 招待の管理の画面（U5、060）の状態ごとの既知の違反の名前（green・orange の組の color-contrast の primary の Button だけ）。
 * 当たる名前は、060 の最初の実行の結果で確かめて書いた（U5 の計画の Step 16、9節の決定 4）。
 * 一覧と一致しない（消えた・増えた）ときは失敗にする。make-you-chic-ui が直ったら、この一覧と README を見直す。
 */
export const INVITATION_KNOWN_VIOLATIONS: Readonly<Record<InvitationAxeState, readonly string[]>> =
  {
    list: ['invitation-invite-button'],
    inviteDialog: ['invitation-invite-submit'],
    revokeDialog: [],
    unavailable: [],
  }

/** 招待の管理の画面の検査の状態 */
export type InvitationAxeState = 'list' | 'inviteDialog' | 'revokeDialog' | 'unavailable'

/** 流れるべき規則のうち、結果に無いものを返す。 */
export function missingRequiredRules(summary: AxeSummary): string[] {
  return REQUIRED_RULES.filter((rule) => !summary.ruleIds.includes(rule))
}
