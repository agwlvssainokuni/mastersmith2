# 監視の設計 — U2 u2-reference-write-check

NFR 設計の監視の考え（`observability-design.md`）を、手元の監視（grafana/otel-lgtm。compose の profile で見たいときだけ起動）の式とパネルに当てはめる。U2 は**新しい警報・パネル・区画を足さない**（NFR2.29・NFR3.28）。作成・更新の道は既存の業務データの区画と警報 `ms-biz-target-db` にすでに入っているため、足すのは既存のパネル 2 つの説明の文だけ。U1 の監視の設計（`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/infrastructure-design/monitoring-design.md`）が足すパネル id 46・47 と説明の直し（id 40・42）の上に重ねて書き、2つ目の写しは作らない。ダッシュボードと警報の決まりはリポジトリのファイルで持ち、画面からは変えない（`project.md` の学び）。

## Sources

- この段の質問のファイル `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/infrastructure-design/infrastructure-design-questions.md`（決まっていること: 既存の `ms-biz-target-db` で拾い、新しい警報は足さない）
- この単位の NFR 設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/nfr-design/`: `observability-design.md`（1節〜4節、「承認の場の決定」）、`reliability-design.md`（2節の失敗の写し）、`performance-design.md`（2節・5節）、`scalability-design.md`（3節）、`security-design.md`（4節）、`logical-components.md`（3節）
- この単位の NFR 要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/nfr-requirements/observability-requirements.md`（NFR1.32・NFR2.29・NFR3.28）
- この単位の機能設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/functional-design/functional-spec.md`（2節の応答の一覧、4節の失敗の写しの表）
- ドメイン設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/domain-design/components.md`（BusinessData・AuditLog）と契約 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-summary.md`（C3・C4）
- U1 の監視の設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/infrastructure-design/monitoring-design.md`
- 読み取りだけで確かめた既存の設定: `docker/monitoring/dashboards/mastersmith-overview.json`（業務データの区画 id 39〜45。id 40 の説明に「作成と更新は同じ uri で、method で見分ける」、id 43 は 409 の件数、id 45 は書き込みのプール）、`docker/monitoring/provisioning/alerting/mastersmith.yaml`（`ms-biz-target-db` の式は `uri=~"/api/tables/.*"`・`status="503"`、`for: 0s`）、`backend/src/main/java/cherry/mastersmith/businessdata/web/BusinessDataPaths.java`（`ROWS` は `/api/tables/rows`、`DELETE` は `/api/tables/rows/delete`）

## 1. 指標と KPI

指標の名前は既存の式と同じ `http_server_requests_milliseconds_*`。作成（`POST`）と更新（`PATCH`）は同じ `uri="/api/tables/rows"` で、`method` で見分ける。U2 は道を足さないため、`uri` の値も系列の数も増えない。

| Metric | Source | Threshold | Why it matters |
|---|---|---|---|
| 作成・更新の要求の数（状態コードごと） | 既存のパネル id 40（`uri=~"/api/tables/.*"`、`method` が `POST`・`PATCH`、`uri="/api/tables/rows"`） | なし（量を見る） | 参照の値を入れた保存も入れない保存も同じ系列。指標では分けない（参照の有無は本文の中のため） |
| 作成・更新の 503 の数 | 既存のパネル id 42（`status="503"`） | 1 件でも（警報は既存の `ms-biz-target-db`） | ⑩ の時間切れ・借りられない・読み取りの設定が無い（`BUSINESS_TARGET_DB_TIMEOUT`・`BUSINESS_TARGET_DB_UNAVAILABLE`）。⑪ の書き込みの 503 と同じ系列に入る |
| 作成・更新の 409 の数 | 既存のパネル id 43（`status="409"`） | なし（業務の誤り。警報にしない） | 参照の拒否（`BUSINESS_REFERENCE_NOT_FOUND`、項目の理由 `REFERENCE_NOT_FOUND`・`NOT_EDITABLE_NOT_NULL`）と、⑩ の型の食い違い（`BUSINESS_TARGET_DB_MISMATCH`）がここに入る（2節） |
| 作成・更新の時間（95 パーセンタイル） | 既存のパネル id 41 | 目安の線 1,000 ms（判定は k6 の `iteration_duration`） | ⑩ の分（平常 30 ms 未満の見込み。`performance-design.md` 2節）が乗る |
| 対象DB の読み取り・書き込みのプールの使用中・待ち・時間切れ | 既存のパネル id 44・45 | 時間切れが 0 でない | ⑩ は読み取りのプールを1要求0〜1回、⑪ は書き込みのプールを1回使う。2本を同時に持たない |
| ⑩ の失敗の種類 | アプリのログ（Loki）の診断の WARN（`BusinessDataWriteService` のロガー、キー `purpose`（⑩ の目的の値）・`stage`・`reason`・`sqlState`・`vendorCode`・`exceptionClass`・`budgetExceeded`） | なし（調べるときに見る） | 時間切れ・借りられない・型の食い違い・全体の上限の超過・どの読み取り（`BORROW`・`PREVIOUS_ROW`・`COUNT_STATE`・`CHECK`）かを見分ける（`observability-design.md` 2.1） |
| 参照の拒否の件数 | 監査の行（`BUSINESS_ROW_CREATED`・`BUSINESS_ROW_UPDATED` の `FAILURE`・理由 `BUSINESS_REFERENCE_NOT_FOUND`） | なし | 監査は監視の代わりにしない。調べるときに内部DB の複写を読み取りで開いて数える |

## 2. ダッシュボード（パネルは足さない）

既存の `docker/monitoring/dashboards/mastersmith-overview.json` の業務データの区画（行 id 39）のパネルの**説明の文だけ**を直す。式・位置・種類は変えない。U1 が同じ id 40・42 の説明を直すため、U1 の直しの後の文に足す形にする（同じ Intent の Observability Setup で1回にまとめて書く）。

| id | 今の説明（要点） | U2 が足す文 |
|---|---|---|
| 42（API ごとの 503 の件数） | 対象DB に届かない・答えない（`UNAVAILABLE`・`TIMEOUT`）。U1 の直しで参照の読み取りを含むことが足される | 「作成・更新（`POST`・`PATCH /api/tables/rows`）の 503 には、書き込みの失敗に加えて、保存の前の参照の確かめ（読み取りの接続）の時間切れ・接続の失敗も入る。どちらでも行は保存されない。見分けはログの WARN のキー `purpose`・`stage`」 |
| 43（API ごとの 409 の件数） | 業務の理由で受け付けなかった操作（参照先が無い・一意の重なり など） | 「作成・更新の `BUSINESS_REFERENCE_NOT_FOUND` は、外部キーの違反に加えて、保存の前の参照の確かめの拒否（参照先に無い・読めない・必須の参照項目に値を入れられない）も含む。両者は指標でもログの変換の WARN の code でも見分けない（同じ code）。型の食い違い `BUSINESS_TARGET_DB_MISMATCH` も 409 に入る」 |

U1 のパネル id 46・47（参照の API の要求の数・503 の件数）は、定義の取得とピッカーの道（`/api/tables/definition`・`/api/tables/reference/rows`）だけを数え、U2 の作成・更新の道は入らない。U2 の分は id 40〜43 で見る（新しいパネルを足さない理由: 道が既存の `/api/tables/rows` で、参照つきの保存とそうでない保存を指標で分けられないため、専用のパネルを作っても id 40・42・43 と同じ値になる）。

## 3. 警報

| Alert | Condition | Severity | Routes to |
|---|---|---|---|
| `ms-biz-target-db`（既存・変えない） | `sum(increase(http_server_requests_milliseconds_count{service_name="mastersmith",uri=~"/api/tables/.*",status="503"}[5m])) or vector(0)` が 1 以上、`for: 0s` | 中 | 手元の Grafana の警報の一覧だけ（通知の先は配備先が決まるまで置かない）。作成・更新の道 `/api/tables/rows` は `uri=~"/api/tables/.*"` に入るため、⑩ の 503 もこの式で拾う（道の確かめは Sources の `BusinessDataPaths`） |
| `ms-pool-pending`（既存・変えない） | 内部DB・対象DB のプールの待ちが 0 でない状態が 1 分 | 既存のまま | 同上 |
| `ms-5xx-ratio`・`ms-error-logs`（既存・変えない） | 5xx の割合・ERROR のログの数 | 既存のまま | 同上。想定外（500）は既存の ERROR で入る |

- 新しい警報は足さない（NFR2.29・NFR3.28）。
- 409 `BUSINESS_REFERENCE_NOT_FOUND` は警報にしない。利用者の入力（参照先に無い値・権限の無い参照先の値）で起きる業務の誤りで、対象DB の不調ではないため。急に増えたときは id 43 と監査の行で調べる（Incident Response の手順書で扱う）。
- `ms-biz-target-db` の注釈（summary）は変えない。今の注釈の「ログの WARN のキー reason で UNAVAILABLE と TIMEOUT を見分ける」は ⑩ の診断の WARN にもそのまま当たる。⑩ か ⑪ かの見分け（キー `purpose`・`stage`）は Incident Response の手順書に書く。

## 4. SLI と SLO

手元の監視を常に動かしていない間は、SLO を新しく置かず、判定は `Unverified` とする（`project.md` の学び）。合否は Performance Validation の k6 で決める。

| SLI | SLO target | Measurement window |
|---|---|---|
| 保存前の確かめつきの作成（`refCreateChecked`、仮の名前）の `iteration_duration` の p95 | 1,000 ms 未満（NFR2.21。緩めない）と `checks` の率 1 | k6 の場面 3 分・10 VU、3種類の対象DB それぞれ |
| 既存の `bizCreate`・`bizUpdate` の `iteration_duration` の p95 | 1,000 ms 未満のまま（NFR2.23。参照の無い保存が遅くならない） | 同上 |
| 読み取り・書き込みのプールの待ちの時間切れの累計（`refCreateChecked` の前後） | 0 | 同上（使い捨てのアプリにだけ公開した metrics） |
| 作成・更新の 503 の数 | 平常で 0（警報 `ms-biz-target-db` の 5 分の窓） | 手元の監視を動かしている間 |

## 5. ログとトレース

| 項目 | 決めたこと |
|---|---|
| ログの集め方 | 既存のまま。アプリの JSON のログを otel-collector 経由で Loki へ（外部エクスポートは既定で無効、手元の監視を起動したときだけ） |
| 診断の WARN | ⑩ の対象DB の失敗1つについて、`BusinessDataWriteService` のロガーで1行、スタックトレースなし（NFR1.32 の読み方。`observability-design.md` 2.2）。これとは別に、既存の `GlobalExceptionHandler` の変換の WARN（キー `code`・`status`・`exceptionType`）が1行出る |
| 出さないもの | スキーマ・テーブル・カラムの名前、参照の値・行の値、接続先、例外のメッセージ（`project.md` の Forbidden） |
| 出さない場合 | 参照の拒否（409 `BUSINESS_REFERENCE_NOT_FOUND`）と 400 では診断の WARN を出さない（変換の WARN だけ） |
| Loki の問い合わせ（調べるとき） | U1 の `{service_name="mastersmith"} \| json \| purpose="REFERENCE"` は、⑩ の目的の値が同じ名前なら U1 の `ReferenceLookup` の WARN と U2 の WARN の両方に当たる。U2 だけを見るときはロガーの名前（`BusinessDataWriteService`）か `stage` のキーで絞る。目的の値とキーの名前はコード生成で決まるため、Observability Setup で実際のログで確かめてから手順書に書く |
| トレース | 既存の Micrometer Tracing の範囲のまま、新しいスパンを足さない。道の属性は固定の道 `/api/tables/rows` だけで値を持たない（`security-design.md` 4.2） |

表の中の `\|` は Markdown の表のための書き方で、問い合わせの中では `|` 1つ。

## 6. 確かめ方（Observability Setup）

| 順 | 確かめ |
|---|---|
| 1 | 使い捨てのアプリで、参照つきの作成・更新の系列が `uri="/api/tables/rows"`・`method="POST"`・`"PATCH"` に入ること（道のラベルが増えないこと）を確かめる |
| 2 | 要求は指標の送信の周期（1 分）を複数またいで、待ちを入れた少ない数で送る（負荷の試験の場面ではなく。lgtm の受け手がメモリの上限で止まった学び） |
| 3 | 使い捨ての環境で対象DB を止め、参照の値を入れた作成の 503 で `ms-biz-target-db` が firing になることを確かめる。`docker pause` では借りる待ちの `UNAVAILABLE`（`stage=BORROW`）になる。`TIMEOUT` は問い合わせの最中に止めないと起きないため、結合テスト（`reliability-design.md` 6節の T-T1）で確かめ済みとする |
| 4 | 参照先に無い値で作成し、id 43 に 409 が出て、警報が鳴らないこと、診断の WARN が出ないことを確かめる |
| 5 | Loki で ⑩ の診断の WARN のキー（`purpose`・`stage`・`reason`・`budgetExceeded`）の名前と、U1 の WARN との見分け方を確かめる |
| 6 | lgtm を網につないだ後にアプリを起動する（先に起動するとログの送信の失敗の ERROR が出る） |
| 7 | 2節の説明の文を `mastersmith-overview.json` に書く（U1 の id 40・42 の直しと同じ回にまとめる） |

確かめのために送る要求のうち、参照の拒否（409）と作成の成功は使い捨ての環境の監査に残る（`BUSINESS_ROW_CREATED` の `FAILURE`・`SUCCESS`）。配備した環境では行わない。前提を作る操作（ロール・割り当て・DSL の適用）も使い捨ての環境の監査に残る。

## 上流との差

| 上流 | 差 | 扱い |
|---|---|---|
| NFR 設計 `observability-design.md` 1節（区画と警報を足さない） | パネルは足さないが、id 42・43 の説明の文に保存前の確かめの 503・409 を足す | 説明の文だけで、式・パネル・警報は変えない。読む人が 503・409 の出どころを取り違えないため |
| U1 の監視の設計 2節（パネル id 46・47 は参照の読み取りの入口） | U2 の作成・更新の道は id 46・47 に入らない | 2節に書いた。U2 の分は既存の id 40〜43 で見る |

## 後の段に回すこと

- **Observability Setup**: 6節の確かめ、2節の説明の文の追記（U1 の直しと同じ回）。
- **Code Generation**: ⑩ の診断の WARN の1行とそのテスト、目的と段の値の名前（`observability-design.md` 2節）。
- **Incident Response**: 作成・更新の 503 が ⑩（`purpose` が ⑩ の値）か ⑪（`purpose=WRITE`）かの見分け方、409 `BUSINESS_REFERENCE_NOT_FOUND` が急に増えたときの調べ方（id 43 と監査の行）、監査に残る理由（保存前の拒否は残る）を手順書に足す。
