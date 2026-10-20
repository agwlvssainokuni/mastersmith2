# 性能の試験の手順 — Intent 261016-business-data-views

この Intent の性能の目標は3つの種類に分かれ、持ち主の段が違う。手順の本体は `perf/README.md` にあり、この文書は、どの目標をどの段がどの手順で判定するかと、この段（Build and Test）で行うことを書く。目標は緩めない（team.md・`project.md`）。

| 種類 | 目標の出典 | 持ち主の段 | この段ですること |
|---|---|---|---|
| 業務データの API の負荷（k6 の `biz*` の場面） | U1 の NFR1.1〜NFR1.3・NFR2.1・NFR2.4・NFR2.5・NFR3.2、U2 の NFR1.4・NFR1.5・NFR2.7〜NFR2.9・NFR3.4 | Performance Validation（Q3: A） | 手順の確かめと `k6 inspect` の確かめまで。目標は `Unverified`（持ち主 Performance Validation） |
| `dslMixed` の `memory.peak` の内訳（U5 の BR3.4・BR3.5・BR4.1〜BR4.3） | U5 の機能設計・NFR 要件 NFR4.5〜NFR4.7、要件 FR16 | Build and Test（Q2: A） | 別の担当がこの段で測る。結果は `memory-peak-measurement.md`（後でできる） |
| 画面の側の時間（E2E 185 の測り） | U3 の NFR1.7・NFR1.8 | Build and Test（判定）、Code Generation（測り） | B5・B6 の測りの値で判定する（`build-and-test-summary.md`） |

## 1. 共通の決まり

- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行う。測る間は配備したアプリを止める（CPU を取り合わないため。colima の VM の余裕のため）。止めた時間を記録する。
- 判定は1回の繰り返しに要求1つの場面の `iteration_duration`（単調な時計）で行い、`http_req_duration{name:…}` は並べて記録するだけ（colima の VM の時計のずれ。`project.md`）。
- VU ごとのアクセストークンは `setup()` で取り、場面の長さはトークンの有効期限より短い 3 分。setup は前の場面の状態に頼らない（`bizPrepare` が作った表・行・DSL・ロールだけを使う。書き込みの場面は自分で行とロールを用意する）。
- 台本の全体を `caffeinate -dims` で包む。流す前に `pmset -g assertions`、流した後に `pmset -g log` で Sleep・DarkWake が無いかを確かめ、入っていたら結果を使わずに流し直す。
- 接続プールの見積もりは、上限に届く形で確かめる（読み取り 10 VU は上限 5 に必ず届く。書き込みは `bizWritePoolLimit`）。待ちの時間切れの累計と待ちの最大を、使い捨てのアプリにだけ公開した `/actuator/metrics` の `hikaricp` で読む（`MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics`。配備したアプリの公開の範囲は変えない）。
- 確かめの結果（待ちの時間切れの累計・`memory.peak`・OOMKilled・WARN の件数・ログに行の値と試験用の利用者のメールアドレスが無いこと・監査の行の数）を見てから片付ける。
- 待ちの最大のような揺れる値があるため、合否の回は複数回流す（`project.md`）。

## 2. 業務データの負荷の場面（Performance Validation が判定）

手順は `perf/README.md` の「業務データの場面（Intent 261016-business-data-views の U1）」と「業務データの書き込みの場面（U2）」。対象DB の種類（PostgreSQL・MySQL・MariaDB）ごとに使い捨ての環境を1つずつ作って流す。

### 2.1 場面と合格の線

| 場面 | 中身 | 合格の線 | 目標 |
|---|---|---|---|
| `bizPrepare` | DSL の投入と適用、ロールと割り当て、作業ロールの保存 | `checks` の率 1 | U1 NFR1.2 |
| `bizDefinition` | 定義（PG の回だけ） | `iteration_duration` の p95 < 1000 ms・`checks` の率 1 | U1 NFR1.1 |
| `bizSearchFirst` | 1ページ目 100 件 × 100 カラム、全体の件数を含む | 同上 | U1 NFR1.1・NFR1.3・AC1.3.7 |
| `bizSearchContains` | 部分一致（表の全部を読む） | 同上 | U1 NFR1.1 |
| `bizSearchLast` | 最後のページ（深い OFFSET） | 同上 | U1 NFR1.1・NFR1.3 |
| `bizSearchLastSorted` | 索引の無いカラムで並べた最後のページ（悪い側の条件。超えたら Not Met として諮る） | 同上 | U1 NFR1.1（D2: A） |
| `bizDetail` | 主キーで1行の詳細（2,000,000 字の値の行を含む） | 同上 | U1 NFR1.1 |
| `bizSearchWide` | 100 件 × 100 カラム × 100 字（約 3 MB） | アプリの `memory.peak` < 上限 2g の 90%・`checks` の率 1（応答時間は記録だけ） | U1 NFR2.5 |
| `bizCreate`・`bizUpdate`・`bizDelete` | 作成・更新・削除 | p95 < 1000 ms・`checks` の率 1 | U2 NFR1.4 |
| `bizWritePoolLimit` | 外部キーの違反の経路（最も長く接続を持つ）、10 VU で書き込みのプールの上限 5 に届く | p95 < 1000 ms、書き込みのプールの待ちの時間切れの累計 0、503 の件数 0 | U2 NFR1.5・NFR2.7 |
| `bizCreateLarge` | 4 MiB 近い本文の作成 | アプリの `memory.peak` < 上限 2g の 90%・`checks` の率 1 | U2 NFR2.8 |

- 読み取りのプールの合格（U1 NFR2.4）: 各場面の p95 < 1 秒、プールの待ちの時間切れの累計の増分 0、503 の件数 0。待ちの最大は記録だけ。
- 3種類の対象DB のどれでも満たす（U1 NFR3.2・U2 NFR3.4）。1種類でも満たさなければ Not Met。
- 規模（U1 NFR2.1）: `biz_item` 100,000 行・`biz_pair` 1,000 行・`biz_wide` 200 行。`bizPrepare` の後に表ごとの行の数を数えて記録する。MySQL・MariaDB の種の SQL はまだ流していない（U1 の D4: A）。最初の回で通ることと行の数を確かめる。
- 監査の行の増え方（U2 NFR2.9）: 監査の行の数（`event_type LIKE 'BUSINESS_%'` の種類と結果ごと）と内部DB のファイルの大きさを、片付ける前に数えて見積もり（1行 約 0.5 KB）と並べる。
- WARN の件数（U2 の G16: A）: `bizWritePoolLimit` の想定どおりの 409（`BUSINESS_REFERENCE_NOT_FOUND`）の WARN を code で絞って外し、外した件数を並べて記録する。

### 2.2 対象DB の設定とメモリの上限

`perf/README.md` の表のとおり。MySQL は `innodb_buffer_pool_size` 768M・`mem_limit` 2304m（U2 の試しの結果。U1 の設計の文書の 1792m とは違う）、MariaDB は 512M・1g、PostgreSQL は `shared_buffers` 256MB・768m。MySQL・MariaDB の回では lgtm を動かさない。対象DB のメモリは「OOMKilled が false、かつ `oom_kill` が 0」で判断し、`anon` の最大が上限の 85% を超えたら依頼者に諮る。

### 2.3 既に分かっている見込み（Code Generation の試し走り。判定ではない）

| 場面 | 試し走り（PG・1 VU・10 秒・既定の設定）の繰り返しの p95 | 見込み |
|---|---|---|
| `bizDefinition` | 2.1 ms | — |
| `bizSearchFirst` | 24.1 ms | — |
| `bizSearchContains` | 162.7 ms（1回目 272.1 ms） | 10 VU では 1 秒を超える見込みが高い（U1 の R-06）。超えたら Not Met として扱いを諮る |
| `bizSearchLast` | 13.1 ms | — |
| `bizSearchLastSorted` | 47.9 ms | MySQL・MariaDB で 1 秒を超えたら Not Met として諮る（U1 の R-05） |
| `bizDetail` | 24.6 ms | — |
| `bizSearchWide` | 48.2 ms（`memory.peak` 506〜621 MiB） | — |
| `bizCreate`・`bizUpdate`・`bizDelete`・`bizWritePoolLimit` | 4.5 ms・1.3 ms・4.6 ms・1.8 ms | 書き込みのプールの待ちの時間切れの累計 0・待ちの最大 5.9 ms |
| `bizCreateLarge` | 205.7 ms（`memory.peak` 618 MiB・`anon` 610 MiB） | — |

### 2.4 この段の確かめ（Q3: A）

```bash
docker run --rm -v "$PWD/perf/k6:/scripts:ro" \
  grafana/k6:2.3.0@sha256:9c2dee7f8ed74d317e4027c06a10f169b625638189de8d4555d0b3486a5aeb34 \
  inspect --include-system-env-vars /scripts/scenarios.js
```

- `--include-system-env-vars` を付けないと場面の名前が `undefined` になる（`project.md`）。`SCENARIO=<名前>` を `-e` で渡し、13 の `biz*` の場面（読み取り 8・書き込み 5）の名前と、閾値の名前（`iteration_duration{scenario:<名前>}`・`checks{scenario:<名前>}`）が出ることを確かめる。
- Code Generation で U1（B2、8場面）と U2（B4、5場面）は確かめ済み。この段の確かめの結果は Step 9 の実行の後に `test-results.md` に足す。

## 3. `dslMixed` の `memory.peak` の内訳（この段で別の担当が測る）

手順は `perf/README.md` の「`dslMixed` の `memory.peak` と `memory.stat` の記録（U5）」。

- 合否（BR3.4・BR3.5）: 既定の接続先の2回で `memory.peak / memory.max < 0.9` が2回とも成り立てば Met。物差しは変えない（`anon` に替えない）。前の物差しの値（`memory.peak` / 上限）を並べて記録する（team.md）。
- 試し（BR4.1〜BR4.3）: 接続先に `RETENTION_TIME=0` を足した別の環境で2回。合否に使わず、効いたかを見て、取り込むかを依頼者に諮る。`settings.txt` の `RETENTION_TIME` が既定の回 45000・試しの回 0 であること。
- 環境: 既定の2回は1つの `KEEP=1` の環境（内部DB のボリュームは膨らんだまま。悪い側の条件）、試しの2回は消してから作り直した環境。`OUT_DIR` を分ける。各回の前にアプリのコンテナを作り直して前の peak を消す（台本の `--force-recreate`）。
- 最初の回で記録のファイルがそろっているか（特に `settings.txt` の値、`k6.txt` の `post_missing_seconds`）を確かめてから続ける。
- 秘密（NFR4.5・NFR4.6）: `secret_counts` の件数がどれも 0、片付けで一時ディレクトリとボリュームが消えたこと（`mastersmith-perf_` の件数 0）。
- 各回の WARN を `warn.txt` で数えてから片付ける。
- 測る間の配備したアプリの停止と起動し直し（`docker compose start app`）。
- 結果と判定は `construction/build-and-test/memory-peak-measurement.md` に置き、`build-and-test-summary.md` と `test-results.md` の `Pending` の行に足す。

## 4. 画面の側の時間（E2E 185 の測り）

- 測りは E2E 185 の中の1件で、既定の1組（light・md・blue・既定の幅）だけで各 5 回測り、中央値で判定する（U3 の NFR1.8）。最大と1回目も並べる。中央値が目標を超えたら Not Met、最大だけが超えたら Met として最大を記録する。値は記録だけで、テストの成否にしない。
- 目標（U3 の NFR1.7）: 件数 100 の表 1.0 秒以内、メニューから 20 件の表 0.5 秒以内、詳細とフォーカス 0.5 秒以内、更新の入力 0.5 秒以内。詳細から 100 件の一覧へ戻る（R-11）1.0 秒以内。
- 値は E2E の json の報告の添付 `u3-screen-ms`。流す: `caffeinate -dims ./gradlew e2eTest -Pe2e.files=e2e/185-business-data-accessibility.e2e.ts`。

## 5. 時間の記録（verify・E2E・CI）

| 記録 | 線 | 出典 |
|---|---|---|
| `./gradlew verify` の時間 | CI の上限 60 分の7割の約 42 分を超えたら、テストの分け方を依頼者に諮る | team.md、U1 NFR7.3・U2 NFR7.4・U3 NFR7.8・U4 NFR7.3 |
| CI の時間 | 同上 | 同上（試行ごとの結果は指揮役が後で足す） |
| E2E の全件の時間 | 基準（前の Intent の 232 件・約 10分35秒）の 1.5 倍（約 15分53秒）を超えたら 185 の組の分け方を諮る（上限で落とさない） | U3 NFR7.8、U4 NFR7.2 |

## Sources

- `perf/README.md`（業務データの場面・業務データの書き込みの場面・`dslMixed` の `memory.peak` と `memory.stat` の記録）、`perf/k6/scenarios.js`・`perf/biz/make-biz-seed.sh`・`perf/make-biz-dsl.mjs`・`perf/dsl-mixed-memory.sh`
- U1 の `nfr-requirements/performance-requirements.md`・`scalability-requirements.md`、U2 の同じ2つ、U3 の `performance-requirements.md`、U5 の `functional-design/` と `nfr-requirements/security-requirements.md`
- U1・U2 の `code-generation/code-summary.md`（試し走りの値、Performance Validation への引き継ぎ）
- `build-and-test-questions.md`（Q2: A・Q3: A）、`aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] `perf/README.md` の U5 の節は「測るのは Build and Test（Performance Validation の段が無いため）」と書くが、この Intent には Performance Validation の段がある。`memory.peak` の持ち主は U5 の設計と Q2: A のとおり Build and Test とし、README の理由の文の食い違いは記録だけにする（承認の場で直すかを諮る）。
- 業務データの負荷の目標は、この段では `Unverified`（持ち主 Performance Validation）。Build and Test の失敗の判定（Unverified を含めると成功にならない）との関わりは `build-and-test-summary.md` の 7節。
