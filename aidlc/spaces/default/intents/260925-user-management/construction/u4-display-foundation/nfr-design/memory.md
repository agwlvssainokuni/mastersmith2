<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

- 2026-09-27T10:13:06Z — 質問は3問（ログインの画面の幅 375px を B4 に入れるか・axe の合否の規則・トークンを付けないパスの一覧の形）にし、ほかは要点 11 件として要約で確かめる形にした; 最初の描画のゲート・時間の測り方・フォントは承認済みの NFR 要件と機能設計でほぼ決まっていると読んだ。既存の E2E への非干渉（R-01）は、Playwright の既定のテストごとの新しいコンテキストとログインしない検査で作りが1つに決まるため、要点 6 にした。
- 2026-09-27T10:13:06Z — U5〜U7 の共通の決定（幅 375px の6組は B5 で U5〜U7 の画面に当てる）はログインの画面に触れていないと読み、Q1 にした; NFR 要件の承認の場の R-02 がログインの画面の狭い幅の確かめ方を B4 の計画に求めているため。
- 2026-09-27T10:13:06Z — ApiClient の一覧の名前は機能設計の 6.1 がコード生成で決めるとしていたが、Q3 として前倒しで尋ねる形にした; トークンを付けないパスの判定の置き場はセキュリティの設計に当たり、今の名前は apiClient.ts の中だけで使われていて影響が閉じるため。C で「コード生成で決める」も選べるようにした。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

- 2026-09-27T10:34:50Z — Q2 A の「scrollable-region-focusable が無ければ名前で足す」は当たらないとした; 手元の axe-core 4.13.0 の getRules() で scrollable-region-focusable が wcag2a、color-contrast が wcag2aa を持つと確かめたため。代わりに、版を上げてタグが変わっても抜けないよう、2つの規則が結果にあることを検査の中で確かめる設計にした（security-design.md の 6.4）。
- 2026-09-27T10:34:50Z — NFR6.4（Serif を読まない）と NFR9.4（CSP の違反が無い）の確かめを、記録だけでなく失敗の条件にした; 時間と違って不安定ではないため。NFR 要件の記述（記録する）への追加として performance-design.md の上流との差に書いた。
- 2026-09-27T10:34:50Z — 質問ファイルの要点 4 の「font-display: swap と unicode-range で読むときだけ読む」の unicode-range を、成果物では使わなかった; 手元の @fontsource/noto-sans-jp 5.3.0 の japanese-400.css は @font-face が1つで unicode-range を持たなかった。読むときだけ読むのは 'Noto Serif JP' を使う文字を描くときに限るためで、結論は変わらない。Serif も同じ形の見込みとし、コード生成で確かめる形で performance-design.md の5節に書いた。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

- 2026-09-27T10:13:06Z — axe-core は Node の側で本体を読み、page.evaluate で評価する案にした; addScriptTag の中身の埋め込みは CSP の script-src 'self' で止まり、bypassCSP はアプリの条件とずれて同じファイルで CSP の違反を確かめられなくなるため。同じオリジンの道への差し替えは、検査のためだけの道が増えるため選ばなかった。
- 2026-09-27T10:13:06Z — ブランドカラーの組は /api/appearance の答えの差し替え、テーマと文字の大きさは U4 の鍵の初めのスクリプトで切り替える案にした; html の属性を直接書き換えるより本物の読み込みと当て方の道（D5・W1・W3）を通せるため。代わりにサーバーの設定の値そのものからの当て方は、この検査では通らない。
- 2026-09-27T10:13:06Z — 最初の画面の時間はテストの側の時計（goto の直前から見出しが見えるまで）で測る案にした; Playwright の待ちの間隔を含み長めに出るが、長めの側で判定するほうが目標を緩めない決まりに合うため。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->

- 2026-09-27T10:34:50Z — page.evaluate で axe-core の本体を評価しても CSP に止められないことは、まだ実際には確かめていない; @axe-core/playwright と同じ読み込み方で、DevTools の手順での評価はページの CSP の対象外という見込みに頼る。コード生成で、CSP の見出しの付いた WAR の画面で評価でき、違反の知らせが出ないことを確かめる。
- 2026-09-27T10:34:50Z — 既存の 010 の collectProblems と同じ見方の手伝いを、新しい検査の手伝いのモジュールに複写する設計にした; 既存のファイルを変えない方針を優先したため、同じ考えの手伝いが2か所になる。まとめるかは、コード生成の計画で依頼者に確かめる余地がある。
