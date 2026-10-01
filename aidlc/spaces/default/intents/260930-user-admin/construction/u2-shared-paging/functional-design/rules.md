# Rules — U2 ページ送りの共通化（u2-shared-paging）

出典の略号: FR は `aidlc/spaces/default/intents/260930-user-admin/inception/requirements-analysis/requirements.md`、AC と「差7」は `aidlc/spaces/default/intents/260930-user-admin/inception/user-stories/stories.md`、C2・C3・C5 は `aidlc/spaces/default/intents/260930-user-admin/inception/contract-design/contract-summary.md`、ADR-004 は `aidlc/spaces/default/intents/260930-user-admin/inception/domain-design/decisions.md`、「要点 n」はこの段の `functional-design-questions.md` の設計の要点。既存のコードは `backend/src/main/java/cherry/mastersmith/invitation/domain/InvitationPaging.java`・`invitation/service/InvitationService.java`・`frontend/src/features/invitation/paging.ts` と使い手で確かめた。

決まりは3つの群に分ける。BR1 はサーバーの計算（部品 Paging、置き場 `common.paging`）、BR2 は画面の計算（部品 UiPaging、置き場 `frontend/src/shared/paging/`）、BR3 は Paging・UiPaging を使う呼び出し元（招待の Invitation と InvitationUi、U3 の UserAdministration、U5 の UserAdminUi）が守る決まりである。BR1・BR2 の計算は、移す前の `InvitationPaging`・`paging.ts` の振る舞いと同じで、変えない（C2・C5）。

```yaml
rules:
  # --- BR1 サーバーの計算（Paging、C2）
  - id: BR1.1
    statement: 管理の一覧の1ページの件数は 20 とする
    category: constraint
    applies_to: Paging の定数 PAGE_SIZE（招待の一覧・利用者の一覧）
    trigger: 一覧のページを読む・応答を作るとき
    logic: "PAGE_SIZE は 20 で固定する。要求から受け取らず、呼び出し元ごとに変えない。応答の size にもこの値を入れる（BR3.3）"
    violation: —（定数）
    source: FR1.3、C2 の constants、AC1.1.1、AC1.1.7
  - id: BR1.2
    statement: 要求の page の文字列を検証し、1 以上の整数だけをページの番号として受ける
    category: validation
    applies_to: Paging.parsePage（入力は文字列または無し、結果は整数か空）
    trigger: 一覧の要求を受けたとき（呼び出し元が最初に呼ぶ、BR3.1）
    logic: "IF page の指定が無い（null）THEN 1。ELSE IF 文字列が半角の数字 0〜9 だけの 1〜9 文字で、その値が 1 以上 THEN その値。ELSE 空（空の文字列・前後の空白・符号（+ と -）・小数・数字でない文字・10 文字以上の数字（桁あふれ）・0 を含む）。9 文字までに限るため、結果は整数の範囲に必ず収まる"
    violation: 結果が空になる。呼び出し元が入力の誤りにする（BR3.1）
    source: FR1.5、AC1.1.5、差7、C2 の parsePage
  - id: BR1.3
    statement: ページの番号から、そのページの読み始めの位置を (page − 1) × 20 で求める
    category: calculation
    applies_to: Paging.offsetOf（入力は整数のページの番号、結果は 0 から数える位置）
    trigger: 一覧の行を読む前（BR3.2・BR3.4）
    logic: "IF page が 1 以上 THEN (page − 1) × 20 を、桁あふれしない広い整数で返す（page が整数の最大値でもあふれない）。IF page が 1 未満 THEN 呼び出し元の誤り（プログラムの誤り）として例外にする。要求の入力は BR1.2 を通してから渡すため、正しい呼び出しでは起きない"
    violation: page が 1 未満なら IllegalArgumentException（応答の 400 にはしない。BR1.5）
    source: FR1.3、AC1.1.7、C2 の offsetOf
  - id: BR1.4
    statement: 一覧の中の位置（1 から数える）から、その行が載るページの番号を (position − 1) ÷ 20 + 1 で求める
    category: calculation
    applies_to: Paging.pageOf（入力は 1 以上の位置、結果はページの番号）
    trigger: 呼び出し元が「その行が何ページ目に載るか」を応答に入れるとき（今は招待の、同じメールアドレスの招待中の招待があるときの応答だけ）
    logic: "IF position が 1 以上 THEN (position − 1) を 20 で割った商（切り捨て）に 1 を足す（位置 ÷ 20 の切り上げと同じ）。結果が整数の範囲を超えるときは算術の例外。IF position が 1 未満 THEN 呼び出し元の誤りとして例外にする。位置 1〜20 は 1、21〜40 は 2、41 は 3"
    violation: position が 1 未満なら IllegalArgumentException。整数の範囲を超えれば算術の例外（どちらもプログラムの誤り）
    source: C2 の pageOf（既存の招待の口をそのまま移す）
  - id: BR1.5
    statement: Paging は、DB・時刻・設定・ほかの機能に依存しない純粋な関数として作り、口の名前・引数・結果・例外を移す前と同じに保つ
    category: policy
    applies_to: Paging の全体（置き場 cherry.mastersmith.common.paging）
    trigger: 作るとき・使うとき
    logic: "同じ入力にはいつも同じ結果を返し、状態を持たない。依存するのは JDK だけとし、invitation・useradmin などの機能に依存しない（逆向きの依存も作らない）。口は PAGE_SIZE・parsePage・pageOf・offsetOf の4つで、移す前の InvitationPaging と名前・引数の型・結果の型・例外の種類を変えない。InvitationPaging は残さずに消す（中で Paging を呼ぶ形でも残さない）。要求の入力の誤りは例外ではなく BR1.2 の空で返し、例外は呼び出し元のプログラムの誤りだけに使う。扱う値は数と page の文字列だけで、個人に関する値を扱わない。Spring の部品にせず、層の名前（web・service・domain・repository）に当たらないパッケージに置く"
    violation: —（作りの決まり。性質ベースのテストと境界の検査で確かめる）
    source: C2、ADR-004、要点 1・9・10
  - id: BR1.6
    statement: Paging の説明文は、管理の一覧に共通の書き方にする
    category: policy
    applies_to: Paging のクラスと口の説明文
    trigger: 移すとき
    logic: "移す前の説明文のうち、招待に限った書き方（「招待の一覧の」「招待が載るページ」と、招待の段の決まりの番号への参照）を、管理の一覧に共通の書き方と、この単位の決まりの番号（BR1.x）に直す。振る舞いの説明（受ける値・返す値・例外）は変えない"
    violation: —
    source: 要点 8

  # --- BR2 画面の計算（UiPaging、C5）
  - id: BR2.1
    statement: 画面の1ページの件数は 20 とし、ページの数は全体の件数 ÷ 20 の切り上げ（全体の件数が 0 以下なら 0）とする
    category: calculation
    applies_to: UiPaging の PAGE_SIZE と pageCount（入力は全体の件数、結果はページの数）
    trigger: 一覧の応答を受けて、ページ送りの表示を作るとき
    logic: "PAGE_SIZE は 20（BR1.1 と同じ値）。IF total が 0 以下 THEN 0。ELSE total ÷ 20 の切り上げ。total が 20 なら 1、21 なら 2、43 なら 3"
    violation: —（計算）
    source: FR1.3、AC1.1.1、AC1.1.7、C5 の pageCount
  - id: BR2.2
    statement: 「n〜m 件目」の表示の元の値を、ページの番号と全体の件数から求める
    category: calculation
    applies_to: UiPaging の pageRange（入力はページの番号と全体の件数、結果は from と to）
    trigger: 一覧の応答を受けて、件数の範囲を表示するとき
    logic: "IF total が 0 以下 THEN from・to とも 0。ELSE from は (page − 1) × 20 + 1、to は page × 20 と total の小さい方。全体 43 件の2ページ目は 21〜40、3ページ目は 41〜43。全体 21 件の2ページ目は 21〜21（1件）"
    violation: —（計算）
    source: AC1.1.1、AC1.1.7、C5 の pageRange
  - id: BR2.3
    statement: 読んだページが空で全体の件数が 1 以上なら、最後のページへ移るよう知らせる
    category: calculation
    applies_to: UiPaging の correctedPage（入力は読んだページ・全体の件数・応答の行の数、結果は移る先のページか無し）
    trigger: 一覧の応答を受けたとき（ほかの操作の後で行が減り、今のページが最後のページより後になった場合など）
    logic: "IF 行の数が 1 以上、または total が 0 以下 THEN 無し（補正しない）。ELSE 最後のページ pageCount(total) を求め、IF それが読んだページと同じ THEN 無し（同じページを読み続けない）、ELSE 最後のページを返す"
    violation: —（計算）
    source: C5 の correctedPage と behaviour（利用者の管理の画面の操作の後の読み直し）
  - id: BR2.4
    statement: ページ送りのボタンが、読んだ先のページで押せなくなるかを求める
    category: calculation
    applies_to: UiPaging の pagerButtonDisabledAfter（入力は押したボタンの向き・読んだ先のページ・全体の件数、結果は真偽）
    trigger: ページ送りのボタンを押して、次のページの応答を受けたとき（押せなくなるときはフォーカスを移すのに使う）
    logic: "IF 向きが「前へ」THEN page が 1 以下で真。IF 向きが「次へ」THEN page が pageCount(total) 以上で真。それ以外は偽"
    violation: —（計算）
    source: C5 の pagerButtonDisabledAfter
  - id: BR2.5
    statement: UiPaging は、画面の部品・ブラウザ・API に触れない純粋な関数として作り、すべての export の名前・引数・結果を移す前と同じに保つ
    category: policy
    applies_to: UiPaging の全体（置き場 frontend/src/shared/paging/）
    trigger: 作るとき・使うとき
    logic: "React・window・API の呼び出しに触れず、同じ入力にいつも同じ結果を返す。export は PAGE_SIZE・PagerDirection・PageRange・pageCount・pageRange・correctedPage・pagerButtonDisabledAfter で、名前・引数・結果を移す前の paging.ts と変えない。移す前のファイルは残さない。置き場は src/shared/ の用途ごとの下位のフォルダーにそろえ、どの機能のフォルダーにも依存しない。説明文は BR1.6 と同じく管理の一覧に共通の書き方に直す。画面の文言（訳の鍵）は C5 の範囲の外で、この単位では移さない前提とする（移すかはコード生成の計画で決める）"
    violation: —（作りの決まり。性質ベースのテストと lint で確かめる）
    source: C5、ADR-004、要点 3・8

  # --- BR3 呼び出し元が守る決まり（Invitation・UserAdministration・InvitationUi・UserAdminUi）
  - id: BR3.1
    statement: 一覧の要求の page は、BR1.2 だけで検証し、空なら入力の誤りとして拒否する
    category: validation
    applies_to: 管理の一覧の API（招待の一覧、U3 の利用者の一覧）
    trigger: 一覧の要求を受けたとき（全体の件数を数える前）
    logic: "page は問い合わせの文字列のまま受け、BR1.2 に渡す。IF 結果が空 THEN 入力の誤り（VALIDATION_FAILED、400）として拒否し、全体の件数を数えず行も読まない。ページの番号の拒否は監査に残さない。呼び出し元で独自の検証（数への変換・範囲の確かめ）を重ねない"
    violation: 400 VALIDATION_FAILED（既存の共通の code を使う）
    source: FR1.5、AC1.1.5、C2、C3 の page の説明
  - id: BR3.2
    statement: 最後のページより後のページの番号は拒否せず、全体の件数つきの空の一覧を返す
    category: policy
    applies_to: 管理の一覧の API（招待の一覧、U3 の利用者の一覧）
    trigger: BR3.1 を通り、全体の件数 total を数えた後
    logic: "IF BR1.3 で求めた読み始めの位置が total 以上（total が 0 なら1ページ目を含む）THEN 行を読まずに、items を空、total を数えた値、page を要求の値のまま返す（200）。ELSE BR3.4 で行を読む。Paging にこの判定の口を足さず、呼び出し元が BR1.3 の結果で判定する"
    violation: —（拒否しない。200 の空の一覧）
    source: FR1.5、AC1.1.5、差7、C2 の behaviour
  - id: BR3.3
    statement: 一覧の応答には、行のほかに、ページの番号・1ページの件数・全体の件数を含める
    category: constraint
    applies_to: 管理の一覧の応答（招待の一覧、U3 の利用者の一覧）
    trigger: 一覧の応答を作るとき
    logic: "page は受けたページの番号（指定が無ければ 1）、size は BR1.1 の 20、total は条件に当たる全体の件数（検索があれば検索の結果の件数）。画面はこの3つと行の数だけで BR2.1〜BR2.4 を求める"
    violation: —
    source: FR1.3、AC1.1.1、C3 の AdminUserPage（page・size・total）
  - id: BR3.4
    statement: 行は、読み始めの位置から 20 件までを、ページをまたいで変わらない並びで読む
    category: constraint
    applies_to: 管理の一覧の行の読み出し（呼び出し元の DB の読み出し）
    trigger: BR3.2 で空の一覧でないと決まった後
    logic: "BR1.3 の読み始めの位置から BR1.1 の 20 件までを読む。並びは呼び出し元が決め、同じ値の行の順を一意に決める（最後に ID で並べる）。そうすることで、どのページにも同じ行が二度出ず、抜ける行も無い。全体の件数と行は同じ読み取りのトランザクションの中で読む"
    violation: —
    source: FR1.3、AC1.1.7
  - id: BR3.5
    statement: 呼び出し元は、ページ送りの計算を自分で持たず、Paging・UiPaging を呼ぶ
    category: policy
    applies_to: 招待のサーバーと画面、U3・U5 の利用者の管理
    trigger: ページ送りの計算が要るとき
    logic: "1ページの件数・ページの番号の検証・読み始めの位置・位置が載るページ・ページの数・件数の範囲・ページの補正・ボタンの押せる押せないは、Paging（サーバー）と UiPaging（画面）だけで求め、同じ計算を機能の中に書かない。招待は移す前と同じ振る舞いのままで、招待の既存のテストが import の場所の変更だけで通ったままであることで確かめる"
    violation: —（作りの決まり）
    source: ADR-004、C2・C5、要点 2・4・5
```

## 決まりの一覧

| 群 | ID | 要点 |
|---|---|---|
| サーバーの計算（Paging） | BR1.1〜BR1.6 | 1ページ 20 件、page は 1〜9 桁の数字で 1 以上だけ（指定なしは 1、ほかは空）、読み始めは (page − 1) × 20、位置が載るページは (position − 1) ÷ 20 + 1、1 未満は呼び出し元の誤りの例外、JDK だけに依存する純粋な関数で口を変えず InvitationPaging を消す、説明文を共通の書き方に |
| 画面の計算（UiPaging） | BR2.1〜BR2.5 | ページの数は total ÷ 20 の切り上げ（0 以下は 0）、「n〜m 件目」の範囲、空のページで最後のページへの補正、ボタンの押せる押せない、画面の部品に触れない純粋な関数で export を変えず文言は移さない前提 |
| 呼び出し元が守る決まり | BR3.1〜BR3.5 | page は BR1.2 だけで検証し空なら 400 VALIDATION_FAILED（監査なし）、最後のページより後は 200 の空の一覧（total つき）、応答に page・size・total、20 件までを一意の並びで読む、計算を自分で持たない |
