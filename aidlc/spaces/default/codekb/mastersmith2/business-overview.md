# 業務の概要（mastersmith2）

> 2026-10-29、Intent `261029-reference-lookup-cleanup`（Standard・brownfield）の Reverse Engineering（Focused scan、記録のコミット `15a25aa`）で更新した。既存の散文と前回の所見の一覧は残し、今回の範囲（業務データの選択肢 REFERENCE・LOOKUP、`dsl` の選択肢の型、`targetdb`・`role` の口、`frontend/src/features/tables`、perf の JWT、コンテナの版、i18next の2件）の所見 K-74〜K-80 を足した。深く読んだのは今回の範囲だけで、それ以外は前回（`261027-perf-targets-review`）以前の記録による（`reverse-engineering-timestamp.md` の Scope of Analysis）。

## 何をするアプリか

mastersmith2 は、別に動く業務の DB（対象DB: MySQL・MariaDB・PostgreSQL）のテーブルを、DSL（YAML）で定義した見せ方に従って画面から扱う管理のアプリである。今のコードがすること:

- 利用者の認証（ログイン・アカウントロック・トークン・ログアウト）と、招待から登録の完了までの流れ。
- 利用者・招待・グループ・ロールの管理、ロールの権限（スキーマ・テーブル・カラムへの主権限と、テーブルの補助権限 `CREATE`・`DELETE`）、作業ロールの切り替え。
- 対象DB のスキーマの読み取りと、既定の DSL の生成・投入・プレビュー・適用・履歴。
- 適用中の DSL のメニューを、作業ロールの権限で枝を落としてサイドバーに出す。
- 業務データ（対象DB の行）の定義・一覧（検索・並べ替え・ページ送り）・詳細・作成・更新・削除（`businessdata`。前の Intent `261016-business-data-views` で入った）。
- 管理の操作・認証・DSL・ロール・業務データの書き込みなどの出来事を、内部DB（組み込みの H2）の監査の表に追記する。

## 利用者と役割

| 人 | すること |
|---|---|
| 管理者（`admin_flag` が真で停止していない利用者） | 利用者・招待・DSL・ロール・グループの管理（`/api/admin/**`） |
| ログインした利用者 | 自分の設定、作業ロールの切り替え、メニューをたどる、権限に応じて業務データを一覧・詳細・作成・更新・削除する（`/api/me/**`・`/api/tables/**`） |
| 招待を受けた人（ログインなし） | 招待のリンクの確かめと登録の完了（`/api/registration/**`） |

## 所見の一覧（K-62〜K-80）

番号は K-61 から続く（K-62〜K-73 は前回まで、K-74〜K-80 が今回）。本文は持ち主の文書に1回だけ書き、ここには一覧だけを置く。前回までの K-1〜K-61 の本文はこの知識ベースに無い（前の Intent の記録と git の履歴に残る）。

### 今回（Intent 261029-reference-lookup-cleanup）の主題

業務データの選択肢の他テーブル参照（REFERENCE）と参照ピッカー（LOOKUP）の本実装（前の Intent `261016-business-data-views` で意図的に先送りした部分）と、持ち越しの片付け（perf の JWT の自動の伏せ字、コンテナの版、Dependabot の i18next 系2件）。今回の範囲の事実はここに一覧し、本文は持ち主の文書に1回だけ書く。「未検証」と書いた項目は、要件定義・設計の段で確かめる仮説である。

| 番号 | 要点 | 主な部品 | 本文の場所 |
|---|---|---|---|
| K-74 | REFERENCE・LOOKUP は DSL（JSON Schema・モデル・意味の検証・既定の DSL の生成）までは完成しているが、業務データでは `ColumnWriteRulesFactory` が `lookup` を `text` に置き換え、選択肢は FIXED だけを返す（前の Intent の意図的な暫定 BR3.7）。定義の応答にも参照先の情報を載せる欄が無い | `businessdata`・`dsl` | `architecture.md`（Interaction Diagrams 4）・`api-documentation.md` |
| K-75 | 参照先の行を読む専用の API は無い。権限の引き口 `PermissionSnapshot.main(schema, table, column)`・`TableAccessResolver`・`RowSqlExecutor.search/detail` が流用の候補（未検証の仮説）。読み取りの目的 `ReadPurpose` の新設要否・参照先の権限が NONE のときの扱い・件数の上限は設計の判断 | `businessdata`・`role`・`targetdb` | `architecture.md`（Interaction Diagrams 4） |
| K-76 | 画面の流用点: `SearchPanel`・`BusinessDataTable`・`BusinessDataFormPage` の部品の選び方（formPart の分岐）・`shared/modal`。参照ピッカーに近い形だが未検証。make-you-chic-ui に無い部品は先に上流へ依頼する（`team.md`） | `frontend-feature-tables` | `component-inventory.md` |
| K-77 | perf の k6 の summary json に使い捨ての環境の JWT が `setup_data` として出る。自動の伏せ字の仕組みは `perf/` にも README にも無く、決まりは `project.md` の記述だけ（手で置換） | `perf-and-monitoring` | `code-quality-assessment.md` |
| K-78 | コンテナの版の固定先が分散している。`compose.yaml` の lgtm 0.38.0・Mailpit v1.32.0 のタグは動いている（または止まっている）コンテナと一致して見えるが、ダイジェストの一致は未検証 | `container-runtime`・`perf-and-monitoring` | `dependencies.md`・`technology-stack.md` |
| K-79 | Dependabot の i18next 系2件（#81、#82）は patch。画面での使い方は狭く影響は小さい見込み（未検証）。`dependabot.yml` の npm の `ignore` はこの2件を妨げない | `build-and-verify`・`frontend-app-layout-i18n` | `dependencies.md` |
| K-80 | 境界テスト・関門の確かめ: 参照先の読み取りを `businessdata` の内側に置く限り `BusinessDataBoundaryArchitectureTest` の許可は緩めずに済む見込み。`SQL_` の SpotBugs 除外は `RowSqlExecutor` のメソッド単位のため、新しい SQL のメソッドは除外の追加（計画に明記して承認）か既存の `search`／`detail` の再利用が要る | `businessdata`・`build-and-verify` | `code-quality-assessment.md` |

### 前の Intent（261027-perf-targets-review）の所見（今回は再確認していない範囲を含む）

性能・資源の目標（k6 の応答時間の線、`memory.peak`・`anon` の物差し）の見直しと、Dependabot の知らせの洗い出し。bugfix のため `backend/src/main` を変える見込みは低い。物差しを変えるのは、`team.md` により要件定義で依頼者が明示に決めたときだけで、変えた後も1回は前の物差しの値を並べて記録する（緩和の決定は 2026-10-28 に出ている。値と対象は要件定義で記録する）。

| 番号 | 要点 | 主な部品 | 本文の場所 |
|---|---|---|---|
| K-69 | k6 の合否の線の定義場所は `perf/k6/scenarios.js` の `thresholdsFor`。biz の読み取りは `iteration_duration` の `p(95)<1000` と `checks` の率 1、`http_req_duration` は記録だけ。DB の種類別の線は無い | `perf-and-monitoring` | `code-quality-assessment.md` |
| K-70 | `memory.peak`・`anon` の物差しの記述箇所（`perf/README.md` と `perf/dsl-mixed-memory.sh`）と現行の判定（peak/max < 0.9、anon に替えない） | `perf-and-monitoring` | `code-quality-assessment.md` |
| K-71 | `docker/perf/compose.yaml` の資源の上限の既定と、`perf/README.md` の運用値（MySQL 2304m・2688m など）が食い違う | `perf-and-monitoring` | `code-quality-assessment.md` |
| K-72 | Dependabot の現状: ignore は gradle 2件・npm 1件・docker 1件、groups 未使用。Tomcat・Jackson の上書きは外れて Spring Boot 4.1.2 の管理の版になっている。開いている PR は未確認 | `build-and-verify` | `dependencies.md`（一覧）・`technology-stack.md`（版） |
| K-73 | `perf/README.md`（1,359 行。2026-10-29 に再計測）・`perf/k6/scenarios.js`（2595 行）が大きく、同じ指標に前の Intent の結果と現行の判定が混在して、どの値が基準か読み取りにくい | `perf-and-monitoring` | `code-quality-assessment.md` |

### 前の Intent（261025-deps-perf-carryover）から続く所見（今回は再確認した範囲のみ更新）

| 番号 | 要点 | 主な部品 | 本文の場所 |
|---|---|---|---|
| K-62 | 一覧は同じ接続で `COUNT(*)`（上限なし）とページの文の2回を実行する（今回も `RowSqlBuilder.count`・`RowSqlExecutor.search` は変わっていないことを確かめた） | `businessdata` | `architecture.md` の Interaction Diagrams 1 |
| K-63 | 読み取りのプールの既定は上限 10・借りる待ち 3 秒（前回 5 から引き上げ済み）で、1要求が1接続を使い続ける | `targetdb` | `architecture.md` の Interaction Diagrams 1 |
| K-64 | 件数 `total` は確定した `long` だけで、「多数」を持てる形が API・画面・テストのどこにも無い | `businessdata`・`frontend-feature-tables` | `api-documentation.md` |
| K-65 | MySQL と MariaDB は同じ方言クラスで、別の最適化に使う口が無い | `businessdata`・`dsl` | `component-inventory.md` の `businessdata` |
| K-66 | 固定している部品の見直し: Spring Boot 4.1.2 に上がり、Tomcat・Jackson の上書きは外れた。appender と networknt の固定は残る | `build-and-verify` | `technology-stack.md`（一覧）・`dependencies.md` |
| K-67 | perf の biz の場面・種・資源の上限と、読み取りのプールを変える口 | `perf-and-monitoring` | `code-quality-assessment.md` |
| K-68 | `perf/README.md` の依頼の「(c) の grep の形」は、(c) の節 1003 行の 1021 行（今回は読み直していない） | `perf-and-monitoring` | `code-quality-assessment.md` |

前回の所見のうち今回も効く未確認の持ち越し（K-53 管理の可否が `users.admin_flag` だけで決まる件、K-59 `memory.peak` の測り方、K-60 make-you-chic-ui と axe-core）は、K-59 を除き今回も深く読んでいない（K-59 の測り方は K-70 で読み直した）。前の記録と `aidlc/spaces/default/memory/project.md` の記録を参照すること。

## Sources

- 開発担当の調査: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/reverse-engineering/developer-scan.md`（今回）、`aidlc/spaces/default/intents/261027-perf-targets-review/inception/reverse-engineering/developer-scan.md`（今回）、`aidlc/spaces/default/intents/261025-deps-perf-carryover/inception/reverse-engineering/developer-scan.md`（前回）
- アーキテクトが読み取りで確かめ直したもの: `reverse-engineering-timestamp.md` の「確かめ方」
