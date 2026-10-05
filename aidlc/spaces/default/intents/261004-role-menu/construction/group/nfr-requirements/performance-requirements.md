# 性能の要件 — U3 group

## 出典

- `functional-spec.md`・`rules.md`（この単位の承認済みの機能設計）
- `requirements.md`（NFR2.1〜NFR2.4、前提 A6）
- `contract-summary.md`（C4・C6）
- `technology-stack.md`（コード知識ベース）
- この段の答え: `nfr-requirements-questions.md` の Q1: A（7つの口に p95 1 秒、k6 の場面を足して Performance Validation で流す）・Q2: A（試験の悪い側はグループ 1,000・利用者 1,000 名・メンバー 1,000 人）とまとめの確認。前の Intent `260930-user-admin` の利用者の管理の場面（`perf/k6/scenarios.js` の `userAdmin*`）。

## ID の対応表

同じ番号は上流（`requirements.md`）と同じ意味でだけ使い、足す要件は上流の最後の枝番の次から振る。全体の表は `security-requirements.md` の「ID の振り方」にある。この成果物に書くのは次のもの。

| 上流の枝番 | この単位での扱い | この単位の ID |
|---|---|---|
| NFR2.2 権限の木・メニュー・作業ロールの時間 | 当たらない（U4 role・U5 navigation） | — |
| NFR2.3 要求ごとの権限の読み出しの重さ | 部分（U4 の解決が呼ぶ所属の読み取りの口だけ） | NFR2.3 |
| NFR2.4 import の時間 | 当たらない（U4 role） | — |
| （足す） | グループの管理の API の応答時間と問い合わせの数 | NFR2.5・NFR2.6 |

規模の前提（NFR2.1）と接続プール（NFR2.7・NFR2.8）は `scalability-requirements.md` に書く。

## 要件

| ID | 要件 | 測り方・確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR2.3 | U4 の実効の権限の解決が要求ごとに呼ぶ `GroupMembershipQuery.groupIdsOfUser(userId)` は、1回の問い合わせ（メンバーの表の利用者の ID の索引を使う）で返し、メンバーの数・グループの数に比例して問い合わせが増えない。U4 の割り当ての一覧が呼ぶ `memberUserIds(groupIds)` と、一覧が呼ぶメンバーの数の読み取りも、渡した ID の数によらず1回の問い合わせで返す | 結合テスト `GroupMembershipQueryCountIT`（Hibernate の統計で問い合わせの数を数える。既存の `UserAdminListQueryCountIT` と同じ形）。U4 の解決全体の時間は U4 の NFR2.2・NFR2.3 で測る | Code Generation（B3）、全体は U4 |
| NFR2.5 | グループの管理の API の7つの口（一覧・詳細・作成・名前の変更・削除・メンバーの追加・外し）の応答時間は、95 パーセンタイルで 1 秒以内。k6 の `checks` の率は 1（状態コードの誤りを混ぜた p95 で合格にしない）。目標は緩めない | k6 に、**操作ごとに1つの場面**を足す。どの場面も1回の繰り返しに要求1つで、`op` のタグには頼らない（承認の場の直し R-01）。<br>・`groupListFirst`・`groupListLast`: 一覧の1ページ目・最後のページ（10 VU・3 分の constant-vus）。<br>・`groupDetail`: メンバー 1,000 人のグループの詳細（10 VU・3 分）。<br>・`groupRename`: VU ごとに自分のグループの名前を、繰り返しごとに2つの名前で交互に変える（`GROUP_NO_CHANGE` にならない。10 VU・3 分）。<br>・`groupCreate`: VU と繰り返しの番号で一意の名前を作って作成する（10 VU × 100 回の per-vu-iterations、maxDuration 3 分）。<br>・`groupDelete`: `setup()` で VU ごとに 100 個の空のグループを作り、繰り返しごとに1つ消す（10 VU × 100 回、maxDuration 3 分）。<br>・`groupMemberAdd`: `setup()` で VU ごとに自分のグループと 100 人の利用者を用意し、繰り返しごとに別の利用者を足す（10 VU × 100 回、maxDuration 3 分）。<br>・`groupMemberRemove`: `setup()` で VU ごとに 100 人のメンバーを持つグループを用意し、繰り返しごとに1人外す（10 VU × 100 回、maxDuration 3 分）。<br>**判定**: 場面ごとに `iteration_duration{scenario:…}` の p95 < 1000 ms と `checks{scenario:…}` の率 = 1（期待の状態コードだけを合格にする）。`http_req_duration{name:…}` は並べて記録するだけで判定に使わない（`project.md` の学び。colima の VM の時計のずれで `http_req_duration` が崩れ、単調な時計の `iteration_duration` は1回の繰り返しが要求1つのときに要求の時間と一致する）。<br>**トークン**: 操作する管理者のアクセストークンは `setup()` でまとめて取り、場面の長さはトークンの有効期限 5 分より短い 3 分までにして、途中でログインし直さない（ログインの要求を繰り返しに混ぜない）。<br>データは試験の悪い側（グループ 1,000・利用者 1,000 名・メンバー 1,000 人のグループ1つ、Q2: A）に、上の場面の用意の分を足す。使い捨ての環境で流す（`perf/README.md`、`project.md` の学び）。操作する管理者は試験用の利用者で、初期管理者は使わない。測れない段では `Unverified` とし、持ち主の段を明記して引き継ぐ | 台本とデータの用意は Code Generation（B3）、流すのは Performance Validation |
| NFR2.6 | 一覧と詳細の問い合わせの数は、ページの件数・メンバーの数に比例して増えない（N+1 にしない）。一覧は件数・ページ分のグループ・メンバーの数・問う口の `assignedRoleCounts` の1回、詳細はグループ・メンバーの行・`user.service` のまとめて読む口の1回 | 結合テスト `GroupAdminListQueryCountIT`（グループ 1 件と 20 件、メンバー 1 人と 50 人で、問い合わせの数が同じ） | Code Generation（B3） |

## 補足

- 試験は caffeinate -i で台本全体を包み、PC のスリープで結果が崩れないようにする。判定は NFR2.5 のとおり `iteration_duration` で行い、`http_req_duration` は並べて記録する。
- 台本を書くときに `k6 inspect --include-system-env-vars` で場面の名前と閾値の式を確かめる（`project.md` の学び）。
- 書き込みの操作は監査の記録のために確定の後に2本目の接続を使う。接続プールの見積もりと上限に届かせる場面は `scalability-requirements.md` の NFR2.7。

## 承認の場の決定と直し

依頼者は承認の場で Request Changes を選び、直す範囲を「Major 11 件だけを直す」と決めた。この成果物で直したのは次の1件（レビューの記録は `aidlc/spaces/default/intents/261004-role-menu/.aidlc-reviews/nfr-requirements/units/group/4e9510ac79c4db11/` の 1 回目）。

- **R-01（Major）NFR2.5 の判定の指標**: 1回の繰り返しに5つの要求を入れた `groupAdminOps` をやめ、操作ごとに1回の繰り返しに要求1つの場面（`groupListFirst`・`groupListLast`・`groupDetail`・`groupRename`・`groupCreate`・`groupDelete`・`groupMemberAdd`・`groupMemberRemove`）に分けた。`op` のタグには頼らない。判定は場面ごとの `iteration_duration` の p95 < 1000 ms と checks の率 1 とし、`http_req_duration` は並べて記録するだけにした。トークンは `setup()` で取り、場面の長さを 3 分までにして途中でログインし直さない。U4 role の NFR 要件で先に採った形にそろえた。
