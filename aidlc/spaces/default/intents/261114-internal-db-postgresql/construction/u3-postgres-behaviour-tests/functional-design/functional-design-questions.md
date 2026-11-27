# 機能設計の質問: u3-postgres-behaviour-tests（Intent 261114-internal-db-postgresql）

この単位（U3: 内部DB を PostgreSQL で動かす経路の振る舞いを、実際の PostgreSQL の結合テストで確かめる）の機能設計で、依頼者に確かめる点だけを尋ねます。上流（要件定義・Domain Design・U1・U2・`team.md`・`project.md`）で決まっている点は質問にしていません。

## 決まっていること

- テストは、U1 の土台（タグ `internal-db-postgres`・`@InternalDbPostgresIT`・専用の Gradle タスク `internalDbPostgresTest`・実行ごとに一意の空のデータベース）の上に置く。普段の `verify`・`verifyQuick`・CI では流れない（FR5.2）。
- H2 の結合テストの全件とテストの部品は変えない（FR5.4）。本体のソース（`src/main`）は変えない。要る直しが見つかったときは U2 に戻す（Units Generation の境界）。
- テストの説明文は英語、テストデータのメールアドレスは予約のドメインだけ（`team.md` の Testing Posture）。
- U2 のレビューから持ち越した確かめを U3 が持つ: 作業ロールの主キー・プレビューの主キー・`preview_id` の一意の制約の 3 つの重なり（`isKeyConflict` が例外の文字列に頼るため）、`55P03`、10MB ちょうどの往復、小文字の制約名、セーブポイントの振る舞い、検索の結果の同じさ（FR3.6 を含む）。
- PostgreSQL での確かめは、H2 の既存のテストの期待（状態コード・`code`・項目）を正とし、同じ値を PostgreSQL で確かめる。

## 事実の確かめ（質問ではありません）

- 既存の結合テストのうち、内部DB の接続先を `TestDatabase.register(registry, tempDir)`（一時ディレクトリの H2 のファイル）で決めるものは 176 クラス、H2 に触れる記述（`H2`・`org.h2`・`jdbc:h2`）を持つものは 88 クラスある。H2 の構文を直接使うテストの部品（`INFORMATION_SCHEMA.SESSIONS`・`MERGE INTO`・`DATEADD`・Java のトリガー）は、`auth/testsupport/H2SessionWaits`、`role/testsupport/RoleFixtures`・`FailingSettingTrigger`、`useradmin/testsupport/UserAdminFixtures`、`businessdata/testsupport/BusinessDataWriteTables`、`invitation/testsupport` の複数など、少なくとも 18 ファイルにある。
- U1 の土台の最初のテスト（`InternalDbPostgresBaselineIT` など）は、`SpringApplicationBuilder(MastersmithApplication.class).run(database.applicationArgs())` でアプリを実際に起動し、`HttpTestClient` で API を呼ぶ形を取っている。

## Q1. [テストの作り方] 機能ごとの代表の API の流れ（FR3.1・FR5.1）を、どう PostgreSQL で流すか

要件は「どの既存の結合テストを流用するかは設計で決める」としています。流用の仕方で、作る量・H2 のテストへの手入れ・確かめの広さが変わります。

**推奨: A。** 理由: H2 の既存のテストを 1 行も変えずに済み（FR5.4）、H2 の構文に頼るテストの部品の書き換えも要りません。機能ごとの代表の流れ（12 の機能）と、PostgreSQL に固有の確かめ（待ちの上限・失敗の後のトランザクション・書き直した SQL・10MB・時刻・検索・3 つの重なり）を、PostgreSQL 用の部品だけで新しく書けば、要件の範囲（FR3.1〜FR3.9・FR5.1）を過不足なく覆えます。B は確かめが広く本数も増えますが、H2 のテストの部品の書き換えが数十ファイルに及び、FR5.4 と「H2 の結合テストとテストの部品は変えない」に反します。

A. PostgreSQL 用の新しいテストを書く（機能ごとに 1 クラス、期待は H2 の既存のテストの値を写す。H2 のテストは変えない）
B. H2 の既存の結合テストの接続先を切り替えて（`TestDatabase` に PostgreSQL への切り替えを足す）、選んだクラスを PostgreSQL で流し直す（H2 に頼る部品の書き換えが要る）
X. Other (please specify)

[Answer]: A
