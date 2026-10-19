<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-19T07:00:00Z — B1（U5）が Bolt の計画の1番目から3番目（U1・U4 の後）に移る。計画の理由は「CI を安定させる（不安定なテスト2件が後の Bolt の統合の後の CI で落ちうる）」だったため、B2・B3 の統合の後の CI で2件が落ちる危険が残る。依頼者の決定（今の順のまま）で進め、落ちたら team.md の不安定なテストの決まりで扱う。U1 の code-summary.md にも Bolt の計画との差として書く。
- 2026-10-19T06:20:00Z — コード生成は単位ごとに U1→U4→U5→U2→U3 の順で進む（単位の依存の順。各単位のコード生成を終えてから次へ）。Bolt の計画の B7（U4 の後半、axe-core 4.14 の引き上げと依頼（5）の取り込み）は U3 の後に置く必要があるため、依頼者の決定（A）で U3 のコード生成の計画の最後の Bolt（B5→B6→B7）に入れ、U4 のコード生成は B3 だけにする。U4 の B7 の分の ID は U3 へ Deferred にする。B1（U5）は U1・U4 の後になる。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-20T12:00:00Z — U2（B4）のコードのレビュー（READY、Minor 3）は、依頼者の決定で3件とも統合の前に直す: R-01（03-writer-account.sh のライセンスヘッダーの最後の行）、R-02（作成でドライバーに渡す主キーのカラム名の引用。大文字小文字の混じる名前・予約語の結合テストで確かめる）、R-03（生成された鍵を文字で読めないときは MySQL 系と同じく鍵を空にして 201）。直した後に verify と E2E を流し直す。
- 2026-10-20T09:00:00Z — U2（B4）の生成で依頼者が決めたこと: G1: A（秒の端数の精度は型の名前の (n) → DSL の精度 → DB の種類の既定）、G5: A（違反の後の確かめの失敗に誤りを付けず 409 だけ。BR6.4 との差）、G13: A（MySQL・MariaDB の整数の符号なしは DSL から分からず、範囲の外は 409。既知の制約）、G14: A（Connector/J の 22001 を形の誤りに含める）、G15: A（U1 の MySQL の件数の問い合わせの時間切れの後に止まった接続がプールに戻り次の1回も 503 になる。既知の制約として後の Intent に持ち越す）、G16: A（Performance Validation の WARN の件数の判定から bizWritePoolLimit の想定どおりの 409 の WARN を code で絞って外し、外した件数を並べて記録する）。G6〜G12 は承認の場で確かめる。
- 2026-10-19T15:30:00Z — U5（B1）のコードのレビュー（READY、Minor 4）の扱い: 依頼者の決定で R-02（自然の順も 409 を失敗にする）・R-03（dsl-mixed-memory.sh の切れた行と trap）を統合の前に直し、R-01（code-summary の「保証する」を弱める）も直す。R-04（K6_IMAGE の digest と H2 の jar の取り方の2か所書き）は持ち越す。
- 2026-10-19T14:30:00Z — B2（ea23bf3）の CI の1回目に PatternCheckerTest、0427d45 の CI の1回目に PasswordChangePage.test.tsx が落ち、どちらも2回目の試行で通った。依頼者の決定で、team.md の「手元で再現しないもの」として記録し、同じテストが二度落ちたら直す。U5 の生成の N-2〜N-4 と bash での実行も依頼者が受け入れた。
- 2026-10-19T12:30:00Z — U4（B3）のコードのレビュー（READY、Minor 4）の扱い: R-01（L5 は incomplete を合格に数えないため、C4 の確かめは2語の氏名の場面に限る）・R-02（行の値を data-testid に入れない）は U3 の計画（B5〜B7）の前提として引き継ぐ。R-03（測りの見本の生成値を json の探索に入れるか）は U3 の計画で扱う。R-04（記録のコミット）は統合の前に行う。
- 2026-10-19T08:00:00Z — U1 のコードのレビュー（READY、Major 1・Minor 7）の扱いの依頼者の決定: R-04（G7、主キーが NONE のテーブルでも主キーで並べ、深いページの先読みに使う）はこのままとし、既知の制約として記録する（A）。R-01（traceability の負荷の目標 NFR1.1〜1.3・2.4・3.2 を OK でなく Performance Validation への Deferred にする）は Code Generation の承認の場で直す。R-03（MySQL・MariaDB の SET time_zone を戻さない）は U2 と同じく接続を作るときに付ける形に切り替え、U2（B4）の計画で直す。そのほかの Minor（R-02・R-05〜R-08）は記録して持ち越す。B2 は今の形で統合する。
