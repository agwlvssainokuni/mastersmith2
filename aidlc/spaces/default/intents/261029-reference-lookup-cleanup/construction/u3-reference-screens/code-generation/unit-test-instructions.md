# 単体テストの手順 — U3 u3-reference-screens

Test Strategy は Standard（部品ごとに 5〜8 件の要の振る舞い）。方法は test-after（`code-generation-plan.md` の Testing Contract が正）。どのコマンドもこの単位のテストのファイルに絞ってある。画面のテストは `frontend/` で、E2E と `verify` はプロジェクトのルートで流す。

## 1. 道具と設定

| 道具 | 使い方 |
|---|---|
| Vitest 5（`frontend/vitest.config.ts` の `unit` の project） | 画面の単体テスト（`src/**/*.test.{ts,tsx}` と `e2e/support/**/*.test.ts`）。jsdom・`vitest.setup.ts` は既存のまま |
| Testing Library（React）・user-event 14.6 | 部品の描画と操作。キーボード（Tab・矢印・Enter・Escape・Space）は user-event で行う |
| vitest-axe | 部品ごとに1件以上（状態ごとの件は `code-generation-plan.md` Step 8・10）。axe の規則を外さない |
| fast-check | `referenceField.ts`・`pickerSearch.ts`・`businessDataParse.ts`・`formDiff.ts` の性質ベースのテスト。失敗したときは報告に出る種（`seed`）と `path` を記録し、`fc.assert(…, { seed, path })` で再現する |
| Playwright（`./gradlew e2eTest`） | E2E 190・195 と、変えた 180。`verify` と CI の外 |

新しい依存・設定ファイルは足さない（`frontend/package.json`・`package-lock.json`・`vitest.config.ts`・`playwright.config.ts` を変えない。NFR9.41）。

## 2. 前提

- 画面の単体テスト: `frontend/node_modules` が lockfile どおりに入っていること（`cd frontend && npm ci`。`ignore-scripts=true` は既存の `.npmrc` のまま）。
- `NODE_OPTIONS=--no-experimental-webstorage` を付ける（`package.json` の `test` と同じ。付けないと Node の `localStorage` が jsdom のものと食い違う）。
- E2E: colima が動いていること、`cd frontend && npx playwright install chromium`（初回と Playwright の更新のとき）、Mailpit と見本の PostgreSQL を起動しておく（`./gradlew e2eTest` は届くかを確かめ、届かなければ起動の手順を示して失敗する。起動・停止はしない）。

```bash
docker compose --profile mail up -d mailpit
docker compose -f compose.yaml -f docker/e2e/compose.targetdb-port.yaml \
  --profile targetdb-postgres up -d targetdb-postgres     # 見本の PG を 127.0.0.1:15432 に開ける（.env.targetdb が要る）
# 流し終えたら: docker compose --profile targetdb-postgres up -d targetdb-postgres   # 上乗せ無しで作り直してポートを閉じる
```

- `verify` には次の環境変数を付ける（付けないと対象DB のテストが SKIPPED になる）。

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

- 長い実行（`verify`・E2E）は `caffeinate -dims` で包み、後で `pmset -g log` で Sleep・DarkWake が無かったことを確かめる。入っていたら結果を使わずに流し直す。
- U1（B1 549d38b・B2 b048ec1）と U2（B3 f35b7b8）が `develop` に統合済みであること（参照先の API と保存の拒否の形）。

## 3. この単位のテストを流すコマンド

どれも `frontend/` で流す。ファイルの道は正確に書いてあり、プロジェクト全体のテストは流さない。

### 3.1 最初の手順の前に流せることを確かめる（計画の Step 2）

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run --project unit \
  src/features/tables/businessDataParse.test.ts \
  src/features/tables/useTableDefinition.test.tsx \
  src/features/tables/BusinessDataFormPage.test.tsx
```

### 3.2 層ごとの単体テスト

```bash
cd frontend

# 受け渡し（型・写し・API の呼び出し・定義の purpose。計画の Step 4）
NODE_OPTIONS=--no-experimental-webstorage npx vitest run --project unit \
  src/features/tables/businessDataParse.test.ts \
  src/features/tables/businessDataParse.property.test.ts \
  src/features/tables/businessDataApi.test.ts \
  src/features/tables/useTableDefinition.test.tsx

# 純粋な関数（部品の選び方・検索の条件・ページの表示・誤りの鍵・本文と検証の口。計画の Step 6）
NODE_OPTIONS=--no-experimental-webstorage npx vitest run --project unit \
  src/features/tables/reference/referenceField.test.ts \
  src/features/tables/reference/pickerSearch.test.ts \
  src/features/tables/problemMessage.test.ts \
  src/features/tables/formDiff.test.ts \
  src/features/tables/formDiff.property.test.ts \
  src/features/tables/formValidation.test.ts

# 文言・フック・部品（計画の Step 8）
NODE_OPTIONS=--no-experimental-webstorage npx vitest run --project unit \
  src/features/tables/messages.test.ts \
  src/features/tables/reference/useReferenceLabels.test.tsx \
  src/features/tables/reference/ReferenceSelectField.test.tsx \
  src/features/tables/reference/ReadonlyReferenceField.test.tsx \
  src/features/tables/reference/LookupField.test.tsx \
  src/features/tables/reference/LookupPickerDialog.test.tsx \
  src/features/tables/reference/ReferenceColumnField.test.tsx

# 作成・更新の画面と、一覧・詳細を変えないこと（計画の Step 10。AC3.4.3・AC3.4.5）
NODE_OPTIONS=--no-experimental-webstorage npx vitest run --project unit \
  src/features/tables/BusinessDataFormPage.test.tsx \
  src/features/tables/BusinessDataListPage.test.tsx \
  src/features/tables/BusinessDataListPage.write.test.tsx \
  src/features/tables/BusinessDataDetailPage.test.tsx \
  src/features/tables/BusinessDataTable.test.tsx \
  src/features/tables/registration.test.ts

# E2E の補助の型の確かめ（計画の Step 15）
NODE_OPTIONS=--no-experimental-webstorage npx vitest run --project unit \
  e2e/support/referenceFixtures.test.ts \
  e2e/support/businessDataFixtures.test.ts
```

### 3.3 この単位のテストをまとめて流す（計画の Step 11）

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run --project unit \
  src/features/tables/reference/referenceField.test.ts \
  src/features/tables/reference/pickerSearch.test.ts \
  src/features/tables/reference/useReferenceLabels.test.tsx \
  src/features/tables/reference/ReferenceSelectField.test.tsx \
  src/features/tables/reference/ReadonlyReferenceField.test.tsx \
  src/features/tables/reference/LookupField.test.tsx \
  src/features/tables/reference/LookupPickerDialog.test.tsx \
  src/features/tables/reference/ReferenceColumnField.test.tsx \
  src/features/tables/businessDataParse.test.ts \
  src/features/tables/businessDataParse.property.test.ts \
  src/features/tables/businessDataApi.test.ts \
  src/features/tables/useTableDefinition.test.tsx \
  src/features/tables/problemMessage.test.ts \
  src/features/tables/formDiff.test.ts \
  src/features/tables/formDiff.property.test.ts \
  src/features/tables/formValidation.test.ts \
  src/features/tables/messages.test.ts \
  src/features/tables/BusinessDataFormPage.test.tsx \
  src/features/tables/BusinessDataListPage.test.tsx \
  src/features/tables/BusinessDataListPage.write.test.tsx \
  src/features/tables/BusinessDataDetailPage.test.tsx \
  src/features/tables/BusinessDataTable.test.tsx \
  src/features/tables/registration.test.ts \
  e2e/support/referenceFixtures.test.ts \
  e2e/support/businessDataFixtures.test.ts
```

### 3.4 カバレッジ（この単位のファイルの値を見る。別のコマンド）

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run --project unit --coverage \
  src/features/tables/reference/ \
  src/features/tables/businessDataParse.test.ts \
  src/features/tables/businessDataApi.test.ts \
  src/features/tables/useTableDefinition.test.tsx \
  src/features/tables/problemMessage.test.ts \
  src/features/tables/formDiff.test.ts \
  src/features/tables/formValidation.test.ts \
  src/features/tables/BusinessDataFormPage.test.tsx
```

- このコマンドは単独で流し、後ろに続く手順を置かない。`vitest.config.ts` の `thresholds`（行 80%・分岐 70%）は `src/**` の全体に当たるため、この単位のテストだけでは、ほかの単位が覆うソースで下限を割って終了コードが 0 でなくなることがある。選んだテストがすべて通り、`thresholds` だけで落ちたときは、テストの失敗ではないとして、この単位の結果に記録する。`thresholds` は変えない。
- 見る値は `frontend/coverage/coverage-summary.json` の `src/features/tables/reference/` と変えたファイルの行・分岐（記録だけ。線は置かない）。

### 3.5 E2E（プロジェクトのルートで流す）

```bash
# 状態 7 を先に流す（計画の Step 13。R-03）
caffeinate -dims ./gradlew e2eTest -Pe2e.files=e2e/195-reference-picker-accessibility.e2e.ts

# この単位の E2E と、共有の DSL に変えた 180（計画の Step 14・15）
caffeinate -dims ./gradlew e2eTest \
  -Pe2e.files=e2e/180-business-data-flow.e2e.ts,e2e/190-reference-picker-flow.e2e.ts,e2e/195-reference-picker-accessibility.e2e.ts
```

- 統合の前の E2E の全件（`./gradlew e2eTest`、ファイルを選ばない）は計画の Step 16 で流す。E2E の全件の流し方と時間の線（変更の前の 1.5 倍）は計画の Step 2・16 のとおりで、Build and Test でも流す。
- `npx playwright test` を直接流さない（対象DB の一時のロールの環境変数が無いため設定の読み込みで止まる）。
- 流した後に `frontend/test-results/e2e-results.json` を報告の部品（`playwright-secret-check-reporter.ts`）が検索する。資格情報・接続先・トークンと、値のファイルに書いた値（参照先のラベル・作った行の値など）が見つかれば失敗になる。

## 4. 期待するカバレッジ

- 下限は `team.md` の行 80%・分岐 70%（frontend は `vitest.config.ts` の全体の合計の `thresholds`）。プロジェクト全体の下限のため、この単位の実行では判定せず、**Build and Test が全件（`./gradlew verify` の `frontendCoverage`）で判定する**。`thresholds` と除外は変えない。
- この単位の値（`src/features/tables/reference/` と変えたファイル）は 3.4 で見て記録する。新しいファイルの行は 80% 以上を目安にし、下回るときは Step 8・10 で足りない場合を足す。`features/tables` の値は Build and Test で記録する（`team.md`）。

## 5. 差し替え（モック）の決まり

- API はテストの中で `fetch` を差し替える既存の形（`vi.stubGlobal('fetch', …)`、`testing/renderTablesDataRouter.tsx` の `responder`）で返す。道は `BUSINESS_DATA_PATHS` の値で分ける。
- 差し替えの応答は、U1・U2 の本物の形（`code-generation-plan.md` 1.1節。`options`・`picker`・`operator` が無い組、`page: null` のラベルの応答を含む）で書き、`testing/businessDataSamples.ts` の `wire*` の作り方と、足す `testing/referenceSamples.ts` から作る。誤りの場合は正しい応答から1つの項目だけを変えて作る。
- 遅らせた応答は手で解く Promise（`Promise.withResolvers` か同じ形の手作り）で作り、`sleep`・偽の時計・時間の上限の延長に頼らない。描画の後の効果で出る値は `waitFor` で待つ。
- `AbortSignal` の確かめは、差し替えた `fetch` が受けた `init.signal` の `aborted` を読む。
- make-you-chic-ui の部品は差し替えない（本物を描く。部品に頼る振る舞いもこのリポジトリのテストで確かめる。`team.md`）。

## 6. テストのデータ

- 値はすべて架空。日本語でよい。メールアドレスは `example.com` だけ。2語以上の現実に近いデータは品名にする（例「青い 木製の椅子」）。実在しそうな氏名を置かない。
- XSS の見本（`<script>`・`<img onerror>`・`"`・`'`・`javascript:`）と 100 文字以上の名前、200 件の選択肢は `src/features/tables/testing/referenceSamples.ts` の1か所に置き、`ReferenceSelectField`・`LookupPickerDialog`・`LookupField` のテストで共有する。
- テストの説明文（`describe`・`it`）は英語。
- E2E の前提の行は `frontend/e2e/support/businessDataSchema.sql`（顧客 3 行・商品 25 行・注文）にあり、`./gradlew e2eTest` が実行ごとのスキーマに入れ、終わったら `e2eTargetDbCleanup` が消す。190・195 は自分で作った利用者・ロールと、そのスキーマの行だけを使い、初期管理者と見本の行・見本の DSL を変えない。共有の DSL は各ファイルが流れの前に投入・適用し、最後に流れたファイルの DSL は適用中のまま残ってよい。

## 7. 記録すること

- 3.1 のコマンドが流せたこと（Step 2）、各層のコマンドの件数と結果、3.4 の値。
- fast-check の失敗の種（あれば）。
- E2E の件数と時間（全体・190・195）、状態 7 の axe の組の数と違反の件数（Step 13）。
- `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` の時間（Step 16）。
