# API（mastersmith2）

> 2026-11-11（記録のコミット `7f36d8a`）の Intent `261110-deps-forks-review` の Focused scan でも HTTP の口は読み直していない（`common/health/` の本体だけ読んだ）。「ビルドと検査の口」の節に、CI の `-PitForks`・`resolveAndLockAll`・ヘルスチェックの確かめの口の表を足した。
>
> 前回の記録: 2026-11-09（記録のコミット `4fe8e79`）の Intent `261108-ci-checks-release` の Focused scan でも HTTP の口は読み直していない。「ビルドと検査の口」の節に、前の Intent で足された CI の要約の台本と規模を数える台本の呼び方を足し、CI の行を今の形に直した（K-127）。
>
> 前回の記録: 2026-11-08 の2回目（記録のコミット `9fd4f7f`）の Intent `261108-ci-size-coverage` の Focused scan でも HTTP の口は読み直していない。「ビルドと検査の口」の節に、前の Intent で足された `verifyQuick`・`-PitTests`・`-PitForks`・`jacocoQuickReport`・回帰の台本と、CI の口を足した（K-119・K-122）。
>
> 前回の記録: 2026-11-08（記録のコミット `16f46f1`）の Intent `261107-verify-quick-local` の Focused scan では HTTP の口を読み直していない（今回の主題は1コマンドの検査の入口で、アプリの口を変えない見込み）。開発者が使う「ビルドと検査の口」（Gradle のタスクと `-P` の値、npm のスクリプト）の節を足した。
>
> 前回の記録: 2026-11-06（記録のコミット `a789a6f`）の Intent `261104-tz-options-perf` の Focused scan では HTTP の口を読み直していない（今回の不具合は画面の中の関数で、口を変えない見込み）。「内部の口」に画面の日時の変換の公開の入口と選択肢の組み立ての関数を足した。
>
> 前回の記録: 2026-11-03（記録のコミット `fa0b01e`）の Intent `261102-timezone-ui-polish` の Focused scan で、業務のメニューの口（`GET /api/me/navigation`、K-101）と、業務データの日時の値の形（K-94・K-97）の節を足した。口の数は数え直していない。
>
> 前回の記録: 2026-11-01（記録のコミット `6079f13`）の Intent `261101-maintenance-update` の Focused scan では API を読み直していない（今回の Intent は依存の版・文書・小さな直しで、API を変えない見込み）。以下は前回（2026-10-29）以前の記録のまま。
>
> 前回の記録: 2026-10-29（記録のコミット `15a25aa`）の Intent `261029-reference-lookup-cleanup` の Focused scan で更新した。今回は `businessdata/web` の定義の応答（`ColumnDefinitionResponse`・`OptionResponse`）と道の定数を読み、選択肢の REFERENCE・LOOKUP の情報を載せる欄が無いこと（K-74）を記録した。K-64（件数 `total`）は前回（`261027-perf-targets-review`）の記述のまま。ほかの口の一覧は前回の記録で、今回は数え直していない。

## 外部の API（REST、Spring MVC、JSON）

エラー応答は RFC 9457 Problem Details に安定した `code` を足した形。分類は口ごとの印 `@ApiAccess`（`PUBLIC`・`AUTHENTICATED`・`ADMIN`）。

| 道の根 | 分類 | 口の数 | 持ち主 |
|---|---|---|---|
| `/api/auth`・`/api/registration`・`/api/appearance`・`/api/problems/{slug}` | PUBLIC | 7 | `auth`・`invitation`・`appearance`・`common/error`（前回の数） |
| `/api/me/**`（設定・パスワード・作業ロール・自分の権限・メニュー・テーブルの可否） | AUTHENTICATED | 10 | `user`・`role`・`navigation`（前回の数） |
| `/api/admin/**`（check・利用者・招待・DSL・グループ・ロール・権限の設定・割り当て・権限の入れ出し） | ADMIN | 48 | `access`・`useradmin`・`invitation`・`dslmanage`・`group`・`role`（前回の数） |
| `/api/tables`（業務データ） | AUTHENTICATED | 6 | `businessdata/web/BusinessDataReadController`・`BusinessDataWriteController` |

- 前回の合計 65 口に業務データの 6 口を足した 71 口（業務データ以外の数は前回の表の合計で、今回数え直していない）。

### 今回深く読んだ口: 業務データ（`/api/tables`）

道の定数は `businessdata/web/BusinessDataPaths.java`。主キーの値は道にも問い合わせにも載せず、本文で運ぶ（トレースの属性に値を残さないため。前回の決定）。

| 操作 | 方法と道 | 持ち主のメソッド |
|---|---|---|
| 定義 | `POST /api/tables/definition` | `BusinessDataReadController.definition` |
| 一覧・検索 | `POST /api/tables/rows/search` | `BusinessDataReadController.search` |
| 詳細 | `POST /api/tables/rows/get` | `BusinessDataReadController.get` |
| 作成 | `POST /api/tables/rows` | `BusinessDataWriteController.create` |
| 更新 | `PATCH /api/tables/rows` | `BusinessDataWriteController.update` |
| 削除 | `POST /api/tables/rows/delete` | `BusinessDataWriteController.delete` |

- 6 口とも `@ApiAccess(ApiAccessLevel.AUTHENTICATED)`（`BusinessDataReadController` 50 行、`BusinessDataWriteController` 54 行）。権限はサービスの層がテーブルごとに判定する。
- 一覧の応答 `RowPageResponse`（`businessdata/web/RowPageResponse.java` 22〜58 行）は `definitionId`・`definitionChanged`・`items`・`page`（`long`）・`size`（`int`）・`total`（`long`）。`toString` は件数だけを出す。

**K-74（API 側の事実）定義の応答は選択肢を FIXED のものだけ返し、REFERENCE・LOOKUP の情報を載せる欄が無い（`businessdata`）**

- 確かめた事実（開発担当の調査）:
  - `POST /api/tables/definition` の応答の `ColumnDefinitionResponse` は、カラムごとに `formPart` と `choices`（`OptionResponse` の一覧）を持つ。`OptionResponse` は `value` と `label`（`DisplayName`）だけ（アーキテクトが読み直した）。参照先の table・valueColumn・labelColumn・lookupSearch・lookupList を載せる欄は無い。
  - `choices` は DSL の `source: FIXED` の選択肢だけ。REFERENCE・LOOKUP のカラムの `formPart` は、LOOKUP が `text` に置き換わった値で返る（`architecture.md` の Interaction Diagrams 4）。
  - 参照先の行を読む専用の API は無い。検索 API（`POST /api/tables/rows/search`）の対象は1つのテーブルで、参照先の表を指定する形ではない。参照の外部キー違反だけは書き込みの応答に 409 `BUSINESS_REFERENCE_NOT_FOUND`（フィールドの誤り）として出る。
- 未検証の仮説: 参照先の選択肢やピッカーの一覧を出すには、定義の応答に参照先の情報を足すか、参照先を読む新しい API（1〜2本）を足す。どちらも `team.md` の「業務データの機能」の必須のテスト（API ごとの 401・403・200、カラムの出し分け、IDOR など）が当たる。口の数（今は 6）が増える。

**K-64 件数 `total` は確定した `long` だけで、「多数」を表す値を持てる形が API・サービス・画面のどこにも無い（`businessdata`・`frontend-feature-tables`）**

- 確かめた事実:
  - サーバー側: `RowSearchResult.Page(long total, rows)` → `SearchResult.Found(…, page.total(), …)`（`BusinessDataReadService.search`）→ `RowPageResponse.total`（`long`）。「数えていない」「上限を超えた」を表す別の項目は無い。
  - 画面（`frontend/src/features/tables/BusinessDataListPage.tsx`）: 207〜208 行で `t('tables.list.count', { total: page.total, from, to })` と `countEmpty` に数値を渡す。217〜225 行の `isPageOverflow(page.items.length, page.total)`・`previousPageFor(listState.page, page.total, listState.size)` が総数でページの範囲を判断する。`BusinessDataTable.tsx` は `totalCount={page.total}`（251 行）と `pageStatus: (current, pages, total) => …`（213 行）に総数を渡す。ページ数の計算は `frontend/src/shared/paging/paging.ts`（`pageCount`・`pageRange`）と `listPaging.ts`。
  - 画面のテスト（`BusinessDataListPage.test.tsx`・`listPaging.test.ts` ほか）と perf の台本（`bizSearchFirst` は「200 で 100 件・total が BIZ_ROWS」を確かめる。`perf/k6/scenarios.js` 160 行）が総数の数値に依っている。
- 見立て（未検証）: 件数の型を数だけでなく「多数」を持てる形に広げるなら、応答の型・サービスの結果の型・画面のページ送りの判断・画面のテストの期待・perf の確かめが一続きで変わる。ページの文を実行するかの判断（`offset >= total`）にも総数を使っている（`RowSqlExecutor.search`）ため、数えない形では「最後のページかどうか」を別の方法で決める必要がある。

### 今回深く読んだ口: 業務のメニュー（`GET /api/me/navigation`、K-101、2026-11-03）

- 持ち主: `navigation/web/NavigationController`（`@ApiAccess(AUTHENTICATED)`、道の頭 `/api/me`）。主体は要求の文脈の利用者 ID だけ。監査に残さない。
- 応答: `NavigationResponse`（`items` と、空のときの `emptyReason`: `NOT_CONFIGURED`・`NOTHING_VISIBLE`）。項目 `NavNodeResponse` は `id`（絞る前の DSL の木の位置の道、例 `2.0`）・`label`（日英の表示名）・`icon`（`NavIconPolicy` で `allowed-icons.txt` と照らした名前。無い・一致しないときは `list`）・`table`（移れる項目だけ。`schemaName`・`tableName`、エンコードしない）・`navigable`・`items`。
- **K-101**: テーブルかビューかの印は無い。`MenuFilter`（`navigation/domain`）は `TableRef` の組と主権限の操作だけを受け、`DslTable` を見ない。`NavigationService` は適用中の `DslModel` を持つため、`DslModel.findTable(TableRef)` で `DslTable.view` を引いて項目に足すことはできる位置にある（未検証）。足すなら応答の型・画面の `navigationApi.ts` の読み取り（`icon`・`table` などを型で確かめている）・テストの見本がそろって変わる。

### 業務データの日時の値の形（K-94・K-97、2026-11-03）

- 定義の応答の `ColumnDefinitionResponse` は `valueType`（`DATE`・`DATETIME`・`DATETIME_TZ`・`TIME`・`TIME_TZ` ほか）と `listFormat`（`DATE`・`DATETIME`・`TIME` ほか、無ければ null）を返す。秒の端数の上限は返さない。
- 行の値（一覧・詳細・作成の応答の主キー）: `DATE` は `YYYY-MM-DD`、`DATETIME` は `YYYY-MM-DDTHH:mm:ss[.f]`、`TIME` は `HH:mm:ss[.f]`、`DATETIME_TZ` は `YYYY-MM-DDTHH:mm:ss[.f]+00:00`、`TIME_TZ` は `HH:mm:ss[.f]+00:00`（どちらも UTC）。
- 要求の値（検索・作成・更新・行を指す鍵・参照）: 同じ形。時差つきは `Z`・`±hh:mm` も受け付けて UTC にそろえ、オフセットが無ければ UTC とみなす。読めない値は入力の誤り。本文は `architecture.md` の「日時の値の流れ」。

## 画面の道（SPA）

- 業務データ: `/tables` 配下（`frontend/src/features/tables/registration.ts`）。表の道を作る関数は1か所に閉じる（前回の決定）。メニューの業務の項目はこの道へ移る。
- 管理の画面は `/admin/**`、自分の設定は `/preferences` など（流し読み）。

## 内部の口

- 権限の解決: `role/service/EffectivePermissionResolver`（`effectiveWorkRole`・`snapshotFor`・`resolve`）。`businessdata` は `TableAccessResolver` を通してこれを使う（前回の記録）。
- 適用中の DSL: `dsl/service/ActiveDslModelProvider`。
- 対象DB の貸し出し: `targetdb/service/TargetReadAccess.withRead(ReadPurpose, …)`・`TargetWriteAccess`。
- 画面の日時の変換（2026-11-06）: `frontend/src/shared/format/timeZone/index.ts` が `formatInstant(instant, timeZone, fractionDigits?)`・`parseLocalDateTime(text, timeZone, overlapChoice?)`・`compareInstants`・`preserveInstant`・`canInterpretTimeZone` と結果の型（`FormatResult` ほか。例外を投げず `kind` で返す）を出す。整形器のキャッシュ・壁時計（`getFormatter`・`wallAt`・`offsetSecondsAt`）は出さない。
- 表示のタイムゾーンの選択肢（2026-11-06）: `frontend/src/features/preferences/buildTimeZoneOptions.ts` の `buildTimeZoneOptions(names, saved, now, browserLabel): readonly TimeZoneOption[]`（`{ value, label }`、先頭は値が空の「ブラウザに合わせる」、ラベルは `名前 (±HH:mm)`）と `readSupportedTimeZoneNames()`。呼ぶのは `TimeZoneSelect.tsx` だけ。保存・読み込みの HTTP の口は `GET`・`PUT /api/me/preferences` の `displayTimeZone`（null は「ブラウザに合わせる」、64 文字まで。`preferencesApi.ts`）。

## ビルドと検査の口（開発者が呼ぶ。2026-11-08）

段の中身と順は `architecture.md` の「検査の入口 `verify` の流れ」。ここには呼び方だけを置く。

| 口 | 呼び方 | 中身 | 持ち主 |
|---|---|---|---|
| 1コマンドの検査 | `./gradlew verify` | 10 の段をすべて流す。CI も引数なしで同じものを呼ぶ（`ci.yml` 83〜84 行） | `build.gradle.kts` 509〜513 行 |
| 段ごとの検査 | `./gradlew verifyPrepare` 〜 `./gradlew verifyArtifact` | 1つの段（と順の決まりで前に要るもの）だけを流す | `build.gradle.kts` 496〜507 行 |
| バックエンドのテスト | `./gradlew :backend:test`・`:backend:integrationTest` | 単体（`*Test`）と結合（`*IT`）。Gradle の標準の `--tests` で絞れる。`-P` で絞る口は無い | `backend/build.gradle.kts` 135〜156 行 |
| バックエンドのカバレッジ | `./gradlew :backend:jacocoTestReport`・`:backend:jacocoTestCoverageVerification` | `test.exec` と `integrationTest.exec` を合わせた報告と判定（テストには `dependsOn` しない） | `backend/build.gradle.kts` 187〜257 行 |
| 実測のときの流し直し | `:backend:cleanTest :backend:cleanIntegrationTest` を前に付ける | UP-TO-DATE で飛ばさないため（`project.md` の Testing Posture） | Gradle の標準 |
| 依存の脆弱性の流し直し | `./gradlew osvScan --rerun` | lockfile が変わらないと UP-TO-DATE になるため（`docs/verification.md` 49 行。流し読み） | `build.gradle.kts` 327〜417 行 |
| E2E | `./gradlew e2eTest`（`-Pe2e.files=e2e/180-….e2e.ts,…`） | `verify` と CI の外。`-P` で値を受ける前例はこれだけ（`providers.gradleProperty("e2e.files")`） | `build.gradle.kts` 811〜860 行 |
| 画面のテスト | `npm run test`・`npm run test:coverage`（`frontend/` で） | 同じテストを計測なし・計測つきで流す。Gradle からは `frontendTest`・`frontendCoverage` | `frontend/package.json` 22〜23 行 |

- 上の行は 2026-11-08 の1回目（`16f46f1`）の記録で、行の番号はその時点のもの。その時点では `verifyQuick`・`-PitTests`・`-PitForks` は無かった（論点は `architecture.md` の K-116）。今は実装済み（K-122、下の表）。

2026-11-08 の2回目（記録のコミット `9fd4f7f`）に確かめた口:

| 口 | 呼び方 | 中身 | 持ち主 |
|---|---|---|---|
| 統合の前の速い入口 | `./gradlew verifyQuick` | `verify` の段の表から `frontendTest`・`jacocoTestCoverageVerification`・（`-PitTests` が無ければ）`integrationTest` を外し、`jacocoTestReport` を `jacocoQuickReport` に替えた並び。使い分けは `team.md` | `build.gradle.kts` 515〜555 行 |
| 結合テストを絞る | `-PitTests=<形>[,<形>...]`（`verifyQuick`・`:backend:integrationTest` に付ける） | 当たる `*IT` だけを流す。空の値・当たるもの無しは失敗。`verify` に付けると失敗（559〜566 行） | `backend/build.gradle.kts` 153〜164・173〜200 行 |
| 結合テストを並べる | `-PitForks=<N>` | `maxParallelForks` を N にする。1 以上の整数でなければ失敗。CI は付けない | `backend/build.gradle.kts` 166〜171・196〜198 行 |
| カバレッジの参考値 | `./gradlew :backend:jacocoQuickReport` | `test.exec` だけの報告を `backend/build/reports/jacoco/quick/` に作り、全体の行・分岐を1行表示（判定しない） | `backend/build.gradle.kts` 250〜296 行 |
| 入口の回帰の確かめ | `node scripts/verify-graph-check.mjs`（リポジトリのルートで） | `verify`・`verifyQuick` のタスクの一覧と順、`-P` の誤りの扱いの 8 項目を `--dry-run` だけで確かめる。手で流す | `scripts/verify-graph-check.mjs` |
| CI | `develop` への push・`v*` のタグ・手動 | ジョブ `verify` が `./gradlew verify` を引数なしで流し、WAR を保存する。実行の要約（`$GITHUB_STEP_SUMMARY`）には書かない | `.github/workflows/ci.yml`（`architecture.md` の K-119） |

- 上の CI の行は 2026-11-08 の2回目の記録で、今は古い（下の表）。

2026-11-09（記録のコミット `4fe8e79`）に確かめた口:

| 口 | 呼び方 | 中身 | 持ち主 |
|---|---|---|---|
| CI | `develop` への push・`v*` のタグ・手動（`main` の push では動かない） | ジョブ `verify` が `./gradlew verify` を引数なしで流し、WAR を保存し、報告だけの手順で cloc を入れて要約を書き、カバレッジの HTML を 14 日保存する（10 手順） | `.github/workflows/ci.yml`（`architecture.md` の K-127） |
| CI の要約 | `node scripts/ci-summary.mjs [--base <commit>] [--backend-reports <dir>] [--frontend-coverage <dir>]`（リポジトリのルートで） | 規模 → バックエンドのテストの件数 → カバレッジを `$GITHUB_STEP_SUMMARY`（無ければ標準出力）に書く。欄が作れなくても終了コード 0 | `scripts/ci-summary.mjs` |
| 規模を数える | `node scripts/program-size.mjs [--base <commit>] [<数える場所>]` | git が管理するファイルを分類して cloc で数え、Markdown を標準出力に出す。git・cloc が無ければ終了コード 1 | `scripts/program-size.mjs` |

2026-11-11（記録のコミット `7f36d8a`）に確かめた口（上の表の CI の行の「引数なしで流す」・`-PitForks` の行の「CI は付けない」は古い）:

| 口 | 呼び方 | 中身 | 持ち主 |
|---|---|---|---|
| CI | `develop` への push・`v*` のタグ・手動（`workflow_dispatch`、入力 `it_forks`） | ジョブ `verify` が `./gradlew verify -PitForks="$IT_FORKS"` を流す。`IT_FORKS` は入力 `it_forks`、無ければ `IT_FORKS_DEFAULT`（3）。1〜8 の外は手順が止まる。ほかの手順は 2026-11-09 と同じ | `.github/workflows/ci.yml` 43・98〜104 行（`architecture.md` の K-140） |
| CI を N を変えて流す | `gh workflow run ci.yml --ref <ブランチ> -f it_forks=<N>` | 手動の実行で並べる JVM の数を替える | `docs/verification.md` 83 行 |
| 結合テストを並べる | `-PitForks=<N>` | `maxParallelForks` を N にする。1 以上の整数でなければ失敗（上限は Gradle の側に無い） | `backend/build.gradle.kts` 166〜171・196〜198 行 |
| 依存の固定の更新 | `./gradlew :backend:resolveAndLockAll --write-locks` | すべての構成を解決して `backend/gradle.lockfile` を書き直す（`--write-locks` が無いと失敗） | `backend/build.gradle.kts` 516〜527 行 |
| ヘルスチェック | `GET /actuator/health`（ログインなし） | 内部DB の確かめは `TimeBoundedDbHealthIndicator`（`mastersmith.health.db-timeout`、既定 2s）。応答は状態だけ。前の確かめが終わっていなければ待たずに DOWN（503） | `common/health/`（`component-inventory.md` の `common-health`） |

## API を足すときの決まり

新しい口には同じ変更で `@ApiAccess` を付ける。`ADMIN` は `/api/admin` の下だけ。テストの `ApiAccessArchitectureTest` と `ApiAccessConsistencyIT` が分類の網羅を確かめる。`team.md` の「役割・権限の機能」「業務データの機能」の必須のテスト（API ごとの 401・403・200 など）が当たる（前回の記録。今回は読み直していない）。

## Sources

- 2026-11-11: `.github/workflows/ci.yml`・`backend/build.gradle.kts`・`backend/src/main/java/cherry/mastersmith/common/health/`（どれも全文）、`docs/verification.md` 79〜95 行、開発担当の調査 `aidlc/spaces/default/intents/261110-deps-forks-review/inception/reverse-engineering/developer-scan.md`。
- 2026-11-09: `.github/workflows/ci.yml`・`scripts/ci-summary.mjs`・`scripts/program-size.mjs`（どれも全文。開発担当の調査 `aidlc/spaces/default/intents/261108-ci-checks-release/inception/reverse-engineering/developer-scan.md`）。
- 2026-11-08 の2回目: `build.gradle.kts`・`backend/build.gradle.kts`・`scripts/verify-graph-check.mjs`・`.github/workflows/ci.yml`・`docs/verification.md`（どれも全文。開発担当の調査 `aidlc/spaces/default/intents/261108-ci-size-coverage/inception/reverse-engineering/developer-scan.md`）。
- 2026-11-08: `build.gradle.kts`・`backend/build.gradle.kts`・`frontend/package.json`・`.github/workflows/ci.yml`、流し読みの `docs/verification.md`（開発担当の調査 `aidlc/spaces/default/intents/261107-verify-quick-local/inception/reverse-engineering/developer-scan.md`）。
- 2026-11-06: `frontend/src/shared/format/timeZone/index.ts`・`frontend/src/features/preferences/{buildTimeZoneOptions,preferencesApi}.ts`（開発担当の調査 `aidlc/spaces/default/intents/261104-tz-options-perf/inception/reverse-engineering/developer-scan.md`）。
- 2026-11-03: `navigation/web/{NavigationController,NavNodeResponse,NavTableRefResponse}.java`・`navigation/domain/{MenuFilter,NavIconPolicy}.java`・`navigation/service/NavigationService.java`・`businessdata/web/ColumnDefinitionResponse.java`・`businessdata/repository/ColumnValueCodec.java`（開発担当の調査 `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/reverse-engineering/developer-scan.md`）。
- 2026-11-01: 読み直しなし（開発担当の調査 `aidlc/spaces/default/intents/261101-maintenance-update/inception/reverse-engineering/developer-scan.md` の APIs Discovered）。
- 開発担当の調査（今回: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/reverse-engineering/developer-scan.md` の APIs Discovered）と `OptionResponse.java`（アーキテクト、2026-10-29）。
- `businessdata/web/BusinessDataReadController.java`・`BusinessDataWriteController.java`・`BusinessDataPaths.java`・`RowPageResponse.java`、`BusinessDataListPage.tsx`（アーキテクト、2026-10-25）と開発担当の APIs Discovered・K-64。
