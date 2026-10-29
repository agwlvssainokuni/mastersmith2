<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-29T22:40:00Z — 契約 C2 の検索の条件の形（column と value の1組）では範囲・複数の値を表せないため、ピッカーの演算子を EQUALS と CONTAINS に限ると読んだ（D6、BR3.2）。承認の場で確かめる。
- 2026-10-29T22:40:00Z — 値のカラムがバイナリか password のときは値を文字で渡せないため、参照を提供しない（NO_PERMISSION）と読んだ（BR3.1）。契約の状態の値を増やさないため。
- 2026-10-29T22:40:00Z — scaffold が出した AC2.3.5 は stories.md の初稿の ID の対応表にしか無いため、N/A（最終版の AC2.1.10〜AC2.1.12 で追う）にした。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-29T22:40:00Z — 契約 C2 は formPart を用途によらず DSL の値で返すとしたが、purpose が CREATE・UPDATE のときだけ lookup を返し、VIEW では今のまま text にした（D2）。U3 が入るまでの間、purpose を送らない既存の作成・更新の画面を壊さないため。
- 2026-10-29T22:40:00Z — 契約 C1 の check の注記（detail を使う）と違い、ラベルの1件読みと check は search（値の一致・読む行 1）を使う形にした（D4）。detail は主キーで引き2行で「1つに決まらない」を返すため。check の Unavailable は理由つきの FAILED にした（D3）。
- 2026-10-29T22:40:00Z — AC3.5.1 は検索を「件数とページの 2 文」と書くが、契約 C2 の「件数を数えない」に従い文は1つにした（functional-spec.md の 9節）。
- 2026-10-30T00:00:00Z — 依頼者の Request Changes で直した: ReferenceReadSpec に切らない値のカラムと固定の並びを持たせ（BR7.3・BR11.4 を新設）、定義の全体の上限は ReferenceLookup が上限だけを絞った TargetReadSession（同じ接続・同じ壊れた印）を文ごとに作る形にし残りを秒に切り上げ 1 秒未満は 503（BR8.4）、check を 200 行まで読み型つきの値の厳密な一致で FOUND にし（BR9.4・BR7.6 を新設）、選択肢の重複の除去も同じ一致にし、U2 向けの countState を足し（BR9.7 を新設、契約との差 D7・D8）、ピッカーの searchFields・listColumns に valueType を足し（D9）、U3 へ入力不可の表示を先に出す引き継ぎを足した。決まりは 71 件。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-29T22:40:00Z — ラベルは既存の一覧の上限（LIST_TEXT_LIMIT）で切り、切った印は契約に無いため返さない（BR7.3）。200 件の選択肢の応答の大きさを抑える代わりに、長いラベルは画面で全文を出せない。上限は NFR 設計で見直してよい。
- 2026-10-29T22:40:00Z — 用途で入力できない参照元のカラムは参照先を読まず NO_PERMISSION にした（BR3.3）。読み取りを省ける代わりに、U3 は権限の文言ではなく通常の入力できない表示を優先する必要がある。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-29T22:40:00Z — 承認の場で確かめる4点（functional-spec.md の 12節）: D6 の演算子の限定、値のカラムがバイナリ・password のときの NO_PERMISSION、入力できない参照元の NO_PERMISSION、ラベルの切り方。
- 2026-10-29T22:40:00Z — REFERENCE の問い合わせ1回の時間の上限と、定義の取得の全体の上限の値は NFR 設計で決める（functional-spec.md の 10節）。
