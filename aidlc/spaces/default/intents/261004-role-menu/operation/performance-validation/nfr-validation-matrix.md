# NFR の確かめの表（nfr-validation-matrix）

Build and Test から引き継いだ `Unverified` 20 件（`construction/build-and-test/build-and-test-summary.md` の Target Verification Matrix）と、Observability Setup から移された警報・BUSY の確かめを、目標を緩めずに判定した表です。実測の詳細は `test-results.md` にあります。Source は `aidlc/spaces/default/intents/261004-role-menu/` を省いた相対パスで書きます。

## 1. 引き継いだ 20 件

| ID | Source | 目標 | 実測 | 判定 |
|---|---|---|---|---|
| U2-NFR2.5 | construction/dsl-v2/nfr-requirements/tech-stack-decisions.md | 生成 30 s・投入・戻し・プレビューの表示 10 s・ダウンロード 2 s・画面の表示 3 s（照合を含む 11 s）・違いの表の行 0.5 s・10 MiB を選んでから送るまで 2 s | 生成 2.69 s、投入 1.16 s、戻し 0.92 s、プレビュー 0.21 s、ダウンロード 0.046 s、画面 110 ms・380 ms、行 35 ms、送るまで 649 ms。OOMKilled なし（ヒープは見積もり超え、3節） | Met |
| U2-NFR2.6 | 同上 | 今の状態・履歴・破棄・適用の p95 1 s、10 MiB の投入とプレビューにログインを重ねても失敗しない | `dslLight` 3.5・3.4 ms、`dslCycle` 適用 8.6・破棄 5.9・履歴 3.7 ms、`checks` すべて合格。`dslMixed` の `checks` 1,491/1,491、ログインの 500 は 0、OOMKilled なし（`memory.peak` 1,869 MB）。`--busy` は 201 と 503 `DSL_BUSY` | Met |
| U2-NFR2.7 | 同上 | 既定の DSL の生成の出力が 10 MiB の内 | 7,261,390 バイト（試算の 約 6.3 MB より大きい） | Met |
| U2-NFR2.8 | 同上 | 英語の文言が届く（版の誤り・深さの誤り・`SCHEMA_MISMATCH`） | `ui-lang.json` の `pass: true`（`<html lang>` が `en`、5 段とも日本語の文字・全角の記号なし） | Met |
| U3-NFR2.5 | construction/group/nfr-requirements/performance-requirements.md | グループの7つの口の p95 1 s | 8 場面の p95 は 3.5〜62.5 ms、`checks` すべて合格 | Met |
| U3-NFR2.1 | construction/group/nfr-requirements/scalability-requirements.md | 悪い側（グループ 1,000・利用者 1,000・メンバー 1,000 人のグループ）で上の目標 | グループ 1,000 件以上・候補 1,000 人・メンバー 1,000 人の詳細で満たす | Met |
| U3-NFR2.7b | 同上 | 書き込みの2本使い（負荷の部分） | 合否の回（上限 11・VUS 5）: 時間切れ 0・500 0・待ちの最大 4.24 ms。記録の回（上限 10・VUS 20）は上限に届いた（待ちの最大 5,022 ms、時間切れ 388・500 189・409 163） | Met |
| U3-NFR2.8b | 同上 | 数の上限を置かず、詳細はメンバーを全件返す | メンバー 1,000 人の詳細が全件で 200（p95 62.5 ms） | Met |
| U4-NFR2.2 | construction/role/nfr-requirements/performance-requirements.md | 木の1階層・保存・作業ロールの切り替えの p95 1 s | 木 97.0・108.9・13.3 ms、保存 24.9 ms、切り替え 4.7 ms | Met |
| U4-NFR2.3b | 同上 | 要求ごとの権限の解決（時間の部分） | 解決を通る場面（`navMenu` 181.1、`tableAccess*` 35.1・31.1、`myPermissionsTree` 177.3〜185.2、作業ロールの場面 3.9〜4.7 ms）の p95 がすべて 1 s 未満 | Met |
| U4-NFR2.4 | 同上 | 上限ちょうどの YAML で確かめ 15 s・適用 30 s、OOM なし、ヒープの最大を記録 | 確かめ 4,070 ms、適用 8,744 ms、ヒープの最大（読めた点）674 MB、`memory.peak` 1,302 MB、OOMKilled なし | Met |
| U4-NFR2.5 | 同上 | role のほかの API の p95 1 s・`checks` の率 1 | `roleAdminRead`・`roleAdminOps`・`roleAssignOps` 3.2〜4.3 ms、`roleExport` 468.1 ms、`workRoleRead` 3.9 ms・`roleAssignmentsRead` 4.7 ms（流し直し、`test-results.md` 4節） | Met |
| U4-NFR2.10 | 同上 | 1回の繰り返しに要求1つ、状態を戻す操作は交互 | どの場面も op ごとの `iteration_duration` で判定でき、作成と削除・割り当てと外しの交互で状態が戻った（監査の件数が回数と一致） | Met |
| U4-NFR2.1 | construction/role/nfr-requirements/scalability-requirements.md | 目安（ロール 100・グループ 100・利用者 50、100 × 100、1ロールに最大 1 万カラム）の悪い側で上の目標 | ロール 1,222（準備 1,000）、悪い側のロールに 1万カラム、グループ 1,000 件以上・利用者 1,013 で満たす | Met |
| U4-NFR2.7b | 同上 | 書き込みの2本使い（負荷の部分。合否は時間切れ 0・500 0・待ちの最大 20 ms 未満） | 合否の回の1回目は待ちの最大 **23.55 ms**（時間切れ 0・500 0）。流し直しは 8.55 ms で再現しなかった。記録の回は上限に届いた（5,087 ms、時間切れ 371・500 182・409 51） | **Not Met**（1回目。流し直しで再現せず。依頼者の決定で持ち越す） |
| U4-NFR2.8 | 同上 | 数の上限を置かず、割り当ての一覧・利用者のロール・作業ロールの選択肢を全件返す | ロール 1,222・割り当て多数の状態で、`roleAssignmentsRead`・`workRoleRead`・`roleAdminRead` が 200 で p95 4.7 ms 以下 | Met |
| U5-NFR2.2 | construction/navigation/nfr-requirements/performance-requirements.md | メニューと置き場の p95 1 s・`checks` の率 1 | `navMenu` 181.1、`tableAccessVisible` 35.1、`tableAccessDenied` 31.1、`navMenuBaseline` 229.2 ms | Met |
| U5-NFR2.3b | 同上 | 読み出しの数がメニューの大きさによらず一定（負荷の部分） | 1,000 項目（181.1 ms）と 100 項目（229.2 ms）で p95 が大きさに比例しない。数そのものは結合テスト `NavigationConnectionUsageIT` で確かめ済み | Met |
| U5-NFR2.5 | 同上 | 1回の繰り返しに要求1つ | 4 場面とも要求1つで `iteration_duration` で判定 | Met |
| U5-NFR2.1 | construction/navigation/nfr-requirements/scalability-requirements.md | 目安（menus 100・深さ 1、100 × 100、利用者 50）と悪い側（menus 1,000・深さ 5） | 悪い側 1,000 項目・深さ 5 と目安 100 項目・深さ 1 の両方で満たす | Met |

## 2. Observability Setup から移された項目

| 項目 | Source | 実測 | 判定 |
|---|---|---|---|
| `ms-audit-fail` が鳴る | operation/observability-setup/alarms.md 2節 | 23:39:38 に `firing`（始めてから約 33 秒） | Met |
| `ms-error-logs` が鳴る | 同上 | 23:40:44 に `firing` | Met |
| `ms-pool-pending` が鳴る | 同上 | 23:40:44 に `pending`、23:41:49 に `firing`、終わった後の 23:45:39 に `inactive` | Met |
| BUSY（`ROLE_BUSY`・`GROUP_BUSY`）の L3 と L4 の結び付き | operation/observability-setup/log-queries.md、operation/incident-response/runbooks.md の RB-28 | どの回でも BUSY は 0 件 | Unverified（feedback-optimization へ） |

## 3. この段で扱わなかったもの・申し送り

| 項目 | 扱い |
|---|---|
| 任意の重なり（DSL の投入と権限の YAML の確かめ、dsl-v2 の NFR 設計 R-07） | Q2: A で流さない。`Unverified`（feedback-optimization へ） |
| 10 MiB の DSL の投入のヒープ（dsl-v2 の読み直し R-03） | GC の後 573〜746 MB で見積もり（256〜384 MB）を超えた（OOMKilled なし）。見積もりの直しを feedback-optimization へ |
| `dslMixed` の `memory.peak` 1,869 MB（上限 2g の 87%） | 記録。余裕は小さい |
| 置き場の p95 が目標の 約 30 分の 1 | 目標を見直すかは後の Intent（`performance-design.md` 3節） |
| ロールの場面の setup が前の場面の割り当てに頼る | 後の Intent で台本を直す候補（`test-results.md` 4節） |

## 4. 集計

| 判定 | 数 |
|---|---|
| Met | 19（引き継いだ 20 件のうち）＋ 警報 3 |
| Not Met | 1（U4-NFR2.7b の合否の回の1回目。流し直しで再現せず） |
| Unverified | BUSY の結び付き・任意の重なり（どちらも持ち主 feedback-optimization） |

## Sources

- `aidlc/spaces/default/intents/261004-role-menu/operation/performance-validation/test-results.md`・`load-test-plan.md`
- `aidlc/spaces/default/intents/261004-role-menu/construction/build-and-test/build-and-test-summary.md`（Target Verification Matrix）
- 各単位の `construction/*/nfr-requirements/`（`dsl-v2/tech-stack-decisions.md`、`group`・`role`・`navigation` の `performance-requirements.md`・`scalability-requirements.md`）
- `aidlc/spaces/default/intents/261004-role-menu/operation/observability-setup/alarms.md`・`log-queries.md`、`operation/incident-response/runbooks.md`
- `build/perf-results/pv/`（git の対象外）

## Assumptions & Open Questions

- U4-NFR2.7b は、依頼者の決定（結果の確認の場で A）で、1回目の `Not Met` のまま持ち越す。原因の調べ（流し直しを重ねて出る割合を見るなど）は Feedback & Optimization か次の Intent に回す。目標（20 ms 未満）は緩めていない。team.md の「不安定なテストは原因を直すまで次へ進まない」とは、負荷の試験の合否の回であり統合の関門ではない点で別だが、原因を確かめずに進む差として記録する。
- `workRoleRead`・`myPermissionsTree`・`roleAssignmentsRead` は、割り当てを外した後に流し直した値で判定した（`test-results.md` 4節）。
