# 単位をまたぐ網羅の確かめ（cross-unit-traceability）

Intent `260930-user-admin` の要件（`inception/requirements-analysis/requirements.md` のすべての FR・NFR）とストーリーの受け入れ基準（`inception/user-stories/stories.md` の3段の AC）が、各単位のコード生成の記録（`construction/u*/code-generation/traceability.json`）まで、status `OK` と実在する target でつながるかを確かめました（パスは `aidlc/spaces/default/intents/260930-user-admin/` からの相対）。

- 数: 129 件（FR 43・NFR 11・AC 75）。FR は枝番の付いたもの（FR1.1〜FR8.3）を数え、親の FR1〜FR8 は枝番の網羅で代える。
- 読んだもの: 単位ごとの5つの `code-generation/traceability.json`（段の `construction/code-generation/traceability.json` はありません）、各単位の `functional-design/rules.md`（BR の `source`）と `nfr-requirements/traceability.json`、`inception/user-stories/traceability.json`（FR → ストーリー）。
- たどり方: `traceability.json` は要件の ID を直接持たず、単位ごとの ID で持っています。そのため次の2段の連鎖でたどりました（`project.md` の Way of Working）。
  - FR → 機能設計の BR（`rules.md` の `source` に FR を挙げる BR）→ コード生成の `traceability.json` の BR。あわせて FR → ストーリー（US）→ その AC → コード生成の AC。画面の単位（U4・U5）は `rules.md` を持たないため、FR からはストーリーの AC でたどる。
  - NFR → 各単位の NFR 要件の枝番（`nfr-requirements/traceability.json`）→ コード生成の `traceability.json` の同じ単位の枝番。
  - AC → コード生成の `traceability.json` の AC（いずれかの単位で OK）。
- target のファイルの実在は、リポジトリのルートからの相対パスでファイルがあるかで確かめました。

**判定: 合格（条件つき）。未網羅は 0 件です。ただし、次の2つを承認の場で扱ってください。**

- `traceability.json` の status `OK` だけで見ると、3件（AC2.2.6・AC3.2.9・AC3.2.10）はどの単位でも OK になっていません（単位の間で Deferred が回っている）。段全体のレビューの R-02 の決定どおり、この段でテストのソースを読んで判定し、3件とも網羅としました。承認済みの `traceability.json` は書き換えていません。
- 4件（AC2.1.8・AC3.1.7・AC4.1.9・AC5.1.7）は、`traceability.json` では OK ですが、実際のブラウザでは閉じた後のフォーカスが行の「操作」に戻らない（N-19）ため、「条件つき（N-19）」としました。後の Intent への持ち越しとしてコード生成の承認の場で承認済みです。

## 判定

- ID の単位では、129 件すべてが網羅（条件つき4件を含む）。target のファイルはすべて実在した。
- `traceability.json` の OK だけでつながらない ID: 3件（AC2.2.6・AC3.2.9・AC3.2.10）。この段の判定で網羅。
- 条件つき: 4件（AC2.1.8・AC3.1.7・AC4.1.9・AC5.1.7）。これらを含むストーリー（US2.1・US3.1・US4.1・US5.1）につながる FR も「条件つきの AC（N-19）を含む」と書いた。
- 持ち越しのある NFR: NFR5（13 枝番）・NFR6（2 枝番）・NFR10（2 枝番）が Deferred のまま。持ち主の段（performance-validation・observability-setup・feedback-optimization・deployment-pipeline・deployment-execution）はどれもこの Intent の流れで EXECUTE の段。目標ごとの判定は `build-and-test-summary.md` の Target Verification Matrix（Unverified）。

## 網羅の表

| ID | 種類 | つながる単位と単位の ID | code-generation の status | target のファイル（実在の有無） | 判定 |
|---|---|---|---|---|---|
| FR1.1 | FR | U3 BR1.2・U3 BR1.8、US1.1 | BR の OK 2 件、US1.1 の AC 13/13 件が OK | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminListApiIT.java（実在） | 網羅 |
| FR1.2 | FR | U3 BR1.7、US1.1・US4.1 | BR の OK 1 件、US1.1・US4.1 の AC 25/25 件が OK | backend/src/test/java/cherry/mastersmith/auth/domain/LockViewTest.java（実在） | 網羅。条件つきの AC（N-19）を含む |
| FR1.3 | FR | U2 BR1.1・U2 BR1.3・U2 BR2.1・U2 BR3.3・U2 BR3.4・U3 BR1.1・U3 BR1.6、US1.1 | BR の OK 7 件、US1.1 の AC 13/13 件が OK | backend/src/test/java/cherry/mastersmith/common/paging/PagingTest.java（実在） | 網羅 |
| FR1.4 | FR | U3 BR1.2・U3 BR1.5、US1.1 | BR の OK 2 件、US1.1 の AC 13/13 件が OK | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminListApiIT.java（実在） | 網羅 |
| FR1.5 | FR | U2 BR1.2・U2 BR3.1・U2 BR3.2・U3 BR1.1・U3 BR1.4、US1.1 | BR の OK 5 件、US1.1 の AC 13/13 件が OK | backend/src/test/java/cherry/mastersmith/common/paging/PagingTest.java（実在） | 網羅 |
| FR1.6 | FR | U3 BR1.7・U3 BR1.8・U3 BR7.5、US1.1・US4.1 | BR の OK 3 件、US1.1・US4.1 の AC 25/25 件が OK | backend/src/test/java/cherry/mastersmith/auth/domain/LockViewTest.java（実在） | 網羅。条件つきの AC（N-19）を含む |
| FR2.1 | FR | U3 BR4.1・U3 BR4.2、US2.1 | BR の OK 2 件、US2.1 の AC 14/14 件が OK | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminOperationsApiIT.java（実在） | 網羅。条件つきの AC（N-19）を含む |
| FR2.2 | FR | U3 BR4.1・U3 BR4.2、US2.1・US2.2 | BR の OK 2 件、US2.1・US2.2 の AC 19/20 件が OK | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminOperationsApiIT.java（実在） | 網羅（AC2.2.6 はこの段でテストのソースで確かめた）。条件つきの AC（N-19）を含む |
| FR2.3 | FR | U3 BR4.2、US2.1・US2.2 | BR の OK 1 件、US2.1・US2.2 の AC 19/20 件が OK | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminOperationsApiIT.java（実在） | 網羅（AC2.2.6 はこの段でテストのソースで確かめた）。条件つきの AC（N-19）を含む |
| FR2.4 | FR | U3 BR2.2、US2.1 | BR の OK 1 件、US2.1 の AC 14/14 件が OK | backend/src/test/java/cherry/mastersmith/useradmin/domain/RejectionPolicyTest.java（実在） | 網羅。条件つきの AC（N-19）を含む |
| FR2.5 | FR | U3 BR2.2、US2.1 | BR の OK 1 件、US2.1 の AC 14/14 件が OK | backend/src/test/java/cherry/mastersmith/useradmin/domain/RejectionPolicyTest.java（実在） | 網羅。条件つきの AC（N-19）を含む |
| FR3.1 | FR | U1 BR1.1・U3 BR4.3・U3 BR4.4、US3.1 | BR の OK 3 件、US3.1 の AC 12/12 件が OK | backend/src/main/java/cherry/mastersmith/user/domain/User.java（実在） | 網羅。条件つきの AC（N-19）を含む |
| FR3.2 | FR | U1 BR1.3・U1 BR2.1・U1 BR3.1・U1 BR4.1、US3.2 | BR の OK 4 件、US3.2 の AC 8/10 件が OK | backend/src/main/java/cherry/mastersmith/user/service/UserSummary.java（実在） | 網羅（AC3.2.9・AC3.2.10 はこの段でテストのソースで確かめた） |
| FR3.3 | FR | U1 BR3.3・U1 BR5.1・U1 BR5.4・U3 BR4.3・U3 BR4.4、US3.1・US3.2 | BR の OK 5 件、US3.1・US3.2 の AC 20/22 件が OK | backend/src/test/java/cherry/mastersmith/auth/web/SuspendedUserAuthenticationIT.java（実在） | 網羅（AC3.2.9・AC3.2.10 はこの段でテストのソースで確かめた）。条件つきの AC（N-19）を含む |
| FR3.4 | FR | U1 BR2.4・U1 BR3.1・U1 BR4.1・U1 BR6.1・U1 BR6.3、US3.2 | BR の OK 5 件、US3.2 の AC 8/10 件が OK | backend/src/test/java/cherry/mastersmith/auth/web/SuspendedUserAuthenticationIT.java（実在） | 網羅（AC3.2.9・AC3.2.10 はこの段でテストのソースで確かめた） |
| FR3.5 | FR | U1 BR6.2・U3 BR4.4、US3.2 | BR の OK 2 件、US3.2 の AC 8/10 件が OK | backend/src/test/java/cherry/mastersmith/auth/web/AccessTokenApiIT.java（実在） | 網羅（AC3.2.9・AC3.2.10 はこの段でテストのソースで確かめた） |
| FR3.6 | FR | U3 BR2.2、US3.1 | BR の OK 1 件、US3.1 の AC 12/12 件が OK | backend/src/test/java/cherry/mastersmith/useradmin/domain/RejectionPolicyTest.java（実在） | 網羅。条件つきの AC（N-19）を含む |
| FR3.7 | FR | U3 BR2.2、US3.1 | BR の OK 1 件、US3.1 の AC 12/12 件が OK | backend/src/test/java/cherry/mastersmith/useradmin/domain/RejectionPolicyTest.java（実在） | 網羅。条件つきの AC（N-19）を含む |
| FR3.8 | FR | U1 BR4.4・U3 BR7.1、US3.2 | BR の OK 2 件、US3.2 の AC 8/10 件が OK | backend/src/test/java/cherry/mastersmith/access/web/AccessDeniedEventsIT.java（実在） | 網羅（AC3.2.9・AC3.2.10 はこの段でテストのソースで確かめた） |
| FR3.9 | FR | U1 BR6.4、US3.2 | BR の OK 1 件、US3.2 の AC 8/10 件が OK | backend/src/test/java/cherry/mastersmith/invitation/web/InvitationAdminApiIT.java（実在） | 網羅（AC3.2.9・AC3.2.10 はこの段でテストのソースで確かめた） |
| FR4.1 | FR | U3 BR3.2、US2.1・US3.1 | BR の OK 1 件、US2.1・US3.1 の AC 26/26 件が OK | backend/src/test/java/cherry/mastersmith/useradmin/service/UserAdminConcurrencyIT.java（実在） | 網羅。条件つきの AC（N-19）を含む |
| FR4.2 | FR | U3 BR2.4・U3 BR3.2、US2.1・US3.1 | BR の OK 2 件、US2.1・US3.1 の AC 26/26 件が OK | backend/src/test/java/cherry/mastersmith/useradmin/service/UserAdminServiceTest.java（実在） | 網羅。条件つきの AC（N-19）を含む |
| FR4.3 | FR | U3 BR3.2、US2.1・US3.1 | BR の OK 1 件、US2.1・US3.1 の AC 26/26 件が OK | backend/src/test/java/cherry/mastersmith/useradmin/service/UserAdminConcurrencyIT.java（実在） | 網羅。条件つきの AC（N-19）を含む |
| FR4.4 | FR | U3 BR3.2、US2.1・US3.1 | BR の OK 1 件、US2.1・US3.1 の AC 26/26 件が OK | backend/src/test/java/cherry/mastersmith/useradmin/service/UserAdminConcurrencyIT.java（実在） | 網羅。条件つきの AC（N-19）を含む |
| FR5.1 | FR | U3 BR4.5、US4.1 | BR の OK 1 件、US4.1 の AC 12/12 件が OK | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminResetLoginFailuresApiIT.java（実在） | 網羅。条件つきの AC（N-19）を含む |
| FR5.2 | FR | U3 BR2.2、US4.1 | BR の OK 1 件、US4.1 の AC 12/12 件が OK | backend/src/test/java/cherry/mastersmith/useradmin/domain/RejectionPolicyTest.java（実在） | 網羅。条件つきの AC（N-19）を含む |
| FR5.3 | FR | （FR5.3 を `source` に挙げる BR なし）、US4.1 | US4.1 の AC 12/12 件が OK | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminResetLoginFailuresApiIT.java（実在） | 網羅。条件つきの AC（N-19）を含む |
| FR5.4 | FR | U3 BR3.4・U3 BR4.5、US4.1 | BR の OK 2 件、US4.1 の AC 12/12 件が OK | backend/src/test/java/cherry/mastersmith/auth/service/FailureResetPortIT.java（実在） | 網羅。条件つきの AC（N-19）を含む |
| FR6.1 | FR | U3 BR5.2、US5.1 | BR の OK 1 件、US5.1 の AC 8/8 件が OK | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminProfileApiIT.java（実在） | 網羅。条件つきの AC（N-19）を含む |
| FR6.2 | FR | U3 BR5.1、US5.1 | BR の OK 1 件、US5.1 の AC 8/8 件が OK | backend/src/test/java/cherry/mastersmith/user/domain/ProfileValidationTest.java（実在） | 網羅。条件つきの AC（N-19）を含む |
| FR6.3 | FR | U3 BR5.2、US5.1 | BR の OK 1 件、US5.1 の AC 8/8 件が OK | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminProfileApiIT.java（実在） | 網羅。条件つきの AC（N-19）を含む |
| FR6.4 | FR | U3 BR5.3・U3 BR6.4、US5.1 | BR の OK 2 件、US5.1 の AC 8/8 件が OK | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminProfileApiIT.java（実在） | 網羅。条件つきの AC（N-19）を含む |
| FR6.5 | FR | U3 BR5.2・U3 BR5.4、US5.1 | BR の OK 2 件、US5.1 の AC 8/8 件が OK | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminProfileApiIT.java（実在） | 網羅。条件つきの AC（N-19）を含む |
| FR6.6 | FR | U3 BR5.2、US5.1 | BR の OK 1 件、US5.1 の AC 8/8 件が OK | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminProfileApiIT.java（実在） | 網羅。条件つきの AC（N-19）を含む |
| FR7.1 | FR | U3 BR6.1、US2.1・US3.1・US4.1 | BR の OK 1 件、US2.1・US3.1・US4.1 の AC 38/38 件が OK | backend/src/test/java/cherry/mastersmith/audit/service/UserAdminAuditIT.java（実在） | 網羅。条件つきの AC（N-19）を含む |
| FR7.2 | FR | U3 BR6.1、US2.1・US3.1・US4.1 | BR の OK 1 件、US2.1・US3.1・US4.1 の AC 38/38 件が OK | backend/src/test/java/cherry/mastersmith/audit/service/UserAdminAuditIT.java（実在） | 網羅。条件つきの AC（N-19）を含む |
| FR7.3 | FR | U3 BR1.9・U3 BR5.3・U3 BR6.4、US1.1・US2.2・US5.1 | BR の OK 3 件、US1.1・US2.2・US5.1 の AC 26/27 件が OK | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminListApiIT.java（実在） | 網羅（AC2.2.6 はこの段でテストのソースで確かめた）。条件つきの AC（N-19）を含む |
| FR7.4 | FR | U3 BR6.2、US1.1・US3.2 | BR の OK 1 件、US1.1・US3.2 の AC 21/23 件が OK | backend/src/test/java/cherry/mastersmith/audit/service/UserAdminAuditIT.java（実在） | 網羅（AC3.2.9・AC3.2.10 はこの段でテストのソースで確かめた） |
| FR7.5 | FR | U3 BR2.1・U3 BR6.1、US2.1・US3.1・US4.1 | BR の OK 2 件、US2.1・US3.1・US4.1 の AC 38/38 件が OK | backend/src/test/java/cherry/mastersmith/useradmin/domain/RejectionPolicyTest.java（実在） | 網羅。条件つきの AC（N-19）を含む |
| FR7.6 | FR | U3 BR2.7・U3 BR6.2、US2.1・US3.1・US4.1 | BR の OK 2 件、US2.1・US3.1・US4.1 の AC 38/38 件が OK | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminOperationsApiIT.java（実在） | 網羅。条件つきの AC（N-19）を含む |
| FR8.1 | FR | （FR8.1 を `source` に挙げる BR なし。画面の置き場は U5 の機能設計 2節）、US1.1・US2.2 | US1.1・US2.2 の AC 18/19 件が OK | frontend/src/features/useradmin/registration.test.tsx（実在） | 網羅（AC2.2.6 はこの段でテストのソースで確かめた） |
| FR8.2 | FR | U3 BR2.6・U3 BR7.1、US1.1・US2.1・US3.1・US4.1・US5.1 | BR の OK 2 件、US1.1・US2.1・US3.1・US4.1・US5.1 の AC 59/59 件が OK | backend/src/test/java/cherry/mastersmith/useradmin/service/UserAdminConcurrencyIT.java（実在） | 網羅。条件つきの AC（N-19）を含む |
| FR8.3 | FR | U1 BR1.6・U3 BR5.4・U3 BR7.2・U3 BR7.3、US2.1・US5.1 | BR の OK 4 件、US2.1・US5.1 の AC 22/22 件が OK | backend/src/test/java/cherry/mastersmith/user/web/MePreferencesApiIT.java（実在） | 網羅。条件つきの AC（N-19）を含む |
| NFR1 | NFR | U1 NFR1.1〜1.5・U3 NFR1.1〜1.5・U4 NFR1.1〜1.5・U5 NFR1.1〜1.2 | OK 17 件 | backend/src/test/java/cherry/mastersmith/auth/web/SuspendedUserAuthenticationIT.java（実在） | 網羅 |
| NFR2 | NFR | U1 NFR2.1〜2.3（ほかの単位は N/A） | OK 3 件 | backend/src/test/java/cherry/mastersmith/auth/web/SuspendedUserAuthenticationIT.java（実在） | 網羅 |
| NFR3 | NFR | U1 NFR3.1〜3.2・U2 NFR3.1・U3 NFR3.1〜3.4・U4 NFR3.1〜3.2・U5 NFR3.1〜3.5 | OK 14 件 | backend/src/test/java/cherry/mastersmith/auth/web/AuthSuspensionSecretLeakIT.java（実在） | 網羅 |
| NFR4 | NFR | U3 NFR4.1〜4.5（ほかの単位は N/A） | OK 5 件 | backend/src/test/java/cherry/mastersmith/useradmin/service/UserAdminConcurrencyIT.java（実在） | 網羅 |
| NFR5 | NFR | U1 NFR5.1〜5.5・U2 NFR5.1〜5.2・U3 NFR5.1〜5.11・U4 NFR5.1・U5 NFR5.1〜5.6 | OK 12 件・Deferred 13 件（U1 NFR5.2・NFR5.4、U3 NFR5.1・5.3〜5.6・5.8〜5.11、U5 NFR5.1・5.2） | backend/src/test/java/cherry/mastersmith/auth/web/SuspendedUserAuthenticationIT.java（実在） | 網羅（持ち越しあり） |
| NFR6 | NFR | U1 NFR6.1〜6.2・U3 NFR6.1〜6.3 | OK 3 件・Deferred 2 件（U3 NFR6.2・NFR6.3） | backend/src/test/java/cherry/mastersmith/audit/service/AuditAuthenticationEventsIT.java（実在） | 網羅（持ち越しあり） |
| NFR7 | NFR | U4 NFR7.1〜7.3・U5 NFR7.1〜7.3 | OK 6 件 | frontend/src/app/admin-forbidden/AdminForbiddenView.test.tsx（実在） | 網羅（U5 NFR7.1 は目標の判定では Not Met、N-19） |
| NFR8 | NFR | U3 NFR8.1・U4 NFR8.1〜8.2・U5 NFR8.1〜8.3 | OK 6 件 | backend/src/test/java/cherry/mastersmith/useradmin/domain/UserAdminProblemTypesTest.java（実在） | 網羅 |
| NFR9 | NFR | U1 NFR9.1〜9.6・U2 NFR9.1〜9.11・U3 NFR9.1〜9.8・U4 NFR9.1〜9.12・U5 NFR9.1〜9.9 | OK 46 件 | backend/src/test/java/cherry/mastersmith/auth/service/LoginServiceTest.java（実在） | 網羅 |
| NFR10 | NFR | U1 NFR10.1〜10.3・U3 NFR10.1〜10.2 | OK 2 件（U1 NFR10.3・U3 NFR10.1）・Deferred 2 件（U1 NFR10.1・NFR10.2）・N/A 1 件（U3 NFR10.2、移行を足していない） | README.md（実在） | 網羅（持ち越しあり） |
| NFR11 | NFR | U1 NFR11.1〜11.2・U2 NFR11.1・U3 NFR11.1〜11.2 | OK 5 件 | backend/src/test/java/cherry/mastersmith/ArchitectureTest.java（実在） | 網羅 |
| AC1.1.1 | AC | U2 AC1.1.1・U3 AC1.1.1 | OK（U2・U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/common/paging/PagingTest.java（実在） | 網羅 |
| AC1.1.2 | AC | U3 AC1.1.2 | OK（U3）・Deferred（U2・U5） | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminListApiIT.java（実在） | 網羅 |
| AC1.1.3 | AC | U3 AC1.1.3 | OK（U3）・Deferred（U2・U5） | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminListApiIT.java（実在） | 網羅 |
| AC1.1.4 | AC | U3 AC1.1.4 | OK（U3）・Deferred（U2・U5） | backend/src/test/java/cherry/mastersmith/user/repository/UserAdminQueriesIT.java（実在） | 網羅 |
| AC1.1.5 | AC | U2 AC1.1.5・U3 AC1.1.5 | OK（U2・U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/common/paging/PagingTest.java（実在） | 網羅 |
| AC1.1.6 | AC | U3 AC1.1.6 | OK（U3）・Deferred（U2・U5） | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminSecretLeakIT.java（実在） | 網羅 |
| AC1.1.7 | AC | U2 AC1.1.7・U3 AC1.1.7 | OK（U2・U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/common/paging/PagingTest.java（実在） | 網羅 |
| AC1.1.8 | AC | U5 AC1.1.8 | OK（U5）・Deferred（U2・U3） | frontend/src/features/useradmin/UserAdminPage.test.tsx（実在） | 網羅 |
| AC1.1.9 | AC | U5 AC1.1.9 | OK（U5）・Deferred（U2・U3） | frontend/src/features/useradmin/UserAdminPage.test.tsx（実在） | 網羅 |
| AC1.1.10 | AC | U5 AC1.1.10 | OK（U5）・Deferred（U2・U3） | frontend/src/features/useradmin/UserAdminPage.test.tsx（実在） | 網羅 |
| AC1.1.11 | AC | U5 AC1.1.11 | OK（U5）・Deferred（U2・U3） | frontend/src/features/useradmin/UserSearchBox.test.tsx（実在） | 網羅 |
| AC1.1.12 | AC | U5 AC1.1.12 | OK（U5）・Deferred（U2・U3） | frontend/src/features/useradmin/UserAdminPage.test.tsx（実在） | 網羅 |
| AC1.1.13 | AC | U3 AC1.1.13 | OK（U3）・Deferred（U2・U5） | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminListApiIT.java（実在） | 網羅 |
| AC2.1.1 | AC | U3 AC2.1.1 | OK（U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminOperationsApiIT.java（実在） | 網羅 |
| AC2.1.2 | AC | U3 AC2.1.2 | OK（U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminOperationsApiIT.java（実在） | 網羅 |
| AC2.1.3 | AC | U3 AC2.1.3 | OK（U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminOperationsApiIT.java（実在） | 網羅 |
| AC2.1.4 | AC | U3 AC2.1.4 | OK（U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminOperationsApiIT.java（実在） | 網羅 |
| AC2.1.5 | AC | U3 AC2.1.5 | OK（U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminOperationsApiIT.java（実在） | 網羅 |
| AC2.1.6 | AC | U3 AC2.1.6 | OK（U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/useradmin/service/UserAdminConcurrencyIT.java（実在） | 網羅 |
| AC2.1.7 | AC | U3 AC2.1.7 | OK（U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminMassAssignmentIT.java（実在） | 網羅 |
| AC2.1.8 | AC | U5 AC2.1.8 | OK（U5）・Deferred（U3） | frontend/src/features/useradmin/ConfirmActionDialog.test.tsx（実在） | 条件つき（N-19） |
| AC2.1.9 | AC | U5 AC2.1.9 | OK（U5）・Deferred（U3） | frontend/src/features/useradmin/UserRowActions.test.tsx（実在） | 網羅 |
| AC2.1.10 | AC | U3 AC2.1.10 | OK（U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminOperationsApiIT.java（実在） | 網羅 |
| AC2.1.11 | AC | U3 AC2.1.11 | OK（U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/useradmin/service/UserAdminOperationsIT.java（実在） | 網羅 |
| AC2.1.12 | AC | U3 AC2.1.12 | OK（U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/useradmin/service/UserAdminConcurrencyIT.java（実在） | 網羅 |
| AC2.1.13 | AC | U5 AC2.1.13 | OK（U5）・Deferred（U3） | frontend/src/features/useradmin/UserAdminPage.test.tsx（実在） | 網羅 |
| AC2.1.14 | AC | U3 AC2.1.14 | OK（U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminOperationsApiIT.java（実在） | 網羅 |
| AC2.2.1 | AC | U4 AC2.2.1 | OK（U4）・Deferred（U5） | frontend/src/app/admin-forbidden/AdminForbiddenView.test.tsx（実在） | 網羅 |
| AC2.2.2 | AC | U4 AC2.2.2 | OK（U4）・Deferred（U5） | frontend/src/app/layout/ShellLayout.test.tsx（実在） | 網羅 |
| AC2.2.3 | AC | U4 AC2.2.3 | OK（U4）・Deferred（U5） | frontend/src/app/layout/ShellLayout.test.tsx（実在） | 網羅 |
| AC2.2.4 | AC | U4 AC2.2.4 | OK（U4）・Deferred（U5） | frontend/src/features/invitation/InvitationAdminPage.test.tsx（実在） | 網羅 |
| AC2.2.5 | AC | U4 AC2.2.5 | OK（U4）・Deferred（U5） | frontend/src/app/routing/AppRouter.test.tsx（実在） | 網羅 |
| AC2.2.6 | AC | U3（この段で判定）: UserAdminOperationsApiIT#flagChangeTakesEffectOnTheNextRequest・UserAdminListApiIT#authorization・UserAdminAuditIT#notRecorded、E2E 110 の順9 | traceability.json は Deferred のみ（U4→U3、U5→U4・U3。どの単位も OK にしていない） | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminOperationsApiIT.java（実在）・backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminListApiIT.java（実在） | 網羅（この段でテストのソースで判定。段全体のレビュー R-02） |
| AC3.1.1 | AC | U3 AC3.1.1 | OK（U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminOperationsApiIT.java（実在） | 網羅 |
| AC3.1.2 | AC | U3 AC3.1.2 | OK（U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminOperationsApiIT.java（実在） | 網羅 |
| AC3.1.3 | AC | U3 AC3.1.3 | OK（U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminOperationsApiIT.java（実在） | 網羅 |
| AC3.1.4 | AC | U3 AC3.1.4 | OK（U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminOperationsApiIT.java（実在） | 網羅 |
| AC3.1.5 | AC | U3 AC3.1.5 | OK（U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/useradmin/service/UserAdminConcurrencyIT.java（実在） | 網羅 |
| AC3.1.6 | AC | U3 AC3.1.6 | OK（U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminOperationsApiIT.java（実在） | 網羅 |
| AC3.1.7 | AC | U5 AC3.1.7 | OK（U5）・Deferred（U3） | frontend/src/features/useradmin/ConfirmActionDialog.test.tsx（実在） | 条件つき（N-19） |
| AC3.1.8 | AC | U5 AC3.1.8 | OK（U5）・Deferred（U3） | frontend/src/features/useradmin/UserRowActions.test.tsx（実在） | 網羅 |
| AC3.1.9 | AC | U3 AC3.1.9 | OK（U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/useradmin/service/UserAdminOperationsIT.java（実在） | 網羅 |
| AC3.1.10 | AC | U3 AC3.1.10 | OK（U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminOperationsApiIT.java（実在） | 網羅 |
| AC3.1.11 | AC | U5 AC3.1.11 | OK（U5）・Deferred（U3） | frontend/src/features/useradmin/UserAdminPage.test.tsx（実在） | 網羅 |
| AC3.1.12 | AC | U3 AC3.1.12 | OK（U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminOperationsApiIT.java（実在） | 網羅 |
| AC3.2.1 | AC | U1 AC3.2.1 | OK（U1）・Deferred（U3） | backend/src/test/java/cherry/mastersmith/auth/web/SuspendedUserAuthenticationIT.java（実在） | 網羅 |
| AC3.2.2 | AC | U1 AC3.2.2 | OK（U1）・Deferred（U3） | backend/src/test/java/cherry/mastersmith/auth/web/SuspendedUserAuthenticationIT.java（実在） | 網羅 |
| AC3.2.3 | AC | U1 AC3.2.3 | OK（U1）・Deferred（U3） | backend/src/test/java/cherry/mastersmith/auth/web/LoginApiIT.java（実在） | 網羅 |
| AC3.2.4 | AC | U1 AC3.2.4 | OK（U1）・Deferred（U3） | backend/src/test/java/cherry/mastersmith/auth/web/SuspendedUserAuthenticationIT.java（実在） | 網羅 |
| AC3.2.5 | AC | U1 AC3.2.5 | OK（U1）・Deferred（U3） | backend/src/test/java/cherry/mastersmith/auth/web/SuspendedUserAuthenticationIT.java（実在） | 網羅 |
| AC3.2.6 | AC | U1 AC3.2.6 | OK（U1）・Deferred（U3） | backend/src/test/java/cherry/mastersmith/invitation/web/InvitationAdminApiIT.java（実在） | 網羅 |
| AC3.2.7 | AC | U1 AC3.2.7 | OK（U1）・Deferred（U3） | backend/src/test/java/cherry/mastersmith/auth/web/SuspendedUserAuthenticationIT.java（実在） | 網羅 |
| AC3.2.8 | AC | U1 AC3.2.8 | OK（U1）・Deferred（U3） | backend/src/test/java/cherry/mastersmith/auth/service/LoginServiceTest.java（実在） | 網羅 |
| AC3.2.9 | AC | U5（この段で判定）: E2E 110 の順7、U1: SuspendedUserAuthenticationIT | traceability.json は Deferred のみ（U1→U5、U3→U1、U5→U1。どの単位も OK にしていない） | frontend/e2e/110-user-admin-flow.e2e.ts（実在）・backend/src/test/java/cherry/mastersmith/auth/web/SuspendedUserAuthenticationIT.java（実在） | 網羅（この段でテストのソースで判定。段全体のレビュー R-02） |
| AC3.2.10 | AC | U5（この段で判定）: E2E 110 の順7、U1: SuspendedUserAuthenticationIT#responsesAreIndistinguishable・LoginApiIT、既存の LoginForm.test.tsx | traceability.json は Deferred のみ（U1→U5、U3→U1、U5→U1。どの単位も OK にしていない） | frontend/e2e/110-user-admin-flow.e2e.ts（実在）・backend/src/test/java/cherry/mastersmith/auth/web/LoginApiIT.java（実在） | 網羅（この段でテストのソースで判定。段全体のレビュー R-02） |
| AC4.1.1 | AC | U3 AC4.1.1 | OK（U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminResetLoginFailuresApiIT.java（実在） | 網羅 |
| AC4.1.2 | AC | U3 AC4.1.2 | OK（U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminResetLoginFailuresApiIT.java（実在） | 網羅 |
| AC4.1.3 | AC | U3 AC4.1.3 | OK（U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminResetLoginFailuresApiIT.java（実在） | 網羅 |
| AC4.1.4 | AC | U3 AC4.1.4 | OK（U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminResetLoginFailuresApiIT.java（実在） | 網羅 |
| AC4.1.5 | AC | U3 AC4.1.5 | OK（U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/useradmin/service/ResetLoginConcurrencyIT.java（実在） | 網羅 |
| AC4.1.6 | AC | U3 AC4.1.6 | OK（U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/audit/service/UserAdminAuditIT.java（実在） | 網羅 |
| AC4.1.7 | AC | U3 AC4.1.7 | OK（U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminResetLoginFailuresApiIT.java（実在） | 網羅 |
| AC4.1.8 | AC | U3 AC4.1.8 | OK（U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminResetLoginFailuresApiIT.java（実在） | 網羅 |
| AC4.1.9 | AC | U5 AC4.1.9 | OK（U5）・Deferred（U3） | frontend/src/features/useradmin/ConfirmActionDialog.test.tsx（実在） | 条件つき（N-19） |
| AC4.1.10 | AC | U3 AC4.1.10・U5 AC4.1.10 | OK（U3・U5） | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminResetLoginFailuresApiIT.java（実在） | 網羅 |
| AC4.1.11 | AC | U3 AC4.1.11 | OK（U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminBusyApiIT.java（実在） | 網羅 |
| AC4.1.12 | AC | U3 AC4.1.12 | OK（U3）・Deferred（U5） | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminOperationsApiIT.java（実在） | 網羅 |
| AC5.1.1 | AC | U3 AC5.1.1 | OK（U3）・Deferred（U4・U5） | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminProfileApiIT.java（実在） | 網羅 |
| AC5.1.2 | AC | U5 AC5.1.2 | OK（U5）・Deferred（U3・U4） | frontend/src/features/useradmin/UserAdminPage.test.tsx（実在） | 網羅 |
| AC5.1.3 | AC | U4 AC5.1.3・U5 AC5.1.3 | OK（U4・U5）・Deferred（U3） | frontend/src/app/display-settings/DisplaySettingsProvider.test.tsx（実在） | 網羅 |
| AC5.1.4 | AC | U3 AC5.1.4 | OK（U3）・Deferred（U4・U5） | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminProfileApiIT.java（実在） | 網羅 |
| AC5.1.5 | AC | U3 AC5.1.5 | OK（U3）・Deferred（U4・U5） | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminProfileApiIT.java（実在） | 網羅 |
| AC5.1.6 | AC | U3 AC5.1.6 | OK（U3）・Deferred（U4・U5） | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminProfileApiIT.java（実在） | 網羅 |
| AC5.1.7 | AC | U5 AC5.1.7 | OK（U5）・Deferred（U3・U4） | frontend/src/features/useradmin/EditProfileDialog.test.tsx（実在） | 条件つき（N-19） |
| AC5.1.8 | AC | U3 AC5.1.8 | OK（U3）・Deferred（U4・U5） | backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminProfileApiIT.java（実在） | 網羅 |

## 未網羅の一覧

ID の単位で未網羅（Deferred・N/A・GAP のみで、この段の判定でも網羅にできない、target が無い、連鎖が切れている）のものは **0 件**です。

### `traceability.json` の OK だけではつながらず、この段でテストのソースを読んで判定したもの（段全体のレビューの R-02）

承認済みの `traceability.json` は書き換えていません（`construction/code-generation/gate-decisions.md` の2節の決定）。どの単位も、自分を主の単位にせず、ほかの単位へ Deferred にしていました（AC2.2.6 は U4→U3・U5→U4・U3、AC3.2.9・AC3.2.10 は U1→U5・U3→U1・U5→U1）。

| ID | 受け入れ基準の要点 | 確かめたテスト（ソースを読んだ） | 判定と根拠 |
|---|---|---|---|
| AC2.2.6 | B の印が外された後に B が管理の API を呼ぶと、監査に今までどおり「アクセスの拒否」（管理者でない）が残る | (1) `useradmin/web/UserAdminOperationsApiIT#flagChangeTakesEffectOnTheNextRequest`: 印を外した直後の次の要求で一覧の API が 403 `ACCESS_DENIED`。(2) `useradmin/web/UserAdminListApiIT#authorization`: 管理者でない利用者の 403 で、監査に `ACCESS_DENIED NOT_ADMIN` と道（`/api/admin/users`）が1行残る。(3) `audit/service/UserAdminAuditIT#notRecorded`: 入口の 403 は既存のアクセスの拒否（`ACCESS_DENIED NOT_ADMIN`）だけが残る。(4) E2E 110 の順9: 画面と本物のサーバーを通した 403 `ACCESS_DENIED` | 網羅。サーバーは要求ごとに内部DB の今の印で判定する（U3 の NFR1.2）ため、印を外された B は管理者でない利用者と同じ経路で拒否され、同じ監査が残る。ただし「外した直後の要求の監査の行」を1つのテストで続けて確かめるものは無く、(1) と (2)・(3) の組み合わせによる判定である |
| AC3.2.9 | 画面を開いたまま止められた B が次に操作すると、401 → 更新も 401 → ログインの画面。止められたことを示す文言は出さない | (1) E2E `110-user-admin-flow.e2e.ts` の順7（step `suspend the user and the user cannot continue`）: 管理者が止めた後、U のブラウザでプリファレンスを開くと API が 401、更新（`POST /api/auth/refresh`）も 401、`login-layout` が出る。(2) `auth/web/SuspendedUserAuthenticationIT`: 停止中のアクセストークンは 401 `AUTHENTICATION_REQUIRED`、更新は 401 `REFRESH_FAILED`、本文に `SUSPENDED` が無い | 網羅。ログインの画面へ移る画面の動きは既存のまま（ほかの理由でログインが切れたときと同じ）で、停止を示す値は応答にも画面にも無い |
| AC3.2.10 | 止められた B がログインの画面で正しいパスワードを入れても、文言はパスワードを誤ったときと同じで、停止・ロックを見分けられない | (1) E2E 110 の順7: 正しいパスワードで `login-form-error-text` が「メールアドレスまたはパスワードが正しくありません」（パスワードの誤りの文言）。(2) `auth/web/SuspendedUserAuthenticationIT#responsesAreIndistinguishable`: 停止中のログインの応答とパスワードの誤りの応答（状態コード・code・本文）が同じ。(3) `auth/web/LoginApiIT`: 停止中・ロック中・誤りで読み書きの回数と本文がそろう。(4) 既存の `features/auth/LoginForm.test.tsx`「shows the same message when the account is locked」 | 網羅 |

### 条件つき（N-19）

| ID | 中身 | 扱い |
|---|---|---|
| AC2.1.8・AC3.1.7・AC4.1.9・AC5.1.7 | 確かめ・入力の表示（S3・S4）を閉じた後のフォーカスを、開いた行の「操作」へ戻す（U5 の機能設計 W5 の 2・W12）。画面のテスト（jsdom）の `ConfirmActionDialog.test.tsx`・`EditProfileDialog.test.tsx` は通るが、実際のブラウザでは make-you-chic-ui の `useFocusTrap` が背景に `inert` が付いたまま前の要素へ戻そうとし、フォーカスが body に移る（N-19）。E2E は `inert` が外れるのを待つ形のため、この動きを確かめていない | 後の Intent への持ち越しとして、コード生成の承認の場で承認済み（`gate-decisions.md` の 2節 R-01・4節）。make-you-chic-ui の直った版へ固定先を上げる専用のコミット（C2′）と、E2E 110 の `confirmAction`・120 の `expectBackgroundInteractive` を「閉じた後にフォーカスが行の『操作』に戻る」ことを確かめる形に替える。目標の判定では U5-NFR7.1 を Not Met とした（`build-and-test-summary.md`） |

### 持ち越しのある NFR（ID としては網羅）

- **NFR5（応答時間・指標）**: U1 NFR5.2・NFR5.4、U3 NFR5.1・NFR5.3〜NFR5.6・NFR5.8 → performance-validation。U3 NFR5.9〜NFR5.11 → observability-setup（NFR5.11 は performance-validation と共同）。U5 NFR5.1・NFR5.2 → performance-validation・observability-setup・feedback-optimization（記録のみ、本番での判定）。
- **NFR6（接続の使い方）**: U3 NFR6.2・NFR6.3 → performance-validation（上限に届く形を含める）。
- **NFR10（スキーマの変更）**: U1 NFR10.1・NFR10.2 → deployment-execution（事後の裏付け）・deployment-pipeline（戻しの手順）。

## 気づいた食い違い

1. **単位の間で Deferred が回る**: AC2.2.6・AC3.2.9・AC3.2.10 は、どの単位も自分を主にせず、ほかの単位へ Deferred にしていた（上の表）。機能設計の `traceability.json` で主の単位を決めずに受け持たない側だけを書くと、コード生成で誰も OK にしない形が起きる。
2. **target の付け方がそろっていない**: U1 の BR は本番のソースを指すもの（例: BR1.1 → `User.java`、BR1.3 → `UserSummary.java`）が多く、U3 の BR・AC はテストを指す。U4・U5 の NFR の枝番にも、本番のソース・設定を指すもの（例: U4 NFR9.4 → `check-bundle-size.mjs`、U5 NFR9.3 → `vendor/make-you-chic-ui`）がある。判定にはテストのソースと実測を合わせて使った。
3. **FR から BR へつながらない FR**: FR5.3（失敗回数を戻した直後からしきい値まで失敗できる）と FR8.1（画面の置き場）は、どの `rules.md` の BR の `source` にも挙がっていない。FR5.3 は US4.1 の AC（`UserAdminResetLoginFailuresApiIT`）で、FR8.1 は US1.1・US2.2 の AC と U5 の `registration.test.tsx` でたどった。
4. **U3 の `traceability.json` に他の単位の ID が N/A で入る**: U3 の NFR2.3・NFR6.7 は、U3 の NFR 要件の文書が出典として引いた U1・前の Intent の ID をセンサーが拾うため N/A で足したもの（U3 の G-50）。判定には使っていない。
5. **U5-NFR7.1 は `traceability.json` で OK だが目標の判定は Not Met**: `traceability.json` の OK の target は `UserRowActions.test.tsx`（jsdom）で、実際のブラウザの N-19 を映していない。

## Sources

- `aidlc/spaces/default/intents/260930-user-admin/inception/requirements-analysis/requirements.md`（FR・NFR）
- `aidlc/spaces/default/intents/260930-user-admin/inception/user-stories/stories.md`（AC）・`inception/user-stories/traceability.json`（FR → US）
- `aidlc/spaces/default/intents/260930-user-admin/construction/u1-user-suspension/` 〜 `u5-user-admin-ui/` の `functional-design/rules.md`・`nfr-requirements/traceability.json`・`code-generation/traceability.json`
- `aidlc/spaces/default/intents/260930-user-admin/construction/code-generation/gate-decisions.md`（2節 R-01・R-02、4節）、`construction/u5-user-admin-ui/code-generation/generation-notes.md`（「レビューの後」「Step 22（C2′ の前）」）
- テストのソース（読み取り）: `backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminOperationsApiIT.java`・`UserAdminListApiIT.java`、`backend/src/test/java/cherry/mastersmith/audit/service/UserAdminAuditIT.java`、`backend/src/test/java/cherry/mastersmith/auth/web/SuspendedUserAuthenticationIT.java`・`LoginApiIT.java`、`frontend/e2e/110-user-admin-flow.e2e.ts`、`frontend/src/features/auth/LoginForm.test.tsx`
- `aidlc/spaces/default/memory/project.md`（Way of Working: 2段の連鎖でたどる決まり）

## Assumptions & Open Questions

- AC2.2.6 は、印を外した直後の要求の監査の行を1つのテストで続けて確かめるものが無く、2つのテストの組み合わせで網羅と判定しました。この判定でよいか、目印のテストを後の Intent で足すかを承認の場で確かめます。
