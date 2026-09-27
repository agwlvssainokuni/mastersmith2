# Infrastructure Design の質問 — u4-display-foundation（画面の表示の設定の土台、ui）

U4 は、画面の言語・テーマ・文字の大きさと、インスタンスの見た目の設定（U8 の `GET /api/appearance`）を画面に当てる土台です。自分の API・内部DB の表・サーバーの状態を持ちません。ui の単位のため、この段の成果物は `infrastructure-specification.md`・`monitoring-design.md`・`cicd-pipeline.md`・`traceability.json` です。

配備先は開発者の PC 上のコンテナだけで、クラウドの基盤は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。U4 の基盤（配信とキャッシュ・CSP・依存・検査の流れ・実際のブラウザの検査）は、承認済みの NFR 要件・NFR 設計と既存の仕組みで大半が決まっています。決まっていることは要点（案）に書き、上流から1つに決まらない2点だけを質問にします。

読んだ上流（どれも `aidlc/spaces/default/intents/260925-user-management/` の下）:

- この単位の承認済みの NFR 設計 `construction/u4-display-foundation/nfr-design/`（`performance-design.md`・`security-design.md`・`logical-components.md`・`traceability.json`・`nfr-design-questions.md` の Q1: A・Q2: A・Q3: A）と、承認の場の決定（監査ログ 2026-09-27 の `GATE_APPROVED`・`DECISION_RECORDED`: U4 R-01「見張りの順番は既存の E2E にならう」、U4 R-02「B4 でフォントの CSS を確かめる」はコード生成の計画で拾う。上流との差 A1〜A10 は受け入れ）
- この単位の承認済みの NFR 要件 `construction/u4-display-foundation/nfr-requirements/`（NFR2.1・NFR2.2、NFR6.1〜NFR6.5、NFR7.1〜NFR7.5、NFR8.1・NFR8.2、NFR9.1〜NFR9.11、`tech-stack-decisions.md`）
- この単位の承認済みの機能設計 `construction/u4-display-foundation/functional-design/functional-spec.md`（W1〜W12・D1〜D14・9節のフォントの採用）と `frontend-components.md`
- 部品の一覧 `inception/domain-design/components.md` と契約 `inception/contract-design/contract-summary.md`（C6・C7・C9）
- この段で先に承認された同じ段の成果物: `construction/u1-mail/infrastructure-design/cicd-pipeline.md`（E2E の WAR は `webServer.env` で設定を受け取る、`e2eTest` は始める前に Mailpit の API に届くかを確かめる）、`construction/u8-instance-appearance/infrastructure-design/`（見た目の設定は `.env` の2項目、E2E の WAR は設定なしで既定の blue・sans、`GET /api/appearance` は認証なし）
- 決まり `aidlc/spaces/default/memory/org.md`・`team.md`・`project.md`・`phases/construction.md`
- 既存の仕組み: `Dockerfile`・`compose.yaml`・`backend/src/main/resources/application.yaml`（CSP）・`backend/src/main/java/cherry/mastersmith/common/web/CacheControlFilter.java`・`build.gradle.kts`（`verify` の段、`frontendBundleSize`、`osvScan` の対象の lockfile 3つ、`vendorUnchanged`、`e2eTest`）・`.github/workflows/ci.yml`（`submodules: true`、`./gradlew verify`）・`.github/dependabot.yml`（npm は `/frontend`）・`frontend/playwright.config.ts`（Chromium だけ、`workers: 1`、reporter は `list` と `html`、`trace: 'retain-on-failure'`）・`frontend/vitest.config.ts`（`thresholds` 行 80・分岐 70）・`frontend/scripts/check-bundle-size.mjs`・`frontend/package-lock.json`（`axe-core` 4.13.0 が vitest-axe の推移依存として既に載っている）・`.gitignore`（`frontend/test-results/`・`frontend/playwright-report/`・`frontend/coverage/`）・`vendor/make-you-chic-ui`（今の固定先 `edb1f94`、手元の履歴に `735ef04` がある）・README の E2E の節

## Infrastructure Design の要点（案）

### 配備と配信（`infrastructure-specification.md`）

1. **実行の形**: 画面のビルド結果（`dist`）を同梱した既存の実行可能 WAR を、既存の `Dockerfile`・`compose.yaml` の `app` で動かす。U4 のためのコンテナ・ポート・ボリューム・環境変数・資源の上限・健全性の確かめの変更は無い。見た目の設定の2項目は U8 の持ち物（`.env`）で、U4 は設定を足さない。
2. **配信とキャッシュ**: 既存の `CacheControlFilter` を変えない（`/assets/**` は `public, max-age=31536000, immutable`、`index.html` と画面の URL は `no-cache`、`/api/**` は `no-store`）。Noto Serif JP のフォントのファイルも `dist/assets/` の名前にハッシュが付くファイルになり、同じ扱いになる（`performance-design.md` の5節）。`<link rel="preload">` は足さない。
3. **CSP**: `application.yaml` の CSP（`default-src 'self'`・`script-src 'self'`・`style-src 'self'`・`font-src 'self'`・`connect-src 'self'` ほか）に差分を作らない。外部への通信・外部のフォントの配信元を足さず、フォントは `@fontsource` から自前で配信する。`frontend/vite.config.ts` の `modulePreload.polyfill: false`・`assetsInlineLimit: 0` のままで、埋め込みのスクリプト・スタイルを足さない（NFR9.4、`security-design.md` の5節）。
4. **配信物の大きさ**: 上限は置かない（NFR6.5）。コード生成で、変更の前後のフォントのファイルの合計（名前ごと）・入口の CSS・`dist` の全体・WAR の大きさを `du`・`stat` などで測って記録する（今は Noto Sans JP の分が約 9.8MB、WAR が約 87MB）。コンテナのイメージも同じだけ大きくなるが、資源の上限（CPU 4・メモリ 2g の既定）は変えない。初回の JavaScript は既存の `frontendBundleSize`（gzip で 500KB を超えたら警告だけ）のまま。
5. **ブラウザに残す値**: localStorage の U4 の鍵 `mastersmith.display-settings` と make-you-chic-ui の写しの鍵（`design-system-theme`・`design-system-font-size`）だけで、値は3つの軸（`language`・`theme`・`fontSize`）だけ。トークン・メールアドレス・氏名を置かない。登録の完了からログインの画面へのメールアドレスの受け渡しはモジュールの変数だけで、URL・ブラウザの保存・`history.state` に載せない（NFR2.1・NFR2.2）。サーバーの状態・内部DB・Flyway の変更は無いため、バックアップ・スキーマの後方互換の論点は無い。
6. **共有する資源**: U8 の `GET /api/appearance`（認証なし、I/O を持たない）と既存のトークンの更新を、画面を開くたびに1回ずつ呼ぶ。トークンを付けない公開の API のパスは、ApiClient の1つの一覧と完全な一致の判定にまとめる（NFR9.1、Q3: A）。実際のブラウザの検査の手伝い `frontend/e2e/support/`（組の切り替え・axe-core の読み込みと実行・はみ出しの判定・CSP の違反の集め）は U4 が B4 で作り、U5〜U7 が B5 で使う（共有の表に載せる）。
7. **秘密情報と個人に関する値**: U4 は新しい秘密情報・鍵ファイル・設定を持たない。テストのデータに実在の個人に関する値を使わず、メールアドレスは `example.com` にする。画面の `console` にトークン・メールアドレス・氏名を出さない。

### 監視（`monitoring-design.md`）

8. **指標とログ**: 画面の側に、独自の指標・ログの送り先・画面の実測（RUM）・外部への送信を足さない（`logical-components.md` の4節）。サーバーの側の要求の数と時間は、既存の `http.server.requests` の `uri="/api/appearance"`（U8）とトークンの更新の `uri` で見える。
9. **警報とダッシュボード**: U4 だけの警報・パネルは足さない。画面を開くたびの `/api/appearance` の p95 のパネルを足すかは、U8 の決定のとおり observability-setup の段で決める。
10. **SLI・SLO**: SLI は「キャッシュが空の状態で画面を開いてからログインの画面の見出しが見えるまでの時間」、目標は 5 回すべて 2 秒以内（NFR6.1）。Build and Test が `./gradlew e2eTest` の中の測定のテストの値を結果に写し、統合の関門にはしない。手元の監視を常に動かしていないため、運用の中での判定は `Unverified` とし、持ち主の段（observability-setup・feedback-optimization）を明記して引き継ぐ（`project.md` の Testing Posture・Deployment）。

### 検査の流れと配備（`cicd-pipeline.md`）

11. **1コマンドの検査**: 既存の `./gradlew verify` のまま（CI も同じタスク）。U4 の画面部品のテスト（Vitest ＋ Testing Library ＋ vitest-axe、性質ベースのテストは fast-check で失敗時の `seed`・`path` を出力に残す）は既存の単体テストの段で動く。フロントエンドのカバレッジの下限（行 80%・分岐 70%、`@vitest/coverage-v8` の `thresholds`）を守り、計測の除外を増やさない。`frontend/e2e/` は既存どおり計測の対象外（NFR9.8〜NFR9.10）。ビルドの設定・CI の変更は無い。
12. **依存**: `@fontsource/noto-serif-jp` 5.3.0 を `dependencies`（OFL-1.1、採用の記録は機能設計の 9.2。実行時の依存のため重大度 High 以上で統合を止める。推移依存が無いことを記録する）、`axe-core` 4.13.0 を `devDependencies` に明示で足す（MPL-2.0、`tech-stack-decisions.md` の 2.1。改変しない。脆弱性は警告で、悪意のあるパッケージ（OSV の `MAL-`）だけ止める。成果物を作る道具ではないため `config/npm-build-tools.txt` に足さない。lockfile の axe-core が1つの版にそろうことを記録する）。どちらも `frontend/package-lock.json` で固定して CI は `npm ci`、既存の `osvScan` と Dependabot の npm（`/frontend`）の対象になる。ビルドの後の `dist/` に axe-core の本体に特有の文字列（`Deque Systems`）が無いことをコード生成で1回確かめて記録する（NFR9.5〜NFR9.7）。
13. **サブモジュールの固定先の更新**: `vendor/make-you-chic-ui` を `edb1f943c0e66293494fa974605f34fcd7e258d7` → `735ef04ce6eb618cb875f5c4b31c1645a1f84c28`（Modal・RadioGroup・Table・Dropdown・Button の変更と見本の画面、8 コミット）に、B4 で承認を得た専用のコミットで更新し、前後のハッシュを記録する。中身は変えない（`project.md` の Mandated・Forbidden）。固定先の更新を含むため、B4 は短命のブランチから `develop` へ fast-forward で統合してよく、統合の前に `./gradlew verify` を通す（`team.md` の Way of Working）。更新した版の `vendor/make-you-chic-ui/package-lock.json` は既存の `osvScan` の対象で、`vendorInstall`・`vendorBuild`・`vendorUnchanged` の段も既存のまま動く。CI は `submodules: true` で新しい固定先を取得する。
14. **実際のブラウザの検査（`frontend/e2e/050-display-accessibility.e2e.ts`、番号はコード生成で確かめる）**: `./gradlew e2eTest` の中で、`./gradlew verify` と CI の外。`workers: 1` のまま番号の順で既存の 010〜040 の後に動き、既存のファイルは変えない。B4 はログインの画面の最初の状態を 20 組（(a) テーマ × 文字の大きさ、(b) ブランドカラー × テーマ、(c) 幅 375px の6組）で検査し、axe は検査のページで `page.evaluate` を使って WCAG 2.0・2.1 の A・AA のタグで流す（`bypassCSP`・`addScriptTag` は使わない）。同じファイルで最初の画面の時間を5回測り、CSP の違反と `sans` のときの Noto Serif JP の要求は失敗の条件にする。流れの E2E ではないため、`team.md` の「代表の流れを1本まで」に数えない（NFR7.3・NFR7.5・NFR9.11）。U4 R-01（各回で「監視を張る → `page.goto` → 測定 → 判定」の順）と U4 R-02（fontsource の CSS が `unicode-range` を持たない前提が崩れたときの方針の一言）はコード生成の計画に載せる。
15. **実行の時点と前提**: 画面・認証に関わる変更を統合する前（B4・B5）と、リリースの前に、手元で `./gradlew e2eTest` を実行する（`team.md` の Testing Posture）。U1 の決定で `e2eTest` は始める前に Mailpit の API（`127.0.0.1:8025`）に届くかを確かめるため、050 がメールを使わなくても、実行の前に `docker compose --profile mail up -d mailpit` が要る。E2E の WAR は見た目の設定なし（既定の blue・sans）で動き、ブラウザは既存どおり Chromium だけ。長い実行は `caffeinate -i` を付ける。README の E2E の節の表に 050 の行（検査の中身と、時間は記録だけで失敗させないこと）を足す。
16. **検査の結果のファイルの扱い**: `frontend/test-results/`・`frontend/playwright-report/` は既存どおり管理外。失敗のときのトレースには、実行ごとに作る仮の初期管理者のパスワード・トークンが含まれうるため、コミット・共有しない（新しい秘密の置き場は作らない）。Build and Test には、組ごとの成否・違反の件数と規則の名前・`incomplete`・時間の値だけを写す。
17. **配備と戻し**: 既存の流れ（WAR を作りイメージを作り直して `docker compose up -d`、ヘルスチェックとスモークテスト）のまま。戻しはイメージだけを直前の版に戻す既存の決まりのまま。U4 はサーバーの状態を変えないため、戻しで消すものは無い。ブラウザに残った U4 の鍵は直前の版では読まれず、写しの鍵は make-you-chic-ui が読む既存の形の値のため、害は無い見込み（具体の確かめは配備の段で決める）。
18. **上流との差**: U4 について、承認済みの設計の文書と違う承認の場の決定は無い（R-01・R-02 はコード生成の計画で拾う）。要点 15 の Mailpit の前提（U1 の決定の影響）と、要点 16 の結果のファイルの扱いは、上流に書かれていない具体を既存の決まりで埋めた追加として成果物に明記する。

## Q1. 最初の画面の時間（NFR6.1、2 秒）を、`./gradlew e2eTest` のほかに配備したアプリでも測りますか？

背景: NFR6.1 は「手元の PC のブラウザで、キャッシュが空の状態で 2 秒以内」とし、測る場を `./gradlew e2eTest`（Build and Test）に決めています。`e2eTest` の WAR は PC の上で `java -jar` で動き（空の内部DB、資源の上限なし）、配備したアプリはコンテナ（CPU 4・メモリ 2g の既定、内部DB は使い続けて膨らんだファイル）で動きます。最初の画面の待ちの大半は `/api/appearance` とトークンの更新の遅いほうで、`/api/appearance` の p95 は U8 が使い捨ての環境（コンテナ）の k6 で確かめます。測定のテストはログインせず、内部DB・監査の記録を変えないため、配備したアプリに向けても記録は汚れませんが、今の `playwright.config.ts` は必ず WAR を起動する作りです。

A. `./gradlew e2eTest` の中だけで測る（NFR6.1 のとおり）。配備したアプリの API の時間は U8 の k6 と既存のスモークテストで押さえ、画面の時間の運用の中での判定は `Unverified` として observability-setup・feedback-optimization に引き継ぐ。基盤の変更は無い（推奨）
B. A に加えて、配備の段（deployment-execution）で1回、配備したアプリ（`http://localhost:8080`）に向けて同じ測定のテストだけを流す。`playwright.config.ts` に、環境変数（例 `E2E_BASE_URL`）があれば WAR を起動せずその URL を使う切り替えを足し、測定のテストだけを選んで流す手順を README に書く。コンテナの条件での値が取れる代わりに、設定の分かれ道が1つ増え、ほかの E2E を配備したアプリに向けて流さない約束が要る
C. A に加えて、配備の段で1回、開発者がブラウザの開発者の道具（キャッシュを無効にした再読み込み）で配備したアプリのログインの画面の時間を見て記録する。設定の変更は無いが、手で測るため回数と見方がそろわない
X. Other (please specify)

[Answer]: A

## Q2. 実際のブラウザの検査の結果（時間の値・組ごとの成否）を、Build and Test がどう読み取れるようにしますか？

背景: NFR 設計は、測った時間と組ごとの成否・違反・`incomplete` を、テストの注記（`test.info().annotations`）と添付（JSON）で残し、Build and Test が結果に写すとしています。今の `playwright.config.ts` の reporter は `list`（画面に流れるだけ）と `html`（`frontend/playwright-report/`、ブラウザで開く形）で、注記と添付は HTML の報告の中にだけ残ります。B5 で U5〜U7 の検査のファイルも同じ形で記録を足します。

A. reporter に `json` を足し、結果を管理外の `frontend/test-results/` の下の1つのファイル（例 `e2e-results.json`）に書く。Build and Test はこのファイルから注記と添付の値を読んで写す。既存の `list`・`html` は残し、`.gitignore` は変えない。新しい依存は無い（`@playwright/test` の標準の reporter）。ファイルには時間・規則の名前・件数だけが入り、秘密は入らないことをコード生成で確かめる（推奨）
B. reporter は変えず、Build and Test は `npx playwright show-report` で HTML の報告を開いて値を写す。設定の変更は無いが、写す作業が手になり、B5 で組が増えると写し漏れが起きやすい
C. reporter は変えず、測定と検査のテストの中で、値を `frontend/test-results/` の下の JSON のファイルに自分で書き出す（手伝いのモジュールに書き出しの関数を置く）。形を自由に決められる代わりに、書き出しの作りと片付けを自分で持つ
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめ（回答の後に書き直します）:

- 要点（案）は冒頭の 18 件のとおり（実行の形は既存のまま、配信とキャッシュと CSP は変えない、配信物の大きさは上限なしで記録、ブラウザに残すのは3つの軸だけ、共用の検査の手伝いを U4 が作る、画面の側に独自の指標・送信を足さない、NFR6.1 は Build and Test で記録し運用の判定は `Unverified`、`./gradlew verify` と CI は変えずカバレッジの下限を守る、`@fontsource/noto-serif-jp` と `axe-core` の依存の扱い、make-you-chic-ui の固定先の更新を B4 の専用のコミットで行い fast-forward で統合してよい、050 の検査は `e2eTest` の中で verify と CI の外、実行の前に Mailpit の起動が要る、結果のファイルはコミット・共有しない、戻しはイメージだけ）
- Q1: A — 最初の画面の時間（NFR6.1）は `./gradlew e2eTest` の中だけで測る。配備したアプリの API の時間は U8 の k6 と既存のスモークテストで押さえ、画面の時間の運用の中での判定は `Unverified` として observability-setup・feedback-optimization に引き継ぐ。基盤の変更は無い
- Q2: A — `playwright.config.ts` の reporter に `json` を足し、結果を管理外の `frontend/test-results/` の下の1つのファイルに書く。Build and Test はこのファイルから注記と添付の値を読んで写す。既存の `list`・`html` は残し、`.gitignore` は変えない。ファイルに秘密が入らないことをコード生成で確かめる

Does this all look correct before I generate the artifact?

- Looks correct — 要点と答えのとおりに成果物を作る
- Request changes — 直したい点を書いてください

[Answer]: Looks correct
