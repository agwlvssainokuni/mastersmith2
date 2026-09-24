# 受け手と権限の表（escalation-matrix）

障害の重さ（`incident-plan.md` 1節）ごとの受け手・対応の期限と、AI に頼めること・依頼者の承認が要る操作を決める（Q1: A）。

## 1. 重さごとの受け手と期限

| 重さ | 受け手・判断者 | 着手 | 戻すまでの期限 | その先の受け手 |
|---|---|---|---|---|
| 高 | 依頼者 | 気づいたらすぐ（ほかの作業より先） | 1日の内（RTO 1日。`incident-plan.md` 5節）。直す見込みが立たなければ戻す（`runbooks.md` の RB-10） | 無い（1名での運用） |
| 低 | 依頼者 | 当日か翌日 | 期限は置かない。直すか既知の制約として記録するかを依頼者が決める | 無い |

- 連絡の先: 依頼者本人。受け手が1名のため、連絡の手段・呼び出しの順・交代の決まりは置かない。連絡の先の個人の情報（メールアドレスなど）は、公開のリポジトリに書かない。
- 依頼者が対応できない間は、アプリは止まったまま・障害は残ったままになる。利用者も依頼者だけのため、これを受け入れる。
- コードの不具合で直しが要るときは、新しい Intent（bugfix の範囲など）を始める。受け手と判断者は同じ依頼者。

## 2. AI に頼めること（依頼者に頼まれたとき）

AI は、依頼者に頼まれたときだけ動く。自分から状態を変える操作はしない。

### 2.1 承認なしで行ってよい調べ（読み取りだけ）

| 調べ | コマンドの例 | 気をつけること |
|---|---|---|
| コンテナの状態 | `docker compose --profile targetdb-postgres ps`・`docker ps` | — |
| 止まった理由・健全性の記録 | `docker inspect mastersmith-app-1 --format '{{.State.Status}} {{.State.OOMKilled}} {{.State.ExitCode}} {{.State.FinishedAt}}'`・`docker inspect mastersmith-app-1 --format '{{json .State.Health}}'` | **`--format` で項目を絞る。** 絞らない `docker inspect` は環境変数（パスワード・署名鍵）を表示する |
| 上限と使用量 | `docker inspect mastersmith-app-1 --format '{{.HostConfig.Memory}} {{.HostConfig.NanoCpus}}'`・`docker stats --no-stream mastersmith-app-1` | — |
| ログ | `docker compose logs --no-log-prefix app`・`docker compose logs targetdb-postgres`（`grep` で絞る・件数を数える） | 監査の書き込みの失敗の ERROR と初期管理者の INFO にはメールアドレスが載る。報告には件数・種類・時刻だけを書き、値を書き写さない |
| 内部DB のファイルの大きさ | `docker run --rm -v mastersmith_mastersmith-data:/data:ro eclipse-temurin:25.0.4_7-jre-noble du -sh /data` | 読み取りだけ（`:ro`）。アプリは止めない |
| VM のディスクとメモリ | `colima list`・`colima ssh -- df -h /`・`colima ssh -- free -m` | — |
| 見本の対象DB の応答 | `docker compose exec targetdb-postgres pg_isready -U target_admin -d business` | パスワードは使わない |
| ヘルスチェック | `curl -s http://localhost:8080/actuator/health` | ログインなしの要求。監査には残らない |
| 手元の監視（起動しているとき） | Grafana の画面、`docker exec mastersmith-lgtm-1 curl -s 'localhost:9090/api/v1/query?...'`・`localhost:3100/loki/api/v1/query?...` | — |
| 監査の複写の読み取り | 既に `~/.mastersmith-backup/` にある複写を展開し、H2 の道具で読み取り（`ACCESS_MODE_DATA=r`）で開く（README の「監査ログの確かめ方」） | 展開した作業の複写は、終わったら消す。メールアドレス・接続元IPを報告に書き写さない |
| 手順書・設定・ソースの読み取り | README・`compose.yaml`・`docker/monitoring/`・`backend/src/main/java/` | — |

### 2.2 手順の実行の補助

- 依頼者と一緒に `runbooks.md` の手順をたどり、次に打つコマンドと、その結果の読み方を示す。
- 状態を変えるコマンド（2.3節）は、依頼者の承認を得てから AI が実行するか、依頼者が自分で実行する。
- 記録（`incident-plan.md` 3節）と振り返り（同 4節）の下書きを作る。

## 3. 依頼者の承認が要る操作

次の操作は、AI が実行する前に、何をするか・何が失われるか・戻し方を依頼者に示し、承認を得る。依頼者が自分で行うときは承認は要らないが、記録には残す。

| 操作 | 例 | 承認のときに示すこと |
|---|---|---|
| アプリ・見本の対象DB・監視を止める・起動し直す | `docker compose stop app`・`docker compose --profile targetdb-postgres restart app`・`docker compose --profile targetdb-postgres start targetdb-postgres` | 止まっている間の要求は失敗する（約 10 秒〜2分） |
| コンテナを作り直す | `docker compose --profile targetdb-postgres up -d app`（`.env` を直した後など） | **今のコンテナのログが消える**。先にログをリポジトリの外へ保存する（`runbooks.md` の 0節） |
| 版を戻す | `MASTERSMITH_IMAGE_TAG=pre-dsl docker compose up -d --no-build app`（`rollback-runbook.md` 2節） | DSL の管理画面が無い古い版になる。古い版の起動は未確認（U4-MIGRATION） |
| 内部DB のデータを戻す | バックアップの展開（`rollback-runbook.md` 4節、README の「内部DBのバックアップと戻し方」） | 最後の複写の後の記録（監査ログ・利用者・DSL の履歴）が失われる |
| 内部DB を複写する（アプリを止める） | README の「内部DBのバックアップと戻し方」・「監査ログの確かめ方」 | アプリが数秒〜数十秒止まる |
| ボリュームに関わる操作 | 見本の対象DB のボリュームの削除（`rollback-runbook.md` 6節）、`docker volume rm` | 内部DB のボリューム（`mastersmith_mastersmith-data`）は、障害の対応の中では消さない |
| `.env` に関わる操作 | 項目の値を直す・署名鍵を替える・`.env` の複写 | **`.env` は依頼者が編集する。AI は中身を開かない。** AI が確かめるときは、承認を得て、項目があるかどうかの件数（`grep -c '^<項目>=.' .env`）だけを出す |
| 監査に残る要求を送る | ログイン・DSL の操作・存在しない API への要求 | 監査ログに残り、消せない（`project.md` の Corrections） |
| colima の VM を止める・起動し直す・広げる | `colima stop`・`colima start --cpu 4 --memory 6` | 動いているコンテナがすべて止まる |
| イメージのタグを消す・付け替える | `docker rmi`・`docker tag` | 戻し先のイメージ `mastersmith:pre-dsl` は消さない（`rollback-runbook.md` 8節） |

## 4. AI がしないこと

- 承認の無い止める・戻す・データを戻す操作。
- `.env` を開く・表示する、秘密の値をコマンドや記録に書く。`docker compose config`（`--services` なし）・`docker compose exec app env`・項目を絞らない `docker inspect` も、環境変数の値を表示するため使わない。
- 秘密情報が要る操作（初期管理者でのログインなど）。依頼者が行い、AI は監査イベントとログで裏付ける（`project.md` の Corrections）。
- `git push`（`team.md` の Way of Working）と、承認の無い `git commit`（`project.md` の Change Control）。
- 配備した環境でのドライバーのログの有効化（接続情報がログに残る。README の「対象DB」）。
- ボリュームを消す操作をバックアップの前に行うこと（README の「消してはいけない操作」）。

## 5. 知らせ（通知）

- 今は通知の仕組みを持たない。警報（16 件、`docker/monitoring/provisioning/alerting/mastersmith.yaml`）は、手元の監視（profile `monitoring`）を起動しているときに Grafana の「Alerting」→「Alert rules」（フォルダー MasterSmith）で見るだけ（README の「手元の監視（Grafana）」）。
- 手元の監視を止めている間は、警報も評価されない。そのため、アプリを使う前の `docker compose --profile targetdb-postgres ps` が見つけるための主な手になる（`incident-plan.md` 2節）。
- 配備先が決まったら、通知の先（Grafana の連絡先など）と、重さごとの知らせ方を決める（`project.md` の Deployment）。

## Sources

- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/incident-response-questions.md`（Q1: A・Q3: A、確認済みの要約）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/incident-plan.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/deployment-pipeline/rollback-runbook.md`（2節・4節・6節・8節）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/environment-provisioning/environment-inventory.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/observability-setup/alarms.md`
- `README.md`（「コンテナでの起動と確認」「内部DBのバックアップと戻し方」「対象DB」「監査ログ（U4）」「監査ログの確かめ方」「消してはいけない操作」「手元の監視（Grafana）」「DSL の管理の API（U4）」）
- `compose.yaml`、`docker/monitoring/provisioning/alerting/mastersmith.yaml`
- `backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java`（ERROR に項目を載せる）、`backend/src/main/java/cherry/mastersmith/user/service/InitialAdminInitializer.java`（INFO に `email`）
- `aidlc/spaces/default/memory/team.md`（Way of Working）、`aidlc/spaces/default/memory/project.md`（Change Control・Deployment・Forbidden・Corrections）、`aidlc/spaces/default/memory/phases/operation.md`（Incident Response）

## Assumptions & Open Questions

- [assumption] `operation.md` は手順書に呼び出しの順（escalation path）と連絡の先を求めるが、受け手が依頼者1名のため、呼び出しの順は「無い」、連絡の先は「依頼者本人」とした。個人の連絡の情報は公開のリポジトリに書かない。
