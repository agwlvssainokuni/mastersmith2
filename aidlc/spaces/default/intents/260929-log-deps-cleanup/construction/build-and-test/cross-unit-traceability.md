# 要件の網羅の確かめ（260929-log-deps-cleanup）

単位の分割は無い（単位なし）。要件（`aidlc/spaces/default/intents/260929-log-deps-cleanup/inception/requirements-analysis/requirements.md`）の FR・NFR を、コード生成の段の `aidlc/spaces/default/intents/260929-log-deps-cleanup/construction/code-generation/traceability.json` と突き合わせた。User Stories の段は無いため AC は無い。

## 判定

**条件つきの合格**: OK が 19 件、N/A が 2 件（依頼者の決定で行わない）、Deferred が 2 件（この段で行って確かめた）。行うと決めた要件で、確かめていないものは無い。コード生成の段の成果物は確定の後のため書き換えず、この段で確かめた分をここに記録する（`project.md` の学び）。

## 要件ごとの網羅

| ID | 状態 | 持ち主 | 対象のファイル | 備考 |
|---|---|---|---|---|
| FR1.1 | OK | code-generation | `backend/src/main/java/cherry/mastersmith/user/service/InitialAdminInitializer.java` | キー `maskedEmail` の伏せ字（Q-B: B） |
| FR1.2 | OK | code-generation | `backend/src/main/java/cherry/mastersmith/user/domain/EmailAddress.java` | `mask` の形（Q-A: A） |
| FR1.3 | OK | code-generation | `README.md` | |
| FR1.4 | OK | code-generation | `backend/src/main/java/cherry/mastersmith/user/service/InitialAdminInitializer.java` | Javadoc |
| FR2.1 | N/A | ― | ― | 要件のレビューの R-01 を受けた依頼者の決定で行わない（監査の失敗の ERROR は既知の例外） |
| FR2.2 | N/A | ― | ― | 同上（README 774 行は変えない） |
| FR3.1 | OK | code-generation | `README.md` | 固定先 `077f5b48ce84cd020ecec2d925836a085f9d9e11`（コミット `ac0d007`） |
| FR3.2 | OK | code-generation | `frontend/e2e/100-app-text-contrast.e2e.ts` | E2E 110 件成功 |
| FR3.3 | OK | code-generation | `README.md` | |
| FR4.1 | OK | code-generation | `gradle/libs.versions.toml` | spotless 8.10.3 |
| FR4.2 | OK | code-generation | `frontend/package-lock.json` | `@types/node` 26.6.3 |
| FR4.3 | OK | code-generation | `.github/dependabot.yml` | typescript の大きな版だけ ignore |
| FR4.4 | OK | code-generation | `.github/dependabot.yml` | jackson-bom はすべての版を ignore |
| FR4.5 | OK | code-generation | `gradle/libs.versions.toml` | #5・#20 を取り込まない |
| FR5.1 | OK | code-generation | `docker/monitoring/provisioning/alerting/mastersmith.yaml` | 500 ms |
| FR5.2 | OK | code-generation | `docker/monitoring/dashboards/mastersmith-overview.json` | 5 か所 |
| FR5.3 | OK | code-generation | `README.md` | |
| FR6.1 | OK（この段で確かめた） | build-and-test | `aidlc/spaces/default/memory/team.md` | コード生成では Deferred。この段でコミット `f5fc7ed` で直した（Q3: A） |
| FR6.2 | OK（この段で確かめた） | build-and-test | `aidlc/spaces/default/memory/team.md` | Dependabot の方針は team.md に足していない |
| NFR1 | OK | code-generation | `backend/src/test/java/cherry/mastersmith/user/service/InitialAdminInitializerTest.java` | 監査の失敗の経路は FR2 と同じ決定で対象の外 |
| NFR2 | OK | build-and-test | `aidlc/spaces/default/intents/260929-log-deps-cleanup/construction/build-and-test/test-results.md` | この段でも verify を3回流した |
| NFR3 | OK | code-generation | `aidlc/spaces/default/intents/260929-log-deps-cleanup/construction/code-generation/code-summary.md` | E2E 110 件 |
| NFR4 | OK | code-generation | `aidlc/spaces/default/intents/260929-log-deps-cleanup/construction/code-generation/code-summary.md` | 読み込みと式の評価まで（Q-D: A） |

## 承認の場で扱うこと

- FR2.1・FR2.2 は行わない（依頼者の決定）。
- 要件の外で、この段で取り込んだもの: 不安定なテスト3件の直し（`48a4c5c`）、otel-collector 0.162.0（`2ac7df1`）、spotbugs の Gradle プラグイン 6.5.12（`8489241`）。
