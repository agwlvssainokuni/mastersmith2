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

## 8. 承認の場の Request Changes の直し（直しの Bolt B7）

計画の「承認の場の Request Changes の直し（直しの Bolt B7）」の Step 19〜37 の確かめ。上の 1〜7 節の決まり（道具・差し替え・テストのデータ・説明文は英語）はそのまま当てる。層ごとに実装の後でその層のテストを流し、通ってから次へ進む（test-after）。

### 8.1 前提

- ブランチ `fix/261029-reference-lookup-cleanup-review`。始める前に `frontend/playwright-report`・`frontend/test-results` を消す。
- backend の結合テストと `verify` は、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡して流す（渡さないと対象DB のテストが SKIPPED になる）。長い実行は `caffeinate -dims` で包み、前に `pmset -g assertions`、後に `pmset -g log | grep -E ' (Sleep|DarkWake|Wake) ' | tail -n 20` で眠りが無いことを確かめる。

### 8.2 U2 R-01（⑩ の想定外の診断の WARN。計画の Step 20・21）

```bash
./gradlew :backend:compileJava :backend:spotlessCheck
./gradlew :backend:test --tests '*BusinessDataWriteReferenceTest' --tests '*BusinessDataWriteServiceTest' --tests '*ReferenceLookupTest'
caffeinate -dims ./gradlew :backend:integrationTest --tests '*ReferenceWriteSecretLeakIT'
```

期待: 足した3件（`INVALID_VALUE`・`UNEXPECTED`・値の欠け）で WARN がちょうど1行、`purpose=REFERENCE`・`reason=UNEXPECTED`、`sqlState`・`vendorCode`・`exceptionClass` が入力と一致（値の欠けでは無いキーを出さない）、本文とキーの値に参照の値・カラム名・テーブル名が無い、例外は `BusinessDataUnexpectedException`、書き込みの口と監査が呼ばれない。既存の T-F2（WARN 1行）と BR2.7（拒否で WARN 0行）が通る。

### 8.3 U3 R-01（必須の判定の一本化。計画の Step 22〜25）

```bash
cd frontend
npm run typecheck
# 検証の純粋な関数（Step 23）
npx vitest run src/features/tables/formValidation.test.ts src/features/tables/references/referenceField.test.ts
# 部品と画面（Step 25）
npx vitest run src/features/tables/references src/features/tables/BusinessDataFormPage.test.tsx src/features/tables/formValidation.test.ts
```

期待: NOT NULL で `required: false` の REFERENCE・LOOKUP の項目が、作成で値が無い・空の値で `REQUIRED`、必須の印と `aria-required="true"` が付き、値が入った select に「（選ばない）」が無い。`nullable: true`・`required: false` の参照の項目と、参照でない NOT NULL・`required: false` の項目は今の振る舞いのまま。fast-check の性質（参照の項目で `validateValue(column, '') === 'REQUIRED'` と `isRequiredColumn(column)` が一致）が通り、失敗したら種を記録する。欄から離れたときの誤りは `waitFor` で待って確かめる。

### 8.4 U3 R-02（LookupField の欄への結び付け。計画の Step 25）

`LookupField.test.tsx` で次を確かめる（vendor の `FormField` は `FormFieldContext` で配り、`TextInput` が `useFieldProps` で受ける。`vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/FormField/`）:

- `getByLabelText(<ラベル>)` が `tables-lookup-value-c<n>` の欄を返す。
- 誤りがあるとき、欄の `aria-invalid="true"`、`aria-describedby` が誤りの文の要素の `id` を指す。
- 誤りが無く値が空のとき、`aria-describedby` が説明文の要素の `id` を指し、`aria-invalid` が無い。値が入ると `aria-describedby` が無い。
- 「選ぶ…」「クリア」のボタンに `aria-describedby`・`aria-invalid` が無い。
- vitest-axe（誤りの状態・必須の形）で違反 0 件。

どれかが届かないと分かったときだけ部品を直し（計画の Step 24 の条件）、直した後に同じテストを流し直す。

### 8.5 frontend の手元の確かめと E2E への影響（計画の Step 26・27）

```bash
cd frontend && npm run format:check && npm run lint && npm run lint:css && npm run typecheck
npm run license:check   # ライセンスヘッダー（scripts/check-license-header.mjs）
npx vitest run src/features/tables
git diff --stat develop -- package.json package-lock.json   # 空
# E2E の欄の探し方と「（選ばない）」に頼る箇所の洗い出し（直すかを決める材料）
grep -n "getByLabel\|getByRole('combobox'\|unselected\|選ばない" e2e/180-*.e2e.ts e2e/190-*.e2e.ts e2e/195-*.e2e.ts
```

### 8.6 U2 R-03（参照元が 0 行の確かめ。計画の Step 28）

```bash
bash -n perf/biz/make-biz-seed.sh
command -v shellcheck > /dev/null && shellcheck perf/biz/make-biz-seed.sh
for k in postgres mysql mariadb; do
  printf '%s\t' "$k"; ./perf/biz/make-biz-seed.sh "$k" 10 | grep -c '<足した確かめの文の目印>'   # どれも 1
done
```

見本の PostgreSQL（`targetdb-postgres`）が動いているときだけ、計画の Step 28 のとおり、出力を流して終了コード 0、`biz_refcreate` に1行入れた後に確かめの文だけを流して 0 でない終了、`bizperf` を消して片付ける（消すのは台本が作る `bizperf` だけ。誤りの文に行の値と数が出ないことも見る）。MySQL・MariaDB の流し込みは Build and Test の試し走りで確かめる。

### 8.7 U4 R-04・U5 R-01・R-02・R-05（README。計画の Step 29〜33）

- Step 31 の取り出し（本文 = 「今の基準の一覧」の後から `## 履歴` の前まで）:

```bash
h=$(grep -n '^## 履歴' perf/README.md | head -1 | cut -d: -f1)
awk -v h="$h" 'NR < h { print NR "\t" $0 }' perf/README.md \
  | grep -E 'memory\.peak|memory\.max|anon|[0-9.]+ ?%|[0-9,]+ ?ms|p\(95\)|< ?0\.[0-9]|[0-9][^ ]* ?(未満|以下|以内)' \
  > "$B/threshold-lines.tsv"   # $B はリポジトリの外の作業の置き場（U5 の手順書と同じ）
wc -l "$B/threshold-lines.tsv"
```

  1行ずつ (a) 今の線（一覧の行と対応）・(b) 前の線（履歴へ）・(c) 線ではない（理由）に分けた表を `u5-container-perf-docs/code-generation/perf-docs-check-record.md` の新しい節に書く。
- 直した後の確かめ: U5 の `unit-test-instructions.md` 2.3〜2.6 の CB5・CB6・CB9〜CB12・Q1〜Q7 を流し直す（CB9〜CB12 の「前」は B7 の直しの前の README）。新しい CB22:

```bash
# 取り出しを流し直し、(a) の行がすべて一覧と対応し、(b) の行が本文に無く、分けていない行が 0 件
# 連続する参照と括弧の二重
grep -c '（履歴 H[0-9]*）（履歴' perf/README.md   # 0
grep -n '。）' perf/README.md                     # コメントの括弧の二重が無い（残る行は文の終わりとして正しいかを1行ずつ見る）
grep -n 'コード 2\|終了コード 2' perf/README.md   # U4 R-04 の案内の行がある
gitleaks dir --redact --no-banner --config .gitleaks.toml --exit-code 1 perf/README.md; echo "Q7 rc=$?"   # 0
```

- 1214 行の直しの前に `docker/perf/compose.yaml` の上限の既定を読み、計算の各項（perf のアプリ・MySQL・見本の PG・Mailpit）が今の値と一致することを確かめる。

### 8.8 B7 の全体の確かめ（計画の Step 35）

```bash
cd frontend && npx prettier --write . && cd ..
time caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
caffeinate -dims ./gradlew e2eTest   # Mailpit と見本の PostgreSQL を起動してから
```

期待: SKIPPED の対象DB のテストが無い、`businessdata.service` のパッケージごとの下限（行 80%・分岐 70%）と frontend の `thresholds` が通る、E2E の全件（180・190・195 を含む）が通る、E2E の報告の json に資格情報・接続先・トークンが無い。

### 8.9 記録すること

- 各コマンドの件数と結果、`verify` の時間（U3 の B4 の後の値と並べる）、E2E の件数と時間、`businessdata.service` のカバレッジ（`jacocoTestReport.xml`）。
- fast-check の失敗の種（あれば）。
- Step 26 で直した E2E の箇所（無ければ無いこと）、R-02 で部品を直したか（テストだけで閉じたか）。
- Step 30 の依頼者の答えと、それに合わせた README の直し。Step 31 の分け方の表と CB22 の結果（`perf-docs-check-record.md`）。
- push の後の CI の試行ごとの結果と検査の job の時間。
