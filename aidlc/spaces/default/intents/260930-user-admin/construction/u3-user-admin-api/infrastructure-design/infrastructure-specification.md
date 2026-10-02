# Infrastructure Specification — U3 利用者の管理の API（u3-user-admin-api）

U3 の基盤の設計です。U3 は、管理者が使う利用者の管理の API（一覧 GET `/api/admin/users`、氏名と言語の変更 PUT `/api/admin/users/{userId}/profile`、5つの操作 POST `/api/admin/users/{userId}/grant-admin`・`revoke-admin`・`suspend`・`resume`・`reset-login-failures`、契約 C3）を新しいパッケージ `useradmin` に置く service の単位です。コード生成では前半の B3（一覧・氏名と言語の変更）と後半の B4（5つの操作・最後の管理者の保護・監査・既存の経路の上限切れの漏えいの直し）に分けます（`inception/delivery-planning/bolt-plan.md`）。

配備先は開発者の PC 上のコンテナだけで、クラウドの基盤（IaC・検証環境・警報の通知の先）は作りません。配備先が決まったときに置き換える前提です（`aidlc/spaces/default/memory/project.md` の Deployment、`team.md` の Deployment）。U3 の基盤の中身は承認済みの NFR 要件・NFR 設計と既存の仕組みでほぼ決まっているため、この文書は新しい設計ではなく、既にある仕組みの上で U3 がどう動くかの記録です。変わるのは WAR の中のコードだけで、`Dockerfile`・`compose.yaml`・`.env.example`・`application.yaml` の設定の項目・`docker/monitoring/` は変えません。

- 答え: この段の `infrastructure-design-questions.md`（Q1 A・Q2 A、Consolidated Summary Confirmation: Looks correct）
- 上流（どれも `aidlc/spaces/default/intents/260930-user-admin/` の下）: `construction/u3-user-admin-api/nfr-design/`（`scalability-design.md`・`reliability-design.md` 5節・8節〜11節・`security-design.md` 7節〜12節と「承認の場の決定」・`observability-design.md`・`performance-design.md` 7節・`logical-components.md` 4節〜8節）、`construction/u3-user-admin-api/nfr-requirements/`、`construction/u3-user-admin-api/functional-design/functional-spec.md` 8節、`inception/domain-design/components.md`、`inception/contract-design/contract-summary.md`（C1・C3・C6・C8）、`inception/delivery-planning/bolt-plan.md`（B3・B4）
- 既にある仕組み（正とする、読むだけ）: `Dockerfile`・`compose.yaml`・`docker/perf/compose.yaml`・`backend/src/main/resources/application.yaml`・`backend/build.gradle.kts`・`build.gradle.kts`・`.github/workflows/ci.yml`・`docker/monitoring/provisioning/alerting/mastersmith.yaml`・`README.md`

出典の略号: 「要点 n」「Qn」はこの段の質問の文書、NFR はこの単位の NFR 要件の枝番、BR はこの単位の機能設計の `rules.md`、ND・SD・PD はこの単位の NFR 設計の上流との差の ID。

## 1. Deployment

| Facet | Choice | Rationale |
|---|---|---|
| Compute model | 既存のまま。フロントエンドの `dist` を同梱した実行可能 WAR のイメージ `mastersmith:local`（`Dockerfile`）を、compose の `app` 1台で動かす。U3 は新しいコンテナ・compose の profile・定期の処理を足さない | U3 はアプリのメモリに状態を持たず、排他は内部DB の行で行う（`scalability-design.md` 1節）。最後の管理者の保護も DB の行の排他で守るため、1台の前提に新しい依存を足さない |
| Networking topology | 既存のまま。`app` の 8080 は PC の `127.0.0.1:8080` だけに公開し、画面と API は同じオリジン（CORS の設定なし）。7つの API は既存の `/api/admin/**`（管理者だけ）の決まりに乗り、公開の道を足さない。U3 は外へ通信しない（メールを送らない） | NFR1.1。要求の回数の制限が無いこと（`security-design.md` 11節の R1）の影響を、配備先が決まるまで PC の中に限る |
| Storage strategy | 既存のボリューム `mastersmith-data`（内部DB の H2 のファイル）のまま。U3 は表・列・索引を足さず、Flyway の移行を足さない（4節） | NFR10.1・NFR10.2（索引を足さない決定 Q2 A、`performance-design.md` 2.3）。監査の行は5つの操作1回に1行で、ログインの監査に比べて小さい（`scalability-design.md` 4節） |
| Environments | 配備した環境（`compose.yaml`、プロジェクト `mastersmith`）1つ。一時の環境は、負荷の試験の使い捨ての環境（`docker/perf/compose.yaml`、プロジェクト `mastersmith-perf`、`127.0.0.1:18080`）と、E2E（WAR を PC の上で直接起動、`frontend/playwright.config.ts`、既定のポート 18081） | 配備先が決まるまで検証環境・本番環境は無い（`team.md` の Deployment）。一時の環境は本物のデータと監査ログを汚さない（`project.md` の Testing Posture） |
| IaC approach | 基盤の定義は既存のファイルで持ち、U3 では変えない。U3 が書き換えるリポジトリの基盤まわりのファイルは、負荷の試験の `perf/k6/scenarios.js`・`perf/README.md`（`cicd-pipeline.md` 4節）と `README.md`（9節）だけ | 配備先が決まるまでクラウドの IaC は作らない（`project.md` の Deployment） |
| Resource sizing | 既存のまま。colima の VM は CPU 4・メモリ 6GiB（2026-10-02 に読み取りで確かめた）、`app` の上限は CPU 4・メモリ 2g、接続プールは上限 30・借りる待ち 5000 ms、排他の待ちの上限 3000 ms | 同時 10 件がすべて5つの操作でも最大 20 本で上限 30 に収まる（NFR6.1、`scalability-design.md` 2節）。見積もりそのものは上限 10 の場面で確かめる（NFR6.3、`cicd-pipeline.md` 4節） |
| Scaling | 縦（コンテナの CPU・メモリ）だけ。複数台の妨げは組み込みの H2 で U3 の外の制約。複数台・別の DB へ移すときの確かめ直しは `scalability-design.md` 5節 | NFR5.8 |
| Configuration and secrets | 新しい設定の項目・環境変数・秘密を足さない（3節）。U3 が読む設定は既存の接続プールの上限・ログの設定・要求の本文の上限だけ | `security-design.md` 9節、要点の「決まっていること」 |

## 2. Infrastructure Services

| Service | Role | Configuration | Notes |
|---|---|---|---|
| 内部DB（組み込みの H2） | database | 既存のまま（`MASTERSMITH_DB_URL` の既定 `jdbc:h2:file:./data/mastersmith;DEFRAG_ALWAYS=TRUE`、ボリューム `mastersmith-data`）。U3 は `users`・`login_attempt_states`・`refresh_tokens`・`audit_events` を読み書きし、列・索引・CHECK 制約を変えない | 状態を持つのは内部DB だけ（NFR5.8）。一覧の検索は `ilike` の部分一致で全体を走査し、1,000 名でも DB の中の時間は p95 1 ミリ秒未満の見込み（`performance-design.md` 2.2） |
| 接続プール（HikariCP） | database（接続） | 既存のまま（上限 `MASTERSMITH_DB_MAXIMUM_POOL_SIZE` の既定 30、`connection-timeout` 5000 ms） | 一覧・氏名と言語の変更は1件に1本、5つの操作は確定の後の監査で2本目を借りて1件に2本（NFR6.1）。管理の操作 10 件とログインの失敗 10 件が重なると最大 40 本で上限を超えうる点は、前の Intent からの既知の制約のまま（README の「既知の制約（同時の要求と接続プール）」）で、この単位では確かめない |
| 行の排他 | database（排他） | `PESSIMISTIC_WRITE`、待ちの上限 3000 ms（既存の定数、Hibernate は `for update wait 3`）。管理者の行は利用者 ID の昇順で取る。設定の項目にしない | NFR4.3・NFR4.4・NFR5.6。上限切れは U3 の5つの操作では 409 USER_ADMIN_BUSY、既存のログイン・招待・送り直し・取り消し・登録の完了では今までどおり 500（6節） |
| スキーマの移行（Flyway） | database（移行） | 既存の設定のまま。U3 は移行ファイルを足さない。V9 は U1 の持ち物 | NFR10.1・NFR10.2（4節） |
| 監査の記録（`audit_events`） | database（追記だけ） | 既存の仕組み（`AuditEventListener` の AFTER_COMMIT、`AuditEventRecorder` の `REQUIRES_NEW`）のまま。B4 で種類5つと理由4つの値を足す（列は変えない） | NFR9.3〜NFR9.5。監査の種類と理由は今のコードの `AuditEventType`・`AuditFailureReason` には無く、B4 で足す（`monitoring-design.md` 5節） |
| アクセス制御（SecurityFilterChain） | ingress の判定 | 既存の `/api/admin/**` の管理者の決まりと、U1 のアクセストークンの認証の入口（停止中は 401）のまま。U3 は差し込み口の決まりを足さない | NFR1.1・NFR1.3。README の「API のアクセス制御（U3）」の公開の一覧は変わらない |
| 要求の本文の上限 | ingress の制限 | 既存の `MASTERSMITH_WEB_MAX_REQUEST_BODY_SIZE`（既定 1MB）のまま | NFR9.1 |
| メソッドの追跡（`TraceAspect`）とアプリのログ | 観測（ログ） | 既存の `mastersmith.trace.*`（`MASTERSMITH_TRACE_*`）・`logging.level`（root は INFO）・`logback-spring.xml` を変えない。TRACE は漏えいのテストの設定の中だけで有効にする | 伏せ字の型と、例外を repository の外へ出さない作り（`security-design.md` 4節・7節）。B4 で `TraceAspect` の例外の出力の側にも手当てが入る（6.2） |
| 手元の監視（grafana/otel-lgtm、profile `monitoring`） | 観測 | 既存のまま。U3 は指標・警報・ダッシュボードを足さない | `monitoring-design.md` |
| 負荷の試験の使い捨ての環境 | 試験の環境 | 既存の `docker/perf/compose.yaml`（`mastersmith-perf`、`127.0.0.1:18080`、上限 CPU 4・メモリ 2g）のまま。U3 はメールを送らないため Mailpit（profile `mail`）を起動しない | NFR5.1〜NFR5.7・NFR6.2・NFR6.3（`cicd-pipeline.md` 4節） |
| E2E の環境 | 試験の環境 | 既存の `frontend/playwright.config.ts`（WAR を直接起動、一時の内部DB、初期管理者 `e2e-admin@example.com`）と Mailpit（profile `mail`、`127.0.0.1:8025`）のまま。U3 は E2E の流れを足さない | Q1 A で B4 の統合の前に 010〜100 を流す（`cicd-pipeline.md` 5節） |

### 2.1 Shared Infrastructure

| Shared Resource | Owner Unit | Consumer Units | Access Boundary |
|---|---|---|---|
| 内部DB の `users` の表 | 既存の UserAccount（停止の列は U1 の V9） | U3（印・停止・氏名・言語の更新、管理者の行の排他、一覧の読み取り）、U1（停止の判定） | U3 は `user.service` の口（C8・C1）だけを使い、`user.repository` を直接呼ばない（`UserAdminBoundaryArchitectureTest`）。書き換えの口の呼び出し元は `useradmin.service` だけ（NFR11.2） |
| 内部DB の `login_attempt_states` の表 | 既存の Authentication（`auth`） | U3（失敗回数を戻す操作の排他と更新、一覧のロックの判定の読み取り） | `auth.service` の口（C8）だけを使う。ログインの判定と同じ行・同じ待ちの上限。行を作らない |
| 内部DB の `refresh_tokens` の表 | 既存の Authentication | U3（止める操作のまとめての無効化、U1 の C1 の口） | U1 の C1 の口だけを使う。既存の利用者 ID の索引のまま |
| 内部DB の `audit_events` の表 | 既存の AuditLog | U3（種類5つ・理由4つの行） | 書き込みは `audit` の記録の仕組みだけ。`useradmin` は出来事（`UserAdminAuditEvent` の例）で知らせ、`audit` に依存しない |
| 接続プール（上限 30） | 既存（`application.yaml`） | すべての API。U3 の5つの操作は1件に2本 | 上限の値を U3 で変えない（3節） |
| `TraceAspect`・`GlobalExceptionHandler` | 既存（`common.observability`・`common.error.web`） | すべての層・すべての API | B4 で、排他の失敗の連なりを持つ例外を型の名前だけにする中央の手当てを足す（6.2）。設定の項目と応答の形は変えない |
| 排他の失敗の判定の部品と値を含まない例外 | U3（B4、置き場の候補は新しいパッケージ `common.persistence`） | U3 の排他の口、既存の E1〜E4、6.2 の中央の手当て | 部品の形と置き場は B4 のコード生成の計画で決める |
| `perf/k6/scenarios.js`・`perf/README.md`・`docker/perf/compose.yaml` | 既存 | 前の Intent の各単位、U3 が場面を足す | 使い捨ての環境だけに向ける。配備した環境には流さない |

## 3. 設定の項目

U3 は設定の項目・環境変数・秘密を足さず、既存の値も変えません。U3 に関わる既存の項目は次のとおりです。

| 項目 | 値（既定） | U3 での使い方 | 出典 |
|---|---|---|---|
| `MASTERSMITH_DB_MAXIMUM_POOL_SIZE`（`spring.datasource.hikari.maximum-pool-size`） | 30 | 変えない。負荷の試験の NFR6.3 の場面だけ、使い捨てのアプリの一時の環境ファイルに 10 を置く | NFR6.1〜NFR6.3 |
| `spring.datasource.hikari.connection-timeout` | 5000 ms（設定の項目にしていない固定値） | 変えない | NFR6.2 |
| 排他の待ちの上限（`jakarta.persistence.lock.timeout`） | 3000 ms（既存の定数） | 変えない。設定の項目にしない | NFR4.4・NFR5.6 |
| `MASTERSMITH_TRACE_*`（`mastersmith.trace.*`）と `logging.level` | 既存の値（`log-exception-stack-trace` は true、root は INFO） | 変えない。B4 の手当ては、これらの値によらず行の値が出ない作りにする（6.2） | Q2 A、`security-design.md` 9節 |
| `MASTERSMITH_WEB_MAX_REQUEST_BODY_SIZE` | 1MB | 変えない | NFR9.1 |
| `management.endpoints.web.exposure.include` | health だけ | 配備したアプリでは変えない。負荷の試験の使い捨てのアプリにだけ `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics` を置く | NFR6.2・NFR6.3、`project.md` の Testing Posture |

- 配備した環境の `.env` は変えない。そのため U3 の配備で `.env` の複写を取る必要は無い。

## 4. 内部DB と移行・後方互換（NFR10.1・NFR10.2）

| 項目 | 設計 | 出典 |
|---|---|---|
| 表と列 | 変えない。Flyway の移行を足さない | NFR10.1、`reliability-design.md` 9節 |
| 監査の種類と理由 | 既存の `event_type`・`failure_reason`（VARCHAR(32)、CHECK 制約なし）に値を足すだけ。最長は `LOGIN_FAILURES_RESET` の 20 文字と `LAST_ACTIVE_ADMIN` の 17 文字。32 文字以内であることはコード生成のレビューで確かめる | NFR10.1 |
| 1つ前の版との後方互換 | 1つ前の版のアプリは監査の行を書くだけで（本番のコードで読むのはテストだけ）、新しい値の行を読まない。Hibernate の `validate` は列の値を確かめない。新しい値の行があっても1つ前の版は困らない | NFR10.1、`reliability-design.md` 9節 |
| 索引 | 足さない（Q2 A）。NFR10.2 は当てはまらない。performance-validation で NFR5.1 に届かず索引を足すと決めたときは、V10 以降・前進のみ・1つ前の版が動く後方互換・移行は U3 が持つ、の決まりで足し、依頼者に諮る | NFR10.2、`performance-design.md` 2.3 |

## 5. 戻し方

| 手 | 内容 | U3 から見た扱い |
|---|---|---|
| 第一の手 | 直前の版のイメージ（戻し用のタグ）で起動し直す | U3 は表・列・`.env` を変えないため、U3 の分はイメージだけで戻る。U3 が残した監査の行（新しい種類・理由の値）は1つ前の版が読まないため残してよい |
| 第二の手 | 内部DB のバックアップの展開 | U3 の分では要らない。この Intent では V9（U1）があるため、バックアップと戻しの練習の要否は U1 の持ち物として deployment-pipeline で決める |

- 戻しても、U3 が変えた利用者の状態（管理者の印・停止・氏名・言語・失敗回数）は内部DB に残る。1つ前の版も同じ列（停止の列は V9）を読むため、戻した後の状態は U3 の操作の結果のまま。
- 手順の書き起こしは deployment-pipeline、実行は deployment-execution。

## 6. 排他の待ちの上限切れとログの漏えい（B4）

### 6.1 承認済みの設計の範囲（行の排他の読み取り）

| 経路 | 直す形 | 応答 | 出典 |
|---|---|---|---|
| U3 の5つの操作の排他の口 | repository のメソッドの本体の中で `PersistenceException` を受け、排他の失敗の判定（型と誤りの番号 50200・40001、SQLState HYT00・40001）で見分け、WARN（排他の種類と例外のクラスの名前だけ）を出して Busy を返す | 409 USER_ADMIN_BUSY（約 3 秒） | ND-1、`security-design.md` 7.1、`reliability-design.md` 5.2 |
| 既存の E1（`LoginAttemptStateRepository#lockForUpdate`） | 同じ判定で受け、WARN を出してから値を含まず原因をつながない例外を投げる | 今までどおり 500 INTERNAL_ERROR | SD-6、`security-design.md` 7.2 |
| 既存の E2〜E4（`InvitationRepository` の `@Lock` の3本） | EntityManager を直接使う実装に移し、E1 と同じく受ける | 今までどおり 500 | SD-6、`security-design.md` 7.2 |

### 6.2 承認の場の決定（書き込みの問い合わせの上限切れ、Q2 A）

NFR 設計の2回目のレビューの R-01（Major）が指摘した書き込みの問い合わせ（`UserRepository` の `updatePreferences`・`updatePasswordHashIfUnchanged`、`RefreshTokenRepository` の `@Modifying` の2本、`InvitationRepository` の送信の結果などの `@Modifying`、`LoginAttemptStateRepository` の `update`・`createIfAbsent`）の上限切れは、承認の場の決定（レビューの選択肢 (2)）で、個別の問い合わせではなく `TraceAspect` の出力と `GlobalExceptionHandler` の ERROR の側で、原因をつながず型の名前だけにする中央の手当てに寄せ、B4 で行います。U3 の管理の操作が `users`・`refresh_tokens` の行を持つ間に、本人のパスワードの変更・表示の設定の保存・トークンの更新がその行を待って上限切れになる経路が新しく生まれるためです。

基盤への影響は次のとおりです。

| 影響 | 設計 |
|---|---|
| 設定 | 設定の項目・既定値・ログの設定（`mastersmith.trace.*`・`logging.level`・`logback-spring.xml`）を変えない。`MASTERSMITH_TRACE_*` の環境変数の値（例: `log-exception-stack-trace` を true のまま、TRACE を有効にした場合）によらず、排他の失敗の連なりを持つ例外の行の値と `MVStoreException` の文がログに出ない作りにする |
| 応答と巻き戻し | 変えない。書き込みの問い合わせの上限切れは今までどおり想定外の誤り（500 INTERNAL_ERROR）で、トランザクションは巻き戻る。409 などに変えない |
| 変わるもの | ログの中身だけ。`GlobalExceptionHandler` の ERROR は今までどおり1件出て `ms-error-logs` に数えられるが、スタックトレースの原因の連なりの代わりに型の名前だけになる。TRACE の `TraceAspect` の EXCEPTION の行も同じ（`monitoring-design.md` 4節） |
| カバレッジ | 手を入れる `common.observability`（`TraceAspect`）と `common.error.web`（`GlobalExceptionHandler`）は今は `backend/build.gradle.kts` の `packagesJudgedByTotal` にあるため、B4 でテストを足してパッケージごとの下限（行 80%・分岐 70%）を満たし、一覧から外す。今の値は B4 のコード生成の計画で実測して見積もる（`cicd-pipeline.md` 2節） |
| 確かめ | 書き込みの待ちの上限切れ（例: 管理の操作が行を持つ間のパスワードの変更）を、TRACE を有効にした場合と既定のレベル（INFO）の場合の両方で起こし、ログに行の値と `MVStoreException` の文が出ないテストを足す（`cicd-pipeline.md` 1節） |
| B4 の計画で決めること | 中央の手当ての部品の形と置き場、6.1 の E1〜E4 の個別の直しとの役割の分け方、対象にする書き込みの問い合わせの範囲 |

- 書き込みの待ちの上限切れの例外の文に行の値が入るかは、NFR 設計の試しのコードでは確かめていない（試したのは `SELECT ... FOR UPDATE` の連なりだけ）。中央の手当ては、入る場合も入らない場合も行の値を出さない形として決めた。
- NFR 設計の2回目のレビューの R-02（Minor）の「E2〜E4 を移した後も、断片のメソッドの名前・引数・戻り値を今と同じにし、`InvitationService` の既存の単体テストの差し替えを壊さない」も、B4 のコード生成の計画の条件として渡す。

## 7. 秘密・個人に関する値の扱い（DevSecOps・Compliance の視点）

| 対象 | 扱い | 出典 |
|---|---|---|
| 検索の文字・メールアドレス・氏名 | controller の引数から repository の口まで伏せ字の型のまま渡し、アプリのログ・トレースの属性・監査ログ・エラー応答に出さない。確かめは TRACE を有効にした `UserAdminSecretLeakIT` | NFR3.1、`project.md` の Forbidden |
| 排他されていた行の値（メールアドレス・パスワードのハッシュ値・氏名・招待のトークンのハッシュ値・失敗回数・解除の予定の時刻） | 例外の連なりの最後の `MVStoreException` の文に入りうるため、6節の作りでログに出さない。確かめは TRACE と既定のレベル（INFO）の両方の漏えいのテスト | NFR3.1・NFR3.4、`security-design.md` 7節・7.3、6.2 |
| 応答に含めない値 | パスワードのハッシュ値・招待のトークンのハッシュ値・リフレッシュトークン・失敗回数そのもの・ロックの判定の内部の値 | NFR3.2、`project.md` の Forbidden |
| 管理の操作の監査 | 5つの操作は操作した人・対象・結果・理由を追記だけの `audit_events` に残す。監査の行は消せない。監査ログをアプリのログで代用しない | NFR9.3、`project.md` の Mandated |
| 負荷の試験の資格情報と一時の環境ファイル | リポジトリの外（ホームの下、権限 700）に置き、値を表示せず、終わったら消す。試験用の利用者のメールアドレスは予約のドメイン（`example.com` など）だけ | `project.md` の Testing Posture・Forbidden、`team.md` の Testing Posture |
| 配備した環境 | `.env`・秘密・公開の範囲を変えない。`/actuator/metrics` を配備したアプリで公開しない | 3節 |
| 要求の回数の制限 | 置かない（R1）。管理者だけが呼び、すべての5つの操作が監査に残り、最後の管理者は無くせない。社外に公開する配備先が決まったときに見直す | `security-design.md` 11節 |

## 8. 配備の後の確かめに渡す事実（deployment-pipeline で決める）

| 要求 | 監査 | データ | 注意 |
|---|---|---|---|
| 一覧（GET `/api/admin/users`） | 残らない | 変えない | 応答に利用者のメールアドレス・氏名が入る。値を記録・報告に写さない |
| 氏名と言語の変更（PUT `/{userId}/profile`） | 残らない（BR5.3） | 対象の利用者の氏名・言語を変える | 元に戻す要求も監査に残らない |
| 5つの操作（成功と業務の拒否） | 1件ごとに1行（追記だけで消せない） | 成功は利用者の状態を変える | 送る前に依頼者に伝える（`project.md` の Corrections） |
| 初期管理者が自分の印を外す・自分を止める | 409 USER_ADMIN_SELF_OPERATION で拒否され、監査に理由 SELF_OPERATION で残る（拒否の順で自分自身が最後の管理者より先、`rules.md` の BR2.1・BR2.3） | 変えない | 同上。最後の管理者の拒否（LAST_ACTIVE_ADMIN）を配備した環境で見せるには別の管理者が要り、利用者が残る |

- 監査の種類と理由の名前は今のコードの `AuditEventType`・`AuditFailureReason` には無く B4 で足す。手順書やスモークテストに書くときは、B4 の後の定義で確かめてから書く（`project.md` の学び）。
- パスワードなど秘密が要る操作は依頼者が行い、AI は監査とログで裏付ける（`project.md` の Corrections）。

## 9. README に足すこと（コード生成で書く）

| 節 | 足すこと |
|---|---|
| 監査ログ（U4） | 記録の種類に `USER_ADMIN_GRANTED`・`USER_ADMIN_REVOKED`・`USER_SUSPENDED`・`USER_RESUMED`・`LOGIN_FAILURES_RESET` と、理由（`USER_NOT_FOUND`・`NOT_ADMIN`・`SELF_OPERATION`・`TARGET_SUSPENDED`・`NO_CHANGE`・`LAST_ACTIVE_ADMIN`）を足す。一覧・氏名と言語の変更・BUSY・入力の誤り・認可の入口の 401 と 403 は残らないこと |
| 既知の制約（同時の要求と接続プール） | 5つの操作も確定の後の監査で1件に2本の接続を使うこと。管理の操作とログインの失敗が重なると上限 30 を超えうること |
| API のアクセス制御（U3） | 変えない（7つの API は既存の `/api/admin/**` に乗る） |

## 10. 上流との差

承認済みの文書は書き換えず、差をここに記録します（`project.md` の Way of Working・Change Control）。

| ID | 上流 | 上流の記載 | この段の設計 | 理由と扱い |
|---|---|---|---|---|
| I-D1 | NFR 設計 `security-design.md` 7.2・11節・12節、`observability-design.md` 3節、`traceability.json`、監査の記録 | 上限切れで行の値がログに出る経路として、行の排他の読み取り（E1〜E4）だけを挙げ、7.2 の冒頭は「すべて洗い出しました」のまま。書き込みの問い合わせの上限切れの扱いは書かれていない | 書き込みの問い合わせの上限切れを、`TraceAspect` の出力と `GlobalExceptionHandler` の ERROR の側で、原因をつながず型の名前だけにする中央の手当てでまとめて扱い、B4 で行う（6.2）。基盤への影響として、設定とログの設定を変えない、`common.observability`・`common.error.web` を一覧から外して下限を満たす、TRACE と INFO の両方の漏えいのテストを書く | 出典は NFR 設計の2回目のレビュー（`.aidlc-reviews/nfr-design/units/u3-user-admin-api/956a484ea854c0bc/1.json`）の R-01 の選択肢 (2) と、承認のコミット 1bd45bf の件名。承認済みの NFR 設計の文書と監査の記録にはこの決定が書かれていないため、Q2 A でこの段に記録した。NFR 設計の文書は書き換えない |
| I-D2 | NFR 要件 `tech-stack-decisions.md` の NFR9.6、Delivery Planning の見積もり | 一覧の残りのパッケージに手を入れるときの例は `common.web`。B4 の見積もりに `common.observability`・`common.error.web` の作業は無い | B4 で2つのパッケージを `packagesJudgedByTotal` から外し、パッケージごとの下限を満たす作業が付く（`cicd-pipeline.md` 2節） | I-D1 の決定による。NFR9.6 の「実際に手を入れたときは下限を満たして一覧から外す」の範囲で、決まりとの食い違いは無い |
| I-D3 | Bolt の計画（共通の完了の条件） | 統合の前に E2E を流すのは B1・B2・B5 | B4 も統合の前に E2E（010〜100）を流す。B3 は流さない（`cicd-pipeline.md` 5節） | Q1 A。B4 が認証の経路（ログインの判定・ロックの状態の行・招待と登録の完了の排他・すべての要求が通る `TraceAspect` と `GlobalExceptionHandler`）に手を入れるため。Bolt の計画は書き換えない |
