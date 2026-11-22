## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-22T01:02:37Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/code-generation/traceability.json > upstream_ids と coverage | 計画の「持ち越した指摘の受け方」（NFR 設計 R-03）は NFR4.11（指標のメーター）と NFR4.12（TRACE での漏れ）を traceability.json に載せるとしているが、upstream_ids にも coverage にも NFR4.11・NFR4.12 が無い。NFR4.1〜4.10 までしか並んでいない。NFR6.x・NFR8.x・NFR9.x も計画の「要件との対応」に挙がっているが載っていない。確かめるテスト（InternalDbPostgresMigrationIT の poolMetricsAreExposed・InternalDbSecretLeakIT）は実在する。 | NFR4.11 を InternalDbPostgresMigrationIT へ、NFR4.12 を InternalDbSecretLeakIT へ、OK で coverage に足す。NFR6・NFR8・NFR9 は、足すか、載せない理由（別の段が持つ）を code-summary.md に書く。 | New |
| R-02 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/code-generation/traceability.json > FR3.2・BR2.5・BR2.6・FR3.7・BR4.5・NFR7.4 の target | これらの OK の target が移行の SQL ファイルそのもの（実装）で、確かめるテストではない。PostgreSQL の結合テストは名前の有無（pg_class・pg_constraint）と列の型 bytea しか見ておらず、BR2.5 の主張（参照のある親の削除が 23503 で拒まれる）と、uk_invitations_pending_email が PENDING の行だけに一意を効かせる振る舞いは、どのテストでも確かめていない（グレップで DELETE・pending_email が出るのは MigrationSetsConsistencyTest の文字列検査だけ）。 | target を実在するテストに替えるか、振る舞いの確かめは後の単位（U3 など）が持つと Deferred にして持ち主を書く。または PostgreSQL の結合テストに、親の削除が拒まれる 1 件と、同じメールの PENDING の重複が拒まれる 1 件を足す。 | New |
| R-03 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/code-generation/code-summary.md > 依頼者に確かめたいこと 1 | Flyway が接続先（ホスト・ポート・データベース名）を INFO に出す件は正直に書かれている。ただし project.md の Forbidden の文を「接続先・ユーザー名・パスワード・資格情報つきの URL」と引いているが、内部DB の Forbidden は「パスワード・資格情報を含む JDBC の URL」で、接続先の語は対象DB の Forbidden のもの。また InternalDbPostgresMigrationFailureIT の assertNoSecrets は Flyway の 1 行をログから除いて「接続先が出ない」を確かめており、この除外は FR3.10（接続失敗のときに URL を出さない）の網を移行失敗の経路だけ弱めている。URL に資格情報を含めて渡された場合に Flyway がそれを伏せるかは、どのテストでも確かめていない。 | 引用を内部DB の Forbidden の文に直し、論点を「資格情報を含む URL が出うるか」と「ホスト・ポート・DB 名が出ることを許すか」の 2 つに分けて依頼者に諮る。資格情報つきの URL を渡したときの Flyway の出力を 1 件確かめるテストを足すか、確かめていないことを未確認として明記する。 | New |
| R-04 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/code-generation/code-generation-plan.md > Step 15 のチェック | code-summary.md は「すべてのチェックを付けた」と書くが、計画の Step 15 は `- [ ]` のまま。 | traceability.json と code-summary.md の直しの後に Step 15 にチェックを付けるか、要約の文を実際に合わせる。 | New |
| R-05 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/code-generation/code-summary.md > 実測の再現手順（Hikari のログ） | 計画の Step 15 は「版・条件・見えた行」を残すとしているが、本文は「Starting... の INFO 以外が出ないことを確認した」と Spring Boot 4.1.2 だけで、Hikari の版と見えた行の例が無く、再現の手順になっていない。 | Hikari の版と、閉じたポートで見えた Hikari のログの行（値は伏せる）を書く。 | New |
| R-06 | Minor | backend/src/test/java/cherry/mastersmith/internaldb/postgres/InternalDbPostgresMigrationIT.java > lockTimeoutIsTwoSeconds | 説明文は「every pooled connection」だが、確かめているのは jdbc.queryForObject 1 回（接続 1 本）の SHOW lock_timeout だけ。握られた移行のテストが別の接続でも 2 秒台で切れることは支えているが、この 1 件だけでは全接続とは言えない。 | 説明文を「a pooled connection」に直すか、プールの複数の接続で確かめる。 | New |
| R-07 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/code-generation/code-summary.md > テストの結果（単体・境界の件数） | 「単体 85 件＋土台の判定 4 件・境界 2 件を足した分」と書くが、InternalDbBoundaryArchitectureTest には @Test が 4 件ある。件数の書き方が曖昧で、突き合わせられない。 | 実測のクラスごとの件数（または全体の合計）を、実行結果から転記して書き直す。 | New |
| R-08 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/code-generation/code-summary.md > 上流との差（移行の書き換え） | 要約は「書き換えは BYTEA 4 列・STORED・ON DELETE の 7 つだけ」と書くが、diff では V10・V11・V12・V8 から「H2 は DDL を巻き戻せない…バックアップから戻す」のコメントが消え、V3・V5・V8・V10〜V12 に PostgreSQL 用の注が足されている。文には影響しないが、「H2 用と同じ文」の確かめ（計画 Step 2 の diff）と書き方がずれる。 | 要約に、コメントの足し引きだけがあることを 1 行書く。 | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| H2 用の移行と PostgreSQL 用の移行の行ごとの diff（V1〜V14） | 文の差は BYTEA 4 列・STORED の生成列・ON DELETE RESTRICT を外した外部キー 7 つだけ。V1・V2・V4・V6・V7・V9・V13・V14 は同一。H2 用の移行は未変更 | 計画の Step 2 と BR2.5・BR2.6 に合う（コメントの差のみ上の Minor） |
| git status・git diff の範囲 | 既存のテスト・ArchitectureTest・H2 の移行は未変更。src/main の変更は internaldb・PostgreSQL 用の移行・DSL の 2 つのエンティティだけ。application.yaml・Dockerfile・compose は無変更 | 計画との食い違い・紛れ込みは無い。Q1: A の 2 エンティティの変更は @Lob を外して @JdbcTypeCode(SqlTypes.LONG32VARBINARY) を付けるだけ |
| ソースの読み合わせ（Read のみ。テストは流していない） | 種類の判断は InternalDbUrlResolver の 1 か所で、BPP と InternalDbSettings が使う。dataSource の名前だけで当て、PostgreSQL で Hikari でない・起動済みなら UNKNOWN で止める。H2 は何もしない。失敗は ERROR 1 件でキーは ENT-005 の項目だけ。InternalDbStartupException は cause 無し。55P03 は版なしの MIGRATION_FAILED。秘密の直書きは無い（接続の利用者・パスワードはテストごとの乱数） | BR・NFR・DD の実現と上流との差 1〜2 の記載が一致する |
| build.gradle.kts の読み合わせ | integrationTest は excludeTags で PostgreSQL のタグを除く。internalDbPostgresTest は includeTags・JaCoCo 無効で、verify・verifyQuick・check の依存に入らない。0 件の失敗は afterSuite の testCount で見る。verifyPostgresMigrationsInWar は verifyArtifact の段に入っている。ci.yml の手順は手動の入力だけで動き、WAR の保存の後・報告の手順の前 | 通常の verify に PostgreSQL の結合テストが入らない仕組みは成り立つ。0 件のとき afterSuite が呼ばれるかと、欠けたファイルでの失敗は、要約の記録に頼るしかなく、私は実行していない |
| 要約のテストの結果とテストのコードの突き合わせ | PostgreSQL の結合テストの @Test は 7+3+1+1+1=13 件で要約の「13 件」と一致。握られた移行（対象の表・履歴の表）・パスワード違い・閉じたポート・TRACE の漏れのテストがコード上に実在する | 実行結果の数字そのものは私が流していないため未確認 |

### Summary

承認済みの計画と作ったものは合っており、種類の判断の 1 か所化・dataSource の名前だけで当てる後処理・接続ごとの lock_timeout・移行の書き換えの範囲・通常の検査に PostgreSQL の結合テストを入れない仕組みに、秘密の漏えいと成り立たない仕組みは見つからなかった。残るのは追跡表の抜け（NFR4.11・4.12）と、振る舞いを確かめない移行ファイルへの対応、Flyway の接続先の INFO の扱い（Forbidden の引用の直し）などの Minor で、承認の場で直すかを決めればよい。
