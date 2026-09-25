# 要件の網羅の確かめ（cross-unit-traceability）

Intent 260924-followup-fixes は単位の分割が無い（bugfix）。要件（`aidlc/spaces/default/intents/260924-followup-fixes/inception/requirements-analysis/requirements.md`）の FR・NFR を、Code Generation の `aidlc/spaces/default/intents/260924-followup-fixes/construction/code-generation/traceability.json`（設計の段が無いため FR・NFR の ID を直接持つ）と突き合わせた。User Stories の段は無いため AC は無い。

## 判定

**不合格（網羅されていない ID が 2 件）**。32 件のうち 30 件は `OK` で、対象のファイルがすべて実在する。残る2件は `traceability.json` で `OK` ではない:

- FR1.5（警報とダッシュボードは変えない）: `N/A`。変えないことが要件のため、実装のファイルが無い。この段で `git diff develop..HEAD -- docker/monitoring/` に差分が無いことを確かめた。
- FR8.2（負荷の試験で2件目の直しを確かめる）: `Deferred`（この段へ）。この段で `dslMixed` を流して満たした（`test-results.md` 3節、`build-and-test-summary.md` の FR8-LOGIN500）。`traceability.json` は Code Generation の承認済みの成果物のため書き換えていない。

どちらも要件は満たしているが、`traceability.json` の上では `OK` にならないため、承認の場で扱いを確かめる。

## ID ごとの網羅

| ID | traceability.json の状態 | 対象 | ファイルの有無 | 判定 |
|---|---|---|---|---|
| FR1.1 | OK | backend/src/main/java/cherry/mastersmith/config/ObservabilityConfig.java | あり | OK |
| FR1.2 | OK | backend/src/main/java/cherry/mastersmith/common/observability/SanitizingLogRecordExporter.java | あり | OK |
| FR1.3 | OK | backend/src/test/java/cherry/mastersmith/common/observability/JsonLogFormatTest.java | あり | OK |
| FR1.4 | OK | backend/src/test/java/cherry/mastersmith/common/observability/OtlpLogExportIT.java | あり | OK |
| FR1.5 | N/A | 変えないことが要件。docker/monitoring/ の警報とダッシュボードに差分が無いことを Step 18 の git status で確かめた | — | 要確認 |
| FR2.1 | OK | backend/src/main/java/cherry/mastersmith/auth/service/LoginService.java | あり | OK |
| FR2.2 | OK | backend/src/test/java/cherry/mastersmith/auth/service/LoginConcurrencyIT.java | あり | OK |
| FR2.3 | OK | backend/src/test/java/cherry/mastersmith/auth/service/LoginServiceTest.java | あり | OK |
| FR3.1 | OK | perf/k6/scenarios.js | あり | OK |
| FR3.2 | OK | perf/k6/scenarios.js | あり | OK |
| FR4.1 | OK | backend/src/main/java/cherry/mastersmith/common/observability/SingleLineMessageJsonProvider.java | あり | OK |
| FR4.2 | OK | backend/src/main/resources/logback-spring.xml | あり | OK |
| FR4.3 | OK | backend/src/test/java/cherry/mastersmith/common/observability/JsonLogFormatTest.java | あり | OK |
| FR5.1 | OK | compose.yaml | あり | OK |
| FR5.2 | OK | docker/check-container-limits.sh | あり | OK |
| FR5.3 | OK | docker/check-container-limits.sh | あり | OK |
| FR6.1 | OK | .env.targetdb.example | あり | OK |
| FR6.2 | OK | compose.yaml | あり | OK |
| FR6.3 | OK | README.md | あり | OK |
| FR6.4 | OK | .env.example | あり | OK |
| FR7.1 | OK | vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/Modal/Modal.tsx | あり | OK |
| FR7.2 | OK | frontend/src/features/dsl/DslConfirmDialog.tsx | あり | OK |
| FR7.3 | OK | frontend/src/features/dsl/DslConfirmDialog.test.tsx | あり | OK |
| FR7.4 | OK | frontend/src/features/dsl/DslAdminPage.test.tsx | あり | OK |
| FR8.1 | OK | perf/README.md | あり | OK |
| FR8.2 | Deferred | build-and-test の段: 使い捨ての環境で、ロックの状態の行が無い試験用の利用者のまま dslMixed を k6 で流し、ログインの checks の率 1（500 が 0 件）と、loginLoop-user の行の数が V | — | 要確認 |
| NFR1 | OK | backend/src/test/java/cherry/mastersmith/common/observability/OtlpLogExportIT.java | あり | OK |
| NFR2 | OK | backend/build.gradle.kts | あり | OK |
| NFR3 | OK | build.gradle.kts | あり | OK |
| NFR4 | OK | backend/src/test/java/cherry/mastersmith/common/observability/JsonLogFormatTest.java | あり | OK |
| NFR5 | OK | frontend/src/features/dsl/DslConfirmDialog.test.tsx | あり | OK |
| NFR6 | OK | backend/src/test/java/cherry/mastersmith/auth/web/LoginApiIT.java | あり | OK |

## 網羅されていない要素

| ID | 理由 | この段での確かめ |
|---|---|---|
| FR1.5 | 変えないことが要件（`N/A`） | `docker/monitoring/` と `application.yaml` に `develop` からの差分が無い |
| FR8.2 | この段へ回した（`Deferred`） | `dslMixed` のログインの `checks` の率 1、500 が 0 件（`test-results.md` 3節） |

## Sources

- `aidlc/spaces/default/intents/260924-followup-fixes/inception/requirements-analysis/requirements.md`
- `aidlc/spaces/default/intents/260924-followup-fixes/construction/code-generation/traceability.json`
- `aidlc/spaces/default/intents/260924-followup-fixes/construction/build-and-test/test-results.md`
