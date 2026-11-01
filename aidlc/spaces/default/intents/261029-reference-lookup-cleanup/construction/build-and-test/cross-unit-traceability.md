# 単位をまたぐ追跡 — Build and Test（Intent 261029-reference-lookup-cleanup）

元: `aidlc engine sensor-traceability --stage build-and-test` の出力（指揮役が流し、Build and Test の段で保存した）。要件の網羅は、要件定義の FR・NFR から、ストーリーの AC と単位の NFR 要件の枝番を経て、各単位の Code Generation の `traceability.json` へ至る2段の連鎖でたどる（`project.md`）。センサーは承認済みの Code Generation の `traceability.json` を読むため、`Deferred` のまま残る行は、この段で確かめても「作られていない（not_built）」と出る。承認済みの記録は書き換えず、この段で確かめた結果をここと `build-and-test-summary.md` に書く。

## 判定

**センサーの判定: fail**（`pass: false`）。追跡した ID 167（FR・NFR・枝番 45、AC 122）のうち covered 148、uncovered 19（FR1.5・FR3.1〜FR3.4・FR4.1・FR5.1〜FR5.5・NFR2・NFR3・NFR5・NFR8・AC3.1.7・AC3.4.5・AC3.5.4・AC4.1.6）。N/A は FR8.1・FR8.2（ストーリーにしない Dependabot の取り込み、Q5: A）と AC2.3.5（初稿の ID）。

この段の確かめを足した後の読み（下の2節）:

| 区分 | ID | 扱い |
|---|---|---|
| この段で確かめ済み（記録の上だけ Deferred が残る） | FR5.1〜FR5.5（AC4.1.6）、NFR5（U1 NFR5.3・U2 NFR5.23・U4 NFR5.62）、NFR2 のうち U1 NFR2.4・U2 NFR2.22・U3 NFR2.45、NFR8 のうち U4 NFR8.69 | Met。承認の場で記録の Deferred をどう扱うかを確かめる |
| 作って確かめ済みだが、追跡の連鎖が途切れている | FR3.1〜FR3.4・AC3.1.7、FR4.1・AC3.4.5 | 実装とテストは U3 にある。持ち主の単位（U2・U1）が U3 へ Deferred にしたが、U3 の `traceability.json` は機能設計の決まり（U3 の `functional-spec.md` 33 行）でこれらの AC を載せないため、連鎖が行き止まる。**記録の抜け**で、作られていないのではない |
| 後の段が持ち主（この計画に EXECUTE で載っている） | FR1.5・FR3.4（AC3.5.4）、NFR2 のうち U1 NFR2.1・2.3・2.5、U2 NFR2.21・2.23・2.24・2.25（Performance Validation）、U1 NFR2.10・U2 NFR2.29、NFR3 の U1 NFR3.8・U2 NFR3.28（Observability Setup） | Unverified。持ち主の段で確かめる |
| この段の持ち主で、まだ確かめていない | NFR8 のうち U4 NFR8.64（H1） | **Unverified（本当に足りない）**。`test-results.md` 12節 |

## 1. 単位の受け持ちと持ち主

| 単位 | 受け持つストーリー・要件 | `traceability.json` の Deferred（行き先） |
|---|---|---|
| U1 u1-reference-read（B1・B2） | US1.1〜US1.4・US2.1〜US2.3・US3.4・US3.5、NFR1.1〜NFR9.1 | 画面の AC（AC1.1.7・1.1.8・1.1.10・1.3.3・1.3.6・1.3.7・2.3.3・3.4.5）→ U3、AC3.5.4・NFR2.1・2.3・2.5 → performance-validation、NFR2.4・5.3 → build-and-test、NFR2.10・3.8 → observability-setup |
| U2 u2-reference-write-check（B3） | US1.5・US3.1〜US3.3、NFR1.21〜NFR9.21 | AC1.5.1・AC3.1.7 → U3、NFR2.21・2.25 → performance-validation、NFR2.22〜2.24 → build-and-test・performance-validation、NFR5.23 → build-and-test、NFR2.29・3.28 → observability-setup |
| U3 u3-reference-screens（B4） | 画面の AC（US1.1〜US1.3・US1.5・US2.2〜US2.5） | サーバー側の AC（AC1.1.5・1.1.9・1.2.1・1.2.5・1.3.5・1.5.2）→ U1・U2（どちらも OK）、NFR2.45 → build-and-test |
| U4 u4-perf-evidence-redactor（B5） | US4.1（FR5） | AC4.1.6・NFR5.62・NFR8.64・NFR8.69・BR4.3 → build-and-test |
| U5 u5-container-perf-docs（B6） | US4.2・US4.3（FR6・FR7） | なし |

## 2. uncovered の ID ごとの分析

| ID | センサーが「作られていない」とした元 | 行き先（持ち主） | この段の結果 | 区分 |
|---|---|---|---|---|
| AC3.5.4 | U1 の `traceability.json` で Deferred | performance-validation | 種・`k6 inspect`・試し走りは済み（`k6-trial-record.md`）。3種類の判定の回は未実施 | 後の段（Unverified） |
| FR1.5 | AC3.5.4 | 同上 | 同上 | 後の段 |
| AC3.1.7 | U2 で Deferred → U3。U3 の `traceability.json` に行が無い | U3（画面） | 実装とテストあり: `LookupField.test.tsx`（`aria-describedby` を誤りの文に結ぶ、R-02）・`ReadonlyReferenceField.test.tsx`・`BusinessDataFormPage.test.tsx`（409 `BUSINESS_REFERENCE_NOT_FOUND` と `REFERENCE_NOT_FOUND` の項目の誤り）。fix ブランチの verify で通過（jsdom）。フォーカスは項目ではなく誤りのまとめへ移す（U3 の `functional-spec.md` X7、`mockups.md` 12節で承認済みの差）。実際のブラウザでは E2E 195 の誤りの状態の axe が通過 | 連鎖の途切れ（記録の抜け） |
| FR3.1・FR3.2・FR3.3 | AC3.1.7 | 同上 | 同上。ほかの AC（AC3.1.1〜3.1.6・3.1.8・AC1.5.x）は covered | 連鎖の途切れ |
| FR3.4 | AC3.1.7・AC3.5.4 | U3・performance-validation | 上の2行のとおり | 連鎖の途切れ＋後の段 |
| AC3.4.5 | U1 で Deferred → U3。U3 の `traceability.json` に行が無い | U3（画面） | 実装とテストあり: `BusinessDataListPage.test.tsx`・`BusinessDataDetailPage.test.tsx` の「参照の項目を含む定義でも一覧・詳細は今のまま」（AC3.4.3・AC3.4.5 と注記）。fix ブランチの verify で通過（jsdom）。E2E 180 も実際の Chromium で通過 | 連鎖の途切れ（記録の抜け） |
| FR4.1 | AC3.4.5 | 同上 | 同上 | 連鎖の途切れ |
| AC4.1.6 | U4 で Deferred → build-and-test | build-and-test | Met: U4 のテストは verify と CI で流れて通過（ファイルごとの件数は B5 の実測、以後変更 0）。`evidence/` の `gitleaks dir` 0 件（H4） | この段で確かめ済み |
| FR5.1〜FR5.5 | AC4.1.6 | 同上 | 同上 | この段で確かめ済み |
| NFR2 | U1 NFR2.1・2.3・2.5・2.4・2.10、U2 NFR2.21〜2.25・2.29、U3 NFR2.45 | performance-validation・observability-setup・build-and-test | build-and-test の分（U1 NFR2.4・U2 NFR2.22・U3 NFR2.45）は Met。U2 NFR2.23・2.24 はこの段の分（台本の差・閾値の照合）が済み、比べと判定が Performance Validation | 後の段（Unverified）＋この段で確かめ済み |
| NFR3 | U1 NFR3.8・U2 NFR3.28 | observability-setup | 警報 `ms-biz-target-db` の firing は未確かめ。接続の数と時間の上限（NFR3.1〜3.7・3.21〜3.27）は Met | 後の段 |
| NFR5 | U1 NFR5.3・U2 NFR5.23・U4 NFR5.62 | build-and-test | Met（verify 26分4秒、CI の検査の job はすべて 42 分の内、U4 のテストの件数） | この段で確かめ済み |
| NFR8 | U4 NFR8.64・NFR8.69 | build-and-test | NFR8.69 は Met（H4）。**NFR8.64（H1）は未実施** | この段で確かめ済み＋本当に足りない |

## 3. ID ごとの網羅（センサーの出力の写し）

単位の名前は U1〜U5 と短く書いた（ID 167 行: FR・NFR・枝番 45、AC 122）。ファイルの道は `backend/src/test/java/cherry/mastersmith/` と `frontend/src/` を省いた。

| ID | 網羅（センサー） | たどった先 | 作られていない（not_built） |
|---|---|---|---|
| FR1.1 | covered | AC1.1.1・AC1.1.2・AC1.1.3・AC1.1.4・AC1.1.5・AC1.1.6・AC1.1.7・AC1.1.8・AC1.1.9・AC1.1.10 | — |
| FR1.2 | covered | AC1.2.1・AC1.2.2・AC1.2.3・AC1.2.4・AC1.2.5 | — |
| FR1.3 | covered | AC1.3.3・AC1.3.6・AC1.3.1・AC1.3.2・AC1.3.4・AC1.3.5・AC1.3.7・AC1.3.8・AC1.5.1・AC1.5.2・AC1.5.3・AC1.5.4 | — |
| FR1.4 | covered | AC1.3.3・AC1.3.6・AC1.3.1・AC1.3.2・AC1.3.4・AC1.3.5・AC1.3.7・AC1.3.8・AC1.4.2・AC1.4.7・AC1.4.1・AC1.4.3・AC1.4.4・AC1.4.5・AC1.4.6 | — |
| FR1.5 | **uncovered** | AC1.1.1・AC1.1.2・AC1.1.3・AC1.1.4・AC1.1.5・AC1.1.6・AC1.1.7・AC1.1.8・AC1.1.9・AC1.1.10・AC3.5.1・AC3.5.2・AC3.5.3・AC3.5.4・AC3.5.5 | AC3.5.4 |
| FR1.6 | covered | AC1.4.2・AC1.4.7・AC1.4.1・AC1.4.3・AC1.4.4・AC1.4.5・AC1.4.6 | — |
| FR2.1 | covered | AC1.3.3・AC1.3.6・AC1.3.1・AC1.3.2・AC1.3.4・AC1.3.5・AC1.3.7・AC1.3.8・AC2.2.9・AC2.2.5・AC2.2.1・AC2.2.2・AC2.2.3・AC2.2.4・AC2.2.6・AC2.2.7・AC2.2.8・AC2.2.10・AC2.2.11 | — |
| FR2.2 | covered | AC2.1.15・AC2.1.1・AC2.1.2・AC2.1.3・AC2.1.4・AC2.1.5・AC2.1.6・AC2.1.7・AC2.1.8・AC2.1.11・AC2.1.9・AC2.1.10・AC2.1.12・AC2.1.13・AC2.1.14・AC2.1.16 | — |
| FR2.3 | covered | AC2.1.15・AC2.1.1・AC2.1.2・AC2.1.3・AC2.1.4・AC2.1.5・AC2.1.6・AC2.1.7・AC2.1.8・AC2.1.11・AC2.1.9・AC2.1.10・AC2.1.12・AC2.1.13・AC2.1.14・AC2.1.16・AC2.2.9・AC2.2.5・AC2.2.1・AC2.2.2・AC2.2.3・AC2.2.4・AC2.2.6・AC2.2.7・AC2.2.8・AC2.2.10・AC2.2.11 | — |
| FR2.4 | covered | AC2.2.9・AC2.2.5・AC2.2.1・AC2.2.2・AC2.2.3・AC2.2.4・AC2.2.6・AC2.2.7・AC2.2.8・AC2.2.10・AC2.2.11 | — |
| FR2.5 | covered | AC2.3.3・AC2.3.1・AC2.3.2・AC2.3.4・AC2.3.5 | — |
| FR2.6 | covered | AC2.1.15・AC2.1.1・AC2.1.2・AC2.1.3・AC2.1.4・AC2.1.5・AC2.1.6・AC2.1.7・AC2.1.8・AC2.1.11・AC2.1.9・AC2.1.10・AC2.1.12・AC2.1.13・AC2.1.14・AC2.1.16 | — |
| FR2.7 | covered | AC2.2.9・AC2.2.5・AC2.2.1・AC2.2.2・AC2.2.3・AC2.2.4・AC2.2.6・AC2.2.7・AC2.2.8・AC2.2.10・AC2.2.11・AC2.4.1・AC2.4.2・AC2.4.3・AC2.4.4・AC2.4.5・AC2.4.6 | — |
| FR3.1 | **uncovered** | AC3.1.3・AC3.1.1・AC3.1.2・AC3.1.4・AC3.1.5・AC3.1.6・AC3.1.7・AC3.1.8 | AC3.1.7 |
| FR3.2 | **uncovered** | AC3.1.3・AC3.1.1・AC3.1.2・AC3.1.4・AC3.1.5・AC3.1.6・AC3.1.7・AC3.1.8・AC1.5.1・AC1.5.2・AC1.5.3・AC1.5.4 | AC3.1.7 |
| FR3.3 | **uncovered** | AC3.1.3・AC3.1.1・AC3.1.2・AC3.1.4・AC3.1.5・AC3.1.6・AC3.1.7・AC3.1.8 | AC3.1.7 |
| FR3.4 | **uncovered** | AC3.1.3・AC3.1.1・AC3.1.2・AC3.1.4・AC3.1.5・AC3.1.6・AC3.1.7・AC3.1.8・AC3.2.1・AC3.2.2・AC3.2.3・AC3.2.4・AC3.2.5・AC3.2.6・AC3.2.7・AC3.5.1・AC3.5.2・AC3.5.3・AC3.5.4・AC3.5.5 | AC3.1.7・AC3.5.4 |
| FR4.1 | **uncovered** | AC3.4.1・AC3.4.2・AC3.4.3・AC3.4.4・AC3.4.5 | AC3.4.5 |
| FR5.1 | **uncovered** | AC4.1.1・AC4.1.5・AC4.1.2・AC4.1.3・AC4.1.4・AC4.1.6・AC4.1.7・AC4.1.8・AC4.1.9 | AC4.1.6 |
| FR5.2 | **uncovered** | AC4.1.1・AC4.1.5・AC4.1.2・AC4.1.3・AC4.1.4・AC4.1.6・AC4.1.7・AC4.1.8・AC4.1.9 | AC4.1.6 |
| FR5.3 | **uncovered** | AC4.1.1・AC4.1.5・AC4.1.2・AC4.1.3・AC4.1.4・AC4.1.6・AC4.1.7・AC4.1.8・AC4.1.9 | AC4.1.6 |
| FR5.4 | **uncovered** | AC4.1.1・AC4.1.5・AC4.1.2・AC4.1.3・AC4.1.4・AC4.1.6・AC4.1.7・AC4.1.8・AC4.1.9 | AC4.1.6 |
| FR5.5 | **uncovered** | AC4.1.1・AC4.1.5・AC4.1.2・AC4.1.3・AC4.1.4・AC4.1.6・AC4.1.7・AC4.1.8・AC4.1.9 | AC4.1.6 |
| FR6.1 | covered | AC4.2.1・AC4.2.2・AC4.2.3・AC4.2.4・AC4.2.5 | — |
| FR6.2 | covered | AC4.2.1・AC4.2.2・AC4.2.3・AC4.2.4・AC4.2.5 | — |
| FR6.3 | covered | AC4.2.1・AC4.2.2・AC4.2.3・AC4.2.4・AC4.2.5 | — |
| FR6.4 | covered | AC4.2.1・AC4.2.2・AC4.2.3・AC4.2.4・AC4.2.5 | — |
| FR7.1 | covered | AC4.3.1・AC4.3.2・AC4.3.3・AC4.3.4 | — |
| FR7.2 | covered | AC4.3.1・AC4.3.2・AC4.3.3・AC4.3.4 | — |
| FR7.3 | covered | AC4.3.1・AC4.3.2・AC4.3.3・AC4.3.4 | — |
| FR7.4 | covered | AC4.3.1・AC4.3.2・AC4.3.3・AC4.3.4 | — |
| FR8.1 | covered | N/A: user-stories: Dependabot の取り込みは決まりが team.md の Way of Working に既にあり、ストーリーにしない（質問 Q5: A）。専用の短命ブランチで版と lockfile を手元で更新し、./gradlew verify を通して develop に統合し、PR を閉じる手順で扱う | — |
| FR8.2 | covered | N/A: user-stories: Dependabot の取り込みは決まりが team.md の Way of Working に既にあり、ストーリーにしない（質問 Q5: A）。専用の短命ブランチで版と lockfile を手元で更新し、./gradlew verify を通して develop に統合し、PR を閉じる手順で扱う | — |
| NFR1 | covered | U1 NFR1.1・U1 NFR1.14・U1 NFR1.15・U1 NFR1.2・U1 NFR1.3・U1 NFR1.4・U1 NFR1.5・U1 NFR1.6・U1 NFR1.7・U1 NFR1.8・U1 NFR1.9・U1 NFR1.10・U1 NFR1.11・U1 NFR1.12・U1 NFR1.13・U2 NFR1.21・U2 NFR1.31・U2 NFR1.32・U2 NFR1.22・U2 NFR1.23・U2 NFR1.24・U2 NFR1.25・U2 NFR1.26・U2 NFR1.27・U2 NFR1.28・U2 NFR1.29・U2 NFR1.30・U3 NFR1.41・U3 NFR1.47・U3 NFR1.42・U3 NFR1.43・U3 NFR1.44・U3 NFR1.45・U3 NFR1.46 | — |
| NFR2 | **uncovered** | U1 NFR2.1・U1 NFR2.5・U1 NFR2.2・U1 NFR2.3・U1 NFR2.4・U1 NFR2.6・U1 NFR2.9・U1 NFR2.10・U1 NFR2.8・U1 NFR2.7・U2 NFR2.21・U2 NFR2.25・U2 NFR2.22・U2 NFR2.23・U2 NFR2.24・U2 NFR2.28・U2 NFR2.26・U2 NFR2.29・U2 NFR2.27・U3 NFR2.41・U3 NFR2.45・U3 NFR2.42・U3 NFR2.43・U3 NFR2.44 | U1 NFR2.1・U1 NFR2.5・U1 NFR2.3・U1 NFR2.4・U1 NFR2.10・U2 NFR2.21・U2 NFR2.25・U2 NFR2.22・U2 NFR2.23・U2 NFR2.24・U2 NFR2.29・U3 NFR2.45 |
| NFR3 | **uncovered** | U1 NFR3.3・U1 NFR3.5・U1 NFR3.1・U1 NFR3.4・U1 NFR3.7・U1 NFR3.8・U1 NFR3.2・U1 NFR3.6・U2 NFR3.21・U2 NFR3.25・U2 NFR3.24・U2 NFR3.23・U2 NFR3.27・U2 NFR3.28・U2 NFR3.22・U2 NFR3.26 | U1 NFR3.8・U2 NFR3.28 |
| NFR4 | covered | U3 NFR4.41・U3 NFR4.45・U3 NFR4.42・U3 NFR4.43・U3 NFR4.44 | — |
| NFR5 | **uncovered** | U1 NFR5.3・U1 NFR5.1・U1 NFR5.2・U2 NFR5.23・U2 NFR5.21・U2 NFR5.22・U3 NFR5.41・U3 NFR5.43・U3 NFR5.42・U4 NFR5.61・U4 NFR5.62・U4 NFR5.63・U5 NFR5.81 | U1 NFR5.3・U2 NFR5.23・U4 NFR5.62 |
| NFR6 | covered | U1 NFR6.1・U2 NFR6.21・U3 NFR6.41・U3 NFR6.42・U4 NFR6.61 | — |
| NFR7 | covered | U1 NFR7.1・U1 NFR7.3・U1 NFR7.2・U2 NFR7.21・U2 NFR7.22・U3 NFR7.41・U4 NFR7.61 | — |
| NFR8 | **uncovered** | U4 NFR8.66・U4 NFR8.61・U4 NFR8.62・U4 NFR8.68・U4 NFR8.69・U4 NFR8.63・U4 NFR8.64・U4 NFR8.65・U4 NFR8.67・U4 NFR8.70・U4 NFR8.71・U5 NFR8.81・U5 NFR8.82・U5 NFR8.83・U5 NFR8.86・U5 NFR8.87・U5 NFR8.88・U5 NFR8.89・U5 NFR8.90・U5 NFR8.84・U5 NFR8.85 | U4 NFR8.69・U4 NFR8.64 |
| NFR9 | covered | U1 NFR9.1・U2 NFR9.21・U3 NFR9.41・U4 NFR9.61・U4 NFR9.62・U4 NFR9.63・U4 NFR9.64・U4 NFR9.65・U5 NFR9.81・U5 NFR9.82 | — |
| FR2.2a | covered | AC2.1.15・AC2.1.1・AC2.1.2・AC2.1.3・AC2.1.4・AC2.1.5・AC2.1.6・AC2.1.7・AC2.1.8・AC2.1.11・AC2.1.9・AC2.1.10・AC2.1.12・AC2.1.13・AC2.1.14・AC2.1.16 | — |
| FR3.1a | covered | AC3.2.1・AC3.2.2・AC3.2.3・AC3.2.4・AC3.2.5・AC3.2.6・AC3.2.7 | — |
| FR3.2a | covered | AC3.3.2・AC3.3.1・AC3.3.3 | — |

| AC | 網羅（センサー） | 作った単位とファイル |
|---|---|---|
| AC1.4.2 | covered | U1 businessdata/web/ReferenceSecretLeakIT.java |
| AC1.4.7 | covered | U1 businessdata/BusinessDataBoundaryArchitectureTest.java |
| AC2.1.15 | covered | U1 businessdata/web/ReferenceTraceExposureIT.java |
| AC3.3.2 | covered | U2 businessdata/web/ReferenceWriteSecretLeakIT.java |
| AC1.1.1 | covered | U1 businessdata/web/AbstractReferenceReadIT.java・U3 features/tables/references/ReferenceSelectField.test.tsx |
| AC1.1.2 | covered | U1 businessdata/web/AbstractReferenceReadIT.java・U3 features/tables/references/ReferenceSelectField.test.tsx |
| AC1.1.3 | covered | U1 businessdata/web/AbstractReferenceReadIT.java・U3 features/tables/BusinessDataFormPage.test.tsx |
| AC3.2.1 | covered | U2 businessdata/web/AbstractReferenceWriteIT.java |
| AC1.1.4 | covered | U1 businessdata/web/ReferenceTimeoutIT.java・U3 features/tables/BusinessDataFormPage.test.tsx |
| AC1.1.5 | covered | U1 businessdata/web/ReferenceConnectionUsageIT.java |
| AC1.1.6 | covered | U1 businessdata/service/ReferenceLookupTest.java・U3 features/tables/references/ReferenceSelectField.test.tsx |
| AC1.1.7 | covered | U3 features/tables/BusinessDataFormPage.test.tsx |
| AC1.1.8 | covered | U3 features/tables/references/referenceField.test.ts |
| AC1.1.9 | covered | U1 businessdata/web/AbstractReferenceReadIT.java |
| AC1.2.1 | covered | U1 businessdata/web/AbstractReferenceReadIT.java |
| AC1.1.10 | covered | U3 features/tables/references/ReferenceSelectField.test.tsx |
| AC2.2.9 | covered | U3 features/tables/references/LookupPickerDialog.test.tsx |
| AC2.3.3 | covered | U3 features/tables/references/LookupField.test.tsx |
| AC1.2.2 | covered | U1 businessdata/web/AbstractReferenceReadIT.java・U3 features/tables/references/ReadonlyReferenceField.test.tsx |
| AC1.2.3 | covered | U1 businessdata/service/ReferenceLookupTest.java・U3 features/tables/references/ReferenceColumnField.test.tsx |
| AC1.2.4 | covered | U1 businessdata/web/AbstractReferenceReadIT.java・U3 features/tables/references/ReadonlyReferenceField.test.tsx |
| AC1.3.3 | covered | U3 features/tables/references/ReadonlyReferenceField.test.tsx |
| AC1.3.6 | covered | U3 features/tables/references/ReadonlyReferenceField.test.tsx |
| AC1.2.5 | covered | U1 businessdata/repository/RowSqlExecutorTest.java |
| AC1.3.1 | covered | U1 businessdata/web/AbstractReferenceReadIT.java・U3 features/tables/references/referenceField.test.ts |
| AC1.3.2 | covered | U1 businessdata/domain/ReferencePolicyTest.java・U3 features/tables/references/referenceField.test.ts |
| AC1.3.4 | covered | U1 businessdata/domain/ReferencePolicyTest.java・U3 features/tables/references/ReadonlyReferenceField.test.tsx |
| AC1.3.5 | covered | U1 businessdata/web/ReferenceAuthorizationApiIT.java |
| AC1.3.7 | covered | U3 features/tables/references/ReadonlyReferenceField.test.tsx |
| AC1.3.8 | covered | U1 businessdata/web/ReferenceAuthorizationApiIT.java・U3 features/tables/useTableDefinition.test.tsx |
| AC1.4.1 | covered | U1 businessdata/web/ReferenceSameResponseIT.java |
| AC1.4.3 | covered | U1 businessdata/web/ReferenceTimeoutIT.java |
| AC1.4.4 | covered | U1 businessdata/web/AbstractReferenceReadIT.java |
| AC1.4.5 | covered | U1 businessdata/web/AbstractReferenceReadIT.java |
| AC1.4.6 | covered | U1 businessdata/domain/ReferenceTypesTest.java |
| AC1.5.1 | covered | U3 features/tables/BusinessDataFormPage.test.tsx |
| AC1.5.2 | covered | U2 businessdata/web/AbstractReferenceWriteIT.java |
| AC3.1.3 | covered | U2 businessdata/web/AbstractReferenceWriteIT.java |
| AC1.5.3 | covered | U2 businessdata/web/AbstractReferenceWriteIT.java・U3 features/tables/references/referenceField.test.ts |
| AC1.5.4 | covered | U2 businessdata/web/AbstractReferenceWriteIT.java・U3 features/tables/BusinessDataFormPage.test.tsx |
| AC2.1.1 | covered | U1 businessdata/web/ReferenceSameResponseIT.java |
| AC2.1.2 | covered | U1 businessdata/web/AbstractReferenceReadIT.java |
| AC2.1.3 | covered | U1 businessdata/web/AbstractReferenceReadIT.java |
| AC2.1.4 | covered | U1 businessdata/domain/ReferenceRowsRequestValidatorTest.java |
| AC2.2.5 | covered | U3 features/tables/references/pickerSearch.test.ts |
| AC2.1.5 | covered | U1 businessdata/web/AbstractReferenceReadIT.java |
| AC2.1.6 | covered | U1 businessdata/web/ReferenceRequestLimitIT.java |
| AC2.1.7 | covered | U1 businessdata/web/AbstractReferenceReadIT.java |
| AC2.1.8 | covered | U1 businessdata/web/ReferenceAuthorizationApiIT.java |
| AC2.1.11 | covered | U1 businessdata/web/ReferenceSameResponseIT.java |
| AC2.1.9 | covered | U1 businessdata/web/ReferenceSameResponseIT.java |
| AC2.1.10 | covered | U1 businessdata/web/ReferenceAuthorizationApiIT.java |
| AC2.1.12 | covered | U1 businessdata/web/ReferenceAuthorizationApiIT.java |
| AC2.1.13 | covered | U1 businessdata/web/ReferenceTimeoutIT.java |
| AC2.1.14 | covered | U1 ApiAccessArchitectureTest.java |
| AC2.1.16 | covered | U1 businessdata/web/AbstractReferenceReadIT.java |
| AC2.2.1 | covered | U3 features/tables/references/LookupPickerDialog.test.tsx |
| AC2.2.2 | covered | U3 features/tables/references/LookupPickerDialog.test.tsx |
| AC2.2.3 | covered | U3 features/tables/references/LookupPickerDialog.test.tsx |
| AC2.2.4 | covered | U3 features/tables/references/LookupPickerDialog.test.tsx |
| AC2.2.6 | covered | U3 features/tables/references/LookupPickerDialog.test.tsx |
| AC2.2.7 | covered | U3 features/tables/references/LookupField.test.tsx |
| AC2.2.8 | covered | U3 features/tables/references/LookupPickerDialog.test.tsx |
| AC2.2.10 | covered | U3 features/tables/references/LookupPickerDialog.test.tsx |
| AC2.2.11 | covered | U3 features/tables/problemMessage.test.ts |
| AC2.3.1 | covered | U1 businessdata/web/AbstractReferenceReadIT.java・U3 features/tables/references/useReferenceLabels.test.tsx |
| AC2.3.2 | covered | U1 businessdata/web/ReferenceSameResponseIT.java・U3 features/tables/references/useReferenceLabels.test.tsx |
| AC2.3.4 | covered | U1 businessdata/web/AbstractReferenceReadIT.java・U3 features/tables/BusinessDataListPage.test.tsx |
| AC2.4.1 | covered | U3 features/tables/references/LookupPickerDialog.test.tsx |
| AC2.4.2 | covered | U3 features/tables/references/LookupPickerDialog.test.tsx |
| AC2.4.3 | covered | U3 features/tables/references/LookupPickerDialog.test.tsx |
| AC2.4.4 | covered | U3 frontend/e2e/195-reference-picker-accessibility.e2e.ts |
| AC2.4.5 | covered | U3 frontend/e2e/195-reference-picker-accessibility.e2e.ts |
| AC2.4.6 | covered | U3 features/tables/messages.test.ts |
| AC2.5.1 | covered | U3 frontend/e2e/190-reference-picker-flow.e2e.ts |
| AC2.5.2 | covered | U3 frontend/e2e/support/referenceSetup.ts |
| AC2.5.3 | covered | U3 frontend/e2e/190-reference-picker-flow.e2e.ts |
| AC2.5.4 | covered | U3 features/tables/BusinessDataFormPage.test.tsx |
| AC3.1.1 | covered | U2 businessdata/web/AbstractReferenceWriteIT.java |
| AC3.1.2 | covered | U2 businessdata/web/AbstractReferenceWriteIT.java |
| AC3.1.4 | covered | U2 businessdata/web/AbstractReferenceWriteIT.java |
| AC3.1.5 | covered | U2 businessdata/web/AbstractReferenceWriteIT.java |
| AC3.1.6 | covered | U2 businessdata/web/AbstractReferenceWriteIT.java |
| AC3.1.7 | **uncovered** | — |
| AC3.1.8 | covered | U2 businessdata/web/ReferenceWriteTimeoutIT.java |
| AC3.2.2 | covered | U2 businessdata/web/AbstractReferenceWriteIT.java |
| AC3.2.3 | covered | U2 businessdata/web/AbstractReferenceWriteIT.java |
| AC3.2.4 | covered | U2 businessdata/domain/ReferenceWritePolicyPropertyTest.java |
| AC3.2.5 | covered | U2 businessdata/web/AbstractReferenceWriteIT.java |
| AC3.2.6 | covered | U2 businessdata/web/AbstractReferenceWriteIT.java |
| AC3.2.7 | covered | U2 businessdata/web/ReferenceWriteConnectionUsageIT.java |
| AC3.3.1 | covered | U2 businessdata/web/ReferenceWriteAuditIT.java |
| AC3.3.3 | covered | U2 businessdata/web/ReferenceWriteAuditIT.java |
| AC3.4.1 | covered | U1 businessdata/repository/RowSqlExecutorTest.java |
| AC3.4.2 | covered | U1 businessdata/web/AbstractReferenceReadIT.java |
| AC3.4.3 | covered | U1 businessdata/web/AbstractReferenceReadIT.java |
| AC3.4.4 | covered | U1 businessdata/web/AbstractReferenceReadIT.java |
| AC3.4.5 | **uncovered** | — |
| AC3.5.1 | covered | U1 businessdata/web/ReferenceConnectionUsageIT.java |
| AC3.5.2 | covered | U1 businessdata/web/ReferenceConnectionUsageIT.java |
| AC3.5.3 | covered | U1 businessdata/web/ReferenceTimeoutIT.java |
| AC3.5.4 | **uncovered** | — |
| AC3.5.5 | covered | U1 backend/build.gradle.kts |
| AC4.1.1 | covered | U4 perfEvidenceRedactor.test.ts |
| AC4.1.5 | covered | U4 perfEvidenceRedactor.test.ts |
| AC4.1.2 | covered | U4 perfEvidenceRedactor.test.ts |
| AC4.1.3 | covered | U4 perfEvidenceRedactorCli.test.ts |
| AC4.1.4 | covered | U4 perfEvidenceRedactorCli.test.ts |
| AC4.1.6 | **uncovered** | — |
| AC4.1.7 | covered | U4 perfEvidenceScan.test.ts |
| AC4.1.8 | covered | U4 perfEvidenceRedactorCli.test.ts |
| AC4.1.9 | covered | U4 perf/README.md |
| AC4.2.1 | covered | U5 aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u5-container-perf-docs/code-generation/container-swap-record.md |
| AC4.2.2 | covered | U5 compose.yaml |
| AC4.2.3 | covered | U5 aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u5-container-perf-docs/code-generation/container-swap-record.md |
| AC4.2.4 | covered | U5 aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u5-container-perf-docs/code-generation/container-swap-record.md |
| AC4.2.5 | covered | U5 aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u5-container-perf-docs/code-generation/container-swap-record.md |
| AC4.3.1 | covered | U5 perf/README.md |
| AC4.3.2 | covered | U5 perf/README.md |
| AC4.3.3 | covered | U5 perf/k6/scenarios.js |
| AC4.3.4 | covered | U5 perf/k6/scenarios.js |
| AC2.3.5 | covered | N/A: u1-reference-read/code-generation: 初稿の ID（stories.md の「群2 の ID の対応」の表）。最終版の AC2.1.10〜AC2.1.12 で追う（機能設計の traceability と同じ） / u3-reference-screens/code-generation: 初稿の ID |
