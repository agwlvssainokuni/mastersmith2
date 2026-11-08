# 要件の網羅（Intent 261107-verify-quick-local）

`aidlc engine sensor-traceability --stage build-and-test` の出力から書いた。単位は無い（bugfix の範囲、作業はステージ単位の1回）。

## 判定

合格。道具の出力は NFR1・NFR2・NFR3 を「作られていない」としたが、この3つは Code Generation の対応表で Deferred（Build and Test が持ち主）とした非機能の要件で、この段の実行で確かめた（下の表）。ほかの ID はすべて Code Generation のファイルで作られている。

| ID | 網羅 | 持ち主 | ファイル・確かめ |
|---|---|---|---|
| FR1.1〜FR1.4 | 作られた | code-generation | `build.gradle.kts`。確かめの台本の項目 2・3（`test-results.md` の 4） |
| FR2.1〜FR2.3 | 作られた | code-generation | `backend/build.gradle.kts`・`build.gradle.kts`。台本の項目 4〜6、`code-summary.md` の実行の確かめ |
| FR2.4 | 作られた | code-generation | `backend/src/test/java/cherry/mastersmith/targetdb/testsupport/ContainerRuntimeCheck.java` |
| FR3.1〜FR3.3 | 作られた | code-generation | `backend/build.gradle.kts`。`test-results.md` の 3 の参考値 |
| FR4.1 | 作られた | code-generation | `scripts/verify-graph-check.mjs`（項目 1・8） |
| FR4.2 | 作られた | code-generation | `backend/build.gradle.kts`。`test-results.md` の 2 |
| FR4.3 | 作られた | code-generation | `.github/workflows/ci.yml`（`develop` からの差が無い） |
| FR5.1〜FR5.5 | 作られた | code-generation | `aidlc/spaces/default/memory/project.md`・`team.md`・`code-summary.md` |
| FR6.1・FR6.2 | 作られた | code-generation | `docs/verification.md`・`README.md` |
| NFR1 | この段で確かめた | build-and-test | `test-results.md` の時間の比べ（`verifyQuick` 3分7秒 < `verify` 27分44秒） |
| NFR2 | この段で確かめた | build-and-test | `test-results.md` の 1（全件の `verify` が下限とセキュリティの検査を通った） |
| NFR3 | この段で確かめた | build-and-test | `test-results.md` の 1・2（直列と並べた `verify` が通った） |
| NFR4 | 作られた | code-generation | `scripts/verify-graph-check.mjs` |
| NFR5 | 作られた | code-generation | `build.gradle.kts`（Spotless・ライセンスヘッダーが `verify` の 1 の段で通った） |

作られていない ID: 無し。
