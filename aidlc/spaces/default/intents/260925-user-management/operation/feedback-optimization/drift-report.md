# 設定のずれ（drift-report）

動いている環境を読み取りだけで確かめ、記録した設計の値と1つずつ比べました（Q3: A、2026-09-29 02:22）。`.env` は開かず、項目の名前の件数だけを見ました。

## 1. 比べた結果

| 項目 | 記録した値（出典） | 実際 | ずれ |
|---|---|---|---|
| 配備したイメージ | `mastersmith:local`・`sha256:9e5243a30b77…`（`deployment-log.md` 1節） | 同じ | 無し |
| アプリの上限 | メモリ 2g・CPU 4、`restart: "no"`（`environment-inventory.md`・`cd-config.md`） | `2147483648`・`4000000000`・`no` | 無し |
| アプリの状態 | healthy、OOMKilled なし | healthy（02:14:41 に Performance Validation の後に起動し直した）・`OOMKilled=false` | 無し |
| 内部DB の版 | Flyway 8（V7・V8 を当てた、`deployment-log.md` 3節） | `Successfully validated 8 migrations`・`Current version … 8` | 無し |
| 見本の PostgreSQL | `postgres:18.6`（ダイジェスト固定）、512MiB | 同じ | 無し |
| `.env` の4行（配備で足した） | `MASTERSMITH_WEB_BASE_URL`・`SPRING_MAIL_HOST`・`SPRING_MAIL_PORT`・`MASTERSMITH_MAIL_FROM` が1件ずつ（`deployment-log.md` 手順 7） | 有効 1 件ずつ | 無し |
| `.env` の外部エクスポートの2行 | コメントに戻す（Observability Setup の Q4: A） | 有効 0・コメント 1 件ずつ | 無し |
| `.env` の権限 | 600 | 600 | 無し |
| 招待を使える設定 | `enabled: true`（`deployment-log.md` 3節） | 起動のログで `enabled: true` | 無し |
| 戻し先のイメージ | `mastersmith:pre-user-management`・`sha256:305fddf4aa93…`（`deployment-log.md` 1節） | 同じ | 無し |
| 配備の前のバックアップ | `~/.mastersmith-backup/mastersmith-data-202609290030-before-user-management.tgz`（15,001 B） | 同じ大きさで残る | 無し |
| 配備の前の `.env` の複写 | `~/.mastersmith-env-before-user-management`（600） | 600 で残る | 無し |
| Mailpit | 見たいときだけ起動（Incident Response の Q3: A）。配備の確かめの後に止めて消した（`health-check-report.md` 4節） | 動いていない | 無し。Environment Provisioning の時点（`environment-inventory.md` 2節）では動いていたが、その後の決定どおり |
| 手元の監視 | 見たいときだけ起動。`GF_USERS_VIEWERS_CAN_EDIT` を足した（Observability Setup） | 止まっている。`compose.yaml` に足した値がある | 無し |
| ボリューム | `mastersmith_mastersmith-data`・`-monitoring`・`-targetdb-postgres` | 同じ3つ。使い捨ての環境のボリューム（`mastersmith-perf_*`）は残っていない | 無し |
| リポジトリ | 配備した版 `83b572b` | develop の HEAD は `fb496e5`。`83b572b` の後のアプリの外の変更は `README.md`・`compose.yaml`（lgtm の環境変数）・ダッシュボードだけで、アプリのイメージに入るソースは変わっていない | 無し（イメージの作り直しは要らない） |

## 2. 記録どうしの食い違い（直さず記録したもの）

| 食い違い | 扱い |
|---|---|
| Observability Setup の `alarms.md` 1節「送信の失敗は既存の警報で拾う」は誤り（送信の失敗は 201・200 と WARN・INFO で、どの警報にも当たらない） | Incident Response の Q4: A。`runbooks.md` 2節を正とする |
| Observability Setup の `log-queries.md` 3節・Incident Response の `runbooks.md` の RB-17「形の誤ったトークンの拒否は監査に残らない」は、登録の完了については誤り（監査に残る） | Performance Validation の承認の場の決定「記録だけ」。`test-results.md` 6節を正とする |
| 前の Intent の `alarms.md` 3節「p95 が NaN なのは直近 5 分に要求が無いため」は誤り（バケットが無いため） | この Intent の `dashboards.md` 2節に記録 |
| Performance Validation の `test-results.md` を、承認を記録した後に1行直した（6節の扱いを、承認の場の決定「記録だけ」に合わせた） | 中身は承認の場の決定の反映だけ。完了した段の結果が変わった知らせが出たが、やり直さずに進めた（この段の始めに依頼者に伝えた） |

## 3. 結論

- 動いている環境に、記録した設計の値とのずれはありません。
- 直すものは無いため、承認の場で諮る事項はありません。記録どうしの食い違いは、上の表のとおり、それぞれの段で扱いを決め済みです。

## Sources

- `operation/feedback-optimization/feedback-optimization-questions.md`（Q3: A）
- `operation/deployment-execution/deployment-log.md`・`health-check-report.md`、`operation/environment-provisioning/environment-inventory.md`、`operation/deployment-pipeline/cd-config.md`
- `operation/observability-setup/`・`operation/incident-response/`・`operation/performance-validation/` の記録
- この段で実行したコマンドの出力（`docker compose ps`・`docker inspect`・`docker images`・`docker volume ls`・`grep -c` による `.env` の項目の件数・`ls -l`・`docker compose logs app` の件数・`git diff --stat 83b572b..HEAD`）

## Assumptions & Open Questions

None.
