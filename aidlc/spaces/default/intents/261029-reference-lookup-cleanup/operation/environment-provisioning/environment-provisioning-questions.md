# Environment Provisioning の質問（261029-reference-lookup-cleanup）

この段では、配備先（開発者の PC 上のコンテナ）の実行環境を読み取りだけで調べ、承認済みの設計の値（各単位の `infrastructure-specification.md`、`operation/deployment-pipeline/cd-config.md`・`rollback-runbook.md`）を満たせない点だけを質問にします（`project.md` の Corrections の学び）。値は 2026-11-01 04:35〜04:45 UTC に確かめました。コンテナ・イメージ・ボリューム・`.env` は変えていません。

## この PC の実行環境（読み取りで確かめた結果）

| 項目 | 値 |
|---|---|
| colima の VM | Running、CPU 4・メモリ 6GiB・ディスク 100GiB（aarch64・docker）。Docker 29.9.2（今日 01:07Z 頃の VM の apt の更新で docker が再起動した後） |
| VM の中のメモリ | 全体 5,911 MiB・使用 972 MiB・使える量 4,938 MiB |
| VM の中のディスク | 96G のうち 40G 使用（42%）、空き 57G |
| PC のディスク | 460GiB のうち 388GiB 使用（90%）、空き 47GiB |
| PC の電源 | バッテリー駆動（77%、放電中） |
| 動いているコンテナ | `mastersmith-app-1`（`mastersmith:local` = `e0ede6ddd5c4`、healthy、01:52:57Z 起動、再起動 0・OOMKilled なし）、見本の PG（`postgres:18.6`、`127.0.0.1:15432` の上乗せのまま）、Mailpit（`v1.32.0`、healthy）。lgtm（`0.38.0`）は Exited |
| `mastersmith` のタグ | `local`（`e0ede6dd`）・`reference-lookup-cleanup`（`eab1676f`、試験用）・`pre-perf-targets-review`（`3075065a`）・`carryover`（`009a757d`）・`pre-deps-perf-carryover`（`4cec01e7`）・`pre-business-data-views`（`f103aee1`）。戻し先のタグ `pre-reference-lookup-cleanup` はまだ無い |
| `.env` の項目（名前だけ） | 18 項目、どれも1件。足した2つの設定（`MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_REFERENCE`・`MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL`）は 0 件、`MASTERSMITH_JAVA_OPTIONS` は 0 件 |
| バックアップの置き場 | `~/.mastersmith-backup/` が 700、複写 20 件はすべて 600 |
| リポジトリの外の `.env` の複写 | `~/.mastersmith-env-before-user-management`（600、2026-09-29 作成）が残っている（Q1） |

## 決まっていること（質問にしない）

- 配備の流れ・戻し（イメージだけ）・配備の前と後の複写・k6 を配備の前に流さないことは、Deployment Pipeline の決定（`cd-config.md`、Q1〜Q4）のとおり。
- メモリは足りる。配備はアプリのコンテナを同じ上限（2g）で入れ替えるだけで、戻しの練習もしない。動いている3つのコンテナの上限の合計は 2,816 MiB、コンテナの外の使用は約 429 MiB で、余裕は約 2,666 MiB（lgtm を起動しても約 1,130 MiB 残る。`validation-report.md` の E1）。
- ディスクは足りる（VM の空き 57G・PC の空き 47GiB）。使われていないイメージ（回収できる 16GB）の片付けは、配備に要らないため、この段では行わない。試験用のタグは `cd-config.md` の順 12（Q4: A）で消す。
- 見本の PG の 15432 の上乗せは、配備の前の状態として残す（`cd-config.md` の 1節）。配備の入れ替えはサービス名 `app` だけを渡し、`app` に `depends_on` が無いため見本の PG は作り直されない（`validation-report.md` の E9）。

## Q1. リポジトリの外に残っている古い `.env` の複写

`~/.mastersmith-env-before-user-management`（権限 600、2026-09-29 作成）が残っています。Intent `260925-user-management` の配備の前に、戻しのために取った `.env` の複写と見られます（中身は開いていません）。今の `.env`（2026-10-21 更新、18 項目）とは別の古い値で、初期管理者のパスワード・署名鍵などの秘密を含みうるものです。この Intent の戻し（`rollback-runbook.md`）はイメージだけで、`.env` を変えないため、この複写は使いません。前の Intent でも、使い終えた `.env` の複写は依頼者の決定で消しています（`261016-business-data-views` の Incident Response の Q5: A、Feedback & Optimization の Q3: A）。

**推奨は A です。** 理由: 戻しに使う見込みが無く、秘密を含みうるファイルをリポジトリの外に残し続ける意味が無いためです。中身を開かずに消すことで、値を表示する危険もありません。代わりに、`260925-user-management` の前の `.env` の値には戻せなくなります（その版へ戻す手順は今は無い）。

A. この段で、中身を開かずに `rm` で消す。消した後に、ファイルが無いことだけを `ls` で確かめて記録する
B. 消さずに残し、`validation-report.md` に残っていることだけを記録する（片付けは Feedback & Optimization の段で決める）
X. Other (please specify)

[Answer]: A

## Sources

- この Intent の配備の段: `operation/deployment-pipeline/cd-config.md`（1節・3節・4節・4.1）・`deployment-strategy.md`・`rollback-runbook.md`（2節・3節・4節）・`deployment-pipeline-questions.md`
- この Intent の基盤の設計: `construction/u1-reference-read/infrastructure-design/infrastructure-specification.md`（1節・2節）、`construction/u2-reference-write-check/infrastructure-design/infrastructure-specification.md`（1節・2節）、`construction/u3-reference-screens/infrastructure-design/infrastructure-specification.md`（1節・2節）、`construction/u5-container-perf-docs/infrastructure-design/infrastructure-specification.md`（1節・2節）
- 前の同じ段: `aidlc/spaces/default/intents/261016-business-data-views/operation/environment-provisioning/`
- 前の決定: `aidlc/spaces/default/intents/261016-business-data-views/operation/incident-response/`（Q5: A）、同 `feedback-optimization/`（Q3: A）
- 実行環境（読み取りだけ）: `colima list`、`colima ssh -- free -m`・`df -h /`、`df -h`（PC）、`pmset -g batt`、`docker version`・`ps -a`・`stats --no-stream`・`inspect`・`images`・`system df`・`volume ls`、`ls -l ~/.mastersmith-*`、`.env` の項目の名前と件数
- 決まり: `aidlc/spaces/default/memory/team.md`（Deployment）、`project.md`（Deployment・Corrections・Forbidden）

## Assumptions & Open Questions

- [assumption] `~/.mastersmith-env-before-user-management` は `260925-user-management` の配備の前の `.env` の複写で、今の配備と戻しには使わない（名前と作成日からの見立て。中身は開いていない）。
- 古い複写の扱いは Q1 で決める。
