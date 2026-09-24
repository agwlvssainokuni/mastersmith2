# 警報（alarms）

前の Intent の `aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/alarms.md` を正とし、今回の差だけを書く。警報はファイル（`docker/monitoring/provisioning/alerting/mastersmith.yaml`、16 件）でリポジトリに置く。知らせの先は配備先が決まるまで作らない（Grafana の画面で見るだけ。project.md の決まり）。

## 1. 今回の差

- **この Intent では警報を足していない。** 質問の前提で「U4 で警報『照合の時間の範囲外』を足した」と書いたが、誤りだった。この警報は前の Intent のパスワードの照合の時間（`パスワードの照合の時間が目安（100〜500 ミリ秒）の外です`）の警報である。
- DSL の操作の失敗の警報は置かない。操作の結果（`outcome`）はダッシュボードで見る。アプリの ERROR のログの警報（既存）が、DSL の操作の想定外の失敗（ERROR のログを出す）も拾う。

## 2. 内部DB のファイルの大きさ（U4-STORAGE-RUN、Q2: A）

- 内部DB のファイルの大きさを表す指標はアプリに無いため、警報は置かない。運用の手順で見る（README の「DSL の管理」の「既知の制約」）: `docker run --rm -v mastersmith_mastersmith-data:/data:ro eclipse-temurin:25.0.4_7-jre-noble du -sh /data`（2026-09-25 の配備の後で 164K）。300MB を超えたらディスクの空きを確かめ、アプリを起動し直す。
- 指標と警報を足すことは、依頼者の判断（Q2: A）で今回は行わない。

## 3. 確かめた結果（すべての式、2026-09-25）

| 種類 | 名前 | 取り出し先 | 結果 | 値 |
|---|---|---|---|---|
| ダッシュボード | 稼働（5xx でない応答の割合、30 日） | Prometheus | success | 全体=1 |
| ダッシュボード | 要求の数（1 分あたり） | Prometheus | success | 全体=5.99999791672743 |
| ダッシュボード | 5xx の割合（5 分） | Prometheus | success | 全体=0 |
| ダッシュボード | ERROR のログ（5 分あたり） | Prometheus | success | 全体=0 |
| ダッシュボード | ログインの API | Prometheus | success | 全体=NaN |
| ダッシュボード | トークンの更新の API | Prometheus | success | 全体=NaN |
| ダッシュボード | 確認用 API（/api/admin/check） | Prometheus | success | 全体=NaN |
| ダッシュボード | コネクションプールの待ち | Prometheus | success | 全体=0 |
| ダッシュボード | JVM のヒープの使用率 | Prometheus | success | 全体=0.046238650935687936 |
| ダッシュボード | 403 の件数（1 時間） | Prometheus | success | 全体=0 |
| ダッシュボード | 監査の書き込みの失敗（1 時間） | Loki | success | （結果なし） |
| ダッシュボード | アカウントのロック（1 時間） | Loki | success | （結果なし） |
| ダッシュボード | 正規化されていないパスの拒否（1 時間） | Loki | success | （結果なし） |
| ダッシュボード | 送り元の不一致の拒否（1 時間） | Loki | success | （結果なし） |
| ダッシュボード | 監査の書き込みの遅れ（1 日） | Loki | success | （結果なし） |
| ダッシュボード | 操作ごとの件数（1 時間） | Prometheus | success | operation=generate=3.272541599315394・operation=discard=3.272503382511111 |
| ダッシュボード | 結果ごとの件数（1 時間） | Prometheus | success | outcome=success=6.545302610497716 |
| ダッシュボード | 操作ごとの時間（95 パーセンタイル） | Prometheus | success | operation=discard=11.517500784393414・operation=generate=131.9807649 |
| 警報 | アプリの停止（指標が届かない） | Prometheus | success | （結果なし） |
| 警報 | 5xx の割合の増加 | Prometheus | success | 全体=0 |
| 警報 | ERROR のログの増加 | Prometheus | success | 全体=0 |
| 警報 | 監査の書き込みの失敗 | Loki | success | （結果なし） |
| 警報 | ログインの応答の遅れ | Prometheus | success | 全体=NaN |
| 警報 | トークンの更新の応答の遅れ | Prometheus | success | 全体=NaN |
| 警報 | 確認用 API の遅れ | Prometheus | success | 全体=NaN |
| 警報 | コネクションプールの待ち | Prometheus | success | 全体=0 |
| 警報 | JVM のメモリの不足の兆し | Prometheus | success | 全体=0.046238650935687936 |
| 警報 | 管理画面への拒否の増加 | Prometheus | success | 全体=0 |
| 警報 | ロックの多発 | Loki | success | （結果なし） |
| 警報 | 回り込みの試み | Loki | success | （結果なし） |
| 警報 | 送り元の不一致の多発 | Loki | success | （結果なし） |
| 警報 | 監査の書き込みの遅れ | Loki | success | （結果なし） |
| 警報 | トークンの削除の失敗 | Loki | success | （結果なし） |
| 警報 | 照合の時間の範囲外 | Loki | success | （結果なし） |

- 応答時間の式（ログイン・トークンの更新・確認用 API）が `NaN` なのは、直近 5 分にその要求が無く、`rate` が 0 どうしの割り算になるため（要求があれば値になる）。
- 「アプリの停止（指標が届かない）」は `absent(...)` のため、アプリが動いているときは結果が空になるのが正しい。
- ログから数える警報（7 件）は、確かめの時間に該当の出来事が無く空。

## Sources

- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/observability-setup/observability-setup-questions.md`（Q1〜Q4、確認済みの要約）
- 前の Intent の同じ段の記録（正とする）: `aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/`
- `docker/monitoring/dashboards/mastersmith-overview.json`、`docker/monitoring/provisioning/alerting/mastersmith.yaml`、`compose.yaml`（`lgtm`）、`README.md`（「手元の監視（Grafana）」「DSL の管理」）
- `backend/src/main/java/cherry/mastersmith/config/ObservabilityConfig.java`、`backend/src/main/java/cherry/mastersmith/dslmanage/service/DslOperationMetrics.java`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/u4-dsl-management/nfr-design/observability-design.md`、`construction/build-and-test/build-and-test-summary.md`（OBS-DASH・U4-STORAGE-RUN）
- 実行したコマンド: `docker compose --profile monitoring up -d lgtm`、`docker compose --profile targetdb-postgres up -d app`、`docker exec mastersmith-lgtm-1 curl -s localhost:9090/api/v1/query?...`・`localhost:3100/loki/api/v1/query?...`（すべての式を1つずつ実行）、`colima ssh -- sudo dmesg`、`docker exec mastersmith-lgtm-1 cat /sys/fs/cgroup/memory.events`

## Assumptions & Open Questions

None.
