# 設定のずれ（drift-report）

動いている環境を読み取りだけで確かめ（Q3: A）、記録した設計の値と1つずつ比べた結果。確かめた日時は 2026-09-25T07:09+09:00（コミット `a7ab1b7`）。`.env` は開かず、項目があるかどうかだけを数えた。

## 1. 比べた結果

| 項目 | 設計・記録の値 | 実際 | 判定 | 設計・記録の出どころ |
|---|---|---|---|---|
| アプリのイメージ | `mastersmith:local`、`sha256:1585bd4ef3dd…`（版 `7bc1b68`） | 同じ | 一致 | `operation/deployment-execution/deployment-log.md` 1節 |
| アプリの上限 | メモリ 2GiB・CPU 4 | `2147483648`・`4000000000` | 一致 | `deployment-log.md` 2節の 13、`operation/environment-provisioning/environment-inventory.md` 2節 |
| ポート | `127.0.0.1:8080` だけ | `127.0.0.1:8080` | 一致 | `compose.yaml`、前の Intent の `cd-config.md` |
| ヘルスチェック | 30 秒ごと・猶予 40 秒・3 回 | 同じ | 一致 | 同上 |
| 止めるときの猶予・再起動の方針 | 45 秒・`restart: "no"` | 45・`no` | 一致 | 同上、U1 の `infrastructure-specification.md` |
| ログ | json-file、10MB × 3 | 同じ | 一致 | 同上 |
| タイムゾーン | `TZ=Asia/Tokyo` | 同じ | 一致 | 同上 |
| 内部DB | ボリューム `mastersmith_mastersmith-data`、Flyway の版 6、`MASTERSMITH_DB_URL` は無し（既定の `DEFRAG_ALWAYS=TRUE`） | 同じ（起動のログ `Current version of schema "PUBLIC": 6`・`up to date`、`.env` の項目 0） | 一致 | `deployment-log.md` 2節の 12、`environment-inventory.md` 4節 |
| 見本の対象DB | `postgres:18.6@sha256:86c951e0…`、上限 512MiB、`restart: "no"`、ポートは開けない | 同じ | 一致 | `environment-inventory.md` 2節 |
| `.env` の項目 | 9項目（見本の DB の2つ・対象DB の7つ）と、署名鍵・初期管理者・上限の項目がある。外部エクスポートの2項目は無い（Observability Setup の Q4: A） | すべてある（各 1）。外部エクスポートの2項目・`MASTERSMITH_JAVA_OPTIONS`・`MASTERSMITH_DB_MAXIMUM_POOL_SIZE` は 0 | 一致 | `environment-inventory.md` 4節、`operation/observability-setup/observability-setup-questions.md` |
| 手元の監視 | `grafana/otel-lgtm:0.33.1`、上限 1536m、見たいときだけ起動 | 上限 1536m（compose の設定）、止まっている（Exited 0） | 一致 | `operation/observability-setup/dashboards.md` 3節、`compose.yaml` |
| ボリューム | 3つ（内部DB・見本の対象DB・手元の監視） | 3つ | 一致 | `environment-inventory.md` 3節 |
| 戻し用のタグ | `mastersmith:pre-dsl` = `sha256:8441534a745f…` | 同じ | 一致 | `deployment-log.md` 2節の 7 |
| バックアップ | `~/.mastersmith-backup/`（権限 700）に配備の前と後の2つ | 2つ、置き場の権限 `drwx------` | 一致 | `deployment-log.md` 5節 |
| 性能の試験の後片付け | 使い捨ての環境・一時ディレクトリ・試験用のタグは消す | `mastersmith-perf` のコンテナ・ボリュームは無い、`~/.mastersmith-perf-*` は無い、`mastersmith:perf-dsl` は無い | 一致 | `operation/performance-validation/load-test-plan.md` 5節 |

## 2. 見つかったずれ・食い違い

| # | 内容 | 根拠 | 影響 | 案 |
|---|---|---|---|---|
| D1 | **アプリのメモリの上限の「既定」の書き方の食い違い**。`compose.yaml` の既定は `1g` で、2g はこの PC の `.env` の `MASTERSMITH_CONTAINER_MEMORY=2g` で決まっている（README の表も既定 `1g`、「VM を 6GiB にした PC では 2g にする」）。一方、Performance Validation の記録（`test-results.md` 5節 F1・`nfr-validation-matrix.md` 1節）と、その承認の決定は「配備の既定は前の Intent で既に 2g」「要件の条件を配備の既定 2g とする」と書いた | `compose.yaml` の `mem_limit: ${MASTERSMITH_CONTAINER_MEMORY:-1g}`、`README.md` の環境変数の表、`.env` の項目の数 1 | 今の配備（この PC）は 2g で動いており、動きに影響は無い。ただし `.env` にこの項目が無い PC では 1g で起動し、10MB の DSL とログインの重ねで止まる（Performance Validation の 1g の結果） | 記録を「この PC の配備の設定値 2g（`.env`）。compose の既定は 1g」と読み替える。compose の既定を 2g にするか、10MB の DSL を扱うには 2g が要ることを README に書くかは、後の Intent で決める（依頼者に諮る） |
| D2 | **アプリのコンテナが、見本の対象DB の管理者のパスワードを環境変数で持っている**。アプリは `.env` 全体を `env_file` で読むため、アプリに要らない `MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD`（見本の DB の管理者 `target_admin`）と `MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD` も渡っている | `docker inspect mastersmith-app-1` の環境変数の名前（値は見ていない）、`compose.yaml` の `app` の `env_file: .env` | 手元の見本の DB だけの話で、ログ・応答には出ない（Forbidden の対象はログ・応答）。ただし、アプリが要らない資格情報を持つのは最小の権限の考え方から外れる。Code Generation の記録（project.md の学び「compose の app に対象DB の環境変数を足さず、`.env.example` に足すだけにした。app はすでに `.env` を env_file で読むため」）の副作用 | 後の Intent で、`app` に渡す項目を絞る（`environment` で必要な項目だけを渡す、または見本の DB 用の `.env` を分ける）かを決める（依頼者に諮る） |

- 変えたもの: 無し（この段は読み取りだけ）。

## 3. 範囲外として記録だけするもの

- Docker の使われていないイメージ 約 10.8GB・ボリューム 約 9.1GB・ビルドのキャッシュ 約 1.0GB（`docker system df`）。Testcontainers や試験の残りと見られる。VM のディスクは 64G 空いており、今は消さない（`cost-analysis.md` 3節）。
- AWS Config・Trusted Advisor による確かめは、クラウドの基盤が無いため対象が無い。

## Sources

- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/feedback-optimization/feedback-optimization-questions.md`（Q3: A、確認済みの要約）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/deployment-execution/deployment-log.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/environment-provisioning/environment-inventory.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/observability-setup/dashboards.md`・`alarms.md`・`slo-config.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/performance-validation/test-results.md`・`nfr-validation-matrix.md`・`load-test-plan.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/operation/deployment-pipeline/cd-config.md`、`aidlc/spaces/default/intents/260922-auth-audit-base/construction/u1-app-skeleton/infrastructure-design/infrastructure-specification.md`
- `compose.yaml`、`README.md`（環境変数の表・「コンテナの資源の上限」）、`.env.example`
- 実行したコマンド: `colima list`・`colima ssh -- df -h /`・`colima ssh -- free -m`・`docker ps -a`・`docker inspect`（環境変数は名前だけ）・`grep -c '^<項目>=.' .env`・`docker volume ls`・`docker images`・`ls -la ~/.mastersmith-backup`・`docker system df`・`docker logs mastersmith-app-1`（Flyway の行）

## Assumptions & Open Questions

- [assumption] D1・D2 を直すかどうかと、直す時期は、承認の場で依頼者が決める。
