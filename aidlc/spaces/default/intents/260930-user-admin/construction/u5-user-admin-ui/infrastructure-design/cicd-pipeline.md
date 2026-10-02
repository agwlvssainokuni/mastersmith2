# CI/CD Pipeline — U5 利用者の管理の画面（u5-user-admin-ui）

U5 の検査の流れ（CI と1コマンドの検査）、依存と make-you-chic-ui の固定先と `frontend/.npmrc` の扱い、E2E（110 の代表の流れ、120 の実際のブラウザの検査と画面の時間の測り）とその報告の扱い、統合・配備・戻しを示します。U5 は新しい依存・API・スキーマ・設定・イメージを持たないため、CI と1コマンドの検査の段と配備の流れは **変えず**、この文書は既にある仕組みの記録として、どの段で何を確かめるかを書きます。変えるのは、手元だけで動く E2E の設定（`frontend/playwright.config.ts` の reporter と trace）と報告の部品（`frontend/playwright-secret-check-reporter.ts`）です。配備先は開発者の PC 上のコンテナだけで、クラウドの基盤・IaC・警報の通知先は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。

- 答え: `infrastructure-design-questions.md`（決まっていること、要点 4〜7、Q1: A、Consolidated Summary Confirmation: Looks correct）
- 上流: `construction/u5-user-admin-ui/nfr-design/`（`security-design.md` 3節・4節・5節・7節・8節・10節〜12節と「承認の場の決定」、`performance-design.md` 4節・5節、`logical-components.md` 3節・5節・8節・9節）、`construction/u5-user-admin-ui/nfr-requirements/tech-stack-decisions.md`（NFR7.3・NFR9.2〜NFR9.9）・`security-requirements.md`（NFR3.4・NFR3.5・NFR9.1）、`construction/u5-user-admin-ui/functional-design/functional-spec.md`（9節）、`inception/delivery-planning/bolt-plan.md`（共通の完了の条件と B5）、同じ段の `construction/u3-user-admin-api/infrastructure-design/`（Q1 A）・`construction/u4-admin-forbidden-ui/infrastructure-design/cicd-pipeline.md`（5節）（どれも `aidlc/spaces/default/intents/260930-user-admin/` の下）
- 既にある仕組み（正とする、読むだけ）: `.github/workflows/ci.yml`・`build.gradle.kts`（`verify`・`verifyPrepare` の `vendorInstall`・`vendorBuild`・`vendorUnchanged`・`frontendInstall`、`frontendBundleSize`・`osvScan`・`e2eTest` と Mailpit の確かめ）・`frontend/.npmrc`（今は `engine-strict=true` だけ）・`frontend/playwright.config.ts`・`frontend/playwright-secret-check-reporter.ts`・`frontend/e2e/`（010〜100 と `support/`）・`.gitignore`（`frontend/test-results/`・`frontend/playwright-report/`）・`README.md`（「ビルドした WAR での画面の確認（E2E）」）

## 1. CI（GitHub Actions）

`.github/workflows/ci.yml` は変えません。

| 項目 | 今の形 | U5 での扱い |
|---|---|---|
| きっかけ | `develop` へのプッシュ・`v*` のタグ・手動 | 変えない。CI は統合の後の再確認（`team.md` の Way of Working） |
| 実行 | `./gradlew verify`（`ubuntu-latest`、制限時間 60 分） | 変えない。E2E（`./gradlew e2eTest`、110・120 を含む）は CI に入れない（`team.md` の Testing Posture） |
| サブモジュール | `submodules: true` で固定先のコミットを取得 | 変えない。U5 が上げた make-you-chic-ui の固定先のコミットを取得する。そのコミットが make-you-chic-ui のリポジトリの公開の側に無いと、取得で失敗する（5節の 6） |
| 依存の入れ方 | lockfile どおり（`npm ci`）。npm のキャッシュの鍵は `frontend/package-lock.json` と `vendor/make-you-chic-ui/package-lock.json` | 変えない。`frontend/.npmrc` の `ignore-scripts=true` は CI の `frontend/` の `npm ci` にも効く（3節） |
| 秘密 | CI は秘密を使わない | U5 は秘密を足さない |
| 成果物 | WAR をコミットのハッシュの名前で保存 | 変えない。WAR の中の `dist` の中身が変わるだけ。E2E の報告は CI の成果物に載らない |

CI が失敗したときは、次の Bolt に進む前に `team.md` の Testing Posture「不安定なテストと CI の失敗」の決まりで扱います。

## 2. 1コマンドの検査（`./gradlew verify`）の段と関門

段・関門・設定は増やさず変えません。U5 の変更は既存の段で次のとおり確かめられます。どれか1つでも失敗したら全体を失敗とします（既存のとおり）。

| 段 | U5 で確かめること | 関門（失敗の条件） | 当たる要件 |
|---|---|---|---|
| 0 準備 | `vendorInstall`・`vendorBuild`（上げた固定先の make-you-chic-ui を今までの手順で入れてビルドする。`ignore-scripts=true` は効かない）、`vendorUnchanged`（サブモジュールの中身を変えていないこと）、`frontendInstall`（`npm ci`、`ignore-scripts=true` が効く） | 入れられない・ビルドできない・サブモジュールの中身が変わっている | NFR9.3・NFR9.4 |
| 1 フォーマット | U5 の TypeScript・CSS、`frontend/e2e/110-user-admin-flow.e2e.ts`・`120-user-admin-accessibility.e2e.ts`・新しい手伝い、`playwright.config.ts`・報告の部品（Prettier） | 書式の違い | NFR9.1 |
| 2 リンタ | oxlint（`react/no-danger`・`no-eval`・`no-new-func`・`no-script-url` など）・ESLint（`export default`・`enum` の禁止、react-hooks）・Stylelint。決まりを緩めず、除外を足さない | 1件でも error | NFR9.1 |
| 3 ライセンスヘッダー | 新しいファイルの先頭の `/* ... */` の Apache License 2.0 のヘッダー | ヘッダーが無い・形が違う | — |
| 4 ビルド | 型検査（`tsc --noEmit`。`frontend/tsconfig.json` の `include` に `e2e` があり、見本の型 `AdminUserPage`・`AdminUser` もここで確かめる）と Vite のビルド | 型の誤り・ビルドの失敗 | NFR9.1・NFR9.9 |
| 5 単体テスト | 画面部品のテスト（6つの部品ごとの vitest-axe 1件以上、`UserAdminPage.test.tsx` など）、`api/userAdminApi.test.ts`・`failureMessage.test.ts`・`lockedUntil.test.ts`、性質ベースのテスト（fast-check、`rowActions.test.ts`・`searchInput.test.ts`、失敗時の乱数の種を残す）、`fieldErrors.ts` を移した後のプリファレンスの既存のテスト、上げた固定先での既存の画面のテスト | 1件でも失敗。テストの時間の上限は原因を確かめずに延ばさない | NFR1.1〜NFR3.3・NFR5.3〜NFR5.5・NFR7.2・NFR8.1〜NFR8.3・NFR9.6・NFR9.7 |
| 6 結合テスト | U5 は足さない。`backend/` に差分が無いため既存のまま通る | 1件でも失敗 | NFR1.1 |
| 7 カバレッジ | フロントエンドの `@vitest/coverage-v8` の `thresholds`（行 80%・分岐 70%、全体の合計）。バックエンドの `packagesJudgedByTotal` とパッケージごとの下限に U5 の作業は無い | 下回る。U5 のために計測の除外を増やさない。`frontend/e2e/` は既存どおり計測の対象外 | NFR9.5 |
| 8 安全の検査 | SpotBugs ＋ FindSecBugs・OSV-Scanner（`vendor/make-you-chic-ui` の lockfile を含む）・Gitleaks を除外を足さずに通す | 既存の基準（重大度 High 以上。フロントエンドの依存は `team.md` の Deployment の決まり） | NFR9.2・NFR9.3 |
| 9 成果物 | `bootWar` と `frontendBundleSize`（初回の JavaScript、gzip で 500KB を超えたら警告だけ） | WAR が作れない（大きさは警告だけ） | NFR5.6 |

## 3. 依存・make-you-chic-ui・`frontend/.npmrc`

| 項目 | 扱い |
|---|---|
| 新しい依存 | 実行時・開発時とも足さない。axe-core・fast-check・Playwright は既存のものを使い、報告の部品の zip の読み込みは `node:zlib`・`node:fs` だけで書く。コード生成で `frontend/package.json` に差分が無いこと、`frontend/package-lock.json` の差分が make-you-chic-ui の版の分だけであることを確かめて記録する（NFR9.2） |
| make-you-chic-ui の固定先 | `077f5b4` から `3481488` 以降へ上げる。取り込む具体のコミットは B5 のコード生成の計画で決め、計画の承認の前に部品の口（`frontend-components.md` 2.4・2.5）があることを確かめる。上げた直後（`UserRowActions` を書く前）に 2.5 の表を部品のソースとテストで確かめ、「依頼者に戻す差」が出たら依頼者に報告する。中身は変えない（NFR9.3、`project.md` の Forbidden） |
| 固定先の更新のコミット | 承認を得た専用のコミットで行い、更新前後のハッシュを記録する（`project.md` の Mandated）。コミットの後に `npm ci` と `./gradlew e2eTest` を流す |
| `frontend/.npmrc` | `ignore-scripts=true` を足し、`engine-strict=true` は残す。固定先の更新と別のコミットにし、コミットの後に `npm ci`・`./gradlew verify`・`./gradlew e2eTest` を流す（失敗の原因を切り分けるため、NFR9.4） |
| `ignore-scripts=true` の効く範囲 | `frontend/` の中のインストールだけ。`verifyPrepare` の `vendorInstall`（`vendor/make-you-chic-ui` の中の `npm ci`）には効かず、今までの手順のまま通ることだけを確かめる。`npx playwright install chromium` はインストールのスクリプトではないため影響しない |
| lockfile と脆弱性 | 依存は lockfile どおりに入れる（`project.md` の Mandated）。make-you-chic-ui の lockfile は OSV-Scanner の対象のまま |

## 4. E2E（`./gradlew e2eTest`、verify と CI の外）

### 4.1 置き場と中身

| 項目 | 作り |
|---|---|
| 110 | `frontend/e2e/110-user-admin-flow.e2e.ts`。代表の流れ（検索 → ロック → 失敗回数を戻す → 止める → 入れない → 停止を解く → 入れる）。自分で作った利用者 U だけを変え、初期管理者は変えない。本物の一覧・409（`USER_ADMIN_NO_CHANGE`）・400（`VALIDATION_FAILED`）と見本の項目の名前と型を照らし合わせる。前提（招待・Mailpit）が無いときは理由の種類だけを注記して飛ばす（NFR9.8・NFR9.9、`functional-spec.md` 9節） |
| 120 | `frontend/e2e/120-user-admin-accessibility.e2e.ts`。U4 の 130 と同じ 20 組（`support/displayCombos.ts`）×11 状態の検査（axe-core の違反 0 件、`REQUIRED_RULES`、横のはみ出し、CSP の違反）と、既定の1組だけの画面の時間の測り1件（NFR7.3、NFR5.1・NFR5.2） |
| 120 の差し替えの口 | page ごとの1つの `page.route` で、同じオリジンの正規化したパスが `/api/admin` ちょうどか `/api/admin/` の下の要求を受ける。見本で返すか、測りのときだけ一覧の GET を本物へ通し、GET 以外は打ち切って記録する。各テストの終わりに、口が受けた件数 1 以上と、打ち切りの記録 0 件を確かめる。口は `page.goto` とログインより前に張る（NFR3.5、`security-design.md` 4節） |
| 本数 | 流れの本数に数えるのは 110 だけ。120 は流れではないため数えない（`team.md` の Testing Posture、`project.md` の学び） |
| 既存のファイル | 010〜100・U4 の 130・`support/` の既存のファイルは変えない。手伝いの置き場とファイルの名前はコード生成の計画で決める（`logical-components.md` 3節） |

### 4.2 設定と報告の部品（B5 で直す）

| 項目 | 直し |
|---|---|
| reporter | html を外し、list・json・報告の部品にする。json の報告（`test-results/e2e-results.json`）は変えず、Build and Test が写す唯一の報告にする |
| trace | 既定を `off` にする。`E2E_TRACE` で `on`・`retain-on-failure` に切り替え、決まった3つの外の値は設定の読み込みで止める。110・120 は `test.use({ trace: e2eTraceMode() })` を置く |
| 効く範囲 | 010〜100・130 の報告にも効く（E2E のファイルは変えない） |
| 報告の部品が探す値 | 環境変数の3つ・値のファイル（`test-results/` の下、権限 600、探し終えたら消す）の値・値の形。値はそのままの形に加えて URL の形と JSON の `\u` の形でも探す。`runTag` は乱数の部分 16 文字以上で、24 文字以上のときだけ単独で探す |
| 報告の部品が探す先 | json の報告（JSON として読み、`attachments[].body` と `stdout`・`stderr` の `buffer` の base64 を復号して探す）、`test-results/` の下のすべてのファイル（zip は `node:zlib` で展開）、前の実行の `frontend/playwright-report/` |
| 前の `playwright-report/` | 残っていれば前の html の報告として探し、見つかれば失敗にして消す手順を示す。見つからなくても消すよう警告する。`e2eTest` が自動で消すことはしない（見つける前に消すと確かめにならないため） |
| 失敗の扱い | 読めない・展開できない・復号できないものは失敗にする。値は表示せず、種類と件数だけを出す |
| 冒頭の説明 | `playwright.config.ts` の冒頭の「結果は list・html に加えて json」を直す |

```text
// playwright.config.ts の変わる所（説明用。security-design.md 3.2 と同じ）
const traceMode = process.env.E2E_TRACE ?? 'off'   // off・on・retain-on-failure の外なら読み込みで止める
reporter: [['list'], ['json', { outputFile: jsonResultsFile }], ['./playwright-secret-check-reporter.ts', {...}]],
use: { ..., trace: traceMode },
```

### 4.3 B5 までの E2E の報告の扱い（Q1: A）

設定の直しは B5 で行い、先取りしません。B2・B4 の統合の前の `./gradlew e2eTest` では、次の順で扱います（`infrastructure-specification.md` 3.1）。

1. 結果を json の報告（`frontend/test-results/e2e-results.json`）から、その Bolt のコード生成の記録に写す。
2. 写した後に `frontend/playwright-report/` と `frontend/test-results/` を消す。
3. 消したことと、報告を共有していないことを、その Bolt のコード生成の記録に書く。

B5 が B4 の後すぐに始まらないときも、この扱いのまま進めます。B5 の最初の実行で前の html の報告が残っていないため部品の失敗は起きにくくなり、前の報告で失敗することは `security-design.md` 3.8 の (6)（わざと残した報告）で確かめます。

### 4.4 B5 の中で流す時点と前提

| 時点 | 流すもの | 記録すること |
|---|---|---|
| 固定先の更新のコミットの後 | `npm ci`・`./gradlew e2eTest` | 010〜100・130 が通ったこと |
| `.npmrc` のコミットの後 | `npm ci`・`./gradlew verify`・`./gradlew e2eTest` | すべて通ったこと |
| 統合の前（画面の作業の後） | `./gradlew verify`・`./gradlew e2eTest` | 010〜100・130・110・120 が通ったこと、110 で飛ばした注記が出ていないこと、報告の部品の結果（見つかった件数 0）、120 の組と状態ごとの結果、`user-admin-screen-ms` の値 |
| リリースの前 | `./gradlew e2eTest` | 同上 |

- 前提: Mailpit を `docker compose --profile mail up -d mailpit` で起動しておく（`e2eTest` は始める前に届くかを確かめ、届かなければ失敗する）。ブラウザは `npx playwright install chromium`。
- 長い実行は `caffeinate -i` で台本の全体を包む（`project.md` の Testing Posture の学び）。
- 固定先の更新と `.npmrc` のコミットの後の実行が、`playwright.config.ts` の直しより前になるときは、その実行も html と trace を作るため、4.3 と同じく結果を写した後に報告を消して記録する。直しをどの順に置くかはコード生成の計画で決める（7節の確かめたいこと）。
- 不安定なときは `team.md` の Testing Posture「不安定なテストと CI の失敗」の決まりで扱う。時間の上限を原因を確かめずに延ばさない。

### 4.5 報告の部品とわざと値を入れた確かめ（コード生成 B5）

`security-design.md` 3.8 のとおり、次を種類ごとに確かめて記録し、確かめに使った報告は消します。(1) json の報告の注記に U の氏名、(2) `test-results/` の下の zip（圧縮あり）の中に U のパスワードの URL の形、(3) `error-context.md` に U のメールアドレス、(4) 値のファイルを書かずに値の形だけで見つかること、(5) 壊れた zip で失敗すること、(6) 残した `playwright-report/` で失敗すること、(7) json の報告の添付の `body`（base64）に U のメールアドレス、(8) `runTag` が 24 文字より短いときに単独では探さず警告すること。あわせて、値のファイルが探し終えた後に無いこと、作ったときの権限が 600 であることを確かめます。120 の差し替えの口は、わざと POST を送る形・口を張らない形・二重のスラッシュの形を一度ずつ流して失敗になることを確かめ、元に戻してコミットに含めません（`security-design.md` 4.3）。

### 4.6 README の直し（B5 のコード生成）

`README.md` の「ビルドした WAR での画面の確認（E2E）」の節を直します。

| 直すこと |
|---|
| 表に 110・120 の行を足す |
| 「110 について」（流れの本数に数えるのは 110 だけ、自分で作った利用者だけを変える、Mailpit の起動が要る、飛ばした注記の確かめ）と「120 について」（流れに数えない、差し替えの口と書き換えの打ち切り、測りの記録、Mailpit の起動が要る）の節を足す |
| 結果は json だけになり、html の報告を作らないこと |
| 手元だけ trace を有効にする手順（`E2E_TRACE=retain-on-failure`、その実行は合否に使わない、見た後に `frontend/test-results/` を消す） |
| 失敗の画面の写し（`error-context.md`）で報告の部品が失敗したときは、写しを読んで原因を確かめてから `frontend/test-results/` を消す手順 |
| 前の `frontend/playwright-report/` が残っていれば消す手順 |

## 5. 統合と配備の流れ

| 順 | すること | 出典 |
|---|---|---|
| 1 | `develop` から短命のブランチ（例 `feature/260930-user-admin-b5`）を作って作業する。worktree は使わない | `team.md` の Way of Working |
| 2 | 固定先の更新を専用のコミットにし、前後のハッシュを記録する。`.npmrc` は別のコミットにする。それぞれの後に 4.4 の検査を流す。画面の作業のコミットの分け方はコード生成の計画で決める | `project.md` の Mandated、NFR9.3・NFR9.4 |
| 3 | 統合の前に `./gradlew verify`（コンテナの実行環境あり、対象DB のテストを飛ばさない）と手元の `./gradlew e2eTest` を通す | `team.md` の Way of Working・Testing Posture |
| 4 | 短命のブランチから `develop` へ fast-forward で統合する（squash でない。固定先の更新を専用のコミットとして残すため） | `team.md` の Way of Working、`bolt-plan.md` の B5 |
| 5 | 依頼者が `develop` を push する前に、取り込む make-you-chic-ui のコミットがそのリポジトリの公開の側にあることを確かめる（CI の取得で失敗しないため）。push は依頼者が行う | `.github/workflows/ci.yml`、`team.md` の Way of Working |
| 6 | push の後の CI が通ったことを記録する | `bolt-plan.md` の共通の完了の条件 |
| 7 | WAR を作り、イメージを作り直して `docker compose up -d`。イメージの作り方・`compose.yaml`・`.env`・ボリューム・JVM の設定は変えない | `team.md` の Deployment |
| 8 | ヘルスチェックとスモークテストで確かめる。U5 の分は、管理者でログインしてサイドバーの「利用者の管理」から一覧が開けること。5つの操作と氏名・言語の変更は監査に残り利用者の状態を変えるため、スモークテストに入れるかは deployment-pipeline の段で決め、行うときは先に依頼者に伝える | `team.md` の Deployment、`project.md` の Corrections |

環境の昇格（検証環境・本番環境）は、配備先が決まるまで作りません。

## 6. 戻し

| 項目 | 扱い |
|---|---|
| 前の版への戻し | 直前の版のイメージで起動し直すだけ（既存の決まり）。make-you-chic-ui の固定先と `.npmrc` はビルドの時だけに効くため、イメージを戻せば画面も戻る |
| DB スキーマ・サーバーの状態 | U5 は変えないため、内部DB のバックアップと戻しの練習の論点は無い |
| 設定（`.env`・`application.yaml`） | 変えないため、戻すものは無い |
| ブラウザに残る値 | U5 はブラウザの保存を使わないため、残る値は無い |

## 7. DevSecOps と Compliance の視点

| 観点 | 確かめた結果 |
|---|---|
| 認可 | 画面の押せない形はサーバーの判定の代わりにしない。管理の API の 401・403・200・自分自身の操作の拒否・最後の管理者の保護と監査は U3 のサーバー側のテストが受け持つ。U5 は `backend/` を変えない（NFR1.1、`project.md` の Mandated・Forbidden） |
| 入力と出力 | 失敗の文言に `detail`・`title` を出さず、HTML を直接埋め込まない（NFR3.2、NFR9.1） |
| CSP | 変えない。120 のためにも緩めない。120 で CSP の違反を記録する（NFR9.1） |
| 秘密情報 | 新しい秘密を持たない。E2E の資格情報と U の値は報告に残さない（B5 の後は html と trace を作らず、報告の部品で探す。B5 までは 4.3）。Gitleaks の除外を足さない |
| 依存の脆弱性とサプライチェーン | 新しい依存を足さない。make-you-chic-ui の lockfile は OSV-Scanner の対象のまま。`frontend/` のインストールでパッケージのスクリプトを動かさない（NFR9.2〜NFR9.4） |
| 報告の置き場 | `frontend/test-results/`・`frontend/playwright-report/` は `.gitignore` の対象のままで、コミット・共有しない。値のファイルは権限 600 で、探し終えたら消す |
| 公開のリポジトリ | テストのデータは `example.com` の宛先と架空の氏名だけ |

## 8. 記録すること（コード生成と Build and Test）

| 記録 | 段 |
|---|---|
| `backend/`・`backend/src/main/resources/application.yaml`・`frontend/package.json` に差分が無いこと、`frontend/package-lock.json` の差分が make-you-chic-ui の版の分だけであること、`vendor/make-you-chic-ui` の中身に差分が無いこと | コード生成（B5） |
| 固定先の更新前後のハッシュ、`frontend-components.md` 2.5 の表の確かめの結果 | コード生成（B5） |
| 固定先の更新と `.npmrc` のそれぞれのコミットの後の `npm ci`・`./gradlew e2eTest`（`.npmrc` の後は `./gradlew verify` も）の結果 | コード生成（B5） |
| `./gradlew verify` が通ったこと、フロントエンドのカバレッジの値 | コード生成・Build and Test |
| 初回の JavaScript の大きさ（固定先を上げる前と U5 の後） | コード生成（B5） |
| `./gradlew e2eTest` で 010〜100・130・110・120 が通ったこと、110 で飛ばした注記が出ていないこと、120 の組と状態ごとの結果 | コード生成・Build and Test |
| 報告の部品の結果（見つかった件数 0）と、4.5 のわざと値を入れた確かめ（(1)〜(8)）と差し替えの口の3つの確かめ | コード生成（B5）・Build and Test（部品の結果） |
| B5 の最初の実行で前の html の報告を見つけたかと、消した結果 | コード生成（B5） |
| `user-admin-screen-ms` の値（`list` と `nextPage`、1回目と 2〜5 回目、`nextPage` は API の時間を含まないこと、`list` は差し替えの口の上乗せを含むこと） | コード生成（B5）・Build and Test |
| B2・B4 の `e2eTest` の後に報告を消したことと共有していないこと（4.3） | B2・B4 のコード生成 |
| push の後の CI の結果 | Build and Test |

## 9. 上流との差

承認済みの文書は書き換えず、差をここに記録します（`project.md` の Way of Working）。

| 対象 | 承認済みの形 | この段の設計 | 理由 |
|---|---|---|---|
| B5 までの E2E の報告 | NFR 設計は設定の直しを B5 に置く。U4 の基盤の設計は「先取りしない」 | 先取りせず、B2・B4 では結果を写した後に報告を消して記録する（4.3） | Q1: A。U4 のレビューの R-03 への答え。B2・B4 の手順の追加で、食い違いではない |
| 固定先の更新と `.npmrc` の後の E2E の報告 | NFR 設計は2つのコミットの後に `e2eTest` を流すことだけを書く | 設定の直しより前に流すときは 4.3 と同じく報告を消す（4.4） | Q1: A の考え方を B5 の中の同じ状況に当てた追加。食い違いではない |
| push の前の確かめ | 上流に記述が無い | 取り込む make-you-chic-ui のコミットが公開の側にあることを push の前に確かめる（5節の 5） | CI の `submodules: true` の取得で埋めた追加 |
| スモークテスト | 上流に記述が無い | 一覧が開けることを U5 の分とし、操作を入れるかは deployment-pipeline の段で決める（5節の 8） | `team.md` の Deployment（配備のたびのスモークテスト）。操作は監査に残るため段で決める |
| 承認済みの設計の文書 | — | 食い違う点は無い。NFR 設計の承認の場の申し送り（json の復号と 3.8 の (7)・(8)、`runTag` の作り方と検索の上限、口の件数の確かめと 4.3 の3つの確かめ、`loadingRef`・`submittingRef` の戻りのテスト、前の html の報告を消した結果）はコード生成の計画で扱う | NFR Design の承認の場の決定のとおり |

### 承認の場で確かめたいこと

| # | 中身 |
|---|---|
| 1 | Q1 A は B2・B4 を挙げた。`bolt-plan.md` の共通の完了の条件では B1 も統合の前に `./gradlew e2eTest` を流すため、B1 の実行の後も同じく結果を写した後に報告を消して記録するか（B2 の実行の後に消せば B1 の残りも消えるが、B1 から B2 までの間は残る） |
| 2 | B5 の中で、固定先の更新と `.npmrc` のコミットの後の `e2eTest` が `playwright.config.ts` の直しより前になるとき、4.3 と同じ扱いにすること（4.4）でよいか。直しを先に置く順にするかはコード生成の計画で決める |
