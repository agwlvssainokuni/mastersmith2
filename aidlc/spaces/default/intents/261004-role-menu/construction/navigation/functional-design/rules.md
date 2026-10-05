# 決まり — U5 navigation

出典の表記: FR・NFR・C は `requirements.md`、AC は `stories.md`、Q1〜Q6 と SP（この段で決める設計の要点、番号は節の中の順）は `functional-design-questions.md` の答え（まとめの確認で承認）、C1・C3・C5・C9 は `contract-summary.md`、dsl-BR は `construction/dsl-v2/functional-design/rules.md`、role-BR は `construction/role/functional-design/rules.md`、TP は `team.md`、PM は `project.md`。

```yaml
rules:
  # BR1 木を作る（読み取りの流れ）
  - id: BR1.1
    statement: 業務のメニューの API と置き場の問い合わせは、主体を要求の文脈（認証の結果の利用者）から読み、利用者を指す値を要求から受け取らない
    category: authorization
    applies_to: GET /api/me/navigation・GET /api/me/table-access
    trigger: 要求を受けたとき
    logic: IF 要求に利用者を指す値（userId などの問い合わせの引数）がある THEN 読まずに無視し、主体の userId だけを使う
    violation: なし（ほかの利用者の木や権限は返らない）
    source: FR6.2・NFR1.4・TP（認証の主体は要求の文脈から読む）・C9
  - id: BR1.2
    statement: 1回の要求で適用中の DSL を current() で1回だけ読み、Absent なら空の木を返す
    category: policy
    applies_to: 業務のメニューの API
    trigger: 要求を受けたとき
    logic: IF current() が Absent THEN items を空、emptyReason を NOT_CONFIGURED にして 200 で返し、解決の口は呼ばない
    violation: なし（誤りにしない）
    source: FR9.5・AC5.1.6・C3・C9・Q3
  - id: BR1.3
    statement: 適用中の DSL の menus が空なら空の木を返す
    category: policy
    applies_to: 業務のメニューの API
    trigger: current() が Present のとき
    logic: IF model.menus が空 THEN items を空、emptyReason を NOT_CONFIGURED にして 200
    violation: なし
    source: FR9.5・AC5.1.6・Q3
  - id: BR1.4
    statement: 実効の権限は1回の要求で snapshotFor(userId) を1回だけ呼んで得た写しで判定し、写しを要求をまたいで持たない
    category: policy
    applies_to: 業務のメニューの API
    trigger: menus が空でないとき
    logic: snapshotFor(userId) を1回呼び、写しの main(schemaName, tableName, null) をテーブルの主権限として絞る関数に渡す。キャッシュを置かない
    violation: なし
    source: FR5.2・FR8.2・AC5.1.5・AC5.1.11・AC5.1.18・C5・role-BR5.4・SP 3
  - id: BR1.5
    statement: current() と写しの DSL の識別が食い違っても読み直さない
    category: policy
    applies_to: 業務のメニューの API
    trigger: 写しの dslHash が current() の dslHash と違うとき（読み取りの間に DSL が適用し直された）
    logic: current() で読んだ menus を、写しの値でそのまま絞る。新しい DSL に無いテーブルは写しが NONE を返すため落ちる
    violation: なし（権限の無い項目は出ない。決めた側の動作としてテストに書く）
    source: SP 3・role-BR5.3
  - id: BR1.6
    statement: 業務のメニューの API は業務のメニューの木だけを返し、管理のメニューを含めない
    category: policy
    applies_to: NavigationResponse
    trigger: 応答を作るとき
    logic: IF 項目を作る THEN 出どころは DSL の menus だけ。画面の登録・管理者の印は見ない
    violation: なし
    source: FR10.3・FR10.5・AC5.1.5・C9

  # BR2 絞る（純粋な関数 MenuFilter）
  - id: BR2.1
    statement: テーブルを指す項目は、テーブルの階層の実効の主権限だけで出すかを決め、カラムの値は見ない
    category: authorization
    applies_to: MenuFilter
    trigger: 項目が table を持つとき
    logic: tableMain(schema, name) を求める。READ・FULL なら見える、NONE なら見えない。カラムに明示の READ・FULL があっても見ない
    violation: なし（見えない項目は応答に含めない）
    source: FR10.1・AC5.1.2・NFR1.1
  - id: BR2.2
    statement: テーブルと子の両方を持つ項目は、テーブルの見え方と子の残り方の組で出し方を決める
    category: calculation
    applies_to: MenuFilter
    trigger: 項目が table と items の両方を持つとき
    logic: IF テーブルが見える THEN navigable を true（子は絞った後の子、空でもよい）。IF テーブルが見えず、絞った後の子が1つ以上 THEN navigable を false・table を null のまとまり。IF テーブルが見えず、子がすべて落ちた THEN 項目を落とす
    violation: なし
    source: AC5.1.2・C9
  - id: BR2.3
    statement: 子がすべて落ちたまとまり（テーブルを持たない項目、またはテーブルが見えない項目）は落とし、落とした結果も上へさかのぼって当てる
    category: calculation
    applies_to: MenuFilter
    trigger: 子を絞った後
    logic: 子を先に絞り（深さ優先）、IF 項目が navigable でなく絞った後の子が空 THEN 項目を落とす。親でも同じ判定を繰り返す
    violation: なし
    source: FR10.2・AC5.1.2・AC5.1.15
  - id: BR2.4
    statement: 残った項目の親子と並びは元の DSL の木と同じにする
    category: calculation
    applies_to: MenuFilter
    trigger: 絞るとき
    logic: 項目を消すだけで、並べ替え・付け替え・深さの変更をしない
    violation: なし
    source: AC5.1.15・SP 7
  - id: BR2.5
    statement: 作業ロールが無い・すべて NONE なら絞った結果は空で、emptyReason は NOTHING_VISIBLE
    category: calculation
    applies_to: MenuFilter・NavigationResponse
    trigger: menus が空でないとき
    logic: 写しは作業ロールが無ければすべて NONE（C5）。IF 絞った結果が空 THEN items を空、emptyReason を NOTHING_VISIBLE にして 200
    violation: なし（誤りにしない）
    source: FR10.1・AC5.1.17・Q3・C5
  - id: BR2.6
    statement: DSL に無いテーブルを指す項目は、主権限が NONE として落ちる
    category: authorization
    applies_to: MenuFilter
    trigger: tableMain が DSL に無い組を問われたとき
    logic: 解決の口は DSL に無い対象を NONE で返す（role-BR5.3）。絞る関数は NONE として扱い、特別な分岐を持たない。適用中のモデルでは起きない（dsl-BR1.6）が、関数の単独のテストで確かめる
    violation: なし
    source: FR8.3・AC5.1.14
  - id: BR2.7
    statement: 絞る関数は副作用の無い純粋な関数にし、6つの性質を jqwik の性質ベースのテストで確かめる
    category: constraint
    applies_to: MenuFilter
    trigger: テストのとき
    logic: 性質は (a) 移れる項目の実効はすべて READ・FULL (b) 子の無いまとまりが無い (c) 親子と並びが元と同じ (d) すべて NONE なら空 (e) 権限を強めても残る項目は減らない (f) 深さは元を超えない。失敗時の乱数の種を記録する
    violation: テストの失敗で統合しない
    source: NFR6.2・AC5.1.15・TP（役割・権限と N 階層のメニューの性質ベースのテスト）・SP 4

  # BR3 応答の値
  - id: BR3.1
    statement: 項目の id は、絞る前の DSL の木での位置の道（各段の 0 から数えた番号を . でつないだ文字列）にする
    category: calculation
    applies_to: NavNode.id
    trigger: 項目を作るとき
    logic: 1段目の i 番目は "i"、その下の j 番目は "i.j"。番号は絞る前の DSL の順で数え、落とした項目があっても詰めない。前置きは付けない
    violation: なし
    source: Q2・AC5.1.9・C9
  - id: BR3.2
    statement: label は DSL の表示名の組（ja・en）をそのまま返し、サーバーではエスケープも空白の除去もしない
    category: policy
    applies_to: NavNode.label・TableAccessResponse.displayName
    trigger: 応答を作るとき
    logic: DSL の DisplayName の ja・en を写す。空の文字列もそのまま。HTML として描かないことは画面が守る
    violation: なし
    source: FR9.6・AC5.1.7・Q1
  - id: BR3.3
    statement: emptyReason は items が空のときだけ値を持ち、空の理由で値を決める
    category: calculation
    applies_to: NavigationResponse.emptyReason
    trigger: 応答を作るとき
    logic: IF DSL が無い OR menus が空 THEN NOT_CONFIGURED。ELSE IF 絞った結果が空 THEN NOTHING_VISIBLE。ELSE null
    violation: なし
    source: Q3・AC5.1.6・AC5.1.17
  - id: BR3.4
    statement: 応答に DSL の識別・ロールの ID・権限の値（navigable 以外）を載せない
    category: policy
    applies_to: NavigationResponse・NavNode
    trigger: 応答を作るとき
    logic: 項目は id・label・icon・table・navigable・items だけ。置き場の問い合わせの 200 だけが main（READ・FULL）を持つ
    violation: なし
    source: FR10.5・SP 7
  - id: BR3.5
    statement: navigable はテーブルを指し実効が READ・FULL のとき true で、そのときだけ table に組を入れる
    category: calculation
    applies_to: NavNode.navigable・NavNode.table
    trigger: 項目を作るとき
    logic: IF 項目が table を持ち見える THEN navigable true・table に {schemaName, tableName}。ELSE navigable false・table null
    violation: なし
    source: C9・AC5.1.2

  # BR4 アイコン
  - id: BR4.1
    statement: アイコンの許した名前は resources の一覧のファイル1つから読む
    category: policy
    applies_to: AllowedNavIconList
    trigger: アプリの起動のとき（読み込みは1回）
    logic: navigation/allowed-icons.txt を1行ずつ読み、# で始まる行と空行を飛ばし、前後の空白を除いた名前の集まりにする
    violation: なし
    source: Q5・C9
  - id: BR4.2
    statement: DSL の icon が null・空・許した名前に無いときは list に置き換え、置き換えのログは出さない
    category: validation
    applies_to: NavNode.icon
    trigger: 項目を作るとき
    logic: IF icon が一覧の名前と文字どおり一致する（大文字と小文字を区別し、前後の空白を許さない） THEN そのまま。ELSE list
    violation: なし（拒否しない。TP の★は要件で既定のアイコンに決まっている）
    source: FR9.6・AC5.1.7・C9・SP 5
  - id: BR4.3
    statement: 一覧のファイルが読めない・名前が1つも無い・list を含まない・名前が重なるときは起動を止める
    category: constraint
    applies_to: AllowedNavIconList
    trigger: アプリの起動のとき
    logic: IF ファイルが無い OR 読めない OR 名前が0個 OR list が無い OR 重なりがある THEN 起動に失敗する（ERROR に理由の種類だけを出す）
    violation: 起動の失敗（設定の誤りは早く止める）
    source: Q5・construction.md の Error Handling
  - id: BR4.4
    statement: 一覧のファイルと画面の app/registry のアイコンの一覧が一致することを、画面のテストで確かめる
    category: constraint
    applies_to: AllowedNavIconList・画面の app/registry の一覧
    trigger: ./gradlew verify の画面のテスト
    logic: 画面のテストが一覧のファイルを読み（BR4.1 と同じ読み方）、app/registry の一覧と集まりとして一致しなければ失敗する
    violation: テストの失敗で統合しない（make-you-chic-ui の固定先を上げたときの直し忘れを止める）
    source: Q5

  # BR5 テーブルの置き場の問い合わせ
  - id: BR5.1
    statement: 問い合わせは問い合わせの引数 schema と table で名前を受け、どちらかが無い・空のときだけ入力の誤りにする。長さでは拒否しない
    category: validation
    applies_to: GET /api/me/table-access
    trigger: 要求を受けたとき
    logic: IF schema か table が無い・空 THEN 拒否。名前の長さと文字の種類は問わない（DSL の名前に長さと文字の制限が無く、上限を置くとメニューに出るのに開けないテーブルができるため）。要求の大きさは既存の仕組みの範囲に任せる
    violation: 400 VALIDATION_FAILED（テーブルの有無によらず同じ）
    source: Q4・AC5.1.13・SP 6・承認の場の直し R-01
  - id: BR5.2
    statement: DSL が無い・組が DSL に無い・実効の主権限が NONE は、区別せずに同じ 403 にする
    category: authorization
    applies_to: GET /api/me/table-access
    trigger: 入力の検証を通った後
    logic: 主体の写しを snapshotFor で1回作り（DSL が無いとき・組が無いときも呼ぶ）、main(schema, table, null) が NONE なら NotVisible。表示名など中身を返さない
    violation: 403 ACCESS_DENIED（本文は既存の共通の形だけ）
    source: FR8.3・FR10.4・AC5.1.4・AC5.1.11・C9・SP 6
  - id: BR5.3
    statement: 実効が READ・FULL なら、組と表示名の組と主権限を 200 で返す
    category: policy
    applies_to: GET /api/me/table-access
    trigger: 主権限が READ・FULL のとき
    logic: Visible(schemaName, tableName, テーブルの DisplayName, main) を返す
    violation: なし
    source: FR9.3・AC5.1.3・C9
  - id: BR5.4
    statement: 置き場の問い合わせは、そのテーブルがメニューに出ているかを見ない
    category: policy
    applies_to: GET /api/me/table-access
    trigger: 判定のとき
    logic: 判定は FR8 の口（写しの主権限）だけ。メニューに無くても DSL にあり READ 以上なら 200
    violation: なし
    source: FR10.4・C9
  - id: BR5.5
    statement: 置き場の問い合わせの 403 は監査に残さず、既存の例外の変換の WARN の1行だけにする
    category: policy
    applies_to: GET /api/me/table-access
    trigger: NotVisible のとき
    logic: BusinessException(ACCESS_DENIED) に変え、既存の GlobalExceptionHandler の WARN（code・状態・例外の型）だけ。スキーマ名・テーブル名はログに出さない
    violation: なし
    source: Q6

  # BR6 API と認可
  - id: BR6.1
    statement: 2本の API はログインだけの分類（AUTHENTICATED）の印を付け、未認証は 401、停止中の利用者は既存の認証の決まりで通らない
    category: authorization
    applies_to: GET /api/me/navigation・GET /api/me/table-access
    trigger: 要求を受けたとき
    logic: 注釈 ApiAccess(AUTHENTICATED) を付ける。認証は既存の仕組み（アクセストークンの認証で停止中を拒否）に任せ、navigation の中で重ねない
    violation: 401 AUTHENTICATION_REQUIRED（停止中は既存の応答のまま）
    source: NFR1.2・NFR1.3・AC5.1.5・C1・TP
  - id: BR6.2
    statement: 2本の API は読み取りだけで、内部DB に書かない
    category: constraint
    applies_to: navigation の機能
    trigger: 要求を受けたとき
    logic: 書き込みの操作を持たない。解決の口の読み取りのトランザクションは role が持つ
    violation: なし
    source: SP 1・C9

  # BR7 構造・観測
  - id: BR7.1
    statement: navigation は domain・service・web の3つの層で作り、依存してよい先を境界テストで固定する
    category: constraint
    applies_to: navigation のパッケージ
    trigger: テストのとき
    logic: navigation が依存してよいのは dsl.service・dsl.domain・role.service・role.domain・access.domain・auth.domain・common だけ。どの機能も navigation に依存しない。NavigationBoundaryArchitectureTest に書く。既存の境界テストは緩めない
    violation: テストの失敗で統合しない
    source: C4（要件の制約）・TP（Code Style）・SP 1・SP 2
  - id: BR7.2
    statement: 置き場の問い合わせの業務処理は結果の型で返し、画面入出力の層が switch で場合を尽くして応答に変える
    category: constraint
    applies_to: TableAccessResult
    trigger: 業務処理の戻りのとき
    logic: Visible は 200、NotVisible は BusinessException(ACCESS_DENIED)。想定外の失敗（DB の誤り）は例外のまま投げる
    violation: なし
    source: TP（Code Style）
  - id: BR7.3
    statement: メニューの API は監査に残さず、新しい指標を足さない
    category: policy
    applies_to: navigation の機能
    trigger: 要求を受けたとき
    logic: 既存の HTTP の指標（http.server.requests）だけを使う
    violation: なし
    source: SP 8・NFR5.2
  - id: BR7.4
    statement: 受け渡す値は個人に関する値・秘密を持たず、伏せ字の型を使わない
    category: policy
    applies_to: navigation の各層の引数と戻り値
    trigger: TRACE のログのとき
    logic: 引数は userId と名前・表示名・権限の値だけ。メールアドレス・氏名を受け渡さない
    violation: なし
    source: SP 9・NFR1.6
```

## 要約

| ID | 種類 | 決まり | 違反のとき |
|---|---|---|---|
| BR1.1 | authorization | 主体は要求の文脈から。利用者を指す値は受け取らない | 無視 |
| BR1.2 | policy | current() を1回、Absent なら空・NOT_CONFIGURED | 誤りにしない |
| BR1.3 | policy | menus が空なら空・NOT_CONFIGURED | 誤りにしない |
| BR1.4 | policy | snapshotFor を1回、要求をまたがない | なし |
| BR1.5 | policy | DSL の識別が食い違っても読み直さない | なし |
| BR1.6 | policy | 管理のメニューを含めない | なし |
| BR2.1 | authorization | テーブルの階層の主権限だけで判定、カラムは見ない | 応答に含めない |
| BR2.2 | calculation | テーブルと子の両方を持つ項目の3つの場合 | なし |
| BR2.3 | calculation | 子の無いまとまりをさかのぼって落とす | なし |
| BR2.4 | calculation | 親子と並びを保つ | なし |
| BR2.5 | calculation | 作業ロール無し・すべて NONE は空・NOTHING_VISIBLE | 誤りにしない |
| BR2.6 | authorization | DSL に無いテーブルは NONE として落ちる | なし |
| BR2.7 | constraint | 純粋な関数と6つの性質（jqwik） | 統合しない |
| BR3.1 | calculation | id は絞る前の位置の道（例 2.0） | なし |
| BR3.2 | policy | label は ja・en の組をそのまま | なし |
| BR3.3 | calculation | emptyReason の決め方 | なし |
| BR3.4 | policy | 識別・ロール・権限の値を載せない | なし |
| BR3.5 | calculation | navigable と table の組 | なし |
| BR4.1 | policy | 許した名前は resources の一覧のファイルから | なし |
| BR4.2 | validation | 一覧に無い・null・空は list、ログなし | 拒否しない |
| BR4.3 | constraint | 一覧のファイルの誤りで起動を止める | 起動の失敗 |
| BR4.4 | constraint | 一覧のファイルと画面の一覧の一致を画面のテストで確かめる | 統合しない |
| BR5.1 | validation | 引数 schema・table が無い・空なら拒否。長さでは拒否しない | 400 VALIDATION_FAILED |
| BR5.2 | authorization | DSL 無し・組が無い・NONE は同じ 403 | 403 ACCESS_DENIED |
| BR5.3 | policy | READ・FULL は組・表示名・主権限を 200 | なし |
| BR5.4 | policy | メニューに出ているかを見ない | なし |
| BR5.5 | policy | 403 は監査に残さず既存の WARN だけ | なし |
| BR6.1 | authorization | AUTHENTICATED の印、401、停止中は既存の拒否 | 401 |
| BR6.2 | constraint | 読み取りだけ | なし |
| BR7.1 | constraint | 3つの層と依存の向きを境界テストで固定 | 統合しない |
| BR7.2 | constraint | 結果の型と switch | なし |
| BR7.3 | policy | 監査なし、指標は既存だけ | なし |
| BR7.4 | policy | 個人に関する値を受け渡さない | なし |
