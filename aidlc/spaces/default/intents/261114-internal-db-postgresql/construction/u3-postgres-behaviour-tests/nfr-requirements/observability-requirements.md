# 観測の要件 — u3-postgres-behaviour-tests

この単位はテストだけで、アプリのログ・指標・トレースを足しません。上流の要件のうち観測に当たるのは、NFR2（出力に値を出さない）と NFR5（時間の記録）で、`security-requirements.md`（NFR2.x）と `performance-requirements.md`（NFR5.x）に書きました。ここでは、テストの結果の見え方だけを決めます。

## 要件

| ID | 要件 | 確かめ方 | 由来 |
|---|---|---|---|
| NFR5.4 | 待ちの確かめと起動の時間は、標準出力に 1 行ずつ、`INTERNAL_DB_PG_<名前> <キー>=<値>` の形で出す（U1 の `INTERNAL_DB_PG_BASELINE` と同じ形）。値には秘密・行の値を含めない。 | テストの標準出力に当該の行があること | 要件 NFR5、U1 の記録の形 |
| NFR2.6 | テストの失敗の出力は、期待と実際の状態コード・`code`・件数・クラス名だけを出し、原因が分かること（どの機能のどの流れか）を、テスト名と `@DisplayName`（英語）で示す。 | 失敗させて出力を読む確かめを、Code Generation で 1 回行う | 要件 NFR2、`team.md` の Testing Posture |

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md`（NFR2・NFR5）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/nfr-requirements/performance-requirements.md`

## Assumptions & Open Questions

None.
