# 配備の方式（deployment-strategy）

## 1. 変えないもの

- 方式は、止めて入れ替える形（recreate）。開発者の PC 上の1台のコンテナで、入れ替えの間（数十秒〜2分）はアプリに届かない。前の Intent と同じ。
- 内部DB のボリューム（`mastersmith_mastersmith-data`）はそのまま使う。見本の対象DB は触らない。
- 手元の監視（`observability` の profile）は起動しない。

## 2. 配備の手順（手で行う）

| 順 | 手順 | コマンド | 通る条件 |
|---|---|---|---|
| 1 | 作業ツリーにアプリのソースの未コミットの変更が無いことを確かめる | `git status --porcelain` | アプリのソースに変更が無い（ワークフローの記録と監査ログのディレクトリは除く。project.md の Deployment） |
| 2 | `develop` へ取り込み、配備する版を控える（D9） | `git switch develop && git merge --ff-only fix/260925-storage-memory-fixes && git rev-parse --short HEAD` | fast-forward で終わる。ハッシュを控えた |
| 3 | 検査を通して WAR を作る | `DOCKER_HOST=… TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=… caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` | 成功し、対象DB のテストを飛ばしていない |
| 4 | 戻し先のタグを付ける | `docker tag mastersmith:local mastersmith:pre-storage-memory` | `docker image inspect mastersmith:pre-storage-memory --format '{{.Id}}'` が `sha256:a9cfa9dc…` |
| 5 | アプリのイメージを作り直し、アプリを起動し直す | `docker compose up -d --build app` | エラーなく終わる。`mastersmith:local` が新しいイメージになり、`mastersmith:pre-storage-memory` は元の ID のまま |
| 6 | 健全になるまで待つ | `docker compose ps app` | `healthy`（最長で約 2 分） |
| 7 | 最大ヒープを確かめる | 同じ版の JDK のイメージを PID の名前空間を共有して動かし `jcmd 1 VM.flags`（`perf/README.md` の NMT の手順と同じ形）で `MaxHeapSize` を読む | 1,073,741,824（1,024MiB。上限 2g の 50%） |
| 8 | 道具を流す（Q1: A） | `./docker/hikari-pool.sh status`、続けて `./docker/hikari-pool.sh compact`、もう一度 `status` | `status` で MBean が見え（接続の本数と大きさが出る）、`compact` が終わりの値 0 で「再開しました」まで進む。その後も `healthy` |
| 9 | 手でスモークテストを行う | 3節 | すべての項目が通る |
| 10 | 配備の完了 | — | 手順 2 のハッシュが、いま動いている版の記録になる |

- 手順 8 の `compact` の間（Build and Test では 3.5〜4.9 秒）は、内部DB を使う要求が待たされる。詰め直しの操作は監査ログにもアプリのログにも残らない（依頼者の決定 Q3）。道具の出力を Deployment Execution の記録に残す。

## 3. スモークテスト

前の Intent（`aidlc/spaces/default/intents/260924-followup-fixes/operation/deployment-pipeline/deployment-strategy.md` 3節）の項目を使う。今回は画面・ログイン・DSL のコードを変えていないため、次に絞る。

| 項目 | 確かめ方 | 合格の基準 | 行う人 |
|---|---|---|---|
| 健全性 | `curl -s http://localhost:8080/actuator/health` | `{"status":"UP"}` | AI |
| ログイン画面・ログイン・管理者向け領域 | ブラウザで `http://localhost:8080/`、初期管理者でログイン、サイドバーの「管理」 | それぞれ表示される | 依頼者 |
| DSL の管理画面 | サイドバーの「DSL」を開く | 今の状態が表示される（「接続できない」にならない） | 依頼者 |
| ログアウト | ユーザーメニューのログアウト | ログイン画面に戻る | 依頼者 |
| ログ | `docker compose logs app` | ERROR が無い。すべて1行1件の JSON | AI |
| 詰め直しの後（手順 8 の後） | 健全性とログインをもう一度 | 通る | AI（健全性）・依頼者（ログイン） |

- パスワードが要る操作は依頼者が行う。AI はログで裏付け、個人に関する値は表示しない（project.md の Corrections）。
- **スモークテストの要求（ログイン・ログアウト）は監査ログに残り、追記だけで消せない。** 送る前に依頼者に伝える（project.md の Corrections）。

## 4. 中止して戻す条件

| 条件 | 見方 | 扱い |
|---|---|---|
| 約 2 分で `healthy` にならない、起動が失敗する | 手順 6、`docker compose logs app` | `rollback-runbook.md` 2節（イメージを戻す） |
| ログイン・管理者向け領域・DSL の画面のスモークテストが通らない | 3節 | `rollback-runbook.md` 2節 |
| `compact` の後に `healthy` に戻らない、要求が返らない | 手順 8 | `rollback-runbook.md` 3節（道具の戻し。`resume`、だめなら起動し直し） |
| 最大ヒープが 1,024MiB でない | 手順 7 | 配備は止めず、`.env` の `MASTERSMITH_JAVA_OPTIONS` の有無（名前だけ）を確かめて依頼者に諮る |

## 5. 運用の注意

- 内部DB のファイルは、動いている間は DSL の投入と適用（とトークンの更新の多い使い方。Build and Test の観察）で伸びる。README の「内部DBのファイルの詰め直し」の手順で、アプリを止めずに `./docker/hikari-pool.sh compact` で詰め直す。

## Sources

- `aidlc/spaces/default/intents/260925-storage-memory-fixes/operation/deployment-pipeline/deployment-pipeline-questions.md`（Q1・まとめの確認）
- `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/code-generation-plan.md`（D9 の統合の方法）
- `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/build-and-test/test-results.md`（配備と同じソースのイメージでの負荷の試験）
- `aidlc/spaces/default/intents/260924-followup-fixes/operation/deployment-pipeline/`（前の Intent の配備の手順。正とする）
- `README.md`（コンテナでの起動と確認、内部DBのファイルの詰め直し）
