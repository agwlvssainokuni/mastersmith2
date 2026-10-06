# NFR 設計の質問 — U1 cross-cutting

対象の単位: U1 cross-cutting（kind: library）。作る成果物は `security-design.md`・`logical-components.md`・`traceability.json` の3つです（段の定義の produces_kinds）。

質問は 1 問です。ほかの点は、承認済みの NFR 要件（`nfr-requirements/security-requirements.md`・`tech-stack-decisions.md`）、機能設計（`functional-spec.md`・`rules.md`）、契約 C1・C2、`team.md`・`project.md` で決まっています。そのため下の「決まっていること」と「設計の要点」に書き、まとめの確認で確かめます。NFR 要件の読み直しで直していない Minor・Suggestion（R-02〜R-07）は、この段で手当てできる形にして「設計の要点」に入れました。

---

## 決まっていること

- **守りの形**: 多層で守る。1層目は口の隣の印（`ApiAccess`）、2層目は静的な構造の検査（印の有無・1つだけ・ADMIN と `AdminPaths` の両方向・AUTHENTICATED の道）、3層目は実行時の検査（3つの主体の判定と PUBLIC の口の一覧）、4層目は今までどおりの Spring Security の決まり（本番の判定の正）。U1 は本番の判定の仕組み（`SecurityConfig`・各機能の `SecurityRuleContributor`・`AdminAuthorizationManager`）を変えない（要件 NFR1.1〜NFR1.8、機能設計 BR1.1〜BR1.8）。
- **置き場**: 静的な検査と実行時の検査は、ADR-008 のとおり既存の `ArchitectureTest` と同じ全体の置き場（`backend/src/test/java/cherry/mastersmith/` の直下）に置く。静的な検査は `*Test`、実行時の検査は `*IT`。PUBLIC の口の一覧（口の単位で 9 行、宣言された方法の集合と道の型の集合で比べる）は、実行時の検査の中の定数にする（要件 NFR1.6・2.2、tech-stack 4節）。
- **主体**: 実行時の検査の主体は、未ログイン、管理者でない利用者、管理者の3つ。ログイン中の2つは、本番と同じ `AuthenticatedUserToken` に `AuthenticatedUser`（`admin()` が偽か真）を入れて作る（要件 NFR1.3、機能設計の承認の場の直し R-01）。主体を作るテストの手伝いは `src/test/java` の `common/testsupport` に置く（`team.md` の「テストの手伝いは `<機能>/testsupport`」。全体の検査は機能に属さないため、既存の `common/testsupport` を使う）。
- **起動の文脈**: 実行時の検査は、本番の設定で起動する既存の結合テストと同じ文脈を使い回せる形にする。テスト用の決まり（`PublicApiTestRules`）とテストだけの口を有効にする設定は入れない（要件 NFR1.3・NFR6.8）。
- **空振りの守り**: U1 の規則だけ `allowEmptyShould(false)` にし、全体の設定 `archRule.failOnEmptyShould = false` は変えない。静的な検査と実行時の検査の口の集合が一致し、34 以上あることを確かめる（要件 NFR1.5、tech-stack 3節）。
- **新しい依存**: 足さない（tech-stack 1節）。
- **個人に関する値**: U1 は個人に関する値・秘密を扱わない。`TraceAspect` の対象の層（web・service・domain・repository）に、U1 が足すのは注釈だけ（実行の中身を持たない）。検査の失敗の文には、口のクラス名・方法名・道・方法・主体の種類だけを出す（要件 NFR1.10）。
- **捨ての試し（`project.md` の学び「部品の振る舞いに頼る設計の前提は NFR 設計で先に確かめる」）**: 判定の部品 `WebInvocationPrivilegeEvaluator` が、Spring Security 7.1.1・Spring Boot 4.1.1 で使えるかを、この段でリポジトリの外の捨ての試しのコードで確かめる。確かめる点は、tech-stack 2節の4つに、読み直しの R-02 の3つを足した次の7つ。
  1. Bean として得られるか。
  2. 方法つきの決まり（`GET /api/appearance` だけの公開）を方法ごとに判定できるか。
  3. 未ログインの主体の渡し方（null か匿名のトークンか）。
  4. `AuthenticatedUserToken` の主体を `AdminAuthorizationManager` が読めるか。
  5. パス変数を持つ口（`/api/problems/{slug}`）の判定用の URI に入れる見本の値（例 `x`）で、決まりの照合が変わらないか。
  6. `server.error.path` を解決した道（今は `/error`）を判定にかけられるか。
  7. 明示の matcher を持たず `anyRequest().permitAll()` で通っている `/error` を、未ログインで「通る」と判定するか。
  試しの範囲と置き場は Q1。結果の要点（版・設定・判定の結果・設計への意味）は security-design.md に写す。使えないと分かったら、機能設計 BR1.5 と要件 NFR1.4 のとおり MockMvc の形に決め直し、まとめの確認で差として伝える。
- **要件 NFR6.4 の `access.service`**: どの単位も手を入れない。U1 の機能設計 W1、`role` の NFR 要件（tech-stack・memory）、`group`・`navigation`・`dsl-v2` の NFR 要件で確かめた。要件の見込み（手を入れる Bolt で一覧から外す）は当たらず、一覧の作業は起きない。Build and Test で、`access.service` の本体に差が無いことを `git diff` で確かめて記録する。読み直しの R-04 への手当てとして、security-design.md と traceability.json に持ち主の単位が「無し（当たらない）」であることを明記する。

---

## 設計の要点（質問にしない案。まとめの確認で確かめる）

1. **論理の部品**（logical-components.md に書く）:
   - バックエンド: `ApiAccess`・`ApiAccessLevel`（`common.security`）、静的な検査 `ApiAccessArchitectureTest`、実行時の検査 `ApiAccessConsistencyIT`（PUBLIC の一覧の定数を含む）、主体の手伝い（`common/testsupport`）、`AccessBoundaryArchitectureTest`・`UserBoundaryArchitectureTest`。
   - 画面: `eslint.config.js` の機能ごとの決まりとそのテスト、`shared/tree`、`app/registry` の型と登録の検査、`app/login-state` の `useLogout`。
   - 名前はコード生成で確かめて決めてよい（機能設計・要件と同じ扱い）。
2. **アイコンの照合（読み直しの R-03）**: 実行時の照合の一覧（18 個）を `app/registry` に置き、両方向を型の検査で守る。
   - 一覧の要素が `IconName` に入る向きは `satisfies readonly IconName[]` で守る。
   - `IconName` のすべてが一覧に入る向きは、`Exclude<IconName, 一覧の要素>` が `never` であることの型の確かめで守る。
   - 要素の数（18）は固定せず、両方向の一致で守る（make-you-chic-ui の固定先を上げて増えたら、型の検査が落ちて気づく）。
   - 登録の検査の境界は、一覧の名前は通る、一覧に無い名前と空の文字は `RegistrationError` で止まる。
3. **共有の木の深さ（読み直しの R-05）**: 共有の木そのものには深さの上限を置かない。
   - 理由: 子は呼ぶ側の `loadChildren` が返したものだけで、深さは呼ぶ側のデータで決まる。S4・S8 はスキーマ→テーブルの2段で、カラムは右の表に出す。
   - 木は再帰ではなく、開いた節ごとに描く（開いていない節の子は描かない）ため、深い木でも開いた所までしか描かない。
   - 深さの上限の決まり（`team.md` の N 階層のメニュー）は、メニューの木を扱う U2（DSL の検証）・U7（サイドバー）が持ち、共有の木には当たらない。security-design.md にこの理由を書く。
4. **ESLint の制限の確かめ（読み直しの R-06）**: テストは Vitest の node の実行環境（ファイルの先頭で `@vitest-environment node`）で、ESLint の `ESLint` の部品に `frontend/eslint.config.js` を読ませ、`lintText` に見本の文を渡して誤りの有無を見る。見本の道の例は次のとおり。
   - 止める: `features/registration/x.ts` から `../auth/authSession`・`../../features/auth/authSession`、`shared/tree/x.ts` から `../../app/registry/types`・`../../features/auth/authSession`。
   - 許す: `features/useradmin/testing/x.ts` から `../api/types`、`features/auth/x.ts` から `../../app/i18n/i18n`・`../../shared/api-client/apiClient`、`features/registration/x.test.ts` から `../auth/authSession`。
5. **後の Intent での分類の広げ方（読み直しの R-07、1行）**: 業務データの API を権限で守る後の Intent（J・K）が `ApiAccessLevel` に値を足す（互換の変更）か、AUTHENTICATED のまま中で判定するかを決める。値を足すときは、その Intent が実行時の検査の主体（権限を持つ・欠く利用者）と期待の表を足す。U1 の検査は表を足すだけで広げられる形にしておく（主体と期待を表で持つ）。
6. **観測**: U1 は実行時に動く部品（指標・ログを出すもの）を持たないため、観測の設計は当たらない。検査の時間は Build and Test で実測して記録する（要件 NFR6.8）。

---

## Q1 捨ての試しの範囲と置き場

背景: 判定の部品の7つの点を確かめる試しのコード（要件 NFR1.3・tech-stack 2節、`project.md` の学び）は、リポジトリの外で作ります。どこまで本物に近づけるかで、確かめの確かさと、かかる手間が決まります。

- 本物の決まりは `SecurityConfig` と、各機能の `SecurityRuleContributor`（auth・access・invitation・appearance）の組み合わせです。
- 主体の型（`AuthenticatedUserToken`・`AuthenticatedUser`）は auth にあります。
- `/error` の道は `server.error.path` の解決で決まります。

試しの結果は security-design.md に写し、試しのコードは捨てます（リポジトリには入れない）。

A. **このリポジトリを scratchpad（リポジトリの外の一時の場所）へ写し、その写しに試しの結合テストを1つ足して流す**（推奨）。本物の `SecurityConfig`・contributor・主体の型・`/error` の設定をそのまま使えるため、7つの点をすべて本番と同じ決まりで確かめられる。流すのは写しの中の `:backend:integrationTest --tests <試しのテスト>` だけで、このリポジトリの作業フォルダ・ブランチ・ビルドの結果には触れない。試しの後に写しを消す。手間は写しと1回のビルド（数分）
B. Spring Boot 4.1.1・Spring Security 7.1.1 だけを入れた最小の新しいプロジェクトを scratchpad に作り、決まりを似せて書いて確かめる。速くてこのリポジトリに依らないが、本物の決まりの組み合わせ（order・`AdminAuthorizationManager`・`/error` の転送）を再現しきれず、点 4・6・7 の確かさが下がる
C. 試しをせず、コード生成の最初の Step で本番のテストとして書いて確かめる。手間は最も少ないが、使えなかったときの設計の決め直しがコード生成の中に入り、`project.md` の学びと承認済みの要件（tech-stack 2節の「NFR 設計で先に確かめる」）から外れる
X. Other (please specify)

[Answer]: A **Mode:** guided

## Consolidated Summary Confirmation

答えのまとめ（cross-cutting の NFR 設計）:

- Q1 A: 捨ての試しは、このリポジトリを作業用の一時の置き場（scratchpad）へ写し、写しに試しの結合テストを1つ足してそのテストだけを流す。試しの後に写しを消す。作業フォルダ・ブランチ・ビルドの結果には触れない。確かめる7つの点（技術の選択の4点と、読み直しの R-02 の3点: パス変数の見本の値・`server.error.path` の解決・`anyRequest().permitAll()` で通る `/error`）の結果の要点を成果物に写す。
- 決まっていることと設計の要点（守りの4層、検査の置き場は ADR-008 のとおり全体の置き場、主体を作るテストの手伝いは `common/testsupport`、実行時の検査は既存の起動の文脈を使い回す、空振りの守り（U1 の規則だけ `allowEmptyShould(false)`・口の数 34 以上）、新しい依存は足さない、U1 は個人に関する値を扱わない、要件 NFR6.4 の `access.service` の持ち主は「無し（当たらない）」と明記、アイコンの照合は両方向を型で守る（R-03）、共有の木に深さの上限を置かない（R-05）、ESLint の制限のテストは Vitest の node の実行環境で `lintText` を使う（R-06）、後の Intent での分類の広げ方は表を足す形（R-07）、観測は当たらない）のとおり。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
