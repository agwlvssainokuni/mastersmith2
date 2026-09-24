# Code Summary — U4 DSL の管理（u4-dsl-management）— B4

承認済みの `code-generation-plan.md` の **B4（Must）の Step 1〜Step 14** を、test-after（層ごとに実装 → その層のテストを書いて実行 → 通ってから次の層）で行った結果を記録する。**B5（Should: 履歴からの戻し US5.1・適用中のダウンロード US5.2、Step 15〜17）はまだ行っていない。** B4 のコミットを依頼者が確かめた後の別の指示で進める。

## 1. 作った・変えたファイル

一覧の正本は `source-manifest.json`（87 件。アプリのソース・設定・文書・テスト）。

### 1.1 U4 の新しいファイル（本番）

| 置き場 | ファイル | 内容 |
|---|---|---|
| `dslmanage/domain` | `DslSource`・`DslPreviewRef`・`DslAppliedRef`・`DslContent`・`DslUserRef` | 出どころ、本文を持たない参照、本文と参照の組（本文を写して持ち、文字列化に本文を出さない）、操作した人 |
| 同上 | `DslPreviewRecord`・`DslAppliedRevisionRecord` | JPA のエンティティ（読み取りだけに使う。書き込みは1文の MERGE と1文の写し） |
| 同上 | `PreviewView` | プレビューの中身（要約・違い・照合の警告、区分と種類の列挙を入れ子に持つ） |
| 同上 | `DslOperationEvent`・`DslOperationType` | 契約 C7 の出来事 |
| 同上 | `DslProblemTypes`・`DslStatus`・`DslErrorItem`・`DslDownload` | code 9 つ（日英の説明）、今の状態と履歴の値、422 の誤り1件、ダウンロードとファイル名 |
| `dslmanage/repository` | `DslPreviewRepository`・`DslAppliedRevisionRepository` | 固定の鍵の1行への MERGE、previewId を指定した削除の件数、本文を取らない射影、プレビューの行の履歴への1文の写し、上限を超えた古い履歴の削除 |
| `dslmanage/service` | `DslLifecycle` | 生成・投入・表示・破棄・適用・今の状態・履歴・ダウンロード、結果の型から業務の例外への変換、確定の後の差し替えと出来事 |
| 同上 | `DslRecordStore` | トランザクションの境界（内部DB の短い読み書きだけを包む） |
| 同上 | `DslPreviewAnalysis`・`DslSummaryCalculator`・`DslDiffCalculator`・`DslReconciler`・`DslPreviewCache` | 要約・違い・照合、プレビューのモデルを同じ previewId の間だけ1つ持つ |
| 同上 | `DslErrorMessages`・`DslProblemTypeCatalog`・`DslOperationMetrics`・`DslOperation`・`DslOutcome`・`DslRequestContext`・`DslStartupLoader`・`DslManageProperties` | U2 の文言の鍵 32 個の日英の文言、code の登録、指標とログ、要求の文脈、起動時の読み込み、設定 2 つ |
| `dslmanage/web` | `DslAdminController`・`DslResponses`・`ApplyRequest`・`DslAdminPaths` | 契約 C6 の API（B4 の 8 本）と `record` の DTO |
| 同上 | `DslHeavyOperationGate`・`HeavyDslOperation`・`DslWebConfig` | 重い道の同時の数を1つにする仕組み（本文を読む前）、投入の道の本文の上限の決まり |
| 同上 | `DslSubmitRejectionListener`・`DslRequestContextResolver` | 大きさで断った投入の監査と指標、要求から操作した人・送り手・表示言語 |
| 各層 | `package-info.java`（4 つ） | 層の説明 |
| `db/migration` | `V5__u4_dsl_management.sql`・`V6__u4_dsl_audit_columns.sql` | プレビュー（固定の鍵 1、本文 `BINARY LARGE OBJECT`）と履歴（追加の順 `sequence_no`）、監査の表の NULL を許す列 4 つ |

### 1.2 既存の部分の変更

| ファイル | 変更 |
|---|---|
| `common/web/RequestSizeLimitFilter.java` | 道ごとの上限と code の表（`RequestBodyLimitRoute`）と、断ったことを知らせる口（`RequestSizeRejectionListener`・`RequestSizeRejection`、3 つとも新規）を足した。今までのコンストラクターも残した |
| `config/SecurityConfig.java` | 本文の上限の仕組みを `HeaderWriterFilter` の後から `AuthorizationFilter` の後へ移し（決定 A）、道ごとの決まりと受け取りを集めて渡す |
| `common/error/domain/BusinessException.java` | 応答の追加の項目（`getProperties()`、既定は空）を持てるようにした |
| `common/error/web/ErrorResponseFactory.java` | 追加の項目を載せる。既存の項目名（`type`・`title`・`status`・`detail`・`instance`・`code`・`traceId`）は上書きさせない |
| `common/error/web/GlobalExceptionHandler.java` | 業務の例外の追加の項目を載せる。業務の例外は状態コードが 5xx（503）でも WARN・スタックトレースなしにした（NFR5.3） |
| `audit/domain/AuditEventType.java`・`AuditEvent.java`・`AuditEventFactory.java` | 出来事の種類 `DSL_*` 5 つ、列 4 つ（V6）、`DslOperationEvent` からの写し取り |
| `audit/service/AuditEventListener.java` | `DslOperationEvent` を確定の後（トランザクションの外なら、その場で）に受けて記録する。書き込みの失敗の ERROR に DSL の項目を足した |
| `application.yaml`・`.env.example` | `mastersmith.dsl.max-submit-size`（`MASTERSMITH_DSL_MAX_SUBMIT_SIZE`、既定 10MB）・`mastersmith.dsl.history-limit`（`MASTERSMITH_DSL_HISTORY_LIMIT`、既定 20）。`.env.example` は既存の決まりのとおり既定値を使う項目をコメントの行で置いた |
| `backend/build.gradle.kts` | テストの JVM のヒープの上限を 1g にした（6節） |
| `docker/monitoring/dashboards/mastersmith-overview.json` | 行「DSL の操作」とパネル 3 つ |
| `README.md` | 環境変数 2 つ、節「DSL の管理の API（U4）」（API と code、本文の上限の置き場の変化、`DSL_BUSY`、適用・起動・保存、監査の出来事と列、指標とログ、ダッシュボードの行） |
| `audit/service/AuditSecretLeakIT.java`（テスト） | 監査の表の列の一覧の確かめに V6 の 4 列を足した（6節） |

U1・U2・U3 のコード（`targetdb`・`dsl`・`dslmanage.generate`）と `vendor/` は変えていない。新しい依存は足していない（`gradle.lockfile` は変わらない）。

## 2. 主な判断

| 判断 | 理由 |
|---|---|
| トランザクションの境界を `DslLifecycle` ではなく `DslRecordStore`（同じ service の層）に置いた | 生成（U3、対象DB の問い合わせ 20 秒×4 回まで）と照合を内部DB のトランザクションの中で行うと、内部DB の接続を長く持つため。`DslLifecycle` は確定して戻った後にだけ差し替えと出来事を行う（BR4.6）。巻き戻ると例外が戻るため、どちらもしない |
| 出来事はトランザクションの外で出す | 既存の受け手（`fallbackExecution = true`）がその場で記録し、確定の後に記録する決まり（BR7.3）を満たす。1要求で内部DB の接続を2本同時に持たない |
| 適用はプレビューの行を1文（`INSERT ... SELECT`）で履歴へ写す | 本文（最大 10MB）を読み直さない（NFR 設計 performance-design.md 3節）。写した件数 0・消した件数 0 のどちらも 409 で巻き戻す |
| 同時の適用で行の削除が競って DB の例外（`ConcurrencyFailureException`）になった場合も 409 にした | H2 の働きによらず「負けた方は 409」になるように。結合テストでは消した件数 0 の道で 409 になった |
| 履歴の主キーを追加の順（DB の連番）にし、`revision_id`（UUID）を一意の別の列にした | JPA は主キー以外の列を DB の連番にできないため |
| 置いた人・適用した人に利用者の表への外部キーを置かない | 利用者が見つからないときは「不明」（メールアドレス `null`）として示す（BR6.3） |
| 照合の型の比べ方: 名前は大文字・小文字を区別せず、長さ・精度・桁は DSL に書かれているものだけを比べる。32 ビットに収まらない対象DB の長さは比べない | U3 は `longtext` の長さを書かない。人が書いた DSL が精度を省いたときに誤った警告を出さないため |
| 違いの「変わった項目」の名前 | カラムは `label.ja`・`label.en`・`dbType.name`・`dbType.length`・`dbType.precision`・`dbType.scale`・`dbType.nullable`・`formPart`・`search.*`・`list.*`・`detail.visible`・`validations`・`options`（並びは項目全体で1つ）。テーブルの表示名・ビュー・主キー・外部キーの違いは、テーブルを CHANGED にする（契約 C6 のテーブルの行に項目の名前の欄が無い） |
| 表示名の未設定に数える場所 | メニューの項目・テーブル・カラム・固定の選択肢の表示名。言語ごとに1件 |
| 指標 `compare` はプレビューの表示（`GET /preview`）の照合だけ。`GET /preview` を `DSL_BUSY` で断ったときも `operation=compare`・`outcome=busy` | 決まった語の中に「表示」が無いため、重い部分の照合の名前にした |
| 重い道の許可は印（`@HeavyDslOperation`）のある窓口だけで取る | 同じパスの `DELETE /preview` を軽い道のままにするため |
| 投入の本文は `@RequestBody(required = false) byte[]`。空の本文は U2 の検証の誤り（版が無い）として 422 | 空の本文を 400 ではなく、投入と同じ誤りの一覧で返すため |
| 大きさで断った投入の出どころは、問い合わせの文字列だけから取る | フォームの形の要求で引数を読むと本文を読むことになるため |
| 監査のエンティティの項目名を `dslDigest` にした（列は `dsl_hash`、読み出しは `getDslHash()`） | 既存の `AuditEventTest` が、パスワードのハッシュ値を持たないことを項目名に `hash` を含まないことで確かめているため（テストは変えていない） |

## 3. テストの件数とカバレッジ（実測）

README の「対象DB」の節の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を設定し、colima（CPU 4・メモリ 6GiB）が動いている状態で `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行した（2026-09-24、4 分 8 秒、BUILD SUCCESSFUL、全段が通過、失敗・飛ばし 0）。途中で Gradle の daemon が `node` を起動できなかったため、指示どおり `./gradlew --stop` してやり直した。

| 対象 | 単体テスト（`*Test`） | 結合テスト（`*IT`） |
|---|---|---|
| バックエンド全体 | 703 件（失敗 0・飛ばし 0） | 338 件（失敗 0・飛ばし 0） |
| うち U4 | 70 件 | 33 件 |
| 画面（Vitest） | 167 件 | — |

U3 の記録（単体 633 件・結合 305 件）からの差は、単体 +70（U4）、結合 +33（U4）。

U4 のテストの内訳:

- 単体: `DslLifecycleTest` 23、`DslSummaryAndDiffTest` 9、`DslReconcilerTest` 7、`AuditDslEventFactoryTest` 6、`DslManageDomainTest` 5、`RequestSizeLimitRoutesTest` 5、`DslManageBoundaryArchitectureTest` 4、`DslStartupLoaderTest` 3、`BusinessExceptionPropertiesTest` 3、`AuditDslEventListenerTest` 3、`DslSubmitRejectionListenerTest` 2
- 結合: `DslAdminApiIT` 14、`DslManageRepositoryIT` 10、`DslConcurrencyIT` 3、`DslTargetDbIT` 3（PostgreSQL のコンテナ）、`DslStartupIT` 2、`DslAuditWriteFailureIT` 1

| カバレッジ（JaCoCo、単体と結合の合算） | 行 | 分岐 |
|---|---|---|
| 全体 | 98.1%（3858/3934） | 94.1%（1344/1429） |
| `cherry.mastersmith.dslmanage.domain` | 100.0%（129/129） | 92.3%（24/26） |
| `cherry.mastersmith.dslmanage.repository` | 100.0%（74/74） | 100.0%（4/4） |
| `cherry.mastersmith.dslmanage.service` | 100.0%（483/483） | 98.4%（126/128） |
| `cherry.mastersmith.dslmanage.web` | 100.0%（129/129） | 86.7%（26/30） |
| `cherry.mastersmith.dslmanage.generate`（U3） | 99.3%（303/305） | 99.3%（144/145） |
| `cherry.mastersmith.audit.service`（既存、全体で判定） | 80.9%（76/94） | 83.3%（10/12） |
| `cherry.mastersmith.common.web`（既存、全体で判定） | 96.5%（110/114） | 85.2%（46/54） |

下限（行 80%・分岐 70%、全体と新しいパッケージごと）はすべて満たした。全体の合計は U3 の記録（97.5%・93.8%）を下回っていない。計測の除外は既存のもの（起動クラスと `*Properties`）だけで、U4 のために足していない（`DslManageProperties` は既存の除外に当たる）。U1〜U3 のパッケージの下限も保たれている。

途中の実行で `dslmanage.domain` の分岐が 46% になり関門で止まったため、ドメインの値のテストを足した。その過程で、読み込み前のエンティティの文字列化が例外になる誤りを見つけて直した（`DslPreviewRecord`・`DslAppliedRevisionRecord` の `toString`）。

安全の検査: SpotBugs の統合を止める指摘（priority 1・`SQL_`）0 件。Gitleaks は漏えい無し。OSV-Scanner は依存が変わらないため前回の結果のまま（UP-TO-DATE）。

team.md の「投入 DSL の必ず書くテスト」との対応（U4 の受け持ち）: 大きさはちょうど 10,485,760 バイトが受け付けられ、1 バイト超えは 413 `DSL_TOO_LARGE`（本文を送らずに `Content-Length` だけで断ることを確かめた）。拒否の応答は Problem Details で、YAML・JSON Schema の部品の文言を含まない。深さ・別名・タグ・重複キー・`$ref` は U2 のテストが確かめている。認可は未認証 401・管理者でない 403 の代表を確かめた（一括は U5）。

## 4. 決定 A・B・C の反映

| 決定 | 反映 |
|---|---|
| A（本文の上限の置き場） | `SecurityConfig` で `RequestSizeLimitFilter` を `AuthorizationFilter` の後へ移し、道ごとの上限（投入の道 10MB・`DSL_TOO_LARGE`、ほか 1MB・`PAYLOAD_TOO_LARGE`）と知らせの口を足した。`common.web` は DSL を知らない（構造の検査 `DslManageBoundaryArchitectureTest` で守る）。413 で断った投入は `DslSubmitRejectionListener` が操作した人つきで `DSL_SUBMISSION_REJECTED`（`SIZE_LIMIT`、識別なし）と指標 `submit`・`rejected` に出す。ログインしていない 10MB 超の投入が 401 になることを結合テストで確かめた |
| B（照合の全体の上限なし） | 照合は U1 の `readSchema(COMPARE)` だけで、全体の上限は作っていない。応答しない対象DB（手元の待ち受け）で、プレビューの表示が警告 `TARGET_UNAVAILABLE` つきで 10 秒の内に返ることを確かめた（接続の待ち 3 秒で打ち切り） |
| C（既存のテスト3件を直さない） | `ExposureIT` の2件（`Content-Length`・分割の送信で 1MB 超が 413）と `SecurityHeadersIT` の1件（413 にもヘッダー）は、テスト用の決まり `PublicApiTestRules` の下で直さずに 413 のまま通った |

## 5. 計画からのずれ

| ずれ | 扱い |
|---|---|
| 計画の部品の表に無い型を足した: `DslRecordStore`（トランザクションの境界）、`DslOperation`・`DslOutcome`・`DslRequestContext`、`DslPreviewRef`・`DslAppliedRef`・`DslContent`・`DslUserRef`・`DslStatus`・`DslErrorItem`・`DslDownload`・`DslOperationType`、`DslAdminPaths`・`DslResponses`・`ApplyRequest`・`HeavyDslOperation`・`DslWebConfig`・`DslRequestContextResolver`、`common.web` の `RequestBodyLimitRoute`・`RequestSizeRejection`・`RequestSizeRejectionListener` | 3節の部品の中身を分けたもの。トランザクションの境界を `DslLifecycle` から分けた理由は 2節 |
| Step 6 で使う `BusinessException` の追加の項目を、Step 8（共通の拡張）より先に足した | 業務処理の層が 422 の例外を作るのに要るため。応答に載せる部分（共通の変換）は Step 8 で足し、Step 9 で確かめた |
| 契約 C7 の出来事に `sourceIp`・`userAgent` を足した | 監査の表の `source_ip` が NOT NULL のため。項目の追加は契約の決まりで安全な変更 |
| `DslAdminApiIT` の 10MB 超の投入は、HTTP の部品ではなく、ヘッダーだけを送る手元のソケットで確かめた | アプリは本文を読まずに 413・401 を返し、Tomcat の読み捨ての上限（2MB）を超える本文は接続を閉じるため、HTTP の部品が送り切れずに失敗した。本文を読まずに断ることの確かめにもなる |
| 既存の `AuditSecretLeakIT` の「監査の表に記録の項目以外の列が無い」の確かめに、V6 の 4 列を足した | 承認済みの V6（NULL を許す列 4 つ）と必ず食い違う確かめのため。列の一覧を固定で確かめる決まり自体は保った |
| `backend/build.gradle.kts` でテストの JVM のヒープの上限を既定の 512MB から 1g にした | U4 のクラスが増えたことで、構造の検査の各クラスが静的に持つクラスの読み込みの結果が大きくなり、U3 の 10MB を超える DSL を作る単体テストでヒープが尽きた（U4 の単体テストを外しても再現）。既存・U1〜U3 のテストを変えずに直す最小の変更として選んだ。件数・カバレッジの下限は変えていない |
| Step 13 の式の確かめは、compose の profile `monitoring` ではなく、同じイメージ（`grafana/otel-lgtm:0.33.1`）の使い捨てのコンテナ（`127.0.0.1` だけに結び付け、ボリュームなし、終わったら止めて消した）と、手元で一時の内部DB で起動したアプリ（作ったばかりの WAR）で行った | profile の OTLP の受け口は PC に開いていないため、手元で起動したアプリから送れない。配備済みの `mastersmith-app-1` には触れていない（要求も送っていない） |

## 6. 承認済みの文書との差

承認済みの文書は書き換えていない。

| 項目 | 文書 | 承認済みの記述 | 実装 |
|---|---|---|---|
| 大きさの上限 | 契約 C6、機能設計 BR1.2・BR1.6、functional-spec.md 6節 | 5MB | 10MB（U3 の NFR 要件の決定） |
| 本文の型 | `functional-design/entities.md` の `yamlText`（text） | 文字列 | `BINARY LARGE OBJECT`、列の名前 `yaml_bytes`（NFR 設計 Q3: A） |
| 大きさの確かめの置き場 | 機能設計 BR1.2・BR1.6（業務処理で確かめる形に読める） | — | 認証・認可の後の `RequestSizeLimitFilter` の道ごとの上限（決定 A） |
| 既存のテスト3件を直す | `nfr-design/security-design.md` 1節・5節 | コード生成で直す | 直していない（決定 C） |
| `DSL_BUSY`（503） | 契約 C6 | 無い | 足した（NFR 要件の決定） |
| 利用者の ID の型 | `entities.md`（`placedByUserId` などは uuid） | uuid | 既存の利用者の ID（`BIGINT`）に合わせた |
| 業務の例外の 5xx のログ | 既存の `GlobalExceptionHandler`（5xx は ERROR） | — | 業務の例外は 5xx でも WARN（NFR5.3「想定内の失敗（503）は WARN 以下」） |
| 契約 C7 の出来事の項目 | 契約 C7 | 接続元IP・User-Agent は無い | 足した（5節） |

## 7. U5・Build and Test への申し送り

- **U5（画面）**: API の形は README の「DSL の管理の API（U4）」と契約 C6 のとおり。日時は ISO 8601（UTC）、利用者の ID は文字列、利用者が見つからないときは `by.email` が `null`。`DSL_BUSY`（503）の文言の追加（U4 の tech-stack-decisions.md 2節）。422 の `errors[].message` と警告の `message` は `Accept-Language` の言語で返る。ダウンロードは `Content-Disposition: attachment; filename="dsl-preview-<識別の先頭12文字>.yaml"`。
- **U5（一括のアクセス制御）**: C6 の 11 本の API を並べた 401・403・200 の確かめは U5 の Bolt の受け持ち（U4 は代表だけ）。B5 の2本（戻し・適用中のダウンロード）は B5 で足す。
- **U5（画面の送り方）**: 本文の上限は `Content-Length` がある送り方なら本文を読まずに断る。chunked で送ると、断る要求（`DSL_BUSY` を含む）の本文も先に読まれる（security-design.md 5節の残る危険）。
- **Build and Test（測定）**: NFR1.6（生成 30 秒）・NFR1.8（表示・投入 10 秒、想定の規模と 10MB）・NFR1.10（軽い API 1 秒）・NFR1.11（10MB のダウンロード 2 秒）・NFR1.12（10MB の DSL のヒープ）、H2 のファイルの大きさ、適用の後の監査で接続を使うときのプールの余裕を測ること。照合は決定 B により応答しない対象DB で最悪 23〜28 秒で、その間ほかの重い処理は `DSL_BUSY` になる。
- **Build and Test（監視）**: ダッシュボードの 3 つの式は使い捨ての監視のコンテナで実行して確かめた（名前 `mastersmith_dsl_operation_milliseconds_{bucket,count,sum}`、タグ `operation`・`outcome`・`service_name`）。ログの画面（Loki）で `dsl.operation` のキーで絞れることは確かめていない（monitoring-design.md 4節）。compose の profile `monitoring` での確かめと合わせて、Build and Test または Observability Setup で確かめること。
- **Build and Test（テストの JVM）**: テストのヒープを 1g にした（5節）。CI（GitHub Actions）でも同じ設定で動く。
- **B5（Step 15〜17）**: `DslProblemTypes` の `DSL_APPLIED_NOT_FOUND`・`DSL_REVISION_NOT_FOUND` は登録済み。`DslOperation.RESTORE`、`DslAppliedRevisionRepository.findContent`、`DslRecordStore.findRevisionContent` は用意してある。`DslDownload` の適用中のファイル名（`dsl-applied-...`）の作り方は B5 で足す。
