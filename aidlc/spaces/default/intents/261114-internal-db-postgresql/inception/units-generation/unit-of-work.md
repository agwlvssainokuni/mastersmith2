# 作る単位（Unit of Work。Intent 261114-internal-db-postgresql）

Domain Design の部品（`inception/domain-design/components.md`）を、依頼者の答え（Q1: A の4単位、Q2: A の結合テストの土台は u1）に沿って4つの単位にまとめる。この文書は単位の中身と境界だけを書き、作る順は Delivery Planning が決める。

## 単位の一覧

| Unit ID | Directory | 種類 | 配備の形 | 大きさ | 持つ部品 |
|---|---|---|---|---|---|
| U1 | u1-internal-db-platform | service | 埋め込み（同じ WAR とテストの設定） | L | InternalDbPlatform・InternalDbMigration（PostgreSQL 用の一式）・InternalDbPostgresTestSuite の土台 |
| U2 | u2-portable-sql | service | 埋め込み（同じ WAR） | M | UserPersistence・RolePersistence・DslManagePersistence |
| U3 | u3-postgres-behaviour-tests | （種類なし） | テストだけ（WAR に入らない） | M | InternalDbPostgresTestSuite の振る舞いの確かめ（AuthPersistence・InvitationPersistence・PersistenceFailureClassification の確かめを含む） |
| U4 | u4-local-runtime-docs | packaging | リポジトリの設定と文書（WAR に入らない） | S | LocalContainerRuntime・文書 |

U3 はテストだけの単位で、`service`・`spec`・`ui`・`packaging`・`library` のどれにも当たらないため種類を付けない（設計の段の成果物は全部の組になる。当たらない成果物は、その段で当たらない理由を書いて済ませる）。

## U1: u1-internal-db-platform

- **説明**: 内部DB の種類を接続先から判断し、PostgreSQL のときに PostgreSQL 用の移行で表を作って起動できるようにする。PostgreSQL の結合テストの土台（明示の指定・コンテナの起動）を作り、移行と起動を実際の PostgreSQL で確かめる。
- **責務**:
  - 接続先（`MASTERSMITH_DB_URL`）から種類（H2・PostgreSQL・対応しない種類）を起動時に1回だけ判断し、対応しない種類で起動を止める（FR1.1〜FR1.3）。止めるときのログに URL・利用者・パスワードを出さない（NFR2）。
  - 種類に応じて Flyway の移行の置き場を選ぶ。PostgreSQL 用の移行 V1〜V14 を、H2 と同じ版の番号・同じ表・同じ名前の制約と索引で作る（外部キーは NO ACTION、ADR-002・ADR-004）。H2 の移行は変えない（FR2.2）。
  - Flyway の PostgreSQL の部品を依存に足す（ライセンス・lockfile・OSV-Scanner・依存の木の確かめ、NFR8）。
  - PostgreSQL のときに接続ごとの待ちの上限（`lock_timeout` 2 秒）を設定し、起動時の移行がその上限で切れない形にする（FR3.3 の接続の設定の部分、NFR4）。
  - 内部DB の PostgreSQL への接続の失敗の原因がログに出るようにする（対象DB のドライバーのログの抑えが隠さない形、FR3.10）。
  - 普段の `verify` で流れる単体テスト: 種類の判断、対応しない種類での起動の失敗、届かない接続先でのログ、H2 と PostgreSQL の移行の版の番号と制約・索引の名前の突き合わせ（ADR-002、FR5.5）。
  - PostgreSQL の結合テストの土台: Gradle の明示の指定（名前は機能設計で決める）、Testcontainers で対象DB の PostgreSQL と同じ版とダイジェストのコンテナを起動、コンテナの実行環境が無いときの警告と飛ばし（明示して CI で流したときは飛ばさない）、CI の手動の実行の入力（FR5.2・FR5.3）。
  - 土台の上の最初の結合テスト: 空の PostgreSQL への移行の全件（FR2.1）、移行の失敗で起動が止まり版の番号がログに出る（FR2.4）、初期管理者を1回だけ作る（FR2.5）、ヘルスチェックの UP と DOWN（FR3.9）、起動時のエンティティと表の照合（`ddl-auto: validate`）。
- **境界**: 業務処理の層と repository の層は変えない（種類で分岐しない、ADR-001・NFR9）。H2 にしか通らない SQL の書き直しは U2。振る舞いの結合テストは U3。compose と文書は U4。
- **実装の注意**:
  - Spring Boot の自動設定（DataSource・Flyway）との順序は機能設計で決める（ADR-001 の続く判断）。
  - 起動時の移行と `lock_timeout` の関係は NFR 設計で決める（NFR4）。
  - V8 の生成列の一意、`@Lob byte[]` と `bytea`（K-149）は、PostgreSQL 用の移行の型に関わるため、機能設計の前に捨ての試しで確かめる（ADR-006）。`bytea` のエンティティの写し方そのものは U2 が持つが、列の型は U1 の移行が持つ。両方が食い違わないことは U1 の `ddl-auto: validate` の確かめで拾う。
  - 既存の `config` の部品に手を入れるため、その機能の境界テストが無ければ足す（`team.md` の Code Style）。

## U2: u2-portable-sql

- **説明**: H2 にしか通らない SQL を、H2 と PostgreSQL の両方に通る書き方に書き直し、大きなバイナリの写し方を両方で通る形にする。H2 での結果は変えない。
- **責務**:
  - `UserRepository.savePreferences` の `FINAL TABLE (UPDATE ...)` を、更新と読み直しを同じトランザクションで分ける形などに書き直す（FR3.5）。
  - `RoleStore` と `DslPreviewRepository` の `MERGE INTO ... KEY` を、標準の `MERGE INTO ... USING` などに書き直す（FR3.5）。
  - `DslAppliedRevisionRepository` の `CAST(? AS ...)` を含む `INSERT ... SELECT` を書き直す（FR3.5）。
  - DSL の本文（最大 10MB）とハッシュの写し方を、H2 と PostgreSQL の両方で `ddl-auto: validate` が通る形にする（FR3.6）。写し方の具体は機能設計で、捨ての試しで確かめてから決める。
  - 利用者の検索（大文字と小文字を区別しない部分一致、エスケープ付き）が両方で同じ結果になる書き方であることを確かめ、要れば直す（FR3.8）。
  - 書き直した SQL を、普段の `verify` の H2 の結合テストの全件で確かめる（結果を変えない。FR5.4）。足りない場合は H2 の結合テストを足す（減らさない）。
- **境界**: DB の種類で分岐しない（ADR-003）。種類の判断と移行は U1。PostgreSQL での確かめは U3。
- **実装の注意**:
  - 同時の実行での `MERGE` の振る舞い（一意の違反の出方）は、H2 と PostgreSQL で違うおそれがあり、機能設計の前に捨ての試しで確かめる（ADR-003・ADR-006）。
  - user・role・dslmanage の本体のソースに手を入れるため、それぞれの機能の境界テストが無ければ足す。
  - `TraceAspect` の対象の層で、個人に関する値を文字列にしない今の形を崩さない（`project.md` の Code Style）。
  - 単位の中身は H2 だけで確かめられるため、U1 の作業を含まない状態でも通る。

## U3: u3-postgres-behaviour-tests

- **説明**: 内部DB を PostgreSQL で動かす経路の振る舞いを、U1 の土台の上の結合テストで確かめる。H2 の結合テストとは別のバリエーションで、明示の指定のときだけ流れる（ADR-005）。
- **責務**:
  - 内部DB を使う機能ごとの代表の API の流れ1本ずつ（ログイン・トークン・パスワードの変更・利用者の管理・招待・ロールと権限・作業ロール・グループ・メニュー・DSL の読み込みと適用・表示の設定・監査）が、H2 と同じ状態コード・`code`・応答の項目を返す（FR3.1）。
  - 違反の区分（一意・親の無い子・参照のある行の削除、大文字と小文字の扱い）が H2 と同じ応答になる（FR3.2）。
  - 待ちの上限: 書き込みの待ち（接続の 2 秒）と行の排他の読み取り（ヒントの 3 秒台）の両方の経路、行の排他の後に接続の上限が 2 秒へ戻ること、行き詰まりの区分（FR3.3）。ログインの状態の SKIP LOCKED の経路も、PostgreSQL で期待どおり動くことを確かめる（Domain Design のレビューの持ち越し）。
  - 失敗の後のトランザクション（書きかけを残さない、失敗の監査は別のトランザクション）（FR3.4）。
  - 書き直した SQL（U2）の PostgreSQL での結果（FR3.5）、10MB ちょうどの DSL の本文と 32 バイトのハッシュ（FR3.6）、時刻の往復と時刻で決まる判定（FR3.7）、検索のエスケープ（FR3.8）。
  - AuthPersistence・InvitationPersistence の今のネイティブの SQL が PostgreSQL でも通ること（見込みが外れたら、ADR-003 と同じ形で直す。直しの持ち主は機能設計で決める）。
  - H2 の結合テストとテストの部品は変えない（FR5.4）。テストの部品は H2 に頼る書き方を使わない。
- **境界**: 本体のソース（`src/main`）は変えない前提。見込みが外れて本体の直しが要るときは、機能設計で U2 か U3 のどちらで直すかを決め、依存の図を見直す。
- **実装の注意**:
  - テストの手伝いは `src/test/java` の `<機能>/testsupport` に置く（`team.md` の Code Style）。
  - テストの説明文は英語、テストデータのメールアドレスは予約のドメインだけ（`team.md` の Testing Posture）。
  - 明示して流したときの時間を測って記録する（NFR5 は普段の検査の時間で、この単位の時間は含まない）。

## U4: u4-local-runtime-docs

- **説明**: 手元の compose で内部DB 用の PostgreSQL を選べるようにし、PostgreSQL の内部DB の使い方と運用の手順を文書にする。
- **責務**:
  - compose に内部DB 用の PostgreSQL を、見本の対象DB とは別のサービス・別のボリューム・別の資格情報で置き、profile で選んだときだけ起動する（FR4.1）。既定の内部DB は H2 のまま（FR4.3）。
  - イメージを対象DB の PostgreSQL と同じ版とダイジェストに固定し、内部DB 用の固定先（テストの定数・compose）を `.github/dependabot.yml` の固定先の一覧に足す（FR4.2）。
  - PostgreSQL の内部DB のバックアップと戻し（`pg_dump` など）の手順を書き、H2 だけの運用（`DEFRAG_ALWAYS`・`docker/hikari-pool.sh` の compact・`perf/h2-shell.sh`・ボリュームの `tar` の戻し・`-Dh2.compactThreads=1`）を H2 のときだけのものと明記する（FR4.4）。
  - README と `docs/internal-db.md` に、接続先の設定・compose の選び方・対応する版（確かめた 18 系だけ）を書く（FR4.5）。
- **境界**: アプリの本体のソースは変えない。`.env.example` は値を空のまま項目だけ足す（`project.md` の Forbidden）。
- **実装の注意**:
  - 文書の手順は、U1 で決まる接続先の設定の決まり（対応する種類・環境変数の名前）に合わせる。
  - バックアップと戻しの手順は Deployment Execution で1回練習する（`team.md` の Deployment）。

## 単位にまたがる約束

- どの単位も、統合の前に `./gradlew verifyQuick`（全件の場面では `./gradlew verify`）を通す。PostgreSQL の結合テストは明示の指定のときだけ流す。
- 内部DB の接続情報をログ・監査ログ・エラー応答に出さない（`project.md` の Forbidden）。
- バックエンドの全体とすべてのパッケージのカバレッジの下限（行 80%・分岐 70%）を、PostgreSQL の結合テストを流さない普段の `verify` だけで満たす（NFR6）。PostgreSQL のときだけ通るコードは U1 に集まるため、U1 の単体テストで満たす。

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/domain-design/components.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/domain-design/decisions.md`（ADR-001〜ADR-006）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md`（FR1〜FR5・NFR1〜NFR9）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/units-generation/units-generation-questions.md`（Q1: A・Q2: A）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/domain-design/reviews/review-02.md`（SKIP LOCKED の経路の持ち越し）
- `aidlc/spaces/default/memory/team.md`（Way of Working・Testing Posture・Code Style・Deployment）
- `aidlc/spaces/default/knowledge/aidlc-shared/study-internal-db-postgresql.md`
