<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

- 2026-09-27T00:43:17Z — 質問は判断が分かれる4点（ログインしたまま開いたとき・フラグメントの消し方・サーバーの 400 の出し方・使えないリンクの文と導線）に絞った; 残りは上流（契約 C6・U3・U4 の W8・W9・refined-mockups）で決まっているため設計の要点 20 件と「決まっていること」に書いた。確かめの通信の失敗を「使えない」と見せないこと、選んだ軸だけを見せることはリードの判断として要点に置いた。
- 2026-09-27T00:43:17Z — 確かめの関数（パスワードの規則・氏名）の置き場を `frontend/src/shared/validation/` とし U7 と共用する案にした; U6 と U7 は同じ B5 で作るため、先に設計する U6 で置き場と形を決め、U7 の機能設計はそれを使う前提とした。
- 2026-09-27T01:01:00Z — Q2 B の `history.replaceState` を React Router の置き換えの移動（replace）で行うと読んだ; 直接 `history.replaceState` を呼ぶとルーターの場所の情報にフラグメントが残り食い違うため。履歴の項目を増やさない点は答えと同じで、テストでも MemoryRouter の最初の URL でリンクを与えられる。
- 2026-09-27T01:01:00Z — Q1 A の案内はトークンがあるときだけ出し、トークンが無い・空ならログアウトを求めずに「リンクが使えない」にした; 確かめるものが無いのにセッションを終わらせないため。答えの「リンクを確かめる前に」を、確かめる対象があるときと読んだ。
- 2026-09-27T01:01:00Z — 完了の時点の 404 で「リンクが使えない」に移ったときは見せ方（選んだ言語・テーマ・文字の大きさ）をそのまま残し、離れるときに clearPreview で戻すことにした; 移った瞬間に言語が変わると読んでいた人が戸惑うため。
- 2026-09-27T01:01:00Z — traceability の upstream_ids は US3.2 の AC3.2.1〜AC3.2.18 と、単位の対応表で U6 に結び付く CR1.1〜CR1.5・CR6・CR6.1〜CR6.9 の 33 件にした; U3 と U4 が U6 に Deferred にした AC3.2.1・AC3.2.16・AC3.2.17・AC3.2.18 はすべて OK で受けた。サーバーで確かめる AC は、画面の表示とつながるものを u3-invitation への Deferred、つながらないものを N/A とした。
- 2026-09-27T02:28:06Z — 承認の場の Request Changes で、網羅の記録の N/A 8 件をすべて Deferred にした; 行き先の単位の traceability.json を読み、AC3.2.8・AC3.2.12〜AC3.2.14 は u3-invitation が OK、CR1.1・CR1.5 は u4-display-foundation が OK、CR6.7・CR6.8 は u5-invitation-ui が OK で受けていたため。この単位に全く関わらないものは残らず、N/A は 0 件になった。
- 2026-09-27T02:28:06Z — 新しい版の Button の loading（aria-disabled でフォーカスを保つ）に合わせ、送信中は文字の入力の欄を disabled ではなく readOnly にし、ラジオは選んでも値を変えない形にした; disabled にするとフォーカスのあった要素が押せなくなった時点でフォーカスが本文へ移り、Button の変更の狙いと食い違うため。フックの側でも submitting の間の送信と選択を無視し、Enter キーでの二重の送信も防ぐ。

## Deviations
- 2026-09-27T02:28:06Z — 自前の RadioFieldset をやめ、make-you-chic-ui の新しい版（735ef04）の RadioGroup に legend と選択肢ごとの lang を渡す形に置き換えた; make-you-chic-ui 側に取り込まれたため design-system-mapping.md との差は無くなった。RadioGroup は aria-describedby を渡す口を持たず FormField に入れると label と legend が重なるため、言語の案内は legend の2行目に入れ（選ぶと画面の言語が変わることを選ぶ前に知らせる）、テーマと文字の大きさの案内は結び付けずに文字として置いた。固定先の更新はコード生成の B4 で行う。
- 2026-09-27T02:28:06Z — U6 R-01 を受け、完了の成功を Toast ではなくログインの画面の Alert（U4 の W9）で知らせる差を functional-spec.md の 10節と traceability.json の CR6.4 に記録した; 完了の直後に置き換えで画面を移るため、この画面の Toast は移動で消えるかログインの画面の案内と二重になる。失敗を role=alert で残す部分は CR6.4 のとおり。
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-27T01:01:00Z — `design-system-mapping.md` の RadioGroup を使わず、`frontend/src/shared/ui/` に RadioFieldset を作る設計にした; make-you-chic-ui の RadioGroup・Radio は選択肢の名前を文字列だけで受け、選択肢の文字に `lang` 属性を付けられず（CR6.6）、fieldset・legend の名前付けも持たないため（`vendor/make-you-chic-ui` のコードで確かめた）。make-you-chic-ui は変えず、差を functional-spec.md の 10節に記録した。
- 2026-09-27T01:01:00Z — 使えないリンクの文を画面イメージ（S2）ではなくストーリーの AC3.2.2 の趣旨にし「ログインの画面へ」のリンクを足した（Q4 B）; 承認済みの画面イメージは書き換えず、functional-spec.md の 10節に差を記録した。
- 2026-09-27T01:01:00Z — OK の target を BR ではなく functional-spec.md の流れの番号（W1〜W13）にした; ui の単位は rules.md を作らないため、依頼と U4 の形に合わせた。検査の道具は OK の target に rules.md の BRx.y を求めるため、OK の 21 件は invalid_targets として出る見込み。決まりは D1〜D12 とし、BR の形を使わない。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-27T01:01:00Z — 氏名の前後の空白の除去を JavaScript の標準の除去ではなく Unicode の White_Space の正規表現で行う形にした; 標準の除去は U+FEFF を除き U+0085 を除かず、U2 の BR1.1 と集合が違うため。画面とサーバーの判定がそろう代わりに、関数を自前で持ち性質ベースのテストで確かめる必要がある。
- 2026-09-27T01:01:00Z — サーバーの 400 のときはフォームの上の知らせにフォーカスを移し、通信の失敗・5xx のときは移さないことにした; 400 は入力を直す必要があり知らせから読み始めるほうがよく、送れない失敗はそのまま送り直せばよいため。

## Open questions
- 2026-09-27T00:43:17Z — 使えないリンクの文がストーリーの AC3.2.2 と画面イメージの S2 で食い違う; AC3.2.2 は「いちばん新しい招待メールのリンクを使うか」を含み、画面イメージは送り直しの依頼だけ。確定済みの成果物の食い違いのため Q4 で依頼者に確かめる（`project.md` の Change Control の決まり）。
- 2026-09-27T00:43:17Z — 既存の VALIDATION_FAILED は誤りの項目の一覧を持たず、契約 C6 も形を決めていない; U3 の BR4.1 の「項目ごと」と応答の形がつながっていないため、画面の扱いを Q3 で確かめる。B を選ぶと U3 の承認済みの設計と共通の変換への追加が要る。
- 2026-09-27T01:01:00Z — 確かめ・完了の API にトークンを付けないことは U4 の R-01 の直し（U4 の承認の場で行う予定）に頼る; この単位では ApiClient を変えないため、U4 の直しが入らないとログインしたままの経路以外でも期限切れのトークンが付きうる。U6 はログアウトの後に確かめる（W3）ため通常は通らないが、U4 の承認の結果を確かめる。
- 2026-09-27T01:01:00Z — `frontend/src/shared/ui/` の RadioFieldset を U7 も使う前提にした; U7 の機能設計で同じ部品を使うかを確かめる（プリファレンスの言語の選択肢にも lang 属性が要る）。
- 2026-09-27T02:28:06Z — U7 も RadioGroup に置き換えるため、案内の置き方（legend の中か、結び付けない文字か）を U6 とそろえるかを承認の場で確かめる; U7 の初めの版はテーマと文字の大きさの案内を aria-describedby で結び付ける形で、新しい RadioGroup にはその口が無い。Button の loading の変更が既存の画面のテスト（disabled を確かめるもの）に及ぶかは、固定先を更新する B4 で確かめる。
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
