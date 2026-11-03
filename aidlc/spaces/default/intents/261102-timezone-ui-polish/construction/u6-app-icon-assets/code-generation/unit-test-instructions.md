# 単位のテストの手順 — u6-app-icon-assets

出典: コード生成の計画（`code-generation-plan.md`）、NFR Design（`nfr-design/security-design.md` の SD7）、NFR Requirements（`security-requirements.md` の NFR1.1.5・NFR2.1.1・NFR3.1.1・NFR3.2.1・NFR5.3.1）、Infrastructure Design（`infrastructure-design/cicd-pipeline.md`）、`team.md` Testing Posture。書き方は前の単位（`construction/u3-target-db-time/code-generation/unit-test-instructions.md`）にそろえる。この単位には functional-spec・rules が無い。

U6 は、アプリのアイコンと favicon の素材（自作の SVG・`.ico`）と、その検査を足す単位（種類 packaging）。テストの中心は `frontend/`（検査のスクリプトと素材のファイルを読む Vitest、ログイン画面の部品）で、バックエンドは `/favicon.ico` の配信の結合テスト 1 クラスだけ。方法は test-after（層ごとに実装 → その層のテスト → 通ってから次へ。ただし配信の仕組みの結合テストは素材を作る前に先に流す）。テストの量は Standard（部品ごとに 5〜8 件）。

## テストの枠組みと設定

- 枠組み（フロントエンド）: Vitest（`unit` プロジェクト。`src/**/*.test.{ts,tsx}` だけが対象）・Testing Library（jsdom）・vitest-axe・`fast-check`（性質ベース。失敗時の乱数の種を出力に残す）・jsdom の `DOMParser`（SVG を厳密な XML として読む突き合わせ）。新しい依存は足さない。
- 枠組み（バックエンド）: JUnit 5・AssertJ・Spring の `@SpringBootTest`（`RANDOM_PORT`、組み込みの H2 の内部DB。コンテナは要らない）。既存の `SecurityHeadersIT` と同じ形・同じ設定。
- 枠組み（E2E）: Playwright（`./gradlew e2eTest`）。新しい流れ・新しい `test()` は足さず、既存の `050-display-accessibility.e2e.ts` の 2 つのテストに書き足す。
- 置き場: Vitest のテストは `frontend/src/` の下（`.mjs` のスクリプトを読むテストは `src/` の直下に `xxx.test.ts`。既存の `src/perfEvidenceRedactor.test.ts` と同じ、`pathToFileURL` と動的な `import()`、手書きの型）。画面の部品のテストは対象と同じ場所（`src/shared/app-icon/AppIcon.test.tsx`）。結合テストは `backend/src/test/java/cherry/mastersmith/config/StaticIconAssetsIT.java`。テストの説明文（`describe` / `it` / `@DisplayName`）は英語、テストデータは日本語でよい。
- 悪い例の SVG は**テストの中の文字列**にする。`.svg` のファイルとして `frontend/` の下に置かない（置くと `svg:check`・`license:check` が実際に失敗する）。ファイルが要るテスト（集める関数・`dist` の検査）は、`os.tmpdir()` の下の一時のディレクトリに作って終わりに消す。
- 時刻・タイムゾーンに依存するテストは無い。ネットワークも使わない（`AppIcon` の描画の間に `fetch` が呼ばれないことは、スパイで確かめる）。

## この単位だけを流すコマンド（リポジトリのルートで実行）

フロントエンド（Vitest。この単位のファイルだけ）:

```
cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx --no-install vitest run --project unit \
  src/licenseHeaderCheck.test.ts \
  src/svgSafetyCheck.test.ts \
  src/svgSafetyCheck.property.test.ts \
  src/svgSafetyCheck.parser.test.ts \
  src/svgCheckScope.test.ts \
  src/iconBinary.test.ts \
  src/appIconAssets.test.ts \
  src/distAssetsCheck.test.ts \
  src/shared/app-icon/AppIcon.test.tsx \
  src/app/layout/LoginLayout.test.tsx
```

1 つのファイルだけを流すときは、そのファイルの道だけを渡す。`LoginLayout` を描く画面のテスト（変わらず通ることを確かめる）:

```
cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx --no-install vitest run --project unit \
  src/features/auth/LoginPage.test.tsx src/features/auth/LoginForm.test.tsx src/app/App.test.tsx
```

検査の道具そのもの（テストではなく、素材と `dist` に対する確かめ。この単位のファイルだけを対象にする）:

```
cd frontend && npm run svg:check && npm run license:check
cd frontend && npm run build && npm run dist:check
```

Spring の結合テスト（コンテナ不要。Step 3 で素材を作る前に先に流す）:

```
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.config.StaticIconAssetsIT'
```

Gradle の検査のタスク（組み込みの確かめ。Step 14 以降）:

```
./gradlew :frontendSvgCheck :frontendDistCheck :backend:verifyFaviconInWar
```

E2E（`frontend/index.html` と `frontend/public/` は frontend の本体にあたるため、統合の前に流す。事前に Mailpit と見本の対象DB のコンテナが動いていること。`docs/verification.md` の「ビルドした WAR での画面の確認（E2E）」）:

```
./gradlew e2eTest -Pe2e.files=e2e/050-display-accessibility.e2e.ts
./gradlew e2eTest
```

1 行目は Step 16（書き足した 050 だけ）、2 行目は Step 20（既存の全件）。長い実行（`verify`・E2E の全件）は `caffeinate -dims` で包み、流す前に `pmset -g assertions` で眠らない状態か、流した後に `pmset -g log` で Sleep・DarkWake が無かったかを確かめ、入っていたら結果を使わずに流し直す。`verify` と結合テストを流すときは、colima のコンテナの実行環境のため次の 2 つをシェルに渡す（渡さないと対象DB のテストが SKIPPED になり、飛ばした状態では統合しない）。

```
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

`.ico` を作る道具（手で流す道具。テストではない。手順と出力の大きさは `docs/app-icon.md` に書く）:

```
rsvg-convert -w 16 -h 16 frontend/public/favicon.svg -o <一時の場所>/icon-16.png   # 32・48 も同じ
node frontend/scripts/make-ico.mjs frontend/public/favicon.ico <一時の場所>/icon-16.png <一時の場所>/icon-32.png <一時の場所>/icon-48.png
```

カバレッジの値（フロントエンド全体。床の検証を含む）は、別のコマンドで単独に流し、その終了コードで後の手順を止めない:

```
cd frontend && npm run test:coverage
```

## 期待する件数（Standard）

単体テスト（Vitest）:

| ファイル | 件数の目安 | 内容 |
|---|---|---|
| `src/licenseHeaderCheck.test.ts`（新規） | 7 | `.svg` の合格・ヘッダー無し・`/* */` の形・先頭が XML 宣言・CRLF の合格、`.html`・`.ts` の判定が不変、`collectFiles` が入れ子の `.svg` を見つけ `node_modules`・`dist`・`.` で始まるディレクトリを飛ばす、import しても `main()` が走らない |
| `src/svgSafetyCheck.test.ts`（新規、表を使う） | 約 6 件（表の行は約 60 ケース） | 良い例が通る、許可リストの外を 1 つ足すと落ちる、SD1.6 の全変形と SD7.1 の一覧が落ちる（違反の種別のコードと位置）、`standalone: true / false` の違い、入口の確かめ（4096 文字ちょうどは通り 4097 は落ちる、ASCII 以外・制御文字） |
| `src/svgSafetyCheck.property.test.ts`（新規、`fast-check`） | 5 | 任意の文字列で例外が出ず短い時間で返る、許可リスト外の要素を 1 つ入れると落ちる、名前の大文字小文字を 1 文字変えると落ちる、前置き・後置きで落ちる、受け入れた値に `<`・`>`・`&`・`:` が無い |
| `src/svgSafetyCheck.parser.test.ts`（新規、jsdom の `DOMParser`） | 3 | 受け入れた例が厳密な XML として読め、要素・属性の名前が許可リストの内側で、接頭辞・処理命令・外部の参照が無い。良い例の集合で足りる |
| `src/svgCheckScope.test.ts`（新規） | 3 | 一時の `e2e/` の下の仮の `.svg` を、ヘッダーの検査と中身の検査の両方の集める関数が見つける。除外するディレクトリが同じ |
| `src/iconBinary.test.ts`（新規） | 8 | `packIco` の出力が `inspectIco` を通る（1・2・3 枚）、HTML の本文・先頭 4 バイト違い・画像の数 0 と 9・範囲外・PNG の署名なし・32 KiB 超の拒否、`readPngSize`、`inspectPng` の 16 KiB 超 |
| `src/appIconAssets.test.ts`（新規、実ファイル） | 8 | 名前の一覧にちょうど一致、SVG が `standalone` の検査を通る、`.ico` の形と 16・32・48 の 3 枚、大きさの上限、2 つの SVG の `d` の一致、色（固定の 1 色と `currentColor`）、`index.html` の `<link>` が外部を指さず `public/` を指す |
| `src/distAssetsCheck.test.ts`（新規、一時のディレクトリ） | 9 | 正しい `dist`、素材の欠け、バイト列の違い、`favicon.ico` が HTML、`public/` が空、SVG の検査の失敗、`<link>` の `href` が外部・`//`・`..`・存在しないファイル、`<link>` が 0 本 |
| `src/shared/app-icon/AppIcon.test.tsx`（新規） | 7 | `outerHTML` が `standalone: false` の検査を通る、属性が許可リストの内側（`aria-hidden`・`focusable`・`fill`）、`title`・`aria-label`・`class`・`style`・`href`・`on*` が無い、`img` の役割で見つからない、`d` が `public/app-icon.svg` と一致、`size` の反映、`fetch` が呼ばれない、vitest-axe 1 件 |
| `src/app/layout/LoginLayout.test.tsx`（直す） | 3 増 | アイコンの包みがアプリ名の直前、読み上げに出ない、アプリ名が読める（既存の axe の件は通ること） |

Vitest は合わせて約 60 件（表の行・性質の反復を含めると約 150 ケース）。

結合テスト（Spring）:

| ファイル | 件数 | 内容 |
|---|---|---|
| `StaticIconAssetsIT`（新規） | 3 | `/favicon.ico`（未認証で 200・`image/` で始まる種類・本文が `<!doctype html` で始まらない・`nosniff`・`Cache-Control: no-cache`。`Content-Type` の実際の値を出力に残す）、`/favicon.svg`（`image/svg+xml`・同上）、素材が無い道（`/missing-icon.ico`）が `index.html`（`text/html`）に落ちること |

E2E（書き足しのみ。本数に数えない）:

| ファイル | 内容 |
|---|---|
| `050-display-accessibility.e2e.ts` の「login screen（組ごと）」 | アイコンの包みが見える、その中の `svg` が `aria-hidden="true"`、`getByRole('img')` が 0 件。axe（ブランドカラーとテーマのすべての組）の違反 0 件・はみ出しなし（既存の検査のまま） |
| 同じファイルの「first screen time ...」の `run === 1` | `/favicon.ico`・`/favicon.svg` を未認証で読む（200・種類・本文が HTML でない・ICO の先頭 4 バイト・`nosniff`）、`<head>` の `link[rel="icon"]` が 2 本で `href` が外部を指さない |

手で失敗することを確かめるもの（Gradle のタスクの単体テストは作らない。計画の Step 17）: `dist:check`（`dist` から `favicon.ico` を消す）、`verifyFaviconInWar`（`bootWar` から `favicon.ico` を一時的に除く、`public/` を空にする）、素材の名前の一覧（余計なファイルを置く）、`svg:check`（`<script>` を足す）。いずれも元に戻したことを `git diff --stat` で確かめ、結果を `code-summary.md` に書く。

## 期待するカバレッジ

- 全体の合計の下限（`team.md`）: フロントエンドは行 80%・分岐 70%（`vitest.config.ts` の `coverage.thresholds`）、バックエンドも同じ下限と、パッケージごとの下限。**これらの下限は、プロジェクト全体のテストを流す Build and Test（と統合の前の `./gradlew verify`）が判定する**。この単位のコマンド（`vitest run <ファイル>`・`:backend:integrationTest --tests …`）は床の検証を含まないため、床で終了コードが非 0 になることはない。床の検証を含むコマンド（`verify`・`npm run test:coverage`）は、この単位のコマンドに混ぜず、単独で流す（その終了コードで後の手順を止めない）。下限の設定は変えない。
- この単位の新しい `src/` のコード（`src/shared/app-icon/AppIcon.tsx`、`LoginLayout.tsx` の変更部分）は、行・分岐とも 90% 以上を目安にする（小さな固定の部品）。`frontend/scripts/` の `.mjs` は `coverage.include` の対象でなく、対象に入れない（除外を増やす・下限を逃れる形にしない）。代わりに、スクリプトは純粋な関数の単体テストと、手で失敗することの確かめ（上）、`verify` の実行で守る。
- バックエンドの `src/main` には手を入れないため、`packagesJudgedByTotal`（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）の値は変わらない。入れることになったら、テストを足して下限を満たし一覧から外す手順を計画に足す。

## モック・スタブの方針

- `AppIcon` と `LoginLayout` のテストは、実物の描画（jsdom）を使い、モックを使わない。`fetch` のスパイは「呼ばれないこと」を確かめるためだけに使う。
- 検査のスクリプトのテストは、ファイルシステムの代わりにモックを使わず、一時のディレクトリの実ファイルを使う（`os.tmpdir()`、終わりに消す）。
- 結合テスト（`StaticIconAssetsIT`）は Spring を実際に起動し、HTTP クライアント（既存の `HttpTestClient`）で未認証の要求を送る。`HttpTestClient` が本文のバイト列を返せないときは、ICO の本文は先頭の文字（`<!doctype html` で始まらないこと）で確かめ、先頭 4 バイトの確かめは `dist`・WAR の検査と E2E に任せる。
- E2E は、素材と検査の確かめにサーバーの応答を差し替えない（本物の WAR を通す）。

## テストデータ

- SVG の良い例は、実際の `public/` の SVG と同じ形（許可リストの要素・属性だけ）をテストの中の文字列で持つ。悪い例は SD1.6 の表の変形（大文字小文字・名前空間の接頭辞・開始タグの名前の直後の空白や改行・コメント・CDATA・処理命令・`DOCTYPE`・`ENTITY`・属性値の中の `>`・文字参照・`data:`・`javascript:`・外部の URL・`style`・引用符の欠け・属性の続け書き・二重の属性・制御文字・BOM・双方向の制御文字・根の外の要素・大きさ超過）を表（`it.each`）で持つ。
- ICO・PNG の見本は、テストの中で `Buffer` から作る最小のもの（1×1 の PNG、署名と IHDR だけの壊れた PNG、HTML の本文）。バイナリのファイルをテストの資源として置かない（`backend/src/test/resources/static/favicon.ico` を除く。Node の短い書き込みで作る最小の ICO で、画像として表示される必要はない）。
- 秘密に見える文字列・実在しそうな氏名・メールアドレスを置かない（Gitleaks、公開のリポジトリ）。素材は公開のもので、図柄だけを含む。

## この単位に当たらないテスト

- 新しい流れの E2E は足さない（NFR5.3.1）。axe の確かめは既存の 050 の書き足しで、本数に数えない。
- k6 の場面・perf の種は足さない（新しい API・経路・状態が無い。素材の大きさの上限 NFR2.1.1 は素材のテストで守る）。
- 秘密情報の漏えいのテスト（`XxxSecretLeakIT`）・認証・認可・監査のテストは足さない（新しい API・個人に関する値・秘密が無い。`/favicon.ico` は未認証で読める公開の静的なファイル）。
- Firefox・Safari での favicon の見え方、ブラウザの favicon の保持（図柄を変えた後の更新）は、この Bolt の自動のテストでは確かめられない。配備の後のスモークテスト（S1〜S6、Deployment Pipeline で手順を書き、Deployment Execution で依頼者が実際の画面で見る）の持ち主とし、`code-summary.md` に「未実行」と理由を書く。
