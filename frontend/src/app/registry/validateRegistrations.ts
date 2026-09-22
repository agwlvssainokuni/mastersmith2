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
// 登録の検査（BR7.2）。重複や決まりに合わない登録があれば、どの登録が問題かを示して起動を止める。
import { RegistrationError, type FeatureRegistration } from './types'

/** ホーム（U1 が用意する）の URL。機能は同じ URL を登録できない。 */
export const HOME_PATH = '/'

const FEATURE_ID_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/

/** 同じ値が2回以上出てきたものを、最初に出てきた登録とともに記録する。 */
function collectDuplicates(entries: readonly { value: string; owner: string }[]): string[] {
  const owners = new Map<string, string[]>()
  for (const { value, owner } of entries) {
    owners.set(value, [...(owners.get(value) ?? []), owner])
  }
  return [...owners.entries()]
    .filter(([, list]) => list.length > 1)
    .map(([value, list]) => `"${value}"（${list.join('、')}）`)
}

function checkMessages(registration: FeatureRegistration, baseKeys: ReadonlySet<string>): string[] {
  const { featureId, messages } = registration
  if (!messages) {
    return []
  }
  const problems: string[] = []
  const prefix = `${featureId}.`
  const jaKeys = Object.keys(messages.ja)
  const enKeys = Object.keys(messages.en)
  for (const key of new Set([...jaKeys, ...enKeys])) {
    if (!key.startsWith(prefix)) {
      problems.push(`${featureId}: 文言の鍵 "${key}" は "${prefix}" で始めてください`)
    }
    if (baseKeys.has(key)) {
      problems.push(`${featureId}: 文言の鍵 "${key}" は骨組みの文言と重複しています`)
    }
    if (!messages.ja[key] || !messages.en[key]) {
      problems.push(`${featureId}: 文言の鍵 "${key}" に日本語と英語の両方の文言がありません`)
    }
  }
  return problems
}

/**
 * 登録を検査する。問題があれば、すべての問題を示す {@link RegistrationError} を投げる。
 *
 * @param registrations 読み込んだ登録の一覧
 * @param baseMessageKeys 骨組み（U1）の文言の鍵（機能の文言の鍵と重複させない）
 */
export function validateRegistrations(
  registrations: readonly FeatureRegistration[],
  baseMessageKeys: ReadonlySet<string> = new Set(),
): void {
  const problems: string[] = []

  for (const { featureId } of registrations) {
    if (!FEATURE_ID_PATTERN.test(featureId)) {
      problems.push(`featureId "${featureId}" は小文字の英字・数字・ハイフンで書いてください`)
    }
  }
  for (const duplicate of collectDuplicates(
    registrations.map((r) => ({ value: r.featureId, owner: r.featureId })),
  )) {
    problems.push(`featureId が重複しています: ${duplicate}`)
  }

  const routes = registrations.flatMap((r) =>
    (r.routes ?? []).map((route) => ({ route, owner: r.featureId })),
  )
  for (const { route, owner } of routes) {
    if (!route.path.startsWith('/')) {
      problems.push(`${owner}: 画面の URL "${route.path}" は / で始めてください`)
    }
  }
  for (const duplicate of collectDuplicates([
    { value: HOME_PATH, owner: 'ホーム（U1）' },
    ...routes.map(({ route, owner }) => ({ value: route.path, owner })),
  ])) {
    problems.push(`画面の URL が重複しています: ${duplicate}`)
  }

  const loginRoutes = routes.filter(({ route }) => route.role === 'LOGIN')
  if (loginRoutes.length > 1) {
    problems.push(
      `ログイン画面が2つ以上登録されています: ${loginRoutes.map(({ owner }) => owner).join('、')}`,
    )
  }
  for (const { route, owner } of loginRoutes) {
    if (route.layout !== 'STANDALONE' || route.access !== 'PUBLIC') {
      problems.push(`${owner}: ログイン画面 "${route.path}" は STANDALONE・PUBLIC にしてください`)
    }
  }

  const providers = registrations.filter((r) => r.loginStateProvider !== undefined)
  if (providers.length > 1) {
    problems.push(
      `ログイン状態の提供元が2つ以上登録されています: ${providers.map((r) => r.featureId).join('、')}`,
    )
  }

  const sidebarItems = registrations.flatMap((r) =>
    (r.sidebarItems ?? []).map((item) => ({ item, owner: r.featureId })),
  )
  for (const duplicate of collectDuplicates(
    sidebarItems.map(({ item, owner }) => ({ value: item.id, owner })),
  )) {
    problems.push(`サイドバーの項目の id が重複しています: ${duplicate}`)
  }
  const routePaths = new Set([HOME_PATH, ...routes.map(({ route }) => route.path)])
  for (const { item, owner } of sidebarItems) {
    if (!routePaths.has(item.path)) {
      problems.push(
        `${owner}: サイドバーの項目 "${item.id}" の URL "${item.path}" は登録されていません`,
      )
    }
  }

  const userMenuItems = registrations.flatMap((r) =>
    (r.userMenuItems ?? []).map((item) => ({ value: item.id, owner: r.featureId })),
  )
  for (const duplicate of collectDuplicates(userMenuItems)) {
    problems.push(`ユーザーメニューの項目の id が重複しています: ${duplicate}`)
  }

  for (const registration of registrations) {
    problems.push(...checkMessages(registration, baseMessageKeys))
  }

  if (problems.length > 0) {
    throw new RegistrationError(problems)
  }
}
