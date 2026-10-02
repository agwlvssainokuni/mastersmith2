# Unit Test Instructions — U4 管理の画面の 403 の共通の扱い（u4-admin-forbidden-ui）

U4 のテストの道具・実行のしかた・カバレッジの目標・性質ベースのテストの種の扱い・差し替えの方針・テストのデータの扱いを示す。テストの量は Standard（部品ごとに 5〜8 件の単体テストと、主な境界のテスト）。手順の番号は `code-generation-plan.md` の Step を指す。パスはリポジトリのルートからの相対パスで、画面のファイルは `frontend/src/` の下を `src/...` と書く。U4 は画面だけの単位で、バックエンドのテストを足さず、変えない。

## 1. 道具と設定

| 用途 | 道具 | 設定の場所 |
|---|---|---|
| 画面のテストの実行 | Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe（既存） | `frontend/vitest.config.ts`（テストは `src/**/*.test.{ts,tsx}`、`e2e/` は対象外）、`frontend/vitest.setup.ts` |
| 画面の性質ベースのテスト | fast-check（既存、`frontend/package.json` の版） | 既定の回数（100 回）。失敗の報告に `seed` と `path` が出る |
| 骨組みの中に描くテストの補助 | 既存の `src/app/testing/renderWithProviders.tsx`（Step 7 で `AdminForbiddenProvider` を足す）、`src/features/dsl/testing/renderDsl.tsx`・`src/features/invitation/testing/renderInvitation.tsx`（Step 10 で ShellLayout の中に描く選択とログイン状態の提供元を渡す口を足す。既定は今のまま） | — |
| 実際のブラウザの検査（E2E） | Playwright（既存）＋ axe-core（既存の devDependencies）。補助は既存の `frontend/e2e/support/`（`loginPreferences.ts` は Step 13 で型に省略できる `displayName` を足す。ほかは変えない） | `frontend/playwright.config.ts`（変えない。`workers: 1`、json の報告 `test-results/e2e-results.json`、`playwright-secret-check-reporter.ts`） |
| 型・書式・リンタ | `tsc --noEmit -p tsconfig.json`（`include` に `e2e` がある）・Prettier・oxlint・ESLint・Stylelint（既存） | `frontend/tsconfig.json`・`.prettierrc`・`oxlint` と `eslint.config.js`・Stylelint の設定（どれも変えない） |
| カバレッジ | `@vitest/coverage-v8`（既存） | `frontend/vitest.config.ts` の `thresholds`（行 80%・分岐 70%、全体の合計）。報告は `frontend/coverage/`（`coverage-summary.json` を含む） |
| 初回の JavaScript の大きさ | 既存の `frontend/scripts/check-bundle-size.mjs`（`./gradlew frontendBundleSize`、`verify` の段 9） | 目安 gzip 500KB（超えたら警告だけ） |

新しいテストの設定のファイルと新しいテストの依存は足さない（NFR9.6）。テストの説明文（`describe`・`it`・`test`・`test.step` の題）は英語で書く。テストのファイルは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、新しいファイルの先頭に `/* ... */` の Apache License 2.0 のヘッダー（2026、agwlvssainokuni）を置く。

## 2. この単位のテストの実行のしかた

リポジトリのルートで実行する。どのコマンドも U4 で作る・書き換えるテストと、U4 の変更の影響を受けるテストのファイルかフォルダーを名指しする（プロジェクト全体のコマンドは、B2 の関門の Step 17 だけで使う）。画面のテストは、`frontend/package.json` の `test` と同じ `NODE_OPTIONS=--no-experimental-webstorage` を付ける。範囲を絞って流すときは `--coverage` を付けない（`thresholds` は `src/**` 全体に当たるため、範囲を絞ると下限で失敗する。カバレッジは Step 17 の `verify` で判定する）。

### 2.1 最初のテストより前の確かめ（Step 2）

作業ブランチ `feature/260930-user-admin-b2` の上で、変更の前に、U4 が書き換える・足す先の既存のテストが通り、道具（Vitest・fast-check・vitest-axe・Playwright）が動くことを確かめる:

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/shared/api-client/apiClient.test.ts \
  src/app/layout/ShellLayout.test.tsx \
  src/app/routing/decideRoute.test.ts \
  src/app/routing/AppRouter.test.tsx \
  src/app/display-settings/displaySettingsStore.test.ts \
  src/app/display-settings/DisplaySettingsProvider.test.tsx \
  src/app/i18n/messages.test.ts \
  src/features/admin/AdminAreaPage.test.tsx \
  src/features/admin/adminAreaStatus.test.ts \
  src/features/dsl/DslAdminPage.test.tsx \
  src/features/invitation/InvitationAdminPage.test.tsx \
  src/features/invitation/failureMessage.test.ts)
(cd frontend && npx playwright test --list e2e/060-invitation-accessibility.e2e.ts)
```

- 2行目は Playwright が E2E のファイルを読めて、テストの一覧（060 の 21 件）を出せることだけを確かめる。`--list` はアプリ（WAR）を起動しない。
- まだ作っていない新しいテスト（`src/shared/api-client/adminForbidden.test.ts`・`src/app/admin-forbidden/*.test.*`・`frontend/e2e/130-admin-forbidden-accessibility.e2e.ts`）を名指しすると、Vitest は「テストのファイルが無い」、Playwright は「テストが見つからない」で失敗する。これは想定どおりで、それを作った Step からコマンドが通る。

### 2.2 判定と読み直しの口（Step 4）

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/shared/api-client/adminForbidden.test.ts \
  src/shared/api-client/apiClient.test.ts \
  src/shared/api-client/apiClient.download.test.ts)
```

`apiClient.download.test.ts` は変えずに流し、`refreshSessionOnce` を足しても既存の受け取りの道が変わらないことを確かめる。

### 2.3 骨組みの状態と表示（Step 6）

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/app/admin-forbidden \
  src/app/i18n/messages.test.ts)
```

### 2.4 振り分けと置き換え（Step 8）

U4 の骨組みのテストと、`App.tsx`・`renderWithProviders` の変更の影響を受ける `src/app` のすべてのテストを流す:

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/app src/shared/api-client)
```

### 2.5 既存の3つの画面（Step 10）

書き換えた3つの画面のテストと、描画の補助を使うほかのテスト（`renderDsl`・`renderInvitation` を使う部品のテストと登録のテスト）を流す。`renderWithProviders` に `AdminForbiddenProvider` が入っても通り続けることも、ここで確かめる:

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/features/admin \
  src/features/dsl \
  src/features/invitation)
```

`renderWithProviders` を使うほかの機能（`src/features/auth`・`src/features/preferences`・`src/features/registration`）が通り続けることの確かめ（NFR9.11）:

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/features/auth \
  src/features/preferences \
  src/features/registration)
```

### 2.6 自分の氏名と言語の反映（Step 12）

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/app/display-settings \
  src/app/layout/ShellLayout.test.tsx)
```

### 2.7 画面の静的検査と E2E のファイルの読み込み（Step 13・14）

```bash
(cd frontend && npm run typecheck)
(cd frontend && npx prettier --check \
  src/shared/api-client src/app/admin-forbidden src/app/routing src/app/layout \
  src/app/display-settings src/app/i18n src/app/App.tsx src/app/testing \
  src/features/admin src/features/dsl src/features/invitation \
  e2e/130-admin-forbidden-accessibility.e2e.ts e2e/support/loginPreferences.ts)
(cd frontend && npx oxlint \
  src/shared/api-client src/app src/features/admin src/features/dsl src/features/invitation e2e \
  && npx eslint \
  src/shared/api-client src/app src/features/admin src/features/dsl src/features/invitation e2e)
(cd frontend && npx stylelint "src/app/admin-forbidden/**/*.css")
(cd frontend && node scripts/check-license-header.mjs)
(cd frontend && npx playwright test --list \
  e2e/060-invitation-accessibility.e2e.ts e2e/130-admin-forbidden-accessibility.e2e.ts)
```

- 型の検査は範囲を絞れない（プロジェクト全体の型を一度に確かめる道具のため）。`LoginPreferences` の型の変更と 130 の型もここで確かめる。
- `--list` で 060 が 21 件、130 が 20 件（既存の 20 組）であることを確かめる。130 の実行は B2 の関門（Step 17）の `./gradlew e2eTest` の中で行う（2.8）。
- README（リポジトリのルート）は Prettier（`frontend` の中の `prettier --check .`）と Spotless の対象の外にある。README の直しは、読み直しと Step 15 の確かめ（表の行の数と 130 の節）で確かめる。検査の範囲は変えない。

### 2.8 E2E（Step 17、B2 の関門で1回）

E2E は、130 を含めて `./gradlew e2eTest` の中で流す（`verify` と CI の外）。130 だけのコマンドは作らない（共有の手伝い `loginPreferences.ts` に手が入るため、既存の 010〜100 が通ることの確かめを兼ねる。NFR9.10）。前提は Mailpit の起動と Chromium:

```bash
docker compose ps
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8025/api/v1/info
# 200 でなければ起動する（.env は開かない）
docker compose --profile mail up -d mailpit
caffeinate -i ./gradlew e2eTest
```

- 期待: 11 ファイル・130 件（010〜100 の 110 件と 130 の 20 件）がすべて成功し、失敗・飛ばした・不安定が 0。060 の実データの1件が飛ばされたときは、注記 `skip-reason` を記録する（U2 の計画の Step 15）。
- 失敗を調べるために 130 だけを流すときは、`./gradlew :backend:bootWar` と Mailpit の起動の後に `(cd frontend && npx playwright test e2e/130-admin-forbidden-accessibility.e2e.ts)` を使ってよい（README の 050〜100 と同じ形）。ただし、直した後の合否は `./gradlew e2eTest` の全体で決める。
- 流した後は、`frontend/test-results/e2e-results.json` の `stats` とテストごとの題・状態・注記（130 の組ごとの記録）だけを読んで記録し、その後に `frontend/playwright-report/`・`frontend/test-results/` を消す（`code-generation-plan.md` の Step 17）。json のほかの中身と html の報告は開かない。2語の氏名の値が json の報告に含まれないことは、値を表示しない形で件数だけを数える（例: `grep -c` の件数だけを記録する）。

### 2.9 U4 のテストをまとめて流す

U4 で作る・書き換える画面のテストと、影響を受けるテストを、まとめて流す（Build and Test がこの単位のテストを流すときもこれを使う）:

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/shared/api-client \
  src/app \
  src/features/admin \
  src/features/dsl \
  src/features/invitation)
```

- 実測の件数は、このコマンドの出力の数字だけを報告する。カバレッジは Step 17 の `verify` の画面の段（`frontendCoverage`）の実測とする。
- U4 のテストはコンテナを使わないため、コンテナの実行環境（colima）が無くても動き、飛ばされない。統合の前の `./gradlew verify`（Step 17）は対象DB のテストを含むため、colima を動かし、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡し、`caffeinate -i` で包んで実行する（B2 の関門で、この単位だけのコマンドではない）:

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
```

## 3. テストの一覧（Standard の量）

| 部品 | テストのファイル | 新しい・書き換え | 件数の見込み | 確かめる内容（要点） |
|---|---|---|---|---|
| `isAdminForbidden`（`src/shared/api-client/adminForbidden.ts`） | `adminForbidden.test.ts` | 新しい | 8（例 6・性質 2） | 3つの条件がそろうときだけ true。`/api/me/…` の 403、`/api/administrator`、code の無い・違う 403、401・404・409・500、通信の失敗、`null`・`undefined`・文字列・数は false で例外を出さない。性質: パス・状態・code・`kind` の組でそろうときだけ true、任意の値で例外を出さない |
| `refreshSessionOnce`（`apiClient.ts`） | `apiClient.test.ts` | 足す | 4 | 登録された更新を1回呼び結果を返す。401 の更新と同時に呼んでも更新は1回（同じ約束）。登録が無ければ false で `fetch` を呼ばない。新しい要求を送らない（`fetch` の呼び出しが0）。既存の 401 のテストは変えずに通る |
| `forbiddenHeadingKey`（`src/app/admin-forbidden/forbiddenHeading.ts`） | `forbiddenHeading.test.ts` | 新しい | 5（例 4・性質 1） | 登録されたサイドバーの項目の鍵を返す。`visibleWhen: 'ADMIN'` の項目も探す。URL の引数を持つ path に合う。無ければ `adminForbidden.heading`。性質: 登録に無い URL はいつも共通の鍵 |
| `AdminForbiddenProvider`・`useAdminForbidden`・`useIsAdminForbiddenHere` | `AdminForbiddenProvider.test.tsx` | 新しい | 12 | `report` が true のとき今の URL が S6 になり更新が1回。同じ URL の重なりで1回。false の失敗で何も変わらない。URL が変わると S6 が消える。Provider の外で呼ぶと例外。R-02 の3点（A → B → 戻るで A は中身、B へ移った最初の描画でも S6 を描かない／外れた後に渡した 403 で B は S6 にならず更新は呼ばれる／関数の同一性と、それを依存にした `useEffect` が繰り返されない）。読み直しが終わる前の結び付かない 403 で1回。保留の更新でも `report` の直後の描画で S6（NFR9.1）。偽の時計を進めても更新が呼ばれない。`detail` の目印が出ない・`console` の5つの関数が呼ばれない |
| `AdminForbiddenView` | `AdminForbiddenView.test.tsx` | 新しい | 7 | ja・en の文言。Alert が `role="status"`。見出しへのフォーカス（`waitFor`）。「ホームへ戻る」を Tab で届き Enter でホームへ移る（読み込み直しなし）。見出しが登録の項目の名前・無ければ「管理」。`detail` の目印と `console` の呼び出しが無い。アクセシビリティの検査（vitest-axe）1件 |
| 文言 | `src/app/i18n/messages.test.ts` | 足す | 1 | `adminForbidden.message`・`homeLink`・`heading` が ja・en の両方にある（既存の鍵のそろいのテストも通る） |
| `ShellLayout` | `ShellLayout.test.tsx` | 足す | 5 | 権限が無い URL のとき子の代わりに S6、サイドバーと上の帯は残る。AC2.2.2 のきっかけごと（403 の後の読み直し、トークンの更新の応答、ログインし直しの応答）に、管理者でない状態の知らせで管理のメニューが消える（`waitFor`）。S6 の後も管理の外の画面へ移れる |
| `decideRoute` | `decideRoute.test.ts` | 書き換え 1・足す 2 | 3 | ログインしていて管理者でない利用者の `ADMIN` の画面は `ADMIN_FORBIDDEN`（`route` 付き）。ログインしていなければログインの画面へ。登録に無い URL は今までどおり `NOT_FOUND` |
| `AppRouter` | `AppRouter.test.tsx` | 書き換え 1・足す 3 | 4 | 管理者でない利用者の管理の画面が S6 で、偽物の `fetch` に管理の API が届かない。起動の時の更新で印が外れた状態から描くと管理のメニューが無く S6。ForbiddenByApi → ForbiddenByRoute で見出しの要素が同じもの・フォーカスが見出しに残る・サイドバーの開閉が変わらない（R-05） |
| `applyOwnProfileFor`（`displaySettingsStore.ts`） | `displaySettingsStore.test.ts` | 足す | 5 | 氏名と言語が `savedUser` に入り、テーマと文字の大きさは渡した当てている値のまま。ブラウザの保存は言語だけ。言語の見せ方は捨て、テーマと文字の大きさの見せ方は残す。ja・en 以外は言語を変えず氏名だけ。次のログイン状態でサーバーの値に戻る |
| `applyOwnProfile`・`useApplyOwnProfile` | `DisplaySettingsProvider.test.tsx` | 足す | 8 | 上の帯の氏名と画面の言語・`<html lang>`・`Accept-Language` が変わる。テーマと文字の大きさ（ほかのタブで変わった保存の値を含む）は変わらない。見せ方で試しに当てたテーマが保存されない（R-01）。ログインしていないときは何もしない。印が変わらない。保存の途中で更新が入っても新しいログイン状態に結び付く（S-1）。関数の同一性。ブラウザの保存のどの鍵にもテストの氏名が無い |
| 管理の入口 | `AdminAreaPage.test.tsx`・`adminAreaStatus.test.ts` | 書き換え（描き方・2件・4件） | 既存の件数のまま | 描き方を ShellLayout の中・管理者の提供元に直す。確かめの API の 403・`ACCESS_DENIED` で S6（「ページが見つかりません」ではない）。表示のたびに確かめをやり直すテストも S6。`statusFromError` は `report` が true なら `Forbidden`、false なら `Error`。性質は「`ACCESS_DENIED` 以外の 403 も `Error`」 |
| DSL の管理 | `DslAdminPage.test.tsx`（`renderDsl` を使う） | 書き換え 1・足す 2 | +2 | 状態の読み込みの 403・`ACCESS_DENIED` で S6。確かめの表示を開いた状態の操作の 403 で表示が閉じて S6。code の無い 403 は今の誤りの扱い |
| 招待の管理 | `InvitationAdminPage.test.tsx`（`renderInvitation` を使う）・`failureMessage.test.ts` | 書き換え 3か所・1件、足す | +3 | 一覧・入力の表示・送り直しの 403・`ACCESS_DENIED` で S6（入力の表示は閉じる）。今の「一般の文言」の確かめは code の無い 403 に置き換える。`failureMessageKey` は code の無い・違う 403 で一般の 4xx の文言 |
| 実際のブラウザの検査 | `frontend/e2e/130-admin-forbidden-accessibility.e2e.ts` | 新しい | 20（既存の 20 組） | 組ごとに axe 違反 0（`REQUIRED_RULES` が走った）、はみ出し無し、Alert が `role="status"`、読み直しの応答の後も見出しにフォーカス、Avatar の文字の数が 2、`detail` の目印が画面に無い、ログインと復元の書き換えが1回以上、全 20 組で「ホームへ戻る」でホームへ移り S6 が消える（計画 9節の Q-A の決定 A）、引いた後の問題 0・CSP 違反 0・管理の API への GET 以外の要求 0 |

- 部品ごとに 5〜8 件を目安とし、Provider と表示の設定の Provider は決まりの数（R-02 の3点・NFR9.1・NFR9.2・NFR3.1 と S-1）を合わせるため多くなる。
- どのテストも、成功の場合と、少なくとも2つの失敗・境目の場合を含める（`phases/construction.md` の Testing Standards）。
- 403 の作り方は画面ごとに違う（機能設計の 8節）: DSL の管理・招待の管理は画面に渡す偽物の API（ApiClient を通らない）、管理の入口は偽物の `fetch`（ApiClient を通る、`registerAuthHandlers` で偽物の更新を登録）。偽物の API を使う画面のテストで更新を登録していないときは、`refreshSessionOnce` は false を返して何もしない。
- 書き換えた既存のテストの一覧（ファイル・テストの題・書き換えの中身）は `generation-notes.md` と `code-summary.md` に記録する（NFR9.11）。
- E2E の流れの本数は増やさない。`030-admin-access.e2e.ts` は変えない。130 は操作の流れではないため、流れの本数に数えない（NFR9.10）。

## 4. カバレッジの目標

- 画面の全体: 行 80% 以上・分岐 70% 以上（`frontend/vitest.config.ts` の `thresholds`、全体の合計）。基準は U2 の後の実測 行 97.44%（2322/2383）・分岐 92.67%（1443/1557）。U4 の後も下限を満たし、基準から大きく下げないことを目標とし、Step 17 の `verify` の実測（`frontend/coverage/coverage-summary.json` の全体と、U4 で作った・変えたファイルの行・分岐）を記録する。
- U4 で作るファイル（`src/shared/api-client/adminForbidden.ts`・`src/app/admin-forbidden/*`・`src/app/display-settings/useApplyOwnProfile.ts`）は、3節のテストで行・分岐とも 90% 以上を目安にする（下限の値ではなく、記録のための目安。下回ったら理由を記録する）。
- 計測の除外を増やさない（`src/main.tsx`・型の宣言・テストのファイルのまま）。下限の値を変えない（Testing Contract、`team.md` の Testing Posture、NFR9.8）。`frontend/e2e/` は既存どおり計測の対象外。
- バックエンドは変えないため、`packagesJudgedByTotal` とパッケージごとの下限に U4 の作業は無い。B2 の関門の `verify` で全体の値を記録する（U2 の計画の Step 15）。
- 範囲を絞ったテストの実行では `--coverage` を付けない（2節の冒頭）。

## 5. 性質ベースのテストと乱数の種

- **fast-check（`adminForbidden.test.ts`・`forbiddenHeading.test.ts`・`adminAreaStatus.test.ts`）**: 既定の 100 回。失敗すると、報告に `seed` と `path` が出る。再現は、その `fc.assert(property)` を `fc.assert(property, { seed: <seed>, path: '<path>' })` に一時的に替えて、そのファイルを名指しして流す。原因を直した後に元に戻す（指定を残してコミットしない）。再現の仕方は各テストのファイルの先頭の説明文に書く。
- CI で失敗したときも、Vitest の出力が CI の記録に残るため、同じ手順で手元で再現する。
- 失敗の種と再現の結果は `generation-notes.md`（Build and Test では `test-results.md`）に記録する。手元で再現した失敗は原因を直すまで統合しない（`team.md` の「不安定なテストと CI の失敗」）。

## 6. 差し替え（モック・スタブ）の方針

- **純粋な関数は差し替えない**: `isAdminForbidden`・`forbiddenHeadingKey` は本物を呼ぶ。Provider・各画面のテストも本物の判定を通す。
- **トークンの更新**: ApiClient に `registerAuthHandlers` で偽物の `refresh` を登録し、呼ばれた回数を数える。答えを保留できる形（解決を外から呼ぶ約束）にして、読み直しが終わる前の状態を確実に作る（NFR9.1・NFR9.2）。テストの前後で `resetApiClient`（`resetDisplayTestState`）を呼ぶ。
- **ログイン状態**: `fakeProvider` と、`subscribe` を持つ偽物の提供元（状態を差し替えて知らせる）で、管理者・管理者でない・ログアウトの状態とその移り変わりを作る（AC2.2.2 のきっかけ、S-1）。
- **画面の API**: DSL の管理・招待の管理は今の偽物の API（`DslApi`・`InvitationApi` の形の部品）が失敗を返す。管理の入口は偽物の `fetch`（`vi.stubGlobal('fetch', …)`）で ApiClient を通す。
- **URL と移動**: `MemoryRouter`（`renderWithProviders` の `route`）と、ブラウザの戻るは `useNavigate()(-1)` を呼ぶテスト用の部品で作る。今の URL は `LocationProbe`（`data-testid="location"`）で確かめる。
- **時計**: Provider に時刻による判定は無い。決まった間隔の処理が無いことは Vitest の偽の時計（`vi.useFakeTimers()`）を進めて確かめ、確かめた後に `vi.useRealTimers()` で戻す。実時刻と `sleep` に頼らない。描画の後に反映される値は `waitFor` で待つ。テストの時間の上限は原因を確かめずに延ばさない。
- **コンソール**: `vi.spyOn(console, 'log' | 'info' | 'warn' | 'error' | 'debug')` で呼ばれないことを確かめ、テストの後に戻す。
- **E2E**: 管理の API では `GET /api/admin/check` の1本だけを 403・`ACCESS_DENIED` の見本（`application/problem+json`、`detail` に目印の固定の文字）に差し替える。ログインと復元の応答は `routeLoginPreferences` の1つの差し替えで、組のテーマ・文字の大きさと2語の氏名を当てる（本物の応答の `user` の項目だけを書き換える）。管理の API への GET 以外の要求は送らない。

## 7. テストのデータ

- 画面のテストのメールアドレスは予約のドメイン（`example.com` など）だけにし、実在しそうな氏名・宛先を置かない（リポジトリは公開、`team.md` の Testing Posture）。氏名は既存のテストと同じく架空の値を使う。
- `detail` の目印は、個人に関する値でも秘密でもない固定の文字（例 `admin-forbidden-detail-marker`）とする。
- 130 の2語の氏名は、空白で区切った ASCII の架空の2語（例 `Probe Sample`。頭文字が2文字になる値で、実在しそうな氏名にしない）を 130 の中の定数に置き、値を `test.step` の題・注記・添付・標準出力に出さない。注記に残すのは組の名前・違反の件数と規則の名前・はみ出しの有無・Avatar の文字の数・ログインと復元の書き換えの回数・差し替えた 403 を返した回数・除いたコンソールの表示の件数だけ。
- 130 はアクセストークンをテストのコードで取り出さず、初期管理者の状態（印・氏名・設定）を変えない。初期管理者のメールアドレスとパスワードは既存の `support/adminLogin.ts` のとおりプロセスの環境変数から読む。
- U4 は内部DB・Mailpit にデータを書かない（130 も書かない。`e2eTest` の全体では 060・080・090 が既存どおり書く）。
