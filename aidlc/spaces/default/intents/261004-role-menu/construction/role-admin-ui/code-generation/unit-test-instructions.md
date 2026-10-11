# 単体テストの手順（Unit Test Instructions）— U6 役割・権限の管理の画面（role-admin-ui）

計画: `code-generation-plan.md`（同じディレクトリ）。Test Strategy は Standard（部品ごとに 5〜8 件と、要所の結合）、方法は test-after（層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて流す）。

ここに書くコマンドは、どれもこの単位のテストだけを名指しで流す（ファイルかディレクトリの道）。素の `npm test`・`npm run test:coverage`・`./gradlew verify`・`./gradlew e2eTest` はこの単位の確かめには使わない（統合の前の関門と、変更の前後の基準・全体の検査は、計画の Step 3・5・22・29 で別に流す）。

## 1. テストの道具と設定（既存のものをそのまま使う）

| 対象 | 道具 | 設定 | この単位で変えること |
|---|---|---|---|
| 画面 | Vitest 5.0.2・Testing Library（jsdom）・user-event 14.6.7・vitest-axe 0.1.0・fast-check 4.10.2・`@vitest/coverage-v8` | `frontend/vitest.config.ts`（`include: ['src/**/*.test.{ts,tsx}']`、`thresholds` 行 80%・分岐 70%）・`vitest.setup.ts` | 変えない |
| ルーター | react-router 8.4.0 の `createMemoryRouter`・`RouterProvider`（`useBlocker` を使う部品のテスト） | 既存の依存 | 変えない |
| E2E（参考。単体テストではない） | Playwright・@axe-core/playwright（`./gradlew e2eTest`、`verify` と CI の外） | `frontend/playwright.config.ts` | 変えない |

新しい依存は足さない（`frontend/package.json`・`frontend/package-lock.json` を変えない。NFR6.5）。バックエンドのテストはこの単位に無い。

画面のテストのコマンドは、`frontend/` で `NODE_OPTIONS=--no-experimental-webstorage` を付けて流す（`package.json` の `test` と同じ）。

## 2. テストを始める前に動くことを確かめるコマンド（計画の Step 2）

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/features/useradmin \
  src/shared/tree \
  src/shared/api-client/apiClient.download.test.ts
npm run typecheck
```

どれも失敗 0 であることを記録する。

## 3. この単位のテストを流すコマンド

### 3.1 入口の差し替え（Step 5）

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/app/routing/DataRouterBlocker.test.tsx
```

### 3.2 共有の部品（Step 7）

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/shared/api-client/apiClient.download.test.ts \
  src/shared/validation/validateAdminName.test.ts \
  src/shared/validation/searchText.test.ts \
  src/features/useradmin \
  src/features/dsl/api
```

`searchText.test.ts` は useradmin の `searchInput.test.ts` を中身を変えずに移したもの（計画 13節 Q3: A）。`src/features/useradmin` は検索の文字の確かめの import を直した影響の確かめ、`src/features/dsl/api` は `ApiDownload` を使う既存のダウンロードの確かめ。

### 3.3 純粋な関数と API の関数（Step 9・11）

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/features/roleadmin/permissionDraft.test.ts \
  src/features/roleadmin/permissionLabels.test.ts \
  src/features/roleadmin/removalImpact.test.ts \
  src/features/roleadmin/failureMessage.test.ts \
  src/features/groupadmin/removedRoles.test.ts \
  src/features/groupadmin/failureMessage.test.ts \
  src/features/roletransfer/changePaging.test.ts \
  src/features/roletransfer/reasonMessage.test.ts \
  src/features/roletransfer/failureMessage.test.ts \
  src/features/roletransfer/safeFileName.test.ts \
  src/features/roletransfer/transferLimits.test.ts

NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/features/roleadmin/api \
  src/features/groupadmin/api \
  src/features/roletransfer/api \
  src/features/useradmin/api
```

### 3.4 画面（機能ごと。Step 13・15・17・19・21）

```bash
cd frontend
# roleadmin（S3・S4・S5、登録、403 の表、英語の表示）
NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/roleadmin
# groupadmin（S6）
NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/groupadmin
# roletransfer（S7）
NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/roletransfer
# useradmin（S9 と既存のテストの直し）
NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/useradmin
```

### 3.5 この単位の画面のテストをまとめて流すとき（カバレッジつき）

```bash
cd frontend
caffeinate -i env NODE_OPTIONS=--no-experimental-webstorage npx vitest run --coverage \
  --coverage.include='src/features/roleadmin/**' \
  --coverage.include='src/features/groupadmin/**' \
  --coverage.include='src/features/roletransfer/**' \
  --coverage.include='src/features/useradmin/**' \
  --coverage.include='src/shared/validation/**' \
  --coverage.include='src/shared/api-client/apiClient.ts' \
  --coverage.thresholds.lines=80 --coverage.thresholds.branches=70 \
  src/app/routing/DataRouterBlocker.test.tsx \
  src/shared/api-client/apiClient.download.test.ts \
  src/shared/validation \
  src/features/roleadmin \
  src/features/groupadmin \
  src/features/roletransfer \
  src/features/useradmin
```

この単位のファイルだけの値を見るための実行で、関門の判定は全体のカバレッジ（Step 22・29 の `npm run test:coverage`・`verify` の段 7。`vitest.config.ts` の `thresholds`）で行う。計測の除外は足さない。

### 3.6 静的検査（この単位のファイルを含む全体。Step 22）

```bash
cd frontend
npm run format:check
npm run lint
npm run lint:css
npm run license:check
npm run typecheck
```

`lint` は機能どうしの import の禁止（`eslint.config.js` が `src/features/` のディレクトリの一覧から作る）を新しい3つの機能にも当てる。

### 3.7 E2E（参考。計画の Step 24・25。全体は Step 29）

```bash
# Mailpit を起動しておく（e2eTest と 150 の招待のメールに要る）
docker compose --profile mail up -d mailpit
./gradlew :backend:bootWar
cd frontend
# U6 の画面の検査（本数に数えない）と、口の変更が既存を変えないことの確かめ
caffeinate -i npx playwright test \
  e2e/140-role-admin-accessibility.e2e.ts \
  e2e/120-user-admin-accessibility.e2e.ts \
  e2e/130-admin-forbidden-accessibility.e2e.ts \
  e2e/135-dsl-admin-accessibility.e2e.ts
# F の流れ（150）
caffeinate -i npx playwright test e2e/150-role-permission-flow.e2e.ts
```

E2E を流している間は、同じ作業フォルダでビルドを流さない。終わったら `frontend/playwright-report`・`frontend/test-results` を消す（git の対象外の生成物を残さない、`project.md` の学び）。

## 4. テストの中身（Standard、部品ごと）

テストの説明文（`describe`・`it`）は英語で書く。テストデータの文字は日本語でよい。

| 置き場 | テスト | 確かめること |
|---|---|---|
| 入口 | `src/app/routing/DataRouterBlocker.test.tsx` | `App` を `createMemoryRouter`＋`RouterProvider`（`path: '*'`）で描き、テスト用の登録の画面の `useBlocker` がサイドバーの項目の押下とブラウザの戻るで止まる・止めないときは移る・`App` の中の振り分け（ホーム・見つからない・管理の画面）が変わらない |
| 共有 | `apiClient.download.test.ts`（足す）・`validateAdminName.test.ts`・`searchText.test.ts`（移す） | `headers` が返り `blob`・`contentDisposition` が変わらない。名前の 64・65 コードポイント・サロゲートペア・空白だけ・全角の空白・前後を除いた長さ・Cc と U+2028・U+2029 を拒む・U+200B（Cf）を受け付ける、fast-check（前後に White_Space を足しても結果が変わらない） |
| roleadmin の純粋な関数 | `permissionDraft.test.ts`・`permissionLabels.test.ts`・`removalImpact.test.ts`・`failureMessage.test.ts` | fast-check: 写しが空なら `entries` も空、サーバーの値に戻すと消える、スキーマの範囲はスキーマの段だけ・テーブルの範囲はそのテーブルとカラムだけ、同じ対象は1つ、鍵が重ならない。直接以外の出どころがあれば無くならない・残りの最初は ID の最小。文言: 明示・スキーマとテーブルからの継承（親の表示名、無ければ物理名）・既定、補助権限は「可」「不可」と、明示のときだけ「（明示）」を添え、明示が無いときは出どころを添えない（計画 13節 Q4: A）。失敗の文言: `detail`・`title` の目印で変わらない、知らない code は汎用 |
| groupadmin の純粋な関数 | `removedRoles.test.ts`・`failureMessage.test.ts` | fast-check: グループのロールのうち外した後に無いものだけ、直接で残るロールを含まない。失敗の文言は上と同じ |
| roletransfer の純粋な関数 | `changePaging.test.ts`・`reasonMessage.test.ts`・`failureMessage.test.ts`・`safeFileName.test.ts`・`transferLimits.test.ts` | fast-check: 100 件ずつの範囲が重ならず全件を覆う。`reason` は任意の文字列で例外にならず、13 個は ja・en の文言、知らない値は汎用。ファイル名の境界（`roles.yaml`・`../x.yaml`・`a/b.yaml`・制御文字・空・`x.txt`・無い）。上限 10,485,760 |
| API の関数 | `roleadmin/api/roleAdminApi.test.ts`・`groupadmin/api/groupAdminApi.test.ts`・`roletransfer/api/roleTransferApi.test.ts`・`useradmin/api/userAdminApi.test.ts`（足す） | 送る道・問い合わせの引数・方法・本文の項目が決めたものだけ（`scope` に `columnName` を送らない、割り当ては一方だけ、確かめ・適用は `application/yaml` と指紋のヘッダー）。`/`・`?`・`#`・`%` を含む名前と ID の道。応答の写し（余分な項目を捨てる、`{items}`・配列・出どころの `type`・null を許す `line`・`column`）。欠けた・型の違う・列挙の知らない値は通信の失敗。書き出しの `Content-Disposition` と上限超えのヘッダー |
| S3 | `RoleListPage.test.tsx`・`RoleNameDialog.test.tsx`・`DeleteConfirmDialog.test.tsx`・`RoleRowActions.test.tsx`・`latestLoad.test.ts`・`useSlowNotice.test.tsx` | 一覧・空・失敗と再試行・ページ送り（読み直しの間の押下を捨てる、`correctedPage`）、名前の境界とサーバーの code ごとの入力欄の下の文言、削除の確かめ（はじめのフォーカスは「やめる」、やめると送らない、閉じたら開いたボタンへ）、`ROLE_IN_USE` の数とリンク（数が読めないとき）、BUSY で入力が残る、`detail` の目印が出ない、Dropdown が `bottom-end`、`<script>`・`<img onerror>`・`&`・`"`・`'` が文字のまま、偽の時計で 4.9 秒で出ず 5 秒で出て応答で消える待ちの案内、送信中の2回目で API が呼ばれない、答えの順を入れ替えた2つの読み込みで古い答えが描かれない、画面を外した後の答えで状態が変わらない、偽の時計を進めても API が呼ばれない。axe を1件 |
| S4 | `RoleDetailPage.test.tsx`・`PermissionPanel.test.tsx`・`PermissionTable.test.tsx`・`useUnsavedGuard.test.tsx`・`LeaveConfirmDialog.test.tsx` | D32（無い ID・正の整数でない道で他を読まない）、タブの切り替えの後もフォーカスがタブにあり読み込みが1回（`App` を通した確かめを含む）、木の `aria-expanded`・`aria-current`・キーボード、実効の値の文字、カラムに補助権限の欄が無い、Select の名前に対象、保存の可否と理由（`aria-describedby`）、`entries` に変えた対象だけ、保存の後の読み直しと Alert、D14 の code ごとの写しの扱い、noDsl と DSL の管理へのリンク、orphan の印と消す操作、未保存の確かめ（木の節・タブ・サイドバー・ブラウザの戻る、留まる・移る、留まる後のタブへのフォーカス）、`beforeunload` の張り外し。axe を1件 |
| S5 | `AssignmentPanel.test.tsx`・`CandidateDialog.test.tsx` | 出どころの文字（直接・グループ）、外すは直接のときだけ、外した後に出どころが変わる、`ROLE_NO_CHANGE`（相手がいない外しを含む）、候補の検索（Enter・20 件・済み・利用停止・足した後も開いたまま・`role="status"`・閉じたら読み直しと「利用者を足す」へのフォーカス・検索の文字の上限）、グループの候補のページ送り、D19 の確かめ（直接だけ・グループ経由で残る・残りが無い・読めない）。axe を1件 |
| roleadmin の登録・横断 | `registration.test.tsx`・`RoleAdminForbidden.test.tsx`・`RoleAdminEnglish.test.tsx` | 3つの道で管理者でないとき `decideRoute` が `ADMIN_FORBIDDEN`、未ログインはログインへ、管理者は画面、画面の部品が `lazy`、サイドバーの項目（240・ADMIN）。画面と API の組の 403 の表（読み込みと各操作の API が 403 を返すと `useAdminForbidden` が要求の道つきで1回呼ばれ、画面が独自の文言を出さない）。en で文言がすべて英語・部品の日本語の既定（Table の「前へ」など）が出ない・名前は訳さない。操作の後に `console` が呼ばれず、ブラウザの保存に書かれず、`window.location` に検索の文字が入らない |
| S6 | `GroupListPage.test.tsx`・`GroupNameDialog.test.tsx`・`GroupDetailPage.test.tsx`・`MemberTable.test.tsx`・`CandidateDialog.test.tsx`・`registration.test.tsx`・`GroupAdminForbidden.test.tsx`・`GroupAdminEnglish.test.tsx` | S3 と同じ形の一覧と名前、`GROUP_IN_USE` の文言、詳細の missing、メンバーの表と利用停止の印、割り当てたロールのリンク（`/admin/roles/{roleId}/assignments`）、メンバーの外しの Alert（無くなるロールあり・なし・読めない）、候補（メンバー済み）、登録（250）。axe を部品ごとに1件 |
| S7 | `RoleTransferPage.test.tsx`・`useRoleTransfer.test.tsx`・`TransferFilePicker.test.tsx`・`TransferCheckTable.test.tsx`・`TransferErrorList.test.tsx`・`ApplyConfirmDialog.test.tsx`・`registration.test.tsx`・`RoleTransferForbidden.test.tsx`・`RoleTransferEnglish.test.tsx` | 書き出しの間は押せない・保存の名前・上限超えのヘッダーが `true`・無い・`false`・ほかの値で案内の有無、10,485,760 は送る・10,485,761 は送らずに文言（上限の値は定数から）、確かめた本文がそのまま適用で送られる・選び直しで本文と指紋が捨てられる・画面を離れると捨てられる、結果の表の行の開閉の名前（`aria-expanded`・`aria-controls`）・100 件ずつ・理由と ⚠ の文字・noChange で適用を押せない、適用の確かめと結果、STALE でファイルの選択が残る、誤りの一覧の件数の警告にフォーカス・無い値は「—」・`path` は点の形のまま、`DSL_NOT_APPLIED`・413・415・BUSY の文言、登録（260）。axe を部品ごとに1件 |
| S9 | `UserRolesDialog.test.tsx`（新）、`rowActions.test.ts`・`UserRowActions.test.tsx`・`UserAdminPage.test.tsx`（直す・足す） | 「ロールを見る」がどの行でも Dropdown の最後にある、Modal の題に氏名、出どころの並び、無いとき「ロールはありません」、`USER_NOT_FOUND`、403 は骨組み、閉じたら行の「操作」へ戻る、ロールの一覧へのリンク、既存の操作と表の列が変わらない。axe を1件 |

## 5. カバレッジの目標

- 画面の全体で **行 80%・分岐 70% 以上**（`frontend/vitest.config.ts` の `thresholds`、`team.md`）。Step 22・29 の `npm run test:coverage`（`verify` の段 7）で判定する。
- 新しい3つの機能と useradmin の変更を計測から外さない。除外（`src/main.tsx` と型の宣言とテストのファイル）を足さない（NFR6.4）。`src/main.tsx` は今も計測の外のため、入口の差し替えは `DataRouterBlocker.test.tsx` と E2E で確かめる。
- この単位のファイルだけの値（3.5）も、目安として行 80%・分岐 70% 以上を保ち、`code-summary.md` に機能ごとの値を書く。下回る部品があれば、除外を足さずにテストを足す。

## 6. モック・差し替えの指針

- **API**: 画面の部品とフックは、各機能の `api` を props（`api?: RoleAdminApi` など）で差し替える（既存の useradmin と同じ形）。`fetch` を直接差し替えるのは API の関数のテスト（`apiRequest`・`apiDownload` の差し替え）だけ。
- **ルーター**: `useBlocker` を使う部品（`RoleDetailPage`・`PermissionPanel`・`useUnsavedGuard`）は `createMemoryRouter`＋`RouterProvider` で描く（`roleadmin/testing/renderRoleAdmin.tsx`）。それ以外は既存の `renderWithProviders`（`MemoryRouter`）のままでよい。`renderWithProviders` は変えない。
- **骨組み**: 403 の表のテストは `AdminForbiddenProvider` を本物で使い、`useAdminForbidden` の呼び出しを確かめる。サイドバーと登録のテストは `decideRoute` と各機能の `registration` を本物で使う。
- **時計**: 待ちの案内と古い答えのテストは Vitest の偽の時計（`vi.useFakeTimers`）で進める。実時刻と `sleep` に頼らない。描画の後の値は `waitFor`・`findBy*` で待つ。テストの時間の上限（既定 5 秒）は、原因を確かめずに延ばさない。
- **ブラウザの口**: `beforeunload` は `window.addEventListener` の spy で張り外しを確かめる。ファイルの保存（`saveFile.ts`）は `URL.createObjectURL`・`revokeObjectURL` を差し替えて、一時の URL をすぐ捨てることを確かめる。`File` は大きさだけを持つ見本（`Object.defineProperty` で `size` を与える）で 10 MiB の境界を作り、本文を実際に 10 MiB 作らない。
- **make-you-chic-ui**: 部品は差し替えない（本物で描く）。開閉・フォーカス・`aria-*` は make-you-chic-ui の部品に頼る振る舞いも含めて、このリポジトリのテストで確かめる（`team.md`）。
- **fast-check**: 失敗したときの種（`seed`）と道（`path`）が出るよう、`fc.assert` の既定の報告を使う。種を固定して再現するときは `{ seed, path }` を渡す。

## 7. テストデータの扱い

- 見本は各機能の `testing/fixtures.ts` に関数で置き、テストごとに新しい値を作る（共有の可変の見本を持たない）。型は各機能の `api/types.ts` を付ける。
- メールアドレスは予約のドメイン（`example.com`）だけ。氏名は既存の見本と同じく実在しそうでない架空の名前（2語の氏名を含める）。ロール・グループの名前に 64 文字のもの、`<script>`・`<img onerror>`・`&`・`"`・`'` を含むもの、U+200B を含むものを入れる。
- 画面のテストの見本は小さく保つ（行は数件〜100 件。1,000 カラムと 25 万の変わる点は E2E の 140 の測りだけで使う）。
- E2E の 150 は走らせるごとに一意の名前（`E2EF_` と `runTag`）で、自分で作った利用者・ロール・グループ・DSL だけを対象にし、初期管理者を変えない。作った利用者の値は `recordSecretValues` で値のファイルに書き、報告の部品が json の報告と `test-results/` に残っていないことを確かめる。後始末はしない。
