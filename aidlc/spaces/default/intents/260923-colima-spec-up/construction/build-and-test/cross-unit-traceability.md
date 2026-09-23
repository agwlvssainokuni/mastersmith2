# 要件の網羅の確かめ（cross-unit-traceability）

単位の分割の無い Intent のため、確かめる対象は段の単位の `aidlc/spaces/default/intents/260923-colima-spec-up/construction/code-generation/traceability.json` の1つだけである。User Stories の段は行っていないため、受け入れ条件（AC）は無い。

## 判定

**不合格（fail）**。要件の ID 33 件（親の FR1〜FR7 を含む）のうち、traceability.json で `OK` になっていないものが 18 件ある。

- 13 件は、Code Generation が後の段（この段・Deployment Execution）に回したもの（`Deferred`）である。
- 5 件（FR1・FR2・FR4・FR5・FR6）は、子に `Deferred` があるため親も網羅されていない扱いである。

どれも実装の漏れではない。この段に回した 12 件は、この段で実行し、結果を `test-results.md` に記録した。traceability.json は承認済みの成果物のため書き換えていない（下の「網羅されていない要素」を参照）。承認の関門で依頼者に示す。

## ID ごとの網羅

| ID | traceability.json の状態 | 持ち主 | 対象のファイル（存在の確認）・この段の結果 |
|---|---|---|---|
| FR1 | 未網羅（子の FR1.1・FR1.2 が Deferred） | — | — |
| FR1.1 | Deferred | build-and-test | この段で実施: VM を CPU 4・6GiB に作り直した（`test-results.md` 3章） |
| FR1.2 | Deferred | build-and-test | この段で実施: 依頼者の確認（Q1: A）、作り直しの後に healthy（`test-results.md` 3章） |
| FR1.3 | OK | code-generation | `README.md`（あり） |
| FR2 | 未網羅（子の FR2.2 が Deferred） | — | — |
| FR2.1 | OK | code-generation | `compose.yaml`（あり） |
| FR2.2 | Deferred | deployment-execution | この PC の `.env` の変更（未実施） |
| FR3 / FR3.1 | OK | code-generation | `Dockerfile`（あり） |
| FR3.2 | OK | code-generation | `Dockerfile`（あり） |
| FR3.3 | OK | code-generation | `docker/perf/compose.yaml`（あり） |
| FR4 | 未網羅（子が Deferred） | — | — |
| FR4.1 | Deferred | build-and-test | この段で実施: NMT で 1g・2g の内訳を記録（`test-results.md` 5章） |
| FR4.2 | Deferred | build-and-test | この段で実施: アプリのコードに原因の兆しは無し（`test-results.md` 5章） |
| FR5 | 未網羅（子が Deferred） | — | — |
| FR5.1 | Deferred | build-and-test | この段で実施: 使い捨ての環境で k6（`test-results.md` 4章） |
| FR5.2 | Deferred | build-and-test | この段で実施: 合格（p95 940・926 ms、refresh で停止なし） |
| FR5.3 | Deferred | build-and-test | 満たしたため相談は不要だった |
| FR5.4 | Deferred | build-and-test | この段で実施: 前回と並べて記録（`test-results.md` 4章） |
| FR6 | 未網羅（子の FR6.4 が Deferred） | — | — |
| FR6.1 | OK | code-generation | `README.md`（あり） |
| FR6.2 | OK | code-generation | `.env.example`（あり） |
| FR6.3 | OK | code-generation | `compose.yaml`（あり） |
| FR6.4 | Deferred（前半は対応済み） | build-and-test | この段で実施: `perf/README.md` の末尾の注記を結果に合わせて書き換えた（未コミット） |
| FR6.5 | OK | code-generation | `README.md`（あり） |
| FR7 / FR7.1 | OK | code-generation | `aidlc/spaces/default/intents/260923-colima-spec-up/construction/code-generation/code-summary.md`（あり）。この段でも `verify` が通った |
| FR7.2 | OK | code-generation | `backend/src/main/java`（あり。差分なし） |
| NFR1 | Deferred | build-and-test | この段で実施: Met（`build-and-test-summary.md` の Target Verification Matrix） |
| NFR2 | Deferred | build-and-test | この段で実施: Met |
| NFR3 | Deferred | build-and-test | この段で実施: Met |
| NFR4 | OK | code-generation | `build.gradle.kts`（あり） |
| NFR5 | OK | code-generation | `docker/check-container-limits.sh`（あり） |

## 網羅されていない要素

| ID | 理由 | 扱い |
|---|---|---|
| FR1.1・FR1.2・FR4.1・FR4.2・FR5.1〜FR5.4・FR6.4・NFR1〜NFR3（と親の FR1・FR4・FR5・FR6） | VM の作り直しと負荷の試験が要るため、Code Generation が Build and Test に回した | この段で実行し、すべて満たした（上の表と `build-and-test-summary.md`）。traceability.json は承認済みのため `Deferred` のまま |
| FR2.2（と親の FR2） | この PC の `.env`（秘密情報を含む）を変える作業で、配備の一部である | `deployment-execution` の段で行う（実行の計画に EXECUTE として入っている） |

## Sources

- `aidlc/spaces/default/intents/260923-colima-spec-up/inception/requirements-analysis/requirements.md`
- `aidlc/spaces/default/intents/260923-colima-spec-up/construction/code-generation/traceability.json`
- `aidlc/spaces/default/intents/260923-colima-spec-up/construction/build-and-test/test-results.md`
