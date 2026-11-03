# コード生成のまとめ — u6-app-icon-assets

## 作った・変えたファイル

`source-manifest.json` のとおり。要点:

- 素材（`frontend/public/`、3 ファイルだけ）: `favicon.svg`（固定の 1 色 `#2563eb`）・`app-icon.svg`（`fill="currentColor"`）・`favicon.ico`（16・32・48 px の PNG を詰めた ICO、2085 バイト）。図柄は金床とハンマーの直線だけのシルエット（`viewBox="0 0 64 64"`、`path` 2 つ、背景透明）。ヘッダー込みの文字数は `app-icon.svg` が 811 文字、`favicon.svg` が 806 文字で、上限 4096 文字に収まった（図柄を簡単にする必要は無かった）。
- `frontend/index.html`: favicon の `<link>` を 2 本（`.ico` が先、SVG が後。D-P4）。
- 検査の道具（`frontend/scripts/`、Node の標準の機能だけ。依存は足していない）: `check-svg-safety.mjs`（許可リストの状態機械。最初の違反で止まり、種別のコードと位置だけを返す）、`check-dist-assets.mjs`（`dist:check`）、`icon-binary.mjs`（PNG・ICO の形の確かめと ICO を詰める純粋な関数）、`make-ico.mjs`（手で流す道具。ビルドには組み込まない）。`check-license-header.mjs` は `.svg` を HTML 形の対象に足し、`collectFiles`・`SKIP_DIRS` を export した。`package.json` は `scripts` に `svg:check`・`dist:check` を足しただけ。
- Gradle: ルートの `build.gradle.kts` に `frontendSvgCheck`（`verifyLicense`）・`frontendDistCheck`（`verifyBuild`、`frontendBuild` の後）、`frontendBuild` の `inputs` に `public/`、`verifyStages` の 3・4・9 の説明文、`verifyArtifact` に `verifyFaviconInWar`。`backend/build.gradle.kts` に `verifyFaviconInWar`（`public/` の下のすべてのファイルを WAR の `static/` と比べる。素材の名前は書かない。`public/` が空でも失敗）。
- ログイン画面: `frontend/src/shared/app-icon/AppIcon.tsx`（props は大きさだけ。`APP_ICON_PATHS` は `public/app-icon.svg` の `d` と同じ）、`LoginLayout.tsx` がアプリ名の直前に包み（`login-layout-app-icon`）と `<AppIcon size={48} />` を置く。`LoginLayout.css` は `color: var(--color-text-muted)` だけで独自の色は足していない。
- 文書: `docs/app-icon.md`（新規。素材・`.ico` の作り方・道具の版・守りの検査）、`README.md` の `docs/` の表に 1 行、`docs/verification.md` の表の 3・4・9 の説明。
- テスト: 下に示す。サーバー（`backend/src/main`）・`SecurityConfig`・`WebConfig`・`CacheControlFilter`・`application.yaml` は変えていない。

## `.ico` の作り方と Content-Type

- 道具: `rsvg-convert` 2.63.4（cairo 1.18.6）、Node 26.13.0。`rsvg-convert -w N -h N frontend/public/favicon.svg -o <一時の場所>/icoN.png`（N = 16・32・48、背景は透明のまま。一時の場所はリポジトリの外）→ `node frontend/scripts/make-ico.mjs frontend/public/favicon.ico <16> <32> <48>`。出力 2085 バイト、画像 3 枚。ImageMagick は PC に無いため使っていない（設計の差 4 のとおり）。手順と別の PC での代わりの道具は `docs/app-icon.md`。
- `/favicon.ico` の実際の `Content-Type`（`StaticIconAssetsIT` の出力）: **`image/x-icon`**。`/favicon.svg` は `image/svg+xml`。素材が無い道（`/missing-icon.ico`）は `text/html` で 200 を返し `index.html` に落ちる。L1 は素材を作る前に流して通り、サーバーの変更は要らなかった（ADR-008 の前提は崩れていない）。
- 16 px・32 px・64 px・256 px に描いた画像を自分で見て確かめた。16 px では金床とハンマーの塊として読め、潰れて消えてはいない（細部は見えない）。実際のタブでの見え方は配備の後のスモークテスト（S5）で依頼者が確かめる。

## テストの数と実測

- Vitest（新規・追加）: `licenseHeaderCheck` 11、`svgSafetyCheck` 94（表の悪い例 72 行を含む）、`svgSafetyCheck.property` 6（`fast-check`、失敗時の種を記録）、`svgSafetyCheck.parser` 7（jsdom の厳密な XML パーサーとの突き合わせ）、`svgCheckScope` 3、`iconBinary` 17、`appIconAssets` 14、`distAssetsCheck` 18、`AppIcon.test.tsx` 7（vitest-axe 1 件を含む）、`LoginLayout.test.tsx` に 1 件追加。合計 178 件。フロントエンド全体: `npm run test:coverage` で 255 ファイル・2642 件が通過。
- フロントエンドのカバレッジ（全体の合計）: 行 95.24%・分岐 90.54%（下限は行 80%・分岐 70%）。`src/shared/app-icon/AppIcon.tsx` は行・分岐とも 100%、`src/app/layout/LoginLayout.tsx` は行・分岐とも 100%。`frontend/scripts/` は計測の対象外のまま（`vitest.config.ts` は変えていない。除外は増やしていない）。
- Spring の結合テスト（新規）: `StaticIconAssetsIT` 3 件（`/favicon.ico` が未認証で 200・`image/x-icon`・本文が HTML でない・`nosniff`・`no-cache`、`/favicon.svg` が `image/svg+xml`、素材が無い道が `index.html` に落ちる）。`SecurityHeadersIT`・`DslSchemaPublicationIT` と合わせて流して通過。
- E2E: `./gradlew e2eTest -Pe2e.files=e2e/050-display-accessibility.e2e.ts` で 21 件が通過（ログイン画面の組ごとの axe 違反 0 件、アイコンが見えて `aria-hidden="true"`・`getByRole('img')` が 0 件、`/favicon.ico`・`/favicon.svg` を未認証で画像として読めること、`<head>` の `link[rel="icon"]` が 2 本）。新しい `test()` は足していない。axe の規則は外していない・`exclude` していない・`KNOWN_VIOLATIONS` には足していない。
- 個別の Gradle のタスク: `spotlessCheck`・`:backend:spotlessCheck`・`frontendFormatCheck`・`frontendLicenseCheck`・`frontendSvgCheck`・`frontendLint`・`frontendLintCss`・`frontendTypecheck`・`frontendDistCheck`・`:backend:verifyFaviconInWar` が通過。

## 手で失敗を確かめた結果（Step 17。すべて元に戻した）

| 確かめ | 結果 |
|---|---|
| ① `frontend/dist/favicon.ico` を消して `npm run dist:check` | 失敗（終了コード 1。「dist に favicon.ico がありません」と、`<link>` の `href` が dist に無いファイルを指す、の 2 件）。複写し直して通過に戻した |
| ② `backend/build.gradle.kts` の `bootWar` に一時的に `exclude("**/favicon.ico")` | `verifyFaviconInWar` が「WAR に WEB-INF/classes/static/favicon.ico がありません」で失敗。`Edit` で戻し、保存しておいた写しと `cmp` で一致を確かめた |
| ③ `frontend/public/` の中身を一時的に外へ移して空にする | `verifyFaviconInWar` が「frontend/public が空です」で失敗。戻した後の SHA-256 が移す前と一致し、再実行で通過 |
| ④ `frontend/public/` に一覧に無いファイルを置く | `src/appIconAssets.test.ts` の「余計なファイルが無い」が失敗。消して通過に戻した |
| ⑤ `app-icon.svg` に `<script/>` を足す | `npm run svg:check` が `unknown-element @688` で失敗（終了コード 1）。戻した後の SHA-256 が一致 |

最後に `git diff --stat` で `build.gradle.kts`・`backend/build.gradle.kts` の差分が意図した追加だけ（`exclude` が残っていない）であること、`frontend/public/` の 3 ファイルの SHA-256 が最初の値と同じであることを確かめた。

## 構成の差分の確かめ（Step 18）

- 変更が無いこと（`git diff` で確認）: `frontend/package.json` の `dependencies`・`devDependencies`（`scripts` に 2 行だけ）、`frontend/package-lock.json`、`backend/gradle.lockfile`、`config/npm-build-tools.txt`、`.github/workflows/ci.yml`、`Dockerfile`、`compose.yaml`、`.env.example`、`db/migration`、`backend/src/main`、`frontend/vitest.config.ts`、`frontend/vite.config.ts`、`vendor/`。
- 新しいソース（`.mjs`・`.ts`・`.tsx`・`.java`・`.svg`）の先頭に Apache License 2.0 のヘッダーがあること（`.svg` は先頭に HTML のコメント形、XML 宣言なし）を確認した。
- 既存の `LoginLayout` を描く画面のテスト（`LoginPage`・`LoginForm`・`App`）は、アイコンを足しても変更なしで通った。`LoginLayout.test.tsx` はアイコンの順序・読み上げの 1 件を足し、既存の axe の件は通った。

## 計画との差・決めたこと

- Step 1: ブランチ `feature/261102-timezone-ui-polish-u6` は呼び出し元が作成・checkout 済みのため、計画（`feature/261102-timezone-ui-polish-b5`）と名前が違う。ブランチは変えていない。
- 計画に無い小さな足し物: 3 つの検査のテストが共有する `frontend/src/svgFixtures.ts`（テストの補助。`src/` の下に置いたため、テストだけが読み、カバレッジは通過）。
- `no-script-url`（oxlint）が悪い例の文字列 `javascript:` に当たったため、規則は外さず、テストの中で `['java', 'script:'].join('')` と組み立てる形にした。テストの名前の文字列にも `javascript:` は使わない。
- 悪い例の文字列は計画どおりテストの中の文字列で、`.svg` のファイルとして `frontend/` の下に置いていない。
- 許可リストの属性に `xmlns` は根だけ（子では `unknown-attribute`）、`role`・`aria-hidden`・`focusable` を入れた（SD1.5 のとおり）。`fill` を根の `svg` に置いた（2 つの SVG とも `fill` は根の属性）。
- 図柄は 2 つの `path`。ハンマーの path は頭と柄の 2 つの閉じた図形を 1 つの `d` に持つ（`M…Z M…Z`）。計画の「金床とハンマーの 2 つの `path`」のとおり。

## 未実行・確かめられなかったもの（Unverified）

- `./gradlew verify` 全体（Step 19）: 呼び出し元が流す。部分実行で通したのは上の個別のタスクとフロントエンド全体のテスト・カバレッジだけで、バックエンド全体のテスト・カバレッジ（本体に手を入れていないため値は変わらない見込み）・SpotBugs・OSV-Scanner・Gitleaks（新しい `.svg`・`.ico` の秘密情報の検出を含む）・成果物の判定・`verify` の時間は未測定。
- `./gradlew e2eTest` の全件（Step 20）: 呼び出し元が流す。050 の 21 件だけ通過を確認した。
- Firefox・Safari での favicon の見え方、ブラウザの favicon の保持、タブでの実際の見え方（`.ico` が先・SVG が後の `<link>` の優先）: 配備の後のスモークテスト（S1〜S6）の持ち主（Deployment Pipeline / Execution）。ここではブラウザの実機で確かめていない。
- `favicon.ico` の図柄が `favicon.svg` と一致することは機械では確かめない（設計のとおり。上の 16〜256 px の目視と `docs/app-icon.md` の手順で担保）。

## 依頼者に確かめたいこと

1. 図柄（`frontend/public/app-icon.svg`・`favicon.svg`）は、金床にハンマーを振り上げる直線のシルエットにした。256 px では読めるが、ハンマーの頭がやや大きく柄が短い。形を変えたい場合は `d` を直して `.ico` を作り直す（`docs/app-icon.md`）。
2. `docs/verification.md` の表の 3・4・9 の説明を書き換え、`README.md` の `docs/` の表に 1 行足した（承認の場の 3・4 の推奨どおり）。
3. `frontend/coverage/`・`frontend/test-results/` は、この作業のテストの実行で（git の対象外の）生成物ができている。承認の場が「git の対象外の生成物」で止まる学び（`project.md`）があるため、必要なら消してから承認の場を開く。

## Sources

- 承認済みの計画: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u6-app-icon-assets/code-generation/code-generation-plan.md`・`unit-test-instructions.md`
- 設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u6-app-icon-assets/nfr-design/security-design.md`・`infrastructure-design/cicd-pipeline.md`・`nfr-requirements/security-requirements.md`
- 規則: `aidlc/spaces/default/memory/team.md`・`project.md`（Code Style の SVG・ライセンスヘッダー、Forbidden、Testing Posture）
- ストーリー: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/user-stories/stories.md`（US4.1・US5.2）

## Assumptions & Open Questions

- 前提: 呼び出し元が `verify` と `e2eTest` の全件を統合の前に流す（この作業は個別のタスクと 050 までの部分実行）。
- 前提: 上のバーのロゴ（U5）は `AppIcon` と `public/app-icon.svg` の `d` の一致を使う。形を変えるときは両方を同時に直す。
- 未確認: 実際のブラウザのタブでの `.ico` と SVG の優先（Chromium 系以外）と、ブラウザの favicon の保持。
- 依頼者への問いは上の「依頼者に確かめたいこと」の 3 点。
