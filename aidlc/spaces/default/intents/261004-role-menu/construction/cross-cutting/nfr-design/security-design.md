# セキュリティの設計（Security Design）— U1 cross-cutting

## 1. 出典

- この単位の NFR 要件: `nfr-requirements/security-requirements.md`（NFR1.1〜NFR1.10・NFR4.1〜NFR4.5・NFR6.1〜NFR6.8、2.2 の PUBLIC の一覧、4節の承認の場の直し R-01）、`tech-stack-decisions.md`（1〜5節）
- この単位の機能設計: `functional-design/functional-spec.md`（W1〜W7、3節の 34 の口、10節の直し R-01・R-02）、`rules.md`（BR1.1〜BR5.6）
- 契約: `contract-summary.md`（C1 API の分類の注釈、C2 画面の登録の型と共有の木）
- 部品: `components.md`（AccessControl・SharedTreeView・AppFrame）
- ADR-008（`decisions.md`）
- この段の答え `nfr-design-questions.md`（Q1: A、まとめの確認 Looks correct）
- NFR 要件の読み直しの記録（R-02〜R-07、この段で手当てする Minor・Suggestion）
- `team.md`（Code Style・Testing Posture）、`project.md`（学び）

## 2. 捨ての試しの結果（Q1: A）

### 2.1 試しの形

- このリポジトリを scratchpad（リポジトリの外）へ写した（`.git`・`build`・`node_modules`・E2E の報告・`.env` は写さない）。写しの全体の置き場に試しの結合テスト1つ（`@SpringBootTest`、既存の `TestDatabase` で一時の内部DB）を足し、写しの中で `:backend:integrationTest --tests <その1件>` だけを流した。終わった後に写しを消した。本物の作業フォルダでは git・ビルドを使っていない。
- 版: Spring Boot 4.1.1、Spring Security 7.1.1（`spring-security-web`）。設定は本番と同じ（テスト用の決まり `PublicApiTestRules` とテストだけの口は無効）。
- かかった時間: コンパイルを含めて約 32 秒（2026-10-06 07:55:29〜07:56:01 JST）。
- 主体: 未ログインは `AnonymousAuthenticationToken`（比べに null も渡した）。ログイン中は `AuthenticatedUserToken(new AuthenticatedUser(id, 見本のメールアドレス, admin))`（`example.com` の見本の値。秘密の値は使っていない）。

### 2.2 7つの点の結果

| # | 確かめた点 | 結果 | 設計への意味 |
|---|---|---|---|
| 1 | Bean として得られるか | 得られた。型 `WebInvocationPrivilegeEvaluator` の Bean は1つ（名前 `privilegeEvaluator`、実体 `RequestMatcherDelegatingWebInvocationPrivilegeEvaluator`） | 型で注入できる。MockMvc への切り替え（BR1.5・要件 NFR1.4）は要らない |
| 2 | 方法つきの決まりを方法ごとに判定できるか | できた。`GET /api/appearance` は未ログインで通り、`POST /api/appearance` は未ログインで止まり、ログイン中は通った（`/api/**` の既定に落ちる） | 判定は方法ごとに行う。口の宣言された方法をそのまま渡す |
| 3 | 未ログインの主体の渡し方 | null と匿名のトークンで結果が同じ（`/api/me/preferences` はどちらも止まり、`POST /api/auth/login` はどちらも通る） | 本番の入口と同じ匿名のトークンで渡す（読み手に意図が分かるため）。null は使わない |
| 4 | `AuthenticatedUserToken` の主体を `AdminAuthorizationManager` が読めるか | 読めた。`GET /api/admin/check` は管理者でない利用者で止まり、管理者で通った。`GET /api/me/preferences` は管理者でない利用者で通った | 3つの主体の期待の表（要件 NFR1.3）をそのまま検査にできる |
| 5 | パス変数の見本の値で照合が変わらないか | 変わらない。`/api/problems/x` と `/api/problems/{slug}` の文字のままの両方で、未ログインが通った | 判定用の URI は、パス変数を見本の値 `1` に置き換えて作る（どの値でも決まりの照合は変わらないが、実際の要求に近い形にそろえる） |
| 6 | `server.error.path` を解決した道を判定にかけられるか | この設定は無く（既定）、口の一覧の道の型は解決済みの `/error` だった | 実行時の検査は口の一覧の道の型をそのまま使う。静的な検査は注釈の文字の `${server.error.path:/error}` を既定値で読む（2つの検査の集合はクラスと方法で比べ、道では比べない。4.3） |
| 7 | 明示の matcher の無い `/error` を未ログインで通すか | 通した（`GET`・`POST` は匿名のトークン、`DELETE` は null でも通る。`anyRequest().permitAll()` による） | `/error` の2つの口は PUBLIC の期待どおりに判定される。明示の matcher を足す必要は無い |

あわせて、全体の確かめ（設計の前提の裏付け）:

- `requestMappingHandlerMapping` の口は 34 で、すべて `cherry.mastersmith` の本番のクラスだった（Spring 自身の口は無い。actuator は別の対応づけ）。
- 34 の口について、宣言された方法ごとに3つの主体で判定し、期待の分類（PUBLIC の一覧・`AdminPaths`・それ以外は AUTHENTICATED）との食い違いは 0 件だった。
- PUBLIC の口は方法と道の組で 14、口の単位で 9（要件 2.2 と一致）。
- `/error` の2つの口の宣言された方法は `[GET, HEAD, OPTIONS]` と `[POST, PUT, PATCH, DELETE]`、`/api/problems/{slug}` の道の型は変数名を含む宣言の形のままだった（要件 2.2 の正規化の決まりのとおりに比べられる）。

## 3. 守りの多層（Defense in Depth）

| 層 | 仕組み | 落とすもの | 要件 |
|---|---|---|---|
| L1 | 口の隣の印 `ApiAccess` | 読み手が分類を取り違える | NFR1.1 |
| L2 | 静的な構造の検査（ArchUnit） | 印の書き忘れ・二重、ADMIN と `AdminPaths` の食い違い、AUTHENTICATED の道の誤り | NFR1.1・NFR1.2・NFR1.5 |
| L3 | 実行時の検査（3つの主体の判定と PUBLIC の一覧） | 印と安全の決まりの食い違い、`permitAll` の広げすぎ・書き忘れ、PUBLIC の口の意図しない増減 | NFR1.3・NFR1.5・NFR1.6 |
| L4 | 本番の Spring Security の決まり（変えない） | 実際の要求の拒否（判定の正） | NFR1.7・NFR1.8 |

L2・L3 はテストで、本番の判定には関わらない。U1 は L4 の仕組み（`SecurityConfig`・各機能の `SecurityRuleContributor`・`AdminAuthorizationManager`・`ApiDefaultAccess`）を変えない。

## 4. バックエンドの検査の設計

### 4.1 注釈（`common.security`）

- `ApiAccess`（実行時に読める、型と方法に付けられる）、値は `ApiAccessLevel`（PUBLIC・AUTHENTICATED・ADMIN）を1つ（機能設計 ENT-001・ENT-002）。
- 注釈は実行の中身を持たないため、`TraceAspect` の対象の層に個人に関する値・秘密を流す経路を作らない（要件 NFR1.10）。

```java
// 説明用の断片（形だけ）
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ApiAccess {
    ApiAccessLevel value();
}
```

### 4.2 静的な検査（`ApiAccessArchitectureTest`、全体の置き場の `*Test`）

1. 本番のクラスを読み込む（既存の `ArchitectureTest` と同じ `DO_NOT_INCLUDE_TESTS`）。
2. `@RestController`・`@Controller` を持つクラスの、RequestMapping 系の注釈（`@GetMapping` などを含む）を持つ方法を口として集める。
3. 規則（それぞれ `allowEmptyShould(false)`）:
   - 方法かクラスのどちらか一方だけに `ApiAccess` がある（要件 NFR1.1）。
   - 道（クラスと方法の注釈の道をつないだもの。`${名前:既定値}` は既定値で読む）について、ADMIN と `AdminPaths.isAdminOnly` が両方向で一致し、AUTHENTICATED の道は `/api/` で始まり管理者の道の外にある（要件 NFR1.2）。
   - `common.security` が機能のパッケージに依存しない（機能設計 BR1.8）。
4. 口の集合（クラス名と方法の名前・引数の型の組）を数え、34 以上であることを確かめる（要件 NFR1.5）。
5. 違反の文は、口のクラス名・方法名・道だけを並べる（要件 NFR1.10）。

#### 4.2.1 検査が違反を本当に落とすことの確かめ（要件 NFR1.1・NFR1.2、承認の場の直し R-01）

規則は本番のクラスにしか当てないと、規則の中身が緩んでも（例: 二重の印を見逃す）口の数 34 以上の確かめでは気づけない。そこで、規則を作る所を1か所にまとめ、本番のクラスと違反の見本のクラスの両方に同じ規則を当てる。

- **規則の置き場**: 規則（ArchUnit の規則と条件）を作る手伝い `ApiAccessRules` を `backend/src/test/java/cherry/mastersmith/common/testsupport/` に置く。読み込むクラスの集まりを引数に取り、4.2 の手順 3 の規則を返す。`ApiAccessArchitectureTest` は本番のクラス（`DO_NOT_INCLUDE_TESTS`）に、確かめのテストは見本のクラスにこの規則を当てる。
- **違反の見本のクラス**: `backend/src/test/java/cherry/mastersmith/common/testsupport/apiaccess/` に、1つのクラスに違反を1つだけ持つ見本を置く。どれも `@RestController` を付けるが、決して設定しない設定の値の条件（`@ConditionalOnBooleanProperty("mastersmith.test-fixture.api-access-samples")`。既存の `TestFixtureEndpoints` と同じ形）を付け、ほかの結合テストの起動で Bean にならないようにする。本番の検査（`DO_NOT_INCLUDE_TESTS`）にも入らない。

| 見本のクラス | 違反 | 落とすべき規則 |
|---|---|---|
| `NoMarkSample` | 印が無い口 | 印は一方だけ（NFR1.1） |
| `DoubleMarkSample` | クラスと方法の両方に印がある口 | 同上 |
| `AdminOutsideSample` | `/api/other` に ADMIN | ADMIN と `AdminPaths`（NFR1.2） |
| `AdminPathNotAdminSample` | `/api/admin/x` に AUTHENTICATED（もう1つの口に PUBLIC） | 同上（逆向き） |
| `AuthenticatedOutsideApiSample` | `/other` に AUTHENTICATED | AUTHENTICATED の道（NFR1.2） |
| `ValidSample` | 違反なし（方法の印でクラスの印が無い口、`${server.error.path:/error}` の PUBLIC、`/api/admin` そのものの ADMIN） | どの規則も通す（規則が広げすぎて正しい口を落とさないこと） |

- **確かめのテスト** `ApiAccessRulesTest`（全体の置き場の `*Test`）: 見本のクラスを1つずつ ArchUnit の `ClassFileImporter` で読み込み、規則を当てる。
  - 違反の見本ごとに、規則の確かめが `AssertionError` で落ち、その文に見本のクラス名と方法名が入ることを確かめる。
  - `ValidSample` はすべての規則を通ることを確かめる。
  - 何も読み込まない集まりに規則を当てると落ちること（`allowEmptyShould(false)` が効いていること）を確かめる。

```java
// 説明用の断片（見本1つの確かめ）
JavaClasses sample = new ClassFileImporter().importClasses(DoubleMarkSample.class);
assertThatThrownBy(() -> ApiAccessRules.markedExactlyOnce().check(sample))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("DoubleMarkSample");
```

### 4.3 実行時の検査（`ApiAccessConsistencyIT`、全体の置き場の `*IT`）

1. `@SpringBootTest`（本番の設定、`TestDatabase` の一時の内部DB）で起動する。テスト用の決まりとテストだけの口の設定は入れない。ほかの結合テストと同じ設定の形にして、起動の文脈を使い回せるようにする（要件 NFR6.8）。
2. `WebInvocationPrivilegeEvaluator` を型で、`RequestMappingHandlerMapping` を名前 `requestMappingHandlerMapping` で注入する（2.2 の点 1）。
3. 口の一覧のうち、口のクラスが `cherry.mastersmith` の本番のクラスのものだけを集める。その集合（クラスと方法）が静的な検査の集合と一致すること、34 以上あることを確かめる（要件 NFR1.5）。
4. 口ごとに、方法かクラスの `ApiAccess` を読み（Spring の注釈の読み取りの部品で、方法を先に見る）、宣言された方法（無ければ GET・POST・PUT・PATCH・DELETE）と道の型（パス変数は見本の値 `1` に置き換える）の組ごとに、3つの主体で判定する（2.2 の点 2・3・5）。
5. 期待の表（要件 NFR1.3）と比べ、違いをすべて並べて落とす。

| 印 | 未ログイン（匿名のトークン） | 管理者でない利用者 | 管理者 |
|---|---|---|---|
| PUBLIC | 通る | 通る | 通る |
| AUTHENTICATED | 止まる | 通る | 通る |
| ADMIN | 止まる | 止まる | 通る |

6. 主体は `common/testsupport` の手伝いで作る（匿名のトークン、`AuthenticatedUserToken` に `AuthenticatedUser` を入れたもの。メールアドレスは `example.com` の見本の値、利用者 ID は固定の数）。内部DB に利用者を作らない（判定は主体の値だけで決まるため。2.2 の点 4）。
7. 主体と期待を表で持ち、後の Intent が主体（権限を持つ・欠く利用者）と行を足すだけで広げられる形にする（読み直しの R-07。7節）。

```java
// 説明用の断片（判定の1組）
boolean allowed = evaluator.isAllowed("", uriOf(pattern), method, subject.authentication());
if (allowed != EXPECTED.get(level).get(subject)) {
    violations.add(handlerName + " " + method + " " + pattern + " " + subject + " expected=" + !allowed);
}
```

### 4.4 PUBLIC の口の一覧（要件 NFR1.6・2.2）

- 4.3 の同じテストの中の定数に、口の単位で 9 行（宣言された方法の集合と道の型の集合、理由のコメントにクラス名と方法名）を持つ。
- 印が PUBLIC の口の集まりを、同じ形（宣言された方法の集合と道の型の集合）で作って一覧と比べ、増えた行と減った行を並べて落とす。暗黙の HEAD・OPTIONS は足さない。道の型は口の一覧のもの（解決済み、変数名を含む）をそのまま使う（2.2 の点 6、要件 2.2 の正規化）。
- ADR-008 の補足（PUBLIC だけの追加の守り）として、コード生成の計画に書く。

#### 4.4.1 一覧の比べが増減を本当に落とすことの確かめ（要件 NFR1.6 の境界、承認の場の直し R-01）

- **比べる部品の置き場**: 比べ方を、Spring を起動せずに確かめられる純粋な関数にして、手伝い `PublicApiInventory` に置く（`backend/src/test/java/cherry/mastersmith/common/testsupport/`）。
  - `entryOf(RequestMappingInfo)`: 口の対応づけから、宣言された方法の集合と道の型の集合の組（`PublicApiEntry`）を作る。暗黙の HEAD・OPTIONS は足さず、道の型は変数名を含む宣言の形のまま。
  - `diff(expected, actual)`: 増えた行と減った行を返す（どちらも空なら一致）。
  - 一覧の定数（9 行）も同じ手伝いに置く。
- **実行時の検査（4.3）での使い方**: `ApiAccessConsistencyIT` は、印が PUBLIC の口の対応づけを `entryOf` で組にし、`diff` が空であることを確かめる。空でなければ、増えた行と減った行を並べて落とす。
- **確かめのテスト** `PublicApiInventoryTest`（全体の置き場の `*Test`、Spring を起動しない）: 一覧の定数を写して1か所だけ変えた見本を `diff` に渡し、要件の4つの境界で差が出ることを確かめる。

| 見本 | 期待 |
|---|---|
| 一覧に無い PUBLIC の口を1行足す（例 `POST /api/sample`） | 増えた行に1行出る |
| 一覧の1行を外す（例 `GET /api/appearance` の口から PUBLIC を外した形） | 減った行に1行出る |
| 道の型の変数名を変える（`/api/problems/{slug}` を `/api/problems/{name}` に） | 増えた行と減った行に1行ずつ出る |
| `/error` の口の宣言された方法を増やす（`[GET, HEAD, OPTIONS]` に `TRACE` を足す）、減らす（`HEAD` を外す） | それぞれ増えた行と減った行に1行ずつ出る |
| 変えない（一覧の定数そのもの） | 差が無い |

- あわせて `entryOf` に、Spring の `RequestMappingInfo.paths(...).methods(...)` で作った対応づけを渡し、宣言した方法の集合だけが入ること（HEAD・OPTIONS を足さない）と、変数名が残ることを確かめる。

```java
// 説明用の断片（変数名を変えた見本）
Set<PublicApiEntry> renamed = replace(PublicApiInventory.EXPECTED,
        entry("GET", "/api/problems/{slug}"), entry("GET", "/api/problems/{name}"));
Diff diff = PublicApiInventory.diff(PublicApiInventory.EXPECTED, renamed);
assertThat(diff.added()).containsExactly(entry("GET", "/api/problems/{name}"));
assertThat(diff.removed()).containsExactly(entry("GET", "/api/problems/{slug}"));
```

### 4.5 対象外の入口（要件 NFR1.7）

- actuator（Web に出すのは health だけ、`/actuator/health` だけが誰でも届く）と画面の静的配信は、口の一覧に入らないため検査の対象外のまま。守りは既存の公開の範囲と配信のテストに任せる（2.2 の「全体の確かめ」で、口の一覧に Spring 自身の口が無いことを確かめた）。

### 4.6 境界テストとカバレッジ（要件 NFR6.3・NFR6.4）

- `AccessBoundaryArchitectureTest`・`UserBoundaryArchitectureTest` を、今の依存をそのまま書いて足す。既存の構造の検査は緩めない。
- 手を入れるパッケージ（`common.security` と各機能の `web` の 8 つ）は `packagesJudgedByTotal` に当たらない。要件 NFR6.4 の `access.service` は、どの単位も手を入れない（U1・`role`・`group`・`navigation`・`dsl-v2` の NFR 要件で確かめた）。持ち主の単位は「無し（当たらない）」で、Build and Test で `access.service` の本体に差が無いことを確かめて記録する（読み直しの R-04）。

## 5. 画面の設計

### 5.1 共有の木（要件 NFR1.9・NFR4.1〜NFR4.5）

- 表示名と印は React の文字の差し込みだけで描き、HTML として描かない。子の読み込みの失敗は `labels.loadFailed` だけを出し、拒否の理由（例外・応答の中身）は画面に出さない。
- 開閉のボタンに `aria-expanded` と表示名を含む名前、選ぶボタンに `aria-current="true"`。tree の役割と矢印のキーは使わない。部品ごとに vitest-axe を1件（開いた状態・読み込み中・失敗を含む）。
- 深さの上限は木に置かない（読み直しの R-05）。子は呼ぶ側の `loadChildren` が返したものだけで、深さは呼ぶ側のデータで決まる（S4・S8 はスキーマ→テーブルの2段、カラムは右の表）。開いた節だけを描くため、深い木でも開いた所までしか描かない。深さの上限の決まり（`team.md` の N 階層のメニュー）はメニューの木を扱う U2・U7 が持つ。
- 実際のブラウザの axe は、木を使う画面の検査（S4 は U6、S8 は U7）が持つ。

### 5.2 登録の型とアイコンの照合（要件 NFR1.8・NFR6.5、読み直しの R-03）

- 区画（`section`）と `visibleWhen` は見せ方だけを決め、管理の API は L4 の判定で守る。登録の検査で、section が ADMIN なら visibleWhen が ADMIN であることを確かめる。
- アイコンの照合の一覧（18 個）を `app/registry` に置き、両方向を型の検査で守る。要素の数は固定しない（make-you-chic-ui の固定先を上げて増えたら、型の検査が落ちて気づく）。
- 登録の検査の境界: 一覧の名前は通る。一覧に無い名前と空の文字は `RegistrationError` で止まる。

```ts
// 説明用の断片（両方向の型の確かめ）
import type { IconName } from 'make-you-chic-ui'

export const ALLOWED_ICONS = ['menu', 'list' /* …18 個 */] as const satisfies readonly IconName[]
type MissingIcons = Exclude<IconName, (typeof ALLOWED_ICONS)[number]>
export const ALL_ICONS_COVERED: [MissingIcons] extends [never] ? true : never = true
```

### 5.3 ESLint の制限（要件 NFR6.1・NFR6.2、読み直しの R-06）

- `eslint.config.js` が `src/features/` のディレクトリの一覧から機能ごとの `no-restricted-imports` を作り、`src/shared/**` には `app/`・`features/` を止める決まりを当てる。テストのファイルは対象外。
- 確かめのテストは Vitest の node の実行環境（ファイルの先頭で `@vitest-environment node`）で、ESLint の `ESLint` の部品に `frontend/eslint.config.js` を読ませ、`lintText` に見本の文と `filePath` を渡して誤りの有無を見る。
  - 止める: `features/registration/x.ts` から `../auth/authSession`・`../../features/auth/authSession`、`shared/tree/x.ts` から `../../app/registry/types`・`../../features/auth/authSession`。
  - 許す: `features/useradmin/testing/x.ts` から `../api/types`、`features/auth/x.ts` から `../../app/i18n/i18n`・`../../shared/api-client/apiClient`、`features/registration/x.test.ts` から `../auth/authSession`。
- 本体の違反 1 件は、`useLogout` に直して 0 件にする（5.4）。

### 5.4 ログアウトの口

- `LoginStateProvider` の任意の `logout` を `auth` が渡し、骨組みの `useLogout` が呼ぶ。提供元に `logout` が無ければ何もしない。失敗は外へ出さない（画面の側のトークンの破棄は auth が必ず行う）。`authSession` の持ち主は auth のまま。

## 6. テスト・依存・観測

- テストの決まり（要件 NFR6.6）: 説明文は英語、描画の後の値は `waitFor`、時間の上限を原因を確かめずに延ばさない。Java の単体は `*Test`、Spring を起動するものは `*IT`。
- 画面のカバレッジ（要件 NFR6.5）: `shared/tree`・`app/registry`・`app/login-state` の追加の分をそれぞれのテストで覆い、全体の下限を満たす。
- 新しい依存は足さない（要件 NFR6.7）。試しで使った部品は、すべて今の依存に含まれていた。
- 検査の時間（要件 NFR6.8）: 目標の数値は置かない。試しでは1つの結合テストがコンパイルを含めて約 32 秒だった。Build and Test で `verify` の時間の増え方を実測して記録する。
- 観測: U1 は実行時に動く部品（指標・ログを出すもの）を持たないため、観測の設計は当たらない。

## 7. 後の Intent での広げ方（読み直しの R-07）

業務データの API を権限で守る後の Intent（J・K）は、`ApiAccessLevel` に値を足す（互換の変更）か、AUTHENTICATED のまま中で判定するかを決める。値を足すときは、その Intent が 4.3 の主体（権限を持つ・欠く利用者）と期待の表の行を足す。U1 の検査は表を足すだけで広げられる。

## 8. 上流との差

1. 要件 NFR1.4 と機能設計 BR1.5 の MockMvc への切り替えは、試しで判定の部品が使えると確かめたため使わない（決まりは切り替えの手順として残るが、発動しない）。
2. 未ログインの主体は、匿名のトークンで渡すと決めた（要件は渡し方を決めていなかった。null でも同じ結果）。
3. 静的な検査と実行時の検査の口の集合の一致（要件 NFR1.5）は、クラスと方法で比べる。道では比べない（静的な検査は `${server.error.path:/error}` を既定値で読み、実行時は解決した値を読むため、設定を変えると道が食い違いうる）。
4. 要件 NFR6.4 の `access.service` の持ち主は「無し（当たらない）」とした（読み直しの R-04）。
5. NFR 要件の読み直しの Minor・Suggestion（R-02・R-03・R-05・R-06・R-07）は、この設計で手当てした（2.2・5.2・5.1・5.3・7節）。要件の文書は書き換えていない。

## 9. 承認の場の決定と直し

- 日付: 2026-10-06
- 決定: 「推奨の案のとおり直す」（Request Changes。直す範囲は各単位の読み直しの Major と、単位の間でそろえる3点。この単位で当たるのは R-01 の1件で、R-02 以降の Minor は直していない）
- 直した指摘:
  - **R-01（Major）**: 要件 NFR1.1・NFR1.6 が求める「検査が違反を本当に落とす」ことの確かめ（検査の検査）が設計に無かった。次の2つを足した。
    - 4.2.1: 規則を作る所を `ApiAccessRules` の1か所にまとめ、本番のクラスと違反の見本のクラスに同じ規則を当てる形にした。見本は、印なし・二重・ADMIN と `AdminPaths` の両方向の食い違い・AUTHENTICATED の道の誤り・違反なしの6つ。見本は決して設定しない条件で Bean にしない。確かめのテスト `ApiAccessRulesTest` で、違反の見本が落ち、違反なしが通り、空の集まりが落ちることを確かめる。
    - 4.4.1: PUBLIC の一覧の比べを純粋な関数（`PublicApiInventory.entryOf`・`diff`）にした。`PublicApiInventoryTest` で、行を足す・外す・変数名を変える・`/error` の方法を増やす減らすの4つの境界で差が出ること、変えなければ差が無いことを確かめる。
  - あわせて、traceability.json の NFR1.1・NFR1.6 の target をこの節に向け、logical-components.md 1節に手伝い（`ApiAccessRules`・`PublicApiInventory`）と、確かめのテスト（`ApiAccessRulesTest`・`PublicApiInventoryTest`）・違反の見本のクラスの行を足した。

