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
// DSL の場面（Intent 260923-dsl-schema-loader の Performance Validation 用。手順は perf/README.md の「DSL の時間を測る」）。
// 管理者のトークンは5分で切れるため、VU ごとに4分でログインし直す。DSL の本文は DSL_FILE（k6 のコンテナの中のパス）から読む。
//   dslLight      今の状態と履歴を同時 VUS で繰り返す（U4 の NFR1.10 の今の状態・履歴。重い処理の制限を受けない）
//   dslCycle      投入 → 適用 → 投入 → 破棄 → 履歴を1人で繰り返す（U4 の NFR1.10 の適用・破棄。投入は重い処理のため VUS=1 で使う）
//   dslMixed      10MB の DSL の投入とプレビューの表示（1人）に、別々の利用者 VUS 名のログインを重ねる
//                 （U4 の NFR1.12・U4-POOL。コンテナの上限を 1g にして流し、止まらないこと・失敗が無いことを見る）
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
const DSL_API = `${BASE}/api/admin/dsl`
const DSL_SCENARIOS = ['dslLight', 'dslCycle', 'dslMixed']
// DSL の本文は初期化の段でだけ読める（k6 の open）。DSL の場面のときだけ読む。
const DSL_BODY = DSL_SCENARIOS.includes(SCENARIO) && __ENV.DSL_FILE ? open(__ENV.DSL_FILE, 'b') : null

function scenariosFor(name) {
  if (name === 'dslMixed') {
    return {
      dslHeavy: { executor: 'constant-vus', vus: 1, duration: DURATION, exec: 'dslHeavy' },
      logins: { executor: 'constant-vus', vus: VUS, duration: DURATION, exec: 'loginLoop' },
    }
  }
  return { [name]: { executor: 'constant-vus', vus: VUS, duration: DURATION } }
}

// U4 の NFR1.10（今の状態・履歴・破棄・適用の 95% が 1 秒以内）。重なりの失敗（dslMixed）は率で見る。
function thresholdsFor(name) {
  if (name === 'dslLight') {
    return { 'http_req_duration{name:dslStatus}': ['p(95)<1000'], 'http_req_duration{name:dslHistory}': ['p(95)<1000'] }
  }
  if (name === 'dslCycle') {
    return { 'http_req_duration{name:dslApply}': ['p(95)<1000'], 'http_req_duration{name:dslDiscard}': ['p(95)<1000'] }
  }
  if (name === 'dslMixed') {
    return { 'checks{scenario:logins}': ['rate==1'], 'checks{scenario:dslHeavy}': ['rate==1'] }
  }
  return {}
}

export const options = {
  scenarios: scenariosFor(SCENARIO),
  thresholds: thresholdsFor(SCENARIO),
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
  if (DSL_SCENARIOS.includes(SCENARIO) && SCENARIO !== 'dslLight' && !DSL_BODY) fail('DSL_FILE を指定してください')
  const tokens = {}
  if (SCENARIO === 'adminCheck') tokens.admin = tokenOf(login(ADMIN_EMAIL, ADMIN_PASSWORD))
  if (SCENARIO === 'forbidden') tokens.user = tokenOf(login(userEmail(1), USER_PASSWORD))
  return tokens
}

const vuState = {}

// 管理者のアクセストークン（VU ごと。4分でログインし直す）。
function adminAuth() {
  const now = Date.now()
  if (!vuState.adminToken || now - vuState.adminTokenAt > 240_000) {
    vuState.adminToken = tokenOf(login(ADMIN_EMAIL, ADMIN_PASSWORD))
    vuState.adminTokenAt = now
  }
  return { Authorization: `Bearer ${vuState.adminToken}`, Origin: BASE }
}

function dslSubmit(name) {
  return http.post(`${DSL_API}/preview?source=UPLOAD`, DSL_BODY, {
    headers: { ...adminAuth(), 'Content-Type': 'application/yaml' },
    tags: { name },
    timeout: '60s',
  })
}

function dslPreviewId() {
  const res = http.get(`${DSL_API}/status`, { headers: adminAuth(), tags: { name: 'dslStatus' } })
  check(res, { 'status 200': (r) => r.status === 200 })
  return res.status === 200 && res.json('preview') ? res.json('preview.previewId') : null
}

function dslLight() {
  const status = http.get(`${DSL_API}/status`, { headers: adminAuth(), tags: { name: 'dslStatus' } })
  check(status, { 'status 200': (r) => r.status === 200 })
  const history = http.get(`${DSL_API}/history`, { headers: adminAuth(), tags: { name: 'dslHistory' } })
  check(history, { 'history 200': (r) => r.status === 200 })
}

function dslCycle() {
  check(dslSubmit('dslSubmit'), { 'submit 201': (r) => r.status === 201 })
  const previewId = dslPreviewId()
  const apply = http.post(`${DSL_API}/apply`, JSON.stringify({ previewId }), {
    headers: { ...adminAuth(), 'Content-Type': 'application/json' },
    tags: { name: 'dslApply' },
  })
  check(apply, { 'apply 200': (r) => r.status === 200 })
  check(dslSubmit('dslSubmit'), { 'submit 201': (r) => r.status === 201 })
  const discard = http.del(`${DSL_API}/preview`, null, { headers: adminAuth(), tags: { name: 'dslDiscard' } })
  check(discard, { 'discard 204': (r) => r.status === 204 })
  const history = http.get(`${DSL_API}/history`, { headers: adminAuth(), tags: { name: 'dslHistory' } })
  check(history, { 'history 200': (r) => r.status === 200 })
}

// dslMixed の重い側（1人）: 10MB の投入と、プレビューの表示（照合を含む）を繰り返す。
export function dslHeavy() {
  check(dslSubmit('dslSubmit'), { 'submit 201': (r) => r.status === 201 })
  const preview = http.get(`${DSL_API}/preview`, { headers: adminAuth(), tags: { name: 'dslPreview' }, timeout: '60s' })
  check(preview, { 'preview 200': (r) => r.status === 200 })
}

// dslMixed の軽い側: 別々の利用者のログイン（loginSuccess と同じ）。
export function loginLoop() {
  const res = login(userEmail(((exec.vu.idInTest - 1) % 10) + 1), USER_PASSWORD)
  check(res, { 'login 200': (r) => r.status === 200 })
}

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
  } else if (SCENARIO === 'dslLight') {
    dslLight()
  } else if (SCENARIO === 'dslCycle') {
    dslCycle()
  } else {
    fail(`知らない SCENARIO です: ${SCENARIO}`)
  }
}
