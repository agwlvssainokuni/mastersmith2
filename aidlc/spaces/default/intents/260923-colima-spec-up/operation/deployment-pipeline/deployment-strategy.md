# 配備の方式（deployment-strategy）

方式は前の Intent と同じ**入れ替え（止めてから新しい版で起動する）**である（`aidlc/spaces/default/intents/260922-auth-audit-base/operation/deployment-pipeline/deployment-strategy.md` 1節）。コンテナは1台で、組み込みの H2 のファイルを2つのプロセスから開けないため、ブルー/グリーン・カナリア・順次の入れ替えは採らない。本書は今回の配備の手順を書く。

## 1. 配備の手順（手で行う）

| 順 | 手順 | コマンド | 通る条件 |
|---|---|---|---|
| 1 | 作業ツリーに未コミットの変更が無いことを確かめる | `git status --porcelain` | アプリのソースに変更が無い（ワークフローの記録と監査ログのディレクトリは除いて判断する。`project.md` の Deployment） |
| 2 | 配備する版のハッシュを控える | `git rev-parse --short HEAD` | 値を控えた（`e4b10af` 以降の `develop` の先頭） |
| 3 | 検査を通して WAR を作る | `./gradlew verify` | 成功し、`backend/build/libs/mastersmith.war` がある |
| 4 | 動いているアプリを止め、ボリュームを複写する | README の「内部DBのバックアップと戻し方」 | バックアップのファイル（`mastersmith-data-<日時>.tgz`、Git 管理外）ができた |
| 5 | `.env` を複写する（中身は表示しない） | `mkdir -p ~/.mastersmith-env-backup && chmod 700 ~/.mastersmith-env-backup && cp -p .env ~/.mastersmith-env-backup/env-<日時>` | 複写ができた。権限は利用者だけ |
| 6 | `.env` の2行を変える（値は表示しない） | `MASTERSMITH_CONTAINER_CPUS` の行を `MASTERSMITH_CONTAINER_CPUS=4` に置き換え、`MASTERSMITH_CONTAINER_MEMORY=2g` の行を足す | `grep -E '^MASTERSMITH_CONTAINER_(CPUS|MEMORY)=' .env` がこの2行だけを示す |
| 7 | 展開した設定を確かめる | `docker compose config app` の `cpus`・`mem_limit` | `cpus: 4`、`mem_limit: "2147483648"` |
| 8 | イメージを作り直して起動する | `docker compose up -d --build` | エラーなく終わる |
| 9 | 健全になるまで待つ | `docker compose ps` | `app` が `healthy`（最長で約 2 分） |
| 10 | 上限と起動の形を確かめる | `docker inspect mastersmith-app-1 --format '{{.HostConfig.Memory}} {{.HostConfig.NanoCpus}}'`、`docker exec mastersmith-app-1 cat /proc/1/cmdline` | 2147483648・4000000000。PID 1 が java で、`-XX:MaxRAMPercentage=75.0` と `-Duser.timezone=Asia/Tokyo` を含む |
| 11 | 手でスモークテストを行う | 2節 | すべての項目が通る |
| 12 | 配備の完了 | — | 手順 2 のハッシュが、いま動いている版の記録になる |

- `.env` の値（秘密情報を含む）は、どの手順でも表示しない。確かめで表示するのは、手順 6 の2つのキーの行だけである。
- `MASTERSMITH_JAVA_OPTIONS` は設定しない（既定の JVM の引数のまま）。
- **ボリュームを消す操作（`docker compose down -v` など）はしない。**

## 2. スモークテスト

前の Intent の `aidlc/spaces/default/intents/260922-auth-audit-base/operation/deployment-pipeline/deployment-strategy.md` 3節と同じ7項目を、手で確かめる。

- 健全性（`/actuator/health` が UP）
- ログイン画面
- ログイン
- 管理者向け領域
- ログアウト
- 監査イベント（スモークテストのログインとログアウトの2件）
- ログ（ERROR が無い。1行1件の JSON のまま。起動の時に JSON でない行が無い）

- ログインなど、パスワードが要る操作は依頼者が行う。AI は監査イベントとログで裏付け、個人に関する値は表示しない（`project.md` の Corrections）。
- 監査イベントを確かめるためにアプリを止めてボリュームを読む操作は、行う前に依頼者に伝える。

## 3. 中止して戻す条件

前の Intent の `aidlc/spaces/default/intents/260922-auth-audit-base/operation/deployment-pipeline/deployment-strategy.md` 4節と同じ。加えて、今回の変更に固有の条件は次のとおり。

| 条件 | 見方 | 扱い |
|---|---|---|
| 起動しない・healthy にならない（`ENTRYPOINT` の形の変更に原因があると見られる） | `docker compose logs app`、`docker inspect` | `rollback-runbook.md` の直前の版に戻す（設定の戻しでは直らない） |
| 手順 10 の上限・起動の形が期待と違う | 手順 10 | `.env` と `docker compose config` を見直す。直らなければ `rollback-runbook.md` |
| 配備の後、メモリや CPU の上限に原因があると見られる不具合 | `docker compose logs app`、`docker stats`、手元の監視（Grafana） | `rollback-runbook.md` の第一の手（`.env` を戻す） |

## 4. DB のスキーマ

今回はスキーマの変更が無い。前進のみ・後方互換の決まり（team.md の Deployment）はそのまま。

## 5. 引き継ぐもの

| 事項 | 持ち主の段 |
|---|---|
| 本書の手順での配備（`.env` の変更＝要件 FR2.2 を含む）、上限と起動の形の確認、スモークテスト | deployment-execution |

## Sources

- `deployment-pipeline-questions.md`（Q1: A、Q2: A）
- 前の Intent の `aidlc/spaces/default/intents/260923-audit-pool-exhaustion/operation/deployment-pipeline/deployment-strategy.md`、`aidlc/spaces/default/intents/260922-auth-audit-base/operation/deployment-pipeline/deployment-strategy.md`
- `compose.yaml`、`Dockerfile`、README の「コンテナでの起動と確認」「内部DBのバックアップと戻し方」

## Assumptions & Open Questions

- `.env` の複写の置き場（`~/.mastersmith-env-backup/`）は質問では決めていない。要約の確認で「リポジトリの外（ホームの下など）」として了承を得た。
