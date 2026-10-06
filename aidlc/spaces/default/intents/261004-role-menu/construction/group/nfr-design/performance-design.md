# 性能の設計 — U3 group

## 出典

- この単位の承認済みの NFR 要件 `construction/group/nfr-requirements/performance-requirements.md`（NFR2.3・NFR2.5・NFR2.6、承認の場の直し R-01）と、NFR 要件の読み直し（R-05・R-08）
- この単位の承認済みの機能設計（`functional-spec.md` の 2.6・2.7、`entities.md`）
- `contract-summary.md`（C4・C6）、`components.md`（GroupManagement）
- この段の答え: `nfr-design-questions.md` の Q1〜Q3 とまとめの確認
- `perf/k6/scenarios.js`（既存の場面の書き方、`SCENARIO` の環境変数で1つずつ選ぶ形）、`perf/README.md`

## 1. 問い合わせの形（NFR2.3・NFR2.6）

| 口 | 問い合わせ | 回数 | 索引 |
|---|---|---|---|
| `groupIdsOfUser(userId)` | `group_members` を `user_id` で絞り、グループの ID だけを読む | 1 | `group_members(user_id)` の索引を足す（主キーは `(group_id, user_id)` のため、`user_id` だけの絞り込みに効かない） |
| `memberUserIds(groupIds)` | `group_members` を `group_id IN (…)` で絞る | 1 | 主キーの先頭の `group_id` |
| 一覧 | 件数1回、ページの 20 件1回、ページの ID のメンバーの数を `GROUP BY` で1回、問う口の `assignedRoleCounts` を1回 | 4 | `groups` の主キー、`group_members` の主キー |
| 詳細 | グループ1回、メンバーの行1回（`added_at, user_id` の順）、`user.service` のまとめて読む口1回 | 3 | 主キー |
| `exists(groupId)`・`summaries(groupIds)` | 主キーで1回 | 1 | 主キー |

- どの口も、件数・メンバーの数・グループの数に比例して問い合わせの回数が増えない。`GroupMembershipQueryCountIT`・`GroupAdminListQueryCountIT` で、Hibernate の統計を使って回数を数える（グループ 1 件と 20 件、メンバー 1 人と 50 人で同じ回数）。
- **`groupIdsOfUser` の時間**（NFR 要件の読み直しの R-05）: 時間の目安は U4 の NFR2.3 の `snapshotFor` 1回 300 ミリ秒（`groupIdsOfUser` の時間を含む）で確かめる。group の側は、回数 1 と索引だけを持つ。U4 の悪い側（利用者が 100 のグループに属する）は、この索引で1回の読み取りに収まる。
- 詳細でメンバーの氏名・メールアドレス・停止を読む口は、`user.service` に足す（`findSummariesByIds(Set<Long>)`。`IN` の1回）。

## 2. 応答時間の確かめ（NFR2.5）

### 2.1 場面

`perf/k6/scenarios.js` に、操作ごとに1つの場面を足す。どの場面も1回の繰り返しに要求1つ。

| 場面 | 形 | 用意（`setup()`） |
|---|---|---|
| `groupListFirst`・`groupListLast` | 10 VU・3 分の constant-vus。一覧の1ページ目・最後のページ | グループ 1,000 |
| `groupDetail` | 10 VU・3 分。メンバー 1,000 人のグループの詳細 | 利用者 1,000・そのグループ |
| `groupRename` | 10 VU・3 分。VU ごとに自分のグループの名前を2つの名前で交互に変える | VU ごとのグループ |
| `groupCreate` | 10 VU × 100 回の per-vu-iterations、maxDuration 3 分 | なし |
| `groupDelete` | 10 VU × 100 回 | VU ごとに空のグループ 100 |
| `groupMemberAdd` | 10 VU × 100 回 | VU ごとにグループ1つと利用者 100 |
| `groupMemberRemove` | 10 VU × 100 回 | VU ごとにメンバー 100 人のグループ |

### 2.2 判定

- 場面ごとに `iteration_duration{scenario:…}` の p95 < 1000 ms と `checks{scenario:…}` の率 = 1（期待の状態コードだけを合格にする）。`http_req_duration{name:…}` は並べて記録するだけ（colima の VM の時計のずれ、`project.md` の学び）。
- 操作する管理者のアクセストークンは `setup()` で取る。場面は 3 分までで、途中でログインし直さない。台本は既存どおり `SCENARIO` の環境変数で場面を1つずつ選び、場面ごとに `setup()` が走る。

### 2.3 台本の書き方（NFR 要件の読み直しの R-08）

- 繰り返しの中に `sleep` や待ちを置かない（`iteration_duration` に上乗せされるため）。
- `groupRename` の2つの名前は、VU の番号を含める（例: `perf-rename-<VU>-a`・`perf-rename-<VU>-b`）。グループの名前の鍵は全体で一意（大文字と小文字を区別しない）のため、ほかの VU と重ならない形にする。
- `groupCreate` の名前は、VU と繰り返しの番号で一意にする（例: `perf-create-<VU>-<回>`）。
- 台本を書いた後に `k6 inspect --include-system-env-vars` で、場面の名前と閾値の式を確かめる（`project.md` の学び）。
- 台本とデータの用意の手順は B3 で足し、`perf/README.md` に書く。流すのは Performance Validation。使い捨ての環境で、`caffeinate -i` で台本全体を包む。

## 3. 時間の予算

| 区分 | 予算 | 根拠 |
|---|---|---|
| 書き込み1件 | 排他の取得（待ちが無ければ数ミリ秒）＋書き込みと flush＋確定＋確定の後の監査の記録 | 目標 1 秒に対し、待ちが無ければ数十ミリ秒の見込み |
| 排他の待ち | 行は最大 3,000 ms、一意の鍵・主キーは最大約 2,000 ms（H2 の既定） | 待ちの上限切れは `GROUP_BUSY` で返る。p95 の判定は同じ VU が自分のグループだけを触る場面のため、待ちは起きない作り |
| 詳細（メンバー 1,000 人） | 3回の問い合わせと 1,000 行の応答の組み立て | 目標 1 秒の内に収まる見込み。Performance Validation で確かめる |
