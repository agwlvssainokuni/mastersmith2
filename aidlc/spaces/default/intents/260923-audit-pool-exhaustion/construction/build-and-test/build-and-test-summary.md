# Build and Test のまとめ

本 Intent（F2 の修正: 接続プールの上限の既定値を 30 にし、環境変数で変えられるようにし、同時 10・20 件の再現テストを加えた）のビルドとテストの結果。対象のコミットは `d948544`。

## 1. ビルドの状態と前提

- ビルド: 成功（`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`、1分55秒）。成果物は `backend/build/libs/mastersmith.war`。
- 前提: JDK 25、Node.js 24、Gitleaks、OSV-Scanner（`build-instructions.md`）。テストにコンテナの実行環境は要らない。

## 2. テストの種類の一覧

Test Strategy は Minimal、範囲は bugfix である。

| 種類 | 内容 | 手順 |
|---|---|---|
| 回帰テスト（結合） | `ConcurrentLoginAuditIT`（同時 10・20 件の成功のログイン）、`DataSourcePoolIT`（上限の既定値・環境変数・30 本の同時の借用） | `integration-test-instructions.md` |
| 既存のテスト | 単体 387 件、結合 244 件（新しい 5 件を含む）、画面 167 件 | `build-instructions.md` の `verify` |
| 安全の検査 | Gitleaks、SpotBugs＋FindSecBugs、OSV-Scanner（`verify` の中） | `security-test-instructions.md` |
| 負荷の確かめ | k6 による同時 10 件のログイン（配備した後） | `performance-test-instructions.md`（持ち主は `deployment-execution`） |

## 3. カバレッジの見込みと実測

単位の分割は無い。本番のコードの変更は無く、カバレッジは変わらない見込みで、実測でも変わらなかった。

- バックエンド: 行 96.14%、分岐 91.45%（Code Generation の Step 1 の基準と同じ）
- 画面: 行 98.73%、分岐 94.02%

## Target Verification Matrix

目標の出どころ: `code-generation-plan.md` の Testing Contract（team.md・project.md の Testing Posture と bugfix の範囲の下限）と、要件の非機能要件（`inception/requirements-analysis/requirements.md` の NFR1〜NFR4）、および配備の後の確かめの要件 FR6.2。本 Intent では NFR Requirements・NFR Design の段を行っていないため、その成果物は無い。

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| TC-COV-BE-LINE | `code-generation-plan.md` Testing Contract（team.md Testing Posture） | バックエンドの行カバレッジ 80% 以上 | 96.14%（1393/1449） | `backend/build/reports/jacoco/test/jacocoTestReport.xml`、`test-results.md` 1章 | build-and-test | Met |
| TC-COV-BE-BRANCH | 同上 | バックエンドの分岐カバレッジ 70% 以上 | 91.45%（417/456） | 同上 | build-and-test | Met |
| TC-COV-FE-LINE | 同上 | 画面の行カバレッジ 80% 以上 | 98.73% | `verify` の出力（`frontend/coverage/`）、`test-results.md` 1章 | build-and-test | Met |
| TC-COV-FE-BRANCH | 同上 | 画面の分岐カバレッジ 70% 以上 | 94.02% | 同上 | build-and-test | Met |
| TC-SCOPE-REGRESSION | Testing Contract `obligations.scope_floor`（bugfix） | 不具合を再現する回帰テストがある | `ConcurrentLoginAuditIT` が上限 10 で失敗し、30 で通る | `test-results.md` 2章（コマンド1・3） | build-and-test | Met |
| TC-SCOPE-GREEN | Testing Contract `obligations.scope_floor` | 既存のテストがすべて通る | 単体 387・結合 244・画面 167 件、失敗 0 | `test-results.md` 1章 | build-and-test | Met |
| NFR1 | `requirements.md` NFR1 | 同時 20 件までの成功のログインで、監査の欠落 0 件、接続の待ちの時間切れ 0 件 | N=10・20 とも欠落 0 件・時間切れ 0 件 | `backend/src/test/java/cherry/mastersmith/auth/web/ConcurrentLoginAuditIT.java`、`test-results.md` 2章（コマンド1） | build-and-test | Met |
| NFR2 | `requirements.md` NFR2 | 既定値 30 で、`mem_limit: 1g` を変えずに起動・スモークテスト・FR6.2 の確かめが通る | 未測定（配備した環境が要る） | Deployment Execution の段で、コンテナを起動して確かめる | deployment-execution | Unverified |
| NFR3 | `requirements.md` NFR3 | カバレッジの下限を下げない・除外を増やさない | 下限・除外とも変わらず、実測値も変わらない | `backend/build.gradle.kts`（変更なし）、`test-results.md` 1章 | build-and-test | Met |
| NFR4 | `requirements.md` NFR4 | 新しい設定と記録に秘密情報を含めない | Gitleaks `no leaks found`、`.env.example` はコメントの行だけ | `test-results.md` 1章、`security-test-instructions.md` | build-and-test | Met |
| FR6.2 | `requirements.md` FR6.2 | 使い捨ての環境で k6 の同時 10 件のログインを流し、ログインの数と監査の行の数が一致し、時間切れと監査の失敗のログが0件 | 未測定（配備した環境が要る） | Deployment Execution の段の記録に置く（`perf/README.md` の手順） | deployment-execution | Unverified |

判定のまとめ: Met 9 件、Not Met 0 件、Unverified 2 件。Unverified の2件は、実行の計画に EXECUTE として入っている `deployment-execution` の段が持つ。

## 4. 準備の状態の判定

| 観点 | 判定 |
|---|---|
| ビルド | 準備できている（build-ready） |
| テスト | 準備できている（test-ready）。実行したすべてのテストと検査が通った |
| 配備 | 条件つき。配備の後の確かめ（FR6.2・NFR2）が残る |

ステージ定義の判定では、`Unverified` が2件残るため「成功」とはならない。依頼者の判断を仰ぐ（`test-results.md` 4章）。

## 5. 分かっている制約と残る点

- 同時の要求がプールの上限（30）に達すると、F2 は再び起きうる（README の「既知の制約」、FR5.1）。
- ログインの失敗とログアウトの同時実行は確かめていない（要件の「残る未確定の点」）。
- 要件の網羅（`cross-unit-traceability.md`）では、FR1.4（N/A）、FR3.3（Deferred のまま。ただし実際はコミット `d948544` で満たした）、FR6.1・FR6.2・NFR2（Deferred）が `OK` になっていない。
- `traceability.json` を自動で確かめる検査（traceability のセンサー）は、単位の分割の無い置き場所から単位を導けず、`pass:false` を返す（中身の不足ではない）。

## Sources

- `test-results.md`、`build-instructions.md`、`integration-test-instructions.md`、`performance-test-instructions.md`、`security-test-instructions.md`（このディレクトリ）
- `aidlc/spaces/default/intents/260923-audit-pool-exhaustion/construction/code-generation/code-generation-plan.md`（Testing Contract）、`unit-test-instructions.md`、`code-summary.md`
- `aidlc/spaces/default/intents/260923-audit-pool-exhaustion/inception/requirements-analysis/requirements.md`

## Assumptions & Open Questions

- None.
