# Construction → Operation の確かめ — reference-lookup-cleanup

**判定（更新 2026-11-01）: 条件つき合格（conditional）。保留は解けた** — 下の当初の判定の保留（`e1e028d1` の CI の未処理の例外）は、2回目も同じ誤りで落ちたため `team.md` の決まりで直すまで進めず、依頼者の決定で直した（`cac6fa54` の `vitest.setup.ts` の片付けと、上流の Tooltip の直しの取り込み `cb23466b`、固定先 `cab4207`）。二段の確かめ（D1・D2）を記録し、統合の前の verify と E2E 277 件を通し、`cb23466b` の CI（run 41270200479）が1回目で success（検査の job 28分49秒）。条件つきの理由（後の段の Unverified 17 件・追跡の記録の抜け2件）は変わらない。4・5節の追記を見る。

以下は当初の判定（消さずに残す）。

**判定: 条件つき合格（conditional）。ただし1点の保留あり** — 5つの単位がすべて作られテストされ、コード生成の `traceability.json` に GAP・ORPHAN は無く、Build and Test の目標の判定は Not Met 0 件です。CI は Build and Test のコマンドを実行し、`df79634c`（Build and Test の直し）まで success（再実行で通ったのは `a75baec4`・`cf887ad8` の2つで、扱い済み）。

**保留**: その後の記録だけの push `e1e028d1` の CI（run 41264314230）の1回目が、画面のテストの未処理の例外（`ReferenceError: window is not defined`、`BusinessDataListPage.write.test.tsx` の実行中）で failure でした。2回目は確認中です。`team.md` の「不安定なテストと CI の失敗」の決まりでの扱いを依頼者が決め、2回目の結果を確かめるまで、この保留は解けません（4節）。

ほかに、後の段（Performance Validation・Observability Setup）への Unverified 17 件と、追跡の記録の抜け2件（AC3.1.7・AC3.4.5）が残ります。どちらも Build and Test の承認の場で受け入れられています。

確かめた日: 2026-11-01。CI Pipeline の段の Step 5 として、`construction/build-and-test/cross-unit-traceability.md` と、全単位の `construction/<単位>/code-generation/traceability.json`（5 つ）を読み、Build and Test の記録と CI の結果に照らしました（パスは `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/` からの相対）。

## 1. 全単位が作られテストされたこと

| 単位 | Bolt | 統合のコミット（`develop`） | テスト |
|---|---|---|---|
| U1 u1-reference-read | B1・B2 | `549d38b6`・`b048ec15` | Build and Test の clean 付きの `verify`（fix ブランチ、`df79634c` と同じアプリのソース。単体 2,807・結合 1,654・画面 2,312、失敗 0、SKIPPED 10 はどれも DB の種類ごとの `assumeTrue`）と、E2E 277 件がすべて通過（`build-and-test/test-results.md` 2・3節） |
| U2 u2-reference-write-check | B3 | `f35b7b88` | 同上 |
| U3 u3-reference-screens | B4 | `711ddd07` | 同上（E2E 190・195 を含む） |
| U4 u4-perf-evidence-redactor | B5 | `13883f6c` | 同上（道具のテスト 115 件、`perfEvidenceScan` の通過、H1 で関門が落ちることの確かめ） |
| U5 u5-container-perf-docs | B6 | `c91d831d` | 同上 |
| Code Generation の承認の場の直し | B7 | `6412122c` | 同上 |
| 不安定なテストの直し（`ReferenceSameResponseIT`） | — | `a3099f80` | 二段の確かめ（`test-results.md` 8節） |
| Build and Test の直し（U2 R-02・U3 R-03・U4 R-01） | — | `df79634c` | 同上（直した後の `verify` 26分4秒で通過） |

- 5 単位すべてに `code-generation/` の成果物（計画・まとめ・`traceability.json`）がそろっています。
- カバレッジは下限（行 80%・分岐 70%）を全体とパッケージごとに満たしています（バックエンド 行 97.6%・分岐 91.3%、画面 行 95.21%・分岐 90.37%。`packagesJudgedByTotal` は 7 のまま、除外は増やしていない）。

## 2. コード生成の `traceability.json`

| 単位 | 上流の ID の数 | OK | Deferred | N/A | GAP | ORPHAN |
|---|---|---|---|---|---|---|
| u1-reference-read | 173 | 156 | 16 | 1 | 0 | 0 |
| u2-reference-write-check | 90 | 80 | 10 | 0 | 0 | 0 |
| u3-reference-screens | 77 | 69 | 7 | 1 | 0 | 0 |
| u4-perf-evidence-redactor | 55 | 50 | 5 | 0 | 0 | 0 |
| u5-container-perf-docs | 22 | 22 | 0 | 0 | 0 | 0 |
| 合計 | 417 | 377 | 38 | 2 | **0** | **0** |

- 5 つのファイルはすべてあります（抜けなし）。`GAP`・`ORPHAN` は 0 件です。
- `Deferred` 38 件の行き先（target の書き出しで数えた）:

| 行き先 | 件数 | 扱い |
|---|---|---|
| ほかの単位（U1 → U3 8、U2 → U3 2、U3 → U1 5、U3 → U2 1） | 16 | 多くはその単位の `traceability.json` で `OK`。**ただし AC3.1.7（U2 → U3）と AC3.4.5（U1 → U3）は U3 の `traceability.json` に行が無く、追跡の連鎖が途切れる**（3節） |
| Build and Test | 12（U1 2・U2 4・U3 1・U4 5） | Build and Test で確かめて Met（U2 の NFR2.23・2.24 は Build and Test の分が済み、比べと判定は Performance Validation）。承認済みの `traceability.json` は `Deferred` のまま |
| Performance Validation | 6（U1 4・U2 2） | この Intent の流れで EXECUTE（`aidlc-state.md`） |
| Observability Setup | 4（U1 2・U2 2） | 同上 |

- 行き先の無い `Deferred` はありません。
- 未解決の指摘: 各単位の `code-summary.md` の「依頼者に確かめたいこと」とコードのレビューの指摘は、Code Generation の承認（`b398ed5f`、直しの Bolt B7 を含む）と Build and Test の承認（`e1e028d1`、直し `df79634c` を含む）の場で、直したものと、依頼者の決定で受け入れた既知の制約（`build-and-test-summary.md` 5節: U4 R-02・R-03、`stage` の無い WARN の経路、MySQL の 1038）に振り分けられています。Construction の中で扱いの決まっていない指摘はありません（CI の保留は 4節）。

## 3. 要件・受け入れ基準の網羅（単位をまたぐ）

`construction/build-and-test/cross-unit-traceability.md` のセンサーの判定は **fail**（追跡した 167 の ID のうち covered 148・uncovered 19）でした。Build and Test は uncovered の 19 を次のように分けています。

| 区分 | ID | 扱い |
|---|---|---|
| Build and Test で確かめ済み（記録の上だけ Deferred が残る） | FR5.1〜FR5.5・AC4.1.6、NFR5、NFR2 の一部（U1 NFR2.4・U2 NFR2.22・U3 NFR2.45）、NFR8 の一部（U4 NFR8.69） | Met |
| 作って確かめ済みだが、追跡の連鎖が途切れている | FR3.1〜FR3.4・AC3.1.7、FR4.1・AC3.4.5 | U3 に実装とテストがある（`LookupField.test.tsx`・`BusinessDataFormPage.test.tsx`・`BusinessDataListPage.test.tsx` ほか、E2E 180・195）。**記録の抜け**で、作られていないのではない。承認済みの記録は書き換えていない |
| 後の段が持ち主 | FR1.5・FR3.4（AC3.5.4）、NFR2 の性能の分、NFR3 の監視の分 | Performance Validation・Observability Setup |
| Build and Test の持ち主で未確かめだったもの | U4 NFR8.64（H1） | Build and Test の段で後から実施して Met（`test-results.md` 13節） |

Build and Test の目標の判定表は 195 行: **Met 176・Not Met 0・Unverified 18・N/A 1** です。Unverified 18 件の持ち主は、Performance Validation 11 件（BT-K6-JUDGE・BT-MEMPEAK-JUDGE・AC3.5.4・U1 NFR2.1・2.3・2.5・U2 NFR2.21・2.23・2.24・2.25・REQ-NFR2）、Observability Setup 6 件（OBS-PANELS・U1 NFR2.10・3.8・U2 NFR2.29・3.28・REQ-NFR3。REQ-NFR2 は両方）、Build and Test 1 件（BT-K6-INSPECT。承認の場で「変えた6場面の inspect と 78 場面の閾値の台本の照合」で足りるとして Approve）です。目標は緩めていません。N/A の BT-CI-MERGE はこの段で受け持ちました（`construction/ci-pipeline/quality-gates.md` 0.1）。

## 4. CI の関門が Build and Test のコマンドを実行していること

| Build and Test のコマンド | CI での実行 |
|---|---|
| `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（0〜9 の段: フォーマット（perf の道具を含む）・リンタ（同）・ライセンスヘッダー（`perf/` を含む）・ビルド・単体・結合（対象DB の3種類）・カバレッジの下限・SpotBugs・OSV-Scanner・Gitleaks・`perfEvidenceScan`・WAR と JSON Schema） | **実行する**。ジョブ `./gradlew verify` が同じ `./gradlew verify` を呼ぶ（まっさらな環境のため clean なしでもテストは必ず走る） |
| `./gradlew osvScan --rerun` | **実行する**（CI では `osvScan` が毎回走る） |
| H1（関門をわざと落とす1回） | 意図して CI の外（1回きりの確かめ） |
| `./gradlew e2eTest`（277 件） | 意図して CI の外（`team.md` の Testing Posture）。対象のブランチの統合の前と Build and Test・リリースの前に手元で実行 |
| k6 の場面（`k6 inspect`・試し走り） | 意図して CI の外。Performance Validation の使い捨ての環境 |

対応の全体は `construction/ci-pipeline/quality-gates.md` 4・5節にあります。統合の前の関門は手元の `verify` で、CI は統合の後に同じタスクで再確認します（BT-CI-MERGE と org の既定との差は同 0.1）。

CI の結果（`construction/ci-pipeline/ci-config.md` 6節）:

- 前の `main` の取り込みの後の `df79634c` までの 34 の run: `a75baec4` の1回目（`ReferenceSameResponseIT`、`a3099f80` で原因から直し二段で確かめた）と `cf887ad8` の1回目（cancelled、job 0 件）を除き、1回目で success。検査の job の最大は 37分45秒で、42 分の線の内。同じテストの二度目の失敗は無い。
- **`e1e028d1`（run 41264314230）**: 1回目 **failure**（2026-11-01T02:13:59Z〜02:19:32Z）。画面のテストは 238 ファイル・2,312 件がすべて passed だが、make-you-chic-ui の `Tooltip` の遅らせた表示の時計から出た未処理の例外 `ReferenceError: window is not defined`（`BusinessDataListPage.write.test.tsx` の実行中）を Vitest が数え、`:frontendTest` が失敗した。`df79634c..e1e028d1` は `aidlc/` の下だけの変更で、同じテストのコードの `df79634c` の CI は success だった。2回目（02:20:49Z〜）は**確認中**。
- **追記（結果）**: 2回目も **failure**（02:20:49Z〜02:56:46Z、`:frontendCoverage`、238 files・2,312 passed、Unhandled Errors 1、同じ `ReferenceError`・同じ Tooltip の時計・同じテストのファイル）。同じ誤りの二度目のため、`team.md` の決まりで直すまで進めないと判断した。依頼者の決定（9594abae）で 30 分の再現の試みを行い、手元では再現せず、仕組みはソースで確かめた（`construction/ci-pipeline/flaky-repro-record.md`）。依頼者の決定（e4538e84）B4＋A で、`cac6fa54`（`frontend/vitest.setup.ts`: jsdom のときだけ `window.setTimeout`・`setInterval` を包み、各テストの `cleanup()` の後に未実行の予約を取り消す）と、上流への依頼（`make-you-chic-ui-request-7.md`）の回答 `cab4207` の取り込み `cb23466b`（固定先 `4c63d11` → `cab4207`、専用のコミット。変更は `Tooltip.tsx`・`Tooltip.test.tsx` だけ、`package-lock.json` の差 0 行。依頼者の決定 58fa4ef4）を入れた。二段の確かめ: D1 で直す前は3回とも `ReferenceError`（4 errors、CI と同じスタック）、直した後は3回とも 0。D2 で片付けの後に鳴った予約が直す前 4・直した後 0。短命のブランチで `verify`（3分42秒、未処理の例外 0）と E2E 277 passed（14.1 分）を通し、`develop` へ fast-forward（依頼者の決定 86a3562b）。**`cb23466b` の CI（run 41270200479）は1回目で success**（03:41:58Z〜04:10:47Z、28分49秒）。`construction/ci-pipeline/ci-config.md` 6.4。

## 5. 判定

**条件つき合格（conditional）、保留1点**。

- 進んでよい条件: `e1e028d1` の CI の1回目の失敗を、`team.md` の「不安定なテストと CI の失敗」の決まりで依頼者が扱いを決めること（手元で再現しないなら、試みの上限と見立てを記録し、2回目が success なら「不安定と確かめられていない」扱いで進める。同じ形で二度目に落ちたら原因を直すまで進まない）。2回目の結果はこの記録に書き足す。
- 条件つきの理由: Operation の段の持ち物として、Performance Validation と Observability Setup が持ち主の Unverified 17 件が残ります。どちらもこの Intent の流れで EXECUTE で、目標は緩めていません。
- 追跡の記録の抜け2件（AC3.1.7・AC3.4.5）は、実装とテストが U3 にあることを Build and Test が確かめ、承認の場で受け入れられています。承認済みの記録は書き換えません。

### 5.1 判定の更新（追記、2026-11-01）

**条件つき合格（conditional）。保留は解けた**。上の「進んでよい条件」は、依頼者が扱いを決め（二度目のため直す）、直して（`cac6fa54`・`cb23466b`）二段の確かめを記録し、`cb23466b` の CI の1回目で success を確かめたことで満たした。Construction から Operation へ進んでよいと判断します。条件つきの理由（Performance Validation・Observability Setup が持ち主の Unverified 17 件、追跡の記録の抜け2件）は変わりません。

## Sources

- `construction/build-and-test/cross-unit-traceability.md`・`build-and-test-summary.md`（1・4・5節、Target Verification Matrix）・`test-results.md`（2〜8・12・13節）
- `construction/u1-reference-read/code-generation/traceability.json`・`construction/u2-reference-write-check/code-generation/traceability.json`・`construction/u3-reference-screens/code-generation/traceability.json`・`construction/u4-perf-evidence-redactor/code-generation/traceability.json`・`construction/u5-container-perf-docs/code-generation/traceability.json`（status と target を数えた）
- 各単位の `code-generation/code-summary.md`
- `construction/ci-pipeline/ci-config.md`・`quality-gates.md`
- `git log -1` で各 Bolt のコミットの件名、`git diff --stat df79634c..e1e028d1`（読み取り）
- `gh run view 41264314230 --attempt 1 --log-failed`・`gh api …/runs/41264314230/attempts/1/jobs`・`…/runs/41264314230/jobs`（読み取り）
- `audit/sakura-local-4e42a93f87ce.md`（Build and Test の承認の場の問いと答え）
- 追記の根拠: `gh api …/runs/41264314230/attempts/2/jobs`・`gh run view 41270200479`（読み取り）、`git show --stat cac6fa54`・`git show --stat cb23466b`、`construction/ci-pipeline/flaky-repro-record.md`・`make-you-chic-ui-request-7.md`
