<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-27T00:02:23Z — traceability の upstream_ids に US1.1・US3.2・US4.1 の受け入れ基準をすべて入れた; 検査の道具は unit-of-work-story-map.md で U4 と結び付いたストーリーの AC をすべて求めるため、44 件を並べた。U4 が関わらないものは N/A、U5〜U7 の画面で確かめるものは Deferred とした。共通の決まりは CR1.1〜CR1.5・CR2・CR6・CR6.6 を足した。
- 2026-09-27T00:02:23Z — clearPreview は言語の見せ方もやめる、と読んだ; C9 は clearPreview をテーマ・文字の大きさの見せ方として書いているが、U6 が完了せずに画面を離れたときに招待の言語を残さないため、見せ方をすべてやめる口とした。U7 は言語の見せ方を置かないため影響しない。
- 2026-09-27T00:02:23Z — ログインの画面の言語の切り替えのために saveBrowserLanguage を U4 の中の口として足した; 設計の要点 6(d) の「言語だけを書き換え、ほかの2つは保存済みのまま」を saveBrowserDisplaySettings（3つをまとめて保存する）では表せないため。C9 への安全な追加として扱った（resolvedTheme・displayName・LANGUAGE_NAMES も同じ）。
- 2026-09-27T02:16:21Z — 承認の場の Request Changes で、N/A 30 件をすべて Deferred にした; 依頼者の決定（ほかの単位で確かめるものは Deferred、この単位に全く関わらないものだけ N/A）に沿って、各単位の traceability.json を読んで確かめたところ、30 件はどれも U2・U3・U5・U6・U7 のどれかが OK で持っていた。そのため N/A は 0 件になり、target には確かめる単位の名前と理由を書いた。
- 2026-09-27T02:16:21Z — make-you-chic-ui の更新（edb1f94 → 735ef04）で U4 が触れる API は変わっていないと判断した; 差分は Button・Dropdown・Modal・RadioGroup・Table だけで、ThemeProvider・useTheme・design-system-* の鍵・Alert は変わっていない。LoginLanguageSwitch は interaction-spec.md 6節のフォーカスと Enter・Space の決まりに合わせて Button の組のままとし、新しい RadioGroup（選択肢ごとの lang・legend）には切り替えなかった。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-27T00:02:23Z — OK の target を BR ではなく functional-spec.md の流れの番号（W1〜W12）と決まり（D1〜D14）にした; ui の単位は rules.md を作らないため、依頼どおりにした。段の定義と検査の道具は OK の target に rules.md の BRx.y を求めるため、OK の 16 件は invalid_targets、rules.md が無いことも理由として出る見込み。D は BR の番号の形を使わず、検査の道具が孤立した規則として拾わないようにした。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-27T00:02:23Z — 画面の値は描画の中で純粋な関数で決め、保存の後の値と見せ方はログイン状態に結び付けて持つ形にした; ログイン状態が変わった描画でそのまま文言が切り替わり、前の利用者の値が途中で出ない（AC4.1.5）。その代わりに状態の持ち方がやや込み入る。make-you-chic-ui の属性の反映が描画の後になる場合に備え、U4 が同じ時点で html の属性にも置く逃げ道を書き、どちらにするかはコード生成で確かめる。
- 2026-09-27T00:02:23Z — U4 が make-you-chic-ui に値を渡すのは、そのタブの画面の値が変わったときだけにした; ほかのタブの見た目の変化を打ち消すと、Q3 A の「ほかのタブへの映り方は make-you-chic-ui の今の動きのまま受け入れる」に反するため。
- 2026-09-27T02:16:21Z — 登録の完了の API（/api/registration/verify・/api/registration/complete）を ApiClient の公開の API のパスに足した（U4 R-01）; 既存のセキュリティの決まりは公開のパスでも付いたトークンを検証するため、ログインしたままのタブで開くと契約 C6 に無い 401 が起きうる。前の版で U6 に任せるとしていた未解決の点を、U4 が持つ一覧の問題としてこの単位で閉じた。判定はパスの完全一致のままとし、一覧の名前（AUTH_API_PATHS）の改め方はコード生成で決める。
- 2026-09-27T02:16:21Z — 見た目の設定のハングの影響範囲を W2 の5と 7節に明記した（U4 R-02）; 待ちに上限を置かない Q2 A は変えず、1つの API の障害が全画面の最初の描画を止めうること、依頼者が受け入れたこと、今のセッションの復元と同じ待ち方であることを記録した。
- 2026-09-27T02:16:21Z — 既存の @fontsource/noto-sans-jp（OFL-1.1、5.3.0、推移依存なし）の採用の理由を functional-spec.md 9.3 に後から記録した; 前の Intent の最初の Bolt で足されたが記録が無く、team.md のライセンスの決まりに当たるため。採用そのものは変えていない。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-27T00:02:23Z — ユーザーメニューの項目の登録（UserMenuItemRegistration.action）は引数の無い関数で、画面の移動の手段を持たない; U7 が「プリファレンス」「パスワードの変更」の画面へ移るには、登録に移動先を持たせる変更が要るかもしれない。U4 は登録の仕組みを変えない（設計の要点 14）ため、U7 の機能設計か承認の場で確かめる。
- 2026-09-27T00:02:23Z — ログインした人が登録の完了のリンクを開くと、/api/registration/ の要求にトークンが付く; 期限切れのトークンなら 401 のあとの更新で通る見込みだが、ApiClient の公開の API のパスに /api/registration/ を足すかは U4 の範囲の外として決めていない。U6 の機能設計で確かめる。
- 2026-09-27T00:02:23Z — 見た目の設定の待ちに上限を置かない（Q2 A）ため、応答が返らないと画面が出ないまま残る; 今のセッションの復元と同じ待ち方で、受け入れた決定として記録した。
- 2026-09-27T00:02:23Z — 既に使っている @fontsource/noto-sans-jp（OFL-1.1）の採用の理由の記録は、intents の文書には見当たらなかった; Noto Serif JP の記録（functional-spec.md 9.2）で同じライセンスと書いたが、sans の記録を足すかは依頼者に確かめる。
