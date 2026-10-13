# Construction → Operation の確かめ — role-menu

**判定: 条件つき合格（conditional）** — 全単位が作られテストされ、コード生成の `traceability.json` に GAP・ORPHAN は無く、CI は Build and Test のコマンドを実行して `005730a` まで success。ただし受け入れ基準の未網羅 5 件と Unverified 24 件が、Operation の段の持ち物として残る。

確かめた日: 2026-10-14。CI Pipeline の段の Step 5 として、`construction/build-and-test/cross-unit-traceability.md` と、全単位の `construction/<単位>/code-generation/traceability.json`（7 つ）を読み、Build and Test の記録と CI の結果に照らしました（パスは `aidlc/spaces/default/intents/261004-role-menu/` からの相対）。

## 1. 全単位が作られテストされたこと

| 単位 | Bolt | 統合のコミット（`develop`） | テスト |
|---|---|---|---|
| U1 cross-cutting | B2 | `65a76c5` | Build and Test の clean 付きの `verify`（単体 2,102・結合 1,165・画面 1,581、失敗 0・飛ばし 0）と E2E 230 件がすべて通過（`build-and-test/test-results.md` の 2節） |
| U2 dsl-v2 | B1 | `44677cc` | 同上 |
| U3 group | B3 | `f9d1134` | 同上 |
| U4 role | B4・B5・B6 | `3d4f344`・`b1c2760`・`fcc25b5` | 同上 |
| U5 navigation | B7 | `3a30982` | 同上 |
| U6 role-admin-ui | B8 | `ea55919` | 同上 |
| U7 app-frame-ui | B9 | `e911e59`（make-you-chic-ui の固定先）・`0415b94` | 同上 |
| Build and Test の直し | — | `005730a` | 同上（直した後の3回目の `verify` で通過） |

- 7 単位すべてに `code-generation/` の成果物（計画・まとめ・`traceability.json`・`unit-test-instructions.md`）がそろっています。段の `construction/code-generation/` には `memory.md` だけがあり、`traceability.json` は単位ごとです。
- カバレッジは下限（行 80%・分岐 70%）を全体とパッケージごとに満たしています（バックエンド 行 97.9%・分岐 92.5%、画面 行 95.42%・分岐 90.31%）。

## 2. コード生成の `traceability.json`

| 単位 | 上流の ID の数 | OK | Deferred | N/A | GAP | ORPHAN |
|---|---|---|---|---|---|---|
| cross-cutting | 110 | 69 | 36 | 5 | 0 | 0 |
| dsl-v2 | 129 | 76 | 53 | 0 | 0 | 0 |
| group | 118 | 94 | 15 | 9 | 0 | 0 |
| role | 263 | 212 | 47 | 4 | 0 | 0 |
| navigation | 90 | 69 | 8 | 13 | 0 | 0 |
| role-admin-ui | 162 | 125 | 37 | 0 | 0 | 0 |
| app-frame-ui | 112 | 99 | 13 | 0 | 0 | 0 |
| 合計 | 984 | 744 | 209 | 31 | **0** | **0** |

- 7 つのファイルはすべてあります（抜けなし）。`GAP`・`ORPHAN` は 0 件です。
- `Deferred` 209 件の行き先は、ほかの単位（188 件）か、この Intent の流れで EXECUTE の段（performance-validation・observability-setup・deployment-execution・build-and-test、21 件）です（`aidlc-state.md`）。行き先の無い `Deferred` はありません。
- 未解決の指摘: コード生成の段の読み直しの指摘は、Build and Test で直したもの（`build-and-test-summary.md` の 5.3）と、依頼者の決定で受け入れた制約・後の段か後の Intent への引き継ぎ（同 5.4）に振り分けられ、Build and Test の承認（`ae794e4`、2026-10-13）で確定しています。Construction の中で扱いの決まっていない指摘はありません。

## 3. 要件・受け入れ基準の網羅（単位をまたぐ）

`construction/build-and-test/cross-unit-traceability.md` の判定は **不合格（条件つき）** です。

| 種類 | 数 | 網羅 | 未網羅 |
|---|---|---|---|
| FR | 52 | 52（FR12.4 は連鎖に載らず、Build and Test で `rules.md` を読んで判定） | 0 |
| NFR | 21 | 21（時間の目標そのものは各単位で performance-validation へ `Deferred`） | 0 |
| AC | 149 | 144 | 5 |

未網羅の 5 件は、どれもテストの書き忘れではなく、Construction では測れない性能と配備の確かめです。

| ID | 中身（要約） | 持ち主の段 |
|---|---|---|
| AC1.2.18 | 上限の規模でロールの木の API の 95 パーセンタイルが 1 秒以内 | performance-validation |
| AC3.1.17 | 上限ちょうどの権限の YAML の確かめと適用の時間（上限は Build and Test で 5 MiB・節 500,000 に下げた） | performance-validation |
| AC4.1.20 | 作業ロールの切り替えの API の 95 パーセンタイル | performance-validation |
| AC5.1.18 | 業務のメニューの API の 95 パーセンタイル | performance-validation |
| AC6.1.8 | 版 2 の DSL を適用した環境で前の版へ戻したときの起動と既定の DSL の生成し直し | deployment-pipeline・deployment-execution |

依頼者は Build and Test の承認（`ae794e4`「Unverified 25 件は後の段へ」）で、この 5 件と Target Verification Matrix の Unverified 25 件（performance-validation 20・observability-setup 2・deployment-execution 2・ci-pipeline 1）を後の段の持ち物として受け入れています。持ち主の段はすべてこの Intent の流れで EXECUTE です。

## 4. CI の関門が Build and Test のコマンドを実行していること

| Build and Test のコマンド | CI での実行 |
|---|---|
| `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（0〜9 の段: フォーマット・リンタ・ライセンスヘッダー・ビルド・単体・結合（対象DB の3種類）・カバレッジの下限・SpotBugs・OSV-Scanner・Gitleaks・WAR と JSON Schema v2） | **実行する**。ジョブ `./gradlew verify` が同じ `./gradlew verify` を呼ぶ（まっさらな環境のため clean なしでもテストは必ず走る） |
| `./gradlew osvScan --rerun` | **実行する**（CI では `osvScan` が毎回走る） |
| `./gradlew e2eTest`（010〜170、230 件） | 意図して CI の外（`team.md` の Testing Posture）。統合の前とリリースの前に手元で実行 |
| k6 の場面・権限の YAML の上限の測り | 意図して CI の外。performance-validation と Build and Test の使い捨ての環境 |

対応の全体は `construction/ci-pipeline/quality-gates.md` の 4節・5節にあります。統合の関門に当たる目標（`TC-1`〜`TC-5`・`SEC-1`・`SEC-2`・`ART-1`）は、どれも CI の `./gradlew verify` で実行されます。

CI の結果（`construction/ci-pipeline/ci-config.md` の 6節）:

- B1〜B9 の統合を含む push の CI は、すべて success でした。`0415b94` の CI（run 38398376712）は1回目が `ShellLayout.test.tsx` の1件で落ち、再実行の2回目で success でした。同じテストは Build and Test で原因を直しました（`005730a`）。
- `005730a`（Build and Test の直し）の CI（run 38432556919、`TC-7b`）: **success**（1回目、2026-10-13T16:41:22Z〜16:57:59Z、ジョブ `./gradlew verify` は success）。`TC-7b` は Met になり、Build and Test から CI Pipeline への持ち越しは残っていません。

## 5. 判定

**条件つき合格（conditional）**。Construction から Operation へ進んでよいと判断します。

合格とする根拠:
- 7 単位すべてが Bolt B1〜B9 で `develop` に統合され、clean 付きの `verify`（単体・結合・画面のテスト、カバレッジの下限、静的な検査）と E2E 230 件が通っている（1節）。
- コード生成の `traceability.json` は 7 つともそろい、GAP・ORPHAN は 0 件。`Deferred` はすべて行き先がある（2節）。
- FR 52・NFR 21 はすべて網羅（3節）。
- CI は Build and Test の関門のコマンド（`./gradlew verify`）をそのまま実行し、`005730a` の CI まで success（4節）。

条件（fail にしない理由と、Operation で果たすこと）:
- AC の未網羅 5 件（AC1.2.18・AC3.1.17・AC4.1.20・AC5.1.18・AC6.1.8）は、Construction の中では測れない性能と配備の確かめで、持ち主の段（performance-validation・deployment-pipeline・deployment-execution）がこの Intent の流れにあり、依頼者が Build and Test の承認で持ち越しを受け入れている。段の定義の「unresolved finding」には当たらないと読んだ（扱いが決まっていて持ち主がある）。
- Target Verification Matrix の Unverified は、`TC-7b` がこの段で Met になり 24 件（performance-validation 20・observability-setup 2・deployment-execution 2）が残る。
- 記録の食い違いが1件ある（Build and Test の記録が run 38398376712 の1回目の失敗に触れていない。`ci-config.md` の 6.2）。判定は変わらないが、直すかどうかを依頼者に確かめる。

pass にしない理由: 段の定義の「cross-Unit FR/NFR/AC gate passed」を、AC の未網羅 5 件が残る状態で「合格」とは言えないため。

## 承認

- [ ] 依頼者の確かめ（CI Pipeline の承認の場で行う）

## Sources

- `construction/build-and-test/cross-unit-traceability.md`・`build-and-test-summary.md`（Target Verification Matrix・4節・5節）・`test-results.md`
- `construction/cross-cutting/code-generation/traceability.json`・`construction/dsl-v2/…`・`construction/group/…`・`construction/role/…`・`construction/navigation/…`・`construction/role-admin-ui/…`・`construction/app-frame-ui/code-generation/traceability.json`（status の集計と `Deferred` の行き先を読み取りで数えた）
- `aidlc-state.md`（後の段の EXECUTE）、`git log --first-parent` と `git log -1 ae794e4`（読み取り）
- `construction/ci-pipeline/ci-config.md`・`quality-gates.md`、`gh run view 38432556919`・`gh run view 38398376712 --attempt 1 --log-failed`（読み取り）
- `aidlc/spaces/default/memory/team.md`（Testing Posture・Way of Working）・`project.md`（Way of Working の網羅のたどり方・Testing Posture・Deployment）

## Assumptions & Open Questions

- 3節の未網羅の 5 件と Unverified の持ち越しは、Build and Test の承認で受け入れられたものとして扱いました（承認の操作の記録は `Approve` で、持ち越しの文言はコミット `ae794e4` の件名にあります）。
