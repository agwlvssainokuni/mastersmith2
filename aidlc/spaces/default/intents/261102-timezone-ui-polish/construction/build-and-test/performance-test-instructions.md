# 性能の試験の手順 — Build and Test（Intent 261102-timezone-ui-polish）

各単位の NFR 要件で Deferred（持ち主 Build and Test）とした k6 の目標を、使い捨ての環境で確かめる。この Intent の流れに Performance Validation の段が無く、使い捨ての環境を手元で用意できるため、持ち主はこの段（`project.md` の学び）。手順の正は `perf/README.md`（「手順」「利用者の設定と招待の場面」「業務のメニューの場面」「権限の受け渡しの場面と準備の場面」「業務データの場面」「他テーブル参照の読み取りの場面」「記録（evidence/）に置く前の伏せ字」）。結果は `evidence/perf-results.md`。

## 1. 測る場面（範囲は下の ID に当たる場面だけ）

目標と物差しは変えない・緩めない。閾値は `perf/README.md` の「今の基準の一覧」と `perf/k6/scenarios.js` の今の定義のまま。始めに場面ごとに `k6 inspect --include-system-env-vars` で閾値の名前を照らし、`bash perf/k6/thresholds-test.sh` を流した（どちらも通過。`preferencesSaveTimeZone` の op と name の閾値も一覧どおり）。

| 場面 | 出どころ | 対象DB | 判定（緩めない） |
|---|---|---|---|
| `preferencesGet`・`preferencesSave`・`preferencesInvalid` | U2 NFR2.1.3 | 要らない | p95 1 秒・`checks` の率 1（台本の閾値は `http_req_duration{name:<場面>}`。判定の値は場面を1つだけ流した回の `iteration_duration` の p95 と並べる） |
| `preferencesSaveTimeZone` | U2 NFR2.1.4 | 要らない | `iteration_duration{scenario:…,op:set|clear|invalid}` の p95 1 秒・`{name:preferencesSaveTimeZone}`・`{name:preferencesSaveTimeZoneInvalid}` の p95 1 秒・`checks` の率 1 |
| `navMenu`・`navMenuBaseline` | U5 NFR2.1.2 | 要らない | `iteration_duration{scenario:<場面>}` の p95 < 1,000 ms・`checks` の率 1 |
| `bizDefinition`・`bizSearchFirst`・`bizDetail` | U4 NFR2.1.2（`bizSearchFirst`・`bizDetail` は U3 NFR2.1.5 も） | PostgreSQL | p95 < 1,000 ms・`checks` の率 1（読み取りのプールは既定の上限 10） |
| `bizSearchContains` | U4 NFR2.1.2・U3 NFR2.1.5 | PostgreSQL | p95 < 3,000 ms（`BIZ_P95_LIMIT_MS`） |
| `bizSearchLast`・`bizSearchLastSorted` | U4 NFR2.1.2・U3 NFR2.1.5 | PostgreSQL | p95 < 2,000 ms（`BIZ_P95_LIMIT_MS`） |
| `refDefinition200`・`refPickerEquals`・`refLabel` | U4 NFR2.1.2（参照の選択肢・ピッカー・ラベル） | PostgreSQL | p95 < 1,000 ms（読み取りのプールの上限 5） |
| `refPickerContains` | U4 NFR2.1.2 | PostgreSQL | p95 < 3,000 ms（`REF_P95_LIMIT_MS`、上限 5） |
| `bizSearchFirst`・`bizDetail`・`bizSearchContains`・`bizSearchLast`・`bizSearchLastSorted`・`refDefinition200`・`refPickerEquals`・`refLabel`・`refPickerContains` | U3 NFR2.1.5・U4 NFR2.1.2 | MySQL・MariaDB（依頼者の決定で追加） | 上の行と同じ線（業務データはプールの上限 10、参照は上限 5）。`bizDefinition` は対象DB に接続しないため PostgreSQL の回だけ |

- U3 NFR2.1.5 の前後の比べは、直前の版（Intent 261101-maintenance-update の Build and Test）の PostgreSQL・MySQL・MariaDB の値と、MySQL・MariaDB は Intent 261027-perf-targets-review の測り直しの値とも並べる（`evidence/perf-results.md` の 4 節と 5.3 節）。
- MySQL・MariaDB の回は、最初の報告で時間の上限のため Unverified としたが、依頼者の決定で同じ版・同じ手順で追加して流した（`evidence/perf-results.md` の 5 節）。

## 2. 流し方（実際に流した順）

1. 配備したアプリを `docker compose stop app` で止め、止める前のイメージ ID と healthy を記録した。
2. `./gradlew :backend:bootWar` で WAR が今の HEAD の入力に対して UP-TO-DATE であることを確かめ、WAR の SHA-256 を記録した。手元の WAR から試験のイメージを別のタグで作った（`docker build -t mastersmith:perf-tz-bt .`。`mastersmith:local` は変えない）。イメージの中の WAR の SHA-256 が手元と同じことを確かめた。
3. 一時の環境ファイルをホームの下（`~/.mastersmith-perf-XXXXXX`、権限 700、ファイル 600）に乱数で作った（値は表示しない）。アプリの側に `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics`（使い捨てのアプリだけ）と対象DB の接続先（読み取り・書き込みのアカウント）を置いた。DSL は `make-biz-dsl.mjs postgres` と `make-nav-dsl.mjs --worst|--baseline`（元は `build/perf-results/role-data/dsl.yaml`）で作り直した。
4. 駆動の台本の全体を1つの `caffeinate -dims` で包み、次の順で流した（場面ごとに起こし直さない）:
   1. 対象DB（PostgreSQL、業務データの試験の値 `shared_buffers` 256MB・768m）を起動し、種を入れ、種の確かめ（行の数・目印の語の当たり・最長の文字数）。
   2. アプリを起動して止め、H2 の道具で試験用の利用者（`perf-biz-01`〜`10`・`perf-nav-01`〜`10`・`perf-user01`〜`11`・管理者 `perf-roleop01`、どれも `@example.test`）を入れて起動した。
   3. `bizPrepare` → 表の大きさ・`shared_buffers`・メモリの上限の記録 → 集計の形の暖め → 業務データの6場面を場面ごとに r1・r2（場面ごとに前後でプールの時間切れの累計と借りる待ちの最大を読む）。
   4. 読み取りのプールの上限 5 の行を足し、アプリのログを一時の置き場に残してからアプリを作り直し、暖め（判定に数えない `refPickerEquals` の 5 秒）の後に `hikaricp.connections.max` が 5 であることを確かめ（5 でなければ参照の場面を流さない）、参照の4場面を r1・r2。
   5. `rolePrepare`（版 2 の大きな DSL）→ `navPrepare`（悪い側の DSL）→ `navMenu` を r1・r2 → 設定の4場面を r1・r2（内部DB が膨らんだ悪い側の条件）→ 最後に `navMenuBaseline` を r1・r2（目安の DSL に入れ替えるため最後）。
   6. 片付けの前の状態（コンテナの `OOMKilled`・対象DB の `memory.events`・アプリの `memory.peak`）を記録し、アプリのログを一時の置き場に残した。
5. 流した後に `pmset -g log` で窓に眠りが無いことを確かめた。アプリのログを件数とロガー・code ごとに数え（利用者の名前・行の値・`eyJ` が 0 件、WARN の内訳が期待の 400・409 と一致）、取り直しが要らないことを確かめてから片付けた。
6. 片付け: `docker compose -p mastersmith-perf -f docker/perf/compose.yaml --profile targetdb-postgres down -v`、一時の置き場の削除（`mastersmith-perf` のボリューム・コンテナ 0、`~/.mastersmith-perf-*` 0）。試験のタグの変数を渡さずに `docker compose start app` で配備したアプリを戻し、healthy とイメージ ID（`mastersmith:local` と同じ）を確かめた。自分が起動した `caffeinate` が残っていないことを `pgrep -lf caffeinate` で確かめた。

### 2.1 MySQL・MariaDB の回（追加）

1. 配備したアプリを `docker compose stop app` で止め、止めた時刻を記録した。手元の WAR とイメージ `mastersmith:perf-tz-bt` の中の WAR の SHA-256 が `e2563954…` のままであることを確かめ直した（イメージは作り直していない）。
2. 一時の環境ファイルを新しく作り直した（上の 3 と同じ形。ホームの下、権限 700）。DSL は `make-biz-dsl.mjs mysql`・`mariadb` で種類ごとに作った。
3. 駆動の台本の全体（2種類）を1つの `caffeinate -dims` で包み、種類ごと（MySQL → MariaDB）に次を流した:
   1. 対象DB を README の業務データの試験の値（MySQL 1280M・2688m、MariaDB 896M・1408m）で起動し、種を `--default-character-set=utf8mb4` で入れ、種の確かめ（行の数・目印の語の当たり・最長の文字数、`biz_refcreate` が 0 行）。対象DB の `memory.stat` の `anon` を 2 秒ごとに記録した。
   2. アプリを起動して止め、試験用の利用者（`perf-biz-01`〜`10`・`perf-roleop01`）を入れて起動した。
   3. `bizPrepare` → 表の大きさ（`information_schema.TABLES` の `DATA_LENGTH + INDEX_LENGTH`）と `@@innodb_buffer_pool_size`・メモリの上限の記録 → 集計の形の暖め（`CONCAT_WS` で 100 カラムを1行にまとめて長さを足す）→ 業務データの5場面を r1・r2。
   4. 読み取りのプールの上限 5 の行を足し、ログを一時の置き場に残してからアプリを作り直し、暖めの後に `hikaricp.connections.max` が 5 であることを確かめてから参照の4場面を r1・r2。
   5. 片付けの前の状態（`OOMKilled`・対象DB の `memory.events`・アプリの `memory.peak`）を記録し、アプリのログを一時の置き場に残してから `down -v`。
4. 流した後に `pmset -g log` で窓に眠りが無いことを確かめ、一時の置き場に残したログを件数とロガー・code ごとに数えた。試験のタグの変数を渡さずに `docker compose start app` で配備したアプリを戻し、healthy とイメージ ID を確かめ、一時の置き場を消した。自分の `caffeinate` が残っていないことを確かめた。

## 3. 記録（`evidence/`）に置くもの

測った 68 回の k6 の summary json（PostgreSQL の回の `biz-<場面>-r<n>.json`・`ref-<場面>-r<n>.json`・`nav-<場面>-r<n>.json`・`pref-<場面>-r<n>.json` の 32 件と、`mysql-<場面>-r<n>.json`・`mariadb-<場面>-r<n>.json` の 36 件）と `perf-results.md`。json は伏せ字の道具を通して置き、置いた後に確かめる。

```bash
node perf/redact-k6-evidence.mjs build/perf-results/tzbt/<名前>.json <記録の置き場>/evidence/<名前>.json
node perf/redact-k6-evidence.mjs --scan <記録の置き場>/evidence
gitleaks dir --redact --no-banner --config .gitleaks.toml --exit-code 1 <記録の置き場>/evidence
grep -rc 'eyJ' <記録の置き場>/evidence
```

## 4. 計画との差・気づいたこと

1. 依頼では「WAR は今のコミットの `verify` が作ったもの」とされていたが、WAR のファイルの日時が 09:29 で、U4 のコードのコミット（20:44）より前だった。`./gradlew :backend:bootWar` で全タスクが UP-TO-DATE（今の入力と一致）であること、WAR の中の画面の資産の名前が今の `frontend/dist` と同じことを確かめ、今の HEAD の WAR として使った（作り直しは起きていない）。
2. 業務データ・参照・業務のメニュー・設定の場面を、1つの使い捨ての環境（PostgreSQL つき）で順に流した（README では節ごとに別の環境）。順は、業務データ・参照を先に（前の Intent の値と同じ条件にそろえる）、DSL を入れ替える業務のメニュー・設定を後に、目安の DSL に替える `navMenuBaseline` を最後にした。業務のメニューと設定の場面は、内部DB に業務データの準備の分（ロール・割り当て・監査）が足された悪い側の条件で測っている。
3. MySQL・MariaDB の回は、最初は時間の上限のため流さず Unverified としたが、依頼者の決定で追加して流した（`evidence/perf-results.md` の 5 節）。駆動は種類ごとの `down -v` を自動で行うため、アプリのログは `down -v` の前にリポジトリの外の一時の置き場へ残し、片付けの後にそこから数えた（README の「確かめの結果を見てから片付ける」との差。数える材料は片付けの前にすべて残してある）。
4. 設定の3場面の判定の値は、台本に場面ごとの `iteration_duration` の閾値が無いため、全体の `iteration_duration`（setup の1件を含む）を使った。台本は変えていない。

## Sources

- `construction/u2-display-time-zone/nfr-requirements/performance-requirements.md`（NFR2.1.3・NFR2.1.4）
- `construction/u5-menu-icons-home/nfr-requirements/performance-requirements.md`（NFR2.1.2）
- `construction/u4-business-data-time/nfr-requirements/performance-requirements.md`（NFR2.1.2）
- `construction/u3-target-db-time/nfr-requirements/tech-stack-decisions.md`（NFR2.1.5）と `infrastructure-design/cicd-pipeline.md`（前後の記録の場面と線）
- `perf/README.md`・`perf/k6/scenarios.js`・`perf/k6/thresholds-test.sh`
- 前の値: Intent 261101-maintenance-update の `construction/build-and-test/test-results.md`（5.3 節）、Intent 261025-deps-perf-carryover の `construction/build-and-test/test-results.md`（9.6 節）、Intent 261004-role-menu の `operation/performance-validation/nfr-validation-matrix.md`、Intent 260925-user-management の `operation/performance-validation/test-results.md`
- `aidlc/spaces/default/memory/project.md`・`team.md`（使い捨ての環境・眠り・伏せ字・片付けの前の確かめ・2 回の合否の回）

## Assumptions & Open Questions

- [assumption] U3 NFR2.1.5 の「前」は、直前の版（Intent 261101）の各 DB の1回の値（MySQL・MariaDB は Intent 261027 の値も）とした（U3 の統合の前の版を今回あらためて測ってはいない）。
