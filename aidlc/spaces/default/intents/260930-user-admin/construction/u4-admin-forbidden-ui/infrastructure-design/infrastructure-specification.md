# Infrastructure Specification — U4 管理の画面の 403 の共通の扱い（u4-admin-forbidden-ui）

U4 の基盤の設計です。U4 は画面（ui）の単位で、管理の API の 403（`ACCESS_DENIED`）を受けた画面を S6（権限が無いときの表示）に置き換え、ログインの状態を1回読み直す、画面の側だけの仕組みです。自分の API・内部DB の表・サーバーの状態・設定を持たず、`backend/` に手を入れません。画面のビルド結果（`dist`）として実行可能 WAR に同梱され、既存のコンテナで配信されます。配備先は開発者の PC 上のコンテナだけで、クラウドの基盤（IaC・検証環境・警報の通知の先）は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。

この文書は新しい設計ではなく、承認済みの NFR 要件・NFR 設計と既にある仕組みの記録です（`project.md` の Deployment「既に実装されている段は既にあるものの記録として書く」）。

- 答え: `infrastructure-design-questions.md`（質問 0 問、要点 1〜7、Consolidated Summary Confirmation: Looks correct）
- 上流: `construction/u4-admin-forbidden-ui/nfr-design/`（`performance-design.md` 5節・7節、`security-design.md` 5節・6節・8節・9節と「承認の場の決定」、`logical-components.md` 6節〜8節）、`construction/u4-admin-forbidden-ui/nfr-requirements/`（`security-requirements.md`・`performance-requirements.md`・`tech-stack-decisions.md`）、`construction/u4-admin-forbidden-ui/functional-design/functional-spec.md`・`frontend-components.md`、`inception/domain-design/components.md`、`inception/contract-design/contract-summary.md`（C4）、`inception/delivery-planning/bolt-plan.md`（B2）（どれも `aidlc/spaces/default/intents/260930-user-admin/` の下）
- 既にある仕組み（正とする、読むだけ）: `Dockerfile`・`compose.yaml`・`backend/src/main/resources/application.yaml`（CSP）・`backend/src/main/java/cherry/mastersmith/common/web/CacheControlFilter.java`・`backend/src/main/java/cherry/mastersmith/access/web/AdminAccessDeniedHandler.java`・`frontend/playwright.config.ts`・`frontend/scripts/check-bundle-size.mjs`

## 1. Deployment

| Facet | Choice | Rationale |
|---|---|---|
| 実行の形 | 画面のビルド結果（`dist`）を同梱した既存の実行可能 WAR を、既存の `Dockerfile`・`compose.yaml` の `app` で動かす。U4 のためのコンテナ・ポート・ボリューム・資源の上限・ヘルスチェックの変更は無い | U4 はブラウザの中で動く部品と関数だけを足す（要点 1、`logical-components.md` 8節）。成果物の形は `team.md` の Deployment（`dist` を同梱した WAR） |
| 設定 | 環境変数・`.env` の項目・`application.yaml` を足さない・変えない | U4 は設定を読まない。403 の判定（`isAdminForbidden`）はパス・状態・code だけで決まる（NFR1.2） |
| 配信とキャッシュ | 既存の `CacheControlFilter` を変えない。U4 の部品は骨組みの一部として初回の読み込みの塊に入り、`dist/assets/` のハッシュ付きの名前で `public, max-age=31536000, immutable`。`index.html` と画面の URL は `no-cache`、管理の API（`/api/**`）は `no-store` | 骨組みの部品のため遅れて読む分け方（`lazy`）を足さない（`performance-design.md` 5節。足すと S6 を描くまでに読み込みの待ちが入る） |
| CSP | `application.yaml` の `content-security-policy` に差分を作らない。130 のためにも緩めない。外部への通信・外部の資源・埋め込みのスクリプトとスタイルを足さない。スタイルは部品と同じ場所の素の CSS ファイル | NFR9.5、`security-design.md` 6節 |
| ブラウザの保存と URL | 権限が無い URL は状態に1つだけ持ち、URL が変わったら捨てる。ブラウザの保存に書くのは自分の言語の反映の言語だけで、氏名を保存しない。トークンと管理者の印は既存どおりメモリだけ | NFR3.1・NFR1.5、`performance-design.md` 6節 |
| 内部DB と移行 | 変更なし。Flyway の新しい版は足さず、バックアップの対象も無い | U4 はサーバーの状態を持たない（要点 1） |
| 環境 | 開発者の PC 上のコンテナだけ（`http://localhost:8080`）。E2E は `./gradlew e2eTest` が PC の上で `java -jar` で WAR を一時の内部DB（番号 18081）で起動する（既存の `frontend/playwright.config.ts`） | `project.md` の Deployment。E2E の WAR の起動の形は変えない |
| IaC | 作らない | 配備先が決まるまでクラウドの基盤は作らない（`project.md` の Deployment） |
| 資源の大きさ | 資源の上限（CPU・メモリの既定）とヘルスチェックは変えない。初回の JavaScript は既存の目安（gzip で 500KB、超えたら警告だけ）のまま見張り、変更の前後の値を記録する（3節） | U4 が増やすのは小さな部品・関数・文言の鍵だけで、数 KB（gzip）までの見込み（`performance-design.md` 5節）。見込みは確かめの代わりにしない |

## 2. Infrastructure Services

| Service | Role | Configuration | Notes |
|---|---|---|---|
| 静的なファイルの配信（Spring Boot の既存の配信と `CacheControlFilter`） | 画面の塊（JavaScript・CSS）の配信 | 変更なし | 入口のファイルの中身が U4 の分だけ増える（NFR9.4） |
| 管理の API のサーバー側の認可（既存の `AdminAccessDeniedHandler`） | 管理者でない要求に 403・`ACCESS_DENIED` を返し、WARN のログ（`code` だけ）を出し、監査の「アクセスの拒否」の出来事を知らせる | 変更なし。U4 はこの応答を受けて表示を変えるだけ | 画面の表示はサーバーの判定の代わりにしない（NFR1.1）。401・403・200 のサーバー側のテストは U3 の持ち物 |
| トークンの更新の API（POST `/api/auth/session/refresh`、既存） | 403 の後のログインの状態の読み直し | 変更なし。U4 は `refreshSessionOnce` で ApiClient の既存の更新のまとめ（`pendingRefresh`）に乗るだけ。新しい要求の経路・API・送り直しを作らない | 時間の目標は既存の p95 1 秒（同時 10 件）のまま（NFR5.1）。停止の判定は U1 の持ち物。U4 が増やす要求は、印を外された利用者1人につき 403 を受けた URL ごとに1回 |
| ApiClient（`frontend/src/shared/api-client/`） | 要求の共通の口 | `isAdminForbidden`（`adminForbidden.ts`）と `refreshSessionOnce` を足す。アクセストークンの付与・401 での更新・`Accept-Language` は変えない | 401 とログアウトの扱いは変えない（NFR1.4） |
| make-you-chic-ui（`vendor/make-you-chic-ui`） | S6 の `Alert`（info、`role="status"`） | 今の固定先のまま。中身も固定先も変えない | NFR9.7、`project.md` の Forbidden |

## 3. 配信物の大きさ

| 項目 | 扱い |
|---|---|
| 上限 | 新しく置かない。既存の目安（gzip で 500KB、超えたら警告だけで統合は止めない）のまま（NFR9.4） |
| 見張り | 既存の `frontendBundleSize`（`frontend/scripts/check-bundle-size.mjs`、`./gradlew verify` の段 9） |
| 記録 | コード生成で、U4 の変更の前と後の初回の JavaScript の値（gzip）を測って記録する |
| 依存 | 実行時・開発時とも足さないため、`dist` と WAR の増えは U4 のコードと文言の分だけ（NFR9.6、`cicd-pipeline.md` 3節） |

## 4. Shared Infrastructure

複数の単位・既存の画面と共有するものです（`logical-components.md` 8節）。新しいサーバーの資源はありません。

| Shared Resource | Owner Unit | Consumer Units | Access Boundary |
|---|---|---|---|
| 403 の共通の扱いと自分の氏名・言語の反映の口（契約 C4。`useAdminForbidden`・`AdminForbiddenView`・`useApplyOwnProfile`・`isAdminForbidden`・`refreshSessionOnce`） | U4 | U5 と既存の管理の入口・DSL の管理・招待の管理の画面 | 画面の中の関数・部品の呼び出し。機能（`features/*`）は `src/app/` から読み、骨組みは `features/auth` を読まず、ApiClient は `src/app/` を読まない（NFR9.12） |
| ApiClient の更新のまとめ（`pendingRefresh`） | 既存（ApiClient） | 401 の更新と U4 の読み直し | 同じ約束を共有し、重なっても更新の API は1回（NFR9.2） |
| ログイン状態（`LoginStateGate`） | 既存（auth） | U4（読み直しの結果を今の知らせで受ける） | U4 は書き換える口を作らない。印はトークンの応答からだけ入る（NFR1.5） |
| 表示の設定のストア（`savedUser`・ブラウザの保存の言語） | 既存（display-settings） | U4 の `applyOwnProfileFor` | 書く値は今の `applyUserPreferencesFor` と同じ種類。氏名は保存しない（NFR3.1） |
| 骨組みの文言（`frontend/src/app/i18n/messages/ja.ts`・`en.ts`） | 既存（骨組み） | U4 が `adminForbidden.*` の鍵を3つ足す | ja・en をそろえる（NFR8.1） |
| E2E の共有の手伝い `frontend/e2e/support/loginPreferences.ts` | 既存（前の Intent） | 060 と U4 の 130 | 型 `LoginPreferences` に省略できる `displayName` を1つ足すだけ。渡さない呼び出しの動作は変えない。`support/pageProblems.ts`・`support/axe.ts` などほかの手伝いは変えない（`logical-components.md` 6.2・6.3） |
| E2E の WAR・一時の内部DB・初期管理者（`frontend/playwright.config.ts`） | 既存（E2E の設定） | 010〜100（B5 の後は 110・120 も）と 130 | 全ファイルで共有し、`workers: 1` で番号の順に1本ずつ動く。130 は状態を変える要求を送らず、初期管理者の状態を変えない（NFR3.2） |
| Mailpit（compose の profile `mail`、`127.0.0.1:1025`・`127.0.0.1:8025`） | 既存（前の Intent の U1） | E2E の全体 | 130 はメールを使わないが、`e2eTest` が始める前に届くかを確かめるため、実行の前に起動が要る。130 は Mailpit に書き込まない |

## 5. セキュリティと秘密情報（DevSecOps・Compliance の視点）

| 項目 | 扱い |
|---|---|
| 新しい秘密 | 足さない。秘密情報・鍵ファイル・設定・`.env` の項目を持たない |
| サーバーの判定 | 画面の S6 とメニューを隠すことは表示だけ。管理の API の 401・403・200 の判定と監査はサーバーの既存の動作のまま（NFR1.1、`project.md` の Mandated） |
| 画面に出す値 | S6 は見出し・決まった文言・「ホームへ戻る」だけ。応答の `detail`・原因・利用者の値を出さない。「ホームへ戻る」の URL はアプリの中の定数（NFR3.1・NFR9.5） |
| ブラウザのコンソールと保存 | U4 の部品と関数はトークン・メールアドレス・氏名をコンソールに出さない。ブラウザの保存は言語だけ（NFR3.1） |
| E2E の資格情報 | 初期管理者のメールアドレスとパスワードは既存どおりプロセスの環境変数で渡し、`webServer.env` に置かない（NFR3.2、`project.md` の Testing Posture の学び） |
| E2E の報告 | パスワード・アクセストークン・メールアドレス・2語の氏名の値を `test.step` の題・注記・添付・標準出力に入れない。json の報告の確かめは既存の `frontend/playwright-secret-check-reporter.ts` が受け持つ（`cicd-pipeline.md` 5節） |
| テストのデータ | 2語の氏名は ASCII の架空の値で、実在しそうな氏名にしない。見本の 403 の `detail` の目印は個人に関する値でも秘密でもない固定の文字（公開のリポジトリのため、`team.md` の Testing Posture） |
| 依存とサプライチェーン | 新しい依存を足さないため、ライセンスの確かめ・lockfile の更新・OSV-Scanner の対象の変化は無い（NFR9.6） |
| 監査 | U4 は監査の記録を書かない。管理の API の 403 は既存の `AdminAccessDeniedHandler` が「アクセスの拒否」の出来事として知らせる（`monitoring-design.md` 4節） |

## 6. 上流との差

承認済みの文書は書き換えず、差をここに記録します（`project.md` の Way of Working）。

| 対象 | 承認済みの形 | この段の設計 | 理由 |
|---|---|---|---|
| 配信のキャッシュの扱い | U4 の NFR 設計に記述は無い（`logical-components.md` 8節は「配信の仕組みは変わらない」） | 既存の `CacheControlFilter` の `/assets/**`・`/api/**` の扱いがそのまま当たることを明記した | 既存の仕組みで埋めた追加。食い違いではない |
| Mailpit の起動 | U4 の NFR 設計に記述は無い | 130 はメールを使わないが `e2eTest` の前に起動が要ることを書いた | 既存の `e2eTest` の確かめ（前の Intent の U1 の決定）の影響。食い違いではない |
| 承認済みの設計の文書 | — | 食い違う点は無い。NFR 設計の承認の場の申し送り R-01・R-02 はコード生成の計画で扱う（`cicd-pipeline.md` 4.3） | NFR Design の承認の場の決定のとおり |
