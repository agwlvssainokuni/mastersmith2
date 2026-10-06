# コードの要約（Code Summary）— U1 横断の準備（cross-cutting、Bolt B2）

計画: `code-generation-plan.md`（同じディレクトリ）。Step ごとのコマンドと結果は `generation-notes.md`。方法は test-after（Testing Contract のとおり、層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて流した）。作業ブランチは `feature/261004-role-menu-b2`（`develop` の c97f760 から）。記録のコミット c85c11b の後、コードは依頼者の承認を得て C1〜C5 の5つのコミット（1411bcb・b380ec6・8b4fefc・95e0789・83fbaaf）に積んだ。読み直し（R-01・R-04）の直しは、その後の未コミットの変更（4節 D-17・D-18）。

## 1. 作ったもの・変えたもの

アプリのソースの道の一覧は `source-manifest.json`（59 件）。

### 1.1 バックエンドの本体

| ファイル | 種類 | 中身 |
|---|---|---|
| `backend/src/main/java/cherry/mastersmith/common/security/ApiAccessLevel.java` | 足した | `PUBLIC`・`AUTHENTICATED`・`ADMIN`（ENT-001） |
| `backend/src/main/java/cherry/mastersmith/common/security/ApiAccess.java` | 足した | 印（`RUNTIME`、`TYPE`・`METHOD`、値1つ）。Javadoc に決まりと検査の場所（ENT-002） |
| 10 のコントローラー（`auth.web`・`appearance.web`・`common.error.web` の2つ・`invitation.web` の2つ・`user.web`・`useradmin.web`・`dslmanage.web`・`access.web`） | 印 | クラスに `@ApiAccess(ApiAccessLevel.X)` と import 2行だけ。PUBLIC 9・AUTHENTICATED 3・ADMIN 22 の口 |

### 1.2 バックエンドのテスト

| ファイル | 件数 | 中身 |
|---|---|---|
| `common/security/ApiAccessTest.java` | 3 | 注釈の形 |
| `ApiAccessArchitectureTest.java` | 5 | 本番のクラスに規則4つ、口 34 以上 |
| `ApiAccessRulesTest.java` | 7 | 違反の見本5つが落ちる・`ValidSample` が通る・空の集まりが落ちる |
| `PublicApiInventoryTest.java` | 9 | PUBLIC の一覧の比べの境界 |
| `ApiAccessConsistencyIT.java` | 5 | 3つの主体の判定（34 の口・117 組・食い違い 0）、集合の一致、本番のクラスだけ、PUBLIC 9 口（14 組） |
| `access/AccessBoundaryArchitectureTest.java`・`user/UserBoundaryArchitectureTest.java` | 3・3 | 今の依存のまま |
| 手伝い `common/testsupport/{ApiAccessRules, ApiAccessSubjects, PublicApiInventory}.java`、見本 `common/testsupport/apiaccess/` の6つ | — | 規則の1か所・主体と期待の表・PUBLIC の一覧 |

### 1.3 画面

| ファイル | 種類 | 中身 |
|---|---|---|
| `frontend/src/app/registry/types.ts` | 変えた | `SidebarSection`・`section`・`icon`・`LoginStateProvider.logout` |
| `frontend/src/app/registry/allowedIcons.ts` | 足した | 照合の一覧 18 個と両方向の型の確かめ |
| `frontend/src/app/registry/validateRegistrations.ts` | 変えた | 区画・区画 ADMIN と visibleWhen・アイコンの3つの問題 |
| `frontend/src/features/{admin,dsl,invitation,useradmin}/registration.ts` | 変えた | `section: 'ADMIN'` |
| `frontend/src/app/login-state/LoginStateGate.tsx` | 変えた | ログアウトの文脈と `useLogout()`（`Promise<boolean>`） |
| `frontend/src/features/auth/loginStateProvider.ts` | 変えた | `logout` を渡す |
| `frontend/src/features/registration/useRegistration.ts` | 変えた | `../auth/authSession` の import を消し `useLogout()` を使う（既存の違反の直し） |
| `frontend/src/shared/tree/{types.ts, SharedTreeNodeItem.tsx, SharedTreeView.tsx, SharedTreeView.css}` | 足した | 共有の木 |
| `frontend/eslint.config.js` | 変えた | 機能どうし・shared の import の制限、名前の重なりの検出 |
| `frontend/src/features/preferences/testing/renderPreferences.tsx` と呼ぶ側のテスト3つ | 変えた | 計画に無い直し（4節 D-12） |
| `frontend/src/features/README.md` | 変えた | 4つの節（区画とアイコン・`useLogout`・import の制限・共有の木） |
| テスト: `validateRegistrations.test.ts`（+6）・`LoginStateGate.test.tsx`（+4）・`loginStateProvider.test.ts`（+1）・`RegistrationPage.test.tsx`（+1）・`SharedTreeView.test.tsx`（10）・`eslintImportRules.test.ts`（22） | 足した | — |
| 既存のテストの見本6ファイル（`navigationItems`・`AdminForbiddenView`・`forbiddenHeading`・`ShellLayout`・`AppRouter`・`validateRegistrations` の各テスト） | 型だけの直し | `section: 'ADMIN'` |

## 2. 実装で決めたこと

- 静的な検査の規則は `ApiAccessRules` の1か所で作り、口をすべて対象に取って条件の中で印ごとに場合分けする。どれも `allowEmptyShould(false)`。`common.security` の規則は「`cherry.mastersmith` の中で依存してよいのは `common` の下だけ」。
- 2つの検査の口の集合は、クラス名・方法名・引数の型の鍵で比べる（道では比べない）。
- 実行時の検査の主体はトークンの値だけで作り、内部DB に利用者を作らない。判定の件数をテストのログ（構造化のキーと値）に出して記録できるようにした。
- 共有の木は、子の読み込みの状態を「どの `nodes` に対するものか」と組で持ち、`nodes` が替わったら捨てる。届いた結果は、呼んだときの `nodes` が今と同じで部品が付いているときだけ使う。再試行の後は子の区画にフォーカスを移す。
- ESLint の決まりのテストは node の実行環境で、ESLint の部品を `beforeAll` で1回だけ作る（初回の読み込みが遅く、Step 2 の確かめの1回目が既定の 5 秒に届いたため。時間の上限は延ばしていない）。

## 3. テストの量とカバレッジ

`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（colima の環境変数つき、13 分 56 秒）の実測:

- テストの件数: バックエンドの単体 1,577 件・結合 712 件（失敗 0・飛ばし 0、対象DB のテストも実行）、画面 112 ファイル・1,004 件。この単位で足したテストはバックエンド 35 件（単体 30・結合 5）、画面 44 件（`validateRegistrations` 6・`useLogout` まわり 6・共有の木 10・ESLint の決まり 22）。
- バックエンドのカバレッジ（Step 3 の基準 → 今回）: `common.security` 行 100% → 100%（14 → 18 行）・分岐 100% → 100%。8 つの `web` は行・分岐とも基準と同じ値（下がったものは無い）。全体 行 98.9%・分岐 94.8%（基準と同じ）。
- 画面のカバレッジ（全体）: 行 97.49%・分岐 92.67%。`shared/tree/SharedTreeView.tsx` 行 98.07%・分岐 86.66%、`SharedTreeNodeItem.tsx` 行 100%・分岐 91.66%、`app/registry` の足した分 100%、`LoginStateGate.tsx` 行 100%・分岐 85%。
- 下限（行 80%・分岐 70%、パッケージごとを含む）と計測の除外は変えていない。計画 4.5 のファイル（`access.service` を含む）に差は無い。
- `ApiAccessConsistencyIT` の所要は 8.6 秒（増え方の比べは Build and Test）。
- E2E（`./gradlew e2eTest`、6 分 37 秒）: 13 ファイル・153 件が通過（失敗 0）。新しい E2E は足していない。報告に資格情報・利用者のメールアドレス・トークンは無かった（メールアドレスの形は送り元の設定の見本 `example.com` の1件だけ）。報告の置き場は消した。

## 4. 計画との差

| # | 差 | 理由・扱い |
|---|---|---|
| D-10 | `ValidSample` に `/api/administrator` の AUTHENTICATED の口を足した（計画は3つの口） | 要件 NFR1.2 の境界（管理者の道でない似た名前）を見本で確かめるため |
| D-11 | `PublicApiInventoryTest` を 9 件にした（目安 8 件）。「一覧が 9 行・方法と道の組で 14」の確かめを1件足した | 一覧の定数の数え違いを落とすため |
| D-12 | `frontend/src/features/preferences/testing/renderPreferences.tsx`（テストのファイルではない手伝い）が `auth` を読む3件が、ESLint の制限で違反になった（計画と BR2.4 は違反を `useRegistration.ts` の1件だけとしていた）。依頼者の決定（A）で、手伝いから auth の import を外し、auth の登録・提供元・認証の状態の準備（`PreferencesTestAuth`）を呼ぶ側のテストのファイル3つ（`PasswordChangePage.test.tsx`・`PreferencesPage.test.tsx`・`registration.test.tsx`）から引数で渡す形にした。制限に例外は作っていない（BR2.3 のとおり） | 計画に無い変更。preferences のテスト 9 ファイル・89 件が通った |
| D-13 | ESLint の兄弟の機能を指す道の型を、機能の中のファイルの深さごとに作る形にした（深さ 0 は `../B`、深さ 1 は `../../B`、深さ 2 以上は深さ 2 の決まりにまとめる。`**/features/B` はどの深さでも止める） | 深さによらず同じ型にすると、`testing/` から自分の機能の `../registration`（ファイル）を同じ名前の機能 `registration` への道と取り違えた（誤検出2件）。依頼者が受け入れた。確かめを `eslintImportRules.test.ts` に1件足した |
| D-14 | 既存のテストの見本で visibleWhen が LOGGED_IN のものにも `section: 'ADMIN'` を入れた | 区画の値が今 ADMIN だけのため。登録の検査を通る見本は問題の文を部分一致で確かめていて、確かめの中身は変わらない |
| D-15 | `ApiAccess` の Javadoc（Step 20 の分）を Step 4 で書いた | 注釈を作るときに一緒に書いた。中身は計画のとおり |
| D-16 | `eslintImportRules.test.ts` に「node の実行環境で動く」と「同じ名前のファイルを兄弟の機能と取り違えない」の確かめを足し 12 件にした（目安 10 件） | D-13 の守りと実行環境の確かめ |
| D-17 | 読み直しの R-01（Major）の直し: ESLint の機能どうしの制限の `files` を、機能の中の深さちょうどのファイルにだけ当たる型（`**` を使わない）にして、深さ 0〜6 の決まりを作った。前は深さ 2 の決まりに `**` でより深いファイルをまとめていたため、深さ 3 以上から兄弟の機能を指す相対の道を止めず、深さ 1 のファイルに深さ 1 と 2 の決まりが重なって当たっていた。深さが上限 `MAX_FEATURE_DEPTH`（6）を超えるファイルが機能の中にあれば、設定の読み込みで失敗させる（`findTooDeepFeatureFiles`）。`eslintImportRules.test.ts` に、深さ 2・3・6 の違反、深さ 2・3 の許す例（自分の機能の同じ名前のファイル `../../registration` を含む）、各深さで効く決まりが1つでその深さの型であること、`files` の型が1つの深さだけに当たること、上限を超えるファイルの検出を足した（22 件） | 依頼者の決定（統合の前に直す） |
| D-18 | 読み直しの R-04（Minor）の直し: この要約の冒頭の「コードは未コミット」を、C1〜C5 のコミット済みに直した | 同上 |

計画 9節の D-1〜D-9 はそのとおりに作った。承認済みの設計の文書は書き換えていない。

## 5. 依頼者に確かめたいこと・承認の場で確かめること

1. 計画の「既存の境界テスト 11 個」に対し、検索で見つかった `*BoundaryArchitectureTest` は 10 ファイルだった（全件が通った。計画の数え方は確かめていない）。
2. Step 9 の追加の確かめ（`MeController` の印を一時に PUBLIC に変えて実行時の検査が落ちることを確かめ、元に戻した）の1回だけ、Gradle 全体で 15 分 18 秒かかった（テストのクラスは約 10 秒、前後の実行は約 1 分以内）。原因は確かめていない。
3. SpotBugs の関門で「`reactor.core.scheduler.Schedulers` が解析に無い」という案内が出たが、関門は通った。前からの案内かは確かめていない。
4. 計画との差 D-10〜D-16（4節）。

## 6. Build and Test に引き継ぐこと

計画の「Build and Test に引き継ぐこと」のとおり（`verify` の時間と `ApiAccessConsistencyIT` による増え方、`access.service` に差が無いこと、B9 の最初の make-you-chic-ui の実物と `shared/tree` の形の突き合わせ、実際のブラウザの axe は U6・U7、後の Bolt の口への印と PUBLIC の一覧の直し）。
