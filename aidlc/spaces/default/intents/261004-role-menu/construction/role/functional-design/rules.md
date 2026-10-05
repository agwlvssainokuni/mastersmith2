# 決まり — U4 role

出典の表記: FR・NFR・C は `requirements.md`、AC は `stories.md`、Q1〜Q9 は `functional-design-questions.md` の答え、SP は同じファイルの「この段で決める設計の要点」（まとめの確認で承認）、C4〜C10 は `contract-summary.md`、group-BR は `construction/group/functional-design/rules.md`、dsl-BR は `construction/dsl-v2/functional-design/rules.md`、TP は `team.md`、PM は `project.md`。

```yaml
rules:
  # BR1 ロールの名前（group-BR1 にそろえる）
  - id: BR1.1
    statement: ロールの名前は前後の空白（全角の空白を含む）を取り除いてから判定し、保存する
    category: validation
    applies_to: Role.name（作成・名前の変更・import の作成）
    trigger: 名前を受け取ったとき
    logic: IF 名前に前後の空白がある THEN 取り除いた値を使う
    violation: なし（取り除いた値で続ける）
    source: FR1.1・group の Q1 A
  - id: BR1.2
    statement: 名前の長さは取り除いた後のコードポイントの数で 1〜64
    category: validation
    applies_to: Role.name
    trigger: 作成・名前の変更・import の確かめ
    logic: IF 長さが 0 または 65 以上 THEN 拒否。64 ちょうどは受け付ける（サロゲートペアの文字も1つと数える）
    violation: 400 VALIDATION_FAILED（import では ROLE_TRANSFER_INVALID の INVALID_ROLE_NAME）。ロールは変わらない
    source: FR1.1・AC1.1.9・group-BR1.2
  - id: BR1.3
    statement: 制御文字を含む名前は拒否する
    category: validation
    applies_to: Role.name
    trigger: 作成・名前の変更・import の確かめ
    logic: IF 名前に制御文字（改行・タブなど）がある THEN 拒否
    violation: BR1.2 と同じ
    source: group-BR1.3
  - id: BR1.4
    statement: 名前の重なりは大文字と小文字をそろえた鍵で判定し、全角と半角は別の文字とする。鍵は Java の1か所の関数で作り通常の列に保存する
    category: constraint
    applies_to: Role.nameKey
    trigger: 作成・名前の変更・import の照らし合わせ
    logic: IF ほかのロールの nameKey が要求の名前の鍵と同じ THEN 名前の重なり。自分自身は比べの外（大文字と小文字だけの変更は受け付ける）
    violation: 409 ROLE_NAME_DUPLICATE（監査 FAILURE）。ロールは変わらない
    source: FR1.1・AC1.1.2・AC1.1.11・group-BR1.4・SP（group のレビュー R-04 の手当て。承認の場の直しで group の鍵も Java で作る同じ作りにそろえる、role のレビュー R-10）
  - id: BR1.5
    statement: 今と同じ名前への変更は変えるものが無い操作として拒否する
    category: validation
    applies_to: 名前の変更
    trigger: 取り除いた後の名前が今の名前と文字どおり同じとき
    logic: IF 新しい名前 = 今の名前 THEN 拒否
    violation: 409 ROLE_NO_CHANGE（監査 FAILURE、理由 NO_CHANGE）
    source: AC1.1.14・SM4 C

  # BR2 認可と入力
  - id: BR2.1
    statement: ロール・権限・割り当て・受け渡しの API は /api/admin/ の下に置き、管理者の印を持ち停止していない利用者だけが呼べ、すべて ADMIN に分類する
    category: authorization
    applies_to: C7 のすべての API
    trigger: 要求ごと
    logic: IF 未認証 THEN 401。IF 管理者の印が無い・停止中 THEN 403（既存の AdminPaths と管理者の印の決まり）。ロール・作業ロール・権限の設定では管理の可否は変わらない
    violation: 401 AUTHENTICATION_REQUIRED・403 ACCESS_DENIED
    source: FR1.3・NFR1.2・NFR1.3・C1・AC1.1.5・AC1.2.11・AC2.2.5・AC2.2.14・AC3.1.16
  - id: BR2.2
    statement: 作業ロールと自分の権限の API は /api/me/ の下に置き、ログインだけで呼べ、AUTHENTICATED に分類する。利用者を指す値を受け取らず、主体は要求の文脈から読む
    category: authorization
    applies_to: C8 のすべての API
    trigger: 要求ごと
    logic: IF 未認証 THEN 401。IF 停止中 THEN 既存のアクセストークンの認証で通らない。パラメーター・本文に他人を指す値があっても使わない
    violation: 401 AUTHENTICATION_REQUIRED
    source: FR6.2・NFR1.4・AC4.1.12・AC4.1.19・AC4.2.3・AC4.2.5
  - id: BR2.3
    statement: 要求の本文は決めた項目だけを受け、ほかの項目は反映しない
    category: validation
    applies_to: 作成・名前の変更（name）、割り当て（userId・groupId）、作業ロールの切り替え（roleId）、権限の保存（scope・entries）、消す（targets）
    trigger: 本文を受け取ったとき
    logic: 決めた項目だけを読み、ID・作成者・時刻・組み込みの印など足した項目は無視する。割り当ての本文は userId と groupId のちょうど一方
    violation: 足した項目は無視。必須の項目の欠け・型の誤り・両方あり・両方なしは 400 VALIDATION_FAILED
    source: AC1.1.10・AC2.2.11・AC1.2.10・TP
  - id: BR2.4
    statement: 道と本文の ID は正の整数として受け、存在しないものは理由ごとの code で拒否する
    category: validation
    applies_to: roleId・userId・groupId
    trigger: 要求を受けたとき
    logic: IF 整数でない・0 以下 THEN 400。IF ロールが無い THEN ROLE_NOT_FOUND。IF 利用者が無い THEN USER_NOT_FOUND。IF グループが無い THEN GROUP_NOT_FOUND（判定の順は BR3.6・BR6.9）
    violation: 400 VALIDATION_FAILED・404（監査 FAILURE。400 は残さない）
    source: AC1.1.12・AC2.2.10・group-BR2.3

  # BR3 ロールの管理
  - id: BR3.1
    statement: ロールの作成は名前だけを受け、作ったロールを 201 で返す
    category: policy
    applies_to: POST /api/admin/roles
    trigger: 作成の要求
    logic: 名前の規則（BR1）→ 重なり（BR1.4）→ 行を足す → ROLE_CREATED
    violation: BR1 のとおり
    source: FR1.1・AC1.1.1・C7
  - id: BR3.2
    statement: 利用者かグループへの割り当てが1つでも残るロールは削除できない
    category: constraint
    applies_to: DELETE /api/admin/roles/{roleId}
    trigger: 削除の要求
    logic: ロールの行を排他した後に、直接の利用者の割り当ての数とグループの割り当ての数を数え、IF どちらかが 1 以上 THEN 拒否
    violation: 409 ROLE_IN_USE（応答に assignedUsers・assignedGroups。氏名・メールアドレスは載せない。監査 FAILURE、detail は InUse）
    source: FR1.2・AC1.1.3・AC1.1.8
  - id: BR3.3
    statement: 削除は、そのロールの権限の設定と、そのロールを指す作業ロールの保存を同じトランザクションで消してからロールを消す
    category: policy
    applies_to: 削除
    trigger: BR3.2 を通ったとき
    logic: 権限の設定の行 → 作業ロールの保存の行 → ロールの行の順に消す。覚えている作業ロールは削除を止めない
    violation: なし
    source: AC1.1.4・AC1.1.13・AC4.1.14・SP
  - id: BR3.4
    statement: 一覧と ROLE_IN_USE の利用者とグループの数は直接の割り当ての数とする
    category: calculation
    applies_to: GET /api/admin/roles の userCount・groupCount、ROLE_IN_USE
    trigger: 一覧・削除の拒否
    logic: userCount = そのロールの UserRoleAssignment の数。groupCount = GroupRoleAssignment の数。グループ経由の利用者は数えない
    violation: なし
    source: AC1.1.8・SP
  - id: BR3.5
    statement: ロールの一覧は共通のページ送りに合わせ、1ページ 20 件で page だけを受け、ロールの ID の順に並べる
    category: policy
    applies_to: GET /api/admin/roles
    trigger: 一覧の要求
    logic: page を common.paging.Paging で読む（誤りは 400）。size は受け取らず、応答の size は 20
    violation: 400 VALIDATION_FAILED
    source: SP・Q1 A・group-BR7.1
  - id: BR3.7
    statement: ロール1件の読み取り（GET /api/admin/roles/{roleId}）を持ち、無ければ ROLE_NOT_FOUND で返す
    category: policy
    applies_to: GET /api/admin/roles/{roleId}
    trigger: 読み取りの要求
    logic: ADMIN に分類し、Role と同じ形（roleId・name・createdAt・updatedAt）を返す。監査に残さない
    violation: 404 ROLE_NOT_FOUND（監査なし）
    source: role-admin-ui の Q1 A（承認の場の直し）・契約 C7 との差
  - id: BR3.6
    statement: 名前の変更の拒否の判定は、入力 → ロールの有無 → 変えるものが無い → 名前の重なりの順に行う
    category: policy
    applies_to: PUT /api/admin/roles/{roleId}
    trigger: 名前の変更の要求
    logic: 最初に当たった理由で拒否する
    violation: 各理由の code
    source: SP・group と同じ順

  # BR4 権限の設定
  - id: BR4.1
    statement: 主権限はスキーマ・テーブル・カラムに、補助権限 CREATE・DELETE はスキーマ・テーブルにだけ設定でき、値は「設定なし」を含む
    category: validation
    applies_to: PermissionSetting・保存・import
    trigger: 保存・import の確かめ
    logic: main は null・NONE・READ・FULL。create・delete は null・true・false。IF カラムに create か delete がある THEN 拒否。IF 許していない値（ADMIN など）THEN 拒否
    violation: 400 VALIDATION_FAILED（import は ROLE_TRANSFER_INVALID の INVALID_VALUE・COLUMN_AUX_NOT_ALLOWED）。設定は変わらない
    source: FR2.1・FR3.1・AC1.2.10
  - id: BR4.2
    statement: 設定は対象の名前で持ち、値が1つでも設定されている対象だけ行を持つ
    category: constraint
    applies_to: PermissionSetting
    trigger: 保存・消す・import の適用
    logic: 下位の名前が無い階層は空の文字列。IF 対象の3つの値がすべて設定なし THEN 行を消す
    violation: なし
    source: FR2.4・Q2 A
  - id: BR4.3
    statement: 対象の名前は 1〜128 コードポイントで制御文字を含まず、大文字と小文字を区別して DSL の名前と完全一致で照らす
    category: validation
    applies_to: PermissionTarget
    trigger: 保存・消す・import の確かめ（木の読み取りの引数には長さの上限を置かない。BR4.12）
    logic: IF 長さが範囲外・制御文字あり THEN 拒否。照らし合わせで前後の空白を取り除かない
    violation: 400 VALIDATION_FAILED（import は INVALID_TARGET_NAME）
    source: SP
  - id: BR4.4
    statement: 権限の保存は scope（スキーマかテーブル）の中の、entries に載せた対象だけを置き換える差分の保存とする
    category: policy
    applies_to: PUT /api/admin/roles/{roleId}/permissions
    trigger: 保存の要求
    logic: IF entry が scope の外の対象 THEN 400。載せた対象を要求の値（null は設定なし）にする。載せていない対象は変えない。同じ対象が entries に2回あれば 400
    violation: 400 VALIDATION_FAILED
    source: SP・C7
  - id: BR4.5
    statement: 適用済みの DSL が無いときは、権限の設定の木を読めず、保存できない
    category: constraint
    applies_to: 木の API・保存
    trigger: ActiveDslModelProvider.current() が Absent のとき
    logic: IF Absent THEN 拒否（消すは受け付ける）
    violation: 409 DSL_NOT_APPLIED（保存は監査 FAILURE、木の読み取りは残さない）
    source: FR2.3・AC1.2.6・AC6.1.6
  - id: BR4.6
    statement: 今の DSL に無い対象には、値を設定する保存を受け付けず、設定なしへ戻す entry と消す操作だけを受け付ける
    category: constraint
    applies_to: 保存・POST .../permissions/clear
    trigger: 保存・消すの要求
    logic: IF entry の値のどれかが設定なしでない AND 対象が今の DSL に無い THEN 拒否
    violation: 409 PERMISSION_TARGET_NOT_IN_DSL（監査 FAILURE）。設定は変わらない
    source: FR2.4・AC1.2.7・AC1.2.13・C7
  - id: BR4.7
    statement: 今の DSL に無い対象の設定を消す操作は、DSL の有無によらず受け付ける
    category: policy
    applies_to: POST /api/admin/roles/{roleId}/permissions/clear
    trigger: 消すの要求
    logic: 指定の対象の行を消す。IF 消す行が1つも無い THEN 変えるものが無い。今の DSL にある対象を指したら 400
    violation: 409 ROLE_NO_CHANGE（監査 FAILURE）・400 VALIDATION_FAILED
    source: FR2.4・AC1.2.7（Should）
  - id: BR4.8
    statement: 保存で変わる対象が1つも無いときは、変えるものが無い操作として拒否する
    category: validation
    applies_to: 保存
    trigger: 排他の後に今の値と比べたとき
    logic: IF すべての entry の値が今の値と同じ THEN 拒否
    violation: 409 ROLE_NO_CHANGE（監査 FAILURE）
    source: AC1.2.17・SM4 C
  - id: BR4.9
    statement: 同じロールの同時の保存は後勝ちで、それぞれの保存が排他の後に読んだ前の値と後の値を監査に残す
    category: policy
    applies_to: 保存
    trigger: 2つの保存が重なったとき
    logic: ロールの行の排他で順に並べ（BR8.1）、後の保存は前の保存の確定した値を前の値として読む。どちらも成功
    violation: なし
    source: SM2 A・AC1.2.12
  - id: BR4.10
    statement: 権限の木は開いた階層の分だけを返す
    category: policy
    applies_to: GET /api/admin/roles/{roleId}/permissions/schemas・.../tables?schema=…・.../columns?schema=…&table=…
    trigger: 木の読み取り
    logic: 1段目はスキーマだけ、スキーマの道はそのテーブルだけ、テーブルの道はそのカラムだけを返す。今の DSL の対象と、設定だけがある対象（inCurrentDsl 偽）を名前で合わせる。今の DSL に無いスキーマ・テーブルの下も設定があれば返す。並びは DSL の順、今の DSL に無いものはその後に名前のコードポイントの順
    violation: ロールが無ければ 404 ROLE_NOT_FOUND
    source: FR11.2・AC1.2.1・AC1.2.7・SP
  - id: BR4.11
    statement: 木の各節に明示の値・実効の値・継承の元・メニューに出るか・今の DSL にあるかを載せる
    category: calculation
    applies_to: PermissionNodes
    trigger: 木の読み取り
    logic: effective は BR5.1・BR5.2 で求める。inheritedFrom は値を決めた階層（EXPLICIT・TABLE・SCHEMA、どこも設定なしなら DEFAULT。主権限と補助権限で別に求める）。inMenu はテーブルの節だけで、実効の主権限が NONE でないとき真。今の DSL に無い節の effective は NONE・不可
    violation: なし
    source: FR11.3・AC1.2.2〜AC1.2.4・AC1.2.16
  - id: BR4.12
    statement: 権限の木の読み取りと自分の権限の API は、スキーマ名・テーブル名を道ではなく問い合わせの引数で受け、長さの上限で拒否しない
    category: validation
    applies_to: GET /api/admin/roles/{roleId}/permissions/{schemas,tables,columns}・GET /api/me/permissions/{schemas,tables,columns}
    trigger: 木の読み取りの要求
    logic: tables は schema を、columns は schema と table を必須の引数で受ける。名前に / ・ .. ・ ; ・ % を含んでも要求の検査に当たらない。今の DSL にも設定にも無い名前は空の items を返す
    violation: 引数の欠け・空は 400 VALIDATION_FAILED（長さでは拒否しない。DSL の名前に上限が無いため）
    source: 承認の場の直し（navigation の置き場の問い合わせとそろえる）・契約 C7・C8 との差

  # BR5 実効の権限の解決（契約 C5）
  - id: BR5.1
    statement: 実効の主権限は、カラム・テーブル・スキーマの順に見て最初に設定なしでない値とし、すべて設定なしなら NONE
    category: calculation
    applies_to: 解決の純粋な関数
    trigger: 解決のたび
    logic: column ?? table ?? schema ?? NONE
    violation: なし
    source: FR2.2・AC1.2.2・AC1.2.3・AC1.2.9
  - id: BR5.2
    statement: 実効の補助権限は、テーブル・スキーマの順に見て最初に設定なしでない値とし、すべて設定なしなら不可。CREATE と DELETE は互いに影響しない。カラムの補助権限はそのテーブルの値
    category: calculation
    applies_to: 解決の純粋な関数
    trigger: 解決のたび
    logic: table ?? schema ?? false を CREATE と DELETE で別々に求める
    violation: なし
    source: FR3.2・AC1.2.4・AC1.2.9
  - id: BR5.3
    statement: 有効な作業ロールが無い・適用済みの DSL が無い・対象が今の DSL に無いときは NONE・不可を返す
    category: policy
    applies_to: EffectivePermissionResolver
    trigger: 解決のたび
    logic: IF workRole が null OR DSL が Absent OR 名前が DSL に無い THEN NONE・不可。DSL に無い対象の設定は使わない
    violation: なし（誤りにしない）
    source: FR4.4・FR5.5・FR8.3・AC1.2.8・AC2.2.3・AC4.1.18・AC6.1.6
  - id: BR5.4
    statement: 解決は要求ごとに内部DB の割り当て・作業ロールの保存・設定から求め、トークン・画面から送られた値・要求をまたぐキャッシュを使わない
    category: policy
    applies_to: EffectivePermissionResolver
    trigger: 解決のたび
    logic: snapshotFor(userId) は1回の要求で有効な作業ロールの設定をまとめて1回読み、その要求の中だけで使う
    violation: なし
    source: ADR-003・FR4.4・FR8.2・PM Mandated・AC1.2.5・AC2.1.9・AC2.2.3
  - id: BR5.5
    statement: 実効の権限は作業ロールの設定だけで決め、ほかのロールの設定を合算しない。管理者の印も使わない
    category: policy
    applies_to: 解決
    trigger: 解決のたび
    logic: 有効な作業ロールの PermissionSetting だけを読む
    violation: なし
    source: FR5.1・AC4.1.6・AC4.1.21
  - id: BR5.6
    statement: 継承の解決は DB と時刻に依存しない純粋な関数にし、性質ベースのテスト（jqwik）を当てる
    category: policy
    applies_to: 解決の関数
    trigger: テスト
    logic: 性質: BR5.1・BR5.2 の式に等しい、作業ロール以外のロールの足し外しで結果が変わらない。失敗時の乱数の種を記録する
    violation: なし
    source: NFR6.2・AC1.2.9・AC4.1.21・TP

  # BR6 割り当て
  - id: BR6.1
    statement: 割り当ての相手は登録の終わった利用者かグループのどちらか一方で、利用停止の利用者にも割り当てられる
    category: validation
    applies_to: POST /api/admin/roles/{roleId}/assignments
    trigger: 割り当ての要求
    logic: IF 利用者の行が無い THEN USER_NOT_FOUND（招待中の人を含む）。停止中でも受け付ける
    violation: 404 USER_NOT_FOUND（監査 FAILURE）
    source: FR4.2・AC2.2.16・要件との差 D7
  - id: BR6.2
    statement: 1人の利用者・1つのグループに複数のロールを割り当てられる
    category: policy
    applies_to: 割り当て
    trigger: 割り当て
    logic: 組（ロール・相手）ごとに1行
    violation: なし
    source: FR4.2
  - id: BR6.3
    statement: すでに割り当てた組の割り当ては変えるものが無い操作として拒否する
    category: validation
    applies_to: 割り当て
    trigger: 排他の後の判定・主キーの違反
    logic: IF 組の行がある THEN 拒否。同時の追加で主キーの違反になったときも同じ理由に読み替える（BR8.5）
    violation: 409 ROLE_NO_CHANGE（監査 FAILURE）
    source: AC2.2.15・SM4 C
  - id: BR6.4
    statement: 割り当てていない組の外しは変えるものが無い操作として拒否する（存在しない利用者・グループも同じ）
    category: validation
    applies_to: DELETE .../assignments/users/{userId}・.../groups/{groupId}
    trigger: 外しの要求
    logic: ロールの有無 → 組の行が無ければ拒否
    violation: 409 ROLE_NO_CHANGE（監査 FAILURE）
    source: AC2.2.15・SP（group のメンバーの外しと同じ読み）
  - id: BR6.5
    statement: 利用者のロールは直接の割り当てと所属グループへの割り当ての和で、同じロールは1つとして扱う
    category: calculation
    applies_to: 有効な作業ロール・C8 の roles・C7 の割り当ての一覧・利用者のロールの読み取り
    trigger: 読み取りのたび
    logic: 直接の割り当て ∪（GroupMembershipQuery.groupIdsOfUser で得たグループへの割り当て）。出どころは DIRECT と GROUP を並べる。ロールの側から利用者を並べる割り当ての一覧は BR6.11
    violation: なし
    source: FR4.3・AC2.2.2・AC2.2.7・AC2.2.8・AC2.1.9・AC2.1.10
  - id: BR6.6
    statement: グループへの割り当て・外しは、ロールの行の排他の後に group の lockForAssignment でグループの行を排他してから行う
    category: constraint
    applies_to: グループへの割り当て・外し
    trigger: 割り当て・外し
    logic: IF Busy THEN 巻き戻して GROUP_BUSY。IF exists が偽 THEN 割り当ては GROUP_NOT_FOUND、外しは組の行が無いため ROLE_NO_CHANGE（BR6.4 にそろえる。role のレビュー R-03）
    violation: 409 GROUP_BUSY（監査なし）・404 GROUP_NOT_FOUND（割り当て、監査 FAILURE）・409 ROLE_NO_CHANGE（外し、監査 FAILURE）
    source: group-BR5.3・Q3 A
  - id: BR6.7
    statement: 割り当ての表からロール・利用者・グループへの外部キーを最後の守りとして置く
    category: constraint
    applies_to: UserRoleAssignment・GroupRoleAssignment
    trigger: 書き込み
    logic: 削除の制限の外部キー
    violation: 違反は業務の拒否に読み替える（BR8.5）
    source: group-BR5.4
  - id: BR6.8
    statement: 割り当て・外しは、その利用者（グループならメンバー全員）の次の要求から、作業ロールに選べるロールと実効の権限に効く
    category: policy
    applies_to: 割り当て・外し
    trigger: 確定の後の次の要求
    logic: BR5.4 により次の要求で読み直される。変更の前に出したアクセストークンのままで効く
    violation: なし
    source: FR4.4・AC2.2.3・AC2.2.4・TP の★
  - id: BR6.9
    statement: 割り当ての拒否の判定は、入力 → ロールの有無 → 相手の有無 → すでに割り当て済みの順に行う
    category: policy
    applies_to: 割り当て
    trigger: 割り当ての要求
    logic: 最初に当たった理由で拒否する
    violation: 各理由の code
    source: SP
  - id: BR6.10
    statement: 管理者は、自分の作業ロールに無い権限を持つロールも自分や他人に割り当てられ、自分のロールをすべて外せる
    category: authorization
    applies_to: 割り当て・外し
    trigger: 割り当て・外し
    logic: 操作する人の業務データの権限では縛らない。最後の有効な管理者の保護は管理者の印で数える既存の決まりのまま
    violation: なし
    source: SM5 A・AC2.2.12・AC2.2.13・C1
  - id: BR6.11
    statement: 割り当ての一覧のグループ経由の利用者は、group の GroupMembershipQuery.memberUserIds でまとめて1回で読む
    category: policy
    applies_to: GET /api/admin/roles/{roleId}/assignments
    trigger: 割り当ての一覧の読み取り
    logic: そのロールを割り当てたグループの ID の集合を渡し、グループごとの利用者の ID の集合を受ける（存在しないグループは含まれない）。直接の割り当てと合わせ、利用者ごとに出どころを並べる。グループごとに問い合わせを重ねない
    violation: なし
    source: 承認の場の直し（role のレビュー R-01）・契約 C4 との差・AC2.2.7

  # BR7 作業ロール
  - id: BR7.1
    statement: 「最初のロール」はロールの ID の小さい順の先頭とし、作業ロールの選択肢・ロールの一覧・書き出しも同じ順に並べる
    category: calculation
    applies_to: 有効な作業ロール・C8 の roles
    trigger: 読み取り
    logic: 利用者のロール（BR6.5）をロールの ID の昇順に並べる
    violation: なし
    source: Q1 A・FR5.5・AC4.1.16
  - id: BR7.2
    statement: 有効な作業ロールは、保存した作業ロールが利用者のロールに含まれればそれ、含まれなければ最初のロール、ロールが無ければ無し
    category: calculation
    applies_to: EffectivePermissionResolver.effectiveWorkRole
    trigger: 読み取りのたび
    logic: IF 保存がありロールの集合に含まれる THEN 保存のロール ELSE IF ロールがある THEN BR7.1 の先頭 ELSE null
    violation: なし
    source: C5・FR5.5・AC2.2.4・AC4.1.14・AC4.1.16
  - id: BR7.3
    statement: 有効な作業ロールの読み替えは保存を書き換えず、監査に残さない
    category: policy
    applies_to: 読み取り
    trigger: 読み替えたとき
    logic: 読み取りの要求で状態を変えない
    violation: なし
    source: C5・AC4.1.15・D11
  - id: BR7.4
    statement: 割り当てを外した後に同じロールがまた割り当てられたら、覚えていた作業ロールに戻ってよい
    category: policy
    applies_to: WorkRoleSelection
    trigger: 再び割り当てたとき
    logic: 割り当ての変更では保存を書き換えない（BR7.3）ため、保存のロールがまたロールの集合に含まれれば有効な作業ロールになる
    violation: なし
    source: Q9 A
  - id: BR7.5
    statement: 作業ロールは利用者のロールの中からだけ切り替えられ、自分に無いロールと存在しないロールは同じ応答で拒否する
    category: authorization
    applies_to: PUT /api/me/work-role
    trigger: 切り替えの要求
    logic: 要求の時点の利用者のロール（BR6.5）を読み、IF roleId が含まれない THEN 拒否
    violation: 409 ROLE_NOT_ASSIGNED（監査 FAILURE、targetRoleId は要求の値）。保存は変わらない
    source: FR5.3・AC4.1.3・AC4.1.11・C8
  - id: BR7.6
    statement: 保存がすでに選んだロールを指しているときだけ、切り替えは何も変えない成功とし監査に残さない。選んだロールが今の有効な作業ロールと同じでも、保存が無い・保存が別のロール（読み替え中）を指すときは保存を書く
    category: policy
    applies_to: PUT /api/me/work-role
    trigger: 選んだロールが利用者のロールに含まれるとき（BR7.5 を通った後）
    logic: IF 保存の roleId = 選んだ roleId THEN 書かずに 204（監査なし）。ELSE 保存を選んだ roleId に書き、WORK_ROLE_SWITCHED（SUCCESS）を残す（有効な作業ロールが変わらない場合も、利用者が明示で選んだロールを保存に残す変更として残す。detail の storedBeforeRoleId に書く前の保存）。これにより、利用者が明示で選んだ後は保存と有効な作業ロールが一致し、作業ロール以外のロールの足し外しで作業ロールが変わらない（AC4.1.21 の性質が成り立つ。BR7.4 で戻るのは、保存が有効な作業ロールと違う読み替え中の状態で、保存のロールがまた割り当てられたときだけ）
    violation: なし
    source: AC4.1.13・SM4 C・AC4.1.21・承認の場の直し（role のレビュー R-02）
  - id: BR7.7
    statement: 切り替えは保存の行を書き（無ければ作り）、次のログインでも同じロールで始まる
    category: policy
    applies_to: WorkRoleSelection
    trigger: 切り替えの成功
    logic: 利用者の行に roleId と時刻を書く → WORK_ROLE_SWITCHED（SUCCESS、detail は WorkRoleSwitch）
    violation: なし
    source: FR5.2・FR5.4・AC4.1.1・AC4.1.2
  - id: BR7.8
    statement: 作業ロールの切り替えは行の排他を取らず、割り当ての外しと重なっても読みの読み替えで割り当ての外のロールを使わない
    category: constraint
    applies_to: 切り替え
    trigger: 切り替えと外しが重なったとき
    logic: 切り替えは確かめの後に保存を書く。外しが先に確定していれば ROLE_NOT_ASSIGNED、後なら両方成功し、次の要求で BR7.2 が最初のロールに読み替える
    violation: 409 ROLE_NOT_ASSIGNED
    source: Q3 A・NFR3.1・AC4.1.5

  # BR8 排他と待ち
  - id: BR8.1
    statement: ロールを変える管理の操作は、最初にロールの行を排他してから確かめと書き換えを行う。複数のロールはロールの ID の昇順に排他する
    category: constraint
    applies_to: 名前の変更・削除・権限の保存・消す・割り当てと外し・import の適用
    trigger: 操作の開始
    logic: 入力の判定 → ロールの行の排他 → 待ち合わせの口 → 拒否の判定 → 書き換え → 監査の出来事。排他の前に書き込みは無い
    violation: ロールが無ければ ROLE_NOT_FOUND
    source: Q3 A・AC1.1.6・AC1.2.12
  - id: BR8.2
    statement: ロールの行とグループの行の両方を排他するときは、ロール → グループの順に固定する
    category: constraint
    applies_to: グループへの割り当て・外し
    trigger: 割り当て・外し
    logic: 順を固定して待ちの循環を作らない
    violation: なし
    source: Q3 A
  - id: BR8.3
    statement: 排他の待ちが上限（既存と同じ 3 秒）を超えたら、巻き戻して ROLE_BUSY で断り、監査に残さない
    category: policy
    applies_to: BR8.1 の操作
    trigger: 待ちの上限切れ
    logic: 巻き戻しの印を先に付け、DB に触れずに Busy を返す
    violation: 409 ROLE_BUSY。状態は変わらない
    source: Q3 A・useradmin の BUSY の扱い
  - id: BR8.4
    statement: 一意の鍵の待ち（同じ名前の同時の作成・同じ名前への同時の変更・同じ組の同時の割り当て）の上限切れも ROLE_BUSY に読み替える
    category: policy
    applies_to: 作成・名前の変更・割り当て・import の作成
    trigger: 一意の鍵の待ちの上限切れ
    logic: 既存の RowLockFailures で分類する
    violation: 409 ROLE_BUSY（監査なし）
    source: SP（group のレビュー R-02 の手当て）
  - id: BR8.5
    statement: 一意の制約・主キー・外部キーの違反は業務の拒否に読み替え、巻き戻した後の書き込みの無い新しいトランザクションで失敗の出来事を出す
    category: policy
    applies_to: 作成・名前の変更・割り当て・import の作成
    trigger: 違反の例外
    logic: IF nameKey の一意の違反 THEN ROLE_NAME_DUPLICATE。IF 割り当ての主キーの違反 THEN ROLE_NO_CHANGE。IF 外部キーの違反 THEN 相手の NOT_FOUND。巻き戻しの印が付いたトランザクションで確定させない
    violation: 各 code（監査 FAILURE）
    source: SP（group のレビュー R-01 の手当て）
  - id: BR8.6
    statement: 同時の重なりのテストのため、本番では何もしない待ち合わせの口を排他の直後に置く
    category: policy
    applies_to: role.service
    trigger: 排他の直後
    logic: 本番の実装は何もしない。テストは @Primary の実装で待ち合わせる。スレッドの数に頼らない
    violation: なし
    source: TP・UserAdminBarrier と同じ形

  # BR9 権限の YAML の受け渡し
  - id: BR9.1
    statement: 権限の YAML は version 1 と、ロールの名前をキーにした入れ子の対応表の形とする
    category: validation
    applies_to: RoleTransferDocument
    trigger: 書き出し・確かめ・適用
    logic: ルートは version と roles だけ。ロールは schemas、スキーマとテーブルは main・create・delete と下位の対応表、カラムは主権限の値だけ。書かない項目は設定なし
    violation: 中身の誤りは ROLE_TRANSFER_INVALID
    source: Q4 A
  - id: BR9.2
    statement: 確かめと適用は本文を application/yaml で送り、適用の指紋は要求のヘッダー X-Role-Transfer-Fingerprint で送る
    category: policy
    applies_to: POST /api/admin/role-transfer/check・apply
    trigger: 要求を受けたとき
    logic: IF 本文の型が application/yaml でない THEN 415。IF 適用で指紋のヘッダーが無い・16 進数 64 文字でない THEN 400
    violation: 415 UNSUPPORTED_MEDIA_TYPE・400 VALIDATION_FAILED
    source: Q8 A（契約 C7 との差）
  - id: BR9.3
    statement: 読み込みは SafeYamlReader だけで行い、上限は深さ 10・別名 0・展開後の節 1,000,000・大きさ 10 MiB（仮）を渡す
    category: validation
    applies_to: 確かめ・適用
    trigger: 読み込み
    logic: Rejected の区分を errors の reason に写す。TOO_LARGE は 413。上限ちょうどは受け付ける
    violation: 400 ROLE_TRANSFER_INVALID・413 PAYLOAD_TOO_LARGE。部品の例外の文を含めない
    source: Q5 A・FR7.4・ADR-007・AC3.1.7・AC3.1.13
  - id: BR9.4
    statement: 確かめと適用の道には、本文の大きさの上限を RequestBodyLimitRoute で足す
    category: constraint
    applies_to: POST /api/admin/role-transfer/check・apply
    trigger: 要求を受けたとき
    logic: role.web の設定で道と上限（BR9.3 の大きさ）と PAYLOAD_TOO_LARGE を登録する。common.web の本体は変えない
    violation: 413 PAYLOAD_TOO_LARGE
    source: Q8 A
  - id: BR9.5
    statement: 形は正しいが中身が誤りの YAML は、確かめの段で位置と種類の一覧つきで拒否する
    category: validation
    applies_to: 確かめ・適用
    trigger: 読めた後の検証
    logic: 版の誤り・必須の項目の欠け・知らない項目・値の誤り・カラムの CREATE と DELETE・ロールの名前の規則の違反・ロールの鍵の重なり・対象の名前の誤りを、最大 100 件まで errors に入れる。入れた値は載せない
    violation: 400 ROLE_TRANSFER_INVALID。設定は変わらない。監査に残さない
    source: AC3.1.12・FR7.4
  - id: BR9.6
    statement: 読み込むロールは名前の鍵で今のロールと照らし、当たれば置き換え（名前は変えない）、当たらなければ作る。ファイルに無いロールは残す
    category: policy
    applies_to: 確かめ・適用
    trigger: 一覧を作るとき
    logic: 鍵が同じ今のロール → REPLACE、無ければ CREATE。untouchedRoles にファイルに無いロールを並べる
    violation: なし
    source: FR7.2・RF3・AC3.1.2・AC3.1.3
  - id: BR9.7
    statement: 置き換えるロールの設定は、ファイルの中の対象の状態にそろえ、ファイルに無い対象の設定は設定なしになる
    category: policy
    applies_to: 適用
    trigger: 適用
    logic: 今の行のうちファイルに無いものは CLEARED、ファイルにあり値が違うものは CHANGED、無いものは ADDED
    violation: なし
    source: AC3.1.3
  - id: BR9.8
    statement: 今の DSL に無い対象の名前の設定も名前で持つ設定として読み込み、一覧で今の DSL に無いと示す
    category: policy
    applies_to: 確かめ・適用
    trigger: 一覧を作るとき
    logic: inCurrentDsl を名前で照らして付け、notInDsl を数える
    violation: なし
    source: FR7.5・AC3.1.6
  - id: BR9.9
    statement: 指紋は作り直した変わる点の一覧と適用中の DSL の識別から決まった順で作る SHA-256 とする
    category: calculation
    applies_to: TransferPlan.fingerprint
    trigger: 確かめ・適用
    logic: ロールの鍵の順 → 対象の名前のコードポイントの順に、ロールの名前・CREATE か REPLACE・置き換えるロールの ID・対象・前後の値・今の DSL にあるかを並べ、dslHash（無ければ空）を足して SHA-256 の 16 進数の小文字にする
    violation: なし
    source: C7・FR7.3a・AC3.1.4・SP
  - id: BR9.10
    statement: 適用は、同じ YAML を検証し直して一覧を作り直し、指紋が確かめの指紋と違えば拒否する
    category: constraint
    applies_to: 適用
    trigger: 適用の要求
    logic: 置き換えるロールの行をロールの ID の昇順に排他した後に一覧を作り直す。IF 指紋が違う THEN 拒否
    violation: 409 ROLE_TRANSFER_STALE（監査 FAILURE）。設定は変わらない
    source: FR7.3a・AC3.1.4・AC3.1.11
  - id: BR9.11
    statement: 適用は1つのトランザクションでまとめて確定し、途中で失敗したらロールも設定も変わらず、成功の監査は残らない
    category: constraint
    applies_to: 適用
    trigger: 適用
    logic: 作成・置き換えをすべて同じトランザクションで行い、確定の後に1件の監査の出来事
    violation: 想定外の失敗は 500（巻き戻し）
    source: NFR3.2・AC3.1.8
  - id: BR9.12
    statement: 変わる点が1つも無い適用は、変えるものが無い操作として拒否する
    category: validation
    applies_to: 適用
    trigger: 作り直した一覧が空のとき
    logic: IF 一覧に CREATE も変わる対象も無い THEN 拒否
    violation: 409 ROLE_NO_CHANGE（監査 FAILURE）
    source: AC3.1.14・SM4 C
  - id: BR9.13
    statement: 適用済みの DSL が無いときは確かめと適用を拒否し、書き出しは DSL の有無によらず今ある設定を書く
    category: constraint
    applies_to: 確かめ・適用・書き出し
    trigger: DSL が Absent のとき
    logic: 確かめ・適用は YAML を読む前に拒否する
    violation: 409 DSL_NOT_APPLIED（適用は監査 FAILURE、確かめは残さない）
    source: FR7.3b・AC3.1.5・AC3.1.1
  - id: BR9.14
    statement: 書き出しは、ロールを ID の順、スキーマ・テーブル・カラムを名前のコードポイントの順に並べ、設定のある対象だけを書く
    category: policy
    applies_to: GET /api/admin/role-transfer/export
    trigger: 書き出し
    logic: グループ・メンバー・割り当ては書かない。今の DSL に無い対象も書く。別名・タグを使わない。添付のファイル名を付ける
    violation: なし
    source: FR7.1・AC3.1.1・AC3.1.14・AC3.1.15・Q1 A
  - id: BR9.15
    statement: 確かめは設定を変えず、指紋を保存しない
    category: policy
    applies_to: 確かめ
    trigger: 確かめ
    logic: 一覧と指紋を返すだけ
    violation: なし
    source: FR7.3・C7（CQ1 A）・AC3.1.2

  # BR10 グループの問う口の実装（契約 C4）
  - id: BR10.1
    statement: group の GroupDeletionGuard を role が実装し、canDelete はグループへの直接の割り当ての数で答える
    category: policy
    applies_to: GroupDeletionGuard.canDelete
    trigger: グループの削除の確かめ
    logic: IF 数 = 0 THEN Allowed ELSE Blocked(数)
    violation: なし
    source: ADR-002・C4・group-BR6.1・AC2.1.3
  - id: BR10.2
    statement: assignedRoleCounts は渡したグループの ID のすべてにキーを返し、割り当てが無ければ 0 とする
    category: calculation
    applies_to: GroupDeletionGuard.assignedRoleCounts
    trigger: グループの一覧・削除
    logic: 1回の問い合わせでまとめて数える
    violation: なし
    source: group-BR6.2・group の Q3 A
  - id: BR10.3
    statement: B3 の仮の実装を B5 で本物に置き換え、仮の実装が残っていないことを B5 の終わりの条件に入れる
    category: policy
    applies_to: role パッケージの GroupDeletionGuard の実装
    trigger: B5
    logic: 実装は1つだけ。group には既定の実装を置かない
    violation: なし
    source: group-BR6.3・group の Q4 A

  # BR11 監査（契約 C10、group-BR8 にそろえる）
  - id: BR11.1
    statement: 成功した変える操作はすべて監査に残す
    category: policy
    applies_to: 作成・名前の変更・削除・権限の保存・消す・割り当て・外し・import の適用・作業ロールの切り替え
    trigger: 確定の後
    logic: 操作ごとの種類で SUCCESS
    violation: なし
    source: FR12.1・AC1.1.1・AC1.1.4・AC1.2.5・AC2.2.1・AC3.1.3・AC4.1.1
  - id: BR11.2
    statement: 業務の理由で拒否した変える操作は、その操作の種類で FAILURE と理由を付けて残す
    category: policy
    applies_to: 404・409 の業務の拒否（ROLE_BUSY・GROUP_BUSY を除く）
    trigger: 拒否
    logic: 書き込みなしで確定し、失敗の出来事を出す。違反の読み替えは BR8.5
    violation: なし
    source: FR12.2・group の Q5 A・SM4 C
  - id: BR11.3
    statement: 入力の誤り・排他の待ちの上限切れ・読み取り・import の確かめ・書き出し・何も変えない作業ロールの切り替え・有効な作業ロールの読み替えは監査に残さない
    category: policy
    applies_to: 400・ROLE_BUSY・GROUP_BUSY・GET・check・export・BR7.3・BR7.6 の書かない場合
    trigger: それらの要求
    logic: 出来事を出さない
    violation: なし
    source: group-BR8.3・Q7 A・AC4.1.13・AC4.1.15
  - id: BR11.4
    statement: 監査の行の対象の列を操作に合わせて埋める
    category: policy
    applies_to: RoleAuditEvent
    trigger: 出来事を作るとき
    logic: ロールの操作は targetRoleId。利用者への割り当て・外しは targetUserId も、グループへは targetGroupId も埋める。作業ロールの切り替えは actor と targetUserId を同じ ID、targetRoleId に切り替えた先（拒否は要求の値）。import の適用は対象の列を空にし detail に書く
    violation: なし
    source: C10・FR12.1
  - id: BR11.5
    statement: detail は決めた型（RoleAuditDetail）だけから作る JSON の文字列で、16,384 文字を超えないように要約へ切り替え、途中で切らない
    category: constraint
    applies_to: RoleAuditDetail
    trigger: 出来事を作るとき
    logic: IF 全件の JSON が上限を超える THEN 要約の型（PermissionChangesSummary・TransferSummary）にする
    violation: なし
    source: group-BR8.5・group-BR8.6・SP
  - id: BR11.6
    statement: 権限の保存の detail は変わった対象ごとの前後の値を入れる
    category: policy
    applies_to: ROLE_PERMISSION_CHANGED
    trigger: 保存・消すの成功
    logic: 対象・前の値・後の値（主権限・CREATE・DELETE）。超えるときは数と先頭の何件か
    violation: なし
    source: AC1.2.5・AC1.2.12・SM2 A
  - id: BR11.7
    statement: import の適用は1回を1行にし、detail はファイルの SHA-256・指紋・ロールごとの要約とする
    category: policy
    applies_to: ROLE_TRANSFER_APPLIED
    trigger: 適用の成功と業務の拒否
    logic: ロールごとに名前・ID・作る／置き換え・足す／変わる／消える／今の DSL に無いの数。前後の値の全件は残さない
    violation: なし
    source: Q6 A・FR12.3
  - id: BR11.8
    statement: detail と監査の行に秘密と個人に関する値を入れない
    category: constraint
    applies_to: RoleAuditEvent
    trigger: 出来事を作るとき
    logic: パスワード・トークン・パスワードと招待のトークンのハッシュ値・メールアドレス・氏名を入れない。入れるのは ID・ロールとグループの名前・対象の名前・権限の値・数・ファイルの SHA-256・指紋だけ
    violation: なし
    source: FR12.3・PM Forbidden・group-BR8.7
  - id: BR11.9
    statement: 監査の種類と失敗の理由の名前は 32 文字以内で、一度決めたら変えない
    category: constraint
    applies_to: AuditEventType・AuditFailureReason
    trigger: 足すとき
    logic: 単体テストで全件の長さを確かめる
    violation: テストの失敗
    source: NFR5.1・AC1.1.16・TP

  # BR12 漏えい
  - id: BR12.1
    statement: 割り当ての一覧と利用者のロールの読み取りに出る氏名・メールアドレスは、すべての層で伏せ字の型のまま受け渡す
    category: constraint
    applies_to: RoleAssignments・UserRoleView の利用者の値
    trigger: 読み取り
    logic: user.service の伏せ字の型（UserSummary・RedactedText）で受け、文字列にするのは応答の DTO の組み立ての時だけ
    violation: なし
    source: NFR1.6・AC2.2.6・PM の学び
  - id: BR12.2
    statement: 応答にパスワードのハッシュ値・招待のトークンのハッシュ値・ロックの判定の内部の値を含めない
    category: constraint
    applies_to: role のすべての応答
    trigger: 応答
    logic: 応答の DTO に項目を持たない
    violation: なし
    source: FR4.5・PM Forbidden・AC2.2.6・AC2.2.16
  - id: BR12.3
    statement: DB の例外・YAML の部品の例外の文は外へ出さず、受けた所でクラスの名前だけをログに出す。YAML の木の中身もログに出さない
    category: constraint
    applies_to: role の service と repository
    trigger: 例外・読み込み
    logic: TraceAspect の対象の層の外へ例外の文を出さない
    violation: なし
    source: PM の学び・PM Forbidden・AC3.1.13

  # BR13 誤りの code
  - id: BR13.1
    statement: 業務の拒否は理由ごとの code と固定の状態コードで返す
    category: policy
    applies_to: role.domain.RoleProblemTypes・RoleProblemTypeCatalog
    trigger: 拒否
    logic: ROLE_NOT_FOUND 404・ROLE_NAME_DUPLICATE 409・ROLE_IN_USE 409・ROLE_NO_CHANGE 409・ROLE_NOT_ASSIGNED 409・ROLE_BUSY 409・PERMISSION_TARGET_NOT_IN_DSL 409・DSL_NOT_APPLIED 409・ROLE_TRANSFER_INVALID 400・ROLE_TRANSFER_STALE 409 を role.domain に定義し、結果の型を web の層が switch で尽くして BusinessException に変える
    violation: なし
    source: C7・TP Code Style
  - id: BR13.2
    statement: 共通とほかの機能の code は使い回し、role で重ねて定義しない
    category: policy
    applies_to: VALIDATION_FAILED・AUTHENTICATION_REQUIRED・ACCESS_DENIED・PAYLOAD_TOO_LARGE・UNSUPPORTED_MEDIA_TYPE・USER_NOT_FOUND・GROUP_NOT_FOUND・GROUP_BUSY
    trigger: 拒否
    logic: USER_NOT_FOUND は user.domain.UserProblemTypes、GROUP_ は group.domain の定義を使う
    violation: 同じ code の二重の定義は起動で止まる
    source: TP Code Style・group-BR10.2
```

## 要約

| ID | 種類 | 決まり | 違反のとき |
|---|---|---|---|
| BR1.1〜BR1.5 | validation・constraint | ロールの名前の規則（group と同じ） | 400・409 ROLE_NAME_DUPLICATE・ROLE_NO_CHANGE |
| BR2.1〜BR2.4 | authorization・validation | 管理の API は ADMIN、自分の API は AUTHENTICATED、決めた項目だけ、ID の確かめ | 401・403・400・404 |
| BR3.1〜BR3.7 | policy・constraint | 作成・削除（割り当てが残れば拒否、設定と作業ロールの保存も消す）・一覧と数・判定の順・1件の読み取り | 409 ROLE_IN_USE |
| BR4.1〜BR4.12 | validation・policy | 設定の値と持ち方・名前の照らし合わせ・差分の保存・DSL が無い・今の DSL に無い・後勝ち・木の API（名前は問い合わせの引数） | 400・409 DSL_NOT_APPLIED・PERMISSION_TARGET_NOT_IN_DSL・ROLE_NO_CHANGE |
| BR5.1〜BR5.6 | calculation・policy | 継承の解決・NONE の場合・要求ごと・合算しない・性質ベースのテスト | なし |
| BR6.1〜BR6.11 | validation・policy | 割り当ての相手・和・重ね・外し・グループの排他・外部キー・次の要求から・自分自身・グループのメンバーのまとめての読み取り | 404・409 ROLE_NO_CHANGE・GROUP_BUSY |
| BR7.1〜BR7.8 | calculation・policy | 最初のロール（ID の順）・有効な作業ロール・読み替えは書かない・戻ってよい・切り替え（保存が同じときだけ何もしない） | 409 ROLE_NOT_ASSIGNED |
| BR8.1〜BR8.6 | constraint・policy | ロールの行の排他・順・ROLE_BUSY・違反の読み替えと失敗の監査・待ち合わせの口 | 409 ROLE_BUSY |
| BR9.1〜BR9.15 | validation・policy | YAML の形・送り方・上限・照らし方・置き換え・指紋・適用・書き出し | 400 ROLE_TRANSFER_INVALID・409 ROLE_TRANSFER_STALE・413 |
| BR10.1〜BR10.3 | policy | グループの問う口の実装と仮の実装の置き換え | なし |
| BR11.1〜BR11.9 | policy・constraint | 監査の線引き・対象・detail・import の要約・名前の長さ | なし |
| BR12.1〜BR12.3 | constraint | 伏せ字の型・応答の値・例外の文 | なし |
| BR13.1・BR13.2 | policy | code と状態コード、使い回し | なし |
