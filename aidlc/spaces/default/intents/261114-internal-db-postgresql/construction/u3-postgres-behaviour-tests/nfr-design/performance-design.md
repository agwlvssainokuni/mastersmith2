# 性能の設計 — u3-postgres-behaviour-tests

NFR1.1・NFR1.2・NFR5.1〜NFR5.4 を設計に落とします。この単位はテストだけで、アプリの性能は変えません。

## 設計の決定

| ID | 決定 | 満たす NFR | 理由 |
|---|---|---|---|
| DD-8 | アプリの性能・資源（応答時間・メモリ）の閾値の確かめを PostgreSQL のテストに入れない。時間を測るのは待ちの上限（`reliability-design.md` の DD-1）と起動・全体の記録だけ。 | NFR1.1 | 要件 NFR1 |
| DD-1（参照） | 待ちの時間は呼び出しの開始から測り、幅は要件のまま（書き込み: 2 秒以上 3 秒未満、行の排他: 3 秒以上 4 秒未満）。 | NFR1.2 | `reliability-design.md` |
| DD-9 | 普段の検査への影響を、2 つの構えで抑える。(1) PostgreSQL のテストは `@InternalDbPostgresIT`（タグ `internal-db-postgres`）で `integrationTest` から除外される（U1 の設定）。(2) 普段の `verify` で流れる U3 の新しいテストは構造のテスト 1 件（`*Test`、ArchUnit）だけで、コンテナを使わない。測り方: 普段の `verifyQuick` の中の `:backend:test` で、この 1 クラスの時間を Gradle の結果の XML の `time` で読み、1 秒未満であることを記録する。 | NFR5.1 | 要件 NFR5。機能設計のレビューの「1 秒未満の根拠」への答え |
| DD-10 | 明示して流したときの時間の記録: 全体の時間は Gradle の実行の時間、待ちの確かめ 1 件ごと（BR3.1〜BR3.4・BR5.2・BR5.3）は、標準出力に `INTERNAL_DB_PG_WAIT <テスト名> waitMs=<値>` の 1 行、起動は `INTERNAL_DB_PG_BASELINE`（U1 と同じ）に並べて `INTERNAL_DB_PG_SHARED_APP startupMs=<値>` の 1 行を出す。値は数字だけ。合否には使わない（待ちの幅の合否は DD-1 の判定のみ）。 | NFR5.2・NFR5.3・NFR5.4 | U1 の形に合わせる |
| DD-11 | 待ちの確かめの時間の合計の見込み: 書き込みの待ち（2 秒台）× 回数 + 行の排他（3 秒台）× 回数 + 行き詰まり（約 1 秒）。Code Generation の計画で、待ちの確かめの回数を数え（1 回ごとに 2〜4 秒）、合計をテストの総数と並べる。 | NFR5.2 | 時間の見込みを計画で数える |

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/nfr-requirements/performance-requirements.md`・`observability-requirements.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/nfr-design/reliability-design.md`

## Assumptions & Open Questions

None.
