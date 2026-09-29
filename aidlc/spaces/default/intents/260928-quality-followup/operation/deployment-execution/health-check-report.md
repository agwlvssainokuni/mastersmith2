# 健全性の確かめ（health-check-report）

配備の後の健全性の確かめです（2026-09-29）。流れと中止の条件は `aidlc/spaces/default/intents/260928-quality-followup/operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`、関門の元は `aidlc/spaces/default/intents/260928-quality-followup/construction/build-and-test/test-results.md` です。

## 1. コンテナ

| コンテナ | イメージ | 状態 |
|---|---|---|
| `mastersmith-app-1` | `mastersmith:local`（`sha256:252bc44e810813e9…`） | healthy（配備の後 21:59:33、監査の確かめの再起動の後 22:05:17・22:07:22） |
| `mastersmith-targetdb-postgres-1` | `postgres:18.6@sha256:5a5a84b19854…` | running |
| `mastersmith-mailpit-1` | — | 配備の前に止めて消した（手順どおり） |

## 2. アプリ

| 確かめ | 結果 |
|---|---|
| `/actuator/health`（compose の健全性の確かめ。bash の `/dev/tcp` で 200） | healthy |
| 起動のログ | ERROR 0 件。WARN は既知の2件（Spring の Bean の案内、Flyway の H2 の版の案内） |
| 起動の後のエラー応答 | `REFRESH_FAILED`（401）1件、`DSL_PREVIEW_NOT_FOUND`（404）2件。どれも想定内の業務の応答（`smoke-test-results.md`） |
| 内部DB | スキーマの変更は無く、Flyway は新しい版を当てていない。監査の記録は追記されている（`LOGIN_SUCCEEDED`・`DSL_GENERATED`・`DSL_APPLIED`・`LOGGED_OUT`） |
| 見本の対象DB への接続 | 既定の DSL の生成で、`mastersmith-target-db` のプールが起動し、生成が成功した（`dsl.outcome: success`） |

## 3. 止めた時間

| いつ | 長さ | 理由 |
|---|---|---|
| 21:59:22〜21:59:33 | 約 11 秒 | 入れ替え |
| 22:05:08〜22:05:17 | 約 9 秒 | 監査の確かめの複写 |
| 22:07:11〜22:07:22 | 約 11 秒 | 監査の確かめの複写（確かめ直し） |

## 4. 判定（まとめの確かめ直しの後に保存）

健全。戻し（`rollback-runbook.md`）は不要。
