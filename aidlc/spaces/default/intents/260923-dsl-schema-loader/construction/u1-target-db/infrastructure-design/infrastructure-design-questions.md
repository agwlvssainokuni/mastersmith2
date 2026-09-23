# Infrastructure Design — U1 対象DB（u1-target-db）— 質問

この段では、U1 の検査の流れ（CI・手元の検査）と、対象DB を手元で動かす環境を決めます。U1 は library のため、成果物は cicd-pipeline.md と traceability.json です。配備先は開発者の PC 上のコンテナだけで、クラウドの基盤は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。

調べた手元の環境（読み取りだけ、2026-09-24）:
- colima: CPU 4・メモリ 6GiB・ディスク 100GiB（動いている）
- 手元にあるイメージ: `mysql:8.4`・`mariadb:11.8`・`postgres:18`（NFR 要件で決めた版）と、Testcontainers の `testcontainers/ryuk:0.14.0` がすでにある
- CI: GitHub Actions の `ubuntu-latest` で `./gradlew verify`（制限時間 60 分）。Docker が使える

すでに決まっていること:
- 対象DB の結合テストは、3種類とも `./gradlew verify` の中で毎回、Testcontainers で実際の DB を起動して行う（CI も同じ）。コンテナの実行環境が無いときは警告を出して飛ばし、その状態では統合しない（team.md・U1 の NFR12.1〜NFR12.3）
- イメージは細かい版まで固定する（U1 の NFR 要件）

---

## Q1. 手元で試すための対象DB

画面から「スキーマの読み込み」を手で試したり、Build and Test で 100 テーブル × 100 カラムの時間を測ったりするには、アプリのコンテナから届く対象DB が要ります。

A. compose に profile `targetdb` で PostgreSQL 18 を1つ足す（必要なときだけ起動。見本のスキーマを入れる SQL をリポジトリに置く。読み取りだけのアカウントで接続する）。MySQL・MariaDB を手で試すときは、同じ形で自分で起動する手順を README に書く
B. 3種類とも profile で足す（`targetdb-postgres`・`targetdb-mysql`・`targetdb-mariadb`。手元のメモリを多く使う）
C. compose には足さない（手で試すときは各自が起動する。README に手順だけ書く）
X. Other (please specify)

[Answer]: B

## Q2. イメージの固定の仕方

A. 版の名前とダイジェスト（`mysql:8.4.x@sha256:...`）で固定し、Dependabot の docker の更新の通知で上げる（既存の Dockerfile と同じ扱い）
B. 版の名前だけで固定する（`mysql:8.4.x`。ダイジェストは付けない）
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

- **手元で試す対象DB**（Q1: B）: compose に profile を3つ足す（`targetdb-postgres`・`targetdb-mysql`・`targetdb-mariadb`、それぞれ NFR 要件の版）。必要なものを1つずつ起動する使い方を README に書く（3つ同時に起動すると VM のメモリ 6GiB のうち約 1.5〜3GiB を使う）。ポートはホストに開けず、アプリのコンテナから compose の中の名前で接続する
- **見本のスキーマ**: DB の種類ごとに見本のスキーマ（テーブル・ビュー・外部キー・コメント・記号を含む名前）を作る SQL をリポジトリに置き、起動時に入れる。Build and Test 用に 100 テーブル × 100 カラムを作る SQL（または作るスクリプト）も置く。読み取りだけのアカウントを作り、アプリはそのアカウントで接続する
- **秘密情報**: 対象DB の管理者と読み取りのアカウントのパスワードは `.env` だけで渡す（コミットしない）。`.env.example` には項目名を空の値で置く（`MASTERSMITH_TARGET_DB_*` と見本の DB のパスワード）
- **イメージの固定**（Q2: A）: Testcontainers と compose の見本の DB のイメージは、版の名前とダイジェストで固定する。Dependabot の docker の更新の通知で上げる（テストのコードの中のイメージの名前は、1か所の定数にまとめる）
- **CI**: 今の `./gradlew verify`（`ubuntu-latest`、制限時間 60 分）のまま、Testcontainers で3種類の DB を起動する。イメージの取得は毎回（キャッシュは足さない）。時間は Build and Test で測り、制限時間に近ければそのとき見直す
- **手元の検査**: colima が動いていないときは警告を出して対象DB のテストだけ飛ばす（その状態では統合しない）。Testcontainers が colima の Docker に届く設定を README に書く
- **成果物**: U1 は library のため、cicd-pipeline.md と traceability.json を作る

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
