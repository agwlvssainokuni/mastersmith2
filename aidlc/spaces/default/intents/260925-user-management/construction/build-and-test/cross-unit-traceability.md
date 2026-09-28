# 単位をまたぐ網羅の確かめ（cross-unit-traceability）

Intent `260925-user-management` の要件（`inception/requirements-analysis/requirements.md` のすべての FR・NFR）とストーリーの受け入れ基準（`inception/user-stories/stories.md` の3段の AC）が、各単位のコード生成の記録（`construction/u*/code-generation/traceability.json`）まで、status `OK` と実在する target でつながるかを確かめました。

- 数：138 件（FR 43・NFR 11・AC 84）
- 読んだもの：単位ごとの8つのファイル（stage-level の `construction/code-generation/traceability.json` はありません）
- たどり方：traceability.json は要件の ID を直接持たず、単位ごとの ID で持っています。そのため、要件 → 機能設計の BR・NFR 要件の枝番 → code-generation の traceability.json、という2段の連鎖でたどりました（project.md の Way of Working）。

**判定: 合格。ID の単位の未網羅は 0 件です。ただし、NFR5・NFR6・NFR9・NFR10 には後の段への持ち越しがあります。**

- 持ち越しは、承認の場で扱いを確かめてもらう対象です。
- 持ち越しの先の段は、どれもこの Intent の流れにあります。

## 判定

要件の ID の単位では、138 件すべてが少なくとも1つの単位の code-generation で status OK につながり、target のファイルも実在した（未網羅 0 件）。ただし、NFR5・NFR6・NFR9・NFR10 の4件は、要件の中の一部の枝番が Deferred のまま持ち越されている（計 24 項目。持ち主は performance-validation・observability-setup・deployment-pipeline・deployment-execution・feedback-optimization で、いずれもこの Intent の流れで EXECUTE の段）。特に NFR6 は要件の本体である応答時間の 95 パーセンタイル（招待・送り直し 5 秒、ほか 1 秒）がすべて Deferred で、code-generation で OK なのは時間切れの設定・指標の名前など周辺の枝番だけであり、NFR5 も後半（負荷の試験で接続プールが尽きないこと）が Deferred である。ID の単位の判定では網羅とするが、実質は「部分網羅（持ち越しあり）」として扱うのが妥当である。また FR の4件（FR4.3・FR5.5・FR7.1・FR7.4）は traceability.json ではなく文書の記述でたどった連鎖である。

## 網羅の表

| ID | 種類 | つながる単位と単位の ID | code-generation の status | target のファイル（実在の有無） | 判定 |
|---|---|---|---|---|---|
| FR1.1 | FR | U3 BR2.6 | OK（1件） | backend/src/test/java/cherry/mastersmith/invitation/web/InvitationAdminApiIT.java（実在） | 網羅 |
| FR1.2 | FR | U1 BR3.1・U1 BR3.4・U3 BR1.1・U3 BR1.2 | OK（4件） | backend/src/main/java/cherry/mastersmith/mail/domain/MailAddressRule.java（実在） | 網羅 |
| FR1.3 | FR | U3 BR7.7 | OK（1件） | backend/src/test/java/cherry/mastersmith/invitation/web/InvitedPersonAuthenticationIT.java（実在） | 網羅 |
| FR1.4 | FR | U3 BR2.1・U3 BR2.2 | OK（2件） | backend/src/test/java/cherry/mastersmith/invitation/web/InvitationAdminApiIT.java（実在） | 網羅 |
| FR1.5 | FR | U3 BR1.6・U3 BR2.6・U3 BR3.1・U3 BR3.3 | OK（4件） | backend/src/test/java/cherry/mastersmith/invitation/service/InvitationSettingsTest.java（実在） | 網羅 |
| FR1.6 | FR | U3 BR2.6・U3 BR5.3 | OK（2件） | backend/src/test/java/cherry/mastersmith/invitation/web/InvitationAdminApiIT.java（実在） | 網羅 |
| FR1.7 | FR | U3 BR1.3・U3 BR3.4 | OK（2件） | backend/src/test/java/cherry/mastersmith/invitation/domain/BaseUrlRuleTest.java（実在） | 網羅 |
| FR1.8 | FR | U1 BR1.2・U1 BR1.3・U1 BR1.7・U3 BR1.3・U3 BR1.4・U3 BR1.5 | OK（6件） | backend/src/main/java/cherry/mastersmith/mail/config/MailSettings.java（実在） | 網羅 |
| FR2.1 | FR | U1 BR2.1・U1 BR2.3・U1 BR4.2・U1 BR5.1・U3 BR4.2・U3 BR10.1 | OK（6件） | backend/src/main/java/cherry/mastersmith/mail/template/MailTemplateRegistry.java（実在） | 網羅 |
| FR2.2 | FR | U1 BR2.4・U3 BR1.6・U3 BR4.2・U3 BR10.2 | OK（4件） | backend/src/test/java/cherry/mastersmith/mail/template/MailTemplateLintTest.java（実在） | 網羅 |
| FR2.3 | FR | U1 BR5.4・U3 BR4.1 | OK（2件） | backend/src/test/java/cherry/mastersmith/mail/MailBoundaryArchitectureTest.java（実在） | 網羅 |
| FR2.4 | FR | U1 BR5.2・U1 BR5.3・U1 BR6.4・U3 BR4.3・U3 BR4.5 | OK（5件） | backend/src/main/java/cherry/mastersmith/mail/transport/SmtpMailTransport.java（実在） | 網羅 |
| FR2.5 | FR | U1 BR1.1・U1 BR1.2・U1 BR1.3・U1 BR1.4・U1 BR1.7 | OK（5件） | backend/src/main/java/cherry/mastersmith/mail/config/MastersmithMailProperties.java（実在） | 網羅 |
| FR2.6 | FR | U1 BR1.3・U1 BR6.1・U1 BR6.2・U3 BR4.5 | OK（4件） | backend/src/main/java/cherry/mastersmith/mail/config/MailSettings.java（実在） | 網羅 |
| FR3.1 | FR | U3 BR5.1・U3 BR5.2・U3 BR5.3 | OK（3件） | backend/src/test/java/cherry/mastersmith/invitation/repository/InvitationRepositoryIT.java（実在） | 網羅 |
| FR3.2 | FR | U3 BR6.1 | OK（1件） | backend/src/test/java/cherry/mastersmith/invitation/service/InvitationServiceTest.java（実在） | 網羅 |
| FR3.3 | FR | U3 BR6.2 | OK（1件） | backend/src/test/java/cherry/mastersmith/invitation/web/InvitationAdminApiIT.java（実在） | 網羅 |
| FR4.1 | FR | U3 BR7.1・U3 BR7.5 | OK（2件） | backend/src/test/java/cherry/mastersmith/invitation/web/RegistrationApiIT.java（実在） | 網羅 |
| FR4.2 | FR | U3 BR7.2 | OK（1件） | backend/src/test/java/cherry/mastersmith/invitation/domain/RegistrationValidationTest.java（実在） | 網羅 |
| FR4.3 | FR | U6 AC3.2.1・U6 AC3.2.17・U6 AC3.2.18・U4 AC3.2.17・U4 AC3.2.18（U4 の functional-spec.md 11節の対応表で AC へ） | OK（5件） | frontend/src/features/registration/useRegistration.ts（実在） | 網羅 |
| FR4.4 | FR | U2 BR4.1・U2 BR5.1・U3 BR7.2 | OK（3件） | backend/src/main/java/cherry/mastersmith/user/domain/PasswordChangeValidation.java（実在） | 網羅 |
| FR4.5 | FR | U2 BR5.1・U2 BR5.3・U3 BR7.3 | OK（3件） | backend/src/main/java/cherry/mastersmith/user/service/UserAccountService.java（実在） | 網羅 |
| FR4.6 | FR | U3 BR6.4・U3 BR7.4 | OK（2件） | backend/src/test/java/cherry/mastersmith/invitation/service/RegistrationConcurrencyIT.java（実在） | 網羅 |
| FR4.7 | FR | U3 BR7.3 | OK（1件） | backend/src/test/java/cherry/mastersmith/invitation/web/RegistrationApiIT.java（実在） | 網羅 |
| FR5.1 | FR | U2 BR3.1・U2 BR3.2・U2 BR3.3 | OK（3件） | backend/src/main/java/cherry/mastersmith/user/service/UserPreferencesService.java（実在） | 網羅 |
| FR5.2 | FR | U2 BR1.1・U2 BR1.2・U2 BR1.3・U2 BR2.1・U2 BR3.2 | OK（5件） | backend/src/main/java/cherry/mastersmith/user/domain/DisplayName.java（実在） | 網羅 |
| FR5.3 | FR | U2 BR3.3 | OK（1件） | backend/src/main/java/cherry/mastersmith/user/service/UserPreferencesService.java（実在） | 網羅 |
| FR5.4 | FR | U2 BR3.3・U2 BR6.1 | OK（2件） | backend/src/main/java/cherry/mastersmith/user/service/UserPreferencesService.java（実在） | 網羅 |
| FR5.5 | FR | U4 AC4.1.6（U4 の functional-spec.md 11節で AC へ） | OK（1件） | frontend/src/app/display-settings/resolveDisplaySettings.ts（実在） | 網羅 |
| FR6.1 | FR | U2 BR4.1・U2 BR4.3 | OK（2件） | backend/src/main/java/cherry/mastersmith/user/domain/PasswordChangeValidation.java（実在） | 網羅 |
| FR6.2 | FR | U2 BR4.1・U2 BR4.2・U2 BR4.4 | OK（3件） | backend/src/main/java/cherry/mastersmith/user/domain/PasswordChangeValidation.java（実在） | 網羅 |
| FR6.3 | FR | U2 BR4.5 | OK（1件） | backend/src/test/java/cherry/mastersmith/user/web/MePasswordApiIT.java（実在） | 網羅 |
| FR7.1 | FR | U4 CR1.1・U5 CR1.1・U7 CR1.1・U2 CR1.1（stories.md の CR1＝FR7 で CR1.1 へ） | OK（4件） | frontend/src/app/display-settings/displaySettingsStore.ts（実在） | 網羅 |
| FR7.2 | FR | U2 BR8.3 | OK（1件） | backend/src/test/java/cherry/mastersmith/user/web/MePasswordApiIT.java（実在） | 網羅 |
| FR7.3 | FR | U1 BR4.1・U1 BR4.3・U3 BR4.2 | OK（3件） | backend/src/main/java/cherry/mastersmith/mail/template/MailTemplateRegistry.java（実在） | 網羅 |
| FR7.4 | FR | U5 AC1.1.1（U4 の frontend-components.md の U5 の行で AC へ） | OK（1件） | frontend/src/features/invitation/useInvitationAdmin.ts（実在） | 網羅 |
| FR8.1 | FR | U8 FR8.1・U8 BR1.1・U8 BR1.2・U8 BR1.6・U8 BR3.1・U8 BR3.2 | OK（6件） | backend/src/main/java/cherry/mastersmith/appearance/web/AppearanceController.java（実在） | 網羅 |
| FR8.2 | FR | U8 FR8.2・U8 BR1.1・U8 BR1.2・U8 BR1.3・U8 BR1.4・U8 BR1.5・U8 BR1.7・U8 BR2.1 | OK（8件） | backend/src/test/java/cherry/mastersmith/appearance/web/AppearanceStartupIT.java（実在） | 網羅 |
| FR9.1 | FR | U2 BR7.2・U2 BR7.4・U3 BR8.1・U3 BR8.2・U3 BR8.3・U3 BR8.6・U8 BR3.5 | OK（7件） | backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventFactory.java（実在） | 網羅 |
| FR9.2 | FR | U1 BR6.4・U2 BR3.5・U3 BR4.5・U3 BR8.1 | OK（4件） | backend/src/main/java/cherry/mastersmith/mail/service/SmtpMailSender.java（実在） | 網羅 |
| FR10.1 | FR | U3 BR9.1 | OK（1件） | backend/src/test/java/cherry/mastersmith/invitation/web/InvitationAdminApiIT.java（実在） | 網羅 |
| FR10.2 | FR | U2 BR8.1 | OK（1件） | backend/src/main/java/cherry/mastersmith/user/web/MeController.java（実在） | 網羅 |
| FR10.3 | FR | U3 BR9.2 | OK（1件） | backend/src/test/java/cherry/mastersmith/invitation/web/RegistrationPublicScopeIT.java（実在） | 網羅 |
| NFR1 | NFR | U1 NFR1.1・U1 NFR1.2・U3 NFR1.1・U3 NFR1.2・U3 NFR1.3・U3 NFR1.4・U3 NFR1.5・U3 NFR1.6・U5 NFR1.1・U6 NFR1.1・U6 NFR1.2・U6 NFR1.3・U6 NFR1.4・U6 NFR1.5 | OK（14件） | backend/src/test/java/cherry/mastersmith/mail/service/MailSecretLeakIT.java（実在） | 網羅 |
| NFR2 | NFR | U1 NFR2.1・U1 NFR2.2・U1 NFR2.3・U1 NFR2.4・U1 NFR2.5・U1 NFR2.6・U1 NFR2.7・U1 NFR2.8・U1 NFR2.9・U2 NFR2.1・U2 NFR2.2・U2 NFR2.3・U2 NFR2.4・U3 NFR2.1・U3 NFR2.2・U3 NFR2.3・U4 NFR2.1・U4 NFR2.2・U5 NFR2.1・U6 NFR2.1・U7 NFR2.1 | OK（21件） | backend/src/test/java/cherry/mastersmith/mail/service/MailSecretLeakIT.java（実在） | 網羅 |
| NFR3 | NFR | U3 NFR3.1・U3 NFR3.2・U3 NFR3.3・U6 NFR3.1 | OK（4件） | backend/src/test/java/cherry/mastersmith/invitation/web/RegistrationApiIT.java（実在） | 網羅 |
| NFR4 | NFR | U2 NFR4.1・U2 NFR4.2・U2 NFR4.3・U2 NFR4.4・U2 NFR4.5・U3 NFR4.1・U3 NFR4.2・U3 NFR4.3・U3 NFR4.4・U3 NFR4.5・U8 NFR4.1・U8 NFR4.2・U8 NFR4.3・U8 NFR4.4・U8 NFR4.5・U8 NFR4.6・U8 NFR4.7・U8 NFR4.8 | OK（18件） | backend/src/test/java/cherry/mastersmith/user/web/MePreferencesApiIT.java（実在） | 網羅 |
| NFR5 | NFR | U1 NFR5.1・U2 NFR5.1・U3 NFR5.1・U3 NFR5.2・U3 NFR5.4・U8 NFR5.1・U8 NFR5.2 | OK（7件）・Deferred（3件） | backend/src/test/java/cherry/mastersmith/mail/MailBoundaryArchitectureTest.java（実在） | 網羅（一部の枝番は持ち越し） |
| NFR6 | NFR | U1 NFR6.1・U1 NFR6.2・U1 NFR6.3・U1 NFR6.4・U1 NFR6.5・U2 NFR6.5・U2 NFR6.6・U3 NFR6.6・U3 NFR6.10・U4 NFR6.1・U4 NFR6.2・U4 NFR6.3・U4 NFR6.4・U4 NFR6.5・U5 NFR6.1・U5 NFR6.2・U5 NFR6.3・U5 NFR6.4・U5 NFR6.5・U5 NFR6.6・U6 NFR6.1・U6 NFR6.2・U6 NFR6.3・U7 NFR6.1・U7 NFR6.2・U7 NFR6.3・U7 NFR6.4・U7 NFR6.5・U7 NFR6.6・U8 NFR6.2・U8 NFR6.3・U8 NFR6.4・U8 NFR6.6 | OK（33件）・Deferred（17件） | backend/src/main/resources/application.yaml（実在） | 網羅（一部の枝番は持ち越し） |
| NFR7 | NFR | U4 NFR7.1・U4 NFR7.2・U4 NFR7.3・U4 NFR7.4・U4 NFR7.5・U5 NFR7.1・U5 NFR7.2・U5 NFR7.3・U5 NFR7.4・U6 NFR7.1・U6 NFR7.2・U6 NFR7.3・U6 NFR7.4・U6 NFR7.5・U7 NFR7.1・U7 NFR7.2・U7 NFR7.3・U7 NFR7.4・U7 NFR7.5 | OK（19件） | frontend/e2e/support/axe.ts（実在） | 網羅 |
| NFR8 | NFR | U1 NFR8.1・U1 NFR8.2・U1 NFR8.3・U2 NFR8.1・U3 NFR8.1・U3 NFR8.2・U4 NFR8.1・U4 NFR8.2・U5 NFR8.1・U5 NFR8.2・U6 NFR8.1・U6 NFR8.2・U7 NFR8.1・U7 NFR8.2 | OK（14件） | backend/src/main/java/cherry/mastersmith/mail/template/MailTemplateRegistry.java（実在） | 網羅 |
| NFR9 | NFR | U1 NFR9.1・U1 NFR9.2・U1 NFR9.3・U1 NFR9.4・U1 NFR9.5・U2 NFR9.1・U2 NFR9.2・U2 NFR9.3・U2 NFR9.4・U2 NFR9.5・U2 NFR9.6・U2 NFR9.7・U2 NFR9.8・U3 NFR9.1・U3 NFR9.2・U3 NFR9.3・U3 NFR9.4・U3 NFR9.5・U3 NFR9.6・U3 NFR9.7・U3 NFR9.8・U3 NFR9.9・U3 NFR9.10・U3 NFR9.12・U3 NFR9.13・U4 NFR9.1・U4 NFR9.2・U4 NFR9.3・U4 NFR9.4・U4 NFR9.5・U4 NFR9.6・U4 NFR9.7・U4 NFR9.8・U4 NFR9.9・U4 NFR9.10・U4 NFR9.11・U5 NFR9.1・U5 NFR9.2・U5 NFR9.3・U5 NFR9.4・U5 NFR9.5・U5 NFR9.6・U5 NFR9.7・U5 NFR9.8・U5 NFR9.9・U6 NFR9.1・U6 NFR9.2・U6 NFR9.3・U6 NFR9.4・U6 NFR9.5・U6 NFR9.6・U6 NFR9.7・U6 NFR9.8・U6 NFR9.9・U6 NFR9.10・U6 NFR9.11・U7 NFR9.1・U7 NFR9.2・U7 NFR9.3・U7 NFR9.4・U7 NFR9.5・U7 NFR9.6・U7 NFR9.7・U7 NFR9.8・U7 NFR9.9・U7 NFR9.10・U8 NFR9.1・U8 NFR9.2・U8 NFR9.4・U8 NFR9.5・U8 NFR9.6・U8 NFR9.7・U8 NFR9.8 | OK（73件）・Deferred（2件） | backend/src/test/java/cherry/mastersmith/mail/transport/MailSendIT.java（実在） | 網羅（一部の枝番は持ち越し） |
| NFR10 | NFR | U2 NFR10.1・U2 NFR10.2・U2 NFR10.4・U3 NFR10.1・U3 NFR10.2・U3 NFR10.3 | OK（6件）・Deferred（2件） | backend/src/test/java/cherry/mastersmith/user/repository/V7MigrationIT.java（実在） | 網羅（一部の枝番は持ち越し） |
| NFR11 | NFR | U1 NFR11.1・U1 NFR11.2 | OK（2件） | compose.yaml（実在） | 網羅 |
| AC1.1.1 | AC | U5 AC1.1.1 | OK（U5）・Deferred（U3・U4） | frontend/src/features/invitation/useInvitationAdmin.ts（実在） | 網羅 |
| AC1.1.2 | AC | U3 AC1.1.2・U5 AC1.1.2 | OK（U3・U5）・Deferred（U4） | backend/src/test/java/cherry/mastersmith/invitation/web/InvitationAdminApiIT.java（実在） | 網羅 |
| AC1.1.3 | AC | U3 AC1.1.3・U5 AC1.1.3 | OK（U3・U5）・Deferred（U4） | backend/src/test/java/cherry/mastersmith/invitation/web/InvitationAdminApiIT.java（実在） | 網羅 |
| AC1.1.4 | AC | U3 AC1.1.4・U5 AC1.1.4 | OK（U3・U5）・Deferred（U4） | backend/src/test/java/cherry/mastersmith/invitation/web/InvitationAdminApiIT.java（実在） | 網羅 |
| AC1.1.5 | AC | U3 AC1.1.5・U5 AC1.1.5 | OK（U3・U5）・Deferred（U4） | backend/src/test/java/cherry/mastersmith/invitation/web/InvitationSendFailureIT.java（実在） | 網羅 |
| AC1.1.6 | AC | U3 AC1.1.6・U5 AC1.1.6 | OK（U3・U5）・Deferred（U4） | backend/src/test/java/cherry/mastersmith/invitation/web/InvitationAdminApiIT.java（実在） | 網羅 |
| AC1.1.7 | AC | U3 AC1.1.7・U5 AC1.1.7 | OK（U3・U5）・Deferred（U4） | backend/src/test/java/cherry/mastersmith/invitation/web/InvitationSendFailureIT.java（実在） | 網羅 |
| AC1.1.8 | AC | U5 AC1.1.8 | OK（U5）・Deferred（U3・U4） | frontend/src/features/invitation/useInvitationAdmin.ts（実在） | 網羅 |
| AC1.1.9 | AC | U5 AC1.1.9 | OK（U5）・Deferred（U3・U4） | frontend/src/features/invitation/InviteDialog.tsx（実在） | 網羅 |
| AC1.1.10 | AC | U3 AC1.1.10・U5 AC1.1.10 | OK（U3・U5）・Deferred（U4） | backend/src/test/java/cherry/mastersmith/invitation/web/InvitationAdminApiIT.java（実在） | 網羅 |
| AC1.1.11 | AC | U3 AC1.1.11 | OK（U3）・Deferred（U4・U5） | backend/src/test/java/cherry/mastersmith/invitation/web/InvitationMailIT.java（実在） | 網羅 |
| AC1.1.12 | AC | U3 AC1.1.12 | OK（U3）・Deferred（U4・U5） | backend/src/test/java/cherry/mastersmith/invitation/web/InvitationSendFailureIT.java（実在） | 網羅 |
| AC1.1.13 | AC | U3 AC1.1.13 | OK（U3）・Deferred（U4・U5） | backend/src/test/java/cherry/mastersmith/invitation/service/InvitationConcurrencyIT.java（実在） | 網羅 |
| AC2.1.1 | AC | U3 AC2.1.1・U5 AC2.1.1 | OK（U3・U5） | backend/src/test/java/cherry/mastersmith/invitation/web/InvitationAdminApiIT.java（実在） | 網羅 |
| AC2.1.2 | AC | U3 AC2.1.2・U5 AC2.1.2 | OK（U3・U5） | backend/src/test/java/cherry/mastersmith/invitation/web/InvitationAdminApiIT.java（実在） | 網羅 |
| AC2.1.3 | AC | U3 AC2.1.3 | OK（U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/invitation/web/InvitationAdminApiIT.java（実在） | 網羅 |
| AC2.1.4 | AC | U5 AC2.1.4 | OK（U5）・Deferred（U3） | frontend/src/features/invitation/InvitationList.tsx（実在） | 網羅 |
| AC2.1.5 | AC | U3 AC2.1.5・U5 AC2.1.5 | OK（U3・U5） | backend/src/test/java/cherry/mastersmith/invitation/web/InvitationAdminApiIT.java（実在） | 網羅 |
| AC2.1.6 | AC | U5 AC2.1.6 | OK（U5）・Deferred（U3） | frontend/src/features/invitation/InvitationList.tsx（実在） | 網羅 |
| AC2.1.7 | AC | U3 AC2.1.7・U5 AC2.1.7 | OK（U3・U5） | backend/src/test/java/cherry/mastersmith/invitation/web/InvitationAdminApiIT.java（実在） | 網羅 |
| AC2.1.8 | AC | U3 AC2.1.8・U5 AC2.1.8 | OK（U3・U5） | backend/src/test/java/cherry/mastersmith/invitation/web/InvitationAdminApiIT.java（実在） | 網羅 |
| AC2.2.1 | AC | U3 AC2.2.1 | OK（U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/invitation/web/RegistrationApiIT.java（実在） | 網羅 |
| AC2.2.2 | AC | U3 AC2.2.2・U5 AC2.2.2 | OK（U3・U5） | backend/src/test/java/cherry/mastersmith/invitation/web/InvitationAdminApiIT.java（実在） | 網羅 |
| AC2.2.3 | AC | U3 AC2.2.3・U5 AC2.2.3 | OK（U3・U5） | backend/src/test/java/cherry/mastersmith/invitation/service/InvitationSendResultIT.java（実在） | 網羅 |
| AC2.2.4 | AC | U3 AC2.2.4・U5 AC2.2.4 | OK（U3・U5） | backend/src/test/java/cherry/mastersmith/invitation/web/InvitationAdminApiIT.java（実在） | 網羅 |
| AC2.2.5 | AC | U3 AC2.2.5 | OK（U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/invitation/service/InvitationServiceTest.java（実在） | 網羅 |
| AC2.2.6 | AC | U3 AC2.2.6 | OK（U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/invitation/web/RegistrationApiIT.java（実在） | 網羅 |
| AC2.2.7 | AC | U3 AC2.2.7・U5 AC2.2.7 | OK（U3・U5） | backend/src/test/java/cherry/mastersmith/invitation/web/InvitationAdminApiIT.java（実在） | 網羅 |
| AC2.2.8 | AC | U3 AC2.2.8・U5 AC2.2.8 | OK（U3・U5） | backend/src/test/java/cherry/mastersmith/invitation/web/InvitationSendFailureIT.java（実在） | 網羅 |
| AC2.2.9 | AC | U3 AC2.2.9・U5 AC2.2.9 | OK（U3・U5） | backend/src/test/java/cherry/mastersmith/invitation/web/InvitationAdminApiIT.java（実在） | 網羅 |
| AC2.2.10 | AC | U3 AC2.2.10・U5 AC2.2.10 | OK（U3・U5） | backend/src/test/java/cherry/mastersmith/invitation/web/InvitationAdminApiIT.java（実在） | 網羅 |
| AC2.2.11 | AC | U5 AC2.2.11 | OK（U5）・Deferred（U3） | frontend/src/features/invitation/CancelConfirmDialog.tsx（実在） | 網羅 |
| AC2.2.12 | AC | U5 AC2.2.12 | OK（U5）・Deferred（U3） | frontend/src/features/invitation/InvitationList.tsx（実在） | 網羅 |
| AC2.2.13 | AC | U3 AC2.2.13 | OK（U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/invitation/service/RegistrationConcurrencyIT.java（実在） | 網羅 |
| AC3.1.1 | AC | U1 AC3.1.1 | OK（U1） | backend/src/test/java/cherry/mastersmith/mail/transport/MailSendIT.java（実在） | 網羅 |
| AC3.1.2 | AC | U3 AC3.1.2 | OK（U3）・Deferred（U1） | backend/src/test/java/cherry/mastersmith/invitation/web/InvitationMailIT.java（実在） | 網羅 |
| AC3.1.3 | AC | U3 AC3.1.3 | OK（U3）・Deferred（U1） | backend/src/test/java/cherry/mastersmith/invitation/web/InvitationMailIT.java（実在） | 網羅 |
| AC3.1.4 | AC | U1 AC3.1.4 | OK（U1） | backend/src/test/java/cherry/mastersmith/mail/template/MailTemplateLintTest.java（実在） | 網羅 |
| AC3.1.5 | AC | U1 AC3.1.5 | OK（U1） | backend/src/test/java/cherry/mastersmith/mail/template/MailTemplateLintTest.java（実在） | 網羅 |
| AC3.1.6 | AC | U1 AC3.1.6 | OK（U1） | backend/src/test/java/cherry/mastersmith/mail/template/MailTemplateLintTest.java（実在） | 網羅 |
| AC3.1.7 | AC | U1 AC3.1.7 | OK（U1） | backend/src/test/java/cherry/mastersmith/mail/transport/MailHeaderInjectionIT.java（実在） | 網羅 |
| AC3.1.8 | AC | U3 AC3.1.8 | OK（U3）・Deferred（U1） | backend/src/test/java/cherry/mastersmith/mail/template/InvitationTemplateContentTest.java（実在） | 網羅 |
| AC3.1.9 | AC | U3 AC3.1.9 | OK（U3）・Deferred（U1） | backend/src/test/java/cherry/mastersmith/mail/template/InvitationTemplateContentTest.java（実在） | 網羅 |
| AC3.1.10 | AC | U1 AC3.1.10 | OK（U1） | backend/src/test/java/cherry/mastersmith/mail/transport/MailSendIT.java（実在） | 網羅 |
| AC3.2.1 | AC | U6 AC3.2.1 | OK（U6）・Deferred（U3・U4） | frontend/src/features/registration/useRegistration.ts（実在） | 網羅 |
| AC3.2.2 | AC | U3 AC3.2.2・U6 AC3.2.2 | OK（U3・U6）・Deferred（U4） | backend/src/test/java/cherry/mastersmith/invitation/web/RegistrationApiIT.java（実在） | 網羅 |
| AC3.2.3 | AC | U3 AC3.2.3 | OK（U3）・Deferred（U4・U6） | backend/src/test/java/cherry/mastersmith/invitation/web/RegistrationApiIT.java（実在） | 網羅 |
| AC3.2.4 | AC | U3 AC3.2.4・U6 AC3.2.4 | OK（U3・U6）・Deferred（U4） | backend/src/test/java/cherry/mastersmith/invitation/web/RegistrationApiIT.java（実在） | 網羅 |
| AC3.2.5 | AC | U3 AC3.2.5・U6 AC3.2.5 | OK（U3・U6）・Deferred（U4） | backend/src/test/java/cherry/mastersmith/invitation/web/RegistrationApiIT.java（実在） | 網羅 |
| AC3.2.6 | AC | U3 AC3.2.6 | OK（U3）・Deferred（U4・U6） | backend/src/test/java/cherry/mastersmith/invitation/web/RegistrationApiIT.java（実在） | 網羅 |
| AC3.2.7 | AC | U3 AC3.2.7 | OK（U3）・Deferred（U4・U6） | backend/src/test/java/cherry/mastersmith/invitation/service/RegistrationConcurrencyIT.java（実在） | 網羅 |
| AC3.2.8 | AC | U3 AC3.2.8 | OK（U3）・Deferred（U4・U6） | backend/src/test/java/cherry/mastersmith/invitation/web/InvitedPersonAuthenticationIT.java（実在） | 網羅 |
| AC3.2.9 | AC | U3 AC3.2.9・U6 AC3.2.9 | OK（U3・U6）・Deferred（U4） | backend/src/test/java/cherry/mastersmith/invitation/web/RegistrationApiIT.java（実在） | 網羅 |
| AC3.2.10 | AC | U3 AC3.2.10・U6 AC3.2.10 | OK（U3・U6）・Deferred（U4） | backend/src/test/java/cherry/mastersmith/invitation/web/RegistrationApiIT.java（実在） | 網羅 |
| AC3.2.11 | AC | U3 AC3.2.11・U6 AC3.2.11 | OK（U3・U6）・Deferred（U4） | backend/src/test/java/cherry/mastersmith/invitation/service/RegistrationRollbackIT.java（実在） | 網羅 |
| AC3.2.12 | AC | U3 AC3.2.12 | OK（U3）・Deferred（U4・U6） | backend/src/test/java/cherry/mastersmith/invitation/web/RegistrationApiIT.java（実在） | 網羅 |
| AC3.2.13 | AC | U3 AC3.2.13 | OK（U3）・Deferred（U4・U6） | backend/src/test/java/cherry/mastersmith/invitation/web/RegistrationApiIT.java（実在） | 網羅 |
| AC3.2.14 | AC | U3 AC3.2.14 | OK（U3）・Deferred（U4・U6） | backend/src/test/java/cherry/mastersmith/invitation/web/InvitedPersonAuthenticationIT.java（実在） | 網羅 |
| AC3.2.15 | AC | U3 AC3.2.15 | OK（U3）・Deferred（U4・U6） | backend/src/test/java/cherry/mastersmith/invitation/web/RegistrationPublicScopeIT.java（実在） | 網羅 |
| AC3.2.16 | AC | U6 AC3.2.16 | OK（U6）・Deferred（U3・U4） | frontend/src/features/registration/RegistrationForm.tsx（実在） | 網羅 |
| AC3.2.17 | AC | U4 AC3.2.17・U6 AC3.2.17 | OK（U4・U6）・Deferred（U3） | frontend/src/features/auth/LoginForm.tsx（実在） | 網羅 |
| AC3.2.18 | AC | U4 AC3.2.18・U6 AC3.2.18 | OK（U4・U6）・Deferred（U3） | frontend/src/app/display-settings/displaySettingsStore.ts（実在） | 網羅 |
| AC4.1.1 | AC | U7 AC4.1.1 | OK（U7）・Deferred（U2・U4） | frontend/src/features/preferences/usePreferencesForm.ts（実在） | 網羅 |
| AC4.1.2 | AC | U4 AC4.1.2 | OK（U4）・Deferred（U2・U7） | frontend/src/app/display-settings/DisplaySettingsProvider.tsx（実在） | 網羅 |
| AC4.1.3 | AC | U4 AC4.1.3 | OK（U4）・Deferred（U2・U7） | frontend/src/app/display-settings/colorScheme.ts（実在） | 網羅 |
| AC4.1.4 | AC | U4 AC4.1.4・U7 AC4.1.4 | OK（U4・U7）・Deferred（U2） | frontend/src/app/display-settings/DisplaySettingsProvider.tsx（実在） | 網羅 |
| AC4.1.5 | AC | U4 AC4.1.5 | OK（U4）・Deferred（U2・U7） | frontend/src/app/display-settings/DisplaySettingsProvider.tsx（実在） | 網羅 |
| AC4.1.6 | AC | U4 AC4.1.6 | OK（U4）・Deferred（U2・U7） | frontend/src/app/display-settings/resolveDisplaySettings.ts（実在） | 網羅 |
| AC4.1.7 | AC | U2 AC4.1.7・U7 AC4.1.7 | OK（U2・U7）・Deferred（U4） | backend/src/test/java/cherry/mastersmith/user/web/MePreferencesApiIT.java（実在） | 網羅 |
| AC4.1.8 | AC | U4 AC4.1.8・U7 AC4.1.8 | OK（U4・U7）・Deferred（U2） | frontend/src/app/layout/ShellLayout.tsx（実在） | 網羅 |
| AC4.1.9 | AC | U4 AC4.1.9 | OK（U4）・Deferred（U2・U7） | frontend/src/app/display-settings/DisplaySettingsProvider.tsx（実在） | 網羅 |
| AC4.1.10 | AC | U7 AC4.1.10 | OK（U7）・Deferred（U2・U4） | frontend/src/features/preferences/PreferencesForm.tsx（実在） | 網羅 |
| AC4.1.11 | AC | U7 AC4.1.11 | OK（U7）・Deferred（U2・U4） | frontend/src/features/preferences/usePreferencesForm.ts（実在） | 網羅 |
| AC4.1.12 | AC | U7 AC4.1.12 | OK（U7）・Deferred（U2・U4） | frontend/src/features/preferences/usePreferencesForm.ts（実在） | 網羅 |
| AC4.1.13 | AC | U2 AC4.1.13 | OK（U2）・Deferred（U4・U7） | backend/src/test/java/cherry/mastersmith/user/web/MePreferencesApiIT.java（実在） | 網羅 |
| AC5.1.1 | AC | U2 AC5.1.1 | OK（U2）・Deferred（U7） | backend/src/test/java/cherry/mastersmith/user/web/MePasswordApiIT.java（実在） | 網羅 |
| AC5.1.2 | AC | U2 AC5.1.2 | OK（U2）・Deferred（U7） | backend/src/test/java/cherry/mastersmith/user/web/MePasswordApiIT.java（実在） | 網羅 |
| AC5.1.3 | AC | U2 AC5.1.3・U7 AC5.1.3 | OK（U2・U7） | backend/src/test/java/cherry/mastersmith/user/web/MePasswordApiIT.java（実在） | 網羅 |
| AC5.1.4 | AC | U2 AC5.1.4 | OK（U2）・Deferred（U7） | backend/src/test/java/cherry/mastersmith/user/web/MePasswordApiIT.java（実在） | 網羅 |
| AC5.1.5 | AC | U2 AC5.1.5 | OK（U2）・Deferred（U7） | backend/src/test/java/cherry/mastersmith/user/web/MePasswordApiIT.java（実在） | 網羅 |
| AC5.1.6 | AC | U2 AC5.1.6・U7 AC5.1.6 | OK（U2・U7） | backend/src/test/java/cherry/mastersmith/user/web/MePasswordApiIT.java（実在） | 網羅 |
| AC5.1.7 | AC | U7 AC5.1.7 | OK（U7）・Deferred（U2） | frontend/src/features/preferences/usePasswordChangeForm.ts（実在） | 網羅 |
| AC5.1.8 | AC | U7 AC5.1.8 | OK（U7）・Deferred（U2） | frontend/src/features/preferences/usePasswordChangeForm.ts（実在） | 網羅 |
| AC5.1.9 | AC | U7 AC5.1.9 | OK（U7）・Deferred（U2） | frontend/src/app/layout/ShellLayout.tsx（実在） | 網羅 |

## 未網羅の一覧

ID の単位で未網羅（Deferred・N/A・GAP のみ、target が無い、連鎖が切れている）のものは 0 件。

以下は、ID としては網羅だが、要件の一部の枝番が code-generation で Deferred のまま持ち越されているもの（持ち主の段つき）。Build and Test の判定の場で扱いを確かめる対象。

- **NFR5（接続の使い方）**: 後半の「同時の要求で接続プールが尽きないことを負荷の試験で確かめる」が未確かめ。
  - U2 NFR5.2（同時 10 件で hikaricp の待ちの時間切れ 0・監査が欠けない）・U2 NFR5.3（接続の数の見積もりの実測）・U3 NFR5.3（k6 で p95 と接続の待ち）→ performance-validation（場面と仮の利用者の用意は Build and Test）
- **NFR6（応答時間）**: 要件の本体である p95 の目標がすべて未確かめ。
  - U2 NFR6.1〜NFR6.4・NFR6.7（プリファレンスの取得・保存 1 秒、パスワードの変更 2 秒、誤り 1 秒、想定の規模）→ performance-validation
  - U3 NFR6.1〜NFR6.5・NFR6.7（招待・送り直しの p95 5 秒、応答しない受け手で 1 件 5 秒以内、ほか）→ performance-validation（FAILED で確定するふるまいは InvitationSendFailureIT で確かめ済み）
  - U8 NFR6.1（見た目の設定の API の p(95)<300。場面は perf/k6/scenarios.js に置いた）→ performance-validation（k6 inspect での読み込みは Build and Test）
  - U2 NFR6.8・NFR6.9、U3 NFR6.8・NFR6.9、U8 NFR6.5（指標のラベルの実際の名前・警報の式の流し直し）→ observability-setup
- **NFR9（テスト）**:
  - U3 NFR9.11（招待から登録の完了までの E2E）→ u6-registration-ui（B5）。U6 NFR9.10 が OK（`frontend/e2e/090-invitation-registration-flow.e2e.ts`、実在）で受けており、連鎖は閉じている。
  - U8 NFR9.3（U8 だけの SLO は置かない。判定は Unverified）→ observability-setup・feedback-optimization
- **NFR10（スキーマの変更）**:
  - U2 NFR10.3（1つ前の版のイメージを V7 の後の内部DB の複写で起動し、Hibernate の validate が足した列を許すこと。V7BackwardCompatibilityIT で (1) は済み）→ deployment-pipeline・deployment-execution（戻しの練習）
  - U2 NFR10.5（配備の前のバックアップと戻しの手順）→ deployment-pipeline・deployment-execution

## 気づいた食い違い

1. **target がテストではなく本番のソースを指す**: 単位によって target の付け方がそろっていない。
   - U4〜U7（画面の単位）は、status OK の AC・CR の target がすべて本番のソース（例: AC1.1.1 → `frontend/src/features/invitation/useInvitationAdmin.ts`、AC3.2.1 → `frontend/src/features/registration/useRegistration.ts`、AC4.1.6 → `frontend/src/app/display-settings/resolveDisplaySettings.ts`）。U4 AC 9/9・CR 7/7、U5 AC 26/26・CR 11/11、U6 AC 10/10・CR 11/11、U7 AC 12/12・CR 9/9。一方 U1〜U3 の AC はすべてテストを指す。
   - そのうち、同じ場所に `*.test.ts(x)` が無い target が 12 ファイルある（`useInvitationAdmin.ts`・`usePreferencesForm.ts`・`usePasswordChangeForm.ts`・`useRegistration.ts`・各 `messages.ts`・`i18n/messages/ja.ts`・`en.ts`・`displaySettingsTypes.ts`・`renderWithProviders.tsx`・`main.tsx`）。部品のテスト経由で確かめている可能性はあるが、traceability.json からは確かめたテストをたどれない。
   - NFR の枝番でも本番のソース・設定を指すものがある（U1 16/27、U4 17/25、U5 13/23、U6 16/28、U7 12/24 など）。例: U1 NFR2.2・NFR2.3・NFR6.1、U2 NFR6.6、U3 NFR6.6 → `backend/src/main/resources/application.yaml`、U8 NFR6.3・NFR6.4 → `AppearanceService.java`。
   - BR も U1（27/30）・U2（26/35）は本番のソースを指し、U3 は 54/54 がテストを指す。
2. **連鎖の形が単位でそろっていない**: U8 だけ code-generation の traceability.json が要件の FR（FR8.1・FR8.2）を直接持つ。ほかの単位は FR を持たず、BR の `source` を経る。画面の単位（U4〜U7）は rules.md が無く、functional-design の traceability.json も AC → 設計の記号（W・D）で、FR からの連鎖は functional-spec.md の本文の表でしかたどれない（FR4.3・FR5.5・FR7.1・FR7.4）。
3. **FR7.4 の連鎖が細い**: FR7.4（招待の言語の初期値は管理者自身の言語）は U4 の frontend-components.md の U5 の行にだけ現れ、AC1.1.1（U5 で OK、target は本番のソース `useInvitationAdmin.ts`）へは内容の一致でつないだ。明示の対応表は無い。
4. **NFR の枝番の名前空間が単位ごと**: nfr-requirements の traceability.json の N/A の行の target が、ほかの単位の枝番を名前で引く（例: U6 の NFR4 → 「U3 の NFR4.2」、U3 の NFR11 → 「U1 の NFR11.1・NFR11.2」、U5 の NFR5 → 「U3 の NFR5.1」、U6 の NFR6 → 「U3 の NFR6.3・NFR6.4」）。同じ単位の code-generation には無い ID のため、機械的に照合すると連鎖が切れたように見える。今回は単位の名前を読んで照合し、実害は無い。
5. **nfr-requirements で N/A の単位が code-generation で同名の枝番を持つ**: U4 の NFR1 は N/A だが target に NFR2.1 を挙げ、U8 の NFR2 は N/A だが NFR4.5・NFR9.4 を挙げる、など、N/A の行が別の NFR の枝番を代わりの置き場として指す。判定には使っていない。
6. **U6 の CR1.1 が Deferred で u4-display-foundation を持ち主とする**が、U4・U5・U7・U2 で OK のため連鎖は閉じている。同様に、ある単位で Deferred の AC（64 件）は、すべてほかの単位で OK になっている（持ち主の単位の付け替えの記録）。
7. **U4 の CR6 が枝番なしの ID で Deferred**: U4 の code-generation は `CR6` を枝番なしで持ち（U5〜U7 は CR6.1 などの枝番）、ID の粒度がそろっていない。

## Sources

- `inception/requirements-analysis/requirements.md`（FR・NFR）
- `inception/user-stories/stories.md`（AC）
- `construction/u1-mail/` 〜 `construction/u8-instance-appearance/` の `functional-design/traceability.json`・`nfr-requirements/traceability.json`・`code-generation/traceability.json`
- 各単位の `code-generation-plan.md`・`unit-test-instructions.md`・`code-summary.md`（持ち越しの持ち主の段）
- `aidlc/spaces/default/memory/project.md`（Way of Working：2段の連鎖でたどる決まり）

## Assumptions & Open Questions

- FR4.3・FR5.5・FR7.1・FR7.4 の4件は、traceability.json ではつながりません。機能設計の文書と stories.md の本文でたどりました（画面の単位 U4〜U7 に rules.md が無いため）。
