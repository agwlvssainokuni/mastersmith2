# 結合テストの手順 — Build and Test（Intent 261029-reference-lookup-cleanup）

Test Strategy は Standard。この手順書は、単位の間の受け渡し（U1 の読み取りの口 → U2 の保存前の確かめ → U3 の画面、U4 の道具 → `verify` の記録の検査）と、外部の依存（3種類の対象DB・内部DB の H2・Mailpit・見本の PG）との境界を確かめる結合テストと E2E を扱う。単位ごとの単体テストは各単位の `code-generation/unit-test-instructions.md` にある。ビルドの前提は `build-instructions.md`。

## 1. 道具と置き場

| 種類 | 道具 | 置き場 | 名前の決まり |
|---|---|---|---|
| バックエンドの結合テスト | JUnit 5・Spring Boot Test・Testcontainers（MySQL・MariaDB・PostgreSQL の固定のダイジェスト） | `backend/src/test/java/cherry/mastersmith/businessdata/web/` | `XxxIT`（`:backend:integrationTest`） |
| 性質ベースのテスト | jqwik（Java）・fast-check（画面） | 各パッケージ | 乱数の種を失敗時に出す |
| 画面の部品と受け渡し | Vitest・Testing Library・user-event・vitest-axe（jsdom。U4 は node） | `frontend/src/features/tables/`・`frontend/src/` の直下（U4） | `*.test.ts(x)` |
| 画面からの一連の操作（E2E） | Playwright（実際の Chromium） | `frontend/e2e/` | `NNN-*.e2e.ts`（`./gradlew e2eTest`） |

内部DB は本番と同じ組み込みの H2 を使い、コンテナにしない（`team.md`）。対象DB に書くテストは、テストのクラスごとに名前の重ならないスキーマ（MySQL・MariaDB はデータベース）を作って消す。書いた後の値はアプリとは別の接続で読み直す。

## 2. 単位の間の境界と、確かめるテスト

| 境界 | 確かめること | テスト |
|---|---|---|
| U1 の参照の読み取りの API（定義の `purpose`・`POST /api/tables/reference/rows`）と3種類の対象DB | 件数 199・200・201、名前の大文字小文字・引用符、NULL の並び、境界の値、`%`・`_` のエスケープ、参照先の形（複合の主キー・主キー無し・ビュー・自己参照） | `PostgresReferenceReadIT`・`MysqlReferenceReadIT`・`MariadbReferenceReadIT`（`AbstractReferenceReadIT`） |
| U1 の API の認可と同じ応答 | 401・要る権限だけを欠く利用者の 403（表のパラメーター）・200、DSL に無い名前の拒否、判定の順 | `ReferenceAuthorizationApiIT`・`ReferenceSameResponseIT`・`ReferenceRequestLimitIT` |
| U1 と2つのプール（内部DB・対象DB の読み取り） | 1要求で対象DB の読み取り 1 本まで、内部DB を返してから借りる | `ReferenceConnectionUsageIT` |
| U1 の時間の上限と失敗の分け方 | 5 秒・全体 10 秒、TIMEOUT と UNAVAILABLE、壊れた接続を使い続けない | `ReferenceTimeoutIT`・`ReferenceReadDeadlineTest` |
| U1 → U2（保存前の確かめの口 `check`・`countState`） | 参照先に無い値・権限の無い値・差し替え（IDOR）の 409 `BUSINESS_REFERENCE_NOT_FOUND`、状態が変わらない | `PostgresReferenceWriteIT`・`MysqlReferenceWriteIT`・`MariadbReferenceWriteIT`（`AbstractReferenceWriteIT`）・`ReferenceWriteAuthorizationApiIT` |
| U2 と3つのプール（内部DB・読み取り・書き込み） | 読み取りと書き込みを組で数える。内部DB は同時 1 本まで・応答の後に返す（この段で MariaDB と内部DB の借りた回数を足した。U2 R-02） | `ReferenceWriteConnectionUsageIT` |
| U2 の時間切れ | 応答しない相手は 503 `UNAVAILABLE`（保存されていない）。設計の T-T1 の TIMEOUT との差は U2 の `code-summary.md` 4節で読み替え済み（U1 R-01） | `ReferenceWriteTimeoutIT` |
| U2 と監査 | 業務の理由の拒否が監査に残り、値が入らない | `ReferenceWriteAuditIT` |
| 漏えい（U1・U2） | 行の値・ラベル・主キー・接続情報が TRACE のログ・トレースの属性・エラー応答に出ない | `ReferenceSecretLeakIT`・`ReferenceWriteSecretLeakIT`・`ReferenceTraceExposureIT`・`ReferenceWriteTraceExposureIT` |
| 境界テスト | `prepareStatement`・`createStatement` の許可の場所、SpotBugs の除外、既存の境界テストが緩んでいない | `BusinessDataBoundaryArchitectureTest` ほか 19 個の `*ArchitectureTest`（前の main からの変更 0） |
| U1・U2 → U3（定義の応答・誤りの応答と画面） | 受け渡しの写し、項目の近くの誤り（`aria-describedby`）、一覧・詳細に入力不可の見た目が漏れない（AC3.4.5） | `BusinessDataFormPage.test.tsx`・`ReferenceSelectField.test.tsx`・`LookupField.test.tsx`・`LookupPickerDialog.test.tsx`・`BusinessDataListPage.test.tsx`・`BusinessDataDetailPage.test.tsx`（jsdom） |
| U3 と本物のサーバー（実際のブラウザ） | 参照ピッカーの代表の流れ、axe（12 の状態 × 20 組）、既存の業務データの流れ | E2E `190-reference-picker-flow`・`195-reference-picker-accessibility`・`180-business-data-flow`（実際の Chromium） |
| U4 の道具 → `verify` | 記録（`evidence/`）の JWT の形の検査が `verify` と CI で流れる | `perfEvidenceRedactor.test.ts`・`perfEvidenceRedactorCli.test.ts`・`perfEvidenceScan.test.ts`（node）、`perfEvidenceScan` のタスク |

## 3. 流し方

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock

# 全体（件数とカバレッジを報告するときはこの形）
caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify

# 結合テストだけを選ぶ（途中の確かめ）
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.businessdata.web.Reference*'

# E2E（Mailpit と、E2E の口の上乗せ付きの見本の PG を先に起動する。手順は README.md）
docker compose --profile mail up -d mailpit
docker compose -f compose.yaml -f docker/e2e/compose.targetdb-port.yaml --profile targetdb-postgres up -d targetdb-postgres
caffeinate -dims ./gradlew e2eTest
docker compose --profile targetdb-postgres up -d targetdb-postgres   # 流し終えたら上乗せ無しで作り直して口を閉じる
```

E2E の後に、報告の json（`frontend/test-results/e2e-results.json`）を文字列で検索し、資格情報・接続先・参照の値が入っていないことを確かめる（資格情報の環境変数は名前と種類だけが残り、値の項目が無いこと）。

## 4. カバレッジの目標

- バックエンド: 全体 行 80%・分岐 70% 以上、`packagesJudgedByTotal` の外のパッケージごとに同じ下限（`businessdata.*`・`targetdb.*` を含む）。
- 画面: 全体 行 80%・分岐 70% 以上（`thresholds`）。新しい機能のフォルダ `src/features/tables/`（`references/` を含む）の値を記録する。
- 除外を増やして下限を満たすことはしない（`team.md`）。

## 5. テストのデータ

- 対象DB の行は架空の値だけ。メールアドレスは `example.com` などの予約のドメインだけ（`team.md`）。
- E2E の流れは、前の流れが作った状態に頼らず、自分で作った利用者・ロール・専用のスキーマの行だけを使い、見本の行と初期管理者を変えない。
- 書き込みのテストは、テストのクラスごとのスキーマを作って消し、実行ごとに一意の名前を入れる。
