# 環境の確認の報告（validation-report）

Intent `260922-auth-audit-base`（auth-audit-foundation）の配備先の環境（開発者の PC 上のコンテナ）が、基盤の設計どおりに用意されているかを、実際にコンテナを起動して確かめた結果（Q2）。基準は U1〜U4 の `construction/<単位>/infrastructure-design/infrastructure-specification.md`（infrastructure-specification）と `operation/deployment-pipeline/cd-config.md`（cd-config）である。用意した環境の一覧は `environment-inventory.md` にある。

- 確かめた日時: 2026-09-23 12:07〜12:08（日本時間）
- 手順: `./gradlew :backend:bootWar` → `docker compose up -d --build` → `docker compose up -d --wait`（healthy まで待つ）→ 下の各項目 → `docker compose stop`
- 判定: **合格**（差あり 1 件は依頼者の決定どおり。注意 3 件は次の段以降に引き継ぐ）

## 1. 項目ごとの結果

判定は「合格」「差あり（決定どおり）」「注意」の3つ。

| # | 項目 | 設計の値（出どころ） | 確かめ方 | 実際の値 | 判定 |
|---|---|---|---|---|---|
| 1 | 実行の利用者 | root 以外の専用の利用者 UID 10001（U1 の `infrastructure-specification.md` 1章） | `docker compose exec app id`、`docker image inspect`（`User`） | `uid=10001(mastersmith) gid=10001(mastersmith)`、イメージの `User=10001:10001` | 合格 |
| 2 | 内部DBの置き場所の権限 | `/app/data` は実行の利用者だけが読み書きできる（U1 の同 1章、U4 の `infrastructure-specification.md` 1章・NFR3.3） | `docker compose exec app ls -ld /app/data` | `drwx------ mastersmith mastersmith`。中の `mastersmith.mv.db` は `-rw-r--r--` だが、ディレクトリが所有者だけのため他の利用者は届かない | 合格 |
| 3 | 公開する番号 | 8080 だけを PC の `localhost` に結び付ける（U1 の同 1章） | `docker port`、`docker inspect`（`PortBindings`） | `8080/tcp -> 127.0.0.1:8080` だけ | 合格 |
| 4 | CPU の上限 | 4（U2 の `infrastructure-specification.md` 1章） | `docker inspect`（`NanoCpus`） | **2**（`.env` の `MASTERSMITH_CONTAINER_CPUS=2`） | 差あり（Q1 の決定どおり） |
| 5 | メモリの上限と JVM | 1GB、最大ヒープはメモリの 75%（U1 の同 1章） | `docker inspect`（`Memory`）、PID 1 の起動の引数 | `1073741824`、`-XX:MaxRAMPercentage=75.0` | 合格 |
| 6 | タイムゾーン | `Asia/Tokyo`（`TZ` と `-Duser.timezone` の両方。U2 の同 1章） | `docker compose exec app date`、起動の引数 | `JST` 表示、`-Duser.timezone=Asia/Tokyo`、ログの時刻も `+09:00` | 合格 |
| 7 | 起動と健全性 | `/actuator/health` が 200（UP）、起動は 30 秒以内（U1 の同 1章・NFR1.4） | `docker compose up --wait`、`curl http://127.0.0.1:8080/actuator/health`、起動のログ | `healthy`（失敗 0 回）、`200 {"status":"UP"}`、`Started MastersmithApplication in 5.37 seconds`（1回の観測。性能の測定ではない） | 合格 |
| 8 | 再起動の方針 | 異常で止まっても再起動しない（U1 の同 1章） | `docker inspect`（`RestartPolicy`） | `no` | 合格 |
| 9 | 停止 | 停止の猶予 45 秒、`exec` 形式で SIGTERM を Java が受け取る（U1 の同 1章） | `docker inspect`（`StopTimeout`）、`docker compose stop` の後のログと終了コード | `45`、ENTRYPOINT は exec 形式、停止の合図から 1 秒未満で終了（終了コード 143）、コネクションプールと JPA を閉じるログが出た | 合格 |
| 10 | ログ | 1行1件の JSON。保存は json-file、10MB × 3（`compose.yaml`、U1 の観測性の設計） | `docker compose logs --no-color` の全行を JSON として読む、`docker inspect`（`LogConfig`） | 全行が JSON として読める。`json-file`、`max-size 10m`、`max-file 3` | 合格 |
| 11 | ベースイメージ | 版の番号まで固定（U1 の同 1章 Q2） | `Dockerfile`、`docker image inspect` | `eclipse-temurin:25.0.4_7-jre-noble`、Java 25.0.4 | 合格 |
| 12 | イメージに秘密情報が入らない | 秘密情報はイメージに入れず、実行時に環境変数で渡す（U1 の同 3章） | `.dockerignore`、`docker image inspect`（`Env`）、`docker history` | `.dockerignore` は WAR 以外を送らない。イメージの環境変数と層の記録に署名鍵・`.env` の記述は 0 件 | 合格 |
| 13 | ログに秘密情報が出ない | 署名鍵などをログに出さない（project.md の Forbidden） | 起動から停止までのログを署名鍵の値で検索（値は表示しない） | 0 件 | 合格 |
| 14 | health 以外の Actuator に届かない | health だけを公開（U3 の `cicd-pipeline.md` 1章の確認項目） | `curl` で `/actuator`・`/actuator/env`・`/actuator/info`・`/actuator/metrics`・`/actuator/prometheus` | すべて 404。画面の `/` は 200 | 合格 |
| 15 | 外部エクスポートが既定で無効 | 既定は無効、配備の設定で切り替える（team.md の Deployment） | コンテナの環境変数の名前、起動のログ | `MASTERSMITH_OBSERVABILITY_EXPORT_ENABLED` は渡していない（既定 false）。OTLP の送信のログは無い | 合格 |
| 16 | `.env` の扱い | Git 管理外、コミットしない（project.md の Forbidden・Mandated） | `git status --ignored .env`、`ls -l .env` | 無視の対象（`!! .env`）、権限 `-rw-------` | 合格 |
| 17 | 初期管理者 | 設定が無い・正しくないときは作らずに警告し、起動を続ける（U2 の `infrastructure-specification.md` 3章） | 起動のログ | WARN「初期管理者を作成しませんでした」（メールアドレスとパスワードが未設定）。起動は続いた | 注意（Q3 の決定どおり。依頼者が値を入れるまでログインできない） |
| 18 | Flyway と H2 の版 | — | 起動のログ | WARN「Using H2 2.4.240 which is newer than the version Flyway has been verified with」。スキーマの変更 V1〜V4 は当たり、起動は成功 | 注意（既存の状態。本段では変えない） |

確認のために送った `/actuator/*` への要求 5 件は、アプリのログに WARN「要求をエラー応答に変換しました」として残っている（想定どおり）。

## 2. セキュリティの観点（まとめ）

| 観点 | 結果 |
|---|---|
| 最小の権限 | root 以外で動き、データの置き場所は所有者だけ。番号は `127.0.0.1` だけに開く（1〜3） |
| 秘密情報 | `.env` だけに置き、所有者だけが読める。イメージ・ログ・リポジトリに出ていない（12・13・16）。署名鍵は乱数で作り、値をどこにも表示していない |
| 公開の面 | health と画面だけ。ほかの Actuator は 404（14） |
| 保存時の暗号化 | 内部DBのファイルは暗号化していない（設計どおり。守りは OS の権限。U4 の NFR3.3）。PC のディスクの暗号化は本段では確かめていない |

## 3. コンプライアンスの観点（まとめ）

- 内部DBには、利用が始まると個人に関する情報（メールアドレス、接続元IP、User-Agent）とパスワードのハッシュ値が入る。今は初回の起動の中身だけで、利用者の記録は無い。
- リポジトリは公開のため、`.env` と内部DBのバックアップをコミットしないことが重要である。`.env` は無視の対象になっている。**内部DBのバックアップ（`mastersmith-data-<日時>.tgz`）は無視の対象になっておらず、前の段（`cd-config.md` の「Assumptions & Open Questions」）から依頼者の判断待ちのまま残っている。**

## 4. 次の段へ引き継ぐもの

| 事項 | 持ち主の段 |
|---|---|
| 初期管理者の値を `.env` に入れてから、スモークテスト（ログイン・管理者向け領域・ログアウト・監査イベント2件）を行う | deployment-execution（値を入れるのは依頼者） |
| 内部DBのファイルの権限（U4-NFR3.3）の最終の判定。本段の 2 の結果を証拠として使える | deployment-execution |
| 戻しの手順（`rollback-runbook.md`）を一度通して実行する | deployment-execution |
| コンテナの CPU の上限 2 でログインの照合の時間の目標を満たすか。どの値で測るか | performance-validation |
| 内部DBのバックアップを誤ってコミットしない手当て | 依頼者の判断 |

## Sources

- `aidlc/spaces/default/intents/260922-auth-audit-base/operation/environment-provisioning/environment-provisioning-questions.md`（Q1〜Q4）
- `aidlc/spaces/default/intents/260922-auth-audit-base/operation/environment-provisioning/environment-inventory.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/operation/deployment-pipeline/cd-config.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u1-app-skeleton/infrastructure-design/infrastructure-specification.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u2-authentication/infrastructure-design/infrastructure-specification.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u3-access-control/infrastructure-design/infrastructure-specification.md`、`cicd-pipeline.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u4-audit-log/infrastructure-design/infrastructure-specification.md`
- 2026-09-23 12:07〜12:08 に実行した `docker compose`・`docker inspect`・`docker image inspect`・`docker history`・`curl`・`git status` の出力
- `compose.yaml`、`Dockerfile`、`.dockerignore`、`.gitignore`、`aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- 7 の起動の時間（5.37 秒）は1回の観測で、性能の測定ではない。起動の時間の目標（30 秒以内）の判定は performance-validation の段で行う。
