<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-29T14:46:27Z — 計画の 6節の確かめ（Q-A〜Q-E）を計画の承認の前に依頼者に尋ねて反映した。キーの名前は maskedEmail に替え（外部エクスポートでは伏せ字がそのまま送られる）、team.md は Testing Contract の入力のため Build and Test で直し、新しい違反は一覧に足して進める形になった。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-29T15:35:34Z — ログのキーの名前を替えた（Q-B: B）ことで、計画の影響の範囲に無かった OtlpLogExportIT（外部エクスポートで email が [REDACTED] になることを確かめる）が最後の verify で落ちた。依頼者の答え G1: A で、email は 0 件・maskedEmail は伏せ字のまま送られることを確かめる形に直した。ログのキーの名前を替える計画では、そのキーを確かめている既存のテスト（外部エクスポート・漏えいのテスト）をキーの名前の検索で洗い出して影響の範囲に入れる。
- 2026-09-29T14:32:23Z — 要件の承認の後、依頼者が R-01（監査の失敗の ERROR を伏せ字にすると手で補う手がかりが失われる）を受けて「伏せ字にしない」と決めた。承認済みの requirements.md は書き換えず、FR2.1・FR2.2 を行わないことと Forbidden との差（既知の例外）を計画の「要件との差」に記録する形にした。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-29T14:46:27Z — 開発担当への依頼で、testing-posture brief の出力（約 84KB）を依頼の本文に貼らず、先頭の2行（AIDLC-STAGE・AIDLC-TESTING-CONTRACT）だけを本文に置き、残りは保存したファイルを最初に読ませる形にした。依頼を2回（Step 1〜12・13〜23）に分けるため、同じ内容を2回貼る量を避けた。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
