# 負荷の試験の計画（load-test-plan）

Intent 261016-business-data-views の Performance Validation の計画です。依頼者の決定（Q1〜Q5: すべて A、まとめの確認 Looks correct）は `performance-validation-questions.md` にあります。判定する目標は、Build and Test で `Unverified` として持ち主をこの段にした 12 件です（`construction/build-and-test/build-and-test-summary.md`）。結果は `test-results.md`、判定は `nfr-validation-matrix.md` にあります。

## 1. 対象と環境

| 項目 | 内容 |
|---|---|
| 試験のイメージ | `mastersmith:perf-business-data-views`。配備したイメージ `mastersmith:local`（ID `sha256:4cec01e7…`、2026-10-20 作成。中の WAR の SHA-256 は `6426111d…bfe041f4`）に `docker tag` で付けた別名 |
| 使い捨ての環境 | `docker/perf/compose.yaml`（`-p mastersmith-perf`）。仮の署名鍵・仮の初期管理者・試験用の利用者 `perf-biz-01`〜`10` と操作する管理者 `perf-roleop01`（メールアドレスは予約のドメインだけ）。対象DB の種類ごとに作り直して、終わったら消す |
| アプリの上限 | CPU 4・メモリ 2g（配備と同じ）。`MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics` は使い捨てのアプリにだけ足す |
| 対象DB の試験の値 | `perf/README.md` の表どおりで、6つを export してから起動する。PostgreSQL は `shared_buffers` 256MB・上限 768m、MySQL はバッファープール 768M・上限 2304m、MariaDB はバッファープール 512M・上限 1g |
| VM | colima CPU 4・メモリ 6GiB。試験の間は配備したアプリを止める。見本の PG・Mailpit は止めない。lgtm は動かさない（Q4: A） |
| 一時の置き場 | ホームの下に `mktemp -d` で作り、権限 700 にする。環境ファイル（乱数の資格情報）・台本の写し・ログの写し・内部DB の複写を置く。値は表示しない |
| 結果の置き場 | `build/perf-results/biz-pv/`（git の対象外）。k6 の要約、プールの指標、メモリの見本、時刻、ディスク、行の数、監査の数え |

### 1.1 README の手順 0 との差（依頼者の決定 A）

- README の手順 0 は、`./gradlew :backend:bootWar` の後に Dockerfile で手元の WAR を写して、Intent ごとのタグのイメージを作ります。
- 手元の WAR（`backend/build/libs/mastersmith.war`）の SHA-256 は `ac9c58d4…49dca77` で、配備したもの（`6426111d…`）と違いました。2026-10-25 09:08 に作り直されていました（make-you-chic-ui の固定先を上げた後の verify と見られる）。
- そこで依頼者の決定 A により、手元の WAR は使わず、作り直しもしません。配備したイメージにそのまま `docker tag mastersmith:local mastersmith:perf-business-data-views` で別名を付けて使いました。
- 配備したものと同じ中身を測る代わりに、手順 0 の「WAR を Dockerfile で写して作る」形とは違います。タグを別にしたため、配備したアプリのタグ `local` は変わりません。

### 1.2 対象DB の試験の値の、設計の値との差（依頼者の決定 A、基盤の設計 2.2 の 80% の決まり）

基盤の設計（`construction/u1-businessdata-read/infrastructure-design/infrastructure-specification.md` 2.1）と README の表の値で起動し、`bizPrepare` の後に表の大きさを測りました。80% を超えた種類だけ、決まりどおり 128MiB ずつ上げます。目標の値と物差しは変えません。README の表は変えていません（この段では記録だけ）。

| DB | 設計・README の値 | 正しい種の表の合計 | 実際に使う値 | 割合 | VM の上限の合計 |
|---|---|---|---|---|---|
| PostgreSQL | `shared_buffers` 256MB・上限 768m | 212,566,016 B | 設計どおり | 79.2% | 3,584 MiB |
| MariaDB | プール 512M・上限 1g | 747,274,240 B（139.2%） | **プール 896M・上限 1408m** | 79.5% | 4,224 MiB |
| MySQL | プール 768M・上限 2304m | 1,017,806,848 B（126.4%） | **プール 1280M・上限 2688m**（依頼者の決定 F1: A） | 75.8% | 5,504 MiB（余裕 406 MiB） |

- MariaDB の条件: VM の上限の合計が、docker から見える量（約 5,910 MiB）から 300 MiB の余裕を残せること。4,224 MiB で満たしました。
- MySQL の経緯:
  - 依頼者の決定 A の値（1152M・2688m）で流し始めましたが、途中で種が誤っていたと分かりました（1.3）。その回の値は使いません。
  - 正しい種では、1152M でも 84.3% で 80% を超えました。決まりどおりの 1280M・2816m では VM の余裕が 278 MiB で、300 MiB の線を残せません。そこで依頼者に諮りました。
  - 依頼者の決定 F1: A により、プールは決まりどおり 1280M（128MiB ずつ4回）にしました。メモリの上限は 2688m で、決まり（同じだけ上げる、2816m）より **128 MiB 少ない** 値です。
  - 理由は、VM の余裕 300 MiB を残すためです。プール以外に使える量は 2688 − 1280 = 1,408 MiB で、設計の試しで見たプール以外の量（約 840 MiB）より大きくなります。
  - 設定の実際の値: `@@innodb_buffer_pool_size` は 1,342,177,280、コンテナのメモリの上限は 2,818,572,288 B です（08:40:23Z）。

### 1.3 写しの手順の直し（台本の明らかな誤り）

- `perf/README.md` の 2B は、種を `mysql`・`mariadb` の道具に標準入力で流します。MySQL の道具は文字の組が `latin1` で、日本語の値が二重に符号化されて入りました。そのため、部分一致の場面（`bizSearchContains`）の setup が「一覧が 0 件」で止まりました。
- リポジトリの外の写しの手順だけ、`mysql`・`mariadb` の道具に `--default-character-set=utf8mb4` を付けて流しています（MariaDB は直した後の手順だけで流したため、直す前の道具の既定の文字の組は確かめていません）。
- リポジトリの台本と README は変えていません。PostgreSQL（`psql`）は UTF-8 で入っており、部分一致の当たりがあったため、直していません。

## 2. 流す順（Q1: A）

PostgreSQL → MySQL → MariaDB の順に流します。種類ごとの手順は次のとおりで、README の 1B〜6B と「業務データの書き込みの場面」に沿います。

実際に流し終えた順は、PostgreSQL → MariaDB → MySQL です。MySQL の種の誤り（1.3）と試験の値の決定（1.2）を待つ間に、MariaDB を先に流しました。MySQL では、片方の回だけ満たさなかった `bizSearchFirst`・`bizSearchLast` を、環境を作り直して3回目として流しました（Q2: A。`test-results.md` 3.3）。

1. 1B: 対象DB の資格情報（乱数）とアプリの接続先（読み取り・書き込みのアカウント）を、一時の環境ファイルに書く
2. 2B: 対象DB を試験の値で起動し、種（`perf/biz/make-biz-seed.sh`）を入れる。行の数は種の最後の出力で記録する（U1-NFR2.1）
3. 3B: アプリを起動して止め、試験用の利用者を H2 の道具で入れて、起動し直す
4. DSL（`perf/make-biz-dsl.mjs`）を作り、`bizPrepare` を流す
5. 設定の実際の値（`SHOW shared_buffers`・`@@innodb_buffer_pool_size`）、コンテナのメモリの上限（`docker inspect`）、表のデータと索引の大きさを記録する。表の合計がプールの 80% を超えたら、試験の値を変えずに止めて諮る
6. 表を1回全部読んで暖める
7. 合否の回を2回流す（Q2: A）。1回の中身は次のとおり:
   - 読み取り: `bizDefinition`（PostgreSQL だけ）・`bizSearchFirst`・`bizSearchContains`・`bizSearchLast`・`bizSearchLastSorted`・`bizDetail`。各 10 VU・3 分
   - アプリのコンテナを作り直す（前にログを一時の置き場に残す）→ `bizSearchWide`（10 VU・3 分）
   - 書き込み: `bizCreate`・`bizUpdate`・`bizWritePoolLimit`（各 10 VU・3 分）、`bizDelete`（既定 100 回）
   - アプリのコンテナを作り直す（前にログを残す）→ `bizCreateLarge`（10 VU × 既定 3 回）
8. 確かめの結果を見る。内容は次のとおり:
   - ログの件数（秘密・行の値・利用者のメールアドレス・WARN・ERROR）
   - 使い捨てのアプリを止め、内部DB の複写を読み取り（`ACCESS_MODE_DATA=r`）で開いて、監査の行を種類・結果・理由ごとに数える。`mastersmith.mv.db` の大きさも記録する（Q5: A）
9. 片付け: `perfu --profile "targetdb-$KIND" down -v`

場面ごとに次を記録します。

- 前後の対象DB のプールの指標（読み取り `mastersmith-target-db`・書き込み `mastersmith-target-db-write`）: 待ちの時間切れの累計・借りる待ち（COUNT・最大）・待っている数・上限
- 1秒ごとのメモリ（アプリと対象DB の `memory.current`・`memory.stat` の `anon`）と、場面の後の `memory.peak`・`memory.events`・OOMKilled
- 回ごとに、ディスクの空き（ホスト・VM）と使い捨てのボリュームの大きさ、`biz_new` の行の数と表の大きさ、内部DB のファイルの大きさ。ホストの空きが 5Gi を切りそうなら止めて報告する（依頼者の指示）

PC の眠りの守り: 種類ごとに、準備の台本と2回の台本をそれぞれ1つの `caffeinate -dims` で包みます。流した後に `pmset -g log` で、試験の窓に Sleep・DarkWake が無いことを確かめます。入っていたら、その回の値を使わずに流し直します。

## 3. 合否（目標は緩めない。物差しも変えない）

| 目標 | 合格の条件 |
|---|---|
| U1-NFR1.1・NFR1.3 | 読み取りの6場面（PG）・5場面（MySQL・MariaDB）の `iteration_duration{scenario:<名前>}` の p95 < 1000 ms と、`checks` の率 1。全体の件数（`bizSearchFirst`）と深い OFFSET（`bizSearchLast`）を含む |
| U1-NFR3.2 | 対象DB の4場面（`bizSearchFirst`・`bizSearchContains`・`bizSearchLast`・`bizDetail`）が、3種類のどれでも NFR1.1 を満たす。1種類でも満たさなければ Not Met |
| U1-NFR2.1 | 3種類で、種の台本の行の数（`biz_item` 100,000・`biz_pair` 1,000・`biz_wide` 200）を記録する |
| U1-NFR2.4 | 読み取りのプール 5 のまま、10 VU で各場面の p95 < 1 秒、読み取りのプールの待ちの時間切れの累計の増分 0、503 の件数 0（`checks` の率 1 で数える）。待ちの最大は記録だけ |
| U1-NFR2.5b | `bizSearchWide` の後のアプリの `memory.peak` < 2g の 90%（1,932,735,283 B）。`anon` の最大を並べる |
| U2-NFR1.4 | `bizCreate`・`bizUpdate`・`bizDelete` の p95 < 1000 ms と `checks` の率 1 |
| U2-NFR1.5 | `bizWritePoolLimit` の p95 < 1000 ms（409 を正しい答えとして `checks` に入れる）と、U2-NFR2.7 の条件 |
| U2-NFR3.4 | 書き込みの4場面が、3種類のどれでも NFR1.4・NFR1.5 を満たす |
| U2-NFR2.7 | 書き込みのプール 5、10 VU で、書き込みのプールの待ちの時間切れの累計の増分 0、503 の件数 0。待ちの最大は記録だけ |
| U2-NFR2.8 | `bizCreateLarge` の後のアプリの `memory.peak` < 2g の 90%。`anon` の最大を並べる。対象DB は OOMKilled false かつ `oom_kill` 0（`anon` の最大が上限の 85% を超えたら諮る） |
| U2-NFR2.9 | 監査の行の数と内部DB の大きさを数え、見積もり（1行 約 0.5 KB）と前の段の実測（約 100 B）と並べる（記録の目標） |

- **回の数（Q2: A）**: 種類ごとに2回流し、両方の回で満たせば Met とします。片方だけ満たさないときは3回目を流し、原因（眠り・PC の負荷・時計のずれ）をログと記録で確かめてから判定します。
- **満たさない場面（Q3: A）**: 次のとおりに扱います。
  - Not Met として、次を並べて記録します: p95・最大・`http_req_duration`、対象DB の設定の実際の値、表の大きさ
  - 原因の見立ても書きます。
  - コードと設定（試験の値を含む）は変えず、直すかは承認の場で諮ります。
- `http_req_duration{name:…}` は VM の時計のずれで崩れうるため、並べて記録するだけです。

## Sources

- `aidlc/spaces/default/intents/261016-business-data-views/operation/performance-validation/performance-validation-questions.md`（Q1〜Q5、まとめの確認）
- `aidlc/spaces/default/intents/261016-business-data-views/construction/build-and-test/build-and-test-summary.md`（Unverified 12 件）
- `aidlc/spaces/default/intents/261016-business-data-views/construction/u1-businessdata-read/nfr-requirements/performance-requirements.md`・`scalability-requirements.md`
- `aidlc/spaces/default/intents/261016-business-data-views/construction/u2-businessdata-write/nfr-requirements/performance-requirements.md`・`scalability-requirements.md`
- `perf/README.md`（手順 0〜4、「業務データの場面」「業務データの書き込みの場面」）、`perf/k6/scenarios.js`、`docker/perf/compose.yaml`
- `aidlc/spaces/default/intents/261016-business-data-views/operation/observability-setup/measurement-notes.md`（7節・10節）
- `aidlc/spaces/default/memory/team.md`・`project.md`（Testing Posture・Deployment・Corrections の学び）
- 依頼者の決定 A（試験のイメージを配備したイメージの別名にする。手元の WAR は使わない）

## Assumptions & Open Questions

- 503 の件数は、どの場面の `checks` も 200・201・409 だけを合格にするため、`checks` の率 1 を「503 が 0 件」と読みます。
- `memory.peak` は作り直した後のアプリのコンテナの値で読みます。`anon` の最大は1秒ごとの見本の最大で、見本の間の瞬間の最大は取りこぼしうります。
- 読み取りの場面のうち `bizSearchWide` より前の5〜6場面は、`bizPrepare` の後のアプリで流します。2回目は1回目の書き込みの後のアプリ（作り直した後）で流すため、内部DB の監査の行が多い状態（悪い側）で測ります。
