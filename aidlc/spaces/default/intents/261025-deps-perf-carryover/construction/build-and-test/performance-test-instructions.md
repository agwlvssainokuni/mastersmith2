# 性能の確かめの手順（261025-deps-perf-carryover）

**Collaborator:** aidlc-quality-agent

範囲は bugfix（深さ Minimal、Test Strategy Minimal）。この Intent の流れには Performance Validation の段が無いため、負荷の試験と測定は Build and Test が持つ（`project.md` の決まり）。この手順は、`construction/code-generation/unit-test-instructions.md` の 7.1 を元に、順番・判定・記録の形を決めたものです。操作の細かいコマンドは `perf/README.md` の該当の節を正とし、ここでは「どの節をどの値で流し、何を記録し、何で判定するか」を書きます。

**目標は緩めない。** 線・物差しは前の Intent の `aidlc/spaces/default/intents/261016-business-data-views/operation/performance-validation/nfr-validation-matrix.md` のまま。直した後も Not Met が残った件は、Not Met のまま記録して次の Intent へ持ち越す（要件 FR4.9・F1: A）。

## 0. 共通

1. 長い試験は台本の全体を `caffeinate -dims` で包む（場面ごとに起こし直さない）。流す前に `pmset -g assertions` で眠らない状態を、流した後に `pmset -g log` で窓の中に `Sleep`・`DarkWake` が無いことを確かめる。入っていたらその回は使わずに流し直す。
2. 始めに `source perf/versions.env`（`K6_IMAGE`・`PERF_JRE_IMAGE`）。H2 に SQL を流すのは `perf/h2-shell.sh`。zsh ではコマンドを変数に入れず、関数にする。
3. 使い捨ての環境（`docker/perf/compose.yaml`、プロジェクト名 `mastersmith-perf`）は、配備した環境とは別。仮の鍵・仮の利用者で、終わったら消す。一時の環境ファイルの置き場はホームの下（権限 700。macOS の一時ディレクトリは colima の VM から見えない）。
4. 配備したアプリ（`mastersmith-app-1`）は、負荷の試験のあいだ **止める**（`docker compose stop app`。`up` ではなく `stop` にして、試験の後に `docker compose start app` で同じ状態に戻せる形にする）。止めた時刻と戻した時刻を記録する（約 4〜5 時間の見込み）。見本の対象DB（PostgreSQL）と Mailpit は止めない。
5. 確かめの結果（時間切れの累計・`memory.peak`・OOMKilled・WARN の件数）を **見てから** 片付ける。消してから取り直せない。
6. 試験の値・件数・時間は、名前と数だけを記録する。資格情報・接続先・行の値・メールアドレスは記録に書かない。
7. 結果の置き場は `build/perf-results/`（git の対象外）。記録は `construction/build-and-test/test-results.md` に写す。

## 1. 3 種類の対象DB の業務データの負荷の試験（FR4.9・FR4.1・FR4.2）

### 1.1 順序

PostgreSQL → MariaDB → MySQL の順で、1 種類ずつ流す（MySQL の結果が出るまで、他の決めを待たない）。MySQL・MariaDB の回は手元の監視（lgtm）を動かさない（VM の余裕）。各種類で、読み取りの場面を先に流し、書き込みの場面はその後（FR4.6 の流す順の見直し）。書き込みの後に読み取りの値が要るときは、対象DB を作り直して種を入れ直す。

### 1.2 環境と値

手順は `perf/README.md` の「業務データの場面」の 1B〜6B と「業務データの書き込みの場面」。

| DB | バッファープール | メモリの上限 | 備考 |
|---|---|---|---|
| PostgreSQL | `shared_buffers` 256MB | 768m | |
| MariaDB | 896M | 1408m | |
| MySQL | 1280M | 2688m | VM の余裕を残すため決まりより 128 MiB 少ない（依頼者の決定、前の Intent） |

- 値は `MASTERSMITH_PERF_TARGETDB_*` を export してから起動する。実際の `docker inspect` の `HostConfig.Memory` を記録する（`perf/README.md` の値と違えば差を記録）。
- アプリの読み取りのプールの既定は **10**（B3 で 5 から変更）。使い捨てのアプリに `MASTERSMITH_TARGET_DB_POOL_MAXIMUM_SIZE` を渡さず、既定の 10 で動いていることを `hikaricp.connections.max`（`pool="mastersmith-target-db"`）が 10 であることで確かめる。使い捨てのアプリにだけ `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics` を渡す。
- 種は `perf/biz/make-biz-seed.sh <種類>` を、**MySQL・MariaDB では `--default-character-set=utf8mb4` を付けた道具**で流す（README の 2B のとおり）。
- **種の確かめ（2B'）は表の大きさを測る前に必ず通す**: 目印の語を含む行の数が 0 でない、`c_010` の最長が 2,000,000 字、行の数が `biz_item` 100,000・`biz_pair` 1,000・`biz_wide` 200（U1-NFR2.1 の記録）。
- 表のデータと索引の大きさを測り、プール（PG は共有バッファー）の 80% を超えたら、128MiB ずつ上げてメモリの上限も同じだけ上げる（前の Intent の実績が正しければ上げずに済む。割合を記録する）。

### 1.3 場面と線（前回と同じ）

| 場面 | 流し方 | 線 |
|---|---|---|
| `bizPrepare` | 1 VU・1 回 | `checks` の率 1 |
| `bizDefinition`（PG だけ） | 10 VU・3 分 | `iteration_duration` の p95 < 1000 ms、`checks` の率 1 |
| `bizSearchFirst`・`bizSearchContains`・`bizSearchLast`・`bizSearchLastSorted`・`bizDetail` | 10 VU・3 分、**合否の回を 2 回** | 同上。さらに、場面ごとに前後で対象DB のプール（`pool="mastersmith-target-db"`）の待ちの時間切れの累計の増分 0・503 の件数 0 |
| `bizSearchContains` の上限に届く形 | `-e VUS=12`（読み取りのプール 10 に対し 12 VU）、3 分 | 記録（待ちの最大・時間切れ・503）。FR4.1 の「プール 10 で上限に届いたときの挙動」の資料 |
| `bizSearchWide` | 10 VU。**前にアプリのコンテナを作り直して前の `memory.peak` を消す**（作り直す前にログを `$D` に残す） | アプリの `memory.peak` < 上限 2g の 90%（1,843.2 MiB）、`anon` の最大を並べる、`checks` の率 1。応答時間は記録だけ |
| `bizCreate`・`bizUpdate`・`bizDelete`・`bizWritePoolLimit` | 10 VU・3 分（書き込みの場面。読み取りの後） | `iteration_duration` の p95 < 1000 ms、`checks` の率 1、書き込みのプール（`mastersmith-target-db-write`、上限 5）の時間切れの増分 0・503 0 |
| `bizCreateLarge` | 10 VU × 3 回。前にアプリのコンテナを作り直す | アプリの `memory.peak` < 2g の 90%、`anon` を並べる、`checks` の率 1 |

- **判定は `iteration_duration`（単調な時計）で行う**。`http_req_duration` は colima の VM の時計のずれで崩れうるため、並べて記録するだけ。
- 揺れる値（待ちの最大）は、合否の回を複数回流して並べる。2 回で一致しないときは原因を確かめてから 3 回目を流す（前回の MySQL の例）。原因を確かめずに片方を採らない。
- アプリが止まる・極端に遅いなどが出たら、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。

### 1.4 対象DB の合格の条件（FR4.2）

各種類・各場面の後に、対象DB のコンテナについて次を記録する。

```bash
docker inspect -f '{{.State.OOMKilled}}' <対象DB のコンテナ>                      # false であること
docker exec <対象DB のコンテナ> sh -c 'grep -E "^(oom_kill|max) " /sys/fs/cgroup/memory.events'   # oom_kill が 0
docker exec <対象DB のコンテナ> sh -c 'grep -E "^(anon|file) " /sys/fs/cgroup/memory.stat'        # anon の最大は 1 秒ごとの記録から読む
```

- **合格の条件**: OOMKilled が false で `oom_kill` が 0。**どちらかが 0 でなければ、プール 10 は採らず、既定を 5 に戻して依頼者に諮る**（変えず、報告する。コードの変更はこの段で行わない）。
- `anon` の最大 ÷ 上限 は **85% の線**（諮る線）と並べる。超えたときは合格の条件で Met とし、超えた値を並べて依頼者に諮る（前回は MariaDB 85.01%・MySQL 90.3%）。

### 1.5 前回との並記（NFR1）

Not Met 4 件（U1-NFR1.1・U1-NFR1.3・U1-NFR3.2・U1-NFR2.4）は、場面 × 種類ごとに、前回の値と今回の値を並べる。前回の値は `nfr-validation-matrix.md` 1 節と `test-results.md`。Met になった件・ならなかった件を件数で数える。

## 2. `dslMixed` の `memory.peak`（FR4.5）

手順は `perf/README.md` の「(b) 大きな入力のヒープ」と `perf/dsl-mixed-memory.sh`。

- 既定の回を **2 回**、`RETENTION_TIME=0`（H2 の保持の設定）の試しの回を **2 回**。各回の前に使い捨てのアプリを作り直して前の `memory.peak` を消す。
- **合否**: `memory.peak / memory.max` < **0.90**（上限 2g）。物差しは `memory.peak`（`anon` に替えない）。2 回とも成り立てば Met。前回の値（既定の回 0.8731・0.8696、試しの回 0.7400・0.7175）と並べる。
- 並べる値: `anon` の最大 / `memory.max`、peak の時の `anon`・`file`・`inactive_file`・`kernel`、内部DB のファイルの大きさの差。
- `RETENTION_TIME=0` を配備物に入れるかは、**測った結果を添え、書き込みの途中で止まったときに壊れうる点も添えて、依頼者に諮る**（この段では配備物を変えない）。

## 3. 画面の時間（FR4.7・U3-NFR1.7a〜e）

`./gradlew e2eTest` の全件の流しの中の E2E 185（`frontend/e2e/185-business-data-accessibility.e2e.ts` の `measure business data screen time with the default combo`）が、5 つの判定をそれぞれ **5 回ずつ**測り、中央値・最大・1 回目・「塗りまで」を `u3-screen-ms` の注記として報告 json に書く。

| ID | 判定 | 線（中央値） | 前回（B6） |
|---|---|---|---|
| U3-NFR1.7a | `listShown100`（件数 100 を選んでから 100 件 × 100 カラムの表まで） | 1.0 秒以内 | 70.1 ms |
| U3-NFR1.7b | `listShown20`（メニューから既定の 20 件の表まで） | 0.5 秒以内 | 50.1 ms |
| U3-NFR1.7c | `detailFocused`（行の「詳細」から 100 カラムと見出しのフォーカスまで） | 0.5 秒以内 | 45.2 ms |
| U3-NFR1.7d | `formShown100`（「更新」から 100 カラムの入力まで） | 0.5 秒以内 | 32.8 ms |
| U3-NFR1.7e | `listShown100Back`（詳細から 100 件の一覧へ戻るまで） | 1.0 秒以内 | 179.0 ms |

- 読み取り方: `jq` で `frontend/test-results/e2e-results.json` から `u3-screen-ms` の注記だけを取り出す（名前と時間だけ。値は他に出さない）。測れない回があれば `note` を記録する。
- どれかが線を超えたら Not Met とし、緩めない。5 回が揃わない（測れない回がある）ときは、判定に使える回数を記録する。

## 4. 置き場の p95 の替え（P6）

測る対象だった `GET /api/me/table-access` は B3（FR3.3）で消え、台本の場面も外したため、測り直せない。代わりに、残っている業務のメニュー `navMenu`（`GET /api/me/navigation`、10 VU・3 分）の `iteration_duration` の p95 を測り直して記録し、前回の置き場の p95（`261004-role-menu/operation/performance-validation/nfr-validation-matrix.md`）と並べ、**締める案を提案するだけ**にする。目標の数値は変えない。手順は `perf/README.md` の「業務のメニューの場面」。

## 5. DSL の投入と権限の YAML の確かめの重なり（P5）

`perf/README.md` の「DSL の投入と権限の YAML の確かめを重ねる場面」。前提の準備（`rolePrepare` と大きな DSL・`perf-roleop01`、10MB の DSL、上限ちょうどの権限の YAML）を済ませてから `dslRoleOverlap` を流す。`checks{scenario:dslHeavy}`・`checks{scenario:roleChecks}` の率 1、`iteration_duration{scenario:roleChecks}` の最大 < 15 秒を記録する。重なる間の 5xx・時間切れの件数はアプリのログの WARN・ERROR の件数だけで数える。

## 6. 手元の監視の式の値（FR3.8）

`monitorLight`（1 VU・繰り返しごとに 1〜2 秒の待ち）を、lgtm を使い捨ての環境のネットワークに **先に** つないだ後にアプリを起動して流す（先に起動するとログの送信の失敗の ERROR が出る）。業務データの式は `MONITOR_BIZ=1`（先に `bizPrepare`）。判定: lgtm（`otelcol`・Tempo）が止まらず、監視の式の値が返る。`checks{scenario:monitorLight}` の率 1。MySQL・MariaDB の回とは別に、PostgreSQL の回の後に流す（VM の余裕）。

## 7. 単体テストの JVM のヒープ（P7）

`verify` の実測で、単体テスト（1g）・結合テスト（1536m）の JVM が `OutOfMemoryError` で尽きていないこと。`backend/build/test-results/` の XML と `verify` の出力を `OutOfMemoryError`・`GC overhead` で検索して 0 件。尽きたときだけ、境界テストの読み込みを共有する直しを提案する（この段ではコードを変えない）。

## 8. 判定と記録

- 各目標について、`Met`（線を満たす）・`Not Met`（満たさない。緩めず記録）・`Unverified`（流せなかった。持ち主の段と証拠の場所を書く）の 1 つ。
- 記録の形は `test-results.md` の「Target Verification Matrix」の行（Target ID・Source・Expected・Actual・Evidence・Owning Stage・Verdict）。Actual は場面 × 種類の値、Evidence は `build/perf-results/` の報告の名前とコマンドの出力の要約。
- 片付ける前に、時間切れの累計・`memory.peak`・OOMKilled・WARN の件数・ログに行の値と試験用の利用者のメールアドレスが無いこと・監査の行の数（`event_type LIKE 'BUSINESS_%'`）を見てから、`perfu --profile "targetdb-$KIND" down -v` で消し、export した変数を `unset` し、配備したアプリを `docker compose start app` で戻して `healthy` を確かめる。
