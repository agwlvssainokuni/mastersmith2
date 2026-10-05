<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-05T17:29:59Z — 要件 NFR6.4 の access.service を一覧から外す作業は role の範囲で当たらないと読んだ; 作業ロールは専用の API で渡し access・auth の本体を変えないため。cross-cutting で持ち主が決まっていなかった点を tech-stack-decisions.md に書いた。
- 2026-10-05T17:29:59Z — 別名の上限 0 はコレクションを指す別名 0 と読んだ; 今の部品はコレクションを指す別名だけを数え、スカラーを指す別名は節を増やさないため（機能設計の再レビューの R-05）。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-05T22:29:47Z — 承認の場の Request Changes（Major 11 件だけを直す）で R-01・R-02 を直した; resolve が写しを使わない形を契約 C5・機能設計 2.9 との差として記録し U5 に必須の引き継ぎを書いた。1要求の接続の本数は NFR2.11 の RoleConnectionUsageIT で決定的に確かめ、rolePoolLimit は上限に届いた証拠と合格の条件を数で持つ補助の確かめにした。
- 2026-10-05T17:29:59Z — 0 以下の ID を 400 ではなく 404 にした; group にそろえるためで、まとめの確認で機能設計の BR2.4 との差として受け入れられた。security-requirements.md の NFR1.10 に記録した。
- 2026-10-05T17:29:59Z — 書き出しの応答に X-Role-Transfer-Exceeds-Import-Limit のヘッダーを足した; Q4 A の「上限を超えたことを示す」の形。契約 C7 に足す互換の変更として tech-stack-decisions.md の引き継ぎに書いた。
- 2026-10-05T17:29:59Z — import の判定の順で DSL の有無を指紋より先に置いた; 機能設計の再レビューの R-12 の手当てで、BR9.10 の書き方との差として NFR3.8 に記録した。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-05T17:29:59Z — k6 の判定は1回の繰り返しに要求1つの場面の iteration_duration で行い、トークンは setup() で取って場面を 3 分にした; 時計のずれに強い代わりに、操作が2つ以上の場面は op のタグが iteration_duration に付くかを台本で確かめる必要がある（group のレビューの R-01 を先に避けた）。
- 2026-10-05T17:29:59Z — import は 10 MiB・確かめ 15 秒・適用 30 秒とした（Q4 A）; 往復できる範囲が広い代わりに、適用の間は同じロールの操作が ROLE_BUSY になる。時間は NFR 設計の捨ての試しで先に測る。
- 2026-10-05T17:29:59Z — resolve は祖先の行だけを読み、たくさんの対象は snapshotFor を1回とした（Q2 A）; 契約 C5 の口の形を変えずに重さを抑える代わりに、呼ぶ側の使い分けを口の説明で守らせる必要がある。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-05T17:29:59Z — 10 MiB の確かめ 15 秒・適用 30 秒と、snapshotFor 300 ミリ秒は根拠の測定が無い目標; NFR 設計の捨ての試し（NFR3.7）で先に測り、届かなければ上限を下げる案を依頼者に諮る。
- 2026-10-05T17:29:59Z — rolePoolLimit の 20 VU は待ちが起きることだけを期待にした; 尽きて 500 が出るかは確率で決まるため合格の条件にせず、件数を記録する。
- 2026-10-05T17:29:59Z — exec.vu.metrics.tags の op のタグが iteration_duration に付くかは未確かめ; 台本を書く B4 で確かめ、付かなければ操作ごとに場面を分ける。
