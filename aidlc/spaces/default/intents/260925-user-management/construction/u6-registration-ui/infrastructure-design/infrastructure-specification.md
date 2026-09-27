# Infrastructure Specification — U6 登録の完了の画面（u6-registration-ui）

U6 の基盤の設計です。U6 は、招待メールのリンクから開く登録の完了の画面（`/register`）を受け持つ画面（ui）の単位です。自分の API・内部DB の表・サーバーの状態は持たず、U3 の公開の API（`POST /api/registration/verify`・`POST /api/registration/complete`）、U8 の `GET /api/appearance`、U4 の表示の土台を使います。配備先は開発者の PC 上のコンテナだけで、クラウドの基盤（IaC・検証環境・警報の通知先）は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。

- 答え: `infrastructure-design-questions.md`（要点 24 件、Q1: C、Q2: A、まとめの確認は Looks correct）
- 上流: この単位の NFR 設計 `construction/u6-registration-ui/nfr-design/`（`performance-design.md`・`security-design.md`・`logical-components.md`）、NFR 要件、機能設計（W1〜W13・D1〜D12）、`inception/domain-design/components.md`、`inception/contract-design/contract-summary.md`（C6・C9）。この段で先に承認された U1・U3・U4・U8 の基盤の設計
- 既にある仕組み（正とする）: `Dockerfile`・`compose.yaml`・`backend/src/main/resources/application.yaml`（CSP）・`backend/src/main/java/cherry/mastersmith/common/web/CacheControlFilter.java`・`frontend/playwright.config.ts`・`.gitignore`・README

## 1. Deployment

| Facet | Choice | Rationale |
|---|---|---|
| Compute model | 既存の実行可能 WAR（画面のビルド結果 `dist` を同梱）を、既存の `Dockerfile`・`compose.yaml` の `app` で動かす。U6 のためのコンテナ・ポート・環境変数・資源の上限・健全性の確かめの変更は無い | 画面は WAR の中の静的なファイルで、実行の形は変わらない（要点 1） |
| Networking topology | `/register` は既存の画面の URL の配信（SPA の戻し先）で配る。画面は同じオリジンの API だけを呼び、外部への通信を足さない。`Referrer-Policy` と CSP（`default-src 'self'` ほか）に差分を作らない | 招待のトークンをリファラーや外部の読み込みで漏らさない（NFR1.4・NFR9.5、要点 2） |
| Storage strategy | 新しいブラウザの保存の鍵を足さない。トークン・招待のメールアドレス・パスワードはフックのメモリだけに持ち、localStorage・sessionStorage・URL・`history.state` に置かない。サーバーの側の保存（内部DB・Flyway）の変更は無い | NFR1.2・NFR2.1（要点 4・5） |
| Environments | 配備した環境（`compose.yaml`、プロジェクト `mastersmith`）1つ。E2E は WAR を PC の上で直接起動する（`frontend/playwright.config.ts`、一時ディレクトリの内部DB、番号 18081）。Mailpit は profile `mail` で開発者が起動する（U1） | 配備先が決まるまで検証環境・本番環境は無い（`team.md` の Deployment） |
| IaC approach | 無し。コンテナの設定は既存の `compose.yaml` と `.env` のまま | 配備先が決まるまでクラウドの基盤を作らない（`project.md` の Deployment） |
| Resource sizing | 変えない（既定の CPU 4・メモリ 2g）。画面の塊は遅延読み込みで入口と別のファイルにし、初回の JavaScript の目安は既存の `frontendBundleSize`（gzip で 500KB を超えたら警告だけ）のまま。B5 のコード生成で、塊が入口から静的にたどれないこと（`dist/.vite/manifest.json`）と塊の大きさ（圧縮前・gzip）を記録する | NFR6.3（要点 3） |
| Caching | 既存の `CacheControlFilter` を変えない（`/assets/**` は長いキャッシュ、画面の URL は `no-cache`、`/api/**` は `no-store`）。画面の塊のファイルは名前にハッシュが付き、`/assets/**` と同じ扱いになる | 既存の配信の決まりに乗る（要点 2） |
| Schema and rollback | スキーマの変更は無い。戻しは直前の版のイメージだけ（`cicd-pipeline.md` の6節） | `team.md` の Deployment（前進のみ・後方互換）。U6 は内部DB に触れない |

## 2. Infrastructure Services

| Service | Role | Configuration | Notes |
|---|---|---|---|
| 登録の API（U3、`POST /api/registration/verify`・`POST /api/registration/complete`） | 公開の API（認証なし） | U3 の持ち物。確かめは招待を消費せず監査しない（U3 の BR7.1）。p95 は U3 の NFR6.3・NFR6.4 | U6 はトークンを要求の本文だけで送る。ApiClient の公開のパスの一覧は U4 の持ち物 |
| 見た目の設定（U8、`GET /api/appearance`） | 公開の API（認証なし） | U8 の持ち物。I/O を持たない | 最初の描画のゲート（U4）で1回呼ぶ |
| 内部DB（組み込みの H2） | database | 変更なし | U6 は読み書きしない |
| 手元の受け手（Mailpit、profile `mail`） | メールの受け手（開発・E2E の確認用） | U1 のとおり（`axllent/mailpit:v1.31.2` をダイジェストで固定、`127.0.0.1:8025`・`127.0.0.1:1025`、ボリュームなし、`256m`） | E2E-1 は API の読み取り（`GET`）だけを使い、書き込まない（Q1: C）。片付けは開発者が止めて消す（`cicd-pipeline.md` の4.5） |
| E2E の WAR（`./gradlew e2eTest`） | 一時の実行の環境 | `frontend/playwright.config.ts` の `webServer`。SMTP の接続先と差出人は U1、`MASTERSMITH_WEB_BASE_URL: http://localhost:${port}` は U3 が足す。U6 は足さない。見た目の設定は無し（U8） | 一時ディレクトリの内部DB にだけ E2E-1 の招待・利用者・監査の行が残る（要点 13・19） |

## 3. Shared Infrastructure

| Shared Resource | Owner Unit | Consumer Units | Access Boundary |
|---|---|---|---|
| 検査の手伝い `frontend/e2e/support/`（組の切り替え・axe・はみ出し・CSP の違反の集め） | U4（B4 で作る） | U5・U6・U7 | 状態を持たないモジュール。U6 は読むだけで、共用の部分の重なりの扱いは B5 の計画で U5・U7 とそろえる |
| Mailpit の取り出しの部品（`frontend/e2e/support/` の下、名前はコード生成で決める。例 `mailpit.ts`） | U6（B5 で作る） | U6 の E2E-1（U5・U7 が使うときも同じ部品） | Mailpit の API の読み取り（`GET /api/v1/search`・`GET /api/v1/message/{ID}`）だけ。書き込み・消す API は使わない（Q1: C）。値をテストの外に出さない（`cicd-pipeline.md` の4.3） |
| Mailpit の API の場所（`http://127.0.0.1:8025`） | U1（`e2eTest` の前提の確かめ） | U6 の取り出しの部品 | 1か所の定数を共有し、2か所で値を持たない（要点 14） |
| 確かめの見本（型付きの答え1つ） | U6 | U6 の E2E-1 とアクセシビリティの検査 | 状態を持たない。画面の側の確かめの応答の型を `import type` で読む |
| 手元の受け手（Mailpit） | U1 | U3（招待・送り直し）・U5〜U7 の E2E・開発者の確認 | 同じ受け手を分け合い、宛先で見分ける。E2E は実行ごとに違う宛先を使う |
| E2E の WAR の設定（`webServer.env`） | U1（SMTP）・U3（ベース URL） | U5〜U7 の E2E | U6 は読むだけ。初期管理者の設定（`adminEmail`・`E2E_ADMIN_PASSWORD`）も読むだけ |
| ApiClient の公開のパスの一覧 | U4 | U6（登録の2つの API） | U6 は ApiClient を変えない（NFR9.1） |

## 4. セキュリティと秘密情報

| 対象 | 扱い | 出典 |
|---|---|---|
| 新しい秘密情報・鍵ファイル・設定 | U6 は持たない | 要点 6 |
| 招待のトークン（画面） | フックのメモリだけに持ち、描画の確定の後にアドレス欄から消す。保存・`console`・文言に出さない | NFR1.1〜NFR1.3、`security-design.md` の1節 |
| E2E-1 のトークンとパスワード | 実行ごとの一時の内部DB の中の使い捨ての値。トークンは流れの最後に登録を完了して使い終わる | NFR1.5 |
| 検査の見本のトークン | どの招待にも当たらない低いエントロピーの固定の値（例 `a11y-sample-token`）。Gitleaks に掛からない形 | NFR1.5、要点 6 |
| E2E-1 で取り出した URL・アドレス・メールの本文 | 注記・添付・失敗の知らせ・標準出力・`json` のファイルに載せない。Mailpit への要求は Node の `fetch` で送り、応答の本文をトレースと報告に記録させない | 要点 16 の (d)、U3 の要点 12 |
| Playwright の HTML の報告と失敗のときのトレース | `page.goto(リンク)` の操作の記録に使い捨てのトークンが載ることを受け入れる。どちらも `.gitignore` で管理外、手元だけで共有しない（README に書く） | Q2: A、NFR1.5、`security-design.md` の1.4 |
| Mailpit に残る招待メール | E2E-1 は消さない。有効な招待のリンクと `example.com` のアドレスが入るため、開発者が `e2eTest` の後に止めて消す | Q1: C、U3 の7節 |
| テストのデータ | 実在の個人に関する値を使わず、メールアドレスは `example.com` | 要点 6、`project.md` の Forbidden |

## 5. 上流との差

承認済みの文書は書き換えません（`aidlc/spaces/default/memory/project.md` の Way of Working）。

| ID | 文書 | 承認済みの記述 | この段での扱い | 理由 |
|---|---|---|---|---|
| I-D1 | U3 の基盤の設計 `cicd-pipeline.md` の5節・`infrastructure-specification.md` の7節 | 取り出した URL はログ・報告・Playwright の報告の添付に出さない | 添付・注記・知らせ・標準出力・`json` のファイルには出さないが、HTML の報告の操作の題と失敗のときのトレースに載ることは受け入れる（`cicd-pipeline.md` の7節の C-D1） | 依頼者の決定（Q2: A） |
| I-D2 | U3 の基盤の設計 `infrastructure-specification.md` の7節 | Mailpit が受けたメールは見終えたら止めて消す | E2E-1 は自分で消さず、開発者が `e2eTest` の後に止めて消す手順を README に書く | 依頼者の決定（Q1: C）。U3 の決まりを具体の手順にしたもので、食い違いではない |
| I-D3 | 質問の文書の要点 16 | 取り出しの部品の作り | 消す API（`DELETE /api/v1/messages`）を使う記述は置かない。読み取りの API だけ | Q1: C の答えに合わせた |
