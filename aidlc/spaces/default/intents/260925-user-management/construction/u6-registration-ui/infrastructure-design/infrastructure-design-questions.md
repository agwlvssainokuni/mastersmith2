# Infrastructure Design の質問 — u6-registration-ui（招待された人の登録の完了の画面、ui）

U6 は、招待メールのリンクから開く登録の完了の画面（`/register`）を受け持つ画面の単位です。自分の API・内部DB の表・サーバーの状態を持たず、U3 の公開の API（`POST /api/registration/verify`・`POST /api/registration/complete`）と U4 の表示の土台を使います。ui の単位のため、この段の成果物は `infrastructure-specification.md`・`monitoring-design.md`・`cicd-pipeline.md`・`traceability.json` です。

配備先は開発者の PC 上のコンテナだけで、クラウドの基盤は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。U6 の基盤のほとんどは、承認済みの NFR 要件・NFR 設計と、この段で先に承認された U1・U3・U4・U8 の決定、既存の仕組みで決まっています。この段に持ち越された「E2E が Mailpit から招待のリンクを取り出す方法」（U3 が U6 と B5 に残した助けの部品と代表の流れ1本）も、大半は上流の決まりから1つに決まるため要点（案）に書き、上流から1つに決まらない2点（Mailpit に残ったメールの片付け、Playwright の報告に載るリンクの扱い）だけを質問にします。

読んだ上流（どれも `aidlc/spaces/default/intents/260925-user-management/` の下）:

- この単位の承認済みの NFR 設計 `construction/u6-registration-ui/nfr-design/`（`performance-design.md` の2節（5回の計測と 2.1 の確かめ）・4節（検査の回数の見込み）、`security-design.md` の 1.3〜1.5（トークンの確かめ・テストの値・残る危険）・5節（差し替えと見本の照合）・7節、`logical-components.md` の 5節（ファイルの置き場と非干渉）・8節、`traceability.json`、`nfr-design-questions.md` の Q1: A・Q2: A）と、承認の場の決定（監査ログ 2026-09-27 の `DECISION_RECORDED`: U6 R-01「B5 で handOffToLogin を確かめる」、U6 R-02・U7 R-02「検査のファイルの番号は B5 でそろえる」、A9「閲覧の履歴は機能設計を書き換えず残る危険として受け入れ、B5 で実際の動きを確かめて記録する」）
- この単位の承認済みの NFR 要件 `construction/u6-registration-ui/nfr-requirements/`（NFR1.1〜NFR1.5、NFR6.1〜NFR6.3、NFR7.1〜NFR7.5、NFR9.1〜NFR9.11、`tech-stack-decisions.md`）
- この単位の承認済みの機能設計 `construction/u6-registration-ui/functional-design/`（`functional-spec.md` の W1〜W13・D1〜D12、W13 の5「リンクの取り出し方は infrastructure-design の持ち主」、`frontend-components.md`）
- 部品の一覧 `inception/domain-design/components.md` と契約 `inception/contract-design/contract-summary.md`（C6・C9）
- この段で先に承認された同じ段の成果物: `construction/u1-mail/infrastructure-design/cicd-pipeline.md`（Mailpit の SMTP 1025 と API 8025 は `127.0.0.1` だけに公開、E2E の WAR は `webServer.env` で SMTP の接続先と差出人を受け取る、`e2eTest` は始める前に Mailpit の API に届くかを確かめる、E2E は実行ごとに違う宛先を使いその宛先で取り出す）、`construction/u3-invitation/infrastructure-design/`（E2E の WAR に `MASTERSMITH_WEB_BASE_URL: http://localhost:${port}`、本文に `ベース URL ＋ /register#token= ＋ トークン`、取り出した URL をログ・報告・Playwright の報告の添付に出さない、Mailpit が受けたメールは見終えたら止めて消す）、`construction/u4-display-foundation/infrastructure-design/`（共用の検査の手伝い `frontend/e2e/support/` は U4 が B4 で作る、reporter に `json` を足し `frontend/test-results/` に書く、結果のファイルはコミット・共有しない）、`construction/u8-instance-appearance/infrastructure-design/`（E2E の WAR は見た目の設定なしで既定の blue・sans）
- 決まり `aidlc/spaces/default/memory/org.md`・`team.md`・`project.md`・`phases/construction.md`
- 既存の仕組み: `frontend/playwright.config.ts`（`workers: 1`・`retries: 0`・`trace: 'retain-on-failure'`・reporter `list`・`html`、初期管理者 `adminEmail`・`E2E_ADMIN_PASSWORD` を実行ごとに作る、WAR は一時ディレクトリの内部DB で `18081`）・`frontend/e2e/010`〜`040`（`collectProblems` の CSP 違反とエラーの集め方）・`build.gradle.kts`（`verify`・`frontendBundleSize`・`e2eTest`）・`.github/workflows/ci.yml`・`.gitignore`（`frontend/test-results/`・`frontend/playwright-report/` は管理外）・`frontend/package.json`（Node 24、`@playwright/test`・`fast-check` は既存）・`compose.yaml`（Mailpit は B1 で足す）・README の E2E の節

## Infrastructure Design の要点（案）

### 配備と配信（`infrastructure-specification.md`）

1. **実行の形**: 画面は既存の実行可能 WAR に同梱した `dist` の一部で、既存の `Dockerfile`・`compose.yaml` の `app` で動く。U6 のためのコンテナ・ポート・ボリューム・環境変数・資源の上限・健全性の確かめの変更は無い。`/register` は既存の画面の URL の配信（SPA の戻し先）で配られる。
2. **配信とキャッシュ・CSP**: 既存の `CacheControlFilter`（`/assets/**` は長いキャッシュ、画面の URL は `no-cache`、`/api/**` は `no-store`）、`application.yaml` の CSP、`SecurityConfig` の `Referrer-Policy` に差分を作らない。画面の部品に外部の URL を置かない（NFR1.4・NFR9.5、`security-design.md` の 1.3）。
3. **画面の塊**: 登録の完了の画面は機能の登録の遅延読み込みで入口と別の塊にする。初回の JavaScript の目安は既存の `frontendBundleSize`（gzip で 500KB を超えたら警告だけ）のまま。B5 のコード生成で、塊が入口から静的にたどれないこと（`dist/.vite/manifest.json`）と塊の大きさを記録する（NFR6.3、`performance-design.md` の3節）。
4. **ブラウザに残す値**: U6 は新しい保存の鍵を足さない。トークン・招待のメールアドレス・パスワードはフックのメモリだけに持ち、localStorage・sessionStorage・URL・`history.state` に置かない（NFR1.2・NFR2.1）。
5. **サーバーの資源**: U6 はサーバーの状態・内部DB・Flyway を変えないため、バックアップ・スキーマの後方互換の論点は無い。使う API は U3 の2つ（`verify` は招待を消費せず監査しない、U3 の BR7.1）と U8 の `GET /api/appearance`。公開の API にトークンを付けないパスの一覧は U4 の ApiClient の持ち物（共有の表に載せる）。
6. **秘密情報と個人に関する値**: U6 は新しい秘密情報・鍵ファイル・設定を持たない。テストのデータに実在の個人に関する値を使わず、メールアドレスは `example.com` にする。アクセシビリティの検査の見本のトークンは、どの招待にも当たらない低いエントロピーの固定の値（例 `a11y-sample-token`）とし、Gitleaks に掛からない形にする（NFR1.5）。

### 監視（`monitoring-design.md`）

7. **指標とログ**: 画面の側に独自の指標・ログの送り先・画面の実測（RUM）・外部への送信を足さず、`console` に何も出さない（`logical-components.md` の4節）。サーバーの側の要求の数と時間は既存の `http.server.requests` の `uri="/api/registration/verify"`・`uri="/api/registration/complete"`、登録の完了の記録は U3 の監査（`REGISTRATION_COMPLETED`）で見える。どちらも U3 の持ち物。
8. **警報とダッシュボード**: U6 だけの警報・パネルは足さない。登録の API のパネル・警報は U3 の決定のとおり observability-setup の段で扱う。
9. **SLI・SLO**: SLI は「キャッシュが空の新しいコンテキストで招待のリンクを開いてから、メールアドレスの欄が見えるまでの時間」、目標は5回すべて 2 秒以内（NFR6.1）。E2E-1 の中で測り、5回の値（ミリ秒だけ）をテストの注記と添付に残し、U4 の決定の `json` の reporter のファイルから Build and Test が結果に写す。統合の関門にしない。運用の中での判定は `Unverified` とし、持ち主の段（observability-setup・feedback-optimization）を明記して引き継ぐ（`project.md` の Testing Posture・Deployment）。

### 検査の流れ（`cicd-pipeline.md`）

10. **1コマンドの検査**: 既存の `./gradlew verify` のまま（CI も同じタスク）。U6 の画面部品のテスト（Vitest ＋ Testing Library ＋ user-event ＋ vitest-axe、部品ごとにアクセシビリティ検査1件）と `shared/validation` の性質ベースのテスト（fast-check、失敗時の `seed`・`path` を出力に残す）は既存の単体テストの段で動く。フロントエンドのカバレッジの下限（行 80%・分岐 70%）を守り、計測の除外を増やさない。`frontend/e2e/` は既存どおり計測の対象外（NFR9.8・NFR9.9）。`frontend/tsconfig.json` の `include` が `e2e` を含むため、見本の型のずれは `verify` の型検査で落ちる（`security-design.md` の 5.2）。ビルドの設定・CI・Dependabot の変更は無い。
11. **依存**: U6 は新しい依存を足さない（`@playwright/test`・`fast-check` は既存、`axe-core` は U4 が B4 で足す）。Mailpit の API は Node 24 の組み込みの `fetch` で呼び、HTTP の部品を足さない。

### E2E-1 と Mailpit の取り出し（`cicd-pipeline.md`、この段に持ち越された論点）

12. **置き場と本数**: E2E-1（W13: 管理者でログイン → U5 の画面で招待 → Mailpit で受けてリンクを取り出す → 5回の計測 → リンクを開いて登録の完了 → ログインの画面の案内とメールアドレスの持ち越し → 新しい利用者でログイン → 管理メニューが無く管理画面の URL も使えない → ログアウト）を `frontend/e2e/` に1本足す。この Intent の代表の流れの1本で、アクセシビリティの検査は別のファイル（流れの本数に数えない）。ファイルの番号は B5 のコード生成の計画で U5・U7 とそろえて決める（承認の場の U6 R-02）。`./gradlew e2eTest` の中で、`verify` と CI の外。
13. **WAR への設定**: U6 は `playwright.config.ts` の `webServer.env` に何も足さない。SMTP の接続先と差出人は U1、`MASTERSMITH_WEB_BASE_URL: http://localhost:${port}` は U3 が足す。見た目の設定は無し（U8）。既存の初期管理者の設定（`adminEmail`・`E2E_ADMIN_PASSWORD`）は読むだけで変えない。
14. **前提**: U1 の決定のとおり、`e2eTest` は始める前に `127.0.0.1:8025` の Mailpit の API に届くかを確かめ、届かなければ `docker compose --profile mail up -d mailpit` を示して失敗させる。E2E-1 は Mailpit を起動・停止しない。Mailpit の API の場所（`http://127.0.0.1:8025`）は U1 の前提の確かめと同じ1か所の定数を使う（2か所で値を持たない）。
15. **実行ごとに違う宛先**: 招待するアドレスは `e2e-invitee-<実行時刻>-<乱数の16進>@example.com` の形で実行ごとに作る。開発で見るメールや前の実行のメールと重ならず、前のテストの状態に頼らない（`team.md` の Testing Posture）。実在の宛先・外部の SMTP へは送らない（`project.md` の Forbidden）。
16. **取り出しの助けの部品**: `frontend/e2e/support/` の下（U4 が B4 で作る置き場）に Mailpit の取り出しのモジュールを1つ置く（名前はコード生成で決める。例 `mailpit.ts`）。状態を持たず、次の順で動く。
    - (a) Mailpit の検索の API（`GET /api/v1/search`、問い合わせは宛先の `to:`）でそのアドレスのメッセージを探し、見つかった各メッセージの宛先がそのアドレスと完全に一致するものだけを残す。1通でないとき（0通・2通以上）は失敗にする（送り直しを含まない流れのため、1通のはず）。
    - (b) メッセージの取得の API（`GET /api/v1/message/{ID}`）で HTML と本文の文字を読み、`/register#token=` を含む URL を取り出す。HTML の `href` と本文の文字の URL が同じで、Playwright の `baseURL` ＋ `/register#token=` で始まることを確かめる（U3 のベース URL から組み立てる決まりの実際のブラウザでの裏付け）。
    - (c) 待つ時間の上限は 10 秒、間隔は 500 ミリ秒（`expect.poll`）。招待の送信は要求の中で済む（ADR-009）ため、招待の応答の後すぐに届く。上限を超えたら、Mailpit の起動・U1 の SMTP の設定を確かめる旨だけの失敗にする。
    - (d) 失敗の知らせ・`console`・注記・添付・標準出力に、アドレス・URL・トークン・本文を載せない（件数と「見つからない」などの種類だけ）。Mailpit への要求は Playwright の `request` ではなく Node の `fetch` で送り、Mailpit の応答の本文（トークンとアドレスを含む）がトレースと報告に記録されないようにする。
17. **応答の形の照合**: E2E-1 は本物の確かめの応答を `page.waitForResponse` で受け、状態コード 200、項目の名前の集まりと各項目の型が見本と同じことを確かめる。値は比べず、失敗の知らせにも項目の名前と型だけを出す。見本は `frontend/e2e/` の手伝いのモジュールに1つだけ置き、画面の側の型を付ける（NFR 設計の Q1 A、`security-design.md` の 5.2、`project.md` の学び）。
18. **フォームの時間の測り方**: 取り出した後、登録を完了する前に、5回とも新しいコンテキストで同じリンクを開き、移動の直前からメールアドレスの欄が見えるまでをテストの側の時計で測る。同じ5回で (a) アドレス欄に `#token=` が無い、(b) CSP の違反が無い、(c) localStorage・sessionStorage にトークンが無いことを確かめ、こちらは失敗の条件にする（`performance-design.md` の2節）。長い実行は `caffeinate -i` を付けて流す（`project.md` の Testing Posture）。
19. **E2E-1 が残すもの**: E2E-1 が作る招待・利用者・監査の行（`INVITATION_ISSUED`・`REGISTRATION_COMPLETED` など）は、`e2eTest` の一時ディレクトリの内部DB にだけ残り、配備したアプリの内部DB と監査ログを汚さない。`workers: 1` で後のファイルに利用者が残るが、後のファイルはこの利用者に頼らない（`logical-components.md` の 5.2）。Mailpit に残るメールの扱いは → Q1。
20. **アクセシビリティの検査**: 登録の完了の画面の2状態（`ready`・`unavailable`）× 20 組を U4 の手伝いで流し、`ready` は `POST /api/registration/verify` だけを `page.route` で差し替える。ログインせず、サーバーの状態を変える要求を送らず、Mailpit を使わない（`security-design.md` の 5.1）。回数の見込みは U6 の分で約 1〜2 分、実測は B5 と Build and Test で記録する。

### 統合・配備・戻し（`cicd-pipeline.md`）

21. **統合**: U6 は B5（U5〜U7 の画面）で作る。画面・認証に関わる変更のため、統合の前に `./gradlew verify` と `./gradlew e2eTest`（Mailpit を起動して）を手元で通し、短命のブランチから `develop` へ squash で統合する（`team.md` の Way of Working・Testing Posture）。B5 にサブモジュールの固定先の更新は無い見込み（更新は B4）。`git push` は依頼者が行う。
22. **配備の後の確かめ**: 画面の単位としての配備の後の確かめは、フラグメントの無い `/register` を開いて「リンクが使えない」の表示が出ること（API を呼ばず、内部DB・監査を変えない）とする。招待から登録の完了までの確かめ（配備した内部DB に利用者と監査の行が残る）は U3 の決定のとおり deployment-pipeline で、送る前に依頼者に伝える。
23. **戻し**: 戻しは直前の版のイメージだけ（スキーマの変更は無い）。B5 の前の版に戻すと `/register` の画面が無くなり、戻している間に届いた招待のリンクは開けない。トークンは有効期限まで使えるため、戻しを直せば同じリンクで続けられる。この点を戻しの手順の注意に書く（手順は deployment-pipeline）。
24. **README に足すこと**: E2E の節の表に E2E-1 とアクセシビリティの検査の行（Mailpit の起動が要ること、時間は記録だけで失敗させないこと、取り出したリンクを報告に写さないこと）を足す。メールの片付けは Q1 の答えのとおり書く。

## 決まっていること（質問にしない）

| 決まっていること | 出典 |
|---|---|
| Mailpit の SMTP・API は `127.0.0.1` だけに公開し、E2E の WAR は `webServer.env` で SMTP の設定を受け取る。`e2eTest` は Mailpit に届かなければ手順を示して失敗する | U1 の同じ段 Q1 A |
| E2E の WAR のベース URL は `http://localhost:${port}`。本文に `ベース URL ＋ /register#token= ＋ トークン`。取り出した URL をログ・報告・添付に出さない | U3 の同じ段の要点 11・12 |
| 共用の手伝い `frontend/e2e/support/`、`json` の reporter、結果のファイルをコミット・共有しない | U4 の同じ段 Q2 A、要点 6 |
| E2E-1 で5回の計測と 2.1 の3つの確かめ、見本と本物の応答の形の照合 | U6 の NFR 設計 Q1 A、`performance-design.md` の2節 |
| E2E は代表の流れを Intent ごとに1本まで、検査は本数に数えない、`verify` と CI の外 | `team.md` の Testing Posture、`project.md` の学び |
| ファイルの番号・共用の手伝いの重なりは B5 の計画で U5・U7 とそろえる | NFR 設計の承認の場の U6 R-02・U7 R-02 |

## Q1. E2E-1 が Mailpit に残す招待メールを、どう片付けますか？

背景: Mailpit はボリュームを置かず、受けたメールをコンテナの中にだけ持ちます（U1）。E2E-1 は実行のたびに1通の招待メール（有効な招待のリンクと `example.com` のアドレス）を Mailpit に入れます。リンクは E2E の一時の内部DB の招待にしか効かず、流れの最後で登録を完了するため使い終わりますが、流れが途中で落ちると使われていないリンクが残ります。U3 の決定は「Mailpit が受けたメールは見終えたら止めて消す」で、開発で見るメールと E2E のメールは同じ受け手に入り、宛先で見分けます。Mailpit には ID を指定してメッセージを消す API（`DELETE /api/v1/messages`）があります。すべてを消す操作は、開発者が見ているメールも消すため使いません。

A. E2E-1 が自分の宛先のメッセージだけを、ID を指定して消す。流れの成否にかかわらず、テストの後始末（`finally` か `afterEach`）で消す。失敗を調べるときは流し直す（一時の内部DB は実行ごとに作り直すため、残したリンクは調べる役に立たない）。開発者の見ているメールには触らない。止めて消す U3 の決まりはそのまま残す（推奨）
B. A と同じく ID を指定して消すが、流れが成功したときだけ消し、失敗のときは Mailpit の画面で本文を見られるように残す。残ったメールは開発者が止めて消す
C. E2E-1 は消さない。開発者が `e2eTest` の後に Mailpit を止めて消す（`docker compose rm -sf mailpit`）手順を README に書く。E2E の作りは小さくなるが、手の作業を忘れると実行ごとのメールがたまる
X. Other (please specify)

[Answer]: C

## Q2. 開いたリンク（トークンを含む URL）が Playwright の HTML の報告と失敗のときのトレースに載ることを、どう扱いますか？

背景: 要点 16 の (d) で、Mailpit の応答と取り出した URL を、注記・添付・失敗の知らせ・標準出力に載せない作りにします。ただし、画面の側でリンクを開く `page.goto(リンク)` は、Playwright が操作の記録として残します。既存の reporter の `html`（`frontend/playwright-report/`）は成功したときも操作の題（開いた URL を含む）を持ち、失敗のときのトレース（`trace: 'retain-on-failure'`、`frontend/test-results/`）は URL と画面の写しを持ちます。どちらも `.gitignore` で管理外で、手元だけに置き共有しないことが NFR 要件（NFR1.5）と NFR 設計（`security-design.md` の 1.4）で決まっています。トークンは E2E の一時の内部DB の招待にしか効かず、流れの最後に登録を完了して使い終わります。U3 の決定の「報告の添付に出さない」は添付を指し、操作の題とトレースは決めていません。U4 の `json` のファイルに操作の題が入るかは Playwright の版で違いうるため、コード生成で確かめます。

A. 受け入れる。`page.goto(リンク)` のまま開き、HTML の報告とトレースに使い捨てのトークンが載ることを NFR1.5・`security-design.md` の 1.4 の範囲として受け入れる。注記・添付・失敗の知らせ・標準出力・`json` のファイルには載せない（`json` のファイルに操作の題が入るならその扱いをコード生成で確かめる）。README に「E2E の報告とトレースは共有しない」と書く。U3 の文言との差を `cicd-pipeline.md` の上流との差に記録する（推奨）
B. HTML の報告の題に出ない開き方にする。リンクを開く操作を `page.evaluate` の中の `location.assign` で行い、題に URL が出ないようにする。失敗のときのトレースには残る。計測の時計の起点は `location.assign` の直前にする。実際の移動は同じだが、既存の E2E と開き方がそろわない
C. B に加えて、E2E-1 だけトレースを残さない（`test.use({ trace: 'off' })`）。報告とトレースのどちらにもトークンが残らない代わりに、E2E-1 が落ちたときの調べる手がかりが画面の写しと失敗の知らせだけになる
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめ（回答の後に書き直します）:

- 要点（案）は冒頭の 24 件のとおり（実行の形・配信とキャッシュ・CSP は変えない、画面の塊は遅延読み込みで大きさを記録、ブラウザに新しい保存を足さない、サーバーの状態とスキーマの変更は無い、画面の側に独自の指標・送信を足さない、NFR6.1 は E2E-1 の中で測って Build and Test が記録し運用の判定は `Unverified`、`./gradlew verify` と CI は変えずカバレッジの下限を守る、新しい依存は無い、E2E-1 は `frontend/e2e/` に1本で番号は B5 でそろえる、WAR の設定は U1・U3 が足し U6 は足さない、Mailpit の API の場所は U1 の前提の確かめと同じ定数、実行ごとに違う `example.com` の宛先、`frontend/e2e/support/` の取り出しの部品（宛先の完全な一致で1通、HTML と本文の URL の一致とベース URL の確かめ、上限 10 秒、値を出さず Node の `fetch` で呼ぶ）、見本と本物の応答の形の照合、5回の計測と3つの確かめ、E2E-1 の行は一時の内部DB にだけ残る、検査は差し替えで Mailpit を使わない、B5 は `verify` と `e2eTest` を通して squash で統合、配備の後の確かめはフラグメントの無い `/register`、戻しはイメージだけで戻している間のリンクの注意、README の E2E の節）
- Q1: C — E2E-1 は Mailpit のメールを消さない（U5 の測定のメールと同じ扱い）。E2E は Mailpit の API に書き込まず、開発者が `e2eTest` の後に Mailpit を止めて消す手順（`docker compose rm -sf mailpit`）を README の E2E の節に書く
- Q2: A — 招待のリンクは `page.goto` のまま開き、Playwright の HTML の報告と失敗のときのトレースに使い捨てのトークンが載ることを NFR1.5・`security-design.md` の 1.4 の範囲（手元だけ、共有しない）として受け入れる。注記・添付・失敗の知らせ・標準出力・`json` のファイルには載せない（`json` のファイルに操作の題が入るかはコード生成で確かめる）。README に「E2E の報告とトレースは共有しない」と書き、U3 の文言（報告の添付に出さない）との差を `cicd-pipeline.md` の上流との差に記録する

Does this all look correct before I generate the artifact?

- Looks correct — 要点と答えのとおりに成果物を作る
- Request changes — 直したい点を書いてください

[Answer]: Looks correct
