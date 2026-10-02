# コード生成のまとめ — U3 利用者の管理の API（u3-user-admin-api）

> **B3 までの途中の版**（依頼者の決定 Q-B A）。B3（一覧と氏名・言語の変更、計画の Step 1〜15）の生成の結果を書く。B4（5つの操作と最後の管理者の保護、Step 17〜43）の結果は B4 の終わり（Step 43）に書き足して仕上げる。`traceability.json` も B3 までの版で、B4 で受け持つ受け入れ基準・決まり・NFR は `Deferred`（持ち主 B4）にしてある。

パスはリポジトリのルートからの相対パス。経過の詳しい記録は同じディレクトリの `generation-notes.md`。

## 1. 作ったもの・変えたもの（B3）

作業ブランチ `feature/260930-user-admin-b3`（`develop` の 493b4dc から）。コミットはまだしていない（3.3 の C1〜C3 の区切りで依頼者の承認を得て行う）。ファイルの一覧は `source-manifest.json`（54 件）。

### 本体（`backend/src/main`）

| パッケージ | 部品 | 新しい・手を入れた |
|---|---|---|
| `user.domain` | `SearchText`・`ProfileUpdate`・`ProfileValidation` | 新しい |
| `user.repository` | `UserAdminRow`（投影、Q-A）、`UserRepository` に `findAdminRows`・`findAdminRowsBySearch`・`countBySearch`・`updateProfile` | 新しい・手を入れた（メソッドを足しただけ） |
| `user.service` | `UserAdminSummary`・`UserAdminSlice`・`ProfileCommand`・`ProfileUpdateResult`、`UserAccountService#findAdminPage`・`#updateProfile` | 新しい・手を入れた（メソッドを足しただけ） |
| `auth.domain` | `LockView` | 新しい |
| `auth.repository` | `LoginAttemptStateRepository#findBySubjectIds` | 手を入れた（メソッドを足しただけ） |
| `auth.service` | `LockAdministrationService#lockViewsOf` | 新しい |
| `useradmin`・`useradmin.domain` | `package-info`、`UserAdminProblemTypes`（404 `USER_NOT_FOUND`） | 新しい |
| `useradmin.service` | `UserAdminService`（`list`・`updateProfile`）・`UserAdminListResult`・`UserAdminPage`・`UserAdminEntry`・`UserAdminProblemTypeCatalog` | 新しい |
| `useradmin.web` | `UserAdminController`（`GET /api/admin/users`・`PUT /api/admin/users/{userId}/profile`）・`AdminUser`・`AdminUserPage`・`ProfileRequest`・`SearchTextConverter`・`UserAdminWebConfig`・`UserAdminRequestContextResolver`・`UserAdminFieldErrors` | 新しい |

手を入れていない: `common.*`・`access.*`・`config`・認証の経路（`auth.web`・`LoginService` など）・`frontend/`・`application.yaml`・`compose.yaml`・`Dockerfile`・`.env.example`・`backend/build.gradle.kts`・`docker/monitoring/`。新しい依存・設定・移行・指標・警報は足していない。

### テスト（`backend/src/test`）

| 置き場 | テスト | 件数 |
|---|---|---|
| `user/domain` | `SearchTextTest`（性質ベース 2）・`ProfileValidationTest` | 10・8 |
| `auth/domain` | `LockViewTest`（性質ベース 1） | 9 |
| `useradmin/domain` | `UserAdminProblemTypesTest` | 2 |
| `user/repository` | `UserAdminQueriesIT` | 9 |
| `auth/repository` | `LoginAttemptStateRepositoryIT`（足した 2） | 10 |
| `user/service` | `UserAccountServiceTest`（足した 8）・`UserAdminAccountIT` | 28・3 |
| `auth/service` | `LockAdministrationServiceTest` | 4 |
| `useradmin/service` | `UserAdminServiceTest`・`UserAdminProblemTypeCatalogTest` | 9・1 |
| `useradmin/web` | `SearchTextConverterTest`・`UserAdminWebTypesTest`・`UserAdminRequestContextResolverTest` | 3・3・5 |
| `useradmin/web` | `UserAdminListApiIT`・`UserAdminListQueryCountIT`・`UserAdminProfileApiIT`・`UserAdminSecretLeakIT` | 8・3・6・2 |
| `useradmin` | `UserAdminBoundaryArchitectureTest` | 6 |
| `useradmin/testsupport` | `UserAdminApi`・`UserAdminFixtures`（テストの手伝い） | — |
| `auth/testsupport` | `SqlStatementCounter` に `recorded()` を足した（G-2） | — |

### 文書

- `README.md` に「利用者の管理の API（Intent 260930-user-admin の U3）」の節を足した（2つの API、行の 11 項目、並びとページ、検索の決まりと q の上限、既知の差 `İ`・`ß`、氏名と言語、監査、秘密と個人情報、8KB を超える要求）。B4 で5つの操作を足す。

## 2. 実測（Step 1 の基準と Step 15 の関門）

どちらも colima の設定（`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`）を渡し、`caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` で測った。

| 項目 | Step 1（変更の前） | Step 15（B3 の関門） | 差 |
|---|---|---|---|
| 結果 | BUILD SUCCESSFUL | BUILD SUCCESSFUL | — |
| 時間（壁時計） | 6 分 23 秒（383 秒） | 6 分 37 秒（397 秒。Gradle の表示 6m 36s） | +14 秒 |
| 単体（`test`） | 1,287 件 | 1,355 件（失敗・誤り・飛ばし 0） | +68 |
| 結合（`integrationTest`） | 587 件 | 620 件（失敗・誤り・飛ばし 0。対象DB のテストも SKIPPED なし） | +33 |
| 画面（Vitest） | 95 ファイル・801 件 | 95 ファイル・801 件 | 0 |
| 全体のカバレッジ | 行 98.8%（5652/5719）・分岐 94.5%（2069/2190） | 行 98.9%（5883/5951）・分岐 94.6%（2156/2280） | — |

パッケージごと（Step 15 の `jacocoTestReport.xml`）:

| パッケージ | 行 | 分岐 | 下限の判定 |
|---|---|---|---|
| `useradmin.web` | 100.0%（67/67） | 100.0%（12/12） | パッケージごと（新しい） |
| `useradmin.service` | 100.0%（40/40） | 100.0%（8/8） | パッケージごと（新しい） |
| `useradmin.domain` | 100.0%（2/2） | 分岐なし | パッケージごと（新しい） |
| `user.domain` | 99.6%（238/239） | 97.8%（135/138） | パッケージごと |
| `user.repository` | 100.0%（7/7） | 分岐なし | パッケージごと（`UserAdminRow` が入り、B3 から計測の対象になった） |
| `user.service` | 99.6%（283/284） | 94.7%（108/114） | パッケージごと |
| `auth.domain` | 98.2%（112/114） | 100.0%（38/38） | パッケージごと |
| `auth.repository` | 100.0%（39/39） | 100.0%（4/4） | パッケージごと |
| `auth.service` | 99.2%（240/242） | 92.1%（70/76） | パッケージごと |
| `audit.domain`・`audit.service` | 99.5%（214/215）・100.0%（148/148） | 97.8%（44/45）・84.4%（27/32） | 変わらない（B3 では手を入れていない） |
| `common.observability`・`common.error.web` | 97.5%（115/118）・96.9%（190/196） | 94.4%（34/36）・88.2%（82/93） | 一覧（全体の合計で判定）。B3 では手を入れていない |

- `./gradlew osvScan --rerun-tasks`: 失敗の条件に当たるもの 0 件、警告 14 件（どれも `vendor/make-you-chic-ui/package-lock.json` の開発用の `brace-expansion`・`undici`）。
- SpotBugs ＋ FindSecBugs・Gitleaks は除外を足さずに通った。`backend/config/spotbugs-exclude.xml`・`.gitleaks.toml`・`backend/gradle.lockfile`・`frontend/package-lock.json` に差は無い。新しいコードの SpotBugs の指摘は priority 2・3 の警告だけ（`EI_EXPOSE_REP2`・`CT_CONSTRUCTOR_THROW`・`SPRING_ENDPOINT`・`SERVLET_HEADER_USER_AGENT`。招待の同じ形の部品と同じ種類）。
- `packagesJudgedByTotal` の一覧のパッケージには手を入れていない（`git diff --name-only develop -- backend/src/main` と突き合わせた）。
- E2E は流していない（Step 14 の条件に当たらない。`generation-notes.md` の Step 14）。

## 3. 主な実装の決定

- **SD-4**: 検索は HQL の `ilike :#{#pattern.value()} escape '\'` のまま受け付けられ、H2 で `ILIKE ... ESCAPE` に写った（native の問い合わせへの切り替えなし）。
- **伏せ字の経路**: 検索の文字は controller の引数の時点から `SearchText`、repository の口には `RedactedText` のパターン、氏名は `ProfileRequest` → `ProfileCommand` → `ProfileUpdate` で受け渡し、文字列にするのは record の中と SpEL の中だけ。`String` を受ける公開のメソッドは、`UserAdminService#list` の page（受け入れた残る危険 R3）と、Bean でない `SearchTextConverter#convert`・`ProfileValidation#validate`（メソッドの呼び出しの追跡の対象外）だけ。
- **一覧の問い合わせの回数**: 1ページの一覧は内部DB へ3回（件数・行・ロックの状態）、最後のページより後は1回（件数だけ）で、行の数で増えない（認証と認可の入口の分を除いて数えた）。
- **Spring Data の repository の追跡**: `UserRepository` のようなインターフェースのメソッドの追跡は、実体のクラス（`SimpleJpaRepository`）の名前のロガーで出るため、`cherry.mastersmith` を TRACE にしただけでは出ない。漏えいのテストは `org.springframework.data.jpa.repository.support` も TRACE にして、repository の口の引数が伏せ字になることまで確かめた。

## 4. 計画・承認済みの文書との差（B3）

| ID | 計画・設計の形 | 実際 | 理由 |
|---|---|---|---|
| G-1 | R1 は作業ブランチの最初のコミット | Plan Approval の記録のコミット 493b4dc は `develop` の上にあり、作業ブランチはそこから作った | 依頼者の側で先に行われていた（依頼者の了承済み） |
| G-2 | 記録した SQL の文で確かめる | テストの手伝い `SqlStatementCounter` に `recorded()` を足した（テストだけの変更） | 文の形を確かめるため（依頼者の了承済み） |
| G-3〜G-8 | 名前・細部 | `generation-notes.md` の表のとおり | 細部 |
| G-9 | `AdminUserPage` の `toString`（計画に無い） | 行の値を出さず件数だけを出す形にした | `AdminUser` と同じく、追跡で行の値が出ないようにするため |
| G-10 | Step 11「どれも監査の管理の操作の行が増えない（403 は既存のアクセスの拒否だけ）」 | 未認証の 401 も既存のアクセスの拒否（理由 `TOKEN_MISSING`）として1行残る。停止中の管理者の 401 は残らない。テストはこの既存の動作を固定した | アクセスの拒否の既存の決まり（`AccessDeniedReason`）のため。管理の操作の行は増えない |
| G-11 | Step 12 の追跡の行 `UserRepository#findAdminRowsBySearch`・`#updateProfile` | 行の名前は `SimpleJpaRepository#findAdminRowsBySearch`・`#updateProfile`。テストの中で `org.springframework.data.jpa.repository.support` のロガーも TRACE にした | 3節。1つの Spring の文脈の中でのレベルの切り替え（D-10）は追跡に効いたため、クラスは分けていない |
| G-12 | Step 11 の 404 の説明文に利用者 ID を載せない | `title`・`detail` には載らない。Problem Details の `instance` には要求のパス（ID を含む）が既存の共通の仕組みで入る | 要求した人が送ったパスそのもので、既存の共通の動作 |
| G-13 | `UserAdminRequestContextResolver` の送り手の情報 | B3 では使わないが、B4 の監査で使うため `origin` を置き、単体テストで確かめた | 招待と同じ形 |
| G-14 | BR2.8（操作した人は要求の文脈から読む） | 氏名と言語の変更は操作した人を使わない（監査・確かめ直し・自分自身の判定が無い）ため読まない | BR5.2・BR5.3 |
| G-15 | 4.5「`user.repository` は B4 で計測の対象になる」 | B3 の `UserAdminRow` で計測の対象になった（100.0%） | Q-A で投影の record を B3 に置いたため |

## 5. 依頼者の決定（計画の 9節のうち B3 に関わるもの）

- Q-A A: 投影の `UserAdminRow` を `user.repository` に置き、`UserAccountService` で `UserAdminSummary` に写した。
- Q-B A: この途中の版を B3 の終わりに書いた。
- Q-E A: `İ`・`ß` の既知の差を `UserAdminQueriesIT#knownDifferences` に固定した。

## 6. 承認の場で確かめること（B3）

- G-10（未認証の 401 がアクセスの拒否として残る既存の動作を、テストで固定したこと）。
- G-11（repository の追跡の確かめのために、テストの中だけ Spring Data のロガーも TRACE にしたこと。本番の設定は変えていない）。
- OSV-Scanner の警告 14 件（`vendor/make-you-chic-ui` の開発用の依存。関門の基準では止めない）。

## 7. Build and Test に引き継ぐこと

計画の「Build and Test に引き継ぐこと」の表のとおり（B4 の後に仕上げる）。B3 の分の値は 2節。

## 8. コミットの区切りの案（B3。依頼者の承認を得てから行う）

計画 3.3 の C1〜C3。メッセージは日本語で、末尾に `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` を付ける。どれも `aidlc/` の下を含まない。

### C1 一覧の検索の問い合わせと値の型（Step 3〜7）

件名の案「B3 利用者の一覧の検索の問い合わせと値の型（SearchText・ProfileUpdate・LockView・UserAdminRow）」

- 本体: `user/domain/SearchText.java`・`ProfileUpdate.java`・`ProfileValidation.java`、`user/repository/UserAdminRow.java`・`UserRepository.java`、`auth/domain/LockView.java`、`auth/repository/LoginAttemptStateRepository.java`、`useradmin/package-info.java`、`useradmin/domain/package-info.java`・`UserAdminProblemTypes.java`
- テスト: `user/domain/SearchTextTest.java`・`ProfileValidationTest.java`、`auth/domain/LockViewTest.java`、`useradmin/domain/UserAdminProblemTypesTest.java`、`user/repository/UserAdminQueriesIT.java`、`auth/repository/LoginAttemptStateRepositoryIT.java`、`auth/testsupport/SqlStatementCounter.java`

### C2 業務処理（Step 8・9）

件名の案「B3 利用者の一覧と氏名・言語の変更の業務処理（UserAccount と Authentication の口、UserAdminService）」

- 本体: `user/service/UserAdminSummary.java`・`UserAdminSlice.java`・`ProfileCommand.java`・`ProfileUpdateResult.java`・`UserAccountService.java`、`auth/service/LockAdministrationService.java`、`useradmin/service/` の6つ（`package-info`・`UserAdminService`・`UserAdminListResult`・`UserAdminPage`・`UserAdminEntry`・`UserAdminProblemTypeCatalog`）
- テスト: `user/service/UserAccountServiceTest.java`・`UserAdminAccountIT.java`、`auth/service/LockAdministrationServiceTest.java`、`useradmin/service/UserAdminServiceTest.java`・`UserAdminProblemTypeCatalogTest.java`

### C3 web・漏えいのテスト・境界テスト・README（Step 10〜14）

件名の案「B3 利用者の管理の API（一覧・氏名と言語の変更）と漏えい・境界のテスト」

- 本体: `useradmin/web/` の9つ（`package-info`・`UserAdminController`・`AdminUser`・`AdminUserPage`・`ProfileRequest`・`SearchTextConverter`・`UserAdminWebConfig`・`UserAdminRequestContextResolver`・`UserAdminFieldErrors`）
- テスト: `useradmin/web/` の7つ（`SearchTextConverterTest`・`UserAdminWebTypesTest`・`UserAdminRequestContextResolverTest`・`UserAdminListApiIT`・`UserAdminListQueryCountIT`・`UserAdminProfileApiIT`・`UserAdminSecretLeakIT`）、`useradmin/testsupport/UserAdminApi.java`・`UserAdminFixtures.java`、`useradmin/UserAdminBoundaryArchitectureTest.java`
- 文書: `README.md`

### `develop` への squash の統合の案

件名「B3 利用者の一覧と氏名・言語の変更（U3 前半）: useradmin の骨組み・一覧と検索・ページ送り・氏名と言語の変更」（計画 3.1）。手順は計画 3.2（`aidlc/` を squash から外す）。
