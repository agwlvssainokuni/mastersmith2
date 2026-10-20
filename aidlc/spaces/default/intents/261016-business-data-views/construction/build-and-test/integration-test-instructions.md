# 結合テストの手順 — Intent 261016-business-data-views

Test Strategy は Standard。この文書は、単位の境目（U1 の読み取り ⇄ U2 の書き込み ⇄ U3 の画面、U4 の axe の補助 ⇄ U3 の画面）と外の依存（3種類の対象DB・内部DB・既存の `role`・`dsl`・`audit`・`targetdb`）をまたぐテストの流し方を書く。単体テストの流し方は各単位の `code-generation/unit-test-instructions.md` にある。

## 1. 道具と設定

| 層 | 道具 | 置き場・名前の決まり |
|---|---|---|
| バックエンドの結合テスト | JUnit 5・Spring Boot のテスト・Testcontainers | `backend/src/test/java/cherry/mastersmith/<機能>/...`、名前は `XxxIT`（単体は `XxxTest`） |
| 対象DB | MySQL 26.7・MariaDB 13.0・PostgreSQL 18（版とダイジェストは `targetdb/testsupport/TargetDbImages.java`） | 3種類とも `./gradlew verify` の中で毎回（CI も同じ）。H2 やモックで代用しない（`project.md` の Mandated） |
| 内部DB | 組み込みの H2（本番と同じ。`-Dh2.compactThreads=1`） | コンテナを使わない |
| 構造の検査 | ArchUnit | `BusinessDataBoundaryArchitectureTest`・`TargetDbBoundaryArchitectureTest`・`RoleBoundaryArchitectureTest`・`ArchitectureTest`・`ApiAccessArchitectureTest` |
| 画面の結合（部品＋フック＋API の見本） | Vitest・Testing Library（jsdom）・user-event・vitest-axe | 対象と同じ場所の `*.test.ts(x)` |
| E2E | Playwright（`./gradlew e2eTest`） | `frontend/e2e/NNN-*.e2e.ts`。`verify` と CI の外 |

前提の環境（colima・`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`・Mailpit・見本の PG の 15432）は `build-instructions.md` の 2節。

## 2. 単位の境目と確かめるテスト

| 境目 | 確かめること | テスト |
|---|---|---|
| U1 ⇄ `role`（権限の写し `PermissionSnapshot`） | 判定の順（401 → 404 DSL → 404 テーブル → 403 → 409 → 400 → 404 行 → 503・409）、作業ロールを切り替えた直後・割り当てを変えた直後の次の要求で切り替わる。403 の利用者は要る権限だけを欠く人 | `BusinessDataAuthorizationApiIT`・`TableAccessResolverTest`・`BusinessDataReadServiceTest` |
| U1 ⇄ `targetdb`（読み取りの口） | 1要求で対象DB 1本、借りている間は内部DB 0 本。時間の上限（一覧・詳細 5 秒）。応答しない対象DB（ServerSocket）で 503 | `BusinessDataConnectionUsageIT`・`BusinessDataTimeoutIT`・`JdbcTargetReadAccessTest` |
| U1 の3種類の DB | 型・識別子の大文字と小文字・NULL の並び・境界の値（BIGINT の上限と下限・DECIMAL(38,10)・NULL）・時差つきの型・部分一致の `%`・`_`・`\`・`!` | `AbstractBusinessDataReadIT` を継ぐ `PostgresBusinessDataReadIT`・`MysqlBusinessDataReadIT`・`MariadbBusinessDataReadIT` |
| U2 ⇄ U1（SQL の部品・出し分けの関数） | `RowSqlExecutor` だけが SQL を組み立てる。読み取りの接続と書き込みの接続を分ける。⑤〜⑨ で拒んだ要求は書き込みの接続を借りない | `BusinessDataBoundaryArchitectureTest`・`BusinessDataWriteAuthorizationApiIT`・`BusinessDataConnectionUsageIT` |
| U2 ⇄ `targetdb`（書き込みの口） | 名前つきの書き込みのプールと対象DB 用のトランザクションの管理者、既定の候補にしない。書き込みの設定の欠け | `TargetWriteTransactionManagerInjectionIT`・`BusinessDataWriteNotConfiguredIT` |
| U2 の3種類の DB | 作成・更新・削除、件数、2行以上・0 行、制約の違反、丸めない、後勝ち、時差つきの型。書いた後の値は別の接続で読み直す | `AbstractBusinessDataWriteIT` を継ぐ `PostgresBusinessDataWriteIT`・`MysqlBusinessDataWriteIT`・`MariadbBusinessDataWriteIT` |
| U2 ⇄ `audit` | 確定の後に別の短いトランザクションで監査。3列（`target_schema`・`target_table`・`work_role_id`）と `detail` の主キー、16,384 字の切り詰め、時間切れの `UNKNOWN`、監査の書き込みの失敗の ERROR に値を載せない | `BusinessDataWriteAuditIT`・`AuditEventListenerTest`・`AuditSchemaIT`・`AuditMigrationCompatibilityIT`（V13） |
| U2 の時間切れ | 書き込みの文の後・確定の段・違反の後の段の時間切れ（3段 × 3種類）。どれも約 10 秒で 503（N22） | `BusinessDataWriteTimeoutIT` |
| U2 の見本の初期化 | `03-writer-account.sh` が冪等で、書き込みのアカウントが DDL を拒まれる。PG の PUBLIC の `TEMPORARY` を取り上げる | `TargetDbSampleInitScriptIT` |
| U3 ⇄ U1・U2（API の形） | 本物の応答と型を付けた見本の形が一致する（180 の中で毎回比べる）。画面は `unknown` で受けて形を確かめる | E2E `180-business-data-flow.e2e.ts`、`businessDataParse.test.ts`・`businessDataParse.property.test.ts` |
| U3 ⇄ 骨組み（4つの道・ログインの範囲の知らせ・作業ロールの切り替えの確かめ） | 主キーを道・履歴・保存・DOM の `id`・`data-testid` に置かない。ログアウト・別の利用者のログイン・作業ロールの変化で記憶を消す | `businessDataStore.test.ts`・`loginScope.test.ts`・`BusinessDataFormPage.test.tsx`・`useScreenPresence.test.tsx` |
| U3 ⇄ `src/shared/unsaved-guard` | `markClean()` の後の移動を止めない・旗が次のタスクで下りる・roleadmin の振る舞いが同じ | `useUnsavedGuard.test.tsx`・`roleadmin` の画面のテスト |
| U4 ⇄ U3（axe の補助と行の操作） | 区分 C1〜C9 の passes、`data-testid` の形（`tables-row-actions-r<n>`・`tables-row-link-r<n>`） | `axeElementKey.test.ts`・`axeRuleChecks.test.ts`、E2E 120・130・140・160・185 |
| U5（持ち越し） | `RoleConcurrencyIT` の #7（監査の書き込みの順の入れ替えを待ち合わせで確実に作る）、`AdminForbiddenProvider.test.tsx`、CI の不安定なテスト2件（`PasswordChangePage.test.tsx`・`H2CompactionByPoolSuspensionIT`） | 各テストを `--rerun` で5回 |

## 3. 流し方

| 目的 | コマンド |
|---|---|
| すべて（統合の前の関門） | `caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` |
| バックエンドの結合テストだけ | `./gradlew :backend:cleanIntegrationTest :backend:integrationTest` |
| 業務データの機能だけ | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.businessdata.*'` |
| 対象DB の1種類だけ | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.businessdata.web.Postgres*'`（`Mysql*`・`Mariadb*`） |
| 境界の構造の検査 | `./gradlew :backend:test --tests '*ArchitectureTest'` |
| 持ち越しの直しの繰り返し | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.role.web.RoleConcurrencyIT' --rerun`（5回）、`./gradlew :backend:integrationTest --tests 'cherry.mastersmith.config.H2CompactionByPoolSuspensionIT' --rerun`（5回） |
| 画面の業務データの機能 | `cd frontend && npx vitest run src/features/tables src/shared/unsaved-guard src/shared/scroll-region src/shared/page-size src/shared/null-value src/app/work-role src/app/login-state` |
| E2E の全件 | `caffeinate -dims ./gradlew e2eTest`（前提は `build-instructions.md` の 2.3） |
| E2E を選んで | `./gradlew e2eTest -Pe2e.files=e2e/180-business-data-flow.e2e.ts,e2e/185-business-data-accessibility.e2e.ts` |

- 飛ばし（SKIPPED）は、DB の種類に当たらない確かめの `assumeTrue` の 9 件だけ（U1 の読み取り 4・U2 の書き込み 5）。コンテナの実行環境の警告が出たら、統合しない。

## 4. テストのデータと後始末

- 対象DB のテストは、テストのクラスごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。名前に実行ごとに一意の値を入れ、前の実行の残りに頼らない（team.md）。
- 書き込みのテストは、アプリのトランザクションの巻き戻しに頼らず、書いた後の値をアプリとは別の接続で読み直す。
- 対象DB の業務データの値は架空のものだけ。メールアドレスは `example.com` などの予約のドメインだけ。
- E2E の見本の PG: 実行ごとにスキーマ `e2e_biz_<UTC>_<16進 8 文字>`・表 `e2e_item`・架空の行 25 件と、一時の読み取りと書き込みのロール（2 時間で失効）を作る。E2E の後に失敗しても `e2eTargetDbCleanup` が消し、接頭辞の残りの件数をログに出す（0 件であること）。資格情報は Playwright の `webServer.env` に置かずプロセスの環境変数で渡し、報告の部品（`playwright-secret-check-reporter.ts`）が json の報告と `frontend/test-results/` を検索する（見つかった件数 0 であること）。
- E2E は1つの WAR・内部DB・初期管理者を全ファイルで共有して番号の順に流す。180 は自分でロール・利用者を作り、初期管理者の状態を変えない。185・160・170 は業務データの API を型を付けた見本で差し替える。

## 5. 合格の線（Standard）

| 項目 | 線 | 最後の値（B7 の作業ブランチの上、レビューの直しの後） |
|---|---|---|
| バックエンドの単体・結合テスト | 失敗 0 | 単体 2,589・結合 1,386（SKIPPED 9）、失敗 0 |
| 画面のテスト | 失敗 0 | 226 ファイル・1,975 件、失敗 0 |
| E2E | 全件通過・flaky 0 | 256 件すべて通過、flaky 0（13分2秒） |
| E2E の業務データの代表の流れ | 1本まで（team.md） | 180 の1本 |
| カバレッジ | 全体とパッケージごとに行 80%・分岐 70%（フロントエンドは全体） | バックエンド 行 97.64%・分岐 91.23%、フロントエンド 行 95.06%・分岐 89.82%（`src/features/tables/` は 行 93.94%・分岐 88.39%） |

## Sources

- 各単位の `code-generation/unit-test-instructions.md`・`code-summary.md`・`generation-notes.md`
- 各単位の `nfr-requirements/`（U1 の NFR2.2・NFR2.3・NFR3.1・NFR3.3・NFR4.8、U2 の NFR2.6・NFR3.5・NFR3.6・NFR4.12〜NFR4.15、U3 の NFR7.9〜NFR7.13）
- `README.md`（E2E・E2E の見本の PostgreSQL・対象DB の結合テストとコンテナの実行環境）、`build.gradle.kts`
- `aidlc/spaces/default/memory/team.md` の Testing Posture

## Assumptions & Open Questions

- [assumption] 5節の最後の値は B7 の作業ブランチの上の回で、`develop` の今の形では流していない（Q1: B）。合わせた形の確かめは CI が受け持つ。
- AC3.4.5（一覧・詳細の読み取りとその拒否は監査に残らない）を確かめる結合テストが、どの単位の traceability にも無い（U2 が U1 へ Deferred にしたが、U1 に行が無い）。`cross-unit-traceability.md` の 4節。
