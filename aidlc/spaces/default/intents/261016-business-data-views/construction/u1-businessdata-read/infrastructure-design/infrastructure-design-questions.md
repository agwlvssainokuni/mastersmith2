# Infrastructure Design の質問 — U1 u1-businessdata-read

配備先は、開発者の PC の上のコンテナだけです。この段は、次の4つの範囲で書きます。

- 手元の compose・perf の環境
- `./gradlew verify`
- GitHub Actions
- 手元の監視

## 前の段から持ち越した論点

- NFR 設計 F1: A。Performance Validation の対象DB を「表が入る設定」にして測る。値は、表の大きさの実測とメモリの上限をあわせて決める。
- U2 の F1: A。perf の MySQL は、4 MiB の本文を5本同時に書くと 97% まで使う。その分も見込む。
- NFR 設計のレビューの R-05（主キー以外の並べ替えの深いページ）と R-06（PG の共有バッファー）。
- 一覧・詳細の時間の上限（5 秒）の設定の口。

## この PC の実行環境（読み取りで確かめた結果）

**colima**
- CPU 4・メモリ 6GiB（docker から見える量は約 5,910 MiB）。

**今動いているコンテナ**

| コンテナ | 使っている量 / 上限 |
|---|---|
| 配備したアプリ | 673 MiB / 2 GiB |
| 見本の PG | 56 MiB / 512 MiB |
| Mailpit | 38 MiB / 256 MiB |

上限の合計は 2,816 MiB です。lgtm は止まっています。

**perf の対象DB**
- メモリの上限は PG 512m・MySQL 768m・MariaDB 512m です。
- DB の設定は既定のまま（バッファープール・共有バッファーは 128MB）です。
- 同じ compose を DSL の時間の測り（`perf/dsl-timing.sh`）も使っています。これは配備したアプリを止めない前提です。

**試しの表の大きさ**
- PG 198MB・MySQL 532MB・MariaDB 372MB。

## 決まっていること（質問にしない）

- アプリのコンテナ（CPU 4・2g・最大ヒープ 50%）は変えない。内部DB・移行・秘密も足さない。戻しはイメージだけ。
- 対象DB の読み取りのプール（5）・借りる待ち（3s）・生成 20s と照合 5s の上限は変えない。新しい接続の Bean は作らない。
- 監視
  - 1000 ms のバケットはある。
  - 指標の名前は `http_server_requests_milliseconds_*`、プールは `mastersmith-target-db`。
  - 警報は足さない。既存の警報が拾えるか（R-05）は Observability Setup で実測する。
- ダッシュボードの区画の持ち主は Observability Setup とする（前の Intent の role-menu と同じ形）。
  - 区画は「業務データ」を1つ足し、U1・U2 のパネルを入れる。
  - B2・B4 は `docker/monitoring/` に触れない。
- CI
  - `ci.yml` は変えない。3種類の対象DB の結合テストは verify で毎回流す。100,000 行の種は verify に入れない。
  - B2 の後と Build and Test で、verify と CI の時間を記録する（直近の verify は 14 分 1 秒。42 分を超えたら諮る）。
- E2E
  - U1 は認証済みの API と業務データのパッケージを足すため、B2 の統合の前に E2E の全体を流す。
  - U1 の E2E のファイルは足さない。
- 負荷の試験
  - `caffeinate -dims` で包む。配備したアプリは止める。片付けは確かめた後に行う。setup は前の状態に頼らない。判定は `iteration_duration` で行う。
  - 台本は B2 が書き、流して判定するのは Performance Validation とする。
  - `bizSearchLast` に、主キー以外で並べ替える深いページを足す（R-05）。

## Q1. perf の対象DB の「表が入る設定」の置き場

A. 3つの対象DB に DB の設定（`--innodb-buffer-pool-size`・`-c shared_buffers`）を足し、その値とメモリの上限をシェルの環境変数で受ける（推奨）。
- 既定の値は今の値のままにし、DSL の時間の測りは変えない。
- 業務データの場面の手順（README に足す節）では、表が入る値を export してから起動する。

B. compose の既定の値を、表が入る値に変える。DSL の時間の測りの README を、「MySQL・MariaDB の回は配備したアプリを止める」に直す。

C. 業務データの専用の重ねるファイル（`docker/perf/compose.bizdata.yaml`）を足し、`-f` で重ねて起動する。

X. Other (please specify)

[Answer]: A

## Q2. 設定の値の決め方

A. 仮の値を今決め、Performance Validation で表の大きさを実測して確かめる（推奨）。
- 仮の値は次のとおり。

| DB | プール・共有バッファー | メモリの上限 |
|---|---|---|
| MySQL | 768M | 1792m（U2 の書き込みの分を見込む） |
| MariaDB | 512M | 1g |
| PG | 256MB | 768m（R-06 の答え） |

- 表の大きさがプールの 80% を超えたら、128MiB ずつ上げる。
- 測る前に、表を1回全部読んで暖める。
- 設定の実際の値・表の大きさ・対象DB のコンテナの `memory.peak` を記録する。
- U2 も同じ値で測る。

B. 今は式だけを決め、値はすべて Performance Validation で実測してから決める。

C. 3種類とも同じ大きな値にする（例: プール 1G・上限 2g）。VM の余裕が減る。

X. Other (please specify)

[Answer]: A

## Q3. VM に入りきらないときの扱い

Q2: A の値で上限の合計を出すと、次のようになります。

| 動かす組み合わせ | 上限の合計 | VM（約 5,910 MiB）に |
|---|---|---|
| perf のアプリ＋MySQL＋見本の PG・Mailpit（配備したアプリを止める） | 4,608 MiB | 収まる |
| 上と同じで、配備したアプリも動かしたまま | 6,656 MiB | 収まらない |
| MySQL＋lgtm を同時に動かす | 6,656 MiB | 収まらない |

A. 業務データの負荷の試験の間は配備したアプリを止める（今の手順と同じ）。MySQL・MariaDB の回では lgtm を同時に動かさず、指標は actuator の metrics で読む。Observability Setup の R-05 の実測は PG（768m）で行う。Q2 の上げ方で線を超えるときは、その時点で諮る。VM は変えない（推奨）。

B. colima の VM を 8GiB に広げる。PC 全体に響き、VM の作り直しの記録が要る。

C. 入りきらなければ、種の行の数を減らす。NFR2.1 の悪い側の規模との差として記録する。

X. Other (please specify)

[Answer]: A

## Q4. 一覧・詳細の時間の上限の設定の口

NFR 設計の logical-components.md 5節には「`.env` の項目は増えない」とあります。ただし、既存の生成・照合の上限は、どちらも環境変数で受けています（`application.yaml` 95〜98 行、`.env.example` 57〜58 行）。

A. 既存と同じ形にそろえる（推奨）。
- `application.yaml` で `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_LIST`・`_DETAIL`（既定 5s）を受ける。
- `.env.example` に、コメントの行を2つ足す。
- 配備の `.env` は変えない（既定の値で動く）。
- 「`.env` の項目は増えない」は「`.env` に値を入れなくてよい」と読み、差として記録する。

B. 環境変数にせず、Java の既定の値だけにする（既存の2つと形が違い、値を変えるにはコードの変更が要る）。

X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

**infrastructure-specification**
- アプリ・内部DB・移行・秘密・対象DB の読み取りのプールは変えない。戻しはイメージだけ。
- perf の対象DB は次の設定にする。
  - 設定（バッファープール・共有バッファー）とメモリの上限は、環境変数で受ける。既定は今の値のまま（Q1: A）。
  - 業務データの試験の値は MySQL 768M／1792m、MariaDB 512M／1g、PG 256MB／768m（Q2: A）。
  - 表の大きさを実測し、80% を超えたら上げる。暖めてから測る。実際の値・表の大きさ・`memory.peak` を記録する。
  - U2 も同じ値で測る。
- 試験の間は配備したアプリを止める。MySQL・MariaDB の回は lgtm と同時に動かさない。線を超えるときは、その時点で諮る（Q3: A）。
- 一覧・詳細の時間の上限は、環境変数（既定 5s）で受ける。`.env.example` にコメントの行を足し、配備の `.env` は変えない。「`.env` の項目は増えない」との読み方の差を記録する（Q4: A）。
- 共有の資源（perf の設定・種の台本・読み取りの口）の持ち主は U1、使う側は U2 とする。

**monitoring-design**
- 指標は、3本の p95、`status="503"`、`pool="mastersmith-target-db"` の使用中・待ち・待ちの時間切れ。
- 警報は足さない。既存の警報が拾えるか（R-05）は、Observability Setup で PG を止めた状態と `docker pause` の状態で実測する。
- SLO の目標は、k6 の `iteration_duration` の p95 < 1000 ms、`checks` 1、待ちの時間切れ 0、503 は 0 件。緩めない。
- ログは WARN の1行で、決めたキーだけを出す。
- ダッシュボードの区画は Observability Setup が足す。
- 上流との差として、指標の名前が `_milliseconds_` になることを書く。

**cicd-pipeline**
- CI は変えない。verify の結合テストに、業務データの IT を加える。
- 新しいパッケージは、パッケージごとの下限の対象にする。
- SpotBugs の除外と境界テストの変更は、B2 の計画で承認を得る。
- 時間は、clean を付けた verify と CI の試行ごとに記録する。
- B2 の統合の前に、E2E の全体を流す。
- k6 の場面と種の台本は B2 が書く（`bizSearchLast` に主キー以外の並べ替えを足す）。流すのは Performance Validation。
- 流す順は、起動 → 種 → 表の大きさ → 暖め → 合否の回（複数回）。
- 統合は squash で行い、push は依頼者が行う。

**traceability**
- U1 の NFR の枝番に行を立て、基盤の置き場に当てる。基盤に当たらないものは、確かめの置き場を書く。

Does this all look correct before I generate the infrastructure design?

- Looks correct
- Request changes

[Answer]: Looks correct
