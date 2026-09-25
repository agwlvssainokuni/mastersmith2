# 配備の記録（deployment-log）

2026-09-25、開発者の PC 上のコンテナ（`compose.yaml`、プロジェクト名 `mastersmith`）へ配備した。手順は `aidlc/spaces/default/intents/260925-storage-memory-fixes/operation/deployment-pipeline/deployment-strategy.md` 2節、構成は同じフォルダの `cd-config.md`。決定は `deployment-execution-questions.md`（Q1: A 今すぐ行う、まとめの確認は Looks correct）。

## 1. 配備した版

| 項目 | 値 |
|---|---|
| 配備した版 | `develop` の `581b006`（作業ブランチ `fix/260925-storage-memory-fixes` を fast-forward で取り込んだ先頭。アプリの中身は C4 `5769cc1` と同じ） |
| 新しいイメージ | `mastersmith:local`（`sha256:305fddf4aa93f…`） |
| 戻し先 | `mastersmith:pre-storage-memory`（`sha256:a9cfa9dc97544…`、前の Intent の版） |
| コンテナの上限 | メモリ 2,147,483,648（2g）・CPU 4,000,000,000（4） |
| JVM | `-XX:MaxRAMPercentage=50.000000`、`MaxHeapSize=1073741824`（1,024MiB） |

## 2. 手順の記録

| 順 | 手順 | 時刻 | 結果 |
|---|---|---|---|
| 1 | アプリのソースの未コミットの変更の確かめ | 19:26 | 無し（ワークフローの記録だけ） |
| 2 | `develop` へ fast-forward | 19:26 | `9773cb9` → `581b006`（8 コミット。merge コミットなし） |
| 3 | `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` | 19:26〜19:30 | 成功（4 分 27 秒）。単体 733・結合 385 件、失敗・誤り・飛ばし 0 |
| 4 | `docker tag mastersmith:local mastersmith:pre-storage-memory` | 19:30 | `sha256:a9cfa9dc97544…` |
| 5 | `docker compose up -d --build app` | 19:30:48 | 終わりの値 0。新しいイメージ `sha256:305fddf4aa93f…`、戻し先は元の ID のまま |
| 6 | healthy になるまで | 19:30:58 | 約 10 秒で `healthy` |
| 7 | 最大ヒープの確かめ（JDK のイメージから `jcmd 1 VM.flags`） | 19:31 | `MaxHeapSize=1073741824` |
| 8 | 詰め直しの道具（`status` → `compact` → `status`） | 19:31 | `health-check-report.md` 2節 |
| 9 | スモークテスト | 〜19:52 | `smoke-test-results.md`。すべて通過 |
| 10 | 配備の完了 | 19:52 | `581b006` が動いている版 |

- アプリが止まっていたのは、手順 5 の入れ替えの間の約 10 秒。見本の対象DB（`mastersmith-targetdb-postgres-1`）には触れていない。
- 配備の前の k6 と内部DB のバックアップは省いた（Deployment Pipeline の決まり。Build and Test で同じソースのイメージを試験済み、スキーマの変更なし）。

## 3. 戻し方の前提の確かめ（`rollback-runbook.md` 4節）

- 戻し先のイメージ `mastersmith:pre-storage-memory` を、使い捨てのコンテナ（一時のボリューム `mastersmith-rollback-check`、番号は公開しない、同じ `.env`）で起動し、約 8 秒で `/actuator/health` が 200 になった。ログ 44 行に ERROR は無い。終わったらコンテナとボリュームを消した。
- 配備した内部DB のデータでの起動は確かめていない（スキーマが変わらないため）。未確認のまま残る前提で、次に確かめる機会は実際に戻すとき（project.md の Corrections）。
- 1回目の確かめでは、イメージに健全性の確かめ（HEALTHCHECK）が無く、起動の直後に「running」で終わったため判定に使えなかった。compose と同じ bash の `/dev/tcp` の確かめでやり直した。

## 4. 統合とプッシュ

- `develop` は `581b006`。`origin` へのプッシュは依頼者が行う（team.md）。プッシュの後の GitHub Actions の CI の結果を確かめる。
- 配備の前に、この段の質問と確認の記録を依頼者の承認なしでコミットした（`581b006`）。依頼者に伝え、了承を得た（「ok」）。

## Sources

- `aidlc/spaces/default/intents/260925-storage-memory-fixes/operation/deployment-pipeline/cd-config.md`
- `aidlc/spaces/default/intents/260925-storage-memory-fixes/operation/deployment-pipeline/deployment-strategy.md`
- `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/build-and-test/test-results.md`
- 生の結果（コミットしない）: `build/perf-results/deploy/`
