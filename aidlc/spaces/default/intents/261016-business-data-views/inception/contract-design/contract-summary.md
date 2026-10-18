# 契約の一覧（Contract Design）— business-data-views

出典: 単位 `aidlc/spaces/default/intents/261016-business-data-views/inception/units-generation/unit-of-work.md`・`unit-of-work-dependency.md`、部品 `aidlc/spaces/default/intents/261016-business-data-views/inception/domain-design/components.md`・`decisions.md`（ADR-001〜008）、要件 `aidlc/spaces/default/intents/261016-business-data-views/inception/requirements-analysis/requirements.md`、ストーリー `aidlc/spaces/default/intents/261016-business-data-views/inception/user-stories/stories.md`（SM・SL）、この段の答え `contract-design-questions.md`（CQ1〜CQ3: A）。既存のコードの口（`EffectivePermissionResolver`・`targetdb.domain`・Problem Details の `code` と `fieldErrors`）は書く前に確かめた。

## 契約の一覧

| # | Provider Unit | Consumer | Mechanism | Owner |
|---|---|---|---|---|
| C1 | U1 u1-businessdata-read | U2 u2-businessdata-write | 同じ機能 `businessdata` の中の型と口（共有のモデル） | U1 |
| C2 | U1 u1-businessdata-read | U3 u3-businessdata-screens | 同期の HTTP（JSON・Problem Details）: 定義・一覧・詳細 | U1 |
| C3 | U2 u2-businessdata-write | U3 u3-businessdata-screens | 同期の HTTP（JSON・Problem Details）: 作成・更新・削除 | U2 |
| C4 | U4 u4-a11y-carryover | U3 u3-businessdata-screens | 依存の固定先（make-you-chic-ui `b0a1e79`）とフロントエンドの関数 | U4 |
| C5 | U2 u2-businessdata-write | AuditLog（`audit`、U2 の中） | Spring の出来事 | U2 |
| C6 | TargetDb（`targetdb`、U1・U2 が変える） | U1・U2 | アプリの中の口（`targetdb.service`） | U1（読み取りの口）・U2（書き込みの口） |
| C7 | RoleManagement（`role`、既存） | U1・U2 | アプリの中の口（`role.service` の `EffectivePermissionResolver`、既存の形を変えない） | 既存（前の Intent の契約 C5）。使い方の決まりは U1 |

外部（アプリの外の利用者）に公開する API は無い。業務データの API は同じ WAR の画面（SPA）だけが使う。C2・C3 の API 6本（定義・一覧・詳細・作成・更新・削除）は、すべて `@ApiAccess` の「ログインだけ」（`AUTHENTICATED`、`/api/` の下で管理の道の外）に分類し、判定は作業ロールの権限で行う。分類を足し忘れると既存の `ApiAccessArchitectureTest` が落ちる（K-61。R-04）。

この版は、承認の場の Request Changes（アーキテクチャのレビュー役の指摘 R-01〜R-09）を、`contract-design-questions.md` の「Requested Changes Feedback」の推奨の案で直した版である。

## C1 U1 → U2（同じ機能の中の型と口）

```yaml
# shared-schema（Java の型と口。名前は仮で、機能設計で決める）
package: cherry.mastersmith.businessdata
owner: U1
types:
  TableAccessView:            # 1要求の中で使う、作業ロールの写しと DSL から求めた判定の材料
    source: "EffectivePermissionResolver.snapshotFor(userId) の1回の写し（C7）と、その写しの DSL（DslModel・dslHash）"
    fields: [schema, table, dslHash, workRole, columns, primaryKey, view, operations, writeConfigured]
  ColumnAccess:
    fields: [column, main, listVisible, detailVisible, editableOnCreate, editableOnUpdate, formPart, validations, searchSetting, options]
  AllowedOperations:          # 許す操作（ADR-005 の純粋な関数の結果）
    values: [LIST, DETAIL, CREATE, UPDATE, DELETE]
  RowKey:                     # 行の主キーの値（主キーのカラムの名前と値の組）。toString は値を伏せる
    fields: [values]
  RowValues:                  # 行の値（カラムの名前と値の組）。toString は値を伏せる
    fields: [values]
operations:
  - name: accessFor
    in: [userId, schema, table]
    out: "TableAccessView | 404（DSL が無い・DSL に無いスキーマかテーブル）| 403（テーブルが NONE）"
    note: "判定の順の①〜③（ADR-006）。U2 も作成・更新・削除の前にこれを使う"
  - name: decide
    in: [TableAccessView, requestedOperation, requestedColumns]
    out: "許す | 409（主キーとビューの決まり）| 403（CREATE が不可・DELETE が不可・FULL のカラムが1つも無いテーブルの更新）| 400（書けないカラム）"
    note: "判定の順の④⑤。書けないカラム（NONE・READ・DSL に無い・detail.visible で隠す・更新の主キー）を本文や条件に入れたときは、どれも同じ 400 と同じ code（カラムの有無と権限を推測させない。SL・R-01）。403 は操作そのものの権限の不足だけに使う"
  - name: RowSqlExecutor
    note: "行の SQL を組み立てて実行する唯一の部品（ADR-001）。識別子は TableAccessView の DSL の名前と照らしてから DB の種類ごとに引用し、値はバインドで渡す。U1 が読み取り（select・count）を、U2 が書き込み（insert・update・delete）を足す。prepareStatement・createStatement を呼んでよいのはこの部品だけ（ArchUnit）"
  - name: BusinessDataProblemTypes
    note: "業務データの拒否の code の一覧（businessdata.domain）。1つの code に1つの状態コード。U1 が作り、U2 が書き込みの code を足す。名前は機能設計"
```

## C2 U1 → U3（定義・一覧・詳細の HTTP）

```yaml
openapi: 3.1.0
info:
  title: 業務データの読み取りの API（U1）
  version: "intent-261016"   # 版の番号は付けない（1つの WAR で同じ版を配備）。ここは記録のための印
paths:
  /api/tables/{schema}/{table}/definition:
    get:
      summary: テーブルの定義（作業ロールの権限で判定した見せ方と操作の可否）
      parameters:
        - { name: schema, in: path, required: true, schema: { type: string }, description: "DSL のスキーマ名（エンコード済み）" }
        - { name: table, in: path, required: true, schema: { type: string }, description: "DSL のテーブル名（エンコード済み）" }
      responses:
        "200": { description: 定義, content: { application/json: { schema: { $ref: "#/components/schemas/TableDefinition" } } } }
        "401": { $ref: "#/components/responses/Problem" }
        "403": { $ref: "#/components/responses/Problem" }   # ACCESS_DENIED（テーブルが NONE）
        "404": { $ref: "#/components/responses/Problem" }   # DSL が無い・DSL に無いスキーマかテーブル
  /api/tables/{schema}/{table}/rows/search:
    post:
      summary: 一覧・検索（行の値と検索の値は本文で運ぶ。CQ1）
      parameters: [ { $ref: "#/components/parameters/Schema" }, { $ref: "#/components/parameters/Table" } ]
      requestBody: { required: true, content: { application/json: { schema: { $ref: "#/components/schemas/SearchRequest" } } } }
      responses:
        "200": { description: 1ページ, content: { application/json: { schema: { $ref: "#/components/schemas/RowPage" } } } }
        "400": { $ref: "#/components/responses/Problem" }   # 入力の誤り（fieldErrors）・書けないカラム
        "401": { $ref: "#/components/responses/Problem" }
        "403": { $ref: "#/components/responses/Problem" }
        "404": { $ref: "#/components/responses/Problem" }
        "503": { $ref: "#/components/responses/Problem" }   # 対象DB とつながらない・時間切れ（状態コードは機能設計で確かめる）
  /api/tables/{schema}/{table}/rows/get:
    post:
      summary: 1行の詳細（主キーは本文で運ぶ）
      parameters: [ { $ref: "#/components/parameters/Schema" }, { $ref: "#/components/parameters/Table" } ]
      requestBody: { required: true, content: { application/json: { schema: { $ref: "#/components/schemas/RowKeyRequest" } } } }
      responses:
        "200": { description: 1行, content: { application/json: { schema: { $ref: "#/components/schemas/RowDetail" } } } }
        "400": { $ref: "#/components/responses/Problem" }
        "401": { $ref: "#/components/responses/Problem" }
        "403": { $ref: "#/components/responses/Problem" }   # NONE のテーブルでは行の有無にかかわらず 403
        "404": { $ref: "#/components/responses/Problem" }   # 行が無い
        "409": { $ref: "#/components/responses/Problem" }   # 主キーの無いテーブル・主キーが NONE・行が1つに決まらない
        "503": { $ref: "#/components/responses/Problem" }
components:
  parameters:
    Schema: { name: schema, in: path, required: true, schema: { type: string } }
    Table: { name: table, in: path, required: true, schema: { type: string } }
  schemas:
    DefinitionId:
      type: object
      required: [dslHash, workRoleId]
      properties:
        dslHash: { type: string }
        workRoleId: { type: [integer, "null"] }   # 作業ロールが無ければ null（その場合はすべて NONE で 403）
    TableDefinition:
      type: object
      required: [definitionId, displayName, view, hasPrimaryKey, operations, listColumns, detailColumns, searchFields, sortableColumns, defaultSort, writeConfigured]
      properties:
        definitionId: { $ref: "#/components/schemas/DefinitionId" }
        displayName: { $ref: "#/components/schemas/DisplayName" }
        view: { type: boolean }
        hasPrimaryKey: { type: boolean }
        primaryKeyColumns: { type: array, items: { type: string }, description: "行を指すための主キーのカラム（READ 以上のときだけ）" }
        operations: { type: array, items: { enum: [LIST, DETAIL, CREATE, UPDATE, DELETE] } }
        restrictionReason: { enum: [NO_PRIMARY_KEY, PRIMARY_KEY_HIDDEN, VIEW, null], description: "行えない操作の理由（画面の理由の表示）" }
        writeConfigured: { type: boolean }
        listColumns: { type: array, items: { $ref: "#/components/schemas/ColumnDefinition" } }
        detailColumns: { type: array, items: { $ref: "#/components/schemas/ColumnDefinition" } }
        searchFields: { type: array, items: { $ref: "#/components/schemas/SearchField" } }
        sortableColumns: { type: array, items: { type: string } }
        defaultSort: { type: array, items: { $ref: "#/components/schemas/SortKey" } }
    ColumnDefinition:
      type: object
      required: [column, displayName, valueType, nullable]
      properties:
        column: { type: string }
        displayName: { $ref: "#/components/schemas/DisplayName" }
        valueType: { enum: [STRING, INTEGER, BIG_INTEGER, DECIMAL, BOOLEAN, DATE, DATETIME, DATETIME_TZ, TIME, TIME_TZ, BINARY, OTHER] }
        listFormat: { enum: [DATE, DATETIME, TIME, NUMBER_GROUPED, BOOLEAN_YES_NO, null] }
        width: { type: [integer, "null"] }
        formPart: { enum: [text, textarea, checkbox, switch, radio, select, date, datetime, number, password, lookup] }
        editableOnCreate: { type: boolean }
        editableOnUpdate: { type: boolean }
        required: { type: boolean }
        maxLength: { type: [integer, "null"] }
        numericRange: { type: object, properties: { min: { type: [string, "null"] }, max: { type: [string, "null"] } } }
        hasPattern: { type: boolean, description: "pattern はサーバーだけで当てる（SM4）。式そのものは返さない" }
        options: { type: array, items: { $ref: "#/components/schemas/Option" }, description: "FIXED の選択肢だけ" }
        nullable: { type: boolean }
        masked: { type: boolean, description: "password のカラム（SM3）" }
    SearchField:
      type: object
      required: [column, displayName, operator, valueType]
      properties:
        column: { type: string }
        displayName: { $ref: "#/components/schemas/DisplayName" }
        operator: { enum: [EQUALS, CONTAINS, RANGE, CHOICE] }   # REFERENCE・LOOKUP の CHOICE は EQUALS で返す（FR3.6）
        valueType: { $ref: "#/components/schemas/ColumnDefinition/properties/valueType" }
        defaultValue: { type: [string, "null"] }
        collapsed: { type: boolean }
        options: { type: array, items: { $ref: "#/components/schemas/Option" } }   # 真偽値で選択肢の無い CHOICE は true・false
    Option: { type: object, required: [value, label], properties: { value: { type: string }, label: { $ref: "#/components/schemas/DisplayName" } } }
    DisplayName: { type: object, properties: { ja: { type: string }, en: { type: string } } }
    SortKey: { type: object, required: [column, direction], properties: { column: { type: string }, direction: { enum: [ASC, DESC] } } }
    SearchRequest:
      type: object
      required: [page, size]
      properties:
        definitionId: { $ref: "#/components/schemas/DefinitionId" }
        conditions:
          type: array
          items:
            type: object
            required: [column]
            properties:
              column: { type: string }
              value: { type: [string, "null"] }        # EQUALS・CONTAINS
              from: { type: [string, "null"] }         # RANGE
              to: { type: [string, "null"] }
              values: { type: array, items: { type: string } }   # CHOICE
        sort: { $ref: "#/components/schemas/SortKey" }  # sortable の1カラム（無ければ defaultSort、無ければ主キーの順）
        page: { type: integer, minimum: 1 }             # 1 から始まる（既存の一覧と同じ）
        size: { enum: [20, 50, 100] }
    Value:
      description: "カラムの値はすべて文字列か null で運ぶ（BIGINT・DECIMAL の桁を失わない。日時は ISO の形、時差つきの型は +00:00 付き、真偽値は \"true\"/\"false\"、password のカラムは値を返さない）"
      type: [string, "null"]
    RowPage:
      type: object
      required: [definitionId, items, page, size, total]
      properties:
        definitionId: { $ref: "#/components/schemas/DefinitionId" }
        items: { type: array, items: { $ref: "#/components/schemas/RowItem" } }
        page: { type: integer }
        size: { type: integer }
        total: { type: integer, format: int64 }
    RowItem:
      type: object
      required: [values]
      properties:
        key: { type: object, additionalProperties: { $ref: "#/components/schemas/Value" }, description: "行を指す主キー（DETAIL が許されるときだけ）" }
        values: { type: object, additionalProperties: { $ref: "#/components/schemas/Value" }, description: "listColumns のカラムだけ" }
    RowKeyRequest:
      type: object
      required: [key]
      properties:
        definitionId: { $ref: "#/components/schemas/DefinitionId" }
        key: { type: object, additionalProperties: { type: string } }
    RowDetail:
      type: object
      required: [definitionId, key, values]
      properties:
        definitionId: { $ref: "#/components/schemas/DefinitionId" }
        key: { type: object, additionalProperties: { $ref: "#/components/schemas/Value" } }
        values: { type: object, additionalProperties: { $ref: "#/components/schemas/Value" }, description: "detailColumns のカラムだけ" }
  responses:
    Problem:
      description: "Problem Details に code を足した形（既存の @RestControllerAdvice）。入力の誤りは fieldErrors（[{field, reason}]）。detail・instance・fieldErrors に入力した値と主キーの値を入れない"
      content:
        application/problem+json:
          schema:
            type: object
            required: [type, title, status, code]
            properties:
              type: { type: string }
              title: { type: string }
              status: { type: integer }
              code: { type: string }
              traceId: { type: string }
              fieldErrors:
                type: array
                description: "書けないカラムの拒否では、NONE のカラムと DSL に無いカラムで同じ形（field は要求に入っていた名前、reason は同じ値）にする（R-06）"
                items: { type: object, properties: { field: { type: string }, reason: { type: string } } }
              definitionChanged: { type: boolean, description: "読み取りで、送った definitionId が今と違うとき true（画面は定義を読み直す）" }
```

- 道に載るのは DSL の識別子（スキーマ名・テーブル名）だけで、行の値・検索の値は載せない（CQ1・PM の Forbidden）。識別子は画面が道の1区切りとしてエンコードし（`/`・`%`・`?`・`#`・空白を含む名前も `%2F` などで1区切りに収める）、サーバーは復号した名前を DSL の名前と照らす。照らして無い名前は 404（R-09）。
- 読み取り（定義・一覧・詳細）で `definitionId` を送ると、サーバーは今の定義と照らし、違えば今の権限で判定した結果に `definitionChanged: true` を付けて答える（古い定義のカラムを返さない）。画面は定義を読み直す（CQ3）。書き込みの扱いは C3（R-05）。
- ページの番号は 1 から始まる（既存の一覧と同じ。R-09）。

## C3 U2 → U3（作成・更新・削除の HTTP）

```yaml
openapi: 3.1.0
info:
  title: 業務データの書き込みの API（U2）
  version: "intent-261016"
paths:
  /api/tables/{schema}/{table}/rows:
    post:
      summary: 作成（入れた FULL のカラムだけを送る。空の入力は送らず、送らないカラムは対象DB の既定値。FR6.4・X9）
      parameters: [ { $ref: "#/components/parameters/Schema" }, { $ref: "#/components/parameters/Table" } ]
      requestBody: { required: true, content: { application/json: { schema: { $ref: "#/components/schemas/CreateRequest" } } } }
      responses:
        "201": { description: 作成した行の主キー（DETAIL が許されるときだけ。無ければ空）と定義の識別, content: { application/json: { schema: { $ref: "#/components/schemas/WriteResult" } } } }
        "400": { $ref: "#/components/responses/Problem" }   # 入力の誤り（fieldErrors）・書けないカラム（NONE・READ・DSL に無い・隠す）
        "401": { $ref: "#/components/responses/Problem" }
        "403": { $ref: "#/components/responses/Problem" }   # テーブルが NONE（ACCESS_DENIED）・CREATE が不可
        "404": { $ref: "#/components/responses/Problem" }   # DSL が無い・DSL に無いテーブル
        "409": { $ref: "#/components/responses/Problem" }   # ビュー・定義が変わった・同じ値がある・参照先に無い値・DB が拒んだ保存・書き込みの設定が無い（code で分ける）
        "503": { $ref: "#/components/responses/Problem" }   # 対象DB とつながらない・時間切れ（保存されたか分からない）
    patch:
      summary: 更新（主キーと、変えたカラムだけ。SM1）
      parameters: [ { $ref: "#/components/parameters/Schema" }, { $ref: "#/components/parameters/Table" } ]
      requestBody: { required: true, content: { application/json: { schema: { $ref: "#/components/schemas/UpdateRequest" } } } }
      responses:
        "200": { description: 更新した（定義の識別を返す）, content: { application/json: { schema: { $ref: "#/components/schemas/WriteResult" } } } }
        "400": { $ref: "#/components/responses/Problem" }   # 入力の誤り・書けないカラム（READ のカラム・主キーを values に入れたときを含む。R-01）
        "401": { $ref: "#/components/responses/Problem" }
        "403": { $ref: "#/components/responses/Problem" }   # テーブルが NONE・FULL のカラムが1つも無いテーブル
        "404": { $ref: "#/components/responses/Problem" }   # DSL・テーブルが無い・行が無い
        "409": { $ref: "#/components/responses/Problem" }   # 主キーとビューの決まり・定義が変わった・行が1つに決まらない（巻き戻す）・同じ値・参照先に無い値・DB が拒んだ保存・書き込みの設定が無い
        "503": { $ref: "#/components/responses/Problem" }   # つながらない・時間切れ
  /api/tables/{schema}/{table}/rows/delete:
    post:
      summary: 削除（主キーは本文で運ぶ）
      parameters: [ { $ref: "#/components/parameters/Schema" }, { $ref: "#/components/parameters/Table" } ]
      requestBody: { required: true, content: { application/json: { schema: { $ref: "#/components/schemas/DeleteRequest" } } } }
      responses:
        "200": { description: 削除した（定義の識別を返す）, content: { application/json: { schema: { $ref: "#/components/schemas/WriteResult" } } } }
        "400": { $ref: "#/components/responses/Problem" }   # 入力の誤り
        "401": { $ref: "#/components/responses/Problem" }
        "403": { $ref: "#/components/responses/Problem" }   # テーブルが NONE・DELETE が不可
        "404": { $ref: "#/components/responses/Problem" }   # DSL・テーブルが無い・行が無い
        "409": { $ref: "#/components/responses/Problem" }   # 主キーとビューの決まり・定義が変わった・行が1つに決まらない（巻き戻す）・外部キーで拒まれた・書き込みの設定が無い
        "503": { $ref: "#/components/responses/Problem" }   # つながらない・時間切れ
components:
  parameters:
    Schema: { name: schema, in: path, required: true, schema: { type: string }, description: "DSL のスキーマ名（道の1区切りとしてエンコード）" }
    Table: { name: table, in: path, required: true, schema: { type: string }, description: "DSL のテーブル名（道の1区切りとしてエンコード）" }
  schemas:
    DefinitionId:
      type: object
      required: [dslHash, workRoleId]
      properties:
        dslHash: { type: string }
        workRoleId: { type: [integer, "null"] }
    CreateRequest:
      type: object
      required: [definitionId, values]
      properties:
        definitionId: { $ref: "#/components/schemas/DefinitionId" }
        values: { type: object, additionalProperties: { type: [string, "null"] }, description: "入れた FULL のカラムだけ。空の入力のカラムは送らない（FR6.4・X9）。時差つきの型は UTC（SM7）" }
    UpdateRequest:
      type: object
      required: [definitionId, key, values]
      properties:
        definitionId: { $ref: "#/components/schemas/DefinitionId" }
        key: { type: object, additionalProperties: { type: string } }
        values: { type: object, additionalProperties: { type: [string, "null"] }, description: "変えたカラムだけ。空にしたカラムは画面が写して送る（文字の型は \"\"、ほかの型は null。SM2）。サーバーは型と照らして確かめる。password は入れたときだけ（SM3）" }
    DeleteRequest:
      type: object
      required: [definitionId, key]
      properties:
        definitionId: { $ref: "#/components/schemas/DefinitionId" }
        key: { type: object, additionalProperties: { type: string } }
    WriteResult:
      type: object
      required: [definitionId]
      properties:
        definitionId: { $ref: "#/components/schemas/DefinitionId" }
        key: { type: object, additionalProperties: { type: [string, "null"] }, description: "作成した行の主キー（自動の採番を含む）。詳細へ移れないとき・更新と削除では無し" }
  responses:
    Problem:
      description: "C2 の Problem と同じ形（Problem Details・code・fieldErrors・traceId）。detail・instance・fieldErrors に入力した値と主キーの値を入れない"
```

- 誤りの応答の形は C2 と同じ（Problem Details・`code`・`fieldErrors`。入力した値と主キーの値を入れない）。業務の失敗の code（定義が変わった・同じ値がある・参照先に無い値・行を1つに決められない・書き込みの設定が無い・接続できない・時間切れ など）と状態コードの割り当ては機能設計で確定し、1つの code に1つの状態コードを固定する（team.md の Code Style）。
- 書き込み（作成・更新・削除）では、送った `definitionId` が今の定義（`dslHash` と作業ロール）と違えば、行を変えずに 409（定義が変わった）で拒否し、画面に定義を読み直させる（R-05。読み取りの `definitionChanged` とは扱いを分ける）。この拒否は対象DB に触れる前の業務の拒否のため、監査に残すかは U2 の機能設計で決める。
- 空の入力の写し（R-03）: 作成の画面は空のカラムを送らない。更新の画面は、空にしたカラムを文字の型なら `""`、ほかの型なら `null` にして送る。サーバーは送られた値をカラムの型と照らし（文字の型でないカラムに `""` が来たら入力の誤り）、写しの決まりを二重に持たない。
- 作成・更新・削除の成功と業務の失敗は監査に残る（C5）。403・400・DSL に無いテーブルの 404 は残らない。

## C4 U4 → U3（make-you-chic-ui の固定先と名前の関数）

```yaml
# shared-schema（フロントエンドの依存と関数）
owner: U4
dependency:
  name: make-you-chic-ui
  pin: b0a1e79            # Dropdown の説明のある項目の DOM の形が変わる（WCAG 2.5.3 の直し）
  rule: "固定先の更新は承認を得た専用のコミット。前後のハッシュ・上流の main から辿れること・vendor の lockfile の差を記録"
function:
  module: "frontend/src/shared/（置き場は機能設計で決める）"
  name: "rowActionsLabel（仮）"
  in: [visibleLabel, rowIdentifier]        # 「操作」・Actions と、行を見分ける値
  out: "\"操作: <行を見分ける値>\" の形の文字列（英語は \"Actions: ...\"）"
  rule: "見えている文字で始める（WCAG 2.5.3）。行を見分ける値は画面に出す値で、ログ・トレースには出さない"
upstream_request:
  file: "aidlc/spaces/default/intents/261016-business-data-views/make-you-chic-ui-request-5.md"
  items:
    - "Topbar の利用者のメニューのトリガーの名前（E2E 080 の違反の部品と確かめたとき。今の Topbar の口で frontend の側から直せるときは送らない）"
    - "Table の横のスクロールの領域へのキーボードのフォーカスとラベル（取り込みまでは src/shared/ の枠で包む）"
    - "Table の1ページの件数の選択（取り込みまでは Table の外に Select を置く）"
  repin_rule: "上流の対応を取り込むときは、b0a1e79 の後のコミットへ固定先をもう一度上げる。報告ではなく上流のソースで口の形を確かめ、承認を得た専用のコミットで行い、前後のハッシュ・上流の main から辿れること・vendor の lockfile の差を記録する。取り込んだら src/shared/ の手当てを make-you-chic-ui の口に置き換える（R-07）"
```

## C5 U2 → AuditLog（業務データの出来事）

```yaml
asyncapi: 3.0.0
info:
  title: 業務データの操作の出来事（U2 の中、Spring の出来事）
  version: "intent-261016"
channels:
  businessDataEvents:
    description: "businessdata が対象DB の確定（失敗なら巻き戻し）の後に出し、AuditEventListener が fallbackExecution で受けて内部DB に書く（ADR-003）"
    messages:
      BusinessDataChanged:
        payload:
          type: object
          required: [actorUserId, workRoleId, schema, table, operation, result]
          properties:
            actorUserId: { type: integer }
            workRoleId: { type: [integer, "null"] }
            schema: { type: string }
            table: { type: string }
            operation: { enum: [CREATE, UPDATE, DELETE] }
            result: { enum: [SUCCESS, FAILURE] }       # 既存の AuditResult の値
            failureCode: { type: [string, "null"], description: "失敗の理由の code（業務の拒否・DB が拒んだ保存・接続できない・時間切れ）" }
            key: { type: object, additionalProperties: { type: [string, "null"] }, description: "行の主キーの値（主キーの無いテーブルの作成では無し）。監査の中身の上限を超える分は切り詰めたと分かる形（機能設計）" }
operations:
  recordBusinessData:
    action: receive
    channel: { $ref: "#/channels/businessDataEvents" }
    description: "主キー以外の値・前後の値・JDBC の例外のメッセージ・接続先は持たない。書き込みに失敗しても操作は成功のまま、アプリのログに失敗を残す（主キーの値と行の値をログに含めない）"
```

- `AuditEventType` に業務データの作成・更新・削除の種類を足す（名前は機能設計、32 文字以内）。既存の監査の表の欄（`result`・`failure_reason` など）を使い、スキーマ・テーブル・主キーを置く欄は機能設計で決める（ADR-003）。
- 依存の向き: `audit` が `businessdata.domain` の出来事の型に依存する（`businessdata` は `audit` に依存しない）。U2 の作業として、`AuditBoundaryArchitectureTest` に `audit` から `businessdata.domain` への依存を許す決まりを足し（既存の決まりを緩める変更ならコード生成の計画で承認を得る）、`audit` のパッケージに手を入れることによるカバレッジの下限（`packagesJudgedByTotal` の `audit.repository` を含む）を Delivery Planning とコード生成の計画で実測して見積もる（team.md。R-08）。

## C6 TargetDb → U1・U2（接続の口）

```yaml
# shared-schema（targetdb.service の口。名前は仮で、機能設計で決める）
owner: "U1（読み取り）・U2（書き込み）"
port: TargetDbConnections
operations:
  - name: readDataSource
    out: "読み取りの DataSource と DatabaseProduct（今の読み取りの接続。設定が無いときは無い）"
  - name: writeDataSource
    out: "書き込みの DataSource と DatabaseProduct（.env の書き込みの接続。設定が無いときは無い → 業務データの書き込みは「設定が無い」で拒否）"
rules:
  - "targetdb の中では書き込み・DDL を発行せず、JdbcTemplate を使わない（境界テストを緩めない）。行の SQL は businessdata の RowSqlExecutor が発行する"
  - "書き込みの DataSource と対象DB 用のトランザクションの管理者は、既存の内部DB の DataSource とトランザクションの管理者の既定の候補にならないように作る（Domain Design のレビュー R-01。置き場と作り方は機能設計・NFR 設計）"
  - "業務データの読み取りの目的の時間の上限（ReadPurpose に目的を足すか、businessdata の側に置くか）と、SqlIdentifiers・DatabaseProduct を businessdata が使うか移すかは機能設計で決める（Domain Design のレビュー R-02）"
  - "接続先と資格情報は環境変数（.env）だけから受け取り、画面・API・DSL から受け取らない（PM の Forbidden）"
```

## C7 RoleManagement → U1・U2（権限の解決の口の使い方）

```yaml
# shared-schema（既存の口、形を変えない）
owner: "既存（前の Intent の契約 C5）"
port: cherry.mastersmith.role.service.EffectivePermissionResolver
rules:
  - "業務データの要求1件で snapshotFor(userId) を1回だけ呼び、その写し（PermissionSnapshot）で、テーブル・カラムの主権限とテーブルの CREATE・DELETE を判定する（2つ以上の対象を判定するときの既存の決まり）"
  - "写しが持つ DSL（DslModel・dslHash）を、判定と SQL の識別子の照合の両方に使う（ActiveDslModelProvider を別に読まない）。2つの間で DSL がずれない（Domain Design のレビュー R-03）"
  - "写しは要求の中だけで使い、要求をまたいで持たない。作業ロールが無い・DSL が無いときは写しがそれを示し、業務データの機能は 403・404 に写す"
  - "RoleBoundaryArchitectureTest に businessdata を role.service・role.domain に依存してよい相手として足す（ADR-002、コード生成の計画で承認を得る）"
```

## 契約の持ち主の決まり

- 各契約の持ち主は上の表の Owner。持ち主の単位の機能設計で、名前・code・状態コードの割り当てを確定する。
- 版の番号は付けない。画面とサーバーは同じ WAR で配備されるため、形を変えるときは同じ変更で両方を合わせる。
- 足すだけの変更（項目・code・操作の値を足す）は安全な変更とし、受ける側は知らない項目を無視する。項目の意味を変える・消すときは、持ち主と使う側の単位の両方の計画に書く。
- 誤りの応答に、入力した値・主キーの値・JDBC の例外のメッセージ・接続先を入れない（PM の Forbidden）。

## Open questions

| Contract | Question | Blocks |
|---|---|---|
| C2・C3 | 業務の失敗の code の名前と、409・503 などの状態コードの割り当て（定義が変わった・つながらない・時間切れ・書き込みの設定が無い） | U1・U2 の機能設計 |
| C2 | 全体の件数が重い表での扱い（ADR-007） | U1 の機能設計・NFR 要件 |
| C3 | 書き込みの「定義が変わった」の拒否を監査に残すか | U2 の機能設計 |
| C4 | 依頼（5）の上流の対応の時期と、間に合わないときに frontend の側の手当てを続けるか（team.md により Functional Design の承認の場で諮る） | U3・U4 の機能設計 |
| C2 | `valueType` の分け方（DSL の型の分類から決める）と、`BINARY`・大きな文字列の値の扱い（上限・表示しない） | U1 の機能設計 |
| C5 | 監査の行のスキーマ・テーブル・主キーの欄の置き方と切り詰めの形 | U2 の機能設計 |
| C6 | 書き込みの DataSource とトランザクションの管理者の作り方（既定の候補にしない）、業務データの時間の上限の置き場、`SqlIdentifiers`・`DatabaseProduct` の持ち主 | U1・U2 の機能設計・NFR 設計 |
| C4 | 「操作: …」の名前の関数の置き場と、行を見分ける値（主キーが列に無いときの代表のカラム） | U4・U3 の機能設計 |
