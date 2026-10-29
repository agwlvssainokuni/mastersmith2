## Developer Code Scan Results

Conversation language: 日本語。読み取り専用で実施した（Read・Grep・Glob と読み取りの git・`docker ps`/`docker images` の一覧のみ。ビルド・gradlew・コンテナの起動はしていない）。

### Scan Coverage
- **Analyzed deeply**:
  - backend/src/main/java/cherry/mastersmith/businessdata（DSL の選択肢・書き込みの規則・検索の演算子・定義の応答・ページ・検索の検証・文の実行者の境界）
  - backend/src/main/java/cherry/mastersmith/dsl（OptionSource・OptionSourceKind・FormPart・Lookup 系の型、DslModelMapper、DslSemanticValidator の選択肢の節）
  - backend/src/main/java/cherry/mastersmith/targetdb（ReadPurpose・TargetDbProperties の時間の上限・読み取りの口。署名レベルで確認）
  - backend/src/main/java/cherry/mastersmith/role（PermissionSnapshot の公開の口）
  - backend/src/main/resources（dsl/dsl-schema-v2.json の options の節）
  - frontend/src/features/tables（構成・BusinessDataFormPage の部品の選び方・SearchPanel・型の定義の頭）
  - frontend/src/shared（構成の一覧）
  - perf（README の見出しの構成・「性能・資源の目標の見直し」の節・scenarios.js の閾値と setup・versions.env）
  - compose.yaml、docker/perf/compose.yaml（image 行）
  - .github（dependabot.yml の全文、ci.yml の段の名前）
  - frontend/package.json、backend/build.gradle.kts（packagesJudgedByTotal）
  - README.md（Mailpit・E2E に関わる記述の検索）
- **Skimmed only**:
  - docker（targetdb の SQL の外部キーの検索、monitoring・otel-collector・jmx は未読）
  - gradle/libs.versions.toml、config（ファイルの存在の確認のみ）
  - backend/src/test（businessdata のテストの一覧と BusinessDataBoundaryArchitectureTest の許可の一覧、REFERENCE・LOOKUP を含むファイル名の検索のみ。テストの本体は未読）
  - frontend/src/features/tables の各 test、frontend/e2e（名前の検索のみ）
  - frontend/package-lock.json（i18next・react-i18next の版のみ）
  - vendor/make-you-chic-ui（固定先 ecb07e9 の確認のみ。使える部品の一覧は読んでいない）
  - perf/README.md の本文の大半（約 1,360 行のうち、見出し・JWT 関連・1322 行以降のみ）、scenarios.js の大半（約 2,600 行のうち閾値・setup・トークンの箇所のみ）
- **注記**: 範囲は Focused scan。`analyzed.paths` に載せてよいのは「Analyzed deeply」の内側だけ。

### Packages Found
- cherry.mastersmith.businessdata — Java パッケージ（web・service・domain・repository の4層） — Java — 業務データの定義・一覧・詳細・作成・更新・削除。SQL を組み立てて実行するのは `repository/RowSqlExecutor` 1クラス
- cherry.mastersmith.dsl — Java パッケージ（parse・validate・service・domain） — Java — DSL（YAML）の読み込み・JSON Schema 検証・意味の検証・モデル（`OptionSource` など）
- cherry.mastersmith.targetdb — Java パッケージ — Java — 対象DB（MySQL・MariaDB・PostgreSQL）の読み取り・書き込みの接続の貸し出しと時間の上限
- cherry.mastersmith.role — Java パッケージ — Java — ロール・権限の解決（`PermissionSnapshot`・`EffectivePermissionResolver`）
- frontend/src/features/tables — 画面の機能 — TypeScript/React — 業務データの一覧・詳細・作成・更新の画面（約 5,300 行のうちテスト含む）
- frontend/src/shared — 共有部品 — TypeScript/React — api-client・paging・page-size・scroll-region・tree・unsaved-guard・validation など
- perf — 負荷試験の道具 — JS（k6）・shell・Node — 台本 `perf/k6/scenarios.js`、手順書 `perf/README.md`、固定の版 `perf/versions.env`

### Build System
- **Type**: Gradle（Kotlin DSL）。backend は Spring Boot、frontend は npm（Vite）。WAR に `dist` を同梱。`./gradlew verify` が入口
- **Config Files**: build.gradle.kts、backend/build.gradle.kts、settings.gradle.kts、gradle/libs.versions.toml（未読）、backend/gradle.lockfile、frontend/package.json・package-lock.json、compose.yaml、docker/perf/compose.yaml、perf/versions.env、.github/workflows/ci.yml、.github/dependabot.yml、.gitleaks.toml、config/
- **Build Dependencies**: frontend のビルド結果 → backend の WAR。vendor/make-you-chic-ui は npm の `file:` 依存、vendor/java-mustache-processor は Gradle の composite build

### APIs Discovered
- REST（業務データ、すべて `/api/tables` の下） — businessdata/web/BusinessDataReadController・BusinessDataWriteController — 6本: 読み取り3（`POST /definition`、`POST /rows/search`、`POST /rows/get`）、書き込み3（`POST /rows`＝作成、`PATCH /rows`＝更新、`POST /rows/delete`）。行の値・主キーは道に載せず本文で運ぶ（BusinessDataPaths で確認）
- 参照先の行を読む専用の API は **無い**（検索 API の対象は1つのテーブル。`definition` の応答は選択肢を FIXED のものだけ `choices` に載せる）

### Frameworks & Libraries
- i18next — package.json は `^26.5.5`、lockfile は 26.5.10 — 画面の文言の切り替え。Dependabot #81 は 26.5.10 → 26.5.11
- react-i18next — package.json は `^17.1.2`、lockfile は 17.1.8 — React 連携。Dependabot #82 は 17.1.8 → 17.1.9
- Spring Boot 4.1.x、Jackson 3.1.x（BOM の上書き）、networknt json-schema-validator 3.0.6、SnakeYAML、HikariCP — backend（版の詳細は build.gradle.kts 側。今回は未確認）
- Vitest 5 ＋ @vitest/coverage-v8、Testing Library、vitest-axe、fast-check、Playwright（E2E）— frontend
- k6（`grafana/k6:2.3.0@sha256:…` を perf/versions.env に固定）— 負荷試験

### Test Coverage
- **Test Directories**: backend/src/test/java（`XxxTest`・`XxxIT`）、backend/src/test/resources、frontend/src の `*.test.ts(x)`、frontend/e2e、perf の `*-test.sh`
- **Test Frameworks**: JUnit 5・ArchUnit・jqwik・Testcontainers（backend）、Vitest・Testing Library・fast-check・vitest-axe（frontend）、Playwright（E2E）
- **Coverage Config**: 有り。JaCoCo（行 80%・分岐 70%、全体＋パッケージごと）、`@vitest/coverage-v8`。`packagesJudgedByTotal`（backend/build.gradle.kts 213〜221 行）は現在 7 パッケージ: `access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`（team.md の記載と一致）。`businessdata`・`dsl`・`role`・`targetdb` はこの一覧に無く、パッケージごとの下限の対象

### Code Quality Indicators
- **Linting**: Spotless（palantir-java-format）、SpotBugs＋FindSecBugs（`backend/config/spotbugs-exclude.xml`。`RowSqlExecutor` の `search`・`detail`・`precheckUnique`・`insert`・`update` ほかを `SQL_` の誤検知として個別に除外）、oxlint・ESLint・Prettier・Stylelint（frontend）、Gitleaks、OSV-Scanner
- **CI/CD**: .github/workflows/ci.yml（`./gradlew verify` ＋ WAR の保存＋リリース）、.github/dependabot.yml（gradle・npm・github-actions・docker・docker-compose）
- **Documentation**: README.md、perf/README.md（約 1,360 行、Intent ごとの節が積み重なった構成）。コードのコメントは日本語の Javadoc が厚い

### Technical Debt Signals
- perf/README.md は Intent ごとの節を足し続けた構成で、1322 行以降の「性能・資源の目標の見直し」が今の線、それより前の節（例: 業務データの場面 1133〜1286 行）に前の線（1000 ms など）の文が残る。節の中で「前の線」と「今の線」が混在し、基準を読み取りにくい（事実）。依頼文の「どの値が基準か読み取りやすい構成に整理」に対応する箇所
- 閾値は `perf/k6/scenarios.js` の `thresholdsFor`（`BIZ_P95_LIMIT_MS` ほか）と `perf/k6/thresholds-test.sh` の期待の表、`perf/dsl-mixed-memory.sh` の `judge`/`judge_090`、README の表の4か所に分かれる（事実）。同じ値を4か所に持つ
- 同じイメージの固定先が分散: `compose.yaml`、`docker/perf/compose.yaml`（Mailpit・対象DB。lgtm は無い）、`README.md`（Mailpit）、`perf/README.md`（lgtm: 433 行ほか）、`backend/.../TargetDbImages.java`、`perf/versions.env`（k6・JRE）、`docker/hikari-pool.sh`（jdk）。一覧は `.github/dependabot.yml` のコメントにある
- `ColumnWriteRulesFactory` は `formPart: lookup` を `text` に落とし、REFERENCE・LOOKUP の選択肢を検証から外している（BR3.7）。この Intent で本実装に置き換える対象（前の Intent の意図的な暫定）

## Handoff Summary
- **Intent-relevant finding**:
  1. **REFERENCE・LOOKUP は DSL までは完成、業務データでは「文字の入力」扱い**。
     - JSON Schema（`backend/src/main/resources/dsl/dsl-schema-v2.json` 271〜335 行）: `options.source` は FIXED／REFERENCE／LOOKUP。REFERENCE は `table`・`valueColumn`・`labelColumn` 必須、LOOKUP は `table`・`valueColumn`・`lookupSearch`（column＋operator）・`lookupList`（column＋order）必須。formPart には `lookup` がある（160 行）。
     - モデル: `dsl/domain/OptionSource`（コンストラクターで種類ごとの必須を検査）、`LookupSearchItem`、`LookupListItem`、`OptionSourceKind`、`FormPart.LOOKUP`。`DslModelMapper`（160〜180 行）が写す。
     - 意味の検証: `DslSemanticValidator` 230〜290 行・375〜380 行。`select`・`radio` は FIXED か REFERENCE、`lookup` は LOOKUP のみ。参照先の table・valueColumn・labelColumn・lookup の column が DSL に実在するかを `tables.get(name)` で照合する（`tables` はスキーマ内のため、**参照先は同じスキーマの中**）。
     - 既定の DSL の生成: `dslmanage/generate/DslTreeBuilder.reference()`（241〜250 行）が1カラムの外部キーから `source: REFERENCE`・valueColumn と labelColumn が同じカラム、を出す。LOOKUP は生成しない（手書きの DSL のみ）。
     - 業務データ側: `businessdata/domain/ColumnWriteRulesFactory` 48 行で `FormPart.LOOKUP` を `TEXT` に置換、`choices()`（106〜113 行）は FIXED の select・radio だけ選択肢を返し REFERENCE・LOOKUP は空。`ColumnWriteRules` 80 行は `LOOKUP` を渡すと例外。`SearchOperatorPolicy` は REFERENCE・LOOKUP の CHOICE／IN を EQUALS に読み替える（冒頭 Javadoc）。定義の応答（`ColumnDefinitionResponse`・`OptionResponse`）には REFERENCE・LOOKUP の情報（table・labelColumn・lookupSearch/List）を載せる欄が **無い**。画面は `choices` と `formPart` だけで部品を選ぶ（`BusinessDataFormPage.tsx` 780〜870 行）。
     - 外部キー違反は `RowWriteFailureReason.REFERENCE_NOT_FOUND`→`WriteRejection.REFERENCE_NOT_FOUND`→フィールドの誤り（`BusinessDataWriteService` 245〜292 行）で既に返す。
  2. **参照先の行を読む経路の流用点**: 権限は `PermissionSnapshot.main(schema, table, column)`（132 行）で参照先のテーブル・カラムごとに引ける。`TableAccessResolver` 94〜170 行が1テーブルぶんの `TableAccessInput`→`TableAccessPolicy.evaluate` で `TableAccessView` を作る。SQL は `RowSqlExecutor.search/detail`（78・148 行）が `TableAccessView` に対して組む（文の作成・実行を許すのは `RowSqlExecutor` と `targetdb.repository` ほか数クラスだけ、`BusinessDataBoundaryArchitectureTest` の `ALLOWED_STATEMENT_OWNERS`）。ページは `PageRequest`（サイズ 20・50・100、既定 20、最大ページ 1,000,000）、検索値は 1,000 文字まで（`SearchRequestValidator.MAX_VALUE_LENGTH`）。時間の上限は `ReadPurpose`（LIST・DETAIL とも既定 5 秒、`TargetDbProperties.QueryTimeout`）。**未検証の仮説**: 参照先の選択肢・ピッカーの一覧は、`TableAccessResolver` に参照先のテーブル名を渡して既存の `search` を再利用できる見込み（新しい API 1〜2本）。ただし、選択肢の読み取りに使う ReadPurpose の新設要否、参照先の権限が NONE のときの扱い（選択肢を出さない・値だけ出す）、件数の上限は設計の判断で、コードからは決まらない。
  3. **画面の流用点**: `frontend/src/features/tables` に `SearchPanel`（EQUALS・CONTAINS・RANGE・CHOICE の入力）、`BusinessDataTable`、`BusinessDataFormPage`（部品選択は formPart 分岐）、`useRowSearch`、`businessDataStore`、`listPaging`・`searchConditions`。共有部品は `src/shared/{paging,page-size,scroll-region,modal,tree,unsaved-guard,validation}`。参照ピッカー用の Modal（`shared/modal`）・表（`BusinessDataTable`）・検索（`SearchPanel`）は形が近く流用候補（未検証の仮説）。team.md により make-you-chic-ui に無い部品は先に上流へ依頼する（固定先は ecb07e9）。
  4. **i18next 系の影響**: 使い方は `useTranslation`（21 ファイル）、`I18nextProvider`（`app/i18n/I18nProvider.tsx`）、`i18next` 本体の import は `app/i18n/i18n.ts` の1か所のみ。#81 は i18next の patch（26.5.10→26.5.11）、#82 は react-i18next の patch（17.1.8→17.1.9）。API の使い方が狭く、影響は小さいと見込める（未検証。リリースノートは読んでいない）。team.md の決まりでは PR をマージせず、手元で package.json／lockfile を更新して `./gradlew verify` を通す。`.github/dependabot.yml` の npm の `ignore` は typescript の major のみで、この2件を妨げない。
  5. **perf の JWT**: k6 の `--summary-export` の JSON に、台本の `setup()` が返すデータ（`bizTokens` ほか）が `setup_data` として出る（実物のキー構造を前の Intent の証拠 `…/261027-perf-targets-review/construction/build-and-test/evidence/biz-mysql-bizSearchLast-r1.json` 177〜195 行で確認。値は `[REDACTED-JWT]` に置換済み）。k6 の呼び出しは perf/README.md に約 15 か所（50・179・248・333・358・452・574・595・659・762・791・1042・1110・1254 行）と `perf/dsl-mixed-memory.sh` 312 行で、出力先は `build/perf-results`（git 無視）→手で証拠のディレクトリへ複写する流れ。**既存の自動の伏せ字の仕組みは perf にも README にも無い**（`REDACTED` の文字列検索は README・scenarios.js・perf の shell で 0 件）。決まりは project.md の記述のみ（手で置換）。自動化の置き場の候補は、k6 実行の直後に通す小さな後処理スクリプト（例 `perf/lib/` の下）か、台本の `handleSummary`（現在 `handleSummary` は未使用）。k6 2.x の `--summary-export` が `handleSummary` で上書き可能かは **未検証**。
  6. **コンテナの版**: compose.yaml の otel-lgtm は `grafana/otel-lgtm:0.38.0@sha256:75cdf892…`（83 行）、Mailpit は `axllent/mailpit:v1.32.0@sha256:0790692b…`（121 行）。`docker ps -a` の事実: Mailpit は `axllent/mailpit:v1.32.0` で稼働中（compose と同じ版。ダイジェストは未確認）、lgtm コンテナ `mastersmith-lgtm-1` は **`grafana/otel-lgtm:0.38.0` で停止中**（Exited 45 時間前）、ローカルのイメージ一覧には `grafana/otel-lgtm:0.34.0` も残る。依頼文の「動いているコンテナを compose.yaml の版にそろえる」は、実際のずれは今は見えない（版のタグは一致）。**ダイジェストの一致は `docker inspect` 未実施のため未検証**。ほかに compose.yaml にある固定先: otel-collector 0.163.2、postgres 18.6、mysql 26.10.0、mariadb 13.0.2（`docker/perf/compose.yaml` の postgres・mysql・mariadb・mailpit は compose.yaml と同じ版・ダイジェスト。lgtm と collector は無い）。
  7. **境界テスト・関門**: `BusinessDataBoundaryArchitectureTest` の `allowedDependencies` は businessdata が依存してよい先を `targetdb.service/domain`・`role.service/domain`・`dsl.domain/service`・`common` などに限定。参照先の読み取りを businessdata の内側に置く限り緩める必要は無い見込み。`targetdb` に手を入れると `TargetDbBoundaryArchitectureTest`、`role` に手を入れると `RoleBoundaryArchitectureTest` の許可の一覧に触れうる（緩める場合は計画に明記して依頼者の承認が必要）。SpotBugs の `SQL_` 除外は `RowSqlExecutor` のメソッド単位で個別に書かれており、新しいメソッドで SQL を組むと除外を足す（計画に明記して承認）か、既存の `search`／`detail` を再利用する必要がある。
- **Risks / follow-up**:
  - 外部へ行う操作・データ: JWT を含む `setup_data` は k6 の `--summary-export` のほかに k6 の標準出力・ログ（`$R/$TAG-k6.log` の形で保存する箇所がある）にも出うるかは **未検証**。伏せ字の対象ファイルの範囲を設計で確かめる必要がある。
  - project.md の Forbidden（業務データの行の値・主キーの値をログ・トレース・エラー応答に含めない、SQL に利用者の入力を文字列で組み込まない）は参照先の選択肢・ピッカーにも適用される。ピッカーの検索値・参照先の値を本文で運ぶ現行の方針（`/api/tables` は GET で値を道に載せない）を維持する必要がある。
  - カラムの主権限が NONE のものは応答に含めない（Mandated）。参照先のテーブル／カラムの権限が NONE のときの選択肢・ラベルの扱いが新しい論点。
  - `packagesJudgedByTotal` の 7 パッケージには businessdata・dsl・role・targetdb は含まれないため、この Intent で手を入れても一覧は増減しない見込み（`common.web` などに触れる場合は team.md の外す義務が発生）。
  - team.md により E2E は代表の流れ1本まで、変更が frontend 本体・businessdata に及ぶブランチは統合前に E2E を流す。
  - 上記の「未検証」と書いた項目（ピッカーの経路の再利用、`handleSummary`、コンテナのダイジェスト、i18n 2件のリリースノート）は要件定義・設計の段で確かめること。
