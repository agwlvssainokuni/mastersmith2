# Rules — U4 DSL の管理（u4-dsl-management）

```yaml
rules:
  # --- プレビューに置く（生成・投入・履歴からの戻し）
  - id: BR1.1
    statement: スキーマの読み込みは、U3 で既定の DSL を作ってプレビューに置く
    category: policy
    applies_to: 生成
    trigger: POST /preview/generate
    logic: "IF U3 が GENERATED THEN BR1.4 でプレビューに置き、DSL_GENERATED の出来事を出す。IF TARGET_UNCONFIGURED THEN 503 TARGET_DB_UNCONFIGURED。IF TARGET_UNAVAILABLE THEN 503 TARGET_DB_UNAVAILABLE。失敗のときはプレビューも適用中も変えない"
    violation: 503（プレビューは変わらない）
    source: FR3.1、FR3.6、FR2.4
  - id: BR1.2
    statement: 投入（アップロード・貼り付け）は、U2 で検証を通ったものだけをプレビューに置く
    category: validation
    applies_to: 投入
    trigger: POST /preview?source=UPLOAD|PASTE
    logic: "IF 本文が application/yaml でない THEN 415。IF 5MB を超える THEN 413 DSL_TOO_LARGE。IF U2 が INVALID THEN 422 DSL_INVALID（保存しない）。IF VALID THEN BR1.4 でプレビューに置き、DSL_SUBMITTED の出来事を出す。受け付けなかったときは DSL_SUBMISSION_REJECTED の出来事を出す"
    violation: 413・415・422（プレビューも適用中も変わらない）
    source: FR4.1〜FR4.5、NFR2
  - id: BR1.3
    statement: 履歴からの戻しは、その版を U2 で検証し直してからプレビューに置く
    category: validation
    applies_to: 履歴からの戻し
    trigger: POST /history/{revisionId}/restore
    logic: "IF 版が無い THEN 404 DSL_REVISION_NOT_FOUND。IF 今の U2 の検証が INVALID THEN 422 DSL_INVALID（プレビューは変わらない）。ELSE 出どころ RESTORE で BR1.4 でプレビューに置き、DSL_SUBMITTED（source RESTORE）の出来事を出す"
    violation: 404・422
    source: FR7.2
  - id: BR1.4
    statement: プレビューに置くと、今のプレビューを置き換え、新しい previewId を振る
    category: policy
    applies_to: プレビュー
    trigger: BR1.1〜BR1.3 の成功
    logic: "固定の鍵の1行を、新しい previewId・本文・識別・出どころ・置いた人・日時で書き換える（行が無ければ入れる。1文の MERGE）。適用中の DSL は変えない。応答は BR2.1 のプレビューの中身。2人がほぼ同時に置いたときは、後に確定した方が残る（最後の書き込みが勝つ）。先に置いた人の応答の previewId は残らないため、それで適用すると BR4.1 で 409 になる。置く操作どうしで待ち合わせや拒否はしない"
    violation: —
    source: FR3.6、FR4.5、RF3
  - id: BR1.5
    statement: 誤りの一覧は、先頭の100件と総数を返す
    category: policy
    applies_to: 422 の応答
    trigger: 検証を通らないとき
    logic: "U2 の誤りを先頭から100件まで errors に、総数を total に入れる。文言は要求の表示言語で、部品の例外のメッセージを含めない"
    violation: —
    source: FR4.3、RQ（Refined Mockups Q5）
  - id: BR1.6
    statement: 要求の本文を 5MB まで受けるのは、投入の API だけ
    category: constraint
    applies_to: 本文の大きさの上限
    trigger: すべての要求
    logic: "IF 要求が POST /api/admin/dsl/preview THEN 本文の上限は 5MB。ELSE 今までどおり 1MB（超えれば 413）"
    violation: 413
    source: NFR2

  # --- プレビューの表示
  - id: BR2.1
    statement: プレビューの中身は、要約・違い・照合の警告を表示のたびに求める
    category: policy
    applies_to: GET /preview（と BR1.4 の応答）
    trigger: 表示
    logic: "U2 でプレビューをモデルにし、要約（テーブル数・ビューの数・カラム数・メニューの木・表示名の未設定）、適用中との違い、対象DB との照合を求めて返す。保存しない"
    violation: —
    source: FR5.1、FR5.2
  - id: BR2.2
    statement: 違いは、テーブルの増えた・減った・変わった・変わらない、カラムの増えた・減った・変わったと、変わった項目の名前で示す
    category: calculation
    applies_to: 違い
    trigger: 表示
    logic: "テーブルは表示名・ビューかどうか・主キー・外部キー、カラムは表示名・DB 上の型・フォーム部品・検索・一覧・詳細・バリデーション・選択肢を比べ、違う項目の名前（例: label.ja、list.order）を changedItems に並べる。テーブルは UNCHANGED を含めてすべて並べる。カラムは ADDED・REMOVED・CHANGED だけを並べ、変わらないカラムは並べない（契約 C6 のカラムの区分は3つ）。適用中が無ければ、すべて ADDED で appliedExists は false"
    violation: —
    source: FR5.1
  - id: BR2.3
    statement: 表示名の未設定は、先頭100件と総数を返す
    category: calculation
    applies_to: 要約
    trigger: 表示
    logic: "ja または en が空の表示名の場所を、先頭100件まで missingDisplayNames に並べ、総数を missingDisplayNameTotal に入れる（契約 C6）"
    violation: —
    source: FR5.1
  - id: BR2.4
    statement: 対象DB との照合の食い違いは警告にとどめる。照合できないときも警告にする
    category: policy
    applies_to: 照合
    trigger: 表示
    logic: "U1 の写しと比べ、無いテーブル（TABLE_MISSING）・無いカラム（COLUMN_MISSING）・型の名前と長さ・精度・桁の違い（TYPE_MISMATCH）を警告にする。U1 が UNCONFIGURED・UNAVAILABLE なら、その警告を1件にする。どれも失敗にしない。警告に接続先と内部の文言を含めない"
    violation: —
    source: FR5.2、RQ7
  - id: BR2.5
    statement: プレビューが無いときの取得・ダウンロード・破棄は 404
    category: validation
    applies_to: GET /preview・GET /preview/download・DELETE /preview
    trigger: プレビューが無い
    logic: "IF プレビューの行が無い THEN 404 DSL_PREVIEW_NOT_FOUND"
    violation: 404
    source: FR5.3、FR8.1

  # --- 破棄
  - id: BR3.1
    statement: 破棄はプレビューの行を消し、適用中の DSL は変えない
    category: policy
    applies_to: DELETE /preview
    trigger: 破棄
    logic: "プレビューの行を消し、確定の後に DSL_PREVIEW_DISCARDED の出来事を出す"
    violation: —
    source: FR5.3

  # --- 適用
  - id: BR4.1
    statement: 適用は、要求の previewId が今のプレビューと同じときだけ行う
    category: validation
    applies_to: POST /apply
    trigger: 適用
    logic: "IF プレビューが無い、または previewId が違う THEN 409 DSL_PREVIEW_CHANGED（適用中は変わらない）"
    violation: 409
    source: FR6.2
  - id: BR4.2
    statement: 適用は、履歴への追加・プレビューの削除・古い履歴の削除を1つのトランザクションで行う
    category: constraint
    applies_to: 適用
    trigger: BR4.1 を通った後
    logic: "1つのトランザクションで、(1) プレビューの内容で履歴に1行足す、(2) previewId を指定してプレビューの行を消す、(3) 上限を超えた古い履歴を消す。どこかで失敗したら全部を巻き戻す"
    violation: 巻き戻し（途中の状態を残さない）
    source: FR6.1、FR7.1、FR7.3、NFR8
  - id: BR4.3
    statement: 同時の適用は、プレビューの行を消せた1件だけが成功する
    category: constraint
    applies_to: 適用
    trigger: 2つ以上の同時の適用
    logic: "IF BR4.2 の (2) で消した行が0件 THEN 巻き戻して 409 DSL_PREVIEW_CHANGED"
    violation: 409
    source: FR6.2、NFR8
  - id: BR4.4
    statement: 履歴の件数は mastersmith.dsl.history-limit まで
    category: constraint
    applies_to: 履歴
    trigger: 適用
    logic: "適用で1行足した後、件数が上限を超えたら、追加の順の古いものから消す（既定 20、1以上）"
    violation: —
    source: FR7.3
  - id: BR4.5
    statement: 適用中と同じ内容の DSL を適用しても、履歴に1件足す
    category: policy
    applies_to: 適用
    trigger: 適用
    logic: "識別が適用中と同じでも、BR4.2 のとおり行う"
    violation: —
    source: FR7.1（M3: A）
  - id: BR4.6
    statement: 確定の後に適用中のモデルを差し替え、出来事を出す。巻き戻ったときはどちらもしない
    category: constraint
    applies_to: 適用
    trigger: BR4.2 の確定
    logic: "IF 確定した THEN U2 のモデルで適用中を差し替え、DSL_APPLIED の出来事を出す。IF 巻き戻った THEN 差し替えず、出来事も出さない"
    violation: —
    source: FR6.1、FR6.4、FR10.3
  - id: BR4.7
    statement: 適用で対象DB に何も書き込まない
    category: constraint
    applies_to: 適用
    trigger: 適用
    logic: "適用で対象DB に接続しない"
    violation: —
    source: FR2.5

  # --- 起動
  - id: BR5.1
    statement: 起動時に適用中の DSL を読み、適用中のモデルに入れる
    category: policy
    applies_to: 起動
    trigger: アプリの起動（要求を受ける前）
    logic: "履歴の最新を読み、U2 でモデルにして差し替える。履歴が無ければ『無い』"
    violation: —
    source: FR6.3
  - id: BR5.2
    statement: 起動時に適用中の DSL を読めないときは、ERROR を1件出して『無い』で起動を続ける
    category: policy
    applies_to: 起動
    trigger: BR5.1 で U2 が INVALID（書式の版が変わった など）
    logic: "ERROR のログを1件（識別と誤りの種類だけ、本文は出さない）出し、適用中のモデルを『無い』にする。起動は止めない"
    violation: —
    source: FR6.3

  # --- 履歴・ダウンロード・今の状態
  - id: BR6.1
    statement: 履歴は新しい順に、適用中の印をつけて返す
    category: policy
    applies_to: GET /history
    trigger: 履歴の表示
    logic: "追加の順の新しい順に、版の識別・識別・出どころ・適用した人（メールアドレス）・日時・適用中か を返す"
    violation: —
    source: FR7.1
  - id: BR6.2
    statement: ダウンロードは保存した本文をそのまま返し、ファイル名で種類と識別が分かるようにする
    category: policy
    applies_to: GET /preview/download・GET /applied/download
    trigger: ダウンロード
    logic: "本文を application/yaml で返す。ファイル名は dsl-preview-<識別の先頭12文字>.yaml・dsl-applied-<識別の先頭12文字>.yaml。適用中が無ければ 404 DSL_APPLIED_NOT_FOUND"
    violation: 404
    source: FR8.1
  - id: BR6.3
    statement: 今の状態は、適用中とプレビューの識別・出どころ・人・日時を返す
    category: policy
    applies_to: GET /status
    trigger: 画面の表示
    logic: "適用中（履歴の最新）とプレビューを、無ければ null で返す。人はメールアドレス、利用者が消えていたら不明とする"
    violation: —
    source: FR5.1、FR7.1

  # --- 監査
  - id: BR7.1
    statement: DSL の操作ごとに出来事を出し、既存の監査の仕組みで記録する
    category: policy
    applies_to: 監査
    trigger: 生成・投入・受け付けなかった投入・適用・破棄
    logic: "DslOperationEvent を出す。AuditLog が確定の後に、操作した人・日時・種類・識別・出どころ・理由の種類を監査の表に記録する（列を足す）"
    violation: —
    source: FR10.1、FR10.2
  - id: BR7.2
    statement: 監査の記録に DSL の本文と接続先を入れない
    category: constraint
    applies_to: 監査
    trigger: 記録
    logic: "出来事にも記録にも、本文・接続先・ユーザー名・パスワードを入れない"
    violation: —
    source: FR10.2、NFR4
  - id: BR7.3
    statement: 監査の書き込みに失敗しても、元の操作は失敗させない。巻き戻った操作は記録しない
    category: policy
    applies_to: 監査
    trigger: 記録
    logic: "既存の決まり（確定の後に記録、失敗は ERROR のログ1件）に従う"
    violation: —
    source: FR10.3
  - id: BR7.4
    statement: 受け付けなかった投入の理由の種類は、最初の誤りの種類とする
    category: calculation
    applies_to: DSL_SUBMISSION_REJECTED
    trigger: 受け付けなかった投入
    logic: "誤りの一覧の最初の誤りの種類。読む前に大きさで止めたときは SIZE_LIMIT。本文を読めないときは識別を無しにする"
    violation: —
    source: FR10.1（M2: A）

  # --- 応答
  - id: BR8.1
    statement: 誤りの一覧は、業務の例外の追加の項目として共通の変換で Problem Details に載せる
    category: constraint
    applies_to: エラー応答
    trigger: 想定内の失敗
    logic: "業務処理の層で結果の型を業務の例外に変え、追加の項目（errors・total）を持たせて投げる。共通の変換が追加の項目を載せる。既存の項目名（code・traceId など）は上書きしない"
    violation: —
    source: FR4.3、NFR5
  - id: BR8.2
    statement: 1つの code の状態コードは1つだけ
    category: constraint
    applies_to: 新しい code の登録
    trigger: 起動
    logic: "DSL_INVALID 422、DSL_TOO_LARGE 413、DSL_PREVIEW_NOT_FOUND 404、DSL_PREVIEW_CHANGED 409、DSL_APPLIED_NOT_FOUND 404、DSL_REVISION_NOT_FOUND 404、TARGET_DB_UNCONFIGURED 503、TARGET_DB_UNAVAILABLE 503 を、日英の説明つきで登録する"
    violation: 起動の失敗（既存の重複の検査）
    source: NFR5
  - id: BR8.3
    statement: DSL の操作の API はすべて /api/admin/dsl/ の下に置く
    category: authorization
    applies_to: API
    trigger: すべての要求
    logic: "既存の AccessControl により、未認証 401・管理者でない 403（アクセス拒否の監査つき）、管理者なら処理する"
    violation: 401・403
    source: FR9.2
```

## 決まりの一覧

| 群 | ID | 要点 |
|---|---|---|
| プレビューに置く | BR1.1〜BR1.6 | 生成・投入・戻しは検証を通ったものだけ、置き換えて新しい previewId、誤りは先頭100件と総数、5MB は投入の API だけ |
| プレビューの表示 | BR2.1〜BR2.5 | 要約・違い・照合は表示のたびに求める、変わった項目の名前、未設定の表示名は先頭100件、照合の食い違いは警告、無ければ 404 |
| 破棄 | BR3.1 | プレビューを消し、適用中は変えない |
| 適用 | BR4.1〜BR4.7 | previewId の一致、1つのトランザクション、同時は1件だけ、履歴の上限、同じ内容も足す、確定の後に差し替えと出来事、対象DB に書かない |
| 起動 | BR5.1・BR5.2 | 適用中を読む、読めなければ ERROR と「無い」 |
| 履歴・ダウンロード・状態 | BR6.1〜BR6.3 | 新しい順と適用中の印、本文をそのまま、今の状態 |
| 監査 | BR7.1〜BR7.4 | 出来事と列の追加、本文と接続先を入れない、既存の決まり、理由の種類 |
| 応答 | BR8.1〜BR8.3 | 追加の項目の口、1つの code に1つの状態、/api/admin/dsl/ の下 |
