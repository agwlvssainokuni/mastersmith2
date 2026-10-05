# 機能設計の質問 — U7 app-frame-ui

対象の単位: U7 app-frame-ui（kind: ui、大きさ L）。骨組み（`frontend/src/app/`）の作業ロールの切り替え（トップバー）と、業務と管理のメニュー（サイドバー、N 階層）（S1）、`features/tables`（S2 テーブルの画面の置き場）、`features/mypermissions`（S8 自分の権限、Should）、make-you-chic-ui の固定先の更新。作るのは最後の Bolt の B9（B8 role-admin-ui の後）。ui の単位のため、成果物は `functional-spec.md`（画面の流れと状態の遷移の正）・`frontend-components.md`・`traceability.json` で、`entities.md`・`rules.md` は作らない（段の定義の `produces_kinds`）。

質問は 6 問です。上流・承認の場・先に確定した単位（cross-cutting・navigation・role・role-admin-ui）・`team.md`・`project.md`・コードと make-you-chic-ui のソースで決まっている点は、下の「決まっていること」に書き、質問にしていません。

---

## 決まっていること

### 画面と置き場（単位・部品・画面の段・Bolt の計画）

- U7 は骨組みの作業ロールの切り替えと業務・管理のメニュー、`features/mypermissions`（S8、Should）、`features/tables`（S2）、make-you-chic-ui の固定先の更新（入れ子のサイドバーを取り込む専用のコミット）を持つ。共有の木と登録の型は U1 のものを使い、U6 には依存しない（`aidlc/spaces/default/intents/261004-role-menu/inception/units-generation/unit-of-work.md` の U7、UQ4: A）。
- 画面の形は `aidlc/spaces/default/intents/261004-role-menu/inception/refined-mockups/mockups.md` の S1・S2・S8・10a 節、`interaction-spec.md` の WorkRoleSwitcher・NavTree・PermissionTree・TablePlaceholderPage、`accessibility-checklist.md`、`design-system-mapping.md` のとおり。トップバーに「作業ロール: 営業 ▼」、サイドバーは「業務」「管理」の見出しで分け、中身の無い見出しは出さない。開閉は開閉のボタン（`aria-expanded`）と `ul` の入れ子で、tree の役割と矢印のキーの移動は使わない。テーブルと子の両方を持つ項目は名前をリンク・開閉をボタンに分け、テーブルが NONE で子だけ見えるときはリンクにしない（ストーリーの D9）。
- 作業ロールの選択中の印は、Dropdown の `role="menuitem"` の項目の名前に文字「（使用中）」を含めて示す（`menuitemradio` は使わない。上流の 5bf1ffe でも対象外）。ロールが1つならボタンにせず文字で示し、ロールが無ければ「作業ロール: なし」と案内（AC4.1.4・AC4.1.9）。
- 〔Should〕は US4.2（S8）。時間が足りないときに Should の部分だけを B9 から外すかは、コード生成の計画の承認で諮る（`aidlc/spaces/default/intents/261004-role-menu/inception/delivery-planning/bolt-plan.md` の「Should の扱い」）。
- B9 の終わりの条件: 固定先の更新（専用のコミット、更新の前後のハッシュを記録、fast-forward での統合）、S1・S2・S8、実際のブラウザの axe（深い階層・長い名前、ブランドカラーとテーマのすべての組）、I の流れの E2E、`./gradlew verify` と統合の前の E2E（`bolt-plan.md` の B9、`team.md` の Way of Working）。

### API（契約 C8・C9 と、role・navigation の機能設計で確定した形）

- 作業ロール（`aidlc/spaces/default/intents/261004-role-menu/construction/role/functional-design/functional-spec.md` の 2.10・10節）: `GET /api/me/work-role` は `{roles: [{roleId, name}], current: {roleId, name} または null}`。`roles` はロールの ID の順で、先頭が「最初のロール」。`current` は読み替えの後の有効な作業ロール（読み取りは保存を書き換えず監査なし）。`PUT /api/me/work-role` は本文 `{roleId}` だけで 204。今と同じロールは何もしない 204（監査なし）。割り当ての外・存在しないロールは 409 `ROLE_NOT_ASSIGNED` で、画面は作業ロールを読み直す。整数でなければ 400。
- 自分の権限（S8、Should）: `GET /api/me/permissions/schemas`・`.../schemas/{schemaName}/tables`・`.../tables/{tableName}/columns`。応答は `{workRole: {roleId, name} または null, items: [{schemaName, tableName, columnName, displayName, effective: {main, create, delete}, inMenu, hasChildren}]}`。DSL が無いときは `items` が空、作業ロールが無いときは `workRole` が null ですべて NONE・不可（role の 2.11、契約 C8）。
- 業務のメニュー（`aidlc/spaces/default/intents/261004-role-menu/construction/navigation/functional-design/functional-spec.md` の 2.1・8節・9節）: `GET /api/me/navigation` は `{items: [NavNode], emptyReason: NOT_CONFIGURED または NOTHING_VISIBLE または null}`。`NavNode` は `id`（絞る前の DSL の木での位置の道。例 `2.0.1`。前置きは無い）・`label: {ja, en}`・`icon`（許した名前、既定 `list`）・`table: {schemaName, tableName} または null`・`navigable`・`items`。サーバーが作業ロールの実効の主権限で絞り、管理のメニューは含めない（FR10.5）。同じ DSL の間は作業ロールを切り替えても `id` は変わらず、DSL を適用し直すと変わりうる。
- テーブルの置き場: `GET /api/me/table-access?schema=…&table=…`（道の中の `%2F` などが要求の検査で 400 になるため問い合わせの引数にした。navigation の Q4 A）。READ・FULL は 200 `{schemaName, tableName, displayName: {ja, en}, main}`、NONE・DSL に無い組・DSL が無いは同じ 403 `ACCESS_DENIED`（表示名を含まない）。引数が無い・空・257 文字以上は 400 `VALIDATION_FAILED`。
- アイコン: サーバーは `backend/src/main/resources/navigation/allowed-icons.txt`（18 個）で照らした後の名前を返すが、画面も `app/registry` の一覧でもう一度照らし、知らない名前は `list` にする。一覧のファイルと `app/registry` の一覧の一致のテストは B7 で置く（navigation の 9節）。
- `label`・`displayName` は文字として描き、HTML として描かない（BR3.2、AC5.1.7）。`ja`・`en` に空の文字列がありうる（navigation の 9節）。
- `/tables` の置き場の 403 は管理の画面の 403 ではない（`frontend/src/shared/api-client/adminForbidden.ts` は `/api/admin/` で始まる道だけを扱う）。置き場の画面が自分で権限なしの表示にする。

### 先に確定した単位からの引き継ぎ

- cross-cutting（`aidlc/spaces/default/intents/261004-role-menu/construction/cross-cutting/functional-design/frontend-components.md` の 3節）: 登録の型に `section: 'ADMIN'`（必須）と `icon?: IconName` が足される。登録の項目を section ごとに束ねて見出しつきの区画にするのは U7。見出しの文言・区画の並び・「ホーム」の置き場・業務のメニューの id との重なりの避け方は U7 が決める。`icon` の無い項目には骨組みの既定（今は `list`）を当てる。`useLogout()` が `app/login-state` から出る。
- navigation（9節）: `NavNode.id` に U7 が前置き（例 `biz:`）を付けて登録の項目の `id` と重ならないようにし、開閉の状態も前置きつきで持つ。覚えた開閉の状態に無い `id` は無視する。画面の道 `/tables/{スキーマ名}/{テーブル名}` は要求の検査に当たる（Q3）。同じテーブルを指す項目が複数あるときの今の項目（Q3）。`label` が空のときの既定の表示。空の理由の文言（NOT_CONFIGURED は「業務のメニューが設定されていません」、NOTHING_VISIBLE は「表示できる業務のメニューがありません」）。古いメニューから移って置き場が 403 なら業務のメニューを読み直す（AC5.1.11）。
- role-admin-ui（`aidlc/spaces/default/intents/261004-role-menu/construction/role-admin-ui/functional-design/functional-spec.md` の 7節・8.3・11節）: B8 で `frontend/src/main.tsx` を `createBrowserRouter`＋`RouterProvider` に替え（`App` を `path: '*'` で包む）、S4 は `useBlocker` で道の移動を止める。使えなかったときは `BrowserRouter` のまま（7.3）。管理の詳細の道（`/admin/roles/:roleId` など）とサイドバーの項目（`/admin/roles`）の今の項目の決め方は U7。F の流れの E2E は B8 で前半を書き、B9 で同じファイルに画面での作業ロールの切り替えとサイドバーの変化を足す（本数は1本のまま、U6 の Q6 A）。骨組みのログアウトでは S4 の未保存の確かめが出ないことがある（Q6）。管理の項目は「ロール」240・「グループ」250・「権限の受け渡し」260 で、アイコンは付けない。

### テストの決まり（`team.md`・`project.md`）

- N 階層のメニューの画面: 開閉（`aria-expanded`）と今の項目（`aria-current`）、キーボードだけでたどれること、深さの上限（5 段。上限の拒否は U2 が確かめ、U7 は 5 段ちょうどが欠けずに描かれることを確かめる）、表示の木を作る純粋な関数に fast-check、信頼できない入力（`label` の `<`・`>`・`&`・`"`・`'` が文字として出る、`icon` の照合、`table` の名前から作る道のエンコードと決めた形から外れないこと）、make-you-chic-ui の部品に頼る振る舞いもこのリポジトリの画面のテストと実際のブラウザの axe（展開した状態・深い階層・長い名前・ブランドカラーとテーマのすべての組）で確かめる。
- 役割・権限の画面の出し分け: メニュー・画面を隠すことをサーバー側の検査の代わりにしない。権限の無い画面へ直接移ると権限なしの表示（AC5.1.4）。
- 画面のテストは対象と同じ場所の `*.test.tsx` で、Vitest＋Testing Library＋user-event＋vitest-axe、部品ごとに axe を1件。描画の後に反映される値は `waitFor` で待ち、時間の上限は原因を確かめずに延ばさない。説明文は英語、メールアドレスは `example.com` だけ。
- 実際のブラウザの axe は、誤りを出した状態・現実に近いデータ（5 段の深さ・40 文字の名前・2語の氏名）で、`frontend/e2e/support/displayCombos.ts` の 20 組すべてについて行い、開いた Dropdown・メニューが画面の中に収まることも確かめる。この検査は E2E の本数に数えない。E2E で API の答えを差し替えるときは見本を1つにして画面の側の型を付け、本物の応答と一致することを流れの E2E で確かめる（`project.md` の学び）。
- E2E の流れは Intent 261004-role-menu で最大2本（F と I）。役割・権限を変える操作は、その流れで自分で作った利用者とロールだけを対象にし、初期管理者は変えない。新しく足す流れは前のテストが作った状態に頼らない。E2E の番号は U6 と合わせてコード生成の計画で決める（`project.md` の学び）。

### コードと make-you-chic-ui で確かめた事実

- 今の骨組み: `frontend/src/main.tsx` は `BrowserRouter`（B8 で data router に替わる予定）。`ShellLayout`（`frontend/src/app/layout/ShellLayout.tsx`）は `buildSidebarEntries`（`app/navigation/navigationItems.ts`）の平らな一覧を `AppShell` の `navItems` に渡す。先頭は常に `HOME_ENTRY`（`/`、文言の鍵 `nav.home`、アイコン `home`）で、ほかの項目のアイコンは `list` の固定。ホームの画面（`app/pages/HomePage.tsx`）は見出しと説明だけ。ログイン状態（`LoginState`）は `loggedIn`・`admin`・`displayName`・`preferences` だけで、作業ロールは持たない。ログイン状態の提供元（`features/auth/authSession.ts`）はログイン・トークンの更新・ログアウトのたびに知らせを出す。アクセストークンは既定 5 分で、更新は要求の 401 のときに行う。
- 画面の振り分け（`app/routing/decideRoute.ts`）は道（pathname）だけを `matchPath` で照らす。問い合わせの引数は振り分けに使わない。機能の画面は `app/` の口（`useAdminForbidden`・`useDisplaySettings`・`useMessages` など）を import してよい（今の機能が使っている）。機能どうしの import は U1 の ESLint で止まる。
- サーバーの画面の配信（`backend/src/main/java/cherry/mastersmith/config/WebConfig.java`）は、`/api`・`/actuator` 以外で静的なファイルが無い道に `index.html` を返す。要求の検査（Spring Security）は道の中の `%2F`・`%25`・`..`・`;` などを 400 `REQUEST_REJECTED` にする（`access/web/AccessRequestRejectedHandler.java`）。問い合わせの引数は道の検査に当たらない（navigation のテストの観点で確かめる）。
- 固定先 e82b651 の `AppShell` は `navItems`（必須、平ら）・`user`・`userMenuItems`・`topbarStart`・`topbarEnd`（ユーザーメニューの前、右寄せ）を持つ。`Sidebar` は `aria-label="メインナビゲーション"` の固定、入れ子・見出し・`aria-current` は無い。`Dropdown` の項目は `label`・`href`・`onClick`・`disabled`・`description` だけで、区切り線・まとまりは無い。トップバーの畳むボタンの名前「サイドバーを開く」「サイドバーを折り畳む」は日本語の固定。AppShell の CSS に画面の幅による切り替え（`@media`）は無く、サイドバーは畳む（アイコンだけ）か開くかの2つ。
- 上流の make-you-chic-ui の 5bf1ffe（`main` に push 済み、`/Users/agawa/Documents/project/git/make-you-chic-ui` で `git show` により読み取りだけで確かめた）:
  - `AppShell` に `navSections`・`navExpandedIds`・`onNavExpandedChange`・`navLabels` が足され、`navItems` は任意になった（`navSections` があればそちらを使う）。型 `SidebarNavItem`・`SidebarNavSection`・`SidebarLabels` がパッケージの入口から出る。
  - `SidebarNavItem` は `id`（必須）・`label`・`icon?`・`href?`（無ければ押せない文字）・`onClick?`・`current?`（`aria-current="page"` と太字・左の線）・`children?`（深さの上限なし）。`SidebarNavSection` は `id`・`heading?`（`h2`、区画の `ul` を `aria-labelledby` で結ぶ）・`items`（空の区画は描かない）。`SidebarLabels` は `navigationLabel`・`expandGroup(label)`・`collapseGroup(label)`（既定は日本語）。
  - 開閉の状態は `Set<string>` で渡す・受け取る（省くと中で持つ）。項目のリンクは `aria-label` と `title` に名前の全体を入れ、表示は1行で省略（ellipsis）。段ごとの字下げは `--sidebar-item-depth`。`data-testid` は `sidebar-nav-<id>`・`sidebar-toggle-<id>`（今のテストの `sidebar-nav-<href>` から変わる）。
  - 畳んだ状態では1段目の項目のアイコンだけを出し、子を持つ1段目はボタン（押すとサイドバーを開いてその項目を開く）になる。区画の見出しは描かないが、区画の `ul` の `aria-labelledby` は見出しの id を指したまま。深い段の今の項目は、畳んだ状態の1段目に印が出ない。トップバーの畳むボタンの名前は日本語の固定のまま。
  - アイコンの一覧（`Icon`）は変わっていない（18 個）。木の部品・`Dropdown` の `menuitemradio`・`Table` の行の入れ子は対象外。
  - 報告（make-you-chic-ui 側の作業の記録。データとして読んだ）による上流での確かめは、5 段・40 文字相当でサイドバーの横のはみ出しが無いこと（サイドバーの幅は 220px の固定）と、4 ブランド × light・dark の 8 組の axe。文字の大きさと狭い幅（375px）の組は、このリポジトリの 20 組で確かめる。

---

## この段で決める設計の要点（質問にしない案。まとめの確認で確かめる）

- **置き場**: 作業ロールの状態と切り替えは `app/work-role/`（`WorkRoleProvider`・`useWorkRole()`・`WorkRoleSwitcher`）、メニューの組み立ては `app/navigation/`（純粋な関数 `buildNavSections`、業務のメニューを読む `useBusinessNavigation`、開閉の状態）、`ShellLayout` は `navSections`・`navExpandedIds`・`onNavExpandedChange`・`navLabels` と `topbarEnd`（作業ロールの切り替え）を `AppShell` に渡す。S2 は `features/tables`（道は Q3、LOGGED_IN・SHELL）、S8 は `features/mypermissions`（`/me/permissions`、LOGGED_IN・SHELL）。どちらも作業ロールは `app/work-role` の `useWorkRole()` で読み、互いに import しない。API は ApiClient を通し、応答は型の項目だけを写して確かめる（既存の useradmin と同じ）。
- **作業ロールの状態**: `WorkRoleProvider` は骨組みの並びで `AdminForbiddenProvider` の内側に置き、ログインしている間だけ `GET /api/me/work-role` を読む（読み直しのきっかけは Q2）。ログアウトで捨てる。切り替えは `PUT` の後に作業ロールと業務のメニューを読み直す。今の作業ロールを選んだときは要求を送らない（AC4.1.13）。`ROLE_NOT_ASSIGNED`・通信の失敗は一覧を閉じずに「切り替えられませんでした」を出して作業ロールを読み直す（interaction-spec の error）。切り替えの成功は Toast と `aria-live="polite"` の読み上げ「作業ロールを経理に切り替えました」で伝え、フォーカスは切り替えのボタンに戻す（AC4.1.8）。
- **切り替えの部品**: `topbarEnd` に置き、`Dropdown`（`placement: 'bottom-end'`）の項目は自分のロールを `roles` の順に並べ、今のロールの名前に「（使用中）」を足す。S8 があるときは一覧の最後に「自分の権限を見る」（`/me/permissions` へのリンク）を足す（区切り線の部品が無いため、普通の項目として最後に置く）。ロールが1つなら「作業ロール: 営業」を文字で出し、Tooltip と説明（`aria-describedby`）で切り替える先が無いことを示す（AC4.1.9）。ロールが無ければ「作業ロール: なし」のボタンで、押すと押せない項目（`disabled` と `description` で「割り当てられたロールがありません。管理者に依頼してください。」）と「自分の権限を見る」を出す（AC4.1.4）。768px 未満では見える文字を「営業 ▼」に縮め、読み上げの名前は「作業ロール: 営業」のまま（10a 節）。S8 への入口は、あわせてユーザーメニューにも「自分の権限」として登録する（ロールが1つでも入れる）。
- **サイドバーの組み立て**: `buildNavSections` は、ログイン状態・登録・業務のメニューの応答・今の道・表示の言語から区画の配列を作る純粋な関数にする。業務の区画（見出し「業務」）は `NavNode` の木を写し、`id` に `biz:` を前置き、`href` は Q3 の形で作り、`navigable` が false の項目は `href` を持たない。管理の区画（見出し「管理」）は登録の `section: 'ADMIN'` の項目を `order` の順に並べ、管理者だけに出し、`id` に `adm:` を前置く。アイコンは業務・管理とも `app/registry` の一覧で照らし、無い・知らない名前は `list`。ホームの置き場と空のときは Q1。
- **表示名**: 表示の言語の値をそのまま文字として出す。空の文字列のときは、もう片方の言語の値、それも空ならテーブルを指す項目はテーブル名、まとまりは「（名前なし）」「(Untitled)」にする。React の文字として描き、HTML として描く書き方（`react/no-danger` の対象）は使わない。
- **今の項目**: サイドバー全体で `current` は多くて1つ。ホームは道 `/` のときだけ。管理の項目は道の区切り（`/`）での前方一致（`/admin/roles` は `/admin/roles` と `/admin/roles/12/assignments` に当たる）で、当たる項目が複数あれば最も長い道の項目にする。業務の項目は Q3。
- **開閉の状態**: `navExpandedIds` を骨組みが持ち、ブラウザの `sessionStorage`（タブの間だけ。読み書きの失敗は無視）に覚え、ログアウトで捨てる。今の木に無い `id` は使わずに捨てる。道が変わったとき（直接開いた・再読み込みを含む）は、今の項目の祖先を開いた状態に足す（ほかの開閉は変えない）（AC5.1.8・AC5.1.9）。作業ロールを切り替えても開閉の状態は保つ（同じ DSL の間は `id` が変わらない）。
- **業務のメニューの読み込み**: 読み直しの間は今の木を出したままにし、遅れて届いた古い結果は捨てる（要求の世代で比べる）。読み込みの失敗は、業務の区画に押せない項目「業務のメニューを読み込めませんでした」を出し、次のきっかけ（Q2）で読み直す。［もう一度］のボタンはホームの画面に出す（サイドバーの区画には文字とボタンを自由に置けないため。interaction-spec の NavTree の error との差として記録する）。
- **ホームの画面**: 業務のメニューが空のときに、`emptyReason` ごとの案内（NOT_CONFIGURED は「業務のメニューが設定されていません」、NOTHING_VISIBLE は「表示できる業務のメニューがありません。作業ロールに見てよいテーブルが無いか、ロールが割り当てられていません。」）と、S8 があるときは［自分の権限を見る］を出す（AC4.2.7・AC5.1.6・AC5.3.3）。作業ロールが無いときは「ロールが割り当てられていません。管理者に依頼してください。」を出す（AC4.1.4）。
- **S2 テーブルの置き場**: 開いたときと作業ロールを切り替えたときに置き場の問い合わせを送る。200 は見出しに表示名と「この画面は準備中です」、403・400・引数が無いは同じ「この画面を表示する権限がありません」と今の作業ロールの名前、［作業ロールを切り替える］（トップバーの切り替えのボタンへフォーカスを移す。ロールが2つ以上のときだけ出す）・［ホームへ］（AC5.1.3・AC5.1.4・AC4.1.10）。403 のときは業務のメニューを読み直す（AC5.1.11）。表示が変わったら見出しへフォーカスを移さず、`aria-live` で伝える。
- **S8 自分の権限（Should）**: S4 と同じ形で、左は `shared/tree`（スキーマ→テーブル、テーブルは子なし）、右は選んだ対象の実効の主権限と CREATE・DELETE、テーブルを選んだときはカラムの表。値は「READ（見られる・直せない）」のように文字で意味を添え、業務のメニューに出るかを `inMenu` から文字で出す（AC4.2.1・AC4.2.6）。節の id は名前を JSON の配列にした文字列（U6 と同じ）。作業ロールが無いときは案内（AC4.2.4）。作業ロールを切り替えたら読み直す（AC4.2.2）。768px 未満は縦に積む（10a 節）。
- **画面の言語**: サイドバーの `navLabels`（「メインメニュー」「Main menu」、「〇〇を開く」「Expand 〇〇」）、見出し（「業務」「管理」、「Business」「Administration」）、作業ロールの文言は日本語と英語。ロール・テーブル・DSL の表示名は訳さない。
- **固定先の更新**: B9 の最初に `vendor/make-you-chic-ui` を e82b651 から 5bf1ffe（または Q4 の答えの版）へ上げる専用のコミットを作り、前後のハッシュを記録する。サイドバーの `data-testid` が `sidebar-nav-<id>` に変わるため、`ShellLayout.test.tsx`・`AppRouter.test.tsx` の既存のテストを書き換える。E2E はリンクの名前で探しているため変わらない見込み（コード生成で確かめる）。アイコンの一覧は変わらない。
- **テスト**: `buildNavSections` に fast-check（任意の `NavNode` の木・登録・道で、(a) 業務の項目の親子と並びが応答と同じ（前置きを外すと `id` が一致）(b) `href` はすべて Q3 の決めた形で、読み戻すと同じスキーマ名とテーブル名になり、外部の URL・`javascript:` にならない (c) アイコンはすべて一覧の名前 (d) `current` は多くて1つ (e) 子の無いまとまりと移れない項目が無い（応答が崩れていても画面で落とす） (f) 管理の区画は管理者のときだけ）。部品のテストは開閉・`aria-current`・キーボードだけで 5 段をたどる・英語の表示の名前・空の見出しを出さない・`label` の `<script>` と `&` が文字で出る・作業ロールの3つの状態と切り替えの読み上げとフォーカス・置き場の3つの表示。実際のブラウザの axe（本数に数えない）は、5 段を開いて 40 文字の名前を含むサイドバー・開いた作業ロールの Dropdown・置き場の権限なし・S8 を、20 組すべてと 360px・768px・1280px で確かめる。

---

## Q1 サイドバーの「ホーム」の置き場と、業務のメニューが空のときのサイドバー

背景: 今のサイドバーは先頭に必ず「ホーム」（`HOME_ENTRY`）を置きますが、画面の段の図（S1）には無く、置き場は U7 に引き継がれています（cross-cutting の 3.3）。また S1 の 1.1a は「業務のメニューも空のときは、見出しを1つも出さず『表示できるメニューはありません』とだけ出す」としていますが、5bf1ffe の `navSections` は項目（リンクか押せない文字）しか描けず、文だけの区画は置けません。

A. 見出しの無い先頭の区画に「ホーム」（アイコン `home`）を置き、続けて「業務」「管理」の区画を置く。業務のメニューが空のとき（NOT_CONFIGURED・NOTHING_VISIBLE）は業務の区画を出さず、サイドバーには案内を置かない。案内はホームの画面（S1 の 1.3）だけに出す。1.1a の文を出さないことを差として記録する（推奨: 今の使い方とテスト・E2E（ホームへ戻るリンク）をそのまま保て、ロールの無い利用者も必ずホームへ戻れる。案内はホームの画面のほうが文とボタンを置けて伝わる）
B. A と同じくホームを先頭の見出しの無い区画に置き、業務のメニューが空のときは、その区画に押せない項目「表示できるメニューはありません」を足す（1.1a に近づける。項目の形で文を出すため、読み上げではメニューの項目の1つとして読まれる）
C. ホームをサイドバーから外し、トップバーの左（`topbarStart`）に「MasterSmith」のホームへのリンクを置く（S1 の図どおり）。業務のメニューが空のときは B と同じ押せない項目を出す
X. Other (please specify)

[Answer]: A **Mode:** guided

## Q2 作業ロールと業務のメニューを読み直すきっかけ

背景: 作業ロール（`GET /api/me/work-role`）と業務のメニュー（`GET /api/me/navigation`）は、管理者の操作（割り当ての外し・ロールの削除・権限の変更）でサーバー側の結果が変わります。ストーリーは、本人への案内は出さず「作業ロールの表示が変わるだけ」（SM8 B）とし、古いメニューから移ったときは置き場の 403 で読み直す（AC5.1.11）としていますが、いつ表示が追いつくかは決めていません。アクセストークンは既定 5 分で、切れた後の要求の 401 で更新され、そのときログイン状態の知らせが出ます。

A. ログイン状態の知らせ（ログイン・トークンの更新・ログアウト）、作業ロールの切り替えの後、置き場の 403 と `ROLE_NOT_ASSIGNED` の後に加え、画面の道が変わるたびに2つを裏で読み直す。読み直しの間は今の表示を残し、続けて移ったときは前の結果を捨てる（推奨: 管理者の変更が、利用者の次の画面の移動でトップバーとサイドバーに出る（「次の要求から」の考え方にそろう）。要求は移るたびに2本増えるが、どちらも NFR2.2 の 1 秒の目標の軽い API で、利用者は 50 名程度）
B. 画面の移動では読まず、ログイン状態の知らせ・切り替えの後・置き場の 403 と `ROLE_NOT_ASSIGNED` の後だけ読み直す。管理者の変更は、遅くとも次のトークンの更新（最大で約 5 分と次の要求の後）か、古いメニューから移ったときの 403 で追いつく
C. B に加え、ブラウザのタブが前に戻ったとき（`visibilitychange`）にも読み直す
X. Other (please specify)

[Answer]: A **Mode:** guided

## Q3 テーブルの置き場の画面の道と、同じテーブルを指す項目が複数あるときの今の項目

背景: 画面の段の道 `/tables/{スキーマ名}/{テーブル名}`（名前をエンコード）は、名前に `/`・`%`・`..`・`;` があると、エンコードした `%2F`・`%25` などが要求の検査に当たり、直接開く・再読み込みすると画面を返す前に 400 になります（AC5.1.8・AC5.1.13 に当たる。navigation の 9節）。また DSL は同じテーブルを複数の項目が指すことを禁じておらず、今の道に当たる項目が複数あるとき、どれを今の項目（`aria-current`）にし、どの祖先を開くかが決まっていません。`aria-current="page"` は1つの項目にだけ付けるのが望ましい形です。

A. 道は `/tables?schema=…&table=…`（`URLSearchParams` でエンコード）にし、押した項目の位置の道を引数 `item=…` に足す。今の項目は、`item` がその組を指す項目に当たればその項目、無い・当たらない（ブックマーク・DSL の適用し直し）ときは DSL の順で最初の1つにし、その祖先を開く（推奨: 道の形が `/tables` に固定され、名前に何が入っても要求の検査に当たらず道から外れない。押した項目がそのまま今の項目になり、別の枝が開く戸惑いが無い）
B. 道は A と同じ `/tables?schema=…&table=…` で `item` を持たず、今の項目は常に DSL の順で最初の1つにする（道は短いが、2つ目の項目から移ると最初の項目とその枝が今の項目になる）
C. 道は `/tables?schema=…&table=…` で、組を指すすべての項目に `aria-current` を付け、それぞれの祖先を開く
D. 画面の段の道 `/tables/{スキーマ名}/{テーブル名}` のままにし、要求の検査に当たる名前のテーブルは直接開く・再読み込みすると 400 になることを既知の制約として記録する（今の項目は A と同じく最初の1つ）
X. Other (please specify)

[Answer]: A **Mode:** guided

## Q4 5bf1ffe で見つかった足りない点の扱い

背景: 5bf1ffe のソースを確かめたところ、依頼の必須（入れ子・`aria-current`・見出し・開閉の状態と文言の口・長い名前・畳んだ状態）はそろっていますが、次の点が残っています。(1) 畳んだ状態では見出しを描かないのに、区画の `ul` の `aria-labelledby` が無い見出しの id を指したままで、axe の違反になりうる（未検証）。(2) 畳んだ状態では1段目だけを出すため、深い段の今の項目がどの枝にあるかの印が無い。(3) トップバーの畳むボタンの名前「サイドバーを開く」「サイドバーを折り畳む」が日本語の固定で、英語の表示でも日本語のまま（AC5.1.12 の対象外だが同じ種類）。`vendor/make-you-chic-ui` はこのリポジトリから変えられず、直すには上流への依頼が要ります（`team.md`）。

A. (1)〜(3) を上流へ追加で依頼する。B9 までに取り込まれればその版を固定先にし、間に合わなければ 5bf1ffe で進め、残る点を差として記録して承認の場で伝える。(1) が axe の違反になったときは、その時点で依頼者に諮る（推奨: 畳んだ状態もすべての組の axe の対象で、英語の表示の名前も今回の決まり（NFR4.3）の範囲に近い。待たずに進められる）
B. (1) だけを依頼し、(2)・(3) は今回の範囲の外として差に記録する
C. 依頼せず 5bf1ffe に固定する。B9 の axe・画面のテストで実際に違反が出たときに諮る
X. Other (please specify)

[Answer]: A **Mode:** guided

## Q5 I の流れの E2E（深い階層をたどって準備中の画面へ）の前提の作り方

背景: I の流れ（NFR6.3）は、版 2 の DSL の深い `menus`、テーブルの権限を持つロール、そのロールを割り当てた利用者が要ります。E2E は1つのアプリと内部DB を全ファイルで共有して順に流すため、DSL の適用は後のファイルにも残ります（既存の 040 と B8 の F の流れも自分で DSL を適用する）。前のテストの状態に頼らず、初期管理者は変えない決まりです。F の流れ（B8 で前半、B9 で切り替えとサイドバーの変化を足す）とは別の1本にします。

A. 前提は API で作る（管理者のトークンで、5 段の項目と 40 文字の名前を含む版 2 の DSL を投入・適用し、ロールを作って末端のテーブルを READ・別のテーブルを NONE にし、招待から登録まで済ませた利用者（`support/registeredUser.ts`）に割り当てる）。画面では、その利用者でログイン → サイドバーで 5 段を開いて末端のテーブルを選ぶ → 準備中の画面と表示名・`aria-current`・祖先が開いていることを確かめる → 再読み込みしても同じ → NONE のテーブルの道を直接開くと権限なしの表示で表示名が出ない、までを確かめる。応答の見本の型と本物の応答の一致も確かめる（推奨: 画面の操作は I の流れの中身（たどる・移る・直接開く）に絞れ、管理の画面の操作は F の流れと重ならない）
B. 前提も画面で作る（DSL の管理の画面で投入・適用、ロールの画面で権限の設定と割り当て）。流れが長くなり、F の流れと同じ操作を重ねる
C. I の流れは別のファイルにせず、F の流れのファイルに深い階層をたどって準備中の画面へ移る手順を足して1本にまとめる（流れの本数は1本になる）
X. Other (please specify)

[Answer]: A **Mode:** guided

## Q6 ユーザーメニューのログアウトと、S4 の保存していない変更

背景: U6 は S4（権限の設定）の保存していない変更を、data router の `useBlocker` で道の移動のときに確かめます。骨組みのユーザーメニューのログアウトは、道を変える前にトークンを捨てる操作（`features/auth` の登録の action）のため、確かめが出ずに変更が失われることがあり、U6 は「気にするなら U7 で決める」と引き継いでいます（role-admin-ui の 7.2・11節）。

A. 変えない。ログアウトは確かめを経ずに行い、保存していない変更が失われうることを U6 の差（9節の (b)）のまま記録する（今のログアウトの流れと `features/auth` を変えない）
B. ログアウトを道の移動にする（`/logout` の画面を足し、移った先でトークンを捨ててログインの画面へ移る。ユーザーメニューの項目は action から path に替える）。道の移動のため U6 の `useBlocker` がそのまま止める。data router に替えられなかったとき（U6 の 7.3）は A と同じ（推奨: S4 のコードに手を入れずにサイドバーの移動と同じ確かめになる。変わるのは `features/auth` の登録とログアウトの画面1つ）
C. 骨組みに「保存していない変更があるか」を画面が知らせる口を足し、ユーザーメニューの action の項目を選んだときに骨組みが同じ文言の確かめを出す（S4 に知らせる処理を足す。U6 の画面に手が入る）
X. Other (please specify)

[Answer]: B **Mode:** guided

## Consolidated Summary Confirmation

答えのまとめ（app-frame-ui）:

- Q1 A: 見出しの無い先頭の区画に「ホーム」を置き、続けて「業務」「管理」の区画。業務のメニューが空のときは業務の区画を出さず、案内はホームの画面だけに出す（画面イメージ 1.1a の文を出さないことを差として記録）。
- Q2 A: 作業ロールと業務のメニューは、ログイン状態の知らせ・切り替えの後・置き場の 403 と `ROLE_NOT_ASSIGNED` の後に加え、画面の道が変わるたびに裏で読み直す（読み直しの間は今の表示を残し、古い結果は捨てる）。
- Q3 A: テーブルの置き場の画面の道は `/tables?schema=…&table=…&item=<位置の道>`。今の項目は `item` が当たればその項目、当たらなければ DSL の順で最初の1つにし、祖先を開く（画面の段の道との差を記録）。
- Q4 A: make-you-chic-ui 5bf1ffe に残る3点（畳んだ状態の `aria-labelledby`、畳んだ状態の深い段の今の項目の印、畳むボタンの名前の日本語固定）を上流へ追加で依頼する。B9 までに入ればその版を固定先にし、間に合わなければ 5bf1ffe で進めて差を記録する。axe の違反になったときはその時点で依頼者に諮る。
- Q5 A: I の流れの E2E の前提（版 2 の深い DSL・ロール・割り当て・利用者）は API で作り、画面では 5 段をたどって準備中の画面へ移る・再読み込み・NONE のテーブルを直接開く、を確かめる。
- Q6 B: ログアウトを道の移動（`/logout` の画面）にし、S4 の `useBlocker` がそのまま確かめを出す。`features/auth` の登録とログアウトの画面に手が入る（U7 の持ち物の外として差に記録）。data router に替えられなかったときは確かめを経ずにログアウトする。
- 「この段で決める設計の要点」（作業ロールとメニューの置き場、id の前置き `biz:`・`adm:`、今の項目と開閉の状態の持ち方、label が空のときの表示、アイコンの照らし直し、作業ロールの切り替えの Dropdown、読み込みの失敗の示し方、`buildNavSections` の fast-check、axe と幅、固定先の更新の専用のコミットと既存のテストの書き換え）もこのまま設計に入れる。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
