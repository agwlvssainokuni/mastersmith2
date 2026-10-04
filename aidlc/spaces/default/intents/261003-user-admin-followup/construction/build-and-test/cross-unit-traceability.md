# 要件の網羅の確かめ（261003-user-admin-followup）

- 対象: 要件定義（`inception/requirements-analysis/requirements.md`）のすべての FR・NFR。この Intent では User Stories の段を飛ばしたため（`aidlc-state.md` の Stages to Skip の 2.4）、三つ組の AC は無い。
- 照らし合わせる先: zero-Unit のため、段の水準の `construction/code-generation/traceability.json` だけ（単位ごとの traceability.json は無い）。機能設計・NFR 要件の段も飛ばしたため、`project.md` の学びの「2段の連鎖」ではなく、要件の ID が traceability.json に直接載っている。
- 確かめたこと: 各 ID が状態 `OK` で載り、target のファイルが存在すること（2026-10-04、`test -e` で確かめた）。

## 判定

**不合格（条件つき）**: 24 件のうち 22 件が `OK` で、target のファイルがすべて存在する。2 件（FR4.2・FR9.1）は traceability.json で `Deferred` のため、`OK` の条件を満たさない。

- FR4.2 はこの段で確かめた（下の 2節）。ただし、コード生成の成果物は確定の後のため書き換えず、ここに記録する（`project.md` の学び 2026-09-25 と同じ扱い）。(c) は Unverified。
- FR9.1 は配備の段（Deployment Pipeline・Deployment Execution）が持つ。この段では確かめない。
- どちらも承認の場で示す。

## 1. ID ごとの網羅

| ID | traceability.json の状態 | 持ち主（段・単位） | target | ファイルの有無 | 判定 |
|---|---|---|---|---|---|
| FR1.1 | OK | code-generation（zero-Unit） | `vendor/make-you-chic-ui` | あり（固定先 `e82b651`） | 網羅 |
| FR1.2 | OK | code-generation | `frontend/src/features/useradmin/UserAdminPage.tsx` | あり | 網羅 |
| FR1.3 | OK | code-generation | `frontend/e2e/120-user-admin-accessibility.e2e.ts` | あり | 網羅 |
| FR1.4 | OK | code-generation | `frontend/e2e/060-invitation-accessibility.e2e.ts` | あり | 網羅 |
| FR2.1 | OK | code-generation | `frontend/src/features/useradmin/UserRowActions.tsx` | あり | 網羅 |
| FR2.2 | OK | code-generation | `frontend/e2e/support/overflow.ts` | あり | 網羅 |
| FR3.1 | OK | code-generation | `frontend/src/features/useradmin/EditProfileDialog.tsx` | あり | 網羅 |
| FR3.2 | OK | code-generation | `frontend/src/features/useradmin/EditProfileDialog.test.tsx` | あり | 網羅 |
| FR4.1 | OK | code-generation | `perf/k6/scenarios.js` | あり | 網羅 |
| FR4.2 | **Deferred** | build-and-test | （Build and Test の負荷の試験） | — | **未網羅**（この段で確かめた。2節） |
| FR4.3 | OK | code-generation | `perf/README.md` | あり | 網羅 |
| FR5.1 | OK | code-generation | `backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminOperationsApiIT.java` | あり | 網羅 |
| FR6.1 | OK | code-generation | `backend/src/test/java/cherry/mastersmith/mail/config/MailConfigurationIT.java` | あり | 網羅 |
| FR6.2 | OK | code-generation | `backend/src/test/java/cherry/mastersmith/mail/config/MailConfigurationIT.java` | あり | 網羅 |
| FR7.1 | OK | code-generation | `perf/README.md` | あり | 網羅 |
| FR8.1 | OK | code-generation | `backend/src/test/java/cherry/mastersmith/user/service/UserUniqueViolationSecretLeakIT.java` | あり | 網羅 |
| FR8.2 | OK | code-generation | `backend/src/main/resources/application.yaml` | あり | 網羅 |
| FR8.3 | OK | code-generation | `backend/src/test/java/cherry/mastersmith/invitation/service/InvitationUniqueViolationSecretLeakIT.java` | あり | 網羅 |
| FR9.1 | **Deferred** | deployment-pipeline・deployment-execution | （配備） | — | **未網羅**（後の段が持つ） |
| NFR1 | OK | code-generation | `frontend/e2e/110-user-admin-flow.e2e.ts` | あり | 網羅 |
| NFR2 | OK | code-generation | `backend/src/main/java/cherry/mastersmith/common/error/web/UniqueViolations.java` | あり | 網羅 |
| NFR3 | OK | code-generation | `build.gradle.kts` | あり | 網羅 |
| NFR4 | OK | code-generation | `backend/src/test/java/cherry/mastersmith/common/error/web/GlobalExceptionHandlerTest.java` | あり | 網羅 |
| NFR5 | OK | code-generation | `frontend/src/features/invitation/InvitationAdminPage.test.tsx` | あり | 網羅 |

- 数: FR 19 件（OK 17・Deferred 2）、NFR 5 件（OK 5）。合わせて 24 件、OK 22・Deferred 2。
- NFR5 の target は G1 の回帰のテストを指しているが、NFR5 は FR1・FR2・FR3・FR8 の再現のテストの全体にかかる。全体の確かめは `test-results.md` の Target Verification Matrix の NFR5 の行（`a7fdc2c`・`fcf5dee` に直しと回帰のテストを同梱）で行った。

## 2. 網羅されていない ID

| ID | 理由 | この段での扱い | 行き先 |
|---|---|---|---|
| FR4.2 | コード生成の段では負荷をかけないため `Deferred`（計画 7.1節、依頼者の決定 D2: A） | 使い捨ての環境で流した。(a) Met（時間切れの累計 662、待ちの最大 5,049.8 ms）・(b) Met（警報3件が `Alerting`）・(c) Unverified（BUSY 0 件）。`t1-load-test-results.md` | (c) は依頼者の決定で次の Intent へ持ち越す |
| FR9.1 | 配備は後の段の仕事 | 確かめていない | Deployment Pipeline・Deployment Execution（`aidlc-state.md` の 4.1・4.3） |

## Sources

- `aidlc/spaces/default/intents/261003-user-admin-followup/inception/requirements-analysis/requirements.md`
- `aidlc/spaces/default/intents/261003-user-admin-followup/construction/code-generation/traceability.json`
- `aidlc/spaces/default/intents/261003-user-admin-followup/aidlc-state.md`（Scope Configuration）
- `aidlc/spaces/default/intents/261003-user-admin-followup/construction/build-and-test/t1-load-test-results.md`
- `aidlc/spaces/default/intents/261003-user-admin-followup/construction/build-and-test/test-results.md`（8節）
- `aidlc/spaces/default/intents/261003-user-admin-followup/construction/code-generation/gate-decisions.md`（2節 R-07）

## Assumptions & Open Questions

- FR4.2 の `Deferred` を、確定済みの traceability.json を書き換えずにこの文書で「この段で確かめた」と記録する扱いでよいかを、承認の場で確かめる。
- FR4.2 の (c) の持ち越しは、`build-and-test-summary.md` の 6節の1と同じ論点。
