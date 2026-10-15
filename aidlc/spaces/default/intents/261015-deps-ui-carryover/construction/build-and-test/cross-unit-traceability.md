# 要件の網羅の確かめ（Intent 261015-deps-ui-carryover）

## 判定

**不合格（条件つき）**。要件の FR・NFR 44 件のうち、コード生成の `traceability.json` で `OK` の 28 件は対象のファイルが実在する。`Deferred` の 13 件のうち 9 件（FR1.4・FR7.2・FR8.1〜FR8.4・FR9.1・FR9.3・NFR3 の一部）はこの段で確かめて満たした。FR9.4 は後半（`memory.peak`）が Not Met で、依頼者の決定で持ち越す。FR11.1〜FR11.3 は持ち主の段（Deployment Pipeline・Deployment Execution）がこの後に予定されている。FR12.1〜FR12.3 は記録の要件で `N/A`。ユーザーストーリーの段は無い（bugfix）。

コード生成の `traceability.json` は確定の後の成果物のため書き換えず、この段で確かめたものはこの文書に「この段で確かめた」と記録する（`project.md` の学び）。

## 要件ごとの網羅

| ID | traceability.json | この段の扱い | 対象・根拠 |
|---|---|---|---|
| FR1.1 | OK | — | `frontend/package.json`（④ の 4 件を含む） |
| FR1.2 | OK | — | `Dockerfile`（jdk・mailpit の固定先は `docker/hikari-pool.sh`・`README.md`・`perf/README.md`） |
| FR1.3 | OK | — | `.github/dependabot.yml`（axe-core の見送り） |
| FR1.4 | Deferred | この段で確かめた: Met | `test-results.md` 1〜3節（verify・E2E・CI の試行ごと） |
| FR2.1〜FR2.5 | OK | — | `ShellLayout.tsx`・`WorkRoleSwitcher.css`・`README.md`・`frontend/e2e/160-app-frame-accessibility.e2e.ts` |
| FR3.1 | OK | — | `DslSecretLeakIT.java` |
| FR3.2 | OK | — | `DslStatusPanel.tsx`（確かめは `DslStatusPanel.test.tsx`・`DslDiffTable.test.tsx`・E2E 060・120 の 360px） |
| FR3.3 | OK | — | `DslStartupIT.java`（ほかに `DslStatusPanel.test.tsx`・`DslSecretLeakIT.java`・E2E 060・120。コード生成のレビューの R-02） |
| FR4.1・FR4.2 | OK | — | `NavigationSecretLeakIT.java`・`MenuFilter.java` |
| FR5.1〜FR5.3 | OK | — | `SharedTreeNodeItem.tsx`・`validateRegistrations.ts`・`SharedTreeView.test.tsx` |
| FR6.1・FR6.2 | OK | — | `frontend/src/shared/slow-notice/useSlowNotice.ts`・`.test.tsx` |
| FR7.1 | OK | — | `perf/k6/scenarios.js` |
| FR7.2 | Deferred | この段で確かめた: Met | `perf-run-notes.md` (c) |
| FR8.1〜FR8.4 | Deferred | この段で確かめた: Met（最大 12.62 ms） | `perf-run-notes.md` (a)、FR8.3 は直す必要が無かった |
| FR9.1 | Deferred | この段で確かめた: Met | `perf-run-notes.md` (b)（657.4 MB・682.0 MB） |
| FR9.2 | OK | — | `SafeYamlParser.java`（境界の確かめは `SafeYamlParserTest`・`DslAdminApiIT`・`RoleTransferLimitsApiIT`。R-02） |
| FR9.3 | Deferred | この段で決めた | 設定は変えない（依頼者の決定「持ち越す」。根拠は `perf-run-notes.md` の内訳） |
| FR9.4 | Deferred | **Not Met（後半）・持ち越し** | ヒープは Met、`memory.peak` は 94.4%・93.1%（線 90% 未満） |
| FR10.1〜FR10.3 | OK | — | `RoleBusyLogTraceIT.java`・`GroupBusyLogIT.java` |
| FR11.1〜FR11.3 | Deferred | 持ち主の段がこの後に予定 | Deployment Pipeline・Deployment Execution（スモークテストに書体の表示と新しい JRE への `hikari-pool.sh` の接続を足す） |
| FR12.1〜FR12.3 | N/A | — | 記録の要件（計画の 5節・計画の承認） |
| NFR1 | OK | この段で確かめた: Met | `test-results.md` 1節 |
| NFR2 | OK | この段で確かめた: Met | 行 98.0%・分岐 92.6%（backend）、行 95.36%・分岐 90.36%（frontend） |
| NFR3 | Deferred | 目標は緩めずに測った | FR9.4 の後半だけ Not Met |
| NFR4 | OK | この段で確かめた: Met | `DslSecretLeakIT`・`NavigationSecretLeakIT`（伏せた範囲は下の注） |
| NFR5 | OK | この段で確かめた: Met（依頼者の決定で Intent の最後に1回） | E2E 232 件 passed。③・④ は統合の前に E2E を流していない（`team.md` との差） |
| NFR6 | OK | この段で確かめた: Met | OSV-Scanner の止める指摘 0 |

## 注（コード生成のレビューの持ち越し）

- R-01: `code-summary.md` 12節の「118 パス」は ④ の前の数で、今の `source-manifest.json` は 122 パス（④ のテスト3つと `BusinessNavigationProvider.test.tsx` を含む）。確定の後の成果物のため書き換えず、ここに記録する。
- R-02: 上の表の「対象・根拠」に、確かめるテストのファイルを併記した。
- R-04: 伏せたのは `DslError`・`DslErrorItem`・navigation の6つの record・`DslModel` の `toString`。`DslSchema`・`DslTable`・`DslMenuItem`・`DisplayName` などの下位の record は伏せていない。D-10 の A（名前は業務の名前として受け入れる）のとおりで、守られているのは `DslSecretLeakIT`・`NavigationSecretLeakIT` が通る経路である（依頼者が承認の場で受け入れた推奨 b）。
