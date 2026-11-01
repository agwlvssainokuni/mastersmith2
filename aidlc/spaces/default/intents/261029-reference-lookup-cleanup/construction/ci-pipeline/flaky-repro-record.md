# CI の画面のテストの「処理されない誤り」の再現の試み（run 41264314230 の1回目）

team.md の Testing Posture「不安定なテストと CI の失敗」の決まりに従い、時間の上限を決めて手元で再現を試みた記録。コードは変えていない（読み取りと実行だけ）。

## 1. 対象の失敗

- 追記: 同じ run の2回目の試行も同じ形で落ちた（今度は `:frontendCoverage`（`test:coverage`）。`:frontendTest` は2回目では通過）。238 files・2,312 tests passed、`Errors 1 error`、スタックと帰属は1回目と同じ。**同じ誤りの二度目**のため、team.md の決まりにより直すまで次へ進まない。

- CI: run 41264314230 の1回目（コミット e1e028d1。コードは df79634c と同じ）、`./gradlew verify` の `:frontendTest`（`npm test`、カバレッジなし）。
- 結果: Test Files 238 passed・Tests 2,312 passed だが、Vitest が処理されない誤りを1件報告して失敗（`Errors 1 error`）。
- 誤り: `ReferenceError: window is not defined`。スタックは `Timeout._onTimeout`（`vendor/make-you-chic-ui/packages/make-you-chic-ui/dist/index.js:1198`、Tooltip の 300 ms の `window.setTimeout` の中の `setState`）→ `dispatchSetState` → `requestUpdateLane` → `resolveUpdatePriority`（react-dom が `window.event` を読む）。
- Vitest の帰属: `src/features/tables/BusinessDataListPage.write.test.tsx` を流している間に起きた（そのファイルの中で投げられたとは限らない、という定型の文）。そのファイル自体は 8 件すべて通過（CI で 1,431 ms）。

## 2. 試みの時間と結果

- 時間の上限: 開始から 30 分（壁時計）。開始 2026-11-01T02:30:18Z、終了 2026-11-01T02:59:05Z（5 番目の試みを上限の前に止めた）。
- 作業場所: develop（HEAD e1e028d1）、`frontend/` で `npx vitest`（リポジトリの `vitest.config.ts` をそのまま使い、`npm test` と同じ `NODE_OPTIONS=--no-experimental-webstorage` を付けた）。すべて `caffeinate -dims` で包んだ。PC は CPU 8。
- 記録の置き場（リポジトリの外の作業用）: スクラッチパッドの `repro/` の下の a1〜a4 のログ。

| # | 時刻（UTC） | コマンド（要点） | 回数・規模 | 結果 | 処理されない誤り |
|---|---|---|---|---|---|
| 1 | 02:30:59〜02:31:42 | `vitest run --project unit src/features/tables/BusinessDataListPage.write.test.tsx` を順に | 20 回 | 毎回 8 passed、終了コード 0 | 0 回 |
| 2 | 02:32:04〜02:36:22 | `vitest run --project unit --maxWorkers=1 src/features/tables/`（このファイルの後に同じ作業プロセスの枠で別のファイルが続く形） | 5 回（各 41 ファイル・517 件） | 毎回全件 passed | 0 回 |
| 3 | 02:36:33〜02:41:30 頃 | CPU の負荷（`yes > /dev/null` を 12 本、負荷平均 約 80）の下で、1 と同じコマンドを 6 本同時に | 6 回（1 巡目。2・3 巡目は台本の `wait` の誤りで流れず、02:41 に止めた） | 6 回とも 8 passed（1 回が約 131 秒まで遅れた） | 0 回 |
| 4 | 02:41:38〜02:45:38 | CPU の負荷（`yes` を 6 本）の下で、画面のテストの全件 `vitest run --maxWorkers=4` | 1 回 | 238 files・2,312 tests passed、終了コード 0（237.5 秒） | 0 回 |

| 5 | 02:45:54〜02:59:05 | 強い負荷（`yes` を 10 本）の下で、全件 `vitest run --maxWorkers=2` | 1 回（時間の上限のため途中で止めた。止めるまでの出力に誤りは無し） | 未完 | 0 回（途中まで） |

結論: 時間の上限の中では **再現しなかった**（不安定と確かめられていない扱い）。

## 3. 見立て（仕組み。未検証の部分を含む）

### 3.1 確かめた事実（ソースを読んで確かめた）

1. make-you-chic-ui の Tooltip（`vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/Tooltip/Tooltip.tsx`、固定先 4c63d11）は、マウスが入る・フォーカスが来ると `scheduleShow`（65〜68 行）で `window.setTimeout(() => setVisible(true), 300)` を予約する。`hide`（70〜73 行）でしか取り消さず、**外されたとき（unmount）に予約を取り消す後片付け（`useEffect` の cleanup）が無い**。配布物 `dist/index.js` の 1197〜1199 行が同じ処理。
2. 業務データの一覧の表（`frontend/src/features/tables/BusinessDataTable.tsx`）は、行の詳細へのリンク（128〜143 行）と各セルの文字（165〜167 行）を Tooltip で包む。
3. `BusinessDataListPage.write.test.tsx` は偽の時計（`vi.useFakeTimers`）を使わず、`userEvent.setup()` の `user.click` で行のリンク「詳細: 商品 A01」を押す（133・139・166 行）。user-event の click はポインターの移動（mouseover/mouseenter）とフォーカスを伴うため、Tooltip の 300 ms の予約が入る。押すと詳細の画面へ移り、一覧の画面（と Tooltip）は外されるが、予約は残る。
4. ファイルの最後のテスト「does not move or show a toast when a delete answer arrives after going back」（145〜190 行）は、166 行でリンクを押した後、手元では全体が約 100 ms で終わる（`--reporter verbose` で確かめた）。つまり、**ファイルの最後のテストが終わり jsdom の環境が片付けられた（`window` などの大域の値が消された）時点で、300 ms の予約がまだ残っている**のが手元でも常の状態。
5. 予約が `window` の消えた後に走ると、`setVisible` → react-dom の `resolveUpdatePriority` が `window.event` を読んで `ReferenceError: window is not defined` になる。CI のスタックと一致する。

### 3.2 見立て（確かめていない部分）

- 誤りが表に出るのは、予約が「jsdom の片付けの後」かつ「作業プロセスがまだ生きている（次のファイルの環境が整う前、またはプロセスが終わる前）」の間に走ったときだけ。手元では、片付けの後すぐ作業プロセスが終わる（または次の環境が整う）ため、予約は走らずに消えると見る。CI（GitHub の実行機、CPU が少なく負荷が高い。CI の内訳は environment 38%・setup 26%）では、片付けから次の環境の準備・プロセスの終わりまでの間が 300 ms 近くまで延び、予約がその隙間で走ったと見る。
- どの作業プロセスの再利用・終わり方（Vitest 5.0.5、既定の forks・isolate）で隙間ができるかは確かめていない。手元の負荷（3・4）でも隙間は作れなかった。

### 3.3 誤りがテストの合否を変えない理由

一覧の画面はすでに外されており、`setVisible` は外された部品への更新で、どのテストの検証にも関わらない。誤りは「処理されない誤り」として Vitest が全体を失敗にする（`dangerouslyIgnoreUnhandledErrors` は使っていない）ことだけが影響。

## 4. 直し方の候補（どれも未実施。決めるのは依頼者）

| 案 | 内容 | 置き場 | 手間の見込み | 長所・短所 |
|---|---|---|---|---|
| A（上流、根本） | Tooltip に外されたときの後片付け（`useEffect(() => () => window.clearTimeout(showTimeoutRef.current), [])`）を足し、固定先を上げる | make-you-chic-ui のリポジトリ（依頼の文面を `<record>/make-you-chic-ui-request-<n>.md` に置く）→ 固定先の更新の専用のコミット（fast-forward）・verify・E2E・CI | 上流の直しは数行と単体テスト1件。こちらは固定先の更新の手順（package-lock の差と `hasInstallScript` の確かめ、verify・E2E）で 1〜2 時間 ＋上流の待ち | 本番でも外した後の無駄な更新が無くなる。上流の対応を待つ |
| B（こちらのテスト） | 最後のテスト（または Tooltip を押すテスト）の終わりで、予約が流れ終わるまで待つ（例: 押した後の流れの終わりに `act` の中で 300 ms 余り待つ）か、詳細へ移る前にリンクからポインターを外す（`user.unhover`。ただし外された後なので効かない場合がある） | `frontend/src/features/tables/BusinessDataListPage.write.test.tsx` | 数行・30 分程度（単独の繰り返しと全件1回の確かめを含む） | テストだけの変更で早い。原因は上流に残り、ほかのテスト（Tooltip を持つ画面を押して終わるもの）で同じことが起こりうる |
| C（こちらの共通の後片付け） | `vitest.setup.ts` の `afterEach` で、外されずに残った予約を流すか消す（例: 全テストを偽の時計で動かすのは影響が大きいので不可。`afterAll` で 300 ms 余り待つ形は全ファイルに約 0.3 秒ずつ足す） | `frontend/vitest.setup.ts` | 待つ形は数行だが、全ファイル（約 238）で計 1 分強遅くなる | 広く効くが、時間を足す代償が大きい。原因を隠す形でもある |

推奨（参考）: A を上流へ依頼し、取り込むまでの間は CI の再実行で進める（手元で再現しないため、team.md の「手元で再現しないものは CI の再実行で通れば進めてよい」に当たる）。同じ誤りが二度目に出たら、決まりどおり B を入れて止める（B はテストだけの変更で、上流の直しが入った後も害は無い）。

## 5. team.md の決まりとの対応

- 手元で再現しなかった → 「手元で再現しないものは、時間の上限を決めて試み、見立てと範囲を記録したうえで、CI の再実行で通れば進めてよい」に当たる。
- 同じ誤り（Tooltip の予約による `window is not defined`）が二度目に出たら、原因を直すまで次へ進まない。→ 同じ run の2回目の試行で二度目が出たため、直すまで進まない状態にある（6・7節）。

## 6. 直す前に落ち・直した後に通ることを決定的に作る手段（未実施の案）

誤りの条件は「Tooltip の 300 ms の予約が、jsdom の片付け（大域の `window` が消える）の後に走る」こと。時間の競り合いに預けず、この順を確実に作る。

- 手段 D1（推奨。確かめ用の一時のテスト、コミットしない）: `BusinessDataListPage.write.test.tsx` の最後に一時的に次の `afterAll` を足す。`cleanup()` の後で、Node の本物の時計（`window.setTimeout` ではなく、jsdom を通さない `node:timers/promises` の `setTimeout`）を使い、`globalThis.window` を一時的に外して 400 ms 待ち、元に戻す。
  - 直す前: 待つ間に Tooltip の予約が走り、`ReferenceError: window is not defined` が処理されない誤りとして毎回出る（CI と同じスタックになる見込み）。
  - 直した後（下の B2・B4・上流の A）: 予約は流れ終わっているか消されているため、誤りは出ない。
  - 注意: Vitest が jsdom の大域の値を消せる形（configurable）で置いていることが前提。外せなければ、代わりに「片付けの後に予約が残っているか」を数える（下の D2）。
- 手段 D2（補助）: `vitest.setup.ts` 相当の一時の確かめで `window.setTimeout`・`clearTimeout` を包んで予約の ID を数え、`cleanup()` の後に残った予約の数を出す。直す前は最後のテストの後に 1 以上（行のリンクの Tooltip）、直した後は 0 になることを件数で確かめる。誤りそのものではなく原因（片付けの後に残る予約）を決定的に示す。
- いずれも、直した後に単独の繰り返し（例: 20 回）と画面のテストの全件1回、`./gradlew verify` と CI で確かめる。

## 7. このリポジトリの側（テストの側）で確実に防ぐ直し方の案（未実施）

| 案 | 内容 | 範囲 | 手間 | 長所・短所 |
|---|---|---|---|---|
| B2 | このファイルの `afterAll`（または最後のテストの終わり）で、`act` の中で 350 ms 待って予約を jsdom が生きている間に流し切る | `BusinessDataListPage.write.test.tsx` だけ | 数行・確かめ込みで 30 分程度 | 単純で確実（外された部品への更新は何もしない）。ファイルが 0.35 秒延びる。ほかのファイル（Tooltip を押して終わるもの。DslHash・tables のほかのテスト）には効かない |
| B3 | このファイルを偽の時計（`vi.useFakeTimers({ shouldAdvanceTime: true })` と `userEvent.setup({ advanceTimers: vi.advanceTimersByTime })`）で動かし、`afterEach` で `vi.clearAllTimers()` | このファイルだけ | 中（`waitFor` と偽の時計の組み合わせの確かめが要る） | 待ち時間が無い。テストの書き方が変わり、ほかの非同期の待ちに影響しうる |
| B4（推奨） | `vitest.setup.ts` で `window.setTimeout` の予約を記録し、`afterEach` の `cleanup()` の後に残った予約をすべて `clearTimeout` する（テストの間の後片付けを1か所で決める） | 画面のテストの全ファイル | 15 行程度・確かめ込みで 1 時間程度（全件1回・verify・CI） | 時間を足さず、Tooltip に限らず「外された部品の予約が片付けの後に走る」ことをまとめて防ぐ。全テストの共通の部品を変えるため、`cleanup()` の後に予約へ頼るテストが無いことを全件で確かめる必要がある |
| A（上流、根本） | Tooltip に外されたときの後片付けを足す（4節の A） | make-you-chic-ui | 4節のとおり | 本番の振る舞いも直る。こちらの B2・B4 と併用でき、取り込んだ後も害は無い |

推奨（参考、二度目のため改める）: B4（または最小なら B2）をこのリポジトリで入れて D1 で「直す前に落ち、直した後に通る」を記録し、並行して A を上流へ依頼する。4節の「CI の再実行で進める」は、二度目の失敗により当たらなくなった。

## 8. 結果（追記）

- 直し方: 依頼者の決定（e4538e84）で **B4＋A**。
  - B4: `cac6fa54`「画面のテストの後片付けで、残った時計の予約を取り消す」（`frontend/vitest.setup.ts`。jsdom のときだけ `window.setTimeout`・`setInterval` を包み、各テストの `cleanup()` の後に未実行の予約を取り消す）。
  - A: 上流への依頼 `make-you-chic-ui-request-7.md` → 上流の `cab4207`（Tooltip に `useEffect` の後片付け、単体テスト2件）。上流の `main` から辿れること、変更が `Tooltip.tsx`・`Tooltip.test.tsx` だけであること、`package-lock.json` の差 0 行（増えたパッケージ・`hasInstallScript` の増加なし）を `git show` で確かめた（依頼者の決定 58fa4ef4）。`cb23466b` で固定先 `4c63d11bff10ac08af1269fdafa0eb115fc6b528` → `cab42074cf040e3c03127db8c92582cc329f934d`（専用のコミット）。
- 二段の確かめ（6節の手段）:
  - D1（片付けの後に `window` を外して 400 ms 待つ一時の確かめ、コミットしない）: 直す前は3回とも `ReferenceError: window is not defined`（4 errors、CI と同じスタック）、直した後は3回とも 0。
  - D2（片付けの後に鳴った予約を数える）: 直す前 4、直した後 0。
- 統合の前: 短命のブランチ `fix/261029-reference-lookup-cleanup-tooltip-timer` で `verify` 3分42秒 BUILD SUCCESSFUL（画面のテスト 238 files・2,312 passed、未処理の例外 0）、E2E 277 passed（14.1 分、Sleep・DarkWake 0）。固定先の更新を含むため `team.md` の例外で `develop` へ fast-forward（依頼者の決定 86a3562b）。
- CI: `cb23466b`（run 41270200479）の1回目で success、検査の job 28分49秒（2026-11-01T03:41:58Z〜04:10:47Z）。これで直ったことを確かめた。
- 4節の推奨（CI の再実行で進める）は、二度目の失敗により当たらなくなり、7節の推奨（B4＋A）で直した。
