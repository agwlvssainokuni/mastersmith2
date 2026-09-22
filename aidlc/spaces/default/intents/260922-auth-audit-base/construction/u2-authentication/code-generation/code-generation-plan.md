# Code Generation Plan — U2 認証（u2-authentication）

## 1. 概要

U2 は、メールアドレスとパスワードでログインし、アクセストークン（HS256 の JWT、既定5分）とリフレッシュトークン（Cookie、既定24時間）を受け取り、更新し、ログアウトできるようにする。あわせて、アカウントロック（既定5回で30分）、初期管理者の自動作成、ログイン・ログアウトの出来事の通知（受け取りは U4）、ログイン画面と画面側のトークンの保持・更新・ログアウト、API 呼び出しの共通部分（ApiClient）を作る。

- 対象の要件: FR2.1、FR2.4、FR3.1〜FR3.4、FR4.1〜FR4.5、FR5.1〜FR5.3、FR6.1、FR6.2、FR7.1〜FR7.5（本スコープはユーザーストーリーを作っていないため、手順と要件の対応は FR・BR・NFR の ID で示す。5章）。
- 対象の決まり: U2 の BR1.1〜BR9.1、NFR1.1〜NFR1.10、NFR2.1〜NFR2.3、NFR3.1〜NFR3.6、NFR4.1〜NFR4.4、NFR5.1〜NFR5.6、NFR6.1〜NFR6.3、NFR7.1、NFR8.1、NFR9.1〜NFR9.5、NFR10.1〜NFR10.7。
- U2 は U1 が作った骨格（`backend/`・`frontend/`、`./gradlew verify`、CI、コンテナ）の上に作る。アプリのコードはリポジトリのルート直下の `backend/` と `frontend/` に置き、`aidlc/` の下には置かない。骨格は既にあるため、チームの進め方の Walking Skeleton の一式は U1 のものが維持されていることを前提とし、U2 はそれを壊さない。
- U1 のファイルは書き換えない。例外は、依頼の範囲で共有とされたもの（`.env.example`、`README.md` の節、Flyway の新しいファイル、`gradle/libs.versions.toml` と lockfile、全単位で共用する `backend/src/main/resources/application.yaml` の `mastersmith.auth` のまとまり）と、7章の D1 で承認を求める3つのファイルだけである。

## 2. 前提と守ること

- **版**: U1 と同じ（Java 25、Spring Boot 4.1.1、Node.js 24、React 19、Vitest 4）。追加する依存関係は Spring Boot が版を管理する `spring-boot-starter-oauth2-resource-server`（JWT の発行と検証の JOSE の仕組みを含む）の1つだけとし、`gradle/libs.versions.toml` と `backend/gradle.lockfile` で固定する。画面側の依存関係は増やさない（make-you-chic-ui の `FormField`・`TextInput`・`Button`・`Alert` を使う）。版の組み合わせが合わないと分かったら、その場で生成を止めて案を示す。
- **ライセンスヘッダー**: 生成するすべてのソースファイルの先頭に Apache License 2.0 の標準ヘッダー（年 `2026`、著作権者 `agwlvssainokuni`）を入れる。Java・TypeScript・CSS は `/* ... */`（`/** ... */` は使わない）、SQL は `--`、`spring.factories` などのプロパティのファイルは `#`。
- **言語**: コメント（Javadoc・JSDoc を含む）、画面の文言（日英を文言の鍵で持つ）、README は日本語。テストの説明文（テスト名、`@DisplayName`、`describe` / `it`）は英語。
- **バックエンドの書き方**: パッケージは `cherry.mastersmith.user`（UserAccount）と `cherry.mastersmith.auth`（Authentication）に分け、それぞれを `web`・`service`・`domain`・`repository` に分ける。トランザクションは `service` にだけ置く。`web` は `repository` を呼ばない。API の要求・応答は `record` の DTO（`XxxRequest` / `XxxResponse`）で、エンティティを返さない。依存性の注入はコンストラクター注入だけ。Lombok は使わない。ロガーは `LoggerFactory.getLogger`、可変の値はキーと値で渡す。現在時刻は注入できる `Clock` から得る。
- **ADR-001 の閉じ込め**: パスワードのハッシュは `user` の外へ出さない。`auth` は `user.service` の公開の操作（照合の結果と利用者の要約）だけを使い、`user.domain` のエンティティ・`user.repository` を参照しない。`user` は `auth` を参照しない。`auth`・`user` は U4（`audit`）を参照しない（出来事で知らせるだけ。ADR-004）。これを U2 の構造の検査（Step 8）で確かめる。
- **秘密情報**: パスワード（平文・ハッシュ）・アクセストークン・リフレッシュトークン・署名鍵を、ログ・トレースの属性・出来事・エラー応答に入れない。メソッドの呼び出しの追跡（U1 の TraceAspect。`web`・`service`・`domain`・`repository` の引数と戻り値を文字列にする）で漏れないよう、秘密の値は `String` のまま引数・戻り値に使わず、文字列化で `***` にする小さな型（`Password`・`RefreshTokenValue`・`AccessTokenValue` など）で包む。秘密を持つ `record`（要求・応答・設定）は `toString` を上書きする。JPA のエンティティは `toString` を上書きしない（既定の文字列化は中身を出さない）。リフレッシュトークンのハッシュは `byte[]`（`BINARY(32)`）で扱う。
- **フロントエンドの書き方**: 名前付きのエクスポートだけ、`enum` を使わず文字列リテラルの union、CSS は部品と同じ場所に素の CSS、画面に HTML を直接埋め込まない、操作できる要素に `data-testid`（`{部品}-{役割}` の kebab-case）。
- **品質の目標は下げない**: カバレッジの下限（行 80%・分岐 70%）、NFR の値（bcrypt の cost 12、有効期限、しきい値、排他の待ち 3 秒など）を、手順を通すために緩めない。カバレッジの除外を増やさない（U1 の除外の型 `*Properties` に当たる設定の record は、設定値だけを持たせ、検証の処理は別の型に置く）。
- **`vendor/make-you-chic-ui` は変更しない**。
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

方法は test-after である。テスト可能な層ごとに「実装する → 同じ層のテストを書いて実行し、すべて通す → 次の層へ」の順に進める。テストの実行の枠組み（JUnit 5・jqwik・ArchUnit・Spring Boot Test・JaCoCo、Vitest・Testing Library・vitest-axe・fast-check・`@vitest/coverage-v8`、Playwright）は U1 が既に用意している。Step 2 で、最初のテストの手順（Step 4）より前に、この単位に絞ったコマンドが動くことを確かめ、`unit-test-instructions.md` に記録する。

| Testing Contract の段 | 本計画の手順 |
|---|---|
| Project structure and production configuration skeleton | Step 1 |
| Bootstrap the minimal test runner/configuration | Step 2（U1 が用意済み。U2 のコマンドの確認と、U2 のテストの補助を置く） |
| Data model / database behavior（実装 → テスト） | Step 3 → Step 4 |
| Repository / data access（実装 → テスト） | Step 5 → Step 6 |
| Business logic（実装 → テスト） | 2つに分ける。ドメインの決まりと UserAccount Step 7 → Step 8、Authentication の業務処理 Step 9 → Step 10 |
| API / endpoint（実装 → テスト） | Step 11 → Step 12 |
| Frontend behavior（実装 → テスト） | 2つに分ける。ApiClient と AuthSession Step 13 → Step 14、ログイン画面と登録 Step 15 → Step 16 |
| Environment/build configuration | Step 17、Step 18 |
| Documentation and traceability | Step 19 |

## 4. 実装の手順

パッケージ名・クラス名・ファイル名は計画上の名前であり、意味と置き場所（パッケージ・ディレクトリ）を変えない範囲で生成時に整えてよい。テストの置き場所は `unit-test-instructions.md` の絞り込みの範囲（バックエンドは `cherry.mastersmith.auth` と `cherry.mastersmith.user` の下、フロントエンドは `frontend/src/features/auth/` と `frontend/src/shared/api-client/` の下、E2E は `frontend/e2e/u2-auth.e2e.ts`）から外さない。

### 4.1 プロジェクトの構成と本番の設定の骨組み

- [ ] **Step 1 — 依存関係、パッケージ、設定の型、時計**
  - `gradle/libs.versions.toml` に `spring-boot-starter-oauth2-resource-server` を足し、`backend/build.gradle.kts` の依存の一覧に1行足す（7章の D1）。`./gradlew :backend:resolveAndLockAll --write-locks` で `backend/gradle.lockfile` を更新する。
  - パッケージの骨組み: `cherry.mastersmith.user.{web,service,domain,repository}`、`cherry.mastersmith.auth.{web,service,domain,repository}`（`user.web` は本 Intent では使わないため作らない）。各パッケージに日本語の `package-info.java`。
  - 設定の型（U1 と同じく `@ConfigurationPropertiesScan` で読み込む。値だけを持つ `record`、既定値は `@DefaultValue`、範囲は Bean Validation で起動時に検証し、範囲外なら起動を止める。秘密の項目は `toString` で `***`）:
    - `cherry.mastersmith.auth.service.AuthProperties`（`mastersmith.auth`）: `signing-key`（必須、Base64）、`access-token-ttl`（既定 5m）、`refresh-token-ttl`（既定 24h）、`lock.threshold`（既定 5、1 以上）、`lock.duration`（既定 30m、正の値）、`refresh-token-cleanup.retention`（既定 7d）、`refresh-token-cleanup.cron`（既定 `0 30 3 * * *`）。
    - `cherry.mastersmith.user.service.PasswordProperties`（`mastersmith.auth.password`）: `bcrypt-cost`（既定 12、4〜31）。
    - `cherry.mastersmith.user.service.InitialAdminProperties`（`mastersmith.auth.initial-admin`）: `email`、`password`（どちらも任意。無い・不正は Step 7 の起動時の処理で WARN）。
    - 環境変数の名前は 7章の C1 のとおり。全単位で共用する `backend/src/main/resources/application.yaml` の `mastersmith:` の下に `auth:` のまとまりを足し、U1 の `web`・`health`・`trace` と同じく各項目を `${MASTERSMITH_AUTH_XXX:既定値}` の形で書き、日本語のコメントを付ける（依頼者の指示）。必須・秘密の項目（`signing-key`、`initial-admin.email`、`initial-admin.password`）は `${MASTERSMITH_AUTH_SIGNING_KEY:}` のように既定値を空にし、値を書かない（無ければ起動時の検証で止める、または Step 7 の処理で WARN）。Java の `@DefaultValue` にも同じ既定値を置き、`application.yaml` の値と揃っていることをテストで確かめる。
  - `Clock` の Bean（`Clock.systemUTC()`）を `cherry.mastersmith.auth.service.AuthClockConfig` に置く（7章の C4）。時刻は UTC の時点（`Instant`）として保存・比較する。
  - 定期実行を有効にする設定（`@EnableScheduling`）を `auth.service` の設定のクラスに置く。
  - 対応: NFR3.3、NFR6.2、NFR6.3、NFR9.4、BR3.7、BR4.2、BR5.3、tech-stack-decisions（U2）

### 4.2 テストの実行の枠組み

- [ ] **Step 2 — この単位のテストのコマンドを確かめ、U2 のテストの補助を置く**
  - U1 が用意した枠組みをそのまま使う（新しい道具は入れない）。`unit-test-instructions.md` の 2.2 のコマンドで、U2 のパッケージに絞った実行が動くことを確かめる（この時点では U2 のテストは無い。常に通るだけのテストは書かない）。
  - テストの起動の補助（テストのソースの中だけ。新しいファイルで、U1 のファイルは変えない）:
    - `cherry.mastersmith.auth.testsupport.TestSigningKeyEnvironmentPostProcessor` と `backend/src/test/resources/META-INF/spring.factories`: Spring を起動するすべてのテスト（U1 の `*IT` を含む）で、`mastersmith.auth.signing-key` が無ければ、実行のたびに作る 32 バイトの乱数（Base64）を入れる。鍵の値をリポジトリに置かない（7章の C6）。登録の鍵の名前は Spring Boot 4.1 で確かめる。
    - `AuthTestTokens`（テストの中で作った鍵で、改ざん・`alg: none`・別の方式・期限切れのトークンを作る）、`SqlStatementCounter`（Hibernate の `StatementInspector` で、スレッドごとに発行した SQL を数える）、`CountingPasswordEncoder`（照合の回数を数える包み）、`MutableClock`（テストで時刻を進める `Clock`）、`CapturedAuthenticationEvents`（出来事を集めるテスト用の受け取り）。
  - 対応: NFR9.4、team.md Testing Posture

### 4.3 データモデル・DB の振る舞い

- [ ] **Step 3 — スキーマの変更とエンティティ**
  - `backend/src/main/resources/db/migration/V2__u2_user_account.sql`: 表 `users`（`user_id BIGINT` の自動採番の主キー、`email VARCHAR(254)` の一意、`password_hash VARCHAR(100)`、`admin_flag BOOLEAN`、`created_at TIMESTAMP WITH TIME ZONE`）。
  - `V3__u2_authentication.sql`:
    - 表 `login_attempt_states`（`subject_id BIGINT` の主キー、`consecutive_failures INT`（0 以上の検査の制約）、`locked_until TIMESTAMP WITH TIME ZONE`（任意））。利用者の行は `subject_id`＝`user_id`、ダミーの記録（BR2.7・BR3.9）は利用者と結びつかない負の ID（-1〜-8 の 8 行）を同じ表にこのファイルで入れる。ダミーの行と同じ表にするため、`users` への外部キーは置かない（同じ SQL で読み書きし、回数と種類をそろえるため。7章の C7）。
    - 表 `refresh_tokens`（`token_id BIGINT` の自動採番の主キー、`user_id` は `users` への外部キー、`token_hash BINARY(32)` の一意、`issued_at`・`expires_at`・`revoked_at`（任意）は `TIMESTAMP WITH TIME ZONE`）。索引は `expires_at` と `user_id`。
    - どちらも前進のみ・後方互換（1つ前の版のアプリが動く）。
  - JPA のエンティティ: `cherry.mastersmith.user.domain.User`、`cherry.mastersmith.auth.domain.LoginAttemptState`、`cherry.mastersmith.auth.domain.RefreshToken`。時刻は `Instant`。`ddl-auto: validate`（U1 の設定）でスキーマと合うことを起動時に確かめる。
  - 対応: FR3.1、FR4.1、FR7.1、BR1.5、BR3.9、BR5.1、BR5.3、NFR1.8、NFR1.9、NFR5.2、scalability-design 1章

- [ ] **Step 4 — データモデルのテスト（結合テスト、組み込みの H2）**
  - `cherry.mastersmith.user.repository.UserSchemaIT`、`cherry.mastersmith.auth.repository.AuthSchemaIT`: スキーマの変更が当たり、エンティティがスキーマと合う（起動が通る）、メールアドレスの一意の制約、`token_hash` の一意の制約、`consecutive_failures` の 0 未満の拒否、ダミーの行が 8 行あり `users` の ID と重ならない、時刻が時点として保存され JVM のタイムゾーン（`Asia/Tokyo`）で値が変わらない。
  - `unit-test-instructions.md` の Step 4 のコマンドで実行し、すべて通す。

### 4.4 Repository・データアクセス

- [ ] **Step 5 — repository**
  - `cherry.mastersmith.user.repository.UserRepository`: メールアドレス（小文字にそろえた値）で探す、ID で探す、保存。
  - `cherry.mastersmith.auth.repository.LoginAttemptStateRepository`:
    - 排他つきの読み取り（`PESSIMISTIC_WRITE`、`SELECT ... FOR UPDATE`）を `subject_id` で1回。待ちの上限は 3 秒（reliability-design 1章）。
    - ダミーの行の選び方: 8 行のうち、ほかの試みが排他を持っている行を飛ばして1行を排他つきで読む（待たない指定）。H2 と Hibernate の組み合わせで「飛ばす」指定と待ちの上限の指定が効くことを Step 6 で確かめ、効かない場合は「行を乱数で選び、待たない指定で取れなければ次の行へ（上限 8 回）」で代える。どちらも効かないときは生成を止めて案を示す（7章の C7）。
    - 書き込みは、JPA の変更の検出に頼らず、明示の更新の問い合わせ（`UPDATE ... SET consecutive_failures = ?, locked_until = ? WHERE subject_id = ?`）を1回行う。値が変わらない場合（ロック中）も同じ問い合わせを1回行う（BR2.7）。
    - 行が無いときの作成（`MERGE` など、既にあれば何もしない）。
  - `cherry.mastersmith.auth.repository.RefreshTokenRepository`: ハッシュ（`byte[]`）で探す、保存、条件付きの無効化（`UPDATE ... SET revoked_at = ? WHERE token_id = ? AND revoked_at IS NULL`、更新した行の数を返す）、削除の対象（無効または期限切れで、期限から保存の日数を過ぎた行）を件数の上限（1,000）ごとに消す。
  - 対応: BR2.7、BR3.8、BR5.3、BR5.6、NFR1.5、NFR1.7、NFR9.1、NFR9.2、performance-design 4章

- [ ] **Step 6 — repository のテスト（結合テスト）**
  - `UserRepositoryIT`: 小文字の値で見つかる、無ければ空、ID で探せる。
  - `LoginAttemptStateRepositoryIT`: 排他つきの読み取りで、同じ行への2つ目の読み取りが1つ目の確定まで待つ（同時に始める合図 `CountDownLatch` で2つのスレッドをそろえる。時間の待ちに頼らない）、待ちの上限を超えたら例外（上限 3 秒が効く）、別の利用者の行は待たない、ダミーの行の読み取りどうしが待たない、明示の更新が値が同じでも1回の更新として発行される（`SqlStatementCounter`）、行の作成が2回目で何もしない。
  - `RefreshTokenRepositoryIT`: ハッシュで見つかる、条件付きの無効化が1回目は 1、2回目は 0、同時の2回の無効化で 1 になるのは1つだけ、削除が対象の行だけを消し件数の上限ごとに分かれる、有効な行・期限から保存の日数に満たない行は消さない。
  - `unit-test-instructions.md` の Step 6 のコマンドで実行し、すべて通す。

### 4.5 業務処理

- [ ] **Step 7 — ドメインの決まり、秘密の値の型、問題の種類、出来事、UserAccount**
  - `auth.domain`・`user.domain` の DB を使わない純粋な関数（性質ベースのテストの対象）:
    - `EmailAddress.normalize`（前後の空白を除き `Locale.ROOT` で小文字にする。BR2.1）と形式の確認（初期管理者の設定の検査に使う）。
    - `PasswordPolicy`（12 文字以上（コードポイントで数える）、UTF-8 で 72 バイト以内。作成時の規則。ログインでは 72 バイトの確認だけに使う。BR1.3、BR2.8、NFR2.2）。
    - `LockPolicy.decide(状態, 照合が一致したか, 現在時刻, しきい値, ロックの時間)` → 結果（`SUCCEEDED`・`PASSWORD_MISMATCH`・`ACCOUNT_LOCKED`）と書き込む状態（BR3.1〜BR3.6）。解除時刻ちょうどは解除（`now >= lockedUntil`）。
    - `TokenExpiry.isValid(現在時刻, 有効期限)`（`now < expiresAt`。BR4.2、BR5.3）。
    - リフレッシュトークンの値の生成（`SecureRandom` の 32 バイトを URL で使える Base64）と SHA-256 のハッシュ（`byte[]`）。
  - 秘密の値の型: `user.domain.Password`、`auth.domain.RefreshTokenValue`、`auth.domain.AccessTokenValue`（`toString` は `***`）。
  - `auth.domain.AuthProblemTypes`（`AUTHENTICATION_FAILED` 401、`AUTHENTICATION_REQUIRED` 401、`REFRESH_FAILED` 401、`ORIGIN_NOT_ALLOWED` 403、日英の title・description・resolution）と、それを返す `auth.service.AuthProblemTypeCatalog`（U1 の `ProblemTypeCatalog` の Bean）。`ORIGIN_NOT_ALLOWED` は承認済みの `rules.md` BR9.1 に無いもので、7章の D3 で揃える。
  - `auth.domain.TokenFailureReason`（`TOKEN_MALFORMED`・`TOKEN_INVALID`・`TOKEN_EXPIRED`・`USER_NOT_FOUND`）と、U3 との約束の例外 `auth.domain.TokenAuthenticationException`（Spring Security の `OAuth2AuthenticationException` を継承し、したがって `AuthenticationException` の子。区分を `reason()` で返す。メッセージには区分だけを入れ、トークンの値を入れない。7章の C3）。`TOKEN_MISSING` は U2 では作らない（U3 が「認証が足りない」の例外から判断する）。
  - `auth.domain.AuthenticatedUser`（`userId`・`email`・`admin`。U3 が使う）と `auth.domain.AuthenticationEvent`（`eventType`（`LOGIN_SUCCEEDED`・`LOGIN_FAILED`・`LOGGED_OUT`）、`occurredAt`、`enteredEmail`、`userId`（任意）、`failureReason`（`USER_NOT_FOUND`・`PASSWORD_MISMATCH`・`ACCOUNT_LOCKED`、任意）、`sourceIp`、`userAgent`（512 文字で切る）、`traceId`（任意））。秘密情報の項目は持たない（BR7.2）。
  - UserAccount（`user.service`）:
    - `UserAccountService.verifyPassword(email, Password)` → 照合の結果（利用者の要約 `UserSummary(userId, email, admin)` があれば入れる、一致したか）。利用者の検索1回と照合1回を必ず行う。利用者がいない・パスワードが 72 バイトを超えるときは、ダミーのハッシュで照合して不一致とする（BR2.5、BR2.8）。ハッシュは返さない（ADR-001）。
    - `findById(userId)` → `Optional<UserSummary>`（BR4.5 の要求ごとの読み取り、更新の応答に使う）。
    - `createUser(email, Password, admin)`（ハッシュにして保存し、同じトランザクションで出来事 `UserCreatedEvent(userId)` を知らせる。7章の C8）。
    - `DummyPasswordHash`: 起動時に設定の cost でダミーのハッシュを1つ作り、照合1回の時間を測って INFO（`bcryptCost`・`elapsedMs`）、100〜500 ミリ秒の外なら WARN（NFR2.1、observability-design 1章）。
    - `PasswordEncoder` の Bean は `BCryptPasswordEncoder`（cost は設定）。
    - `InitialAdminInitializer`: Flyway の後、要求の受け付けの前（`SmartInitializingSingleton` など、Web サーバーが待ち受けを始める前に動く形）に、設定を検査し、無い・不正なら作らずに WARN（`reason` に足りない・正しくない項目、直し方の文。パスワードの値は出さない）、既にいれば何もしない、いなければ管理者として作り INFO（`email`）。例外で起動を止めない（BR1.1〜BR1.5、NFR6.1、NFR10.3）。
  - 対応: FR3.1〜FR3.4、BR1.1〜BR1.5、BR2.1、BR2.5、BR2.8、BR3.1〜BR3.6、BR4.2、BR5.1、BR5.3、BR7.1、BR7.2、BR9.1、NFR2.1〜NFR2.3、NFR3.1、NFR3.6、NFR5.2、NFR10.3

- [ ] **Step 8 — ドメインの決まりと UserAccount のテスト**
  - 単体テスト（`*Test`）:
    - `EmailAddressTest`（jqwik: 正規化は2回行っても同じ、前後の空白と大文字によらず同じ値。境界: 空白だけ、全角の空白を除かない（trim の範囲を明記））。
    - `PasswordPolicyTest`（11 文字・12 文字、72 バイト・73 バイト、多バイト文字での境界。jqwik: 72 バイト以内かの判定が UTF-8 のバイト数と一致する）。
    - `LockPolicyTest`（jqwik: しきい値−1 回まではロックしない、しきい値ちょうどでロックし解除時刻＝現在＋時間、ロック中は一致しても拒否し状態を変えない、解除時刻ちょうど・以後は 0 から判定、成功で 0。境界の例を明示の例としても書く）。
    - `TokenExpiryTest`（jqwik: `now < expiresAt` と同値。4分59秒は有効、5分ちょうどは無効）。
    - `RefreshTokenValuesTest`（長さ、URL で使える文字だけ、2回の生成で異なる、ハッシュは 32 バイトで同じ値から同じハッシュ）。
    - `SecretTypesTest`（`Password`・`RefreshTokenValue`・`AccessTokenValue` と秘密を持つ設定の `toString` に値が出ない）。
    - `AuthProblemTypesTest`（4つの code・状態コード・日英がそろう）、`TokenAuthenticationExceptionTest`（`AuthenticationException` の子で、区分を返し、メッセージにトークンが無い）。
    - `UserAccountServiceTest`（repository と照合をモックにする: いる・一致、いる・不一致、いない（ダミーで照合1回）、73 バイト（照合の仕組みに渡さずダミーで1回、不一致）、ハッシュを返さない、作成で出来事を知らせる）。
    - `InitialAdminInitializerTest`（`LogEvents` で: 無い・メールアドレスの形式の誤り・11 文字・73 バイトで作らずに WARN、理由と直し方が載る、パスワードの値がどのログにも無い、既にいれば何もしない、作れば INFO）。
    - `DummyPasswordHashTest`（範囲内は INFO、範囲外は WARN）。
    - `cherry.mastersmith.auth.AuthBoundaryArchitectureTest`（ArchUnit: `auth` は `user.domain`・`user.repository` に依存しない、`user` は `auth` に依存しない、`auth`・`user` は `cherry.mastersmith.audit` に依存しない、`User` のパスワードのハッシュを読むのは `user` の中だけ）。
  - 結合テスト（`*IT`）: `InitialAdminIT`（2回起動しても1人だけ、保存したハッシュが bcrypt の形式で cost 12、設定が無くても起動する、大文字を含むメールアドレスが小文字で保存される、作成と同時にロックの状態の行ができる）。
  - `unit-test-instructions.md` の Step 8 のコマンドで実行し、すべて通す。

- [ ] **Step 9 — Authentication の業務処理（鍵、トークン、ログイン、更新、ログアウト、削除の定期実行）**
  - `SigningKeyProvider`: `mastersmith.auth.signing-key` を Base64 から戻し、無い・戻せない・32 バイト未満なら起動を止める（例外のメッセージとログには設定の名前と必要な長さだけを載せ、値を載せない。NFR3.3、NFR6.2）。
  - `AccessTokenService`:
    - 発行: HS256 の JWT（`sub`＝利用者ID、`iat`、`exp`＝発行＋有効期限だけ。BR4.1）。Spring Security の `NimbusJwtEncoder` を使う。
    - 検証: `NimbusJwtDecoder` を HS256 だけを受け付ける形で1つ作って使い回す。時刻の確認は注入した `Clock` で、ずれの許容は 0。失敗を区分に分けて `TokenAuthenticationException` にする（形式の誤り → `TOKEN_MALFORMED`、署名・方式の不一致と `alg: none` → `TOKEN_INVALID`、期限切れ → `TOKEN_EXPIRED`。BR4.2、BR4.3、NFR3.2）。
  - `LoginAttemptStateInitializer`: `UserCreatedEvent` を同じトランザクションで受けて、利用者のロックの状態の行（失敗 0）を作る（C8）。
  - `LoginService.login(LoginCommand, ClientInfo)` の順番（security-design 8章・reliability-design 1章。承認済みの `functional-spec.md` WF2 と順番が違う。7章の D3）:
    1. `UserAccountService.verifyPassword`（利用者の検索1回と照合1回。トランザクションと排他の外）。
    2. 1回の短いトランザクションで、利用者がいればその行を、いなければダミーの行を排他つきで読み（行が無ければ作ってから読む）、`LockPolicy.decide` で判定し、明示の更新を1回行う。成功ならアクセストークンを発行し、リフレッシュトークンを作って保存する（BR5.7: ログインのたびに新しい行）。
    3. 同じトランザクションの中で `AuthenticationEvent`（成功は `LOGIN_SUCCEEDED`、失敗は `LOGIN_FAILED` と理由）を知らせる（`ApplicationEventPublisher`）。受け取り側は確定の後に同じスレッドで記録する（U4）。U2 は受け取り側を知らない（BR7.3）。
    4. ロックした試みでは、INFO で `userId` だけをログに出す（NFR10.4）。
    5. 失敗は理由によらず `BusinessException(AUTHENTICATION_FAILED)`（detail なし。BR2.4）。
    - 3つの失敗の経路（いない・ロック中・誤り）と 72 バイト超えで、検索1・照合1・排他つきの読み取り1・書き込み1がそろう（BR2.7、NFR4.2）。
  - `TokenRefreshService.refresh(RefreshTokenValue)`: ハッシュで探し、有効か（`revokedAt` が無く `now < expiresAt`）を確かめ、条件付きの無効化が 1 のときだけ続ける（0 なら失敗）。新しいリフレッシュトークンを今の時刻から数えて作り、利用者を読み、新しいアクセストークンと利用者の要約を返す。無効化と新しい行の保存は同じトランザクション。失敗は理由によらず `BusinessException(REFRESH_FAILED)`。ほかのトークンは無効にしない（BR5.3〜BR5.6）。
  - `LogoutService.logout(RefreshTokenValue or 無し, ClientInfo)`: 有効ならそのトークンだけを無効にし、`LOGGED_OUT`（そのトークンの利用者のメールアドレスと ID）を知らせる。無い・無効なら何もしない（BR6.1、BR6.2）。アクセストークンは失効させない（BR4.6）。
  - `RefreshTokenCleanupJob`: `@Scheduled(cron = 設定)` で、保存の日数を過ぎた行を件数の上限ごとに消し、件数を INFO（`deleted`）、失敗は ERROR（スタックトレース付き）で出して次の回に任せる（NFR1.7）。
  - `auth.domain.ClientInfo`（接続元IP・User-Agent・トレースID を持つ record）を業務処理の引数に受け取る。要求からの取り出しは Step 11 の `web` で行う。
  - 対応: FR4.1〜FR4.5、FR5.1、FR5.2、FR6.1、FR6.2、FR7.1〜FR7.5、BR2.3〜BR2.7、BR3.1〜BR3.9、BR4.1〜BR4.3、BR4.6、BR5.1〜BR5.7、BR6.1、BR6.2、BR7.1〜BR7.3、NFR1.5、NFR1.7、NFR3.2〜NFR3.4、NFR4.1、NFR4.2、NFR9.1、NFR9.2、NFR10.1、NFR10.2、NFR10.4

- [ ] **Step 10 — Authentication の業務処理のテスト**
  - 単体テスト（`*Test`、`Clock` は固定の時計、repository・UserAccount はモック）:
    - `SigningKeyProviderTest`（32 バイトは使える、31 バイト・無い・Base64 でない値は失敗し、例外のメッセージに値が無い）。
    - `AccessTokenServiceTest`（発行した中身が `sub`・`iat`・`exp` だけ、4分59秒は有効・5分ちょうどは `TOKEN_EXPIRED`、署名の1文字の改ざん・別の鍵は `TOKEN_INVALID`、`alg: none`・HS512・RS256 は `TOKEN_INVALID`、形の崩れた値は `TOKEN_MALFORMED`）。
    - `LoginServiceTest`（成功で失敗回数 0・トークン2つ・`LOGIN_SUCCEEDED`、誤りで回数＋1・`PASSWORD_MISMATCH`、しきい値ちょうどでロックと INFO（`userId` だけ）、ロック中は一致しても `ACCOUNT_LOCKED` で値を変えない書き込み1回、解除時刻を過ぎたら 0 から判定、いないメールアドレスはダミーの行を使い `USER_NOT_FOUND`、出来事の項目がそろう、失敗の例外が理由によらず同じ）。
    - `TokenRefreshServiceTest`（成功で古い行を無効にし新しい行の期限は今から 24 時間、23時間59分59秒は有効・24 時間ちょうどは失敗、無効化の結果が 0 なら失敗、存在しない・使用済みで失敗し、ほかの行を触らない）。
    - `LogoutServiceTest`（有効なら無効にして `LOGGED_OUT`、無い・無効・期限切れなら何もせず出来事も無い、ほかの行を触らない）。
    - `RefreshTokenCleanupJobTest`（件数を INFO、失敗は ERROR で例外を外へ出さない）。
  - 結合テスト（`*IT`、組み込みの H2）:
    - `LoginConcurrencyIT`: 同じ利用者で同時に 5 回の失敗（合図でそろえる）→ 失敗回数 5 でロック、同時に 4 回ならロックしない。別の利用者のログインは待たされない（NFR1.5、NFR9.1）。
    - `RefreshConcurrencyIT`: 同じリフレッシュトークンで同時に2回の更新 → 1つだけ成功（NFR9.2）。
    - `SigningKeyStartupIT`: 鍵が無い・短いとアプリの起動が失敗し、出力に鍵の値が無い（NFR3.3、NFR6.2）。
    - `AuthSettingsIT`: 設定を変えた起動（しきい値 3、有効期限など）が効く、範囲外の値（しきい値 0 など）で起動が失敗する（NFR6.3）。
  - `unit-test-instructions.md` の Step 10 のコマンドで実行し、すべて通す。

### 4.6 API・エンドポイント

- [ ] **Step 11 — 認証の API、Cookie、Origin の確認、フィルターの連鎖への決まり**
  - `cherry.mastersmith.auth.web.AuthController`（パスは security-design 3章。形は 7章の C2）:
    - `POST /api/auth/login`: `LoginRequest(email, password)`（`@NotBlank`。空は U1 の変換で 400 / `VALIDATION_FAILED`。`toString` でパスワードを伏せる）→ 200 `TokenResponse(accessToken, expiresAt, user: CurrentUserResponse(email, admin))`（`toString` でトークンを伏せる）と、リフレッシュトークンの Cookie。
    - `POST /api/auth/session/refresh`: Origin の確認 → Cookie の値で更新 → 200 `TokenResponse` と新しい Cookie。失敗は 401 / `REFRESH_FAILED` と Cookie の削除。
    - `POST /api/auth/session/logout`: Origin の確認 → 204（内容なし）と Cookie の削除。Cookie が無い・無効でも同じ応答。
    - 応答の組み立ては U1 の決まりのとおり、エラーは `BusinessException` を起こして `GlobalExceptionHandler` の1か所で変換する。Cookie を消す指示は、例外を起こす前に応答のヘッダーに入れておく（例外の変換の後もヘッダーが残ることを Step 12 で確かめる。残らない場合は生成を止めて案を示す）。
  - `RefreshCookies`: 名前 `mastersmith_refresh`、`HttpOnly`・`Secure`・`SameSite=Strict`・`Path=/api/auth/session`・`Max-Age`＝有効期限。削除は同じ名前・同じ Path・`Max-Age=0`（NFR5.3）。
  - `ClientInfoResolver`: 要求から `ClientInfo` を作る（接続元IP＝要求の接続元。転送元のヘッダーは U1 の信頼の設定があるときだけ `ForwardedHeaderFilter` が反映したもの。User-Agent は 512 文字で切る。トレースIDは U1 の `TraceIdProvider`）。
  - `OriginVerifier`: 要求の `Origin` を、U1 の `ProblemBaseUrlResolver` と同じ決め方の自分の配信元と比べ、無い・一致しないなら `BusinessException(ORIGIN_NOT_ALLOWED)`（403）。WARN で `originPresent` だけを出す（Cookie の値は出さない。NFR5.4）。
  - `AuthSecurityContributor`（U1 の `SecurityRuleContributor`、order 110。100 は U1 のテストの役の決まりが使うため避ける。7章の C3）:
    - `/api/auth/login` と `/api/auth/session/**` を認証なしにする。
    - OAuth2 Resource Server の Bearer の検証を足す。トークンは `Authorization: Bearer` のヘッダーからだけ取り出し、`/api/auth/**` では取り出さない（期限切れのトークンが付いていても、ログイン・更新・ログアウトを妨げない）。
    - 検証は U2 の `AccessTokenAuthenticationProvider` で行う: `AccessTokenService` で検証し、利用者を `UserAccountService.findById` で読み、`AuthenticatedUser`（管理者は DB の値）を主体とする認証の結果を作る。利用者がいなければ `TokenAuthenticationException(USER_NOT_FOUND)`（BR4.5）。
  - `TokenAuthenticationEntryPoint`: 401 / `AUTHENTICATION_REQUIRED` を U1 の `ErrorResponseWriter` で書き、区分（`TokenAuthenticationException` なら `reason()`、そうでなければ `TOKEN_MISSING`）を DEBUG で出す（トークンの値は出さない。NFR10.5）。U3 ができるまでの入口の受け持ちは 7章の D2 のとおり。
  - 対応: FR4.1、FR4.4、FR4.5、FR5.1、FR5.2、FR6.1、FR6.2、BR2.2〜BR2.4、BR4.3〜BR4.6、BR5.2、BR5.5、BR6.1、BR9.1、NFR3.5、NFR4.1、NFR5.3〜NFR5.5、NFR10.5、U3 の security-design 3章の約束

- [ ] **Step 12 — API のテスト**
  - 単体テスト（`*Test`）: `ClientInfoTest`（User-Agent を 512 文字で切る、無ければ無し、トレースIDが無ければ無し）、`OriginVerifierTest`（一致・不一致・無し・ポートの違い）、`RefreshCookiesTest`（属性と Path、削除が同じ名前・Path・`Max-Age=0`）、`TokenAuthenticationEntryPointTest`（401 / `AUTHENTICATION_REQUIRED`、区分の DEBUG、トークンの値が無い）。
  - 結合テスト（`*IT`。実際の番号で待ち受けるアプリに `HttpTestClient` で送る）:
    - `LoginApiIT`: 成功（200、本文の形、Cookie の属性と Path、アクセストークンで保護された API を呼べる）、空のメールアドレス・空のパスワードで 400 / `VALIDATION_FAILED`（応答にパスワードが無い）、いない・誤り・ロック中・73 バイトの4通りで状態コード・`code`・`type`・`title`・`detail` が同じ（`traceId`・`instance` を除く）、4通りの SQL の回数と種類・照合の回数が同じ（`SqlStatementCounter`・`CountingPasswordEncoder`）、しきい値−1 回ではロックされない・しきい値ちょうどでロック・ロック中は正しいパスワードでも 401・30 分後（`MutableClock`）に成功、成功で失敗回数が 0 に戻る。
    - `TokenApiIT`: 更新の成功（新しいトークンと Cookie、古い Cookie での再度の更新は 401 / `REFRESH_FAILED` と Cookie の削除で、発行と削除の名前・Path が同じ）、Cookie が無い更新は 401、期限切れ（`MutableClock` で 24 時間ちょうど）は 401、ほかのブラウザのトークンは使い続けられる、Origin の一致は受け付け・不一致と無しは 403 / `ORIGIN_NOT_ALLOWED`（更新とログアウトの両方）。
    - `LogoutApiIT`: ログアウトで 204 と Cookie の削除、そのリフレッシュトークンでの更新は 401、ログアウト後も期限内のアクセストークンで保護された API を呼べる（決定済みの仕様 BR4.6 として明示）、Cookie が無くても 204、ほかのブラウザのトークンは有効のまま。
    - `AccessTokenApiIT`: テスト用の保護された窓口（テストのソースの中の、ログインを求める `SecurityRuleContributor` と窓口。`mastersmith.test-fixture.*` の条件つき）で、トークン無し・形の崩れ・改ざん・`alg: none`・期限切れ（5 分ちょうど）・利用者が DB にいない、がすべて 401 / `AUTHENTICATION_REQUIRED`、4分59秒は通る、管理者のフラグは DB の値（DB で変えるとすぐ反映）、DEBUG のログに区分が出てトークンの値が無い、入口の処理に届く例外が `TokenAuthenticationException` で区分を持つ（U3 との約束）。
    - `AuthEventsIT`: 成功・失敗（3つの理由）・ログアウトの出来事の項目（日時・種類・メールアドレス・利用者ID・理由・接続元IP・User-Agent・トレースID）がそろう、存在しないメールアドレスの失敗も知らせる、無効な Cookie のログアウトでは知らせない、確定の後に例外を起こす受け取り側（テスト用の `@TransactionalEventListener(AFTER_COMMIT)`）があっても、ログイン・更新・ログアウトの応答が変わらない（NFR10.1）。
    - `AuthSecretLeakIT`: `OutputCaptureExtension` と `JsonLogRecords.assertContainsNoSecret` で、TRACE を有効にして（`cherry.mastersmith.auth`・`cherry.mastersmith.user` のロガー）起動（初期管理者の作成）・ログイン・失敗・更新・ログアウトを行い、パスワード・パスワードのハッシュ・アクセストークン・リフレッシュトークン・署名鍵の値がログに無い。エラー応答と出来事にも無い（NFR3.1、NFR3.5、NFR3.6）。
    - `AuthProblemTypesIT`: U2 の4つの問題の種類が U1 の説明ページ（`/api/problems/...`）に日英で出る。
  - U1 の構造の検査（`cherry.mastersmith.ArchitectureTest`）と U1 の結合テスト全体が U2 を足しても通ることを、Step 12 の最後に一度確かめる（U2 のコマンドとは別に。回帰の確認）。
  - `unit-test-instructions.md` の Step 12 のコマンドで実行し、すべて通す。

### 4.7 画面（フロントエンド）

- [ ] **Step 13 — ApiClient、認証の API の呼び出し、AuthSession**
  - `frontend/src/shared/api-client/`（ApiClient。AuthUi に依存しない。ADR-007。U3 も使う。7章の C5）:
    - `apiClient.ts`: `registerAuthHandlers({ getAccessToken, refresh, onUnauthenticated })`、`apiFetch(path, init)`（同じオリジンの API を呼ぶ。アクセストークンがあれば `Authorization: Bearer` を付ける）。401 / `AUTHENTICATION_REQUIRED` を受けたら更新を1回だけ行い（同時の 401 は1つの更新にまとめる）、成功したら元の要求を1回だけ送り直す。送り直しでまた 401、または更新の失敗なら `onUnauthenticated` を呼ぶ。`/api/auth/login`・`/api/auth/session/refresh`・`/api/auth/session/logout` は明示的に対象外（トークンを付けず、更新と送り直しもしない。BR8.5）。
    - `apiError.ts`: エラー応答を `{ status, code }` の形にする（本文が Problem Details でなければ `code` なし）。通信の失敗は別の種類にする。
  - `frontend/src/features/auth/`（featureId `auth`。AuthUi）:
    - `authApi.ts`: ログイン・更新・ログアウトの呼び出し（`credentials: 'same-origin'`）。
    - `authSession.ts`: 状態（`Restoring`・`LoggedIn`・`LoggedOut`）、アクセストークンと利用者の要約をモジュールの中の変数（メモリ）だけに持つ（localStorage・sessionStorage・Cookie に置かない。BR8.3）。`restore()`（起動時に更新を1回。BR8.4）、`login()`、`logout()`（API の結果によらず破棄。BR8.6）、`subscribe()`。起動時に ApiClient へトークンの取得と更新の手段を登録する。
    - `loginStateProvider.ts`: U1 の `LoginStateProvider`（`getLoginState()` は最初の復元の結果を待って返す、`subscribe()` は状態の変化を知らせる。`displayName` はメールアドレス、`admin` は CurrentUserView の値で表示の切り替えにだけ使う。BR8.7）。
    - `validateLoginInput.ts`: 空の入力の検査（純粋な関数。BR8.2）。
  - 対応: FR5.3、FR6.1、BR8.3〜BR8.7、NFR5.1、performance-design 5章

- [ ] **Step 14 — ApiClient と AuthSession のテスト**
  - `apiClient.test.ts`（`fetch` を `vi.fn` で置き換える）: トークンを付ける、認証の API には付けない、401 / `AUTHENTICATION_REQUIRED` で更新して1回送り直す、同時の3つの 401 で更新は1回、送り直しの 401 で `onUnauthenticated`、更新の失敗で `onUnauthenticated`、認証の API の 401 は更新しない、`AUTHENTICATION_REQUIRED` 以外の 401・403 は更新しない。fast-check: 同時の 401 の数によらず更新は1回。
  - `apiError.test.ts`: Problem Details・そうでない本文・通信の失敗の変換。
  - `authApi.test.ts`: パスとメソッド、`credentials`、成功と失敗の変換。
  - `authSession.test.ts`: 復元の成功で LoggedIn・失敗で LoggedOut、ログインの成功でメモリに持ち localStorage・sessionStorage に何も無い、ログアウトは API が失敗しても破棄して LoggedOut、更新の失敗の知らせで LoggedOut、状態の変化で `subscribe` の受け取りが呼ばれる。
  - `loginStateProvider.test.ts`: 復元の結果を待って返す、未ログインでは admin が false、表示名がメールアドレス。
  - `validateLoginInput.test.ts`（fast-check: 空白だけ・空は拒否、それ以外は受け付け。パスワードの長さは検査しない）。
  - `unit-test-instructions.md` の Step 14 のコマンドで実行し、すべて通す。

- [ ] **Step 15 — ログイン画面、登録、文言**
  - `LoginPage.tsx`: U1 の `LoginLayout` の中に `LoginForm` を置く（BR8.1）。
  - `LoginForm.tsx`（make-you-chic-ui の `FormField`・`TextInput`・`Button`・`Alert`）: メールアドレス（`type="email"`、`autocomplete="username"`）、パスワード（`type="password"`、`autocomplete="current-password"`）、ログインボタン。空なら送らずに入力欄の近くに知らせる（`aria-invalid`・`aria-describedby`）。送信中はボタンを押せない。成功で AuthSession に反映してからホーム（`/`）へ移る。`AUTHENTICATION_FAILED` なら理由によらず1種類の文言（`role="alert"`）を出し、パスワード欄を空にする。それ以外のエラー・通信の失敗は一般的な文言（BR8.2、BR8.8、NFR4.3）。`data-testid`: `login-form-email-input`、`login-form-password-input`、`login-form-submit-button`、`login-form-error-alert` など。
  - `LoginForm.css`（素の CSS。make-you-chic-ui の CSS 変数を使う）。
  - `registration.ts`: `featureId: 'auth'`、画面 `/login`（`role: 'LOGIN'`、`layout: 'STANDALONE'`、`access: 'PUBLIC'`、画面の部品は遅延読み込み）、ユーザーメニューの項目「ログアウト」（`id: 'auth-logout'`、`action` で AuthSession のログアウト。ログイン画面への移動は U1 の振り分けが状態の変化で行う）、ログイン状態の提供元、文言（鍵は `auth.` で始め、日英をそろえる: 入力欄のラベル、ボタン、空の入力の文言、失敗の文言、一般的なエラー、ログアウト）。
  - 対応: FR2.1、FR2.4、FR6.1、BR8.1、BR8.2、BR8.6〜BR8.8、NFR7.1、NFR8.1

- [ ] **Step 16 — ログイン画面と登録のテスト**
  - `LoginForm.test.tsx`（user-event）: 空のメールアドレス・空のパスワードで送らずに文言が出る、成功で `/` へ移る、`AUTHENTICATION_FAILED` で1種類の文言とパスワード欄が空になる（ロック中の応答も同じ文言。team.md の「ロック時のメッセージ表示」）、通信の失敗で一般的な文言、送信中はボタンを押せない、英語の表示で英語の文言、vitest-axe で違反なし。
  - `LoginPage.test.tsx`: ログイン用レイアウトの中にフォームがある、vitest-axe で違反なし。
  - `registration.test.ts`: U1 の `validateRegistrations` を通る（role=LOGIN・STANDALONE・PUBLIC、文言の鍵が日英でそろう）、ログアウトの項目で AuthSession のログアウトが呼ばれトークンが破棄される。
  - `unit-test-instructions.md` の Step 16 のコマンドで実行し、すべて通す。

### 4.8 環境・ビルドの設定

- [ ] **Step 17 — 環境変数の見本と README**
  - `.env.example`: U1 が置いた「U2（認証）で使う秘密情報」の節を確定した名前にする。`MASTERSMITH_AUTH_SIGNING_KEY=`（値は空。作り方 `openssl rand -base64 32` をコメントで）、`MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL=`、`MASTERSMITH_AUTH_INITIAL_ADMIN_PASSWORD=`（12 文字以上、UTF-8 で 72 バイト以内）。任意の設定（有効期限・しきい値・cost・削除）は行ごとコメントのまま名前と既定値を書く。
  - `README.md` の節（U1 の節の構成は変えず、U2 の行と節を足す）: 環境変数の表に U2 の設定、コンテナでの初めての起動（署名鍵と初期管理者を `.env` に入れる、起動のログで「初期管理者を作成した」を確かめる）、署名鍵の交換（`.env` を替えて作り直す。発行済みのアクセストークンは 401 になり、画面は更新で取り直す）、配備の確認のスモークテスト（初期管理者でログインとログアウト）、開発・E2E は `http://localhost` で行う（`Secure` の Cookie のため）、後の単位が使う差し込み口の表に U2 が提供するもの（`AuthenticatedUser`、`TokenAuthenticationException`、`AuthenticationEvent`、ApiClient）を足す。
  - `compose.yaml` は変えない（CPU の上限 4 とタイムゾーン `Asia/Tokyo` は U1 で反映済み）。
  - 対応: NFR3.3、NFR3.4、NFR5.5、NFR6.3、cicd-pipeline（U2）3章、infrastructure-specification（U2）3章

- [ ] **Step 18 — E2E と全体の検査**
  - `frontend/playwright.config.ts` の起動のコマンドに、テストの実行のたびに作る署名鍵と初期管理者の値を環境変数で渡す（リポジトリに値を置かない）。`frontend/e2e/u1-skeleton.e2e.ts` の問題の集め方で、起動時のトークンの更新の 401（未ログインでは必ず起きる）によるブラウザの「資源の読み込みの失敗」の表示だけを除く（7章の D1）。
  - `frontend/e2e/u2-auth.e2e.ts`: 初期管理者でログイン → ホームが表示される → 再読み込みしてもログインしたまま（FR5.3）→ ユーザーメニューのログアウト → ログイン画面に戻る → 再読み込みしても未ログイン。誤ったパスワードで1種類の文言が出る。CSP の違反とスクリプトのエラーが無い（起動時の更新の 401 を除く）。管理画面に入れるかの確認は U3 で足す（7章の C9）。
  - `./gradlew verify`（0〜9 の段すべて。カバレッジの下限、SpotBugs の High 0 件、OSV-Scanner、Gitleaks を含む）と `./gradlew e2eTest` と `pre-commit run --all-files` がすべて通ることを確かめる。通らなければ、下限を下げずにテストを足すか、差を依頼者に示す。
  - `docker compose up -d --build` で、`.env` に署名鍵と初期管理者を入れて起動し、ヘルスチェック・初期管理者の作成のログ・ログインとログアウト（スモークテスト）を確かめる。
  - 対応: NFR8.1、NFR9.3、cicd-pipeline（U2）1章〜3章、team.md Testing Posture（E2E）

### 4.9 文書とトレーサビリティ

- [ ] **Step 19 — まとめの記録**
  - `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u2-authentication/code-generation/` に `code-summary.md`（作った・変えたファイル、判断、テストとカバレッジ、`./gradlew verify` の結果、計画からの逸脱、U3・U4 への申し送り。security-design 8章の2点を Functional Design との違いとして記録する）、`traceability.json`（FR・BR・NFR から実装とテストへの対応）、`source-manifest.json`（作った・変えた・消したアプリのソースのパスの一覧）を書く。
  - 対応: 全要件のトレーサビリティ

## 5. 要件と手順の対応（トレーサビリティ）

| 要件・決まり | 実装の手順 | テストの手順 |
|---|---|---|
| FR2.1 ログイン画面（BR8.1、NFR7.1、NFR8.1） | Step 15 | Step 16、Step 18 |
| FR2.4 ログイン失敗の同じ表示（BR8.2、NFR4.3） | Step 15 | Step 16、Step 18 |
| FR3.1〜FR3.4 初期管理者（BR1.1〜BR1.5、NFR2.1、NFR2.2、NFR6.1、NFR10.3） | Step 1、Step 3、Step 7 | Step 4、Step 8、Step 12（秘密情報） |
| FR4.1 ログインとトークンの受け渡し（BR2.1〜BR2.3、BR4.1、BR5.1、BR5.2、BR5.7、NFR5.2、NFR5.3） | Step 7、Step 9、Step 11 | Step 8、Step 10、Step 12 |
| FR4.2 アクセストークンの有効期限（BR4.2） | Step 7、Step 9 | Step 8、Step 10、Step 12 |
| FR4.3 リフレッシュトークンの有効期限（BR5.3） | Step 7、Step 9 | Step 8、Step 10、Step 12 |
| FR4.4 無効なトークンの拒否（BR4.3〜BR4.5、NFR3.2、NFR10.5） | Step 9、Step 11 | Step 10、Step 12 |
| FR4.5 ログイン失敗の同じ応答（BR2.4、BR2.5、BR2.7、BR2.8、NFR2.3、NFR4.1、NFR4.2） | Step 5、Step 7、Step 9 | Step 6、Step 8、Step 10、Step 12 |
| FR5.1 更新と作り直し（BR5.4、BR5.6、NFR9.2） | Step 5、Step 9、Step 11 | Step 6、Step 10、Step 12 |
| FR5.2 無効なリフレッシュトークンの拒否（BR5.5、BR5.8、NFR5.4） | Step 9、Step 11 | Step 10、Step 12 |
| FR5.3 再読み込みでのログイン状態の継続（BR8.4、BR8.5） | Step 13 | Step 14、Step 18 |
| FR6.1 ログアウト（BR6.1、BR6.2、BR8.6、NFR5.4） | Step 9、Step 11、Step 13、Step 15 | Step 10、Step 12、Step 14、Step 16、Step 18 |
| FR6.2 ログアウト後もアクセストークンは期限まで有効（BR4.6） | Step 9、Step 11 | Step 12 |
| FR7.1〜FR7.5 アカウントロック（BR3.1〜BR3.9、NFR1.5、NFR9.1、NFR10.4） | Step 1、Step 3、Step 5、Step 7、Step 9 | Step 6、Step 8、Step 10、Step 12 |
| 出来事の通知（BR2.6、BR7.1〜BR7.3、NFR10.1、NFR10.2） | Step 7、Step 9 | Step 10、Step 12 |
| 問題の種類（BR9.1、security-design 8章の `ORIGIN_NOT_ALLOWED`） | Step 7、Step 11 | Step 8、Step 12 |
| 画面のトークンの保持とログイン状態（BR8.3、BR8.7、NFR5.1） | Step 13、Step 15 | Step 14、Step 16 |
| 秘密情報を出さない（NFR3.1、NFR3.5、NFR3.6、BR1.4、BR7.2） | Step 1、Step 7、Step 9、Step 11、Step 17 | Step 8、Step 12 |
| 設定と起動（NFR3.3、NFR3.4、NFR6.2、NFR6.3） | Step 1、Step 9、Step 17 | Step 10、Step 18 |
| 使い終わったトークンの削除（NFR1.7、NFR1.8） | Step 5、Step 9 | Step 6、Step 10 |
| 性質ベースのテスト・時刻（NFR9.4、NFR9.5） | Step 1、Step 2、Step 7 | Step 8、Step 10 |
| チームの必須の認証のテスト（NFR9.3） | — | `unit-test-instructions.md` 7章の対応表 |
| 性能（NFR1.1〜NFR1.4、NFR1.6） | Step 7（照合の時間のログ）、Step 9 | Performance Validation で測る（code-summary と traceability で Deferred とする） |

## 6. 単位どうしのつなぎ目

本ワークフローは Contract Design を行わないため、U2 が使う約束と U2 が提供する約束をここに記録する。

### 6.1 U2 が使う U1 の約束（U1 のファイルは書き換えない）

| つなぎ目 | U2 の使い方 |
|---|---|
| `SecurityRuleContributor` | `AuthSecurityContributor`（order 110）で、認証の API の公開、Bearer の検証、入口の処理を足す。ヘッダー・セッション・CSRF は変えない |
| `ErrorResponseWriter` | 401 / `AUTHENTICATION_REQUIRED` を書く |
| `BusinessException` と `ProblemTypeCatalog` | `AUTHENTICATION_FAILED`・`REFRESH_FAILED`・`ORIGIN_NOT_ALLOWED` を起こす。4つの問題の種類を `AuthProblemTypeCatalog` に定義する |
| `ProblemBaseUrlResolver` | Origin の確認の「自分の配信元」を同じ決め方で得る |
| `TraceIdProvider` | 出来事のトレースID |
| `common.testsupport`（`JsonLogRecords`・`LogEvents`・`HttpTestClient`・`TestDatabase`） | 秘密情報がログに出ないこと、ログの項目、API の結合テスト |
| 画面の差し込み口（`FeatureRegistration`、`LoginStateProvider` の `getLoginState()` と `subscribe()`、`messages`） | `frontend/src/features/auth/registration.ts` |
| `LoginLayout` | ログイン画面の枠 |
| Flyway のファイルの名前 | `V2__u2_user_account.sql`、`V3__u2_authentication.sql` |

### 6.2 U2 が提供する約束

| つなぎ目 | 形 | 使う単位 |
|---|---|---|
| 検証済みの利用者 | `cherry.mastersmith.auth.domain.AuthenticatedUser`（`userId`・`email`・`admin`）。Spring Security の認証の結果の主体として要求ごとに置く。`admin` は要求ごとに DB から読んだ値 | U3 |
| トークンの認証の失敗 | `cherry.mastersmith.auth.domain.TokenAuthenticationException`（`OAuth2AuthenticationException` の子、つまり `AuthenticationException` の子）と `TokenFailureReason`（`TOKEN_MALFORMED`・`TOKEN_INVALID`・`TOKEN_EXPIRED`・`USER_NOT_FOUND`）。トークンが無い要求は U2 の検証を通らず、Spring Security の「認証が足りない」の例外で入口の処理へ届く（U3 が `TOKEN_MISSING` とみなす）。U2 は区分を DEBUG で出す | U3 |
| 401 の入口の処理 | 7章の D2 のとおり（U3 が order 200 台の決まりで置き換える） | U3 |
| 認証の出来事 | `cherry.mastersmith.auth.domain.AuthenticationEvent` を `ApplicationEventPublisher` で U2 のトランザクションの中で知らせる。受け取り側は `@TransactionalEventListener(phase = AFTER_COMMIT)` で確定の後に同じスレッドで記録する（取り消されたら記録しない）。U2 は U4 を参照しない | U4 |
| 時計 | `Clock` の Bean（UTC）。U4 などもこれを使い、別に定義しない（7章の C4） | U3、U4 |
| API 呼び出しの共通部分 | `frontend/src/shared/api-client/` の `apiFetch` と `{ status, code }` のエラー。トークンの付与と 401 の更新・送り直しを含む | U3（画面） |

## 7. 判断を仰いだ点（決定済み）

依頼者の回答（`code-generation-questions.md` の Q1〜Q4）により、次のとおり決まった。

| 点 | 決定 |
|---|---|
| D1 U1 のファイルの最小限の変更 | A: 3か所（`backend/build.gradle.kts`・`frontend/playwright.config.ts`・`frontend/e2e/u1-skeleton.e2e.ts`）を変え、`code-summary.md` に記録する |
| D2 401 の入口の処理 | A: U2 が `TokenAuthenticationEntryPoint` を Bearer の検証の失敗と未認証の両方の入口に置き、U3 が order 200 台で置き換える（U3 への申し送り） |
| D3 Functional Design との違い | A: NFR Design のとおりに作り、Functional Design は書き換えず、違いを `code-summary.md` と `traceability.json` に記録する |
| C1〜C9 | A: すべて下の内容のとおり進める。ただし C1 は、計画の承認の際の修正依頼（「全単位で共用するものなので `application.yaml` を変えてよい」）により、設定を `application.yaml` に書く形に変えた |

以下は、決定の前に示した案の内容である。

### 決定が必要

#### D1 — U1 のファイルの最小限の変更（3か所）

U2 を足すと、次の3か所は U1 のファイルを変えないと成り立たない。

| ファイル | 変更 | 理由 |
|---|---|---|
| `backend/build.gradle.kts` | 依存の一覧に `implementation(libs.spring.boot.starter.oauth2.resource.server)` の1行を足す | JWT の発行と検証（tech-stack-decisions（U2））。版は `libs.versions.toml` と lockfile で固定する |
| `frontend/playwright.config.ts` | WAR の起動のコマンドに、実行のたびに作る署名鍵と初期管理者の値を環境変数で渡す | 署名鍵が無いとアプリが起動しない（NFR3.3）。E2E のログインに初期管理者が要る |
| `frontend/e2e/u1-skeleton.e2e.ts` | ブラウザのエラーの集め方で、起動時のトークンの更新の 401 による「資源の読み込みの失敗」の表示だけを除く | 未ログインで画面を開くと、BR8.4 により更新を1回試み、401 / `REFRESH_FAILED` になる。Chromium はこれをエラーとして表示するため、U1 の「エラーが無い」の確認が必ず失敗する |

- **A（推奨）**: 上の3か所を変える。変更は U2 の手順（Step 1・Step 18）で行い、`code-summary.md` に記録する。
- **X. Other (please specify)**

#### D2 — U3 ができるまでの 401 の入口の処理の受け持ち

U3 の設計（`construction/u3-access-control/nfr-design/security-design.md` 3章）は「401 の入口の処理は U3 が1つ用意する」としている。一方、U2 の決まり BR4.4 は、無効なトークンに 401 / `AUTHENTICATION_REQUIRED` を U1 の共通の組み立てで返すことを求め、U2 だけの状態（U3 の前）でもこれを確かめる必要がある。

- **A（推奨）**: U2 は `TokenAuthenticationEntryPoint`（401 / `AUTHENTICATION_REQUIRED`、区分を DEBUG）を置き、Bearer の検証の失敗と、ログインが必要な要求の未認証の両方の入口に設定する。U3 は、自分の order 200 台の決まりで、同じ2か所を自分の入口の処理（出来事の通知つき）に置き換える。U3 の Code Generation の計画にこの置き換えを書く（申し送り）。
- **B**: U2 は Bearer の検証の失敗の入口だけに設定し、トークンが無い要求は U1 の既定（本文なしの 401）のままにする（U3 が置くまで、トークンが無い 401 は共通の形にならない）。
- **X. Other (please specify)**

#### D3 — 承認済みの Functional Design との違い（security-design 8章）の揃え方

NFR Design（確定回答 Q1 と NFR5.4）により、承認済みの U2 の Functional Design と次の2点が異なる。security-design 8章は、Code Generation の計画の確認で揃えることを求めている。

| 違い | Functional Design | 本計画（NFR Design のとおり） |
|---|---|---|
| ログインの手順の順番 | `functional-spec.md` WF2: ロックの状態を排他つきで読んでから照合 | 利用者の検索 → 照合（排他の外）→ 1回の短いトランザクションでロックの状態を排他つきで読み、判定し、書き込む。ロック中は照合の結果を使わない（Step 9）。外から見える結果（応答・読み書きの回数・判定の結果）は同じ |
| 問題の種類 | `rules.md` BR9.1: 3つ | `ORIGIN_NOT_ALLOWED`（403）を加えて4つ（Step 7、Step 11） |

- **A（推奨）**: 本計画のとおり NFR Design に合わせて作る。Functional Design の文書は書き換えず、`code-summary.md` と `traceability.json` に違いとして記録する。
- **B**: 先に Functional Design をやり直し（`/aidlc --stage functional-design`）、2点を反映してから Code Generation に戻る。
- **X. Other (please specify)**

### 確認（このまま進めてよいかの確認）

- **C1 — 設定と環境変数の名前**: 必須・秘密は `MASTERSMITH_AUTH_SIGNING_KEY`、`MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL`、`MASTERSMITH_AUTH_INITIAL_ADMIN_PASSWORD`。任意は `MASTERSMITH_AUTH_ACCESS_TOKEN_TTL`（5m）、`MASTERSMITH_AUTH_REFRESH_TOKEN_TTL`（24h）、`MASTERSMITH_AUTH_LOCK_THRESHOLD`（5）、`MASTERSMITH_AUTH_LOCK_DURATION`（30m）、`MASTERSMITH_AUTH_PASSWORD_BCRYPT_COST`（12）、`MASTERSMITH_AUTH_REFRESH_TOKEN_CLEANUP_RETENTION`（7d）、`MASTERSMITH_AUTH_REFRESH_TOKEN_CLEANUP_CRON`（`0 30 3 * * *`）。reliability-design 4章の「`mastersmith.auth.*` の1つのまとまり」に合わせ、すべて `mastersmith.auth` の下に置く。全単位で共用する `application.yaml` に `${MASTERSMITH_AUTH_XXX:既定値}` の形で書く（秘密の項目は既定値を空にする。一覧は README と `.env.example`）。依頼者の指示（計画の承認の際の修正依頼）により、当初の案「`application.yaml` には書かず、既定値は Java 側に置く」から変えた。
- **C2 — API の形**: パスと Cookie は security-design 3章のとおり。ログインと更新は、リフレッシュトークンも返す。ただし応答の JSON の本文には入れず、同じ応答の `Set-Cookie` ヘッダーで Cookie `mastersmith_refresh`（`HttpOnly`・`Secure`・`SameSite=Strict`・`Path=/api/auth/session`・`Max-Age`＝有効期限）として返す。更新では、使った古いトークンを無効にし、新しいトークンを同じ Cookie で返す（NFR5.1〜NFR5.3。画面のスクリプトからリフレッシュトークンを読めないようにするため）。応答の JSON の本文は `{ "accessToken": "...", "expiresAt": "<ISO 8601 の時点>", "user": { "email": "...", "admin": false } }`。ログアウトは 204 で、同じ名前・同じ `Path` の `Max-Age=0` の Cookie で削除する。（依頼者の質問「refreshToken も返却するよね？」を受けて、書き方をはっきりさせた。）
- **C3 — U3 との約束の型と order**: 例外は `TokenAuthenticationException extends OAuth2AuthenticationException`（Spring Security の Bearer の検証の途中で起きた場合も入口の処理へ届く形にするため。U3 が求める「`AuthenticationException` の子で区分を持つ」を満たす）。`AuthSecurityContributor` の order は 110（U2 の 100 台の中で、U1 のテストの役の決まりが使う 100 を避ける）。
- **C4 — 時計**: `Clock` の Bean は U1 に無いため U2 が置く（`auth.service`）。U4 はこれを使い、別に定義しない。
- **C5 — 画面のファイルの置き場所**: AuthUi は `frontend/src/features/auth/`（featureId `auth`）。ApiClient は U3 も使う共通の部品で AuthUi に依存しないため、`frontend/src/shared/api-client/` に置く（登録用ファイルを持たない）。
- **C6 — テストの署名鍵**: Spring を起動するすべてのテスト（U1 の結合テストを含む）に、テストのソースの中の仕組み（`EnvironmentPostProcessor` と `src/test/resources/META-INF/spring.factories`）で、実行のたびに作る乱数の鍵を入れる。U1 のテストのファイルは変えない。
- **C7 — ロックの状態の表とダミーの行**: ダミーの行は同じ表の負の ID（8 行）とし、そのため `users` への外部キーを置かない。H2 での「排他の待ちの上限 3 秒」と「ほかの試みが持つ行を飛ばす」の指定は Step 6 で効くことを確かめ、効かないときは Step 5 に書いた代わりの方法にする。それでも満たせないときは生成を止めて案を示す。
- **C8 — ロックの状態の行を作る時期**: 利用者の作成（`user`）が知らせる `UserCreatedEvent` を `auth` が同じトランザクションで受けて作る（scalability-design 1章。`user` が `auth` に依存しない形）。行が無い場合の作成も残す。
- **C9 — E2E の範囲**: U2 の E2E は「ログイン → ホーム → 再読み込み → ログアウト」と失敗の文言まで。チームの代表の流れ「ログイン → 管理画面に入れるか → ログアウト」の管理画面の部分は U3 で足す。

## 8. 生成中の進め方

- 手順は番号の順に1つずつ進め、終わった手順のチェックボックスに印を付ける（印を付ける以外の本書の変更は Plan Approval をやり直す）。
- 各テストの手順では、`unit-test-instructions.md` のこの単位に絞ったコマンドで実行し、すべて通ってから次の手順へ進む。不安定なテスト（特に同時の処理のテスト）は原因を直す。
- 版の組み合わせが合わない、品質の目標に届かない、設計書と食い違う、U1 のファイルを D1 の3か所以外で変える必要がある、と分かったときは、生成を止めて案を示し、依頼者の判断を仰ぐ。
- 区切りのよいところ（例: Step 2、Step 6、Step 10、Step 12、Step 16、Step 18、Step 19 の後）でコミットを提案し、承認を得てから行う。

## 参照した文書

- 単位の設計: `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u2-authentication/` の `functional-design/`（`functional-spec.md`・`rules.md`・`entities.md`・`frontend-components.md`）、`nfr-requirements/`（6つの文書）、`nfr-design/`（6つの設計書。特に `security-design.md` 8章）、`infrastructure-design/`（`infrastructure-specification.md`・`cicd-pipeline.md`・`monitoring-design.md`）
- ほかの単位との約束: `construction/u1-app-skeleton/nfr-design/security-design.md` 3章、`construction/u1-app-skeleton/code-generation/code-summary.md`（申し送り）、`construction/u3-access-control/nfr-design/security-design.md` 3章、`construction/u4-audit-log/functional-design/functional-spec.md`
- Inception: `inception/units-generation/unit-of-work.md`・`unit-of-work-story-map.md`、`inception/domain-design/components.md`・`decisions.md`（ADR-001・ADR-004・ADR-006・ADR-007・ADR-008）、`inception/requirements-analysis/requirements.md`
- 既存のコード: `backend/src/main/java/cherry/mastersmith/common/`・`config/SecurityConfig.java`、`backend/src/test/java/cherry/mastersmith/common/testsupport/`、`frontend/src/app/registry/types.ts`・`layout/LoginLayout.tsx`・`login-state/LoginStateGate.tsx`・`routing/decideRoute.ts`、`frontend/src/features/README.md`、`frontend/playwright.config.ts`、`frontend/e2e/u1-skeleton.e2e.ts`、`README.md`、`.env.example`、`compose.yaml`
- チームの決まり: `aidlc/spaces/default/memory/org.md`・`team.md`・`project.md`・`phases/construction.md`
