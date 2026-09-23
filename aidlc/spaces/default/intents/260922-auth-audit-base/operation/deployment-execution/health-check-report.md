# 健全性の確認の報告（health-check-report）

Intent `260922-auth-audit-base`（auth-audit-foundation）を版 `7040876` で配備した後の、アプリの健全性と、配備の段が持ち主の未検証の目標の判定。基準は `operation/deployment-pipeline/deployment-strategy.md`（deployment-strategy）4節の中止の条件、`operation/deployment-pipeline/cd-config.md`（cd-config）、`operation/environment-provisioning/environment-inventory.md`（environment-inventory）、`construction/build-and-test/test-results.md`（build-test-results）の Target Verification Matrix である。

## 1. 健全性

| 項目 | 基準 | 結果 | 判定 |
|---|---|---|---|
| コンテナのヘルスチェック | 約 2 分以内に `healthy`（`deployment-strategy.md` 2節の手順6） | 配備の起動で約 9 秒、スモークテストの後の起動し直しで約 9 秒で `healthy`。失敗 0 回 | 合格 |
| `/actuator/health` | 200・`UP` | `200 {"status":"UP"}`（配備の直後と、起動し直した後の2回） | 合格 |
| 起動のログ | 起動の失敗が無い、ERROR が無い | `Started MastersmithApplication in 5.85 seconds`。ERROR 0 件 | 合格 |
| DB のスキーマ | 起動時の Flyway の確認が通る | `Successfully validated 4 migrations`、`Schema "PUBLIC" is up to date. No migration necessary.` | 合格 |
| 初期管理者 | 設定があれば作られる | INFO「初期管理者を作成しました」 | 合格 |
| 中止の条件（`deployment-strategy.md` 4節） | どれにも当たらないこと | 当たらない | 合格（戻しは不要） |

## 2. 配備の段が持ち主の未検証の目標

`construction/build-and-test/test-results.md` の Target Verification Matrix で、持ち主を `deployment-execution` とした `Unverified` は1件である。

| ID | 目標 | 確かめ方 | 結果 | 判定 |
|---|---|---|---|---|
| U4-NFR3.3 | 監査ログの改ざんへの備えは、内部DBのファイルを OS の権限で守ること（コンテナのボリュームの権限をアプリの実行者だけに限る） | 配備したコンテナで `ls -ld /app/data`・`ls -l /app/data`・`id`・`umask` | `/app/data` は `drwx------ mastersmith mastersmith`（UID 10001）。アプリは UID 10001 で動く。中の `mastersmith.mv.db` は `-rw-r--r--`（umask 0022）だが、ディレクトリが所有者だけのため、他の利用者はファイルに届かない。環境の用意の段（`validation-report.md` 1節の 2）と同じ結果 | **Met** |

### 判定の注意

- 守りの範囲は「コンテナの中の他の利用者」である。ボリュームは colima の VM の中にあり、VM の root や Docker を操作できる者はファイルを読み書きできる。これは U4 の NFR3.6 で受け入れた危険（OS の権限を持つ者による直接の書き換えは検知できない）の範囲である。
- ボリュームの外に作ったバックアップ（リポジトリの直下の `mastersmith-data-*.tgz`）は、PC の上では `-rw-r--r--` になっている。U4-NFR3.3 の対象はボリュームの権限であり、判定には含めないが、`deployment-log.md` の「Assumptions & Open Questions」に扱いを残した。

## 3. 後の段へ引き継ぐもの

| 事項 | 持ち主の段 |
|---|---|
| 起動の時間（今回 5.85 秒の1回の観測）と、応答の時間の目標の測定。CPU の上限 2 で測るか 4 で測るか | performance-validation |
| ログの ERROR・遅れの WARN・ログインの失敗の割合などを、運用で見る指標にすること | observability-setup |
| 戻しの手順を通して確かめること | 次に版を替えるとき（Q3） |

## Sources

- `aidlc/spaces/default/intents/260922-auth-audit-base/operation/deployment-pipeline/deployment-strategy.md`（2節の手順6、4節の中止の条件）、`cd-config.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/operation/environment-provisioning/environment-inventory.md`、`validation-report.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/build-and-test/test-results.md`（Target Verification Matrix）、`build-and-test-summary.md`（U4-NFR3.3 の持ち主）
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u4-audit-log/nfr-requirements/security-requirements.md`（NFR3.3・NFR3.6）
- 2026-09-23 12:22〜12:28 の `docker compose`・`docker compose exec`・`curl`・`docker compose logs` の出力

## Assumptions & Open Questions

None.
