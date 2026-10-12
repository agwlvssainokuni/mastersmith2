# 単体テストの手順（Unit Test Instructions）— U7 骨組みの画面（app-frame-ui）

計画: `code-generation-plan.md`（同じディレクトリ）。Test Strategy は Standard（部品ごとに 5〜8 件と、要所の結合）、方法は test-after（層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて流す）。

ここに書くコマンドは、どれもこの単位のテストだけを名指しで流す（ファイルかディレクトリの道）。素の `npm test`・`npm run test:coverage`・`./gradlew verify`・`./gradlew e2eTest` はこの単位の確かめには使わない（変更の前後の基準・全体の検査・統合の前の関門は、計画の Step 3・5・17・24・33 で別に流す）。

## 1. テストの道具と設定（既存のものをそのまま使う）

| 対象 | 道具 | 設定 | この単位で変えること |
|---|---|---|---|
| 画面 | Vitest・Testing Library（jsdom）・user-event・vitest-axe・fast-check・`@vitest/coverage-v8` | `frontend/vitest.config.ts`（`include: ['src/**/*.test.{ts,tsx}']`、`thresholds` 行 80%・分岐 70%）・`vitest.setup.ts` | 変えない |
| ルーター | react-router の `MemoryRouter`（`renderWithProviders`）と `createMemoryRouter`・`RouterProvider`（`App` を描くテスト・`useBlocker` を使う画面） | 既存の依存 | 変えない |
| E2E（参考。単体テストではない） | Playwright・@axe-core/playwright（`./gradlew e2eTest`、`verify` と CI の外） | `frontend/playwright.config.ts` | 変えない |

新しい依存は足さない（`frontend/package.json`・`frontend/package-lock.json` を変えない。NFR6.5）。バックエンドのテストはこの単位に無い。

画面のテストのコマンドは、`frontend/` で `NODE_OPTIONS=--no-experimental-webstorage` を付けて流す（`package.json` の `test` と同じ）。長く流すもの（10 ファイルを超えるとき）は `caffeinate -di` で包み、流した後に `pmset -g log` でスリープが無かったかを確かめる。テストを流している間に同じ作業フォルダでビルドを流さない。出力は要約（件数・失敗の名前）だけを見る。

## 2. テストを始める前に動くことを確かめるコマンド（計画の Step 2）

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/app/layout \
  src/app/routing \
  src/app/navigation \
  src/app/login-state \
  src/app/registry \
  src/features/auth
npm run typecheck
```

どれも失敗 0 であることを記録する。固定先の更新の後（計画の Step 5）にも同じコマンドを流し、同じ件数で通ることを確かめる（旧形の `navItems` の経路のまま）。

## 3. この単位のテストを流すコマンド

### 3.1 骨組みの口（Step 9）

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/app/registry/validateRegistrations.test.ts \
  src/app/login-state/LoginStateGate.test.tsx \
  src/app/routing/AppRouter.test.tsx
```

### 3.2 純粋な関数と API の写し（Step 11）

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/app/navigation/tablePlaceholderPath.test.ts \
  src/app/navigation/navExpansion.test.ts \
  src/app/navigation/buildNavSections.test.ts \
  src/app/navigation/buildNavSections.property.test.ts \
  src/app/navigation/navigationApi.test.ts \
  src/app/work-role/workRoleApi.test.ts
```

性質ベースのテストが落ちたときは、fast-check の報告の `seed` と `path` を `generation-notes.md` に書き、`fc.assert(…, { seed, path })` で再現する。

### 3.3 状態と作業ロールの部品（Step 13・15）

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/app/work-role \
  src/app/navigation/BusinessNavigationProvider.test.tsx \
  src/app/navigation/useReloadTriggers.test.tsx
```

### 3.4 骨組みの差し替えと書き換えた既存のテスト（Step 17）

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/app/layout/ShellLayout.test.tsx \
  src/app/pages/HomePage.test.tsx \
  src/app/routing \
  src/app/navigation \
  src/app/i18n/messages.test.ts \
  src/app/App.test.tsx \
  src/features/admin/registration.test.ts \
  src/features/dsl/registration.test.tsx \
  src/features/invitation/registration.test.tsx \
  src/features/useradmin/registration.test.tsx \
  src/features/roleadmin/registration.test.tsx \
  src/features/groupadmin/registration.test.tsx \
  src/features/roletransfer/registration.test.tsx \
  src/features/registration/registration.test.tsx \
  src/features/preferences/registration.test.tsx
```

9つの機能の登録のテストは、`buildSidebarEntries`・`HOME_ENTRY` の代わりに `selectAdminSidebarItems` を使う形に書き換えたもの（計画 14節 Q5: A。確かめる中身は同じ）。この Step では、続けて画面のテストの全体（`caffeinate -di npm run test`）を1回流し、計画 6.1 の (d)(e) の当たりが無いことを確かめる（全体の実行はこの単位のコマンドではなく、影響の確かめとして計画の Step 17 に置く）。

### 3.5 ログアウトの道（Step 19）

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/features/auth \
  src/features/preferences/registration.test.tsx \
  src/app/routing/DataRouterBlocker.test.tsx
```

### 3.6 tables（Step 21）・mypermissions（Step 23、Should。計画 14節 Q3: A で B9 に含める）

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/tables
NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/mypermissions
```

### 3.7 この単位の画面のテストをまとめて流すとき（カバレッジつき）

```bash
cd frontend
caffeinate -di env NODE_OPTIONS=--no-experimental-webstorage npx vitest run --coverage \
  --coverage.include='src/app/work-role/**' \
  --coverage.include='src/app/navigation/**' \
  --coverage.include='src/app/layout/ShellLayout.tsx' \
  --coverage.include='src/app/pages/HomePage.tsx' \
  --coverage.include='src/app/login-state/LoginStateGate.tsx' \
  --coverage.include='src/app/registry/validateRegistrations.ts' \
  --coverage.include='src/app/routing/AppRouter.tsx' \
  --coverage.include='src/features/tables/**' \
  --coverage.include='src/features/mypermissions/**' \
  --coverage.include='src/features/auth/**' \
  --coverage.thresholds.lines=0 --coverage.thresholds.branches=0 \
  src/app/work-role src/app/navigation src/app/layout src/app/pages src/app/login-state \
  src/app/registry src/app/routing src/features/tables src/features/mypermissions src/features/auth
```

`thresholds` を 0 にするのは、この単位のファイルだけを集めた値を読むためで、関門ではない。関門は計画の Step 24・33 の全体のカバレッジ（下限 行 80%・分岐 70%、`vitest.config.ts` の値のまま）。

### 3.8 静的検査（この単位のファイルを含む全体。Step 24）

```bash
cd frontend
npm run format:check
npm run lint
npm run lint:css
npm run license:check
npm run typecheck
```

### 3.9 E2E（参考。計画の Step 26〜29。全体は Step 33）

```bash
# Mailpit を起動しておく（e2eTest と 170・150 の招待のメールに要る）
docker compose --profile mail up -d mailpit
./gradlew :backend:bootWar          # この後、E2E を流している間はビルドを流さない
cd frontend
# 既存の E2E の開き口・ログアウト・サイドバー・幅の書き換えの確かめ（Step 26）
caffeinate -di npx playwright test e2e/020-auth.e2e.ts e2e/030-admin-access.e2e.ts e2e/040-dsl-admin.e2e.ts \
  e2e/080-preferences-accessibility.e2e.ts e2e/090-invitation-registration-flow.e2e.ts \
  e2e/100-app-text-contrast.e2e.ts e2e/110-user-admin-flow.e2e.ts \
  e2e/120-user-admin-accessibility.e2e.ts e2e/130-admin-forbidden-accessibility.e2e.ts
# U7 の画面の検査（本数に数えない、Step 27）
caffeinate -di npx playwright test e2e/160-app-frame-accessibility.e2e.ts
# I の流れ（Step 28）と F の流れへの追加（Step 29）
caffeinate -di npx playwright test e2e/170-menu-navigation-flow.e2e.ts
caffeinate -di npx playwright test e2e/150-role-permission-flow.e2e.ts
```

統合の前の手元の確かめ（計画の Step 33、`cicd-pipeline.md` 5節）は、AI が scratchpad の Playwright の台本で行う（計画 14節 Q7: A。台本はリポジトリに足さず、この単位のテストの件数に数えない）。

E2E の初期管理者の資格情報はプロセスの環境変数で渡す（README の手順。`webServer.env` に置かない）。流した後は報告の確かめの部品（`playwright-secret-check-reporter.ts`）の結果を見て、`frontend/playwright-report`・`frontend/test-results` を消す。

## 4. テストの中身（Standard、部品ごと）

| ファイル | 件数の目安 | 確かめること |
|---|---|---|
| `src/app/registry/validateRegistrations.test.ts`（足し） | +3 | `path` の項目の `beforeNavigate` の関数は通る、`action` の項目に付けると止まる、関数でない値は止まる |
| `src/app/login-state/LoginStateGate.test.tsx`（足し） | +2 | `useClearLogoutIntent()` が提供元の口を呼ぶ、口が無いときは何もしない |
| `src/app/routing/AppRouter.test.tsx`（足しと書き換え） | +2 | `/logout` 以外の道で消す口が呼ばれ、`/logout` では呼ばれない。`sidebar-nav-<href>` の書き換え |
| `src/app/navigation/tablePlaceholderPath.test.ts` | 6〜8 | 先頭 `/tables?`、`/`・`..`・`?`・`#`・`%`・空白・`&`・`=`・`javascript:`・`https://` を含む名前の往復、`item` の読み戻し、引数の欠け・空。fast-check の往復 |
| `src/app/navigation/navExpansion.test.ts` | 8〜10 | 壊れた保存の値・保存の例外を無視、空なら鍵を消す、`clearExpanded` の後に鍵が無い、`pruneExpanded`、祖先を足す、木の読み込み前・失敗で捨てない、木が道より後に届いて祖先を足す、同じ道で閉じた枝を開き直さない |
| `src/app/navigation/buildNavSections.test.ts` | 8〜10 | ホーム・業務・管理の区画の並びと見出し、空の区画を出さない、管理者でなければ管理の区画が無い、`biz:`・`adm:`、表示名の既定（他方の言語 → テーブル名 → 名前なし）、知らないアイコンは `list`、今の項目（ホームは `/` だけ、管理は区切りでの前方一致で最も長い道、業務は `item` か行きがけの順の最初） |
| `src/app/navigation/buildNavSections.property.test.ts` | 7 | 機能設計 6.2 の性質 1〜7 |
| `src/app/navigation/navigationApi.test.ts`・`src/app/work-role/workRoleApi.test.ts` | 各 5〜6 | 送る道と方法、PUT の本文が `{roleId}` だけ、余分な項目を写さない、欠けた・型の違う・知らない列挙の本文は通信の失敗 |
| `src/app/work-role/WorkRoleProvider.test.tsx` | 7〜8 | ログイン状態の知らせ・切り替え・道の変化で読み直す、古い答えを捨てる、切り替えの4つの結果と `ROLE_BUSY`（D8 と同じ扱い、計画 14節 Q6: A）、送信の最中の2回目を捨てる、未ログインで捨てる、提供元が無いときの既定の値 |
| `src/app/navigation/BusinessNavigationProvider.test.tsx`・`useReloadTriggers.test.tsx` | 6〜8・5〜6 | D4 の (a)〜(d)、同じ描画の重なりを1回、`/logout` で読み直さない、読み直しの間に前の木が残る、置き場の 403 と `ROLE_NOT_ASSIGNED` で読み直す、要求の数（移動1回で2本、切り替え1回で PUT 1本と読み直し2本） |
| `src/app/work-role/WorkRoleSwitcher.test.tsx` | 8〜10 | 5つの表示、読み上げの名前、「（使用中）」、今のロールで要求を送らない、成功の Toast とフォーカス、拒否・失敗の文、押せない項目と `aria-describedby`、入れ物 `work-role-switcher`、キーボードだけ、待ちの間は押せない、英語、axe |
| `src/app/layout/ShellLayout.test.tsx`（書き換えと足し） | 既存＋8〜10 | 区画、`aria-expanded` と名前（日英）、`aria-current` は1つ、キーボードだけで5段の末端まで、深さちょうど5段、信頼できない名前を文字で出す、知らないアイコン、「Main menu」、開閉の保存と祖先、作業ロールの切り替えで業務の区画が替わる、`beforeNavigate` が移る前に1回、axe |
| `src/app/pages/HomePage.test.tsx`（足し） | +5〜6 | D19 の5つの案内（読み込み中は出さない、設定なし、作業ロールなし、表示なし、失敗と［もう一度］）、［自分の権限を見る］は道が登録されているときだけ |
| `src/features/auth/logoutIntent.test.ts`・`LogoutPage.test.tsx`・`registration.test.ts`（書き換え） | 4〜5・5〜6・既存 | 印の3つの口、StrictMode で `logout()` が1回、印が無ければホームへ `replace`、開閉の鍵が無い、axe、ログアウトの項目が `/logout` と `beforeNavigate` |
| `src/features/tables/tableAccessApi.test.ts`・`TablePlaceholderPage.test.tsx`・`registration.test.ts` | 5・8〜10・3 | D20〜D23（計画の Step 21） |
| 〔Should、含める〕`src/features/mypermissions/myPermissionsApi.test.ts`・`MyPermissionsPage.test.tsx`・`registration.test.ts` | 5・8〜10・3 | D24〜D26（計画の Step 23）。`displayName` は文字列1つ、表示の言語を替えたら最上位から読み直す（計画 14節 Q4: A） |
| `src/app/navigation/navigationItems.test.ts`（書き換え） | 既存＋3 | `selectAdminSidebarItems`（管理者だけ・`order` の順・未ログインで空、計画 14節 Q5: A）と `buildUserMenuItems` |
| 書き換える既存のテスト | — | 計画 6.1 の (a)〜(e)。確かめていた中身を減らさない |

テストの説明文（`describe`・`it`）は英語で書く。描画の後に反映される値は `waitFor`・`findBy*` で待つ。テストの時間の上限（Vitest の既定 5 秒）は原因を確かめずに延ばさない。

## 5. カバレッジの目標

- 関門: 画面のテストの全体で 行 80%・分岐 70% 以上（`vitest.config.ts` の `thresholds`、変えない。除外を足さない）。B8 の後の値は 行 94.88%・分岐 89.59%。
- この単位の目安: 新しいフォルダー（`app/work-role`・`app/navigation`・`features/tables`・`features/mypermissions`）と変えたファイルで 行 90% 以上・分岐 80% 以上を目指す。下回るファイルは、テストを足すか、理由を `code-summary.md` に書く（下限を下げない）。
- 値は計画の Step 3（前）・Step 24・Step 33（`verify` の `frontendCoverage`、実測）で記録する。

## 6. モック・差し替えの指針

- **API**: 画面の部品のテストは、API の関数（`workRoleApi`・`navigationApi`・`tableAccessApi`・`myPermissionsApi`）を `vi.mock` か props・提供元の差し替えで替える（既存の画面のテストと同じ形）。API の関数のテストは `apiRequest` を差し替えて、送る形と写しを確かめる。
- **`App` を描くテスト**（`App.test.tsx`・`DataRouterBlocker.test.tsx`）は `fetch` を `vi.stubGlobal` で差し替え、`GET /api/me/work-role`・`GET /api/me/navigation` に見本を返す。ほかの道は既存のとおり。
- **骨組みの提供元**: `renderWithProviders` は変えない。`WorkRoleProvider`・`BusinessNavigationProvider` が要るテストは、テストの中で包む（`app/work-role` と `app/navigation` のテストの補助の関数を、そのテストのファイルの隣に置く）。提供元が無いときはフックの既定の値（要求を送らない）が使われる。
- **時刻と遅れ**: 遅れた古い応答は、解決を手で制御する約束（`Promise` の `resolve` を持つ）で作る。`sleep` と実時刻に頼らない。偽の時計を使うのは「読み込んでいます」の表示の時間だけ（必要なとき）。
- **`sessionStorage`**: テストの前後で `sessionStorage.clear()`（`resetDisplayTestState` が行う）。例外を投げる保存は `vi.spyOn(Storage.prototype, 'setItem')` で作る。
- **ルーター**: 道の変化・戻るは `createMemoryRouter` の `router.navigate` で起こす。`useBlocker` を使う画面は data router の中だけで描く。
- **make-you-chic-ui の部品**: 差し替えない（部品に頼る振る舞いもこのリポジトリのテストで確かめる。`team.md`）。

## 7. テストデータの扱い

- 名前の見本: ロール「営業」「経理」と 40 文字のロール名、5段の木（各段 40 文字までの名前）、記号を含む名前（`<script>alert(1)</script>`・`<img src=x onerror=…>`・`&`・`"`・`'`・`/`・`..`・`%`・空白）、空の表示名、表示名が片方の言語だけのもの。日本語でよい。
- 利用者の見本: 氏名は実在しそうでない名前（「検査 太郎」など既存の形）、メールアドレスは `example.com` だけ。
- 見本は各テストのファイルか、機能の中の `testing/fixtures.ts` に置き、テストの間で状態を共有しない。
- 重い見本（1,000 項目・5段の木）は画面のテストに入れず、E2E の 160 の測りだけで使う（画面のテストの時間とヒープのため。B8 の D-11 と同じ）。性質ベースのテストの木は深さ 5・項目の数を絞った生成器にする。
- E2E の値（170・150 の利用者のメールアドレス・パスワード・氏名）は `support/secretValues.ts` の口で書き、報告の確かめの部品が探す。値をテストの題・注記・添付に入れない。
