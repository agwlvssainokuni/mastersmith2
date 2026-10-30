# 基盤の仕様 — U2 u2-reference-write-check

U2（作成・更新の保存前の参照先の確かめ）が要る基盤を、配備（開発者の PC 上のコンテナ）・設定・負荷の試験の使い捨ての環境の3つで決める。U2 は新しい設定・接続・アカウント・コンテナ・ボリューム・依存を足さない。U1 の基盤の仕様（`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/infrastructure-design/infrastructure-specification.md`）が足す2つの設定・使い捨ての環境の決まり（読み取りのプールの上限 5、種の追加、U1 の場面を先に流す順）をそのまま使い、この文書には U2 が**足す分だけ**を書く（2つ目の写しは作らない）。配備先が決まるまでクラウドの基盤（IaC・検証環境・通知の先）は作らない（`team.md` の Deployment、`project.md` の学び）。

## Sources

- この段の質問のファイル `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/infrastructure-design/infrastructure-design-questions.md`（質問なし。決まっていること）
- この単位の NFR 設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/nfr-design/`: `performance-design.md`（1節〜5節）、`scalability-design.md`（1節〜3節）、`reliability-design.md`（1節・5節）、`security-design.md`（7節）、`observability-design.md`（1節・3節）、`logical-components.md`（1節〜3節、「後の段に回すこと」の「新しい設定は無い」）、`trial-results.md`（2.3）
- この単位の NFR 要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/nfr-requirements/`（`performance-requirements.md` の NFR2.21〜NFR2.25、`scalability-requirements.md` の NFR2.26〜NFR2.28・NFR3.21〜NFR3.23、`reliability-requirements.md` の NFR3.24〜NFR3.27・NFR7.21・NFR7.22）
- この単位の機能設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/functional-design/functional-spec.md`（2節の応答、3節の判定の順、4節の流れ。道は既存の `POST /api/tables/rows`・`PATCH /api/tables/rows`）
- ドメイン設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/domain-design/components.md`（BusinessData・`ReferenceLookup`・RowSqlGateway・TargetDb）と契約 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-summary.md`（C1・C3・C4・C5）
- U1 の基盤の設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/infrastructure-design/infrastructure-specification.md`（2節・4節・5節）と `monitoring-design.md`
- 読み取りだけで確かめた既存のもの: `backend/src/main/java/cherry/mastersmith/businessdata/web/BusinessDataWriteController.java`（作成 `@PostMapping`・更新 `@PatchMapping` の道はどちらも `BusinessDataPaths.ROWS`、削除は `BusinessDataPaths.DELETE`）、`backend/src/main/resources/application.yaml`（`mastersmith.target-db.pool.maximum-size` 既定 10、`write.pool.maximum-size` 既定 5、`query-timeout.write` 既定 5s）、`.env.example`（`MASTERSMITH_TARGET_DB_WRITE_*`）、`compose.yaml`、`docker/perf/compose.yaml`、`perf/README.md`（「業務データの場面」「業務データの書き込みの場面」）、`perf/biz/make-biz-seed.sh`（権限を与える文はスキーマ単位で、表を作った後に置かれている）

## 1. 配備

| Facet | Choice | Rationale |
|---|---|---|
| 計算の形 | 既存の `app` のコンテナ（`mastersmith:local`）のまま。CPU 4・メモリ 2g を変えない | U2 は既存の書き込みの流れに ⑩ の段を足すだけ。1要求で残る量は参照の数によらず約 4 MB（`performance-design.md` 4節）で、ヒープの設定（`MASTERSMITH_JAVA_OPTIONS`・`MaxRAMPercentage`）を変えない |
| 網 | 変えない。`127.0.0.1:8080` だけに結び付ける | 新しい道を足さない。既存の作成・更新の道（`/api/tables/rows`）の応答に拒否が増えるだけ（C3） |
| 保存 | 変えない。内部DB に移行を足さない。確かめの結果は保存もキャッシュもしない | NFR7.21・`reliability-design.md` 5節 |
| 環境 | 開発者の PC 上の配備（`compose.yaml`）と、負荷の試験の使い捨ての環境（`docker/perf/compose.yaml`）の2つ。検証・本番の環境は作らない | `team.md` の Deployment |
| IaC | 作らない。ファイル（`compose.yaml`・`docker/perf/compose.yaml`・`application.yaml`・`.env.example`）で持つ | `project.md` の学び |
| 資源の大きさ | アプリ・対象DB のコンテナの上限は変えない（3節・4.3） | 試しで、参照 5 個・10 本の同時でも保存の p95 は 14.89〜25.70 ms（`trial-results.md` 2.3） |
| 新しい依存 | 足さない | NFR9.21 |

## 2. 設定（足さない）

| 使う設定 | 持ち主 | U2 での使い方 |
|---|---|---|
| `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_REFERENCE`（既定 5s） | U1 が足す | ⑩ の文1回（更新前の行の `detail`・`countState`・`check`）の上限（NFR3.24） |
| `MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL`（既定 10s） | U1 が足す | ⑩ の全体の上限。U1 の定義の取得と同じ値で、別々には変えられない（NFR3.25、NFR 設計の Q2: A）。`TargetReadAccess.referenceReadTotal()` だけから受ける |
| 読み取りの接続（`MASTERSMITH_TARGET_DB_HOST` などと読み取りのアカウント） | 既存 | ⑩ で1回だけ借りる（`ReadPurpose.REFERENCE`） |
| 書き込みの接続（`MASTERSMITH_TARGET_DB_WRITE_USERNAME`・`_PASSWORD`・`_POOL_MAXIMUM_SIZE`・`QUERY_TIMEOUT_WRITE`） | 既存 | ⑪ で今までどおり。読み取りの接続を返してから借りる |

- `application.yaml`・`.env.example`・`compose.yaml`・`docker/perf/compose.yaml`・README の環境変数の表は、U2 のためには変えない（設定の追記は U1 の分だけ）。
- 誤った値の扱い（1 秒未満で `Invalid`、項目名だけの WARN）は U1 の 2.3 のとおり。U2 の ⑩ も同じ設定の点検に従い、対象DB が使えないときは参照の値を入れた作成・更新が 503 になる。
- 書き込みのアカウントの権限は変えない（`project.md` の Mandated: DSL の対象のスキーマの行の操作だけ、DDL を与えない）。⑩ は読み取りの接続だけで行い、書き込みのアカウントに読み取りの仕事をさせない（ADR-003）。

## 3. 基盤のサービス

| Service | Role | Configuration | Notes |
|---|---|---|---|
| 対象DB の読み取りのプール（`mastersmith-target-db`） | database（接続のプール） | 配備: 上限 10・借りる待ち 3 秒。変えない | ⑩ で1要求0〜1回（読むものが無ければ借りない。BR1.2）。一覧・詳細・U1・DSL の生成と照合と共有。枯渇は 503 `BUSINESS_TARGET_DB_UNAVAILABLE`（保存しない） |
| 対象DB の書き込みのプール（`mastersmith-target-db-write`） | database（接続のプール） | 配備: 上限 5（既定）。変えない | 読み取りを返した後に借りる。2本を同時に持たない（NFR3.21・ADR-003）。⑩ は書き込みの接続を持つ時間を増やさない（`scalability-design.md` 1節） |
| 対象DB の読み取り・書き込みのアカウント | database（資格情報） | 既存。新しい権限を足さない | 参照先は同じ DSL の対象のスキーマの表のため、今の `SELECT` と行の操作の範囲で足りる |
| 内部DB（組み込みの H2） | database | 変えない。移行を足さない | ⑩・⑪ の間は接続を持たない。拒否の監査は対象DB の接続を返した後の別の短いトランザクション（既存の出来事に `FAILURE`・`BUSINESS_REFERENCE_NOT_FOUND`。C4） |
| 手元の対象DB（`targetdb-*` の profile） | database（手元の確かめ） | 変えない | 見本のスキーマに参照の項目を足すかは、U1 と同じく Deployment Pipeline でスモークテストと合わせて決める |
| キャッシュ・キュー・検索・CDN・DNS・ロードバランサー | — | 使わない | 状態を持たない |

## 4. 負荷の試験の使い捨ての環境（U1 の 4節に足す分）

U1 の 4節（使い捨てのアプリ・種・対象DB のメモリ・流す順）を前提にし、U2 の場面（仮の名前 `refCreateChecked`。確定は Build and Test）のために足すものだけを書く。

### 4.1 使い捨てのアプリ

| 項目 | 値 | 渡し方・理由 |
|---|---|---|
| 読み取りのプールの上限 | 5（U1 の 4.1 と同じ回のまま） | U1 が一時の環境ファイル `app-targetdb.env` に足す `MASTERSMITH_TARGET_DB_POOL_MAXIMUM_SIZE=5` をそのまま使う。U2 の場面のためにアプリを起動し直さない |
| 書き込みのプールの上限 | 5（既定。既存の書き込みの場面 `bizCreate`・`bizUpdate`・`bizWritePoolLimit` と同じ） | 一時の環境ファイルに書かない（既定で動く）。10 VU で読み取り 5・書き込み 5 を取り合う形は、試しで3種類とも失敗 0（`scalability-design.md` 3節） |
| 書き込みのアカウント | 既存の `mastersmith_writer`（`1B` で一時の環境ファイルに書く） | 変えない |
| 参照の2つの設定 | 既定（5s・10s）のまま | U1 と同じ |
| 記録 | 読み取り・書き込みの2つのプールの待ちの時間切れの累計（`hikaricp.connections.timeout`、`pool=mastersmith-target-db`・`mastersmith-target-db-write`）の前後の差を並べる | 使い捨てのアプリにだけ公開した metrics で読む（U1 の 4.1 と同じ口）。どちらも 0 であること（`scalability-design.md` 3節） |
| コンテナの上限 | CPU 4・メモリ 2g（配備と同じ） | 変えない |

読み取りのプールの上限を下げた U2 だけの場面は置かない（`scalability-design.md` 3節。接続の数は結合テストで決定的に確かめる）。

### 4.2 種と DSL に足すもの（NFR2.22）

| 足すもの | 中身 | 置き場 |
|---|---|---|
| 参照元の表（1つ） | 自動の採番の主キーと、U1 が足す REFERENCE のちょうど 200 行の参照先を指す NOT NULL の参照のカラム1つを持つ小さな表。行は入れない（作成の場面が入れる）。値はすべて架空 | `perf/biz/make-biz-seed.sh`（3種類の DB で同じ形） |
| DSL の参照の options | 上の参照のカラムに REFERENCE の options（参照先は U1 の 200 行の表）を足す。`biz_new` の `ref_id` には参照の options を足さない（NFR2.23。既存の `bizCreate`・`bizWritePoolLimit` の意味を保つ） | `perf/make-biz-dsl.mjs`（種と DSL を同時に変える） |
| 足す順 | U1 の参照先の表と DSL の変更を先に足し、その後に U2 の参照元の表と options を足す（参照元は U1 の参照先を指すため） | 同じ2つの台本。Build and Test の手順に順を書く |
| 権限 | 新しい権限の文を足さない。参照元の表は、台本の最後の権限を与える文（PostgreSQL は `ON ALL TABLES IN SCHEMA bizperf`・`ON ALL SEQUENCES`、MySQL・MariaDB は `ON bizperf.*`）より**前**で作る。そうすれば書き込みのアカウントに行の操作（と PostgreSQL の sequence の USAGE）が、読み取りのアカウントに `SELECT` が既存の文で付く | PostgreSQL の `ON ALL TABLES` は文を流した時点の表にだけ効くため、置く位置を守る |
| 種を入れた後の確かめ | U1 の確かめ（行の数 200・201・100,000、目印の当たり）に加えて、参照元の表が空であることを数えてから流す。MySQL の種は `--default-character-set=utf8mb4` で流す | `project.md` の学び |

名前と列の形は Code Generation（U2 の Bolt B3）で決める。

### 4.3 対象DB のコンテナのメモリ

| DB | 業務データの試験の値（`perf/README.md`） | U2 の追加で変わるか |
|---|---|---|
| MySQL | プール 1280M・上限 2688m | 変えない見込み。種で足すのは空の小さな表1つ |
| MariaDB | プール 896M・上限 1408m | 同上 |
| PostgreSQL | `shared_buffers` 256MB・上限 768m | 同上 |

種を入れた後に表の合計（データと索引）を測り、プールの 80% 以内であることを確かめてから流す（`perf/README.md` の決まり。U1 の 4.3 と同じ1回の測りで足りる）。`refCreateChecked` は書き込みで参照元の表の行を増やす（10 VU・3 分で、既存の `bizCreate` と同じ程度の件数の短い行）。増える量は流した後にも表の大きさを測って記録する。

### 4.4 流す順（U1 の 4.4 の 2 に当たる）

| 順 | 何を | 理由 |
|---|---|---|
| 1 | U1 の読み取りの場面（U1 の 4.4 の 1） | 書き込みの後の読み取りは悪い側に寄る |
| 2 | `refCreateChecked` | U2 の合否の場面（NFR2.21・NFR2.22） |
| 3 | 既存の書き込みの場面（`bizCreate`・`bizUpdate`・`bizDelete`・`bizWritePoolLimit`、その後に `bizCreateLarge`） | 参照の無い作成・更新が遅くならないことを並べて記録する（NFR2.23）。`bizCreateLarge` の前はアプリのコンテナを作り直して `memory.peak` を消す（既存の手順） |
| 4 | 書き込みの後に読み取りの値が要るときは、対象DB を作り直して種を入れ直す | `perf/README.md` の見直しの案 |

`refCreateChecked` と既存の書き込みの場面の前後（2 と 3）は、どちらも書き込みのため入れ替えてもよい。決めるのは Build and Test の計画。台本全体を `caffeinate -dims` で包み、流した後に `pmset -g log` で眠りが無かったことを確かめる。試験の間は配備したアプリを止める。

## 5. 共有の基盤

| Shared Resource | Owner Unit | Consumer Units | Access Boundary |
|---|---|---|---|
| 対象DB の読み取りのプール | 既存（`targetdb`） | U1、U2（⑩）、既存の一覧・詳細・DSL の生成と照合 | `TargetReadAccess.withRead(REFERENCE, …)` だけを通す。U2 はその中で U1 の `check`・`countState` と既存の `detail` を呼ぶ |
| 対象DB の書き込みのプール | 既存（前の Intent の業務データの書き込み） | U2（⑪。今までどおり） | `TargetWriteAccess.withWrite` だけ。読み取りを返した後に借りる |
| 2つの設定（`reference`・`referenceReadTotal`） | U1 | U2 | U1 の 5節のとおり |
| perf の種と DSL の台本 | U1 | U2（参照元の表と options を後から足す） | 4.2 の順 |
| 手元の監視のダッシュボードと警報 | 既存 | U1・U2 | `monitoring-design.md` |

## 上流との差

| 上流 | 差 | 扱い |
|---|---|---|
| NFR 要件 NFR2.22（書き込みのアカウントに新しい表の行の操作を与える） | 新しい権限の文は足さず、参照元の表を既存のスキーマ単位の権限の文より前に作ることで与える | 権限の範囲（DSL の対象のスキーマの行の操作）は変わらない。PostgreSQL の `ON ALL TABLES` が表の作成の後でしか効かないことを 4.2 に書いた |
| NFR 要件 NFR2.24（流す順は読み取りの場面の後） | U2 の場面と既存の書き込みの場面の前後は Build and Test の計画で決める | 4.4。どちらも書き込みのため、読み取りの結果への影響は変わらない |

## 後の段に回すこと

- **Code Generation（B3）**: 4.2 の参照元の表と DSL の options（U1 の分の後に足す。権限の文より前に作る）、`perf/k6/scenarios.js` の `refCreateChecked`、`perf/README.md` に U2 の場面の手順（流す順・2つのプールの時間切れの記録・参照元の表が空の確かめ）を足す。
- **Build and Test**: 場面の名前の確定、`k6 inspect --include-system-env-vars`、短い試し走り（1 VU・10 秒）、種を入れた後の表の大きさ、流す順の決定。
- **Performance Validation**: NFR2.21 の合否（3種類の DB）、2つのプールの時間切れの累計、`bizCreate`・`bizUpdate`・`bizWritePoolLimit` を前の Intent の結果と並べる（NFR2.23）。
- **Deployment Pipeline**: `cicd-pipeline.md` 3節・4節（`.env` を変えない、戻しはイメージだけ）を前の Intent の配備の手順に重ねる。
