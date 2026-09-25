<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-25T11:15:17Z — 既存の知識ベースが STALE のため再利用の選択肢は出さず、依頼者は Full rescan を選んだ。スナップショットは ./ 全体（source git:f6133c3e…）。深く読んだ範囲だけを analyzed に記録する方針（前回までと同じ）で、今回の Intent G・H に関わるユーザー・認証・監査・管理画面・フロントエンドの骨組みを重点に読む。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-25T11:33:39Z — 依頼者は Full rescan を選んだが、深さ Standard で開発担当が深く読んだのは今回の Intent に関わる 89 パス・19 部品だけだった。記録上の範囲は kind: partial とし ./ を analyzed.paths に入れない（前回までと同じ扱い）。比較の結果は NARROWER で、前回の深い範囲（dsl・dslmanage・backend-test-support・perf-and-monitoring）は流し読みに下がった。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-25T11:33:39Z — 今回の Intent に関わる所見 11 件に K-1〜K-11 の番号を付け、本文は持ち主の文書に1回だけ書き、business-overview.md には一覧だけを置いた。重複は無くなるが、読む人は所見の文書をたどる必要がある。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-25T11:33:39Z — アーキテクトがコードで確かめたところ、/api/auth/ の下ではアクセストークンを読まないため、ログインした利用者の API（パスワード変更・プリファレンス保存）をそこに置くと 401 になり、登録の完了の API は公開の決まりが要る（K-6）。java-mustache-processor の取得元・ライセンス・推移依存は未確認（K-3）。要件定義で扱う。
