# エンティティ: u3-postgres-behaviour-tests（機能設計。Intent 261114-internal-db-postgresql）

この単位は、アプリのデータ（表・エンティティ）を増やしたり変えたりしません。テストだけの単位で、`src/main` は変えません。ここに書くのは、**PostgreSQL の結合テストが確かめる対象を整理するための、テストの設計上の持ち物**だけです（テストのクラス・部品の形は `functional-spec.md` と `rules.md`、実装は Code Generation）。内部DB の既存のエンティティと表の形は U1 の移行が持ち、変えません。

次の YAML のブロックがエンティティの正（source of truth）です。

```yaml
entities:
  - id: ENT-001
    name: PostgresScenarioApp
    description: >
      PostgreSQL の結合テストが確かめる相手の、実際に起動したアプリ 1 つと、その専用の空のデータベース。U1 の土台
      （InternalDbPostgresDatabase・SpringApplicationBuilder(MastersmithApplication.class)）の上に作る。
    attributes:
      - name: lifetime
        type: enum
        required: true
        allowed_values: [per-jvm-shared, per-class]
        constraints: >
          機能ごとの代表の流れ（FR3.1）は JVM ごとに 1 つのアプリを共有してよい（流れごとに自分の利用者を作り、前のテストの状態に頼らない）。
          待ちの上限・行き詰まり・ロックの設定を変える確かめは、クラスごとに専用のアプリとデータベースを持つ（他のテストの待ちに響かせない）
      - name: database
        type: reference
        required: true
        references: InternalDbPostgresDatabase
        constraints: "実行ごとに一意の名前（msidb_<乱数>）。閉じると DROP DATABASE で消す"
      - name: port
        type: integer
        required: true
        constraints: "ランダムな番号の待ち受け（固定の番号に頼らない）"
    constraints:
      - "接続先・利用者・パスワードをログ・テストの出力・例外の文に出さない（project.md の Forbidden）"

  - id: ENT-002
    name: ParityExpectation
    description: >
      「PostgreSQL のときも H2 のときと同じ」の期待 1 件。H2 の既存の結合テストの期待（状態コード・code・応答の項目・件数・文の数）を、
      PostgreSQL のテストへ写した値。PostgreSQL のテストはこの値と照らす。
    attributes:
      - name: requirement
        type: string
        required: true
        allowed_values: [FR3.1, FR3.2, FR3.3, FR3.4, FR3.5, FR3.6, FR3.7, FR3.8]
      - name: referenceTest
        type: string
        required: true
        constraints: "期待の出どころの H2 の既存の結合テストのクラス（無いときは、Code Generation で H2 を使い捨てのコードで先に確かめた値と書く）"
      - name: status
        type: integer
        required: false
      - name: code
        type: string
        required: false
        constraints: "ProblemType の code（H2 のときと文字どおり同じ）"
      - name: fields
        type: list
        required: false
        constraints: "応答の項目の名前の集合（H2 のときと同じ。余計な項目を足さない）"
    constraints:
      - "期待は H2 の既存のテストを正とし、PostgreSQL に合わせて書き換えない。差が出たら、テストを直さず本体の差として U2 に戻す"

  - id: ENT-003
    name: PortableFixture
    description: >
      PostgreSQL のテストが前提のデータを作るための、H2 の構文に頼らない部品（利用者・ロール・グループ・招待・DSL の用意）。
      既存の H2 向けの部品（RoleFixtures・UserAdminFixtures など）には触れず、PostgreSQL 用に別に持つ。
    attributes:
      - name: location
        type: string
        required: true
        constraints: "<機能>/testsupport または internaldb/testsupport（team.md の Code Style）"
      - name: sqlStyle
        type: enum
        required: true
        allowed_values: [application-api, standard-sql]
        constraints: "アプリの API・サービス、または標準の SQL だけで作る。INFORMATION_SCHEMA.SESSIONS・MERGE ... KEY・DATEADD・Java のトリガーを使わない"
    constraints:
      - "メールアドレスは予約のドメイン（example.com など）だけ。一意の値は実行ごとの乱数でつくり、前の実行の残りに頼らない"

  - id: ENT-004
    name: ConflictProbe
    description: >
      U2 から持ち越した「鍵の重なり」の 3 つ（作業ロールの主キー・プレビューの主キー・preview_id の一意の制約）と、待ち・失敗の分類を、
      実際の PostgreSQL で起こして確かめるための、2 本の接続による待ち合わせの道具。
    attributes:
      - name: kind
        type: enum
        required: true
        allowed_values: [work-role-pk, preview-pk, preview-id-unique, row-lock-wait, write-wait, deadlock, skip-locked]
      - name: holder
        type: reference
        required: true
        references: "JDBC の接続（確定前の行を持つ側）"
      - name: waiter
        type: reference
        required: true
        references: "アプリの経路（待つ側）"
    constraints:
      - "待ち合わせは実時間の sleep ではなく、PostgreSQL の pg_locks / pg_stat_activity の観測（待っている印）で行う。上限の測りだけは実時間を測る"
```

## エンティティの要約

- **PostgresScenarioApp**: 起動したアプリと専用のデータベース。流れの確かめは共有、待ちの確かめはクラスごと。
- **ParityExpectation**: H2 の既存のテストの期待を正とする「同じ」の物差し。
- **PortableFixture**: H2 に頼らない前提データの用意。
- **ConflictProbe**: 2 本の接続で、重なり・待ち・行き詰まりを起こす道具。

アプリの既存のエンティティは変えません。上の 4 つは、テストの設計の持ち物で、`src/test/java` に置きます。
