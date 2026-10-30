# 基盤の仕様 — U1 u1-reference-read

U1（参照先の読み取り）が要る基盤を、配備（開発者の PC 上のコンテナ）・設定・負荷の試験の使い捨ての環境の3つで決める。配備先が決まるまでクラウドの基盤（IaC・検証環境・通知の先）は作らない（`team.md` の Deployment、`project.md` の学び）。U1 は新しいコンテナ・ボリューム・接続・アカウント・依存を足さず、既存の基盤に設定を2つ足すだけである。

## 出典

- この段の答え `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/infrastructure-design/infrastructure-design-questions.md`（Q1: A・Q2: A）
- この単位の NFR 設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-design/`（`performance-design.md` 3節・6節、`scalability-design.md` 2節〜5節、`reliability-design.md` 1節・3節、`security-design.md`、`observability-design.md`、`logical-components.md` 1節〜3節）
- この単位の NFR 要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-requirements/`（NFR2.3・NFR2.4・NFR3.3〜NFR3.5・NFR9.1）
- この単位の機能設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/functional-spec.md`（道 `POST /api/tables/definition`・`POST /api/tables/reference/rows`）
- ドメイン設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/domain-design/components.md`（`ReferenceLookup`・RowSqlGateway）と契約 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-summary.md`（C2・C5）
- 読み取りだけで確かめた既存の設定: `backend/src/main/resources/application.yaml`（`mastersmith.target-db.*`・`management.metrics.distribution.slo`）、`.env.example`（対象DB の項目）、`compose.yaml`（app のサービスは `.env` を `env_file` で読む）、`docker/perf/compose.yaml`（app は一時の環境ファイル `MASTERSMITH_PERF_APP_TARGETDB_ENV_FILE` で対象DB の項目を受ける）、`perf/README.md`（業務データの試験の対象DB の値の表）、`README.md`（環境変数の表）、`backend/src/main/java/cherry/mastersmith/businessdata/web/BusinessDataReadController.java`

## 1. 配備

| Facet | Choice | Rationale |
|---|---|---|
| 計算の形 | 既存の `app` のコンテナ（`mastersmith:local`、実行可能 WAR を写すイメージ）のまま。CPU 4・メモリ 2g（`compose.yaml` の既定）を変えない | U1 はアプリの中の読み取りの経路を足すだけ。ヒープの見積もり（1要求で残る量 約 4〜16 MB、10 VU で約 40 MB。`scalability-design.md` 5節）は今の上限の内で、`MASTERSMITH_JAVA_OPTIONS`・`MaxRAMPercentage` も変えない |
| 網 | 変えない。`127.0.0.1:8080` だけに結び付け、対象DB へは既存の読み取りの接続で出る | 新しい道は既存の `/api/tables` の下で、同じオリジン。CORS・公開の範囲は変わらない |
| 保存 | 変えない。内部DB（`mastersmith-data` のボリューム）に移行を足さない。参照先の選択肢・ピッカーの結果は保存もキャッシュもしない | NFR7.1・`scalability-design.md` 1節 |
| 環境 | 開発者の PC 上の配備（`compose.yaml`）と、負荷の試験の使い捨ての環境（`docker/perf/compose.yaml`）の2つ。検証・本番の環境は作らない | `team.md` の Deployment |
| IaC | 作らない。基盤の定義は `compose.yaml`・`docker/perf/compose.yaml`・`application.yaml`・`.env.example` のファイルで持つ | `project.md` の学び（配備先が決まったときに置き換える） |
| 資源の大きさ | アプリ・対象DB のコンテナの上限は変えない（3節・4節） | 読む行の数は 201・21・1 に絞られ、長い値は SQL で除く（`performance-design.md` 1節） |
| 新しい依存 | 足さない（Gradle の依存・npm のパッケージ・コンテナのイメージ） | NFR9.1 |

## 2. 設定（Q1: A）

### 2.1 足す2つの設定

| 環境変数 | 設定の鍵 | 既定 | 範囲 | 置き場 |
|---|---|---|---|---|
| `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_REFERENCE` | `mastersmith.target-db.query-timeout.reference` | `5s` | 1 秒以上。参照先の問い合わせ1回（選択肢・ピッカーの検索・ラベルの1件読み・`check`・`countState`）の上限 | `application.yaml` の `query-timeout:` の下（`detail` と `write` の間）に既定つきで置く |
| `MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL` | `mastersmith.target-db.reference-read-total` | `10s` | 1 秒以上。定義の取得で参照先を読む全体の上限 | `application.yaml` の `target-db:` の下（`query-timeout:` の後）に既定つきで置く。`QueryTimeout` には入れない（`reliability-design.md` 1.2） |

### 2.2 書く場所ごとの扱い

| 場所 | 扱い | 理由 |
|---|---|---|
| `application.yaml` | 2つの鍵を `${環境変数:既定}` の形で足し、コメントに範囲（1 秒以上）と、全体の上限の最悪の値（切り上げのため選択肢の読み取りだけで約 11 秒、これに権限の写しの読み取りと借りる待ち 最大 3 秒が乗る。`reliability-design.md` 1.3）を書く。`reference` を 20 秒（`generate`）より長くしたときは、読み取りのドライバーの通信の待ちが「最も長い上限 + 5 秒」で伸びることを、既存の `detail` のコメントの「4つの上限」を「5つの上限」に直して書く | 既定は1か所（Q1: A）。`longest()` に `reference` を入れるため（`reliability-design.md` 1.1） |
| `.env.example` | 既存の時間の設定の行（`MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_DETAIL` の次）に、コメントアウトした2行と説明を足す。説明の「読み取りの全体の上限は無い」を「参照先を読む定義の取得だけは全体の上限がある（既定 10s）」に直す | Q1: A。値を書かない人は何も書かなくてよい |
| `compose.yaml` の `app` | 足さない | `app` は `.env` を `env_file` で読む（Q1: A、前の Intent と同じ置き方） |
| `README.md` の環境変数の表 | `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_DETAIL` の次に2行足す（既定・1 秒以上・何の上限か）。「業務データの読み取りの API」の節の道の表に `POST /api/tables/reference/rows` を足し、`POST /api/tables/definition` の「対象DB には接続しません」に「`purpose` が `CREATE`・`UPDATE` で参照の項目があるときだけ読み取りの接続を1本借りる」を足す | `logical-components.md` の「後の段に回すこと」（`.env.example`・README の書き方）。既存の README の書き方に合わせる |
| `docker/perf/compose.yaml` | 変えない | 2つの設定は既定のまま測る。使い捨てのアプリは一時の環境ファイルで受ける（4節） |

### 2.3 誤った値の扱い

| 値 | 振る舞い | 運用の手当て |
|---|---|---|
| 設定しない | 既定（5s・10s）で動く | なし |
| 1 秒未満（`0`・負の値・`500ms`） | 起動は続く。`TargetDbSettings` の点検で `Invalid` になり対象DB を使わない（業務データの読み書き・DSL の生成と照合が使えない）。WARN には項目名（`QUERY_TIMEOUT_REFERENCE`・`REFERENCE_READ_TOTAL`）だけを出し、値は出さない | `.env` の該当の行を消す（または 1 秒以上に直す）。`.env` を開いて値を表示せず、項目名の有無で確かめる。アプリのコンテナを作り直す（`--force-recreate --no-deps`）。イメージは替えない |
| `Duration` として読めない（`abc` など） | 既存の時間の設定と同じく、結び付けで起動が止まる | 同じく `.env` の行を直してから起動し直す |

1 秒未満の値は、任意の調整用の設定の打ち間違いでも業務データの読み書き全体に及ぶ。そのため配備では、点検の WARN の項目名の確かめに加えて、既存の業務データの一覧が 200 で返ることを必ず確かめる（`cicd-pipeline.md` 3節の順 5・順 6。R-01）。警報は足さない。

どちらの設定も秘密ではない。資格情報・接続先は今の `MASTERSMITH_TARGET_DB_*` のまま増えない（`project.md` の Forbidden・Mandated）。

## 3. 基盤のサービス

| Service | Role | Configuration | Notes |
|---|---|---|---|
| 対象DB の読み取りのプール（`mastersmith-target-db`） | database（接続のプール） | 配備: 上限 10（既定。`MASTERSMITH_TARGET_DB_POOL_MAXIMUM_SIZE`）・借りる待ち 3 秒（`connectTimeout`）。変えない | 参照の読み取りは一覧・詳細・DSL の生成と照合と共有する。1要求1本、内部DB の接続と同時に持たない（`scalability-design.md` 2節・3節）。枯渇は 503 `BUSINESS_TARGET_DB_UNAVAILABLE` |
| 対象DB の読み取りのアカウント | database（資格情報） | 既存の読み取りのアカウント（`SELECT` だけ）。新しい権限を足さない | 参照先は同じ DSL の対象のスキーマの表のため、今の `SELECT` の範囲で読める |
| 対象DB の書き込みのプール | database | 使わない | U1 は書き込みの接続を借りない（`scalability-design.md` 2節） |
| 内部DB（組み込みの H2） | database | 変えない。移行を足さない | 権限の写しを読むだけで、対象DB を待つ間は接続を持たない（NFR3.2） |
| 手元の対象DB（`targetdb-postgres`・`targetdb-mysql`・`targetdb-mariadb` の profile） | database（手元の確かめ） | 変えない | 見本のスキーマに参照の項目を足すかは Deployment Pipeline のスモークテストで決める（U3 の画面と合わせる） |
| キャッシュ・キュー・検索・CDN・DNS・ロードバランサー | — | 使わない | 状態を持たない（`scalability-design.md` 1節） |

## 4. 負荷の試験の使い捨ての環境（Build and Test・Performance Validation）

負荷の試験は配備した環境とは別の使い捨ての環境で行う（`project.md` の学び。手順は `perf/README.md`）。U1 の場面（`refDefinition200`・`refDefinition201`・`refPickerEquals`・`refLabel`・`refPickerContains`、10 VU・3 分、`iteration_duration` の p95）を流すための基盤の決まり。

### 4.1 使い捨てのアプリ

| 項目 | 値 | 渡し方 |
|---|---|---|
| 読み取りのプールの上限 | **5**（NFR2.3） | 一時の環境ファイル `app-targetdb.env`（`MASTERSMITH_PERF_APP_TARGETDB_ENV_FILE`）に `MASTERSMITH_TARGET_DB_POOL_MAXIMUM_SIZE=5` を足す。`docker/perf/compose.yaml` は変えない。`perf/README.md` の今の手順は書いていない（既定 10 で動く）ため、U1 の場面の手順に明示して足す（5節の上流との差） |
| 流す前の確かめ | 上限が 5 であること。**5 でなければ U1 の場面を流さない（中止し、一時の環境ファイルを直して使い捨てのアプリを起動し直す）**（R-03。書き忘れると既定の 10 で流れ、NFR2.3 のプールが尽きる形の確かめが成り立たないまま合格しうるため） | 使い捨てのアプリにだけ `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics` を渡し、`/actuator/metrics/hikaricp.connections.max`（`pool=mastersmith-target-db`）で読む。配備したアプリの公開の範囲は変えない。Code Generation で `perf/README.md` に 5 の行を足すとき、この中止の条件を同じ手順に明記する |
| 記録 | 待ちの時間切れの累計（`hikaricp.connections.timeout`）の前後の差、借りるまでの待ちの最大 | `scalability-design.md` 3節 |
| 参照の2つの設定 | 既定（5s・10s）のまま | 一時の環境ファイルに書かない |
| コンテナの上限 | CPU 4・メモリ 2g（配備と同じ） | `perf/README.md` のとおり export |

### 4.2 種（NFR2.4）

| 足すもの | 中身 | 置き場 |
|---|---|---|
| REFERENCE の参照先（コードの表）2つ | ちょうど 200 行と 201 行。値は短い文字のコード、ラベルは短い文字。行の番号だけから決まる架空の値（乱数を使わない） | `perf/biz/make-biz-seed.sh`（3種類の DB で同じ形） |
| 参照元の表 | REFERENCE（200 行・201 行の表を指す2つ）と LOOKUP（`biz_item` を指す）のカラムを持つ小さな表 | 同じ種。定義の取得は DSL だけで行を読まないため、行は少なくてよい |
| DSL の options | 上の REFERENCE・LOOKUP を足す。LOOKUP の検索の列は `biz_item` の `c_010`（既存の `bizSearchContains` の目印） | `perf/make-biz-dsl.mjs`（種と DSL を同時に変える） |
| 読み取りの権限 | 新しい表も種の SQL で読み取りのアカウントに `SELECT` を与える | 既存の種と同じ |
| 種を入れた後の確かめ | 表ごとの行の数（200・201・100,000）と、部分一致の目印の当たりの数を数えてから流す。MySQL の種は `--default-character-set=utf8mb4` で流す | `project.md` の学び |

名前と列の形は Code Generation（種と DSL の台本を書く Build and Test の前の Bolt）で決める。

### 4.3 対象DB のコンテナのメモリ

| DB | 業務データの試験の値（`perf/README.md`） | U1 の種で変わるか |
|---|---|---|
| MySQL | プール 1280M・上限 2688m | 変えない。足す表は 200・201 行の短い値と小さな参照元の表で、合計は数百 KB の見込み。表の合計 ÷ プール（今 75.8%）はほぼ変わらない |
| MariaDB | プール 896M・上限 1408m | 変えない（今 79.5%） |
| PostgreSQL | `shared_buffers` 256MB・上限 768m | 変えない（今 79.2%） |

`perf/README.md` の決まり（表の合計がプールの 80% を超えたら 128 MiB ずつ上げる）で、種を入れた後に表の大きさを測り、80% 以内であることを確かめてから流す。U1 の参照の読み取りそのものは、`LIMIT` と `setMaxRows`、長さの条件で読む量を絞るため、対象DB の側の並べ替えの領域（MySQL の `sort_buffer_size` 262,144）を超えない（並びの項を先頭 100 字で作る。`performance-design.md` 2節）。

### 4.4 流す順

| 順 | 何を | 理由 |
|---|---|---|
| 1 | 対象DB の種類ごとに、U1 の読み取りの場面を先に流す | 書き込みの場面の後に読み取りを流すと、表が大きくなって読み取りの結果が悪い側へ寄る（`project.md` の学び、前の Intent の MySQL の `biz_new`） |
| 2 | その後に U2 の保存前の確かめつきの作成の場面（`ref` で始まる名前） | U2 の要件。同じ種と判定の決まりを共有する |
| 3 | 深いページのピッカー（PostgreSQL で 1,000 ページ目）を参考に1回 | 判定の対象の外（`performance-design.md` 3節） |

台本全体を `caffeinate -dims` で包み、流した後に `pmset -g log` でスリープが無かったことを確かめる（`project.md` の学び）。試験の間は配備したアプリを止め、MySQL・MariaDB の回では手元の監視（lgtm）を動かさない（VM の余裕。`perf/README.md`）。

## 5. 共有の基盤

| Shared Resource | Owner Unit | Consumer Units | Access Boundary |
|---|---|---|---|
| 対象DB の読み取りのプール | 既存（前の Intent の `targetdb`） | U1（参照の読み取り）、U2（`check`・`countState` は U2 が借りた接続を受ける）、既存の一覧・詳細・DSL の生成と照合 | `TargetReadAccess.withRead(REFERENCE, …)` だけを通す。`businessdata` は `targetdb.service`・`targetdb.domain` だけに依存する |
| 2つの設定（`reference`・`referenceReadTotal`） | U1 | U2（`ReferenceReadDeadline` で同じ締め切りを使う） | `TargetReadAccess.referenceReadTotal()` で受ける（`targetdb.config` に依存しない） |
| perf の種と DSL（`make-biz-seed.sh`・`make-biz-dsl.mjs`） | U1（REFERENCE・LOOKUP の表を足す） | U2（保存前の確かめつきの作成の場面） | 種と DSL を同時に変える。U2 が足す表・行は U2 の Code Generation で同じ台本に足す |
| 手元の監視のダッシュボードと警報 | 既存 | U1・U2 | `monitoring-design.md` |

## 上流との差

| 上流 | 差 | 扱い |
|---|---|---|
| この段の Q1 の文（`application.yml`） | 実際のファイルは `backend/src/main/resources/application.yaml` | 名前の書き違い。中身（既定はアプリの設定ファイルの1か所）は同じ |
| NFR 要件 NFR2.3（読み取りのプールを「前の Intent の業務データの場面と同じ 5」） | `perf/README.md` は Intent 261025-deps-perf-carryover で既定 10 に上がった後の書き方で、使い捨てのアプリにプールの上限を渡していない（既定 10 で動く）。5 で流すには一時の環境ファイルに明示して足す必要がある。また、直前の Intent（261025・261027）の業務データの場面は 10 で流れているため、「並べて読む」相手は 5 で流した Intent 261016-business-data-views の結果になる | 承認済みの要件のとおり 5 で流す（4.1）。比べる相手の選び方は Build and Test の計画で依頼者に確かめる（後の段に回すこと） |
| NFR 設計 `observability-design.md` 1節（区画の説明に新しい道を足すだけ） | Q2: A でパネルを2つ足す | `monitoring-design.md` 2節 |
| NFR 設計 `reliability-design.md` 1.1（`.env.example` に名前だけを足す） | Q1: A のとおりコメントアウトした行と説明を足し、既存の説明の「読み取りの全体の上限は無い」を直す | 説明の直しは事実に合わせるため |
| Q1 の答え（`application.yaml` と `.env.example`） | `README.md` の環境変数の表と読み取りの API の節にも書く | `logical-components.md` の「後の段に回すこと」（README の書き方）。既存の設定はすべて README の表にあるため |

## 後の段に回すこと

- **Code Generation**: 2.1・2.2 の `application.yaml`・`.env.example`・`README.md` の追記（設定の型と点検は `logical-components.md` 1節の部品）。4.2 の種と DSL の台本（`make-biz-seed.sh`・`make-biz-dsl.mjs`）と k6 の場面（`perf/k6/scenarios.js`）、`perf/README.md` に U1 の場面の手順（プールの上限 5 の行を一時の環境ファイルに足す・流す前の確かめ・`hikaricp.connections.max` が 5 でなければ場面を流さない中止の条件・流す順）を足す（R-03）。
- **Build and Test**: 種を入れた後の行の数・目印・表の大きさの確かめ（4.2・4.3）、`k6 inspect` での場面の確かめ、比べる相手（Intent 261016 の 5 の結果）を依頼者に確かめる。
- **Performance Validation**: 4.1 のプールの上限 5 の確かめと待ちの時間切れの累計の記録、NFR2.1・NFR2.3 の合否。
- **Deployment Pipeline**: 配備の手順に、新しい設定が既定で動くこと（`.env` に足さない）と、戻しがイメージだけで済むことを書く（`cicd-pipeline.md` 4節）。点検の WARN の確かめと既存の業務データの一覧の 200 の確かめを手順書とスモークテストに必ず入れる（R-01）。見本の対象DB に参照の項目を持つ表を足すかは、U3 の画面のスモークテストと合わせて決める。

## 承認の場の決定

段の承認の場で、依頼者は Request Changes を選び、レビュー（`reviews/review-01.md`）の3つの Minor の指摘をすべて示された直し方で直すと決めた。

| 指摘 | 決定 | 反映した場所 |
|---|---|---|
| R-01（2つの設定の 1 秒未満の値で対象DB 全体が使えなくなり、検知が点検の WARN だけ） | 配備の手順書とスモークテストで、点検の WARN の項目名の確かめに加えて、既存の業務データの一覧が 200 で返ることを必ず確かめる。新しい警報は足さない判断は保つ | 2.3 の表の後の注記、後の段に回すことの Deployment Pipeline、`cicd-pipeline.md` 3節の順 5・順 6 |
| R-02（パネル 46・47 の `uri` の値が未確認） | パネルの説明に定義の取得が `VIEW` を含むことを書いたまま、式は Observability Setup で `uri` の実際の値を確かめるまで仮とする | `monitoring-design.md` 2節・6節・「承認の場の決定」 |
| R-03（perf の読み取りのプール 5 の書き忘れで既定 10 で流れうる） | Code Generation で `perf/README.md` に 5 の行を手順として足すとき、`hikaricp.connections.max` が 5 でなければ場面を流さない（中止する）条件を明記する。比べる相手（5 で流した Intent 261016）の選び方は Build and Test で依頼者に確かめる記述を保つ | 4.1 の「流す前の確かめ」、後の段に回すことの Code Generation（Build and Test の行は変えない） |
