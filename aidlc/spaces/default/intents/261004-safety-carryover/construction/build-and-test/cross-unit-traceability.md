# 要件の網羅（Intent 261004-safety-carryover）

要件 `aidlc/spaces/default/intents/261004-safety-carryover/inception/requirements-analysis/requirements.md` の FR・NFR を、Code Generation の `aidlc/spaces/default/intents/261004-safety-carryover/construction/code-generation/traceability.json`（単位の分割なし。上流は `code-generation-plan.md`・`unit-test-instructions.md`・`code-summary.md`）と、この段の確かめで突き合わせた。User Stories の段は無いため AC は無い。

## 判定

**合格（条件つき）**。FR8.1〜FR8.3（配備）だけが網羅されていない。持ち主は Deployment Pipeline・Deployment Execution の段で、承認の場で示す。`traceability.json` で `Deferred` だった FR2・FR6・NFR4・NFR5・NFR7 は、この段で確かめて Met だった。確定した Code Generation の成果物は書き換えず、ここに記録する（`project.md` の学び）。

## 網羅の表

| ID | traceability.json | 対象のファイル（実在を確かめた） | この段の確かめ | 結果 |
|---|---|---|---|---|
| FR1.1〜FR1.8（FR1.2a・FR1.6a・FR1.6b を含む） | OK | `UserAccountService.java`・`InitialAdminInitializer.java`・`AuditEventFactory.java`・`AuditEventListener.java`・`InitialAdminRescueIT.java` | verify で通過、使い捨ての環境で作成の監査の行を確かめた | 網羅 |
| FR1.9 | OK | `README.md` | — | 網羅 |
| FR2.1〜FR2.4（FR2.2a を含む） | Deferred（Build and Test） | — | 9回流し、判定と条件を記録（test-results.md 2節） | この段で網羅 |
| FR3.1 | OK | `UserAdminBusyLogTraceIT.java` | verify で通過 | 網羅 |
| FR3.2 | N/A（負荷では確かめない） | — | — | 対象外（依頼者の決定 Q4） |
| FR4.1・FR4.1a・FR4.2 | OK | `FilterExceptionErrorLogIT.java`・`application.yaml` | verify で通過、実機でも確かめた（test-results.md 3節） | 網羅 |
| FR4.1b・FR4.3 | N/A（再現でき、原因も見立てどおり） | — | — | 対象外 |
| FR5.1 | OK | `README.md` | — | 網羅 |
| FR6.1・FR6.2 | Deferred（Build and Test） | `aidlc/spaces/default/memory/team.md` | 依頼者の文言で直した | この段で網羅 |
| FR7.1 | N/A（仕組みは変えない） | — | — | 対象外（依頼者の決定 Q7） |
| FR7.2〜FR7.4 | OK | `Dockerfile`・`.github/dependabot.yml` | ダイジェスト付きの FROM でイメージを作れた | 網羅 |
| FR8.1〜FR8.3 | Deferred（配備の段） | — | — | **未網羅（Deployment Pipeline・Deployment Execution が持ち主）** |
| NFR1 | OK | `InitialAdminSecretLeakIT.java` | 使い捨ての環境のログでも 0 | 網羅 |
| NFR2 | OK | `InitialAdminRescueIT.java` | — | 網羅 |
| NFR3 | OK | `UserAccountServiceTest.java` | 起動の時間を記録 | 網羅 |
| NFR4・NFR5 | Deferred（Build and Test） | — | verify の実測で判定 | この段で網羅 |
| NFR6 | OK | `FilterExceptionErrorLogIT.java` | — | 網羅 |
| NFR7 | Deferred（Build and Test） | — | ログインの p95 を判定 | この段で網羅 |
