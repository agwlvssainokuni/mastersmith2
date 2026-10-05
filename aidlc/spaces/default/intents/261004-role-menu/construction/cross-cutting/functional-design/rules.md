# 業務の決まり（Business Rules）— U1 cross-cutting

出典: `unit-of-work.md`（U1）、`unit-of-work-story-map.md`、`requirements.md`（NFR1.1・NFR1.3・NFR4.1〜NFR4.3・FR10.3・FR10.5・C3〜C5）、`components.md`、`contract-summary.md`（C1・C2）、ADR-008、この段の答え（Q1: B、Q2: A、Q3: A、Q4: A、Q5: A、Q6: A、まとめの確認）、`team.md`（Code Style・Testing Posture）。

群: BR1 API の分類（バックエンド）、BR2 画面の機能どうしの import の制限、BR3 ログアウトの口（既存の違反の直し）、BR4 共有の木、BR5 画面の登録の型。

```yaml
rules:
  # ---- BR1 API の分類 ----
  - id: BR1.1
    statement: 本番のすべてのコントローラーの口は、効く分類の印を1つだけ持つ
    category: validation
    applies_to: ApiAccess・本番のコントローラーの口（RequestMapping を持つ方法）
    trigger: 構造の検査（全体の置き場の ArchUnit のテスト）
    logic: IF 口の方法にも、その口のクラスにも印が無い THEN 落とす。IF 方法とクラスの両方に印がある THEN 落とす（どちらが効くかを読み手に迷わせない）。ELSE 方法の印、無ければクラスの印をその口の分類とする
    violation: テストが落ち、印の無い口・二重の口のクラス名と方法名を並べる
    source: NFR1.3・AC1.1.15・C1・ADR-008
  - id: BR1.2
    statement: ADMIN の印と管理者だけの道（AdminPaths）は両方向で一致する
    category: authorization
    applies_to: ApiAccess・AdminPaths.isAdminOnly
    trigger: 構造の検査
    logic: 口の道（クラスと方法の RequestMapping をつないだもの。置き換えの形 ${名前:既定値} は既定値で読む）ごとに、IF 印が ADMIN で AdminPaths.isAdminOnly が偽 THEN 落とす。IF AdminPaths.isAdminOnly が真で印が ADMIN でない THEN 落とす
    violation: テストが落ち、食い違う口と道を並べる
    source: C1・ADR-008・AC1.1.15
  - id: BR1.3
    statement: AUTHENTICATED の口は /api/ の下で、管理者だけの道の外にある
    category: validation
    applies_to: ApiAccess（AUTHENTICATED）
    trigger: 構造の検査
    logic: IF 印が AUTHENTICATED で、道が /api/ で始まらない、または AdminPaths.isAdminOnly が真 THEN 落とす（/api/** の既定「ログインだけ」は /api/ の下にだけ効くため）
    violation: テストが落ちる
    source: C1・NFR1.3
  - id: BR1.4
    statement: 分類の印と実際の安全の決まりの判定が一致することを、要求を送らずに実行時に確かめる
    category: authorization
    applies_to: 本番のすべての口と Spring Security の判定（WebInvocationPrivilegeEvaluator）
    trigger: 全体の置き場の結合テスト（XxxIT、本番の設定で起動）
    logic: アプリが持つ口の一覧（RequestMappingHandlerMapping）のうち、口のクラスが本番のクラスのものだけを対象にし（W2 の静的な検査と同じ集合。2つの集合が一致することも確かめる）、道と方法ごとに、道の変数は見本の値（例 1）に置き換え、3つの主体で判定にかける。主体は、未ログイン、管理者でないログイン中の利用者（AuthenticatedUser の admin() が偽）、管理者（AuthenticatedUser の admin() が真）。ログイン中の2つは、本番の入口と同じ AuthenticatedUserToken に AuthenticatedUser を入れて作る（AdminAuthorizationManager が主体の AuthenticatedUser の admin() で判断するため）。IF PUBLIC THEN 3つとも通る。IF AUTHENTICATED THEN 未ログインは止まり、管理者でない利用者と管理者は通る。IF ADMIN THEN 未ログインと管理者でない利用者は止まり、管理者は通る。方法の指定が無い口は GET・POST・PUT・PATCH・DELETE のすべてで確かめる
    violation: テストが落ち、口・方法・利用者の種類・期待と実際を並べる
    source: Q1 B・C1・NFR1.3・承認の場の直し（レビュー R-01・R-02）
  - id: BR1.5
    statement: 実行時の検査の部品が使えないときは、実際に要求を送る形に切り替える
    category: policy
    applies_to: BR1.4 の検査
    trigger: コード生成の最初の試し
    logic: IF WebInvocationPrivilegeEvaluator が今の版で Bean として得られない、または与えた利用者で判定できない THEN 同じ組（3つの主体と期待）を MockMvc で実際に要求を送って確かめる。判定は「安全の決まりが止めたか（入口の処理・拒否の処理が返す 401・403）」で行い、口の中の業務の拒否（例 リフレッシュの Cookie が無いときの 401）と見分ける（見分け方はコード生成で確かめる）。監査に行が残る口は、送る本文を空にするなど副作用の無い形にする。切り替えたことと理由をコード生成の記録に書く
    violation: —（切り替えの手順）
    source: Q1 B（C への切り替え）
  - id: BR1.6
    statement: 検査の対象は本番のすべてのコントローラーの口で、道を問わない
    category: constraint
    applies_to: BR1.1〜BR1.4 の検査
    trigger: 構造の検査・実行時の検査
    logic: 対象 = 本番のクラス（テストのクラスは含めない）の RequestMapping を持つ口のすべて（/api/ の外の /error も含む）。テストだけの口（設定の値で有効にするもの）と、テスト用の決まり（PublicApiTestRules など）を有効にした状態では検査しない
    violation: —（範囲の定義）
    source: Q2 A・まとめの確認
  - id: BR1.7
    statement: コントローラーの口でない入口（actuator・画面の静的配信）は印の対象外とし、扱いを決まりとして固定する
    category: policy
    applies_to: /actuator/**・画面の静的配信（/**）
    trigger: 設計の決まり（守りは既存の設定と既存のテスト）
    logic: actuator は Web に health だけを出し、/actuator/health だけを誰でも届くようにする（discovery は無効）。画面の静的配信と SPA の index.html への読み替えは誰でも届く（/api/ と /actuator/ の下は読み替えない）。IF これらを変える THEN その単位が既存の公開の範囲のテストを直し、この決まりを見直す
    violation: 既存の公開の範囲のテストが落ちる
    source: Q2 A・C1（対象外の入口）
  - id: BR1.8
    statement: 注釈は common.security に置き、どの機能にも依存しない。検査は全体の置き場に置く
    category: constraint
    applies_to: ApiAccess・ApiAccessLevel・構造の検査
    trigger: 構造の検査・設計
    logic: IF common.security の注釈が機能のパッケージ（access など）に依存する THEN 落とす。検査（テスト）は AdminPaths を読むが、本体の依存の向きは変えない
    violation: 構造の検査が落ちる
    source: ADR-008・C1
  - id: BR1.9
    statement: 既存の 34 の口に分類の印を付ける（U1 の持ち物）。U2〜U5 が足す・変える口は各単位が付ける
    category: policy
    applies_to: 既存のコントローラー 10 個
    trigger: B2 のコード生成
    logic: 印の値は functional-spec.md 3節の表のとおり（PUBLIC 9・AUTHENTICATED 3・ADMIN 22）。IF U2〜U5 が口を足す THEN その単位が印を付け、BR1.1〜BR1.4 の検査がそれを確かめる
    violation: BR1.1 の検査が落ちる
    source: 単位の一覧 R-03・AC1.1.15・ADR-008
  - id: BR1.10
    statement: 本体に手を入れる機能のうち境界テストが無いものに境界テストを足し、既存の構造の検査は緩めない
    category: policy
    applies_to: access・user（印を付けるために web に手が入る）、既存の ArchitectureTest と境界テスト
    trigger: B2 のコード生成
    logic: AccessBoundaryArchitectureTest と UserBoundaryArchitectureTest を、今の依存をそのまま書いて足す。common は機能ではなく共通部品のため境界テストを足さない（全体の ArchitectureTest が受け持つ）。既存の境界テストは書き換えず足すだけ。IF 手を入れるパッケージが packagesJudgedByTotal にある THEN 下限を満たして一覧から外す（今の見込みでは当たるものは無い）
    violation: —（作業の決まり。既存のテストを緩めるときは計画で承認を得る）
    source: C4・team.md Code Style と Testing Posture・bolt-plan の B2

  # ---- BR2 画面の機能どうしの import の制限 ----
  - id: BR2.1
    statement: 画面の機能は、ほかの機能のファイルを直接 import しない
    category: constraint
    applies_to: frontend/src/features/<featureId>/ の本体のソース（*.test.ts・*.test.tsx を除く）
    trigger: ESLint（no-restricted-imports）
    logic: 機能のディレクトリの一覧から機能ごとの決まりを作る。IF 機能 A のファイルが、A の外の兄弟の機能 B を指す（相対の道で features/B に着く）THEN 誤り。A の中の下位のディレクトリ、app/、shared/、外部のパッケージは許す
    violation: ESLint の誤りで ./gradlew verify が落ちる
    source: C5・team.md Code Style・Q3 A
  - id: BR2.2
    statement: 共有の置き場は骨組みと機能を読まない
    category: constraint
    applies_to: frontend/src/shared/ の本体のソース（テストを除く）
    trigger: ESLint（no-restricted-imports）
    logic: IF shared/ のファイルが app/ または features/ を import する THEN 誤り
    violation: ESLint の誤りで ./gradlew verify が落ちる
    source: Q3 A・features/README.md の shared の決まり
  - id: BR2.3
    statement: テストのファイルは制限の対象外とする
    category: policy
    applies_to: "*.test.ts・*.test.tsx"
    trigger: ESLint の設定
    logic: テストのファイルは画面を組むためにほかの機能の登録や authSession を読んでよい（今の 9 ファイル）。IF テストでないファイル（testing/ の部品など）が他の機能を読む THEN BR2.1 で誤り
    violation: —（範囲の定義）
    source: Q3 A
  - id: BR2.4
    statement: 制限を入れる時点で、本体の違反を 0 件にする
    category: policy
    applies_to: features/registration/useRegistration.ts の ../auth/authSession の import（1件）
    trigger: B2 のコード生成（この Intent で最初に画面に手を入れる Bolt）
    logic: BR3 の口に置き換えて import を消し、ESLint の制限を入れた状態で誤りが 0 件であること
    violation: ESLint の誤り
    source: C5・team.md Code Style・Q4 A

  # ---- BR3 ログアウトの口 ----
  - id: BR3.1
    statement: ログイン状態の提供元は、任意でログアウトの手続きを持つ
    category: policy
    applies_to: LoginStateProvider.logout
    trigger: auth の登録
    logic: auth は登録で logout を渡す（中身は今の authSession の logout）。authSession の持ち主は auth のまま変えない
    violation: auth の登録のテストで logout が無ければ落ちる
    source: Q4 A
  - id: BR3.2
    statement: 機能は骨組みの口 useLogout からだけログアウトを呼ぶ
    category: policy
    applies_to: app/login-state の useLogout と、それを使う機能（今は registration）
    trigger: 機能がログアウトを要するとき
    logic: useLogout は Promise を返す関数を返す。IF 提供元に logout がある THEN それを呼ぶ。IF 無い THEN 何もせずに終わる。IF logout が失敗（拒否）した THEN 外へ出さずに終わる（画面の側のトークンの破棄は auth が必ず行う）
    violation: —（失敗を外へ出さない）
    source: Q4 A・C5
  - id: BR3.3
    statement: 登録の完了の画面の「別の利用者でログイン中」の動きは変えない
    category: constraint
    applies_to: features/registration/useRegistration.ts の logoutAndContinue
    trigger: 既存の違反の直し
    logic: 呼ぶ先を ../auth/authSession の logout から useLogout に替えるだけで、未ログインの知らせで確かめへ移る今の流れは同じにする
    violation: 既存の RegistrationPage のテストが落ちる
    source: C5・Q4 A

  # ---- BR4 共有の木 ----
  - id: BR4.1
    statement: 節は表示名を文字として出し、HTML として描かない
    category: validation
    applies_to: SharedTreeNode.label・SharedTreeBadge.text
    trigger: 描画
    logic: 文字の差し込みだけで描く。IF 表示名に < > & " ' を含む THEN そのまま文字として見える
    violation: 画面のテストが落ちる
    source: team.md N 階層のメニュー（信頼できない入力）・NFR4.1
  - id: BR4.2
    statement: 節は「選ぶボタン」と「開閉のボタン」に分ける
    category: policy
    applies_to: SharedTreeView の節
    trigger: 描画・操作
    logic: 選ぶボタン（表示名）は onSelect(id) を呼び、開閉は変えない。開閉のボタンは hasChildren が真の節だけに出し、onToggle(id, 次の状態) を呼び、選びは変えない
    violation: 画面のテストが落ちる
    source: Q5 A
  - id: BR4.3
    statement: 選んでいる節は aria-current で、開閉の状態は aria-expanded で示す
    category: validation
    applies_to: 選ぶボタン・開閉のボタン
    trigger: 描画
    logic: IF 節の id が selectedId と同じ THEN その選ぶボタンに aria-current="true"。ELSE 付けない。開閉のボタンには aria-expanded（真偽）を常に付ける。selectedId が読み込んだ節に無いときは、どの節にも付けない
    violation: 画面のテストが落ちる
    source: C2・AC1.2.14・NFR4.2
  - id: BR4.4
    statement: 開閉の状態は呼ぶ側が持つ
    category: policy
    applies_to: expandedIds・onToggle
    trigger: 開閉の操作
    logic: 木は expandedIds に入っている節を開いて描く。開閉のボタンの操作は onToggle を呼ぶだけで、木の中で状態を持たない
    violation: —
    source: C2
  - id: BR4.5
    statement: 子は開いたときに初めて読み、一度読んだら持ち続ける
    category: calculation
    applies_to: loadChildren・SharedTreeChildState
    trigger: 節が開いた状態になったとき（操作か、expandedIds に初めから入っていたとき）
    logic: IF 節が開いていて状態が notLoaded THEN loading にして loadChildren(id) を呼ぶ。成功したら loaded として子を持つ。IF 閉じて開き直す THEN 読み直さない。IF 呼ぶ側が nodes を新しい配列に替える THEN 持っていた子をすべて捨て、開いている節は読み直す
    violation: —
    source: まとめの確認（設計の要点）・mockups.md S4・AC1.2.1
  - id: BR4.6
    statement: 子の読み込みの失敗はその節の下だけに出し、再試行できる
    category: policy
    applies_to: SharedTreeChildState（failed）
    trigger: loadChildren の拒否
    logic: IF loadChildren が拒否された THEN 状態を failed にし、その節の下に失敗の文と再試行のボタンを出す。再試行で loading に戻して呼び直す。例外を外へ出さず、木のほかの節は使えるまま
    violation: —
    source: まとめの確認（設計の要点）・construction.md Error Handling
  - id: BR4.7
    statement: 古くなった読み込みの結果は捨てる
    category: constraint
    applies_to: loadChildren の結果
    trigger: 読み込み中に nodes が替わった、または部品が消えた
    logic: IF 結果が届いた時点で、呼んだときの nodes と今の nodes が違う、または部品が消えている THEN 結果を使わない
    violation: —
    source: BR4.5 から導く（AC なし）
  - id: BR4.8
    statement: 開閉のボタンの読み上げの名前に節の表示名を含める
    category: validation
    applies_to: 開閉のボタン
    trigger: 描画
    logic: 名前 = labels.expand(label)（閉じているとき）または labels.collapse(label)（開いているとき）
    violation: 画面のテストが落ちる
    source: まとめの確認（設計の要点）・AC1.2.14・AC5.1.1 と同じ形
  - id: BR4.9
    statement: 木の文言は呼ぶ側が labels で渡す
    category: policy
    applies_to: SharedTreeLabels
    trigger: 描画
    logic: 木は文言を持たず、呼ぶ側が表示言語（日本語・英語）に合わせて渡す
    violation: —
    source: まとめの確認（設計の要点）・NFR4.3
  - id: BR4.10
    statement: ARIA の tree の役割と矢印のキーの移動は使わない
    category: constraint
    applies_to: SharedTreeView
    trigger: 描画・キーボード操作
    logic: 入れ子の ul と button で表す。Tab で移り、Enter・Space で押す。tree・treeitem の役割と矢印のキーの決まりは持たない
    violation: 画面のテストが落ちる
    source: C2・interaction-spec.md（PermissionTree）・make-you-chic-ui 5bf1ffe の Sidebar と同じ形
  - id: BR4.11
    statement: 印（badges）は色だけでなく文字で意味を示す
    category: validation
    applies_to: SharedTreeBadge
    trigger: 描画
    logic: 印は text を必ず描く。tone は見た目の補いだけ
    violation: 画面のテストが落ちる
    source: NFR4.1・AC4.2.6・AC1.2.16 の考え方
  - id: BR4.12
    statement: 子が0件の節を開いたときは「子が無い」旨を出す
    category: policy
    applies_to: SharedTreeChildState（loaded で0件）
    trigger: 読み込みの成功
    logic: IF 読んだ子が0件 THEN その節の下に labels.empty を出す
    violation: —
    source: BR4.5 から導く（AC なし）

  # ---- BR5 画面の登録の型 ----
  - id: BR5.1
    statement: サイドバーの項目の登録は区画（section）を必ず持つ
    category: validation
    applies_to: SidebarItemRegistration.section
    trigger: 型の検査（TypeScript）と登録の検査（validateRegistrations）
    logic: IF section が無い、または SidebarSection の値でない THEN 起動時の登録の検査が問題として止める
    violation: RegistrationError（problems に項目の id）
    source: Q6 A・C2・FR10.3・FR10.5
  - id: BR5.2
    statement: アイコンは任意で、無ければ骨組みが既定のアイコンを当てる
    category: policy
    applies_to: SidebarItemRegistration.icon
    trigger: 骨組みの組み立て（U7）
    logic: IF icon がある THEN その名前を使う。ELSE 骨組みの既定（今は list）
    violation: —
    source: Q6 A
  - id: BR5.3
    statement: 管理の区画の項目は管理者にだけ出す設定である
    category: authorization
    applies_to: SidebarItemRegistration（section ADMIN）
    trigger: 登録の検査
    logic: IF section が ADMIN で visibleWhen が ADMIN でない THEN 問題として止める
    violation: RegistrationError
    source: Q6 A・FR10.3・AC5.3.2
  - id: BR5.4
    statement: アイコンの名前は make-you-chic-ui の許した名前だけ
    category: validation
    applies_to: SidebarItemRegistration.icon
    trigger: 登録の検査
    logic: IF icon があり、make-you-chic-ui のアイコンの一覧に無い THEN 問題として止める（型は IconName だが、登録は実行時に読むため実行時にも確かめる）
    violation: RegistrationError
    source: Q6 A・team.md N 階層のメニュー（icon を許す名前の一覧と照らす）
  - id: BR5.5
    statement: 登録による出し分けは表示の制御で、サーバー側の判定の代わりにしない
    category: authorization
    applies_to: section・visibleWhen
    trigger: 設計の決まり
    logic: 管理の API は今までどおりサーバー側の管理者の印で守る（BR1.2・BR1.4）。登録の区画と visibleWhen は見せ方だけを決める
    violation: —
    source: NFR1.1・FR10.4・PM Mandated
  - id: BR5.6
    statement: 区画の見出しの文言・区画の並び・AppShell への組み立ては骨組み（U7）が持つ
    category: policy
    applies_to: SidebarSection
    trigger: 設計の境界
    logic: U1 は型と登録の検査だけを広げる。今ある4件の登録（admin・dsl・invitation・useradmin）には section を ADMIN として足し、アイコンは足さない（U6・U7 で決めてよい）
    violation: —
    source: Q6 A・単位の一覧 R-02・C2
```

## 要約

| ID | 決まり | 種類 | 出典 |
|---|---|---|---|
| BR1.1 | 口ごとに効く分類の印は1つだけ | validation | NFR1.3・AC1.1.15 |
| BR1.2 | ADMIN と AdminPaths は両方向で一致 | authorization | C1・ADR-008 |
| BR1.3 | AUTHENTICATED は /api/ の下で管理者の道の外 | validation | C1 |
| BR1.4 | 印と安全の決まりを、要求を送らずに3つの主体（未ログイン・管理者でない利用者・管理者）で実行時に照らす | authorization | Q1 B・R-01 |
| BR1.5 | 判定の部品が使えなければ要求を送る形に切り替える | policy | Q1 B |
| BR1.6 | 対象は本番のすべてのコントローラーの口 | constraint | Q2 A |
| BR1.7 | actuator と静的配信は対象外として扱いを固定 | policy | Q2 A |
| BR1.8 | 注釈は common.security、機能に依存しない | constraint | ADR-008 |
| BR1.9 | 既存の 34 の口に印を付ける | policy | R-03・AC1.1.15 |
| BR1.10 | access・user に境界テストを足し、既存は緩めない | policy | C4・team.md |
| BR2.1 | 機能どうしの直接の import を止める | constraint | C5・Q3 A |
| BR2.2 | shared は app・features を読まない | constraint | Q3 A |
| BR2.3 | テストのファイルは対象外 | policy | Q3 A |
| BR2.4 | 本体の違反を 0 件にする | policy | C5 |
| BR3.1 | 提供元に任意の logout を足し auth が渡す | policy | Q4 A |
| BR3.2 | 機能は useLogout からだけログアウトする | policy | Q4 A |
| BR3.3 | 登録の完了の画面の動きは変えない | constraint | C5 |
| BR4.1 | 表示名は文字として出す | validation | team.md |
| BR4.2 | 選ぶボタンと開閉のボタンに分ける | policy | Q5 A |
| BR4.3 | aria-current と aria-expanded | validation | C2・AC1.2.14 |
| BR4.4 | 開閉の状態は呼ぶ側が持つ | policy | C2 |
| BR4.5 | 子は開いたときに読み、持ち続ける | calculation | まとめの確認・AC1.2.1 |
| BR4.6 | 読み込みの失敗は節の下に出し再試行 | policy | まとめの確認 |
| BR4.7 | 古い読み込みの結果は捨てる | constraint | BR4.5 から |
| BR4.8 | 開閉のボタンの名前に表示名を含める | validation | AC1.2.14 |
| BR4.9 | 文言は labels で受け取る | policy | NFR4.3 |
| BR4.10 | tree の役割と矢印のキーは使わない | constraint | C2 |
| BR4.11 | 印は文字で意味を示す | validation | NFR4.1 |
| BR4.12 | 子が0件なら「子が無い」旨 | policy | BR4.5 から |
| BR5.1 | 登録の項目は section を必ず持つ | validation | Q6 A |
| BR5.2 | icon は任意、無ければ既定 | policy | Q6 A |
| BR5.3 | section ADMIN は visibleWhen ADMIN | authorization | Q6 A・FR10.3 |
| BR5.4 | icon は許した名前だけ | validation | Q6 A・team.md |
| BR5.5 | 出し分けはサーバー側の判定の代わりにしない | authorization | NFR1.1 |
| BR5.6 | 見出し・並び・組み立ては U7 | policy | Q6 A・R-02 |
