# Build and Test の要約（261003-user-admin-followup）

- 範囲: bugfix（深さ Minimal、Test Strategy は Minimal）。zero-Unit。進め方は test-after（Testing Contract）。
- 対象: `develop` の `a1b41e7`。この段の中で `perf/README.md` を直し、`c50e64c` として統合した（文書だけ）。
- 結論: ビルドとすべてのテスト・検査は通った。測れる目標 28 件のうち 27 件が Met、1 件（FR4.2-c）が Unverified。段の定義の失敗の条件に当たるため、**段の結果は「成功」ではない**。依頼者はこの段で FR4.2-c の次の Intent への持ち越しを決めており、承認の場でその受け入れを確かめる。

## 1. ビルドの状態と前提

| 項目 | 状態 |
|---|---|
| ビルド（`verify` の 0〜9 の段） | 成功（BUILD SUCCESSFUL in 9m 6s、clean 付き） |
| 前提 | JDK 25・Node.js 24・Gitleaks・OSV-Scanner・colima（CPU 4・6GiB）、`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`、`caffeinate -i` |
| CI | run `37162114053` success（15 分 5 秒） |
| 配備したアプリ | k6 のあいだ止め（23:32:27Z）、同じコンテナで起動し直した（23:53:57Z、healthy、`/actuator/health` 200） |

手順は `build-instructions.md`。

## 2. テストの種類と手順書

| 種類 | 手順書 | 作った理由 |
|---|---|---|
| 単体（JUnit・Vitest） | `construction/code-generation/unit-test-instructions.md` | コード生成の段の成果物 |
| 結合・E2E | `integration-test-instructions.md` | Minimal では任意だが、FR1・FR2・FR5・FR6・FR8 の回帰が結合・E2E の段にあるため |
| 負荷（k6） | `performance-test-instructions.md` | FR4.2 の持ち主がこの段（Performance Validation の段が無い） |
| 安全 | `security-test-instructions.md` | S1（FR8・NFR2）の漏えいの確かめと、`verify` の安全の検査 |

## 3. カバレッジの見込みと実測

zero-Unit のため、単位ごとではなく全体とパッケージで見る。下限は行 80%・分岐 70%（バックエンドはパッケージごとにも当てる）。

| 対象 | 行 | 分岐 |
|---|---|---|
| バックエンドの全体 | 98.9% | 94.8% |
| `common.error.web`（変えたパッケージ） | 97.2% | 89.4% |
| フロントエンドの全体 | 97.44% | 92.77% |

詳しくは `test-results.md` の 4節。

## Target Verification Matrix

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| NFR1 | requirements.md NFR1・FR1.2・FR1.3 | 利用者の管理の確かめの表示を閉じた後、フォーカスが行の「操作」に戻る（WCAG 2.1 AA 2.4.3） | E2E 110・120 のフォーカスの確かめが通過（153 件中 153 件） | code-summary.md 3.5節 | build-and-test | Met |
| PREV-U5-NFR7.1 | code-generation-plan.md 5節 R-05・8節 | 前の Intent の U5-NFR7.1（Not Met）を Met にする | E2E 120 の全体が通過 | code-summary.md 3.5節 | build-and-test | Met |
| PREV-AC-R05 | code-generation-plan.md 5節 R-05・8節 | AC2.1.8・AC3.1.7・AC4.1.9・AC5.1.7 の閉じた後のフォーカス | E2E 110・120 の該当の確かめが通過 | code-summary.md 3.5節 | build-and-test | Met |
| FR1.4 | requirements.md FR1.4 | 招待・DSL の管理の画面の Modal を閉じた後に開いた元へ戻る | 3つの表示とも戻る（E2E 060 の 20 組・040） | code-summary.md 3.6節 | build-and-test | Met |
| FR2.2 | requirements.md FR2.2 | 開いたメニューの矩形が viewport の中（狭い幅と広い幅） | E2E 120 の 20 組が通過。右端 1251/1280px・346/375px | code-summary.md 3.3節・3.5節 | build-and-test | Met |
| FR3.2 | requirements.md FR3.2 | 送信中は言語の選択が押せず、終わると押せる | `EditProfileDialog.test.tsx` が通過（直しを外すと1件落ちる） | test-results.md 2節・code-summary.md 3.3節 | build-and-test | Met |
| FR4.2-a | requirements.md FR4.2(a)・計画 7.1節 | 時間切れの累計 1 以上、または待ちの最大 1,000 ms 以上 | 累計 662、MAX 5,049.8 ms | t1-load-test-results.md 1節・5節 | build-and-test | Met |
| FR4.2-b | requirements.md FR4.2(b)・計画 7.1節 | 警報3件が決まりを変えずに `Alerting` | 3件とも `firing`（実体 `Alerting`） | t1-load-test-results.md 1節・3節 | build-and-test | Met |
| FR4.2-c | requirements.md FR4.2(c)・計画 7.1節 | BUSY の L3 の各行に同じ traceId の L4 がちょうど1件 | BUSY 0 件で判定できない | t1-load-test-results.md 1節・9節 | build-and-test（依頼者の決定で次の Intent へ持ち越し） | Unverified |
| FR5.1 | requirements.md FR5.1 | 印を外した直後の 403 と監査の行を1つのテストで確かめる | `UserAdminOperationsApiIT`（7 件）が通過 | test-results.md 2節 | build-and-test | Met |
| FR6.2 | requirements.md FR6.2・計画 5節 R-06 | `MailConfigurationIT` の単独 5 回と `verify` で通る | 5 回とも 7 件通過、`verify` でも通過 | code-summary.md 3.2節・test-results.md 2節 | build-and-test | Met |
| NFR2 | requirements.md NFR2・FR8.1 | 一意の違反の経路で個人の値をログ（INFO・TRACE）・監査・応答・トレースの属性に出さない | 漏えいの結合テスト4件と `GlobalExceptionHandlerTest` 13 件が通過 | test-results.md 5節・security-test-instructions.md | build-and-test | Met |
| NFR3-VERIFY | requirements.md NFR3 | `develop` で `verify` が通り、対象DB のテストも流れる | BUILD SUCCESSFUL 9m 6s、飛ばし 0 | test-results.md 1節・2節 | build-and-test | Met |
| NFR3-E2E | requirements.md NFR3 | 統合の前に E2E の全体が通る | 153 件通過 | code-summary.md 3.5節 | build-and-test | Met |
| TC-COV-BE-LINE | Testing Contract（team.md） | バックエンドの行 80% 以上 | 98.9%（6287/6357） | test-results.md 4.1節 | build-and-test | Met |
| TC-COV-BE-BRANCH | Testing Contract（team.md） | バックエンドの分岐 70% 以上 | 94.8%（2348/2476） | test-results.md 4.1節 | build-and-test | Met |
| TC-COV-BE-PKG | Testing Contract（team.md）・計画 1.1節 | 変えたパッケージ `common.error.web` の行 80%・分岐 70% 以上 | 行 97.2%・分岐 89.4%（`verifyCoverage` 通過） | code-summary.md 3.7節・test-results.md 1節 | build-and-test | Met |
| NFR4 | requirements.md NFR4・Testing Contract | 下限・除外・`packagesJudgedByTotal` を変えず、一覧のパッケージに手を入れない | 一覧（7 個）に当たる変更なし、設定の変更なし | code-summary.md 1節・3.7節 | build-and-test | Met |
| TC-COV-FE-LINE | Testing Contract（team.md） | フロントエンドの行 80% 以上 | 97.44% | test-results.md 4.1節 | build-and-test | Met |
| TC-COV-FE-BRANCH | Testing Contract（team.md） | フロントエンドの分岐 70% 以上 | 92.77%（1876/2022） | test-results.md 4.1節 | build-and-test | Met |
| NFR5 | requirements.md NFR5・Testing Contract の scope_floor | 不具合（FR1・FR2・FR3・FR8）の再現のテストを直しと同じコミットに含める | 再現の確かめ（直しを外すと落ちる）済み。`a7fdc2c`・`fcf5dee` に同梱。FR1 は C1 が固定先だけのため回帰のテストは C2（決定 D4: A） | code-summary.md 3.3節・gate-decisions.md 3節 | build-and-test | Met |
| TC-SUITE-GREEN | Testing Contract の scope_floor | 既存のテストが通ったまま | 失敗 0（単体・結合・画面・E2E） | test-results.md 2節 | build-and-test | Met |
| SEC-SPOTBUGS | team.md Code Style | High と `SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` の指摘 0 | `spotbugsGate` 通過 | test-results.md 5節 | build-and-test | Met |
| SEC-OSV | team.md Code Style・Deployment | 重大度 High 以上の脆弱性 0（判定の決まりどおり） | `osvScan` 通過 | test-results.md 5節 | build-and-test | Met |
| SEC-GITLEAKS | team.md Code Style | 秘密情報の検出 0 | `gitleaksScan` 通過 | test-results.md 5節 | build-and-test | Met |
| SEC-E2E-REPORT | project.md Testing Posture の学び（2026-09-28） | E2E の報告にパスワード・トークン・メールアドレスが 0 件 | 0 件 | code-summary.md 3.5節 | build-and-test | Met |
| SEC-PERF-LOG | perf/README.md 手順 4''（`c50e64c`） | `perf-ua` は `enteredEmail` の行の数と一致し、ほかは 0。`MVStoreException`・`password`・`Bearer `・`eyJ` は 0 | `perf-ua` 16 件（すべて `enteredEmail`）、ほかは 0 | t1-load-test-results.md 5節 | build-and-test | Met |
| CI-DEVELOP | team.md Way of Working・Deployment、Q2: A | 統合後の CI が通る | run `37162114053` success（15 分 5 秒） | test-results.md 6節 | build-and-test | Met |

判定の数: Met 27・Not Met 0・Unverified 1。

- 目標の出どころ: この Intent は NFR 要件・NFR 設計の段を飛ばしたため、要件定義の NFR1〜NFR5・FR の判定、計画の Testing Contract と 7.1節、team.md・project.md の関門を在庫にした。
- FR9.1（配備）は、この段の測れる目標ではないため表に入れていない。Deployment Pipeline・Deployment Execution の段が持つ（`cross-unit-traceability.md`）。

## 4. 準備の状態

| 観点 | 判定 | 理由 |
|---|---|---|
| ビルド | 準備できている | `verify` と CI が通った |
| テスト | 準備できている（条件つき） | すべてのテストが通った。FR4.2-c だけが Unverified |
| 配備 | 依頼者の判断待ち | 段の定義では Unverified が残ると段は成功にならない。依頼者は FR4.2-c の持ち越しを決めており（2026-10-04）、承認の場でそれを受け入れて Deployment Pipeline へ進むかを確かめる |

- 失敗の手順の段階: (c) の原因は生成したコードではなく、負荷の場面が行の排他の待ちを起こさないこと（計画 7.1節の見込みどおり）。この段の中の直し（段階 1）とコード生成への戻し（段階 3）の対象ではなく、場面を変えて流し直すかは依頼者が決めると計画で定めていた。依頼者は持ち越しを選んだ。ループバックは行っていない。

## 5. 既知の制約と残っていること

- FR4.2-c: Unverified、次の Intent へ持ち越し（依頼者の決定）。
- 負荷の試験の Tomcat の ERROR 239 件: 原因は確かめていない。次の Intent へ持ち越し（記録だけ）。
- R-01（G2）: 言語の選択肢で Enter を押して送信したときのフォーカス。Code Generation の承認の場で次の Intent へ持ち越し済み。
- `PasswordChangePage.test.tsx`: この段の `verify` と CI で通った。不安定と確かめられていない扱いのまま見守る。
- E2E とパッケージごとのカバレッジは、`develop` と同じソースのコード生成の段の実測を使った（Q1 で流し直したのは `verify`）。
- `README.md` の make-you-chic-ui の固定先の記述が古い（`077f5b4`、実際は `e82b651`）。この段では直していない。

## 6. 承認の場で依頼者に確かめること

1. FR4.2-c の `Unverified` を次の Intent への持ち越しとして受け入れ、この段を承認するか（段の定義では成功の結果にならない）。
2. `README.md` の make-you-chic-ui の固定先の記述を直すか（直すならどの段・どのコミットで行うか）。

## Sources

- `aidlc/spaces/default/intents/261003-user-admin-followup/inception/requirements-analysis/requirements.md`
- `aidlc/spaces/default/intents/261003-user-admin-followup/construction/code-generation/code-generation-plan.md`（Testing Contract、5節、7節、8節）
- `aidlc/spaces/default/intents/261003-user-admin-followup/construction/code-generation/code-summary.md`
- `aidlc/spaces/default/intents/261003-user-admin-followup/construction/code-generation/unit-test-instructions.md`
- `aidlc/spaces/default/intents/261003-user-admin-followup/construction/code-generation/gate-decisions.md`
- `aidlc/spaces/default/intents/261003-user-admin-followup/construction/build-and-test/build-and-test-questions.md`
- `aidlc/spaces/default/intents/261003-user-admin-followup/construction/build-and-test/t1-load-test-results.md`
- `aidlc/spaces/default/intents/261003-user-admin-followup/construction/build-and-test/test-results.md`
- `aidlc/spaces/default/memory/team.md`・`aidlc/spaces/default/memory/project.md`

## Assumptions & Open Questions

- FR4.2-c の持ち越しは依頼者がこの段で決めたが、段の承認としての受け入れは承認の場で確かめる（6節の1）。
- `README.md` の固定先の記述の扱いは未決（6節の2）。
