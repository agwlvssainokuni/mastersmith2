# 環境の確かめの結果（validation-report）

Intent 261029-reference-lookup-cleanup の配備先の環境（開発者の PC 上のコンテナ）を、承認済みの設計の値と1つずつ突き合わせた結果です。

- 決定は `environment-provisioning-questions.md` の Q1: A です。結果は 5節に書きました。
- 確かめは 2026-11-01 04:35〜04:45 UTC に読み取りだけで行いました。
- 次のことは、次の Deployment Execution の段で行います（`operation/deployment-pipeline/cd-config.md` の 3節）: WAR の作り直し、戻し先のタグ付け、配備の前の複写と数え、アプリの入れ替え、スモークテスト、配備の後の複写、試験用のタグの片付け、`main` への取り込み。
- 書き方は前の同じ段の記録（`aidlc/spaces/default/intents/261016-business-data-views/operation/environment-provisioning/validation-report.md`）を正としました。

**判定: 合格（見込み）**

- 設計の値を満たせない点は無し。
- 片付けた点が 1 件（N1 の古い `.env` の複写、Q1: A で消した）。
- 申し送りが 4 件（7節）。

この段の役割（基盤）に、セキュリティ（devsecops）とコンプライアンスの観点を合わせて確かめました。

## 1. 設計の値との突き合わせ

| # | 項目 | 設計の値（出典） | 実際の値（確かめ方） | 判定 |
|---|---|---|---|---|
| E1 | VM の資源とメモリの余裕 | CPU 4・メモリ 6GiB。アプリ・対象DB のコンテナの上限は変えない（U1・U2 の `infrastructure-specification.md` 1節） | CPU 4・メモリ 6GiB、Running（`colima list`）。VM の全体 5,911 MiB・使える量 4,938 MiB。動いている上限の合計 2,816 MiB＋コンテナの外 約 429 MiB で余裕 約 2,666 MiB。配備・戻しは同じ上限での入れ替えだけ（`environment-inventory.md` 10節） | 満たす |
| E2 | アプリの資源の上限 | メモリ 2g・CPU 4（U1・U2・U3 の 1節） | Memory 2,147,483,648 B・NanoCpus 4,000,000,000（`docker inspect`）。`docker compose config` も 2g・4 | 満たす |
| E3 | 最大ヒープとヒープが尽きたときの動き | `MASTERSMITH_JAVA_OPTIONS`・`MaxRAMPercentage` を変えない（U1 の 1節） | `.env` に `MASTERSMITH_JAVA_OPTIONS` が 0 件。RestartPolicy は `no`。`Dockerfile` は変わらない | 満たす |
| E4 | 動いている版 | `mastersmith:local`（`e0ede6ddd5c4`、healthy）（`cd-config.md` 1節） | イメージ ID `e0ede6ddd5c4…`、healthy、再起動 0 回、OOMKilled なし、`/actuator/health` が 200。起動のログの ERROR 0 件、`Migrating schema` 0 件 | 満たす |
| E5 | 網 | `127.0.0.1:8080` だけに結び付ける。新しい道は同じオリジン（U1・U2・U3 の 1節） | app の `PortBindings` は `127.0.0.1:8080` だけ。Mailpit は `127.0.0.1:8025`・`1025`、見本の PG は E2E の上乗せの `127.0.0.1:15432`（どれも localhost だけ） | 満たす |
| E6 | 2つの新しい設定 | `.env` に足さず既定 5s・10s で動く（U1 の 2節 Q1: A、`cd-config.md` 1節） | `.env` の2つの項目名は 0 件。今の WAR の `application.yaml` に既定つきの2行がある（`unzip -p`）。今の起動のログに2つの項目名の WARN は 0 件 | 満たす |
| E7 | 内部DB | 移行を足さない（V13 のまま）（U1・U2 の 1節） | 今の起動で `Migrating schema` 0 件。`backend/src/main/resources/db` の差は 0（`cd-config.md` 1節） | 満たす（版の数は順 5 の複写で 13 を確かめる） |
| E8 | 見本の対象DB | 見本の PG の外部キー `sales.orders.customer_id → sales.customers` で足りる。参照の表は足さない（`cd-config.md` 5節） | 外部キーがある、`customers` 2 行・`orders` 1 行。ロール 3 つ（読み取り・書き込みあり）、PUBLIC の `TEMP` は `f`、`e2e_biz_*` の残り 0 | 満たす |
| E9 | 入れ替えで見本の PG・Mailpit を作り直さない | 順 6 の `docker compose --profile targetdb-postgres up -d --build app`（`cd-config.md` 3節） | `app` に `depends_on` が無い（`docker compose config` の JSON を `jq` で選んで確かめた）。サービス名 `app` だけを渡す限り、上乗せなしの compose でも見本の PG（15432 の上乗せ）には触れない | 満たす（7節の H2） |
| E10 | 使っている周辺のコンテナの版 | Mailpit `v1.32.0`・lgtm `0.38.0`・postgres `18.6` を index のダイジェストで固定（U5 の 2節、`team.md` の Deployment） | 3つのコンテナのイメージ ID が `compose.yaml` のダイジェストと一致（Mailpit `0790692b…`・lgtm `75cdf892…`・postgres `5a5a84b1…`） | 満たす |
| E11 | 戻し先 | 入れ替えの前に今のイメージに `pre-reference-lookup-cleanup` を付ける（`rollback-runbook.md` 2節） | `e0ede6ddd5c4` は手元にあり、今の内部DB（V13）で動いている。タグはまだ無い（順 3 で付ける）。イメージに HEALTHCHECK は無く、戻しは compose のヘルスチェックで待つ | 満たす（順 3 で付ける） |
| E12 | 試験用のタグの片付け | `mastersmith:reference-lookup-cleanup`（`eab1676f26de`）を順 12 で消す（Q4: A） | 手元にあり、使っているコンテナは 0（`docker ps -a --filter ancestor=eab1676f26de`） | 満たす（順 12 で消す） |
| E13 | バックアップの置き場 | `~/.mastersmith-backup/` 700、複写 600（`cd-config.md` 4節） | 置き場 700、複写 20 件すべて 600、数える道具 700。`mktemp -d` は使わない | 満たす |
| E14 | ディスク | 複写2件（各 約 40KB）と WAR の作り直し（約 106MB）・イメージの作り直し（約 663MB）が入る | VM の空き 57G（42%）、PC の空き 47GiB（90%） | 満たす |
| E15 | シェルに戻しの変数が残っていない | `env \| grep -c MASTERSMITH_IMAGE_TAG` が 0（`cd-config.md` 順 2） | 0 | 満たす（順 2 で再度確かめる） |

## 2. セキュリティの確かめ（devsecops の観点）

| # | 項目 | 結果 |
|---|---|---|
| S1 | 秘密のファイルの管理 | `.env`・`.env.targetdb` は 600 で Git の管理外（`.gitignore` の 33・34 行目）。中身は開かず、項目の名前と件数だけを数えた |
| S2 | 秘密を記録に出さない | `docker compose config` の全体と `docker inspect` の全体の JSON は表示せず、`jq`・`--format` で必要な項目だけを出した。コンテナの環境は名前だけを出した |
| S3 | アプリのコンテナの権限 | 利用者 `10001:10001`、特権なし、`restart: "no"` |
| S4 | 対象DB の資格情報 | 読み取りと書き込みを別の項目で持つ（`.env` の7つと2つ）。書き込みのアカウントは前の Intent で DDL の権限を与えない形で作り済み。この Intent では変えない（`project.md` の Mandated） |
| S5 | 待ち受けの番号 | すべて `127.0.0.1`。見本の PG の 15432 は E2E の上乗せで、配備の前から開いている（`cd-config.md` 1節）。localhost だけで、ほかの端末からは届かない |
| S6 | リポジトリの外の秘密の複写 | `~/.mastersmith-env-before-user-management`（600、2026-09-29）が残っていた。使う予定が無いため Q1: A で消した（5節） |
| S7 | 今の WAR の秘密 | 確かめは `application.yaml` の該当の行だけ。既定値に秘密は無い（2つの設定は時間の値） |

## 3. 個人に関する値の扱い（コンプライアンスの観点）

- 初期管理者のメールアドレス・氏名・業務データの行の値は、この段で一度も表示していません。見本の PG は件数と真偽値だけを数えました。
- 順 5・順 10 の数えに使う道具 `count-db2.sh` は `.env` の初期管理者のメールアドレスを変数に読むだけで表示しない作りです（前の Intent と同じ）。

## 4. 承認済みの記録との差

- 無し。`cd-config.md` の 1節の今の状態（`e0ede6dd` が healthy、見本の PG は 15432 の上乗せのまま、Mailpit `v1.32.0`、lgtm `0.38.0` は停止）と一致しました。

## 5. 古い `.env` の複写の片付け（Q1: A）

- 依頼者の答え Q1: A で、2026-11-01T04:41:22Z に `~/.mastersmith-env-before-user-management` を中身を開かずに `rm -f` で消した。
- 消した後に `ls` で No such file or directory を確かめた（この記録を書く前にもう一度確かめ、同じ結果）。
- 失うもの: `260925-user-management` の前の `.env` の値（その版へ戻す手順は無い）。この Intent の戻しはイメージだけで、`.env` を使わない。
- この段の決まり（環境を変えない）との差として、変更はこの1件だけ。

## 6. 気づき（食い違いではない）

| # | 内容 |
|---|---|
| N1 | 古い `.env` の複写が残っていた（S6）。Q1: A で消した（5節） |
| N2 | `compose.yaml` の `otel-collector`（profile `observability`、`0.163.2`）のイメージが手元に無い（手元は `0.162.0`）。配備・確かめでは使わないため影響は無い。起動するときに取りに行く |
| N3 | 使われていないイメージの回収できる量が 16GB（古い lgtm のタグなしのイメージ 2 つ（`57b5ab1c`・`57c04cbd`）約 6.97GB、`0.34.0` 3.44GB など。今の `0.38.0` は `75cdf892`）。U5 は lgtm `0.34.0` を戻し先として残す決まり（U5 の 2.3）。ディスクは足りるため、この段では片付けず、Feedback & Optimization の段で扱う |
| N4 | PC がバッテリー駆動（77%）。WAR の作り直し（順 2）は `caffeinate -dims` で包み、配備の前に電源をつなぐのが望ましい（`project.md` の学び） |
| N5 | `develop` の先頭 `23519159` の CI（run 41273010129）は確かめの時点で実行中。`e1e028d1` の CI は2回とも失敗し、固定先の更新 `cb23466b` で直った（`construction/ci-pipeline/ci-config.md` 6.3・6.4 に記録済み） |
| N6 | 今のアプリの WARN は 14 件（`GlobalExceptionHandler` 6・Flyway の `Database` 4・Spring の `BeanPostProcessorChecker` 4）。前の版から出ている種類で、起動の ERROR は 0 件 |

## 7. 次の Deployment Execution の段への申し送り

| # | 内容 |
|---|---|
| H1 | 今の WAR（SHA-256 `4cd5c416…`）は `cb23466b` の後に作られ、2つの設定も入っているが、`cd-config.md` の順 2 のとおり作り直して SHA-256 と中身（画面の参照の部品の文言を含む）を確かめる |
| H2 | 入れ替え（順 6）と起動し直し（順 10）は、必ずサービス名 `app` を付けて行う。サービス名を付けずに `docker compose --profile targetdb-postgres up -d` を行うと、上乗せの無い構成で見本の PG が作り直され、15432 が閉じる（E2E は上乗せを付けて作り直す必要が出る） |
| H3 | 4.1 の数えで使う `~/.mastersmith-backup/count-db2.sh` は、前の Intent の文字列（`customers`・`customer_orders`）を数える形。今回の数え（`orders`・`customer_id`・`REFERENCE` の現れる回数）に合わせて、リポジトリの外で直してから使う。列の名前は `Cols.java` で先に確かめる（`project.md` の学び） |
| H4 | 順 1 で `23519159` の CI の結果を確かめる（N5）。順 13 の前に、`d797ef74` の後のすべてのコミットの CI を試行ごとに確かめる |

## Sources

- 読み取りの結果（2026-11-01）: `environment-inventory.md` の Sources のとおり
- `environment-provisioning-questions.md`
- `operation/deployment-pipeline/cd-config.md`（1節・3節・4節・4.1・5節）・`deployment-strategy.md`・`rollback-runbook.md`（2節・3節）
- 各単位の基盤の設計: `construction/u1-reference-read/infrastructure-design/infrastructure-specification.md`（1節・2節）、`construction/u2-reference-write-check/infrastructure-design/infrastructure-specification.md`（1節・2節）、`construction/u3-reference-screens/infrastructure-design/infrastructure-specification.md`（1節・2節）、`construction/u5-container-perf-docs/infrastructure-design/infrastructure-specification.md`（1節・2節・2.3）
- `construction/ci-pipeline/ci-config.md`（6.3・6.4）
- 前の記録: `aidlc/spaces/default/intents/261016-business-data-views/operation/environment-provisioning/validation-report.md`
- 決まり: `aidlc/spaces/default/memory/team.md`（Deployment）、`project.md`（Deployment・Corrections・Forbidden・Mandated）

## Assumptions & Open Questions

- [assumption] 適用中の DSL は既定の生成のもので、`orders.customer_id` が `REFERENCE`（見本の PG の外部キーがあることまでは確かめた。DSL の本文は順 5 の複写で数える）。
- [assumption] 初期管理者の状態とロールの割り当ては前の Intent の配備の後のまま（順 5 で数える）。
- 古い `.env` の複写は Q1: A で消した（5節）。
