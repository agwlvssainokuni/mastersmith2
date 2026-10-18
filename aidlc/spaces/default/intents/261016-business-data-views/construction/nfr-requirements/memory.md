<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-18T10:20:00Z — 単位ごとの質問の案を並べて作り、先に終わった単位の答え（U1 の上限・場面の名前・ID の割り振り）を U2・U3 の案と依頼に先に反映した。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-18T10:20:00Z — U4・U5 は単位の中で独自に NFR4.5〜・NFR7.1〜 を振り、U1〜U3 と番号が重なった。U3 は NFR7 を 7.8 から振って重なりを避け、扱いを承認の場に回した。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-18T10:20:00Z — 単位ごとのレビューの Major（計8件）はその場で直さず承認の場に持ち越した。多くは NFR 設計で決める作りの論点のため。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-18T10:30:00Z — 承認の場（Approve）の決定: U1 R-01 は「通信の待ち（25 秒）以内に 503」に読み替え NFR 設計で実測、U1 R-02 は page の上限と桁あふれしない計算を NFR 設計で決める、U2 R-01 は切り詰めを呼び出し側（businessdata）で行い AuditEvent は変えない、U2 R-02 は確定・巻き戻しの待ちを含めて NFR 設計で計算し直す、U3 R-01 は見本 DB の管理の資格情報をプロセスの環境変数で渡し SECRET_ENV_NAMES に足す（Infrastructure Design）、U4 R-01 は passes の対象を B3 の試しに追従させ Dropdown のトリガー・作業ロールの項目を候補に足す、U4 R-02 は見分けの鍵を NFR 設計で決める、U5 R-01 は台本で PERF_DB_URL の形を確かめる（コード生成の計画）。ID の重なりは単位の中の ID として扱い、単位の接頭辞を付けて指す。
- 2026-10-18T10:20:00Z — 途中で利用の上限に達し、U3 の文書の書き込みが止まった。上限が戻った後に同じ担当に続きを頼んで書き終えた。
