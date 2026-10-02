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
