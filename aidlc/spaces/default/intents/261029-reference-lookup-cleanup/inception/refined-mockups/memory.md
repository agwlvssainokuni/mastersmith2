<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-30T10:30:00Z — R-10（M7 の項目が空のまま「保存」を押したとき）の依頼者の承認での決定: 画面の検証で M7 の誤りの文を保ち、サーバーへ送らず、フォーカスを誤りのまとめへ移す。409 は、本文を書き換えられたときのサーバー側の守りとして残す（画面の検証をサーバー側の判定の代わりにしない）。M6 の「選び直してください」の文は M7 の項目には使わない。設計（Functional Design）の入力にする。
- 2026-10-30T09:00:00Z — Ideation のラフな画面イメージが無い（classic）ため、ストーリーと要件から直接作った。固定先 ecb07e9 のソースで Modal・Table・TextInput・FormField の口を確かめ、make-you-chic-ui への新しい依頼は出さない見込みとした。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-30T09:00:00Z — 決定の要約の道具（review-brief summary）が今回も動かず、答えのまとめだけで確認した。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-30T09:00:00Z — ピッカーの専用部品は作らず、Modal・TextInput・Select・Table（render の「選ぶ」ボタンの列）の組み合わせにした。専用の部品を上流へ依頼するより早いが、features/tables の中に組み合わせの実装が増える。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-30T10:30:00Z — 承認の場へ持ち越した Minor: R-06（375px の「選ぶ」の位置）・R-07/R-12（interaction-spec への状態の role と closeLabel、ReadonlyReferenceField の入力に作成か更新か・必須か）・R-11（M7 の2種類の日英の完全な文）。設計で決めて書く。
- 2026-10-30T09:00:00Z — レビューの Major 2件（M7 の作成と更新の分け・誤りと説明文の両立、AC3.1.7 のフォーカス先との差）と Minor 7件は承認の場へ持ち越した（advisory）。
