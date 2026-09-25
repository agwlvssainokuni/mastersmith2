# 配備の流れの構成（cd-config）

Intent 260924-followup-fixes の配備の流れ。配備先はこれまでどおり開発者の PC 上のコンテナ（`compose.yaml`、プロジェクト名 `mastersmith`）で、自動の配備は持たず、依頼者の承認のうえで手で配備する（team.md・project.md の Deployment）。CI Pipeline・Infrastructure Design の段が無いため、前の Intent の配備の手順（`aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/deployment-pipeline/`）と README の「コンテナでの起動と確認」を正とし、今回の差だけを書く（project.md の Deployment）。決定は `deployment-pipeline-questions.md`。

## 1. 配備する版と、今動いている版

| 項目 | 値 |
|---|---|
| 今動いている版 | 前の Intent（260923-dsl-schema-loader）で配備した版。イメージ `mastersmith:local`（`sha256:1585bd4e…`、2026-09-24 作成） |
| 見本の対象DB | `mastersmith-targetdb-postgres-1`（`postgres:18.6`、公式イメージの入口のまま）。ボリューム `mastersmith_mastersmith-targetdb-postgres` |
| 配備する版 | 作業ブランチ `fix/260924-followup-fixes` を `develop` へ fast-forward で取り込んだ後の `develop` の先頭（Q1: A）。アプリの中身は C6 `295407f` と同じ |
| 戻し先 | 配備の前に今のイメージに付けるタグ `mastersmith:pre-followup`（Q4: B） |

## 2. 今回の変更と、配備への影響

| 変更 | 配備への影響 |
|---|---|
| ログイン（FR2）・ログの出力（FR1・FR4）・画面（FR7、サブモジュール `edb1f94`） | アプリのイメージを作り直す。内部DB のスキーマは変わらない（Flyway の新しい版は無い） |
| `compose.yaml` のメモリの上限の既定 2g（FR5） | この PC の `.env` には `MASTERSMITH_CONTAINER_MEMORY` の行があり、その値が効く（値は開いていない） |
| 見本の対象DB のパスワードを `.env.targetdb` から読む（FR6） | この PC の `.env` の `MASTERSMITH_SAMPLE_TARGETDB_` の2行を `.env.targetdb` へ移す（Q2: A）。見本の対象DB のコンテナを新しい起動の入口で作り直す（Q3: A）。ボリュームがあるため DB の中身とパスワードは初期化されず今のまま |
| 負荷の試験の台本と文書（FR3・FR8） | 配備には関わらない |

## 3. 流れ

| 順 | 段 | すること | 行う人 | 承認 |
|---|---|---|---|---|
| 1 | 統合 | 作業ブランチを `develop` へ fast-forward で取り込む（`git switch develop && git merge --ff-only fix/260924-followup-fixes`）。squash しない（サブモジュールの専用のコミットを残す。Code Generation の計画の承認） | AI | 依頼者の承認（Deployment Execution の段で改めて確かめる） |
| 2 | 検査 | `develop` で `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（Build and Test で同じ中身が通っているが、配備する WAR を作るため） | AI | — |
| 3 | 戻し先のタグ | `docker tag mastersmith:local mastersmith:pre-followup` | AI | — |
| 4 | `.env` の移し替え | `deployment-strategy.md` 2節の手順 4〜6（値を表示しない） | AI（Q2: A） | 依頼者に伝えてから |
| 5 | 入れ替え | アプリのイメージを作り直し、アプリと見本の対象DB を起動し直す | AI | 依頼者の承認 |
| 6 | 確かめ | healthy、上限、環境変数の名前、スモークテスト | AI と依頼者 | — |
| 7 | プッシュと CI | `origin` へ `develop` をプッシュし、GitHub Actions の CI の結果を確かめる | 依頼者がプッシュ、AI が結果を確かめる | — |

- 配備の前の k6 は省く。Build and Test の段で、配備するものと同じソースから作ったイメージ（`mastersmith:followup-fixes`）を配備と同じ上限で試験済みのため（project.md の Deployment の決まり）。配備の後の healthy とスモークテストで確かめる。
- 内部DB のバックアップは取らない（スキーマが変わらず、戻すときもデータはそのまま使えるため）。
- CI が失敗したら、次の作業に進む前に原因を直す（team.md の Way of Working）。

## 4. 成果物と版の識別

- 成果物は、フロントエンドの `dist` を同梱した実行可能 WAR から作るイメージ `mastersmith:local`（team.md の Deployment）。
- 版はコミットのハッシュで識別する。配備した版のハッシュを Deployment Execution の記録に残す。`main` へのタグ付けはリリースのときに依頼者の承認で行う（この Intent では行わない）。
- 試験のイメージ `mastersmith:followup-fixes` は配備に使わない。配備の後に不要なら消してよい（依頼者の判断）。

## Sources

- `aidlc/spaces/default/intents/260924-followup-fixes/operation/deployment-pipeline/deployment-pipeline-questions.md`（Q1〜Q4、F1）
- `aidlc/spaces/default/intents/260924-followup-fixes/inception/requirements-analysis/requirements.md`（FR5・FR6）
- `aidlc/spaces/default/intents/260924-followup-fixes/construction/build-and-test/test-results.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/deployment-pipeline/`（前の Intent の配備の手順）
- `README.md`（コンテナでの起動と確認、`.env` からの移し替え）

## Assumptions & Open Questions

- この PC の `.env` の `MASTERSMITH_CONTAINER_MEMORY` の値は開いていない。前の Intent の記録では 2g。配備の後に `docker inspect` の上限で確かめる。
- 見本の対象DB を作り直しても、ボリュームがあるため初期化の台本は走らず、DB のパスワードは今のまま。移した値が今の DB のパスワードと同じであることを、アプリから対象DB に接続できることで確かめる。
