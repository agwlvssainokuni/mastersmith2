# Construction → Operation の確かめ — business-data-views

**判定: 条件つき合格（conditional）** — 5つの単位がすべて作られテストされ、コード生成の `traceability.json` に GAP・ORPHAN は無く、単位をまたぐ網羅で覆われていない ID は 0 件。CI は Build and Test のコマンドを実行し、`31d434c` まで success（`b7291d3` 以降は1回目の試行で success）。ただし後の段（Performance Validation・Observability Setup・次の Intent）への `Deferred` と、Build and Test の Unverified 16 件が、Operation の段の持ち物として残る。

確かめた日: 2026-10-21。CI Pipeline の段の Step 5 として、`construction/build-and-test/cross-unit-traceability.md` と、全単位の `construction/<単位>/code-generation/traceability.json`（5 つ）を読み、Build and Test の記録と CI の結果に照らしました（パスは `aidlc/spaces/default/intents/261016-business-data-views/` からの相対）。

## 1. 全単位が作られテストされたこと

| 単位 | Bolt | 統合のコミット（`develop`） | テスト |
|---|---|---|---|
| U1 u1-businessdata-read | B2 | `ea23bf3` | Build and Test の直しの後の clean 付きの `verify`（単体 2,589・結合 1,389・画面 1,975、失敗 0、SKIPPED 9 はどれも DB の種類の `assumeTrue`）と、B7 のレビューの直しの後の E2E 256 件がすべて通過（`build-and-test/test-results.md` の 2節・8.4） |
| U4 u4-a11y-carryover（前半） | B3 | `e34c260`（make-you-chic-ui の固定先 `b0a1e79`）〜`1e78b51` | 同上 |
| U5 u5-carryover-tests | B1 | `2360768` | 同上（`dslMixed` の `memory.peak` は Build and Test で既定の2回 0.8731・0.8696 で Met） |
| U2 u2-businessdata-write | B4 | `04b0589` | 同上 |
| U3 u3-businessdata-screens | B5・B6・B7 | `1152202`・`a031ba1`・`d220447` | 同上 |
| U4 u4-a11y-carryover（後半） | B7（U3 の計画の最後の Bolt） | `d220447` | 同上（axe-core 4.14.0、E2E と vitest-axe の違反 0 件） |
| CI の不安定なテスト2件の直し | — | `b7291d3` | 同上（二段の確かめ、5回の繰り返し） |
| Build and Test の直し | — | `22de65f` | 同上（直した後の `verify` 22分26秒で通過） |

- 5 単位すべてに `code-generation/` の成果物（計画・まとめ・`traceability.json`・`unit-test-instructions.md`）がそろっています。段の `construction/code-generation/` には `memory.md` と `generation-notes.md` があり、`traceability.json` は単位ごとです。
- カバレッジは下限（行 80%・分岐 70%）を全体とパッケージごとに満たしています（バックエンド 行 97.64%・分岐 91.23%、画面 行 95.06%・分岐 89.80%。`packagesJudgedByTotal` は 7 のまま、除外は増やしていない）。

## 2. コード生成の `traceability.json`

| 単位 | 上流の ID の数 | OK | Deferred | N/A | GAP | ORPHAN |
|---|---|---|---|---|---|---|
| u1-businessdata-read | 187 | 165 | 22 | 0 | 0 | 0 |
| u2-businessdata-write | 170 | 150 | 20 | 0 | 0 | 0 |
| u3-businessdata-screens | 181 | 125 | 44 | 12 | 0 | 0 |
| u4-a11y-carryover | 29 | 17 | 9 | 3 | 0 | 0 |
| u5-carryover-tests | 27 | 23 | 4 | 0 | 0 | 0 |
| 合計 | 594 | 480 | 99 | 15 | **0** | **0** |

- 5 つのファイルはすべてあります（抜けなし）。`GAP`・`ORPHAN` は 0 件、`reverse` の行はありません。
- `Deferred` 99 件の行き先（target の書き出しで数えた）:

| 行き先 | 件数 | 扱い |
|---|---|---|
| ほかの単位（U1・U2・U3） | 72 | その単位の `traceability.json` で `OK`（`cross-unit-traceability.md` の 7・8節で連鎖をたどった） |
| Build and Test | 9（U3 3・U4 2・U5 4） | Build and Test で確かめて Met（`cross-unit-traceability.md` の 1節・6節。承認済みの `traceability.json` は `Deferred` のまま） |
| Performance Validation | 12（U1 6・U2 6） | この Intent の流れで EXECUTE（`aidlc-state.md`） |
| Observability Setup | 4（U1 2・U2 2） | 同上 |
| 次の Intent | 2（U3。AC4.1.2・AC4.1.4 の make-you-chic-ui の依頼（5）の分） | 依頼（5）が上流の `main` に無く、固定先の2回目の更新をしなかった。Build and Test の既知の制約として承認済み |

- 行き先の無い `Deferred` はありません。
- 未解決の指摘: 各単位の `code-summary.md` の「依頼者に確かめたいこと」とコードのレビューの指摘は、Code Generation の承認（`288736d`）と Build and Test の承認（`31d434c`）の場で、直したもの（Build and Test の直し `22de65f` を含む）と、依頼者の決定で受け入れた既知の制約（`build-and-test-summary.md` の 5節）に振り分けられています。Construction の中で扱いの決まっていない指摘はありません。U1・U2 の `traceability.json` の target のずれと AC3.4.5 の行の無さは、依頼者の決定で書き換えず、正しい根拠を `cross-unit-traceability.md` の 4・5節に書いています。

## 3. 要件・受け入れ基準の網羅（単位をまたぐ）

`construction/build-and-test/cross-unit-traceability.md` の判定は **合格（条件つき）** です。

| 種類 | 数 | Covered | Covered（一部 Deferred） | Deferred のみ | 覆われていない |
|---|---|---|---|---|---|
| 要件の FR（枝番を含む） | 85 | 79 | 6（FR4・FR4.1〜FR4.4・FR14.5。どれも AC1.3.7） | 0 | 0 |
| 要件の NFR（NFR1〜NFR8 と NFR4.1〜NFR4.4） | 12 | 8 | 4（NFR1・NFR2・NFR3・NFR8） | 0 | 0 |
| ストーリーの受け入れ基準 | 146 | 145 | — | 1（AC1.3.7、Performance Validation） | 0 |

- 前の版で覆われていなかった AC3.4.5（読み取りとその拒否は監査に残らない）は、Build and Test の直しで足した結合テスト `AbstractBusinessDataReadIT#readsLeaveNoAudit`（3種類の対象DB、各 17 件が通過）で覆われました。
- 一部 Deferred の FR・NFR と AC1.3.7 は、Construction の中では測れない性能（業務データの API の p95・プールの見積もり・3種類の DB での測り）と監視（警報のしきい値・ダッシュボードの区画）で、持ち主の段がこの Intent の流れで EXECUTE です。目標は緩めていません。

## 4. CI の関門が Build and Test のコマンドを実行していること

| Build and Test のコマンド | CI での実行 |
|---|---|
| `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（0〜9 の段: フォーマット・リンタ・ライセンスヘッダー・ビルド・単体・結合（対象DB の3種類）・カバレッジの下限・SpotBugs・OSV-Scanner・Gitleaks・WAR と JSON Schema） | **実行する**。ジョブ `./gradlew verify` が同じ `./gradlew verify` を呼ぶ（まっさらな環境のため clean なしでもテストは必ず走る） |
| `./gradlew osvScan --rerun` | **実行する**（CI では `osvScan` が毎回走る） |
| `./gradlew e2eTest`（256 件） | 意図して CI の外（`team.md` の Testing Posture）。対象のブランチの統合の前と Build and Test・リリースの前に手元で実行 |
| k6 の場面・`dslMixed` の `memory.peak` | 意図して CI の外。Performance Validation と Build and Test の使い捨ての環境 |

対応の全体は `construction/ci-pipeline/quality-gates.md` の 4節・5節にあります。統合の関門に当たる目標（`TC-01`〜`TC-05`・`TC-08`・`TC-11`・`TC-17`・`TC-18`）は、どれも CI の `./gradlew verify` で実行されます。

CI の結果（`construction/ci-pipeline/ci-config.md` の 6節）:

- B1〜B7 の統合を含む push の CI は、`1152202`（B5）を除き success でした。`0427d45`・`ea23bf3`・`05a9b37` は1回目が1件ずつ落ち、再実行の2回目で success でした。`1152202` は3回の試行とも落ち、同じテスト2件（`PasswordChangePage.test.tsx`・`H2CompactionByPoolSuspensionIT`）を `b7291d3` で原因から直しました。その後の `b7291d3`・`a031ba1`・`d220447` は1回目で success です。
- `31d434c`（Build and Test の承認、`288736d`・`22de65f` を含む）の CI（run 39465005577）: **success**（1回目、ジョブ `./gradlew verify` は 2026-10-20T17:19:06Z〜17:41:48Z の 22分42秒）。Build and Test が「push の後に確かめる」とした2つのコミットの CI は満たした。
- 記録の食い違いが1件あります（`05a9b37` の CI の1回目の `PasswordChangePage.test.tsx` の失敗が、Build and Test とコード生成の記録に無い。`ci-config.md` の 6.2）。同じテストは `b7291d3` で直し、その後のどの試行でも落ちていないため、判定は変わらないと見ます。直すかどうかを依頼者に確かめます。

## 5. 判定

**条件つき合格（conditional）**。Construction から Operation へ進んでよいと判断します。

合格とする根拠:
- 5 単位すべてが Bolt B1〜B7 で `develop` に統合され、clean 付きの `verify`（単体・結合・画面のテスト、カバレッジの下限、静的な検査）と E2E 256 件が通っている（1節）。
- コード生成の `traceability.json` は 5 つともそろい、GAP・ORPHAN は 0 件。`Deferred` はすべて行き先がある（2節）。
- 単位をまたぐ網羅で、覆われていない FR・NFR・AC は 0 件（3節）。
- CI は Build and Test の関門のコマンド（`./gradlew verify`）をそのまま実行し、`31d434c` の CI まで success（4節）。

条件（fail にしない理由と、Operation で果たすこと）:
- Build and Test の Target Verification Matrix の **Unverified 16 件**（Performance Validation 12: U1-NFR1.1・U1-NFR1.3・U1-NFR2.1・U1-NFR2.4・U1-NFR2.5b・U1-NFR3.2・U2-NFR1.4・U2-NFR1.5・U2-NFR2.7・U2-NFR2.8・U2-NFR2.9・U2-NFR3.4、Observability Setup 4: U1-NFR8.1・U1-NFR8.3・U2-NFR8.5・U2-NFR8.6）は、配備した環境に近い使い捨ての負荷の環境か監視の環境が要る目標です。依頼者は Build and Test の承認の場（2026-10-20T17:18:23Z の Approve。決定は `build-and-test/memory.md`）で、持ち主の段で判定することを受け入れています。持ち主の段はどちらもこの Intent の流れで EXECUTE で、目標は緩めていません。Performance Validation では、U1 の `bizSearchContains`（10 VU で 1 秒を超える見込み）と `bizSearchLastSorted`（MySQL・MariaDB）が Not Met になりうると Build and Test が書いています。
- 後の段への `Deferred`（Performance Validation 12・Observability Setup 4。AC1.3.7 と NFR1・NFR2・NFR3・NFR8 の一部）は、扱いが決まっていて持ち主の段があるため、段の定義の「unresolved finding」には当たらないと読みました。
- 次の Intent への `Deferred` 2 件（AC4.1.2・AC4.1.4 の依頼（5）の分）は、Build and Test の承認で既知の制約として受け入れられています。
- 記録の食い違い1件（4節）は判定を変えないが、扱いを依頼者に確かめる。

pass にしない理由: 段の定義の失敗の述語で、Build and Test は Unverified が残る間は成功の結果にならず、FR・NFR の一部と AC1.3.7 の確かめが後の段に残るため。

## 承認

- [ ] 依頼者の確かめ（CI Pipeline の承認の場で行う）

## Sources

- `construction/build-and-test/cross-unit-traceability.md`・`build-and-test-summary.md`（Target Verification Matrix・5〜8節）・`test-results.md`・`memory.md`
- `construction/u1-businessdata-read/code-generation/traceability.json`・`construction/u2-businessdata-write/…`・`construction/u3-businessdata-screens/…`・`construction/u4-a11y-carryover/…`・`construction/u5-carryover-tests/code-generation/traceability.json`（status の集計と `Deferred` の行き先を読み取りで数えた）
- 各単位の `code-generation/code-summary.md`、`construction/code-generation/memory.md`・`generation-notes.md`
- `aidlc-state.md`（後の段の EXECUTE）、`git log --first-parent`、`git log -1 31d434c`・`288736d`、`audit/` の Build and Test の `GATE_APPROVED`（読み取り）
- `construction/ci-pipeline/ci-config.md`・`quality-gates.md`、`gh run view`・`gh api …/attempts/<n>/jobs`・`gh run view 39261104974 --attempt 1 --log-failed`（読み取り）
- `aidlc/spaces/default/memory/team.md`（Testing Posture・Way of Working）・`project.md`（Way of Working の網羅のたどり方・Testing Posture・Deployment・Change Control）

## Assumptions & Open Questions

- 2節の `Deferred` の行き先は、target の書き出し（単位の名前・段の名前）で数えました。
- 5節の Unverified 16 件の持ち越しは、Build and Test の承認で受け入れられたものとして扱いました（承認の操作の記録は `Approve` で、決定の文は `build-and-test/memory.md` の Open questions にあります）。
