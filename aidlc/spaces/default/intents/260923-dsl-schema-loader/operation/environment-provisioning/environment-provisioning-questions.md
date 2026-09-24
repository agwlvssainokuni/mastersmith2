# Environment Provisioning — 質問

この段は、配備先が開発者の PC 上のコンテナのため、Docker の実行環境・イメージ・ボリューム・`.env`・`compose.yaml` の設定と読み替える（project.md の決まり）。質問の前に、読み取りだけで今の環境を調べた（2026-09-25。`.env` は中身を開かず、項目があるかどうかだけを数えた）。

| 項目 | 今の状態 | 設計・決定の値 | 判定 |
|---|---|---|---|
| colima の VM | CPU 4・メモリ 6GiB・ディスク 100GiB、動いている | CPU 4・6GiB（前の Intent）。アプリ 2g＋見本の対象DB 512m が収まる | 満たす |
| VM のディスクの空き | 96G のうち 64G 空き（34% 使用） | 内部DB は動いている間に適用のたびに本文の大きさの分ずつ増える（U4-STORAGE-RUN） | 満たす |
| 配備したアプリ | `mastersmith-app-1`（`10742a3`、イメージ `mastersmith:local`）が healthy | 次の段で新しい版に替える | — |
| 内部DB のボリューム | `mastersmith_mastersmith-data`（中身 約 72KB） | 配備の前に複写する（Deployment Pipeline の Q2: A） | — |
| バックアップの置き場 | `~/.mastersmith-backup/` はまだ無い | ホームの下、権限 700 | 次の段の手順で作る |
| 見本の対象DB のイメージ | `postgres:18.6`（`compose.yaml` と同じダイジェスト）が PC にある | 版を固定したイメージ | 満たす |
| 見本の対象DB のボリューム | `mastersmith_mastersmith-targetdb-postgres` は無い（初めての起動で作られる） | 初めての起動で見本のスキーマと `mastersmith_reader` を作る | — |
| `.env` の見本の対象DB の2項目 | `MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD`・`MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD` は無い | 初めて起動する前に依頼者が入れる | **満たさない** |
| `.env` の対象DB の接続の7項目 | `MASTERSMITH_TARGET_DB_*` は無い | 依頼者が入れる（接続先 `targetdb-postgres`・5432、DB `business`・スキーマ `sales`・ユーザー `mastersmith_reader`） | **満たさない** |
| `.env` のそのほか | 署名鍵・コンテナの上限（メモリ・CPU）はある | — | 満たす |
| Docker のそのほか | ボリューム 60 個（うち使われていない 9.1GB）、イメージ 31 個（使われていない 11.3GB） | — | 参考（今回の範囲外） |

## Q1. `.env` に足す9項目をいつ入れるか

A. この段の間に依頼者が入れる。入れた後に、AI が項目があるかどうかだけを数えて確かめる（値は見ない）
B. 次の段（Deployment Execution）の配備の直前に入れる
X. Other (please specify)

[Answer]: A

## Q2. 見本の対象DB をこの段で先に起動して確かめるか

A. この段で見本の対象DB（`targetdb-postgres`）だけを起動し、初期化の誤りが無いこと、`mastersmith_reader` で `sales` のテーブルを読めて書けないことを確かめる（配備したアプリには触らない。確かめの操作は見本の DB の中だけで、アプリの監査ログには残らない）
B. この段では起動せず、次の段の配備で一緒に起動して確かめる
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

回答をまとめると、この段では次のとおり進める。

1. 依頼者が `.env` に9項目（`MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD`・`MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD` と `MASTERSMITH_TARGET_DB_*` の7項目。接続先 `targetdb-postgres`・5432、DB `business`・スキーマ `sales`・ユーザー `mastersmith_reader`、`MASTERSMITH_TARGET_DB_PASSWORD` は読み取りのパスワードと同じ値）を入れる。AI は値を見ず、項目があるかどうかだけを数えて確かめる（Q1: A）。
2. 入った後に、AI が見本の対象DB（`targetdb-postgres`）だけを起動し（`docker compose --profile targetdb-postgres up -d targetdb-postgres`）、次を1つずつ確かめる（Q2: A）。配備したアプリ（`mastersmith-app-1`）とその内部DB には触らない。
   - 起動し、初期化（見本のスキーマ `sales` と `mastersmith_reader`）の誤りがログに無いこと
   - メモリの上限が 512m であること
   - `mastersmith_reader` で `sales` のテーブルとビューを読めること、書き込み（表を作る・行を入れる）ができないこと（確かめの接続は見本の DB のコンテナの中の `psql` で行い、パスワードはコマンドに書かず環境変数から渡し、表示しない）
   - VM のメモリに、アプリ（2g）と見本の DB（512m）が収まること
3. 見本の対象DB は起動したまま次の段（Deployment Execution）に渡す（配備で一緒に動かし続けるため）。
4. 成果物は、環境の一覧（`environment-inventory.md`）と確かめの記録（`validation-report.md`）。Docker の使われていないボリューム・イメージ（合わせて約 20GB）は今回の範囲外として記録だけする。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct

