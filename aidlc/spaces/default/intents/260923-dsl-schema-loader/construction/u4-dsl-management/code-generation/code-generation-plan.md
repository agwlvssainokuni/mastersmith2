# Code Generation Plan — U4 DSL の管理（u4-dsl-management）

U4 のコード生成の計画を示す。作るものは、管理者だけの DSL の管理の API（`/api/admin/dsl/**`、契約 C6）、プレビューと適用の履歴の保存、適用のトランザクション、プレビューの要約・違い・照合、起動時の適用中の読み込み、監査の出来事（契約 C7）、重い処理の同時の数の制限、道ごとの本文の上限、指標とログとダッシュボードである（パッケージ `cherry.mastersmith.dslmanage` の `web`・`service`・`domain`・`repository` と、既存の `common`・`config`・`audit` の拡張）。Bolt は B4（Must）と B5（Should）（`inception/delivery-planning/bolt-plan.md`）。

## 1. 入力にした設計

| 文書 | 使うところ |
|---|---|
| `construction/u4-dsl-management/functional-design/functional-spec.md`・`rules.md`・`entities.md` | 状態の移り変わり、各 API の手順、同時の適用、失敗のふるまい、決まり BR1.1〜BR8.3、保存するもの・出来事 |
| `construction/u4-dsl-management/nfr-requirements/*.md` | 時間・資源の目標（NFR1.6・NFR1.8〜NFR1.17）、NFR2.6・NFR3.7・NFR3.8・NFR4.10・NFR5.3〜NFR5.5・NFR7.8・NFR8.1〜NFR8.5、`DSL_BUSY` |
| `construction/u4-dsl-management/nfr-design/*.md` | 部品と置き場、本文の上限の置き場（決定 A）、重い処理の許可、プレビューの読み込みの保持、照合（決定 B）、指標・ログ・ダッシュボード、承認済みの文書との差 |
| `construction/u4-dsl-management/infrastructure-design/*.md` | Flyway の V5・V6、設定 2つ、共有するものの変更、テストの段、ダッシュボードの行 |
| `inception/contract-design/contract-summary.md` の C1・C4・C5・C6・C7 | 使う口（U1・U2・U3）、API の形と code の一覧、監査への出来事 |
| `inception/user-stories/stories.md` | US1.1・US2.1・US2.2・US2.3・US3.1〜US3.4・US4.1・US4.2・US6.3（B4）、US5.1・US5.2（B5）の U4 の部分（機能設計の traceability.json で OK のもの） |

## 2. 前提と、この計画での読み方

- **承認の場で決まったこと（すべて反映する）**:
  - 決定 A（NFR Design）: 既存の `RequestSizeLimitFilter` を `SecurityConfig` で認証（`BearerTokenAuthenticationFilter`）と認可（`AuthorizationFilter`）の後に移し、道ごとの上限と code の表を足す。`POST /api/admin/dsl/preview` は 10MB・`DSL_TOO_LARGE`、ほかは今までどおり 1MB・既存の code。413 で断った投入も、Filter の「断ったことを知らせる口」を通して U4 が操作した人つきで監査（`DSL_SUBMISSION_REJECTED`、理由 SIZE_LIMIT、識別なし）に出す。`common.web` は DSL を知らない。
  - 決定 B（NFR Design）: 照合の全体の上限は作らない。照合は U1 の `readSchema(COMPARE)`（接続 3 秒・問い合わせ 1回 5 秒、U1 で実装済み）だけ。
  - 決定 C（NFR Design）: ログインなしで大きな本文を送って 413 を確かめている既存のテスト3件（`ExposureIT` の2件、`SecurityHeadersIT` の1件）は直さない。テスト用の決まり（`backend/src/test/java/cherry/mastersmith/access/testsupport/PublicApiTestRules.java`）の下で 413 のまま通ることを確かめる。`construction/u4-dsl-management/nfr-design/security-design.md` 1節・5節の「既存のテスト3件はコード生成で直す」はこの決定で読み替える。通らなければ直さずに止めて報告する。
  - Infrastructure Design の承認の場: `traceability.json` に NFR8.3・NFR5.3 を含める（反映済みの設計のとおり）。
- **大きさの上限は 10MB**（U3 の NFR 要件の決定）。契約 C6・機能設計の 5MB は書き換えない。
- **本文はバイト列で保存**（NFR 設計の差）。列は `BINARY LARGE OBJECT` で、列の名前は本文のバイト列だと分かるもの（例 `yaml_bytes`）にする。
- **`DSL_BUSY`（503）** を足す（NFR 要件の決定）。重い道（`POST /preview/generate`・`POST /preview`・`POST /history/{revisionId}/restore`・`GET /preview`）だけ、本文を読む前に許可を取る（`Semaphore(1)`、待たない）。断った要求は状態を変えず、監査の出来事を出さない。
- **Bolt の分け方**: B4（Must）を Step 1〜14、B5（Should: 履歴からの戻し US5.1・適用中のダウンロード US5.2）を Step 15〜17 に分ける。B4 の Step 14 で `./gradlew verify` を通した後、依頼者にコミットを確かめてから B5 に進む（team.md の「1 Bolt が1コミット」）。履歴の一覧（GET /history）は B4 の今の状態と同じ軽い道として B4 に含め、戻しとダウンロード（適用中）を B5 にする。
- **誤りの文言**: U2 の `DslMessageKeys` のすべての鍵に、ja・en の文言を用意し、要求の表示言語（既存の `AcceptLanguageResolver`）で `message` にする。鍵の抜けは単体テストで止める。
- **一括のアクセス制御のテスト**（C6 の 11 本の API を並べた 401・403・200、AC6.2.1〜AC6.2.3）は U5 の Bolt で書く（U5 の機能設計 BR8.1a）。U4 は各 API の 401・403 の代表を結合テストで確かめる。
- **時間・資源の測定**（NFR1.6・NFR1.8・NFR1.10〜NFR1.12、10MB の DSL のメモリ、H2 の大きさ、接続の2本使いの余裕）は Build and Test で行う。
- **U1〜U3 の共通の関門**: パッケージごとのカバレッジの下限は `cherry.mastersmith.dslmanage` の下の新しいパッケージに当たる。既存のパッケージ（`common.web`・`config`・`audit` など）に足す変更は、全体の合計で判定される。

## 3. 作るもの

| 置き場 | 部品 | 役割 |
|---|---|---|
| `dslmanage.domain` | `DslSource`・`DslPreviewRecord`・`DslAppliedRevisionRecord`（JPA のエンティティ）、`PreviewView`（要約・違い・警告の値）、`DslOperationEvent`（契約 C7）、`DslProblemTypes`（code の定義） | 保存するものと出来事、応答の元の値 |
| `dslmanage.repository` | `DslPreviewRepository`（固定の鍵の1行への1文の MERGE、previewId を指定した削除の件数）・`DslAppliedRevisionRepository`（本文を取らない射影、上限を超えた古い行の削除） | 内部DB |
| `dslmanage.service` | `DslLifecycle` | 生成・投入・戻し・適用・破棄・起動時の読み込み。トランザクションの境界。適用は1つのトランザクション（BR4.2・BR4.3）、確定の後に適用中の差し替えと出来事（BR4.6）。結果の型を業務の例外に変える（BR8.1） |
| `dslmanage.service` | `DslPreviewAnalysis`・`DslDiffCalculator`・`DslSummaryCalculator`・`DslReconciler`・`DslPreviewCache` | 要約（BR2.3）・違い（BR2.2）・照合の警告（BR2.4）。プレビューの読み込みの結果を同じ previewId の間だけ1つ持つ |
| `dslmanage.service` | `DslOperationMetrics`・`DslErrorMessages`・`DslProblemTypeCatalog`・`DslStartupLoader` | 指標（`mastersmith.dsl.operation`）と操作ごとの INFO のログ、誤りの鍵から表示言語の文言、code の登録（日英の説明）、起動時の適用中の読み込み（BR5.1・BR5.2） |
| `dslmanage.web` | `DslAdminController`・要求と応答の `record`（`XxxRequest`・`XxxResponse`）・`DslHeavyOperationGate`（`HandlerInterceptor`）・`DslSubmitRejectionListener`（Filter の知らせを受けて監査と指標） | 契約 C6 の HTTP の受け渡し |
| `common.error`（既存） | 業務の例外に追加の項目（`errors`・`total`）を持たせる口と、共通の変換がそれを載せる仕組み（既存の項目名は上書きしない、BR8.1） | 既存の拡張 |
| `common.web`（既存）・`config`（既存） | `RequestSizeLimitFilter` に道ごとの上限と code の表、断ったことを知らせる口。`SecurityConfig` で置き場を認証・認可の後へ（決定 A） | 既存の拡張 |
| `audit`（既存） | 出来事の種類（`DSL_*`）と列（操作した人・識別・出どころ・理由の種類）、`DslOperationEvent` を確定の後に記録する受け手（既存の仕組み） | 既存の拡張 |
| `backend/src/main/resources/db/migration/` | `V5__u4_dsl_management.sql`（プレビューと履歴の表）・`V6__u4_dsl_audit_columns.sql`（監査の表に NULL を許す列） | Flyway の前進のみ |
| 設定 | `mastersmith.dsl.max-submit-size`（`MASTERSMITH_DSL_MAX_SUBMIT_SIZE`、既定 10MB）・`mastersmith.dsl.history-limit`（`MASTERSMITH_DSL_HISTORY_LIMIT`、既定 20、1以上） | `application.yaml`・`.env.example`・README |
| 監視 | `docker/monitoring/dashboards/mastersmith-overview.json` に DSL の操作の行 | ファイルが正 |

## 4. 手順

各層で実装を書き、同じ Bolt の中でその層のテストを書いて実行し、通ってから次の層へ進む（test-after、Testing Contract の `ordering`）。

### B4（Must）

#### Step 1: 骨組みと設定

- [x] `dslmanage` の `web`・`service`・`domain`・`repository` の `package-info.java`
- [x] `application.yaml` に `mastersmith.dsl.max-submit-size`・`mastersmith.dsl.history-limit`（環境変数の参照と既定値）、`.env.example` に項目名を空の値で足す。設定の型は `dslmanage` の中に置く
- [x] 対応するストーリー: US2.1・US4.1

#### Step 2: DB の移行と保存 — 実装

- [x] `V5__u4_dsl_management.sql`（プレビューの表: 固定の鍵・previewId・本文のバイト列・識別・出どころ・置いた人・置いた日時（UTC）。履歴の表: revisionId・本文のバイト列・識別・出どころ・適用した人・適用した日時・追加の順（一意））。既存の命名（V1〜V4）に合わせる
- [x] `V6__u4_dsl_audit_columns.sql`（`audit_events` に NULL を許す列: 操作した人・DSL の識別・出どころ・理由の種類）
- [x] エンティティとリポジトリ（3節）。プレビューを置くのは1文の MERGE（BR1.4）、previewId を指定した削除は消した件数を返す（BR4.3）、今の状態と履歴は本文を取らない射影、上限を超えた古い履歴を追加の順で消す（BR4.4）
- [x] 対応するストーリー: US4.1・US4.2・US5.1（履歴の保存）

#### Step 3: DB の移行と保存 — テスト（結合、組み込みの H2）

- [x] 移行の後の表と列（既存の `AuditSchemaIT` の形）、MERGE で行が1つのまま置き換わる、previewId を指定した削除の件数（一致 1・不一致 0）、射影で本文を読まない、上限を超えた古い履歴の削除、10MB の本文を保存して同じバイト列で読める
- [x] `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.dslmanage.*'` を実行し、通す

#### Step 4: 要約・違い・照合 — 実装

- [x] `DslSummaryCalculator`（テーブル数・ビューの数・カラム数・メニューの木・表示名の未設定の先頭 100 件と総数、BR2.3）
- [x] `DslDiffCalculator`（テーブルは UNCHANGED を含めてすべて、カラムは ADDED・REMOVED・CHANGED だけ、`changedItems` は `label.ja`・`list.order` のような項目の名前、適用中が無ければすべて ADDED で `appliedExists` false、BR2.2）
- [x] `DslReconciler`（U1 の写しと比べ TABLE_MISSING・COLUMN_MISSING・TYPE_MISMATCH。U1 が UNCONFIGURED・UNAVAILABLE ならその警告を1件。失敗にしない。警告に接続先と内部の文言を含めない、BR2.4）
- [x] 対応するストーリー: US3.1（AC3.1.1〜AC3.1.7）、US3.2（AC3.2.1〜AC3.2.4・AC3.2.6）

#### Step 5: 要約・違い・照合 — テスト（単体）

- [x] 要約の件数と未設定の表示名の先頭 100 件と総数（101 件以上のとき）、違いの区分とテーブル・カラムの並び、`changedItems` の名前、適用中が無いとき、照合の3種類の警告と、UNCONFIGURED・UNAVAILABLE（理由ごと）の警告が1件で接続先を含まない
- [x] `./gradlew :backend:test --tests 'cherry.mastersmith.dslmanage.*'` を実行し、通す

#### Step 6: 業務処理（プレビューに置く・表示・破棄・適用・起動・状態・履歴） — 実装

- [x] `DslLifecycle`・`DslPreviewAnalysis`・`DslPreviewCache`・`DslStartupLoader`・`DslErrorMessages`・`DslProblemTypeCatalog`・`DslOperationMetrics`（3節）
- [x] 生成（BR1.1）: U3 の結果の型から 503 `TARGET_DB_UNCONFIGURED`・`TARGET_DB_UNAVAILABLE` の業務の例外に。U3 の想定外の失敗（`IllegalStateException`）はプレビューを変えずに 500
- [x] 投入（BR1.2・BR1.5・BR7.4）: U2 で読み込み、INVALID は 422 `DSL_INVALID`（誤りの先頭 100 件と総数、表示言語の文言）で保存しない。`DSL_SUBMISSION_REJECTED`（理由は最初の誤りの種類、識別あり）の出来事
- [x] 表示（BR2.1・BR2.5）・破棄（BR3.1）・適用（BR4.1〜BR4.7）・起動時の読み込み（BR5.1・BR5.2）・今の状態（BR6.3）・履歴の一覧（BR6.1）・プレビュー中のダウンロード（BR6.2）
- [x] 監査の出来事（BR7.1〜BR7.3）は確定の後に既存の受け手が記録する。出来事に本文・接続先を入れない
- [x] 指標（`mastersmith.dsl.operation`、`operation`・`outcome` のタグ、95 パーセンタイル）と操作ごとの INFO のログ（`dsl.operation`・`dsl.outcome`・`dsl.durationMs`・`dsl.hash`（先頭 12 文字）・`dsl.source`）
- [x] 対応するストーリー: US1.1・US2.1・US2.2・US3.1〜US3.4・US4.1・US4.2・US6.3（B4 の分）、BR1.1〜BR8.2、NFR1.13〜NFR1.17・NFR4.10・NFR5.4・NFR5.5・NFR7.8・NFR8.1〜NFR8.5

#### Step 7: 業務処理 — テスト（単体）

- [x] `DslLifecycle` の各操作（U1・U3 の口を差し替え、U2 は本物、リポジトリは差し替え）、結果の型から業務の例外への変換、適用で previewId が違う・無い・消した件数 0 のときの 409 と差し替えも出来事もしないこと、起動時に読めないときの ERROR 1件と「無い」、`DslErrorMessages` が `DslMessageKeys` のすべての鍵に ja・en の文言を持つこと、指標のタグの値が決まった語だけ、ログに本文・接続先・部品の例外の文言が無い
- [x] 単位の単体のコマンドを実行し、通す

#### Step 8: 共通の拡張（本文の上限・エラー応答・監査） — 実装

- [x] `RequestSizeLimitFilter` に道ごとの上限と code の表と、断ったことを知らせる口（断った道・上限・送られた大きさ）を足す。`Content-Length` があればヘッダーだけで判定し本文を読まない。chunked は読んだ量を数えて上限で止める（既存の働き）
- [x] `SecurityConfig` で置き場を認証・認可の後へ移す（決定 A）
- [x] 共通のエラー応答に追加の項目の口（BR8.1）
- [x] `audit` の出来事の種類と列（V6）、`DslOperationEvent` の記録
- [x] `DslSubmitRejectionListener`（投入の道の知らせで、操作した人つきの `DSL_SUBMISSION_REJECTED`（SIZE_LIMIT、識別なし）と指標 `submit`・`rejected`）
- [x] 対応するストーリー: US2.3（AC2.3.9・AC2.3.10）、US6.3（AC6.3.1）、NFR2.6・NFR5.3・NFR5.4

#### Step 9: 共通の拡張 — テスト

- [x] 単体: 道ごとの上限の表（投入の道 10MB・ほか 1MB）、知らせの口、追加の項目が既存の項目名を上書きしない、監査の出来事の記録の列
- [x] 既存のテスト（`common.web`・`common.error`・`audit`・`config` のもの）がすべて通る。決定 C の3件（`ExposureIT` の2件・`SecurityHeadersIT` の1件）は直さずに通ることを確かめる
- [x] `./gradlew :backend:test` と `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.common.*' --tests 'cherry.mastersmith.config.*' --tests 'cherry.mastersmith.audit.*'` を実行し、通す

#### Step 10: API — 実装

- [x] `DslAdminController` と要求・応答の `record`（契約 C6: `GET /status`・`GET /preview`・`POST /preview?source=UPLOAD|PASTE`（`application/yaml`、以外は 415）・`DELETE /preview`・`POST /preview/generate`・`GET /preview/download`・`POST /apply`・`GET /history`）。画面入出力の層は HTTP の受け渡しだけ（team.md の Code Style）
- [x] `DslHeavyOperationGate`（重い道だけ、本文を読む前に許可、取れたときだけ応答の後に必ず返す）
- [x] ダウンロードは `application/yaml`・`Content-Disposition: attachment; filename="dsl-preview-<識別の先頭12文字>.yaml"`（NFR3.8）
- [x] 対応するストーリー: B4 の各ストーリーの API の部分、NFR1.13・NFR3.7・NFR3.8

#### Step 11: API — テスト（結合、組み込みの H2 と U1 のコンテナ）

- [x] 各 API の 200・201・204 と、未認証 401・管理者でない 403 の代表（NFR3.7。一括は U5）
- [x] 投入: ちょうど 10MB は大きさで拒否されず、1 バイト超えは 413 `DSL_TOO_LARGE` と操作した人つきの監査（AC2.3.1・AC6.3.1）。ほかの API の 1MB 超えは既存の 413（AC2.3.9）。415。422 の誤りの一覧（先頭 100 件と総数、表示言語、部品の例外の文言なし、AC2.2.8）と監査（AC2.2.10・AC2.3.10）。画面を通さず API を直接呼んでも検証される（U2 の NFR3.4）
- [x] 生成: 3種類の DB のうち1種類以上のコンテナで 201、設定が無い・止まっている対象DB で 503（AC1.1.7・AC1.1.8）、応答と監査とログに接続先が無い（NFR4.10、AC3.4.4）
- [x] プレビューの表示: 要約・違い・警告。応答しない対象DB（U1 と同じ手元の待ち受け）で警告つきで返る（NFR1.9、決定 B）。対象DB が止まっていても表示・状態・履歴・適用・破棄・ダウンロードが使える（NFR7.8）
- [x] 適用: 1つのトランザクション、同時の適用で1件だけ成功し履歴が1件だけ増える（待ち合わせで重なりを作る、AC4.2.3、NFR8.2）、履歴の書き込みで失敗を差し込むと巻き戻り適用中が変わらない（AC4.2.4、NFR8.1）、同じ内容の再適用も履歴に足す、履歴の上限、適用で対象DB に接続しない
- [x] `DSL_BUSY`: 1つ目を止めた間に2つ目が 503 `DSL_BUSY`、状態・監査が変わらない（NFR1.13・NFR1.14）
- [x] 起動: 履歴の最新が読めれば適用中になり、読めなければ ERROR 1件で「無い」（NFR8.4）
- [x] 監査: 各出来事の必須の列、書き込みの失敗でも操作は成功しログ1件（AC6.3.3、NFR8.5）、本文・接続先が無い（AC6.3.2）
- [x] ログ: 操作ごとの INFO 1件のキー（NFR1.17）、想定内の失敗は WARN 以下でスタックトレースなし（NFR5.3）
- [x] 単位の結合のコマンドを実行し、通す

#### Step 12: 構造の検査

- [x] `src/test/java/cherry/mastersmith/dslmanage/DslManageBoundaryArchitectureTest.java`: `dslmanage.web` は `dslmanage.repository` を呼ばない、トランザクションは `dslmanage.service` だけ、`dslmanage` は `targetdb.config`・`targetdb.repository`・`dsl.parse`・`dsl.validate` を使わない、`common.web` は `dslmanage` を知らない
- [x] 単位の単体のコマンドを実行し、通す

#### Step 13: ダッシュボードと文書

- [x] `docker/monitoring/dashboards/mastersmith-overview.json` に DSL の操作の行（操作ごとの件数・結果ごとの件数・時間の 95 パーセンタイル）を足す。指標の名前とタグは結合テストで確かめた実際の名前を使う。式の実行による確かめは、手元の監視（compose の profile `monitoring`）が起動できるとき（既存のコンテナを止めずに、ほかの監視のコンテナが動いていないとき）にこの段で行い、できないときは持ち主を Build and Test として `code-summary.md` に記録する
- [x] `README.md` に書く: DSL の管理の API の一覧と code、設定 2つ、本文の上限の置き場の変化（ログインしていない大きな要求は 401）、重い処理の同時の数と `DSL_BUSY`、監査の出来事と列、ダッシュボードの行
- [x] 対応するストーリー: NFR1.16・NFR1.17

#### Step 14: B4 の検査と記録

- [x] `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` をコンテナの実行環境が動いている状態で実行し、すべての段が通ることを確かめる
- [x] `code-summary.md`（B4 の分）、`source-manifest.json`、`traceability.json`（B4 の分）を作る
- [x] ここで止め、依頼者に B4 のコミットを確かめる（B5 は確かめた後の別の指示で進める）

### B5（Should）

#### Step 15: 履歴からの戻しと適用中のダウンロード — 実装

- [x] `POST /history/{revisionId}/restore`（重い道。版が無ければ 404 `DSL_REVISION_NOT_FOUND`、今の U2 の検証を通らなければ 422 でプレビューは変わらず出来事を出さない、通れば出どころ RESTORE で置き、`DSL_SUBMITTED`（source RESTORE）、BR1.3）
- [x] `GET /applied/download`（適用中が無ければ 404 `DSL_APPLIED_NOT_FOUND`、ファイル名 `dsl-applied-<識別の先頭12文字>.yaml`、BR6.2）
- [x] 対応するストーリー: US5.1（AC5.1.1〜AC5.1.7）、US5.2（AC5.2.1〜AC5.2.3）

#### Step 16: 履歴からの戻しと適用中のダウンロード — テスト

- [x] 単体と結合（戻しの 201・404・422、戻したプレビューの出どころと出来事、件数の上限で消えた版が 404、`DSL_BUSY`、適用中のダウンロードの本文とヘッダー、未認証 401・管理者でない 403）
- [x] 単位の単体・結合のコマンドを実行し、通す

#### Step 17: B5 の検査と記録

- [x] README の API の一覧に2本を足す
- [x] `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行し、すべての段が通ることを確かめる
- [x] `code-summary.md`・`source-manifest.json`・`traceability.json` を B5 の分まで足して仕上げる

## 5. ストーリーと手順の対応

| ストーリー | 受け入れ基準（U4 の部分） | 手順 |
|---|---|---|
| US1.1 | AC1.1.1・AC1.1.5〜AC1.1.8・AC1.1.11 | Step 6〜11 |
| US2.1〜US2.3 | AC2.1.1〜AC2.1.3・AC2.1.6、AC2.2.8・AC2.2.10、AC2.3.1・AC2.3.9・AC2.3.10 | Step 6〜11 |
| US3.1〜US3.4 | AC3.1.1〜AC3.1.7、AC3.2.1〜AC3.2.4・AC3.2.6、AC3.3.2〜AC3.3.4、AC3.4.1〜AC3.4.5 | Step 4〜11 |
| US4.1・US4.2 | AC4.1.1・AC4.1.3〜AC4.1.10、AC4.2.1〜AC4.2.4 | Step 2・3・6〜11 |
| US6.3 | AC6.3.1〜AC6.3.4 | Step 6〜11 |
| US5.1・US5.2（B5） | AC5.1.1〜AC5.1.7、AC5.2.1〜AC5.2.3 | Step 15〜17 |

画面の働き（AC2.1.4・AC2.1.5・AC3.3.1・AC4.1.2）と一括のアクセス制御（AC6.2.x）は U5、生成の中身（AC1.1.2〜AC1.1.4）は U3、識別子と読み取り専用（AC1.1.9・AC1.1.10）は U1、時間（AC3.2.5、NFR1.6・NFR1.8・NFR1.10〜NFR1.12）は Build and Test が受け持つ。

## 6. テストの量（Standard）

| 部品 | 単体 | 結合 |
|---|---|---|
| 保存（移行・リポジトリ） | — | 6〜8 件 |
| 要約・違い・照合 | 8〜10 件 | — |
| 業務処理（`DslLifecycle` ほか） | 8〜12 件 | — |
| 共通の拡張（本文の上限・エラー応答・監査） | 5〜8 件 | 既存のテストを保つ |
| API（B4） | — | 15〜20 件 |
| 構造の検査 | 4 件 | — |
| 戻しと適用中のダウンロード（B5） | 3〜5 件 | 5〜7 件 |


## 7. Build and Test からの戻し（Loop-back 1）

Build and Test で2つの目標が Not Met になり、依頼者の選択「Retry with fix」（「Code Generation に戻って両方直す」）で戻した（`construction/build-and-test/test-results.md` の Loop-Back Log）。

1. NFR12.3: コンテナの実行環境に届かないとき、`DslTargetDbIT` がクラスの後片付けで `initializationError` になった。原因は `@ExtendWith({ContainerRuntimeCheck.class, OutputCaptureExtension.class})` の順（U1 と同じ）。U1 の Step 18 で足した構造の検査 `ExtensionOrderArchitectureTest` は、この `DslTargetDbIT` を違反として示している。
2. U4-STORAGE: 10MB の DSL の投入→適用を重ねると、古い履歴が消えても内部DB（H2）のファイルが毎回約 10.3MB 増え（21 回で 282MB・40 回で 461MB。期待は最大約 210MB）、起動し直しても縮まない。H2 のファイルは `SHUTDOWN COMPACT`・`SHUTDOWN DEFRAG` を実行しないと小さくならず、接続先に `;DEFRAG_ALWAYS=TRUE` を足すと閉じるときに詰め直しが自動で行われる（依頼者の知見）。今の既定の接続先 `jdbc:h2:file:./data/mastersmith` は指定していない。

### Step 18: 拡張の登録の順の直し（NFR12.3）

- [x] `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslTargetDbIT.java` の登録を `@ExtendWith({OutputCaptureExtension.class, ContainerRuntimeCheck.class})` に入れ替え、U1 と同じ理由のコメントを書く
- [x] `./gradlew :backend:cleanTest :backend:test --tests 'cherry.mastersmith.targetdb.testsupport.ExtensionOrderArchitectureTest'` が通る（違反 0）ことを確かめる
- [x] コンテナの実行環境に届かない状態（`DOCKER_HOST=unix:///nonexistent/docker.sock env -u CI`）で `./gradlew :backend:cleanIntegrationTest :backend:integrationTest --tests 'cherry.mastersmith.dslmanage.*'` を実行し、失敗 0・対象DB を使うテストが警告つきで SKIPPED になることを確かめる。colima は止めない

### Step 19: 内部DB の終了時の詰め直し（U4-STORAGE）

- [x] 内部DB の既定の接続先を `jdbc:h2:file:./data/mastersmith;DEFRAG_ALWAYS=TRUE` にする（`backend/src/main/resources/application.yaml`）。なぜ要るか（10MB の本文の履歴を消しても H2 のファイルは閉じるときに詰め直さないと縮まない）を日本語のコメントで書く。README の環境変数の表の `MASTERSMITH_DB_URL` の既定値と説明、`.env.example` の説明も合わせる。`MASTERSMITH_DB_URL` を上書きするときも `;DEFRAG_ALWAYS=TRUE` を付けることを README に書く
- [x] 再現の確かめ（不具合を直すときは再現するテストを同じ変更に含める決まり）: 組み込みの H2 の単体テストを `backend/src/test/java/cherry/mastersmith/config/` に足す。一時ディレクトリの H2 のファイルに大きな行（合わせて数十 MB。テストの時間は数秒の内）を書いて消し、閉じた後のファイルの大きさを比べる。`;DEFRAG_ALWAYS=TRUE` を付けたときは閉じた後に縮み、付けないときは縮まないことを確かめる。あわせて、`application.yaml` の既定の接続先に `DEFRAG_ALWAYS=TRUE` が含まれることを確かめる。テストの説明文は英語
- [x] `./gradlew :backend:test --tests 'cherry.mastersmith.config.*'` と、既存の内部DB を使う結合テスト（`./gradlew :backend:integrationTest --tests 'cherry.mastersmith.dslmanage.*' --tests 'cherry.mastersmith.config.*'`）を通す
- [ ] 実際の保存量（10MB の投入→適用を 21 回・40 回）、止めて起動し直した後の大きさ、動いている間の増え方、止めるのにかかる時間（`stop_grace_period: 45s` の内か）とデータの無事は、Build and Test で `perf/dsl-timing.sh --storage` を使って測り直す（この段では測らない）

### Step 20: 検査と記録

- [x] `./gradlew spotlessApply` のあと `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行し、すべての段が通ることを確かめる
- [x] `code-summary.md` に戻しの記録の節を足し、`source-manifest.json` と `traceability.json` を直す（NFR12.3 の U4 の分と、保存の量の確かめのテスト）

## Testing Contract

```json
{
  "version": 1,
  "methodology": "test-after",
  "source": "team",
  "ordering": "テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。",
  "scope": "classic",
  "test_strategy": "standard",
  "project_type": "brownfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる。ただし、既存のパッケージに単独で下限を下回るものがあれば、パッケージごとの下限は新しく作るパッケージだけに当てる（既存のパッケージは全体の合計で判定する）。どちらになるかは、パッケージごとの下限を入れる Bolt で既存のパッケージのカバレッジを実測して決め、結果を記録する。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストをどこで実行するかは、Build and Test で `./gradlew verify` の時間と colima の VM のメモリを実測してから決める。決まるまでは、3種類すべてを `./gradlew verify` の中で毎回実行する（CI も同じ）。コンテナの実行環境が無いときの扱いは Way of Working のとおり。\n- 画面からの一連の操作を確かめるテスト（E2E）は、代表的な流れ1〜2本に絞って入れる（まずは「ログイン → 管理画面に入れるか → ログアウト」）。道具は設計のステージで決める。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。不安定なテストは放置せず、原因を直すまで統合しない。\n- 認証・認可・監査に関わる機能では、次のテストを必ず書く。★印の項目は要件（しきい値や動作）が未確定のため、要件定義で決めてからテストを書く。\n  - アカウントロック: 失敗回数のしきい値の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロック中は正しいパスワードでも拒否、ログイン成功時の失敗回数の扱い。★しきい値、回数を数える期間、ロックの解除方法\n  - ログイン: 存在しないユーザーとパスワード誤りで、応答からユーザーIDの存在を推測できないこと\n  - トークン: 有効期限の境界（直前は有効／直後は無効）、署名の改ざん、署名方式の指定を悪用した改ざん（`alg: none` 等）、ログアウト後のリフレッシュトークンの拒否、ログアウト後もアクセストークンが有効期限まで使えること（決定済みの仕様として明示する）。★各トークンの有効期限の値、リフレッシュトークンを使うたびに作り直すか\n  - 初期管理者の自動作成: 2回目以降の起動で重複作成しない、設定が無い／不正なときの動作、パスワードがログに出ない。★設定が無いときに起動を止めるか\n  - 認可: 未認証（401）、管理者フラグなし（403）、管理者（200）をサーバー側のテストで確かめる（画面で管理メニューを隠すことはサーバー側の検査の代わりにしない）\n  - 監査ログ: 対象イベントごとに必須項目が記録されること、存在しないユーザーIDでのログイン失敗も記録されること。★監査ログの書き込みに失敗したときに操作を失敗させるか\n  - 秘密情報の漏えい: ログ・監査ログの出力にパスワード・トークンの値が含まれないこと\n  - 構造化ログ・分散トレース: 決めた形式で出る、トレースIDがログに含まれる、外部エクスポートが既定で無効であること\n  - 画面: ログイン画面のアクセシビリティ検査、ロック時のメッセージ表示、ログアウトでトークンが破棄されること\n- 利用者が投入する DSL（YAML）を読み込む機能では、Code Style の「信頼できない入力」の決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。上限の具体的な数値は設計の段で決め、決めた値の境界で確かめる。\n  - 大きさ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 入れ子の深さ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 別名（アンカー）: 展開の数が上限を超えると拒否され、別名の展開の爆発で処理が止まらない\n  - タグ: 任意の型を作るタグ（`!!` など）を含む DSL は拒否され、型が作られない\n  - 重複キー: 同じキーが重なる DSL はエラーになり、後の値で黙って上書きされない\n  - JSON Schema の `$ref`: 外部の URL を取りに行かない\n  - 拒否の応答: Problem Details の形で返り、YAML・JSON Schema の部品の例外のメッセージを含まない\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22)"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 (learned 2026-09-23) \n- 負荷の環境や配備先が決まらないと測れない目標（応答時間のパーセンタイル、運用の指標、ファイルの権限など）は、Build and Test で `Unverified` とし、持ち主の段（performance-validation・observability-setup・deployment-execution）を明記して引き継ぐ。目標を緩めて「満たした」ことにはしない。 (learned 2026-09-23) \n- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は perf/README.md。 (learned 2026-09-23) \n- 負荷の試験で、アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。 (learned 2026-09-23) \n- 同時の重なりを確実に作るため、本番のコードを変えずに、監査の書き込みの時間を測る LongSupplier（AuditEventListener で2本目を借りる直前に呼ばれる）をテストで差し替えて待ち合わせる方式にした。既存の LoginConcurrencyIT は 8 スレッドでプールの 10 に届かず、前回の失敗のログインで尽きなかった理由の1つと見られる。 (learned 2026-09-23) \n- Intent の流れに Performance Validation の段が無く、負荷の環境（使い捨ての環境）を手元で用意できるときは、k6 の試験と NMT の測定の持ち主を Build and Test とし、Unverified で引き継がずにその段で実行する。 (learned 2026-09-23) \n- 修正の前の設定（例: 上限 1g）も修正の後の環境（例: CPU 4 の VM）で流し（pre1g）、要件の前提（VM を上げても F3 が起きる）を実測で裏付ける。 (learned 2026-09-23) \n- パッケージごとのカバレッジの下限と SpotBugs の SQL_ の関門を U1 の計画に入れた。team.md の決まりだが今のビルドに無く、この Intent で最初に作る単位のため。U1 の設計の文書には無い作業。 (learned 2026-09-24) \n- U4 で既存の AuditSecretLeakIT の列の一覧に V6 の4列を足し、テストの JVM のヒープを 1g にした。前者は承認済みの V6 と必ず食い違うため、後者は構造の検査がクラスを持ち続け U3 の 10MB 超えのテストでヒープが尽きたため。どちらも計画に無い変更で、依頼者に確かめる。 (learned 2026-09-24) \n- 応答しない対象DB の TIMEOUT の確かめに、テストの中で開いた ServerSocket（受け付けて何も返さない）を使う。外の端末に頼らず確実に再現できる代わりに、本物の DB の遅延ではない。 (learned 2026-09-24) \n- パッケージごとのカバレッジの下限は新しいパッケージだけに当てた。実測で audit.service（行 77.2%）・common.health（行 79.2%）・auth.repository（分岐 50.0%）が単独で下回ったため（team.md の決まりどおり）。既存の 22 パッケージを一覧で外し、新しいパッケージは自動で対象になる。 (learned 2026-09-24)"
    }
  ],
  "obligations": {
    "strategy": "standard",
    "strategy_volume": [
      "Five to eight tests per component.",
      "Unit tests plus integration tests for key boundaries.",
      "Add E2E, performance, or security tests when requirements demand them."
    ],
    "scope_floor": [
      "Keep the existing test suite green.",
      "This scope adds no extra new-test floor beyond the selected test strategy."
    ],
    "combination_rule": "Apply every selected-strategy obligation and every scope-floor obligation; neither replaces the other, and a targeted scope regression may add the narrowest necessary test type beyond the strategy default."
  },
  "plan_profile": {
    "methodology": "test-after",
    "runner_step": "Verify the existing test runner/configuration and record the exact unit-scoped command.",
    "runner_ready_before_first_test": true,
    "testable_layers": [
      "Data model / database behavior",
      "Repository / data access",
      "Business logic",
      "API / endpoint",
      "Frontend behavior"
    ],
    "steps": [
      "Project structure and production configuration skeleton.",
      "Verify the existing test runner/configuration and record the exact unit-scoped command.",
      "Data model / database behavior - implement.",
      "Data model / database behavior - write and run its tests after implementation.",
      "Repository / data access - implement.",
      "Repository / data access - write and run its tests after implementation.",
      "Business logic - implement.",
      "Business logic - write and run its tests after implementation.",
      "API / endpoint - implement.",
      "API / endpoint - write and run its tests after implementation.",
      "Frontend behavior - implement.",
      "Frontend behavior - write and run its tests after implementation.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:246b734731604f4b9f4d77cecbeda1baf18157fdfe148f6c242e9cf2724ab8f9",
  "contract_sha256": "sha256:fad83f4781d495b9c1188364b74cbb3df645f2324fe0ecc57c74ab31a9426958"
}
```


Testing Contract の `plan_profile.steps` との対応: 骨組みと本番の設定は Step 1、データの形と DB の振る舞い（移行と保存）は Step 2・3、業務処理（要約・違い・照合、DslLifecycle、共通の拡張）は Step 4〜9、API は Step 10・11、環境とビルドの設定（構造の検査・ダッシュボード・検査）は Step 12〜14、B5 は同じ順で Step 15〜17。U4 は画面を持たない（画面は U5）。テストの実行の準備（`plan_profile` の2つ目）は既存の Gradle の設定を使い、`unit-test-instructions.md` にコマンドを記す。
