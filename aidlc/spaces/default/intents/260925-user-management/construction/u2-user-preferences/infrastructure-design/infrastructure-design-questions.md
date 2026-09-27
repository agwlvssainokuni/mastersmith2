# Infrastructure Design の質問 — u2-user-preferences（利用者のプリファレンスとパスワードの変更、service）

U2 は、自分のプリファレンスとパスワードの変更の API（契約 C4）、利用者の作成（C2）、ログイン・更新の応答の広げ（C3）、監査の出来事の列（C8）と、スキーマの変更 V7 を持つ service の単位です。service の単位のため、この段の成果物は `infrastructure-specification.md`・`monitoring-design.md`・`cicd-pipeline.md`・`traceability.json` です（段の定義の `produces_kinds`）。

配備先は開発者の PC 上のコンテナ（colima、`compose.yaml`、`Dockerfile`、実行可能 WAR）だけで、クラウドの基盤は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。基盤の中身のほとんどは、承認済みの NFR 要件・NFR 設計と既存の仕組みで決まっています。そのため、まず基盤の設計の要点（案）を示し、上流から1つに決まらない2点（戻しの練習の環境、承認済みの NFR10.4 の前提の食い違いの扱い）だけを質問にしました。

読んだ上流:

- この単位の承認済みの NFR 設計 `aidlc/spaces/default/intents/260925-user-management/construction/u2-user-preferences/nfr-design/`（`performance-design.md`・`security-design.md`・`scalability-design.md`・`reliability-design.md`・`observability-design.md`・`logical-components.md`・`traceability.json`・`nfr-design-questions.md`（Q1〜Q4 すべて A））と、承認の場の決定（監査ログの `DECISION_RECORDED`、2026-09-27: U2 の R-01 は B2 のテストに本人の行が消えた 401、R-02 は B2 の計画に Hibernate の `validate` の見込みが外れたときの危険を書く。上流との差は受け入れ。どちらもコード生成で拾うため、この段の作りは変わらない）
- この単位の承認済みの NFR 要件 `construction/u2-user-preferences/nfr-requirements/`（NFR5.1・NFR5.2・NFR6.1〜NFR6.9・NFR9.4〜NFR9.8・NFR10.1〜NFR10.5 ほか）
- この単位の承認済みの機能設計 `construction/u2-user-preferences/functional-design/functional-spec.md`（2.6 の V7 の方針、6節の後の段へ渡すこと）
- 部品の一覧 `inception/domain-design/components.md`（UserAccount・AuditLog）、契約 `inception/contract-design/contract-summary.md`（C2・C3・C4・C8）
- 決まり `aidlc/spaces/default/memory/org.md`・`team.md`・`project.md`・`phases/construction.md`
- 既存の仕組み: `README.md`（「コンテナでの起動と確認」「戻し方」「内部DBのバックアップと戻し方」「スキーマの変更（Flyway）」「監査ログ（U4）」「手元の監視（Grafana）」）、`compose.yaml`、`docker/perf/compose.yaml`（別のプロジェクト名・別のボリューム・`127.0.0.1:18080`・`MASTERSMITH_IMAGE_TAG` の口）、`perf/README.md`・`perf/k6/scenarios.js`、`docker/monitoring/provisioning/alerting/mastersmith.yaml`（`ms-app-absent`・`ms-5xx-ratio`・`ms-error-logs`・`ms-audit-fail`・`ms-login-p95`）・`docker/monitoring/dashboards/mastersmith-overview.json`、`.github/workflows/ci.yml`、`backend/build.gradle.kts`（`packagesJudgedByTotal` の 22 パッケージ、`jacocoTestCoverageVerification`）、`backend/src/main/resources/application.yaml`（`spring.flyway` は `validate-on-migrate: true` だけで、`ignore-migration-patterns` は置いていない）、`backend/src/main/java/cherry/mastersmith/user/service/InitialAdminInitializer.java`
- この PC の実行環境（読み取りだけで確かめた）: colima の VM は CPU 4・メモリ 6GiB で動いている。イメージ `mastersmith` のタグは `local` と戻し用のタグ（`pre-storage-memory` など）がある。ボリューム `mastersmith_mastersmith-data` があり、`~/.mastersmith-backup/`（権限 700）に前のバックアップが 4 件ある

## Infrastructure Design の要点（案）

### 配備と内部DB（`infrastructure-specification.md`）

1. **配備の形は変えない**: WAR を同梱したイメージ `mastersmith:local` を compose の `app` 1台で動かし、内部DB（組み込みの H2）はボリューム `mastersmith-data` に置く。コンテナの上限（CPU 4・メモリ 2g、`.env`）、接続プール（上限 30・借りる待ち 5 秒）も変えない。U2 は新しいコンテナ・ボリューム・ポート・compose の profile を足さない（`logical-components.md` 4節、NFR6.7）。
2. **設定と秘密**: U2 は新しい環境変数を足さず、`.env.example` は変わらない。bcrypt の cost は既定の 12 のまま（`mastersmith.auth.password.bcrypt-cost`、NFR6.6）。新しい秘密情報は無い。
3. **スキーマの変更 V7**: 1つのファイルにまとめ、前進のみ（V1〜V6 は書き換えない）。中の順序は `reliability-design.md` 6.1 のとおり（language・theme・fontSize を既定の値つきで足す → display_name を空を許して足す → 既存の行に email を入れる → 必須にする → 監査の2列を空を許して足す）。Flyway の設定は変えない。1つ前の版も `ignore-migration-patterns` を置いておらず Flyway 12 の既定（知らない新しい移行を無視する）で動く見込みで、これを自動の結合テストで確かめる（要点 9）。
4. **保存量**: 利用者の4列（氏名は最大 254 コードポイント）と監査の2列（整数）だけで、利用者 最大 50 名の規模では内部DB のファイルの伸びは小さい。ボリュームの見直しは要らない。
5. **バックアップと戻し方（NFR10.5）**: 要否は決まっている。配備の前に README の「内部DBのバックアップと戻し方」の手順でバックアップを取り、いま動いているイメージに戻し用のタグ（例 `pre-user-management`）を付ける。戻しの第一の手は「V7 の後の内部DB のまま1つ前の版のイメージで起動」、第二の手は「バックアップの展開」（H2 は DDL を巻き戻せず、V7 の途中の失敗で一部だけ当たった状態が残りうるため）。手順の書き起こしは deployment-pipeline、実行は deployment-execution。V8（U3）も同じ配備に入るため、バックアップと戻しの練習は V7・V8 をまとめて1回行う（V8 の後方互換は U3 の基盤の設計で扱う）。
6. **戻しの練習の環境**（NFR10.3・NFR10.4 の実地の確かめ、`reliability-design.md` 6.2 の (2)）: → Q1。
7. **NFR10.4 の前提**: 既存の `InitialAdminInitializer` は「利用者が1人もいないとき」ではなく「設定したメールアドレスの利用者がいないとき」に作る。扱いは → Q2。

### 検査の流れと CI（`cicd-pipeline.md`）

8. **入口と段の並びは変えない**: `./gradlew verify` と `.github/workflows/ci.yml` はそのまま。U2 の単体テスト（jqwik の性質ベースのテストを含む）と結合テスト（`*IT`）は既存の `test`・`integrationTest` の段で動く。新しい依存・新しい検査の道具は無い（`tech-stack-decisions.md`）。SpotBugs の除外は足さない（NFR9.3）。
9. **V7 の自動の確かめ**（`reliability-design.md` 6.2 の (1)）: V6 までの移行の複写をテストの資源に置き、組み込みの H2 の一時のファイルで「V6 までしか知らない Flyway の `validate`・`migrate` が失敗しない」「足した4列を渡さない利用者の追記は失敗し、監査の2列を渡さない追記は通る」「V7 の値と1回だけの適用」を確かめる。コンテナを使わないため、コンテナの実行環境が無いときに飛ばしてよいテスト（対象DB のテスト）には入らない。
10. **`packagesJudgedByTotal` から外す作業の CI への影響**: 手を入れる `user.domain`・`user.repository`・`user.service`・`audit.domain`・`audit.service`・`auth.service`・`auth.web`（実際に手を入れたら `audit.repository` も）を一覧から外し、パッケージごとの下限（行 80%・分岐 70%）の対象に戻す（`team.md` の Testing Posture、NFR9.6）。新しい `user.web` は自動で対象になる。外したパッケージが1つでも下回ると `jacocoTestCoverageVerification` で `verify` が失敗し、CI も同じ判定で失敗する（`ci.yml` は変えない）。そのため統合の前に、手元で colima の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡し、`:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測して値を記録する（`project.md` の Testing Posture）。前に単独で下回っていた `audit.service`（行 77.2%）はテストを足して上げる。一覧と計測の除外は増やさない。
11. **E2E**: U2 は画面を持たず、`./gradlew e2eTest` に流れを足さない。パスワードの変更の画面の確かめは U7（B5）が持つ。

### 性能の測り方（performance-validation へ渡す、NFR5.2・NFR6.1〜NFR6.5）

12. **k6 の場面**: `perf/k6/scenarios.js` に、プリファレンスの取得・保存（成功・入力の誤り）、パスワードの変更の成功・今のパスワードの誤り・入力の誤りの場面を足す（名前はコード生成・Build and Test で決める）。どれも同時 10 で、p95 の判定は変更の成功だけ 2 秒、ほかは 1 秒。既存の `loginSuccess`・`refresh` を流し直して NFR6.5 を確かめる。場面の用意と手順書は Build and Test、測定は performance-validation（`performance-requirements.md` の前提）。
13. **仮の利用者の用意**: `perf/README.md` の手順 2 の SQL は `users` に email・password_hash・admin_flag・created_at だけを入れるため、V7 の後は display_name が無く失敗する。この手順に display_name（メールアドレスと同じ値）を足す（language・theme・fontSize は既定の値で入る）。パスワードの変更の成功の場面には専用の仮の利用者（例 `perf-pw01`〜`perf-pw10`）を足し、VU ごとに1人を当てて変更の前後のパスワードを交互に使う。ほかの場面の利用者のパスワードは変えず、1人を同時に2つの場面で使わない（`performance-design.md` 7節）。同時のログインの前に1人ずつログインさせてロックの状態の行を作る（`project.md` の Testing Posture）。
14. **2本目の接続の確かめ（NFR5.2）**: 使い捨ての環境にだけ `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics` を渡し、hikaricp の待ちの時間切れの累計が 0、借りるまでの待ちの最大が 5 秒より十分小さいことを見る。流した成功の件数と監査の PASSWORD_CHANGED・SUCCESS の件数の突き合わせは、使い捨ての環境を消す前に H2 の道具（読み取り）で数える。`caffeinate -i` を付けて流し、測る間は配備したアプリを止める（`perf/README.md`、`project.md` の Testing Posture）。

### 監視（`monitoring-design.md`）

15. **新しい指標・警報・ダッシュボードは足さない（NFR6.8・NFR6.9）**: 3本の API は既存の HTTP の指標に入り、既存の警報がそのまま効く（`ms-5xx-ratio`・`ms-error-logs`、PASSWORD_CHANGED の記録の失敗は `ms-audit-fail`）。`ms-login-p95` は応答の広げの後もそのまま使う。
16. **指標の名前の差**: 手元の監視（OTLP で送る）での実際の名前は、既存のダッシュボードの式のとおり `http_server_requests_milliseconds_*` で、`observability-design.md` 1節の `http_server_requests_seconds_*`（Prometheus の形）とは違う。`monitoring-design.md` は実際の名前で書き、差を記録する（承認済みの文書は書き換えない）。`uri` のラベルの値（`/api/me/preferences`・`/api/me/password`）は observability-setup で起動して確かめる（`project.md` の Corrections）。
17. **SLI・SLO**: 手元の監視を常に動かしていない間は Unverified。performance-validation の値を基準の値として記録し、配備先が決まったら `uri` ごとの p95（パスワードの変更は status 204 と 400 を分ける）を SLI にする（`project.md` の Deployment）。
18. **総当たりの見つけ方（残る危険 R1）**: `observability-design.md` 3節の問い合わせを、README の「監査ログの確かめ方」（複写を読み取りで開く）に並べる。定期の実行や警報にはしない。列の名前は observability-setup で実際に流して確かめる。

### 秘密・監査・個人に関する値（devsecops・compliance の観点）

19. **アクセス制御は変えない**: `/api/me/` は既存の「`/api/` の下は既定でログインが必要」にそのまま乗り、公開の決まり・Origin の確かめ・CSRF の設定は足さない（NFR4.1〜NFR4.4）。README の「API のアクセス制御（U3）」の公開の一覧は変わらない。
20. **README に足すこと**: 「監査ログ（U4）」の記録の種類に PASSWORD_CHANGED と対象の利用者の列を足し、U2 の API の節（3本の API、今のパスワードの誤りを制限しないことと見つけ方、ログ・監査にパスワード・ハッシュ・メールアドレスを出さないこと）を置く。保存の期間（無期限）は変えない。
21. **バックアップと複写の扱い**: バックアップと練習に使う複写は、パスワードのハッシュ・監査ログ（接続元 IP・User-Agent・メールアドレス）を含むため、リポジトリの外の `~/.mastersmith-backup/`（権限 700）に置き、中身を開かず、コミットしない。使い終えた複写は消す（`project.md` の Forbidden・Corrections）。

---

## Q1. 戻しの練習（1つ前の版を V7 の後の内部DB で起動する確かめ）を、どの環境で、誰がどう確かめますか？

理由: 承認済みの NFR 設計（Q4 A、`reliability-design.md` 6.2 の (2)）で、deployment-execution の戻しの練習として「1つ前の版のイメージを、V7 を当てた後の内部DB の複写で起動し、健全性・ログイン・トークンの更新・監査の記録と、初期管理者が作られないことを確かめる」と決まり、基盤の設計はその環境だけを扱うことになっています（R-D2）。複写は配備の後（V7・V8 の後）でないと作れません。複写は本物の利用者のハッシュと監査ログを含み、ログインには本物の資格情報が要るため、パスワードの要る操作は依頼者が行い、AI は監査とログで裏付ける決まりです（`project.md` の Corrections）。既存の `docker/perf/compose.yaml` は、別のプロジェクト名・別のボリューム・`127.0.0.1:18080` で、`MASTERSMITH_IMAGE_TAG` で版を選んで起動でき、VM（6GiB）は配備したアプリ（2g）と同時に動かせます。

A. 配備の後に取ったバックアップ（V7・V8 の後）を、`docker/perf/compose.yaml` を別のプロジェクト名（例 `mastersmith-rollback`）で使うボリュームに展開し、`MASTERSMITH_IMAGE_TAG=<戻し用のタグ>` で1つ前の版を起動する。環境ファイルは、本番の戻しと同じ条件にするため配備の `.env` を中身を表示せずにリポジトリの外（ホームの下、権限 700）へ複写して使う。ログイン・トークンの更新・ログアウトは依頼者が `http://localhost:18080/` で行い、AI は複写の側のログ（「初期管理者は既にいるため、作成しませんでした」、ERROR が無いこと）と、複写の H2 を読み取りで開いた監査の行で裏付ける。配備したアプリは動かしたままで、確かめの操作は複写の監査にだけ残る（本物の監査ログを汚さない）。終わったら `down -v` で消し、複写した `.env` とバックアップの複写を消す（推奨）
B. 本物のデータを使わず、使い捨ての環境で新しい版を仮の署名鍵・仮の初期管理者で起動して V7・V8 まで当て、止めて同じボリュームで1つ前の版を起動する。ログイン・更新・監査は仮の資格情報で AI が確かめられ、秘密の値に触れない。代わりに、承認済みの設計（配備した内部DB の複写）と違い、本物の内部DB に特有の状態（V1〜V6 の実際の行、DSL の履歴など）での起動は確かめない（差として記録する）
C. 配備した環境そのものを README の「戻し方」の手順どおり1つ前の版に戻して確かめ、終わったら新しい版に戻す。本当の戻しと同じ手順を練習できる代わりに、アプリが2回止まり、確かめのログイン・ログアウトが本物の監査ログに残る（消せない）
X. Other (please specify)

[Answer]: A

## Q2. 承認済みの NFR10.4 の前提（1つ前の版が利用者を作るのは、利用者が1人もいないときだけ）と、既存のコードの食い違いをどう扱いますか？

理由: 既存の `InitialAdminInitializer` は、初期管理者の設定（メールアドレスとパスワード）が正しいとき、**設定したメールアドレスの利用者がいなければ**作ります（`existsByEmail`）。利用者が1人もいないときだけではありません。そのため、戻したときに `.env` の `MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL` が今いる利用者と違う値だと、1つ前の版は display_name を渡さない追記を行い、V7 の必須の列で失敗します。失敗の例外（`DataIntegrityViolationException`）は受け止められて「初期管理者は既にいるため、作成しませんでした」の INFO が出て起動は続く見込みです（未確認）。配備した環境では初期管理者をこのメールアドレスで作ったため、変えなければ起きません。承認済みの文書（`nfr-requirements/reliability-requirements.md` の NFR10.4、`nfr-design/reliability-design.md` 6.2）の前提の書き方が、コードと食い違っています（`project.md` の Change Control: 食い違いは根拠とともに明記し、直すかを確かめる）。

A. 承認済みの文書は書き換えず、`infrastructure-specification.md` の上流との差に食い違いと根拠を記録する。戻しの手順（deployment-pipeline）に「戻すときは `.env` の初期管理者のメールアドレスを配備のときのまま変えない」と、変えた場合の症状（誤った INFO が出て作られない）を書き、Q1 の練習で「既にいる」の INFO を確かめる。自動の結合テスト（要点 9）が固定する既知の限界（display_name を渡さない追記は失敗する）はそのまま（推奨）
B. A に加えて、承認済みの NFR10.4 と `reliability-design.md` 6.2 の前提の文を直す（前の段の承認済みの文書のため、依頼者の Request Changes の手順を経て直し、レビューをやり直す）
C. 作りを変え、display_name に既定の値を持たせる（または空を許す）。1つ前の版が利用者を作っても失敗しなくなる代わりに、承認済みの BR9.1 と NFR 設計の Q4 A（既定の値なしの必須の列）を変えることになり、機能設計・NFR 設計の見直しが要る
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめ（回答の後に書き直します）:

- 基盤の設計の要点（案）は冒頭の 21 件のとおり（配備の形・設定・秘密は変えない、V7 は前進のみで Flyway の設定は変えない、配備の前のバックアップと戻し用のタグ、V7・V8 をまとめて1回の練習、`./gradlew verify` と CI は変えず V7 の自動の確かめは組み込みの H2 で行う、`packagesJudgedByTotal` から手を入れた 7〜8 パッケージを外して手元で実測してから統合、k6 の場面と仮の利用者の手順（display_name を足す・パスワードの変更の専用の利用者）、新しい指標・警報・ダッシュボードは足さず実際の指標の名前で書く、SLO は Unverified、総当たりの問い合わせは README に置く、アクセス制御は変えない、README の監査と U2 の API の節、バックアップと複写はリポジトリの外で扱う）
- Q1: A — 配備の後に取ったバックアップ（V7・V8 の後）を、`docker/perf/compose.yaml` を別のプロジェクト名（例 `mastersmith-rollback`）で使うボリュームに展開し、1つ前の版を `127.0.0.1:18080` で起動する。環境ファイルは配備の `.env` を中身を表示せずにリポジトリの外（ホームの下、権限 700）へ複写して使う。ログイン・トークンの更新・ログアウトは依頼者が行い、AI は複写の側のログと監査の行で裏付ける。配備したアプリは動かしたままで、本物の監査ログを汚さない。終わったら `down -v` で消し、複写した `.env` とバックアップの複写を消す
- Q2: A — 承認済みの NFR10.4 と `reliability-design.md` 6.2 は書き換えず、`infrastructure-specification.md` の上流との差に食い違い（既存のコードは設定したメールアドレスの利用者がいなければ作る）と根拠を記録する。戻しの手順（deployment-pipeline）に「`.env` の初期管理者のメールアドレスを配備のときのまま変えない」と、変えたときの症状を書き、Q1 の練習で「既にいる」の INFO を確かめる

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
