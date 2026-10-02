# CI/CD Pipeline — U4 管理の画面の 403 の共通の扱い（u4-admin-forbidden-ui）

U4 の検査の流れ（CI と1コマンドの検査）、依存の扱い、実際のブラウザの検査 130（E2E）、配備と戻しを示します。U4 は新しい依存・API・スキーマ・設定・イメージを持たないため、CI と1コマンドの検査と配備の流れは **変えず**、この文書は既にある仕組みの記録として、どの段で何を確かめるかを書きます。配備先は開発者の PC 上のコンテナだけで、クラウドの基盤・IaC・警報の通知先は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。

- 答え: `infrastructure-design-questions.md`（質問 0 問、要点 4〜6、まとめの確認は Looks correct）
- 上流: `construction/u4-admin-forbidden-ui/nfr-design/`（`logical-components.md` 6節〜8節、`security-design.md` 5節・6節・9節と「承認の場の決定」、`performance-design.md` 5節）、`construction/u4-admin-forbidden-ui/nfr-requirements/tech-stack-decisions.md`（NFR7.2・NFR7.3・NFR9.6〜NFR9.12）・`security-requirements.md`（NFR1.1・NFR3.2・NFR9.5）、`inception/delivery-planning/bolt-plan.md`（B2）、同じ Bolt の `construction/u2-shared-paging/infrastructure-design/cicd-pipeline.md`、`construction/u5-user-admin-ui/nfr-design/security-design.md` 3.2（どれも `aidlc/spaces/default/intents/260930-user-admin/` の下）
- 既にある仕組み（正とする、読むだけ）: `.github/workflows/ci.yml`・`build.gradle.kts`（`verify`・`frontendBundleSize`・`e2eTest` と Mailpit の確かめ）・`frontend/vitest.config.ts`・`frontend/playwright.config.ts`・`frontend/playwright-secret-check-reporter.ts`・`frontend/e2e/`（010〜100 と `support/`）・README の E2E の節

## 1. CI（GitHub Actions）

`.github/workflows/ci.yml` は変えません。

| 項目 | 今の形 | U4 での扱い |
|---|---|---|
| きっかけ | `develop` へのプッシュ・`v*` のタグ・手動 | 変えない。CI は統合の後の再確認（`team.md` の Way of Working） |
| 実行 | `./gradlew verify`（`ubuntu-latest`、制限時間 60 分） | 変えない。E2E（`./gradlew e2eTest`）と 130 は CI に入れない（`team.md` の Testing Posture） |
| サブモジュール | `submodules: true` で固定先のコミットを取得 | 変えない。U4 は make-you-chic-ui の固定先を動かさない（NFR9.7） |
| 依存の入れ方 | lockfile どおり（`npm ci`） | 変えない。U4 は lockfile を変えない（NFR9.6） |
| 秘密 | CI は秘密を使わない | U4 は秘密を足さない |
| 成果物 | WAR をコミットのハッシュの名前で保存 | 変えない。WAR の中の `dist` の中身が変わるだけ |

CI が失敗したときは、次の Bolt に進む前に `team.md` の Testing Posture「不安定なテストと CI の失敗」の決まりで扱います。

## 2. 1コマンドの検査（`./gradlew verify`）の段と関門

段・関門・設定は増やさず変えません。U4 の変更は既存の段で次のとおり確かめられます。どれか1つでも失敗したら全体を失敗とします（既存のとおり）。

| 段 | U4 で確かめること | 関門（失敗の条件） | 当たる要件 |
|---|---|---|---|
| 0 準備 | `frontendInstall`（`npm ci`）、`vendorUnchanged`（make-you-chic-ui を変えていないこと） | 入れられない・サブモジュールの中身が変わっている | NFR9.6・NFR9.7 |
| 1 フォーマット | U4 の TypeScript・CSS と `frontend/e2e/130-admin-forbidden-accessibility.e2e.ts`・`support/loginPreferences.ts`（Prettier） | 書式の違い | NFR9.5 |
| 2 リンタ | oxlint（`react/no-danger`・`no-eval`・`no-new-func`・`no-script-url` など）・ESLint（`export default`・`enum` の禁止、react-hooks）・Stylelint。除外を足さない | 1件でも error | NFR9.5・NFR9.12 |
| 3 ライセンスヘッダー | 新しいファイルの先頭の `/* ... */` の Apache License 2.0 のヘッダー（`check-license-header.mjs`） | ヘッダーが無い・形が違う | — |
| 4 ビルド | 型検査（`tsc --noEmit`、`frontend/tsconfig.json` の `include` に `e2e` がある）と Vite のビルド。`LoginPreferences` の型の変更と 130 もここで型を確かめる | 型の誤り・ビルドの失敗 | NFR9.5・NFR9.12 |
| 5 単体テスト | 画面部品のテスト（`AdminForbiddenView.test.tsx` の vitest-axe 1件を含む）、`AdminForbiddenProvider.test.tsx`・`ShellLayout.test.tsx`・`AppRouter.test.tsx`・`decideRoute.test.ts`・`apiClient.test.ts`・`DisplaySettingsProvider.test.tsx` など、性質ベースのテスト（fast-check、`adminForbidden.test.ts`・`forbiddenHeading.test.ts`・`adminAreaStatus.test.ts`、失敗時の乱数の種を残す）、書き換える既存の画面のテスト | 1件でも失敗。テストの時間の上限は原因を確かめずに延ばさない | NFR7.2・NFR9.1・NFR9.2・NFR9.9・NFR9.11 |
| 6 結合テスト | U4 は足さない。`backend/` に差分が無いため既存のまま通る | 1件でも失敗 | NFR1.1 |
| 7 カバレッジ | フロントエンドの `@vitest/coverage-v8` の `thresholds`（行 80%・分岐 70%、全体の合計）。バックエンドの `packagesJudgedByTotal` とパッケージごとの下限に U4 の作業は無い | 下回る。U4 のために計測の除外を増やさない。`frontend/e2e/` は既存どおり計測の対象外 | NFR9.8 |
| 8 安全の検査 | SpotBugs ＋ FindSecBugs・OSV-Scanner・Gitleaks を除外を足さずに通す | 既存の基準（重大度 High 以上） | NFR9.6 |
| 9 成果物 | `bootWar` と `frontendBundleSize`（初回の JavaScript、gzip で 500KB を超えたら警告だけ） | WAR が作れない（大きさは警告だけ） | NFR9.4 |

補足:

- 性質ベースのテストの失敗のときの乱数の種は、既存の仕組みのまま実行の記録に残ります（fast-check は seed と path を出す）。
- 新しい性能の目標・k6 の場面・指標・警報は足しません（`monitoring-design.md`）。

## 3. 依存と make-you-chic-ui

| 項目 | 扱い |
|---|---|
| 新しい依存 | 実行時・開発時とも足さない。コード生成で `frontend/package.json`・`frontend/package-lock.json` に U4 の変更による差分が無いことを確かめて記録する（NFR9.6）。ライセンスの確かめと lockfile の更新は要らない |
| axe-core・Playwright・fast-check | 既存の devDependencies を使う |
| make-you-chic-ui | 今の固定先の `Alert` だけで S6 を作る。中身も固定先も変えない（NFR9.7、`project.md` の Forbidden）。コード生成で `vendor/make-you-chic-ui` に差分が無いことを記録する |
| 統合の形 | B2 は固定先の更新を含まないため、`develop` へ squash で統合する。U2・U4 の単位ごとの squash（2コミット）にするかはコード生成の計画で決める（`bolt-plan.md`、`team.md` の Way of Working） |

## 4. 実際のブラウザの検査 130（`./gradlew e2eTest`、verify と CI の外）

### 4.1 置き場と中身

| 項目 | 作り |
|---|---|
| ファイル | `frontend/e2e/130-admin-forbidden-accessibility.e2e.ts`（新しいファイル）。既存の 010〜100 と `030-admin-access.e2e.ts` は変えない。U5 の 110・120 と番号が重ならない |
| 組 | 既存の 20 組（`support/displayCombos.ts`。既定の幅のテーマ2×文字の大きさ3の6組、ブランドカラー4×テーマ2の8組、幅 375px のテーマ2×文字の大きさ3の6組）。上の帯の氏名を架空の2語にした状態で、すべての組を検査する（NFR7.3） |
| 差し替え | 管理の API では `GET /api/admin/check` の1本だけを 403・`ACCESS_DENIED` の見本に差し替える。ほかに差し替えるのはログインと復元の応答の `user` の項目（組の値と2語の氏名、`routeLoginPreferences`）だけ（`security-design.md` 5節） |
| 確かめ | 組ごとに axe-core の違反 0 件（WCAG 2.0・2.1 の A・AA、`REQUIRED_RULES` が走った）、横のはみ出し無し、Alert が `role="status"`、見出しにフォーカス、Avatar の文字の数が 2、`detail` の目印が画面に出ない、CSP の違反 0 件、管理の API への GET 以外の要求 0 件（`logical-components.md` 6.4） |
| コンソールの表示 | 差し替えた 403 の読み込みの失敗の表示だけを、130 の中の見張りで件数を数えて引く。共有の `support/pageProblems.ts` の既定は変えない（`logical-components.md` 6.3） |
| 確かめない範囲 | ForbiddenByRoute は部品のテスト（`decideRoute.test.ts`・`AppRouter.test.tsx`）で確かめる。画面の時間は測らず記録もしない（NFR9.3） |
| 本数 | 操作の流れではないため、`team.md` の「機能の Intent ごとに代表の流れを1本まで」に数えない（この Intent の代表の流れは U5 の 110、NFR9.10） |

### 4.2 実行の時点と前提

| 項目 | 扱い |
|---|---|
| いつ流すか | B2 の統合の前（画面・認証に関わる変更）とリリースの前に、手元で `./gradlew e2eTest` を流す（`bolt-plan.md`、`team.md` の Testing Posture）。Build and Test でも流す |
| 何が通るか | 既存の 010〜100 と 130 のすべて（B5 の後は 110・120 も）。共有の手伝い `loginPreferences.ts` に手が入るため、既存の E2E が通ることの確かめを兼ねる（NFR9.10） |
| 並び | `workers: 1` のまま、番号の順に同じ WAR・内部DB・初期管理者を共有する。130 は状態を変える要求を送らず、初期管理者の状態を変えない |
| Mailpit | `e2eTest` は始める前に Mailpit の API（`127.0.0.1:8025`）に届くかを確かめ、届かなければ失敗する。130 はメールを使わないが、実行の前に `docker compose --profile mail up -d mailpit` が要る。130 だけを流すときも同じ |
| ブラウザ | 既存の Chromium（`npx playwright install chromium`） |
| 長い実行 | `caffeinate -i` で台本の全体を包む（`project.md` の Testing Posture の学び） |
| 不安定なとき | `team.md` の Testing Posture「不安定なテストと CI の失敗」の決まりで扱う。時間の上限を原因を確かめずに延ばさない |

### 4.3 コード生成の計画へ申し送ること

| # | 中身 | 出典 |
|---|---|---|
| R-01 | 130 で読み直しの応答を待つ約束（`waitForResponse`）を、管理の入口を開く操作より前に作る順を計画に明記する | `security-design.md` の「承認の場の決定」 |
| R-02 | 「ホームへ戻る」の確かめを全組で行うか既定の組だけかを決め、あわせて S6 が消えた後の問題の一覧の確かめの対象の組と、20 組の実行時間を書く | 同上 |
| — | README の E2E の表に 130 の行と「130 について」の節（流れに数えない、差し替えは `GET /api/admin/check` の1本だけ、時間は測らない、130 だけを流すときも Mailpit の起動が要る）を足す | 要点 4、既存の README の E2E の節の形 |

## 5. 検査の結果のファイルと秘密

| 項目 | 扱い |
|---|---|
| 設定 | `frontend/playwright.config.ts` は U4 では変えない。資格情報（仮の署名鍵・初期管理者のメールアドレス・仮のパスワード）は既にプロセスの環境変数で渡しており、`webServer.env` に置かない |
| json の報告の確かめ | 既存の `frontend/playwright-secret-check-reporter.ts`（`SECRET_ENV_NAMES` の値が `test-results/e2e-results.json` に含まれれば実行を失敗にし、値は表示しない）がそのまま受け持つ。130 はアクセストークンをテストのコードで取り出さないため、トークンが報告に入る経路を作らない（NFR3.2） |
| 2語の氏名 | 値を注記・添付・標準出力に入れない。`e2eTest` の後に、値が json の報告に含まれないことを、値を表示しない形で件数を数えて記録する |
| html の報告とトレース | B2 の時点では既存の設定のまま html の報告（`frontend/playwright-report/`）と失敗のときのトレース（`trace: 'retain-on-failure'`）が残り、ログインの手伝いが入れる仮の資格情報が載りうる。これは 010〜100 と同じ既存の状態で、README の「共有しない」の決まりで扱う。B5 で U5 が設定の1か所を直し（html を外し、trace の既定を `off`）、130 にも効く。U4 は設定を先取りしない。その代わり、B2 で 130 を含む `./gradlew e2eTest` を流した後は、json の報告から結果を記録してから `frontend/playwright-report/` と `frontend/test-results/` を消し、消したことと共有していないことを B2 のコード生成の記録に書く（依頼者の決定、U5 の Q1 A と U5 R-02 の決定。B1・B4 と B5 の中の設定を直す前の実行も同じ。いま手元に残っている報告は B1 の始めに消す） |
| 置き場 | `frontend/test-results/`・`frontend/playwright-report/` は既存どおり管理外。`.gitignore` は変えない。コミット・共有しない |
| Build and Test への写し | 組ごとの成否・違反の件数・規則の名前・はみ出しの有無・Avatar の文字の数・除いたコンソールの表示の件数だけを写す |

## 6. 統合と配備の流れ

| 順 | すること | 出典 |
|---|---|---|
| 1 | `develop` から短命のブランチ（例 `feature/260930-user-admin-b2`）を作って作業する。worktree は使わない | `team.md` の Way of Working |
| 2 | 統合の前に `./gradlew verify`（コンテナの実行環境あり、対象DB のテストを飛ばさない）と手元の `./gradlew e2eTest`（4.2）を通す | `team.md` の Way of Working・Testing Posture |
| 3 | `develop` へ squash で統合する。プッシュは依頼者自身が行う | `team.md` の Way of Working |
| 4 | WAR を作り、イメージを作り直して `docker compose up -d`。イメージの作り方・`compose.yaml`・`.env`・ボリューム・JVM の設定は変えない | `team.md` の Deployment |
| 5 | ヘルスチェックとスモークテストで確かめる。S6 の確かめを入れるか（管理者でない利用者が要るため）は deployment-pipeline の段で決める | `team.md` の Deployment |

環境の昇格（検証環境・本番環境）は、配備先が決まるまで作りません。

## 7. 戻し

| 項目 | 扱い |
|---|---|
| 前の版への戻し | 直前の版のイメージで起動し直すだけ（既存の決まり） |
| DB スキーマ・サーバーの状態 | 変えないため、内部DB のバックアップと戻しの練習の論点は無い |
| 設定（`.env`・`application.yaml`） | 変えないため、戻すものは無い |
| ブラウザに残る値 | 言語だけ（今までのプリファレンスの保存と同じ種類）。戻しても前の版がそのまま読める |

## 8. DevSecOps と Compliance の視点

| 観点 | 確かめた結果 |
|---|---|
| 認可 | 画面の表示はサーバーの判定の代わりにしない。管理の API の 401・403・200 と監査は既存の `AdminAccessDeniedHandler` と U3 のサーバー側のテストが受け持つ。U4 は `backend/` を変えない（NFR1.1、`project.md` の Mandated） |
| 入力と出力 | S6 に `detail`・原因・利用者の値を出さない。HTML を直接埋め込まず、URL を応答の値から組み立てない（NFR3.1・NFR9.5） |
| CSP | 変えない。130 のためにも緩めない。130 で CSP の違反 0 件を確かめる（NFR9.5） |
| 秘密情報 | 新しい秘密を持たない。E2E の資格情報は環境変数で渡し、報告の部品で確かめる（NFR3.2）。Gitleaks の除外を足さない |
| 依存の脆弱性 | 新しい依存が無いため、OSV-Scanner の対象は変わらない（NFR9.6） |
| 監査 | U4 は監査を書かない。管理の API の 403 は既存の「アクセスの拒否」の出来事で残る |
| 公開のリポジトリ | テストのデータは架空の値（2語の氏名、`detail` の目印）だけで、実在しそうな氏名・宛先を置かない |

## 9. 記録すること（コード生成と Build and Test）

| 記録 | 段 |
|---|---|
| `backend/`・`backend/src/main/resources/application.yaml`・`vendor/make-you-chic-ui`・`frontend/package.json`・`frontend/package-lock.json` に差分が無いこと | コード生成 |
| `./gradlew verify` が通ったこと、フロントエンドのカバレッジの値、書き換えたテストの一覧 | コード生成・Build and Test |
| 初回の JavaScript の大きさの前後の値（gzip） | コード生成 |
| `./gradlew e2eTest` で 010〜100 と 130 が通ったこと、130 の組ごとの結果、Avatar の文字が2文字だったこと | コード生成・Build and Test |
| 報告の部品が通ったこと、2語の氏名の値が json の報告に含まれないこと（値は表示しない形で数える） | コード生成・Build and Test |
| B2 の `e2eTest` の後に `frontend/playwright-report/`・`frontend/test-results/` を消したことと、共有していないこと（5節） | コード生成 |

## 10. 上流との差

承認済みの文書は書き換えず、差をここに記録します（`project.md` の Way of Working）。

| 対象 | 承認済みの形 | この段の設計 | 理由 |
|---|---|---|---|
| `e2eTest` の前提 | U4 の NFR 設計に Mailpit の起動の記述は無い | 130 はメールを使わないが実行の前に Mailpit の起動が要ることを書いた | 既存の `e2eTest` の確かめの影響。食い違いではない |
| html の報告とトレース | U4 の NFR 設計は報告の部品の確かめだけを書く | B5 までは html と trace に仮の資格情報が載りうる既存の状態で、共有しない決まりで扱い、B5 の U5 の直しが 130 にも効くことを書いた | 要点 5 のとおり。食い違いではない |
| スモークテスト | 上流に記述が無い | S6 の確かめを入れるかを deployment-pipeline の段に回した | `team.md` の Deployment（配備のたびのスモークテスト）。管理者でない利用者が要り、監査に残るため、段で決める |
| 承認済みの設計の文書 | — | 食い違う点は無い。R-01・R-02 はコード生成の計画で扱う（4.3） | NFR Design の承認の場の決定のとおり |

## 承認の場の決定（Request Changes、2026-10-02）

Infrastructure Design の承認の場で、依頼者が Request Changes を選び、前回のレビュー（READY、Minor 3件）の指摘を次のとおり扱いました。

| 指摘・論点 | 依頼者の決定 | 直した箇所 |
|---|---|---|
| R-01 監査の行で S6 を見た利用者の数を数えるという書き方 | 直す。監査の「アクセスの拒否」の行は管理者のみの API の 403 の回数の目安と言い換える（ForbiddenByRoute は残らず、1つの画面が複数の API を呼べば1人で複数行） | `monitoring-design.md` 2節 |
| R-02 403 の件数の範囲が実際より狭い書き方 | 直す。警報 `ms-forbidden`・403 のパネル・`code=ACCESS_DENIED` の WARN は、管理者のみのパス以外も含む `status="403"` のすべてを数えると書き足す。式としきい値は変えない | `monitoring-design.md` 1節・2節・4節 |
| R-03 B2 から B5 までの E2E の html の報告とトレース | 受け入れて扱いを決めた（U5 の Q1 A と U5 R-02 の決定）。B1・B2・B4 と B5 の中の設定を直す前の E2E の後は、json の報告から結果を記録してから `frontend/playwright-report/` と `frontend/test-results/` を消し、消したことと共有していないことをその Bolt のコード生成の記録に書く。いま手元に残っている報告は B1 の始めに消す。設定の先取りはしない | 本書 5節（B2 の 130 の実行）・9節 |
| スモークテストの S6 の扱い | 申し送り。S6 の確かめを入れるか（管理者でない利用者が要り、監査に残る）は deployment-pipeline の段で決める | 本書 6節の5・10節（記述は変えない） |
