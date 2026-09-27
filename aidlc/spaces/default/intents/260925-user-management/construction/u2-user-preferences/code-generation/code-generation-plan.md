# Code Generation Plan — U2 利用者のプリファレンスとパスワードの変更（u2-user-preferences）

U2 のコード生成の計画を示す。作るものは、利用者の表への氏名と表示の設定の4列の追加（Flyway の V7）、ログインした利用者が自分の設定を読み書きしパスワードを変える API（契約 C4、新しいパッケージ `cherry.mastersmith.user.web`）、利用者の作成の広げ（契約 C2）、ログインと更新の応答の広げ（契約 C3）、パスワードの変更の監査と監査の表の対象の2列（契約 C8）である。Bolt は B2（`inception/delivery-planning/bolt-plan.md`）。B1（U1 メール）は `develop` に統合済み（コミット 66948a8〜d12a342）。U2 は service の単位で、画面を持たない。

この計画の中のパスは、特に断らない限りリポジトリのルートからの相対パスで、記録のディレクトリは `aidlc/spaces/default/intents/260925-user-management/`（以下「記録」）とする。

## 1. 入力にした設計

| 文書（記録の下） | 使うところ |
|---|---|
| `construction/u2-user-preferences/functional-design/functional-spec.md`・`rules.md`・`entities.md`・`functional-design-questions.md` | 状態の移り変わり、手順（2.1〜2.6）、失敗の場合の表、決まり BR1.1〜BR9.2、答え Q1 A・Q2 B・Q3 A・Q4 A、上流との差 D1〜D3、承認の場の Request Changes の直し（7.2） |
| `construction/u2-user-preferences/nfr-requirements/security-requirements.md`・`performance-requirements.md`・`reliability-requirements.md`・`scalability-requirements.md`・`observability-requirements.md`・`tech-stack-decisions.md`・`nfr-requirements-questions.md` | NFR2.1〜NFR2.4、NFR4.1〜NFR4.5、NFR5.1〜NFR5.3、NFR6.1〜NFR6.9、NFR8.1、NFR9.1〜NFR9.8、NFR10.1〜NFR10.5、答え Q1 A（変更の成功は p95 2 秒）・Q2 A（今のパスワードの誤りを制限しない、残る危険 R1）、新しい依存を足さないこと |
| `construction/u2-user-preferences/nfr-design/logical-components.md`・`security-design.md`・`performance-design.md`・`reliability-design.md`・`observability-design.md`・`scalability-design.md`・`nfr-design-questions.md` | 部品の一覧と置き場、本人の利用者 ID と送り手の情報の取り方（Q1 A）、`fieldErrors` の形と reason の一覧（Q2 A）、条件つきの更新（Q3 A）、V7 の作りと確かめの2段（Q4 A）、トランザクションの境界、監査の組み立て、ログの出し方、上流との差 S-D1〜S-D3・P-D1・R-D1・R-D2 |
| `construction/u2-user-preferences/infrastructure-design/infrastructure-specification.md`・`cicd-pipeline.md`・`monitoring-design.md`・`infrastructure-design-questions.md` | verify の段への入り方、V7 の自動の確かめ、`packagesJudgedByTotal` から外す作業、負荷の試験への渡し方、README に足すこと、戻し方の注意（Q2 A）、戻しの練習（Q1 A）、上流との差 I-D1〜I-D4・C-D1・M-D1・M-D2 |
| `inception/contract-design/contract-summary.md` の C2・C3・C4・C8 とエラーの code の一覧 | `createUser` の形（NewUser・結果の型）、`CurrentUserResponse` の4つの項目、`/api/me/` の3本の形、監査の列と値、`PASSWORD_CURRENT_MISMATCH`（400） |
| `inception/domain-design/components.md`・`decisions.md` | UserAccount・Authentication・AuditLog の持ち物、ADR-003（氏名とプリファレンスは利用者の表の列）・ADR-004（パスワードの変更は UserAccount）・ADR-005（Accept-Language）・ADR-008（監査） |
| `inception/units-generation/unit-of-work.md`・`unit-of-work-story-map.md` | U2 の責務と境界、US4.1・US5.1 が主、US3.2 に関わる、CR3〜CR5 |
| `inception/requirements-analysis/requirements.md`・`inception/user-stories/stories.md` | FR5.1〜FR5.5・FR6.1〜FR6.3・FR9・FR10.2、NFR2・NFR4・NFR5・NFR6・NFR8・NFR9・NFR10、US4.1 の AC4.1.1〜AC4.1.13、US5.1 の AC5.1.1〜AC5.1.9 |
| `inception/delivery-planning/bolt-plan.md` の B2 | 完了の条件、統合の形（squash） |

## 2. 承認の場の決定と、この計画での読み方

### 2.1 承認の場の決定（監査ログから洗い出したもの）

`project.md` の学び（計画の前に承認の場の決定を洗い出す）に従い、監査ログ（`audit/sakura-local-4e42a93f87ce.md`）の Functional Design・NFR Requirements・NFR Design・Infrastructure Design の `DECISION_RECORDED`（承認の場の決定）・`GATE_APPROVED`・`GATE_REJECTED` と、レビューの記録（`.aidlc-reviews/*/units/u2-user-preferences/*/1.json`）の指摘を読んだ。U2 と B2 に関わるものと、この計画での扱いは次のとおり。

| 段 | 決定・指摘 | この計画での扱い | 手順 |
|---|---|---|---|
| 機能設計（Request Changes の R-01） | `/api/me/` の3本には 403 の場面が無い。確かめるのは未認証の 401 と、管理者でないログインした利用者の 200（パスワードの変更は 204） | 結合テストで 401（トークンなし・改ざん）と、管理者でない利用者の 200・204 を確かめる。403 のテストは書かない（BR8.1、NFR4.1・NFR4.2） | Step 17 |
| 機能設計（Request Changes の R-02） | 表示に関わる受け入れ基準は、サーバーの応答・保存で満たせる部分だけを U2 が OK、画面の表示は画面の単位へ Deferred | `traceability.json` を機能設計の分け方（AC4.1.7・AC4.1.13・AC5.1.1〜AC5.1.6 が OK、ほかは u4・u7 へ Deferred）にそろえる | Step 22 |
| 機能設計（Request Changes の R-03）・機能設計の承認の場の G6 | 契約 C8 の PASSWORD_CHANGED に target_user_id を足す差の反映を、遅くともコード生成の計画で確かめる | 実装は target_user_id に本人を記録する（BR7.2）。契約の文書は書き換えず、差を `code-summary.md` と README に記録する（9節の決定 2） | Step 12・13・20・22 |
| NFR 設計（`logical-components.md` 2節） | 契約 C4（と U3 の C6）への `fieldErrors` の反映を、遅くとも U2 のコード生成の計画で確かめる | 実装は `security-design.md` 3節の形で返す。契約の文書は書き換えず、差を `code-summary.md` と README に記録する（9節の決定 2） | Step 4・16・17・20・22 |
| NFR 設計（承認の場、U2 R-01） | B2 のテストに、本人の行が消えた場合の 401 を明示して入れる | 業務処理が「本人がいない」を結果の型で返し `user.web` が 401 にする単体テストと、トークンが有効なまま本人の行を消した結合テスト（401 になる）を入れる | Step 15・17 |
| NFR 設計（承認の場、U2 R-02） | B2 の計画に、Hibernate の `validate` が余分な列を許す見込みが外れたときの危険と扱いを書く | 確かめ (2)（戻しの練習）は統合の後になる。統合の前は既知の制約として `code-summary.md` に記録し、外れたと分かった時点で速やかに依頼者に諮る（8節、Build and Test に引き継ぐこと） | 8節 |
| 基盤の設計（承認の場、D4） | 戻しの手順に、初期管理者のメールアドレスを変えないことを書く | README の「戻し方」に注意を2点（メールアドレスを変えない、空の内部DB では V7 を当てていない内部DB を使う）を足す。戻しの練習の手順の書き起こしは deployment-pipeline のまま | Step 20 |
| 基盤の設計（承認の場、D5） | 指標の名前は手元の監視では `_milliseconds_` | U2 は指標を足さない。名前の確かめは observability-setup に引き継ぐ | Build and Test に引き継ぐこと |
| 基盤の設計（承認の場、N1） | `perf/README.md` の仮の利用者の SQL に display_name を足す（B2） | 手順 2 の SQL に `display_name`（メールアドレスと同じ値）を足す。V7 の後にこの手順が失敗しないため | Step 20 |
| NFR 設計（Q4 A）・基盤の設計（`cicd-pipeline.md` 2節） | V7 の後方互換の自動の確かめ（V6 までしか知らない Flyway が V7 の後の DB で失敗しないこと）を B2 で行い、見込みが外れたら計画の中で依頼者に諮る | 結合テストで確かめる。外れたら生成を止めて依頼者に諮る（9節の決定 4） | Step 7 |
| 機能設計（`functional-spec.md` 6節）・基盤の設計（`cicd-pipeline.md` 3節） | 手を入れる既存のパッケージを `packagesJudgedByTotal` から外し、パッケージごとの下限を満たす。既存の `AuditSecretLeakIT` の列の一覧に V7 の2列を足す | Step 19 で外して実測・記録する。`AuditSecretLeakIT` の列の一覧を直す | Step 17・19 |
| NFR 要件（Q1 A・Q2 A） | パスワードの変更の成功は p95 2 秒、今のパスワードの誤りの回数を制限しない（残る危険 R1） | 制限の仕組みを作らない。誤りを6回以上続けても正しい今のパスワードで変えられ、ログインのロックの状態が変わらないことを結合テストで確かめる。性能は performance-validation に引き継ぐ | Step 17、Build and Test に引き継ぐこと |

### 2.2 この計画での読み方

- **設計の文書どおりに作るもの**: 部品の構成と置き場（`logical-components.md` 1節）、本人の利用者 ID と送り手の情報の取り方（`security-design.md` 2節）、`fieldErrors` の形と reason の一覧（同 3節）、パスワードの変更の流れ（`performance-design.md` 2節）、条件つきの更新と部分の書き換え（`reliability-design.md` 2節・3節）、`createUser` の巻き戻しの印（同 4節）、V7 の中の順序（同 6.1）、監査の組み立てと受け取り（同 1節）、ログの出し方（`observability-design.md` 2節）。
- **列の名前**（基盤の設計で「コード生成で決める」とされたもの）: `users` の `display_name`（VARCHAR(508)、UTF-16 の単位で 254 コードポイントの2倍）・`language`（VARCHAR(2)、既定 `'ja'`）・`theme`（VARCHAR(6)、既定 `'system'`）・`font_size`（VARCHAR(2)、既定 `'md'`）、`audit_events` の `target_user_id`・`target_invitation_id`（BIGINT、空を許す）。ファイル名は既存に合わせて `V7__u2_user_preferences.sql`。値の検査の CHECK の制約は設計に無いため置かない（決まりは業務処理の純粋な関数で守る）。
- **言語・テーマ・文字の大きさの Java の型**: `user.domain` に列挙 `Language`（ja・en）・`Theme`（light・dark・system）・`FontSize`（sm・md・lg）を置き、小文字の文字列の完全な一致だけで値を返す関数を持たせる（BR2.1）。表の列とは JPA の `AttributeConverter` で小文字の文字列として対応づける。要求の DTO では文字列で受ける（`security-design.md` 3節。列挙で受けると `EN` などが MALFORMED_REQUEST になるため）。TypeScript の「`enum` を使わない」決まりは Java には当たらない。
- **利用者の要約**: `UserSummary` に displayName・language・theme・fontSize を文字列で足す（`auth` は `user.domain` のエンティティに依存できないため、要約は文字列で渡す）。文字列にするときはメールアドレスを伏せる（既存の `TraceAspect` が業務処理の戻り値を TRACE のログに文字列で出すため。`project.md` の Forbidden のメールアドレス、`security-design.md` 4節）。
- **既存の ArchUnit を緩めない**: `ArchitectureTest` と既存の機能ごとの境界テスト（`AuthBoundaryArchitectureTest` の「user does not depend on auth」「auth and user do not depend on audit」「only classes inside user read the password hash of User」、`AuditBoundaryArchitectureTest` の追記だけの決まりなど）は変えずに通す（`team.md` の Code Style）。依存の向きは `audit` → `user.domain`、`auth` → `user.service` だけ。
- **認可の設定を変えない**: `/api/me/` は既存の `/api/` の既定のログイン必須に乗る。新しい `SecurityRuleContributor`・Origin の確かめ・CSRF の設定を足さない（NFR4.4）。

## 3. 作業の場とコミットの区切り

- **作業のブランチ**: `develop` の先頭から短命のブランチ `feature/260925-user-management-b2` を作る（`team.md` の Way of Working。9節の決定 1）。
- **統合の形**: B2 はサブモジュールの固定先の更新を含まないため、`develop` へ **squash** で統合する（1 Bolt が `develop` の1コミット）。統合コミットのメッセージは日本語で、Bolt の内容が分かる件名にする（例: 「B2 利用者の設定（氏名とプリファレンス・パスワードの変更・監査の対象の列）」）。統合の前に `./gradlew verify` を通す（Step 21）。
- **コミット**: 生成の担当はコミットしない。生成の後に、依頼者の承認を得て、次の区切りでまとめてコミットする（`project.md` の Change Control の学び）。メッセージは日本語。

| 区切り | 中身 | 手順 |
|---|---|---|
| C1 | スキーマの変更 `backend/src/main/resources/db/migration/V7__u2_user_preferences.sql` と、V7 の確かめに使う V1〜V6 の複写（テストの資源） | Step 6・7 |
| C2 | `user`（domain・repository・service・web）・`audit`（domain・service）・`auth`（web）の本番のコード | Step 2・4・6・8・10・12・14・16 |
| C3 | テスト（単体・結合・テストの支え。既存のテストの直しを含む） | Step 3・5・7・9・11・13・15・17・18 |
| C4 | ビルドの設定（`backend/build.gradle.kts` の `packagesJudgedByTotal` から外す） | Step 19 |
| C5 | 文書（`README.md`・`perf/README.md`） | Step 20 |
| C6 | この段の記録（記録の `construction/u2-user-preferences/code-generation/` の下） | Step 22 |

- `origin` への `git push` は依頼者が行う。AI はプッシュしない。

## 4. 作るもの・手を入れるもの

| パッケージ | 部品 | 新しい・手を入れる | 役割 |
|---|---|---|---|
| `user.domain` | `DisplayName`（純粋な関数と値。前後の White_Space を除く・空・254 コードポイント・Cc と Cf の拒否・正規化しない、理由の種類を返す） | 新しい | BR1.1〜BR1.6、NFR9.2 |
| `user.domain` | `Language`・`Theme`・`FontSize`（列挙と完全な一致の関数）、JPA の `AttributeConverter` 3つ | 新しい | BR2.1、表の列との対応 |
| `user.domain` | `FieldError`（record: field・reason）・`FieldErrorReason`（REQUIRED・TOO_SHORT・TOO_LONG・INVALID_CHARACTER・INVALID_VALUE・MISMATCH）、`PreferencesValidation`・`PasswordChangeValidation`（誤りを要求の項目の順ですべて集め、1つの項目に最初の理由だけ） | 新しい | `security-design.md` 3節、BR3.2・BR4.1。U3 も使う |
| `user.domain` | `Preferences`（4つの組）、`RequestOrigin`（sourceIp・userAgent（512 文字で切る）・traceId）、`PasswordChangedEvent`（record: userId・result・failureReason・occurredAt・sourceIp・userAgent・traceId。パスワード・ハッシュ・メールアドレスを持たない） | 新しい | `entities.md`、`security-design.md` 2節、`reliability-design.md` 1節 |
| `user.domain` | `UserProblemTypes`（PASSWORD_CURRENT_MISMATCH、400、ja・en） | 新しい | BR8.2、NFR8.1 |
| `user.domain` | `User`（4列、プリファレンスの読み取り） | 手を入れる | BR9.1、ADR-003 |
| `user.repository` | `UserRepository` に、4列だけを書き換える更新と、読んだハッシュのままなら password_hash だけを書き換える条件つきの更新（`@Modifying(clearAutomatically = true)` の JPQL、名前つきの引数） | 手を入れる | BR3.4、`reliability-design.md` 2節・3節、NFR9.3（`SQL_` を出さない） |
| `user.service` | `UserAccountService`（`UserSummary` に4つ、`createUser(NewUser)` と結果の型 `CreateUserResult`（`Created`・`EmailAlreadyUsed`）、bcrypt の前の登録済みの確かめ、一意の制約の受け止め、巻き戻しの印、決まりに合わない値は例外。`findDisplayName`・`findLanguage`） | 手を入れる | C2、BR5.1〜BR5.5 |
| `user.service` | `NewUser`（record、文字列にするとメールアドレスとパスワードを伏せる）・`CreateUserResult`（sealed）・`UserSummary`（4つを足す、文字列にするとメールアドレスを伏せる） | 新しい・手を入れる | C2、`entities.md` |
| `user.service` | `InitialAdminInitializer`（初期値つきの `NewUser` で `createUser` を呼び、結果の型で判定する。`DataIntegrityViolationException` の受け止めを結果の型の判定に置き換える） | 手を入れる | BR5.4、`reliability-design.md` 4節 |
| `user.service` | `UserPreferencesService`（取得・保存・パスワードの変更。`TransactionTemplate` で短いトランザクション、bcrypt はトランザクションの外、結果の型 `PreferencesResult`・`PasswordChangeResult`）、`PasswordChangeBarrier`（照合の後・書き込みの前に呼ぶ待ち合わせの口。本番は何もしない既定の Bean、テストで差し替える） | 新しい | BR3.1〜BR3.5、BR4.1〜BR4.5、NFR5.1、`performance-design.md` 2節・3節、`reliability-design.md` 2節 |
| `user.service` | `UserProblemTypeCatalog`（`UserProblemTypes` の登録） | 新しい | BR8.2 |
| `user.web` | `MeController`（GET・PUT `/api/me/preferences`、POST `/api/me/password`）、`PreferencesRequest`・`PreferencesResponse`・`PasswordChangeRequest`（record、項目はすべて文字列、`PasswordChangeRequest` の文字列はパスワードの3項目を伏せる）、`MeRequestContextResolver`（`Authentication#getName()` から本人の利用者 ID、要求から `RequestOrigin`。AUTHENTICATION_REQUIRED を起動時に `ProblemTypeRegistry.findByCode` で引き、無ければ起動を止める） | 新しい | C4、`security-design.md` 1節〜3節 |
| `audit.domain` | `AuditEvent`（targetUserId・targetInvitationId）、`AuditEventType.PASSWORD_CHANGED`、`AuditFailureReason.CURRENT_PASSWORD_MISMATCH`、`AuditEventFactory.from(PasswordChangedEvent)` | 手を入れる | C8、BR7.1〜BR7.5 |
| `audit.service` | `AuditEventListener`（`onPasswordChangedEvent` を足す。失敗の ERROR の項目に targetUserId・targetInvitationId を足す） | 手を入れる | BR7.3、NFR9.4、`observability-design.md` 2節 |
| `auth.web` | `CurrentUserResponse`（displayName・language・theme・fontSize を足す）・`TokenResponse`（`UserSummary` から写す）、`AuthenticatedUserToken`（`getName()` を `user.web` が利用者 ID として読むことをコメントに書き足す。コメントだけ） | 手を入れる | C3、BR6.1、`security-design.md` 2節 |
| `db/migration` | `V7__u2_user_preferences.sql` | 新しい | BR9.1・BR9.2、`reliability-design.md` 6.1 |

`auth.service`（`LoginService`・`TokenRefreshService`・`IssuedTokens`）は `UserSummary` を受け渡すだけで、コードの変更は要らない見込み。設計のとおりパッケージごとの下限の対象に戻す（8節）。

## 5. 手順

各層で実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む（test-after、Testing Contract の `ordering`）。U2 の層は「ドメイン（値の型と決まり）→ データの形（V7・エンティティ）→ DB アクセス → 業務処理（利用者の作成 → 監査の記録 → プリファレンスとパスワードの変更）→ API」の順とする。画面は持たない。

### Step 1: 作業の場の用意と、変更の前の基準（依頼者が承認した git の操作。9節の決定 1）

- [x] `develop` の先頭のハッシュを記録し、`develop` から短命のブランチ `feature/260925-user-management-b2` を作る（コミットはしない。統合は squash）
- [x] 変更の前の基準をとる: colima が動いていることを確かめ、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡して `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行し、テストの件数（単体・結合、失敗・飛ばした）と、全体のカバレッジと、Step 19 で外す予定のパッケージ（`user.domain`・`user.repository`・`user.service`・`audit.domain`・`audit.repository`・`audit.service`・`auth.service`・`auth.web`）の行・分岐のカバレッジを記録する（brownfield の Test Baseline、`project.md` の Testing Posture）
- [x] 対応: B2 の共通の完了の条件、NFR9.6（外す前の値）

### Step 2: 骨組み

- [x] `backend/src/main/java/cherry/mastersmith/user/web/package-info.java`（日本語の説明、ライセンスヘッダー）を作る
- [x] `user` と `user.service`・`user.domain` の `package-info.java` の説明に、プリファレンスとパスワードの変更、画面入出力の層 `user.web` を足す
- [x] 設定の値（`application.yaml`）と環境変数は足さない（基盤の設計の 1節。`.env.example` は変わらない）
- [x] 対応: `logical-components.md` 1節、`infrastructure-specification.md` 1節

### Step 3: テストの実行の準備（最初のテストより前）

- [x] `unit-test-instructions.md` の単体のコマンド `./gradlew :backend:test --tests 'cherry.mastersmith.user.*' --tests 'cherry.mastersmith.audit.*' --tests 'cherry.mastersmith.auth.*' --tests 'cherry.mastersmith.ArchitectureTest'` が、変更の前の状態で動き、既存のテストが通ることを確かめる
- [x] 結合のコマンド `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.user.*' --tests 'cherry.mastersmith.audit.*' --tests 'cherry.mastersmith.auth.*'` が動き、既存のテストが通ることを確かめる（U2 の結合テストは組み込みの H2 だけを使い、コンテナを使わない）
- [x] `backend/src/test/resources/junit-platform.properties` の jqwik の設定（失敗した例の記録、種の再現）がそのまま使えることを確かめる
- [x] 対応: Testing Contract の `runner_step`

### Step 4: ドメインの値の型と決まり（user.domain・audit.domain の値）— 実装

- [x] `DisplayName`: 前後から Unicode の White_Space の文字を除き（内側は除かない・まとめない）、除いた後が空なら REQUIRED、コードポイントで 254 を超えれば TOO_LONG、Cc・Cf の文字があれば INVALID_CHARACTER。正規化しない。null は REQUIRED。判定の結果（通った値か理由の種類）を返す純粋な関数（BR1.1〜BR1.6）
- [x] `Language`・`Theme`・`FontSize`: 小文字の文字列の完全な一致だけで値を返す（大文字・前後の空白・空・null は INVALID_VALUE、無い値は REQUIRED）。小文字の文字列を返す（BR2.1）
- [x] `FieldError`・`FieldErrorReason`、`PreferencesValidation`（displayName・language・theme・fontSize の順）、`PasswordChangeValidation`（currentPassword の空 → REQUIRED、newPassword は既存の `PasswordPolicy`（12 コードポイント未満 → TOO_SHORT、UTF-8 で 72 バイトを超える → TOO_LONG、空 → REQUIRED）、newPasswordConfirmation の不一致 → MISMATCH（正規化・前後の除去なしの完全な一致））。今のパスワードの長さは入力の誤りにしない（`security-design.md` 5節）。項目の名前は契約 C4 の項目名そのまま
- [x] `Preferences`、`RequestOrigin`（User-Agent を 512 文字で切る）、`PasswordChangedEvent`（成功・失敗の作り方、FAILURE のときだけ理由、文字列にしても秘密が出ない）
- [x] `UserProblemTypes.PASSWORD_CURRENT_MISMATCH`（400、ja・en の説明。既存の `AuthProblemTypes` と同じ形）
- [x] `audit.domain`: `AuditEventType.PASSWORD_CHANGED`、`AuditFailureReason.CURRENT_PASSWORD_MISMATCH` を足す（BR7.5: 32 文字に収まる）
- [x] 対応: US4.1（AC4.1.7）、US5.1（AC5.1.3）、BR1.1〜BR1.6・BR2.1・BR3.2・BR4.1・BR7.1・BR7.5・BR8.2・BR8.4、NFR8.1・NFR9.1・NFR9.2、契約 C4・C8

### Step 5: ドメインの値の型と決まり — テスト（単体・性質ベース）

- [x] `DisplayNameTest`: 前後の半角・全角の空白・タブ・U+00A0 を除く、内側の空白は残る、空・空白だけ・null → REQUIRED、254 コードポイントちょうどは通り 255 は TOO_LONG（絵文字 254 文字も通る）、内側の改行・タブ・NUL・U+200B・U+200D・U+202E・U+FEFF → INVALID_CHARACTER、NFC に変えない
- [x] `DisplayName` の性質ベースのテスト（jqwik、失敗時の種を記録）: 通った値は前後に White_Space が無く、1〜254 コードポイントで、Cc・Cf を含まない。同じ入力を2回判定すると同じ結果（冪等）。通った値をもう一度判定しても同じ値
- [x] `PreferenceValuesTest`（`Language`・`Theme`・`FontSize`）: 決めた値はすべて通る、`EN`・`Dark`・`" md"`・空・null・知らない値は拒否。性質ベースのテスト: 決めた値以外の任意の文字列はいつも拒否
- [x] `PreferencesValidationTest`: 誤りを項目の順ですべて集める、1つの項目に1つの理由、誤りが無ければ空、値を理由に含めない
- [x] `PasswordChangeValidationTest`: AC3.2.4 と同じ一覧（11 文字 → TOO_SHORT・12 文字は通る・空 → REQUIRED・72 バイトは通る・73 バイト → TOO_LONG・絵文字 11 文字 → TOO_SHORT と 12 文字は通る・2回の入力の不一致 → MISMATCH）、今のパスワードの空 → REQUIRED、今のパスワードが 73 バイトでも入力の誤りにならない、確かめの値の前後の空白で MISMATCH。性質ベースのテスト（jqwik）: 新しいパスワードの判定が `PasswordPolicy.isAcceptableForCreation` と一致する、確かめが新しいパスワードと同じなら MISMATCH にならない（NFR9.1）
- [x] `RequestOriginTest`（513 文字の User-Agent が 512 文字になる、null を許す）、`PasswordChangedEventTest`（SUCCESS に理由を付けられない・FAILURE は理由が要る、文字列にパスワード・ハッシュ・メールアドレスが無い）、`UserProblemTypesTest`（code・400・ja・en の説明が空でない）
- [x] 監査の値の長さの確かめ: `AuditEventType`・`AuditFailureReason` のすべての値が 32 文字以内（既存の `AuditEventTest` か新しいテストに足す。BR7.5）
- [x] 単体のコマンドを実行して通す

### Step 6: データの形（V7・エンティティ）— 実装

- [x] `V7__u2_user_preferences.sql`（先頭にライセンスヘッダーのコメント、既存の V1〜V6 と同じ形）を `reliability-design.md` 6.1 の順で書く: (1) `users` に `language`・`theme`・`font_size` を既定の値つき・必須で足す、(2) `display_name` を空を許す VARCHAR(508) で足す、(3) 既存の行の `display_name` に `email` を入れる、(4) `display_name` を必須にする（既定の値なし）、(5) `audit_events` に `target_user_id`・`target_invitation_id` を空を許す BIGINT で足す（参照の制約と索引なし）。V1〜V6 は変えない
- [x] `User`: 4列の対応（`AttributeConverter` で小文字の文字列）と読み取りの口、作るときの引数に4つを足す。パスワードのハッシュの読み取りの決まり（`user` の中だけ）は変えない
- [x] `AuditEvent`: targetUserId・targetInvitationId の列と読み取りの口、作るときの引数（既存の出来事は空のまま）
- [x] 対応: US4.1（AC4.1.10 のサーバーの部分）、BR2.2・BR9.1・BR9.2、NFR10.1・NFR10.2、契約 C8

### Step 7: データの形 — テスト（結合、V7 の後方互換の自動の確かめ）

- [x] V1〜V6 の移行ファイルの複写をテストの資源 `backend/src/test/resources/db/migration-through-v6/` に置く。複写が本番の V1〜V6 と1バイトも違わないことをテストの中で確かめる（片方だけの変更を見逃さないため）
- [x] `V7MigrationIT`（Spring を起動しない。組み込みの H2 の一時のファイルに Flyway の API で当てる）: V6 まで当てた DB に利用者2人と監査の行を入れてから V7 を当てると、利用者に display_name＝email・ja・system・md が入り、監査の2列は空のまま。もう一度 migrate しても V7 は1回だけ当たる（NFR10.2）
- [x] `V7BackwardCompatibilityIT`（同上）: 今の Flyway で V7 まで当てた DB に、V6 までの複写だけを知る Flyway の `validate` と `migrate` が失敗しない（Flyway 12.4.0 の既定が、適用済みで知らない新しい移行を無視することの確かめ）。既知の限界の固定: 4列を渡さない `users` の追記は display_name が無いため失敗し、`language`・`theme`・`font_size` を渡さず display_name を渡した追記は既定の値で入る、2列を渡さない `audit_events` の追記は通る（NFR10.3・NFR10.4、`reliability-design.md` 6.2 の (1)）
  - **見込みが外れたとき**（V6 までの Flyway の `validate` か `migrate` が失敗する）: この手順で生成を止め、失敗の内容と作りの候補（例: 1つ前の版に当たる設定 `spring.flyway.ignore-migration-patterns` を今の版から入れる、など）を示して依頼者に諮る（承認済みの BR9.1・NFR10.3 との差になるため。9節の決定 4）
- [x] 既存の `UserSchemaIT` に4列（必須・既定の値・長さ）を、既存の `AuditSchemaIT` に2列（空を許す）を足す。Hibernate の `validate` で起動できることは既存の起動のテストで確かめる
- [x] 単位の結合のコマンドを実行して通す

### Step 8: DB アクセス（UserRepository）— 実装

- [x] プリファレンスの4列だけを書き換える更新（本人の ID で、書き換えた行の数を返す）
- [x] 読んだときのハッシュのままなら password_hash だけを書き換える条件つきの更新（`UPDATE ... SET passwordHash = :newHash WHERE userId = :userId AND passwordHash = :readHash` に当たる JPQL、書き換えた行の数を返す）
- [x] どちらも `@Modifying(clearAutomatically = true)`、名前つきの引数（文字列の連結で組み立てない）。エンティティの変更の検出では書き換えない（全列を書くため）
- [x] 対応: BR3.3・BR3.4・BR4.3、NFR9.8、`reliability-design.md` 2節・3節、NFR9.3

### Step 9: DB アクセス — テスト（結合）

- [x] 既存の `UserRepositoryIT` に足す: 4列の更新で email・password_hash・admin_flag・created_at が変わらない、条件つきの更新は読んだハッシュのとき1行・違うハッシュのとき0行でハッシュが変わらない、パスワードの更新で4列が変わらない、存在しない利用者は0行、更新の後に同じトランザクションで読むと新しい値（持続化の文脈が消えている）
- [x] 単位の結合のコマンドを実行して通す

### Step 10: 業務処理 — 利用者の作成と要約（UserAccountService・InitialAdminInitializer・UserProblemTypeCatalog）— 実装

- [x] `UserSummary` に displayName・language・theme・fontSize を足し、`toSummary` で写す。文字列にするとメールアドレスを伏せる
- [x] `createUser(NewUser)`: 決まりに合わない値（`DisplayName`・`Language`・`Theme`・`FontSize`・`PasswordPolicy`）は作らずに例外（BR5.1）。メールアドレスをそろえ、bcrypt の前に登録済みかを読み取り1回で確かめ、登録済みなら今のトランザクションに巻き戻しの印を付けて `EmailAlreadyUsed` を返す。一意の制約（`uk_users_email`）に当たったときも受け止め、巻き戻しの印を付けて `EmailAlreadyUsed` を返す。作れたら同じトランザクションで `UserCreatedEvent` を知らせ `Created(userId)` を返す。呼び出し元のトランザクションに参加する（無ければ新しく始める）。呼び出し元は `EmailAlreadyUsed` のとき必ず巻き戻す前提を Javadoc に書く（BR5.2・BR5.3、`reliability-design.md` 4節、`performance-design.md` 5節）
- [x] `findDisplayName`・`findLanguage`（契約 C2。いなければ空）、`existsByEmail`（既存のまま）
- [x] `InitialAdminInitializer`: admin true・氏名＝そろえたメールアドレス・ja・system・md の `NewUser` で `createUser` を呼び、`EmailAlreadyUsed` のとき既存の「初期管理者は既にいるため、作成しませんでした」のログを出す。設定の点検と既存の文は変えない（BR5.4）。既存のログの項目（キー `email` を含む）は変えない（依頼者の判断で据え置き。9節の決定 3、8節）
- [x] `UserProblemTypeCatalog`（`ProblemTypeCatalog` の Bean）
- [x] 対応: US3.2 に関わる（C2）、BR2.2・BR5.1〜BR5.5・BR8.2、契約 C2・C3

### Step 11: 業務処理 — 利用者の作成と要約 — テスト（単体・結合）

- [x] 既存の `UserAccountServiceTest` を直して足す: 要約の項目（`userId`・`email`・`admin`・`displayName`・`language`・`theme`・`fontSize`、ハッシュを含まない）、要約の文字列にメールアドレスが無い、`createUser` がハッシュを保存し `UserCreatedEvent` を1回知らせる、登録済みなら bcrypt を呼ばず `EmailAlreadyUsed`、決まりに合わない氏名・値・パスワードで例外（作らない・知らせない）、`NewUser` の文字列にメールアドレスとパスワードが無い
- [x] `UserCreationIT`（結合）: 呼び出し元のトランザクションに参加し、呼び出し元が巻き戻すと利用者もロックの状態の行も残らない。自分でトランザクションを始めた `createUser` が `EmailAlreadyUsed` のとき `UnexpectedRollbackException` にならない。同じメールアドレスの2つの作成を重ねる（テストの中の待ち合わせで重なりを確実に作り、`sleep` に頼らない）と片方が `Created`・片方が `EmailAlreadyUsed` で、利用者1人・ロックの状態の行1つ（BR5.2・BR5.3）
- [x] 既存の `InitialAdminInitializerTest`・`InitialAdminIT` を直して足す: 作られた初期管理者の氏名＝メールアドレス・ja・system・md・admin true、2回目の起動で重ねて作らない、設定が無い・不正のときの既存の動き、パスワードがログに出ない
- [x] `UserProblemTypeCatalogTest`（1つの code に1つの状態コード、既存の重複の検査で起動できる）
- [x] 単位の単体・結合のコマンドを実行して通す

### Step 12: 業務処理 — 監査の記録（AuditEventFactory・AuditEventListener）— 実装

- [x] `AuditEventFactory.from(PasswordChangedEvent)`: eventType PASSWORD_CHANGED、result、FAILURE のとき failureReason CURRENT_PASSWORD_MISMATCH、actorUserId と targetUserId に本人、sourceIp・userAgent・traceId・occurredAt は出来事の値、enteredEmail・requestPath・targetInvitationId・DSL の列は空（BR7.2、`reliability-design.md` 1節）
- [x] `AuditEventListener.onPasswordChangedEvent`: 既存の3つと同じ設定（`@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)`、最優先の順番）。失敗は既存のとおり受け止めて ERROR を1回、元の応答に影響させない（BR7.3、NFR9.4）
- [x] 失敗の ERROR の項目に targetUserId・targetInvitationId を足す（記録しようとした全項目を載せる既存の決まり）。PASSWORD_CHANGED の失敗で DSL の項目が空で並ぶ組み立ては、actorUserId と DSL の項目を分けて整理してよい（`observability-design.md` 2節で「コード生成に任せる」とされた）。既存の出来事の ERROR の項目と既存のテストの期待は変えない。既存の `enteredEmail` の項目も残し、`AuditWriteFailureIT` の期待も変えない（依頼者の判断で据え置き。9節の決定 3、8節）
- [x] 対応: CR3、BR7.1〜BR7.4、NFR9.4・NFR9.5、契約 C8

### Step 13: 業務処理 — 監査の記録 — テスト（単体）

- [x] 既存の `AuditEventFactoryTest` に足す: 成功・失敗の組み立て、actorUserId と targetUserId が本人、空の列、既存の出来事の組み立てで2列が空
- [x] 既存の `AuditEventListenerTest` に足す: PasswordChangedEvent を受けて1件記録する、記録の失敗で ERROR が1回・例外が伝わらない・項目に targetUserId があり、パスワード・メールアドレスの項目が無い、遅い書き込みの WARN
- [x] `audit.service` の既存の分岐（組み立ての失敗、遅い書き込み、項目の作り方）で足りないテストを足し、単独で行 80%・分岐 70% に届かせる（前の記録で行 77.2%。NFR9.6）
- [x] 単位の単体のコマンドを実行して通す

### Step 14: 業務処理 — プリファレンスとパスワードの変更（UserPreferencesService）— 実装

- [x] 取得: 読み取り専用の短いトランザクションで本人を読み、4つを返す。いなければ「本人がいない」（BR3.1）
- [x] 保存: `PreferencesValidation` で4つを DB の外で検証し、誤りがあれば DB に触れずに「入力の誤り（項目ごとの誤り）」。通れば短いトランザクションで4列の更新を1回。0行なら「本人がいない」。検証を通った値（氏名は除いた後の値）を返す。監査の出来事を出さない（BR3.2〜BR3.5、`performance-design.md` 3節）
- [x] パスワードの変更（`performance-design.md` 2節の流れ）: 入力の検証（誤りなら照合も監査もしない）→ 短い読み取りのトランザクションでハッシュを読む（いなければ「本人がいない」）→ トランザクションの外で、72 バイトを超える今のパスワードは照合に渡さず不一致、そうでなければ bcrypt で照合 → 不一致ならトランザクションの外で PasswordChangedEvent（FAILURE）を知らせ「今のパスワードの誤り」→ トランザクションの外で新しいハッシュを作る → `PasswordChangeBarrier` を呼ぶ → 短いトランザクションで条件つきの更新。1行なら同じトランザクションで PasswordChangedEvent（SUCCESS）を知らせ「変えた」。0行なら同じトランザクションで読み直し、いなければ「本人がいない」、いればトランザクションを終えた後に FAILURE を知らせ「今のパスワードの誤り」（BR4.1〜BR4.5、NFR5.1、`reliability-design.md` 2節）
- [x] トランザクションの境界は `TransactionTemplate`（既存の `LoginService` と同じ）。bcrypt の間は接続を持たない。新しいパスワードが今と同じかは確かめない（BR4.4）。リフレッシュトークン・アクセストークン・ロックの状態に触れない（BR4.5）
- [x] 業務処理は HTTP の状態を知らず、結果の型で返す。成功の場面で新しいログを出さない（`observability-design.md` 2節）
- [x] 対応: US4.1（AC4.1.7・AC4.1.13）、US5.1（AC5.1.1〜AC5.1.6）、BR3.1〜BR3.5・BR4.1〜BR4.5・BR7.3、NFR4.5・NFR5.1・NFR6.6・NFR9.8

### Step 15: 業務処理 — プリファレンスとパスワードの変更 — テスト（単体・結合）

- [x] `UserPreferencesServiceTest`（DB アクセスと出来事の知らせは Mockito で差し替え、パスワードのハッシュは既存のテストの支えの数える仕組みか低い cost の bcrypt）: 取得で本人がいない → 「本人がいない」（承認の場の U2 R-01）、保存の誤りで更新を呼ばない、保存の成功で4列の更新を1回・出来事を出さない、パスワードの入力の誤りで照合も読み取りも出来事も無い、73 バイトの今のパスワードで照合の仕組みを呼ばず FAILURE を知らせる、不一致で FAILURE を1回・更新を呼ばない、一致で新しいハッシュの更新と SUCCESS を1回、同じパスワードへの変更も成功、条件つきの更新が0行で行があれば FAILURE・無ければ「本人がいない」、照合と新しいハッシュの計算のときにトランザクションが動いていない（NFR5.1）
- [x] `PasswordChangeConcurrencyIT`（結合）: `PasswordChangeBarrier` をテストで差し替え、照合の後・書き込みの前に同じ利用者の別の変更を確定させると、後の要求は「今のパスワードの誤り」になり、先の新しいパスワードでだけ照合が通り、監査に SUCCESS 1件・FAILURE 1件が残る（Q3 A、`reliability-design.md` 2節）。待ち合わせに上限の時間を置き、`sleep` に頼らない
- [x] `PreferencesPartialUpdateIT`（結合）: プリファレンスの保存とパスワードの変更を重ねても両方の結果が残る、誤りの項目が1つでもあれば4列が変わらない、同じ4列の保存は後に確定した方が残る（BR3.2〜BR3.4、NFR9.8）
- [x] 単位の単体・結合のコマンドを実行して通す

### Step 16: API（user.web と auth.web の応答の広げ）— 実装

- [x] `MeRequestContextResolver`: `security-design.md` 2節のとおり、認証が無い・匿名・数として読めないときは AUTHENTICATION_REQUIRED。`RequestOrigin` は `request.getRemoteAddr()`・User-Agent（512 文字）・`TraceIdProvider` で作る（既存の `ClientInfoResolver` と同じ取り方）。AUTHENTICATION_REQUIRED は起動時に `ProblemTypeRegistry.findByCode` で1回だけ引き、無ければ起動を失敗させる
- [x] `MeController`: GET → 200 と4つ（email・admin を含めない）、PUT → 200 と保存した4つ、POST `/api/me/password` → 204。結果の型を、入力の誤りは `BusinessException(VALIDATION_FAILED, 既存の一般の説明, {"fieldErrors": [...]})`（誤りが1つ以上のときだけ付け、入れた値を載せない）、今のパスワードの誤りは PASSWORD_CURRENT_MISMATCH（400）、本人がいないは AUTHENTICATION_REQUIRED（401）に変える。応答は既存の `GlobalExceptionHandler` の1か所で作る（変えない）。要求の DTO に利用者 ID・メールアドレスの項目を持たせない
- [x] `PasswordChangeRequest` の文字列はパスワードの3項目を伏せる（既存の `LoginRequest` と同じ扱い）。業務処理へは `Password` の型で渡す
- [x] `CurrentUserResponse` に displayName・language・theme・fontSize を足し、`TokenResponse` で `UserSummary` から写す（theme は system のまま）。ログイン・更新・ログアウト・アクセストークンの認証の判定は変えない。内部DB への問い合わせを増やさない（NFR6.5）
- [x] `AuthenticatedUserToken#getName()` のコメントに「`user.web` がこの値を利用者 ID として読む」ことを書き足す（コメントだけ）
- [x] 対応: US4.1・US5.1、契約 C3・C4、BR3.1・BR6.1・BR8.1〜BR8.4、NFR2.1〜NFR2.3・NFR4.1〜NFR4.4・NFR6.5・NFR8.1

### Step 17: API — テスト（結合）

- [x] `MeRequestContextResolverTest`（単体）: 認証が無い・匿名・数でない名前 → AUTHENTICATION_REQUIRED、513 文字の User-Agent が 512 文字、トレースIDの取り方
- [x] `MePreferencesApiIT`: トークンなし・改ざんしたトークンで 3本とも 401 AUTHENTICATION_REQUIRED で何も変わらない（NFR4.1）、管理者でない利用者で GET・PUT が 200（NFR4.2）、GET が保存された値を返し theme が system のまま・email と admin を含まない（BR3.1）、PUT が保存した値を返し、以降の GET とログイン・更新の応答が同じ値（BR3.3・BR6.1）、V7 の前からいる利用者の初期値（BR9.1）、4つすべてに誤りを入れると `fieldErrors` が4件・項目の順・入れた値を含まず内部DB が変わらない（AC4.1.7）、254 コードポイントの氏名は通り 255 は TOO_LONG、本文に別の利用者の `userId`・`email` を入れても本人の値だけが変わる（NFR4.3）、保存の成功と誤りで監査の件数が増えない（AC4.1.13）、JSON として読めない本文は MALFORMED_REQUEST、トークンが有効なまま本人の行を消すと 401 AUTHENTICATION_REQUIRED（承認の場の U2 R-01）
- [x] `MePasswordApiIT`: 正しい入力で 204、新しいパスワードでログインでき前のパスワードではできない（AC5.1.1）、今のパスワードの誤りで 400 PASSWORD_CURRENT_MISMATCH・パスワードは変わらない・ログインしたまま（アクセストークンとリフレッシュトークンが使える）（AC5.1.2・AC5.1.6）、AC5.1.3 の一覧で通る・`fieldErrors` で拒否される（拒否ではパスワードが変わらず監査も増えない）、端末 A と B でログインし A で変えた後に A も B もリフレッシュトークンで更新できる（AC5.1.4、決めた動作として明示）、変える前に発行したアクセストークンで有効期限まで API を呼べる（AC5.1.5、決めた動作として明示）、今のパスワードの誤りを6回続けても7回目に正しい今のパスワードで変えられ、ログインのロックの状態が変わらない（NFR4.5）、Accept-Language の ja・en で PASSWORD_CURRENT_MISMATCH と VALIDATION_FAILED の説明文が切り替わる（NFR8.1）、管理者でない利用者でも 204（NFR4.2）
- [x] `PasswordChangedAuditIT`: 成功と今のパスワードの誤りで PASSWORD_CHANGED が1件ずつ、必須の項目（result、FAILURE のとき failureReason、actor_user_id と target_user_id が本人、source_ip・user_agent・trace_id・occurred_at）が入る、source_ip・user_agent（513 文字の User-Agent が 512 文字）・trace_id の取り方がログインの監査と同じ、入力の誤りでは記録しない（NFR9.5）。既存の `AuditWriteFailureIT` と同じ形で、成功と誤りの両方で記録を失敗させても応答（204・400）が変わらず ERROR が1回（NFR9.4）。既存の `AuditRollbackIT` と同じ形で、成功のトランザクションを巻き戻すと記録されない（NFR9.8）
- [x] `MeSecretLeakIT`（既存の `*SecretLeakIT` と同じ形、`LogEvents` を使う）: パスワードの変更の成功・今のパスワードの誤り・入力の誤り、プリファレンスの保存の成功・誤りのそれぞれで、アプリのログ・監査の行・応答にパスワード（3つの値）・ハッシュ・トークン・メールアドレスが無い。`cherry.mastersmith` のロガーを TRACE にしてメソッドの追跡を有効にしても、パスワードの値・ハッシュ・メールアドレスが出ない（NFR2.1〜NFR2.3、`team.md` の秘密情報の漏えい）
- [x] 既存の `AuditSecretLeakIT` の列の一覧に `TARGET_USER_ID`・`TARGET_INVITATION_ID` を足す（`functional-spec.md` 6節）
- [x] `LoginResponsePreferencesIT`: ログインとトークンの更新の応答の user に email・admin と4つの値が載る、保存の後の更新の応答が新しい値、ログインと更新の処理が発行する SQL の数が変更の前と同じ（既存の `SqlStatementCounter` を使う。NFR6.5、`performance-design.md` 4節）
- [x] 既存のテストで応答の形を固定しているもの（`LoginApiIT` など）は、4つの項目を足した形に直す（確かめを緩めない）
- [x] 単位の単体・結合のコマンドを実行して通す

### Step 18: 構造の検査と静的解析

- [x] 既存の `ArchitectureTest`・`AuthBoundaryArchitectureTest`・`AuditBoundaryArchitectureTest` と、ほかの機能ごとの境界テストを変えずに通す（緩めない）。`user.web` が `user.repository` を直接呼ばない、`user` が `auth`・`audit` に依存しない、ハッシュを読むのは `user` の中だけ、`auth` は `user.domain` のエンティティと `user.repository` に依存しない、を既存のテストで確かめる。緩める必要が出たときは、緩めずに作れる形を先に探し、それでも要るなら生成を止めて依頼者に諮る（9節の決定 4 の (c)）
- [x] `./gradlew :backend:spotbugsGate` を実行し、除外を足さずに通す（更新の問い合わせは名前つきの引数。NFR9.3）
- [x] `./gradlew spotlessCheck` で新しいファイルのフォーマットとライセンスヘッダー（Java と V7 の SQL）を確かめる
- [x] 対応: NFR9.3、`team.md` の Code Style（層の境界・静的解析）

### Step 19: カバレッジの一覧から外す（packagesJudgedByTotal）と実測

- [x] `backend/build.gradle.kts` の `packagesJudgedByTotal` から、`cherry.mastersmith.user.domain`・`user.repository`・`user.service`・`audit.domain`・`audit.service`・`auth.service`・`auth.web` を外す。`audit.repository` など、ほかに実際に手を入れた既存のパッケージがあれば同じく外す。新しい `user.web` は一覧に無いため自動で対象になる。一覧に足さない、計測の除外を足さない（`team.md` の Testing Posture、NFR9.6）
- [x] colima が動いていることを確かめ、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡して `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行し、外したパッケージと `user.web` の行・分岐のカバレッジを実測して記録する（`project.md` の Testing Posture）
- [x] 下限（行 80%・分岐 70%）を下回るパッケージがあれば、テストを足してやり直す。一覧に戻さない・除外を増やさない。テストを足しても届かないときは生成を止め、値と原因を示して依頼者に諮る
- [x] 対応: NFR9.6、B2 の完了の条件（`user`・`audit`・`auth` のパッケージを下限の対象に戻す）

### Step 20: 文書（README・perf/README）

- [x] `README.md` に新しい節「利用者のプリファレンスとパスワードの変更（U2）」を足す: 3本の API（GET・PUT `/api/me/preferences`、POST `/api/me/password`）、ログインした利用者なら誰でも本人の分だけ（401 の扱い）、入力の誤りの `fieldErrors` と reason の一覧、今のパスワードの誤りの code、今のパスワードの誤りの回数を制限しないことと見つけ方の問い合わせ（`observability-design.md` 3節）、ログ・監査にパスワード・ハッシュ・メールアドレスを出さないこと、環境変数は増えないこと。あわせて、契約との差（C4 の 400 VALIDATION_FAILED に `fieldErrors` と reason の一覧を足したこと、C8 の PASSWORD_CHANGED に `target_user_id`（本人）を足したこと）を書く（`contract-summary.md` は書き換えない。9節の決定 2）
- [x] `README.md` の「監査ログ（U4）」に、記録の種類 PASSWORD_CHANGED（結果と失敗の理由 CURRENT_PASSWORD_MISMATCH）と、対象の列（target_user_id・target_invitation_id）を足す。保存の期間は変えない
- [x] `README.md` の「戻し方」に、V7 の後の注意を足す: 戻すときは `.env` の `MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL` を配備のときのまま変えない（変えると1つ前の版が display_name を渡さない追記を試みて失敗しうる）、空の内部DB で1つ前の版を起動するときは V7 を当てていない内部DB を使う（基盤の設計の 5節、承認の場の D4）。戻しの練習の手順の書き起こしは deployment-pipeline に残す
- [x] `README.md` の「スキーマの変更（Flyway）」に V7 を足す（前進のみ、1つ前の版が動くこと、確かめの2段）
- [x] `perf/README.md` の手順 2 の仮の利用者の SQL に `display_name`（メールアドレスと同じ値）を足す（承認の場の N1、基盤の設計の I-D2）。パスワードの変更の専用の仮の利用者と k6 の場面は Build and Test に引き継ぐ
- [x] 対応: NFR10.5 の前提、`infrastructure-specification.md` 8節、`cicd-pipeline.md` 4節

### Step 21: 1コマンドの検査（統合の前の関門）

- [x] colima が動いていることを確かめ、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡して `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行し、すべての段が通ることを確かめる。対象DB のテストが SKIPPED になっていないことを確かめる（`project.md` の Testing Posture）
- [x] テストの件数（単体・結合）と、全体のカバレッジ、Step 19 のパッケージごとの行・分岐のカバレッジを実測の数字で記録し、Step 1 の基準と比べる（既存のテストが減っていない・失敗していない）
- [x] 秘密情報の検出（Gitleaks）と依存関係の脆弱性検査（OSV-Scanner）が通ることを確かめる（新しい依存は足していない）
- [x] 対応: B2 の共通の完了の条件、`project.md` の Mandated（統合の前の確認）

### Step 22: 記録とコミットの提案

- [x] `code-summary.md`（作ったもの、上流との差（8節）、Step 1 と Step 21 の実測、パッケージごとのカバレッジ、V7 の自動の確かめの結果、9節の決定の反映。とくに、契約 C4 の `fieldErrors` と C8 の PASSWORD_CHANGED の `target_user_id` の差（決定 2）と、既存のログのメールアドレスを依頼者の判断で据え置いたことと `project.md` の Forbidden との食い違い（決定 3））、`source-manifest.json`、`traceability.json`（機能設計の OK と Deferred の分け方にそろえる）を作る（コード生成の段の手順）
- [ ] 3節の C1〜C6 の区切りで、コミットの内容とメッセージの案を依頼者に示して承認を得る（生成の担当はコミットしない）
- [ ] 対応: 段の記録、`project.md` の Change Control

## 6. ストーリー・要件と手順の対応

| ストーリー・要件 | 受け入れ基準・決まり・NFR | 手順 |
|---|---|---|
| US4.1 自分の表示の設定と氏名を変える（主） | AC4.1.7（誤りは拒否し4つとも変わらない、上限ちょうどは通る） | Step 4・5・14・15・16・17 |
| US4.1 | AC4.1.13（監査の件数が増えない） | Step 14・17 |
| US4.1 | AC4.1.1・AC4.1.2・AC4.1.8・AC4.1.9・AC4.1.10 のサーバーの部分（BR3.1・BR3.3・BR6.1・BR9.1） | Step 6・7・14・16・17。表示は Deferred（u4-display-foundation・u7-preferences-ui） |
| US4.1 | AC4.1.3〜AC4.1.6・AC4.1.11・AC4.1.12（画面の表示） | Deferred（u4-display-foundation・u7-preferences-ui） |
| US5.1 自分のパスワードを変える（主） | AC5.1.1（新しいパスワードで入れ、前のでは入れない） | Step 14・15・16・17 |
| US5.1 | AC5.1.2・AC5.1.6（今のパスワードの誤りは 400、ログインしたまま） | Step 4・14・15・16・17 |
| US5.1 | AC5.1.3（規則の境界と2回の入力の不一致） | Step 4・5・17 |
| US5.1 | AC5.1.4・AC5.1.5（ほかの端末のリフレッシュトークン、発行済みのアクセストークン） | Step 14・17 |
| US5.1 | AC5.1.7〜AC5.1.9（画面の表示） | Deferred（u7-preferences-ui） |
| US3.2（関わる） | 利用者の作成（C2、BR5.1〜BR5.5）。受け入れ基準は U3 が確かめる | Step 10・11 |
| CR1.1 | エラーの説明文の言語（BR8.2・BR8.3、NFR8.1） | Step 4・16・17 |
| CR3 | 監査（BR7.1〜BR7.5、BR3.5、NFR9.4・NFR9.5） | Step 4・6・12・13・17 |
| CR4 | 認可（BR8.1、NFR4.1〜NFR4.4） | Step 16・17・18 |
| CR5 | 秘密と個人情報（BR7.4・BR8.4、NFR2.1〜NFR2.4） | Step 10・16・17 |
| 氏名と表示の設定の値 | BR1.1〜BR1.6・BR2.1・BR2.2、NFR9.2 | Step 4・5・6 |
| プリファレンスの読み書き | BR3.1〜BR3.5 | Step 8・9・14・15・16・17 |
| パスワードの変更 | BR4.1〜BR4.5、NFR4.5・NFR5.1・NFR6.6・NFR9.1 | Step 4・5・8・9・14・15・17 |
| 利用者の作成と読み取り | BR5.1〜BR5.5 | Step 10・11 |
| 認証の応答 | BR6.1、NFR6.5 | Step 10・16・17 |
| スキーマの変更 | BR9.1・BR9.2、NFR10.1〜NFR10.4 | Step 6・7・20 |
| テストと品質の関門 | NFR9.3・NFR9.6〜NFR9.8 | Step 3〜19・21 |
| 性能・接続・観測（測定は後の段） | NFR5.2・NFR5.3・NFR6.1〜NFR6.4・NFR6.7〜NFR6.9 | Build and Test に引き継ぐこと |
| 戻し方（実地の確かめは後の段） | NFR10.3（2）・NFR10.5 | Step 20、Build and Test に引き継ぐこと |

## 7. テストの量（Standard）

部品ごとに 5〜8 件の単体テストと、境界の結合テストを置く。成功の場合と、少なくとも2つの失敗・境目の場合を含める（`phases/construction.md` の Testing Standards）。

| 部品 | 単体 | 結合 |
|---|---|---|
| 氏名と値の決まり（`user.domain`） | `DisplayNameTest` 7〜8 件＋性質ベース 3 件、`PreferenceValuesTest` 6 件＋性質ベース 1 件 | — |
| 項目ごとの誤り（`user.domain`） | `PreferencesValidationTest` 5〜6 件、`PasswordChangeValidationTest` 8 件＋性質ベース 2 件 | — |
| 値の型と code（`user.domain`・`audit.domain`） | `RequestOriginTest`・`PasswordChangedEventTest`・`UserProblemTypesTest`・監査の値の長さ（合わせて 6〜8 件） | — |
| スキーマ（V7） | — | `V7MigrationIT` 3〜4 件、`V7BackwardCompatibilityIT` 4〜5 件、`UserSchemaIT`・`AuditSchemaIT` に足す 3〜4 件 |
| DB アクセス（`user.repository`） | — | `UserRepositoryIT` に足す 5〜6 件 |
| 利用者の作成と要約（`user.service`） | `UserAccountServiceTest` に足す 6〜8 件、`InitialAdminInitializerTest` の直し、`UserProblemTypeCatalogTest` | `UserCreationIT` 3〜4 件、`InitialAdminIT` の直し |
| 監査（`audit.domain`・`audit.service`） | `AuditEventFactoryTest` に足す 3〜4 件、`AuditEventListenerTest` に足す 4〜5 件 | `PasswordChangedAuditIT` 6〜8 件 |
| プリファレンスとパスワードの変更（`user.service`） | `UserPreferencesServiceTest` 8 件 | `PasswordChangeConcurrencyIT` 1〜2 件、`PreferencesPartialUpdateIT` 3 件 |
| API（`user.web`・`auth.web`） | `MeRequestContextResolverTest` 5 件 | `MePreferencesApiIT` 8 件、`MePasswordApiIT` 8 件、`MeSecretLeakIT` 5〜6 件、`LoginResponsePreferencesIT` 3〜4 件、`AuditSecretLeakIT` の直し |
| 構造の検査 | 既存の境界テストをそのまま通す | — |

## 8. この計画で決めたこと・承認済みの文書との差

承認済みの文書は書き換えず、差をここと `code-summary.md` に記録する（`project.md` の決まり）。

| 対象 | 承認済みの形 | この計画での扱い | 理由 |
|---|---|---|---|
| 列の名前と長さ | 「列の名前はコード生成で決める」（`infrastructure-specification.md` 2節） | `display_name` VARCHAR(508)・`language` VARCHAR(2)・`theme` VARCHAR(6)・`font_size` VARCHAR(2)、`target_user_id`・`target_invitation_id` BIGINT（2.2） | 氏名の長さは `entities.md` の「UTF-16 の単位で最大 508」、ほかは決めた値の最大の長さ |
| 値の Java の型 | 「表示の設定の値」の純粋な関数（`logical-components.md` 1節） | 列挙 `Language`・`Theme`・`FontSize` と `AttributeConverter`。DTO と `UserSummary` では文字列（2.2） | 型で値を限りつつ、`auth` が `user.domain` のエンティティに依存しない決まりと、DTO を文字列で受ける決まりを守るため |
| `UserSummary` の文字列 | 設計に記載なし | 文字列にするとメールアドレスを伏せる（2.2） | 既存の `TraceAspect` が業務処理の戻り値を TRACE のログに出すため。`project.md` の Forbidden（メールアドレスをアプリのログに含めない）と `security-design.md` 4節の範囲の中の作り |
| 同時の変更の待ち合わせの口 | 「テストで差し替えられる待ち合わせの口を使う。本番のコードの流れは変えない」（`reliability-design.md` 2節） | `user.service` に `PasswordChangeBarrier`（本番は何もしない既定の Bean）を置く | 設計のとおり。前の Intent の `AuditEventListener` の `LongSupplier` と同じ考え方（`project.md` の Testing Posture） |
| `auth.service` をパッケージごとの下限の対象に戻すこと | 「応答を広げる `auth.service`・`auth.web`」を外す（`logical-components.md` 5節、`cicd-pipeline.md` 3節） | `auth.service` はコードを変えない見込みだが、設計のとおり一覧から外す。下回ればテストを足す（Step 19） | 承認済みの設計の一覧に従う。一覧から外すことは下限を強める向きのため、`team.md` の「一覧を増やさない」と食い違わない |
| 戻し方の注意の README への記載 | 「書き起こしは deployment-pipeline」（`infrastructure-specification.md` 8節） | B2 で README の「戻し方」に注意の2点だけを足し、戻しの練習の手順は deployment-pipeline に残す（Step 20） | V7 は B2 で `develop` に入り、配備までの間も README の戻し方が V7 の後の注意を欠かないようにするため（承認の場の D4） |
| Hibernate の `validate` が余分な列を許すことの確かめ（承認の場の U2 R-02） | 確かめ (2)（戻しの練習、deployment-pipeline・deployment-execution） | B2 の統合の前には確かめられない。統合の前は既知の制約として `code-summary.md` に記録し、戻しの練習で外れたと分かった時点で速やかに依頼者に諮る。外れたときの作りの候補は、1つ前の版の配備に限って前の版の設定で起動する手順にするか、V7 の後の版を戻し先にするか（BR9.1・NFR10.3 との差になる） | 自動のテストでは1つ前の版のエンティティを持てないため。Flyway の側の見込みは Step 7 で統合の前に確かめる |
| 監査の失敗の ERROR の組み立て | 「組み立ての整理はコード生成に任せる」（`observability-design.md` 2節） | actorUserId と DSL の項目の並べ方を整理してよい。既存の出来事の項目と既存のテストの期待は変えない（Step 12） | 設計の文書が生成に任せた範囲 |
| 契約 C4・C8 の文書 | C4 の 400 は「VALIDATION_FAILED」だけ、C8 の PASSWORD_CHANGED は actor_user_id・result・failure_reason だけ | 実装は `fieldErrors`（`security-design.md` 3節）と `target_user_id`（BR7.2）を持つ。`contract-summary.md` は書き換えず、差を `code-summary.md` と README の U2 の節に記録する（Step 20・22） | 依頼者の決定（9節の決定 2）。`project.md` の「確定済みの文書は書き換えず差を明記する」に合う |
| 既存のログのメールアドレス（`project.md` の Forbidden 2026-09-25 との食い違い） | 今の `InitialAdminInitializer` は INFO のログにキー `email`、`AuditEventListener` は監査の記録の失敗の ERROR に `enteredEmail` を載せる（前の Intent の NFR10.3、`AuditWriteFailureIT` が確かめる） | **依頼者の判断で据え置き**。B2 ではこの2つのログの項目を変えず、`AuditWriteFailureIT` の期待も変えない。2つのファイルに別の理由で手を入れるとき（Step 10・12）も、この項目には触れない。食い違いは `code-summary.md` に記録するだけ | 依頼者の決定（9節の決定 3）。U2 の設計（`security-design.md` 4節）は新しいログにメールアドレスを足さないことだけを決めており、B2 の新しいログにはメールアドレスを載せない |

## 9. 依頼者の決定

計画の承認の前に諮った論点について、依頼者が次のとおり決めた。計画の各 Step と8節はこの決定に合わせてある。

1. **作業の場と git の操作の順**: 案のとおり。Step 1 で `develop` から短命のブランチ `feature/260925-user-management-b2` を作る。サブモジュールの更新を含まないため、`develop` へは squash で統合する。コミットは生成の後に、依頼者の承認を得て C1〜C6 でまとめて行う（3節、Step 1・Step 22）。
2. **契約 C4 の `fieldErrors` と契約 C8 の PASSWORD_CHANGED の `target_user_id` の反映**（機能設計の承認の場の G6・R-03、`logical-components.md` 2節）: A。実装はどちらも承認済みの設計（`security-design.md` 3節、`rules.md` の BR7.2）のとおりに作る。`inception/contract-design/contract-summary.md` は書き換えず、差（C4 の 400 VALIDATION_FAILED に `fieldErrors` と reason の一覧を足したこと、C8 の PASSWORD_CHANGED に `target_user_id`（本人）を足したこと）を `code-summary.md` と README の U2 の節に記録する（Step 20・22、8節）。後の単位（U3・U7）は `logical-components.md` 2節のとおり `security-design.md` 3節を正として読む。
3. **既存のログのメールアドレスと `project.md` の Forbidden（2026-09-25）の食い違い**: A（据え置く）。既存の `InitialAdminInitializer` の INFO のキー `email` と、`AuditEventListener` の監査の記録の失敗の ERROR の `enteredEmail` は、B2 では変えない。`AuditWriteFailureIT` の期待も変えない。2つのファイルに別の理由で手を入れるとき（Step 10・12）も、このログの項目には触れない。食い違いは記録するだけとし、8節と `code-summary.md` に「依頼者の判断で据え置き」と書く。B2 が足す新しいログにはメールアドレスを載せない（`security-design.md` 4節）。
4. **生成の途中で止めて諮る場面**: 次の3つのときは、生成をその手順で止め、結果と候補を示して依頼者に諮る。
   - (a) Step 7 で、V6 までしか知らない Flyway の `validate`・`migrate` が V7 の後の DB で失敗したとき（見込みが外れた）
   - (b) Step 19 で、テストを足してもパッケージごとの下限（行 80%・分岐 70%）に届かないとき
   - (c) 既存の ArchUnit の境界テストを緩める必要が出たとき（緩めずに作れる形を先に探す）

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

Testing Contract の `plan_profile.steps` との対応: 骨組みと本番の設定は Step 1・2、テストの実行の準備は Step 3（最初のテストの Step 5 より前）、ドメインの値の型と決まり（業務処理の土台の純粋な関数）は Step 4・5、データの形（V7・エンティティ）は Step 6・7、DB アクセスは Step 8・9、業務処理は Step 10〜15、API は Step 16・17、構造の検査・静的解析・環境とビルドの設定は Step 18・19・21、文書と記録は Step 20・22。U2 は画面を持たないため、画面の層（Frontend behavior）の手順は無い。

## Build and Test に引き継ぐこと

| 項目 | 引き継ぐ内容 | 持ち主 |
|---|---|---|
| カバレッジの実測 | `:backend:cleanTest :backend:cleanIntegrationTest` を付けた verify で、全体と、Step 19 で外したパッケージと `user.web` の値をもう一度実測して記録する | Build and Test |
| verify の時間 | U2 の結合テストを足した後の `./gradlew verify` の時間を測り、B1 の後の実測と比べる | Build and Test |
| 負荷の試験の用意 | `perf/k6/scenarios.js` に、プリファレンスの取得・保存（成功・入力の誤り）、パスワードの変更の成功・今のパスワードの誤り・入力の誤りの場面を足す（同時 10、閾値は変更の成功が p95 2 秒、ほかは 1 秒）。`perf/README.md` にパスワードの変更の専用の仮の利用者（例 `perf-pw01`〜`perf-pw10`）を足し、VU ごとに1人を当てて変更の前後のパスワードを交互に使う手順を書く。1人の利用者を同時に2つの場面で使わない（`cicd-pipeline.md` 4節） | Build and Test（用意）・performance-validation（測定） |
| 性能と接続の測定 | NFR6.1〜NFR6.5（既存の `loginSuccess`・`refresh` の流し直しを含む）と NFR5.2（使い捨ての環境の hikaricp の待ちの時間切れの累計 0、借りるまでの待ちの最大、流した成功の件数と監査の PASSWORD_CHANGED・SUCCESS の件数の一致。消す前に数える）。`caffeinate -i` を付け、測る間は配備したアプリを止める。台本を書く前に、この表の項目を台本の手順と1つずつ突き合わせる（`project.md` の Testing Posture） | performance-validation |
| 指標と警報の名前 | 3本の API の `http_server_requests_milliseconds_*` の `uri`・`method`・`status` の実際の名前と値、既存の警報（`ms-login-p95`・`ms-refresh-p95` ほか）の式の流し直し、総当たりの見つけ方の問い合わせの列の名前（承認の場の D5、`monitoring-design.md` 7節） | observability-setup |
| V7 の後方互換の実地の確かめ | 1つ前の版のイメージを V7・V8 の後の内部DB の複写で起動し、健全性・ログイン・トークンの更新・ログアウト・監査の記録が動くこと、初期管理者が作られないこと、Hibernate の `validate` が余分な列を許すこと（承認の場の U2 R-02。外れたら速やかに依頼者に諮る）。V8（U3）とまとめて1回（`infrastructure-specification.md` 5節・6節） | deployment-pipeline（手順）・deployment-execution（実行） |
| バックアップと戻し用のタグ | V7・V8 を当てる前の内部DB のバックアップと、戻し用のタグ（NFR10.5） | deployment-execution |
| 契約の反映 | 9節の決定 2 のとおり、C4 の `fieldErrors` と C8 の `target_user_id` の差が `code-summary.md` と README に記録されているかを確かめる（`contract-summary.md` は書き換えない）。U3（C6 の `fieldErrors`）と U7（画面の読み取り）が `security-design.md` 3節の形を使うことは B3・B5 の計画で確かめる | B3・B5（U3・U7 のコード生成の計画） |
| 利用者の作成の呼び出し元 | U3 の登録の完了が `createUser` の `EmailAlreadyUsed` で必ず巻き戻すこと（BR5.2・`reliability-design.md` 4節の前提）と、同じ `DisplayName` の関数で入力を検証すること（BR1.6）は U3 が確かめる | B3（U3 のコード生成） |
| 既存のログのメールアドレス | `InitialAdminInitializer` の INFO の `email` と `AuditEventListener` の失敗の ERROR の `enteredEmail` は、依頼者の判断で据え置いた（9節の決定 3）。`project.md` の Forbidden との食い違いが残ることを記録として引き継ぐ | 受け入れ済み（記録だけ） |
| 残る危険 | 今のパスワードの総当たり（R1）は受け入れ済み。監査ログの問い合わせで見つける手順を README に置く（Step 20） | 受け入れ済み（記録だけ） |
