# Domain Design の質問（Intent 261016-business-data-views）

要件・ストーリー・画面イメージ・確定したチームの進め方（`aidlc/spaces/default/memory/team.md` の Code Style）と、既存の境界テストの事実（`backend/src/test/java/cherry/mastersmith/*/…BoundaryArchitectureTest.java`）をもとに、部品の分け方で判断が分かれる点だけを尋ねる。Feasibility の段が無いため、実現できるかの判断もこの段の ADR に記録する（`project.md` の学び）。

## 確かめた事実

- `targetdb` の境界テストは、`targetdb` の中で書き込み（`executeUpdate` など）・`JdbcTemplate`・DSL への依存・web の層を止め、`targetdb.config`・`targetdb.repository` を外から使うことも止めている。`targetdb.service`・`targetdb.domain` は外から使ってよい（今はスキーマを読む口 `TargetSchemaReader` だけ）。
- `role` の境界テストは、`role` に依存してよい外の機能を `audit`（`role.domain` だけ）と `navigation`（`role.service`・`role.domain`）に限っている。実効の権限の解決の口は `role.service` にある。
- `dsl` の境界テストは、外から `dsl` に依存することを止めていない（`dsl.parse`・`dsl.validate` を外から使うことだけを止める）。適用中の DSL は `ActiveDslModelProvider` から読める。
- 監査は、各機能が出来事（Spring の出来事）を出し、`audit` の `AuditEventListener` が内部DB の確定の後（無ければ `fallbackExecution`）に書く形（K-54）。
- `team.md` の Code Style: 業務データの機能は新しい機能のパッケージに置いて自分の境界テストを足す。既存の境界テストを緩める・依存してよい相手を足すときは、コード生成の計画に明記して依頼者の承認を得る。SQL を組み立てて実行する部品は1つに閉じ、ArchUnit で `prepareStatement`・`createStatement` を呼んでよい場所をその部品だけに絞る。対象DB へ書き込むなら、対象DB 用の別のトランザクションの管理者を置く。画面は `features/tables` を置き換える1つの機能。

## DQ1. 業務データの行を読み書きする部品の置き場

A. 新しい機能 `businessdata` を作り、行の SQL を組み立てて実行する部品（`businessdata` の中の1つの部品）と、権限・DSL・監査の出来事・API を1つの機能にまとめる。対象DB の接続（読み取りと、新しく足す書き込み）の設定と DataSource は `targetdb` が持ち、`businessdata` には `targetdb.service` の口で渡す（`targetdb` の「書き込みをしない」決まりは `targetdb` の中に対しては保ち、書き込みの SQL は `businessdata` の部品で行う）（推奨）
B. `targetdb` に行の読み書きの口を足す（`targetdb` の境界テストの「書き込みをしない」を緩める）。`businessdata` は権限・DSL・監査・API だけを持つ
C. 2つの新しい機能に分ける: 行の SQL と接続を持つ `rowstore`（DSL と権限を知らない）と、権限・DSL・監査・API を持つ `businessdata`
X. Other (please specify)

[Answer]: A

## DQ2. 業務データの機能が権限の解決の口を使う方法

`role` の境界テストは、`role` に依存してよい外の機能を `audit` と `navigation` に限っている。

A. `businessdata` を、`role.service`（解決の口）と `role.domain` に依存してよい相手として `RoleBoundaryArchitectureTest` に足す。既存の決まりを「緩める」変更のため、コード生成の計画に明記して承認を得る（推奨）
B. `navigation` に業務データ向けの権限の口を足し、`businessdata` は `navigation` を通して権限を読む（`role` の境界テストは変えない）
C. 権限の解決の口の型（インターフェース）を `common` に移し、`role` がそれを実装する（`role` の境界テストは変えないが、既存の `navigation` の依存も移す）
X. Other (please specify)

[Answer]: A

## DQ3. 業務データの操作の監査の書き込み方

監査は対象DB の確定の後に書き、監査の書き込みの失敗では操作を失敗させない（要件 FR12.3）。対象DB の操作には内部DB のトランザクションが無い。

A. 今の形にそろえる: `businessdata` が対象DB の確定の後（失敗のときは巻き戻しの後）に出来事を出し、`audit` の `AuditEventListener` が `fallbackExecution` で自分の短いトランザクションで書く。監査の種類（`AuditEventType`）を足す（推奨）
B. `businessdata` が `audit` の記録の口を直接呼ぶ（出来事を使わない）
X. Other (please specify)

[Answer]: A

## DQ4. 画面の部品の置き場

`team.md` の Code Style により、画面は `features/tables` を置き換える1つの機能で、共通の部品は `src/shared/`。

A. 機能のフォルダは今の `features/tables` を引き継いで広げ（名前は変えない）、一覧・詳細・作成・更新の画面と検索の領域をそこに置く。ほかの画面でも使いうる部品（NULL の表示・横のスクロールの枠・1ページの件数の選択・離れるときの確かめ）は `src/shared/` に置く。行の「操作」のトリガーの名前の形（「操作: …」）は `src/shared/` の小さな関数にし、既存の管理の一覧もそれを使う（推奨）
B. 新しいフォルダ `features/businessdata` を作って `features/tables` を消し、メニューからの道の組み立ての関数も移す
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

- 行を読み書きする部品（DQ1: A）: 新しい機能 `businessdata` に、行の SQL を組み立てて実行する1つの部品と、権限の当て方・DSL の読み取り・監査の出来事・API をまとめる。対象DB の接続の設定と DataSource（今の読み取りと、新しく足す書き込み）は `targetdb` が持ち、`targetdb.service` の口で `businessdata` に渡す。`targetdb` の中では今の「書き込みをしない」決まりを保つ。対象DB 用の別のトランザクションの管理者は `businessdata` の側で使う（細部は機能設計・NFR 設計）。
- 権限の解決の口（DQ2: A）: `businessdata` を `role.service`・`role.domain` に依存してよい相手として `RoleBoundaryArchitectureTest` に足す。既存の決まりを緩める変更のため、コード生成の計画に明記して承認を得る。
- 監査（DQ3: A）: `businessdata` が対象DB の確定の後（失敗のときは巻き戻しの後）に出来事を出し、`audit` の `AuditEventListener` が `fallbackExecution` で自分の短いトランザクションで書く。監査の種類を足す。
- 画面（DQ4: A）: 今の `features/tables` を引き継いで広げ、一覧・詳細・作成・更新の画面と検索の領域を置く。NULL の表示・横のスクロールの枠・1ページの件数の選択・離れるときの確かめ・「操作: …」の名前の関数は `src/shared/` に置き、既存の管理の一覧も使う。
- 実現できるかの判断（Feasibility の段が無いため、この段の ADR に書く）: 3種類の DB の方言（NULL の並び・部分一致のエスケープ・一意と外部キーの違反の見分け・時差つきの型）、全体の件数の問い合わせの重さ、`pattern` の時間の上限、2行以上に当たる更新・削除の巻き戻しを、確かめる段と切り替え先とともに書く。

Does this all look correct before I generate the component catalogue?

- Looks correct
- Request changes

[Answer]: Looks correct
