/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// 負荷の試験の台本（Performance Validation）。使い捨ての環境（docker/perf/compose.yaml）だけに向けて使う。
// 場面は環境変数 SCENARIO で選ぶ。どれも同時 10（VUS）で DURATION の間くり返し、95 パーセンタイルを出す。
//   health        ヘルスチェック（U1-NFR1.1、U1-NFR1.3 の上限）
//   loginSuccess  別々の利用者 10 名のログイン（U2-NFR1.1 成功、U2-NFR1.6）
//   loginFailure  存在しないメールアドレスのログイン（U2-NFR1.1 失敗。ロックを起こさない）
//   refresh       トークンの更新（U2-NFR1.2）
//   adminCheck    管理者の確認用 API（U3-NFR1.1 成功、U2-NFR1.4 の上限）
//   forbidden     管理者でない利用者の確認用 API の 403（U3-NFR1.1 403、U3-NFR1.3、U4-NFR1.2）
//   appearance    見た目の設定の API（トークンなしの GET /api/appearance。U8 の NFR6.1）
// 監視の確かめの場面（Intent 261025-deps-perf-carryover の FR3.8）。負荷の試験の場面ではなく、手元の監視（lgtm）の式の値を確かめるための
// 少ない要求。負荷の試験の場面（待ちが無い）を流すと lgtm の受け手が止まったため、待ちを入れて要求を絞る。
//   monitorLight  1 VU・繰り返しごとに 1〜2 秒の待ち（MONITOR_DURATION、既定 5m）。ヘルスチェック・見た目の設定・管理者の確認用 API を
//                 順に回す。MONITOR_BIZ=1 のときは、試験用の利用者 perf-biz-01 の業務データの定義と一覧（bizPrepare の後）も回す。
//                 判定は checks の率 1 だけ（応答時間は監視の式で見る。閾値は置かない）
// 重なりの場面（Intent 261025-deps-perf-carryover の FR4.8 P5。dsl-v2 の NFR 設計 R-07 の任意の重なり）。
//   dslRoleOverlap  10MB の DSL の投入とプレビューの表示（1人、dslMixed の重い側と同じ）に、上限ちょうどの権限の YAML の確かめ
//                   （roleTransferLargeCheck と同じ YAML、1人）を重ねる。DSL_FILE（10MB の DSL）と ROLE_YAML_FILE（上限ちょうどの YAML）を
//                   渡す。DURATION（既定 60s）の間くり返し、checks の率 1 と、YAML の確かめの繰り返しの時間の最大 15 秒未満で判定する
// DSL の場面（Intent 260923-dsl-schema-loader の Performance Validation 用。手順は perf/README.md の「DSL の時間を測る」）。
// 管理者のトークンは5分で切れるため、VU ごとに4分でログインし直す。DSL の本文は DSL_FILE（k6 のコンテナの中のパス）から読む。
// Intent 261004-role-menu の Performance Validation（Q3: A）で、dslLight・dslCycle を「1回の繰り返しに要求1つ」にし、
// 繰り返しの始めに exec.vu.metrics.tags.op に操作の名前を入れて、iteration_duration{scenario:…,op:…} の p95 < 1000 ms と
// checks の率 1 で判定する形に直した（colima の VM の時計のずれのため、http_req_duration{name:…} は並べて記録するだけ）。
// 管理者のトークンは setup で1回取る（場面の長さはトークンの有効期限 5 分より短くする。既定 DURATION の 60 秒、README では 3 分）。
//   dslPrepare    dslLight の条件（履歴 DSL_HISTORY_MIN 件・適用中の DSL・想定の規模のプレビュー）を作る準備の場面（1 VU・1 回）。
//                 DSL_FILE の投入と適用を履歴が DSL_HISTORY_MIN 件になるまで繰り返し、最後に同じ DSL を投入してプレビューに置く
//   dslLight      今の状態（op status）と履歴（op history）を、繰り返しごとに交互に同時 VUS で送る（U2 dsl-v2 の NFR2.6 の
//                 今の状態・履歴。重い処理の制限を受けない）。setup で条件（上の dslPrepare が作る）がそろっていることを確かめる
//   dslCycle      投入（op submit。記録だけ）→ 適用（op apply）→ 投入 → 破棄（op discard）→ 履歴（op history）を、繰り返しの番号で
//                 1つずつ送る（NFR2.6 の適用・破棄。投入は重い処理のため VUS=1 で使う。適用の previewId は直前の投入の応答から受ける）
//   dslMixed      10MB の DSL の投入とプレビューの表示（1人）に、別々の利用者 VUS 名のログインを重ねる
//                 （U4 の NFR1.12・U4-POOL。コンテナの上限を配備の既定 2g にして流し、止まらないこと・失敗が無いことを見る）
//                 ログインの VU は試験全体で重ならない番号（exec.vu.idInTest）で利用者を選ぶため、VU どうしで利用者が
//                 重ならない。番号は VUS + 1 まで取りうるため、試験用の利用者が VUS + 1 名（PERF_USER_COUNT、既定 11）要る。
//                 VU ごとの最初の回に「loginLoop-user vu=<番号> user=<試験用の利用者>」の1行を出す（重なりの確かめ用）。
// 利用者の設定の場面（Intent 260925-user-management の U2。手順は perf/README.md の「利用者の設定と招待の場面」）。
// ID は construction/u2-user-preferences/nfr-requirements/performance-requirements.md の NFR。
//   preferencesGet      自分の設定の取得（GET /api/me/preferences、200。NFR6.1 の p95 1 秒）
//   preferencesSave     自分の設定の保存の成功（PUT /api/me/preferences、200。NFR6.2 の p95 1 秒）
//   preferencesInvalid  自分の設定の保存の入力の誤り（400 VALIDATION_FAILED。NFR6.2 の p95 1 秒）
//   passwordChange      パスワードの変更の成功（POST /api/me/password、204。NFR6.3 の p95 2 秒）。
//                       専用の利用者 perf-pw01〜（VU ごとに1人）で、変更の前後のパスワードを交互に使う
//   passwordMismatch    今のパスワードの誤り（400 PASSWORD_CURRENT_MISMATCH。NFR6.4 の p95 1 秒）
//   passwordInvalid     入力の誤り（400 VALIDATION_FAILED。NFR6.4 の p95 1 秒）
// 招待と登録の完了の場面（同じ Intent の U3。受け手は使い捨ての環境の Mailpit（profile mail））。
// ID は construction/u3-invitation/nfr-requirements/performance-requirements.md の NFR。BR7.4 の経路は入れない（Unverified）。
//   invite                招待（POST /api/admin/invitations、201。NFR6.1 の p95 5 秒）。回ごとに違う宛先（example.com）
//   invitationResend      送り直し（200。NFR6.1 の p95 5 秒）。setup で VU の数だけ招待を作り、VU ごとに1件を送り直す
//   invitationList        一覧の1ページ目と最後のページ（200。NFR6.3 の p95 1 秒）。setup で招待中を LIST_PENDING_MIN 件以上にする
//   invitationCancel      取り消し（204。NFR6.3 の p95 1 秒）。setup で ITERATIONS 件の招待を作り、全 VU で1件ずつ取り消す
//   registrationVerify    リンクの確かめ（有効 200、形の誤り・見つからない 404。NFR6.3 の p95 1 秒）
//   registrationComplete  登録の完了の成功（204。NFR6.4 の p95 1 秒）。setup で ITERATIONS 件の招待を作り、
//                         Mailpit の API で受けたメールの本文のリンクからトークンを取り出す（トークンは出力しない）
//   registrationInvalid   登録の完了の入力の誤り（400 VALIDATION_FAILED。NFR6.5 の p95 1 秒）
//   registrationRejected  登録の完了のリンクの拒否（見つからない・形の誤り、404 REGISTRATION_LINK_INVALID。NFR6.5 の p95 1 秒）
// U2・U3 の場面は、閾値に加えて checks の率 1（状態コードの誤りを混ぜた p95 で合格にしない）を置く。
// 利用者の管理の場面（Intent 260930-user-admin の U3。手順は perf/README.md の「利用者の管理の場面」）。ID は
// construction/u3-user-admin-api/nfr-requirements/ の performance-requirements.md（NFR5）と reliability-requirements.md（NFR6）。
// どの場面も閾値は p95 1000 ms と checks の率 1（緩めない）。操作する管理者は perf-uaop01〜（VU ごとに1人。どの操作の対象にもしない）、
// 対象は VU ごとに分け、初期管理者は操作する人にも対象にもしない（NFR5.4 の受け入れの条件）。
//   userAdminList          一覧（GET /api/admin/users、200 と total）。LIST_CASE で a（検索なしの1ページ目）・b（検索なしの最後の
//                          ページ）・c（多く当たる検索）・d（ほとんど当たらない検索）を選ぶ（NFR5.1。試験用の利用者 1,000 名）
//   userAdminProfile       氏名と言語の変更（PUT /{userId}/profile、204）と入力の誤り（400）。VU ごとに perf-uapf<VU>（NFR5.3）
//   userAdminOps           5つの操作を組で状態を戻しながらくり返す（印を付ける → 外す → 止める → 解く → ログインを1回失敗させて
//                          戻す。どれも 204）。VU ごとに perf-uat<VU>。操作ごとに p95 を判定し、準備のログインの失敗は数えない
//                          （NFR5.4・NFR5.6）。回ごとの回数は UA_ROUNDS（既定 ITERATIONS / VUS の切り上げ。操作ごとに 100 回以上）
//   userAdminSuspendWorst  止める操作の悪い側（未無効 100 件・無効 1,000 件のリフレッシュトークンを持つ perf-uasw-<VU>-<回>）を
//                          止めて解く（NFR5.5。回ごとに別の対象を使う）
//   userAdminPool          奇数の VU が5つの操作の1回分、偶数の VU が一覧（a）を同時にくり返す（NFR6.2。接続プールは hikaricp の
//                          値で判断する）。上限 10 の場面（NFR6.3）は、使い捨てのアプリの上限を 10 にして userAdminOps を
//                          VUS=5（A）と VUS=10（B）で流す
// 接続プールの上限に届かせる場面（Intent 261003-user-admin-followup の FR4.1。手順は perf/README.md の「接続プールの上限を
// 下げて流す」）。
//   userAdminPoolLimit     VU ごとに perf-uat<VU> へ、印を付ける → 外す → 止める → 解くの4つをくり返す（どれも 204 で状態が戻る。
//                          準備のログインの失敗と失敗回数を戻す操作は入れない）。constant-vus（VUS・DURATION）。接続の時間切れ
//                          （500）を起こすための場面のため、p95 と checks の閾値を置かず、状態コードの件数を数え分けの値
//                          （userAdminPoolLimit_204・_409・_500・_other）で出す。既存の userAdminOps・userAdminPool は変えない
// グループの管理の場面（Intent 261004-role-menu の U3。手順は perf/README.md の「グループの管理の場面」）。ID は
// construction/group/nfr-requirements/ の performance-requirements.md（NFR2.5）と scalability-requirements.md（NFR2.7・NFR2.8）。
// どの場面も1回の繰り返しに要求1つで、繰り返しの中に待ちを置かない。判定は場面ごとの iteration_duration の p95 < 1000 ms と
// checks の率 1（colima の VM の時計のずれのため、http_req_duration{name:…} は並べて記録するだけ）。操作する管理者は perf-graop01
// （setup でトークンを取る。初期管理者は使わない）、メンバーの候補は perf-gr-0001〜perf-gr-1000。グループとメンバーは setup が API で
// 作る。名前は実行ごとの識別（runId）と VU の番号を含めて一意にする（名前の鍵は大文字と小文字を区別せず全体で一意のため）。
//   groupListFirst     一覧の1ページ目（constant-vus。setup でグループを GROUP_LIST_MIN 件以上にする）
//   groupListLast      一覧の最後のページ（同上）
//   groupDetail        メンバー 1,000 人のグループの詳細（constant-vus。setup で1つ作り、候補を全員足す）
//   groupRename        VU ごとに自分のグループの名前を2つの名前で交互に変える（constant-vus）
//   groupCreate        VU ごとに GROUP_ROUNDS 回の作成（per-vu-iterations、maxDuration 3m）
//   groupDelete        VU ごとに setup で作った空のグループ GROUP_ROUNDS 件を1件ずつ消す（per-vu-iterations）
//   groupMemberAdd     VU ごとに自分のグループへ候補 GROUP_ROUNDS 人を1人ずつ足す（per-vu-iterations）
//   groupMemberRemove  VU ごとに setup でメンバー GROUP_ROUNDS 人を足したグループから1人ずつ外す（per-vu-iterations）
//   groupPoolLimit     VU ごとに自分のグループで、自分の候補1人の追加と外しを交互にくり返す（constant-vus。接続プールの上限の
//                      確かめ。p95 と checks の閾値を置かず、状態コードの件数を groupPoolLimit_204・_409・_500・_other で出す）
// ロールの管理の場面（Intent 261004-role-menu の U4、Bolt B4。手順は perf/README.md の「ロールの管理の場面」）。ID は
// construction/role/nfr-requirements/ の performance-requirements.md（NFR2.2・NFR2.5・NFR2.10）。どの場面も 10 VU（VUS）・3 分
// （ROLE_DURATION）の constant-vus で、1回の繰り返しに要求1つ、繰り返しの中に待ちを置かない。操作が2つ以上の場面は、繰り返しの
// 始めに exec.vu.metrics.tags.op に操作の名前を入れ、iteration_duration{scenario:…,op:…} の p95 < 1000 ms と checks の率 1 で判定する
// （op のタグが iteration_duration に付くことは B4 の Step 16 で確かめた）。http_req_duration{name:…} は並べて記録するだけ。操作する
// 管理者は perf-roleop01（setup でトークンを取る。初期管理者は使わない）。DSL・ロールと権限の悪い側のデータは、B6 で足す準備の台本が
// 先に入れる前提（ROLE_WORST_NAME のロール、ROLE_TREE_SCHEMA・ROLE_TREE_TABLE の 100 カラムのテーブル）。
//   roleTreeRead        悪い側のロールの木で、スキーマ（op schemas）・スキーマの下のテーブル（op tables）・100 カラムのテーブル
//                       （op columns）を順に開く
//   rolePermissionSave  VU ごとに自分のロール（setup で作る）の 100 カラムのテーブルの表を、READ と FULL を交互に保存する（変わる点が
//                       必ずあり ROLE_NO_CHANGE にならない）
//   roleAdminRead       一覧の1ページ目（op listFirst）・最後のページ（op listLast）・1件（op one）・グループのロール（op groupRoles）・
//                       利用者のロール（op userRoles。B5 で足した）を順にくり返す
//   roleAdminOps        VU ごとに作成（op create）・名前の変更（op rename）・削除（op delete）を順にくり返す（作ったロールを消し、
//                       名前の変更は自分のロールの名前を2つの名前で交互に変える。どれも状態が戻る）
// B5 で足した場面（Intent 261004-role-menu の U4、Bolt B5。NFR2.2・NFR2.3・NFR2.5・NFR2.7・NFR2.8・NFR2.10）。利用者の側の操作は
// メンバーの候補 perf-gr-<VU の番号>（グループの場面と同じ利用者。パスワードは PERF_USER_PASSWORD）で行い、setup が API でロールを
// 割り当て、場面で使うトークンを最後に取る。
//   workRoleSwitch      VU ごとに自分の2つのロールへ作業ロールを交互に切り替える（どれも保存を書き 204）
//   workRoleRead        自分のロールと今の作業ロールを読む（GET /api/me/work-role）
//   myPermissionsTree   悪い側のロールを作業ロールに持つ利用者で、自分の権限の木のスキーマ（op schemas）・テーブル（op tables）・
//                       100 カラムのテーブル（op columns）を順に開く
//   roleAssignmentsRead 悪い側のロールの割り当ての一覧（利用者 VUS 名と、そのメンバーのグループ）を読む
//   roleAssignOps       VU ごとに自分のロールを、利用者に割り当てる（op assignUser）・外す（op unassignUser）・グループに割り当てる
//                       （op assignGroup）・外す（op unassignGroup）を順にくり返す（どれも 204 で状態が戻る）
//   rolePoolLimit       VU の番号を 5 で割った余りが 1〜3 の VU は作業ロールの切り替え、4・0 の VU は自分のグループへの割り当てと
//                       外しを交互にくり返す（接続プールの上限の確かめ。p95 と checks の閾値を置かず、状態コードの件数を
//                       rolePoolLimit_204・_409・_500・_other で出す。合否の回は VUS=5、記録の回は VUS=20。手順は perf/README.md）
// B6 で足した場面（Intent 261004-role-menu の U4、Bolt B6。NFR2.4・NFR2.5・NFR2.9・NFR2.10、performance-design.md 2.3）。
//   rolePrepare         準備の場面（1 VU・1 回）。DSL_FILE の版 2 の DSL を投入して適用し、準備の YAML（悪い側のロール
//                       ROLE_WORST_NAME に 100 テーブル × 100 カラムを明示、置き換える先の ROLE_LARGE_PREFIX 00001〜ROLE_LARGE_COUNT、
//                       埋めるロールで合わせて ROLE_PREPARE_TOTAL 件）を台本の中で作り、確かめて適用する。ほかのロールの場面の前に1回流す
//   roleExport          権限の YAML の書き出し（GET /api/admin/role-transfer/export、200）。10 VU・3 分
//   roleTransferLargeCheck  上限ちょうど（5 MiB）の YAML（ROLE_YAML_FILE。LargeRoleTransferYaml.java で作る）の確かめ（1 VU・1 回。
//                           判定は iteration_duration の最大 < 15 秒）
//   roleTransferLargeApply  同じ YAML の適用（1 VU・1 回。setup で確かめて指紋を受ける。判定は iteration_duration の最大 < 30 秒）。
//                           ROLE_LARGE_COUNT 件のロールを置き換えるため、流し直すときは使い捨ての環境を作り直して準備からやり直す
// B7 で足した場面（Intent 261004-role-menu の U5 navigation、Bolt B7。NFR2.1・NFR2.2・NFR2.5、performance-design.md 3節）。利用者は
// 試験用の利用者 perf-nav-01〜NAV_USER_COUNT（VU の番号で決める。パスワードは PERF_USER_PASSWORD）。どの場面も1回の繰り返しに要求1つで、
// トークンは setup で VU の数だけ取る。判定は iteration_duration の p95 < 1000 ms と checks の率 1（http_req_duration は並べて記録）。
//   navPrepare          準備の場面（1 VU・1 回。rolePrepare の後に流す）。DSL_FILE の悪い側の DSL（perf/make-nav-dsl.mjs --worst）を
//                       投入して適用し、グループ NAV_GROUP_COUNT 個を作って試験用の利用者を全員メンバーにし、どのグループにもロール
//                       NAV_WORST_NAME（rolePrepare が入れる）を割り当て、各利用者の作業ロールを NAV_WORST_NAME に保存する
//   navMenu             業務のメニュー（GET /api/me/navigation、200）。10 VU・3 分
//                       （置き場の問い合わせ GET /api/me/table-access の場面 tableAccessVisible・tableAccessDenied は、その道を
//                       消した Intent 261025-deps-perf-carryover の FR3.3 で外した）
//   navMenuBaseline     目安の DSL（DSL_FILE。perf/make-nav-dsl.mjs --baseline、menus 100 項目・深さ 1）を setup で適用し直した後の
//                       業務のメニュー（200）。10 VU・3 分。DSL を入れ替えるため、流す順の最後に置く（perf/README.md）
// 業務データの場面（Intent 261016-business-data-views の U1 u1-businessdata-read、Bolt B2。手順は perf/README.md の「業務データの場面」）。ID は
// construction/u1-businessdata-read/nfr-requirements/ の performance-requirements.md（NFR1.1・NFR1.2）と scalability-requirements.md
// （NFR2.1・NFR2.4・NFR2.5）。種は perf/biz/make-biz-seed.sh（スキーマ bizperf の biz_item 100 カラム × 100,000 行・biz_pair・biz_wide）、
// DSL は perf/make-biz-dsl.mjs。利用者は試験用の利用者 perf-biz-01〜BIZ_USER_COUNT（VU の番号で決める。パスワードは PERF_USER_PASSWORD）。
// どの場面も1回の繰り返しに要求1つで、繰り返しの中に待ちを置かない。トークンは setup で VU の数だけ取り、setup は前の場面の状態に
// 頼らず、定義の API が 200 になること（bizPrepare が作った前提）を確かめてから始める。判定は iteration_duration{scenario:…} の
// p95 < 1000 ms と checks の率 1（http_req_duration{name:…} は並べて記録）。10 VU（VUS）・3 分（BIZ_DURATION）の constant-vus。
//   bizPrepare          準備の場面（1 VU・1 回）。DSL_FILE の DSL の投入と適用、ロール BIZ_ROLE_NAME の用意とスキーマへの READ の保存、
//                       試験用の利用者への割り当て、1人ずつログインして作業ロールの保存（前の回の状態も受け入れる）
//   bizDefinition       定義（POST /api/tables/definition、200 で一覧のカラム 100）。対象DB に接続しないため PostgreSQL の回だけで流す
//   bizSearchFirst      一覧の1ページ目（100 件・100 カラム・条件なし・既定の並び、全体の件数を含む。200 で 100 件・total が BIZ_ROWS）
//   bizSearchContains   c_010 の部分一致（表の全部を読む形。setup で数えた total と同じこと）
//   bizSearchLast       主キーの順の最後のページ（setup で total から決める）
//   bizSearchLastSorted 索引の無い c_001 で並べ替えた最後のページ（NFR 設計のレビュー R-05。計画の D2: A）
//   bizDetail           主キーで1行の詳細（POST /api/tables/rows/get。VU と繰り返しの番号で行を回す）
//   bizSearchWide       biz_wide の1ページ目（100 件 × 100 カラム × 100 字。NFR2.5 のメモリの確かめ。応答時間は記録だけ）。
//                       流す前にアプリのコンテナを作り直して前の memory.peak を消す
// 業務データの書き込みの場面（Intent 261016-business-data-views の U2 u2-businessdata-write、Bolt B4。手順は perf/README.md の「業務データの
// 書き込みの場面」）。ID は construction/u2-businessdata-write/nfr-requirements/ の performance-requirements.md（NFR1.4・NFR1.5）と
// scalability-requirements.md（NFR2.7・NFR2.8）。表は種の台本の biz_new（100 カラム、自動の採番の主キー・一意の u_code・biz_ref を指す
// ref_id）と biz_ref。setup は前の場面の状態に頼らず、書き込みのロール BIZ_WRITER_ROLE_NAME（スキーマを READ、biz_new・biz_ref を FULL
// と CREATE・DELETE）を用意して試験用の利用者に割り当て、作業ロールを保存し、トークンを VU の数だけ取り、定義の API で書き込みが使える
// ことを確かめてから始める（DSL の適用は bizPrepare が行う）。どの場面も1回の繰り返しに要求1つで、判定は iteration_duration{scenario:…}
// の p95 < 1000 ms と checks の率 1（bizCreateLarge は記録だけ）。値はすべて架空で、出力しない。
//   bizCreate           作成（100 カラムの表の FULL のカラム。一意と外部キーを満たす）。10 VU・3 分
//   bizUpdate           更新（VU ごとに setup で作った別の行の1カラムを変える）。10 VU・3 分
//   bizDelete           削除（setup で ITERATIONS 件の行を作り、1回に1行を消す）。既定 100 回
//   bizWritePoolLimit   参照先に無い値での作成（外部キーの違反 → 巻き戻し → 同じ接続での確かめ。409 を期待の状態コードにする）。
//                       10 VU で書き込みのプールの上限 5 に必ず届く（NFR2.7）。10 VU・3 分
//   bizCreateLarge      4 MiB 近い本文の作成（97 カラム × 10,000 字の 4 バイトの文字。NFR2.8 のメモリの確かめ）。10 VU ×
//                       BIZ_LARGE_ITERATIONS 回。流す前にアプリのコンテナを作り直して前の memory.peak を消す
import http from 'k6/http'
import exec from 'k6/execution'
import { check, fail, sleep } from 'k6'
import { Counter } from 'k6/metrics'

const BASE = __ENV.BASE_URL || 'http://app:8080'
const SCENARIO = __ENV.SCENARIO
const VUS = Number(__ENV.VUS || 10)
const DURATION = __ENV.DURATION || '60s'
const USER_PASSWORD = __ENV.PERF_USER_PASSWORD
const ADMIN_EMAIL = __ENV.PERF_ADMIN_EMAIL
const ADMIN_PASSWORD = __ENV.PERF_ADMIN_PASSWORD
// 入れてある試験用の利用者の数（perf-user01 から。dslMixed では VUS + 1 名以上が要る）
const PERF_USER_COUNT = Number(__ENV.PERF_USER_COUNT || 11)
const REFRESH_COOKIE = 'mastersmith_refresh'
const JSON_HEADERS = { 'Content-Type': 'application/json', Origin: BASE }
const DSL_API = `${BASE}/api/admin/dsl`
const DSL_SCENARIOS = ['dslPrepare', 'dslLight', 'dslCycle', 'dslMixed', 'dslRoleOverlap']
// dslLight・dslCycle の操作の名前（op のタグ。繰り返しの番号で順に回す）
const DSL_OPS = {
  dslLight: ['status', 'history'],
  dslCycle: ['submit', 'apply', 'submit', 'discard', 'history'],
}
// dslLight の条件の履歴の件数（履歴の上限 20 件。dslPrepare がそろえ、dslLight の setup が確かめる）
const DSL_HISTORY_MIN = Number(__ENV.DSL_HISTORY_MIN || 20)
// DSL の本文は初期化の段でだけ読める（k6 の open）。DSL の場面のときだけ読む。
const DSL_BODY =
  DSL_SCENARIOS.includes(SCENARIO) && __ENV.DSL_FILE ? open(__ENV.DSL_FILE, 'b') : null
// U2・U3 の場面の設定
const MAILPIT = __ENV.MAILPIT_URL || 'http://mailpit:8025'
// 取り消し・登録の完了の回数（setup で同じ数の招待を用意し、全 VU で1回ずつ使い切る）
const ITERATIONS = Number(__ENV.ITERATIONS || 100)
// 一覧の場面で setup が用意する招待中の件数の下限（1ページ 20 件より多くする）
const LIST_PENDING_MIN = Number(__ENV.LIST_PENDING_MIN || 45)
// 入れてあるパスワードの変更の専用の利用者の数（perf-pw01 から。VUS 以上が要る）
const PERF_PW_USER_COUNT = Number(__ENV.PERF_PW_USER_COUNT || 10)
const INVITATION_API = `${BASE}/api/admin/invitations`
const REGISTRATION_API = `${BASE}/api/registration`
const PAGE_SIZE = 20
const TOKEN_LENGTH = 43
const TOKEN_CHARS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_'
// 招待のメールの本文のリンク（ベース URL ＋ /register#token= ＋ トークン）
const TOKEN_IN_MAIL = /\/register#token=([A-Za-z0-9_-]{43})/
// 場面ごとの p95 の閾値（ミリ秒）。キーは要求のタグ name。
const USER_THRESHOLDS = {
  preferencesGet: { preferencesGet: 1000 },
  preferencesSave: { preferencesSave: 1000 },
  preferencesInvalid: { preferencesInvalid: 1000 },
  passwordChange: { passwordChange: 2000 },
  passwordMismatch: { passwordMismatch: 1000 },
  passwordInvalid: { passwordInvalid: 1000 },
  invite: { invite: 5000 },
  invitationResend: { invitationResend: 5000 },
  invitationList: { invitationListFirst: 1000, invitationListLast: 1000 },
  invitationCancel: { invitationCancel: 1000 },
  registrationVerify: {
    registrationVerify: 1000,
    registrationVerifyMalformed: 1000,
    registrationVerifyNotFound: 1000,
  },
  registrationComplete: { registrationComplete: 1000 },
  registrationInvalid: { registrationInvalid: 1000 },
  registrationRejected: { registrationRejectedNotFound: 1000, registrationRejectedMalformed: 1000 },
  userAdminList: { userAdminList: 1000 },
  userAdminProfile: { userAdminProfile: 1000, userAdminProfileInvalid: 1000 },
  userAdminOps: {
    userAdminGrant: 1000,
    userAdminRevoke: 1000,
    userAdminSuspend: 1000,
    userAdminResume: 1000,
    userAdminReset: 1000,
  },
  userAdminSuspendWorst: { userAdminSuspendWorst: 1000 },
  userAdminPool: {},
}
// 利用者の管理の場面（Intent 260930-user-admin の U3）
const USER_ADMIN_API = `${BASE}/api/admin/users`
const USER_ADMIN_SCENARIOS = [
  'userAdminList',
  'userAdminProfile',
  'userAdminOps',
  'userAdminSuspendWorst',
  'userAdminPool',
  'userAdminPoolLimit',
]
// 接続プールの上限の場面（FR4.1）で数える状態コードの件数。閾値は置かず、要約に件数だけを出す。
const POOL_LIMIT_COUNTS = {
  204: new Counter('userAdminPoolLimit_204'),
  409: new Counter('userAdminPoolLimit_409'),
  500: new Counter('userAdminPoolLimit_500'),
  other: new Counter('userAdminPoolLimit_other'),
}
// VU ごとに回数で終わる場面（組で状態を戻しながらくり返す・回ごとに別の対象を使う）
const USER_ADMIN_ROUND_SCENARIOS = ['userAdminOps', 'userAdminSuspendWorst']
// 1つの VU の回数（既定は、操作ごとの要求が全体で ITERATIONS 回以上になる回数）
const UA_ROUNDS = Number(__ENV.UA_ROUNDS || Math.ceil(ITERATIONS / VUS))
// 入れてある操作する管理者・対象の数（perf-uaop01〜・perf-uat01〜・perf-uapf01〜。VUS 以上が要る）
const PERF_UA_COUNT = Number(__ENV.PERF_UA_COUNT || 10)
// 一覧の場面の区分（a〜d）と、区分ごとの問い合わせ
const LIST_CASE = __ENV.LIST_CASE || 'a'
const LIST_QUERIES = {
  a: '',
  b: '?page=50',
  c: '?q=perf-ua-',
  d: '?q=zz-no-such-user',
}
// 区分ごとの total の下限と上限（1,000 名を入れた状態。d は 0 件）
const LIST_TOTALS = { a: [1000, Infinity], b: [1000, Infinity], c: [1000, Infinity], d: [0, 0] }
const UA_WRONG_PASSWORD = 'perf-wrong-password-123'
// 用意した招待を1回ずつ使い切る場面（回数で終わる）
const ONE_SHOT_SCENARIOS = ['invitationCancel', 'registrationComplete']
// setup で招待を用意する場面
const INVITATION_SETUP_SCENARIOS = [
  'invitationResend',
  'invitationList',
  'invitationCancel',
  'registrationVerify',
  'registrationComplete',
]

// グループの管理の場面（Intent 261004-role-menu の U3）
const GROUP_API = `${BASE}/api/admin/groups`
const GROUP_SCENARIOS = [
  'groupListFirst',
  'groupListLast',
  'groupDetail',
  'groupRename',
  'groupCreate',
  'groupDelete',
  'groupMemberAdd',
  'groupMemberRemove',
  'groupPoolLimit',
]
// p95 と checks で判定する8つの場面（groupPoolLimit は件数を数えるだけ）
const GROUP_JUDGED_SCENARIOS = GROUP_SCENARIOS.filter((name) => name !== 'groupPoolLimit')
// VU ごとに回数で終わる場面
const GROUP_ROUND_SCENARIOS = ['groupCreate', 'groupDelete', 'groupMemberAdd', 'groupMemberRemove']
// 1つの VU の回数（既定 100。候補の利用者は VUS × GROUP_ROUNDS 人以上が要る）
const GROUP_ROUNDS = Number(__ENV.GROUP_ROUNDS || 100)
// 一覧の場面で setup が用意するグループの件数の下限
const GROUP_LIST_MIN = Number(__ENV.GROUP_LIST_MIN || 1000)
// 入れてあるメンバーの候補の数（perf-gr-0001 から）
const PERF_GR_COUNT = Number(__ENV.PERF_GR_COUNT || 1000)
// 接続プールの上限の場面で数える状態コードの件数。閾値は置かず、要約に件数だけを出す。
const GROUP_POOL_COUNTS = {
  204: new Counter('groupPoolLimit_204'),
  409: new Counter('groupPoolLimit_409'),
  500: new Counter('groupPoolLimit_500'),
  other: new Counter('groupPoolLimit_other'),
}

// ロールの管理の場面（Intent 261004-role-menu の U4、B4）
const ROLE_API = `${BASE}/api/admin/roles`
const ROLE_SCENARIOS = [
  'roleTreeRead',
  'rolePermissionSave',
  'roleAdminRead',
  'roleAdminOps',
  'workRoleSwitch',
  'workRoleRead',
  'myPermissionsTree',
  'roleAssignmentsRead',
  'roleAssignOps',
  'rolePoolLimit',
  'roleExport',
]
// 操作が2つ以上の場面の操作の名前（op のタグ。iteration_duration で操作ごとに判定する）
const ROLE_OPS = {
  roleTreeRead: ['schemas', 'tables', 'columns'],
  roleAdminRead: ['listFirst', 'listLast', 'one', 'groupRoles', 'userRoles'],
  roleAdminOps: ['create', 'rename', 'delete'],
  myPermissionsTree: ['schemas', 'tables', 'columns'],
  roleAssignOps: ['assignUser', 'unassignUser', 'assignGroup', 'unassignGroup'],
}
// 場面の長さ（トークンの有効期限 5 分より短くする）
const ROLE_DURATION = __ENV.ROLE_DURATION || '3m'
// 準備の台本が入れる悪い側のロールの名前と、木を開くスキーマ・テーブル（perf/dsl-timing.sh が生成した DSL の名前。B6 でそろえた）
const ROLE_WORST_NAME = __ENV.ROLE_WORST_NAME || 'perf-role-worst'
const ROLE_TREE_SCHEMA = __ENV.ROLE_TREE_SCHEMA || 'large'
const ROLE_TREE_TABLE = __ENV.ROLE_TREE_TABLE || 't_001'
// 権限の YAML の受け渡しの場面（B6）
const ROLE_TRANSFER_API = `${BASE}/api/admin/role-transfer`
// 1 VU・1 回の場面（準備と、上限ちょうどの YAML の確かめ・適用）
const ROLE_ONE_SHOT_SCENARIOS = ['rolePrepare', 'roleTransferLargeCheck', 'roleTransferLargeApply']
// 準備の YAML の形（悪い側のロールの 100 テーブル × 100 カラム、置き換える先のロールの数と名前の頭、ロールの合計）
const ROLE_TREE_TABLES = Number(__ENV.ROLE_TREE_TABLES || 100)
const ROLE_TREE_COLUMNS = Number(__ENV.ROLE_TREE_COLUMNS || 100)
const ROLE_LARGE_PREFIX = __ENV.ROLE_LARGE_PREFIX || 'perf_large_'
const ROLE_LARGE_COUNT = Number(__ENV.ROLE_LARGE_COUNT || 19)
const ROLE_PREPARE_TOTAL = Number(__ENV.ROLE_PREPARE_TOTAL || 1000)
// 準備の DSL（rolePrepare）と上限ちょうどの YAML（roleTransferLarge*）は初期化の段でだけ読める（k6 の open）
const ROLE_DSL_BODY = SCENARIO === 'rolePrepare' && __ENV.DSL_FILE ? open(__ENV.DSL_FILE, 'b') : null
const ROLE_YAML_BODY =
  (SCENARIO.startsWith('roleTransferLarge') || SCENARIO === 'dslRoleOverlap') && __ENV.ROLE_YAML_FILE
    ? open(__ENV.ROLE_YAML_FILE, 'b')
    : null
// 接続プールの上限の場面で数える状態コードの件数。閾値は置かず、要約に件数だけを出す。
// 業務のメニューの場面（Intent 261004-role-menu の U5、B7）
const NAV_SCENARIOS = ['navMenu', 'navMenuBaseline']
// 準備の YAML（rolePrepare）が入れる業務のメニューの悪い側のロールの名前（1万カラムに READ と FULL を交互に明示し、テーブルの番号が
// 偶数なら NONE・奇数なら READ を明示する。計画の 13節 Q5: A）
const NAV_WORST_NAME = __ENV.NAV_WORST_NAME || 'perf-nav-worst'
// 試験用の利用者の数（perf-nav-01〜）とグループの数（navPrepare が作る）
const NAV_USER_COUNT = Number(__ENV.NAV_USER_COUNT || 10)
const NAV_GROUP_COUNT = Number(__ENV.NAV_GROUP_COUNT || 100)
// 悪い側・目安の DSL（navPrepare・navMenuBaseline）は初期化の段でだけ読める（k6 の open）
const NAV_DSL_BODY =
  (SCENARIO === 'navPrepare' || SCENARIO === 'navMenuBaseline') && __ENV.DSL_FILE ? open(__ENV.DSL_FILE, 'b') : null
const ROLE_POOL_COUNTS = {
  204: new Counter('rolePoolLimit_204'),
  409: new Counter('rolePoolLimit_409'),
  500: new Counter('rolePoolLimit_500'),
  other: new Counter('rolePoolLimit_other'),
}

// 業務データの場面（Intent 261016-business-data-views の U1、B2。NFR1.1・NFR1.2・NFR2.4・NFR2.5、performance-design.md 4節）
const BIZ_API = `${BASE}/api/tables`
const BIZ_SCENARIOS = [
  'bizDefinition',
  'bizSearchFirst',
  'bizSearchContains',
  'bizSearchLast',
  'bizSearchLastSorted',
  'bizDetail',
  'bizSearchWide',
]
// 場面ごとの p95 の上限（ミリ秒）。ここに無い場面は 1000。bizSearchWide は応答時間を記録だけにする（thresholdsFor）。
const BIZ_P95_LIMIT_MS = { bizSearchContains: 3000, bizSearchLast: 2000, bizSearchLastSorted: 2000 }
// 場面の長さ（トークンの有効期限 5 分より短くする）
const BIZ_DURATION = __ENV.BIZ_DURATION || '3m'
// 種の台本（perf/biz/make-biz-seed.sh）と DSL（perf/make-biz-dsl.mjs）が作るスキーマ・表・カラム
const BIZ_SCHEMA = __ENV.BIZ_SCHEMA || 'bizperf'
const BIZ_ITEM_TABLE = 'biz_item'
const BIZ_WIDE_TABLE = 'biz_wide'
const BIZ_ROWS = Number(__ENV.BIZ_ROWS || 100000)
const BIZ_PAGE_SIZE = 100
const BIZ_CONTAINS_COLUMN = 'c_010'
const BIZ_CONTAINS_VALUE = '目印'
const BIZ_SORT_COLUMN = 'c_001'
// 準備の場面が作るロールの名前と、試験用の利用者の数（perf-biz-01〜）
const BIZ_ROLE_NAME = __ENV.BIZ_ROLE_NAME || 'perf-biz-reader'
const BIZ_USER_COUNT = Number(__ENV.BIZ_USER_COUNT || 10)
// 準備の DSL（bizPrepare）は初期化の段でだけ読める（k6 の open）
const BIZ_DSL_BODY = SCENARIO === 'bizPrepare' && __ENV.DSL_FILE ? open(__ENV.DSL_FILE, 'b') : null

// 業務データの書き込みの場面（Intent 261016-business-data-views の U2、B4。NFR1.4・NFR1.5・NFR2.7・NFR2.8、performance-design.md 4節）
const BIZ_WRITE_SCENARIOS = ['bizCreate', 'bizUpdate', 'bizDelete', 'bizWritePoolLimit', 'bizCreateLarge']
// 1回の繰り返しに要求1つで、p95 を判定する場面（bizCreateLarge は記録だけ）
const BIZ_WRITE_JUDGED = ['bizCreate', 'bizUpdate', 'bizDelete', 'bizWritePoolLimit']
const BIZ_NEW_TABLE = 'biz_new'
const BIZ_REF_TABLE = 'biz_ref'
const BIZ_REF_ROWS = 100
const BIZ_NEW_TEXT_COLUMNS = 97
const BIZ_WRITER_ROLE_NAME = __ENV.BIZ_WRITER_ROLE_NAME || 'perf-biz-writer'
// 大きな本文の場面の回数（VU ごと）と、1カラムの字数・字（UTF-8 で 4 バイトの架空の字）
const BIZ_LARGE_ITERATIONS = Number(__ENV.BIZ_LARGE_ITERATIONS || 3)
const BIZ_LARGE_CHARS = 10000
const BIZ_LARGE_VALUE = SCENARIO === 'bizCreateLarge' ? '\u{2000B}'.repeat(BIZ_LARGE_CHARS) : null
// 違反の道の場面は 409 を期待の状態コードとして宣言する（http_req_failed に数えない）
if (SCENARIO === 'bizWritePoolLimit') http.setResponseCallback(http.expectedStatuses(409))

// 監視の確かめの場面（monitorLight）。1 VU で、繰り返しごとに 1〜2 秒待つ
const MONITOR_DURATION = __ENV.MONITOR_DURATION || '5m'
const MONITOR_BIZ = __ENV.MONITOR_BIZ === '1'

function scenariosFor(name) {
  if (name === 'monitorLight') {
    return { [name]: { executor: 'constant-vus', vus: 1, duration: MONITOR_DURATION } }
  }
  if (name === 'dslRoleOverlap') {
    return {
      dslHeavy: { executor: 'constant-vus', vus: 1, duration: DURATION, exec: 'dslHeavy' },
      roleChecks: { executor: 'constant-vus', vus: 1, duration: DURATION, exec: 'roleCheckLoop' },
    }
  }
  if (name === 'dslPrepare') {
    return { [name]: { executor: 'per-vu-iterations', vus: 1, iterations: 1, maxDuration: '20m' } }
  }
  if (name === 'navPrepare') {
    return { [name]: { executor: 'per-vu-iterations', vus: 1, iterations: 1, maxDuration: '10m' } }
  }
  if (name === 'bizPrepare') {
    return { [name]: { executor: 'per-vu-iterations', vus: 1, iterations: 1, maxDuration: '10m' } }
  }
  if (BIZ_SCENARIOS.includes(name)) {
    return { [name]: { executor: 'constant-vus', vus: VUS, duration: BIZ_DURATION } }
  }
  if (name === 'bizDelete') {
    return { [name]: { executor: 'shared-iterations', vus: VUS, iterations: ITERATIONS, maxDuration: '10m' } }
  }
  if (name === 'bizCreateLarge') {
    return { [name]: { executor: 'per-vu-iterations', vus: VUS, iterations: BIZ_LARGE_ITERATIONS, maxDuration: '10m' } }
  }
  if (BIZ_WRITE_SCENARIOS.includes(name)) {
    return { [name]: { executor: 'constant-vus', vus: VUS, duration: BIZ_DURATION } }
  }
  if (NAV_SCENARIOS.includes(name)) {
    return { [name]: { executor: 'constant-vus', vus: VUS, duration: ROLE_DURATION } }
  }
  if (ROLE_ONE_SHOT_SCENARIOS.includes(name)) {
    return { [name]: { executor: 'per-vu-iterations', vus: 1, iterations: 1, maxDuration: '10m' } }
  }
  if (ROLE_SCENARIOS.includes(name)) {
    return { [name]: { executor: 'constant-vus', vus: VUS, duration: ROLE_DURATION } }
  }
  if (name === 'dslMixed') {
    return {
      dslHeavy: { executor: 'constant-vus', vus: 1, duration: DURATION, exec: 'dslHeavy' },
      logins: { executor: 'constant-vus', vus: VUS, duration: DURATION, exec: 'loginLoop' },
    }
  }
  if (GROUP_ROUND_SCENARIOS.includes(name)) {
    return {
      [name]: { executor: 'per-vu-iterations', vus: VUS, iterations: GROUP_ROUNDS, maxDuration: '3m' },
    }
  }
  if (USER_ADMIN_ROUND_SCENARIOS.includes(name)) {
    return {
      [name]: { executor: 'per-vu-iterations', vus: VUS, iterations: UA_ROUNDS, maxDuration: '30m' },
    }
  }
  if (ONE_SHOT_SCENARIOS.includes(name)) {
    return {
      [name]: {
        executor: 'shared-iterations',
        vus: VUS,
        iterations: ITERATIONS,
        maxDuration: '10m',
      },
    }
  }
  return { [name]: { executor: 'constant-vus', vus: VUS, duration: DURATION } }
}

// U4 の NFR1.10（今の状態・履歴・破棄・適用の 95% が 1 秒以内）。重なりの失敗（dslMixed）は率で見る。
function thresholdsFor(name) {
  // Intent 260925-user-management の U8 の NFR6.1（トークンなしの GET /api/appearance の 95% が 300 ミリ秒以内）。
  if (name === 'appearance') {
    return { 'http_req_duration{name:appearance}': ['p(95)<300'] }
  }
  // Intent 261004-role-menu の Performance Validation（Q3: A）。op ごとの iteration_duration（単調な時計）と checks で判定し、
  // http_req_duration{name:…} は要約に並べるための常に通る閾値（max>=0）にする。投入（op submit）は記録だけ。
  if (name === 'dslPrepare') return { 'checks{scenario:dslPrepare}': ['rate==1'] }
  if (name === 'dslLight') {
    return {
      'iteration_duration{scenario:dslLight,op:status}': ['p(95)<1000'],
      'iteration_duration{scenario:dslLight,op:history}': ['p(95)<1000'],
      'checks{scenario:dslLight}': ['rate==1'],
      'http_req_duration{name:dslStatus}': ['max>=0'],
      'http_req_duration{name:dslHistory}': ['max>=0'],
    }
  }
  if (name === 'dslCycle') {
    return {
      'iteration_duration{scenario:dslCycle,op:apply}': ['p(95)<1000'],
      'iteration_duration{scenario:dslCycle,op:discard}': ['p(95)<1000'],
      'iteration_duration{scenario:dslCycle,op:history}': ['p(95)<1000'],
      'iteration_duration{scenario:dslCycle,op:submit}': ['max>=0'],
      'checks{scenario:dslCycle}': ['rate==1'],
      'http_req_duration{name:dslSubmit}': ['max>=0'],
      'http_req_duration{name:dslApply}': ['max>=0'],
      'http_req_duration{name:dslDiscard}': ['max>=0'],
      'http_req_duration{name:dslHistory}': ['max>=0'],
    }
  }
  if (name === 'dslMixed') {
    return { 'checks{scenario:logins}': ['rate==1'], 'checks{scenario:dslHeavy}': ['rate==1'] }
  }
  // FR3.8: 監視の式の確かめ用。応答時間は閾値にしない（監視の式で見る）。
  if (name === 'monitorLight') return { 'checks{scenario:monitorLight}': ['rate==1'] }
  // FR4.8 P5: 重い側（DSL）と権限の YAML の確かめの重なり。YAML の確かめは roleTransferLargeCheck と同じ 15 秒未満（最大）で判定する。
  if (name === 'dslRoleOverlap') {
    return {
      'checks{scenario:dslHeavy}': ['rate==1'],
      'checks{scenario:roleChecks}': ['rate==1'],
      'iteration_duration{scenario:roleChecks}': ['max<15000'],
      'iteration_duration{scenario:dslHeavy}': ['max>=0'],
    }
  }
  // Intent 261004-role-menu の U4（NFR2.2・NFR2.5・NFR2.10）。操作が2つ以上の場面は op ごとの iteration_duration で判定する。
  if (name === 'rolePoolLimit') return {}
  // Intent 261004-role-menu の U4（NFR2.4）。上限ちょうどの YAML の確かめ 15 秒・適用 30 秒を、1回の要求の iteration_duration の最大で判定する。
  if (name === 'roleTransferLargeCheck' || name === 'roleTransferLargeApply') {
    return {
      [`iteration_duration{scenario:${name}}`]: [name.endsWith('Check') ? 'max<15000' : 'max<30000'],
      [`checks{scenario:${name}}`]: ['rate==1'],
      [`http_req_duration{scenario:${name}}`]: ['max>=0'],
    }
  }
  if (name === 'rolePrepare') return { 'checks{scenario:rolePrepare}': ['rate==1'] }
  // Intent 261004-role-menu の U5（NFR2.2・NFR2.5）。1回の繰り返しに要求1つの場面の iteration_duration（単調な時計）と checks で判定する。
  if (name === 'navPrepare') return { 'checks{scenario:navPrepare}': ['rate==1'] }
  // Intent 261016-business-data-views の U1（NFR1.1・NFR1.2）。場面ごとの iteration_duration（単調な時計）の p95 < 1000 ms と checks の率 1 で
  // 判定し、http_req_duration{name:…} は並べて記録するだけ（max>=0）。bizSearchWide はメモリの確かめの場面（NFR2.5）で、応答時間は記録だけ。
  if (name === 'bizPrepare') return { 'checks{scenario:bizPrepare}': ['rate==1'] }
  // Intent 261027-perf-targets-review（FR1.1・FR1.4）: 部分一致（bizSearchContains）は p95 3000 ms、末尾に近いページ（bizSearchLast・
  // bizSearchLastSorted）は 2000 ms に緩めた（前の線はどれも 1000 ms）。MySQL・MariaDB の部分一致は索引を使えず、深い OFFSET は読む行が増えるため。
  // DB の種類は区別しない（Q1: A）。ほかの場面（bizSearchFirst・bizDetail・bizDefinition）は 1000 ms のまま。
  // 変えたときは perf/k6/thresholds-test.sh の期待の表も合わせる。
  const bizP95LimitMs = BIZ_P95_LIMIT_MS[name] ?? 1000
  if (BIZ_SCENARIOS.includes(name)) {
    return {
      [`iteration_duration{scenario:${name}}`]: [name === 'bizSearchWide' ? 'max>=0' : `p(95)<${bizP95LimitMs}`],
      [`checks{scenario:${name}}`]: ['rate==1'],
      [`http_req_duration{name:${name}}`]: ['max>=0'],
    }
  }
  // Intent 261016-business-data-views の U2（NFR1.4・NFR1.5・NFR2.7・NFR2.8）。U1 と同じ判定で、bizCreateLarge は応答時間を記録だけにする。
  if (BIZ_WRITE_SCENARIOS.includes(name)) {
    return {
      [`iteration_duration{scenario:${name}}`]: [BIZ_WRITE_JUDGED.includes(name) ? 'p(95)<1000' : 'max>=0'],
      [`checks{scenario:${name}}`]: ['rate==1'],
      [`http_req_duration{name:${name}}`]: ['max>=0'],
    }
  }
  if (NAV_SCENARIOS.includes(name)) {
    return {
      [`iteration_duration{scenario:${name}}`]: ['p(95)<1000'],
      [`checks{scenario:${name}}`]: ['rate==1'],
      [`http_req_duration{scenario:${name}}`]: ['max>=0'],
    }
  }
  if (ROLE_SCENARIOS.includes(name)) {
    const thresholds = {
      [`checks{scenario:${name}}`]: ['rate==1'],
      [`http_req_duration{scenario:${name}}`]: ['max>=0'],
    }
    const ops = ROLE_OPS[name]
    if (ops) {
      for (const op of ops) thresholds[`iteration_duration{scenario:${name},op:${op}}`] = ['p(95)<1000']
    } else {
      thresholds[`iteration_duration{scenario:${name}}`] = ['p(95)<1000']
    }
    return thresholds
  }
  // Intent 261004-role-menu の U3（NFR2.5）。判定は1回の繰り返しに要求1つの場面の iteration_duration（単調な時計）と checks。
  // http_req_duration{name:…} は要約に並べるための常に通る閾値（max>=0）で、判定には使わない。
  if (GROUP_JUDGED_SCENARIOS.includes(name)) {
    return {
      [`iteration_duration{scenario:${name}}`]: ['p(95)<1000'],
      [`checks{scenario:${name}}`]: ['rate==1'],
      [`http_req_duration{name:${name}}`]: ['max>=0'],
    }
  }
  // Intent 260925-user-management の U2・U3（出典の ID は先頭のコメント）
  if (USER_THRESHOLDS[name]) {
    const thresholds = { [`checks{scenario:${name}}`]: ['rate==1'] }
    for (const [tag, ms] of Object.entries(USER_THRESHOLDS[name])) {
      thresholds[`http_req_duration{name:${tag}}`] = [`p(95)<${ms}`]
    }
    return thresholds
  }
  return {}
}

export const options = {
  scenarios: scenariosFor(SCENARIO),
  thresholds: thresholdsFor(SCENARIO),
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max', 'count'],
  // 秘密の値を結果に残さないよう、要求の本文や Cookie は出さない。
  discardResponseBodies: false,
}
// 招待の用意（取り消し・登録の完了では ITERATIONS 件）とメールの読み取りに時間がかかるため、
// 招待を用意する場面だけ setup の時間の上限を既定の 60 秒から延ばす（ほかの場面は既定のまま）。
if (INVITATION_SETUP_SCENARIOS.includes(SCENARIO)) options.setupTimeout = '10m'
// 利用者の管理の場面は、setup で対象の利用者 ID を一覧の API で引く（止める悪い側は対象が VUS × UA_ROUNDS 名）
if (USER_ADMIN_SCENARIOS.includes(SCENARIO)) options.setupTimeout = '10m'
// グループの管理の場面は、setup でグループ（最大 1,000 件）とメンバー（最大 1,000 人）を API で作る
if (GROUP_SCENARIOS.includes(SCENARIO)) options.setupTimeout = '10m'
// ロールの管理の場面は、setup で悪い側のロールを一覧から探し、VU ごとのロールを API で作る
if (ROLE_SCENARIOS.includes(SCENARIO)) options.setupTimeout = '10m'
// 上限ちょうどの YAML の適用は、setup で同じ YAML を確かめて指紋を受ける
if (ROLE_ONE_SHOT_SCENARIOS.includes(SCENARIO)) options.setupTimeout = '10m'
// 業務のメニューの準備は setup で操作する管理者のトークンを取り、目安の場面は setup で目安の DSL を適用し直す（大きな DSL の投入）
if (SCENARIO === 'navPrepare' || SCENARIO === 'navMenuBaseline') options.setupTimeout = '10m'
// 業務データの準備は setup で操作する管理者のトークンを取り、ほかの場面は setup で VU の数だけログインして前提を確かめる
if (SCENARIO === 'bizPrepare' || BIZ_SCENARIOS.includes(SCENARIO)) options.setupTimeout = '10m'
// 業務データの書き込みの場面は、setup でロール・割り当て・作業ロール・トークンと、更新・削除の行を API で作る
if (BIZ_WRITE_SCENARIOS.includes(SCENARIO)) options.setupTimeout = '10m'

function userEmail(n) {
  return `perf-user${String(n).padStart(2, '0')}@example.test`
}

function login(email, password) {
  return http.post(`${BASE}/api/auth/login`, JSON.stringify({ email, password }), {
    headers: JSON_HEADERS,
    tags: { name: 'login' },
  })
}

function tokenOf(res) {
  if (res.status !== 200) fail(`ログインに失敗しました: ${res.status}`)
  return res.json('accessToken')
}

export function setup() {
  if (!SCENARIO) fail('SCENARIO を指定してください')
  if (DSL_SCENARIOS.includes(SCENARIO) && SCENARIO !== 'dslLight' && !DSL_BODY)
    fail('DSL_FILE を指定してください')
  // dslMixed のログインの VU の番号は VUS + 1 まで取りうる（dslHeavy の VU が途中の番号を取るため）。
  if (SCENARIO === 'dslMixed' && VUS + 1 > PERF_USER_COUNT) {
    fail(
      `dslMixed には試験用の利用者が VUS + 1 = ${VUS + 1} 名要ります（PERF_USER_COUNT=${PERF_USER_COUNT}）。` +
        '利用者を足して PERF_USER_COUNT を合わせてください',
    )
  }
  if (SCENARIO === 'passwordChange' && VUS > PERF_PW_USER_COUNT) {
    fail(
      `passwordChange には専用の利用者が VUS = ${VUS} 名要ります（PERF_PW_USER_COUNT=${PERF_PW_USER_COUNT}）。` +
        '利用者を足して PERF_PW_USER_COUNT を合わせてください',
    )
  }
  const tokens = {}
  // DSL の場面（dslMixed を除く）は、管理者のトークンを setup で1回取る（繰り返しの中でログインし直さない）
  if (['dslPrepare', 'dslLight', 'dslCycle'].includes(SCENARIO)) {
    tokens.dslAdmin = tokenOf(login(ADMIN_EMAIL, ADMIN_PASSWORD))
  }
  // dslCycle は繰り返しの番号で投入 → 適用 → 投入 → 破棄を回すため、1 VU でしか状態がそろわない
  if (SCENARIO === 'dslCycle' && VUS !== 1) fail(`dslCycle は VUS=1 で流してください（VUS=${VUS}）`)
  if (SCENARIO === 'dslLight') checkDslLightCondition(tokens.dslAdmin)
  if (SCENARIO === 'adminCheck') tokens.admin = tokenOf(login(ADMIN_EMAIL, ADMIN_PASSWORD))
  if (SCENARIO === 'dslRoleOverlap' && !ROLE_YAML_BODY) fail('ROLE_YAML_FILE を指定してください（上限ちょうどの権限の YAML）')
  if (SCENARIO === 'monitorLight') setupMonitor(tokens)
  if (SCENARIO === 'forbidden') tokens.user = tokenOf(login(userEmail(1), USER_PASSWORD))
  // U3 の場面の用意。招待の宛先を実行ごとに変えるための識別（409 を混ぜない）。
  if (SCENARIO === 'invite' || INVITATION_SETUP_SCENARIOS.includes(SCENARIO)) {
    tokens.runId = Date.now().toString(36)
  }
  if (INVITATION_SETUP_SCENARIOS.includes(SCENARIO)) setupInvitations(tokens)
  if (USER_ADMIN_SCENARIOS.includes(SCENARIO)) setupUserAdmin(tokens)
  if (GROUP_SCENARIOS.includes(SCENARIO)) setupGroups(tokens)
  if (ROLE_SCENARIOS.includes(SCENARIO)) setupRoles(tokens)
  if (ROLE_ONE_SHOT_SCENARIOS.includes(SCENARIO)) setupRoleTransfer(tokens)
  if (SCENARIO === 'navPrepare' || NAV_SCENARIOS.includes(SCENARIO)) setupNavigation(tokens)
  if (SCENARIO === 'bizPrepare' || BIZ_SCENARIOS.includes(SCENARIO)) setupBusinessData(tokens)
  if (BIZ_WRITE_SCENARIOS.includes(SCENARIO)) setupBusinessDataWrite(tokens)
  return tokens
}

// U3 の場面の用意（招待の API で管理者として招待を出す。登録の完了に使うトークンは Mailpit で受けたメールから取り出す）。
function setupInvitations(data) {
  const admin = {
    Authorization: `Bearer ${tokenOf(login(ADMIN_EMAIL, ADMIN_PASSWORD))}`,
    Origin: BASE,
  }
  if (SCENARIO === 'invitationResend') {
    data.invitationIds = inviteMany(admin, data.runId, 'resend', VUS).map((i) => i.id)
  } else if (SCENARIO === 'invitationCancel') {
    data.invitationIds = inviteMany(admin, data.runId, 'cancel', ITERATIONS).map((i) => i.id)
  } else if (SCENARIO === 'invitationList') {
    const total = invitationTotal(admin)
    if (total < LIST_PENDING_MIN) inviteMany(admin, data.runId, 'list', LIST_PENDING_MIN - total)
    const after = invitationTotal(admin)
    if (after <= PAGE_SIZE) fail(`招待中が ${PAGE_SIZE} 件以下です（${after} 件）`)
    data.lastPage = Math.ceil(after / PAGE_SIZE)
    console.log(`invitationList の用意: 招待中 ${after} 件、最後のページ ${data.lastPage}`)
  } else if (SCENARIO === 'registrationVerify') {
    data.registrationTokens = tokensFromMail(inviteMany(admin, data.runId, 'verify', VUS))
  } else if (SCENARIO === 'registrationComplete') {
    data.registrationTokens = tokensFromMail(inviteMany(admin, data.runId, 'complete', ITERATIONS))
  }
}

function invitationEmail(runId, kind, n) {
  return `perf-${kind}-${runId}-${n}@example.com`
}

// count 件の招待を、同時 VUS 件ずつ出す。全件が 201 で送信の結果が SENT でなければ止める（宛先・トークンは出さない）。
function inviteMany(admin, runId, kind, count) {
  const invited = []
  for (let start = 0; start < count; start += VUS) {
    const batch = []
    for (let n = start; n < Math.min(start + VUS, count); n++) {
      const email = invitationEmail(runId, kind, n)
      batch.push({ email, request: inviteRequest(admin, email, 'setupInvite') })
    }
    const responses = http.batch(batch.map((b) => b.request))
    responses.forEach((res, i) => {
      if (res.status !== 201 || res.json('sendResult') !== 'SENT') {
        fail(`用意の招待に失敗しました: ${res.status}（${start + i + 1} 件目）`)
      }
      invited.push({ id: res.json('invitationId'), email: batch[i].email })
    })
  }
  console.log(`用意の招待: ${invited.length} 件`)
  return invited
}

function inviteRequest(admin, email, name) {
  return {
    method: 'POST',
    url: INVITATION_API,
    body: JSON.stringify({ email, language: 'ja' }),
    params: { headers: { ...admin, 'Content-Type': 'application/json' }, tags: { name } },
  }
}

function invitationTotal(admin) {
  const res = http.get(`${INVITATION_API}?page=1`, { headers: admin, tags: { name: 'setupList' } })
  if (res.status !== 200) fail(`招待の一覧を読めません: ${res.status}`)
  return res.json('total')
}

// 招待ごとに、Mailpit の API で宛先のメールを探し、本文のリンクからトークンを取り出す。
// トークン・リンク・本文は、ログ・console.log・止めるときの文言に出さない。
function tokensFromMail(invited) {
  return invited.map((inv, i) => {
    for (let attempt = 0; attempt < 10; attempt++) {
      const token = tokenFromMail(inv.email)
      if (token) return token
      sleep(0.5)
    }
    return fail(`招待のメールからトークンを取り出せません（${i + 1} 件目）`)
  })
}

function tokenFromMail(email) {
  const query = encodeURIComponent(`to:"${email}"`)
  const search = http.get(`${MAILPIT}/api/v1/search?query=${query}`, {
    tags: { name: 'mailpitSearch' },
  })
  if (search.status !== 200) return null
  // 検索は部分一致のため、宛先が完全に一致するものだけを使う（宛先は実行ごと・招待ごとに違い、1通だけのはず）。
  const messages = (search.json('messages') || []).filter(
    (m) => Array.isArray(m.To) && m.To.some((to) => to.Address === email),
  )
  if (messages.length !== 1) return null
  const message = http.get(`${MAILPIT}/api/v1/message/${encodeURIComponent(messages[0].ID)}`, {
    tags: { name: 'mailpitMessage' },
  })
  if (message.status !== 200) return null
  const body = `${message.json('Text') || ''}\n${message.json('HTML') || ''}`
  const found = TOKEN_IN_MAIL.exec(body)
  return found ? found[1] : null
}

const vuState = {}

// 管理者のアクセストークン（VU ごと。4分でログインし直す）。
function adminAuth() {
  const now = Date.now()
  if (!vuState.adminToken || now - vuState.adminTokenAt > 240_000) {
    vuState.adminToken = tokenOf(login(ADMIN_EMAIL, ADMIN_PASSWORD))
    vuState.adminTokenAt = now
  }
  return { Authorization: `Bearer ${vuState.adminToken}`, Origin: BASE }
}

function dslSubmit(name) {
  return http.post(`${DSL_API}/preview?source=UPLOAD`, DSL_BODY, {
    headers: { ...adminAuth(), 'Content-Type': 'application/yaml' },
    tags: { name },
    timeout: '60s',
  })
}

// setup で取った管理者のトークンの見出し（dslPrepare・dslLight・dslCycle）
function dslHeaders(tokens, extra) {
  return { Authorization: `Bearer ${tokens.dslAdmin}`, Origin: BASE, ...extra }
}

function dslSubmitWith(tokens) {
  return http.post(`${DSL_API}/preview?source=UPLOAD`, DSL_BODY, {
    headers: dslHeaders(tokens, { 'Content-Type': 'application/yaml' }),
    tags: { name: 'dslSubmit' },
    timeout: '60s',
  })
}

function dslApplyWith(tokens, previewId) {
  return http.post(`${DSL_API}/apply`, JSON.stringify({ previewId }), {
    headers: dslHeaders(tokens, { 'Content-Type': 'application/json' }),
    tags: { name: 'dslApply' },
  })
}

function dslHistoryWith(tokens) {
  return http.get(`${DSL_API}/history`, { headers: dslHeaders(tokens), tags: { name: 'dslHistory' } })
}

// 繰り返しの番号から操作を決め、op のタグに入れる
function dslOp(name) {
  const ops = DSL_OPS[name]
  const op = ops[exec.vu.iterationInScenario % ops.length]
  exec.vu.metrics.tags.op = op
  return op
}

// dslLight の条件（履歴 DSL_HISTORY_MIN 件・適用中の DSL・プレビュー）がそろっているかを確かめる（setup から呼ぶ）
function checkDslLightCondition(token) {
  const tokens = { dslAdmin: token }
  const status = http.get(`${DSL_API}/status`, { headers: dslHeaders(tokens), tags: { name: 'dslSetup' } })
  if (status.status !== 200) fail(`今の状態を読めませんでした: ${status.status}`)
  const history = dslHistoryWith(tokens)
  if (history.status !== 200) fail(`履歴を読めませんでした: ${history.status}`)
  const count = history.json().length
  if (!status.json('applied') || !status.json('preview') || count < DSL_HISTORY_MIN) {
    fail(
      `dslLight の条件がそろっていません（適用中 ${Boolean(status.json('applied'))}、プレビュー ` +
        `${Boolean(status.json('preview'))}、履歴 ${count} 件 / ${DSL_HISTORY_MIN} 件）。先に dslPrepare を流してください`,
    )
  }
  console.log(`dslLight の条件: 適用中あり、プレビューあり、履歴 ${count} 件`)
}

// dslLight の準備（1 VU・1 回）。投入と適用を履歴が DSL_HISTORY_MIN 件になるまで繰り返し、最後に投入してプレビューに置く。
function dslPrepare(tokens) {
  let count = dslHistoryWith(tokens).json().length
  let rounds = 0
  while (count < DSL_HISTORY_MIN) {
    const submit = dslSubmitWith(tokens)
    if (!check(submit, { 'prepare submit 201': (r) => r.status === 201 })) return
    const apply = dslApplyWith(tokens, submit.json('previewId'))
    if (!check(apply, { 'prepare apply 200': (r) => r.status === 200 })) return
    const history = dslHistoryWith(tokens)
    if (!check(history, { 'prepare history 200': (r) => r.status === 200 })) return
    count = history.json().length
    rounds += 1
    if (rounds > DSL_HISTORY_MIN + 5) {
      check(null, { 'prepare history grows': () => false })
      return
    }
  }
  check(dslSubmitWith(tokens), { 'prepare preview 201': (r) => r.status === 201 })
  console.log(`dslPrepare: 投入と適用 ${rounds} 回、履歴 ${count} 件、プレビューを置いた`)
}

// 1回の繰り返しに要求1つ（今の状態と履歴を交互に送る）
function dslLight(tokens) {
  if (dslOp('dslLight') === 'status') {
    const status = http.get(`${DSL_API}/status`, { headers: dslHeaders(tokens), tags: { name: 'dslStatus' } })
    check(status, { 'status 200': (r) => r.status === 200 })
  } else {
    check(dslHistoryWith(tokens), { 'history 200': (r) => r.status === 200 })
  }
}

// 1回の繰り返しに要求1つ（投入 → 適用 → 投入 → 破棄 → 履歴を繰り返しの番号で回す。VUS=1）。
// 適用の previewId は直前の投入（op submit）の応答から受ける。
function dslCycle(tokens) {
  const op = dslOp('dslCycle')
  if (op === 'submit') {
    const res = dslSubmitWith(tokens)
    check(res, { 'submit 201': (r) => r.status === 201 })
    vuState.dslPreviewId = res.status === 201 ? res.json('previewId') : null
  } else if (op === 'apply') {
    check(dslApplyWith(tokens, vuState.dslPreviewId), { 'apply 200': (r) => r.status === 200 })
    vuState.dslPreviewId = null
  } else if (op === 'discard') {
    const res = http.del(`${DSL_API}/preview`, null, { headers: dslHeaders(tokens), tags: { name: 'dslDiscard' } })
    check(res, { 'discard 204': (r) => r.status === 204 })
    vuState.dslPreviewId = null
  } else {
    check(dslHistoryWith(tokens), { 'history 200': (r) => r.status === 200 })
  }
}

// dslMixed の重い側（1人）: 10MB の投入と、プレビューの表示（照合を含む）を繰り返す。
export function dslHeavy() {
  check(dslSubmit('dslSubmit'), { 'submit 201': (r) => r.status === 201 })
  const preview = http.get(`${DSL_API}/preview`, {
    headers: adminAuth(),
    tags: { name: 'dslPreview' },
    timeout: '60s',
  })
  check(preview, { 'preview 200': (r) => r.status === 200 })
}

// dslMixed の軽い側: 別々の利用者のログイン。利用者は試験全体で重ならない VU の番号だけで決め、番号を畳まない
// （剰余で畳むと、dslHeavy の VU が途中の番号を取ったときに、ログインの VU どうしで利用者が重なる）。
export function loginLoop() {
  const user = userEmail(exec.vu.idInTest)
  if (!vuState.loginUserLogged) {
    // 試験用の利用者のメールアドレスだけを出す（秘密の値は含まない）。
    console.log(`loginLoop-user vu=${exec.vu.idInTest} user=${user}`)
    vuState.loginUserLogged = true
  }
  const res = login(user, USER_PASSWORD)
  check(res, { 'login 200': (r) => r.status === 200 })
}

// ---- U2（利用者の設定） ----

// 試験用の利用者のアクセストークン（VU ごと。4分でログインし直す）。パスワードは呼ぶときの値を使う。
function userAuth(email, password) {
  const now = Date.now()
  if (!vuState.userToken || now - vuState.userTokenAt > 240_000) {
    vuState.userToken = tokenOf(login(email, password))
    vuState.userTokenAt = now
  }
  return { Authorization: `Bearer ${vuState.userToken}`, Origin: BASE }
}

// U2 の設定の場面の利用者（perf-user01〜10 を VU ごとに当てる。既存の loginSuccess と同じ当て方）。
function preferenceUserAuth() {
  return userAuth(userEmail(((exec.vu.idInTest - 1) % 10) + 1), USER_PASSWORD)
}

function codeOf(res) {
  try {
    return res.json('code')
  } catch {
    return null
  }
}

function jsonPut(url, body, headers, name) {
  return http.put(url, JSON.stringify(body), {
    headers: { ...headers, 'Content-Type': 'application/json' },
    tags: { name },
  })
}

function jsonPost(url, body, headers, name) {
  return http.post(url, JSON.stringify(body), {
    headers: { ...headers, 'Content-Type': 'application/json' },
    tags: { name },
  })
}

function preferencesGet() {
  const res = http.get(`${BASE}/api/me/preferences`, {
    headers: preferenceUserAuth(),
    tags: { name: 'preferencesGet' },
  })
  check(res, { 200: (r) => r.status === 200 })
}

// 保存の成功。氏名は入れたときと同じ値のまま、テーマを回ごとに変える。
function preferencesSave() {
  const email = userEmail(((exec.vu.idInTest - 1) % 10) + 1)
  const theme = exec.vu.iterationInScenario % 2 === 0 ? 'light' : 'dark'
  const body = { displayName: email, language: 'ja', theme, fontSize: 'md' }
  const res = jsonPut(`${BASE}/api/me/preferences`, body, preferenceUserAuth(), 'preferencesSave')
  check(res, { 200: (r) => r.status === 200 })
}

// 保存の入力の誤り（言語が許されない値）。DB に触れずに 400 になる。
function preferencesInvalid() {
  const email = userEmail(((exec.vu.idInTest - 1) % 10) + 1)
  const body = { displayName: email, language: 'xx', theme: 'light', fontSize: 'md' }
  const res = jsonPut(
    `${BASE}/api/me/preferences`,
    body,
    preferenceUserAuth(),
    'preferencesInvalid',
  )
  check(res, {
    400: (r) => r.status === 400,
    VALIDATION_FAILED: (r) => codeOf(r) === 'VALIDATION_FAILED',
  })
}

// パスワードの変更の専用の利用者（perf-pw01〜）。試験全体で重ならない VU の番号で1人を当てる。
function pwUserEmail(n) {
  return `perf-pw${String(n).padStart(2, '0')}@example.test`
}

// 変更の前後のパスワード（入れたときのパスワードと、その後ろに文字を足したもの）。値は出力しない。
function pwCandidates() {
  return [USER_PASSWORD, `${USER_PASSWORD}-alt`]
}

// VU の最初の回に、今のパスワードを決める。前の実行が後ろのパスワードで終わっていることがあるため、
// 入れたときの値で入れなければ後ろの値で入る（そのときログインの失敗が1件、監査に残る）。
function pwCurrent(email) {
  if (vuState.pwCurrent === undefined) {
    for (const password of pwCandidates()) {
      const res = login(email, password)
      if (res.status === 200) {
        vuState.pwCurrent = password
        vuState.userToken = res.json('accessToken')
        vuState.userTokenAt = Date.now()
        return password
      }
    }
    fail(`パスワードの変更の利用者でログインできません（VU ${exec.vu.idInTest}）`)
  }
  return vuState.pwCurrent
}

function passwordChange() {
  const email = pwUserEmail(exec.vu.idInTest)
  const current = pwCurrent(email)
  const [first, second] = pwCandidates()
  const next = current === first ? second : first
  const body = { currentPassword: current, newPassword: next, newPasswordConfirmation: next }
  const res = jsonPost(`${BASE}/api/me/password`, body, userAuth(email, current), 'passwordChange')
  if (check(res, { 204: (r) => r.status === 204 })) vuState.pwCurrent = next
}

// 今のパスワードの誤り（照合1回、監査1件）。誤りの回数は数えないため、ロックは起きない。
function passwordMismatch() {
  const next = `${USER_PASSWORD}-alt`
  const body = {
    currentPassword: 'wrong-password-123',
    newPassword: next,
    newPasswordConfirmation: next,
  }
  const res = jsonPost(`${BASE}/api/me/password`, body, preferenceUserAuth(), 'passwordMismatch')
  check(res, {
    400: (r) => r.status === 400,
    PASSWORD_CURRENT_MISMATCH: (r) => codeOf(r) === 'PASSWORD_CURRENT_MISMATCH',
  })
}

// 入力の誤り（新しいパスワードが短く、確かめと一致しない）。DB を使わない。
function passwordInvalid() {
  const body = {
    currentPassword: USER_PASSWORD,
    newPassword: 'short',
    newPasswordConfirmation: 'other',
  }
  const res = jsonPost(`${BASE}/api/me/password`, body, preferenceUserAuth(), 'passwordInvalid')
  check(res, {
    400: (r) => r.status === 400,
    VALIDATION_FAILED: (r) => codeOf(r) === 'VALIDATION_FAILED',
  })
}

// ---- U3（招待と登録の完了） ----

function invite(data) {
  const email = invitationEmail(data.runId, `inv${exec.vu.idInTest}`, exec.vu.iterationInScenario)
  const req = inviteRequest(adminAuth(), email, 'invite')
  const res = http.post(req.url, req.body, req.params)
  check(res, {
    201: (r) => r.status === 201,
    SENT: (r) => r.status === 201 && r.json('sendResult') === 'SENT',
  })
}

function invitationResend(data) {
  const id = data.invitationIds[(exec.vu.idInTest - 1) % data.invitationIds.length]
  const res = http.post(`${INVITATION_API}/${id}/resend`, null, {
    headers: adminAuth(),
    tags: { name: 'invitationResend' },
  })
  check(res, {
    200: (r) => r.status === 200,
    SENT: (r) => r.status === 200 && r.json('sendResult') === 'SENT',
  })
}

function invitationList(data) {
  const first = http.get(`${INVITATION_API}?page=1`, {
    headers: adminAuth(),
    tags: { name: 'invitationListFirst' },
  })
  check(first, {
    'first 200': (r) => r.status === 200,
    'first 20 items': (r) => r.status === 200 && r.json('items').length === PAGE_SIZE,
  })
  const last = http.get(`${INVITATION_API}?page=${data.lastPage}`, {
    headers: adminAuth(),
    tags: { name: 'invitationListLast' },
  })
  check(last, {
    'last 200': (r) => r.status === 200,
    'last has items': (r) => r.status === 200 && r.json('items').length > 0,
  })
}

function invitationCancel(data) {
  const id = data.invitationIds[exec.scenario.iterationInTest]
  const res = http.post(`${INVITATION_API}/${id}/cancel`, null, {
    headers: adminAuth(),
    tags: { name: 'invitationCancel' },
  })
  check(res, { 204: (r) => r.status === 204 })
}

// 形は正しいが見つからないトークン（43 文字の URL で使える Base64 の文字）。
function randomToken() {
  let token = ''
  for (let i = 0; i < TOKEN_LENGTH; i++) {
    token += TOKEN_CHARS[Math.floor(Math.random() * TOKEN_CHARS.length)]
  }
  return token
}

function registrationVerify(data) {
  const token = data.registrationTokens[(exec.vu.idInTest - 1) % data.registrationTokens.length]
  const valid = jsonPost(`${REGISTRATION_API}/verify`, { token }, {}, 'registrationVerify')
  check(valid, { 'valid 200': (r) => r.status === 200 })
  const malformed = jsonPost(
    `${REGISTRATION_API}/verify`,
    { token: 'not-a-token' },
    {},
    'registrationVerifyMalformed',
  )
  check(malformed, { 'malformed 404': (r) => r.status === 404 })
  const notFound = jsonPost(
    `${REGISTRATION_API}/verify`,
    { token: randomToken() },
    {},
    'registrationVerifyNotFound',
  )
  check(notFound, { 'not found 404': (r) => r.status === 404 })
}

function completeBody(token, n) {
  return {
    token,
    displayName: `perf-reg ${n}`,
    password: USER_PASSWORD,
    passwordConfirmation: USER_PASSWORD,
    language: 'ja',
    theme: 'system',
    fontSize: 'md',
  }
}

function registrationComplete(data) {
  const n = exec.scenario.iterationInTest
  const body = completeBody(data.registrationTokens[n], n)
  const res = jsonPost(`${REGISTRATION_API}/complete`, body, {}, 'registrationComplete')
  check(res, { 204: (r) => r.status === 204 })
}

// 入力の誤り（パスワードが短く、確かめと一致しない）。内部DB を引かず、招待を消費しない。
function registrationInvalid() {
  const body = {
    ...completeBody(randomToken(), 0),
    password: 'short',
    passwordConfirmation: 'other',
  }
  const res = jsonPost(`${REGISTRATION_API}/complete`, body, {}, 'registrationInvalid')
  check(res, {
    400: (r) => r.status === 400,
    VALIDATION_FAILED: (r) => codeOf(r) === 'VALIDATION_FAILED',
  })
}

// リンクの拒否（見つからない・形の誤り）。入力は正しい。どちらも監査に REGISTRATION_FAILED が1件ずつ残る。
function registrationRejected() {
  const isLinkInvalid = {
    404: (r) => r.status === 404,
    REGISTRATION_LINK_INVALID: (r) => codeOf(r) === 'REGISTRATION_LINK_INVALID',
  }
  const notFound = jsonPost(
    `${REGISTRATION_API}/complete`,
    completeBody(randomToken(), 0),
    {},
    'registrationRejectedNotFound',
  )
  check(notFound, isLinkInvalid)
  const malformed = jsonPost(
    `${REGISTRATION_API}/complete`,
    completeBody('not-a-token', 0),
    {},
    'registrationRejectedMalformed',
  )
  check(malformed, isLinkInvalid)
}

// ---- 利用者の管理（Intent 260930-user-admin の U3） ----

function two(n) {
  return String(n).padStart(2, '0')
}

function uaOperatorEmail(vu) {
  return `perf-uaop${two(vu)}@example.test`
}

function uaTargetEmail(vu) {
  return `perf-uat${two(vu)}@example.test`
}

function uaProfileEmail(vu) {
  return `perf-uapf${two(vu)}@example.test`
}

function uaWorstEmail(vu, round) {
  return `perf-uasw-${two(vu)}-${two(round)}@example.test`
}

// 検索の文字に当たる利用者の、メールアドレスから利用者 ID への対応を一覧の API で引く（setup の中だけ。ページを最後まで読む）
function idsBySearch(headers, prefix) {
  const ids = {}
  for (let page = 1; ; page++) {
    const res = http.get(`${USER_ADMIN_API}?q=${encodeURIComponent(prefix)}&page=${page}`, {
      headers,
      tags: { name: 'userAdminSetup' },
    })
    if (res.status !== 200) fail(`利用者の一覧を読めませんでした: ${res.status}`)
    const items = res.json('items')
    for (const item of items) ids[item.email] = item.userId
    if (page * PAGE_SIZE >= res.json('total') || items.length === 0) return ids
  }
}

// 利用者の管理の場面の用意。操作する管理者・対象の数を確かめ、対象の利用者 ID を引く（ID だけを返し、値は出力しない）。
function setupUserAdmin(data) {
  if (VUS > PERF_UA_COUNT) {
    fail(
      `利用者の管理の場面には操作する管理者と対象が VUS = ${VUS} 名ずつ要ります（PERF_UA_COUNT=${PERF_UA_COUNT}）。` +
        '利用者を足して PERF_UA_COUNT を合わせてください',
    )
  }
  if (SCENARIO === 'userAdminList') {
    if (LIST_QUERIES[LIST_CASE] === undefined) fail(`知らない LIST_CASE です: ${LIST_CASE}`)
    return
  }
  const headers = { Authorization: `Bearer ${tokenOf(login(uaOperatorEmail(1), USER_PASSWORD))}` }
  const ids =
    SCENARIO === 'userAdminProfile'
      ? idsBySearch(headers, 'perf-uapf')
      : SCENARIO === 'userAdminSuspendWorst'
        ? idsBySearch(headers, 'perf-uasw-')
        : idsBySearch(headers, 'perf-uat')
  const need = []
  for (let vu = 1; vu <= VUS; vu++) {
    if (SCENARIO === 'userAdminProfile') need.push(uaProfileEmail(vu))
    else if (SCENARIO === 'userAdminSuspendWorst') {
      for (let round = 1; round <= UA_ROUNDS; round++) need.push(uaWorstEmail(vu, round))
    } else need.push(uaTargetEmail(vu))
  }
  const missing = need.filter((email) => !ids[email])
  if (missing.length > 0) fail(`対象の利用者が ${missing.length} 名足りません（perf/README.md の手順で入れてください）`)
  data.userAdminIds = {}
  for (const email of need) data.userAdminIds[email] = ids[email]
}

// VU ごとの操作する管理者のアクセストークン（4分でログインし直す）
function uaOperatorAuth() {
  const now = Date.now()
  if (!vuState.uaToken || now - vuState.uaTokenAt > 240_000) {
    vuState.uaToken = tokenOf(login(uaOperatorEmail(exec.vu.idInTest), USER_PASSWORD))
    vuState.uaTokenAt = now
  }
  return { Authorization: `Bearer ${vuState.uaToken}`, Origin: BASE }
}

function uaOperate(userId, action, name) {
  const res = http.post(`${USER_ADMIN_API}/${userId}/${action}`, null, {
    headers: uaOperatorAuth(),
    tags: { name },
  })
  check(res, { [`${name} 204`]: (r) => r.status === 204 })
  return res
}

function userAdminList() {
  const res = http.get(`${USER_ADMIN_API}${LIST_QUERIES[LIST_CASE]}`, {
    headers: uaOperatorAuth(),
    tags: { name: 'userAdminList', listCase: LIST_CASE },
  })
  const [min, max] = LIST_TOTALS[LIST_CASE]
  check(res, {
    'list 200': (r) => r.status === 200,
    'list total': (r) => r.status === 200 && r.json('total') >= min && r.json('total') <= max,
  })
}

function userAdminProfile(data) {
  const vu = exec.vu.idInTest
  const userId = data.userAdminIds[uaProfileEmail(vu)]
  const language = exec.vu.iterationInScenario % 2 === 0 ? 'ja' : 'en'
  const ok = jsonPut(
    `${USER_ADMIN_API}/${userId}/profile`,
    { displayName: `perf profile ${two(vu)}`, language },
    uaOperatorAuth(),
    'userAdminProfile',
  )
  check(ok, { 'profile 204': (r) => r.status === 204 })
  const invalid = jsonPut(
    `${USER_ADMIN_API}/${userId}/profile`,
    { displayName: ' ', language: 'xx' },
    uaOperatorAuth(),
    'userAdminProfileInvalid',
  )
  check(invalid, { 'profile 400': (r) => r.status === 400 && codeOf(r) === 'VALIDATION_FAILED' })
}

// 5つの操作の1回分。印を付けて外す・止めて解く・ログインを1回失敗させて（準備。判定に数えない）失敗回数を戻す。
function userAdminOpsRound(data) {
  const email = uaTargetEmail(exec.vu.idInTest)
  const userId = data.userAdminIds[email]
  uaOperate(userId, 'grant-admin', 'userAdminGrant')
  uaOperate(userId, 'revoke-admin', 'userAdminRevoke')
  uaOperate(userId, 'suspend', 'userAdminSuspend')
  uaOperate(userId, 'resume', 'userAdminResume')
  http.post(`${BASE}/api/auth/login`, JSON.stringify({ email, password: UA_WRONG_PASSWORD }), {
    headers: JSON_HEADERS,
    tags: { name: 'userAdminPrepLogin' },
  })
  uaOperate(userId, 'reset-login-failures', 'userAdminReset')
}

function userAdminOps(data) {
  userAdminOpsRound(data)
}

function userAdminSuspendWorst(data) {
  const userId = data.userAdminIds[uaWorstEmail(exec.vu.idInTest, exec.vu.iterationInScenario + 1)]
  uaOperate(userId, 'suspend', 'userAdminSuspendWorst')
  uaOperate(userId, 'resume', 'userAdminResume')
}

function userAdminPool(data) {
  if (exec.vu.idInTest % 2 === 1) userAdminOpsRound(data)
  else userAdminList()
}

// 接続プールの上限の場面（FR4.1）の1つの操作。checks は使わず、状態コードごとの件数を数える（500 を起こすのが目的のため）。
// 操作する管理者のログインし直し（uaOperatorAuth、4分ごと）が失敗したときは、その回を打ち切り、次の回でやり直す。
function uaOperateCounted(userId, action, name) {
  const res = http.post(`${USER_ADMIN_API}/${userId}/${action}`, null, {
    headers: uaOperatorAuth(),
    tags: { name },
  })
  const counter = POOL_LIMIT_COUNTS[res.status] ?? POOL_LIMIT_COUNTS.other
  counter.add(1, { name })
  return res
}

// 印を付ける → 外す → 止める → 解く（どれも 204 で状態が戻る）。準備のログインを含めない（同時の数を上限に届かせるため）。
function userAdminPoolLimit(data) {
  const userId = data.userAdminIds[uaTargetEmail(exec.vu.idInTest)]
  uaOperateCounted(userId, 'grant-admin', 'userAdminPoolLimitGrant')
  uaOperateCounted(userId, 'revoke-admin', 'userAdminPoolLimitRevoke')
  uaOperateCounted(userId, 'suspend', 'userAdminPoolLimitSuspend')
  uaOperateCounted(userId, 'resume', 'userAdminPoolLimitResume')
}

// グループの管理の場面（Intent 261004-role-menu の U3）
function grOperatorEmail() {
  return 'perf-graop01@example.test'
}

function grCandidateEmail(n) {
  return `perf-gr-${String(n).padStart(4, '0')}@example.test`
}

// setup の中の操作する管理者のヘッダー（用意に時間がかかるため、4分でログインし直す）
const grSetupState = {}
function grSetupHeaders() {
  const now = Date.now()
  if (!grSetupState.token || now - grSetupState.at > 240_000) {
    grSetupState.token = tokenOf(login(grOperatorEmail(), USER_PASSWORD))
    grSetupState.at = now
  }
  return { Authorization: `Bearer ${grSetupState.token}`, Origin: BASE }
}

function grCreate(name) {
  const res = jsonPost(GROUP_API, { name }, grSetupHeaders(), 'groupSetup')
  if (res.status !== 201) fail(`グループを作れませんでした: ${res.status} ${codeOf(res)}`)
  return res.json('groupId')
}

function grAdd(groupId, userId) {
  const res = jsonPost(`${GROUP_API}/${groupId}/members`, { userId }, grSetupHeaders(), 'groupSetup')
  if (res.status !== 204) fail(`メンバーを足せませんでした: ${res.status} ${codeOf(res)}`)
}

function grTotal() {
  const res = http.get(GROUP_API, { headers: grSetupHeaders(), tags: { name: 'groupSetup' } })
  if (res.status !== 200) fail(`グループの一覧を読めませんでした: ${res.status}`)
  return res.json('total')
}

// グループの管理の場面の用意。グループとメンバーは API で作る。利用者 ID は利用者の一覧の検索で引く（ID だけを持ち、値は出力しない）。
// 最後に場面で使うトークンを取り直す（用意に時間がかかっても、場面の間（3 分まで）にトークンが切れないようにする）。
function setupGroups(data) {
  const runId = Date.now().toString(36)
  data.groupRunId = runId
  const perVu = SCENARIO === 'groupPoolLimit' ? 1 : GROUP_ROUNDS
  const needsCandidates = ['groupDetail', 'groupMemberAdd', 'groupMemberRemove', 'groupPoolLimit'].includes(SCENARIO)
  let candidates = []
  if (needsCandidates) {
    const ids = idsBySearch(grSetupHeaders(), 'perf-gr-')
    const count = SCENARIO === 'groupDetail' ? PERF_GR_COUNT : VUS * perVu
    for (let n = 1; n <= count; n++) {
      const id = ids[grCandidateEmail(n)]
      if (!id) fail(`メンバーの候補 ${grCandidateEmail(n)} がいません（perf/README.md の手順で ${count} 人入れてください）`)
      candidates.push(id)
    }
  }
  if (SCENARIO === 'groupListFirst' || SCENARIO === 'groupListLast') {
    for (let n = grTotal() + 1; n <= GROUP_LIST_MIN; n++) grCreate(`perf-list-${runId}-${n}`)
    data.groupLastPage = Math.max(1, Math.ceil(grTotal() / PAGE_SIZE))
  } else if (SCENARIO === 'groupDetail') {
    data.groupDetailId = grCreate(`perf-detail-${runId}`)
    for (const userId of candidates) grAdd(data.groupDetailId, userId)
  } else if (SCENARIO === 'groupRename') {
    data.groupIds = {}
    for (let vu = 1; vu <= VUS; vu++) data.groupIds[vu] = grCreate(grRenameName(runId, vu, 'a'))
  } else if (SCENARIO === 'groupDelete') {
    data.groupIds = {}
    for (let vu = 1; vu <= VUS; vu++) {
      data.groupIds[vu] = []
      for (let round = 1; round <= GROUP_ROUNDS; round++) {
        data.groupIds[vu].push(grCreate(`perf-delete-${runId}-${vu}-${round}`))
      }
    }
  } else if (['groupMemberAdd', 'groupMemberRemove', 'groupPoolLimit'].includes(SCENARIO)) {
    data.groupIds = {}
    data.groupMembers = {}
    for (let vu = 1; vu <= VUS; vu++) {
      data.groupIds[vu] = grCreate(`perf-member-${runId}-${vu}`)
      data.groupMembers[vu] = candidates.slice((vu - 1) * perVu, vu * perVu)
      if (SCENARIO === 'groupMemberRemove') {
        for (const userId of data.groupMembers[vu]) grAdd(data.groupIds[vu], userId)
      }
    }
  }
  grSetupState.token = null
  data.groupToken = tokenOf(login(grOperatorEmail(), USER_PASSWORD))
}

function grRenameName(runId, vu, side) {
  return `perf-rename-${runId}-${two(vu)}-${side}`
}

function grHeaders(data) {
  return { Authorization: `Bearer ${data.groupToken}`, Origin: BASE }
}

function groupListFirst(data) {
  const res = http.get(GROUP_API, { headers: grHeaders(data), tags: { name: 'groupListFirst' } })
  check(res, { 'groupListFirst 200': (r) => r.status === 200 && r.json('items').length === PAGE_SIZE })
}

function groupListLast(data) {
  const res = http.get(`${GROUP_API}?page=${data.groupLastPage}`, {
    headers: grHeaders(data),
    tags: { name: 'groupListLast' },
  })
  check(res, { 'groupListLast 200': (r) => r.status === 200 && r.json('items').length > 0 })
}

function groupDetail(data) {
  const res = http.get(`${GROUP_API}/${data.groupDetailId}`, {
    headers: grHeaders(data),
    tags: { name: 'groupDetail' },
  })
  check(res, { 'groupDetail 200': (r) => r.status === 200 && r.json('members').length === PERF_GR_COUNT })
}

function groupRename(data) {
  const vu = exec.vu.idInTest
  // setup で名前を a にしてあるため、1回目は b、2回目は a と交互に変える
  const side = exec.vu.iterationInScenario % 2 === 0 ? 'b' : 'a'
  const res = jsonPut(
    `${GROUP_API}/${data.groupIds[vu]}`,
    { name: grRenameName(data.groupRunId, vu, side) },
    grHeaders(data),
    'groupRename',
  )
  check(res, { 'groupRename 204': (r) => r.status === 204 })
}

function groupCreate(data) {
  const name = `perf-create-${data.groupRunId}-${two(exec.vu.idInTest)}-${exec.vu.iterationInScenario + 1}`
  const res = jsonPost(GROUP_API, { name }, grHeaders(data), 'groupCreate')
  check(res, { 'groupCreate 201': (r) => r.status === 201 })
}

function groupDelete(data) {
  const groupId = data.groupIds[exec.vu.idInTest][exec.vu.iterationInScenario]
  const res = http.del(`${GROUP_API}/${groupId}`, null, { headers: grHeaders(data), tags: { name: 'groupDelete' } })
  check(res, { 'groupDelete 204': (r) => r.status === 204 })
}

function groupMemberAdd(data) {
  const vu = exec.vu.idInTest
  const userId = data.groupMembers[vu][exec.vu.iterationInScenario]
  const res = jsonPost(`${GROUP_API}/${data.groupIds[vu]}/members`, { userId }, grHeaders(data), 'groupMemberAdd')
  check(res, { 'groupMemberAdd 204': (r) => r.status === 204 })
}

function groupMemberRemove(data) {
  const vu = exec.vu.idInTest
  const userId = data.groupMembers[vu][exec.vu.iterationInScenario]
  const res = http.del(`${GROUP_API}/${data.groupIds[vu]}/members/${userId}`, null, {
    headers: grHeaders(data),
    tags: { name: 'groupMemberRemove' },
  })
  check(res, { 'groupMemberRemove 204': (r) => r.status === 204 })
}

// 接続プールの上限の場面の1回（追加と外しを交互に送る）。checks は使わず、状態コードごとの件数を数える。
function groupPoolLimit(data) {
  const vu = exec.vu.idInTest
  const groupId = data.groupIds[vu]
  const userId = data.groupMembers[vu][0]
  const adding = exec.vu.iterationInScenario % 2 === 0
  const res = adding
    ? jsonPost(`${GROUP_API}/${groupId}/members`, { userId }, grHeaders(data), 'groupPoolLimitAdd')
    : http.del(`${GROUP_API}/${groupId}/members/${userId}`, null, {
        headers: grHeaders(data),
        tags: { name: 'groupPoolLimitRemove' },
      })
  const counter = GROUP_POOL_COUNTS[res.status] ?? GROUP_POOL_COUNTS.other
  counter.add(1, { name: adding ? 'groupPoolLimitAdd' : 'groupPoolLimitRemove' })
}

// ロールの管理の場面（Intent 261004-role-menu の U4、B4）
function roleOperatorEmail() {
  return 'perf-roleop01@example.test'
}

function roleHeaders(token) {
  return { Authorization: `Bearer ${token}`, Origin: BASE }
}

// 一覧のページを順に読み、名前の一致するロールの ID を返す（無ければ null）
function roleIdByName(token, name) {
  for (let page = 1; ; page++) {
    const res = http.get(`${ROLE_API}?page=${page}`, { headers: roleHeaders(token), tags: { name: 'roleSetup' } })
    if (res.status !== 200) fail(`ロールの一覧を読めませんでした: ${res.status}`)
    const items = res.json('items')
    const found = items.find((item) => item.name === name)
    if (found) return found.roleId
    if (items.length < PAGE_SIZE) return null
  }
}

function roleCreate(token, name, tag) {
  const res = jsonPost(ROLE_API, { name }, roleHeaders(token), tag)
  if (res.status !== 201) fail(`ロールを作れませんでした: ${res.status} ${codeOf(res)}`)
  return res.json('roleId')
}

function roleSaveBody(columns, main) {
  return {
    scope: { schemaName: ROLE_TREE_SCHEMA, tableName: ROLE_TREE_TABLE },
    entries: columns.map((column) => ({
      schemaName: ROLE_TREE_SCHEMA,
      tableName: ROLE_TREE_TABLE,
      columnName: column,
      main,
      create: null,
      delete: null,
    })),
  }
}

// ロールの管理の場面の用意。悪い側のロールは準備の台本が入れたものを名前で探し、VU ごとのロールは API で作る。ID だけを持つ。
function setupRoles(data) {
  const token = tokenOf(login(roleOperatorEmail(), USER_PASSWORD))
  data.roleToken = token
  const runId = Date.now().toString(36)
  data.roleRunId = runId
  const worst = roleIdByName(token, ROLE_WORST_NAME)
  if (!worst) fail(`ロール ${ROLE_WORST_NAME} がいません（perf/README.md の準備の台本を先に流してください）`)
  data.roleWorstId = worst
  if (SCENARIO === 'rolePermissionSave') {
    const query = `schema=${encodeURIComponent(ROLE_TREE_SCHEMA)}&table=${encodeURIComponent(ROLE_TREE_TABLE)}`
    const res = http.get(`${ROLE_API}/${worst}/permissions/columns?${query}`, {
      headers: roleHeaders(token),
      tags: { name: 'roleSetup' },
    })
    if (res.status !== 200) fail(`カラムを読めませんでした: ${res.status} ${codeOf(res)}`)
    data.roleColumns = res.json('items').filter((item) => item.inCurrentDsl).map((item) => item.columnName)
    data.roleIds = {}
    for (let vu = 1; vu <= VUS; vu++) data.roleIds[vu] = roleCreate(token, `perf-save-${runId}-${two(vu)}`, 'roleSetup')
  } else if (SCENARIO === 'roleAdminRead') {
    const res = http.get(ROLE_API, { headers: roleHeaders(token), tags: { name: 'roleSetup' } })
    data.roleLastPage = Math.max(1, Math.ceil(res.json('total') / PAGE_SIZE))
    const member = roleMembers(token, 1)[0]
    data.roleMemberId = member
    data.roleGroupId = grOf(token, `perf-rolegrp-${runId}-01`)
    roleAssignEnsured(token, worst, { userId: member })
    roleAssignEnsured(token, worst, { groupId: data.roleGroupId })
  } else if (SCENARIO === 'roleAdminOps') {
    data.roleIds = {}
    for (let vu = 1; vu <= VUS; vu++) data.roleIds[vu] = roleCreate(token, roleRenameName(runId, vu, 'a'), 'roleSetup')
  } else if (ROLE_MEMBER_SCENARIOS.includes(SCENARIO)) {
    setupRoleMembers(data, token, runId, worst)
  }
  // 用意に時間がかかっても場面の間（3 分）にトークンが切れないよう、最後に取り直す
  data.roleToken = tokenOf(login(roleOperatorEmail(), USER_PASSWORD))
  if (ROLE_MEMBER_SCENARIOS.includes(SCENARIO)) {
    data.roleMemberTokens = {}
    for (let vu = 1; vu <= VUS; vu++) data.roleMemberTokens[vu] = tokenOf(login(grCandidateEmail(vu), USER_PASSWORD))
  }
  if (SCENARIO === 'myPermissionsTree') {
    // 前の場面（workRoleSwitch など）が保存した作業ロールに頼らず、悪い側のロールを作業ロールにする（FR7.1）
    for (let vu = 1; vu <= VUS; vu++) {
      const headers = roleHeaders(data.roleMemberTokens[vu])
      const res = jsonPut(`${BASE}/api/me/work-role`, { roleId: worst }, headers, 'roleSetup')
      if (res.status !== 204) fail(`作業ロールを切り替えられませんでした: ${res.status} ${codeOf(res)}`)
    }
  }
}

// 利用者の側の操作を行う B5 の場面（メンバーの候補 perf-gr-<VU> のトークンを使う）
const ROLE_MEMBER_SCENARIOS = [
  'workRoleSwitch',
  'workRoleRead',
  'myPermissionsTree',
  'roleAssignmentsRead',
  'roleAssignOps',
  'rolePoolLimit',
]

// メンバーの候補 perf-gr-0001 から count 人の利用者 ID を引く（ID だけを持ち、値は出力しない）
function roleMembers(token, count) {
  const ids = idsBySearch(roleHeaders(token), 'perf-gr-')
  const members = []
  for (let n = 1; n <= count; n++) {
    const id = ids[grCandidateEmail(n)]
    if (!id) fail(`メンバーの候補 ${grCandidateEmail(n)} がいません（perf/README.md の手順で ${count} 人入れてください）`)
    members.push(id)
  }
  return members
}

function grOf(token, name) {
  const res = jsonPost(GROUP_API, { name }, roleHeaders(token), 'roleSetup')
  if (res.status !== 201) fail(`グループを作れませんでした: ${res.status} ${codeOf(res)}`)
  return res.json('groupId')
}

function roleAssign(token, roleId, target) {
  const res = jsonPost(`${ROLE_API}/${roleId}/assignments`, target, roleHeaders(token), 'roleSetup')
  if (res.status !== 204) fail(`ロールを割り当てられませんでした: ${res.status} ${codeOf(res)}`)
}

// 割り当て済み（409 ROLE_NO_CHANGE）も受け入れる割り当て。前の場面が同じ割り当てを残していても止まらないよう、前の状態に頼る場面の
// setup で使う（Intent 261015-deps-ui-carryover の FR7.1、project.md の学び「setup は前の場面が残した状態に頼らない」）。
function roleAssignEnsured(token, roleId, target) {
  const res = jsonPost(`${ROLE_API}/${roleId}/assignments`, target, roleHeaders(token), 'roleSetup')
  if (res.status === 204) return
  if (res.status === 409 && codeOf(res) === 'ROLE_NO_CHANGE') return
  fail(`ロールを割り当てられませんでした: ${res.status} ${codeOf(res)}`)
}

// B5 の場面の用意。VU ごとに利用者（perf-gr-<VU>）を決め、場面に合わせてロール・グループを作って割り当てる。
function setupRoleMembers(data, token, runId, worst) {
  const members = roleMembers(token, VUS)
  data.roleMembers = {}
  data.roleOwnIds = {}
  data.roleSwitchIds = {}
  data.roleGroupIds = {}
  for (let vu = 1; vu <= VUS; vu++) {
    const member = members[vu - 1]
    data.roleMembers[vu] = member
    if (['workRoleSwitch', 'workRoleRead', 'rolePoolLimit'].includes(SCENARIO)) {
      const a = roleCreate(token, `perf-ws-${runId}-${two(vu)}-a`, 'roleSetup')
      const b = roleCreate(token, `perf-ws-${runId}-${two(vu)}-b`, 'roleSetup')
      const assign = SCENARIO === 'workRoleRead' ? roleAssignEnsured : roleAssign
      assign(token, a, { userId: member })
      assign(token, b, { userId: member })
      data.roleSwitchIds[vu] = [a, b]
    }
    if (['myPermissionsTree', 'roleAssignmentsRead'].includes(SCENARIO)) {
      roleAssignEnsured(token, worst, { userId: member })
    }
    if (['roleAssignOps', 'rolePoolLimit'].includes(SCENARIO)) {
      data.roleOwnIds[vu] = roleCreate(token, `perf-assign-${runId}-${two(vu)}`, 'roleSetup')
      data.roleGroupIds[vu] = grOf(token, `perf-assign-${runId}-${two(vu)}`)
    }
  }
  if (SCENARIO === 'roleAssignmentsRead') {
    const groupId = grOf(token, `perf-assigned-${runId}`)
    for (let vu = 1; vu <= VUS; vu++) {
      const res = jsonPost(`${GROUP_API}/${groupId}/members`, { userId: members[vu - 1] }, roleHeaders(token), 'roleSetup')
      if (res.status !== 204) fail(`メンバーを足せませんでした: ${res.status} ${codeOf(res)}`)
    }
    roleAssignEnsured(token, worst, { groupId })
  }
}

function roleRenameName(runId, vu, side) {
  return `perf-role-rename-${runId}-${two(vu)}-${side}`
}

// 繰り返しの番号から操作を決め、op のタグに入れる
function roleOp(name) {
  const ops = ROLE_OPS[name]
  const op = ops[exec.vu.iterationInScenario % ops.length]
  exec.vu.metrics.tags.op = op
  return op
}

function roleTreeRead(data) {
  const op = roleOp('roleTreeRead')
  const base = `${ROLE_API}/${data.roleWorstId}/permissions`
  const schema = `schema=${encodeURIComponent(ROLE_TREE_SCHEMA)}`
  const url =
    op === 'schemas'
      ? `${base}/schemas`
      : op === 'tables'
        ? `${base}/tables?${schema}`
        : `${base}/columns?${schema}&table=${encodeURIComponent(ROLE_TREE_TABLE)}`
  const res = http.get(url, { headers: roleHeaders(data.roleToken), tags: { name: `roleTreeRead_${op}` } })
  check(res, { 'roleTreeRead 200': (r) => r.status === 200 && r.json('items').length > 0 })
}

function rolePermissionSave(data) {
  const vu = exec.vu.idInTest
  // 1回目は READ、2回目は FULL と交互に保存する（自分のロールのため、ほかの VU と重ならない）
  const main = exec.vu.iterationInScenario % 2 === 0 ? 'READ' : 'FULL'
  const res = jsonPut(
    `${ROLE_API}/${data.roleIds[vu]}/permissions`,
    roleSaveBody(data.roleColumns, main),
    roleHeaders(data.roleToken),
    'rolePermissionSave',
  )
  check(res, { 'rolePermissionSave 204': (r) => r.status === 204 })
}

function roleAdminRead(data) {
  const op = roleOp('roleAdminRead')
  const url =
    op === 'listFirst'
      ? ROLE_API
      : op === 'listLast'
        ? `${ROLE_API}?page=${data.roleLastPage}`
        : op === 'one'
          ? `${ROLE_API}/${data.roleWorstId}`
          : op === 'groupRoles'
            ? `${GROUP_API}/${data.roleGroupId}/roles`
            : `${BASE}/api/admin/users/${data.roleMemberId}/roles`
  const res = http.get(url, { headers: roleHeaders(data.roleToken), tags: { name: `roleAdminRead_${op}` } })
  check(res, { 'roleAdminRead 200': (r) => r.status === 200 })
}

function roleAdminOps(data) {
  const vu = exec.vu.idInTest
  const op = roleOp('roleAdminOps')
  const round = Math.floor(exec.vu.iterationInScenario / 3)
  let res
  if (op === 'create') {
    res = jsonPost(
      ROLE_API,
      { name: `perf-role-ops-${data.roleRunId}-${two(vu)}-${round}` },
      roleHeaders(data.roleToken),
      'roleAdminOps_create',
    )
    vuState.roleCreatedId = res.status === 201 ? res.json('roleId') : null
    check(res, { 'roleAdminOps 201': (r) => r.status === 201 })
  } else if (op === 'rename') {
    // setup で名前を a にしてあるため、1回目は b、2回目は a と交互に変える
    const side = round % 2 === 0 ? 'b' : 'a'
    res = jsonPut(
      `${ROLE_API}/${data.roleIds[vu]}`,
      { name: roleRenameName(data.roleRunId, vu, side) },
      roleHeaders(data.roleToken),
      'roleAdminOps_rename',
    )
    check(res, { 'roleAdminOps 204': (r) => r.status === 204 })
  } else {
    res = http.del(`${ROLE_API}/${vuState.roleCreatedId}`, null, {
      headers: roleHeaders(data.roleToken),
      tags: { name: 'roleAdminOps_delete' },
    })
    check(res, { 'roleAdminOps 204': (r) => r.status === 204 })
  }
}

// B5 の場面（Intent 261004-role-menu の U4、Bolt B5）
function memberHeaders(data) {
  return roleHeaders(data.roleMemberTokens[exec.vu.idInTest])
}

// 作業ロールを2つのロールで交互に切り替える（どちらも保存を書く）
function switchOnce(data, name) {
  const ids = data.roleSwitchIds[exec.vu.idInTest]
  const roleId = ids[exec.vu.iterationInScenario % 2]
  return jsonPut(`${BASE}/api/me/work-role`, { roleId }, memberHeaders(data), name)
}

function workRoleSwitch(data) {
  const res = switchOnce(data, 'workRoleSwitch')
  check(res, { 'workRoleSwitch 204': (r) => r.status === 204 })
}

// 自分のロールの一覧に、setup で割り当てた2つのロールが含まれることを確かめる（前の場面が残したほかの割り当てがあっても数で落とさない。
// Intent 261015-deps-ui-carryover の FR7.1）
function workRoleRead(data) {
  const res = http.get(`${BASE}/api/me/work-role`, { headers: memberHeaders(data), tags: { name: 'workRoleRead' } })
  const own = data.roleSwitchIds[exec.vu.idInTest]
  check(res, {
    'workRoleRead 200': (r) => {
      if (r.status !== 200) return false
      const ids = (r.json('roles') || []).map((role) => role.roleId)
      return own.every((id) => ids.includes(id))
    },
  })
}

function myPermissionsTree(data) {
  const op = roleOp('myPermissionsTree')
  const base = `${BASE}/api/me/permissions`
  const schema = `schema=${encodeURIComponent(ROLE_TREE_SCHEMA)}`
  const url =
    op === 'schemas'
      ? `${base}/schemas`
      : op === 'tables'
        ? `${base}/tables?${schema}`
        : `${base}/columns?${schema}&table=${encodeURIComponent(ROLE_TREE_TABLE)}`
  const res = http.get(url, { headers: memberHeaders(data), tags: { name: `myPermissionsTree_${op}` } })
  check(res, { 'myPermissionsTree 200': (r) => r.status === 200 && r.json('items').length > 0 })
}

function roleAssignmentsRead(data) {
  const res = http.get(`${ROLE_API}/${data.roleWorstId}/assignments`, {
    headers: roleHeaders(data.roleToken),
    tags: { name: 'roleAssignmentsRead' },
  })
  check(res, { 'roleAssignmentsRead 200': (r) => r.status === 200 && r.json('users').length >= VUS })
}

// 自分のロールの割り当てと外しを1回送る（利用者・グループの順に、割り当てと外しを交互に）
function assignOnce(data, op, name) {
  const vu = exec.vu.idInTest
  const roleId = data.roleOwnIds[vu]
  const headers = roleHeaders(data.roleToken)
  if (op === 'assignUser') return jsonPost(`${ROLE_API}/${roleId}/assignments`, { userId: data.roleMembers[vu] }, headers, name)
  if (op === 'unassignUser') {
    return http.del(`${ROLE_API}/${roleId}/assignments/users/${data.roleMembers[vu]}`, null, { headers, tags: { name } })
  }
  if (op === 'assignGroup') return jsonPost(`${ROLE_API}/${roleId}/assignments`, { groupId: data.roleGroupIds[vu] }, headers, name)
  return http.del(`${ROLE_API}/${roleId}/assignments/groups/${data.roleGroupIds[vu]}`, null, { headers, tags: { name } })
}

function roleAssignOps(data) {
  const op = roleOp('roleAssignOps')
  const res = assignOnce(data, op, `roleAssignOps_${op}`)
  check(res, { 'roleAssignOps 204': (r) => r.status === 204 })
}

// 接続プールの上限の場面の1回。checks は使わず、状態コードごとの件数を数える。
function rolePoolLimit(data) {
  const vu = exec.vu.idInTest
  const switching = [1, 2, 3].includes(vu % 5)
  let res
  let name
  if (switching) {
    name = 'rolePoolLimitSwitch'
    res = switchOnce(data, name)
  } else {
    const op = exec.vu.iterationInScenario % 2 === 0 ? 'assignGroup' : 'unassignGroup'
    name = `rolePoolLimit_${op}`
    res = assignOnce(data, op, name)
  }
  const counter = ROLE_POOL_COUNTS[res.status] ?? ROLE_POOL_COUNTS.other
  counter.add(1, { name })
}

// U2・U3 の場面の名前と処理
// 権限の YAML の受け渡しの場面（Intent 261004-role-menu の U4、B6）
function yamlHeaders(token, extra) {
  return { Authorization: `Bearer ${token}`, Origin: BASE, 'Content-Type': 'application/yaml', ...extra }
}

function transferCheck(token, body, name) {
  return http.post(`${ROLE_TRANSFER_API}/check`, body, {
    headers: yamlHeaders(token),
    tags: { name },
    timeout: '60s',
  })
}

function transferApply(token, body, fingerprint, name) {
  return http.post(`${ROLE_TRANSFER_API}/apply`, body, {
    headers: yamlHeaders(token, { 'X-Role-Transfer-Fingerprint': fingerprint }),
    tags: { name },
    timeout: '120s',
  })
}

// 1 VU・1 回の場面の用意。操作する管理者のトークンを取り、適用の場面では上限ちょうどの YAML を確かめて指紋を受ける（値は出力しない）。
function setupRoleTransfer(data) {
  if (SCENARIO === 'rolePrepare' && !ROLE_DSL_BODY) fail('DSL_FILE を指定してください（perf/README.md の準備の手順）')
  if (SCENARIO.startsWith('roleTransferLarge') && !ROLE_YAML_BODY) fail('ROLE_YAML_FILE を指定してください')
  data.roleToken = tokenOf(login(roleOperatorEmail(), USER_PASSWORD))
  if (SCENARIO === 'roleTransferLargeApply') {
    const res = transferCheck(data.roleToken, ROLE_YAML_BODY, 'roleSetup')
    if (res.status !== 200) fail(`上限ちょうどの YAML を確かめられませんでした: ${res.status} ${codeOf(res)}`)
    data.roleFingerprint = res.json('fingerprint')
    // 確かめに時間がかかっても適用の間にトークンが切れないよう、最後に取り直す
    data.roleToken = tokenOf(login(roleOperatorEmail(), USER_PASSWORD))
  }
}

// 準備の YAML を作る。悪い側のロールは ROLE_TREE_SCHEMA の主権限と、t_001〜 のテーブル × c_001〜 のカラムに READ と FULL を
// 交互に明示する。置き換える先のロールはスキーマの主権限だけ、業務のメニューの悪い側のロール（B7）はテーブルの半分が NONE、埋める
// ロールは設定なし（合計 ROLE_PREPARE_TOTAL 件）。名前はどれも二重引用符の要らない形。
function preparationYaml() {
  const three = (n) => String(n).padStart(3, '0')
  const lines = ['version: 1', 'roles:', `  ${ROLE_WORST_NAME}:`, '    schemas:', `      ${ROLE_TREE_SCHEMA}:`]
  lines.push('        main: READ', '        tables:')
  for (let t = 1; t <= ROLE_TREE_TABLES; t++) {
    lines.push(`          t_${three(t)}:`, '            main: READ', '            columns:')
    for (let c = 1; c <= ROLE_TREE_COLUMNS; c++) lines.push(`              c_${three(c)}: ${(t + c) % 2 ? 'FULL' : 'READ'}`)
  }
  for (let n = 1; n <= ROLE_LARGE_COUNT; n++) {
    lines.push(`  ${ROLE_LARGE_PREFIX}${String(n).padStart(5, '0')}:`, '    schemas:', `      ${ROLE_TREE_SCHEMA}: {main: NONE}`)
  }
  // 業務のメニューの悪い側のロール（B7、計画の 13節 Q5: A）。1万カラムに READ と FULL を交互に明示し、テーブルの番号が偶数なら NONE・
  // 奇数なら READ を明示する（テーブルの半分を NONE にする）。スキーマには明示しない。
  lines.push(`  ${NAV_WORST_NAME}:`, '    schemas:', `      ${ROLE_TREE_SCHEMA}:`, '        tables:')
  for (let t = 1; t <= ROLE_TREE_TABLES; t++) {
    lines.push(`          t_${three(t)}:`, `            main: ${t % 2 ? 'READ' : 'NONE'}`, '            columns:')
    for (let c = 1; c <= ROLE_TREE_COLUMNS; c++) lines.push(`              c_${three(c)}: ${c % 2 ? 'READ' : 'FULL'}`)
  }
  for (let n = 1; n <= ROLE_PREPARE_TOTAL - ROLE_LARGE_COUNT - 2; n++) lines.push(`  perf-fill-${String(n).padStart(4, '0')}: {}`)
  return lines.join('\n') + '\n'
}

// 準備の場面: DSL の投入と適用（対象DB には接続しない。照合が届かないときは警告になるだけ）→ 準備の YAML の確かめと適用
function rolePrepare(data) {
  const headers = roleHeaders(data.roleToken)
  const submit = http.post(`${DSL_API}/preview?source=UPLOAD`, ROLE_DSL_BODY, {
    headers: { ...headers, 'Content-Type': 'application/yaml' },
    tags: { name: 'rolePrepare' },
    timeout: '60s',
  })
  check(submit, { 'dsl submit 201': (r) => r.status === 201 })
  const previewId = submit.status === 201 ? submit.json('previewId') : null
  const apply = http.post(`${DSL_API}/apply`, JSON.stringify({ previewId }), {
    headers: { ...headers, 'Content-Type': 'application/json' },
    tags: { name: 'rolePrepare' },
    timeout: '60s',
  })
  check(apply, { 'dsl apply 200': (r) => r.status === 200 })
  const yaml = preparationYaml()
  const checked = transferCheck(data.roleToken, yaml, 'rolePrepare')
  check(checked, { 'yaml check 200': (r) => r.status === 200 })
  if (checked.status !== 200) return
  const applied = transferApply(data.roleToken, yaml, checked.json('fingerprint'), 'rolePrepare')
  check(applied, { 'yaml apply 200': (r) => r.status === 200 })
}

function roleExport(data) {
  const res = http.get(`${ROLE_TRANSFER_API}/export`, { headers: roleHeaders(data.roleToken), tags: { name: 'roleExport' } })
  check(res, { 200: (r) => r.status === 200 })
}

function roleTransferLargeCheck(data) {
  const res = transferCheck(data.roleToken, ROLE_YAML_BODY, 'roleTransferLargeCheck')
  check(res, { 200: (r) => r.status === 200 })
}

function roleTransferLargeApply(data) {
  const res = transferApply(data.roleToken, ROLE_YAML_BODY, data.roleFingerprint, 'roleTransferLargeApply')
  check(res, { 200: (r) => r.status === 200 })
}

// 監視の確かめの場面（FR3.8）の用意。管理者のトークンを取る（管理者の確認用 API）。MONITOR_BIZ=1 のときは試験用の利用者 perf-biz-01 の
// トークンを取り、業務データの定義を読めること（bizPrepare が済んでいること）を確かめる。
function setupMonitor(data) {
  data.admin = tokenOf(login(ADMIN_EMAIL, ADMIN_PASSWORD))
  if (!MONITOR_BIZ) return
  data.bizToken = tokenOf(login(bizUserEmail(1), USER_PASSWORD))
  const definition = bizDefinitionRequest(roleHeaders(data.bizToken), BIZ_ITEM_TABLE, 'monitorSetup')
  if (definition.status !== 200) {
    fail(`業務データの定義を読めませんでした: ${definition.status} ${codeOf(definition)}（先に bizPrepare を流してください）`)
  }
}

// 1回の繰り返しに、軽い要求を順に1つ（繰り返しの番号で回す）。そのあと 1〜2 秒待つ。トークンは setup の1つを使うため、場面は
// トークンの有効期限（5 分）より短くする（MONITOR_DURATION の既定は 5m のため、長くするときは setup の取り直しが要る）。
function monitorLight(data) {
  const ops = MONITOR_BIZ ? ['health', 'appearance', 'adminCheck', 'bizDefinition', 'bizSearchFirst'] : ['health', 'appearance', 'adminCheck']
  const op = ops[exec.vu.iterationInScenario % ops.length]
  if (op === 'health') {
    check(http.get(`${BASE}/actuator/health`, { tags: { name: 'monitorHealth' } }), { 'health 200': (r) => r.status === 200 })
  } else if (op === 'appearance') {
    check(http.get(`${BASE}/api/appearance`, { tags: { name: 'monitorAppearance' } }), { 'appearance 200': (r) => r.status === 200 })
  } else if (op === 'adminCheck') {
    const res = http.get(`${BASE}/api/admin/check`, {
      headers: { Authorization: `Bearer ${data.admin}` },
      tags: { name: 'monitorAdminCheck' },
    })
    check(res, { 'adminCheck 204': (r) => r.status === 204 })
  } else if (op === 'bizDefinition') {
    const res = bizDefinitionRequest(roleHeaders(data.bizToken), BIZ_ITEM_TABLE, 'monitorBizDefinition')
    check(res, { 'bizDefinition 200': (r) => r.status === 200 })
  } else {
    const res = bizSearchRequest(roleHeaders(data.bizToken), BIZ_ITEM_TABLE, {}, 'monitorBizSearch')
    check(res, { 'bizSearch 200': (r) => r.status === 200 })
  }
  sleep(1 + Math.random())
}

// dslRoleOverlap の権限の YAML の確かめの側（1人）: 上限ちょうどの YAML の確かめを繰り返す。トークンは VU の中で 4 分ごとに取り直す。
export function roleCheckLoop() {
  const now = Date.now()
  if (!vuState.overlapToken || now - vuState.overlapTokenAt > 240_000) {
    vuState.overlapToken = tokenOf(login(roleOperatorEmail(), USER_PASSWORD))
    vuState.overlapTokenAt = now
  }
  check(transferCheck(vuState.overlapToken, ROLE_YAML_BODY, 'roleOverlapCheck'), { 'yaml check 200': (r) => r.status === 200 })
}

// 業務のメニューの場面（Intent 261004-role-menu の U5、B7）
function navUserEmail(n) {
  return `perf-nav-${two(n)}@example.test`
}

// DSL を投入して適用する（対象DB には接続しない。照合が届かないときは警告になるだけ。基盤の設計の読み直しの R-07）
function navApplyDsl(token, body, name) {
  const headers = roleHeaders(token)
  const submit = http.post(`${DSL_API}/preview?source=UPLOAD`, body, {
    headers: { ...headers, 'Content-Type': 'application/yaml' },
    tags: { name },
    timeout: '60s',
  })
  const previewId = submit.status === 201 ? submit.json('previewId') : null
  const apply = http.post(`${DSL_API}/apply`, JSON.stringify({ previewId }), {
    headers: { ...headers, 'Content-Type': 'application/json' },
    tags: { name },
    timeout: '60s',
  })
  return { submit, apply }
}

// 業務のメニューの場面の用意。準備の場面は操作する管理者のトークンを取る。読み取りの場面は試験用の利用者のトークンを VU の数だけ取る
// （目安の場面は、その前に目安の DSL を適用し直す）。トークンの値は出力しない。
function setupNavigation(data) {
  if ((SCENARIO === 'navPrepare' || SCENARIO === 'navMenuBaseline') && !NAV_DSL_BODY) {
    fail('DSL_FILE を指定してください（perf/make-nav-dsl.mjs で作った DSL。perf/README.md の業務のメニューの手順）')
  }
  if (VUS > NAV_USER_COUNT) fail(`VUS（${VUS}）が試験用の利用者の数 NAV_USER_COUNT（${NAV_USER_COUNT}）を超えています`)
  if (SCENARIO === 'navPrepare') {
    data.roleToken = tokenOf(login(roleOperatorEmail(), USER_PASSWORD))
    return
  }
  if (SCENARIO === 'navMenuBaseline') {
    const { submit, apply } = navApplyDsl(tokenOf(login(roleOperatorEmail(), USER_PASSWORD)), NAV_DSL_BODY, 'navSetup')
    if (submit.status !== 201 || apply.status !== 200) {
      fail(`目安の DSL を適用できませんでした: ${submit.status} ${apply.status} ${codeOf(apply)}`)
    }
  }
  data.navTokens = {}
  for (let vu = 1; vu <= VUS; vu++) data.navTokens[vu] = tokenOf(login(navUserEmail(vu), USER_PASSWORD))
}

// 準備の場面: 悪い側の DSL の投入と適用 → グループと所属 → グループへの割り当て → 各利用者の作業ロールの保存
function navPrepare(data) {
  const token = data.roleToken
  const headers = roleHeaders(token)
  const { submit, apply } = navApplyDsl(token, NAV_DSL_BODY, 'navPrepare')
  check(submit, { 'dsl submit 201': (r) => r.status === 201 })
  check(apply, { 'dsl apply 200': (r) => r.status === 200 })
  const worst = roleIdByName(token, NAV_WORST_NAME)
  if (!worst) fail(`ロール ${NAV_WORST_NAME} がいません（先に rolePrepare を流してください）`)
  const ids = idsBySearch(headers, 'perf-nav-')
  const members = []
  for (let n = 1; n <= NAV_USER_COUNT; n++) {
    const id = ids[navUserEmail(n)]
    if (!id) fail(`試験用の利用者 ${navUserEmail(n)} がいません（perf/README.md の手順で入れてください）`)
    members.push(id)
  }
  const runId = Date.now().toString(36)
  let ok = true
  for (let g = 1; g <= NAV_GROUP_COUNT; g++) {
    const created = jsonPost(GROUP_API, { name: `perf-nav-grp-${runId}-${String(g).padStart(3, '0')}` }, headers, 'navPrepare')
    if (created.status !== 201) fail(`グループを作れませんでした: ${created.status} ${codeOf(created)}`)
    const groupId = created.json('groupId')
    for (const userId of members) {
      ok = jsonPost(`${GROUP_API}/${groupId}/members`, { userId }, headers, 'navPrepare').status === 204 && ok
    }
    ok = jsonPost(`${ROLE_API}/${worst}/assignments`, { groupId }, headers, 'navPrepare').status === 204 && ok
  }
  check(ok, { 'groups and assignments 204': (value) => value })
  let saved = true
  for (let n = 1; n <= NAV_USER_COUNT; n++) {
    // 1人ずつログインする（ロックの状態の行を作る。project.md の学び）
    const userToken = tokenOf(login(navUserEmail(n), USER_PASSWORD))
    saved = jsonPut(`${BASE}/api/me/work-role`, { roleId: worst }, roleHeaders(userToken), 'navPrepare').status === 204 && saved
  }
  check(saved, { 'work roles 204': (value) => value })
}

function navHeaders(data) {
  return roleHeaders(data.navTokens[exec.vu.idInTest])
}

function navMenuRead(data, name) {
  const res = http.get(`${BASE}/api/me/navigation`, { headers: navHeaders(data), tags: { name } })
  check(res, { [`${name} 200`]: (r) => r.status === 200 && r.json('items').length > 0 })
}

function navMenu(data) {
  navMenuRead(data, 'navMenu')
}

function navMenuBaseline(data) {
  navMenuRead(data, 'navMenuBaseline')
}

// 業務データの場面（Intent 261016-business-data-views の U1、B2）。どの場面も1回の繰り返しに要求1つで、要求の名前（name のタグ）は
// 場面の名前と同じ。行の値と主キーの値は出力しない（状態コードと件数だけを checks で見る）。
function bizUserEmail(n) {
  return `perf-biz-${two(n)}@example.test`
}

function bizHeaders(data) {
  return roleHeaders(data.bizTokens[exec.vu.idInTest])
}

function bizDefinitionRequest(headers, table, name) {
  return jsonPost(`${BIZ_API}/definition`, { schema: BIZ_SCHEMA, table }, headers, name)
}

function bizSearchRequest(headers, table, body, name) {
  return jsonPost(
    `${BIZ_API}/rows/search`,
    { schema: BIZ_SCHEMA, table, conditions: [], sort: null, page: 1, size: BIZ_PAGE_SIZE, ...body },
    headers,
    name,
  )
}

// 業務データの場面の用意。準備の場面は操作する管理者のトークンを取る。ほかの場面は試験用の利用者のトークンを VU の数だけ取り、
// 前の場面の状態に頼らず、準備の場面が作った前提（定義の API が 200）がそろっていることを確かめてから始める（NFR1.2、基盤の R-02）。
function setupBusinessData(data) {
  if (SCENARIO === 'bizPrepare') {
    if (!BIZ_DSL_BODY) fail('DSL_FILE を指定してください（perf/make-biz-dsl.mjs で作った DSL。perf/README.md の業務データの場面）')
    data.roleToken = tokenOf(login(roleOperatorEmail(), USER_PASSWORD))
    return
  }
  if (VUS > BIZ_USER_COUNT) fail(`VUS（${VUS}）が試験用の利用者の数 BIZ_USER_COUNT（${BIZ_USER_COUNT}）を超えています`)
  data.bizTokens = {}
  for (let vu = 1; vu <= VUS; vu++) data.bizTokens[vu] = tokenOf(login(bizUserEmail(vu), USER_PASSWORD))
  const headers = roleHeaders(data.bizTokens[1])
  const table = SCENARIO === 'bizSearchWide' ? BIZ_WIDE_TABLE : BIZ_ITEM_TABLE
  const definition = bizDefinitionRequest(headers, table, 'bizSetup')
  if (definition.status !== 200) {
    fail(`業務データの定義を読めませんでした: ${definition.status} ${codeOf(definition)}（先に bizPrepare を流してください）`)
  }
  if (SCENARIO === 'bizSearchLast' || SCENARIO === 'bizSearchLastSorted' || SCENARIO === 'bizSearchContains') {
    const conditions =
      SCENARIO === 'bizSearchContains' ? [{ column: BIZ_CONTAINS_COLUMN, value: BIZ_CONTAINS_VALUE }] : []
    const first = bizSearchRequest(headers, BIZ_ITEM_TABLE, { conditions }, 'bizSetup')
    if (first.status !== 200) fail(`業務データの一覧を読めませんでした: ${first.status} ${codeOf(first)}`)
    data.bizTotal = first.json('total')
    if (data.bizTotal < 1) fail('業務データの一覧が 0 件です（種の台本を流してください）')
    data.bizLastPage = Math.ceil(data.bizTotal / BIZ_PAGE_SIZE)
    data.bizLastCount = data.bizTotal - (data.bizLastPage - 1) * BIZ_PAGE_SIZE
  }
}

// 準備の場面（1 VU・1 回）。DSL の投入と適用、ロール BIZ_ROLE_NAME の用意（無ければ作る）とスキーマ bizperf への READ の保存、
// 試験用の利用者 perf-biz-01〜への割り当て、1人ずつログインして作業ロールの保存を API で行う（使い捨ての環境の監査に残る）。
// 前の回が残したロール・割り当て（409 ROLE_NO_CHANGE）も受け入れ、何度流しても同じ状態になる。
function bizPrepare(data) {
  const token = data.roleToken
  const headers = roleHeaders(token)
  const { submit, apply } = navApplyDsl(token, BIZ_DSL_BODY, 'bizPrepare')
  check(submit, { 'dsl submit 201': (r) => r.status === 201 })
  check(apply, { 'dsl apply 200': (r) => r.status === 200 })
  const roleId = roleIdByName(token, BIZ_ROLE_NAME) || roleCreate(token, BIZ_ROLE_NAME, 'bizPrepare')
  const saved = jsonPut(
    `${ROLE_API}/${roleId}/permissions`,
    {
      scope: { schemaName: BIZ_SCHEMA, tableName: null },
      entries: [{ schemaName: BIZ_SCHEMA, tableName: null, columnName: null, main: 'READ', create: null, delete: null }],
    },
    headers,
    'bizPrepare',
  )
  check(saved, {
    'permission saved': (r) => r.status === 204 || (r.status === 409 && codeOf(r) === 'ROLE_NO_CHANGE'),
  })
  const ids = idsBySearch(headers, 'perf-biz-')
  for (let n = 1; n <= BIZ_USER_COUNT; n++) {
    const userId = ids[bizUserEmail(n)]
    if (!userId) fail(`試験用の利用者 ${bizUserEmail(n)} がいません（perf/README.md の手順で入れてください）`)
    roleAssignEnsured(token, roleId, { userId })
  }
  let switched = true
  let readable = true
  for (let n = 1; n <= BIZ_USER_COUNT; n++) {
    // 1人ずつログインする（ロックの状態の行を作る。project.md の学び）
    const userHeaders = roleHeaders(tokenOf(login(bizUserEmail(n), USER_PASSWORD)))
    switched = jsonPut(`${BASE}/api/me/work-role`, { roleId }, userHeaders, 'bizPrepare').status === 204 && switched
    for (const table of [BIZ_ITEM_TABLE, 'biz_pair', BIZ_WIDE_TABLE]) {
      readable = bizDefinitionRequest(userHeaders, table, 'bizPrepare').status === 200 && readable
    }
  }
  check(switched, { 'work roles 204': (value) => value })
  check(readable, { 'definitions 200': (value) => value })
}

function bizDefinition(data) {
  const res = bizDefinitionRequest(bizHeaders(data), BIZ_ITEM_TABLE, 'bizDefinition')
  check(res, { 'bizDefinition 200': (r) => r.status === 200 && r.json('listColumns').length === 100 })
}

function bizSearchFirst(data) {
  const res = bizSearchRequest(bizHeaders(data), BIZ_ITEM_TABLE, {}, 'bizSearchFirst')
  check(res, {
    'bizSearchFirst 200': (r) =>
      r.status === 200 && r.json('items').length === BIZ_PAGE_SIZE && r.json('total') === BIZ_ROWS,
  })
}

function bizSearchContains(data) {
  const conditions = [{ column: BIZ_CONTAINS_COLUMN, value: BIZ_CONTAINS_VALUE }]
  const res = bizSearchRequest(bizHeaders(data), BIZ_ITEM_TABLE, { conditions }, 'bizSearchContains')
  check(res, {
    'bizSearchContains 200': (r) =>
      r.status === 200 && r.json('total') === data.bizTotal && r.json('items').length === Math.min(BIZ_PAGE_SIZE, data.bizTotal),
  })
}

function bizSearchLast(data) {
  const res = bizSearchRequest(bizHeaders(data), BIZ_ITEM_TABLE, { page: data.bizLastPage }, 'bizSearchLast')
  check(res, {
    'bizSearchLast 200': (r) => r.status === 200 && r.json('items').length === data.bizLastCount,
  })
}

// 主キー以外（索引の無い c_001）で並べ替えた最後のページ（NFR 設計のレビュー R-05、計画の D2: A）
function bizSearchLastSorted(data) {
  const res = bizSearchRequest(
    bizHeaders(data),
    BIZ_ITEM_TABLE,
    { page: data.bizLastPage, sort: { column: BIZ_SORT_COLUMN, direction: 'ASC' } },
    'bizSearchLastSorted',
  )
  check(res, {
    'bizSearchLastSorted 200': (r) => r.status === 200 && r.json('items').length === data.bizLastCount,
  })
}

// 主キーで1行の詳細。VU と繰り返しの番号から行を決め、大きな値（2,000,000 字）の行も回る
function bizDetail(data) {
  const n = ((exec.vu.idInTest * 7919 + exec.vu.iterationInScenario * 104729) % BIZ_ROWS) + 1
  const res = jsonPost(
    `${BIZ_API}/rows/get`,
    { schema: BIZ_SCHEMA, table: BIZ_ITEM_TABLE, key: { id: String(n) } },
    bizHeaders(data),
    'bizDetail',
  )
  check(res, { 'bizDetail 200': (r) => r.status === 200 })
}

// 最悪の応答（100 件 × 100 カラム × 100 字）。メモリの確かめの場面で、応答時間の判定には数えない（NFR2.5）
function bizSearchWide(data) {
  const res = bizSearchRequest(bizHeaders(data), BIZ_WIDE_TABLE, {}, 'bizSearchWide')
  check(res, {
    'bizSearchWide 200': (r) =>
      r.status === 200 && r.json('items').length === BIZ_PAGE_SIZE && r.json('items.0.truncatedColumns').length === 99,
  })
}

// 業務データの書き込みの場面（Intent 261016-business-data-views の U2、B4）。どの場面も1回の繰り返しに要求1つで、要求の名前（name の
// タグ）は場面の名前と同じ。行の値と主キーの値は出力しない（状態コードと code だけを checks で見る）。
function bizNewValues(uCode, refId, text) {
  const values = { u_code: uCode, ref_id: refId }
  for (let i = 1; i <= BIZ_NEW_TEXT_COLUMNS; i++) values[`n_${String(i).padStart(3, '0')}`] = text(i)
  return values
}

function bizWriteBody(data, extra) {
  return { schema: BIZ_SCHEMA, table: BIZ_NEW_TABLE, definitionId: data.bizDefinitionId, ...extra }
}

function bizCreateRequest(data, headers, values, name) {
  return jsonPost(`${BIZ_API}/rows`, bizWriteBody(data, { values }), headers, name)
}

// 書き込みのロールの権限を保存する（前の回が残した同じ設定の 409 ROLE_NO_CHANGE も受け入れる）
function bizSavePermission(headers, roleId, tableName, main, create, del) {
  const res = jsonPut(
    `${ROLE_API}/${roleId}/permissions`,
    {
      scope: { schemaName: BIZ_SCHEMA, tableName },
      entries: [{ schemaName: BIZ_SCHEMA, tableName, columnName: null, main, create, delete: del }],
    },
    headers,
    'bizSetup',
  )
  if (res.status !== 204 && !(res.status === 409 && codeOf(res) === 'ROLE_NO_CHANGE')) {
    fail(`書き込みのロールの権限を保存できませんでした: ${res.status} ${codeOf(res)}`)
  }
}

// 業務データの書き込みの場面の用意。前の場面の状態に頼らず、書き込みのロールの用意・割り当て・作業ロールの保存・トークンを自分で行い、
// 定義の API で書き込みが使えることを確かめる（U1 の基盤の設計のレビュー R-02、project.md の学び）。更新は VU ごとに1行、削除は
// ITERATIONS 件の行を API で作る（使い捨ての環境の監査に残る）。
function setupBusinessDataWrite(data) {
  if (VUS > BIZ_USER_COUNT) fail(`VUS（${VUS}）が試験用の利用者の数 BIZ_USER_COUNT（${BIZ_USER_COUNT}）を超えています`)
  const operator = tokenOf(login(roleOperatorEmail(), USER_PASSWORD))
  const operatorHeaders = roleHeaders(operator)
  const roleId = roleIdByName(operator, BIZ_WRITER_ROLE_NAME) || roleCreate(operator, BIZ_WRITER_ROLE_NAME, 'bizSetup')
  bizSavePermission(operatorHeaders, roleId, null, 'READ', null, null)
  for (const table of [BIZ_NEW_TABLE, BIZ_REF_TABLE]) bizSavePermission(operatorHeaders, roleId, table, 'FULL', true, true)
  const ids = idsBySearch(operatorHeaders, 'perf-biz-')
  data.bizTokens = {}
  for (let vu = 1; vu <= VUS; vu++) {
    const userId = ids[bizUserEmail(vu)]
    if (!userId) fail(`試験用の利用者 ${bizUserEmail(vu)} がいません（perf/README.md の手順で入れてください）`)
    roleAssignEnsured(operator, roleId, { userId })
    // 1人ずつログインする（ロックの状態の行を作る。project.md の学び）
    const token = tokenOf(login(bizUserEmail(vu), USER_PASSWORD))
    const switched = jsonPut(`${BASE}/api/me/work-role`, { roleId }, roleHeaders(token), 'bizSetup')
    if (switched.status !== 204) fail(`作業ロールを保存できませんでした: ${switched.status} ${codeOf(switched)}`)
    data.bizTokens[vu] = token
  }
  const headers = roleHeaders(data.bizTokens[1])
  const definition = bizDefinitionRequest(headers, BIZ_NEW_TABLE, 'bizSetup')
  if (definition.status !== 200) {
    fail(`書き込みの表の定義を読めませんでした: ${definition.status} ${codeOf(definition)}（先に bizPrepare を流してください）`)
  }
  const operations = definition.json('operations') || []
  if (!definition.json('writeConfigured') || !['CREATE', 'UPDATE', 'DELETE'].every((op) => operations.includes(op))) {
    fail('書き込みが使えません（アプリの一時の環境ファイルの MASTERSMITH_TARGET_DB_WRITE_* と、書き込みのロールを確かめてください）')
  }
  data.bizDefinitionId = definition.json('definitionId')
  data.runId = Date.now().toString(36)
  const make = (label) => {
    const res = bizCreateRequest(
      data,
      headers,
      bizNewValues(`${data.runId}-${label}`, '1', (i) => `用意${i}`),
      'bizSetup',
    )
    if (res.status !== 201) fail(`書き込みの場面の行を作れませんでした: ${res.status} ${codeOf(res)}`)
    return res.json('key.id')
  }
  if (SCENARIO === 'bizUpdate') {
    data.bizUpdateIds = {}
    for (let vu = 1; vu <= VUS; vu++) data.bizUpdateIds[vu] = make(`u${vu}`)
  }
  if (SCENARIO === 'bizDelete') {
    data.bizDeleteIds = []
    for (let n = 0; n < ITERATIONS; n++) data.bizDeleteIds.push(make(`d${n}`))
  }
}

function bizWriteHeaders(data) {
  return roleHeaders(data.bizTokens[exec.vu.idInTest])
}

// 作成（一意の u_code と、参照先にある ref_id を満たす）
function bizCreate(data) {
  const vu = exec.vu.idInTest
  const n = exec.vu.iterationInScenario
  const values = bizNewValues(`${data.runId}-c${vu}-${n}`, String(((vu + n) % BIZ_REF_ROWS) + 1), (i) => `書き込み${i}`)
  const res = bizCreateRequest(data, bizWriteHeaders(data), values, 'bizCreate')
  check(res, { 'bizCreate 201': (r) => r.status === 201 && r.json('key.id') !== undefined })
}

// 更新（VU ごとに別の行の1カラム。同じ行の排他の待ちを混ぜない）
function bizUpdate(data) {
  const vu = exec.vu.idInTest
  const res = http.patch(
    `${BIZ_API}/rows`,
    JSON.stringify(
      bizWriteBody(data, {
        key: { id: String(data.bizUpdateIds[vu]) },
        values: { n_001: `更新${exec.vu.iterationInScenario}` },
      }),
    ),
    { headers: { ...bizWriteHeaders(data), 'Content-Type': 'application/json' }, tags: { name: 'bizUpdate' } },
  )
  check(res, { 'bizUpdate 200': (r) => r.status === 200 })
}

// 削除（setup で作った行を1回に1行）
function bizDelete(data) {
  const id = data.bizDeleteIds[exec.scenario.iterationInTest]
  const res = jsonPost(`${BIZ_API}/rows/delete`, bizWriteBody(data, { key: { id: String(id) } }), bizWriteHeaders(data), 'bizDelete')
  check(res, { 'bizDelete 200': (r) => r.status === 200 })
}

// 参照先に無い値での作成（違反の道。409 BUSINESS_REFERENCE_NOT_FOUND を正しい答えとして数える）
function bizWritePoolLimit(data) {
  const vu = exec.vu.idInTest
  const n = exec.vu.iterationInScenario
  const values = bizNewValues(`${data.runId}-p${vu}-${n}`, '999999', (i) => `違反${i}`)
  const res = bizCreateRequest(data, bizWriteHeaders(data), values, 'bizWritePoolLimit')
  check(res, {
    'bizWritePoolLimit 409': (r) => r.status === 409 && codeOf(r) === 'BUSINESS_REFERENCE_NOT_FOUND',
  })
}

// 4 MiB 近い本文の作成（成功の道。97 カラム × 10,000 字 × 4 バイト。NFR2.8）
function bizCreateLarge(data) {
  const vu = exec.vu.idInTest
  const n = exec.vu.iterationInScenario
  const values = bizNewValues(`${data.runId}-l${vu}-${n}`, String(((vu + n) % BIZ_REF_ROWS) + 1), () => BIZ_LARGE_VALUE)
  const res = bizCreateRequest(data, bizWriteHeaders(data), values, 'bizCreateLarge')
  check(res, { 'bizCreateLarge 201': (r) => r.status === 201 })
}

const USER_SCENARIOS = {
  monitorLight,
  preferencesGet,
  preferencesSave,
  preferencesInvalid,
  passwordChange,
  passwordMismatch,
  passwordInvalid,
  invite,
  invitationResend,
  invitationList,
  invitationCancel,
  registrationVerify,
  registrationComplete,
  registrationInvalid,
  registrationRejected,
  userAdminList,
  userAdminProfile,
  userAdminOps,
  userAdminSuspendWorst,
  userAdminPool,
  userAdminPoolLimit,
  groupListFirst,
  groupListLast,
  groupDetail,
  groupRename,
  groupCreate,
  groupDelete,
  groupMemberAdd,
  groupMemberRemove,
  groupPoolLimit,
  roleTreeRead,
  rolePermissionSave,
  roleAdminRead,
  roleAdminOps,
  workRoleSwitch,
  workRoleRead,
  myPermissionsTree,
  roleAssignmentsRead,
  roleAssignOps,
  rolePoolLimit,
  roleExport,
  rolePrepare,
  roleTransferLargeCheck,
  roleTransferLargeApply,
  navPrepare,
  navMenu,
  navMenuBaseline,
  bizPrepare,
  bizDefinition,
  bizSearchFirst,
  bizSearchContains,
  bizSearchLast,
  bizSearchLastSorted,
  bizDetail,
  bizSearchWide,
  bizCreate,
  bizUpdate,
  bizDelete,
  bizWritePoolLimit,
  bizCreateLarge,
}

export default function (tokens) {
  const vu = exec.vu.idInTest
  if (SCENARIO === 'health') {
    const res = http.get(`${BASE}/actuator/health`, { tags: { name: 'health' } })
    check(res, { 200: (r) => r.status === 200 })
  } else if (SCENARIO === 'loginSuccess') {
    const res = login(userEmail(((vu - 1) % 10) + 1), USER_PASSWORD)
    check(res, { 200: (r) => r.status === 200 })
  } else if (SCENARIO === 'loginFailure') {
    const res = login(
      `perf-nobody-${vu}-${exec.vu.iterationInScenario}@example.test`,
      'wrong-password-123',
    )
    check(res, { 401: (r) => r.status === 401 })
  } else if (SCENARIO === 'refresh') {
    if (!vuState.cookie) {
      const res = login(userEmail(((vu - 1) % 10) + 1), USER_PASSWORD)
      tokenOf(res)
      vuState.cookie = res.cookies[REFRESH_COOKIE][0].value
    }
    const res = http.post(`${BASE}/api/auth/session/refresh`, null, {
      headers: { Origin: BASE, Cookie: `${REFRESH_COOKIE}=${vuState.cookie}` },
      tags: { name: 'refresh' },
    })
    if (check(res, { 200: (r) => r.status === 200 }) && res.cookies[REFRESH_COOKIE]) {
      vuState.cookie = res.cookies[REFRESH_COOKIE][0].value
    }
  } else if (SCENARIO === 'adminCheck') {
    const res = http.get(`${BASE}/api/admin/check`, {
      headers: { Authorization: `Bearer ${tokens.admin}` },
      tags: { name: 'adminCheck' },
    })
    check(res, { 204: (r) => r.status === 204 })
  } else if (SCENARIO === 'forbidden') {
    const res = http.get(`${BASE}/api/admin/check`, {
      headers: { Authorization: `Bearer ${tokens.user}` },
      tags: { name: 'forbidden' },
    })
    check(res, { 403: (r) => r.status === 403 })
  } else if (SCENARIO === 'appearance') {
    // トークンを付けない（画面の側も付けない。U8 の BR3.3）。試験用の利用者は要らず、監査ログも増えない。
    const res = http.get(`${BASE}/api/appearance`, { tags: { name: 'appearance' } })
    check(res, {
      200: (r) => r.status === 200,
      'two items': (r) => {
        if (r.status !== 200) return false
        const keys = Object.keys(r.json()).sort()
        return keys.length === 2 && keys[0] === 'brandColor' && keys[1] === 'fontFamily'
      },
    })
  } else if (SCENARIO === 'dslPrepare') {
    dslPrepare(tokens)
  } else if (SCENARIO === 'dslLight') {
    dslLight(tokens)
  } else if (SCENARIO === 'dslCycle') {
    dslCycle(tokens)
  } else if (USER_SCENARIOS[SCENARIO]) {
    USER_SCENARIOS[SCENARIO](tokens)
  } else {
    fail(`知らない SCENARIO です: ${SCENARIO}`)
  }
}
