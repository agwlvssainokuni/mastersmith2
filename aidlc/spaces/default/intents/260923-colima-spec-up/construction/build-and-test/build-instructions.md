# ビルド手順（build-instructions）

本 Intent（F3・F4 の修正: colima の VM を CPU 4・メモリ 6GiB にし、コンテナのメモリの上限と JVM の設定を環境変数で変えられるようにした）のソースをビルドし、統合の前の関門を通し、コンテナのイメージを作るまでの手順。すべてのコマンドはリポジトリのルートで実行する。

## 1. 前提の道具

README の「前提の道具」と同じ（JDK 25、Node.js 24、Gitleaks 8.30.1、OSV-Scanner 2.6.0、pre-commit、Docker（colima）＋ Compose v2）。本 Intent で新しく要る道具は無い。

- colima の VM は CPU 4・メモリ 6GiB にする（README の「コンテナの資源の上限」）。確かめ方: `colima list`（CPUS 4・MEMORY 6GiB）、`docker info --format '{{.NCPU}} {{.MemTotal}}'`（4、約 6GB）。
- VM の設定はリポジトリの外にあり、コミットで固定できない。作り直すと動いているコンテナも止まる（`colima stop` → `colima start --cpu 4 --memory 6`）。

## 2. 取得と依存関係

```bash
git submodule update --init
```

依存関係は `./gradlew verify` の準備の段が lockfile どおりに入れる。本 Intent で依存関係は変えていない。

## 3. 環境の設定

ビルドとテストに環境変数は要らない。本 Intent で加えた設定は次の2つで、どちらも既定のままでよい。

| 変数 | 既定 | 用途 |
|---|---|---|
| `MASTERSMITH_CONTAINER_MEMORY` | `1g` | アプリのコンテナのメモリの上限（`compose.yaml`・`docker/perf/compose.yaml` の `mem_limit`）。この PC では `.env` で `2g` にする（Deployment Execution の段） |
| `MASTERSMITH_JAVA_OPTIONS` | 空 | JVM の引数を既定の引数（`-XX:MaxRAMPercentage=75.0 -Duser.timezone=Asia/Tokyo`）の後ろに足す（`Dockerfile` の `ENTRYPOINT`）。空白で区切る |

- `.env` はコミットしない。

## 4. ビルドと検査のコマンド

```bash
# 統合の前の1コマンドの検査（CI と同じ）。テストの件数とカバレッジを実測するため、テストのタスクを消してから実行する
./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify

# コンテナのイメージ（mastersmith:local）
./gradlew :backend:bootWar && docker compose build app

# コンテナと JVM の設定の効き方の確かめ（イメージを作った後）
./docker/check-container-limits.sh
```

## 5. ビルドの確かめ方

- `verify` が `BUILD SUCCESSFUL` で終わること。成果物 `backend/build/libs/mastersmith.war` ができていること。
- `docker/check-container-limits.sh` が「すべて期待どおりです」で終了コード 0 になること。
- 報告: `backend/build/reports/tests/`、`backend/build/reports/jacoco/`、`backend/build/reports/spotbugs/`、`build/reports/osv-scanner/osv.json`。

## 6. うまくいかないとき

- `verify` がテストのタスクを UP-TO-DATE で飛ばし、件数が出ないとき: `:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直す。
- `docker compose up` が CPU の上限で失敗するとき: VM の CPU が `MASTERSMITH_CONTAINER_CPUS`（既定 4）より少ない。`colima list` で確かめる。
- 確かめのスクリプトが「イメージがありません」で止まるとき: 上のイメージの作成を先に行う。
