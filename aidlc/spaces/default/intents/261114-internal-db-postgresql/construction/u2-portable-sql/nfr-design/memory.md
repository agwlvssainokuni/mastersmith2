<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

- 2026-11-22T04:20:00Z — 質問は作らなかった。この単位の NFR 設計は、機能設計の決め（Q1〜Q3: A）と NFR 要件で決まり、新しく依頼者に判断を仰ぐ論点が無いため。機能設計の審査の Major 2 件（`@Query` から `doReturningWork` を呼べない・`doWork` で flush と例外の型が変わる）には、`reliability-design.md` の「上流との差」の節で設計側の答えを書いた（カスタムのフラグメント・`flush`・重なり以外の `SQLException` は外へ投げる）。機能設計の文書は直していない（承認の場で直す）。
- 2026-11-22T04:20:00Z — NFR 要件の審査の Minor 4 件のうち、文の数を数える手段（`StatementCounter`）と、`ConnectionAcquireCounter` の置き場（別の機能のテストの部品を参照しない）は `performance-design.md` の DD-4 で答え、NFR4.8・4.9 の由来の言い回しは `observability-design.md` で答えた。NFR 要件の文書は直していない（承認の場で直す）。

## Deviations
- 2026-11-22T04:20:00Z — 機能設計の BR2.4「SQLException を外へ出さない」は重なりだけに当てる（ほかの失敗は外へ投げて Hibernate の変換に任せる）。BR2.5 の上限切れの後の継続も、手順の内側（重なりの後のやり直し）だけが対象になる。`reliability-design.md` の「上流との差」に書いた。
- 2026-11-22T04:20:00Z — 審査済みの `security-requirements.md` の Sources に手を入れかけたが、直後に元の文字列へ戻した（審査済みの文書は直さない決まり）。traceability のセンサーが Sources の番号の参照（U1 の NFR2.8）を拾うため、NFR 設計の traceability では N/A にして理由を書いた。

## Tradeoffs
- 2026-11-22T04:20:00Z — `savePreferences` をカスタムのフラグメントに移す代わりに、`@Query` の宣言が 1 つ減り、実装クラスが 1 つ増える（`UserRepository` のメソッドの形は変わらない）。

## Open questions
- 2026-11-22T04:20:00Z — Hibernate が `doWork` の外へ出た `SQLException` を `executeUpdate` と同じ型に変換するかは、コード生成の最初の結合テストで確かめる。
