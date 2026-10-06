# Code Generation Plan — U1 横断の準備（cross-cutting）

- 単位: U1 cross-cutting（kind: library、大きさ M）
- Bolt: B2（承認済みの Bolt の計画の番号）。依頼者の決定で dsl-v2 の B1 と順を入れ替え、**この Intent で最初に作る Bolt** とする（3節）。
- 範囲と量: スコープ classic、Test Strategy Standard（部品ごとに 5〜8 件、要所の結合テスト）、方法は test-after（10節の後の Testing Contract）。
- この計画は Part 1（計画）だけで、計画の承認の前にコード・テスト・設定を書かない。

## 1. 入力にした設計

| 段 | 文書（どれも `aidlc/spaces/default/intents/261004-role-menu/` の下） | 使う所 |
|---|---|---|
| Functional Design | `construction/cross-cutting/functional-design/functional-spec.md`（W1〜W7、3節の 34 の口、10節の直し）・`rules.md`（BR1.1〜BR5.6）・`entities.md`（ENT-001〜ENT-009）・`frontend-components.md` | 作るものの形と決まり |
| NFR Requirements | `construction/cross-cutting/nfr-requirements/security-requirements.md`（NFR1.1〜NFR1.10・NFR4.1〜NFR4.5・NFR6.1〜NFR6.8、2.2 の PUBLIC の一覧、4節の直し）・`tech-stack-decisions.md` | 確かめの要件、新しい依存を足さないこと |
| NFR Design | `construction/cross-cutting/nfr-design/security-design.md`（2節の捨ての試し、4.2〜4.6・5.1〜5.4、9節の直し）・`logical-components.md` | 検査と検査の検査の形、部品の置き場 |
| Infrastructure Design | `construction/cross-cutting/infrastructure-design/cicd-pipeline.md`（2〜11節） | `verify` の段への載せ方、E2E、統合 |
| Inception | `inception/units-generation/unit-of-work.md`（U1）・`unit-of-work-story-map.md`・`unit-of-work-dependency.md`、`inception/requirements-analysis/requirements.md`（NFR1.3・NFR4.1〜NFR4.3・NFR6.4・C4・C5）、`inception/user-stories/stories.md`（AC1.1.15）、`inception/contract-design/contract-summary.md`（C1・C2）、`inception/domain-design/components.md`（AccessControl・SharedTreeView・AppFrame）、`inception/delivery-planning/bolt-plan.md`（B2） | 範囲・ID・終わりの条件 |
| 読み直しの記録 | `.aidlc-reviews/{functional-design,nfr-requirements,nfr-design,infrastructure-design}/units/cross-cutting/*/1.json` | 2.2 の Minor・Suggestion の扱い |
| 既存のコード（読み取りだけで確かめた） | `backend/build.gradle.kts`（`packagesJudgedByTotal` 7 パッケージ、`*Test`→`test`・`*IT`→`integrationTest`）、10 のコントローラー、`ArchitectureTest`・機能ごとの境界テスト 11 個、`common/testsupport/TestFixtureEndpoints`（設定の値の条件つき）、`frontend/eslint.config.js`・`vitest.config.ts`・`vitest.setup.ts`・`app/registry/types.ts`・`app/login-state/LoginStateGate.tsx`・`features/registration/useRegistration.ts`・`features/auth/loginStateProvider.ts`・`frontend/e2e/`（13 本） | 影響の範囲 |

## 2. 承認の場の決定と、この計画での読み方

### 2.1 承認の場の決定（監査ログの GATE_APPROVED・GATE_REJECTED と各文書の「承認の場の決定」の節から洗い出したもの）

| 段（日付） | 監査ログの User Input・Feedback | この単位に当たる中身 | この計画での扱い | 手順 |
|---|---|---|---|---|
| Contract Design（2026-10-04） | Approve（R-09 は Accepted risk） | C1・C2 の形はそのまま | C1（注釈・ADMIN と `AdminPaths` の両方向）、C2（登録の型・共有の木）のとおりに作る | Step 4〜19 |
| Delivery Planning（2026-10-04） | Approve | B2 の終わりの条件（印・構造の検査・ESLint・違反の直し・共有の木・登録の型・`verify`） | そのまま。順は下の「Bolt の順の入れ替え」の行 | 全体 |
| Functional Design（2026-10-05） | GATE_REJECTED「推奨の案のとおり直す」→ Approve | R-01: 実行時の検査の主体を3つ（未ログイン・管理者でない利用者・管理者）。R-02: 実行時の検査の対象を本番のクラスの口に絞り、静的な検査と集合を合わせる | 主体の手伝いで3つの主体を作り、期待の表を3列で持つ。集合の一致を確かめる | Step 9 |
| NFR Requirements（2026-10-05） | GATE_REJECTED「Major 11 件だけを直す」→ Approve | R-01: PUBLIC の一覧は口の単位で 9 行（方法の集合と道の型の集合の組）、暗黙の HEAD・OPTIONS は足さない、`server.error.path` は解決した値、パス変数は変数名つきの型のまま | `PublicApiInventory` の定数 9 行と比べ方をこのとおりに作る | Step 8・9 |
| NFR Design（2026-10-06） | GATE_REJECTED「推奨の案のとおり直す」→ Approve | R-01: 検査の検査（`ApiAccessRules` と違反の見本6つと `ApiAccessRulesTest`、`PublicApiInventory.entryOf`・`diff` と `PublicApiInventoryTest`） | そのまま作る | Step 7・8 |
| Infrastructure Design（2026-10-06） | Approve（まとめの確認は Looks correct） | この単位には Major が無い。承認のコミット（c97f760）にある「Major 5 件の直し方はコード生成の計画へ」は他の単位（navigation など）のもので、この単位には当たらない（各単位の `infrastructure-design` の文書で確かめた）。Minor 3 件・Suggestion 2 件は 2.2 | CI・`verify` の段・Gradle のタスク・Vitest の設定を変えない | Step 23 |
| Code Generation の始め（依頼者の決定、指揮役から伝達） | cross-cutting を最初に作る（dsl-v2 の B1 と順を入れ替える） | 要件 C5・`team.md` の「今ある違反の1件は、この Intent で最初に手を入れる Bolt で直す」は、この Bolt が受け持つ形になる（入れ替えの後も中身は同じ）。dsl-v2 の設計の「API の分類の印は U1 が B2 で付ける（この単位の後）」は「前」に変わる。dsl-v2 は API の道と数を変えないため、`DslAdminController` の印はこの Bolt で付き、B1 は印を保つだけ（外すと Step 7 の検査で落ちる） | 3節。Bolt の番号は B2 のまま（10節 Q-A: A） | Step 1・25 |

### 2.2 読み直しの Minor・Suggestion の扱い（承認の場で直していないもの）

| 読み直し | ID | 中身 | この計画での扱い | 手順 |
|---|---|---|---|---|
| Functional Design | R-02（Resolved、残り） | 本番のクラスかどうかの判定の具体 | 実行時の検査の口の集合を、ArchUnit の `DO_NOT_INCLUDE_TESTS` で読んだ本番のクラス名の集合と突き合わせ、本番に無いクラス（test の出力のクラス）の口が入っていたら落とす。`cherry.mastersmith` の前置きだけでは絞らない | Step 9 |
| Functional Design | R-03 | 提供元に `logout` が無いと、登録の完了の画面が「ログアウト中」のまま戻らない | 依頼者の答え Q-B: B。`useLogout` が呼べたかを `Promise<boolean>` で返し、呼べなければ登録の完了の画面を元に戻す（9節 D-7） | Step 14・15 |
| Functional Design | R-04 | 機能の中の下位のディレクトリの名前と兄弟の機能の名前が重なると誤検出。道の別名の import は判定できない | 依頼者の答え Q-C: A。`eslint.config.js` が設定を作る所で重なりを見つけたら、設定の読み込みで失敗させる（9節 D-8）。道の別名は今の `tsconfig*.json`・`vite.config.ts` に無いことを確かめた（Step 18 で再確認） | Step 18・19 |
| Functional Design | R-05 | 共有の木の形は make-you-chic-ui の 5bf1ffe の報告だけが根拠 | この Bolt の範囲外。B9（U7）の最初に実物と合うかを確かめることを「Build and Test に引き継ぐこと」に書く | — |
| Functional Design | R-06 | `ArchitectureTest` が `common` から機能への依存を受け持つ根拠が無い | `ArchitectureTest` にその決まりは無い（読んで確かめた）。`common.security` が機能に依存しないことは `ApiAccessArchitectureTest` の規則（BR1.8）で確かめ、`common` の他のパッケージには広げない（既存の検査は変えない） | Step 7 |
| NFR Requirements | R-02〜R-07 | 試しの確認項目・icon の行・`access.service` の持ち主・木の深さ・ESLint の例・広げ方 | NFR 設計（2.2・5.1〜5.3・4.6・7節）で手当て済み。そのとおりに作る | Step 7〜19 |
| NFR Design | R-02 | 実行時の検査の起動の形（`webEnvironment` と `@Import`）が決まっていない | 既存の `access/web/ApiDefaultAccessIT` と同じ形（飾りの無い `@SpringBootTest(webEnvironment = RANDOM_PORT)`、`@TempDir` と `@DynamicPropertySource` で `TestDatabase.register`、`@Import`・`@TestPropertySource` なし）にする。既存の結合テストはクラスごとに一時の内部DB の道を設定するため、起動の文脈は使い回されず1回増える。依頼者の答え Q-E: A で、起動が1回増えることを受け入れ、増え方は Build and Test で実測する（9節 D-2） | Step 9・23 |
| NFR Design | R-03 | test の出力のクラスが口の集合に入りうる | FD R-02 と同じ手当て（本番のクラス名の集合と突き合わせ、外れたら落とす） | Step 9 |
| NFR Design | R-04 | NFR 設計の traceability.json に NFR2・NFR3・NFR5 の N/A が無い | コード生成の traceability.json に NFR2・NFR3・NFR5 を N/A と理由（API・データ・監査の出来事・指標を持たない）つきで載せる | Step 22 |
| NFR Design | R-05 | 判定用の URI の見本の値が `x` と `1` で食い違って見える。試しの結果を本番の検査で再確かめ | 見本の値は `1` にそろえる。2.2 の点 1〜7 と「口 34・PUBLIC 9 口（14 組）・食い違い 0」を、本番の検査の結果として Step 9 で記録する | Step 9 |
| NFR Design | R-06 | 静的な検査は道を既定値で読むため、設定を変えた環境とのずれは静的側では見えない | `ApiAccessArchitectureTest` の Javadoc に「ずれを見るのは実行時の検査（解決した道）だけ」と書く | Step 7 |
| NFR Design | R-07 | 規則の形（対象を絞る位置）と、`ValidSample` に AUTHENTICATED の口が無い | 規則は「口をすべて対象に取り、条件の中で印ごとに場合分けする」形にする。見本に当てる規則は「印は一方だけ」「ADMIN と `AdminPaths`」「AUTHENTICATED の道」の3つに限り、「`common.security` が機能に依存しない」は本番のクラスだけに当てる。`ValidSample` に AUTHENTICATED の口（`/api/sample`）も足す | Step 7 |
| NFR Design | R-08 | `PublicApiEntry` の形が2通りに読める | `PublicApiEntry(Set<String> methods, Set<String> patterns)` の1つに決め、一覧の定数・`entryOf` の戻り値・テストの見本をすべてこの形にする | Step 8 |
| Infrastructure Design | R-01 | ESLint の決まりのテストを node 環境で流すとき、共通の `vitest.setup.ts` が害を出さないかが未確かめ | テストの最初の実行の前に、一時の確かめのファイル（コミットしない）で node 環境と `vitest.setup.ts` の読み込みを確かめる。動かなければ `vitest.setup.ts` を触らず、そのテストだけ jsdom のまま `ESLint` を使う形に切り替え、記録する | Step 2・19 |
| Infrastructure Design | R-02 | 補助ファイルを `src/` に置くと計測の分母に入る | 見本の文は `*.test.ts` の中に置き、`src/` に補助のファイルを作らない（計測の除外は増やさない） | Step 19 |
| Infrastructure Design | R-03 | infra の traceability.json の N/A と `cicd-pipeline.md` の段 5 の「当たる要件」が食い違って読める | コード生成の traceability.json では、NFR4.1〜NFR4.4・NFR1.9 を、確かめるテスト（段 5）を target に書いて OK にする（基盤の追加は無い旨を添える） | Step 22 |
| Infrastructure Design | R-04 | `common.health`・`common.web` に口が無いことの確かめ方 | `@RestController`・`@Controller` の検索で、口を持つクラスは 10 で `common.health`・`common.web` に無いことを確かめた（`GlobalExceptionHandler` は `@RestControllerAdvice` で口ではない）。Step 3 で同じ検索を記録に残す | Step 3 |
| Infrastructure Design | R-05 | 確認の記録 | 対応なし | — |

### 2.3 この計画での読み方

- **設計の文書どおりに作るもの**: 注釈と値（ENT-001・ENT-002）、34 の口への印（functional-spec 3節）、静的な検査・検査の検査・実行時の検査・PUBLIC の一覧（security-design 4.2〜4.4.1）、境界テスト2つ（4.6）、共有の木（frontend-components 2節）、登録の型と登録の検査（3節）、ログアウトの口（4節）、ESLint の制限（5節）。
- **層の順（test-after）**: Testing Contract の `ordering` のとおり、層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて流し、通ってから次の層へ進む。この単位の層は「バックエンドの注釈（ドメイン）→ 既存の口への印（API）→ 検査と検査の検査（API の層のテスト）→ 境界テスト → 画面の登録の型と登録の検査 → ログアウトの口と既存の違反の直し → 共有の木 → ESLint の制限」の順とする。データの形（表）・DB アクセス・業務処理の層は持たない（Testing Contract の `plan_profile` の該当しない層として省く）。
- **ESLint の制限は最後**: 本体の違反（`useRegistration.ts` の1件）を Step 14 で直した後に入れ、入れた時点で違反 0 件にする（BR2.4）。
- **本番の安全の決まりは変えない**: `SecurityConfig`・各機能の `SecurityRuleContributor`・`AdminAuthorizationManager`・`ApiDefaultAccess`・`AdminPaths` は読むだけ（security-design 3節 L4）。

## 3. Bolt・ブランチ・統合・コミットの区切り

| 項目 | 扱い | 出典 |
|---|---|---|
| Bolt の番号 | 承認済みの番号 B2 のまま使い、実行の順だけを入れ替えたことを記録する（B2 → B1 → B3 …）。番号は振り直さない（依頼者の答え Q-A: A） | 依頼者の決定、`bolt-plan.md` |
| 作業ブランチ | `develop` の先頭から `feature/261004-role-menu-b2`（案）。同じ作業フォルダで作り、worktree は使わない。作るのは依頼者の承認の後 | `team.md` の Way of Working |
| 統合の前の関門 | コンテナの実行環境（colima）を起動し、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡して `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（対象DB のテストを飛ばさない）。続けて E2E の全体（`./gradlew e2eTest`、Mailpit を起動しておく）。どちらも通るまで統合しない | `team.md`・`project.md` の学び、`cicd-pipeline.md` 2・7節 |
| 統合の形 | `develop` への squash（1 Bolt が `develop` の1コミット）。サブモジュールの更新を含まないため fast-forward の例外に当たらない。単位は1つのため単位ごとの squash の選択は無い | `team.md` |
| 統合コミットの件名（案） | `B2 横断の準備: API の分類の印と構造・実行時の検査、画面の import の制限、共有の木、登録の型の拡張`（日本語） | `team.md` |
| 統合の後 | 作業ブランチを消す。`origin` への push は依頼者が行う。push の後の CI の失敗は `team.md` の「不安定なテストと CI の失敗」の決まりで扱う | `team.md` |
| 作業ブランチの上のコミットの区切り（案） | C1 バックエンド: 注釈と既存の 34 の口への印（Step 4〜6）／C2 バックエンド: 静的な検査・検査の検査・PUBLIC の一覧・実行時の検査・境界テスト（Step 7〜10）／C3 画面: 登録の型と登録の検査・ログアウトの口と既存の違反の直し（Step 12〜15）／C4 画面: 共有の木（Step 16・17）／C5 画面: ESLint の制限とそのテスト・文書（Step 18〜20） | `project.md` の Change Control |
| コミットの進め方 | 生成の担当はコミットしない。生成の後に指揮役が C1〜C5 の区切りを依頼者に提案し、承認を得てから行う（段の記録のコミットも別に提案する）。`aidlc/` の未コミットの変更（監査ログを含む）は squash の前にコミットしておき、`git restore` で消さない | `project.md` の Change Control の学び |
| 承認の場の時点 | コード生成の段の承認の場は、最後のコードのコミットの後、記録だけのコミット（`aidlc/` の下だけ）を積む前に開く | `project.md` の学び |

## 4. 作るもの・手を入れるもの

### 4.1 バックエンドの本体（`backend/src/main/java/cherry/mastersmith/`）

| ファイル | 種類 | 中身 | 出典 |
|---|---|---|---|
| `common/security/ApiAccess.java` | 足す | 注釈（`RUNTIME`、`TYPE`・`METHOD`）、値 `ApiAccessLevel value()` を1つ。Javadoc に分類の意味・1つの口に1つだけ・新しい口に付ける決まり | ENT-002、C1 |
| `common/security/ApiAccessLevel.java` | 足す | `PUBLIC`・`AUTHENTICATED`・`ADMIN` の enum。値の意味を Javadoc に書く | ENT-001 |
| `auth/web/AuthController.java` | 印 | `@ApiAccess(PUBLIC)`（クラス） | functional-spec 3節 |
| `appearance/web/AppearanceController.java` | 印 | `@ApiAccess(PUBLIC)`（クラス） | 同上 |
| `common/error/web/ProblemTypeController.java` | 印 | `@ApiAccess(PUBLIC)`（クラス） | 同上 |
| `common/error/web/ErrorPathController.java` | 印 | `@ApiAccess(PUBLIC)`（クラス） | 同上 |
| `invitation/web/RegistrationController.java` | 印 | `@ApiAccess(PUBLIC)`（クラス） | 同上 |
| `user/web/MeController.java` | 印 | `@ApiAccess(AUTHENTICATED)`（クラス） | 同上 |
| `useradmin/web/UserAdminController.java` | 印 | `@ApiAccess(ADMIN)`（クラス） | 同上 |
| `dslmanage/web/DslAdminController.java` | 印 | `@ApiAccess(ADMIN)`（クラス） | 同上 |
| `access/web/AdminCheckController.java` | 印 | `@ApiAccess(ADMIN)`（クラス） | 同上 |
| `invitation/web/InvitationAdminController.java` | 印 | `@ApiAccess(ADMIN)`（クラス） | 同上 |

- 印は注釈1行と import だけで、口の道・方法・中身・本番の安全の決まりは変えない。
- 手を入れるパッケージは `common.security` と8つの `web`（`auth.web`・`appearance.web`・`common.error.web`・`invitation.web`・`user.web`・`useradmin.web`・`dslmanage.web`・`access.web`）。どれも `packagesJudgedByTotal` に無い（5節）。

### 4.2 バックエンドのテスト（`backend/src/test/java/cherry/mastersmith/`）

| ファイル | 種類 | 中身 | 出典 |
|---|---|---|---|
| `common/security/ApiAccessTest.java` | 足す（`*Test`） | 注釈の保持（`RUNTIME`）・付けられる所（`TYPE`・`METHOD`）・値が3つちょうど | ENT-001・ENT-002 |
| `common/testsupport/ApiAccessRules.java` | 足す（手伝い） | 口の集め方（`@RestController`・`@Controller` のクラスの RequestMapping 系の方法）と規則（印は一方だけ・ADMIN と `AdminPaths`・AUTHENTICATED の道・`common.security` は機能に依存しない）を作る1か所。規則は `allowEmptyShould(false)` | security-design 4.2・4.2.1 |
| `common/testsupport/apiaccess/{NoMarkSample,DoubleMarkSample,AdminOutsideSample,AdminPathNotAdminSample,AuthenticatedOutsideApiSample,ValidSample}.java` | 足す（見本） | 違反を1つずつ持つ見本と違反なしの見本。`@ConditionalOnBooleanProperty("mastersmith.test-fixture.api-access-samples")` で Bean にしない | 4.2.1、2.2 の R-07 |
| `ApiAccessArchitectureTest.java` | 足す（`*Test`、全体の置き場） | 本番のクラスに規則を当てる。口の数 34 以上 | 4.2、BR1.1〜BR1.3・BR1.8 |
| `ApiAccessRulesTest.java` | 足す（`*Test`、全体の置き場） | 見本ごとに落ちる・`ValidSample` は通る・空の集まりは落ちる | 4.2.1 |
| `common/testsupport/PublicApiInventory.java` | 足す（手伝い） | `PublicApiEntry(Set<String> methods, Set<String> patterns)`、一覧の定数 9 行（理由のコメントにクラス名と方法名）、`entryOf(RequestMappingInfo)`、`diff(expected, actual)` | 4.4・4.4.1、要件 2.2 |
| `PublicApiInventoryTest.java` | 足す（`*Test`、全体の置き場） | 4つの境界（足す・外す・変数名・`/error` の方法の増減）と変えない場合、`entryOf` の確かめ | 4.4.1 |
| `common/testsupport/ApiAccessSubjects.java` | 足す（手伝い） | 匿名のトークン、管理者でない利用者・管理者の `AuthenticatedUserToken(new AuthenticatedUser(固定の ID, example.com の見本, admin))`。内部DB に利用者を作らない | 4.3 手順 6、NFR1.10 |
| `ApiAccessConsistencyIT.java` | 足す（`*IT`、全体の置き場） | 3つの主体の判定、静的な検査との集合の一致、本番のクラスだけであること、口 34 以上、PUBLIC の一覧との比べ | 4.3・4.4、BR1.4〜BR1.6 |
| `access/AccessBoundaryArchitectureTest.java` | 足す（`*Test`） | `access` の今の依存をそのまま書く（今は `auth.domain`・`auth.web`・`common.*`・`config` に依存し、`audit`・`useradmin` から依存される） | 4.6、C4 |
| `user/UserBoundaryArchitectureTest.java` | 足す（`*Test`） | `user` の今の依存をそのまま書く（今は `common.*` だけに依存し、`audit`・`auth`・`dslmanage`・`invitation`・`useradmin` から依存される） | 4.6、C4、functional-spec 9節 5 |

- 既存の `ArchitectureTest`・機能ごとの境界テスト・`archunit.properties`（`archRule.failOnEmptyShould = false`）は変えない。
- 境界テストに書く依存は、Step 10 で import を数え直して決める（上の括弧は計画の時点の読み取り）。

### 4.3 画面の本体（`frontend/src/`）

| ファイル | 種類 | 中身 | 出典 |
|---|---|---|---|
| `app/registry/types.ts` | 変える | `SidebarSection = 'ADMIN'`、`SidebarItemRegistration` に必須の `section` と任意の `icon?: IconName`、`LoginStateProvider` に任意の `logout?: () => Promise<void>` | ENT-003〜ENT-005 |
| `app/registry/allowedIcons.ts`（名前は案） | 足す | 照合の一覧 18 個を `as const satisfies readonly IconName[]` と逆向きの網羅の型の確かめで持つ | security-design 5.2 |
| `app/registry/validateRegistrations.ts` | 変える | section の値・section ADMIN と visibleWhen ADMIN・icon の名前（一覧に無い・空の文字は問題）を足す | frontend-components 3.2、BR5.1・BR5.3・BR5.4 |
| `features/{admin,dsl,invitation,useradmin}/registration.ts` | 変える | 4件の項目に `section: 'ADMIN'`（icon は足さない） | BR5.6 |
| `app/login-state/LoginStateGate.tsx` | 変える | 受け取った提供元の `logout` を別の文脈で渡し、`useLogout()` を出す。`useLogout()` が返す関数は `Promise<boolean>`（提供元の `logout` を呼べたら真、無ければ偽。失敗は外へ出さず真）（Q-B: B） | frontend-components 4節、BR3.2 |
| `features/auth/loginStateProvider.ts` | 変える | `logout`（`authSession` の `logout`）を渡す | BR3.1 |
| `features/registration/useRegistration.ts` | 変える | `../auth/authSession` の import を消し、`useLogout()` を使う。偽（呼べなかった）なら `loggingOut` を解いて `loggedIn` に戻し、押し直せるようにする（Q-B: B） | BR3.2・BR3.3、C5 |
| `shared/tree/{SharedTreeView.tsx,SharedTreeNodeItem.tsx,types.ts,SharedTreeView.css}` | 足す | 共有の木 | frontend-components 2節 |
| `../eslint.config.js`（`frontend/eslint.config.js`） | 変える | `src/features/` のディレクトリの一覧から機能ごとの `no-restricted-imports`、`src/shared/**` の決まり、テストのファイルは対象外、機能の中の下位のディレクトリの名前と機能の名前の重なりを見つけたら設定の読み込みで失敗させる（Q-C: A。検出の関数はテストできるよう設定の中で切り出す） | frontend-components 5節、BR2.1〜BR2.3 |
| `features/README.md` | 変える | 登録の `section`・`icon`、`useLogout`、機能どうしの import の制限、共有の木の使い方を足す | 文書 |

- `app/navigation/navigationItems.ts`（今の `buildSidebarEntries`）は区画を読まない（BR5.6。区画ごとの組み立ては U7）。
- `vendor/make-you-chic-ui` は変えない（固定先 e82b651 のまま。`IconName` は入口 `src/index.ts` から型として読める）。

### 4.4 画面のテスト（対象と同じ場所の `*.test.ts(x)`）

| ファイル | 種類 | 中身 |
|---|---|---|
| `app/registry/validateRegistrations.test.ts` | 足す（既存のファイル） | section・visibleWhen・icon の境界 |
| 既存のテストの見本の登録（`app/navigation/navigationItems.test.ts`・`app/layout/ShellLayout.test.tsx`・`app/routing/AppRouter.test.tsx`・`app/admin-forbidden/*.test.ts(x)` など、`SidebarItemRegistration` を組み立てるもの） | 直す | 必須の `section` を足して型の検査を通す（振る舞いの確かめは変えない） |
| `app/login-state/LoginStateGate.test.tsx` | 足す（既存のファイル） | `useLogout` の場合（呼べたら真・提供元に `logout` が無ければ偽・失敗は外へ出さない・提供元が無ければ偽） |
| `features/auth/loginStateProvider.test.ts` | 足す（既存のファイル） | 提供元が `logout` を持ち、`authSession` の `logout` を呼ぶ |
| `features/registration/RegistrationPage.test.tsx` | 足す（既存のファイル） | 提供元に `logout` が無いと「ログアウトして続ける」の後に画面が元に戻り、押し直せる（Q-B: B） |
| `eslintImportRules.test.ts` の中の重なりの検出の確かめ | 足す（下の行のファイルの中） | 重なりがあれば失敗、無ければ通る（Q-C: A） |
| `shared/tree/SharedTreeView.test.tsx` | 足す | 共有の木の振る舞い・エスケープ・キーボード・axe |
| `eslintImportRules.test.ts`（置き場 `frontend/src/` の直下、名前は案） | 足す | 先頭で `@vitest-environment node`。`ESLint` の部品に `frontend/eslint.config.js` を読ませ、`lintText` に止める道・許す道の見本を渡す |

### 4.5 変えないもの（Step 23 で差が無いことを確かめる）

`.github/workflows/ci.yml`、`build.gradle.kts`・`backend/build.gradle.kts`（`packagesJudgedByTotal`・計測の除外・タスク）、`frontend/vitest.config.ts`・`vitest.setup.ts`、`gradle/libs.versions.toml`・`backend/gradle.lockfile`・`frontend/package.json`・`frontend/package-lock.json`、`application.yaml`・`compose.yaml`・`Dockerfile`・`.env.example`、Flyway の移行、`docker/monitoring/`、本番の安全の決まり（2.3）、`vendor/`、`backend/src/main/java/cherry/mastersmith/access/service/`（NFR 要件の R-04）。

## 5. カバレッジの一覧と境界テスト

| 確かめ | 結果（計画の時点の読み取り） | 作業 |
|---|---|---|
| 手を入れるパッケージが `packagesJudgedByTotal`（7 パッケージ）にあるか | `common.security` と8つの `web` はどれも無い。`common.health`・`common.web` に口を持つクラスは無い | 一覧の作業（下限を満たして外す）は付かない。一覧は変えない（増やさない） |
| パッケージごとの下限（行 80%・分岐 70%） | `common.security` と8つの `web` はすでに対象 | 印は実行の中身を持たないため値は変わらない見込み。`ApiAccessLevel` の行は検査が読むことで覆われる見込み。Step 3（変更の前の基準、Q-D: A）と Step 23（実測）の値を記録する |
| `access.service` | 一覧にあり、手を入れない | Step 23 で `git diff develop -- backend/src/main/java/cherry/mastersmith/access/service` が空であることを記録する |
| 画面の全体の下限 | `thresholds` 行 80%・分岐 70% | `shared/tree`・`app/registry`・`app/login-state` の追加の分をテストで覆う。計測の除外を足さない |
| 境界テスト | `access`・`user` に無い（本体に手を入れる：`access.web`・`user.web` に印） | `AccessBoundaryArchitectureTest`・`UserBoundaryArchitectureTest` を今の依存のまま足す。他の機能（`auth`・`appearance`・`invitation`・`useradmin`・`dslmanage`）は境界テストがあり、印の import（`common.security`）はどれの禁止にも当たらない（読んで確かめた。Step 11 で流して確かめる）。`common` は機能ではないため境界テストを足さない |

## 6. 手順

各 Step の終わりに、その Step で流したコマンドと結果（件数・通過）を `code-generation/generation-notes.md`（この段の記録の置き場）に書く。

### Step 1: 作業の場の用意（ブランチの作成は依頼者の承認を得てから）

- [x] `develop` の先頭のハッシュを `git rev-parse HEAD` で記録する。アプリのソースに未コミットの変更が無いことを `git status` で確かめる（ワークフローの記録 `aidlc/` は外して判断する）。
- [x] `aidlc/` の未コミットの変更（監査ログ・この段の記録）があれば、ブランチを作る前に記録のコミットを依頼者に提案し、承認を得て行う（`project.md` の学び）。
- [x] `frontend/playwright-report`・`frontend/test-results` が無いことを確かめる（あれば消す。計画の時点では無い）。
- [x] 依頼者の承認を得て `develop` から `feature/261004-role-menu-b2`（Q-A: A）を作る。

### Step 2: テストの実行の準備（最初のテストより前）

- [x] バックエンドの単位のコマンドが動くことを、既存のテストで確かめる: `./gradlew :backend:test --tests cherry.mastersmith.ArchitectureTest` と `./gradlew :backend:integrationTest --tests cherry.mastersmith.access.web.ApiDefaultAccessIT`。
- [x] 画面の単位のコマンドが動くことを、既存のテストで確かめる: `cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/app/registry/validateRegistrations.test.ts`。
- [x] ESLint の決まりのテストの実行環境（Infrastructure Design R-01）: 一時の確かめのファイル（`frontend/src/` の下の `*.test.ts`、先頭で `@vitest-environment node`、`ESLint` の部品で今の `eslint.config.js` を読み `lintText` を1回呼ぶだけ）を流し、`vitest.setup.ts` の読み込みと `cleanup()` が node 環境で害を出さないことを確かめてから消す（コミットしない）。動かなければ、Step 19 のテストを jsdom のままにすることを記録する。
- [x] 確かめたコマンドを `unit-test-instructions.md` のコマンドと照らし、違いがあれば記録する（承認済みの文書は書き換えない）。

### Step 3: 変更の前の基準（Q-D: A）

- [x] 口を持つクラスの検索（`@RestController`・`@Controller` を `backend/src/main/java` で検索）で 10 クラスであること、`common.health`・`common.web` に無いことを記録する（Infrastructure Design R-04）。
- [x] colima の環境変数を付けて `./gradlew :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport` を流し、`backend/build/reports/jacoco/test/jacocoTestReport.xml` から `common.security` と8つの `web` の行・分岐の値を記録する（`project.md` の学び）。

### Step 4: 注釈（ドメイン）— 実装

- [x] `common/security/ApiAccessLevel.java`・`ApiAccess.java` を足す（ライセンスヘッダー `/* ... */`、Javadoc は日本語）。機能のパッケージを import しない（BR1.8）。

### Step 5: 注釈（ドメイン）— テスト

- [x] `common/security/ApiAccessTest.java` を書き、`./gradlew :backend:test --tests cherry.mastersmith.common.security.ApiAccessTest` で流す。

### Step 6: 既存の 34 の口への印（API）— 実装

- [x] 4.1 の 10 のコントローラーに、クラスの単位で印を付ける（functional-spec 3節の表のとおり。PUBLIC 9・AUTHENTICATED 3・ADMIN 22 の口）。方法の単位の印は使わない（どのコントローラーも中の口がすべて同じ分類のため）。
- [x] `./gradlew :backend:compileJava :backend:spotlessCheck` を通す。

### Step 7: 静的な検査と検査の検査（API の層のテスト 1）

- [x] `common/testsupport/ApiAccessRules.java` を書く。規則は「口をすべて対象に取り、条件の中で印ごとに場合分けする」形（2.2 の NFR 設計 R-07）。道はクラスと方法の注釈の道をつなぎ、`${名前:既定値}` は既定値で読む。違反の文は口のクラス名・方法名・道だけ（NFR1.10）。
- [x] `common/testsupport/apiaccess/` に見本6つを書く（`ValidSample` は方法の印でクラスの印が無い口・`${server.error.path:/error}` の PUBLIC・`/api/admin` そのものの ADMIN・`/api/sample` の AUTHENTICATED を持つ）。
- [x] `ApiAccessRulesTest.java`（見本ごとに落ちて文にクラス名と方法名が入る、`ValidSample` は3つの規則を通る、空の集まりは落ちる）と `ApiAccessArchitectureTest.java`（本番のクラスに4つの規則、口 34 以上。Javadoc に NFR 設計 R-06 の一文、ADR-008 の補足として PUBLIC の一覧は `ApiAccessConsistencyIT` にあること）を書く。
- [x] `./gradlew :backend:test --tests cherry.mastersmith.ApiAccessRulesTest --tests cherry.mastersmith.ApiAccessArchitectureTest` で流す。
- [x] 確かめ: 一時に1つのコントローラーの印を外して `ApiAccessArchitectureTest` が落ちること（B2 の「見せるもの」）を確かめ、元に戻す（戻したことを `git diff` で確かめる）。

### Step 8: PUBLIC の一覧（API の層のテスト 2）

- [x] `common/testsupport/PublicApiInventory.java`（`PublicApiEntry` の形は 2.2 の R-08、一覧の定数 9 行は要件 2.2 の表のとおり）と `PublicApiInventoryTest.java`（4つの境界と変えない場合、`entryOf` が宣言した方法だけを入れ HEAD・OPTIONS を足さず変数名を残すこと）を書く。
- [x] `./gradlew :backend:test --tests cherry.mastersmith.PublicApiInventoryTest` で流す。

### Step 9: 実行時の検査（API の層のテスト 3、結合）

- [x] `common/testsupport/ApiAccessSubjects.java` を書く（主体と期待の表を、後の Intent が行を足すだけで広げられる形で持つ。security-design 7節）。
- [x] `ApiAccessConsistencyIT.java` を書く。起動は `ApiDefaultAccessIT` と同じ形（2.2 の NFR 設計 R-02）。`WebInvocationPrivilegeEvaluator` を型で、`RequestMappingHandlerMapping` を名前 `requestMappingHandlerMapping` で受ける。口の集合を ArchUnit（`DO_NOT_INCLUDE_TESTS`）で読んだ本番のクラスと突き合わせ、本番に無いクラスの口があれば落とす（2.2 の FD R-02・NFR 設計 R-03）。静的な検査の集合とクラスと方法で一致すること、34 以上であることを確かめる。宣言された方法（無ければ GET・POST・PUT・PATCH・DELETE）とパス変数を `1` に置き換えた道で、3つの主体を判定し、期待の表と比べて違いをすべて並べて落とす。印が PUBLIC の口を `PublicApiInventory.entryOf` で組にして `diff` が空であることを確かめる。
- [x] `./gradlew :backend:integrationTest --tests cherry.mastersmith.ApiAccessConsistencyIT` で流す。
- [x] 捨ての試しの再確かめ（2.2 の NFR 設計 R-05）: 判定の部品が Bean で得られたこと、口 34、PUBLIC 9 口（方法と道の組で 14）、食い違い 0 を、このテストの結果として記録する。判定の部品が使えなかったときは、BR1.5 の切り替え（MockMvc）に移る前に止めて依頼者に諮る。

### Step 10: 境界テスト

- [x] `access`・`user` の今の依存を import で数え直し、`access/AccessBoundaryArchitectureTest.java`・`user/UserBoundaryArchitectureTest.java` を既存の境界テスト（`UserAdminBoundaryArchitectureTest` など）と同じ形で書く。
- [x] `./gradlew :backend:test --tests cherry.mastersmith.access.AccessBoundaryArchitectureTest --tests cherry.mastersmith.user.UserBoundaryArchitectureTest` で流す。

### Step 11: バックエンドの区切りの確かめ

- [x] `./gradlew :backend:test`（単体の全体。既存の `ArchitectureTest` と境界テスト 11 個を含む）と `./gradlew :backend:spotlessCheck :backend:spotbugsGate` を流す。結合テストの全体は Step 23 の `verify` に任せる。

### Step 12: 画面の登録の型と登録の検査 — 実装

- [x] `app/registry/types.ts` に `SidebarSection`・`section`・`icon`・`logout` を足す。照合の一覧（18 個）と両方向の型の確かめを足す。
- [x] `validateRegistrations.ts` に3つの問題（frontend-components 3.2 の文）を足す。
- [x] 4件の登録に `section: 'ADMIN'` を足す。`SidebarItemRegistration` を組み立てる既存のテストの見本に `section` を足す（型だけの直し）。
- [x] `cd frontend && npm run typecheck` を通す。

### Step 13: 画面の登録の型と登録の検査 — テスト

- [x] `validateRegistrations.test.ts` に、section の正しくない値・section ADMIN と visibleWhen LOGGED_IN・一覧の icon は通る・一覧に無い icon と空の文字は `RegistrationError`・icon が無い項目は通る、を足す。
- [x] `cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/app/registry src/app/navigation src/app/layout/ShellLayout.test.tsx src/app/routing src/app/admin-forbidden src/features/admin src/features/dsl/registration.test.tsx src/features/invitation/registration.test.tsx src/features/useradmin/registration.test.tsx` で流す。

### Step 14: ログアウトの口と既存の違反の直し — 実装

- [x] `LoginStateGate.tsx` に提供元の `logout` を渡す文脈と `useLogout()` を足す（`LoginState` の文脈とは別）。`useLogout()` が返す関数は `Promise<boolean>` で、提供元の `logout` を呼べたら真、提供元または `logout` が無ければ偽を返す。`logout` の失敗は外へ出さず真を返す（呼べたため。画面の側のトークンは auth が必ず消す）（Q-B: B）。
- [x] `features/auth/loginStateProvider.ts` に `logout` を足す。
- [x] `features/registration/useRegistration.ts` の `../auth/authSession` の import を消し、`useLogout()` を使う。偽（呼べなかった）なら `loggingOut` の印を解いて段階を `loggedIn` に戻し、押し直せるようにする（Q-B: B）。登録の完了の画面のほかの動きは変えない（BR3.3）。
- [x] `cd frontend && npm run typecheck` を通す。

### Step 15: ログアウトの口と既存の違反の直し — テスト

- [x] `LoginStateGate.test.tsx` に `useLogout` の場合（提供元の `logout` を呼んで真を返す・`logout` が無ければ何もせず偽を返す・失敗を外へ出さず真を返す・提供元が無ければ偽を返す）を足す。描画の後の値は `waitFor` で待つ。
- [x] `loginStateProvider.test.ts` に `logout` の確かめを足す。`RegistrationPage.test.tsx` に「提供元に `logout` が無いと画面が戻り、押し直せる」を足す。
- [x] `cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/app/login-state src/features/auth src/features/registration` で流す（既存の `RegistrationPage.test.tsx` の「ログアウトして続ける」を含めて通ること）。

### Step 16: 共有の木 — 実装

- [x] `shared/tree/` に4つのファイルを足す（frontend-components 2.1〜2.5）。`app/`・`features/` を読まない。表示名と印は文字の差し込みだけで描く。子の読み込みの状態と世代を木の中に持ち、`nodes` が替わったら捨てる。`data-testid` を開閉のボタン・選ぶボタン・再試行のボタンに付ける。
- [x] `cd frontend && npm run typecheck && npm run lint:css` を通す。

### Step 17: 共有の木 — テスト

- [x] `shared/tree/SharedTreeView.test.tsx` を書く: 開閉と `aria-expanded`、選びと `aria-current`（`selectedId` が読み込んだ節に無いときは無い）、最初に開いたときだけ読む、`nodes` の替えで読み直す・読み込み中の古い結果を捨てる、失敗の文と再試行（拒否の理由の文は出ない）、0 件の文、表示名・印に `<`・`>`・`&`・`"`・`'`・`<script>` を含めても文字として出て要素が増えない、開閉のボタンの名前に表示名、Tab・Enter・Space だけで深い節まで開いて選べる、渡した文言だけが出る、vitest-axe 1件（開いた状態・読み込み中・失敗を含む）。
- [x] `cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/shared/tree` で流す。

### Step 18: ESLint の制限 — 実装

- [x] 道の別名が `tsconfig*.json`・`vite.config.ts` に無いことを確かめる。
- [x] `frontend/eslint.config.js` に、`src/features/` のディレクトリの一覧から機能ごとの決まり（`files`・`ignores` に `**/*.test.ts`・`**/*.test.tsx`、`no-restricted-imports` の `patterns` で兄弟の機能を指す `../B`・`../B/**`・`../../B`・`../../features/B` など）と、`src/shared/**` の決まり（`app/`・`features/` を指す道）を足す。誤りの文は直し方を示す日本語。
- [x] 名前の重なりの検出（Q-C: A）: 機能ごとの下位のディレクトリの名前の一覧と機能の名前の一覧を比べる関数を設定の中に切り出して名前付きで出し、重なりがあれば重なった機能と下位のディレクトリの名前を並べた誤りを投げて、設定の読み込み（`eslint .`）を失敗させる。今は重なりが無い（下位は `api`・`testing` だけ）ことを確かめる。
- [x] `cd frontend && npx eslint .` で本体の違反が 0 件であることを確かめる。

### Step 19: ESLint の制限 — テスト

- [x] `frontend/src/eslintImportRules.test.ts`（先頭で `@vitest-environment node`。Step 2 で node が使えなかったときは jsdom）を書く。止める: `features/registration/x.ts` から `../auth/authSession`・`../../features/auth/authSession`、`shared/tree/x.ts` から `../../app/registry/types`・`../../features/auth/authSession`。許す: `features/useradmin/testing/x.ts` から `../api/types`、`features/auth/x.ts` から `../../app/i18n/i18n`・`../../shared/api-client/apiClient`、`features/registration/x.test.ts` から `../auth/authSession`。名前の重なりの検出（Q-C: A）は、切り出した関数に、重なる見本（機能 `a` の下に `b` があり、`b` も機能）を渡すと誤りになり、重ならない見本と今の `src/features/` の一覧では通ることを確かめる。見本の文はこのテストの中に置く（2.2 の Infrastructure Design R-02）。
- [x] `cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/eslintImportRules.test.ts` で流す。

### Step 20: 文書

- [x] `frontend/src/features/README.md` に4つの節（登録の `section`・`icon`、`useLogout`、機能どうしの import の制限、共有の木）を足す。`ApiAccess` の Javadoc に新しい口へ印を付ける決まりと、検査（`ApiAccessArchitectureTest`・`ApiAccessConsistencyIT`）の場所を書く。

### Step 21: 画面の区切りの確かめ

- [x] `cd frontend && npm run format:check && npm run lint && npm run typecheck && npm run license:check` を流す。

### Step 22: 記録（コード生成の段の成果物）

- [x] `code-summary.md`（作ったもの、計画との差、依頼者に確かめたいこと、承認の場で確かめること）、`traceability.json`（2.2 の NFR 設計 R-04・Infrastructure Design R-03 を含む。7節の対応）、`source-manifest.json`（作った・変えたアプリのソースの道のすべて）を書く。

### Step 23: 1コマンドの検査（統合の前の関門）

- [x] colima を起動し、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡して `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を流す。対象DB のテストが SKIPPED になっていないことを確かめる。
- [x] 記録: テストの件数、`common.security` と8つの `web` の行・分岐（Step 3 の基準の値と並べて比べ、下がったパッケージがあれば理由を書く。Q-D: A）、画面の全体の行・分岐、`verify` の時間（NFR6.8。目標は置かない）、`access.service` の差が無いこと、4.5 のファイルに差が無いこと（`git diff --stat develop`）。
- [x] 落ちたら、原因を直して流し直す。検査・下限・除外を緩めない。計画の影響の範囲に無い既存のテストが落ちたら、止めて依頼者に諮る。

### Step 24: E2E（統合の前に手元で）

- [x] `docker compose --profile mail up -d mailpit` の後に `./gradlew e2eTest` を流し、13 本がすべて通ることを確かめる（特に `020-auth.e2e.ts`・`090-invitation-registration-flow.e2e.ts`）。新しい E2E は足さない。
- [x] json の報告から結果を記録し、`frontend/playwright-report`・`frontend/test-results` を消す。報告に資格情報・メールアドレスが無いことを確かめる（`project.md` の学び）。

### Step 25: コミットの提案・承認の場・統合の提案

- [ ] 3節の C1〜C5 の区切りを依頼者に提案し、承認を得てコミットする（コミットのメッセージは日本語）。
- [ ] 最後のコードのコミットの後に、コード生成の段の承認の場を開く（記録だけのコミットはその後）。
- [ ] 承認の後、`aidlc/` の未コミットの変更をコミットしてから、`develop` への squash の統合を提案し、承認を得て行う。作業ブランチを消す。push は依頼者が行う。

## 7. ストーリー・要件と手順の対応

| ストーリー・要件 | 決まり・要件（単位の ID） | 手順 |
|---|---|---|
| US1.1 AC1.1.15（API の分類の網羅） | BR1.1〜BR1.10、NFR1.1〜NFR1.7・NFR1.10 | Step 4〜11 |
| 要件 NFR1.3 | 同上 | Step 7〜9 |
| 要件 NFR1.1（画面の出し分けはサーバーの判定の代わりにしない） | BR5.5、NFR1.8 | Step 9（ADMIN の組）・12 |
| 要件 C4（境界テスト） | BR1.10、NFR6.3 | Step 10 |
| 要件 C5（画面の機能どうしの import の制限と既存の違反の直し） | BR2.1〜BR2.4・BR3.1〜BR3.3、NFR6.1・NFR6.2 | Step 14・15・18・19 |
| US1.2・US4.2 の土台（共有の木） | BR4.1〜BR4.12、NFR1.9・NFR4.1〜NFR4.4 | Step 16・17 |
| US5.3 の土台（登録の型） | BR5.1〜BR5.6、NFR1.8・NFR6.5 | Step 12・13 |
| 要件 NFR4.2（実際のブラウザの axe） | NFR4.5（U6・U7 が持つ） | —（引き継ぎ） |
| 要件 NFR6.4・`team.md` のカバレッジ | NFR6.4・NFR6.5 | Step 3・23 |
| 要件 NFR6.1（テストの決まり） | NFR6.6 | すべてのテストの Step |
| 新しい依存を足さない | NFR6.7 | Step 23（lockfile の差なし） |
| 検査の時間 | NFR6.8 | Step 23（記録）、Build and Test |
| 統合・E2E | `team.md`・`cicd-pipeline.md` 7・8節 | Step 23〜25 |

## 8. テストの量（Standard）

| 部品 | テスト | 件数の目安 |
|---|---|---|
| 注釈 | `ApiAccessTest` | 3 |
| 静的な検査 | `ApiAccessArchitectureTest`（規則4・口の数1） | 5 |
| 検査の検査（規則） | `ApiAccessRulesTest`（違反の見本5・違反なし1・空1） | 7 |
| 検査の検査（PUBLIC の一覧） | `PublicApiInventoryTest`（境界5・変えない1・`entryOf` 2） | 8 |
| 実行時の検査（結合） | `ApiAccessConsistencyIT`（3つの主体の判定・集合の一致・本番のクラスだけ・口の数・PUBLIC の一覧） | 5 |
| 境界テスト | `AccessBoundaryArchitectureTest`・`UserBoundaryArchitectureTest` | 各 2〜4 |
| 登録の型と登録の検査 | `validateRegistrations.test.ts` に足す分 | 6 |
| ログアウトの口 | `LoginStateGate.test.tsx`・`loginStateProvider.test.ts`・`RegistrationPage.test.tsx` に足す分（`useLogout` の4つの場合・auth の提供元・既存の流れ・呼べないときに画面が戻る） | 7 |
| 共有の木 | `SharedTreeView.test.tsx` | 8〜12（部品の中心のため Standard の上限を少し超える） |
| ESLint の制限 | `eslintImportRules.test.ts`（止める4・許す4・重なりの検出2） | 10 |

既存のテストはすべて通ったままにする。E2E・性能・セキュリティの新しいテストは要件が求めていないため足さない（Testing Contract の `strategy_volume`）。

## 9. この計画で決めたこと・承認済みの文書との差

| # | 差 | 理由 |
|---|---|---|
| D-1 | 注釈の形を確かめる単体テスト `ApiAccessTest` を足す（設計の部品の表に無い） | Standard の部品ごとのテストの下限と、注釈の保持の誤り（`CLASS` だと実行時の検査が空振り）を早く落とすため |
| D-2 | 実行時の検査の起動は `ApiDefaultAccessIT` と同じ形にするが、既存の結合テストはクラスごとに一時の内部DB を設定するため、起動の文脈は使い回されず1回増える（上流との差: security-design 4.3 手順 1 と `cicd-pipeline.md` 5節の「起動の文脈を使い回せる」と違う） | 読んで確かめた事実。依頼者の答え Q-E: A で、起動が1回増えることを受け入れ、増え方を Build and Test で実測する。設計の文書は書き換えず、差を `code-summary.md` にも書く |
| D-3 | 実行時の検査の口の集合を、ArchUnit で読んだ本番のクラス名の集合と突き合わせて絞る | 2.2 の FD R-02・NFR 設計 R-03 |
| D-4 | 規則の形・見本に当てる規則の範囲・`ValidSample` の AUTHENTICATED の口・`PublicApiEntry` の形を決めた | 2.2 の NFR 設計 R-07・R-08 |
| D-5 | ESLint の決まりのテストのファイル名を `frontend/src/eslintImportRules.test.ts`（案）にした | `cicd-pipeline.md` 3節が Code Generation で決めるとした範囲 |
| D-6 | 照合の一覧の置き場を `app/registry/allowedIcons.ts`（案）にした | frontend-components 3.1 は `app/registry` の中とだけ決めていた |
| D-7 | `useLogout()` が返す関数を `Promise<boolean>`（呼べたら真、提供元または `logout` が無ければ偽、失敗は外へ出さず真）にし、偽なら登録の完了の画面は `loggingOut` を解いて `loggedIn` に戻り、押し直せる（上流との差: rules.md BR3.2・frontend-components 4節は「Promise を返す関数」「無ければ何もせずに終わる」とし、画面の扱いを決めていない。BR3.3 の「動きを変えない」は、auth が `logout` を渡す本番の流れでは保たれる） | 依頼者の答え Q-B: B（Functional Design R-03 の固まる経路を残さない） |
| D-8 | `eslint.config.js` が機能の中の下位のディレクトリの名前と機能の名前の重なりを見つけたら、設定の読み込みで失敗させる（設計に無い追加の守り） | 依頼者の答え Q-C: A（Functional Design R-04） |
| D-9 | 変更の前のカバレッジを基準として測り、変更の後と比べる（設計に無い手順） | 依頼者の答え Q-D: A |

承認済みの設計の文書は書き換えず、差は `code-summary.md` にも書く。

## 10. 依頼者の答え（計画の承認の前に確かめたこと）

| 問い | 答え | 反映した所 |
|---|---|---|
| Q-A Bolt の番号とブランチの名前 | A. 承認済みの番号 B2 のまま使い、ブランチは `feature/261004-role-menu-b2`。実行の順を入れ替えたこと（B2 → B1 → B3 …）だけを記録する | 2.1・3節、Step 1 |
| Q-B 提供元に `logout` が無いときの登録の完了の画面（Functional Design R-03） | B. `useLogout` が呼べたかを `Promise<boolean>` で返し、呼べなければ登録の完了の画面を元に戻す（押し直せる） | 2.2、4.3・4.4、Step 14・15、9節 D-7 |
| Q-C ESLint の名前の重なり（Functional Design R-04） | A. `eslint.config.js` が機能の中の下位のディレクトリの名前と機能の名前の重なりを見つけたら、設定の読み込みで失敗させる | 2.2、4.3・4.4、Step 18・19、9節 D-8 |
| Q-D 変更の前のカバレッジの基準 | A. 変更の前のカバレッジを基準として測る | 5節、Step 3・23、9節 D-9 |
| Q-E 実行時の検査の起動の増え方 | A. 起動が1回増えることを受け入れ、増え方を Build and Test で実測し、設計との差を記録する | 2.2、9節 D-2、Build and Test に引き継ぐこと |

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
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる。ただし、既存のパッケージに単独で下限を下回るものがあれば、パッケージごとの下限は新しく作るパッケージだけに当てる（既存のパッケージは全体の合計で判定する）。どちらになるかは、パッケージごとの下限を入れる Bolt で既存のパッケージのカバレッジを実測して決め、結果を記録する。\n- 全体の合計で判定している既存のパッケージ（`backend/build.gradle.kts` の `packagesJudgedByTotal` の一覧。2026-10-04 の時点で 7 パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）。`user.domain`・`user.repository`・`user.service` は Intent 260925-user-management で一覧から外れ、含まない）に手を入れる Bolt では、テストを足してそのパッケージの下限（行 80%・分岐 70%）を満たし、一覧から外してパッケージごとの下限の対象に必ず戻す。「手を入れる」は、そのパッケージの本体のソース（`src/main`）の変更のすべて（説明文だけの直しを含む）を指し、テストだけの変更は含めない。手を入れる見込みのパッケージとそれに伴う作業は、Delivery Planning とコード生成の計画で、各パッケージの今の値を実測して見積もる。カバレッジは、その Bolt のテストを足した後に `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、値を記録する。一度外したパッケージは一覧に戻さず、一覧を増やす変更はしない。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3種類すべてを `./gradlew verify` の中で毎回実行する（CI も同じ）。Intent `260923-dsl-schema-loader` の Build and Test で `verify` の時間と colima の VM のメモリを実測したうえで、この形に決めた。コンテナの実行環境が無いときの扱いは Way of Working のとおり。\n- 外へ送るメール（SMTP）を使う機能のテストは、送信の部品をモックで置き換えて済ませず、テストの中で JVM の中に起動するテスト用の SMTP の受け手で実際に受け、宛先・件名・本文（HTML）・言語を確かめる。受け手にコンテナは使わない（コンテナの実行環境が無いときに飛ばしてよいテストは、対象DB のテストだけのままとする）。送信の失敗（受け手が接続を拒む・応答しない）のテストも必ず入れる。実在の宛先へは送らない。受け手の具体の道具は、採用の前のライセンスの確認とあわせて設計の段で決める。\n- 画面からの一連の操作を確かめるテスト（E2E）は Playwright（`./gradlew e2eTest`）で書き、`./gradlew verify` と CI の外に置く。本数は代表的な流れに絞り、機能の Intent ごとに代表の流れを1本まで足す。新しく足す流れは、前のテストが作った状態に頼らず、利用者などの前提を自分で作る。複数の機能の Intent を1つに束ねた Intent では、束ねた元の Intent ごとに数える（Intent 261004-role-menu は最大2本）。既存の E2E を新しい形（権限の形など）に合わせて書き換えることは、本数に数えない。利用者の状態を変える操作（利用停止・管理の権限の変更・ロックなど）は、その流れで自分で作った利用者だけを対象にし、初期管理者の状態は変えない。役割・権限を変える操作も、その流れで自分で作った利用者と役割だけを対象にし、初期管理者は変えない（E2E は1つのアプリ・内部DB・初期管理者を全ファイルで共有して順に流すため）。E2E は、画面・認証に関わる変更を統合する前と、リリースの前に手元で実行する。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- リポジトリは公開のため、テストデータのメールアドレスは予約のドメイン（`example.com` など）だけにし、実在しそうな氏名・宛先を置かない。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- 画面のテストで、描画の後（`useEffect` などの効果）に反映される値は、操作の直後に同期で確かめず、`waitFor` で待って確かめる。テストの時間の上限（Vitest の既定 5 秒など）は、原因を確かめずに延ばさない。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。\n- 不安定なテストと CI の失敗は、次の1つの決まりで扱う。\n  - 手元で再現した不安定なテストは、原因を直すまで統合しない（次へ進まない）。\n  - 手元で再現しないものは、再現の試みに先に時間の上限を決め、見立てと試みの範囲を記録したうえで、CI の再実行で通れば進めてよい（不安定と確かめられていない扱い）。\n  - 同じテストが二度目に落ちたら、原因を直すまで次へ進まない。\n  - 失敗を直さずに次の Intent へ持ち越すのは、依頼者の決定があるときだけとし、決まりとの差を記録する。\n- 認証・認可・監査に関わる機能では、次のテストを必ず書く。★印の項目は要件（しきい値や動作）が未確定のため、要件定義で決めてからテストを書く。この一覧の「管理者」は管理の権限を持つ利用者を、「管理の権限」はその判定に使う権限を指し、権限の形（真偽値1つか役割・権限か）によらずに読む。権限の具体の形（役割・権限の名前など）は、要件で決まった後に書き足す。\n  - アカウントロック: 失敗回数のしきい値の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロック中は正しいパスワードでも拒否、ログイン成功時の失敗回数の扱い。★しきい値、回数を数える期間、ロックの解除方法\n  - ログイン: 存在しないユーザーとパスワード誤りで、応答からユーザーIDの存在を推測できないこと\n  - トークン: 有効期限の境界（直前は有効／直後は無効）、署名の改ざん、署名方式の指定を悪用した改ざん（`alg: none` 等）、ログアウト後のリフレッシュトークンの拒否、ログアウト後もアクセストークンが有効期限まで使えること（決定済みの仕様として明示する）。★各トークンの有効期限の値、リフレッシュトークンを使うたびに作り直すか\n  - 初期管理者の自動作成: 2回目以降の起動で重複作成しない、設定が無い／不正なときの動作、パスワードがログに出ない。★設定が無いときに起動を止めるか\n  - 認可: 未認証（401）、その権限を持たない（403）、持つ（200）をサーバー側のテストで確かめる（画面で管理メニューを隠すことはサーバー側の検査の代わりにしない）\n  - 監査ログ: 対象イベントごとに必須項目が記録されること、存在しないユーザーIDでのログイン失敗も記録されること。★監査ログの書き込みに失敗したときに操作を失敗させるか\n  - 秘密情報の漏えい: ログ・監査ログの出力にパスワード・トークンの値が含まれないこと\n  - 構造化ログ・分散トレース: 決めた形式で出る、トレースIDがログに含まれる、外部エクスポートが既定で無効であること\n  - 画面: ログイン画面のアクセシビリティ検査、ロック時のメッセージ表示、ログアウトでトークンが破棄されること\n  - 招待と登録の完了: 招待の有効期限の境界（直前は有効／直後は無効）、使い終えた招待・取り消した招待の再使用の拒否、改ざんした招待・存在しない招待の拒否（応答から利用者の存在を推測できないこと）、招待中（登録が終わっていない）の利用者はログイン・トークンの更新・アクセストークンの認証のどれでも拒否されること。★招待の有効期限の値、再送・取り消しの扱い\n  - パスワードの変更: 今のパスワードの確かめ、パスワードの規則の境界、変更の後のリフレッシュトークンの扱い（決めた側の動作を明示したテストにする）。★パスワードの規則、変更の後にほかの端末のリフレッシュトークンを無効にするか\n  - 利用停止: 停止中の利用者は、ログインの照合・トークンの更新・アクセストークンの認証の3つの入口のすべてで拒否されることを、入口ごとにサーバー側のテストで確かめる。停止を解いた直後は3つの入口のすべてで受け付けること。停止の前に出したリフレッシュトークン・アクセストークンの扱いは、決めた側の動作を明示したテストにする。★停止を応答から推測できてよいか、停止の前に出したトークンの扱い\n  - 管理の権限の変更: 管理の権限を与えた直後・外した直後の次の要求で、管理の API の 403／200 がサーバー側で切り替わること（画面が持つ古い権限に頼らない）。権限を外す前に出したトークンの扱いと、自分の管理の権限を外す操作の扱いは、決めた側の動作を明示したテストにする。★権限を外す前に出したトークンの扱い、自分の管理の権限を外す操作の扱い\n  - ロックの解除: 解除の直後に正しいパスワードで入れること、解除の後の失敗回数の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロックの状態の行が無い利用者の解除。時刻は注入した時計で動かし、実時刻と `sleep` に頼らない。★解除の後の失敗回数の扱い、ロックの状態の行が無いときの応答\n  - 最後の管理者の保護: 最後の有効な管理者（管理の権限を持つ利用者）から管理の権限を外す・利用を止める操作は拒否され、拒否の後に状態が変わっていないこと。2人の管理者が同時に互いの管理の権限を外す・利用を止めても、有効な管理者が 0 人にならないこと。同時の重なりはスレッドの数に頼らず、待ち合わせで確実に作る。★最後の管理者の数え方（止めた管理者を数えるか）\n  - 管理の API の認可: 足す管理の API のすべてについて、未認証（401）・その権限を持たない（403）・持つ（200）をサーバー側のテストで確かめる。停止中の管理者は管理の API を呼べないこと。\n  - 要求の改ざん: 管理の API の外（`/api/me` など）から、要求の本文の値を変えて自分の管理の権限や利用者の状態を変えられないこと（一括代入の防止）。\n  - 管理の操作の監査: 利用者の権限・状態を変える操作（管理の権限の付け外し・利用停止と再開・ロックの解除）ごとに、操作した人・対象の利用者・結果が記録されること。★拒否した操作（403・最後の管理者の拒否など）を記録するか\n  - 利用者の管理の漏えい: 一覧・詳細の応答と監査の行に、パスワードのハッシュ値・リフレッシュトークン・ロックの判定の内部の値が含まれないこと。TRACE のログを有効にして一覧・詳細を読んでも、メールアドレス・氏名がアプリのログに出ないこと（既存の `*SecretLeakIT` と同じ形で確かめる）。\n- 役割・権限の機能では、次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 権限ごとの API の認可: 足す・変える API のすべてについて、未認証（401）・その権限を持たない（403）・持つ（200）をサーバー側のテストで確かめる。403 を確かめる利用者は、権限が何も無い利用者ではなく、要る権限だけを欠く利用者（ほかの権限は持つ）にする。API と権限の組は、パラメーターを使うテストで表として書く。停止中の利用者は、権限があっても通らないこと。\n  - API の分類の網羅: アプリが持つすべての API が「公開・ログインだけ・権限が要る（どの権限か）」のどれかに明示して分類されていることを確かめるテストを置き、分類の無い API があれば失敗させる（`/api/**` の既定は「ログインだけ」で、権限の決まりを書き忘れるとログイン中の誰でも呼べるため）。足した API を分類に足し忘れると落ちる形にする。分類の持ち方と仕組みの置き場は設計の段で決める。\n  - 割り当ての変更の反映: 役割・権限を付けた直後・外した直後の次の要求で、403／200 がサーバー側で切り替わること（画面が持つ古い権限に頼らない）。★変更の前に出したトークンの扱い\n  - 権限の昇格・一括代入・IDOR の防止: 操作する人が持たない権限を、自分や他人に与えられないこと。要求の本文の値を変えて自分の役割・権限を変えられないこと、役割の作成・変更の本文に許していない項目（ID・組み込みの印・作成者など）を足しても反映されないこと。役割・割り当ての ID を差し替えた要求（他人の割り当て・存在しない役割など）は拒否され、状態が変わらないこと。★組み込みの役割の有無と、その削除・権限の取り上げの保護\n  - 自分自身への操作と最後の管理者の読み替え: 自分の役割を外す操作と、最後の有効な管理者を役割でどう数えるかは、決めた側の動作を明示したテストにする。同時の重なりはスレッドの数に頼らず、待ち合わせで確実に作る。★自分の役割を外す操作の扱い、最後の有効な管理者の役割での数え方\n  - 監査: 割り当ての変更ごとに、操作した人・対象・変えた中身・結果が記録されること。★役割そのものの作成・変更・削除を記録するか、拒否した操作（403・昇格の拒否など）を記録するか\n  - 画面の出し分け: メニュー・画面を隠すことを、サーバー側の検査の代わりにしない。権限の無い画面へ直接移ったときに 403 の表示になること。\n  - 性質ベースのテスト: 役割から権限の集合を求める関数（サーバー側）と、権限でメニューの枝を落とす関数（画面側）は純粋な関数にし、jqwik・fast-check で性質（持たない権限の API・項目が出ない、役割を足しても権限は減らない など）を確かめる。\n- N 階層のメニュー（ナビゲーションの木）の画面では、次のテストを必ず書く。深さの上限の具体的な値は設計の段で決め、決めた値の境界で確かめる。\n  - 開閉と今の項目: 開閉の状態（`aria-expanded`）と今の項目（`aria-current`）が正しく出ること、キーボードだけでたどれること。\n  - 深さの上限: 上限ちょうどは受け付け、上限を超えると拒否されること。\n  - 表示の木を作る関数: 定義の木から表示の木を作る純粋な関数（権限で枝を落とす・並べる）に、fast-check の性質ベースのテストを当てる（例: 権限の無い項目が出ない、子の無い枝が残らない）。\n  - 信頼できない入力: メニューの定義（`label`・`icon`・`table`）を信頼できない入力として扱う。表示名に `<`・`>`・`&`・`\"`・`'` を含めても文字として出る（HTML として描かない）こと、`icon` を許す名前の一覧と照らすこと、`table` の名前から組み立てる道をエンコードし、アプリの中の決めた形から外れない（外部の URL・`javascript:` の道を作らない）こと。★一覧に無い `icon` を拒否するか既定のアイコンにするか\n  - make-you-chic-ui の部品に頼る振る舞い: 開閉・`aria-current`・キーボードなど、make-you-chic-ui の部品に頼る振る舞いも、このリポジトリの画面のテストと実際のブラウザの axe で確かめる（make-you-chic-ui のテストはこのリポジトリの CI の対象外のため）。axe は、展開した状態・深い階層・長い名前で、ブランドカラーとテーマのすべての組について行う。\n- 利用者へメールを送る機能では、Code Style と `project.md` のメールの決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 本文のエスケープ: 差し込む利用者の値（氏名・メールアドレスなど）に `<`・`>`・`&`・`\"`・`'` を含めても、タグや属性としてそのまま出ないこと。利用者の値をエスケープしない差し込み（`{{{ }}}`・`{{& }}`）で入れていないこと、属性の値を二重引用符で囲んでいること（エンジンの `{{ }}` は `'` を置き換えないため）\n  - テンプレートの描画: 日本語と英語のすべてのテンプレートを描けて、件名が空でないこと、差し込み漏れ（`{{` の残り）が無いこと、ライセンスヘッダーが描いた本文に出ないこと\n  - 招待の URL: 設定したベース URL だけから組み立て、要求の Host ヘッダーから組み立てないこと。ベース URL の設定が無いときに要求の Host を変えても、招待の URL に出ないこと。★ベース URL の設定が無いときの動作\n  - トークンと URL の漏えい: 招待のトークンと招待の URL が、アプリのログ・監査ログ・トレースの属性・エラー応答に含まれないこと（既存の `*SecretLeakIT` と同じ形で確かめる）\n  - ヘッダーへの差し込み: 宛先・件名・差し込む値に改行（CR・LF）を含めても、メールのヘッダーが増えず拒否されること\n  - 送信の失敗: 送信が失敗したとき（受け手が接続を拒む・応答しない）に、宛先のメールアドレス・SMTP の応答・資格情報が応答とログに含まれないこと。★送信が失敗したときの業務の動作（招待を失敗させるか、再送に回すか）\n- 利用者が投入する DSL（YAML）を読み込む機能では、Code Style の「信頼できない入力」の決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。上限の具体的な数値は設計の段で決め、決めた値の境界で確かめる。\n  - 大きさ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 入れ子の深さ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 別名（アンカー）: 展開の数が上限を超えると拒否され、別名の展開の爆発で処理が止まらない\n  - タグ: 任意の型を作るタグ（`!!` など）を含む DSL は拒否され、型が作られない\n  - 重複キー: 同じキーが重なる DSL はエラーになり、後の値で黙って上書きされない\n  - JSON Schema の `$ref`: 外部の URL を取りに行かない\n  - 拒否の応答: Problem Details の形で返り、YAML・JSON Schema の部品の例外のメッセージを含まない\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22)"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 (learned 2026-09-23) \n- 負荷の環境や配備先が決まらないと測れない目標（応答時間のパーセンタイル、運用の指標、ファイルの権限など）は、Build and Test で `Unverified` とし、持ち主の段（performance-validation・observability-setup・deployment-execution）を明記して引き継ぐ。目標を緩めて「満たした」ことにはしない。 (learned 2026-09-23) \n- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は perf/README.md。 (learned 2026-09-23) \n- 負荷の試験で、アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。 (learned 2026-09-23) \n- 同時の重なりを確実に作るため、本番のコードを変えずに、監査の書き込みの時間を測る LongSupplier（AuditEventListener で2本目を借りる直前に呼ばれる）をテストで差し替えて待ち合わせる方式にした。既存の LoginConcurrencyIT は 8 スレッドでプールの 10 に届かず、前回の失敗のログインで尽きなかった理由の1つと見られる。 (learned 2026-09-23) \n- Intent の流れに Performance Validation の段が無く、負荷の環境（使い捨ての環境）を手元で用意できるときは、k6 の試験と NMT の測定の持ち主を Build and Test とし、Unverified で引き継がずにその段で実行する。 (learned 2026-09-23) \n- 修正の前の設定（例: 上限 1g）も修正の後の環境（例: CPU 4 の VM）で流し（pre1g）、要件の前提（VM を上げても F3 が起きる）を実測で裏付ける。 (learned 2026-09-23) \n- パッケージごとのカバレッジの下限と SpotBugs の SQL_ の関門を U1 の計画に入れた。team.md の決まりだが今のビルドに無く、この Intent で最初に作る単位のため。U1 の設計の文書には無い作業。 (learned 2026-09-24) \n- U4 で既存の AuditSecretLeakIT の列の一覧に V6 の4列を足し、テストの JVM のヒープを 1g にした。前者は承認済みの V6 と必ず食い違うため、後者は構造の検査がクラスを持ち続け U3 の 10MB 超えのテストでヒープが尽きたため。どちらも計画に無い変更で、依頼者に確かめる。 (learned 2026-09-24) \n- 応答しない対象DB の TIMEOUT の確かめに、テストの中で開いた ServerSocket（受け付けて何も返さない）を使う。外の端末に頼らず確実に再現できる代わりに、本物の DB の遅延ではない。 (learned 2026-09-24) \n- パッケージごとのカバレッジの下限は新しいパッケージだけに当てた。実測で audit.service（行 77.2%）・common.health（行 79.2%）・auth.repository（分岐 50.0%）が単独で下回ったため（team.md の決まりどおり）。既存の 22 パッケージを一覧で外し、新しいパッケージは自動で対象になる。 (learned 2026-09-24) \n- 負荷の試験で接続プールが尽きたかを確かめるときは、使い捨てのアプリにだけ MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics を渡して /actuator/metrics の hikaricp の値を読み、数秒ごとの使用中の数ではなく、待ちの時間切れの累計と借りるまでの待ちの最大で判断する（配備したアプリの公開の範囲は変えない）。 (learned 2026-09-24) \n- k6 などの長い試験は caffeinate -i を付けて流し、PC の自動のスリープで要求が止まって結果が崩れるのを防ぐ（バッテリー駆動のまま 414 秒スリープし、要求が 6分52秒止まった）。内部DB に SQL で直接入れた試験用の利用者はロックの状態の行が無いため、同時のログインを流す前に1人ずつログインさせて行を作る。 (learned 2026-09-24) \n- 軽い API の性能は、投入を重ねて内部DB のファイルが膨らんだ状態（悪い側の条件）のまま測る。メモリの最大（memory.peak）を比べる試験の前は、アプリのコンテナを作り直して前の最大の値を消す。 (learned 2026-09-24) \n- Performance Validation の段が無いため、2件目の直しの負荷の試験での確かめ（Q5: A）の持ち主を Build and Test とした（project.md の学びどおり）。 (learned 2026-09-24) \n- colima の PC で ./gradlew verify を流すときは、README の DOCKER_HOST と TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE をシェルに渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗した。付けて流し直した結果を基準とした。 (learned 2026-09-25) \n- unit-test-instructions.md の k6 inspect のコマンドは --include-system-env-vars が無いと場面の名前が undefined になり確かめにならなかった。承認済みの文書は変えず、実際には付けて流し、code-summary.md に差を記録した。 (learned 2026-09-25) \n- 1回目の verify で AccessTokenApiIT の 6 件が接続の失敗で落ちた。変更の経路に触れず、同じ時刻に Gradle の作業プロセスとの接続も時間切れだったため PC の負荷による一時的な失敗と見立て、段の中の直しの1回目としてコードを変えずに verify を流し直して通した。原因は確かめていない。 (learned 2026-09-25) \n- 依頼者の判断で、試験のイメージを mastersmith:local ではなく mastersmith:followup-fixes で作り、perf/README の手順（local を作り直す）から外れた。配備したアプリは k6 のあいだ止め、約 17 分後に同じコンテナで起動し直した。 (learned 2026-09-25) \n- 試験の後にロックの状態の行の数を数える問い合わせの列の名前を誤り、使い捨ての環境を消した後で取り直せなかった。合格の条件（ログインの checks と 500 の件数）で判定した。消す前に確かめの結果を見てから片付けるべきだった。 (learned 2026-09-25) \n- Q4（確かめられたときだけ直す）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違いうるため、追加の質問 F3 で、再現できなければ不安定と確かめられていない扱いとして統合してよいことを確かめた。 (learned 2026-09-25) \n- 要件 FR2.2（目標は計画の承認の場で決める）を満たすため、依頼者の決定（Q1: A）で、計画を書く前に dslMixed でメモリの内訳を測ることにした。測る間は配備したアプリを止める。 (learned 2026-09-25) \n- 生成で、テストの既定で MBean の登録を無効にする置き場を、計画の候補の TestDatabase ではなくテストの EnvironmentPostProcessor にした。TestDatabase を使わずに Spring を起動するテストのクラスが 15 あり、漏れるため。再現の結合テストは 12 回では伸びが小さく判定がはっきりしないため、24 回・履歴の上限 3 にした。 (learned 2026-09-25) \n- AccessTokenApiIT は、同じ例外の文言を仕組み（colima の IPv4 の転送と Java の IPv6 の待ち受けの番号の重なり）ごと再現できたことを原因の確認とみなし、依頼者の決定（D7）でテストの JVM に preferIPv4Stack を付けて直した。実際の1回目がこれで起きたかは確かめられず、今回の繰り返しでは重なりは起きなかった。 (learned 2026-09-25) \n- Test Strategy は Minimal だが、要件 FR5 と計画の「Build and Test に引き継ぐこと」のため、結合・性能・セキュリティの手順書も作り、この段で dslMixed・--storage --compact（40 回）・詰め直しの最中のログイン・refresh・dslCycle を流した。 (learned 2026-09-25) \n- 計画の dslCycle を最初の台本に入れ忘れ、配備したアプリをもう一度（約 4 分）止めて流した。負荷の試験の台本を書く前に、計画の「Build and Test に引き継ぐこと」の項目を一つずつ台本の手順と突き合わせる。 (learned 2026-09-25) \n- 実際のブラウザのアクセシビリティの検査（axe）は、誤りを出した状態と、現実に近いデータ（2語の氏名など）で、ブランドカラーとテーマのすべての組について行う。user-management の U7 の 080 で初めて、make-you-chic-ui の Avatar（頭文字が2文字のとき）と dark の FormField の誤りの文字のコントラスト不足が出た（050〜070 は誤りの状態を見ず、氏名が1語だったため出なかった）。 (learned 2026-09-28) \n- Playwright の webServer.env に置いた値は json の報告に残る。E2E の仮の資格情報はプロセスの環境変数で渡し、報告の部品の確かめと json の文字列の検索で、パスワード・トークン・メールアドレスが入っていないことを確かめる（user-management の U4 で見つかった）。 (learned 2026-09-28) \n- CI（66fe981）の verify が2回とも別々のテストの時間切れ（H2CompactionByPoolSuspensionIT の接続の待ち 10 秒、InvitationAdminPage.test.tsx の既定 5 秒）で失敗し、U4〜U7 の NFR7.1 が Not Met だったが、依頼者の決定「次のintentでコントラストと一緒に直す。このintentではこのまま進める。」で受け入れた失敗とした。team.md の「CI が失敗したら次に進む前に直す」と食い違うため test-results.md 8.1 に差を記録した。 (learned 2026-09-28) \n- k6 の登録の完了と取り消しの場面は、決定の文言（招待を VU の数だけ）ではなく既定 100 回・招待 100 件とした。トークンは1回しか使えず 10 件では p95 の意味が薄いため、U3 の NFR6.4 の「流す回数以上の招待を用意する」に合わせた。承認の場で確かめる。 (learned 2026-09-28) \n- caffeinate -i は場面ごとではなく、試験の台本全体を包む形で付ける。場面ごとに起こし直すと切れ目で守りが外れ、PC が眠る（user-management の Performance Validation で 92 秒眠り、要求が 1 分 29 秒遅れた）。遅れが出たときは pmset -g log でスリープを確かめる。 (learned 2026-09-28) \n- 応答しないメールの受け手は、Mailpit を docker pause で一時停止して作る（接続は受け付け、何も返さない）。コンテナを止めると接続の失敗になり、時間切れの確かめにならない（user-management の Performance Validation）。 (learned 2026-09-28) \n- Q1 で原因を確かめずに上限を延ばす答え（C）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違うため追加の質問 F1 で確かめ、延ばしたうえで失敗時の診断を足し、差を記録する形（B）になった。Q3 の「すべて試す」は verify で判定できない Temurin 26・logback-appender の扱いを F2 で確かめ、見送り（C）になった。 (learned 2026-09-29) \n- 診断の確かめで、上限 1 ミリ秒は Awaitility の問い合わせの間隔（100 ミリ秒）より短く設定の誤りで弾かれ、150 ミリ秒では手元で接続がすぐ 0 本になり時間切れにならなかった。上限 500 ミリ秒と、待ちの条件を一時的に満たせない形（== -1）にして時間切れを起こした。確かめた後に git checkout で戻した。 (learned 2026-09-29) \n- 警報が鳴ることは、しきい値を 50 ms に下げた警報の決まりの写しを使い捨ての環境に読み込ませて確かめた（Q1: A）。本当に遅い応答を作るより早く PC の負荷も小さいが、しきい値の値そのもので鳴ることは確かめていない。途中で ms-check-p95 のしきい値が 1000 ms ではなく 300 ms（バケットの境界に無い）と分かり、要件の前提 A3 と食い違った。 (learned 2026-09-29) \n- 表示の設定を画面の操作の直後に同期で確かめるテストが、負荷の高い CI で2件（ShellLayout・PreferencesPage）落ちた。描画の後の効果（useEffect）で反映される値は waitFor で待って確かめる。同じ形の RegistrationPage も合わせて直した。 (learned 2026-09-29) \n- Delivery Planning でのカバレッジの実測は、./gradlew verify 全体ではなく :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport で行い、jacocoTestReport.xml から手を入れる見込みのパッケージの値を読む（user-admin で約 5 分）。 (learned 2026-10-01) \n- 接続プールの見積もりを確かめる負荷の試験には、上限に届く形（プールの上限を下げた場面など）を含める。上限に届かない負荷では、見積もりが誤っていても合格する（user-admin の U3 の NFR 要件のレビュー R-02）。 (learned 2026-10-01) \n- 画面のはみ出しの確かめ（E2E・axe）では、画面全体の横のスクロールだけでなく、開いたメニュー・ポップアップなど画面に固定で置く部品が画面の中に収まることも確かめる。表の右端に置く make-you-chic-ui の Dropdown は placement に bottom-end を指定する（user-admin の配備の後のスモークテストで、行の「操作」のメニューが右へはみ出していたのを E2E 120 が拾えなかった）。 (learned 2026-10-03) \n- k6 の http_req_duration が、colima の VM の時計が負荷の間にホストより約 2 秒ずれて合わせ直されるため一部崩れた（待ちの最小 0 ms、繰り返しより長い約 3 秒の値）。1回の繰り返しが要求1つの loginSuccess では、単調な時計の iteration_duration とサーバー側の最大が合うため、判定は iteration_duration で行い、http_req_duration は並べて記録した。前の Intent の 939.6 ms も同じ影響を受けていた可能性がある。 (learned 2026-10-04) \n- 1〜6回目で繰り返しの時間の p95 が2回 1 秒を超え、依頼者が VM の時計を合わせて測り直しを求めた（F1・F2 の Other）。測り直しの7〜9回目は 1 秒を下回ったため、1〜6回目を FR2.2a の条件がそろわない回として外した（承認の場で確かめる）。時計のずれを ssh の前後のホスト時刻の中間と比べる誤った測り方で 2.2 秒と読み、測り方を直して範囲（VM−終わり、VM−始め）で記録し直した。配備したアプリは2回止めた（約 12 分と約 4 分）。 (learned 2026-10-04) \n- k6 の判定は1回の繰り返しに要求1つの場面の iteration_duration で行い、トークンは setup() で取って場面を 3 分にした。時計のずれに強い代わりに、操作が2つ以上の場面は op のタグが iteration_duration に付くかを台本で確かめる必要がある（group のレビューの R-01 を先に避けた）。 (learned 2026-10-05) \n- 上限を下げた k6 の場面を置かず、接続の数は結合テスト NavigationConnectionUsageIT で決定的に確かめる。読み取りだけで1要求1本のため詰まりが起きず、role のレビューの R-02 の「k6 では見積もりの誤りを見分けにくい」を避けられる。代わりに、負荷の下での待ちの長さは測らない。 (learned 2026-10-05) \n- 違反のテストは先の側を確定させてから書く待たない違反、上限切れは H2 の上限まで放さない形にした（Q3: A）。時間の境に合否を預けない代わりに、待った後の違反の経路は区分の単体テストだけで確かめる。 (learned 2026-10-06)"
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
  "input_sha256": "sha256:1b1210c2e61bcacd9398989674a4d7c1a18abbfde6a25bf393014ab8e8f2f363",
  "contract_sha256": "sha256:0800cfde8b8915bab9d374a3c5479c6961cc23a534522745ba4170d1b16e7349"
}
```

## Build and Test に引き継ぐこと

- `verify` の全体の時間と、`ApiAccessConsistencyIT` による増え方の実測（要件 NFR6.8、9節 D-2）。
- `access.service` の本体に差が無いことの確かめ（NFR 要件の R-04。Step 23 の記録を正とする）。
- B9（U7）の最初に、make-you-chic-ui の固定先の更新の後の実物（`button` と `aria-expanded`、`ul` の入れ子、tree の役割なし）と `shared/tree` の形が合うことを確かめる（Functional Design R-05）。
- 共有の木の実際のブラウザの axe は U6（S4）・U7（S8）の検査が持つ（要件 NFR4.5）。
- B1（dsl-v2）以後の Bolt は、足す・変える口に `ApiAccess` の印を付け（または保ち）、PUBLIC を足すときは `PublicApiInventory` の一覧の行と理由を同じ変更で直す。
