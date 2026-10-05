# 機能設計の質問 — U1 cross-cutting

対象の単位: U1 cross-cutting（kind: library。バックエンドの API の分類の注釈と構造の検査、画面側の ESLint の制限・共有の木 `shared/tree`・画面の登録の型の拡張）。
画面の部分（共有の木と登録の型）は、U2 と同じ扱いで `frontend-components.md` を作って設計する（単位の一覧の R-04 の考え方）。

質問は 6 問です。上流・承認の場・`team.md`・`project.md`・コードで決まっている点は、下の「決まっていること」に書き、質問にしていません。

---

## 決まっていること

### バックエンド（API の分類）

- 分類の注釈は `common/security` に置く Java の注釈で、値は `PUBLIC`（ログイン不要）・`AUTHENTICATED`（ログインだけ）・`ADMIN`（管理者の印）の3つ。コントローラーの口（RequestMapping を持つ方法）は、型か方法のどちらかに印を1つだけ持つ（契約 C1、ADR-008）。
- 注釈の名前は、契約 C1 の仮の名前のとおり `ApiAccess`（値の列挙は `ApiAccessLevel`）とする案で進め、まとめの確認で確かめる（C1 の未決の「注釈の名前」。値を1つだけ持つ注釈にすると「1つだけ」が型で守られるため、3つの別々の注釈より検査が少なく済む）。
- 印の無い口と、印と安全の決まりの食い違いを落とす検査は、既存の `ArchitectureTest` と同じ置き場（`backend/src/test/java/cherry/mastersmith/` の直下、全体の構造の検査）に置く（ADR-008、DF3: A）。`ADMIN` と `access.domain.AdminPaths` の判定は両方向で確かめる（C1 の rules）。
- U1 が印を付けるのは既存の API だけ。U2〜U5 が足す・変える API への印はそれぞれの単位で付ける（単位の一覧の R-03）。
- 既存の口はコントローラー 10 個・方法 34 個（`/api/**` が 32、`/error` が 2）で、今の安全の決まりから読む分類は次のとおり（コードで確かめた。下の「コードで確かめた事実」）。
  - `PUBLIC` 7: `POST /api/auth/login`・`POST /api/auth/session/refresh`・`POST /api/auth/session/logout`・`GET /api/appearance`・`GET /api/problems/{slug}`・`POST /api/registration/verify`・`POST /api/registration/complete`
  - `AUTHENTICATED` 3: `GET /api/me/preferences`・`PUT /api/me/preferences`・`POST /api/me/password`
  - `ADMIN` 22: `/api/admin/users`（7）・`/api/admin/dsl`（10）・`/api/admin/check`（1）・`/api/admin/invitations`（4）
  - `/error`（`ErrorPathController` の2つ）の扱いは Q2。
- ログインは Spring Security のフィルターではなく `AuthController` の口（`POST /api/auth/login`）で受けている。フィルターだけが受ける入口は今は無い（単位の一覧の「フィルターが受けるログインなど」はコードと違う。下の Issues）。
- 印を付けるために手を入れるのは、各機能の `web` のパッケージ（`useradmin.web`・`dslmanage.web`・`access.web`・`auth.web`・`appearance.web`・`user.web`・`common.error.web`・`invitation.web`）と、注釈を置く `common.security` だけ。`packagesJudgedByTotal` の 7 パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）には手が入らない見込みで、U1 にカバレッジの下限の作業は付かない（`team.md` の Testing Posture。`common.health` の行 79.2% の作業も起きない）。
- 本体に手を入れる機能のうち、境界テストが無い `access` と `user` には、今の依存をそのまま書いた `AccessBoundaryArchitectureTest`・`UserBoundaryArchitectureTest` を足す（`team.md` の Code Style、bolt-plan の B2）。`common` は機能ではなく共通部品で、全体の `ArchitectureTest` が受け持つため、境界テストは足さない（まとめの確認で確かめる）。
- 注釈は `common.security` に置き、`access` などの機能に依存しない。検査（テスト）は全体の置き場から `AdminPaths` を読むだけで、本体の依存の向きは変えない（ADR-008、`access` を部品の依存から外す決定）。
- 既存の ArchUnit の境界テストは緩めない・消さない。足すだけ（`team.md` の Code Style）。

### フロントエンド（ESLint の制限）

- 画面の機能どうしの直接の import は、ESLint の `no-restricted-imports` で止める（`team.md` の Code Style、要件 C5）。新しい ESLint のプラグインは足さない（`team.md` が道具を `no-restricted-imports` と決めているため）。`no-restricted-imports` は import の文字列だけを見るため、`src/features/<featureId>/` ごとに（ディレクトリの一覧から作る）決まりを分け、自分の機能の外の兄弟の機能を指す相対の道（例: `../auth/...`・`../../features/auth/...`）を止める形で書く。機能の中の下位のディレクトリ（例: `useradmin/testing` から `../api/types`）は止めない（今の機能の中の深さは最大2段）。
- 既存の違反は `features/registration/useRegistration.ts` の `import { logout } from '../auth/authSession'` の1件で、この単位（B2、この Intent で最初に画面に手を入れる Bolt）で直す（`team.md`）。直し方は Q4。
- 機能から `app/` と `shared/` を読むことは今までどおり許す（機能の画面は `app/registry/types`・`app/i18n` などを使う。登録で骨組みにつながる形、`features/README.md`）。

### フロントエンド（共有の木 `shared/tree`）

- 置き場は `frontend/src/shared/tree`。U6（S4 の権限の設定）と U7（S8 の自分の権限）が使う（契約 C2、単位の一覧の R-01）。
- props は契約 C2 のとおり: `nodes`（id・label・hasChildren・badges）、`loadChildren`（節を開いたときに子を読む Promise）、`selectedId`・`onSelect`、`expandedIds`・`onToggle`（開閉の状態は外から渡す）。項目を足すだけの変更は互換（契約の共通の決まり）。
- 開閉は入れ子のリスト（`ul`）と開閉のボタン（`button` と `aria-expanded`）で、ARIA の `tree` の役割と矢印のキーの移動は使わない。キーボードは Tab で移り Enter・Space で操作する。選んでいる節は `aria-current` で示す（契約 C2、interaction-spec.md の PermissionTree「木は NavTree と同じ開閉のボタンの形」、AC1.2.14）。開いた所だけ子を読む（mockups.md の S4「開いた所だけを読み込む」）。
- make-you-chic-ui に木の部品は無い（`vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/` に Tree が無いことをソースで確かめた）。make-you-chic-ui からの報告（5bf1ffe）でも、木の部品は今回は対象外とされたため、計画どおり frontend の側で作る（design-system-mapping.md の「木の部品」、`team.md` の Way of Working）。取り込まれたら置き換える。
- 表示名（label）は文字として出し、HTML として描かない（React の文字の差し込みだけを使う。`team.md` の N 階層のメニューの「信頼できない入力」と同じ考え方）。
- 画面の部品ごとにアクセシビリティの検査（vitest-axe）を1件入れる。描画の後に反映される値は `waitFor` で待って確かめる（`team.md` の Testing Posture）。

### フロントエンド（画面の登録の型）

- 拡張は `frontend/src/app/registry/types.ts` で行う。管理のメニュー（ロール・グループ・権限の受け渡し）は、管理者の印（既存の `ADMIN`）で出す。業務のメニューは登録ではなく U5 の API（契約 C9）から作るため、登録の型に業務のメニューは足さない（契約 C2）。
- 管理と業務のメニューを `AppShell` にどう組み立てるか（`navSections` に渡す形、見出しの文言、開閉の状態の持ち方）は U7 の持ち物。U1 は登録の型と、その登録の検査（`validateRegistrations`）の拡張だけを持つ（単位の一覧の U7 の持ち物、R-02）。
- make-you-chic-ui の 5bf1ffe（固定先の更新は B9 の専用のコミット）で、`AppShell` に `navSections`（`{id, heading, items}`、`items` は `id`・`label`・`icon?`・`href?`・`current?`・`children?`、空の区画は描かない）・`navExpandedIds`／`onNavExpandedChange`・`navLabels` が足された（報告の記述による。5bf1ffe のソースは vendor に無い）。畳んだサイドバーでは第1階層のアイコンだけが出る。登録の型はこの形に組み立てやすいように広げる（Q6）。

---

## この段で決める設計の要点（質問にしない案。まとめの確認で確かめる）

- 共有の木の子の読み込み: 一度読んだ子は木の中に持ち、閉じて開き直しても読み直さない（読み直しは呼ぶ側が `nodes` を替えたときだけ）。読み込み中はその節の下に「読み込み中」を出し、失敗したらその節の下に文と「再試行」のボタンを出す（例外を外へ投げない。画面全体を止めない）。
- 共有の木の文言（開く・閉じる・読み込み中・再試行など）は、呼ぶ側が `labels` で渡す（make-you-chic-ui の `navLabels`・`Table` の `labels` と同じ考え方）。契約 C2 の props に足すだけの互換の変更とする。
- 共有の木の節の開閉のボタンの読み上げの名前に、節の表示名を含める（例: 「販売DB を開く」。AC5.1.1 のサイドバーと同じ）。
- 構造の検査で読む口は本番のクラスだけとする（ArchUnit は既存どおり `DO_NOT_INCLUDE_TESTS`）。テストだけの口（`auth/testsupport/ProtectedTestEndpoint`・`common/testsupport/TestFixtureEndpoints`）は設定の値で有効にするもので、検査の対象にしない。

---

## Q1 印と安全の決まりの食い違いの確かめ方

背景: 契約 C1 は、印の無い口と、印と安全の決まりの食い違いを落とすことを求めています。`ADMIN` は `AdminPaths` の純粋な関数と道を比べれば足りますが、`PUBLIC` は各機能の `SecurityRuleContributor`（auth・appearance・invitation）と `SecurityConfig` の `permitAll` で決まっていて、コードを静的に読むだけでは確かめられません。どこまで確かめるかで、`PUBLIC` の決まりの広げすぎ（例: `permitAll` の道の書き間違い）を落とせるかが決まります。

A. 静的な検査だけ（ArchUnit。全体の置き場）: 印の有無と「1つだけ」、`ADMIN` の道と `AdminPaths` の両方向、`AUTHENTICATED` が `/api/` の下で `AdminPaths` の外にあること、を確かめる。`PUBLIC` の決まりとの一致は確かめない
B. A に加えて、実行時の検査（全体の置き場の結合テスト `XxxIT`）を置く（推奨）: アプリを起動し、本番のすべての口（`RequestMappingHandlerMapping` の一覧）を、要求を実際には送らずに Spring Security の判定（`WebInvocationPrivilegeEvaluator`）にかけ、未ログイン・管理者でないログイン中の利用者の2つで「`PUBLIC` は通る」「`AUTHENTICATED` は未ログインで止まり、ログイン中は通る」「`ADMIN` は両方で止まる」を確かめる。要求を送らないため、ログインの失敗の監査などの副作用が無い。`PUBLIC` の広げすぎと、`ApiDefaultAccess` との食い違いも落とせる。判定の部品が今の版（Spring Boot 4.1）で使えるかは、コード生成の最初で試し、使えなければ C の形に切り替える
C. A に加えて、実行時の検査で実際に要求を送る（MockMvc）: B と同じ組を、実際の要求の状態コード（401・403 か、それ以外か）で確かめる。確かさは高いが、`PUBLIC` の口（ログイン・登録の完了など）に送る要求の本文の用意と副作用（監査の行など）に気を配る必要がある
X. Other (please specify)

[Answer]: B **Mode:** guided

## Q2 印の対象外の入口の扱い

背景: 契約 C1 は「印の対象外の入口（actuator・静的配信・フィルターが受ける入口）の扱い」を U1 の機能設計の必須の入力としています。コードで確かめると、actuator（`/actuator/health` だけ公開）と画面の静的配信（`WebConfig` の `/**`）はコントローラーの口ではなく、フィルターだけが受ける入口は無く、コントローラーの口で `/api/` の外にあるのは `ErrorPathController` の `/error`（2つ。サーブレットの誤りの転送を受け、今は `anyRequest().permitAll()` で誰でも届く）だけです。`/error` をどう扱うかと、検査の対象をどこまでにするかが決まります。

A. 検査の対象は本番のすべてのコントローラーの口（道を問わない）とし、`/error` の2つには `PUBLIC` を付ける（推奨）。対象外の決まりを1つ（「コントローラーの口でないもの」）だけにでき、`/api/` の外に新しい口を足したときも印を求められる。actuator と静的配信は対象外として、その扱い（`/actuator/health` だけ公開・ほかの actuator は公開しない設定・静的配信は誰でも届く）を rules に書き、守りは既存のテスト（公開の範囲の確かめ）に任せる
B. 検査の対象を `/api/` の下の口だけにし、`/api/` の外の口は除外の一覧（今は `ErrorPathController` だけ）に明示する。一覧に無い `/api/` の外の口が現れたら落とす
C. A と同じだが、actuator の公開している口（`/actuator/health`）も Q1 の実行時の検査に含め、`PUBLIC` として確かめる
X. Other (please specify)

[Answer]: A **Mode:** guided

## Q3 ESLint の制限を当てる範囲

背景: `team.md` は「今ある違反の1件」としていますが、コードで数えると、本体の違反は `useRegistration.ts` の1件だけで、テストのファイルには別の機能を読むものが 9 件あります（`*/registration.test.tsx` などが、ほかの機能の登録を読んで画面を組む。`preferences/PasswordChangePage.test.tsx` などが `../auth/authSession` を読む）。また `shared/` は今 `app/`・`features/` を読んでいません。テストに当てるか、`shared/` の向きも止めるかで、この単位の作業量と守りの範囲が決まります。

A. 本体のソースだけに当て（`*.test.ts`・`*.test.tsx` は除く）、あわせて `shared/` から `app/`・`features/` を読むことも止める（推奨）。`team.md` の「違反の1件」の数え方と合い、テストは画面を組むためにほかの機能の登録を読む今の形のまま。`shared/` の向きは今の違反が 0 件で、置き場の決まり（`features/README.md` の「`app/`・`features/` には触れない」）を道具で守れる
B. A と同じ範囲だが、`shared/` の向きは止めない（機能どうしだけ）
C. テストのファイルにも当て、テストの 9 件をこの単位で直す（ほかの機能の登録を読む所は、骨組みの登録の読み込み（`registrationModules`）やテスト用の部品に替える）
X. Other (please specify)

[Answer]: A **Mode:** guided

## Q4 既存の違反（`useRegistration.ts` → `../auth/authSession`）の直し方

背景: 登録の完了の画面（`features/registration`）は、別の利用者でログイン中の端末で登録を続けるとき、`auth` の `logout()` を直接呼んでいます。`authSession` はログインの状態とトークンを持つ `auth` の中心の部品で、`shared/api-client` には `auth` が起動時に手段を差し込む口（`registerAuthHandlers`）が既にあります。どこにログアウトの口を置くかで、移すコードの量と、`auth` の持ち物の境界が決まります。

A. 登録の型の `LoginStateProvider` に任意の `logout`（Promise を返す関数）を足し、`auth` が登録で渡す。骨組み（`app/login-state`）が `useLoginState` と同じ形の口（例: `useLogout`）で機能へ渡し、`useRegistration` はそれを使う（推奨）。機能どうしを登録でつなぐ今の形のままで、登録の型の拡張（この単位の持ち物）と同じ所で済み、`authSession` の持ち主は変えない
B. `shared/api-client` の `AuthHandlers` に `logout` を足し、`shared` の関数から呼ぶ。既存の差し込みの口を使えて変更は小さいが、API の呼び出しの部品がログアウトの手続きの窓口を持つ形になる
C. `authSession`（と、それが使う `authApi`）を `src/shared/` へ移し、`auth` と `registration` の両方が `shared` から読む。持ち主がはっきりするが、移す量が多い（テストと `auth` の中の import の書き換え）
X. Other (please specify)

[Answer]: A **Mode:** guided

## Q5 共有の木の節の形（選ぶ操作と開閉の操作）

背景: S4・S8 の木では、スキーマやテーブルを「選ぶ」と右の表が替わり、「開く」と子が読まれます。今の DSL の画面の `DslMenuTree` は、表示名ごと1つの開閉のボタンで、選ぶ操作がありません。make-you-chic-ui の新しいサイドバー（5bf1ffe の報告）は、リンク（または押せない文字）と開閉のボタンを分けた形（`button` と `aria-expanded` と `ul` の入れ子、`tree` の役割なし）です。節の形で、キーボードの操作の数と、骨組みのサイドバーとの見た目・操作のそろい方が決まります。

A. 節を「選ぶボタン（表示名。`aria-current` で選んでいることを示す）」と「開閉のボタン（`aria-expanded`）」の2つに分ける（推奨）。make-you-chic-ui のサイドバーと同じ分け方で、骨組みのメニューと操作がそろう。選んでも開閉は変わらず、開閉しても選びは変わらない。Tab の止まる所は節ごとに2つになる
B. A と同じく2つに分けるが、閉じている節を選んだときは、あわせて開く（開いている節を選んでも閉じない）。選んだ所の子がすぐ見える代わりに、選ぶ操作が開閉の状態も変える
C. 節を1つのボタンにし、押すと選んで開閉も切り替える（`DslMenuTree` に近い形）。Tab の止まる所は少ないが、開いた節を選び直すと閉じてしまう
X. Other (please specify)

[Answer]: A **Mode:** guided

## Q6 画面の登録の型に足す項目

背景: 契約 C2 は、管理のメニューへの登録に足す項目の名前（見出しの区分など）を機能設計で決めるとしています。今の登録のサイドバーの項目（`SidebarItemRegistration`）は `id`・`labelKey`・`path`・`order`・`visibleWhen` だけで、今ある4件（管理・DSL・招待・利用者）はすべて `visibleWhen: 'ADMIN'`、アイコンは骨組み（`ShellLayout`）が固定の `list` を当てています。make-you-chic-ui の 5bf1ffe では、メニューを見出しつきの区画（`navSections`）で渡し、畳んだサイドバーでは第1階層のアイコンだけが出ます。登録の型にどこまで持たせるかで、U6 の登録と U7 の組み立ての形が決まります。

A. `SidebarItemRegistration` に、区画（`section`。今は `'ADMIN'` の1つの値の文字列リテラルの union、必須）と、アイコン（`icon`。make-you-chic-ui の `IconName`、任意。無ければ骨組みが既定のアイコンを当てる）を足す。登録の検査で「`section: 'ADMIN'` の項目は `visibleWhen: 'ADMIN'`」を確かめる。区画の見出しの文言と並びは骨組み（U7）が持つ（推奨）。区画が登録に明示され、後で区画が増えても型を足すだけで済む。今ある4件の登録に `section` を足す（アイコンは任意のため U6・U7 で決めてよい）
B. 型は変えず、骨組み（U7）が `visibleWhen: 'ADMIN'` の項目を管理の区画として読み替える。アイコンも足さず骨組みが当てる。変更は最も小さいが、区画とアイコンの意図が登録に現れず、畳んだサイドバーでは管理の項目のアイコンがすべて同じになる
C. A に加えて、登録の項目に入れ子（`children`）を持たせ、管理のメニューも階層にできるようにする（今は要らないが、管理の画面が増えたときに備える）
X. Other (please specify)

[Answer]: A **Mode:** guided

## Consolidated Summary Confirmation

答えのまとめ（cross-cutting）:

- Q1 B: ArchUnit の静的な検査（印の有無と1つだけ・ADMIN と `AdminPaths` の両方向・AUTHENTICATED の道）に加え、全体の置き場に結合テストを置き、本番のすべての口を要求を送らずに Spring Security の判定（`WebInvocationPrivilegeEvaluator`）にかけ、未ログインと管理者でないログイン中の利用者で確かめる。部品が今の版で使えなければ、実際に要求を送る形（C）に切り替える。
- Q2 A: 検査の対象は本番のすべてのコントローラーの口。`/error` の2つは PUBLIC。actuator と静的配信はコントローラーの口でないため対象外とし、その扱いを決まりに書く。
- Q3 A: ESLint の `no-restricted-imports` は本体のソースだけに当て（テストは除く）、`shared/` から `app/`・`features/` を読むことも止める。
- Q4 A: 登録の型の `LoginStateProvider` に任意の `logout` を足して `auth` が渡し、骨組みが `useLogout` のような口で機能へ渡す。`authSession` の持ち主は変えない。
- Q5 A: 共有の木の節は、選ぶボタン（`aria-current`）と開閉のボタン（`aria-expanded`）に分ける（make-you-chic-ui の 5bf1ffe のサイドバーと同じ分け方）。
- Q6 A: `SidebarItemRegistration` に必須の `section`（今は `'ADMIN'` だけ）と任意の `icon`（`IconName`）を足し、`section: 'ADMIN'` は `visibleWhen: 'ADMIN'` であることを登録の検査で確かめる。見出しの文言と並びは骨組み（U7）が持つ。
- 「この段で決める設計の要点」（共有の木の子の読み込みと再試行、文言を `labels` で渡す、開閉のボタンの名前に表示名を含める、検査は本番のクラスだけ）もこのまま設計に入れる。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
