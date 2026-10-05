# 技術の選択（Tech Stack Decisions）— U1 cross-cutting

出典: この単位の `functional-spec.md`・`rules.md`、`requirements.md`、`contract-summary.md`（C1・C2）、コードの知識ベース `technology-stack.md`、lockfile（`backend/gradle.lockfile`・`frontend/package.json`）、この段の答え `nfr-requirements-questions.md`（Q1: B、まとめの確認）、`team.md`（Code Style）。

## 1. 決定: 新しい依存は足さない

U1 の持ち物（注釈、構造の検査、実行時の検査、ESLint の制限、共有の木、登録の型、ログアウトの口）は、すべて今ある部品で作れる。そのため、ライセンスの確かめ（`team.md`）と、OSV-Scanner・lockfile の対象の変化は起きない（security-requirements.md NFR6.7）。

| 用途 | 部品 | 版（lockfile・package.json） | 使い方 |
|---|---|---|---|
| 分類の注釈 | Java の注釈（標準） | Java 25（今の土台） | `common.security` の `ApiAccess`・`ApiAccessLevel` |
| 静的な構造の検査 | ArchUnit | 1.5.1 | 全体の置き場の `*Test`。U1 の規則だけ `allowEmptyShould(false)` |
| 実行時の検査 | Spring Security の `WebInvocationPrivilegeEvaluator`、Spring Boot Test、spring-security-test | Spring Security 7.1.1、Spring Boot 4.1.1 | 全体の置き場の `*IT`。口の一覧は Spring MVC の `RequestMappingHandlerMapping` |
| 実行時の検査の切り替え先 | MockMvc（Spring Boot Test） | 同上 | 判定の部品が使えないとき（2節） |
| import の制限 | ESLint の標準のルール `no-restricted-imports` | ESLint ^10.8.1 | `frontend/eslint.config.js`。プラグイン（`eslint-plugin-import` など）は足さない |
| 制限のテスト | ESLint の `Linter`（ESLint に含まれる）と Vitest | 同上・Vitest ^5.0.2 | 止める道と許す道の両方 |
| 共有の木 | React（今の土台）、make-you-chic-ui の `Icon` | 固定先 e82b651（B9 で 5bf1ffe に上げる予定。木は影響を受けない） | 木の部品は make-you-chic-ui に無いため frontend の側で作る |
| 画面のテスト | Vitest・Testing Library・user-event・vitest-axe・`@vitest/coverage-v8` | ^5.0.2・^16.3.3 ほか・^0.1.0・^5.0.2 | 共有の木・登録の検査・`useLogout` |
| アイコンの名前の型 | make-you-chic-ui の `IconName`（パッケージの入口から型として出ている） | 同上 | 実行時の照合の一覧（18 個）は `app/registry` に置き、型と一致することを型の検査で確かめる |

Java の版は今の土台（`technology-stack.md`）のまま変えない。

## 2. 決定: 判定の部品の確かめは NFR 設計の捨ての試しで行う

- 実行時の検査（security-requirements.md NFR1.3）は、Spring Security の `WebInvocationPrivilegeEvaluator` で、要求を送らずに口と方法と主体の組を判定する。
- この部品について次の点を、NFR 設計でリポジトリの外の捨ての試しのコードで先に確かめる（`project.md` の学び「部品の振る舞いに頼る設計の前提は NFR 設計で確かめる」）。
  - Spring Security 7.1.1 で Bean として得られるか。
  - 方法つきの決まり（例: `GET /api/appearance` だけを公開）を方法ごとに判定できるか。
  - 未ログインの主体の渡し方（null か匿名のトークンか）。
  - `AuthenticatedUserToken` の主体を `AdminAuthorizationManager` が読めるか。
- 結果の要点（版・設定・判定の結果・設計への意味）は NFR 設計の成果物に写す。
- 使えないと分かったら、NFR 設計で MockMvc の形（security-requirements.md NFR1.4）に決め直し、依頼者に差として伝える。その形では、安全の決まりによる拒否と口の中の業務の拒否の見分け方と、監査に行が残る口の副作用の無い送り方を決める。

### 退けた案

- 最初から MockMvc で実際に送る。判定は確かだが、PUBLIC の口（ログイン・登録の完了など）の本文の用意と、監査の行などの副作用への配慮が要る。この段の答え（機能設計 Q1: B）で、要求を送らない形を先に試すことにした。
- 試しをコード生成の最初に回す。だめだったときに設計の決め直しがコード生成の中に入り、計画の承認とずれるため、NFR 設計で先に確かめる。

## 3. 決定: ArchUnit の空の規則の扱い

- 全体の設定 `backend/src/test/resources/archunit.properties` の `archRule.failOnEmptyShould = false` は変えない。最初の骨組みのときに、まだ無い層の規則を通すために置いたもので、変えると既存の規則に影響しうるため。
- U1 の規則（印の有無、ADMIN と `AdminPaths`、AUTHENTICATED の道、PUBLIC の一覧）だけを、規則ごとの `allowEmptyShould(false)` で空なら落とす。あわせて、対象の口が 34 以上あることを確かめる（security-requirements.md NFR1.5）。

### 退けた案

- 全体の設定を true にする。既存の規則のうち空になるものがあると、U1 と関係の無い所で落ちうる。既存の構造の検査を変えることになり、`team.md` の「既存の ArchUnit のテストを緩める・消すときは承認を得る」に近い扱いが要る。

## 4. 決定: PUBLIC の口の一覧の置き場

- PUBLIC の口の一覧（口の単位で今は 9 つ。各行は宣言された方法の集合と道の型の集合）は、全体の置き場の実行時の検査（`*IT`）の中に、理由のコメントつきで定数として持つ（Q1: B）。設定値の置き換え（`server.error.path`）を解決した道を `RequestMappingHandlerMapping` から読んで比べるため、静的な構造の検査ではなく実行時の検査に置く。正規化の決まりは security-requirements.md 2.2（承認の場の直し R-01）。本体のソースには置かない（本番で使わない値のため）。
- ADR-008 の補足（PUBLIC だけの追加の守り）として、コード生成の計画に書く。

## 5. 決定: ESLint の制限の作り方

- `eslint.config.js` が `src/features/` のディレクトリの一覧を読み、機能ごとに `no-restricted-imports` の `patterns` を作る（承認済みの機能設計 frontend-components.md 5節）。ディレクトリの一覧を読むため、機能を足しても設定を手で直さなくてよい。
- `patterns` の書き方（相対の道 `../B`・`../../features/B` を止め、自分の機能の下位の `../api/types` などを止めない）は、ESLint 10 の `no-restricted-imports` の照合の仕方をコード生成で確かめ、NFR6.2 のテストで固定する。
