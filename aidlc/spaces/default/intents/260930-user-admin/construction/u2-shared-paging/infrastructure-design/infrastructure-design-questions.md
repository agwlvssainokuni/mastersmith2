# Infrastructure Design の質問 — u2-shared-paging（ページ送りの共通化、library）

U2 は、招待の一覧のページ送りの計算を、サーバーは `cherry.mastersmith.common.paging` の Paging（契約 C2）、画面は `frontend/src/shared/paging/paging.ts` の UiPaging（契約 C5）へ、口と振る舞いを変えずに移すだけの library の単位です。library の単位のため、この段の成果物は `cicd-pipeline.md`・`traceability.json` だけです（`infrastructure-specification.md`・`monitoring-design.md` は service・ui・packaging の単位だけ。段の定義の `produces_kinds`）。配備先は開発者の PC 上のコンテナだけで、クラウドの基盤は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。

U2 は新しい依存・API・スキーマ・設定・イメージを持たず、基盤の作りはすべて、承認済みの NFR 要件・NFR 設計・決まりと、既にある仕組み（`.github/workflows/ci.yml`・`build.gradle.kts`・`backend/build.gradle.kts`・`frontend/vitest.config.ts`・`frontend/playwright.config.ts`）で決まっています。判断の分かれる論点が無いため、**質問はありません**（`project.md` の Way of Working「単位に新しく決める論点が無いときは、質問を作らず、設計の要点を要約として確かめる」）。下の要点（案）を確かめてください。

読んだ上流と既にある仕組み:

- この単位の承認済みの NFR 設計 `aidlc/spaces/default/intents/260930-user-admin/construction/u2-shared-paging/nfr-design/`（`security-design.md` の1〜11節と承認の場の決定 A-01〜A-04、`logical-components.md` の1〜7節、`traceability.json`）
- この単位の承認済みの NFR 要件 `aidlc/spaces/default/intents/260930-user-admin/construction/u2-shared-paging/nfr-requirements/`（`tech-stack-decisions.md` の NFR9.6〜NFR9.11、`security-requirements.md` の残る危険 R1・R2 と承認の場の決定 R-01〜R-05）
- この単位の承認済みの機能設計 `aidlc/spaces/default/intents/260930-user-admin/construction/u2-shared-paging/functional-design/`（`functional-spec.md`・`rules.md`）
- `aidlc/spaces/default/intents/260930-user-admin/inception/domain-design/components.md`（Paging・UiPaging）、`inception/contract-design/contract-summary.md`（C2・C5）、`inception/delivery-planning/bolt-plan.md`（B2 = U2・U4、画面に関わる Bolt は統合の前に E2E を流す、単位ごとの squash も可）
- 既にある仕組み（読むだけ）: `.github/workflows/ci.yml`（`develop` へのプッシュで `./gradlew verify`、秘密を使わない、制限時間 60 分）、`build.gradle.kts`（verify の段 0〜9、`e2eTest` は verify と CI の外）、`backend/build.gradle.kts`（`packagesJudgedByTotal` の 12 パッケージ、計測の除外は起動クラスと `*Properties` だけ）、`frontend/vitest.config.ts`（計測は `src/**/*.{ts,tsx}`、下限は全体の合計で行 80%・分岐 70%）、`frontend/e2e/`（招待に関わる `060-invitation-accessibility.e2e.ts`・`090-invitation-registration-flow.e2e.ts`）
- 参照の洗い出し（読み取りだけ、2026-10-02）: `InvitationPaging` を参照するのは `invitation/service/InvitationService.java` と、テストの `invitation/domain/InvitationPagingTest.java`（移す）・`invitation/repository/InvitationRepositoryIT.java`（参照先だけ変える）。画面の `features/invitation/paging.ts` を参照するのは `InvitationList.tsx`・`useInvitationAdmin.ts` と、テストの `paging.test.ts`（移す）

## 決まっていること（質問にしない）

| 決まっていること | 出典 |
|---|---|
| 配備先は開発者の PC 上のコンテナだけで、クラウドの基盤・IaC・警報の通知先は作らない | `project.md` の Deployment |
| U2 はイメージ・`compose.yaml`・`.env`・ボリューム・JVM の設定を変えない。基盤の設計に渡す論点は無い | `nfr-design/security-design.md` の8節 |
| 1コマンドの検査は `./gradlew verify` で、CI も同じタスクを呼ぶ。E2E（`./gradlew e2eTest`）は verify と CI の外 | `project.md` の Way of Working、`team.md` の Testing Posture |
| 新しい依存を足さない。`gradle/libs.versions.toml`・`backend/gradle.lockfile`・`frontend/package.json`・`frontend/package-lock.json` を変えない | NFR9.11 |
| 招待の既存のテスト（サーバー・画面）は import と参照先の変更だけで通し、中身を書き換えない。既存の `ArchitectureTest`・`InvitationBoundaryArchitectureTest` も書き換えない | NFR9.5・NFR11.1、`security-design.md` の5節・7節 |
| `invitation.*` は `packagesJudgedByTotal` に無く、すでにパッケージごとの下限（行 80%・分岐 70%）の対象。`common.paging` は新しいパッケージとして自動で対象になる。一覧は増やさない・変えない。計測の除外も増やさない | `team.md` の Testing Posture、NFR9.8・NFR9.9、`backend/build.gradle.kts` |
| `invitation.domain` の今の値と `InvitationPaging` を除いた見込みの値は、コード生成の計画で実測して並べ、足すテストの量を見積もる | NFR 設計の承認の場の決定 A-04、残る危険 R1 |
| 画面のカバレッジは全体の合計のまま判定し、`src/shared/paging/` を計測から外さない | NFR9.10、`frontend/vitest.config.ts` |
| 新しい性能の目標・k6 の場面・指標・警報・ログを足さない。招待の一覧の応答時間は既存の k6 の場面 `invitationList` で Performance Validation が確かめる | NFR5.1・NFR5.2、`security-design.md` の6節 |
| B2 は画面に関わる Bolt のため、統合の前に E2E を手元で流す。統合は `develop` への squash（単位ごとの squash にするかはコード生成の計画で決める） | `bolt-plan.md`、`team.md` の Way of Working・Testing Posture |
| 要求の行の上限（A-01）・ログの行の偽造（A-02）・`PAGE_SIZE` の一致の確かめ（A-03）は、コード生成の計画で扱う | NFR 設計の承認の場の決定 |

## 質問

ありません（判断の分かれる論点が無いため）。

## Infrastructure Design の要点（案）

1. **成果物**: `cicd-pipeline.md`（CI と1コマンドの検査のどの段で何を確かめるか、E2E、戻し方、B2 で確かめること）と `traceability.json`（NFR 設計の枝番 NFR3.1・NFR5.1・NFR5.2・NFR9.1〜NFR9.11・NFR11.1 を、verify の段・テスト・設定に対応づける）。既存の仕組みの記録として書き、新しい仕組みは作らない（`project.md` の Deployment）。
2. **CI（`ci.yml`）と verify の段は変えない**: 段 1・2・3（フォーマット・リンタ・ライセンスヘッダー）は移したファイルとテストの新しい置き場も対象になる。段 4（ビルド）で古い参照が残らないこと、段 5（単体テスト）で `PagingTest`（jqwik 500 回）・`paging.test.ts`（fast-check 100 回）と招待の既存の単体テスト・境界テスト、段 6（結合テスト）で `InvitationRepositoryIT` など招待の既存の結合テスト（400 `VALIDATION_FAILED`、最後のページとその次の空の 200）、段 8 で SpotBugs・oxlint・Gitleaks・OSV-Scanner を除外なしで通す。CI は同じ `./gradlew verify` を統合の後に流す再確認。
3. **カバレッジ（段 7）**: `packagesJudgedByTotal` は変えない（`invitation.*` は元から一覧に無く、外す作業も戻す作業も無い）。`invitation.domain`・`invitation.service`・`common.paging` の値を、`:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測して記録する。`invitation.domain` が下回れば、除外を増やさずに同じパッケージのほかのクラスのテストを足す。画面は全体の合計で判定する。
4. **E2E（verify と CI の外）**: B2 の統合の前に、手元で `./gradlew e2eTest` を流し、招待に関わる `060-invitation-accessibility.e2e.ts`・`090-invitation-registration-flow.e2e.ts` が変更なしで通ることを確かめる（U4 の変更とあわせて同じ Bolt で1回）。U2 のために E2E のファイルは足さない。
5. **戻し方**: スキーマ・設定・イメージの作り方の変更が無いため、戻しは直前の版のイメージだけで済み、内部DB のバックアップの要否に影響しない。
6. **秘密と依存**: 秘密情報を扱わず、CI に秘密を渡さない。新しい依存が無いため、ライセンスの確かめと lockfile の更新は要らない（変わっていないことをコード生成のレビューで確かめる）。
7. **B2 で確かめること**: (i) 参照の切り替えの後に `InvitationPaging` と `features/invitation/paging.ts` への参照が残っていないこと、(ii) 招待の既存のテストの中身が変わっていないこと（import と参照先だけの差であること）、(iii) カバレッジの実測の値（3つのパッケージと画面の全体）、(iv) E2E の結果、(v) 依存・lockfile・警報の決まりが変わっていないこと。

---

## Consolidated Summary Confirmation

答えのまとめ:

- 質問はありません。要点（案）は上の 7 件のとおり（成果物、CI と verify の段は変えずに既存の段で確かめる、カバレッジは `packagesJudgedByTotal` を変えずに `invitation.domain`・`invitation.service`・`common.paging` を実測して記録する、B2 の統合の前の E2E で招待の流れが変わらないことを確かめる、戻しはイメージだけ、秘密と依存の変更なし、B2 で確かめること）

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
