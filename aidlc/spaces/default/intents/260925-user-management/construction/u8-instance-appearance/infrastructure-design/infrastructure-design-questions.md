# Infrastructure Design の質問 — u8-instance-appearance（インスタンスの見た目の設定、service）

U8 は、設定（`application.yaml` と環境変数）からブランドカラーとフォントファミリーを起動時に1回だけ読み、ログインなしで読める `GET /api/appearance`（契約 C7）で返す小さなバックエンドです。service の単位のため、この段の成果物は `infrastructure-specification.md`・`monitoring-design.md`・`cicd-pipeline.md`・`traceability.json` です。

U8 の基盤（設定の受け取り・コンテナ・監視・検査の流れ・負荷の試験）は、承認済みの NFR 要件・NFR 設計と既存の仕組みで中身が決まっており、新しく依頼者に尋ねる論点はありません。そのため質問は作らず、基盤の設計の要点（案）を示して確かめます（`aidlc/spaces/default/memory/project.md` の Way of Working）。配備先は開発者の PC 上のコンテナだけで、クラウドの基盤は作りません（`project.md` の Deployment）。

読んだ上流:

- この単位の承認済みの NFR 設計 `aidlc/spaces/default/intents/260925-user-management/construction/u8-instance-appearance/nfr-design/`（`performance-design.md`・`security-design.md`・`scalability-design.md`・`reliability-design.md`・`observability-design.md`・`logical-components.md`・`traceability.json`・`nfr-design-questions.md` の Q1: A・Q2: A）と、承認の場の決定（監査ログ 2026-09-27 の `GATE_APPROVED`・`DECISION_RECORDED`: U8 R-01 は U3 で採用済みで受け入れ、U8 R-02 は B4 の最初に指標の可否を確かめる、上流との差 A5（`common.security` の説明文の書き直しとカバレッジの一覧の扱い）は B3 か B4 の計画で確かめる）
- この単位の承認済みの NFR 要件 `.../u8-instance-appearance/nfr-requirements/`（NFR4.1〜NFR4.8、NFR5.1・NFR5.2、NFR6.1〜NFR6.6、NFR9.1〜NFR9.8、`tech-stack-decisions.md`、Q1: A）
- この単位の承認済みの機能設計 `.../u8-instance-appearance/functional-design/functional-spec.md`（2節の設定の項目と環境変数、`rules.md` の BR1.x〜BR3.x）
- 部品の一覧 `aidlc/spaces/default/intents/260925-user-management/inception/domain-design/components.md`（InstanceAppearance、`entities: []`）と契約 `inception/contract-design/contract-summary.md` の C7
- 決まり `aidlc/spaces/default/memory/org.md`・`team.md`・`project.md`・`phases/construction.md`
- 既存の仕組み: `compose.yaml`（`app` は `.env` を `env_file` で読む）・`Dockerfile`・`.env.example`・`backend/src/main/resources/application.yaml`（`${環境変数:}` の書き方）・`.github/workflows/ci.yml`（`./gradlew verify`）・`backend/build.gradle.kts`（`packagesJudgedByTotal` に `cherry.mastersmith.common.security` を含む）・`perf/k6/scenarios.js`（`SCENARIO` の場面と `thresholdsFor`）・`perf/README.md`・`docker/perf/compose.yaml`・`docker/monitoring/dashboards/mastersmith-overview.json`（URI ごとの p95 のパネル）・`docker/monitoring/provisioning/alerting/mastersmith.yaml`

## Infrastructure Design の要点（案）

### 配備と設定（`infrastructure-specification.md`）

1. **実行の形**: 既存の実行可能 WAR を既存の `Dockerfile`・`compose.yaml` の `app` で動かす。U8 のためのコンテナ・ボリューム・ポート・資源の上限（CPU 4・メモリ 2g の既定）・健全性の確かめの変更は無い。インスタンスは1つ（内部DB が組み込みの H2 のため。NFR6.4）。
2. **設定の受け取り**: `application.yaml` に `mastersmith.appearance.brand-color: ${MASTERSMITH_APPEARANCE_BRAND_COLOR:}`・`mastersmith.appearance.font-family: ${MASTERSMITH_APPEARANCE_FONT_FAMILY:}` を既存の書き方どおり空を既定にして足す。`.env.example` に2項目を値を空・コメントで許される値（blue・green・purple・orange / sans・serif）と既定（blue・sans）を添えて足す。`compose.yaml` の `app` はすでに `.env` を `env_file` で読むため、`compose.yaml` は変えない（前の Intent の前例と同じ）。README の設定の一覧に2項目・許される値・既定・「変更は起動し直しで当たる」を足す（functional-spec.md の2節、NFR6.4）。
3. **秘密情報と個人に関する値**: 2項目は秘密ではなく、個人に関する値も扱わない。それでも設定された元の文字列は応答にもログにも出さない（応答は列挙の値だけ、警告のログは項目の名前・既定・許される値だけ。NFR4.5・NFR9.4）。新しい秘密情報・鍵ファイルは無く、`.env` をコミットしない決まりはそのまま。
4. **設定の不正のとき**: 設定の型は文字列で受け `@Validated` を付けないため、どの値でも起動は止まらず既定（blue・sans）で動き、許されない値の項目ごとに WARN を1件（スタックトレースなし）出す。空・空白だけは警告なしで既定（NFR9.1・NFR9.4、BR1.3・BR1.4）。起動の後に失敗しうる依存が無いため、健全性の判定（HealthIndicator）は足さず、既存の `/actuator/health` と compose の健全性の確かめのまま（NFR9.6）。
5. **内部DB と資源**: U8 は内部DB の表・移行（Flyway）・接続を持たない。Flyway の新しい版は足さず、バックアップの対象も無い。接続プール（既定の上限 30）を使わないため、プールが尽きたとき・詰め直しの一時停止のあいだも影響を受けない（NFR5.1・NFR5.2）。保持するのは起動時に作る変わらない `record` 1つだけ（NFR6.2・NFR6.3）。
6. **公開の範囲**: 既存のフィルターの連鎖に、差し込み口（order 410、機能の名前の割り当ての appearance 400 台）で「GET・`/api/appearance`」だけを認証なしにする決まりを1つ足す。ポートは既存どおり PC の `127.0.0.1:8080` だけに結び付け、共通のヘッダー・CSP・`Cache-Control: no-store` は変えない。ログインなしの API の回数の制限は置かず、配備先が決まったときに前段（逆プロキシなど）の制限で扱う（NFR4.1〜NFR4.3・NFR4.6〜NFR4.8）。
7. **共有する資源**: 要求のスレッド・フィルターの連鎖・共通のエラー応答・Micrometer を、ほかの機能と共有する（決まりを1つ足すだけで、共有の資源の設定は変えない）。内部DB の接続プールは共有しない（`logical-components.md` の4節）。

### 監視（`monitoring-design.md`）

8. **指標**: 独自の指標は足さない。既存の `http.server.requests`（Prometheus では `http_server_requests_milliseconds_*`）の `uri="/api/appearance"` で、要求の数・`status` ごとの数・p95 を見る（NFR6.5）。「内部DB の接続を借りない」ことは運用の指標に置かず、結合テストで確かめる（NFR5.2）。
9. **SLI・SLO**: SLI は `uri="/api/appearance"` の 200 の割合と応答時間の p95。SLO は応答時間 p95 300 ミリ秒以内（同時 10 件、NFR6.1）。可用性の SLO は U8 だけには置かない。手元の監視を常に動かしていない間は判定を `Unverified` とし、持ち主の段（observability-setup・feedback-optimization）に引き継ぐ（NFR9.3、`project.md` の Deployment）。
10. **ダッシュボードと警報**: 承認済みの NFR 設計（`observability-design.md` の1節・4節）のとおり、既存のダッシュボードに `/api/appearance` の p95 のパネルを足すか、U8 だけの警報を足すかは observability-setup の段で決める。この段の `monitoring-design.md` には、パネルの候補（既存の「ログインの API」などと同じ形の p95 の式）と、U8 だけの警報は置かずアプリ全体の既存の警報（アプリの停止・5xx の割合・ERROR のログ）で拾う考え方を記録する。式は observability-setup で起動して `uri` のラベルの値を確かめてから書き、書いた後にすべて実行して確かめる（`project.md` の Corrections）。
11. **ログ・トレース・監査**: 起動時の WARN はキー・値の API で出し、要求の外のためトレース ID は付かない。要求ごとのログは足さない。トレースは既存の Micrometer Tracing のままで独自のスパン・属性を足さず、外部エクスポートは既定で無効（NFR6.6、`team.md` の Deployment）。読み取りは監査の出来事を出さない（NFR9.5）。

### 検査の流れと配備（`cicd-pipeline.md`）

12. **1コマンドの検査**: 既存の `./gradlew verify` のまま（CI の `.github/workflows/ci.yml` も同じタスク）。U8 の単体テスト（判定の関数・jqwik の性質ベースのテスト・判定が1回だけ）は単体テストの段、結合テスト（公開の範囲 401・405・200、未認証の HEAD、使えないトークン、応答の2項目と元の文字列が出ないこと、ヘッダー、警告のログ、監査なし、接続を借りない）は結合テストの段、`AppearanceBoundaryArchitectureTest` は単体テストの段で動く。ビルドの設定・CI の変更は無い。
13. **カバレッジ**: 新しいパッケージ `appearance.config`・`appearance.service`・`appearance.web` は、`backend/build.gradle.kts` の仕組みで自動的にパッケージごとの下限（行 80%・分岐 70%）の対象になる。計測の除外は増やさない。値は `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測して記録する（NFR9.7）。`common.security`（`packagesJudgedByTotal` の一覧にある）の説明文の書き直しの扱いは、承認の場の決定 A5 のとおり B3 か B4 の計画で依頼者に確かめる（この段では決めない）。
14. **負荷の試験**: コード生成（B4）で `perf/k6/scenarios.js` に見た目の設定の場面を1本足す（トークンなしの GET、名前は仮に `appearance`）。前例（`dslLight`）どおり `thresholdsFor` に `http_req_duration{name:appearance}` の `p(95)<300` を置き、checks で全件 200・本文が2項目を確かめる。場面の説明を `perf/README.md` の場面の一覧に足す。測定は performance-validation の段が使い捨ての環境（`docker/perf/compose.yaml`、内部DB のファイルが膨らんだ状態、`caffeinate -i`）で行い、`./gradlew verify` と CI の外に置く（NFR6.1）。この API は監査に残らないため、試験で監査ログは増えない。
15. **E2E**: U8 の単独の E2E は足さない。画面での見た目の反映は U4 の検査で扱い、E2E で使う WAR は設定なし（既定の blue・sans）で動く。この段に持ち越された論点（Mailpit・招待のベース URL・V7・V8）は U8 に関わらない。
16. **配備と戻し**: 既存の流れ（WAR を作りイメージを作り直して `docker compose up -d`、ヘルスチェックとスモークテスト）のまま。スモークテストに未認証の `GET /api/appearance` が 200 で2項目を返すことを足す候補とし、具体は deployment-pipeline の段で決める。戻しはイメージだけを直前の版に戻す既存の決まりのまま。直前の版には `/api/appearance` が無く、画面は失敗として既定のまま描く（U4 W3）。`.env` に足した2項目は直前の版では使われず害が無いため、戻すときに消さなくてよい。設定の値の変更の戻しは `.env` の2項目を元に戻して起動し直す（`reliability-design.md` の6節）。
17. **上流との差**: 承認済みの文書と食い違う点は無い。要点 14 の k6 の閾値の置き方（`thresholdsFor`）と要点 16 の戻しのときの `.env` の扱いは、承認済みの設計に書かれていない具体を既存の前例で埋めた追加として成果物に明記する。

## Consolidated Summary Confirmation

答えのまとめ:

- 質問はありません。上の「Infrastructure Design の要点（案）」の 17 件のとおりに、U8 の基盤の設計の文書（`infrastructure-specification.md`・`monitoring-design.md`・`cicd-pipeline.md`・`traceability.json`）を作ります。
- 要点の中心: 設定は `application.yaml`（空を既定）・`.env.example`・README に2項目を足し `compose.yaml` は変えない / 不正な値は既定で起動し WARN を1件（値は出さない）/ 内部DB・Flyway・接続を使わない / 監視は既存の `http.server.requests` の `uri="/api/appearance"` で、パネル・警報を足すかは observability-setup で決める / 検査は既存の `./gradlew verify` のまま / p95 300 ミリ秒は B4 で k6 の場面を足し performance-validation で測る / 戻しはイメージだけ。

Does this all look correct before I generate the artifact?

- Looks correct — 要点のとおりに成果物を作る
- Request changes — 直したい点を書いてください

[Answer]: Looks correct
