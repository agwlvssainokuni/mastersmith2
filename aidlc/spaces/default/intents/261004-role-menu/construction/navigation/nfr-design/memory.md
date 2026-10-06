<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-06T13:30:00Z — navigation の R-08 は role の NFR 設計の Q2 A で決着したと読んだ; role の引き継ぎが「対象が1つなら resolve でよい」に改められ、置き場の resolve 1回と合う。security-design.md 2節と logical-components.md 4節に経緯を書いた。
- 2026-10-06T13:30:00Z — 捨ての試し N1 で印字できる文字・制御文字のどれも拒否されなかったため、Q3 A の「base64url への切り替えを諮る」条件には当たらないと読んだ; /api の下でも 23 種すべて 401（要求の検査の 400 は 0 件）で、StrictHttpFirewall は引数の値を拒否しない。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-06T13:30:00Z — NFR1.12 の測り方を承認済みの要件（メニューの応答から期待を作る・4つの組）から、期待の表・7つの組に変えた（Q1 A）; 要件の書き方はメニューに出ない READ のテーブルを 403 と誤るため。承認済みの文書は書き換えず logical-components.md 4節に差を書いた。
- 2026-10-06T13:30:00Z — NFR2.6 の測り方を「解決の口の途中で止めて HikariCP の使用中の数を読む」から、テストだけの DataSource の包みでスレッドごとの最大を数える形に変えた（Q2 A）; role の RoleBarrier に読み取りの途中の点が無く、瞬間値はほかの要求の接続も数えるため。
- 2026-10-06T15:00:00Z — 承認の場の Request Changes（推奨の案のとおり直す）で R-01・R-02 を直した; R-01 は接続の数えに測る直前の reset() と要求の頭 X-Test-Measure-Id による印ごとの数えを足し、ログインで2本を使った同じスレッドの最大が残らない形にした。R-02 は正しくない % の並びで値が ERROR に出る件を依頼者の決定（案 1）で受け入れた制約とし、NFR1.8 を「部分」にした。認証の前か後かは試しで /api に送っていないため未確認と書いた。
- 2026-10-06T13:30:00Z — 空の引数を画面入出力の層で明示して確かめることにした; 試し N2-f で Spring の結び付けが table= の空を拒否せず 200 になったため。BR5.1 の中身は変えない。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-06T13:30:00Z — 待ち合わせの口 NavigationBarrier を本番のコードに何もしない部品として置いた（Q2 A）; role の承認済みの設計に手を入れずに DSL の差し替えの重なりを決定的に作れる代わりに、本番に使わない口が1つ増え、境界テストとカバレッジの対象になる。
- 2026-10-06T13:30:00Z — 一致のテストの組を7つにし、口の値の網羅は role の EffectivePermissionConsistencyIT に任せた; HTTP を通した側で上書き・カラムだけ・DSL なしまで確かめる代わりに、悪い側の組の用意は SQL で直接入れて時間を抑える。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-06T13:30:00Z — 正しくない % の並び（a%zz など）の引数は 500 になり、ERROR のログの例外の文に引数の名前と値が入る（試し N2-d）; アプリ全体の今の振る舞いで、NFR1.8 と細工した要求で食い違う。受け入れた制約にするか、共通の誤りの変換で 400 にして文を出さないかを承認の場で依頼者に諮る（observability-design.md 3節）。
