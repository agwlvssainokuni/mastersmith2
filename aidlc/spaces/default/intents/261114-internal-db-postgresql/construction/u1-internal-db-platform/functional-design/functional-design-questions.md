# 機能設計の質問: u1-internal-db-platform（Intent 261114-internal-db-postgresql）

この単位（U1: 内部DB の種類の判断・PostgreSQL 用の移行・接続の設定・結合テストの土台）の機能設計で、依頼者に確かめる点だけを尋ねます。上流（要件定義・Domain Design・Contract Design・Delivery Planning・`team.md`・`project.md`）で決まっている点は質問にしていません。

## 決まっていること

- 接続先の URL の先頭で種類を決める: `jdbc:h2:` は H2、`jdbc:postgresql:` は PostgreSQL、それ以外は起動を止める（要件 FR1.2・FR1.3、ADR-001）。既定の接続先は今の H2（ファイル）のまま（FR1.1）。
- PostgreSQL 用の移行は別の置き場に一式置き、H2 の移行は1文字も変えない（FR2.2、ADR-002）。外部キーは NO ACTION（ADR-004）。
- PostgreSQL の接続ごとの `lock_timeout` は 2 秒（FR3.3、契約 C1）。
- 結合テストの土台の名前は契約 C4 で決まっている: Gradle のタスク `:backend:internalDbPostgresTest`、JUnit のタグ `internal-db-postgres`、共通の注釈 `@InternalDbPostgresIT`、CI の手動の入力 `internal_db_postgres`。
- PostgreSQL の内部DB の結合テストで使うイメージは `TargetDbImages.postgres()`（契約 C6）。

## 捨ての試しの結果（質問ではありません）

リポジトリの外で、PostgreSQL 18.6（`TargetDbImages` と同じダイジェスト）・Hibernate 7.4.13・Flyway 12.4.0・今のビルド済みの WAR を使って確かめた結果です（記録は設計の成果物と `aidlc/spaces/default/knowledge/aidlc-shared/study-internal-db-postgresql.md` に写します）。

- バイナリの列（契約 C3）: 今の `@Lob byte[]` は PostgreSQL の `bytea` の列に対して `ddl-auto: validate` で失敗する（`oid` を期待）。`@JdbcTypeCode(SqlTypes.LONG32VARBINARY)`（`@Lob` なし）なら、H2 の `BINARY LARGE OBJECT` と PostgreSQL の `bytea` の両方で `validate` が通り、10MB ちょうどの往復も両方で一致した。トークンのハッシュ（今の `@JdbcTypeCode(SqlTypes.BINARY)`・長さ 32）は、エンティティを変えずに PostgreSQL の `bytea` で `validate` が通り、32 バイトの往復も一致した。
- H2 の移行 V1〜V14 をそのまま PostgreSQL に流すと、通らないのは V3（`BINARY(32)`）・V5（`BINARY LARGE OBJECT`）・V8（`BINARY(32)` と STORED の無い生成列）の3本だけ。書き換え後の一式で、名前付きの制約 24 件と `ix_*` の索引の名前が H2 と同じ集まりになった。名前の無い主キー 9 件は、DB が自動で別の名前を付ける（H2 は `CONSTRAINT_xx`、PostgreSQL は `<表名>_pkey`）。
- V8 の「招待中のメールアドレスの一意」は、PostgreSQL では `GENERATED ALWAYS AS (...) STORED` の生成列 ＋ 同じ名前の一意の制約 `uk_invitations_pending_email` で、H2 と同じ列・同じ名前のまま作れる（重なると 23505 で制約の名前が取れる）。
- 大文字と小文字: `uk_users_email` は PostgreSQL でも大文字と小文字を区別する（`A@example.com` と `a@example.com` は別の値として入る）。H2 と同じ。名前の一意（`name_key`）はアプリ側でそろえているため DB に依らない。
- 主キーの採番: 値を指定して入れても採番は進まない。ただし内部DB の移行で値を指定して IDENTITY の表に入れる行は無い（V3 の INSERT は IDENTITY ではない `login_attempt_states` だけ）ので、今は問題にならない。
- Flyway: PostgreSQL 用の部品 `org.flywaydb:flyway-database-postgresql`（Apache License 2.0、依存は `flyway-core` だけ）が必要。Spring Boot 4.1.2 の BOM が管理する版（12.4.0）がある。接続ごとの `lock_timeout` を 2 秒にしたまま、空の PostgreSQL への V1〜V14 の移行は 365 ms で全件通った。H2 用の移行をそのまま PostgreSQL に流して失敗させると、例外の文に失敗した移行のファイル名（`V3__u2_authentication.sql`）と SQLState が入る。
- Flyway の移行の置き場は再帰して探すため、PostgreSQL 用の一式を `classpath:db/migration` の下に置くと H2 の一式に混ざる。H2 の置き場の外（兄弟の置き場）に置く必要がある。
- 書き直し（U2）の見込み: `LoginAttemptStateRepository` の `MERGE ... USING (VALUES ...)`・`RefreshTokenRepository`・`InvitationRepository` の `FETCH FIRST` つきの削除は、PostgreSQL でそのまま通った（契約 C5 の見込みどおり、U2 の範囲は増えない）。`DslAppliedRevisionRepository` の `CAST(? AS ...)` を含む `INSERT ... SELECT` も PostgreSQL でそのまま通った（U2 の書き直しの対象から外れる見込み。確かめは U2 の設計で行う）。`FINAL TABLE` の代わりの `UPDATE ... RETURNING` と、`MERGE ... KEY` の代わりの標準の `MERGE ... USING` は PostgreSQL で通った（H2 での確かめは U2 の設計で行う）。標準の `MERGE` は、同時に同じ鍵を入れると、後の側が先の側の確定まで待ってから 23505 で失敗する（PostgreSQL）。ログインの状態の行を作る所（`LoginService.createRow`）は `DataIntegrityViolationException` を受けて進む作りなので影響しない。
- アプリ全体の `ddl-auto: validate`: 書き換え後の PostgreSQL 用の一式の表に今のアプリを向け、`@Lob` の2列だけ期待どおり `oid` にして流すと、ほかのすべてのエンティティの照合が通った（`@Lob` 以外に不一致は無い）。

## Q1. [セキュリティ] 内部DB への接続に失敗したとき、ログにどこまで出してよいか（要件 FR3.10・FR1.3）

要件は「失敗の原因（例外の型・SQLState）をログに出す」と「接続先の URL・利用者・パスワードをログに出さない」の両方を求めています。今のアプリを PostgreSQL の接続先で起動して失敗させると、Spring が出すログの例外の文に、次が入ることが分かりました（パスワードと資格情報つきの URL は出ません）。

- 接続できないとき（閉じたポート）: 接続先のホストとポート（`127.0.0.1:1 への接続が拒絶されました`）と SQLState `08001`。
- パスワードが違うとき: サーバーの文として、利用者名（`password authentication failed for user "…"`）と SQLState `28P01`。

どこまで対処するかで、作りの大きさが変わります。

**推奨: A。** 理由: 失敗の原因を探すのは、ほとんど起動のときです。起動の入口（Flyway の移行を呼ぶ所）で例外を受け、ログには例外の型の名前・SQLState・失敗した移行の版の番号だけを出す形にすれば、起動時のホスト・ポート・利用者名の漏れを、DataSource を包むことなく止められます（C のように DataSource を包むと、接続プールの計測と JMX に手を入れる危険があります）。動作中の失敗は、ドライバーのログを OFF にしている今の形のままで、README の既知の制約に書きます。

A. 起動の入口で包む: 起動時の接続の失敗と移行の失敗は、こちらで例外の型の名前・SQLState・失敗した移行の版の番号だけを出す形に置き換える（ホスト・ポート・利用者名は出さない）。動作中の接続の失敗は今のまま（Spring・接続プールのログ。ドライバーのログは OFF のまま）。要件 FR3.10 の「動作中」の部分との差は、決定として記録して README に既知の制約として書く
B. 何も置き換えない: ログに出るのは今の Spring・ドライバーの文のまま（ホスト・ポート・利用者名が出うる）。パスワードと資格情報つきの URL が出ないことだけをテストで確かめ、ホスト・ポート・利用者名は「接続情報には当たらない」と読み替えて README に書く
C. 起動時も動作中も安全にする: 内部DB の接続を出す DataSource を包んで、接続の失敗の例外の文をどの経路でも差し替える（接続プールの計測・JMX・`docker/hikari-pool.sh` に影響しないかの確かめが増える）
X. Other (please specify)

[Answer]: A

## Q2. [運用] 内部DB の接続先が「設定されているが空」のときの動き（要件 FR1.1 の周辺）

`.env` に `MASTERSMITH_DB_URL=`（値なし）と書くと、今のアプリは **エラーにならず、メモリだけの H2（アプリを止めると消える）で静かに起動します**（今のビルドで実測しました。`.env.example` には `# MASTERSMITH_DB_URL=` の行があり、コメントを外しただけで空のまま有効になる間違いが起こりえます）。種類を接続先の URL から決める今回の作りでは、空のときの扱いを決める必要があります。何も設定しない（環境変数が無い）ときは、これまでどおり H2 のファイルで動きます。

**推奨: A。** 理由: 空の値でメモリだけの DB に静かに入ると、入れた利用者・権限・DSL が再起動で消えます。今回は PostgreSQL を選べるようになり、接続先を書き間違える機会が増えるため、空は起動を止めて気づける形にするのが安全です。

A. 空（空白だけを含む）のときは起動を止める: 設定の名前（`MASTERSMITH_DB_URL`）と「空のときは変数ごと消す」ことをログに出す。接続先は出ない（空だから）
B. 空は「未設定」と同じに読み、H2 のファイルの既定の接続先で動かす（メモリだけの DB にはしない）
C. 今のまま（メモリだけの H2 で静かに起動する）
X. Other (please specify)

[Answer]: A

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md`（FR1〜FR5・NFR1〜NFR9）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/domain-design/decisions.md`（ADR-001〜ADR-006）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/contract-design/contract-summary.md`（C1〜C6）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/delivery-planning/delivery-planning-questions.md`（Q3: A）
- `aidlc/spaces/default/knowledge/aidlc-shared/study-internal-db-postgresql.md`
- 実測（2026-11-17、リポジトリの外の捨ての試し。コードは残していない）
