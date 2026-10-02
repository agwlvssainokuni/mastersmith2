# Unit Test Instructions — U5 利用者の管理の画面（u5-user-admin-ui）

U5 のテストの道具・実行のしかた・カバレッジの目標・性質ベースのテストの種の扱い・差し替えの方針・テストのデータの扱いを示す。テストの量は Standard（部品ごとに 5〜8 件の単体テストと、主な境界のテスト）。手順の番号は `code-generation-plan.md` の Step を指す。パスはリポジトリのルートからの相対パスで、画面のファイルは `frontend/src/` の下を `src/...`、`frontend/src/features/useradmin/` の下を `useradmin/...` と書く。U5 は画面だけの単位で、バックエンドのテストを足さず、変えない。

## 1. 道具と設定

| 用途 | 道具 | 設定の場所 |
|---|---|---|
| 画面のテストの実行 | Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe（既存） | `frontend/vitest.config.ts`（テストは `src/**/*.test.{ts,tsx}`、`e2e/` は対象外）、`frontend/vitest.setup.ts`。どちらも変えない |
| 画面の性質ベースのテスト | fast-check（既存） | 既定の回数（100 回）。失敗の報告に `seed` と `path` が出る |
| 骨組みの中に描くテストの補助 | 既存の `src/app/testing/renderWithProviders.tsx`（変えない）と、新しい `useradmin/testing/renderUserAdmin.tsx`（偽物の API・ログイン状態の提供元・言語・表示の設定・ShellLayout の中に描く選択を渡す）・`useradmin/testing/fixtures.ts` | — |
| 実際のブラウザの検査と流れ（E2E） | Playwright（既存）＋ axe-core（既存の devDependencies）。新しい手伝いは `frontend/e2e/support/` の6ファイル（計画 4.4）。既存の `support/` のファイルは変えない | `frontend/playwright.config.ts`（Step 15 で reporter から html を外し、trace の既定を `off` にする。`workers: 1`、json の報告 `test-results/e2e-results.json`、`playwright-secret-check-reporter.ts`） |
| 型・書式・リンタ | `tsc --noEmit -p tsconfig.json`（`include` に `e2e` がある）・Prettier・oxlint・ESLint・Stylelint（既存） | 設定はどれも変えない |
| カバレッジ | `@vitest/coverage-v8`（既存） | `frontend/vitest.config.ts` の `thresholds`（行 80%・分岐 70%、全体の合計）。報告は `frontend/coverage/`（`coverage-summary.json` を含む） |
| 初回の JavaScript の大きさ | 既存の `frontend/scripts/check-bundle-size.mjs`（`./gradlew frontendBundleSize`、`verify` の段 9） | 目安 gzip 500KB（超えたら警告だけ） |

新しいテストの設定のファイルと新しいテストの依存は足さない（NFR9.2）。テストの説明文（`describe`・`it`・`test`・`test.step` の題）は英語で書く。テストのファイルは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、新しいファイルの先頭に `/* ... */` の Apache License 2.0 のヘッダー（2026、agwlvssainokuni）を置く。

## 2. この単位のテストの実行のしかた

リポジトリのルートで実行する。どのコマンドも U5 で作る・移すテストと、U5 の変更の影響を受けるテストのファイルかフォルダーを名指しする（プロジェクト全体のコマンドは、Step 3 と Step 22 の関門の `verify` と `e2eTest` だけで使う）。画面のテストは、`frontend/package.json` の `test` と同じ `NODE_OPTIONS=--no-experimental-webstorage` を付ける。範囲を絞って流すときは `--coverage` を付けない（`thresholds` は `src/**` 全体に当たるため、範囲を絞ると下限で失敗する。カバレッジは Step 22 の `verify` で判定する）。

### 2.1 最初のテストより前の確かめ（Step 2）

作業ブランチ `feature/260930-user-admin-b5` の上で、変更の前に、U5 が移す・影響を受ける既存のテストが通り、道具（Vitest・fast-check・vitest-axe・Playwright）が動くことを確かめる:

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/features/preferences \
  src/app/layout/ShellLayout.test.tsx \
  src/app/registry \
  src/features/invitation/InvitationAdminPage.test.tsx \
  src/shared/paging/paging.test.ts)
(cd frontend && npx playwright test --list e2e/130-admin-forbidden-accessibility.e2e.ts)
```

- 1つ目はプリファレンス（`fieldErrors` を移す先）・ShellLayout のユーザーメニュー（`Dropdown` を使う）・登録の検査・招待の一覧（`Table`・`Dropdown`・`Modal` と `UiPaging` を使う）の今のテストが通ることを確かめる。件数を記録する。
- 2つ目は Playwright が E2E のファイルを読めて一覧（130 の 20 件）を出せることだけを確かめる。`--list` はアプリ（WAR）を起動しない。
- まだ作っていない新しいテスト（`src/shared/api-client/fieldErrors.test.ts`・`useradmin/**/*.test.*`・`frontend/e2e/110-*`・`120-*`）を名指しすると、Vitest は「テストのファイルが無い」、Playwright は「テストが見つからない」で失敗する。これは想定どおりで、それを作った Step からコマンドが通る。

### 2.2 固定先を上げた後の既存の画面（Step 4）

```bash
(cd frontend && npm ci)
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/app/layout \
  src/features/invitation \
  src/features/dsl \
  src/features/preferences)
```

`Dropdown` を使う既存の画面（ShellLayout のユーザーメニュー・招待の一覧）と、`Table`・`Modal` を使う画面が、固定先を上げた版で通ることを確かめる。E2E は計画の Step 4 のとおり `./gradlew e2eTest` で流す。

### 2.3 共通への移し（Step 6）

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/shared/api-client/fieldErrors.test.ts \
  src/features/preferences)
```

### 2.4 純粋な関数と文言（Step 8）

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/features/useradmin/rowActions.test.ts \
  src/features/useradmin/searchInput.test.ts \
  src/features/useradmin/profileInput.test.ts \
  src/features/useradmin/lockedUntil.test.ts \
  src/features/useradmin/failureMessage.test.ts \
  src/features/useradmin/focusTarget.test.ts)
```

### 2.5 API の受け渡し（Step 10）

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/features/useradmin/api/userAdminApi.test.ts)
```

### 2.6 画面の部品（Step 12）

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/features/useradmin/UserSearchBox.test.tsx \
  src/features/useradmin/UserTable.test.tsx \
  src/features/useradmin/UserRowActions.test.tsx \
  src/features/useradmin/ConfirmActionDialog.test.tsx \
  src/features/useradmin/EditProfileDialog.test.tsx)
```

### 2.7 画面の組み立て（Step 14）

U5 のすべての画面のテストと、登録が増えたことで影響を受けうる骨組みのテスト（登録の読み込みと検査・振り分け・ShellLayout・S6）と、既存の管理の画面を流す:

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/features/useradmin \
  src/app \
  src/features/admin \
  src/features/invitation \
  src/features/dsl)
```

### 2.8 画面の静的検査と E2E のファイルの読み込み（Step 16・19）

```bash
(cd frontend && npm run typecheck)
(cd frontend && npx prettier --check \
  src/features/useradmin src/shared/api-client src/features/preferences \
  e2e/110-user-admin-flow.e2e.ts e2e/120-user-admin-accessibility.e2e.ts \
  e2e/support/traceMode.ts e2e/support/secretValues.ts e2e/support/userAdminRun.ts \
  e2e/support/userAdminFixtures.ts e2e/support/adminApiRoute.ts e2e/support/userAdminDiagnostics.ts \
  playwright.config.ts playwright-secret-check-reporter.ts .npmrc)
(cd frontend && npx oxlint src/features/useradmin src/shared/api-client src/features/preferences e2e \
  playwright.config.ts playwright-secret-check-reporter.ts \
  && npx eslint src/features/useradmin src/shared/api-client src/features/preferences e2e \
  playwright.config.ts playwright-secret-check-reporter.ts)
(cd frontend && npx stylelint "src/features/useradmin/**/*.css")
(cd frontend && node scripts/check-license-header.mjs)
(cd frontend && npx playwright test --list \
  e2e/110-user-admin-flow.e2e.ts e2e/120-user-admin-accessibility.e2e.ts)
(cd frontend && E2E_TRACE=bogus npx playwright test --list e2e/110-user-admin-flow.e2e.ts)  # 誤りで止まることを確かめる
```

- 型の検査は範囲を絞れない（プロジェクト全体の型を一度に確かめる道具のため）。E2E の見本に付けた画面の型（`AdminUserPage`・`AdminUser`）もここで確かめる。
- `--list` で 110 が 1 件、120 が 21 件（20 組と測り1件）であることを確かめる。全体の一覧は `(cd frontend && npx playwright test --list)` で 13 ファイル・152 件。
- `.npmrc` が Prettier の対象の外なら、その旨を記録して名指しから外す（検査の範囲は変えない）。README（リポジトリのルート）は Prettier と Spotless の対象の外で、読み直しと計画の Step 20 の確かめで確かめる。

### 2.9 報告の部品のわざと値を入れた確かめ（Step 16）

報告の部品の確かめは、作業フォルダのファイルを書き換えずに、リポジトリの外の一時の場所で行う（計画 8節の D-9）:

```bash
umask 077
WORK="$HOME/tmp-u5-reporter-check"   # 権限 700。macOS の一時ディレクトリは使わない
mkdir -p "$WORK"
# 使い捨ての台本 $WORK/check.mts が、架空の値で (1)〜(8) の報告のファイルを $WORK の下に作り、
# frontend/playwright-secret-check-reporter.ts を読み込んで、探す先・値のファイル・前の html の報告の場所を
# $WORK の下に向けた設定で onEnd を呼び、結果（失敗・警告・通過）を種類ごとに出す（値は表示しない）。
(cd frontend && node --experimental-strip-types "$WORK/check.mts")
rm -rf "$WORK"
git status --short && git diff --stat   # 作業フォルダに確かめの変更・一時のファイルが無いこと
```

- 台本の値は架空のもの（`u7-perf-<16進>@example.com`、`e2e-u7-pw-<24 文字の16進>`、氏名「計測 花子」）だけを使う。台本と作った報告は確かめの後に消す。
- 確かめる種類は計画 Step 16 の (1)〜(8) と、値の無い報告で通ること、値のファイルが探し終えた後に無いこと、`recordSecretValues` が作ったファイルの権限が 600 であること。
- 実行のしかた（`node` で TypeScript を読む形）が今の Node（`engines` は 24）で動かないときは、台本を JavaScript で書く。どちらの形を使ったかを記録する。

### 2.10 E2E 110・120 だけの実行と差し替えの口の確かめ（Step 18）

```bash
docker compose ps
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8025/api/v1/info
# 200 でなければ起動する（.env は開かない）
docker compose --profile mail up -d mailpit
caffeinate -i sh -c './gradlew :backend:bootWar && (cd frontend && npx playwright test \
  e2e/110-user-admin-flow.e2e.ts e2e/120-user-admin-accessibility.e2e.ts)'
```

- 差し替えの口の3つの確かめは、追跡しない一時のファイル `frontend/e2e/990-route-guard-check.e2e.ts` を作り、`(cd frontend && npx playwright test e2e/990-route-guard-check.e2e.ts)` で流した後に消す。消した後に `git status --short` と `git diff --stat` で残っていないことを確かめる。
- 流した後は `frontend/test-results/e2e-results.json` の `stats` とテストごとの題・状態・注記だけを読んで記録し、`frontend/test-results/` を消す。
- 合否の確定は、Step 22 の `./gradlew e2eTest` の全体で行う。

### 2.11 E2E の全体（Step 3・4・22）

```bash
docker compose ps
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8025/api/v1/info
docker compose --profile mail up -d mailpit   # 200 でなければ
caffeinate -i ./gradlew e2eTest
```

- Step 3・4（設定を直す前）: 期待は 11 ファイル・130 件（010〜100 と 130）。json の `stats` とファイルごとの結果だけを記録してから `frontend/playwright-report/`・`frontend/test-results/` を中を開かずに消し、消したことと共有していないことを記録する（`gate-decisions.md` の U5 R-02）。
- Step 22（設定を直した後）: 期待は 13 ファイル・152 件（010〜100・130・110・120）がすべて成功し、失敗・飛ばした・不安定が 0。110 に `skip-reason` の注記が無いこと、120 の組と状態ごとの結果、添付 `user-admin-screen-ms`（base64 を復号した数だけ）、報告の部品の結果を記録する。`frontend/playwright-report/` は作られない。記録の後に `frontend/test-results/` を消す。
- 手元で失敗を調べるときだけ `E2E_TRACE=retain-on-failure` を付けてよい。その実行は合否に使わず、見た後に `frontend/test-results/` を消す（SD 3.7）。

### 2.12 U5 のテストをまとめて流す

U5 で作る・移す画面のテストと、影響を受けるテストを、まとめて流す（Build and Test がこの単位のテストを流すときもこれを使う）:

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/features/useradmin \
  src/shared/api-client/fieldErrors.test.ts \
  src/features/preferences)
```

- 実測の件数は、このコマンドの出力の数字だけを報告する。カバレッジは Step 22 の `verify` の画面の段の実測とする。
- U5 の画面のテストはコンテナを使わないため、コンテナの実行環境（colima）が無くても動き、飛ばされない。統合の前の `./gradlew verify`（Step 3・22）は対象DB のテストを含むため、colima を動かし、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡し、`caffeinate -i` で包んで実行する（この単位だけのコマンドではない）:

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
```

## 3. テストの一覧（Standard の量）

| 部品 | テストのファイル | 新しい・移す | 件数の見込み | 確かめる内容（要点） |
|---|---|---|---|---|
| `readFieldErrors` | `src/shared/api-client/fieldErrors.test.ts` | 移す | 今の件数のまま | 読み込みの1行だけを直し、中身を変えずに通る。プリファレンスのテストも変えずに通る |
| `rowActions` | `useradmin/rowActions.test.ts` | 新しい | 6（全組 16 通りの表 1・性質 4・並び 1） | W4 の表の16通り（`admin`・`suspended`・`resettable`・`self`）。性質: 自分の行の「印を外す」「止める」は出るとき必ず押せない形、停止中は印の項目と suspend が出ず resume が出る、`resettable` が偽なら resetFailures が出ず真なら押せる、editProfile は必ず押せる・grantAdmin と revokeAdmin は同時に出ない・suspend と resume はちょうど1つ |
| `checkSearchInput` | `useradmin/searchInput.test.ts` | 新しい | 8（例 6・性質 2） | 254 ちょうどは通り 255 は `tooLong`、前後の半角・全角の空白を除く、空白だけは検索なし、サロゲートペアを1と数える、除いた後の値を返す。性質: 前後に空白を足しても結果が同じ、返す値は 254 コードポイント以下 |
| `profileInput` | `useradmin/profileInput.test.ts` | 新しい | 6 | 画面の確かめの3つの理由（required・tooLong・invalidCharacter）と鍵、サーバーの `REQUIRED`・`TOO_LONG`・`INVALID_CHARACTER`・`INVALID_VALUE` の寄せ方、言語の誤り、知らない理由は項目に結び付かない |
| `formatLockedUntil` | `useradmin/lockedUntil.test.ts` | 新しい | 6 | 今日なら時刻と時差の略号だけ、明日・前日は日付つき、時間帯の違い（Asia/Tokyo・UTC）で今日の判定が変わる、ja・en。今の時刻と時間帯は引数で固定 |
| `failureMessageKey` | `useradmin/failureMessage.test.ts` | 新しい | 8 | 6つの code の鍵、`VALIDATION_FAILED` は場面ごと（一覧は検索の誤り・保存は項目へ）、知らない code・code の無い応答（code の無い 400 を含む、計画 9節の Q-C）・通信の失敗は場面ごとの一般の鍵。`detail`・`title` を読まない |
| `focusAfterReload` | `useradmin/focusTarget.test.ts` | 新しい | 5 | 同じ `userId` の行があればその行の「操作」、無ければ一覧の見出し、空の一覧、行き先が無い |
| `userAdminApi` | `useradmin/api/userAdminApi.test.ts` | 新しい | 10 | `GET /api/admin/users?page=n`、`q` は空なら付けず特殊文字（`&`・`#`・`%`・空白・全角）を符号化、5つの操作の道と本文なし、`userId` の符号化、`PUT …/profile` の本文は2項目だけ、余計な項目（`passwordHash`・`failedAttempts`・`refreshToken`）を捨てる、`lockedUntil` が `null`・無い・文字列、形の違う本文（項目の欠け・型の違い）は通信の失敗、失敗の応答は `ApiError` のまま |
| 登録と文言 | `useradmin/registration.test.tsx` | 新しい | 5 | 画面（`/admin/users`・`SHELL`・`ADMIN`・`lazy`）とサイドバー（`order: 230`・`visibleWhen: 'ADMIN'`・鍵）の値、文言の鍵が ja・en で一致し空でない、骨組みの登録の検査（`validateRegistrations`）を通る |
| `UserSearchBox` | `useradmin/UserSearchBox.test.tsx` | 新しい | 7 | Enter と [検索]、上限の誤り（`aria-invalid` と説明の `aria-describedby`）、[検索を消す]、busy の間は `aria-disabled` で押しても Enter でも `onSearch`・`onClear` が呼ばれずフォーカスが残る、vitest-axe（既定・誤り） |
| `UserTable` | `useradmin/UserTable.test.tsx` | 新しい | 8 | 列と印の文字、「—」の読み上げ「なし」、自分の行の「あなた」、長いメールアドレス・2語の氏名・記号の氏名が文字として描かれる、日時の書式、ページの状態の文言（ja・en、en に `Table` の日本語の既定が無い）、busy の間に [前へ]・[次へ] を押しても `onPageChange` が呼ばれない、余計な項目の値が出ない、vitest-axe |
| `UserRowActions` | `useradmin/UserRowActions.test.tsx` | 新しい | 8 | 読み上げの名前（busy なら「（処理中）」）、押せない項目の `aria-disabled` と理由の `aria-describedby`、押せない項目をクリック・Enter・Space で押しても `onSelect` が呼ばれずメニューは開いたまま（Step 4 で確かめた形）、矢印で押せない項目にも移る、busy の間はクリック・Enter・Space でメニューが開かずフォーカスが「操作」に残る、Escape で「操作」へ戻る、vitest-axe（自分の行のメニューを開いた状態・busy） |
| `ConfirmActionDialog` | `useradmin/ConfirmActionDialog.test.tsx` | 新しい | 8 | 5種類の見出し・効き目・ボタン（ja・en）、本文の先頭が対象と効き目の文（`aria-describedby` の先）、はじめのフォーカスが「やめる」、背景のクリックで閉じない、「やめる」・Escape で `onConfirm` が呼ばれない、送信中は閉じず「処理中」、`slow` で「時間がかかっています」、閉じるの名前が画面の言語、vitest-axe（5種類） |
| `EditProfileDialog` | `useradmin/EditProfileDialog.test.tsx` | 新しい | 8 | 今の値で開く、はじめのフォーカスが氏名、画面の確かめの誤りと入力が消えない・最初の誤りの欄へフォーカス、サーバーの 400 の項目の誤り（氏名・言語）、404 の表示と「保存」を押せない、一般の失敗で入力が残る、送信中は閉じない、vitest-axe（今の値・誤り） |
| `useUserAdmin`・`UserAdminPage` | `useradmin/UserAdminPage.test.tsx` | 新しい | 40 ほど | 下の表 |

`UserAdminPage.test.tsx` で確かめること（FC 8節、PD 2.4・3.3、SD 6節、計画 8節の D-5〜D-7）:

| まとまり | 確かめること |
|---|---|
| 状態 | loading・reloading・populated・empty-search・empty-page・load-error の6つ。初めの読み込みの 400 は load-error。code の無い 400（Tomcat の HTML の 400 の形）は load-error（計画 9節の Q-C） |
| 読み方 | 重なった2つの読み直しで古い答えが画面に出ない、画面を外した後の答えで状態が変わらない（React の警告が出ない）、空の応答が2回続くと一覧の API の呼び出しが合わせて2回で止まり empty-page、`correctedPage` で最後のページへ移る、偽の時計を進めても一覧の API が呼ばれない |
| ページと検索 | ページ送りで検索の文字を保つ、検索で1ページ目、3ページ目で検索が 400 の後の [次へ] が4ページ目を読む・検索の欄の値が残る、ページ送りの後のフォーカス（押せなくなったら一覧の見出し・押せるままなら押したボタン） |
| 二重の送信 | 読み直しの間のページ送り・[検索]・行の「操作」で要求が増えない、実行・保存の二度押しで要求が1回だけ、参照の戻り（一覧と送信のそれぞれで 409・400・通信の失敗・API の関数の想定外の例外の後に次の操作が通る、送信の途中で外して開き直すと最初の読み直しと操作が通る） |
| 操作の結果 | 5つの操作の成功（Toast・読み直し・同じ行の「操作」へのフォーカス、見えなくなったら一覧の見出し）、6つの code の知らせと読み直し、知らない code・code の無い 400・5xx・通信の失敗の一般の文言、`detail`・`title` の目印が画面に出ない、[×] で知らせが消え読み直しでは消えない |
| 403 と 401 | 一覧・操作・保存の 403 `ACCESS_DENIED` で `useAdminForbidden` が要求のパスとともに呼ばれ画面が何も出さない、code の無い 403 は一般の失敗、401 で何も出さず S3・S4 が閉じる。U4 のレビューの R-04: 本物の `AdminForbiddenProvider` と ShellLayout の中で、古い読み直しの 403 が後から届いても S6 にならず、最新の読み直しの 403 で S6 が出る |
| 待ちの表示 | 偽の時計で 4999 ミリ秒では「時間がかかっています」が出ず 5000 ミリ秒で出て、応答で消える |
| 自分の氏名と言語 | 自分の行の保存で `useApplyOwnProfile` が送った値で呼ばれ、順は反映 → Toast → 読み直し、ほかの行では呼ばれない、自分の言語を en に直した直後の Toast が英語（`waitFor`、計画 8節の D-5） |
| 画面の外に出さない | 一覧・検索・ページ送り・5つの操作・保存の流れ（成功・業務の失敗・通信の失敗）の後に、localStorage・sessionStorage のどの鍵の値にもテストのメールアドレス・氏名・検索の文字が無い、`location` と `history.length` が変わらない、`console` の5つの関数が呼ばれない |
| 言語とアクセシビリティ | en の画面で文言が英語、vitest-axe（表示・読み込み中・読み込みの失敗・0 件・失敗の知らせ）、表示の設定の6つの組（light・dark × sm・md・lg）を表の1件で vitest-axe にかける（計画 8節の D-7） |

E2E（`./gradlew e2eTest` の中、`verify` と CI の外）:

| ファイル | 件数 | 確かめる内容（要点） |
|---|---|---|
| `frontend/e2e/110-user-admin-flow.e2e.ts` | 1 | 計画 Step 17 の表の順1〜10（検索 → ロック → 失敗回数を戻す → 409・400 の照らし合わせ → 止める → 入れない → 停止を解く → 入れる → 画面と本物のサーバーを通した 403）。U だけを変え、初期管理者は変えない。値は `recordSecretValues` で値のファイルに書き、`test.step` の題・注記・添付・locator の名前に入れない |
| `frontend/e2e/120-user-admin-accessibility.e2e.ts` | 21（20 組と測り1） | 組ごとに 11 状態の axe 違反 0・`REQUIRED_RULES` が走った・はみ出し無し、引いた後の問題 0・CSP の違反 0、口が受けた件数 1 以上と GET 以外の打ち切りの記録 0。測りは既定の1組で一覧を開くまでと次のページまでを 5 回ずつ記録（成否にしない） |

- 部品ごとに 5〜8 件を目安とし、`UserAdminPage.test.tsx` はフックと画面の決まりを1か所で確かめるため多くなる（計画 7節）。
- どのテストも、成功の場合と、少なくとも2つの失敗・境目の場合を含める（`phases/construction.md` の Testing Standards）。
- E2E の流れの本数に数えるのは 110 だけ。120 は流れではないため数えない（`project.md` の学び）。既存の 010〜100・130 は変えない。

## 4. カバレッジの目標

- 画面の全体: 行 80% 以上・分岐 70% 以上（`frontend/vitest.config.ts` の `thresholds`、全体の合計）。基準は B2 の関門の実測 行 97.61%（2414/2473）・分岐 93.01%（1505/1618）、画面のテスト 95 ファイル・801 件（B3・B4 は画面を変えていない）。U5 の後も下限を満たし、基準から大きく下げないことを目標とし、Step 22 の `verify` の実測（`frontend/coverage/coverage-summary.json` の全体と、U5 で作った・移した・変えたファイルの行・分岐）を記録する。
- U5 で作るファイル（`useradmin/` の下のすべて）は、3節のテストで行・分岐とも 90% 以上を目安にする（下限の値ではなく、記録のための目安。下回ったら理由を記録する）。
- 計測の除外を増やさない（`src/main.tsx`・型の宣言・テストのファイルのまま）。下限の値を変えない（Testing Contract、`team.md` の Testing Posture、NFR9.5）。`frontend/e2e/`・`playwright.config.ts`・報告の部品は既存どおり計測の対象外。
- バックエンドは変えないため、`packagesJudgedByTotal` とパッケージごとの下限に U5 の作業は無い。関門の `verify` で全体の値を記録する。
- 範囲を絞ったテストの実行では `--coverage` を付けない（2節の冒頭）。

## 5. 性質ベースのテストと乱数の種

- **fast-check（`rowActions.test.ts`・`searchInput.test.ts`）**: 既定の 100 回。失敗すると、報告に `seed` と `path` が出る。再現は、その `fc.assert(property)` を `fc.assert(property, { seed: <seed>, path: '<path>' })` に一時的に替えて、そのファイルを名指しして流す。原因を直した後に元に戻す（指定を残してコミットしない。戻した後に `git diff` で確かめる）。再現の仕方は各テストのファイルの先頭の説明文に書く。
- CI で失敗したときも、Vitest の出力が CI の記録に残るため、同じ手順で手元で再現する。
- 失敗の種と再現の結果は `generation-notes.md`（Build and Test では `test-results.md`）に記録する。手元で再現した失敗は原因を直すまで統合しない（`team.md` の「不安定なテストと CI の失敗」）。

## 6. 差し替え（モック・スタブ）の方針

- **純粋な関数は差し替えない**: `rowActions`・`checkSearchInput`・`profileInput`・`formatLockedUntil`・`failureMessageKey`・`focusAfterReload` は本物を呼ぶ。部品と画面のテストも本物を通す。
- **画面の API**: 画面と部品のテストは、`UserAdminApi` の形の偽物（答えを保留できる約束で、読み直しの重なり・送信中・5 秒の待ちを確実に作る）を `renderUserAdmin` で渡す。`api/userAdminApi.test.ts` だけは偽物の `fetch`（`vi.stubGlobal('fetch', …)`）で ApiClient を通す。
- **403 の扱い**: 多くのテストは `useAdminForbidden` の結果を確かめる形（呼ばれた引数と、真のときに画面が何も出さないこと）で、U4 の本物の Provider の中に描く。U4 のレビューの R-04 のテストは、本物の `AdminForbiddenProvider` と ShellLayout の中に描き、S6（`admin-forbidden-view`）が出るかを確かめる。トークンの更新は ApiClient に `registerAuthHandlers` で偽物の `refresh` を登録する。
- **自分の氏名と言語**: 本物の `DisplaySettingsProvider` の中で `useApplyOwnProfile` を通し、上の帯の氏名と言語の変化を見る。呼ばれた値の確かめが要るときは、ログイン状態の提供元の偽物と組み合わせる。
- **ログイン状態**: `fakeProvider` 形の偽物で、管理者・自分の行の利用者・テーマと文字の大きさを渡す。
- **make-you-chic-ui の部品は差し替えない**: `Table`・`Dropdown`・`Modal`・`Button`・`Toast` は固定先を上げた本物を使う。
- **時計**: 5 秒の表示と自動の読み直しが無いことは Vitest の偽の時計（`vi.useFakeTimers()`）を進めて確かめ、確かめた後に `vi.useRealTimers()` で戻す。解除の予定の時刻の書式は、今の時刻と時間帯を引数で渡す。実時刻と `sleep` に頼らない。描画の後に反映される値は `waitFor` で待つ。テストの時間の上限は原因を確かめずに延ばさない。
- **コンソール**: `vi.spyOn(console, 'log' | 'info' | 'warn' | 'error' | 'debug')` で呼ばれないことを確かめ、テストの後に戻す。
- **E2E 110**: 何も差し替えない（本物の WAR・Mailpit・一時の内部DB）。例外は計画 9節の Q-B の決定 A（計画 8節の D-14）による、順9 の U のページのログインの応答の `user.admin` の書き換え1つだけ（サーバーの状態を変えない）。
- **E2E 120**: `/api/admin` ちょうどか `/api/admin/` の下の要求を、page ごとの1つの差し替えの口（`support/adminApiRoute.ts`）で受ける。検査のモードでは一覧の GET・操作の POST・保存の PUT を見本で返し、見本の無い GET は決まった失敗で返し、GET 以外は打ち切って記録する。測りのモードの前半だけ一覧の GET を本物へ通す。ログインと表示の設定の組は `routeLoginPreferences`（変えない）で当てる。`request` の口で `/api/admin/` の下へ書き換えを送らない。

## 7. テストのデータ

- 画面のテストと E2E の見本のメールアドレスは予約のドメイン（`example.com`）だけにし、実在しそうな氏名・宛先を置かない（リポジトリは公開、`team.md` の Testing Posture）。見本の氏名は架空の2語（例「山田 太郎」）を含め、記号（`<`・`>`・`&`・`"`・`'`）を含む氏名の行も置く。長いメールアドレスは折り返しの確かめに使う。
- E2E の見本（`support/userAdminFixtures.ts`）は、110 が作る値の形（`u7-perf-` で始まる宛先、`e2e-u7-pw-` で始まるパスワード、氏名「計測 花子」）を使わない（報告の部品の値の形での探し方と重ならないため）。
- 110 が作る利用者 U の値（宛先・パスワード・氏名）と `runTag` は、テストの変数と値のファイル（`test-results/e2e-secret-values.json`、権限 600、報告の部品が探し終えたら消す）だけに持つ。`test.step` の題・注記・添付・標準出力・失敗の知らせ・locator の名前に入れない。読み上げの名前に値が入っていることは、比べた真偽だけを `expect` に渡す。
- 110 は初期管理者の印・停止・ロックを変えず、U を消さない（利用者を消す仕組みは無く、宛先は実行ごとに重ならない）。U への招待のメールは Mailpit に残る。120 は内部DB・Mailpit に書かない。
- 初期管理者のメールアドレスとパスワードは既存の `support/adminLogin.ts`・`playwright.config.ts` のとおりプロセスの環境変数から読み、`webServer.env` に置かない。
- `detail` の目印は、個人に関する値でも秘密でもない固定の文字（例 `user-admin-detail-marker`）とする。
