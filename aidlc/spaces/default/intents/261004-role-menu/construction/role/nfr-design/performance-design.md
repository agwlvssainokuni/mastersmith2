# 性能の設計 — U4 role

## 出典

- この単位の承認済みの NFR 要件 `construction/role/nfr-requirements/performance-requirements.md`（NFR2.2〜NFR2.6・NFR2.10）と、その読み直しの記録の R-03・R-05・R-06
- この単位の承認済みの機能設計 `construction/role/functional-design/functional-spec.md`（2.5・2.9〜2.15）
- `contract-summary.md`（C5・C7・C8）
- この段の答え: `nfr-design-questions.md` の Q2・Q4（どちらも A）とまとめの確認
- 捨ての試しの結果: `reliability-design.md` の 1節（U3〜U5）
- group の NFR 設計の `performance-design.md`（場面と判定の形）

## 1. 問い合わせの形（NFR2.3・NFR2.6）

| 口・API | 問い合わせ | 持つ形 |
|---|---|---|
| 有効な作業ロールを決める読み取り（`effectiveWorkRole`） | 直接の割り当て・所属のグループ（group の `groupIdsOfUser`）・グループの割り当て（グループの ID の集合で1回）・作業ロールの保存。合計4回 | ID だけを読む。ロールの名前は、決まった1つのロールについて、写しを作るときにまとめて読む |
| `snapshotFor` | 上の4回と、作業ロールの設定の行をまとめて1回 | 行の値だけを読む射影（エンティティにしない）。写しの中では名前の組をキーにした対応表で持つ |
| `resolve` | 上の4回と、対象の祖先の行（スキーマ・テーブル・カラムの最大3行）を1回 | 行の値だけの射影 |
| 権限の木の1階層 | 開いた階層の範囲の設定の行を1回（スキーマなら `table_name = ''`、テーブルならそのテーブルの行） | 主キーの先頭（ロール・スキーマ名・テーブル名）で範囲を読む |
| ロールの一覧 | 件数・ページの分のロール・直接の割り当ての数（ページの ID の集合で集計）を各1回 | 集計の射影 |
| 割り当ての一覧 | ロール・直接の割り当て・グループの割り当て・`memberUserIds`・`user.service` のまとめて読む口を、各1回 | ID の集合で読む |
| 書き出し | ロールと設定の行をまとめて1回ずつ | 行の値だけの射影。文字列の組み立ては `role.transfer` |
| import の確かめ・適用 | 置き換えるロールの設定の行をロールごとに1回。適用の書き込みは 1,000 件ずつの JDBC のバッチ | `reliability-design.md` の 2.5 |

- 試し U5 では、`snapshotFor`（1万行）が p95 3 ms、`resolve` が p95 1 ms だった。予算の 300 ms に対して大きく余裕があるため、キャッシュは置かない（ADR-006 の切り替え先は使わない）。
- `resolve` の口の説明（Javadoc）に、次の3つを書く（読み直しの R-11・Q2: A）。
  - 「1回の要求で対象が1つのとき（テーブルの置き場など）に使ってよい」。
  - 「2つ以上の対象を判定する呼ぶ側は `snapshotFor` を1回呼ぶ」。
  - 理由は、問い合わせの数と、1回の要求の中で同じ作業ロールで答える一貫性。
- 問い合わせの数は、結合テスト `EffectivePermissionQueryCountIT`（NFR2.3）と `RoleAdminQueryCountIT`（NFR2.6）で数える（Hibernate の統計と、JDBC の部品の呼び出しの数の両方）。

## 2. 応答時間の確かめ（NFR2.2・NFR2.4・NFR2.5）

### 2.1 場面

承認済みの NFR2.2・NFR2.4・NFR2.5 の場面のとおり。どれも 10 VU・3 分（`roleTransferLarge` は 1 VU・1 回）。

- `roleTreeRead`・`rolePermissionSave`・`workRoleSwitch`（NFR2.2）
- `myPermissionsTree`（NFR2.3 の時間）
- `roleTransferLarge`（NFR2.4）
- `roleAdminRead`・`roleAdminOps`・`roleAssignOps`・`roleAssignmentsRead`・`workRoleRead`・`roleExport`（NFR2.5）

### 2.2 判定

- 判定する場面は、どれも1回の繰り返しに要求1つにする。判定は `iteration_duration` の p95 で行う（`roleTransferLarge` は最大）。`http_req_duration{name:…}` は並べて記録する（NFR2.10）。
- 操作が2つ以上の場面は、`exec.vu.metrics.tags` の `op` で操作ごとに判定する。B4 の台本の前に `k6 inspect --include-system-env-vars` と短い試し走りで、タグが `iteration_duration` に付くことを確かめる（読み直しの R-03）。付かないときは、操作ごとに場面を分ける。分けた後の名前は次のとおり。
  - `roleAdminOpsCreate`・`roleAdminOpsDelete`・`roleAdminOpsRename`
  - `roleAssignOpsUser`・`roleAssignOpsGroup`
  - `roleAdminReadList`・`roleAdminReadOne`・`roleAdminReadGroupRoles`・`roleAdminReadUserRoles`
  - `roleTreeReadSchemas`・`roleTreeReadTables`・`roleTreeReadColumns`
  - `myPermissionsTreeSchemas`・`myPermissionsTreeTables`・`myPermissionsTreeColumns`
- `checks` の率は 1。トークンは `setup()` で取り、場面の長さは 3 分。台本全体を `caffeinate -i` で包む。

### 2.3 import の時間の見通し（NFR2.4、Q4: A）

- 試し（上限の 96%）では、確かめ 613〜755 ms、適用 3,393〜3,736 ms（削除 約 1.1 秒・確定 約 1.4〜1.5 秒）だった。本番の Hibernate と `TraceAspect` の上乗せを見込んでも、目標の確かめ 15 秒・適用 30 秒に届く見通し。
- 上限ちょうどのファイルは、ロールの数・カラムの数・名前の長さを引数に取る生成の部品で、バイト数を 10,485,760 にちょうど合わせて作る（テストと k6 で同じ部品を使う。読み直しの R-05）。
- 適用の1文（削除1つ・バッチ1つ）は最大 57 ms。文の上限 10 秒は、`jakarta.persistence.query.timeout` が JDBC の部品には効かないため、`JdbcTemplate` の `setQueryTimeout(10)` で明示する（`reliability-design.md` の 2.5）。バッチは 1,000 件。
- 試しが測った形は長い名前の 26 ロール × 1万カラムだけ。節の数が上限（1,000,000）に近い短い名前の形を、Build and Test で1回測る（`reliability-design.md` の 1.3、読み直しの R-10）。

## 3. 時間の予算

| 経路 | 予算（p95） | 内訳の見積もり |
|---|---|---|
| 権限の木の1階層 | 1 秒 | DSL の写し（メモリ）・設定の範囲の読み取り（数 ms）・継承の計算 |
| 権限の保存（100 カラムの表） | 1 秒 | 排他・今の値の読み取り・差分の書き込み・確定・監査の1行 |
| 作業ロールの切り替え | 1 秒 | 4回の読み取り（数 ms）・保存の1行・監査の1行 |
| `snapshotFor` | 300 ms | 試しで p95 3 ms（1万行） |
| 自分の権限の木 | 1 秒 | `snapshotFor` と DSL の写し |
| import の確かめ／適用 | 15 秒／30 秒 | 試しで 0.8 秒／3.7 秒（96%） |

## 4. 要件との差

- **写しと `resolve` の読み取りはエンティティにしない**: 承認済みの要件に書き方の定めは無い。試しの値を本番に近づけるため、行の値だけを読む射影にした。
- **import の書き込みは JDBC のバッチ**: 要件・機能設計に定めは無い。main ではまだ使っていない `JdbcTemplate` の書き方で、`role/store` の中に閉じる（`logical-components.md`）。

## 読み直し1回目の直し

読み直し（NOT-READY）を受け、依頼者の決定（Q5: A と、指摘をすべて直す）で、この成果物の次を直した。

- **R-06**: 2.3 の文の上限の根拠を、`JdbcTemplate` の `setQueryTimeout(10)` に直した。4節に、`JdbcTemplate` が main で新しい書き方であることを書いた。
- **R-10**: 2.3 に、短い名前の形を Build and Test で1回測る引き継ぎを足した。

## 承認の場の決定と直し

承認の場で依頼者が Request Changes を選び、決定の文は「推奨の案のとおり直す」（直す範囲: 各単位の読み直しの Major と、単位の間でそろえる3点）。この成果物では次を直した。

- この成果物に当たる直しは無かった（鍵の待ちの上限は `reliability-design.md` の 2.1、合否の形は `scalability-design.md` の 2.4）。
