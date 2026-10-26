# 単位をまたぐ網羅の確かめ — Intent 261025-deps-perf-carryover

**Collaborator:** aidlc-quality-agent

## 1. 判定

**合格（条件つき）**。覆われていない ID は 0 件。この Intent は Unit の分割が無く、Code Generation の `traceability.json`（37 件）がそのまま対象である。そのうち Code Generation で `Deferred`（Build and Test へ引き継いだ 11 件、Deployment Execution へ引き継いだ 3 件）だった ID は、この段の実測で最終の判定をつけた。承認済みの `traceability.json` は書き換えていない（Q4: A）。

条件（後の段・次の Intent に残るもの）:

- **Not Met が 5 件ある**（FR4.1 の U1-NFR1.1・1.3・3.2・2.4 と、FR4.5 の `dslMixed` の `memory.peak`）。目標は緩めず、次の Intent へ持ち越す（要件 F1: A、依頼者の決定）。
- FR5.1〜FR5.3 は Deployment Execution の持ち分で、`Unverified`。

| 区分 | 数 | Met | Met（記録の目標・条件つき） | Not Met を含む | Unverified |
|---|---|---|---|---|---|
| FR（枝番を含む） | 32 | 26 | 1（FR4.2） | 2（FR4.1・FR4.5） | 3（FR5.1〜FR5.3） |
| NFR | 5 | 5 | — | — | — |

- FR4.9（前回と同じ場面・同じ線で測り直す）は、測り直して値を記録したため Met（測った）。測った結果の Met・Not Met は FR4.1・FR4.5 の行に出る。

## 2. 確かめ方

`project.md` の決まりのとおり、要件の FR・NFR から、Code Generation の `traceability.json` の行へたどった。ただし今回は Unit の分割が無く、`traceability.json` が要件の ID を直接持つため、1段でたどれた。`OK` の行は、target の道がリポジトリに実在することを確かめた。`Deferred` の行は、この段の `build-and-test-summary.md` 4節の Matrix の行（実測）で判定した。

## 3. 要件 ID ごとの最終の判定

| ID | Code Generation の状態 | 最終の判定 | 根拠（実測） |
|---|---|---|---|
| FR1.1 | OK（`compose.yaml`） | **Met** | #54〜#78（25 件）が CLOSED、マージ済み 0 件。B2・B5 で手元で取り込み、Dependabot が自動で閉じた（Matrix FR1.1-PR）。#79 は範囲外 |
| FR1.2 | OK | Met | イメージは版のタグと index のダイジェストで固定、固定先をそろえた（B2・B5）。`docker compose config` が通る |
| FR1.3 | OK | Met | `verify`・`osvScan` が B5 を含めて通り、見送った更新は `dependabot.yml` の `ignore` に記録 |
| FR1.4 | OK | **Met** | E2E 256 件通過（Playwright 1.64.1。B5 後も 256 件）、報告 json に資格情報・接続先なし（Matrix FR1.4-E2E・BT-B5） |
| FR1.5 | OK | Met | `braces`（上流が未修正）・依頼（5）（未公開）は記録だけ。警告のまま |
| FR2.1〜FR2.2 | OK | Met | Spring Boot 4.1.2。Tomcat・Jackson の上書きを外して `verify`・`osvScan` が通る |
| FR2.3 | OK | **Met** | `OtlpLogExportIT` 4・`ExternalExportIT` 1 が通る（appender 2.28.1-alpha の固定を残した） |
| FR2.4 | OK | Met | コメントを見直した結果に更新 |
| FR3.1〜FR3.3 | OK | **Met** | `ColumnValueCodecTest` 48、`JdbcTargetReadAccessTest` 11、`BusinessDataTimeoutIT` 9、`NavigationApiIT` 7 などが失敗 0（Matrix FR3.1-3.7-TESTS） |
| FR3.4 | OK（`docker/jmx/HikariPoolControl.java`） | **Met（見立てと試みの範囲の記録）** | 取りこぼしは 139 回の試みで 0 回、再現できず。見立て（接続の作成が速く窓がほとんど開かない。未検証）と試みの範囲を記録（Matrix FR3.4-COMPACT。依頼者の決定どおり再現は追加しない） |
| FR3.5〜FR3.7 | OK | **Met** | 画面の `PasswordChange*`・`Preferences*` を含む 1,977 件、`BusinessDataUnknownNamesIT` 6 などが通る。`perf/versions.env` に `K6_IMAGE` が 1 か所 |
| FR3.8 | Deferred（Build and Test） | **Met** | `monitorLight` 1 VU・5 分、`checks` 201/201、lgtm は止まらず、54 件の式が誤りなく返った（Matrix FR3.8） |
| FR3.9 | OK | Met | 存在しない列でも誤りの文を出さず `列なし` と書く（B4） |
| FR3.10 | OK（`README.md`） | **Met** | `osvScan --rerun` の失敗の条件 0 件、`frontend/dist` と WAR に axe-core なし、WAR の SHA-256 80b19c53…（Matrix FR3.10） |
| FR3.11 | OK | Met | `jq -c '.metrics.checks // .root_group.checks'` に直し、同じ報告で値が出ることを確かめた（B4） |
| FR4.1 | Deferred（Build and Test） | **Not Met を含む** | 3 種類の対象DB をプール 10 で測り直した。PG は全場面 Met。MariaDB の `bizSearchContains`・`bizSearchLast`・`bizSearchLastSorted` と MySQL の `bizSearchContains` が p95 1 秒を超える。時間切れ・503 は 0 になった。要件が約束した「Met になった件・ならなかった件を件数で記録する」ことは果たした。Not Met 4 件は目標を緩めず次の Intent へ（Matrix U1-NFR1.1・1.3・3.2・2.4） |
| FR4.2 | Deferred（Build and Test） | **Met（条件つき）** | 読み取りのプールの既定 10（`hikaricp.connections.max` 10 を確認）。3 種類の対象DB で OOMKilled false・`oom_kill` 0（合格の条件）。`anon` の最大は MariaDB 87.7%・MySQL 91.3% で 85% の線を超えた。依頼者の決定で線は緩めず持ち越し（Matrix FR4.2-OOM・FR4.2-ANON） |
| FR4.3 | OK | Met | DSL の説明文を足した（索引の案内） |
| FR4.4 | OK | **Met** | `*ConnectionUsageIT` 計 20・`TargetDbStartupIT` 7 が、プール 10 で失敗 0 |
| FR4.5 | Deferred（Build and Test） | **Not Met を含む** | `dslMixed` の `memory.peak` ÷ 2g は既定の回 0.9099・0.8774・0.9010（線 0.90 未満）で Not Met。`RETENTION_TIME=0` は 0.7084・0.7194 で、依頼者の決定により配備物に入れない（Matrix FR4.5-PEAK・FR4.5-RET0） |
| FR4.6 | OK | **Met** | 種を `utf8mb4` で入れ、3 種類とも 2B' の確かめ（目印の行 909・最長 2,000,000 字・行の数）を通してから表の大きさを測った（Matrix FR4.6-SEED） |
| FR4.7 | Deferred（Build and Test） | **Met** | 画面の時間 U3-NFR1.7a〜e の中央値 69.1・48.3・62.4・50.9・179.9 ms、すべて線の内（Matrix U3-NFR1.7a〜e）。7c・7d は前回より約 17〜18 ms 長い（原因は未切り分け） |
| FR4.8 | Deferred（Build and Test） | **Met** | P5 `dslRoleOverlap` の `checks` の率 1、最大 4,167.3 ms（線 15 秒未満）。P6 は `navMenu` の p95 187.1・159.3 ms を記録し、締める案は提案のみ（次の Intent で決める）。P7 は OOM 0 件（Matrix FR4.8-P5・P6・P7） |
| FR4.9 | Deferred（Build and Test） | **Met（測った）** | 前回と同じ場面・同じ線で 3 種類を 2 回ずつ流し直し、前回の値と並べて記録した（test-results.md 9節）。結果の Met・Not Met は FR4.1・FR4.5 |
| FR5.1〜FR5.3 | Deferred（Deployment Execution） | **Unverified** | この段の範囲の外。Deployment Execution で、イメージの入れ替えと戻し、`main` の先頭からの WAR の作り直しとスモークテスト、取り込みの前の CI の試行ごとの確かめを行う |
| NFR1 | Deferred（Build and Test） | **Met** | 目標の数値・線・物差しは変えていない。直した後の値を前の値と並べて記録した（Matrix NFR1-NOLOOSEN） |
| NFR2 | OK | **Met** | `*SecretLeakIT` 計 42 が失敗 0、E2E の報告に資格情報なし、`osvScan` の失敗の条件 0 件 |
| NFR3 | OK | **Met** | バックエンド 行 97.7%・分岐 91.4%、画面 行 95.06%・分岐 89.82%。除外は増やしていない。`verify` は 22〜23 分（42 分の線の内）、CI の検査の job は 28 分 39 秒・30 分 33 秒 |
| NFR4 | OK | Met | DSL の書式の版 2 は変更なしで読める（後方互換）。符号なしの型名は追加のみ |
| NFR5 | OK | Met | `JdbcTargetReadAccessTest` ほか、再現のテストを同じコミットに入れた |

## 4. 持ち越し（次の Intent へ）

- Not Met 4 件（MariaDB・MySQL の部分一致と MariaDB の深い OFFSET）。直し方は、件数を「多数」と出す案、索引の案内、VU・CPU の見直しなど（要件 F1: A）。
- `dslMixed` の `memory.peak` の Not Met（ページキャッシュと内部DB のファイルの伸びが原因と見立てているが、確定していない）。
- 対象DB の `anon` が 85% を超えた分（メモリの上限の引き上げ）、`RETENTION_TIME=0` の書き込みの途中で止める確かめ、P6 の締める案、新しい Dependabot の PR #79。
- 画面の時間 7c・7d が前回より長い原因の切り分け。
- README の手順 3B（12 VU に要る 12 人の利用者）の直し。
