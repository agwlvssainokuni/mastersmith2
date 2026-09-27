# Infrastructure Specification — U2 利用者のプリファレンスとパスワードの変更（u2-user-preferences）

U2 の基盤の設計です。U2 が持つのは、自分のプリファレンスとパスワードの変更の API（契約 C4）、利用者の作成（C2）、ログイン・更新の応答の広げ（C3）、監査の出来事の列（C8）、スキーマの変更 V7 です。承認済みの NFR 設計（`construction/u2-user-preferences/nfr-design/`）を、既にある配備の仕組み（`compose.yaml`・`Dockerfile`・`docker/perf/compose.yaml`・`README.md`）の上でどう実現するかを書きます。既存の仕組みを正とし、この単位で変わる点だけを書きます（`aidlc/spaces/default/memory/project.md` の Deployment）。

配備先は開発者の PC 上のコンテナだけで、クラウドの基盤（IaC・検証環境・警報の通知の先）は作りません。配備先が決まったときに置き換える前提です（`project.md` の Deployment、`team.md` の Deployment）。

出典の略号: 「要点 n」「Q1」「Q2」はこの段の `infrastructure-design-questions.md`（Q1 A・Q2 A、まとめの確認は Looks correct）。NFR はこの単位の NFR 要件の枝番。`reliability-design.md` などは、この単位の NFR 設計の文書。

## 1. Deployment

| Facet | Choice | Rationale |
|---|---|---|
| Compute model | 既存のまま。WAR を同梱したイメージ `mastersmith:local`（`Dockerfile`）を compose の `app` 1台で動かす。U2 は新しいコンテナ・compose の profile を足さない | 1台・組み込みの H2 の前提（NFR6.7、`scalability-design.md` 1節）。U2 の処理はどれも同じ JVM の中で完結する（要点 1） |
| Networking topology | 既存のまま。`app` の 8080 を PC の上だけに公開し、画面と API は同じオリジン。新しいポート・外への通信は無い | U2 は外部のサービスを呼ばない。`/api/me/` は既存の「`/api/` の下は既定でログインが必要」に乗る（NFR4.1〜NFR4.4、要点 19） |
| Storage strategy | 既存のボリューム `mastersmith-data`（内部DB の H2 のファイル）。V7 で users に4列、audit_events に2列が増える | 利用者 最大 50 名の規模では伸びは小さく、ボリュームの見直しは要らない（要点 4） |
| Environments | 配備した環境（`compose.yaml`、プロジェクト `mastersmith`）1つ。一時の環境として、負荷の試験の使い捨ての環境（`docker/perf/compose.yaml`、プロジェクト `mastersmith-perf`）と、戻しの練習の環境（同じファイルをプロジェクト `mastersmith-rollback` で使う、6節）を必要なときだけ起動して消す | 配備先が決まるまで検証環境・本番環境は無い（`team.md` の Deployment）。一時の環境は本物のデータと監査ログを汚さないため別のプロジェクト名・別のボリュームにする（`project.md` の Testing Posture、Q1 A） |
| IaC approach | 基盤の定義は既存のファイル（`compose.yaml`・`docker/perf/compose.yaml`・`Dockerfile`）で持ち、U2 はどれも変えない。スキーマは Flyway の移行ファイル（V7）で持つ | 配備先が決まるまでクラウドの IaC は作らない（`project.md` の Deployment） |
| Resource sizing | 既存のまま。colima の VM は CPU 4・メモリ 6GiB（読み取りで確かめた）、`app` の上限は CPU 4・メモリ 2g（`.env`）、接続プールは上限 30・借りる待ち 5 秒、bcrypt の cost 12 | NFR5.3（成功のパスワードの変更だけが最大2本、ほかは1本で上限に収まる）、NFR6.6（cost を変えない）。VM は配備したアプリと戻しの練習の環境（各 2g）を同時に動かせる |
| Configuration and secrets | U2 は新しい環境変数・秘密情報を足さず、`.env.example` は変わらない。秘密は既存のとおり `.env`（コミットしない）だけから受け取る | 要点 2。`project.md` の Forbidden（秘密情報を設定ファイルに直接書かない、`.env` をコミットしない） |

## 2. Infrastructure Services

| Service | Role | Configuration | Notes |
|---|---|---|---|
| 内部DB（組み込みの H2） | database | 既存のまま（ファイルはボリューム `mastersmith-data`、`/app/data`）。V7 で users に displayName・language・theme・fontSize の4列（列の名前はコード生成で決める）、audit_events に target_user_id・target_invitation_id を足す（4節） | 状態を持つのは内部DB だけで、アプリのメモリに状態を持たない（NFR6.7） |
| 接続プール（HikariCP） | database（接続） | 既存のまま（上限 30、借りる待ち 5 秒） | パスワードの変更は bcrypt の間は接続を持たない（NFR5.1）。成功だけが確定の後の監査で2本目を借りる（NFR5.2）。確かめは performance-validation（`cicd-pipeline.md` 4節） |
| スキーマの移行（Flyway） | database（移行） | 既存の設定のまま（`spring.flyway.locations: classpath:db/migration`、`validate-on-migrate: true`、`ignore-migration-patterns` は置かない）。V7 を1つ足す | 1つ前の版も同じ設定で、Flyway 12 の既定で知らない新しい移行を無視する見込み。自動の結合テストで確かめる（4節、要点 3） |
| パスワードのハッシュ（bcrypt） | アプリの中の計算 | 既存の `PasswordEncoder`、`mastersmith.auth.password.bcrypt-cost` の既定 12 のまま | CPU の時間が応答時間の大半になる（`performance-design.md` 1節）。VM の CPU 4 を前提に測る |
| アクセス制御（SecurityFilterChain） | ingress の判定 | 変えない。`/api/me/` は既定のログイン必須、公開の一覧は変わらない | 要点 19、NFR4.4 |
| 監査の記録（`audit_events`） | database（追記だけ） | 既存の仕組み（AFTER_COMMIT・`REQUIRES_NEW`）のまま。PASSWORD_CHANGED と対象の2列が増える | 保存の期間は無期限のまま（要点 20） |
| 手元の監視（grafana/otel-lgtm） | 観測 | 既存の compose の profile `monitoring` のまま。U2 は設定を変えない | `monitoring-design.md` |
| 負荷の試験の使い捨ての環境 | 試験の環境 | 既存の `docker/perf/compose.yaml`（`mastersmith-perf`、`127.0.0.1:18080`）。U2 は手順（`perf/README.md`）の仮の利用者の SQL を直す | `cicd-pipeline.md` 4節、9節の I-D2 |
| 戻しの練習の環境 | 試験の環境 | 既存の `docker/perf/compose.yaml` をプロジェクト名 `mastersmith-rollback` で使い、`MASTERSMITH_IMAGE_TAG` で1つ前の版を選ぶ（6節） | Q1 A。練習の間だけ起動して消す |

## 3. Shared Infrastructure

| Shared Resource | Owner Unit | Consumer Units | Access Boundary |
|---|---|---|---|
| 内部DB の users の表 | U2（V7 の4列と、4列だけ・password_hash だけの更新の問い合わせ） | U3（登録の完了で `createUser` を契約 C2 経由で呼ぶ）、既存の Authentication | users に触れるのは `user.repository` だけ。ハッシュは `user` の外へ出さない（既存の ArchUnit） |
| 内部DB の audit_events の表 | 既存の AuditLog。対象の2列の追加と列の一覧の正は U2（C8） | U3（招待と登録の出来事の型を足す、V8） | 書き込みは `audit` の記録の仕組みだけ。追記だけで変える・消す処理を持たない |
| Flyway の移行の番号 | 既存 | U2 が V7、U3 が V8 | 前進のみ。V7 と V8 は同じ配備に入るため、バックアップと戻しの練習はまとめて1回（5節） |
| `backend/build.gradle.kts` の `packagesJudgedByTotal` | 既存（全体の決まりは `team.md` の Testing Posture） | U2 が手を入れた7〜8 パッケージを外す。後の単位も手を入れたパッケージを外す | 外すだけで増やさない（`cicd-pipeline.md` 2節） |
| `perf/k6/scenarios.js` と `perf/README.md` | 既存 | U2 がプリファレンスとパスワードの変更の場面・仮の利用者を足す。U3 も招待・登録の場面を足しうる | 使い捨ての環境だけに向ける。配備した環境には流さない |
| README の「監査ログ（U4）」「戻し方」の節 | 既存 | U2・U3 がそれぞれの記録の種類と戻しの注意を足す | 8節 |

## 4. スキーマの変更 V7 と後方互換（NFR10.1〜NFR10.4）

V7 は1つのファイルにまとめ、前進のみとします。V1〜V6 は書き換えません（`validate-on-migrate` がチェックサムの変化を検知する）。中の順序は `reliability-design.md` 6.1 のとおりです。

| 順 | 変更 | 1つ前の版への影響 |
|---|---|---|
| 1 | users に language・theme・fontSize の列を既定の値（ja・system・md）つき・必須で足す | 1つ前の版の追記でも既定の値が入る |
| 2 | users に display_name を空を許して足す | — |
| 3 | 既存の行の display_name に email を入れる | 初期管理者を含む既存の利用者に氏名が入る（NFR10.2） |
| 4 | display_name を必須にする（既定の値なし） | 1つ前の版が利用者を作ると失敗する（既知の限界 NFR10.4、9節の I-D1） |
| 5 | audit_events に target_user_id・target_invitation_id を空を許す整数で足す | 1つ前の版の監査の追記は通る |

確かめは2段です（`reliability-design.md` 6.2、Q4 A）。

| 段 | 確かめること | 場所 | 持ち主の段 |
|---|---|---|---|
| (1) 自動の結合テスト | V6 までしか知らない Flyway の `validate`・`migrate` が V7 の後の DB で失敗しない。足した4列を渡さない users の追記は失敗し、audit_events の2列を渡さない追記は通る。V6 までの DB に行を入れてから V7 を当てると値が入り、V7 は1回だけ当たる | `./gradlew verify` の `integrationTest`（組み込みの H2 の一時のファイル。コンテナを使わない）（`cicd-pipeline.md` 1節） | code-generation |
| (2) 戻しの練習 | 1つ前の版のイメージを V7・V8 の後の内部DB の複写で起動し、健全性・ログイン・トークンの更新・ログアウト・監査の記録が今までどおり動くこと。初期管理者が作られないこと（「初期管理者は既にいるため、作成しませんでした」の INFO）。Hibernate の `validate` が余分な列を許すこと | 戻しの練習の環境（6節） | deployment-pipeline（手順）・deployment-execution（実行） |

H2 は DDL をトランザクションで巻き戻せないため、V7 の途中で失敗すると一部だけ当たった状態が残りえます。失敗したときは既存の Flyway の扱いで起動が止まり、5節の第二の手（バックアップの展開）で戻します。

## 5. バックアップと戻し方（NFR10.5）

| 項目 | 設計 | 持ち主の段 |
|---|---|---|
| 配備の前のバックアップ | README の「内部DBのバックアップと戻し方」の手順（アプリを止め、ボリュームを `~/.mastersmith-backup/`（権限 700）へ tar で複写）で取る | deployment-execution |
| 戻し用のタグ | 配備の前に、いま動いているイメージに戻し用のタグ（例 `pre-user-management`）を付ける（README の「戻し方」） | deployment-execution |
| 戻しの第一の手 | V7・V8 の後の内部DB のまま、戻し用のタグのイメージで起動する（`MASTERSMITH_IMAGE_TAG=<戻し用のタグ>`、`--no-build`）。スキーマは戻さない | deployment-pipeline で手順を書く |
| 戻しの第二の手 | データが壊れた・移行が途中で失敗したときだけ、配備の前のバックアップを展開する（バックアップの後の記録は失われる） | 同上 |
| 戻すときの注意（Q2 A） | 戻すときは `.env` の `MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL` を配備のときのまま変えない。変えると1つ前の版が display_name を渡さない利用者の追記を試みて失敗し、「初期管理者は既にいるため、作成しませんでした」の誤った INFO が出る見込み（起動は続く。未確認）。空の内部DB で1つ前の版を起動するときは、V7 を当てていない内部DB を使う（NFR10.4） | deployment-pipeline で戻しの手順に書く |
| まとめて1回 | V8（U3）も同じ配備に入るため、バックアップ・戻し用のタグ・戻しの練習は V7・V8 をまとめて1回行う。V8 の後方互換の中身は U3 の基盤の設計で扱う | deployment-pipeline・deployment-execution |

## 6. 戻しの練習の環境（Q1 A）

配備の後に、次の順で行います（手順の書き起こしは deployment-pipeline、実行は deployment-execution）。

1. 配備した `app` を止め、README の手順で配備の後（V7・V8 の後）の内部DB のバックアップを `~/.mastersmith-backup/` に取り、`app` を起動し直す（配備したアプリは練習の間も動かしたまま）。
2. 配備の `.env` を、中身を表示せずにリポジトリの外（ホームの下、権限 700）へ複写する（本番の戻しと同じ設定で起動するため）。複写のパスを `MASTERSMITH_PERF_ENV_FILE` で渡し、`MASTERSMITH_CONTAINER_CPUS=4`・`MASTERSMITH_CONTAINER_MEMORY=2g` を export する（`-f` で指定する compose は `.env` を読まない）。
3. `docker compose -p mastersmith-rollback -f docker/perf/compose.yaml create app` でボリュームを作り、1 のバックアップを `mastersmith-rollback_perf-data` に展開して持ち主を 10001 にそろえる（README の展開の手順と同じ形）。
4. `MASTERSMITH_IMAGE_TAG=<戻し用のタグ> docker compose -p mastersmith-rollback -f docker/perf/compose.yaml up -d --wait app` で1つ前の版を `127.0.0.1:18080` に起動する（健全性は compose の healthcheck の `/actuator/health` の 200）。
5. 依頼者が `http://localhost:18080/` でログイン・トークンの更新（画面の再読み込み）・ログアウトを行う。AI は複写の側のログ（「初期管理者は既にいるため、作成しませんでした」、ERROR が無いこと）と、環境を止めた後に複写の H2 を読み取り（`ACCESS_MODE_DATA=r`）で開いた audit_events の行（LOGIN_SUCCEEDED・LOGGED_OUT の件数）で裏付ける。個人に関する値は表示せず、件数と有無だけを確かめる（`project.md` の Corrections）。
6. 確かめの結果を記録してから、`docker compose -p mastersmith-rollback -f docker/perf/compose.yaml down -v` で消し、複写した `.env` と練習に使ったバックアップの複写を消す（配備の前のバックアップは残す）。

確かめの操作は複写の監査にだけ残り、本物の監査ログを汚しません。署名鍵が同じため、練習の環境で出したトークンは配備したアプリでも通りえますが、練習の利用者は本物の管理者だけで、ログアウトで更新のトークンを無効にしてから消します。

## 7. 秘密・個人に関する値の扱い

| 対象 | 扱い | 出典 |
|---|---|---|
| バックアップと複写（パスワードのハッシュ、監査の接続元 IP・User-Agent・メールアドレスを含む） | リポジトリの外の `~/.mastersmith-backup/`（権限 700）に置き、中身を開かず、コミットしない。練習に使った複写は終わったら消す | 要点 21、`project.md` の Forbidden |
| 配備の `.env` の複写（Q1 A） | 中身を表示せずにホームの下（権限 700）へ複写し、練習の後に消す。`mktemp -d` の一時ディレクトリにしない（colima の VM から見えない） | `project.md` の Deployment・Corrections |
| パスワードの要る操作 | 依頼者が行い、AI は監査とログで裏付ける | `project.md` の Corrections |
| 負荷の試験の仮の資格情報 | 既存の手順どおり乱数で作り、リポジトリの外の一時のファイルに置いて表示しない（`perf/README.md`） | `project.md` の Testing Posture |
| アプリのログ・監査・トレース | U2 はパスワード・ハッシュ・トークン・メールアドレスを出さない。外部エクスポートは既定で無効のまま | NFR2.1〜NFR2.4、`monitoring-design.md` 4節 |

## 8. README に足すこと

| 節 | 足すこと |
|---|---|
| 監査ログ（U4） | 記録の種類に PASSWORD_CHANGED（結果と失敗の理由 CURRENT_PASSWORD_MISMATCH）と、対象の利用者の列（target_user_id・target_invitation_id）を足す。保存の期間（無期限）は変えない |
| 新しい節「利用者のプリファレンスとパスワードの変更（U2）」 | 3本の API（GET・PUT `/api/me/preferences`、POST `/api/me/password`）、今のパスワードの誤りを制限しないことと見つけ方の問い合わせ（`monitoring-design.md` 6節）、ログ・監査にパスワード・ハッシュ・メールアドレスを出さないこと。環境変数は増えない |
| 戻し方 | 5節の「戻すときの注意」（初期管理者のメールアドレスを変えない、空の内部DB の扱い）。書き起こしは deployment-pipeline |
| `perf/README.md` の手順 2 | 仮の利用者の SQL に display_name を足し、パスワードの変更の専用の利用者を足す（`cicd-pipeline.md` 4節） |

## 9. 上流との差

| ID | 上流 | 上流の記載 | この段の設計 | 理由と扱い |
|---|---|---|---|---|
| I-D1 | NFR10.4（`construction/u2-user-preferences/nfr-requirements/reliability-requirements.md`）、`nfr-design/reliability-design.md` 6.2 | 1つ前の版が利用者を作るのは、利用者が1人もいないときの初期管理者の作成だけ | 既存の `backend/src/main/java/cherry/mastersmith/user/service/InitialAdminInitializer.java` は、設定（メールアドレスとパスワード）が正しいとき、設定したメールアドレスの利用者がいなければ作る（`existsByEmail`）。戻すときに `.env` の初期管理者のメールアドレスが今いる利用者と違うと、display_name を渡さない追記が V7 の必須の列で失敗し、受け止めた `DataIntegrityViolationException` で「既にいるため、作成しませんでした」の INFO が出る見込み（起動は続く。未確認） | 依頼者の決定（Q2 A）。承認済みの文書は書き換えない。戻しの手順に「メールアドレスを変えない」と変えたときの症状を書き（5節）、戻しの練習で「既にいる」の INFO を確かめる（6節の 5）。配備した環境では初期管理者をこのメールアドレスで作ったため、変えなければ起きない |
| I-D2 | `nfr-design/performance-design.md` 7節（仮の利用者 10 名以上） | 仮の利用者の用意の手順は書いていない | 既存の `perf/README.md` の手順 2 の SQL（email・password_hash・admin_flag・created_at だけを入れる）は V7 の後に display_name が無く失敗するため、display_name（メールアドレスと同じ値）を足す。パスワードの変更の成功の場面には専用の仮の利用者（例 `perf-pw01`〜`perf-pw10`）を足す | 既存の手順が V7 で壊れるための追加。承認済みの設計の「同じ利用者を同時に使わない」を、ほかの場面の利用者のパスワードを変えない形で具体にした |
| I-D3 | `nfr-design/observability-design.md` 1節 | Prometheus では `http_server_requests_seconds_*` | 手元の監視（OTLP）での実際の名前は `http_server_requests_milliseconds_*`（既存のダッシュボードと警報の式）。`monitoring-design.md` は実際の名前で書く | 承認済みの文書は書き換えない。`monitoring-design.md` 8節にも記録 |
| I-D4 | `nfr-design/reliability-design.md` 6.2 の (2) | 1つ前の版のイメージを V7 の後の内部DB の複写で起動する | 複写は V7・V8 の後に取り、既存の `docker/perf/compose.yaml` を別のプロジェクト名で使い、配備の `.env` の複写で起動する。V8（U3）とまとめて1回の練習にする | 依頼者の決定（Q1 A）。V7 と V8 は同じ配備に入るため。前例は Intent 260925-storage-memory-fixes の deployment-execution（前の版のイメージを一時のボリュームで起動して確かめた） |
