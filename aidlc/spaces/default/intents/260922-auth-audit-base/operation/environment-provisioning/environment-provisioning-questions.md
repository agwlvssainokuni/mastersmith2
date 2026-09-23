# Environment Provisioning の質問（environment-provisioning-questions）

Intent `260922-auth-audit-base`（auth-audit-foundation）の配備先の環境を用意し、設計どおりか確かめるための質問。

## 前提（確認済みのこと）

- 配備先は開発者の PC 上のコンテナだけ（`operation/deployment-pipeline/cd-config.md` 2節）。クラウドの環境（VPC・IAM・Secrets Manager など）は作らない（project.md の Deployment）。
- 本段の「環境」は、Docker の実行環境（colima）、イメージ `mastersmith:local`、名前付きボリューム `mastersmith_mastersmith-data`、`.env`、`compose.yaml` の設定（番号・利用者・権限・資源・タイムゾーン・ヘルスチェック）である（U1・U2 の `infrastructure-design/infrastructure-specification.md`）。
- 2026-09-23 に読み取りだけで調べた、この PC の状態:
  - Docker 29.8.1、Docker Compose 5.5.1、colima が動いている（aarch64、macOS Virtualization.Framework）
  - **colima の VM の資源は CPU 2・メモリ 約 2GB**
  - イメージ `mastersmith:local` はある。コンテナとボリューム `mastersmith_mastersmith-data` は無い
  - `.env` は無い（`.env.example` はある）

## Q1. colima の資源が設計に足りないことへの対応

`compose.yaml` はアプリのコンテナに CPU の上限 4・メモリ 1GB を指定しています（CPU 4 は、同時 10 件のログインのパスワード照合を 1 秒以内に収めるため U2 の基盤の設計で決めた値）。colima の VM は CPU 2 のため、このままでは `docker compose up` が失敗します（CPU の上限は VM の CPU の数を超えられない）。README は `colima start --cpu 4 --memory 4` を案内しています。

A. colima の VM を CPU 4・メモリ 4GB で起動し直す（`colima stop` → `colima start --cpu 4 --memory 4`。colima で動いているほかのコンテナも一度止まる）。設計の値は変えない
B. `compose.yaml` の CPU の上限を 2 に下げる（U2 の基盤の設計の値を変える。照合の時間は Performance Validation で測って見直す）
C. CPU の上限を環境変数で変えられるようにし（既定は 4 のまま）、この PC では 2 で動かす
X. Other (please specify)

[Answer]: C

## Q2. 環境の用意と確認を、この段で実際に行うか

設計どおりかの確認（root 以外の利用者で動く、`/app/data` の権限が所有者だけ、`127.0.0.1` だけに番号を開く、資源の上限、タイムゾーン、ヘルスチェックが healthy になる、など）を、実際にコンテナを動かして行うかを決めます。

A. この段で AI が実際に行う（ボリュームを作り、イメージを作り直してコンテナを起動し、設定を1つずつ確かめてから止める。ボリュームは次の段のために残す）
B. 依頼者が手順どおりに実行し、その出力を AI が記録と判定に使う
C. この段では手順と合格の基準だけを書き、実行は次の Deployment Execution の段でまとめて行う
X. Other (please specify)

[Answer]: A

## Q3. `.env`（秘密情報）の用意のしかた

アプリの起動には署名鍵 `MASTERSMITH_AUTH_SIGNING_KEY` が必須で、初期管理者のメールアドレスとパスワードも要ります。秘密情報はソースコードや設定ファイルに書かず、コミットもしません（project.md の Forbidden）。

A. 依頼者が `.env` を作る。AI は値を読まず、必要な変数の名前がそろっていて値が空でないことだけを確かめる
B. 確認のために、AI がリポジトリの外に一時の環境ファイルを作る（署名鍵は乱数で作り、値は表示しない。初期管理者も仮の値）。確認が済んだら消す。本物の `.env` は依頼者が後で作る
C. AI が `.env` を作る（署名鍵は乱数で作り、値は表示しない）。初期管理者のメールアドレスとパスワードは依頼者が後から入れる
X. Other (please specify)

[Answer]: C

## Q4. 内部DBのパスワード（追加の質問）

Q3 で「AI が `.env` を作る」を選びました。`.env.example` には内部DBのパスワード `MASTERSMITH_DB_PASSWORD` もあります（既定は空）。組み込みの H2 は、初めて起動したときにこのパスワードで DB のファイルを作り、以後は同じ値でなければ開けません。ボリュームを作る前に決めます。

A. AI が乱数で作って `.env` に入れる（値は表示しない。後で変えるには DB 側のパスワードの変更が要る）
B. 空のまま（既定）にする。内部DBは同じコンテナのプロセスからしか開けず、ファイルは OS の権限で守る（U4 の NFR3.3）
X. Other (please specify)

[Answer]: B

## Consolidated Summary Confirmation

回答の要約:

- 本段の環境は、この PC の colima 上の Docker、イメージ `mastersmith:local`、ボリューム `mastersmith_mastersmith-data`、`.env`、`compose.yaml` の設定とする。クラウドの環境は作らない。
- Q1: `compose.yaml` の CPU の上限を環境変数で変えられるようにする（例: `cpus: ${MASTERSMITH_CONTAINER_CPUS:-4}`。既定は設計どおり 4）。この PC では `.env` に 2 を入れて動かす。`.env.example` と README の「環境変数」に名前と意味を足す。colima の VM は作り直さない。CPU 2 では照合の時間の目標（同時 10 件で 1 秒以内）を満たさないおそれがあり、どの値で測るかは Performance Validation で決める。
- Q2: AI が実際に用意と確認を行う。`./gradlew :backend:bootWar` で WAR を作り、`docker compose up -d --build` でイメージを作り直してボリュームとコンテナを作り、設定を1つずつ確かめてから `docker compose stop` で止める。ボリュームは次の段のために残す。確かめる項目は、実行の利用者（UID 10001）、`/app/data` の権限（所有者だけ）、番号が `127.0.0.1:8080` だけに開いていること、CPU とメモリの上限、タイムゾーン（`Asia/Tokyo`）、ヘルスチェックが healthy、再起動しない方針、停止の猶予、ログの保存の上限、イメージに秘密情報が入っていないこと、health 以外の Actuator に届かないこと、外部エクスポートが既定で無効であること、`.env` が Git の管理外であること。
- Q3: AI が `.env` を作る。署名鍵は `openssl rand -base64 32` の乱数を画面に出さずに書き込む。初期管理者のメールアドレスとパスワードの行は入れず、依頼者が後から入れる（それまでは初期管理者が作られず、ログインはできない）。`.env` の権限は所有者だけが読み書きできるようにする。
- Q4: 内部DBのパスワードは空のまま（既定）にし、`.env` に行を入れない。
- 反映の先: 本段の文書（`environment-inventory.md`・`validation-report.md`）、`compose.yaml`、`.env.example`、README。`.env` はコミットしない。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
