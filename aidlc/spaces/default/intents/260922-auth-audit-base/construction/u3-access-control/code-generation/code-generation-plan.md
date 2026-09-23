# Code Generation Plan — U3 管理画面のアクセス制御（u3-access-control）

## 1. 概要

U3 は、管理者だけが使える API と画面を、サーバー側の判定で守る。`/api/admin` そのものと `/api/admin/` の下を管理者のみとし、それ以外の `/api/` の下を既定でログイン必須にする。未ログインは 401、管理者でない利用者は 403 とし、管理者は通す。403 と有効期限切れ以外の 401 では、アクセス拒否の出来事を知らせる（受け取りと記録は U4）。あわせて、正規化されていないパスの拒否を共通の形の 400 で返し、管理者向け領域の画面（見出しと説明のプレースホルダ）とサイドバーの「管理」を U1 の差し込み口に登録する。

- 対象の要件: FR2.2、FR8.1、FR8.2、および FR9.1 のうち「権限不足による管理画面へのアクセス拒否」の出来事の通知（記録は U4）。本スコープはユーザーストーリーを作っていないため、手順と要件の対応は FR・BR・NFR の ID で示す（5章）。
- 対象の決まり: U3 の BR1.1〜BR1.6、BR2.1〜BR2.6、BR3.1〜BR3.6、BR4.1、BR5.1〜BR5.4、BR6.1。NFR は U3 の各要件書の ID（性能 NFR1.1〜NFR1.3、拡張性 NFR1.4・NFR1.5、安全 NFR3.1〜NFR3.8、信頼性 NFR10.1・NFR9.1、品質 NFR7.1・NFR8.1・NFR9.2、観測性 NFR10.2〜NFR10.5）。
- U3 は U1 が作った骨格（`backend/`・`frontend/`、`./gradlew verify`、CI、コンテナ）と U2 が作った認証（アクセストークンの検証、`AuthenticatedUser`、出来事の仕組み、画面の ApiClient）の上に作る。アプリのコードはリポジトリのルート直下の `backend/` と `frontend/` に置き、`aidlc/` の下には置かない。
- U3 は内部DBに表を持たず、DB への問い合わせを増やさない。追加の依存関係・環境変数・秘密情報も無い（`infrastructure-design/infrastructure-specification.md` 2章・3章）。
- U1・U2 のファイルは書き換えない。例外は、7章の D1 で承認を求める8つのファイル（ルートの `build.gradle.kts` と、U1 の結合テスト・テストの補助の7ファイル）と、`README.md` の節の追加だけである。

## 2. 前提と守ること

- **版**: U1・U2 と同じ（Java 25、Spring Boot 4.1.1、Node.js 24、React 19、Vitest 4）。**依存関係は1つも足さない**（Spring Security のアクセス制御の設定・要求の検査、Spring のアプリ内の出来事、U1 の差し込み口だけで足りる。`nfr-requirements/tech-stack-decisions.md`）。画面側も依存関係を増やさない（make-you-chic-ui の既存の部品と U1 の `NotFoundPage` を使う）。足す必要が出たと分かったら、その場で生成を止めて案を示す。
- **ライセンスヘッダー**: 生成するすべてのソースファイルの先頭に Apache License 2.0 の標準ヘッダー（年 `2026`、著作権者 `agwlvssainokuni`）を入れる。Java・TypeScript・CSS は `/* ... */`（`/** ... */` は使わない）。
- **言語**: コメント（Javadoc・JSDoc を含む）、画面の文言（日英を文言の鍵で持つ）、README は日本語。テストの説明文（テスト名、`@DisplayName`、`describe` / `it`）は英語。
- **バックエンドの書き方**: パッケージは `cherry.mastersmith.access` とし、`domain`・`service`・`web` に分ける（7章の C1。DB を使わないため `repository` は作らない）。API の応答は `record` の DTO か内容なし。依存性の注入はコンストラクター注入だけ。Lombok は使わない。ロガーは `LoggerFactory.getLogger`、可変の値はキーと値で渡す。現在時刻は **U2 が置いた `cherry.mastersmith.auth.service.AuthClockConfig` の `Clock` の Bean** から得る（U2 の申し送り。U3 は `Clock` を別に定義しない）。
- **U1・U2 の差し込み口の使い方**: 決まりは `SecurityRuleContributor`（U3 は order 200 台。7章の C1）で足し、ヘッダー・セッション・CSRF の設定は変えない。`ApiDefaultAccess` は U3 が1つだけ置く。401・403・400 の応答は、U1 の `ErrorResponseWriter` を通して書く（U1 の申し送り: `ErrorPathController` は対応を決めていない状態コードを 500 / `INTERNAL_ERROR` にするため、フィルターの段階の応答を `sendError` に任せない）。
- **判定の根拠**: 管理者かどうかは、U2 が要求ごとに内部DBから読んだ `cherry.mastersmith.auth.domain.AuthenticatedUser` の `admin` だけで判断する（BR2.4、NFR3.5）。画面から送られた値・トークンの中身は使わない。U3 は DB を読まない（NFR1.2。Step 6 の結合テストで、確認用 API の1要求の SQL が U2 の利用者の読み取り1回だけであることを確かめる）。
- **秘密情報**: アクセストークン・Authorization ヘッダー・パスワードを、出来事・アプリのログ・エラー応答に入れない（NFR3.7）。細工されたパスもログに出さない（`nfr-design/security-design.md` 2章）。アプリのログにメールアドレスを出さない（NFR10.3。利用者の特定は監査ログで行う）。メソッドの呼び出しの追跡（U1 の TraceAspect）が引数・戻り値を文字列にするため、U3 の `web` の型に秘密の値を持たせない。
- **フロントエンドの書き方**: 名前付きのエクスポートだけ、`enum` を使わず文字列リテラルの union、CSS は部品と同じ場所に素の CSS、画面に HTML を直接埋め込まない、操作できる要素に `data-testid`（`{部品}-{役割}` の kebab-case）。
- **品質の目標は下げない**: カバレッジの下限（行 80%・分岐 70%）と NFR の値を、手順を通すために緩めない。カバレッジの除外を増やさない。既定でログイン必須（NFR3.2）と正規化されていないパスの拒否（NFR3.3）を、既存のテストを通すために本番の設定で緩めない（テストの中だけで緩める仕組みは 7章の C7）。
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

方法は test-after である。テスト可能な層ごとに「実装する → 同じ層のテストを書いて実行し、すべて通す → 次の層へ」の順に進める。テストの実行の枠組み（JUnit 5・jqwik・ArchUnit・Spring Boot Test・JaCoCo、Vitest・Testing Library・vitest-axe・fast-check・`@vitest/coverage-v8`、Playwright）は U1 が、認証のテストの補助（`MutableClock`・`AuthTestTokens`・`SqlStatementCounter` など）は U2 が既に用意している。Step 2 で、最初のテストの手順（Step 4）より前に、この単位に絞ったコマンドが動くことを確かめ、`unit-test-instructions.md` に記録する。

| Testing Contract の段 | 本計画の手順 |
|---|---|
| Project structure and production configuration skeleton | Step 1 |
| Bootstrap the minimal test runner/configuration | Step 2（U1・U2 が用意済み。U3 のコマンドの確認と、U3 のテストの補助を置く） |
| Data model / database behavior（実装 → テスト） | **該当なし**。U3 は内部DBに表を持たず、データを保存しない（`functional-design/entities.md`、`infrastructure-design/infrastructure-specification.md` 1章）。スキーマの変更も行わない。代わりに、DB への問い合わせを増やさないこと（NFR1.2）を Step 6 の結合テストで確かめる |
| Repository / data access（実装 → テスト） | **該当なし**（同上。U3 に `repository` の層を作らない） |
| Business logic（実装 → テスト） | Step 3 → Step 4（`access.domain` の純粋な判定・出来事・問題の種類、`access.service` のカタログと出来事の通知） |
| API / endpoint（実装 → テスト） | 2つに分ける。判定・401／403・確認用 API は Step 5 → Step 6、正規化されていないパスの拒否は Step 7 → Step 8。あわせて、既定の拒否で影響を受ける U1 の既存のテストの調整と全体の回帰を Step 9 |
| Frontend behavior（実装 → テスト） | Step 10 → Step 11 |
| Environment/build configuration | Step 12 |
| Documentation and traceability | Step 13 |

## 4. 実装の手順

パッケージ名・クラス名・ファイル名は計画上の名前であり、意味と置き場所（パッケージ・ディレクトリ）を変えない範囲で生成時に整えてよい。テストの置き場所は `unit-test-instructions.md` の絞り込みの範囲（バックエンドは `cherry.mastersmith.access` の下、フロントエンドは `frontend/src/features/admin/` の下、E2E は `frontend/e2e/u3-admin-access.e2e.ts`）から外さない。ただし Step 9 で調整する U1 の既存のテストは例外とし、7章の D1 で承認を得た範囲だけを変える。

### 4.1 プロジェクトの構成と本番の設定の骨組み

- [x] **Step 1 — パッケージの骨組み（依存関係・設定の追加なし）**
  - パッケージの骨組み: `cherry.mastersmith.access.{domain,service,web}`。各パッケージに日本語の `package-info.java`。
  - 依存関係は足さない（`gradle/libs.versions.toml`・`backend/build.gradle.kts`・`backend/gradle.lockfile` を変えない）。環境変数・秘密情報・設定の型も足さない（`infrastructure-specification.md` 3章）。`application.yaml` は変えない。
  - 時計は U2 の `AuthClockConfig` の `Clock` の Bean を注入して使う（出来事の `occurredAt`）。U3 で `Clock` を定義しない。
  - 対応: `nfr-design/logical-components.md` 1章、`infrastructure-design/infrastructure-specification.md` 2章・3章

### 4.2 テストの実行の枠組み

- [x] **Step 2 — この単位のテストのコマンドを確かめ、U3 のテストの補助を置く**
  - U1・U2 が用意した枠組みをそのまま使う（新しい道具は入れない）。`unit-test-instructions.md` の 2.2 のコマンドで、U3 のパッケージに絞った実行が動くことを確かめる（この時点では U3 のテストは無い。常に通るだけのテストは書かない）。
  - テストの補助（テストのソースの中だけ。新しいファイル）:
    - `cherry.mastersmith.access.testsupport.PublicApiTestRules`: `mastersmith.test-fixture.public-api=true` を設定したテストだけで有効になる `SecurityRuleContributor`（order 250）。`/api/**` を許可し、U3 の既定の拒否が効く前の状態を再現する。U1 の既存の結合テスト（エラー応答・トレース・ヘッダーの確認）が、アクセス制御ではなく本来の対象を確かめ続けられるようにするためのもので、本番の設定では既定の拒否を切り替えられない（7章の C7）。
    - `cherry.mastersmith.access.testsupport.AdminAccessTestConfig`・`AdminTestUsers`: 結合テストで、管理者・管理者でない利用者を `user.service.UserAccountService.createUser` で作り、U2 の `AuthApi`（テストの補助）でログインしてアクセストークンを得る（7章の C9）。時計は U2 の `MutableClock` を `@Primary` で差し替える。
    - `cherry.mastersmith.access.testsupport.CapturedAccessDeniedEvents`: アクセス拒否の出来事を集めるテスト用の受け取り（U3 の出来事は同じスレッドで受け取るため、`@EventListener`）。受け取りで例外を起こす役も置く。
  - 対応: team.md Testing Posture、NFR9.2（品質）

### 4.3 データモデル・DB の振る舞い／Repository・データアクセス

U3 は内部DBに表を持たず、データを保存しない。スキーマの変更（Flyway のファイル）も `repository` の層も作らない。この2つの段に当たる手順は無い。DB に関わる確かめは、「U3 が DB への問い合わせを増やさないこと」（NFR1.2）として Step 6 の結合テストで行う。

### 4.4 業務処理（判定の決まり、出来事、問題の種類）

- [x] **Step 3 — `access.domain` と `access.service`**
  - `access.domain.AdminPaths`（純粋な関数。性質ベースのテストの対象）: 要求のパスが管理者のみの対象か（`/api/admin` そのもの、または `/api/admin/` の下）を返す。大文字・小文字を区別し、末尾のスラッシュの有無で判定を変えない（BR1.6）。フィルターの連鎖の決まり（Step 5）と、401・403 の処理の出来事の要否の判断（BR3.6）の**両方が同じ関数を使う**ことで、判定と記録のずれを防ぐ。
  - `access.domain.AccessDeniedReason`（`NOT_ADMIN`・`TOKEN_MISSING`・`TOKEN_MALFORMED`・`TOKEN_INVALID`・`USER_NOT_FOUND`）と、U2 の `TokenFailureReason` からの変換（`TOKEN_EXPIRED` は出来事にしないことを表すため、変換の結果を `Optional` で返す。BR3.2）。例外が `TokenAuthenticationException` でなければ `TOKEN_MISSING` とみなす（U2 の申し送り）。
  - `access.domain.AdminAccessDeniedEvent`（`eventType`（`ACCESS_DENIED`）・`occurredAt`・`result`（`FAILURE`）・`failureReason`・`enteredEmail`（分かるときだけ）・`sourceIp`・`userAgent`・`requestPath`（判定を通った正規化済みのパス。問い合わせの部分は除き、512 文字で切り詰める）・`traceId`）。秘密情報の項目を持たない（BR3.4、NFR3.7）。要求のパスは載せる（7章の D3 の3つ目）。U2 の `ClientInfo` から `sourceIp`・`userAgent`・`traceId` を受け取る形にする（7章の C5）。
  - `access.domain.AccessProblemTypes`（`ACCESS_DENIED` 403、`REQUEST_REJECTED` 400。日英の title・description・resolution。応答の説明に内部の情報・パスを載せない）と、それを U1 の起動時の収集に載せる `access.service.AccessProblemTypeCatalog`（`ProblemTypeCatalog` の Bean）。`REQUEST_REJECTED` は承認済みの `rules.md` BR6.1 に無いもので、7章の D3 で揃える。
  - `access.service.AccessDeniedEventPublisher`: 出来事を `ApplicationEventPublisher` で知らせる。受け取り側（U4）を知らない（ADR-004、BR3.5）。通知で例外が戻った場合は捕まえて WARN を1回出し（例外の型だけ。メッセージは出さない）、呼び出し元に例外を伝えない（`reliability-design.md` 1章の「受け止めの二重の備え」）。例外を黙って捨てない。
  - 対応: BR1.6、BR3.1〜BR3.6、BR6.1、NFR3.3、NFR3.6、NFR3.7、NFR10.1（信頼性）、NFR10.2・NFR10.3（観測性）

- [x] **Step 4 — `access.domain`・`access.service` のテスト**
  - 単体テスト（`*Test`）:
    - `AdminPathsTest`（jqwik: `/api/admin/` の後ろに何が続いても真、`/api/admin` 以外で始まるパスは偽。明示の例: `/api/admin`・`/api/admin/`・`/api/admin/check`・`/api/admin/nothing` は真、`/API/admin/check`・`/api/administrators`・`/api/auth/login`・`/actuator/health` は偽）。
    - `AccessDeniedReasonTest`（`TOKEN_EXPIRED` は空を返す、ほかの4つの区分はそれぞれに対応する、例外が `TokenAuthenticationException` でなければ `TOKEN_MISSING`、U2 の `TokenFailureReason` の値をすべて網羅する）。
    - `AdminAccessDeniedEventTest`（項目がそろう、`enteredEmail` が無い場合、User-Agent が U2 の `ClientInfo` で 512 文字に切られている、秘密情報の項目を持たない、`toString` に秘密が出ない）。
    - `AccessProblemTypesTest`・`AccessProblemTypeCatalogTest`（2つの code と状態コード、日英がそろう、カタログが2つを返す、U1・U2 の code と重ならない）。
    - `AccessDeniedEventPublisherTest`（知らせる、受け取り側の例外を WARN で受け止めて呼び出し元に伝えない、WARN に例外のメッセージを出さない、通知が1回だけ）。
  - `unit-test-instructions.md` の Step 4 のコマンドで実行し、すべて通す。

### 4.5 API・エンドポイント（1）判定・401／403・確認用 API

- [x] **Step 5 — アクセスの決まり、401・403 の処理、確認用 API**
  - `access.web.AdminAuthorizationManager`: Spring Security の `AuthorizationManager` として、認証の結果の主体が `AuthenticatedUser` で `admin` が真のときだけ許可する（U2 の `AuthenticatedUserToken` は権限の一覧を持たないため、役割の文字列ではなく主体の値で判定する）。後続 Intent F で役割・権限の判定を足す場所がここになる（ADR-003）。
  - `access.web.AdminSecurityContributor`（`SecurityRuleContributor`、order 210。7章の C1）:
    - `/api/admin` と `/api/admin/**` に `AdminAuthorizationManager` を当てる（BR1.1、BR1.6）。
    - 401 の入口の処理と 403 の拒否の処理を、Bearer の検証の失敗と未認証の両方に設定する（U2 が order 110 で設定した入口の処理を、後から当たる U3 の設定で置き換える。U2 の申し送りの D2）。
  - `access.web.AdminApiDefaultAccess`（`ApiDefaultAccess` の Bean、U3 が1つだけ置く）: `/api/**` の既定をログイン必須にする（BR1.3、NFR3.2）。これにより公開の API は U1（`/actuator/health`・`/api/problems/**`）と U2（`/api/auth/login`・`/api/auth/session/**`）の一覧だけになる。
  - `access.web.AdminAuthenticationEntryPoint`: 401 のとき、要求のパスが管理者のみなら（`AdminPaths`）、理由を求め（`AccessDeniedReason` の変換）、`TOKEN_EXPIRED` でなければ出来事を知らせる（BR3.2、BR3.3）。応答の書き出しと区分の DEBUG のログは、U2 の `TokenAuthenticationEntryPoint` に任せる（注入して呼ぶ。7章の D2）。
  - `access.web.AdminAccessDeniedHandler`: 403 のとき、U1 の `ErrorResponseWriter` で `ACCESS_DENIED` を書き、理由 `NOT_ADMIN` の出来事（`enteredEmail` にその利用者のメールアドレス）を知らせる（BR2.2、BR3.1）。WARN で `code` だけを出す（メールアドレス・パス・トークンを出さない。NFR10.3）。
  - `access.web.AdminCheckController`: `GET /api/admin/check` → 204（内容なし）。判定は決まりに任せ、本体は成功を返すだけ（BR4.1、7章の C2）。
  - 送り手の情報は U2 の `cherry.mastersmith.auth.web.ClientInfoResolver` で作る（7章の C5）。時刻は U2 の `Clock` の Bean。
  - 対応: FR8.1、FR8.2、BR1.1〜BR1.5、BR2.1〜BR2.6、BR3.1〜BR3.6、BR4.1、NFR1.2（性能）、NFR1.4（拡張性）、NFR3.1・NFR3.2・NFR3.4〜NFR3.8、NFR10.1（信頼性）、NFR10.2〜NFR10.4（観測性）

- [x] **Step 6 — 判定・401／403・確認用 API のテスト**
  - 単体テスト（`*Test`）: `AdminAuthorizationManagerTest`（管理者は許可、管理者でない利用者は拒否、未認証・主体が `AuthenticatedUser` でないときは拒否）、`AdminAuthenticationEntryPointTest`（管理者のみのパスで期限切れ以外は出来事、期限切れは出来事なし、管理者のみ以外のパスは出来事なし、例外が `TokenAuthenticationException` でなければ `TOKEN_MISSING`、応答の書き出しを U2 の入口の処理に任せる、出来事の通知の例外で応答が変わらない）、`AdminAccessDeniedHandlerTest`（403 を U1 の書き手で書く、`NOT_ADMIN` の出来事とメールアドレス、WARN が `code` だけ、通知の例外を受け止める）。
  - 結合テスト（`*IT`。実際の番号で待ち受けるアプリに U1 の `HttpTestClient` で送る）:
    - `AdminAccessIT`: 未ログインで `/api/admin/check` が 401 / `AUTHENTICATION_REQUIRED`、管理者でない利用者が 403 / `ACCESS_DENIED`、管理者が 204（team.md の必須の認可のテスト）。画面を介さずに直接呼んでも同じ（BR2.6）。管理者でない利用者の `/api/admin/nothing` は 403、管理者は 404 / `NOT_FOUND`（BR2.5、NFR3.4）。管理者フラグを DB で外した直後の要求が 403（BR2.4、NFR3.5）。確認用 API の1要求で発行される SQL が U2 の利用者の読み取り1回だけ（`SqlStatementCounter`。NFR1.2）。応答の形が U1 の共通の形（`type`・`code`・`traceId`）で、内部の情報を含まない（NFR3.6）。
    - `ApiDefaultAccessIT`: 公開の一覧（`/actuator/health`・`/api/problems/**`・`/api/auth/login`・`/api/auth/session/**`）はログインなしで通る、一覧に無い `/api/**` はログインなしで 401（NFR3.2）、画面の配信（`/`）は 200、`/actuator/health` 以外の Actuator に外部から届かない（BR1.5、NFR3.8）。
    - `AccessDeniedEventsIT`: 403 で `NOT_ADMIN` の出来事（項目がそろう）、トークン無し・形の崩れ・改ざん・利用者が DB にいない、の 401 でそれぞれの理由の出来事、期限切れの 401 では出来事なし（`MutableClock` で時刻を進める）、管理者のみ以外のパスの 401 では出来事なし（BR3.3）、出来事の受け取り側で例外を起こしても 401／403 の応答（状態コード・`code`・本文）が変わらない（NFR10.1（信頼性））。
    - `AccessSecretLeakIT`: `cherry.mastersmith.access` を TRACE にして 401・403 を起こし、U1 の `JsonLogRecords` で、アクセストークンの値・Authorization ヘッダー・メールアドレスがアプリのログに出ないこと、出来事とエラー応答にも秘密情報が無いこと、すべての行が JSON でトレースIDが付くことを確かめる（NFR3.7、NFR10.3、NFR10.4）。
    - `AccessProblemTypesIT`: U3 の2つの問題の種類が U1 の説明ページ（`/api/problems/...`）に日英で出る（BR6.1）。
  - `unit-test-instructions.md` の Step 6 のコマンドで実行し、すべて通す。

### 4.6 API・エンドポイント（2）正規化されていないパスの拒否

- [x] **Step 7 — 要求の検査の拒否の処理**
  - Spring Security の既定の要求の検査（エンコードされた区切り、`;`、`..`、`//` などを含むパスの拒否）は外さない（BR1.6、NFR3.3）。
  - `access.web.AccessRequestRejectedHandler`: 拒否を、U1 の `ErrorResponseWriter` で 400 / `REQUEST_REJECTED` の共通の形にする。拒否は U1 のヘッダーを書く処理より手前で起きるため、U1 の `SecurityHeaderProperties` の値を読んで同じヘッダー（CSP・`X-Content-Type-Options`・`X-Frame-Options`・`Referrer-Policy`）を付ける（値は U3 で書き直さない）。`Cache-Control: no-store` は U1 の `CacheControlFilter` が連鎖より前で付けるため、U3 では付けない。WARN は `code` とトレースIDだけを出し、**拒否したパスを出さない**（`nfr-design/security-design.md` 2章）。出来事は作らない（`observability-design.md` 1章）。
  - 仕組みの確かめ方は 7章の C4 のとおり（`WebSecurityCustomizer` で `RequestRejectedHandler` を差し込む形を Step 8 のテストで確かめ、効かない場合は代わりの形にする。どちらも満たせないときは生成を止めて案を示す）。
  - 対応: BR1.6、BR6.1、NFR3.3、NFR3.6、NFR10.3・NFR10.4（観測性）

- [x] **Step 8 — 拒否の処理のテスト**
  - 単体テスト: `AccessRequestRejectedHandlerTest`（400 / `REQUEST_REJECTED` を U1 の書き手で書く、4つのヘッダーが U1 の設定の値で付く、WARN に `code` とトレースIDだけが出てパスが出ない、出来事を作らない）。
  - 結合テスト: `AdminPathBoundaryIT`（`/api/admin`・`/api/admin/` は管理者でない利用者に 403、`/api/admin/..;/` とエンコードされた区切りを含むパスは判定の前に 400 / `REQUEST_REJECTED` で、応答が共通の形（`type`・`code`・`traceId`）とヘッダーを持ち、同じトレースIDが WARN のログにも出る（`security-design.md` 2章の根拠の確認を兼ねる）、ログに拒否したパスが出ない、`/API/admin/check` はログイン中 404・未ログイン 401）。
  - `unit-test-instructions.md` の Step 8 のコマンドで実行し、すべて通す。

### 4.7 既存の単位のテストの調整と回帰

- [x] **Step 9 — U1 の既存のテストの調整（D1）と全体の回帰**
  - 7章の D1 で承認を得た範囲だけを変える。`/api/**` の既定がログイン必須になったこと（NFR3.2）と、`ApiDefaultAccess` が1つまでであることによる調整である。品質の目標も本番の既定も緩めない。
    - `config/SecurityExtensionIT`: 「U1 だけなら `/api/**` はすべて通る」の確認を、U3 の既定が入った状態（ログインなしは 401）に改める。役の `ApiDefaultAccess` を使う入れ子の確認は、U3 の本物の `ApiDefaultAccess` と2つになって起動が失敗するため取り除き、同じ確認は Step 6 の `ApiDefaultAccessIT` が受け持つ。order 100・200 の役の決まりの確認と、order の重複・`ApiDefaultAccess` が2つ以上のときに起動が失敗する確認はそのまま残す（U3 は order 210 のため、役の 200 と重ならない）。
    - `common/testsupport/TestSecurityExtensions`: 使われなくなる役の `ApiDefaultAccess` の設定を取り除く。
    - `common/error/web/ErrorResponseIT`、`common/observability/TracingAndLoggingIT`、`common/observability/ExternalExportIT`、`config/ExposureIT`、`config/SecurityHeadersIT`: クラスの設定に `mastersmith.test-fixture.public-api=true` を1行足す（Step 2 の補助を有効にする）。これらはエラー応答の形・トレース・ヘッダーを確かめるテストであり、アクセス制御を確かめるものではない。
  - U2 のテストは変えない（7章の D2 の A を採るため）。変える必要が出たと分かったら、生成を止めて依頼者の判断を仰ぐ。
  - 全体の回帰: `./gradlew :backend:test :backend:integrationTest`（U1・U2 を含むすべて）が通ることを確かめる。ここで初めて分かる影響（想定外の既存テストの失敗）があれば、原因と最小限の直し方を依頼者に示してから直す。
  - 対応: NFR3.2、既存の検査を壊さないこと（org.md・team.md の統合前の関門）

### 4.8 画面（フロントエンド）

- [x] **Step 10 — 管理者向け領域、登録、文言**
  - `frontend/src/features/admin/`（featureId `admin`。7章の C6）:
    - `adminApi.ts`: U2 の `frontend/src/shared/api-client/` の `apiRequest` で `GET /api/admin/check` を呼ぶ（トークンの付与・401 の更新と送り直しは ApiClient に任せる）。
    - `adminAreaStatus.ts`: 呼び出しの結果から表示の状態（`Checking`・`Shown`・`NotFound`・`Error`）を決める純粋な関数（403 は `NotFound`、そのほかの応答のエラーと通信の失敗は `Error`）。性質ベースのテストの対象。
    - `AdminAreaPage.tsx`: 表示のたびに確認用 API を呼び、確認が終わるまで中身を表示しない（`aria-busy` と読み上げ用の文言で、確認中であることを支援技術に伝える）。成功で `AdminPlaceholder`、403 で U1 の `NotFoundPage`、それ以外で一般的なエラーの文言（アプリシェルとほかの画面は動き続ける）。結果をメモリに残さない（BR5.2、NFR9.1（信頼性））。`data-testid`: `admin-area-page`、`admin-area-checking`、`admin-area-error` など。
    - `AdminPlaceholder.tsx`・`AdminPlaceholder.css`: 見出し「管理」と、今後の管理機能がここに加わる旨の説明（BR5.3）。`data-testid`: `admin-placeholder`。
    - `registration.ts`: `featureId: 'admin'`、画面 `/admin`（`access: 'ADMIN'`、`layout: 'SHELL'`、画面の部品は遅延読み込み）、サイドバーの項目「管理」（`id: 'admin-area'`、`labelKey: 'admin.nav.label'`、`visibleWhen: 'ADMIN'`、`order` はホームの後）、文言（鍵は `admin.` で始め、日英をそろえる: サイドバーの「管理」、見出し、説明、確認中、エラー）。U1 のファイルは変えない（BR5.1、NFR7.1）。
  - 画面の「管理」の表示・非表示は U1 の骨組みが U2 のログイン状態の `admin` で切り替える。これは表示の切り替えにすぎず、判定はサーバー側で行う（BR5.4、FR8.2）。
  - 対応: FR2.2、BR5.1〜BR5.4、NFR7.1、NFR8.1、NFR9.1（信頼性）

- [x] **Step 11 — 画面のテスト**
  - `adminApi.test.ts`（`fetch` を `vi.fn` で置き換える）: パスとメソッド、204 の成功、403・5xx・通信の失敗の変換。
  - `adminAreaStatus.test.ts`（fast-check: 403 は必ず `NotFound`、それ以外の状態コードは `Error`、通信の失敗は `Error`）。
  - `AdminAreaPage.test.tsx`（user-event）: 確認中は中身を出さず確認中であることを伝える、204 で見出しと説明、403 で「ページが見つかりません」、5xx と通信の失敗で一般的な文言、表示のたびに呼び直す、英語の表示で英語の文言、vitest-axe で違反なし（確認中の状態も検査する）。
  - `AdminPlaceholder.test.tsx`: 見出しと説明、vitest-axe。
  - `registration.test.ts`: U1 の `validateRegistrations` を通る（`access=ADMIN`・`SHELL`、URL が重複しない）、サイドバーの項目が `visibleWhen=ADMIN` でホームの後に並ぶ、管理者でないログイン状態では項目が出ない、文言の鍵が日英でそろう。
  - `unit-test-instructions.md` の Step 11 のコマンドで実行し、すべて通す。

### 4.9 環境・ビルドの設定

- [x] **Step 12 — E2E（代表の流れの完成）と全体の検査**
  - `frontend/e2e/u3-admin-access.e2e.ts`: 初期管理者でログイン → サイドバーに「管理」が出る → 選ぶと管理者向け領域（見出しと説明）が表示される → ログアウト → ログイン画面に戻る。これでチームの代表の流れ「ログイン → 管理画面に入れるか → ログアウト」が完成する（team.md Testing Posture。U2 の C9 の申し送り）。CSP の違反とスクリプトのエラーが無いこと（U2 と同じく認証の API の 401 の表示だけを除く）。
  - ルートの `build.gradle.kts` の `e2eTest` の実行の対象を `e2e/` のすべてにする（7章の D1）。これにより U1・U2・U3 の E2E が1つのコマンドで動く。
  - `README.md` に U3 の節を足す（U1・U2 の節の構成は変えない）: `/api/` の下は既定でログインが必要で、公開する API は明示した一覧だけであること、管理者のみの範囲（`/api/admin`・`/api/admin/**`）、正規化されていないパスが 400 / `REQUEST_REJECTED` になること、後の単位が使う差し込み口の表に U3 が提供するもの（アクセス拒否の出来事）を足す。環境変数は増えない。
  - `./gradlew verify`（0〜9 の段すべて。カバレッジの下限、SpotBugs の High 0 件、OSV-Scanner、Gitleaks を含む）と `./gradlew e2eTest` と `pre-commit run --all-files` がすべて通ることを確かめる。通らなければ、下限を下げずにテストを足すか、差を依頼者に示す。
  - `docker compose up -d --build` で起動し、ヘルスチェックと、初期管理者でのログイン → 管理者向け領域の表示 → ログアウト（スモークテスト）を確かめる。
  - 対応: NFR8.1、NFR9.2（品質）、`infrastructure-design/cicd-pipeline.md`、team.md Testing Posture（E2E）・Deployment

### 4.10 文書とトレーサビリティ

- [x] **Step 13 — まとめの記録**
  - `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u3-access-control/code-generation/` に `code-summary.md`（作った・変えたファイル、判断、テストとカバレッジ、`./gradlew verify` の結果、計画からの逸脱、U4 への申し送り。`nfr-design/security-design.md` 5章の違いと、7章の D3 で決めた3点目を Functional Design との違いとして記録する）、`traceability.json`（FR・BR・NFR から実装とテストへの対応）、`source-manifest.json`（作った・変えた・消したアプリのソースのパスの一覧）を書く。
  - 対応: 全要件のトレーサビリティ

## 5. 要件と手順の対応（トレーサビリティ）

| 要件・決まり | 実装の手順 | テストの手順 |
|---|---|---|
| FR2.2 管理者向け領域とサイドバーの「管理」（BR5.1、BR5.3、NFR7.1、NFR8.1） | Step 10 | Step 11、Step 12 |
| FR8.1 管理者のみの API の判定（BR1.1、BR2.1〜BR2.3、NFR3.1） | Step 3、Step 5 | Step 4、Step 6 |
| FR8.2 画面で隠すことを判定の代わりにしない（BR2.6、BR5.4） | Step 5、Step 10 | Step 6、Step 11 |
| FR9.1 アクセス拒否の出来事の通知（BR3.1〜BR3.4、NFR10.2（観測性）） | Step 3、Step 5 | Step 4、Step 6 |
| BR1.2〜BR1.4 公開の一覧と既定でログイン必須（NFR3.2） | Step 5 | Step 6 |
| BR1.5 `/api/` の外は画面の配信と health だけ（NFR3.8） | Step 5 | Step 6 |
| BR1.6 パスの照合と正規化されていないパスの拒否（NFR3.3） | Step 3、Step 5、Step 7 | Step 4、Step 6、Step 8 |
| BR2.4 要求ごとの DB の値で判断（NFR3.5、NFR1.2（性能）） | Step 5 | Step 6 |
| BR2.5 存在しない管理 API も 403（NFR3.4） | Step 5 | Step 6 |
| BR3.2 期限切れの 401 は知らせない | Step 3、Step 5 | Step 4、Step 6 |
| BR3.3 管理者のみ以外の 401 は知らせない | Step 5 | Step 6 |
| BR3.5 受け取り側の失敗で応答を変えない（NFR10.1（信頼性）） | Step 3 | Step 4、Step 6 |
| BR3.6 401・403 の処理と共通の形の応答（NFR3.6） | Step 5 | Step 6 |
| BR4.1 確認用 API `GET /api/admin/check` | Step 5 | Step 6、Step 12 |
| BR5.2 表示のたびの確認と 403 の見つからない画面（NFR9.1（信頼性）） | Step 10 | Step 11 |
| BR6.1 問題の種類（`ACCESS_DENIED`・`REQUEST_REJECTED`） | Step 3、Step 7 | Step 4、Step 6、Step 8 |
| NFR1.4・NFR1.5（拡張性）状態を持たない判定と出来事の量 | Step 5 | Step 6（設計の確認は `code-summary.md`） |
| NFR3.7 出来事に秘密情報を載せない・接続元IPの取り方 | Step 3、Step 5 | Step 4、Step 6 |
| NFR9.2（品質）必須の認可のテストとパスの境界 | — | Step 6、Step 8、`unit-test-instructions.md` 7章 |
| NFR10.3・NFR10.4（観測性）WARN の項目とトレースID | Step 3、Step 5、Step 7 | Step 6、Step 8 |
| NFR10.5（観測性）拒否の件数は監査ログで見る | — | 配備先が決まったときに確かめる（`code-summary.md` で Deferred） |
| NFR1.1・NFR1.3（性能）応答時間 | Step 5 | Performance Validation で測る（`code-summary.md` と `traceability.json` で Deferred） |

## 6. 単位どうしのつなぎ目

本ワークフローは Contract Design を行わないため、U3 が使う約束と U3 が提供する約束をここに記録する。

### 6.1 U3 が使う U1・U2 の約束（U1・U2 のファイルは書き換えない。例外は 7章の D1）

| つなぎ目 | 提供 | U3 の使い方 |
|---|---|---|
| `SecurityRuleContributor` | U1 | `AdminSecurityContributor`（order 210）で、管理者のみの決まりと 401・403 の処理を足す。ヘッダー・セッション・CSRF は変えない |
| `ApiDefaultAccess` | U1 | U3 が1つだけ置き、`/api/**` の既定をログイン必須にする |
| `ErrorResponseWriter` | U1 | 403 / `ACCESS_DENIED` と 400 / `REQUEST_REJECTED` を書く（401 は U2 の入口の処理に任せる。7章の D2） |
| `ProblemTypeCatalog`・`ProblemType` | U1 | `ACCESS_DENIED`・`REQUEST_REJECTED` を `AccessProblemTypeCatalog` に定義する |
| `SecurityHeaderProperties` | U1 | 拒否の 400 に同じヘッダーの値を付ける（値は書き直さない） |
| `TraceIdProvider`・`CacheControlFilter` | U1 | トレースID（U2 の `ClientInfoResolver` 経由）、拒否の応答の `Cache-Control` |
| `NotFoundPage`・画面の差し込み口（`FeatureRegistration`） | U1 | 403 のときの表示、`frontend/src/features/admin/registration.ts` |
| `common.testsupport`（`JsonLogRecords`・`LogEvents`・`HttpTestClient`・`TestDatabase`） | U1 | ログと API の結合テスト |
| `AuthenticatedUser` | U2 | 管理者かどうかの判定（要求ごとに DB から読んだ値） |
| `TokenAuthenticationException`・`TokenFailureReason` | U2 | 401 の理由。例外がこれでなければ `TOKEN_MISSING` とみなす |
| `TokenAuthenticationEntryPoint` | U2 | 401 の応答の書き出しと区分の DEBUG（7章の D2 の A） |
| `ClientInfoResolver`・`ClientInfo` | U2 | 出来事の接続元IP・User-Agent・トレースID（7章の C5） |
| `Clock` の Bean（`AuthClockConfig`） | U2 | 出来事の `occurredAt`。U3 は別に定義しない |
| `apiRequest`・`apiFetch`（`src/shared/api-client/`） | U2 | 確認用 API の呼び出し（トークンの付与、401 の更新と送り直し） |
| ログイン状態の `admin` | U2 | サイドバーの「管理」の表示の切り替えだけに使う |
| テストの補助（`MutableClock`・`AuthTestTokens`・`SqlStatementCounter`・`AuthApi`） | U2 | 期限切れのトークン、時刻の操作、SQL の回数、ログイン |

### 6.2 U3 が提供する約束

| つなぎ目 | 形 | 使う単位 |
|---|---|---|
| アクセス拒否の出来事 | `cherry.mastersmith.access.domain.AdminAccessDeniedEvent`（`eventType`＝`ACCESS_DENIED`・`occurredAt`・`result`＝`FAILURE`・`failureReason`（`NOT_ADMIN`・`TOKEN_MISSING`・`TOKEN_MALFORMED`・`TOKEN_INVALID`・`USER_NOT_FOUND`）・`enteredEmail`（分かるときだけ）・`sourceIp`・`userAgent`・`traceId`）を `ApplicationEventPublisher` で知らせる。**受け取りは要求と同じスレッドで、応答を書く前に行う**（U2 の認証の出来事が `@TransactionalEventListener(AFTER_COMMIT)` であるのと異なり、U3 は内部DBの更新を伴わないため `@EventListener` で受け取る。`performance-design.md` 2章、U4 の決まり 1.3）。受け取り側の失敗で応答は変わらない | U4 |
| 問題の種類 | `ACCESS_DENIED`（403）・`REQUEST_REJECTED`（400）。U1 の起動時の収集に載る | U1 の説明ページ、画面 |
| アクセスの決まり | `/api/admin`・`/api/admin/**` は管理者のみ、それ以外の `/api/**` は既定でログイン必須。後続の単位が API を足すときは、管理者のみにするなら `/api/admin/` の下に置く（個々の API での宣言に頼らない） | 後続 Intent |
| 役割・権限の判定の置き場所 | `AdminAuthorizationManager`（ADR-003。Intent F の判定はここに足す） | 後続 Intent |

## 7. 判断を仰いだ点（決定済み）

依頼者の回答（`code-generation-questions.md` の Q1〜Q4）により、次のとおり決まった。

| 点 | 決定 |
|---|---|
| D1 U1 の既存のファイルの変更（8件） | A: 8ファイルを変え、緩める仕組みはテストのソースの中だけに置く |
| D2 401 の入口の処理の置き換え方 | A: U3 は判定と出来事の通知だけを受け持ち、応答の書き出しは U2 に任せる |
| D3 Functional Design との違い（3点） | A: 3点とも NFR 設計に合わせる（要求のパスは載せる）。設計の文書は書き換えず、違いを記録する |
| C1〜C9 | A: すべて下の内容のとおり進める |

以下は、決定の前に示した案の内容である。

以下は依頼者の判断を仰ぐ点である。D は決定が必要なもの、C はこのまま進めてよいかの確認である。

### 決定が必要

#### D1 — U1 の既存のファイルの変更（8ファイル）

U3 が `/api/**` の既定をログイン必須にし（BR1.3、NFR3.2）、`ApiDefaultAccess` を1つ置くため、次の8ファイルは変えないと成り立たない。いずれもテストと E2E の実行の設定であり、本番の設定・品質の目標は変えない。

| ファイル | 変更 | 理由 |
|---|---|---|
| `build.gradle.kts`（ルート） | `e2eTest` の実行の対象を `e2e/u1-skeleton.e2e.ts` から `e2e/`（すべて）にする | 代表の流れ（ログイン → 管理画面 → ログアウト）を1コマンドで動かすため。U2 の E2E も同じコマンドで動くようになる |
| `backend/src/test/java/cherry/mastersmith/config/SecurityExtensionIT.java` | 「U1 だけなら `/api/**` はすべて通る」の期待を、U3 の既定が入った状態に改める。役の `ApiDefaultAccess` を使う入れ子の確認を取り除く | U3 の本物の `ApiDefaultAccess` と2つになると起動が失敗する。同じ確認は U3 の `ApiDefaultAccessIT` が受け持つ |
| `backend/src/test/java/cherry/mastersmith/common/testsupport/TestSecurityExtensions.java` | 役の `ApiDefaultAccess` の設定を取り除く | 上と同じ理由で使えなくなるため |
| `backend/src/test/java/cherry/mastersmith/common/error/web/ErrorResponseIT.java` | クラスの設定に `mastersmith.test-fixture.public-api=true` を1行足す | `/api/test-fixture/**`・`/api/no-such-api` が既定で 401 になるため。このテストが確かめるのはエラー応答の形であり、アクセス制御ではない |
| `backend/src/test/java/cherry/mastersmith/common/observability/TracingAndLoggingIT.java` | 同上 | 同上（トレースIDとログの形） |
| `backend/src/test/java/cherry/mastersmith/common/observability/ExternalExportIT.java` | 同上 | 同上（外部エクスポートの内容） |
| `backend/src/test/java/cherry/mastersmith/config/ExposureIT.java` | 同上 | 同上（公開する範囲と本文の大きさ） |
| `backend/src/test/java/cherry/mastersmith/config/SecurityHeadersIT.java` | 同上 | 同上（応答のヘッダー） |

U2 のテストは変えない（D2 の A を採る場合）。

- **A（推奨）**: 上の8ファイルを変える（Step 9・Step 12）。テストの中だけで既定の拒否を緩める仕組み（C7）を U3 のテストのソースに置き、本番の設定では切り替えられないようにする。変更は `code-summary.md` に記録する。
- **B**: `ApiDefaultAccess` の既定を設定で切り替えられるようにし、既存のテストは設定を緩めて通す。→ 本番で既定の拒否を無効にできる設定が増えるため勧めない（NFR3.2）。
- **X. Other (please specify)**

#### D2 — 401 の入口の処理の置き換え方

U2 は `TokenAuthenticationEntryPoint`（401 / `AUTHENTICATION_REQUIRED` を U1 の書き手で書き、区分を DEBUG で出す）を order 110 で「Bearer の検証の失敗」と「未認証」の両方に置いている。U3 はこれを order 210 で置き換える（U2 の申し送り）。置き換え方に2つの案がある。

- **A（推奨）**: U3 の `AdminAuthenticationEntryPoint` が、管理者のみのパスかどうかの判断・理由の判定・出来事の通知だけを行い、**応答の書き出しと区分の DEBUG のログは U2 の `TokenAuthenticationEntryPoint` を注入して任せる**。401 の応答の形が1か所に保たれ、U2 のソースもテスト（`AccessTokenApiIT` は U2 の入口の処理のログを見ている）も変えずに済む。
- **B**: U3 が U1 の `ErrorResponseWriter` で自分で 401 を書き、DEBUG も自分で出す。U2 の `AccessTokenApiIT` を U3 のクラスのログを見るように直す必要があり、U2 の入口の処理は使われなくなる（同じ処理が2つ残り、カバレッジも下がる）。
- **X. Other (please specify)**

#### D3 — 承認済みの Functional Design との違い（3点）の揃え方

`nfr-design/security-design.md` 5章は2点を挙げ、Code Generation の計画で揃えることを求めている。計画を書く中で3点目（出来事の項目）が分かったため、あわせて示す。

| 違い | Functional Design の記述 | NFR Design の記述 | 本計画 |
|---|---|---|---|
| 1. 問題の種類 | `rules.md` BR6.1: U3 の問題の種類は `ACCESS_DENIED` だけ | 確定回答 Q1 により `REQUEST_REJECTED`（400）を加えて2つ | **NFR Design に合わせる**（Step 3、Step 7） |
| 2. 拒否の応答 | `functional-spec.md` WF1 の手順1は「判定の前に拒否する」だけで応答の形を決めていない | 共通の形の 400 と U1 のヘッダー | **NFR Design に合わせる**（Step 7） |
| 3. 出来事の要求のパス | `functional-design/entities.md` の `AdminAccessDeniedEvent` に要求のパスの項目は無い | `security-design.md` 4章は記録項目に「要求のパス」を含めるとしている。U4 の `nfr-design/security-design.md` 6章も、依頼者の変更の依頼により、アクセス拒否のときだけ `requestPath`（正規化済み・問い合わせの部分なし・512 文字で切り詰め）を加えている | **NFR Design に合わせる**（要求のパスを載せる）。NFR Design の承認の場で依頼者が決めた内容であり、U4 が記録できる項目にも入っているため |

- **A（推奨）**: 上のとおり（1・2 は NFR Design、3 は Functional Design に合わせる）作る。設計の文書は書き換えず、違いを `code-summary.md` と `traceability.json` に記録する。
- **B**: 先に Functional Design をやり直し（`/aidlc --stage functional-design`）、3点を反映してから Code Generation に戻る。
- **X. Other (please specify)**

### 確認（このまま進めてよいかの確認）

- **C1 — パッケージと order**: パッケージは `cherry.mastersmith.access`（`domain`・`service`・`web`）。DB を使わないため `repository` は作らない。`AdminSecurityContributor` の order は **210**（U3 の 200 台の中で、U1 のテストの役の決まりが使う 200 を避ける。U2 が 110 を使ったのと同じ考え方）。`ApiDefaultAccess` は U3 が1つだけ置く。
- **C2 — 確認用 API の形**: `GET /api/admin/check` → 204（内容なし）。`nfr-design/security-design.md` 3章のとおり。判定は決まりに任せ、本体は成功を返すだけ。
- **C3 — 出来事の受け取りの形**: U3 の出来事は内部DBの更新を伴わないため、**要求と同じスレッドで、応答を書く前に**知らせる（`@EventListener` で受け取る。U2 の認証の出来事が確定の後に受け取るのと異なる）。`performance-design.md` 2章と U4 の決まり 1.3 のとおり。U3 側でも通知の例外を捕まえて WARN を出し、応答を変えない（受け止めの二重の備え）。
- **C4 — 要求の検査の拒否の仕組み**: Spring Security の `RequestRejectedHandler` を `WebSecurityCustomizer` で差し込む形を第一案とし、Step 8 の結合テストで効くことを確かめる。効かない場合は、Spring Security の連鎖より前のフィルターで `RequestRejectedException` を捕まえて同じ応答を書く形にする。どちらも満たせないときは生成を止めて案を示す。いずれの形でも、U1 の `SecurityConfig` は変えない。
- **C5 — 送り手の情報**: 出来事の接続元IP・User-Agent（512 文字で切る）・トレースIDは、U2 の `cherry.mastersmith.auth.web.ClientInfoResolver` をそのまま使う（U2 の認証の出来事と取り方をそろえるため）。U3 に同じものを作らない。
- **C6 — 画面のファイルの置き場所と登録**: `frontend/src/features/admin/`（featureId `admin`）。画面は `/admin`（`access: 'ADMIN'`、`layout: 'SHELL'`、遅延読み込み）、サイドバーの項目は `id: 'admin-area'`、`visibleWhen: 'ADMIN'`、`order` はホームの後（U1 の骨組みがホームを先頭に置く）。文言の鍵は `admin.` で始め、日英をそろえる。
- **C7 — テストの中だけで既定の拒否を緩める仕組み**: U3 のテストのソースに、`mastersmith.test-fixture.public-api=true` を設定したテストだけで有効になる決まり（order 250、`/api/**` を許可）を置く。U1 の既存の結合テスト5件にこの設定を1行足す（D1）。本番の設定では既定の拒否を切り替えられない。
- **C8 — E2E の範囲**: `frontend/e2e/u3-admin-access.e2e.ts` に「ログイン → サイドバーの『管理』→ 管理者向け領域の表示 → ログアウト」を書き、チームの代表の流れを完成させる。管理者でない利用者の 403 は、E2E には初期管理者しかいないためサーバー側の結合テストで確かめる（C9）。
- **C9 — 管理者でない利用者のテストデータ**: 結合テストの中で `user.service.UserAccountService.createUser` により管理者でない利用者を作り、U2 のテストの補助でログインしてアクセストークンを得る。テストごとに作り、実行の順番に依存させない。

## 8. 生成中の進め方

- 手順は番号の順に1つずつ進め、終わった手順のチェックボックスに印を付ける（印を付ける以外の本書の変更は Plan Approval をやり直す）。
- 各テストの手順では、`unit-test-instructions.md` のこの単位に絞ったコマンドで実行し、すべて通ってから次の手順へ進む。
- 版の組み合わせが合わない、品質の目標に届かない、設計書と食い違う、D1 の8ファイル以外で U1・U2 のファイルを変える必要がある、と分かったときは、生成を止めて案を示し、依頼者の判断を仰ぐ。
- 区切りのよいところ（例: Step 2、Step 4、Step 6、Step 8、Step 9、Step 11、Step 12、Step 13 の後）でコミットを提案し、承認を得てから行う。

## 参照した文書

- 単位の設計: `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u3-access-control/` の `functional-design/`（`functional-spec.md`・`rules.md`・`entities.md`・`frontend-components.md`）、`nfr-requirements/`（6つの文書）、`nfr-design/`（6つの設計書。特に `security-design.md` 2章・3章・5章）、`infrastructure-design/`（`infrastructure-specification.md`・`cicd-pipeline.md`・`monitoring-design.md`）
- ほかの単位との約束: `construction/u1-app-skeleton/code-generation/code-summary.md`（申し送り）、`construction/u1-app-skeleton/nfr-design/security-design.md` 3章、`construction/u2-authentication/code-generation/code-summary.md`（申し送り）、`construction/u2-authentication/nfr-design/security-design.md`、`construction/u4-audit-log/functional-design/entities.md`・`functional-spec.md`
- Inception: `inception/units-generation/unit-of-work.md`・`unit-of-work-dependency.md`、`inception/domain-design/components.md`・`decisions.md`（ADR-003・ADR-004・ADR-007・ADR-008）、`inception/requirements-analysis/requirements.md`
- 既存のコード: `backend/src/main/java/cherry/mastersmith/common/security/`・`common/error/`・`config/SecurityConfig.java`・`config/SecurityHeaderProperties.java`・`common/web/CacheControlFilter.java`、`backend/src/main/java/cherry/mastersmith/auth/web/`（`AuthSecurityContributor`・`TokenAuthenticationEntryPoint`・`AuthenticatedUserToken`・`ClientInfoResolver`）・`auth/domain/`（`AuthenticatedUser`・`TokenFailureReason`・`AuthenticationEvent`）、`backend/src/test/java/cherry/mastersmith/`（`common/testsupport/`・`config/SecurityExtensionIT.java` ほか）、`frontend/src/app/registry/types.ts`・`routing/decideRoute.ts`・`navigation/navigationItems.ts`・`pages/NotFoundPage.tsx`、`frontend/src/shared/api-client/`、`frontend/src/features/auth/registration.ts`、`frontend/e2e/`、`build.gradle.kts`、`README.md`
- チームの決まり: `aidlc/spaces/default/memory/org.md`・`team.md`・`project.md`・`phases/construction.md`
