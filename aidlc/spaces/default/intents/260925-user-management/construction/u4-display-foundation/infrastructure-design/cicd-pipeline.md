# CI/CD Pipeline — U4 画面の表示の設定の土台（u4-display-foundation）

U4 の検査の流れ（1コマンドの検査と CI）、依存とサブモジュールの扱い、実際のブラウザの検査（E2E）、配備と戻しを示します。配備先は開発者の PC 上のコンテナだけで、クラウドの基盤は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。

- 答え: `infrastructure-design-questions.md`（要点 11〜18、Q1: A、Q2: A）
- 上流: `construction/u4-display-foundation/nfr-design/`（`performance-design.md` の3節・4節、`security-design.md` の5〜7節、`logical-components.md` の5節・7節）、`construction/u4-display-foundation/nfr-requirements/tech-stack-decisions.md`、同じ段の `construction/u1-mail/infrastructure-design/cicd-pipeline.md`（7節）・`construction/u8-instance-appearance/infrastructure-design/cicd-pipeline.md`（どれも `aidlc/spaces/default/intents/260925-user-management/` の下）
- 既にある仕組み（正とする）: `build.gradle.kts`（`verify`・`frontendBundleSize`・`osvScan`・`vendorInstall`・`vendorBuild`・`vendorUnchanged`・`e2eTest`）・`.github/workflows/ci.yml`・`.github/dependabot.yml`・`frontend/playwright.config.ts`・`frontend/vitest.config.ts`・`frontend/package-lock.json`・`config/npm-build-tools.txt`・`.gitignore`・README の E2E の節

## 1. CI（GitHub Actions）

`.github/workflows/ci.yml` は変えません。

| 項目 | 今の形 | U4 での扱い |
|---|---|---|
| きっかけ | `develop` へのプッシュ・`v*` のタグ・手動 | 変えない |
| サブモジュール | `actions/checkout` の `submodules: true` で固定先のコミットを取得 | make-you-chic-ui の新しい固定先（2節）も同じ設定で取得する |
| 実行 | `./gradlew verify`（`ubuntu-latest`、制限時間 60 分） | 変えない。E2E（`./gradlew e2eTest`）は CI に入れない（`team.md` の Testing Posture） |
| 秘密 | CI は秘密を使わない | U4 は秘密を足さない |

## 2. 1コマンドの検査（`./gradlew verify`）の段と関門

段は増やさず、既存の段で U4 の変更を確かめます。どれか1つでも失敗したら全体を失敗とします（既存のとおり）。ビルドの設定の変更はありません。

| 段 | U4 で動くもの | 関門（失敗の条件） |
|---|---|---|
| 0 準備 | `vendorInstall`・`vendorBuild`（新しい固定先の make-you-chic-ui を lockfile どおりに入れてビルド）、`frontendInstall`（`npm ci`） | 入れられない・ビルドできない |
| 1〜3 フォーマット・リンタ・ライセンスヘッダー | U4 の TypeScript・CSS と `frontend/e2e/` の新しいファイル | 既存の基準（Prettier・oxlint・ESLint・Stylelint・ライセンスヘッダー） |
| 4 ビルド | 型検査と画面のビルド。`vendorUnchanged`（サブモジュールの中身を変えていない） | 型の誤り、ビルドの失敗、サブモジュールの中身の変更 |
| 5 単体テスト | 画面部品のテスト（Vitest ＋ Testing Library ＋ user-event ＋ vitest-axe）、性質ベースのテスト（fast-check、失敗時の `seed`・`path` を出力に残す）、ApiClient の単体テスト | 1件でも失敗 |
| 7 カバレッジ | フロントエンドの `@vitest/coverage-v8` の `thresholds`（行 80%・分岐 70%） | 下回る。U4 のために計測の除外を増やさない。`frontend/e2e/` は既存どおり計測の対象外（NFR9.8） |
| 8 安全の検査 | `osvScan`（`frontend/package-lock.json`・`vendor/make-you-chic-ui/package-lock.json` を含む）・Gitleaks | 既存の基準（3節） |
| 9 成果物 | `frontendBundleSize`（初回の JavaScript、警告だけ）と `bootWar` | WAR が作れない（大きさは警告だけ） |

## 3. 依存とサブモジュール

| 対象 | 扱い | 関門 |
|---|---|---|
| `@fontsource/noto-serif-jp` 5.3.0 | `dependencies`（実行時）。OFL-1.1（採用の記録は機能設計の 9.2）。推移依存が無いことをコード生成で記録する | 実行時の依存のため、重大度 High 以上で統合を止める |
| `axe-core` 4.13.0 | `devDependencies` に明示で足す（今は vitest-axe の推移依存として lockfile に 4.13.0 が載っている）。MPL-2.0（`tech-stack-decisions.md` の 2.1）。改変しない。lockfile の axe-core が1つの版にそろうことを記録する。成果物を作る道具ではないため `config/npm-build-tools.txt` に足さない | 脆弱性は警告。悪意のあるパッケージ（OSV の `MAL-`）だけ止める |
| lockfile | どちらも `frontend/package-lock.json` で固定し、CI は `npm ci` | 既存の `osvScan` の対象 |
| Dependabot | 既存の npm（`/frontend`）の項目で知らせが来る。`team.md` の受け方（画面でマージせず、手元で更新して verify を通してから統合） | — |
| axe-core が成果物に入らないこと | ビルドの後の `dist/` に axe-core の本体に特有の文字列（`Deque Systems`）を含むファイルが無いことを、コード生成で1回確かめて記録する | 入っていたら直す（NFR9.6） |
| make-you-chic-ui の固定先 | `edb1f943c0e66293494fa974605f34fcd7e258d7` → `735ef04ce6eb618cb875f5c4b31c1645a1f84c28`（Modal・RadioGroup・Table・Dropdown・Button の変更と見本の画面、8 コミット。手元の履歴にあることを確かめた）。B4 で承認を得た専用のコミットで更新し、前後のハッシュを記録する。中身は変えない | 更新した版の lockfile は `osvScan` の対象（実行時の依存の High 以上で止める） |
| B4 の統合の形 | 固定先の更新を含むため、短命のブランチから `develop` へ fast-forward で統合してよい。統合の前に `./gradlew verify` を通す | `team.md` の Way of Working、`project.md` の Mandated |

## 4. 実際のブラウザの検査（`./gradlew e2eTest`、verify と CI の外）

### 4.1 置き場と中身

| 項目 | 作り |
|---|---|
| ファイル | `frontend/e2e/050-display-accessibility.e2e.ts`（番号はコード生成で確かめる）。既存の 010〜040 のファイルは変えない |
| 実行 | `playwright.config.ts` の `workers: 1`・`fullyParallel: false` のまま、番号の順で既存の後に動く。ブラウザは既存どおり Chromium だけ |
| B4 の対象 | ログインの画面の最初の状態を 20 組（(a) テーマ × 文字の大きさの6組、(b) ブランドカラー × テーマの8組、(c) 幅 375px のテーマ × 文字の大きさの6組）で検査する（NFR7.3・NFR7.5） |
| axe | 検査のページで `page.evaluate` を使って axe-core を読み込み、WCAG 2.0・2.1 の A・AA のタグ（`wcag2a`・`wcag2aa`・`wcag21a`・`wcag21aa`）で流す。違反 0 件で合格、`incomplete` は記録だけ。`color-contrast`・`scrollable-region-focusable` が流れたことを確かめる。`bypassCSP`・`addScriptTag` は使わない |
| 横のはみ出し | `document.documentElement.scrollWidth` が `window.innerWidth` 以下 |
| 時間の測定 | 同じファイルで、キャッシュが空の新しいコンテキストで5回測る。時間では失敗させない（NFR6.1） |
| 失敗の条件にする確かめ | 測った5回すべてで、CSP の違反が 0 件、`sans` のときに Noto Serif JP のフォントのファイルへの要求が無い（NFR9.4・NFR6.4） |
| 本数 | 流れの E2E ではないため、`team.md` の「代表の流れを1本まで」に数えない（NFR9.11） |
| サーバーの状態 | B4 の検査はログインせず、内部DB・監査の記録・ロックの回数を変えない |

コード生成の計画に載せること（NFR 設計の承認の場の決定）:

- U4 R-01: 5回の各回について「監視（`securitypolicyviolation` と要求の一覧の収集）を張る → `page.goto` → 測定 → 判定」の順にする。既存の `010-skeleton.e2e.ts` の `collectProblems` にならう。
- U4 R-02: fontsource の CSS が `unicode-range` を持たない前提をコード生成で確かめ、崩れたときの方針（設計のやり直しか、確かめの緩和か）を一言記録する。

### 4.2 結果の読み取り（Q2: A）

- `frontend/playwright.config.ts` の reporter に `json` を足し、結果を管理外の `frontend/test-results/` の下の1つのファイル（例 `e2e-results.json`）に書きます。既存の `list`・`html` は残します。
- `.gitignore` は変えません（`frontend/test-results/` は既に管理外）。新しい依存は無い（`@playwright/test` の標準の reporter）。
- 測った時間・組ごとの成否・違反の件数と規則の名前・`incomplete` は、テストの注記（`test.info().annotations`）と添付（JSON）に残り、Build and Test はこの JSON のファイルから値を読んで結果に写します。
- ファイルに時間・規則の名前・件数だけが入り、秘密が入らないことをコード生成で確かめます。B5 の U5〜U7 の検査も同じファイルに記録が入ります。

### 4.3 実行の時点と前提

- 画面・認証に関わる変更を統合する前（B4・B5）と、リリースの前に、手元で `./gradlew e2eTest` を実行します（`team.md` の Testing Posture）。
- U1 の決定で、`e2eTest` は始める前に Mailpit の API（`127.0.0.1:8025`）に届くかを確かめます。050 がメールを使わなくても、実行の前に `docker compose --profile mail up -d mailpit` が要ります。
- E2E の WAR は見た目の設定なし（既定の blue・sans）で動きます（U8 の決定）。組のブランドカラーは `page.route` で `/api/appearance` の答えを差し替え、時間の測定では本物の応答を使います。
- 事前に `npx playwright install chromium`。長い実行は `caffeinate -i` を付けます。
- README の E2E の節の表に 050 の行（アクセシビリティの検査と最初の画面の時間。時間は記録だけで失敗させないこと、Mailpit の起動が要ること）を足します。

## 5. 検査の結果のファイルと秘密

| 項目 | 扱い |
|---|---|
| `frontend/test-results/`・`frontend/playwright-report/` | 既存どおり管理外。コミット・共有しない |
| 失敗のときのトレース（`trace: 'retain-on-failure'`） | 実行ごとに作る仮の初期管理者のパスワード・トークンが含まれうる。コミット・共有せず、新しい秘密の置き場は作らない |
| Build and Test に写すもの | 組ごとの成否・違反の件数と規則の名前・`incomplete`・時間の値だけ |
| CI の秘密 | U4 は足さない |

## 6. 配備

既存の流れのままです。

1. `./gradlew verify` を通して `develop` へ統合する（B4 は fast-forward でよい）。
2. WAR を作り、イメージを作り直して `docker compose up -d`。
3. ヘルスチェックと既存のスモークテスト（ログインの画面が出ること）で確かめる。

画面の時間は配備したアプリでは測りません（Q1: A）。配備したアプリの API の時間は U8 の k6 と既存のスモークテストで押さえます。環境の昇格（検証環境・本番環境）は、配備先が決まるまで作りません。

## 7. 戻し

| 項目 | 扱い |
|---|---|
| 前の版への戻し | イメージだけを直前の版に戻す既存の決まりのまま |
| サーバーの状態 | U4 は変えないため、戻しで消すものは無い |
| ブラウザに残った値 | U4 の鍵は直前の版では読まれない。写しの鍵は make-you-chic-ui が読む既存の形の値のため、害は無い見込み。具体の確かめは配備の段で決める |
| サブモジュール | 戻すときは直前の版のイメージに入っている画面を使う。固定先を戻す必要があれば、承認を得た専用のコミットで行う |

## 8. 上流との差

承認済みの文書は書き換えず、差をここに記録します（`project.md` の決まり）。

| 対象 | 承認済みの形 | この段の設計 | 理由 |
|---|---|---|---|
| 検査の結果の残し方 | NFR 設計は「テストの注記と添付（JSON）で残し、Build and Test が結果に写す」 | reporter に `json` を足し、`frontend/test-results/` の下の1つのファイルに書く（Q2: A） | 写す作業を確かにするための追加。食い違いではない |
| `e2eTest` の前提 | U4 の NFR 設計に Mailpit の記述は無い | 050 だけを流すときも Mailpit の起動が要る | U1 の同じ段の決定（Q1 A）の影響。既存の決まりで埋めた追加 |
| 結果のファイルの扱い | 上流に記述が無い | トレースに仮の資格情報が含まれうるため、コミット・共有しない | `project.md` の Forbidden（秘密情報）で埋めた追加 |
| 承認済みの設計の文書 | — | 食い違う点は無い。U4 R-01・R-02 はコード生成の計画で拾う（4.1） | 承認の場の決定のとおり |
