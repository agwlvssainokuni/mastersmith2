# 配備の方式（deployment-strategy）

## 1. 変えないもの

- 方式は、止めて入れ替える形（recreate）。開発者の PC 上の1台のコンテナで、入れ替えの間（数十秒〜2分）はアプリに届かない。前の Intent と同じ。
- 内部DB のボリューム（`mastersmith_mastersmith-data`）と見本の対象DB のボリューム（`mastersmith_mastersmith-targetdb-postgres`）はそのまま使う。
- 手元の監視（`observability` の profile）は起動しない（見たいときだけ起動する。project.md の Deployment）。

## 2. 配備の手順（手で行う）

| 順 | 手順 | コマンド | 通る条件 |
|---|---|---|---|
| 1 | 作業ツリーにアプリのソースの未コミットの変更が無いことを確かめる | `git status --porcelain` | アプリのソースに変更が無い（ワークフローの記録と監査ログのディレクトリは除いて判断する。project.md の Deployment） |
| 2 | `develop` へ取り込み、配備する版を控える（Q1: A） | `git switch develop && git merge --ff-only fix/260924-followup-fixes && git rev-parse --short HEAD` | fast-forward で終わる（merge コミットができない）。ハッシュを控えた |
| 3 | 検査を通して WAR を作る | `DOCKER_HOST=… TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=… caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` | 成功し、対象DB のテストを飛ばしていない |
| 4 | 戻し先のタグを付ける（Q4: B） | `docker tag mastersmith:local mastersmith:pre-followup` | `docker image inspect mastersmith:pre-followup --format '{{.Id}}'` が `sha256:1585bd4e…` |
| 5 | `.env` をホームの下へ複写する（Q2: A。中身は表示しない） | 下の「手順 5〜6 のコマンド」 | `~/.mastersmith-backup/env-<日時>-before-followup`（権限 600）ができた |
| 6 | `.env` の2行を `.env.targetdb` へ移す（Q2: A） | 下の「手順 5〜6 のコマンド」 | `.env.targetdb`（権限 600）に `MASTERSMITH_SAMPLE_TARGETDB_` の行が2つ、`.env` に 0。`git check-ignore -q .env.targetdb` が 0 |
| 7 | アプリのイメージを作り直し、アプリと見本の対象DB を起動し直す（Q3: A） | `docker compose --profile targetdb-postgres up -d --build` | エラーなく終わる。`mastersmith:local` が新しいイメージになり、`mastersmith:pre-followup` は元の ID のまま。見本の対象DB のコンテナも作り直される |
| 8 | 健全になるまで待つ | `docker compose --profile targetdb-postgres ps` | `app` が `healthy`（最長で約 2 分）、`targetdb-postgres` が `Up` |
| 9 | 見本の対象DB の起動を確かめる | `docker compose logs targetdb-postgres` の終わり | `database system is ready to accept connections` が出ている。`ERROR` が無い（ボリュームがあるため初期化の台本は走らない） |
| 10 | 上限と環境変数の名前を確かめる（値は表示しない） | `docker inspect mastersmith-app-1 --format '{{.HostConfig.Memory}} {{.HostConfig.NanoCpus}}'`、`docker inspect mastersmith-app-1 --format '{{range .Config.Env}}{{println .}}{{end}}' \| cut -d= -f1 \| grep -c '^MASTERSMITH_SAMPLE_TARGETDB_'` | アプリの上限が `.env` の値（前の Intent の記録では `2147483648 4000000000`）。`MASTERSMITH_SAMPLE_TARGETDB_` の名前が 0 件（FR6.2） |
| 11 | 手でスモークテストを行う | 3節 | すべての項目が通る |
| 12 | 配備の完了 | — | 手順 2 のハッシュが、いま動いている版の記録になる |

手順 5〜6 のコマンド（README の「`.env` からの移し替え」を、複写を先に取る形にしたもの。値は表示しない）:

```bash
mkdir -p ~/.mastersmith-backup && chmod 700 ~/.mastersmith-backup
T=$(date +%Y%m%d%H%M)
( umask 077; cp .env ~/.mastersmith-backup/env-$T-before-followup )
test -e .env.targetdb && { echo ".env.targetdb が既にあります。止めて依頼者に確かめる"; exit 1; }
( umask 077; grep '^MASTERSMITH_SAMPLE_TARGETDB_' .env > .env.targetdb )
grep -c '^MASTERSMITH_SAMPLE_TARGETDB_' .env.targetdb     # 2 であること
( umask 077; grep -v '^MASTERSMITH_SAMPLE_TARGETDB_' .env > .env.tmp && mv .env.tmp .env )
grep -c '^MASTERSMITH_SAMPLE_TARGETDB_' .env               # 0 であること
ls -l .env .env.targetdb | awk '{print $1, $NF}'            # どちらも -rw-------
```

- 手順 7 は `--profile targetdb-postgres` を必ず付ける。付けないと見本の対象DB が作り直されず、古い入口のまま残る。
- 置き場をホームの下にする（`mktemp -d` の一時ディレクトリは使わない。project.md の Corrections）。
- 手順 6 で行の数が合わないときは、手順 5 の複写から `.env` を戻して止め、依頼者に確かめる。

## 3. スモークテスト

前の Intent（`aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/deployment-pipeline/deployment-strategy.md` 3節）の項目に、今回の変更の確かめを足す。

| 項目 | 確かめ方 | 合格の基準 | 行う人 |
|---|---|---|---|
| 健全性 | `curl -s http://localhost:8080/actuator/health` | `{"status":"UP"}` | AI |
| ログイン画面・ログイン・管理者向け領域 | ブラウザで `http://localhost:8080/`、初期管理者でログイン、サイドバーの「管理」 | それぞれ表示される | 依頼者 |
| DSL の管理画面と対象DB への接続（Q3: A） | サイドバーの「DSL」を開き、「スキーマを読み込む」で既定の DSL を生成する | 成功し、プレビューに見本のスキーマ `sales` のテーブルが並ぶ。「接続できない」（`TARGET_DB_UNAVAILABLE`）にならない（移した値で見本の対象DB に接続できる） | 依頼者 |
| 閉じるボタンの名前（FR7） | 確認の表示を開いて閉じるボタンの名前を見る（読み上げの名前は画面の検査の道具で。英語の表示でも見る） | 日本語「閉じる」・英語「Close」 | 依頼者（任意） |
| ログアウト | ユーザーメニューのログアウト | ログイン画面に戻る | 依頼者 |
| ログ | `docker compose logs app` を JSON として数える | ERROR が無い。すべて1行1件の JSON で、メッセージに改行を含む行が 0 件。Hibernate の案内は ` ⏎ ` で1件（FR4） | AI |
| ログに接続情報が無い | `docker compose logs app \| grep -c -e targetdb-postgres -e mastersmith_reader` | 0（project.md の Forbidden） | AI |
| 監査イベント | README の「監査ログの確かめ方」の手順（置き場はホームの下） | スモークテストの `LOGIN_SUCCEEDED`・`DSL_GENERATED`・`LOGGED_OUT` がある | AI |

- パスワードが要る操作は依頼者が行う。AI は監査イベントとログで裏付け、個人に関する値は表示せず、値の有無だけを確かめる（project.md の Corrections）。
- **スモークテストの要求（ログイン・生成・ログアウト）は監査ログに残り、追記だけで消せない。** 送る前に依頼者に伝える（project.md の Corrections）。
- 監査イベントの確かめではアプリを止める。止める前に依頼者に伝え、確かめた後は `docker compose start app` で起動し直して `healthy` を確かめる。

## 4. 中止して戻す条件

| 条件 | 見方 | 扱い |
|---|---|---|
| 約 2 分で `healthy` にならない、起動が失敗する | 手順 8、`docker compose logs app` | `rollback-runbook.md` 2節（イメージを戻す） |
| ログイン・管理者向け領域・DSL の画面のスモークテストが通らない | 3節 | `rollback-runbook.md` 2節 |
| 見本の対象DB が起動しない | 手順 9 | アプリは戻さない。`rollback-runbook.md` 4節（見本の対象DB だけを扱う） |
| 生成が「接続できない」になる | 3節 | 移した値の誤りを疑う。`.env.targetdb` の行の数と名前を確かめ（値は表示しない）、直らなければ手順 5 の複写から2行を取り直す。版は戻さない |

## 5. 運用の注意

- 既知の制約 U4-STORAGE-RUN（動いている間は DSL の投入と適用のたびに内部DB のファイルが増え、止めると詰め直される）と、10MB の DSL とログインの重ねでのメモリ（2g の 93%。Build and Test の実測）は、前の振り返りの束 2 のまま。配備を止める条件にはしない。

## 6. DB のスキーマ

- 内部DB のスキーマは変わらない（Flyway の新しい版は無い）。戻すときもデータはそのまま使える。
- 見本の対象DB のスキーマ `sales` はボリュームにあり、作り直しで変わらない。

## Sources

- `aidlc/spaces/default/intents/260924-followup-fixes/operation/deployment-pipeline/deployment-pipeline-questions.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/deployment-pipeline/deployment-strategy.md`
- `aidlc/spaces/default/intents/260924-followup-fixes/construction/build-and-test/test-results.md`
- `README.md`

## Assumptions & Open Questions

- 見本の対象DB を作り直した後に、移した値で接続できるかは、スモークテストの生成で確かめる（値そのものは見ない）。
- 閉じるボタンの名前の確かめは任意（画面のテストで確かめ済み。依頼者が見たいときに行う）。
