# Generation Notes — U3 利用者の管理の API（u3-user-admin-api）

コード生成の経過・実測の値・計画との差を記録する。パスはリポジトリのルートからの相対パス。

## B3（Step 1〜9、1回目の依頼）

作業ブランチ `feature/260930-user-admin-b3`（`develop` の 493b4dc から）。コミット・`git add` はしていない。Testing Contract は `sha256:25c76d4e2f9a91edd5d55fbff38287bcce006bace671ccf3da4e81c3a6393f2a`（test-after、層ごとに実装 → その層のテスト → 通ってから次の層）。

### Step 1: 作業の場と変更の前の基準

- `develop` の先頭: `493b4dcdcb5c4f6e5b7478bc3bbe607b0997f122`（作業ブランチの先頭も同じ）。アプリのソースに未コミットの変更なし（監査ログの追記だけ。ワークフローの記録として外して判断した）。
- `frontend/playwright-report/`・`frontend/test-results/` は無かった（消したものなし・共有なし）。
- Dependabot の開いている知らせ: `gh pr list --state open` は 0 件、`origin` の `dependabot/*` の追跡ブランチも無し。取り込むものなし。
- 変更の前の基準（`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡し、`caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`、2026-10-02 22:00 ごろ）:

| 項目 | 値 |
|---|---|
| 結果 | BUILD SUCCESSFUL |
| 時間 | 6 分 23 秒（壁時計 383 秒。Gradle の表示 6m 22s） |
| 単体（`test`） | 1,287 件、失敗 0・誤り 0・飛ばし 0 |
| 結合（`integrationTest`） | 587 件、失敗 0・誤り 0・飛ばし 0（対象DB のテストも SKIPPED なし） |
| 画面（Vitest） | 95 ファイル・801 件、すべて通過 |
| 全体のカバレッジ | 行 98.8%（5652/5719）・分岐 94.5%（2069/2190） |

4.5 の表のパッケージ（同じ実行の `jacocoTestReport.xml`）:

| パッケージ | 行 | 分岐 |
|---|---|---|
| `user.domain` | 99.5%（208/209） | 97.6%（121/124） |
| `user.repository` | 計測の行なし | — |
| `user.service` | 100.0%（236/236） | 95.6%（86/90） |
| `auth.domain` | 98.0%（98/100） | 100.0%（18/18） |
| `auth.repository` | 100.0%（33/33） | 100.0%（2/2） |
| `auth.service` | 99.1%（222/224） | 92.4%（61/66） |
| `audit.domain` | 99.5%（214/215） | 97.8%（44/45） |
| `audit.service` | 100.0%（148/148） | 84.4%（27/32） |
| `invitation.repository` | 100.0%（2/2） | 分岐なし |
| `access.domain` | 100.0%（53/53） | 100.0%（29/29） |
| `common.observability` | 97.5%（115/118） | 94.4%（34/36） |
| `common.error.web` | 96.9%（190/196） | 88.2%（82/93） |

計画 4.5 の値（B2 の関門の実測）と同じだった。

- `gh run list --branch develop --limit 3`: 37010663674（Plan Approval の記録、実行中・1m14s の時点）、37007594106（B2 の記録、success・13m3s）、36972782269（B1 の記録、success・11m49s）。最新の完了した CI は 13 分 3 秒。

### Step 2: テストの実行の準備

- `./gradlew :backend:test --tests 'cherry.mastersmith.user.service.UserAccountServiceTest'`: 20 件通過。
- `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.user.repository.UserRepositoryIT'`: 14 件通過。
- 単位のテストのコマンドは `unit-test-instructions.md` 2.1〜2.4 のとおりで動いた。U3 のテストは組み込みの H2 だけで、colima の設定なしで流した。

### Step 3: SD-4（`ilike ... escape` と SpEL）

- 結果: **HQL のまま受け付けられた**。`UserRepository#countBySearch`・`#findAdminRowsBySearch` を `u.email ilike :#{#pattern.value()} escape '\'`（氏名と OR）で書き、アプリの起動（Spring Data の `@Query` の解析）が通り、件数と行が返った。native の問い合わせへの切り替えはしていない。
- `SqlStatementCounter` が記録した SQL（小文字にした文）に ` ilike ?` と `escape '\'` が入り、検索の値は `?` のまま文に入っていないことを `UserAdminQueriesIT#ilikeQueryIsAccepted` で確かめた（`spring.jpa.show-sql` は使っていない）。
- 投影の `UserAdminRow`（`user.repository`）はこの Step で作った（Q-A、D-1）。

### Step 4〜9: 層ごとの実装とテスト

| Step | 実行したコマンド（名指し） | 結果 |
|---|---|---|
| 5 ドメイン | `:backend:test` の `SearchTextTest`・`ProfileValidationTest`・`LockViewTest`・`UserAdminProblemTypesTest` | 10・8・9・2 件（性質ベース 3 を含む）、すべて通過 |
| 7 DB アクセス | `:backend:integrationTest` の `UserAdminQueriesIT`・`LoginAttemptStateRepositoryIT` | 9 件・10 件（既存 8 ＋ 足した 2）、すべて通過 |
| 9 業務処理 | `:backend:test` の `UserAccountServiceTest`・`LockAdministrationServiceTest`・`UserAdminServiceTest`・`UserAdminProblemTypeCatalogTest` | 28 件（既存 20 ＋ 足した 8）・4・9・1 件、すべて通過 |
| 9 業務処理（結合） | `:backend:integrationTest` の `UserAdminAccountIT` | 3 件通過 |

確かめのために加えて流したもの（計画の Step の外、壊していないことの確かめ）:

- `./gradlew :backend:test`（単体の全体）: 1,338 件、失敗 0（基準の 1,287 件 ＋ U3 の 51 件。`ArchitectureTest` と既存の機能ごとの境界テストを含めて通過）。
- `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.user.*' --tests 'cherry.mastersmith.common.error.*' --tests 'cherry.mastersmith.auth.repository.*'`: 20 クラス・127 件、失敗 0（`USER_NOT_FOUND` の code が起動時の重複の検査に当たらないことを含む）。

カバレッジはこの段の途中では測っていない（計画どおり Step 15 の `verify` で判定する）。

### 計画との差・この段で決めた細部

| ID | 計画の形 | 実際 | 理由 |
|---|---|---|---|
| G-1 | 3.1・Step 1: R1 は作業ブランチの最初のコミット | Plan Approval の記録のコミット 493b4dc は `develop` の上にあり、作業ブランチはそこから作られていた（依頼の時点で済み） | 依頼者の側で先に行われていた。作業ブランチの上の R1 は無い。統合の手順（3.2）の `aidlc/` を squash から外す手順には影響しない |
| G-2 | Step 3: `SqlStatementCounter` の記録した SQL の文で確かめる | 既存の `SqlStatementCounter` は種類（`select users` など）だけを返すため、記録した SQL をそのまま返す `recorded()` をテストの手伝いに足した（`backend/src/test/java/cherry/mastersmith/auth/testsupport/SqlStatementCounter.java`、テストだけの変更） | 文の形（`ilike`・`escape`・`password_hash` の無いこと）を確かめるため。既存の `start`・`stop`・`kind` は変えていない |
| G-3 | 4.1 `LockView.of(LoginAttemptState の値 or 無し, now)` | `LockView.of(LoginAttemptState state, Instant now)`（行が無ければ `null`）と、定数 `LockView.NONE`。record の作り手で「ロック中のときだけ解除の予定の時刻を持つ」「ロック中なら戻せる」を確かめる | 名前の細部 |
| G-4 | 4.1 `UserAccountService#findAdminPage(SearchText, long offset, int limit)` | 同じ署名。`offset` は `limit` の倍数だけを受け（共通のページ送りの読み始めの位置は 20 の倍数）、`PageRequest.of(offset / limit, limit)` で問い合わせに渡す。倍数でない・負・`limit` が 1 未満は `IllegalArgumentException` | Spring Data の `Pageable` で位置を渡すため |
| G-5 | 4.1 `LockAdministrationService#lockViewsOf` | 渡した ID をすべて含む `Map`（順は渡した順、重なりは1つ）。正でない ID は `IllegalArgumentException`（ダミーの行を読まない守り）。空なら問い合わせない | 細部 |
| G-6 | 4.1 `UserAdminService#updateProfile` の戻り値 | `user.service.ProfileUpdateResult` をそのまま返す（`useradmin` に別の結果の型を作らない） | 計画に `useradmin` の側の結果の型の名前が無く、C8 の結果の3つ（Updated・NotFound・Invalid）でそのまま場合を尽くせるため |
| G-7 | Step 9 `UserAdminServiceTest` の「監査の出来事を出さない」 | `UserAdminService` が出来事の知らせ（`ApplicationEventPublisher`）を持たないことを確かめる形にした | B3 の業務処理は出来事を出す部品を持たないため。B4 で出来事を出すときにこのテストは書き換える |
| G-8 | Step 7 `LoginAttemptStateRepositoryIT` の「ダミーの行を返さない」 | 正の ID だけを渡したときにダミーの行が返らないこと（と、空の ID で問い合わせないこと）を確かめた。負の ID を渡さないことは呼び出し側（`LockAdministrationService`）で守り、`LockAdministrationServiceTest` で確かめた | 計画 4.1 のとおり「正の ID だけを渡す」は呼び出し側の約束のため |

- Step 14 の E2E の条件に関わる点（記録だけ）: `UserRepository`・`LoginAttemptStateRepository` にはメソッドを足しただけで、既存のメソッドの本文は変えていない。Step 1〜9 で手を入れた本体は `user.*`・`auth.domain`・`auth.repository`・`auth.service`・`useradmin.*` だけで、`packagesJudgedByTotal` の一覧のパッケージには手を入れていない。
- `useradmin` の本体は JPA のエンティティに依存していない（`UserAdminSummary`・`LockView` の値だけを使う）。境界テストは Step 13 で足す。

### 依頼者に確かめたいこと（B3 の Step 1〜9 の分）

- G-1（R1 が `develop` の上にあること）を記録の扱いとしてこのまま受け入れてよいか。
- G-2（テストの手伝い `SqlStatementCounter` に `recorded()` を足したこと）を受け入れてよいか。

## B3（Step 10〜15、2回目の依頼）

前の回の G-1・G-2 は依頼者の了承を得た（記録に残っていれば足りる）。コミット・`git add` はしていない。Step 16 には進んでいない。`code-summary.md`・`source-manifest.json`・`traceability.json` は B3 までの途中の版として下書きした（Q-B A）。

### Step 10〜13: web・漏えい・構造の実装とテスト

| Step | 実行したコマンド（名指し） | 結果 |
|---|---|---|
| 11 web（単体） | `:backend:test` の `SearchTextConverterTest`・`UserAdminWebTypesTest`・`UserAdminRequestContextResolverTest` | 3・3・5 件、すべて通過 |
| 11 web（結合） | `:backend:integrationTest` の `UserAdminListApiIT`・`UserAdminListQueryCountIT`・`UserAdminProfileApiIT` | 8・3・6 件、すべて通過 |
| 12 漏えい | `:backend:integrationTest` の `UserAdminSecretLeakIT` | 2 件（TRACE・INFO）、通過 |
| 13 構造 | `:backend:test` の `UserAdminBoundaryArchitectureTest`・`ArchitectureTest`・`AuthBoundaryArchitectureTest`（と既存の境界テスト全体 `*BoundaryArchitectureTest`） | 6・5・4 件ほか、すべて通過。既存のテストは変えていない |

- 途中の失敗と直し（どれもテストの側の誤り）: (1) ロックの表示のテストで時計を 1 時間進めたためアクセストークンが期限切れになり 401 → 時計を進めた後にログインし直す形にした。(2) 認可のテストで監査の行が 2 件増えた → 未認証の 401 も既存のアクセスの拒否（`TOKEN_MISSING`）として残る既存の動作だった（G-10）。(3) 404 の本文に利用者 ID が入った → Problem Details の `instance`（要求のパス）で、`title`・`detail` には入らないことを確かめる形にした（G-12）。(4) 追跡の行に `UserRepository#…` が無かった → G-11。
- `useradmin/testsupport` に `UserAdminApi`（要求の手伝い）と `UserAdminFixtures`（利用者の作成は契約 C2、停止は C1 の口の `TestUserSuspension`、ロックの状態の行だけ JDBC の `MERGE`。メールアドレスは `example.com` だけ）を置いた。認証は `auth/testsupport` の `AuthApi`・`AuthApiTestConfig`・`MutableClock`・`SqlStatementCounter` をそのまま使った（計画 8節の D-7）。
- `UserAdminListQueryCountIT` は、認証と認可の入口も同じ要求の中で内部DB を読むため、業務処理に入らずに返る要求（page=0 の 400）の回数を基準にし、差を一覧の問い合わせの回数とした（1ページ 3 回・最後のページより後 1 回・行の数で増えない、ロックの状態は排他なしの1回）。
- `UserAdminSecretLeakIT` は、1つの Spring の文脈の中で `LoggingSystem` で TRACE と既定の INFO を切り替える形（D-10）がそのまま効いた（クラスは分けていない）。値は ASCII の乱数（JSON の書き方に左右されないため）。
- フォーマット: `:backend:spotlessApply` で新しいテストのファイル 8 つを整えた（ほかのファイルは変わっていない）。

### Step 14: 文書とレビューでの確かめ

- `README.md` に「利用者の管理の API（Intent 260930-user-admin の U3）」の節を足した（「管理の画面の「権限が無い」の扱い」の節の後）。
- **伏せ字の経路**: `git diff develop -- backend/src/main` で足したメソッドの署名を洗い出した。`useradmin`・`user`・`auth` の足した口のうち、Bean のメソッドで `String` を受ける・返すのは `UserAdminService#list` の page だけ（record の `toString` を除く）。`SearchTextConverter#convert(String)` は Bean でなく `new` して登録した部品、`ProfileValidation#validate` は Bean でない純粋な関数で、どちらも追跡の対象外。repository の口は `RedactedText`・`ProfileUpdate`・`Pageable`・利用者 ID だけを受ける。
- **page の文字列（U2 の R3）**: page の文字列が追跡に出うるのは `UserAdminController#list` と `UserAdminService#list` の引数だけ。`Paging.parsePage` は `common.paging` の静的なメソッドで追跡の対象の層の外。
- **E2E を流す条件（基盤の設計の R-03）**: `git diff --name-only develop` とまだ追跡していないファイルの一覧（54 件、`source-manifest.json`）は、`frontend/`・`auth.web`・`LoginService`・`TokenRefreshService`・`LogoutService`・`access.web`・`common.security`・`config`・`common.observability`・`common.error` のどれにも当たらない。`LoginAttemptStateRepository`・`UserRepository`・`UserAccountService` は、`git diff develop` の削除の行が 0 行（メソッドを足しただけ）で、既存のメソッドの本文を変えていない。よって **E2E は流さない**。
- **`packagesJudgedByTotal`**: 手を入れた本体のパッケージは `user.domain`・`user.repository`・`user.service`・`auth.domain`・`auth.repository`・`auth.service`・`useradmin.*` で、一覧（9 パッケージ）のどれにも当たらない。

### Step 15: 1コマンドの検査（B3 の統合の前の関門）

colima が動いていることを確かめ、`DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` を渡して `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行した（2026-10-02 22:38 ごろ）。

| 項目 | 値（Step 1 の基準との差） |
|---|---|
| 結果 | BUILD SUCCESSFUL（1回目で通過） |
| 時間 | 6 分 37 秒（壁時計 397 秒。Gradle の表示 6m 36s。基準 6 分 23 秒から +14 秒） |
| 単体（`test`） | 1,355 件（+68）、失敗 0・誤り 0・飛ばし 0 |
| 結合（`integrationTest`） | 620 件（+33）、失敗 0・誤り 0・飛ばし 0（対象DB のテストも SKIPPED なし） |
| 画面（Vitest） | 95 ファイル・801 件、すべて通過 |
| 全体のカバレッジ | 行 98.9%（5883/5951）・分岐 94.6%（2156/2280） |

| パッケージ | 行 | 分岐 |
|---|---|---|
| `useradmin.web` | 100.0%（67/67） | 100.0%（12/12） |
| `useradmin.service` | 100.0%（40/40） | 100.0%（8/8） |
| `useradmin.domain` | 100.0%（2/2） | 分岐なし |
| `user.domain` | 99.6%（238/239） | 97.8%（135/138） |
| `user.repository` | 100.0%（7/7） | 分岐なし（G-15） |
| `user.service` | 99.6%（283/284） | 94.7%（108/114） |
| `auth.domain` | 98.2%（112/114） | 100.0%（38/38） |
| `auth.repository` | 100.0%（39/39） | 100.0%（4/4） |
| `auth.service` | 99.2%（240/242） | 92.1%（70/76） |
| `audit.domain` | 99.5%（214/215） | 97.8%（44/45） |
| `audit.service` | 100.0%（148/148） | 84.4%（27/32） |
| `invitation.repository` | 100.0%（2/2） | 分岐なし |
| `access.domain` | 100.0%（53/53） | 100.0%（29/29） |
| `common.observability` | 97.5%（115/118） | 94.4%（34/36） |
| `common.error.web` | 96.9%（190/196） | 88.2%（82/93） |

- `./gradlew osvScan --rerun-tasks`: BUILD SUCCESSFUL。失敗の条件に当たるもの 0 件、警告 14 件（すべて `vendor/make-you-chic-ui/package-lock.json` の開発用の依存 `brace-expansion@5.0.9`（3 件）・`undici@8.10.0`（11 件））。
- SpotBugs ＋ FindSecBugs（`spotbugsGate`）・Gitleaks は除外を足さずに通過。`backend/config/spotbugs-exclude.xml`・`.gitleaks.toml`・`backend/gradle.lockfile`・`frontend/package-lock.json` に差なし。新しいコードの SpotBugs の指摘は priority 2・3 の警告だけ（`UserAdminService` の `EI_EXPOSE_REP2`、`UserAdminController` の `SPRING_ENDPOINT`、`UserAdminRequestContextResolver` の `CT_CONSTRUCTOR_THROW`・`SERVLET_HEADER_USER_AGENT`）。
- E2E は流していない（Step 14 の条件に当たらない）。`frontend/playwright-report/`・`frontend/test-results/` は作られていない。
- 一時的な失敗は無かった。

### 計画との差（B3 の Step 10〜15）

G-9〜G-15 は `code-summary.md` の 4節の表に書いた（`AdminUserPage` の `toString`、未認証の 401 のアクセスの拒否の行、Spring Data の repository の追跡のロガー、404 の `instance`、送り手の情報の `origin`、氏名と言語で操作した人を読まないこと、`user.repository` が B3 から計測の対象になったこと）。

### 依頼者に確かめたいこと（B3 の Step 10〜15 の分）

- G-10: 未認証の 401 が既存のアクセスの拒否（`TOKEN_MISSING`）として監査に残る動作を、一覧と氏名・言語の認可のテストで固定してよいか（計画の文言は「403 は既存のアクセスの拒否だけ」）。
- G-11: repository の口の追跡を確かめるため、漏えいのテストの中だけ `org.springframework.data.jpa.repository.support` のロガーも TRACE にした形でよいか（`cherry.mastersmith` だけを TRACE にした本番の運用では、Spring Data の repository の口は追跡に出ない）。
- OSV-Scanner の警告 14 件（`vendor/make-you-chic-ui` の開発用の依存）を、関門の基準どおり警告のまま進めてよいか。

## B3 の関門の後の依頼者の決定と統合（2026-10-02）

- G-10（未認証の 401 が既存のアクセスの拒否 `TOKEN_MISSING` として監査に残る動作をテストで固定する）: 受け入れ
- G-11（漏えいのテストの中だけ Spring Data のロガー `org.springframework.data.jpa.repository.support` も TRACE にする）: 受け入れ
- OSV-Scanner の警告 14 件（`vendor/make-you-chic-ui` の開発用の依存）: 関門の基準どおり警告のまま進める
- コミット: C1 8abf353・C2 14e3d30・C3 80fd70c（依頼者の承認を得てオーケストレーターが作った）。`develop` へ squash の1コミットで統合し、`aidlc/` は squash から外して `develop` で記録のコミットにする

## B4（Step 17〜25、1回目の依頼）

作業ブランチ `feature/260930-user-admin-b4`（`develop` の c13e817 から）。コミット・`git add` はしていない。Testing Contract は B3 と同じ `sha256:25c76d4e2f9a91edd5d55fbff38287bcce006bace671ccf3da4e81c3a6393f2a`。**Step 25 の途中で止めた**（下の「止めた理由」）。Step 26 以降には進んでいない。

### Step 17: 作業の場と変更の前の基準

- `develop` の先頭: `c13e817f43a6f8c03a82cda4f76fcb5271124a7e`（作業ブランチの先頭も同じ）。B3 の squash の統合 f6bf385 と記録のコミット c13e817 の差は `aidlc/` の下だけ（`git diff --name-only f6bf385 c13e817` で `aidlc/` の外は 0 件）。アプリのソースに未コミットの変更なし（監査ログの追記だけ。ワークフローの記録として外して判断した）。
- B3 の統合の CI: `gh run list --branch develop` で 37015756225（c13e817、success・9m21s）。
- `frontend/playwright-report/`・`frontend/test-results/` は無かった。`gh pr list --state open` は 0 件（Dependabot の知らせなし）。
- 基準: `develop` のアプリのソースは B3 の Step 15 の関門を通した作業ブランチと同じ（上の差の確かめ）ため、実測し直さず、B3 の Step 15 の実測（6 分 37 秒、単体 1,355 件・結合 620 件、全体 行 98.9%・分岐 94.6%、4.5 のパッケージの値）を B4 の基準とした。

### Step 18: テストの実行の準備

- `./gradlew :backend:test --tests '…GlobalExceptionHandlerTest' --tests '…InvitationServiceTest'`: 7 件・10 件、通過。
- `./gradlew :backend:integrationTest --tests '…InvitationRepositoryIT'`: 5 件、通過。

### Step 19〜24: 判定の部品・E1〜E4・中央の手当てとテスト

| Step | 実行したコマンド（名指し） | 結果 |
|---|---|---|
| 20 判定の部品 | `:backend:test --tests 'cherry.mastersmith.common.persistence.*'` | `RowLockFailuresTest` 10 件・`RowLockUnavailableExceptionTest` 3 件、通過 |
| 22 E1〜E4（単体） | `:backend:test` の `LoginAttemptStateRepositoryTest`・`InvitationLockQueriesImplTest`・`InvitationServiceTest`・`RegistrationServiceTest` | 10・23（3つのメソッド × 型 5 つほか）・10・8 件、通過（既存の2つは変えていない） |
| 22 E1〜E4（結合） | `:backend:integrationTest` の `LoginAttemptStateRepositoryIT`・`InvitationRepositoryIT`・`InvitationConcurrencyIT`・`RegistrationConcurrencyIT` | 13 件（既存 10 ＋ 足した 3）・6 件（既存 5 ＋ 足した 1）・1・4 件、通過（後の2つは変えていない） |
| 24 中央の手当て（単体） | `:backend:test` の `LockFailureSafeTraceInterceptorTest`・`GlobalExceptionHandlerTest` | 6 件・9 件（既存 7 ＋ 足した 2）、通過 |
| 24 既存の結合 | `:backend:integrationTest` の `TraceAspectIT`・`ErrorResponseIT` | 4・7 件、通過（変えていない） |

- 確かめのために加えて流したもの: `./gradlew :backend:test`（単体の全体）1,409 件、失敗 0（B3 の基準 1,355 件 ＋ B4 で足した 54 件。`ArchitectureTest` と既存の機能ごとの境界テストを含めて通過）。
- フォーマット: `:backend:spotlessApply` で新しいファイルを整えた。

### Step 25（途中まで）: 既存の経路の上限切れの漏えいの結合テスト

| テスト | 結果 |
|---|---|
| `AuthLockTimeoutLeakIT`（実在の利用者のログイン E1・ダミーの行 8 つをすべて持ったときの存在しないメールアドレスのログイン・トークンの更新の書き込み、TRACE と INFO） | 2 件、通過 |
| `MePreferencesLockTimeoutLeakIT`（表示の設定の保存の書き込み、TRACE と INFO） | 2 件、通過 |
| `InvitationLockTimeoutLeakIT`（招待 E2・送り直しと取り消し E3・登録の完了 E4、TRACE と INFO） | INFO は通過、**TRACE は失敗**（下の「止めた理由」） |
| 既存の `InvitationSecretLeakIT`（変えていない） | **1 件失敗**（同じ理由） |

- 書き込みの問い合わせの待ちの上限（4.6 で未測定だった値）: **約 2.0 秒**。TRACE の実行のログの時刻で、トークンの更新（`RefreshTokenRepository#revokeIfActive`）が要求の始まりから Hibernate の誤りのログまで 2,015 ミリ秒、表示の設定の保存（`UserRepository#updatePreferences`）が 2,038 ミリ秒（どちらも上限の見積もり）。行の排他の読み取り（3,000 ミリ秒）より短い。書き込みの上限切れの例外は Spring の `CannotAcquireLockException`（原因の連なりに誤りの番号 50200・SQLState HYT00）。
- Hibernate の誤りのログ（Hibernate 7.4 ではロガーの名前が `SqlExceptionHelper` ではなく `org.hibernate.orm.jdbc.error`）は、誤りの番号と SQLState の WARN と、H2 の `SQLException` の文（表の名前と `?` のままの SQL の文）の WARN の2行だった。読み取りの排他・書き込みの問い合わせのどちらでも、排他されていた行の値は入っていなかった（3つのテストの出力の全体の確かめで、見分けやすい値が 0 件）。`security-design.md` 7.3 の前提は書き込みの問い合わせでも成り立った。止める条件（書き込みの問い合わせで値が入る）には当たらない。
- 再現の確かめ（Q-F B）は、まだ行っていない（3つの `*LockTimeoutLeakIT` がすべて通った後に行う手順のため）。

### 止めた理由（承認済みの計画と違う作りが要る）

- **事実**: E2〜E4 を計画どおり Spring Data の独自の断片 `invitation.repository.InvitationLockQueriesImpl` に移すと、断片の実装はアプリのパッケージ（`..repository..`）の Bean になり、`TraceAspect` の追跡の対象になる。追跡のロガーは対象のクラスの名前（`cherry.mastersmith.invitation.repository.InvitationLockQueriesImpl`）のため、運用の TRACE の設定（`cherry.mastersmith` だけを TRACE）で、`findByTokenHashForUpdate(byte[] tokenHash)` の ENTER の行に引数の **招待のトークンのハッシュ値が16進で出る**（Spring の追跡は `byte[]` の中身を文字に直して出す）。移す前は Spring Data の repository の口の追跡のロガーが `org.springframework.data.jpa.repository.support`（B3 の G-11）で、`cherry.mastersmith` だけを TRACE にしたときは出ていなかった。
- **影響**: 新しい `InvitationLockTimeoutLeakIT` の TRACE の場合と、既存の `InvitationSecretLeakIT`（招待のトークンのハッシュ値をログに出さないことを確かめる）が落ちる。今の作りのまま統合すると、既存の決まり（`InvitationSecretLeakIT` が守ってきた「ハッシュ値をログに出さない」）を壊す。
- **計画の制約**: 計画 Step 21 と NFR 設計の2回目のレビューの R-02 は「断片を `invitation.repository` に置き、名前・引数・戻り値（`byte[]`）を今と同じにする」。引数の型を変えずに、この置き場のまま追跡に出さない手は無い。
- **案**（どれも依頼者の決定が要る）:
  - **案 1（推奨）**: 断片のインターフェース `InvitationLockQueries` は `invitation.repository` に残し（`InvitationRepository` が継ぐ・名前・引数・戻り値は同じ）、実装 `InvitationLockQueriesImpl` だけを `TraceAspect` の対象の層の外の用途名の下位パッケージ（例 `invitation.lock`）に置く。`project.md` の学び（秘密は追跡の対象の層の外の用途名の下位パッケージに置く）と同じ形。Spring Data の断片の実装の検出は既定で基本パッケージ（`cherry.mastersmith`）の下を走査するため見つかる見込み（未検証）。差: 計画 4.3 の置き場、カバレッジの表に新しいパッケージ（自動で下限の対象）、`InvitationBoundaryArchitectureTest` は変えずに通るかを確かめる。
  - **案 2**: `TraceAspect` の対象の式から repository の断片の実装（例 `..repository..*Impl`）を外す。`common.observability` には既に手を入れているが、追跡の範囲（NFR10.12）の決まりを変える。
  - **案 3**: E2〜E4 の断片への移し替えをやめ、`@Lock` の問い合わせに戻す。漏えいは Step 23 の中央の手当て（`TraceAspect` と `GlobalExceptionHandler` の両方が排他の失敗の連なりをクラスの名前だけにする）で防げる（E2〜E4 の例外は Spring が `CannotAcquireLockException` などに変えた連なりで、判定に当たる）。差: `security-design.md` 7.2 の「repository の中で受けて値を含まない例外に置き換える」と WARN（`INVITATION_ROW`）が E2〜E4 では出ない。
- **止めた時点の作り**: 断片（`InvitationLockQueries`・`InvitationLockQueriesImpl`）と `InvitationRepository` の変更、そのテスト（`InvitationLockQueriesImplTest`・`InvitationRepositoryIT` に足した1件・`InvitationLockTimeoutLeakIT`）は作業フォルダに残している（計画 Step 21 の E2〜E4 の項目のチェックは付けていない）。決定の後に直し、`InvitationSecretLeakIT` を含めて流し直してから、再現の確かめ（Q-F B）に進む。

### 計画との差・この段で決めた細部（B4）

| ID | 計画の形 | 実際 | 理由 |
|---|---|---|---|
| G-16 | 4.4・Step 25: 別の接続で行を持つ手伝い `RowLockHolder` の置き場は Step 25 で決める | `common/testsupport/RowLockHolder` に1つ置いた（計画 8節の D-7 の候補のうち共通の置き場）。アプリの接続のプールを使わず、`H2SessionWaits` と同じく JDBC で別に接続する | `auth`・`invitation`・`user`・`useradmin` のテストで使い、機能の間でテストの手伝いを使い回さないため。`common/testsupport` には既存の `TestDatabase` などがある |
| G-17 | Step 22: `LoginAttemptStateRepositoryIT` は足すだけ | 既存の `lockTimeout`（上限切れで `DataAccessException`）の期待を `RowLockUnavailableException`（原因なし）に変えた | E1 の直しで投げる例外が変わるため（直しそのものの確かめ）。待つ時間の確かめ（2.5〜9 秒）は変えていない |
| G-18 | 4.3: `InvitationRepository` から3つの宣言を消す | 加えて、3つの宣言だけが使っていた定数 `InvitationRepository.LOCK_TIMEOUT_MILLIS`（文字列 "3000"）を消し、断片の実装に `int` の定数 3000 を置いた | 使う所が無くなったため。値は変えていない |
| G-19 | 4.3: `RowLockUnavailableException` は決まった文だけを持ち、原因をつながない | `RuntimeException(String, Throwable, boolean, boolean)` で原因を null に固定し、抑えた例外も足せないようにした（後から `initCause`・`addSuppressed` で元の例外を付けられない） | 元の例外の連なりが後から付かないようにするため |
| G-20 | 4.3: `LockFailureSafeTraceInterceptor` は `$[exception]` の置き換えと書き出しを上書きする | Spring 7.0.9 の `CustomizableTraceInterceptor#replacePlaceholders` と `AbstractTraceInterceptor#writeToLog(Log, String, Throwable)` を上書きした（版の違いによる包み直しは要らなかった）。排他の失敗のときは `$[exception]` を例外の外側のクラスの名前に置き換えてから元の処理に渡し、書き出しに例外を渡さない（スタックトレースを出さない） | 設定の項目と文言の形を変えないため |
| G-21 | 4.3: `GlobalExceptionHandler` の ERROR に `exceptionClass` を足す | 足す値は例外の外側のクラスの名前（Spring の変換の後なら `CannotAcquireLockException` など）。キーは `code`・`status`・`exceptionClass` の3つで、原因は付けない | 計画の文言どおり。連なりの中の JPA の型は Hibernate の誤りのログ（誤りの番号と SQLState）で分かる |
| G-22 | — | `LoginAttemptStateRepository#lockForUpdate` は `tryLockForUpdate` の結果を `switch` で写す形にした（問い合わせと判定を1か所で共有。`Busy` なら `RowLockUnavailableException`）。WARN は `tryLockForUpdate` の中で1回だけ出る | 計画の「同じ問い合わせと判定を共有する」の具体 |

### コミットの区切り（計画 3.3 の C4 に入るもの）

C4（既存の経路の上限切れの漏えいの直しと、それを再現するテスト。`project.md` の Mandated のため同じコミット）に入る見込みのファイル（止めた時点）:

- 本体: `backend/src/main/java/cherry/mastersmith/common/persistence/`（`package-info.java`・`RowLockFailures.java`・`RowLockUnavailableException.java`・`RowLockAttempt.java`）、`auth/repository/LoginAttemptStateRepository.java`、`invitation/repository/InvitationRepository.java`・`InvitationLockQueries.java`・`InvitationLockQueriesImpl.java`（案の決定で置き場が変わりうる）、`common/observability/LockFailureSafeTraceInterceptor.java`・`TraceAspect.java`、`common/error/web/GlobalExceptionHandler.java`
- テスト: `common/persistence/RowLockFailuresTest.java`・`RowLockUnavailableExceptionTest.java`、`common/testsupport/RowLockHolder.java`、`auth/repository/LoginAttemptStateRepositoryTest.java`・`LoginAttemptStateRepositoryIT.java`、`invitation/repository/InvitationLockQueriesImplTest.java`・`InvitationRepositoryIT.java`、`common/observability/LockFailureSafeTraceInterceptorTest.java`、`common/error/web/GlobalExceptionHandlerTest.java`、`auth/web/AuthLockTimeoutLeakIT.java`、`invitation/web/InvitationLockTimeoutLeakIT.java`、`user/web/MePreferencesLockTimeoutLeakIT.java`
- `backend/build.gradle.kts` の `packagesJudgedByTotal` から `common.observability`・`common.error.web` を外すのは計画どおり Step 38（C8）。2つのパッケージにはこの区切りで手を入れたため、外す作業は B4 の中で必ず行う。

### 依頼者に確かめたいこと（B4 の Step 17〜25 の分）

- **止めた理由の案 1〜3 のどれにするか**（推奨は案 1）。
- G-16（`RowLockHolder` を `common/testsupport` に1つ置く）を受け入れてよいか。
- G-17（既存の `LoginAttemptStateRepositoryIT#lockTimeout` の期待の例外を変えた）を受け入れてよいか。

## B4（Step 21・25 の続き、2回目の依頼）

### 依頼者の決定（1回目の報告を受けて）

- 止めた理由への答え: **案 1**（インターフェース `InvitationLockQueries` は `invitation.repository` に残し、実装 `InvitationLockQueriesImpl` だけを追跡の対象の外の用途名の下位パッケージ（例 `invitation.lock`）に移す）。計画 Step 21 と NFR 設計の2回目のレビューの R-02 の「置き場は `invitation.repository`」との差になる。
- G-16（`RowLockHolder` を `common/testsupport` に1つ置く）・G-17（既存の `LoginAttemptStateRepositoryIT#lockTimeout` の期待の例外を変えた）: 受け入れ。

### 案 1 を試した結果（止めた）

- 実装だけを `invitation.lock` に移すと、**アプリが起動しない**（`InvitationRepositoryIT`・`InvitationConcurrencyIT`・`RegistrationConcurrencyIT`・`InvitationLockTimeoutLeakIT`・`InvitationSecretLeakIT` がすべて文脈の読み込みで失敗）。Spring Data が断片の実装を見つけられず、`InvitationLockQueries` の3つのメソッドを名前からの問い合わせとして作ろうとして失敗する（`No property 'forUpdate' found for type 'InvitationState'`）。
- 原因（ソースで確かめた）: spring-data-commons 4.1.1 の `DefaultImplementationLookupConfiguration` は、断片の実装を探す基本のパッケージを **断片のインターフェースのパッケージ**（`ClassUtils.getPackageName(interfaceName)`）とし、実装のパッケージがそれで始まるものだけを候補にする。インターフェースが `invitation.repository` にある限り、実装は `invitation.repository` かその下（どれも `..repository..` で追跡の対象）にしか置けない。設定を変えずに案 1 の形は作れないため、依頼の条件どおり止めた。
- 境界テスト（`*BoundaryArchitectureTest` 全部・`ArchitectureTest`）と `invitation.*` の単体テストは、この形でも変えずに通った（`InvitationLockQueriesImplTest` 23 件を含む）。

### 試しで確かめた代わりの形（案 1′、依頼者の確かめが要る）

- インターフェース `InvitationLockQueries` も実装と同じ `invitation.lock` に置き、`InvitationRepository`（`invitation.repository`）がそれを継ぐ。`InvitationRepository` の3つのメソッドの名前・引数・戻り値は今と同じで、`InvitationService`・`RegistrationService` と既存の単体テストは変えない。設定は変えない。
- この形で流した結果: `InvitationRepositoryIT` 6 件・`InvitationLockTimeoutLeakIT` 2 件（TRACE と INFO）・`InvitationSecretLeakIT` 2 件、すべて通過。断片の実装の追跡の行は出ない（`InvitationLockTimeoutLeakIT` の TRACE で `InvitationLockQueriesImpl#` が 0 件であることを確かめる形にした）。
- 案 1 との差: インターフェースの置き場が `invitation.repository` から `invitation.lock` に変わる（`InvitationRepository` の口そのものは同じ）。
- 作業フォルダは **案 1′ の形のまま** にしてある（起動しない形のまま残さないため）。案 1′ を採らないときは戻す。案 1 の形（実装だけを移した状態）の写しはリポジトリの外の一時の場所にあり、計画どおり `invitation.repository` に戻す形も1回目の報告の時点の作りで分かっている。
- Step 21 の E2〜E4 の項目と Step 25 のチェックは付けていない。再現の確かめ（Q-F B）は置き場が決まってから行う。

### 依頼者に確かめたいこと

- 案 1′（インターフェースも `invitation.lock` に置く）でよいか。ほかには、設定で Spring Data の断片の探す場所を変える形があるが、試していない（依頼の条件の「設定を変える必要が出たときは止める」に当たる）。

## B4（Step 21・25 の仕上げ、3回目の依頼）

### 依頼者の決定: 案 1′

- 口 `InvitationLockQueries` も実装 `InvitationLockQueriesImpl` も `invitation.lock`（追跡の対象の層の外の用途名の下位パッケージ）に置き、`InvitationRepository`（`invitation.repository`）がそれを継ぐ。設定は変えない。
- **計画との差（G-23）**: 計画 Step 21・4.3 と NFR 設計の2回目のレビューの R-02 は、断片の置き場を `invitation.repository` としていた。実際は `invitation.lock`。`InvitationRepository` の口（3つのメソッドの名前・引数・戻り値）は変わらず、`InvitationService`・`RegistrationService` と既存の単体テストは変えていない。理由: 実装が `..repository..` にあると `TraceAspect` の追跡の対象になり、`findByTokenHashForUpdate(byte[])` の引数の招待のトークンのハッシュ値が TRACE のログに出るため（1回目の報告）。Spring Data は断片の実装を口のパッケージとその下からしか探さないため、口も同じ下位パッケージに置いた（2回目の報告）。
- 単体テスト `InvitationLockQueriesImplTest` も `invitation/lock` に移した。カバレッジでは `invitation.lock` が新しいパッケージになり、自動でパッケージごとの下限の対象になる（計画 4.5 の表の `invitation.repository` の行の代わり。判定は Step 41 の verify）。
- 追跡の対象の外であることの確かめ: `InvitationLockTimeoutLeakIT` の TRACE の場合に、`InvitationLockQueriesImpl#` の追跡の行が 0 件で、サービスの EXCEPTION の行（`InvitationService#invite`・`#resend`・`#cancel`・`RegistrationService#complete`）があることを確かめる。
- リポジトリの外に置いた写し（2回目の試しの前の形）は消した。

### 流したテスト

| コマンド（名指し） | 結果 |
|---|---|
| `:backend:test --tests 'cherry.mastersmith.invitation.*' --tests '…ArchitectureTest' --tests '…*BoundaryArchitectureTest'` | 25 クラス・195 件、失敗 0。`ArchitectureTest` 5 件と境界テスト 10 クラス（`InvitationBoundaryArchitectureTest` 6 件を含む）は変えずに通過 |
| `:backend:integrationTest --tests 'cherry.mastersmith.invitation.*'` | 19 クラス・63 件、失敗 0（`InvitationConcurrencyIT` 1 件・`RegistrationConcurrencyIT` 4 件・`InvitationRepositoryIT` 6 件・`InvitationLockTimeoutLeakIT` 2 件・既存の `InvitationSecretLeakIT` 2 件を含む） |
| 再現の確かめの後: `:backend:integrationTest` の3つの `*LockTimeoutLeakIT`・`LoginAttemptStateRepositoryIT`・`TraceAspectIT`・`ErrorResponseIT` | 34 件（入れ子のクラスを含む）、失敗 0 |
| 再現の確かめの後: `./gradlew :backend:test`（単体の全体） | 1,409 件、失敗 0 |

### Step 25: 再現の確かめ（依頼者の決定 Q-F B）

- 一時的に戻した直しの本体:
  - `GlobalExceptionHandler`・`TraceAspect` を `develop` の版に戻した（中央の手当てを外す）。
  - `LoginAttemptStateRepository`・`InvitationLockQueriesImpl` は、排他の失敗の判定を常に「排他の失敗でない」として元の例外をそのまま外へ出す形にした（E1〜E4 の受けを外す）。断片への移し替えそのもの（`@Lock` から EntityManager へ）は戻していない。受けを外せば、外へ出る例外の連なりは移す前と同じ形になるため。
- 結果: 3つの `*LockTimeoutLeakIT` の 6 件すべてが落ちた（TRACE と INFO の両方）。

| テスト | 落ちた件数 | 最初に見つかった値の種類 | `MVStoreException` の文を含む出力の行 | その行に入っていた値の種類 |
|---|---|---|---|---|
| `AuthLockTimeoutLeakIT` | 2 / 2 | 解除の予定の時刻 | 42 行（ERROR と TRACE） | 解除の予定の時刻、リフレッシュトークンのハッシュ値（16進のバイト列） |
| `InvitationLockTimeoutLeakIT` | 2 / 2 | メールアドレス | 48 行（ERROR と TRACE） | メールアドレス、招待のトークンのハッシュ値（16進のバイト列） |
| `MePreferencesLockTimeoutLeakIT` | 2 / 2 | メールアドレス | 12 行（ERROR と TRACE） | メールアドレス、パスワードのハッシュ値 |

- 値そのものは記録していない。既定の INFO でも `GlobalExceptionHandler` の ERROR の行に値が出ていた（`security-design.md` 11節の R4 のとおり）。
- 戻した後: 4つのファイルを写しから戻し、写しを消した。`git diff --stat -- backend` が確かめの前と同じ（`LoginAttemptStateRepository` 56 行・`GlobalExceptionHandler` 13 行・`TraceAspect` 5 行の差ほか）。一時的な戻しの印が残っていないこと（`grep` で 0 件）も確かめ、上の表のとおり流し直して通った。
- `git diff` の全体の比べでは監査ログ（`aidlc/…/audit/…md`）の追記だけが変わっていた。ワークフローの記録として外して判断した。

### 計画のチェック

- Step 21 の E2〜E4 の項目と Step 25 のすべての項目にチェックを付けた。Step 17〜25 が終わった。

### コミットの区切り（C4）の追記

- 1回目の一覧のうち `invitation/repository/InvitationLockQueries.java`・`InvitationLockQueriesImpl.java` は `invitation/lock/` の `InvitationLockQueries.java`・`InvitationLockQueriesImpl.java`・`package-info.java` に、`invitation/repository/InvitationLockQueriesImplTest.java` は `invitation/lock/InvitationLockQueriesImplTest.java` に読み替える。

## B4（Step 26〜32、4回目の依頼）

作業ブランチ `feature/260930-user-admin-b4`（Step 17〜25 の変更はコミットされずに残したまま、その上に足した）。コミット・`git add` はしていない。Testing Contract は `sha256:25c76d4e2f9a91edd5d55fbff38287bcce006bace671ccf3da4e81c3a6393f2a`（test-after。ドメイン → DB アクセス → 業務処理の順に、層ごとに実装 → その層のテスト → 通ってから次の層）。Step 33 以降には進んでいない。

### Step 16 のチェックを後から付けたこと

- 計画の Step 16 の5つのチェックボックスが付いていなかった（付け漏れを依頼者が見つけた）。B3 の記録・C1〜C3 のコミット（8abf353・14e3d30・80fd70c）・`develop` への squash の統合（f6bf385）と記録のコミット（c13e817）・ブランチの削除はオーケストレーターが依頼者の承認を得て済ませており、`develop`（c13e817）の CI も success（9 分 21 秒）だったため、B4 の4回目の依頼の中で Step 16 の5つに後からチェックを付けた（チェックの印だけを変えた）。

### Step 26〜27: ドメイン（拒否の判定と監査）

- `useradmin.domain` に `AdminOperation`・`RejectionReason`（宣言の並びが判定の順）・`OperationFacts`・`RejectionPolicy`・`UserAdminAuditFailure`・`UserAdminAuditEvent` を作り、`UserAdminProblemTypes` に 409 の5つ（`USER_ADMIN_SELF_OPERATION`・`USER_ADMIN_TARGET_SUSPENDED`・`USER_ADMIN_NO_CHANGE`・`USER_ADMIN_LAST_ADMIN`・`USER_ADMIN_BUSY`）を足した。
- `audit.domain` に種類5つ（`USER_ADMIN_GRANTED`・`USER_ADMIN_REVOKED`・`USER_SUSPENDED`・`USER_RESUMED`・`LOGIN_FAILURES_RESET`）と理由4つ（`SELF_OPERATION`・`TARGET_SUSPENDED`・`NO_CHANGE`・`LAST_ACTIVE_ADMIN`）を足し、`AuditEventFactory#from(UserAdminAuditEvent)` を場合を尽くす `switch` で書いた。名前は最長 20 文字（`LOGIN_FAILURES_RESET`）・17 文字（`LAST_ACTIVE_ADMIN`）で 32 文字以内。

| Step | 実行したコマンド（名指し） | 結果 |
|---|---|---|
| 27 | `:backend:test --tests 'cherry.mastersmith.useradmin.domain.*' --tests '…audit.domain.AuditEventFactoryTest' --tests '…audit.domain.AuditEventTest'` | `RejectionPolicyTest` 15 件（性質ベース 3 を含む）・`UserAdminAuditEventTest` 8 件・`UserAdminProblemTypesTest` 3 件（B3 の 2 件を直して 1 件足した）・`AuditEventFactoryTest` 50 件（既存に 13 件足した）・`AuditEventTest` 6 件（変えずに通過）、すべて通過 |

- 性質ベースのテスト（jqwik）は、返す理由がその操作に当たり条件が成り立つこと、返した理由より前の当たりうる理由の条件はどれも偽であること、どれも成り立たなければ拒否しないことの3つ。事実の組は 5 × 2⁶ = 320 通りで、jqwik の既定（1,000 回まで）の中で尽くされる。失敗時の種は既存の `junit-platform.properties` の設定で報告に出る。

### Step 28〜29: DB アクセス（排他と印）

- `user.repository.UserRowLockRepository`（EntityManager を直接使う）: `lockAdminRowsAndTarget(long)`（`select u.userId from User u where u.adminFlag = true or u.userId = :target order by u.userId`、`PESSIMISTIC_WRITE`、上限 3000）と `lockUserRow(long)`。どちらも利用者 ID だけを読み、上限切れ・行き詰まりを本体の中で受けて `RowLockFailures.warn`（`ADMIN_ROWS`・`USER_ROW`）の後に `RowLockAttempt.Busy` を返す。
- `UserRepository` に `findAdminRow(long)`（投影）・`findActiveAdminIds()`（印あり・停止なし、ID の昇順）・`updateAdminFlag(long, boolean)`（`@Modifying(clearAutomatically = true, flushAutomatically = true)`、1列だけ）を足した。既存のメソッドの本文は変えていない。

| Step | 実行したコマンド（名指し） | 結果 |
|---|---|---|
| 29（単体） | `:backend:test --tests '…user.repository.UserRowLockRepositoryTest'` | 9 件、通過 |
| 29（結合） | `:backend:integrationTest --tests '…user.repository.UserRowLockRepositoryIT'` | 5 件、通過 |
| 29（結合） | `:backend:integrationTest --tests '…user.repository.UserAdminQueriesIT'` | 12 件（B3 の 9 件 ＋ 足した 3 件）、通過 |

- 利用者 ID の昇順の排他: 入れた順（ID の順）と登録した日時の向きを逆にしたデータで、別の接続が途中の管理者の行（admin2）を持つ間、それより小さい ID の行（admin1・対象）は排他され、大きい ID の行（admin3・管理者でない行）はまだ排他されていないことを、別の接続から上限 50 ミリ秒の `FOR UPDATE` を当てて確かめた。待ちに入ったことは `H2SessionWaits` で確かめた。
- 上限切れ: 別の接続が行を持つと `Busy`、かかった時間は 3000〜9000 ミリ秒の範囲で確かめた（下限 3000 を通った）。
- 実行計画: Hibernate が出した排他の文（`... order by ... for update wait 3`）を `SqlStatementCounter` で取り、そのまま `EXPLAIN` に当てて `PRIMARY_KEY` と `index sorted` が出ることを確かめた（`reliability-design.md` 4節）。

### Step 30: 業務処理（5つの操作）

- `user.service`: `AdminRowsLock`（`Locked(Optional<UserAdminSummary> target, Set<Long> activeAdminIds)`・`Busy`）、`UserRowLock`（`Locked(Optional<UserAdminSummary>)`・`Busy`）と、`UserAccountService` の `lockAdminRowsInIdOrder`（MANDATORY。排他の後に `findAdminRow` と `findActiveAdminIds` を別の問い合わせで読む）・`lockUserRow`（MANDATORY）・`findAdminSummary`（`readOnly`、呼び出し元に入る）・`setAdmin`（MANDATORY、0 行なら `IllegalStateException`）。コンストラクターに `UserRowLockRepository` を足した。
- `auth.service`: `LoginFailureResetPreparation`（`Ready`・`NothingToReset`・`Busy`）、`LoginAttemptBarrier`・`NoOpLoginAttemptBarrier`、`LockAdministrationService#prepareFailureReset`（MANDATORY）・`#completeFailureReset`（MANDATORY、既存の `update(id, 0, null)` の1回、0 行なら `IllegalStateException`、行を作らない）、`LoginService` の待ち合わせの口（実在の利用者の行を `lockForUpdate` で得た直後だけ。ダミーの行・行が無いときは呼ばない）。`LockAdministrationService`・`LoginService` のコンストラクターに待ち合わせの口を足した。
- `useradmin.service`: `OperationResult`（`Done`・`Rejected(reason)`・`OperatorNotAdmin`・`Busy`）、`UserAdminBarrier`・`NoOpUserAdminBarrier`、`UserAdminService` の5つの操作。コンストラクターに `RefreshTokenRevocationService`・`UserAdminBarrier`・`ApplicationEventPublisher`・`Clock` を足した。
- `audit.service.AuditEventListener#onUserAdminAuditEvent`（`AFTER_COMMIT`・`fallbackExecution = true`、既存と同じ形。組み立ての失敗のときの ERROR に操作した人と対象の利用者 ID を載せ、メールアドレスは持たない）。
- **排他の前に書き込みが無いこと（`reliability-design.md` 5.3 の終わり）**: 流れで確かめた。印を付ける・外す・止めるは `lockAdminRowsInIdOrder` が最初の DB アクセス、停止を解くは `lockUserRow` が最初、失敗回数を戻すは `findAdminSummary(対象)`（読むだけ）の後に `prepareFailureReset` が最初の排他。どの操作も排他より前に書かない。Busy のときは `status.setRollbackOnly()` を先に付け、その後に DB に触れずに `Busy` を返す（5つとも単体テストで確かめた）。
- **U1 R-03**: `useradmin` は排他の結果も要約も投影の値（`UserAdminSummary`）と ID だけで持ち、JPA のエンティティを持たない。止める操作は `setSuspended(対象, true)` の後に `revokeAllRefreshTokens(対象)` を呼ぶだけで、C1 の口の後に先に読んだ値で書く処理は無い（監査の出来事は ID と区分だけ）。結合テスト（`UserAdminOperationsIT#suspendKeepsOtherColumns`）で、止めた後に印・氏名・言語が変わらず、停止とトークンの無効化が確定していることを確かめた。

### Step 31: 業務処理のテスト（単体）

| 実行したコマンド（名指し） | 結果 |
|---|---|
| `:backend:test --tests '…useradmin.service.UserAdminServiceTest' --tests '…user.service.UserAccountServiceTest' --tests '…auth.service.LockAdministrationServiceTest' --tests '…auth.service.LoginServiceTest' --tests '…audit.service.AuditEventListenerTest'` | 34 件（B3 の 9 件 ＋ 25 件）・34 件（B3 の 28 件 ＋ 6 件）・10 件（B3 の 4 件 ＋ 6 件）・20 件（既存 18 件 ＋ 2 件）・21 件（既存 19 件 ＋ 2 件）、すべて通過 |

- `UserAdminServiceTest` の B4 の分は、`useradmin/testsupport/RecordingTransactionManager`（`setRollbackOnly()` の回数・確定・巻き戻しを記録する偽の管理）を `TransactionTemplate` に渡し、5つの操作のそれぞれで、口が Busy を返すと印が1回付き、巻き戻しが1回・確定が 0 回で、待ち合わせの口・出来事の知らせ・C1 の口に触れず、`UserAccountService`・`LockAdministrationService` は排他の口（失敗回数を戻すは対象の読み取りと1段目）だけを呼んだことを `verifyNoMoreInteractions` で確かめた（R-02）。

### Step 32: 業務処理のテスト（結合）

| 実行したコマンド（名指し） | 結果 |
|---|---|
| `:backend:integrationTest --tests '…useradmin.service.UserAdminOperationsIT'` | 5 件、通過 |
| `:backend:integrationTest --tests '…useradmin.service.UserAdminConcurrencyIT'` | 7 件、通過 |
| `:backend:integrationTest --tests '…useradmin.service.ResetLoginConcurrencyIT' --tests '…useradmin.service.SuspendWhileLoginIT'` | 2 件・1 件、通過 |
| `:backend:integrationTest --tests '…user.service.UserAdminLockPortsIT' --tests '…auth.service.FailureResetPortIT'` | 3 件・3 件、通過 |

- 同時の重なりは、待ち合わせの口（`useradmin/testsupport/TestUserAdminBarrier`・`auth/testsupport/TestLoginAttemptBarrier`）で1つ目を止め、2つ目が行の排他の待ちに入ったこと（H2 のセッションの一覧で `from users ... for update`・`from login_attempt_states ... for update` が実行中）を確かめてから1つ目を進めた。止める時間の上限は 2000 ミリ秒（3000 ミリ秒より短い）。各テストで「2つ目は1つ目を続けた時点でまだ待っていた（`secondDoneAtRelease` が偽）」ことも確かめた。sleep は使っていない。
- `UserAdminConcurrencyIT`: 互いの印を外す（AC2.1.6）・一方が外し他方が止める（AC2.1.12）・互いに止める（AC3.1.5）で有効な管理者がちょうど1人、監査が成功1行・失敗1行（`LAST_ACTIVE_ADMIN`）。待つ間に印が付いた C が数えに入る（確かめ 1b の本番版）。待つ間に操作した人の印が外れたときの印の操作・停止を解く・失敗回数を戻すが `OperatorNotAdmin`・監査 `NOT_ADMIN`・状態が変わらない（NFR1.4）。
- `ResetLoginConcurrencyIT`（AC4.1.5、失敗回数 4、しきい値は既定 5）: 戻す側が先なら、ログインは 0 から数えて失敗回数 1・ロックなし。ログインが先なら、ログインでロックした後に戻して 0・ロックなし。どちらも 5xx にならない（ログインの失敗は `AUTHENTICATION_FAILED` の業務の例外だけ）。
- `SuspendWhileLoginIT`（確かめ 3 の本番版）: 止める操作を数える直前で止めている間に、同じ利用者のログイン（照合・ロックの状態の行の排他・トークンの追記）が待たずに終わった。**決めた側の動作（M8 B）**: そのログインのトークンは止める操作の確定より前に確定しているため、止める操作のまとめての無効化で無効になった（リフレッシュトークン 1 件・未無効 0 件）。
- `UserAdminOperationsIT`（FS の 2.10、業務処理を直接呼ぶ）: 操作した人がロック中の管理者なら受け付ける、停止中の管理者なら `LAST_ACTIVE_ADMIN` で拒否し状態が変わらない（印を外す・止めるの両方、AC2.1.11・AC3.1.9）。止める操作の途中（`revokeAllRefreshTokens`）で失敗させると、停止もトークンも戻る（NFR9.5）。止めた後に印・氏名・言語が変わらない（U1 R-03）。
- `UserAdminLockPortsIT`・`FailureResetPortIT`（`reliability-design.md` 5.3 の3行目）: テストの `TransactionTemplate` で先に1行を書き換えてから排他の口を呼び、別の接続が行を持つため Busy を受けて `setRollbackOnly()` で返すと、書き換えが残らず例外も出ない。`completeFailureReset` は行の無い利用者に行を作らない。

### 確かめのために加えて流したもの（計画の Step の外、壊していないことの確かめ）

- `./gradlew :backend:test`（単体の全体）: 1,501 件、失敗 0（Step 25 の時点の 1,409 件 ＋ 92 件。`ArchitectureTest` 5 件・`UserAdminBoundaryArchitectureTest` 6 件ほか既存の境界テストを変えずに含めて通過）。
- colima の設定（`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`）を渡して `caffeinate -i ./gradlew :backend:integrationTest`（結合の全体）: 659 件、失敗 0・飛ばし 0（約 6 分 19 秒。`LoginConcurrencyIT` 7 件・既存の `UserAdminSecretLeakIT` 2 件・`AuditSecretLeakIT` 3 件を含む）。
- `./gradlew :backend:spotlessCheck :backend:spotbugsGate`: 通過。新しいコードの SpotBugs の指摘は priority 2 の `EI_EXPOSE_REP2`（`UserRowLockRepository`・`UserAdminService` のコンストラクター。既存の service と同じ種類）だけ。`backend/config/spotbugs-exclude.xml` は変えていない。
- カバレッジはこの段の途中では測っていない（計画どおり Step 41 の `verify` で判定する）。
- フォーマット: `:backend:spotlessApply` で新しいファイルを整えた。

### 計画との差・この段で決めた細部（B4 の Step 26〜32）

| ID | 計画の形 | 実際 | 理由 |
|---|---|---|---|
| G-24 | 4.3 `UserAdminProblemTypes` に 409 の5つ | 加えて、業務の拒否の理由から問題の種類を1対1で引く `UserAdminProblemTypes.of(RejectionReason)` を置いた（`USER_ADMIN_BUSY` は理由の外のため含まない） | Step 33 の controller の写しと、1つの理由に1つの code の確かめを1か所にするため |
| G-25 | 4.3 `RejectionPolicy#decide(operation, facts)` | 戻り値は `Optional<RejectionReason>`（空は拒否しない）。操作ごとに当たりうるかを返す `applies(operation, reason)` も公開し、性質ベースのテストで使った。`OperationFacts.targetMissing()` を置いた | 名前の細部 |
| G-26 | 4.3 `UserRowLockRepository` の2つの口 | `lockAdminRowsAndTarget` は排他した ID の一覧（昇順）を `RowLockAttempt<List<Long>>` で、`lockUserRow` は対象がいたかを `RowLockAttempt<Boolean>` で返す | 名前の細部。値は ID だけ |
| G-27 | 4.3 `AdminRowsLock`・`UserRowLock` | `Locked` の対象の要約は `Optional<UserAdminSummary>`、有効な管理者は `Set<Long>`（変えられない写し） | entities.md の「要約または無し」の具体 |
| G-28 | 4.3 `prepareFailureReset`（排他の直後に待ち合わせの口） | 行を排他できたとき（行があるとき）だけ待ち合わせの口を通る（行が無いときは排他した行が無いため通らない。`LoginService` の「行が無い場合は呼ばない」と同じ扱い）。戻せるかは `LockView.of(行, now).resettable()` で判定し、一覧の表示と同じ定義にした（R-05）。正でない ID は `IllegalArgumentException` | 計画の「LockView と失敗回数を戻す1段目で同じ定義にする」の具体 |
| G-29 | 4.3 `UserAdminService` の5つの操作 | 公開の口は `grantAdmin`・`revokeAdmin`・`suspend`・`resume`・`resetLoginFailures` で、どれも `(long actorUserId, RequestOrigin origin, long targetUserId)`（`InvitationService` の操作と同じ形）。B3 の `UserAdminServiceTest#noAuditEvents`（出来事の知らせを持たないことの確かめ、G-7）は、一覧と氏名・言語の変更が出来事を出さないことの確かめに書き換えた | 名前の細部。G-7 で予告したとおり |
| G-30 | 4.3「`leavesNoActiveAdmin` は対象を除いた有効な管理者が空か」 | 「対象が有効な管理者に含まれ、かつ集合が対象だけ」とした（BR3.2 の文言どおり） | 対象が有効な管理者でないときに「空」で拒否しないため |
| G-31 | — | `AuditEventFactory.resultOf(AuditEventType)` は、足した5つの種類も `PASSWORD_CHANGED` と同じく種類から結果を決められない（呼ぶと `IllegalArgumentException`）とした。`AuditEventListener` の網羅の `switch`（DSL の種類か）に5つを足した | 成功も失敗も同じ種類で、結果は出来事が持つため |
| G-32 | 4.4 `useradmin/testsupport` の手伝い | B3 の `UserAdminFixtures` に、すべての管理者の印を外す（契約 C8 の `setAdmin` で。`users` を JDBC で書き換えない）・有効な管理者の ID・利用者とロックの状態の列・未無効のリフレッシュトークンの数を読む手伝いを足した。内部DB をクラスで共有する結合テストは、各テストの初めに管理者の印をすべて外し、そのテストで作った管理者だけを数える | 最後の有効な管理者の判定を、ほかのテストの管理者に左右されないようにするため |
| G-33 | 6節・計画 7 の待ち合わせの確かめ（H2 のセッションの一覧） | セッションの一覧の問い合わせは、絞り込みを引数で渡し、自分のセッションを除く形にした | 最初の版で絞り込みを文の中に書いたところ、問い合わせ自身の文（`... LIKE '%from users%for update%'`）が絞り込みに当たり、2つ目が待ちに入る前に1つ目を進めてしまっていた（`secondDoneAtRelease` の確かめで見つかった）。下の「依頼者に確かめたいこと」の1つ目 |
| G-34 | Step 32「`revokeAllRefreshTokens` で例外を起こす差し替え」 | Mockito の `@MockitoSpyBean` ではなく、テストの中の `@Primary` の部分クラス（`FailingRevocations`、指定した利用者だけ失敗させる）にした。失敗させる利用者はトランザクションの代理を通して本体に届くようメソッドで渡す | spy を使った最初の版で、ほかのテストの止める操作が `null` を返す・`MANDATORY` の誤りになるなど不安定だったため。既存のテストの差し替えの形（`@Primary` の部品）にそろえた |
| G-35 | Step 32 の失敗回数を戻す確かめ直し | ログインの待ち合わせの口（`TestLoginAttemptBarrier`）で失敗回数を戻す操作をロックの状態の行の排他の直後に止め、その間に印を外す操作を最後まで進めた（2つは同じ行を取り合わないため、2つ目は待たずに終わる。`secondDoneAtRelease` が真） | 失敗回数を戻す操作は利用者の行を排他しないため、確かめ直しの隙は排他の待ちではなく「判定の前に止めている間」に作るのが確実なため |
| G-36 | Step 32 の停止を解く確かめ直し | 対象を停止中の管理者にし、1つ目（印を外す）の管理者の行の排他に対象の行が入るようにして、2つ目（停止を解く）を対象の行で待たせた | 停止を解くは対象の行だけを排他するため、待ちを作るには1つ目が対象の行を持つ必要があるため（停止中の管理者の行も管理者の行の排他に含まれる。BR3.1） |
| G-37 | Step 32 のログインとの重なり | ログインは HTTP ではなく `LoginService#login` を直接呼んだ（`ResetLoginConcurrencyIT`・`SuspendWhileLoginIT`） | 業務処理の層の結合テストとして、待ち合わせの口と同じスレッドの扱いをはっきりさせるため。API の入口の確かめは Step 34 |

### 計画のチェック

- Step 26〜32 のすべての項目にチェックを付けた（チェックの印だけを変えた）。Step 33 以降は手を付けていない。

### コミットの区切り（計画 3.3）

- C5（拒否の判定と監査のドメイン）: `useradmin/domain/` の `AdminOperation.java`・`RejectionReason.java`・`OperationFacts.java`・`RejectionPolicy.java`・`UserAdminAuditFailure.java`・`UserAdminAuditEvent.java`・`UserAdminProblemTypes.java`、`audit/domain/` の `AuditEventType.java`・`AuditFailureReason.java`・`AuditEventFactory.java`。テストは `useradmin/domain/` の `RejectionPolicyTest.java`・`UserAdminAuditEventTest.java`・`UserAdminProblemTypesTest.java`、`audit/domain/AuditEventFactoryTest.java`。
- C6（排他と5つの操作）: `user/repository/` の `UserRowLockRepository.java`・`UserRepository.java`、`user/service/` の `AdminRowsLock.java`・`UserRowLock.java`・`UserAccountService.java`、`auth/service/` の `LoginFailureResetPreparation.java`・`LoginAttemptBarrier.java`・`NoOpLoginAttemptBarrier.java`・`LockAdministrationService.java`・`LoginService.java`、`useradmin/service/` の `OperationResult.java`・`UserAdminBarrier.java`・`NoOpUserAdminBarrier.java`・`UserAdminService.java`、`audit/service/AuditEventListener.java`。テストは `user/repository/` の `UserRowLockRepositoryTest.java`・`UserRowLockRepositoryIT.java`・`UserAdminQueriesIT.java`、`user/service/` の `UserAccountServiceTest.java`・`UserAdminLockPortsIT.java`、`auth/service/` の `LockAdministrationServiceTest.java`・`LoginServiceTest.java`・`FailureResetPortIT.java`、`auth/testsupport/TestLoginAttemptBarrier.java`、`audit/service/AuditEventListenerTest.java`、`useradmin/service/` の `UserAdminServiceTest.java`・`UserAdminOperationsIT.java`・`UserAdminConcurrencyIT.java`・`ResetLoginConcurrencyIT.java`・`SuspendWhileLoginIT.java`、`useradmin/testsupport/` の `RecordingTransactionManager.java`・`TestUserAdminBarrier.java`・`UserAdminFixtures.java`。
- `packagesJudgedByTotal` の一覧のパッケージ（9 つ）のうち、この段で新しく手を入れたものは無い（手を入れた本体は `useradmin.domain`・`useradmin.service`・`audit.domain`・`audit.service`・`user.repository`・`user.service`・`auth.service`）。`common.observability`・`common.error.web` を一覧から外すのは計画どおり Step 38。

### 依頼者に確かめたいこと（B4 の Step 26〜32 の分）

- **既存の `invitation/testsupport/TestInvitationBarrier` の待ちの確かめ（G-33 と同じ形）**: `waitingForLock()` が絞り込み `'%from invitations%for update%'` を文の中に書いているため、問い合わせ自身の文が絞り込みに当たり、2つ目が待ちに入る前に常に真を返している見込みがある（未検証。U3 の範囲の外のため直していない）。`InvitationConcurrencyIT`・`RegistrationConcurrencyIT` の重なりが確実に作られていない恐れがある。後の Intent で直すか、B4 の中で直すか。
- G-24〜G-37 の細部（特に G-28 の「行が無いときは待ち合わせの口を通らない」、G-30 の最後の有効な管理者の読み方、G-34 のテストの差し替えの形）を受け入れてよいか。

## B4（待ち合わせの直しと Step 33〜40、5回目の依頼）

作業ブランチ `feature/260930-user-admin-b4`（Step 17〜32 の変更はコミットされずに残したまま、その上に足した）。コミット・`git add` はしていない。Testing Contract は `sha256:25c76d4e2f9a91edd5d55fbff38287bcce006bace671ccf3da4e81c3a6393f2a`。Step 41 以降には進んでいない。

### 依頼者の決定（前の回の後）

- G-24〜G-37: 受け入れ。
- `invitation/testsupport/TestInvitationBarrier#waitingForLock` の弱点を B4 で直す。テストだけの変更として専用のコミットの区切り（C4b）にする。

### 待ち合わせの直し（C4b、テストだけの変更）

- **直す前の確かめ**（値は記録しない）: 直す前の手伝いの `afterLock` に一時的に計測を入れ、`RegistrationConcurrencyIT`（4 件・待ち合わせ 5 回）を流した。直す前の問い合わせは 5 回とも最初の見回しで真を返し、その時点で自分自身の問い合わせの文が絞り込みに当たっていた（自分のセッションの一致 5/5）。真を返した直後に、自分のセッションを除いて数え直すと、2つ目が排他の待ちに入っていたのは 4/5 で、1/5 は待ちに入っていなかった（その回は重なりを作れていなかった。直す前もテストは通っていた）。計測は写しから戻し、`git diff` で戻ったことを確かめた。
- **直し**: `waitingForLock()` の絞り込みを引数で渡し、自分のセッションを除く形にした（`TestUserAdminBarrier` の G-33 と同じ）。あわせて、元の操作を続けた時点で2つ目が終わっていたか（`secondDoneAtRelease`）を記録するようにし、`RegistrationConcurrencyIT` の4件（待ち合わせ5回）すべてで「続けた時点で後の操作が待ちに入っていた（偽）」を確かめる形にした。
- `InvitationConcurrencyIT` は変えていない（G-38）。
- 流したテスト: `:backend:integrationTest` の `RegistrationConcurrencyIT` 4 件・`InvitationConcurrencyIT` 1 件、通過。`RegistrationConcurrencyIT` を `--rerun` でさらに 3 回流し、3 回とも 4 件通過。
- **C4b の区切りのファイル**: `backend/src/test/java/cherry/mastersmith/invitation/testsupport/TestInvitationBarrier.java`・`backend/src/test/java/cherry/mastersmith/invitation/service/RegistrationConcurrencyIT.java`（どちらもテストだけ。`src/main` の変更を含まない）。計画 3.3 の C4 の後に置く。件名の案「B4 招待の待ち合わせの手伝いが自分の問い合わせを数えていたのを直す（テストだけ）」。

### Step 33〜40

| Step | 作ったもの・変えたもの | 実行したコマンド（名指し） | 結果 |
|---|---|---|---|
| 33 web | `UserAdminController` に5つの POST（`grant-admin`・`revoke-admin`・`suspend`・`resume`・`reset-login-failures`）と、`OperationResult` を場合を尽くす `switch` で 204・理由の code（`UserAdminProblemTypes.of`）・403 `ACCESS_DENIED`（`AccessProblemTypes`）・409 `USER_ADMIN_BUSY` に変える `respond` | `:backend:compileJava` | 通過 |
| 34 web（結合） | `UserAdminOperationsApiIT`・`UserAdminResetLoginFailuresApiIT`・`UserAdminBusyApiIT`・`UserAdminMassAssignmentIT`、`UserAdminApi#operate`、`UserAdminFixtures#removeLockState` | `:backend:integrationTest` の4クラス | 6・5・5・3 件、通過（`UserAdminBusyApiIT` は約 16 秒） |
| 35 監査 | `audit/service` の `UserAdminAuditIT`・`UserAdminAuditWriteFailureIT` | `:backend:integrationTest` の2クラスと既存の `AuditSecretLeakIT` | 4・4・3 件、通過（`AuditSecretLeakIT` は変えていない） |
| 36 漏えい | `UserAdminSecretLeakIT` に B4 の TRACE・INFO の2件を足した（5つの操作の成功と拒否、5つの操作のそれぞれの上限切れ） | `:backend:integrationTest --tests '…UserAdminSecretLeakIT'` | 4 件（B3 の 2 件 ＋ 2 件）、通過（約 38 秒） |
| 37 構造 | `UserAdminBoundaryArchitectureTest` に、書き換えの口を呼ぶのは `useradmin.service` だけ（持ち主のクラスを除く）と、`audit` から `useradmin` への依存は `useradmin.domain` だけ、を足した | `:backend:test` の `UserAdminBoundaryArchitectureTest`・`ArchitectureTest`・`*BoundaryArchitectureTest` | 8 件（6 ＋ 2）・5 件ほか、全 11 クラス通過（既存のテストは変えていない） |
| 38 一覧と文書 | `backend/build.gradle.kts` の `packagesJudgedByTotal` から `common.error.web`・`common.observability` を消し、説明文に B4（U3）で外したことを足した（一覧 9 → 7。足していない・除外を増やしていない）。`README.md` の「利用者の管理の API」「監査ログ（U4）」「既知の制約（同時の要求と接続プール）」に足した | — | — |
| 39 負荷の試験 | `perf/k6/scenarios.js` に `userAdminList`（`LIST_CASE` a〜d）・`userAdminProfile`・`userAdminOps`・`userAdminSuspendWorst`・`userAdminPool`、`perf/README.md` に「利用者の管理の場面」の節 | `k6 inspect --include-system-env-vars`（`grafana/k6:2.3.0`）を5つの場面で | 5つとも読み込めて、場面の名前と閾値が出た（下の表） |
| 40 レビュー | 下の「Step 40 の確かめ」 | — | — |

- **`packagesJudgedByTotal` の突き合わせ**（`git diff --name-only develop -- backend/src/main` とまだ追跡していないファイル）: 手を入れた本体のパッケージは `audit.domain`・`audit.service`・`auth.repository`・`auth.service`・`common.error.web`・`common.observability`・`common.persistence`（新）・`invitation.lock`（新）・`invitation.repository`・`user.repository`・`user.service`・`useradmin.domain`・`useradmin.service`・`useradmin.web`。一覧に入っていたのは見込みどおり `common.error.web`・`common.observability` の2つだけ。
- **途中のカバレッジの確かめ**（計画の外。下限を満たせるかを早く知るため。判定は Step 41 の verify）: colima の設定を渡し、`:backend:cleanTest :backend:test`（1,503 件、失敗 0・飛ばし 0）と `:backend:cleanIntegrationTest :backend:integrationTest :backend:jacocoTestReport`（688 件、失敗 0・飛ばし 0、7 分 13 秒）を流した。全体 行 98.7%（6257/6338）・分岐 94.8%（2326/2454）。外した `common.observability` 行 97.7%（126/129）・分岐 95.5%（42/44）、`common.error.web` 行 97.0%（196/202）・分岐 88.9%（88/99）。新しい `common.persistence` 100.0%・100.0%、`invitation.lock` 100.0%・100.0%、`useradmin.web` 98.8%・93.8%、`useradmin.service` 99.3%・95.6%、`useradmin.domain` 100.0%・100.0%。ほかの手を入れたパッケージも下限を満たす（最も低いのは `audit.service` 行 93.4%・分岐 82.4%）。
- `./gradlew :backend:spotlessCheck :backend:spotbugsGate`: 通過。新しい指摘は `UserAdminController` の priority 3 の `SPRING_ENDPOINT`（警告。B3 と同じ種類）だけ。`backend/config/spotbugs-exclude.xml` は変えていない。

### Step 39: 要件と台本の突き合わせ（`project.md` の学び）

| 要件・設計の項目 | 台本・手順書の対応 |
|---|---|
| NFR5.1 一覧 a〜d、1,000 名、同時 10・100 回以上、p95 1 秒・`checks`（200 と total） | `userAdminList`（`LIST_CASE`、`constant-vus` 10・60 秒）。`checks` は 200 と total の範囲（a〜c は 1,000 以上、d は 0）。手順 2'' で `perf-ua-0001`〜`1000` を入れる |
| NFR5.2 問い合わせの回数 | 結合テスト（B3 の `UserAdminListQueryCountIT`）。台本の対象外 |
| NFR5.3 氏名と言語、204 と 400、VU ごとの対象 | `userAdminProfile`（`perf-uapf<VU>`、`{name:userAdminProfile}`・`{name:userAdminProfileInvalid}`） |
| NFR5.4 5つの操作、操作ごとの p95・100 回以上・204 の率 1、受け入れの条件 (1)〜(3)、準備のログインを数えない | `userAdminOps`（`per-vu-iterations`、`UA_ROUNDS` 既定 100 ÷ VUS の切り上げ）。操作する管理者 `perf-uaop<VU>` は対象にしない、初期管理者は使わない、対象 `perf-uat<VU>` は VU ごと。準備のログインは `{name:userAdminPrepLogin}` で `check` しない |
| NFR5.5 止める悪い側（未無効 100・無効 1,000） | `userAdminSuspendWorst`（回ごとに別の対象 `perf-uasw-<VU>-<回>`）。手順 2'' の SQL でトークンの行を入れる |
| NFR5.6 BUSY を例外にしない | `checks` の率 1（409 を混ぜた p95 で合格にしない） |
| NFR5.7 台本・手順書・`k6 inspect` | この表と上の Step 39 の結果 |
| NFR6.2 上限 30 で5つの操作と一覧を同時 10、hikaricp の値、500 が 0、件数の一致、片付けの前に数える | `userAdminPool`（奇数の VU が操作、偶数の VU が一覧）。手順 1'' の `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE`、3'' の `metrics`、5'' の監査の件数の問い合わせ |
| NFR6.3 上限 10 の (A) 同時 5・(B) 同時 10 | 手順 3'''（`MASTERSMITH_DB_MAXIMUM_POOL_SIZE=10` で起動し直し、`userAdminOps` を `VUS=5`・`VUS=10`。(B) の ERROR の件数を数える） |
| `cicd-pipeline.md` 4.1（Mailpit を起動しない、VM の余裕、k6 の CPU を明記、配備したアプリを止める） | 手順書の前提（手順 0・1 と `docker info`、注記） |
| `cicd-pipeline.md` 4.2 の注記（`caffeinate -i` で全体を包む、目標を緩めない、索引の相談） | 手順 3''・3''' を台本ファイルにして `caffeinate -i zsh` で包む。注記に書いた |

- `k6 inspect` の結果: `userAdminList`（constant-vus 10・1m・閾値 `checks`・`{name:userAdminList}`）、`userAdminProfile`（閾値 2 つの name）、`userAdminOps`（per-vu-iterations 10 回、閾値 5 つの操作の name）、`userAdminSuspendWorst`（per-vu-iterations 10 回）、`userAdminPool`（constant-vus 10・閾値は `checks` だけ）。`VUS=5` の `userAdminOps` は 20 回になることも確かめた。既存の `health`・`invitationList` も変わらず読み込めた。
- 手順書の試験用のデータを入れる SQL は、捨ての H2 のファイル（リポジトリの外）に同じ形の表を作って流し、件数（1,000・管理者 10・対象 10・悪い側 100・未無効のトークン 10,000・全体 110,000）が意図どおりであることを確かめた（使い捨ての環境では流していない）。

### Step 40 の確かめ

- **伏せ字の経路**: B4 で足した本体の署名のうち `String` を受ける・返すのは、`RowLockUnavailableException(String lockKind)`・`RowLockFailures.warn(…, String lockKind, …)`・`getLockKind()`（排他の種類の定数）、`InvitationLockQueriesImpl#lockOne(String jpql, …)`（private、JPQL の定数、追跡の対象の外の `invitation.lock`）、`LockFailureSafeTraceInterceptor` の `replacePlaceholders`・`writeToLog`（追跡の文言）だけ。個人に関する値を `String` で渡す口は無い。5つの操作の controller は本文を読まず、`userId` は `long`。
- **U1 R-03**: `useradmin` は JPA のエンティティに依存しない（`UserAdminBoundaryArchitectureTest#noEntities`、`jakarta.persistence` の import も 0 件）。止める操作は `setSuspended` の後に `revokeAllRefreshTokens` を呼ぶだけで、先に読んだ値で書く処理は無い（4回目の Step 30 の確かめと同じ。この回の変更は controller だけ）。
- **排他の前に書き込みが無いこと**: 4回目の Step 30 の確かめのまま（この回は業務処理を変えていない）。
- **排他の待ちの上限**: `UserRowLockRepository`・`InvitationLockQueriesImpl`・`LoginAttemptStateRepository` とも 3000 ミリ秒の定数。待ち合わせの手伝いの止める上限は `TestUserAdminBarrier`・`TestLoginAttemptBarrier` とも 2000 ミリ秒（3000 より短い）。
- **名前の長さと移行**: 監査の種類・理由の名前は最長 20 文字（32 文字以内）。`backend/src/main/resources/db` に差は無く、移行を足していない（NFR10.2 は当たらない）。
- **設定の差**: `backend/config/spotbugs-exclude.xml`・`.gitleaks.toml`・`docker/monitoring/`（警報の決まりを含む）・`backend/gradle.lockfile`・`frontend/package-lock.json` に差が無い。問い合わせは名前つきの引数と SpEL だけ（足した EntityManager の問い合わせは `:target`・`:id` などの引数）。
- **ほかの待ち合わせの手伝い**: `INFORMATION_SCHEMA.SESSIONS` を読むのは `TestUserAdminBarrier`・`TestLoginAttemptBarrier`・`TestInvitationBarrier`（どれも引数の絞り込みで自分を除く形）と `H2SessionWaits`（絞り込みを引数で渡すため、自分の文は絞り込みに当たらない）だけで、同じ弱点の残りは無い。

### 計画との差・この段で決めた細部（B4 の待ち合わせの直しと Step 33〜40）

| ID | 計画の形・依頼 | 実際 | 理由 |
|---|---|---|---|
| G-38 | 依頼「`InvitationConcurrencyIT`・`RegistrationConcurrencyIT` で、1つ目を進めた時点で2つ目が待ちに入っていたことを確かめる」 | `RegistrationConcurrencyIT` だけを変えた。`InvitationConcurrencyIT` は変えていない | `InvitationConcurrencyIT` は `insertTogether`（`CyclicBarrier` で2つの招待を追記の直前にそろえる）だけを使い、弱点のあった `waitingForLock`（`whileLocked`）を使っていない。そろわなければ `CyclicBarrier` の上限で失敗するため、重なりはもとから確かめられている |
| G-39 | Step 34・35「ロックの状態の行が無い利用者」 | 利用者を作ると行（失敗回数 0）が作られる既存の動作（`LoginAttemptStateInitializer`）のため、行が無い利用者は `UserAdminFixtures#removeLockState`（JDBC で行を消す。`users` は書き換えない。`FailureResetPortIT` と同じ）で作った。行（0・無し）がある利用者も 409 `USER_ADMIN_NO_CHANGE` で、行が変わらないことを確かめた | 最初の版はこの動作を知らず、行が無いと見立てて失敗した（テストの側の誤り） |
| G-40 | Step 35 の監査の結合テスト | テストの手伝いは `audit`・`access`・`auth`・`common` のものだけを使い、`useradmin/testsupport` を使わない（要求は `HttpTestClient` で組む）。API の1件ずつでは起きない `LAST_ACTIVE_ADMIN`・`NOT_ADMIN` は業務処理を直接呼んだ。トレースID の一致は、`traceparent` を付けた成功の行と、拒否の応答の `traceId` と監査の行の一致で確かめた | 計画 8節の D-9（機能の間でテストの手伝いを使い回さない）。成功の操作は業務のログを出さないため、アプリの記録の側の値として応答の `traceId` を使った |
| G-41 | Step 36「上限切れの WARN が2行で同じトレースID」 | アプリの WARN（`lockKind`・`exceptionClass` の行と、code `USER_ADMIN_BUSY` の行）がちょうど2行で同じトレースID であることを確かめた。同じトレースID に Hibernate の誤りのロガー（`org.hibernate.orm.jdbc.error`）の WARN が2行（誤りの番号と SQLState、値の無い SQL の文。Step 25 で確かめた既知のもの）も出るため、アプリの外の WARN はこのロガーだけであることも確かめる形にした | 最初の版は WARN を全体で数えて4行で落ちた（テストの側の誤り）。Hibernate の行に値が無いことは、同じテストの出力の全体の確かめ（見分けやすい値・解除の予定の時刻・`MVStoreException` が0件）で確かめている |
| G-42 | Step 36「出力のどの行にも失敗回数が無い」 | 失敗回数は数字のため値では探さず、見分けやすい解除の予定の時刻（2031 年の日付）がログに無いこと、応答の本文に `consecutive`（失敗回数の項目名）が無いことで確かめた | 数字はログの時刻などに紛れて確かめにならないため。最初の版は応答に `failures` が無いことを確かめたが、Problem Details の `instance`（要求のパス `reset-login-failures`）に当たって落ちた（テストの側の誤り） |
| G-43 | Step 37 の書き換えの口の規則 | ArchUnit の規則の書き方ではなく、取り込んだクラスのメソッドの呼び出しを集めて、呼び出し元が持ち主のクラスか `useradmin.service` であることと、5つの口がどれも `useradmin.service` から呼ばれていること（規則が見分けている）を確かめる形にした。「`useradmin.web` から `access` への依存は `AccessProblemTypes` だけ」は B3 で置いた規則のまま、実際に依存していることの確かめを足した | 呼び出しの持ち主と名前の組で見る規則のため |
| G-44 | Step 34 の `UserAdminMassAssignmentIT` | `PUT /api/me/preferences` は `user/testsupport/MeApi#putPreferencesRaw` を使い、`POST /api/me/password` は余分な項目を入れるため `HttpTestClient` で本文を組んで送った。加えて、ロック中の利用者が表示の設定の本文でロックを解けないことの1件を足した | `MeApi` に本文をそのまま送るパスワードの口が無いため。`useradmin` → `user` は本体の依存と同じ向き |
| G-45 | Step 34「5つの操作の認可」 | API の確かめ直しの 403（`OperatorNotAdmin`）は、API の1件ずつでは起きないため、業務処理の結合テスト（4回目の `UserAdminConcurrencyIT`）と Step 35 の `NOT_ADMIN` の監査に任せた。API では認可の入口の 401・403・停止中の管理者の 401 を5つの操作のそれぞれで確かめた | 操作した人が有効な管理者でなくなるのは同時の操作のときだけのため |
| G-46 | Step 39 の場面 | 名前は計画の案どおり。`userAdminPool` は奇数の VU が5つの操作、偶数の VU が一覧（a）。NFR6.3 の上限 10 の場面は、新しい場面を足さず、`userAdminOps` を `VUS=5`・`VUS=10` で流す手順にした。1つの VU の回数は `UA_ROUNDS`（既定 100 ÷ `VUS` の切り上げ）。手順書の秘密の件数の確かめは、初期管理者の起動のログに伏せ字のメールアドレス（`@example.test` を含む）が出るため、`perf-ua` の件数で見る形にした | NFR6.3 は5つの操作だけを流す場面のため、同じ台本で同時の数だけを変えるのが確実なため |
| G-47 | Step 38 の README | 「利用者の管理の API」に5つの操作の表・拒否の順の表・戻すものが無いとき・最後の有効な管理者の保護・印の変更の効き方を、「監査ログ（U4）」に種類5つと理由6つ・残らない場合・記録の失敗を、「既知の制約」に1件に2本の接続と上限 30 を超えうることを足した。拒否の順は `RejectionPolicy` と `RejectionReason` の宣言で、名前は `AuditEventType`・`AuditFailureReason`・`UserAdminProblemTypes` の定義で確かめてから書いた | 計画 4.3・`infrastructure-specification.md` 9節 |

### 計画のチェック

- Step 33〜40 のすべての項目にチェックを付けた（チェックの印だけを変えた）。Step 41 以降は手を付けていない。

### コミットの区切り（計画 3.3）

- C4b（待ち合わせの直し、テストだけ）: 上のとおり。
- C7（web の5つの API・監査と改ざんと漏えいの結合テスト）: `useradmin/web/UserAdminController.java`。テストは `useradmin/web/` の `UserAdminOperationsApiIT.java`・`UserAdminResetLoginFailuresApiIT.java`・`UserAdminBusyApiIT.java`・`UserAdminMassAssignmentIT.java`・`UserAdminSecretLeakIT.java`、`useradmin/testsupport/` の `UserAdminApi.java`・`UserAdminFixtures.java`（`removeLockState`。4回目の C6 の変更と同じファイル）、`audit/service/` の `UserAdminAuditIT.java`・`UserAdminAuditWriteFailureIT.java`。
- C8（境界テスト・一覧・README・負荷の試験）: `useradmin/UserAdminBoundaryArchitectureTest.java`、`backend/build.gradle.kts`、`README.md`、`perf/k6/scenarios.js`、`perf/README.md`。

### 依頼者に確かめたいこと（B4 の待ち合わせの直しと Step 33〜40 の分）

- G-38（`InvitationConcurrencyIT` は弱点のある確かめを使っていないため変えていない）でよいか。
- G-39〜G-47 の細部を受け入れてよいか（特に G-41 の Hibernate の WARN 2行を許す確かめ方、G-46 の上限 10 の場面の作り方）。
- `UserAdminFixtures.java` は C6（4回目に足した手伝い）と C7（`removeLockState`）の両方に当たる。どちらの区切りに入れるか（案: C6 にまとめる）。

## B4（Step 41〜43 の記録、6回目の依頼）

作業ブランチ `feature/260930-user-admin-b4`（`develop` の c13e817 の上に、Step 17〜40 の変更をコミットせずに残したまま）。コミット・`git add`・統合はしていない。Testing Contract は `sha256:25c76d4e2f9a91edd5d55fbff38287bcce006bace671ccf3da4e81c3a6393f2a`。

### 依頼者の決定（前の回の後）

- G-38〜G-47: 受け入れ。
- `UserAdminFixtures.java`（4回目の手伝いと5回目の `removeLockState` の両方に当たる）は C6 の区切りに入れる。

### Step 41: 1コマンドの検査（B4 の統合の前の関門）

colima が動いていることを確かめ、`DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` を渡して `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行した（2026-10-03 00:47:40〜00:56:35）。

| 項目 | 値（B4 の基準 = B3 の Step 15 との差） |
|---|---|
| 結果 | BUILD SUCCESSFUL（1回目で通過。一時的な失敗なし） |
| 時間 | 8 分 55 秒（壁時計 535 秒。Gradle の表示 8m 55s。基準 6 分 37 秒（397 秒）から +2 分 18 秒） |
| 単体（`test`） | 1,503 件（+148）、失敗 0・誤り 0・飛ばし 0 |
| 結合（`integrationTest`） | 688 件（+68）、失敗 0・誤り 0・飛ばし 0（対象DB のテストも SKIPPED なし） |
| 画面（Vitest） | 95 ファイル・801 件、すべて通過（変わらない） |
| 全体のカバレッジ | 行 98.7%（6257/6338）・分岐 94.8%（2326/2454） |

| パッケージ | 行 | 分岐 | 下限の判定 |
|---|---|---|---|
| `useradmin.web` | 98.8%（83/84） | 93.8%（15/16） | パッケージごと（新しい） |
| `useradmin.service` | 99.3%（133/134） | 95.6%（43/45） | パッケージごと（新しい） |
| `useradmin.domain` | 100.0%（83/83） | 100.0%（52/52） | パッケージごと（新しい） |
| `common.persistence` | 100.0%（26/26） | 100.0%（20/20） | パッケージごと（新しい） |
| `invitation.lock` | 100.0%（21/21） | 100.0%（2/2） | パッケージごと（新しい、G-23） |
| `common.observability` | 97.7%（126/129） | 95.5%（42/44） | パッケージごと（B4 で一覧から外した） |
| `common.error.web` | 97.0%（196/202） | 88.9%（88/99） | パッケージごと（B4 で一覧から外した） |
| `user.domain` | 99.6%（238/239） | 97.8%（135/138） | パッケージごと |
| `user.repository` | 100.0%（28/28） | 100.0%（6/6） | パッケージごと |
| `user.service` | 99.7%（306/307） | 95.1%（116/122） | パッケージごと |
| `auth.domain` | 98.2%（112/114） | 100.0%（38/38） | パッケージごと |
| `auth.repository` | 100.0%（49/49） | 100.0%（8/8） | パッケージごと |
| `auth.service` | 99.3%（265/267） | 93.0%（80/86） | パッケージごと |
| `audit.domain` | 99.6%（248/249） | 98.3%（59/60） | パッケージごと |
| `audit.service` | 93.4%（155/166） | 82.4%（28/34） | パッケージごと |
| `invitation.repository` | 100.0%（2/2） | 分岐なし | パッケージごと |
| `access.domain` | 100.0%（53/53） | 100.0%（29/29） | パッケージごと（変えていない） |

- どのパッケージも下限（行 80%・分岐 70%）を満たした。下限・計測の除外は変えていない（`packagesJudgedByTotal` は 9 → 7 に減っただけ）。値は5回目の依頼の途中の確かめと同じだった。
- **時間の許容（Q-C A）**: 手元の増加は +2 分 18 秒で、許容（基準から +5 分以内）に収まる。CI の見込み: B3 の統合の CI（37015756225）は 9 分 21 秒。手元の増加をそのまま足すと約 12 分前後で、30 分（制限 60 分の半分）に十分収まる見込み。CI の実測は依頼者の push の後に確かめる（Build and Test に引き継ぐ）。計画 4.6 の見込み（約 9〜11 分）の範囲の中。
- SpotBugs ＋ FindSecBugs（`spotbugsGate`）・Gitleaks（no leaks found）は除外を足さずに通過。`git diff develop` で `backend/config/spotbugs-exclude.xml`・`.gitleaks.toml`・`backend/gradle.lockfile`・`frontend/package-lock.json`・`docker/monitoring/`・`backend/src/main/resources/db` に差なし。
- `./gradlew osvScan --rerun-tasks`（00:56:59〜00:57:02）: BUILD SUCCESSFUL。失敗の条件に当たるもの 0 件、警告 14 件（B3 の関門と同じ。すべて `vendor/make-you-chic-ui/package-lock.json` の開発用の依存 `brace-expansion@5.0.9`（3 件）・`undici@8.10.0`（11 件））。

### Step 42: E2E

- Mailpit はもとから動いていた（`mastersmith-mailpit-1`、`/api/v1/info` が 200）。そのまま動かしておく（計画の「もとから動いていたときはそのまま」）。Playwright の chromium は入っていた。実行の前に `frontend/playwright-report/`・`frontend/test-results/` は無かった。
- `caffeinate -i ./gradlew e2eTest`（2026-10-03 00:57:18〜01:01:31、Gradle の表示 4m 12s）: BUILD SUCCESSFUL。Playwright の表示 130 passed（4.2m）。E2E の json に仮の資格情報が含まれていないことの確かめ（3 項目）も通った。
- `frontend/test-results/e2e-results.json` の `stats`: expected 130・unexpected 0・flaky 0・skipped 0、duration 約 250.7 秒。

| ファイル | 通過 |
|---|---|
| `010-skeleton.e2e.ts` | 2 |
| `020-auth.e2e.ts` | 2 |
| `030-admin-access.e2e.ts` | 1 |
| `040-dsl-admin.e2e.ts` | 1 |
| `050-display-accessibility.e2e.ts` | 21 |
| `060-invitation-accessibility.e2e.ts` | 21 |
| `070-registration-accessibility.e2e.ts` | 20 |
| `080-preferences-accessibility.e2e.ts` | 21 |
| `090-invitation-registration-flow.e2e.ts` | 1 |
| `100-app-text-contrast.e2e.ts` | 20 |
| `130-admin-forbidden-accessibility.e2e.ts` | 20 |

- いまある E2E は上の 11 ファイル（110・120 の番号のファイルは無い）。計画の「いまある 010〜130 のすべて」を流した。U3 は E2E の流れを足していない。
- 記録の後、`frontend/playwright-report/`・`frontend/test-results/` を中を開かずに消した（json は上の集計のために `stats` とファイルごとの結果だけを読んだ。消したのはファイル 3 件・ディレクトリ 106 件（どちらも消す前の数））。報告はどこにも共有していない。

### Step 43（記録の部分）

- `code-summary.md` を B3 と B4 を合わせた U3 の最終の版に書き直した。
- `source-manifest.json`: B3 の 54 件（`develop` に統合済み）に B4 の作った・変えたファイルを足して 123 件（本体 62・テスト 57・ほか 4（`backend/build.gradle.kts`・`README.md`・`perf/k6/scenarios.js`・`perf/README.md`））。どれも実在することを確かめた。`aidlc/` の下は含めない。
- `traceability.json`: 155 件（OK 116・Deferred 36・N/A 3）。OK の target は実在する1つのファイルにした（B3 の版は1つの target に複数のファイルとテストの名前を書いていたため、主なファイル1つに絞った。ほかの確かめのファイルは `code-summary.md` の 6節）。Deferred は U5 15 件・U1 10 件・performance-validation 8 件・observability-setup 3 件。N/A は NFR10.2（索引・移行を足していない）と、NFR2.3・NFR6.7（G-50）。`aidlc engine sensor-traceability --stage-slug code-generation` で確かめ、pass（findings 0）。
- 計画の Step 41・42 のすべてと、Step 43 の1つ目（記録）にチェックを付けた。Step 43 のコミット・統合・CI の項目は付けていない。

### 計画との差（Step 41〜43）

| ID | 計画の形 | 実際 | 理由 |
|---|---|---|---|
| G-48 | Step 43「`traceability.json`（U3 の受け入れ基準・決まり・NFR）を仕上げる」 | OK の target を実在する1つのファイルに絞った（B3 の途中の版の書き方から変えた）。AC4.1.2 の「設定で変えたしきい値（例: 3 回）」は U3 のテストでは既定の 5 だけを確かめ、任意のしきい値は既存の `auth/domain/LockPolicyTest` の性質ベースのテスト（しきい値 1〜20）が受け持つ | センサーの target の確かめに合わせるため。U3 の計画 Step 34 も「しきい値は設定の既定 5」 |
| G-49 | Step 42「Mailpit は見終わったら止める」 | 止めていない | 実行の前から動いていた（配備したアプリと同じ compose の Mailpit）ため、計画の但し書きどおりそのまま |
| G-50 | `traceability.json` の upstream_ids は U3 のすべての AC・BR・NFR（B3 の版の 153 件） | 加えて NFR2.3・NFR6.7 を N/A で足した（155 件） | traceability のセンサーが、U3 の NFR 要件の文書の出典の列に引いた U1 の NFR2.3 と前の Intent の U2 の NFR6.7 を U3 の上流の ID として拾い、足さないと pass にならないため。U3 の要件ではない旨を target に書いた |

## B4（コード生成のレビューの指摘への対応、7回目の依頼）

作業ブランチ `feature/260930-user-admin-b4`（B4 の変更はコミットせずに残したまま）。コミット・`git add`・統合・push はしていない。計画の本文は変えていない。Testing Contract は `sha256:25c76d4e2f9a91edd5d55fbff38287bcce006bace671ccf3da4e81c3a6393f2a`。

### レビューの指摘と依頼者の決定

U3 のコード生成のレビュー（`aidlc-architecture-reviewer-agent`、1回目、判定 READY、2026-10-02T16:06:59Z）の指摘 R-01〜R-05 に、依頼者が次のとおり決めた。

| ID | 重さ | 指摘の要点 | 依頼者の決定 |
|---|---|---|---|
| R-01 | Major | 止める操作の中のリフレッシュトークンの無効化（`revokeAllRefreshTokens` の更新）の待ちが上限切れ（H2 の既定、約 2 秒）になると、Busy に写されず 500 `INTERNAL_ERROR` になる。確かめは対象の行を持ち続ける場合だけ | **直す**。ほかの経路と同じく 409 `USER_ADMIN_BUSY` にし、明示の巻き戻しの印・行の値をログに出さない・例外を外へ出さないの決まりを守る。結合テスト（API で 409 と状態が変わらないこと、ログに値が無いこと）と単体テストを足す |
| R-02 | Minor | 停止を解く・失敗回数を戻すでは、操作した人の確かめ直しが排他なしの読み取りのため、確かめた直後に印を外されると1件通りうる | **受け入れて記録**（隙として残る危険に書く。この2つの操作は有効な管理者の数を減らさないため、最後の管理者の保護は崩れない） |
| R-03 | Minor | `audit.service` のカバレッジが B3 の行 100.0%・分岐 84.4% から行 93.4%・分岐 82.4% に下がった理由を確かめていない | **確かめる**。JaCoCo の報告で下がった行・分岐を確かめて記録し、B4 で足したコードの分岐が覆われていなければテストを足す（除外は足さない） |
| R-04 | Minor | 一意の制約の違反（23505）の例外の文の件（Q-H）は未検証のまま後の Intent へ | **Q-H のとおり後の Intent**（持ち主と、再現するテストを先に書くことを申し送りに書く） |
| R-05 | Minor | traceability の target を1つに絞ったため、AC4.1.2 の任意のしきい値の網羅が読み取れない | **受け入れ**（任意のしきい値は既存の `auth/domain/LockPolicyTest` の性質ベースのテストで確かめられている） |

### R-01 の直し

- `useradmin/service/UserAdminService.java`: 止める操作（`adminRowsOperation` の `SUSPEND`）で、`revocations.revokeAllRefreshTokens(対象)` を新しい非公開のメソッド `revokeAllRefreshTokens(long)` で包んだ。`RuntimeException` を受け、`RowLockFailures.isLockFailure` に当たるときだけ `RowLockFailures.warn(LOGGER, "REFRESH_TOKEN_ROWS", e)`（キーは `lockKind`・`exceptionClass` だけで、例外そのものは渡さない）の後に偽を返し、呼び出し元が既存の `busy(status)`（`status.setRollbackOnly()` を先に付け、DB に触れずに `OperationResult.Busy`）を返す。排他の失敗でない例外はそのまま投げる（今までどおり想定外の誤り）。
  - 停止の書き換え（`setSuspended(対象, true)`）の後に起きるが、巻き戻しの印で停止もトークンの無効化も残らない。監査の出来事は出さない（BR3.5・BR6.4）。controller は既存の `Busy` → 409 `USER_ADMIN_BUSY` の写しをそのまま使う（変更なし）。
  - 定数 `REFRESH_TOKEN_ROWS`（排他の種類）とロガーを足した。クラスの説明文に「業務のログは出さないが、この上限切れだけは排他の種類の WARN を出す」ことを足した。
- **受ける場所を業務処理の層にした理由**（計画との差 G-51）: `security-design.md` 7節・`reliability-design.md` 5.2 は上限切れを「問い合わせを実行する repository のメソッドの本体の中で受ける」とするが、それは排他の読み取りの口（E1〜E4・U3 の3つの口）の決まりで、書き込みの問い合わせ（`RefreshTokenRepository#revokeAllActiveByUserId` は Spring Data の `@Modifying`）は承認の場の決定 I-D1 で個別に直さず、`TraceAspect`（`LockFailureSafeTraceInterceptor`）と `GlobalExceptionHandler` の中央の手当てで値を出さない形にしている。レビューの推奨 (a)「service で受けて setRollbackOnly を付けて Busy にし」と依頼者の決定に合わせ、C1 の口（`RefreshTokenRevocationService`・`RevokeAllResult`）の形も変えずに済む業務処理の層で受けた。例外は repository と `RefreshTokenRevocationService` の代理を通るが、TRACE の出力は中央の手当てでクラスの名前だけになり（下の漏えいのテストで確かめた）、業務処理の層より外（web・`GlobalExceptionHandler`）へは出ない。
- テスト:
  - `useradmin/service/UserAdminServiceTest.java` に2件: (1) `revokeAllRefreshTokens` が `CannotAcquireLockException`（原因に誤りの番号 50200・SQLState HYT00 の `SQLException`、文に行の値に見立てた文字）を投げると、結果が `Busy`、`setRollbackOnly()` が1回・巻き戻し1回・確定 0 回、監査の出来事なし、WARN が1件でキーは `lockKind=REFRESH_TOKEN_ROWS` と `exceptionClass` だけ・例外を渡さない・文に値が無い、`setSuspended` の後に `revokeAllRefreshTokens` を呼んだ順。(2) 排他の失敗でない例外（23505 の `DataIntegrityViolationException`）はそのまま投げ、WARN を出さず、出来事なし、例外で巻き戻る。
  - `useradmin/web/UserAdminBusyApiIT.java` に1件: 対象がログインしてリフレッシュトークンを1件持ち、別の接続（`RowLockHolder`）で `SELECT token_id FROM refresh_tokens WHERE user_id = ? FOR UPDATE` で持ち続けて止める操作を送ると、409 `USER_ADMIN_BUSY`（500 にならない）、利用者の列・ロックの状態の列・有効なトークンの数・監査の行の数が変わらず、応答に対象のメールアドレス・氏名・`MVStoreException`・`Timeout` が無い。放した後の止める操作は 204 でトークンが 0 になる。待ちの下限は 1000 ミリ秒で確かめた（書き込みの待ちの上限は H2 の既定で設定ではなく、行の排他の読み取りの 3000 ミリ秒より短いため。既存の `assertBusy` に下限を引数で渡す形を足し、既存の5件は 3000 のまま）。このテストの時間は 2.069 秒（Step 25 の実測の約 2.0 秒と合う）。
  - `useradmin/web/UserAdminSecretLeakIT.java` の B4 の確かめ（TRACE と INFO の両方）に、同じ上限切れを6つ目として足した: 利用者を1人足してログインさせ、そのリフレッシュトークンの行を持ち続けて止める操作を送る。秘密の値に、その利用者のメールアドレス・氏名・パスワードのハッシュ値と、トークンのハッシュ値の16進（小文字・大文字）を足した。WARN の確かめに `REFRESH_TOKEN_ROWS` を足し、6つの上限切れのどれでも、同じトレースID のアプリの ERROR が無いこと（500 にならない）の確かめを足した。
- **再現の確かめ**（`project.md` の Mandated）: テストが通った後、`UserAdminService` の受け止めだけを一時的に効かなくした状態（`catch` の型を当たらない型に替えた写し）で `UserAdminBusyApiIT`・`UserAdminSecretLeakIT` を流し、3件が落ちること（`UserAdminBusyApiIT` の新しい1件が「409 のはずが 500」、`UserAdminSecretLeakIT` の B4 の2件が「6つ目の上限切れが 500」）を確かめてから、ホームの下に取っておいた写しで元に戻し、`catch (RuntimeException e)` に戻ったことを確かめた。単体テスト (1) も受け止めが無ければ落ちる形（`Busy` を期待）。
- 依頼者に確かめたいこと: 直しの本体（C6 の `UserAdminService.java`）と、それを再現する結合テスト（C7 の `UserAdminBusyApiIT.java`・`UserAdminSecretLeakIT.java`）が計画 3.3 の区切りでは別のコミットになる。C6 には再現する単体テスト（`UserAdminServiceTest.java`）が入るため Mandated は満たすと考え、区切りは変えない案にした。結合テストも同じコミットにしたいときは、2つのファイルを C6 へ移す。

### R-03 の確かめ（`audit.service` のカバレッジ）

Step 41 の verify（2026-10-03 00:56）が残した `backend/build/reports/jacoco/test/jacocoTestReport.xml` で、`audit.service` の通らない行と分岐を読んだ。通らないのはすべて `AuditEventListener.java` で、ほかのクラスは 100% だった。

| 行 | 中身 | 通らない行・分岐 | B3 との比べ |
|---|---|---|---|
| 157・170・184・198・213 | 招待と登録の5つの受け取りの、組み立てに失敗したときの項目の `event == null ? null : event.invitationId()` | 分岐 5（それぞれ片方）。行は通っている | B3 の前からある（B3 の 84.4% = 27/32 の未通過の 5 分岐と同じ） |
| 232 | B4 で足した `fields(UserAdminAuditEvent)` の `if (event == null)` | 分岐 1（null でない側） | **B4 で足した** |
| 238〜250 | 同じメソッドの null でない出来事の項目を並べる部分 | 行 11 | **B4 で足した** |

- 下がった理由: B4 で足した `onUserAdminAuditEvent` の「監査イベントの組み立てに失敗したとき」の項目の作り方のうち、出来事が null でない場合（組み立ての部品 `AuditEventFactory.from` が失敗した場合）を通るテストが無かった。既存の `userAdminEventFailureIsContained` は追記の失敗（組み立ては成功し `fields(AuditEvent)` を通る）と null の出来事だけを通していた。本番では出来事の側の確かめ（`UserAdminAuditEvent` の必須の値）で組み立ては失敗しないが、受け止めの経路として既存の出来事（`unbuildableEventsLogTheirFields`）と同じく確かめる。
- 足したテスト: `audit/service/AuditEventListenerTest.java` に1件（`unbuildableUserAdminEventLogsItsFields`）。操作の区分が無い（null）出来事を差し替えで作り、組み立てが失敗しても例外が出ず、ERROR が1件で固定の文、項目に `actorUserId`・`targetUserId`・`targetInvitationId`（null）・`failureReason`・`sourceIp`・`auditTraceId`・`enteredEmail`（null）があり DSL の項目が無いこと、記録の部品を呼ばないことを確かめた。これで B4 で足した行 11・分岐 1 を通る見込み（B3 からある招待の 5 分岐は B4 の変更ではないため、テストを足していない）。除外は足していない。
- 気づいたこと（直していない）: 組み立てに失敗したときの `fields(UserAdminAuditEvent)` は `auditEventType` のキーに `AuditEventType`（例 `USER_SUSPENDED`）ではなく操作の区分（例 `SUSPEND`）を載せる（`event.operation()` をそのまま渡すため）。本番では起きない経路で、値に個人に関する値は無い。直すかは依頼者に確かめる。

### 流したテスト（名指し）

- 単体: `UserAdminServiceTest`（36 件）・`AuditEventListenerTest`（22 件）・`UserAdminBoundaryArchitectureTest`（8 件）。すべて通過。
- 結合: `UserAdminBusyApiIT`（6 件）・`UserAdminSecretLeakIT`（4 件）・`UserAdminOperationsApiIT`（6 件）・`UserAdminOperationsIT`（5 件）。すべて通過。
- `./gradlew :backend:spotlessApply` で書式をそろえた。

### 関門の流し直し（止めた）

colima が動いていることを確かめ、`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡して `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行した（2026-10-03 01:18:34〜01:26:56、Gradle の表示 8m 22s）。

- 結果: **BUILD FAILED**。結合テスト 689 件のうち1件が落ちた: `mail/config/MailConfigurationIT` の `invalidSettingWarnsOnce`「credentials without encryption」。単体テストは通った（`integrationTest` で止まったため、カバレッジの検証・SpotBugs・Gitleaks・OSV-Scanner と画面の検査の結果は取れていない）。
- 落ちた中身: このテストは、起動した文脈の出力の全体（`output.getAll()`）に設定の値（`spring.mail.host` の `127.0.0.1` など）が無いことを確かめる。出力に、**別のテストのクラスの Spring の文脈**の背景のスレッド（`otlp-metrics-publisher-3`）が出した `io.micrometer.registry.otlp.OtlpMeterRegistry` の WARN「Failed to publish metrics to OTLP receiver (context: url=http://127.0.0.1:<閉じた番号>/v1/metrics ...)」が入り、`127.0.0.1` を含んでいたため落ちた。メールの設定の値そのものが出たのではない。指標の外部エクスポートを有効にするのは `common/observability/ExternalExportIT`（指標のレジストリが `OtlpMeterRegistry` になることを確かめる）で、テストの文脈の使い回しで残った文脈の送信が、時刻の重なりでこのテストの取り込みの間に出たと見立てる（未検証）。
- 今回の変更との関係: 今回変えたのは `useradmin.service` の1か所とテスト4ファイルで、メールの設定・観測・`ExternalExportIT` には触れていない。B4 の Step 41（00:47〜00:56）の verify では通っていた。
- 範囲を決めた確かめ: `MailConfigurationIT` だけを1回流し直し、7 件すべて通った（01:27）。
- 依頼の「失敗したら直さずに止めて報告する」に従い、**verify の流し直し・E2E（`e2eTest`）は行っていない**。`team.md` の「不安定なテストと CI の失敗」の決まりでは、手元で再現しないものは見立てと試みの範囲を記録したうえで流し直して進めてよいが、扱いは依頼者に確かめる（同じテストが二度目に落ちたら原因を直すまで進まない）。
- そのため、この回の verify の時間・件数・カバレッジ（`audit.service` の値を含む）と E2E の結果はまだ無い。`code-summary.md` の実測（2節）は Step 41 の値のまま残し、流し直しの後に書き足す。

### 依頼者の決定（関門の失敗の扱い）と流し直し

- 依頼者の決定: (1) `MailConfigurationIT#invalidSettingWarnsOnce` の1件の失敗は、`team.md` の「不安定なテストと CI の失敗」の決まりで扱う。verify を流し直し、通れば進めてよい（不安定と確かめられていない扱い）。同じテストが二度目に落ちたら、直さずに止めて報告する。出力を捕まえる範囲の弱さは、後の Intent への申し送りとして `code-summary.md` に書く。(2) コミットの区切りはそのまま（直しと再現する単体テストは C6、結合テストは C7）。
- 決まりに沿った記録:
  - 手元での再現: しなかった。`MailConfigurationIT` だけを1回流し直し（01:27）、7 件すべて通った。
  - 見立て（未検証）: `common/observability/ExternalExportIT` が有効にした OTLP の指標の送信（`OtlpMeterRegistry`）の文脈が、テストの文脈の使い回しで残った。その背景のスレッド（`otlp-metrics-publisher-*`）が閉じた番号へ送りに失敗した WARN（`127.0.0.1` を含む）を出し、それが `MailConfigurationIT` の出力の捕まえ（`output.getAll()` は捕まえの間のすべてのスレッドの出力を含む）に紛れ込んだ。設定の値そのものの漏えいではない。
  - 試みの範囲: 単独の流し直し1回だけ。時間の上限を決めた再現の試み（両方のクラスを並べて流すなど）は行っていない。今回の変更の経路の外のため、原因の確かめは後の Intent に回す。

#### verify の流し直し（レビューの指摘への対応の後の関門）

colima の設定（`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`）を渡し、`caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行した（2026-10-03 01:31:23〜01:41:57）。

| 項目 | 値（B4 の基準 = B3 の Step 15 との差、Step 41 との差） |
|---|---|
| 結果 | BUILD SUCCESSFUL（流し直しの1回目で通過。`MailConfigurationIT` を含めて失敗なし） |
| 時間 | 10 分 34 秒（壁時計 634 秒。Gradle の表示 10m 33s。基準 6 分 37 秒から +3 分 57 秒、Step 41 の 8 分 55 秒から +1 分 39 秒） |
| 単体（`test`） | 1,506 件（基準 +151、Step 41 +3）、失敗・誤り・飛ばし 0 |
| 結合（`integrationTest`） | 689 件（基準 +69、Step 41 +1）、失敗・誤り・飛ばし 0（対象DB のテストも SKIPPED なし） |
| 画面（Vitest） | 95 ファイル、すべて通過（変わらない） |
| 全体のカバレッジ | 行 98.9%（6276/6346）・分岐 94.8%（2331/2458） |

| パッケージ | 行 | 分岐 |
|---|---|---|
| `useradmin.web` | 98.8%（83/84） | 93.8%（15/16） |
| `useradmin.service` | 99.3%（141/142） | 95.9%（47/49） |
| `useradmin.domain` | 100.0%（83/83） | 100.0%（52/52） |
| `common.persistence` | 100.0%（26/26） | 100.0%（20/20） |
| `invitation.lock` | 100.0%（21/21） | 100.0%（2/2） |
| `common.observability` | 97.7%（126/129） | 95.5%（42/44） |
| `common.error.web` | 97.0%（196/202） | 88.9%（88/99） |
| `user.domain` | 99.6%（238/239） | 97.8%（135/138） |
| `user.repository` | 100.0%（28/28） | 100.0%（6/6） |
| `user.service` | 99.7%（306/307） | 95.1%（116/122） |
| `auth.domain` | 98.2%（112/114） | 100.0%（38/38） |
| `auth.repository` | 100.0%（49/49） | 100.0%（8/8） |
| `auth.service` | 99.3%（265/267） | 93.0%（80/86） |
| `audit.domain` | 99.6%（248/249） | 98.3%（59/60） |
| `audit.service` | **100.0%（166/166）** | **85.3%（29/34）** |
| `invitation.repository` | 100.0%（2/2） | 分岐なし |
| `access.domain` | 100.0%（53/53） | 100.0%（29/29） |

- `audit.service` は Step 41 の 93.4%・82.4% から 100.0%・85.3% になり、B3 の 100.0%・84.4% を上回った。通らないのは B3 の前からある招待と登録の5つの分岐（157・170・184・198・213）だけで、B4 で足した行・分岐はすべて通った（R-03 の確かめ）。
- どのパッケージも下限（行 80%・分岐 70%）を満たした。下限・計測の除外・`packagesJudgedByTotal` は変えていない。
- **時間の許容（Q-C A）**: 基準から +3 分 57 秒で、許容（+5 分以内）に収まる。Step 41 より 1 分 39 秒長い。増えたテストは上限切れを待つもの（`UserAdminBusyApiIT` に約 2 秒、`UserAdminSecretLeakIT` の TRACE と INFO で各約 2 秒）で数秒分のため、差の大半は PC の負荷の揺れと見る（未検証）。CI の見込み: B3 の統合の CI 9 分 21 秒に手元の増加を足して約 13〜14 分で、30 分以内に収まる見込み。
- SpotBugs ＋ FindSecBugs（`spotbugsGate`）・Gitleaks（no leaks found）は通った。`osvScan` は入力（lockfile）が変わらず UP-TO-DATE で、Step 41 の `--rerun-tasks` の結果（失敗の条件に当たるもの 0 件、警告 14 件）のまま。依存・lockfile は変えていない。

#### E2E

- Mailpit はもとから動いていた（`mastersmith-mailpit-1`、`/api/v1/info` が 200）。そのまま動かしておく。実行の前に `frontend/playwright-report/`・`frontend/test-results/` は無かった。
- `caffeinate -i ./gradlew e2eTest`（2026-10-03 01:42:19〜01:46:56、Gradle の表示 4m 36s）: BUILD SUCCESSFUL。Playwright の表示 130 passed（4.3m）。E2E の json に仮の資格情報が含まれていないことの確かめ（3 項目）も通った。
- `frontend/test-results/e2e-results.json` の `stats`: expected 130・unexpected 0・flaky 0・skipped 0、duration 約 259.6 秒。ファイルごとの通過は Step 42 と同じ（010: 2、020: 2、030: 1、040: 1、050: 21、060: 21、070: 20、080: 21、090: 1、100: 20、130: 20）。
- 記録の後、`frontend/playwright-report/`・`frontend/test-results/` を中を開かずに消した（json は `stats` とファイルごとの結果だけを読んだ。消す前はファイル 3 件・ディレクトリ 106 件）。報告はどこにも共有していない。

## B4 のコミットと統合（2026-10-03）

- 依頼者の承認を得て、オーケストレーターが作業ブランチに C4 718832b・C4b 2951439・C5 6b8c3f1・C6 208792a・C7 3c48198・C8 03bcb30 を作った。1回目は git の一時のロック（同時に動いたフックの書き込み）で一部のコミットが失敗して順が崩れたため、作業ブランチを c13e817 に戻して（ファイルは残したまま）、ロックが外れるのを待つ形で計画の順に作り直した。
- 記録のコミット R4 の後、`develop` へ squash の1コミットで統合し（`aidlc/` は外す）、`develop` で記録のコミットを作り、作業ブランチを消す。
