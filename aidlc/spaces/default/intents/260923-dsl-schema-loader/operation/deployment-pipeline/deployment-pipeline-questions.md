# Deployment Pipeline — 質問

前提（読み取りだけで調べた結果。`.env` は中身を開かず、項目があるかどうかだけを数えた）:
- 配備先は、今までどおり開発者の PC 上のコンテナ（`compose.yaml`、プロジェクト名 `mastersmith`）。自動の配備は持たず、依頼者の承認のうえで手で配備する（team.md・project.md の Deployment）。既にある `Dockerfile`・`compose.yaml`・README の手順を正として記録する（project.md の決まり）。
- 今動いているのは前の Intent（260923-colima-spec-up）で配備した版 `10742a3`（イメージ `mastersmith:local`、2026-09-23 作成）。healthy。
- 今回配備する版は、この Intent の最新（`037a33b`。アプリの中身は `8961cb2` と同じ。CI は `6f212e8` で成功）。
- 今回の変更の配備への影響:
  - 内部DB のスキーマが V5（DSL のプレビューと履歴の表）・V6（監査の列）の2つ進む。前進のみ（team.md）。
  - 内部DB の既定の接続先が `jdbc:h2:file:./data/mastersmith;DEFRAG_ALWAYS=TRUE` になった。`.env` に `MASTERSMITH_DB_URL` は無い（既定が効く）。
  - 対象DB の接続の設定（`MASTERSMITH_TARGET_DB_*`）が新しく増えた。`.env` にはまだ無い（無ければ対象DB を使わず、起動は続く。DSL の投入・プレビュー・適用は使え、既定の DSL の生成と照合は「未設定」になる）。
  - 既知の制約（Build and Test で依頼者が受け入れた U4-STORAGE-RUN）: 動いている間は、大きな DSL の投入と適用のたびに内部DB のファイルが本文の大きさの分ずつ増え、止めると詰め直される。
- `.env` の `MASTERSMITH_CONTAINER_MEMORY` はある（前の Intent で 2g にした）。

## Q1. 配備したアプリの対象DB の接続

A. 今回は対象DB を設定しない（`.env` に `MASTERSMITH_TARGET_DB_*` を足さない。DSL の投入・プレビュー・適用だけを使う）
B. 手元の見本の対象DB（`compose.yaml` の profile `targetdb-postgres` などの見本のスキーマ）を起動し、配備したアプリをつなぐ（`.env` に見本の接続の設定と見本のパスワードを依頼者が入れる）
C. 依頼者の実際の業務の DB につなぐ（接続の設定は依頼者が `.env` に入れる。AI は値を見ない）
X. Other (please specify)

[Answer]: B

## Q2. 配備の前の内部DB のバックアップ

内部DB のスキーマが V5・V6 に進むため、前の版に戻すときに備える。

A. 配備の前に、README の「内部DBのバックアップと戻し方」の手順で内部DB のボリュームを複写する（リポジトリの外、ホームの下に置く。Git 管理外）
B. 取らない
X. Other (please specify)

[Answer]: A

## Q3. 戻し方

A. 直前の版 `10742a3` に戻す。配備の前に今のイメージに `mastersmith:pre-dsl` のタグを付けて残し、戻すときはそのイメージで起動する。スキーマは V5・V6 のまま（古い版は Flyway の既定で自分の知らない先の移行を無視する見込み。Deployment Execution で確かめる）。データまで戻す必要があるときだけ、Q2 のバックアップを戻す
B. 直前の版に戻すときは、必ず Q2 のバックアップも戻す（V5・V6 の前の内部DB に戻す。配備の後に行った DSL の操作と監査の記録は失われる）
X. Other (please specify)

[Answer]: A

## F1. つなぐ見本の対象DB の種類（Q1: B の追加の質問）

`compose.yaml` には profile `targetdb-postgres`・`targetdb-mysql`・`targetdb-mariadb` の3つの見本がある（`docker/targetdb/<種類>/` の見本のスキーマと読み取りだけのアカウント `mastersmith_reader`）。配備したアプリの対象DB の接続は1つだけ。

A. PostgreSQL（`targetdb-postgres`、メモリの上限 512m）
B. MySQL（`targetdb-mysql`、768m）
C. MariaDB（`targetdb-mariadb`、512m）
X. Other (please specify)

[Answer]: A

## F2. 見本の対象DB の起動の仕方（Q1: B の追加の質問）

見本の対象DB は compose の profile のため、`docker compose up -d` だけでは起動しない。アプリは対象DB が止まっていても起動を続ける（生成と照合が「接続できない」になる）。

A. 配備の手順に「見本の対象DB も起動する（`docker compose --profile targetdb-<種類> up -d`）」を入れ、アプリと一緒に動かし続ける。README の起動・停止の手順にも書く
B. 見本の対象DB は、DSL の生成や照合を試すときだけ依頼者が起動する（配備の手順では起動しない。止まっている間は生成と照合が「接続できない」になる）
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

回答をまとめると、この段では次のとおり決める。

1. 配備の流れは今までどおり、依頼者の承認のうえで手で行う（自動の配備は持たない）。既にある `Dockerfile`・`compose.yaml`・README の手順を正とし、今回の差だけを書く（project.md の決まり）。配備する版はこの Intent の最新（アプリの中身は `8961cb2` と同じ。CI は `6f212e8` で成功）。
2. 見本の対象DB は PostgreSQL（`targetdb-postgres`。DB `business`・スキーマ `sales`・読み取りだけのアカウント `mastersmith_reader`）とし、配備したアプリにつなぐ（Q1: B、F1: A）。`.env` への設定（`MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD`・`MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD` と `MASTERSMITH_TARGET_DB_*` の7項目。接続先は `targetdb-postgres:5432`）は依頼者が入れ、AI は値を見ない。
3. 見本の対象DB は配備の手順で一緒に起動し（`docker compose --profile targetdb-postgres up -d`）、アプリと一緒に動かし続ける。README の起動・停止の手順にも書く（F2: A）。VM（CPU 4・6GiB）にはアプリ（2g）と見本の対象DB（512m）が収まる。
4. 配備の前に、README の手順で内部DB のボリュームを複写する（リポジトリの外、ホームの下）（Q2: A）。
5. 戻し方は、直前の版 `10742a3` に戻す。配備の前に今のイメージに `mastersmith:pre-dsl` のタグを付けて残し、戻すときはそのイメージで起動する。スキーマは V5・V6 のまま（古い版が先の移行を無視して起動できるかは Deployment Execution で確かめる。U4-MIGRATION）。データまで戻す必要があるときだけ、4 のバックアップを戻す（Q3: A）。
6. 配備の後の確かめは、healthy とスモークテスト（ログイン・DSL の管理画面を開く・今の状態の取得・見本の対象DB からの既定の DSL の生成）。既知の制約（U4-STORAGE-RUN：大きな DSL の投入と適用を重ねたら起動し直す）を配備の記録に書く。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct

