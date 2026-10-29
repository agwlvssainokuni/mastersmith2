# API（mastersmith2）

> 2026-10-29（記録のコミット `15a25aa`）の Intent `261029-reference-lookup-cleanup` の Focused scan で更新した。今回は `businessdata/web` の定義の応答（`ColumnDefinitionResponse`・`OptionResponse`）と道の定数を読み、選択肢の REFERENCE・LOOKUP の情報を載せる欄が無いこと（K-74）を記録した。K-64（件数 `total`）は前回（`261027-perf-targets-review`）の記述のまま。ほかの口の一覧は前回の記録で、今回は数え直していない。

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

## 画面の道（SPA）

- 業務データ: `/tables` 配下（`frontend/src/features/tables/registration.ts`）。表の道を作る関数は1か所に閉じる（前回の決定）。メニューの業務の項目はこの道へ移る。
- 管理の画面は `/admin/**`、自分の設定は `/preferences` など（流し読み）。

## 内部の口

- 権限の解決: `role/service/EffectivePermissionResolver`（`effectiveWorkRole`・`snapshotFor`・`resolve`）。`businessdata` は `TableAccessResolver` を通してこれを使う（前回の記録）。
- 適用中の DSL: `dsl/service/ActiveDslModelProvider`。
- 対象DB の貸し出し: `targetdb/service/TargetReadAccess.withRead(ReadPurpose, …)`・`TargetWriteAccess`。

## API を足すときの決まり

新しい口には同じ変更で `@ApiAccess` を付ける。`ADMIN` は `/api/admin` の下だけ。テストの `ApiAccessArchitectureTest` と `ApiAccessConsistencyIT` が分類の網羅を確かめる。`team.md` の「役割・権限の機能」「業務データの機能」の必須のテスト（API ごとの 401・403・200 など）が当たる（前回の記録。今回は読み直していない）。

## Sources

- 開発担当の調査（今回: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/reverse-engineering/developer-scan.md` の APIs Discovered）と `OptionResponse.java`（アーキテクト、2026-10-29）。
- `businessdata/web/BusinessDataReadController.java`・`BusinessDataWriteController.java`・`BusinessDataPaths.java`・`RowPageResponse.java`、`BusinessDataListPage.tsx`（アーキテクト、2026-10-25）と開発担当の APIs Discovered・K-64。
