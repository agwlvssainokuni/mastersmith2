# テストの結果 — Intent 261016-business-data-views

依頼者の決定（`build-and-test-questions.md` の Q1: B）により、この段では `develop` の今の形での verify と E2E の全件を流さない。各 Bolt の作業ブランチの上の最後の結果と CI の結果を正とする。この文書の 1〜5節は、Code Generation の段の記録（各単位の `code-generation/code-summary.md`・`generation-notes.md`、段の `construction/code-generation/generation-notes.md`）から写した実測の値である。件数とカバレッジは、どれも `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `verify` の実測（`project.md`）。長いテストはどれも `caffeinate -dims` で包み、流した後の `pmset -g log` に Sleep・DarkWake は無かった。

## 1. 統合の順

| 順 | Bolt（単位） | 作業ブランチ | `develop` のコミット | 統合の形 |
|---|---|---|---|---|
| 1 | B2（U1 読み取り） | `feature/261016-business-data-views-b2` | `ea23bf3` | squash |
| 2 | B3（U4 axe の持ち越しの前半） | `feature/261016-business-data-views-b3` | `e694d8f`〜`1e78b51` | fast-forward（固定先の更新 `a9309c4` → `b0a1e79` を含む） |
| 3 | B1（U5 持ち越しのテスト） | `feature/261016-business-data-views-b1` | `2360768` | squash |
| 4 | B4（U2 書き込み） | `feature/261016-business-data-views-b4` | `04b0589` | squash |
| 5 | B5（U3 一覧・詳細） | `feature/261016-business-data-views-b5` | `1152202` | squash |
| 6 | CI の不安定なテスト2件の直し | `fix/261016-business-data-views-ci-flaky` | `b7291d3` | squash |
| 7 | B6（U3 作成・更新・削除） | `feature/261016-business-data-views-b6` | `a031ba1` | squash |
| 8 | B7（U3 の計画の最後、U4 の後半: axe-core 4.14.0 とレビューの直し） | `feature/261016-business-data-views-b7` | `d220447` | squash |

## 2. Bolt ごとの build と test の結果（作業ブランチの上）

### 2.1 `./gradlew verify`

| Bolt | 結果 | 時間 | バックエンドの単体 | バックエンドの結合（SKIPPED） | 画面のテスト | 失敗 |
|---|---|---|---|---|---|---|
| B2 | 通過 | 18分32秒 | 2,358 | 1,253（4） | 184 ファイル・1,589 | 0 |
| B3 | 通過 | 17分37秒 | 記録なし（バックエンドは変えていない） | 記録なし | 188 ファイル・1,633 | 0 |
| B1 | 通過 | 17分38秒（レビューの直しの後 17分44秒） | 2,370 | 1,254（4） | 188 ファイル・1,633 | 0 |
| B4 | 通過 | 20分6秒（レビューの直しの後 21分4秒） | 2,583（直しの後 2,584） | 1,383（9）（直しの後 1,386（9）） | 188 ファイル・1,633 | 0 |
| B5 | 通過 | 20分27秒 | 計 3,970（SKIPPED 9） | ← | 217 ファイル・1,821 | 0 |
| CI の直し | 通過 | 21分35秒 | 2,584 | 1,386（9） | 217 ファイル・1,821 | 0 |
| B6 | 3回目で通過（N-32 の直しの後の最後の回 20分43秒） | 21分15秒（3回目） | 計 3,975（SKIPPED 9） | ← | 225 ファイル・1,963 | 0（1回目は CSS のリンタ、2回目は結合テスト1件。4節） |
| B7 | 直した後に通過 | 21分50秒 | 2,589 | 1,386（9） | 225 ファイル・1,966 | 0（直す前の2回は `frontendCoverage` で落ちた。4節） |
| B7 のレビューの直しの後（最後） | 2回目で通過 | 23分24秒 | 2,589 | 1,386（9） | 226 ファイル・1,975 | 0（1回目は `logoutIntent.test.tsx` の1件。4節） |

- SKIPPED はどれも、DB の種類に当たらない確かめの `assumeTrue`（U1 の読み取り 4・U2 の書き込み 5）で、コンテナの実行環境の不足による飛ばしではない（警告 0 件）。対象DB（MySQL 26.7・MariaDB 13.0・PostgreSQL 18）の結合テストは、どの回もコンテナで流れた。
- `verify` の時間は B2 の前の 14分1秒から最後の 23分24秒まで延びた。CI の上限 60 分の7割（約 42 分）の内で、テストの分け方を諮る線には当たらない。

### 2.2 カバレッジ

| Bolt | バックエンドの全体（行・分岐） | フロントエンドの全体（行・分岐） | 記録したパッケージ・フォルダ |
|---|---|---|---|
| B2 | 97.8%・91.8% | 95.36%・90.38% | `businessdata.domain` 97.7%・90.7%、`businessdata.repository` 94.9%・86.0%、`businessdata.service` 98.6%・93.6%、`businessdata.web` 97.0%・74.3%、`targetdb.config` 100.0%・100.0%、`targetdb.domain` 100.0%・97.1%、`targetdb.repository` 96.0%・87.5%、`targetdb.service` 90.5%・92.5%、`role.domain` 94.2%・85.7% |
| B3 | 記録なし | 95.37%・90.34% | `rowActionsName.ts` 100%・100%。報告に `e2e/` のファイルは 0 |
| B1 | 97.8%・91.8% | 95.37%・90.39%（直しの後 90.32%） | — |
| B4 | 97.6%・91.2% | 95.37%・90.32% | `businessdata.domain` 97.5%・89.8%、`businessdata.repository` 95.9%・87.9%（直しの後 96.2%・87.9%）、`businessdata.service` 95.2%・89.1%、`businessdata.web` 97.6%・77.9%、`targetdb.config` 100.0%・99.1%、`targetdb.domain` 100.0%・97.2%、`targetdb.repository` 96.0%・87.5%、`targetdb.service` 89.1%・75.4%、`audit.domain` 99.6%・95.4%、`audit.service` 98.2%・84.8% |
| CI の直し | 97.64%・91.23% | 95.22%・90.30% | — |
| B5 | 97.64%・91.23% | 95.24%・90.32% | `src/features/tables/` 94.34%・90.03%、`src/app/login-state/` 100%・93.33% |
| B6 | 97.64%・91.23% | 95.10%・89.84%（最後の回 89.90%） | `src/features/tables/` 94.01%・88.17%、`src/shared/unsaved-guard/` 100%・96.97%、`src/app/work-role/` 98.90%・96.52% |
| B7 | 97.64%・91.23% | 95.10%・89.90% | `src/features/tables/` 93.86%・88.34% |
| B7 のレビューの直しの後（最後） | 97.64%・91.23% | 95.06%・89.82% | `src/features/tables/` 93.94%・88.39% |

- 下限（行 80%・分岐 70%）は、全体の合計とパッケージごと（新しく作るパッケージ）の両方で、どの回も満たした（`jacocoTestCoverageVerification` が通過）。除外は増やしていない。
- `packagesJudgedByTotal` の7パッケージの本体のソースには、どの Bolt も手を入れていない（一覧は変わらない）。

### 2.3 E2E（`./gradlew e2eTest`）

| Bolt | 件数 | 結果 | 時間 | 基準（232 件・約 10分35秒）との比 | 備考 |
|---|---|---|---|---|---|
| B2 | 232 | すべて通過 | 10.8 分 | 約 1.02 倍 | — |
| B3 | 232 | すべて通過 | 10分43秒 | 約 1.01 倍 | 4.14.0 の試し（統合しない）も 232 件通過・10分51秒・違反 0 件 |
| B1 | — | 流していない | — | — | テストだけの変更（team.md の Way of Working） |
| B4 | 232 | すべて通過（直しの後も 232 件通過・10.6 分） | 10.7 分 | 約 1.01 倍 | — |
| B5 | 255 | すべて通過 | 13分26秒 | 約 1.27 倍 | 185 は 161.7 秒。4.14.0 の試しも 255 件通過・違反 0 件 |
| CI の直し | — | 流していない | — | — | テストだけの変更 |
| B6 | 256 | すべて通過 | 13分45秒 | 約 1.30 倍 | 180 は 2.6 秒・185 は 182.4 秒。途中で失敗させた回でも片付けの後の残り 0。4.14.0 の試しも 256 件通過・違反 0 件 |
| B7 | 256 | 1回目 255 件通過・1 件失敗、直した後は 256 件すべて通過 | 13分29秒 | 約 1.27 倍 | 落ちた1件は 185 のログイン（4節） |
| B7 のレビューの直しの後（最後） | 256 | すべて通過（flaky 0） | 13分2秒 | 約 1.23 倍 | — |

- どの回も、報告の秘密の確かめ（`playwright-secret-check-reporter.ts`）の見つかった件数は 0。見本の PG の 15432 は E2E の前に開け、直後に誤ったパスワードが拒まれる（終了コード 2・`password authentication failed`）ことを確かめ、E2E の後に閉じた。閉じた後も DB `business` の PUBLIC の `TEMPORARY` は `f`、`e2e_biz_%` と `mastersmith_e2e_%` は 0 件、配備したアプリの `/actuator/health` は 200。
- E2E の全件の時間は基準の 1.5 倍（約 15分53秒）の内で、185 の組の分け方を諮る線には当たらない。

### 2.4 アクセシビリティ（axe-core 4.14.0、B7 のレビューの直しの後の E2E の全件）

- どの規則の違反も 0 件。`KNOWN_VIOLATIONS` は空のまま。規則・要素を外す指定は足していない。
- 規則 `label-content-name-mismatch` の区分の passes: C1 882・C2 40・C3 129・C4 420・C5 1,078・C6 176・C7 80・C8 3,160・C9 2,440（どれも incomplete・violations 0）。C4 の1語の氏名の場面の incomplete 658 件は記録だけ（既知の制約 R-04）。区分に当たらない要素の incomplete 113 件（木の開閉 67・表の見出しのボタン 46）は違反ではない。
- vitest-axe（B7 の verify）: 86 ファイル・113 か所で違反 0 件。
- `npm ls axe-core` は 4.14.0 の1つ。`frontend/dist` に axe-core は 0 件。

### 2.5 そのほかの検査（B7 のレビューの直しの後）

| 検査 | 結果 |
|---|---|
| SpotBugs の関門 | 通過（`SQL_` の残り 0 件。priority 2・3 の警告だけ） |
| OSV-Scanner | 失敗の条件 0 件・警告 18 件（axe-core の指摘無し） |
| Gitleaks | 漏れ無し |
| 構造の検査（B4 の時点） | 境界テスト・`ArchitectureTest`・`ApiAccessArchitectureTest`・`ExtensionOrderArchitectureTest` の 92 件 通過 |
| 初回の JavaScript（B6） | 入口 163.9 KB（gzip。B5 の前 159.3 KB、B5 の後 162.0 KB）。目安 500 KB の内で警告なし。画面の chunk は `BusinessDataListPage` 6,744 B・`BusinessDataDetailPage` 1,623 B・`BusinessDataFormPage` 5,760 B・共有の `useTableDefinition` 6,362 B |
| 画面の時間（185、B6 の測り、各 5 回の中央値） | listShown20 50.1 ms・listShown100 70.1 ms・detailFocused 45.2 ms・listShown100Back 179.0 ms・formShown100 32.8 ms。B5 の測り（中央値・最大）は listShown20 54.1・58.6、listShown100 72.4・88.3、detailFocused 46.6・47.5、listShown100Back 176.1・177.5 |

## 3. 失敗の詳細と扱い（この Intent の中で起きたもの）

| 起きたとき | テスト | 失敗の中身 | 扱い |
|---|---|---|---|
| B6 の verify の1回目 | `frontendLintCss` | 新しい CSS 2つでライセンスヘッダーの後に空行が無い（`comment-empty-line-before`） | 空行を足して直した |
| B6 の verify の2回目 | `MariadbBusinessDataReadIT`「MySQL and MariaDB zero dates and out-of-range times are 409 MISMATCH logged as READ_CONVERSION」 | 応答の本文に `838` が含まれないことの確かめが、乱数のトレースID の中の `838` に当たった（約 2%）。行の値の漏れではない | 依頼者の決定でテストだけを直した（N-32、`RandomFieldMask`）。同じ形の確かめを `AbstractBusinessDataReadIT`・`BusinessDataSecretLeakIT`・`BusinessDataWriteSecretLeakIT` でも直し、直す前に落ちること（二段）を確かめた |
| B7 の verify の2回目 | `formDiff.property.test.ts`「compares decimal texts like their numeric values without losing digits」（fast-check、`seed: 1380462492`・`path: "79"`、反例 `[-28n, -28n]`） | `compareDecimalText` が等しい負の数で `-0` を返した（B6 のコードの欠け。画面の判定は変わらない） | 決まった形で再現したため、依頼者の決定で B7 で直した（N-38）。例のテストを同じコミットに入れ、直す前は落ち・直した後は通る二段を記録した |

## 4. 不安定と確かめられていないもの（3件）

team.md の「手元で再現しないものは、再現の試みに先に時間の上限を決め、見立てと試みの範囲を記録したうえで進めてよい」の扱い。依頼者の決定で記録して進めた。どれも時間の上限は延ばしていない。**同じテストがもう一度落ちたら、原因を直すまで次へ進まない。**

| テスト | 起きたとき | 失敗の中身 | 見立て（未検証） | 試みの上限と結果 |
|---|---|---|---|---|
| `frontend/src/eslintImportRules.test.ts` | B3 の 4.14.0 の試しの `npm test` の1回目、B7 の verify の1回目の `frontendCoverage`（`beforeAll` の `Hook timed out in 10000ms`） | ESLint の部品の読み込みの時間切れ。axe-core とその Bolt の変更には触れない | カバレッジの計測の下で 225 ファイルを並べて流す負荷で、ESLint の部品の最初の読み込みが 10 秒を超えた | B7: 単独 3 回とカバレッジの全件 1 回（どれも通過）。その後の verify（2回）でも通過 |
| E2E 185 `business data (b) orange light md` | B7 の E2E の全件の1回目 | `loginAsAdmin` の最初の `getByTestId('login-layout')` が 5 秒で見えなかった（ログインの状態の読み直しの取り次ぎの最中） | 全件の後半の負荷で、`routeLoginPreferences` の取り次ぎが遅れ、ログインの画面の描画が 5 秒を超えた | 185 を単独で 2 回（46 件すべて通過）。その後の全件（2回）でも通過 |
| `frontend/src/features/auth/logoutIntent.test.tsx`「leaves the intent while staying on the same path and clears it on the next path change」 | B7 のレビューの直しの後の verify の1回目 | `peekLogoutIntent()` が真のはずが偽（Intent 261004-role-menu の B9 の後、変えていないテスト） | 並べて流す負荷で、印を立てた後の `await Promise.resolve()` の間に、骨組みの道の移動の副作用が先に流れて印を消した | 単独 5 回と画面のテストの全件 1 回（どれも通過）。2回目の verify と E2E の全件は通過 |

- `eslintImportRules.test.ts` は、B3 の試しと B7 で2つの別々の実行に落ちている（B3 は試しの版の下の `npm test`、B7 は `frontendCoverage`）。どちらも「ファイルの読み込み・`beforeAll` の時間切れ」の同じ形で、team.md の「同じテストが二度目に落ちたら直す」に当たりうる。B7 の時点では依頼者の決定で「不安定と確かめられていない」扱いとした。承認の場で扱いを確かめる（`build-and-test-summary.md` の 8節）。

## 5. CI の不安定なテスト2件の直しの経緯

1. B2 の `ea23bf3` の CI（run 39196260878）の1回目に `PatternCheckerTest`、`0427d45` の CI（run 39172328548）の1回目に `PasswordChangePage.test.tsx`「drops the answer when the screen is left while sending」が落ち、どちらも2回目の試行で通った。依頼者の決定で「手元で再現しないもの」として記録した。
2. B4 の `04b0589` の CI（run 39339950936）は1回目で通った。
3. B5 の統合の後の CI（run 39375104956、`1152202`）は3回の試行とも失敗した: 1回目 `PasswordChangePage.test.tsx` の同じテスト（2度目）、2回目・3回目 `H2CompactionByPoolSuspensionIT`「the H2 file grows while connections are open and shrinks after suspend, evict and resume via the pool MBean」（`total=1, active=0, idle=1` が残り 30 秒で時間切れ。同じテストが2度）。team.md の決まりにより B6 の統合を止め、依頼者の決定で短命の直しのブランチ `fix/261016-business-data-views-ci-flaky` で先に直した（再現の試みはそれぞれ 1 時間まで）。
4. 直し（テストだけ。本番のコード・`vendor/`・`.env` は変えていない）:
   - `PasswordChangePage.test.tsx`: 前の画面の後片付けより先に答えが届く順の抜け。新しい画面の DOM の後に、新しい画面の副作用が出す読み込みの要求（`GET /api/me/preferences`）を `waitFor` で待ってから答えを返す形にした。二段: 使い捨てのテストで `MutationObserver` の直後に答える順を作り 5回とも落ち、直した順で通った。直したファイルを単独で5回流し、5回とも 17 件通過。
   - `H2CompactionByPoolSuspensionIT`: HikariCP の補充の途中で一時停止と破棄をすると、作り終えた1本が後から足されて 0 本にならない待ち合わせの抜け。一時停止の前に補充が済むのを待つ `awaitFillSettled()` を足した（待ちの上限 30 秒は延ばしていない）。二段: `connection-init-sql` から呼ぶ H2 の関数で `connection-adder` を止める使い捨ての変更で、直す前の順は CI と同じ失敗、直した後の順は `total=0` で通った。直したクラスを `--rerun` で5回、5回とも 3 件通過。
5. 直しの後の verify は通過（21分35秒）。テストだけの変更のため E2E は流していない。
6. 依頼者の決定で後の Intent に回した既知の制約: 運用の詰め直しの道具（`docker/hikari-pool.sh compact`）が補充の途中に流すと1本残って詰め直さずに終わりうる（流し直せば済む）。パスワードの変更と表示の設定の画面で、切り替えの直後の短い間に答えが届くと成功の知らせが出うる（直すには本体の変更が要る）。

## 6. CI の試行ごとの結果

B5 の統合の後のコミットの CI を、試行ごと（`gh run view --json attempt・jobs`）に指揮役が確かめた（Q4: A）。

| コミット | run | 試行 | 結果 | 失敗したテスト・検査の job の時間 |
|---|---|---|---|---|
| `1152202`（B5） | 39375104956 | 1回目 | 失敗 | `PasswordChangePage.test.tsx`「drops the answer when the screen is left while sending」 |
| `1152202`（B5） | 39375104956 | 2回目・3回目 | 失敗 | どちらも `H2CompactionByPoolSuspensionIT`（`total=1, idle=1` で 30 秒の時間切れ） |
| `b7291d3`（CI の直し） | 39410449247 | 1回目 | 成功 | 検査の job 31分27秒 |
| `a031ba1`（B6） | 39410572004 | 1回目 | 成功 | 検査の job 28分37秒（前の run の終わりまで約 31 分待った） |
| `d220447`（B7） | 39441119537 | 1回目 | 成功 | 検査の job 32分49秒 |

- `1152202` の2件は、team.md の決まり（同じテストが二度目に落ちたら直すまで進まない）どおり、直しのブランチ（`b7291d3`）でテストの待ちの抜けとして直し、二段で確かめた（5節、段の `construction/code-generation/generation-notes.md`）。直しの後の3つのコミットは、どれも1回目の試行で通った。
- CI の時間は 28分37秒〜32分49秒で、どれも CI の上限 60 分の7割（約 42 分）の内。テストの分け方を諮る線には当たらない。
- **push の後に確かめる点**: 記録のコミット `288736d` と、この段の直し `22de65f`（`develop` に squash 済み）は、まだ push されていない。この2つの CI の試行ごとの結果は、push の後に確かめて記録する（次の段の CI Pipeline か Deployment Execution の前の確かめで扱う）。

## 7. `dslMixed` の `memory.peak` の内訳

結果の全体は `construction/build-and-test/memory-peak-measurement.md`。`perf/README.md` の手順と `perf/dsl-mixed-memory.sh` で、既定の接続先で2回、`RETENTION_TIME=0` の試しで2回、それぞれ別の使い捨ての環境で流した（2026-10-20、`develop` の `288736d`。backend・frontend は `d220447` と同じ）。`memory.max` はどの回も 2048 MiB。

| 回 | memory.peak（MiB） | peak / max | anon の最大 / max | peak の時の file（MiB） | mv.db の伸び（MiB） | 判定（< 0.9） |
|---|---|---|---|---|---|---|
| default-1 | 1788.1 | 0.8731 | 0.6991 | 335.0 | 363.9 | 0.9 未満 |
| default-2 | 1780.9 | 0.8696 | 0.6902 | 343.4 | 354.1 | 0.9 未満 |
| retention-1（試し） | 1515.5 | 0.7400 | 0.7030 | 72.0 | 71.9 | 0.9 未満 |
| retention-2（試し） | 1469.4 | 0.7175 | 0.6865 | 54.3 | 74.8 | 0.9 未満 |

- **判定（BR3.5・FR16.4）: Met**。既定の2回が 0.873・0.870 で、2回とも 0.9 未満。物差し（`memory.peak / memory.max`）は変えていない。`anon` の割合は並べるだけ。0.9 までの余裕は約 55〜62 MiB で小さい。前の Intent の同じ場面は 94.4%・93.1%（Not Met）で、差の原因は確かめていない。
- **条件のそろえ（BR3.4）**: 4回とも k6 の終了コード 0・checks の失敗 0・`http_req_failed` 0・`dropped_rows` 0・`post_missing_seconds` 0・OOMKilled なし。WARN は各回 2 行（起動の案内 2 つ）・ERROR 0・OutOfMemoryError 0 で、片付けの前に数えた。流す前後の `pmset` で眠りは無かった。
- **設定の確かめ（BR4.1）**: `settings.txt` の `RETENTION_TIME` は、試しの2回が 0、既定の2回が 45000（H2 の既定）。試しの結果に使える。
- **試しの結果（BR4.2・BR4.3）**: 試しの2回は 0.740・0.718 で効いた。下がったのはページキャッシュ（`file`）と内部DB のファイルの伸びで、`anon` は変わらない。**`RETENTION_TIME=0` を配備物に取り込むかは、依頼者の決定で後の Intent で決める**（書き込みの途中で止まったときの壊れやすさと値をそのときに決める）。配備物は変えていない。
- **秘密と片付け（NFR4.5・NFR4.6）**: 秘密の検索は2つの段ともすべて 0 件。各段の後に `down -v` と一時ディレクトリの削除を行い、一時ディレクトリ・`mastersmith-perf_` のボリューム・`mastersmith-perf-` のコンテナがどれも 0 であることを確かめた。記録に書いたのは数値・時刻・状態・ロガーの名前と件数だけ。
- **配備したアプリを止めた間**: 2026-10-20T14:53:31Z〜15:17:13Z（約 23分42秒）。同じコンテナで戻し、`/actuator/health` は 15:17:26Z に 200。
- **`k6 inspect` の確かめ（Q3: A）**: この段で、`develop`（22de65f）の `perf/k6/scenarios.js` を `grafana/k6:2.3.0@sha256:9c2dee7f…` の `k6 inspect --include-system-env-vars` で、業務データの 12 の場面（`bizPrepare`・`bizSearchFirst`・`bizSearchLast`・`bizSearchLastSorted`・`bizSearchContains`・`bizSearchWide`・`bizDetail`・`bizCreate`・`bizUpdate`・`bizDelete`・`bizWritePoolLimit`・`bizCreateLarge`）ごとに流した。12 とも終了コード 0 で、場面の名前が1つだけ出た。`bizPrepare`・`bizSearchWide` のほかの 10 の場面は、閾値に `checks{scenario:…}`・`http_req_duration{name:…}`・`iteration_duration{scenario:…}` が出た。判定（p95 と checks）は Performance Validation で流して行う。

## 8. この段の直し（Build and Test、依頼者の決定）

作業ブランチ `fix/261016-business-data-views-bt`（`develop` の `288736d` から）。直したのはテスト・テストの設定・文書だけで、本体のソース（`src/main`・画面の本体）・`vendor/`・`.env` は変えていない。配備したアプリ・見本の PG・Mailpit には触れていない。

### 8.1 `eslintImportRules.test.ts` の不安定な失敗（4節の1行目。同じテストの2度目の失敗のため直した）

**見立て**

- `beforeAll` は ESLint の部品を作り、`frontend/eslint.config.js` を読み込む。重いのは設定の読み込みで、設定が読む `@typescript-eslint/parser` が TypeScript の本体（約 9 MB の JS）を読む。手元の node の単独の測りで、ESLint の読み込み 66〜132 ms、`new ESLint` 1 ms 未満、**設定の読み込み 360〜526 ms**、最初の `lintText` 63〜100 ms、2回目 2 ms。設定を毎回読み直す形ではない（`beforeAll` で1回、ESLint の中でも1回）。
- この重い読み込みは1つの CPU の処理で、Vitest の並べて流す 8 つの作業の中で、jsdom のほかのテストのファイルが一斉に始まるのと重なる（このファイルは前の実行の時間が長いため最初のほうに置かれる）。PC の負荷（Gradle・colima の VM・IDE・ウイルス対策の検査など）が重なると、`beforeAll` の上限（10 秒）を超える。
- 一時の計測（`beforeAll` の中で時間をファイルに書く形。確かめの後に元に戻した）の値: 単独 321〜387 ms、カバレッジの全件の中 1,691 ms、カバレッジ無しの全件の中 643 ms。

**試み**（上限 1 時間を先に決めた。00:32〜01:35 の約 1 時間で終えた）

| 回 | 条件 | `beforeAll` の読み込み | 結果 |
|---|---|---|---|
| 1 | `npm run test:coverage`、負荷を足さない | 1,691 ms | 226 ファイル・1,975 件通過 |
| 2 | CPU を回すだけの処理（`yes > /dev/null`）を 12 本足して `test:coverage` | —（ファイル全体で 24,315 ms） | **`beforeAll` の時間切れで落ちた**（22 件 skipped）。全体が 30 分を超えて進まず止めた（負荷が強すぎて比べに使えない） |
| 3 | 同じ処理を **6 本**足して `test:coverage`（直す前） | **13,350 ms** | **`Hook timed out in 10000ms` で落ちた**（22 件 skipped）。B3・B7 と同じ形 |
| 4 | 同じ 6 本で `test:coverage`（直した後） | **848 ms** | このファイルは 22 件通過 |

- 3回目・4回目では、ほかに画面のテスト（`registration.test.tsx` の「loads the screen lazily」3件、4回目では `PreferencesPage.test.tsx` の4件と `preferences/registration.test.tsx` の1件も）が `findBy` の既定の待ち（1 秒）で落ちた。人工の強い負荷の下だけの失敗で、負荷を足さない実行と verify では落ちていない。この直しの対象の外とし、記録だけにする。

**二段の確かめ**（team.md の不安定なテストの決まり）

- 疑った競り合い（重い読み込みとほかの作業の重なり）を、外から CPU の負荷を足す形で確実に起こせた。同じ負荷（6 本）で、**直す前は落ち（13,350 ms で時間切れ）、直した後は通った（848 ms）**。
- 直した後に、このファイルを単独で 5 回流し、5 回とも 22 件通過（0.85〜1.36 秒）。`--project eslint-config` だけでも 22 件通過。

**直した中身**

- `frontend/vitest.config.ts`: テストを2つの project に分けた。`eslint-config`（`src/eslintImportRules.test.ts` だけ、`sequence.groupOrder: 0`）を先に1つだけで流し、`unit`（ほかのすべて。対象と除外は前と同じで、このファイルを除外に足した）を後に流す（`groupOrder: 1`）。カバレッジの対象・除外・下限は変えていない。
- 時間の上限（`hookTimeout` 10 秒・テストの 5 秒）は延ばしていない。テストのファイル（`eslintImportRules.test.ts`）は変えていない（一時の計測は元に戻した。`git diff` で差は無い）。
- 代わりに、全件の時間はこのファイルの分（約 1 秒）だけ前後に並ぶ。

### 8.2 AC3.4.5 の結合テスト（業務データの読み取りとその拒否は監査に残らない）

- `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataReadIT.java` に `readsLeaveNoAudit`（「reads and their rejections (401, 400, 403, 404, 409) leave no audit row at all」）を足した。既存の読み取りの結合テストの形に合わせ、子のクラス（`PostgresBusinessDataReadIT`・`MysqlBusinessDataReadIT`・`MariadbBusinessDataReadIT`）で **3種類の対象DB で流れる**。
- 中身: 利用者の用意（403 の利用者は `ITEM` だけ NONE、ほかは READ）を先に済ませてから、内部DB の `audit_events` の**すべての行**を数え、実際の HTTP で次を送った後に行の数が変わらないことを確かめる。
  - 成功: 定義・一覧・詳細（200）
  - 拒否: 未認証の一覧・詳細（401）、ページの上限を超える一覧（400）、要る権限だけを欠く利用者の一覧・詳細（403 `ACCESS_DENIED`）、DSL に無いテーブル（404）、無い行の詳細（404 `BUSINESS_ROW_NOT_FOUND`）、ビューの重なる行（409 `BUSINESS_ROW_NOT_UNIQUE`）、主キーの無いテーブルの詳細（409 `BUSINESS_ROW_OPERATION_NOT_ALLOWED`）、DB に無いテーブル（409 `BUSINESS_TARGET_DB_MISMATCH`）
- 3種類の子のクラスを `--tests '*BusinessDataReadIT'` で流し、3つとも通過（各 17 件）。verify の中でも通過。

### 8.3 手順書の食い違いの直し

- `README.md` の 38 行: make-you-chic-ui の固定先を `a9309c4` から **`b0a1e79`** に直し、経緯に `b0a1e79`（この Intent の B3。Dropdown の説明付きの項目の説明の要素を menuitem の外へ出し、規則 `label-content-name-mismatch` に違反しない形にする直し。固定先の更新のコミット `e34c260`）を足した。`git submodule status` で `b0a1e79ef4f7…` を確かめた。
- `perf/README.md` の U5 の節（`dslMixed` の `memory.peak` と `memory.stat` の記録）: 「Performance Validation の段が無いため」を、「この Intent には Performance Validation の段があるが、`memory.peak` の内訳の測りは依頼者の決定（Build and Test の質問の Q2: A）で Build and Test で行った」に直した。前の Intent（261015-deps-ui-carryover）の節の同じ文は、その Intent の事実のため変えていない。

### 8.4 直した後の verify

- `caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡した）: 2026-10-21 01:37:54〜02:00:21、**BUILD SUCCESSFUL、22分26秒**（B7 の後は 21分50秒。CI の上限の7割の約 42 分の内）。
  - 1回目（01:36:33）は、足したテストの1行が書式の検査（`spotlessJavaCheck`）で落ちた（8 秒）。`spotlessApply` で直してから流し直した。
- バックエンド: 単体 **2,589 件**・結合 **1,389 件**（計 3,978 件。B7 の後より +3 件 = 足したテスト × 3種類の DB）、失敗 0、SKIPPED 9。JaCoCo の全体の合計は **行 97.64%・分岐 91.23%**（本体のソースに触れていないため B7 の後と同じ）。
- フロントエンド: **226 ファイル・1,975 件**がすべて通過（`frontendTest`・`frontendCoverage` の両方）。カバレッジの全体の合計は **行 95.06%・分岐 89.80%**（下限 80%・70% を保つ。下限・対象・除外は変えていない）。
- 安全の検査: Gitleaks は「no leaks found」。OSV-Scanner は入力（lockfile）が変わらないため UP-TO-DATE（前の通過のまま）。SpotBugs の関門は通過。
- 流した後の `pmset -g log` に、この間の Sleep・DarkWake は無い（最後の Sleep は 2026-10-18 21:57:49）。
- E2E は流していない。直しはテスト・テストの設定・文書だけのため（team.md の Way of Working）。

## Sources

- 各単位の `code-generation/code-summary.md`・`generation-notes.md`（U1 の 3節、U2 の 3節・4.1、U3 の 4節・7節・8節・10節・11節、U4 の Step 15〜19、U5 の Step 14 と 4節）
- `aidlc/spaces/default/intents/261016-business-data-views/construction/code-generation/generation-notes.md`（CI の不安定なテスト2件の直し）と `memory.md`
- `build-and-test-questions.md`（Q1: B・Q2: A・Q3: A・Q4: A）
- `git log --oneline`（`develop` のコミットの順）
- 8節: この段の直しの実行の記録（一時の計測の値、負荷を足した `test:coverage` の結果、verify の出力、`backend/build/test-results/`、`git submodule status`）

## Assumptions & Open Questions

- [assumption] `develop` の `d220447` は B7 の作業ブランチを squash で統合したもので、最後の作業ブランチの上の結果（2節の最後の行）が `develop` の中身にも当たると見る。B5・B6・直しを合わせた形の確かめは CI が受け持つ（6節）。
- 6節と7節は足した（CI の試行ごとの結果・`memory.peak` の内訳）。Target Verification Matrix に `Pending` は残っていない。
- 4節の3件のうち `eslintImportRules.test.ts` は、この段で直した（8.1）。4節の行は起きた時点の記録として書き換えていない。残る2件（E2E 185 のログイン・`logoutIntent.test.tsx`）は、引き続き「不安定と確かめられていない」扱い。8.1 の人工の強い負荷の下でだけ落ちた画面のテスト（`registration.test.tsx`・`PreferencesPage.test.tsx`・`preferences/registration.test.tsx`）は、依頼者の決定で記録だけにし、CI や手元で落ちたら team.md の決まりで扱う。
- 最新の verify は 8.4（`develop` の `22de65f` と同じ中身、22分26秒）。E2E は B7 の後の作業ブランチの上の 256 件の通過を正とし、この段の直し（テストと文書だけ）の後は流していない。
