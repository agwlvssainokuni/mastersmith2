# コード生成の計画 — U5 u5-carryover-tests（Bolt B1）

U5 は種類 `library` の単位で、前の Intent から持ち越した3件（FR15・FR16）を受け持つ。ストーリーは無い。この計画が扱うのは次の3つである。

- 不安定なテストの1件目: `RoleConcurrencyIT` の #7（監査の行の ID の順に頼る。K-57）。監査の書き込みの順を入れ替える待ち合わせをテストの補助に足し、2行を中身で見分ける形に直す。
- 不安定なテストの2件目: `AdminForbiddenProvider.test.tsx` の1件（効果の回数を効果が済む前に写しうる。K-58）。写す前に初めの効果を `waitFor` で待つ形に直す。
- `dslMixed` の `memory.peak` の測り方の台本（K-59）: `PERF_DB_URL` の形の確かめ、1秒ごとの記録のループ、`perf/README.md` の (b) 節の手順。測りそのものは Build and Test で行う。

この計画は Part 1（計画）である。依頼者が計画を承認するまで、コードを書かず、ブランチも作らない。

- **本番を変えない**: `backend/src/main`・`frontend/src` のテストでないファイル・`application.yaml`・`Dockerfile`・`compose.yaml`・`docker/perf/compose.yaml`・`.env.example`・`build.gradle.kts`・`vitest.config.ts` は変えない。新しい依存も足さない（TS1）。
- **作る順**: 単位ごとに U1 → U4 → U5 → U2 → U3（`aidlc/spaces/default/intents/261016-business-data-views/construction/code-generation/memory.md` の Deviations）。U1（B2）と U4（B3）は統合済み（`develop` の先頭は `1e78b51`）。B1 は Bolt の計画の1番目から3番目に移った。
- **作業ブランチ**: `develop` から作る `feature/261016-business-data-views-b1`。worktree は使わない。統合は squash（9節）。

## 1. 影響の範囲

### 1.1 新しく作るもの

| パス | 部品 | 中身 |
|---|---|---|
| `backend/src/test/java/cherry/mastersmith/audit/testsupport/AuditWriteReorderBarrierConfig.java` | 入れ替えの待ち合わせ（`logical-components.md` 4節） | `@TestConfiguration(proxyBeanMethods = false)`。待ち合わせ `AuditWriteReorderBarrier`（入れ子の `public static final class`）の Bean と、監査の書き込みの時間を測る `LongSupplier` を `@Primary` で差し替える Bean。`Thread` だけを受け取り、`role` を知らない（依存の向きは role → audit だけ。`security-design.md` S4.3） |
| `backend/src/test/java/cherry/mastersmith/audit/testsupport/AuditWriteReorderBarrierTest.java` | 待ち合わせの単体テスト | 7件（5節の Step 3、`unit-test-instructions.md` 4.1） |
| `backend/src/test/java/cherry/mastersmith/role/testsupport/TestRoleBarrierTest.java` | スレッドの記録の単体テスト | 3件 |
| `backend/src/test/java/cherry/mastersmith/role/testsupport/RoleActorsTest.java` | `Actor` の `toString` の単体テスト | 2件 |
| `perf/lib/perf-db-url.sh` | `PERF_DB_URL` の形の確かめ（`security-design.md` S1、10節の D3: A） | `source` して使う関数 `check_perf_db_url` だけを持つ。受け取った値を表示しない |
| `perf/lib/perf-db-url-test.sh` | 確かめの関数の試しの台本（S1.3） | 悪い入力と正しい入力を1件ずつ与え、件ごとに「通した・止めた」と期待との一致だけを出す。入力の値は出さない（見本の値で秘密ではないが、形をそろえる） |
| `perf/dsl-mixed-memory.sh` | `dslMixed` の1回の測り（記録のループを含む。D3: A） | 作り直し → 始める前の確かめ → 記録のループ → `dslMixed` → 後置き → `memory.peak`・`memory.max`・状態 → 内訳のまとめ → WARN の件数 → 設定の値の確かめ（4.4 の R-02）。1回分だけを流し、回の数え方と環境の作り分けは README の手順で行う |

### 1.2 変える既存のファイル

| パス | 変えること | 出典 |
|---|---|---|
| `backend/src/test/java/cherry/mastersmith/role/testsupport/TestRoleBarrier.java` | `Gate` に `arrivedThread()`（点に来て止まったスレッド）、`Signal` に `passedThread()`（最初に点を通ったスレッド）を足す。記録だけで、止める・通すの振る舞いは変えない | `logical-components.md` 1節・2節 |
| `backend/src/test/java/cherry/mastersmith/role/testsupport/RoleActors.java` | `Actor` の `toString` を上書きし、`userId` だけを出す（`email=***, token=***`）。`email()`・`token()` は変えない | `security-design.md` S4.2（NFR 設計の Q2: A） |
| `backend/src/test/java/cherry/mastersmith/role/web/RoleConcurrencyIT.java` | #7 を2つの場合（自然の順・入れ替えた順）にし、監査の2行を中身で見分ける形に直す。`@Import` に `AuditWriteReorderBarrierConfig` を足し、`@AfterEach` で `disarm()` を呼ぶ。#1・#4 は変えない | BR1.1〜BR1.6 |
| `frontend/src/app/admin-forbidden/AdminForbiddenProvider.test.tsx` | 「keeps the same function before and after the URL becomes forbidden」で、効果の回数を写す前に `waitFor` で回数がちょうど 1 になるのを待つ。ほかの 11 件は変えない | BR2.1〜BR2.3 |
| `perf/dsl-timing.sh` | 引数の確かめの直後・既存の環境の確かめ（106 行）の前の最上位で `perf/lib/perf-db-url.sh` を読み、`PERF_DB_URL` を確かめる（D3: A）。冒頭の注記（45 行）に、受け付ける形を書き足す | `cicd-pipeline.md` 4.4、NFR 設計のレビューの R-02 |
| `perf/README.md` | (b) 節に `dslMixed` の `memory.peak` の測り方（環境の分け方・4回の流れ・記録の列と読み方・設定の値の確かめ・一時ファイルの一覧・秘密の検索・片付け）を足す。DSL の節の `PERF_DB_URL` の行（152 行）に、受け付ける形と止め方を足す | 機能設計 3節、`cicd-pipeline.md` 4節・5節 |

### 1.3 影響を受ける既存のテスト（検索の結果）

`project.md` の学び（変える型・補助を使っている既存のテストを検索で洗い出す）に従い、読み取りの検索で次を確かめた。

| 対象 | 検索で当たったもの | 響き | 扱い |
|---|---|---|---|
| `TestRoleBarrier.Config` を `@Import` するテスト | `role/web` の `RoleConcurrencyIT`・`GroupRoleAssignmentConcurrencyIT`・`RoleUniqueViolationSecretLeakIT`・`RoleTransferApiIT`・`RoleBusyApiIT`・`WorkRoleConcurrencyIT`・`RoleConflictAuditIT`・`RoleConnectionUsageIT`・`RoleTransferTomcatSwallowIT`、`role/service` の `RoleTransferBusyIT`（10 ファイル） | `Gate`・`Signal` にスレッドの記録の欄が増えるだけ | 変えない。Step 14 の verify で通ることを確かめる |
| `RoleActors` を使うテスト | 51 ファイル（`role`・`group`・`navigation`・`businessdata` など） | `Actor` の `toString` が変わる | 文字列にして使う箇所（`+ actor`・`.as(..., actor)`・`String.valueOf(actor)`）は検索で 0 件。`@ParameterizedTest(name = "{0} by {1}")` の `{1}` は列挙の `Subject` で、`Actor` ではない。変えない |
| 監査の書き込みの時間を測る `LongSupplier` を `@Primary` で差し替える補助 | `AuditWriteBarrierConfig`（`auth/web/ConcurrentLoginAuditIT`・`audit/service/AuditWriteTimingIT` が使う）、`SlowAuditWriteConfig`（`AuditWriteTimingIT` が使う） | 新しい `AuditWriteReorderBarrierConfig` も同じ型を `@Primary` で差し替える | `RoleConcurrencyIT` にだけ `@Import` し、ほかの2つと同じクラスに入れない。2つが同じ文脈に入ると Spring が「`@Primary` が2つ」で起動を止めるため、入れ違いは起動の失敗で必ず分かる（NFR 設計のレビューの R-03） |
| `RoleConcurrencyIT` の #1・#4 | 同じクラスの3件 | 文脈に待ち合わせが入るが、準備していない間はすべて止めずに通す | 変えない。Step 6 で4件をまとめて流す |
| 画面のテスト | `AdminForbiddenProvider.test.tsx` だけ（`effectRuns` と `Probe` はこのファイルの中だけにある） | — | 変えるのは1件だけ |
| `perf/dsl-timing.sh` を呼ぶ手順 | `perf/README.md` の DSL の節（106・108・136・171 行）、`README.md` 393 行 | `PERF_DB_URL` を付けない呼び出しは確かめを通らない（何もしない） | 152 行の例 `jdbc:h2:file:/app/data/mastersmith`（設定なし）は受け付ける形に入る。Step 10 の正しい入力に入れて確かめる |
| E2E | 当たり無し | — | 統合の前に流さない（9節。Intent の最後に流す） |

### 1.4 カバレッジと手を入れるパッケージ

- 変えるのは `src/test` とテストのファイルと `perf/` だけで、本体の行・分岐の値は変わらない。`packagesJudgedByTotal` の一覧のパッケージ（`access.service` など7つ）にも手を入れない。
- テストの補助は `src/test` にあり、WAR（`src/main` だけから作る）に入らない（NFR7.1、S4.3）。ArchUnit の境界テストはテストのクラスを読まない（`ImportOption.Predefined.DO_NOT_INCLUDE_TESTS`）。
- フロントエンドのカバレッジの対象と下限は変えない。

## 2. Contract の層の読み替え

この単位は本番の層（内部DB・DB アクセス・業務処理・API・画面の部品）を変えない。Contract の段を次のように読み、methodology（test-after）と ordering（層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて流し、すべて通ってから次の層へ）はそのまま守る。

| Contract の段 | この単位での読み | Step |
|---|---|---|
| Project structure and production configuration skeleton | 作業の準備（本番の設定は変えない） | 1 |
| Verify the existing test runner | 既存のテストで走らせ方を確かめる（brownfield。`unit-test-instructions.md` 3.1） | 1 |
| Data model / database behavior | 当たらない（表・移行を変えない） | — |
| Repository / data access | 当たらない | — |
| Business logic | テストの補助の層（待ち合わせ・スレッドの記録・`Actor` の伏せ字）と、その単体テスト | 2・3 |
| API / endpoint | API を通す結合テスト `RoleConcurrencyIT` の #7（二段の確かめを含む） | 4〜6 |
| Frontend behavior | 画面のテスト `AdminForbiddenProvider.test.tsx`（二段の確かめを含む） | 7・8 |
| Environment/build configuration | `perf/` の台本（確かめの関数・記録のループ・1回の測り）と、その試し | 9〜12 |
| Documentation and traceability | `perf/README.md` の (b) 節（Step 13）、記録（Step 16）。`code-summary.md`・`source-manifest.json`・`traceability.json` は生成の後に指揮役が作る（段の定義の Step 5） | 13・16 |

## Testing Contract

```json
{
  "version": 1,
  "methodology": "test-after",
  "source": "team",
  "ordering": "テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。",
  "scope": "classic",
  "test_strategy": "standard",
  "project_type": "brownfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる。ただし、既存のパッケージに単独で下限を下回るものがあれば、パッケージごとの下限は新しく作るパッケージだけに当てる（既存のパッケージは全体の合計で判定する）。どちらになるかは、パッケージごとの下限を入れる Bolt で既存のパッケージのカバレッジを実測して決め、結果を記録する。\n- 全体の合計で判定している既存のパッケージ（`backend/build.gradle.kts` の `packagesJudgedByTotal` の一覧。2026-10-16 の時点でも 7 パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）。`user.domain`・`user.repository`・`user.service` は Intent 260925-user-management で一覧から外れ、含まない）に手を入れる Bolt では、テストを足してそのパッケージの下限（行 80%・分岐 70%）を満たし、一覧から外してパッケージごとの下限の対象に必ず戻す。「手を入れる」は、そのパッケージの本体のソース（`src/main`）の変更のすべて（説明文だけの直しを含む）を指し、テストだけの変更は含めない。手を入れる見込みのパッケージとそれに伴う作業は、Delivery Planning とコード生成の計画で、各パッケージの今の値を実測して見積もる。カバレッジは、その Bolt のテストを足した後に `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、値を記録する。一度外したパッケージは一覧に戻さず、一覧を増やす変更はしない。\n- フロントエンドのカバレッジの下限は、今の形（全体の合計に当てる `thresholds`）のままとする。新しい機能のフォルダ（`src/features/<featureId>/`）のカバレッジの値は、Build and Test で記録する。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。対象DB の業務データを読むだけのテストでも表を作るため、この決まりを当てる。作る名前には実行ごとに一意の値を入れ、途中で止まった前の実行の残りに頼らない。\n- 対象DB へ書き込むテスト（業務データの作成・更新・削除を範囲に入れる場合）は、アプリの対象DB への接続とテストの接続が別のため、アプリのトランザクションの巻き戻しに頼らない。3種類の対象DB のすべてで、テストのクラスごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）と表を作って行を用意し、終わったら消す。書いた後の値は、アプリとは別の接続で読み直して確かめる。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3種類すべてを `./gradlew verify` の中で毎回実行する（CI も同じ）。Intent `260923-dsl-schema-loader` の Build and Test で `verify` の時間と colima の VM のメモリを実測したうえで、この形に決めた。コンテナの実行環境が無いときの扱いは Way of Working のとおり。\n- テストを足した Bolt の後と Build and Test で、`./gradlew verify` の時間と CI の時間を測って記録する。CI の上限（`timeout-minutes: 60`）の7割の約 42 分を超えたら、テストの分け方を依頼者に諮る。\n- 外へ送るメール（SMTP）を使う機能のテストは、送信の部品をモックで置き換えて済ませず、テストの中で JVM の中に起動するテスト用の SMTP の受け手で実際に受け、宛先・件名・本文（HTML）・言語を確かめる。受け手にコンテナは使わない（コンテナの実行環境が無いときに飛ばしてよいテストは、対象DB のテストだけのままとする）。送信の失敗（受け手が接続を拒む・応答しない）のテストも必ず入れる。実在の宛先へは送らない。受け手の具体の道具は、採用の前のライセンスの確認とあわせて設計の段で決める。\n- 画面からの一連の操作を確かめるテスト（E2E）は Playwright（`./gradlew e2eTest`）で書き、`./gradlew verify` と CI の外に置く。本数は代表的な流れに絞り、機能の Intent ごとに代表の流れを1本まで足す。新しく足す流れは、前のテストが作った状態に頼らず、利用者などの前提を自分で作る。複数の機能の Intent を1つに束ねた Intent では、束ねた元の Intent ごとに数える（Intent 261004-role-menu は最大2本）。既存の E2E を新しい形（権限の形など）に合わせて書き換えることは、本数に数えない。利用者の状態を変える操作（利用停止・管理の権限の変更・ロックなど）は、その流れで自分で作った利用者だけを対象にし、初期管理者の状態は変えない。役割・権限を変える操作も、その流れで自分で作った利用者と役割だけを対象にし、初期管理者は変えない（E2E は1つのアプリ・内部DB・初期管理者を全ファイルで共有して順に流すため）。E2E をどのブランチで統合の前に流すかは Way of Working のとおりとし、リリースの前にも手元で実行する。\n- 業務データを表示する E2E は、見本の対象DB（PostgreSQL の1種類）がコンテナで動いていることを前提にする。`./gradlew e2eTest` は始める前に対象DB に届くかを確かめ、届かなければ起動の手順を示して失敗する（Mailpit と同じ扱い。起動・停止はしない）。流れが使う行は、その流れで自分で作るか、流れの専用のスキーマに置き、見本の行は架空の値だけにする。対象DB の資格情報は Playwright の `webServer.env` に置かずプロセスの環境変数で渡し、報告の json を文字列で検索して資格情報・接続先の値が入っていないことを確かめる。Intent 261016-business-data-views で足す E2E は業務データの代表の流れ1本までとし、持ち越しの直し（make-you-chic-ui の固定先の更新・axe-core の取り込み）と既存の E2E の書き換えは本数に数えない。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- リポジトリは公開のため、テストデータのメールアドレスは予約のドメイン（`example.com` など）だけにし、実在しそうな氏名・宛先を置かない。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- 画面のテストで、描画の後（`useEffect` などの効果）に反映される値は、操作の直後に同期で確かめず、`waitFor` で待って確かめる。テストの時間の上限（Vitest の既定 5 秒など）は、原因を確かめずに延ばさない。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。\n- 不安定なテストと CI の失敗は、次の1つの決まりで扱う。\n  - 手元で再現した不安定なテストは、原因を直すまで統合しない（次へ進まない）。\n  - 手元で再現しないものは、再現の試みに先に時間の上限を決め、見立てと試みの範囲を記録したうえで、CI の再実行で通れば進めてよい（不安定と確かめられていない扱い）。\n  - 同じテストが二度目に落ちたら、原因を直すまで次へ進まない。\n  - 失敗を直さずに次の Intent へ持ち越すのは、依頼者の決定があるときだけとし、決まりとの差を記録する。\n  - 不安定なテストを直したときは、二段で確かめる。疑った競り合い・待ちの抜けを待ち合わせや順の入れ替えで確実に起こせたときは、直す前のテストが落ち、直した後のテストが通ることを記録する。起こせないときは、上の「手元で再現しないもの」と同じく、試みの時間の上限を決めて試み、見立てと試みの範囲を記録する。\n- 認証・認可・監査に関わる機能では、次のテストを必ず書く。★印の項目は要件（しきい値や動作）が未確定のため、要件定義で決めてからテストを書く。この一覧の「管理者」は管理の権限を持つ利用者を、「管理の権限」はその判定に使う権限を指し、権限の形（真偽値1つか役割・権限か）によらずに読む。権限の具体の形（役割・権限の名前など）は、要件で決まった後に書き足す。\n  - アカウントロック: 失敗回数のしきい値の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロック中は正しいパスワードでも拒否、ログイン成功時の失敗回数の扱い。★しきい値、回数を数える期間、ロックの解除方法\n  - ログイン: 存在しないユーザーとパスワード誤りで、応答からユーザーIDの存在を推測できないこと\n  - トークン: 有効期限の境界（直前は有効／直後は無効）、署名の改ざん、署名方式の指定を悪用した改ざん（`alg: none` 等）、ログアウト後のリフレッシュトークンの拒否、ログアウト後もアクセストークンが有効期限まで使えること（決定済みの仕様として明示する）。★各トークンの有効期限の値、リフレッシュトークンを使うたびに作り直すか\n  - 初期管理者の自動作成: 2回目以降の起動で重複作成しない、設定が無い／不正なときの動作、パスワードがログに出ない。★設定が無いときに起動を止めるか\n  - 認可: 未認証（401）、その権限を持たない（403）、持つ（200）をサーバー側のテストで確かめる（画面で管理メニューを隠すことはサーバー側の検査の代わりにしない）\n  - 監査ログ: 対象イベントごとに必須項目が記録されること、存在しないユーザーIDでのログイン失敗も記録されること。★監査ログの書き込みに失敗したときに操作を失敗させるか\n  - 秘密情報の漏えい: ログ・監査ログの出力にパスワード・トークンの値が含まれないこと\n  - 構造化ログ・分散トレース: 決めた形式で出る、トレースIDがログに含まれる、外部エクスポートが既定で無効であること\n  - 画面: ログイン画面のアクセシビリティ検査、ロック時のメッセージ表示、ログアウトでトークンが破棄されること\n  - 招待と登録の完了: 招待の有効期限の境界（直前は有効／直後は無効）、使い終えた招待・取り消した招待の再使用の拒否、改ざんした招待・存在しない招待の拒否（応答から利用者の存在を推測できないこと）、招待中（登録が終わっていない）の利用者はログイン・トークンの更新・アクセストークンの認証のどれでも拒否されること。★招待の有効期限の値、再送・取り消しの扱い\n  - パスワードの変更: 今のパスワードの確かめ、パスワードの規則の境界、変更の後のリフレッシュトークンの扱い（決めた側の動作を明示したテストにする）。★パスワードの規則、変更の後にほかの端末のリフレッシュトークンを無効にするか\n  - 利用停止: 停止中の利用者は、ログインの照合・トークンの更新・アクセストークンの認証の3つの入口のすべてで拒否されることを、入口ごとにサーバー側のテストで確かめる。停止を解いた直後は3つの入口のすべてで受け付けること。停止の前に出したリフレッシュトークン・アクセストークンの扱いは、決めた側の動作を明示したテストにする。★停止を応答から推測できてよいか、停止の前に出したトークンの扱い\n  - 管理の権限の変更: 管理の権限を与えた直後・外した直後の次の要求で、管理の API の 403／200 がサーバー側で切り替わること（画面が持つ古い権限に頼らない）。権限を外す前に出したトークンの扱いと、自分の管理の権限を外す操作の扱いは、決めた側の動作を明示したテストにする。★権限を外す前に出したトークンの扱い、自分の管理の権限を外す操作の扱い\n  - ロックの解除: 解除の直後に正しいパスワードで入れること、解除の後の失敗回数の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロックの状態の行が無い利用者の解除。時刻は注入した時計で動かし、実時刻と `sleep` に頼らない。★解除の後の失敗回数の扱い、ロックの状態の行が無いときの応答\n  - 最後の管理者の保護: 最後の有効な管理者（管理の権限を持つ利用者）から管理の権限を外す・利用を止める操作は拒否され、拒否の後に状態が変わっていないこと。2人の管理者が同時に互いの管理の権限を外す・利用を止めても、有効な管理者が 0 人にならないこと。同時の重なりはスレッドの数に頼らず、待ち合わせで確実に作る。★最後の管理者の数え方（止めた管理者を数えるか）\n  - 管理の API の認可: 足す管理の API のすべてについて、未認証（401）・その権限を持たない（403）・持つ（200）をサーバー側のテストで確かめる。停止中の管理者は管理の API を呼べないこと。\n  - 要求の改ざん: 管理の API の外（`/api/me` など）から、要求の本文の値を変えて自分の管理の権限や利用者の状態を変えられないこと（一括代入の防止）。\n  - 管理の操作の監査: 利用者の権限・状態を変える操作（管理の権限の付け外し・利用停止と再開・ロックの解除）ごとに、操作した人・対象の利用者・結果が記録されること。★拒否した操作（403・最後の管理者の拒否など）を記録するか\n  - 利用者の管理の漏えい: 一覧・詳細の応答と監査の行に、パスワードのハッシュ値・リフレッシュトークン・ロックの判定の内部の値が含まれないこと。TRACE のログを有効にして一覧・詳細を読んでも、メールアドレス・氏名がアプリのログに出ないこと（既存の `*SecretLeakIT` と同じ形で確かめる）。\n- 役割・権限の機能では、次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 権限ごとの API の認可: 足す・変える API のすべてについて、未認証（401）・その権限を持たない（403）・持つ（200）をサーバー側のテストで確かめる。403 を確かめる利用者は、権限が何も無い利用者ではなく、要る権限だけを欠く利用者（ほかの権限は持つ）にする。API と権限の組は、パラメーターを使うテストで表として書く。停止中の利用者は、権限があっても通らないこと。\n  - API の分類の網羅: アプリが持つすべての API が「公開・ログインだけ・権限が要る（どの権限か）」のどれかに明示して分類されていることを確かめるテストを置き、分類の無い API があれば失敗させる（`/api/**` の既定は「ログインだけ」で、権限の決まりを書き忘れるとログイン中の誰でも呼べるため）。足した API を分類に足し忘れると落ちる形にする。分類の持ち方と仕組みの置き場は設計の段で決める。\n  - 割り当ての変更の反映: 役割・権限を付けた直後・外した直後の次の要求で、403／200 がサーバー側で切り替わること（画面が持つ古い権限に頼らない）。★変更の前に出したトークンの扱い\n  - 権限の昇格・一括代入・IDOR の防止: 操作する人が持たない権限を、自分や他人に与えられないこと。要求の本文の値を変えて自分の役割・権限を変えられないこと、役割の作成・変更の本文に許していない項目（ID・組み込みの印・作成者など）を足しても反映されないこと。役割・割り当ての ID を差し替えた要求（他人の割り当て・存在しない役割など）は拒否され、状態が変わらないこと。★組み込みの役割の有無と、その削除・権限の取り上げの保護\n  - 自分自身への操作と最後の管理者の読み替え: 自分の役割を外す操作と、最後の有効な管理者を役割でどう数えるかは、決めた側の動作を明示したテストにする。同時の重なりはスレッドの数に頼らず、待ち合わせで確実に作る。★自分の役割を外す操作の扱い、最後の有効な管理者の役割での数え方\n  - 監査: 割り当ての変更ごとに、操作した人・対象・変えた中身・結果が記録されること。★役割そのものの作成・変更・削除を記録するか、拒否した操作（403・昇格の拒否など）を記録するか\n  - 画面の出し分け: メニュー・画面を隠すことを、サーバー側の検査の代わりにしない。権限の無い画面へ直接移ったときに 403 の表示になること。\n  - 性質ベースのテスト: 役割から権限の集合を求める関数（サーバー側）と、権限でメニューの枝を落とす関数（画面側）は純粋な関数にし、jqwik・fast-check で性質（持たない権限の API・項目が出ない、役割を足しても権限は減らない など）を確かめる。\n- 業務データの一覧・詳細（と、範囲に入れるなら作成・更新・削除）の機能では、次のテストを必ず書く（役割・権限の機能と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。具体的な上限の値は設計の段で決め、決めた値の境界で確かめる。\n  - 権限ごとの操作の可否: テーブルの主権限と補助権限の組ごとに、業務データの API の未認証（401）・その権限を持たない（403）・持つ（200）をサーバー側のテストで確かめる。API と権限の組は、パラメーターを使うテストで表として書き、403 を確かめる利用者は要る権限だけを欠く利用者にする。作業ロールを切り替えた直後・割り当てを変えた直後の次の要求で切り替わること。\n  - 主キーとビュー: 主キーの無いテーブルとビューでは、行を1件に決められない操作（更新・削除など）を、画面で隠すだけでなくサーバー側で拒否する。主キーが複数のカラムのテーブルも確かめる。★主キーの無いテーブルで詳細を見せるか\n  - カラムの出し分け: カラムの主権限が NONE（読めない）のカラムは、画面で隠すだけでなく API の応答に含めないことを確かめる。DSL の表示の設定（`list.visible`・`detail.visible`）で隠すだけのカラムの扱いは設計の段で決め、決めた側の動作を明示したテストにする。\n  - IDOR と一括代入: スキーマ名・テーブル名・主キーの値・並べ替えの列・検索の列を差し替えた要求（適用中の DSL に無い名前、権限の無いテーブル、存在しない行）は拒否され、状態が変わらないこと。作成・更新の本文に許していないカラムを足しても反映されないこと。\n  - 403 と 404 の使い分け: 権限の無いテーブル・DSL に無いテーブル・存在しない行の応答の状態コードと code をそろえるか。★使い分け（決めた側の動作を明示したテストにする）\n  - SQL の組み立て: 識別子は適用中の DSL にある名前と照らし、値はバインド変数で渡すこと。識別子・検索の値・並べ替えに `'`・`\"`・`;`・`--`・バッククォート・改行を含めても、SQL として解釈されないこと（Code Style の SQL の組み立ての決まりを確かめる）。\n  - 3種類の対象DB の境界の値: 一覧・詳細（と書き込み）を、MySQL・MariaDB・PostgreSQL のすべてで結合テストで確かめる。型の違い・識別子の大文字と小文字・NULL の並びと、境界の値（最大の整数・桁の多い小数・NULL）を含める。\n  - 行の数の上限と時間切れ: 一覧のページの大きさの上限の境界（上限ちょうどは受け付け、超えると拒否）。JDBC の取り出しの上限（`setMaxRows` か問い合わせの上限）を置き、上限を超える行を内部で読まないこと。応答しない対象DB での時間切れの応答（既存の ServerSocket の方式）。\n  - 部分一致の検索: 部分一致の検索を設けるなら、検索の値の `%`・`_`・エスケープの文字が一致の条件として働かない（エスケープして渡す）こと。\n  - 画面に出す値: 業務データの値とカラムの表示名を信頼できない入力として扱い、`<script>`・`<img onerror>`・`\"`・`'`・`javascript:` を含めても、一覧・詳細で文字として出る（HTML として描かない、リンクの先にしない）こと。\n  - 道に乗る主キーの値: 画面の道に乗る主キーの値（複合の主キー、`/`・`%`・`?`・`#`・空白・引用符を含む値）をエンコードし、サーバー側でデコードの後に照らすこと。\n  - 接続の数: 業務データの API が1要求で使う対象DB の接続と内部DB の接続の数を、既存の `*ConnectionUsageIT`（`NavigationConnectionUsageIT` など）と同じ形の結合テストで決定的に確かめる。\n  - 性質ベースのテスト: テーブルの主キーの有無・ビューか・主権限・補助権限から許す操作の集合を求める関数と、識別子の照合の部品は純粋な関数にし、jqwik・fast-check で性質（権限を足しても許す操作は減らない、主キーの無いテーブルに行を1件に決める操作が出ない、DSL に無い名前は拒否される、引用符・`;`・改行を含む任意の文字列でも SQL の構文として解釈されない など）を確かめる。\n  - 漏えい: 業務データの行の値（主キーの値を含む）と対象DB の接続情報が、アプリのログ（TRACE を有効にした `XxxSecretLeakIT`）・トレースの属性（外部へ送る設定を有効にした結合テスト）・エラー応答に含まれないこと。JDBC の例外のメッセージが応答にもログにも出ないこと。\n  - 監査: ★業務データの操作（読み取りを含めるか）を監査に残すか、残す中身、対象DB の確定と監査の行の一方だけが残ったときの扱い。決まった側の動作を明示したテストにする。\n  - 画面: 一覧・詳細の画面ごとのアクセシビリティ検査、権限の無いテーブルへ直接移ったときの 403 の表示、操作の可否に合わせてボタンが押せない（または出ない）こと。\n- axe-core を上げて新しい規則の違反が出たときは、規則を外して検査を通さず、違反をすべて直してから上げる（固い制約は `project.md` の Forbidden）。違反の要素が make-you-chic-ui の部品のものなら依頼を出し、固定先の更新で取り込んでから上げる。上げたときは、実際のブラウザの axe（E2E）と画面のテストの vitest-axe の両方を流し、E2E の全件の通過と違反 0 件を件数で記録する。あわせて、axe-core の版が lockfile で1つにそろっているかを確かめる。\n- N 階層のメニュー（ナビゲーションの木）の画面では、次のテストを必ず書く。深さの上限の具体的な値は設計の段で決め、決めた値の境界で確かめる。\n  - 開閉と今の項目: 開閉の状態（`aria-expanded`）と今の項目（`aria-current`）が正しく出ること、キーボードだけでたどれること。\n  - 深さの上限: 上限ちょうどは受け付け、上限を超えると拒否されること。\n  - 表示の木を作る関数: 定義の木から表示の木を作る純粋な関数（権限で枝を落とす・並べる）に、fast-check の性質ベースのテストを当てる（例: 権限の無い項目が出ない、子の無い枝が残らない）。\n  - 信頼できない入力: メニューの定義（`label`・`icon`・`table`）を信頼できない入力として扱う。表示名に `<`・`>`・`&`・`\"`・`'` を含めても文字として出る（HTML として描かない）こと、`icon` を許す名前の一覧と照らすこと、`table` の名前から組み立てる道をエンコードし、アプリの中の決めた形から外れない（外部の URL・`javascript:` の道を作らない）こと。★一覧に無い `icon` を拒否するか既定のアイコンにするか\n  - make-you-chic-ui の部品に頼る振る舞い: 開閉・`aria-current`・キーボードなど、make-you-chic-ui の部品に頼る振る舞いも、このリポジトリの画面のテストと実際のブラウザの axe で確かめる（make-you-chic-ui のテストはこのリポジトリの CI の対象外のため）。axe は、展開した状態・深い階層・長い名前で、ブランドカラーとテーマのすべての組について行う。\n- 利用者へメールを送る機能では、Code Style と `project.md` のメールの決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 本文のエスケープ: 差し込む利用者の値（氏名・メールアドレスなど）に `<`・`>`・`&`・`\"`・`'` を含めても、タグや属性としてそのまま出ないこと。利用者の値をエスケープしない差し込み（`{{{ }}}`・`{{& }}`）で入れていないこと、属性の値を二重引用符で囲んでいること（エンジンの `{{ }}` は `'` を置き換えないため）\n  - テンプレートの描画: 日本語と英語のすべてのテンプレートを描けて、件名が空でないこと、差し込み漏れ（`{{` の残り）が無いこと、ライセンスヘッダーが描いた本文に出ないこと\n  - 招待の URL: 設定したベース URL だけから組み立て、要求の Host ヘッダーから組み立てないこと。ベース URL の設定が無いときに要求の Host を変えても、招待の URL に出ないこと。★ベース URL の設定が無いときの動作\n  - トークンと URL の漏えい: 招待のトークンと招待の URL が、アプリのログ・監査ログ・トレースの属性・エラー応答に含まれないこと（既存の `*SecretLeakIT` と同じ形で確かめる）\n  - ヘッダーへの差し込み: 宛先・件名・差し込む値に改行（CR・LF）を含めても、メールのヘッダーが増えず拒否されること\n  - 送信の失敗: 送信が失敗したとき（受け手が接続を拒む・応答しない）に、宛先のメールアドレス・SMTP の応答・資格情報が応答とログに含まれないこと。★送信が失敗したときの業務の動作（招待を失敗させるか、再送に回すか）\n- 利用者が投入する DSL（YAML）を読み込む機能では、Code Style の「信頼できない入力」の決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。上限の具体的な数値は設計の段で決め、決めた値の境界で確かめる。\n  - 大きさ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 入れ子の深さ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 別名（アンカー）: 展開の数が上限を超えると拒否され、別名の展開の爆発で処理が止まらない\n  - タグ: 任意の型を作るタグ（`!!` など）を含む DSL は拒否され、型が作られない\n  - 重複キー: 同じキーが重なる DSL はエラーになり、後の値で黙って上書きされない\n  - JSON Schema の `$ref`: 外部の URL を取りに行かない\n  - 拒否の応答: Problem Details の形で返り、YAML・JSON Schema の部品の例外のメッセージを含まない\n- 性能・資源の目標の測り方（物差し）を変えるのは、要件定義で依頼者が明示に決めたときだけとし、変える理由を記録する。変えた後も、少なくとも1回は前の物差しの値を並べて記録する。\n- 負荷の試験の使い捨ての環境の対象DB の行は、perf の台本（`perf/` の下）で決めた種から作る（行の数は NFR 要件の段で決める）。k6 の場面の setup は、前の場面が残した状態に頼らない。\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22)"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 (learned 2026-09-23) \n- 負荷の環境や配備先が決まらないと測れない目標（応答時間のパーセンタイル、運用の指標、ファイルの権限など）は、Build and Test で `Unverified` とし、持ち主の段（performance-validation・observability-setup・deployment-execution）を明記して引き継ぐ。目標を緩めて「満たした」ことにはしない。 (learned 2026-09-23) \n- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は perf/README.md。 (learned 2026-09-23) \n- 負荷の試験で、アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。 (learned 2026-09-23) \n- 同時の重なりを確実に作るため、本番のコードを変えずに、監査の書き込みの時間を測る LongSupplier（AuditEventListener で2本目を借りる直前に呼ばれる）をテストで差し替えて待ち合わせる方式にした。既存の LoginConcurrencyIT は 8 スレッドでプールの 10 に届かず、前回の失敗のログインで尽きなかった理由の1つと見られる。 (learned 2026-09-23) \n- Intent の流れに Performance Validation の段が無く、負荷の環境（使い捨ての環境）を手元で用意できるときは、k6 の試験と NMT の測定の持ち主を Build and Test とし、Unverified で引き継がずにその段で実行する。 (learned 2026-09-23) \n- 修正の前の設定（例: 上限 1g）も修正の後の環境（例: CPU 4 の VM）で流し（pre1g）、要件の前提（VM を上げても F3 が起きる）を実測で裏付ける。 (learned 2026-09-23) \n- パッケージごとのカバレッジの下限と SpotBugs の SQL_ の関門を U1 の計画に入れた。team.md の決まりだが今のビルドに無く、この Intent で最初に作る単位のため。U1 の設計の文書には無い作業。 (learned 2026-09-24) \n- U4 で既存の AuditSecretLeakIT の列の一覧に V6 の4列を足し、テストの JVM のヒープを 1g にした。前者は承認済みの V6 と必ず食い違うため、後者は構造の検査がクラスを持ち続け U3 の 10MB 超えのテストでヒープが尽きたため。どちらも計画に無い変更で、依頼者に確かめる。 (learned 2026-09-24) \n- 応答しない対象DB の TIMEOUT の確かめに、テストの中で開いた ServerSocket（受け付けて何も返さない）を使う。外の端末に頼らず確実に再現できる代わりに、本物の DB の遅延ではない。 (learned 2026-09-24) \n- パッケージごとのカバレッジの下限は新しいパッケージだけに当てた。実測で audit.service（行 77.2%）・common.health（行 79.2%）・auth.repository（分岐 50.0%）が単独で下回ったため（team.md の決まりどおり）。既存の 22 パッケージを一覧で外し、新しいパッケージは自動で対象になる。 (learned 2026-09-24) \n- 負荷の試験で接続プールが尽きたかを確かめるときは、使い捨てのアプリにだけ MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics を渡して /actuator/metrics の hikaricp の値を読み、数秒ごとの使用中の数ではなく、待ちの時間切れの累計と借りるまでの待ちの最大で判断する（配備したアプリの公開の範囲は変えない）。 (learned 2026-09-24) \n- k6 などの長い試験は caffeinate -i を付けて流し、PC の自動のスリープで要求が止まって結果が崩れるのを防ぐ（バッテリー駆動のまま 414 秒スリープし、要求が 6分52秒止まった）。内部DB に SQL で直接入れた試験用の利用者はロックの状態の行が無いため、同時のログインを流す前に1人ずつログインさせて行を作る。 (learned 2026-09-24) \n- 軽い API の性能は、投入を重ねて内部DB のファイルが膨らんだ状態（悪い側の条件）のまま測る。メモリの最大（memory.peak）を比べる試験の前は、アプリのコンテナを作り直して前の最大の値を消す。 (learned 2026-09-24) \n- Performance Validation の段が無いため、2件目の直しの負荷の試験での確かめ（Q5: A）の持ち主を Build and Test とした（project.md の学びどおり）。 (learned 2026-09-24) \n- colima の PC で ./gradlew verify を流すときは、README の DOCKER_HOST と TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE をシェルに渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗した。付けて流し直した結果を基準とした。 (learned 2026-09-25) \n- unit-test-instructions.md の k6 inspect のコマンドは --include-system-env-vars が無いと場面の名前が undefined になり確かめにならなかった。承認済みの文書は変えず、実際には付けて流し、code-summary.md に差を記録した。 (learned 2026-09-25) \n- 1回目の verify で AccessTokenApiIT の 6 件が接続の失敗で落ちた。変更の経路に触れず、同じ時刻に Gradle の作業プロセスとの接続も時間切れだったため PC の負荷による一時的な失敗と見立て、段の中の直しの1回目としてコードを変えずに verify を流し直して通した。原因は確かめていない。 (learned 2026-09-25) \n- 依頼者の判断で、試験のイメージを mastersmith:local ではなく mastersmith:followup-fixes で作り、perf/README の手順（local を作り直す）から外れた。配備したアプリは k6 のあいだ止め、約 17 分後に同じコンテナで起動し直した。 (learned 2026-09-25) \n- 試験の後にロックの状態の行の数を数える問い合わせの列の名前を誤り、使い捨ての環境を消した後で取り直せなかった。合格の条件（ログインの checks と 500 の件数）で判定した。消す前に確かめの結果を見てから片付けるべきだった。 (learned 2026-09-25) \n- Q4（確かめられたときだけ直す）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違いうるため、追加の質問 F3 で、再現できなければ不安定と確かめられていない扱いとして統合してよいことを確かめた。 (learned 2026-09-25) \n- 要件 FR2.2（目標は計画の承認の場で決める）を満たすため、依頼者の決定（Q1: A）で、計画を書く前に dslMixed でメモリの内訳を測ることにした。測る間は配備したアプリを止める。 (learned 2026-09-25) \n- 生成で、テストの既定で MBean の登録を無効にする置き場を、計画の候補の TestDatabase ではなくテストの EnvironmentPostProcessor にした。TestDatabase を使わずに Spring を起動するテストのクラスが 15 あり、漏れるため。再現の結合テストは 12 回では伸びが小さく判定がはっきりしないため、24 回・履歴の上限 3 にした。 (learned 2026-09-25) \n- AccessTokenApiIT は、同じ例外の文言を仕組み（colima の IPv4 の転送と Java の IPv6 の待ち受けの番号の重なり）ごと再現できたことを原因の確認とみなし、依頼者の決定（D7）でテストの JVM に preferIPv4Stack を付けて直した。実際の1回目がこれで起きたかは確かめられず、今回の繰り返しでは重なりは起きなかった。 (learned 2026-09-25) \n- Test Strategy は Minimal だが、要件 FR5 と計画の「Build and Test に引き継ぐこと」のため、結合・性能・セキュリティの手順書も作り、この段で dslMixed・--storage --compact（40 回）・詰め直しの最中のログイン・refresh・dslCycle を流した。 (learned 2026-09-25) \n- 計画の dslCycle を最初の台本に入れ忘れ、配備したアプリをもう一度（約 4 分）止めて流した。負荷の試験の台本を書く前に、計画の「Build and Test に引き継ぐこと」の項目を一つずつ台本の手順と突き合わせる。 (learned 2026-09-25) \n- 実際のブラウザのアクセシビリティの検査（axe）は、誤りを出した状態と、現実に近いデータ（2語の氏名など）で、ブランドカラーとテーマのすべての組について行う。user-management の U7 の 080 で初めて、make-you-chic-ui の Avatar（頭文字が2文字のとき）と dark の FormField の誤りの文字のコントラスト不足が出た（050〜070 は誤りの状態を見ず、氏名が1語だったため出なかった）。 (learned 2026-09-28) \n- Playwright の webServer.env に置いた値は json の報告に残る。E2E の仮の資格情報はプロセスの環境変数で渡し、報告の部品の確かめと json の文字列の検索で、パスワード・トークン・メールアドレスが入っていないことを確かめる（user-management の U4 で見つかった）。 (learned 2026-09-28) \n- CI（66fe981）の verify が2回とも別々のテストの時間切れ（H2CompactionByPoolSuspensionIT の接続の待ち 10 秒、InvitationAdminPage.test.tsx の既定 5 秒）で失敗し、U4〜U7 の NFR7.1 が Not Met だったが、依頼者の決定「次のintentでコントラストと一緒に直す。このintentではこのまま進める。」で受け入れた失敗とした。team.md の「CI が失敗したら次に進む前に直す」と食い違うため test-results.md 8.1 に差を記録した。 (learned 2026-09-28) \n- k6 の登録の完了と取り消しの場面は、決定の文言（招待を VU の数だけ）ではなく既定 100 回・招待 100 件とした。トークンは1回しか使えず 10 件では p95 の意味が薄いため、U3 の NFR6.4 の「流す回数以上の招待を用意する」に合わせた。承認の場で確かめる。 (learned 2026-09-28) \n- caffeinate -i は場面ごとではなく、試験の台本全体を包む形で付ける。場面ごとに起こし直すと切れ目で守りが外れ、PC が眠る（user-management の Performance Validation で 92 秒眠り、要求が 1 分 29 秒遅れた）。遅れが出たときは pmset -g log でスリープを確かめる。 (learned 2026-09-28) \n- 応答しないメールの受け手は、Mailpit を docker pause で一時停止して作る（接続は受け付け、何も返さない）。コンテナを止めると接続の失敗になり、時間切れの確かめにならない（user-management の Performance Validation）。 (learned 2026-09-28) \n- Q1 で原因を確かめずに上限を延ばす答え（C）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違うため追加の質問 F1 で確かめ、延ばしたうえで失敗時の診断を足し、差を記録する形（B）になった。Q3 の「すべて試す」は verify で判定できない Temurin 26・logback-appender の扱いを F2 で確かめ、見送り（C）になった。 (learned 2026-09-29) \n- 診断の確かめで、上限 1 ミリ秒は Awaitility の問い合わせの間隔（100 ミリ秒）より短く設定の誤りで弾かれ、150 ミリ秒では手元で接続がすぐ 0 本になり時間切れにならなかった。上限 500 ミリ秒と、待ちの条件を一時的に満たせない形（== -1）にして時間切れを起こした。確かめた後に git checkout で戻した。 (learned 2026-09-29) \n- 警報が鳴ることは、しきい値を 50 ms に下げた警報の決まりの写しを使い捨ての環境に読み込ませて確かめた（Q1: A）。本当に遅い応答を作るより早く PC の負荷も小さいが、しきい値の値そのもので鳴ることは確かめていない。途中で ms-check-p95 のしきい値が 1000 ms ではなく 300 ms（バケットの境界に無い）と分かり、要件の前提 A3 と食い違った。 (learned 2026-09-29) \n- 表示の設定を画面の操作の直後に同期で確かめるテストが、負荷の高い CI で2件（ShellLayout・PreferencesPage）落ちた。描画の後の効果（useEffect）で反映される値は waitFor で待って確かめる。同じ形の RegistrationPage も合わせて直した。 (learned 2026-09-29) \n- Delivery Planning でのカバレッジの実測は、./gradlew verify 全体ではなく :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport で行い、jacocoTestReport.xml から手を入れる見込みのパッケージの値を読む（user-admin で約 5 分）。 (learned 2026-10-01) \n- 接続プールの見積もりを確かめる負荷の試験には、上限に届く形（プールの上限を下げた場面など）を含める。上限に届かない負荷では、見積もりが誤っていても合格する（user-admin の U3 の NFR 要件のレビュー R-02）。 (learned 2026-10-01) \n- 画面のはみ出しの確かめ（E2E・axe）では、画面全体の横のスクロールだけでなく、開いたメニュー・ポップアップなど画面に固定で置く部品が画面の中に収まることも確かめる。表の右端に置く make-you-chic-ui の Dropdown は placement に bottom-end を指定する（user-admin の配備の後のスモークテストで、行の「操作」のメニューが右へはみ出していたのを E2E 120 が拾えなかった）。 (learned 2026-10-03) \n- k6 の http_req_duration が、colima の VM の時計が負荷の間にホストより約 2 秒ずれて合わせ直されるため一部崩れた（待ちの最小 0 ms、繰り返しより長い約 3 秒の値）。1回の繰り返しが要求1つの loginSuccess では、単調な時計の iteration_duration とサーバー側の最大が合うため、判定は iteration_duration で行い、http_req_duration は並べて記録した。前の Intent の 939.6 ms も同じ影響を受けていた可能性がある。 (learned 2026-10-04) \n- 1〜6回目で繰り返しの時間の p95 が2回 1 秒を超え、依頼者が VM の時計を合わせて測り直しを求めた（F1・F2 の Other）。測り直しの7〜9回目は 1 秒を下回ったため、1〜6回目を FR2.2a の条件がそろわない回として外した（承認の場で確かめる）。時計のずれを ssh の前後のホスト時刻の中間と比べる誤った測り方で 2.2 秒と読み、測り方を直して範囲（VM−終わり、VM−始め）で記録し直した。配備したアプリは2回止めた（約 12 分と約 4 分）。 (learned 2026-10-04) \n- k6 の判定は1回の繰り返しに要求1つの場面の iteration_duration で行い、トークンは setup() で取って場面を 3 分にした。時計のずれに強い代わりに、操作が2つ以上の場面は op のタグが iteration_duration に付くかを台本で確かめる必要がある（group のレビューの R-01 を先に避けた）。 (learned 2026-10-05) \n- 上限を下げた k6 の場面を置かず、接続の数は結合テスト NavigationConnectionUsageIT で決定的に確かめる。読み取りだけで1要求1本のため詰まりが起きず、role のレビューの R-02 の「k6 では見積もりの誤りを見分けにくい」を避けられる。代わりに、負荷の下での待ちの長さは測らない。 (learned 2026-10-05) \n- 違反のテストは先の側を確定させてから書く待たない違反、上限切れは H2 の上限まで放さない形にした（Q3: A）。時間の境に合否を預けない代わりに、待った後の違反の経路は区分の単体テストだけで確かめる。 (learned 2026-10-06) \n- 長いテスト（verify・E2E・k6）は caffeinate -dims で包み、流す前に pmset -g assertions で眠らない状態か、流した後に pmset -g log で Sleep・DarkWake が無かったかを確かめ、入っていたら結果を使わずに流し直す。DarkWake の Thermal Emergency のスリープは caffeinate -i では止められず、role-menu の B8 で画面のテストが約 660 秒止まって時間切れになった。 (learned 2026-10-13) \n- コード生成の計画の段で、変える型・表・移行の番号・API・画面の骨組みを使っている既存のテストと E2E を検索で洗い出し、影響の範囲に入れる。role-menu では移行の最後の版を前提にしたテスト（AuditMigrationCompatibilityIT）、SQL の文の数を数えるテスト（GroupAdminListQueryCountIT）、狭い幅のはみ出し（E2E の 375px）で計画の外の失敗が起き、生成が止まった。 (learned 2026-10-13) \n- 木の画面の単体テスト・E2E・スモークテストでは、本物と同じ形の応答（子の有無の印を含む）を使い、すべての段の開閉を押して確かめる（role-menu の配備の後のスモークテストで、「自分の権限を見る」の木でテーブルの開閉を押すと自分自身を子に持って固まる不具合が見つかった。単体の見本のテーブルは子が無い形で本物と違い、E2E は選ぶだけで開閉を押していなかった）。 (learned 2026-10-14) \n- 画面の不具合を使い捨ての環境で再現するときは、依頼者がした操作（どのボタン・どの開閉を押したか、画面の文言とその場所）を先に聞き、同じ操作を試す。role-menu の S8 で、選ぶだけで開閉を押さずに再現せず「環境の差」と誤って見立て、依頼者の再試行で条件が分かった。 (learned 2026-10-14) \n- k6 の場面の setup は、前の場面が残した状態（ロールの割り当てなど）に頼らず、自分で前提を作るか片付けてから始める形で書く（role-menu の Performance Validation で、workRoleRead・myPermissionsTree・roleAssignmentsRead が前の場面の割り当てで 409 ROLE_NO_CHANGE や確かめの不一致になり、割り当てを外して流し直した）。 (learned 2026-10-15) \n- 接続の待ちの最大のように数 ms の目標は1回の値が揺れるため、合否の回を複数回流すことを負荷の試験の計画に入れる（role-menu の U4-NFR2.7b で1回目 23.55 ms、作り直した流し直しで 8.55 ms となり、原因を確かめないまま Not Met で持ち越した）。 (learned 2026-10-15) \n- 大きな入力（10 MiB の DSL の投入など）のヒープの見積もりは、設計の段で試しの測定で裏付ける（role-menu の Performance Validation で GC の後でも 573〜746 MB あり、見積もりの 256〜384 MB を超えた。dslMixed の memory.peak は上限 2g の 87%）。 (learned 2026-10-15) \n- 依頼者の決定で、E2E をブランチごとの統合の前ではなく Intent の最後（Build and Test）に1回だけ流すことにした。① と ② は統合の前に流して通し、③・④ は ./gradlew verify だけで統合した。team.md の「画面・認証に関わる変更は統合の前に E2E」との差。 (learned 2026-10-15) \n- CI の 776738e の1回目が AdminForbiddenProvider.test.tsx の1件（描画の後の効果の回数を、効果が済む前に数える形）で落ち、2回目の試行で通った。手元で10回流して再現しなかったため、team.md の「手元で再現しないものは CI の再実行で通れば進めてよい」の扱いとし、見立てを記録した（同じテストが二度落ちたら直す）。 (learned 2026-10-15) \n- dslMixed の memory.peak が 94.4%・取り直し 93.1% で線（90% 未満）を超えた。peak の時の内訳は anon 68.3%・回収できるページキャッシュ 23.7% で、依頼者の決定「持ち越す」で設定を変えず Not Met のまま記録した。物差しを anon に替えると Met になるが、目標の測り方を後から緩めることになるため選ばなかった。 (learned 2026-10-15) \n- 1回目の使い捨てのアプリのログの WARN 43 行を、内訳を読む前に環境を消したため取り直せなかった（project.md の「確かめの結果を見てから片付ける」の漏れ）。memory.peak の取り直しの回では、消す前に WARN を数える指示を最初から書いた。 (learned 2026-10-15) \n- business-data-views の Delivery Planning の実測（約 13 分）で、全体の合計で判定している7パッケージのうち下限に足りないのは common.health（行 79.2%）だけで、audit.repository は数える行が無かった（全体の合計 行 98.0%・分岐 92.6%）。 (learned 2026-10-18) \n- U1・U2・U3 で捨ての試しを先に行い、前提が崩れた点（U1 は MySQL・MariaDB の重さ、応答しない MySQL の 50 秒、bit と空間の型。U2 は perf の MySQL の OOMKilled、PG の TEMP の権限、更新できないビュー、JDK 25 で重くならない式。U3 は測り終わりの時点、T3 が代わりの案にならない）を追加の質問（F）で依頼者に確かめてから文書に反映した。 (learned 2026-10-18) \n- U2 の Q7: A で perf の MySQL 1792m を短い捨ての試しで確かめ、プールを埋めた状態で上限に張りつき（anon 90%）、プール以外の量も見立ての約 614 MiB でなく約 840 MiB と分かったため、追加の質問 F1 で 2304m にした。U1 の文書の値は承認の場でそろえる。 (learned 2026-10-18)"
    }
  ],
  "obligations": {
    "strategy": "standard",
    "strategy_volume": [
      "Five to eight tests per component.",
      "Unit tests plus integration tests for key boundaries.",
      "Add E2E, performance, or security tests when requirements demand them."
    ],
    "scope_floor": [
      "Keep the existing test suite green.",
      "This scope adds no extra new-test floor beyond the selected test strategy."
    ],
    "combination_rule": "Apply every selected-strategy obligation and every scope-floor obligation; neither replaces the other, and a targeted scope regression may add the narrowest necessary test type beyond the strategy default."
  },
  "plan_profile": {
    "methodology": "test-after",
    "runner_step": "Verify the existing test runner/configuration and record the exact unit-scoped command.",
    "runner_ready_before_first_test": true,
    "testable_layers": [
      "Data model / database behavior",
      "Repository / data access",
      "Business logic",
      "API / endpoint",
      "Frontend behavior"
    ],
    "steps": [
      "Project structure and production configuration skeleton.",
      "Verify the existing test runner/configuration and record the exact unit-scoped command.",
      "Data model / database behavior - implement.",
      "Data model / database behavior - write and run its tests after implementation.",
      "Repository / data access - implement.",
      "Repository / data access - write and run its tests after implementation.",
      "Business logic - implement.",
      "Business logic - write and run its tests after implementation.",
      "API / endpoint - implement.",
      "API / endpoint - write and run its tests after implementation.",
      "Frontend behavior - implement.",
      "Frontend behavior - write and run its tests after implementation.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:51e889664b3e247205a3fc601835ef28134fa4e098f9c6eb3f4c667fcf787668",
  "contract_sha256": "sha256:7b5ef641649418f9e844285e996fba87207000bd5c245758d4323958b43707d3"
}
```

## 3. 依頼者の承認を得ること

計画の承認（Approve Plan）で、次の承認もあわせて得る。生成の途中で、ここに書いた範囲を超える変更が要るとわかったら、生成を止めて依頼者に諮る。

| # | 変更・操作 | 中身 |
|---|---|---|
| A1 | `aidlc/` の下の未コミットの変更の記録のコミット（Step 1） | 今の作業フォルダに `aidlc-state.md` と監査の記録の変更がある。ブランチを作る前に、依頼者の承認を得て `develop` に記録のコミットとして積む（`project.md` の Change Control の学び）。フックが書き続ける監査の記録の追記は、止めても無くならないため、統合の前（Step 18）にもう一度積む |
| A2 | 記録のループの空回し（Step 12。10節の D5: A） | 動いている配備したアプリのコンテナ `mastersmith-app-1` に `docker exec` で cgroup のファイルと `/app/data/mastersmith.mv.db` の大きさを5秒だけ読み、列の並びを確かめる。読むだけで、止めない・書かない。結果は `build/perf-results/u5-loop-dryrun/` に置き、コミットしない |
| A3 | 待ち合わせの口に、上限の時間を引数で渡す形を足す（4.6） | `logical-components.md` 4.1 の `armFor(Thread held, Thread releaser)` に、上限 `Duration waitLimit` を足して `armFor(Thread held, Thread releaser, Duration waitLimit)` にする。`audit/testsupport` が `role` の `TestRoleBarrier.WAIT_LIMIT` を知らずに済み、単体テストで短い上限を使えるため。`RoleConcurrencyIT` は `TestRoleBarrier.WAIT_LIMIT`（20 秒）を渡し、延ばさない |

本番のコードと配備物は、どの Step でも変えない。原因が本番のコード（`role`・`audit` の `src/main`、`frontend/src` のテストでないファイル）に及ぶと分かったときは、その時点で生成を止めて依頼者に諮り、直すと決まれば統合の前に E2E を流す（基盤の設計のレビューの R-06、team.md の Way of Working）。

## 4. 反映した決定（承認の場の決定と、設計の文書との差）

承認済みの設計の文書は書き換えず、計画で直す（`project.md` の学び）。NFR 設計と基盤の設計の承認の場は、各単位のレビューの Major を「文書を直さず、レビューの推奨の方向でコード生成の計画で直す」と決めた（`construction/nfr-design/memory.md` 2026-10-19T00:30:00Z、`construction/infrastructure-design/memory.md` 2026-10-19T06:00:00Z）。

### 4.1 機能設計の承認の場（`functional-spec.md` 8節）

| 指摘 | 直し方（この計画で決めたこと） | Step |
|---|---|---|
| R-01 止める相手 | 設計で直り済み（先の保存の要求のスレッドを `TestRoleBarrier` の `AFTER_LOCK` の印で特定する）。計画では、`Gate.arrivedThread()` を止める相手、`Signal.passedThread()` を放す相手に使う（NFR 設計の Q1: A で放す相手を狭めた形）。印で特定できない形が分かったときは、出来事の中身の後の値（主権限 READ）で特定する形に替え、記録に書く | 2・4・5 |
| R-02 試し方の前提 | 設計で直り済み。計画では 4.6 の「`PERF_DB_URL` が渡ること」と「`RETENTION_TIME` の渡し方」を確かめる | 9・13 |
| R-03 画面のテストの試しの形と「ちょうど 1」 | 試しの形は、`Probe` の既存の `useLayoutEffect`（描画の直後、同じ描画の `useEffect` より前に動く）の中で、最初の1回だけ効果の回数を写す形にする。最初の描画の `useLayoutEffect` は必ずその描画の `useEffect` より前に動くため、写した値は必ず 0 になり、直す前の確かめ方が必ず `expected 1 to be +0` で落ちる。待つ値は「ちょうど 1」とする。効果の依存の口の関数は、パスが変わらない限り同じ関数（`AdminForbiddenProvider.tsx` 120〜128 行の `useCallback`）で、テストの描画は `StrictMode` の外（`renderWithProviders.tsx`）のため、再び描いても効果は動かない。2 以上になれば口の関数が変わったことで、このテストが見つけたい誤りのため、「1 以上」に緩めない | 7・8 |
| R-04 入れ替えた順の場合の 409 `ROLE_BUSY` | 10節の D1: A。入れ替えた順の場合は、待ち合わせの結果の確かめを 409 の分岐より前に置き、409 は入れ替えが成り立たなかったとして失敗にする。自然の順の場合の 409 の分岐（BR1.3）は今のまま | 5 |
| R-05 自然の順の場合の書き方 | 10節の D2: A。自然の順も同じ待ち合わせで確実に作る（止める相手と放す相手を入れ替えるだけ） | 5 |

### 4.2 NFR 要件の承認の場とレビュー

出典: `construction/nfr-requirements/memory.md`（2026-10-18T10:30:00Z の「U5 R-01 は台本で PERF_DB_URL の形を確かめる（コード生成の計画）」）、レビュー `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/nfr-requirements/units/u5-carryover-tests/00d44a7a2452e6b7/1.json`。

| 指摘 | 直し方 | Step |
|---|---|---|
| R-01 `PERF_DB_URL` の素通し | 口を残したまま、確かめの関数1つで形を確かめる（4.3 の R-02 で規則を確定） | 9・10 |
| R-02 `Actor` の `toString` | `toString` を上書きし、単体テストで確かめる（NFR 設計の Q2: A） | 2・3 |
| R-03 `releasedByTimeout()`・`disarm()` は新しく足す口 | 足す口として作る。判定の口は4.6 の表のとおり | 2・3 |
| R-04 一時ファイルの一覧 | 4.3 の R-01・4.4 の R-05 で実物にそろえる | 13 |
| R-05 書かない範囲 | 記録のループに限る（S3）。ループは `/app/data/mastersmith.mv.db` の大きさだけを読み、`gc.log` と混ざらない | 11 |
| R-06 cgroup v2 の前提 | 始める前の確かめで、5つのキーと3つのファイルが読めなければ、その回を無効として記録し合否に使わない | 11・12 |

### 4.3 NFR 設計の承認の場（レビュー `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/nfr-design/units/u5-carryover-tests/df13e747868fc8fa/1.json`）

| 指摘 | 直し方（この計画で決めたこと） | Step |
|---|---|---|
| R-01 一時ファイルの一覧と `KEEP=1` | 一覧を実物（`perf/dsl-timing.sh` の読み取り）から作り直し、README の手順に書く。一時ディレクトリ: `app.env`・`targetdb.env`・`app-targetdb.env`（271 行）・`login.json`（153 行）・`ui.env`（155 行。`dslMixed` の手順で `PERF_USER_PASSWORD` を足す）・`auth.header`（178 行。アクセストークン）・`apply.json`（秘密ではない。前の適用の previewId）。結果の置き場（`OUT_DIR`）: `resp-*.body`・`busy-*.result`・`busy-*.stderr`（秘密ではない応答・状態コード・curl の標準エラー）と、この単位が足す記録（TSV・まとめ・`env.txt`）。`KEEP=1` は `dslMixed` の手順の前提のため認め、その間は秘密が一時ディレクトリに残ることを手順に書く。終わりに手で `down -v` と一時ディレクトリの削除を行い、消えたことを確かめる | 13 |
| R-02 `PERF_DB_URL` の確かめの矛盾と抜け | (1) `RETENTION_TIME` は接続先の URL の設定で渡せる（4.6 の H2 のソースの読み取り）ため、許す一覧から `INIT=SET RETENTION_TIME <整数>` を外す。これで「全体に空白を含まない」と矛盾しない（`security-design.md` S1.2 の「渡せると分かったら一覧から外す」のとおり）。(2) 区切りは `IFS` の `read -a` を使わず、文字列を `;` で1つずつ切り出して空の要素（`;;`・末尾の `;`・前の部分の直後の `;` だけ）を拒否する。同じ設定（`=` の前の名前）が2回出たら拒否する。(3) 確かめは `perf/dsl-timing.sh` の引数の確かめの直後、既存の環境の確かめ（106 行）と `mktemp`（116 行）の前の最上位で呼び、失敗したら `exit 1` で親を止める（サブシェルの中で呼ばない。`trap`（126 行）はまだ置かれていない） | 9・10 |
| R-03 `armFor` の初期化と `@Primary` の重なり | `armFor` の入口で、前の回の状態（止めた・放した・上限で放した・止める相手が来た）をすべて初めに戻す。`@AfterEach` でも `disarm()` を呼ぶ（何度呼んでもよい）。`@Primary` の重なりは 1.3 の表のとおり、起動の失敗で分かる | 2・3・5 |
| R-04 `heldArrived()` が偽のとき | どちらの場合も、`heldArrived()` が真で `releasedByTimeout()` が偽であることを確かめ、どちらかが外れたらテストを失敗にする。あわせて、2行の ID の順が、その場合に作った順になっていること（入れ替えた順なら「後: FULL」の行の ID が小さい）を確かめる。止める相手が保存以外の監査を先に書いて止まったときは、ID の順が作った順にならないため、この確かめで必ず失敗になる | 5 |
| R-05 失敗の文を組み立てる箇所 | 洗い出した結果: (a) `RoleConcurrencyIT` の `assertCode` は応答の本文（Problem Details。トークンを含まない）を出す。(b) #7 の確かめは監査の行の `detail`（前と後の権限の値）と件数だけを出す。(c) 待ち合わせと `TestRoleBarrier` の例外の文は、点・操作の区分・鍵（ロールの ID）・スレッドの名前だけを出す。(d) `Actor` は `toString` で伏せる。(e) 画面のテストの失敗の文は効果の回数と画面の文字だけ。`admin.token()` を失敗の文・`.as(...)` に渡す箇所を作らないことを Step 5 で読み合わせる | 2・5・8 |
| R-06 traceability の OK の意味 | 生成の後の `traceability.json` で、`NFR4.5`・`NFR4.7` は「台本と手順はコード生成、件数の確かめと設定の値の確かめは Build and Test」と target に書き分ける（7節） | 16 |

### 4.4 基盤の設計の承認の場（レビュー `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/infrastructure-design/units/u5-carryover-tests/4bcccff4724084fb/1.json`）

| 指摘 | 直し方（この計画で決めたこと） | Step |
|---|---|---|
| R-01 `memory.stat` の列の順 | 列の順を次に固定し、TSV の見出しの行に書く: `time_utc`・`memory_current`・`memory_peak`・`anon`・`file`・`inactive_file`・`active_file`・`kernel`・`mv_db_bytes`。`memory.stat` は awk の連想配列で名前を引いて、この順に出す（ファイルの並びに頼らない）。キーが無いときは空欄ではなく `NA` を出す。始める前の確かめで5つのキーがすべてあることを確かめる。Step 12 の空回しで、`anon` の列の値と `grep '^anon ' memory.stat` の値が同じ時刻に一致することを確かめる | 11・12 |
| R-02 試しの回の設定の値の確かめ方 | 1回の測りの終わりに、`memory.peak`・`memory.max`・`memory.stat` と状態を読んだ後で使い捨てのアプリを `docker compose stop app` で止め、README の既存の形（`perf/README.md` 38〜40 行の H2 の `Shell` を同じ JRE のイメージで動かす形）で、内部DB のボリュームを読み取り専用（`:ro`、`ACCESS_MODE_DATA=r`）で開き、`SELECT SETTING_VALUE FROM INFORMATION_SCHEMA.SETTINGS WHERE SETTING_NAME = 'RETENTION_TIME'` を読んで記録する。止めると peak が消えるため、順は必ず「peak と stat を読む → 止める → 設定を読む」。`RETENTION_TIME` は H2 の中に保存される設定（`Set.java` の `addOrUpdateSetting`）で、接続先の URL の設定は DB を開いた時点（アプリの起動）で当たるため、測りの後に読んだ値は測りの間に効いていた値と同じとみなす（BR4.1 の「起動の後」を、この理由で測り終わりの確かめで満たす）。既定の回も同じ形で読み、H2 の既定の値を並べて記録する | 11・13 |
| R-03 ループの秒の上限と止め方 | 上限の式は「前置き 20 秒 ＋ 場面の長さ 180 秒 ＋ 後置き 60 秒 ＋ 余裕 60 秒（k6 の起動と setup）＝ 320 秒」とし、引数で渡す。ループは上限で自分で終わる。手元の側は `docker exec` を後ろで動かし、その PID を変数に持ち、後置きの 60 秒の後に `kill` して `wait` する（macOS には標準の `timeout` が無いため使わない）。手元を止めてもコンテナの中のループは出力先が閉じて次の書き込みで終わり、次の回の `--force-recreate` でコンテナごと作り直されるため、回をまたいで残らない。k6 が遅れて後置きの 60 秒が上限の中に入りきらなかったときは、TSV の最後の行の時刻と k6 の終わりの時刻を比べて「後置きの欠け」を記録する。`memory.peak` は cgroup の値を最後に読むため欠けない | 11 |
| R-04 試しの回の作り方 | README の手順を「既定の環境を消す（`down -v` と一時ディレクトリの削除、消えたことの確かめ）→ `KEEP=1` と `PERF_DB_URL`（4.6 の試しの値。`RETENTION_TIME=0`）を付けて `perf/dsl-timing.sh postgres` で作る → アプリを止めて試験用の利用者 11 名を入れる → `ui.env` に `PERF_USER_PASSWORD` を足す → 2回流す」の順で書く。`perf/dsl-timing.sh` は既存の環境があると止まる（103〜106 行）ため、消してから作る。`OUT_DIR` は既定の環境と分ける（`build/perf-results/dsl-k6` と `build/perf-results/dsl-k6-retention`） | 13 |
| R-05 一時ファイルの一覧の抜け | `apply.json`（秘密ではない）を足し、結果の置き場の `resp-*.body`・`busy-*.result`・`busy-*.stderr` を「応答・状態コード・curl の標準エラー」として束ねて、検索の対象に入れる（4.3 の R-01 の一覧） | 13 |
| R-06 原因が本体に及んだとき | 3節の最後の段落のとおり、本体に及んだら止めて諮り、直すなら統合の前に E2E を流す。本体を変えない根拠は、監査の書き込みの時間を測る `LongSupplier` の差し替えの口（`AuditConfig` 37 行・`AuditEventListener` 412〜414 行）と、ロールの待ち合わせの口 `RoleBarrier` が既にあること | 3節・15 |
| R-07 持ち越しの確かめの閉じる条件 | 7節の表に、行ごとに「何を満たせば閉じるか」を1文ずつ書く。R の番号は「NFR 設計の R-」「基盤の設計の R-」のように段の名前を付けて書く | 7節・16 |

### 4.5 コード生成の段の Deviations（`construction/code-generation/memory.md`）

| 決定 | 反映した所 |
|---|---|
| コード生成は U1 → U4 → U5 → U2 → U3 の順。B1（U5）は U1・U4 の後 | 冒頭 |
| B1 が Bolt の計画の1番目から3番目に移ったため、B2・B3 の統合の後の CI で2件が落ちる危険が残る。落ちたら team.md の不安定なテストの決まりで扱う | Step 1 で、B2・B3 の統合の後の CI を試行ごと（`gh run view --json attempt,jobs`）に読み取り、2件のどちらかが落ちていたかを記録する（読むだけ。落ちていれば、二段の確かめの見立ての材料にする） |

### 4.6 計画で決めたこと（設計の文書が計画に回したもの）

| 論点 | 決めたこと | 出典 |
|---|---|---|
| 待ち合わせの名前と置き場 | `audit/testsupport/AuditWriteReorderBarrierConfig`（設定）と入れ子の `AuditWriteReorderBarrier`（待ち合わせ）。既存の `AuditWriteBarrierConfig` と同じ形 | `logical-components.md` 1節 |
| 待ち合わせの口 | `armFor(Thread held, Thread releaser, Duration waitLimit)`・`heldArrived()`・`releasedByTimeout()`・`disarm()`。3節の A3 のとおり上限を引数で受ける | `logical-components.md` 4.1 |
| 待ち合わせの振る舞い | 1回の書き込みで同じスレッドから2回呼ばれる（1回目が直前、2回目が終わり）。`held` の1回目でだけ止め、`releaser` の2回目でだけ放す。放す条件が先に満たされていたら止めずに通す（`heldArrived()` は真、`releasedByTimeout()` は偽）。上限で放したら `releasedByTimeout()` を真にする。準備していない間は止めずに通す。スレッドごとの呼び出しの回数は、準備の後に数え始める | `logical-components.md` 4.2 |
| 結果を待つ順 | 放す相手の応答を先に待ち、次に止める相手の応答を待つ（止めた側の上限と結果を待つ上限が重なって取り違えるのを避ける） | `logical-components.md` 4.3 の4 |
| `PERF_DB_URL` の受け付ける形 | 前の部分は `jdbc:h2:file:/app/data/mastersmith` ちょうど。後ろの `;` の設定は `DEFRAG_ALWAYS=TRUE` と `RETENTION_TIME=<整数>`（`^[0-9]{1,9}$`）だけ。空白・改行・タブ・`\`・`'`・`"` を含まない。空の要素と同じ設定の重なりを拒否する。空（未設定）のときは何もしない（既定の接続先）。止めたときは、どの決まりに外れたか（前の部分・使えない文字・空の要素・許さない設定・重なり）だけを標準エラーに出し、値は出さない | `security-design.md` S1.2、4.3 の R-02 |
| `RETENTION_TIME` の渡し方 | 接続先の URL の設定で渡す。H2 2.4.240 のソース（Gradle の手元の置き場の `h2-2.4.240-sources.jar` を読み取りで展開）で、URL の設定の名前は `SetTypes` の名前（`RETENTION_TIME` を含む）を受け付け（`ConnectionInfo` の `KNOWN_SETTINGS`・`readSettingsFromURL`）、`Set` の `RETENTION_TIME` は管理者の権限で値を DB に保存することを確かめた。アプリの接続の利用者は既定の `sa`（`application.yaml` 171 行）。そのため `INIT` は使わない。効いたことは Build and Test で 4.4 の R-02 の形で確かめる | 機能設計 4.1 の手順2、NFR4.7 |
| `PERF_DB_URL` が `dslMixed` の環境に渡ること | 読み取りで確かめた: `dslMixed` の手順は `KEEP=1 ... ./perf/dsl-timing.sh postgres`（README 171 行）で環境を作り、`PERF_DB_URL` は `perf/dsl-timing.sh` 144〜145 行で一時ディレクトリの `app.env` の `MASTERSMITH_DB_URL` に書かれ、`docker/perf/compose.yaml` の `env_file`（`MASTERSMITH_PERF_ENV_FILE`）で app に渡る。新しい上書きの口は足さない（`cicd-pipeline.md` 8節の差） | 機能設計 4.1 の手順1 |
| 1回の測りの台本の形 | 確かめの関数 `perf/lib/perf-db-url.sh`・試しの台本 `perf/lib/perf-db-url-test.sh`・1回の測り `perf/dsl-mixed-memory.sh` の別のファイルにする | 10節の D3: A |
| 試しの値 | 試しの2回の接続先は `PERF_DB_URL='jdbc:h2:file:/app/data/mastersmith;DEFRAG_ALWAYS=TRUE;RETENTION_TIME=0'`（`RETENTION_TIME=0`）。壊れうる点と取り込むときの値は、取り込みを諮るとき（BR4.2）にあらためて決める | 10節の D4: A |
| peak の時の内訳 | TSV の中で `memory_current` が最大の行を「peak の時」とし、その行の `anon`・`file`・`inactive_file`・`kernel` を並べる。`anon` の最大 / `memory.max`、`file` の始めと最大の差、`mv_db_bytes` の始めと最大の差もまとめに出す（BR3.2）。合否は `memory.peak / memory.max < 0.9` だけで見る（BR3.1） | 機能設計 3節 |
| 時刻の差 | 各回の始めと終わりに、手元の時刻（前）・VM の時刻（`docker exec ... date +%s`）・手元の時刻（後）を読み、範囲（VM−後、VM−前）を記録する（`project.md` の時計のずれの学び） | `cicd-pipeline.md` 4.3 |
| 二段の確かめの時間の上限 | どちらの1件も、起こせなかったときの再現の試みは 30 分まで（BR2.5）。確かめはファイルの単独の5回・全体1回（verify）に絞る（`project.md` の学び） | BR2.5 |

## 5. Step（実装 → その層のテスト → 実行）

どの Step も、コードのコメント・Javadoc・JSDoc は日本語、テストの説明文（`@DisplayName`・`describe`・`it`）は英語、ファイルの先頭に Apache License 2.0 のヘッダー（Java・TypeScript は `/* ... */`、シェルは既存の台本と同じ `#` の形。2026、agwlvssainokuni）を置く。Java は `record`・コンストラクターでの受け渡し、Lombok は使わない。テストのデータは架空の値だけにし、メールアドレスは予約のドメインだけにする。テストに `sleep` を入れず、`TestRoleBarrier.WAIT_LIMIT`（20 秒）・Vitest の既定の上限・`waitFor` の既定の上限は延ばさない（NFR7.3）。数分を超える実行（結合テストの全体・verify）は `caffeinate -dims` で包み、流す前に `pmset -g assertions`、流した後に `pmset -g log` で Sleep・DarkWake が無いことを確かめ、入っていたら結果を使わずに流し直す（`project.md` の学び）。`.env` は開かない。

### 準備

- [ ] **Step 1: 作業の準備と走らせ方の確かめ**
  - `aidlc/` の下の未コミットの変更を確かめ、あれば依頼者の承認（3節の A1）を得て `develop` に記録のコミットとして積む。
  - git の対象外の生成物（`frontend/playwright-report`・`frontend/test-results`）が残っていれば、ここで消す。段の途中では消さない（`project.md` の学び）。
  - B2・B3 の統合の後の CI を試行ごとに読み取りで確かめ、`RoleConcurrencyIT` と `AdminForbiddenProvider.test.tsx` が落ちた試行があったかを記録する（4.5）。
  - `develop` から `feature/261016-business-data-views-b1` を作って移る。
  - 走らせ方の確かめ（brownfield。最初のテストの Step の前）: `unit-test-instructions.md` の 3.1 のコマンドで、既存の `RoleConcurrencyIT`（4件）・`LargeRoleTransferYamlTest`・`AdminForbiddenProvider.test.tsx`（12件）が今の形で通ることを確かめ、件数を記録する。

### テストの補助の層（BR1.5、NFR7.1・NFR7.2、S4）

- [ ] **Step 2: テストの補助を書く**
  - `TestRoleBarrier`: `Gate` に `volatile Thread arrivedThread` を足し、`pass` の中で `gate.arrived.countDown()` の直前に記録する。口 `arrivedThread()`（来ていなければ `null`）。`Signal` に `passedThread` を足し、最初に点を通ったスレッドだけを記録する（`compareAndSet` で1回だけ）。口 `passedThread()`。止める・通すの振る舞いと既存の口は変えない。
  - `AuditWriteReorderBarrierConfig` と `AuditWriteReorderBarrier`: 4.6 の口と振る舞いのとおり。状態は1つのロックで守るか、`CountDownLatch` と `AtomicInteger`・`AtomicBoolean` で持ち、止めたスレッドと放す相手は別々の値で持つ。`armFor` は前の回の状態を初めに戻す。`disarm()` は止めているスレッドを放し、何度呼んでもよい。止めている間に割り込まれたら割り込みの印を戻して先へ進ませる。例外の文には、スレッドの名前と上限の値だけを出す。`LongSupplier` は待ち合わせを通した後に `System.nanoTime()` を返す。
  - `RoleActors.Actor`: `toString` を `Actor[userId=<数>, email=***, token=***]` にする（`security-design.md` S4.2）。
- [ ] **Step 3: テストの補助の単体テストを書いて流す**
  - `AuditWriteReorderBarrierTest`（7件）: 準備していない間は止めない／`held` の1回目で止まり、`releaser` の2回目で放され、`heldArrived` が真・`releasedByTimeout` が偽／`releaser` の1回目では放されない／ほかのスレッドの呼び出しでは放されない／放す条件が先に満たされていたら止めずに通す／上限（単体テストでは 200 ミリ秒）で放したら `releasedByTimeout` が真／`disarm` で止めているスレッドが放され、`armFor` で前の回の状態が消える。スレッドの待ち合わせは `CountDownLatch` で作り、`sleep` で順を作らない。上限の場合だけは経過の時間で決まるが、結果（放されたこと・印が立つこと）は決まっている。
  - `TestRoleBarrierTest`（3件）: `Gate` の `arrivedThread` が止まったスレッド／`Signal` の `passedThread` が最初に通ったスレッドで、2回目に通ったスレッドで変わらない／来る前は `null`。
  - `RoleActorsTest`（2件）: `toString` にメールアドレスとトークンの値が含まれず、`userId` が含まれる／`email()`・`token()` は値をそのまま返す。
  - `unit-test-instructions.md` 3.2 の「テストの補助」のコマンドで流し、通ることを確かめる。

### #7 の直しと二段の確かめ（BR1.1〜BR1.6、FR15.1・FR15.2）

- [ ] **Step 4: 入れ替えた順の場合を足し、直す前の確かめ方で落ちることを確かめる（二段の1段目）**
  - `RoleConcurrencyIT` に `@Import(AuditWriteReorderBarrierConfig.class)` を足し、`@AfterEach` で `reorderBarrier.disarm()` を `barrier.reset()` の前に呼ぶ（テストが落ちても止めたスレッドを必ず放す）。
  - #7 の本体を手伝いのメソッドに移し、入れ替えた順の場合を足す。流れは `logical-components.md` 4.3 のとおり: 先の保存を `first.awaitArrival()` まで進め、後の保存を `second.awaitPassed()` まで進め、`reorderBarrier.armFor(first.arrivedThread(), second.passedThread(), TestRoleBarrier.WAIT_LIMIT)` の後に `first.release()`。後の保存の結果を先に待ち、次に先の保存の結果を待つ。
  - この時点では、確かめ方を **直す前の形（行の ID の順に1行目・2行目を決め打ち）のまま** にして入れ替えた順の場合を流し、落ちることと失敗の文（`detail` の値だけが出る）を記録する。あわせて、待ち合わせが止めて放したこと（`heldArrived` 真・`releasedByTimeout` 偽）と、「後: FULL」の行の ID が小さいことを記録する。この状態はコミットしない。
  - 入れ替えが起きなかったとき（上限で放された・ID の順が変わらない）は、Step 6 の 30 分の上限の中で試み、範囲と見立てを記録する（BR1.6・BR2.5）。止める相手を印で特定できないと分かったときは、出来事の中身の後の値（主権限 READ）で特定する形に替え、替えたことを記録する（BR1.5）。
- [ ] **Step 5: 確かめ方を直し、2つの場合を確かめる（二段の2段目）**
  - 確かめ方を BR1.1・BR1.2 の形にする: 対象のロールの `ROLE_PERMISSION_CHANGED` の行がちょうど2行で、「前が空・後が READ」と「前が READ・後が FULL」がちょうど1行ずつ。最後の主権限が FULL。行の ID の順は合否に使わない。失敗の文には見つかった行の `detail` だけを出す。
  - 場合ごとの前提の確かめ（BR1.1 の判定とは別。待ち合わせが意図した順を作れたかの確かめ）: `heldArrived()` が真、`releasedByTimeout()` が偽、2行の ID の順がその場合に作った順。どれかが外れたらテストを失敗にする（4.3 の R-04）。
  - 409 の扱い（10節の D1: A）: 自然の順の場合は今の分岐（`ROLE_BUSY` の code だけを確かめる。BR1.3）のまま。入れ替えた順の場合は、待ち合わせの結果の確かめ（`heldArrived` が真・`releasedByTimeout` が偽）を 409 の分岐より前に置き、409 は入れ替えが成り立たなかったとしてテストを失敗にする（合格に数えない）。409 で落ちたときは team.md の不安定なテストの決まりで扱う。
  - 自然の順の場合の書き方（10節の D2: A）: 止める相手を `second.passedThread()`、放す相手を `first.arrivedThread()` にして、同じ待ち合わせで確実に作る。2行の ID の順は「後: READ」の行が小さいことを確かめる。
  - 2つの場合を別のテストのメソッドにし（`@DisplayName` は英語。例 `#7 ... in the natural audit order`・`#7 ... with the audit writes reordered`）、毎回流す（BR1.5、機能設計の Q2: A）。#1・#4 は変えない。
  - `admin.token()` を失敗の文・`.as(...)` に渡す箇所を作っていないことを読み合わせる（4.3 の R-05）。
  - `unit-test-instructions.md` 3.2 の「#7」のコマンドで、`RoleConcurrencyIT` の5件（#7 の2件と #1・#4 の3件）が通ることを記録する。
- [ ] **Step 6: 繰り返しの確かめ**
  - `RoleConcurrencyIT` を単独で5回流し（`--rerun`）、5回とも通ることを記録する。落ちたときは結果を使わず、原因を確かめてから直す（team.md の「手元で再現した不安定なテストは原因を直すまで統合しない」）。
  - Step 4 で入れ替えを起こせなかったときの試みは、この Step と合わせて 30 分を上限にし、回数と負荷の掛け方と見立てを記録する。

### 画面のテストの直しと二段の確かめ（BR2.1〜BR2.5、FR15.1・FR15.2）

- [ ] **Step 7: 試し（作業フォルダの中だけ。コミットしない。BR2.4）**
  - 試し T1: `Probe` の既存の `useLayoutEffect` に「最初の1回だけ、その時点の `effectRuns` をファイルの中の変数に写す」行を足し（`beforeEach` で初めに戻す）、対象のテストの写す値をその変数に替える。確かめ方は直す前の形（`expect(effectRuns).toBe(runsBefore)`）のまま流し、`expected 1 to be +0` で落ちることを、単独で5回流して5回とも落ちることで記録する（4.1 の R-03 の理由で、写す値は必ず 0）。
  - 試し T2: T1 の `Probe` の変更を残したまま、写す前に `await waitFor(() => expect(effectRuns).toBe(1))` で待ってから `effectRuns` を写す形にし、単独で5回流して5回とも通ることを記録する。
  - 試しの変更をすべて戻し、`git diff` でこのファイルに差が無いことを確かめて記録する。
  - T1 で確実に落とせなかったときは、30 分を上限に試み（くり返し・負荷の下での実行）、範囲と見立てを記録する（BR2.5）。
- [ ] **Step 8: 本番のテストを直して流す**
  - 対象の1件だけを、`await screen.findByTestId('probe')` の後に `await waitFor(() => expect(effectRuns).toBe(1))` を置き、その後に `before`・`runsBefore` を写す形にする。403 を知らせた後の確かめ（画面が「forbidden」、口の関数が同じ、効果の回数が写した値から変わらない）は保つ（BR2.2）。時間の上限は延ばさない（BR2.3）。
  - `unit-test-instructions.md` 3.2 の「画面のテスト」のコマンドで、ファイルの 12 件を単独で5回流し、5回とも通ることを記録する。

### `perf/` の台本（BR3.1〜BR4.3、NFR4.5〜NFR4.9、S1〜S3）

- [ ] **Step 9: `PERF_DB_URL` の確かめの関数**
  - 4.6 の受け付ける形のとおりに関数 `check_perf_db_url` を `perf/lib/perf-db-url.sh` に書く（10節の D3: A。`perf/dsl-timing.sh` が `source` で読む）。区切りは `;` で1つずつ切り出し、空の要素と同じ名前の重なりを拒否する。止めたときは標準エラーに決まりの名前だけを出し、`return 1` する。
  - `perf/dsl-timing.sh` の引数の確かめ（91〜105 行）の直後、既存の環境の確かめ（106 行）の前の最上位で `if [ -n "${PERF_DB_URL:-}" ] && ! check_perf_db_url "${PERF_DB_URL}"; then exit 1; fi` の形で呼ぶ。冒頭の注記（45 行）に、受け付ける形と、外れたら起動の前に止まることを書き足す。144〜145 行の書き込みは変えない。
- [ ] **Step 10: 確かめの関数の試し（S1.3）**
  - 悪い入力: `INIT=RUNSCRIPT FROM 'x'` を付けたもの・`INIT=SET RETENTION_TIME 0`（一覧から外したため拒否）・`;;` を含むもの・末尾の `;`・前の部分の直後の `;` だけ・`jdbc:h2:tcp:` の前の部分・`jdbc:postgresql:` の外部の URL・`jdbc:h2:file:/app/data/mastersmith2`（前の部分の後ろに文字が続く）・`RETENTION_TIME=abc`・`RETENTION_TIME=1234567890`（10 桁）・`RETENTION_TIME=-1`・同じ設定の2回・改行を含む値・空白を含む値・小文字の `retention_time=0`。正しい入力: 設定なし・`DEFRAG_ALWAYS=TRUE` だけ・`DEFRAG_ALWAYS=TRUE;RETENTION_TIME=0`・`RETENTION_TIME=45000;DEFRAG_ALWAYS=TRUE`。
  - 件ごとに、止めたか通したかと期待との一致を記録する。止めたときの標準エラーに入力の値が出ていないことを、入力の中の目印の語（例 `RUNSCRIPT`・`tcp`・`postgresql`）で数えて 0 件であることを確かめる。試しは `perf/lib/perf-db-url-test.sh` に入力の表として書いて流す（D3: A。いつでも流し直せる）。
  - 親が止まることの確かめ: 悪い入力を付けて `perf/dsl-timing.sh postgres` を流し、終了コードが 1 で、ホームの下に新しい `.mastersmith-perf-*` の一時ディレクトリが作られていないこと（流す前と後の一覧の差）を記録する。この呼び出しは確かめが既存の環境とイメージの確かめより前に止まるため、Docker を使わない。
- [ ] **Step 11: 記録のループと1回の測りの台本**
  - 記録のループ（4.4 の R-01・R-03、S3）: `docker exec <アプリのコンテナ> bash -c '<ループ>' _ <上限の秒>` を1回だけ起こす。`--privileged`・`--user`・追加の権限は付けない。ループは `/sys/fs/cgroup/memory.current`・`memory.peak`・`memory.stat`（連想配列で5つのキーを固定の順に出す、無ければ `NA`）と `stat -c %s /app/data/mastersmith.mv.db` を1秒ごとに読み、`date -u +%FT%TZ` の時刻と一緒に1行を書く。書き先はホームの側の結果の置き場だけ。見出しの行は手元の側で書く。
  - 始める前の確かめ: `memory.peak`・`memory.current`・`memory.max` が読めて、`memory.stat` に5つのキーがすべてあり、`mastersmith.mv.db` があること。どれかが外れたら、その回を無効として `invalid.txt` に理由（ファイル・キーの名前）を書き、測りを始めない。
  - 1回の測り（D3: A。`perf/dsl-mixed-memory.sh <一時ディレクトリ> <回の名前> <DSL の置き場>`）: 機能設計 3節の8手順のうち、作り直し（`--force-recreate app`）・始める前の確かめ・時刻の差（始め）・ループを後ろで起こす（PID を持つ）・20 秒後に `dslMixed`（VUS 10・`DURATION=3m`・10 MiB の DSL。k6 のイメージと引数は README の `k6d` と同じ）・終わりの後 60 秒・ループを止める（`kill` と `wait`）・時刻の差（終わり）・`memory.peak` と `memory.max` と状態（OOMKilled・終了コード）を読む・まとめ（4.6 の peak の時の内訳）を `summary.txt` に書く・WARN の件数とロガーごとの内訳・アプリを止めて設定の値（`RETENTION_TIME`）を読む（4.4 の R-02）までを1つにする。配備したアプリを止める・起動し直す、`caffeinate -dims`、`pmset` の確かめ、環境の作り分け、片付けは README の手順で行い、台本の中では行わない（1回分だけを受け持つ）。
  - 台本は compose を `-f docker/perf/compose.yaml` で呼ぶため、`MASTERSMITH_PERF_ENV_FILE`・`MASTERSMITH_PERF_APP_TARGETDB_ENV_FILE`（`dsl-timing.sh` が作った一時ディレクトリの `app.env`・`app-targetdb.env`）・`MASTERSMITH_IMAGE_TAG`・`MASTERSMITH_CONTAINER_CPUS=4`・`MASTERSMITH_CONTAINER_MEMORY=2g` をシェルの環境変数で渡す。指標の公開の範囲は `dsl-timing.sh` が `app.env` に書いた値のまま（NFR4.8）。
  - 新しい記録に書くのは数値と時刻と状態だけにする（NFR4.6）。`env.txt` は `dsl-timing.sh` が書いたものを使い、台本は秘密を読まない（k6 には `--env-file` で `ui.env` を渡すだけ）。
- [ ] **Step 12: 台本の静かな確かめと、記録のループの空回し**
  - `bash -n` で `perf/dsl-timing.sh` と新しい3つの台本の構文を確かめる（`shellcheck`・`shfmt` はこの PC に無いため使わない）。
  - 10節の D5: A と3節の A2 のとおり、記録のループだけを配備したアプリのコンテナ `mastersmith-app-1` に向けて上限 5 秒で流し、見出しの行と5行前後が書かれること、どの列にも `NA` が無いこと、`anon` の列の値が同じ時刻の `grep '^anon ' /sys/fs/cgroup/memory.stat` の値と一致すること（列の取り違えが無いこと）、終わった後に `docker exec` の処理が残っていないこと（`docker top mastersmith-app-1` に `sleep 1` のループが無い）を記録する。結果の置き場は `build/perf-results/u5-loop-dryrun/` で、コミットしない。
  - `dslMixed` を含む1回の測りの全体は、配備したアプリを止める必要があるため、この段では流さない（Build and Test。7節）。
- [ ] **Step 13: `perf/README.md` の手順**
  - (b) 節に「`dslMixed` の `memory.peak` と `memory.stat` の記録（Intent 261016-business-data-views の U5）」を足す。中身: 合否と並べる値（BR3.1・BR3.2・BR3.5）、記録の列と読み方（4.4 の R-01 の列）、環境の分け方（`cicd-pipeline.md` 4.1、基盤の設計の Q1: A）、試しの回の作り方の順（4.4 の R-04。接続先は 4.6 の試しの値 `RETENTION_TIME=0`、10節の D4: A）、各回の前後の確かめ（作り直し・`caffeinate -dims` で台本の全体を包む・流す前の `pmset -g assertions`・流した後の `pmset -g log`・WARN を数えてから片付ける・配備したアプリは測る間だけ止め、4回が終わったら起動し直す）、設定の値の確かめ（4.4 の R-02）、一時ファイルの一覧と `KEEP=1` の間は秘密が残ること（4.3 の R-01・4.4 の R-05）、秘密の検索（今回の `OUT_DIR` だけを対象に、仮の管理者のパスワードと署名鍵の値で検索して件数だけを出す。`env.txt` と TSV に `password`・`PASSWORD`・`jdbc:mysql`・`jdbc:mariadb`・`jdbc:postgresql` が無いことを件数で見る。NFR4.5・NFR4.6）、片付けと消えたことの確かめ、コンテナの上限 2g を上げないこと（BR4.3）。
  - DSL の節の `PERF_DB_URL` の行（152 行）に、受け付ける形と、外れたら起動の前に止まることを書き足す。
  - 書いたコマンドのうち、ファイルの名前・道・環境変数の名前（`k6d`・`ui.env`・`PERF_USER_PASSWORD`・`perf-data` のボリューム・H2 の `Shell` の形）を、既存の README と台本の行で照らして確かめる。

### 検査

- [ ] **Step 14: 1コマンドの検査**
  - `unit-test-instructions.md` 2節の準備（colima の確かめと2つの環境変数）をしてから、`caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を流し、通ること、対象DB のテストが飛んでいない（WARN が無い）こと、テストの件数（バックエンドの単体・結合、フロントエンド）、全体のカバレッジ、`verify` の時間を記録する（team.md の Testing Posture、`project.md` の学び）。
  - 落ちたときは、この単位の変更の経路に触れるかを見立て、team.md の「不安定なテストと CI の失敗」の決まりで扱う。計画の外の失敗で直す必要があるときは、止めて依頼者に諮る。
- [ ] **Step 15: 変えていないことの確かめ**
  - `git diff --name-only develop...` で、変えたファイルが 1.1・1.2 の表のものだけであることを確かめる。`backend/src/main`・`frontend/src` のテストでないファイル・`application.yaml`・`Dockerfile`・`compose.yaml`・`docker/`・`.env.example`・`build.gradle.kts`・`backend/build.gradle.kts`・`frontend/vitest.config.ts`・lockfile に差が無いことを記録する（NFR4.8、TS1）。差があれば止めて諮る（3節の最後の段落）。
  - 新しいファイルの先頭にライセンスのヘッダーがあることを確かめる（`perf/` のシェルは verify の検査の対象の外のため、目で確かめる）。

### 記録

- [ ] **Step 16: 記録**
  - `aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/code-generation/generation-notes.md` に、Step 1〜15 の結果を書く: 走らせ方の確かめの件数、4.5 の CI の読み取り、二段の確かめ（#7 の直す前の失敗の文と止めて放した記録・直した後の2つの場合の結果・5回の繰り返し、画面のテストの T1・T2 の結果と戻したことの差）、止める相手の特定の仕方（印で特定できたか）、`PERF_DB_URL` の試しの表、空回しの結果、verify の件数と時間とカバレッジ、変えていないことの確かめ。計画に無い変更は、計画を書き換えずにここへ書く（`project.md` の学び）。
  - 同じ内容を `aidlc/spaces/default/intents/261016-business-data-views/construction/code-generation/generation-notes.md` の B1 の節に写す（B3 と同じ形）。
  - Build and Test へ引き継ぐこと（7節の表）を同じ記録に書く。

### コミットと統合

- [ ] **Step 17: コミットの提案と積み（依頼者の承認）**
  - 生成の後に、8節の区切りで提案し、依頼者の承認を得てから積む。コミットの提案は、確かめたい点の問いとは別に、積む前に必ず尋ねる（`project.md` の学び）。
  - 区切りごとに、そのコミットの時点でコンパイルと型の検査が通ることを確かめる。積んだ後のコミットごとに、後のコミットの変更を退けた状態で `./gradlew :backend:compileTestJava`（C1・C2）と `(cd frontend && npm run typecheck)`（C3）を流す。
  - コード生成の段の承認の場は、最後のコードのコミットの後、`aidlc/` の下だけの記録のコミットを積む前に開く（`project.md` の学び）。
- [ ] **Step 18: 統合（依頼者の承認の後）**
  - 統合の前に、`aidlc/` の未コミットの変更（監査ログを含む）を、依頼者の承認を得て作業ブランチの上の記録のコミットとして積む（`project.md` の学び）。
  - `develop` が作業ブランチを作った後に進んでいれば、依頼者に諮ってから作業ブランチを `develop` の上に積み直し、Step 14 を流し直す。
  - Step 14 の verify が、統合する状態と同じ作業フォルダで通っていることを確かめ、`git checkout develop && git merge --squash feature/261016-business-data-views-b1` の後に、依頼者の承認を得た日本語の件名で1つのコミットにする。統合を終えたブランチは消す。
  - `origin` への push は依頼者が行う。CI の結果は、push の後に試行ごと（`gh run view --json attempt,jobs`）に確かめ、CI の時間を記録する。CI で2件のどちらかが落ちたら、team.md の「同じテストが二度目に落ちたら、原因を直すまで次へ進まない」に当たるかを、前の Intent の失敗と合わせて数えて扱う。

## 6. 要件と決まりから Step への対応

U5 はストーリーを持たない（`unit-of-work-story-map.md`）。要件と決まりから Step へ対応させる。

| 要件 | 決まり・NFR | Step |
|---|---|---|
| FR15.1 `RoleConcurrencyIT` の #7 を直す | BR1.1・BR1.2・BR1.3・BR1.4 | 4・5 |
| FR15.1 `AdminForbiddenProvider.test.tsx` の1件を直す | BR2.1・BR2.2・BR2.3 | 8 |
| FR15.2 二段の確かめ | BR1.5・BR1.6・BR2.4・BR2.5 | 2〜8・16 |
| FR16.1 物差しを変えない | BR3.1 | 11・13 |
| FR16.2 `anon` の最大を並べる | BR3.2・BR3.3 | 11・12・13 |
| FR16.3 ページキャッシュを減らす手を試す | BR4.1（台本と手順、渡し方の確かめ）・BR4.2・BR4.3（Build and Test で諮る） | 9・10・11・13 |
| FR16.4 合否 | BR3.4・BR3.5 | 13（測りは Build and Test） |
| 横断（使い捨ての環境のセキュリティ） | NFR4.5〜NFR4.9、S1〜S3 | 9〜13・15 |
| 横断（テストの品質） | NFR7.1〜NFR7.3、S4 | 2〜8・14 |

## 7. traceability の見込みと Build and Test への引き継ぎ

生成の後に作る `traceability.json`（段の定義の Step 5）は、次の扱いにする。行ごとに、閉じる条件を書く（基盤の設計のレビューの R-07）。

| ID | 扱い | 対象と閉じる条件 |
|---|---|---|
| BR1.1〜BR1.6 | OK | `RoleConcurrencyIT`・`AuditWriteReorderBarrierConfig`・`TestRoleBarrier`。閉じる条件: Step 4〜6 の二段の記録（直す前の失敗・直した後の2つの場合の通過・5回の繰り返し）が `generation-notes.md` にある |
| BR2.1〜BR2.5 | OK | `AdminForbiddenProvider.test.tsx`。閉じる条件: Step 7・8 の T1・T2 と5回の繰り返しの記録、戻したことの差 |
| BR3.1〜BR3.3 | OK（台本と手順） | `perf/dsl-mixed-memory.sh`・`perf/README.md`。閉じる条件: Step 12 の空回しで列が正しいこと |
| BR3.4・BR3.5 | Deferred | Build and Test（4回の測りと判定） |
| BR4.1 | OK（部分） | 渡し方の確かめ（4.6）と台本の口。閉じる条件: Step 10 の試しの表。効いたことの確かめは Build and Test |
| BR4.2・BR4.3 | Deferred | Build and Test（結果の時点で依頼者に諮る） |
| NFR4.5 | OK（部分） | 一時ファイルの一覧と手順。件数の検索は Build and Test |
| NFR4.6 | OK（部分） | 記録の列。`env.txt` と TSV の件数の確かめは Build and Test |
| NFR4.7 | OK（部分） | 確かめの関数（`INIT` を一覧から外す）。設定の値の確かめは Build and Test |
| NFR4.8・NFR4.9 | OK | Step 11・15 |
| NFR7.1〜NFR7.3 | OK | Step 2〜8・14 |
| FR16.1〜FR16.4 | Deferred（部分） | 測りは Build and Test。台本と手順は B1 で OK |

Build and Test へ引き継ぐこと:

- `dslMixed` の `memory.peak` の測りそのもの（既定の2回・試しの2回。`perf/README.md` の (b) 節の手順）。判定は既定の2回で `memory.peak / memory.max < 0.9` が2回とも成り立てば Met（BR3.5）。`anon` の最大と内訳は並べるだけで、物差しは変えない（team.md の Testing Posture）。
- 試しの2回の設定の値の確かめ（4.4 の R-02）。確かめていない回は試しの結果に使わない（BR4.1）。効いた・効かないの結果で依頼者に諮る（BR4.2・BR4.3）。
- 秘密の検索の件数（NFR4.5・NFR4.6）と、片付けで一時ディレクトリが消えたことの確かめ。
- 配備したアプリを止める時間を、U1・U2 の k6 とまとめて1回に寄せられるかの並べ方（`cicd-pipeline.md` 10節）。
- E2E の全件（Intent の最後。team.md の Way of Working）。
- B1 の統合の後の CI の試行ごとの結果と、verify と CI の時間（42 分の判定は U1・U2 の結合テストの増えと合わせて見る）。

## 8. コミットの区切り

場所ごとに区切り、どのコミットの時点でもコンパイルと型の検査が通る順にする（`project.md` の学び）。使われる側（テストの補助）を先に積む。

| # | 中身 | コンパイルが通る理由 |
|---|---|---|
| C1 | テストの補助: `TestRoleBarrier` のスレッドの記録、`AuditWriteReorderBarrierConfig`、`RoleActors.Actor` の `toString`、その単体テスト3ファイル | 足すだけで、既存の口を変えない |
| C2 | `RoleConcurrencyIT` の #7 の2つの場合（再現の場合と直した確かめ方を同じコミットに入れる。`project.md` の Mandated） | C1 の口だけを使う |
| C3 | `AdminForbiddenProvider.test.tsx` の直し | フロントエンドのテストのファイルだけ |
| C4 | `perf/` の台本（確かめの関数・試しの台本・1回の測り、`dsl-timing.sh` の呼び出し）と `perf/README.md` | Java と TypeScript に触れない |

- Step 4 の直す前の確かめ方と Step 7 の試しの変更は、どのコミットにも入れない。
- コミットのメッセージは日本語で、件名で中身が分かる形にする（例「監査の書き込みの順を入れ替える待ち合わせをテストの補助に足す」）。各コミットの末尾に共著者の行を付ける。
- `develop` へは squash で1つのコミットにする（9節）。件名の例「不安定なテスト2件を直し、memory.peak の測り方の台本を足す（Intent 261016-business-data-views、B1）」。

## 9. 統合

| 項目 | 扱い |
|---|---|
| ブランチ | `feature/261016-business-data-views-b1`（`develop` から作る。worktree は使わない） |
| 統合の形 | squash（team.md の Way of Working。サブモジュールの固定先を変えないため fast-forward の例外に当たらない） |
| 統合の前の関門 | `caffeinate -dims` で包んだ `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（コンテナの実行環境あり、対象DB のテストが飛んでいない）。E2E は流さない: 変えるのはテストのファイルと `perf/` だけで、team.md の「テストだけ・文書だけのブランチは verify だけ」に当たる（基盤の設計の `cicd-pipeline.md` 3節）。Step 15 で本体に差が無いことを確かめる。原因が本体に及んで本体を変えたときは、統合の前に E2E を流す |
| push | 依頼者が行う。AI は push しない |
| 統合の後 | ブランチを消す。CI の結果を試行ごとに記録する |

## 10. 依頼者の決定（D1〜D5: A）

計画の承認の前に、判断が分かれる5点を依頼者に尋ね、5つとも推奨の A に決まった（答えの記録は `code-generation-questions.md`）。決定は本文の次の所に反映した。

| 決定 | 中身 | 反映した所 |
|---|---|---|
| D1: A | 入れ替えた順の場合は、待ち合わせの結果の確かめを 409 の分岐より前に置き、409 は入れ替えが成り立たなかったとしてテストを失敗にする。自然の順の場合の 409 の分岐（BR1.3）は今のまま | 4.1 の R-04、Step 5、`unit-test-instructions.md` 4.2 |
| D2: A | 自然の順も同じ待ち合わせで確実に作る（止める相手を後の保存のスレッド、放す相手を先の保存のスレッドにする） | 4.1 の R-05、Step 5、`unit-test-instructions.md` 4.2 |
| D3: A | 確かめの関数 `perf/lib/perf-db-url.sh`・その試しの台本 `perf/lib/perf-db-url-test.sh`・1回の測り `perf/dsl-mixed-memory.sh` の別のファイルにする | 1.1・1.2、4.6、Step 9〜12、8節の C4、`unit-test-instructions.md` 1・3・4.3 |
| D4: A | 試しの2回の `RETENTION_TIME` は `0` | 4.6、Step 13 |
| D5: A | この段で、記録のループだけを配備したアプリのコンテナ `mastersmith-app-1` に向けて 5 秒流し、列が正しいことを確かめる（読むだけ） | 3節の A2、Step 12、Assumptions |

尋ねたときの選択肢は、記録のため次に残す。

### D1. 入れ替えた順の場合に、後の保存が 409 `ROLE_BUSY` で返ったときの扱い（機能設計のレビューの R-04）

入れ替えた順の場合、後の保存は先の保存の確定を待ってから排他を得るため、409 は起こりにくいものの、負荷の高い CI では待ちが 3 秒を超えて 409 になりえます。409 のときは後の保存の監査が書かれず、止めた先の保存は上限（20 秒）で放されます。

A. 入れ替えた順の場合は、待ち合わせの結果の確かめ（`heldArrived` が真・`releasedByTimeout` が偽）を 409 の分岐より前に置き、409 は「入れ替えが成り立たなかった」としてテストを失敗にする。合格に数えない形で BR1.5 とそろう。409 で落ちたときは team.md の不安定なテストの決まりで扱う（推奨）

B. 入れ替えた順の場合も、409 なら自然の順と同じく `ROLE_BUSY` の code だけを確かめて通す。CI で落ちにくいが、その回は入れ替えを確かめないまま合格に数える（止めた先の保存の上限の 20 秒を待ってから終わる）

X. Other (please specify)

### D2. 自然の順の場合の作り方（機能設計のレビューの R-05）

今の #7 は、2行が自然の順（先の保存の監査が先）になるかを運に任せています。

A. 自然の順も同じ待ち合わせで確実に作る。止める相手を後の保存のスレッド、放す相手を先の保存のスレッドにするだけで、2つの場合のどちらも毎回同じ順になり、二段の2段目（直した確かめ方が両方の順で通る）を毎回確かめられる（推奨）

B. 自然の順の場合は待ち合わせを準備せず、順を問わない（BR1.1 の趣旨のまま）。どちらの順になったかは ID の順で記録だけする。書くことは少ないが、自然の順で通ったことは運に任せる

X. Other (please specify)

### D3. `perf/` の台本の形

確かめの関数、その試し、記録のループを含む1回の測りを、どこに置くかを決めます。

A. 別のファイルにする: 確かめの関数 `perf/lib/perf-db-url.sh`（`dsl-timing.sh` が読む）と、その試しの台本 `perf/lib/perf-db-url-test.sh`、1回の測り `perf/dsl-mixed-memory.sh`。悪い入力の試しをいつでも流し直せ、測りの手順を README から1行で呼べる。ファイルが3つ増える（推奨）

B. 増やさない: 確かめの関数は `dsl-timing.sh` の中に置き、試しはこの段で一度だけ手で流して記録する。記録のループと1回の測りは、README の既存の節と同じく、手順の中で一時ディレクトリに台本を書き出す形（ヒアドキュメント）にする

X. Other (please specify)

### D4. 試しの回の `RETENTION_TIME` の値

H2 の `RETENTION_TIME` は古いデータを残す時間（ミリ秒）で、既定は H2 の既定の値（Build and Test で読んで記録する）です。短いほど空いた場所を早く使い直し、ファイルの伸びを抑える見込みです。H2 の資料では、短くすると、ファイルシステムが書き込みを書き終える前に PC が止まったときに壊れうるとされています。試しは使い捨ての環境だけで行い、配備物は変えません（取り込むかは結果の時点で諮る。BR4.2）。

A. `0`（できるだけ早く使い直す）。効くかどうかを最も大きく見られる。0 でも効かなければ、ほかの値でも効かないと読める。取り込みを諮るときに、壊れうる点と値をあらためて決める（推奨）

B. `5000`（5 秒）。取り込むときに近い値で測れるが、効かなかったときに値のせいか手のせいかを見分けにくい

X. Other (please specify)

### D5. 記録のループの空回し（基盤の設計のレビューの R-01）

記録のループの列の取り違え（`memory.stat` の並び）を、Build and Test の測りの前に見つけるかを決めます。

A. この段で、動いている配備したアプリのコンテナ `mastersmith-app-1` に向けて、記録のループだけを 5 秒流す。cgroup のファイルを読むだけで、止めない・書かない。列が正しいことを確かめてから Build and Test に渡せる（推奨）

B. 流さない。Build and Test の最初の回で、値が取れているかを結果のファイルで確かめてから続ける（前の Intent の (b) 節と同じ扱い）

X. Other (please specify)

## Sources

- 段の定義: `.claude/aidlc-common/stages/construction/code-generation.md`（Step 1〜2）
- コード生成の進め方: `aidlc/spaces/default/intents/261016-business-data-views/construction/code-generation/memory.md`（Deviations・Open questions）
- U5 の設計（承認済み）:
  - 機能設計: `aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/functional-design/functional-spec.md`・`rules.md`・`entities.md`・`traceability.json`
  - NFR 要件: `aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/nfr-requirements/security-requirements.md`・`tech-stack-decisions.md`
  - NFR 設計: `aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/nfr-design/logical-components.md`・`security-design.md`・`nfr-design-questions.md`（Q1: A・Q2: A）
  - 基盤の設計: `aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/infrastructure-design/cicd-pipeline.md`・`infrastructure-design-questions.md`（Q1: A）
- 承認の場の決定: `aidlc/spaces/default/intents/261016-business-data-views/construction/functional-design/memory.md`・`nfr-requirements/memory.md`・`nfr-design/memory.md`・`infrastructure-design/memory.md`
- レビュー:
  - 機能設計: `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/functional-design/units/u5-carryover-tests/`（R-01〜R-05、`functional-spec.md` 8節の写し）
  - NFR 要件: `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/nfr-requirements/units/u5-carryover-tests/00d44a7a2452e6b7/1.json`（R-01〜R-06）
  - NFR 設計: `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/nfr-design/units/u5-carryover-tests/df13e747868fc8fa/1.json`（R-01〜R-06）
  - 基盤の設計: `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/infrastructure-design/units/u5-carryover-tests/4bcccff4724084fb/1.json`（R-01〜R-07）
- Inception: `aidlc/spaces/default/intents/261016-business-data-views/inception/requirements-analysis/requirements.md`（FR15・FR16）、`inception/units-generation/unit-of-work.md`（U5）、`inception/delivery-planning/bolt-plan.md`（B1）
- 前の単位の計画の形: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/code-generation/code-generation-plan.md`・`unit-test-instructions.md`、`aidlc/spaces/default/intents/261016-business-data-views/construction/u1-businessdata-read/code-generation/unit-test-instructions.md`
- コード（読み取りだけ）:
  - `backend/src/test/java/cherry/mastersmith/role/web/RoleConcurrencyIT.java`（69・117〜121・145〜179 行）
  - `backend/src/test/java/cherry/mastersmith/role/testsupport/TestRoleBarrier.java`（45・60〜128・189〜202 行）、`RoleActors.java`（53 行）
  - `backend/src/test/java/cherry/mastersmith/audit/testsupport/AuditWriteBarrierConfig.java`・`SlowAuditWriteConfig.java`・`AuditRows.java`
  - `backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java`（95・106〜291・408〜425 行）、`AuditConfig.java`（37 行）
  - `backend/src/test/java/cherry/mastersmith/ArchitectureTest.java`・`role/RoleBoundaryArchitectureTest.java`・`audit/AuditBoundaryArchitectureTest.java`（`DO_NOT_INCLUDE_TESTS`）
  - `frontend/src/app/admin-forbidden/AdminForbiddenProvider.test.tsx`（55〜75・231〜242 行）・`AdminForbiddenProvider.tsx`（75〜130 行）、`frontend/src/app/testing/renderWithProviders.tsx`（118〜145 行）、`frontend/src/app/login-state/LoginStateGate.tsx`（70〜95 行）、`frontend/vitest.config.ts`
  - `perf/dsl-timing.sh`（45・91〜126・144〜145・153〜178・190・222・271・500〜513 行）、`perf/README.md`（7・38〜41・152・155〜189・829〜906 行）、`docker/perf/compose.yaml`（25〜60 行）、`Dockerfile`（17・52 行）、`backend/src/main/resources/application.yaml`（166〜172 行）、`build.gradle.kts`（355〜416 行）
  - H2 2.4.240 のソース（Gradle の手元の置き場の `h2-2.4.240-sources.jar` を読み取りで展開）: `org/h2/engine/ConnectionInfo.java`（`KNOWN_SETTINGS`・`readSettingsFromURL`）、`org/h2/command/dml/SetTypes.java`（`RETENTION_TIME`）、`org/h2/command/dml/Set.java`（469〜480 行）、`org/h2/engine/Database.java`（533 行）
- 決まり: `aidlc/spaces/default/memory/team.md`（Way of Working・Testing Posture・Code Style）、`aidlc/spaces/default/memory/project.md`（Forbidden・Mandated・Testing Posture と Change Control の学び）

## Assumptions & Open Questions

- [assumption] 監査は確定の後に、要求を処理するスレッドと同じスレッドで書かれる（`AuditEventListener` の `AFTER_COMMIT`）。そのため、`TestRoleBarrier` の `AFTER_LOCK` に来たスレッドと `BEFORE_LOCK` を通ったスレッドが、それぞれの保存の監査を書く。違う形だと分かったら、Step 4 のとおり出来事の中身で特定する形に替えて記録する。
- [assumption] `RETENTION_TIME` は URL の設定で渡せる（4.6 の H2 のソースの読み取り）。実際に効いたことは Build and Test で設定の値を読んで確かめる。読めなかったときは、試しの回を試しの結果に使わず、範囲を記録して依頼者に諮る（機能設計 4.1 の手順3）。
- [assumption] アプリのイメージ（`eclipse-temurin` の Ubuntu noble の JRE）に `bash`・`awk`・`stat`・`date` がある。Step 12 の空回し（D5: A）で確かめる。
- [assumption] colima の VM は cgroup v2（基盤の設計の読み取り）。読めない回は無効にする。
- 照合: 2節の後の `## Testing Contract` の塊は、`aidlc engine testing-posture render` の出力（指揮役が保存したもの）と一字も変えずに同じであることを、貼った後に比べて確かめる（結果は下の行）。
- 照合の結果: 貼った塊は保存された render の出力の塊と文字列として同じ（JSON の本文も一致。`contract_sha256` は `sha256:7b5ef641649418f9e844285e996fba87207000bd5c245758d4323958b43707d3`）。承認の前に指揮役が `aidlc engine testing-posture render` を流し直して比べる。
