# 拡張性の設計 — u2-portable-sql

アプリのインスタンスは 1 つ、内部DB も 1 つの前提のままです。スケールアウト・分割・キャッシュ・非同期は、この単位に当たらないため使いません。ここでは、同時の書き込みと接続の使い方の設計だけを書きます。

## SD-1 同時の書き込み（NFR7.1・NFR7.3）

- 同じ鍵（行がまだ無い）への 2 つの書き込みは、**一意の制約（主キー）と行の排他**で直列になる。後の側は、先の側が確定するまで待った後、鍵の重なりを受けて、更新をやり直す（後勝ち）。行が既にある鍵への書き込みは、更新の行の排他で直列になる。
- 待ちは有限: H2 は約 4 秒（未確定の挿入）・約 2 秒（更新）、PostgreSQL は `lock_timeout` の 2 秒（U1）。上限を足したり変えたりしない。分散のロック・アドバイザリーロックは使わない（1 インスタンス）。

## SD-2 接続の使い方（NFR7.2）

- 手順は呼び出し元のトランザクションの接続の中で完結する（セーブポイントも同じ接続）。接続プール（上限 30）の使い方を増やさない。確かめ: `StatementCounter`（`performance-design.md` の DD-4）で、書き込みの手順が接続を借りる回数が書き直しの前と同じであること。
- 既知の制約（要求 1 件で接続を 2 本使う経路。確定の後の監査の書き込み）は変わらない（`docs/operations.md` の既知の制約）。

## SD-3 表の形・データの量（NFR7.4）

- 表・列・制約・索引を変えない（移行を足さない）。データの量の見積もりは、U1 の NFR 要件のとおり。

## 採らなかった形

| 形 | 理由 |
|---|---|
| アドバイザリーロックで鍵ごとに直列にする | PostgreSQL にだけ通る書き方で、H2 に当てられない（ADR-003）。1 インスタンスの前提で足りる |
| 重なったときに待たずに失敗させる | H2 の今の「後勝ち」が変わる（FR5.4） |

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/nfr-requirements/scalability-requirements.md`（NFR7.1〜NFR7.4）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/functional-design/rules.md`（BR2.1・BR2.2・BR2.5）・`functional-spec.md`（F5・F6）
- `aidlc/spaces/default/memory/team.md`（Deployment）

## Assumptions & Open Questions

None.
