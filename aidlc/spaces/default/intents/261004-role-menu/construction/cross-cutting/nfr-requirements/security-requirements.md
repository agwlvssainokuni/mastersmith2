# セキュリティの要件（Security Requirements）— U1 cross-cutting

## 出典

- この単位の機能設計: `construction/cross-cutting/functional-design/functional-spec.md`（W1〜W7、3節の 34 の口、10節の承認の場の直し R-01・R-02）、`rules.md`（BR1.1〜BR5.6）
- 要件: `requirements.md`（NFR1 セキュリティ・NFR4 アクセシビリティと画面・NFR6 テストと品質、制約 C4・C5）
- 契約: `contract-summary.md`（C1 API の分類の注釈、C2 画面の登録の型と共有の木）
- 技術の土台: コードの知識ベース `technology-stack.md`（Spring Boot 4.1.1、ArchUnit 1.5.1、Vitest・vitest-axe・axe-core）
- ADR-008（`decisions.md`）、この段の答え `nfr-requirements-questions.md`（Q1: B、まとめの確認 Looks correct）、`team.md`（Code Style・Testing Posture）、`project.md`（学び）

## ID の寄せ方

- 要件の枝番は、この Intent の要件の NFR の番号の意味で寄せ、単位の中で .1 から振る（`project.md` の学びを、この Intent の NFR の並び NFR1 セキュリティ〜NFR6 テスト・品質に読み替えた）。
  - NFR1（セキュリティ）: API の分類と検査、PUBLIC の一覧、画面の出し分け、共有の木の描き方。
  - NFR4（アクセシビリティ・画面）: 共有の木の a11y と文言。
  - NFR6（テスト・品質）: ESLint の制限、境界テスト、カバレッジ、テストの決まり、依存を足さないこと、検査の時間。
- 制約 C5（画面の機能どうしの import の制限）と C4（境界テスト）は NFR の番号を持たないため、NFR6 の枝番（NFR6.1・NFR6.3）に寄せた。
- NFR2（性能）・NFR3（信頼性・同時性）・NFR5（監査・観測）は U1 に当たらない。U1 は API・データ・監査の出来事・指標を持たず、注釈・構造の検査・画面の型と部品・ESLint の設定だけを持つため（`traceability.json` に N/A と理由を書く）。
- ここで振る `NFRx.y` は単位の中の ID で、要件の同じ番号（例: 要件の NFR1.3）とは別のもの。要件の各項目との対応は各行の「出典」の列に書く。

## 1. 脅威（STRIDE）

| 種類 | U1 で考える脅威 | 守り |
|---|---|---|
| Spoofing（なりすまし） | — （U1 は認証を持たない。トークンの検証は auth） | 当たらない |
| Tampering（改ざん） | 画面の登録や木の表示名に、HTML・スクリプトが入る | NFR1.9 |
| Repudiation（否認） | — （U1 は監査の対象の操作を持たない） | 当たらない |
| Information disclosure（漏えい） | 検査の失敗の文・木の読み込みの失敗の文に、秘密や内部の文が出る | NFR1.9・NFR1.10 |
| Denial of service（妨げ） | — （U1 は要求を受ける口を持たない） | 当たらない |
| Elevation of privilege（昇格） | 口の分類の書き忘れ・書き間違いで、ログインなし・管理者でない利用者が届く。PUBLIC の印と `permitAll` を同じ変更で書き間違える。検査が空振りして違反を見逃す | NFR1.1〜NFR1.8 |

## 2. 要件

### 2.1 API の分類と検査（NFR1）

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR1.1 | 本番のすべてのコントローラーの口は、効く分類の印（`ApiAccess`）を1つだけ持つ。印が無い・クラスと方法の両方にある口があれば落とす | 全体の置き場の ArchUnit のテスト（`ApiAccessArchitectureTest` の形の `*Test`）。違反の見本のクラスを読む単体の確かめで、無い・二重の両方が落ちることを確かめる | 要件 NFR1.3・C1・BR1.1 |
| NFR1.2 | ADMIN の印と `AdminPaths.isAdminOnly` は両方向で一致し、AUTHENTICATED の口は `/api/` の下で管理者の道の外にある | 同じテスト。境界: `/api/admin` そのもの・`/api/admin/` の下・`/api/administrator`（管理者の道でない）・`/api/me` | C1・BR1.2・BR1.3 |
| NFR1.3 | 印と安全の決まりの判定が一致することを、要求を送らずに3つの主体で確かめる。未ログインは PUBLIC だけ通る。管理者でない利用者（`AuthenticatedUser` の `admin()` が偽）は PUBLIC と AUTHENTICATED が通り ADMIN は止まる。管理者（`admin()` が真）は3つとも通る。ログイン中の主体は本番と同じ `AuthenticatedUserToken` で作る | 全体の置き場の結合テスト（`ApiAccessConsistencyIT` の形の `*IT`、本番の設定で起動。`PublicApiTestRules` とテストだけの口は有効にしない）。34 の口×方法×3つの主体の組をすべて判定し、違いを並べて落とす | 要件 NFR1.3・BR1.4・承認の場の直し R-01 |
| NFR1.4 | 判定の部品が使えなければ、同じ組を MockMvc で実際に送る形に切り替える。安全の決まりによる拒否と口の中の業務の拒否を見分け、監査に行が残る口は副作用の無い形で送る | NFR 設計の捨ての試しで、どちらの形にするかを決めて記録する（tech-stack-decisions.md） | BR1.5 |
| NFR1.5 | 静的な検査と実行時の検査の対象は、本番のクラスの口の同じ集合とし、2つが一致することを確かめる。どちらも空振りしない。U1 の規則は対象が空なら落とし（規則ごとの `allowEmptyShould(false)`）、対象の口が 34 以上あることを確かめる | NFR1.1・NFR1.3 のテストの中で確かめる。全体の設定 `archRule.failOnEmptyShould = false` は変えない | BR1.6・R-02・まとめの確認の要点 2 |
| NFR1.6 | PUBLIC の口の増え方をテストの側の一覧でも止める。全体の置き場のテストに PUBLIC の口の一覧を持ち、印が PUBLIC の口の集まりが一覧と一致しなければ落とす。比べる単位は口（ハンドラーの方法）で、今は 9 つ（NFR1.5 の 34 と同じ単位）。1つの口は「宣言された方法の集合」と「道の型の集合」の組で表し、正規化の決まりは下の 2.2 のとおり。PUBLIC を足す・外すときは、一覧の行と理由のコメントを同じ変更で直す | NFR1.3 と同じ実行時の検査（`*IT`）の中で比べる（設定値を解決した道を読むため）。境界: 一覧に無い PUBLIC の口を足すと落ちる。一覧の口から PUBLIC を外すと落ちる。`/error` の口の宣言された方法が増える・減ると落ちる。道の型の変数名を変えると落ちる | Q1 B・ADR-008 の補足 |
| NFR1.7 | 印の対象外の入口は、承認済みの扱いのまま変えない。actuator は Web に health だけを出し、`/actuator/health` だけを誰でも届くようにする。画面の静的配信は誰でも届き、`/api/`・`/actuator/` の下は `index.html` に読み替えない | 既存の公開の範囲と配信のテストが通り続ける | BR1.7 |
| NFR1.8 | 登録の区画（`section`）と `visibleWhen` は見せ方だけを決め、管理の API はサーバー側の管理者の判定で守る | NFR1.3 の ADMIN の組（管理者でない利用者が止まる）で確かめる | 要件 NFR1.1・BR5.5・PM の Mandated |
| NFR1.9 | 共有の木は、表示名と印の文字を文字として描き、HTML として描かない。子の読み込みの失敗では `labels.loadFailed` だけを出し、例外の文や応答の中身を画面に出さない | 共有の木の画面のテスト: 表示名・印に `<`・`>`・`&`・`"`・`'`・`<script>` を含めても文字として出て要素が増えない。拒否の理由に文を入れた `loadChildren` でも、その文が画面に出ない | BR4.1・BR4.6・`team.md` の N 階層のメニュー |
| NFR1.10 | 検査の失敗の文には、口のクラス名・方法名・道・方法・主体の種類だけを出す。トークン・利用者の値は出さない（主体の利用者は検査の中で作る仮の値） | テストの見直し（コード生成のレビュー）。U1 は個人に関する値を扱わないため、`XxxSecretLeakIT`（要件 NFR1.6）は当たらない | 要件 NFR1.6・PM の Forbidden |

### 2.2 PUBLIC の口の一覧（NFR1.6 の初めの中身）

比べる単位と正規化の決まり（承認の場の直し R-01）:

- **単位**: 口（コントローラーの RequestMapping を持つ方法）1つが一覧の1行。件数は口の数で、今は 9。方法と道の組の数（今は 14）では数えない。
- **方法**: 口に宣言された方法の集合そのもので比べる。Spring が暗黙に受ける HEAD・OPTIONS は足さない。宣言が無い口は「宣言なし（すべての方法）」として比べ、方法を列挙した口とは別のものとして扱う。
- **道**: アプリが持つ口の一覧（`RequestMappingHandlerMapping` の対応づけ）の道の型をそのまま比べる。クラスと方法の道をつなぎ、設定値の置き換え（`${server.error.path:/error}` など）はアプリの設定で解決した値にする。パス変数は宣言の形（変数名を含む型、例 `/api/problems/{slug}`）のまま比べ、見本の値に置き換えない。
- 1つの口が複数の道の型を持つときは、道の型の集合で比べる（今は無い）。
- 一覧の行の理由のコメントには、口のクラス名と方法名も書き、読み手が口を見つけられるようにする（比べるのは方法の集合と道の型の集合だけ）。

| # | 口（クラス.方法） | 宣言された方法 | 道の型（解決した値） | 理由 |
|---|---|---|---|---|
| 1 | AuthController.login | POST | /api/auth/login | ログインの入口 |
| 2 | AuthController.refresh | POST | /api/auth/session/refresh | アクセストークンの更新（リフレッシュの Cookie で確かめる） |
| 3 | AuthController.logout | POST | /api/auth/session/logout | ログアウト（アクセストークンが切れていても行える） |
| 4 | AppearanceController.get | GET | /api/appearance | ログイン画面の前に見た目の設定を読む |
| 5 | ProblemTypeController.describe | GET | /api/problems/{slug} | エラー応答の `type` の説明 |
| 6 | RegistrationController.verify | POST | /api/registration/verify | 招待のリンクの確かめ（ログインの前） |
| 7 | RegistrationController.complete | POST | /api/registration/complete | 登録の完了（ログインの前） |
| 8 | ErrorPathController.error | GET・HEAD・OPTIONS | /error（`server.error.path` の解決した値。今の設定では既定の /error） | サーブレットの誤りの転送の応答（読み取りの方法） |
| 9 | ErrorPathController.errorForUpdate | POST・PUT・PATCH・DELETE | 同上 | 同上（更新の方法） |

8・9 の HEAD・OPTIONS は暗黙のものではなく、`ErrorPathController` に宣言された方法である。方法と道の組で数えると 14（1〜7 が各1、8 が3、9 が4）になるが、一覧は口の単位の 9 行で持つ。クラス名と方法名は今のコードで確かめた（名前が変わったら一覧の行の理由のコメントも直す）。

ADR-008 は「テストの側に分類の一覧を持つ」案を、すべての口について退けた（口を足すたびに別のファイルを直す必要があるため）。この一覧は PUBLIC だけに限った追加の守りで、AUTHENTICATED・ADMIN は一覧を持たない。分類の正は今までどおり口の隣の印で、ADR-008 の決定とは食い違わない。コード生成の計画にも、ADR-008 の補足として書く。

### 2.3 共有の木の a11y と文言（NFR4）

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR4.1 | 共有の木は、開閉のボタンに `aria-expanded`、選んでいる節の選ぶボタンに `aria-current="true"` を付ける。ARIA の tree の役割と矢印のキーの決まりは使わない。Tab と Enter・Space だけで深い節まで開いて選べる | 共有の木の画面のテスト（`SharedTreeView.test.tsx`）。境界: `selectedId` が読み込んだ節に無いときは、どの節にも `aria-current` が無い | 要件 NFR4.2・C2・BR4.2・BR4.3・BR4.10 |
| NFR4.2 | 開閉のボタンの読み上げの名前に、節の表示名を含める | 同じテスト（名前で `getByRole('button', …)` が引ける） | BR4.8・AC1.2.14 |
| NFR4.3 | 共有の木の部品ごとに vitest-axe の検査を1件入れる（開いた状態・読み込み中・失敗を含む）。違反 0 件 | 同じテスト | 要件 NFR4.1・`team.md` |
| NFR4.4 | 共有の木は文言を持たず、呼ぶ側が日本語と英語を `labels` で渡す | 共有の木のテストで、渡した文言だけが出ることを確かめる。日本語と英語の両方の文言は呼ぶ側（U6・U7）のテストで確かめる | 要件 NFR4.3・BR4.9 |
| NFR4.5 | 実際のブラウザの axe（表示の設定の 20 組、深い階層・長い名前）は、共有の木を使う画面の単位が持つ。S4 は U6、S8 は U7 の画面の検査に入っている。U1 は共有の木だけの e2e の検査を足さない | U6・U7 の検査の結果（Build and Test で確かめる） | 要件 NFR4.2・U6・U7 の承認済みの機能設計 |

### 2.4 ESLint の制限・テスト・品質（NFR6）

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR6.1 | 画面の機能どうしの直接の import と、`shared/` から `app/`・`features/` への import を、ESLint の `no-restricted-imports` で止める（テストのファイルは対象外）。制限を入れた状態で、本体の違反は 0 件（`useRegistration.ts` の1件を `useLogout` に直す） | `./gradlew verify` の中の ESLint が通る | C5（NFR6 に寄せた）・BR2.1〜BR2.4・BR3 |
| NFR6.2 | 制限の決まりそのものを、止めるべき道（兄弟の機能を指す `../B`・`../../features/B`、`shared` から `app`・`features`）と許す道（自分の機能の下位、`app`、`shared`、テストのファイル）の両方で確かめる | ESLint の `Linter` で設定を読むテスト（置き場はコード生成で決める）。止めるべき道が1つでも通れば落ちる | まとめの確認の要点 4 |
| NFR6.3 | 本体に手を入れる機能のうち境界テストが無い `access`・`user` に、今の依存をそのまま書いた境界テストを足す。既存の ArchUnit のテストは緩めない・消さない | `AccessBoundaryArchitectureTest`・`UserBoundaryArchitectureTest` が通る | C4（NFR6 に寄せた）・BR1.10・`team.md` |
| NFR6.4 | バックエンドで手を入れるパッケージ（`common.security` と各機能の `web` の 8 つ）は `packagesJudgedByTotal` に当たらず、パッケージごとの下限（行 80%・分岐 70%）をそのまま満たす。一覧の作業（下限を満たして外す）は U1 に付かない。除外を増やさない | `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` の実測（`project.md` の学び）。要件 NFR6.4 の `access.service` は U1 では手を入れないため、U1 の範囲では当たらない | 要件 NFR6.4・`team.md` の Testing Posture |
| NFR6.5 | 画面は全体の下限（`@vitest/coverage-v8` の `thresholds`、行 80%・分岐 70%）を満たす。`shared/tree`・`app/registry`・`app/login-state` の追加の分は、それぞれのテスト（共有の木、登録の検査の section・visibleWhen・icon、`useLogout` の3つの場合）で覆う | `./gradlew verify` の画面のカバレッジ | `team.md` の Testing Posture |
| NFR6.6 | テストの決まりに従う。説明文は英語、描画の後の値は `waitFor` で待つ、時間の上限を原因を確かめずに延ばさない。Java の単体は `*Test`、Spring を起動するものは `*IT` | コード生成のレビュー | `team.md` の Testing Posture |
| NFR6.7 | 新しい依存は足さない（tech-stack-decisions.md）。足す必要が出たら、採用の前にライセンスと推移依存を確かめ、依頼者に諮る | lockfile（`backend/gradle.lockfile`・`frontend/package-lock.json`）に U1 の差が無い | `team.md` の Code Style・`project.md` の学び |
| NFR6.8 | 検査の時間に目標の数値は置かない。実行時の検査は既存の結合テストと同じアプリの起動の文脈を使い回せる形にし（新しい起動の形を増やさない）、Build and Test で `verify` の時間の増え方を実測して記録する | Build and Test の記録 | まとめの確認の要点 3 |

## 3. 要件との対応

| 要件 | U1 での扱い |
|---|---|
| NFR1.1 | NFR1.8（画面の出し分けは見せ方だけ） |
| NFR1.2 | 各単位（U2〜U5）が足す API ごとに持つ。U1 は NFR1.3 で分類と判定の一致だけを確かめる |
| NFR1.3 | NFR1.1〜NFR1.7（網羅の検査と PUBLIC の一覧） |
| NFR1.4・NFR1.5 | U4（U1 に当たらない） |
| NFR1.6 | U1 は個人に関する値を扱わない（NFR1.10） |
| NFR4.1〜NFR4.3 | NFR4.1〜NFR4.5（共有の木） |
| NFR6.1 | 共有の木の部分だけ NFR4.1〜NFR4.3。役割・権限の必須のテストは U3〜U7 |
| NFR6.2・NFR6.3 | U4・U5・U6・U7（U1 に当たらない） |
| NFR6.4 | NFR6.4（U1 の範囲。`access.service` は U1 では当たらない） |
| C4・C5 | NFR6.3・NFR6.1・NFR6.2 |

## 4. 承認の場の決定と直し

- 日付: 2026-10-05
- 決定: 「Major 11 件だけを直す」（Request Changes。この単位で当たるのは R-01 の1件。Minor の R-02〜は直していない）
- 直した指摘:
  - **R-01（Major）**: PUBLIC の口の一覧（NFR1.6）の比べる単位と道の正規化が決まっておらず、件数が 9 か 14 か一意でなかった。直した中身は次のとおり（NFR1.6 の行、2.2 の決まりと表）。
    - 単位を口（ハンドラーの方法）に決め、件数を 9 にそろえた（方法と道の組では 14）。
    - 1つの口を「宣言された方法の集合」と「道の型の集合」の組で比べる。暗黙の HEAD・OPTIONS は足さない。設定値の置き換え（`server.error.path`）は解決した値で比べる。パス変数は宣言の形（変数名を含む型）のまま比べる。
    - 境界に「`/error` の宣言された方法の増減で落ちる」「変数名を変えると落ちる」を足した。
    - 2.2 の表を、口のクラス名・方法名つきの9行に書き直した。
    - 比べる場所を、設定値を解決した道を読める実行時の検査（`*IT`）にした（tech-stack-decisions.md 4節も合わせて直した）。
- traceability.json は変えていない（要件の NFR と枝番の対応は変わらない）。
