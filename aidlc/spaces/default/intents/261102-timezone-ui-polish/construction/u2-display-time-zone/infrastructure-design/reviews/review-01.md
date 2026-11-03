## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-03
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/infrastructure-design/cicd-pipeline.md > 2.4 負荷の場面（k6）の「周辺」の行 | 「`verify` の閾値の部品の確かめで台本の登録の漏れを拾う」は実物と合わない。`perf/k6/thresholds-test.sh` は `build.gradle.kts`・`backend/build.gradle.kts`・`.github/workflows/ci.yml` のどこからも呼ばれず（`perf/README.md` の手動の手順だけ）、期待の表（CASES）も業務データと参照の場面だけで `preferences*` の場面を持たない。また既存の 3 場面は `USER_THRESHOLDS` で `http_req_duration` の p95 だけを判定しており、設計（PD6）が求める `iteration_duration{op}` の判定は別の経路（`GROUP_JUDGED_SCENARIOS` 側）になる | 「verify が登録漏れを拾う」の記述を直す。`thresholds-test.sh` の表に足す必要があるのか、`USER_THRESHOLDS` に入れるのか、`iteration_duration` の判定の経路に入れるのかをコード生成の計画で決めると書き、手動の確かめ（`k6 inspect --include-system-env-vars`）を登録漏れの唯一の守りとして明記する | New |
| R-02 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/infrastructure-design/cicd-pipeline.md > 2.5 E2E と 3.4 の「実行時のタイムゾーンの設定（4 か所）」 | 4 か所のうち「E2E の起動」は、実物の `frontend/playwright.config.ts` の webServer に `-Duser.timezone` も `timezoneId` も無く、機械の既定のタイムゾーンで動く（手元は Tokyo、CI の機械は UTC という `team.md` の注意と同じ形）。「今のまま」とだけあり、書き換える 080・100・110 の管理の画面の時刻（`±HH:mm` の形）の期待が機械に依存するのか固定するのかが書かれていない | 書き換える E2E は、時差を明示に指定して（`test.use` の `timezoneId` など）期待を機械の既定に預けないと書く。4 か所の確認の文に、E2E の起動は元から固定が無い点を添える | New |
| R-03 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/infrastructure-design/cicd-pipeline.md > 3.4 スモークテスト S6 | 無効な名前の拒否を省き「結合テストと k6 の `invalid` で確かめ済み」とするが、k6 は配備の前に流さない（3.5節）使い捨ての環境の結果で、配備したアプリでの確かめではない。省くこと自体は前の Intent に例があり妥当だが、根拠の書き方が実際より強い | 「配備したアプリでは確かめない。根拠は結合テストと Build and Test の k6」と書き直し、省く判断を承認の場の確かめ点に含めるか、Deployment Pipeline の判断として残す | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| 読み取りによる突き合わせ（Read と検索のみ。gradlew・docker・npm は未使用） | 実行済み | 下の各行の確認の根拠 |
| 検査の流れと実物（`build.gradle.kts` の `verifyStages`、`ci.yml`） | 一致 | `verifyStages` は 0 準備〜9 成果物の並びで、リンタ（2）に `frontendLint`、単体（5）に JUnit と Vitest、結合（6）に `integrationTest`、カバレッジ（7）に JaCoCo と `frontendCoverage`。CI は `./gradlew verify` のみ（`timeout-minutes: 60`）で、`ci.yml` を変えない主張は正しい |
| user の 4 層とパッケージごとの下限 | 正しい | `backend/build.gradle.kts` の `packagesJudgedByTotal` は 7 パッケージ（`access.service` ほか）で `user.*` を含まず、`user` は domain・repository・service・web の 4 層が在る。`UserBoundaryArchitectureTest` も在る |
| E2E を統合の前に流す根拠 | 決まりどおり | `frontend/src` のテストでないファイルを変えるため、`team.md` の「frontend の本体」に当たる。`e2e/` に 080・100・110 が在り、書き換えは本数に数えない読みも `team.md` の例と合う |
| 移行 V14 と前の版の互換 | 妥当 | 実物の最後は V13（`V13__u2_business_data_audit_columns.sql`）。`application.yaml` は `validate-on-migrate: true`・`ddl-auto: validate` で、足した列は前の版の検証を止めず（Flyway の既定の `ignoreMigrationPatterns` は `*:future`）、`AuditMigrationCompatibilityIT` は `CURRENT_LAST_VERSION = 13` を固定している（H3 の 13 から 14 の直しと一致）。NULL 可・既定なしの列だけの広げる側で、`team.md` の「縮める側」は当たらないとの整理も正しい |
| 戻し方・複写・Dockerfile | 実物と整合 | `docs/deployment.md` の戻し用のタグ・`--no-build`・`MASTERSMITH_IMAGE_TAG` と `docs/internal-db.md` の `~/.mastersmith-backup/`（700）・`mktemp -d` を避ける注意を踏襲。`Dockerfile` は WAR を写すだけの旨も合う。複写を停止の時間にまとめ権限 600 とする点は `project.md` と一致 |
| 影響の洗い出し（H3） | 実物と一致 | `updatePreferences` は `UserRepositoryIT`（6 行）・`MePreferencesLockTimeoutLeakIT`・`UserPreferencesServiceTest`・`UserRepository`・`UserPreferencesService` に在り、本文の見込みと同じ。`flyway_schema_history` を読むテストは版を指定した確認が中心で、`DatabasePersistenceIT` は件数に依らない |
| 監視を足さない主張 | 妥当 | 既存の警報は `ms-5xx-ratio`・ERROR のログ・`ms-app-absent` などで、新しい経路は既存の道の項目 1 つとログイン後の読み取り 1 回だけ。実在しない基盤・通知先は作っていない。目標（p95 1 秒）も緩めていない |
| タイムゾーンの 4 か所に変更が無い主張 | 正しい | `Dockerfile` の `-Duser.timezone=Asia/Tokyo`、`compose.yaml` の `TZ`、`backend/build.gradle.kts` の `user.timezone` はこの単位の差分に無い（設計も触れない）。E2E の起動の扱いは上の Minor 2 件目 |
| 引き継ぎ H1〜H12 と前の段のレビューの指摘 | 漏れなし | R-01（追跡の欠落）は G1 とこの段の `traceability.json`（NFR5.1・NFR5.3 を OK で足し、NFR2.1 は Deferred・持ち主 build-and-test）、R-02 は H11 の D9、R-03 は H3 の (b)、R-04 は H4 と 2.2節 に反映。G1 の差の書き方は正直で、承認済みの NFR 設計の追跡を書き換えていない |
| 追跡と本文の一致・他の単位の枝番・Sources／Assumptions | 問題なし | 追跡の 13 行（OK 12・Deferred 1）の target は本文の節（2.1・2.2・2.5・3.1〜3.4・H1〜H12）と一致する。散文に他の単位の枝番は無く、Sources と Assumptions & Open Questions の節も在る |

### Summary

検査の流れ・V14 の広げる側だけの移行・前の版の互換・戻し方・複写・監視を足さない主張・タイムゾーン 4 か所の主張は、実物のビルドと文書に照らして正しく、前の段の指摘もすべて拾われている。残るのは、k6 の登録漏れを `verify` が拾うという誤った記述、E2E のタイムゾーンの固定の書き方、S6 を省く根拠の強さの Minor 3 件で、Critical・Major は無い。
