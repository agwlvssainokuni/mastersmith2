# 業務の概要（mastersmith2）

> 2026-10-25、Intent `261025-deps-perf-carryover` の Reverse Engineering（Full rescan・深さ Minimal、記録のコミット `bc484c9`）で作り直した。深く読んだのは今回の Intent に関わる範囲だけで、それ以外は流し読みか前回の記録による（`reverse-engineering-timestamp.md` の Scope of Analysis）。

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

## 今回の Intent の主題と所見（K-62〜K-68）

今回の Intent は前の Intent からの持ち越し（2つの束）である。番号は前回の K-61 の続き。本文は持ち主の文書に1回だけ書き、ここには一覧だけを置く。前回までの K-1〜K-61 の本文はこの知識ベースに無い（前の Intent の記録と git の履歴に残る）。

- 第1の束: 固定している部品（Spring Boot 4.1.1 とその上書き）を 4.1.2 で見直す。
- 第2の束: MySQL・MariaDB の一覧が重い（部分一致・深い OFFSET）ことへの手当て（件数の取り方・読み取りのプール・perf の測り方）。

| 番号 | 要点 | 主な部品 | 本文の場所 |
|---|---|---|---|
| K-62 | 一覧は同じ接続で `COUNT(*)`（上限なし）とページの文の2回を実行する | `businessdata` | `architecture.md` の Interaction Diagrams 1 |
| K-63 | 読み取りのプールは既定 5・借りる待ち 3 秒で、1要求が1接続を使い続ける | `targetdb` | `architecture.md` の Interaction Diagrams 1 |
| K-64 | 件数 `total` は確定した `long` だけで、「多数」を持てる形が API・画面・テストのどこにも無い | `businessdata`・`frontend-feature-tables` | `api-documentation.md` |
| K-65 | MySQL と MariaDB は同じ方言クラスで、別の最適化に使う口が無い。索引の案内の部品も DSL の索引の情報も無い | `businessdata`・`dsl` | `component-inventory.md` の `businessdata` |
| K-66 | 固定している部品（Spring Boot 4.1.1・Tomcat・Jackson・OpenTelemetry の appender・networknt）と Dependabot の見送り3件 | `build-and-verify` | `technology-stack.md`（一覧）・`dependencies.md`（見送り） |
| K-67 | perf の biz の場面・種・資源の上限と、読み取りのプールを変える口 | `perf-and-monitoring` | `code-quality-assessment.md` |
| K-68 | `perf/README.md` の依頼の「(c) の grep の形」は該当行を特定できていない | `perf-and-monitoring` | `code-quality-assessment.md` |

前回の所見のうち今回も効く未確認の持ち越し（K-53 管理の可否が `users.admin_flag` だけで決まる件、K-59 `memory.peak` の測り方、K-60 make-you-chic-ui と axe-core）は、今回は深く読んでいない。前の記録（`261016-business-data-views` の知識ベース）と `aidlc/spaces/default/memory/project.md` の記録を参照すること。

## Sources

- 開発担当の調査: `aidlc/spaces/default/intents/261025-deps-perf-carryover/inception/reverse-engineering/developer-scan.md`
- アーキテクトが読み取りで確かめ直したもの: `reverse-engineering-timestamp.md` の「確かめ方」
