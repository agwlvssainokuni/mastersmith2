# Infrastructure Design の質問 — u5-invitation-ui（管理者の招待の画面、ui）

U5 は、管理者が利用者を招待し、招待の一覧・送り直し・取り消しを行う画面です。自分の API・内部DB の表・サーバーの状態を持たず、U3 の API（契約 C5）を既存の ApiClient から呼びます。ui の単位のため、この段の成果物は `infrastructure-specification.md`・`monitoring-design.md`・`cicd-pipeline.md`・`traceability.json` です。

配備先は開発者の PC 上のコンテナだけで、クラウドの基盤は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。U5 の基盤（配信・CSP・依存・検査の流れ・実際のブラウザの検査・画面の時間の測り方）は、承認済みの NFR 要件・NFR 設計と、この段で先に承認された U1・U3・U4・U8 の決定、既存の仕組みで大半が決まっています。決まっていることは要点（案）に書き、上流から1つに決まらない1点だけを質問にします。

読んだ上流（どれも `aidlc/spaces/default/intents/260925-user-management/` の下）:

- この単位の承認済みの NFR 設計 `construction/u5-invitation-ui/nfr-design/`（`performance-design.md`・`security-design.md`・`logical-components.md`・`traceability.json`・`nfr-design-questions.md` の Q1: A・Q2: A・Q3: A）と、承認の場の決定（監査ログ 2026-09-27 の `GATE_APPROVED`・`DECISION_RECORDED`: U5 R-01「念のための検査の意図を書く」、U5 R-02「B5 で飛ばす判定の前提を確かめる」、U6 R-02・U7 R-02「検査のファイルの番号は B5 でそろえる」はコード生成の計画で拾う。上流との差 A1〜A10 は受け入れ、A9 の閲覧の履歴は B5 で実際の動きを確かめて記録する）
- この単位の承認済みの NFR 要件 `construction/u5-invitation-ui/nfr-requirements/`（NFR1.1・NFR2.1、NFR6.1〜NFR6.6、NFR7.1〜NFR7.4、NFR8.1・NFR8.2、NFR9.1〜NFR9.9、`tech-stack-decisions.md`）
- この単位の承認済みの機能設計 `construction/u5-invitation-ui/functional-design/functional-spec.md`・`frontend-components.md`
- 部品の一覧 `inception/domain-design/components.md`（InvitationUi）と契約 `inception/contract-design/contract-summary.md`（C5・C9）
- この段で先に承認された同じ段の成果物: `construction/u1-mail/infrastructure-design/cicd-pipeline.md`（Mailpit の SMTP 1025 と API 8025 は `127.0.0.1` だけに公開、profile `mail`、ボリュームなし、E2E の WAR は `webServer.env` で SMTP の接続先と差出人を受け取る、`e2eTest` は始める前に Mailpit の API に届くかを確かめて届かなければ失敗させる）、`construction/u3-invitation/infrastructure-design/`（E2E の WAR に `MASTERSMITH_WEB_BASE_URL: http://localhost:${port}` を渡す、Mailpit の API から宛先で探して招待の URL を取り出す助けの部品と代表の流れ1本は U6 と B5、配備した環境は `http://localhost:8080` で招待を使える設定、招待の API の指標は既存の `http_server_requests_milliseconds_*` を U3 の監視の設計で見る）、`construction/u4-display-foundation/infrastructure-design/`（共用の検査の手伝い `frontend/e2e/support/` は U4 が B4 で作る、axe-core は `page.evaluate` で WCAG A・AA、画面の時間は `e2eTest` の中だけで測り運用の判定は `Unverified`、reporter に `json` を足して `frontend/test-results/` の下に書く、結果のファイルはコミット・共有しない）、`construction/u8-instance-appearance/infrastructure-design/`（E2E の WAR は見た目の設定なしで既定の blue・sans）
- 決まり `aidlc/spaces/default/memory/org.md`・`team.md`・`project.md`・`phases/construction.md`
- 既存の仕組み: `Dockerfile`・`compose.yaml`（Mailpit は B1 で足す）・`backend/src/main/resources/application.yaml`（CSP）・`backend/src/main/java/cherry/mastersmith/common/web/CacheControlFilter.java`・`backend/src/main/java/cherry/mastersmith/auth/web/AuthController.java` と `OriginVerifier.java`（Origin の確かめはトークンの更新とログアウトだけで、ログインの API には当たらない）・`build.gradle.kts`（`verify` の段、`frontendBundleSize`、`osvScan`、`e2eTest` は `:backend:bootWar` の後に `npx playwright test e2e`）・`.github/workflows/ci.yml`（`./gradlew verify`）・`frontend/playwright.config.ts`（Chromium だけ、`workers: 1`、`retries: 0`、`trace: 'retain-on-failure'`、実行ごとに作る初期管理者のパスワードと署名鍵、一時ディレクトリの内部DB）・`frontend/tsconfig.json`（`include` に `e2e` があり、`npm run typecheck` が検査のファイルと手伝いも型検査する）・`frontend/e2e/`（今は 010〜040 の4つ、`support/` はまだ無い）・README の E2E の節

## Infrastructure Design の要点（案）

### 配備と配信（`infrastructure-specification.md`）

1. **実行の形**: 画面のビルド結果（`dist`）を同梱した既存の実行可能 WAR を、既存の `Dockerfile`・`compose.yaml` の `app` で動かす。U5 のためのコンテナ・ポート・ボリューム・環境変数・資源の上限・健全性の確かめの変更は無い。招待を使える設定（SMTP の接続先・差出人・ベース URL）は U1・U3 の持ち物（`.env`）で、U5 は設定を足さない。
2. **配信とキャッシュ**: 既存の `CacheControlFilter` を変えない（`/assets/**` は `public, max-age=31536000, immutable`、`index.html` と画面の URL は `no-cache`、`/api/**` は `no-store`）。U5 の画面は機能の登録で遅延読み込みの塊に入り、名前にハッシュが付く `dist/assets/` のファイルとして同じ扱いになる。招待の一覧の応答は `/api/**` のため `no-store` で、ブラウザのキャッシュにメールアドレスを残さない（NFR2.1）。
3. **CSP**: `application.yaml` の CSP に差分を作らない。外部への通信・外部の資源・埋め込みのスクリプトとスタイルを足さず、目立たせた行の見た目は機能の CSS（`:has()`）で付ける（NFR9.3、`security-design.md` の5節）。
4. **配信物の大きさ**: 上限は置かない（NFR6.6）。既存の `frontendBundleSize`（gzip で 500KB を超えたら警告だけ）のまま、コード生成で U5 の変更の前後の値と、U5 の画面の塊が入口と別のファイルに出ることと、その塊の大きさ（圧縮前と gzip）を記録する（`performance-design.md` の5節）。
5. **ブラウザに残す値**: U5 のコードは localStorage・sessionStorage を読み書きせず、今のページを URL に載せない。表示の設定は U4 の口（`useDisplaySettings`、契約 C9）から読むだけ。トークンは既存どおりメモリだけ（NFR2.1、`security-design.md` の2節）。サーバーの状態・内部DB・Flyway の変更は無いため、バックアップ・スキーマの後方互換の論点は無い（V8 の後方互換は U3 の持ち主）。
6. **共有する資源**: U3 の招待の4本の API（一覧・招待・送り直し・取り消し）を既存の ApiClient から呼ぶ。共用の検査の手伝い `frontend/e2e/support/`（U4 が B4 で作る。U5 はログインの関数と一覧の見本の組み立てを足す）と、E2E の WAR と一時の内部DB（全ファイルで共有、番号の順に1本ずつ）、Mailpit（U1、開発・E2E・配備の後の確かめで共用し宛先で見分ける）を使う側として共有の表に載せる。足す関数の形と置き場は B5 のコード生成の計画で U6・U7 とそろえる（`logical-components.md` の5.1）。
7. **秘密情報と個人に関する値**: U5 は新しい秘密情報・鍵ファイル・設定を持たない。検査の見本と測定で置く招待のメールアドレスは予約済みのドメイン `example.com` の下の値だけ、招待した管理者の氏名は架空の値にする（公開のリポジトリのため）。招待のトークンと URL は応答に無く、画面・状態・注記に持たない（NFR1.1）。

### 監視（`monitoring-design.md`）

8. **指標とログ**: 画面の側に独自の指標・ログの送り先・画面の実測（RUM）・外部への送信を足さず、`console` を呼ばない（`logical-components.md` の4節）。サーバーの側の要求の数と時間は、既存の `http_server_requests_milliseconds_*`（`uri` が `/api/admin/invitations` ほか、U3 の `monitoring-design.md` の指標の表）と U1 の送信の観測で見える。
9. **警報とダッシュボード**: U5 だけの警報・パネルは足さない。招待の API のパネル・警報の扱いは U3 の決定のとおり（手元の監視では警報にせず、observability-setup で起動して確かめる）。
10. **SLI・SLO**: SLI は「サイドバーの『利用者の招待』を押してから一覧の 20 行目が見えるまで」（目標 2 秒、NFR6.1）と「『次へ』を押してから2ページ目の最初の行が見えるまで」（目標 1.5 秒、NFR6.2）。各5回の値を `./gradlew e2eTest` の中の測定のテストが注記と添付で残し、U4 の決定の `json` の reporter の結果から Build and Test が写す。時間で失敗させず、統合の関門にしない。運用の中での判定は、手元の監視を常に動かしていないため `Unverified` とし、持ち主の段（observability-setup・feedback-optimization）を明記して引き継ぐ（`project.md` の Testing Posture・Deployment）。測定は招待を 21 件置き監査に残すため、配備したアプリに向けては流さない（U4 の Q1: A と同じく `e2eTest` の中だけで測る）。

### 検査の流れと配備（`cicd-pipeline.md`）

11. **1コマンドの検査**: 既存の `./gradlew verify` のまま（CI も同じタスク）。U5 の画面部品のテスト（Vitest ＋ Testing Library ＋ user-event ＋ vitest-axe、fast-check の性質ベースのテストは失敗時の乱数の種を残す）は既存の単体テストの段で動き、フロントエンドのカバレッジの下限（行 80%・分岐 70%）を守って計測の除外を増やさない。`frontend/e2e/` の検査のファイルと手伝いは Vitest の計測の対象外のまま、`npm run typecheck`（`verify` の中）で型検査される。そのため一覧の見本と `frontend/src/features/invitation/api/types.ts` の型の食い違い（契約 C5 の項目）は `verify` で止まる（NFR9.6〜NFR9.8、`logical-components.md` の5.3）。ビルドの設定・CI の変更は無い。
12. **依存と make-you-chic-ui**: U5 は新しい依存（実行時・開発時とも）を足さず、`frontend/package.json`・`frontend/package-lock.json` に差分を作らないことをコード生成で確かめて記録する。axe-core は U4 が B4 で devDependencies に明示で足したものを使う。make-you-chic-ui は B4 の固定先の更新（`edb1f94` → `735ef04`）の後の版を使い、B5 のコード生成の計画でその更新が済んでいることを前提として確かめる。`vendor/make-you-chic-ui` の中身は変えない（NFR9.4・NFR9.5、`project.md` の Forbidden）。B5 は固定先の更新を含まないため、`develop` へ squash で統合し、統合の前に `./gradlew verify` を通す（`team.md` の Way of Working）。
13. **実際のブラウザの検査と測定（`frontend/e2e/` の `050-` の後の番号の新しいファイル、番号は B5 で U6・U7 とそろえる）**: `./gradlew e2eTest` の中で、`./gradlew verify` と CI の外。既存の 010〜040 と U4 の 050 は変えない。アクセシビリティの検査は 20 組（(a) 6・(b) 8・(c) 幅 375px の6組）で、一覧の API の答えだけを `page.route` の見本で差し替え、Modal は開くだけで招待・取り消しの要求を送らない。同じファイルで画面の時間を5回測る。流れの E2E ではないため、`team.md` の「代表の流れを1本まで」に数えない（NFR7.3・NFR7.4・NFR9.9）。U5 は流れの E2E を足さず、代表の流れ（招待から登録の完了まで）は U6 が B5 で書く。
14. **測定の招待の用意と前提**: 測定のテストは、そのテストの要求の口（Playwright の `request`）で管理者としてログインの API を呼んでアクセストークンを得て（ログインの API には Origin の確かめが当たらないため、要求の口からそのまま呼べる）、招待の API で 21 件を置く（`performance-design.md` の4.2）。招待はそのたびに本物の送信の道を通り、U1 の決定で E2E の WAR に渡した Mailpit（`127.0.0.1:1025`）だけに送られ、実在の宛先・外部の SMTP へは送らない。`e2eTest` は始める前に Mailpit の API に届くかを確かめて届かなければ失敗させる（U1）ため、実行の前に `docker compose --profile mail up -d mailpit` が要る。E2E の WAR には SMTP の接続先と差出人（U1）とベース URL（U3）が渡るため、一覧の `invitationEnabled` は真になる見込みで、偽のときに `test.skip` にする判定は念のための備え（U5 R-01 の意図の記述と U5 R-02 の前提の確かめは B5 のコード生成の計画で拾う）。飛ばしたときは Build and Test で `Unverified` とし、持ち主を明記して引き継ぐ。
15. **後のファイルへの影響**: 測定で置いた 21 件の招待とログインの監査の記録は、同じ WAR と一時の内部DB を使う後のファイル（U6 の E2E-1 など）に残る。後のファイルは一覧の件数・順・監査の件数に頼らない作りにし、B5 のファイルの番号と並びを U6・U7 とそろえる。1回の実行でログインは約 25 回増え、`e2eTest` の時間が延びる。長い実行は `caffeinate -i` を付ける（`project.md` の Testing Posture）。
16. **実行の時点と README**: 画面・認証に関わる変更を統合する前（B5）とリリースの前に、手元で `./gradlew e2eTest` を実行し、U5 の変更の後に既存の 010〜040・U4 の 050・U6 の E2E-1 が通ることを記録する（`team.md` の Testing Posture、NFR9.9）。README の E2E の節の表に U5 の検査のファイルの行（検査の中身、時間は記録だけで失敗させないこと、招待を 21 件置き Mailpit に 21 通届くこと）を足す。
17. **検査の結果のファイルの扱い**: `frontend/test-results/`・`frontend/playwright-report/` は既存どおり管理外。失敗のときのトレースには、実行ごとの仮の初期管理者のパスワード、要求の口で得たアクセストークン、見本のメールアドレスが含まれうるため、コミット・共有しない（新しい秘密の置き場は作らない）。アクセストークンはテストの変数だけに持ち、注記・添付に出さない。Build and Test には、組ごとの成否・違反の件数と規則の名前・`incomplete`・時間の値だけを写す（`security-design.md` の6節、U4 の要点 16）。
18. **配備と戻し**: 既存の流れ（WAR を作りイメージを作り直して `docker compose up -d`、ヘルスチェックとスモークテスト）のまま。U5 のスモークテストは、`http://localhost:8080/` に管理者でログインし、サイドバーの「利用者の招待」から一覧が開けること（招待の操作と Mailpit での確かめは U3 のスモークテストで行い、利用者と監査が残ることを先に依頼者に伝える）。戻しはイメージだけを直前の版に戻す既存の決まりのまま。U5 はサーバーの状態とブラウザの保存を変えないため、戻しで消すものは無い。
19. **上流との差**: U5 について、承認済みの設計の文書と違う承認の場の決定は無い（U5 R-01・R-02 はコード生成の計画で拾う）。要点 14 の Mailpit の前提（U1 の決定の影響）、要点 17 の結果のファイルの扱い、要点 18 のスモークテストは、上流に書かれていない具体を既存の決まりと先の単位の決定で埋めた追加として成果物に明記する。

## Q1. 測定のテストが招待を 21 件置くたびに Mailpit に届く 21 通を、テストの終わりに消しますか？

背景: 測定のテストは、実行のたびに招待の API で 21 件を置き（要点 14）、そのたびに本物の送信の道を通って Mailpit に 21 通が届きます。Mailpit は開発で見るメール・E2E のメール・配備の後の確かめのメールを同じ受け手で分け合い、宛先で見分けます（U1・U3）。届くメールの宛先は `example.com` の下の実行ごとに違う値で、招待のリンクはその実行の E2E の WAR（一時の内部DB、番号 18081）にしか効かず、WAR が止まると使えません。Mailpit はボリュームを持たないため、コンテナを消すと受けたメールも消え、件数が増えると古いものから消す既定の上限があります（値は B1 で Mailpit を足すときに確かめる）。U6 の E2E-1 は宛先で探すため、ほかのメールが残っていても取り違えません。

A. 消さない。Mailpit にそのまま残し、見終えたら既存の手順（`docker compose stop mailpit`、消すときは `docker compose rm -f mailpit`）で片付ける。README の E2E の節に「測定のテストが1回の実行で 21 通を送ること」を書く。検査は Mailpit の API に書き込まず、U1 の前提の確かめ（届くかの確認）だけに頼る。開発者が手元でメールを見るとき、一覧に実行ごとの 21 通が混ざる（推奨）
B. 測定のテストの終わりに、Mailpit の API で、その実行の印を含む宛先のメールだけを消す（共用の手伝いに消す関数を1つ置く）。手元のメールの一覧に測定のメールが残らない代わりに、U5 の検査が Mailpit の API に書き込むようになり、消すことに失敗したときの扱い（失敗させるか、警告だけか）と、E2E-1 など後のファイルのメールを消さない条件の確かめが要る
C. 測定のテストの初めに、Mailpit のすべてのメールを消してから招待を置く。手元の一覧は毎回その実行の分だけになるが、開発で見ていたメールや、配備の後の確かめのメールまで消えるおそれがある
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめ（回答の後に書き直します）:

- 要点（案）は冒頭の 19 件のとおり（実行の形・配信とキャッシュ・CSP は既存のまま、配信物の大きさは上限なしで記録、ブラウザに保存せず URL にも載せない、共用の検査の手伝いと E2E の WAR と Mailpit を使う側として共有、画面の側に独自の指標・送信を足さない、NFR6.1・NFR6.2 は `e2eTest` の中で記録し運用の判定は `Unverified`、`./gradlew verify` と CI は変えず見本の型の食い違いは型検査で止まる、新しい依存なしで make-you-chic-ui は B4 の更新の後の版、検査と測定は `e2eTest` の中で流れの本数に数えない、測定の招待は API で置き Mailpit だけに送る、後のファイルは件数・順に頼らない、結果のファイルはコミット・共有しない、スモークテストは一覧が開けること、戻しはイメージだけ）
- Q1: A — 測定のテストが Mailpit に置く招待のメール（1回の実行で 21 通）は消さない。見終えたら既存の手順（`docker compose stop mailpit`、消すときは `docker compose rm -f mailpit`）で片付ける。README の E2E の節に「測定のテストが1回の実行で 21 通を送ること」を書く。検査は Mailpit の API に書き込まない

Does this all look correct before I generate the artifact?

- Looks correct — 要点と答えのとおりに成果物を作る
- Request changes — 直したい点を書いてください

[Answer]: Looks correct
