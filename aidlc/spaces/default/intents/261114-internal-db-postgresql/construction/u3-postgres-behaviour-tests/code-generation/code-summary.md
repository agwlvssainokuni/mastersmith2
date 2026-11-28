# コードの要約 — u3-postgres-behaviour-tests

内部DB を PostgreSQL で動かす経路の振る舞い（11 機能の代表の流れ・違反の区分・待ちの上限と行き詰まり・失敗後のトランザクション・書き直した SQL と鍵の重なり・10MB と 32 バイト・時刻・検索）を、実際の PostgreSQL 18.6 の結合テストで確かめた。`src/main`・H2 の既存のテストと部品・`build.gradle.kts`・`ci.yml`・lockfile・依存は変えていない（`git status`・`git diff --stat`。新しいファイルだけ）。テストの説明は英語、記録は日本語。

## 作ったファイル（すべて `backend/src/test/java/cherry/mastersmith/` の下の新規ファイル 34 個）

| 種類 | ファイル | 件数 |
|---|---|---|
| 構造のテスト（普段の `verify` に入る） | `internaldb/PgTestBoundaryArchitectureTest` | 6 |
| 部品（`internaldb/testsupport`） | `PgApp`・`PgSharedApp`・`ConflictProbe`・`WaitWindow`・`PortableFixture`・`PgClockConfig`・`PgUserSearchSamples` | – |
| 流れ（BR1。11 クラス） | `auth/postgres/PgAuthFlowIT`・`user/postgres/PgPasswordFlowIT`・`user/postgres/PgPreferencesFlowIT`・`useradmin/postgres/PgUserAdminFlowIT`・`invitation/postgres/PgInvitationFlowIT`・`role/postgres/PgRoleFlowIT`・`role/postgres/PgWorkRoleFlowIT`・`group/postgres/PgGroupFlowIT`・`navigation/postgres/PgNavigationFlowIT`・`dslmanage/postgres/PgDslFlowIT`・`audit/postgres/PgAuditFlowIT` | 各 2 |
| 違反（BR2） | `internaldb/postgres/PgViolationClassificationIT`・`PgCaseSensitivityIT` | 4・3 |
| 待ち・行き詰まり・失敗後（BR3・BR4） | `PgWriteWaitIT`・`PgRowLockWaitIT`・`PgDeadlockIT`・`PgSkipLockedLoginIT`・`PgFailureTransactionIT` | 2・1・2・2・2 |
| 書き直した SQL（BR5） | `PgPortableSqlIT`・`PgKeyConflictIT`・`PgLockFailureClassificationIT` | 5・3・4 |
| 型・時刻・検索（BR6〜BR8） | `PgBinaryRoundTripIT`・`PgTimeRoundTripIT`・`PgUserSearchIT` | 3・4・2 |
| 出力の検査・部品の確かめ | `PgSecretLeakIT`・`PgSharedAppSmokeIT` | 1・3 |

PostgreSQL の結合テストは 26 クラス・63 件（計画の 25 クラスに、部品の動作確認の `PgSharedAppSmokeIT` を足した）。流れ 11 + 違反 2 + 待ち 5（失敗後を含む）+ 書き直し 3 + 型 3 + `PgSecretLeakIT` + `PgSharedAppSmokeIT`。内部DB の PostgreSQL の結合テストの全件（U1 の 5 クラス・13 件を含む）は 31 クラス・76 件（下の表）。

計画との違い（作り方）:
- `PgApp`（計画に無い部品）: クラスごとのアプリと共有のアプリの土台。`PgClockConfig`・`PgUserSearchSamples` も足した。
- 招待の流れは、H2 の参照のテストと同じく、実際に受けるだけの SMTP の受け手（`SmtpTestServer`）を使う（`PgApp.startWithMail`）。許す一覧に `SmtpTestServer`・`ReceivedMails`・`StatementCounter` を足した。
- `PgSharedAppSmokeIT` は流れのクラスに吸収せず残した（部品の回帰の目印）。

## 実行の結果と時間

実行は colima の 2 つの環境変数つき。待ちの確かめの時間は呼び出しの開始から測る。

| 実行 | 結果 | 時間 |
|---|---|---|
| `./gradlew :backend:internalDbPostgresTest`（U1 のクラスを含む全件） | 31 クラス・76 件が通る | `BUILD SUCCESSFUL in 1m 32s`（クラスごとの時間の合計は 88.3 秒） |
| 代表の 3 クラスを単独で（流れ・待ち・時刻） | `PgAuthFlowIT`・`PgWriteWaitIT`・`PgTimeRoundTripIT` が通る（他のクラスも単独で通した記録が下にある） | 13 秒・16 秒・15 秒（Gradle の起動を含む） |
| `./gradlew verifyQuick :backend:internalDbPostgresTest`（承認済みの確かめ） | 通る。フォーマット・リンタ・ライセンスヘッダー・型検査・単体テスト・画面のテスト・Gitleaks（`no leaks found`）・OSV-Scanner・SpotBugs の関門（新しい指摘なし。除外は足していない）が通り、続けて `internalDbPostgresTest` の全件が通る | `BUILD SUCCESSFUL in 5m 10s`（`pmset -g log` で Sleep・DarkWake が無いことを確かめた。`caffeinate -dims` で包んだ） |
| `./gradlew :backend:test --tests '…PgTestBoundaryArchitectureTest'` | 6 件が通る | クラス全体 1.471 秒（ArchUnit の読み込みを含む最初の 1 回）、続けて流したときは 0.5 秒（DD-9 の「1 秒未満」は 2 回目以降） |
| `git status`・`git diff --stat` | `src/main`・H2 の既存のテストと部品・`build.gradle.kts`・`.github`・lockfile・`frontend` に差分なし（新しいファイルのみ） | – |

`INTERNAL_DB_PG_*` の行（全件の 1 回）:

| 行 | 値 |
|---|---|
| `INTERNAL_DB_PG_SHARED_APP startupMs` | 6,455 ms（U1 の `INTERNAL_DB_PG_BASELINE startupToHealthMs=1,560`・`migrationMillis=224` と並べる。共有のアプリは他のクラスと同じ設定で、起動に流れ・待ちのクラスより多くの部品の読み込みが入る） |
| `INTERNAL_DB_PG_WAIT PgWriteWaitIT.workRoleSaveIsBusy` | 2,019 ms（2000 以上 3000 未満） |
| `INTERNAL_DB_PG_WAIT PgWriteWaitIT.previewPlaceIsALockFailure` | 2,016 ms（同上） |
| `INTERNAL_DB_PG_WAIT PgRowLockWaitIT.rowLockRead` | 3,041 ms（3000 以上 4000 未満） |
| `INTERNAL_DB_PG_WAIT PgRowLockWaitIT.nextWriteOnTheSameConnection` | 2,049 ms（2000 以上 3000 未満） |
| `INTERNAL_DB_PG_WAIT PgSkipLockedLoginIT.allHeldWaitForTheHint` | 3,040 ms（3000 以上 4000 未満） |
| `INTERNAL_DB_PG_WAIT PgSkipLockedLoginIT.sevenHeldDoNotWait` | 21 ms（待たない） |
| `INTERNAL_DB_PG_WAIT PgSharedAppSmokeIT.holderMakesAppWriteWait` | 2,062 ms |
| `INTERNAL_DB_PG_DEADLOCK` | app=409 `GROUP_BUSY`、保持側は続けた、1,060 ms |

## 上流との差

1. 機能設計 BR3.3（行き詰まり）の合否は「ちょうど一方が排他の失敗で終わり、もう一方は続けられる」まで（NFR 設計 DD-2 の「アプリが犠牲」は合否にしない。計画の決め）。実測ではアプリが犠牲になる回が多いが、保持側が犠牲になる回もある（`PgLockFailureClassificationIT` の観測で 1 回あった）。
2. NFR 要件 NFR1.2（観測した時点から測る）は、NFR 設計 DD-1 で「呼び出しの開始から測る」に変えた。この単位は NFR 設計に従う。
3. NFR 要件 NFR9.1 の `randomSecret` は、NFR 設計 DD-14 で許す側に変えた。この単位は NFR 設計に従う。
4. NFR 要件 NFR2.1「接続先の文字列が無い」は、実際には起動の出力（Flyway の INFO）に資格情報を含まない接続先の URL が出るため、合否を「利用者・パスワード・初期管理者の資格情報が無い」にし、URL は観測にした（下の「依頼者に確かめたいこと」）。
5. BR4.1 の「2 文目の失敗」は、成功の監査の書き込みを失敗させる案が成り立たなかった（成功の監査は同じトランザクションで書かれない）ため、同じトランザクションで 2 文を書く DSL の適用（版の挿入 → プレビューの削除）に変えた。
6. BR5.2 (c)（`preview_id` の一意の制約）は、`dsl_previews` が 1 行しか持てないため、データだけでは起こせない。専用のデータベースの中で `ck_dsl_previews_slot` を一時的に外し、スロット 2 に同じ `preview_id` を置いて起こした（終わったら戻す）。
7. BR7.1 は、JVM の既定の時差ごとにアプリを起動し直して確かめた（`pgjdbc` は接続を作るときの既定の時差を読むため）。PostgreSQL のセッションの `TimeZone` は、トランザクションの中の `SET LOCAL` で変えた。
8. BR6.1 の「全バイト値」の本文は、API（YAML の検証）を通らないため、リポジトリを直接呼んで確かめ、API では上限ちょうどの有効な DSL の受け付けと +1 バイトの 413 を確かめた。BR6.2 のハッシュは、先頭 0x00・末尾 0xFF とその逆のハッシュになるトークンを乱数で探して使った（32 バイトで 256 の値すべては含められない）。
9. BR8.1 の検索の見本は、U2 の捨ての試しの 36 人・約 55 入力そのものは記録に残っていないため、同じ種類の入力（21 人・25 の検索語）を作り、H2 でも期待どおりであることを使い捨てのコードで確かめてから固定した。H2 と PostgreSQL で食い違う入力は見つからなかった（Unicode の端の 3 つは対象外）。

## 依頼者に確かめたいこと

1. **`40P01` の分類（本体の差の疑い。U2 に戻す候補）**: `KeyedUpsertExecutor`（JDBC を直接使う）の中で行き詰まり（`40P01`）の犠牲になると、作業ロールの保存は `Busy`（409 `ROLE_BUSY`）ではなく `RoleStoreUnexpectedException`（想定外。500）になる。`RowLockFailures` の SQLState の一覧（`HYT00`・`40001`・`55P03`）に `40P01` が無いため。`EntityManager` 経由の経路（ロールへのグループの割り当て）は 409 `GROUP_BUSY` になる。直すなら U2 で一覧に `40P01` を足す小さな直し。この単位は合否に入れていない（観測の行 `INTERNAL_DB_PG_OBSERVE keyedUpsertDeadlock`）。犠牲になる側は輪が閉じる時刻で決まり、観測のたびに入れ替わりうるため、観測のテストはアプリが犠牲になるまで最大 5 回やり直す（全件の 1 回目はアプリが犠牲にならず、保持側が `40P01` を受けた回もあった）。アプリが犠牲になった回の結果が `RoleStoreUnexpectedException`。
2. **起動の出力の接続先 URL**: 起動の出力（Flyway の INFO）に、資格情報を含まない接続先の URL が出る。`project.md` の Forbidden は資格情報を含む URL を禁じるので違反ではないが、NFR 要件 NFR2.1・NFR 設計 DD-12 の「接続先の文字列が無い」とは読み方が違う。`PgSecretLeakIT` は、利用者・パスワード・初期管理者の資格情報を合否にし、URL は観測にしている（この読み方でよいか）。
3. **構造のテストで起こせなかった 2 点**: (a) 「注釈付きなのに名前が `…IT` で終わらない」の検出、(b) `org.h2` の型への依存の検出（H2 は実行時だけの依存でソースから参照できないため、わざと入れる確認ができない）。他の 5 つの規則は、わざと違反を入れて落ちることを確かめて戻した。
4. **BR4.1 の作り方の変更**（上流との差の 5）と、**BR5.2 (c) の作り方**（同 6）。
5. 表示の設定の保存の上限切れが 500 `INTERNAL_ERROR` になる既存の挙動（H2 でも同じ）。BR3.1 の確かめには使わず、作業ロールの保存の 409 `ROLE_BUSY` とプレビューを置く経路を使った。この 500 を直すかは別の話。

## 後の単位に渡すこと

- **U4（文書）**: 検索の Unicode の端の 3 つ（`İ`・語末シグマ・`ǅ`）は確かめの対象に入れていない（U2 の Q3: A）。U4 の文書に書く。明示して流す手順は `unit-test-instructions.md`（全件は `internalDbPostgresTest`、承認済みの確かめは `verifyQuick :backend:internalDbPostgresTest`）。
- **Build and Test**: 全件の `./gradlew verify` と CI の時間・カバレッジの値が、この単位の変更で動かないこと（`src/main`・`build.gradle.kts` に差分が無い）を確かめる。普段の `verify` に入る U3 のテストは構造のテスト 1 クラスだけ。
- **テストの部品を足すとき**: `PgTestBoundaryArchitectureTest` の許す一覧にない既存のテスト部品を使うときは、一覧を先に更新する。`Pg…IT` には `@InternalDbPostgresIT` が必要。クラスごとのアプリは同時に 2 つ以上起動しない（接続は 1 アプリあたり最大 30 本、共有 + クラスごとで約 60〜70 本、`max_connections` 100）。

## 確かめの結果

### Step 1: 実行の確かめと洗い出し

- 骨組みの確かめ: `internalDbPostgresTest --tests 'cherry.mastersmith.internaldb.postgres.InternalDbPostgresBaselineIT'` が通る（colima の 2 つの環境変数つき、`BUILD SUCCESSFUL in 20s`）。`unit-test-instructions.md` の命令が使える。
- `@InternalDbPostgresIT` を付けた既存のクラス（Step 開始時）: 5 クラス（`InternalDbPostgresBaselineIT`・`InternalDbPostgresHealthIT`・`InternalDbPostgresMigrationIT`・`InternalDbPostgresMigrationFailureIT`・`InternalDbPostgresStartupFailureIT`）。すべて `*IT` で終わり、`*IT` で終わらないものは無い（設計の文書の「8 クラス」とは違う。数はこの実測を正とする）。
- Step 開始時の差分は `aidlc/` だけ。`backend/build.gradle.kts`・`.github`・`backend/gradle.lockfile`・`frontend`・`backend/src/main` に差分は無い（Step 4 の終わりにも同じ）。
- H2 の参照のテストの期待（`ParityExpectation`。状態コードと `code`。コード上の値は参照のテストの `assertCode(...)`・`isEqualTo(...)` から読んだ。Step 5 で読み直して確定した値は、下の「Step 5 で確定した期待」に書いた）。

| 機能 | 代表の成功（API） | 代表の失敗（H2 の参照の値） |
|---|---|---|
| 1 ログイン・更新 | `POST /api/auth/login` 200（項目は `accessToken`・`expiresAt`・`user`）、`POST /api/auth/session/refresh` 200 | 誤った資格情報 401 `AUTHENTICATION_FAILED`、空の入力 400 `VALIDATION_FAILED`、再利用・停止後の更新 401 `REFRESH_FAILED`、Origin 無し 403 `ORIGIN_NOT_ALLOWED` |
| 2 パスワード | `POST /api/me/password` 204 | 現在のパスワードの食い違い 400 `PASSWORD_CURRENT_MISMATCH`、検証 400 `VALIDATION_FAILED` |
| 3 利用者の管理 | `POST /api/admin/users/{id}/suspend` `resume` `reset-login-failures` 204 | 自分への操作 409 `USER_ADMIN_SELF_OPERATION`、変化なし 409 `USER_ADMIN_NO_CHANGE`、停止中の対象 409 `USER_ADMIN_TARGET_SUSPENDED`、権限なし 403 `ACCESS_DENIED` |
| 4 招待と登録 | 登録の確かめ `POST /api/registration/verify` 200、完了 `POST /api/registration/complete` 204 | 無い・期限切れ・使用済みのトークンは 404 `REGISTRATION_LINK_INVALID`（理由の区分は監査の行）、検証 400 |
| 5 ロールと権限 | ロール作成 201、`POST /api/admin/roles/{id}/assignments` 204、削除 204 | 名前の重なり 409 `ROLE_NAME_DUPLICATE`、変化なし 409 `ROLE_NO_CHANGE`、使用中の削除 409 `ROLE_IN_USE`、無い対象 404 `ROLE_NOT_FOUND` / `USER_NOT_FOUND` / `GROUP_NOT_FOUND`、検証 400 `VALIDATION_FAILED` |
| 6 作業ロール | `PUT /api/me/work-role` 204 | 割り当て外のロール 409 `ROLE_NOT_ASSIGNED`、検証 400 `VALIDATION_FAILED` |
| 7 グループ | 作成 201、メンバー追加 204、削除 204 | 名前の重なり 409 `GROUP_NAME_DUPLICATE`、使用中 409 `GROUP_IN_USE`、変化なし 409 `GROUP_NO_CHANGE`、無い対象 404 `GROUP_NOT_FOUND` / `USER_NOT_FOUND` |
| 8 メニュー | `GET /api/me/navigation` 200（`emptyReason` は `NOTHING_VISIBLE` / `NOT_CONFIGURED`） | 未認証 401、DSL が無いとき `NOT_CONFIGURED` |
| 9 DSL | プレビュー `POST /api/admin/dsl/preview` 201、適用 `POST /api/admin/dsl/apply` 200 | 検証の失敗 422 `DSL_INVALID`、サイズ超過・形式 `DSL_SUBMISSION_REJECTED`、非管理者 403、未認証 401 |
| 10 表示の設定 | `PUT /api/me/preferences` 200 | 検証 400 `VALIDATION_FAILED`、未認証 401 `AUTHENTICATION_REQUIRED` |
| 11 監査 | 上の操作の後の `audit_events` の種類と件数（`RoleAuditIT`・`UserAdminAuditIT` の形） | 拒否・失敗の操作も種類として残る |

H2 の部品に頼る前提データの作り方の置き換え（`PortableFixture`）: `RoleFixtures` / `UserAdminFixtures` / `TestUserAccounts`（本体のサービスを直接呼ぶ） → 利用者は標準の SQL の `INSERT`（bcrypt cost 4）、ロール・グループ・割り当て・メンバーは初期管理者の API、招待は標準の SQL の `INSERT`（トークンは 32 バイトの乱数の URL 安全な Base64、ハッシュは SHA-256。招待の API は SMTP 未設定だと 503 `INVITATION_NOT_CONFIGURED` で使えない）、DSL はプレビュー → 適用の API。

### Step 2: 使い捨ての確かめ（リポジトリの外。PostgreSQL 18.6・`max_connections` 100）

使い捨てのコード（リポジトリの外の `scratchpad`）を、実際のアプリ（Spring Boot）に対して流した。H2 と比べられるものは H2 でも流した。

1. 行の排他の読み取りの 3 秒台の切れ方と上限の戻り — 計画どおり。
   - 保持側が行を `FOR UPDATE` で持った状態で `POST /api/admin/users/{id}/suspend`: 409 `USER_ADMIN_BUSY`、**3,084 ms**（3 秒以上 4 秒未満）。
   - その直後に、同じ接続（プールの接続を 1 本に絞った状態）で書き込み（`PUT /api/me/preferences`、利用者の行を保持側が持つ）: **2,055 ms** で切れた（接続の `lock_timeout` が 2 秒へ戻っている。ヒントの上限は接続に残らない）。
   - 注意: この書き込みの応答は **500 `INTERNAL_ERROR`**（H2 でも同じ 500・2,052 ms。H2 と同じ）。表示の設定の保存は、上限切れを区分に分けない既存の挙動のため、BR3.1 の「排他の失敗の区分」の確かめには、区分に分ける経路（作業ロールの保存の `Busy`・プレビューを置く）を使う（Step 7）。
2. 行き詰まり（`40P01`）の経路 — 計画の経路が使える。
   - 経路あり: `POST /api/admin/roles/{roleId}/assignments`（本文 `{"groupId": …}`）。アプリはロールの行（`RoleStore.lockRole`）を取ってからグループの行（`GroupMembershipQueryImpl.lockForAssignment`）を取る。保持側がグループの行を先に持ち、待ちを観測した直後にロールの行を取りに行くと輪ができる。
   - 5 回の繰り返しで、毎回 **アプリが犠牲**になり（409 `GROUP_BUSY`、約 1.04〜1.07 秒）、保持側は続けられた（保持側は 40P01 を受けなかった）。待ちの観測は 65〜70 ms で見えた（`wait_event_type = 'Lock'`）。H2 でも同じ応答（409 `GROUP_BUSY`、約 0.33 秒）。
   - 分類: `RowLockFailures` の SQLState の一覧（`HYT00`・`40001`・`55P03`）に `40P01` は無い。Hibernate が `40P01` を JPA の `PessimisticLockException` に変換するため、問い合わせ経由（`EntityManager`）の経路は排他の失敗に分けられる（上の 409 `GROUP_BUSY` で確認）。**未確認の点**: `KeyedUpsertExecutor`（JDBC を直接使う経路）の中の生の `SQLException`（`40P01`）は、一覧に無いため排他の失敗に分けられない可能性がある。BR5.3 の対象は `55P03` なので Step 8 の合否には入れないが、本体の差の候補として「依頼者に確かめたいこと」に載せる（U2 に戻す候補）。
3. 大文字と小文字だけが違う組（H2 と PostgreSQL の両方で実測。結果は同じ）:

| 対象 | 先に作る値 → 後に作る値 | H2 | PostgreSQL |
|---|---|---|---|
| ロールの名前 | `Alpha` → `alpha`、`BETA` → `beta`、`gamma` → `GAMMA`、`Delta` → `Delta` | 先 201、後 409 `ROLE_NAME_DUPLICATE`（4 組とも） | 同じ |
| グループの名前 | 同じ 4 組（`g` を付けた名前） | 先 201、後 409 `GROUP_NAME_DUPLICATE`（4 組とも） | 同じ |
| ログインのメールアドレス | 保存は小文字、`EXIST-USER@example.com` でログイン | 200 | 200（同じ） |
| 招待のメールアドレス | 大文字を含むメールアドレスの招待 | 招待の API は SMTP 未設定で 503 `INVITATION_NOT_CONFIGURED` のため、API では確かめられない（本体はメールアドレスを小文字にそろえてから照らす: `EmailAddress.normalize`） | 同じ |

   つまり期待は「大文字と小文字だけが違うロール・グループの名前は後の作成が重なりとして拒否される（409）。メールアドレスは小文字にそろえて照らす」で、H2 と PostgreSQL は同じ。`PgCaseSensitivityIT`（Step 6）はこの表を期待にする。
4. ログインの状態の SKIP LOCKED の経路 — 期待どおり（H2 と同じ）。
   - ダミーの行 8 つのうち 7 つを保持側が持った状態で、存在しないメールアドレスのログイン: 401 `AUTHENTICATION_FAILED`、**26 ms**（待たず、残りの 1 行を選んだ）。何も持たない状態: 401、17 ms。
   - 8 つすべてを持った状態: 500 `INTERNAL_ERROR`、**3,053 ms**（待つ行を 1 つ選んで 3 秒のヒントで切れる）。H2 も同じ（500、3,040 ms）。
5. `deadlock_timeout` と `pg_stat_activity` — 設計の前提どおり。`SHOW deadlock_timeout` は `1s`。アプリの接続と同じ利用者（データベースの所有者）で `pg_stat_activity` を観測すると、待つ側が `wait_event_type = 'Lock'`・`state = 'active'` として見えた（専用の利用者で足りる。管理者の接続に頼らない）。
6. 接続の数 — アプリ 1 つあたり、プールの上限 30 の接続がすべて起動の直後に作られる（最小の待機数が上限と同じ）。共有のアプリ 1 つ + クラスごとのアプリ 1 つ + 保持側・観測の数本で約 60〜70 本で、`max_connections` 100 の内に収まる（ただし、クラスごとのアプリを同時に 2 つ以上起動しない前提。テストは並列に流さない）。

**外れた前提**: 計画と違った点は無い。注意が要る点は、2 の `KeyedUpsertExecutor` の `40P01` の分類（未確認）と、1 の表示の設定の保存の上限切れが 500 になる既存の挙動（H2 と同じ）。

### Step 3: 共有のテストの部品

`backend/src/test/java/cherry/mastersmith/internaldb/testsupport/` に足した（すべて新しいファイル。H2 の部品に依存しない）。

- `PgApp`（計画に無い部品。クラスごとのアプリと共有のアプリの共通の土台）: 専用のデータベース・実行ごとの乱数の初期管理者でアプリを起動する。起動の失敗は例外の型の名前だけを伝える。
- `PgSharedApp`: JVM で共有するアプリ（最初の使用時に 1 回起動し、`INTERNAL_DB_PG_SHARED_APP startupMs=` の 1 行を出す。JVM の終了の時に閉じる）。
- `ConflictProbe`: 保持側の接続・待つ側を別のスレッドで走らせる部品・`pg_stat_activity`（`wait_event_type = 'Lock'`）を 50 ms の間隔で最大 10 秒観測する部品。10 秒で終わらなければ失敗（件数だけを出す）。`@AfterEach` 用の `awaitNoWaiters()`。
- `WaitWindow`: 呼び出しの開始から失敗が戻るまでを測り、書き込み（2 秒以上 3 秒未満）・行の排他の読み取り（3 秒以上 4 秒未満）の幅で判定し、`INTERNAL_DB_PG_WAIT <名前> waitMs=<値>` を判定の前に出す。幅を広げる口は無い。
- `PortableFixture`: HTTP の API と標準の SQL だけで、利用者・ロール・グループ・割り当て・招待・DSL を作る。初期管理者のログインの資格情報は `PgApp` が起動の引数に乱数で渡し、出力に出さない。

動作の確かめ: `PgSharedAppSmokeIT`（3 件。Step 5 の流れのクラスに吸収してよい）。

### Step 4: 構造のテスト

`backend/src/test/java/cherry/mastersmith/internaldb/PgTestBoundaryArchitectureTest.java`（コンテナを使わない。6 件の確かめ: Pg…IT の注釈、…IT の名前、H2 とH2 向けの部品への依存が無い、`TestDatabase` の `url`・`register` を呼ばない、新しい U3 のクラスが使うテストの部品が固定の一覧の内、ソースに `postgres:` のイメージの記述と H2 固有の SQL が無い）。計画の表の「禁止する依存」と「許す依存」を、そのとおりに固定した。

### Step 5: 11 の機能の代表の流れ（確定した期待）

H2 の参照のテストを読み直して、状態コード・`code`・項目の名前を確定した。PostgreSQL のテストはすべてその値で通った（期待を弱めたものは無い）。メールアドレスは `example.com` だけ、値は出力に出さない。

| クラス（置き場は各機能の `postgres`） | アプリ | 成功の流れ（確定した値） | 代表の失敗（確定した値） |
|---|---|---|---|
| `PgAuthFlowIT` | 共有 | ログイン 200（項目は `accessToken`・`expiresAt`・`user` ちょうど、`user` は `email`・`admin`・`displayName`・`language`・`theme`・`fontSize`、Cookie は `Path=/api/auth/session`・`HttpOnly`・`Secure`・`SameSite=Strict`・`Max-Age=86400`）、更新 200（`accessToken`・`expiresAt`・`user` を含む。Cookie が回る）、ログアウト 204 | 誤ったパスワード 401 `AUTHENTICATION_FAILED`、空のメールアドレス 400 `VALIDATION_FAILED`、Origin 無し 403 `ORIGIN_NOT_ALLOWED`、使い終えた・ログアウト後・Cookie 無しの更新 401 `REFRESH_FAILED` |
| `PgPasswordFlowIT` | 共有 | `POST /api/me/password` 204（本文・Set-Cookie 無し、新しいパスワードだけでログイン 200・古いのは 401） | 現在のパスワードの食い違い 400 `PASSWORD_CURRENT_MISMATCH`（`fieldErrors` 無し）、短い新パスワード 400 `VALIDATION_FAILED`（`fieldErrors` に `newPassword`・`TOO_SHORT`） |
| `PgUserAdminFlowIT` | 共有 | 止める・戻す・ロックの解除（5 回の失敗の後）が 204、止めた間のログイン 401 `AUTHENTICATION_FAILED`・更新 401 `REFRESH_FAILED`、監査 `USER_SUSPENDED`・`USER_RESUMED`・`LOGIN_FAILURES_RESET` が各 1 件増える | 自分への操作 409 `USER_ADMIN_SELF_OPERATION`、変化なし 409 `USER_ADMIN_NO_CHANGE`、管理者でない 403 `ACCESS_DENIED`、未認証 401 |
| `PgInvitationFlowIT` | クラスごと（SMTP の受け手つき） | 招待 201 → メールのトークンで確かめ 200（`email`・`language` ちょうど）→ 完了 204 → ログイン 200 → 招待の一覧の `total` が 0 | 知らないトークンの確かめ・完了が 404 `REGISTRATION_LINK_INVALID` |
| `PgRoleFlowIT` | 共有 | ロール作成 201（`roleId`・`name`・`createdAt`・`updatedAt` ちょうど）、割り当て 204、一覧 200、割り当て解除 204、削除 204、監査 `ROLE_CREATED`・`ROLE_ASSIGNED` が各 1 件増える | 名前の重なり 409 `ROLE_NAME_DUPLICATE`、変化なし 409 `ROLE_NO_CHANGE`、無い利用者 404 `USER_NOT_FOUND`、無いロール 404 `ROLE_NOT_FOUND`、使用中の削除 409 `ROLE_IN_USE`、管理者でない 403 `ACCESS_DENIED` |
| `PgWorkRoleFlowIT` | 共有 | 先頭の割り当てのロールが現在の作業ロール（`GET /api/me/work-role` の `current.roleId`）、別の割り当て済みのロールへ `PUT` 204、監査 `WORK_ROLE_SWITCHED` が 1 件増える | 割り当て外 409 `ROLE_NOT_ASSIGNED`、`roleId` 無し 400 `VALIDATION_FAILED` |
| `PgGroupFlowIT` | 共有 | 作成 201（`groupId`・`name`・`createdAt`・`updatedAt` ちょうど）、メンバー追加 204、詳細 200（`groupId`・`name`・`members`、メンバーは `userId`・`displayName`・`email`・`suspended`）、メンバー削除 204、削除 204 → 詳細 404 `GROUP_NOT_FOUND` | 名前の重なり 409 `GROUP_NAME_DUPLICATE`、無い利用者 404 `USER_NOT_FOUND`、使用中の削除 409 `GROUP_IN_USE` |
| `PgNavigationFlowIT` | クラスごと | 作業ロールが表を `READ` できる利用者に、`GET /api/me/navigation` が 200、`emptyReason` は null、項目 1 つ（`id`・`label`・`icon`・`table`・`navigable`・`items`・`tableKind` ちょうど） | DSL を適用する前は `NOT_CONFIGURED`、適用後に作業ロールが無い利用者は `NOTHING_VISIBLE`（`items` は空）、未認証 401（参照のテスト `emptyReasons` の値） |
| `PgDslFlowIT` | クラスごと | プレビュー 201（`source`・`previewId`・`dslHash`・`at`・`summary`、`summary` は表 1・列 2）→ 適用 200 → 状態の `applied` が埋まり `preview` が空 → 履歴 200、監査 `DSL_SUBMITTED`・`DSL_APPLIED` が各 1 件増える | 不正な DSL 422 `DSL_INVALID`、管理者でない 403、未認証 401 |
| `PgPreferencesFlowIT` | 共有 | 読み取り 200（`displayName`・`language`・`theme`・`fontSize`・`displayTimeZone` ちょうど、初期値は ja・system・md・null）、保存 200（氏名の前後の空白と全角空白が取れる）、保存後の読み取り・ログインの `user` に反映 | 不正な値 400 `VALIDATION_FAILED`、トークン無し 401 `AUTHENTICATION_REQUIRED` |
| `PgAuditFlowIT` | 共有 | ログインの失敗・成功・ロール作成・割り当て・グループ作成・メンバー追加・ログアウトが、種類（`LOGIN_FAILED`/`FAILURE`・`LOGIN_SUCCEEDED`・`ROLE_CREATED`・`ROLE_ASSIGNED`・`GROUP_CREATED`・`GROUP_MEMBER_ADDED`・`LOGGED_OUT`）ごとにちょうど 1 件ずつ増える | 変化なしの割り当て 409 `ROLE_NO_CHANGE` は成功の行を増やさず、管理者でない呼び出し 403 は `ACCESS_DENIED` が 1 件増える |

招待の流れ: 招待の API はメールの送信の設定（SMTP の接続先・差出人・ベース URL）が要るため、H2 の参照のテストと同じく、実際に受けるだけの SMTP の受け手（`SmtpTestServer`）を起動し、`PgApp.startWithMail(受け手の番号)` でアプリに渡した。H2 と同じ形が作れ、期待は H2 と同じにできた（弱めたものは無い）。構造のテストの許す一覧に `SmtpTestServer`・`ReceivedMails`（どちらも H2 に依存しない）を足した。

`PgSharedAppSmokeIT`（Step 3 の部品の動作確認）は、そのまま残した。理由: 共有のアプリの再利用・`ConflictProbe`・`WaitWindow`・DSL の前提の部品（クラスごとのアプリ）を 1 クラスで確かめており、Step 7 の待ちのクラスが先に使う部品の回帰の目印になる。流れのクラスの役割とは重ならない。

### Step 6: 違反の区分と大文字・小文字

- `PgViolationClassificationIT`（クラスごと、SMTP の受け手つき。4 件）: 登録済みのメールアドレスの招待（大文字）409 `INVITATION_EMAIL_REGISTERED`（`invitationId` 無し）、同じメールアドレスの待ち中の招待 409 `INVITATION_ALREADY_PENDING`（`invitationId`・`page` を含む）、ロール・グループの名前の重なり 409、存在しない親（利用者・グループ・ロール）への追加 404 `USER_NOT_FOUND` / `GROUP_NOT_FOUND` / `ROLE_NOT_FOUND`（500 無し）、使用中のロール・グループの削除 409 `ROLE_IN_USE` / `GROUP_IN_USE`。あわせて、参照の残る利用者・ロール・グループを SQL で直接消すと SQLState は `23503`（`23001` は起きない。ADR-002・ADR-004）。
- `PgCaseSensitivityIT`（クラスごと、SMTP の受け手つき。3 件）: Step 2 の表のとおり。ロール・グループの名前は 4 組とも後の作成が 409 `ROLE_NAME_DUPLICATE` / `GROUP_NAME_DUPLICATE`、大文字のメールアドレスでログイン 200、登録済みのメールアドレスを大文字で招待 409 `INVITATION_EMAIL_REGISTERED`。

### 構造のテストの否定の確認

わざと違反を入れたクラス（`PgTmpViolationIT`: 注釈無しの `Pg…IT`・`H2BlockedSessions` への依存・`TestDatabase.url` の呼び出し・`postgres:` の文字列）を 1 回足して構造のテストを流した結果、6 件のうち 5 件が失敗した（Pg…IT の注釈・許す一覧・H2 向けの部品への依存・`TestDatabase` の呼び出し・ソースの検査）。戻して（ファイルを消して）通ることを確かめた。流れの外の「注釈付きなのに名前が `…IT` で終わらない」と `org.h2` の型への依存は、H2 が実行時だけの依存でソースから参照できないため（`org.h2` の参照はコンパイルが通らない）、この確認では起こせていない。

### Step 7: 待ち・行き詰まり・失敗後のトランザクション

すべてクラスごとのアプリ（`PgApp.start()`）。待ちは別スレッドで走らせ、`pg_stat_activity` で待ちを観測してから結果を受け取る（`sleep` は合否に使わない）。時間は呼び出しの開始から測る。

| クラス・確かめ | 決まり | 結果 |
|---|---|---|
| `PgWriteWaitIT` 作業ロールの保存 | BR3.1 | 保持側が行を持つ間、`PUT /api/me/work-role` が 409 `ROLE_BUSY`。`waitMs=2026` |
| `PgWriteWaitIT` プレビューを置く | BR3.1 | 排他の失敗として分類できる例外（`RowLockFailures`）。`waitMs=2005` |
| `PgRowLockWaitIT` 行の排他の読み取り | BR3.2 | suspend が 409 `USER_ADMIN_BUSY`。`waitMs=3047`（3 秒以上 4 秒未満） |
| `PgRowLockWaitIT` 続く書き込み（同じ接続） | BR3.2 | 作業ロールの保存が 409 `ROLE_BUSY`。`waitMs=2034`（上限が 2 秒へ戻る） |
| `PgDeadlockIT` | BR3.3 | `deadlock_timeout` は `1s`。ロールへのグループ割り当て: アプリが 409 `GROUP_BUSY`、保持側は続けた（`elapsedMs=1057`）。合否は「ちょうど一方が失敗し、もう一方は続ける」 |
| `PgSkipLockedLoginIT` 7 つ保持 | BR3.4 | 401 `AUTHENTICATION_FAILED`、`waitMs=16` |
| `PgSkipLockedLoginIT` 8 つ保持 | BR3.4 | 500 `INTERNAL_ERROR`、`waitMs=3045`（3 秒以上 4 秒未満。H2 と同じ） |
| `PgFailureTransactionIT` 名前の重なりの競り合い | BR4.1 | 先の側が確定すると 409 `ROLE_NAME_DUPLICATE`、`ROLE_CREATED`/`FAILURE` が 1 件増え、成功は増えず、次の書き込み（201）・読み取りが通る（25P02 を持ち越さない） |
| `PgFailureTransactionIT` 2 文目の失敗 | BR4.1 | DSL の適用（版の挿入 → プレビューの削除）の 2 文目をトリガーで失敗させる: 500、挿入した版は残らず（件数が元のまま）、プレビューは残り、トリガーを外すと再試行が 200 |

`PgFailureTransactionIT` の 2 文目の失敗は、成功の監査（ロールの作成）の書き込みを失敗させる案を試したが、成功の監査は同じトランザクションで書かれず（201 のまま）合否にならなかったため、同じトランザクションで 2 文を書く DSL の適用に変えた。

### Step 8: 書き直した SQL と鍵の重なり

- `PgPortableSqlIT`（5 件。アプリに `StatementCounter.Config` を足す）: 作業ロールの保存は初回 2 文・2 回目と同じ値の再保存は 1 文、接続は 1 本。プレビューを置くは初回 2 文・2 回目 1 文、接続 1 本。`savePreferences` は 1 文・接続 1 本、存在しない利用者は空で 1 文、変更しないフラグで時差が残り、null で消える。存在しない利用者への作業ロールの保存は `Referenced(USER)`。いずれも H2 の参照のテストと同じ値。
- `PgKeyConflictIT`（3 件、別のメソッド）: (a) 作業ロールの主キーと (b) プレビューの主キーは、先の側の確定後に収束して後の値が残り、同じトランザクションが続けられる。(c) `preview_id` の一意の制約は重なりと扱わず、SQLState `23505` の失敗として出て、トランザクションは続けられ、スロット 1 の行は作られない。(c) は 1 つの表に 1 行しか置けない検査（`ck_dsl_previews_slot`）を専用のデータベースの中で一時的に外し、スロット 2 に同じ `preview_id` を置いて起こした（終わったら戻す）。
- `PgLockFailureClassificationIT`（4 件）: 実際の PostgreSQL の生の `55P03` の `SQLException`、それを JPA の例外で包んだ形（2 重も）が `RowLockFailures` に当たる。アプリの経路では、作業ロールの保存が `Busy(WORK_ROLE_SELECTION_KEY)`、プレビューを置くが排他の失敗として分類できる例外。

### 観測（合否に入れない）

- **`KeyedUpsertExecutor` の中の `40P01`（本体の差の疑い）**: 作業ロールの保存の更新が行き詰まり（保持側が輪を閉じる）の犠牲になると、`RoleStore.saveWorkRoleSelection` は `Busy` ではなく `RoleStoreUnexpectedException`（想定外。500 になる）を投げる（`INTERNAL_DB_PG_OBSERVE keyedUpsertDeadlock app{thrown=RoleStoreUnexpectedException lockFailure=false} holderSqlState=null`）。`RowLockFailures` の SQLState の一覧（`HYT00`・`40001`・`55P03`）に `40P01` が無く、`KeyedUpsertExecutor` が JDBC で直接受ける形は Hibernate の変換（行き詰まり → `PessimisticLockException`）を通らないため。`EntityManager` 経由の経路（ロールへのグループの割り当て）は 409 `GROUP_BUSY` になる。U2 に戻す候補（`40P01` を排他の失敗に足す）。
- **起動の出力の接続先の URL**: アプリの起動の出力（Flyway の INFO）に、資格情報を含まない接続先の URL が出る（`INTERNAL_DB_PG_OBSERVE startupOutputContainsConnectionUrl=true`）。利用者・パスワード・初期管理者の資格情報は出ない。`project.md` の Forbidden は資格情報を含む URL を禁じるため違反ではないが、DD-12 の「接続先」の読み方は依頼者に確かめたい。

### Step 9: 型・時刻・検索・出力の検査

- `PgBinaryRoundTripIT`（3 件）: 全バイト値を含む 10,485,760 バイトの本文が、プレビューの表を往復して長さと SHA-256（データベースの `sha256` とクライアントの両方）が一致する。API では、上限ちょうどの有効な DSL が 201 で全長のまま保存され、+1 バイトは 413 `DSL_TOO_LARGE`（本文を送らず Content-Length だけで断る）。先頭 0x00・末尾 0xFF（とその逆）の 32 バイトのハッシュを、リフレッシュトークン（更新 200）と招待（確かめ 200）の両方で保存し、保存したハッシュが 32 バイトのまま一致する。ヒープは 1g のまま、メモリ不足は起きなかった。
- `PgTimeRoundTripIT`（4 件）: JVM の既定の時差（UTC・Asia/Tokyo・America/New_York）× PostgreSQL のセッションの時差（UTC・Asia/Kolkata・America/New_York）× 5 つの時点（夏時間の境の前後・マイクロ秒の端数つき）で、アプリが読んだ時点も SQL が読んだ時点も書いた時点と同じ。注入した時計で、リフレッシュトークンの有効期限（24 時間の 1 秒前は更新でき、回した後のトークンは期限ちょうどで 401 `REFRESH_FAILED`）、招待の有効期限（1 マイクロ秒前は 200・204、ちょうどと直後は 404）、ログインのロックの解除（30 分後に 200）が、H2 の参照のテストと同じ。クラスの終わりで既定の時差が元に戻ったことを `assertThat` で確かめる。
- `PgUserSearchIT`（2 件）: 21 人・25 の検索語で、結果の集合がすべて固定の期待と同じ（`%`・`_`・`\` は一致の条件として働かない。大文字と小文字違い・全角半角・カナ・ß・結合文字・連続空白）。同じ見本と検索語を H2 でも流して、同じ結果であることを確かめた（食い違う入力は見つからなかった）。254 文字は 200、255 文字は 400。
- `PgSecretLeakIT`（1 件）: アプリの起動から初期管理者のログインまでの出力に、データベースの利用者・パスワード・初期管理者のパスワード・メールアドレスが無い。BR2・BR3・BR5 の 3 クラス（`PgViolationClassificationIT`・`PgWriteWaitIT`・`PgKeyConflictIT`）に、作った値（メールアドレス・ロールとグループの名前）がアプリのログに出ない確かめを足した（DD-13）。

### 待ちの確かめの時間（`INTERNAL_DB_PG_WAIT`。Step 7・8 を 1 回まとめて流したとき）

| 名前 | 時間（ms） | 幅 |
|---|---|---|
| `PgWriteWaitIT.workRoleSaveIsBusy` | 2026 | 2000 以上 3000 未満 |
| `PgWriteWaitIT.previewPlaceIsALockFailure` | 2005 | 2000 以上 3000 未満 |
| `PgRowLockWaitIT.rowLockRead` | 3047 | 3000 以上 4000 未満 |
| `PgRowLockWaitIT.nextWriteOnTheSameConnection` | 2034 | 2000 以上 3000 未満 |
| `PgSkipLockedLoginIT.allHeldWaitForTheHint` | 3045 | 3000 以上 4000 未満 |
| `PgSkipLockedLoginIT.sevenHeldDoNotWait` | 16 | 待たない（1500 未満） |
| `PgDeadlockIT`（行き詰まりの検出まで） | 1057 | 10000 未満 |

## 流したコマンドと結果（Step 7・8 の依頼の分）

- 各クラスを単独で（`--tests`、colima の 2 つの環境変数つき、並べずに）通した: `PgWriteWaitIT`・`PgRowLockWaitIT`・`PgDeadlockIT`・`PgSkipLockedLoginIT`・`PgFailureTransactionIT`・`PgPortableSqlIT`・`PgKeyConflictIT`・`PgLockFailureClassificationIT`・`PgSecretLeakIT`（`PgFailureTransactionIT` は 2 文目の失敗の作り方を変えて、`PgSecretLeakIT` は接続先の URL を観測に変えて通した）。
- まとめて 1 回: `caffeinate -dims ./gradlew :backend:internalDbPostgresTest --tests …`（上の 9 クラス）: 22 件が通る（`BUILD SUCCESSFUL in 48s`）。
- `./gradlew :backend:test --tests 'cherry.mastersmith.internaldb.PgTestBoundaryArchitectureTest'`: 通る（許す一覧に `StatementCounter` を足した）。

## 流したコマンドと結果（Step 5・6 の依頼の分）

- 各クラスを単独で（`--tests`、colima の 2 つの環境変数つき、並べずに）: `PgAuthFlowIT`・`PgPasswordFlowIT`・`PgPreferencesFlowIT`・`PgUserAdminFlowIT`・`PgRoleFlowIT`・`PgWorkRoleFlowIT`・`PgGroupFlowIT`・`PgInvitationFlowIT`・`PgDslFlowIT`・`PgNavigationFlowIT`・`PgAuditFlowIT`・`PgViolationClassificationIT`・`PgCaseSensitivityIT` が通る（`PgNavigationFlowIT`・`PgAuditFlowIT` は最初の実行で期待の読み違い・基準の数え方を直して通った）。
- まとめて 1 回: `caffeinate -dims ./gradlew :backend:internalDbPostgresTest --tests 'cherry.mastersmith.*.postgres.Pg*IT'`: 14 クラス・31 件が通る（`BUILD SUCCESSFUL in 30s`。共有のアプリの起動 7,298 ms、`PgSharedAppSmokeIT` の待ち 2,051 ms）。
- `./gradlew :backend:test --tests 'cherry.mastersmith.internaldb.PgTestBoundaryArchitectureTest'`: 6 件が通る。

## 流したコマンドと結果（Step 1〜4 の依頼の分）

- 骨組みの確かめ（`InternalDbPostgresBaselineIT`）: 通る。
- `./gradlew :backend:internalDbPostgresTest --tests 'cherry.mastersmith.internaldb.postgres.PgSharedAppSmokeIT'`（colima の 2 つの環境変数つき）: 通る（3 件。`INTERNAL_DB_PG_WAIT PgSharedAppSmokeIT.holderMakesAppWriteWait waitMs=2158`、`INTERNAL_DB_PG_SHARED_APP startupMs=2625`）。
- `./gradlew :backend:test --tests 'cherry.mastersmith.internaldb.PgTestBoundaryArchitectureTest'`: 通る（6 件。最初の実行は ArchUnit の読み込みを含めて 1.9 秒、続けて流したときは 0.5 秒）。
