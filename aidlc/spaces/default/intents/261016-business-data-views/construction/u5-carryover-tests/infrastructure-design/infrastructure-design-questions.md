# Infrastructure Design の質問 — U5 u5-carryover-tests

U5 は library の単位です。成果物は cicd-pipeline と traceability の2つだけです。

## 前の段から持ち越した論点

- NFR 設計の「後の段に回すこと」は、すべてコード生成と Build and Test が持ち主になる。
- 承認の場の Major は、コード生成の計画で直す。
  - R-01: 一時ファイルの一覧と `KEEP=1`
  - R-02: `PERF_DB_URL` の確かめ
- 基盤に関わる論点は、記録のループの動かし方と、使い捨ての環境の扱いの2つ。

## 決まっていること（読み取りで確かめた事実を含む。質問にしない）

**cgroup v2**
- VM の `docker info` は `CgroupVersion=2` を返す。
- 動いているアプリのコンテナで、`memory.peak`・`memory.max`・`memory.stat` が読めることを確かめた。
- 始める前の確かめ（S3）は残す。

**記録のループ**
- アプリのイメージの bash で、`docker exec` を1回だけ回して記録する。出力は、ホームの側の `build/perf-results/<OUT_DIR>/` に書く。
- ループには秒の上限を渡し、自分で終わらせる。
- 時刻は VM の時計の UTC で記録する。始めと終わりに、VM とホームの時刻の差の範囲を記録する。

**一時ファイル**
- ホームの下の権限 700 の一時ディレクトリに置く。
- R-01 の事実を読み取りで確かめた。
  - `auth.header` は一時ディレクトリに書かれるため、一覧に足す。
  - `app-targetdb.env` は実在する。
  - `resp-*.body` は結果の置き場に書かれる。
- `KEEP=1` は `dslMixed` の手順の前提のため、「認めない」にはできない。残ることを明記して手で片付け、消えたことを確かめる。

**verify と CI**
- B1 の変更は、テストのファイルと `perf/` だけ。そのため、統合の前は verify だけで、E2E は要らない。
- CI・Gradle のタスク・vitest の設定は変えない。
- B1 の後に、verify と CI の時間を試行ごとに記録する。

**測る間**
- 配備したアプリを止める。
- 各回の前に `--force-recreate` する。
- `caffeinate -dims` で包む。
- WARN を数えてから片付ける。

**上流との差**
- `dslMixed` の手順は `KEEP=1 ./perf/dsl-timing.sh postgres` で環境を作るため、`PERF_DB_URL` は `$TMP/app.env` に書かれる。つまり、使い捨ての環境に渡る見込み。
- NFR 設計の前提（渡らない）と食い違うため、差として記録する。

## Q1. memory.peak の2回と、RETENTION_TIME の試しの2回の環境の分け方

A. 既定の2回と試しの2回で、環境を分ける（推奨）。
- 既定の2回は、1つの `KEEP=1` の環境で流し、各回の前に `--force-recreate app` する。内部DB は膨らんだまま流す（悪い側の条件）。
- 試しの2回は、環境を消して（`down -v` と一時ディレクトリの削除）から、`PERF_DB_URL` を付けて作り直し、同じ順で流す。確かめの関数は、1か所を通るようにする。
- どちらの環境も、終わりに手で片付け、消えたことを確かめる。

B. 4回とも同じ環境で流す。試しの回は、`app.env` に URL を足して作り直す（上書きの口を新しく足す。試しの回の H2 のファイルは、既定の2回で伸びた後から始まる）。

C. 4回とも、毎回環境を作り直す（最もそろうが、配備したアプリを止める時間が延びる）。

X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

**cicd-pipeline**
- CI・Gradle のタスク・vitest の設定は変えない。統合は squash で行い、統合の前の確かめは verify だけにする（E2E は要らない）。
- verify で足すテスト
  - 結合テスト: `RoleConcurrencyIT` の2つの場合
  - 単体テスト: `Actor` の toString のテストと、`AdminForbiddenProvider.test.tsx`
  - カバレッジ: テストのファイルだけの変更のため、下限への響きは無い。
- B1 の後に、verify と CI の時間を試行ごとに記録する。
- perf の手順（verify と CI の外）
  - 記録のループ: アプリのイメージの bash で `docker exec` を1回だけ使う。秒の上限で自分で終わる。時刻は VM の時計の UTC にする。VM とホームの時刻の差の範囲を記録する。
  - 始める前に、cgroup v2 のファイルが読めるかを確かめる。
  - `PERF_DB_URL` の確かめの関数は、一時ディレクトリを作る前の1か所で呼ぶ。失敗したら、親の処理を止める（R-02）。
  - 環境の分け方（Q1: A）
    - 既定の2回は、1つの `KEEP=1` の環境で流す。各回の前に作り直す。
    - 試しの2回は、環境を消してから `PERF_DB_URL` を付けて作り直す。
    - 終わったら、どちらの環境も手で片付け、消えたことを確かめる。
  - 測る間は、配備したアプリを止める。`caffeinate -dims` で包み、WARN を数えてから片付ける。
- 秘密
  - 一時ファイルの一覧を実物に合わせる（`auth.header` を足す。`KEEP=1` は「残る」と明記する）。
  - 文字列の検索は、今回の `OUT_DIR` だけを対象にする。
- Build and Test で、2回の合否（`memory.peak / memory.max < 0.9`）を判定する。あわせて、`anon` の最大と設定の値の確かめを並べて記録する。
- 上流との差: `dslMixed` の手順では、`PERF_DB_URL` が使い捨ての環境に渡る見込み（NFR 設計の前提と食い違う）。

**traceability**
- NFR4.5〜4.9 と NFR7.1〜7.3 を、cicd-pipeline の節に当てる。一部をコード生成で確定するものは、そう書く。

Does this all look correct before I generate the infrastructure design?

- Looks correct
- Request changes

[Answer]: Looks correct
