# コードの要約（Code Summary）— U6 役割・権限の管理の画面（role-admin-ui）、Bolt B8

計画 `code-generation-plan.md`（13節の Q1〜Q7 はすべて A）に沿って、Step 1〜29 を行った。作業ブランチは `feature/261004-role-menu-b8`（develop の 3977919 から）。コミットは依頼者の承認を得て、計画 3節の区切り（C1〜C9）ではなく機能ごとの6つにした（ec0d994 入口の差し替えと共有の部品、bae7c06 useradmin の広げと検索の文字の確かめの shared への移し、23f2b3d roleadmin、b8517e4 groupadmin、1b3422f roletransfer、583c975 E2E と README）。手順ごとに同じ機能のフォルダーの中をファイルで振り分けると途中でビルドが通らない区切りが出やすいためで、機能どうしは import し合わないため、どのコミットの時点でもビルドが通る（ec0d994・bae7c06 の時点で型の検査を確かめた）。develop へは squash で1コミットになる。Step ごとのコマンドと結果は `generation-notes.md` にある。

## 1. 作ったもの・変えたもの

| 区分 | 道（`frontend/` の下） | 中身 |
|---|---|---|
| 入口（C1） | `src/main.tsx`、`src/app/routing/DataRouterBlocker.test.tsx`（新） | `BrowserRouter` を `createBrowserRouter`＋`RouterProvider` に替えた（前処理と `StrictMode` は保つ）。`useBlocker` がサイドバーの移動とブラウザの戻るで止まることの見張りのテスト |
| 共有（C2） | `src/shared/api-client/apiClient.ts`、`src/shared/validation/validateAdminName.ts`（新）・`limits.ts`・`searchText.ts`（useradmin から移した） | `ApiDownload` の `headers`、ロール・グループの名前の画面の検査（Cc と U+2028・U+2029 を拒み、Cf は拒まない）、検索の文字の確かめの共有 |
| roleadmin（C3・C4、新） | `src/features/roleadmin/` | S3 ロールの一覧、S4 権限の設定（共有の木・表・未保存の確かめ）、S5 割り当て（候補・D19 の確かめ）、登録（道3つ、サイドバー「ロール」240）、API の関数 18、純粋な関数（`permissionDraft`・`permissionLabels`・`removalImpact`・`failureMessage`）、`paths.ts` |
| groupadmin（C3・C5、新） | `src/features/groupadmin/` | S6 グループの一覧と詳細（メンバーの表・候補・D20・D21）、登録（道2つ、「グループ」250）、API の関数 10、`removedRoles`・`failureMessage` |
| roletransfer（C3・C6、新） | `src/features/roletransfer/` | S7 権限の受け渡し（書き出し・ファイルの選択・確かめの結果の表・誤りの一覧・適用の確かめ）、登録（`/admin/role-transfer`、「権限の受け渡し」260）、API の関数 3、`changePaging`・`reasonMessage`・`safeFileName`・`transferLimits`・`saveFile` |
| useradmin（C7） | `src/features/useradmin/` の `rowActions.ts`・`useUserAdmin.ts`・`UserAdminPage.tsx`・`messages.ts`・`api/`、`UserRolesDialog.tsx`（新）・`.css`（新） | S9 行の操作の最後に「ロールを見る」、読み取りの Modal（D27）。`ConfirmActionKind` から `viewRoles` を外した |
| E2E（C8） | `e2e/support/adminApiRoute.ts`（`body` の形を足す）、`e2e/support/roleAdminFixtures.ts`・`u6Measure.ts`・`shapeCompare.ts`（新）、`e2e/140-role-admin-accessibility.e2e.ts`・`e2e/150-role-permission-flow.e2e.ts`（新） | 140 は 20 組 × 12 の状態の axe と幅・道の扱い・画面の時間の測り（本数に数えない）。150 は F の流れ（この Intent の2本までの1本） |
| 文書（C9） | `README.md`（リポジトリのルート） | E2E の表の 140・150、「140・150 について」、画面の節 |

バックエンド・移行・Gradle・Vitest と Playwright の設定・`package.json`・`package-lock.json`・`vendor/` は変えていない（Step 27 で差が無いことと lockfile の SHA-256 が Step 3 と同じことを確かめた）。

## 2. 実装で決めたこと

- **応答の形は実装に合わせた**（計画 2.3）: 木の応答は `{items}`、保存の `scope` に `columnName` を送らない、利用者・グループのロールは配列、誤りの `line`・`column`・`path` は null を許す。
- **出どころの値（13節 Q5: A）**: 割り当てと利用者のロールの出どころは、実装の `RoleSourceResponse.from` が返す `type` の `DIRECT`・`GROUP`（`DIRECT` の `groupId`・`name` は null）を正として読み、画面の型の `kind` に写す。role の `code-summary.md` の B5-5 は「`USER`・`GROUP`」と書いており実装と食い違うが、role の記録は書き換えていない。150 で本物の応答と見本の形が一致することを確かめた（違い 0 件）。
- **`useSlowNotice`・`latestLoad` を3つの機能に複製した**: 機能どうしの import の禁止（ESLint の制限）のため、roleadmin・groupadmin・roletransfer にそれぞれ同じ形を持つ（専用のテストは roleadmin だけに置き、groupadmin・roletransfer の複製は画面のテストを通して確かめる）。同じく `CandidateDialog`（groupadmin は利用者の検索だけ）・名前の Modal・削除の確かめ・`saveFile`（DSL と同じ形）も各機能に持つ。
- **未保存の確かめ**: `useBlocker` は道（pathname）が変わる移動だけを止め、木の節の選び（道は変わらない）は画面の中で同じ Modal を出す。`beforeunload` は写しがある間だけ張る。「留まる」の後は、開いたときのフォーカスがタブなら選んでいるタブへ戻す。
- **保存の後の読み直し**（計画 D-9）: スキーマの一覧を新しい配列にして共有の木に開いている節を読み直させる。選んでいるスキーマのテーブルの一覧は先に読み、木の `loadChildren` はそれを使って二重に読まない。
- **候補の Modal の 403**: 足した結果に `handled`（骨組みが扱った）を足し、画面は文言を出さない（D2）。
- **S7**: 確かめたときに読んだ本文と指紋は画面の状態にだけ持ち（NFR 設計 Q1 A）、選び直し・適用の成功・画面を離れたときに捨てる。変わる点が無いのは「ロールが無い、または作るロールが無く、どのロールの足す・変わる・消えるも 0」。適用の `ROLE_BUSY` は結果を残してもう一度適用できる。待ちの案内は確かめ・適用で最大 15 秒・30 秒を文に含める（NFR2.10）。
- **開いたときの読み込み**は効果の中から `queueMicrotask` で始め、後始末で取りやめる（oxlint の `react(set-state-in-effect)` のため。振る舞いは同じ）。
- **横に動く表の領域**（13節 Q7: A）: `TransferErrorList` と S7 の開いた行の変わる点の表は `role="region"`・`tabIndex={0}`・読み上げの名前を持ち、oxlint の `jsx-a11y/no-noninteractive-tabindex` はその行だけを日本語の理由つきのコメントで外した。

## 3. テストの量とカバレッジ

| 時点 | 画面のテストのファイル | 件数 | 行 | 分岐 |
|---|---|---|---|---|
| Step 3（変更の前） | 114 | 1,046 | 97.52% | 92.68% |
| Step 22 | 165 | 1,408 | 94.83% | 89.46% |
| Step 29（verify の `frontendCoverage`、実測） | 165 | 1,409 | 94.88% | 89.59% |

機能ごと（Step 22、行・分岐）: `features/roleadmin` 91.24%・84.02%（`api` 88%・84.84%）、`features/groupadmin` 91.41%・84.42%（`api` 85.26%・80.51%）、`features/roletransfer` 92.55%・88.77%、`features/useradmin` 96.17%・90.93%、`shared/validation` 100%・97.72%。分岐の低いファイルは `roleadmin/useRolePermissions.ts`（71.42%）・`groupadmin/useGroupDetail.ts`（74.41%）・`roleadmin/useRoleAssignments.ts`（75.3%）と、複製の `groupadmin/useSlowNotice.ts`（50%）・`roletransfer/useSlowNotice.ts`（62.5%）。計測の除外は足していない。統合の前の関門（Step 29）の値は 8節。

E2E: 140 は 25 件（20 組・幅3・道の扱い・測り）、150 は1件。口の変更の後も 120・130・135 は 66 件 passed。

画面の時間（140 の測り、既定の1組、画面の中の `performance.now()`、5 回の中央値。記録だけ）: NFR2.5（100 カラム）31 ms・NFR2.6（保存）22 ms・NFR2.7（25 ロール × 1万の確かめ）232 ms（ヒープ 約 148 MB）・NFR2.8（次のページ）15 ms はすべて目標以内（Met）。1,000 カラムの選び 61 ms・1,000 カラムの Select の変更 33 ms は記録だけ。差し替えた応答でも `responseEnd` が得られた（`cicd-pipeline.md` 7節 (iv)）。

入口の量（計画 10節）: 新しい5つの画面はすべて `isDynamicEntry: true` で、入口からたどった静的な集まり（`index`・`useTranslation` の2つ）に無い。`UserRolesDialog` は利用者の管理の画面の遅延読み込みの塊の中。→ 満たす。初回の JavaScript の合計は 126.3 KB → 151.9 KB（gzip、+25.6 KB、記録だけ。見立て（未検証）は入口が静的に読む3つの機能の `messages.ts` と data router の分）。

## 4. 計画との差

### 4.1 テストで見つけて直した本体の誤り

| 誤り | 直し | 見つけた所 |
|---|---|---|
| S4 の共有の木に渡す `nodes` を描画のたびに新しい配列で作っていたため、木が開いている節を読み直し続け、`listTables` が数百回呼ばれた | `PermissionPanel.tsx` で `useMemo` にし、スキーマの一覧が変わったときだけ作り直す。テストに「テーブルの一覧は1回だけ読む」「保存の後も二重に読まない」を足した | Step 15 `PermissionPanel.test.tsx` |
| 見出しの段が飛び（h1 → h3）、axe の `heading-order` に当たった | S4 の表の見出しと S5 の利用者・グループの見出しを h2、カラムを h3 にした | Step 15 `RoleDetailPage.test.tsx` |
| S3 の削除の成功の後の一覧の見出しへのフォーカスが、Modal の閉じた後のフォーカスの戻しに上書きされることがあった | Modal を閉じた後の読み直しを `runAfterModalClosed` で閉じ終わった後に始める（招待の画面と同じ形。S3・S5・S6） | Step 13 `RoleListPage.test.tsx` |
| 候補の Modal で「足す」の API が 403 を返すと、骨組みに渡したうえで Modal の中に汎用の失敗の文言を出していた（D2 に反する。roleadmin の S5 と groupadmin の S6 の両方） | 足した結果に `handled` を足し、文言を出さない形にした。roleadmin の 403 の表に「S5 add user」の行を足した | Step 17 `GroupAdminForbidden.test.tsx` |
| S7 の待ちの案内に、確かめは最大 15 秒・適用は最大 30 秒の文を入れていなかった（NFR2.10） | 文言 `roletransfer.slow.check`・`roletransfer.slow.apply` を足し、偽の時計のテスト（4.9 秒で出ず 5 秒で出る）を足した | Step 28 で NFR 設計の traceability と突き合わせて見つけた |

### 4.2 テストの直し

- `DataRouterBlocker.test.tsx`（Step 5）の印 `guard-ready` を2回直した。(1) 効果の中の `setState` が oxlint の `react(set-state-in-effect)` に当たったため DOM の属性で立てる形に。(2) 画面のテストの全体の中で「ブラウザの戻る」が1回落ちた（手元で再現）。`useBlocker` は2回目の描画の効果で `router.getBlocker` に登録する（`node_modules/react-router` の `hooks.js` で確かめた）ため、印を2回目の描画の効果で立てる形に直した。本体は変えていない。
- roleadmin の見本の `ZERO_WIDTH_NAME` に U+200B の文字そのものが入っていたため、`​` のエスケープの書き方にそろえた。

### 4.3 そのほかの差（判断の要らない小さなもの）

- `paths.ts`（roleadmin・groupadmin）を足した。登録と画面の中のリンクが同じ関数で道を組むため。
- 画面の上の知らせは Alert の役割（成功は `role="status"`、拒否は `role="alert"`）で読み上げ、包む要素に `aria-live` を重ねない（二重の読み上げを避ける）。
- 名前の Modal の画面の検査の誤りは、入力を始めた後か送ろうとした後に出す。
- 今の DSL に無い印は Badge に `warning` が無いため `danger` の見た目（意味は文字で示す）。
- S6 の一覧の `GROUP_IN_USE` の知らせにはリンクを付けない（D9 のリンクはロールの割り当てのタブのため）。
- `GroupRowActions.test.tsx` を足した（計画の一覧に無い）。計画の `RoleTransferEnglish.test.tsx` は `RoleTransferForbidden.test.tsx` の中に置いた。
- S7 の開いた行の「次へ」に `data-testid="roletransfer-changes-next"` を足した（140 の測りで選択子に使う）。
- 汎用の `e2e/support/shapeCompare.ts` を足し、既存の `userAdminFixtures.ts` の `shapeDifferences` は変えなかった。
- 150 は作ったロールを一覧の行からではなく、一覧の API で ID をたどって道で開いた（ロールが多いと後のページに出るため）。DSL の投入の応答は 201（プレビューを作る）。
- README の E2E の表に `135-dsl-admin-accessibility.e2e.ts` の行が無い（前の Bolt の書き漏れ）。範囲の外のため足していない。

### 4.4 E2E の 060 の1回の失敗（再現せず）

Step 5 の E2E の全体（010〜135）の1回目で、`060 … invitations (c) light sm blue 375px` が1件落ちた（`loginAsAdmin` の `goto('/')` の後、5 秒以内にログインの画面が出なかった。`support/loginPreferences.ts` が差し替える `POST /api/auth/session/refresh` の `route.fetch()` がテストの終わりまでサーバーから返っていなかった）。再現の試みの上限を「060 の単独 2 回と 010〜060 の通し 1 回」と先に決めて流し、21・21・48 passed で再現しなかった。見立て（未検証）は PC の負荷による refresh の応答の遅れで、ルーターの差し替えとは別の経路。2回目の全体は 177 passed。`team.md` の「手元で再現しないもの」に当たり、原因は確かめていない。Step 29 の結果は 8節。

## 5. 承認の場で確かめたいこと

1. 画面のテストの全体のカバレッジが行 97.52% → 94.83%・分岐 92.68% → 89.46% に下がった（下限は満たす）。分岐の低いフックと複製の `useSlowNotice` にテストを足すか。
2. 初回の JavaScript の合計が +25.6 KB（gzip）増えた（判定の (i)(ii) は満たし、合計は記録だけの決まり）。入口が静的に読む `messages.ts` を遅延にするかは後の Intent で扱うか。
3. 出どころの値の記録の食い違い（role の B5-5 の「`USER`・`GROUP`」）を role の記録で直すか（13節 Q5: A では書き換えない決まり）。
4. README の E2E の表に 135 の行が無いことを、この Bolt で足すか。
5. 機能設計の traceability で Deferred だった画面の確かめを持つ AC（AC1.1.12・AC3.1.4・AC3.1.7・AC3.1.13）を、コード生成の `traceability.json` では OK（画面の部分）にした（計画 2.2 の R-03）。サーバーの持ち物は U3・U4 のまま。

## 6. 後に回すこと・引き継ぎ

- **Build and Test**: 入口の量の前後の値と判定（3節）、遅延読み込みの塊の大きさ（RoleListPage 3.9・RoleDetailPage 9.7・GroupListPage 3.5・GroupDetailPage 3.7・RoleTransferPage 5.6・UserAdminPage 7.3 KB gzip）、画面の時間（3節）、E2E の全体と `verify` の時間（8節）。
- **配備の段**: 前の版に戻すと U6 の画面が無くなる（戻しはイメージだけ）。入れ替えの後は開いているタブを読み込み直す（README に書いた）。スモークテストで依頼者がロール・グループ・割り当て・受け渡しを触ったときは、監査に残る操作をその場で聞き、監査の確かめの期待の件数を合わせる。150 を流すと適用中の DSL が `E2EF_` のものに置き換わる（E2E の一時の内部DB の中だけ）。
- **Observability Setup**: 画面が呼ぶ API の時間は group・role の監視の区画で見る（`monitoring-design.md` 3節）。
- **U7 app-frame-ui（B9）**: `main.tsx` は data router のまま。150 の 6 の後に作業ロールの切り替えとサイドバーの変化を足す。骨組みのログアウトでは未保存の確かめが出ないことがある。管理の区画の今の項目は詳細の道（`/admin/roles/:roleId` など）との前方一致で決めるかを U7 で決める。

## 7. 受け入れた制約

- S7 は確かめた本文（最大 10 MiB）を文字列で画面に持つ。JavaScript の文字列は UTF-16 のため、最大 約 20 MiB になり、置換文字（読めないバイト）で増えうる（NFR 設計の読み直し R-06、記録だけ）。
- 画面の時間は手元の1台・1つのブラウザで、差し替えた応答で測った記録で、API の時間は含まない。
- 画面の時間の測りの NFR2.7 のヒープの値は Chrome の `performance.memory`（丸めた値）で、ほかのブラウザでは取れない。

## 8. 統合の前の関門（Step 29）

| 検査 | 時刻（2026-10-12） | 結果 |
|---|---|---|
| `caffeinate -di ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を付けた） | 22:40〜22:56 | BUILD SUCCESSFUL（15 分 32 秒） |
| 画面のテスト・カバレッジ | 同上 | 165 ファイル・1,409 件 passed。行 94.88%・分岐 89.59%（文 94.75%・関数 96.96%） |
| バックエンドのテスト | 同上 | 単体 240 クラス・2,082 件、結合 222 クラス・1,165 件。失敗・中断・SKIPPED 0。JaCoCo 全体 行 97.92%・分岐 92.44%、下限の検証を通った |
| セキュリティ | 同上 | Gitleaks「no leaks found」、SpotBugs の止める基準に当たる指摘なし、OSV-Scanner は lockfile が変わらず UP-TO-DATE（前回の合格のまま） |
| `caffeinate -di ./gradlew e2eTest`（010〜150） | 22:57〜23:06 | 203 passed（9.0 分）、failed・flaky 0。報告の部品で残してはならない値 0 件（値の種類 7・json の添付 300） |

- 1回目の verify（15:27〜15:52）は、PC が利用者のいない DarkWake の状態から「Dark Wake Thermal Emergency」で 664 秒休止したため、`frontendCoverage` の `InviteDialog.test.tsx`・`LoginLanguageSwitch.test.tsx` の各1件が時間切れで落ちた（`pmset -g log` で確かめた。コードの誤りではない）。結果は使わず、依頼者が PC を起こした後に流し直した。2回目の verify と E2E の間には休止が無いことを `pmset -g log` で確かめた。
- 前の1回ずつの失敗（B4 の 135 `dsl admin green dark 360px`、B8 Step 5 の 060 `invitations (c) light sm blue 375px`）は、今回の全体ではどちらも通った。
- 報告のフォルダーは消した。詳しいコマンドと値は `generation-notes.md` の Step 29。
