# 業務の決まり: u1-internal-db-platform（機能設計。Intent 261114-internal-db-postgresql）

この単位の決まりを `BR<グループ>.<連番>` で並べます。グループは、BR1 種類の判断、BR2 移行の置き場、BR3 接続の設定、BR4 バイナリと時刻の列、BR5 起動時の失敗の記録、BR6 結合テストの土台、BR7 構造とテストの置き場です。源（`source`）は要件定義の FR・NFR、または質問の答え（`Q1: A` など。この段の `functional-design-questions.md`）です。

次の YAML のブロックが決まりの正（source of truth）です。

```yaml
rules:
  # ---- BR1 内部DB の種類の判断 ----
  - id: BR1.1
    statement: "内部DB の種類は、接続先の値の先頭で決める。jdbc:h2: は H2、jdbc:postgresql: は PostgreSQL。"
    category: validation
    applies_to: [InternalDbPlatform, ENT-001, ENT-002]
    trigger: "起動のとき、内部DB の接続を作る前"
    logic: >
      IF 接続先の値が空でも空白だけでもない
      THEN 値が jdbc:h2: で始まれば SUPPORTED(H2)、jdbc:postgresql: で始まれば SUPPORTED(POSTGRESQL)、どちらでもなければ UNSUPPORTED。
      比べ方は大文字と小文字を区別する前方一致で、前後の空白は取り除かない（取り除かないので、前に空白のある値は UNSUPPORTED になる）。
    violation: "BR1.4 による（UNSUPPORTED で起動を止める）"
    source: "FR1.2"

  - id: BR1.2
    statement: "接続先の環境変数が無いときは、今と同じ H2 のファイルで動く。"
    category: policy
    applies_to: [InternalDbPlatform, application.yaml の既定値]
    trigger: "起動のとき、MASTERSMITH_DB_URL の環境変数が無い"
    logic: >
      IF MASTERSMITH_DB_URL が無い
      THEN 既定の接続先（jdbc:h2:file:./data/mastersmith;DEFRAG_ALWAYS=TRUE）が使われ、BR1.1 で H2 と判断される。既定の接続先と利用者・パスワードの既定値は変えない。
    violation: "なし（既定の動き）"
    source: "FR1.1"

  - id: BR1.3
    statement: "接続先が「設定されているが空（空白だけを含む）」のときは、起動を止める。"
    category: validation
    applies_to: [InternalDbPlatform, ENT-002, ENT-005]
    trigger: "起動のとき、spring.datasource.url の値が空文字・空白だけ"
    logic: >
      IF 接続先の値が空文字または空白だけ
      THEN 結果は EMPTY とし、起動を止める。ログ（ERROR）には failureKind=EMPTY_URL と設定の名前（MASTERSMITH_DB_URL）と、
      「空のときは環境変数ごと消す」ことだけを出す（接続先の値は空なので出すものが無い）。今のアプリは空だとメモリだけの H2 で静かに起動するが、それをやめる。
    violation: "起動の失敗（BR5.1 の形のログと、起動を止める例外）"
    source: "FR1.1; 質問 Q2: A（2026-11-17、依頼者の決定）"

  - id: BR1.4
    statement: "対応しない種類の接続先（MySQL・MariaDB など）では、起動を止める。"
    category: validation
    applies_to: [InternalDbPlatform, ENT-002, ENT-005]
    trigger: "起動のとき、BR1.1 の結果が UNSUPPORTED"
    logic: >
      IF 結果が UNSUPPORTED
      THEN 起動を止める。ログ（ERROR）には failureKind=UNSUPPORTED_KIND と、設定の名前（MASTERSMITH_DB_URL）と、対応する種類（H2・PostgreSQL）だけを出す。
      接続先の値・利用者・パスワード・ホスト・ポートは出さない。
    violation: "起動の失敗（BR5.1 の形のログと、起動を止める例外）"
    source: "FR1.3"

  - id: BR1.5
    statement: "種類の判断は起動のときに1回だけ、InternalDbPlatform の1か所で行う。ほかの部品は種類を見ない。"
    category: policy
    applies_to: [InternalDbPlatform, 業務処理の層, repository の層]
    trigger: "設計・実装"
    logic: >
      IF 内部DB の種類によって動きが変わる処理がある（移行の置き場・接続のたびの文）
      THEN その処理は InternalDbPlatform が決めた InternalDbKind（ENT-001）の値だけを使い、業務処理の層と repository の層は種類で分岐しない。
      判断の結果は起動の間じゅう変わらない。
    violation: "ArchUnit の境界テストが失敗する（BR7.2）"
    source: "FR1.2; NFR9; ADR-001"

  - id: BR1.6
    statement: "内部DB の接続先・利用者・パスワードは、環境変数（.env）だけから受け取る。"
    category: constraint
    applies_to: [application.yaml, InternalDbPlatform]
    trigger: "設計・実装"
    logic: >
      IF 内部DB の接続の設定を受け取る
      THEN 今の環境変数（MASTERSMITH_DB_URL・MASTERSMITH_DB_USERNAME・MASTERSMITH_DB_PASSWORD・MASTERSMITH_DB_MAXIMUM_POOL_SIZE）の名前と形を変えず、
      画面・API・DSL から受け取る口を作らない。.env.example には値を空のまま項目を足すだけにする（足す項目は無い）。
    violation: "境界テスト（内部DB の設定を受ける部品が web の層に依存しない）と、application.yaml の読み合わせで拾う"
    source: "FR1.4"

  - id: BR1.7
    statement: "H2 のときの設定は、今と1つも変えない。"
    category: constraint
    applies_to: [InternalDbKind=H2]
    trigger: "起動のとき、結果が SUPPORTED(H2)"
    logic: >
      IF 種類が H2
      THEN 移行の置き場は classpath:db/migration、接続のたびの文は無し、接続プールの設定・DEFRAG_ALWAYS・H2 だけの運用の設定は今のまま。
      前の版で作った H2 のファイルが、接続先を変えずにそのまま起動し、移行の検証（validate-on-migrate）が通り、既存の利用者でログインできる。
    violation: "前の版の H2 のファイルでの起動の確かめ（BR7.3 の T6）と、既存の結合テストの全件が拾う"
    source: "FR1.1"

  # ---- BR2 移行の置き場 ----
  - id: BR2.1
    statement: "H2 の移行（今の V1〜V14）は1文字も変えない。置き場も今のまま（classpath:db/migration）。"
    category: constraint
    applies_to: [InternalDbMigration, ENT-003]
    trigger: "設計・実装・後の Intent での移行の追加"
    logic: >
      IF 適用済みの H2 の移行のファイルがある
      THEN 内容と置き場を変えない。普段の verify の単体テストが、V1〜V14 のファイルごとの SHA-256 を照らし、1つでも変わったら失敗する
      （Flyway の validate-on-migrate と同じ守りを、適用の前の段階で行う）。後の Intent で足す移行のファイルは、この固定の一覧に足す。
    violation: "単体テストの失敗（BR2.8）"
    source: "FR2.2"

  - id: BR2.2
    statement: "PostgreSQL 用の移行は、H2 の置き場の外の兄弟の置き場（classpath:db/migration-postgresql）に一式置き、種類に応じて Flyway に渡す置き場を選ぶ。"
    category: policy
    applies_to: [InternalDbMigration, InternalDbPlatform, ENT-001]
    trigger: "起動のとき、Flyway の設定を組み立てる"
    logic: >
      IF 種類が POSTGRESQL THEN Flyway の置き場は classpath:db/migration-postgresql だけにする。
      IF 種類が H2 THEN classpath:db/migration だけにする。
      置き場の名前を db/migration の下の階層にしない（Flyway は置き場を再帰して探すため、H2 の一式に PostgreSQL の一式が混ざり、版の番号が重なって失敗する。捨ての試しで確かめた）。
      spring.flyway.locations は H2 の既定の値のまま残し、種類が POSTGRESQL のときだけ起動の設定の差し替えで置き場を替える。
    violation: "単体テスト（H2 の置き場を再帰して探しても H2 の14本だけが見つかる）の失敗"
    source: "FR2.1; FR2.2"

  - id: BR2.3
    statement: "2つの置き場は、同じ版の番号・同じ説明のファイル（V<番号>__<説明>.sql）を1組ずつ持つ。"
    category: constraint
    applies_to: [InternalDbMigration, ENT-003]
    trigger: "設計・実装・後の Intent での移行の追加"
    logic: >
      IF H2 の置き場に V<n>__<説明>.sql がある THEN PostgreSQL の置き場に同じ名前のファイルがある（逆も同じ）。版の番号は 1 から欠けなく続く。
      このIntent の始めは V1〜V14 の14組。
    violation: "突き合わせの単体テストの失敗（BR2.8）"
    source: "FR2.3"

  - id: BR2.4
    statement: "同じ版の2つのファイルは、同じ表・同じ名前付きの制約・同じ索引の名前を作る。"
    category: constraint
    applies_to: [InternalDbMigration, ENT-003]
    trigger: "設計・実装"
    logic: >
      IF 同じ版の2つのファイルから objectNames（表・名前付きの制約・索引の名前の集まり）を取り出す
      THEN 2つは同じ集まりである。違反の区分が制約の名前で照らすため、名前は H2 と同じにする。名前の無い主キー（9つの表）は、DB が自動で付ける名前に任せ、比べない。
      列の名前と型は、普段は H2・PostgreSQL の結合テストの ddl-auto: validate が拾う（列まで読む解析はしない）。
    violation: "突き合わせの単体テストの失敗（BR2.8）。PostgreSQL の結合テスト（BR2.10）で、H2 の置き場の名前がすべて PostgreSQL のカタログにあることも確かめる"
    source: "FR2.1"

  - id: BR2.5
    statement: "PostgreSQL 用の外部キーには ON DELETE を付けない（NO ACTION）。制約の名前は H2 と同じにする。"
    category: constraint
    applies_to: [InternalDbMigration]
    trigger: "PostgreSQL 用の V10〜V12（H2 では ON DELETE RESTRICT の外部キーが7つ）"
    logic: >
      IF PostgreSQL 用の外部キーを作る
      THEN ON DELETE の指定を書かない。参照のある親の削除は SQLState 23503 になり、制約の名前が取れ、今の区分の部品の判定の表を変えずに H2 と同じ応答になる
      （ON DELETE RESTRICT だと 23001 になり、Hibernate が制約の名前を取れず想定外の失敗に分けられる）。今のアプリは遅らせた確かめ（DEFERRABLE）を使わないため、1文の中の振る舞いは H2 と同じ。
      この前提は PostgreSQL 用の移行のファイルの先頭の注に書き、後の Intent で DEFERRABLE を使うときは見直す。
    violation: "単体テスト（PostgreSQL の置き場に ON DELETE が無い）の失敗"
    source: "FR3.2; ADR-004"

  - id: BR2.6
    statement: "PostgreSQL 用の移行は、H2 用の文のうち次の3種類だけを書き換え、それ以外は H2 用と同じ文にする。"
    category: constraint
    applies_to: [InternalDbMigration, V3, V5, V8]
    trigger: "PostgreSQL 用の移行の作成"
    logic: >
      (1) BINARY(32) を BYTEA にする（V3 の refresh_tokens.token_hash・V8 の invitations.token_hash）。
      (2) BINARY LARGE OBJECT を BYTEA にする（V5 の dsl_previews.yaml_bytes・dsl_applied_revisions.yaml_bytes）。
      (3) V8 の生成列 pending_email は GENERATED ALWAYS AS (CASE WHEN state = 'PENDING' THEN email END) STORED にし、同じ名前の一意の制約 uk_invitations_pending_email を付ける
      （STORED が無いと PostgreSQL は生成列に一意の制約を付けられない。H2 と同じ列・同じ制約の名前のまま作れる）。
      (4) BR2.5 のとおり ON DELETE の指定を外す。
      V1・V2・V4・V6・V7・V9〜V14 は、捨ての試しで PostgreSQL 18.6 にそのまま通ったので、文の内容を変えない（ファイルの内容は H2 用と同じ。ON DELETE を除く）。
    violation: "単体テスト（PostgreSQL の置き場に BINARY(・BINARY LARGE OBJECT が無い）と、PostgreSQL の結合テストの移行の失敗"
    source: "FR2.1; FR3.6"

  - id: BR2.7
    statement: "後の Intent で表を変えるときは、両方の置き場に同じ版の番号で足す。"
    category: policy
    applies_to: [InternalDbMigration]
    trigger: "後の Intent で内部DB の表を変える"
    logic: >
      IF 新しい移行を足す THEN 同じコミットで、H2 の置き場と PostgreSQL の置き場の両方に、同じ版の番号・同じ説明のファイルを足し、
      H2 用のファイルを BR2.1 の固定の一覧に足す。片方だけ足すと BR2.8 の単体テストが失敗する。
    violation: "突き合わせの単体テストの失敗"
    source: "FR2.3"

  - id: BR2.8
    statement: "移行の2つの置き場の突き合わせの確かめを、普段の ./gradlew verify に入る単体テストにする。"
    category: validation
    applies_to: [MigrationScript, 単体テスト]
    trigger: "普段の verify・verifyQuick（コンテナ不要）"
    logic: >
      次をすべて確かめる単体テスト（クラスは H2 の移行のファイルの固定、置き場の再帰、2つの置き場の突き合わせの3つに分けてよい）を置く。
      (a) H2 の V1〜V14 のファイルごとの SHA-256 が固定の一覧と同じ（BR2.1）。
      (b) classpath:db/migration を再帰して探すと H2 のファイルの14本だけが見つかる（BR2.2）。
      (c) 2つの置き場のファイルの名前（版の番号と説明）の集まりが同じで、版の番号が欠けなく続く（BR2.3）。
      (d) 同じ版の2つのファイルの objectNames が同じ（BR2.4）。
      (e) PostgreSQL の置き場に ON DELETE・BINARY(・BINARY LARGE OBJECT が無い（BR2.5・BR2.6）。
      (f) 突き合わせの部品自身を確かめる小さな例（わざと片方に足した名前・欠けた版・変えたファイル）で失敗すること。
    violation: "テストの失敗（統合の前の検査が止まる）"
    source: "FR2.3; FR5.5"

  - id: BR2.9
    statement: "移行が失敗したときは起動を止め、失敗した移行の版の番号をログに出す。"
    category: policy
    applies_to: [InternalDbPlatform, ENT-005]
    trigger: "起動のとき、Flyway の移行・検証が例外で終わる"
    logic: >
      IF 起動の入口（Flyway の移行を呼ぶ所。BR5.1）が Flyway の移行の例外を受ける
      THEN failureKind を MIGRATION_FAILED（移行の文の失敗）または MIGRATION_VALIDATION_FAILED（検証の失敗）にし、取れれば migrationVersion（BR5.3）を付けて ERROR を出し、起動を止める。
      資格情報・接続先の値は出さない（BR5.2）。H2 のときも同じ形で止める。
    violation: "なし（これが失敗時の決まり）"
    source: "FR2.4"

  - id: BR2.10
    statement: "空の PostgreSQL への移行が V1〜V14 のすべてで通ることと、エンティティとの照合が通ることを、PostgreSQL の結合テストで確かめる。"
    category: validation
    applies_to: [InternalDbPostgresTestSuite（土台の上の最初の結合テスト）]
    trigger: "./gradlew :backend:internalDbPostgresTest"
    logic: >
      空のデータベース（専用に作ったもの）に、本番と同じ設定（接続のたびの lock_timeout 2 秒を含む）でアプリを起動し、
      (a) flyway_schema_history に V1〜V14 が成功で記録される、(b) H2 の置き場に書かれた制約と索引の名前がすべて PostgreSQL のカタログにある、
      (c) 起動時の ddl-auto: validate が通ってアプリが起動する、(d) バイナリの4列の型が bytea である、(e) 接続の lock_timeout が 2 秒になっている（SHOW lock_timeout）、ことを確かめる。
      移行の失敗の確かめは、専用のデータベースに前もって同じ名前の表を作っておき、起動が止まり、ログに失敗した版の番号が出て、接続先・利用者・パスワードが出ないことを確かめる。
    violation: "テストの失敗"
    source: "FR2.1; FR5.1"

  - id: BR2.11
    statement: "PostgreSQL の内部DB を空から始めたときも、初期管理者は今の H2 の初回の起動と同じ決まりで作る。"
    category: policy
    applies_to: [InitialAdminInitializer（今のまま）, InternalDbPostgresTestSuite]
    trigger: "空の PostgreSQL で、初期管理者の設定を付けて起動する"
    logic: >
      IF 初期管理者の設定が有る THEN 1人だけ作られ、2回目以降の起動（同じデータベースで文脈を作り直す）では重ねて作られない。
      設定が無い・正しくないときは作らず警告して起動を続ける（今のまま）。ログにパスワードが出ない。コードは変えず、PostgreSQL の結合テストで確かめる。
    violation: "テストの失敗"
    source: "FR2.5"

  - id: BR2.12
    statement: "Flyway の PostgreSQL 用の部品（org.flywaydb:flyway-database-postgresql）を依存に足す。版は Spring Boot の BOM に任せる。"
    category: constraint
    applies_to: [backend/build.gradle.kts, gradle/libs.versions.toml, backend/gradle.lockfile]
    trigger: "実装（コード生成の最初の手順）"
    logic: >
      IF 依存を足す THEN (a) ライセンス（Apache License 2.0。確かめ済み）、(b) lockfile で版を固定（Spring Boot 4.1.2 の BOM が管理する 12.4.0）、
      (c) OSV-Scanner の対象に載ること、(d) 依存の木で既存の部品の版を引き上げないこと（flyway-core と同じ版だけ。確かめ済みの見込みを実装で再確認）、
      (e) 実行できる WAR に入り、META-INF/third-party-licenses の一覧の決まりに従うこと、を確かめて記録する。
    violation: "統合の前の検査（OSV-Scanner・lockfile）が止める"
    source: "NFR8"

  # ---- BR3 接続の設定 ----
  - id: BR3.1
    statement: "PostgreSQL のときは、接続を作るたびに lock_timeout を 2 秒にする。"
    category: policy
    applies_to: [InternalDbPlatform, ENT-001]
    trigger: "PostgreSQL の内部DB の接続を作るとき"
    logic: >
      IF 種類が POSTGRESQL THEN 内部DB の接続プールが接続を作るたびに SET lock_timeout = '2s' を流す（プールの「接続のたびの文」の設定に、種類が決まった後で入れる）。
      利用者が spring.datasource.hikari.connection-init-sql を指定していても、PostgreSQL のときはこの文で置き換える（この設定はこの機能が持つ）。
      これで、書き込みの待ち・確定前の同じ一意の鍵への追加の待ちが 2 秒台で切れ、H2 と同じ失敗の区分になる（捨ての試しで確かめた）。
    violation: "PostgreSQL の結合テストの失敗（BR2.10(e)）。単体テスト（プールの設定を組み立てる部品が PostgreSQL のときだけ文を入れる）の失敗"
    source: "FR3.3; 契約 C1"

  - id: BR3.2
    statement: "H2 のときは、接続のたびの文を何も足さない。"
    category: constraint
    applies_to: [InternalDbKind=H2]
    trigger: "H2 の内部DB の接続を作るとき"
    logic: IF 種類が H2 THEN 接続プールの設定に手を入れない（connectionInitSql は null のまま）。
    violation: "単体テストの失敗"
    source: "FR1.1"

  - id: BR3.3
    statement: "行の排他の読み取りの待ちの上限のヒント（jakarta.persistence.lock.timeout の 3000ms）には手を入れない。"
    category: constraint
    applies_to: [既存の repository の @QueryHints]
    trigger: "実装"
    logic: >
      IF 行の排他の読み取り THEN Hibernate が問い合わせの間だけ lock_timeout をヒントの値（3 秒台）で上書きし、終わると接続の 2 秒へ戻る（下調べで PostgreSQL 18.6 に確かめ、
      確かめの結合テストは U3）。このためには、接続をまとめる中継（PgBouncer の transaction モードなど）を挟まない前提が要る（ADR-006）。
    violation: "U3 の結合テストの失敗"
    source: "FR3.3; ADR-001; ADR-006"

  - id: BR3.4
    statement: "起動時の移行も、BR3.1 と同じ接続の設定（lock_timeout 2 秒）のまま通る。"
    category: policy
    applies_to: [InternalDbPlatform, Flyway]
    trigger: "起動のとき、PostgreSQL の内部DB に移行を流す"
    logic: >
      IF 内部DB を使うのがこのアプリ1つだけ（NFR7）THEN 移行の文が待つ相手が無く、lock_timeout に当たらずに通る（捨ての試しで、空の PostgreSQL に V1〜V14 が 365 ms で全件通った）。
      外の接続が移行の表を握っているときは 2 秒で失敗し、BR2.9 の移行の失敗として起動が止まる。移行の接続だけ待ちの上限を外すか（別の接続で移行する）の判断は、NFR 設計に回す（NFR4）。
    violation: "PostgreSQL の結合テストの失敗（BR2.10）"
    source: "FR3.3; NFR4"

  - id: BR3.5
    statement: "PostgreSQL のドライバーのログは OFF のままにする（今の対象DB の設定と同じ）。"
    category: constraint
    applies_to: [application.yaml の logging.level.org.postgresql]
    trigger: "実装"
    logic: >
      IF PostgreSQL を内部DB にする THEN logging.level.org.postgresql: OFF を変えない。ドライバーのログは接続の URL・利用者を出すことがあるため、失敗の原因は BR5.1 の
      安全な行（例外の型の名前・SQLState）で見る。「対象DB のドライバーのログを抑える設定が、内部DB の失敗の原因まで隠す」（要件 FR3.10）という心配は、
      ドライバー自身のログに頼らず、BR5.1 の行で原因を出す形で解く。
    violation: "設定の読み合わせ（単体テストで application.yaml の値を確かめる）"
    source: "FR3.10"

  - id: BR3.6
    statement: "ヘルスチェックは、内部DB が PostgreSQL のときも届けば UP、届かなければ DOWN を返す。時間の上限は今と同じ。"
    category: policy
    applies_to: [TimeBoundedDbHealthIndicator（今のまま）, InternalDbPostgresTestSuite]
    trigger: "ヘルスチェックの要求"
    logic: >
      IF 内部DB が PostgreSQL THEN 今のヘルスチェックの部品（自分で接続を借りて SELECT 1 を1回流す。上限は mastersmith.health.db-timeout の既定 2 秒）がそのまま UP・DOWN を返す。
      コードは変えず、PostgreSQL の結合テストで確かめる（届くとき UP、コンテナを一時停止して届かないとき DOWN、再開して UP に戻る）。
    violation: "テストの失敗"
    source: "FR3.9"

  # ---- BR4 バイナリと時刻の列 ----
  - id: BR4.1
    statement: "大きなバイナリ（DSL の本文の2列）は、PostgreSQL の列を BYTEA にし、エンティティの写し方を @JdbcTypeCode(SqlTypes.LONG32VARBINARY)（@Lob を外す）にする。"
    category: constraint
    applies_to: [ENT-004, DslPreviewRecord, DslAppliedRevisionRecord]
    trigger: "PostgreSQL 用の移行（U1）と、エンティティの写し方の変更（U2）"
    logic: >
      IF 列が dsl_previews.yaml_bytes または dsl_applied_revisions.yaml_bytes
      THEN PostgreSQL の列は BYTEA、H2 の列は BINARY LARGE OBJECT のまま、エンティティは @Lob を外して @JdbcTypeCode(SqlTypes.LONG32VARBINARY)。
      捨ての試し（Hibernate 7.4.13・H2 2.4.240・PostgreSQL 18.6）で、今の @Lob byte[] は PostgreSQL の BYTEA に対して validate が失敗し（oid を期待）、
      この写し方は両方で validate が通り、10MB ちょうどの往復が一致した。BYTEA の上限（1GB）は DSL の本文の上限（10MB）を十分に超える。
      エンティティの変更は U2 が実装する（契約 C3）。
    violation: "ddl-auto: validate の失敗（H2 の結合テスト全件と PostgreSQL の結合テスト）"
    source: "FR3.6; 契約 C3"

  - id: BR4.2
    statement: "固定長の短いバイナリ（トークンのハッシュの2列）は、PostgreSQL の列を BYTEA にし、エンティティの写し方は今のまま（@JdbcTypeCode(SqlTypes.BINARY)・長さ 32）にする。"
    category: constraint
    applies_to: [ENT-004, RefreshToken, Invitation]
    trigger: "PostgreSQL 用の移行"
    logic: >
      IF 列が refresh_tokens.token_hash または invitations.token_hash
      THEN PostgreSQL の列は BYTEA（H2 は BINARY(32) のまま）。捨ての試しで、エンティティを変えずに両方で validate が通り、32 バイトの往復が一致した。
      PostgreSQL の列に長さの CHECK は足さない（名前付きの制約の集まりを H2 と同じにするため。長さはアプリの requireHash が守る）。
    violation: "ddl-auto: validate の失敗"
    source: "FR3.6; 契約 C3"

  - id: BR4.3
    statement: "バイナリの4列は、どれも両方の種類で1つの写し方だけを使い、種類で分岐しない。"
    category: constraint
    applies_to: [ENT-004]
    trigger: "設計・実装"
    logic: >
      IF 4列のどれか THEN 列の型（BR4.1・BR4.2）とエンティティの写し方の組は1つだけで、DB の種類による分岐を作らない（ADR-003）。
      単一の写し方が見つからないときは、閉じる前に依頼者に諮る（契約 C3 の決め）ことになっていたが、見つかったので諮る必要は無い。
    violation: "ddl-auto: validate の失敗（H2 の結合テスト全件・PostgreSQL の結合テスト）"
    source: "FR3.6; ADR-003"

  - id: BR4.4
    statement: "バイナリの組の確かめは、この単位が ddl-auto: validate を持ち、往復は U2・U3 が持つ。"
    category: policy
    applies_to: [u1-internal-db-platform, u2-portable-sql, u3-postgres-behaviour-tests]
    trigger: "設計・実装"
    logic: >
      IF 組の確かめを割り振る THEN U1 は PostgreSQL の結合テスト（BR2.10）で列の型が bytea であることと起動時の validate を確かめる。
      写し方のエンティティの変更は U2、10MB ちょうどの本文と 32 バイトのハッシュの PostgreSQL での往復は U3（FR3.6 の受け持ち）。
    violation: "なし（割り振り）"
    source: "FR3.6"

  - id: BR4.5
    statement: "時刻の列（TIMESTAMP WITH TIME ZONE）は、PostgreSQL でも同じ型（timestamptz）で作り、実行する機械のタイムゾーンに預けない。"
    category: constraint
    applies_to: [InternalDbMigration]
    trigger: "PostgreSQL 用の移行"
    logic: >
      IF 時刻の列を作る THEN H2 用と同じ TIMESTAMP WITH TIME ZONE を使う（PostgreSQL では timestamptz。時差を持たない時点として保存される）。書き換えは要らない。
      往復と時刻で決まる判定の確かめは U3。実行時のタイムゾーンの設定（Dockerfile の -Duser.timezone・compose の TZ・テストの JVM・E2E の起動）は、この単位では変えない。
    violation: "U3 の結合テストの失敗"
    source: "FR3.7"

  # ---- BR5 起動時の失敗の記録 ----
  - id: BR5.1
    statement: "起動時の失敗（対応しない接続先・空の接続先・接続できない・移行の失敗）は、起動の入口で受け、ログには ENT-005 の項目だけを出して起動を止める。"
    category: policy
    applies_to: [InternalDbPlatform, ENT-005]
    trigger: "起動のとき、BR1.3・BR1.4 の判断の結果、または Flyway の移行を呼んだときの例外"
    logic: >
      IF 起動の入口の部品（Flyway の移行を呼ぶ入口として Spring Boot に登録した部品。種類の判断の失敗はその前に同じ部品の形で出す）が失敗を受ける
      THEN (1) ENT-005 の StartupFailureReport を作る。(2) ERROR を、キーと値（failureKind・exceptionTypes・sqlState・migrationVersion・settingName・supportedKinds）で1回だけ出す。
      (3) 新しい例外（文は failureKind と固定の説明だけ。原因 cause は付けない）を投げて起動を止める。元の例外はログにもスタックトレースにも持ち越さない。
      これで、起動を止める経路で Spring が出す WARN（context 初期化の取り消し）と ERROR（Application run failed）にも、ホスト・ポート・利用者名・URL が出ない。
    violation: "単体テストと、届かない接続先の起動のテスト（BR7.3 の T3）の失敗"
    source: "FR3.10; FR1.3; FR2.4; 質問 Q1: A（2026-11-17、依頼者の決定）"

  - id: BR5.2
    statement: "ログと、投げ直す例外には、接続先の値・利用者名・パスワード・ホスト・ポート・ドライバーや Flyway の例外の文を入れない。"
    category: constraint
    applies_to: [StartupFailureReport]
    trigger: "実装・ログの出力"
    logic: >
      IF 失敗の記録を作る THEN 例外の連なりから取るのは、クラスの名前・SQLState・移行の版の番号だけにする。getMessage() の文・cause・スタックトレースは使わない。
      （捨ての試しで、今のアプリは接続できないとき「127.0.0.1:1 への接続が拒絶されました」を、パスワードが違うとき「password authentication failed for user "…"」をログに出すことを確かめた）。
      単体テストでは、URL・利用者名・パスワード・ホストの文字列を文に入れた例外を渡しても、出力にそれらが出ないことを確かめる。
    violation: "単体テストの失敗"
    source: "FR3.10; NFR2; project.md の Forbidden（内部DB の接続情報をログ・監査ログ・エラー応答に含めない）"

  - id: BR5.3
    statement: "失敗した移行の版の番号は、移行の失敗の例外の文の中の移行のファイルの名前（V<番号>__…sql）から取り出す。"
    category: calculation
    applies_to: [StartupFailureReport]
    trigger: "failureKind が MIGRATION_FAILED・MIGRATION_VALIDATION_FAILED"
    logic: >
      IF 例外の連なりの文に V<数字>__ で始まる移行のファイルの名前があれば THEN その版の番号（数字だけ）を migrationVersion にする（最初に見つかったもの）。
      検証の失敗（チェックサムの不一致など）は「migration version <数字>」の形から取る。どちらも取れなければ migrationVersion は持たない。
      文は版の番号を取り出すためだけに読み、ログには出さない（出すのは数字だけ）。移行のファイルの名前は秘密ではない。
      実際の Flyway の例外（H2 の内存の DB で壊した移行・変えた移行を流して作る）で、取り出せることを単体テストで確かめる。
    violation: "単体テストの失敗"
    source: "FR2.4"

  - id: BR5.4
    statement: "動作中の接続の失敗は、今のまま（Spring・接続プールのログ。ドライバーのログは OFF のまま）にする。"
    category: policy
    applies_to: [内部DB の接続の失敗（起動の後）]
    trigger: "起動の後に内部DB へ届かなくなる"
    logic: >
      IF 動作中に内部DB の接続が失敗する THEN この単位は何も置き換えない。ログの例外の文にホスト・ポート・利用者名が出うることを、README の既知の制約（U4）と、
      この段の「上流との差」に書く。要件 FR3.10 の「動作中」の部分との差は、依頼者の決定（Q1: A）。ヘルスチェックの DOWN は BR3.6 で確かめる。
    violation: "なし（既知の制約）"
    source: "FR3.10; 質問 Q1: A"

  - id: BR5.5
    statement: "失敗の記録のログは ERROR で、構造化ログのキーと値で渡す。"
    category: policy
    applies_to: [InternalDbPlatform]
    trigger: "BR5.1 のログの出力"
    logic: >
      IF 起動を止める失敗のログを出す THEN SLF4J のキー・値の API（addKeyValue）で、failureKind・exceptionTypes（文字列を連結せず配列の文字列でまとめる）・sqlState・migrationVersion・
      settingName・supportedKinds を渡す。文面は固定の日本語1行。同じ失敗を2回出さない（Spring の「Application run failed」は BR5.1 の例外が文だけで受ける）。
    violation: "単体テストの失敗"
    source: "FR3.10; team.md の Code Style（構造化ログのキーと値）"

  - id: BR5.6
    statement: "接続先の値や失敗の例外を受ける部品は、TraceAspect の対象の層の外の用途名のパッケージに置く。"
    category: constraint
    applies_to: [cherry.mastersmith.internaldb.platform]
    trigger: "実装"
    logic: >
      IF 部品が接続先の値（URL に資格情報が入りうる）や失敗の例外を引数・戻り値に取る THEN web・service・domain・repository の層のパッケージに置かず、
      cherry.mastersmith.internaldb.platform（用途名の下位パッケージ）に置く。TraceAspect が各層の引数と戻り値を TRACE に文字列で出すため、URL が TRACE のログに出るのを避ける
      （project.md の Code Style）。ENT-001〜ENT-005 の型と起動の入口の部品はすべてこのパッケージに置く。
    violation: "TRACE を有効にした InternalDbSecretLeakIT（BR7.3 の T11）の失敗（URL・利用者・パスワードを入れた起動の失敗のログに値が出ない）"
    source: "FR3.10; NFR2; project.md の Code Style"

  - id: BR5.7
    statement: "failureKind は、例外の連なりの SQLState とクラスから決める。"
    category: calculation
    applies_to: [StartupFailureReport]
    trigger: "Flyway の例外を受けたとき"
    logic: >
      IF 連なりの中の SQLException の SQLState の先頭2文字が 08（接続）・28（認証）・3D（データベースが無い）THEN CONNECTION_FAILED。
      ELSE IF 連なりに Flyway の移行の文の失敗（Failed to execute script …）の例外がある THEN MIGRATION_FAILED。
      ELSE IF 連なりに Flyway の検証の例外（FlywayValidateException）がある THEN MIGRATION_VALIDATION_FAILED。
      ELSE UNKNOWN（失敗は同じ形でログに出して起動を止める）。接続の失敗が移行の失敗より先に判定される（接続できないときの Flyway の例外にも SQLState 08 が入る）。
    violation: "単体テストの失敗"
    source: "FR3.10; FR2.4"

  # ---- BR6 結合テストの土台 ----
  - id: BR6.1
    statement: "PostgreSQL の結合テストは、Gradle の専用のタスク :backend:internalDbPostgresTest を明示して流したときだけ流れる。"
    category: policy
    applies_to: [backend/build.gradle.kts]
    trigger: "./gradlew :backend:internalDbPostgresTest"
    logic: >
      IF タスク :backend:internalDbPostgresTest を流す THEN JUnit のタグ internal-db-postgres が付いた *IT のクラスだけが流れる。
      結合テストと同じクラスパス・同じ JVM の設定（-Dh2.compactThreads=1 などはそのまま。PostgreSQL に頼るテストが H2 の設定に影響されないよう、テストの設定の部品は H2 に頼らない）を使う。
      verify・verifyQuick・CI の develop へのプッシュは、このタスクに依存しない。JaCoCo の記録（.exec）は verify の報告の対象に入れない。
    violation: "普段の verify の時間が増える（NFR5）、または PostgreSQL のコードがカバレッジに混ざる（NFR6）"
    source: "FR5.2; 契約 C4"

  - id: BR6.2
    statement: "普段の結合テスト（integrationTest）は、タグ internal-db-postgres のクラスを流さない。"
    category: policy
    applies_to: [integrationTest（Gradle のタスク）]
    trigger: "./gradlew verify・verifyQuick・integrationTest"
    logic: IF integrationTest を流す THEN タグ internal-db-postgres を除外する。除外はタスクの設定の1か所に置く。
    violation: "コンテナ無しの環境で verify が落ちる"
    source: "FR5.2; 契約 C4"

  - id: BR6.3
    statement: "-PitTests の形がタグ付きのクラスだけに当たったときは、黙って 0 件で通さず、専用のタスクを案内して失敗する。"
    category: validation
    applies_to: [integrationTest（Gradle のタスク）]
    trigger: "./gradlew verifyQuick -PitTests=<形>"
    logic: >
      IF -PitTests を付けて integrationTest を流し、実行されたテストが 0 件 THEN 失敗にし、
      「タグ internal-db-postgres のクラスは :backend:internalDbPostgresTest で流してください」という文を出す（実行の終わりの集計で判定する）。
      形が普段のクラスにも当たるときは、タグ付きのクラスだけが流れず、普段のクラスが流れる（失敗にしない）。
    violation: "なし（失敗にする）"
    source: "FR5.2; 契約 C4"

  - id: BR6.4
    statement: "コンテナの実行環境が無いとき、手元では警告を出して PostgreSQL の結合テストを飛ばし、CI（明示して流した実行）では飛ばさず失敗する。"
    category: policy
    applies_to: [InternalDbPostgresTestSuite の拡張（InternalDbPostgresRuntimeCheck）]
    trigger: "PostgreSQL の結合テストの前"
    logic: >
      IF Docker が使える THEN 続ける。ELSE IF 環境変数 CI が true、またはシステムプロパティ mastersmith.requireContainerRuntime が true THEN 失敗（飛ばさない）。
      ELSE 警告（アプリのログと標準エラー）を出し、テストを中止扱い（TestAbortedException）で飛ばす。警告の文は内部DB の PostgreSQL のテストを飛ばしたこと、この状態では統合しないことを書く
      （今の ContainerRuntimeCheck の文は「対象DB のテスト」と書かれているため、内部DB 用の確認は別のクラスにする。判定の考え方は同じ）。
      判定の部分は純粋な関数にし、単体テストで確かめる（普段の verify に入る）。
    violation: "なし（飛ばした状態では統合しない。team.md の Way of Working）"
    source: "FR5.3; 契約 C4"

  - id: BR6.5
    statement: "PostgreSQL のコンテナは、テストの JVM ごとに1つ起動して使い回し、イメージは TargetDbImages.postgres() を使う。"
    category: constraint
    applies_to: [InternalDbPostgresTestSuite]
    trigger: "PostgreSQL の結合テストの準備"
    logic: >
      IF 結合テストの土台を作る THEN コンテナは JVM ごとに1つ（クラスをまたいで使い回し、JVM の終わりに止める）。Spring の文脈も同じ設定のテストの間で共有する。
      イメージは対象DB の結合テストと同じ TargetDbImages.postgres()（版 18.6 のダイジェスト固定。新しいテストの定数は作らない。契約 C6）。
      拡張の順序は、targetdb の ExtensionOrderArchitectureTest と同じ決まり（コンテナ・文脈を作る拡張が、確認の拡張の後）を、内部DB の側の境界テストでも確かめる。
    violation: "境界テストの失敗"
    source: "FR5.1; 契約 C4; 契約 C6"

  - id: BR6.6
    statement: "空のデータベースが要るテスト（移行・初期管理者・移行の失敗）は、同じコンテナの中に専用のデータベースを作り、終わったら消す。"
    category: constraint
    applies_to: [InternalDbPostgresTestSuite]
    trigger: "空の内部DB が要る結合テスト"
    logic: >
      IF 空のデータベースが要る THEN 実行ごとに一意の名前（例: msidb_<乱数>）で CREATE DATABASE し、終わったら DROP DATABASE する。途中で止まった前の実行の残りに頼らない。
      それ以外のテストは、1つの共有のデータベースで、自分で作ったデータだけを使い、自分で後始末をして、実行順に依存しない。
    violation: "テストの失敗・実行順に依存した失敗"
    source: "FR5.1; team.md の Testing Posture"

  - id: BR6.7
    statement: "PostgreSQL の結合テストの部品は、H2 に頼る書き方を使わない。説明文は英語、メールアドレスは予約のドメインだけ。"
    category: constraint
    applies_to: [InternalDbPostgresTestSuite]
    trigger: "実装"
    logic: >
      IF テストの部品を書く THEN INFORMATION_SCHEMA.SESSIONS・H2 の Java のトリガー・DATEADD・RANDOM_UUID() など H2 にしか通らない書き方を使わない。
      既存の H2 の結合テストとテストの部品（common/testsupport など）は変えない（FR5.4）。テストの説明文（テスト名・@DisplayName）は英語、テストデータのメールアドレスは example.com などの予約のドメインだけ。
      共通の注釈 @InternalDbPostgresIT（タグ internal-db-postgres と接続先の設定をまとめる）とコンテナの部品は、src/test/java の cherry.mastersmith.internaldb.testsupport に置く（契約 C4 の未決の点を、この単位が決める）。
    violation: "レビューと境界テスト"
    source: "FR5.1; ADR-005; team.md の Testing Posture・Code Style"

  - id: BR6.8
    statement: "CI は、手動の実行の入力 internal_db_postgres（真偽値、既定 false）が真のときだけ、PostgreSQL の結合テストを流す。"
    category: policy
    applies_to: [.github/workflows/ci.yml]
    trigger: "GitHub Actions の workflow_dispatch"
    logic: >
      IF 手動の実行で internal_db_postgres を真にした THEN 全件の ./gradlew verify の後に ./gradlew :backend:internalDbPostgresTest を流す。コンテナの実行環境が無ければ失敗（CI=true のため飛ばさない。BR6.4）。
      develop へのプッシュでは流さない。既存の入力 it_forks と同じ並びに足す。
    violation: "なし"
    source: "FR5.2; 契約 C4"

  # ---- BR7 構造とテストの置き場 ----
  - id: BR7.1
    statement: "PostgreSQL のときだけ通るコードは cherry.mastersmith.internaldb.platform に集め、普段の verify だけでパッケージの下限（行 80%・分岐 70%）を満たす。"
    category: constraint
    applies_to: [cherry.mastersmith.internaldb.platform, 単体テスト]
    trigger: "実装・テスト"
    logic: >
      IF PostgreSQL のときだけ通る処理（種類の判断・置き場の選択・接続のたびの文・失敗の整形・起動の入口）を書く
      THEN すべてこのパッケージに置き、PostgreSQL の結合テストに頼らず、単体テスト（コンテナ不要）と H2 の上の結合テストで通す。
      計測の除外を増やさない。ここで通らない分岐が出たら、除外せずテストを足す（team.md の Testing Posture）。
    violation: "./gradlew verify のパッケージごとの下限の判定が失敗する"
    source: "NFR6"

  - id: BR7.2
    statement: "業務処理の層と repository の層は内部DB の種類で分岐せず、既存の ArchUnit の境界テストは緩めない。新しい機能 internaldb の境界テストを足す。"
    category: constraint
    applies_to: [ArchUnit の境界テスト]
    trigger: "実装"
    logic: >
      IF 新しい機能 internaldb を作る THEN src/test/java の cherry/mastersmith/internaldb に InternalDbBoundaryArchitectureTest を置き、internaldb が他の機能に依存しないこと
      （依存されるのは Spring の設定の読み込みだけ。ほかの機能は internaldb を知らない）と、ほかの機能が internaldb.platform の型を使わないことを確かめる。
      既存の ArchitectureTest と他の機能の境界テストは書き換えず、足す。このIntent では既存の config の本体のソースに手を入れない（設定の部品は新しい機能 internaldb に置く）ため、config の境界テストは足さない。
    violation: "境界テストの失敗"
    source: "NFR9"

  - id: BR7.3
    statement: "普段の ./gradlew verify に入る単体テストと H2 の結合テストで、コンテナ無しに確かめる項目を次のとおり置く。"
    category: validation
    applies_to: [単体テスト, H2 の結合テスト]
    trigger: "普段の verify・verifyQuick"
    logic: >
      T1 InternalDbPlatformTest: 種類の判断（H2・PostgreSQL・MySQL・MariaDB・空・空白だけ・大文字の jdbc:POSTGRESQL:・前に空白・接頭辞だけ・jdbc: だけ）の表（パラメーターを使うテスト。BR1.1・BR1.3・BR1.4）。
      T2 起動の失敗（BR1.3・BR1.4）: 対応しない接続先と空の接続先で起動が止まり、ログに設定の名前・対応する種類が出て、URL・利用者・パスワードが出ない。
      T3 届かない接続先（BR5.1・FR3.10）: 閉じたポートの PostgreSQL の接続先で起動が止まり、ログに failureKind=CONNECTION_FAILED・SQLState 08001・例外の型の名前が出て、ホスト・ポート・利用者名・パスワードが出ない（コンテナを使わない）。パスワードが違う場合は T4 の単体で確かめる。
      T4 StartupFailureReportTest: 失敗の整形（BR5.2・BR5.3・BR5.7）。URL・利用者名・パスワード・ホストの文字列を入れた例外を渡しても出力に出ないこと、failureKind の分類の表、本物の Flyway の例外（H2 の内存の DB で壊した移行・変えた移行）から版の番号が取れること。
      T5 MigrationSetsConsistencyTest・H2MigrationFilesUnchangedTest（BR2.8）。
      T6 前の版の H2 のファイルでの起動（BR1.7・FR1.1）: H2 の置き場だけで作った H2 のファイル（前の版が作るものと同じ）に利用者を入れ、接続先を変えずに起動して、移行の検証が通り、その利用者でログインできる。
      T7 種類が H2 のときの設定（BR1.2・BR3.2・BR2.2）: 置き場が classpath:db/migration で、接続のたびの文が無い。
      T8 接続プールの設定の組み立て（BR3.1）: PostgreSQL のときだけ接続のたびの文が入る（接続を開かずに、設定の値だけで確かめる）。
      T9 InternalDbPostgresRuntimeCheckTest（BR6.4）: 判定の表。
      T10 InternalDbBoundaryArchitectureTest（BR7.2）と、application.yaml の値の確かめ（BR1.6・BR3.5）。
      T11 InternalDbSecretLeakIT（BR5.6）: TRACE を有効にして、URL・利用者・パスワードを入れた対応しない接続先で起動を試み、ログに値が出ない。
    violation: "テストの失敗"
    source: "FR5.5; FR1.1; FR1.3; FR3.10; NFR6"
```

## 決まりの要約（`rules.md` の YAML から作った人が読む表）

| ID | 区分 | 一言 | 源 |
|---|---|---|---|
| BR1.1 | validation | 種類は URL の先頭で決める（jdbc:h2: / jdbc:postgresql:、大文字小文字を区別、空白は取らない） | FR1.2 |
| BR1.2 | policy | 環境変数が無ければ今と同じ H2 のファイル | FR1.1 |
| BR1.3 | validation | 接続先が空（空白だけを含む）なら起動を止める（質問 Q2: A） | FR1.1・Q2 |
| BR1.4 | validation | 対応しない種類で起動を止める（ログに URL・利用者・パスワードを出さない） | FR1.3 |
| BR1.5 | policy | 種類の判断は起動時に1回・1か所。ほかは種類を見ない | FR1.2・NFR9 |
| BR1.6 | constraint | 接続の設定は環境変数だけ（名前と形は今のまま） | FR1.4 |
| BR1.7 | constraint | H2 のときは今と1つも変えない | FR1.1 |
| BR2.1 | constraint | H2 の移行は変えない（SHA-256 を固定） | FR2.2 |
| BR2.2 | policy | PostgreSQL の置き場は db/migration-postgresql（H2 の置き場の外） | FR2.1・FR2.2 |
| BR2.3 | constraint | 同じ版の番号・同じ説明のファイルを1組ずつ | FR2.3 |
| BR2.4 | constraint | 同じ表・名前付きの制約・索引の名前 | FR2.1 |
| BR2.5 | constraint | 外部キーは ON DELETE を付けない（NO ACTION） | FR3.2 |
| BR2.6 | constraint | 書き換えは BINARY(32)・BINARY LARGE OBJECT・V8 の生成列だけ | FR2.1・FR3.6 |
| BR2.7 | policy | 後の移行は両方に同じ版の番号で足す | FR2.3 |
| BR2.8 | validation | 突き合わせの単体テストを普段の verify に置く | FR2.3・FR5.5 |
| BR2.9 | policy | 移行の失敗で起動を止め、版の番号をログに出す | FR2.4 |
| BR2.10 | validation | 空の PostgreSQL への移行と validate の結合テスト | FR2.1・FR5.1 |
| BR2.11 | policy | 空の PostgreSQL でも初期管理者は1回だけ作る | FR2.5 |
| BR2.12 | constraint | flyway-database-postgresql を足す（BOM が版を管理） | NFR8 |
| BR3.1 | policy | PostgreSQL は接続のたびに lock_timeout を 2 秒に | FR3.3 |
| BR3.2 | constraint | H2 は接続のたびの文を足さない | FR1.1 |
| BR3.3 | constraint | 行の排他のヒント（3000ms）には手を入れない | FR3.3 |
| BR3.4 | policy | 起動時の移行も同じ接続の設定のまま通る | FR3.3・NFR4 |
| BR3.5 | constraint | ドライバーのログは OFF のまま | FR3.10 |
| BR3.6 | policy | ヘルスチェックは PostgreSQL でも UP・DOWN を返す | FR3.9 |
| BR4.1 | constraint | 大きなバイナリは BYTEA ＋ LONG32VARBINARY（@Lob を外す） | FR3.6 |
| BR4.2 | constraint | 固定長のハッシュは BYTEA ＋ 今のまま | FR3.6 |
| BR4.3 | constraint | 4列とも1つの写し方で、種類で分岐しない | FR3.6 |
| BR4.4 | policy | validate は U1、往復は U2・U3 が確かめる | FR3.6 |
| BR4.5 | constraint | 時刻の列は同じ型（timestamptz） | FR3.7 |
| BR5.1 | policy | 起動時の失敗は起動の入口で受け、安全な項目だけをログに出す（Q1: A） | FR3.10・FR1.3・FR2.4・Q1 |
| BR5.2 | constraint | 接続先の値・利用者名・パスワード・ホスト・ポート・例外の文を出さない | FR3.10・NFR2 |
| BR5.3 | calculation | 移行の版の番号の取り出し方 | FR2.4 |
| BR5.4 | policy | 動作中の接続の失敗は今のまま（既知の制約） | FR3.10・Q1 |
| BR5.5 | policy | ERROR・構造化ログのキーと値 | FR3.10 |
| BR5.6 | constraint | 接続先の値を受ける部品は TraceAspect の対象の外（internaldb.platform） | FR3.10・NFR2 |
| BR5.7 | calculation | failureKind の分類（SQLState の先頭2文字・例外の種類） | FR3.10・FR2.4 |
| BR6.1 | policy | 専用のタスク :backend:internalDbPostgresTest を明示したときだけ流す | FR5.2 |
| BR6.2 | policy | integrationTest はタグ付きのクラスを流さない | FR5.2 |
| BR6.3 | validation | -PitTests がタグ付きだけに当たったら専用のタスクを案内して失敗 | FR5.2 |
| BR6.4 | policy | コンテナが無いとき手元は警告して飛ばし、CI は失敗 | FR5.3 |
| BR6.5 | constraint | コンテナは JVM ごとに1つ。イメージは TargetDbImages.postgres() | FR5.1 |
| BR6.6 | constraint | 空のデータベースが要るテストは専用のデータベースを作って消す | FR5.1 |
| BR6.7 | constraint | テストの部品は H2 に頼らない。注釈とコンテナの置き場を決める | FR5.1 |
| BR6.8 | policy | CI は手動の入力 internal_db_postgres が真のときだけ流す | FR5.2 |
| BR7.1 | constraint | PostgreSQL のときだけ通るコードは internaldb.platform に集める | NFR6 |
| BR7.2 | constraint | 種類で分岐しない。境界テスト InternalDbBoundaryArchitectureTest を足す | NFR9 |
| BR7.3 | validation | 普段の verify に入る単体テストと H2 の結合テスト（T1〜T11） | FR5.5・FR1.1・FR1.3・FR3.10・NFR6 |

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md`（FR1〜FR3・FR5・NFR2・NFR4・NFR6・NFR8・NFR9）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/domain-design/decisions.md`（ADR-001〜ADR-006）・`components.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/contract-design/contract-summary.md`（C1〜C4・C6）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/functional-design/functional-design-questions.md`（Q1: A・Q2: A と、捨ての試しの結果）
- `aidlc/spaces/default/knowledge/aidlc-shared/study-internal-db-postgresql.md`
- `aidlc/spaces/default/knowledge/aidlc-shared/checklist-auth-audit.md`（初期管理者・秘密情報の漏えい）・`checklist-dsl.md`（DSL の本文の上限と列の上限の突き合わせ）・`checklist-timezone.md`（時刻の列の型）
- `aidlc/spaces/default/memory/team.md`（Way of Working・Testing Posture・Code Style・Deployment）・`aidlc/spaces/default/memory/project.md`（Code Style・Forbidden・Mandated）
- コードで確かめたこと（2026-11-17）: `backend/src/main/resources/db/migration/` の V1〜V14、`backend/src/main/resources/application.yaml`（`spring.datasource`・`spring.flyway`・`logging.level`）、`DslPreviewRecord`・`DslAppliedRevisionRecord`・`RefreshToken`・`Invitation` の列の写し方、`LoginService.createRow`、`TargetDbImages`・`ContainerRuntimeCheck`、`backend/build.gradle.kts` の `integrationTest`
