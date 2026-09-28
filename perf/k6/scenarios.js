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
//   appearance    見た目の設定の API（トークンなしの GET /api/appearance。U8 の NFR6.1）
// DSL の場面（Intent 260923-dsl-schema-loader の Performance Validation 用。手順は perf/README.md の「DSL の時間を測る」）。
// 管理者のトークンは5分で切れるため、VU ごとに4分でログインし直す。DSL の本文は DSL_FILE（k6 のコンテナの中のパス）から読む。
//   dslLight      今の状態と履歴を同時 VUS で繰り返す（U4 の NFR1.10 の今の状態・履歴。重い処理の制限を受けない）
//   dslCycle      投入 → 適用 → 投入 → 破棄 → 履歴を1人で繰り返す（U4 の NFR1.10 の適用・破棄。投入は重い処理のため VUS=1 で使う）
//   dslMixed      10MB の DSL の投入とプレビューの表示（1人）に、別々の利用者 VUS 名のログインを重ねる
//                 （U4 の NFR1.12・U4-POOL。コンテナの上限を配備の既定 2g にして流し、止まらないこと・失敗が無いことを見る）
//                 ログインの VU は試験全体で重ならない番号（exec.vu.idInTest）で利用者を選ぶため、VU どうしで利用者が
//                 重ならない。番号は VUS + 1 まで取りうるため、試験用の利用者が VUS + 1 名（PERF_USER_COUNT、既定 11）要る。
//                 VU ごとの最初の回に「loginLoop-user vu=<番号> user=<試験用の利用者>」の1行を出す（重なりの確かめ用）。
// 利用者の設定の場面（Intent 260925-user-management の U2。手順は perf/README.md の「利用者の設定と招待の場面」）。
// ID は construction/u2-user-preferences/nfr-requirements/performance-requirements.md の NFR。
//   preferencesGet      自分の設定の取得（GET /api/me/preferences、200。NFR6.1 の p95 1 秒）
//   preferencesSave     自分の設定の保存の成功（PUT /api/me/preferences、200。NFR6.2 の p95 1 秒）
//   preferencesInvalid  自分の設定の保存の入力の誤り（400 VALIDATION_FAILED。NFR6.2 の p95 1 秒）
//   passwordChange      パスワードの変更の成功（POST /api/me/password、204。NFR6.3 の p95 2 秒）。
//                       専用の利用者 perf-pw01〜（VU ごとに1人）で、変更の前後のパスワードを交互に使う
//   passwordMismatch    今のパスワードの誤り（400 PASSWORD_CURRENT_MISMATCH。NFR6.4 の p95 1 秒）
//   passwordInvalid     入力の誤り（400 VALIDATION_FAILED。NFR6.4 の p95 1 秒）
// 招待と登録の完了の場面（同じ Intent の U3。受け手は使い捨ての環境の Mailpit（profile mail））。
// ID は construction/u3-invitation/nfr-requirements/performance-requirements.md の NFR。BR7.4 の経路は入れない（Unverified）。
//   invite                招待（POST /api/admin/invitations、201。NFR6.1 の p95 5 秒）。回ごとに違う宛先（example.com）
//   invitationResend      送り直し（200。NFR6.1 の p95 5 秒）。setup で VU の数だけ招待を作り、VU ごとに1件を送り直す
//   invitationList        一覧の1ページ目と最後のページ（200。NFR6.3 の p95 1 秒）。setup で招待中を LIST_PENDING_MIN 件以上にする
//   invitationCancel      取り消し（204。NFR6.3 の p95 1 秒）。setup で ITERATIONS 件の招待を作り、全 VU で1件ずつ取り消す
//   registrationVerify    リンクの確かめ（有効 200、形の誤り・見つからない 404。NFR6.3 の p95 1 秒）
//   registrationComplete  登録の完了の成功（204。NFR6.4 の p95 1 秒）。setup で ITERATIONS 件の招待を作り、
//                         Mailpit の API で受けたメールの本文のリンクからトークンを取り出す（トークンは出力しない）
//   registrationInvalid   登録の完了の入力の誤り（400 VALIDATION_FAILED。NFR6.5 の p95 1 秒）
//   registrationRejected  登録の完了のリンクの拒否（見つからない・形の誤り、404 REGISTRATION_LINK_INVALID。NFR6.5 の p95 1 秒）
// U2・U3 の場面は、閾値に加えて checks の率 1（状態コードの誤りを混ぜた p95 で合格にしない）を置く。
import http from 'k6/http'
import exec from 'k6/execution'
import { check, fail, sleep } from 'k6'

const BASE = __ENV.BASE_URL || 'http://app:8080'
const SCENARIO = __ENV.SCENARIO
const VUS = Number(__ENV.VUS || 10)
const DURATION = __ENV.DURATION || '60s'
const USER_PASSWORD = __ENV.PERF_USER_PASSWORD
const ADMIN_EMAIL = __ENV.PERF_ADMIN_EMAIL
const ADMIN_PASSWORD = __ENV.PERF_ADMIN_PASSWORD
// 入れてある試験用の利用者の数（perf-user01 から。dslMixed では VUS + 1 名以上が要る）
const PERF_USER_COUNT = Number(__ENV.PERF_USER_COUNT || 11)
const REFRESH_COOKIE = 'mastersmith_refresh'
const JSON_HEADERS = { 'Content-Type': 'application/json', Origin: BASE }
const DSL_API = `${BASE}/api/admin/dsl`
const DSL_SCENARIOS = ['dslLight', 'dslCycle', 'dslMixed']
// DSL の本文は初期化の段でだけ読める（k6 の open）。DSL の場面のときだけ読む。
const DSL_BODY =
  DSL_SCENARIOS.includes(SCENARIO) && __ENV.DSL_FILE ? open(__ENV.DSL_FILE, 'b') : null
// U2・U3 の場面の設定
const MAILPIT = __ENV.MAILPIT_URL || 'http://mailpit:8025'
// 取り消し・登録の完了の回数（setup で同じ数の招待を用意し、全 VU で1回ずつ使い切る）
const ITERATIONS = Number(__ENV.ITERATIONS || 100)
// 一覧の場面で setup が用意する招待中の件数の下限（1ページ 20 件より多くする）
const LIST_PENDING_MIN = Number(__ENV.LIST_PENDING_MIN || 45)
// 入れてあるパスワードの変更の専用の利用者の数（perf-pw01 から。VUS 以上が要る）
const PERF_PW_USER_COUNT = Number(__ENV.PERF_PW_USER_COUNT || 10)
const INVITATION_API = `${BASE}/api/admin/invitations`
const REGISTRATION_API = `${BASE}/api/registration`
const PAGE_SIZE = 20
const TOKEN_LENGTH = 43
const TOKEN_CHARS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_'
// 招待のメールの本文のリンク（ベース URL ＋ /register#token= ＋ トークン）
const TOKEN_IN_MAIL = /\/register#token=([A-Za-z0-9_-]{43})/
// 場面ごとの p95 の閾値（ミリ秒）。キーは要求のタグ name。
const USER_THRESHOLDS = {
  preferencesGet: { preferencesGet: 1000 },
  preferencesSave: { preferencesSave: 1000 },
  preferencesInvalid: { preferencesInvalid: 1000 },
  passwordChange: { passwordChange: 2000 },
  passwordMismatch: { passwordMismatch: 1000 },
  passwordInvalid: { passwordInvalid: 1000 },
  invite: { invite: 5000 },
  invitationResend: { invitationResend: 5000 },
  invitationList: { invitationListFirst: 1000, invitationListLast: 1000 },
  invitationCancel: { invitationCancel: 1000 },
  registrationVerify: {
    registrationVerify: 1000,
    registrationVerifyMalformed: 1000,
    registrationVerifyNotFound: 1000,
  },
  registrationComplete: { registrationComplete: 1000 },
  registrationInvalid: { registrationInvalid: 1000 },
  registrationRejected: { registrationRejectedNotFound: 1000, registrationRejectedMalformed: 1000 },
}
// 用意した招待を1回ずつ使い切る場面（回数で終わる）
const ONE_SHOT_SCENARIOS = ['invitationCancel', 'registrationComplete']
// setup で招待を用意する場面
const INVITATION_SETUP_SCENARIOS = [
  'invitationResend',
  'invitationList',
  'invitationCancel',
  'registrationVerify',
  'registrationComplete',
]

function scenariosFor(name) {
  if (name === 'dslMixed') {
    return {
      dslHeavy: { executor: 'constant-vus', vus: 1, duration: DURATION, exec: 'dslHeavy' },
      logins: { executor: 'constant-vus', vus: VUS, duration: DURATION, exec: 'loginLoop' },
    }
  }
  if (ONE_SHOT_SCENARIOS.includes(name)) {
    return {
      [name]: {
        executor: 'shared-iterations',
        vus: VUS,
        iterations: ITERATIONS,
        maxDuration: '10m',
      },
    }
  }
  return { [name]: { executor: 'constant-vus', vus: VUS, duration: DURATION } }
}

// U4 の NFR1.10（今の状態・履歴・破棄・適用の 95% が 1 秒以内）。重なりの失敗（dslMixed）は率で見る。
function thresholdsFor(name) {
  // Intent 260925-user-management の U8 の NFR6.1（トークンなしの GET /api/appearance の 95% が 300 ミリ秒以内）。
  if (name === 'appearance') {
    return { 'http_req_duration{name:appearance}': ['p(95)<300'] }
  }
  if (name === 'dslLight') {
    return {
      'http_req_duration{name:dslStatus}': ['p(95)<1000'],
      'http_req_duration{name:dslHistory}': ['p(95)<1000'],
    }
  }
  if (name === 'dslCycle') {
    return {
      'http_req_duration{name:dslApply}': ['p(95)<1000'],
      'http_req_duration{name:dslDiscard}': ['p(95)<1000'],
    }
  }
  if (name === 'dslMixed') {
    return { 'checks{scenario:logins}': ['rate==1'], 'checks{scenario:dslHeavy}': ['rate==1'] }
  }
  // Intent 260925-user-management の U2・U3（出典の ID は先頭のコメント）
  if (USER_THRESHOLDS[name]) {
    const thresholds = { [`checks{scenario:${name}}`]: ['rate==1'] }
    for (const [tag, ms] of Object.entries(USER_THRESHOLDS[name])) {
      thresholds[`http_req_duration{name:${tag}}`] = [`p(95)<${ms}`]
    }
    return thresholds
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
// 招待の用意（取り消し・登録の完了では ITERATIONS 件）とメールの読み取りに時間がかかるため、
// 招待を用意する場面だけ setup の時間の上限を既定の 60 秒から延ばす（ほかの場面は既定のまま）。
if (INVITATION_SETUP_SCENARIOS.includes(SCENARIO)) options.setupTimeout = '10m'

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
  if (DSL_SCENARIOS.includes(SCENARIO) && SCENARIO !== 'dslLight' && !DSL_BODY)
    fail('DSL_FILE を指定してください')
  // dslMixed のログインの VU の番号は VUS + 1 まで取りうる（dslHeavy の VU が途中の番号を取るため）。
  if (SCENARIO === 'dslMixed' && VUS + 1 > PERF_USER_COUNT) {
    fail(
      `dslMixed には試験用の利用者が VUS + 1 = ${VUS + 1} 名要ります（PERF_USER_COUNT=${PERF_USER_COUNT}）。` +
        '利用者を足して PERF_USER_COUNT を合わせてください',
    )
  }
  if (SCENARIO === 'passwordChange' && VUS > PERF_PW_USER_COUNT) {
    fail(
      `passwordChange には専用の利用者が VUS = ${VUS} 名要ります（PERF_PW_USER_COUNT=${PERF_PW_USER_COUNT}）。` +
        '利用者を足して PERF_PW_USER_COUNT を合わせてください',
    )
  }
  const tokens = {}
  if (SCENARIO === 'adminCheck') tokens.admin = tokenOf(login(ADMIN_EMAIL, ADMIN_PASSWORD))
  if (SCENARIO === 'forbidden') tokens.user = tokenOf(login(userEmail(1), USER_PASSWORD))
  // U3 の場面の用意。招待の宛先を実行ごとに変えるための識別（409 を混ぜない）。
  if (SCENARIO === 'invite' || INVITATION_SETUP_SCENARIOS.includes(SCENARIO)) {
    tokens.runId = Date.now().toString(36)
  }
  if (INVITATION_SETUP_SCENARIOS.includes(SCENARIO)) setupInvitations(tokens)
  return tokens
}

// U3 の場面の用意（招待の API で管理者として招待を出す。登録の完了に使うトークンは Mailpit で受けたメールから取り出す）。
function setupInvitations(data) {
  const admin = {
    Authorization: `Bearer ${tokenOf(login(ADMIN_EMAIL, ADMIN_PASSWORD))}`,
    Origin: BASE,
  }
  if (SCENARIO === 'invitationResend') {
    data.invitationIds = inviteMany(admin, data.runId, 'resend', VUS).map((i) => i.id)
  } else if (SCENARIO === 'invitationCancel') {
    data.invitationIds = inviteMany(admin, data.runId, 'cancel', ITERATIONS).map((i) => i.id)
  } else if (SCENARIO === 'invitationList') {
    const total = invitationTotal(admin)
    if (total < LIST_PENDING_MIN) inviteMany(admin, data.runId, 'list', LIST_PENDING_MIN - total)
    const after = invitationTotal(admin)
    if (after <= PAGE_SIZE) fail(`招待中が ${PAGE_SIZE} 件以下です（${after} 件）`)
    data.lastPage = Math.ceil(after / PAGE_SIZE)
    console.log(`invitationList の用意: 招待中 ${after} 件、最後のページ ${data.lastPage}`)
  } else if (SCENARIO === 'registrationVerify') {
    data.registrationTokens = tokensFromMail(inviteMany(admin, data.runId, 'verify', VUS))
  } else if (SCENARIO === 'registrationComplete') {
    data.registrationTokens = tokensFromMail(inviteMany(admin, data.runId, 'complete', ITERATIONS))
  }
}

function invitationEmail(runId, kind, n) {
  return `perf-${kind}-${runId}-${n}@example.com`
}

// count 件の招待を、同時 VUS 件ずつ出す。全件が 201 で送信の結果が SENT でなければ止める（宛先・トークンは出さない）。
function inviteMany(admin, runId, kind, count) {
  const invited = []
  for (let start = 0; start < count; start += VUS) {
    const batch = []
    for (let n = start; n < Math.min(start + VUS, count); n++) {
      const email = invitationEmail(runId, kind, n)
      batch.push({ email, request: inviteRequest(admin, email, 'setupInvite') })
    }
    const responses = http.batch(batch.map((b) => b.request))
    responses.forEach((res, i) => {
      if (res.status !== 201 || res.json('sendResult') !== 'SENT') {
        fail(`用意の招待に失敗しました: ${res.status}（${start + i + 1} 件目）`)
      }
      invited.push({ id: res.json('invitationId'), email: batch[i].email })
    })
  }
  console.log(`用意の招待: ${invited.length} 件`)
  return invited
}

function inviteRequest(admin, email, name) {
  return {
    method: 'POST',
    url: INVITATION_API,
    body: JSON.stringify({ email, language: 'ja' }),
    params: { headers: { ...admin, 'Content-Type': 'application/json' }, tags: { name } },
  }
}

function invitationTotal(admin) {
  const res = http.get(`${INVITATION_API}?page=1`, { headers: admin, tags: { name: 'setupList' } })
  if (res.status !== 200) fail(`招待の一覧を読めません: ${res.status}`)
  return res.json('total')
}

// 招待ごとに、Mailpit の API で宛先のメールを探し、本文のリンクからトークンを取り出す。
// トークン・リンク・本文は、ログ・console.log・止めるときの文言に出さない。
function tokensFromMail(invited) {
  return invited.map((inv, i) => {
    for (let attempt = 0; attempt < 10; attempt++) {
      const token = tokenFromMail(inv.email)
      if (token) return token
      sleep(0.5)
    }
    return fail(`招待のメールからトークンを取り出せません（${i + 1} 件目）`)
  })
}

function tokenFromMail(email) {
  const query = encodeURIComponent(`to:"${email}"`)
  const search = http.get(`${MAILPIT}/api/v1/search?query=${query}`, {
    tags: { name: 'mailpitSearch' },
  })
  if (search.status !== 200) return null
  // 検索は部分一致のため、宛先が完全に一致するものだけを使う（宛先は実行ごと・招待ごとに違い、1通だけのはず）。
  const messages = (search.json('messages') || []).filter(
    (m) => Array.isArray(m.To) && m.To.some((to) => to.Address === email),
  )
  if (messages.length !== 1) return null
  const message = http.get(`${MAILPIT}/api/v1/message/${encodeURIComponent(messages[0].ID)}`, {
    tags: { name: 'mailpitMessage' },
  })
  if (message.status !== 200) return null
  const body = `${message.json('Text') || ''}\n${message.json('HTML') || ''}`
  const found = TOKEN_IN_MAIL.exec(body)
  return found ? found[1] : null
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
  const status = http.get(`${DSL_API}/status`, {
    headers: adminAuth(),
    tags: { name: 'dslStatus' },
  })
  check(status, { 'status 200': (r) => r.status === 200 })
  const history = http.get(`${DSL_API}/history`, {
    headers: adminAuth(),
    tags: { name: 'dslHistory' },
  })
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
  const discard = http.del(`${DSL_API}/preview`, null, {
    headers: adminAuth(),
    tags: { name: 'dslDiscard' },
  })
  check(discard, { 'discard 204': (r) => r.status === 204 })
  const history = http.get(`${DSL_API}/history`, {
    headers: adminAuth(),
    tags: { name: 'dslHistory' },
  })
  check(history, { 'history 200': (r) => r.status === 200 })
}

// dslMixed の重い側（1人）: 10MB の投入と、プレビューの表示（照合を含む）を繰り返す。
export function dslHeavy() {
  check(dslSubmit('dslSubmit'), { 'submit 201': (r) => r.status === 201 })
  const preview = http.get(`${DSL_API}/preview`, {
    headers: adminAuth(),
    tags: { name: 'dslPreview' },
    timeout: '60s',
  })
  check(preview, { 'preview 200': (r) => r.status === 200 })
}

// dslMixed の軽い側: 別々の利用者のログイン。利用者は試験全体で重ならない VU の番号だけで決め、番号を畳まない
// （剰余で畳むと、dslHeavy の VU が途中の番号を取ったときに、ログインの VU どうしで利用者が重なる）。
export function loginLoop() {
  const user = userEmail(exec.vu.idInTest)
  if (!vuState.loginUserLogged) {
    // 試験用の利用者のメールアドレスだけを出す（秘密の値は含まない）。
    console.log(`loginLoop-user vu=${exec.vu.idInTest} user=${user}`)
    vuState.loginUserLogged = true
  }
  const res = login(user, USER_PASSWORD)
  check(res, { 'login 200': (r) => r.status === 200 })
}

// ---- U2（利用者の設定） ----

// 試験用の利用者のアクセストークン（VU ごと。4分でログインし直す）。パスワードは呼ぶときの値を使う。
function userAuth(email, password) {
  const now = Date.now()
  if (!vuState.userToken || now - vuState.userTokenAt > 240_000) {
    vuState.userToken = tokenOf(login(email, password))
    vuState.userTokenAt = now
  }
  return { Authorization: `Bearer ${vuState.userToken}`, Origin: BASE }
}

// U2 の設定の場面の利用者（perf-user01〜10 を VU ごとに当てる。既存の loginSuccess と同じ当て方）。
function preferenceUserAuth() {
  return userAuth(userEmail(((exec.vu.idInTest - 1) % 10) + 1), USER_PASSWORD)
}

function codeOf(res) {
  try {
    return res.json('code')
  } catch {
    return null
  }
}

function jsonPut(url, body, headers, name) {
  return http.put(url, JSON.stringify(body), {
    headers: { ...headers, 'Content-Type': 'application/json' },
    tags: { name },
  })
}

function jsonPost(url, body, headers, name) {
  return http.post(url, JSON.stringify(body), {
    headers: { ...headers, 'Content-Type': 'application/json' },
    tags: { name },
  })
}

function preferencesGet() {
  const res = http.get(`${BASE}/api/me/preferences`, {
    headers: preferenceUserAuth(),
    tags: { name: 'preferencesGet' },
  })
  check(res, { 200: (r) => r.status === 200 })
}

// 保存の成功。氏名は入れたときと同じ値のまま、テーマを回ごとに変える。
function preferencesSave() {
  const email = userEmail(((exec.vu.idInTest - 1) % 10) + 1)
  const theme = exec.vu.iterationInScenario % 2 === 0 ? 'light' : 'dark'
  const body = { displayName: email, language: 'ja', theme, fontSize: 'md' }
  const res = jsonPut(`${BASE}/api/me/preferences`, body, preferenceUserAuth(), 'preferencesSave')
  check(res, { 200: (r) => r.status === 200 })
}

// 保存の入力の誤り（言語が許されない値）。DB に触れずに 400 になる。
function preferencesInvalid() {
  const email = userEmail(((exec.vu.idInTest - 1) % 10) + 1)
  const body = { displayName: email, language: 'xx', theme: 'light', fontSize: 'md' }
  const res = jsonPut(
    `${BASE}/api/me/preferences`,
    body,
    preferenceUserAuth(),
    'preferencesInvalid',
  )
  check(res, {
    400: (r) => r.status === 400,
    VALIDATION_FAILED: (r) => codeOf(r) === 'VALIDATION_FAILED',
  })
}

// パスワードの変更の専用の利用者（perf-pw01〜）。試験全体で重ならない VU の番号で1人を当てる。
function pwUserEmail(n) {
  return `perf-pw${String(n).padStart(2, '0')}@example.test`
}

// 変更の前後のパスワード（入れたときのパスワードと、その後ろに文字を足したもの）。値は出力しない。
function pwCandidates() {
  return [USER_PASSWORD, `${USER_PASSWORD}-alt`]
}

// VU の最初の回に、今のパスワードを決める。前の実行が後ろのパスワードで終わっていることがあるため、
// 入れたときの値で入れなければ後ろの値で入る（そのときログインの失敗が1件、監査に残る）。
function pwCurrent(email) {
  if (vuState.pwCurrent === undefined) {
    for (const password of pwCandidates()) {
      const res = login(email, password)
      if (res.status === 200) {
        vuState.pwCurrent = password
        vuState.userToken = res.json('accessToken')
        vuState.userTokenAt = Date.now()
        return password
      }
    }
    fail(`パスワードの変更の利用者でログインできません（VU ${exec.vu.idInTest}）`)
  }
  return vuState.pwCurrent
}

function passwordChange() {
  const email = pwUserEmail(exec.vu.idInTest)
  const current = pwCurrent(email)
  const [first, second] = pwCandidates()
  const next = current === first ? second : first
  const body = { currentPassword: current, newPassword: next, newPasswordConfirmation: next }
  const res = jsonPost(`${BASE}/api/me/password`, body, userAuth(email, current), 'passwordChange')
  if (check(res, { 204: (r) => r.status === 204 })) vuState.pwCurrent = next
}

// 今のパスワードの誤り（照合1回、監査1件）。誤りの回数は数えないため、ロックは起きない。
function passwordMismatch() {
  const next = `${USER_PASSWORD}-alt`
  const body = {
    currentPassword: 'wrong-password-123',
    newPassword: next,
    newPasswordConfirmation: next,
  }
  const res = jsonPost(`${BASE}/api/me/password`, body, preferenceUserAuth(), 'passwordMismatch')
  check(res, {
    400: (r) => r.status === 400,
    PASSWORD_CURRENT_MISMATCH: (r) => codeOf(r) === 'PASSWORD_CURRENT_MISMATCH',
  })
}

// 入力の誤り（新しいパスワードが短く、確かめと一致しない）。DB を使わない。
function passwordInvalid() {
  const body = {
    currentPassword: USER_PASSWORD,
    newPassword: 'short',
    newPasswordConfirmation: 'other',
  }
  const res = jsonPost(`${BASE}/api/me/password`, body, preferenceUserAuth(), 'passwordInvalid')
  check(res, {
    400: (r) => r.status === 400,
    VALIDATION_FAILED: (r) => codeOf(r) === 'VALIDATION_FAILED',
  })
}

// ---- U3（招待と登録の完了） ----

function invite(data) {
  const email = invitationEmail(data.runId, `inv${exec.vu.idInTest}`, exec.vu.iterationInScenario)
  const req = inviteRequest(adminAuth(), email, 'invite')
  const res = http.post(req.url, req.body, req.params)
  check(res, {
    201: (r) => r.status === 201,
    SENT: (r) => r.status === 201 && r.json('sendResult') === 'SENT',
  })
}

function invitationResend(data) {
  const id = data.invitationIds[(exec.vu.idInTest - 1) % data.invitationIds.length]
  const res = http.post(`${INVITATION_API}/${id}/resend`, null, {
    headers: adminAuth(),
    tags: { name: 'invitationResend' },
  })
  check(res, {
    200: (r) => r.status === 200,
    SENT: (r) => r.status === 200 && r.json('sendResult') === 'SENT',
  })
}

function invitationList(data) {
  const first = http.get(`${INVITATION_API}?page=1`, {
    headers: adminAuth(),
    tags: { name: 'invitationListFirst' },
  })
  check(first, {
    'first 200': (r) => r.status === 200,
    'first 20 items': (r) => r.status === 200 && r.json('items').length === PAGE_SIZE,
  })
  const last = http.get(`${INVITATION_API}?page=${data.lastPage}`, {
    headers: adminAuth(),
    tags: { name: 'invitationListLast' },
  })
  check(last, {
    'last 200': (r) => r.status === 200,
    'last has items': (r) => r.status === 200 && r.json('items').length > 0,
  })
}

function invitationCancel(data) {
  const id = data.invitationIds[exec.scenario.iterationInTest]
  const res = http.post(`${INVITATION_API}/${id}/cancel`, null, {
    headers: adminAuth(),
    tags: { name: 'invitationCancel' },
  })
  check(res, { 204: (r) => r.status === 204 })
}

// 形は正しいが見つからないトークン（43 文字の URL で使える Base64 の文字）。
function randomToken() {
  let token = ''
  for (let i = 0; i < TOKEN_LENGTH; i++) {
    token += TOKEN_CHARS[Math.floor(Math.random() * TOKEN_CHARS.length)]
  }
  return token
}

function registrationVerify(data) {
  const token = data.registrationTokens[(exec.vu.idInTest - 1) % data.registrationTokens.length]
  const valid = jsonPost(`${REGISTRATION_API}/verify`, { token }, {}, 'registrationVerify')
  check(valid, { 'valid 200': (r) => r.status === 200 })
  const malformed = jsonPost(
    `${REGISTRATION_API}/verify`,
    { token: 'not-a-token' },
    {},
    'registrationVerifyMalformed',
  )
  check(malformed, { 'malformed 404': (r) => r.status === 404 })
  const notFound = jsonPost(
    `${REGISTRATION_API}/verify`,
    { token: randomToken() },
    {},
    'registrationVerifyNotFound',
  )
  check(notFound, { 'not found 404': (r) => r.status === 404 })
}

function completeBody(token, n) {
  return {
    token,
    displayName: `perf-reg ${n}`,
    password: USER_PASSWORD,
    passwordConfirmation: USER_PASSWORD,
    language: 'ja',
    theme: 'system',
    fontSize: 'md',
  }
}

function registrationComplete(data) {
  const n = exec.scenario.iterationInTest
  const body = completeBody(data.registrationTokens[n], n)
  const res = jsonPost(`${REGISTRATION_API}/complete`, body, {}, 'registrationComplete')
  check(res, { 204: (r) => r.status === 204 })
}

// 入力の誤り（パスワードが短く、確かめと一致しない）。内部DB を引かず、招待を消費しない。
function registrationInvalid() {
  const body = {
    ...completeBody(randomToken(), 0),
    password: 'short',
    passwordConfirmation: 'other',
  }
  const res = jsonPost(`${REGISTRATION_API}/complete`, body, {}, 'registrationInvalid')
  check(res, {
    400: (r) => r.status === 400,
    VALIDATION_FAILED: (r) => codeOf(r) === 'VALIDATION_FAILED',
  })
}

// リンクの拒否（見つからない・形の誤り）。入力は正しい。どちらも監査に REGISTRATION_FAILED が1件ずつ残る。
function registrationRejected() {
  const isLinkInvalid = {
    404: (r) => r.status === 404,
    REGISTRATION_LINK_INVALID: (r) => codeOf(r) === 'REGISTRATION_LINK_INVALID',
  }
  const notFound = jsonPost(
    `${REGISTRATION_API}/complete`,
    completeBody(randomToken(), 0),
    {},
    'registrationRejectedNotFound',
  )
  check(notFound, isLinkInvalid)
  const malformed = jsonPost(
    `${REGISTRATION_API}/complete`,
    completeBody('not-a-token', 0),
    {},
    'registrationRejectedMalformed',
  )
  check(malformed, isLinkInvalid)
}

// U2・U3 の場面の名前と処理
const USER_SCENARIOS = {
  preferencesGet,
  preferencesSave,
  preferencesInvalid,
  passwordChange,
  passwordMismatch,
  passwordInvalid,
  invite,
  invitationResend,
  invitationList,
  invitationCancel,
  registrationVerify,
  registrationComplete,
  registrationInvalid,
  registrationRejected,
}

export default function (tokens) {
  const vu = exec.vu.idInTest
  if (SCENARIO === 'health') {
    const res = http.get(`${BASE}/actuator/health`, { tags: { name: 'health' } })
    check(res, { 200: (r) => r.status === 200 })
  } else if (SCENARIO === 'loginSuccess') {
    const res = login(userEmail(((vu - 1) % 10) + 1), USER_PASSWORD)
    check(res, { 200: (r) => r.status === 200 })
  } else if (SCENARIO === 'loginFailure') {
    const res = login(
      `perf-nobody-${vu}-${exec.vu.iterationInScenario}@example.test`,
      'wrong-password-123',
    )
    check(res, { 401: (r) => r.status === 401 })
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
    if (check(res, { 200: (r) => r.status === 200 }) && res.cookies[REFRESH_COOKIE]) {
      vuState.cookie = res.cookies[REFRESH_COOKIE][0].value
    }
  } else if (SCENARIO === 'adminCheck') {
    const res = http.get(`${BASE}/api/admin/check`, {
      headers: { Authorization: `Bearer ${tokens.admin}` },
      tags: { name: 'adminCheck' },
    })
    check(res, { 204: (r) => r.status === 204 })
  } else if (SCENARIO === 'forbidden') {
    const res = http.get(`${BASE}/api/admin/check`, {
      headers: { Authorization: `Bearer ${tokens.user}` },
      tags: { name: 'forbidden' },
    })
    check(res, { 403: (r) => r.status === 403 })
  } else if (SCENARIO === 'appearance') {
    // トークンを付けない（画面の側も付けない。U8 の BR3.3）。試験用の利用者は要らず、監査ログも増えない。
    const res = http.get(`${BASE}/api/appearance`, { tags: { name: 'appearance' } })
    check(res, {
      200: (r) => r.status === 200,
      'two items': (r) => {
        if (r.status !== 200) return false
        const keys = Object.keys(r.json()).sort()
        return keys.length === 2 && keys[0] === 'brandColor' && keys[1] === 'fontFamily'
      },
    })
  } else if (SCENARIO === 'dslLight') {
    dslLight()
  } else if (SCENARIO === 'dslCycle') {
    dslCycle()
  } else if (USER_SCENARIOS[SCENARIO]) {
    USER_SCENARIOS[SCENARIO](tokens)
  } else {
    fail(`知らない SCENARIO です: ${SCENARIO}`)
  }
}
