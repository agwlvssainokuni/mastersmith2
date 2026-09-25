# 配備の流れの構成（cd-config）

Intent 260925-storage-memory-fixes の配備の流れ。配備先はこれまでどおり開発者の PC 上のコンテナ（`compose.yaml`、プロジェクト名 `mastersmith`）で、自動の配備は持たず、依頼者の承認のうえで手で配備する（team.md・project.md の Deployment）。CI Pipeline・Infrastructure Design の段が無いため、前の Intent の配備の手順（`aidlc/spaces/default/intents/260924-followup-fixes/operation/deployment-pipeline/`）と README の「コンテナでの起動と確認」を正とし、今回の差だけを書く。決定は `deployment-pipeline-questions.md`。

## 1. 配備する版と、今動いている版

| 項目 | 値 |
|---|---|
| 今動いている版 | 前の Intent（260924-followup-fixes）で配備した版。イメージ `mastersmith:local`（`sha256:a9cfa9dc…`） |
| 見本の対象DB | `mastersmith-targetdb-postgres-1`（今回は触らない） |
| 配備する版 | 作業ブランチ `fix/260925-storage-memory-fixes` を `develop` へ fast-forward で取り込んだ後の `develop` の先頭（D9）。アプリの中身は C4 `5769cc1` と同じ |
| 戻し先 | 配備の前に今のイメージに付けるタグ `mastersmith:pre-storage-memory` |

## 2. 今回の変更と、配備への影響

| 変更 | 配備への影響 |
|---|---|
| HikariCP の JMX と一時停止を有効にする（`application.yaml`、FR1） | イメージの中の設定。JMX は外に公開しない。何もしなければ動きは今と同じ |
| 最大ヒープの割合 75% → 50%（`Dockerfile`、FR2） | この PC の `.env` に `MASTERSMITH_JAVA_OPTIONS` の行が無いため、イメージの既定（50%、1,024MiB）が効く |
| 詰め直しの道具 `docker/hikari-pool.sh`（FR1） | リポジトリに入るだけ。配備の後の確かめで `status` と `compact` を1回流す（Q1: A） |
| テストの JVM の IPv4・`perf/` の直し（FR3・FR4） | 配備には関わらない |

- 内部DB のスキーマは変わらない（Flyway の新しい版は無い）。`compose.yaml`・`.env`・`.env.targetdb` も変わらない。

## 3. 流れ

| 順 | 段 | すること | 行う人 | 承認 |
|---|---|---|---|---|
| 1 | 統合 | 作業ブランチを `develop` へ fast-forward で取り込む（`git switch develop && git merge --ff-only fix/260925-storage-memory-fixes`） | AI | 依頼者の承認（Deployment Execution の段で改めて確かめる） |
| 2 | 検査 | `develop` で `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（配備する WAR を作るため） | AI | — |
| 3 | 戻し先のタグ | `docker tag mastersmith:local mastersmith:pre-storage-memory` | AI | — |
| 4 | 入れ替え | アプリのイメージを作り直し、アプリだけを起動し直す | AI | 依頼者の承認 |
| 5 | 確かめ | healthy、最大ヒープ、スモークテスト、道具の `status`・`compact` | AI と依頼者 | — |
| 6 | プッシュと CI | `origin` へ `develop` をプッシュし、GitHub Actions の CI の結果を確かめる | 依頼者がプッシュ、AI が結果を確かめる | — |

- 配備の前の k6 は省く。Build and Test で、配備するものと同じソースのイメージ（`mastersmith:storage-memory-fix`）を配備と同じ上限で試験済み（project.md の Deployment の決まり）。
- 内部DB のバックアップは取らない（スキーマが変わらず、戻すときもデータはそのまま使えるため）。
- CI が失敗したら、次の作業に進む前に原因を直す（team.md の Way of Working）。

## 4. 成果物と版の識別

- 成果物は、フロントエンドの `dist` を同梱した実行可能 WAR から作るイメージ `mastersmith:local`（team.md の Deployment）。
- 版はコミットのハッシュで識別し、Deployment Execution の記録に残す。`main` へのタグ付けはリリースのときに依頼者の承認で行う（この Intent では行わない）。
- 試験のイメージ（`mastersmith:storage-memory-base`・`mastersmith:storage-memory-fix`）は配備に使わない。配備の後に不要なら消してよい（依頼者の判断）。

## Sources

- `aidlc/spaces/default/intents/260925-storage-memory-fixes/operation/deployment-pipeline/deployment-pipeline-questions.md`（Q1・まとめの確認）
- `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/code-generation-plan.md`（D9 の統合の方法）
- `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/build-and-test/test-results.md`（配備と同じソースのイメージでの負荷の試験）
- `aidlc/spaces/default/intents/260924-followup-fixes/operation/deployment-pipeline/`（前の Intent の配備の手順。正とする）
- `README.md`（コンテナでの起動と確認、内部DBのファイルの詰め直し）

## Assumptions & Open Questions

- この PC の `.env` に `MASTERSMITH_JAVA_OPTIONS` の行が無いことは名前だけで確かめた（値は開いていない）。配備の後に JVM の最大ヒープを確かめる。
