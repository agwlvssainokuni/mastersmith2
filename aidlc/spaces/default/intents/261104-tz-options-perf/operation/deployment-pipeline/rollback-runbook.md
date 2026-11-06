# 戻しの手順書（rollback-runbook）

Intent 261104-tz-options-perf の配備を戻す手順です。この配備は、引き受けた Intent 261102 の変更（内部DB の移行 V14 を含む）を一緒に入れます。`docs/deployment.md` の「戻し方」「スキーマの変更（Flyway）」の V14 の項と、前の手順（`aidlc/spaces/default/intents/261101-maintenance-update/operation/deployment-pipeline/rollback-runbook.md`）を正とし、差を中心に書きます。

V14 は列を足すだけの前進のみ・後方互換の移行のため、戻しは**イメージだけ**で、スキーマは戻しません。前の版のアプリを V14 の後の内部DB で起動する確かめは、配備の後の複写を使って練習します（3節、Q2: A）。

## 1. 戻すきっかけ

- 起動が healthy にならない、Flyway の誤りが出る、ERROR が出続ける、ログに秘密・メールアドレスそのもの・業務データの値が出る、スモークテスト（`deployment-strategy.md` の S1〜S10）が通らない。
- 表示のタイムゾーンの選択肢が出ない、ブラウザで時差の表記が食い違う、管理の画面・業務データの画面の時刻が崩れる。
- 業務データの時刻の読み書きが 5xx になる、保存で時刻が変わる。
- 戻すかどうかは依頼者に諮って決める。
- アプリが `ExitOnOutOfMemoryError` で止まっただけのときは、すぐには戻さない（6節）。

## 2. 戻しの形（第一の手）: イメージだけ

- 戻し先は、配備の前に付けたタグ `mastersmith:pre-tz-options-perf`（イメージ ID `ffd2b8380460…`、Intent 261101 で配備した版）。
- 内部DB はそのまま使い、スキーマは戻さない。前の版のアプリ（V1〜V13 の移行だけを持つ）は、V14 で足した列 `display_time_zone` を読み書きせずに起動する。Flyway は自分の知らない新しい版（14）を記録に見ても止まらない（`DisplayTimeZoneMigrationCompatibilityIT` と 3節の練習が根拠）。
- `.env`・`.env.targetdb`・見本の PG・Mailpit は戻さない（この配備で変えていない）。

### 2.1 手順

```bash
docker image inspect mastersmith:pre-tz-options-perf --format '{{.Id}}'   # sha256:ffd2b8380460… であること
docker compose stop app
# 調べるために、今の内部DB を複写する（docs/internal-db.md の「内部DBのバックアップと戻し方」。名前は …-before-rollback.tgz、置き場は ~/.mastersmith-backup/、複写は権限 600）
MASTERSMITH_IMAGE_TAG=pre-tz-options-perf docker compose up -d --no-build --no-deps app
MASTERSMITH_IMAGE_TAG=pre-tz-options-perf docker compose ps app   # healthy を待つ
docker inspect mastersmith-app-1 --format '{{.Image}}'   # sha256:ffd2b8380460… であること。違えば --force-recreate を足して作り直す
```

- **戻している間は、アプリを起動・作り直す `docker compose` のコマンドに毎回 `MASTERSMITH_IMAGE_TAG=pre-tz-options-perf` を付けます。** 付けずに `docker compose up -d` を行うと、`mastersmith:local`（新しい版）で作り直されます。
- **`--no-build` を必ず付けます**（付けないと、戻し先のイメージが上書きされる）。
- **コマンドの外でこの変数を `export` したままにしないでください。** 戻した後は `unset MASTERSMITH_IMAGE_TAG` で消し、`env | grep -c MASTERSMITH_IMAGE_TAG` が 0 であることを確かめる。
- 入れ替えの後は、コンテナのイメージ ID が戻し先の ID と一致することを `docker inspect` で確かめる（構成が同じだと作り直されず古いイメージのまま起動することがある。`project.md` の学び）。
- 戻し先のタグのイメージが無いときは、`docs/deployment.md` の「戻し方」のとおり、`main`（`f388fb8d`。取り込みの後なら1つ前の `main` の先頭）を `git worktree add` で取り出して WAR とイメージを作り直す。
- 開いているタブを読み込み直す（前の版の画面のファイルを読むため）。
- **片付け（`cd-config.md` の順 13）では、戻し先 `mastersmith:pre-tz-options-perf` を消さない。**

### 2.2 戻したときに変わること

| 変わること | 内容 |
|---|---|
| 表示のタイムゾーン | 前の版の画面には選択が無い。管理の画面の時刻は前の版の書式（`JST` などの略称）に戻る。列と保存した名前は消えず、前の版の保存（4 項目）は列を書き換えない。新しい版へ進み直すと、保存した名前が再び効く |
| 業務データの時刻 | 前の版の表示（UTC の表示と `datetime-local` の入力）に戻る。PostgreSQL の接続の初期化（`SET TIME ZONE 'UTC'`）が無くなるが、型付きの読み書きの値は変わらない（261102 の U3 の NFR Design の D3）。変わりうるのは `timestamptz` を文字で読む経路だけ |
| ホーム・メニュー・アイコン | ホームのカード・ロゴ・管理のアイコンが消え、管理の仮の画面と `/admin` が戻る |
| favicon | 前の版には素材が無く、`/favicon.ico` は元の形（HTML を返す）に戻る。ブラウザは古い favicon を保持しうる |
| 監査・業務データの行・内部DB の列 | 戻しでは変わらない。戻す前に残った行と、V14 の列はそのまま残る（監査は追記だけ） |

### 2.3 戻した後の確かめ

| # | 確かめ | 行う人 | 通る条件 |
|---|---|---|---|
| R1 | healthy と起動のログ | AI | `app` が healthy、`/actuator/health` が 200、コンテナのイメージ ID が `ffd2b8380460…`。ERROR 0 件。Flyway の `Migrating schema` 0 件で、検証の誤りが無い。救済の WARN 0 件。WARN は前の版から出ているもののほかに無い |
| R2 | ★ログインと画面 | 依頼者 | 初期管理者でログインし、ホームと管理の画面と業務データの一覧（`customers`）と詳細が開ける。見本の PostgreSQL の日時の列の表示が、前の版の表示の形で出る。`LOGIN_SUCCEEDED` が監査に残る（送る前に伝える） |

### 2.4 新しい版へ上げ直すとき

- 移行は V14 が当たったままのため、新しく当たる移行は無い。直した WAR で `docker compose up -d --build --no-deps app` を行い（`MASTERSMITH_IMAGE_TAG` を付けない）、イメージ ID の一致を確かめる。

## 3. 戻しの練習（Q2: A。`cd-config.md` の順 11）

V14 の後の内部DB で、戻し先のイメージが起動することを、動いているアプリと内部DB に触れずに確かめる。261102 の U2 の設計（`infrastructure-design/cicd-pipeline.md` の 3.3）と `docs/deployment.md` の V14 の項が、配備の段に回した確かめ。

1. 配備の後の複写 `…-after-tz-options-perf.tgz`（`cd-config.md` の順 10）を、使い捨ての名前付きボリューム（例: `mastersmith-rollback-drill`）へ展開する（`docs/internal-db.md` の展開の手順と同じイメージ。置き場はホームの下）。
2. 戻し先のイメージ `mastersmith:pre-tz-options-perf` を、使い捨てのコンテナ（例: `mastersmith-rollback-drill`）で起動する。
   - 内部DB の口に使い捨てのボリュームをつなぐ。
   - 公開の番号は持たない（`/actuator/health` はコンテナの中から bash の `/dev/tcp` で確かめる）。
   - `.env` は動いているアプリと同じものを `--env-file` で渡す（値は表示しない）。
   - 対象DB とメールの接続は、ネットワークにつながず使わない。起動の確かめだけで、ログインはしない。
   - メモリの上限は compose と同じ 2 GiB。colima の VM のメモリの余裕が少ないため、練習の間は lgtm を起動しない。
3. 確かめ（R1 と同じ形）:
   - healthy（`/actuator/health` の 200）。
   - 起動のログで ERROR 0 件、Flyway の `Migrating schema` 0 件、検証の誤り 0 件（版 14 を記録に見ても止まらない）。
   - 救済の WARN 0 件。
4. 終わったら、使い捨てのコンテナとボリューム、展開に使った作業用のディレクトリを消す。配備の後の複写（`…-after-tz-options-perf.tgz`）は、バックアップとして残す。
5. 結果（起動にかかった秒数・確かめの件数・消したもの）を Deployment Execution の記録に書く。練習が通らなかったときは、戻しの第一の手が使えないものとして依頼者に諮り、第二の手（4節）を戻しの手とするかを決める。

## 4. 配備の前の複写を戻すとき（第二の手）

- 使えるのは、V14 の移行の前に取った配備の前の複写 `…-pre-tz-options-perf.tgz`（`cd-config.md` の順 5）。
- 使うのは、V14 の当たりが途中で失敗したとき、内部DB が壊れたとき、3節の練習が通らず前の版が V14 の後の内部DB で起動しないと分かったときだけ。戻す前に依頼者に諮る。
- 戻すと、T0 の後のデータ（スモークテストの操作、その後の利用者・ロールの変更、保存した表示のタイムゾーン）と監査の行を失う。
- 戻し方は `docs/internal-db.md` の「内部DBのバックアップと戻し方」のとおり。アプリを止めてボリュームへ展開し、戻し先のイメージで起動する（2.1）。
- 配備の後の複写 `…-after-tz-options-perf.tgz`（順 10）は、スモークテストの後の状態を保つバックアップで、戻しの手には使わない。

## 5. 片付けの後に戻すとき

- 片付け（`cd-config.md` の順 13）は、配備の完了と戻しの練習の後に行い、戻し先 `mastersmith:pre-tz-options-perf` は残す。そのため、片付けの後も 2節の手順で戻せる。
- 片付けで消す `mastersmith:pre-maintenance-update`（261101 の前の版）と `mastersmith:perf-tz-bt`（試験用）は、今回の戻しには使わない。

## 6. アプリが止まったとき（`ExitOnOutOfMemoryError`）

- 前の手順（`aidlc/spaces/default/intents/261016-business-data-views/operation/deployment-pipeline/rollback-runbook.md` 7節）のとおり。
- ヒープが尽きると JVM が止まる（終了コード 3・OOMKilled false）。`restart: "no"` のため起き直さない。
- `docker compose start app` で約 10 秒で戻る。
- 対象DB（見本の PG）が止まったとき（`OOMKilled` true など）は、アプリは動き続け、業務データの API が 503 を返す。`docker compose --profile targetdb-postgres up -d --no-deps targetdb-postgres` で起動し直す（`compose.yaml` の記述と同じ版で作り直される）。

## Sources

- 前の手順: `aidlc/spaces/default/intents/261101-maintenance-update/operation/deployment-pipeline/rollback-runbook.md`、`aidlc/spaces/default/intents/261016-business-data-views/operation/deployment-pipeline/rollback-runbook.md`
- 引き受けた Intent の設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/infrastructure-design/cicd-pipeline.md`（3.1〜3.3）、`u3-target-db-time/`・`u4-business-data-time/`・`u5-menu-icons-home/`・`u6-app-icon-assets/` の `infrastructure-design/cicd-pipeline.md`（戻し方）
- `cd-config.md`・`deployment-strategy.md`・`deployment-pipeline-questions.md`
- リポジトリ: `docs/deployment.md`（「戻し方」「スキーマの変更（Flyway）」の V14 の項）、`docs/internal-db.md`（「内部DBのバックアップと戻し方」）、`compose.yaml`（`MASTERSMITH_IMAGE_TAG`・`restart: "no"`・メモリの上限）、`Dockerfile`、`backend/src/main/resources/db/migration/V14__u2_display_time_zone.sql`
- 決まり: `aidlc/spaces/default/memory/team.md`（Deployment）、`project.md`（Deployment・Corrections の学び）

## Assumptions & Open Questions

- [assumption] 前の版のアプリは、V14 の後の内部DB でも起動の時に Flyway の検証で止まらない（Flyway は知らない新しい版を既定で無視する）。結合テスト `DisplayTimeZoneMigrationCompatibilityIT` は Flyway とマイグレーションのファイルだけで確かめており、アプリ全体の起動は 3節の練習で初めて確かめる。
- [assumption] 戻しの練習の使い捨てのコンテナは、対象DB とメールにつながなくても起動する（起動の時に対象DB とメールへ接続しない）。起動しないときは、接続の誤りか本物の失敗かをログのロガーと例外のクラスの名前で見分けてから判定する。
