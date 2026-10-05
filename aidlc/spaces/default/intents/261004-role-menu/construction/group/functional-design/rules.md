# 業務の決まり（Business Rules）— U3 group

出典: `unit-of-work.md`（U3）、`unit-of-work-story-map.md`（US2.1・US2.2）、`requirements.md`（FR4.1・FR4.1a・FR4.3〜FR4.5・FR11.1・FR12.1〜FR12.3・NFR1.1〜NFR1.6・NFR3.1・NFR5.1・C1・C2）、`components.md`（GroupManagement）、`contract-summary.md`（C1・C4・C6・C10）、ADR-001・ADR-002・ADR-006、`stories.md`（US2.1 と「前提と読み方」）、この段の答え（Q1〜Q6 すべて A、まとめの確認）、`team.md`（Code Style・Testing Posture）、`project.md`（Forbidden・学び）。

群: BR1 名前、BR2 認可と入力、BR3 メンバー、BR4 削除、BR5 排他と同時の重なり、BR6 単位の境界の口、BR7 一覧と詳細、BR8 監査、BR9 漏えい、BR10 誤りの応答。

```yaml
rules:
  # ---- BR1 名前（Q1: A） ----
  - id: BR1.1
    statement: グループの名前は前後の空白を取り除いてから判定し、保存する
    category: validation
    applies_to: GroupName（作成・名前の変更）
    trigger: 作成・名前の変更の要求を受けたとき
    logic: IF 名前の前後に空白（全角の空白を含む Unicode の空白）がある THEN 取り除いた値を以後の判定と保存に使う。IF 取り除いた後が空 THEN 拒否する
    violation: 400 VALIDATION_FAILED（項目 name、理由は空）。監査に残さない。状態は変わらない
    source: FR4.1・AC2.1.2・Q1
  - id: BR1.2
    statement: 名前の長さは取り除いた後でコードポイントの数で 1〜64
    category: validation
    applies_to: GroupName
    trigger: 作成・名前の変更
    logic: IF コードポイントの数が 64 を超える THEN 拒否する。64 ちょうどは受け付ける
    violation: 400 VALIDATION_FAILED（項目 name、理由は長すぎ）。監査に残さない
    source: FR4.1・AC2.1.2・Q1
  - id: BR1.3
    statement: 制御文字を含む名前は拒否する
    category: validation
    applies_to: GroupName
    trigger: 作成・名前の変更
    logic: IF 取り除いた後の名前に制御文字（改行・タブ・そのほかの Unicode の制御文字）が1つでもある THEN 拒否する
    violation: 400 VALIDATION_FAILED（項目 name、理由は使えない文字）。監査に残さない
    source: Q1・NFR1.4（信頼できない入力）
  - id: BR1.4
    statement: 名前の重なりは大文字と小文字を区別せずに判定し、全角と半角は別の文字とする
    category: constraint
    applies_to: Group.nameKey
    trigger: 作成・名前の変更
    logic: 鍵は Java の GroupName だけが作り（言語に依らない小文字化）、通常の列 nameKey に保存して一意の制約を置く（DB の生成列にしない）。書く前に、同じトランザクションの中で鍵を読んで判定する。IF ほかのグループの nameKey が要求の名前の鍵と同じ THEN 名前の重なりとして拒否する。自分自身の nameKey は比べの外（大文字と小文字だけの変更は受け付ける）。事前の判定の後に同時の作成・変更で起きた一意の違反だけを BR5.7 の経路に回す
    violation: 409 GROUP_NAME_DUPLICATE。監査に FAILURE（GROUP_NAME_DUPLICATE）で残る。状態は変わらない
    source: FR4.1・AC2.1.2・Q1・Q5
  - id: BR1.5
    statement: 今と同じ名前への変更は変えるものが無い操作として拒否する
    category: policy
    applies_to: 名前の変更
    trigger: 名前の変更
    logic: IF 取り除いた後の名前が今の名前と文字どおり同じ THEN 拒否する（大文字と小文字だけが違えば変更として受け付ける）
    violation: 409 GROUP_NO_CHANGE。監査に FAILURE（NO_CHANGE）で残る
    source: AC2.1.13・SM4 C

  # ---- BR2 認可と入力 ----
  - id: BR2.1
    statement: グループの管理の API は管理者の印を持ち停止していない利用者だけが呼べ、すべて ADMIN に分類する
    category: authorization
    applies_to: /api/admin/groups の下のすべての口
    trigger: 要求ごと
    logic: すべての口に ApiAccess（ADMIN）を付ける。IF 未認証 THEN 401。IF 管理者の印が無い、または停止中 THEN 403（既存の AdminPaths の決まりと判定。ロール・作業ロールは判定に使わない）
    violation: 401 AUTHENTICATION_REQUIRED・403 ACCESS_DENIED（403 は既存の ACCESS_DENIED の監査）。状態は変わらない
    source: FR4.1・FR11.1・NFR1.1〜NFR1.3・C1・AC2.1.6・契約 C1・C6
  - id: BR2.2
    statement: 要求の本文は決めた項目だけを受け、ほかの項目は反映しない
    category: validation
    applies_to: 作成・名前の変更（name だけ）、メンバーの追加（userId だけ）
    trigger: 要求を受けたとき
    logic: IF 本文に ID・時刻・作成者などの許していない項目がある THEN それらを読まずに捨て、決めた項目だけで処理する。IF 決めた項目が無い・型が違う THEN 拒否する
    violation: 決めた項目の誤りは 400 VALIDATION_FAILED（監査に残さない）。足した項目は黙って捨てる
    source: NFR1.4・team.md（一括代入の防止）
  - id: BR2.3
    statement: 道と本文の ID は正の整数として受け、存在しないものは理由ごとの code で拒否する
    category: validation
    applies_to: groupId・userId
    trigger: 要求を受けたとき
    logic: IF 整数でない THEN 既存の型の誤りの扱い（400）。IF グループが無い THEN GROUP_NOT_FOUND。IF メンバーの追加で利用者が無い THEN USER_NOT_FOUND
    violation: 400・404 GROUP_NOT_FOUND・404 USER_NOT_FOUND。404 は監査に FAILURE で残る（BR8.2）。状態は変わらない
    source: AC2.1.11・AC2.2.10・NFR1.4（IDOR）・Q5

  # ---- BR3 メンバー ----
  - id: BR3.1
    statement: メンバーに足せるのは登録の終わった利用者だけ
    category: constraint
    applies_to: GroupMembership.userId
    trigger: メンバーの追加
    logic: IF 利用者の行が無い（招待中で登録が終わっていない人を含む） THEN 拒否する
    violation: 404 USER_NOT_FOUND。監査に FAILURE（USER_NOT_FOUND）で残る
    source: AC2.1.11・components.md・要件との差 D7
  - id: BR3.2
    statement: 利用停止の利用者もメンバーに足せる
    category: policy
    applies_to: GroupMembership
    trigger: メンバーの追加
    logic: IF 利用者が停止中 THEN 拒否せずに足す（停止中の人のロールは解決では効くが、停止中は入口で拒否される）
    violation: なし
    source: AC2.1.11
  - id: BR3.3
    statement: すでにメンバーの利用者の追加は変えるものが無い操作として拒否する
    category: policy
    applies_to: メンバーの追加
    trigger: メンバーの追加
    logic: IF 同じグループと利用者の組がすでにある THEN 拒否する（同時の追加で主キーの違反になったときも同じ。BR5.5）
    violation: 409 GROUP_NO_CHANGE。監査に FAILURE（NO_CHANGE）で残る
    source: AC2.1.13・SM4 C・契約 C6
  - id: BR3.4
    statement: メンバーでない利用者の外しは変えるものが無い操作として拒否する
    category: policy
    applies_to: メンバーの外し
    trigger: メンバーの外し
    logic: IF その組が無い（存在しない利用者を含む） THEN 拒否する
    violation: 409 GROUP_NO_CHANGE。監査に FAILURE（NO_CHANGE）で残る
    source: AC2.1.13・SM4 C・契約 C6（外しの応答に USER_NOT_FOUND は無い）
  - id: BR3.5
    statement: メンバーの判定の順はグループの有無、利用者の有無、メンバーかの順
    category: policy
    applies_to: メンバーの追加・外し
    trigger: メンバーの追加・外し
    logic: 追加は グループの有無（排他の結果）→ 利用者の有無 → すでにメンバーか。外しは グループの有無 → メンバーか。最初に当たった理由で拒否する
    violation: 当たった理由の code
    source: 契約 C6・まとめの確認（拒否の判定の順）
  - id: BR3.6
    statement: メンバーの変更は、その利用者の次の要求から所属として読まれる
    category: policy
    applies_to: GroupMembershipQuery.groupIdsOfUser
    trigger: U4 が利用者のロールの和を求めるとき
    logic: 所属は要求ごとに内部DB から読み、写しを持たない。IF メンバーを外した THEN 確定の後の次の読み取りでその所属は出ない（作業ロールの読み替えは U4）
    violation: なし
    source: FR4.3・FR4.4・AC2.1.9・AC2.1.10・ADR-003

  # ---- BR4 削除 ----
  - id: BR4.1
    statement: メンバーかロールの割り当てが残るグループは削除できない
    category: constraint
    applies_to: Group の削除
    trigger: 削除
    logic: グループの行を排他した後、IF メンバーの数が 1 以上、または問う口の答えが Blocked THEN 拒否する
    violation: 409 GROUP_IN_USE。グループ・メンバー・割り当ては変わらない。監査に FAILURE（GROUP_IN_USE）で残る
    source: FR4.1a・AC2.1.3・ADR-002・契約 C4・C6
  - id: BR4.2
    statement: 使用中の拒否の応答に残りのメンバーの数とロールの数を載せる
    category: calculation
    applies_to: GROUP_IN_USE の応答
    trigger: BR4.1 で拒否したとき
    logic: 応答の members にメンバーの数、assignedRoles に問う口の数（Allowed なら 0）を入れる。メンバーの氏名・メールアドレスは載せない
    violation: なし
    source: AC2.1.7・契約の GROUP_IN_USE
  - id: BR4.3
    statement: 削除では、メンバーが残っていても問う口に必ず聞く
    category: policy
    applies_to: 削除
    trigger: 削除
    logic: メンバーの数と問う口の答えの両方を求めてから可否を決める（数を応答と監査に出すため）
    violation: なし
    source: BR4.2・まとめの確認
  - id: BR4.4
    statement: メンバーも割り当ても無いグループは削除され、行は残らない
    category: policy
    applies_to: Group の削除
    trigger: 削除
    logic: IF メンバーが 0 かつ問う口が Allowed THEN グループの行を消す。監査の detail に消したグループの名前を残す
    violation: なし
    source: FR4.1a・AC2.1.4

  # ---- BR5 排他と同時の重なり（Q2: A） ----
  - id: BR5.1
    statement: グループを変える操作は、最初にそのグループの行を排他する
    category: constraint
    applies_to: 名前の変更・削除・メンバーの追加・外し
    trigger: 操作のトランザクションの始め
    logic: 入力の判定（BR1.1〜BR1.3）の後、書き込みの前に、対象のグループの行を排他する。IF 行が無い THEN GROUP_NOT_FOUND。排他を持ったまま確かめと書き換えを1つのトランザクションで行う
    violation: GROUP_NOT_FOUND（BR2.3）
    source: Q2・AC2.1.5・NFR3.1・ADR-006
  - id: BR5.2
    statement: 排他の待ちが上限を超えたら巻き戻して GROUP_BUSY で断る（行の排他と、一意の鍵・主キーの待ちの両方）
    category: policy
    applies_to: BR5.1・BR5.3 の行の排他と、作成・名前の変更（別の名前への変更を含む）・メンバーの追加の書き込みでの一意の鍵（nameKey）・主キーの待ち
    trigger: 待ちの上限（既存の行の排他と同じ 3 秒）を超えたとき。H2 では、同じ鍵を未確定のトランザクションが持つと、重なった側は違反ではなく待ちの上限切れの例外になりうる
    logic: IF 行の排他を取れない、または書き込みの一意の鍵・主キーの待ちが上限を超えた THEN トランザクションを巻き戻し、その後に DB に触れずに断る。例外の区分けは既存の RowLockFailures と同じ考え方で行う。業務のログは排他の種類だけの WARN を1回。同じ名前の同時の作成と、同じ名前への同時の変更で、違反になるか待ちの上限切れになるかは NFR 設計の捨ての試しのコードで確かめる（承認の場の直し R-02）
    violation: 409 GROUP_BUSY。監査に残さない。状態は変わらない
    source: Q2・Q5・useradmin の BUSY の扱い
  - id: BR5.3
    statement: グループへのロールの割り当て・外し（U4）も、group の口でグループの行を排他してから行う
    category: constraint
    applies_to: GroupMembershipQuery.lockForAssignment
    trigger: U4 のグループへの割り当て・外し
    logic: U4 は自分のトランザクションの中で lockForAssignment を呼ぶ。IF Busy THEN U4 も巻き戻して断る。IF Locked で exists が偽 THEN U4 は GROUP_NOT_FOUND で断る。ELSE 割り当てを書く
    violation: U4 の応答（GROUP_BUSY・GROUP_NOT_FOUND）
    source: Q2・契約 C4（足す口）・AC2.1.5
  - id: BR5.4
    statement: メンバー（と U4 の割り当て）からグループへの外部キーを最後の守りとして置く
    category: constraint
    applies_to: group_members.group_id
    trigger: 書き込み
    logic: 外部キー（削除の制限）で、消えたグループを指す行を作らない。BR5.1 が守るため通常は働かないが、働いたときは違反の例外を想定内の拒否（削除なら GROUP_IN_USE、追加なら GROUP_NOT_FOUND）に読み替え、500 にしない。読み替えた後の確定と監査は BR5.7 の経路
    violation: 読み替えた code（監査は BR5.7）
    source: Q2
  - id: BR5.5
    statement: 一意の制約と主キーの違反は業務の拒否に読み替える
    category: policy
    applies_to: 作成・名前の変更・メンバーの追加
    trigger: 確定の前の書き込みで違反が起きたとき
    logic: 違反を起こしうる書き込みは操作の中で明示して DB へ送り（flush）、確定の前に違反が起きるようにする。IF nameKey の一意の違反 THEN GROUP_NAME_DUPLICATE。IF メンバーの主キーの違反 THEN GROUP_NO_CHANGE。違反の例外の文は外へ出さない（BR9.3）。読み替えた後の確定と監査は BR5.7 の経路
    violation: 409 の該当の code。監査は BR5.7 の経路で BR8.2 のとおり残る
    source: AC2.1.2・AC2.1.13・まとめの確認・承認の場の直し R-01
  - id: BR5.7
    statement: DB の違反を業務の拒否に読み替えたときは、違反の起きたトランザクションを巻き戻し、書き込みの無い新しいトランザクションで失敗の出来事だけを出す
    category: constraint
    applies_to: BR5.4・BR5.5 の読み替え（同時の作成・名前の変更・メンバーの追加の重なり）
    trigger: 書き込みの一意・主キー・外部キーの違反を受けたとき
    logic: IF 違反を受けた THEN 違反の起きたトランザクションは巻き戻す（巻き戻しの印が付いたトランザクションを確定させない）。その後、書き込みの無い新しいトランザクションの中で失敗の GroupAuditEvent だけを出して確定させ、audit が確定の後に記録する。名前の重なりは書く前の判定（BR1.4）で拒否するのが通常で、この経路は同時の重なりで起きた違反だけ。U4 role も同じ形にそろえる
    violation: 該当の 409・404。500 にしない。監査に FAILURE が残る
    source: 承認の場の直し R-01・FR12.2・Q5・AC2.1.5・AC2.1.13
  - id: BR5.6
    statement: グループの操作では、操作した人の管理者の印をトランザクションの中で確かめ直さない
    category: policy
    applies_to: すべての変える操作
    trigger: 操作
    logic: 判定は要求の入口の既存の決まり（BR2.1）だけで行う。グループの操作は管理者の印を変えないため、利用者の管理のような確かめ直しは置かない
    violation: なし
    source: まとめの確認

  # ---- BR6 単位の境界の口（契約 C4、Q3・Q4） ----
  - id: BR6.1
    statement: グループへのロールの割り当ての問いは、group が定義し role が実装する口だけで行う
    category: constraint
    applies_to: GroupDeletionGuard
    trigger: 削除・一覧
    logic: group は role のクラス・表を知らない。group の中に既定の実装を置かない。IF 実装が無い THEN アプリは起動しない
    violation: 起動の失敗（構成の誤り）
    source: ADR-002・契約 C4
  - id: BR6.2
    statement: 問う口は、グループごとの割り当ての数をまとめて1回で返す
    category: calculation
    applies_to: GroupDeletionGuard.assignedRoleCounts
    trigger: 一覧の1ページ
    logic: 渡したグループの ID のすべてに数を返す（無ければ 0）。canDelete は同じ数で答える（0 なら Allowed、1 以上なら Blocked(数)）
    violation: なし
    source: Q3・契約 C6（assignedRoleCount）
  - id: BR6.3
    statement: B3 では role のパッケージに仮の実装を置き、B5 で本物に置き換える
    category: policy
    applies_to: GroupDeletionGuard の実装
    trigger: B3〜B5 のコード生成
    logic: B3 の仮の実装は、割り当ての表がまだ無いため数を 0・Allowed で返す。B5 で本物に置き換え、仮の実装が残っていないことを B5 の終わりの条件にする。割り当てが残るときの拒否は、テスト用の実装（group/testsupport、@Primary）で確かめる
    violation: なし
    source: Q4・bolt-plan の B3
  - id: BR6.4
    statement: 所属・有無・要約の読み取りの口を U4 に出す
    category: policy
    applies_to: GroupMembershipQuery
    trigger: U4 が呼ぶとき
    logic: groupIdsOfUser は利用者が属するグループの ID の集合、exists はグループの有無、summaries は ID と名前を返す。どれも読み取りだけで、状態を変えない
    violation: なし（失敗は想定外として例外のまま）
    source: 契約 C4・FR4.3
  - id: BR6.5
    statement: グループのメンバーの利用者の ID を、グループの ID の集合でまとめて読む口を U4 に出す
    category: policy
    applies_to: GroupMembershipQuery.memberUserIds
    trigger: U4 が割り当ての一覧（グループ経由の利用者）を作るとき
    logic: 渡したグループの ID ごとにメンバーの利用者の ID の集合を1回の読み取りで返す。IF グループが無い THEN 結果に含めない（メンバーが 0 のグループは空の集合で含める）。読み取りだけで状態を変えない
    violation: なし（失敗は想定外として例外のまま）
    source: 承認の場の決定（role の設計の直しで使う口）・契約 C4（足すだけの互換の変更）・FR4.3

  # ---- BR7 一覧と詳細 ----
  - id: BR7.1
    statement: 一覧は既存の共通のページ送りに合わせ、1ページ 20 件で page だけを受ける
    category: policy
    applies_to: GET /api/admin/groups
    trigger: 一覧の要求
    logic: page が無ければ 1。IF page が 1 以上の整数でない THEN 拒否。size は受け取らず、応答の size は 20。最後のページより後は items が空で total はそのまま
    violation: 400 VALIDATION_FAILED（監査なし）
    source: 契約 C6・まとめの確認（Paging に合わせる。C6 の size との差）
  - id: BR7.2
    statement: 一覧はグループの ID の順（作った順）に並べる
    category: policy
    applies_to: GET /api/admin/groups
    trigger: 一覧の要求
    logic: groupId の昇順で並べ、ページで切る
    violation: なし
    source: まとめの確認
  - id: BR7.3
    statement: 一覧の各行にメンバーの数と割り当てたロールの数を載せる
    category: calculation
    applies_to: GroupRow
    trigger: 一覧の要求
    logic: memberCount はそのグループのメンバーの行の数。assignedRoleCount は問う口の assignedRoleCounts でページの分をまとめて求める
    violation: なし
    source: 契約 C6・Q3・画面 S6
  - id: BR7.4
    statement: 詳細はメンバーを足した順に全件返す
    category: policy
    applies_to: GET /api/admin/groups/{groupId}
    trigger: 詳細の要求
    logic: IF グループが無い THEN GROUP_NOT_FOUND。ELSE メンバーを addedAt の昇順（同じなら userId の昇順）で全件、userId・displayName・email・suspended を返す。ページ送りしない
    violation: 404 GROUP_NOT_FOUND（読み取りのため監査なし）
    source: 契約 C6・AC2.1.8・NFR2.1・まとめの確認

  # ---- BR8 監査（Q5・Q6、契約 C10） ----
  - id: BR8.1
    statement: 成功した変える操作はすべて監査に残す
    category: policy
    applies_to: 作成・名前の変更・削除・メンバーの追加・外し
    trigger: 操作の確定の後
    logic: 操作ごとに GroupAuditEvent（成功）を出し、audit が確定の後に別のトランザクションで記録する。種類は GROUP_CREATED・GROUP_RENAMED・GROUP_DELETED・GROUP_MEMBER_ADDED・GROUP_MEMBER_REMOVED
    violation: 監査の書き込みの失敗は既存の決まりどおり（操作は成功のまま、既存の失敗の扱い）
    source: FR12.1・AC2.1.1・AC2.1.4・契約 C10
  - id: BR8.2
    statement: 業務の理由で拒否した変える操作は、その操作の種類で FAILURE と理由を付けて残す
    category: policy
    applies_to: GROUP_NOT_FOUND・USER_NOT_FOUND・GROUP_NAME_DUPLICATE・GROUP_IN_USE・GROUP_NO_CHANGE の拒否
    trigger: 拒否の確定の後
    logic: 書き込みなしで確定させ、失敗の GroupAuditEvent を出す。理由は GROUP_NOT_FOUND・USER_NOT_FOUND・GROUP_NAME_DUPLICATE・GROUP_IN_USE・NO_CHANGE
    violation: なし
    source: FR12.2・Q5・SM4 C・AC2.1.13
  - id: BR8.3
    statement: 入力の誤り・排他の待ちの上限切れ・読み取りは監査に残さない
    category: policy
    applies_to: 400 VALIDATION_FAILED・409 GROUP_BUSY・一覧と詳細
    trigger: その応答のとき
    logic: IF 入力の誤り、排他の上限切れ、読み取り THEN 監査の出来事を出さない（403 は既存の ACCESS_DENIED の監査のまま）
    violation: なし
    source: Q5・useradmin の線引き
  - id: BR8.4
    statement: 監査の行の対象の列を操作に合わせて埋める
    category: policy
    applies_to: audit_events の actor_user_id・target_group_id・target_user_id
    trigger: 記録のとき
    logic: actor_user_id は操作した管理者。target_group_id は対象のグループ（作成の名前の重なりでは空、存在しない ID の拒否では要求の ID）。メンバーの追加・外しでは target_user_id に対象の利用者（存在しない利用者の拒否でも要求の ID）。target_role_id は U3 では空
    violation: なし
    source: FR12.1・契約 C10
  - id: BR8.5
    statement: detail はキーを決めた型だけから作る JSON の文字列にする
    category: constraint
    applies_to: audit_events.detail・GroupAuditDetail
    trigger: 記録のとき
    logic: GroupAuditDetail の場合（Name・Rename・Membership・InUse）ごとに決めたキー（name・before・after・groupName・members・assignedRoles）だけを出す。自由な Map・任意の文字列の連結は受けない。GROUP_NOT_FOUND・USER_NOT_FOUND の拒否では空
    violation: なし
    source: Q6・契約 C10
  - id: BR8.6
    statement: detail の上限は 16,384 文字で、途中で切らない
    category: constraint
    applies_to: audit_events.detail
    trigger: 記録のとき
    logic: IF 作った JSON が 16,384 文字を超える THEN 記録を作る側の誤り（想定外）として扱う。超えそうな中身は呼ぶ側（U4）が要約の形に切り替えてから渡す。U3 の中身は名前 64 文字以内と数だけで、上限に届かない
    violation: 想定外の誤り（テストで捕まえる）
    source: Q6・契約 C10
  - id: BR8.7
    statement: detail と監査の行に秘密と個人に関する値を入れない
    category: constraint
    applies_to: GroupAuditEvent・audit_events
    trigger: 記録のとき
    logic: パスワード・トークン・ハッシュ値・メールアドレス・氏名を型に持たない。対象の利用者は target_user_id だけで示す
    violation: なし（漏えいのテストで確かめる）
    source: FR12.3・project.md（Forbidden）・契約 C10
  - id: BR8.8
    statement: 監査の表は列を足すだけで変え、前の版のアプリが動く形を保つ
    category: constraint
    applies_to: audit_events（target_role_id・target_group_id・detail）
    trigger: 移行
    logic: 列は空を許し、参照の制約と索引を置かない。既存の列と行は変えない。移行は前進のみ
    violation: なし
    source: 要件 C2・team.md（Deployment）・契約 C10
  - id: BR8.9
    statement: 監査の種類と失敗の理由の名前は 32 文字以内で、一度決めたら変えない
    category: constraint
    applies_to: AuditEventType・AuditFailureReason に足す値
    trigger: 定義のとき
    logic: IF 名前が 32 文字を超える THEN 単体テストで落とす
    violation: テストの失敗
    source: NFR5.1・AC1.1.16

  # ---- BR9 漏えい ----
  - id: BR9.1
    statement: メンバーの氏名・メールアドレスは、すべての層で伏せ字の型のまま受け渡す
    category: constraint
    applies_to: GroupMember・詳細の経路（repository から web まで）・user.service の読み取りの口
    trigger: 詳細の読み取り
    logic: 文字列にすると *** になる型で持ち、応答の DTO を作る web の層でだけ値を取り出す。TRACE を有効にしてもアプリのログに値が出ない
    violation: なし（GroupSecretLeakIT で確かめる）
    source: NFR1.6・AC2.1.12・team.md（TraceAspect）
  - id: BR9.2
    statement: 応答にパスワードのハッシュ値・招待のトークンのハッシュ値・ロックの判定の内部の値を含めない
    category: constraint
    applies_to: 一覧・詳細・作成の応答
    trigger: 応答を作るとき
    logic: 応答の型に該当の項目を持たない
    violation: なし
    source: FR4.5・project.md（Forbidden）・AC2.1.12
  - id: BR9.3
    statement: DB の例外の文は外へ出さず、クラスの名前だけをログに出す
    category: constraint
    applies_to: 一意・主キー・外部キーの違反、排他の上限切れ
    trigger: 例外を受けたとき
    logic: 例外は受けた所（TraceAspect の対象の層の外の用途名の下位パッケージか repository の実装の中）で区分に変え、応答・ログ・監査に例外の文を載せない
    violation: なし（漏えいのテストで確かめる）
    source: project.md の学び（例外の連なりに行の値が入る）・Forbidden

  # ---- BR10 誤りの応答 ----
  - id: BR10.1
    statement: 業務の拒否は理由ごとの code と固定の状態コードで返す
    category: policy
    applies_to: GroupProblemTypes・GroupProblemTypeCatalog
    trigger: 拒否のとき
    logic: GROUP_NOT_FOUND 404・GROUP_NAME_DUPLICATE 409・GROUP_IN_USE 409・GROUP_NO_CHANGE 409・GROUP_BUSY 409 を group.domain に定義し、業務処理の結果の型を web の層が switch で尽くして BusinessException に変える
    violation: なし
    source: 契約 C6・Q2・team.md（Code Style）
  - id: BR10.2
    statement: USER_NOT_FOUND は user.domain の定義を使う
    category: policy
    applies_to: UserProblemTypes・UserAdminProblemTypes
    trigger: 定義のとき
    logic: 既存の USER_NOT_FOUND（404）の定義を useradmin.domain から user.domain へ移し（code・状態コード・文言は変えない）、useradmin と group（と U4）がそれを使う。一覧に入れるのは UserProblemTypeCatalog だけ。境界テストは緩めない
    violation: なし（code の二重の定義は起動で止まる）
    source: 契約の USER_NOT_FOUND の未決・まとめの確認
```

## 決まりの要約

| ID | 区分 | 要旨 | 違反のとき |
|---|---|---|---|
| BR1.1 | validation | 前後の空白を取り除いて判定・保存 | 400 |
| BR1.2 | validation | 1〜64 コードポイント | 400 |
| BR1.3 | validation | 制御文字は拒否 | 400 |
| BR1.4 | constraint | 重なりは大文字と小文字を区別しない、全角と半角は別 | 409 GROUP_NAME_DUPLICATE・監査 |
| BR1.5 | policy | 同じ名前への変更は拒否 | 409 GROUP_NO_CHANGE・監査 |
| BR2.1 | authorization | 管理者の印だけ、ADMIN に分類 | 401・403 |
| BR2.2 | validation | 決めた項目だけを受ける | 400（足した項目は捨てる） |
| BR2.3 | validation | 存在しない ID は理由ごとの code | 404・監査 |
| BR3.1 | constraint | 登録の終わった利用者だけ | 404 USER_NOT_FOUND・監査 |
| BR3.2 | policy | 停止中の利用者も足せる | なし |
| BR3.3 | policy | 重ねての追加は拒否 | 409 GROUP_NO_CHANGE・監査 |
| BR3.4 | policy | メンバーでない人の外しは拒否 | 409 GROUP_NO_CHANGE・監査 |
| BR3.5 | policy | 判定の順はグループ、利用者、メンバー | 当たった code |
| BR3.6 | policy | 所属は次の要求から読まれる | なし |
| BR4.1 | constraint | メンバーか割り当てが残れば削除しない | 409 GROUP_IN_USE・監査 |
| BR4.2 | calculation | 残りの数を応答に載せる | なし |
| BR4.3 | policy | 問う口に必ず聞く | なし |
| BR4.4 | policy | 何も無ければ削除する | なし |
| BR5.1 | constraint | 最初にグループの行を排他 | GROUP_NOT_FOUND |
| BR5.2 | policy | 行の排他と一意の鍵・主キーの待ちの上限切れは巻き戻して断る | 409 GROUP_BUSY・監査なし |
| BR5.3 | constraint | U4 の割り当ても同じ行を排他 | U4 の応答 |
| BR5.4 | constraint | 外部キーは最後の守り | 読み替えた code |
| BR5.5 | policy | 一意・主キーの違反を読み替える | 409 |
| BR5.6 | policy | 操作した人を確かめ直さない | なし |
| BR5.7 | constraint | 違反の後は巻き戻し、書き込みの無い新しいトランザクションで失敗の出来事だけを出す | 409・404・監査 |
| BR6.1 | constraint | 問う口は group が定義し role が実装、既定なし | 起動しない |
| BR6.2 | calculation | 割り当ての数をまとめて返す | なし |
| BR6.3 | policy | B3 は role に仮の実装、B5 で置き換え | なし |
| BR6.4 | policy | 所属・有無・要約の読み取りの口 | なし |
| BR6.5 | policy | メンバーの利用者の ID をまとめて読む口 | なし |
| BR7.1 | policy | 1ページ 20 件、page だけ | 400 |
| BR7.2 | policy | 一覧は ID の順 | なし |
| BR7.3 | calculation | 行にメンバーとロールの数 | なし |
| BR7.4 | policy | 詳細はメンバーを足した順に全件 | 404 |
| BR8.1 | policy | 成功は監査に残す | なし |
| BR8.2 | policy | 業務の拒否は FAILURE で残す | なし |
| BR8.3 | policy | 入力の誤り・BUSY・読み取りは残さない | なし |
| BR8.4 | policy | 対象の列を埋める | なし |
| BR8.5 | constraint | detail は決めた型からの JSON | なし |
| BR8.6 | constraint | detail は 16,384 文字まで、切らない | 想定外の誤り |
| BR8.7 | constraint | detail に秘密と個人に関する値を入れない | なし |
| BR8.8 | constraint | 監査の表は列を足すだけ | なし |
| BR8.9 | constraint | 名前は 32 文字以内 | テストの失敗 |
| BR9.1 | constraint | 氏名・メールアドレスは伏せ字の型 | なし |
| BR9.2 | constraint | 応答にハッシュ値などを含めない | なし |
| BR9.3 | constraint | DB の例外の文を出さない | なし |
| BR10.1 | policy | 理由ごとの code と状態コード | なし |
| BR10.2 | policy | USER_NOT_FOUND は user.domain の定義 | なし |
