# 基盤の仕様 — U1 u1-businessdata-read

U1 u1-businessdata-read は service の単位で、B2 で作ります。業務データを読み取るだけの3本の API を持ちます（契約 C6・C7）。

- `/api/tables/definition`
- `/api/tables/rows/search`
- `/api/tables/rows/get`

既存のアプリのコンテナと組み込みの H2 をそのまま使います。対象DB は、既存の読み取りの接続（`targetDataSource`）だけを使います。

足すものは次の2つです。

- 一覧・詳細の時間の上限の設定の口（`application.yaml`）
- 負荷の試験の使い捨ての対象DB の「表が入る設定」の口（`docker/perf/compose.yaml`）

配備先は開発者の PC の上のコンテナだけです。クラウドの基盤・IaC・検証環境は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。

## 1. Deployment

| Facet | Choice | Rationale |
|---|---|---|
| Compute model | 既存のアプリのコンテナ1つ（CPU 4・メモリ 2g・最大ヒープ 50%）。変えない | 既存のアプリの中の読み取りの機能。ヒープの見積もりは残る量で最大 約 70MB（`scalability-design.md` 5.2） |
| Instances | 単一のインスタンス | 内部DB が組み込みの H2 のため（`scalability-design.md` 1節） |
| Networking | 既存のまま（127.0.0.1:8080、同じオリジン）。対象DB へは、利用者の設定の接続先へ出ていく | 道は3つに固定で、値は本文で運ぶ（BR10.4） |
| Storage | 内部DB は変えない（表・移行を足さない）。対象DB は利用者のもので、読み取りだけ | U1 は読み取りだけ |
| Environments | 配備は `compose.yaml`、負荷の試験は使い捨ての環境（`docker/perf/compose.yaml`）、CI はテストだけ | `team.md` の Deployment |
| IaC approach | 作らない | 配備先が決まっていない（`project.md`） |
| Resource sizing（アプリ） | 既存のまま | 1要求の接続は、内部DB 1・対象DB 1 の最大（`scalability-design.md` 3節） |
| Resource sizing（perf の対象DB） | 3節の表。業務データの試験のときだけ環境変数で大きくする（Q1: A・Q2: A） | 表が入る設定で測る（NFR 設計 F1: A） |
| Configuration | `application.yaml` に `list`・`detail` の時間の上限（既定 5s）を環境変数で受ける形で足す。`.env.example` にコメントの行を2つ足す。配備の `.env` は変えない（Q4: A） | 既存の生成・照合と同じ形 |
| Secrets | 足さない | 接続の資格情報は、既存の `MASTERSMITH_TARGET_DB_*` のまま |
| Schema migration | 無い | 読み取りだけ |
| Rollback | 直前の版のイメージで起動し直す（イメージだけ）。前の版には3本の API が無い | 移行と `.env` の変更が無い |

### 1.1 一覧・詳細の時間の上限の口（Q4: A）

既存の `generate`・`compare` の並びに足します（`backend/src/main/resources/application.yaml` 93〜98行）。

```yaml
    query-timeout:
      generate: ${MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_GENERATE:20s}
      compare: ${MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_COMPARE:5s}
      # 一覧（件数とページのそれぞれ）と詳細の問い合わせ1回の待ちの上限
      list: ${MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_LIST:5s}
      detail: ${MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_DETAIL:5s}
```

- `.env.example` には、57〜58行の後に `# MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_LIST=` と `# MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_DETAIL=` を足します。
- 貸すときに付ける通信の待ち（目的の上限 + 5 秒 = 10 秒）は、接続ごとの値です。設定の項目にはしません（`logical-components.md` L9）。
- ドライバーの通信の待ち（最も長い上限 + 5 秒 = 25 秒）は変わりません（NFR4.4）。

## 2. Infrastructure Services

| Service | Role | Configuration | Notes |
|---|---|---|---|
| 対象DB の読み取りのプール（`mastersmith-target-db`、HikariCP） | database（接続） | 上限 5・借りる待ち 3s・接続の待ち 3s・ドライバーの通信の待ち 25s。変えない | 貸すたびに `setNetworkTimeout`（10 秒）を付けて戻す（F2: A）。借りる失敗は 3 秒（MySQL で確かめが走ると 6 秒）で 503（`reliability-design.md` 1.2） |
| 内部DB（組み込みの H2） | database（読み取りだけ） | 変えない | 権限の写しの短い読み取りだけ。対象DB を借りている間は 0 本 |
| 見本の対象DB（`compose.yaml` の `targetdb-postgres`） | database（手元の確かめ・E2E） | 変えない | U3 の E2E 180 の扱いは U3 の基盤の設計 |
| perf の対象DB（`docker/perf/compose.yaml` の `targetdb-mysql`・`targetdb-mariadb`・`targetdb-postgres`） | 負荷の試験の database | 2.1 の表 | profile で1つずつ起動する。種・暖め・記録の決まりは 2.2 |
| cache・queue・search・CDN・DNS・load-balancer | — | 使わない | キャッシュは置かない |

### 2.1 perf の対象DB の設定（Q1: A・Q2: A）

DB の設定とメモリの上限を、シェルの環境変数で受けます。既定の値は今の値のままにし、DSL の時間の測り（`perf/dsl-timing.sh`。配備したアプリを止めない前提）は変えません。業務データの試験の手順では、次の値を export してから起動します。

| DB | 設定 | 既定（今の値） | 業務データの試験の仮の値 | 根拠 |
|---|---|---|---|---|
| MySQL | `--innodb-buffer-pool-size` | 128M | 768M | 試しの表 532MB × 1.25 を 128MiB の倍数に上げた値 |
| MySQL | `mem_limit` | 768m | 1792m | プール 768 ＋ U2 で実測したプール以外の量 約 614 MiB（742 − 128）＋ 余裕 約 410 MiB |
| MariaDB | `--innodb-buffer-pool-size` | 128M | 512M | 試しの表 372MB × 1.25 を上げた値 |
| MariaDB | `mem_limit` | 512m | 1g | プール 512 ＋ プール以外の量と余裕（実測なし。6節） |
| PG | `-c shared_buffers` | 128MB | 256MB | 試しの表 198MB × 1.25 を上げた値（R-06 への答え） |
| PG | `mem_limit` | 512m | 768m | 共有バッファー 256 ＋ 接続とページキャッシュの分 |

形の例（MySQL。変数の名前は B2 で決める）:

```yaml
  targetdb-mysql:
    command:
      - mysqld
      - --innodb-buffer-pool-size=${MASTERSMITH_PERF_TARGETDB_MYSQL_BUFFER_POOL:-128M}
    mem_limit: ${MASTERSMITH_PERF_TARGETDB_MYSQL_MEMORY:-768m}
```

### 2.2 値を確かめて記録する決まり

1. Performance Validation で `bizPrepare` の後に、表のデータと索引の大きさを実測する。
2. 表の大きさがプール（PG は共有バッファー）の 80% を超えたら、128MiB ずつ上げる。メモリの上限も同じだけ上げる。
3. 測る前に、表を1回全部読んで暖める。
4. 次を記録する。
   - 設定の実際の値（`@@innodb_buffer_pool_size`・`SHOW shared_buffers`）
   - コンテナのメモリの上限（`docker inspect` の Memory）
   - 表の大きさ（データと索引）
   - 対象DB のコンテナの `memory.peak` と OOMKilled の有無
5. U2 の書き込みの場面も、同じ値で測る（U2 F1: A）。

### 2.3 colima の VM に収まるか（Q3: A）

判定の線は、同時に動くコンテナのメモリの上限の合計が、docker から見える量（約 5,910 MiB）以下であることです。

| 動かす組み合わせ | 上限の合計 | 判定 |
|---|---|---|
| perf のアプリ（2g）＋ perf の MySQL（1792m）＋ 見本の PG（512m）・Mailpit（256m）。配備したアプリは止める | 4,608 MiB | 収まる |
| 上と同じで、配備したアプリ（2g）も動かしたまま | 6,656 MiB | 収まらない |
| perf のアプリ ＋ perf の MySQL ＋ lgtm（1536m）＋ 見本の PG・Mailpit | 6,656 MiB | 収まらない |
| perf のアプリ ＋ perf の PG（768m）＋ lgtm ＋ 見本の PG・Mailpit（Observability Setup の R-05） | 5,120 MiB | 収まる（余裕は小さい） |
| DSL の時間の測り（既定の値のまま。配備したアプリも動かす） | 5,632 MiB | 収まる（今のまま） |

- 業務データの負荷の試験の間は、配備したアプリを止めます（`perf/README.md` の手順 0 と同じ）。
- MySQL・MariaDB の回では lgtm を同時に動かしません。指標は、使い捨てのアプリにだけ公開する actuator の metrics で読みます。
- Observability Setup の R-05 の実測は、PG（768m）で行います。
- 2.2 の上げ方で線を超えるときは、その時点で依頼者に諮ります。VM は変えません。

## 3. Shared Infrastructure

| Shared Resource | Owner Unit | Consumer Units | Access Boundary |
|---|---|---|---|
| perf の対象DB の設定の口と値（2.1） | U1 u1-businessdata-read | U2 u2-businessdata-write（同じ値で測る） | `docker/perf/compose.yaml`。値は試験の手順で export する。U2 は書き込みのアカウントの初期化を足す |
| `bizPrepare` の種の台本（`perf/` の下） | U1（B2 で作る） | U2（B4 の書き込みの場面で使う） | 種は台本で決めた値から作る（`team.md`）。値はすべて架空 |
| `targetdb` の読み取りの口・`ReadPurpose`・`QueryTimeout` | U1 | U2（書き込みの口を同じ形で足す） | `targetdb.service` の貸す口からだけ使う（NFR4.3） |
| ダッシュボードの「業務データ」の区画 | Observability Setup | U1・U2 | `monitoring-design.md` 5節 |

## 4. 上流との差（承認済みの文書は書き換えない）

| 上流 | 上流の書き方 | この設計 | 理由 |
|---|---|---|---|
| `nfr-design/logical-components.md` 5節 | 対象DB のプールと時間の上限の値は既定のままにし、「`.env` の項目は増えない」 | `application.yaml` で `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_LIST`・`_DETAIL`（既定 5s）を受け、`.env.example` にコメントの行を足す。配備の `.env` は変えない | Q4: A。既存の生成・照合と同じ形にそろえるため。「`.env` に値を入れなくてよい」と読む |
| `nfr-design/observability-design.md` 2節 | 元の指標を `http.server.requests` と書く | 手元の監視の名前は `http_server_requests_milliseconds_*` | 既存の式（`docker/monitoring/`）の名前。中身は同じ（`monitoring-design.md` 7節） |
| `nfr-design/performance-design.md` 4節 | PG は「共有バッファー」の名前だけを書く（R-06） | PG は `shared_buffers` を 256MB、メモリの上限を 768m にする | R-06 への答え（Q2: A） |

## 5. 後の段に回すこと

- **コード生成の計画（B2）**
  - 環境変数の名前を決める（2.1 の perf の変数と、1.1 の `list`・`detail`）。
  - `perf/README.md` に業務データの節（export する値・配備したアプリを止める・lgtm を動かさない）を足す。
  - k6 の場面と `bizPrepare` の種の台本（`cicd-pipeline.md` 5節）
  - SpotBugs の `SQL_` の除外と、`RoleBoundaryArchitectureTest` の変更の承認
- **Performance Validation**
  - 2.2 の実測・上げ方・暖め・記録
  - 合否の回
- **Observability Setup**
  - ダッシュボードの区画
  - R-05 の実測（PG を止めた状態と `docker pause` の状態）

## 6. U2・U3 とそろえる点

- **U2**
  - perf の MySQL の上限 1792m は、U2 の 4 MiB × 5 本の書き込みを見込んだ値です。U2 も 2.1 の同じ値で測り、対象DB の `memory.peak` と OOMKilled の有無を記録します。書き込みのプールを 10 に上げる案は採りません（U2 F1: A）。
  - 書き込みの設定の項目は、1.1 と同じ形（`application.yaml` で環境変数を受け、`.env.example` にコメントの行）にそろえます。
- **U3**
  - E2E 180 は、配備の compose の見本の PG を使います。perf の compose の既定の値は変わらないため、E2E に響きません。
  - 画面の時間の目標は、U1 の Performance Validation の結果に合わせます。

## 7. 未確認の点

- **VM の中の空き**: VM の OS・dockerd・k6 のコンテナが使う量は測っていません。2.3 の判定は、上限の合計と docker から見える量の比べだけです。
- **MariaDB の実測**: MariaDB のバッファープール以外の量は実測がありません。1g は見立ての値で、Performance Validation の `memory.peak` で確かめます。
- **MySQL の丸め**: MySQL 26.7 は、バッファープールの値をチャンクの単位に丸めます。実際の値は起動して確かめ、記録します。

## Sources

- 答え: `aidlc/spaces/default/intents/261016-business-data-views/construction/u1-businessdata-read/infrastructure-design/infrastructure-design-questions.md`（Q1〜Q4: A、まとめの確認は Looks correct）
- 上流（どれも `aidlc/spaces/default/intents/261016-business-data-views/` の下）
  - `construction/u1-businessdata-read/nfr-design/` の `performance-design.md`（4節・7節）・`scalability-design.md`（1節・3節・4節・5節・7節）・`reliability-design.md`（1.2・5節）・`security-design.md`（5節）・`observability-design.md`・`logical-components.md`（L9・L14・5節）・`nfr-design-followup-questions.md`（F1〜F3）
  - `construction/u1-businessdata-read/nfr-requirements/`（NFR2.1・NFR2.4・NFR4.3・NFR4.4）
  - `construction/u1-businessdata-read/functional-design/functional-spec.md`
  - `inception/domain-design/components.md`、`inception/contract-design/contract-summary.md`（C6・C7）
  - `.aidlc-reviews/nfr-design/units/u1-businessdata-read/df13e747868fc8fa/1.json`（R-05・R-06）
  - `construction/u2-businessdata-write/nfr-design/nfr-design-followup-questions.md`（F1: A）
- 既存のもの（正とする。読むだけ）: `compose.yaml`・`docker/perf/compose.yaml`・`Dockerfile`・`backend/src/main/resources/application.yaml`・`backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDbProperties.java`・`.env.example`・`perf/README.md`・`perf/dsl-timing.sh`
- この PC の実行環境: `colima list`・`docker ps`・`docker stats --no-stream`・`docker info`（2026-10-19、読み取りだけ）

## Assumptions & Open Questions

- 前提: 試しの表の大きさ（PG 198MB・MySQL 532MB・MariaDB 372MB）は、B2 の `bizPrepare` の種でも近い値になると見立てた。違えば 2.2 の決まりで上げる。
- 前提: U2 の書き込みで実測したプール以外の量（約 614 MiB）は、プールを大きくしても増えないと見立てた（未検証）。
- 依頼者の決定（Q1〜Q4: A）は、1節・2節・4節に反映した。
- 諮る点は無い。2.3 の線を超えるときだけ、Performance Validation の時点で諮る。
