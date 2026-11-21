# エンティティ: u1-internal-db-platform（機能設計。Intent 261114-internal-db-postgresql）

この単位が新しく持つデータは、アプリの業務データではなく、**起動のときに内部DB の種類を決める判断と、その結果の持ち物**です。内部DB の14の表（`users`・`refresh_tokens` など）の形は変えません（PostgreSQL 用の移行で同じ形に作る）ので、ここには載せません。Spring Boot・Flyway・接続プールが持つ設定値や状態は、エンティティにせず `rules.md` の決まりとして書きます（`project.md` の Code Style）。

次の YAML のブロックがエンティティの正（source of truth）です。

```yaml
entities:
  - id: ENT-001
    name: InternalDbKind
    description: >
      アプリが内部DB として動かせる DB の種類（列挙）。種類ごとに、接続先の URL の先頭・移行の置き場・接続のたびに流す文を1組持つ。
      対応しない種類は列挙の値にしない（判断の結果 ENT-002 の UNSUPPORTED で表す）。
    attributes:
      - name: code
        type: enum
        required: true
        unique: true
        allowed_values: [H2, POSTGRESQL]
      - name: urlPrefix
        type: string
        required: true
        unique: true
        allowed_values: ["jdbc:h2:", "jdbc:postgresql:"]
        constraints: "code ごとに1つ。H2 は jdbc:h2:、POSTGRESQL は jdbc:postgresql:。比べ方は大文字と小文字を区別する前方一致（BR1.1）"
      - name: migrationLocation
        type: string
        required: true
        unique: true
        allowed_values: ["classpath:db/migration", "classpath:db/migration-postgresql"]
        constraints: >
          H2 は classpath:db/migration（今のまま。BR2.1）、POSTGRESQL は classpath:db/migration-postgresql（H2 の置き場の外の兄弟の置き場。
          Flyway は置き場を再帰して探すので、H2 の置き場の下には置かない。BR2.2）
      - name: connectionInitSql
        type: string
        required: false
        default: null
        allowed_values: [null, "SET lock_timeout = '2s'"]
        constraints: "H2 は無し（null。BR3.2）。POSTGRESQL は接続を作るたびに流す文（BR3.1）"
    constraints:
      - "code・urlPrefix・migrationLocation は種類ごとに1つで、重ならない"
      - "後の Intent で種類を足すとき（MySQL・MariaDB）は、この列挙に値を足す（ほかの部品を変えない）"

  - id: ENT-002
    name: InternalDbUrlResolution
    description: >
      内部DB の接続先の値（MASTERSMITH_DB_URL を Spring Boot が束ねた spring.datasource.url の値）を判断した結果。起動のたびに1回だけ作る（BR1.5）。
      結果の型は sealed な結果（Supported・Empty・Unsupported の3つ）で、画面入出力の層に当たる部品は無いため例外ではなく値で返し、
      起動の入口の部品が受けて起動を止める（BR1.3・BR1.4）。
    attributes:
      - name: outcome
        type: enum
        required: true
        allowed_values: [SUPPORTED, EMPTY, UNSUPPORTED]
      - name: kind
        type: reference(InternalDbKind)
        required: false
        references: ENT-001
        constraints: "outcome が SUPPORTED のときだけ値を持つ。EMPTY・UNSUPPORTED のときは持たない"
    constraints:
      - "接続先の値そのもの（URL・利用者・パスワード）はこの結果に入れない。EMPTY・UNSUPPORTED はログに出す設定の名前と対応する種類を、固定の文で持つだけにする（BR1.3・BR1.4・BR5.2）"
      - "値が null・空・空白だけのとき EMPTY。それ以外で ENT-001 のどの urlPrefix でも始まらないとき UNSUPPORTED"
    relationships:
      - target: ENT-001
        cardinality: "0..1（SUPPORTED のとき1）"
        direction: ENT-002 -> ENT-001

  - id: ENT-003
    name: MigrationScript
    description: >
      Flyway の移行の1ファイル（V<版の番号>__<説明>.sql）。種類ごとの置き場に、同じ版の番号・同じ説明のファイルが1組ずつある（BR2.3）。
      普段の verify の突き合わせの単体テスト（BR2.8）が、2つの置き場のファイルをこの形で読んで照らす。
    attributes:
      - name: kind
        type: reference(InternalDbKind)
        required: true
        references: ENT-001
      - name: fileName
        type: string
        required: true
        constraints: "V<版の番号>__<説明>.sql の形。置き場の中で重ならない"
      - name: version
        type: integer
        required: true
        min: 1
        constraints: "このIntent の始めは 1〜14。種類の中で重ならない。連続している（欠けが無い）"
      - name: description
        type: string
        required: true
      - name: objectNames
        type: set(string)
        required: true
        constraints: >
          ファイルの SQL から取り出した表の名前・名前付きの制約の名前・索引の名前の集まり（CREATE TABLE・CONSTRAINT・CREATE [UNIQUE] INDEX の後の名前。
          コメントは取り除いてから読む）。名前の無い主キーは含めない（DB が自動で付けた名前は種類で違うため。BR2.4）
    constraints:
      - "同じ version の MigrationScript は H2 と POSTGRESQL に1つずつあり、fileName が同じ（BR2.3）"
      - "同じ version の2つの objectNames は同じ集まり（BR2.4）"
      - "POSTGRESQL の MigrationScript の SQL に ON DELETE の指定・BINARY(・BINARY LARGE OBJECT が無い（BR2.5・BR2.6）"
      - "H2 の MigrationScript の内容は、適用済みのまま変えない。ファイルごとの SHA-256 を単体テストに固定して照らす（BR2.1）"
    relationships:
      - target: ENT-001
        cardinality: "多対1（種類ごとに V1〜V14 の14本）"
        direction: ENT-003 -> ENT-001

  - id: ENT-004
    name: BinaryColumnMapping
    description: >
      バイナリの列の型とエンティティの写し方の組（契約 C3 の決定）。H2 の列の型は変えず、PostgreSQL の列の型とエンティティの写し方を1つに決める。
      捨ての試し（Hibernate 7.4.13・H2 2.4.240・PostgreSQL 18.6）で、両方の `ddl-auto: validate` と往復が通ることを確かめた組だけを持つ（4行の参照データ）。
    attributes:
      - name: column
        type: string
        required: true
        unique: true
        allowed_values:
          - dsl_previews.yaml_bytes
          - dsl_applied_revisions.yaml_bytes
          - refresh_tokens.token_hash
          - invitations.token_hash
      - name: group
        type: enum
        required: true
        allowed_values: [LARGE_BINARY, FIXED_SHORT_BINARY]
      - name: h2ColumnType
        type: string
        required: true
        allowed_values: ["BINARY LARGE OBJECT", "BINARY(32)"]
        constraints: "H2 の移行は変えないため、今の型のまま（BR2.1）"
      - name: postgresqlColumnType
        type: string
        required: true
        allowed_values: ["BYTEA"]
        constraints: "4列とも BYTEA。固定長の列にも CHECK は足さない（長さはアプリの requireHash が守る。BR4.2）"
      - name: entityMapping
        type: string
        required: true
        allowed_values:
          - "@JdbcTypeCode(SqlTypes.LONG32VARBINARY)（@Lob を外す）"
          - "@JdbcTypeCode(SqlTypes.BINARY)・長さ 32（今のまま）"
        constraints: "列ごとに1つ。種類で分岐しない（BR4.3）"
    constraints:
      - "LARGE_BINARY の2列: h2ColumnType=BINARY LARGE OBJECT、entityMapping=@JdbcTypeCode(SqlTypes.LONG32VARBINARY)。エンティティの変更は U2 が行う（契約 C3）"
      - "FIXED_SHORT_BINARY の2列: h2ColumnType=BINARY(32)、entityMapping は今のまま。エンティティの変更は無い"
      - "どの列も両方の種類で ddl-auto: validate が通り、往復（10MB ちょうどの本文・32 バイトのハッシュ）が1バイトも変わらない（BR4.3。往復の確かめは U3）"

  - id: ENT-005
    name: StartupFailureReport
    description: >
      起動時の失敗（対応しない接続先・空の接続先・接続できない・移行の失敗）を、ログに出してよい項目だけに写した記録。例外の文（getMessage）と原因の連なり（cause）は持たない
      （接続先のホスト・ポート・利用者名・URL を持ち込まないため。質問 Q1: A）。
    attributes:
      - name: failureKind
        type: enum
        required: true
        allowed_values: [EMPTY_URL, UNSUPPORTED_KIND, CONNECTION_FAILED, MIGRATION_FAILED, MIGRATION_VALIDATION_FAILED, UNKNOWN]
      - name: exceptionTypes
        type: list(string)
        required: false
        constraints: "原因の連なりの各例外のクラスの名前（パッケージ付き）。連なりの深さの上限は 10。文は含めない"
      - name: sqlState
        type: string
        required: false
        constraints: "連なりの中の SQLException の SQLState（5文字）。無いときは持たない"
      - name: migrationVersion
        type: string
        required: false
        constraints: "failureKind が MIGRATION_FAILED・MIGRATION_VALIDATION_FAILED のとき、取れれば失敗した移行の版の番号（例: 3）。取れなければ持たない"
      - name: settingName
        type: string
        required: false
        allowed_values: ["MASTERSMITH_DB_URL"]
        constraints: "failureKind が EMPTY_URL・UNSUPPORTED_KIND のとき持つ。環境変数の名前だけ（値は持たない）"
      - name: supportedKinds
        type: list(string)
        required: false
        allowed_values: ["H2", "PostgreSQL"]
        constraints: "failureKind が UNSUPPORTED_KIND のとき持つ。ENT-001 の code から作る固定の並び"
    constraints:
      - "どの項目にも、接続先の値・利用者名・パスワード・ホスト・ポート・例外の文を入れない（BR5.2。単体テストで、これらの文字列を入れた例外を渡しても出力に出ないことを確かめる）"
      - "failureKind=CONNECTION_FAILED は、連なりの中の SQLException の SQLState の先頭2文字が 08（接続）・28（認証）・3D（データベースが無い）のとき。判定できないときは UNKNOWN"
```

## 人が読む要約

| ID | エンティティ | 持ち主の部品 | 何を表すか |
|---|---|---|---|
| ENT-001 | InternalDbKind | InternalDbPlatform | 内部DB の種類（H2・PostgreSQL）と、種類ごとの URL の先頭・移行の置き場・接続のたびの文 |
| ENT-002 | InternalDbUrlResolution | InternalDbPlatform | 接続先の値の判断の結果（対応する種類・空・対応しない種類） |
| ENT-003 | MigrationScript | InternalDbMigration | 移行の1ファイル。H2 と PostgreSQL の2つの置き場で同じ版の番号・同じ名前の組を作る |
| ENT-004 | BinaryColumnMapping | InternalDbMigration | バイナリの4列の PostgreSQL の列の型とエンティティの写し方（契約 C3 の決定） |
| ENT-005 | StartupFailureReport | InternalDbPlatform | 起動時の失敗を、ログに出してよい項目だけに写した記録 |

- InternalDbPlatform が ENT-001・ENT-002・ENT-005 を持ち、InternalDbMigration が ENT-003・ENT-004 を持つ（`inception/domain-design/components.md` の2つの部品。持ち主は1つだけ）。
- 内部DB の既存のエンティティ（User・Role・DslPreview・DslAppliedRevision など）の持ち主は変えない。ENT-004 は、そのうち DslPreview・DslAppliedRevision・RefreshToken・Invitation の4列の PostgreSQL での型と写し方だけを決める。

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/domain-design/components.md`（InternalDbPlatform・InternalDbMigration）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/domain-design/decisions.md`（ADR-001〜ADR-004）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/contract-design/contract-summary.md`（C1〜C3）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md`（FR1〜FR3・NFR2）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/functional-design/functional-design-questions.md`（Q1: A・Q2: A、捨ての試しの結果）
- `aidlc/spaces/default/memory/team.md`（Code Style）・`aidlc/spaces/default/memory/project.md`（Code Style・Forbidden）
- 読んだ知識の置き場（`team.md` の Knowledge Pointers。この単位が触れる範囲に当たるものだけを読んだ）: `aidlc/spaces/default/knowledge/aidlc-shared/checklist-auth-audit.md`（初期管理者の自動作成・秘密情報の漏えい）・`aidlc/spaces/default/knowledge/aidlc-shared/checklist-dsl.md`（DSL の本文の大きさと、バイナリ列の上限の突き合わせ）・`aidlc/spaces/default/knowledge/aidlc-shared/checklist-timezone.md`（時刻の列の型と、実行する機械のタイムゾーンに預けないテスト）。ほかのチェックリスト（業務データ・他テーブル参照・役割・メニュー・メール）と `ops-notes.md`（配備の段以降）は、この単位の範囲に当たらないため読んでいない。
