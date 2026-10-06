# 基盤の設計の質問 — U1 cross-cutting

単位 U1 cross-cutting（library。B2 で作る）の基盤の設計の前に、決まっていない点を確かめます。この単位が作る成果物は、段の定義の `produces_kinds` により `cicd-pipeline.md` と `traceability.json` の2つです（library のため、`infrastructure-specification.md`・`monitoring-design.md` は作りません）。

読んだもの: この単位の承認済みの NFR 設計（`nfr-design/security-design.md`・`logical-components.md`・`traceability.json`、9節の承認の場の直し R-01）、NFR 要件（`nfr-requirements/security-requirements.md`・`tech-stack-decisions.md`）、機能設計（`functional-design/functional-spec.md`）、`inception/domain-design/components.md`、`inception/contract-design/contract-summary.md`（C1・C2）、`inception/delivery-planning/bolt-plan.md`（B2）、既存の CI とビルド（`.github/workflows/ci.yml`・`build.gradle.kts`・`backend/build.gradle.kts`・`frontend/package.json`・`frontend/vitest.config.ts`）、`team.md`・`project.md`。

## 決まっていること（質問にしない）

### 基盤の範囲

- 配備先が決まるまで、基盤の設計は開発者の PC 上のコンテナの範囲に限り、クラウドの基盤（IaC・検証環境・警報の通知の先）は作らない（`project.md` の Deployment）。
- U1 は実行時に要求を受ける部品・データ・外への接続・指標・ログを持たない（`logical-components.md` 冒頭・3節、`security-design.md` 6節「観測は当たらない」）。そのため、`compose.yaml`・`Dockerfile`・`.env.example`・内部DB の表（Flyway の移行）・監視の設定（`docker/monitoring`）は変えない。
- 本番の Spring Security の決まり（`SecurityConfig`・各機能の `SecurityRuleContributor`・`AdminAuthorizationManager`・`ApiDefaultAccess`）は変えない（`security-design.md` 3節 L4）。

### CI と1コマンドの検査（既にあるものの記録）

- CI（`.github/workflows/ci.yml`）は `develop` へのプッシュで `./gradlew verify` を流すだけの形のままで、変えない（`project.md` の「CI の仕組みが既に実装されているときは、新しい設計ではなく記録として書く」）。新しい Actions・秘密・道具の取得は足さない。
- 新しい依存は足さない（要件 NFR6.7。試しで使った ArchUnit 1.5.1・Spring Security の `WebInvocationPrivilegeEvaluator`・ESLint ^10.8.1 はすべて今の依存）。そのため lockfile（`backend/gradle.lockfile`・`frontend/package-lock.json`）と OSV-Scanner の対象は変わらない。
- Gradle のタスク（`verify` の段の並び、`test`・`integrationTest` の名前での分け方、JaCoCo の下限、`packagesJudgedByTotal`）は変えない。手を入れる `common.security` と各機能の `web` の 8 パッケージは `packagesJudgedByTotal` に入っておらず、パッケージごとの下限（行 80%・分岐 70%）がそのまま当たる（要件 NFR6.4）。`access.service` には手を入れない（読み直しの R-04）。
- `./gradlew verify` の段に、この単位で次が足される（どれも既存のタスクの中に入る）。

| verify の段 | 既存のタスク | この単位で足されるもの |
|---|---|---|
| 2 リンタ（`verifyLint`） | `frontendLint`（`oxlint . && eslint .`） | `frontend/eslint.config.js` の機能ごとの `no-restricted-imports` と `src/shared/**` の制限。本体の違反は 0 件（`useRegistration.ts` の1件を `useLogout` に直す） |
| 5 単体テスト（`verifyUnitTest`） | `:backend:test`（`*Test`）・`frontendTest`（Vitest） | `ApiAccessArchitectureTest`・`ApiAccessRulesTest`・`PublicApiInventoryTest`・`AccessBoundaryArchitectureTest`・`UserBoundaryArchitectureTest`、画面の共有の木・登録の検査・`useLogout`・ESLint の決まりのテスト |
| 6 結合テスト（`verifyIntegrationTest`） | `:backend:integrationTest`（`*IT`） | `ApiAccessConsistencyIT`（ほかの結合テストと同じ設定の形で、起動の文脈を使い回す。要件 NFR6.8） |
| 7 カバレッジ（`verifyCoverage`） | JaCoCo・`@vitest/coverage-v8` | 新しいコードが全体とパッケージごとの下限に入る（除外は増やさない） |

- ESLint の決まりのテスト（要件 NFR6.2）は、Vitest の今の対象（`frontend/vitest.config.ts` の `include: ['src/**/*.test.{ts,tsx}']`）の中、つまり `frontend/src/` の下の `*.test.ts` に置き、ファイルの先頭で `@vitest-environment node` を指定する。こうすると Vitest の設定を変えずに `frontendTest` と CI で毎回流れる。ファイルの名前と置き場の細部は Code Generation で決める（`logical-components.md` 2節の「置き場はコード生成で決める」の範囲を、Vitest の対象の中に絞った）。
- 違反の見本のクラス（`common/testsupport/apiaccess/`）は、決して設定しない設定の値の条件で Bean にならず、本番の検査（`DO_NOT_INCLUDE_TESTS`）にも WAR にも入らない（`security-design.md` 4.2.1）。
- 検査の時間に目標の数値は置かず、Build and Test で `verify` の時間の増え方を `:backend:cleanTest :backend:cleanIntegrationTest` を付けて実測して記録する（要件 NFR6.8、`project.md` の Testing Posture）。CI の `timeout-minutes: 60` は変えない。

### 統合・E2E・配備

- 統合は B2 の短命のブランチから `develop` へ squash で戻す。サブモジュールの固定先の更新は含まない（`team.md` の Way of Working）。
- B2 は画面の登録の型とログアウトの口（`useLogout`、auth が渡す `logout`）に手を入れる、画面・認証に関わる変更のため、統合の前に手元で `./gradlew e2eTest` を流す（`team.md` の Testing Posture）。新しい E2E は足さない（実際のブラウザの axe は U6・U7 が持つ。要件 NFR4.5）。
- 配備の手順・戻し方は変わらない。U1 はスキーマ・`.env`・イメージの設定を変えないため、戻しは直前の版のイメージだけで行える（`team.md` の Deployment）。配備の段への引き継ぎは無い。

## 質問

この単位で新しく決める論点はありません（`project.md` の Way of Working「Construction の設計の段で、単位に新しく決める論点が無いときは、質問を作らず、設計の要点を要約として依頼者に確認する」）。次の設計の要点を要約として確かめ、Looks correct / Request changes で進めます。

## 設計の要点（要約の確認に使う）

1. **作る成果物**: `cicd-pipeline.md`（既にある CI と `verify` の記録に、この単位で足される検査を対応づける）と `traceability.json`（NFR 設計の ID のうち、CI・検査の置き場に当たるものを対応づける）の2つだけ。
2. **基盤の変更なし**: CI のワークフロー・Gradle のタスク・`compose.yaml`・`Dockerfile`・`.env`・内部DB・監視は変えない。新しい依存・道具・秘密も足さない。
3. **足される検査の置き場**: 静的な検査と検査の検査は `verifyUnitTest`（`*Test`）、実行時の検査は `verifyIntegrationTest`（`*IT`、既存の起動の文脈を使い回す）、ESLint の制限は `verifyLint`、ESLint の決まりのテストは `frontend/src/` の下の `*.test.ts`（node の実行環境）で `frontendTest` に入る。どれも CI で毎回流れる。
4. **意図して CI の外に置くもの**: E2E（`./gradlew e2eTest`）は今までどおり CI の外で、B2 の統合の前に手元で流す。実際のブラウザの axe は U6・U7 が持つ。
5. **時間と戻し**: `verify` の時間は Build and Test で実測して記録する（目標の数値は置かない）。戻しはイメージだけで、配備の段への引き継ぎは無い。

## Consolidated Summary Confirmation

答えのまとめ（cross-cutting の基盤の設計）: 質問は無く、上の「設計の要点」1〜5 のとおりとする（作る成果物は cicd-pipeline.md と traceability.json、基盤の変更なし、足される検査の置き場、E2E は CI の外で B2 の統合の前に手元で流す、verify の時間は Build and Test で実測・戻しはイメージだけ）。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
