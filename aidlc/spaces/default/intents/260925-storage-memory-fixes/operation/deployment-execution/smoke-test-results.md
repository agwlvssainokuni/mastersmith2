# スモークテストの結果（smoke-test-results）

`aidlc/spaces/default/intents/260925-storage-memory-fixes/operation/deployment-pipeline/deployment-strategy.md` 3節の項目。配備した版は `develop` の `581b006`（`deployment-log.md`）。

## 1. 結果

| 項目 | 確かめ方 | 結果 | 行った人 |
|---|---|---|---|
| 健全性 | `curl -s http://localhost:8080/actuator/health` | `{"status":"UP"}`（入れ替えの後・`compact` の後・スモークテストの後の3回） | AI |
| ログイン画面・ログイン・管理者向け領域 | ブラウザで初期管理者でログインし、サイドバーの「管理」 | 通過 | 依頼者 |
| DSL の管理画面 | サイドバーの「DSL」 | 通過（今の状態が表示され、「接続できない」にならない） | 依頼者 |
| ログアウト | ユーザーメニューのログアウト | 通過（ログイン画面に戻る） | 依頼者 |
| 詰め直しの後のログイン | `compact` の後にブラウザでログイン（上のログインが兼ねる） | 通過 | 依頼者 |
| ログ | `docker compose logs app` を JSON として数えた | 49 行すべて1行1件の JSON、ERROR 0 件、WARN 5 件 | AI |

- WARN の内訳: 起動の案内2件（Spring の Bean の案内、Flyway が H2 2.4.240 を未検証とする案内。前の版から同じ）と、エラー応答への変換3件（`REFRESH_FAILED` 401 が1件、`DSL_PREVIEW_NOT_FOUND` 404 が2件）。後者はログインの前の画面の読み込みのトークンの更新と、プレビューが無い状態の DSL の画面の読み込みで、想定どおりの業務の応答。
- ログの `jdbc:` の文字列は内部DB（組み込みの H2）のファイルの URL だけで、資格情報や対象DB の接続情報は含まない（project.md の Forbidden は対象DB の接続情報が対象）。

## 2. 監査ログについて

- スモークテストのログイン・ログアウトは監査ログに残り、追記だけで消せない（送る前に依頼者に伝えた）。今回は画面・ログイン・監査のコードを変えていないため、監査イベントの確かめ（アプリを止めて内部DB を複写する）は行っていない。
- 詰め直しの操作は監査ログにもアプリのログにも残らない（依頼者の決定 Q3）。道具の出力を `health-check-report.md` に残した。

## Sources

- `aidlc/spaces/default/intents/260925-storage-memory-fixes/operation/deployment-pipeline/deployment-strategy.md`（3節）
- `aidlc/spaces/default/intents/260925-storage-memory-fixes/operation/deployment-pipeline/cd-config.md`
- `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/build-and-test/test-results.md`
