# 結合テストの手順（integration-test-instructions）

Test Strategy は Minimal で、本 Intent はアプリのコードを変えていない。新しい結合テスト（`*IT`）は無く、既存の結合テストがすべて通ることを確かめる。

## 1. 実行のコマンド

```bash
# 既存の結合テスト（Spring と組み込み H2）。verify に含まれる
./gradlew :backend:cleanIntegrationTest :backend:integrationTest
```

## 2. 本 Intent の「結合」に当たる確かめ

コンテナ・イメージ・compose の設定が組み合わさって効くことは、次で確かめる（アプリの Java のテストでは確かめられないため）。

| 確かめ | コマンド | 合格の条件 |
|---|---|---|
| 設定の効き方 | `./docker/check-container-limits.sh` | 12 項目すべて OK、終了コード 0 |
| 直す前は失敗すること | `MASTERSMITH_IMAGE_TAG=pre-fix ./docker/check-container-limits.sh` | 終了コード 1（割合の指定が効かない） |
| 使い捨ての環境での上限 | `perf/README.md` の手順で起動し、`docker inspect mastersmith-perf-app-1 --format '{{.HostConfig.Memory}} {{.HostConfig.NanoCpus}}'` | 2147483648・4000000000 |

## 3. テストのデータ

- 結合テストは各テストがデータを用意する（既存のまま）。
- 使い捨ての環境は仮の署名鍵・仮の利用者を使い、終わったら `down -v` で消す。
