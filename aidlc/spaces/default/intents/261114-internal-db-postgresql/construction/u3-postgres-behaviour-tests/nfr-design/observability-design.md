# 観測の設計 — u3-postgres-behaviour-tests

NFR2.6・NFR5.4 を設計に落とします。この単位はテストだけで、アプリのログ・指標・トレースを足しません。

## 設計の決定

| ID | 決定 | 満たす NFR | 理由 |
|---|---|---|---|
| DD-21 | 記録の行の形は、U1 の `INTERNAL_DB_PG_BASELINE startupToHealthMs=<値>` に合わせる。`INTERNAL_DB_PG_<種類> <キー>=<値>` の 1 行で、種類は `WAIT`（待ちの確かめの 1 件）・`SHARED_APP`（共有のアプリの起動）・`BASELINE`（U1）。値は数字だけで、秘密・行の値・接続の情報を含めない。出力の取り込みは、Build and Test が行を読んで表にする。 | NFR5.4 | U1 の形 |
| DD-22 | 失敗の出力の読みやすさ: 英語の `@DisplayName` に機能・流れ・確かめる点を入れ、`assertThat(…).as("<機能>/<流れ>: <確かめる点>")` で状態コード・`code`・件数だけを出す。 | NFR2.6 | `security-design.md` の DD-17 |

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/nfr-requirements/observability-requirements.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/nfr-design/performance-design.md`・`security-design.md`

## Assumptions & Open Questions

None.
