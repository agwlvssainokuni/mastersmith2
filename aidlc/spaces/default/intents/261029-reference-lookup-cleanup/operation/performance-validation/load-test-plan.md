# 負荷の試験の計画（load-test-plan）

Intent 261029-reference-lookup-cleanup の Performance Validation の計画の初稿です。依頼者に尋ねる点は `performance-validation-questions.md` にあり、答えが決まるまでは各問いの推奨（Q1: A・Q2: B・Q3: C）を前提に書いています。答えが推奨と違えば、この計画を直します。結果は `test-results.md`、判定は `nfr-validation-matrix.md` に書きます（実施の記録は 8節）。

## 1. この段で判定する目標

Build and Test で `Unverified` とし、持ち主をこの段にした行です（`construction/build-and-test/build-and-test-summary.md`）。目標の値と物差し（`iteration_duration` の p95）は変えません（`team.md`・`project.md`、U1 の NFR2.5・U2 の NFR2.25）。

| 行 | 中身 | 判定に使う場面 |
|---|---|---|
| U1-NFR2.1 | 参照先の読み取りの応答時間（p95 < 1,000 ms、部分一致だけ < 3,000 ms）と `checks` の率 1 | `refDefinition200`・`refDefinition201`・`refPickerEquals`・`refLabel`・`refPickerContains` |
| U1-NFR2.3・AC-U1-AC3.5.4 | 3種類の対象DB（MySQL・MariaDB・PostgreSQL）のどれでも U1-NFR2.1 を満たす。読み取りのプールの上限 5 | 同上（3種類） |
| U1-NFR2.5 | 場面ごとに Met・Not Met を記録する（満たさなくても緩めない） | 同上 |
| U2-NFR2.21・U2-NFR2.24 | 保存前の確かめつきの作成の p95 < 1,000 ms と `checks` の率 1 を、3種類のどれでも満たす | `refCreateChecked` |
| U2-NFR2.23 | 既存の書き込みの場面の意味を保つ。`bizWritePoolLimit` の 409 の数と書き込みのプールの待ちを前の Intent と並べる。`bizCreate`・`bizUpdate` が遅くならないことを並べて記録する | `bizCreate`・`bizUpdate`・`bizDelete`・`bizWritePoolLimit` |
| U2-NFR2.25 | U2-NFR2.21 の Met・Not Met を記録する | `refCreateChecked` |
| BT-K6-JUDGE | 上の参照の6場面の判定の回（10 VU・3 分、業務データの試験の値で起動、合否の回 2 回） | 参照の6場面 |
| BT-MEMPEAK-JUDGE | `bizSearchWide`・`bizCreateLarge` のアプリのコンテナの `memory.peak` / `memory.max` < 0.9（Build and Test の Q5: B で 90% 未満のまま確定。物差しの変更は無いため、前の物差しを並べる記録は要らない） | `bizSearchWide`・`bizCreateLarge` |
| REQ-NFR2（性能の分） | 上の単位の行がすべて決まれば決まる | — |

あわせて記録するもの（判定の行ではないが、設計がこの段の記録を求めているもの）:

- U1-NFR3.3・U2-NFR3.23: 読み取りのプール（`mastersmith-target-db`）と書き込みのプール（`mastersmith-target-db-write`）の待ちの時間切れの累計の前後の差と、借りる待ちの最大（`u1-reference-read/nfr-design/scalability-design.md`・`u2-reference-write-check/nfr-design/scalability-design.md` 3節）。
- BT-K6-POOL5: 判定の回ごとに、読み取りのプールの上限が 5 であることを確かめてから流す（中止の条件 R-03）。
- BT-K6-BASELINE: 比べる相手（Intent 261016 の Performance Validation、上限 5）と並べた表。

## 2. 対象と環境

| 項目 | 内容 |
|---|---|
| 配備したアプリ | `mastersmith-app-1`（イメージ `mastersmith:local`、ID `sha256:d8a467b60f6f…`、2026-11-01T05:01Z 作成）。中の WAR の SHA-256 は `4cd5c416…008222f`（`operation/deployment-execution/health-check-report.md`） |
| 試験のイメージ | `mastersmith:perf-reference-lookup-cleanup`。`perf/README.md` の手順 0 のとおり、手元の WAR（`backend/build/libs/mastersmith.war`、SHA-256 `4cd5c416…008222f`。配備したものと同じであることを 2026-11-01 に確かめた）を Dockerfile で写して作る。配備したアプリのタグ `local` は上書きしない。作るのは配備したアプリを止める前（Build and Test の D1 と同じ。止める時間を短くするため） |
| 使い捨ての環境 | `docker/perf/compose.yaml`（`-p mastersmith-perf`）。仮の署名鍵・仮の初期管理者・試験用の利用者 `perf-biz-01`〜`10` と操作する管理者 `perf-roleop01`（メールアドレスは予約のドメインだけ）。対象DB の種類ごとに作り直し、終わったら消す。本物のデータと監査ログには触れない |
| アプリの上限 | CPU 4・メモリ 2g（配備と同じ）。`MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics` は使い捨てのアプリにだけ渡す |
| アプリの一時の環境ファイルに足す行 | `MASTERSMITH_TARGET_DB_POOL_MAXIMUM_SIZE=5`（読み取りのプールの上限。U1 の基盤の設計 4.1）、書き込みのアカウントの2行（`MASTERSMITH_TARGET_DB_WRITE_USERNAME`・`MASTERSMITH_TARGET_DB_WRITE_PASSWORD`。値は表示しない）。参照の2つの設定（`..._QUERY_TIMEOUT_REFERENCE`・`..._REFERENCE_READ_TOTAL`）と書き込みのプールの上限は書かない（既定の 5s・10s・5） |
| 対象DB の試験の値 | `perf/README.md` の「業務データの場面」の表どおりに export してから起動する。PostgreSQL は `shared_buffers` 256MB・上限 768m、MySQL はバッファープール 1280M・上限 2688m、MariaDB はバッファープール 896M・上限 1408m |
| k6 | `perf/versions.env` の `K6_IMAGE`（`grafana/k6:2.3.0`） |
| VM | colima CPU 4・メモリ 6GiB。2026-11-01 の読み取りの確かめで `free -m` は total 5,911・available 4,813（配備したアプリが動いている状態）。試験の間は配備したアプリを止める。見本の PG・Mailpit は止めない。手元の監視（lgtm）は動かさない |
| 一時の置き場 | ホームの下に `mktemp -d` で作り、権限 700 にする（macOS の一時ディレクトリは VM から見えないため）。環境ファイル（乱数の資格情報）・台本の写し・ログの写しを置く。値は表示しない。終わったら消す |
| 結果の置き場 | `build/perf-results/ref-pv/`（git の対象外）。k6 の summary json・プールの指標・メモリの見本・時刻・ログの件数 |

### 2.1 メモリの見積もり（コンテナの上限の合計）

| 対象DB の回 | 使い捨てのアプリ | 対象DB | 見本の PG | Mailpit | 合計 | docker から見える量（5,911 MiB）との余り |
|---|---|---|---|---|---|---|
| PostgreSQL | 2,048 | 768 | 512 | 256 | 3,584 MiB | 2,327 MiB |
| MySQL | 2,048 | 2,688 | 512 | 256 | 5,504 MiB | 407 MiB |
| MariaDB | 2,048 | 1,408 | 512 | 256 | 4,224 MiB | 1,687 MiB |

- 配備したアプリ（上限 2g、今の使用 約 609 MiB）を止める前提の値です。止めないと MySQL の回は上限の合計が VM を超えます。
- k6 のコンテナには上限がありません。`bizCreateLarge` は k6 の側も大きな本文を作るため、`docker stats` で k6 のコンテナの CPU とメモリも記録します（`perf/README.md` の片付ける前に見るもの）。
- 表の大きさがプール（PG は共有バッファー）の 80% を超えたときの決まり（128 MiB ずつ上げ、メモリの上限も同じだけ上げる）で MySQL を上げると、余りが 300 MiB を切ります。そのときは試験の値を変えずに止めて諮ります（3節の 5）。Build and Test の記録では MySQL の `biz_item` 742,391,808 B・`biz_wide` 275,267,584 B（合計 約 1,017 MB、1280M の 約 75.8%）で、前の Intent と同じ値のため、超えない見込みです。

## 3. 流す順

PostgreSQL → MySQL → MariaDB の順に、対象DB の種類ごとに次を行います。手順は `perf/README.md` の「業務データの場面」（1B〜4B）・「業務データの書き込みの場面」・「他テーブル参照の読み取りの場面」・「他テーブル参照の保存前の確かめの場面」に沿い、場面の順は U1・U2 の基盤の設計 4.4 に従います（読み取りを先、書き込みを後）。

**種類ごとの準備**

1. 1B: 対象DB の資格情報（乱数）とアプリの接続先（読み取り・書き込みのアカウント）を一時の環境ファイルに書き、読み取りのプールの上限 5 の行を足す
2. 2B: 対象DB を業務データの試験の値で起動し、種（`perf/biz/make-biz-seed.sh`）を入れる。MySQL・MariaDB は `--default-character-set=utf8mb4` を付ける
3. 種の後の確かめ（U1-NFR2.4・U2 の基盤の設計 R-03）: 種の最後の行の数の出力で `biz_item` 100,000・`biz_code200` 200・`biz_code201` 201・`biz_refcreate` 0 を確かめ、2B' の目印の語の当たりの数（0 でないこと。Build and Test では 909）と最長の文字数を数える
4. 3B: アプリを起動して止め、試験用の利用者を H2 の道具で入れて、起動し直す。DSL（`perf/make-biz-dsl.mjs`）を作り、`bizPrepare` を流す（`checks` の率 1）
5. 設定の実際の値（`SHOW shared_buffers`・`@@innodb_buffer_pool_size`）、コンテナのメモリの上限（`docker inspect` の Memory）、表のデータと索引の大きさを記録する。表の合計がプールの 80% を超えたら、試験の値を変えずに止めて諮る
6. 表を1回全部読んで暖める

**合否の回（2回。1回の中身）**

| # | 場面 | 長さ | 前後で行うこと |
|---|---|---|---|
| 1 | 暖め（`refPickerEquals`、判定に数えない） | 1 VU・5 秒 | 後に `hikaricp.connections.max`（`pool=mastersmith-target-db`）を読み、**5 でなければ中止**（R-03） |
| 2 | `refDefinition200` | 10 VU・3 分 | 前後で読み取りのプールの時間切れの累計と待ちの最大を読む |
| 3 | `refDefinition201` | 10 VU・3 分 | 同上 |
| 4 | `refPickerEquals` | 10 VU・3 分 | 同上 |
| 5 | `refLabel` | 10 VU・3 分 | 同上 |
| 6 | `refPickerContains` | 10 VU・3 分 | 同上 |
| 7 | アプリのコンテナを作り直す（前にログを一時の置き場に残す）→ 暖め（1 VU・5 秒）で上限 5 を読み直す → `bizSearchWide` | 10 VU・3 分 | 後に `memory.peak`・`memory.stat` の `anon` の最大・`memory.events` を読む |
| 8 | `refCreateChecked` | 10 VU・3 分 | 前後で2つのプールの時間切れの累計と待ちの最大を読む（書き込みのプールは最初に借りるまで出ない） |
| 9 | `bizCreate`・`bizUpdate` | 各 10 VU・3 分 | 同上 |
| 10 | `bizDelete` | 10 VU・既定 100 回 | 同上 |
| 11 | `bizWritePoolLimit`（409 を正しい答えとして数える） | 10 VU・3 分 | 同上。409 の数を数える |
| 12 | アプリのコンテナを作り直す（前にログを残す）→ `bizCreateLarge` | 10 VU × 既定 3 回 | 後に `memory.peak`・`anon` の最大・`memory.events` と k6 のコンテナの使用量を読む |

- 1回目と2回目の間に、種を入れ直して（2B〜3 と同じ。スキーマを作り直すため `biz_refcreate` と `biz_new` が 0 行に戻る）、6 の暖めをやり直します。`refCreateChecked` は流すたびに `biz_refcreate` に行を作るため、`perf/README.md` が流す前に毎回 0 行にすることを求めています。入れ直すことで、2回目の読み取りも1回目の書き込みの影響を受けません（前の Intent の MySQL の2回目で起きた悪い側への寄り（`project.md` の学び）を避ける）。
- 場面ごとに、アプリと対象DB の `memory.current`・`memory.stat` の `anon` を1秒ごとに記録し、場面の後に対象DB の OOMKilled・`memory.events`（`oom_kill`・`max`）を読みます。
- 各場面の k6 の終了コードを記録します（閾値を満たさないと 99）。
- 深いページのピッカー（PostgreSQL の 1,000 ページ目）の参考の測りは流しません（Build and Test の Q6: A、`perf/README.md` 履歴 H76。U1 の基盤の設計 4.4 の 3 との差）。

**種類ごとの後始末**

7. 確かめの結果を見てから片付ける（`project.md` の学び）: 使い捨てのアプリのログをロガーの名前ごとに数える（WARN・ERROR・JSON でない行・試験用の利用者の名前・行の値の目印・`eyJ` の件数。値は表示しない）、2つのプールの時間切れの累計、OOMKilled、`memory.peak`
8. 片付け: `perfu --profile "targetdb-$KIND" down -v`。ボリュームと網の残りが 0 であることを確かめる

**全体の前後**

- 始める前: AC 電源につなぐ（今は電池で動いている。電池のままでは前の Intent で 414 秒眠った）。`caffeinate -dims` を作業の全体に掛け、台本ごとにも掛ける。`pmset -g assertions` で `PreventUserIdleSystemSleep`・`PreventUserIdleDisplaySleep` が 1 であることを確かめる
- 配備したアプリを止める: `docker compose stop app`（消さず作り直さない）。止める前に状態（healthy・RestartCount・OOMKilled・イメージ ID）を記録する
- 終わった後: `docker compose start app`、healthy と `/actuator/health` 200、コンテナのイメージ ID が `sha256:d8a467b60f6f…` のままであること（`project.md` の学び）、RestartCount、起動のログの WARN・ERROR の件数（ロガーの名前ごと）
- 眠りの確かめ: 試験の窓に `pmset -g log` で `Sleep`・`DarkWake` が無いこと。入っていたら、その回の値を使わずに流し直す

## 4. 見込みの時間と配備したアプリを止める時間

| 区切り | 見込み |
|---|---|
| 止める前の準備（WAR の確かめ・試験のイメージ・一時の置き場） | 約 10 分（止める前に行う） |
| 種類ごとの準備（1〜6） | 約 10〜15 分 |
| 合否の回 1回（12 の区切り。3 分の場面が 9、短い場面と作り直しが 3、場面ごとの setup） | 約 33〜40 分 |
| 回の間の種の入れ直しと暖め | 約 5 分 |
| 種類ごとの後始末（7・8） | 約 5 分 |
| 種類ごとの合計 | 約 85〜100 分 |
| 3種類の合計（配備したアプリを止める時間） | **約 4 時間 15 分〜5 時間**（Q1: A の場合） |

- 前の Intent（261016）では、場面の数がほぼ同じ（1回 11 場面）で種類ごとに約 65〜67 分、止めた時間は種の誤りと決定の待ちを含めて約 4 時間 27 分でした。今回は回の間の種の入れ直しが足されるため、少し長く見込みます。
- Q1 で既存の業務データの読み取りの場面も流す（B）と、1回あたり約 17〜20 分、合計で約 1 時間 40 分〜2 時間増えます。
- 依頼者の決定を待つあいだの扱いは Q2 で決めます（推奨 B: 待ちが 15 分を超えそうなら配備したアプリを一度起動し直す）。

## 5. 合否（「今の基準の一覧」の値。緩めない）

| 対象 | 物差し | 線 | 線の値を持つ場所 |
|---|---|---|---|
| `refDefinition200`・`refDefinition201`・`refPickerEquals`・`refLabel`・`refCreateChecked` | `iteration_duration{scenario:<場面>}` の p95・`checks{scenario:<場面>}` の率 | p95 < 1,000 ms・率 1 | `perf/k6/scenarios.js` の `thresholdsFor` |
| `refPickerContains` | 同上 | p95 < 3,000 ms・率 1 | `REF_P95_LIMIT_MS` |
| `bizCreate`・`bizUpdate`・`bizDelete`・`bizWritePoolLimit` | 同上 | p95 < 1,000 ms・率 1（`bizWritePoolLimit` は 409 を合格として数える） | `thresholdsFor` |
| `bizSearchWide`・`bizCreateLarge` | `checks` の率と、アプリのコンテナの `memory.peak` / `memory.max` | 率 1・**< 0.9**（1,932,735,283 B） | `perf/README.md` の「k6 の外の線」 |
| 対象DB のコンテナ（どの回も） | `anon` の最大 / `memory.max`・OOMKilled・`oom_kill` | 92% 以下・false・0 | `perf/README.md` の「k6 の外の線」 |
| 2つのプール（記録） | 待ちの時間切れの累計の増分・503 の件数（`checks` の率 1 で読む） | 増分 0・0 件 | U1-NFR3.3・U2-NFR3.23（記録の目標） |

- `http_req_duration{name:…}` は colima の VM の時計のずれで崩れうるため、並べて記録するだけです（`project.md` の学び）。
- **回の数**: 種類ごとに2回流し、両方の回で満たせば Met とします（Build and Test の引き継ぎ「合否の回は 2 回」、`project.md` の学び「数 ms の目標は1回の値が揺れる」）。片方だけ満たさないときは、原因（眠り・PC の負荷・時計のずれ・表の状態）をログと記録で確かめてから3回目を流して判定します（前の Intent の Q2: A と同じ扱い）。
- **満たさない場面**: Not Met として、p95・最大・`http_req_duration`・対象DB の設定の実際の値・表の大きさ・プールの待ちの最大と時間切れを並べ、原因の見立てを書きます。コードと設定（試験の値を含む）は変えず、扱いは承認の場で諮ります（U1-NFR2.3・NFR2.5、U2-NFR2.25、U1 の `scalability-design.md` 5節）。`refPickerContains` は MySQL・MariaDB で線を超える見込みがあります（Build and Test の試し走りの MySQL は 1 VU で p95 1,814 ms。前の Intent の `bizSearchContains` は同じ形で MySQL・MariaDB が Not Met）。

## 6. 比べる相手（BT-K6-BASELINE、Build and Test の Q4: A）

比べる相手は Intent 261016-business-data-views の Performance Validation（読み取りのプールの上限 5）の結果です。上限 10 の回は流しません（`perf/README.md` 履歴 H61）。直前の Intent（261025・261027）の値（上限 10）と並べるときは、上限が違うことを書きます。前の Intent の値（`iteration_duration` の p95 ms。1回目 / 2回目）:

| 場面 | PostgreSQL | MySQL | MariaDB |
|---|---|---|---|
| `bizCreate` | 12.48 / 15.10 | 15.95 / — | 20.89 / — |
| `bizUpdate` | 7.21 / 8.05 | 11.31 / — | 14.21 / — |
| `bizDelete` | 15.72 / 35.17 | 14.04 / — | 57.06 / — |
| `bizWritePoolLimit`（409 の数） | 12.71（295,114）/ 12.48（301,998） | 12.25（291,399）/ — | 17.63（198,836）/ — |
| `bizSearchWide` の `memory.peak` | 43.2% / 46.0% | 43.5% / 44.5% | 45.8% / 47.3% |
| `bizCreateLarge` の `memory.peak` | 30.9% / 32.8% | 43.2% / 43.3% | 34.8% / 33.5% |
| （参考）`bizSearchContains`（`refPickerContains` と同じ形の部分一致） | 674.14 / 732.64 | **1,627.03**（2回目は Not Met） | **4,698.24**（Not Met） |

（「—」は、この計画を書く時点で前の記録から写していない値。結果を書くときに `261016-business-data-views/operation/performance-validation/test-results.md` から写す）

参照の6場面は新しい場面のため、前の値はありません。Build and Test の試し走り（1 VU・10 秒）の値は判定にも比べにも使いません（`k6-trial-record.md` 9節）。

## 7. 記録（evidence/）に置くもの

Q3 の推奨（C）の前提で、参照の6場面の summary json（3種類 × 2回 × 6 = 36 ファイル、1つ 約 4 KB）を、`node perf/redact-k6-evidence.mjs` の JSON の口で `operation/performance-validation/evidence/` に置きます。置いた後に `--scan`・`gitleaks dir --redact`・`eyJ` の検索で、どれも 0 件であることを確かめます（`project.md` の Forbidden・学び）。ほかの場面の json とログは `build/perf-results/ref-pv/` に残し、数字だけを `test-results.md` に写します。

## 8. 実施の記録と計画との差（2026-11-01）

依頼者の答えは Q1: A・Q2: B・Q3: C（どれも推奨どおり）で、この計画を直す必要はありませんでした。「始めて」の合図の後、計画どおり流しました。結果は `test-results.md`、判定は `nfr-validation-matrix.md`（Met 10・Not Met 0・Unverified 0）です。

- 配備したアプリを止めた時間は 08:41:42Z〜11:58:54Z の **約 3 時間 17 分**（見込みの 4 時間 15 分〜5 時間より短い）。決定を待つ場面が無く、Q2: B の起動し直しは使っていません。3回目の回もありません。
- 戻した後のイメージ ID は `sha256:d8a467b60f6f…` のまま、healthy・`/actuator/health` 200・救済の WARN 0 件です。

計画・`perf/README.md` との差:

| # | 計画・手順書 | 実際 | 理由・影響 |
|---|---|---|---|
| E1 | 3節「始める前に電源につなぐ」 | 電池で始めた（83%）。10:52Z には電源につながっていたが、終わり（11:59Z）は電池（73%）だった | 依頼者が電池のまま `caffeinate -dims` で包んで流すことを受け入れた（依頼の指示）。作業の全体と台本ごとに `caffeinate -dims` を掛け、`pmset -g log` で窓に `Sleep`・`DarkWake` が 0 件であることを確かめた。流し直した回は無い |
| E2 | README 2B・2B'・確かめの `perfu exec -T "targetdb-$KIND" "${client[@]}"` | `docker exec -i mastersmith-perf-targetdb-<種類>-1 <同じ道具と引数>` | Build and Test の D2 と同じ。同じコンテナ・同じ道具・同じ引数（MySQL・MariaDB は `--default-character-set=utf8mb4`）で、結果は同じ |
| E3 | README の `biz-run.sh`・`ref-run.sh`・`refw-run.sh` と書き込みの手順を別々に流す | リポジトリの外の一時の置き場の台本で、準備・1回の合否の回（12 の区切り）・回の間をそれぞれ1つの台本にし、場面ごとに k6 の終了コード・始めと終わりの時刻・2つのプールの値を記録した | 場面ごとの終了コードと前後の値を漏れなく残すため。場面・順・引数・中止の条件（上限 5）は計画と README のとおり |
| E4 | 3節「回の間に種を入れ直し、6 の暖めをやり直す」 | 種の入れ直しの後に、使い捨てのアプリのコンテナも作り直した（`up -d --wait --force-recreate app`）。内部DB のボリュームは残るため、利用者・DSL・ロールはそのまま使えた | 対象DB のスキーマ（MySQL・MariaDB ではデータベース）を作り直した後に、前の接続をプールに残さないため。2回目の最初の暖めで上限 5 を読み直してから流した。`bizPrepare` の流し直しは要らなかった |
| E5 | 3節「アプリと対象DB の `memory.current`・`anon` を1秒ごとに記録」 | コンテナの中の1つのループで1秒ごとに記録した。アプリの `anon` の最大は、`bizSearchWide`・`bizCreateLarge` の始めと終わりの時刻の窓の中の見本で出した | アプリの見本は書き込みの場面の間も続くため、窓で切らないと場面と関係のない値が混ざる |
| E6 | 2.1「`bizCreateLarge` の k6 のコンテナの使用量を記録」 | 2 秒ごとの `docker stats` で、場面が 5〜7 秒のため各回1〜2点だけ取れた | 前の Intent と同じ程度の記録。MySQL の回だけ 約 240 MiB で、ほかは 約 16 MiB |
| E7 | 1節・U1-NFR3.3・U2-NFR3.23「借りる待ちの最大」 | `hikaricp.connections.acquire` の `MAX`（数分の窓の値）を場面の後に読んだ。前の場面の値が残ることがある（`bizDelete` は `bizUpdate` と同じ値） | Micrometer の `MAX` の性質。記録だけで合否に使わない |
| E8 | 2節「試験のイメージを作る」 | 計画どおり止める前に作った（`sha256:b672c6765fad…`、中の WAR は `4cd5c416…008222f`）。イメージは終わった後も残した | 計画に消す手順が無いため |

## Sources

- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-requirements/performance-requirements.md`（NFR2.1〜NFR2.5）・`scalability-requirements.md`（NFR2.6〜NFR3.3）
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/nfr-requirements/performance-requirements.md`（NFR2.21〜NFR2.25）・`scalability-requirements.md`（NFR2.26〜NFR3.23）
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-design/performance-design.md`・`scalability-design.md`、`u2-reference-write-check/nfr-design/performance-design.md`・`scalability-design.md`
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/infrastructure-design/infrastructure-specification.md` 4節、`u2-reference-write-check/infrastructure-design/infrastructure-specification.md` 4節
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/build-and-test/build-and-test-summary.md`・`build-and-test-questions.md`（Q3〜Q6）・`k6-trial-record.md`
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/observability-setup/dashboards.md`（パネル 41 はサーバー側の参考で、合否は k6 の `iteration_duration` で決める。パネル 46・47 は確かめ済み）
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/deployment-execution/health-check-report.md`（配備したイメージと WAR の SHA-256）
- `aidlc/spaces/default/intents/261016-business-data-views/operation/performance-validation/load-test-plan.md`・`test-results.md`（比べる相手と流し方の前例）
- `perf/README.md`（今の基準の一覧・手順 0〜4・業務データの場面・書き込みの場面・参照の2つの節・伏せ字の節・履歴 H61・H76）
- 2026-11-01 の読み取りだけの確かめ（`colima list`・`colima ssh -- free -m`・`docker ps`・`docker stats --no-stream`・`docker images`・`shasum -a 256`・`pmset -g batt`）
- `aidlc/spaces/default/memory/team.md`・`project.md`（Testing Posture・Forbidden・学び）

## Assumptions & Open Questions

- 503 の件数は、どの場面の `checks` も 200・201・409 だけを合格にするため、`checks` の率 1 を「503 が 0 件」と読みます。
- 回の間の種の入れ直しは、スキーマを作り直してもアプリの DSL（同じ形）とロール・割り当て（内部DB）がそのまま使える前提です。入れ直した後の最初の場面の setup が定義の確かめで失敗したら、`bizPrepare` を流し直します。
- `bizSearchWide` は、参照の読み取りの後・書き込みの前に置きます（`biz_wide` は書き込みで増えないため、どこに置いても読む量は同じ。作り直しで読み取りのプールが作り直されるため、暖めで上限 5 を読み直します）。
- 見込みの時間は前の Intent の実測からの見積もりで、決定の待ち・流し直しは含みません。
- 依頼者に尋ねる点（場面の範囲・止める時間の扱い・記録に置く範囲）は `performance-validation-questions.md` の Q1〜Q3 です。
