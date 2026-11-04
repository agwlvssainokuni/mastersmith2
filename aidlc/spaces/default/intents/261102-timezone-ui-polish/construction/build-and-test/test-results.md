# テストの結果 — Intent 261102-timezone-ui-polish

この記録は Build and Test の1回目の実行の結果。目標の突き合わせの表（Target Verification Matrix）は、失敗の扱いが決まった後に `build-and-test-summary.md` とあわせて書き足す。

## 1回目の実行（2026-11-04、`develop` の `f2f2eb9b` と同じソース）

`caffeinate -dims` で包み、`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡して、次の2つを続けて流した。

| 命令 | 始め〜終わり | 結果 |
|---|---|---|
| `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` | 20:47:19〜21:15:01（27分41秒） | **失敗（終了コード 1）**: 画面のカバレッジの段（`frontendCoverage`）で 273 ファイル中 1 ファイル・2962 件中 1 件が落ちた。カバレッジなしの画面のテストの段（`frontendTest`）は 273 ファイル・2962 件すべて通過 |
| `./gradlew e2eTest` | 21:15:01〜21:31:00（15分58秒） | **失敗（終了コード 1）**: 298 件中 297 件通過、1 件失敗 |

### 失敗 1: `src/eslintTimeZoneRule.test.ts` の「rejects resolvedOptions().timeZone in another file」

- 結果: 5138 ms で Vitest の既定の時間の上限（5 秒）を超えた（カバレッジの計測つき・ほかのテストと並行の負荷の下）。
- 再現の試み（時間の上限 5 分）: このファイルだけを3回流し、3回とも 24 件すべて通過（全体 5.9〜6.5 秒）。並行の負荷の下でだけ落ちる。
- 見立て: このファイルの最初のテスト（`it.each` の1件目）が、ESLint の設定とプラグインの初回の読み込みの時間を負う（`beforeAll` は `new ESLint()` を作るだけで、読み込みは最初の `lintText` で起こる）。カバレッジの計測と並行の負荷が重なると 5 秒を超える。同じファイルの「the real sources」のテストは単独でも 5048 ms かかり、こちらは上限を 60 秒にしてある。
- 持ち主: U2（コミット `102608aa` で足したテスト）。

### 失敗 2: E2E `200-business-data-time-flow.e2e.ts` の手順 6

- 結果: `locator.focus: strict mode violation: getByTestId('tables-create') resolved to 2 elements`。表が空のとき、一覧の見出しの「作成」と、空の表の案内の中の「作成」の2つが同じ `data-testid` を持つ。
- 見立て: テストの書き方の誤り（流れが作った専用の表は始めは空のため、2つ目のボタンが出る）。画面の不具合ではない。手順 6 より後（存在しない時刻の誤り・重なる時刻の2択・一覧の `-05:00`・更新）は、まだ実際のブラウザで流れていない。
- 持ち主: U4（コミット `028985bc` で足した E2E）。

## 2回目の実行（繰り戻し 1 の後、2026-11-04、`fix/261102-timezone-ui-polish-bt1` の `2a6e05f7`）

U2（`30f12364`）と U4（`7f2e9001`）の直しの後、同じ形で続けて流した。流す前に `frontend/test-results` を消した。`pmset -g log` で、22:38〜23:21 に Sleep・DarkWake は無かった。

| 命令 | 始め〜終わり | 結果 |
|---|---|---|
| `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` | 22:38:10〜23:05:19（27分8秒） | **成功（終了コード 0）** |
| `./gradlew e2eTest` | 23:05:19〜23:20:58（15分38秒） | **成功（終了コード 0）**: 298 件すべて通過 |

### 件数

| 対象 | 件数 |
|---|---|
| サーバーの単体（`backend/build/test-results/test`） | 2936 件、失敗 0、飛ばし 0 |
| サーバーの結合（`backend/build/test-results/integrationTest`） | 1716 件、失敗 0、飛ばし 10（対象DB の種類ごとに当たらない場面の既存の飛ばし。MySQL・MariaDB の場面で PostgreSQL だけの確かめ、など） |
| 画面（Vitest、カバレッジなし・ありの2回） | どちらも 273 ファイル・2962 件、すべて通過 |
| E2E（Chromium） | 298 件、すべて通過。200（U4 の流れ）1 件、185（業務データの axe）43 件、130（権限が無い表示の axe）20 件を含む |

E2E のファイルごとの件数: 010: 2・020: 2・030: 1・040: 1・050: 21・060: 22・070: 20・080: 21・090: 1・100: 20・110: 1・120: 23・130: 20・135: 24・140: 25・150: 1・160: 26・170: 1・180: 1・185: 43・190: 1・195: 20・200: 1。

### カバレッジ（行 / 分岐、下限 80% / 70%）

| 範囲 | 行 | 分岐 |
|---|---|---|
| サーバー全体（JaCoCo） | 97.7% | 91.5% |
| `businessdata.domain` | 98.2% | 91.6% |
| `businessdata.service` | 95.9% | 89.1% |
| `businessdata.web` | 97.3% | 80.1% |
| `businessdata.repository` | 96.9% | 90.1% |
| `user.domain` | 99.7% | 97.6% |
| `user.service` | 99.7% | 95.9% |
| `user.web` | 96.9% | 90.5% |
| `user.repository` | 100.0% | 100.0% |
| `navigation.domain` | 100.0% | 93.3% |
| `navigation.service` | 100.0% | 100.0% |
| `navigation.web` | 96.2% | 100.0% |
| `targetdb.config` | 100.0% | 99.1% |
| `dslmanage.generate` | 99.4% | 100.0% |
| 画面全体（`@vitest/coverage-v8`） | 95.49% | 90.95% |
| `src/features/tables/` | 95.63% | 91.42% |

下限・除外は変えていない。`packagesJudgedByTotal` の一覧も変えていない。

### そのほかの確かめ

- E2E の報告の json（`frontend/test-results/e2e-results.json`）に、JWT の形（`eyJ…`）の文字は 0 件。
- 見本の PostgreSQL の E2E 用の公開の番号は、流し終えた後に上乗せ無しで作り直して閉じた（`docker port` で公開なし）。
- `verify` の時間 27分8秒は、CI の上限の7割（約 42 分）の内。

## Loop-Back Log

### Loop-back 1 — 2026-11-04T12:45:00Z

- **Diagnosis**: (1) `frontend/src/eslintTimeZoneRule.test.ts` の最初のテストが ESLint の設定とプラグインの初回の読み込みを負い、カバレッジの計測と並行の負荷の下で Vitest の既定の上限 5 秒を超えた（単独では3回とも通過）。(2) `frontend/e2e/200-business-data-time-flow.e2e.ts` の手順 6 が、表が空のときに「作成」ボタン（`data-testid="tables-create"`）が2つあることを見落とし、strict mode の誤りで止まった。
- **Root-cause stage**: code-generation（(1) は u2-display-time-zone、(2) は u4-business-data-time の生成したテスト）。
- **Planned fix**: (1) `beforeAll` で ESLint を一度空の文で動かして初回の読み込みを済ませ、その準備だけに明示の上限（60 秒）を付ける。各テストの 5 秒は緩めない。(2) 一覧の見出しの「作成」を1つに絞って押す（空の表の案内の中のボタンを選ばない）。どちらもテストだけの直しで、本体のコードは変えない。直した後に、(1) は直す前のテストが負荷の下で落ち直した後のテストが通ることを並行の負荷で確かめる（team.md の不安定なテストを直したときの二段の確かめ）。(2) は 200 を単独で流して通ることを確かめる。
- **Estimated impact**: 手間 小（2ファイル・数行）、費用 なし、危険 小（テストだけ）。U2・U4 のコード生成のレビューと確かめの場のやり直し、`verify` と E2E の流し直し（約 45 分）。依頼者の選択: Retry with fix（2026-11-04）。
