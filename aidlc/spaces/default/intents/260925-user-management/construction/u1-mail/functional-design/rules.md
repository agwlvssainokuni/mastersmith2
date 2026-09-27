# Rules — U1 メールの描画と送信（u1-mail）

U1 の決まりの正本。値の形は `entities.md`、手順と状態は `functional-spec.md`。出典の Q1〜Q4 は `functional-design-questions.md` の答え（Q1: C・Q2: B・Q3: A・Q4: A）、「要点 n」は同じファイルの「設計の要点（案）」の番号。具体の時間切れの値・環境変数の名前・テスト用の SMTP の受け手の道具は、後の段（NFR 要件・コード生成）で決める。

```yaml
rules:
  # ---- BR1: 設定 ----
  - id: BR1.1
    statement: SMTP の設定（接続先・ポート・暗号化・資格情報・差出人・差出人の表示名）は環境変数（.env）だけから受け取る
    category: policy
    applies_to: SMTP の設定
    trigger: アプリの起動
    logic: "IF 設定の値が環境変数以外（画面・API・送信の依頼）から渡される THEN 使わない（受け取る口を作らない）。設定は起動のときに読み、動いている間は変えない"
    violation: 口を作らないことで起きない（構造で守る）
    source: FR2.5、要点 8
  - id: BR1.2
    statement: 接続先と差出人のメールアドレスの両方がそろっているときだけ「設定がある」とする。どちらも無ければ警告を出さずに「設定がない」とする
    category: validation
    applies_to: SMTP の設定
    trigger: アプリの起動
    logic: "IF 接続先または差出人のメールアドレスが無い THEN 「設定がない」とする。IF SMTP の項目が1つも無い THEN 警告を出さない（手元で送らない使い方を許す）。どちらの場合も起動は続ける"
    violation: 起動は止めない。送信は NOT_CONFIGURED（BR1.7）
    source: FR1.8、FR2.5、Q2
  - id: BR1.3
    statement: 設定の一部が欠けている・不正なときは、問題のある項目の名前だけを並べた WARN を1件出し、「設定がない」として起動を続ける
    category: validation
    applies_to: SMTP の設定
    trigger: アプリの起動
    logic: "IF SMTP の項目が1つ以上あり、かつ次のどれかに当たる THEN 項目の名前だけの WARN を1件出し「設定がない」とする: 接続先または差出人が無い、ポートが 1〜65535 の数でない、暗号化が NONE・STARTTLS・SMTPS のどれでもない、差出人が BR3.1 のメールアドレスの形に合わない、差出人または表示名に改行（CR・LF）を含む、資格情報のユーザー名とパスワードの片方だけがある"
    violation: 起動は止めない。WARN に設定の値（接続先・差出人・資格情報）を出さない
    source: FR1.8、FR2.5、FR2.6、NFR2
  - id: BR1.4
    statement: 資格情報を設定したのに暗号化が「なし」のときは送らず、「設定がない」と同じ扱いにする
    category: constraint
    applies_to: SMTP の設定
    trigger: アプリの起動
    logic: "IF 資格情報（ユーザー名とパスワード）があり、かつ暗号化が NONE THEN 「設定がない」とし、項目の名前（資格情報と暗号化）だけの WARN を1件出す（資格情報を平文の経路に流さない）"
    violation: 送信は NOT_CONFIGURED（BR1.7）
    source: Q3、FR2.5
  - id: BR1.5
    statement: 暗号化は NONE・STARTTLS（必須）・SMTPS の3つから選び、既定は NONE とする。ポートを省いたときは方式ごとの標準の番号を使う
    category: policy
    applies_to: SMTP の接続
    trigger: 送信のたび
    logic: "IF 暗号化が STARTTLS THEN 受け手が STARTTLS を受け付けない・暗号化の確立に失敗したときは暗号化なしで続けず CONNECTION_FAILED とする。IF SMTPS THEN 接続の始めから暗号化する。IF 暗号化の項目が無い THEN NONE（手元の受け手向け）。IF ポートが無い THEN NONE は 25、STARTTLS は 587、SMTPS は 465"
    violation: 暗号化できないときは送らない（CONNECTION_FAILED）
    source: Q3、NFR11
  - id: BR1.6
    statement: 差出人は環境変数の差出人のメールアドレスと表示名で決め、表示名が無ければ「MasterSmith」とする
    category: policy
    applies_to: 送るメールの差出人
    trigger: 送信のたび
    logic: "IF 表示名が設定されている THEN その表示名と差出人のメールアドレスを差出人にする。IF 無い THEN 表示名を MasterSmith にする"
    violation: —
    source: Q2
  - id: BR1.7
    statement: 「設定があるか」は真偽だけを返し、設定の値を返さない。「設定がない」ときの送信は SMTP に接続せずに NOT_CONFIGURED を返す
    category: policy
    applies_to: isConfigured と send
    trigger: isConfigured の呼び出し、送信の依頼
    logic: "IF isConfigured を呼ぶ THEN 起動のときに決めた「設定がある・ない」を真偽だけで返す（接続先・差出人・資格情報の値や、ない理由の項目名は返さない）。IF 「設定がない」ときに send を呼ぶ THEN 依頼を確かめず・描かず・接続せずに FAILED（NOT_CONFIGURED）を返す"
    violation: —
    source: FR1.8、FR2.5、契約 C1、要点 8
  - id: BR1.8
    statement: メールの部品のデバッグ出力（mail.debug）は常に無効とし、設定で有効にする口も作らない
    category: constraint
    applies_to: SMTP の送信の部品
    trigger: アプリの起動
    logic: "IF 送信の部品を用意する THEN デバッグ出力を無効に固定する。環境変数・設定ファイルから有効にできる項目を持たない"
    violation: 口を作らないことで起きない（構造で守る）
    source: NFR2、要点 10

  # ---- BR2: テンプレートの置き場・一覧・検査 ----
  - id: BR2.1
    statement: テンプレートはバックエンドのリソースの mail/templates/<templateId>_<language>.html に1つの階層で置く
    category: constraint
    applies_to: テンプレートの置き場
    trigger: テンプレートを足すとき、アプリの起動
    logic: "IF テンプレートを置く THEN ファイル名を <templateId>_<language>.html にする。templateId は英小文字で始まり英小文字・数字・ハイフンだけ（下線を含まない）、language は ja か en。例: mail/templates/invitation_ja.html"
    violation: 名前の決まりに合わないファイルがあれば起動を止める（BR2.3）
    source: Q1、FR2.1、契約 C1・C10 の Open question
  - id: BR2.2
    statement: U1 はテンプレートの一覧（templateId と差し込みの名前の集合）を持ち、テンプレートを足す単位は一覧に行を足す
    category: policy
    applies_to: テンプレートの一覧（MailTemplateDefinition）
    trigger: テンプレートを足すとき
    logic: "IF 新しいテンプレートを足す THEN 置き場に ja・en のファイルを置き、一覧に templateId と受け取る差し込みの名前を足す。今回は U3 が invitation を足し、差し込みは registrationUrl（招待の URL）と validityHours（招待の有効期限の長さの時間の数。U3 が設定から作る正の整数を10進の文字列で渡す）の2つとする。文面は ja が「このリンクは {{validityHours}} 時間有効です」、en が同じ意味の文で、どちらもエスケープされる差し込み（BR2.4）で入れる（文面の中身は U3 の決まり）。U1 は validityHours を他の差し込みと同じ文字列として扱い、正の整数かの確かめは U3 が持つ"
    violation: 一覧とファイルが合わなければ起動を止める（BR2.3）。名前の食い違いはテストで見つける（BR2.6）
    source: Q4、契約 C10、要点 14、承認の場の Request Changes（2026-09-27、U3 の R-02 に伴う validityHours の追加）
  - id: BR2.3
    statement: アプリの起動のときにすべてのテンプレートを読んで描ける形に準備し、欠け・壊れ・置き場の誤りがあれば起動を止める
    category: validation
    applies_to: テンプレートの置き場と一覧
    trigger: アプリの起動
    logic: "IF 一覧の templateId に ja か en のファイルが無い、またはファイルの形が Mustache として壊れていて準備できない、または置き場に BR2.1 の名前に合わないファイルや一覧に無い templateId のファイルがある THEN 起動を止める（テンプレートは WAR に入るもので、設定の不足とは違うため）。止めるときのログは templateId・language・原因の種類だけ"
    violation: 起動の失敗
    source: Q1、FR2.1、NFR8
  - id: BR2.4
    statement: 差し込む値はエスケープされる差し込み（二重の波かっこ）だけで入れ、属性の値に差し込む箇所は二重引用符で囲む
    category: constraint
    applies_to: すべてのテンプレート（本文と title）
    trigger: テンプレートを書くとき、ビルドの検査（テスト）
    logic: "IF テンプレートに差し込みを書く THEN 二重の波かっこだけを使い、エスケープしない差し込み（三重の波かっこ・アンパサンドの差し込み）を書かない。属性の値に差し込むときは値を二重引用符で囲む（エンジンは単一引用符を置き換えないため）。テストは置き場のすべてのテンプレートを数え上げて調べ、U3 が足したテンプレートも自動で対象になる。あわせて、差し込む値に < > & \" ' を含めても描いた本文でタグや属性として出ずエスケープされた形で出ることを確かめる。この確かめは次の2つで行う。(1) 置き場のすべてのテンプレート（今回は invitation の ja・en）について、一覧の差し込みの名前すべて（invitation では registrationUrl と validityHours）に5つの文字を含む値を入れて描き、本文と title のどこにもタグや属性として出ず、エスケープされた形で出ることを確かめる（差し込む値すべてを対象にするため、後のテンプレートが利用者の入れた値（氏名・メールアドレスなど）を差し込んでも自動で対象になる）。(2) 仕組みとしてのエスケープを、U1 のテスト用のテンプレート（本文の文面・二重引用符で囲んだ属性の値・title の3か所に差し込む）に同じ5つの文字を含む値を与えて確かめる。招待のテンプレートは利用者の入れた値（招待先のメールアドレス・氏名）を差し込まないため、AC3.1.4 の「差し込む利用者の値」は (1) と (2) で満たす"
    violation: テストの失敗（統合しない）
    source: FR2.2、NFR2、AC3.1.4、AC3.1.6、要点 4、承認の場の Request Changes（2026-09-27、U1 の R-03）
  - id: BR2.5
    statement: テンプレートのライセンスヘッダーは Mustache のコメントで書き、描いた本文に出さない
    category: constraint
    applies_to: すべてのテンプレート
    trigger: テンプレートを書くとき、ビルドの検査
    logic: "IF テンプレートにライセンスヘッダーを書く THEN Mustache のコメントで書く（HTML のコメントは受け手に届くため使わない）。ライセンスヘッダーの検査の対象にバックエンドのメールのテンプレートを加える"
    violation: ライセンスヘッダーの検査・テストの失敗（統合しない）
    source: AC3.1.5、要点 4（team.md の Code Style）
  - id: BR2.6
    statement: 置き場のすべてのテンプレートを、一覧の差し込みの名前すべてに値を入れて描けること、件名が空でないこと、描き残しとライセンスヘッダーが出ないこと、テンプレートの中の差し込みの名前と一覧が一致することをテストで確かめる
    category: validation
    applies_to: すべてのテンプレートと一覧
    trigger: ビルドの検査（テスト）
    logic: "IF ビルドの検査を行う THEN 置き場のテンプレートを数え上げ、ja・en それぞれについて: 一覧の名前すべてに見本の値を入れて描ける、BR4.2 の件名が空でない、本文と件名に二重の波かっこの残りとライセンスヘッダーの文面が無い、BR4.3 の lang が合う、テンプレートの中に出てくる差し込みの名前の集合が一覧の variableNames と一致する"
    violation: テストの失敗（統合しない）
    source: AC3.1.5、Q4

  # ---- BR3: 送信の依頼の確かめ ----
  - id: BR3.1
    statement: 宛先は正規化済みのメールアドレスで、254 文字まで・形式に合うものだけを受け付ける
    category: validation
    applies_to: MailRequest.to
    trigger: 送信の依頼
    logic: "IF 宛先が空、前後に空白がある、大文字を含む（正規化済みでない）、254 文字を超える、または「空白と @ を含まない文字列 @ 空白と @ を含まない文字列 . 空白と @ を含まない文字列」の形に合わない THEN 描かず・接続せずに FAILED（INVALID_INPUT）を返す。決まりは既存の利用者のメールアドレスと同じだが、Mail は他の部品に依存しない（components.md の depends_on が空）ため、U1 の中で同じ決まりを持つ。正規化は呼び出し元が行う"
    violation: FAILED（INVALID_INPUT）
    source: FR1.2、契約 C1、要点 3
  - id: BR3.2
    statement: 宛先・差し込む値に改行（CR・LF）があれば、描かず・送らずに拒否する
    category: validation
    applies_to: MailRequest.to・MailRequest.variables
    trigger: 送信の依頼
    logic: "IF 宛先、またはどれかの差し込む値に CR か LF を含む THEN 描かず・SMTP に接続せずに FAILED（INVALID_INPUT）を返す（受け手が受けるメールは0通、ヘッダーは増えない）"
    violation: FAILED（INVALID_INPUT）
    source: AC3.1.7、NFR2、要点 3
  - id: BR3.3
    statement: 差し込む値の名前の集合は、テンプレートの一覧の名前と完全に一致しなければならず、どの値も null・空の文字列・空白だけの文字列であってはならない
    category: validation
    applies_to: MailRequest.variables
    trigger: 送信の依頼
    logic: "IF 依頼の差し込みの名前に、一覧にある名前の欠け、または一覧に無い余分な名前がある、または値が null のもの、空の文字列のもの、前後の空白を除くと0文字になるもの（空白の判定は Java の String.strip と同じ Character.isWhitespace。半角の空白・タブ・全角の空白を含む）がある THEN 描かずに FAILED（INVALID_INPUT）を返す。空白だけの値も拒否する理由: Q4 の動機は「欠けた値が空で描かれ、リンクの無いメールが送信済みになるのを防ぐ」ことで、空白だけの registrationUrl も描くと中身の無いリンクになり、受け手から見て空と同じだから。値そのものは変えない（前後の空白を除いた値で描くことはせず、渡された値をそのまま描く）。テストの境界: null・\"\"・\" \"（半角の空白1つ）・\"\\t\"（タブだけ）・全角の空白だけは INVALID_INPUT でメールは0通、\"a\"（1文字）と \" a \"（前後に空白のある中身のある値）は受け付けて描く。どの差し込みの名前でも同じに扱うことを、invitation の registrationUrl と validityHours の両方で確かめる"
    violation: FAILED（INVALID_INPUT）
    source: Q4、承認の場の Request Changes（2026-09-27、U1 の R-01）
  - id: BR3.4
    statement: 言語は ja・en だけを受け付け、一覧に無い templateId はテンプレートの誤りとする
    category: validation
    applies_to: MailRequest.language・MailRequest.templateId
    trigger: 送信の依頼
    logic: "IF language が ja・en のどちらでもない THEN FAILED（INVALID_INPUT）。IF templateId が一覧に無い THEN 描かずに FAILED（TEMPLATE_ERROR）"
    violation: FAILED（INVALID_INPUT または TEMPLATE_ERROR）
    source: FR1.2、NFR8、要点 6

  # ---- BR4: 描画と件名 ----
  - id: BR4.1
    statement: 依頼の言語のテンプレートだけで描き、ほかの言語へ切り替えない
    category: policy
    applies_to: 描画
    trigger: 送信の依頼
    logic: "IF 依頼の language が ja THEN templateId の ja のテンプレートで描く（en も同じ）。どの場合も、別の言語や既定の言語へ切り替えて描かない"
    violation: —
    source: FR7.3、CR1.4、AC3.1.1、要点 6
  - id: BR4.2
    statement: 件名は描いた本文の title 要素の文面とし、文字参照を戻し、空白と改行を1つの空白にまとめ、前後の空白を除く。空なら送らない
    category: calculation
    applies_to: RenderedMail.subject
    trigger: 描画の後
    logic: "IF 描いた本文に title 要素がある THEN その文面の HTML の文字参照（例: &amp;）を元の文字に戻し、連続する空白・タブ・改行を1つの空白にまとめ、前後の空白を除いたものを件名にする（件名は改行を含まない）。IF title 要素が無い、または件名が空になる THEN 送らずに FAILED（TEMPLATE_ERROR）"
    violation: FAILED（TEMPLATE_ERROR）
    source: FR2.1、AC3.1.1、AC3.1.5、AC3.1.7、要点 5
  - id: BR4.3
    statement: 描いた本文の html 要素の lang 属性が依頼の言語と違えば送らない
    category: validation
    applies_to: RenderedMail
    trigger: 描画の後
    logic: "IF 描いた本文の html 要素の lang 属性が無い、または依頼の language と一致しない THEN 送らずに FAILED（TEMPLATE_ERROR）"
    violation: FAILED（TEMPLATE_ERROR）
    source: AC3.1.10、FR7.3、要点 6
  - id: BR4.4
    statement: 送信のときに描けなかったときはテンプレートの誤りとして結果で返す
    category: policy
    applies_to: 描画
    trigger: 送信の依頼
    logic: "IF 準備したテンプレートを描く途中で失敗する THEN 送らずに FAILED（TEMPLATE_ERROR）を返す（起動のときの準備で多くは見つかるが、描く途中の失敗も例外にせず結果にする）"
    violation: FAILED（TEMPLATE_ERROR）
    source: 契約 C1、要点 6

  # ---- BR5: メールの形と送信 ----
  - id: BR5.1
    statement: メールは HTML だけ（text/html、文字コード UTF-8）とし、テキストの部分を付けない。件名と差出人の表示名は規格どおりに符号化する
    category: constraint
    applies_to: 送るメール
    trigger: 送信のたび
    logic: "IF メールを組み立てる THEN 本文は描いた HTML だけ（Content-Type は text/html、charset は UTF-8）、宛先は依頼の1人だけ、差出人は BR1.6、件名は BR4.2。日本語を含む件名と表示名はヘッダーの規格どおりに符号化し、ヘッダーの値に改行を入れない"
    violation: —
    source: FR2.1、AC3.1.1、AC3.1.7、要点 7
  - id: BR5.2
    statement: 送信は1回だけで自動の再試行はせず、接続と読み取り（書き込みを含む）に時間切れを持つ
    category: constraint
    applies_to: SMTP の送信
    trigger: 送信のたび
    logic: "IF 送る THEN 1回だけ試みる（失敗しても自動でやり直さない。やり直しは管理者の送り直し）。接続の確立と応答の待ち（書き込みを含む）に時間切れを持ち、超えたら打ち切る（値は NFR 要件の段）"
    violation: 打ち切って FAILED（TIMEOUT）
    source: NFR6、FR2.4、契約 C1、要点 9
  - id: BR5.3
    statement: 想定内の送信の失敗は例外にせず、3つの種類に分けて結果で返す。想定外の失敗だけを例外にする
    category: policy
    applies_to: MailSendResult
    trigger: 送信の失敗
    logic: "IF 接続できない・暗号化を確立できない・途中で接続が切れる THEN CONNECTION_FAILED。IF 接続または応答の待ちが時間切れになる THEN TIMEOUT。IF 受け手が拒否の応答を返す（認証・差出人・宛先・本文のどれの拒否も） THEN REJECTED。IF U1 の不具合など想定外の失敗 THEN 例外（呼び出し元に伝える）"
    violation: —
    source: FR2.4、契約 C1（共通の決まり）、要点 9
  - id: BR5.4
    statement: U1 は内部DB に触れず、トランザクションを持たない。呼び出し元はトランザクションの外で呼ぶ
    category: constraint
    applies_to: send
    trigger: 送信の依頼
    logic: "IF send を呼ぶ THEN 内部DB を読み書きせず、トランザクションを始めない。呼び出し元（U3）は招待の確定の後、トランザクションの外で呼ぶ（送信の待ちの間に内部DB の接続を持たない）"
    violation: 構造で守る（呼び出し元の順序は U3 の決まりとテスト）
    source: FR2.3、NFR5、ADR-009、要点 11

  # ---- BR6: 秘密と個人情報・記録 ----
  - id: BR6.1
    statement: 送信の結果は成功か失敗の種類だけを持ち、宛先・SMTP の応答・例外のメッセージ・資格情報・差し込んだ値・件名を持たない
    category: constraint
    applies_to: MailSendResult
    trigger: 結果を返すとき
    logic: "IF 結果を作る THEN outcome と failureKind だけを入れる"
    violation: —
    source: FR2.6、NFR2、CR5、契約 C1
  - id: BR6.2
    statement: ログは U1 の中で1回だけ、templateId・language・失敗の種類・例外の型の名前だけを出す
    category: constraint
    applies_to: アプリのログ
    trigger: 送信の成功・失敗
    logic: "IF 送信に成功する THEN templateId と language だけの INFO を1件。IF 失敗する THEN templateId・language・失敗の種類（と例外があればその型の名前）だけの WARN を1件（スタックトレースなし）。どの場合も、宛先のメールアドレス・差出人・SMTP の応答の文面・例外のメッセージ・資格情報・差し込んだ値（招待の URL とトークンを含む）・件名・本文を出さない。想定外の例外（BR5.3）のログは呼び出し元の変換の境界で出す"
    violation: —
    source: FR2.6、NFR1、NFR2、CR5、要点 10
  - id: BR6.3
    statement: トレースの属性にも宛先・差し込んだ値・件名・本文・SMTP の応答を入れない
    category: constraint
    applies_to: 分散トレースの属性
    trigger: 送信のたび
    logic: "IF 送信の処理にトレースの属性を付ける THEN templateId・language・結果の種類だけにする"
    violation: —
    source: NFR1、NFR2、CR5
  - id: BR6.4
    statement: U1 は監査ログに書かない。送信の失敗は監査ログに残さない
    category: policy
    applies_to: 監査ログ
    trigger: 送信の失敗
    logic: "IF 送信に失敗する THEN 監査の出来事を出さない（送信の結果の記録は U3 が招待に行う）"
    violation: —
    source: FR9.2、FR2.4
```

## 決まりの一覧

| ID | 決まり | 分類 | 出典 |
|---|---|---|---|
| BR1.1 | SMTP の設定は環境変数だけから受け取る | 方針 | FR2.5 |
| BR1.2 | 接続先と差出人がそろって「設定がある」、何も無ければ警告なし | 検証 | FR1.8、FR2.5、Q2 |
| BR1.3 | 欠け・不正は項目名だけの WARN、起動を続ける | 検証 | FR1.8、FR2.6、NFR2 |
| BR1.4 | 資格情報と暗号化 NONE の組は「設定がない」 | 制約 | Q3 |
| BR1.5 | 暗号化は NONE・STARTTLS・SMTPS、既定 NONE、ポートは方式の標準 | 方針 | Q3、NFR11 |
| BR1.6 | 差出人は環境変数のアドレスと表示名（既定 MasterSmith） | 方針 | Q2 |
| BR1.7 | isConfigured は真偽だけ、「設定がない」ときは接続せず NOT_CONFIGURED | 方針 | FR1.8、契約 C1 |
| BR1.8 | mail.debug は常に無効、有効にする口なし | 制約 | NFR2 |
| BR2.1 | 置き場は mail/templates/<templateId>_<language>.html | 制約 | Q1 |
| BR2.2 | U1 がテンプレートの一覧を持ち、足す単位が行を足す（invitation は registrationUrl・validityHours） | 方針 | Q4、C10 |
| BR2.3 | 起動時にすべて準備、欠け・壊れ・置き場の誤りで起動を止める | 検証 | Q1、FR2.1 |
| BR2.4 | エスケープされる差し込みだけ、属性は二重引用符。確かめは差し込む値すべてとテスト用の差し込み | 制約 | FR2.2、AC3.1.4、AC3.1.6 |
| BR2.5 | ライセンスヘッダーは Mustache のコメント、本文に出さない | 制約 | AC3.1.5 |
| BR2.6 | すべてのテンプレートを描くテストと、名前と一覧の一致のテスト | 検証 | AC3.1.5、Q4 |
| BR3.1 | 宛先は正規化済み・254 文字まで・形式に合う | 検証 | FR1.2 |
| BR3.2 | 宛先・差し込む値の改行は拒否 | 検証 | AC3.1.7 |
| BR3.3 | 差し込みの名前は一覧と完全一致（欠け・余分・null・空・空白だけは拒否） | 検証 | Q4 |
| BR3.4 | 言語は ja・en だけ、一覧に無い templateId は TEMPLATE_ERROR | 検証 | FR1.2、NFR8 |
| BR4.1 | 依頼の言語のテンプレートだけで描く | 方針 | FR7.3、CR1.4 |
| BR4.2 | 件名は title の文面（戻す・まとめる・除く）、空なら TEMPLATE_ERROR | 計算 | FR2.1、AC3.1.1 |
| BR4.3 | html の lang が依頼の言語と違えば TEMPLATE_ERROR | 検証 | AC3.1.10 |
| BR4.4 | 描く途中の失敗は TEMPLATE_ERROR | 方針 | 契約 C1 |
| BR5.1 | HTML だけ・UTF-8・ヘッダーは規格どおりに符号化 | 制約 | FR2.1、AC3.1.1 |
| BR5.2 | 送信は1回だけ、接続と読み取りに時間切れ | 制約 | NFR6、契約 C1 |
| BR5.3 | 失敗は CONNECTION_FAILED・TIMEOUT・REJECTED の結果、想定外だけ例外 | 方針 | FR2.4、契約 C1 |
| BR5.4 | 内部DB に触れない、トランザクションの外で呼ばれる | 制約 | FR2.3、ADR-009 |
| BR6.1 | 結果は成功か失敗の種類だけ | 制約 | FR2.6、NFR2 |
| BR6.2 | ログは templateId・language・種類・例外の型の名前だけを1回 | 制約 | FR2.6、NFR1、NFR2 |
| BR6.3 | トレースの属性に宛先・値・件名を入れない | 制約 | NFR1、NFR2 |
| BR6.4 | 監査ログに書かない | 方針 | FR9.2 |
