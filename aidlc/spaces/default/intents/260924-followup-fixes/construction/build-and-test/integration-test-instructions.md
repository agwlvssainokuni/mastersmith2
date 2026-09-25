# 結合テストの手順（integration-test-instructions）

Test Strategy は Minimal だが、bugfix の回帰の確かめ（org.md の Testing Posture）として、この Intent の結合テストと、変更が触れる既存の結合テストをこの段で流す。結合テストは組み込みの H2 で Spring を起動する `XxxIT`（`:backend:integrationTest`）と、対象DB のコンテナを使う結合テスト（`verify` の中）からなる。

## 1. この Intent の結合テスト

| テスト | 確かめること | 要件 |
|---|---|---|
| `LoginConcurrencyIT`（足した 4 件を含む 7 件） | ロックの状態の行が無い利用者への同時のログインで内部の失敗が 0 件、行が 1 つ、監査の記録が二重にならない。同時の失敗 4 回はロックされず 5 回でロック、ロック中は正しいパスワードでも拒否。待ち合わせでの再現 | FR2.1・FR2.2 |
| `OtlpLogExportIT`（新規） | 送り出しを有効にしたとき、キーと値が属性として届き、`email`・`enteredEmail`・`sourceIp`・`userAgent` が `[REDACTED]`。パスワード・アクセストークン・リフレッシュトークン・署名鍵が `/v1/logs`・`/v1/traces` に無い | FR1.1・FR1.2・FR1.4、NFR1 |

## 2. 守るべき既存の結合テスト

`LoginAttemptStateRepositoryIT`・`LoginApiIT`・`AuthSettingsIT`・`AuthSecretLeakIT`・`RefreshConcurrencyIT`・`AuditAuthenticationEventsIT`・`AuditTraceIdIT`・`AuditRollbackIT`・`AuditWriteFailureIT`・`AuditWriteTimingIT`・`ExternalExportIT`・`TracingAndLoggingIT`・`AuditNotInAppLogIT`・`AuditSecretLeakIT`（NFR6。FR1.3 の標準出力が変わらないことを含む）。

## 3. 実行

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
# すべての結合テスト（対象DB のコンテナを含む）は verify の中で流れる（build-instructions.md の 2節）
caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
```

この Intent に絞った実行のコマンドは `aidlc/spaces/default/intents/260924-followup-fixes/construction/code-generation/unit-test-instructions.md` にある。`verify` は全体を1回流すため、絞ったコマンドを重ねて流さない（二重に数えない）。

## 4. コンテナの設定の確かめ

```bash
MASTERSMITH_IMAGE_TAG=followup-fixes ./docker/check-container-limits.sh
```

- 既定のメモリの上限が両方の compose で 2g（2147483648）、`MASTERSMITH_CONTAINER_MEMORY=768m` で 768m、JVM の確かめ、節5（`app` は `.env` だけを読み `MASTERSMITH_SAMPLE_TARGETDB_*` を持たない、見本の対象DB の3つは `.env.targetdb` を読む）がすべて通る（FR5・FR6.2）。
- 依頼者の判断で試験のイメージは `mastersmith:followup-fixes` とする（配備の `mastersmith:local` は上書きしない）。

## 5. 期待するカバレッジ

行 80%・分岐 70%（全体の合計と、新しいパッケージごと。バックエンドは JaCoCo、画面は `@vitest/coverage-v8`）。下げない・除外を増やさない（team.md）。この Intent では新しいパッケージは作らない。

## 6. テストのデータ

組み込みの H2 のテストは、テストごとにデータを用意して巻き戻す（`TestDatabase`）。対象DB は版を固定したイメージのコンテナで起動する（project.md の Mandated）。秘密の値はテストの中で乱数で作り、ログに出さない。
