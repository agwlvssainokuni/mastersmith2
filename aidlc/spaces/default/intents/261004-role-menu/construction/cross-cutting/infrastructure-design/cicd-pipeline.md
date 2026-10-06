# CI/CD Pipeline — U1 横断の準備（cross-cutting）

この文書には、U1 の検査の流れ（CI と1コマンドの検査）、E2E、戻し方、B2 で確かめることを書きます。

U1 は library の単位で、B2 で作ります。中身は次のとおりです。

- バックエンド: API の分類の印 `ApiAccess`（契約 C1）と、それを確かめる静的な検査・実行時の検査
- 画面: ESLint の import の制限、共有の木 `SharedTreeView`、画面の登録の型の拡張、`useLogout`（契約 C2）

実行時に要求を受ける部品・データ・外への接続・指標・ログは持ちません。新しい依存・API・スキーマ・設定・イメージ・秘密も足しません。そのため、CI と1コマンドの検査と配備の流れは **変えません**。この文書は既にある仕組みの記録として、どの段で何を確かめるかを書きます。

配備先は開発者の PC 上のコンテナだけです。クラウドの基盤・IaC・警報の通知先は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。

## 出典

- 答え: `infrastructure-design-questions.md`（質問なし、設計の要点 5 件、まとめの確認は Looks correct）
- 上流（どれも `aidlc/spaces/default/intents/261004-role-menu/` の下）:
  - `construction/cross-cutting/nfr-design/security-design.md`（2〜8節、9節の承認の場の直し R-01）・`logical-components.md`（1〜4節）・`traceability.json`
  - `construction/cross-cutting/nfr-requirements/security-requirements.md`（NFR1・NFR4・NFR6）・`tech-stack-decisions.md`
  - `construction/cross-cutting/functional-design/functional-spec.md`
  - `inception/domain-design/components.md`（AccessControl・SharedTreeView・AppFrame）
  - `inception/contract-design/contract-summary.md`（C1・C2）
  - `inception/delivery-planning/bolt-plan.md`（B2）
- library の単位のため、NFR 設計の段は `performance-design.md`・`scalability-design.md`・`reliability-design.md`・`observability-design.md` を作っていません。性能・規模は `logical-components.md` 4節（API を持たないため N/A）、観測は `security-design.md` 6節（当たらない）が扱っています。
- 既にある仕組み（正とする。読むだけ）: `.github/workflows/ci.yml`・`build.gradle.kts`・`backend/build.gradle.kts`・`frontend/package.json`・`frontend/vitest.config.ts`・`frontend/eslint.config.js`・`frontend/e2e/`

## 1. CI（GitHub Actions）

`.github/workflows/ci.yml` は変えません。

| 項目 | 今の形 | U1 での扱い |
|---|---|---|
| きっかけ | `develop` へのプッシュ・`v*` のタグ・手動 | 変えない。CI は統合の後の再確認（`team.md` の Way of Working） |
| 実行 | `./gradlew verify`（`ubuntu-latest`、制限時間 60 分） | 変えない。足す結合テストは1つで、既存の起動の文脈を使い回す（5節） |
| サブモジュール | `submodules: true` で固定先のコミットを取得 | 変えない。U1 は固定先を更新しない |
| 依存の入れ方 | lockfile どおり（`npm ci`、Gradle の lockfile） | 変えない。U1 は lockfile を変えない（要件 NFR6.7） |
| 道具 | Gitleaks・OSV-Scanner の版と SHA-256 を固定して入れる | 変えない |
| 秘密 | CI は秘密を使わない | 変えない（9節） |
| 成果物 | WAR をコミットのハッシュの名前で保存 | 変えない。違反の見本のクラスはテストのソースにあり、WAR に入らない |

CI が失敗したときは、次の Bolt に進む前に `team.md` の Testing Posture「不安定なテストと CI の失敗」の決まりで扱います。

## 2. 1コマンドの検査（`./gradlew verify`）の段と関門

段・関門・設定は増やさず、変えません。U1 の変更は、既存の段で次のとおり確かめられます。どれか1つでも失敗したら全体が失敗します（既存のとおり）。

| 段 | U1 で確かめること | 関門（失敗の条件） | 当たる要件 |
|---|---|---|---|
| 0 準備 | 変わらない（サブモジュールを変えていないことの確認を含む） | 既存のとおり | NFR6.7 |
| 1 フォーマット | 足す Java（`ApiAccess`・検査・手伝い・見本）は Spotless（palantir-java-format）、足す TypeScript（`shared/tree`・`app/registry`・`app/login-state`・ESLint の決まりのテスト）と `eslint.config.js` は Prettier の対象になる | 書式の違い | NFR6.6 |
| 2 リンタ | `frontend/eslint.config.js` の機能ごとの `no-restricted-imports` と `src/shared/**` の制限が、`eslint .` で本体のすべてに当たる。本体の違反は 0 件（`useRegistration.ts` の1件を `useLogout` に直す）。テストのファイルは制限の対象外 | 1件でも error | NFR6.1 |
| 3 ライセンスヘッダー | 足すファイルの先頭に `/* ... */` の Apache License 2.0 のヘッダーがある（Java は段 1 の Spotless、画面は `check-license-header.mjs`） | ヘッダーが無い・形が違う | NFR6.6 |
| 4 ビルド | 34 の口への印のコンパイル、登録の型（`section`・`icon`・`logout`）と、アイコンの照合の一覧の両方向の型の確かめ（`tsc --noEmit`）、Vite のビルド | コンパイル・型の誤り。make-you-chic-ui の `IconName` が増えると型の確かめが落ちる | NFR1.1・NFR1.8・NFR6.5 |
| 5 単体テスト | 3節の `*Test` と画面のテスト | 1件でも失敗 | NFR1.1・NFR1.2・NFR1.5・NFR1.6・NFR1.9・NFR4.1〜NFR4.4・NFR6.2・NFR6.3・NFR6.5 |
| 6 結合テスト | `ApiAccessConsistencyIT`（3つの主体の判定、静的な検査との集合の一致、PUBLIC の口の一覧との比べ） | 1件でも失敗（違いをすべて並べて落とす） | NFR1.3・NFR1.5・NFR1.6・NFR1.10 |
| 7 カバレッジ | 4節のとおり | 全体またはパッケージごとの行 80%・分岐 70% を下回る。除外は足さない | NFR6.4・NFR6.5 |
| 8 安全の検査 | SpotBugs ＋ FindSecBugs（priority 1 と `SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION`）、OSV-Scanner、Gitleaks を、除外を足さずに通す。注釈は実行の中身を持たない | 既存の基準 | NFR1.10・NFR6.7 |
| 9 成果物 | 既存の `bootWar`・`verifyDslSchemaInWar`・初回の読み込みの量の確かめ | WAR が作れない・量の上限を超える | （U1 の画面の追加は量にほとんど効かない） |

補足:

- `ApiAccessConsistencyIT` は、組み込みの H2 の一時の内部DB（`TestDatabase`）だけで起動します。コンテナを使わないため、コンテナの実行環境が無いときに飛ばされることはありません（飛ばしてよいのは対象DB のテストだけ。`team.md` の Way of Working）。
- 新しい性能の目標・k6 の場面・指標・警報・ログは足しません。警報の決まり（`docker/monitoring/`）は変えません。

## 3. 足すテストの置き場（Gradle のタスクと Vitest の対象）

テストは、既存の名前での振り分けに乗せます。バックエンドは `*Test` が `test`、`*IT` が `integrationTest` です（`backend/build.gradle.kts`）。画面は `frontend/vitest.config.ts` の `include: ['src/**/*.test.{ts,tsx}']` に入ります。どの振り分けの設定も変えません。

| テスト | 置き場 | 入るタスク（verify の段） | 出典 |
|---|---|---|---|
| `ApiAccessArchitectureTest` | `backend/src/test/java/cherry/mastersmith/`（全体の置き場） | `:backend:test`（段 5） | `security-design.md` 4.2 |
| `ApiAccessRulesTest`・`PublicApiInventoryTest`（検査の検査） | 同上 | `:backend:test`（段 5） | `security-design.md` 4.2.1・4.4.1 |
| `AccessBoundaryArchitectureTest`・`UserBoundaryArchitectureTest` | 各機能のテストのパッケージ | `:backend:test`（段 5） | `security-design.md` 4.6 |
| `ApiAccessConsistencyIT` | `backend/src/test/java/cherry/mastersmith/`（全体の置き場） | `:backend:integrationTest`（段 6） | `security-design.md` 4.3・4.4 |
| 手伝い（`ApiAccessRules`・`PublicApiInventory`・主体の手伝い）と違反の見本のクラス | `backend/src/test/java/cherry/mastersmith/common/testsupport/`（見本は `apiaccess/`） | テストから使うだけ（見本は決して設定しない条件で Bean にならない） | `logical-components.md` 1節 |
| 共有の木・登録の検査・`useLogout` のテスト（部品ごとに vitest-axe を1件） | 対象と同じ場所の `*.test.ts(x)` | `frontendTest`（段 5） | `security-design.md` 5.1・5.2 |
| ESLint の決まりのテスト | `frontend/src/` の下の `*.test.ts`。ファイルの先頭で `@vitest-environment node` を指定する。名前と置き場の細部は Code Generation で決める | `frontendTest`（段 5） | `security-design.md` 5.3、この段のまとめの確認 |

ESLint の決まりのテストを `frontend/src/` の下に置く理由は、次のとおりです。

- Vitest の今の対象は `src/**` だけです。`src/` の外に置くと、Vitest の設定を広げない限り `verify` と CI で流れません。
- テストは ESLint の `ESLint` の部品に `frontend/eslint.config.js` を読ませ、`lintText` で見本の文を確かめます。`eslint.config.js` そのものは import しません。
- 画面のカバレッジの計測は `src/**/*.{ts,tsx}` からテストのファイルを除いた範囲のため、このテストは下限の計算に影響しません。

## 4. カバレッジ（段 7）

| 対象 | 今の扱い | U1 での扱い |
|---|---|---|
| `packagesJudgedByTotal`（`backend/build.gradle.kts`、2026-10-06 の時点で 7 パッケージ） | 全体の合計で判定する既存のパッケージの一覧 | 変えない。U1 が手を入れる `common.security` と、口を持つ各機能の `web`（`access.web`・`appearance.web`・`auth.web`・`common.error.web`・`dslmanage.web`・`invitation.web`・`user.web`・`useradmin.web` の 8 つ）は一覧に無く、パッケージごとの下限（行 80%・分岐 70%）がそのまま当たる（要件 NFR6.4）。口を持つクラスは `common.health`・`common.web` に無いことをコードで確かめた |
| `access.service` | 一覧にある | 手を入れない。Build and Test で、本体に差が無いことを確かめて記録する（`security-design.md` 4.6、読み直しの R-04） |
| 印だけを足す `web` のパッケージ | すでにパッケージごとの下限の対象 | 注釈は実行の中身を持たないため、行・分岐の数は変わらない見込み。実測して記録する |
| 画面（`frontend/vitest.config.ts` の `thresholds`） | 全体の合計で行 80%・分岐 70% | `shared/tree`・`app/registry`・`app/login-state` の追加の分を、それぞれのテストで覆う（要件 NFR6.5）。計測から外さない |
| 計測の除外 | 起動クラス・設定値だけのクラス・自動生成コード・`vendor/` | 増やさない |

実測は `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で行います（`team.md` の Testing Posture、`project.md` の学び）。

## 5. 検査の時間（要件 NFR6.8）

- 目標の数値は置きません。
- `ApiAccessConsistencyIT` は、ほかの結合テストと同じ設定の形（本番の設定、`TestDatabase`、テスト用の決まりとテストだけの口を入れない）にします。こうすると Spring の起動の文脈を使い回せ、新しい起動の形が増えません（`security-design.md` 4.3 手順 1）。
- NFR 設計の捨ての試しでは、1つの結合テストがコンパイルを含めて約 32 秒でした（`security-design.md` 2.1）。
- Build and Test で、`verify` の全体の時間と増え方を実測して記録します。CI の `timeout-minutes: 60` は変えません。

## 6. 対象外の入口と本番の決まり（変えないもの）

- 本番の Spring Security の決まり（`SecurityConfig`・各機能の `SecurityRuleContributor`・`AdminAuthorizationManager`・`ApiDefaultAccess`）は変えません。U1 の検査は、判定の部品（`WebInvocationPrivilegeEvaluator`）を通して読むだけです（`security-design.md` 3節 L4）。
- 次の2つは口の一覧に入らないため、U1 の検査の対象外のままです。守りは既存の公開の範囲と配信のテストに任せます（要件 NFR1.7、`security-design.md` 4.5）。
  - actuator（Web に出すのは health だけ）
  - 画面の静的配信
- `application.yaml`・`compose.yaml`・`Dockerfile`・`.env.example`・内部DB の表（Flyway の移行）は変えません。

## 7. E2E（`./gradlew e2eTest`、verify と CI の外）

- E2E は意図して `verify` と CI の外に置いています（`build.gradle.kts` の `e2eTest`、`team.md` の Testing Posture）。代わりの実行の場は、統合の前とリリースの前の手元です。
- B2 は画面の登録の型とログアウトの口（auth が渡す `logout`、骨組みの `useLogout`）に手を入れる、画面と認証に関わる変更です。そのため、統合の前に手元で `./gradlew e2eTest` を流し、既存の E2E（`frontend/e2e/` の 13 本）がすべて通ることを確かめます。
  - 特に `020-auth.e2e.ts`（ログアウト）と `090-invitation-registration-flow.e2e.ts`（登録の完了の画面）が、`useLogout` への直しの後も通ることを見ます。
- U1 のために E2E のファイルは足しません。共有の木の実際のブラウザの axe は、木を使う画面の単位が持ちます（S4 は U6、S8 は U7。要件 NFR4.5）。
- 前提は既存のとおりです（Playwright の chromium、`docker compose --profile mail up -d mailpit` で起動したメールの受け手）。

## 8. 統合と配備の流れ

| 項目 | 扱い | 出典 |
|---|---|---|
| 作業ブランチ | `develop` から作る短命のブランチ（例 `feature/261004-role-menu-b2`）。worktree は使わない | `team.md` の Way of Working |
| 統合の前の関門 | `./gradlew verify`（コンテナの実行環境あり、対象DB のテストを飛ばさない）と E2E（7節） | `team.md` の Way of Working・Testing Posture |
| 統合の形 | `develop` への squash（1 Bolt が1コミット）。サブモジュールの更新は無いため、fast-forward の例外には当たらない | `team.md` の Way of Working |
| プッシュ | 依頼者自身が行う。AI はプッシュしない | `team.md` の Way of Working |
| 配備 | 既存の手順（`Dockerfile`・`compose.yaml`・README）のまま。イメージの作り方・`.env`・ボリューム・JVM の設定を変えない | 6節 |
| 配備の段への引き継ぎ | 無い（スキーマ・設定・データの形を変えないため） | `logical-components.md` 3節 |

## 9. 戻し方と秘密

| 項目 | 扱い |
|---|---|
| DB スキーマ | 変えない。内部DB に触れない |
| 前の版への戻し | 直前の版のイメージで起動し直すだけで済む。前の版には `ApiAccess` の印と画面の登録の型の拡張が無いが、どちらも本番の判定と API を変えないため、戻しても権限の判定は同じ |
| 設定（`.env`・`application.yaml`） | 変えない。戻すものは無い |
| 秘密 | 足さない。CI に秘密を渡さない。主体の手伝いは `example.com` の見本の値と固定の利用者 ID だけを使い、内部DB に利用者を作らない（要件 NFR1.10）。Gitleaks の除外を足さない |
| 依存 | 新しい依存を足さず、`gradle/libs.versions.toml`・`backend/gradle.lockfile`・`frontend/package.json`・`frontend/package-lock.json` を変えない。ライセンスの確かめと lockfile の更新は要らない（要件 NFR6.7） |

## 10. DevSecOps と Compliance の視点

| 観点 | 確かめた結果 |
|---|---|
| 認可の守り | 本番の判定（L4）は変えない。U1 の検査は、分類の書き忘れ・食い違い・`permitAll` の広げすぎを統合の前（段 5・6）で落とす追加の守り。画面の区画・`visibleWhen` は見せ方だけで、サーバー側の判定の代わりにしない（`project.md` の Mandated） |
| 検査が本当に落とすこと | 規則と比べ方を手伝いの1か所にまとめ、違反の見本と変えた一覧で落ちることを段 5 で確かめる（`security-design.md` 4.2.1・4.4.1） |
| 失敗の文と個人に関する値 | 検査の失敗の文は、口のクラス名・方法名・道・方法・主体の種類だけ。主体は見本の値（要件 NFR1.10） |
| 静的解析・依存の脆弱性 | 既存の段 8 の関門を除外なしで通す。新しい依存が無いため、OSV-Scanner の対象も変わらない |
| 画面の入力 | 共有の木は文字の差し込みだけで描き、子の読み込みの失敗は `labels.loadFailed` だけを出す（要件 NFR1.9）。ESLint のセキュリティ系のルール（`react/no-danger` など）は段 2 の既存の設定のまま |
| 公開のリポジトリ | テストのデータは予約のドメインと固定の値だけで、個人に関する値・秘密を置かない |

## 11. B2 で確かめること

| 確かめ | 方法 | 成り立たないとき |
|---|---|---|
| (i) 34 の口すべてに印があり、静的な検査と実行時の検査の集合が一致する | 段 5・6 | 印を直す。検査を緩めない |
| (ii) 違反の見本が落ち、違反なしの見本が通り、空の集まりが落ちる。PUBLIC の一覧の比べが4つの境界で差を出す | 段 5（`ApiAccessRulesTest`・`PublicApiInventoryTest`） | 規則と比べ方を直す |
| (iii) ESLint の本体の違反が 0 件で、決まりのテストが止める道と許す道の見本で通る | 段 2・段 5 | 本体を直す。制限を緩めない |
| (iv) カバレッジの実測の値（`common.security`・8 つの `web`・画面の全体）と、`access.service` に差が無いこと | 4節の実測 | 除外を増やさず、テストを足す |
| (v) `verify` の時間の増え方 | 5節（Build and Test で記録） | 目標は置かない。記録する |
| (vi) E2E の結果（全体） | 7節 | 統合しない。原因を直す |
| (vii) 依存・lockfile・警報の決まり・`packagesJudgedByTotal`・計測の除外・本番の安全の決まりが変わっていない | コード生成のレビュー | 元に戻す |

## 12. 上流との差

1. ESLint の決まりのテストの置き場について、`logical-components.md` 2節は「コード生成で決める」としていました。この段では、Vitest の今の対象の中（`frontend/src/` の下の `*.test.ts`、node の実行環境）に絞りました。`verify` と CI で、Vitest の設定を変えずに毎回流すためです。ファイルの名前と細部は、今までどおり Code Generation で決めます。
2. このほかに、上流（要件・機能設計・契約 C1・C2・NFR 要件・NFR 設計）と違う作りはありません。この文書は、承認済みの設計と既にある仕組みを、CI と verify の段ごとに並べ直した記録です。
