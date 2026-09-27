# Code Generation Plan — U8 インスタンスの見た目の設定（u8-instance-appearance）

U8 のコード生成の計画を示す。作るものは、インスタンス全体の見た目の設定（ブランドカラーとフォントファミリー）を設定から起動時に1回だけ読み、無い・許されない値のときは既定（blue・sans）に置き換えて警告のログを出し、ログインなしで読める `GET /api/appearance`（契約 C7）で返す新しいパッケージ `cherry.mastersmith.appearance`（`config`・`service`・`web`）と、公開の決まりの差し込み口（order 410）である。U8 は service の単位で、画面を持たず、内部DB に触れない。

Bolt の計画（`inception/delivery-planning/bolt-plan.md`）では B4（U8・U4）の一部だが、依頼者の判断で U8 を先に独立して作り、`develop` に統合する。U4 と make-you-chic-ui の固定先の更新は後の Bolt で行う。B1（U1 メール）と B2（U2 利用者の設定）は `develop` に統合済み（`develop` の先頭 9a9a633）。u3-invitation（B3）はまだコード生成に入っていない。

この計画の中のパスは、特に断らない限りリポジトリのルートからの相対パスで、記録のディレクトリは `aidlc/spaces/default/intents/260925-user-management/`（以下「記録」）とする。

## 1. 入力にした設計

| 文書（記録の下） | 使うところ |
|---|---|
| `construction/u8-instance-appearance/functional-design/functional-spec.md`・`rules.md`・`entities.md`・`functional-design-questions.md` | 置き場（1節）、設定の項目（2節）、流れ W1〜W3、境界（4節）、エンティティ無し、決まり BR1.1〜BR3.6、答え Q1 A（画面の側がトークンを付けない）・Q2 A（前後の空白を除き大文字・小文字を問わない）、承認の場の Request Changes の直し（9節） |
| `construction/u8-instance-appearance/nfr-requirements/security-requirements.md`・`performance-requirements.md`・`reliability-requirements.md`・`scalability-requirements.md`・`observability-requirements.md`・`tech-stack-decisions.md`・`nfr-requirements-questions.md` | NFR4.1〜NFR4.8、NFR5.1・NFR5.2、NFR6.1〜NFR6.6、NFR9.1〜NFR9.8、答え Q1 A（p95 300 ミリ秒）、新しい依存を足さないこと |
| `construction/u8-instance-appearance/nfr-design/logical-components.md`・`security-design.md`・`performance-design.md`・`reliability-design.md`・`observability-design.md`・`scalability-design.md`・`nfr-design-questions.md` | 部品の一覧と置き場、order の割り当て（Q1 A: 機能の名前で 100 台ずつ、U8 は 410）、公開の範囲の確かめの表、起動時の解決と要求の処理、`AppearanceBoundaryArchitectureTest`、接続を借りないことの結合テスト（Q2 A）、警告のログの形、上流との差（`security-design.md` 8節） |
| `construction/u8-instance-appearance/infrastructure-design/infrastructure-specification.md`・`cicd-pipeline.md`・`monitoring-design.md`・`infrastructure-design-questions.md` | `application.yaml`・`.env.example`・README に足すこと、`compose.yaml` を変えないこと、検査の段と U8 の確かめ、k6 の場面（仮の名前 `appearance`、`thresholdsFor` に `p(95)<300`）、配備と戻し、指標の候補の式 |
| `inception/contract-design/contract-summary.md` の共通の決まり（認可）と C7 | `GET /api/appearance` の応答の形（`brandColor`・`fontFamily`、どちらも必須の列挙）、`/api/appearance` は差し込み口で公開にすること |
| `inception/domain-design/components.md` | InstanceAppearance の持ち物（`depends_on: []`、`entities: []`）、ADR-006（公開の API で渡す） |
| `inception/units-generation/unit-of-work.md`・`unit-of-work-story-map.md` | U8 の責務と境界（画面に当てるのは U4）、共通の決まり CR2 を受け持つこと |
| `inception/requirements-analysis/requirements.md`・`inception/user-stories/stories.md` | FR8.1・FR8.2、共通の決まり CR2（Should、受け入れ基準の番号なし）、NFR4・NFR5・NFR6・NFR9 |
| `inception/delivery-planning/bolt-plan.md` の B4 | 完了の条件のうち U8 の分（「見た目の設定の API（C7）が既定への置き換えと警告を含めて確かめられている」）、統合の形（squash） |

## 2. 承認の場の決定と、この計画での読み方

### 2.1 承認の場の決定（監査ログから洗い出したもの）

`project.md` の学び（計画の前に承認の場の決定を洗い出す）に従い、監査ログ（`audit/sakura-local-4e42a93f87ce.md`）の Functional Design・NFR Requirements・NFR Design・Infrastructure Design の `DECISION_RECORDED`（承認の場の決定）・`GATE_APPROVED`・`GATE_REJECTED` と、レビューの記録（`.aidlc-reviews/*/units/u8-instance-appearance/*/1.json`）の指摘を読んだ。U8 に関わるものと、この計画での扱いは次のとおり。

| 段 | 決定・指摘 | この計画での扱い | 手順 |
|---|---|---|---|
| 機能設計（レビュー R-01 Minor、承認の場の Request Changes で「U8 R-01 を受け入れ」） | `SecurityRuleContributor` の説明文に U8 の order の範囲が無い。コード生成の計画で説明文の書き足しを拾う | 説明文を機能の名前の割り当て（NFR 設計の Q1 A）に書き直し、`appearance` の 400 台を足す | Step 12 |
| 機能設計（レビュー R-02 Minor、承認の場の Request Changes で直した） | GET 以外のメソッドの応答（未認証 401 / `AUTHENTICATION_REQUIRED`、使えるトークン付き 405 / `METHOD_NOT_ALLOWED` と `Allow`） | 直した後の W3.3・BR3.2 のとおり、U8 では新しい応答を作らず、結合テストで既存の扱いを確かめる | Step 11 |
| NFR 要件（承認の場の決定、Minor 12 件の1つ「U8 の order の説明の U3 の明確化」、レビュー R-01） | NFR4.7 の「U3」が前の Intent の旧 U3（`access`、order 210）か、この Intent の u3-invitation かを書き分ける | この計画では書き分ける: 「前の Intent の U3」＝`access`（`AdminSecurityContributor`、210）、「u3-invitation」＝この Intent の招待（まだコードが無い。NFR 設計の割り当てで 310 の予定）。説明文も単位の番号ではなく機能の名前で書く | 2.2・Step 12 |
| NFR 設計（承認の場の決定「U8 R-01 は U3 で採用済みで受け入れ」、レビュー R-01） | u3-invitation の order 310 を U8 の段で先に決めた。引き継ぎの確かめ | U8 の説明文には `invitation` の 300 台を割り当てとして書く（値 310 そのものは U3 のコードが置く）。B3 の計画の承認の場で 310 を確かめることを「Build and Test に引き継ぐこと」に書く | Step 12、引き継ぎ |
| NFR 設計（承認の場の決定「U8 R-02 B4 の最初に指標の可否を確かめる」、レビュー R-02） | `hikaricp.connections.acquire` がテストの文脈で読めるかを最初に確かめ、読めなければ数える包みに替える。替える判定の基準を記録する | 最初のテストより前（Step 3）に、判定の手順（2.2 の「接続を借りた回数の数え方」）で確かめ、どちらにしたかを `code-summary.md` に記録する | Step 3・11 |
| NFR 設計（承認の場の決定「A5 の common.security の説明文の書き直しとカバレッジの一覧の扱いは B3 か B4 の計画で確かめる」、`security-design.md` 8節） | 説明文の書き直しの範囲と、`packagesJudgedByTotal` にある `common.security` の扱い | U8 が B3 より先に作られるため、この計画で依頼者に確かめた。説明文は同じ割り当てに触れた5か所と README の表をそろえ、`common.security`・`config`・`access.web` は実測して下限を満たせば一覧から外す（9節の決定 2・3） | Step 12・15・16 |
| NFR 設計（Q2 A） | 接続を借りた回数が、トークンを付けない GET の前後で増えないことを、既存の結合テストと同じ文脈で確かめる | 結合テストで確かめる。プールの一時停止の専用の文脈は作らない | Step 11 |
| 基盤の設計（承認の場、全8単位 READY） | U8 に固有の決定は無い（D1〜D8・N1〜N10 は U1〜U7 のもの） | 基盤の設計の文書（`application.yaml`・`.env.example`・README、`compose.yaml` は変えない、k6 の場面）のとおりに作る | Step 2・14・15 |

### 2.2 この計画での読み方

- **部品の名前**（NFR 設計で「仮、コード生成で決める」とされたもの）: 仮の名前をそのまま使う。`appearance.config.AppearanceProperties`、`appearance.service.BrandColor`・`FontFamily`（許される値の列挙）・`AppearanceResolver`（判定の純粋な関数）・`ResolvedAppearance`（解決した2つの値の `record`）・`AppearanceService`（起動時に1回だけ解決して持つ Bean）、`appearance.web.AppearanceController`・`AppearanceResponse`・`AppearanceSecurityContributor`。
- **判定の結果の形**: `AppearanceResolver` は項目ごとに「採った値」と「警告の有無」を返す（例: `Resolution<T>(T value, boolean warned)`）。どの入力（`null`・空・空白だけ・任意の文字列）でも例外を投げない全域の関数とする（NFR9.2）。前後の空白は `String.strip`、比べ方は `toLowerCase(Locale.ROOT)` による大文字・小文字を問わない比べ方（BR1.1・BR1.2、NFR9.8）。
- **警告のログのキーの名前**（「コード生成で決める」とされたもの）: `property`（項目の名前）・`defaultValue`（使った既定の値）・`allowedValues`（許される値の一覧、`blue, green, purple, orange` の形）。SLF4J のキー・値の API（`addKeyValue`）で出し、設定された値は渡さない（BR2.1、NFR9.4）。文言は日本語。
- **設定の型の登録**: 既存の `@ConfigurationPropertiesScan`（`MastersmithApplication`）で拾われる `record` とし、`@Validated` を付けない（BR1.7、NFR9.1）。`appearance.config` には `AppearanceProperties` だけを置く（8節のカバレッジの注意を参照）。
- **公開の決まり**: `AppearanceSecurityContributor` は `requestMatchers(HttpMethod.GET, "/api/appearance").permitAll()` の1つだけを足し、トークンの検証・入口の処理・ヘッダーは足さない（`security-design.md` 2.1節）。order は 410（`public static final int ORDER = 410`）。
- **order の割り当ての書き分け**: 単位の番号ではなく機能の名前で書く。`auth`（前の Intent の U2、110）・`access`（前の Intent の U3、210）・`invitation`（この Intent の u3-invitation、300 台。値 310 は U3 のコード生成で置く）・`appearance`（この Intent の U8、410）。x00・x50 はテストの決まりが使う（100・150・200・250）。
- **接続を借りた回数の数え方**（U8 R-02 の判定の手順）: Step 3 で、テストの Spring の文脈の `MeterRegistry` から (a) 名前 `hikaricp.connections.acquire`・タグ `pool=mastersmith-db` のタイマーが見つかり、(b) 内部DB の接続を借りる操作（テストの中で `DataSource#getConnection` を1回呼んで閉じる）の後に件数が1以上増えることを確かめる。(a) と (b) の両方が成り立てば指標を使い、どちらかが成り立たなければ、テストの中だけで `DataSource` を包んで `getConnection` の回数を数える仕組み（テストの設定の `BeanPostProcessor`）に替える。本番のコードは変えない。どちらを使う場合も、結合テストの中に (b) と同じ「数え方が働いていること」の確かめを残し、「増えない」が空振りの確かめにならないようにする。
- **既存の ArchUnit を緩めない**: `ArchitectureTest` と既存の機能ごとの境界テストは変えずに通す。`AppearanceBoundaryArchitectureTest` を新しく足す（`team.md` の Code Style）。
- **既存の共通の仕組みを変えない**: `SecurityConfig` の決まりの並び・ヘッダー・CSP・`CacheControlFilter`・`GlobalExceptionHandler`・`compose.yaml` は変えない（説明文の書き直しは9節の決定 2 の範囲だけで、コードの中身は変えない）。エラーの `code` の一覧（`XxxProblemTypeCatalog`）・監査の出来事・独自の指標・HealthIndicator は作らない。

## 3. 作業の場とコミットの区切り

- **作業のブランチ**: `develop` の先頭から短命のブランチ `feature/260925-user-management-u8` を作る（`team.md` の Way of Working。9節の決定 1）。
- **統合の形**: U8 はサブモジュールの固定先の更新を含まないため、`develop` へ **squash** で統合する（`develop` の1コミット）。統合コミットのメッセージは日本語で、内容が分かる件名にする（例: 「B4 の一部 U8 インスタンスの見た目の設定（GET /api/appearance と公開の決まり）」）。統合の前に `./gradlew verify` を通す（Step 17）。
- **コミット**: 生成の担当はコミットしない。生成の後に、依頼者の承認を得て、次の区切りでまとめてコミットする（`project.md` の Change Control の学び）。メッセージは日本語。

| 区切り | 中身 | 手順 |
|---|---|---|
| C1 | `appearance`（config・service・web）の本番のコードと、`application.yaml`・`.env.example` の2項目 | Step 2・4・6・8・10 |
| C2 | テスト（単体・結合・境界の構造の検査・テストの支え） | Step 3・5・7・9・11 |
| C3 | 差し込み口の order の説明文の書き直し（5か所）と、`backend/build.gradle.kts` の `packagesJudgedByTotal` の変更（9節の決定 2・3） | Step 12・16 |
| C4 | 負荷の試験の台本と文書（`perf/k6/scenarios.js`・`perf/README.md`・`README.md`） | Step 14・15 |
| C5 | この段の記録（記録の `construction/u8-instance-appearance/code-generation/` の下） | Step 18 |

- `origin` への `git push` は依頼者が行う。AI はプッシュしない。

## 4. 作るもの・手を入れるもの

| パッケージ・ファイル | 部品 | 新しい・手を入れる | 役割 |
|---|---|---|---|
| `appearance`（`package-info.java`） | パッケージの説明 | 新しい | 機能の範囲（画面に当てるのは U4）と層の構成 |
| `appearance.config` | `AppearanceProperties`（`record`、`@ConfigurationProperties("mastersmith.appearance")`、`brandColor`・`fontFamily` を文字列で受ける、`@Validated` なし） | 新しい | BR1.7、NFR9.1 |
| `appearance.service` | `BrandColor`（BLUE・GREEN・PURPLE・ORANGE、既定 BLUE）・`FontFamily`（SANS・SERIF、既定 SANS）。小文字の名前と許される値の一覧を返す | 新しい | BR1.1・BR1.2・BR1.3、C7 の列挙 |
| `appearance.service` | `AppearanceResolver`（全域の純粋な関数。前後の空白を除き `Locale.ROOT` で大文字・小文字を問わず比べ、値と警告の有無を返す） | 新しい | BR1.1〜BR1.5、NFR9.2・NFR9.8 |
| `appearance.service` | `ResolvedAppearance`（`record`: brandColor・fontFamily）・`AppearanceService`（コンストラクターで1回だけ解決し、許されない値の項目ごとに WARN を1件、`final` のフィールドに持つ。`current()` で返す） | 新しい | BR1.6・BR2.1、NFR6.2・NFR6.3・NFR9.4 |
| `appearance.web` | `AppearanceController`（`GET /api/appearance` → 200）・`AppearanceResponse`（`record`: brandColor・fontFamily、小文字の名前の文字列） | 新しい | BR3.1・BR3.4・BR3.5、C7、NFR4.5・NFR6.2 |
| `appearance.web` | `AppearanceSecurityContributor`（`SecurityRuleContributor`、order 410、GET・`/api/appearance` だけ認証なし） | 新しい | BR3.2・BR3.3、NFR4.1〜NFR4.4・NFR4.7 |
| `backend/src/main/resources/application.yaml` | `mastersmith.appearance.brand-color: ${MASTERSMITH_APPEARANCE_BRAND_COLOR:}`・`font-family: ${MASTERSMITH_APPEARANCE_FONT_FAMILY:}`（説明のコメントつき） | 手を入れる | functional-spec.md 2節、`infrastructure-specification.md` 1節 |
| `.env.example` | 2項目（値は空、許される値と既定をコメントで） | 手を入れる | 同上 |
| `common.security.SecurityRuleContributor`・`config.SecurityConfig`・`auth.web.AuthSecurityContributor`・`access.web.AdminSecurityContributor`・`auth.web.TokenAuthenticationEntryPoint` | order の割り当ての説明文（9節の決定 2） | 手を入れる（説明文だけ） | NFR4.7、機能設計の R-01、NFR 要件の R-01、NFR 設計の A5 |
| `backend/build.gradle.kts` | `packagesJudgedByTotal` から `common.security`・`config`・`access.web` を外す（実測して下限を満たすとき。9節の決定 3） | 手を入れる | `team.md` の Testing Posture |
| `perf/k6/scenarios.js`・`perf/README.md` | 場面 `appearance` | 手を入れる | NFR6.1、`cicd-pipeline.md` 3節 |
| `README.md` | 環境変数の表、API のアクセス制御の表、新しい節「インスタンスの見た目の設定（U8）」、差し込み口の表の order の割り当て | 手を入れる | `infrastructure-specification.md` 1節、NFR4.7 |

`compose.yaml`・`Dockerfile`・`SecurityConfig` の決まりの並び・`CacheControlFilter`・`GlobalExceptionHandler`・Flyway の移行は変えない。新しい依存は足さない。

## 5. 手順

各層で実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む（test-after、Testing Contract の `ordering`）。U8 の層は「設定の型（config）→ 業務処理（判定の関数 → 保持と警告）→ API（受け口と公開の決まり）」の順とする。内部DB（データの形・DB アクセス）と画面の層は持たない。

### Step 1: 作業の場の用意と、変更の前の基準（依頼者が承認した git の操作。9節の決定 1）

- [x] `develop` の先頭のハッシュを記録し、`develop` から短命のブランチ `feature/260925-user-management-u8` を作る（コミットはしない。統合は squash）
- [x] 変更の前の基準をとる: colima が動いていることを確かめ、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡して `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行し、テストの件数（単体・結合、失敗・飛ばした）と、全体のカバレッジと、9節の決定 3 で外すパッケージ（`common.security`・`config`・`access.web`）の行・分岐のカバレッジを記録する（brownfield の Test Baseline、`project.md` の Testing Posture）
- [x] 対応: B4 の共通の完了の条件、NFR9.7（外す前の値）

### Step 2: 骨組みと本番の設定

- [x] `backend/src/main/java/cherry/mastersmith/appearance/package-info.java` と、`appearance/config`・`appearance/service`・`appearance/web` の `package-info.java`（日本語の説明、ライセンスヘッダー）を作る
- [x] `backend/src/main/resources/application.yaml` の `mastersmith:` の下に `appearance:` を足す: `brand-color: ${MASTERSMITH_APPEARANCE_BRAND_COLOR:}`・`font-family: ${MASTERSMITH_APPEARANCE_FONT_FAMILY:}`。コメントで許される値（blue・green・purple・orange、sans・serif）・既定（blue・sans）・大文字・小文字と前後の空白を問わないこと・許されない値は既定にして警告すること・変更は起動し直しで当たることを書く
- [x] `.env.example` に節「インスタンスの見た目の設定（Intent 260925-user-management の U8）。すべて任意」を足し、`# MASTERSMITH_APPEARANCE_BRAND_COLOR=`・`# MASTERSMITH_APPEARANCE_FONT_FAMILY=` を値を空にして、許される値と既定をコメントで添える
- [x] `compose.yaml` は変えない（`app` は `.env` を `env_file` で読む）
- [x] 対応: functional-spec.md 2節、`infrastructure-specification.md` 1節、BR1.3

### Step 3: テストの実行の準備（最初のテストより前）と、接続を借りた回数の数え方の確かめ（U8 R-02）

- [x] `unit-test-instructions.md` の単体のコマンド `./gradlew :backend:test --tests 'cherry.mastersmith.appearance.*' --tests 'cherry.mastersmith.ArchitectureTest' --tests 'cherry.mastersmith.common.security.*'` が、U8 のテストが無い状態でも動き、既存のテストが通ることを確かめる（Gradle は、指定した絞り込みのどれかに当たるテストがあれば失敗しない）
- [x] 結合のコマンド `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.appearance.*' --tests 'cherry.mastersmith.config.SecurityExtensionIT' --tests 'cherry.mastersmith.access.web.ApiDefaultAccessIT'` が動き、既存のテストが通ることを確かめる（U8 の結合テストは組み込みの H2 だけを使い、コンテナを使わない）
- [x] `backend/src/test/resources/junit-platform.properties` の jqwik の設定（失敗した例の記録、種の再現）がそのまま使えることを確かめる
- [x] テストの支え `backend/src/test/java/cherry/mastersmith/appearance/testsupport/ConnectionAcquireCounter.java` を書き、2.2 の「接続を借りた回数の数え方」の判定（(a) タイマーが見つかる、(b) `DataSource#getConnection` の後に件数が増える）を、既存の結合テストと同じ形の Spring の文脈で確かめる。成り立てば指標で数え、成り立たなければテストの設定の `BeanPostProcessor` で `DataSource` を包んで数える形に替える。判定の結果（どちらにしたか、(a)・(b) のどちらが成り立たなかったか）を `code-summary.md` に記録する。どちらの形でも数えられないときは、この手順で生成を止めて依頼者に諮る（9節の決定 5 の (b)）
- [x] 対応: Testing Contract の `runner_step`、NFR 設計の承認の場の U8 R-02、NFR5.2

### Step 4: 設定の型（appearance.config）— 実装

- [x] `AppearanceProperties`（`record`、`@ConfigurationProperties("mastersmith.appearance")`、`String brandColor`・`String fontFamily`。既定値を持たせず、無ければ `null` または空のまま受ける。`@Validated`・検証の注釈を付けない。列挙に結び付けない）
- [x] 対応: BR1.7、NFR9.1、`tech-stack-decisions.md` の選定（設定の受け取り）

### Step 5: 設定の型 — テスト（単体）

- [x] `AppearancePropertiesTest`（Spring を起動せず、Spring Boot の `Binder` で結び付ける）: 2項目がある値を文字列のまま受ける、項目が無いと `null` または空（起動を止める例外にならない）、`red`・`mono`・` Green `・記号を含む任意の文字列でも結び付けが失敗しない、環境変数の形の名前（`MASTERSMITH_APPEARANCE_BRAND_COLOR`）からも結び付く
- [x] 単体のコマンドを実行して通す
- [x] 対応: BR1.7、NFR9.1

### Step 6: 業務処理（判定の関数）— 実装

- [x] `BrandColor`（BLUE・GREEN・PURPLE・ORANGE）・`FontFamily`（SANS・SERIF）: 小文字の名前（`value()`）、既定（`defaultValue()`: BLUE・SANS）、許される値の一覧の文字列（`blue, green, purple, orange` と `sans, serif`）。書き方は既存の `targetdb/domain/DatabaseProduct` にならう
- [x] `AppearanceResolver`: 項目ごとに、(1) `null` → 既定・警告なし、(2) `strip` して空 → 既定・警告なし（BR1.3）、(3) `toLowerCase(Locale.ROOT)` で許される値のどれかと一致 → その値・警告なし（BR1.1・BR1.2）、(4) 一致しない → 既定・警告あり（BR1.4）。2つの項目は別々に判定する（BR1.5）。例外を投げない（NFR9.2）
- [x] 対応: CR2、FR8.1・FR8.2、BR1.1〜BR1.5、NFR9.2・NFR9.8

### Step 7: 業務処理（判定の関数）— テスト（単体・性質ベース）

- [x] `AppearanceResolverTest`: functional-spec.md W1 の判定の例の表をすべて確かめる（設定なし → blue・警告なし、空・空白だけ → blue・警告なし、`green` → green、`Green`・` green `・`GREEN` → green、`red` → blue・警告あり、`blue-ish` → blue・警告あり、フォントファミリーの `Serif` → serif、`mono` → sans・警告あり）。全角の空白・タブの前後の除去、トルコ語の `I` などロケールに左右される文字を含む値が実行環境のロケールに関わらず同じ結果になること（`Locale.setDefault` を一時的に変えて確かめ、元に戻す）、4色・2フォントのすべての許される値が通ること
- [x] 性質ベースのテスト（jqwik、失敗時の種を記録）: 任意の文字列（`null` を含む）を与えても結果は常に許される値のどれか / 許される値の大文字・小文字と前後の空白の揺れは同じ結果になる / 許される値に一致しない空でない値（`strip` した後）は既定で「警告あり」になる（NFR9.8）
- [x] 単体のコマンドを実行して通す
- [x] 対応: BR1.1〜BR1.5、NFR9.2・NFR9.8

### Step 8: 業務処理（保持と警告）— 実装

- [x] `ResolvedAppearance`（`record`: `BrandColor brandColor`・`FontFamily fontFamily`）
- [x] `AppearanceService`（`@Service`、コンストラクター注入で `AppearanceProperties` を受ける）: コンストラクターの中で `AppearanceResolver` で2項目を1回だけ判定し、警告ありの項目ごとに WARN を1件出し（`LOG.atWarn().setMessage(...).addKeyValue("property", ...).addKeyValue("defaultValue", ...).addKeyValue("allowedValues", ...).log()`、スタックトレースなし、設定された値は渡さない）、結果を `private final ResolvedAppearance current` に持つ。`current()` は持った値を返すだけ（設定を読み直さず、判定もしない）。ロック・同期・`volatile` を置かない
- [x] 対応: BR1.6・BR2.1、NFR6.2・NFR6.3・NFR9.4

### Step 9: 業務処理（保持と警告）— テスト（単体）

- [x] `AppearanceServiceTest`（既存の `common/testsupport/LogEvents.java` でロガーを捕まえる）: 設定なしで blue・sans・WARN 0件、`green`・`serif` で green・serif・WARN 0件、`red`・`mono` で blue・sans・WARN ちょうど2件（項目ごと）、`red`・`Serif` で blue・serif・WARN 1件（ブランドカラーだけ）、WARN のキー（`property`・`defaultValue`・`allowedValues`）と値がそろい、スタックトレース（`throwableProxy`）が無く、メッセージとキーの値に `red`・`mono` の文字列を含まない、`current()` を何回呼んでも WARN が増えず同じインスタンスを返す（判定はコンストラクターの1回だけ。NFR6.2）
- [x] 単体のコマンドを実行して通す
- [x] 対応: BR1.3〜BR1.6・BR2.1、NFR6.2・NFR9.4

### Step 10: API と公開の決まり（appearance.web）— 実装

- [x] `AppearanceResponse`（`record`: `String brandColor`・`String fontFamily`、`from(ResolvedAppearance)` で小文字の名前を写す。2項目の外を持たない）
- [x] `AppearanceController`（`@RestController`、`@GetMapping("/api/appearance")` → 200 と `AppearanceResponse`）。業務のエラーを返さない。要求ごとのログを出さない。監査の出来事を出さない
- [x] `AppearanceSecurityContributor`（`@Component`、`ORDER = 410`、`contribute` で `http.authorizeHttpRequests(a -> a.requestMatchers(HttpMethod.GET, "/api/appearance").permitAll())` だけ）。説明文に「appearance は 400 台、x00・x50 はテストの決まりが使う」と書く。このクラスを API のテストより先に入れる（`project.md` の学び: セキュリティの決まりを、それを前提とするテストより先に入れる）
- [x] 対応: CR2、FR8.1、BR3.1〜BR3.6、C7、NFR4.1〜NFR4.5・NFR4.7・NFR6.2

### Step 11: API と公開の決まり — テスト（単体・結合・境界の構造の検査）

- [x] `AppearanceSecurityContributorTest`（単体）: order が 410、既存の本番の値（110・210）・テストの決まりの値（100・150・200・250）・u3-invitation の予定の値（310）と一緒に `SecurityExtensionValidator.sortedContributors` に渡しても重ならず、並びの位置が 310 の後・`/api/**` の既定より前になる
- [x] `AppearanceApiIT`（Spring と組み込みの H2 を起動する。`/api/**` を公開にするテスト用の決まり（`mastersmith.test-fixture.public-api`）は有効にしない）: `security-design.md` 3節の表のとおり、GET・トークンなし → 200 で本文がちょうど2項目（`brandColor`・`fontFamily`）で既定の blue・sans（NFR4.1・NFR4.5）、POST・トークンなし → 401 / `AUTHENTICATION_REQUIRED`、POST（と PUT・DELETE）・使えるトークン付き → 405 / `METHOD_NOT_ALLOWED` で `Allow` に GET を含み U8 の code が無い（NFR4.2）、HEAD・トークンなし → 401（NFR4.3）、GET・期限切れと改ざんのトークン付き → 401、GET・使えるトークン付き → 200（NFR4.4）、末尾に `/` の付いた道（`/api/appearance/`）のトークンなしの GET → 401、未認証の GET の応答に `Cache-Control: no-store`・Content-Security-Policy（`font-src 'self'` を含む）・`X-Content-Type-Options: nosniff`・`X-Frame-Options: DENY` が付く（NFR4.8）、GET の前後で監査の表の件数が変わらない（既存の `audit/testsupport/AuditRows.java`。NFR9.5）、トークンなしの GET の前後で接続を借りた回数が増えない（Step 3 で決めた数え方。同じテストの中で数え方が働くことの確かめを先に置く。NFR5.2）。使えるトークン付きの HEAD の扱い（Spring の既定で GET と同じ処理が本文なしで返る見込み）も記録する。HEAD・末尾の `/` の実際の扱いが見込みと違うときは記録し、9節の決定 5 の (a) のとおり扱う
- [x] `AppearanceStartupIT`（既存の `targetdb/config/TargetDbStartupIT.java` と同じく `SpringApplicationBuilder` と起動の引数で2項目を明示し、`OutputCaptureExtension`・既存の `common/testsupport/JsonLogRecords.java` でログを読む。開発者の環境変数に影響されない）: ブランドカラー `red`・フォントファミリー `mono` で起動が止まらず GET が 200 で blue・sans、WARN がちょうど2件（`AppearanceService` のロガー）・スタックトレースなし・キーがそろい・ログと本文に `red`・`mono` の文字列が無い、GET を3回送っても WARN の件数が増えない（NFR9.1・NFR9.4・NFR4.5）。設定なし（空の値）で起動して blue・sans・WARN 0件。`GREEN`・`Serif` で起動して green・serif・WARN 0件（BR1.1・BR1.2・BR3.1、CR2 のサーバー側の確かめ方）
- [x] `AppearanceBoundaryArchitectureTest`（`backend/src/test/java/cherry/mastersmith/appearance/`、既存の `auth/AuthBoundaryArchitectureTest.java` と同じ形）: `appearance..` は `..repository..`・`javax.sql..`・`jakarta.persistence..`・`org.springframework.jdbc..`・`org.springframework.data..`・`org.springframework.transaction..`・`com.zaxxer.hikari..` に依存しない、`appearance..` は `common..` と自分の外の機能（`auth`・`access`・`user`・`audit`・`dsl`・`dslmanage`・`targetdb`・`mail` など、`config` を含む）に依存しない、`appearance..` の外の `cherry.mastersmith` のクラスは `appearance..` に依存しない、`appearance.web` は `appearance.config` を直接使わない（`reliability-design.md` 3.1節、NFR5.1）
- [x] 単位の単体・結合のコマンドを実行して通す
- [x] 対応: CR2、FR8.1・FR8.2、BR1.3〜BR1.7・BR2.1・BR3.1〜BR3.6、NFR4.1〜NFR4.5・NFR4.7・NFR4.8・NFR5.1・NFR5.2・NFR9.1・NFR9.4・NFR9.5・NFR9.6

### Step 12: 差し込み口の order の説明文の書き直し（A5、機能設計の R-01、NFR 要件の R-01。範囲は9節の決定 2）

- [x] `common/security/SecurityRuleContributor.java` の説明文の「order の割り当て: U2 は 100 台（100〜199）、U3 は 200 台（200〜299）を使う」を、機能の名前の割り当てに書き直す: `auth` 100 台（本番 110）・`access` 200 台（本番 210）・`invitation` 300 台・`appearance` 400 台（本番 410）。本番の決まりは x10、x00・x50 はテストの決まりが使う（100・150・200・250）。冒頭の「U2・U3 が Bean として置き」も機能の名前に直す。コードの中身は変えない
- [x] 同じ割り当てに触れた説明文も、同じ機能の名前の割り当てにそろえる（9節の決定 2。コードの中身は変えない）: `config/SecurityConfig.java` の決まりの並びの「（U2 は 100 台、U3 は 200 台）」、`auth/web/AuthSecurityContributor.java` の `ORDER` の説明、`access/web/AdminSecurityContributor.java` の `ORDER` の説明、`auth/web/TokenAuthenticationEntryPoint.java` の「U3 が order 200 台の決まりで置き換える」
- [x] 既存の説明文の「U2・U3」は前の Intent（`260922-auth-audit-base`）の単位を指すため、書き直した説明文では単位の番号を使わない（この Intent の u2・u3 と取り違えないため。NFR 要件の R-01）
- [x] 対応: NFR4.7、`security-design.md` 2.3節・8節

### Step 13: 構造の検査と静的解析

- [x] 既存の `ArchitectureTest` と、ほかの機能ごとの境界テストを変えずに通す（緩めない）。緩める必要が出たときは、緩めずに作れる形を先に探し、それでも要るなら生成を止めて依頼者に諮る（9節の決定 5 の (d)）
- [x] `./gradlew :backend:spotbugsGate` を実行し、除外を足さずに通す
- [x] `./gradlew spotlessCheck` で新しいファイルのフォーマットとライセンスヘッダーを確かめる
- [x] 対応: `team.md` の Code Style（層の境界・静的解析）

### Step 14: 負荷の試験の台本（perf/k6/scenarios.js・perf/README.md）

- [x] `perf/k6/scenarios.js` に場面 `appearance` を足す: 先頭の場面の一覧のコメントに `appearance  見た目の設定の API（トークンなしの GET /api/appearance。U8 の NFR6.1）` を足し、`export default` の分かれ道に、トークンを付けない `GET ${BASE}/api/appearance`（タグ `name: appearance`）と、checks（200、本文の項目がちょうど `brandColor`・`fontFamily` の2つ）を足す。`thresholdsFor` に `appearance` → `{ 'http_req_duration{name:appearance}': ['p(95)<300'] }` を置く（前例の `dslLight` と同じ形）。場面は同時 `VUS`（既定 10）で `DURATION` の間くり返す既存の形のまま
- [x] `perf/README.md` の手順 3 の場面の一覧に `appearance` を足し、この場面は試験用の利用者とトークンが要らないこと・監査ログを増やさないこと・目標（U8 の NFR6.1、p95 300 ミリ秒、閾値 `http_req_duration{name:appearance}`）を書く
- [x] 測定はしない（performance-validation の段）。k6 で読み込めること（`k6 inspect --include-system-env-vars`）の確かめは Build and Test に引き継ぐ
- [x] 対応: NFR6.1、`cicd-pipeline.md` 3節

### Step 15: 文書（README）

- [x] `README.md` の「環境変数」の表に `MASTERSMITH_APPEARANCE_BRAND_COLOR`（既定 `blue`、許される値 blue・green・purple・orange）・`MASTERSMITH_APPEARANCE_FONT_FAMILY`（既定 `sans`、許される値 sans・serif）を足す
- [x] `README.md` に新しい節「インスタンスの見た目の設定（U8）」を足す: 2項目と許される値・既定、大文字・小文字と前後の空白を問わないこと、許されない値は既定にして起動時に項目ごとに WARN を1件出し起動は止めないこと（ログのキーと、設定された値を出さないこと）、変更は起動し直しで当たること、`GET /api/appearance` の応答の形（契約 C7）とログインなしで読めること、GET 以外のメソッドは既存の 401・405、監査に残さないこと、戻すときに `.env` の2項目を消さなくてよいこと（`cicd-pipeline.md` 5節）
- [x] `README.md` の「API のアクセス制御（U3）」の、ログインなしで呼べる一覧に `GET /api/appearance`（置く単位 U8、理由: ログインの前の画面も見た目の設定を読む）を足す（「環境変数は増えません」は U3 の節の記述のため変えない）
- [x] `README.md` の「後の単位（U2・U3・U4）が使う差し込み口」の表の「order は U2 が 100 台、U3 が 200 台」を、Step 12 と同じ機能の名前の割り当てに書き直す（9節の決定 2）
- [x] 対応: `infrastructure-specification.md` 1節、`cicd-pipeline.md` 5節、NFR4.7

### Step 16: カバレッジの一覧（packagesJudgedByTotal）と実測（9節の決定 3）

- [x] 説明文だけの変更も `team.md` の「手を入れる」に当たるとみなし、Step 12 で説明文を書き直した一覧のパッケージ `cherry.mastersmith.common.security`・`cherry.mastersmith.config`・`cherry.mastersmith.access.web` を `backend/build.gradle.kts` の `packagesJudgedByTotal` から外し、一覧の説明のコメントに「Intent 260925-user-management の U8 で common.security・config・access.web を外した」ことを足す。一覧に足さない、計測の除外を足さない（`team.md` の Testing Posture）。`auth.web` は B2 で外し済みのため対象外
- [x] 新しい `appearance.service`・`appearance.web` は一覧に無いため、自動でパッケージごとの下限の対象になる
- [x] colima が動いていることを確かめ、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡して `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行し、`appearance.service`・`appearance.web` と、外したパッケージの行・分岐のカバレッジを実測して記録する（`project.md` の Testing Posture）
- [x] 下限（行 80%・分岐 70%）を下回るパッケージがあれば、テストを足してやり直す（参考の値では `config` の分岐 73.3% が下限に近い）。一覧に戻さない・除外を増やさない。テストを足しても届かないときは生成を止め、値と原因を示して依頼者に諮る（9節の決定 3・決定 5 の (c)）
- [x] 対応: NFR9.7、`team.md` の Testing Posture

### Step 17: 1コマンドの検査（統合の前の関門）

- [x] colima が動いていることを確かめ、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡して `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行し、すべての段が通ることを確かめる。対象DB のテストが SKIPPED になっていないことを確かめる（`project.md` の Testing Posture）
- [x] テストの件数（単体・結合）と、全体のカバレッジ、Step 16 のパッケージごとの行・分岐のカバレッジを実測の数字で記録し、Step 1 の基準と比べる（既存のテストが減っていない・失敗していない）
- [x] 秘密情報の検出（Gitleaks）と依存関係の脆弱性検査（OSV-Scanner）が通ることを確かめる（新しい依存は足していない）
- [x] README の「ビルドした WAR での画面の確認（E2E）」の手順で、先に手元のメールの受け手 Mailpit を起動し（`docker compose --profile mail up -d mailpit`。`./gradlew e2eTest` は始める前に Mailpit の API に届くかを確かめ、届かなければ失敗する）、既存の `./gradlew e2eTest` を流して通ることを確かめる（9節の決定 4。公開の決まりを足す変更を、`team.md` の「認証に関わる変更」と読む）。E2E が Mailpit に残したメールの片付けは README の手順（開発者が止めて消す）のとおりとし、AI は Mailpit を止めない・消さない。結果（件数・通ったか）を `code-summary.md` に記録する
- [x] 対応: B4 の共通の完了の条件、`project.md` の Mandated（統合の前の確認）

### Step 18: 記録とコミットの提案

- [x] `code-summary.md`（作ったもの、上流との差（8節）、Step 1 と Step 17 の実測、パッケージごとのカバレッジ、Step 3 の接続を借りた回数の数え方の判定の結果、HEAD・末尾の `/` の実際の扱い、E2E の結果、9節の決定の反映）、`source-manifest.json`（作った・変えたアプリのソースのすべてのパス）、`traceability.json`（FR8.1・FR8.2 と CR2 から BR・NFR の枝番を経て手順と部品へ）を作る（コード生成の段の手順）
- [ ] 3節の C1〜C5 の区切りで、コミットの内容とメッセージの案を依頼者に示して承認を得る（生成の担当はコミットしない）
- [ ] 対応: 段の記録、`project.md` の Change Control

## 6. ストーリー・要件と手順の対応

U8 はストーリーを持たず、共通の決まり CR2（Should、受け入れ基準の番号なし）を受け持つ（`unit-of-work-story-map.md`）。CR2 の画面側の確かめ方は U4 が受け持つ。

| ストーリー・要件 | 決まり・NFR | 手順 |
|---|---|---|
| CR2（サーバー側）: green・serif を設定すると画面へ渡す値が green・serif になる | FR8.1、BR1.1・BR1.2・BR3.1 | Step 6・7・10・11 |
| CR2（サーバー側）: red を設定すると起動し、渡す値が blue になり、警告のログが1件出る | FR8.2、BR1.4・BR1.7・BR2.1・BR3.1 | Step 4〜11 |
| CR2（サーバー側）: ログインなしで読め、利用者の操作で変える口を持たない | FR8.1、BR3.1・BR3.2 | Step 10・11 |
| 設定の読み取りと解決 | BR1.1〜BR1.7、NFR9.1・NFR9.2・NFR9.8 | Step 2・4〜9 |
| 警告のログ | BR2.1、NFR9.4 | Step 8・9・11 |
| 公開の API と公開の決まり | BR3.1〜BR3.6、NFR4.1〜NFR4.5・NFR4.8 | Step 10・11 |
| order の割り当て | NFR4.7 | Step 10・11・12・15 |
| 内部DB に依存しない | NFR5.1・NFR5.2 | Step 3・11 |
| 性能と待ち合わせを置かないこと | NFR6.1〜NFR6.4 | Step 8・9・14、Build and Test に引き継ぐこと |
| 観測（指標・トレース・健全性・監査） | NFR6.5・NFR6.6・NFR9.3・NFR9.5・NFR9.6 | Step 11（監査なし・既存の健全性のテスト）、Build and Test に引き継ぐこと |
| テストと品質の関門 | NFR9.7・NFR9.8 | Step 3〜17 |
| 回数の制限を置かないこと | NFR4.6 | 作らない（記録だけ。Step 15 の README） |

## 7. テストの量（Standard）

部品ごとに 5〜8 件の単体テストと、境界の結合テストを置く。成功の場合と、少なくとも2つの失敗・境目の場合を含める（`phases/construction.md` の Testing Standards）。

| 部品 | 単体 | 結合 |
|---|---|---|
| 設定の型（`appearance.config`） | `AppearancePropertiesTest` 4〜5 件 | `AppearanceStartupIT` で起動の結び付けを確かめる |
| 判定の関数（`appearance.service`） | `AppearanceResolverTest` 8 件＋性質ベース 3 件 | — |
| 保持と警告（`appearance.service`） | `AppearanceServiceTest` 6〜7 件 | `AppearanceStartupIT` 3 件（`red`・`mono`、設定なし、`GREEN`・`Serif`） |
| API と公開の決まり（`appearance.web`） | `AppearanceSecurityContributorTest` 2〜3 件 | `AppearanceApiIT` 8〜10 件（公開の範囲の表、ヘッダー、本文の2項目、監査なし、接続を借りない） |
| 構造の検査 | `AppearanceBoundaryArchitectureTest` 4 件、既存の境界テストをそのまま通す | — |

## 8. この計画で決めたこと・承認済みの文書との差

承認済みの文書は書き換えず、差をここと `code-summary.md` に記録する（`project.md` の決まり）。

| 対象 | 承認済みの形 | この計画での扱い | 理由 |
|---|---|---|---|
| 部品の名前 | 「部品の名前は仮で、コード生成で決める」（`logical-components.md` 1節） | 仮の名前をそのまま使い、判定の結果の型・`ResolvedAppearance`・`BrandColor`・`FontFamily` を足す（2.2） | 設計どおりの構成。名前を変える理由が無い |
| 警告のログのキー | 「キーの名前はコード生成で決める」（`observability-design.md` 3.1節） | `property`・`defaultValue`・`allowedValues`（2.2） | 設計の説明用の断片の仮の名前のまま |
| `appearance.config` のカバレッジ | 「`appearance.config`・`appearance.service`・`appearance.web` はパッケージごとの下限の対象」（`logical-components.md` 5節、NFR9.7） | `appearance.config` には `AppearanceProperties` だけを置く。既存の計測の除外（`backend/build.gradle.kts` の `coverageExclusions` の `**/*Properties.class`、設定値だけのクラス）に当たるため、`appearance.config` は計測の対象のクラスを持たず、パッケージごとの報告に出ない。下限の対象として実測するのは `appearance.service`・`appearance.web` | 除外を足したのではなく、`team.md` の「設定値だけのクラス」の既存の除外に当たるだけ。設定の型の結び付けは `AppearancePropertiesTest`・`AppearanceStartupIT` で確かめる |
| 接続を借りた回数の数え方 | 「指標と数える包みのどちらを使うかはコード生成に残す」（`reliability-design.md` 7節）、「B4 の最初に指標の可否を確かめる」（承認の場の U8 R-02） | Step 3 で判定の手順（2.2）により決め、結果を記録する | 承認の場の決定のとおり |
| 説明文の書き直しの範囲 | 「`SecurityRuleContributor` の説明文を書き直す」（`security-design.md` 2.3節） | 依頼者の決定（9節の決定 2）で、同じ割り当てに触れた説明文（`SecurityConfig`・`AuthSecurityContributor`・`AdminSecurityContributor`・`TokenAuthenticationEntryPoint`）と README の差し込み口の表もそろえる（Step 12・15）。コードの中身は変えない | `SecurityRuleContributor` だけを直すと、ほかの説明文の「U2 は 100 台、U3 は 200 台」と食い違ったまま残るため |
| `common.security` などのカバレッジの一覧 | 「書き直す Bolt（先に作る U3 の B3 か U8 の B4）の計画で、依頼者に扱いを確かめる」（`security-design.md` 8節、承認の場の A5） | 依頼者の決定（9節の決定 3）で、説明文だけの変更も「手を入れる」に当たるとし、`common.security`・`config`・`access.web` を実測して下限を満たせば一覧から外す。届かなければテストを足し、それでも届かなければ止めて諮る（Step 16） | 承認の場の決定のとおり、この計画で確かめた。一覧から外すのは下限を強める向き |
| 統合の前の E2E | U8 の単独の E2E は足さない（`cicd-pipeline.md` 2節）。既存の E2E を流すかの記述は無い | 依頼者の決定（9節の決定 4）で、統合の前に既存の `./gradlew e2eTest` を Mailpit を起動して流す（Step 17）。E2E は足さない | `team.md` の「認証に関わる変更を統合する前に E2E を流す」を、公開の決まりの追加に当てた |
| u3-invitation の order | 「U3 の NFR 設計・コード生成に 310 を引き継ぐ」（`security-design.md` 2.3節） | U8 の説明文には `invitation` の 300 台だけを書き、値 310 は U3 のコードが置く。U8 の単体テストでは 310 を「予定の値」として重ならないことだけを確かめる | u3-invitation のコードがまだ無いため、U8 から U3 の値を先に作らない |
| HEAD・末尾の `/` の扱い | 「実際の扱いが見込みと違えばコード生成で記録し、依頼者に確かめる」（NFR4.3、`security-design.md` 3節） | 結合テストで確かめ、見込み（401）と違えば9節の決定 5 の (a) のとおり扱う | 承認済みの設計のとおり |
| k6 の場面の名前 | 「仮に `appearance`」（`cicd-pipeline.md` 3節） | `appearance` に決める | 設計の仮の名前のまま |
| functional-spec.md W3.3 の入口の処理の名前 | 未認証の 401 は `TokenAuthenticationEntryPoint` が返す | 実際には `AdminSecurityContributor`（order 210）が入口の処理を `AdminAuthenticationEntryPoint` に置き換え、それが `TokenAuthenticationEntryPoint` に応答の書き出しを任せる。応答（401 / `AUTHENTICATION_REQUIRED`）は同じ | 記述の細部の差で、ふるまいの差は無い。結合テストで応答を確かめる |

## 9. 依頼者の決定

計画の承認の前に諮った論点について、依頼者が次のとおり決めた。計画の各 Step と8節はこの決定に合わせてある。

1. **作業の場と git の操作の順**: A（案のとおり）。Step 1 で `develop` から短命のブランチ `feature/260925-user-management-u8` を作り、`develop` へは squash で統合する。コミットは生成の後に、依頼者の承認を得て C1〜C5 でまとめて行う（3節、Step 1・Step 18）。
2. **order の説明文の書き直しの範囲**（承認の場の A5、機能設計の R-01、NFR 要件の R-01）: B。`common/security/SecurityRuleContributor.java` に加え、同じ割り当てに触れた `config/SecurityConfig.java`・`auth/web/AuthSecurityContributor.java`・`access/web/AdminSecurityContributor.java`・`auth/web/TokenAuthenticationEntryPoint.java` の説明文と、README の「後の単位（U2・U3・U4）が使う差し込み口」の表を、機能の名前の割り当て（`auth` 100 台・`access` 200 台・`invitation` 300 台・`appearance` 400 台、x00・x50 はテストの決まりが使う）にそろえる。コードの中身は変えない（Step 12・15、8節）。
3. **説明文だけを変えた既存のパッケージのカバレッジの一覧（`packagesJudgedByTotal`）の扱い**（承認の場の A5）: A。説明文だけの変更も `team.md` の「手を入れる」に当たるとみなし、`common.security`・`config`・`access.web` を実測して下限（行 80%・分岐 70%）を満たせば一覧から外し、パッケージごとの下限の対象に戻す。届かなければテストを足し、それでも届かなければ生成を止めて諮る（Step 1・Step 16、8節）。参考の値（B2 の統合の前の報告 `backend/build/reports/jacoco/test/jacocoTestReport.xml`、2026-09-28 02:13。確定の値ではなく、Step 1・Step 16 で実測し直す）: `common.security` 行 100%・分岐 100%、`config` 行 92.9%・分岐 73.3%、`access.web` 行 100%・分岐 93.8%（`config` の分岐は下限に近い）。
4. **統合の前の E2E**: A。公開の決まり（差し込み口）を足す変更を `team.md` の「認証に関わる変更」と読み、統合の前に既存の `./gradlew e2eTest` を流す。先に手元のメールの受け手 Mailpit を起動する（`docker compose --profile mail up -d mailpit`）。U8 の E2E は足さない（Step 17、8節）。
5. **生成の途中で止めて諮る場面**: 推奨のとおり。
   - (a) Step 11 で、HEAD・末尾の `/` の実際の扱いが見込み（トークンなしで 401）と違ったとき: GET の公開の決まりが HEAD にも当たる（本文の無い同じ応答が返る）だけなら、記録して続け、承認の場で確かめる。GET と HEAD の外のメソッドや別の道が公開になるときは、生成を止めて諮る
   - (b) Step 3 で、接続を借りた回数を指標でも数える包みでも数えられないとき: 生成を止めて諮る
   - (c) Step 16 で、テストを足してもパッケージごとの下限（行 80%・分岐 70%）に届かないとき: 生成を止めて諮る
   - (d) 既存の ArchUnit の境界テストを緩める必要が出たとき（緩めずに作れる形を先に探す）: 生成を止めて諮る

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
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる。ただし、既存のパッケージに単独で下限を下回るものがあれば、パッケージごとの下限は新しく作るパッケージだけに当てる（既存のパッケージは全体の合計で判定する）。どちらになるかは、パッケージごとの下限を入れる Bolt で既存のパッケージのカバレッジを実測して決め、結果を記録する。\n- 全体の合計で判定している既存のパッケージ（`backend/build.gradle.kts` の `packagesJudgedByTotal` の一覧。`user.domain`・`user.repository`・`user.service` を含む 22 パッケージ）に手を入れる Bolt では、テストを足してそのパッケージの下限（行 80%・分岐 70%）を満たし、一覧から外してパッケージごとの下限の対象に必ず戻す。カバレッジは、その Bolt のテストを足した後に `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、値を記録する。一度外したパッケージは一覧に戻さず、一覧を増やす変更はしない。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3種類すべてを `./gradlew verify` の中で毎回実行する（CI も同じ）。Intent `260923-dsl-schema-loader` の Build and Test で `verify` の時間と colima の VM のメモリを実測したうえで、この形に決めた。コンテナの実行環境が無いときの扱いは Way of Working のとおり。\n- 外へ送るメール（SMTP）を使う機能のテストは、送信の部品をモックで置き換えて済ませず、テストの中で JVM の中に起動するテスト用の SMTP の受け手で実際に受け、宛先・件名・本文（HTML）・言語を確かめる。受け手にコンテナは使わない（コンテナの実行環境が無いときに飛ばしてよいテストは、対象DB のテストだけのままとする）。送信の失敗（受け手が接続を拒む・応答しない）のテストも必ず入れる。実在の宛先へは送らない。受け手の具体の道具は、採用の前のライセンスの確認とあわせて設計の段で決める。\n- 画面からの一連の操作を確かめるテスト（E2E）は Playwright（`./gradlew e2eTest`）で書き、`./gradlew verify` と CI の外に置く。本数は代表的な流れに絞り、機能の Intent ごとに代表の流れを1本まで足す。新しく足す流れは、前のテストが作った状態に頼らず、利用者などの前提を自分で作る。E2E は、画面・認証に関わる変更を統合する前と、リリースの前に手元で実行する。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。不安定なテストは放置せず、原因を直すまで統合しない。\n- 認証・認可・監査に関わる機能では、次のテストを必ず書く。★印の項目は要件（しきい値や動作）が未確定のため、要件定義で決めてからテストを書く。\n  - アカウントロック: 失敗回数のしきい値の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロック中は正しいパスワードでも拒否、ログイン成功時の失敗回数の扱い。★しきい値、回数を数える期間、ロックの解除方法\n  - ログイン: 存在しないユーザーとパスワード誤りで、応答からユーザーIDの存在を推測できないこと\n  - トークン: 有効期限の境界（直前は有効／直後は無効）、署名の改ざん、署名方式の指定を悪用した改ざん（`alg: none` 等）、ログアウト後のリフレッシュトークンの拒否、ログアウト後もアクセストークンが有効期限まで使えること（決定済みの仕様として明示する）。★各トークンの有効期限の値、リフレッシュトークンを使うたびに作り直すか\n  - 初期管理者の自動作成: 2回目以降の起動で重複作成しない、設定が無い／不正なときの動作、パスワードがログに出ない。★設定が無いときに起動を止めるか\n  - 認可: 未認証（401）、管理者フラグなし（403）、管理者（200）をサーバー側のテストで確かめる（画面で管理メニューを隠すことはサーバー側の検査の代わりにしない）\n  - 監査ログ: 対象イベントごとに必須項目が記録されること、存在しないユーザーIDでのログイン失敗も記録されること。★監査ログの書き込みに失敗したときに操作を失敗させるか\n  - 秘密情報の漏えい: ログ・監査ログの出力にパスワード・トークンの値が含まれないこと\n  - 構造化ログ・分散トレース: 決めた形式で出る、トレースIDがログに含まれる、外部エクスポートが既定で無効であること\n  - 画面: ログイン画面のアクセシビリティ検査、ロック時のメッセージ表示、ログアウトでトークンが破棄されること\n  - 招待と登録の完了: 招待の有効期限の境界（直前は有効／直後は無効）、使い終えた招待・取り消した招待の再使用の拒否、改ざんした招待・存在しない招待の拒否（応答から利用者の存在を推測できないこと）、招待中（登録が終わっていない）の利用者はログイン・トークンの更新・アクセストークンの認証のどれでも拒否されること。★招待の有効期限の値、再送・取り消しの扱い\n  - パスワードの変更: 今のパスワードの確かめ、パスワードの規則の境界、変更の後のリフレッシュトークンの扱い（決めた側の動作を明示したテストにする）。★パスワードの規則、変更の後にほかの端末のリフレッシュトークンを無効にするか\n- 利用者へメールを送る機能では、Code Style と `project.md` のメールの決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 本文のエスケープ: 差し込む利用者の値（氏名・メールアドレスなど）に `<`・`>`・`&`・`\"`・`'` を含めても、タグや属性としてそのまま出ないこと。利用者の値をエスケープしない差し込み（`{{{ }}}`・`{{& }}`）で入れていないこと、属性の値を二重引用符で囲んでいること（エンジンの `{{ }}` は `'` を置き換えないため）\n  - テンプレートの描画: 日本語と英語のすべてのテンプレートを描けて、件名が空でないこと、差し込み漏れ（`{{` の残り）が無いこと、ライセンスヘッダーが描いた本文に出ないこと\n  - 招待の URL: 設定したベース URL だけから組み立て、要求の Host ヘッダーから組み立てないこと。ベース URL の設定が無いときに要求の Host を変えても、招待の URL に出ないこと。★ベース URL の設定が無いときの動作\n  - トークンと URL の漏えい: 招待のトークンと招待の URL が、アプリのログ・監査ログ・トレースの属性・エラー応答に含まれないこと（既存の `*SecretLeakIT` と同じ形で確かめる）\n  - ヘッダーへの差し込み: 宛先・件名・差し込む値に改行（CR・LF）を含めても、メールのヘッダーが増えず拒否されること\n  - 送信の失敗: 送信が失敗したとき（受け手が接続を拒む・応答しない）に、宛先のメールアドレス・SMTP の応答・資格情報が応答とログに含まれないこと。★送信が失敗したときの業務の動作（招待を失敗させるか、再送に回すか）\n- 利用者が投入する DSL（YAML）を読み込む機能では、Code Style の「信頼できない入力」の決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。上限の具体的な数値は設計の段で決め、決めた値の境界で確かめる。\n  - 大きさ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 入れ子の深さ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 別名（アンカー）: 展開の数が上限を超えると拒否され、別名の展開の爆発で処理が止まらない\n  - タグ: 任意の型を作るタグ（`!!` など）を含む DSL は拒否され、型が作られない\n  - 重複キー: 同じキーが重なる DSL はエラーになり、後の値で黙って上書きされない\n  - JSON Schema の `$ref`: 外部の URL を取りに行かない\n  - 拒否の応答: Problem Details の形で返り、YAML・JSON Schema の部品の例外のメッセージを含まない\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22)"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 (learned 2026-09-23) \n- 負荷の環境や配備先が決まらないと測れない目標（応答時間のパーセンタイル、運用の指標、ファイルの権限など）は、Build and Test で `Unverified` とし、持ち主の段（performance-validation・observability-setup・deployment-execution）を明記して引き継ぐ。目標を緩めて「満たした」ことにはしない。 (learned 2026-09-23) \n- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は perf/README.md。 (learned 2026-09-23) \n- 負荷の試験で、アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。 (learned 2026-09-23) \n- 同時の重なりを確実に作るため、本番のコードを変えずに、監査の書き込みの時間を測る LongSupplier（AuditEventListener で2本目を借りる直前に呼ばれる）をテストで差し替えて待ち合わせる方式にした。既存の LoginConcurrencyIT は 8 スレッドでプールの 10 に届かず、前回の失敗のログインで尽きなかった理由の1つと見られる。 (learned 2026-09-23) \n- Intent の流れに Performance Validation の段が無く、負荷の環境（使い捨ての環境）を手元で用意できるときは、k6 の試験と NMT の測定の持ち主を Build and Test とし、Unverified で引き継がずにその段で実行する。 (learned 2026-09-23) \n- 修正の前の設定（例: 上限 1g）も修正の後の環境（例: CPU 4 の VM）で流し（pre1g）、要件の前提（VM を上げても F3 が起きる）を実測で裏付ける。 (learned 2026-09-23) \n- パッケージごとのカバレッジの下限と SpotBugs の SQL_ の関門を U1 の計画に入れた。team.md の決まりだが今のビルドに無く、この Intent で最初に作る単位のため。U1 の設計の文書には無い作業。 (learned 2026-09-24) \n- U4 で既存の AuditSecretLeakIT の列の一覧に V6 の4列を足し、テストの JVM のヒープを 1g にした。前者は承認済みの V6 と必ず食い違うため、後者は構造の検査がクラスを持ち続け U3 の 10MB 超えのテストでヒープが尽きたため。どちらも計画に無い変更で、依頼者に確かめる。 (learned 2026-09-24) \n- 応答しない対象DB の TIMEOUT の確かめに、テストの中で開いた ServerSocket（受け付けて何も返さない）を使う。外の端末に頼らず確実に再現できる代わりに、本物の DB の遅延ではない。 (learned 2026-09-24) \n- パッケージごとのカバレッジの下限は新しいパッケージだけに当てた。実測で audit.service（行 77.2%）・common.health（行 79.2%）・auth.repository（分岐 50.0%）が単独で下回ったため（team.md の決まりどおり）。既存の 22 パッケージを一覧で外し、新しいパッケージは自動で対象になる。 (learned 2026-09-24) \n- 負荷の試験で接続プールが尽きたかを確かめるときは、使い捨てのアプリにだけ MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics を渡して /actuator/metrics の hikaricp の値を読み、数秒ごとの使用中の数ではなく、待ちの時間切れの累計と借りるまでの待ちの最大で判断する（配備したアプリの公開の範囲は変えない）。 (learned 2026-09-24) \n- k6 などの長い試験は caffeinate -i を付けて流し、PC の自動のスリープで要求が止まって結果が崩れるのを防ぐ（バッテリー駆動のまま 414 秒スリープし、要求が 6分52秒止まった）。内部DB に SQL で直接入れた試験用の利用者はロックの状態の行が無いため、同時のログインを流す前に1人ずつログインさせて行を作る。 (learned 2026-09-24) \n- 軽い API の性能は、投入を重ねて内部DB のファイルが膨らんだ状態（悪い側の条件）のまま測る。メモリの最大（memory.peak）を比べる試験の前は、アプリのコンテナを作り直して前の最大の値を消す。 (learned 2026-09-24) \n- Performance Validation の段が無いため、2件目の直しの負荷の試験での確かめ（Q5: A）の持ち主を Build and Test とした（project.md の学びどおり）。 (learned 2026-09-24) \n- colima の PC で ./gradlew verify を流すときは、README の DOCKER_HOST と TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE をシェルに渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗した。付けて流し直した結果を基準とした。 (learned 2026-09-25) \n- unit-test-instructions.md の k6 inspect のコマンドは --include-system-env-vars が無いと場面の名前が undefined になり確かめにならなかった。承認済みの文書は変えず、実際には付けて流し、code-summary.md に差を記録した。 (learned 2026-09-25) \n- 1回目の verify で AccessTokenApiIT の 6 件が接続の失敗で落ちた。変更の経路に触れず、同じ時刻に Gradle の作業プロセスとの接続も時間切れだったため PC の負荷による一時的な失敗と見立て、段の中の直しの1回目としてコードを変えずに verify を流し直して通した。原因は確かめていない。 (learned 2026-09-25) \n- 依頼者の判断で、試験のイメージを mastersmith:local ではなく mastersmith:followup-fixes で作り、perf/README の手順（local を作り直す）から外れた。配備したアプリは k6 のあいだ止め、約 17 分後に同じコンテナで起動し直した。 (learned 2026-09-25) \n- 試験の後にロックの状態の行の数を数える問い合わせの列の名前を誤り、使い捨ての環境を消した後で取り直せなかった。合格の条件（ログインの checks と 500 の件数）で判定した。消す前に確かめの結果を見てから片付けるべきだった。 (learned 2026-09-25) \n- Q4（確かめられたときだけ直す）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違いうるため、追加の質問 F3 で、再現できなければ不安定と確かめられていない扱いとして統合してよいことを確かめた。 (learned 2026-09-25) \n- 要件 FR2.2（目標は計画の承認の場で決める）を満たすため、依頼者の決定（Q1: A）で、計画を書く前に dslMixed でメモリの内訳を測ることにした。測る間は配備したアプリを止める。 (learned 2026-09-25) \n- 生成で、テストの既定で MBean の登録を無効にする置き場を、計画の候補の TestDatabase ではなくテストの EnvironmentPostProcessor にした。TestDatabase を使わずに Spring を起動するテストのクラスが 15 あり、漏れるため。再現の結合テストは 12 回では伸びが小さく判定がはっきりしないため、24 回・履歴の上限 3 にした。 (learned 2026-09-25) \n- AccessTokenApiIT は、同じ例外の文言を仕組み（colima の IPv4 の転送と Java の IPv6 の待ち受けの番号の重なり）ごと再現できたことを原因の確認とみなし、依頼者の決定（D7）でテストの JVM に preferIPv4Stack を付けて直した。実際の1回目がこれで起きたかは確かめられず、今回の繰り返しでは重なりは起きなかった。 (learned 2026-09-25) \n- Test Strategy は Minimal だが、要件 FR5 と計画の「Build and Test に引き継ぐこと」のため、結合・性能・セキュリティの手順書も作り、この段で dslMixed・--storage --compact（40 回）・詰め直しの最中のログイン・refresh・dslCycle を流した。 (learned 2026-09-25) \n- 計画の dslCycle を最初の台本に入れ忘れ、配備したアプリをもう一度（約 4 分）止めて流した。負荷の試験の台本を書く前に、計画の「Build and Test に引き継ぐこと」の項目を一つずつ台本の手順と突き合わせる。 (learned 2026-09-25)"
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
  "input_sha256": "sha256:591505487a7fb6a8983ed2b2b5ed40524251e26ce603ffd5e0b713cb57a870cd",
  "contract_sha256": "sha256:b0e0f1eed9e80e43e2a774e6087e20f8a66c419412203955de70d736ee76c67f"
}
```

Testing Contract の `plan_profile.steps` との対応: 骨組みと本番の設定は Step 1・2、テストの実行の準備は Step 3（最初のテストの Step 5 より前）、設定の型（業務処理の入口の設定の受け取り）は Step 4・5、業務処理は Step 6〜9、API は Step 10・11、説明文・構造の検査・静的解析・環境とビルドの設定は Step 12・13・16・17、負荷の試験の台本と文書と記録は Step 14・15・18。U8 は内部DB に触れないため、データの形（Data model / database behavior）と DB アクセス（Repository / data access）の手順は無い（`functional-spec.md` 1節、`entities.md` の `entities: []`）。画面を持たないため、画面の層（Frontend behavior）の手順も無い（画面に当てるのは U4）。

## Build and Test に引き継ぐこと

| 項目 | 引き継ぐ内容 | 持ち主 |
|---|---|---|
| カバレッジの実測 | `:backend:cleanTest :backend:cleanIntegrationTest` を付けた verify で、全体と、`appearance.service`・`appearance.web` と、9節の決定 3 で外したパッケージ（`common.security`・`config`・`access.web`）の値をもう一度実測して記録する | Build and Test |
| k6 の台本の読み込み | `perf/k6/scenarios.js` の場面 `appearance` が k6 で読み込めること（`k6 inspect --include-system-env-vars`、`SCENARIO=appearance`。`project.md` の学びのとおり `--include-system-env-vars` を付ける）と、閾値 `http_req_duration{name:appearance}` の `p(95)<300` が載っていることを確かめる | Build and Test |
| 応答時間の測定 | NFR6.1（トークンなしの `GET /api/appearance` が同時 10 件で p95 300 ミリ秒以内、全件 200・本文2項目）。使い捨ての環境（`docker/perf/compose.yaml`）で、内部DB のファイルが膨らんだ状態のまま、`caffeinate -i` を付けて流す。測る間は配備したアプリを止める。台本を書く前に、この表の項目を台本の手順と1つずつ突き合わせる（`project.md` の Testing Posture） | performance-validation |
| 指標と警報 | `http_server_requests_milliseconds_*` の `uri="/api/appearance"` の実際のラベルの値、既存のダッシュボードの式で値が出ること、パネル・警報を足すか（`monitoring-design.md` 1節・2節、NFR6.5）。SLO の判定は `Unverified`（NFR9.3） | observability-setup・feedback-optimization |
| スモークテスト | 配備の後のスモークテストに「未認証の `GET /api/appearance` が 200 で2項目を返す」を足すか、起動のログに見た目の設定の WARN が無いこと（`cicd-pipeline.md` 4節） | deployment-pipeline・deployment-execution |
| u3-invitation の order | 説明文の割り当て（`invitation` の 300 台）のとおり、u3-invitation の差し込み口が 310 を使うことを B3 の計画の承認の場で確かめる（NFR 設計のレビュー R-01、承認の場の「U8 R-01 は U3 で採用済みで受け入れ」） | B3（U3 のコード生成の計画） |
| U4 との突き合わせ | U4 の画面の側が、この API をトークンを付けずに呼ぶこと（BR3.3、U4 D10）と、応答の2項目の形（契約 C7）を使うことを、B4 の後半の U4 の計画で確かめる | B4 の後半（U4 のコード生成の計画） |
| 残る危険 | ログインなしの API の回数の制限を置かない（NFR4.6）。配備先が決まったときに前段の制限で扱う | 受け入れ済み（記録だけ） |
