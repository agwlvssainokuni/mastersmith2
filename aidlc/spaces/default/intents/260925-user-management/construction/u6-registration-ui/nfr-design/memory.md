<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

- 2026-09-27T10:50:06Z — 質問は2問（差し替えの答えと本物の応答の形の照合・400 の fieldErrors の出し方）にし、ほかは要点 11 件として要約で確かめる形にした; 検査の作りは U4 の NFR 設計、トークンの扱いは機能設計と NFR 要件でほぼ決まっていると読んだ。検査の回数と時間は承認の場で受け入れ済みのため、見込みの値を要点 4 に書くだけにした。
- 2026-09-27T10:50:06Z — 登録の完了の 400 に U3 が fieldErrors を載せることになった点を Q2 にした; 承認済みの機能設計の Q3 A は項目ごとの誤りの形が無いことを前提にしており、前提が変わったため。読む関数は U7 の features/preferences にあり、機能どうしは依存しないため置き場の選択が要る。
- 2026-09-27T10:50:06Z — E2E-1 の招待メールのリンクの取り出し方は、NFR 要件どおり infrastructure-design の持ち主のまま質問にしなかった; この段では、E2E-1 がその手段で取り出したリンクを使う前提だけを書いた。
- 2026-09-27T12:10:20Z — 質問の要点 5 の「window.location.hash から取り出す」を、成果物では機能設計の W2 のとおり React Router の場所の hash にした; 承認済みの機能設計がルーターの場所を読むと決めており、要点の書き方が不正確だったため。作りの結論（最初の描画で読み、確定の後に消す）は変わらない。
- 2026-09-27T12:10:20Z — StrictMode の二重の確かめは、機能設計の W4 の4（害は無く最後の答えだけで移す）のままとし、送らない印は足さなかった; 初稿で印を足しかけたが承認済みの設計と違う作りになるため取り下げた。
- 2026-09-27T12:10:20Z — 見本の型の食い違いは frontend の typecheck（tsconfig の include に e2e が入る）で verify の中で気づけると確かめた; E2E-1 の毎回の照合と合わせ、画面の側の型と本物の応答の両方からずれを見る。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

- 2026-09-27T10:50:06Z — E2E-1 の5回の計測で、アドレス欄の #token= と CSP の違反も確かめる（失敗の条件にする）案にした; NFR 要件では NFR1.3 は流れの1回、NFR9.5 は記録だけだが、U4 の NFR 設計が不安定でない確かめを失敗の条件にした前例に合わせた。要約で確かめ、成果物の上流との差に書く。
- 2026-09-27T12:10:20Z — 計測の5回で実際のブラウザの保存にトークンが無いことも確かめる（2.1 の (c)）を足した; 要約に無かった追加で、jsdom と実際のブラウザの保存の差を埋める読み取りだけの確かめのため。performance-design.md の PD-D3 に書き、承認の場で伝える。
- 2026-09-27T12:10:20Z — 機能設計の W2 の4（閲覧の履歴の同期ではフラグメントの無いアドレスだけが残る）を言い過ぎの見込みとして security-design.md の SD-D2 に書いた; 最初の移動のアドレスが閲覧の履歴に残るかは確かめていない。project.md の Change Control に従い、機能設計を直すかは承認の場で依頼者に確かめる。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

- 2026-09-27T10:50:06Z — Q1 の推奨は E2E-1 の中で本物の確かめの応答と見本の形を毎回照合する A にした; R-02 の「1回の照合」より強く、形のずれに e2eTest のたびに気づけるため。代わりに E2E-1 が見本のモジュールに依存し、流れのファイルと検査のファイルが見本を共有する。
- 2026-09-27T10:50:06Z — Q2 の推奨は機能設計の Q3 A のままの A にした; サーバーの 400 は画面の確かめをすり抜けたときに限られ、項目に結び付けると U7 の承認済みの置き場に手が入るため。代わりに CR6.1 の「最初の誤りの項目にフォーカス」はサーバーの 400 では満たさないまま（Q3 A で受け入れ済み）。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->

- 2026-09-27T10:50:06Z — 検査の時間の見込み（U6 の分 約 1〜2 分、e2eTest の検査の部分 約 3〜8 分）は実測していない; 1組あたり 1〜3 秒の見込みからの試算で、B5 のコード生成と Build and Test で実測して記録する。
- 2026-09-27T10:50:06Z — フラグメントを置き換えの移動で消しても、ブラウザの閲覧の履歴に最初のアドレス（トークンを含む）が残るかは確かめていない; ブラウザに依存するため残る危険として要点 5 に書き、トークンの1回だけ有効と有効期限（U3）で支える形にした。
