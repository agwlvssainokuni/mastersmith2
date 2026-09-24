# Phase Check — Construction → Operation

**判定: 条件付きで合格（Pass with conditions）** — 5単位すべてが作られ試され、各単位の `traceability.json` に未解決（GAP・ORPHAN）は無く、単位をまたぐ FR・NFR・AC の網羅は合格した。CI の関門は Build and Test が記録した build と test のコマンドを同じ `./gradlew verify` で実行する。CI での実行（G-CI）は `6f212e8` で成功した（2026-09-25、`ci-config.md` の8節）。依頼者が受け入れた失敗1件と、Build and Test で Unverified だった13件のうち残りの12件（G-CI はこの段で Met）を後の段と依頼者に引き継ぐため、下の条件を付ける。

点検日: 2026-09-25。点検した記録: `construction/build-and-test/cross-unit-traceability.md`、各単位の `construction/*/code-generation/traceability.json`（5つ）、`construction/build-and-test/test-results.md`・`build-and-test-summary.md`、`construction/ci-pipeline/ci-config.md`・`quality-gates.md`。点検の対象のコミットは `8961cb2`（Build and Test の判定の元）と、記録だけを足した `6f212e8`。

## 1. 5単位が作られ試されたこと

| 単位 | 種別 | コード生成の記録 | テスト（単体・結合・画面・E2E） | カバレッジ（行・分岐） | 状態 |
|---|---|---|---|---|---|
| U1 対象DB | library | `u1-target-db/code-generation/`（code-summary・traceability.json ほか） | 83・50・—・— | 98.5%・96.1% | 作成・試験済み |
| U2 DSL の定義 | library | `u2-dsl-definition/code-generation/` | 89・2・—・— | 98.6%・93.4% | 作成・試験済み |
| U3 既定の DSL の生成 | library | `u3-default-dsl-generation/code-generation/` | 75・9・—・— | 99.3%・100% | 作成・試験済み |
| U4 DSL の管理 | service | `u4-dsl-management/code-generation/` | 62・39・—・— | 100%・95.8% | 作成・試験済み |
| U5 DSL の管理画面 | ui | `u5-dsl-admin-ui/code-generation/` | —・31（`DslAccessControlIT`）・16 ファイル・1 | 96.98%・92.28%（`features/dsl`） | 作成・試験済み |

- 全体（`8961cb2`、`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`、6分17秒）: 単体 716・結合 375・画面 313 がすべて成功（失敗 0・飛ばし 0）。E2E は 6 件すべて成功（直した後の WAR で 18.7 秒）。
- カバレッジ: バックエンド全体 行 98.1%・分岐 94.1%、新しいパッケージ 13 個もすべて下限（行 80%・分岐 70%）以上。画面 行 97.86%・分岐 93.6%。
- 安全の検査: Gitleaks 0 件、SpotBugs の priority 1 と `SQL_` 0 件、OSV-Scanner の脆弱性 0（`--rerun` で走査し直し）。

## 2. 各単位の traceability.json の未解決

| 単位 | 上流の ID の数 | OK | N/A | Deferred | GAP | ORPHAN | reverse |
|---|---|---|---|---|---|---|---|
| u1-target-db | 50 | 50 | 0 | 0 | 0 | 0 | 0 件 |
| u2-dsl-definition | 53 | 50 | 3 | 0 | 0 | 0 | 0 件 |
| u3-default-dsl-generation | 32 | 29 | 3 | 0 | 0 | 0 | 0 件 |
| u4-dsl-management | 118 | 113 | 5 | 0 | 0 | 0 | 0 件 |
| u5-dsl-admin-ui | 92 | 89 | 3 | 0 | 0 | 0 | 0 件 |
| **合計** | **345** | **331（95.9%）** | **14（4.1%）** | **0** | **0** | **0** | **0 件** |

**GAP・ORPHAN は 0 件**。N/A の14件は、コード生成では測れない時間・資源・画面の時間の目標を Build and Test に引き継いだもので、その行き先は次のとおり。

| 単位 | N/A の ID | 引き継ぎ先での判定 |
|---|---|---|
| u2 | NFR1.4・NFR1.5 | Build and Test で Met |
| u2 | NFR3.4 | U4 の結合テスト（`DslAdminApiIT`）で Met |
| u3 | AC1.2.3・NFR1.6・NFR1.7 | Build and Test で Met |
| u4 | NFR1.6・NFR1.8・NFR1.11 | Build and Test で Met |
| u4 | NFR1.10・NFR1.12 | **Unverified**（持ち主 performance-validation。4節） |
| u5 | NFR1.18・NFR1.19・NFR1.20 | Build and Test で Met |

## 3. 単位をまたぐ FR・NFR・AC の網羅

`cross-unit-traceability.md` の判定は **合格（Pass）**。要件定義の FR・NFR（62 件、親と枝番）とストーリーの AC（101 件）の合計 163 件は、すべてテスト（または Build and Test の実測）までたどれた。

| たどり方 | 件数 |
|---|---|
| コード生成の traceability.json で直接 `OK`（対象のファイルが実在） | 99 |
| BR・ストーリー・NFR の枝番を経る2段の連鎖で `OK`（project.md の Way of Working の決まり） | 60 |
| Build and Test で埋めたもの（AC1.2.3・AC3.2.5・FR1.7・NFR11） | 4 |
| **合計** | **163（100%）** |

記録上の注意（網羅の判定は変えない。Build and Test の Q7: A により直さず記録）:

- FR1.4・FR3.5 と、NFR1.1・NFR1.2・NFR6.2・NFR6.3・NFR4.8・NFR9.2・NFR12.1〜NFR12.3 の一部は、確かめたテストではなく本番のソースや設定を `OK` の対象にしている。Build and Test はテストに読み替えて判定した。
- AC3.2.5 はどの単位の traceability.json にも無かった。Build and Test の実測（NFR1.8）で満たしたことを確かめた。

## 4. CI の関門が Build and Test のコマンドを実行すること

`quality-gates.md` の6節で対応づけた。

| Build and Test のコマンド | CI での実行 |
|---|---|
| C12 `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（build と全テスト・カバレッジ・安全の検査・成果物） | **実行する**。CI（`.github/workflows/ci.yml`）の「1コマンドの検査を実行する」の段が同じ `./gradlew verify` を呼ぶ。CI はまっさらな環境のためテストは必ず走り、`CI=true` で対象DB のテストを飛ばさない |
| C13 `./gradlew osvScan --rerun` | **実行する**（`verify` の 8 の段。CI では UP-TO-DATE にならない） |
| C3 `./gradlew e2eTest` | 意図して CI の外（Q1: A）。統合の前とリリースの前に手元で実行 |
| C14 コンテナに届かないときの確かめ | 意図して CI の外（CI では失敗させる道を通る）。登録の順の構造の検査は CI でも走る |
| C6〜C11・C15〜C18 性能の測定 | 意図して CI の外。使い捨ての環境で、持ち主は Build and Test と Performance Validation |

CI の定義はこの Intent で変えておらず、`verify` に増えた中身（対象DB 3種類、テストのヒープ 1g、`verifyDslSchemaInWar`、構造の検査、H2 の詰め直しのテスト、`SQL_` の関門、新しいパッケージごとのカバレッジの下限）は、そのまま CI でも実行される（`ci-config.md` の5節）。GitHub Actions での実行（run 36021036733、`6f212e8`）は success（`./gradlew verify` 7分44秒、SKIPPED 0 件）で、G-CI は Met（`ci-config.md` の8節）。

## 5. 受け入れた失敗（依頼者の判断）

| Target ID | 内容 | 判断 | 記録 | 残る危険と見張り |
|---|---|---|---|---|
| U4-STORAGE-RUN（Not Met） | 動いている間、10MB の DSL の投入→適用の1回ごとに内部DB（H2）のファイルが約 10.8MB 増え、履歴 20 件の後も頭打ちにならない（21 回＋プレビューで 278.2MB、40 回で 483.1MB。期待は最大約 210MB）。止めて起動し直すと `DEFRAG_ALWAYS=TRUE` で 15.9MB に戻る（U4-STORAGE-RESTART は Met） | 依頼者が「Accept failure」（2026-09-25、Loop-backs used: 1/3）。目標は緩めていない | README の「DSL の管理」の節に既知の制約として記録（大きな DSL の投入と適用を重ねたら起動し直す）。承認済みの要件は書き換えない | 起動し直さずに重ねるとボリュームの使用量が増え続ける。コンテナのメモリ（`anon`）は上限 2g の約 92%。Operation の段（deployment-execution・observability-setup）では、この制約を前提に配備の手順と見張り（ボリュームの大きさ）を扱う |

## 6. Unverified の13件と持ち主の段

| Target ID | 内容 | 持ち主の段 |
|---|---|---|
| NFR1.10 | 今の状態・履歴・破棄・適用の 95% が 1 秒以内（履歴 20 件など） | performance-validation |
| NFR1.12 | 10MB の DSL の処理が 1g のコンテナで、ログインと同時でも失敗しない（2g でもメモリが上限に近いため最初に確かめる） | performance-validation |
| U4-POOL | 適用などの操作で内部DB のプール（上限 30）をログインと共有しても尽きない | performance-validation |
| OBS-DASH | ダッシュボードの行「DSL の操作」の式がすべて値を返し、Loki で `dsl.operation` のキーで絞れる | observability-setup |
| G-CI | GitHub Actions で同じ `verify` が通る（ヒープ 1g、対象DB のコンテナを含む） | ci-pipeline（この段）。**Met**: run 36021036733（`6f212e8`）が success。`ci-config.md` の8節 |
| U4-MIGRATION | Flyway の V5・V6 は前進のみで、1つ前の版のアプリが移行後の内部DB で動く | deployment-execution |
| A11Y-O1・A11Y-O3・A11Y-O4・A11Y-P6・A11Y-SR・A11Y-U5・A11Y-DESIGN（7件） | キーボードだけの操作、Tab の順、フォーカスの見え方、200% の拡大と狭い幅、VoiceOver、求めていない画面の切り替え、時間の制限と文言の一貫性 | 依頼者の手での確認（持ち主の段なし。リリースの前） |

## 7. 判定

**Pass with conditions（条件付きで合格）**

合格の根拠:

- 5単位すべてが作られ、`8961cb2` の `./gradlew verify` で単体・結合・画面のテストがすべて成功し、カバレッジと安全の検査の関門を通った。E2E も成功。
- 5つの traceability.json に GAP・ORPHAN は無く、N/A の14件は Build and Test（12件）か performance-validation（2件）に行き先がある。
- 単位をまたぐ 163 件の網羅は Pass（100%）。
- CI の関門は、Build and Test の build と test のコマンド（C12・C13）を同じ `./gradlew verify` で実行する。CI の外に置く検査には、代わりの実行の場がある（`quality-gates.md` の7節）。

条件:

1. **G-CI（満たした）**: 依頼者が `gh auth login` をしてプッシュした後に、AI が `gh run list`・`gh run view` で読み、`6f212e8` の実行（run 36021036733）が success であることを `construction/ci-pipeline/ci-config.md` の8節に記録した（2026-09-25）。今後も CI が失敗したら、次へ進む前に原因を直す（team.md の Way of Working）。
2. **performance-validation** で NFR1.10・NFR1.12・U4-POOL を確かめる（NFR1.12 を最初に）。目標は緩めない。
3. **observability-setup** で OBS-DASH を確かめる。あわせて、受け入れた失敗 U4-STORAGE-RUN を踏まえ、内部DB のボリュームの大きさを見張れるようにするかを扱う。
4. **deployment-execution** で U4-MIGRATION（V5・V6 の前進のみと、1つ前の版のアプリが動くこと）を確かめ、戻し方に含める。配備の手順は U4-STORAGE-RUN の既知の制約（大きな DSL の投入と適用を重ねたら起動し直す）を前提にする。
5. **依頼者の手でのアクセシビリティの確認**（7件）をリリースの前に行う。行うまでは、部品ごとの自動の検査（vitest-axe）とキーボードの操作のテストが緑なら流れ全体でも操作できる、という前提が未確認のまま残る。
6. **E2E** は統合の前とリリースの前に手元で `./gradlew e2eTest` を実行する（CI の外）。

## 8. 承認

- [ ] 依頼者の承認（CI Pipeline の承認をもって確かめる）

## Sources

- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/build-and-test/cross-unit-traceability.md`（判定 Pass、163 件）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/u1-target-db/code-generation/traceability.json`・`u2-dsl-definition/code-generation/traceability.json`・`u3-default-dsl-generation/code-generation/traceability.json`・`u4-dsl-management/code-generation/traceability.json`・`u5-dsl-admin-ui/code-generation/traceability.json`（状態の件数を数えた）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/build-and-test/test-results.md`（実行の記録、受け入れた失敗）・`build-and-test-summary.md`（Target Verification Matrix、Met 93・Not Met 1・Unverified 13）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/ci-pipeline/ci-config.md`・`quality-gates.md`・`ci-pipeline-questions.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/aidlc-state.md`（Operation の段の実行の計画）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/verification/phase-check-inception.md`（書き方の見本）
- `.claude/knowledge/aidlc-shared/verification.md`（Construction → Operation の点検の内容）
- `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- G-CI は `6f212e8` で success（`./gradlew verify` 7分44秒）。`ubuntu-latest` のラベルは 2026-10-19 から Ubuntu 26 に移るため、移った後の最初の CI の実行で同じく通ることを確かめる。
- 受け入れた失敗 U4-STORAGE-RUN のボリュームの見張りを observability-setup で扱うかは、その段で依頼者が決める。
