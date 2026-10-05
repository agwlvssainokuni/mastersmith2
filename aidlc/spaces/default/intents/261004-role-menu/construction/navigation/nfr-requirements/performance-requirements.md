# 性能の要件 — U5 navigation

## 出典

- `functional-spec.md`・`rules.md`（この単位の承認済みの機能設計。2.1・2.2 の流れ、BR1.2〜BR1.4・BR5.2）
- `requirements.md`（NFR2.2・NFR2.3、AC5.1.18）
- `contract-summary.md`（C5・C9）
- `technology-stack.md`（コード知識ベース。`http.server.requests` のバケット、k6 の台本 `perf/k6/scenarios.js`）
- この段の答え: `nfr-requirements-questions.md` の Q1: A（置き場は `resolve` を1回）・Q2: A（悪い側の規模）と、まとめの確認。
- role の NFR 要件（`construction/role/nfr-requirements/performance-requirements.md` の NFR2.3、`snapshotFor` の予算 300 ミリ秒）、group・role のレビューの R-01・R-03（判定の指標、`op` のタグに頼らない）、機能設計の再レビューの R-04（数える範囲）。

## ID の対応表

全体の表は `security-requirements.md` の「ID の振り方」にある。この成果物に書くのは次のもの。

| 上流の枝番 | この単位での扱い | この単位の ID |
|---|---|---|
| NFR2.2 応答時間 | 部分（メニューを返す API と置き場の問い合わせ。木・保存・作業ロールの切り替えは U4 role） | NFR2.2 |
| NFR2.3 要求ごとの読み出しの重さ | 当たる | NFR2.3 |
| （足す） | k6 の場面の判定の決まり | NFR2.5 |

規模の前提（NFR2.1）と接続の数（NFR2.6）は `scalability-requirements.md` に書く。

## 要件

| ID | 要件 | 測り方・確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR2.2 | 業務のメニューの API と置き場の問い合わせの応答時間は、95 パーセンタイルで 1 秒以内。k6 の `checks` の率は 1。目標は緩めない（AC5.1.18）。<br>悪い側の規模（`scalability-requirements.md` の NFR2.1）で次の場面を流す。どれも 10 VU・3 分で、場面どうしは時間をずらして順に流す（同じ時間に重ねない）。<br>・`navMenu`: 10 VU それぞれが悪い側の利用者で `GET /api/me/navigation`。期待は 200 で `items` が空でない。<br>・`tableAccessVisible`: 読めるテーブルの組で置き場の問い合わせ。期待は 200。<br>・`tableAccessDenied`: NONE のテーブルの組で置き場の問い合わせ。期待は 403（`checks` は 403 を合格とする）。<br>・`navMenuBaseline`: 目安の規模（menus 100 項目、深さ 1）で `navMenu` と同じ。判定は同じ閾値で、結果を並べて記録する（Q2 A） | k6 の判定は NFR2.5 のとおり。閾値は各場面の `iteration_duration{scenario:<名前>}` の p95 < 1000 ms と `checks{scenario:<名前>}` の rate == 1。<br>測れない段では `Unverified` とし、持ち主の段を明記して引き継ぐ（`project.md` の学び） | 台本とデータの用意は Code Generation（B7）、流すのは Performance Validation |
| NFR2.3 | 1回の要求の読み出しの数を、メニューの大きさ・対象の数によらず一定にする。<br>・メニューの API: navigation が呼ぶのは `ActiveDslModelProvider.current()` 1回と `snapshotFor(userId)` 1回だけ。`resolve` を項目ごとに呼ばない（BR1.4、role の NFR2.3）。<br>・置き場の問い合わせ: navigation が呼ぶのは `resolve(userId, {schema, table, null})` 1回と、READ・FULL のときだけ表示名のための `current()` 1回（Q1 A）。`snapshotFor` は呼ばない。<br>・数えるのは navigation が呼ぶ回数だけで、解決の口の内側の問い合わせの数は role の `EffectivePermissionQueryCountIT` が持つ（機能設計の再レビューの R-04）。<br>・時間の予算: `snapshotFor` 1回の 300 ミリ秒（role の予算）を含めて、メニューの API が NFR2.2 を満たす。絞る関数だけの予算は置かない（Q2 A） | 単体テスト `NavigationCallCountTest`。解決の口と DSL の提供口を、呼ばれた回数を数える替え物にし、menus 1 項目と 1,000 項目、置き場の 200・403（DSL 無し・組が無い・NONE）で、回数が上のとおりであること | Code Generation（B7）、時間は Performance Validation（NFR2.2 の `navMenu`） |
| NFR2.5 | k6 の場面の判定の決まり（group・role のレビューの R-01・R-03 を先に避ける）。<br>・どの場面も、1回の繰り返しに要求1つにする。操作ごとの `op` のタグに頼らず、操作ごとに場面を分ける。<br>・判定は単調な時計の `iteration_duration` の p95 で行い、`http_req_duration{name:…}` は並べて記録する（`project.md` の学び。colima の VM の時計のずれで `http_req_duration` が崩れるため）。<br>・VU ごとのアクセストークンは `setup()` でまとめて取り、場面の長さはトークンの有効期限 5 分より短い 3 分にして、途中でログインし直さない。<br>・台本全体を `caffeinate -i` で包み、使い捨ての環境で流す（`perf/README.md`） | 台本を書いたとき、`k6 inspect --include-system-env-vars` で場面と閾値の名前を確かめる（`project.md` の学び）。短い試し走り（各場面 1 VU・10 秒）で、閾値の名前の指標が出ることを確かめてから本番の長さで流す | Code Generation（B7）、Performance Validation |

## 補足

- 画面は、画面の道が変わるたびに業務のメニューと作業ロールを裏で読み直す（app-frame-ui の Q2 A）。メニューの API の要求の数の見積もりは `scalability-requirements.md` の NFR2.1。
- `http.server.requests` のバケットに 1000 ms の境界があり、手元の監視で p95 を後から見られる（警報は無い。`observability-requirements.md` の受け入れた制約）。
