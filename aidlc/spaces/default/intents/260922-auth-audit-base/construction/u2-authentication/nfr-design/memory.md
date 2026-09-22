<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-22T11:30:00Z — 決まり 2.5・3.3 の「ロック中はダミーの照合」を、確定回答 Q1（照合を排他の外で先に行う）により「照合を1回行い結果を使わない」と読み替えた; 照合の回数と時間はそろい、決まり 2.7 の読み書きの回数も変わらないため。
- 2026-09-22T11:30:00Z — Contract Design を行わないため、U2 の API のパス（/api/auth/login、/api/auth/session/refresh・logout）と Cookie の名前・Path を NFR Design で決めた; Cookie の送り先を更新とログアウトに限る要件（NFR5.3）を1つの Path で満たすため、2つの API を共通の下位のパスに置いた。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-22T11:30:00Z — Origin の不一致の 403 のために、決まり 9.1 に無い code `ORIGIN_NOT_ALLOWED` を加えた; U1 の決まり 5.14（使う code すべてに日英の説明）に合わせるため。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-22T11:30:00Z — 存在しないメールアドレスのダミーの記録を複数行にし、排他を待たない指定で選ぶ形にした; 1行だと存在しないメールアドレスの試みどうしが待ち合わせ、決まり 2.7 の「待ち合わせが起きない形」に反するため。
- 2026-09-22T11:30:00Z — アクセストークンの検証の時刻のずれの許容を既定の 60 秒から 0 にした; 決まり 4.2 の境界（5分ちょうどで無効）をテストで確かめられるようにするため。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-22T11:32:00Z — ログインの手順の順番（照合を排他の前に）と ORIGIN_NOT_ALLOWED の追加が、承認済みの functional-spec.md WF2 と rules.md BR9.1 と食い違う（レビュー R-01・R-02）; 設計書の security-design.md 8章に違いを明記した。Functional Design の文書を直すか（やり直し）、Code Generation の計画の確認で揃えるかを依頼者に確かめる。
- 2026-09-22T11:30:00Z — U1 の共通のエラー応答の変換は 4xx を WARN で1回出すため、ログインの失敗の 401 も毎回 WARN になる; 運用でうるさければ、認証の失敗だけ INFO にするかを Code Generation で確かめる。
