# 単位の間の約束（Intent 261114-internal-db-postgresql）

4つの単位の間には実行時の通信が無い（1つの WAR とテストの設定の中で完結する）。約束は、起動時の設定・移行の表の形・テストの土台・repository の口・イメージの固定先の5種類で、どれも同じリポジトリの中のファイルとして受け渡す。外部の API は今のまま変えない（FR3.1）。答えは `contract-design-questions.md`（Q1: A・Q2: A・Q3: A）。

## 約束の一覧

| # | Provider Unit | Consumer | Mechanism | Owner |
|---|---|---|---|---|
| C1 | U1 u1-internal-db-platform | U3・U4 | 起動時の設定（環境変数） | U1 |
| C2 | U1 u1-internal-db-platform | U2・U3 | 共有の表の形（Flyway の移行の一式） | U1 |
| C3 | U1 u1-internal-db-platform | U2・U3 | 共有の表の形（バイナリの列の型とエンティティの写し方の組） | U1（機能設計で決める） |
| C4 | U1 u1-internal-db-platform | U3 | テストの土台（Gradle のタスク・JUnit のタグ・共通の注釈・コンテナ） | U1 |
| C5 | U2 u2-portable-sql | U3 | Java の口（repository のメソッドの形を変えない） | U2 |
| C6 | U1 u1-internal-db-platform（テスト）・U4 u4-local-runtime-docs（compose） | U1・U3・U4 | 共有の定数（イメージの版とダイジェスト） | U1（テスト）・U4（compose） |
| C7 | U1・U2（本体） | External: 画面（SPA）と API の利用者 | 同期の REST/HTTP（今の API。変えない） | 既存の各機能 |

## C1: 起動時の設定（U1 → U3・U4）

U1 は、今の環境変数の名前と形を保ったまま、URL の先頭で内部DB の種類を判断する（FR1.1〜FR1.4）。U3 は C4 の土台を通してこの設定を与え、U4 は compose と文書でこの設定を書く。

```yaml
shared-schema: internal-db-runtime-config
owner: u1-internal-db-platform
source: environment variables only (.env)   # FR1.4・project.md の Forbidden
variables:
  MASTERSMITH_DB_URL:
    required: false
    default: "jdbc:h2:file:./data/mastersmith;DEFRAG_ALWAYS=TRUE"
    kind_by_prefix:
      "jdbc:h2:": H2
      "jdbc:postgresql:": PostgreSQL
      other: startup-failure            # FR1.3
  MASTERSMITH_DB_USERNAME: { required: false, default: "sa" }
  MASTERSMITH_DB_PASSWORD: { required: false, default: "" }
  MASTERSMITH_DB_MAXIMUM_POOL_SIZE: { required: false, default: 30 }
postgresql_session:
  lock_timeout: 2s                      # 書き込みの待ち。行の排他の読み取りはヒントの 3 秒台が上書きする（ADR-001）
supported_versions: ["18"]              # 確かめた版だけ（ADR-006）
failure_behaviour:
  unsupported_kind: stop-startup
  unreachable: stop-startup             # 原因をログに出す（FR3.10）
  migration_failed: stop-startup        # 版の番号をログに出す（FR2.4）
never_logged: [url-with-credentials, username, password]
```

## C2: 移行の表の形（U1 → U2・U3）

```yaml
shared-schema: internal-db-migrations
owner: u1-internal-db-platform
sets:
  h2:
    location: classpath:db/migration    # 今のまま。変えない（FR2.2）
  postgresql:
    location: decided-in-u1-functional-design
rules:
  - same version numbers in both sets (V1..V14 at the start of this Intent)
  - same tables, columns, constraint names and index names
  - foreign keys are NO ACTION (ADR-004)
  - a new migration is added to both sets, with the same version, in the same commit
  - the version and name cross-check runs as a unit test in the normal verify (ADR-002)
  - ddl-auto: validate passes on both kinds
consumers_rely_on:
  u2-portable-sql: table and column names used by the rewritten SQL
  u3-postgres-behaviour-tests: constraint names used to classify violations (FR3.2)
```

## C3: バイナリの列の型と写し方の組（U1 → U2・U3）

組の持ち主は U1 の機能設計（Units Generation のレビュー R-03）。U1 の機能設計の前の捨ての試しで決め、U2 はその組のとおりに写し方を実装し、U3 は 10MB の本文と 32 バイトのハッシュの往復で組を確かめる。この段では型そのものは決めない（未決の点を参照）。

対象は、大きなバイナリ（DSL の本文、H2 では `BINARY LARGE OBJECT`）の2列と、固定長の短いバイナリ（トークンのハッシュ、H2 では `BINARY(32)`）の2列の2組とする。PostgreSQL には `BINARY(n)` の型が無いため、後者も PostgreSQL の側の型を決める必要がある。

```yaml
shared-schema: binary-column-mapping
owner: u1-internal-db-platform
decided_in: u1 functional design (after a throwaway check)
groups:
  large_binary:
    columns:
      - dsl_previews.yaml_bytes             # DSL の本文（最大 10MB、FR3.6。V5）
      - dsl_applied_revisions.yaml_bytes    # V5
    h2_column_type: BINARY LARGE OBJECT (unchanged)
    postgresql_column_type: TBD-by-u1
    entity_mapping: TBD-by-u1               # @Lob byte[] のままか、型の指定を変えるか
  fixed_short_binary:
    columns:
      - refresh_tokens.token_hash           # V3
      - invitations.token_hash              # V8
    h2_column_type: BINARY(32) (unchanged)
    postgresql_column_type: TBD-by-u1
    entity_mapping: TBD-by-u1
must_hold:
  - ddl-auto: validate passes on H2 and PostgreSQL
  - 10MB exactly and the 32-byte hash round-trip unchanged (checked by u3)
  - one mapping per column for both kinds; no branching on the database kind (ADR-003)
if_no_single_mapping:                       # H2 の列の型のまま両方で通る写し方が見つからないとき
  - u1 brings it to the requester before closing its functional design (options: an added migration on both sets, or another mapping)
  - u2 scope may change accordingly (recorded in u2 functional design)
order: u1 functional design comes before u2 and u3 functional design
```

## C4: PostgreSQL の結合テストの土台（U1 → U3）

Q1: A と Q2: A のとおり。名前は ADR-005 で後の設計に回していたが、U3 を U1 と並べて設計できるよう、この段で決める。

```yaml
test-support: internal-db-postgres-it
owner: u1-internal-db-platform
gradle:
  task: ":backend:internalDbPostgresTest"   # 明示して流すときだけ（FR5.2）
  selects: "*IT classes tagged internal-db-postgres"
  integrationTest: excludes tag internal-db-postgres  # 普段の verify・verifyQuick では流れない
  itTests_matches_only_tagged: fail with a message pointing to ":backend:internalDbPostgresTest"  # -PitTests の形がタグ付きのクラスだけに当たったとき（黙って 0 件で通さない）
  not_in: [verify, verifyQuick, ci-push]
ci:
  trigger: workflow_dispatch
  input: internal_db_postgres (boolean, default false)
  container_runtime_missing: fail       # CI では飛ばさない（FR5.3）
junit:
  tag: internal-db-postgres
  annotation: "@InternalDbPostgresIT"   # タグと接続先の設定をまとめる共通の注釈
  package: decided-in-u1-functional-design (under src/test/java, <feature>/testsupport)
container:
  image: TargetDbImages.postgres()      # C6
  lifecycle: one per JVM, reused; Spring context shared
  local_runtime_missing: warn-and-skip  # ContainerRuntimeCheck と同じ扱い（FR5.3）
data:
  default: each test uses only data it creates and cleans up after itself
  empty_database_needed: [migration, initial-admin, migration-failure]
  empty_database_how: create a dedicated database in the same container, drop it afterwards
constraints:
  - follows targetdb/testsupport ExtensionOrderArchitectureTest rules for extension order
  - no H2-only constructs in test helpers (ADR-005)
  - test descriptions in English, test e-mail addresses on reserved domains only
```

## C5: repository の口（U2 → U3）

U2 は SQL の中身だけを両方に通る書き方にし、メソッドの名前・引数・戻り値の型は変えない（ADR-001・ADR-003）。業務処理の層は変わらない。

```yaml
shared-model: portable-repositories
owner: u2-portable-sql
unchanged_signatures:
  - "UserRepository.savePreferences(long userId, Preferences preferences, boolean change, String zone): Optional<SavedPreferences>"
  - "RoleStore.saveWorkRoleSelection(long userId, long roleId, Instant now): RoleStoreOutcome<Void>"
  - "DslPreviewRepository.place(UUID previewId, byte[] yamlBytes, String dslHash, DslSource source, long placedByUserId, Instant placedAt): int"
  - "DslAppliedRevisionRepository.copyFromPreview(UUID revisionId, UUID previewId, long appliedByUserId, Instant appliedAt): int"
also_owned_if_needed:                    # 見込みが外れたときの直し（Units Generation のレビュー R-04）
  - LoginAttemptStateRepository (MERGE INTO ... USING (VALUES ...))
  - RefreshTokenRepository.deleteExpiredBefore (FETCH FIRST :limit ROWS ONLY)
  - InvitationRepository.deleteEndedBefore・deleteExpiredPendingBefore
rules:
  - no branching on the database kind (ADR-003)
  - H2 results stay the same; all H2 ITs pass (FR5.4)
  - personal values stay in masked types along the TraceAspect path (project.md の Code Style)
```

## C6: イメージの固定先（U1・U4 → U1・U3・U4）

Q3: A のとおり、テストの側は対象DB の結合テストと同じ定数を使う。テストの固定先は1か所のままで、内部DB のために新しいテストの定数は作らない。

```yaml
shared-schema: postgres-image-pin
owners:
  test: TargetDbImages (targetdb/testsupport, already pinned)   # 内部DB の結合テストも postgres() をそのまま使う
  compose: u4-local-runtime-docs (new internal-db PostgreSQL service under a profile)
value: same version and multi-arch index digest as the target-DB PostgreSQL (18.6 at the start of this Intent)
dependabot_pin_list:
  add: compose internal-db PostgreSQL service      # .github/dependabot.yml の固定先の一覧
  unchanged: TargetDbImages (already listed)
upgrade_rule: raise the target-DB and internal-DB pins together (team.md の Deployment)
kept_in_step:
  - TargetDbImages.postgres()
  - compose internal-db PostgreSQL service (compose.yaml)
  - docs/internal-db.md (the image version written in the document)
check: u4 adds a unit test in the normal verify that compares the compose service image with TargetDbImages.postgres() (u4 comes after u1 and adds the compose service in the same change); Build and Test also records the match, including docs/internal-db.md
```

突き合わせの単体テストは、compose のサービスを足す U4 が持つ（承認の場の決定、レビュー R-06）。U1 を先に統合しても、相手のサービスが無いまま落ちる・何も確かめないテストにならない。unit-of-work.md の U4 の責務には書かれていないため、U4 の機能設計で責務に入れる。

`team.md` の「内部DB 用の固定先（テストの定数・compose）を固定先の一覧に足す」は、テストの定数を共有するため、足すものが compose のサービスだけになる。足すテストの定数が無いだけで固定先は揃うが、文面とは形が違うため、依頼者の決定（Q3: A）として `memory.md` の Deviations にも記録した。

## C7: 外部の API（本体 → 画面と API の利用者）

新しい API は作らない。内部DB が H2 でも PostgreSQL でも、今の API は同じ応答を返す。

```yaml
openapi: 3.1.0
info:
  title: mastersmith existing API (unchanged by this Intent)
  version: unchanged
x-contract:
  change: none
  invariant: for every existing operation, status code, problem `code` and response fields are the same on H2 and PostgreSQL (FR3.1・FR3.2)
  verified_by: u3-postgres-behaviour-tests (one representative flow per feature)
paths: {}
```

## 約束の持ち主と変え方

- 約束ごとの持ち主は上の表の Owner 列の単位とする。持ち主の単位の設計の段で、依頼者の承認を得て変える。
- 1つの WAR と同じリポジトリの中の約束のため、版の番号は付けない。約束を変えるコミットで、使う側の単位の作業（テスト・文書）も同じ統合の中で合わせる。
- 足すだけの変更（移行の新しい版を両方に足す、注釈に任意の属性を足すなど）は、使う側を壊さない限り持ち主の単位だけで行ってよい。名前の変更・消す変更は、使う側の単位を名指しして設計の段で諮る。
- C2 の版と名前の突き合わせは単体テストで機械で確かめ、C3 の組は `ddl-auto: validate` と U3 の 10MB とハッシュの往復で確かめ、C6 の固定先は U4 の単体テストで compose と `TargetDbImages` を突き合わせる。

## 承認の場の決定

- 2026-11-17: 依頼者は Approve し、レビュー R-06 を直すよう求めた。C6 の突き合わせの単体テストの持ち主を U1 から U4 に移した（U4 の機能設計で責務に入れる）。

## 未決の点

設計の順は、U1 の機能設計を先に置き、U2・U3 の機能設計はその後とする（Delivery Planning に渡す）。

| Contract | Owner | Question | Blocks |
|---|---|---|---|
| C3 | U1 | PostgreSQL の列の型とエンティティの写し方の組（大きなバイナリと 32 バイトのハッシュの2組。捨ての試しで決める）。単一の写し方が無ければ依頼者に諮る | U2 の機能設計・U3 の機能設計（どちらも U1 の機能設計の後に置く） |
| C2 | U1 | PostgreSQL 用の移行の置き場の名前と、Flyway の置き場の切り替えの仕方（Spring Boot の自動設定との順序、ADR-001） | U1 の機能設計 |
| C4 | U1 | 共通の注釈・コンテナの部品を置くパッケージの名前 | U3 の機能設計（U1 の機能設計で決めた後に置く） |
| C5 | U2 | AuthPersistence・InvitationPersistence の SQL が PostgreSQL で通るか（U2 の機能設計の前の捨ての試し） | U2 の機能設計（外れたら U2 の範囲に足す）・U3 の機能設計（結果を確かめる対象に入れる） |

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/units-generation/unit-of-work.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/units-generation/unit-of-work-dependency.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/domain-design/components.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/domain-design/decisions.md`（ADR-001〜ADR-006）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md`（FR1〜FR5）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/contract-design/contract-design-questions.md`（Q1: A・Q2: A・Q3: A）
- `aidlc/spaces/default/memory/team.md`（Testing Posture・Deployment）
- `backend/src/main/resources/application.yaml`（今の環境変数と Flyway の置き場）
- `backend/build.gradle.kts`（今の `integrationTest`）
- `backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java`・`ContainerRuntimeCheck.java`・`ExtensionOrderArchitectureTest.java`
- `.github/dependabot.yml`・`.github/workflows/ci.yml`（固定先の一覧と手動の実行の入力）
