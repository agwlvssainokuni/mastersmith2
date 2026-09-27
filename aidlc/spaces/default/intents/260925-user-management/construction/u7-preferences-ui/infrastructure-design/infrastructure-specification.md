# Infrastructure Specification — U7 プリファレンスとパスワードの変更の画面（u7-preferences-ui）

U7 の基盤の設計です。U7 は画面（ui）の単位で、次の2つの画面を持ちます。

- プリファレンスの画面（S4）: ログインした利用者が、氏名・言語・テーマ・文字の大きさを変えて保存する
- パスワードの変更の画面（S5）: ログインした利用者が、自分のパスワードを変える

U7 は自分の API・内部DB の表・サーバーの状態を持ちません。U2 の3本の API（`GET /api/me/preferences`・`PUT /api/me/preferences`・`POST /api/me/password`）を呼ぶだけです。配備先は開発者の PC 上のコンテナだけで、クラウドの基盤は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。

- 答え: `infrastructure-design-questions.md`（要点 1〜6・20・21、質問なし、Consolidated Summary Confirmation: Looks correct）
- 上流: `construction/u7-preferences-ui/nfr-design/`（`performance-design.md` の2節・5節、`security-design.md` の1節・2節・6節・7節、`logical-components.md` の4節・5.1）。同じ段の `construction/u2-user-preferences/infrastructure-design/`・`construction/u4-display-foundation/infrastructure-design/`（どれも `aidlc/spaces/default/intents/260925-user-management/` の下）
- 既にある仕組み（正とする）: `Dockerfile`・`compose.yaml`・`backend/src/main/resources/application.yaml`（CSP）・`backend/src/main/java/cherry/mastersmith/common/web/CacheControlFilter.java`・`backend/src/main/java/cherry/mastersmith/config/WebConfig.java`（`SpaFallbackResourceResolver`）・`frontend/vite.config.ts`・`frontend/scripts/check-bundle-size.mjs`

## 1. Deployment

| Facet | Choice | Rationale |
|---|---|---|
| Compute model | 既存の実行可能 WAR（画面のビルド結果 `dist` を同梱）を、既存の `Dockerfile` のイメージにして `compose.yaml` の `app` で動かす。U7 のためのコンテナ・プロセスは足さない | 配備先は開発者の PC 上のコンテナだけ（`team.md` の Deployment）。U7 は画面だけの単位（要点 1） |
| Networking topology | 既存のまま。`app` の 8080 は PC の `127.0.0.1:8080` だけに公開し、画面と API は同じオリジン。U7 の画面が呼ぶのは同じオリジンの U2 の3本の API だけで、外への通信を足さない | CORS の設定を置かない決まり（`team.md` の Code Style）。CSP の `connect-src 'self'` のまま（要点 2） |
| Storage strategy | サーバーの側の保存を持たない。内部DB・Flyway の移行ファイルの変更は無い。利用者の設定の保存は U2 の持ち物 | 要点 4。V7 の後方互換は U2 の基盤の設計で扱う |
| ブラウザの側の保存 | U7 はブラウザの保存（localStorage・sessionStorage・Cookie）・URL・`history.state` に触れない。氏名とパスワードの値はフックの状態（メモリ）だけに持つ。ブラウザの保存は U4 の `applyUserPreferences`（C9）が3つの表示の設定（言語・テーマ・文字の大きさ）だけを書く | NFR2.1・NFR9.1、NFR 設計 `security-design.md` の1節・2節（要点 6） |
| 配信とキャッシュ | 2つの画面は `React.lazy` の遅延読み込みの塊として `dist/assets/` の名前にハッシュが付くファイルになり、既存の `CacheControlFilter` のまま配る（`/assets/**` は `public, max-age=31536000, immutable`、画面の URL と `index.html` は `no-cache`、`/api/**` は `no-store`）。2つの画面の URL の直接の表示と再読み込みは、既存の `SpaFallbackResourceResolver` が `index.html` を返す | 設定の変更なしで新しい画面の URL が動く（要点 2）。`WebConfig.java` で確かめた |
| CSP | `application.yaml` の CSP（`default-src 'self'`・`script-src 'self'`・`style-src 'self'`・`connect-src 'self'` ほか）と `frontend/vite.config.ts` を変えない。埋め込みのスクリプト・スタイル、外部の配信元を足さない | NFR 設計 `security-design.md` の6節、U4 の基盤の設計と同じ（要点 2） |
| Environments | 配備した環境（`compose.yaml`、プロジェクト `mastersmith`）1つ。E2E は WAR を PC の上で直接起動する（`frontend/playwright.config.ts`、実行ごとの一時の内部DB）。U7 は使い捨ての負荷の試験の環境（`docker/perf/compose.yaml`）に場面を足さない | 配備先が決まるまで検証環境・本番環境は無い（`team.md` の Deployment）。API の時間は U2 の目標と k6 で押さえ、U7 では測らない（NFR6.4） |
| IaC approach | 基盤の定義は既存のファイルのまま。U7 で変えるファイルは無い（`compose.yaml`・`Dockerfile`・`.env.example`・`application.yaml`・`playwright.config.ts` を変えない） | 配備先が決まるまでクラウドの IaC は作らない（`project.md` の Deployment） |
| Resource sizing | 既存のまま（`app` の上限は CPU 4・メモリ 2g の既定、接続プールの上限 30）。サーバーの負荷は、プリファレンスの画面を開くたびの GET 1件と、操作ごとの PUT・POST 1件だけ | NFR 設計 `logical-components.md` の4節。U2 の見積もりの中に入る |
| Configuration and secrets | U7 は新しい設定の項目・秘密情報・鍵ファイルを持たない | 要点 1・6。`project.md` の Forbidden（秘密を設定ファイルに書かない、`.env` をコミットしない） |

## 2. Infrastructure Services

| Service | Role | Configuration | Notes |
|---|---|---|---|
| U2 の3本の API（`GET`・`PUT /api/me/preferences`、`POST /api/me/password`） | 画面が呼ぶ API（同じオリジン、ログインが要る） | U2 の持ち物。U7 は既存の ApiClient（`apiRequest`）で呼び、独自の時間切れ・再送・中断を置かない | API の時間は U2 の目標（取得・保存 p95 1 秒、パスワードの変更の成功 p95 2 秒・誤り p95 1 秒）で押さえる（NFR6.4）。401 は既存の ApiClient の更新と送り直しに任せる（NFR9.3） |
| U4 の表示の設定の口（`applyUserPreferences`、C9） | 画面の中の部品（ブラウザの中） | U4 の持ち物。U7 は読み込んだ値・保存した値を渡すだけ | ブラウザの保存に書くのは U4 だけ（1節） |
| U6 の共用の確かめの関数（`frontend/src/shared/validation/`） | 画面の中の部品 | U6 の持ち物。B5 で U6 が先に作る | 関数の名前と理由の union の突き合わせは B5 のコード生成の計画の承認の前（NFR 設計の R-02・承認の場の U7 R-01） |
| 既存の配信（`CacheControlFilter`・`SpaFallbackResourceResolver`） | 画面の配信 | 変えない（1節） | 新しい画面の URL も既存の転送で `index.html` が返る |
| U1 の手元の受け手（Mailpit、compose の profile `mail`） | E2E の測りで使うメールの受け手 | U1 の持ち物（`127.0.0.1:1025`・`127.0.0.1:8025`、ボリュームなし、メモリの上限 `256m`）。U7 は使うだけ | 測りの利用者を招待から作るときだけ使う（`cicd-pipeline.md` の3.3）。配備した環境の U7 の画面はメールを送らない |

- データベース・キャッシュ・キュー・検索・CDN・DNS・ロードバランサーは U7 では使いません。

## 3. 配信物の大きさ

| 対象 | 扱い | 出典 |
|---|---|---|
| 初回の JavaScript（入口の塊、gzip） | 既存の `check-bundle-size.mjs`（`./gradlew verify` の中の `frontendBundleSize`、500KB を超えたら警告だけ）で、U7 の変更の前後の値をコード生成で記録する。上限は変えない | NFR6.6、要点 3 |
| 骨組みの変更（`frontend/src/app/registry/types.ts`・`validateRegistrations.ts`・`frontend/src/app/layout/ShellLayout.tsx`） | 入口の塊に入るが、型と判定の数行の追加だけ | NFR 設計 `performance-design.md` の5節 |
| 2つの画面の塊 | 遅延読み込みで、入口の塊に入らない。`features/preferences` を `frontend/src/app/` やほかの機能から静的に読み込まない | NFR6.6 |

## 4. Shared Infrastructure

| Shared Resource | Owner Unit | Consumer Units | Access Boundary |
|---|---|---|---|
| U2 の `/api/me/` の3本の API | U2 | U7 | ログインが要る。本人の行だけを読み書きする（U2 が決める）。U7 は ApiClient だけから呼ぶ |
| 表示の設定の口 `frontend/src/app/display-settings/`（C9） | U4 | U6・U7 | U7 は `applyUserPreferences` と見せ方の口を呼ぶだけで、ブラウザの保存に直接触れない |
| 共用の確かめの関数 `frontend/src/shared/validation/` | U6 | U6・U7 | 理由の union を返し文言を持たない。U7 は差し替えずに使う |
| 骨組み（`types.ts`・`validateRegistrations.ts`・`ShellLayout.tsx`） | 既存（U7 が `path` の項目を足す。U4 が B4 で `ShellLayout` のユーザーメニューの名前を変えた後に足す） | 全機能 | `path` は登録済みの画面の URL の完全な一致だけを許し、外の URL へ移る道を作らない（NFR9.4） |
| 共用の検査の手伝い `frontend/e2e/support/` | U4（B4 で作る） | U5・U6・U7 | 検査のブラウザのコンテキストの中だけで効く。B5 で足す関数（ログイン・差し替えの答えの組み立て・招待から利用者を作る関数）の形と置き場は B5 のコード生成の計画で U5・U6 とそろえる。利用者を作る関数は U6 の E2E-1 と同じもの（要点 5） |
| 手元の受け手（Mailpit、profile `mail`） | U1 | U3・U5・U6・U7（E2E）と開発者 | `127.0.0.1` だけに公開。E2E は実行ごとに違う `example.com` の宛先で探し、メールを消さず Mailpit の API に書き込まない（U6 の段の決定、U5 も同じ） |

## 5. セキュリティと秘密情報

| 項目 | 扱い | 出典 |
|---|---|---|
| 氏名・パスワードの値 | フックの状態（メモリ）だけに持ち、ブラウザの保存・URL・`console` に出さない。3つのパスワードの項目は `type="password"` と `autocomplete`、フォームに `method`・`action` を置かず ApiClient で送る | NFR2.1・NFR9.1 |
| 応答の値 | 失敗の文言は `detail` を使わず `code` と `fieldErrors` の `field`・`reason` から鍵を選ぶ。応答の値は React の文字としてだけ描く（`react/no-danger` のリンタのまま） | NFR9.2 |
| 骨組みの `path` | 外の URL（`http:` などの絶対 URL・`//` で始まる URL）へ移る道を作らない。登録の検査で起動を止める | NFR9.4 |
| 新しい秘密情報 | 持たない。検査と測りで使うパスワードは実行ごとに作る仮の値で、リポジトリ・`.env` に置かない（`cicd-pipeline.md` の4節） | 要点 6・15 |
| 依存 | 新しい依存を足さない | NFR9.10 |

## 6. 配備と戻しでの扱い

| 項目 | 扱い | 出典 |
|---|---|---|
| 配備 | 既存の流れ（WAR を作りイメージを作り直して `docker compose up -d`、ヘルスチェックとスモークテスト）のまま（`cicd-pipeline.md` の5節） | 要点 20 |
| 配備の後の確かめでの保存・パスワードの変更 | 行うと配備した内部DB の設定と監査ログに残るため、行うかと、行うときに先に依頼者に伝えることは配備の段で決める。パスワードが要る操作は依頼者が行う | `project.md` の Corrections、要点 20 |
| 戻し | イメージだけを直前の版に戻す既存の決まりのまま。U7 はサーバーの状態を変えないため、戻しで消すものは無い | 要点 20 |

## 7. 上流との差

承認済みの文書は書き換えません（`aidlc/spaces/default/memory/project.md` の Way of Working）。

| ID | 対象 | 承認済みの形 | この段の設計 | 理由 |
|---|---|---|---|---|
| I-D1 | 新しい画面の URL の配信 | 上流に記述なし | 既存の `SpaFallbackResourceResolver` が `index.html` を返すため、配信の設定を変えない（1節） | コードで確かめた既存の仕組みで埋めた追加。食い違いではない |
| I-D2 | 配備の後の確かめ | 上流に記述なし | 保存・パスワードの変更を配備した環境で行うかは配備の段で決める（6節） | 監査ログに残る操作の扱い（`project.md` の Corrections）を既存の決まりで埋めた追加 |
| I-D3 | Mailpit の中身の扱い | U1・U3 の決まり（見終えたら止めて消す） | E2E はメールを消さず Mailpit の API に書き込まず、開発者が `e2eTest` の後に止めて消す（4節、`cicd-pipeline.md` の 3.4） | U6 の段の決定（U5 も同じ）にそろえた。要点 17 と合う |
