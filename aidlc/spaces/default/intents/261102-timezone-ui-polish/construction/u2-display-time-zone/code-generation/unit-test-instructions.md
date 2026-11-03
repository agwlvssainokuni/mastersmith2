# 単位のテストの手順 — u2-display-time-zone

出典: コード生成の計画（`code-generation-plan.md`）、NFR Design（`nfr-design/security-design.md` の SD9・`performance-design.md` の PD3・PD4・PD6・PD7・`reliability-design.md` の RD1〜RD6・`logical-components.md`）、NFR Requirements（`tech-stack-decisions.md` の NFR5.1.1〜NFR5.3.1）、Infrastructure Design（`infrastructure-design/cicd-pipeline.md` の 2 節・4 節）、`team.md` Testing Posture、`docs/target-db.md`（colima の環境変数）。書き方は同じ Intent の前の単位（`construction/u3-target-db-time/code-generation/unit-test-instructions.md`・`construction/u6-app-icon-assets/code-generation/unit-test-instructions.md`）にそろえる。

U2 は、利用者ごとの表示のタイムゾーンの保存・API・配布・選択・管理の画面の時刻の単位（種類 service。バックエンドの `user` と画面の両方に入る）。方法は test-after（層ごとに実装 → その層のテスト → 通ってから次へ。例外は保存の 1 文の最初の確かめと、文の数の基準を変更の前に固定すること）。テストの量は Standard（部品ごとに 5〜8 件）。内部DB の結合テストは組み込みの H2 で行い、対象DB のコンテナは使わない（`team.md`）。

## テストの枠組みと設定

- 枠組み（バックエンド）: JUnit 5・AssertJ・Mockito（単体）、Spring の `@SpringBootTest`（結合。`RANDOM_PORT`、組み込みの H2 の内部DB）、jqwik（性質ベース。失敗時の乱数の種を出力に残す）、ArchUnit。新しい依存は足さない。
- 枠組み（画面）: Vitest（`unit` プロジェクト）・Testing Library（jsdom）・user-event・vitest-axe・`fast-check`（性質ベース。失敗時の乱数の種を出力に残す）。ESLint の規則の確かめは `eslint-config` プロジェクト（ESLint の `lintText`。node 環境）。新しい依存は足さない。
- 枠組み（E2E）: Playwright（`./gradlew e2eTest`）。新しい流れ・新しい `test()` は足さず、既存の 080・100・110 を書き換える。
- 置き場: バックエンドは `backend/src/test/java` に対象と同じパッケージ構成で置く（単体は `XxxTest`、Spring を起動する結合は `XxxIT`。Gradle の `test` は `*Test`、`integrationTest` は `*IT` を流す）。テストの手伝いは `user/testsupport`。画面のテストは対象と同じ場所の `*.test.ts`・`*.test.tsx`。提供元を差し込む共用の手伝いは `frontend/src/app/display-settings/testing/`。ESLint の規則の確かめは `frontend/src/eslintTimeZoneRule.test.ts`。テストの説明文（`@DisplayName`・`describe`・`it`）は英語、テストデータは日本語でよい。
- 時刻・タイムゾーン: 名前の門はタイムゾーンも時計も使わない。画面のテストは、提供元の `browserTimeZone`・API・現在時刻を差し込んで固定し、機械の既定（手元は `Asia/Tokyo`、CI の機械は UTC）に結果を預けない。別の時差を確かめるテストはその時差を明示に指定する。機械の既定を変えても同じ結果になることは `process.env.TZ` を `Asia/Tokyo`・`America/Los_Angeles`・`UTC` に変えて確かめ、`finally` で戻す。E2E は `timezoneId` で固定する。
- 描画の後の効果で反映される値（配布の `ready`・選択の保存値・差し替え）は、操作の直後に同期で確かめず `waitFor` で待つ。取得の解決（`Promise`）を注入して順を入れ替え、待ち合わせを時間に預けない。Vitest の既定の上限（5 秒）は原因を確かめずに延ばさない。
- 外へ出さない確かめ: 検証の前の名前の値・氏名・メールアドレスが、ログ・トレースの属性・外へ送る設定を有効にしたときの出力・エラー応答に出ないことを、TRACE を有効にした `PreferencesSecretLeakIT`（既存の `MeSecretLeakIT` と同じ形）と結合テストで確かめる。
- テストデータ: 名前は公開の地域の名前（`Asia/Tokyo`・`America/New_York`・`Asia/Kolkata`・`Asia/Kathmandu`・`Australia/Adelaide`・`UTC` など）と、集合に無い名前・`+09:00`・`Z`・空・65 文字の文字列。氏名は架空、メールアドレスは `example.com` だけ。k6 の結果や `evidence/` に認証の値を置かない。

## この単位だけを流すコマンド（リポジトリのルートで実行）

バックエンドの結合・`verify` では、colima のコンテナの実行環境を使うため、シェルに次の 2 つを渡す（渡さないと対象DB のテストが SKIPPED になり、飛ばした状態では統合しない。この単位自身は H2 だけで足りる）。

```
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

バックエンドの単体テスト（コンテナ不要。この単位のクラスだけ）:

```
./gradlew :backend:test \
  --tests 'cherry.mastersmith.user.domain.*Test' \
  --tests 'cherry.mastersmith.user.service.*Test' \
  --tests 'cherry.mastersmith.user.web.*Test' \
  --tests 'cherry.mastersmith.user.UserBoundaryArchitectureTest' \
  --tests 'cherry.mastersmith.ApiAccessRulesTest'
```

バックエンドの結合テスト（組み込みの H2。この単位が足す・直すクラスと、通ることを確かめるクラス）:

```
./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.user.repository.UserSchemaIT' \
  --tests 'cherry.mastersmith.user.repository.UserRepositoryIT' \
  --tests 'cherry.mastersmith.user.repository.DisplayTimeZoneMigrationCompatibilityIT' \
  --tests 'cherry.mastersmith.audit.domain.AuditMigrationCompatibilityIT' \
  --tests 'cherry.mastersmith.user.service.PreferencesPartialUpdateIT' \
  --tests 'cherry.mastersmith.user.service.DisplayTimeZoneKeptByOtherUpdatesIT' \
  --tests 'cherry.mastersmith.user.web.MePreferencesApiIT' \
  --tests 'cherry.mastersmith.user.web.MePreferencesQueryCountIT' \
  --tests 'cherry.mastersmith.user.web.MePreferencesLockTimeoutLeakIT' \
  --tests 'cherry.mastersmith.user.web.PreferencesSecretLeakIT' \
  --tests 'cherry.mastersmith.user.web.MeSecretLeakIT'
```

版の個数に依らないこと・ほかの機能が変わらないことを確かめる結合テスト（変更なしで通ること）:

```
./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.role.RoleMigrationCompatibilityIT' \
  --tests 'cherry.mastersmith.role.RoleSchemaIT' \
  --tests 'cherry.mastersmith.common.db.DatabasePersistenceIT' \
  --tests 'cherry.mastersmith.audit.domain.AuditSchemaIT' \
  --tests 'cherry.mastersmith.dslmanage.repository.DslManageRepositoryIT' \
  --tests 'cherry.mastersmith.invitation.repository.InvitationSchemaIT' \
  --tests 'cherry.mastersmith.useradmin.web.UserAdminMassAssignmentIT' \
  --tests 'cherry.mastersmith.role.web.RoleMassAssignmentApiIT' \
  --tests 'cherry.mastersmith.auth.web.AuthSuspensionSecretLeakIT' \
  --tests 'cherry.mastersmith.auth.web.LoginApiIT' \
  --tests 'cherry.mastersmith.auth.web.LoginResponsePreferencesIT' \
  --tests 'cherry.mastersmith.common.error.web.FilterExceptionErrorLogIT'
```

1 つのクラスだけを流すときは、`--tests 'cherry.mastersmith.user.domain.TimeZoneNameTest'` のように完全なクラス名を書く。

画面の Vitest（この単位のファイルだけ。`unit` プロジェクト）:

```
cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx --no-install vitest run --project unit \
  src/app/display-settings \
  src/features/preferences \
  src/shared/format \
  src/features/useradmin \
  src/features/invitation \
  src/features/dsl \
  src/app/App.test.tsx
```

構造の検査の確かめ（ESLint の規則。`eslint-config` プロジェクトで 1 つだけ）と、規則を実物に当てる確かめ:

```
cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx --no-install vitest run --project eslint-config
cd frontend && npm run lint && npm run typecheck
```

画面の全件・書式・ライセンスヘッダー（Step 15）:

```
cd frontend && npm run format:check && npm run lint:css && npm run license:check && npm run test:coverage
```

E2E（`frontend/src` の本体に触れるため、統合の前に流す。事前に Mailpit と見本の対象DB のコンテナが動いていること。`docs/verification.md` の「ビルドした WAR での画面の確認（E2E）」）:

```
./gradlew e2eTest -Pe2e.files=e2e/080-preferences-accessibility.e2e.ts
./gradlew e2eTest -Pe2e.files=e2e/100-app-text-contrast.e2e.ts
./gradlew e2eTest -Pe2e.files=e2e/110-user-admin-flow.e2e.ts
./gradlew e2eTest
```

1〜3 行目は Step 16（書き換えた 3 本を個別に）、4 行目は Step 22（既存の全件）。

負荷の場面の台本と閾値の確かめ（測りは流さない。Build and Test が持つ）:

```
docker run --rm -e SCENARIO=preferencesSaveTimeZone -v "$PWD/perf/k6:/scripts:ro" "$K6_IMAGE" inspect --include-system-env-vars /scripts/scenarios.js
bash perf/k6/thresholds-test.sh
```

`K6_IMAGE` は `perf/versions.env` の値（`source perf/versions.env`）。`--include-system-env-vars` を付けないと場面の名前が `undefined` になり確かめにならない。

長い実行（結合テストの全部・`verify`・E2E の全件）は `caffeinate -dims` で包み、流す前に `pmset -g assertions` で眠らない状態か、流した後に `pmset -g log` で Sleep・DarkWake が無かったかを確かめ、入っていたら結果を使わずに流し直す。最初のテストを書く前（計画の Step 1）に、上の単体・結合・画面のコマンドが変更の前の状態で実行できることを確かめる。

カバレッジの値（バックエンドのこの単位のパッケージ）は、別のコマンドで読む（床の検証 `jacocoTestCoverageVerification` を含めない）:

```
./gradlew :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport
```

読むのは `backend/build/reports/jacoco/test/jacocoTestReport.xml` の `user.domain`・`user.repository`・`user.service`・`user.web`。画面は `npm run test:coverage` の表（全体の合計と `src/app/display-settings/`・`src/features/preferences/` の行）。

## 期待する件数（Standard）

バックエンドの単体テスト（約 40 件）:

| ファイル | 件数の目安 | 内容 |
|---|---|---|
| `TimeZoneNameTest`（新規） | 6〜8 | 集合の要素が通る、空・65 文字・前後の空白・制御文字・NUL・改行・大文字小文字違い・`+09:00`・`Z`・`UTC+9` が通らない、`UTC` が通る、集合が作り置きの 1 つで変更できない |
| `TimeZoneNamePropertyTest`（新規、jqwik） | 6 | SD1 の P1〜P6（集合の任意の要素は通る／1 文字足す・削る・変える・大文字小文字を変えると通らない／`[+-]HH`・`[+-]HH:MM`・`[+-]HHMM` は通らない／65 文字以上は通らず集合を引かない／前後に空白・制御文字を付けると通らない／同じ入力は同じ結果）。失敗時の乱数の種を記録 |
| `DisplayTimeZoneInputTest`（新規） | 4〜5 | `Keep`・`Clear`・`Text`・`NotText` の `toString` が値を伏せる、検証の前の値を SQL に届けない型 |
| 3 状態の変換の単体テスト（`user.web`、新規） | 7 | 項目が無い・`null`・文字列・数・真偽・オブジェクト・配列・空の文字列 |
| `PreferencesRequestTest`・`PreferencesResponseTest`（新規） | 4 | `toString` が氏名と検証の前の `displayTimeZone` を伏せる、5 つ目は常に出る（未設定は `null`）、名前を再検証しない |
| `PreferencesValidationTest`（既存を変えず足す） | 3〜4 | 5 つ目の誤りと並び（氏名・言語・テーマ・文字の大きさ・`displayTimeZone`）、4 項目の判定が変わらない |
| `UserPreferencesServiceTest`（直す） | 5〜6 増 | `Keep`・`Clear`・`Set`、無効な名前では文を出さない、`NotText`、本人がいない、誤りの並び |

バックエンドの結合テスト（組み込みの H2。約 45 件）:

| ファイル | 件数の目安 | 内容 |
|---|---|---|
| `UserSchemaIT`（足す） | 4 | 列の型・NULL 可・既定なし、既存の行が `NULL`、移行のファイルが列の追加 1 文だけ、V14 が成功で 1 件 |
| `DisplayTimeZoneMigrationCompatibilityIT`（新規） | 4 | 前の版（V1〜V13）の Flyway が止まらない（実行 0 件・最大の版 14）、前の版の行が読めて列が `NULL`、前の版の列だけで行を足せる、4 列を更新しても足した列が変わらない |
| `AuditMigrationCompatibilityIT`（直す） | 0（期待を 13 → 14） | 緩めず、新しい最後の版に合わせる |
| `UserRepositoryIT`（直す・足す） | 8〜10 | 保存の 1 文: `Keep`・`Clear`・`Set`、行が無い、5 項目が 1 文で確定、ほかの列を変えない、特殊な文字の氏名、`null` の束縛、保存の後の読み取り、更新不可の列の読み取り |
| `MePreferencesApiIT`（直す・足す） | 14〜16 | 5 つ目の常時の出力、3 状態、前の版の PUT が名前を消さない、無効な 4 種類、全体を保存しない、誤りの並びと値を載せない、重なる項目は後の値、10 万文字の実測、本人だけ・改ざん、401、削除された行、停止中、ログインの応答不変、分類不変、400 のログ、監査が増えない |
| `DisplayTimeZoneKeptByOtherUpdatesIT`（新規） | 4〜5 | パスワードの変更・停止と再開・ロックの解除・管理者の印・管理者による氏名と言語の変更の後も列が残る |
| `PreferencesPartialUpdateIT`（直す・足す） | 2 増 | 同じ利用者の 2 つの保存の重なり（待ち合わせ）で 5 項目が片方の要求の値でそろう |
| `MePreferencesQueryCountIT`（新規） | 3 | 文の数が変更の前の基準を超えない（読み取り・保存の各場合）、接続が 1 本 |
| `PreferencesSecretLeakIT`（新規）＋外へ送る設定の結合 | 3〜4 | TRACE と外へ送る設定でも検証の前の名前・氏名が出ない |
| `MePreferencesLockTimeoutLeakIT` | 0（説明の名前だけ直す） | 新しい保存の文でも変更なしの期待で通る |

画面のテスト（Vitest。新規と書き換えを合わせて約 150 件）:

| ファイル | 件数の目安 | 内容 |
|---|---|---|
| `decideDisplayTimeZone.test.ts`＋性質 | 6 + 3 | `saved`・`browser`・`saved-unresolvable`・`UTC`、`ready` の `timeZone` は常に解釈できる（fast-check） |
| `displayTimeZoneApi.test.ts` | 7 | 形の確かめ（`string`・`null`・長さ 64・65・数・欠落・配列）、失敗の種類 |
| `DisplayTimeZoneProvider.test.tsx` | 14 | 状態の遷移、世代、未ログインで捨てる、別の利用者に残らない、`applySaved`・`reload`、取得の回数、`StrictMode`、401、保存先を使わない |
| `browserTimeZone.test.ts` | 3 | 差し替え、解釈できないとき `UTC` |
| `buildTimeZoneOptions.test.ts`＋性質＋測り | 8 + 4 + 2 | 先頭・昇順・重複なし・保存済みの名前・`UTC`・夏時間・端数の時差・固定のオフセット。100 ミリ秒（5 回の中央値） |
| `TimeZoneSelect.test.tsx` | 8 | キーボード・時差のラベル・拒否の誤り・文字のノード・`Alert`・長い名前・vitest-axe 1 件 |
| `preferencesApi`・`PreferencesPage`・`formChecks`・`errorMessages`・`PreferencesForm`（直す） | 約 20 増 | 5 項目 |
| `formatInstantToMinutes.test.ts` | 6 + 2 | 分までの書式、読めない時点、fast-check |
| `DisplayTime.test.tsx`・`DisplayTimeZoneErrorBar.test.tsx` | 8 | 3 状態、整形の回数（`loading`・`error` で 0 回）、vitest-axe 各 1 件 |
| `lockedUntilInZone.test.ts` | 8 | 今日・今日でない・日付の変わり目・読めない値 |
| 管理の画面のテスト 8 ファイル（書き換え） | 約 30 の期待の書き換え | 提供元の差し込み、新しい書式、機械の既定を変えても同じ、保存の成功で時刻が切り替わる、`saved-unresolvable` |
| `eslintTimeZoneRule.test.ts` | 約 20 | 止める 8・通す 5・ファイル名の組・`export default` と `enum`・`@ts-expect-error` |

E2E（Playwright）: 新しい流れは足さない。書き換えるのは 080・100・110 と `support/preferencesFixtures.ts`。本数に数えない。

## 期待するカバレッジ

- 全体の合計の下限（`team.md`）: バックエンドの行 80%・分岐 70%（`backend/build.gradle.kts` の `jacocoTestCoverageVerification`）とパッケージごとの同じ下限、画面の行 80%・分岐 70%（`vitest.config.ts` の `thresholds`）。**この下限は、プロジェクト全体のテストを流す Build and Test（と統合の前の `./gradlew verify`・`npm run test:coverage`）が判定する**。この単位のコマンド（`--tests …`・`vitest run <ファイル>`）は床の検証を含まない（Vitest の単位の実行は床の閾値で終了コードが非 0 になりうるが、選んだテストがすべて通っていればテストの失敗ではない）。床の検証を含むコマンド（`verify`・`jacocoTestCoverageVerification`・`npm run test:coverage`）は、この単位のコマンドに混ぜず、単独で流す。下限の設定は変えない。
- 手を入れるバックエンドのパッケージ（`user.domain`・`user.repository`・`user.service`・`user.web`）はいずれも `packagesJudgedByTotal` の外で、パッケージごとの下限の対象。この単位のコードの目安は行・分岐とも 90% 以上（名前の門・3 状態・保存の 1 文）。
- `packagesJudgedByTotal` の 7 パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）の `src/main` には手を入れない。入れることになったら、テストを足して下限を満たし一覧から外す手順を計画に足す。
- 画面の新しい機能のフォルダ（`src/app/display-settings/`・`src/features/preferences/`）の値は Build and Test で記録する。計測から外すものを増やさない。

## 性能・資源の測り

- 選択肢の組み立て（NFR2.1.6・PD4）: 一覧（その環境の `Intl` の名前。個数を記録）と現在時刻を注入して固定し、各回の前に `clearFormatterCache` で整形器のキャッシュを空にして新しい配列で 5 回組み立て、**5 回の中央値が 100 ミリ秒以内**で合格にする。失敗時は 5 回の値・名前の個数・Node の版・CPU を出す。冷たい最初の 1 回の値を `code-summary.md` に残す。100 ミリ秒は延ばさない。描画（`<option>` 約 400 個）の時間は jsdom では実際のブラウザと違うため目標にせず、値の記録だけにする。
- 管理の画面の整形（NFR2.1.7・PD7）: `loading`・`error` で整形 0 回、`ready` で「表示する時刻の数 + 1」以下。1 つの値の変換が平均 0.05 ミリ秒以内、1 ページ最大 1 万個で 500 ミリ秒以内の範囲（時刻変換の部品の目標）に収まること。
- 内部DB の文の数（NFR2.1.2・PD2）: 変更の前に測った数（Step 2）を超えないこと。接続は 1 要求で 1 本。
- 負荷の場面（NFR2.1.3・NFR2.1.4・PD6）: 台本・閾値・文書は `verify`（閾値の試し）と `k6 inspect` で確かめる。p95 の測りは **Build and Test が持つ**（Deferred）。測っていない目標を満たした扱いにしない。

## モック・スタブの方針

- 内部DB の結合テストは組み込みの H2 の実物を使う（モックで代用しない）。保存の 1 文の確かめは、Spring Data の実物とトランザクションの中で行う。
- 単体テストでは `UserRepository` を Mockito で置き換えてよい（`UserPreferencesServiceTest`）。時計は注入し、`sleep` と実時刻に頼らない。同時の重なりは待ち合わせ（`RowLockHolder` の形）で作り、スレッドの数に頼らない。
- 画面のテストでは、`displayTimeZoneApi`・`browserTimeZone`・現在時刻・名前の一覧の取り出しを差し込む。`apiRequest` は共通の通信の部品を通し、401 での更新の流れが変わらないことは既存の確かめに任せる。
- E2E の `GET /api/me/preferences` の差し替えは見本（`preferencesSample`）だけを使う。見本の形は本物の応答と 080 の測りで毎回比べる（`hasPreferencesShape`。値は比べない）。

## この単位に当たらないテスト

- E2E の新しい流れ・業務データの日時の画面の確かめは、業務データの単位が受け持つ（日時の代表の流れ 1 本）。この単位は既存の 3 本の書き換えと axe の状態の追加だけ（本数に数えない）。
- 実機（Chromium）でのログイン後の取得の回数・既存の 3 場面と新しい場面の p95・E2E の全件の通過と axe の違反 0 件の件数・カバレッジの値・`verify` と CI の時間は、Build and Test が記録する。
- 配備・戻し・スモークテスト S1〜S8・戻しの練習は Deployment Pipeline／Execution が手順書にして実行する。
