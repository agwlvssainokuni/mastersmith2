# Code Generation Plan — U4 監査ログ（u4-audit-log）

## 1. 概要

U4 は、U2 の認証の出来事（ログイン成功・ログイン失敗・ログアウト）と U3 のアクセス拒否の出来事を受け取り、監査イベントとして内部DBの新しい表に1件ずつ追記する。受け取りの経路は2つあり、U2 の出来事は内部DBの更新が確定した後に、U3 の出来事はその場で（どちらも要求と同じスレッドで）記録する。書き込みに失敗しても元の操作（ログイン・ログアウト・401／403）の応答は変えず、ERROR のログを1回出す。監査ログを見る画面・API は本 Intent では作らない。

- 対象の要件: FR9.1〜FR9.5、および FR10.2 のうち監査イベントとアプリのログのトレースIDの一致。本スコープはユーザーストーリーを作っていないため、手順と要件の対応は FR・BR・NFR の ID で示す（5章）。
- 対象の決まり: U4 の BR1.1〜BR1.6、BR2.1〜BR2.4、BR3.1・BR3.2、BR4.1。NFR は U4 の各要件書の ID（性能 NFR1.1〜NFR1.3、拡張性 NFR1.4〜NFR1.6、安全 NFR3.1〜NFR3.7、信頼性 NFR10.1・NFR10.2・NFR9.1・NFR9.2、観測性 NFR10.3〜NFR10.5）。
- U4 は U1 が作った骨格（`backend/`、Flyway、JSON のログ、トレースID、`./gradlew verify`、CI、コンテナ）、U2 が作った認証の出来事、U3 が作ったアクセス拒否の出来事の上に作る。アプリのコードはリポジトリのルート直下の `backend/` に置き、`aidlc/` の下には置かない。
- U4 は画面を持たない（`inception/units-generation/unit-of-work.md` の U4 は種別 `service`）。フロントエンドのファイルは1つも作らず、変えない。
- U4 は API を持たない（NFR3.5）。監査イベントを変更・削除する処理も持たない（BR4.1、NFR3.2）。
- U1・U2・U3 のファイルは書き換えない。例外は、7章の D1 で承認を求める2つのファイル（U2 の結合テスト1件と `README.md` の節の追加）だけである。
- チームの決まりの「★ 監査ログの書き込みに失敗したときに操作を失敗させるか」は、U4 の設計で決着済みである（BR3.1・NFR10.1: 元の操作は続け、失敗は U4 の中で受け止めて ERROR を1回出す。再試行しない）。本計画で問い直さず、そのとおりのテストを書く。

## 2. 前提と守ること

- **版**: U1〜U3 と同じ（Java 25、Spring Boot 4.1.1、Spring Data JPA、Flyway、組み込みの H2）。**依存関係は1つも足さない**（受け取りは Spring の出来事の仕組み、保存は Spring Data JPA と Flyway、テストは JUnit 5・AssertJ・Mockito・jqwik・ArchUnit・Spring Boot Test で足りる。`nfr-requirements/tech-stack-decisions.md`）。足す必要が出たと分かったら、その場で生成を止めて案を示す。
- **ライセンスヘッダー**: 生成するすべてのソースファイルの先頭に Apache License 2.0 の標準ヘッダー（年 `2026`、著作権者 `agwlvssainokuni`）を入れる。Java は `/* ... */`（`/** ... */` は使わない）、SQL は `--` の行で U1・U2 の移行のファイルと同じ形にする。
- **言語**: コメント（Javadoc を含む）と README は日本語。テストの説明文（テスト名、`@DisplayName`）は英語。テストデータは日本語でよい。
- **バックエンドの書き方**: パッケージは `cherry.mastersmith.audit` とし、`domain`・`repository`・`service` に分ける（7章の C1。API を持たないため `web` は作らない）。依存性の注入はコンストラクター注入だけ。Lombok は使わない。`record` と不変のエンティティで受け渡す。ロガーは `LoggerFactory.getLogger`、可変の値はキーと値（`addKeyValue`）で渡し、文字列の連結をしない。
- **層の境界**: トランザクションの指定（`@Transactional`）は `audit.service` にだけ置く（U1 の `ArchitectureTest` が全パッケージを検査する）。DB アクセスは `audit.repository` だけが行い、出来事の受け取りと記録の組み立ては `audit.service`、監査イベントの形と切り詰めは `audit.domain` に置く。
- **時刻**: 監査イベントの日時は出来事に載った日時をそのまま使う（BR1.5）。U4 は `Clock` を新しく定義しない（必要な場合は U2 の `cherry.mastersmith.auth.service.AuthClockConfig` の Bean を注入する）。書き込みの時間の測定は、U1 の `DummyPasswordHash` と同じく差し替えられる `LongSupplier` で行う（7章の C5）。
- **出来事の受け取り方（U2・U3 の申し送り）**:
  - U2 の `cherry.mastersmith.auth.domain.AuthenticationEvent` は U2 のトランザクションの中で知らされ、`@TransactionalEventListener(phase = AFTER_COMMIT)` で確定の後に同じスレッドで受け取る。取り消されたら受け取りは呼ばれない（BR1.4）。
  - U3 の `cherry.mastersmith.access.domain.AdminAccessDeniedEvent` はトランザクションの外（401／403 の処理）で知らされ、要求と同じスレッドで応答を書く前に受け取る（7章の C2）。この出来事の `toString()` はメールアドレスを伏せるため、値は必ず `enteredEmail()` で取る（U3 の申し送り）。
  - どちらの経路でも、監査イベントの書き込みは**新しいトランザクション**（`REQUIRES_NEW`）で行う（`nfr-design/reliability-design.md` 1章）。
- **失敗を持ち込まない**: 出来事の組み立て・トランザクションの開始・追記・確定のすべてを U4 の中で捕まえ、呼び出し元へ伝えない。再試行しない。ERROR は1件の失敗につき1回（BR3.1、NFR10.1、`nfr-design/reliability-design.md` 2章）。例外を黙って捨てるコードは書かない。
- **秘密情報**: 監査イベントとログに、パスワード（平文・ハッシュとも）・アクセストークン・リフレッシュトークン・Authorization ヘッダー・署名鍵を入れない（BR2.3、NFR3.1、project.md Forbidden）。受け取る出来事にもこれらの項目は無い。監査イベントの `toString()` はメールアドレスを伏せる（U1 のメソッドの呼び出しの追跡が引数・戻り値を文字列にするため）。書き込みの失敗の ERROR にだけ、記録しようとしたメールアドレスをキーと値で出す（U4 の NFR10.3。7章の C6）。
- **値の扱い**: 攻撃者が決められる値（メールアドレス・User-Agent・要求のパス）は、文字（コードポイント）単位で上限まで切り詰め、サロゲートペアを分断しない。それ以外の加工はしない（BR2.1、BR2.2、NFR3.4）。SQL は値の結び付け（パラメーター）だけで組み立てる。
- **品質の目標は下げない**: カバレッジの下限（行 80%・分岐 70%）と NFR の値を、手順を通すために緩めない。カバレッジの除外を増やさない。既存のテスト（U1〜U3）を、通すために弱めない（調整は 7章の D1 の範囲だけ）。
- **`vendor/make-you-chic-ui` は変更しない**。フロントエンドのファイルは変更しない。
- **コミットとプッシュ**: コミットはファイル変更のまとまりごとに提案し、依頼者の承認を得てから行う（日本語のメッセージ）。`origin` への `git push` は行わない。

## Testing Contract

```json
{
  "version": 1,
  "methodology": "test-after",
  "source": "team",
  "ordering": "テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。",
  "scope": "auth-audit-foundation",
  "test_strategy": "standard",
  "project_type": "greenfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。\n- 画面からの一連の操作を確かめるテスト（E2E）は、代表的な流れ1〜2本に絞って入れる（まずは「ログイン → 管理画面に入れるか → ログアウト」）。道具は設計のステージで決める。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。不安定なテストは放置せず、原因を直すまで統合しない。\n- 認証・認可・監査に関わる機能では、次のテストを必ず書く。★印の項目は要件（しきい値や動作）が未確定のため、要件定義で決めてからテストを書く。\n  - アカウントロック: 失敗回数のしきい値の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロック中は正しいパスワードでも拒否、ログイン成功時の失敗回数の扱い。★しきい値、回数を数える期間、ロックの解除方法\n  - ログイン: 存在しないユーザーとパスワード誤りで、応答からユーザーIDの存在を推測できないこと\n  - トークン: 有効期限の境界（直前は有効／直後は無効）、署名の改ざん、署名方式の指定を悪用した改ざん（`alg: none` 等）、ログアウト後のリフレッシュトークンの拒否、ログアウト後もアクセストークンが有効期限まで使えること（決定済みの仕様として明示する）。★各トークンの有効期限の値、リフレッシュトークンを使うたびに作り直すか\n  - 初期管理者の自動作成: 2回目以降の起動で重複作成しない、設定が無い／不正なときの動作、パスワードがログに出ない。★設定が無いときに起動を止めるか\n  - 認可: 未認証（401）、管理者フラグなし（403）、管理者（200）をサーバー側のテストで確かめる（画面で管理メニューを隠すことはサーバー側の検査の代わりにしない）\n  - 監査ログ: 対象イベントごとに必須項目が記録されること、存在しないユーザーIDでのログイン失敗も記録されること。★監査ログの書き込みに失敗したときに操作を失敗させるか\n  - 秘密情報の漏えい: ログ・監査ログの出力にパスワード・トークンの値が含まれないこと\n  - 構造化ログ・分散トレース: 決めた形式で出る、トレースIDがログに含まれる、外部エクスポートが既定で無効であること\n  - 画面: ログイン画面のアクセシビリティ検査、ロック時のメッセージ表示、ログアウトでトークンが破棄されること\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22)"
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
    "runner_step": "Bootstrap the minimal test runner/configuration and record the exact unit-scoped command.",
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
      "Bootstrap the minimal test runner/configuration and record the exact unit-scoped command.",
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
  "input_sha256": "sha256:25ea3f7583fa9dae61eb335f3373b648c506cb380b4e6c284ca079c3e54d5d34",
  "contract_sha256": "sha256:996000910b5fb215f929c890fc5f17bdde4a6dab5a0e9e7a0e7de8f7b25a4e16"
}
```

## 3. 手順の並べ方（Testing Contract との対応）

方法は test-after である。テスト可能な層ごとに「実装する → 同じ層のテストを書いて実行し、すべて通す → 次の層へ」の順に進める。テストの実行の枠組み（JUnit 5・jqwik・ArchUnit・Spring Boot Test・JaCoCo・Vitest・Playwright）は U1 が、認証のテストの補助（`MutableClock`・`AuthApi`・`SqlStatementCounter` など）は U2 が、アクセス拒否のテストの補助（`AdminTestUsers`・`CapturedAccessDeniedEvents`・`PublicApiTestRules`）は U3 が用意している。Step 2 で、最初のテストの手順（Step 4）より前に、この単位に絞ったコマンドが動くことを確かめ、`unit-test-instructions.md` に記録する。

| Testing Contract の段 | 本計画の手順 |
|---|---|
| Project structure and production configuration skeleton | Step 1 |
| Bootstrap the minimal test runner/configuration | Step 2（U1〜U3 が用意済み。U4 のコマンドの確認と、U4 のテストの補助を置く） |
| Data model / database behavior（実装 → テスト） | Step 3 → Step 4（Flyway の V4、監査イベントのエンティティ、切り詰め、出来事からの写し取り） |
| Repository / data access（実装 → テスト） | Step 5 → Step 6（追記と読み取りだけの保存の部品、構造の検査） |
| Business logic（実装 → テスト） | Step 7 → Step 8（2つの受け取りの経路、新しいトランザクションでの追記、失敗の受け止め、遅れの WARN） |
| API / endpoint（実装 → テスト） | **実装は該当なし**。U4 は API を持たない（NFR3.5、`infrastructure-design/infrastructure-specification.md` 1章）。この段の代わりに、**呼び出し元の境界（U2 のログイン・ログアウト、U3 の 401／403）から記録までの結合テスト**を Step 9 に置く。あわせて、既存のテストの調整と全体の回帰を Step 10 |
| Frontend behavior（実装 → テスト） | **該当なし**。U4 は画面を持たない（`unit-of-work.md` の U4 は種別 `service`）。フロントエンドのファイルを作らず、変えない |
| Environment/build configuration | Step 11 |
| Documentation and traceability | Step 12 |

## 4. 実装の手順

パッケージ名・クラス名・ファイル名は計画上の名前であり、意味と置き場所（パッケージ・ディレクトリ）を変えない範囲で生成時に整えてよい。テストの置き場所は `unit-test-instructions.md` の絞り込みの範囲（`cherry.mastersmith.audit` の下）から外さない。ただし Step 10 で調整する U2 の既存のテストは例外とし、7章の D1 で承認を得た範囲だけを変える。

### 4.1 プロジェクトの構成と本番の設定の骨組み

- [x] **Step 1 — パッケージの骨組み（依存関係・設定の追加なし）**
  - パッケージの骨組み: `cherry.mastersmith.audit.{domain,repository,service}`。各パッケージに日本語の `package-info.java`。
  - 依存関係は足さない（`gradle/libs.versions.toml`・`backend/build.gradle.kts`・`backend/gradle.lockfile` を変えない）。環境変数・秘密情報・設定の型も足さない（`infrastructure-design/infrastructure-specification.md` 1章・2章）。`application.yaml` は変えない（遅れの WARN のしきい値は定数にする。7章の C5）。
  - 対応: `nfr-design/logical-components.md` 1章、`infrastructure-design/infrastructure-specification.md` 1章〜4章

### 4.2 テストの実行の枠組み

- [x] **Step 2 — この単位のテストのコマンドを確かめ、U4 のテストの補助を置く**
  - U1〜U3 が用意した枠組みをそのまま使う（新しい道具は入れない）。`unit-test-instructions.md` の 2.2 のコマンドで、U4 のパッケージに絞った実行が動くことを確かめる（この時点では U4 のテストは無い。常に通るだけのテストは書かない）。
  - テストの補助（テストのソースの中だけ。`cherry.mastersmith.audit.testsupport`）:
    - `AuditRows`: `JdbcTemplate` で `audit_events` の行を読む（監査を見る API が無いため。7章の C8）。件数、最後の1件、日時の順の一覧を返す。
    - `FailingAuditEventRepositoryConfig`: 追記で必ず例外を投げる保存の部品を `@Primary` の Bean で差し替える（書き込みの失敗の再現。7章の C9）。接続を借りる失敗も、同じ差し替えで別の例外として起こす。
    - `SlowAuditWriteConfig`: 書き込みの時間の測り方（`LongSupplier`）を、呼ぶたびに 200 ミリ秒を超える値を返すものに差し替える（遅れの WARN の確認。7章の C5）。
  - 対応: team.md Testing Posture、NFR9.1（信頼性）

### 4.3 データモデル・DB の振る舞い

- [x] **Step 3 — 監査イベントの表とエンティティ、切り詰め、出来事からの写し取り**
  - `backend/src/main/resources/db/migration/V4__u4_audit_event.sql`（U1 の申し送りの命名）: 表 `audit_events` を作る。列は `audit_event_id`（連番の主キー）・`occurred_at`・`event_type`・`result`・`entered_email`・`failure_reason`・`source_ip`・`user_agent`・`request_path`・`trace_id`。発生の日時に索引を1つだけ付ける（`ix_audit_events_occurred_at`）。前進のみ・後方互換（1つ前の版のアプリはこの表を使わずに動く）。列の長さは切り詰めの上限の2倍とする（7章の C3・C4）。
  - `audit.domain.AuditEvent`（JPA のエンティティ、表 `audit_events`）: 項目は `auditEventId`・`occurredAt`・`eventType`・`result`・`enteredEmail`・`failureReason`・`sourceIp`・`userAgent`・`requestPath`・`traceId`。作ったあとに変える手段（設定の操作）を持たず、Hibernate の `@Immutable` と各列の `updatable = false` で更新させない（NFR3.2、`nfr-design/security-design.md` 2章）。パスワード・トークン・ハッシュ値の項目を持たない（BR2.3）。`toString()` はメールアドレスを伏せる（U1 の申し送り。U3 の出来事と同じ考え方）。
  - `audit.domain.AuditEventType`（`LOGIN_SUCCEEDED`・`LOGIN_FAILED`・`LOGGED_OUT`・`ACCESS_DENIED`）、`audit.domain.AuditResult`（`SUCCESS`・`FAILURE`）、`audit.domain.AuditFailureReason`（`USER_NOT_FOUND`・`PASSWORD_MISMATCH`・`ACCOUNT_LOCKED`・`NOT_ADMIN`・`TOKEN_MISSING`・`TOKEN_MALFORMED`・`TOKEN_INVALID`）。
  - `audit.domain.AuditText`（純粋な関数。性質ベースのテストの対象）: 文字（コードポイント）単位で数え、上限を超える分を、文字を分断しない位置で切り詰める。上限の定数はメールアドレス 254、User-Agent 512、要求のパス 512（BR2.1、`nfr-design/security-design.md` 3章）。それ以外の加工はしない（BR2.2）。
  - `audit.domain.AuditEventFactory`（純粋な関数）: U2 の `AuthenticationEvent` と U3 の `AdminAccessDeniedEvent` から監査イベントを作る。種類から結果を決め（BR1.2）、日時は出来事の日時（BR1.5）、失敗の理由は U2 の `LoginFailureReason`・U3 の `AccessDeniedReason` から漏れなく写す（`switch` の網羅で、値が増えたらコンパイルで気づけるようにする）。メールアドレスは U3 の出来事では `enteredEmail()` で取る（`toString()` は伏せ字のため。U3 の申し送り）。要求のパスはアクセス拒否のときだけ入れ、認証の出来事では空にする（7章の D2）。U2 の出来事の `userId` は記録しない（`functional-design/entities.md`）。
  - 対応: FR9.1、FR9.2、BR1.2、BR1.5、BR1.6、BR2.1〜BR2.3、BR4.1、NFR1.5（拡張性）、NFR3.1・NFR3.2・NFR3.4

- [x] **Step 4 — 表とエンティティ、切り詰め、写し取りのテスト**
  - 単体テスト（`*Test`）:
    - `AuditTextTest`（jqwik: 切り詰めの結果は必ず上限以下のコードポイント数、元の文字列の先頭と一致する、壊れたサロゲートペアを含まない、上限以下の値は変わらない。明示の例: 300 文字のメールアドレス、600 文字の要求のパス、512 文字目がサロゲートペアの途中になる User-Agent、null と空文字）。
    - `AuditEventFactoryTest`（4つの種類と結果の対応、失敗の理由の変換を U2・U3 の enum の全値で網羅、日時は出来事の日時、存在しないメールアドレスもそのまま、アクセス拒否では要求のパスが入り認証の出来事では空、長い値が切り詰められる、U3 の出来事から取るメールアドレスが伏せ字にならない）。
    - `AuditEventTest`（必須の項目がそろう、`toString()` にメールアドレスが出ない、パスワード・トークンの項目を持たない）。
  - 結合テスト（`*IT`。組み込みの H2）:
    - `AuditSchemaIT`: V4 の移行が起動時に成功していること（`flyway_schema_history`）、監査イベントを保存して読み戻せること、必須でない列が空でも保存できること、日時がタイムゾーンに依存しない時点として保存されること（U2 の `UserSchemaIT` と同じ考え方）、発生の日時の索引があること、上限いっぱいの値（サロゲートペアを含む）が切り詰めのあと保存できること。
  - `unit-test-instructions.md` の Step 4 のコマンドで実行し、すべて通す。

### 4.4 Repository・データアクセス

- [x] **Step 5 — 監査イベントの保存の部品**
  - `audit.repository.AuditEventRepository`: Spring Data の既定の部品（`JpaRepository` など、削除をまとめて持つもの）は継承せず、最小の `Repository` から**追記と読み取りだけ**を宣言する（`save`、`findById`、日時の順の読み取り）。更新・削除の操作と更新の問い合わせを宣言しない（BR4.1、NFR3.2、`nfr-design/security-design.md` 2章）。
  - 追記は1件につき挿入1回だけで、ほかの表を読まない（NFR1.3）。主キーは DB の連番で、挿入の前に読み取りをしない（`nfr-design/performance-design.md` 1章）。
  - 対応: BR4.1、NFR1.3（性能）、NFR1.5（拡張性）、NFR3.2

- [x] **Step 6 — 保存の部品と構造の検査のテスト**
  - 結合テスト: `AuditEventRepositoryIT`（追記して読み戻す、複数件を日時の順に読む、1件の追記で発行される SQL が `insert audit_events` 1回だけ（U2 の `SqlStatementCounter`。NFR1.3）、保存した行が更新されないこと（同じエンティティを再び保存しても `update` が出ないこと））。
  - 単体テスト: `AuditBoundaryArchitectureTest`（ArchUnit。U2 の `AuthBoundaryArchitectureTest` と同じ置き方で `cherry.mastersmith.audit` に置く）: 監査の保存の部品に `delete`・`update`・`remove` で始まる操作が無い、`@Modifying` の問い合わせが無い、`audit` の外から `audit.repository` を使わない、トランザクションの指定が `audit.service` にだけある。
  - `unit-test-instructions.md` の Step 6 のコマンドで実行し、すべて通す。

### 4.5 業務処理（受け取りと記録）

- [x] **Step 7 — 出来事の受け取りと記録、失敗の受け止め、遅れの WARN**
  - `audit.service.AuditEventRecorder`: `@Transactional(propagation = REQUIRES_NEW)` で監査イベントを1件追記するだけの部品（元のトランザクションに加わらない。`nfr-design/reliability-design.md` 1章）。
  - `audit.service.AuditEventListener`: 出来事を受け取り、監査イベントを組み立て、`AuditEventRecorder` を呼ぶ。
    - 認証の出来事: `@TransactionalEventListener(phase = AFTER_COMMIT)`（U2 の申し送り。取り消されたら呼ばれない＝記録しない。BR1.4）。
    - アクセス拒否の出来事: 要求と同じスレッドで、応答を書く前に受け取る（7章の C2）。
    - どちらの受け取りも、ほかの受け取り側より先に動くようにする（`@Order` の最優先。ほかの受け取り側の失敗で監査の記録が飛ばないようにするため。7章の C2）。
    - 組み立て・トランザクションの開始・追記・確定のすべてを `try`／`catch` で囲み、例外を呼び出し元へ伝えない。再試行しない（BR3.1、NFR10.1）。
    - 失敗のとき: ERROR を1回出す。メッセージは U4 が決めた固定の文、キーと値は `auditEventType`・`result`・`occurredAt`・`enteredEmail`・`failureReason`・`sourceIp`・`userAgent`・`requestPath`・`auditTraceId`、例外は型の名前とスタックトレース（`nfr-design/observability-design.md` 1章、7章の C6）。
    - 遅れ: 1件の書き込み（開始から確定まで）が 200 ミリ秒を超えたら、かかった時間をキーと値で WARN に1回出す（`infrastructure-design/monitoring-design.md` 1章）。測り方は差し替えられる `LongSupplier`（7章の C5）。成功したときに ERROR・INFO の記録は出さない（二重の記録にしない。BR3.2、`nfr-design/reliability-design.md` 3章）。
  - `audit.service.AuditConfig`: 書き込みの時間の測り方（`LongSupplier`）の Bean を置く（テストで `@Primary` で差し替える）。設定の型・環境変数は足さない。
  - 対応: FR9.1、FR9.4、FR9.5、BR1.1〜BR1.6、BR3.1、BR3.2、NFR1.1・NFR1.2（性能）、NFR10.1・NFR10.2・NFR9.2（信頼性）、NFR10.3（観測性）

- [x] **Step 8 — 受け取りと記録のテスト（単体）**
  - `AuditEventListenerTest`（Mockito で保存の部品と時間の測り方を置き換える）: 認証の出来事で監査イベントが組み立てられて1回追記される、アクセス拒否の出来事でも同じ、追記が例外を投げても呼び出し元へ伝わらない（例外が外に出ない）、そのとき ERROR が1回だけ出て全項目のキーと値が載る、ERROR のメッセージが固定の文で例外のメッセージを使わない、ERROR とその他のログにパスワード・トークンの値が出ない、200 ミリ秒を超えたら遅れの WARN が1回、200 ミリ秒以下では WARN が出ない、成功のときに監査の内容をログに出さない。
  - `AuditEventRecorderTest`（新しいトランザクションの指定があること・保存を1回呼ぶことの確認。トランザクションの実際の振る舞いは Step 9 の結合テストで確かめる）。
  - `unit-test-instructions.md` の Step 8 のコマンドで実行し、すべて通す。

### 4.6 呼び出し元の境界から記録までの結合テスト（API・エンドポイントの段の代わり）

- [x] **Step 9 — 2つの経路の結合テスト**
  - `AuditAuthenticationEventsIT`（U2 の `AuthApi` でログイン・ログアウトを実行）: `LOGIN_SUCCEEDED`・`LOGIN_FAILED`・`LOGGED_OUT` のそれぞれで必須の項目がそろって1件記録される（日時・種類・結果・メールアドレス・失敗の理由・接続元IP・User-Agent・トレースID）、存在しないメールアドレスでのログインの失敗が入力されたメールアドレスと `USER_NOT_FOUND` で記録される（team.md の必須の監査ログのテスト）、ロック中の失敗が `ACCOUNT_LOCKED` で記録される、認証の出来事では要求のパスが空、記録は同じ要求のスレッドで行われる。
  - `AuditAccessDeniedIT`（U3 の `AdminTestUsers` で管理者でない利用者を作り 403、トークン無し・改ざん・利用者が DB にいない、の 401）: `ACCESS_DENIED` が理由ごとに記録される、403 ではメールアドレスが入り 401 では空、**要求のパスが正規化済みで問い合わせの部分なしで記録される**、有効期限切れの 401 では U3 が出来事を作らないため記録が無い、記録が応答の前に行われている（応答が返った時点で行が存在する）。
  - `AuditRollbackIT`: 元の操作の内部DBの更新が取り消された場合に記録が無いこと（BR1.4、NFR10.2（信頼性））。U2 の更新を失敗させる差し替えで再現する。
  - `AuditWriteFailureIT`（`FailingAuditEventRepositoryConfig`）: 2つの経路それぞれで、追記の失敗と接続を借りる失敗を起こし、**ログイン・ログアウト・401／403 の応答（状態コード・`code`・本文）が変わらない**こと、ERROR が1回だけ出て記録しようとした全項目が載ること、再試行が無いこと（追記の呼び出しが1回）。
  - `AuditTraceIdIT`: traceparent つきの要求と無い要求のそれぞれで、監査イベントのトレースIDが同じ要求のアプリのログのトレースIDと一致すること（U1 の `JsonLogRecords`。BR2.4、NFR10.4）。
  - `AuditSecretLeakIT`: `cherry.mastersmith.audit` を TRACE にしてログイン成功・失敗・ログアウト・403 と書き込みの失敗を起こし、監査イベントの行とアプリのログにパスワード（平文・ハッシュ）・アクセストークン・リフレッシュトークン・Authorization ヘッダーが出ないこと、すべての行が JSON でトレースIDが付くこと（NFR3.1、team.md の秘密情報の漏えいのテスト）。
  - `AuditWriteTimingIT`（`SlowAuditWriteConfig`）: 書き込みが 200 ミリ秒を超えたときに遅れの WARN が1回出ること（実時間に頼らず、測り方の差し替えで再現する。`infrastructure-design/cicd-pipeline.md` 1章）。
  - `AuditNotInAppLogIT`: 成功した記録がアプリのログに出ず、内部DBの表にだけ増えること（BR3.2、NFR9.2（信頼性））。
  - `unit-test-instructions.md` の Step 9 のコマンドで実行し、すべて通す。
  - 対応: FR9.1〜FR9.5、FR10.2、BR1.1〜BR1.6、BR2.4、BR3.1、BR3.2、NFR1.3、NFR3.1、NFR9.1、NFR9.2、NFR10.1〜NFR10.4

### 4.7 既存の単位のテストの調整と回帰

- [x] **Step 10 — U2 の既存のテストの調整（D1）と全体の回帰**
  - 7章の D1 で承認を得た範囲だけを変える。監査の追記が同じ要求のスレッドで1回増えることによる調整であり、品質の目標も本番の振る舞いも緩めない。
    - `backend/src/test/java/cherry/mastersmith/auth/web/LoginApiIT.java`: `failuresAreIndistinguishable` が期待する SQL の並びに、監査の追記（`insert audit_events`）を加える。**4つの失敗の経路で同じ並びであること**（利用者の存在を推測できないこと）の確認は保つ。
  - U1・U3 のテストは変えない見込みである（U3 の `AdminAccessIT` の SQL の回数の確認は管理者の 204 が対象で、成功のアクセスは記録しないため増えない）。変える必要が出たと分かったら、生成を止めて依頼者の判断を仰ぐ。
  - 全体の回帰: `./gradlew :backend:test :backend:integrationTest`（U1〜U3 を含むすべて）と、フロントエンドのテストが通ることを確かめる。ここで初めて分かる影響（想定外の既存テストの失敗、同時実行のテストの不安定）があれば、原因と最小限の直し方を依頼者に示してから直す。
  - 対応: 既存の検査を壊さないこと（org.md・team.md の統合前の関門）

### 4.8 環境・ビルドの設定

- [x] **Step 11 — 全体の検査と配備の確認、README**
  - `README.md` に U4 の節を足す（U1〜U3 の節の構成は変えない）: 監査ログは内部DBの `audit_events` に追記だけで記録すること、見る画面・API は本 Intent では作らないこと、確認の方法（アプリを止めてボリュームを複写し、複写したファイルを読み取りで開く。H2 のコンソールは使わない）、**ボリュームを消す操作（`docker compose down -v` など）をバックアップの前に行わない**こと、バックアップは U1 の手順で監査ログも守られること（`infrastructure-design/infrastructure-specification.md` 3章、`monitoring-design.md` 5章）。環境変数は増えない。
  - `./gradlew verify`（0〜9 の段すべて。カバレッジの下限、SpotBugs の High 0 件、OSV-Scanner、Gitleaks を含む）と `./gradlew e2eTest`（U1〜U3 の3本。U4 は E2E を増やさない。7章の C8）と `pre-commit run --all-files` がすべて通ることを確かめる。通らなければ、下限を下げずにテストを足すか、差を依頼者に示す。
  - `docker compose up -d --build` で起動し、ヘルスチェックと、初期管理者でのログイン → ログアウトの後に監査イベントが2件（`LOGIN_SUCCEEDED`・`LOGGED_OUT`）記録されていることを、README の手順（止めて複写して読む）で確かめる（`infrastructure-design/cicd-pipeline.md` 3章）。
  - 対応: NFR1.4（拡張性）、NFR3.3、NFR9.1、`infrastructure-design/cicd-pipeline.md`・`monitoring-design.md`、team.md Deployment

### 4.9 文書とトレーサビリティ

- [x] **Step 12 — まとめの記録**
  - `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u4-audit-log/code-generation/` に `code-summary.md`（作った・変えたファイル、判断、テストとカバレッジ、`./gradlew verify` の結果、計画からの逸脱、後続 Intent への申し送り（監査ログを見る画面・API を作るときは管理者のみ・表示で無害化、保存期間の決定、削除の処理を持たないこと）、7章の D2 で決めた Functional Design との違い）、`traceability.json`（FR・BR・NFR から実装とテストへの対応）、`source-manifest.json`（作った・変えた・消したアプリのソースのパスの一覧）を書く。
  - Deferred として記録するもの: NFR1.1・NFR1.2（書き込みの時間と待ち合い。Performance Validation で測る）、NFR10.5（運用で見る指標。配備先が決まったときに定める）、NFR3.3（ボリュームの権限。配備の手順で確かめる）。
  - 対応: 全要件のトレーサビリティ

## 5. 要件と手順の対応（トレーサビリティ）

| 要件・決まり | 実装の手順 | テストの手順 |
|---|---|---|
| FR9.1 4種類の出来事を内部DBに記録（BR1.1、BR1.3） | Step 3、Step 5、Step 7 | Step 4、Step 6、Step 8、Step 9 |
| FR9.2 記録する項目（BR1.2、BR1.5、BR1.6、BR2.1） | Step 3 | Step 4、Step 9 |
| FR9.3 削除の仕組みを作らない（BR4.1、NFR3.2、NFR1.6） | Step 3、Step 5 | Step 6（ArchUnit）、Step 4 |
| FR9.4 失敗しても元の操作を続け ERROR を出す（BR3.1、NFR10.1、NFR10.3） | Step 7 | Step 8、Step 9（`AuditWriteFailureIT`） |
| FR9.5 アプリのログで代用しない（BR3.2、NFR9.2） | Step 5、Step 7 | Step 9（`AuditNotInAppLogIT`） |
| FR10.2 トレースIDの一致（BR2.4、NFR10.4） | Step 3、Step 7 | Step 9（`AuditTraceIdIT`） |
| BR1.3 確定の後／その場の2つの経路、同じスレッド（NFR10.2） | Step 7 | Step 9（2つの経路） |
| BR1.4 取り消された操作は記録しない | Step 7 | Step 9（`AuditRollbackIT`） |
| BR1.6 存在しないメールアドレスも記録（NFR9.1） | Step 3 | Step 4、Step 9 |
| BR2.1 コードポイント単位・文字を分断しない切り詰め（NFR3.4） | Step 3 | Step 4（jqwik） |
| BR2.2 値を加工せず保存し、出力時に無害化 | Step 3 | Step 4、Step 9 |
| BR2.3 秘密情報を含めない（NFR3.1） | Step 3、Step 7 | Step 4、Step 8、Step 9（`AuditSecretLeakIT`） |
| NFR1.3 1件の記録は挿入1回だけ | Step 5 | Step 6（`SqlStatementCounter`） |
| NFR1.5 日時の索引 | Step 3 | Step 4（`AuditSchemaIT`） |
| 遅い書き込みの WARN（`monitoring-design.md` 1章） | Step 7 | Step 8、Step 9（`AuditWriteTimingIT`） |
| NFR3.2・NFR3.3 変更・削除させない | Step 3、Step 5 | Step 6（ArchUnit）、Step 11（ボリュームの権限は配備の手順） |
| NFR1.1・NFR1.2（性能）書き込みの時間と待ち合い | Step 5、Step 7 | Performance Validation で測る（`code-summary.md` と `traceability.json` で Deferred） |
| NFR1.4（拡張性）記録の量 | — | Step 11（見積もりの記録）、運用の確認（`monitoring-design.md` 5章） |
| NFR1.6 保存期間は後続 Intent | Step 5 | Step 12（申し送り） |
| NFR3.5 監査を見る画面・API を作らない | Step 1（`web` を作らない） | Step 6（ArchUnit）、Step 12（申し送り） |
| NFR3.6・NFR3.7 受け入れる危険（直接の書き換え、停止による欠落） | — | Step 12（`code-summary.md` に記録） |
| NFR9.1 チームの必須の監査ログのテスト | — | Step 9、`unit-test-instructions.md` 7章 |
| NFR10.5（観測性）運用で見る指標 | — | 配備先が決まったときに定める（Deferred） |

## 6. 単位どうしのつなぎ目

本ワークフローは Contract Design を行わないため、U4 が使う約束と U4 が提供する約束をここに記録する。

### 6.1 U4 が使う U1・U2・U3 の約束（既存のファイルは書き換えない。例外は 7章の D1）

| つなぎ目 | 提供 | U4 の使い方 |
|---|---|---|
| Flyway の移行（`db/migration`、`V<番号>__<単位>_<内容>.sql`） | U1 | `V4__u4_audit_event.sql` で監査イベントの表と日時の索引を作る |
| 内部DB（H2、ファイル保存、`/app/data`）とコネクションプール | U1 | 監査イベントの追記（確定の後の経路で一時的に2本目を借りる） |
| JSON のログ・トレースID（`TraceIdProvider`、Logback の設定） | U1 | 失敗の ERROR と遅れの WARN。トレースIDは U1 の仕組みで付く |
| `common.testsupport`（`JsonLogRecords`・`LogEvents`・`HttpTestClient`・`TestDatabase`） | U1 | ログと結合テスト |
| `ArchitectureTest` の層の決まり | U1 | `@Transactional` は `audit.service` だけ、`web` から `repository` を呼ばない（U4 は `web` を持たない） |
| `AuthenticationEvent`（`eventType`・`occurredAt`・`enteredEmail`・`userId`・`failureReason`・`sourceIp`・`userAgent`・`traceId`） | U2 | `@TransactionalEventListener(AFTER_COMMIT)` で確定の後に受け取る。`userId` は記録しない |
| `AuthenticationEventType`・`LoginFailureReason` | U2 | 種類・結果・失敗の理由の写し取り（網羅する） |
| `UserAccountService`・`AuthApi`・`MutableClock`・`SqlStatementCounter` | U2 | 結合テストの利用者の作成、ログイン、時刻の操作、SQL の回数 |
| `AdminAccessDeniedEvent`（`eventType`・`occurredAt`・`result`・`failureReason`・`enteredEmail`・`sourceIp`・`userAgent`・`requestPath`・`traceId`） | U3 | 要求と同じスレッドで受け取る。メールアドレスは `enteredEmail()` で取る（`toString()` は伏せ字） |
| `AccessDeniedReason` | U3 | 失敗の理由の写し取り（網羅する） |
| `AdminTestUsers`・`AdminAccessTestConfig`・`CapturedAccessDeniedEvents`・`PublicApiTestRules` | U3 | 403・401 を起こす結合テスト |

### 6.2 U4 が提供する約束

| つなぎ目 | 形 | 使う単位 |
|---|---|---|
| 監査イベントの表 | `audit_events`（`audit_event_id`・`occurred_at`・`event_type`・`result`・`entered_email`・`failure_reason`・`source_ip`・`user_agent`・`request_path`・`trace_id`、日時の索引1つ）。追記だけ。ほかの単位は表を直接読み書きせず、U4 の保存の部品を通す | 後続 Intent（監査ログの参照） |
| 監査イベントの形 | `cherry.mastersmith.audit.domain.AuditEvent`（不変。`toString()` はメールアドレスを伏せる） | 後続 Intent |
| 記録の窓口 | `audit.service` の受け取り。新しい出来事を記録したい単位は、出来事の型を足して `AuditEventFactory` に写し取りを加える（出来事を知らせる側は U4 を知らない。ADR-004） | 後続 Intent |
| 後続 Intent への引き継ぎ | 監査ログを見る画面・API は管理者のみ（`/api/admin/` の下）とし、表示の際に値を無害化する（NFR3.5）。保存期間と古い記録の扱いは後続 Intent で決める（NFR1.6）。種類・メールアドレスでの絞り込みが要るときに索引を足す（NFR1.5） | 後続 Intent |

## 7. 判断を仰いだ点（決定済み）

依頼者の回答（`code-generation-questions.md` の Q1〜Q4）により、次のとおり決まった。

| 点 | 決定 |
|---|---|
| D1 既存のファイルの変更（2件） | A: `LoginApiIT` の SQL の並びの期待と `README.md` を変える |
| D2 Functional Design と NFR 設計の違い（2点） | A: NFR 設計に合わせる（要求のパスの記録とその切り詰め）。設計の文書は書き換えず、違いを記録する |
| C6 失敗の ERROR のログ | A: 要件どおりメールアドレスを載せる（U4 に限った例外として記録する） |
| C1〜C5・C7〜C9 | A: すべて下の内容のとおり進める |

以下は、決定の前に示した案の内容である。

以下は依頼者の判断を仰ぐ点である。D は決定が必要なもの、C はこのまま進めてよいかの確認である。

### 決定が必要

#### D1 — 既存の単位のファイルの変更（2ファイル）

U4 が監査の追記を1回増やすため、次のファイルは変えないと成り立たない。本番の設定・品質の目標は変えない。

| ファイル | 変更 | 理由 |
|---|---|---|
| `backend/src/test/java/cherry/mastersmith/auth/web/LoginApiIT.java` | `failuresAreIndistinguishable` が期待する SQL の並び（現在は `select users`・`select login_attempt_states for update`・`update login_attempt_states` のちょうど3件）に、監査の追記（`insert audit_events`）を加える | ログインの失敗の要求と同じスレッドで、確定の後に監査の追記が1回増えるため。**4つの失敗の経路で並びが同じであること（利用者の存在を推測できないこと）の確認はそのまま残す**ので、このテストの目的は弱まらない |
| `README.md` | U4 の節を足す（追記のみ。U1〜U3 の節は変えない） | 監査ログの置き場所・確認の方法・ボリュームを消す操作の注意を運用の手順として書くため（`infrastructure-design/infrastructure-specification.md` 3章） |

U1・U3 のテストは変えない見込みである（U3 の `AdminAccessIT` の SQL の回数の確認は、成功の 204 が対象で記録が増えないため）。

- **A（推奨）**: 上の2ファイルを変える（Step 10・Step 11）。ほかのファイルを変える必要が出たときは、生成を止めて依頼者の判断を仰ぐ。変更は `code-summary.md` に記録する。
- **B**: 既存のテストを変えずに済むよう、テストのときだけ監査の記録を止められる設定を入れる。→ 本番で監査の記録を無効にできる設定が増えるため勧めない（FR9.1、NFR3.2）。
- **X. Other (please specify)**

#### D2 — 承認済みの Functional Design との違い（2点）の揃え方

`nfr-design/security-design.md` 6章は1点目を挙げ、Code Generation の計画で承認を得ることを求めている。計画を書く中で2点目（切り詰めの対象）が分かったため、あわせて示す。

| 違い | Functional Design の記述 | NFR Design の記述 | 本計画 |
|---|---|---|---|
| 1. 要求のパスの項目 | `functional-design/entities.md` の AuditEvent に要求のパスの項目が無い。`functional-spec.md` WF2 も記録しない | `security-design.md` 1章・6章: 依頼者の判断により、アクセス拒否のときだけ `requestPath`（任意、正規化済み、問い合わせの部分なし、512 文字で切り詰め）を加える。ログイン・ログアウトでは空 | **NFR Design に合わせる**（Step 3、Step 9）。U3 は既にこの項目を出来事に載せて実装済みで、表はまだ作られていないため後からの追加にもならない |
| 2. 切り詰めの対象 | `rules.md` BR2.1 は、メールアドレス 254 文字と User-Agent 512 文字だけを挙げる | `security-design.md` 3章: 要求のパスも 512 文字（コードポイント単位、文字を分断しない）で切り詰める | **NFR Design に合わせる**（Step 3、Step 4）。1点目で項目を加える以上、同じ切り詰めを当てないと表の列に収まらない。U3 側でも切り詰め済みのため、U4 の切り詰めは二重の備えになる |

- **A（推奨）**: 上のとおり NFR Design に合わせて作る。設計の文書は書き換えず、違いを `code-summary.md` と `traceability.json` に記録する。
- **B**: 先に Functional Design をやり直し（`/aidlc --stage functional-design`）、2点を反映してから Code Generation に戻る。
- **X. Other (please specify)**

### 確認（このまま進めてよいかの確認）

- **C1 — パッケージと部品の名前**: パッケージは `cherry.mastersmith.audit`（`domain`・`repository`・`service`）。API を持たないため `web` は作らない。部品は `AuditEvent`（エンティティ）・`AuditEventType`・`AuditResult`・`AuditFailureReason`・`AuditText`（切り詰め）・`AuditEventFactory`（写し取り）・`AuditEventRepository`（追記と読み取りだけ）・`AuditEventRecorder`（新しいトランザクションでの追記）・`AuditEventListener`（受け取り）・`AuditConfig`。
- **C2 — 受け取りの形と順番**: 認証の出来事は `@TransactionalEventListener(phase = AFTER_COMMIT)`。アクセス拒否の出来事は、`nfr-design/reliability-design.md` 1章のとおり**同じ仕組みで、トランザクションが無いときも受け取る設定**（`fallbackExecution = true`）にする。U3 は 401／403 の処理（トランザクションの外）で知らせるため、実際には要求と同じスレッドで、応答を書く前にその場で受け取る（U3 の申し送りの `@EventListener` と同じ振る舞い）。Step 9 の結合テストで、応答が返った時点で行が存在することを確かめる。あわせて、U4 の受け取りに**最優先の順番**（`@Order`）を付け、ほかの受け取り側（テストの補助など）が例外を投げても監査の記録が飛ばないようにする。
- **C3 — 表と列の名前**: 表 `audit_events`、列 `audit_event_id`（連番の主キー）・`occurred_at`・`event_type`・`result`・`entered_email`・`failure_reason`・`source_ip`・`user_agent`・`request_path`・`trace_id`、索引 `ix_audit_events_occurred_at`（発生の日時に1つだけ）。移行のファイルは `V4__u4_audit_event.sql`。`result` が H2 の予約語と衝突した場合だけ `event_result` に改める（そのときは `code-summary.md` に記録する）。
- **C4 — 列の長さ**: 切り詰めの上限はコードポイント（文字）単位だが、DB の `VARCHAR(n)` は Java の文字数で数えるため、絵文字などを含む値は上限のコードポイント数の2倍の長さになりうる。列の長さは上限の2倍とする（`entered_email` 508、`user_agent` 1024、`request_path` 1024、`source_ip` 45、`event_type`・`failure_reason` 32、`result` 16、`trace_id` 64）。切り詰めの決まり（254・512・512 コードポイント）は変えない。
- **C5 — 遅い書き込みの WARN の測り方**: しきい値 200 ミリ秒は定数とし、設定（`application.yaml`・環境変数）にしない。時間は U1 の `DummyPasswordHash` と同じく差し替えられる `LongSupplier`（既定は `System::nanoTime`）で測り、テストでは差し替えて実時間に頼らずに確かめる。測る範囲は新しいトランザクションの開始から確定までとする（`nfr-design/performance-design.md` 1章）。
- **C6 — 書き込みの失敗の ERROR のログ**: メッセージは U4 が決めた固定の文にし、例外のメッセージは使わない。記録しようとした全項目（**メールアドレスを含む**）をキーと値で出し、例外は型の名前をキーと値で、スタックトレースを例外として出す（`nfr-design/observability-design.md` 1章、`security-design.md` 1章）。これは U4 の NFR10.3 が明示的に求めるもので、U2・U3 の「アプリのログにメールアドレスを出さない」方針の、U4 に限った例外になる（後から手で記録を補えるようにするため）。パスワード・トークンは監査イベントの項目に無いため、この ERROR にも出ない（テストで確かめる）。
- **C7 — 変更・削除をさせない作り**: 保存の部品は `JpaRepository` を継承せず、最小の `Repository` から追記と読み取りだけを宣言する。エンティティは Hibernate の `@Immutable` と各列の `updatable = false` を付ける。構造の検査（ArchUnit）は U2 と同じ置き方で `cherry.mastersmith.audit` の下の `AuditBoundaryArchitectureTest` に置き、U1 の `ArchitectureTest` は変えない。
- **C8 — 記録の読み方と E2E**: 監査ログを見る API が無いため、結合テストは `JdbcTemplate` で `audit_events` を直接読む（テストの補助 `AuditRows`）。E2E は増やさず、U1〜U3 の3本のままとする（画面の操作で確かめられる振る舞いが無いため）。配備の確認（ログイン・ログアウトで2件記録されること）は、README の手順（アプリを止めてボリュームを複写し、複写したファイルを読み取りで開く）で Step 11 に手で行い、自動化しない。
- **C9 — 書き込みの失敗の起こし方**: 本物の DB を壊さず、テスト用に失敗する保存の部品を `@Primary` の Bean で差し替えて、追記の失敗（DB の例外）と接続を借りる失敗（別の例外）を起こす（`infrastructure-design/cicd-pipeline.md` 1章）。元の操作の取り消しは、U2 の更新を失敗させる差し替えで再現する。

## 8. 生成中の進め方

- 手順は番号の順に1つずつ進め、終わった手順のチェックボックスに印を付ける（印を付ける以外の本書の変更は Plan Approval をやり直す）。
- 各テストの手順では、`unit-test-instructions.md` のこの単位に絞ったコマンドで実行し、すべて通ってから次の手順へ進む。
- 版の組み合わせが合わない、品質の目標に届かない、設計書と食い違う、D1 の2ファイル以外で U1・U2・U3 のファイルを変える必要がある、と分かったときは、生成を止めて案を示し、依頼者の判断を仰ぐ。
- 区切りのよいところ（例: Step 2、Step 4、Step 6、Step 8、Step 9、Step 10、Step 11、Step 12 の後）でコミットを提案し、承認を得てから行う。

## 参照した文書

- 単位の設計: `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u4-audit-log/` の `functional-design/`（`functional-spec.md`・`rules.md`・`entities.md`）、`nfr-requirements/`（6つの文書）、`nfr-design/`（6つの設計書。特に `security-design.md` 1章〜6章、`reliability-design.md` 1章・2章・4章、`performance-design.md` 1章〜3章、`observability-design.md` 1章〜3章）、`infrastructure-design/`（`infrastructure-specification.md`・`monitoring-design.md`・`cicd-pipeline.md`）
- ほかの単位との約束: `construction/u1-app-skeleton/code-generation/code-summary.md`（申し送り）、`construction/u2-authentication/code-generation/code-summary.md`（申し送り）、`construction/u3-access-control/code-generation/code-summary.md`（申し送り）、`construction/u3-access-control/nfr-design/security-design.md` 4章
- Inception: `inception/units-generation/unit-of-work.md`・`unit-of-work-dependency.md`、`inception/requirements-analysis/requirements.md`（FR9.1〜FR9.5、FR10.2）、`inception/domain-design/components.md`・`decisions.md`（ADR-004・ADR-008）
- 既存のコード: `backend/src/main/java/cherry/mastersmith/auth/domain/`（`AuthenticationEvent`・`AuthenticationEventType`・`LoginFailureReason`・`ClientInfo`・`RefreshToken`）、`auth/repository/RefreshTokenRepository.java`、`auth/service/AuthClockConfig.java`、`access/domain/AdminAccessDeniedEvent.java`・`AccessDeniedReason.java`、`user/service/DummyPasswordHash.java`、`backend/src/main/resources/db/migration/`、`backend/src/test/java/cherry/mastersmith/`（`ArchitectureTest`・`auth/AuthBoundaryArchitectureTest`・`auth/web/LoginApiIT`・`auth/web/AuthEventsIT`・`auth/testsupport/`・`access/testsupport/`・`access/web/AdminAccessIT`・`common/testsupport/`・`common/db/DatabasePersistenceIT`・`user/repository/UserSchemaIT`）、`README.md`
- チームの決まり: `aidlc/spaces/default/memory/org.md`・`team.md`・`project.md`・`phases/construction.md`
