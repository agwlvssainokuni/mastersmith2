<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-25T16:05:00Z — Q3 A の「Unicode の空白の文字」を White_Space の性質と読み、前後を除いてから Cc・Cf を判定する順にした; 前後の改行・タブは除かれて通り、内側にあるときだけ Cc として拒否される。U+00A0 なども前後なら除く。
- 2026-09-25T16:05:00Z — 今のパスワードが空なら VALIDATION_FAILED、72 バイトを超えるなら照合せず不一致（PASSWORD_CURRENT_MISMATCH）とした; 質問の要点 7 の順序（検証→照合→保存）に、既存のログインの照合（72 バイト超えはダミーで不一致）の扱いを当てはめた。
- 2026-09-25T16:05:00Z — traceability の upstream_ids に US4.1・US5.1 の AC 22 件と、U2 が受け持つ共通の決まり CR1.1・CR3・CR4・CR5 を並べた; US3.2 の AC（AC3.2.9・AC3.2.11 など）は U3 が主の単位のため入れず、作成の決まり BR5.x は reverse に理由を書いた。
- 2026-09-25T16:05:00Z — 言語の既定（Accept-Language が無い・当たらないときは ja）と、/api/me/ の 401 がアクセスの拒否として監査されないことをコードで確かめて書いた; AcceptLanguageResolver と AdminAuthenticationEntryPoint（管理者のみのパスだけ知らせる）による。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-25T16:05:00Z — 確認済みの要点に無い決まり BR3.4（保存は自分の列だけを書き換え、相手の列を古い値で上書きしない）を足した; 既存の User は全列を持つエンティティで、プリファレンスの保存とパスワードの変更が同時に起きると、先に読んだ古いハッシュで新しいハッシュを上書きしうるため。要点 6 の「後勝ち」は同じ4列どうしに限ると読んだ。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-25T16:05:00Z — Q2 B で Cf を拒否するため、ゼロ幅の接合子（U+200D）を使う絵文字の組み合わせ（家族の絵文字など）と異体字の選択子の一部を含む氏名も拒否される; 表示の向きの上書きを防ぐ代わりに受け入れた。異体字の選択子（U+FE0F）は Mn で Cf ではないため通る。
- 2026-09-25T16:05:00Z — 利用者の作成（BR5.1）は決まりに合わない値を結果の型ではなく想定外の誤りにした; 入力の誤りは呼び出し元（U3）が同じ関数で先に返す前提で、作成の結果の型は C2 のとおり Created・EmailAlreadyUsed の2つに保つ。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-25T16:05:00Z — 対になっていないサロゲート（Cs）を含む氏名の扱いを決めていない; Q2 B は Cc・Cf だけを拒否とし、ほかは許すとしたため BR に入れなかった。JSON の読み取りで弾かれるかをコード生成で確かめ、通るなら依頼者に確かめる。
- 2026-09-25T16:05:00Z — 今のパスワードの誤りが続いたときの制限は持たない（BR4.5）; NFR 要件の段で決め、入れるときは BR4.5 を広げる。
