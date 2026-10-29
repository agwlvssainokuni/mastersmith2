# 順の理由と危険 — reference-lookup-cleanup

Bolt は、1 つ以上の作る単位（Unit of Work）を、設計から統合まで通して動くものにする 1 回の作り込みのこと。この文書は、Bolt を B1〜B6 の順に作る理由と、各 Bolt で早めに確かめる危険を書く。

出典: 単位と依存 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/units-generation/unit-of-work.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/units-generation/unit-of-work-dependency.md`、部品と決定 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/domain-design/components.md`・`decisions.md`（ADR-006 の実現の判断）、契約 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-summary.md`、要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`、チームの進め方 `aidlc/spaces/default/memory/team.md`、この段の答え `delivery-planning-questions.md`。

## 順の決め方

- 危ないものから先に作る考え方（risk-first。Cohn の「危険の大きいものを早く」）で順を決めた（DQ1: A）。点数を付けて順位を決める型（WSJF: 価値と急ぎと危険の減り方を、大きさで割った点数。Reinertsen・SAFe）は使わない。依存がほぼ一本道で、順の選択肢が U1・U4 の前後くらいしか無いため（DQ2: A、前の Intent と同じ）。
- 順は B1（U1 前半）→ B2（U1 後半）→ B3（U2）→ B4（U3）→ B5（U4）→ B6（U5）。単位の依存（U2 → U1、U3 → U1・U2、U5 → U4）をすべて守っており、依存の形の順からの外れは無い。
- U1 と U4 は依存の上では並べて作れるが、依頼者 1 名で確かめと承認を Bolt ごとに行うため、1 つずつ順に作る（DQ4: A）。

## なぜこの順か

| 位置 | Bolt | 理由 |
|---|---|---|
| 1 | B1 U1 前半 | ADR-006 の実現の論点（既存の `search`・`detail` への口の足し方、200 件の上限、接続 1 本）がここに集まる。だめなときの切り替え先（専用のメソッド・件数の別問い合わせ）は計画の承認や依頼者の決定が要るため、最初に確かめて後の Bolt への影響を早く知る |
| 2 | B2 U1 後半 | 同じ応答（403）・API の分類・漏えいは、U3 の画面が頼る契約の形を固める。U1 を 2 つに分け（DQ3: A）、1 回の統合を小さくした |
| 3 | B3 U2 | 保存前の確かめは U1 の部品（`ReferenceLookup`）を使う。既存の書き込みの流れ（`write()`）に手を入れるため、画面より前にサーバー側で固める |
| 4 | B4 U3 | 画面は U1・U2 の本物の API で画面のテストと E2E を通す（Units Generation の Q5: A） |
| 5 | B5 U4 | 持ち越しの片付け。参照の機能と独立で、`perf/` の記録を作るのは Build and Test 以降のため急がない |
| 6 | B6 U5 | `perf/README.md` を B5 の後に整理する（文書の衝突を避ける順の依存）。`thresholdsFor` への集約を、Build and Test で k6 の新しい場面を足す前に終える |

Construction の前に、Dependabot の #81・#82 を取り込む（DQ7: A）。小さい更新で、B4 の画面を新しい版の上で作れる。

## 早めに確かめる危険（DQ6: B・C・D）

| 危険 | 確かめる Bolt | 確かめ方 | だめなとき |
|---|---|---|---|
| 200 件の上限と 201 件目の判定、REFERENCE が複数でも読み取りの接続を 1 本だけ使うことが、3 種類の DB で成り立たない（DQ6 の B、NFR3） | B1（保存前の確かめの分は B3） | 3 種類の DB の結合テストと、`*ConnectionUsageIT` と同じ形の接続の数の結合テスト | 件数の問い合わせ（`COUNT`）を別に行う。定義の取得を参照の項目ごとの別の要求に分ける（ADR-006。画面の変更が要る） |
| 「同じ応答」（403）が、場合によって状態コード・code・構造・メッセージのどこかで食い違う（DQ6 の C、契約 C2） | B2 | 権限の欠け方 4 種類・DSL に無い名前・参照でないカラム・行が無い、の応答を並べて同一を確かめるパラメーターのテスト | 応答の組み立てを 1 か所（同じ例外）に寄せる |
| `frontend/` の Vitest から `perf/` の伏せ字の道具を読めない、または ESLint・ライセンスヘッダーの検査に合わない（DQ6 の D、ADR-006） | B5 | `./gradlew verify` の通過 | 道具を `frontend/` の下に置く、または `perf/` に専用の検査の入口を置く（ADR-006） |

DQ6 の A（既存の `search`・`detail` に口を足しても、今の一覧・詳細の結果が変わらないか）は、依頼者は早めの確かめに選ばなかった。この点は B1 の終わりの条件（既存の一覧・詳細・作成・更新・削除のテストが変えずに通る）として、既存のテストで確かめる。

## そのほかの危険

- **E2E の時間**: B1〜B4 は統合の前に E2E を流す。E2E は長いため、`caffeinate -dims` で包んで流し、流した後に `pmset -g log` で眠りが無かったかを確かめる（`project.md` の学び）。
- **不安定なテスト**: 統合の後の CI が失敗したら、`team.md` の「不安定なテストと CI の失敗」の決まりで扱う。
- **U5 のコンテナの入れ替え**: Mailpit のメールが消える。B6 の最初の手順で依頼者の了承を得る。止まっているコンテナは作り直されないことがあるため、`docker inspect` で版とダイジェストを確かめる（`team.md` の Deployment）。

## Sources

- この段の答え: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/delivery-planning/delivery-planning-questions.md`（DQ1〜DQ7）
- 実現の判断: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/domain-design/decisions.md`（ADR-006）
- 単位と依存: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/units-generation/unit-of-work-dependency.md`

## Assumptions & Open Questions

None.
