# 環境の一覧（environment-inventory）

Intent 260925-user-management の配備先の環境です。配備先は、開発者の PC 上のコンテナです（project.md の Deployment）。値は 2026-09-29 に読み取りだけで確かめたものです。秘密の値と個人に関する値は書きません。

## 1. 実行環境

| 項目 | 値 |
|---|---|
| コンテナの実行環境 | colima（プロファイル `default`、aarch64、Docker ランタイム）。Running |
| VM の資源 | CPU 4・メモリ 6GiB・ディスク 100GiB |
| PC のディスクの空き | 38GiB（`/System/Volumes/Data` の 91% を使用） |
| Docker の使用量 | イメージ 39 個（16.28GB、うち 10.94GB は回収できる）、ボリューム 64 個（10.03GB） |
| compose のプロジェクト | `mastersmith`（配備）。戻しの練習は `mastersmith-rollback`（`docker/perf/compose.yaml`） |

## 2. コンテナ（今）

| コンテナ | イメージ | 状態 | メモリの上限・使用 | 番号 |
|---|---|---|---|---|
| `mastersmith-app-1` | `mastersmith:local`（`sha256:305fddf4…`） | Up・healthy（2026-09-27 起動） | 2GiB・478MiB。CPU の上限 4 | `127.0.0.1:8080` |
| `mastersmith-mailpit-1` | `axllent/mailpit:v1.31.2`（ダイジェスト固定） | Up・healthy | 256MiB・21MiB | `127.0.0.1:8025`・`127.0.0.1:1025` |
| `mastersmith-targetdb-postgres-1` | `postgres:18.6`（ダイジェスト固定） | Up | 512MiB・26MiB | 公開しない |
| `mastersmith-lgtm-1` | `grafana/otel-lgtm:0.33.1` | Exited（profile `monitoring`。observability-setup で使う） | 1.5GB（設定） | `127.0.0.1:3000` |

- アプリのコンテナは、root でない利用者 `10001:10001` で動いています。
- 配備と戻しの練習で同時に動かすと、アプリ 2g＋対象DB 512m＋Mailpit 256m＋練習のアプリ 2g で、約 4.8GB です。VM の 6GiB に入ります。
- 監視（1.5GB）は、戻しの練習とは同時に動かしません。

## 3. イメージ（`mastersmith`）

| タグ | 作成 | 使い道 |
|---|---|---|
| `local` | 3日前 | 今配備している版。配備の前に `pre-user-management` のタグを付けて戻し先にする |
| `storage-memory-fix`・`storage-memory-base`・`pre-storage-memory` | 3日前 | 前の Intent の試験・戻し先 |
| `followup-fixes`・`pre-followup` | 3〜4日前 | 前々の Intent の試験・戻し先 |
| `pre-dsl` | 5日前 | それより前の戻し先 |

古いタグの整理は、この Intent では行いません（依頼者の判断で、後で消してよい）。

## 4. ボリュームとバックアップ

| 名前 | 中身 | 大きさ |
|---|---|---|
| `mastersmith_mastersmith-data` | 内部DB（H2、`/app/data/mastersmith.mv.db`、持ち主 10001） | 56KB（ファイルは 53KB。2026-09-27 の起動のときから書き換わっていない） |
| `mastersmith_mastersmith-targetdb-postgres` | 見本の対象DB | — |
| `mastersmith_mastersmith-monitoring` | 手元の監視のデータ | — |
| `~/.mastersmith-backup/`（権限 700） | 内部DB のバックアップの置き場。前の Intent の3つがある | — |

## 5. 設定のファイル（値は書かない）

| ファイル | 権限・管理 | この Intent の項目 |
|---|---|---|
| `.env` | 600、Git の管理外 | 今ある項目：初期管理者の2つ・署名鍵・コンテナの CPU とメモリ・対象DB の7つ。**配備のときに足す項目**：`MASTERSMITH_WEB_BASE_URL`・`SPRING_MAIL_HOST`・`SPRING_MAIL_PORT`・`MASTERSMITH_MAIL_FROM`（`cd-config.md`） |
| `.env.targetdb` | 600、Git の管理外 | 見本の対象DB の2項目（変えない） |
| `.env.example` | Git の管理 | 上の4項目と、見た目の設定・外部エクスポートの見本が、コメントとしてある |
| `compose.yaml` | Git の管理 | profile `targetdb-postgres`・`mail`・`monitoring`・`observability`。`docker compose config --quiet` が通る |
| `docker/perf/compose.yaml` | Git の管理 | 使い捨ての環境と戻しの練習（`127.0.0.1:18080`、ボリューム `perf-data`、profile `mail`）。`config --quiet` が通る |

## 6. 番号

| 番号 | 使うもの | 今 |
|---|---|---|
| 8080 | アプリ | 使用中（colima の転送） |
| 8025・1025 | Mailpit | 使用中（colima の転送） |
| 18080 | 戻しの練習のアプリ | 空き |
| 3000 | Grafana（observability-setup） | 空き |

## Sources

- `docker`・`colima`・`docker compose config`・`ls -l` の読み取りの結果（2026-09-29）
- `construction/u2-user-preferences/infrastructure-design/infrastructure-specification.md`・`construction/u3-invitation/infrastructure-design/infrastructure-specification.md` ほか、各単位の `infrastructure-design/infrastructure-specification.md`
- `operation/deployment-pipeline/cd-config.md`・`rollback-runbook.md`
- `compose.yaml`・`docker/perf/compose.yaml`・`.env.example`・`README.md`（コンテナの資源の上限）

## Assumptions & Open Questions

- 今のアプリのイメージが前の Intent の配備の版かは、deployment-execution で前の Intent の記録と突き合わせて確かめます。
