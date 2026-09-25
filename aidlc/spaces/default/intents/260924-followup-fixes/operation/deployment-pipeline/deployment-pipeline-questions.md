# Deployment Pipeline — 質問

配備の仕組み（開発者の PC 上のコンテナ、手で行う入れ替え、スモークテスト、戻し方）は、前の Intent の `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/deployment-pipeline/` と README の「コンテナでの起動と確認」を正とする（project.md の Deployment）。ここでは今回決まっていない点だけを聞く。

前提（読み取りだけで調べた結果。`.env` は中身を開かず、項目の名前だけを数えた）:

- 今動いているのは前の Intent（260923-dsl-schema-loader）で配備した版（イメージ `mastersmith:local`、`sha256:1585bd4e…`、2026-09-24 作成）と、見本の対象DB の PostgreSQL（`mastersmith-targetdb-postgres-1`、公式イメージの入口 `docker-entrypoint.sh` のまま）。どちらも動いている。
- 今回配備する版は作業ブランチ `fix/260924-followup-fixes` の最新（`2d63b2f`。アプリの中身は C6 の `295407f` と同じ）。`develop` へはまだ統合していない。
- 今回の変更の配備への影響:
  - アプリのコード（ログイン・ログの出力）と画面（サブモジュールの固定先 `edb1f94`）が変わる。内部DB のスキーマは変わらない（Flyway の新しい版は無い）。
  - `compose.yaml` のメモリの上限の既定が 2g になる。この PC の `.env` には `MASTERSMITH_CONTAINER_MEMORY` の行があり（値は開いていない）、その値が効く。
  - 見本の対象DB のパスワードの2項目（`MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD`・`MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD`）は `.env.targetdb` から読むようになる。この PC の `.env` にはこの2項目の行があり、`.env.targetdb` はまだ無い。移さないと、見本の対象DB を作り直したときにパスワードが空になる（ボリュームが既にあるので、DB の中身とパスワードは初期化されず今のまま）。
  - 見本の対象DB の3つのサービスは起動の入口（`entrypoint`）が変わるため、今のコンテナを作り直す必要がある（ボリュームはそのまま使える）。
- 負荷の確かめ（k6 の `dslMixed`、上限 2g・CPU 4）は、Build and Test の段で配備するものと同じソースから作ったイメージ `mastersmith:followup-fixes` で済んでいる。project.md の決まりにより、配備の前の k6 は省き、配備の後の healthy とスモークテストで確かめる。
- スキーマが変わらないため、配備の前の内部DB のバックアップは取らない（戻すときもデータは今のまま使える）。

## Q1. `develop` への統合と配備する版

計画（Code Generation の Step 20）では、依頼者の承認を得て作業ブランチを `develop` へ fast-forward で取り込む。統合の前の関門（`./gradlew verify`）は Build and Test の段で通っている。

A. 配備の前に `develop` へ fast-forward で取り込み、`develop` の版を配備する（`origin` へのプッシュは依頼者が行い、その後の CI の結果を配備の後に確かめる）
B. 作業ブランチのまま配備して確かめ、配備の後（Deployment Execution の承認のとき）に `develop` へ取り込む
X. Other (please specify)

[Answer]: A

## Q2. この PC の `.env` から `.env.targetdb` への移し替え（要件 FR6.3）

`.env` は秘密情報を含む。移す手順は README の「`.env` からの移し替え」（値を表示しない形）にある。

A. AI が行う。先に `.env` をリポジトリの外（ホームの下、権限 600）へ中身を表示せずに複写してから、`.env` の `MASTERSMITH_SAMPLE_TARGETDB_` で始まる2行を `.env.targetdb`（権限 600）に写し、`.env` から消す。確かめは行の名前と数だけを表示する
B. 依頼者が README の手順で自分で行う。AI は `docker compose config` の変数の名前だけで確かめる
X. Other (please specify)

[Answer]: A

## Q3. 見本の対象DB のコンテナの作り直し

A. 配備と同じときに、見本の対象DB（`targetdb-postgres`）も新しい起動の入口で作り直す（`docker compose --profile targetdb-postgres up -d`。ボリュームはそのまま）。作り直した後、配備したアプリから対象DB に接続できること（DSL の画面の状態で「接続できない」にならないこと）を確かめる
B. 見本の対象DB は今のコンテナのまま動かし続け、次に止めて起動するときに作り直す（それまでは古い入口のまま）
X. Other (please specify)

[Answer]: A

## Q4. 戻し方

A. 配備の前に今のイメージに `mastersmith:pre-followup` のタグを付けて残す。戻すときは、そのイメージと、Q2 の前に取った `.env` の複写と、前の版の `compose.yaml`（`git show 450ac12:compose.yaml` を一時のファイルに出して `-f` で使う）で起動し直す。データはそのまま（スキーマは変わらない）
B. A と同じだが、`compose.yaml` は戻さず新しいもののまま、イメージと `.env` だけを戻す（見本の対象DB の入口は新しいまま。`.env.targetdb` を残す）
X. Other (please specify)

[Answer]: B

## F1. 戻すときに `.env` の複写を戻すか（Q2: A と Q4: B の組み合わせの追加の質問）

Q4: B では戻すときにイメージと `.env` を戻す。ただし今回の `.env` の変更は、見本の対象DB の2行を `.env.targetdb` へ移すことだけで、前の版のアプリはこの2行を使わない。`.env` の複写を戻すと、アプリのコンテナが再び見本の対象DB の管理者のパスワードを持つ（6件目の直しが戻る）。

A. 戻すときは `.env` を戻さない（イメージだけを `mastersmith:pre-followup` に戻す。`.env` と `.env.targetdb` は新しいまま）。`.env` の複写は、移し替えを失敗したときの復旧のためだけに使う
B. Q4: B のとおり、戻すときは `.env` の複写も戻す（6件目の直しが戻ることを受け入れる）
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

- 統合と配備する版（Q1: A）: 配備の前に作業ブランチ `fix/260924-followup-fixes` を `develop` へ fast-forward で取り込み、`develop` の版を配備する。`origin` へのプッシュは依頼者が行い、CI の結果は配備の後に確かめる
- `.env` の移し替え（Q2: A）: AI が行う。先に `.env` をホームの下（権限 600）へ中身を表示せずに複写し、`MASTERSMITH_SAMPLE_TARGETDB_` で始まる2行を `.env.targetdb`（権限 600）に写して `.env` から消す。表示するのは行の名前と数だけ
- 見本の対象DB（Q3: A）: 配備と同じときに `targetdb-postgres` を新しい起動の入口で作り直す（ボリュームはそのまま）。配備したアプリから対象DB に接続できることを確かめる
- 戻し方（Q4: B、F1: A）: 配備の前に今のイメージに `mastersmith:pre-followup` のタグを付けて残す。戻すときはイメージだけを戻し、`compose.yaml`・`.env`・`.env.targetdb` は新しいまま。`.env` の複写は移し替えに失敗したときの復旧にだけ使う。データはそのまま（スキーマは変わらない）
- 決まっていること: 配備の前の k6 は省く（Build and Test で同じソースのイメージで済み）。内部DB のバックアップは取らない（スキーマの変更なし）。配備の後に healthy とスモークテストで確かめる

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
