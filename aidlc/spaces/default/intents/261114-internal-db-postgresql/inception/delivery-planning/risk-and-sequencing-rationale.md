# 順の理由と危険 — internal-db-postgresql

Bolt は、1つ以上の作る単位（Unit of Work）を、設計から統合まで通して動くものにする1回の作り込みのこと。この文書は、Bolt を B1（U1）→ B2（U2）→ B3（U3）→ B4（U4）の順に並べた理由を書く。

## 使った考え方

危険を先にする並べ方（risk-first。Cohn の「分からないことを早く確かめる」順）を使った（Q1: A）。価値と大きさで点を付ける形（WSJF: 価値と急ぎを大きさで割った値の大きい順）は使わなかった。4単位はどれも PostgreSQL の内部DB を動かすのに欠かせず、価値で差が付かないため。

## 順の理由

| 位置 | 単位 | 理由 |
|---|---|---|
| 1 | U1 | U3・U4 が頼り、U2・U3 の機能設計が U1 の機能設計で決める組（契約 C3）と土台（契約 C4）を使う。移行と起動が通るかが最も大きな未知 |
| 2 | U2 | U3 が頼る。H2 だけで確かめられ、U1 の組（バイナリの写し方）に従う |
| 3 | U3 | PostgreSQL で全機能の振る舞いを確かめ、本体の直し（U2 の小さな直し）が見つかりうる。文書より先に置き、直しの結果を U4 に一度で書く |
| 4 | U4 | compose と文書。それまでの直しの結果（設定・対応する版・手順）を反映する。手元での起動確認とバックアップの練習は Deployment Execution で行う |

並びは依存の図のトポロジー順で、外れはない（`bolt-plan.md` のトポロジー順の確かめ）。B と C の案（U4 を先に置く・U2 と U4 の後に U3）は、U3 で見つかった直しが文書に及ぶため選ばなかった。

## 危険と手当て

| 危険 | 起きると | 手当て | 確かめる場所 |
|---|---|---|---|
| H2 の列の型のまま、両方の `ddl-auto: validate` が通る単一の写し方が無い（契約 C3） | U2 の範囲と移行の形が変わる | 捨ての試しで U1 の機能設計の前に確かめ（Q3: A）、無ければ U1 の機能設計で依頼者に諮る | U1 の機能設計の前 |
| Auth・Invitation の SQL が PostgreSQL で通らない（契約 C5） | U2 の範囲が増える | U2 用の捨ての試しも U1 の機能設計の前にまとめて行い、外れたものを U2 の範囲に足す | U1 の機能設計の前・B2 |
| `MERGE` の書き換えで、同時の実行の一意の違反の出方が変わる | 区分の応答が変わる | 捨ての試しと H2 の結合テストで確かめる | B2・B3 |
| 起動時の移行が `lock_timeout` 2 秒で切れる | PostgreSQL で起動しない | U1 の NFR 設計で決め、B1 の結合テストで確かめる | B1 |
| U3 で本体の直しが見つかる | B3 の統合が待たされる | U2 の小さな直しとして別に統合してから B3 を統合する（U3 は本体を変えない） | B3 |
| PostgreSQL の結合テストが長い | 確かめの時間が延びる | 確認の場には入れず、PostgreSQL の経路を変える Bolt の終わりの条件で明示して流し、時間を記録する（Q5: A） | B1〜B3 |
| カバレッジの下限を PostgreSQL の結合テストなしで満たせない | `verify` が失敗する | PostgreSQL のときだけ通るコードを U1 に集め、単体テストで満たす（NFR6） | B1 |

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/delivery-planning/delivery-planning-questions.md`（Q1〜Q5: A）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/units-generation/unit-of-work-dependency.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/contract-design/contract-summary.md`（C3・C4・C5）
- `aidlc/spaces/default/knowledge/aidlc-shared/study-internal-db-postgresql.md`
