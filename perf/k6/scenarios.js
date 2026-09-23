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

// 負荷の試験の台本（Performance Validation）。使い捨ての環境（docker/perf/compose.yaml）だけに向けて使う。
// 場面は環境変数 SCENARIO で選ぶ。どれも同時 10（VUS）で DURATION の間くり返し、95 パーセンタイルを出す。
//   health        ヘルスチェック（U1-NFR1.1、U1-NFR1.3 の上限）
//   loginSuccess  別々の利用者 10 名のログイン（U2-NFR1.1 成功、U2-NFR1.6）
//   loginFailure  存在しないメールアドレスのログイン（U2-NFR1.1 失敗。ロックを起こさない）
//   refresh       トークンの更新（U2-NFR1.2）
//   adminCheck    管理者の確認用 API（U3-NFR1.1 成功、U2-NFR1.4 の上限）
//   forbidden     管理者でない利用者の確認用 API の 403（U3-NFR1.1 403、U3-NFR1.3、U4-NFR1.2）
import http from 'k6/http'
import exec from 'k6/execution'
import { check, fail } from 'k6'

const BASE = __ENV.BASE_URL || 'http://app:8080'
const SCENARIO = __ENV.SCENARIO
const VUS = Number(__ENV.VUS || 10)
const DURATION = __ENV.DURATION || '60s'
const USER_PASSWORD = __ENV.PERF_USER_PASSWORD
const ADMIN_EMAIL = __ENV.PERF_ADMIN_EMAIL
const ADMIN_PASSWORD = __ENV.PERF_ADMIN_PASSWORD
const REFRESH_COOKIE = 'mastersmith_refresh'
const JSON_HEADERS = { 'Content-Type': 'application/json', Origin: BASE }

export const options = {
  scenarios: {
    [SCENARIO]: { executor: 'constant-vus', vus: VUS, duration: DURATION },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max', 'count'],
  // 秘密の値を結果に残さないよう、要求の本文や Cookie は出さない。
  discardResponseBodies: false,
}

function userEmail(n) {
  return `perf-user${String(n).padStart(2, '0')}@example.test`
}

function login(email, password) {
  return http.post(`${BASE}/api/auth/login`, JSON.stringify({ email, password }), {
    headers: JSON_HEADERS,
    tags: { name: 'login' },
  })
}

function tokenOf(res) {
  if (res.status !== 200) fail(`ログインに失敗しました: ${res.status}`)
  return res.json('accessToken')
}

export function setup() {
  if (!SCENARIO) fail('SCENARIO を指定してください')
  const tokens = {}
  if (SCENARIO === 'adminCheck') tokens.admin = tokenOf(login(ADMIN_EMAIL, ADMIN_PASSWORD))
  if (SCENARIO === 'forbidden') tokens.user = tokenOf(login(userEmail(1), USER_PASSWORD))
  return tokens
}

const vuState = {}

export default function (tokens) {
  const vu = exec.vu.idInTest
  if (SCENARIO === 'health') {
    const res = http.get(`${BASE}/actuator/health`, { tags: { name: 'health' } })
    check(res, { '200': (r) => r.status === 200 })
  } else if (SCENARIO === 'loginSuccess') {
    const res = login(userEmail(((vu - 1) % 10) + 1), USER_PASSWORD)
    check(res, { '200': (r) => r.status === 200 })
  } else if (SCENARIO === 'loginFailure') {
    const res = login(`perf-nobody-${vu}-${exec.vu.iterationInScenario}@example.test`, 'wrong-password-123')
    check(res, { '401': (r) => r.status === 401 })
  } else if (SCENARIO === 'refresh') {
    if (!vuState.cookie) {
      const res = login(userEmail(((vu - 1) % 10) + 1), USER_PASSWORD)
      tokenOf(res)
      vuState.cookie = res.cookies[REFRESH_COOKIE][0].value
    }
    const res = http.post(`${BASE}/api/auth/session/refresh`, null, {
      headers: { Origin: BASE, Cookie: `${REFRESH_COOKIE}=${vuState.cookie}` },
      tags: { name: 'refresh' },
    })
    if (check(res, { '200': (r) => r.status === 200 }) && res.cookies[REFRESH_COOKIE]) {
      vuState.cookie = res.cookies[REFRESH_COOKIE][0].value
    }
  } else if (SCENARIO === 'adminCheck') {
    const res = http.get(`${BASE}/api/admin/check`, {
      headers: { Authorization: `Bearer ${tokens.admin}` },
      tags: { name: 'adminCheck' },
    })
    check(res, { '204': (r) => r.status === 204 })
  } else if (SCENARIO === 'forbidden') {
    const res = http.get(`${BASE}/api/admin/check`, {
      headers: { Authorization: `Bearer ${tokens.user}` },
      tags: { name: 'forbidden' },
    })
    check(res, { '403': (r) => r.status === 403 })
  } else {
    fail(`知らない SCENARIO です: ${SCENARIO}`)
  }
}
