# 単位をまたぐ追跡 — Build and Test（Intent 261101-maintenance-update）

単位の分割の無い bugfix のため、追跡は「要件定義の FR・NFR → Code Generation の `traceability.json`（段の全体で1つ）→ この段の結果」の連鎖でたどる。この段では `aidlc engine sensor-traceability` を流しておらず（指揮役の扱い）、`construction/code-generation/traceability.json` の 43 行（FR1.1〜FR11.3・NFR1〜NFR5）を読んで書いた。承認済みの `traceability.json` は書き換えず、この段で確かめた結果をここと `build-and-test-summary.md` に書く。

## 判定

**covered（作られ、この段で確かめた）: 39 行。Deferred が残るもの: 4 行（FR5.4、FR11.1〜FR11.3）**。要件定義の ID で `traceability.json` に無いものは無い（43 行 = 要件の FR・NFR の全部）。

| 区分 | ID | 扱い |
|---|---|---|
| Code Generation で作り、この段の verify・E2E で確かめた | FR1.1〜FR1.3・FR2.1〜FR2.2・FR3.1〜FR3.3・FR4.1〜FR4.7・FR5.1〜FR5.3・FR6.1・FR7.1〜FR7.3・FR8.1〜FR8.4・FR9.1〜FR9.4・NFR1〜NFR5 | covered |
| Code Generation で Deferred（Build and Test）、この段で確かめた | FR10.1〜FR10.4 | covered（この段で Met。記録の上では Deferred のまま） |
| 上流の公開待ちで見送り、記録した | FR5.4（A2・A3） | Deferred（要件の「無ければ見送って記録する」のとおり。持ち越し） |
| 後の段が持ち主 | FR11.1〜FR11.3 | Deferred（Deployment Pipeline・Deployment Execution） |

## ID ごとの連鎖

| 要件 | `traceability.json` の状態と作った先 | この段の結果 |
|---|---|---|
| FR1.1〜FR1.3（Java 25 系） | OK: `gradle/libs.versions.toml`・`Dockerfile`・`README.md` | verify 成功（手元 JDK 25）、CI の Java 25.0.5+8 と Dockerfile の JRE 25.0.5_8 が一致、試験のイメージ（JRE 25.0.5_8）で負荷の試験が通過 |
| FR2.1・FR2.2（Node 26 系） | OK: `frontend/package.json`・`build.gradle.kts`・CI | 手元 v26.13.0 で verify の `checkToolchain` と画面のテスト（`NODE_OPTIONS=--no-experimental-webstorage`）が通過、CI の Node v26.13.0 |
| FR3.1〜FR3.3（Spring Boot と連動する固定） | OK: `gradle/libs.versions.toml` | verify・E2E が通過（固定は残した）。OSV で High 0 |
| FR4.1〜FR4.7（依存・道具・Dependabot） | OK: lockfile・`.github/dependabot.yml`・`.github/workflows/ci.yml` | verify の `osvScan`・`spotbugsGate`・`gitleaksScan` が通過。axe-core 4.14.0 の1つで E2E の axe が違反 0（実際の Chromium）。Dependabot の PR は開いているもの 0 |
| FR5.1〜FR5.3（サブモジュール） | OK: 固定先の更新の専用のコミット `28dd694b` | verify が `vendor/make-you-chic-ui` を組み込んで通過、E2E 全件通過、CI 1回目で成功 |
| FR5.4（A2・A3） | Deferred: 上流の公開待ち | 見送りを `code-summary.md` と `build-and-test-summary.md` の持ち越しに記録 |
| FR6.1（GitHub Actions） | OK: `.github/workflows/ci.yml` | この Intent の6つの CI の run がすべて1回目で成功 |
| FR7.1〜FR7.3（コンテナのイメージ） | OK: `compose.yaml`・`docker/perf/compose.yaml`・`TargetDbImages.java`・`perf/versions.env` | 対象DB 3種類の結合テストが通過。E2E の後に作り直した見本の PG が `compose.yaml` の版とダイジェストに一致。負荷の試験の対象DB と k6 も固定の版で動いた |
| FR8.1〜FR8.4（README と docs/） | OK: `README.md`・`docs/`・`perf/README.md` | この段は `build-instructions.md` ほかで `docs/verification.md`・`perf/README.md` の今の手順を使って流せた。暖めの形が README に無い点を持ち越しに記録 |
| FR9.1（P1） | OK: `RowSqlBuilder.java`・`*DeepPageSortIT` | 結合テスト 3種類 × 3 件が verify で通過。並びの意味の変化（R-01）を `build-and-test-summary.md` に記録 |
| FR9.2（T1） | OK: `frontend/vitest.config.ts` | 道具のカバレッジ 94.23%・90.65% が全体の下限の対象に入って通過 |
| FR9.3（T2） | OK: `frontend/eslint.config.js`・`tsconfig.perf-tool.json` | verify の `perfToolEslint`・`perfToolTypecheck` が通過 |
| FR9.4（T3） | OK: `BusinessDataWriteService.java`・`BusinessDataWriteReferenceTest.java` | 単体テストが verify で通過 |
| FR10.1〜FR10.4（負荷の試験） | Deferred（Build and Test） | **Met**: 判定の回の全場面が線と `checks` を通過、Not Met 0。前の Intent と並べた。記録の json は伏せ、`--scan`・`gitleaks dir`・`eyJ` 0（`test-results.md` 5節） |
| FR11.1〜FR11.3（配備・片付け・取り込み） | Deferred（Deployment Pipeline・Deployment Execution） | 未実施（後の段） |
| NFR1（目標を変えない） | OK | 下限・除外・一覧・線の値の差 0（`build-and-test-summary.md` のカバレッジの期待） |
| NFR2（安全） | OK | `osvScan` High 0、E2E の報告・負荷の試験のログと記録に秘密の形 0 |
| NFR3（品質の関門） | OK | clean 付きの verify・E2E の通過 |
| NFR4（互換と戻し） | OK | 移行の差 0、E2E 全件通過、配備したアプリは止める前のイメージで戻った |
| NFR5（時間） | OK | verify 27分34秒、CI の検査の job の最大 37分05秒（42 分の内） |

## 作られていない ID

無い。Deferred の4行（FR5.4・FR11.1〜FR11.3）は、上流の公開待ちと後の段の作業で、承認の場で並べる。

## Sources

- `aidlc/spaces/default/intents/261101-maintenance-update/inception/requirements-analysis/requirements.md`
- `aidlc/spaces/default/intents/261101-maintenance-update/construction/code-generation/traceability.json`・`code-summary.md`
- この段の `test-results.md`・`build-and-test-summary.md`

## Assumptions & Open Questions

- [assumption] `aidlc engine sensor-traceability --stage build-and-test` の出力は使っていない（この段の作業の範囲で engine のコマンドを流さないため）。指揮役が流して食い違いがあれば、センサーの出力を正としてこの文書を直す。
