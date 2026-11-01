# テストの結果 — Build and Test（Intent 261029-reference-lookup-cleanup）

時刻は UTC。件数とカバレッジは `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `verify` の実測だけを書く（`project.md`）。秘密の値・行の値は書かない。目標ごとの最終の判定は `build-and-test-summary.md` の `## Target Verification Matrix`（195 行: Met 174・Not Met 0・Unverified 20・N/A 1）。

## 1. ビルドの状態

| 項目 | 結果 |
|---|---|
| 確かめたソース | `develop` の `df79634c`（Q2 の直しの短命のブランチ `fe2abede..99f109c0` の squash）。アプリのソースはブランチの先頭と同じ |
| ビルド（`verify` の中の型の検査・WAR を含む） | **成功**（`BUILD SUCCESSFUL`） |
| WAR | `backend/build/libs/mastersmith.war`（SHA-256 の先頭 `36711d7a84770551`、`:backend:bootWar` は UP-TO-DATE）。消した文言の鍵 `tables.reference.loading` は WAR の中に 0 件 |
| Q1 の決定 | B: `develop` の先頭では verify と E2E を流し直さず、fix ブランチの上の実測と B7 の作業ブランチの実測、CI の結果で代える |

## 2. 1コマンドの検査（verify）

### 2.1 この段の実測（fix ブランチ）

| 項目 | 値 |
|---|---|
| コマンド | `caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（colima の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` 付き） |
| 終了コード | 0（`BUILD SUCCESSFUL`、26分4秒） |
| 単体テスト（backend） | 2,807 件、失敗 0 |
| 結合テスト（backend） | 1,654 件、失敗 0、SKIPPED 10（DB の種類ごとの前からの `assumeTrue`。コンテナが無いための飛ばしは 0） |
| 画面のテスト（Vitest） | 238 ファイル・2,312 件、失敗 0 |
| 安全の検査 | `spotbugsGate`・`gitleaksScan`・`perfEvidenceScan`・`osvScan` が通過（verify の成功に含まれる） |

結合テストの件数が B7 より 2 件多いのは、`ReferenceWriteConnectionUsageIT` に MariaDB を足したため（U2 R-02、Q2: A）。

### 2.2 B7 の作業ブランチの実測（Code Generation の承認の場の直し）

`verify` 26分25秒、単体 2,807・結合 1,652、失敗 0。E2E 277 件 passed（13.8 分）。

## 3. 画面からの一連の操作（E2E）

| 回 | 時刻 | 結果 | 原因・扱い |
|---|---|---|---|
| 試み | — | テストが始まる前に止まった | 見本の PG が 22:26Z に E2E の口の上乗せ（`docker/e2e/compose.targetdb-port.yaml`）無しで作り直されていたため、前提の確かめで止まった。依頼者の決定で上乗せ付きで作り直した |
| 1回目 | 〜01:07Z ごろに途中で崩れた | 1 failed（`090-invitation-registration-flow`: `invitation-failure-alert` が出た）・26 件未実行・250 passed | **コードの失敗ではない環境の止まり**。依頼者が colima の VM の中で `apt upgrade` を流し、Docker のデーモンが起動し直して、配備したアプリ（終了コード 143）・Mailpit・見本の PG が 01:07Z ごろに止まった。090 の失敗は Mailpit が止まったことによる。不安定なテストとしては扱わない。依頼者の決定でコンテナを起動し直した |
| 2回目 | 01:19:56Z〜01:34:00Z | **277 passed・unexpected 0・flaky 0・skipped 0**（840.6 秒 = 14.0 分） | `pmset -g log` に Sleep・DarkWake 無し。実際の Chromium（Playwright） |

E2E の時間 14.0 分は、変更の前（13.1 分）の 1.5 倍の線 19.7 分の内。

### 3.1 この Intent の E2E

| ファイル | 件数 | 結果 |
|---|---|---|
| `180-business-data-flow.e2e.ts` | 1 | passed（前の main からの差は共有の DSL を `support/` へ移しただけで、`expect` の行の変更 0） |
| `190-reference-picker-flow.e2e.ts`（この Intent の代表の流れ） | 1 | passed |
| `195-reference-picker-accessibility.e2e.ts` | 20 | すべて passed（各件で 12 の状態の axe の違反 0、計 240 回） |

axe-core は `frontend/package-lock.json` で `4.14.0` の1つ。

### 3.2 報告の json の検索（`frontend/test-results/e2e-results.json`）

`eyJ` 0 件・`Authorization` 0・`refreshToken` 0・`postgresql://` 0。資格情報の環境変数（`MASTERSMITH_AUTH_INITIAL_ADMIN_PASSWORD`・`MASTERSMITH_TARGET_DB_USERNAME`・`MASTERSMITH_TARGET_DB_PASSWORD`・`MASTERSMITH_TARGET_DB_WRITE_USERNAME`・`MASTERSMITH_TARGET_DB_WRITE_PASSWORD`）は名前と種類の項目だけで、値の項目が無い。`jdbc:` の 1 件は E2E の一時の内部DB（H2 のファイル）の起動の引数で、資格情報・対象DB の接続先ではない。

## 4. カバレッジ

### 4.1 バックエンド（`backend/build/reports/jacoco/test/jacocoTestReport.xml`、fix ブランチの verify の出力）

| 範囲 | 行 | 分岐 |
|---|---|---|
| 全体 | 97.6% | 91.3% |
| `businessdata.domain` | 98.2% | 91.5% |
| `businessdata.service` | 95.6% | 88.9% |
| `businessdata.repository` | 96.2% | 88.4% |
| `businessdata.web` | 97.3% | 79.9% |
| `targetdb.domain` | 100.0% | 97.2% |
| `targetdb.service` | 89.3% | 77.1% |
| `targetdb.repository` | 96.0% | 87.5% |
| `targetdb.config` | 100.0% | 99.1% |

下限（行 80%・分岐 70%）を下回るパッケージは `common.health`（行 79.2%）だけで、`packagesJudgedByTotal` の一覧にあり全体の合計で判定する（前からの扱い）。U2 の B7 の実測（`businessdata.domain` 98.2/91.5・`businessdata.service` 95.6/88.9）と同じ値。

### 4.2 画面（`frontend/coverage/coverage-summary.json`）

| 範囲 | 行 | 分岐 |
|---|---|---|
| 全体 | 95.21% | 90.37%（文 95.07%・関数 96.28%） |
| `src/features/tables/` | 94.9%（1,857/1,956） | 90.4%（1,690/1,869） |
| うち `src/features/tables/references/` | 97.2%（247/254） | 94.5%（208/220） |

U4 の道具 `perf/redact-k6-evidence.mjs` は画面のカバレッジの対象の外（11節）。

## 5. 前の main（d797ef74）からの差の確かめ（読み取り）

| 対象 | 差 |
|---|---|
| `packagesJudgedByTotal` の7パッケージの `src/main` | 0 |
| `backend/build.gradle.kts`（一覧と下限） | 0 |
| `backend/config/spotbugs-exclude.xml` | 0 |
| 19 個の `*ArchitectureTest.java` | 0 |
| 移行ファイル（`backend/src/main/resources/db`）・`dsl-schema-v2.json` | 0 |
| `backend/gradle.lockfile` | 0 |
| `frontend/package.json`・`package-lock.json` | `41b8bf5`（Dependabot #81・#82 を含む依存の入れ直し）だけ。`hasInstallScript` の付いた項目は fsevents の1つのまま |
| `vendor/make-you-chic-ui` の固定先 | `476785a4` で ecb07e9 → 4c63d11（専用のコミット、前後のハッシュと lockfile の差を記録済み） |

## 6. 時間

| 回 | verify | E2E |
|---|---|---|
| U1 B1 | 23分28秒 | — |
| U1 B2 | 23分34秒 | — |
| U2 B3 | 26分48秒 | — |
| U3 B4 | 27分48秒 | 14.4 分 |
| B7 | 26分25秒 | 13.8 分 |
| **この段（fix ブランチ）** | **26分4秒** | **14.0 分** |

## 7. CI（前の main の取り込みの後のすべてのコミット、試行ごと）

`gh run list` と `gh run view --attempt` で取り直した。時間は run の全体ではなく、検査の job（`./gradlew verify`）の始めから終わり。run を持たないコミットは、まとめて push された先頭のコミットの run で確かめた。

| コミット | run | 試行 | 結果 | 検査の job |
|---|---|---|---|---|
| `df79634c`（この段の直し） | 41261683558 | 1 | success | 28分11秒（01:35:09Z〜02:03:20Z） |
| `b398ed5f` | 41255614894 | 1 | success | 35分24秒 |
| `6412122c`（B7） | 41184252745 | 1 | success | 28分27秒 |
| `aa6883cd` | 41179841570 | 1 | success | 36分50秒 |
| `a3099f80`（不安定なテストの直し） | 41177060211 | 1 | success | 31分30秒（06:32:54Z〜07:04:24Z） |
| `711ddd07`（B4） | 41166457994 | 1 | success | 27分49秒 |
| `a75baec4` | 41161783903 | 1 | **failure** | 32分01秒。`ReferenceSameResponseIT`（判定の順のテストの目印 "abc" が乱数のトレースID に偶然に当たった）。8節 |
| 〃 | 〃 | 2 | success | 36分32秒 |
| `c91d831d`（B6） | 41157941909 | 1 | success | 37分45秒 |
| `f1f3c2a0` | 41157419805 | 1 | success | 36分03秒 |
| `f35b7b88`（B3） | 41146703033 | 1 | success | 35分32秒 |
| `67dc8a97` | 41129322895 | 1 | success | 32分46秒 |
| `13883f6c`（B5） | 41100718494 | 1 | success | 35分25秒 |
| `9f28abd6` | 41079812332 | 1 | success | 33分29秒 |
| `6e20da67` | 41077463100 | 1 | success | 33分33秒 |
| `b048ec15`（B2） | 41070279755 | 1 | success | 34分49秒 |
| `9d1d2dd2` | 41063163854 | 1 | success | 27分59秒 |
| `549d38b6`（B1） | 41050809643 | 1 | success | 32分25秒 |
| `dbb34304` | 41034157238 | 1 | success | 31分04秒 |
| `b0845306` | 41026503068 | 1 | success | 27分13秒 |
| `cf887ad8` | 41025421073 | 1 | cancelled（job 0 件。テストは流れていない） | — |
| 〃 | 〃 | 2 | success | 31分24秒 |
| `548f1210` | 41022564230 | 1 | success | 30分48秒 |
| `8b4ad8b8` | 41020232931 | 1 | success | 31分11秒 |
| `b9eb2277` | 40985300792 | 1 | success | 31分29秒 |
| `0e853ba8` | 40981333436 | 1 | success | 27分46秒 |
| `6a28cbb2` | 40974036223 | 1 | success | 31分27秒 |
| `1fa6715a`（`41b8bf51`・`476785a4` を含む push） | 40962975187 | 1 | success | 26分17秒 |
| `ed0c65f1` | 40960090007 | 1 | success | 27分29秒 |
| `c96b5637` | 40911555760 | 1 | success | 31分27秒 |
| `4e5923c7` | 40904303094 | 1 | success | 32分54秒 |
| `b848503c` | 40901839248 | 1 | success | 24分06秒 |
| `658bf6e4` | 40896636296 | 1 | success | 31分55秒 |
| `5b1c7a30` | 40883788606 | 1 | success | 23分17秒 |
| `15a25aaf` | 40877567175 | 1 | success | 23分48秒 |
| `2eadc0a9` | 40872281884 | 1 | success | 27分29秒 |

- run を持たないコミット（`8c1a0c63`・`ac1dda7e`・`e1f53b87`・`55985fc7`・`8546ff26`・`5be72bc6`・`601d979b`・`476785a4`・`41b8bf51`・`4b744f5f`・`dda03d6c`・`72f5832b`・`c5777ada`・`7088b6df`・`aa75ff29`・`aa023144`・`916ca127`・`1f2d614a`・`a0838b9c`・`3589a905`・`78c80506`・`25b056ae`・`8c3a3b57`・`fd3141ea`）は、後にまとめて push された先頭の run で確かめた。
- 検査の job の最大は `c91d831d` の 37分45秒で、42 分の線（上限 60 分の7割）の内。テストの分け方を諮る必要は無い。
- 同じテストの二度目の失敗は無い。`ReferenceTimeoutIT`（U1 R-02）は B7 の後のどの試行でも落ちていない。
- `cf887ad8` の1回目は job が 0 件の cancelled で、テストの失敗ではない。
- **記録の食い違い**: 指揮役の連絡では「`a3099f8` は単独の CI の run を持たない」とされたが、`gh run list` では `a3099f80` を先頭とする run 41177060211（1回目で success、31分30秒）がある。gh の結果を正としてこの表に書いた。承認の場で確かめる。

## 8. 不安定なテストの直し（`team.md` の二段の確かめ）

- テスト: `ReferenceSameResponseIT`（判定の順のテスト）。CI の run 41161783903（`a75baec4`）の1回目で落ち、2回目で通過。
- 原因: 目印 "abc" が乱数の16進のトレースID に偶然に当たった（1回の実行で約 0.73%）。本番の応答は値を返していない。
- 直し: `a3099f80`（テストだけの変更。目印を16進に無い文字を含む値に替えた）。
- 確かめ（コミットの本文と指揮役の記録）: 直す前は CI と同じ本文の確かめが落ち、直した後は通る。乱数のトレースID 1,000,000 件で、目印 "abc" の当たり 7,257 件に対し新しい目印は 0 件。直した後の繰り返し 4 回が通過。以後の CI（`a3099f80`・`aa6883cd`・`6412122c`・`b398ed5f`・`df79634c`）はすべて1回目で成功。

## 9. 記録の検索

- `evidence/ref-postgres-refCreateChecked.json`: `--scan` 0 件・`gitleaks dir` 0 件・`eyJ` 0 件（`k6-trial-record.md` 5節）。
- この段の Markdown の記録: JWT の形 0 件（`eyJ` の文字は検索の語としての言及だけ）。成果物を書いた後に、この段の記録の置き場の全体に `gitleaks dir --redact --no-banner --config .gitleaks.toml --exit-code 1` を流して終了コード 0（no leaks found、約 305 KB）。

## 10. 読み取りの確かめ（レビューの指摘と引き継ぎ）

| 項目 | 結果 |
|---|---|
| U1 R-01 | `ReferenceWriteTimeoutIT` と U2 の `code-summary.md` 4節が、応答しない相手を 503 `UNAVAILABLE` に読み替えている。設計の文書は書き換えていない |
| U3 R-04 | E2E 180 の差は DSL の置き場の移動だけ（`expect` の変更 0）。`messages.ts` の日英の鍵は 174 と 174 で過不足 0。`ColumnDefinitionResponse` の参照の項目は `JsonInclude.ALWAYS` で null を落とさない。画面との受け渡しは E2E 190・195 が本物のサーバーで確かめる。B4・B7 の CI は1回目で成功 |
| U4 H2・H3 | U4 の 3 テストのファイルは `13883f6c` の後に変更 0（`.skip`・`.todo`・`.only` 0）。ファイルごとの passed は B5 の実測（計 115、skipped・todo 0。U4 の `code-summary.md`）。この段は fix ブランチの verify の frontendTest が失敗 0、`13883f6c` の後のすべての CI の run が success（`perfEvidenceScan` を含む） |
| U4 H6 | `develop` の `frontend/src`・`perf` に完成した JWT の形 0 件。backend の既存のテスト `AuditEventListenerTest` に 1 件（2026-09-23 から、"payload.signature" の形の仮の値）あるが U4 の範囲の外 |
| U4 の B7 の差 | `git show --stat 6412122` に道具と U4 のテストは無い |
| U5 の README | 本文に 2304m を今の値とする文が無い・5,504 MiB の計算がある・本文に Intent の名前が無い・JWT の形が無い（指揮役が確かめた）。df79634c の README の変更の後も verify（`gitleaksScan` を含む）が通過 |
| Dependabot #81・#82 | どちらも CLOSED（#81 2026-10-29T22:12:29Z、#82 2026-10-29T22:06:48Z） |

## 11. 既知の制約（直さずに記録）

- U4 R-02: 道具のカバレッジ（行 94.2%・分岐 90.61%）は `verify` の下限の外で、下がっても気づく手段が無い。`coverage.include` に足すかは後の Intent の候補。
- U4 R-03: 道具に Prettier・oxlint は当たるが、ESLint と型の検査は当たらない。
- U2・U3 の新しい Minor: `BusinessDataWriteService` の参照の段で、手がかりの無い想定外の失敗のときに `stage` の無い WARN が出る経路が残る（値は出ない）。
- MySQL の 1038 は今回の形では再現しなかった（`k6-trial-record.md` 4.4節）。直すかは後の Intent の要件で決める。

## 12. 失敗と未確かめ

コマンドの失敗: 最終の回には無い。途中の失敗は E2E の1回目（3節、環境の止まり）と CI の `a75baec4` の1回目（8節、直した）だけ。

この段の持ち主で確かめていない目標（Unverified、持ち主 build-and-test）:

| 目標 | 内容 | 必要な手当て |
|---|---|---|
| U4-NFR8.64（REQ-NFR8 も同じ理由で Unverified） | H1: 架空の JWT の形を `evidence/` に一時に置いた 1 回の `perfEvidenceScan` が終了コード 4 で失敗し、出力に値と3区間のどれも出ないことの確かめ。この段で後から実施して Met（13節） | `security-test-instructions.md` 3節の形で1回流す（数分。コード・設定の変更は要らない） |
| BT-K6-INSPECT | 目録の期待の 78 場面の `k6 inspect` のうち、参照の6場面だけを流した。残りは `thresholds-test.sh` の期待の表との照合だけ | 78 場面の inspect を流す（使い捨ての環境は要らない）か、承認の場で「6場面＋閾値の台本の照合」で足りると決める |

後の段が持ち主の目標（Unverified）: Performance Validation 11 行（U1-NFR2.1・2.3・2.5、U2-NFR2.21・2.23・2.24・2.25、AC3.5.4、BT-K6-JUDGE、BT-MEMPEAK-JUDGE、REQ-NFR2 の性能の分）、Observability Setup 6 行（U1-NFR2.10・3.8、U2-NFR2.29・3.28、OBS-PANELS、REQ-NFR3）。どちらもこの計画の後の段に EXECUTE で載っている。

ステージの失敗の判定（段の定義の Failure predicate）: 適用される目標に `Unverified` が残るため、この段の結果は「成功」ではない。`Not Met` は 0 件。

## 13. Target Verification Matrix

最終の行ごとの判定は `build-and-test-summary.md` の `## Target Verification Matrix` を見る（`Pending` は残っていない）。

## 13. H1（わざと残存を作った 1 回の perfEvidenceScan、U4-NFR8.64）

H1 を 2026-11-01T02:12:07Z に実施: 架空の JWT の形（実行の時に組み立てた値）を evidence/h1-planted.json に一時に置き `./gradlew perfEvidenceScan` を流すと、道具が終了コード 4（検出 1 件・1 ファイル、調べたファイル 99 件）で失敗し Gradle も失敗した。出力の中の値の全体 0 件・3区間それぞれ 0 件・eyJ 0 件。ファイルは消し、git status に残っていない。`security-test-instructions.md` 3節の形。値は記録に書いていない。
