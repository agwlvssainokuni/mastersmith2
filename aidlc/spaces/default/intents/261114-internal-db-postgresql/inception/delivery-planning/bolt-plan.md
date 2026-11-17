# Bolt の計画 — internal-db-postgresql

Bolt は、1つ以上の作る単位（Unit of Work）を、設計から統合まで通して動くものにする1回の作り込みのこと。この文書は、Construction で作る Bolt の順と、それぞれの終わりの条件（Definition of Done）と、確かめたいこと（confidence hypothesis: その Bolt を出すと何が分かるか）を書く。

出典: 要件 `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md`（FR1〜FR5・NFR1〜NFR9）、単位 `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/units-generation/unit-of-work.md`・`unit-of-work-dependency.md`・`unit-of-work-story-map.md`、契約 `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/contract-design/contract-summary.md`（C1〜C7）、この段の答え `delivery-planning-questions.md`（Q1〜Q5: すべて A）、チームの進め方 `aidlc/spaces/default/memory/team.md`。

## 前提

- 作業は依頼者1名と AI で、Bolt は1つずつ順に作る。Bolt ごとに `develop` から短命のブランチ（`feature/261114-internal-db-postgresql-b<n>`）を作り、squash で `develop` に戻す。worktree は使わない。
- 最初の Bolt だけを単独で承認する手順（walking skeleton: 最初に全体を一通り動かす薄い作り込み）は行わない（`team.md` の Walking Skeleton。アプリの骨格は既にある）。walking skeleton の印を付けた Bolt は無い。
- Construction は単位ごと（unit-major、Q4: A）。1つの単位を設計の段からコード生成まで通し、確認の場で確かめてから次の単位へ進む。U1 は大きさ L のため、設計の段は1つの単位として通し、コード生成の計画で Bolt の中の手順に分ける（`project.md` の学び）。
- 設計の順: U1 の機能設計が、U2・U3 の機能設計より前（契約 C3・C4、Units Generation のレビュー R-03）。単位ごとに U1 を最初に置くため、この順は守られる。
- 捨ての試し（リポジトリの外のコードで DB の振る舞いを確かめる）は、U1 用と U2 用の2組をまとめて、U1 の機能設計の前に行う（Q3: A）。結果は U1 の機能設計の成果物と `aidlc/spaces/default/knowledge/aidlc-shared/study-internal-db-postgresql.md` に写す。
- 統合の前に `./gradlew verifyQuick`（変えた所に関わる結合テストは `-PitTests` で絞る）を通す。全件の `./gradlew verify` は Build and Test とリリースの前に流す（`team.md`）。
- 内部DB の PostgreSQL の結合テスト（`./gradlew :backend:internalDbPostgresTest`、契約 C4）は、PostgreSQL の経路を変える Bolt（B1・B2・B3）の終わりの条件に入れて明示して流し、結果と時間を記録する（Q5: A）。単位ごとの確認の場のコマンドは今のまま（colima の環境変数付きの `./gradlew verify`）。
- E2E は画面と認証・権限・業務データの本体を変えないため Bolt ごとには流さず、Build and Test で全体を1回流す。

## Bolt の一覧

| Bolt | 単位 | 中身 | 統合の形 |
|---|---|---|---|
| B1 | U1 u1-internal-db-platform | 種類の判断・PostgreSQL 用の移行 V1〜V14・バイナリの列の型・PostgreSQL の接続の設定・テストの土台・最初の結合テスト | squash |
| B2 | U2 u2-portable-sql | H2 にしか通らない SQL の書き直し・バイナリの写し方・（捨ての試しで外れたら）Auth・Invitation の SQL の直し | squash |
| B3 | U3 u3-postgres-behaviour-tests | PostgreSQL での機能ごとの代表の流れと、違反の区分・待ち・トランザクション・時刻・検索の結合テスト | squash |
| B4 | U4 u4-local-runtime-docs | compose の内部DB 用の PostgreSQL・固定先の一覧と突き合わせのテスト・バックアップと戻しの手順・README と文書 | squash |

## B1 U1 内部DB の土台

- **単位**: U1 `u1-internal-db-platform`
- **中身**: 接続先の URL の先頭による種類の判断と、対応しない種類での起動の停止（契約 C1、FR1）。PostgreSQL 用の移行 V1〜V14（同じ版の番号・同じ名前の制約と索引、外部キーは NO ACTION、契約 C2、FR2）と、H2 との突き合わせの単体テスト。バイナリの列の型（契約 C3。U1 の機能設計で決めた組）。Flyway の PostgreSQL の部品の追加。接続ごとの `lock_timeout` 2 秒と、接続の失敗の原因のログ（FR3.3・FR3.10）。テストの土台（`:backend:internalDbPostgresTest`・タグ `internal-db-postgres`・`@InternalDbPostgresIT`・コンテナ1つの使い回し・CI の手動の入力 `internal_db_postgres`、契約 C4、FR5.2・FR5.3）。最初の結合テスト（移行の全件・移行の失敗・初期管理者・ヘルスチェック・`ddl-auto: validate`）。
- **Definition of Done**: FR1.1〜FR1.4・FR2.1〜FR2.5・FR3.9・FR3.10・FR5.2・FR5.3・FR5.5 が、普段の `verify` の単体テストと `:backend:internalDbPostgresTest` の結合テストで通る。`integrationTest` がタグ付きのクラスを流さず、`-PitTests` がタグ付きのクラスだけに当たったときは専用のタスクを促す失敗になる。新しい依存のライセンス・lockfile・OSV-Scanner・依存の木を確かめた記録がある（NFR8）。全体とすべてのパッケージのカバレッジの下限を、PostgreSQL の結合テストを流さずに満たす（NFR6）。`./gradlew verifyQuick` が通り、`:backend:internalDbPostgresTest` の結果と時間を記録する。
- **確かめたいこと**: H2 の移行を変えずに、同じ版の番号と名前の PostgreSQL 用の移行で、エンティティとの照合（`ddl-auto: validate`）が両方で通るか。起動時の移行が `lock_timeout` 2 秒で切れないか。種類で選ぶ移行の置き場が Spring Boot の自動設定と食い違わないか。
- **見せるもの**: `:backend:internalDbPostgresTest` の結果（移行の全件と起動）と、H2 と PostgreSQL の移行の突き合わせの単体テストの結果。

## B2 U2 両方に通る SQL

- **単位**: U2 `u2-portable-sql`
- **中身**: `UserRepository.savePreferences` の `FINAL TABLE (UPDATE ...)`、`RoleStore`・`DslPreviewRepository` の `MERGE INTO ... KEY`、`DslAppliedRevisionRepository` の `CAST(? AS ...)` を含む `INSERT ... SELECT` を、DB の種類で分岐しない書き方に直す（FR3.5、ADR-003）。バイナリの写し方を U1 の組のとおりに直す（契約 C3、FR3.6）。捨ての試しで外れた Auth・Invitation の SQL があれば、ここで直す（契約 C5）。利用者の検索の大文字と小文字・エスケープの確かめ（FR3.8）。メソッドの形は変えない（契約 C5）。
- **Definition of Done**: H2 の結合テストの全件が結果を変えずに通る（FR5.4。足りなければ H2 の結合テストを足し、減らさない）。user・role・dslmanage の境界テストが無ければ足す。`./gradlew verifyQuick`（`-PitTests` で関わる結合テストを絞る）が通り、`:backend:internalDbPostgresTest` も通る（この時点では U1 の結合テストだけ。PostgreSQL での起動とエンティティの照合が、写し方の変更の後も通ることを確かめる）。
- **確かめたいこと**: `MERGE` の書き換えが、同時の実行での一意の違反の出方を H2 で変えないか。バイナリの写し方の変更で、H2 の 10MB の本文とハッシュの往復が変わらないか。
- **見せるもの**: H2 の結合テストの全件の結果と、書き直した SQL の差。

## B3 U3 PostgreSQL での振る舞いの確かめ

- **単位**: U3 `u3-postgres-behaviour-tests`
- **中身**: U1 の土台の上の結合テスト。機能ごとの代表の API の流れ（FR3.1）、違反の区分（FR3.2）、待ちの上限と行き詰まり・SKIP LOCKED の経路（FR3.3）、失敗の後のトランザクション（FR3.4）、書き直した SQL の結果（FR3.5）、10MB の本文と 32 バイトのハッシュ（FR3.6）、時刻の往復（FR3.7）、検索のエスケープ（FR3.8）、Auth・Invitation の SQL。本体のソース（`src/main`）は変えない。
- **Definition of Done**: FR3.1〜FR3.8 が `:backend:internalDbPostgresTest` で通り、結果と時間を記録する。H2 の結合テストとテストの部品を変えない（FR5.4）。本体の直しが要ると分かったときは、U2 の小さな直しとして別に統合してから、この Bolt を統合する。`./gradlew verifyQuick` が通る。
- **確かめたいこと**: 内部DB を PostgreSQL にしても、今の API が H2 と同じ状態コード・`code`・応答の項目を返すか。待ちの上限（接続の 2 秒とヒントの 3 秒台）が両方の経路で期待どおり切れるか。
- **見せるもの**: `:backend:internalDbPostgresTest` の全件の結果と時間。

## B4 U4 手元の実行と文書

- **単位**: U4 `u4-local-runtime-docs`
- **中身**: compose に内部DB 用の PostgreSQL のサービス（別のボリューム・別の資格情報・profile で選んだときだけ起動、FR4.1）。イメージを `TargetDbImages.postgres()` と同じ版とダイジェストに固定し、`.github/dependabot.yml` の固定先の一覧に足す（契約 C6、FR4.2）。compose と `TargetDbImages` の突き合わせの単体テスト（契約 C6 の承認の場の決定）。`.env.example` に値を空のまま項目を足す。バックアップと戻し（`pg_dump` など）の手順と、H2 だけの運用の明記（FR4.4）。README と `docs/internal-db.md` の接続先の設定・compose の選び方・対応する版（FR4.5）。
- **Definition of Done**: FR4.1〜FR4.5 が満たされる。既定の内部DB は H2 のまま（FR4.3）。突き合わせの単体テストが普段の `verify` で流れて通る。`docs/internal-db.md` のイメージの版が固定先と揃う。`./gradlew verifyQuick` が通る。compose で内部DB を PostgreSQL にしたアプリの起動確認とバックアップと戻しの練習は Deployment Execution に回す（`team.md` の Deployment）。
- **確かめたいこと**: 文書の手順だけで、内部DB を PostgreSQL にした手元の起動ができるか（Deployment Execution で確かめる）。
- **見せるもの**: compose の profile と、文書の手順。

## トポロジー順の確かめ

| Bolt | 単位 | 頼る単位 | 頼る単位が先にあるか |
|---|---|---|---|
| B1 | U1 | なし | はい |
| B2 | U2 | なし（設計の順で U1 の後） | はい（U1 は B1） |
| B3 | U3 | U1・U2 | はい（B1・B2） |
| B4 | U4 | U1 | はい（B1） |

並びは依存の図（`unit-of-work-dependency.md`）のトポロジー順の1つで、外れていない。

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/domain-design/components.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/units-generation/unit-of-work.md`・`unit-of-work-dependency.md`・`unit-of-work-story-map.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/contract-design/contract-summary.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/practices-discovery/team-practices.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/delivery-planning/delivery-planning-questions.md`（Q1〜Q5: A）
- `aidlc/spaces/default/memory/team.md`・`aidlc/spaces/default/memory/project.md`
