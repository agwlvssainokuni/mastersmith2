# Infrastructure Design の質問 — u7-preferences-ui（プリファレンスとパスワードの変更の画面、ui）

U7 は、ログインした利用者が自分の氏名・言語・テーマ・文字の大きさを変えて保存する画面（S4）と、自分のパスワードを変える画面（S5）の単位です。自分の API・内部DB の表・サーバーの状態を持たず、U2 の3本の API（`GET`・`PUT /api/me/preferences`、`POST /api/me/password`）を呼ぶだけです。ui の単位のため、この段の成果物は `infrastructure-specification.md`・`monitoring-design.md`・`cicd-pipeline.md`・`traceability.json` です。

配備先は開発者の PC 上のコンテナだけで、クラウドの基盤は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。U7 の基盤に関わる論点（画面の時間の測り方と測りに使う利用者、検査のファイルの置き場、検査と測りの資格情報の扱い、Mailpit への頼り方）は、承認済みの NFR 設計（Q1: A・Q2: A・Q3: A）と、この段で先に承認された U1・U3・U4・U8 の基盤の設計、既存の仕組みでどれも1つに決まっています。そのため、`project.md` の Way of Working（新しく決める論点が無いときは質問を作らず要点を確かめる）に従い、**質問は作らず**、要点（案）とまとめの確認だけにします。

読んだ上流（どれも `aidlc/spaces/default/intents/260925-user-management/` の下）:

- この単位の承認済みの NFR 設計 `construction/u7-preferences-ui/nfr-design/`
  - `performance-design.md`（2節 読み込み、3節 二重送信、4節 画面の時間の測り方と測りに使う利用者の用意、5節 初回の JavaScript）
  - `security-design.md`（6節 実際のブラウザの検査と測りの安全、7節 依存）
  - `logical-components.md`（4節 拡張性・信頼性・観測性の扱い、5節 実際のブラウザの検査の置き場・組・ログインと組の当て方・検査する状態・結果の記録、7節 テストと依存の順）
  - `traceability.json`、`nfr-design-questions.md`（Q1: A・Q2: A・Q3: A、Looks correct）
- NFR 設計の承認の場の決定（監査ログ 2026-09-27 の `GATE_APPROVED`・`DECISION_RECORDED`）: U7 R-01（U6 との突き合わせの手順を B5 の最初に決める）と U6 R-02・U7 R-02（検査のファイルの番号は B5 でそろえる）はコード生成の計画で拾う。上流との差 A1〜A10 は受け入れ（A9 の閲覧の履歴は U6 の残る危険で、B5 で実際の動きを確かめて記録する）
- この単位の承認済みの NFR 要件 `construction/u7-preferences-ui/nfr-requirements/`（NFR2.1、NFR6.1〜NFR6.6、NFR7.1〜NFR7.5、NFR8.1・NFR8.2、NFR9.1〜NFR9.10、承認の場の R-01・R-02）
- この単位の承認済みの機能設計 `construction/u7-preferences-ui/functional-design/`（`functional-spec.md` の D1〜D14・W1〜W13・9節の骨組みの変更、`frontend-components.md`）
- 部品の一覧 `inception/domain-design/components.md` と契約 `inception/contract-design/contract-summary.md`（C4・C9 の受ける側）
- この段で先に承認された同じ段の成果物
  - `construction/u1-mail/infrastructure-design/cicd-pipeline.md`（6節 Mailpit の SMTP 1025・API 8025 を `127.0.0.1` だけに公開、7節 E2E の WAR は `webServer.env` で SMTP の接続先と差出人を受け取り、`e2eTest` は始める前に Mailpit の API に届くかを確かめて届かなければ失敗させる）
  - `construction/u2-user-preferences/infrastructure-design/`（`monitoring-design.md` の `/api/me/` の3本の指標と SLO は Unverified、`cicd-pipeline.md` の「画面の確かめは U7 が B5 で持つ」）
  - `construction/u3-invitation/infrastructure-design/`（E2E の WAR に `MASTERSMITH_WEB_BASE_URL: http://localhost:${port}`、本文の `/register#token=` の URL を Mailpit の API から宛先で探して取り出す、助けの部品と代表の流れ1本は U6 と B5）
  - `construction/u4-display-foundation/infrastructure-design/`（共用の検査の手伝い `frontend/e2e/support/` は U4 が B4 で作る、axe-core は `page.evaluate` で WCAG A・AA のタグ、画面の時間は `e2eTest` の中だけで測り運用の判定は Unverified、reporter に `json` を足して `frontend/test-results/` の下に書く、結果のファイルはコミット・共有しない）
  - `construction/u8-instance-appearance/infrastructure-design/`（E2E の WAR は見た目の設定なしで既定の blue・sans）
- 並行する単位の上流（読み取りだけ）: `construction/u6-registration-ui/nfr-design/logical-components.md`（E2E-1 と検査のファイルの分け方、初期管理者の設定を変えない方針）・`security-design.md`（閲覧の履歴の残る危険 SD-D2）
- 決まり `aidlc/spaces/default/memory/org.md`・`team.md`・`project.md`・`phases/construction.md`
- 既存の仕組み（読み取りだけ）: `frontend/playwright.config.ts`（`workers: 1`・`fullyParallel: false`・`retries: 0`、Chromium だけ、ロケール `ja-JP`、WAR は実行ごとの一時の内部DB、初期管理者 `e2e-admin@example.com` のパスワードと署名鍵は実行ごとに `randomBytes` で作って環境変数で渡す、reporter は `list` と `html`、`trace: 'retain-on-failure'`、`webServer.env` に SMTP とベース URL はまだ無い）・`frontend/e2e/`（010〜040 の4本）・`build.gradle.kts` の `e2eTest`（`:backend:bootWar` の後に `npx playwright test e2e`）・`.gitignore`（`frontend/test-results/`・`frontend/playwright-report/`）・`compose.yaml`・`Dockerfile`・`.github/workflows/ci.yml`

## Infrastructure Design の要点（案）

### 配備と配信（`infrastructure-specification.md`）

1. **実行の形**: 画面のビルド結果（`dist`）を同梱した既存の実行可能 WAR を、既存の `Dockerfile`・`compose.yaml` の `app` で動かす。U7 のためのコンテナ・ポート・ボリューム・環境変数・資源の上限（CPU 4・メモリ 2g の既定）・健全性の確かめの変更は無い。U7 は新しい設定の項目を持たない。
2. **配信とキャッシュと CSP**: 2つの画面は `React.lazy` の遅延読み込みの塊として `dist/assets/` の名前にハッシュが付くファイルになり、既存の `CacheControlFilter`（`/assets/**` は `immutable`、画面の URL は `no-cache`、`/api/**` は `no-store`）のまま配られる。`application.yaml` の CSP・`frontend/vite.config.ts` は変えず、外部への通信・埋め込みのスクリプトを足さない。画面の URL は SPA の既存の転送の決まりで `index.html` を返す（新しい URL の許し方の設定は要らない）。
3. **配信物の大きさ**: 上限は置かない。既存の `frontend/scripts/check-bundle-size.mjs`（`./gradlew verify` の中、gzip 500KB を超えたら警告だけ）で、U7 の変更の前後の初回の JavaScript の値をコード生成で記録する（NFR6.6）。
4. **サーバーの状態と後方互換**: U7 は内部DB・Flyway の変更を持たない。サーバーの側の変更（V7 の後方互換を含む）は U2 の持ち物で、U7 に後方互換・バックアップの論点は無い。
5. **共有する資源**: U2 の3本の API（ログインが要る。本人の行は U2 が決める）、U4 の `applyUserPreferences`（C9）、U6 の `frontend/src/shared/validation/`、U4 の共用の検査の手伝い `frontend/e2e/support/`、U1 の Mailpit（profile `mail`）を使うだけで、どれも持たない。B5 で `frontend/e2e/support/` に足す関数（ログイン・差し替えの答えの組み立て・招待から利用者を作る関数）の形と置き場は U5・U6 とそろえてコード生成の計画で決める（NFR 設計 `logical-components.md` の 5.1）。利用者を作る関数は U6 の E2E-1 と同じものを使う。
6. **ブラウザに残す値と個人に関する値**: 氏名とパスワードの値はフックの状態（メモリ）だけに持ち、U7 はブラウザの保存・Cookie・URL・`console` に触れない。ブラウザの保存は U4 の `applyUserPreferences` が3つの表示の設定だけを書く（NFR2.1・NFR9.1）。U7 は新しい秘密情報・鍵ファイル・設定を持たない。

### 監視（`monitoring-design.md`）

7. **指標とログ**: 画面の側に、独自の指標・ログの送り先・画面の実測（RUM）・外部への送信を足さない（NFR 設計 `logical-components.md` の4節）。サーバーの側の要求の数と時間は、U2 の `monitoring-design.md` のとおり既存の `http.server.requests` の `uri="/api/me/preferences"`・`uri="/api/me/password"` で見える。
8. **警報とダッシュボード**: U7 だけの警報・パネルは足さない。`/api/me/` の p95 のパネル・警報を足さないのは U2 の決定のとおり。既存の 5xx の割合の警報の全体の値に含まれる。
9. **SLI・SLO**: SLI は画面の時間（開く 2 秒・保存 1.5 秒・パスワードの変更 2.5 秒、NFR6.1〜NFR6.3）。Build and Test が `./gradlew e2eTest` の中の測りの値（場面ごとに5回）を記録し、時間で失敗させない。運用の中の判定は、手元の監視を常に動かしていない間は `Unverified` とし、observability-setup・feedback-optimization に引き継ぐ（`project.md` の Testing Posture・Deployment、U4 の Q1: A と同じ扱い）。配備したアプリに向けた画面の時間の測りは足さない。

### 検査の流れと E2E（`cicd-pipeline.md`）

10. **`./gradlew verify` と CI**: 段の並びと関門は変えない。U7 の画面部品のテスト（vitest-axe を画面ごとに1件、`fieldErrors.ts` の性質ベースのテスト（fast-check、失敗時の `seed`・`path` を出力に残す）、骨組みのテスト）は既存の Vitest の段で動き、フロントエンドのカバレッジの下限（行 80%・分岐 70%）を守る。`frontend/e2e/` は Vitest の計測の外のまま、除外の設定は変えない（NFR9.5〜NFR9.8）。新しい依存は足さない（NFR9.10）ため、lockfile・`osvScan`・Dependabot の変更は無い。make-you-chic-ui の新しい版は B4 の固定先の更新の後に使い、U7 は固定先を変えない。
11. **検査と測りのファイルの置き場**: `frontend/e2e/` の `050-display-accessibility.e2e.ts`（U4）の後の番号の新しいファイル1本（例 `05x-preferences-accessibility.e2e.ts`）に、2画面×2状態×20 組のアクセシビリティの検査と、3つの場面の画面の時間の測りを置く。`./gradlew e2eTest` の中で、`./gradlew verify` と CI の外。`workers: 1` のまま番号の順に動き、既存の 010〜040 と U4 の 050 は変えない。流れの E2E ではないため、`team.md` の「代表の流れを1本まで」に数えない（NFR9.9）。番号の最終の値と U5・U6 の検査・E2E-1 との並びは、B5 のコード生成の計画でそろえる（承認の場の U6 R-02・U7 R-02）。
12. **E2E の WAR の設定**: U7 は `frontend/playwright.config.ts` に項目を足さない。SMTP の接続先と差出人（U1）、`MASTERSMITH_WEB_BASE_URL: http://localhost:${port}`（U3）を先に足された `webServer.env` をそのまま使い、見た目の設定は入れない（U8、既定の blue・sans）。reporter の `json`（U4）の結果のファイルに、U7 の注記と添付も入る。
13. **測りに使う利用者と Mailpit（NFR 設計 `performance-design.md` の 4.2）**: 測りのテストの中で、初期管理者として要求の口（Playwright の `request`）からログインし、招待の API で実行ごとに重ならない `example.com` の宛先（例 `u7-perf-<実行の印>@example.com`）へ1件招待し、U6 の助けの部品で Mailpit の API（`127.0.0.1:8025`）からその宛先のメールを探して `/register#token=` の値を取り出し、登録の完了の API で利用者を作る。**U7 は招待のリンクをブラウザで開かない**。そのため、閲覧の履歴にトークンを含むアドレスが残る危険（A9、U6 の SD-D2）には触れず、A9 の B5 での確かめは U6 の E2E-1 の持ち物とする。
14. **Mailpit の前提と飛ばす判定**: U1 の決定で `e2eTest` は始める前に Mailpit の API に届くかを確かめて届かなければ失敗し、`webServer.env` に SMTP の設定が入るため、招待を使える設定（`invitationEnabled`）も真になる。そのため NFR 設計の 4.2 の「招待を使えない・受け手の手段が無いときは `test.skip` にして `Unverified`」の道は、通常の実行では通らない念のための道として残す（承認の場の U5 R-02 と同じく、B5 で飛ばす判定の前提を確かめる）。実行の前に `docker compose --profile mail up -d mailpit` が要ることは README の E2E の節（U1・U4 で足す）のとおり。
15. **検査と測りの資格情報**: 初期管理者のパスワードと署名鍵は既存どおり実行ごとに `randomBytes` で作って環境変数で渡し、U7 は初期管理者の設定もパスワードも変えない（ログインするだけ）。作った利用者のパスワード2つ（変更の前と後で交互に使う）は、テストの中で `randomBytes` から作る仮の値（12 コードポイント以上 72 バイト以下）で、リポジトリ・`.env` に置かない。パスワード・アクセストークン・招待のトークンと URL・作った宛先は、そのテストの変数だけに持ち、注記・添付・`console`・テストの名前・結果の JSON に出さない。結果の JSON に入るのは時間の値・組と状態の名前・成否・違反の件数と規則の名前・`incomplete` だけで、このことをコード生成で確かめる（NFR 設計 `security-design.md` の6節）。
16. **検査の要求の差し替え**: `GET /api/me/preferences` と `/api/appearance` の差し替え（`page.route`）は検査のブラウザのコンテキストの中だけで効き、アプリのコード・CSP・サーバーの設定は変えない。差し替えの答えの氏名は固定のテストの値（例 `検査 太郎`）で、実在の個人に関する値を使わない。検査は PUT・POST を送らず、送られていないことを要求の記録で確かめる。測りは本物の応答で行い、差し替えない（NFR 設計 `logical-components.md` の 5.3・5.4、`performance-design.md` の 4.1）。
17. **結果のファイルと Mailpit の中身の扱い**: `frontend/test-results/`・`frontend/playwright-report/` は既存どおり管理外。失敗のときのトレースには、ログインの画面に入れたパスワード・トークン・作った宛先が含まれうるため、コミット・共有しない（新しい秘密の置き場は作らない）。Mailpit に残る測りの招待のメールのトークンは、実行ごとの一時の内部DB の WAR にだけ効き、`e2eTest` が終わって WAR が止まると使えなくなる。開発と同じ受け手に入るため、見終えたら U1・U3 の決まりどおり Mailpit を止めて消す（ボリュームなし）。本文・トークン・宛先を Build and Test の記録に写さない。
18. **実行の時点**: 画面・認証に関わる変更を統合する前（B5）と、リリースの前に、手元で `./gradlew e2eTest` を実行する（`team.md` の Testing Posture）。骨組みの変更（ユーザーメニューの `path`）の影響は、既存の 010〜040 を変えずに通すことで確かめる（NFR9.8）。長い実行は `caffeinate -i` を付ける。README の E2E の節の表に U7 のファイルの行（検査の中身、時間は記録だけで失敗させないこと、Mailpit の起動が要ること）を足す。
19. **統合**: B5 は make-you-chic-ui・java-mustache-processor の固定先の更新を含まないため、短命のブランチから `develop` へ squash で統合し、統合の前に `./gradlew verify` を通す（`team.md` の Way of Working）。`git push` は依頼者が行う。
20. **配備と戻し**: 既存の流れ（WAR を作りイメージを作り直して `docker compose up -d`、ヘルスチェックとスモークテスト）のまま。配備の後のスモークテストでプリファレンスの保存やパスワードの変更を行うと、配備した内部DB の設定と監査ログに残るため、行うかと、行うときに先に伝えることは配備の段で決める（`project.md` の Corrections「確認の要求が監査ログに残るときは送る前に伝える」「秘密情報が要る操作は依頼者が行う」）。戻しはイメージだけを直前の版に戻す既存の決まりのまま。U7 はサーバーの状態を変えないため、戻しで消すものは無い。
21. **上流との差**: U7 について、承認済みの設計の文書と違う承認の場の決定は無い（R-01・R-02 はコード生成の計画で拾う）。要点 13 の「A9 は U7 に関わらない」、要点 14 の「飛ばす道は通常は通らない」、要点 17 の Mailpit の中身の扱い、要点 20 の配備の後の確かめの扱いは、上流に書かれていない具体を既存の決まりと先に承認された U1・U3・U4 の決定で埋めた追加として成果物に明記する。

質問にしなかった候補の論点と、決まっている理由:

| 候補の論点 | 決まっている理由 |
|---|---|
| 画面の時間の測り方と、測りに使う利用者（Mailpit に頼る） | NFR 設計の Q1: A で `e2eTest` の中の U7 のファイルに置き、招待から作った利用者で測ると決まった。Mailpit の口と取り出し方は U1・U3 の基盤の設計で決まり、助けの部品は U6 と B5 の持ち物（要点 13・14） |
| `GET /api/me/preferences` の答えの差し替え | NFR 設計の Q2: A で、検査のコンテキストの中だけで差し替え、測りは差し替えないと決まった（要点 16） |
| 検査のファイルの置き場と番号 | 置き場は NFR 設計の `logical-components.md` の 5.1 で決まり、番号は承認の場の U6 R-02・U7 R-02 で B5 のコード生成の計画でそろえると決まった（要点 11） |
| パスワードの変更を検査で行うときの資格情報 | NFR 設計の `security-design.md` の6節と、既存の `playwright.config.ts` の実行ごとに作る値の作りで決まる（要点 15） |
| 履歴の残る危険（A9）の B5 での確かめ方 | A9 は招待のリンクをブラウザで開く U6 の残る危険で、U7 はリンクを開かないため関わらない。確かめは U6 の E2E-1 と B5 の持ち物（要点 13） |
| 配備したアプリでも画面の時間を測るか | U4 の Q1: A と同じ扱い（`e2eTest` の中だけ、運用の判定は `Unverified`）で1つに決まる（要点 9） |

---

## Consolidated Summary Confirmation

答えのまとめ（質問が無いため、要点（案）の確認だけです）:

- 要点（案）は冒頭の 21 件のとおり。主な点は次のとおり。
  - 実行の形・配信とキャッシュ・CSP・資源の上限は既存のまま。U7 は設定の項目・秘密情報・内部DB の変更を持たない
  - 画面の側に独自の指標・送信を足さない。サーバーの側は U2 の `/api/me/` の指標で見る。画面の時間は Build and Test が `e2eTest` の中で記録し、運用の判定は `Unverified`
  - `./gradlew verify` と CI の段は変えず、カバレッジの下限を守る。新しい依存は無い
  - 検査と測りは `050-` の後の番号の U7 のファイル1本で、`e2eTest` の中（verify と CI の外）。番号は B5 でそろえる
  - `playwright.config.ts` に項目を足さず、U1・U3 が足した SMTP とベース URL を使う。測りの利用者は招待と Mailpit の API から作り、リンクをブラウザで開かないため A9 には触れない
  - 資格情報は実行ごとに作る仮の値で、注記・添付・結果の JSON・`console` に出さない。結果のファイルと Mailpit の中身はコミット・共有・記録に写さない
  - B5 は squash で統合。配備と戻しは既存のまま。配備の後のスモークテストでの保存・パスワードの変更の扱いは配備の段で決める

この要約で成果物を作ってよいかを選んでください。

- Looks correct — 要点のとおりに成果物を作る
- Request changes — 直したい点を書いてください

[Answer]: Looks correct
