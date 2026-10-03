# 環境の確かめの結果（validation-report）

Intent 260930-user-admin の配備先の環境（開発者の PC 上のコンテナ）を、承認済みの設計の値と1つずつ突き合わせた結果です。確かめは 2026-10-03 に読み取りで行い、そのうえで依頼者が承認した片付け（Q1: B）だけを行いました。アプリの入れ替え・戻し先のタグ付け・スモークテストは、次の Deployment Execution の段で行います（`operation/deployment-pipeline/cd-config.md` の 3節）。

**判定: 合格**（設計の値を満たせない点は無し。食い違いは無く、記録に残す気づきが 4 件。5節）

この段の役割（基盤）にセキュリティ（devsecops）とコンプライアンスの観点を合わせて確かめました。

## 1. 設計の値との突き合わせ

| # | 項目 | 設計の値（出典） | 実際の値（確かめ方） | 判定 |
|---|---|---|---|---|
| E1 | VM の資源 | CPU 4・メモリ 6GiB（U3 の `infrastructure-specification.md` 1節、`cd-config.md` 1節） | CPU 4・メモリ 6GiB・ディスク 100GiB、Running（`colima list`） | 満たす |
| E2 | アプリの資源の上限 | CPU 4・メモリ 2g（U3 の 1節、`cd-config.md` 1節、`compose.yaml` の既定） | CPU 4（NanoCpus 4,000,000,000）・メモリ 2,147,483,648 B（`docker inspect`、`docker compose config` の `cpus`・`mem_limit`） | 満たす |
| E3 | 動いている版 | `mastersmith:local`（`c77c1bb247f9`、healthy）。前の Intent で配備した版（`cd-config.md` 1節） | イメージ ID `c77c1bb247f9…`、healthy、2026-09-30 23:50 JST 起動、再起動 0 回、OOMKilled なし（`docker inspect`） | 満たす |
| E4 | 実行の形 | 既存の `Dockerfile`・`compose.yaml` の `app` 1台。新しいコンテナ・profile・ボリュームを足さない（U3・U4・U5 の 1節） | `mastersmith` のサービスは `app`・`otel-collector`・`lgtm`・`mailpit`・`targetdb-*` のまま。`compose.yaml`・`Dockerfile` は `31b980b` から差が無い（`git diff`） | 満たす |
| E5 | 番号の公開 | `app` の 8080 は PC の `127.0.0.1:8080` だけ（U3 の 1節）。Mailpit は `127.0.0.1` の 8025・1025（U4 の 4節・U5 の 5節） | `PortBindings` はどれも `HostIp 127.0.0.1`。PC で待ち受けているのは 8080・8025・1025 の3つだけで、どれも `127.0.0.1`（`docker inspect`・`lsof`） | 満たす |
| E6 | 内部DB の置き場 | 既存のボリューム `mastersmith-data`、`/app/data`（U3 の 2節） | `mastersmith_mastersmith-data` → `/app/data`（読み書き）、65.54kB | 満たす |
| E7 | スキーマ | 今は V8。配備で V9（`users.suspended`）が前進のみで当たる（`cd-config.md` 1節・2節） | `backend/src/main/resources/` の差は `db/migration/V9__u1_user_suspension.sql` の追加だけ。今のアプリは前の版のため V9 は未適用 | 予定どおり（当てるのは Deployment Execution） |
| E8 | 設定の項目 | 新しい設定・環境変数・秘密を足さない。`.env` を変えない（U3 の 3節、U4・U5 の 1節、`cd-config.md` 2節） | `.env` は 16 項目で、前の Intent の配備で足した4項目（ベース URL・SMTP の2つ・差出人）を含む。最後の更新は 2026-09-30 23:48 で、この Intent の間に変わっていない。`.env.example`・`application*.yaml` は差が無い | 満たす |
| E9 | 接続プール | 上限 30（`MASTERSMITH_DB_MAXIMUM_POOL_SIZE` の既定）、配備したアプリでは変えない（U3 の 3節） | `.env` にこの項目は無く、既定の 30 が効く | 満たす |
| E10 | 公開する管理の口 | `management.endpoints.web.exposure.include` は health だけ。配備したアプリで `/actuator/metrics` を公開しない（U3 の 3節・7節） | `/actuator/health` は 200・`{"status":"UP"}`、`/actuator/metrics` は 404（`curl`） | 満たす |
| E11 | ヘルスチェック | bash の `/dev/tcp` で `/actuator/health` の 200（`compose.yaml`、U4 の 1節「変えない」） | 間隔 30 秒・時間切れ 5 秒・3回・猶予 40 秒・始めの間隔 2 秒（`docker inspect` の `Healthcheck`） | 満たす |
| E12 | JVM の設定 | `MaxRAMPercentage=50`・`-Dh2.compactThreads=1`・`Asia/Tokyo`（`Dockerfile`、`project.md` の Tech Stack） | ENTRYPOINT にすべてある。`MASTERSMITH_JAVA_OPTIONS` は `.env` に無く空 | 満たす |
| E13 | 一時の環境 | 負荷の試験は `docker/perf/compose.yaml`（`127.0.0.1:18080`、上限 CPU 4・メモリ 2g）、E2E は WAR を直接起動（番号 18081）（U3 の 1節・2節、U5 の 3節） | `docker/perf/compose.yaml` は差が無く、一時の環境ファイルの場所を仮に与えると `config --quiet` が通る。18080・18081 は空き | 満たす |
| E14 | Mailpit | profile `mail`、版はダイジェスト固定、受けたメールを外へ中継しない（U4 の 4節・U5 の 5節） | `axllent/mailpit:v1.31.2@sha256:74d609a4…`、healthy、上限 256MiB、ボリュームなし | 満たす |
| E15 | 見本の対象DB | `targetdb-postgres` は今回触らない（`cd-config.md` 1節） | Up、PC に番号を開かない、上限 512MiB | 満たす |
| E16 | 同時に動かせる量 | アプリ 2g・Mailpit 256m・見本の対象DB 512m を VM の 6GiB で動かす（`compose.yaml` の注記） | 動いている3つの上限の合計は約 2.75GiB。入れ替えでも変わらない | 満たす |
| E17 | compose の設定が壊れていない | — | `docker compose --profile targetdb-postgres --profile mail --profile monitoring --profile observability config --quiet` が通る | 満たす |
| E18 | 入れ替えに使うイメージ | 土台 `eclipse-temurin:25.0.4_7-jre-noble`（`Dockerfile`） | 手元にある | 満たす |
| E19 | 配備に要るディスクの空き | イメージの作り直し（約 650MB）とビルドに数 GB（質問 Q1 の前提） | PC の空き 30GiB。Docker の中は VM のディスク 100GiB の中で、イメージ・ボリュームの合計は約 32GB | 満たす |

## 2. セキュリティの確かめ（devsecops の観点）

| # | 確かめ | 確かめ方 | 結果 |
|---|---|---|---|
| S1 | PC に開く番号は `127.0.0.1` だけ。見本の対象DB は開かない | `docker inspect` の `PortBindings`、`lsof -iTCP -sTCP:LISTEN` | 満たす（8080・8025・1025 だけ。3000・18080・18081・5432 は待ち受けなし） |
| S2 | 秘密を含む設定のファイルは Git の管理外で、権限 600 | `ls -l`・`git check-ignore -v` | 満たす（`.env`・`.env.targetdb` とも 600。`.gitignore` の `.env`・`.env.*`） |
| S3 | 秘密がイメージに焼き込まれていない | `docker image inspect` の `Config.Env` の名前、`docker history --no-trunc` の命令 | 満たす（イメージの環境変数は `PATH`・`JAVA_HOME`・`JAVA_VERSION`・`LANG`・`LANGUAGE`・`LC_ALL` だけ。ビルドの命令に `PASSWORD`・`SIGNING_KEY`・`SECRET`・`TOKEN` の語は 0 件） |
| S4 | 秘密と初期管理者のメールアドレスがアプリのログに出ていない | `.env` と `.env.targetdb` の値をシェルの変数に読み、表示せずに `docker logs` の中の件数だけを数えた（92 行） | 満たす（初期管理者のパスワード・署名鍵・対象DB のパスワード・見本の対象DB の2つのパスワード・初期管理者のメールアドレスとも 0 件。初期管理者の INFO は伏せ字のキー `maskedEmail` で 2 行） |
| S5 | 見本の対象DB の管理者のパスワードがアプリのコンテナに渡っていない | アプリのコンテナの環境変数の名前 | 満たす（`MASTERSMITH_SAMPLE_*` は 0 件。`.env.targetdb` は `targetdb-*` だけが読む） |
| S6 | アプリのコンテナは root で動かず、特権を持たない | `docker inspect` の `Config.User`・`Privileged` | 満たす（`10001:10001`、Privileged false） |
| S7 | 配備したアプリで指標の口を開いていない | `curl /actuator/metrics` | 満たす（404） |
| S8 | 秘密の値を画面・記録に出さない | この段の出力 | 満たす（`.env` は開かず、項目の名前ごとの件数だけを数えた。`docker compose config` は `--quiet` と、値を除いた項目だけの出力で使った） |

- 気づき: `docker compose config` を素のまま流すと、`env_file` が展開され、`.env` と `.env.targetdb` の秘密の値がそのまま出力に入ります（見本の対象DB の2つのパスワードが出力の中にあることを、件数だけで確かめた）。設定の確かめで出力を記録・共有するときは、`--quiet` か、値を除いた項目だけにします。今の README・手順書の書き方を変える必要は無く、この段の記録として残します。
- 要求の回数の制限が無いこと（U3 の `infrastructure-specification.md` 7節の R1）は、PC の `127.0.0.1` に限って公開する今の形で受け入れたままです。社外に公開する配備先が決まったときに見直します。

## 3. 個人に関する値の扱い（コンプライアンスの観点）

- 配備した内部DB（`mastersmith_mastersmith-data`）には、利用者のメールアドレス・氏名・パスワードのハッシュ値・監査の記録が入ります。この Intent の配備で加わるのは、停止の印（`users.suspended`）と、管理の操作の監査の行（種類5つ・理由の値）です。新しい種類の個人に関する値は加わりません。
- この Intent はバックアップを取らない（`cd-config.md` の 2節、Deployment Pipeline の Q1: B）ため、`~/.mastersmith-backup/`（権限 700、6 件）は増えません。前の Intent までのバックアップの保存の期間と消し方は、前の記録と同じく、配備先が決まったときに見直す点として残します。
- 片付けではボリュームを消していません（Q1: B）。名前の無いボリューム（使い捨ての環境・テストの名残）に、前の負荷の試験の仮の利用者のデータ（予約のドメインのメールアドレス）が残りえます。本物の個人に関する値は入らない作り（`project.md` の Testing Posture）ですが、整理するときは依頼者の判断で行います。
- 外部の法令の枠組み（GDPR など）に当たる扱いは、配備先が開発者の PC の間は対象外です。配備先が決まったときに見直します。

## 4. 片付けの前後（Q1: B）

依頼者が承認した `docker image prune -f`（名前の無いイメージだけ、`-a` なし）と `docker builder prune -f` だけを実行しました。`docker volume prune`・`docker system prune` は実行していません。

| 種類 | 前 | 後 | 減った量 |
|---|---|---|---|
| イメージ | 47 個・22.1GB（回収できる 16.37GB） | 46 個・21.43GB（回収できる 15.7GB） | 671.3MB（名前の無いイメージ `22c89fe0d0f5` 1 個と、その層） |
| ビルドのキャッシュ | 24 個・2.595GB（回収できる 2.142GB） | 9 個・453.2MB（回収できる 0B） | 2.142GB（15 個） |
| ボリューム | 64 個・10.07GB | 64 個・10.07GB | 0（消していない） |
| コンテナ | 4 個（動いている 3） | 4 個（動いている 3） | 0 |
| PC のディスクの空き | 29GiB（93%） | 30GiB（93%） | — |

- 合計で約 2.8GB 減りました。
- 名前の無いイメージとして出た3つの ID のうち2つ（`c592c15aaf4a`・`efb4959ef2c8`）は、`mysql:8`・`mariadb:11` のタグも指していました。containerd の置き場では、タグの外れた古い参照だけが名前の無いイメージとして並ぶためです。消す前と後でタグ付きのイメージの一覧（46 行）を比べ、変わっていないことを確かめました。`mastersmith:*` の 10 個のタグも残っています。
- ダイジェスト固定のイメージ（Mailpit・postgres・mysql・mariadb）は名前の無いイメージに当たらず、残っています。結合テストで取り直しは要りません。
- 片付けの後も、アプリ・Mailpit・見本の対象DB は変わらず動いています（app と Mailpit は healthy）。

## 5. 気づき（食い違いではない）

| # | 気づき | 扱い |
|---|---|---|
| N1 | 手元の監視のイメージは `grafana/otel-lgtm:0.34.0` で、前の Intent の記録（0.33.1）から上がっている | 前の Intent までの変更（`compose.yaml` は `31b980b` から差が無い）。この Intent の設計（U3 の 2節「手元の監視は既存のまま」）とは食い違わない |
| N2 | 止めている `mastersmith-lgtm-1` の健全性の表示が `unhealthy` のまま残っている | 止めた時点の表示が残っているだけ。observability-setup などで起動したときに確かめ直す |
| N3 | `docker compose config` の素の出力に秘密の値が入る | 2節の気づきのとおり。記録・共有には `--quiet` か値を除いた項目だけを使う |
| N4 | この段の確かめの途中で、ボリュームの大きさを見ようとして、`mastersmith:local` から使い捨てのコンテナを1つ起動してしまった（内部DB のボリュームを読み取りだけで結び付け、コマンドは `true`、起動と同時に終了コード 0 で終わり `--rm` で消えた。`docker events` で確かめた） | 依頼の「コンテナを起動しない」から外れた操作。内部DB のファイルは開いておらず（`true` だけ）、配備したアプリ・ボリューム・データは変わっていない。大きさは `docker system df -v` で取り直した |

## 6. 次の Deployment Execution の段への申し送り

| こと | 内容 |
|---|---|
| 空き | PC の空き 30GiB、VM のディスク 100GiB。イメージの作り直し（約 650MB）とビルドに足りる |
| 戻し先のタグ | `mastersmith:pre-user-admin` は **まだ付けていない**。入れ替えの前に `docker tag mastersmith:local mastersmith:pre-user-admin` を付け、ID が `c77c1bb247f9` で始まることを確かめる（`cd-config.md` の 3節の順 3） |
| 今の版 | `mastersmith:local` は `c77c1bb247f9`（ソース `8489241`）、healthy |
| スキーマ | 今は V8。入れ替えの起動で Flyway が V9 を当てることを、起動のログで確かめる（`cd-config.md` の 5節の U1-NFR10.1） |
| `.env` | 変えない。複写も取らない（`cd-config.md` の 2節） |
| 同時に動くもの | Mailpit と見本の対象DB は動いたまま。入れ替えは `docker compose --profile targetdb-postgres up -d --build app` で `app` だけ（Mailpit は profile `mail` を付けないため触らない） |
| 片付けの残り | 回収できるイメージ 15.7GB・ボリューム 9.26GB は残してある。整理は依頼者の判断で後で行う |

## Sources

- 読み取りの結果（2026-10-03）: `colima list`・`docker version`・`docker info`・`docker compose -p mastersmith ps`・`docker inspect`・`docker image inspect`・`docker history`・`docker images`（`--digests`・`dangling=true`）・`docker volume ls`・`docker system df`（`-v` を含む）・`docker compose config`（`--quiet` と値を除いた項目）・`docker logs`（件数だけ）・`docker events`・`curl`・`lsof`・`df -h`・`ls -l`・`git check-ignore`・`git diff 31b980b develop`
- 片付けの実行の結果: `docker image prune -f`・`docker builder prune -f`
- `construction/u3-user-admin-api/infrastructure-design/infrastructure-specification.md`（1節〜3節・5節・7節）
- `construction/u4-admin-forbidden-ui/infrastructure-design/infrastructure-specification.md`（1節・4節・5節）
- `construction/u5-user-admin-ui/infrastructure-design/infrastructure-specification.md`（1節・3節・5節・6節）
- `operation/deployment-pipeline/cd-config.md`（1節〜3節・5節）
- `operation/environment-provisioning/environment-provisioning-questions.md`（Q1: B、まとめの確認 Looks correct）
- `aidlc/spaces/default/intents/260925-user-management/operation/environment-provisioning/validation-report.md`（前の記録の形）
- `aidlc/spaces/default/memory/team.md`・`project.md`（Deployment・Forbidden・Mandated・Corrections）
- `compose.yaml`・`docker/perf/compose.yaml`・`Dockerfile`（読むだけ）

## Assumptions & Open Questions

- [assumption] 片付けの判断に使った「名前の無いイメージ」は Docker の `dangling=true` の判定どおりとしました。containerd の置き場ではタグ付きのイメージの古い参照も含まれましたが、タグ付きのイメージの一覧が変わらないことを前後で確かめたので、承認の範囲（タグ付きのイメージを消さない）に収まっていると見ています。
- N4（使い捨てのコンテナを1つ起動したこと）を、承認の場で依頼者に伝えます。
- バックアップの保存の期間と消し方、古いタグのイメージと名前の無いボリュームの整理は、配備先が決まったときか依頼者の判断で見直します。
