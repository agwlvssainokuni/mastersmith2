# Infrastructure Design の質問 — u4-admin-forbidden-ui（管理の画面の 403 の共通の扱い、ui）

U4 は、管理の API の 403（`ACCESS_DENIED`）を受けた画面を S6（権限が無いときの表示）に置き換え、ログインの状態を1回読み直す、画面の側だけの仕組みです。自分の API・内部DB の表・サーバーの状態・設定を持たず、`backend/` に手を入れません。ui の単位のため、この段の成果物は `infrastructure-specification.md`・`monitoring-design.md`・`cicd-pipeline.md`・`traceability.json` です。

配備先は開発者の PC 上のコンテナだけで、クラウドの基盤は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。U4 の基盤（配備・配信・CSP・監視・検査の流れ・実際のブラウザの検査 130）は、承認済みの NFR 要件・NFR 設計と既存の仕組みで決まっており、判断の分かれる論点はありません。そのため、この文書は質問を置かず（0問）、決まっていることと、要約として確かめる設計の要点だけを書きます（`project.md` の Way of Working「単位に新しく決める論点が無いときは、質問を作らず要約として確かめる」）。

読んだ上流（どれも `aidlc/spaces/default/intents/260930-user-admin/` の下）:

- この単位の承認済みの NFR 設計 `construction/u4-admin-forbidden-ui/nfr-design/`（`performance-design.md` 4節〜7節、`security-design.md` 5節・6節・8節・9節と終わりの「承認の場の決定」の節、`logical-components.md` 6節〜8節、`nfr-design-questions.md` の Q1 A・Q2 B・Q3 A）
- この単位の承認済みの NFR 要件 `construction/u4-admin-forbidden-ui/nfr-requirements/`（`security-requirements.md` の NFR3.2 と「承認の場の決定」の節、`tech-stack-decisions.md` の NFR7.3、`performance-requirements.md`）
- この単位の承認済みの機能設計 `construction/u4-admin-forbidden-ui/functional-design/`（`functional-spec.md` の「承認の場の決定」の節、`frontend-components.md` の 7節・8節）
- 部品の一覧 `inception/domain-design/components.md`、契約 `inception/contract-design/contract-summary.md`（C4）、Bolt の計画 `inception/delivery-planning/bolt-plan.md`（B2 は U2 と U4、画面・認証に関わる Bolt は統合の前に E2E を手元で流す）
- 同じ Intent の U5 の承認済みの NFR 設計 `construction/u5-user-admin-ui/nfr-design/security-design.md` 3.2・`logical-components.md` 9節（B5 で `frontend/playwright.config.ts` の html の報告を外し、trace の既定を `off` にする。130 にも効く）
- 決まり `aidlc/spaces/default/memory/team.md`（Way of Working・Testing Posture・Deployment）・`project.md`
- 既存の仕組み（読むだけ）: `frontend/playwright.config.ts`・`frontend/e2e/`（010〜100 と `support/`）・`build.gradle.kts`（`verify`・`e2eTest` と Mailpit の確かめ）・`.github/workflows/ci.yml`（`./gradlew verify`）・`docker/monitoring/provisioning/alerting/mastersmith.yaml`・`docker/monitoring/dashboards/mastersmith-overview.json`・`backend/src/main/java/cherry/mastersmith/access/web/AdminAccessDeniedHandler.java`・`README.md` の E2E の節

## 決まっていること（質問にしない）

| 決まっていること | 根拠 |
|---|---|
| 配備の形は変えない。変わるのは実行可能 WAR に同梱する `dist` の中身だけで、`Dockerfile`・`compose.yaml`・`.env` の項目・資源の上限・ヘルスチェックは変わらない | `nfr-design/performance-design.md` 7節、`nfr-design/logical-components.md` 8節、`team.md` の Deployment |
| CSP（`backend/src/main/resources/application.yaml`）を変えない。130 のためにも緩めない。外部の資源・外部への通信・埋め込みのスクリプトを足さない | `nfr-design/security-design.md` 6節 |
| 新しい依存（実行時・開発時）を足さず、make-you-chic-ui の固定先も変えない。`frontend/package.json`・`frontend/package-lock.json` に差分を作らない | `nfr-design/security-design.md` 9節の申し送り、`nfr-design/performance-design.md` 5節 |
| スキーマの変更・サーバーの状態の変更が無いため、戻しは直前の版のイメージへ戻すだけで、内部DB のバックアップと戻しの練習の論点は無い | `nfr-design/logical-components.md` 8節、`team.md` の Deployment |
| 画面の側に、独自の指標・ログの送り先・画面の時間の計測と送信を足さない | `nfr-design/performance-design.md` 4節・6節 |
| 画面の 403 は、サーバー側の既存の仕組みで見える（`http.server.requests` の `status="403"`、警報 `ms-forbidden`「1 時間に 20 件を超えた」、ダッシュボードの「403 の件数（1 時間）」、`AdminAccessDeniedHandler` の WARN のログ `code=ACCESS_DENIED`、監査の拒否の記録）。403 の後の読み直しは既存のトークンの更新の API で、警報 `ms-refresh-p95` とダッシュボードのパネルで見える。U4 のために警報・パネルを足さない | 既存の `docker/monitoring/` と `AdminAccessDeniedHandler.java`、`nfr-design/performance-design.md` 6節 |
| 管理者でない利用者が管理の画面の URL を開いたとき（ForbiddenByRoute）は API を呼ばないため、サーバーには何も残らない。受け入れ済みの残る危険（R3・R4）として扱い、観測は足さない | `nfr-design/security-design.md` 8節 |
| `./gradlew verify` と CI（`.github/workflows/ci.yml`）は変えない。U4 の部品のテスト・性質ベースのテスト・カバレッジの下限（行 80%・分岐 70%）・初回の JavaScript の大きさの警告（`frontendBundleSize`）は既存の `verify` の中で動く。`backend/` に差分が無いことを記録する | `nfr-design/logical-components.md` 7節、`project.md` の Way of Working |
| 実際のブラウザの検査は `frontend/e2e/130-admin-forbidden-accessibility.e2e.ts` に置き、`./gradlew e2eTest` の中で動かす（`verify` と CI の外）。`workers: 1` のまま番号の順で 010〜100（B5 の後は 110・120 も）と同じ WAR・内部DB・初期管理者を共有する。既存の 20 組（`support/displayCombos.ts`）を使い、流れの E2E の本数には数えない | `nfr-design/logical-components.md` 6節、`team.md` の Testing Posture、`project.md` の学び（アクセシビリティの検査は本数に数えない） |
| 実行の時点は、B2 の統合の前（画面・認証に関わる変更）とリリースの前に、手元で `./gradlew e2eTest` を流し、010〜100 と 130 が通ることを記録する。`e2eTest` は始める前に Mailpit に届くかを確かめるため、130 はメールを使わないが、実行の前に Mailpit の起動が要る。長い実行は `caffeinate -i` で台本の全体を包む | `bolt-plan.md` の B2、`build.gradle.kts` の `e2eTest`、`project.md` の学び（caffeinate） |
| `frontend/playwright.config.ts` は U4 では変えない。資格情報は既にプロセスの環境変数で渡しており（`webServer.env` に置かない）、json の報告（`test-results/e2e-results.json`）と `playwright-secret-check-reporter.ts` も既にある。130 はこの形をそのまま使う | 既存の `frontend/playwright.config.ts`、`nfr-design/security-design.md` 5節 |
| B2 の時点では html の報告と失敗のときの trace（`retain-on-failure`）が残り、ログインの手伝いが入れる仮の資格情報が載りうる。これは 010〜100 と同じ既存の状態で、README の「共有しない」の決まりで扱う。B5 で U5 が設定の1か所を直し（html を外し、trace の既定を `off`）、130 にも効く。U4 は先取りしない | `README.md` の E2E の節、`construction/u5-user-admin-ui/nfr-design/security-design.md` 3.2 |
| 共有の手伝いで変えるのは `support/loginPreferences.ts` の型に省略できる `displayName` を1つ足すことだけ。`support/pageProblems.ts` の既定は変えない。130 は状態を変える要求を送らず、初期管理者の状態を変えない | `nfr-design/logical-components.md` 6.2・6.3、`nfr-design/security-design.md` 5節 |
| README の E2E の表に 130 の行と「130 について」の節（流れに数えない、差し替えは `GET /api/admin/check` の1本だけ、時間は測らない、130 だけを流すときも Mailpit の起動が要る）を足す。書くのはコード生成 | 既存の README の E2E の節の形（050〜100） |
| 承認の場の申し送り（130 の `waitForResponse` の約束を操作の前に作る順 R-01、「ホームへ戻る」の確かめの対象の組と 20 組の実行時間 R-02）はコード生成の計画で扱い、この段では決めない | `nfr-design/security-design.md` の「承認の場の決定」の節 |

## 質問

この単位には、判断の分かれる論点がありません（0問）。

## Infrastructure Design の要点（要約として確かめる）

1. **配備と配信（`infrastructure-specification.md`）**: 既存の WAR・イメージ・`compose.yaml` の `app` のまま。U4 で変わるのは同梱の `dist` の中身だけで、CSP・キャッシュの扱い・資源の上限・`.env` は変えない。戻しは直前の版のイメージへ戻すだけ。
2. **共有するもの**: ApiClient の更新のまとめ・ログイン状態・表示の設定のストア・骨組みの文言・E2E の共有の手伝い（`loginPreferences.ts` の型に省略できる項目を1つ）を、`logical-components.md` 8節の表のとおり記録する。新しいサーバーの資源は無い。
3. **監視（`monitoring-design.md`）**: 画面の側には何も足さない。画面の 403 は既存の 403 の件数・警報 `ms-forbidden`・WARN のログ・監査の拒否の記録で、403 の後の読み直しは既存のトークンの更新の指標と警報 `ms-refresh-p95` で見る。ForbiddenByRoute はサーバーに残らないことを受け入れ済みの危険として書く。SLO は置かない（画面の時間の目標を置かない NFR9.3）。
4. **検査の流れ（`cicd-pipeline.md`）**: `./gradlew verify` と CI は変えない（U4 の部品のテスト・カバレッジ・大きさの警告は既存の段で動く）。130 は `./gradlew e2eTest` の中で `verify` と CI の外に置き、B2 の統合の前とリリースの前に手元で流す（Mailpit の起動が前提）。
5. **E2E の設定と報告**: `playwright.config.ts` は U4 では変えない。資格情報は環境変数、json の報告の確かめは既存の報告の部品が受け持つ。html と trace に仮の資格情報が載りうるのは B5 までの既存の状態で、共有しない決まりで扱い、B5 の U5 の直しが 130 にも効くことを記録する。
6. **記録すること**: コード生成と Build and Test で、`backend/`・`application.yaml`・`vendor/make-you-chic-ui`・`frontend/package.json`・`frontend/package-lock.json` に差分が無いこと、`./gradlew e2eTest` で 010〜100 と 130 が通ったこと、報告の部品が通り2語の氏名の値が json の報告に含まれないこと（値は表示しない形で数える）、初回の JavaScript の大きさの前後の値。
7. **traceability**: 基盤に関わる NFR（NFR3.2・NFR7.3・NFR9.4・NFR9.5・NFR9.8・NFR9.10 など）を、3つの成果物の節に結ぶ。新しい枝番は足さない。

---

## Consolidated Summary Confirmation

答えのまとめ:

- 質問は 0 問。要点は上の 1〜7 のとおり（配備と配信は変えない、画面の側に監視を足さず既存の 403 と更新の指標・警報で見る、`verify` と CI は変えない、130 は `e2eTest` の中で `verify` と CI の外、`playwright.config.ts` は U4 では変えず B5 の U5 の直しが 130 にも効く、差分が無いことと E2E の結果を記録する）

Does this all look correct before I generate the artifact?

- Looks correct — 要点のとおりに成果物を作る
- Request changes — 直したい点を書いてください

[Answer]: Looks correct
