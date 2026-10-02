# Unit Test Instructions — U2 ページ送りの共通化（u2-shared-paging）

U2 のテストの道具・実行のしかた・カバレッジの目標・性質ベースのテストの種の扱い・差し替えの方針・テストのデータの扱いを示す。テストの量は Standard（部品ごとに 5〜8 件の単体テストと、主な境界の結合テスト）。手順の番号は `code-generation-plan.md` の Step を指す。パスはリポジトリのルートからの相対パスで、Java のテストのクラスは `backend/src/test/java/cherry/mastersmith/` の下をパッケージ名で書く。

## 1. 道具と設定

| 用途 | 道具 | 設定の場所 |
|---|---|---|
| サーバーのテストの実行 | JUnit 5・AssertJ（既存） | `backend/build.gradle.kts` の `tasks.test`（名前が `*Test`）と `integrationTest`（名前が `*IT`） |
| サーバーの性質ベースのテスト | jqwik（既存、`gradle/libs.versions.toml` の版） | `@Property(tries = 500)`。失敗した例の記録は `backend/src/test/resources/junit-platform.properties` の `jqwik.database = build/jqwik-database`（リポジトリに残さない） |
| 失敗の詳細の出力 | Gradle のテストの出力（既存） | `backend/build.gradle.kts` の `testLogging` の `exceptionFormat = FULL`（jqwik・fast-check の種を出力に残す） |
| 内部DB（招待の結合テスト） | 組み込みの H2（既存の `common/testsupport/TestDatabase`） | 招待の既存の結合テストがそのまま使う。コンテナは使わない（`team.md` の Testing Posture） |
| 画面のテストの実行 | Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe（既存） | `frontend/vitest.config.ts`（テストは `src/**/*.test.{ts,tsx}`） |
| 画面の性質ベースのテスト | fast-check（既存、`frontend/package.json` の 4 系） | 既定の回数（100 回）。失敗の報告に `seed` と `path` が出る |
| 構造の検査 | ArchUnit（既存） | 既存の `ArchitectureTest` と機能ごとの `*BoundaryArchitectureTest` を変えずに使う |
| カバレッジ | JaCoCo（サーバー）・`@vitest/coverage-v8`（画面）（既存） | `backend/build.gradle.kts` の `jacocoTestReport`・`jacocoTestCoverageVerification`・`packagesJudgedByTotal`、`frontend/vitest.config.ts` の `thresholds` |

新しいテストの設定のファイルと新しいテストの依存は足さない（NFR9.11）。既存の `test`・`integrationTest` のタスク、名前の決まり（`XxxTest`・`XxxIT`）、画面のテストの置き場（対象と同じ場所の `*.test.ts`）をそのまま使う。テストの説明文（`@DisplayName`・`@Label`・`describe`・`it`）は英語で書く。

## 2. この単位のテストの実行のしかた

リポジトリのルートで実行する。どのコマンドも U2 で作る・手を入れるテストと、U2 の変更の影響を受ける招待のテストだけを名指しする（プロジェクト全体のコマンドは、統合の前の関門の Step 13・15 だけで使う）。画面のテストは、`frontend/package.json` の `test` と同じ `NODE_OPTIONS=--no-experimental-webstorage` を付けて、ファイルかフォルダーを名指しする。

### 2.1 最初のテストより前の確かめ（Step 2）

作業ブランチの上で、既存の単体テスト（jqwik を含む）・結合テスト・画面のテスト（fast-check を含む）の道具が動くことを確かめる。どれも U2 で移す・参照先を変える既存のテストで、変更の前に通る:

```bash
./gradlew :backend:test --tests 'cherry.mastersmith.invitation.domain.InvitationPagingTest'
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.invitation.repository.InvitationRepositoryIT'
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/invitation/paging.test.ts)
```

まだ作っていない新しいテスト（`cherry.mastersmith.common.paging.PagingTest`・`frontend/src/shared/paging/paging.test.ts`）を名指しすると、Gradle の「一致するテストが無い」か Vitest の「テストのファイルが無い」で失敗する。これは想定どおりで、それを作った Step からコマンドが通る。Step 4・8 で移した後は、移す前のテスト（`InvitationPagingTest`・`features/invitation/paging.test.ts`）は消えるため、上の1行目と3行目は使わない。

### 2.2 サーバーのページ送り（Step 4）

```bash
./gradlew :backend:test --tests 'cherry.mastersmith.common.paging.PagingTest'
```

### 2.3 招待のサーバー（Step 6）

`PagingTest` と、招待の既存の単体テスト・結合テストをすべて流す（`cherry.mastersmith.invitation` の下。`InvitationBoundaryArchitectureTest` を含む）:

```bash
./gradlew :backend:test \
  --tests 'cherry.mastersmith.common.paging.PagingTest' \
  --tests 'cherry.mastersmith.invitation.*' \
  :backend:integrationTest \
  --tests 'cherry.mastersmith.invitation.*'
```

ページ送りに直接関わるものだけを先に見るとき:

```bash
./gradlew :backend:test \
  --tests 'cherry.mastersmith.invitation.service.InvitationServiceTest' \
  :backend:integrationTest \
  --tests 'cherry.mastersmith.invitation.web.InvitationAdminApiIT' \
  --tests 'cherry.mastersmith.invitation.web.InvitationAuditIT' \
  --tests 'cherry.mastersmith.invitation.repository.InvitationRepositoryIT'
```

### 2.4 画面のページ送り（Step 8）

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/shared/paging/paging.test.ts)
```

### 2.5 招待の画面と画面の静的検査（Step 10）

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/shared/paging src/features/invitation)
(cd frontend && npm run typecheck)
(cd frontend && npx prettier --check src/shared/paging src/features/invitation)
(cd frontend && npx oxlint src/shared/paging src/features/invitation && npx eslint src/shared/paging src/features/invitation)
```

- 型の検査（`tsc --noEmit -p tsconfig.json`）はテストではなく、プロジェクト全体の型を一度に確かめる道具のため、範囲を絞れない。古い `./paging` への import が残っていればここで止まる。
- ライセンスヘッダーの検査（`node scripts/check-license-header.mjs`）と Spotless（`./gradlew :backend:spotlessCheck`）は、Step 13 の `verify` の段で確かめる。

### 2.6 構造の検査（Step 11。どれも変えずに流す）

```bash
./gradlew :backend:test \
  --tests 'cherry.mastersmith.ArchitectureTest' \
  --tests 'cherry.mastersmith.*BoundaryArchitectureTest'
```

### 2.7 ログの1行の形の確かめ（Step 12 の A-02。変えずに流す）

```bash
./gradlew :backend:test --tests 'cherry.mastersmith.common.observability.JsonLogFormatTest'
```

A-01 の一時の確かめ（`code-generation-plan.md` の Step 12、9節の Q-B の決定）は、一時のテストのクラスを名指しして `:backend:integrationTest --tests '<一時のクラス>'` で流し、結果を記録した後にそのファイルを消す。消した後に `git status` で残っていないことを確かめる。このコマンドは Build and Test では流さない。

### 2.8 U2 のテストをまとめて流す

上の単体のクラスと結合のクラス、画面のテストを、まとめて流す（Build and Test がこの単位のテストを流すときもこれを使う）:

```bash
./gradlew :backend:cleanTest :backend:cleanIntegrationTest \
  :backend:test \
  --tests 'cherry.mastersmith.common.paging.PagingTest' \
  --tests 'cherry.mastersmith.invitation.*' \
  --tests 'cherry.mastersmith.ArchitectureTest' \
  --tests 'cherry.mastersmith.*BoundaryArchitectureTest' \
  --tests 'cherry.mastersmith.common.observability.JsonLogFormatTest' \
  :backend:integrationTest \
  --tests 'cherry.mastersmith.invitation.*'
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/shared/paging src/features/invitation)
```

- Gradle の `--tests` は、その直前のタスク（`:backend:test` か `:backend:integrationTest`）にだけ効く。
- テストの件数を報告するときは、UP-TO-DATE で飛ばされないよう `:backend:cleanTest`・`:backend:cleanIntegrationTest` を先に付けて実行し、実測の数字だけを報告する（`project.md` の Testing Posture）。
- U2 のテストはコンテナを使わないため、コンテナの実行環境（colima）が無くても動き、飛ばされない。統合の前の `./gradlew verify`（Step 13・15）は対象DB のテストを含むため、colima を動かし、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡し、`caffeinate -i` で包んで実行する（統合の前の関門で、この単位だけのコマンドではない）:

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
```

## 3. テストの一覧（Standard の量）

| 部品 | 単体 | 結合・既存の画面のテスト（変えずに流す） |
|---|---|---|
| Paging（`common.paging`） | `PagingTest`（10 件）: 移す6件（位置とページの境目 1・20・21・40・41、指定なしと正の整数、拒否する入力 `0`・`-1`・`1.5`・`abc`・空・` 1`・`+1`・`9999999999`、`offsetOf` の 1・2・`Integer.MAX_VALUE`、1 未満の例外、jqwik の「位置が載るページ」の性質）と、足す jqwik の性質3件（1〜999,999,999 の往復、数字以外・10 文字以上・`"0"` は空、`offsetOf` と `pageOf` の往復）と、`PAGE_SIZE` が 20 であることの確かめ1件 | `InvitationAdminApiIT`（一覧の 20 件・2ページ目の1件・最後のページより後の空の 200・不正な page の 400 `VALIDATION_FAILED`・`size` 20・招待中の重なりの page 2）、`InvitationAuditIT`（招待中の重なりのページ）、`InvitationRepositoryIT`（参照先だけを変える）、`InvitationServiceTest`（`InvalidPage` と空の一覧） |
| UiPaging（`src/shared/paging`） | `paging.test.ts`（11 件）: 移す7件（ページの数 0・20・21・43、範囲、0 件の範囲、補正する、補正しない、ボタンの端、fast-check の「範囲と補正が収まる」性質）と、足す fast-check の性質4件（範囲の隙間なしと件数の和、`pageCount` の上下、「次へ」が押せなくなる条件、行があれば補正しない） | `InvitationList.test.tsx`（ページ送りの状態の文とボタンの端、英語の表示）、`InvitationAdminPage.test.tsx`（ページの移動とフォーカス、最後のページより後の補正）、ほかの招待の画面のテスト |
| 構造 | 既存の `ArchitectureTest`・`*BoundaryArchitectureTest`（変えない） | — |
| ログの1行の形（A-02） | 既存の `JsonLogFormatTest`（変えない） | — |

- 単体テストが 5〜8 件を超えるのは、移すテストの事例をすべて残す決まり（NFR9.5）と、性質を足す決まり（NFR9.6、機能設計の 6節）を合わせたため。
- どのテストも、成功の場合と、少なくとも2つの失敗・境目の場合を含める（`phases/construction.md` の Testing Standards）。
- 招待の既存のテストは、import の行と `PAGE_SIZE` の参照先のほかは変えない。失敗したときはテストではなく、移した部品か参照の切り替えを直す（NFR9.5）。
- 本物のサーバーと画面を通したページ送りの操作は、B2 の関門（Step 15）の E2E の `060-invitation-accessibility.e2e.ts` の実データの1件（「次へ」「前へ」）が確かめる。U2 は E2E のファイルを足さず、変えない。

## 4. カバレッジの目標

- 全体: 行 80% 以上・分岐 70% 以上（既存の `jacocoTestCoverageVerification`、画面は `frontend/vitest.config.ts` の `thresholds`）。
- パッケージごと（行 80%・分岐 70%）:
  - `common.paging`（新しいパッケージ）: 自動で下限の対象になる。見込みは 行 100%（13/13）・分岐 100%（10/10）で、移すテストの事例で `parsePage` の3つの分かれ道（`null`・数字の形・1 以上）と、`pageOf`・`offsetOf` の 1 未満の分かれ道を両側とも通る（`code-generation-plan.md` 4.3）。
  - `invitation.domain`（すでに下限の対象）: 今 行 98.4%（246/250）・分岐 96.7%（117/121）。`InvitationPaging` が抜けた後の見込みは 行 98.3%（233/237）・分岐 96.4%（107/111）で、下限を満たし、足すテストは無い見込み。Step 1 と Step 13 で実測する。下回ったら、計測の除外を増やさず、同じパッケージのほかのクラスのテストを足す（残る危険 R1）。
  - `invitation.service`（参照先だけ）: 今 行 100.0%（332/332）・分岐 93.5%（87/93）。変わらない見込み。
  - 画面: 全体の合計で判定する。`src/shared/paging/` を計測から外さない（NFR9.10）。
- `packagesJudgedByTotal` を変えない（`invitation.*` は元から一覧に無い）。計測の除外を増やさない。下限の値を変えない（Testing Contract の coverage の決まり、`team.md` の Testing Posture）。
- この単位のテストだけのサーバーのカバレッジを見るときは、2.8 のサーバーのコマンドの後に報告を作る:

```bash
./gradlew :backend:jacocoTestReport
```

  報告は `backend/build/reports/jacoco/test/html/` と `backend/build/reports/jacoco/test/jacocoTestReport.xml`。単位のテストだけでは、ほかの単位のテストが通る経路の分だけ低く出る。下限の判定と記録する値は、Step 13・15 の `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` の実測とする（`team.md` の Testing Posture）。
- 画面のテストを範囲を絞って流すときは `--coverage` を付けない。`frontend/vitest.config.ts` の `thresholds` は `src/**` 全体に当たるため、範囲を絞ると下限で失敗する。画面のカバレッジは `verify` の画面の段で判定する。

## 5. 性質ベースのテストと乱数の種

- **jqwik（`PagingTest`）**: どの性質も `@Property(tries = 500)`。失敗すると、Gradle のテストの出力（`exceptionFormat = FULL`）に `seed` と縮めた例が出る。再現は、そのテストに `@Property(tries = 500, seed = "<出た seed>")` を一時的に付けて 2.2 のコマンドで流す。原因を直した後に `seed` の指定を外す（指定を残してコミットしない）。失敗した例の記録は `build/jqwik-database` に置かれ、リポジトリに残らない。
- **fast-check（`paging.test.ts`）**: 既定の 100 回。失敗すると、報告に `seed` と `path` が出る。再現は、その `fc.assert(property)` を `fc.assert(property, { seed: <seed>, path: '<path>' })` に一時的に替えて 2.4 のコマンドで流す。原因を直した後に元に戻す。
- CI で失敗したときも、Gradle と Vitest の出力が CI の記録に残るため、同じ手順で手元で再現する。再現の仕方は、それぞれのテストのファイルの先頭の説明文に書く（NFR9.7）。
- 失敗の種と再現の結果は `generation-notes.md`（Build and Test では `test-results.md`）に記録する。手元で再現した失敗は原因を直すまで統合しない（`team.md` の「不安定なテストと CI の失敗」）。

## 6. 差し替え（モック・スタブ）の方針

- **Paging・UiPaging は差し替えない**: どちらも状態を持たない純粋な関数のため、テストでは本物を呼ぶ。招待の業務処理と画面のテストも、本物の Paging・UiPaging を通す（`./paging` を差し替えている既存の画面のテストは無い）。
- **招待の既存のテストの差し替え**: `InvitationServiceTest` の DB アクセスの部品の差し替え（Mockito）など、既存のテストの差し替えはそのままにし、変えない。
- **時計**: U2 の計算は時刻に依存しない。招待の既存のテストの注入した時計（`MutableClock` など）はそのまま使う。
- **E2E**: U2 は E2E のファイルを足さず、変えない。B2 の関門（Step 15）の `./gradlew e2eTest` の後は、`frontend/test-results/e2e-results.json` の `stats` とテストごとの題・状態・注記の種類だけを読んで記録し、`frontend/playwright-report/`・`frontend/test-results/` を消す（`gate-decisions.md` の決定）。

## 7. テストのデータ

- `PagingTest`・`paging.test.ts` のデータは、整数（ページの番号・位置・全体の件数・行の数）と page の文字列（数字・符号・空白・小数・数字でない文字）だけ。メールアドレス・氏名・パスワード・トークンを置かない（リポジトリは公開、`team.md` の Testing Posture）。
- 性質ベースのテストの値の範囲: jqwik は位置 1〜1,000,000（既存）、page の文字列の値 1〜999,999,999、`offsetOf` と `pageOf` の往復は page 1〜`Integer.MAX_VALUE - 1`。fast-check は全体の件数 1〜100,000（既存と同じ）と、その範囲で正しいページ。
- 招待の既存の結合テストは、テストのクラスごとに一時ディレクトリの H2 を使い、テストごとに招待と利用者を作る（既存のまま。予約のドメインのメールアドレスだけを使う）。U2 はテストのデータを足さない。
- A-01 の一時の確かめで送る page の文字列は、数字だけの長い文字列（例 7,000 文字と 9,000 文字）とし、個人に関する値を含めない。結果の記録は状態コードと `code` だけにする。
