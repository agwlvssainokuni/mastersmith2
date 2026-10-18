# 基盤の仕様 — U2 u2-businessdata-write

U2 は service の単位で、B4 で作ります。業務データの作成・更新・削除の3本の API を持ちます（契約 C3）。

- `POST /api/tables/rows`
- `PATCH /api/tables/rows`
- `POST /api/tables/rows/delete`

既存のアプリのコンテナと組み込みの H2 をそのまま使います。対象DB へは、読み取りの接続（`targetDataSource`）とは別に、名前つきの書き込みの接続を足します。

配備先は開発者の PC の上のコンテナだけです。クラウドの基盤・IaC・検証環境は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。読み替え方は U1 と同じです。

## 1. Deployment

| Facet | Choice | Rationale |
|---|---|---|
| Compute model | 既存のアプリのコンテナ1つ（CPU 4・メモリ 2g・最大ヒープ 50%）。変えない。新しいコンテナは足さない | 書き込みは既存のアプリの中の機能。最悪の本文を 10 本同時に持っても約 81 MB（`nfr-design/scalability-design.md` 4節） |
| Instances | 単一のインスタンス | 内部DB が組み込みの H2 のため（`nfr-design/scalability-design.md` 6節） |
| Networking | 既存のまま（127.0.0.1:8080、同じオリジン）。書き込みは、読み取りと同じ接続先へ、別の資格情報で出ていく | 接続先は読み取りの設定をそのまま使う（BR9.1）。道は3つに固定で、値は本文で運ぶ |
| Storage（内部DB） | V13 を足す（監査の3列 `target_schema`・`target_table`・`work_role_id`。空を許す列を足すだけ）。前進だけで、後方互換を保つ | `functional-design/entities.md`。前の版のアプリは足した列を読まない |
| Storage（対象DB） | 利用者のもの。書き込みのアカウントには、DSL の対象のスキーマの行の操作だけを与える | `project.md` の Mandated |
| Environments | 配備は `compose.yaml`、負荷の試験は使い捨ての環境（`docker/perf/compose.yaml`）、CI はテストだけ | `team.md` の Deployment |
| IaC approach | 作らない | 配備先が決まっていない（`project.md`） |
| Resource sizing（アプリ） | 既存のまま | 1要求で使う接続は、内部DB 1・対象DB の書き込み 1 の最大（`nfr-design/scalability-design.md` 1節） |
| Resource sizing（perf の対象DB） | MySQL は上限を **2304m**、プール 768M にする（F1: A）。MariaDB 512M/1g と PG 256MB/768m は U1 の値のまま | 捨ての試し（5節）。U1 の 2.1 の口（環境変数）を共用する |
| Configuration | `application.yaml` に書き込みの項目を4つ、環境変数で受ける形で足す（2.1）。`.env.example` にコメントの行、`.env.targetdb.example` に項目を1つ足す | Q1: A。U1 の 1.1 と同じ形 |
| Secrets | 書き込みのパスワードは、アプリが `.env` から、見本の DB が `.env.targetdb` から受け取る | 管理者のパスワードをアプリに渡さない分け方は、今のまま |
| Schema migration | 内部DB の V13 だけ。対象DB には表を作らない | — |
| Rollback | 直前の版のイメージで起動し直す（イメージだけ）。V13・書き込みのアカウント・`.env` の行は残る。前の版はどれも使わない | 前進だけの移行（`team.md` の Deployment）。Q4: A |

### 1.1 書き込みの設定の口（Q1: A）

既存の `target-db` の並びに足します（`backend/src/main/resources/application.yaml` 75〜102行）。形の例（名前は Q1: A で確定、並びは B4 で決める）:

```yaml
    query-timeout:
      # 書き込みの文1回の待ちの上限（事前の問い合わせ・書き込みの文・違反の後の確かめ）
      write: ${MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_WRITE:5s}
    write:
      # 書き込みのアカウント（秘密）。両方があるときだけ書き込みを使う
      username: ${MASTERSMITH_TARGET_DB_WRITE_USERNAME:}
      password: ${MASTERSMITH_TARGET_DB_WRITE_PASSWORD:}
      pool:
        maximum-size: ${MASTERSMITH_TARGET_DB_WRITE_POOL_MAXIMUM_SIZE:5}
```

- 使っていない接続を閉じるまでの時間は、読み取りの `pool.idle-timeout` を共用します（Q1: A）。
- `TargetDbProperties` の record に `write` を足します。あわせて、既定値を補う処理（`QueryTimeout` の compact constructor と `new QueryTimeout(null, null)`）にも書き込みの分を足します。
- 片方だけがあるときは、項目の名前だけを WARN で1回出し、書き込みは 409 `BUSINESS_WRITE_NOT_CONFIGURED` にします。起動と読み取りは続けます（NFR4.11）。

## 2. Infrastructure Services

| Service | Role | Configuration | Notes |
|---|---|---|---|
| 対象DB の書き込みのプール（`mastersmith-target-db-write`、HikariCP） | database（接続） | 2.1 の表 | 名前つきの `targetWriteDataSource` と `targetWriteTransactionManager`。どちらも既定の候補にしない |
| 対象DB の読み取りのプール（`mastersmith-target-db`） | database（接続） | 変えない（U1） | 書き込みの API は使わない |
| 内部DB（組み込みの H2） | database | プール 30 のまま。V13 を足す | 権限の写しの読み取りと監査の書き込みで、それぞれ1本を短く使う。対象DB の接続を借りている間は 0 本 |
| 見本の対象DB（`compose.yaml` の `targetdb-postgres`・`targetdb-mysql`・`targetdb-mariadb`） | database（手元の確かめ） | 書き込みのアカウントを足す（3節） | 配備には PG だけをつなぐ。配備した PG への適用は 4節 |
| perf の対象DB（`docker/perf/compose.yaml`） | 負荷の試験の database | 2.2 の表 | 見本と同じ初期化の台本（`../targetdb/<種類>`）を使う |
| cache・queue・search・CDN・DNS・load-balancer | — | 使わない | — |

### 2.1 書き込みのプールの設定

| 項目 | 値 | 根拠 |
|---|---|---|
| 読み取り専用 | しない | NFR4.3 |
| 分離のレベル | READ COMMITTED（管理者に付ける） | 試しの W2。REPEATABLE READ では、重なったときに違反のカラムが分からない |
| 上限 | 5（`MASTERSMITH_TARGET_DB_WRITE_POOL_MAXIMUM_SIZE`）。10 に上げる案は採らない | F1: A（NFR 設計）。対象DB のメモリを守る役目も持つ |
| minIdle・起動時の接続 | 0・接続しない（`initializationFailTimeout -1`） | 読み取りと同じ |
| 借りる待ち・確かめの待ち | 既存の接続の待ち（既定 3s）を共用 | 読み取りと同じ |
| ドライバーの接続の待ち | 接続の待ち + 2 秒 | 既存の `DRIVER_TIMEOUT_MARGIN` |
| ドライバーの通信の待ち | **書き込みの上限 + 5 秒**（既定 10 秒）。計算で求める | U1 の基盤の設計のレビューの R-01 を先に避ける。書き込みのプールは専用のため、計算に入るのは書き込みの上限だけ |
| 文の上限 | `setQueryTimeout`（既定 5 秒） | NFR4.4 |
| MySQL・MariaDB の初期化 | `connectionInitSql` で `time_zone` と `sql_mode`（定数） | Q3: A（NFR 設計）・試しの W6 |
| そのほか | MySQL は `useAffectedRows=false`。LOAD DATA LOCAL を許さない（MySQL は `allowLoadLocalInfile=false`・`allowUrlInLocalInfile=false`、MariaDB は `allowLocalInfile=false`） | 試しの W4。既存の読み取りと同じ守り |
| 失敗した接続 | 管理者が返した後に捨てる | 試しの W12 |

### 2.2 perf の対象DB の値（F1: A）

| DB | 設定 | 業務データの試験の値 | 根拠 |
|---|---|---|---|
| MySQL | `--innodb-buffer-pool-size` | 768M（U1 のまま） | 試しの表 532MB × 1.25 |
| MySQL | `mem_limit` | **2304m**（U1 の 1792m から上げる） | 5節。プールを埋めた状態の anon の最大 1,607 MiB が、上限の約 70% になる |
| MariaDB | `--innodb-buffer-pool-size`・`mem_limit` | 512M・1g（U1 のまま） | 5節。埋めた状態でも 83%（上限に当たらない） |
| PG | `-c shared_buffers`・`mem_limit` | 256MB・768m（U1 のまま） | U1 の Q2: A（U2 の試しの範囲の外） |

- 既定の値は今の値のままです（U1 の Q1: A）。DSL の時間の測りは変えません。
- 業務データの試験の手順では、上の値を export してから起動します。

### 2.3 colima の VM に収まるか

| 動かす組み合わせ | 上限の合計 | 判定（docker から見える量 約 5,910 MiB） |
|---|---|---|
| perf のアプリ（2048）＋ perf の MySQL（2304）＋ 見本の PG（512）・Mailpit（256）。配備したアプリは止める | 5,120 MiB | 収まる（余裕 約 790 MiB） |
| 上と同じで、配備したアプリも動かしたまま | 7,168 MiB | 収まらない |
| perf のアプリ ＋ perf の MySQL ＋ lgtm（1536）＋ 見本の PG・Mailpit | 6,656 MiB | 収まらない |
| perf のアプリ ＋ perf の MariaDB（1024）＋ 見本の PG・Mailpit | 3,840 MiB | 収まる |

- 試験の間は配備したアプリを止めます。MySQL・MariaDB の回は lgtm と同時に動かしません（U1 の Q3: A）。
- 線を超えるときは、その時点で依頼者に諮ります。VM は変えません。

### 2.4 対象DB のメモリの判断（F2: A）

| 項目 | 扱い |
|---|---|
| 合否 | OOMKilled が false、かつ `memory.events` の `oom_kill` が 0 |
| 記録だけ | `memory.peak`・`memory.stat` の `anon` の最大・`memory.events` の `max`（上限に当たった回数）・バッファープールの使用（ページの数） |
| アプリの `memory.peak` | 2g の 90% 未満の線は、今のまま変えない |

対象DB の `memory.peak` は、捨てられるページキャッシュを含みます。そのため、問題が無くても上限に張りつきます（5節）。

## 3. 見本の対象DB の書き込みのアカウント

### 3.1 初期化の台本（Q2: A）

| 項目 | 内容 |
|---|---|
| 置き場 | `docker/targetdb/postgres/03-writer-account.sh`・`docker/targetdb/mysql/03-writer-account.sh`・`docker/targetdb/mariadb/03-writer-account.sh` |
| アカウント | `mastersmith_writer` |
| パスワード | `.env.targetdb` の `MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD`。空なら、読み取りの台本と同じく exit 1 で止める（黙って飛ばさない） |
| 渡し方 | 値は psql の変数・`MYSQL_PWD` で渡し、コマンド行と SQL の連結に入れない（`02-reader-account.sh` と同じ形） |
| 冪等 | 何度流しても同じ結果になる形にする（PG は存在を確かめてから作る、MySQL・MariaDB は `CREATE USER IF NOT EXISTS`）。付与と取り上げは毎回当て直す。同じ台本を、既存のボリュームへの手順（4節）にも使う |
| PG の一時の表 | 同じ台本で `REVOKE TEMPORARY ON DATABASE business FROM PUBLIC`（F2: A。NFR 設計） |
| 動く時 | 初めての起動（ボリュームが空のとき）。perf の環境も同じ台本を使う |

### 3.2 付与の範囲（Q3: A・NFR4.16）

| DB | 付与 | 与えないもの |
|---|---|---|
| PG | `USAGE ON SCHEMA`、`SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA`、`USAGE ON ALL SEQUENCES IN SCHEMA`。`ALTER DEFAULT PRIVILEGES` で、後から作る表と sequence にも付ける | DDL・`TEMPORARY`（PUBLIC から取り上げる）・スキーマの `CREATE` |
| MySQL・MariaDB | `SELECT, INSERT, UPDATE, DELETE ON business.*` | DDL・`CREATE TEMPORARY TABLES`・`LOCK TABLES`・`SHOW VIEW` |

- **残る広さ**: DSL に無い表と後から作った表も、DB の上では書けます。アプリはサーバー側で、適用中の DSL の表だけに絞ります。これを README とこの文書に書きます。
- **表ごとに絞る書き方**: 試しの W5 で、MySQL・MariaDB でも表ごとの GRANT が効くと分かりました。利用者の DB で絞る書き方も README に書きます。その場合、参照先の表にも SELECT が要ります（外部キーの確かめのため）。
- PG の sequence の USAGE は、`serial` の表に要ります（試しの W5）。`identity` は表の INSERT だけで足ります。
- PG 14 以前の利用者の DB では、`public` スキーマの `CREATE` が PUBLIC に付いています。README に取り上げ方を書きます。

## 4. 配備した見本の PG への適用（Q4: A）

| 手順 | 中身 | 確かめ |
|---|---|---|
| 1 | `.env.targetdb` に `MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD` の行を足す（乱数。表示しない） | 項目の名前の件数だけを見る |
| 2 | 見本の PG のコンテナだけを作り直す（ボリュームは残す。`--force-recreate targetdb-postgres`） | コンテナが新しい環境変数を持つ |
| 3 | `docker compose exec targetdb-postgres bash /docker-entrypoint-initdb.d/03-writer-account.sh` を流す | 値を表示しない |
| 4 | ロールの有無・付与・`TEMPORARY` が無いことを件数で確かめる | 値と名前以外を出さない |
| 5 | `.env` をリポジトリの外（ホームの下）へ複写してから、`MASTERSMITH_TARGET_DB_WRITE_USERNAME`・`_PASSWORD` の2行を足し、アプリを作り直す | `project.md` の学び（`.env` の戻し） |

- 戻すときは、イメージだけを戻します。書き込みのアカウントと `.env` の行は残っても、前の版は使いません。消すときの手順（`DROP ROLE` と付与の取り消し）を README に置きます。
- 実地は Deployment Execution が行います（7節）。

## 5. 捨ての試しの結果（Q7: A、約 18 分）

| 項目 | 内容 |
|---|---|
| 日時 | 2026-10-19 01:17〜01:35（`caffeinate -dims` で包み、`pmset -g log` で Sleep・DarkWake 0 件） |
| 置き場 | リポジトリの外の作業用の一時の場所（`docker run` の使い捨てのコンテナ。結果を見てから `docker rm -f -v`） |
| イメージ | perf と同じダイジェスト（mysql 26.7.0・mariadb 13.0.2） |
| 本文 | 100 カラム × 絵文字 10,000 字（約 4,000,000 バイト）。Q3: A の書き込みのプールの形 |
| 実際のプール | MySQL 805,306,368（768 MiB）、MariaDB 536,870,912（512 MiB） |

| 場合 | MySQL 768M/1792m の memory.peak | MySQL の anon | MariaDB 512M/1g の memory.peak | MariaDB の anon |
|---|---|---|---|---|
| 5本 × 6回 | 1,019 MiB（57%） | 783 MiB | 404 MiB（40%） | 387 MiB |
| 参考: 10本 × 2回 | 1,373 MiB（77%） | 1,057 MiB | 589 MiB（58%） | 573 MiB |
| 表でプールを 95〜98% 埋めた後に 5本 × 6回 | 1,792 MiB（上限） | 1,581 MiB | 844 MiB（82%） | 835 MiB |
| 埋めた後に 10本 × 2回（参考） | 1,792 MiB（上限） | 1,607 MiB（90%） | 848 MiB（83%） | 839 MiB |

- どちらも OOMKilled は false、`oom_kill` は 0 でした。
- MySQL は上限に 4,326 回当たり、捨てられるページキャッシュを捨てて凌ぎました。MariaDB は 0 回でした。
- MySQL のプール以外の anon は約 840 MiB でした（起動の直後だけで 478 MiB）。U1 の見立て（約 614 MiB で増えない）は崩れました。
- INSERT 1本の時間は、5本の同時で MySQL 123〜419 ms、MariaDB 86〜253 ms でした。

## 6. 上流との差（承認済みの文書は書き換えない）

| 上流 | 上流の書き方 | この設計 | 理由 |
|---|---|---|---|
| `nfr-design/logical-components.md` L11 | 書き込みのプールの socketTimeout 10 秒 | 「書き込みの上限 + 5 秒」で計算する。既定の値は 10 秒で同じ | U1 の基盤の設計のレビューの R-01 を先に避ける。上限を環境変数で変えても、通信の待ちが先に切れない |
| `nfr-design/logical-components.md` 5節 | `.env.example` の書き込みの項目は Infrastructure Design で決める | `.env` の項目が4つ増える（Q1: A）。`.env.targetdb` の項目が1つ増える | 書き込みの資格情報は `.env` だけから受け取る（Mandated） |
| `nfr-requirements/security-requirements.md` NFR4.16 | MySQL・MariaDB の付与を絞れるかを確かめる | 絞れる（試しの W5）。見本はスキーマ全体に付与し、残る広さと絞る書き方を README に書く（Q3: A） | DSL・perf・E2E の表が変わるたびに付与を足す作りは、漏れると 503 になる |
| `README.md` 566行 | 「アプリは接続を読み取り専用にし、書き込み・DDL を発行するコードを持ちません」 | 書き込みの接続は別のアカウントで、行の操作だけを発行すると直す | 事実と合わなくなる |
| `README.md` 588行 | 「見本の DB が持つのは見本のスキーマと読み取りのアカウントだけ」 | 書き込みのアカウントと、アプリから書いた行も持つと直す。作り直すと書いた行も失われる | 同上 |
| `.env.example` 43行 | 「アカウントは読み取りの権限だけにすることを勧める」 | 読み取りと書き込みのアカウントの勧めを分けて書く | 同上 |
| U1 の `infrastructure-design/infrastructure-specification.md` 2.1・2.3（承認待ち） | perf の MySQL の上限 1792m。根拠は「プール以外 約 614 MiB、プールを大きくしても増えない」 | 2304m（2.2）。プール以外は約 840 MiB（5節） | 試しで崩れた（F1: A）。**U1 の承認の場で、値と根拠をそろえる** |
| U1 の `infrastructure-design/infrastructure-specification.md` 2.2 | `memory.peak` と OOMKilled を記録する（判断の線が無い） | 対象DB は「OOMKilled false、かつ `oom_kill` 0」で判断する（2.4） | F2: A。対象DB の `memory.peak` は問題が無くても上限に張りつく |
| `functional-design/rules.md` BR13.4 | 見本の書き込みのアカウントを足す | 置き場を `03-writer-account.sh` にし、冪等にし、PG の `TEMPORARY` の取り上げを足す | Q2: A・F2: A（NFR 設計） |

README に足すこと:
- 413 と、`\u` のエスケープで送る呼び出し側の注意（NFR 設計のレビューの R-09）
- 大きな本文を同時に書き込むと、対象DB のメモリが尽きうること（F1: A。NFR 設計）
- PG の `TEMPORARY` の取り上げと、利用者の DB での手順

## 7. 後の段に回すこと

- **コード生成の計画（B4）**
  - 名前（1.1）と、`application.yaml`・`.env.example`・`.env.targetdb.example`・README の直し（6節）
  - `03-writer-account.sh` の3つの台本。`perf/dsl-timing.sh` と README の一時の環境ファイルに、書き込みのパスワードの生成を足す
  - perf の MySQL の上限の変数の既定を変えずに、業務データの試験の値を 2304m にする（U1 の B2 の口を使う）
  - k6 の5場面と、その setup の前提（`cicd-pipeline.md` 5節）
  - SpotBugs の `SQL_` の除外を広げることの承認（BR13.3）
  - 名前つきの書き込みの管理者が、内部DB の管理者を退けないことの結合テストを、書き込みの部品より前の手順に置く（NFR 設計のレビューの R-06）
- **Deployment Pipeline・Deployment Execution**
  - 4節の手順の実地（初めての確かめ）。配備の前の内部DB のバックアップ（V13 があるため）
  - スモークテストの書き込みが、見本の PG の行と監査に残ることを、依頼者に先に伝える
- **Performance Validation**
  - 2.2 の値で、3種類を1つずつ流す。2.4 の決まりで判断し、記録する
- **Observability Setup**
  - 書き込みのプールの系列が、初めて借りるまで出ないこと。先に1回書いてから式を流す（`monitoring-design.md`）
  - 既存の警報が書き込みを拾うかの実測

## 8. U1・U3 とそろえる点

- **U1**
  - perf の値・変数の名前・README の業務データの節・`bizPrepare` を共用します。MySQL の上限だけを 2304m にそろえます（U1 の承認の場）。
  - 時間の上限の口は、U1 の `list`・`detail` と同じ形（`application.yaml` と `.env.example` のコメントの行）です。
  - 通信の待ちの計算の式（上限の最大 + 5 秒）を、読み取りと書き込みでそろえます。
  - 中継のテストの DB の種類と `BusinessDataConnectionUsageIT` は、1つにまとめます。
- **U3**
  - E2E 180 の書き込みのアカウントは、Q5: A の形にします（`cicd-pipeline.md` 3節）。この答えを U3 の基盤の設計に渡します。
  - 書き込みのアカウントに DDL を与えない決まりは、E2E のためにも緩めません。
  - 503 の出し分け（`BUSINESS_TARGET_DB_TIMEOUT` は「保存されたか分からない」、`UNAVAILABLE` は「保存されていない」）と、413 の文を合わせます。

## Sources

- 答え: `aidlc/spaces/default/intents/261016-business-data-views/construction/u2-businessdata-write/infrastructure-design/infrastructure-design-questions.md`（Q1〜Q7: A、F1: A、F2: A、まとめの確認は Looks correct）
- 上流（どれも `aidlc/spaces/default/intents/261016-business-data-views/` の下）
  - `construction/u2-businessdata-write/nfr-design/`: `logical-components.md`（L11・3節・5節）、`scalability-design.md`（1節・2節・4節・8節）、`security-design.md`（3節・6節・11節）、`reliability-design.md`（1節・5節・7節）、`performance-design.md`（4節）、`nfr-design-followup-questions.md`（F1・F2: A）
  - `construction/u2-businessdata-write/nfr-requirements/security-requirements.md`（NFR4.3・NFR4.11・NFR4.16）
  - `construction/u2-businessdata-write/functional-design/rules.md`（BR9.1・BR13.4）、`entities.md`
  - `construction/u1-businessdata-read/infrastructure-design/infrastructure-specification.md`（1.1・2.1〜2.3）
  - `.aidlc-reviews/infrastructure-design/units/u1-businessdata-read/4bcccff4724084fb/1.json`（R-01・R-02・R-04・R-07）
  - `.aidlc-reviews/nfr-design/units/u2-businessdata-write/df13e747868fc8fa/1.json`（R-06・R-08・R-09）
  - `.aidlc-reviews/functional-design/units/u2-businessdata-write/7dacbaa3e842d0af/1.json`（R-06）
- 既存のもの（正とする。読むだけ）
  - `backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDataSourceConfig.java`（49〜52行・97〜116行・158〜187行）
  - `backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDbProperties.java`
  - `backend/src/main/resources/application.yaml`（75〜102行・197〜201行）
  - `.env.example`（41〜73行）、`.env.targetdb.example`
  - `compose.yaml`（134〜205行）、`docker/perf/compose.yaml`（65〜128行）
  - `docker/targetdb/postgres/02-reader-account.sh`・`docker/targetdb/mysql/02-reader-account.sh`・`docker/targetdb/mariadb/02-reader-account.sh`
  - `perf/dsl-timing.sh`（148行・155行）、`README.md`（264〜268行・557〜566行・584〜612行）
- この PC の実行環境: `colima list`・`docker ps`・`docker stats --no-stream`・`docker volume inspect`（2026-10-19、読み取りだけ）
- 捨ての試し: リポジトリの外の試しのコードと結果（2026-10-19 01:17〜01:35）、U2 の NFR 設計の試し（W2・W4・W5・W6・W10・W12）

## Assumptions & Open Questions

- 前提: 2304m の判断は、プールを表で 95〜98% 埋めた MySQL での実測にもとづく。`bizPrepare` の種と読み取りの場面が重なると、さらに増えうる。Performance Validation で 2.4 の決まりで確かめる。
- 前提: 見本の PG はローカルのソケットで管理者に入れる（README の手順 4 と同じ）。4節の手順 3 はこれに頼る。
- 前提: Spring Boot の Flyway の既定のまま、前の版のアプリは V13 を無視して起動する。戻しの確かめは Deployment の段で行う。
- 未確認: MySQL のプール以外の量が約 840 MiB になる内訳（performance_schema の数えは 1,278 MiB で、anon より小さい）。割り当ての返し方の差と見立てた。
- 未確認: PG の 768m（U1 の値）は、U2 の書き込みでは測っていない。PV で 2.4 の決まりで確かめる。
- 諮る点は無い。2.3 の線を超えるときだけ、その時点で諮る。
