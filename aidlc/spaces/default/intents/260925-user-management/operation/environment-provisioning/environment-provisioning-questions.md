# Environment Provisioning — Questions

配備先は開発者の PC 上のコンテナです。そのため、この段では、環境を Docker の実行環境・イメージ・ボリューム・`.env`・compose の設定と読み替えます（project.md の Deployment）。

質問を作る前に、この PC の実行環境を読み取りだけで調べました（project.md の Corrections）。その結果、承認済みの設計（8単位の `infrastructure-specification.md`、`operation/deployment-pipeline/cd-config.md`）の値を満たせない点は見つかりませんでした。そのため、質問は作りません。確かめの要点を、下のまとめで確認していただきます（project.md の Way of Working と同じ扱い）。

## 読み取りで調べたこと（2026-09-29）

| 項目 | 設計の値 | 実物 | 判定 |
|---|---|---|---|
| colima の VM | CPU 4・メモリ 6GiB | CPU 4・メモリ 6GiB・ディスク 100GiB（Running） | 満たす |
| 同時に動かすコンテナのメモリ | アプリ 2g・対象DB 512m・Mailpit 256m、戻しの練習のアプリ 2g（計 約 4.8GB） | 今の上限：アプリ 2GiB・対象DB 512MiB・Mailpit 256MiB。使用中：アプリ 478MiB・対象DB 26MiB・Mailpit 21MiB | 満たす（6GiB に入る） |
| アプリの CPU の上限 | 4 | 4（`NanoCpus` 4,000,000,000） | 満たす |
| PC のディスク | バックアップを `~/.mastersmith-backup/` に置ける | PC の空き 38GiB。内部DB のボリュームは 56KB（`mastersmith.mv.db` 53KB） | 満たす |
| バックアップの置き場 | `~/.mastersmith-backup/`（権限 700） | あり（`drwx------`）。前の Intent のバックアップが3つある | 満たす |
| 使う番号 | 8080（アプリ）・8025 と 1025（Mailpit）・18080（戻しの練習）・3000（Grafana、observability-setup） | 8080・8025・1025 は colima の転送が使用中（今のアプリと Mailpit）。18080・3000 は空き | 満たす |
| compose の設定 | `compose.yaml`（profile `targetdb-postgres`・`mail`）と `docker/perf/compose.yaml`（プロジェクト `mastersmith-rollback`、`127.0.0.1:18080`） | どちらも `docker compose config --quiet` が通る | 満たす |
| イメージ | Mailpit `axllent/mailpit:v1.31.2`（ダイジェスト固定）、バックアップに使う `eclipse-temurin:25.0.4_7-jre-noble` | どちらも PC にある | 満たす |
| ボリューム | `mastersmith_mastersmith-data`（内部DB）・`mastersmith_mastersmith-targetdb-postgres`・`mastersmith_mastersmith-monitoring` | 3つともある | 満たす |
| `.env` | 秘密でない4行（`MASTERSMITH_WEB_BASE_URL`・`SPRING_MAIL_HOST`・`SPRING_MAIL_PORT`・`MASTERSMITH_MAIL_FROM`）を配備のときに足す | 4行とも無い（名前だけを見た。値は見ていない）。`.env.example` には、4つともコメントとして見本がある | 配備のとき（deployment-execution）に足す予定どおり |
| 今のアプリ | 健全 | `/actuator/health` が `{"status":"UP"}`。Mailpit の API が 200 | 満たす |

## Consolidated Summary Confirmation

この段では、次のとおり進めます。

- 質問はありません。上の表のとおり、PC の実行環境は、承認済みの設計と配備の手順の値を満たしています。
- `environment-inventory.md` に、環境の一覧を書きます。コンテナ・イメージ・ボリューム・番号・`.env` の項目の名前・バックアップの置き場です。
- `validation-report.md` に、確かめの結果を書きます。
  - 設計の値との突き合わせ
  - セキュリティの確かめ：番号が `127.0.0.1` だけに開いていること、`.env` と `.env.targetdb` が Git の管理外で権限が絞られていること、バックアップの置き場の権限
  - 個人に関する値の扱い（コンプライアンスの確かめ）
- この段では、アプリの入れ替え・`.env` の変更・コンテナの起動と停止は行いません。どれも deployment-execution で、依頼者の承認を得て行います。ただし、確かめのための読み取り（`docker inspect`・`docker compose config`・`ls -l`）は行います。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
