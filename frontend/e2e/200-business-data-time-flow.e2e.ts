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
// 業務データの日時の代表の流れ（Intent 261102-timezone-ui-polish の U4、計画 Step 14、NFR5.3.1、NFR 要件 Q1: A、
// security-design.md 2.5・2.7・2.8）。この Intent の N6 の E2E の1本（team.md）。
// - 200 は専用の DSL（版の印 E2ET_。support/businessDataTimeDsl.ts）を投入・適用し、後始末はしない（適用した DSL が残る）。
//   200 より後のファイルは適用中の DSL に頼らない（後に足すファイルは、自分で DSL を投入・適用する）。
// - 対象DB は本物の見本の PostgreSQL の実行のスキーマ（./gradlew e2eTest が E2E_BIZ_SCHEMA を作り、表 e2e_event を置く。
//   frontend/e2e/support/businessDataSchema.sql）。見本の行は無く、行はこの流れが作る1行だけ。
// - 前提を自分で作り、前のテストの状態に頼らない。ロール（E2ET_ROLE_<印>。e2e_event に FULL・作成あり）と利用者を作って割り当てる。
//   初期管理者の状態・ロール・プリファレンスは変えない（プリファレンスを変えるのは、この流れで作った利用者だけ）。
// - ブラウザのタイムゾーンは playwright.config.ts の timezoneId（Asia/Tokyo）のまま、利用者のプリファレンスの表示のタイムゾーンを
//   America/New_York にする（ブラウザと表示のタイムゾーンが違う状態を通す。security-design.md 2.7）。
// - 流れ: プリファレンスで America/New_York → 作成の画面で存在しない時刻（2026-03-08 02:30:00）の誤りが出て保存されない →
//   重なる時刻（2026-11-01 01:30:00）を2択の「後（-05:00）」で保存 → 一覧と詳細で 2026-11-01 01:30:00.000000 -05:00（6 桁の列） →
//   更新で名前だけ直して保存し、日時が変わらない（一覧の表示と、更新の要求の本文に日時の欄の鍵が無いことで確かめる）。
// - 時刻の表の見本（businessDataFixtures.ts の timeDefinition・timePage・timeDetail。185 の時刻の状態に使う）の形を、本物の応答と
//   shapeCompare で比べる（project.md の学び。行の値の組は、値が文字か null の組であることだけを確かめて比べから外す）。
// - 行の値は架空の値だけ。作った行の主キーと名前は目印（E2E-BIZ-MARK）を付け、値のファイル（businessValue）に書く。要求の本文は
//   鍵の名前だけを見て、値を記録に出さない。test.step の題・注記・添付に値を入れない。資格情報はプロセスの環境変数で受け、
//   webServer.env に置かない。
import { expect, test, type APIRequestContext, type Page, type Request } from '@playwright/test'
import { BUSINESS_DATA_API } from './support/businessDataApiRoute'
import { timeDefinition, timeDetail, timePage } from './support/businessDataFixtures'
import {
  businessDataTimeDsl,
  businessDataTimeLabels,
  EVENT_COLUMN_LABELS,
  EVENT_COLUMNS,
  EVENT_TABLE,
} from './support/businessDataTimeDsl'
import { requestAdminAccessToken } from './support/invitationSeed'
import {
  createRegisteredUser,
  loginWithForm,
  REGISTERED_DISPLAY_NAME,
  registrationPrerequisites,
} from './support/registeredUser'
import {
  BUSINESS_VALUE_MARK,
  readSecretValues,
  recordSecretValues,
  secretValuesFilePath,
} from './support/secretValues'
import { shapeCompare } from './support/shapeCompare'
import { e2eTraceMode } from './support/traceMode'
import { newUserAdminRunTag } from './support/userAdminRun'
import { openUserMenuItem } from './support/userMenu'

test.use({ trace: e2eTraceMode() })

/** 表示のタイムゾーン（夏時間のある地域） */
const ZONE = 'America/New_York'

/** 夏時間の切り替えで飛ばされる時刻（存在しない時刻） */
const SKIPPED_TIME = '2026-03-08 02:30:00'

/** 夏時間の終わりで2回ある時刻 */
const OVERLAPPING_TIME = '2026-11-01 01:30:00'

/**
 * 2択の「後」を選んで保存した時点の、表示のタイムゾーンでの書式。列は timestamptz(6) で、サーバーは
 * DB の定義の桁（6 桁）に固定して返し、画面は丸めずに出す（機能設計の D1）ため、端数の 6 桁が付く。
 */
const SAVED_TIME_TEXT = '2026-11-01 01:30:00.000000 -05:00'

/** 存在しない時刻の誤りの文（features/tables/messages.ts の tables.error.DATE_TIME_GAP） */
const GAP_MESSAGE = `${ZONE} では、この時刻はありません（夏時間の切り替えで飛ばされる時刻です）`

/** プリファレンスのユーザーメニューの項目 */
const PREFERENCES_ITEM = 'プリファレンス'

/** null を許す項目（見本と本物のどちらかが null でもよい。180 と同じ） */
const NULLABLE = new Set([
  'workRoleId',
  'restrictionReason',
  'listFormat',
  'width',
  'formPart',
  'minLength',
  'maxLength',
  'precision',
  'scale',
  'min',
  'max',
  'defaultValue',
  'key',
])

/** 行の値の組（名前はテーブルごとに違う）を、値が文字か null の組であることを確かめてから決まった形に置き換える（180 と同じ） */
function withoutColumnMaps(value: unknown, broken: string[], at = ''): unknown {
  if (Array.isArray(value)) {
    return value.map((item) => withoutColumnMaps(item, broken, `${at}[]`))
  }
  if (typeof value !== 'object' || value === null) {
    return value
  }
  return Object.fromEntries(
    Object.entries(value as Record<string, unknown>).map(([name, item]) => {
      if ((name === 'key' || name === 'values') && typeof item === 'object' && item !== null) {
        const entries = Object.values(item as Record<string, unknown>)
        if (!entries.every((entry) => typeof entry === 'string' || entry === null)) {
          broken.push(at === '' ? name : `${at}.${name}`)
        }
        return [name, { columns: 'string-or-null' }]
      }
      return [name, withoutColumnMaps(item, broken, at === '' ? name : `${at}.${name}`)]
    }),
  )
}

/** トークンつきで POST し、JSON を読む（状態コードを確かめる） */
async function postJson(
  request: APIRequestContext,
  token: string,
  path: string,
  data: unknown,
): Promise<unknown> {
  const response = await request.post(path, { headers: { Authorization: `Bearer ${token}` }, data })
  expect(response.status(), `POST の状態コード（${path}）`).toBe(200)
  return response.json()
}

/** 業務データの書き込みの要求（作成の POST・更新の PATCH）か */
function isRowWrite(request: Request, method: 'POST' | 'PATCH'): boolean {
  return request.method() === method && new URL(request.url()).pathname === BUSINESS_DATA_API.rows
}

/** 書き込みの要求の本文の values の鍵の名前（値は読まない・出さない） */
function sentColumnNames(request: Request): string[] {
  let body: unknown
  try {
    body = request.postDataJSON()
  } catch {
    return []
  }
  const values =
    typeof body === 'object' && body !== null ? (body as { values?: unknown }).values : undefined
  return typeof values === 'object' && values !== null ? Object.keys(values).sort() : []
}

/** 要素にフォーカスを移して Enter で押す（キーボードの操作） */
async function pressEnter(page: Page, testId: string): Promise<void> {
  await page.getByTestId(testId).focus()
  await page.keyboard.press('Enter')
}

/**
 * 一覧の見出しの「作成」を Enter で押す。表が空のときは、空の表の案内の中にも同じ data-testid の
 * 「作成」が出るため、案内の外の1つ（見出しの横のボタン）に絞る（Build and Test の繰り戻し 1）。
 */
async function pressCreate(page: Page): Promise<void> {
  const create = page.locator(
    '[data-testid="tables-create"]:not([data-testid="tables-empty-table"] *)',
  )
  await expect(create).toHaveCount(1)
  await create.focus()
  await page.keyboard.press('Enter')
}

test.describe('200 business data time flow', () => {
  test('saves an overlapping New York time with the later offset and keeps it through an update', async ({
    page,
    request,
  }) => {
    test.setTimeout(240_000)
    const schema = process.env.E2E_BIZ_SCHEMA ?? ''
    expect(schema.startsWith('e2e_biz_'), '実行のスキーマ（./gradlew e2eTest が渡す）').toBe(true)
    const runTag = newUserAdminRunTag()
    const tag = runTag.slice(-12).toUpperCase()
    const labels = businessDataTimeLabels(tag)
    const roleName = `E2ET_ROLE_${tag}`
    const email = `u7-perf-${runTag}@example.com`
    const eventId = `T-${tag}`
    const eventName = `${BUSINESS_VALUE_MARK} 出来事 ${tag}`
    const editedName = `${BUSINESS_VALUE_MARK} 直した出来事 ${tag}`
    recordSecretValues([
      { kind: 'runTag', value: runTag },
      { kind: 'email', value: email },
      { kind: 'displayName', value: REGISTERED_DISPLAY_NAME },
      { kind: 'businessValue', value: eventId },
      { kind: 'businessValue', value: eventName },
      { kind: 'businessValue', value: editedName },
    ])

    const adminToken = await requestAdminAccessToken(request)
    const auth = { Authorization: `Bearer ${adminToken}` }

    await test.step('1. apply the E2ET DSL of the run schema', async () => {
      const preview = await request.post('/api/admin/dsl/preview?source=PASTE', {
        headers: { ...auth, 'Content-Type': 'application/yaml' },
        data: businessDataTimeDsl(schema, labels),
      })
      expect(preview.status(), 'DSL の投入の状態コード').toBe(201)
      const { previewId } = (await preview.json()) as { previewId: string }
      const applied = await request.post('/api/admin/dsl/apply', {
        headers: auth,
        data: { previewId },
      })
      expect(applied.status(), 'DSL の適用の状態コード').toBe(200)
    })

    const roleId =
      await test.step('2. create the role with FULL and create on the event table', async () => {
        const created = await request.post('/api/admin/roles', {
          headers: auth,
          data: { name: roleName },
        })
        expect(created.status(), 'ロールの作成の状態コード').toBe(201)
        const { roleId: id } = (await created.json()) as { roleId: number }
        const saved = await request.put(`/api/admin/roles/${id}/permissions`, {
          headers: auth,
          data: {
            scope: { schemaName: schema, tableName: EVENT_TABLE },
            entries: [
              {
                schemaName: schema,
                tableName: EVENT_TABLE,
                columnName: null,
                main: 'FULL',
                create: true,
                delete: false,
              },
            ],
          },
        })
        expect(saved.status(), '権限の保存の状態コード').toBe(204)
        return id
      })

    const user = await test.step('3. prepare a registered user with the role', async () => {
      const unavailable = await registrationPrerequisites(request, adminToken)
      if (unavailable !== undefined) {
        test.info().annotations.push({ type: 'skip-reason', description: unavailable })
        return undefined
      }
      const created = await createRegisteredUser(request, runTag)
      recordSecretValues([{ kind: 'password', value: created.password }])
      const written = readSecretValues(secretValuesFilePath())
      expect(
        written.some((entry) => entry.kind === 'password' && entry.value === created.password),
        '値のファイルにパスワードがある',
      ).toBe(true)
      const list = await request.get(
        `/api/admin/users?page=1&q=${encodeURIComponent(created.email)}`,
        { headers: auth },
      )
      expect(list.status(), '利用者の一覧の状態コード').toBe(200)
      const userId = ((await list.json()) as { items: { userId: number }[] }).items[0]?.userId ?? 0
      expect(userId > 0, '作った利用者が一覧にある').toBe(true)
      const assigned = await request.post(`/api/admin/roles/${roleId}/assignments`, {
        headers: auth,
        data: { userId },
      })
      expect(assigned.status(), '割り当ての状態コード').toBe(204)
      return created
    })
    if (user === undefined) {
      test.skip(true, 'registration prerequisites are not available')
      return
    }

    // 作成・更新の要求を数える（存在しない時刻の誤りの間に送られないことを確かめる）
    const writes = { create: 0, update: 0 }
    page.on('request', (sent) => {
      if (isRowWrite(sent, 'POST')) {
        writes.create += 1
      } else if (isRowWrite(sent, 'PATCH')) {
        writes.update += 1
      }
    })

    await test.step('4. choose America/New_York as the display time zone in the preferences', async () => {
      await loginWithForm(page, user.email, user.password)
      await openUserMenuItem(page, PREFERENCES_ITEM)
      const zone = page.getByRole('combobox', { name: '表示のタイムゾーン' })
      await expect(zone).toBeVisible()
      await zone.selectOption(ZONE)
      await page.getByTestId('preferences-save-button').click()
      await expect(page.getByText('保存しました')).toBeVisible()
    })

    await test.step('5. open the empty list from the menu and see the display time zone notice', async () => {
      // ホームの入口のカードにも同じ名前のリンクが並ぶため、サイドバー（ナビゲーションの領域）に絞る。
      const menu = page.getByRole('navigation').getByRole('link', { name: labels.menu })
      await menu.focus()
      await page.keyboard.press('Enter')
      await expect(page.getByTestId('tables-heading')).toHaveText(labels.table)
      await expect(page.getByTestId('tables-time-zone-notice')).toContainText(
        `表示のタイムゾーン: ${ZONE}`,
      )
    })

    const occurredAt = page.getByRole('textbox', { name: EVENT_COLUMN_LABELS.occurredAt })

    await test.step('6. a skipped time shows the error and is not saved', async () => {
      await pressCreate(page)
      await expect(page.getByTestId('tables-form-heading')).toHaveText(`${labels.table}を作成`)
      await page.getByRole('textbox', { name: `${EVENT_COLUMN_LABELS.id} *必須` }).fill(eventId)
      await page.getByRole('textbox', { name: `${EVENT_COLUMN_LABELS.name} *必須` }).fill(eventName)
      await expect(occurredAt).toHaveAccessibleDescription(new RegExp(`^${ZONE} \\(`))
      await occurredAt.fill(SKIPPED_TIME)
      await occurredAt.press('Tab')
      await expect(occurredAt).toHaveAttribute('aria-invalid', 'true')
      await expect(occurredAt).toHaveAccessibleDescription(GAP_MESSAGE)
      await pressEnter(page, 'tables-form-save')
      await expect(page.getByTestId('tables-form-summary')).toBeFocused()
      await expect(page.getByTestId('tables-form-summary')).toContainText(
        `${EVENT_COLUMN_LABELS.occurredAt}: ${GAP_MESSAGE}`,
      )
      expect(writes.create, '存在しない時刻の間の作成の要求').toBe(0)
    })

    await test.step('7. an overlapping time is saved with the later offset (-05:00)', async () => {
      await occurredAt.fill(OVERLAPPING_TIME)
      await occurredAt.press('Tab')
      const later = page.getByRole('radio', { name: '後（-05:00）' })
      await expect(later).toBeVisible()
      await expect(page.getByRole('radio', { name: '先（-04:00）' })).toBeVisible()
      await later.check()
      await expect(later).toBeChecked()
      const createRequest = page.waitForRequest((sent) => isRowWrite(sent, 'POST'))
      await pressEnter(page, 'tables-form-save')
      expect(sentColumnNames(await createRequest), '作成の本文の欄の名前').toEqual(
        [EVENT_COLUMNS.id, EVENT_COLUMNS.name, EVENT_COLUMNS.occurredAt].sort(),
      )
      await expect(page.getByTestId('tables-detail-heading')).toHaveText(
        `${labels.table}: ${eventId}`,
      )
      await expect(page.getByText('作成しました。')).toBeVisible()
      await expect(page.getByTestId('tables-leave-dialog')).toHaveCount(0)
      await expect(page.getByTestId('tables-detail')).toContainText(SAVED_TIME_TEXT)
    })

    await test.step('8. the list shows the saved time with -05:00', async () => {
      await page.getByTestId('tables-back-to-list').click()
      await expect(page.getByTestId('tables-heading')).toHaveText(labels.table)
      await expect(page.getByTestId('tables-page')).toHaveAttribute('aria-busy', 'false')
      await expect(page.getByTestId('tables-row-link-r0')).toBeVisible()
      await expect(page.getByTestId('tables-page')).toContainText(SAVED_TIME_TEXT)
    })

    await test.step('9. the time table samples have the shape of the real responses', async () => {
      const login = await request.post('/api/auth/login', {
        data: { email: user.email, password: user.password },
      })
      expect(login.status(), '利用者のログインの状態コード').toBe(200)
      const { accessToken } = (await login.json()) as { accessToken: string }
      const definition = (await postJson(request, accessToken, BUSINESS_DATA_API.definition, {
        schema,
        table: EVENT_TABLE,
      })) as { definitionId: unknown }
      const rows = (await postJson(request, accessToken, BUSINESS_DATA_API.search, {
        schema,
        table: EVENT_TABLE,
        definitionId: definition.definitionId,
        conditions: [],
        page: 1,
        size: 20,
      })) as { items: { key: unknown }[] }
      const detail = await postJson(request, accessToken, BUSINESS_DATA_API.get, {
        schema,
        table: EVENT_TABLE,
        definitionId: definition.definitionId,
        key: rows.items[0]?.key,
      })
      const sample = timeDefinition(roleId)
      const broken: string[] = []
      const differences: Record<string, string[]> = {}
      const compare = (name: string, actual: unknown, expected: unknown) => {
        const found = shapeCompare(
          withoutColumnMaps(actual, broken),
          withoutColumnMaps(expected, broken),
          NULLABLE,
        )
        if (found.length > 0) {
          differences[name] = found
        }
      }
      compare('definition', definition, sample)
      compare('rowPage', rows, timePage(sample, 1, 20, 3))
      compare('rowDetail', detail, timeDetail(sample, 1))
      test
        .info()
        .annotations.push({ type: 'shape-differences', description: JSON.stringify(differences) })
      expect(broken, '行の値の組が文字か null でない所').toEqual([])
      expect(differences, '見本と本物の応答の項目の名前と型の違い').toEqual({})
    })

    await test.step('10. an update of the name only does not send or change the time', async () => {
      await page.getByTestId('tables-row-link-r0').click()
      await expect(page.getByTestId('tables-detail-heading')).toBeFocused()
      await pressEnter(page, 'tables-detail-update')
      const name = page.getByRole('textbox', { name: `${EVENT_COLUMN_LABELS.name} *必須` })
      await expect(name).toHaveValue(eventName)
      // 2回ある時刻のままの欄は、表示のタイムゾーンの壁時計の文字で出て、2択を出さない（D22）。
      // 初めの値は列の桁（6 桁）の端数を落とさない（D16）
      await expect(occurredAt).toHaveValue(`${OVERLAPPING_TIME}.000000`)
      await name.fill(editedName)
      await occurredAt.focus()
      await occurredAt.press('Tab')
      await expect(page.getByRole('radio')).toHaveCount(0)
      const updateRequest = page.waitForRequest((sent) => isRowWrite(sent, 'PATCH'))
      await pressEnter(page, 'tables-form-save')
      expect(sentColumnNames(await updateRequest), '更新の本文の欄の名前').toEqual([
        EVENT_COLUMNS.name,
      ])
      await expect(page.getByTestId('tables-detail-heading')).toBeFocused()
      await expect(page.getByText('更新しました。')).toBeVisible()
      await expect(page.getByTestId('tables-leave-dialog')).toHaveCount(0)
      await expect(page.getByTestId('tables-detail')).toContainText(editedName)
      await expect(page.getByTestId('tables-detail')).toContainText(SAVED_TIME_TEXT)
      await page.getByTestId('tables-back-to-list').click()
      await expect(page.getByTestId('tables-page')).toHaveAttribute('aria-busy', 'false')
      await expect(page.getByTestId('tables-page')).toContainText(SAVED_TIME_TEXT)
      expect(writes, '作成と更新の要求の数').toEqual({ create: 1, update: 1 })
    })
  })
})
