# 単体テストの手順（261015-deps-ui-carryover）

- 範囲: bugfix、Test Strategy Minimal（要件ごとに1つ、部品ごとに正常系の下限）に、bugfix の床の再現のテスト（FR3.1・FR4.1 の `*SecretLeakIT`）を足す。方法は test-after（`code-generation-plan.md` の Testing Contract）。
- ここに書くコマンドは、この Intent で足す・変えるテストだけを名指しで流す。統合の前の全体の確かめは `./gradlew verify`（NFR1）と `./gradlew e2eTest`（NFR5）で、別に流す。
- 計画の D-1〜D-13 の答えで対象が変わるものは、各行に（D-x の A のとき）と書いた。

## 1. 道具と設定

| 側 | 道具 | 設定 |
|---|---|---|
| backend 単体 | JUnit 5・AssertJ・jqwik（性質ベース、失敗時の種を記録） | `backend/build.gradle.kts` の `tasks.test`（`*Test`） |
| backend 結合 | JUnit 5・Spring Boot Test・`OutputCaptureExtension`・組み込みの H2（`TestDatabase`）・Testcontainers（対象DB） | `integrationTest`（`*IT`）。TRACE の確かめは既存の `SafeYamlReaderSecretLeakIT` と同じ形（`LoggingSystem` で `cherry.mastersmith` を TRACE にし、捕まえた出力を検索する） |
| frontend | Vitest・Testing Library（jsdom）・user-event・vitest-axe・fast-check | `frontend/vitest.config.ts`（`thresholds` は行 80・分岐 70。変えない） |
| E2E | Playwright | `frontend/playwright.config.*`。WAR とメールの受け手（Mailpit）が要る |
| k6 | k6（`grafana/k6` のイメージ） | 台本の読み込みの確かめだけ（流すのは Build and Test） |

前提: colima が動いていて、シェルに `DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` を渡す（README の値。渡さないと対象DB のテストが SKIPPED になる）。長い実行は `caffeinate -dims` で包む。既存のテストは計画の Step で直したうえで、すべて通ったままにする。

## 2. この Intent で足す・変えるテスト

| 要件 | テスト（場所） | 種類 | 確かめること |
|---|---|---|---|
| FR1.1〜FR1.4 | 新しいテストは無い | — | 版の更新は `./gradlew verify`・`./gradlew e2eTest` と計画の Step 8 の残りの検索で確かめる |
| FR2.3・FR2.4・FR2.5 | `frontend/src/app/layout/ShellLayout.test.tsx` | 画面 | トップバーの畳むボタンとユーザーメニューのトリガーの名前が ja・en で出し分けられる。畳んだ状態で今の項目を子孫に持つ1段目のまとまりの名前に「今のページを含む」の文言が付く。axe |
| FR2.2 | `frontend/src/app/work-role/WorkRoleSwitcher.test.tsx` | 画面 | `:has()` の上書きを外しても一覧が開き選べる（既存のテストが通る） |
| FR2.2・FR2.5・FR3.2 | `frontend/e2e/160-app-frame-accessibility.e2e.ts`・`120-user-admin-accessibility.e2e.ts`・`140-role-admin-accessibility.e2e.ts`・`060-invitation-accessibility.e2e.ts` | E2E（書き換え） | 英語のトップバーの文言、畳んだ状態の印、axe のすべての組（畳んだ・開いた）、表の右端の Dropdown が画面に収まる（360px を含む）、060・120 の 360px の横のはみ出しなし |
| FR3.1（再現） | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslSecretLeakIT.java`（新） | 結合 | TRACE で目印の値を持つ誤りのある DSL を投入しても、目印がアプリのログに出ない。直す前に落ちることを一度確かめる |
| FR3.1 | `backend/src/test/java/cherry/mastersmith/dsl/domain/DslErrorTest.java`（新）、`dslmanage/domain/DslManageDomainTest.java`（D-11 の A のとき `DslErrorItem`） | 単体 | `toString` に埋める値・文言・`path` が出ず、種類・行・列・鍵・件数が出る |
| FR3.2（R-07、D-6 の A のとき） | `frontend/src/features/dsl/DslDiffTable.test.tsx`・`DslPreviewPanel.test.tsx` | 画面 | 違いが無いときの文言と見出しの説明が、メニューを比べないことを ja・en で伝える |
| FR3.2（R-08、D-7 の A のとき） | `backend/src/test/java/cherry/mastersmith/dslmanage/service/DslStartupLoaderTest.java`・`DslStartupIT.java`、`dslmanage/web/DslAdminApiIT.java`、`frontend/src/features/dsl/DslStatusPanel.test.tsx` | 単体・結合・画面 | 落とした項目の数が状態の応答に出る（落とさなければ 0）。1以上なら画面に警告が出る |
| FR4.1（再現） | `backend/src/test/java/cherry/mastersmith/navigation/web/NavigationSecretLeakIT.java`（新） | 結合 | TRACE で `GET /api/me/navigation`・`GET /api/me/table-access` を呼んでも、DSL の表示名の目印がログに出ない。直す前に落ちることを一度確かめる |
| FR4.1 | `backend/src/test/java/cherry/mastersmith/navigation/domain/NavigationRecordsToStringTest.java`（新） | 単体 | 6つの record の `toString` に名前・表示名が出ない |
| FR5.1 | `frontend/src/shared/tree/SharedTreeView.test.tsx` | 画面 | 失敗の文が `role="alert"`、フォーカスの区画に名前、失敗の状態の axe（`waitFor` で待つ） |
| FR5.2（R-03、D-9 の A のとき） | `frontend/src/app/registry/validateRegistrations.test.ts`・`frontend/src/app/navigation/navigationItems.test.ts`・`frontend/src/app/admin-forbidden/forbiddenHeading.test.ts` | 画面 | 見本が決まりに合う組（か反する見本と分かる名前）で通る |
| FR5.2（R-05） | `backend/src/test/java/cherry/mastersmith/ApiAccessArchitectureTest.java`・`ApiAccessConsistencyIT.java` | 単体・結合 | 共通の定数 `common/testsupport/ApiEndpointCounts` の下限で通る |
| FR6.1・FR6.2 | `frontend/src/shared/slow-notice/useSlowNotice.test.tsx`（新。3つの複製のテストをまとめる）と、groupadmin・roleadmin・roletransfer の画面のテスト | 画面 | まとめたフックの正常系と時間の境、3つの機能の画面が今までどおり通る |
| FR7.1 | `perf/k6/scenarios.js` | 台本 | `k6 inspect --include-system-env-vars` で読める（流すのは Build and Test） |
| FR9.2 | `backend/src/test/java/cherry/mastersmith/dsl/parse/SafeYamlParserTest.java`・`SafeYamlParserPropertyTest.java` | 単体 | 上限ちょうどの行は受け付け、上限＋1 は拒否。`\r\n`・`\r`、全角はコードポイントで数える。拒否に本文が入らない。性質: 上限以下の行だけなら行の長さで拒否されない |
| FR9.2 | `dsl/service/DefaultDslReaderTest.java`・`SafeYamlReaderTest.java`、`role/transfer/RoleTransferReaderTest.java` | 単体 | 投入と起動時の読み方、権限の YAML の読み方で上限を超える行を拒否する |
| FR9.2 | `dslmanage/generate/DslTreeBuilderTest.java`・`DslYamlWriterTest.java`、`role/transfer/RoleTransferWriterTest.java` | 単体 | 生成した既定の DSL（長いコメントを切る。D-4 の A のとき）と権限の書き出しの最も長い行が上限の内で、`SafeYamlParser` で読める |
| FR9.2 | `dslmanage/web/DslAdminApiIT.java`、`role/web/RoleTransferLimitsApiIT.java` | 結合 | 上限を超える行は 422 の Problem Details で拒否され、YAML の部品の例外の文と本文を含まない。上限ちょうどは受け付ける |
| FR10.1〜FR10.3 | `role/web/RoleBusyLogTraceIT.java`・`group/web/GroupBusyLogIT.java` | 結合 | `ROLE_BUSY`・`GROUP_BUSY` の応答の `code` と、応答の `traceId` の WARN がちょうど1行。ロールの割り当ての道の `GROUP_BUSY` が確かめられていなければ1件足す |

## 3. このテストだけを流すコマンド

リポジトリのルートで流す。backend:

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock

# ブランチ ② の単体（D-11 の A のとき DslManageDomainTest を含む）
caffeinate -dims ./gradlew :backend:test \
  --tests 'cherry.mastersmith.dsl.domain.DslErrorTest' \
  --tests 'cherry.mastersmith.dsl.domain.DslResultTypesTest' \
  --tests 'cherry.mastersmith.dslmanage.domain.DslManageDomainTest' \
  --tests 'cherry.mastersmith.navigation.domain.NavigationRecordsToStringTest' \
  --tests 'cherry.mastersmith.navigation.domain.NavNodeTest' \
  --tests 'cherry.mastersmith.navigation.domain.MenuFilterTest' \
  --tests 'cherry.mastersmith.dslmanage.service.DslStartupLoaderTest' \
  --tests 'cherry.mastersmith.ApiAccessArchitectureTest'

# ブランチ ② の結合（DslStartupIT・DslAdminApiIT は D-7 の A のとき）
caffeinate -dims ./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.dslmanage.web.DslSecretLeakIT' \
  --tests 'cherry.mastersmith.navigation.web.NavigationSecretLeakIT' \
  --tests 'cherry.mastersmith.dslmanage.service.DslStartupIT' \
  --tests 'cherry.mastersmith.dslmanage.web.DslAdminApiIT' \
  --tests 'cherry.mastersmith.ApiAccessConsistencyIT'

# ブランチ ③ の単体
caffeinate -dims ./gradlew :backend:test \
  --tests 'cherry.mastersmith.dsl.parse.SafeYamlParserTest' \
  --tests 'cherry.mastersmith.dsl.parse.SafeYamlParserPropertyTest' \
  --tests 'cherry.mastersmith.dsl.parse.YamlTreeConverterTest' \
  --tests 'cherry.mastersmith.dsl.service.DefaultDslReaderTest' \
  --tests 'cherry.mastersmith.dsl.service.SafeYamlReaderTest' \
  --tests 'cherry.mastersmith.role.transfer.RoleTransferReaderTest' \
  --tests 'cherry.mastersmith.role.transfer.RoleTransferWriterTest' \
  --tests 'cherry.mastersmith.role.service.RoleTransferServiceTest' \
  --tests 'cherry.mastersmith.dslmanage.generate.DslTreeBuilderTest' \
  --tests 'cherry.mastersmith.dslmanage.generate.DslYamlWriterTest'

# ブランチ ③ の結合
caffeinate -dims ./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.dsl.service.SafeYamlReaderSecretLeakIT' \
  --tests 'cherry.mastersmith.dslmanage.web.DslAdminApiIT' \
  --tests 'cherry.mastersmith.role.web.RoleTransferLimitsApiIT' \
  --tests 'cherry.mastersmith.role.web.RoleBusyLogTraceIT' \
  --tests 'cherry.mastersmith.group.web.GroupBusyLogIT'
```

`RoleTransferWriterTest`・`DslTreeBuilderTest`・`DslYamlWriterTest` は既にあるファイルに足す。`DslErrorTest`・`NavigationRecordsToStringTest`・`DslSecretLeakIT`・`NavigationSecretLeakIT`・`useSlowNotice.test.tsx`（shared）は新しく作る。

frontend（`frontend/` で流す。`package.json` の `test` と同じ設定を付ける）:

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/app/layout/ShellLayout.test.tsx \
  src/app/work-role/WorkRoleSwitcher.test.tsx \
  src/shared/tree/SharedTreeView.test.tsx \
  src/shared/slow-notice/useSlowNotice.test.tsx \
  src/app/registry/validateRegistrations.test.ts \
  src/app/navigation/navigationItems.test.ts \
  src/app/admin-forbidden/forbiddenHeading.test.ts \
  src/features/dsl/DslDiffTable.test.tsx \
  src/features/dsl/DslPreviewPanel.test.tsx \
  src/features/dsl/DslStatusPanel.test.tsx \
  src/features/dsl/DslAdminPage.test.tsx \
  src/features/groupadmin src/features/roleadmin src/features/roletransfer
```

E2E（書き換えたファイルだけ。先に WAR を作り Mailpit を起動する。README の手順）:

```bash
./gradlew :backend:bootWar
docker compose --profile mail up -d mailpit
(cd frontend && caffeinate -dims npx playwright test \
  e2e/060-invitation-accessibility.e2e.ts \
  e2e/120-user-admin-accessibility.e2e.ts \
  e2e/140-role-admin-accessibility.e2e.ts \
  e2e/160-app-frame-accessibility.e2e.ts)
```

D-7 の A で E2E の状態の見本を変えたときは `e2e/040-dsl-admin.e2e.ts`・`e2e/135-dsl-admin-accessibility.e2e.ts` も足す。k6 の台本の確かめ:

```bash
docker run --rm -v "$PWD/perf/k6:/scripts:ro" grafana/k6:<ブランチ ① で固定した版>@sha256:<ダイジェスト> \
  inspect --include-system-env-vars -e SCENARIO=workRoleRead /scripts/scenarios.js
```

## 4. カバレッジの下限

- backend: 行 80%・分岐 70% を全体と、`packagesJudgedByTotal` に無いパッケージごとに保つ。手を入れる見込みの dsl・dslmanage・navigation・role のパッケージは一覧に無く、パッケージごとの下限の対象。一覧の 7 パッケージには手を入れない（入れることになったら計画に戻す）。値は各ブランチの統合の前に、`:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測して記録する（UP-TO-DATE の数字は使わない）。
- frontend: `vitest.config.ts` の `thresholds`（行 80・分岐 70）。`npm run test:coverage` は `verify` の中で流れる。
- 下限・除外は緩めない。除外を足して下限を満たすことはしない。

## 5. モックとテストデータ

- 漏えいの確かめ（`*SecretLeakIT`）は、本物の Spring の部品と TRACE のログで行い、`TraceAspect` を外したりモックにしたりしない。目印は `LEAK_MARKER_` で始まる一意の文字列にする。
- BUSY の確かめは、既存の形（別の接続で行を持ち続けて上限切れを起こす）を使い、スレッドの数や `sleep` に頼らない。時刻に頼る処理は注入した時計で動かす。
- 1行の長さの境界は、テストの中で文字列を組み立てて作り、大きなファイルをリポジトリに置かない。大きさの境界のテストは、行の上限の内の行に分けて作る。
- 画面のテストは API の応答を見本で差し替える。見本は画面の側の型を付け、本物の応答と項目の名前・型を一致させる（状態の応答に項目を足すときは `testing/fixtures.ts` と E2E の `support/adminApiRoute.ts` を一緒に直す）。描画の後に出る値は `waitFor` で待ち、時間の上限は延ばさない。
- テストデータのメールアドレスは `example.com` などの予約のドメインだけにし、実在しそうな氏名・宛先を置かない。テストの説明文（`@DisplayName`・`describe`・`it`）は英語、データは日本語でよい。
