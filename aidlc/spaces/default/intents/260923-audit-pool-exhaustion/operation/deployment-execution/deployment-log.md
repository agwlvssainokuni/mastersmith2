# 配備の記録（deployment-log）

Intent `260923-audit-pool-exhaustion`（F2 の修正）の配備の記録。手順は `aidlc/spaces/default/intents/260923-audit-pool-exhaustion/operation/deployment-pipeline/deployment-strategy.md`、決定は `deployment-execution-questions.md`（Q1: A、Q2: A）による。配備は依頼者の承認のもとで AI がコマンドを実行し、パスワードが要るスモークテストは依頼者が行った。

## 1. 配備した版

| 項目 | 値 |
|---|---|
| 配備した版（控えたコミットのハッシュ） | **`3287050`**（`develop` の先頭。修正は `d948544`。その後のコミットは記録だけで、アプリのソースは同じ） |
| 直前の版 | `7040876`（前の Intent で配備した版） |
| 成果物 | `backend/build/libs/mastersmith.war`（`./gradlew verify` で作ったもの。中の `application.yaml` の 106 行が `maximum-pool-size: ${MASTERSMITH_DB_MAXIMUM_POOL_SIZE:30}`） |
| イメージ | `mastersmith:local`（`docker compose up -d --build` で作り直した） |
| スキーマの変更 | 無し（V1〜V4 のまま） |
| `.env` | 変えていない。`MASTERSMITH_DB_MAXIMUM_POOL_SIZE` は設定していない（既定の 30 が効く） |

## 2. 手順と結果（時刻は UTC）

| 順 | 手順 | 時刻 | 結果 |
|---|---|---|---|
| 1 | 作業ツリーの確認 | 06:07 | アプリのソースに未コミットの変更は無い。`git status` に出たのは、監査ログとこの段の記録のディレクトリだけ（ワークフローの記録であり、アプリのソースではない） |
| 2 | ハッシュを控える | 06:07 | `3287050` |
| 3 | `./gradlew verify` | 06:07〜06:09 | BUILD SUCCESSFUL（1分44秒）。WAR は UP-TO-DATE（ソースが Build and Test の時と同じ） |
| 4 | 負荷の確かめ（FR6.2、配備の前） | 06:10〜06:12 | **合格**。詳細は `health-check-report.md` 2節。前の版のアプリは 06:10 に止め、確かめの間は止めたままにした |
| 5 | ボリュームのバックアップ | 06:12 | `mastersmith-data-202609231512.tgz`（7,197 バイト、ルート、Git 管理外）。中身は読んでいない |
| 6 | `docker compose up -d --build` | 06:12:43 | コンテナを作り直して起動 |
| 7 | healthy | 06:12:51 | `healthy`（起動から約 8 秒） |
| 8 | スモークテスト | 06:13〜06:15 | 7 項目すべて合格（`smoke-test-results.md`） |
| 9 | 配備の完了 | 06:15 | いま動いている版は `3287050` |

- ボリュームを消す操作（`docker compose down -v`）は、配備した環境には一度も行っていない。`down -v` は使い捨ての環境（`mastersmith-perf`）の片付けにだけ使った。
- 使い捨ての環境の一時の環境ファイル（仮の署名鍵・仮のパスワード）は、リポジトリの外に作り、確かめの後に消した。値は表示していない。

## 3. 設計との差

| 差 | 内容 | 扱い |
|---|---|---|
| 負荷の確かめの時点 | 要件 FR6.2 は「配備した後」。依頼者の決定（Deployment Pipeline の Q2: A）で配備の前に行った | `aidlc/spaces/default/intents/260923-audit-pool-exhaustion/operation/deployment-pipeline/cd-config.md` 4節に記録済み。要件は書き換えていない |
| 戻しの練習 | 行っていない（Deployment Pipeline の Q3: B） | 未確認の前提と次の機会は `aidlc/spaces/default/intents/260923-audit-pool-exhaustion/operation/deployment-pipeline/rollback-runbook.md` 5節のとおり |

## 4. Build and Test から引き継いだ目標

| 目標 | 判定 | 根拠 |
|---|---|---|
| FR6.1 配備した後のスモークテスト | **Met** | `smoke-test-results.md` |
| FR6.2 k6 の同時 10 件のログインで監査が欠けない | **Met** | `health-check-report.md` 2節（ログイン 400 件、`LOGIN_SUCCEEDED` 400 件） |
| NFR2 既定値 30 で、1g の中で起動・スモークテスト・FR6.2 が通る | **Met** | `health-check-report.md` 1・2節（負荷の中で 353.8MiB、配備した後 287.6MiB、OOMKilled なし） |

## Sources

- `deployment-execution-questions.md`（配備の前の確認、Q1・Q2、要約）
- `aidlc/spaces/default/intents/260923-audit-pool-exhaustion/operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`
- `aidlc/spaces/default/intents/260923-audit-pool-exhaustion/construction/build-and-test/test-results.md`・`build-and-test-summary.md`
- 実行したコマンドの出力（`git`、`./gradlew verify`、`docker compose`、`docker inspect`、`docker stats`、k6）

## Assumptions & Open Questions

- None.
