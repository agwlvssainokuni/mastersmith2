# 単位をまたぐ網羅の確かめ（cross-unit-traceability）

Intent `261004-role-menu` の要件（`inception/requirements-analysis/requirements.md` のすべての FR・NFR）とストーリーの受け入れ基準（`inception/user-stories/stories.md` の3段の AC）が、各単位のコード生成の記録（`construction/<単位>/code-generation/traceability.json`）まで、status `OK` と実在する target でつながるかを確かめました（パスは `aidlc/spaces/default/intents/261004-role-menu/` からの相対。target のファイルはリポジトリのルートからの相対）。

- 数: 222 件（FR 52・NFR 21・AC 149）。FR・NFR は枝番の付いたもの（FR1.1〜FR12.4、FR4.1a・FR7.3a・FR7.3b を含む。NFR1.1〜NFR6.4）を数え、親の FR1〜FR12・NFR1〜NFR6 は枝番の網羅で代える。AC は US1.1〜US6.1 の 11 のストーリーの 149 件。
- 読んだもの: 単位ごとの7つの `code-generation/traceability.json`（cross-cutting・dsl-v2・group・role・navigation・role-admin-ui・app-frame-ui。段の `construction/code-generation/traceability.json` はありません）、`rules.md` を持つ5つの単位（U1〜U5）の `functional-design/rules.md`（BR の `source`）、各単位の `nfr-requirements/traceability.json` と `nfr-design/traceability.json`、`inception/user-stories/traceability.json`（FR → ストーリー）。
- たどり方: `traceability.json` は要件の ID を直接持たず、単位ごとの ID（BR・D・AC・NFR の枝番）で持っています。そのため次の2段の連鎖でたどりました（`project.md` の Way of Working の学び）。
  - FR → 機能設計の BR（`rules.md` の `source` に FR を挙げる BR。`FR4.3〜FR4.5` のような範囲は広げて読む）→ 同じ単位のコード生成の `traceability.json` の BR。あわせて FR → ストーリー（US）→ その AC → いずれかの単位のコード生成の AC。画面の単位（U6・U7）は `rules.md` を持たないため（機能設計の D の番号で持つ）、FR からはストーリーの AC でたどる。
  - NFR → 各単位の NFR 要件の枝番（この Intent の単位は上流と同じ番号を同じ意味でだけ使う。`nfr-requirements/traceability.json`）→ コード生成の `traceability.json` の同じ ID。単位の NFR の枝番は、直接の ID の一致でつながる。
  - AC → いずれかの単位のコード生成の `traceability.json` の AC で `OK`。
- target のファイルの実在は、target の文の中のパス（`・` でつないだ複数のパスと括弧の注記を含む）をすべて取り出し、リポジトリのルートからの相対でファイルがあるかで確かめました。`OK` の行の target はすべて実在しました（パスを持たない target は、ここで数えた ID の行にはありません）。
- この段の直し（作業ブランチ `fix/261004-role-menu-bt`、Q3 A〜D と権限の YAML の上限）は `traceability.json` を書き換えません。確定した成果物は書き換えない決まり（`project.md`）のため、この表はコード生成の承認の時点の `traceability.json` で判定しています。

**判定: 不合格（条件つき）。未網羅は 5 件（すべて AC）です。** どれもコード生成で `Deferred` とし、持ち主の段がこの Intent の流れにある（`aidlc-state.md` で EXECUTE）ものです。網羅の穴ではなく、この段では測れない性能と配備の確かめです（テストの書き忘れではない）。承認の場で扱いを確かめてください。

## 判定

- FR 52 件: すべて、BR の `OK`（実在する target）か、つながるストーリーの AC の `OK` のどちらかでつながった。ただし、性能・配備の AC（下の未網羅の5件）を含むストーリーにつながる FR は「AC の一部が持ち越し」と書いた。FR12.4 は FR → BR・FR → US のどちらの連鎖にも載っていない（`inception/user-stories/traceability.json` で FR12.4 は `Deferred`（機能設計）。`rules.md` の `source` にも FR12.4 の文字が無い）ため、この段で `rules.md` を読んで判定した（role の BR11.3「書き出しは監査に残さない」が FR12.4 の決定で、`RoleAuditIT` が `OK`）。
- NFR 21 件: すべて、いずれかの単位の同じ ID の `OK`（実在する target）でつながった。NFR2.2・NFR2.4 などの時間の目標は、`OK` の行が台本や問い合わせの数のテストを指し、時間そのものは各単位で `Deferred`（performance-validation）。目標ごとの判定は `build-and-test-summary.md` の Target Verification Matrix に書く。
- AC 149 件: 144 件が `OK`（実在する target）。5 件（AC1.2.18・AC3.1.17・AC4.1.20・AC5.1.18・AC6.1.8）はどの単位でも `OK` が無い（すべての単位で `Deferred`）。
- 持ち主の段: AC1.2.18・AC3.1.17・AC4.1.20・AC5.1.18 は performance-validation、AC6.1.8 は deployment-pipeline・deployment-execution。

## 網羅の表

### FR

| ID | つながる単位の BR（コード生成で OK・実在） | つながるストーリー（AC の OK / 全数） | target のファイル（代表、実在） | 判定 |
|---|---|---|---|---|
| FR1.1 | U1 BR5.5・U3 BR2.1・U4 BR1.1・U4 BR1.2・U4 BR1.4・U4 BR3.1・U5 BR2.1 | US1.1（17/17） | backend/src/test/java/cherry/mastersmith/ApiAccessConsistencyIT.java（実在） | 網羅 |
| FR1.2 | U4 BR2.1・U4 BR3.2・U5 BR6.1 | US1.1（17/17） | backend/src/test/java/cherry/mastersmith/role/web/RoleAdminAuthorizationApiIT.java（実在） | 網羅 |
| FR1.3 | U1 BR1.1・U1 BR1.3・U1 BR1.4・U3 BR2.1・U4 BR2.1・U5 BR6.1 | US1.1・US1.2・US2.1・US2.2・US3.1（79/81） | backend/src/test/java/cherry/mastersmith/common/testsupport/ApiAccessRules.java（実在） | 網羅。AC の一部が持ち越し（AC1.2.18・AC3.1.17） |
| FR2.1 | U3 BR7.4・U4 BR4.1 | US1.2・US6.1（26/28） | backend/src/test/java/cherry/mastersmith/group/repository/GroupRepositoryIT.java（実在） | 網羅。AC の一部が持ち越し（AC1.2.18・AC6.1.8） |
| FR2.2 | U4 BR5.1 | US1.2（17/18） | backend/src/test/java/cherry/mastersmith/role/domain/PermissionInheritanceTest.java（実在） | 網羅。AC の一部が持ち越し（AC1.2.18） |
| FR2.3 | U2 BR1.1・U2 BR4.1・U4 BR4.5 | US1.2・US6.1（26/28） | backend/src/test/java/cherry/mastersmith/dsl/service/DefaultDslReaderTest.java（実在） | 網羅。AC の一部が持ち越し（AC1.2.18・AC6.1.8） |
| FR2.4 | U4 BR4.2・U4 BR4.6・U4 BR4.7 | US1.2（17/18） | backend/src/test/java/cherry/mastersmith/role/store/RoleStoreConstraintIT.java（実在） | 網羅。AC の一部が持ち越し（AC1.2.18） |
| FR3.1 | U3 BR5.1・U4 BR4.1・U4 BR7.8 | US1.2・US6.1（26/28） | backend/src/test/java/cherry/mastersmith/group/web/GroupConcurrencyIT.java（実在） | 網羅。AC の一部が持ち越し（AC1.2.18・AC6.1.8） |
| FR3.2 | U4 BR5.2・U4 BR9.11 | US1.2（17/18） | backend/src/test/java/cherry/mastersmith/role/domain/PermissionInheritanceTest.java（実在） | 網羅。AC の一部が持ち越し（AC1.2.18） |
| FR3.3 | — | US1.2（17/18） | backend/src/test/java/cherry/mastersmith/dsl/domain/DslModelTest.java（実在） | 網羅。AC の一部が持ち越し（AC1.2.18）。BR の連鎖なし。AC の連鎖で網羅 |
| FR4.1 | U1 BR4.1・U1 BR4.11・U3 BR1.1・U3 BR1.2・U3 BR1.4・U3 BR2.1 | US2.1（13/13） | backend/src/test/java/cherry/mastersmith/group/domain/GroupNameTest.java（実在） | 網羅 |
| FR4.1a | U3 BR4.1・U3 BR4.4 | US2.1（13/13） | backend/src/test/java/cherry/mastersmith/group/web/GroupAdminApiIT.java（実在） | 網羅 |
| FR4.2 | U1 BR4.3・U4 BR6.1・U4 BR6.2 | US2.2（16/16） | backend/src/test/java/cherry/mastersmith/role/web/RoleAssignmentApiIT.java（実在） | 網羅 |
| FR4.3 | U1 BR4.9・U3 BR3.6・U3 BR6.4・U3 BR6.5・U4 BR6.5 | US2.1・US2.2（29/29） | backend/src/test/java/cherry/mastersmith/group/service/GroupMembershipQueryIT.java（実在） | 網羅 |
| FR4.4 | U3 BR3.6・U4 BR5.3・U4 BR5.4・U4 BR6.8 | US2.1・US2.2（29/29） | backend/src/test/java/cherry/mastersmith/group/service/GroupMembershipQueryIT.java（実在） | 網羅 |
| FR4.5 | U3 BR9.2・U4 BR12.2 | US2.1・US2.2（29/29） | backend/src/test/java/cherry/mastersmith/group/web/GroupSecretLeakIT.java（実在） | 網羅 |
| FR5.1 | U3 BR8.9・U4 BR5.5・U4 BR11.9 | US4.1（20/21） | backend/src/test/java/cherry/mastersmith/audit/domain/AuditEventTest.java（実在） | 網羅。AC の一部が持ち越し（AC4.1.20） |
| FR5.2 | U4 BR7.7・U5 BR1.4・U5 BR7.3 | US4.1（20/21） | backend/src/test/java/cherry/mastersmith/role/web/WorkRoleSwitchApiIT.java（実在） | 網羅。AC の一部が持ち越し（AC4.1.20） |
| FR5.3 | U4 BR7.5 | US4.1（20/21） | backend/src/test/java/cherry/mastersmith/role/web/WorkRoleSwitchApiIT.java（実在） | 網羅。AC の一部が持ち越し（AC4.1.20） |
| FR5.4 | U4 BR7.7 | US4.1（20/21） | backend/src/test/java/cherry/mastersmith/role/web/WorkRoleSwitchApiIT.java（実在） | 網羅。AC の一部が持ち越し（AC4.1.20） |
| FR5.5 | U4 BR5.3・U4 BR7.1・U4 BR7.2 | US1.1・US2.1・US2.2・US4.1（66/67） | backend/src/test/java/cherry/mastersmith/role/service/EffectivePermissionConsistencyIT.java（実在） | 網羅。AC の一部が持ち越し（AC4.1.20） |
| FR5.6 | — | US4.1（20/21） | backend/src/test/java/cherry/mastersmith/role/web/WorkRoleSwitchApiIT.java（実在） | 網羅。AC の一部が持ち越し（AC4.1.20）。BR の連鎖なし。AC の連鎖で網羅 |
| FR6.1 | — | US4.2（7/7） | backend/src/test/java/cherry/mastersmith/role/web/MyPermissionsApiIT.java（実在） | 網羅。BR の連鎖なし。AC の連鎖で網羅 |
| FR6.2 | U4 BR2.2・U4 BR5.6・U5 BR1.1・U5 BR2.7 | US4.2（7/7） | backend/src/test/java/cherry/mastersmith/role/web/RoleMeAuthorizationApiIT.java（実在） | 網羅 |
| FR7.1 | U4 BR9.14 | US3.1（16/17） | backend/src/test/java/cherry/mastersmith/role/transfer/RoleTransferWriterTest.java（実在） | 網羅。AC の一部が持ち越し（AC3.1.17） |
| FR7.2 | U4 BR9.6 | US3.1（16/17） | backend/src/test/java/cherry/mastersmith/role/transfer/TransferPlannerTest.java（実在） | 網羅。AC の一部が持ち越し（AC3.1.17） |
| FR7.3 | U4 BR9.15 | US3.1（16/17） | backend/src/test/java/cherry/mastersmith/role/service/RoleTransferServiceTest.java（実在） | 網羅。AC の一部が持ち越し（AC3.1.17） |
| FR7.3a | U4 BR9.9・U4 BR9.10 | US3.1（16/17） | backend/src/test/java/cherry/mastersmith/role/transfer/TransferFingerprintTest.java（実在） | 網羅。AC の一部が持ち越し（AC3.1.17） |
| FR7.3b | U4 BR9.13 | US3.1（16/17） | backend/src/test/java/cherry/mastersmith/role/web/RoleTransferApiIT.java（実在） | 網羅。AC の一部が持ち越し（AC3.1.17） |
| FR7.4 | U2 BR6.1・U2 BR6.2・U4 BR9.3・U4 BR9.5 | US3.1（16/17） | backend/src/test/java/cherry/mastersmith/dsl/service/SafeYamlReaderTest.java（実在） | 網羅。AC の一部が持ち越し（AC3.1.17） |
| FR7.5 | U4 BR9.8 | US3.1（16/17） | backend/src/test/java/cherry/mastersmith/role/web/RoleTransferApiIT.java（実在） | 網羅。AC の一部が持ち越し（AC3.1.17） |
| FR8.1 | — | US4.1・US5.1（37/39） | backend/src/test/java/cherry/mastersmith/role/web/WorkRoleSwitchApiIT.java（実在） | 網羅。AC の一部が持ち越し（AC4.1.20・AC5.1.18）。BR の連鎖なし。AC の連鎖で網羅 |
| FR8.2 | U4 BR5.4・U5 BR1.4 | US4.1（20/21） | backend/src/test/java/cherry/mastersmith/role/web/RoleChangeVisibilityIT.java（実在） | 網羅。AC の一部が持ち越し（AC4.1.20） |
| FR8.3 | U4 BR5.3・U5 BR2.6・U5 BR5.2 | US4.1・US1.2・US5.1（54/57） | backend/src/test/java/cherry/mastersmith/role/service/EffectivePermissionConsistencyIT.java（実在） | 網羅。AC の一部が持ち越し（AC1.2.18・AC4.1.20・AC5.1.18） |
| FR9.1 | — | US5.1・US5.3（24/25） | backend/src/test/java/cherry/mastersmith/navigation/web/NavigationApiIT.java（実在） | 網羅。AC の一部が持ち越し（AC5.1.18）。BR の連鎖なし。AC の連鎖で網羅 |
| FR9.2 | — | US5.1（17/18） | backend/src/test/java/cherry/mastersmith/navigation/web/NavigationApiIT.java（実在） | 網羅。AC の一部が持ち越し（AC5.1.18）。BR の連鎖なし。AC の連鎖で網羅 |
| FR9.3 | U5 BR5.3 | US5.1（17/18） | backend/src/test/java/cherry/mastersmith/navigation/web/NavigationApiIT.java（実在） | 網羅。AC の一部が持ち越し（AC5.1.18） |
| FR9.4 | U2 BR2.1 | US5.2（5/5） | backend/src/test/java/cherry/mastersmith/dsl/domain/MenuDepthTest.java（実在） | 網羅 |
| FR9.5 | U5 BR1.2・U5 BR1.3 | US5.1（17/18） | backend/src/test/java/cherry/mastersmith/navigation/service/NavigationServiceTest.java（実在） | 網羅。AC の一部が持ち越し（AC5.1.18） |
| FR9.6 | U5 BR3.2・U5 BR4.2 | US5.1（17/18） | backend/src/test/java/cherry/mastersmith/navigation/web/NavigationUntrustedInputIT.java（実在） | 網羅。AC の一部が持ち越し（AC5.1.18） |
| FR10.1 | U5 BR2.1・U5 BR2.5 | US5.1・US1.2・US4.2（41/43） | backend/src/test/java/cherry/mastersmith/navigation/domain/MenuFilterTest.java（実在） | 網羅。AC の一部が持ち越し（AC1.2.18・AC5.1.18） |
| FR10.2 | U5 BR2.3 | US5.1（17/18） | backend/src/test/java/cherry/mastersmith/navigation/domain/MenuFilterTest.java（実在） | 網羅。AC の一部が持ち越し（AC5.1.18） |
| FR10.3 | U1 BR5.1・U1 BR5.3・U5 BR1.6 | US5.3（7/7） | backend/src/test/java/cherry/mastersmith/navigation/web/NavigationApiIT.java（実在） | 網羅 |
| FR10.4 | U1 BR5.5・U5 BR5.2・U5 BR5.4 | US5.1（17/18） | backend/src/test/java/cherry/mastersmith/ApiAccessConsistencyIT.java（実在） | 網羅。AC の一部が持ち越し（AC5.1.18） |
| FR10.5 | U1 BR5.1・U5 BR1.6・U5 BR3.4 | US5.1・US5.3（24/25） | backend/src/test/java/cherry/mastersmith/navigation/web/NavigationApiIT.java（実在） | 網羅。AC の一部が持ち越し（AC5.1.18） |
| FR11.1 | U3 BR2.1 | US1.1・US2.1・US2.2・US3.1（62/63） | backend/src/test/java/cherry/mastersmith/group/web/GroupAdminAuthorizationApiIT.java（実在） | 網羅。AC の一部が持ち越し（AC3.1.17） |
| FR11.2 | U4 BR4.10 | US1.2（17/18） | backend/src/test/java/cherry/mastersmith/role/domain/PermissionTreeBuilderTest.java（実在） | 網羅。AC の一部が持ち越し（AC1.2.18） |
| FR11.3 | U4 BR4.11 | US1.2（17/18） | backend/src/test/java/cherry/mastersmith/role/domain/PermissionTreeBuilderTest.java（実在） | 網羅。AC の一部が持ち越し（AC1.2.18） |
| FR12.1 | U3 BR8.1・U3 BR8.4・U4 BR11.1・U4 BR11.4 | US1.1・US1.2・US2.1・US2.2・US3.1・US4.1（99/102） | backend/src/test/java/cherry/mastersmith/audit/service/GroupAuditIT.java（実在） | 網羅。AC の一部が持ち越し（AC1.2.18・AC3.1.17・AC4.1.20） |
| FR12.2 | U3 BR5.7・U3 BR8.2・U4 BR11.2 | — | backend/src/test/java/cherry/mastersmith/group/service/GroupStoreTransactionsIT.java（実在） | 網羅 |
| FR12.3 | U3 BR8.7・U4 BR11.7・U4 BR11.8 | US1.1・US1.2・US3.1（50/52） | backend/src/test/java/cherry/mastersmith/audit/service/AuditSecretLeakIT.java（実在） | 網羅。AC の一部が持ち越し（AC1.2.18・AC3.1.17） |
| FR12.4 | U4 BR11.3（この段で `rules.md` を読んで判定） | — | backend/src/test/java/cherry/mastersmith/role/web/RoleAuditIT.java（実在） | 網羅。連鎖に載らず、この段で判定（書き出しは監査に残さない決定） |

### NFR

| ID | つながる単位（コード生成で OK・実在） | target のファイル（代表、実在） | Deferred の単位と持ち主 | 判定 |
|---|---|---|---|---|
| NFR1.1 | U1・U3・U4・U5・U6・U7（OK 6 件） | backend/src/test/java/cherry/mastersmith/ApiAccessRulesTest.java（実在） | — | 網羅 |
| NFR1.2 | U1・U3・U4・U5（OK 4 件） | backend/src/test/java/cherry/mastersmith/ApiAccessRulesTest.java（実在） | — | 網羅 |
| NFR1.3 | U1・U3・U4・U5（OK 4 件） | backend/src/test/java/cherry/mastersmith/ApiAccessConsistencyIT.java（実在） | — | 網羅 |
| NFR1.4 | U3・U4・U5・U6・U7（OK 5 件） | backend/src/test/java/cherry/mastersmith/group/web/GroupAdminMassAssignmentIT.java（実在） | — | 網羅 |
| NFR1.5 | U1・U2・U4・U6（OK 4 件） | backend/src/test/java/cherry/mastersmith/ApiAccessConsistencyIT.java（実在） | — | 網羅 |
| NFR1.6 | U1・U3・U4・U5・U6・U7（OK 6 件） | backend/src/test/java/cherry/mastersmith/PublicApiInventoryTest.java（実在） | — | 網羅 |
| NFR2.1 | U3（OK 1 件） | perf/k6/scenarios.js（実在） | U4（Performance Validation）・U5（Performance Validation） | 網羅 |
| NFR2.2 | U6・U7（OK 2 件） | frontend/src/features/roleadmin/api/roleAdminApi.test.ts（実在） | U4（Performance Validation）・U5（Performance Validation） | 網羅 |
| NFR2.3 | U3・U5（OK 2 件） | backend/src/test/java/cherry/mastersmith/group/service/GroupMembershipQueryCountIT.java（実在） | U4（Performance Validation） | 網羅 |
| NFR2.4 | U6（OK 1 件） | frontend/src/features/roletransfer/RoleTransferPage.test.tsx（実在） | U4（Performance Validation） | 網羅 |
| NFR3.1 | U3・U4（OK 2 件） | backend/src/test/java/cherry/mastersmith/group/web/GroupConcurrencyIT.java（実在） | — | 網羅 |
| NFR3.2 | U4（OK 1 件） | backend/src/test/java/cherry/mastersmith/role/service/RoleTransferAtomicityIT.java（実在） | — | 網羅 |
| NFR4.1 | U1・U2・U6・U7（OK 4 件） | frontend/src/shared/tree/SharedTreeView.test.tsx（実在） | — | 網羅 |
| NFR4.2 | U1・U6・U7（OK 3 件） | frontend/src/shared/tree/SharedTreeView.test.tsx（実在） | — | 網羅 |
| NFR4.3 | U1・U2・U6・U7（OK 4 件） | frontend/src/shared/tree/SharedTreeView.test.tsx（実在） | — | 網羅 |
| NFR5.1 | U3・U4（OK 2 件） | backend/src/test/java/cherry/mastersmith/audit/service/GroupAuditIT.java（実在） | — | 網羅 |
| NFR5.2 | U3・U4（OK 2 件） | backend/src/test/java/cherry/mastersmith/group/web/GroupAdminMetricsIT.java（実在） | — | 網羅 |
| NFR6.1 | U1・U2・U3・U4・U5・U6・U7（OK 7 件） | backend/src/test/java/cherry/mastersmith/dsl/validate/DslSemanticValidatorTest.java（実在） | — | 網羅 |
| NFR6.2 | U1・U4・U5・U6・U7（OK 5 件） | frontend/src/eslintImportRules.test.ts（実在） | — | 網羅 |
| NFR6.3 | U1・U6・U7（OK 3 件） | backend/src/test/java/cherry/mastersmith/user/UserBoundaryArchitectureTest.java（実在） | — | 網羅 |
| NFR6.4 | U1・U2・U3・U4・U5・U6・U7（OK 7 件） | backend/build.gradle.kts（実在） | — | 網羅 |

### AC

| ID | つながる単位（コード生成で OK・実在） | target のファイル（代表、実在） | 判定 |
|---|---|---|---|
| AC1.1.1 | U4・U6 | backend/src/test/java/cherry/mastersmith/role/web/RoleAdminApiIT.java（実在） | 網羅 |
| AC1.1.2 | U4・U6 | backend/src/test/java/cherry/mastersmith/role/domain/RoleNameTest.java（実在） | 網羅 |
| AC1.1.3 | U4・U6 | backend/src/test/java/cherry/mastersmith/role/web/RoleAssignmentApiIT.java（実在） | 網羅 |
| AC1.1.4 | U4・U6 | backend/src/test/java/cherry/mastersmith/role/web/RoleAdminApiIT.java（実在） | 網羅 |
| AC1.1.5 | U1・U4 | backend/src/test/java/cherry/mastersmith/ApiAccessConsistencyIT.java（実在） | 網羅 |
| AC1.1.6 | U4 | backend/src/test/java/cherry/mastersmith/role/web/RoleConcurrencyIT.java（実在） | 網羅 |
| AC1.1.7 | U6 | frontend/src/features/roleadmin/DeleteConfirmDialog.test.tsx（実在） | 網羅 |
| AC1.1.8 | U4・U6 | backend/src/test/java/cherry/mastersmith/role/web/RoleAssignmentApiIT.java（実在） | 網羅 |
| AC1.1.9 | U4・U6 | backend/src/test/java/cherry/mastersmith/role/domain/RoleNameTest.java（実在） | 網羅 |
| AC1.1.10 | U4 | backend/src/test/java/cherry/mastersmith/role/domain/RoleNameTest.java（実在） | 網羅 |
| AC1.1.11 | U4 | backend/src/test/java/cherry/mastersmith/role/web/RoleAdminAuthorizationApiIT.java（実在） | 網羅 |
| AC1.1.12 | U4・U6 | backend/src/test/java/cherry/mastersmith/role/web/RoleIdorApiIT.java（実在） | 網羅 |
| AC1.1.13 | U4 | backend/src/test/java/cherry/mastersmith/role/service/RoleDeleteCleanupIT.java（実在） | 網羅 |
| AC1.1.14 | U4・U6 | backend/src/test/java/cherry/mastersmith/role/web/RoleAdminApiIT.java（実在） | 網羅 |
| AC1.1.15 | U1・U4 | backend/src/test/java/cherry/mastersmith/ApiAccessArchitectureTest.java（実在） | 網羅 |
| AC1.1.16 | U4 | backend/src/test/java/cherry/mastersmith/role/domain/RoleAuditEventTest.java（実在） | 網羅 |
| AC1.1.17 | U4 | backend/src/test/java/cherry/mastersmith/role/web/RoleMetricsIT.java（実在） | 網羅 |
| AC1.2.1 | U1・U2・U4・U6 | backend/src/test/java/cherry/mastersmith/dsl/domain/DslModelTest.java（実在） | 網羅 |
| AC1.2.2 | U4・U6 | backend/src/test/java/cherry/mastersmith/role/domain/PermissionTreeBuilderTest.java（実在） | 網羅 |
| AC1.2.3 | U4・U6 | backend/src/test/java/cherry/mastersmith/role/domain/PermissionTreeBuilderTest.java（実在） | 網羅 |
| AC1.2.4 | U4・U6 | backend/src/test/java/cherry/mastersmith/role/domain/PermissionTreeBuilderTest.java（実在） | 網羅 |
| AC1.2.5 | U4 | backend/src/test/java/cherry/mastersmith/role/web/RoleAuditIT.java（実在） | 網羅 |
| AC1.2.6 | U4・U6 | backend/src/test/java/cherry/mastersmith/role/web/RolePermissionApiIT.java（実在） | 網羅 |
| AC1.2.7 | U1・U4・U6 | backend/src/test/java/cherry/mastersmith/role/web/RolePermissionApiIT.java（実在） | 網羅 |
| AC1.2.8 | U4 | backend/src/test/java/cherry/mastersmith/role/web/RolePermissionApiIT.java（実在） | 網羅 |
| AC1.2.9 | U4 | backend/src/test/java/cherry/mastersmith/role/domain/PermissionInheritanceTest.java（実在） | 網羅 |
| AC1.2.10 | U4 | backend/src/test/java/cherry/mastersmith/role/domain/PermissionInheritanceTest.java（実在） | 網羅 |
| AC1.2.11 | U1・U4 | backend/src/test/java/cherry/mastersmith/ApiAccessConsistencyIT.java（実在） | 網羅 |
| AC1.2.12 | U4 | backend/src/test/java/cherry/mastersmith/role/web/RoleAdminAuthorizationApiIT.java（実在） | 網羅 |
| AC1.2.13 | U4・U6 | backend/src/test/java/cherry/mastersmith/role/web/RoleConcurrencyIT.java（実在） | 網羅 |
| AC1.2.14 | U1・U6 | frontend/src/features/roleadmin/PermissionPanel.test.tsx（実在） | 網羅 |
| AC1.2.15 | U6 | frontend/src/features/roleadmin/RoleDetailPage.test.tsx（実在） | 網羅 |
| AC1.2.16 | U1・U4・U6 | backend/src/test/java/cherry/mastersmith/role/domain/PermissionTreeBuilderTest.java（実在） | 網羅 |
| AC1.2.17 | U4・U6 | backend/src/test/java/cherry/mastersmith/role/service/RoleAdminServiceTest.java（実在） | 網羅 |
| AC1.2.18 | —（U1・U2・U4・U6 がすべて Deferred） | — | **未網羅**。持ち主 performance-validation（U4 role の k6 `roleTreeRead`・`rolePermissionSave`） |
| AC2.1.1 | U3・U6 | backend/src/test/java/cherry/mastersmith/group/web/GroupAdminApiIT.java（実在） | 網羅 |
| AC2.1.2 | U3・U6 | backend/src/test/java/cherry/mastersmith/group/domain/GroupNameTest.java（実在） | 網羅 |
| AC2.1.3 | U3・U4・U6 | backend/src/test/java/cherry/mastersmith/group/web/GroupAdminApiIT.java（実在） | 網羅 |
| AC2.1.4 | U3・U6 | backend/src/test/java/cherry/mastersmith/group/web/GroupAdminApiIT.java（実在） | 網羅 |
| AC2.1.5 | U3・U4 | backend/src/test/java/cherry/mastersmith/group/web/GroupConcurrencyIT.java（実在） | 網羅 |
| AC2.1.6 | U3 | backend/src/test/java/cherry/mastersmith/group/web/GroupAdminAuthorizationApiIT.java（実在） | 網羅 |
| AC2.1.7 | U3・U6 | backend/src/test/java/cherry/mastersmith/group/web/GroupAdminApiIT.java（実在） | 網羅 |
| AC2.1.8 | U3・U6 | backend/src/test/java/cherry/mastersmith/group/web/GroupAdminApiIT.java（実在） | 網羅 |
| AC2.1.9 | U3・U4・U6 | backend/src/test/java/cherry/mastersmith/group/service/GroupMembershipQueryIT.java（実在） | 網羅 |
| AC2.1.10 | U3・U4・U6 | backend/src/test/java/cherry/mastersmith/group/service/GroupMembershipQueryIT.java（実在） | 網羅 |
| AC2.1.11 | U3 | backend/src/test/java/cherry/mastersmith/group/web/GroupAdminIdorApiIT.java（実在） | 網羅 |
| AC2.1.12 | U3 | backend/src/test/java/cherry/mastersmith/group/web/GroupSecretLeakIT.java（実在） | 網羅 |
| AC2.1.13 | U3・U6 | backend/src/test/java/cherry/mastersmith/group/web/GroupAdminApiIT.java（実在） | 網羅 |
| AC2.2.1 | U3・U4・U6 | backend/src/test/java/cherry/mastersmith/group/service/GroupMembershipQueryIT.java（実在） | 網羅 |
| AC2.2.2 | U4・U6 | backend/src/test/java/cherry/mastersmith/role/web/RoleAssignmentApiIT.java（実在） | 網羅 |
| AC2.2.3 | U4 | backend/src/test/java/cherry/mastersmith/role/web/RoleChangeVisibilityIT.java（実在） | 網羅 |
| AC2.2.4 | U4 | backend/src/test/java/cherry/mastersmith/role/web/RoleChangeVisibilityIT.java（実在） | 網羅 |
| AC2.2.5 | U4 | backend/src/test/java/cherry/mastersmith/role/web/RoleAdminAuthorizationApiIT.java（実在） | 網羅 |
| AC2.2.6 | U4 | backend/src/test/java/cherry/mastersmith/role/web/RoleSecretLeakIT.java（実在） | 網羅 |
| AC2.2.7 | U4・U6 | backend/src/test/java/cherry/mastersmith/role/web/RoleAssignmentApiIT.java（実在） | 網羅 |
| AC2.2.8 | U4・U6 | backend/src/test/java/cherry/mastersmith/role/web/RoleAssignmentApiIT.java（実在） | 網羅 |
| AC2.2.9 | U6 | frontend/src/features/roleadmin/AssignmentPanel.test.tsx（実在） | 網羅 |
| AC2.2.10 | U3・U4 | backend/src/test/java/cherry/mastersmith/group/service/GroupMembershipQueryIT.java（実在） | 網羅 |
| AC2.2.11 | U4 | backend/src/test/java/cherry/mastersmith/role/web/RoleMassAssignmentApiIT.java（実在） | 網羅 |
| AC2.2.12 | U4 | backend/src/test/java/cherry/mastersmith/role/web/RoleSelfOperationIT.java（実在） | 網羅 |
| AC2.2.13 | U4 | backend/src/test/java/cherry/mastersmith/role/web/RoleSelfOperationIT.java（実在） | 網羅 |
| AC2.2.14 | U4 | backend/src/test/java/cherry/mastersmith/role/web/RoleGrantsNoAdminAccessIT.java（実在） | 網羅 |
| AC2.2.15 | U4・U6 | backend/src/test/java/cherry/mastersmith/role/web/RoleAssignmentApiIT.java（実在） | 網羅 |
| AC2.2.16 | U4・U6 | backend/src/test/java/cherry/mastersmith/role/web/RoleAssignmentApiIT.java（実在） | 網羅 |
| AC3.1.1 | U4・U6 | backend/src/test/java/cherry/mastersmith/role/web/RoleTransferApiIT.java（実在） | 網羅 |
| AC3.1.2 | U4・U6 | backend/src/test/java/cherry/mastersmith/role/web/RoleTransferApiIT.java（実在） | 網羅 |
| AC3.1.3 | U4・U6 | backend/src/test/java/cherry/mastersmith/role/web/RoleTransferApiIT.java（実在） | 網羅 |
| AC3.1.4 | U4・U6 | backend/src/test/java/cherry/mastersmith/role/service/RoleTransferStaleIT.java（実在） | 網羅 |
| AC3.1.5 | U4・U6 | backend/src/test/java/cherry/mastersmith/role/web/RoleTransferApiIT.java（実在） | 網羅 |
| AC3.1.6 | U4・U6 | backend/src/test/java/cherry/mastersmith/role/web/RoleTransferApiIT.java（実在） | 網羅 |
| AC3.1.7 | U2・U4・U6 | backend/src/test/java/cherry/mastersmith/dsl/service/SafeYamlReaderTest.java（実在） | 網羅 |
| AC3.1.8 | U4 | backend/src/test/java/cherry/mastersmith/role/service/RoleTransferAtomicityIT.java（実在） | 網羅 |
| AC3.1.9 | U4・U6 | backend/src/test/java/cherry/mastersmith/role/web/RoleTransferApiIT.java（実在） | 網羅 |
| AC3.1.10 | U4・U6 | backend/src/test/java/cherry/mastersmith/role/web/RoleTransferApiIT.java（実在） | 網羅 |
| AC3.1.11 | U6 | frontend/src/features/roletransfer/RoleTransferPage.test.tsx（実在） | 網羅 |
| AC3.1.12 | U2・U4・U6 | backend/src/test/java/cherry/mastersmith/dsl/service/SafeYamlReaderTest.java（実在） | 網羅 |
| AC3.1.13 | U2・U4・U6 | backend/src/test/java/cherry/mastersmith/dsl/service/SafeYamlReaderTest.java（実在） | 網羅 |
| AC3.1.14 | U4・U6 | backend/src/test/java/cherry/mastersmith/role/web/RoleTransferApiIT.java（実在） | 網羅 |
| AC3.1.15 | U4 | backend/src/test/java/cherry/mastersmith/role/web/RoleTransferApiIT.java（実在） | 網羅 |
| AC3.1.16 | U4 | backend/src/test/java/cherry/mastersmith/role/web/RoleAdminAuthorizationApiIT.java（実在） | 網羅 |
| AC3.1.17 | —（U2・U4・U6 がすべて Deferred） | — | **未網羅**。持ち主 performance-validation（U4 role の k6 `roleTransferLargeCheck`・`roleTransferLargeApply`。上限ちょうどの YAML の時間とヒープの先の測りはこの段の Q1） |
| AC4.1.1 | U4・U7 | backend/src/test/java/cherry/mastersmith/role/web/WorkRoleSwitchApiIT.java（実在） | 網羅 |
| AC4.1.2 | U4 | backend/src/test/java/cherry/mastersmith/role/web/WorkRoleSwitchApiIT.java（実在） | 網羅 |
| AC4.1.3 | U4・U7 | backend/src/test/java/cherry/mastersmith/role/web/WorkRoleSwitchApiIT.java（実在） | 網羅 |
| AC4.1.4 | U4・U7 | backend/src/test/java/cherry/mastersmith/role/web/WorkRoleSwitchApiIT.java（実在） | 網羅 |
| AC4.1.5 | U4 | backend/src/test/java/cherry/mastersmith/role/web/WorkRoleConcurrencyIT.java（実在） | 網羅 |
| AC4.1.6 | U4 | backend/src/test/java/cherry/mastersmith/role/web/MyPermissionsApiIT.java（実在） | 網羅 |
| AC4.1.7 | U7 | frontend/src/app/work-role/WorkRoleSwitcher.test.tsx（実在） | 網羅 |
| AC4.1.8 | U7 | frontend/src/app/work-role/WorkRoleSwitcher.test.tsx（実在） | 網羅 |
| AC4.1.9 | U7 | frontend/src/app/work-role/WorkRoleSwitcher.test.tsx（実在） | 網羅 |
| AC4.1.10 | U7 | frontend/src/features/tables/TablePlaceholderPage.test.tsx（実在） | 網羅 |
| AC4.1.11 | U4・U7 | backend/src/test/java/cherry/mastersmith/role/web/WorkRoleSwitchApiIT.java（実在） | 網羅 |
| AC4.1.12 | U4・U7 | backend/src/test/java/cherry/mastersmith/role/web/RoleMassAssignmentApiIT.java（実在） | 網羅 |
| AC4.1.13 | U4・U7 | backend/src/test/java/cherry/mastersmith/role/web/WorkRoleSwitchApiIT.java（実在） | 網羅 |
| AC4.1.14 | U4 | backend/src/test/java/cherry/mastersmith/role/service/RoleDeleteCleanupIT.java（実在） | 網羅 |
| AC4.1.15 | U4・U7 | backend/src/test/java/cherry/mastersmith/role/web/WorkRoleSwitchAuditIT.java（実在） | 網羅 |
| AC4.1.16 | U4 | backend/src/test/java/cherry/mastersmith/role/domain/WorkRoleChooserTest.java（実在） | 網羅 |
| AC4.1.17 | U4・U7 | backend/src/test/java/cherry/mastersmith/role/web/WorkRoleSwitchApiIT.java（実在） | 網羅 |
| AC4.1.18 | U4 | backend/src/test/java/cherry/mastersmith/role/service/EffectivePermissionConsistencyIT.java（実在） | 網羅 |
| AC4.1.19 | U4 | backend/src/test/java/cherry/mastersmith/role/web/RoleMeAuthorizationApiIT.java（実在） | 網羅 |
| AC4.1.20 | —（U4・U7 がすべて Deferred） | — | **未網羅**。持ち主 performance-validation（U4 role の k6 `workRoleSwitch`。指標は `RoleMetricsIT` で確かめ済み） |
| AC4.1.21 | U4 | backend/src/test/java/cherry/mastersmith/role/domain/WorkRoleResolutionPropertyTest.java（実在） | 網羅 |
| AC4.2.1 | U1・U4・U7 | backend/src/test/java/cherry/mastersmith/role/web/MyPermissionsApiIT.java（実在） | 網羅 |
| AC4.2.2 | U4・U7 | backend/src/test/java/cherry/mastersmith/role/web/RoleChangeVisibilityIT.java（実在） | 網羅 |
| AC4.2.3 | U4 | backend/src/test/java/cherry/mastersmith/role/web/MyPermissionsApiIT.java（実在） | 網羅 |
| AC4.2.4 | U4・U7 | backend/src/test/java/cherry/mastersmith/role/web/MyPermissionsApiIT.java（実在） | 網羅 |
| AC4.2.5 | U1・U4 | backend/src/test/java/cherry/mastersmith/ApiAccessConsistencyIT.java（実在） | 網羅 |
| AC4.2.6 | U1・U4・U7 | backend/src/test/java/cherry/mastersmith/role/web/MyPermissionsApiIT.java（実在） | 網羅 |
| AC4.2.7 | U7 | frontend/src/app/pages/HomePage.test.tsx（実在） | 網羅 |
| AC5.1.1 | U5・U7 | backend/src/test/java/cherry/mastersmith/navigation/web/NavigationApiIT.java（実在） | 網羅 |
| AC5.1.2 | U5・U7 | backend/src/test/java/cherry/mastersmith/navigation/domain/MenuFilterTest.java（実在） | 網羅 |
| AC5.1.3 | U5・U7 | backend/src/test/java/cherry/mastersmith/navigation/web/NavigationApiIT.java（実在） | 網羅 |
| AC5.1.4 | U5・U7 | backend/src/test/java/cherry/mastersmith/navigation/web/NavigationApiIT.java（実在） | 網羅 |
| AC5.1.5 | U5・U7 | backend/src/test/java/cherry/mastersmith/navigation/web/NavigationApiIT.java（実在） | 網羅 |
| AC5.1.6 | U5・U7 | backend/src/test/java/cherry/mastersmith/navigation/web/NavigationApiIT.java（実在） | 網羅 |
| AC5.1.7 | U5・U7 | backend/src/test/java/cherry/mastersmith/navigation/web/NavigationUntrustedInputIT.java（実在） | 網羅 |
| AC5.1.8 | U7 | frontend/src/app/layout/ShellLayout.test.tsx（実在） | 網羅 |
| AC5.1.9 | U5・U7 | backend/src/test/java/cherry/mastersmith/navigation/domain/MenuFilterTest.java（実在） | 網羅 |
| AC5.1.10 | U7 | frontend/e2e/160-app-frame-accessibility.e2e.ts（実在） | 網羅 |
| AC5.1.11 | U5・U7 | backend/src/test/java/cherry/mastersmith/navigation/web/NavigationApiIT.java（実在） | 網羅 |
| AC5.1.12 | U7 | frontend/src/app/layout/ShellLayout.test.tsx（実在） | 網羅 |
| AC5.1.13 | U5・U7 | backend/src/test/java/cherry/mastersmith/navigation/web/NavigationUntrustedInputIT.java（実在） | 網羅 |
| AC5.1.14 | U2・U4・U5 | backend/src/test/java/cherry/mastersmith/dsl/domain/DslModelTest.java（実在） | 網羅 |
| AC5.1.15 | U5・U7 | backend/src/test/java/cherry/mastersmith/navigation/domain/MenuFilterPropertyTest.java（実在） | 網羅 |
| AC5.1.16 | U7 | frontend/e2e/160-app-frame-accessibility.e2e.ts（実在） | 網羅 |
| AC5.1.17 | U5・U7 | backend/src/test/java/cherry/mastersmith/navigation/web/NavigationApiIT.java（実在） | 網羅 |
| AC5.1.18 | —（U2・U4・U5・U7 がすべて Deferred） | — | **未網羅**。持ち主 performance-validation（U5 navigation の k6 `navMenu` ほか） |
| AC5.2.1 | U2 | backend/src/test/java/cherry/mastersmith/dsl/validate/DslSemanticValidatorTest.java（実在） | 網羅 |
| AC5.2.2 | U2 | backend/src/test/java/cherry/mastersmith/dsl/validate/DslSemanticValidatorTest.java（実在） | 網羅 |
| AC5.2.3 | U2 | backend/src/test/java/cherry/mastersmith/dslmanage/service/DslStartupLoaderTest.java（実在） | 網羅 |
| AC5.2.4 | U2 | backend/src/test/java/cherry/mastersmith/dslmanage/service/DslLifecycleTest.java（実在） | 網羅 |
| AC5.2.5 | U2 | backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java（実在） | 網羅 |
| AC5.3.1 | U1・U6・U7 | frontend/src/features/roleadmin/registration.test.tsx（実在） | 網羅 |
| AC5.3.2 | U1・U6・U7 | frontend/src/features/roleadmin/RoleAdminForbidden.test.tsx（実在） | 網羅 |
| AC5.3.3 | U7 | frontend/src/app/navigation/buildNavSections.test.ts（実在） | 網羅 |
| AC5.3.4 | U1・U7 | frontend/src/app/layout/ShellLayout.test.tsx（実在） | 網羅 |
| AC5.3.5 | U7 | frontend/src/app/layout/ShellLayout.test.tsx（実在） | 網羅 |
| AC5.3.6 | U1・U7 | frontend/src/app/navigation/buildNavSections.test.ts（実在） | 網羅 |
| AC5.3.7 | U7 | frontend/src/app/layout/ShellLayout.test.tsx（実在） | 網羅 |
| AC6.1.1 | U2 | backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java（実在） | 網羅 |
| AC6.1.2 | U2 | backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java（実在） | 網羅 |
| AC6.1.3 | U2 | backend/src/test/java/cherry/mastersmith/dslmanage/generate/AbstractDefaultDslGeneratorIT.java（実在） | 網羅 |
| AC6.1.4 | U2 | backend/src/test/java/cherry/mastersmith/dslmanage/service/DslReconcilerTest.java（実在） | 網羅 |
| AC6.1.5 | U2 | backend/src/test/java/cherry/mastersmith/dsl/parse/SafeYamlParserTest.java（実在） | 網羅 |
| AC6.1.6 | U2 | backend/src/test/java/cherry/mastersmith/dslmanage/service/DslStartupIT.java（実在） | 網羅 |
| AC6.1.7 | U2 | backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java（実在） | 網羅 |
| AC6.1.8 | —（U2 がすべて Deferred） | — | **未網羅**。持ち主 deployment-pipeline・deployment-execution（U2 dsl-v2 の配備の段への引き継ぎ。前の版へ戻したときの起動と DSL の生成し直し） |
| AC6.1.9 | U2 | frontend/e2e/040-dsl-admin.e2e.ts（実在） | 網羅 |
| AC6.1.10 | U2 | backend/src/test/java/cherry/mastersmith/dsl/DslSchemaPublicationIT.java（実在） | 網羅 |

## 未網羅の一覧

| ID | 受け入れ基準の中身（要約） | コード生成の記録 | 持ち主の段 | この段での扱い |
|---|---|---|---|---|
| AC1.2.18 | 上限の規模（1 ロールで1万カラム）で、木の1階層を開く API と保存する API の 95 パーセンタイルが 1 秒以内（NFR2.2） | U1・U2・U6 は U4 へ、U4 は performance-validation へ Deferred | performance-validation | Q4: A のとおり k6 を流さない。目標は Target Verification Matrix の U4-NFR2.2 で Unverified として引き継ぐ |
| AC3.1.17 | 上限ちょうどの YAML の確かめと適用が目標の時間以内（NFR2.4） | U2・U6 は U4 へ、U4 は performance-validation へ Deferred | performance-validation | この段の Q1 で、配備と同じ上限の使い捨ての環境で確かめと適用の時間とヒープを測り、依頼者の決定で上限を節 500,000・5 MiB に下げた。下げた後の先の測りは、正しい書き出しの最大で確かめ 1.11 秒・適用 2.35 秒、悪い形で 5.42 秒・11.41 秒（目標の内。悪い形のヒープの余裕は受け入れた制約）。AC の「NFR 要件の段で決めた上限」は承認済みの 10 MiB から 5 MiB に変わった（要件と設計の文書は書き換えない。`build-and-test-summary.md` の 5.2）。k6 の `roleTransferLargeCheck`・`roleTransferLargeApply` での判定は performance-validation（U4-NFR2.4） |
| AC4.1.20 | 作業ロールの切り替えの API の 95 パーセンタイルが 1 秒以内で、管理の API と同じ指標が出る（NFR2.2・NFR5.2） | U4 は performance-validation へ、U7 は U4 へ Deferred | performance-validation | 指標が出ることは `RoleMetricsIT`（U4-NFR5.2、OK）。時間は U4-NFR2.2 で Unverified として引き継ぐ |
| AC5.1.18 | 業務のメニューを返す API の 95 パーセンタイルが 1 秒以内（NFR2.2） | U2・U4・U7 は U5 へ、U5 は performance-validation へ Deferred | performance-validation | U5-NFR2.2 で Unverified として引き継ぐ |
| AC6.1.8 | 版 2 の DSL を適用した環境で前の版のアプリへ戻すと、DSL が無い状態で起動し、既定の DSL を生成し直す | U2 が配備の段へ Deferred | deployment-pipeline・deployment-execution | 戻しの手順と練習で確かめる（U2 の計画の「配備の段へ引き継ぐこと」） |

### 連鎖に載らず、この段で読んで判定したもの

- **FR12.4**（export を監査に残すかは機能設計で決める）: `inception/user-stories/traceability.json` では `Deferred`（functional-design）で、どの `rules.md` の `source` にも FR12.4 の文字が無い。`construction/role/functional-design/rules.md` の BR11.3（入力の誤り・排他の上限切れ・読み取り・import の確かめ・書き出しは監査に残さない）が決定に当たり、role の `traceability.json` で BR11.3 は `OK`（`backend/src/test/java/cherry/mastersmith/role/web/RoleAuditIT.java`、実在）。網羅と判定した。承認済みの `traceability.json` と `rules.md` は書き換えていない。

## 気づいた食い違い

- FR → BR の連鎖を持たない FR が7件ある（FR3.3・FR5.6・FR6.1・FR8.1・FR9.1・FR9.2・FR12.4）。FR12.4 のほかは、ストーリーの AC の連鎖でつながる（FR5.6・FR6.1・FR9.1・FR9.2 は画面の単位 U6・U7 が D の番号で持つため `rules.md` に出ない。FR8.1 は BR の `source` が契約の ID（C5 解決の口）で、FR3.3 は同じ扱いの FR（FR2.3・FR2.4）で挙げている）。網羅の判定は変わらない。
- 画面の単位（U6・U7）の `traceability.json` は機能設計の D の番号（`D1`〜）を持つ。D は FR の ID を持たないため、この表では D を連鎖に使っていない（AC の連鎖で足りる）。
- コード生成の段の読み直し（group の R-02）が指摘したとおり、`OK` の target の多くは `・` で複数のファイルをつなぐか括弧の注記を含む。この段では括弧を外して各パスを取り出し、すべて実在することを確かめた。

## Sources

- `inception/requirements-analysis/requirements.md`（2節 FR1〜FR12、3節 NFR1〜NFR6）
- `inception/user-stories/stories.md`（US1.1〜US6.1 の AC）・`inception/user-stories/traceability.json`
- `construction/<単位>/functional-design/rules.md`（U1〜U5）、`construction/<単位>/nfr-requirements/traceability.json`・`nfr-design/traceability.json`（U1〜U7）
- `construction/<単位>/code-generation/traceability.json`（U1〜U7。コード生成の承認の時点 0415b94）
- `aidlc/spaces/default/memory/project.md`（Way of Working の「要件の網羅は2段の連鎖でたどる」、Change Control の「確定済みの成果物の食い違いは明記して依頼者に確かめる」）

## Assumptions & Open Questions

- 未網羅の5件（持ち主は performance-validation と配備の段）を、この段の承認でどう扱うか（持ち越しとして受け入れるか）を承認の場で確かめる。
- FR12.4 をこの段の判定で網羅とした扱いでよいかを承認の場で確かめる。
