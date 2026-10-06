# 性能の設計 — U5 navigation

## 出典

- この単位の承認済みの NFR 要件 `construction/navigation/nfr-requirements/performance-requirements.md`（NFR2.2・NFR2.3・NFR2.5）と、その読み直しの記録の R-02〜R-05
- この単位の承認済みの機能設計 `construction/navigation/functional-design/functional-spec.md`（2.1〜2.3）・`rules.md`（BR1.4・BR5.2）
- この段の答え `nfr-design-questions.md`（まとめの確認）
- 統合の点: role の NFR 設計 `construction/role/nfr-design/performance-design.md`・`reliability-design.md`（捨ての試し U5）
- 上流との差: 置き場で `resolve` を使うことは、機能設計 BR5.2 との差（`construction/navigation/nfr-requirements/security-requirements.md` の「上流との差」、この段の `logical-components.md` の 4節）。読み直しの R-05 の手当て。

## 1. 読み出しの数（NFR2.3）

| API | navigation が呼ぶもの | 回数 |
|---|---|---|
| メニュー | `ActiveDslModelProvider.current()` | 1 |
| メニュー | `EffectivePermissionResolver.snapshotFor(userId)` | 1（DSL が無い・menus が空なら 0） |
| 置き場 | `resolve(userId, {schema, table, null})` | 1 |
| 置き場 | `current()`（表示名のため） | READ・FULL のときだけ 1 |

- 数えるのは navigation が呼ぶ回数だけ。口の内側の `current()` と問い合わせの数は role の `EffectivePermissionQueryCountIT` が持つ（機能設計の再レビューの R-04）。
- `NavigationCallCountTest`（単体、Spring を起動しない）: 2つの口を、呼ばれた回数を数える替え物にし、menus 1 項目と 1,000 項目、置き場の 200 と 403（DSL 無し・組が無い・NONE）で、上の表の回数であること。

## 2. 時間の予算

- role の捨ての試し U5 で、`snapshotFor`（1万行）は p95 3 ms・最大 42 ms、`resolve` は p95 1 ms・最大 8 ms だった。role の予算 300 ms に大きく余裕がある。
- 絞る関数は 1,000 項目・深さ 5 段の木を1回たどるだけで、項目ごとに写しの表を1回引く（写しはメモリの中の表）。別の予算は置かない（NFR 要件の Q2 A）。
- キャッシュは置かない（要求をまたいで写しを持たない、BR1.4）。

## 3. k6 の場面（NFR2.2・NFR2.5）

- 台本は今の `perf/k6/scenarios.js` に場面を足す。どの場面も1回の繰り返しに要求1つで、判定は `iteration_duration{scenario:<名前>}` の p95 < 1000 ms と `checks{scenario:<名前>}` の rate == 1。`http_req_duration{name:…}` は並べて記録する。

| 場面 | DSL と利用者 | 要求 | 期待の状態コード |
|---|---|---|---|
| `navMenu` | 悪い側の DSL（menus 1,000 項目・深さ 5）。利用者は VU の番号で決まる試験用の利用者 `nav-vu-01`〜`nav-vu-10`（どれも 100 のグループに属し、作業ロールは1万カラム明示のロール） | `GET /api/me/navigation` | 200 |
| `tableAccessVisible` | 同じ | 読める組の置き場の問い合わせ（VU ごとに READ のテーブルを順に回す） | 200 |
| `tableAccessDenied` | 同じ | NONE の組の置き場の問い合わせ | 403 |
| `navMenuBaseline` | 目安の DSL（menus 100 項目・深さ 1）を適用し直した後。利用者は同じ | `GET /api/me/navigation` | 200 |

- 実行の分け方（読み直しの R-02）: 場面ごとに別の実行（`k6 run` を4回）にする。`navMenuBaseline` の前に目安の DSL を DSL の管理の API で適用し直す。VU と利用者の対応は VU の番号で固定し、トークンは `setup()` で10人分取る。
- 期待の状態コードの宣言（読み直しの R-03）: `tableAccessDenied` は `http.setResponseCallback(http.expectedStatuses(403))` を場面の始めに置き、`checks` も 403 を合格にする。
- 基準の値の記録（読み直しの R-04）: Performance Validation で、置き場の p95 の実測値を基準の値として記録する。目標（1 秒）より桁違いに小さければ、後の Intent で目標を下げるかを検討する、と結果に書く。
- 台本全体を `caffeinate -i` で包み、使い捨ての環境で流す。台本を書いたら `k6 inspect --include-system-env-vars` で場面と閾値の名前を確かめ、短い試し走り（各場面 1 VU・10 秒）で閾値の指標が出ることを確かめてから本番の長さ（10 VU・3 分）で流す。
