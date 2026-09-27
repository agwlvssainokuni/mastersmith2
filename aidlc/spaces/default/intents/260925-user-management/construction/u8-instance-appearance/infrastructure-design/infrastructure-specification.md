# Infrastructure Specification — U8 インスタンスの見た目の設定（u8-instance-appearance）

U8（設定からブランドカラーとフォントファミリーを起動時に1回だけ読み、ログインなしの `GET /api/appearance`（契約 C7）で返す service の単位）の基盤の設計です。答えは `infrastructure-design-questions.md`（質問なし、Consolidated Summary Confirmation: Looks correct）。配備先は開発者の PC 上のコンテナだけで、クラウドの基盤（IaC・検証環境・警報の通知の先）は作らない（`aidlc/spaces/default/memory/project.md` の Deployment）。既存の仕組みを正として、U8 で足すものだけを書く。

出典の略号: NFR はこの単位の NFR 要件 `aidlc/spaces/default/intents/260925-user-management/construction/u8-instance-appearance/nfr-requirements/` の枝番、BR は同じ単位の `functional-design/rules.md`、「要点 n」は `infrastructure-design-questions.md` の「Infrastructure Design の要点（案）」の番号、設計の各節は同じ単位の `nfr-design/` の文書。

## 1. Deployment

| Facet | Choice | Rationale |
|---|---|---|
| 計算の形 | 既存の実行可能 WAR を既存の `Dockerfile`・`compose.yaml` の `app` のコンテナで動かす。U8 のためのコンテナは足さない | U8 はアプリの中の新しいパッケージ `cherry.mastersmith.appearance`（`config`・`service`・`web`）だけ（要点 1、`logical-components.md` の1節） |
| インスタンスの数 | 1つ | 内部DB が組み込みの H2 のため。U8 は複数のインスタンスへの配り直しを持たず、増やすときは各インスタンスが同じ環境変数を読めば同じ値を返す（NFR6.4、`scalability-design.md` の1節） |
| 網の構成 | 既存どおり PC の `127.0.0.1:8080` だけに結び付ける。新しいポート・受け口は無い | 要点 6。公開は既存の受け口の中の道1つだけ |
| 公開の範囲 | 差し込み口（order 410、appearance は 400 台）で「GET・`/api/appearance`」だけを認証なしにする決まりを1つ足す。ほかのメソッド・道は `/api/**` の既定（ログインが必要）のまま | NFR4.1〜NFR4.3・NFR4.7、`security-design.md` の2節 |
| 保存 | 無し。内部DB の表・Flyway の移行・ファイルを持たず、ボリューム `mastersmith-data` の使い方も変わらない | NFR5.1、要点 5 |
| 設定 | `application.yaml` に `mastersmith.appearance.brand-color: ${MASTERSMITH_APPEARANCE_BRAND_COLOR:}`・`mastersmith.appearance.font-family: ${MASTERSMITH_APPEARANCE_FONT_FAMILY:}` を空を既定にして足す。`.env.example` に2項目を値を空にして、許される値（blue・green・purple・orange と sans・serif）と既定（blue・sans）をコメントで添えて足す。README の設定の一覧に同じ内容と「変更は起動し直しで当たる」を足す | functional-spec.md の2節、NFR6.4、要点 2 |
| compose の変更 | 無し | `app` はすでに `.env` を `env_file` で読むため、環境変数を `environment` に足す必要が無い（前の Intent の前例） |
| 環境 | 開発者の PC 上のコンテナ（配備）・使い捨ての負荷の試験の環境（`docker/perf/compose.yaml`）・テスト（`./gradlew verify` の中の Spring の文脈）。どれも設定が無ければ既定（blue・sans）で動く | 環境の差は設定の値だけで、形は同じ |
| 資源の大きさ | 既存の上限（CPU 4・メモリ 2g の既定、`.env` で変更）のまま。U8 が足すのは起動時に作る変わらない `record` 1つだけ | NFR6.2・NFR6.3、`performance-design.md` の4節 |
| 健全性 | 既存の `/actuator/health` と compose の健全性の確かめのまま。HealthIndicator は足さない | 起動の後に失敗しうる依存が無い（NFR9.6、`reliability-design.md` の1節） |
| 起動の失敗 | 設定の値で起動は止まらない。設定の型は文字列で受け `@Validated` を付けず、許されない値は既定に置き換え、項目ごとに WARN を1件出す。空・空白だけは警告なしで既定 | NFR9.1・NFR9.4、BR1.3・BR1.4・BR1.7、要点 4 |
| IaC | 採らない。基盤はリポジトリの `compose.yaml`・`Dockerfile`・`.env.example`・`application.yaml` で表す | 配備先が決まるまでクラウドの基盤を作らない（`project.md` の Deployment） |

## 2. Infrastructure Services

| Service | Role | Configuration | Notes |
|---|---|---|---|
| アプリ（組み込みのサーブレットのコンテナ） | 受け口 | 既存の設定のまま。U8 は `GET /api/appearance` を1本足す | 要求のスレッドをほかの API と共有する。回数の制限は置かない（NFR4.6） |
| フィルターの連鎖（`config/SecurityConfig` と差し込み口） | 認証・認可 | 差し込み口を1つ（order 410）足す。トークンの検証・401・403 の処理・ヘッダー・セッションは変えない | order の重なりは既存の `SecurityExtensionValidator` で起動が止まる。割り当ては機能の名前で 100 台ずつ（`security-design.md` の2.3節） |
| 共通のヘッダー・キャッシュの見出し（`CacheControlFilter` など） | 応答のヘッダー | 変えない。未認証の GET の応答にも CSP（`font-src 'self'` を含む）・`X-Content-Type-Options: nosniff`・`X-Frame-Options: DENY`・`Referrer-Policy: same-origin`・`Cache-Control: no-store` が付く | NFR4.8。サーバー側のキャッシュも置かない（`performance-design.md` の3節） |
| 共通のエラー応答（`GlobalExceptionHandler`） | エラー応答 | 変えない。GET 以外は 401 / `AUTHENTICATION_REQUIRED` か 405 / `METHOD_NOT_ALLOWED`、想定外は 500 / `INTERNAL_ERROR` | U8 は新しい `code`・例外・`XxxProblemTypeCatalog` を足さない（NFR4.2・NFR9.2） |
| 内部DB（組み込みの H2）と接続プール（HikariCP、既定の上限 30） | データベース | 使わない | プールが尽きたとき・詰め直しの一時停止のあいだも U8 は影響を受けない。境界テストと結合テストで確かめる（NFR5.1・NFR5.2） |
| Micrometer（指標・トレース） | 観測 | 変えない。既存の HTTP の指標とスパンに乗る | 独自の指標・スパンは足さない（NFR6.5・NFR6.6、`monitoring-design.md`） |
| アプリのログ（SLF4J・構造化ログ） | ログ | 変えない。起動時の WARN をキー・値の API で出す | 設定された値は出さない（NFR9.4） |
| 監査ログ | 監査 | 使わない | 読み取りは監査の出来事を出さない（NFR9.5） |

キャッシュ・待ち行列・検索・CDN・DNS・負荷分散は U8 では使わない。

## 3. Shared Infrastructure

| Shared Resource | Owner Unit | Consumer Units | Access Boundary |
|---|---|---|---|
| フィルターの連鎖と差し込み口の order の割り当て | 既存（`config/SecurityConfig`・`common/security/SecurityRuleContributor`） | u3-invitation（310）・U8（410） | 各単位は自分の道とメソッドだけの決まりを1つずつ足す。道は重ならない。値の割り当ては機能の名前で 100 台ずつ、x00・x50 はテストの決まりが使う |
| `GET /api/appearance`（契約 C7） | U8 | u4-display-foundation（AppFrame、トークンを付けずに呼ぶ） | GET だけ公開、応答は `brandColor`・`fontFamily` の2項目だけ |
| 要求のスレッド・共通のエラー応答・共通のヘッダー | 既存 | U8 を含む全機能 | U8 は設定を変えずに使う |

## 4. セキュリティと秘密情報

| 観点 | 設計 | 出典 |
|---|---|---|
| 秘密情報 | U8 の2項目は秘密ではない。新しい秘密情報・鍵ファイルは無い。`.env` はコミットしない決まりのまま、`.env.example` の値は空 | `project.md` の Forbidden、要点 3 |
| 個人に関する値 | 扱わない | 要点 3 |
| 設定された文字列の漏えい | 応答は業務処理が持つ列挙の値から写した2項目だけで、元の文字列は届かない。警告のログは項目の名前・既定・許される値だけ | NFR4.5・NFR9.4 |
| 書き込みの口 | 持たない。値は起動し直しでだけ変わる | NFR4.2、BR3.1 |
| 濫用 | 回数の制限を置かない。処理はメモリの値を読むだけで内部DB を使わない。配備先が決まったときに前段（逆プロキシなど）の制限で扱う | NFR4.6 |

## 5. 上流との差

承認済みの文書は書き換えない（`project.md` の Way of Working）。承認済みの文書と食い違う点は無い。承認済みの設計に具体が書かれていない点を、既存の前例で次のとおり埋めた（追加）。

| 項目 | 承認済みの記述 | この段での扱い |
|---|---|---|
| `compose.yaml` | functional-spec.md の2節は `application.yaml`・`.env.example`・README に足すとだけ書く | `app` が `.env` を `env_file` で読むため、`compose.yaml` は変えないと明記した |
| 戻すときの `.env` | `reliability-design.md` の6節は戻しを直前の版の成果物での再配備とだけ書く | 直前の版は2項目を使わず害が無いため、イメージだけを戻し `.env` の2項目は消さなくてよいとした（`cicd-pipeline.md` の5節） |
| k6 の閾値の置き方 | `performance-design.md` の5節は場面を1本足して p95 を出すとだけ書く | 既存の前例（`dslLight`）どおり `thresholdsFor` に閾値を置くとした（`cicd-pipeline.md` の3節） |
