# Infrastructure Specification — U4 画面の表示の設定の土台（u4-display-foundation）

U4 の基盤の設計です。U4 は画面（ui）の単位で、自分の API・内部DB の表・サーバーの状態を持ちません。画面のビルド結果（`dist`）として実行可能 WAR に同梱され、既存のコンテナで配信されます。配備先は開発者の PC 上のコンテナだけで、クラウドの基盤は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。

- 答え: `infrastructure-design-questions.md`（要点 18 件、Q1: A、Q2: A、Consolidated Summary Confirmation: Looks correct）
- 上流: `construction/u4-display-foundation/nfr-design/`（`performance-design.md`・`security-design.md`・`logical-components.md`）、`construction/u4-display-foundation/nfr-requirements/`、`construction/u4-display-foundation/functional-design/functional-spec.md`・`frontend-components.md`、`inception/domain-design/components.md`、`inception/contract-design/contract-summary.md`（C6・C7・C9）（どれも `aidlc/spaces/default/intents/260925-user-management/` の下）
- 既にある仕組み（正とする）: `Dockerfile`・`compose.yaml`・`backend/src/main/resources/application.yaml`（CSP）・`backend/src/main/java/cherry/mastersmith/common/web/CacheControlFilter.java`・`frontend/vite.config.ts`・`frontend/scripts/check-bundle-size.mjs`

## 1. Deployment

| Facet | Choice | Rationale |
|---|---|---|
| 実行の形 | 画面のビルド結果（`dist`）を同梱した既存の実行可能 WAR を、既存の `Dockerfile`・`compose.yaml` の `app` で動かす。U4 のためのコンテナ・ポート・ボリューム・健全性の確かめの変更は無い | U4 はブラウザの中で動く画面の部品だけを足す（要点 1）。成果物の形は `team.md` の Deployment（`dist` を同梱した WAR） |
| 設定 | U4 は環境変数・`.env` の項目を足さない。見た目の設定の2項目（`MASTERSMITH_APPEARANCE_BRAND_COLOR`・`MASTERSMITH_APPEARANCE_FONT_FAMILY`）は U8 の持ち物 | U4 は `GET /api/appearance`（契約 C7）の答えを当てるだけ（要点 1） |
| 配信とキャッシュ | 既存の `CacheControlFilter` を変えない。`/assets/**` は `public, max-age=31536000, immutable`、`index.html` と画面の URL は `no-cache`、`/api/**` は `no-store` | 名前にハッシュが付くファイルは長く保存し、入口の `index.html` は毎回確かめる既存の形で足りる（要点 2、`performance-design.md` の5節） |
| フォント | `@fontsource/noto-serif-jp` の `japanese-400`〜`700`・`latin-400`〜`700` の8つの CSS を、既存の Noto Sans JP の後に画面の入口で読み込む。ファイルは `dist/assets/` のハッシュ付きの名前になり、同じオリジンから配信する。`font-display: swap`、`<link rel="preload">` は足さない | 外部のフォントの配信元に頼らず CSP を変えないため。`serif` を描くときだけブラウザが読む（NFR6.4、要点 2・3） |
| CSP | `application.yaml` の CSP（`default-src 'self'`・`script-src 'self'`・`style-src 'self'`・`font-src 'self'`・`connect-src 'self'` ほか）に差分を作らない。`frontend/vite.config.ts` の `modulePreload.polyfill: false`・`assetsInlineLimit: 0` のまま | 外部への通信・埋め込みのスクリプトとスタイルを足さない（NFR9.4、要点 3） |
| ブラウザの保存 | localStorage の U4 の鍵 `mastersmith.display-settings` と make-you-chic-ui の写しの鍵（`design-system-theme`・`design-system-font-size`）だけ。値は `language`・`theme`・`fontSize` の3つの軸だけ | トークン・メールアドレス・氏名をブラウザに残さない（NFR2.2、要点 5） |
| 登録の完了からの受け渡し | メールアドレスをモジュールの変数1つだけで渡す。URL・ブラウザの保存・`history.state` に載せない | 履歴・リファラー・アクセスログに残さない（NFR2.1、要点 5） |
| 内部DB と移行 | 変更なし。Flyway の新しい版は足さず、バックアップの対象も無い | U4 はサーバーの状態を持たないため、スキーマの後方互換の論点が無い（要点 5） |
| 環境 | 開発者の PC 上のコンテナだけ（`http://localhost:8080`）。E2E は `./gradlew e2eTest` が PC の上で `java -jar` で WAR を起動する（見た目の設定なしで既定の blue・sans） | `project.md` の Deployment。E2E の WAR の設定は U8 の決定のとおり |
| IaC | 作らない | 配備先が決まるまでクラウドの基盤は作らない（`project.md` の Deployment） |
| 資源の大きさ | 資源の上限（CPU 4・メモリ 2g の既定）は変えない。配信物の大きさの上限は置かず、変更の前後の値を記録する（3節） | サーバーの負荷は画面を開くたびの `GET /api/appearance` 1件だけ増え、U8 の NFR6.1 で押さえる（NFR6.5、要点 4） |

## 2. Infrastructure Services

| Service | Role | Configuration | Notes |
|---|---|---|---|
| 静的なファイルの配信（Spring Boot の既存の配信と `CacheControlFilter`） | 画面のファイル（入口の JavaScript・CSS・フォント）の配信 | 変更なし。フォントのファイルは `/assets/` のハッシュ付きの名前で `immutable` | Noto Serif JP の分だけ `dist` と WAR が大きくなる（3節） |
| `GET /api/appearance`（U8） | 見た目の設定の読み取り（認証なし） | U4 は画面を開くたびに描画の外で1回だけ呼ぶ。待ちの上限・再試行・中断は置かない | U8 は I/O を持たず、p95 300 ミリ秒（U8 の NFR6.1）。失敗は既定の値で描く（W3） |
| トークンの更新（既存の認証の API） | セッションの復元 | 変更なし。既存の `LoginStateGate` が最初の描画の副作用で始める | 見た目の設定の読み取りと並べて送る（NFR6.2） |
| ApiClient（`frontend/src/shared/api-client/apiClient.ts`） | 要求の共通の口 | トークンを付けないパスを1つの一覧と完全な一致の判定にまとめる（認証の API の3つと公開の API の3つ）。`Accept-Language` は `ja`・`en` だけを付ける | NFR9.1・NFR9.2、NFR 設計の Q3: A |
| ブラウザの localStorage | 表示の設定の保存 | 1節のとおり3つの軸だけ。読み書きは `frontend/src/app/display-settings/` の1つのモジュールだけ。例外は外へ出さない | サーバー側の保存は U2 の利用者の設定 |

## 3. 配信物の大きさ

上限は置きません（NFR6.5）。コード生成で、U4 の変更の前後の次の値を `du`・`stat` か Node の1回だけのコマンドで測り、記録します。測る道具は足しません。

| 値 | 測り方 | 今の値 |
|---|---|---|
| フォントのファイルの合計 | `dist/assets/` の `.woff2`・`.woff` を名前の `noto-sans-jp`・`noto-serif-jp` ごとに足す | Noto Sans JP の分が約 9.8MB |
| 入口の CSS | `dist/.vite/manifest.json` の入口の `css` のファイル（圧縮前と gzip） | 記録する |
| `dist` の全体 | `dist/` の下のファイルの合計 | 記録する |
| WAR | `backend/build/libs/mastersmith.war` | 約 87MB |
| 初回の JavaScript | 既存の `frontendBundleSize`（`check-bundle-size.mjs`、gzip、500KB を超えたら警告だけ） | 圧縮前で約 370KB |

コンテナのイメージも WAR と同じだけ大きくなります。資源の上限は変えません。axe-core は検査の側だけで読み込み、画面の成果物に入りません（`cicd-pipeline.md` の3節）。

## 4. Shared Infrastructure

| Shared Resource | Owner Unit | Consumer Units | Access Boundary |
|---|---|---|---|
| `GET /api/appearance` | U8 | U4 | 認証なしの読み取りだけ。U4 は応答の `brandColor`・`fontFamily` を項目ごとに許される値と比べてから当てる（NFR9.3） |
| 表示の設定の口 `useDisplaySettings`・`DisplaySettingsProvider` | U4 | U5・U6・U7 | 契約 C9。画面の言語などを読む口。ブラウザの保存の読み書きは U4 のモジュールの中だけ |
| 実際のブラウザの検査の手伝い `frontend/e2e/support/` | U4（B4 で作る） | U5・U6・U7（B5） | 組の切り替え・axe-core の読み込みと実行・横のはみ出しの判定・CSP の違反の集め。U5 などはログインの関数と見本を足す。既存の 010〜040 のファイルは変えない |
| ApiClient のトークンを付けないパスの一覧 | U4 | U3（公開の API）・U6（登録の完了の画面） | 一覧と判定の1か所だけで決める。前方一致を使わない |
| localStorage（make-you-chic-ui の鍵と同じ置き場） | U4（U4 の鍵）・make-you-chic-ui（写しの鍵） | 同じタブの画面 | `design-system-brand`・`design-system-font-family` は U4 から書かない |

## 5. セキュリティと秘密情報

| 項目 | 扱い |
|---|---|
| 新しい秘密情報 | 無い。U4 は秘密情報・鍵ファイル・設定を持たない（要点 7） |
| ブラウザに残る値 | 3つの表示の設定だけ（1節）。共用の PC でも前の利用者の個人に関する値が残らない |
| `console` | トークン・メールアドレス・氏名を出さない |
| テストのデータ | 実在の個人に関する値を使わない。メールアドレスは `example.com`（公開のリポジトリのため） |
| CSP とヘッダー | 変えない（1節）。検査のために CSP を緩めない（`bypassCSP` を使わない） |
| 依存 | `@fontsource/noto-serif-jp`（OFL-1.1）・`axe-core`（MPL-2.0、検査だけ）。扱いは `cicd-pipeline.md` の3節 |
| 検査の結果のファイル | `frontend/test-results/`・`frontend/playwright-report/` は管理外で、コミット・共有しない（`cicd-pipeline.md` の5節） |

## 6. 上流との差

承認済みの文書は書き換えません（`aidlc/spaces/default/memory/project.md` の Way of Working）。

| 対象 | 承認済みの形 | この段の設計 | 理由 |
|---|---|---|---|
| 承認済みの設計の文書 | — | 食い違う点は無い | U4 に関わる承認の場の決定は U4 R-01・R-02 だけで、どちらもコード生成の計画で拾う（`cicd-pipeline.md` の4節） |
| 配信物の大きさの記録 | NFR6.5 は「変更の前後のフォントの合計と WAR を記録する」 | 入口の CSS・`dist` の全体も記録する（`performance-design.md` の6節のとおり）。コンテナのイメージが同じだけ大きくなることを明記した | 上流に書かれていない具体を既存の設計で埋めた追加 |
