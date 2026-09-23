# 結合テストの手順（integration-test-instructions）

本 Intent の Test Strategy は Minimal のため、結合テストの手順は、bugfix の回帰テスト（不具合を再現できる最も狭い層が結合テスト）と、既存の結合テストの確かめに絞る。

## 1. 道具と設定

- JUnit 5、AssertJ、Spring Boot Test。Gradle の `integrationTest` タスク（`*IT`）。
- DB は `TestDatabase.register` による一時ディレクトリの組み込み H2（本番と同じ種類。コンテナは使わない）。
- 結合テストのプールの設定は本番と同じ（URL だけ差し替える）ため、既定値 30 がそのまま効く。

## 2. 実行のコマンド

```bash
# 本 Intent の再現テスト（同時 10 件・20 件の成功のログイン）と、プールの上限の設定のテスト
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.auth.web.ConcurrentLoginAuditIT' --tests 'cherry.mastersmith.config.DataSourcePoolIT' --rerun

# 検出力の確かめ: 上限を 10 に戻すと、再現テストが F2 を検出して失敗する（失敗が期待どおり）
MASTERSMITH_DB_MAXIMUM_POOL_SIZE=10 ./gradlew :backend:integrationTest --tests 'cherry.mastersmith.auth.web.ConcurrentLoginAuditIT' --rerun

# 既存の結合テストを含む全体（verify の結合テストの段）
./gradlew :backend:cleanIntegrationTest :backend:integrationTest
```

## 3. 確かめること

| テスト | 合格の条件 | 要件 |
|---|---|---|
| `ConcurrentLoginAuditIT`（N=10・20） | 応答がすべて 200、待ち合わせに N 件がそろう、`LOGIN_SUCCEEDED` の行が N 件、監査の失敗と「Connection is not available」のログが0件 | FR3.1〜FR3.4、NFR1 |
| `DataSourcePoolIT` | 既定の上限が 30（接続を借りる待ち 5000 ms は変わらない）、`MASTERSMITH_DB_MAXIMUM_POOL_SIZE=12` で 12、既定で 30 本を同時に借りられる | FR1.1、FR1.2 |
| 既存の監査・ログインの結合テスト（`AuditAuthenticationEventsIT`・`AuditTraceIdIT`・`AuditRollbackIT`・`AuditWriteFailureIT`・`AuditWriteTimingIT`・`LoginConcurrencyIT` ほか） | 変更なしですべて通る | FR4.1 |
| 上限 10 での再現テスト | N=10・20 とも失敗する（F2 を検出できる） | FR1.2、FR3 の検出力 |

## 4. 期待するカバレッジ

本番のコードの変更は無い。JaCoCo の下限（行 80%・分岐 70%）を満たし、値が下がらないこと。

## 5. テストのデータと環境

- 利用者はテストごとに N 人作り、監査の行はそのテストの利用者のメールアドレスに絞って数える。
- 要求の重なりは、`AuditWriteBarrierConfig` の待ち合わせ（上限 20 秒、`sleep` なし）で作る。
- 前提: シェルに `MASTERSMITH_DB_MAXIMUM_POOL_SIZE` を残さない（検出力の確かめのとき以外）。
