# Generation Notes — U5 利用者の管理の画面（u5-user-admin-ui、B5）

U5 のコード生成の経過・実測の値・計画との差を、依頼の回ごとに記録する。計画は `code-generation-plan.md`、テストの手順は `unit-test-instructions.md`（同じディレクトリ）。パスはリポジトリのルートからの相対パス。

## 回1（Step 1・2 と Step 3 の変更）— 2026-10-03

### Step 1: 作業の場と変更の前の基準

| 項目 | 結果 |
|---|---|
| ブランチ | `feature/260930-user-admin-b5`（`git rev-parse --abbrev-ref HEAD`）。オーケストレーターが依頼者の承認を得て `develop` から作成済み |
| 先頭 | `3f6ae9b817b43aea934f63b5880c8ef6f7f3752b`（`develop` も同じ） |
| 未コミットの変更（回1 の作業の前） | `aidlc/spaces/default/intents/260930-user-admin/audit/sakura-local-4e42a93f87ce.md` だけ（アプリのソースに未コミットの変更なし） |
| 記録だけのコミット R1 | `3f6ae9b`（オーケストレーターが依頼者の承認を得て作成）。`21a2fdd..3f6ae9b` は `37a5566`・`5624d86`・`3f6ae9b` の3コミットで、`git diff --stat 21a2fdd HEAD -- . ':!aidlc'` は空（`aidlc/` の外のファイルは入っていない） |
| B4 の統合の後の CI | `gh run list --branch develop --limit 3` の先頭は run `37036697597`（「U3 の計画の Step 43 のチェック…」、push、success、12m29s、2026-10-02T16:50:51Z）。その前の2件（`37015756225`・`37010663674`）も success |
| Dependabot の開いている知らせ | `gh pr list --state open` は 0 件（High 以上の知らせなし） |
| `frontend/playwright-report/`・`frontend/test-results/` | 作業の前は手元に無かった |
| 画面の基準 | B2 の関門の実測（行 97.61%・分岐 93.01%、95 ファイル・801 件）を基準とする。`git diff --stat 1de9ef6 21a2fdd -- frontend` は空 |
| 初回の JavaScript の大きさ (1) | `./gradlew frontendBundleSize` で **122.7 KB（gzip）**（`assets/index-*.js` 106.1 KB・`assets/useTranslation-*.js` 16.6 KB）。`frontendBuild` は UP-TO-DATE（`21a2fdd` から画面のソースに差が無いため、前のビルドの結果を測った）。B2 の基準 122.7 KB と同じ |

#### 影響の洗い出し（U4 の N-16 の反省）

U5 の変更（サイドバーの項目「利用者の管理」`/admin/users` の追加・固定先の更新・報告の設定の変更）で期待が変わる既存のテストと E2E を検索した。**期待を変える必要があるものは見つからなかった**（直す提案は無い）。

| 検索 | 当たったもの | 判断 |
|---|---|---|
| `frontend/src` のテストの `registrationModules`・`loadRegistrations`・`sidebarItems`・`order:` | `AdminForbiddenView.test.tsx`・`forbiddenHeading.test.ts`・`ShellLayout.test.tsx`・`navigationItems.test.ts`・`loadRegistrations.test.ts`・`validateRegistrations.test.ts`・`AppRouter.test.tsx`・各機能の `registration.test.tsx`（dsl・invitation・preferences・registration） | どれもテストの中で作った登録（見本）を使い、実際の `registrationModules`（`import.meta.glob`）を読まない。`App.test.tsx` も `modules` を引数で渡す。新しい登録の影響を受けない |
| `frontend/e2e` の `getByRole('link'`・`toHaveCount` | 030（`管理` を exact で）・040（`DSL` を exact で）・090（管理者でない利用者に `管理`・`利用者の招待`・`DSL` のリンクが無いこと）・`support/adminLogin.ts`（名前を exact で押す） | どれも名前を exact で指すため「利用者の管理」と重ならない。090 は管理者でない利用者で、新しい項目（`visibleWhen: 'ADMIN'`）も出ない。リンクの数を数えるものは無い |
| `app-shell` の中の `dropdown-trigger`（`openUserMenuItem`・080 の 232 行目） | 080（ホーム・プリファレンスの画面で使う）・100（ホームで使う） | どれも利用者の管理の画面の外で使うため、行の「操作」の `dropdown-trigger` と重ならない。**110・120 では、利用者の管理の画面の上で `openUserMenuItem` を使わない**（重なって厳密モードの誤りになる）ことを Step 17 で守る |
| `Dropdown` の項目の押下で閉じることに頼るもの | 020・030・040・090 の `menuitem`「ログアウト」の押下、`openUserMenuItem` | 固定先の更新（`3481488`）は押せない項目のふるまいだけを変え、押せる項目の押下で閉じる形は変えない見込み（計画 8節の D-3）。Step 4 の E2E で確かめる |
| `html` の報告・`trace` に頼るもの | `playwright.config.ts`（reporter の html、`trace: 'retain-on-failure'`）だけ。E2E のファイルと `support/` は頼っていない | Step 15 の設定の直しで既存の E2E の期待は変わらない |
| （追加で検索）`/admin/users` | 010 の「画面の URL を直接開いてもログインの枠になる」（ログインしていない状態で `/admin/users` を開く）、`decideRoute.test.ts`・`forbiddenHeading.test.ts`（見本の登録） | 010 は、今は登録の無い URL として、U5 の後は `access: 'ADMIN'` の登録のある URL としてログインの枠を出す。`decideRoute.test.ts` で、ログインしていないときに登録のある画面の URL がログインの枠になることが確かめられているため、期待は変わらない見込み。Step 22 の E2E で確かめる |

ほかに、サイドバーの項目が1つ増えることで、管理者でログインする既存の E2E（060・070・080・100・130 など）の axe とはみ出しの検査の対象に「利用者の管理」のリンクが入る。期待を変えるものではないが、結果は Step 4（固定先の後、U5 の画面の前のため、まだ項目は無い）ではなく Step 22 の E2E で確かめる。

### Step 2: テストの実行の準備

`unit-test-instructions.md` 2.1 のコマンドを作業ブランチの上（変更の前）で流した。

| コマンド | 結果 |
|---|---|
| `(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/preferences src/app/layout/ShellLayout.test.tsx src/app/registry src/features/invitation/InvitationAdminPage.test.tsx src/shared/paging/paging.test.ts)` | **15 ファイル・175 件すべて成功**（6.73 秒）。内訳: ShellLayout 18・loadRegistrations 4・validateRegistrations 13・InvitationAdminPage 32・preferences の errorMessages 5・fieldErrors 8・formChecks 9・PasswordChangeForm 6・PasswordChangePage 17・preferencesApi 8・PreferencesForm 10・PreferencesLoadFailure 5・PreferencesPage 23・registration 6・paging 11 |
| 道具 | fast-check は `fieldErrors.test.ts`・`validateRegistrations.test.ts`・`paging.test.ts`、vitest-axe は `PasswordChangeForm`・`PasswordChangePage`・`PreferencesForm`・`PreferencesLoadFailure`・`PreferencesPage`・`ShellLayout`・`InvitationAdminPage` のテストで動いた |
| `(cd frontend && npx playwright test --list e2e/130-admin-forbidden-accessibility.e2e.ts)` | `Total: 20 tests in 1 file`。報告の部品は「仮の資格情報は含まれていません（3 項目を確かめた）」 |

**計画との差（N-1）**: `--list` はアプリを起動しないが、今の設定（reporter の html・json）で `frontend/playwright-report/index.html` と `frontend/test-results/e2e-results.json` を作った（計画・手順書はこのことに触れていない）。テストは実行されておらず（json の `stats` は expected 0・skipped 20・unexpected 0・flaky 0・duration 約 88 ミリ秒）、資格情報も使っていないが、`gate-decisions.md` の U5 R-02 の手順にそろえ、`stats` だけを読んで記録した後に中を開かずに消した（ファイル 2・ディレクトリ 2）。共有していない。Step 15 の設定の直しの前に `--list` を流すときは同じ扱いにする。

### Step 3: `frontend/.npmrc` の `ignore-scripts=true`（変更だけ）

- `frontend/.npmrc` を次の3行にした（`engine-strict=true` は残し、説明のコメントを1行足した）。ほかのファイルは変えていない。

  ```
  engine-strict=true
  # パッケージのインストールでスクリプト（install・postinstall など）を動かさない（team.md の Code Style）。
  ignore-scripts=true
  ```

- `frontend/` で `npm config get ignore-scripts` は `true`、`npm config get engine-strict` は `true`。
- 先に読み取りだけで確かめた影響の見込み: `frontend/package.json` の `scripts` にインストールの前後で動くもの（`preinstall`・`install`・`postinstall`・`prepare`）は無い。`frontend/package-lock.json` でインストールのスクリプトを持つ（`hasInstallScript`）のは `node_modules/fsevents`（dev・optional）の1件だけ。fsevents は作り済みの部品を同梱しており、スクリプトはその部品が使えないときの作り直しの予備のため、動かさなくても影響は無い見込み。確かめは C1 のコミットの後の `npm ci`・verify・E2E（回2）で行う。
- `.npmrc` は `vendor/make-you-chic-ui` の中のインストール（`vendorInstall`）には効かない（`vendor/make-you-chic-ui/.npmrc` は無い）。
- 回1 はここで止まる（C1 のコミットはオーケストレーターが依頼者の承認を得て行う。コミットの後の確かめは回2）。

### 回1 の終わりの作業フォルダ

- 変えたアプリのファイル: `frontend/.npmrc` だけ。
- 記録: この `generation-notes.md`（新しい）と `code-generation-plan.md` のチェック（Step 1 の8項目・Step 2 の3項目・Step 3 の1項目目）。
- `git add`・コミットはしていない。

## 回2（Step 3 の確かめ・Step 4 の固定先の更新の準備と FC 2.5 の確かめ）— 2026-10-03

依頼者の決定: N-1（`--list` が作った報告を記録してから消した扱い）は受け入れ。C1 は依頼者の承認を得てオーケストレーターがコミットした（`17af97d`、`frontend/.npmrc` だけ）。

### Step 3: C1 の後の確かめ

| 項目 | 結果 |
|---|---|
| 先頭 | `17af97d`（C1）。作業フォルダの未コミットは `aidlc/` の下だけ |
| 道具の版 | Node `v24.21.0`・npm `11.19.0` |
| `(cd frontend && npm ci)` | 成功（346 パッケージ、6 秒、脆弱性 0 件）。スクリプトに関する警告・誤りは出なかった。`hasInstallScript` の `fsevents`（dev・optional）は入り、同梱の部品（`fsevents.node`）を `require` で読み込めた |
| `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡した） | **BUILD SUCCESSFUL**（9 分 31 秒、05:54〜06:03）。バックエンドの単体 173 クラス・1506 件、結合 136 クラス・689 件で、失敗・誤り・飛ばしたもの 0（対象DB のテストは SKIPPED になっていない）。バックエンドのカバレッジ 行 98.90%（6276/6346）・分岐 94.83%（2331/2458）。画面のテスト 95 ファイル・801 件成功、画面のカバレッジ 行 97.61%（2414/2473）・分岐 93.01%（1505/1618）（B2 の基準と同じ）。初回の JavaScript 122.7 KB（gzip）。SpotBugs の関門・Gitleaks は通過。`osvScan` は lockfile が変わらないため UP-TO-DATE |
| verify の中のインストールとビルドのタスク | verify では `vendorInstall`・`vendorBuild`・`frontendInstall`・`frontendBuild` が **UP-TO-DATE** だった（`frontendInstall` の入力は `package-lock.json` だけで `.npmrc` を含まず、直前の手での `npm ci` が出力を更新していたため）。そのため `caffeinate -i ./gradlew frontendBuild --rerun-tasks` で4つを実際に流し直した（下の行） |
| `./gradlew frontendBuild --rerun-tasks` | 成功。`vendorInstall`（371 パッケージ）・`vendorBuild`・`frontendInstall`（346 パッケージ）・`frontendBuild` がすべて実行されて通った。`frontendInstall` にスクリプトの警告は出ない。`vendorInstall`（`vendor/make-you-chic-ui` の中、`frontend/.npmrc` は効かない）では npm 11 が「`fsevents@2.3.3` の install のスクリプトは allowScripts で許されていない」の警告（`npm warn install-scripts`）を出した。誤りではなくビルドは通った。これは C1 の前からの npm の既定のふるまいで、`.npmrc` の変更とは関わらない（N-2） |
| Mailpit | `docker compose ps` で `mastersmith-mailpit-1` がもとから動いていた（healthy）。`http://127.0.0.1:8025/api/v1/info` は 200。この回で起動していないため止めない |
| `caffeinate -i ./gradlew e2eTest` | **BUILD SUCCESSFUL**（4 分 15 秒、06:04〜06:09）。報告の部品「仮の資格情報は含まれていません（3 項目を確かめた）」 |
| json の `stats` | expected 130・skipped 0・unexpected 0・flaky 0・duration 約 252.7 秒 |
| ファイルごと | 010: 2・020: 2・030: 1・040: 1・050: 21・060: 21・070: 20・080: 21・090: 1・100: 20・130: 20（11 ファイル・130 件、すべて expected） |
| 報告を消したこと | 記録した後に `frontend/playwright-report/`・`frontend/test-results/` を中を開かずに消した（ファイル 3・ディレクトリ 106（2つの根を含む））。共有していない |
| 判定 | `ignore-scripts=true` による失敗は無い。Step 3 の4つ目（失敗したら止める）は当たらなかった |

### Step 4: 固定先の更新（準備）

| 項目 | 結果 |
|---|---|
| 更新の前 | `077f5b48ce84cd020ecec2d925836a085f9d9e11`（`git submodule status`） |
| `git -C vendor/make-you-chic-ui fetch` | `077f5b4..3481488  main -> origin/main`（取得元は `https://github.com/agwlvssainokuni/make-you-chic-ui`） |
| 公開の側にあること | `git branch -r --contains 34814887433c61a4eb51d3572362d6eb41edae30` は `origin/HEAD -> origin/main`・`origin/main`。`git ls-remote origin main HEAD` は `refs/heads/main` と `HEAD` がどちらも `34814887433c61a4eb51d3572362d6eb41edae30`（GitHub の公開のリポジトリの `main` の先頭）。コミットの日時 2026-10-01 09:38:47 +0900 |
| `077f5b4..3481488` | コミット1つ（`3481488 Dropdown: 項目にdisabled/descriptionを追加し押せない項目に理由を添えられるように`）。差分は `docs/integration-guide.md`・`Dropdown.css`・`Dropdown.test.tsx`・`Dropdown.tsx` の4ファイル（135 行の追加・4 行の削除）で、計画のとおり |
| `git -C vendor/make-you-chic-ui checkout 34814887433c61a4eb51d3572362d6eb41edae30` | 更新の後の先頭 `34814887433c61a4eb51d3572362d6eb41edae30`。サブモジュールの `git status --porcelain` は空（中身は変えていない） |
| ルートの差分 | `git diff vendor/make-you-chic-ui` は gitlink の1行（`077f5b4` → `3481488`）だけ。`git add`・コミットはしていない（C2 はオーケストレーターが依頼者の承認を得て作る） |

### Step 4: FC 2.5 の確かめ（`3481488` のソース・テスト・CSS と `Button.tsx` を読んだ結果）

| 確かめること | 結果 | 判定 |
|---|---|---|
| 押せない項目の押下（クリック・Enter・Space） | `handleActivate` の先頭で `item.disabled` なら何もせず戻る（`onClick` を呼ばず `close` もしない）。項目は `<button>`（`href` が無いとき）で、Enter・Space もネイティブの click として同じ `handleActivate` に入る。`href` の項目は Space を click に変え、押せないときは `href` 属性を出さない。部品のテスト「does not call onClick or close when a disabled item is clicked」「…activated with Enter or Space」「omits href and does not navigate for a disabled href item」がある | 期待どおり（R1） |
| 押せない形の印 | `aria-disabled={item.disabled \|\| undefined}`（`'true'` で付く。ネイティブの `disabled` 属性は付けない）。型は `MenuItem.disabled?: boolean` | 期待どおり（R1） |
| 理由の文 | `description` があれば `<span class="mycui-dropdown-item-description" id="${id}-item-${index}-desc">` を項目名の `<span>` の下（`display: flex; flex-direction: column`）に出し、項目に `aria-describedby` で結ぶ。部品のテスト「links an item to its description via aria-describedby」がある | 期待どおり（R2）。ただし下の N-3（理由の文が項目の名前にも入る） |
| 押せない項目へのフォーカス | ↑↓・Home・End は `items.length` と添字だけで移り、押せなさを見ない（押せない項目にも移る）。開いたときは `itemRefs.current[0]` へ（押せるかを問わず最初の項目）。部品のテスト「moves focus to a disabled item with ArrowDown」がある | 期待どおり（R3） |
| 押せない項目と理由の文の文字のコントラスト | 押せない項目の文字・理由の文はどちらも `--color-text-muted`。light は `--gray-500`（`#6b7280`）、dark は `--gray-300`（`#d1d5db`）。通常の背景 `--color-surface`（light `#fff`・dark `--gray-800` `#1f2937`）の上では light **4.83:1**・dark 9.96:1。**ホバーとキーボードのフォーカス（`:hover`・`:focus-visible`）の背景 `--color-surface-hover`（light `--gray-100` `#f3f4f6`・dark `--gray-750` `#283141`）の上では light 4.39:1**・dark 8.87:1（WCAG の相対輝度の式で計算した値。ブラウザの axe では確かめていない）。文字の大きさは `--font-size-sm`（通常の文字のため 4.5:1 が要る） | **light の押せない項目にホバー・キーボードのフォーカスを置いた状態で 4.5:1 を下回る**。FC 2.5 の「依頼者に戻す差」（4.5:1 を下回る）に当たる（N-4）。計画 8節の D-3 が「120 の状態 (11) で確かめる」とした点で、120 の状態 (11) の axe は light の組で color-contrast の違反になる見込み |
| 開き口の `aria-haspopup` | `'true'` のまま | 期待どおり（FS 10節 (d)） |
| 開き口の押下の受け方 | 今と同じく `cloneElement` で `onClick` を上書き（`ref`・`aria-haspopup`・`aria-expanded`・`data-testid="dropdown-trigger"` も）。`Button` は `onClick` を分割代入して `handleClick` で受け、`loading` なら `preventDefault` して呼ばない。`aria-disabled`・`aria-busy` を付け、ネイティブの `disabled` は付けない（フォーカスは残る） | 期待どおり（D13 の (2) は保たれる） |
| 今の使い方 | `label`・`href`・`onClick` だけの使い方は、押下の動きは変わらない。見た目は項目が `display: block` から `display: flex; flex-direction: column` になり、項目名が `<span class="mycui-dropdown-item-label">` に包まれた。既存の画面のテストの見込み（下の行）は通った。E2E は C2 の後に流す | 見込みでは崩れていない（確定は C2 の後の E2E） |

### Step 4: 見込みの確かめ（C2 の前、作業フォルダの上。結果は見込みとして扱う）

| 項目 | 結果 |
|---|---|
| `./gradlew vendorBuild` | 新しい固定先のソースで実行され成功（`vendorInstall` は `package-lock.json` が変わらないため UP-TO-DATE） |
| `(cd frontend && npm ci)` | 成功（脆弱性 0 件）。`frontend/node_modules/make-you-chic-ui` は `vendor/make-you-chic-ui/packages/make-you-chic-ui` への symlink で、`dist` に `mycui-dropdown-item-description` が入った |
| lockfile の差 | `frontend/package-lock.json`・`vendor/make-you-chic-ui/package-lock.json` とも差なし |
| `unit-test-instructions.md` 2.2 の画面のテスト（`src/app/layout`・`src/features/invitation`・`src/features/dsl`・`src/features/preferences`） | **40 ファイル・359 件すべて成功**（22.18 秒） |
| 作業フォルダ | `git status --short` は `aidlc/` の下の記録と `vendor/make-you-chic-ui`（gitlink）だけ。サブモジュールの中は空 |

E2E（010〜100・130）・初回の大きさ (2) は C2 のコミットの後（回3）に流す。

### 計画との差・気づいたこと

- **N-2（`frontendInstall` の入力に `.npmrc` が無い）**: Gradle の `frontendInstall`・`vendorInstall` の入力は `package-lock.json` だけで、`.npmrc` を変えても UP-TO-DATE になる。今回は `--rerun-tasks` で実際に流して確かめた。CI は毎回まっさらな取得から入れるため影響は無い。`build.gradle.kts` は U5 で変えない決まり（計画 4.2）のため直していない。あわせて、`vendorInstall` で npm 11 が `fsevents` の install のスクリプトについて警告を出す（`vendor/make-you-chic-ui` に `.npmrc` は無く、npm 11 の既定で許可の無いスクリプトを動かしていない様子）。ビルドは通る。
- **N-3（理由の文が項目の名前にも入る）**: `description` の `<span>` が項目（`role="menuitem"` の `<button>`）の中にあるため、項目の読み上げの名前は「項目名＋理由の文」をつないだもの（例「管理者の印を外す自分自身の印は外せません」）になり、説明（`aria-describedby`）でも理由の文がもう一度読まれる（`dom-accessibility-api` で計算して確かめた。ブラウザでは確かめていない）。FC 2.5 の「依頼者に戻す差」（結ばれず理由が分からない）には当たらないが、理由が2回読まれる。U5 のテスト・E2E で項目を名前で探すときは、名前の完全一致ではなく `dropdown-item-<n>` か名前の前方一致で探す必要がある。
- **N-4（light の押せない項目のホバー・フォーカスのコントラスト 4.39:1）**: 上の表のとおり。FC 2.5 の「依頼者に戻す差」に当たるため、依頼の決まり（「依頼者に戻す差」が出たら直さずに止めて報告する）に従い、ここで止める。固定先のチェックアウトは済み（C2 の中身の準備まで）。扱い（make-you-chic-ui への再依頼・frontend の側の一時の部品・受け入れ）は依頼者が決める。make-you-chic-ui の中身は変えていない（Forbidden）。

### 回2 の終わりの作業フォルダ

- 変えたもの: `vendor/make-you-chic-ui` の gitlink（`077f5b4` → `3481488`、未コミット）。アプリのソースは変えていない。
- 記録: この `generation-notes.md` の回2 の節と、`code-generation-plan.md` のチェック（Step 3 の2〜5項目目、Step 4 の1・2項目目）。
- `git add`・コミットはしていない。`frontend/playwright-report/`・`frontend/test-results/` は手元に無い。

## 回3（Step 5〜14）— 2026-10-03

依頼者の決定（回2 の後）: N-4（light の押せない項目のホバー・フォーカスのコントラスト 4.39:1）と N-3（理由の文が項目の名前にも入る）は make-you-chic-ui の側で依頼者が直す（依頼の文書は `make-you-chic-ui-request-2.md`）。直るまで作業フォルダのサブモジュールは `3481488` のまま（未コミット）で画面の生成を進め、C2 は作らない。N-2 は記録だけ。Step 4 の残り（C2 の後の確かめ）と Step 15 以降はこの回では行わない。

### Step 5・6: `fieldErrors` を共通へ移す

- `frontend/src/features/preferences/fieldErrors.ts`・`fieldErrors.test.ts` を `frontend/src/shared/api-client/` へ移した。口（`readFieldErrors`・`ReadFieldError`）とふるまいは変えていない。説明文に移した理由を1行足し、`apiError` の読み込みを同じフォルダーの `./apiError` に直した（`../../shared/api-client/apiError` のままでも解決するが、置き場が変わったため）。
- `usePreferencesForm.ts`・`usePasswordChangeForm.ts` の読み込み先を `../../shared/api-client/fieldErrors` に直した。`grep -rn "fieldErrors'" frontend/src` で古い読み込みが残っていないことを確かめた。
- `unit-test-instructions.md` 2.3: **10 ファイル・97 件すべて成功**（`fieldErrors.test.ts` 8 件は移す前と同じ件数、プリファレンスの 89 件も回1 の基準と同じ）。

### Step 7・8: 純粋な関数と文言

- 作ったもの: `useradmin/rowActions.ts`・`searchInput.ts`・`profileInput.ts`・`lockedUntil.ts`・`failureMessage.ts`・`focusTarget.ts`・`messages.ts`・`useUserAdminText.ts`（と型のため先に `api/types.ts`）。
- `unit-test-instructions.md` 2.4: **6 ファイル・39 件すべて成功**（rowActions 6・searchInput 8・profileInput 6・lockedUntil 6・failureMessage 8・focusTarget 5。手順書 3節の見込みと同じ）。fast-check は rowActions（性質 4）・searchInput（性質 2）で既定の 100 回。失敗は無く、種の記録は無い。各テストの先頭に種の再現の仕方を書いた。

### Step 9・10: API の受け渡し

- `useradmin/api/types.ts`・`api/userAdminApi.ts` を作った。一覧の本文は C3 の項目だけを写し、`lockedUntil` は無いか `null` なら持たない（D-4）。`useAdminForbidden` に渡すパスは `USER_ADMIN_API_ROOT`（一覧）と `userAdminOperationPath(userId, 操作)`（操作・保存）で作る。一覧のパスを作る `userAdminListPath` も同じファイルに置いた（計画 4.2 の一覧に無い小さな関数）。
- `unit-test-instructions.md` 2.5: **1 ファイル・10 件すべて成功**。

### Step 11・12: 画面の部品

- 作ったもの: `UserSearchBox.tsx`・`.css`、`UserTable.tsx`・`.css`、`UserRowActions.tsx`、`ConfirmActionDialog.tsx`、`EditProfileDialog.tsx`・`.css`、`testing/fixtures.ts`・`testing/renderUserAdmin.tsx`（と、`renderUserAdmin` が URL を読むため `registration.ts` を先に作った）。
- `unit-test-instructions.md` 2.6: **5 ファイル・40 件すべて成功**（UserSearchBox 7・UserTable 9・UserRowActions 8・ConfirmActionDialog 8・EditProfileDialog 8）。部品ごとに vitest-axe を1件以上入れた（誤りの状態・2語の氏名・長いメールアドレス・記号の氏名・5種類の確かめ・自分の行のメニューを開いた状態・busy）。
- N-3 の扱い: 押せない項目は、読み上げの名前に理由の文も入る今の形のため、`UserRowActions.test.tsx` と `UserAdminPage.test.tsx` では `dropdown-item-<n>` と名前の前方一致（`/^管理者の印を外す/`）で探した。理由の文は `toHaveAccessibleDescription` で完全一致を確かめた。make-you-chic-ui の直った版に上げたら、名前の完全一致に切り替えるかを決める（`UserRowActions.test.tsx` の先頭の説明文に書いた）。
- 押せない項目の押下（クリック・Enter・Space）で `onSelect` が呼ばれずメニューが開いたままであること、矢印で押せない項目に移ること、busy の間はクリック・Enter・Space で開かずフォーカスが残ることを、固定先 `3481488` の本物の `Dropdown` を通して確かめた。

### Step 13・14: 画面の組み立て

- 作ったもの: `useUserAdmin.ts`・`UserAdminPage.tsx`・`.css`・`registration.ts`。
- `unit-test-instructions.md` 2.7（`src/features/useradmin`・`src/app`・`src/features/admin`・`src/features/invitation`・`src/features/dsl`）: **71 ファイル・627 件すべて成功**。既存のテストは書き換えていない。
- `UserAdminPage.test.tsx` 57 件・`registration.test.tsx` 5 件。U4 のレビューの R-04（古い読み直しの 403 が後から届いても S6 にならず、最新の読み直しの 403 で S6 が出る）は、本物の `AdminForbiddenProvider` と ShellLayout の中で確かめた。PD 3.3 の参照の戻り（(a) 409・400、(b) 通信の失敗、(c) API の関数の想定外の例外、(d) 送信の途中で外して開き直す）を、一覧と送信のそれぞれで確かめた。
- 計画 8節の D-5 の確かめ: ja の画面で自分の言語を en に直して保存した直後の Toast が英語（`Updated the name and language of 管理 二郎`）で出ることを、ShellLayout の中で確かめた。ja・en の文言はどちらも i18next に読み込まれており（`createI18n` が両方の言語の資源を持つ）、`lng` で引けた。止めて諮る必要は無かった。
- 計画 8節の D-7: 表示の設定の6つの組（light・dark × sm・md・lg）を1件のテストで vitest-axe にかけた（違反 0）。

### Step 14 の終わりの確かめ（画面の全体）

| 確かめ | 結果 |
|---|---|
| `(cd frontend && npm run test:coverage)`（画面のテストの全体とカバレッジ） | **109 ファイル・952 件すべて成功**（回2 の 95 ファイル・801 件から 14 ファイル・151 件増えた。U5 の新しいテスト 14 ファイル・150 件と、`UserAdminPage.test.tsx` の追加 1 件） |
| 画面の全体のカバレッジ（`frontend/coverage/coverage-summary.json`） | **行 97.43%（2882/2958）・分岐 92.85%（1870/2014）**（下限 行 80%・分岐 70% を満たす。B2 の基準 行 97.61%・分岐 93.01% から少し下がった） |
| U5 の `useradmin/` の下の合計 | 行 96.49%（468/485）・分岐 92.17%（365/396） |
| U5 のファイルで目安（行・分岐とも 90%）を下回るもの | `useUserAdmin.ts` 分岐 85.06%（行 93.75%）、`UserRowActions.tsx` 分岐 83.3%（5/6）、`lockedUntil.ts` 分岐 87.5%（7/8）、`profileInput.ts` 分岐 75.0%（3/4）。理由: `useUserAdmin.ts` は、部品の側の守り（Table の押下を捨てる・Button の loading）で画面からは届かない参照の守り（`goToPage` などの先頭の `loadingRef`・`submittingRef` の確かめ）と、画面を離れた後の分かれ道が残る。ほかの3つは `??` の右辺（`parts.find(...)?.value ?? ''`・`SERVER_REASONS[...] ?? 'unknown'`）と、busy の間に項目の選択が届く守り（ふつうは起きない、FS 4.4）。目安は記録のためのもので、下限は満たす |
| `fieldErrors.ts`（移したもの） | 行 100%・分岐 100%（移す前と同じテスト） |
| `npm run typecheck` | **1 件の誤り**: `e2e/support/preferencesFixtures.ts` の 22 行目が移す前の `../../src/features/preferences/fieldErrors` を読み込んでいる（下の N-6。依頼者の決定を待つ）。U5 のファイルの型の誤りは 0 件 |
| `npx oxlint .` | 成功（指摘 0） |
| `npx eslint .` | 成功（指摘 0） |
| `npx stylelint --allow-empty-input "src/**/*.css"` | 成功 |
| `node scripts/check-license-header.mjs` | 「すべてのファイルにヘッダーがあります」 |
| `npx prettier --check src/features/useradmin src/shared/api-client src/features/preferences`・`npm run format:check` | 成功。`.npmrc` は Prettier が形式を推定できず対象の外（手順書 2.8 の注記どおり、名指しから外した） |
| 遅いテスト | U5 のテストで最も遅いのは 400 ミリ秒ほど（`keeps the page and the search text after a search 400 …`）。テストの時間の上限は延ばしていない |
| 作業フォルダ | `git add`・コミットはしていない。サブモジュールは `34814887433c61a4eb51d3572362d6eb41edae30` のままで中身の変更なし（`git -C vendor/make-you-chic-ui status --porcelain` は空）。`git diff --stat 21a2fdd` で `frontend/src/app`・`invitation`・`admin`・`dsl`・`auth`・`registration`・`shared/{paging,format,validation}`・`frontend/e2e`・`package.json`・`package-lock.json`・`backend` に差が無い |

### 計画との差・気づいたこと

- **N-5（`fieldErrors.test.ts` の読み込みが2行）**: 計画は「読み込みの1行だけを直す」としたが、テストはプリファレンスの `formChecks`（`PASSWORD_FIELDS`・`PREFERENCES_FIELDS`）も読み込んでいたため、その1行も `../../features/preferences/formChecks` に直した（計2行。件数と中身は変えていない）。その結果、共通（`src/shared`）のテストが機能（`src/features/preferences`）を読み込む形になった（本体のコードの向きは変わらない。決まりを検査する道具は無く、型・リンタは通る）。共通の側に項目の一覧をテストの中で持つ形に替えるかは、依頼者に確かめたい。
- **N-6（`e2e/support/preferencesFixtures.ts` が移す前の `fieldErrors` を読み込む）**: 計画は既存の `e2e/support/` のファイルを変えないとし（4.5 の差分が無いことを記録するもの）、同時に `fieldErrors.ts` を移すとしたため、両方は守れない。`preferencesFixtures.ts` の 22 行目 `import type { ReadFieldError } from '../../src/features/preferences/fieldErrors'` が解決できず、`npm run typecheck`（`include` に `e2e` がある）が失敗する。計画と違う作りが要るため、直さずに止めて諮る。案: (A) `preferencesFixtures.ts` の読み込みの1行だけを `../../src/shared/api-client/fieldErrors` に直す（型の読み込みだけで、E2E のふるまいは変わらない。4.5 の記録に差を書く）。(B) `features/preferences/fieldErrors.ts` に共通のものを読み直す1行の再輸出を残す（機能の置き場に古い口が残る）。推奨は A。画面のテスト（Vitest）は E2E を読まないため、この誤りの影響を受けずに通っている。verify の型の検査の段は、決まるまで失敗する。
- **N-7（`git mv` ではなく `mv`）**: 計画 Step 5・6 は `git mv` としたが、依頼の決まり（`git add` しない）を守るため、索引に載せない `mv` で移した。コミットのときに `git add -A` などで移しとして記録される（中身はほぼ同じため、git は名前の変更として見分ける見込み）。
- **N-8（検索の説明の文は誤りのときも残す）**: make-you-chic-ui の `FormField` は誤りがあると補助の文（`helperText`）を出さず、`aria-describedby` も誤りだけになる。FS W3 の「説明を `aria-describedby`、誤りを `aria-invalid`」を両方満たすため、`UserSearchBox` は `FormField` を見える label にだけ使い、説明の文と誤りの文を自分で描いて、入力欄の `aria-describedby` に誤り → 説明の順で結んだ（誤りの文の色はトークン `--color-danger-text`、`role="alert"`）。make-you-chic-ui の中身は変えていない。
- **N-9（言語の誤りの文は言語のまとまりに `aria-describedby` で結べない）**: `RadioGroup` の `fieldset` は `FormField` の中でだけ `aria-describedby` を受ける。`FormField` の `label` が `fieldset` を指す形を避けるため、`EditProfileDialog` は言語の誤りを `RadioGroup` のすぐ下に `role="alert"` で出す形にした（読み上げは alert で届く。まとまりに結ぶ口は無い）。誤りのときは選んでいる言語の選択肢へフォーカスを移す。
- **N-10（`useUserAdmin` の受け方）**: FC 3節の断片 `useUserAdmin(api, t, language)` ではなく、`useUserAdmin({ api, t, reportForbidden, applyOwnProfile })` の形にした（計画 4.2 は画面が U4 の口を渡すと書いており、それに合わせた）。画面の言語は日時の書式と文言のために画面（`UserAdminPage`）が使い、フックには渡していない（フックの中で使い道が無いため）。
- **N-11（部品の props の小さな違い）**: `UserTable` の Table の文言（`labels`）は props で受けず部品の中で文言から作る。`UserTable` は表の名前（`label`）・今の時刻（`now`）を受ける。`UserRowActions` は文言を引く関数を props で受けず部品の中で引き、フォーカスを戻すための包む要素の参照（`containerRef`）を受ける（`Dropdown` が開き口の `ref` を上書きするため、`Button` への `ref` では受けられない）。`ConfirmActionDialog`・`EditProfileDialog` も文言は部品の中で引く。確かめの表示の `Modal` は `role="alertdialog"`（招待の取り消しの確かめと同じ）。
- **N-12（一覧の読み直しが重なる道）**: D13 の守りで、ページ送り・検索・もう一度読み込むは読み直しの間に捨てられるため、画面の操作で読み直しが重なるのは、保存の失敗（404・5xx）の後ろの読み直しの最中にもう一度保存して失敗したときなどに限られる。「最後の読み直しの答えだけを使う」と U4 の R-04 のテストは、この道（保存の 500 を2回）で重ねて確かめた。
- **N-13（開いたメニューの vitest-axe）**: メニューは make-you-chic-ui が `body` の直下に描く（ランドマークの外）ため、`body` を検査すると best-practice の `region` の規則に当たる。既存の `ShellLayout.test.tsx` のユーザーメニューのテストと同じく、WCAG 2.0・2.1 の A・AA の規則で確かめた（文字のコントラストは jsdom で計算できないため、120 の実際のブラウザで確かめる）。
- **N-14（`useAdminForbidden` に渡したパスの確かめ方）**: `UserAdminPage.test.tsx` は `vi.mock` で本物の `AdminForbiddenProvider` のモジュールを包み、`useAdminForbidden` が返す関数に渡したパスだけを記録する（判定と S6 は本物のまま）。一覧は `/api/admin/users`、操作は `/api/admin/users/11/suspend`、保存は `/api/admin/users/11/profile` が渡ることを確かめた。

### 回3 の終わりの作業フォルダ

- 新しいファイル: `frontend/src/features/useradmin/` の下のすべて（本体 18・CSS 4・テスト 14・テストの補助 2）、`frontend/src/shared/api-client/fieldErrors.ts`・`fieldErrors.test.ts`（移したもの）。
- 消えたファイル（移したため）: `frontend/src/features/preferences/fieldErrors.ts`・`fieldErrors.test.ts`。
- 変えたファイル: `frontend/src/features/preferences/usePreferencesForm.ts`・`usePasswordChangeForm.ts`（読み込み先の1行ずつ）。
- 記録: この節と、`code-generation-plan.md` の Step 5〜14 のチェック（36 項目）。
- `git add`・コミットはしていない。サブモジュールは `3481488` のまま（未コミット）。`frontend/coverage/` は `.gitignore` の対象。

## 回3 の続き（N-5・N-6 の直しと、固定先 3d9521a への更新）— 2026-10-03

依頼者の決定（回3 の後）: N-6 は案 A、N-5 は直す、N-8・N-9 は受け入れ。make-you-chic-ui の直し（N-3・N-4）が `3d9521a` として公開の側の `main` に入った（`3481488..3d9521a`）。

### N-6・N-5 の直し

- N-6（案 A）: `frontend/e2e/support/preferencesFixtures.ts` の 22 行目の型の読み込みだけを `../../src/shared/api-client/fieldErrors` に直した（ほかは変えていない。`git diff --stat -- frontend/e2e` は1ファイル・1行）。**計画 4.5（既存の `e2e/support/` に差分が無いことを記録する）との差**として記録する: `preferencesFixtures.ts` は `fieldErrors.ts` を移したことに伴う読み込みの1行だけが変わる。E2E のふるまいは変わらない（型だけの読み込み）。
- N-5: `src/shared/api-client/fieldErrors.test.ts` はプリファレンスの `formChecks` を読み込まず、テストの中に項目の一覧（`PREFERENCES_FIELDS`・`PASSWORD_FIELDS`、`formChecks.ts` と同じ名前と値）を持つ形にした。件数は 8 件のまま。
- 確かめ: `npm run typecheck` 誤り 0 件、`npx oxlint .`・`npx eslint .`・Stylelint・ライセンスヘッダー・`npx prettier --check src e2e` すべて通過。`unit-test-instructions.md` 2.3: 10 ファイル・97 件成功。

### 固定先の更新（3481488 → 3d9521a）

| 項目 | 結果 |
|---|---|
| `git -C vendor/make-you-chic-ui fetch` | `3481488..3d9521a  main -> origin/main` |
| 取り込むコミット | `3d9521aa54b1d6277de473f9e935a496fb56ac1b`（2026-10-03 06:24:54 +0900「Dropdown: 押せない項目のhover/focusコントラスト不足と読み上げ重複を修正」） |
| 公開の側にあること | `git branch -r --contains 3d9521aa…` は `origin/HEAD -> origin/main`・`origin/main`。`git ls-remote origin main HEAD` は `refs/heads/main`・`HEAD` ともに `3d9521aa54b1d6277de473f9e935a496fb56ac1b` |
| `3481488..3d9521a` の差分 | `Dropdown.css`（+7・−2）・`Dropdown.test.tsx`（+11）・`Dropdown.tsx`（+6・−1）の3ファイル |
| `077f5b4..3d9521a` の差分（C2 で取り込む全体） | `docs/integration-guide.md`・`Dropdown.css`・`Dropdown.test.tsx`・`Dropdown.tsx` の4ファイル（158 行の追加・6 行の削除） |
| `git -C vendor/make-you-chic-ui checkout 3d9521aa54b1d6277de473f9e935a496fb56ac1b` | 先頭 `3d9521aa54b1d6277de473f9e935a496fb56ac1b`、サブモジュールの `git status --porcelain` は空 |
| ルートの差分 | `git diff vendor/make-you-chic-ui` は gitlink の1行（`077f5b4` → `3d9521a`）だけ。`git add`・コミットはしていない |

### 直りの確かめ（ソース）

| 確かめること | 結果 |
|---|---|
| 押せない項目のホバー・フォーカスで背景が変わらない | `.mycui-dropdown-item:hover`・`:focus-visible` の背景（`--color-surface-hover`）に `:not([aria-disabled='true'])` が付いた。押せない項目の背景は通常のまま（`--color-surface`） |
| フォーカスの見せ方 | `.mycui-dropdown-item[aria-disabled='true']:focus-visible` に `outline: none; box-shadow: var(--focus-ring)` |
| 読み上げの名前（N-3） | 項目名の `<span class="mycui-dropdown-item-label">` に `id`（`${id}-item-${index}-label`）が付き、項目（`<button>`・`<a>` の両方）に `aria-labelledby` でその id だけを結ぶ。`aria-describedby` の理由の文はそのまま。部品のテスト「exposes the label alone as the accessible name, not the label plus description」が足された |
| 押下・フォーカスの移り（R1・R3）と開き口（`cloneElement`・`aria-haspopup="true"`） | 変わっていない（差分は上の3点だけ）。props（`MenuItem`）も変わらない |

### コントラスト（WCAG の相対輝度の式で計算し直した値。ブラウザの axe は C2 の後の 120 で確かめる）

| テーマ | 押せない項目の文字・理由の文（`--color-text-muted`）と背景 | 通常 | ホバー | キーボードのフォーカス |
|---|---|---|---|---|
| light | `#6b7280` と `#ffffff`（`--color-surface`） | 4.83:1 | 4.83:1（背景が変わらない） | 4.83:1（背景が変わらない） |
| dark | `#d1d5db` と `#1f2937`（`--color-surface`） | 9.96:1 | 9.96:1 | 9.96:1 |

- 回2 の N-4（light のホバー・フォーカスで 4.39:1）は直った。どちらのテーマでも 4.5:1 以上。
- 気づいたこと（直すかは依頼者が決めること。U5 の作りには影響しない）:
  - 押せる項目に理由の文（`description`）を付けた場合は、ホバー・フォーカスで理由の文が `--color-surface-hover` の上に出て light で 4.39:1 になる。U5 は押せない項目にだけ理由の文を付けるため当たらない。
  - フォーカスリング（`--focus-ring`、ブランドカラーの 40% の混ぜ）と背景の比は、4つのブランドカラー × light・dark で 1.40〜1.89:1。このリングは make-you-chic-ui の部品で共通に使われている既存のもので、axe の検査の対象にはならない。

### 固定先を上げた後の確かめ（C2 の前、作業フォルダの上。見込みとして扱う）

| 項目 | 結果 |
|---|---|
| `./gradlew vendorBuild` | 新しい固定先のソースで実行され成功（`vendorInstall` は lockfile が変わらないため UP-TO-DATE） |
| `(cd frontend && npm ci)` | 成功（脆弱性 0 件）。`frontend/package-lock.json`・`vendor/make-you-chic-ui/package-lock.json` とも差なし |
| テストで押せない項目を探す形（N-3 の切り替え） | `UserRowActions.test.tsx`・`UserAdminPage.test.tsx` の `dropdown-item-<n>` と名前の前方一致を、名前の完全一致（`getByRole('menuitem', { name: '管理者の印を外す' })` など）に切り替えた。理由の文は説明として完全一致で確かめる。2 ファイル・65 件成功。`UserRowActions.tsx`・`.test.tsx` の説明文の固定先を `3d9521a` に直した |
| `npm run test:coverage`（画面のテストの全体） | **109 ファイル・952 件すべて成功**。カバレッジは行 97.43%（2882/2958）・分岐 92.85%（1870/2014）（回3 と同じ） |
| 型・oxlint・ESLint・Stylelint・ライセンスヘッダー・Prettier | すべて通過（型の誤り 0 件） |
| 作業フォルダ | `git add`・コミットはしていない。`git diff --cached` は空 |

E2E（010〜100・130）・初回の大きさ (2) は C2 のコミットの後に流す（まだ流していない）。

### C2 のコミットの案

対象は `vendor/make-you-chic-ui`（gitlink）だけ（`aidlc/` の下と画面のソースを含めない。`git show --stat` で確かめる）。

件名:

```
make-you-chic-ui の固定先を 3d9521a に上げる（Dropdown の押せない項目と理由の文）
```

本文:

```
vendor/make-you-chic-ui の固定先を更新する（専用のコミット）。

更新前: 077f5b48ce84cd020ecec2d925836a085f9d9e11
更新後: 3d9521aa54b1d6277de473f9e935a496fb56ac1b

取り込む変更（077f5b4..3d9521a、2コミット）:
- 3481488 Dropdown: 項目にdisabled/descriptionを追加し押せない項目に理由を添えられるように
  （押せない項目は onClick を呼ばずメニューを開いたまま、aria-disabled、理由の文を aria-describedby で結ぶ。
  docs/integration-guide.md・Dropdown の3ファイル）
- 3d9521a Dropdown: 押せない項目のhover/focusコントラスト不足と読み上げ重複を修正
  （押せない項目はホバー・フォーカスで背景を変えず、フォーカスは --focus-ring で示す。項目の名前は
  aria-labelledby で項目名だけを結ぶ）

公開の側の確かめ: 3d9521aa54b1d6277de473f9e935a496fb56ac1b は origin/main に含まれ
（git branch -r --contains）、git ls-remote origin main HEAD の先頭と一致する。
サブモジュールの中身はこのリポジトリから変更していない。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```

- C2 はこのリポジトリのコミットの区切り（計画 3.3）で、更新後のハッシュだけが計画の `3481488` から `3d9521a` に変わる（依頼者の決定による計画との差。Step 4 の FC 2.5 の表の確かめは `3481488` で行い、`3d9521a` の差分は上の「直りの確かめ」で確かめた）。
- N-5・N-6 の直し（`fieldErrors.test.ts`・`preferencesFixtures.ts`）は C3 に入れる（共通への移しの区切り）。

## 回4（Step 4 の残り・Step 15〜18）— 2026-10-03

前の回の後: C2 は依頼者の承認を得てオーケストレーターがコミットした（`364e9d6`、`vendor/make-you-chic-ui` の gitlink を `077f5b4` → `3d9521a`）。作業ブランチには Step 5〜14 の画面の変更と N-5・N-6 の直しが未コミットのまま残っている（消さない・戻さない）。

### Step 4: C2 の後の確かめ

| 項目 | 結果 |
|---|---|
| 先頭 | `364e9d6`（C2）。サブモジュールは `3d9521aa54b1d6277de473f9e935a496fb56ac1b`、中の `git status --porcelain` は空 |
| `(cd frontend && npm ci)` | 成功（脆弱性 0 件）。`frontend/package-lock.json`・`vendor/make-you-chic-ui/package-lock.json` とも差なし |
| `./gradlew vendorBuild` | UP-TO-DATE（回3 の続きで `3d9521a` のソースから作った `dist` のまま。`dist/index.js` に `item-label` が入っていることを確かめた） |
| `unit-test-instructions.md` 2.2 の画面のテスト | **39 ファイル・351 件すべて成功**（回2 の 40 ファイル・359 件との差は、`fieldErrors.test.ts`（8 件）を `src/features/preferences` から `src/shared/api-client` へ移したため） |
| Mailpit | `mastersmith-mailpit-1` がもとから動いていた（healthy、`/api/v1/info` は 200）。止めない |
| `caffeinate -i ./gradlew e2eTest` | **BUILD SUCCESSFUL**（4 分 18 秒、06:53〜06:58）。報告の部品「仮の資格情報は含まれていません（3 項目を確かめた）」 |
| json の `stats` | expected 130・skipped 0・unexpected 0・flaky 0・duration 約 252.4 秒 |
| ファイルごと | 010: 2・020: 2・030: 1・040: 1・050: 21・060: 21・070: 20・080: 21・090: 1・100: 20・130: 20（11 ファイル・130 件、すべて expected） |
| 報告を消したこと | 記録した後に `frontend/playwright-report/`・`frontend/test-results/` を中を開かずに消した（ファイル 3・ディレクトリ 106（2つの根を含む））。共有していない |
| 初回の JavaScript の大きさ (2) | **122.8 KB（gzip）**（`index-*.js` 106.1 KB・`useTranslation-*.js` 16.7 KB）。下の N-15 のとおり、`364e9d6`（C2 の先頭、U5 の画面を含まない）の `frontend` を `git archive` でリポジトリの外（`$HOME/tmp-u5-bundle`、権限 700）に出し、`vendor` と `node_modules` を作業フォルダへの symlink にして `npx vite build` と `scripts/check-bundle-size.mjs` で測った。測った後に一時の場所ごと消した。(1) 122.7 KB からの増え 0.1 KB は Dropdown の直しの分 |
| 参考: 作業フォルダ（U5 の画面の変更を含む）の大きさ | `./gradlew frontendBundleSize` は 125.5 KB（gzip）（`index-*.js` 108.8 KB・`useTranslation-*.js` 16.7 KB）。`frontendBuild` は e2eTest の `bootWar` で作業フォルダから作られたもの。確定の (3) は Step 22 で測る |

- **N-15（Step 4 の確かめが U5 の画面の変更を含む作業フォルダで流れた）**: 計画は C2 の後に「固定先の更新だけの影響」を確かめる形だが、依頼者の決定（回3）で Step 5〜14 の画面の変更が未コミットのまま作業フォルダにあるため、`npm ci`・画面のテスト・E2E（`bootWar` は作業フォルダから作る）は U5 の画面の変更を含んだ状態で流れた。E2E 010〜100・130 は 130 件すべて成功で、固定先の更新と U5 の画面の変更のどちらでも既存の E2E が崩れていないことは言える（サイドバーに「利用者の管理」が出た状態で、既存の E2E の axe・はみ出しも通った）。切り分けが要る初回の大きさ (2) だけは、上のとおり C2 の先頭のソースから作り直して測った。

### Step 15: E2E の土台

- 作ったもの（`frontend/e2e/support/`、既存のファイルは変えていない）: `traceMode.ts`（`TRACE_MODES`・`traceModeFromEnv()`・`e2eTraceMode()`）、`secretValues.ts`（`SECRET_VALUES_FILE` = `test-results/e2e-secret-values.json`・`recordSecretValues`・`readSecretValues`・値の形の表 `SECRET_VALUE_SHAPES`（宛先・パスワード）・決まった値 `SECRET_FIXED_VALUES`（氏名「計測 花子」）・`RUN_TAG_MIN_SEARCH_LENGTH` = 24）、`userAdminRun.ts`（`newUserAdminRunTag()` = 時刻の16進＋`randomBytes(8)` の16進、27 文字ほど）、`userAdminFixtures.ts`（検査の一覧・空の一覧・測りの2ページ分（25 件）・409・400 の見本、5つの code、`shapeDifferences`）、`adminApiRoute.ts`（`routeAdminApi`・`expectNoBlockedWrites`・`normalizeAdminPath`・`isAdminApiPath`・`templatePath`）、`userAdminDiagnostics.ts`（`startUserAdminDiagnostics`）。
- `frontend/playwright.config.ts`: reporter から html を外した（list・json・報告の部品）。`use.trace` を `traceModeFromEnv()`（既定 `off`）にした。`outputDir` を `test-results` と明示した（既定と同じ場所）。報告の部品に、環境変数の名前と種類（`signingKey`・`adminEmail`・`adminPassword`）・`test-results/`・値のファイル・前の `playwright-report/`・trace の設定を渡す。資格情報は今のままプロセスの環境変数で、`webServer.env` は触っていない。冒頭の説明を直した。
- `frontend/playwright-secret-check-reporter.ts`: SD 3.5 の 1〜10 のとおり広げた（`node:fs`・`node:path`・`node:zlib` と `e2e/support/secretValues.ts` だけを読む。新しい依存は足していない）。
- **N-16（報告の部品の読み込みに拡張子 `.ts` を付けた）**: 使い捨ての台本から Node の型の取り除き（`node --experimental-strip-types`、Node 24.21）で報告の部品を読み込めるよう、報告の部品から `secretValues.ts` を拡張子つきで読み込む（`tsconfig.json` は `allowImportingTsExtensions: true`。Playwright もそのまま解決する）。`secretValues.ts`・`traceMode.ts`・`userAdminRun.ts` は Node の部品だけを読み込み、`registeredUser.ts` を読み込まない。そのため氏名「計測 花子」の値を `secretValues.ts` に重ねて置いた（説明文に同じ値であることを書いた）。台本は TypeScript のまま流せた（手順書 2.9 の JavaScript に替える必要は無かった）。
- **N-17（`E2E_TRACE` の誤りの知らせに指定の値を出す）**: `E2E_TRACE` は秘密ではないため、誤りの知らせに指定された値を出した（`E2E_TRACE は off・on・retain-on-failure のどれかにしてください（指定: "bogus"）。`）。
- 型の検査は誤り 0 件。Prettier・oxlint・ESLint・ライセンスヘッダーは通過。

### Step 16: E2E の土台の確かめ

使い捨ての台本（`$HOME/tmp-u5-reporter-check/check.mts`、`umask 077`・権限 700 の一時の場所）を `(cd frontend && node --experimental-strip-types "$WORK/check.mts")` で流した。台本は架空の値（`newUserAdminRunTag()` の `runTag`、`u7-perf-<runTag>@example.com`、`e2e-u7-pw-<24 文字の16進>`、氏名「計測 花子」、架空の署名鍵を架空の環境変数 `U5_CHECK_FAKE_KEY` に置いたもの）で報告のファイルを場合ごとに作り、報告の部品を `$WORK` の下へ向けた設定で `onEnd` を呼んだ。値のファイルは `recordSecretValues` で `umask 022` にしてから書き、権限を確かめた。報告の部品の出力に値が出ていないことも台本の中で比べた（結果は真偽だけを出した）。

| 場合 | 結果（期待） | 報告の部品が出した種類と件数 |
|---|---|---|
| (0) 値の無い報告 | 通過（通過） | 値の種類 5・見つかった件数 0 |
| (1) json の報告の注記に U の氏名 | 失敗（失敗） | displayName（json の報告）1 |
| (2) `test-results/` の下の `trace.zip`（deflate）の中に U のパスワードの URL の形 | 失敗（失敗） | password（trace）1 |
| (3) `error-context.md` に U のメールアドレス | 失敗（失敗） | email・runTag（失敗の画面の写し）各 1 |
| (4) 値のファイルを書かず、json に宛先（`@` を `%40` にした形）とパスワードの形 | 失敗（失敗） | email・password（json の報告）各 1（値の形だけで見つかった） |
| (5) 壊れた zip（先頭だけ zip の印） | 失敗（失敗） | 読めないもの: trace 1 |
| (6) 残した `playwright-report/index.html` の base64 の zip の中に U のメールアドレス | 失敗（失敗） | email・runTag（前の html の報告）各 1、消す手順の知らせ |
| (7) json の報告の添付の `body`（base64）に U のメールアドレス | 失敗（失敗） | email・runTag（json の報告の添付）各 1 |
| (8) 値のファイルの `runTag` が 16 文字（json にその値だけがある） | 通過・警告（通過・警告） | 「24 文字より短いため、単独では探さず宛先の形の中だけで探しました（1 件）」、見つかった件数 0 |
| （追加）標準出力の `buffer`（base64）に U のパスワード | 失敗（失敗） | password（json の報告の添付）1 |
| （追加）添付の `body` が base64 として壊れている | 失敗（失敗） | 読めないもの: json の報告の添付 1 |
| （追加）前の `playwright-report/` が値なしで残っている | 通過・警告（通過・警告） | 「もう作らない報告のため、frontend/playwright-report/ を消してください」 |

- どの場合も、報告の部品の出力に値は出ず、探し終えた後に値のファイルは無かった。`recordSecretValues` が作った値のファイルの権限はどれも 600（`umask 022` の下で書いた）。
- `E2E_TRACE=bogus npx playwright test --list e2e/130-admin-forbidden-accessibility.e2e.ts` は設定の読み込みで止まった（終了コード 1、「E2E_TRACE は off・on・retain-on-failure のどれかにしてください（指定: "bogus"）。」）。無指定は `off`（報告の部品が trace の知らせを出さない）。`E2E_TRACE=retain-on-failure` では報告の部品が「trace を有効にした実行です（retain-on-failure）。この実行は合否に使わず…」を出した。`--list` は html の報告を作らなくなった（`frontend/playwright-report/` は作られない）。`--list` が作った `frontend/test-results/e2e-results.json`（テストは実行されていない）はその都度消した。
- 確かめの後に一時の場所ごと消し、`git status --short`・`git diff --stat` で、確かめの変更・一時のファイルが作業フォルダに残っていないことを確かめた（変わっているのは Step 5〜15 の変更と記録だけ。`frontend/test-results/`・`frontend/playwright-report/` も無い）。

### Step 17: E2E 110・120 と README

- `frontend/e2e/110-user-admin-flow.e2e.ts`（1 件）: 計画の Step 17 の表の順1〜10。`test.use({ trace: e2eTraceMode() })`。`runTag` は `newUserAdminRunTag()`。U の宛先・氏名・`runTag` は U を作る前に、パスワードは作った直後に `recordSecretValues` で値のファイルに書く（作る途中で失敗しても報告の部品が探せるように）。宛先が 254 コードポイント以内であることを確かめる。前提が無ければ `skip-reason` に理由の種類だけを注記して飛ばす。行の「操作」は行の中の役割 `button` と名前の末尾「の操作」で探し、読み上げの名前に U の氏名と宛先が入っていることは比べた真偽だけを `expect` に渡す。順3・6 で一覧・409・400 の本物の応答と見本の項目の名前と型を `shapeDifferences` で照らし合わせる。順9 は依頼者の決定 Q-B A のとおり、管理者でない U の新しいコンテキストのページのログインの応答の `user.admin` だけを真に書き換え（書き換えは1回だけで、復元・更新の応答は書き換えないことを件数で確かめる）、本物の一覧の API の 403 `ACCESS_DENIED`、S6（`admin-forbidden-view`）、読み直し（`POST /api/auth/session/refresh` の 200）の後に「利用者の管理」のリンクが 0 件になることを確かめる。初期管理者の状態は変えない（ログインと `requestAdminAccessToken` で使うだけ）。`test.afterEach` で `userAdminDiagnostics` を失敗のときだけ添付する。
- `frontend/e2e/120-user-admin-accessibility.e2e.ts`（21 件）: 20 組×状態 (1)〜(11)（(11) はホバーとキーボードのフォーカスの2つに分けたため、組ごとに 12 回の検査）と、既定の1組の測り1件。見張り・コンソールの見張り・差し替えの口（検査のモード）は `prepareCombo`・ログインより前に張る。差し替えた失敗に出る「Failed to load resource:」は、位置の URL を正規化して管理の API の道に当たるものだけを数えて引く。終わりに `expectNoBlockedWrites`・除いた件数が失敗で返した件数以下・CSP の違反 0・残りの問題 0 を確かめる。測りは `Date.now()` の差を一覧 5 回（サイドバーでホームへ戻る）・次のページ 5 回（「前へ」で戻す）、添付と注記 `user-admin-screen-ms`（PD 5.4 の形）に数だけを残し、成否にしない。失敗のときだけ `user-admin-diagnostics` を添付する（SD 3.6 のとおり 120 にも置いた）。
- `README.md`: 「ビルドした WAR での画面の確認（E2E）」の表に 110・120 の行、「110 について」「120 について」の節を足し、最後の段落を「結果と報告」の小見出しに直した（json だけで html を作らない、trace の既定 off と `E2E_TRACE` の扱い、報告の部品の探す先と失敗の扱い、前の `frontend/playwright-report/` を消す手順、`error-context.md` で報告の部品が失敗したときの手順、手元だけ trace を有効にする手順）。090 の節の「HTML の報告と失敗のときのトレースにリンクが載る」の1行を、今の設定（HTML を作らず、トレースは手元で有効にしたときだけ）に合わせて直した。110 の監査の記述は `AuditEventType` の定義で確かめてから書いた。

#### 計画との差・気づいたこと（Step 17）

- **N-18（狭い幅で画面全体が横にはみ出していた。U5 の画面の CSS を直した）**: 120 の初回の実行で、375px の6組の状態 (1) が「横にはみ出しています（scrollWidth 524〜563px > innerWidth 375px）」で失敗した。原因は、表の「—」と見えない「なし」（`NoMark`）の見えない文字（`.useradmin-visually-hidden`、`position: absolute`）の位置の基準となる祖先が無く、表の横に動く包む要素（`mycui-table-wrapper`、`overflow-x: auto`）の外に出て文書の横の大きさを広げていたこと（一時の調べのファイルで、包む要素の外に出ている絶対配置の要素を数えて確かめた）。`UserTable.css` に `.useradmin-no-mark { position: relative; }` を足して直した（見た目は変わらない）。直した後は 20 組×12 の検査ではみ出し 0。jsdom では配置を計算しないため、画面のテストでは見つからなかった。`useradmin` の画面のテスト（14 ファイル・151 件）と Stylelint は直した後も通過。
- **N-19（確かめ・入力の表示を「やめる」で閉じた後、フォーカスが「操作」に戻らず body になる。直していない）**: 実際のブラウザでは、S3・S4 を「やめる」で閉じた後のフォーカスが `body` になる（FS W5 の 2「フォーカスはその行の『操作』へ戻す」と食い違う）。原因は make-you-chic-ui 側で、`useFocusTrap` が閉じるとき（片付け）に開く前のフォーカス（「操作」）へ戻そうとするが、その時点では `ModalStack` がまだ背景（アプリの根）に `inert` を付けており（`inert` は閉じた後の描画で外す）、`inert` の中の要素にはフォーカスを移せないため。jsdom は `inert` を扱わないため、`UserAdminPage.test.tsx` の「cancels the confirm and the edit … returns the focus to the row」は通る。あわせて、閉じた直後（`inert` が外れる前）の入力・クリックが届かないため、120 の最初の実行で状態 (6) の検索の入力が空のままになった。E2E の側は、表示を閉じた後に `body > [inert]` が 0 件になるまで待つ形にした（110 の確かめの後、120 の「やめる」・失敗の知らせ・成功の Toast の後）。画面の側で直す（閉じた後に `focusTarget` で「操作」へ戻し、`inert` が外れるのを待つ）か、make-you-chic-ui に直してもらう（片付けで戻す前に `inert` を外す、または次の描画でフォーカスを戻す）かは、承認済みの作りと違う手当てになるため、依頼者に確かめたい。既存の招待の画面の確かめの表示（取り消し）なども同じ部品のため、同じことが起きている見込み（確かめていない）。
- **N-20（押せない項目の文字は axe の color-contrast の対象にならない）**: 状態 (11) の axe の結果を一時の調べのファイルで確かめたところ、`aria-disabled="true"` の項目（項目名と理由の文）は axe の color-contrast の検査から外れる（WCAG 1.4.3 の「使えない部品の文字」の例外。passes にも violations にも出ない）。そのため 120 の状態 (11) は、押せない項目の文字のコントラストを判定していない（ほかの要素の違反が 0 であることは確かめている）。計画 8節の D-3 の「押せない項目のホバーの背景の上の文字のコントラストは 120 の状態 (11) で確かめる」は、axe では満たせない。代わりの確かめとして、ホバーしたときの押せない項目の背景が透明のまま（`rgba(0, 0, 0, 0)`、`3d9521a` の `:not([aria-disabled='true'])` のとおり）であることをブラウザで確かめ、回3 の続きで計算した値（light 4.83:1・dark 9.96:1、どちらも背景が変わらない）が実際の画面に当たることを確かめた。120 に専用の確かめ（文字と背景の色を読んで比を計算する）を足すかは依頼者に確かめたい。
- **N-21（axe の incomplete）**: 120 の検査で axe が判定できなかった（incomplete）ものは color-contrast（状態 (1) で 3〜21 件、開いたメニュー・知らせ・Toast・確かめの表示がある状態で 1〜10 件。主に、メニュー・Modal などが重なった行の Badge の背景が決められないもの）と bypass（Modal を開いた状態で1件）。どちらも違反ではなく、130 と同じく注記の `incomplete` に件数を残した。
- **N-22（状態 (9) の後の「もう一度読み込む」）**: 機能設計の R-04（検索の文字は 200 の応答のときだけ置き換える）のとおり、検索に当たらない状態から「検索を消す」で読み込みに失敗した後の「もう一度読み込む」は前の検索の文字のまま読む（検索に当たらない表示に戻る）。120 はその後に「検索を消す」で一覧へ戻す形にした（画面の作りは変えていない）。
- **N-23（110 で「確かめの表示の見出し」を役割 alertdialog の名前で探した）**: 確かめの表示（`Modal` の `role="alertdialog"`）の題は見出しの役割を持たないため、`getByRole('alertdialog', { name: 題 })` で探した。

### Step 18: E2E 110・120 の実行と差し替えの口の確かめ

| 項目 | 結果 |
|---|---|
| 前提 | Mailpit は `mastersmith-mailpit-1` がもとから動いていた（healthy、`/api/v1/info` は 200。止めない） |
| 実行 | `caffeinate -i sh -c './gradlew :backend:bootWar -q && (cd frontend && npx playwright test e2e/110-user-admin-flow.e2e.ts e2e/120-user-admin-accessibility.e2e.ts)'`（07:30〜07:33）。**22 件すべて成功**（2.6 分） |
| json の `stats` | expected 22・skipped 0・unexpected 0・flaky 0・duration 約 157.8 秒 |
| 110 | expected（約 9.1 秒）。`skip-reason` の注記なし。注記 `user-admin-flow` は lockAttempts 5・管理者の画面の問題 0・CSP の違反 0 |
| 120 の組ごと（20 件） | すべて expected（各約 6.9〜7.1 秒）。どの組も 12 回の検査で axe の違反 0・`REQUIRED_RULES` が流れた・はみ出し 0。注記 `user-admin-problems` はどの組も routeSeen 9・blocked 0・failedReplies 2（409 と通信の失敗）・除いたコンソールの表示 2・残りの問題 0・CSP の違反 0 |
| 120 の測り（1 件） | expected（約 2.0 秒）。添付 `user-admin-screen-ms`（base64 を復号）: list は first 137・rest [104, 101, 101, 102]・max 137 ミリ秒・withinTarget true（目標 2000、apiTimeIncluded true、本物の一覧の API を差し替えの口を通して読む＝口の上乗せを含む）。nextPage は first 84・rest [79, 79, 77, 78]・max 84 ミリ秒・withinTarget true（目標 1500、apiTimeIncluded false、見本の応答での描画の時間で API の時間を含まない）。記録のみで成否にしない。本番での判定は `Unverified`（持ち主は performance-validation・observability-setup・feedback-optimization） |
| 報告の部品 | 「E2E の報告に残してはならない値は含まれていません（値の種類 7・確かめたファイル: json の報告 1・json の報告の添付 1・trace 0・失敗の画面の写し 0・そのほか 0・前の html の報告 0・見つかった件数 0）」。値の種類 7 は環境変数の3つ・email・password・displayName・runTag |
| html の報告 | `frontend/playwright-report/` は作られなかった |
| 報告を消したこと | 記録した後に `frontend/test-results/` を中を開かずに消した（ファイル 2・ディレクトリ 2（根を含む））。共有していない |
| `npx playwright test --list` | 全体 13 ファイル・152 件（110 が 1 件、120 が 21 件）。作った `test-results/` は消した |

- 途中の実行（直す前）: 1回目の 110 は確かめの表示の題を見出しで探して失敗し（N-23）、報告の部品が `error-context.md` の中の U の宛先・氏名・`runTag` と初期管理者のメールアドレスを見つけて失敗の知らせを出した（設計どおり）。1回目・2回目の 120 は N-18（375px のはみ出し）と N-19（閉じた直後の入力）・N-22 で失敗し、`error-context.md` の初期管理者のメールアドレス（上の帯のメニューの名前）を報告の部品が見つけた。どれも、失敗の原因を写しで確かめた後（値は伏せた形で読んだ）に `frontend/test-results/` を消した。
- 一時の調べ（N-18・N-19・N-20 の原因の確かめ）は、追跡しない一時のファイル `frontend/e2e/991-debug-tmp.e2e.ts` で流し、流すたびに消した。

差し替えの口の3つの確かめ（SD 4.3）は、追跡しない一時のファイル `frontend/e2e/990-route-guard-check.e2e.ts` に書き、`expectNoBlockedWrites` が失敗することとその理由を確かめる形で流した（3 件とも期待どおり。json の `stats` は expected 3）。

| 形 | 口の記録 | `expectNoBlockedWrites` の失敗の理由 |
|---|---|---|
| わざと POST を送る（見本の無い「利用を止める」を画面から実行） | seen 2・打ち切り `POST /api/admin/users/{id}/suspend` 1 件 | 打ち切りの記録 |
| 口を張らない（口を使わない別のページに張り、画面のページは口なしで一覧を開く） | seen 0・打ち切り 0 | 受けた件数（1 以上でない） |
| 二重のスラッシュの道（`//api/admin/users/1/suspend` と `/api//admin/users/1/resume` へ `fetch` で POST） | seen 2・打ち切り `POST /api/admin/users/{id}/suspend`・`POST /api/admin/users/{id}/resume` の 2 件 | 打ち切りの記録 |

- 流した後に一時のファイルと `frontend/test-results/` を消した。`git status --short`・`git diff --stat` で、一時のファイル（990・991）と確かめの変更が作業フォルダに残っていないことを確かめた（変わっているのは Step 5〜18 の変更と記録だけ）。
- 型の検査・Prettier・oxlint・ESLint・ライセンスヘッダーを、作った・変えたファイルに流して通過（Step 19 の正式の確かめは次の回）。

### 回4 の終わりの作業フォルダ

- 新しいファイル: `frontend/e2e/110-user-admin-flow.e2e.ts`・`120-user-admin-accessibility.e2e.ts`、`frontend/e2e/support/` の6ファイル（`traceMode.ts`・`secretValues.ts`・`userAdminRun.ts`・`userAdminFixtures.ts`・`adminApiRoute.ts`・`userAdminDiagnostics.ts`）。
- 変えたファイル: `frontend/playwright.config.ts`・`frontend/playwright-secret-check-reporter.ts`・`README.md`・`frontend/src/features/useradmin/UserTable.css`（N-18）。
- 記録: この節と、`code-generation-plan.md` の Step 4 の残り（4 項目）・Step 15〜18 のチェック。
- `git add`・コミットはしていない。`frontend/test-results/`・`frontend/playwright-report/` は手元に無い。リポジトリの外の一時の場所（`$HOME/tmp-u5-bundle`・`$HOME/tmp-u5-reporter-check`）は消した。

## 回5（Step 19・20）— 2026-10-03

依頼者の決定（回4 の後）:

- **N-19**: make-you-chic-ui の側で直す（依頼者が直す。依頼の文書は `make-you-chic-ui-request-3.md`）。直った版へ上げるのは C2 とは別の専用のコミット C2′ にする。直るまでの間は、今の E2E の形（`body > [inert]` が外れるのを待つ）のままでよい。直った後に、閉じた後のフォーカスが行の「操作」に戻ることを E2E で確かめる形に替えるかを決める。
- **N-20**: 受け入れて記録だけにする（押せない項目の文字のコントラストの専用の確かめは足さない）。
- **N-18**（`UserTable.css` の `.useradmin-no-mark { position: relative; }` の1つの規則）と **N-15**（Step 4 の確かめを U5 の変更を含む作業フォルダで流した扱い）: 受け入れ。計画との差として記録する。

サブモジュールは `3d9521aa54b1d6277de473f9e935a496fb56ac1b` のまま（中の `git status --porcelain` は空）。

### Step 19: 画面の静的検査と構造の確かめ

| 確かめ（手順書 2.8） | 結果 |
|---|---|
| `npm run typecheck`（`e2e` と見本の型を含む） | 誤り 0 件 |
| `npx prettier --check`（手順書 2.8 の名指しのフォルダーとファイル） | 通過（`.npmrc` は Prettier が形式を推定できず対象の外のため、名指しから外した。回3 と同じ扱い） |
| `npx oxlint`（名指し） | 終了コード 0 |
| `npx eslint`（名指し） | 終了コード 0 |
| `npx stylelint "src/features/useradmin/**/*.css"` | 通過 |
| `node scripts/check-license-header.mjs` | 「すべてのファイルにヘッダーがあります」 |
| 参考: 全体の `npm run format:check`・`npx oxlint .`・`npx eslint .` | どれも終了コード 0 |
| `npx playwright test --list` | 110 が 1 件、120 が 21 件、全体が 13 ファイル・152 件 |
| `E2E_TRACE=bogus npx playwright test --list e2e/110-user-admin-flow.e2e.ts` | 終了コード 1（「E2E_TRACE は off・on・retain-on-failure のどれかにしてください（指定: "bogus"）。」） |
| `--list` の後 | `frontend/playwright-report/` は作られない。作られた `frontend/test-results/`（json だけ）は消した |

画面の側の境界:

- `grep -rn "features/\(invitation\|preferences\|admin\|dsl\|auth\|registration\)" frontend/src/features/useradmin` は空。
- `frontend/src/shared`・`frontend/src/app` に `features/useradmin` への読み込みは無い。
- 新しい E2E と手伝いが画面のコード（`frontend/src`）を読むのは `userAdminFixtures.ts` の `import type { AdminUser, AdminUserPage }` の1か所だけ（型だけ）。

4.5 の差分が無いことの確かめ（`git diff --stat 21a2fdd -- <パス>`）:

- `backend`・`frontend/package.json`・`vitest.config.ts`・`vite.config.ts`・`eslint.config.js`・`tsconfig.json`・`frontend/src/app`・`frontend/src/features/{invitation,admin,dsl,auth,registration}`・`frontend/src/shared/{paging,format,validation}`・`frontend/src/shared/api-client/{apiClient.ts,apiError.ts,adminForbidden.ts}`・`frontend/e2e/0*.e2e.ts`・`130-admin-forbidden-accessibility.e2e.ts`・`.github`・`docker`・`compose.yaml`・`gradle`・`build.gradle.kts` は差分なし。
- `frontend/package-lock.json` も差分なし。
- 既存の `frontend/e2e/support/` の 13 ファイルは、`preferencesFixtures.ts` の読み込みの1行（N-6 の案 A、依頼者の決定済み）だけが差分。
- `vendor/make-you-chic-ui` はルートの差分が gitlink（`077f5b48…` → `3d9521aa…`）だけ。サブモジュールの作業フォルダの `git status --porcelain` は空。verify の `vendorUnchanged` は Step 22 で確かめる。

影響の洗い出し（実装の後にもう一度）:

- `frontend/src` のテストで、実際の登録（`registrationModules`・`import.meta.glob`）を読むものは無い（回1 と同じ）。
- 既存の E2E でリンクの数を数えるものは無い。
- `app-shell` の中の `dropdown-trigger` を押すのは 080 の 232 行目と `registeredUser.ts` の `openUserMenuItem` だけ。どちらも利用者の管理の画面の外で使う。110 は U のホームの画面でだけ `openUserMenuItem` を使う。
- Step 4 と Step 18 の E2E（サイドバーに「利用者の管理」が出た状態）で 010〜100・130 の 130 件と 110・120 の 22 件が通っている。期待を変える必要のある既存のテスト・E2E は無い。

### Step 20: コードのレビューでの確かめ（検索で確かめた。レビュー役の起動はしていない）

| 項目 | 確かめた方法と結果 |
|---|---|
| 失敗の入口の1か所（NFR1.2、SD 2.2） | API を呼ぶ道は3つ（一覧の `load`、操作の `confirmAction`、保存の `saveEdit`）で、どれも `catch` から `handledCommonly(error, path)`（401 → `useAdminForbidden` の順）を通る。一覧は `handleListFailure` の先頭で、操作・保存は `catch` の先頭で呼ぶ。`useradmin` の本体（テストと `testing/` を除く）に、`403`・`ACCESS_DENIED` を自分で比べる式は無い（当たったのは説明文だけ） |
| 画面の外に出さない（NFR3.1、SD 6.1） | `console.`・`localStorage`・`sessionStorage`・`history.`・`location.` は本体に 0 件 |
| 文言と項目（NFR3.2・NFR3.3） | `.detail`・問題の `.title` を読む式は 0 件（当たったのは文言の鍵と `Modal` の `title` の props だけ）。`dangerouslySetInnerHTML`・要素の `style={` は 0 件。一覧の本文は `userAdminApi.ts` で C3 の項目だけを写して返す（回3 の Step 9・10 のテストで確かめ済み） |
| 参照の戻り（PD 3.2） | `loadingRef` は `load` の `finally` で戻す。戻すのは最後に始めた読み込みのときだけ（後から始めた読み込みが参照を持つため）。`submittingRef` は `beginSubmit` で立て、`confirmAction`・`saveEdit` の `finally` の `endSubmit()` で戻す。戻ることは `UserAdminPage.test.tsx` の (a)〜(d) で確かめ済み |
| 自動の読み直しが無い（NFR5.4） | `setInterval` は 0 件。`setTimeout` は `beginSubmit` の 5 秒の「時間がかかっています」の1か所だけで、`endSubmit` と画面を離れたときに `clearTimeout` で解く |
| 報告と資格情報（NFR3.4、SD 3.3） | 110・120 の `test.step` の題は英語の決まった文だけ。注記（`user-admin-flow`・`axe`・`user-admin-problems`・`user-admin-screen-ms`）は件数・組と状態の名前・規則の名前・時間だけ。`expect` の説明文と locator の名前・期待値に、宛先・パスワード・`runTag`・氏名の変数を入れていない（読み上げの名前は比べた真偽だけを渡す）。`playwright.config.ts` の差分は報告の部品の設定と trace だけで、`webServer.env` は変えていない。120 は `request` の口を使っていない（説明文だけ）。Step 18 の報告の部品の結果は「見つかった件数 0」 |
| 8KB を超える要求（U2 の申し送り、Q-C A） | 決定のとおり、作りは足していない（画面は検索の文字を 254 コードポイントで止める）。code の無い 400 の扱いは、一覧は `UserAdminPage.test.tsx`「treats a 400 of the first load and a 400 without a code as a load error」、操作は「shows the general message for an unknown code, a 400 without a code, …」、保存は `failureMessage.test.ts`（`failureMessageKey(apiError(400), 'save')` が `useradmin.edit.failed`）で確かめている（保存は関数のテストだけで、画面のテストは 500 で一般の失敗を確かめている。下の N-25） |

#### 計画との差・気づいたこと（Step 19・20）

- **N-24（失敗の入口の名前）**: 計画 4.2・Step 13 は失敗の入口を `handleFailure(error, path, context)` と書いたが、実装は `handledCommonly(error, path)`（401 と 403 の共通の扱い）と、一覧の場面の `handleListFailure` に分けた。操作・保存の場面ごとの扱いは各関数の `catch` の中にある（回3 の実装のまま）。順（401 → 403 → 場面ごと）と「API を呼ぶ道がすべて通る」形は計画と同じ。説明文（`useUserAdmin.ts` の 28 行目、`failureMessage.ts` の 20 行目）に古い名前 `handleFailure` が残っていたため、今の名前に直した（説明文だけで、ふるまいは変わらない。型の検査と Prettier は通過）。
- **N-25（保存の code の無い 400）**: Q-C の決定は「code の無い 400 のテスト（一覧・操作・保存）で確かめる」。保存は画面のテストではなく関数のテスト（`failureMessageKey`）で確かめている。画面の保存の失敗の道は、500 で入力を残して failed を出すテストで確かめている（同じ道）。画面のテストを1件足すかは依頼者に確かめたい。

### Step 20 のレビューに渡す範囲（オーケストレーターが依頼する）

U5（B5）で作った・移した・変えたファイル（記録は除く）:

- 本体: `frontend/src/features/useradmin/` の本体 18（`api/types.ts`・`api/userAdminApi.ts`・`rowActions.ts`・`searchInput.ts`・`profileInput.ts`・`lockedUntil.ts`・`failureMessage.ts`・`focusTarget.ts`・`messages.ts`・`useUserAdminText.ts`・`UserSearchBox.tsx`・`UserTable.tsx`・`UserRowActions.tsx`・`ConfirmActionDialog.tsx`・`EditProfileDialog.tsx`・`useUserAdmin.ts`・`UserAdminPage.tsx`・`registration.ts`）と CSS 4（`UserSearchBox.css`・`UserTable.css`・`EditProfileDialog.css`・`UserAdminPage.css`）
- 画面のテストと補助: `frontend/src/features/useradmin/` のテスト 14 と `testing/fixtures.ts`・`testing/renderUserAdmin.tsx`
- 共通への移し: `frontend/src/shared/api-client/fieldErrors.ts`・`fieldErrors.test.ts`（`frontend/src/features/preferences/` から移した）、`frontend/src/features/preferences/usePreferencesForm.ts`・`usePasswordChangeForm.ts`（読み込みの1行ずつ）
- E2E: `frontend/e2e/110-user-admin-flow.e2e.ts`・`120-user-admin-accessibility.e2e.ts`、`frontend/e2e/support/traceMode.ts`・`secretValues.ts`・`userAdminRun.ts`・`userAdminFixtures.ts`・`adminApiRoute.ts`・`userAdminDiagnostics.ts`、`frontend/e2e/support/preferencesFixtures.ts`（読み込みの1行、N-6）
- 設定と報告の部品: `frontend/playwright.config.ts`・`frontend/playwright-secret-check-reporter.ts`
- リポジトリの作業（コミット済み）: `frontend/.npmrc`（C1 `17af97d`）・`vendor/make-you-chic-ui` の gitlink（C2 `364e9d6`）
- 文書: `README.md`
- 照らし合わせる設計: 計画 `code-generation-plan.md`・`unit-test-instructions.md`、この `generation-notes.md`、FS・FC・SD・PD・LC・CP（計画 1節）

### 回5 の終わりの作業フォルダ

- 変えたもの（この回）: `frontend/src/features/useradmin/useUserAdmin.ts`・`failureMessage.ts` の説明文の名前（N-24）。
- 記録: この節と、`code-generation-plan.md` の Step 19・20 のチェック。
- `git add`・コミットはしていない。`frontend/test-results/`・`frontend/playwright-report/` は手元に無い。

## 回5 の続き（N-25 のテストの追加）— 2026-10-03

依頼者の決定（回5 の後）:

- **N-25**: 保存の code の無い 400 を確かめる画面のテストを1件足す（一覧・操作と同じく、一般の失敗として扱うことを画面で確かめる）。
- **N-24**: 計画との差として記録して受け入れ（失敗の入口を `handledCommonly` と `handleListFailure` に分けた形）。

### 足したテスト

- `frontend/src/features/useradmin/UserAdminPage.test.tsx` に「treats a save 400 without a code (an HTML 400) as a general failure」を1件足した（`describe` は既存の保存のまとまり）。
- 中身: 保存の API が code の無い 400 を返したとき、入力の表示に一般の失敗の文言「保存できませんでした。…」が出る。入力は残り、氏名の欄に `aria-invalid` が付かない（項目の誤りにしない）。「保存」は押せるまま。`detail` の目印は画面に出ない。一覧は読み直される。
- 本体は変えていない（既存の作りのまま通った）。

### 確かめの結果

| 確かめ | 結果 |
|---|---|
| `useradmin` の画面のテスト（`npx vitest run src/features/useradmin`） | 14 ファイル・**152 件**すべて成功（151 件から1件増えた。`UserAdminPage.test.tsx` は 58 件） |
| `npm run typecheck` | 誤り 0 件 |
| Prettier・oxlint・ESLint（`src/features/useradmin`） | どれも終了コード 0 |
| `npm run test:coverage`（画面のテストの全体） | 109 ファイル・**953 件**すべて成功。全体のカバレッジ 行 97.43%（2882/2958）・分岐 92.85%（1870/2014）（下限 行 80%・分岐 70% を満たす） |
| `useradmin` の下の合計（`testing/` を含む。回3 と同じ数え方） | 行 96.49%（468/485）・分岐 92.17%（365/396）。回3 と同じ値（足したテストは 500 のテストと同じ道を通るため、カバレッジは増えない） |
| 参考: `useradmin` の本体だけ（`testing/` を除く） | 行 96.38%（453/470）・分岐 91.84%（349/380） |
| 目安（行・分岐とも 90%）を下回るファイル | 回3 と同じ4つ（`useUserAdmin.ts` 分岐 85.06%、`UserRowActions.tsx` 分岐 83.33%、`lockedUntil.ts` 分岐 87.5%、`profileInput.ts` 分岐 75.0%。理由は回3 の記録のとおり） |

- `frontend/coverage/` は `.gitignore` の対象。`git add`・コミットはしていない。

### レビューに渡す範囲の確かめ

回5 の「Step 20 のレビューに渡す範囲」は最新のまま。この回で変えたのは `frontend/src/features/useradmin/UserAdminPage.test.tsx`（一覧の「画面のテスト 14」に入っている）だけで、新しいファイルは無い。

## 回6（Step 21 の1項目目: 記録の3ファイル）— 2026-10-03

- `code-summary.md`・`source-manifest.json`・`traceability.json` を作った。コミットの提案などの Step 21 のほかの項目には進んでいない。`git add`・コミットはしていない。
- `source-manifest.json`: 56 件。U5 で作った・移した・変えたアプリのソース・テスト・E2E・設定・README と、`frontend/.npmrc`・`vendor/make-you-chic-ui`（gitlink）を入れた。記録（`aidlc/` の下）は入れていない。移した元の `frontend/src/features/preferences/fieldErrors.ts`・`fieldErrors.test.ts`（消えたファイル）は、ファイルが無いため一覧に入れず、`code-summary.md` の 1.2・1.3 に移したことを書いた。
- `traceability.json`: upstream_ids 116（機能設計の AC 67・NFR 設計の NFR 28・D1〜D20・E2E-M9）、OK 63・Deferred 53・GAP 0。OK の target はすべて実在するファイル1つ。
  - Deferred の target は、機能設計の `traceability.json` の持ち主（U3・U4・U1）の書き方を単位の名前（`u3-user-admin-api` など）に置き換えたもの。
  - AC3.2.9・AC3.2.10 は u1-user-suspension を主とし、110 の順7 で画面の流れを確かめたことを書いた。
  - NFR5.1・NFR5.2 は「記録のみ・本番での判定は Unverified」として、持ち主の段に Deferred にした。
- `code-summary.md` の「依頼者に確かめたいこと」と「承認の場で確かめること」は、決定済みと未決を分けて並べた。N-19 は両方の未決に「make-you-chic-ui の直し待ち。直った版へ C2′ で上げ、閉じた後のフォーカスを E2E で確かめる形に替えるかはその時に決める」と書いた。C2′ の後の差は、この `generation-notes.md` に記録する前提で書いた。

## レビューの後（Iteration 1、READY）— 2026-10-03

レビュー（`aidlc-architecture-reviewer-agent`、READY、Major 1・Minor 4）の記録は `.aidlc-reviews/code-generation/units/u5-user-admin-ui/fad218ee222aa49d/1.json` にある（読むだけで、書き換えていない）。`code-summary.md`・`traceability.json`・`source-manifest.json` はレビューの後のため書き換えていない。

### 依頼者の決定（推奨どおり）

| ID | 重さ | 指摘の要点 | 決定 |
|---|---|---|---|
| R-01 | Major | 実際のブラウザでは、確かめ・入力の表示を閉じた後のフォーカスが行の「操作」に戻らない（N-19）。それなのに W5 に結んだ AC が、jsdom のテストだけを根拠に OK になっている | 記録して C2′ で解く。下の「条件つきの AC」のとおり |
| R-02 | Minor | E2E が `body > [inert]` が消えるのを待つ形のため、N-19 が E2E で見えない | 記録して C2′ で解く。C2′ で E2E を、`inert` が外れるのを待つ形から「閉じた後にフォーカスが行の『操作』に戻る」ことを確かめる形に替える予定 |
| R-03 | Minor | 110 の `chooseRowAction` は、操作の直前に、対象の行が U の行であることを確かめていない | 押す前に、その行が U の行であることを確かめる |
| R-04 | Minor | `EditProfileDialog.tsx` の `languageErrorId` がどこからも結ばれていない。README の「20 組×11 の状態」が実装（12 回の検査）と合わない | id を外す。README を 12 回の検査に合わせる |
| R-05 | Minor | 送信中も氏名の欄に入力できる | 送信中は氏名の欄を読み取り専用にし、テストを足す |

### 条件つきの AC（R-01・R-02）

- `traceability.json` で OK とした AC2.1.8・AC3.1.7・AC4.1.9・AC5.1.7（機能設計 W5・W7 の確かめ・入力の表示）は、**C2′ の後に実際のブラウザの E2E で確かめるまで条件つき**とする。
- 条件の中身は W5 の 2（閉じた後のフォーカスを行の「操作」へ戻す）である。make-you-chic-ui の N-19 の直し（`make-you-chic-ui-request-3.md`）を C2′ で取り込んだ後に確かめる。
- C2′ では、E2E（110 の `confirmAction`、120 の `expectBackgroundInteractive`）を、`inert` が外れるのを待つ形から「閉じた後にフォーカスが行の『操作』に戻る」ことを確かめる形に替える予定。
- C2′ の後の確かめと差は、この `generation-notes.md` に記録する。
- レビューは、キーボード・読み上げの利用者に影響するため C2′ を次の Bolt に持ち越さないよう求めている。

### 直したこと

| ID | 直した所 | 中身 |
|---|---|---|
| R-03 | `frontend/e2e/110-user-admin-flow.e2e.ts` | `chooseRowAction(page, userId, item)` にした。押す前に、一覧がちょうど1行で、その行に `useradmin-row-actions-<userId>` があることを確かめる（真偽だけを `expect` に渡す）。3つの呼び出し（ロックの解除・利用を止める・停止を解く）に、順2 で取った U の `userId` を渡す |
| R-03（直す途中で見つかった N-26） | 同上の `searchUsers` | 下の N-26 のとおり。待つ応答を、検索の文字（`q`）が入れた文字と同じ一覧の応答だけに絞った |
| R-04 | `frontend/src/features/useradmin/EditProfileDialog.tsx`・`README.md`・`frontend/e2e/120-user-admin-accessibility.e2e.ts` | `languageErrorId`（と使わなくなった `useId` の読み込み）を外し、結べない理由（N-9）を説明文に残した。README の 120 の行を「20 組 × 12 回の検査（11 の状態で、押せない項目はホバーとキーボードのフォーカスを分けて検査する）」に直した。120 の先頭の説明文も 12 回の検査に合わせた |
| R-05 | `EditProfileDialog.tsx`・`EditProfileDialog.test.tsx` | 送信中（`status === 'submitting'`）は氏名の欄を `readOnly` にした。テストを2件足した。1件目は送信中は `readonly` で、入力しても値が変わらないこと。2件目は送信中でなければ `readonly` が付かないこと |

- **N-26（R-03 を直す途中で見つかった 110 の不具合。直した）**:
  - 何が起きていたか: 110 の順2 の `searchUsers` は、`GET /api/admin/users` の最初の応答を待っていた。画面を開いたときの全体の一覧（`q` なし）の応答が、検索の応答より後に届くと、それを U の検索の応答と取り違えうる。
  - 見つけた経緯: R-03 の確かめを足した1回目の実行で、行は U の1行なのに、取った `userId` がその行と一致せず失敗した。取った `userId` は、全体の一覧の先頭の行（初期管理者と見られる）のものだった。
  - 前の実行への影響: Step 18 などの前の実行の順6（409 の照らし合わせ）でも、初期管理者に「失敗回数を戻す」要求を送っていた可能性がある。送っていたとしても、初期管理者のロックは無いため 409 `USER_ADMIN_NO_CHANGE` で拒否され、状態は変わらない（拒否の監査が E2E の一時の内部DB に1件残るだけ）。そのため、照らし合わせの結果（409 の形の一致）は変わらない。
  - 直し方: `searchUsers` が待つ応答を、検索の文字（`q`）が入れた文字と同じものに絞った（比べるのは URL の問い合わせの値どうしで、値は出さない）。あわせて、R-03 の確かめで、操作の前に行が U の行であることを確かめる。
  - 1回目の失敗の報告は、json から失敗の内容（`expect` の説明文と診断の添付）を読んだ後に `frontend/test-results/` を消した。報告の部品は `error-context.md` の値（設計どおり）を見つけて失敗の知らせを出していた。
- R-01・R-02 の `test.fixme` は足していない（依頼者の決定は「記録して C2′ で解く」）。

### 直した後の確かめ

| 確かめ | 結果 |
|---|---|
| `useradmin` の画面のテスト | 14 ファイル・**154 件**すべて成功（152 件に R-05 の2件を足した。`EditProfileDialog.test.tsx` は 10 件） |
| 型（`npm run typecheck`）・Prettier・oxlint・ESLint（`src/features/useradmin`・`e2e`・`playwright.config.ts`・報告の部品）・Stylelint（`useradmin` の CSS）・ライセンスヘッダー | すべて通過 |
| 画面のテストの全体（`npm run test:coverage`） | 109 ファイル・**955 件**すべて成功。全体 行 97.42%（2881/2957）・分岐 92.85%（1870/2014）（下限を満たす） |
| `useradmin` の下の合計（`testing/` を含む） | 行 96.49%（467/484）・分岐 92.17%（365/396）。目安を下回るのは回3 と同じ4つ（分岐: `useUserAdmin.ts` 85.06%・`UserRowActions.tsx` 83.33%・`lockedUntil.ts` 87.5%・`profileInput.ts` 75.0%） |
| E2E の前提 | Mailpit は `mastersmith-mailpit-1` がもとから動いていた（healthy、`/api/v1/info` は 200。止めない） |
| E2E 110・120（1回目、08:01〜08:03） | 21 件成功・110 が失敗（N-26。R-03 の確かめで見つかった）。報告は記録してから消した |
| E2E 110・120（直した後、08:04〜08:07、`caffeinate -i sh -c './gradlew :backend:bootWar -q && (cd frontend && npx playwright test e2e/110-user-admin-flow.e2e.ts e2e/120-user-admin-accessibility.e2e.ts)'`） | **22 件すべて成功**（2.6 分）。`stats` は expected 22・skipped 0・unexpected 0・flaky 0・duration 約 158.7 秒 |
| 110 | `skip-reason` なし。注記 `user-admin-flow` は lockAttempts 5・管理者の画面の問題 0・CSP の違反 0 |
| 120 | 20 組×12 回＝240 回の検査で axe の違反 0・はみ出し 0。打ち切り 0・残りの問題 0・CSP の違反 0 |
| `user-admin-screen-ms`（記録のみ） | list は first 101・rest [99, 100, 100, 121]・max 121 ミリ秒（目標 2000、口の上乗せを含む）。nextPage は first 83・rest [79, 77, 78, 76]・max 83 ミリ秒（目標 1500、API の時間を含まない）。本番での判定は `Unverified` |
| 報告の部品 | 「値の種類 7・json の報告 1・json の報告の添付 1・trace 0・失敗の画面の写し 0・そのほか 0・前の html の報告 0・見つかった件数 0」 |
| 報告の片付け | `frontend/playwright-report/` は作られない。記録した後に `frontend/test-results/` を中を開かずに消した（ファイル 2・ディレクトリ 2）。共有していない |

- `git add`・コミットはしていない。この節で変えたアプリのファイル: `frontend/e2e/110-user-admin-flow.e2e.ts`・`frontend/e2e/120-user-admin-accessibility.e2e.ts`（説明文）・`frontend/src/features/useradmin/EditProfileDialog.tsx`・`EditProfileDialog.test.tsx`・`README.md`。どれも `source-manifest.json` に入っている。

## Step 22（C2′ の前）— 2026-10-03

対象は作業ブランチ `feature/260930-user-admin-b5` の先頭 `81d2423`（C1 `17af97d`・C2 `364e9d6`・C3 `519a69b`・C4 `afa7cb2`・C5 `2e86a4f`・C6 `3e9d12a`・R2 `81d2423`）。サブモジュール `vendor/make-you-chic-ui` は `3d9521aa54b1d6277de473f9e935a496fb56ac1b`、中の `git status --porcelain` は空。作業フォルダの未コミットの変更は監査ログ（`aidlc/` の下）だけ。

**この節の結果は C2′（make-you-chic-ui の N-19 の直しを取り込む固定先の更新）の前の関門の結果である。** make-you-chic-ui の N-19 の直しはまだ公開の側に入っていない（`origin/main` は `3d9521a` のまま）。そのため `code-generation-plan.md` の Step 22 のチェックは付けず、この記録だけにした（C2′ の後の確かめを終えてから付ける）。`code-summary.md` はレビューの後のため書き換えていない。

### 関門の結果

| 項目 | 結果 |
|---|---|
| 前提 | colima が動いている（`colima status`）。`DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` をシェルに渡した。`frontend/test-results/`・`frontend/playwright-report/` は始める前に無かった |
| `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` | **BUILD SUCCESSFUL**（9 分 2 秒、08:38〜08:47） |
| バックエンドの件数（JUnit の XML から） | 単体 173 クラス・**1506 件**、結合 136 クラス・**689 件**。失敗・誤り・飛ばしたもの 0（対象DB のテストは SKIPPED になっていない。`DslTargetDbIT`・`TargetDbStartupIT` などは 08:46 に実行された） |
| バックエンドのカバレッジ（`jacocoTestReport.xml` の全体） | 行 **98.90%**（6276/6346）・分岐 **94.83%**（2331/2458）。Step 3 の基準と同じ（U5 はバックエンドを変えていない） |
| 画面のテスト | **109 ファイル・955 件**すべて成功 |
| 画面のカバレッジ（`frontend/coverage/coverage-summary.json` の全体） | 行 **97.42%**（2881/2957）・分岐 **92.85%**（1870/2014）。下限（行 80%・分岐 70%）を満たす。B2 の基準 行 97.61%・分岐 93.01% からの下がりは 0.19・0.16 ポイント |
| `useradmin` の下の合計（`testing/` を含む） | 行 96.49%（467/484）・分岐 92.17%（365/396） |
| SpotBugs の関門・Gitleaks | 通過（SpotBugs は警告だけ） |
| 初回の JavaScript の大きさ (3) | **125.5 KB（gzip）**（`assets/index-*.js` 108.8 KB・`assets/useTranslation-*.js` 16.7 KB）。`verify` の中の `frontendBundleSize` の値。(1) 122.7 KB・(2) 122.8 KB からの増えは 2.7 KB（U5 の登録と共通へ移した部品の分と見込む。画面そのものは `lazy`。内訳は確かめていない）。回4 の参考の値（作業フォルダ）と同じ。目安 500 KB の内 |
| `caffeinate -i ./gradlew osvScan --rerun-tasks` | **BUILD SUCCESSFUL**（08:47〜08:48）。走査したパッケージ: `backend/gradle.lockfile` 252・`frontend/package-lock.json` 382・`vendor/make-you-chic-ui/package-lock.json` 404（走査の外の手元のパッケージ 3）。**失敗の条件に当たるもの 0 件、警告 16 件**（すべて npm の開発用: `braces@3.0.3` が frontend と vendor に各1、vendor の `brace-expansion@5.0.9` 3・`undici@8.10.0` 11） |
| Mailpit | `mastersmith-mailpit-1` がもとから動いていた（healthy、`/api/v1/info` は 200）。起動していないため止めない |
| `caffeinate -i ./gradlew e2eTest` | **BUILD SUCCESSFUL**（6 分 13 秒、08:48〜08:54）。Playwright は 152 passed（6.2 分） |
| json の `stats` | expected **152**・skipped 0・unexpected 0・flaky 0・duration 約 371.6 秒 |
| ファイルごと（すべて expected） | 010: 2・020: 2・030: 1・040: 1・050: 21・060: 21・070: 20・080: 21・090: 1・100: 20・110: 1・120: 21・130: 20（**13 ファイル・152 件**） |
| 110 | expected（約 7.0 秒）。`skip-reason` の注記なし。注記 `user-admin-flow` は lockAttempts 5・管理者の画面の問題 0・CSP の違反 0 |
| 120 の組ごと（20 件） | すべて expected。20 組×12 回＝**240 回の検査で axe の違反 0**（違反の規則なし）・はみ出し 0、12 の状態すべてを検査した。注記 `user-admin-problems` はどの組も routeSeen 9・blocked（GET 以外の打ち切り）0・failedReplies 2（409 と通信の失敗）・除いたコンソールの表示 2・残りの問題 0・CSP の違反 0。120 の全体の時間は 21 件の合計で約 137.4 秒 |
| 120 の測り（`user-admin-screen-ms`、base64 を復号した数だけ） | list は first 98・rest [103, 63, 48, 46]・max 103 ミリ秒・withinTarget true（目標 2000、apiTimeIncluded true、口の上乗せを含む）。nextPage は first 65・rest [57, 79, 80, 60]・max 80 ミリ秒・withinTarget true（目標 1500、apiTimeIncluded false、見本の応答での描画の時間で API の時間を含まない）。記録のみで成否にしない。本番での判定は `Unverified` |
| 報告の部品 | 「E2E の報告に残してはならない値は含まれていません（値の種類 7・確かめたファイル: json の報告 1・json の報告の添付 299・trace 0・失敗の画面の写し 0・そのほか 0・前の html の報告 0・見つかった件数 0）」。値のファイルは `test-results/` の中にあり、下のとおり消した |
| html の報告 | `frontend/playwright-report/` は作られなかった |
| 報告を消したこと | 記録した後に `frontend/test-results/` を中を開かずに消した（ファイル 2・ディレクトリ 106（根を含む））。共有していない。json から読んだのは `stats`・テストの題・状態・時間・注記と添付 `user-admin-screen-ms` だけ |
| 失敗と再実行 | 失敗なし。再実行していない |

- 作業フォルダ: 関門の後の `git status --short` は監査ログの1行だけ（関門の実行で追跡するファイルは変わらなかった）。手元の一時のログ（ホームの下）は消した。`git add`・コミットはしていない。Step 23 には進んでいない。

### C2′ の後に流し直すもの

C2′ は `vendor/make-you-chic-ui` の gitlink だけを上げるコミット（N-19 の直しを含む版へ）と、それに伴う E2E の書き換え（110 の `confirmAction`・120 の `expectBackgroundInteractive` を「閉じた後にフォーカスが行の『操作』に戻る」ことを確かめる形へ。R-01・R-02）を前提とする。バックエンドのソース・lockfile は変わらないため、バックエンドの単体・結合テストとカバレッジ（上の 1506 件・689 件・行 98.90%・分岐 94.83%）は流し直さない前提とする。流し直すのは次のとおり。

1. 固定先の確かめ: サブモジュールの中の `git status --porcelain` が空、更新前後のハッシュ（`3d9521a` → 新しいハッシュ）を記録、取り込むコミットが make-you-chic-ui の公開の側にあるか（`git branch -r --contains`）を記録する。
2. 依存: `(cd frontend && npm ci)` と、`frontend/package-lock.json`・`vendor/make-you-chic-ui/package-lock.json` の差の有無。
3. vendor のビルド: `./gradlew vendorBuild --rerun-tasks`（`vendorInstall`・`vendorBuild`・`vendorUnchanged` を含む）。`dist` に直しが入ったことを確かめる。
4. 画面の静的検査とテスト: 型・Prettier・oxlint・ESLint・Stylelint・ライセンスヘッダー（書き換えた E2E のファイルを含む）、画面のテストの全体とカバレッジ（上の 109 ファイル・955 件・行 97.42%・分岐 92.85% と比べる）。実際には、`verify` の画面の段をまとめて流す形として `./gradlew verify` の流し直しを勧める（バックエンドのテストは UP-TO-DATE で飛ぶことがあるが、C2′ で変わらないため扱いは上の結果のまま。`:backend:cleanTest` は付けない）。Gitleaks も新しいコミットを含めて流れる。
5. 初回の JavaScript の大きさ (4): `frontendBundleSize`（上の 125.5 KB と比べる）。
6. `./gradlew osvScan --rerun-tasks`: vendor の lockfile が変われば必須。変わらなくても、走査の数と警告の件数（上の 252・382・404、失敗 0・警告 16）を比べて記録する。
7. E2E の全体: `caffeinate -i ./gradlew e2eTest`（13 ファイル・152 件。件数が変わるなら理由を記録）。特に 110・120 で閉じた後のフォーカスの確かめが通ること（AC2.1.8・AC3.1.7・AC4.1.9・AC5.1.7 の条件を外す根拠）、120 の axe・はみ出し・CSP、報告の部品、`test-results/` を消すこと。make-you-chic-ui の部品はほかの画面でも使うため、010〜100・130 も同じ実行で流し直す。
8. E2E の書き換えの後に、`frontend/` の外や `backend/` に変更が出たときは、この前提を外し、Step 22 の全体（`:backend:cleanTest :backend:cleanIntegrationTest verify`・`osvScan`・`e2eTest`）を流し直す。

## 依頼者の決定: N-19 の直しを待たずに統合する（2026-10-03）

- make-you-chic-ui の N-19 の直し（`make-you-chic-ui-request-3.md`）は、Step 22 の後の時点でまだ公開の側に無かった（`git ls-remote origin main` の先頭は `3d9521aa54b1d6277de473f9e935a496fb56ac1b` のまま）。
- 依頼者の決定「先に統合する」で、C2′ を待たずに、今の作業ブランチ（固定先 `3d9521a`）のまま `develop` へ fast-forward で統合する。Step 22 の結果（上の「Step 22（C2′ の前）」）を統合の前の関門の結果とする。
- N-19 は後の Intent へ回す。AC2.1.8・AC3.1.7・AC4.1.9・AC5.1.7 は、実際のブラウザでは閉じた後のフォーカスが行の「操作」に戻らず body に移るため、「条件つき（N-19、後の Intent）」のまま残る。後の Intent で、make-you-chic-ui の直った版へ固定先を上げる専用のコミットと、E2E 110 の `confirmAction`・120 の `expectBackgroundInteractive` を「閉じた後にフォーカスが行の『操作』に戻る」ことを確かめる形に替える作業（R-01・R-02）を行う。
- 上の「C2′ の後に流し直すもの」は、その後の Intent で固定先を上げるときの確かめの手順として使う。
- 計画の Step 22 の確かめの結果は、計画の書き方（`code-summary.md` に記録）と違い、この `generation-notes.md` に記録した（`code-summary.md` はレビューの後のため書き換えない）。

## Step 23: 統合（fast-forward）

- `develop` は計画の時点の `21a2fdd` から、記録だけのコミット 37a5566・5624d86・3f6ae9b（計画・計画の決定・計画の承認）だけ進んでいた。作業ブランチは `3f6ae9b` から作ったため、そのまま fast-forward できた（3.4 の諮りは不要）。
- 依頼者の承認を得て、`git switch develop` → `git merge --ff-only feature/260930-user-admin-b5` で統合した。`develop` の先頭は作業ブランチの先頭と同じ `afd69c3`。`git log --oneline 3f6ae9b..develop` で、C1 17af97d・C2 364e9d6・C3 519a69b・C4 afa7cb2・C5 2e86a4f・C6 3e9d12a・R2 81d2423・R3 afd69c3 が区切りのまま入ったことを確かめた（R1 は計画の承認の記録 3f6ae9b として `develop` の上にある）。
- push の前の確かめ（基盤の設計の R-04）: `vendor/make-you-chic-ui` で `git fetch` の後に `git ls-remote origin main` を実行し、取り込む `3d9521aa54b1d6277de473f9e935a496fb56ac1b` が公開の側の `main` の先頭であることを確かめた（計画が書いた `3481488` は、その祖先として含まれる）。
- 依頼者の承認を得て、作業ブランチ `feature/260930-user-admin-b5` を消した。この記録を `develop` の上の記録のコミット R4 にする。
- push は依頼者が行う。push の後の CI の結果は Build and Test で記録する。
