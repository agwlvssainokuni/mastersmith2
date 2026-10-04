# テストの結果（261003-user-admin-followup）

- 対象: `develop` の `a1b41e7`（コード生成の統合の版）。この段の後に `perf/README.md` だけを直した `c50e64c` を統合した。
- 段の質問の答え: Q1: A（`develop` で clean 付きの `verify` を流し直す）、Q2: A（依頼者が push し、この段で CI を確かめる）、Q3: A（k6 のあいだ配備したアプリを止める）。
- 数字はすべて実測（clean 付きの `verify`、`project.md` の学び）。この段で流し直していないもの（E2E・パッケージごとのカバレッジ）は、出どころをそのつど書く。

## 1. ビルド

| 項目 | 結果 |
|---|---|
| コマンド | `DOCKER_HOST=unix://$HOME/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` |
| 版 | `develop` の `a1b41e7` |
| 結果 | **BUILD SUCCESSFUL in 9m 6s** |
| 段 | `verifyPrepare` 〜 `verifyArtifact` の 0〜9 の段がすべて通った（フォーマット・リンタ・ライセンスヘッダー・ビルド・単体・結合・カバレッジ・安全の検査・成果物） |

`perf/README.md` の直し（`c50e64c`）は、短命のブランチ `fix/261003-perf-readme` の上で `./gradlew verify` が BUILD SUCCESSFUL（1m 23s。文書だけの変更）になった後に `develop` へ squash で統合した。

## 2. テストの件数

| 対象 | 件数 | 通過 | 失敗 | 誤り | 飛ばし | 出どころ |
|---|---|---|---|---|---|---|
| バックエンドの単体（`*Test`） | 1,512 | 1,512 | 0 | 0 | 0 | この段の `verify` |
| バックエンドの結合（`*IT`。対象DB の3種類を含む） | 694 | 694 | 0 | 0 | 0 | この段の `verify` |
| フロントエンド（Vitest） | 110 ファイル | 110 ファイル | 0 | — | — | この段の `verify` |
| フロントエンドのテストの数 | 970 | 970 | 0 | — | — | コード生成の段の `verify` の記録（画面のソースは同じ） |
| E2E（Playwright） | 153 | 153 | 0 | — | — | コード生成の段の Step 20（6 分 34 秒。アプリのソースは同じ） |
| `MailConfigurationIT` の単独の繰り返し（FR6.2） | 5 回 × 7 件 | 5 回とも通過 | 0 | — | — | コード生成の段の Step 7 |

- 対象DB のテストは飛ばされていない（飛ばし 0）。
- `frontend/src/features/preferences/PasswordChangePage.test.tsx` の「drops the answer when the screen is left while sending」（コード生成の段で1回落ち、再現しなかったもの）は、この段の `verify` と CI の両方で通った。

## 3. 失敗の詳細

- 失敗したコマンド・テストは無い。
- 段の中の直し・ループバックは行っていない（`## Loop-Back Log` は無い）。

## 4. カバレッジ

### 4.1 全体（この段の `verify` の実測）

| 対象 | 行 | 分岐 | そのほか | 下限（行・分岐） |
|---|---|---|---|---|
| バックエンド（JaCoCo） | **98.9%**（6287/6357） | **94.8%**（2348/2476） | — | 80%・70% |
| フロントエンド（`@vitest/coverage-v8`） | **97.44%** | **92.77%**（1876/2022） | 文 97.27%（3003/3087）・関数 97.92%（850/868） | 80%・70% |

### 4.2 パッケージ・ディレクトリごと（`code-summary.md` 3.7節の値。コード生成の段の `verify` の実測）

| 対象 | 行 | 分岐 | 扱い |
|---|---|---|---|
| `common.error.web`（`src/main` を変えたパッケージ） | 97.2% | 89.4% | パッケージごとの下限の対象 |
| `user.service` | 99.7% | 95.1% | テストを足した |
| `invitation.service` | 100% | 93.5% | テストを足した |
| `mail.config` | 98.0% | 96.2% | テストを変えた |
| `useradmin.web` | 98.8% | 93.8% | テストを足した |
| `src/features/useradmin` | 96.22% | 90.93% | — |
| `src/features/invitation` | 95.74% | 90.58% | — |
| `src/shared/modal` | 100% | 100% | 新規 |

- 変えた Java のパッケージは `common.error.web` だけで、`packagesJudgedByTotal` の一覧（7 個）に当たらない。下限・除外・一覧は変えていない（NFR4）。
- この段の `verify` の 7 の段（パッケージごとの下限の検証を含む）は通った。

## 5. 安全の検査

| 検査 | 結果 | 出どころ |
|---|---|---|
| SpotBugs＋FindSecBugs（`spotbugsGate`） | 通過 | この段の `verify` |
| OSV-Scanner（`osvScan`） | 通過 | この段の `verify` |
| Gitleaks（`gitleaksScan`） | 通過 | この段の `verify` |
| 漏えいの結合テスト（`UserUniqueViolationSecretLeakIT`・`InvitationUniqueViolationSecretLeakIT`、INFO・TRACE） | 通過 | この段の `verify` |
| E2E の報告の秘密の値 | 0 件 | コード生成の段の Step 20 |
| 負荷の試験のログ（手順 4''） | `perf-ua` 16 件（すべて既知の例外 `enteredEmail`）、ほかは 0 件 | `t1-load-test-results.md` 5節 |

## 6. CI

| 項目 | 結果 |
|---|---|
| きっかけ | 依頼者が `develop`（`a1b41e7`）を `origin` へ push した |
| 実行 | GitHub Actions の run `37162114053` |
| 結果 | **success**（15 分 5 秒） |

- `c50e64c`（文書だけ）の CI は、この段の記録の対象にしていない。

## 7. 負荷の試験（T1・FR4.2）

詳細は `t1-load-test-results.md`。手順は `performance-test-instructions.md`。

| 基準 | 実測 | 判定 |
|---|---|---|
| (a) 時間切れの累計 1 以上、または待ちの最大 1,000 ms 以上 | `hikaricp.connections.timeout` 0 → **662**、`acquire` の MAX 最大 **5,049.8 ms** | Met |
| (b) 警報3件が `Alerting` | `ms-audit-fail`・`ms-error-logs`・`ms-pool-pending` の3件とも `firing`（実体は `Alerting`）。警報の決まりは変えていない | Met |
| (c) BUSY の L3・L4 が traceId で 1 対 1 | 409 `USER_ADMIN_BUSY` **0 件** | **Unverified** |

- 配備したアプリは 2026-10-03T23:32:27Z に止め、23:53:57Z に同じコンテナで起動し直した（healthy、`/actuator/health` 200）。
- 依頼者の決定（2026-10-04）: (c) は `Unverified` のまま次の Intent へ持ち越す。Tomcat の ERROR 239 件の原因の確かめも次の Intent へ持ち越す（記録だけ）。`perf/README.md` はこの Intent で直す（`c50e64c`）。

## 8. Target Verification Matrix（最終の判定）

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
| NFR5 | requirements.md NFR5・Testing Contract の scope_floor | 不具合（FR1・FR2・FR3・FR8）の再現のテストを直しと同じコミットに含める | 再現の確かめ（直しを外すと落ちる）済み。C2 `a7fdc2c`・`fcf5dee` に同梱。FR1 は C1 が固定先だけのため回帰のテストは C2（決定 D4: A） | code-summary.md 3.3節・gate-decisions.md 3節 | build-and-test | Met |
| TC-SUITE-GREEN | Testing Contract の scope_floor | 既存のテストが通ったまま | 失敗 0（単体・結合・画面・E2E） | test-results.md 2節 | build-and-test | Met |
| SEC-SPOTBUGS | team.md Code Style | High と `SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` の指摘 0 | `spotbugsGate` 通過 | test-results.md 5節 | build-and-test | Met |
| SEC-OSV | team.md Code Style・Deployment | 重大度 High 以上の脆弱性 0（判定の決まりどおり） | `osvScan` 通過 | test-results.md 5節 | build-and-test | Met |
| SEC-GITLEAKS | team.md Code Style | 秘密情報の検出 0 | `gitleaksScan` 通過 | test-results.md 5節 | build-and-test | Met |
| SEC-E2E-REPORT | project.md Testing Posture の学び（2026-09-28） | E2E の報告にパスワード・トークン・メールアドレスが 0 件 | 0 件 | code-summary.md 3.5節 | build-and-test | Met |
| SEC-PERF-LOG | perf/README.md 手順 4''（`c50e64c`） | `perf-ua` は `enteredEmail` の行の数と一致し、ほかは 0。`MVStoreException`・`password`・`Bearer `・`eyJ` は 0 | `perf-ua` 16 件（すべて `enteredEmail`）、ほかは 0 | t1-load-test-results.md 5節 | build-and-test | Met |
| CI-DEVELOP | team.md Way of Working・Deployment、Q2: A | 統合後の CI が通る | run `37162114053` success（15 分 5 秒） | test-results.md 6節 | build-and-test | Met |

判定の数: Met 27・Not Met 0・Unverified 1（FR4.2-c）。

## 9. 持ち越し

| 項目 | 中身 | 決めた人・場 | 行き先 |
|---|---|---|---|
| FR4.2-c | 409 `USER_ADMIN_BUSY` が 0 件で、L3・L4 の結び付きを確かめられなかった。確かめるには行の排他の待ちの上限切れを起こす別の場面が要る | 依頼者（2026-10-04、この段） | 次の Intent |
| Tomcat の ERROR 239 件 | 負荷の試験で `dispatcherServlet` のロガーが `Servlet.service() … threw exception` を出した。例外のログを境界で1回だけ出す決まりと重なる出力の可能性。原因は確かめていない | 依頼者（2026-10-04、この段） | 次の Intent（記録だけ） |
| R-01（G2） | 言語の選択肢で Enter を押して送信すると、送信中はフォーカスが body に落ちる（10 回中 10 回）。送信の後は行の「操作」へ戻る | 依頼者（Code Generation の承認の場） | 次の Intent |
| 不安定かもしれないテスト | `PasswordChangePage.test.tsx` の「drops the answer when the screen is left while sending」。この段の `verify` と CI では通った。不安定と確かめられていない扱いのまま、次に落ちたときは team.md の決まり（2回目なら原因を直すまで進まない）で扱う | Code Generation の承認の場 | 見守り |
| FR9.1 | 配備 | 計画どおり | Deployment Pipeline・Deployment Execution |

## Sources

- `aidlc/spaces/default/intents/261003-user-admin-followup/construction/build-and-test/build-and-test-questions.md`
- `aidlc/spaces/default/intents/261003-user-admin-followup/construction/build-and-test/t1-load-test-results.md`
- `aidlc/spaces/default/intents/261003-user-admin-followup/construction/code-generation/code-summary.md`（3節）
- `aidlc/spaces/default/intents/261003-user-admin-followup/construction/code-generation/code-generation-plan.md`（Testing Contract、5節、7節、8節）
- `aidlc/spaces/default/intents/261003-user-admin-followup/construction/code-generation/gate-decisions.md`
- `aidlc/spaces/default/intents/261003-user-admin-followup/inception/requirements-analysis/requirements.md`
- この段の `verify` の出力（`backend/build/test-results/`・`backend/build/reports/jacoco/`・`frontend/coverage/`）、GitHub Actions の run `37162114053`

## Assumptions & Open Questions

- FR4.2-c が `Unverified` のため、段の定義の失敗の条件（applicable な目標に `Unverified` がある）に当たる。依頼者は (c) の持ち越しをこの段で決めた。承認の場で、この持ち越しを受け入れて段を承認するかを確かめる。
- E2E とパッケージごとのカバレッジは、`develop` と同じソースのコード生成の段の実測を使った（この段では流し直していない）。
- `README.md` の make-you-chic-ui の固定先の記述（`077f5b4`）が実際（`e82b651`）と食い違っている。直すかを承認の場で確かめる（`build-instructions.md` の Assumptions & Open Questions）。
