# Infrastructure Specification — U5 招待の管理の画面（u5-invitation-ui）

U5 の基盤の設計です。U5 は画面（ui）の単位で、自分の API・内部DB の表・サーバーの状態を持たず、U3 の招待の API（契約 C5）を既存の ApiClient から呼びます。画面のビルド結果（`dist`）として実行可能 WAR に同梱され、既存のコンテナで配信されます。配備先は開発者の PC 上のコンテナだけで、クラウドの基盤は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。

- 答え: `infrastructure-design-questions.md`（要点 19 件、Q1: A、Consolidated Summary Confirmation: Looks correct）
- 上流: `construction/u5-invitation-ui/nfr-design/`（`performance-design.md`・`security-design.md`・`logical-components.md`）、`construction/u5-invitation-ui/nfr-requirements/`、`construction/u5-invitation-ui/functional-design/functional-spec.md`・`frontend-components.md`、`inception/domain-design/components.md`（InvitationUi）、`inception/contract-design/contract-summary.md`（C5・C9）、同じ段の `construction/u1-mail/infrastructure-design/`・`construction/u3-invitation/infrastructure-design/`・`construction/u4-display-foundation/infrastructure-design/`・`construction/u8-instance-appearance/infrastructure-design/`（どれも `aidlc/spaces/default/intents/260925-user-management/` の下）
- 既にある仕組み（正とする）: `Dockerfile`・`compose.yaml`・`backend/src/main/resources/application.yaml`（CSP）・`backend/src/main/java/cherry/mastersmith/common/web/CacheControlFilter.java`・`backend/src/main/java/cherry/mastersmith/auth/web/AuthController.java`・`OriginVerifier.java`・`frontend/playwright.config.ts`・`frontend/tsconfig.json`・`frontend/scripts/check-bundle-size.mjs`

## 1. Deployment

| Facet | Choice | Rationale |
|---|---|---|
| 実行の形 | 画面のビルド結果（`dist`）を同梱した既存の実行可能 WAR を、既存の `Dockerfile`・`compose.yaml` の `app` で動かす。U5 のためのコンテナ・ポート・ボリューム・資源の上限・健全性の確かめの変更は無い | U5 はブラウザの中で動く画面だけを足す（要点 1）。成果物の形は `team.md` の Deployment（`dist` を同梱した WAR） |
| 設定 | U5 は環境変数・`.env` の項目を足さない。招待を使える設定（SMTP の接続先・差出人・ベース URL）は U1・U3 の持ち物 | U5 は一覧の `invitationEnabled` と 503 の理由を表示に使うだけ（要点 1） |
| 配信とキャッシュ | 既存の `CacheControlFilter` を変えない。U5 の画面は機能の登録で遅延読み込みの塊になり、`dist/assets/` のハッシュ付きの名前で `public, max-age=31536000, immutable`。`index.html` と画面の URL は `no-cache`、招待の API（`/api/**`）は `no-store` | 招待の一覧の応答をブラウザのキャッシュに残さず、メールアドレスを残さない（NFR2.1、要点 2） |
| CSP | `application.yaml` の CSP に差分を作らない。外部への通信・外部の資源・埋め込みのスクリプトとスタイルを足さない。目立たせた行の見た目は機能の CSS（`:has()`）で付け、`style` 属性で差し込まない | NFR9.3、`security-design.md` の5節（要点 3） |
| ブラウザの保存と URL | U5 のコードは localStorage・sessionStorage を読み書きしない。今のページは画面の状態だけに持ち、URL に載せない。表示の設定は U4 の口（`useDisplaySettings`、契約 C9）から読むだけ。トークンは既存どおりメモリだけ | 共用の PC に招待のメールアドレスを残さない（NFR2.1、要点 5） |
| 内部DB と移行 | 変更なし。Flyway の新しい版は足さず、バックアップの対象も無い | U5 はサーバーの状態を持たない。招待の表（V8）の後方互換は U3 の持ち主（要点 5） |
| 環境 | 開発者の PC 上のコンテナだけ（`http://localhost:8080`、U3 の決定で招待を使える設定）。E2E は `./gradlew e2eTest` が PC の上で `java -jar` で WAR を起動し、U1 の SMTP の設定と U3 のベース URL（`http://localhost:${port}`）を受け取る。見た目の設定なし（既定の blue・sans） | `project.md` の Deployment。E2E の WAR の設定は U1・U3・U8 の決定のとおり |
| IaC | 作らない | 配備先が決まるまでクラウドの基盤は作らない（`project.md` の Deployment） |
| 資源の大きさ | 資源の上限（CPU 4・メモリ 2g の既定）は変えない。配信物の大きさの上限は置かず、変更の前後の値を記録する（3節） | サーバーの負荷は一覧の API（1ページ 20 行）の読み取りで、U3 の NFR6.3 で押さえる（NFR6.6、要点 4） |

## 2. Infrastructure Services

| Service | Role | Configuration | Notes |
|---|---|---|---|
| 静的なファイルの配信（Spring Boot の既存の配信と `CacheControlFilter`） | 画面の塊（JavaScript・CSS）の配信 | 変更なし。U5 の塊は `/assets/` のハッシュ付きの名前で `immutable` | 入口のファイルは増やさない（NFR6.6） |
| 招待の4本の API（U3、契約 C5） | 一覧（`GET /api/admin/invitations`、200）・招待（201）・送り直し（200）・取り消し（204）。管理者だけ | U5 は画面を開いたとき・ページを変えたとき・操作の後・「もう一度読み込む」のときだけ一覧を読む。決まった間隔の読み直し・画面の側の時間切れ・再試行・中断は置かない | 一覧・取り消しは p95 1 秒、招待・送り直しは p95 5 秒（U3 の NFR6.1・NFR6.3）。受け手が応答の手前で遅れ続ける既知の限界（U1 の NFR6.3）の間は Modal が送信中のまま（NFR6.3〜NFR6.5） |
| ApiClient（`frontend/src/shared/api-client/`） | 要求の共通の口 | 変更なし。アクセストークンの付与・401 での更新・`Accept-Language` は既存のまま | 成功の本文は一覧・招待・送り直しだけ JSON として読み、取り消しの 204 は本文を読まない（`security-design.md` の3.3） |
| 表示の設定の口（U4、契約 C9） | 画面の言語 | 読むだけ。`setPreview`・`setLanguage` などを呼ばない | ja・en の文言（NFR8.1・NFR8.2） |
| make-you-chic-ui（`vendor/make-you-chic-ui`） | Table・Modal・Button・RadioGroup・Alert・Badge・`useToast` | B4 の固定先の更新（`edb1f94` → `735ef04`）の後の版を使う。中身は変えない | 足りない口は Table の外側で足す（NFR9.5） |

## 3. 配信物の大きさ

| 項目 | 扱い |
|---|---|
| 上限 | 置かない（NFR6.6） |
| 初回の JavaScript | 既存の `frontendBundleSize`（`frontend/scripts/check-bundle-size.mjs`、gzip で 500KB を超えたら警告だけ）のまま |
| 記録 | コード生成で、U5 の変更の前後の初回の JavaScript の値、U5 の画面の塊が入口と別のファイルに出ること、その塊の大きさ（圧縮前と gzip）を記録する |
| 依存 | U5 は新しい依存を足さないため、`dist` と WAR の増えは U5 の画面のコードの分だけ（`cicd-pipeline.md` の3節） |

## 4. Shared Infrastructure

| Shared Resource | Owner Unit | Consumer Units | Access Boundary |
|---|---|---|---|
| 招待の4本の API（契約 C5） | U3 | U5 | 管理者だけ。サーバー側の認可（401・403・成功）は U3 のサーバー側のテストで確かめ、画面で隠すことを代わりにしない（NFR9.2） |
| 表示の設定の口（契約 C9） | U4 | U5・U6・U7 | 画面の中の関数・フックの呼び出し。U5 は読むだけ |
| 共用の検査の手伝い `frontend/e2e/support/` | U4（B4 で作る） | U5・U6・U7（B5） | U5 はログインの関数と一覧の見本の組み立ての関数を足す。形と置き場は B5 のコード生成の計画で U6・U7 とそろえる（`logical-components.md` の5.1） |
| E2E の WAR と一時の内部DB（`frontend/playwright.config.ts`） | 既存（E2E の設定。SMTP は U1、ベース URL は U3 が足す） | 010〜040・U4・U5・U6・U7 の検査 | 全ファイルで1つの WAR と内部DB を共有し、`workers: 1` で番号の順に1本ずつ動く。U5 の測定が置く招待 21 件と監査の記録は後のファイルに残るため、後のファイルは件数・順に頼らない（要点 15） |
| Mailpit（compose の profile `mail`、`127.0.0.1:1025`・`127.0.0.1:8025`） | U1 | 開発者・E2E（U5〜U7）・配備の後の確かめ（U3） | 宛先で見分ける。U5 の測定は1回の実行で 21 通を送り、消さない。U5 の検査は Mailpit の API に書き込まない（Q1: A） |

## 5. セキュリティと秘密情報

| 項目 | 扱い |
|---|---|
| 新しい秘密 | U5 は秘密情報・鍵ファイル・設定を足さない |
| 招待のトークンと URL | 応答に無く（U3 の BR5.4）、画面・状態・文言の鍵・注記に持たない。表示は応答の型の決まった項目だけから作る（NFR1.1） |
| メールアドレス | ブラウザの保存・URL・`console` に出さない（NFR2.1）。招待の API の応答は `no-store` |
| 失敗の文言 | サーバーの `detail`・`title` を使わず、`code` と状態コードの種類から選ぶ（NFR9.1） |
| テストのデータ | 検査の見本と測定で置く招待のメールアドレスは `example.com` の下の値だけ、招待した管理者の氏名は架空の値（公開のリポジトリのため、要点 7） |
| 測定のアクセストークン | 要求の口で得たトークンはテストの変数だけに持ち、注記・添付に出さない。ログインの API には Origin の確かめが当たらない（`AuthController` と `OriginVerifier` の確かめはトークンの更新とログアウトだけ）ため、要求の口からそのまま呼べる（要点 14） |
| 検査の結果のファイル | 失敗のときのトレースに仮の初期管理者のパスワード・アクセストークン・見本のメールアドレスが含まれうるため、コミット・共有しない（`cicd-pipeline.md` の5節） |

## 6. 上流との差

承認済みの文書は書き換えず、差をここに記録します（`project.md` の Way of Working）。

| 対象 | 承認済みの形 | この段の設計 | 理由 |
|---|---|---|---|
| 招待の API の応答のキャッシュ | U5 の NFR 設計に記述は無い | 既存の `CacheControlFilter` の `/api/**` の `no-store` がそのまま当たることを明記した | 既存の仕組みで埋めた追加。食い違いではない |
| Mailpit の共用と 21 通 | NFR 設計は「受け手の設定の渡し方は infrastructure-design の持ち主」 | U1 の設定の渡し方を使い、測定の 21 通は消さない（Q1: A） | U1・U3 の決定と Q1 の答えで埋めた追加 |
| 要求の口からのログイン | NFR 設計の `performance-design.md` の4.2 | ログインの API に Origin の確かめが当たらないことをコードで確かめて明記した | 実現の前提の確かめ。食い違いではない |
| 承認済みの設計の文書 | — | 食い違う点は無い。U5 R-01・R-02 はコード生成の計画で拾う | NFR Design の承認の場の決定のとおり |
