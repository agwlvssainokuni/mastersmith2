# 単位をまたぐ網羅の確かめ — Intent 261016-business-data-views

## 1. 判定

**合格（条件つき）**。覆われていない ID は 0 件。前の版で覆われていなかった AC3.4.5 は、この段で足した結合テスト `AbstractBusinessDataReadIT#readsLeaveNoAudit`（3種類の対象DB、各 17 件が通過。`test-results.md` 8.2）で覆われた（4節）。Code Generation が「Build and Test」へ `Deferred` にした ID（U5 の BR3.4・BR3.5・BR4.2・BR4.3、U4 の NFR7.2・NFR7.3、U3 の NFR1.7）は、この段で確かめて Met にした。条件として、後の段（Performance Validation・Observability Setup・次の Intent）への `Deferred` が残る（6節）。承認済みの単位の `traceability.json` は書き換えていない。

| 区分 | 数 | Covered | Covered（一部 Deferred） | Deferred のみ | 覆われていない |
|---|---|---|---|---|---|
| 要件の FR（枝番を含む） | 85 | 79 | 6（FR4・FR4.1〜FR4.4・FR14.5。どれも AC1.3.7） | 0 | 0 |
| 要件の NFR（NFR1〜NFR8 と NFR4.1〜NFR4.4） | 12 | 8 | 4（NFR1・NFR2・NFR3・NFR8） | 0 | 0 |
| ストーリーの受け入れ基準（三つの段の AC） | 146 | 145 | — | 1（AC1.3.7、Performance Validation） | 0 |

- 「Covered（一部 Deferred）」の FR・NFR は、2段目の ID の一部が後の段への `Deferred`（6節）。目標は緩めていない。
- 「Covered」のうち、この段で確かめて覆ったもの: FR12・FR12.1〜FR12.4（AC3.4.5）、FR16・FR16.1・FR16.3・FR16.4（`memory-peak-measurement.md`）、NFR7（U4 の NFR7.2・NFR7.3）。各行の注記に書いた。

## 2. 確かめ方

`project.md` の決まりのとおり、要件の ID から2段の連鎖でたどった（Code Generation の `traceability.json` は要件の ID を直接持たず、単位ごとの ID で持つため）。

- **FR**: 要件の FR → ストーリーの段の `inception/user-stories/traceability.json`（FR → US）→ その US の受け入れ基準（AC）→ 各単位の `code-generation/traceability.json` の AC の行。FR15・FR16 はストーリーにしない持ち越しのため、U5 の `functional-design/traceability.json`（FR → BR）→ U5 の `code-generation/traceability.json` の BR の行。
- **NFR**: 要件の NFR → 各単位の `nfr-requirements/traceability.json`（NFR → 単位の枝番 NFRx.y）→ 同じ単位の `code-generation/traceability.json` の枝番の行。U4 の枝番のうち B7 に回したもの（NFR4.8・NFR5.1〜NFR5.3）は、U3 の `traceability.json` の `U4-NFR*` の行でたどった。
- **AC**: `inception/user-stories/stories.md` の三つの段の AC（146 件）を、5つの単位の `code-generation/traceability.json` で探した。
- `OK` の行は、target に書かれた道（「、」で区切ったものはすべて）がリポジトリに実在することを確かめた（注記の括弧を除いて読んだ）。Code Generation の段の `construction/code-generation/traceability.json` は無い（単位ごとに持つ）。

## 3. 持ち主の単位

| 単位 | `traceability.json` の行（OK・Deferred・N/A） | 主に受け持つもの |
|---|---|---|
| U1 u1-businessdata-read | 165・22・0 | US1.x の読み取り、US3.x の判定の順・SQL・漏えい、NFR1〜NFR4・NFR7・NFR8 の読み取りの分 |
| U2 u2-businessdata-write | 150・20・0 | US2.x、US3.4 の監査、NFR の書き込みの分 |
| U3 u3-businessdata-screens | 125・44・12 | 画面の AC、NFR1.7〜NFR8.7（画面）、U4 から受けた B7 の分 |
| U4 u4-a11y-carryover | 17・9・3 | US4.1（AC4.1.1・AC4.1.2・AC4.1.4）、NFR4.x・NFR5.x・NFR6.x・NFR7.x（axe・名前） |
| U5 u5-carryover-tests | 23・4・0 | FR15・FR16（BR1〜BR4）、NFR4.5〜NFR4.9・NFR7.1〜NFR7.3 |

## 4. 覆われていない ID と、この段で覆ったもの

覆われていない ID は無い。前の版で覆われていなかった ID と、この段で覆った根拠を書く。

| ID | 中身（`stories.md`） | 前の状態 | この段で覆った根拠 |
|---|---|---|---|
| AC3.4.5 | 一覧・詳細を読んだ、または詳細が拒否された（409・403・404）ときは、監査に残らない（FR12.2） | U2 の `traceability.json` が「U1 u1-businessdata-read（読み取りとその拒否を監査に残さない）」への `Deferred` にしたが、U1 の `traceability.json` に AC3.4.5 の行が無かった（単位の割り当てで US3.4 は U2 だけ。`inception/units-generation/unit-of-work-story-map.md`） | 依頼者の決定で、この段の直し（`22de65f`）で `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataReadIT.java` に `readsLeaveNoAudit`（「reads and their rejections (401, 400, 403, 404, 409) leave no audit row at all」）を足した。定義・一覧・詳細の 200 と、401・400・403・404・409 の拒否の後に `audit_events` のすべての行の数が変わらないことを確かめる。子のクラス `PostgresBusinessDataReadIT`・`MysqlBusinessDataReadIT`・`MariadbBusinessDataReadIT` の3種類で、各 17 件が通過し、verify の中でも通過（`test-results.md` 8.2・8.4） |

- U1・U2 の承認済みの `traceability.json` は書き換えていない。AC3.4.5 の持ち主は、この段（Build and Test）が足したテストとしてこの文書に記録する。

## 5. 記録の食い違い

承認済みの単位の記録は書き換えていない。見つかったものを、判定の根拠とともに書く（`project.md` の Change Control）。U1・U2 の `traceability.json` のずれは、依頼者の決定で書き換えずに、この表を正しい根拠とする。

| 記録 | 食い違い | 判定への響き |
|---|---|---|
| U2 の `code-generation/traceability.json` | NFR 要件の文書のその ID の中身と target がずれている（隣の ID の中身を指す形）。下の表 | どの target も実在し、機械の判定は `OK` のまま。中身に合うテストも実在し通過している。`build-and-test-summary.md` の照合の表は下の正しい根拠を使った |
| U1 の `code-generation/traceability.json` | NFR1.2（場面の判定の決まり）の target の説明が「検索の場面 bizSearchContains・bizSearchLastSorted を含む p95」、NFR3.2（3種類で測る）の説明が「メモリ。手順は perf/README.md」 | どちらも Performance Validation への `Deferred` で、持ち主は合っている。正しい読み: NFR1.2 は台本の判定の決まり（Code Generation の B2 で `k6 inspect` と試し走りを確かめ済み、照合の表の U1-NFR1.2 は Met）、NFR3.2 は NFR1.1 の4場面を3種類の対象DB で流すこと（Performance Validation） |
| U1・U2 の `code-generation/traceability.json` | AC3.4.5 の行が U1 に無い（U2 は U1 への `Deferred`） | 4節のとおり、この段で足したテストで覆った |
| `README.md` 38 行・`perf/README.md` の U5 の節 | make-you-chic-ui の固定先が `a9309c4` のまま、U5 の節の「Performance Validation の段が無いため」 | この段の直し（`test-results.md` 8.3）で直した。網羅には響かない |

U2 の target のずれと、正しい根拠（どれも `backend/src/test/java/cherry/mastersmith/` の下で、実在し verify で通過）:

| ID | NFR 要件の中身 | traceability.json の target | 正しい根拠 |
|---|---|---|---|
| NFR1.6 | 検証の全体が実時間で 400 ms 以内 | `businessdata/web/BusinessDataWriteTimeoutIT.java` | `businessdata/domain/DeadlinePatternTimingTest.java` |
| NFR4.1 | 書き込みの SQL の組み立て | `businessdata/web/BusinessDataWriteAuthorizationApiIT.java` | `businessdata/BusinessDataBoundaryArchitectureTest.java`・`backend/config/spotbugs-exclude.xml`・`businessdata/web/AbstractBusinessDataWriteIT.java` |
| NFR4.4 | 書き込みの時間の上限 | `businessdata/web/BusinessDataWriteSecretLeakIT.java` | `businessdata/web/BusinessDataWriteTimeoutIT.java` |
| NFR4.7 | pattern の照合の時間の上限 | `businessdata/web/AbstractBusinessDataWriteIT.java`・`businessdata/repository/RowSqlFailureClassifierTest.java` | `businessdata/domain/DeadlinePatternMatcherTest.java`・`businessdata/domain/DeadlinePatternMatcherPropertyTest.java` |
| NFR4.10 | 認可・一括代入・IDOR | `businessdata/domain/DeadlinePatternMatcherTest.java` | `businessdata/web/BusinessDataWriteAuthorizationApiIT.java`・`ApiAccessConsistencyIT.java` |
| NFR4.11 | 書き込みの設定の欠け | `businessdata/BusinessDataBoundaryArchitectureTest.java`・`backend/config/spotbugs-exclude.xml` | `businessdata/web/BusinessDataWriteNotConfiguredIT.java` |
| NFR7.5 | 業務データの必須のテスト（書き込みの分） | `businessdata/web/BusinessDataWriteTimeoutIT.java` | `businessdata/web/AbstractBusinessDataWriteIT.java` と3種類の DB の子のクラス、`BusinessDataWriteAuthorizationApiIT.java`・`BusinessDataWriteAuditIT.java`・`BusinessDataWriteSecretLeakIT.java` ほか（U2 の `traceability.json` の AC の行） |

## 6. Deferred の行

| ID | 持ち主 | 中身 |
|---|---|---|
| AC1.3.7 | Performance Validation | 1ページ 100 件 × 100 カラム・全体の件数の応答時間（U1 の NFR1.1・NFR1.3、場面 `bizSearchFirst`） |
| FR16.4・FR16（BR3.4・BR3.5）・FR16.1（BR3.4） | Build and Test（この段で確かめた） | 既定の2回 0.8731・0.8696 で `memory.peak / memory.max < 0.9` が Met。条件のそろえも Met（`memory-peak-measurement.md`） |
| FR16・FR16.3（BR4.2・BR4.3） | Build and Test（この段で諮った）、取り込みは後の Intent | 試しの2回（0.7400・0.7175）で効いた。`RETENTION_TIME=0` を取り込むかは、依頼者の決定で後の Intent で決める |
| NFR1（U1 の NFR1.1〜NFR1.3、U2 の NFR1.4・NFR1.5） | Performance Validation | 業務データの API の p95 |
| NFR1（U3 の NFR1.7） | Build and Test | 画面の時間。この段で B6 の測りの値で Met と判定した（`build-and-test-summary.md` の U3-NFR1.7a〜e） |
| NFR2（U1 の NFR2.4、U2 の NFR2.7〜NFR2.9） | Performance Validation | プールの見積もり・大きな本文のメモリ・監査の行の増え方 |
| NFR3（U1 の NFR3.2、U2 の NFR3.4） | Performance Validation | 3種類の対象DB で測ること |
| NFR7（U4 の NFR7.2・NFR7.3） | Build and Test（この段で確かめた） | E2E・verify・CI の時間はこの段で Met と判定した（CI の検査の job 28分37秒〜32分49秒） |
| NFR8（U1 の NFR8.1・NFR8.3、U2 の NFR8.5・NFR8.6） | Observability Setup | `ms-forbidden` のしきい値、ダッシュボードの区画、既存の警報で拾えること |
| AC4.1.2・AC4.1.4 の依頼（5）の分 | 次の Intent | make-you-chic-ui の固定先の2回目の更新と E2E 080 の違反の部品の取り込み（依頼（5）が上流の `main` に無い）。B3 の分（`b0a1e79` への更新とトップバーの名前）は U4 で `OK` |

## 7. 要件の FR・NFR の網羅

| ID | 判定 | 2段の連鎖の1段目 | 2段目の数 | Deferred の中身 |
|---|---|---|---|---|
| FR1 | Covered | US1.1 | AC 9 件（OK 9） | — |
| FR1.1 | Covered | US1.1 | AC 9 件（OK 9） | — |
| FR1.2 | Covered | US1.1 | AC 9 件（OK 9） | — |
| FR1.3 | Covered | US1.1・US3.3 | AC 19 件（OK 19） | — |
| FR2 | Covered | US3.1・US3.5 | AC 12 件（OK 12） | — |
| FR2.1 | Covered | US3.5 | AC 2 件（OK 2） | — |
| FR2.2 | Covered | US3.1 | AC 10 件（OK 10） | — |
| FR2.2a | Covered | US3.1・US1.4 | AC 18 件（OK 18） | — |
| FR2.3 | Covered | US3.5・US2.1・US2.2・US2.3 | AC 31 件（OK 31） | — |
| FR2.4 | Covered | US3.1・US2.1・US2.2 | AC 33 件（OK 33） | — |
| FR2.5 | Covered | US3.5 | AC 2 件（OK 2） | — |
| FR2.6 | Covered | US3.5 | AC 2 件（OK 2） | — |
| FR3 | Covered | US1.2・US1.1 | AC 23 件（OK 23） | — |
| FR3.1 | Covered | US1.1 | AC 9 件（OK 9） | — |
| FR3.2 | Covered | US1.2 | AC 14 件（OK 14） | — |
| FR3.3 | Covered | US1.2 | AC 14 件（OK 14） | — |
| FR3.4 | Covered | US1.2 | AC 14 件（OK 14） | — |
| FR3.5 | Covered | US1.2 | AC 14 件（OK 14） | — |
| FR3.6 | Covered | US1.2 | AC 14 件（OK 14） | — |
| FR4 | Covered（一部 Deferred） | US1.3 | AC 16 件（OK 15・Deferred 1） | AC1.3.7（Performance Validation） |
| FR4.1 | Covered（一部 Deferred） | US1.3 | AC 16 件（OK 15・Deferred 1） | AC1.3.7（Performance Validation） |
| FR4.2 | Covered（一部 Deferred） | US1.3 | AC 16 件（OK 15・Deferred 1） | AC1.3.7（Performance Validation） |
| FR4.3 | Covered（一部 Deferred） | US1.3 | AC 16 件（OK 15・Deferred 1） | AC1.3.7（Performance Validation） |
| FR4.4 | Covered（一部 Deferred） | US1.3 | AC 16 件（OK 15・Deferred 1） | AC1.3.7（Performance Validation） |
| FR5 | Covered | US1.4 | AC 8 件（OK 8） | — |
| FR5.1 | Covered | US1.4 | AC 8 件（OK 8） | — |
| FR5.2 | Covered | US1.4 | AC 8 件（OK 8） | — |
| FR5.3 | Covered | US1.4・US3.2 | AC 15 件（OK 15） | — |
| FR6 | Covered | US2.1 | AC 10 件（OK 10） | — |
| FR6.1 | Covered | US2.1 | AC 10 件（OK 10） | — |
| FR6.2 | Covered | US2.1 | AC 10 件（OK 10） | — |
| FR6.2a | Covered | US2.1 | AC 10 件（OK 10） | — |
| FR6.3 | Covered | US2.1・US3.2 | AC 17 件（OK 17） | — |
| FR6.4 | Covered | US2.1 | AC 10 件（OK 10） | — |
| FR6.5 | Covered | US2.1 | AC 10 件（OK 10） | — |
| FR7 | Covered | US2.2 | AC 13 件（OK 13） | — |
| FR7.1 | Covered | US2.2 | AC 13 件（OK 13） | — |
| FR7.2 | Covered | US2.2 | AC 13 件（OK 13） | — |
| FR7.3 | Covered | US2.2 | AC 13 件（OK 13） | — |
| FR7.4 | Covered | US2.2 | AC 13 件（OK 13） | — |
| FR8 | Covered | US2.3 | AC 6 件（OK 6） | — |
| FR8.1 | Covered | US2.3 | AC 6 件（OK 6） | — |
| FR8.2 | Covered | US2.3 | AC 6 件（OK 6） | — |
| FR9 | Covered | US3.2 | AC 7 件（OK 7） | — |
| FR9.1 | Covered | US3.2 | AC 7 件（OK 7） | — |
| FR9.2 | Covered | US3.2 | AC 7 件（OK 7） | — |
| FR9.3 | Covered | US3.2・US3.1 | AC 17 件（OK 17） | — |
| FR10 | Covered | US2.4・US2.5 | AC 10 件（OK 10） | — |
| FR10.1 | Covered | US2.4 | AC 8 件（OK 8） | — |
| FR10.2 | Covered | US2.5 | AC 2 件（OK 2） | — |
| FR10.3 | Covered | US2.5 | AC 2 件（OK 2） | — |
| FR10.4 | Covered | US2.4 | AC 8 件（OK 8） | — |
| FR10.5 | Covered | US2.4・US1.2 | AC 22 件（OK 22） | — |
| FR11 | Covered | US1.5 | AC 8 件（OK 8） | — |
| FR11.1 | Covered | US1.5 | AC 8 件（OK 8） | — |
| FR11.2 | Covered | US1.5 | AC 8 件（OK 8） | — |
| FR11.3 | Covered | US1.5 | AC 8 件（OK 8） | — |
| FR11.4 | Covered | US1.5 | AC 8 件（OK 8） | — |
| FR11.5 | Covered | US1.5 | AC 8 件（OK 8） | — |
| FR12 | Covered | US3.4 | AC 8 件（OK 7・この段で覆った 1） | AC3.4.5 はこの段のテストで覆った（4節） |
| FR12.1 | Covered | US3.4・US2.1・US2.2・US2.3 | AC 37 件（OK 36・この段で覆った 1） | AC3.4.5 はこの段のテストで覆った（4節） |
| FR12.2 | Covered | US3.4 | AC 8 件（OK 7・この段で覆った 1） | AC3.4.5 はこの段のテストで覆った（4節。FR12.2 は AC3.4.5 の直接の出典） |
| FR12.3 | Covered | US3.4 | AC 8 件（OK 7・この段で覆った 1） | AC3.4.5 はこの段のテストで覆った（4節） |
| FR12.4 | Covered | US3.4・US3.2・US2.5 | AC 17 件（OK 16・この段で覆った 1） | AC3.4.5 はこの段のテストで覆った（4節） |
| FR13 | Covered | US3.3 | AC 10 件（OK 10） | — |
| FR13.1 | Covered | US3.3・US1.1 | AC 19 件（OK 19） | — |
| FR13.2 | Covered | US3.3 | AC 10 件（OK 10） | — |
| FR13.3 | Covered | US3.3 | AC 10 件（OK 10） | — |
| FR13.4 | Covered | US3.3 | AC 10 件（OK 10） | — |
| FR14 | Covered | US4.1 | AC 6 件（OK 6） | — |
| FR14.1 | Covered | US4.1 | AC 6 件（OK 6） | — |
| FR14.2 | Covered | US4.1 | AC 6 件（OK 6） | AC4.1.2・AC4.1.4 の依頼（5）の分は次の Intent（6節） |
| FR14.3 | Covered | US4.1 | AC 6 件（OK 6） | — |
| FR14.3a | Covered | US4.1 | AC 6 件（OK 6） | — |
| FR14.4 | Covered | US4.1 | AC 6 件（OK 6） | — |
| FR14.5 | Covered（一部 Deferred） | US4.1・US1.3 | AC 22 件（OK 21・Deferred 1） | AC1.3.7（Performance Validation） |
| FR14.6 | Covered | US4.1 | AC 6 件（OK 6） | — |
| FR15 | Covered | U5 の BR1.1〜BR1.6・BR2.1〜BR2.5 | BR 11 件（OK 11） | CI の試行ごとの結果は `test-results.md` 6節（直した2件は B5 の後の CI で落ちていない） |
| FR15.1 | Covered | U5 の BR1.1〜BR1.4・BR2.1〜BR2.3 | BR 7 件（OK 7） | — |
| FR15.2 | Covered | U5 の BR1.5・BR1.6・BR2.4・BR2.5 | BR 4 件（OK 4） | — |
| FR16 | Covered | U5 の BR3.1〜BR3.5・BR4.1〜BR4.3 | BR 8 件（OK 4・この段で確かめた 4） | BR3.4・BR3.5・BR4.2・BR4.3 はこの段の測りで確かめた（`memory-peak-measurement.md`）。`RETENTION_TIME=0` の取り込みは後の Intent |
| FR16.1 | Covered | U5 の BR3.1・BR3.4 | BR 2 件（OK 1・この段で確かめた 1） | BR3.4 はこの段の測りで確かめた |
| FR16.2 | Covered | U5 の BR3.2・BR3.3 | BR 2 件（OK 2） | — |
| FR16.3 | Covered | U5 の BR4.1・BR4.2・BR4.3 | BR 3 件（OK 1・この段で確かめた 2） | `RETENTION_TIME` が試しの回 0・既定の回 45000（BR4.1）。試しは効き、取り込むかは依頼者の決定で後の Intent で決める（BR4.2・BR4.3） |
| FR16.4 | Covered | U5 の BR3.5 | BR 1 件（この段で確かめた 1） | 既定の2回 0.8731・0.8696 で Met（`memory-peak-measurement.md`） |
| NFR1 | Covered（一部 Deferred） | 単位の枝番 | 枝番 10 件（OK 4・Deferred 6）、N/A の単位 2 | U1 の NFR1.1〜NFR1.3・U2 の NFR1.4・NFR1.5（Performance Validation）、U3 の NFR1.7（この段で判定、Met） |
| NFR2 | Covered（一部 Deferred） | 単位の枝番 | 枝番 11 件（OK 7・Deferred 4）、N/A の単位 2 | U1 の NFR2.4・U2 の NFR2.7〜NFR2.9（Performance Validation） |
| NFR3 | Covered（一部 Deferred） | 単位の枝番 | 枝番 7 件（OK 5・Deferred 2）、N/A の単位 2 | U1 の NFR3.2・U2 の NFR3.4（Performance Validation） |
| NFR4 | Covered | 単位の枝番 | 枝番 38 件（OK 38） | — |
| NFR4.1 | Covered | 単位の枝番 | 枝番 2 件（OK 2）、N/A の単位 2 | — |
| NFR4.2 | Covered | 単位の枝番 | 枝番 5 件（OK 5）、N/A の単位 1 | — |
| NFR4.3 | Covered | 単位の枝番 | 枝番 4 件（OK 4）、N/A の単位 2 | — |
| NFR4.4 | Covered | 単位の枝番 | 枝番 6 件（OK 6）、N/A の単位 2 | — |
| NFR5 | Covered | 単位の枝番 | 枝番 10 件（OK 10）、N/A の単位 3 | — |
| NFR6 | Covered | 単位の枝番 | 枝番 4 件（OK 4）、N/A の単位 3 | — |
| NFR7 | Covered | 単位の枝番 | 枝番 22 件（OK 20・この段で確かめた 2） | U4 の NFR7.2・NFR7.3 はこの段で E2E・verify・CI の時間を確かめた |
| NFR8 | Covered（一部 Deferred） | 単位の枝番 | 枝番 7 件（OK 3・Deferred 4）、N/A の単位 2 | U1 の NFR8.1・NFR8.3、U2 の NFR8.5・NFR8.6（Observability Setup） |

- FR14.2 の行の注記: 依頼（5）の分（固定先の2回目の更新）は AC4.1.2・AC4.1.4 の U3 の行で次の Intent への `Deferred`。B3 の分は U4 の行で `OK` のため、機械の判定は Covered。

## 8. 受け入れ基準（AC）の網羅

| AC | 判定 | 持ち主の単位 | target（実在を確かめた道の1つ目） |
|---|---|---|---|
| AC1.1.1 | Covered | U3 | `frontend/src/features/tables/BusinessDataListPage.tsx` |
| AC1.1.2 | Covered | U3 | `frontend/src/features/tables/BusinessDataListPage.tsx` |
| AC1.1.3 | Covered | U1・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/BusinessDataAuthorizationApiIT.java` |
| AC1.1.4 | Covered | U1・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/service/TableAccessResolverTest.java` |
| AC1.1.5 | Covered | U1・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/service/TableAccessResolverTest.java` |
| AC1.1.6 | Covered | U3 | `frontend/src/features/tables/BusinessDataListPage.tsx` |
| AC1.1.7 | Covered | U1・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/service/TableAccessResolverTest.java` |
| AC1.1.8 | Covered | U1・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/BusinessDataAuthorizationApiIT.java` |
| AC1.1.9 | Covered | U1・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/TableAccessPolicyTest.java` |
| AC1.2.1 | Covered | U1・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/SearchRequestValidatorTest.java` |
| AC1.2.2 | Covered | U1・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/SearchRequestValidatorTest.java` |
| AC1.2.3 | Covered | U1・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/ContainsPatternTest.java` |
| AC1.2.4 | Covered | U1 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/SearchOperatorPolicyTest.java` |
| AC1.2.5 | Covered | U1・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/SearchRequestValidatorTest.java` |
| AC1.2.6 | Covered | U1・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/BusinessDataReadControllerTest.java` |
| AC1.2.7 | Covered | U1・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/BusinessDataReadControllerTest.java` |
| AC1.2.8 | Covered | U1・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/SearchOperatorPolicyTest.java` |
| AC1.2.9 | Covered | U1・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/TableAccessPolicyTest.java` |
| AC1.2.10 | Covered | U3 | `frontend/src/features/tables/SearchPanel.tsx` |
| AC1.2.11 | Covered | U3 | `frontend/src/features/tables/SearchPanel.tsx` |
| AC1.2.12 | Covered | U3 | `frontend/src/features/tables/SearchPanel.tsx` |
| AC1.2.13 | Covered | U1・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/ContainsPatternTest.java` |
| AC1.2.14 | Covered | U1 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/SearchRequestValidatorTest.java` |
| AC1.3.1 | Covered | U1・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataReadIT.java` |
| AC1.3.2 | Covered | U1・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/SearchRequestValidatorTest.java` |
| AC1.3.3 | Covered | U1・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataReadIT.java` |
| AC1.3.4 | Covered | U1 | `backend/src/test/java/cherry/mastersmith/businessdata/repository/RowSqlExecutorTest.java` |
| AC1.3.5 | Covered | U1 | `backend/src/test/java/cherry/mastersmith/businessdata/repository/RowDialectTest.java` |
| AC1.3.6 | Covered | U1 | `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataReadIT.java` |
| AC1.3.7 | Deferred | — | U1: Performance Validation（場面 `bizSearchFirst` と全体の件数。台本は `perf/k6/scenarios.js`）。U3: U1 への Deferred |
| AC1.3.8 | Covered | U1 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/SearchRequestValidatorTest.java` |
| AC1.3.9 | Covered | U1 | `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataReadIT.java` |
| AC1.3.10 | Covered | U1 | `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataReadIT.java` |
| AC1.3.11 | Covered | U1 | `backend/src/test/java/cherry/mastersmith/businessdata/repository/RowSqlExecutorTest.java` |
| AC1.3.12 | Covered | U1・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataReadIT.java` |
| AC1.3.13 | Covered | U3 | `frontend/src/features/tables/BusinessDataTable.tsx` |
| AC1.3.14 | Covered | U1・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataReadIT.java` |
| AC1.3.15 | Covered | U3 | `frontend/src/features/tables/BusinessDataTable.tsx` |
| AC1.3.16 | Covered | U1 | `backend/src/test/java/cherry/mastersmith/businessdata/repository/RowSqlExecutorTest.java` |
| AC1.4.1 | Covered | U1・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/TableAccessPolicyTest.java` |
| AC1.4.2 | Covered | U1・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/SearchRequestValidatorTest.java` |
| AC1.4.3 | Covered | U1・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataReadIT.java` |
| AC1.4.4 | Covered | U1・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataReadIT.java` |
| AC1.4.5 | Covered | U1・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/TableAccessPolicyTest.java` |
| AC1.4.6 | Covered | U3 | `frontend/src/features/tables/BusinessDataDetailPage.tsx` |
| AC1.4.7 | Covered | U1・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/TableAccessPolicyTest.java` |
| AC1.4.8 | Covered | U1 | `backend/src/test/java/cherry/mastersmith/businessdata/repository/ColumnValueCodecTest.java` |
| AC1.5.1 | Covered | U1・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/TableAccessPolicyTest.java` |
| AC1.5.2 | Covered | U3 | `frontend/src/features/tables/valueFormat.ts` |
| AC1.5.3 | Covered | U1・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/repository/ColumnValueCodecTest.java` |
| AC1.5.4 | Covered | U3 | `frontend/src/features/tables/valueFormat.ts` |
| AC1.5.5 | Covered | U1・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataReadIT.java` |
| AC1.5.6 | Covered | U1・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataReadIT.java` |
| AC1.5.7 | Covered | U1・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/repository/ColumnValueCodecTest.java` |
| AC1.5.8 | Covered | U1・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/TableAccessPolicyTest.java` |
| AC2.1.1 | Covered | U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataWriteIT.java` |
| AC2.1.2 | Covered | U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/BusinessDataWriteAuthorizationApiIT.java` |
| AC2.1.3 | Covered | U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataReadIT.java` |
| AC2.1.4 | Covered | U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataWriteIT.java` |
| AC2.1.5 | Covered | U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataWriteIT.java` |
| AC2.1.6 | Covered | U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataWriteIT.java` |
| AC2.1.7 | Covered | U2 | `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataWriteIT.java` |
| AC2.1.8 | Covered | U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataWriteIT.java` |
| AC2.1.9 | Covered | U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataWriteIT.java` |
| AC2.1.10 | Covered | U3 | `frontend/src/features/tables/BusinessDataFormPage.tsx` |
| AC2.2.1 | Covered | U3 | `frontend/src/features/tables/BusinessDataFormPage.tsx` |
| AC2.2.2 | Covered | U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataWriteIT.java` |
| AC2.2.3 | Covered | U2 | `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataWriteIT.java` |
| AC2.2.4 | Covered | U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/BusinessDataWriteAuditIT.java` |
| AC2.2.5 | Covered | U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/BusinessDataWriteAuthorizationApiIT.java` |
| AC2.2.6 | Covered | U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/BusinessDataWriteAuthorizationApiIT.java` |
| AC2.2.7 | Covered | U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/WriteRequestValidatorTest.java` |
| AC2.2.8 | Covered | U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataWriteIT.java` |
| AC2.2.9 | Covered | U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/WriteRequestValidatorTest.java` |
| AC2.2.10 | Covered | U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataWriteIT.java` |
| AC2.2.11 | Covered | U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/WriteRequestValidatorTest.java` |
| AC2.2.12 | Covered | U3 | `frontend/src/features/tables/BusinessDataFormPage.tsx` |
| AC2.2.13 | Covered | U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/BusinessDataWriteAuthorizationApiIT.java` |
| AC2.3.1 | Covered | U3 | `frontend/src/features/tables/DeleteConfirmModal.tsx` |
| AC2.3.2 | Covered | U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataWriteIT.java` |
| AC2.3.3 | Covered | U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/BusinessDataWriteAuthorizationApiIT.java` |
| AC2.3.4 | Covered | U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataWriteIT.java` |
| AC2.3.5 | Covered | U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/BusinessDataWriteAuditIT.java` |
| AC2.3.6 | Covered | U2 | `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataWriteIT.java` |
| AC2.4.1 | Covered | U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/WriteRequestValidatorTest.java` |
| AC2.4.2 | Covered | U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/WriteRequestValidatorTest.java` |
| AC2.4.3 | Covered | U2 | `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataWriteIT.java` |
| AC2.4.4 | Covered | U2 | `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataWriteIT.java` |
| AC2.4.5 | Covered | U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/DeadlinePatternMatcherTest.java` |
| AC2.4.6 | Covered | U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/WriteRequestValidatorTest.java` |
| AC2.4.7 | Covered | U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataWriteIT.java` |
| AC2.4.8 | Covered | U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataReadIT.java` |
| AC2.5.1 | Covered | U2 | `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataWriteIT.java` |
| AC2.5.2 | Covered | U2 | `backend/src/test/java/cherry/mastersmith/businessdata/web/BusinessDataWriteAuditIT.java` |
| AC3.1.1 | Covered | U1・U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/TableAccessPolicyTest.java` |
| AC3.1.2 | Covered | U1・U2 | `backend/src/test/java/cherry/mastersmith/businessdata/web/BusinessDataAuthorizationApiIT.java` |
| AC3.1.3 | Covered | U2 | `backend/src/test/java/cherry/mastersmith/businessdata/web/BusinessDataWriteAuthorizationApiIT.java` |
| AC3.1.4 | Covered | U1・U2 | `backend/src/test/java/cherry/mastersmith/businessdata/service/TableAccessResolverTest.java` |
| AC3.1.5 | Covered | U1 | `backend/src/test/java/cherry/mastersmith/businessdata/service/TableAccessResolverTest.java` |
| AC3.1.6 | Covered | U1・U2 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/SearchRequestValidatorTest.java` |
| AC3.1.7 | Covered | U1 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/TableAccessPolicyPropertyTest.java` |
| AC3.1.8 | Covered | U1・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/TableAccessPolicyTest.java` |
| AC3.1.9 | Covered | U1・U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/TableAccessPolicyTest.java` |
| AC3.1.10 | Covered | U1・U2 | `backend/src/test/java/cherry/mastersmith/businessdata/service/TableAccessResolverTest.java` |
| AC3.2.1 | Covered | U1・U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/TableAccessPolicyTest.java` |
| AC3.2.2 | Covered | U1・U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/TableAccessPolicyTest.java` |
| AC3.2.3 | Covered | U1・U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/TableAccessPolicyTest.java` |
| AC3.2.4 | Covered | U1・U2 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/TableAccessPolicyTest.java` |
| AC3.2.5 | Covered | U1・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/TableAccessPolicyTest.java` |
| AC3.2.6 | Covered | U1・U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataReadIT.java` |
| AC3.2.7 | Covered | U1 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/TableAccessPolicyPropertyTest.java` |
| AC3.3.1 | Covered | U1・U2 | `backend/src/test/java/cherry/mastersmith/businessdata/service/TableAccessResolverTest.java` |
| AC3.3.2 | Covered | U1・U2 | `backend/src/test/java/cherry/mastersmith/businessdata/web/BusinessDataAuthorizationApiIT.java` |
| AC3.3.3 | Covered | U1・U2 | `backend/src/test/java/cherry/mastersmith/businessdata/web/BusinessDataReadControllerTest.java` |
| AC3.3.4 | Covered | U1・U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/service/BusinessDataReadServiceTest.java` |
| AC3.3.5 | Covered | U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/BusinessDataWriteTimeoutIT.java` |
| AC3.3.6 | Covered | U1・U2 | `backend/src/test/java/cherry/mastersmith/businessdata/service/BusinessDataReadServiceTest.java` |
| AC3.3.7 | Covered | U3 | `frontend/src/features/tables/BusinessDataProblemView.tsx` |
| AC3.3.8 | Covered | U1・U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/service/BusinessDataReadServiceTest.java` |
| AC3.3.9 | Covered | U1・U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/TableAccessPolicyTest.java` |
| AC3.3.10 | Covered | U1・U2 | `backend/src/test/java/cherry/mastersmith/businessdata/service/BusinessDataReadServiceTest.java` |
| AC3.4.1 | Covered | U2 | `backend/src/test/java/cherry/mastersmith/businessdata/web/BusinessDataWriteAuditIT.java` |
| AC3.4.2 | Covered | U2 | `backend/src/test/java/cherry/mastersmith/businessdata/web/BusinessDataWriteAuditIT.java` |
| AC3.4.3 | Covered | U2 | `backend/src/test/java/cherry/mastersmith/businessdata/web/BusinessDataWriteTimeoutIT.java` |
| AC3.4.4 | Covered | U2 | `backend/src/test/java/cherry/mastersmith/businessdata/web/BusinessDataWriteAuditIT.java` |
| AC3.4.5 | Covered（この段で足したテスト） | Build and Test | `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataReadIT.java`（`readsLeaveNoAudit`。U1・U2 の traceability は書き換えていない。4節） |
| AC3.4.6 | Covered | U2 | `backend/src/test/java/cherry/mastersmith/businessdata/web/BusinessDataWriteAuditIT.java` |
| AC3.4.7 | Covered | U2 | `backend/src/test/java/cherry/mastersmith/businessdata/web/BusinessDataWriteAuditIT.java` |
| AC3.4.8 | Covered | U2 | `backend/src/test/java/cherry/mastersmith/businessdata/web/BusinessDataWriteSecretLeakIT.java` |
| AC3.5.1 | Covered | U1・U2 | `backend/src/test/java/cherry/mastersmith/ApiAccessArchitectureTest.java` |
| AC3.5.2 | Covered | U1・U2 | `backend/src/test/java/cherry/mastersmith/businessdata/web/BusinessDataAuthorizationApiIT.java` |
| AC3.6.1 | Covered | U1・U2 | `backend/src/test/java/cherry/mastersmith/businessdata/service/TableAccessResolverTest.java` |
| AC3.6.2 | Covered | U1・U2 | `backend/src/test/java/cherry/mastersmith/businessdata/repository/ColumnValueCodecTest.java` |
| AC3.6.3 | Covered | U1・U2 | `backend/src/test/java/cherry/mastersmith/businessdata/repository/RowSqlExecutorTest.java` |
| AC3.6.4 | Covered | U1 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/IdentifierMatchingPropertyTest.java` |
| AC3.6.5 | Covered | U1・U2・U3 | `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractBusinessDataReadIT.java` |
| AC3.6.6 | Covered | U1・U2 | `backend/src/test/java/cherry/mastersmith/businessdata/web/BusinessDataConnectionUsageIT.java` |
| AC3.6.7 | Covered | U1・U2 | `backend/src/test/java/cherry/mastersmith/targetdb/service/JdbcTargetReadAccessTest.java` |
| AC3.6.8 | Covered | U1・U2 | `backend/src/test/java/cherry/mastersmith/businessdata/domain/ValueTypesTest.java` |
| AC3.6.9 | Covered | U1・U2 | `backend/src/test/java/cherry/mastersmith/businessdata/web/BusinessDataSecretLeakIT.java` |
| AC4.1.1 | Covered | U3・U4 | `frontend/src/features/tables/RowActionsMenu.tsx` |
| AC4.1.2 | Covered | U4 | `vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/Dropdown/Dropdown.tsx`（依頼（5）の分は U3 の行で次の Intent への Deferred） |
| AC4.1.3 | Covered | U3 | `frontend/package-lock.json` |
| AC4.1.4 | Covered | U4 | `frontend/src/app/layout/ShellLayout.tsx`（依頼（5）の分は U3 の行で次の Intent への Deferred） |
| AC4.1.5 | Covered | U3 | `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/code-generation/generation-notes.md` |
| AC4.1.6 | Covered | U3 | `frontend/e2e/185-business-data-accessibility.e2e.ts` |

- U3 の `OK` の行の target は、B7 の直し（N-39）で、そこに書いていた道のうち最初の実在する道1つにしてある。画面の AC の target が本体のソース（テストでない）を指す行は、同じ名前の `*.test.tsx` が確かめを持つ（U3 の `unit-test-instructions.md` 3.4）。

## Sources

- `inception/requirements-analysis/requirements.md`（FR1〜FR16 と枝番、NFR1〜NFR8 と NFR4.1〜NFR4.4）
- `inception/user-stories/stories.md`（AC 146 件）・`traceability.json`、`inception/units-generation/unit-of-work-story-map.md`
- 各単位の `functional-design/traceability.json`（U5 の FR → BR）・`nfr-requirements/traceability.json`・`code-generation/traceability.json`
- `aidlc/spaces/default/memory/project.md`（2段の連鎖でたどる決まり、記録の食い違いの扱い）

## Assumptions & Open Questions

- [assumption] FR → US の段の Covered は「その US の AC のすべてが、どれかの単位で `OK`」とし、後の段への `Deferred` が混じるときは「Covered（一部 Deferred）」とした。
- [assumption] 要件の NFR の網羅は、単位の NFR 要件の段が置いた枝番で判定した（単位が `N/A` とした NFR はその単位の分を数えない）。
- 後の段への `Deferred`（AC1.3.7 と NFR1・NFR2・NFR3・NFR8 の一部）は、Performance Validation・Observability Setup の結果で確かめ直す。
- FR16・FR16.1・FR16.3・FR16.4 は、この段の `memory.peak` の測り（`memory-peak-measurement.md`）で確かめて Covered にした。
- [assumption] Code Generation が「Build and Test」へ `Deferred` にした ID は、この段で確かめた結果で Covered とした（承認済みの `traceability.json` は `Deferred` のまま）。
