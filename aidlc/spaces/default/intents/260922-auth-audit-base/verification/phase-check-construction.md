# Phase Check — Construction → Operation

対象Intent: `260922-auth-audit-base`（スコープ `auth-audit-foundation`）
実施の段: `ci-pipeline`（Step 5）
実施日: 2026-09-23

## 0. 判定

**合格（pass）。Construction から Operation へ移ってよい。**

未対応の要件 0 件、未解決の指摘 0 件、ビルドとテストは全4単位で緑。ただし次の2点を**承認の場で明示して引き継ぐ**。

1. 後の段でしか測れない目標が **17 件**（`Unverified`）残る。持ち主の段は `performance-validation`・`observability-setup`・`deployment-execution`（5節）。依頼者は Build and Test の承認の場でこの引き継ぎを受け入れ済み。
2. 記録の食い違いが3件あった（U3 の `traceability.json` に**すでに解決した**問題の行が `OPEN` のまま残っている／状態の表記が `Deferred` と `DEFERRED` で揺れている／`Unverified` の持ち主ごとの件数が表と集計で1件ずれる）。いずれも実体は解決済みか数え方の違いであり、要件の網羅にも判定にも影響しない（2.1 節・5 節・6 節）。

## 1. 全単位のビルドとテスト

| 単位 | ビルド | 単体テスト | 結合テスト | 画面のテスト | 判定 |
|---|---|---|---|---|---|
| U1 アプリの骨格（u1-app-skeleton） | 済 | 済 | 済 | 済（`src/app` 85 件） | OK |
| U2 認証（u2-authentication） | 済 | 済 | 済 | 済（`src/features/auth` ほか 52 件） | OK |
| U3 アクセス制御（u3-access-control） | 済 | 済 | 済 | 済（`src/features/admin` 30 件） | OK |
| U4 監査ログ（u4-audit-log） | 済 | 済 | 済 | 画面を持たない | OK |

全体の実測（`build-and-test-summary.md` 1節、`test-results.md` 2節。C2 の2回の完全な実行で同じ結果）。

| 項目 | 結果 |
|---|---|
| `./gradlew verify` | **BUILD SUCCESSFUL**（0〜9 の全段） |
| バックエンドの単体テスト（`*Test`） | **387 件すべて成功**（失敗 0・飛ばし 0） |
| バックエンドの結合テスト（`*IT`） | **239 件すべて成功**（失敗 0・飛ばし 0） |
| フロントエンドのテスト（Vitest） | **167 件すべて成功** |
| E2E（Playwright、ビルドした WAR） | **5 件すべて成功** |
| 合計 | **798 件すべて成功、失敗 0** |
| バックエンドのカバレッジ | 行 **96.14%**（1393/1449）、分岐 **91.45%**（417/456）— 下限 80%／70% を満たす |
| フロントエンドのカバレッジ | 行 **98.73%**（391/396）、分岐 **94.02%**（189/201）— 同上 |
| 秘密情報（Gitleaks） | 検出 **0 件**（93 コミット走査） |
| 依存関係の脆弱性（OSV-Scanner） | 統合を止める条件に当たるもの **0 件**、警告 **0 件** |
| 静的解析（SpotBugs＋FindSecBugs） | 重大度 High（priority 1）**0 件**（priority 2・3 の警告 52 件は止めない） |

証拠: `backend/build/test-results/test/`・`backend/build/test-results/integrationTest/`・`backend/build/reports/jacoco/test/jacocoTestReport.xml`・`frontend/coverage/`・`build/reports/osv-scanner/osv.json`・`backend/build/reports/spotbugs/main.xml`、および `construction/build-and-test/test-results.md`。

## 2. Code Generation の `traceability.json`（4単位）

4つとも存在し、`GAP`（要件に対応が無い）・`ORPHAN`（要件に無い実装で根拠が無いもの）は **0 件**。

| 単位 | ファイル | 上流の ID | `coverage` の内訳 | `reverse` の内訳 |
|---|---|---|---|---|
| U1 | `construction/u1-app-skeleton/code-generation/traceability.json` | 93 | OK 90、Deferred 3 | 0 件 |
| U2 | `construction/u2-authentication/code-generation/traceability.json` | 117 | OK 109、Deferred 6、N/A 2 | N/A 3 |
| U3 | `construction/u3-access-control/code-generation/traceability.json` | 50 | OK 47、DEFERRED 3 | N/A 7（うち1件は本段で `OPEN` から直したもの） |
| U4 | `construction/u4-audit-log/code-generation/traceability.json` | 41 | OK 34、DEFERRED 5、N/A 2 | N/A 4 |

- `Deferred`／`DEFERRED` の 17 件は、いずれも**実装は存在し機能としてはテストで確かめられており**、残っているのは負荷の環境や配備先が決まらないと測れない数値の測定だけである（5節）。
- `N/A` の 17 件（`coverage` 4 件・`reverse` 13 件）は、要件の側で明示的に受け入れた危険、または本Intentの範囲外として記載のあるもの。

### 2.1 `OPEN` の1件の扱い（本段で解決）

**決定（依頼者）**: 実体が解決済みであることを確かめたうえで、`traceability.json` の当該の行を `N/A` に直し、解決の経緯（依頼者の承認を得た `tamper` の修正と、5回続けての成功、Build and Test の 798 件すべて成功）を `target` に書いた。以下は、その前の状況の記録である。

U3 の `reverse` に `"status": "OPEN"` の行が1件ある（`AuthTestTokens.tamper の不安定さ`。「D1 の範囲外のため直していない」と書かれている）。**この問題はその後に解決済みである。**

| 確かめたこと | 証拠 |
|---|---|
| 実装が直っている | `backend/src/test/java/cherry/mastersmith/auth/testsupport/AuthTestTokens.java` の `tamper(String)` は、署名の**最初**の1文字を置き換える形になっている（詰め物のビットに当たらないため、必ず署名が壊れる） |
| 依頼者の承認を得て直した記録がある | `construction/u3-access-control/code-generation/code-summary.md` 135 行目「（解決済み）…依頼者の承認を得て … `tamper(String)` を『署名の最初の1文字を置き換える』形に直し、トークンのテストを5回続けて実行してすべて成功することを確かめた（レビューでも修正の内容を確認済み）」 |
| 修正が取り込まれている | コミット `30f73a4`（`AuthTestTokens.java` を含む） |
| 再発していない | Build and Test の2回の完全な実行で 798 件すべて成功（該当の `AccessTokenServiceTest`・`AccessTokenApiIT` を含む） |

したがって**未解決の指摘は無い**。残っているのは、`traceability.json` の行が直されていないという**記録の食い違い**だけである。Code Generation の成果物は確定済みのため本段では書き換えない。あわせて、U3・U4 の `traceability.json` は状態を `DEFERRED`（大文字）で書いており、`Deferred` を使う U1・U2 と表記が揃っていない。いずれも判定には影響しない。

## 3. 単位をまたぐ要件の網羅（cross-unit FR/NFR の関門）

`construction/build-and-test/cross-unit-traceability.md` の判定は **合格（pass）。未対応の ID は 0 件。**

| 区分 | 件数 |
|---|---|
| 要件定義書の ID（`FR`・`NFR`） | 56 |
| 群見出し（下位の ID で対応） | 10 |
| 下位の ID がすべて `OK` | 43 |
| 下位の ID がすべて `OK` だが一部が後の段で測る | 3（NFR1・NFR3・NFR10） |
| **どの単位にも対応づかない（未対応）** | **0** |

- 対応先のファイルの実在も確認済み（`OK` の 280 件が挙げるリポジトリ内のパス 384 件、欠け 0 件）。
- 受け入れ基準（`AC`）は、本Intentで `user-stories` が SKIP のため存在せず、照合の対象外（同書 17 行目）。
- 要件に無い追加（`reverse`）は U2・U3・U4 に計 3 群あり、いずれも `nfr-design` の確定回答または依頼者の決定（D2-A・D3-A）に基づく記録済みの逸脱で、要件の網羅の判定には影響しない。

## 4. CI の関門が、Build and Test の記録したコマンドを実行しているか

Build and Test が走らせたコマンド（`test-results.md` 1節の C1〜C12）と、CI（`.github/workflows/ci.yml`）の対応。

| コマンド | 内容 | CI での扱い | 判定 |
|---|---|---|---|
| C1・C2 `./gradlew verify` | 統合の前の関門そのもの（0〜9 の段） | **CI が同じタスクをそのまま実行する**（「1コマンドの検査を実行する」の段） | **OK** |
| C12 `./gradlew osvScan` | 依存関係の脆弱性 | `verify` の 8 の段に含まれる。CI は OSV-Scanner 2.6.0 を SHA-256 で確かめて入れる | **OK** |
| C11 `pre-commit run --all-files` | 秘密情報の検出とフォーマットの確認 | フック自体は CI に無いが、**同じ検査は `verify` の中で効く**（8 の段の Gitleaks はリポジトリの履歴全体、1・3 の段の Spotless・Prettier・ライセンスヘッダー）。CI は `fetch-depth: 0` で履歴全体を取得する | **OK**（同等） |
| C4〜C10 | 単位ごとに絞った実行 | C2 の部分集合。CI は全体（C2 相当）を実行する | **OK** |
| **C3 `./gradlew e2eTest`** | E2E（Playwright、5 件） | **CI に入れていない**（意図した決定。`build.gradle.kts` の `e2eTest` の説明に「`./gradlew verify` と CI には入れない。計画の P2 の決定」）。統合の前とリリースの前に手で実行する | **意図した除外**（3節の関門の外。引き継ぎ事項ではない） |

カバレッジの下限（行 80%・分岐 70%）、SpotBugs の High、OSV-Scanner の判定（実行時の依存は High 以上で失敗、開発用は警告。ただし `MAL-` と `config/npm-build-tools.txt` の道具は失敗）も、すべて `verify` の中にあるため CI でそのまま効く。詳細は `construction/ci-pipeline/quality-gates.md`。

**結論**: CI の品質の関門は、Build and Test が記録したビルドとテストのコマンドを（意図して外した E2E を除き）すべて実行する。検査の中身は Gradle の1か所にあり、手元と CI で二重管理になっていない。

## 5. 後の段へ引き継ぐ `Unverified` の 17 件

いずれも実装は存在し、機能としてはテストで確かめられている。残るのは測定だけである。持ち主の段はいずれも実行の計画で **EXECUTE**（`aidlc-state.md` の OPERATION PHASE）。

`build-and-test-summary.md` 4.2 節の表の `Owning Stage` の列をそのまま数えた内訳。

| 持ち主の段 | 件数 | 対象の ID |
|---|---|---|
| `performance-validation` | 12 | U1: NFR1.1・NFR1.3・NFR1.9／U2: NFR1.1・NFR1.2・NFR1.3・NFR1.4・NFR1.6／U3: NFR1.1・NFR1.3／U4: NFR1.1・NFR1.2 |
| `observability-setup` | 4 | U2: NFR10.7／U3: NFR10.5／U4: NFR1.4・NFR10.5（運用で見る指標と記録の量） |
| `deployment-execution` | 1 | U4: NFR3.3（内部DBのファイルを OS の権限で守る。配備の手順で確かめる） |
| 合計 | **17** | — |

**上流の集計との差（記録）**: 同書 4.3 節の内訳は「負荷をかけた測定が要るもの 11 件（`performance-validation`）／運用の指標・見積もり 5 件（`observability-setup`）／配備の権限 1 件」となっており、上の表と1件ずれる。ずれるのは **U1-NFR1.9**（内部DBのファイルの増え方の見積もり）で、性質としては「運用で確かめる見積もり」だが、表の `Owning Stage` の列は `performance-validation` である。合計 17 件と、個々の ID の顔ぶれは同じ。**決定（依頼者）**: U1-NFR1.9 は **`performance-validation` が測る**（表の `Owning Stage` の記載にそろえる）。したがって持ち主ごとの件数は performance-validation 12 件・observability-setup 4 件・deployment-execution 1 件である。

依頼者は Build and Test の承認の場でこの引き継ぎを受け入れ済み（`test-results.md` 5節「止まった点と選べる手（決定済み）」）。**目標を緩めて「満たした」ことにはしていない**（`Not Met` 0 件、`Pending` 0 件）。

そのほか、要件で明示的に**受け入れた危険**が4件ある（U2-NFR4.4、U2-NFR5.6、U4-NFR3.6、U4-NFR3.7）。これは測定の対象外であり、引き継ぎではない。

## 6. フェーズ間の矛盾

要件・設計・実装の間に矛盾は検出されなかった。記録上の不一致が3件あり、いずれも判定（合格）には影響しない。

| # | 不一致 | 場所 | 扱い |
|---|---|---|---|
| 1 | 解決済みの問題の行が `OPEN` のまま残っていた | U3 の `code-generation/traceability.json` の `reverse` | 2.1 節。本段で `N/A` に直し、経緯を記録した（依頼者の決定） |
| 2 | 状態の表記が `Deferred`（U1・U2）と `DEFERRED`（U3・U4）で揺れている | 各単位の `code-generation/traceability.json` | 2 節 |
| 3 | `Unverified` の持ち主の段ごとの件数が、表（12／4／1）と集計（11／5／1）で1件ずれる | `build-and-test-summary.md` 4.2 節と 4.3 節 | 5 節。合計 17 件と ID の顔ぶれは同じ |

## 6.1 画面の読み込み量の上限（500KB）の扱い（決定）

**決定（依頼者）**: 承認済みの設計と実装のとおり、**超えても検査は失敗させず、警告にとどめる**。CI Pipeline の要約（`ci-pipeline-questions.md` 3節）の表で「上限超過で失敗」と書いていたのは説明の誤りであり、実装（`frontend/scripts/check-bundle-size.mjs`）と `u1-app-skeleton/infrastructure-design/cicd-pipeline.md` が正しい。`ci-config.md` 8節・`quality-gates.md` 8節の記載も実装どおりである。

## 7. 残っている観察事項（引き継ぎではないが記録する）

1. `SecurityHeadersIT` の一過性の失敗（U4 の `code-summary.md` 6章）は、Build and Test の2回の完全な実行では再現しなかった。監視を続ける。
2. パッケージ単位で行カバレッジが 80% を下回る箇所が2つ（`common/health` 79.2%、`audit/service` 77.2%）。下限は成果物の全体に対して定義しており関門は通るが、テストを足す余地として記録する。
3. U1 の `unit-test-instructions.md` 2.4 節のコマンドは、そのままでは失敗する（単位に絞った実行に全体のカバレッジの判定を含めているため。`test-results.md` 3節）。
4. 部品表（SBOM）は未実装。配備先が決まってから入れる（team.md の Deployment）。

## Sources

- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/build-and-test/build-and-test-summary.md`・`test-results.md`・`cross-unit-traceability.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u1-app-skeleton/code-generation/traceability.json`（U2・U3・U4 の同名のファイルを含む）
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u3-access-control/code-generation/code-summary.md`（`tamper` の解決の記録）
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/ci-pipeline/ci-config.md`・`quality-gates.md`
- `.github/workflows/ci.yml`、`build.gradle.kts`、`backend/build.gradle.kts`、`backend/src/test/java/cherry/mastersmith/auth/testsupport/AuthTestTokens.java`
- `aidlc/spaces/default/intents/260922-auth-audit-base/aidlc-state.md`（後の段が EXECUTE であること）
- `.claude/knowledge/aidlc-shared/verification.md`（本書の様式）

## Assumptions & Open Questions

- U3 の `traceability.json` の `OPEN` の行は、依頼者の決定により本段で `N/A` に直した（実体は解決済みで、経緯を `target` に記録した）。

---

- [ ] 人による確認（CI Pipeline の承認ゲートで確認する）
