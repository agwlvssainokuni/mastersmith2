<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

- 2026-10-30T00:00:00Z — 要件に監視の NFR が無く、NFR8（運用）はコンテナと JWT の要件のため、監視の要件をログは NFR1.15、p95 の区画は NFR2.10、503 とプールは NFR3.8 に寄せ、NFR8 は N/A とした（project.md の「当たる NFR が無い要件は近い NFR に寄せ、寄せたことを書く」）。
- 2026-10-30T00:00:00Z — k6 の場面は Build and Test で書いて最初の値を記録し、合否の持ち主は Performance Validation（AC3.5.4）と読んだ。この Intent には Performance Validation の段がある。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-30T00:00:00Z — 「決まっていること」は値の長さが MAX_VALUE_LENGTH（10,000 文字）に従うとするが、コードを読むとそれは書き込みの側の上限で、読み取りでは valueColumn を切らずに読む。見積もり（200 × 10,000 文字）はそのまま書き、10,000 文字を超える値の扱いを NFR 設計の未決として scalability-requirements.md の補足に書いた。
- 2026-10-30T00:20:00Z — 承認の場の Request Changes（依頼者の決定: 各指摘の提案どおり）で直した: 読み取り側の値の長さの上限（10,001 文字に切って読み、10,000 文字を超える行を選択肢・ピッカーから外し check で無いとする。F1: A）を NFR2.8 の要件にし先送りをやめた（機能設計 BR7.3 との差を補足に記録）、perf の読み取りのプールを前の Intent と同じ 5 に固定し MySQL・MariaDB の部分一致が線を超えうることと緩めずに諮ることを NFR2.3・NFR3.3 に書いた、NFR1.1 の 403 の利用者にラベルのカラム NONE・使える検索の条件なし・valueColumn がバイナリか password を足した、NFR3.5 に時間切れの最悪約 11 秒と k6 の p95 の対象外を書いた。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-30T00:00:00Z — プールの上限を下げた k6 の場面を置かず、接続の数は *ConnectionUsageIT で決定的に確かめる形にした（NFR3.3）。負荷の下での借りる待ちの長さは測らない代わりに、見積もりの誤りを決定的に見分けられる。
- 2026-10-30T00:00:00Z — 部分一致の線 3,000 ms は http.server.requests のバケットの境界に無いため、サーバーの側の p95 は参考とし、合否は k6 で判定する形にした（バケットを足すと系列が増える）。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-30T00:00:00Z — 10,000 文字を超える値（アプリの外で入れた行）を valueColumn に持つ参照先の読み取りの扱い（読むまま・落とす・NO_PERMISSION と同じ）を NFR 設計で決める。
- 2026-10-30T00:00:00Z — 定義の全体の上限 10 秒を定数にするか設定にするか、longest() に REFERENCE を入れる形を NFR 設計で確かめる。
