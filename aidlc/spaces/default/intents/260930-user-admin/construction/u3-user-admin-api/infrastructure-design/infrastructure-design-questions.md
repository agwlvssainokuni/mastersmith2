# Infrastructure Design の質問 — u3-user-admin-api（利用者の管理の API、service）

U3 は、管理者が使う利用者の管理の API（一覧・氏名と言語の変更と、印を付ける・外す・止める・停止を解く・失敗回数を戻すの5つの操作、契約 C3）を新しいパッケージ `useradmin` に置く service の単位です。コード生成では、前半の B3（一覧・氏名と言語の変更）と後半の B4（5つの操作・最後の管理者の保護・監査・既存の経路の上限切れの漏えいの直し）の2つの Bolt に分けます（`inception/delivery-planning/bolt-plan.md`）。service の単位のため、この段の成果物は `infrastructure-specification.md`・`monitoring-design.md`・`cicd-pipeline.md`・`traceability.json` です（段の定義 `.claude/aidlc-common/stages/construction/infrastructure-design.md` の `produces_kinds`）。

配備先は開発者の PC 上のコンテナ（colima、`compose.yaml`、`Dockerfile`、実行可能 WAR）だけで、クラウドの基盤は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。U3 の基盤の中身のほとんどは、承認済みの NFR 要件・NFR 設計と既存の仕組みで決まっています。判断の分かれる2点（B3・B4 の統合の前に E2E を流すか、NFR 設計の承認の場の決定が承認済みの文書に書かれていない点の記録の仕方）だけを質問にしました。

読んだ上流（とくに断りの無いものは `aidlc/spaces/default/intents/260930-user-admin/` の下）:

- この単位の承認済みの NFR 設計 `construction/u3-user-admin-api/nfr-design/`（`observability-design.md`・`performance-design.md`・`scalability-design.md`・`reliability-design.md` の 5節・8節〜11節・`security-design.md` の 7節〜12節と「承認の場の決定（Request Changes、2026-10-02）」・`logical-components.md` の 4節〜8節・`traceability.json`・`nfr-design-questions.md`）。最後の承認（Approve）の前の2回目のレビューの記録（`.aidlc-reviews/nfr-design/units/u3-user-admin-api/956a484ea854c0bc/1.json` の R-01・R-02）と、承認のコミット 1bd45bf の件名（「U3 の書き込みの問い合わせの上限切れは B4 で TraceAspect と例外の変換の側でまとめて手当てする」）
- この単位の承認済みの NFR 要件 `construction/u3-user-admin-api/nfr-requirements/`（`performance-requirements.md` の「前提」「測り方の決まり」、`observability-requirements.md`、`tech-stack-decisions.md`）
- この単位の承認済みの機能設計 `construction/u3-user-admin-api/functional-design/functional-spec.md`（8節「後の段へ渡すこと」・9節・「承認の場の決定」）
- 部品の一覧 `inception/domain-design/components.md`、契約 `inception/contract-design/contract-summary.md`（C1・C3・C6・C8）、Bolt の計画 `inception/delivery-planning/bolt-plan.md`（B3・B4 と、すべての Bolt に共通の完了の条件）
- 同じ段で先に決まった U1・U2・U4 の質問の文書（`construction/u1-user-suspension/infrastructure-design/`・`construction/u2-shared-paging/infrastructure-design/`・`construction/u4-admin-forbidden-ui/infrastructure-design/`、どれも 0 問で Looks correct）
- 前の Intent の手本 `aidlc/spaces/default/intents/260925-user-management/construction/u3-invitation/infrastructure-design/`（service の単位の形、負荷の試験は Build and Test が台本と手順書・performance-validation が測定）
- 決まり `aidlc/spaces/default/memory/org.md`・`team.md`・`project.md`・`phases/construction.md`
- 既存の仕組み（読むだけ）: `.github/workflows/ci.yml`（`./gradlew verify`、制限時間 60 分）、`build.gradle.kts`（`verify`・`e2eTest`）、`backend/build.gradle.kts`（`packagesJudgedByTotal` の 12 パッケージ）、`backend/src/main/resources/application.yaml`（`mastersmith.trace.*`、接続プールの上限 `MASTERSMITH_DB_MAXIMUM_POOL_SIZE` の既定 30・借りる待ち 5000 ms、`http.server.requests` の `slo` のバケット 100・250・500・1000・2000・5000 ms）、`backend/src/main/java/cherry/mastersmith/common/observability/TraceAspect.java`、`compose.yaml`、`docker/perf/compose.yaml`、`perf/README.md`・`perf/k6/scenarios.js`、`docker/monitoring/provisioning/alerting/mastersmith.yaml`、`frontend/e2e/`（010〜100）、`README.md`（「API のアクセス制御（U3）」「監査ログ（U4）」「既知の制約（同時の要求と接続プール）」「監査ログの確かめ方」「警報と対応の手順」）
- この PC の実行環境（読み取りだけで確かめた、2026-10-02）: colima の VM は CPU 4・メモリ 6GiB で動いている

## 決まっていること（質問にしない）

| 決まっていること | 根拠 |
|---|---|
| 配備の形は変えない。イメージ `mastersmith:local` を `compose.yaml` の `app` 1台で動かし、内部DB（組み込みの H2）はボリューム `mastersmith-data`。コンテナの上限（CPU 4・メモリ 2g）・接続プール（上限 30・借りる待ち 5 秒）・ポート（`127.0.0.1:8080` だけ）は変えない。新しいコンテナ・ボリューム・ポート・compose の profile・環境変数・秘密を足さず、`Dockerfile`・`compose.yaml`・`.env.example` は変えない | `nfr-design/security-design.md` 9節、`nfr-design/scalability-design.md` 1節・2節 |
| 表と列を変えず、Flyway の移行を足さない。監査の種類5つと理由は、既存の `event_type`・`failure_reason`（VARCHAR(32)）に値を足すだけ。そのため U3 から見た戻しは直前の版のイメージへ戻すだけで、U3 の分の内部DB のバックアップと戻しの練習の論点は無い（V9 は U1 の持ち物で、戻しの手順は deployment-pipeline） | `nfr-design/reliability-design.md` 9節（NFR10.1・NFR10.2）、`construction/u1-user-suspension/infrastructure-design/infrastructure-design-questions.md` |
| 排他の待ちの上限 3000 ミリ秒は既存の定数のまま、設定の項目にしない。接続プールの上限は既存の環境変数 `MASTERSMITH_DB_MAXIMUM_POOL_SIZE`（既定 30）のまま変えない | `nfr-design/security-design.md` 9節、`nfr-design/reliability-design.md` 5.1 |
| ログの設定（`application.yaml` の `mastersmith.trace.*` と既定のログのレベル、`logback-spring.xml`）は変えない。TRACE は漏えいのテストの設定の中だけで有効にする | `nfr-design/security-design.md` 9節、U1 の基盤の設計の要点 7 |
| 新しい指標・警報・ダッシュボードは足さず、`docker/monitoring/` は変えない。7つの API は既存の `http.server.requests` に入り、`slo` のバケットがあるため p95 を判定できる。U3 の失敗は、数が多いときだけ既存の警報（`ms-forbidden`・`ms-5xx-ratio`・`ms-error-logs`・`ms-audit-fail`・`ms-audit-slow`・`ms-pool-pending`）が拾う。409（BUSY・最後の管理者など）は警報にしない。`ms-pool-pending` の式の見直しは配備先が決まるまでの申し送りのまま | `nfr-requirements/observability-requirements.md` の NFR5.9・NFR5.10、`nfr-design/observability-design.md` 1節・2節、`application.yaml` の `slo` |
| 指標の名前・`uri` の値・`le` のバケットと、拾うと書いた警報が実際に数えて鳴ることは、observability-setup で起動して確かめる。要求は指標の送信の周期（1 分）を複数またいで送る | `nfr-design/observability-design.md` 1節・2節、`project.md` の学び（式は起動して確かめる・送信の周期・p95 のバケット） |
| 監査の種類 `USER_ADMIN_GRANTED`・`USER_ADMIN_REVOKED`・`USER_SUSPENDED`・`USER_RESUMED`・`LOGIN_FAILURES_RESET` と理由は B4 で `AuditEventType` などに足す（今のコードには無い）。監査に残るのは5つの操作だけで、一覧・氏名と言語の変更・BUSY・入力の誤り・認可の入口の 401 と 403 は残らない。手順書やスモークテストに監査の種類を書くときは、B4 の後の `AuditEventType` の定義で確かめてから書く | `nfr-design/observability-design.md` 4節、`project.md` の学び（監査に残るかは `AuditEventType` で確かめる） |
| SLO は決めない。performance-validation と observability-setup の値を基準の値として記録し、判定は Unverified として持ち主の段を書く | NFR5.11、`nfr-design/observability-design.md` 6節 |
| 負荷の試験の台本は既存の `perf/k6/scenarios.js` に場面を足し、手順を `perf/README.md` に書く。台本と手順書と `k6 inspect`（`--include-system-env-vars` を付ける）は Build and Test、測定は performance-validation が持つ。場所は既存の使い捨ての環境（`docker/perf/compose.yaml`、プロジェクト名 `mastersmith-perf`、`127.0.0.1:18080`）で、配備した環境には流さない | NFR5.7、`nfr-design/performance-design.md` 7節、`project.md` の Testing Posture、前の Intent の手本 |
| 使い捨ての環境では、利用者 1,000 名・操作する管理者と対象の利用者・止める対象のリフレッシュトークン（未無効 100 件・無効 1,000 件）を、アプリを止めて SQL で入れる。NFR6.3 の上限 10 の場面は、使い捨てのアプリの一時の環境ファイルにだけ `MASTERSMITH_DB_MAXIMUM_POOL_SIZE=10` と `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics` を置く。U3 はメールを送らないため Mailpit は要らない。`caffeinate -i` で台本の全体を包み、測る間は配備したアプリを止め、監査の件数を数えてから片付ける。上限 10 の場面の条件と BUSY の件数の扱い（NFR 要件のレビューの R-07・R-08）は performance-validation の台本で扱う | `nfr-design/reliability-design.md` 8節、`nfr-requirements/performance-requirements.md` の「測り方の決まり」、`project.md` の Testing Posture |
| VM（CPU 4・メモリ 6GiB）に、使い捨てのアプリ（2g）と k6 を同時に置ける。k6 の分の CPU が値に混ざることは結果に明記する | この PC の読み取り、`nfr-requirements/performance-requirements.md` の「測り方の決まり」 |
| `./gradlew verify` と CI（`.github/workflows/ci.yml`）は変えない。U3 のテスト（単体・jqwik・結合・境界）は既存の単体と結合の段で動き、コンテナを使わないため、コンテナの実行環境が無いときも飛ばさない。B4 の上限切れのテストは1件ごとに約 3 秒待つため結合テストの時間が延びる。前後の時間を記録し、CI の制限時間 60 分に余裕があることを確かめる | `team.md` の Testing Posture、`nfr-design/reliability-design.md` 5.3、`security-design.md` 10節 |
| 新しいパッケージ（`useradmin` の各層、判定の部品と例外を置く `common.persistence` の候補）は自動でパッケージごとの下限（行 80%・分岐 70%）の対象になる。`packagesJudgedByTotal` の一覧のパッケージの `src/main` に手を入れた Bolt は、テストを足して一覧から外す。B4 の決定（Q2）で `common.observability`（`TraceAspect`）と `common.error.web`（`GlobalExceptionHandler`）に手が入る見込みで、どちらも今は一覧にある。今の値は B4 のコード生成の計画で実測して見積もる。一覧と計測の除外は増やさない | `team.md` の Testing Posture、`backend/build.gradle.kts`、`nfr-design/logical-components.md` 6節 |
| 静的解析・依存は変わらない。問い合わせは名前つきの引数と SpEL だけで、SpotBugs の除外・lockfile・OSV-Scanner・Gitleaks・Dependabot の設定は変えない | `nfr-design/security-design.md` 9節、`nfr-requirements/tech-stack-decisions.md` |
| 配備の後のスモークテストに何を入れるか（5つの操作は監査に追記だけで消せない行を残す、一覧と氏名と言語の変更は監査に残らないが氏名の変更はデータを変える、初期管理者が唯一の管理者なら自分への印を外す・止めるは 409 で監査に残る）は、事実をこの段の成果物に書いて deployment-pipeline に渡し、そこで決める。監査に残る要求を送る前は依頼者に伝える | `project.md` の Deployment の学び（配備の段は決まっていない点だけを質問する、監査に残る確かめは書く前にソースで確かめる）と Corrections（監査に残る要求は送る前に伝える） |
| 警報が鳴ったときの対応の手順（BUSY の WARN・最後の管理者の拒否の見方など）は incident-response の段で `README.md` の「警報と対応の手順」に足すかを決める。この段は見るものの表だけを書く | `aidlc-state.md` の段の一覧（incident-response を実行）、`nfr-requirements/observability-requirements.md` の「何を見れば分かるか」 |
| `README.md` の直し（コード生成で書く）: 「監査ログ（U4）」に5つの種類と理由と残さない場合、「既知の制約（同時の要求と接続プール）」に5つの操作も1件に2本の接続を使うこと。管理の API は既存の `/api/admin/**` に乗るため「API のアクセス制御（U3）」の公開の一覧は変わらない | `nfr-design/scalability-design.md` 2節、`nfr-design/observability-design.md` 4節、前の Intent の手本の要点 19 |
| 統合は B3・B4 それぞれ `develop` への squash の1コミット。B4 の既存の経路の上限切れの漏えいの直しは、再現するテストを直しと同じコミットに含める | `team.md` の Way of Working、`project.md` の Mandated、`nfr-design/security-design.md` 7.2 |

## Infrastructure Design の要点（案）

### 配備と共有するもの（`infrastructure-specification.md`）

1. **配備**: 上の表のとおり何も変えない。U3 で変わるのは WAR の中のコードだけ。縦に伸ばすだけで、アプリのメモリに状態を持たない（`scalability-design.md` 1節）。
2. **内部DB**: `users`・`login_attempt_states`・`refresh_tokens`・`audit_events` を共有し、列・索引・移行を足さない。行の排他は利用者 ID の昇順、待ちの上限 3000 ミリ秒（`logical-components.md` 5節）。
3. **接続プール**: 5つの操作は1件に2本（確定の後の監査）、ほかは1本。同時 10 件で最大 20 本で上限 30 に収まる。管理の操作 10 件とログインの失敗 10 件が重なる場合（最大 40 本）は前の Intent からの既知の制約で、この単位では確かめない（`scalability-design.md` 2節）。
4. **既存の経路の上限切れの漏えいの直し（B4）**: 排他の読み取り（E1〜E4）は repository の中で受けて値を含まない例外にする（`security-design.md` 7.2）。書き込みの問い合わせの上限切れは、承認の場の決定で `TraceAspect` と例外の変換の側でまとめて手当てする（→ Q2）。どちらも応答（500）・設定の項目・ログの設定は変えず、変わるのはログの中身だけ。
5. **上流との差**: Q2 の答えに従って書く。

### 監視（`monitoring-design.md`）

6. **指標・警報・ダッシュボード**: 足さない。U3 の失敗と拾う既存の警報の対応を `observability-design.md` 2節の表のとおりに書き、実際の指標の名前（手元の監視では `http_server_requests_milliseconds_*`、`uri` は道の型）で書く。
7. **ログ**: BUSY と上限切れは repository の WARN（排他の種類と例外のクラスの名前だけ）と変換の境界のログの2行で、同じトレースID で結び付く（`security-design.md` 12節の SD-5）。既存の経路の上限切れの ERROR は値を含まない例外になり、`ms-error-logs` が今までどおり数える。
8. **監査と SLI**: 5つの種類と理由を表にし、見る手順は README の「監査ログの確かめ方」（アプリを止めて複写を読み取りで開く）のまま。SLO は Unverified、SLI の候補は `observability-design.md` 6節。

### 検査の流れ・負荷の試験・E2E（`cicd-pipeline.md`）

9. **verify と CI**: 変えない。B3・B4 の統合の前に、colima の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡し、`:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` でカバレッジを実測して記録する（`project.md` の Testing Posture）。
10. **負荷の試験**: 場面は一覧 a〜d・氏名と言語の変更・5つの操作（操作ごとに判定）・止める操作の悪い側・接続プール（上限 30 の同時 10 件、上限 10 の同時 5 件と 10 件）。場面ごとに p95 1000 ms 以内と `checks` の率 1、5つの操作は BUSY・NO_CHANGE・5xx が 0 件。台本の受け入れの条件（操作する管理者と初期管理者を対象にしない、VU ごとに対象を分ける）を守る（`performance-design.md` 7節、`reliability-design.md` 8節）。
11. **E2E**: U3 は E2E の流れを足さない（代表の流れ1本は B5）。統合の前に流すかは → Q1。
12. **traceability**: 基盤に関わる枝番（NFR3.1・NFR3.4・NFR5.1〜NFR5.11・NFR6.1〜NFR6.3・NFR9.2・NFR9.3・NFR9.6・NFR10.1・NFR10.2 など）を、3つの成果物の節と設定・タスク・ファイルに結ぶ。新しい枝番は足さない。

---

## Q1: B3・B4 の統合の前に、E2E（`./gradlew e2eTest`）を手元で流しますか？

背景: `team.md` の Testing Posture は「E2E は、画面・認証に関わる変更を統合する前と、リリースの前に手元で実行する」としています。Bolt の計画（`inception/delivery-planning/bolt-plan.md` の共通の完了の条件）は、統合の前に E2E を流す Bolt を B1・B2・B5 としており、B3・B4 は入っていません。ところが、Bolt の計画の後に決まった機能設計と NFR 設計で、B4 は認証の経路に手を入れることになりました。ログインの判定（`LoginService` に待ち合わせの口を足す、BR3.6）、ロックの状態の行の排他（`LoginAttemptStateRepository#lockForUpdate` の直し、E1）、招待・送り直し・取り消し・登録の完了の排他の問い合わせを EntityManager に移す直し（E2〜E4）、すべての要求が通る `TraceAspect` と `GlobalExceptionHandler` の手当て（Q2）です。既存の E2E のうち `020-auth`（ログイン）と `090-invitation-registration-flow`（招待から登録の完了まで）がこの経路を通ります。B3 は新しい管理の API と検索の文字の型の変換を足すだけで、画面と認証の経路には手を入れません。E2E は始める前に Mailpit の起動が要り、010〜100 の全体で時間がかかります。

A. B4 は統合の前に `./gradlew e2eTest` を手元で流し（Mailpit を profile `mail` で起動、`caffeinate -i` で全体を包む）、010〜100 が通ったことを記録する。B3 は流さず、`./gradlew verify` だけで統合する。Bolt の計画は書き換えず、差（B4 を足したこと）と理由を `cicd-pipeline.md` に記録する（推奨）
B. B3 と B4 の両方で、統合の前に E2E を流す。B3 では画面と認証の経路は変わらないが、念のため確かめる。差は A と同じく記録する
C. Bolt の計画のとおり、B3・B4 では E2E を流さない。B4 の認証の経路の変更は `./gradlew verify` の結合テスト（ログイン・招待・登録の完了の上限切れと漏えいのテスト）で確かめ、E2E は B5 の統合の前とリリースの前に初めて流れる
X. Other (please specify)

[Answer]: A

---

## Q2: NFR 設計の承認の場の決定（書き込みの問い合わせの上限切れを `TraceAspect` と例外の変換の側でまとめて手当てする）を、どこに記録しますか？

背景: NFR 設計の2回目のレビュー（`.aidlc-reviews/nfr-design/units/u3-user-admin-api/956a484ea854c0bc/1.json` の R-01、Major）は、`security-design.md` 7.2 の洗い出しが行の排他の読み取り（E1〜E4）だけで、書き込みの問い合わせ（`UserRepository` の `updatePreferences`・`updatePasswordHashIfUnchanged`、`RefreshTokenRepository` の2本、`InvitationRepository` の送信の結果の更新、`LoginAttemptStateRepository` の `update`・`createIfAbsent`）が入っていないと指摘しました。U3 の管理の操作が `users`・`refresh_tokens` の行を持つ間に、本人のパスワードの変更・表示の設定の保存・トークンの更新がその行を待って上限切れになる経路が新しく生まれ、例外が repository の外へ出ると `TraceAspect` の出力と `GlobalExceptionHandler` の ERROR のスタックトレースに行の値が出うる、というものです（書き込みの待ちの上限切れの文に行の値が入るかは、試しのコードでは確かめていません）。承認の場では、レビューの選択肢の (2)「個別の問い合わせでなく、`TraceAspect` の出力と `GlobalExceptionHandler` の ERROR の側で、原因をつながず型の名前だけにする中央の手当てに寄せる」を選び、B4 で行うと決めました（承認のコミット 1bd45bf の件名）。ただし、承認済みの `security-design.md`・`observability-design.md`・`traceability.json` と監査の記録には、この決定が書かれていません（7.2 の冒頭の「すべて洗い出した」も直っていません）。`project.md` の Change Control（確定済みの成果物の記録の食い違いは、根拠とともに明記し、直すかを依頼者に確かめる）に当たります。基盤の面では、`TraceAspect` のある `common.observability` と `GlobalExceptionHandler` のある `common.error.web` がどちらも `packagesJudgedByTotal` の一覧にあり、手を入れると `team.md` の決まりでテストを足して一覧から外す作業が B4 に付きます（Delivery Planning では見積もっていない）。

A. 承認済みの NFR 設計の文書は書き換えない。この段の `infrastructure-specification.md` の「上流との差」に、決定の中身と出典（レビューの R-01 の選択肢 (2)、承認のコミット 1bd45bf）と、承認済みの文書に書かれていないことを明記する。基盤への影響として、(1) 設定の項目・既定値・ログの設定を変えず、`MASTERSMITH_TRACE_*` の環境変数の値によらず行の値が出ない作りにする、(2) 手を入れる `common.observability`・`common.error.web` を一覧から外して下限を満たす（今の値は B4 の計画で実測）、(3) 確かめに、書き込みの待ちの上限切れ（例: 管理の操作が行を持つ間のパスワードの変更）を TRACE と既定のレベル（INFO）の両方で起こし、ログに行の値と `MVStoreException` の文が出ないテストを足す、を書く。部品の形と置き場、E1〜E4 の個別の直しとの役割の分け方、対象にする書き込みの問い合わせの範囲は B4 のコード生成の計画で決める（推奨）
B. NFR 設計に戻り（Request Changes）、`security-design.md` 7.2・11節・12節と `observability-design.md` 3節・`traceability.json` に決定を書き直してから、この段を続ける。記録は1か所にそろうが、Construction の設計の段の決まりで5つの単位のレビューのやり直しになり、この段の再開が遅れる
C. この段の成果物には書かず、B4 のコード生成の計画で初めて書く。この段の文書は短くなるが、`cicd-pipeline.md` のカバレッジの作業（一覧から外す2つのパッケージ）とテストの見積もりが B4 の計画まで見えない
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめ:

- 基盤の設計の要点（案）は、冒頭の「決まっていること」と要点 1〜12 のとおり（配備・内部DB・接続プール・ログの設定は変えない、移行なしで戻しはイメージだけ、新しい指標・警報・ダッシュボードは足さず既存の警報の対応を実際の名前で書き observability-setup で確かめる、SLO は Unverified、負荷の試験は Build and Test が台本と手順書・performance-validation が使い捨ての環境で測定し Mailpit は要らない、verify と CI は変えず clean を付けて実測、B4 の上限切れのテストで延びる時間を記録、スモークテストの中身は deployment-pipeline、対応の手順は incident-response、README の監査と接続プールの節を直す、B3・B4 は squash の1コミット）
- Q1: A。B4 は統合の前に `./gradlew e2eTest`（010〜100）を手元で流して記録する（Mailpit を起動し、`caffeinate -i` で全体を包む）。B3 は `./gradlew verify` だけで統合する。Bolt の計画は書き換えず、差（B4 を足したこと）と理由を `cicd-pipeline.md` に記録する。
- Q2: A。NFR 設計の文書は書き換えない。`infrastructure-specification.md` の「上流との差」に、書き込みの問い合わせの上限切れを `TraceAspect` と例外の変換の側でまとめて手当てする決定と、出典（レビューの R-01 の選択肢 (2)、承認のコミット 1bd45bf）、承認済みの文書と監査の記録に書かれていないことを明記する。基盤への影響として、設定とログの設定を変えないこと、`common.observability`・`common.error.web` を一覧から外して下限を満たすこと、TRACE と INFO の両方での漏えいのテストを書く。細かい形は B4 のコード生成の計画で決める。

Does this all look correct before I generate the artifact?

- Looks correct: 上のまとめで成果物（`infrastructure-specification.md`・`monitoring-design.md`・`cicd-pipeline.md`・`traceability.json`）を書く
- Request changes: 直したい点を書いてください。直してからもう一度確かめます

[Answer]: Looks correct
