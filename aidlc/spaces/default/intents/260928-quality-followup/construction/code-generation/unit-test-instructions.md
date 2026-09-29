# テストの手順（Intent 260928-quality-followup）

scope bugfix・Test Strategy Minimal・methodology test-after（`code-generation-plan.md` の Testing Contract）。この Intent のテストは、要件ごとに確かめるテストか確かめの手順を1つ以上置き（NFR6）、bugfix の下限として不具合ごとの的を絞った確かめを加える。既存のテストはすべて通ったままにする。

## 1. 道具と設定

既存の道具と設定をそのまま使い、設定は変えない（依存の更新で道具の版が変わるときも、下限と対象の設定は変えない）。

| 対象 | 道具 | 設定の場所 |
|---|---|---|
| バックエンドの結合テスト（`*IT`） | JUnit Jupiter・AssertJ・Awaitility・Spring Boot Test・Testcontainers | `backend/build.gradle.kts` のタスク `integrationTest`（`includeTestsMatching("*IT")`、テストの JVM は `-Dh2.compactThreads=1`・`maxHeapSize = "1g"`・`-Djava.net.preferIPv4Stack=true`） |
| バックエンドの単体テスト（`*Test`） | JUnit Jupiter・AssertJ・jqwik・ArchUnit | 同じファイルのタスク `test` |
| 画面の単体テスト | Vitest・Testing Library（jsdom）・user-event・vitest-axe | `frontend/vitest.config.ts`（既定の上限 5 秒、`css: false`、下限 行 80・分岐 70） |
| 画面の CSS の検査 | Stylelint（stylelint-config-standard） | `frontend` の `lint:css` の設定 |
| E2E とブラウザのアクセシビリティの検査 | Playwright（Chromium）・axe-core | `frontend/playwright.config.ts`（ビルドした WAR を起動。`./gradlew e2eTest` は `verify` と CI の外） |

前提の環境:

- colima が動いていること。対象DB のテストのため、README の2つの環境変数をシェルに渡す（渡さないと対象DB のテストが SKIPPED になる。`project.md` の Testing Posture）。

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

- E2E は Mailpit を起動しておく（`docker compose --profile mail up -d mailpit`）。ブラウザは初回と Playwright の更新のときだけ `cd frontend && npx playwright install chromium`。

jsdom は色を計算しない（`css: false`）ため、文字のコントラスト（axe の `color-contrast`）は画面の単体テストでは判定できない。コントラストは E2E（実際のブラウザ）で確かめる。

## 2. この Intent のテストだけを流すコマンド

どのコマンドもリポジトリのルートで実行する。全体のコマンド（`npm test`・`./gradlew test` など）は使わない。

### 2.1 バックエンド（FR3.1・FR3.3・FR4.1・FR4.2・FR6.3・NFR3・NFR5）

```bash
./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.config.H2CompactionByPoolSuspensionIT' \
  --tests 'cherry.mastersmith.common.observability.HistogramBucketsIT' \
  --tests 'cherry.mastersmith.config.ExposureIT' \
  --tests 'cherry.mastersmith.invitation.web.RegistrationApiIT' \
  --tests 'cherry.mastersmith.invitation.web.InvitationAuditIT'
```

- `H2CompactionByPoolSuspensionIT`: 0 本の待ちの上限 30 秒と、時間切れのときの診断（FR3.1・FR3.3）。
- `HistogramBucketsIT`（新規）: `http.server.requests` の境界が 100・250・500・1000・2000・5000 ms のちょうど6つ、`mastersmith.mail.send` が 10000 ms までのちょうど7つ（FR4.1・FR4.2・NFR3）。
- `ExposureIT`（既存）: Web に公開する窓口が health だけのまま（NFR5）。
- `RegistrationApiIT`・`InvitationAuditIT`（既存）: README に書く「リンクの確かめの拒否は監査に残らない」「登録の完了の拒否は `REGISTRATION_FAILED` が残る」の裏付け（FR6.3・R-01）。

計画の Step 2 では、`HistogramBucketsIT` を除いた4つを変更の前に流し、コマンドが動くことを確かめる。

### 2.2 対象DB の結合テスト（FR5.3。イメージの更新を試すとき）

```bash
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.targetdb.*'
```

`TargetDbImages` の版と digest を上げたときに、3種類の DB（MySQL・MariaDB・PostgreSQL）の読み取りを確かめる。SKIPPED の件数が 0 であることを結果で確かめる。

### 2.3 画面の単体テスト（FR2・FR3.2・FR3.3）

```bash
cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/features/invitation/InvitationAdminPage.test.tsx \
  src/features/preferences/PreferencesForm.test.tsx \
  src/features/preferences/PreferencesPage.test.tsx \
  src/features/dsl/DslSubmitForm.test.tsx \
  src/app/pages/NotFoundPage.test.tsx
```

- `InvitationAdminPage.test.tsx`: 該当の1件の上限 15 秒と、失敗したときの時間の診断（FR3.2・FR3.3）。
- ほかの4つ: CSS のクラスを変えた画面の既存のテスト（表示とアクセシビリティの検査）が通ったままであること（FR2）。

`NODE_OPTIONS` は `frontend/package.json` の `test` と同じ値を使う。

### 2.4 画面の CSS の検査（FR2）

```bash
cd frontend && npx stylelint \
  src/features/preferences/PreferencesForm.css \
  src/features/dsl/DslSubmitForm.css \
  src/app/pages/Page.css
```

`.page-link` を消すとき（計画の Q3 が B・C のとき）は、あわせて `grep -rn "page-link" frontend/src` で使っている箇所が残らないことを確かめる。

### 2.5 E2E（FR1・FR2・NFR1）

WAR を作り、Mailpit を起動してから、この Intent に関わるファイルだけを流す。

```bash
./gradlew :backend:bootWar
docker compose --profile mail up -d mailpit
(cd frontend && npx playwright test \
  e2e/050-display-accessibility.e2e.ts \
  e2e/060-invitation-accessibility.e2e.ts \
  e2e/070-registration-accessibility.e2e.ts \
  e2e/080-preferences-accessibility.e2e.ts \
  e2e/100-app-text-contrast.e2e.ts)
```

- 050〜080: make-you-chic-ui の固定先の更新の後の既知の違反の一覧（FR1.2）。
- 100（新規）: アプリ独自の CSS の文字（`.preferences-choice-error`・`.dsl-link`・Q3 の答えによって `.page-link`）の 20 組のコントラスト（FR2・NFR1）。
- 100 だけを流すときは、上の `npx playwright test` の引数を `e2e/100-app-text-contrast.e2e.ts` だけにする。

統合の前の確かめ（計画の Step 25）では、`./gradlew e2eTest` で 010〜100 のすべてを流す（`team.md` の Testing Posture。画面に関わる変更のため）。

### 2.6 統合の前の関門（計画の Step 2・Step 18・Step 25）

絞ったコマンドの代わりではなく、関門として流す。テストの件数とカバレッジを報告するときは、UP-TO-DATE で飛ばさないよう次の形で流し、実測の数字だけを書く（`project.md` の Testing Posture）。

```bash
./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
```

## 3. カバレッジの目標

- 下限は変えない: バックエンドは JaCoCo で全体の合計とパッケージごとに行 80%・分岐 70%、画面は `@vitest/coverage-v8` の `thresholds` で行 80%・分岐 70%（`team.md` の Testing Posture）。計測の除外を増やさない。
- この Intent は `backend/src/main/java` と `frontend/src` の TypeScript のコードを変えない見込み（変えるのは設定・CSS・テストのコード・文書・依存の版）。そのため、カバレッジの値は変わらない見込みで、`packagesJudgedByTotal` の作業も付かない。Java のコードを足す必要が出たら（計画の Step 5）、足す前に止めて依頼者に確かめる。
- 依存の更新（Vitest 5・coverage-v8 5 など）で計測の値や判定が変わったときは、下限を下げずに、その更新を「通らずに見送り」とする（NFR4）。
- 変更の前（計画の Step 2）と後（Step 25）の実測を並べて記録する。

## 4. モックと差し替えの方針

- メールは、送信の部品をモックに替えない。バックエンドのテストは既存の JVM の中の SMTP の受け手（SubEthaSMTP）を使い、E2E と手元の確かめは Mailpit（手元の受け手）へ送る。実在の宛先・外部の SMTP には送らない（`team.md` の Testing Posture、`project.md` の Forbidden）。
- 対象DB は、版と digest を固定したイメージのコンテナ（Testcontainers）で起動した実際の MySQL・MariaDB・PostgreSQL を使い、H2 やモックで代えない（`project.md` の Mandated）。
- 内部DB は本番と同じ組み込みの H2 を使う。
- 指標は、`HistogramBucketsIT` で Spring の文脈の `MeterRegistry` を読み、実際の要求で記録された値を確かめる。登録先や `MeterFilter` をモックに替えない。
- E2E の 100 で、サーバーが選択のまとまりの誤りを返した状態を作るため、`PUT /api/me/preferences` だけを 400 の Problem Details（項目の誤り付き）に差し替える。見本は1か所にまとめ、画面の側の型を付ける。形は画面の API の型とバックエンドの誤りの応答の形で確かめてから書く。ほかの要求は差し替えない。ブランドカラーは既存の `prepareCombo`（`/api/appearance` の差し替え）で当てる。
- 時間に依存する確かめ（待ちの上限）は、`sleep` を使わず Awaitility の待ちで行う。FR3.3 の診断がわざと時間切れのときに出ることの確かめは、上限を一時的に極端に短くして Build and Test で1回だけ行い、コードに残さない。

## 5. テストデータ

- テストの説明文（`@DisplayName`・`describe`・`it`・Playwright の `test`）は英語で書く。テストデータは日本語でよい。
- メールアドレスは `example.com`・`example.test` のような実在しない宛先だけを使う。
- `HistogramBucketsIT` のログインの要求は、テストの内部DB（テストごとに用意し、実行順に依存しない）の中だけで行う。誤ったパスワードで送るため、監査とロックの状態はそのテストの内部DB にだけ残る。
- E2E は、実行ごとの一時の内部DB で起動した WAR を使う。100 は初期管理者でログインし、サーバーの状態を変える要求（保存・投入）を送らない。題・注記・添付・失敗の知らせに、メールアドレス・パスワード・トークンを入れない。json の結果に仮の資格情報が入っていないことは、既存の `frontend/playwright-secret-check-reporter.ts` で確かめる。
- 手元の監視の確かめ（計画の Step 7）は、配備した環境ではなく使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す。`perf/README.md`）で行い、本物のデータと監査ログを汚さない。要求を送る前に依頼者に伝える。

## 6. 要件ごとの確かめ

| 要件 | 確かめ | 種類 |
|---|---|---|
| FR1.1・FR1.2・NFR1 | E2E 050〜090 が更新の後の一覧で通る | E2E |
| FR1.3 | README の記述を E2E の結果と突き合わせる | 読み合わせ（Build and Test） |
| FR2.1・FR2.2・FR2.3・NFR1 | E2E 100、画面の単体テスト（2.3）、Stylelint（2.4） | E2E・単体・静的検査 |
| FR3.1 | `H2CompactionByPoolSuspensionIT` が通る | 結合 |
| FR3.2 | `InvitationAdminPage.test.tsx` が通る | 単体 |
| FR3.3 | 上限を一時的に短くして診断が出ることを1回確かめる | 手順（Build and Test） |
| FR3.4 | `code-summary.md` と `test-results.md` の差の節 | 記録 |
| FR4.1・FR4.2・NFR3 | `HistogramBucketsIT` | 結合 |
| FR4.3・FR4.4 | 手元の監視で名前・`le`・式の値を確かめる（計画の Step 7） | 手順 |
| FR5.1〜FR5.5・NFR4 | 更新ごとの `verify`（対象DB の結合テスト 2.2 を含む）と `e2eTest`、一覧 | 関門・記録 |
| FR6.1〜FR6.3 | ソースと既存の結合テスト（2.1 の `RegistrationApiIT`・`InvitationAuditIT`）で裏付けてから README に書く | 結合・記録 |
| NFR2 | 統合の後の CI の `verify` が通る | CI（Build and Test） |
| NFR5 | `ExposureIT`、診断の出力が数だけであることの読み合わせ | 結合・読み合わせ |
| NFR6 | 2.6 の関門と `./gradlew e2eTest` がすべて通る | 関門・E2E |
