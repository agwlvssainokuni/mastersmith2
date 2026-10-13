# テストの結果（test-results）

Intent `261004-role-menu`（U1〜U7、Bolt B1〜B9）の Build and Test の実測です。対象は `develop` の `0415b94` に、この段の直しを作業ブランチ `fix/261004-role-menu-bt` で足した作業ツリーです（未コミット。コミットは提案して承認を得てから行う）。数字はすべて、`:backend:cleanTest :backend:cleanIntegrationTest` を付けた実行の実測です（`project.md` の Testing Posture）。詳しいコマンドと出力の置き場は `fix-notes.md` と、開発担当の作業メモ（scratchpad の `bt-q1-limits.md`・`bt-verify.log`・`bt-e2e.log`）にあります。

## 1. ビルドの状態

| 項目 | 結果 |
|---|---|
| 前の確かめ | colima は Running（CPU 4・6GiB、ホームの下を共有）。README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡した。`pmset -g assertions` で眠らない状態を確かめた |
| コマンド | `caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` |
| 1回目（2026-10-13 21:57 ごろ） | 失敗。`:frontendTest` で1件が落ちて止まった（3節） |
| 2回目（2026-10-13 22:07:54〜22:10:20） | 失敗。同じ1件で止まった |
| 3回目（2026-10-14 00:49:04〜01:03:31） | **BUILD SUCCESSFUL in 14m 26s**（原因を直した後）。対象DB のテストの飛ばしの警告なし |
| 成果物 | 実行可能 WAR（`verifyArtifact`）、WAR の中の DSL の JSON Schema は v2（`verifyDslSchemaInWar`）。初回の JavaScript は 158.4 KB（gzip。`index` 151.9 KB・`useTranslation` 6.4 KB） |
| イメージの JVM の設定（Q2: A） | `docker run --rm mastersmith:bt-q2 -XX:+PrintFlagsFinal -version` で `ExitOnOutOfMemoryError = true {command line}`・`MaxRAMPercentage = 50.0`。PID 1 のコマンド行に `-XX:+ExitOnOutOfMemoryError`。`MASTERSMITH_JAVA_OPTIONS=-Xmx16m` でヒープが尽きると1行出して終了（終了コード 3、OOMKilled false）。確かめのコンテナとイメージは消した（`fix-notes.md` 2節） |
| スリープ | `pmset -g log` で、測りと実行の窓（2026-10-14 00:44〜01:15）に Sleep・DarkWake・Wake なし |

## 2. テストの結果

| 種類 | 合計 | 通過 | 失敗 | 飛ばし | 出典 |
|---|---|---|---|---|---|
| バックエンドの単体テスト（`*Test`、240 クラス） | 2,102 | 2,102 | 0 | 0 | verify の3回目 |
| バックエンドの結合テスト（`*IT`、222 クラス。対象DB の3種類を含む） | 1,165 | 1,165 | 0 | 0 | 同上 |
| 画面のテスト（Vitest、185 ファイル） | 1,581 | 1,581 | 0 | 0 | 同上 |
| E2E（Playwright、010〜170） | 230 | 230 | 0 | 0（再実行・不安定も 0） | `caffeinate -dims ./gradlew e2eTest`（2026-10-14 01:04:18〜01:14:50、10.4 分） |

- B9 の関門（`0415b94` の前の作業ブランチ）からの増え方: 単体 2,082 → 2,102（+20。主に Q3 B の名前のテスト）、結合 1,165 → 1,165（上限のテストは値の書き換えで、件数は同じ）、画面 1,558 → 1,581（+23。Q3 A・B・C・D と上限）、E2E 230 → 230。
- 性質ベースのテスト（jqwik・fast-check）はすべて通過し、失敗が無いため記録する乱数の種は無い。
- E2E の報告の秘密の確かめ: 「E2E の報告に残してはならない値は含まれていません（値の種類 7・json の報告 1・添付 301・trace 0・失敗の画面の写し 0・そのほか 0・前の html の報告 0・見つかった件数 0）」。報告の置き場（`frontend/test-results`）は確かめた後に消した。`frontend/playwright-report` は作られていない。Mailpit のメールは消していない。
- この段では、E2E の測りのテスト（140・160）の値の添付を写す前に報告を消したため、画面の時間の値はこの段の実測として残っていない（判定は B8・B9 の関門の値。`build-and-test-summary.md` の「判定の根拠を補うもの」）。

## 3. 失敗の詳細

### 3.1 画面のテスト `ShellLayout.test.tsx`（verify の1回目・2回目、直した）

| 項目 | 内容 |
|---|---|
| テスト | `src/app/layout/ShellLayout.test.tsx` の「work role switcher placement by width (G2: A) > moves the switcher to the top of the content area below 768px and keeps its name」 |
| 失敗 | `TestingLibraryElementError: Unable to find an accessible element with the role "button" and name "作業ロール: 営業"`。失敗の時の切り替えの部品は「作業ロール: 読み込んでいます」 |
| 回数 | verify の1回目・2回目の2回（1,581 件のうち 1 failed・1,580 passed）。2回とも `:frontendTest` で止まり、後ろの段は流れなかった |
| 再現の試み | 上限を約 10 分と決め、このファイルだけを5回（37 件ずつ）、画面のテストの全体を2回（1,581 件ずつ）流し、どれも passed（verify の中だけで落ちた） |
| 原因 | 帯（`shell-work-role-bar`）が出るのを待った直後に、作業ロールの読み込みの答え（非同期）の後に出るボタンを同期の `getByRole` で探していた。verify では Gradle が `:frontendTest` を型の検査などと並べて動かし負荷が高く、答えが帯より後に届くと落ちる。2回の失敗の DOM がこの状態だった |
| 扱い | `team.md` の「同じテストが二度目に落ちたら原因を直すまで次へ進まない」に当たるため止め、依頼者の決定 1: A で直した |
| 直し | ボタンを `await within(switcher).findByRole('button', { name: '作業ロール: 営業' })` で待って押す（期待は変えない。待つ時間の上限は既定のまま延ばしていない）。同じファイルの同じ形を洗い出し、ほかに直す所は無かった |
| 確かめ | 直した後、このファイルだけを3回流し 37 件ずつ passed。verify の3回目で全体が通った |

### 3.2 権限の YAML の上限の測り（Q1）の悪い形の結果

コード生成の段（role の B6）と、この段の Q1 の前の測り（`r01-memory-trial.keep.md`）では、配備と同じ最大ヒープ 1 GiB で、10 MiB の上限の内で作れる悪い形が目標を外れていた。

| 測り | 形 | 結果 |
|---|---|---|
| 前の測り（上限 10 MiB・1,000,000 の節） | 流れの書き方で 100 万の節（7.5 MB） | 確かめ 21.9 秒（目標 15 秒を超える）、Young GC の後もヒープ 約 931 MB |
| 同上 | 50 万の節に 6.7 MB の1行のコメント | Full GC が起きた |
| この段の Q1 の1回目（正しい書き出しの形、値は変えずに測った） | 約 29 万・50 万・80 万・99 万の節（5.65〜12.16 MiB） | どれも 200、OOM・Full GC なし、確かめ 2.0〜4.7 秒・適用 3.8〜15.2 秒。ただし 10 MiB 前後と 12 MiB では適用の GC の直前のヒープが 909〜996 MB、Evacuation Failure が1〜2回 |
| 直した後（節 500,000・5 MiB） | 正しい書き出しの最大（39.9 万の節、5,155,174 バイト） | 確かめ 1.11 秒・適用 2.35 秒、GC の直前の最大 661 MB、Evacuation Failure 0、OOM・Full GC なし |
| 同上 | 悪い形（流れの書き方 49.7 万の節＋1,517,209 バイトの1行のコメント、5,242,880 バイトちょうど） | 確かめ 5.42 秒・適用 11.41 秒（目標の内）、どれも 200、OOM・OOMKilled・Full GC なし。GC の直前のヒープは 1,024 MB（上限）に届き、Young GC の後も 830 MB、Evacuation Failure 5回・11回、巨大な割り当て 11回・25回。`memory.peak` 最大 1.57 GB（上限 2 GiB） |

- 悪い形のヒープの余裕の小ささは、依頼者の決定で**受け入れた制約**とした。尽きたときは `ExitOnOutOfMemoryError` で JVM が止まる。1行の長さに上限を置く守りは、DSL と共通の部品のため後の Intent で検討する。
- 測りは配備した環境とは別の使い捨ての環境（`docker/perf/compose.yaml`、仮の署名鍵・仮の利用者）で行い、終わった後に消した。配備したアプリ・mailpit・見本の対象DB は動いたまま。

## 4. カバレッジ

| 対象 | 行 | 分岐 | 下限 | 出典 |
|---|---|---|---|---|
| バックエンドの全体（JaCoCo） | 97.9%（10,525/10,748） | 92.5%（4,013/4,340） | 行 80%・分岐 70% | `backend/build/reports/jacoco/test/jacocoTestReport.xml` |
| 画面の全体（`@vitest/coverage-v8`） | 95.42%（6,044/6,334） | 90.31%（4,064/4,500） | 同上 | `frontend/coverage/coverage-summary.json`（文 95.25%・関数 96.76%） |

- パッケージは 67。`packagesJudgedByTotal`（7 パッケージ）に無い 60 パッケージは、すべて行 80%・分岐 70% 以上。余裕が最も小さいのは `config` 分岐 76.7%、`group.service` 分岐 80.4%、`navigation.web` 行 92.1%、`role.domain` 分岐 85.7%。
- 単独で下限を下回るのは `common.health`（行 79.2%）だけで、一覧の既存のパッケージ（全体の合計で判定）。7 パッケージの `src/main` に Intent の間の差は 0 件で、一覧と計測の除外は変えていない。
- この段で手を入れたパッケージ: `group.domain` 97.0%・90.3%、`role.domain` 94.2%・85.7%、`role.service` 96.6%・90.3%、`role.web` 96.0%・86.0%、`role.transfer` 97.8%・91.6%。画面の複製の `useSlowNotice.ts` は3つとも行 100%・分岐 75%。

## 5. 静的な検査

| 検査 | 結果 |
|---|---|
| フォーマット・リンタ・ライセンスヘッダー・型の検査 | verify の3回目で通過（画面は直しの時にも `npm run typecheck`・`lint`・`format:check`・`license:check` で誤りなし） |
| Gitleaks | `no leaks found` |
| SpotBugs＋FindSecBugs | 関門 `spotbugsGate` を通過。priority 1 の指摘 0 件、`SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` の指摘 0 件、警告（priority 2・3）278 件 |
| OSV-Scanner | verify の中では UP-TO-DATE のため `./gradlew osvScan --rerun` で流し直した。失敗の条件 0 件、警告 19 件（`vendor/make-you-chic-ui/package-lock.json` の開発用の npm `undici` など） |
| 除外の設定 | `backend/config/spotbugs-exclude.xml`・`.gitleaks.toml`・`.github/dependabot.yml`・lockfile に Intent の間の差なし（`git diff 82c36b6`、読み取り） |

## 6. CI

| run | 対象 | 結果 |
|---|---|---|
| 38398376712（CI） | `develop` の `0415b94`（B9 の統合の後の push） | **success**（2026-10-13T12:35:11Z〜13:11:59Z）。`gh run view 38398376712 --json conclusion` で読み取りで確かめた |
| （未） | この段の直しを `develop` に統合した後の push | まだ統合していない。コミットの承認・squash の統合・依頼者の push の後に確かめる（`TC-7b`、持ち主 ci-pipeline） |

## 7. Target Verification Matrix（最終）

表の全体（242 行）は `build-and-test-summary.md` の Target Verification Matrix にあります。ここには集計と、Met でないものを並べます。

| Verdict | 数 |
|---|---|
| Met | 217 |
| Not Met | 0 |
| Unverified | 25（すべて後の段が持ち主） |

| Target ID | Owning Stage | 理由 |
|---|---|---|
| U2-NFR2.5・U2-NFR2.7・U2-NFR2.8 | performance-validation | 依頼者の追加の決定で `perf/dsl-timing.sh` の DSL の時間を回した（`--lang` の台本はその段で直す） |
| U2-NFR2.6、U3-NFR2.1・NFR2.5・NFR2.7b・NFR2.8b、U4-NFR2.1・NFR2.2・NFR2.3b・NFR2.4・NFR2.5・NFR2.7b・NFR2.8・NFR2.10、U5-NFR2.1・NFR2.2・NFR2.3b・NFR2.5 | performance-validation | Q4: A。k6 の場面はその段で流す（U4-NFR2.4 はこの段の Q1 の先の測りを Actual に並べた） |
| U3-NFR5.4・U4-NFR5.4 | observability-setup | 警報と SLO（既存の p95 の警報はこの Intent の API を拾わない） |
| U3-NFR3.6b・U4-NFR3.6b | deployment-execution | 前の版への戻しの練習 |
| TC-7b | ci-pipeline | この段の直しの統合の後の CI |

- 持ち主の無い Unverified は無い。ただし段の定義の失敗の述語（Unverified があれば失敗）に当たるため、承認の場で後の段への持ち越しとして受け入れるかを確かめる。
- 失敗の段の手当て（ループ・バック）は行っていない（この段の中の直しで足りた）。そのため `## Loop-Back Log` は無い。

## Sources

- `aidlc/spaces/default/intents/261004-role-menu/construction/build-and-test/fix-notes.md`（1〜11節）・`build-and-test-questions.md`（Q1〜Q4）と依頼者の追加の決定（指揮役から伝達）
- 開発担当の作業メモ `bt-q1-limits.md`（1〜5節）・`bt-verify.log`・`bt-e2e.log`（scratchpad、読み取り）と、前の測り `r01-memory-trial.keep.md`
- `backend/build/reports/jacoco/test/jacocoTestReport.xml`・`frontend/coverage/coverage-summary.json`（読み取り）、`gh run view 38398376712 --json conclusion`（読み取り）、`git diff 82c36b6`（読み取り）
- `aidlc/spaces/default/memory/team.md`（Testing Posture）・`aidlc/spaces/default/memory/project.md`（Testing Posture）

## Assumptions & Open Questions

- 後の段が持ち主の Unverified 25 件の持ち越しを、承認の場で受け入れるかを確かめる。
