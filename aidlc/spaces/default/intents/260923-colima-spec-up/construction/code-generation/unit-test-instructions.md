# テストの手順 — コンテナのメモリの上限と JVM の設定の口（F3・F4）

## テストの道具と設定

- 今回の変更は、コンテナと JVM の起動の設定（`Dockerfile`・compose）と文書だけで、アプリの Java のコードは変えない。そのため、JUnit のテストは加えない。
- 確かめは、シェルのスクリプト `docker/check-container-limits.sh`（新規）で行う。使う道具は Docker（colima）と Docker Compose v2 で、追加の依存は無い。
- 前提:
  - Docker の実行環境が動いていること（`docker info` が通る）
  - イメージ `mastersmith:local` が作ってあること（`./gradlew :backend:bootWar && docker compose build app`）
- スクリプトは、小さなメモリの上限（例: 512m）で JVM の `-version` だけを起動する。そのため、今の VM（CPU 2・メモリ 2GiB）で、配備したアプリを止めずに実行できる。アプリは起動しないので、秘密情報（署名鍵など）は要らない。

## この Intent のテストを実行するコマンド

プロジェクトのルートで実行する。どれも、この Intent で加える確かめのスクリプトだけを動かす。

```bash
# 実行の準備の確認（Docker とイメージがあること）
docker info --format '{{.NCPU}} {{.MemTotal}}' && docker image inspect mastersmith:local --format '{{.Id}}'

# 設定の効き方の確かめ（メモリの上限の変数・JVM の既定の動作・JVM の設定の口・PID 1）
./docker/check-container-limits.sh

# 直す前のイメージでスクリプトが失敗すること（再現の確認。失敗が期待どおり）
# 直す前の Dockerfile で作ったイメージを mastersmith:pre-fix のタグで残しておく
MASTERSMITH_IMAGE_TAG=pre-fix ./docker/check-container-limits.sh
```

統合の前の検査はプロジェクト全体で1回だけ行う（Build and Test の段と、計画の Step 1・8）。テストの件数とカバレッジを実測するため、テストのタスクを消してから実行する。

```bash
./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
```

## テストの一覧（Minimal ＋ bugfix の回帰の確かめ）

| 確かめ | 種類 | 確かめること | 要件 |
|---|---|---|---|
| メモリの上限の既定値 | 設定の展開（`docker compose config`） | 環境変数なしで `app` の `mem_limit` が 1g（配備用・負荷の試験用の両方） | FR2.1 |
| メモリの上限の変数 | 設定の展開 | `MASTERSMITH_CONTAINER_MEMORY=768m` で 768m（両方） | FR2.1 |
| JVM の既定の動作 | コンテナでの JVM の起動（`-XX:+PrintFlagsFinal -version`） | 環境変数なしで `MaxHeapSize` が上限の 75% | FR3.2 |
| JVM の設定の口（割合） | 同上 | `MASTERSMITH_JAVA_OPTIONS='-XX:MaxRAMPercentage=60.0'` で `MaxHeapSize` が上限の 60% | FR3.1 |
| JVM の設定の口（ヒープ以外） | 同上 | `-XX:MaxMetaspaceSize=128m` を渡すと `MaxMetaspaceSize` が 128m | FR3.1 |
| 起動の形 | 同上 | PID 1 が java（`exec`）、`-Duser.timezone=Asia/Tokyo` が残る | FR3.2 |
| 回帰（直す前は失敗） | 同上 | 直す前のイメージでは、割合の指定が効かずスクリプトが失敗する | FR3.1（TD-8 の再現） |

不具合（F3・F4）そのものは、高い負荷と VM の大きさでだけ起きる。そのため、負荷による再現と修正の確認は Build and Test の段の k6 の試験（要件 FR5）で行う。この段では、その前提となる「設定が環境変数どおりに効くこと」を、確かめられる最も狭い形（JVM の起動だけ）で確かめる。

## カバレッジの下限

- JaCoCo の行 80%・分岐 70%（バックエンド）と Vitest の下限（フロントエンド）を下げない。除外も増やさない。
- アプリのコードの変更は無いため、カバレッジの値は変わらない見込みである。計画の Step 1 と Step 8 の実測で確かめる。

## 差し替えと待ち合わせの方針

- 差し替え（モック）は使わない。本物のイメージと Docker Compose で確かめる。
- 実時刻の待ち（`sleep`）には頼らない。JVM の `-version` は起動してすぐ終わる。
- スクリプトは、期待と違うときに、何が違ったか（期待の値と実際の値）を出して 0 以外で終わる。

## テストのデータ

- データは使わない。アプリは起動しない。
- 一時のコンテナは `docker run --rm` で作り、終わると消える。ボリュームは作らない。
- 配備したアプリのコンテナとボリュームには触れない。
