# 性能の要件 — U4 role

## 出典

- `functional-spec.md`・`rules.md`（この単位の承認済みの機能設計。2.5〜2.15 の流れ、BR4.10・BR5.4・BR6.11・BR9）
- `requirements.md`（NFR2.1〜NFR2.4、前提 A6）
- `contract-summary.md`（C5・C7・C8）
- `technology-stack.md`（コード知識ベース）
- この段の答え: `nfr-requirements-questions.md` の Q1: A（import を除く role のすべての API に p95 1 秒）、Q2: A（`resolve` は祖先の行だけ、`snapshotFor` は悪い側で 300 ミリ秒）、Q3: A（悪い側の規模）、Q4: A（10 MiB、確かめ 15 秒・適用 30 秒）と、まとめの確認（k6 の判定の指標を含む）。
- group の NFR 要件のレビューの R-01（判定の指標）・R-05（時間の目標）を先に避ける。

## ID の対応表

全体の表は `security-requirements.md` の「ID の振り方」にある。この成果物に書くのは次のもの。

| 上流の枝番 | この単位での扱い | この単位の ID |
|---|---|---|
| NFR2.2 木・保存・作業ロールの切り替えの時間 | 部分（メニューの時間は U5 navigation） | NFR2.2 |
| NFR2.3 要求ごとの権限の読み出しの重さ | 当たる | NFR2.3 |
| NFR2.4 import の時間 | 当たる（大きさの上限は `scalability-requirements.md` の NFR2.9） | NFR2.4 |
| （足す） | role のほかの API の時間、問い合わせの数、k6 の判定の決まり | NFR2.5・NFR2.6・NFR2.10 |

規模の前提（NFR2.1）、接続プール（NFR2.7・NFR2.11）、件数の上限を置かないこと（NFR2.8）、YAML の大きさの上限（NFR2.9）は `scalability-requirements.md` に書く。

## 要件

| ID | 要件 | 測り方・確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR2.2 | 権限の設定の木の1階層（スキーマ・テーブル・カラム）を開く API、保存（PUT）、作業ロールの切り替え（PUT `/api/me/work-role`）の応答時間は、95 パーセンタイルで 1 秒以内。k6 の `checks` の率は 1。目標は緩めない | k6 に場面を足す（判定は NFR2.10 のとおり、どれも 10 VU・3 分）。<br>・`roleTreeRead`: 1万カラムすべてに明示の値のロールの木で、スキーマ・100 テーブル・100 カラムのテーブルを順に開く。<br>・`rolePermissionSave`: VU ごとに自分のロールの 100 カラムのテーブルの表を、繰り返しごとに READ と FULL を交互に保存する。変わる点が必ずあり、`ROLE_NO_CHANGE` にならない。<br>・`workRoleSwitch`: VU ごとに自分の利用者で、2つのロールを交互に選ぶ。データは NFR2.1 の悪い側。測れない段では `Unverified` とし、持ち主の段を明記して引き継ぐ | 台本とデータの用意は Code Generation（木と保存は B4、切り替えは B5）、流すのは Performance Validation |
| NFR2.3 | 要求ごとの実効の権限の解決（契約 C5）は、次の数と時間に収める（Q2: A）。<br>・**有効な作業ロールを決める読み取り**: 利用者のロールの数・グループの数によらず、4 回以内の問い合わせ（直接の割り当て・所属のグループ（group の `groupIdsOfUser`）・グループの割り当て・作業ロールの保存）。<br>・**`resolve(userId, target)`**: 全件を読まず、その対象の祖先の行（スキーマ・テーブル・カラムの最大3行）だけを1回の問い合わせで読む。そのため `resolve` 1回あたりの問い合わせは、有効な作業ロールを決める読み取りの4回と祖先の行の1回で、合計5回以内になる。対象ごとに呼ぶと、呼ぶ数だけこの5回がくり返される。これは承認済みの契約 C5 と機能設計の 2.9（`resolve` は `snapshotFor` の写しを使う）との差で、`tech-stack-decisions.md` の「契約との差」に記録した（R-01 の直し）。たくさんの対象を扱う呼ぶ側（業務のメニュー・自分の権限の木・後の Intent の一覧）は `snapshotFor` を1回呼んで写しを使う、と口の Javadoc に書く。<br>・**`snapshotFor(userId)`**: 有効な作業ロールの設定の行をまとめて1回で読む。<br>・**時間の予算**: 悪い側（1万カラムに明示の値のロールを作業ロールにし、利用者は 100 のグループに属し、各グループにロールを割り当てる）で、`snapshotFor` 1回は 300 ミリ秒以内（group の `groupIdsOfUser` の時間を含む） | 結合テスト `EffectivePermissionQueryCountIT`（Hibernate の統計で問い合わせの数を数える）。割り当て 1 件と 100 件、グループ 1 と 100、設定の行 1 と 1 万で、決める読み取りが 4 回以内、`resolve` が祖先の行の1回であること。<br>時間: NFR 設計の捨ての試しのコードで先に測る（NFR3.7）。Performance Validation では、k6 の `myPermissionsTree`（自分の権限の木のテーブルの階層を開く。1 VU が悪い側の利用者、10 VU・3 分）の p95 が 1 秒以内であることで確かめる | Code Generation（B5）、捨ての試しは NFR Design、時間は Performance Validation |
| NFR2.4 | 権限の YAML の確かめと適用の時間は、上限ちょうど（10 MiB、約 25 万の対象を置き換える）のファイルで、確かめ 15 秒以内・適用 30 秒以内。くり返す操作ではないため、1回の要求の時間で判定する。届かないことが NFR 設計の捨ての試しで分かったときは、上限を下げる案をその段で依頼者に諮る（Q4: A） | k6 に場面 `roleTransferLarge` を足す。1 VU・1 回で、確かめ → 適用の順に流す（1回の繰り返しに要求1つ。確かめと適用は別の場面にし、適用は確かめの指紋を `setup()` で受ける）。判定は各場面の `iteration_duration` の最大。データは置き換える先のロール 25 個（各1万カラム明示）。使い捨ての環境で、適用の後は設定が入れ替わったことを件数で確かめる | 台本とファイルの用意は Code Generation（B6）、時間の先の測りは NFR Design、流すのは Performance Validation |
| NFR2.5 | role のほかの API の応答時間は、95 パーセンタイルで 1 秒以内（Q1: A）。k6 の `checks` の率は 1。<br>・対象: ロールの一覧・1件の読み取り・作成・名前の変更・削除、割り当て・外し（利用者・グループ）、割り当ての一覧、グループのロールと利用者のロールの読み取り、作業ロールの読み取り、自分の権限の木の3つ、書き出し、今の DSL に無い設定を消す。<br>・書き出しは、悪い側（ロール 1,000、うち1つは1万カラム明示）の全件でも 1 秒以内 | k6 に場面を足す（判定は NFR2.10、どれも 10 VU・3 分）。<br>・`roleAdminRead`: 一覧の1ページ目と最後のページ、1件、グループのロール、利用者のロール。<br>・`roleAdminOps`: VU ごとに作成と削除を交互にくり返す（名前の変更は自分のロールの名前を交互に変える）。<br>・`roleAssignOps`: VU ごとに自分のロールを、利用者とグループへ割り当てと外しを交互にくり返す。グループの側はメンバー 1,000 人のグループ。<br>・`roleAssignmentsRead`: メンバー 1,000 人のグループに割り当てたロールの割り当ての一覧。<br>・`workRoleRead`: 100 のグループに属する利用者の作業ロールの読み取り。<br>・`myPermissionsTree`: 自分の権限の3つの階層。<br>・`roleExport`: 書き出し。<br>操作する管理者は試験用の利用者で、初期管理者は使わない | 台本とデータの用意は Code Generation（B4・B5・B6 でそれぞれの API の分）、流すのは Performance Validation |
| NFR2.6 | 一覧と読み取りの問い合わせの数は、件数に比例して増えない（N+1 にしない）。<br>・ロールの一覧: 件数・ページ分のロール・直接の割り当ての数を、それぞれ1回。<br>・割り当ての一覧: ロール・直接の割り当て・グループの割り当て・`memberUserIds`・`user.service` のまとめて読む口を、それぞれ1回。<br>・木の1階層: DSL の読み取り（メモリ）と、その階層の範囲の設定の行の読み取りの1回 | 結合テスト `RoleAdminQueryCountIT`（ロール 1 件と 20 件、割り当ての利用者 1 人と 50 人、グループ 1 と 10 で、問い合わせの数が同じ） | Code Generation（B4・B5） |
| NFR2.10 | k6 の場面の判定の決まり（group のレビューの R-01 を先に避ける）。<br>・判定する場面は、どれも1回の繰り返しに要求1つにする。状態を戻す操作（作成と削除、割り当てと外し、保存の値、作業ロール）は、繰り返しごとに交互に送る。<br>・操作が2つ以上ある場面は、繰り返しの始めに `exec.vu.metrics.tags` に操作の名前（`op`）を入れ、`iteration_duration{scenario:…,op:…}` の p95 で操作ごとに判定する。タグが `iteration_duration` に付くことを台本を書くときに確かめ、付かなければ操作ごとに場面を分ける。<br>・判定は単調な時計の `iteration_duration` で行い、`http_req_duration{name:…}` は並べて記録する（`project.md` の学び。colima の VM の時計のずれで `http_req_duration` が崩れるため）。<br>・VU ごとのアクセストークンは `setup()` でまとめて取り、場面の長さはトークンの有効期限 5 分より短い 3 分にして、途中でログインし直さない（ログインの要求を判定の繰り返しに混ぜない）。<br>・台本全体を `caffeinate -i` で包み、使い捨ての環境で流す | 台本の書き方の確かめ（`k6 inspect --include-system-env-vars` で場面と閾値の名前を確かめる、`project.md` の学び） | Code Generation（B4〜B6）、Performance Validation |

## 補足

- 書き込みの操作（管理の操作と作業ロールの切り替え）は、確定の後の監査の記録で2本目の接続を使う。接続プールの見積もりと、上限に届かせる場面は `scalability-requirements.md` の NFR2.7。
- import の確かめ・適用の 15〜30 秒は、指標の境界（5000 ms まで）を超えて `+Inf` にしか入らない。時間は k6 の場面で測る（`observability-requirements.md` の NFR5.2）。
- U5 navigation の業務のメニューの時間（NFR2.2 のメニューの部分）は U5 が持ち、U5 は `snapshotFor` を1回だけ呼ぶ（NFR2.3）。

## 承認の場の決定と直し

承認の場で依頼者が Request Changes を選び、直す範囲を「Major 11 件だけを直す」とした。この成果物では、レビュー `aidlc/spaces/default/intents/261004-role-menu/.aidlc-reviews/nfr-requirements/units/role/4e9510ac79c4db11/1.json` の次の指摘を直した。

- **R-01（Major）**: NFR2.3 の `resolve` の形は、承認済みの契約 C5 と機能設計 2.9 との差になっていた。差であることと、`resolve` 1回あたりの問い合わせ（作業ロールの決定の4回と祖先の行の1回、合計5回以内）を NFR2.3 に書いた。差は `tech-stack-decisions.md` の「契約との差」に、U5 への必須の引き継ぎはその U5 の項に記録した。
- 接続の本数の決定的な確かめ（R-02）は `scalability-requirements.md` の NFR2.11 に置いた。
