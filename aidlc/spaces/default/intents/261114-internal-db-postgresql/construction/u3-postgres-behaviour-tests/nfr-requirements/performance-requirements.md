# 性能の要件 — u3-postgres-behaviour-tests

上流の要件 NFR1（性能）・NFR5（検査の時間）を、この単位の `NFR1.x`・`NFR5.x` に分けます。この単位はテストだけで、アプリの性能は変えません。性能に関わるのは、PostgreSQL のテストを明示して流したときの時間と、普段の検査の時間への影響だけです。

## 要件

| ID | 要件 | 確かめ方 | 由来 |
|---|---|---|---|
| NFR1.1 | アプリの性能・資源の目標（応答時間・メモリなど）は変えない。PostgreSQL の結合テストは、アプリの性能の合否に使わない（性能の値は基準として記録するだけ）。 | PostgreSQL のテストに、応答時間・メモリの閾値の確かめを入れないこと（コードの読み取り） | 要件 NFR1 |
| NFR1.2 | 待ちの上限の確かめ（BR3.1〜BR3.4・BR5.2・BR5.3）が測る時間は、要件 FR3.3 の幅だけで合否を決める（書き込みの待ち: 2 秒以上 3 秒未満、行の排他の待ち: 3 秒以上 4 秒未満）。時間は、待ち側が `pg_stat_activity` で待ち（`wait_event_type = 'Lock'`）に入ったことを観測した時点から、失敗が戻る時点まで測る。幅の外れは、機器の負荷の揺れではなく設計の差として扱い、広げて通さない。 | 待ちのテストが、観測の後に測った時間と幅を照らすこと | 要件 FR3.3、`team.md` の「不安定なテスト」 |
| NFR5.1 | PostgreSQL のテストは、普段の `./gradlew verify`・`./gradlew verifyQuick`・CI（`develop` へのプッシュ）の検査の時間に加わらない（タグ `internal-db-postgres` で除外され、`internalDbPostgresTest` だけが流す）。 | Build and Test で、CI の検査の job の時間が基準（21分32秒）の 1.1 倍（約 23分42秒）以内で、約 42 分の線の内であること。U3 の構造のテスト（`*Test`、1 件）が足す時間は 1 秒未満 | 要件 NFR5 |
| NFR5.2 | 明示して流したときの時間を測って記録する（全体と、待ちの確かめの 1 件ごと。標準出力に 1 行）。合否には使わない。待ちのテストの時間の合計は、待ちの上限の合計（約 2 秒 × 書き込み系 + 約 3 秒 × 行の排他系）に、起動の時間を足した範囲に収まる見込みを、Build and Test で測った値と並べる。 | `internalDbPostgresTest` の実行の記録（Build and Test） | 要件 NFR5、Units Generation の U3 の実装の注意 |
| NFR5.3 | 共有のアプリ（流れの確かめ）は JVM ごとに 1 回だけ起動し、クラスごとのアプリは待ちの設定を変える確かめだけに限る。起動の時間は U1 の基準（`InternalDbPostgresBaselineIT` の値）と並べて記録する。 | Build and Test の記録 | 要件 NFR5、`team.md` の Testing Posture |

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md`（NFR1・NFR5・FR3.3）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/functional-design/rules.md`（BR3・BR5・BR9.6）
- `aidlc/spaces/default/memory/team.md`（Testing Posture）

## Assumptions & Open Questions

- [assumption] 待ちの幅の判定は、観測の後の測りで 1 秒の幅があるため、機器の負荷で揺れない。揺れたときは、team.md の「不安定なテスト」の決まりで原因を確かめる。
