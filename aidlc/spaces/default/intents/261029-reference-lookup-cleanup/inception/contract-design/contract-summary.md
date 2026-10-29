# 契約の一覧（Contract Design）— reference-lookup-cleanup

出典: 単位 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/units-generation/unit-of-work.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/units-generation/unit-of-work-dependency.md`、部品 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/domain-design/components.md`・`decisions.md`（ADR-001〜006）、要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`、ストーリー `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/user-stories/stories.md`（AC・X）、この段の答え `contract-design-questions.md`（Q1〜Q5: A）。

書く前に既存のコードの口を確かめた: 定義の API `POST /api/tables/definition`（`DefinitionRequest`: `schema`・`table`・`definitionId`）と、そのカラムの項目 `ColumnDefinitionResponse`（`formPart`・`choices` は `OptionResponse` の `value` と日英の `label`、今は `lookup` を `text` に置き換える）、`BusinessDataProblemTypes`（`BUSINESS_REFERENCE_NOT_FOUND` は 409、503 の `BUSINESS_TARGET_DB_TIMEOUT`・`BUSINESS_TARGET_DB_UNAVAILABLE`）、共通の 403 `ACCESS_DENIED`、項目の誤り `BusinessDataFieldError`（`field`・`reason`・`message`、`reason` に `REFERENCE_NOT_FOUND`・`NOT_EDITABLE_NOT_NULL`・`NOT_NULL` がある）、監査の理由 `BusinessDataAuditReason.BUSINESS_REFERENCE_NOT_FOUND`、`ReadPurpose`（`GENERATE`・`COMPARE`・`LIST`・`DETAIL`）、DSL の `OptionSource`（`table`・`valueColumn`・`labelColumn`・`lookupSearch`・`lookupList`）。

## 契約の一覧

| # | Provider Unit | Consumer | Mechanism | Owner |
|---|---|---|---|---|
| C1 | U1 u1-reference-read | U2 u2-reference-write-check | 同じ機能 `businessdata` の中の型と口（共有のモデル） | U1 |
| C2 | U1 u1-reference-read | U3 u3-reference-screens | 同期の HTTP（JSON・Problem Details）: 定義の取得（足し方）と参照先の検索 | U1 |
| C3 | U2 u2-reference-write-check | U3 u3-reference-screens | 同期の HTTP（JSON・Problem Details）: 作成・更新の拒否の形（既存の API の応答を足す） | U2 |
| C4 | U2 u2-reference-write-check | AuditLog（`audit`、既存） | Spring の出来事（既存の業務データの出来事） | U2 |
| C5 | TargetDb（`targetdb`、U1 が変える） | U1・U2 | アプリの中の口（`ReadPurpose` と時間の上限） | U1 |
| C6 | RoleManagement（`role`、既存） | U1・U2 | アプリの中の口（`EffectivePermissionResolver`・`PermissionSnapshot`、形を変えない） | 既存。使い方の決まりは U1 |
| C7 | U4 u4-perf-evidence-redactor | U5 u5-container-perf-docs（と perf の手順の利用者） | コマンドの呼び方（入力・出力・終わりのコード）と `perf/README.md` の節 | U4 |

外部（アプリの外の利用者）に公開する API は無い。新しい API（C2 の参照先の検索）は同じ WAR の画面（SPA）だけが使い、`@ApiAccess` の「ログインだけ」（`AUTHENTICATED`）に分類し、判定は作業ロールの権限で行う。分類を足し忘れると既存の `ApiAccessArchitectureTest` が落ちる。

## C1 U1 → U2（同じ機能の中の型と口）

```yaml
# shared-schema（Java の型と口。名前は仮で、機能設計で決める）
package: cherry.mastersmith.businessdata
owner: U1
types:
  ReferenceTarget:             # 参照元のカラムの DSL の定義から引いた参照先（利用者の入力からは作らない）
    fields: [kind, schema, table, valueColumn, labelColumn, lookupSearch, lookupList]
    note: "kind は REFERENCE・LOOKUP。スキーマは参照元と同じ（別のスキーマは範囲の外）"
  ReferenceAccess:             # 参照先を、その要求の作業ロールの写しで判定した結果
    values: [AVAILABLE, NO_PERMISSION]
    note: "参照先のテーブルが NONE、valueColumn（REFERENCE は labelColumn も、LOOKUP は lookupList のカラムも）が NONE のときは NO_PERMISSION（X4: 部分的に落として提供しない）"
  ReferenceValue:              # 参照先の値（1つ）。toString は値を伏せる
    fields: [value]
  ReferenceCheckResult:        # 保存前の確かめの結果（結果の型。sealed interface の record）
    cases:
      - Found                  # 参照先にあり、読める
      - NotFoundOrDenied       # 参照先に無い・読めない（区別しない。FR3.1）
      - Unavailable            # 対象DB の接続の失敗・時間切れ（503 の既存の code へ）
operations:
  - name: targetOf
    in: [TableAccessView, column]
    out: "ReferenceTarget | 参照でないカラム"
    note: "適用中の DSL（TableAccessView の写しの DSL）から引く。読み出しのたびに引き、DSL を持ち続けない"
  - name: accessOf
    in: [PermissionSnapshot, ReferenceTarget]
    out: ReferenceAccess
    note: "純粋な関数。jqwik の性質ベースのテストの対象（権限を足しても AVAILABLE が NO_PERMISSION に変わらない）"
  - name: check
    in: [userId, PermissionSnapshot, ReferenceTarget, ReferenceValue, readConnection]
    out: ReferenceCheckResult
    note: "U2 が保存の前に呼ぶ。対象DB の読み取りの接続で、値で1行を引く（RowSqlGateway の detail に出すカラムの指定を渡す）。書き込みの接続は使わない（ADR-003）。読み取りの接続は1要求で1本まで（NFR3）で、U2 は複数の参照を同じ接続で順に確かめる"
  - name: RowSqlGateway（既存の RowSqlExecutor・RowDialect）
    note: "既存の search・detail に「出すカラムの指定」と「上限+1件読み」を足す（ADR-002）。既定の値では今の一覧・詳細と同じ動き。新しい SQL のメソッドは足さず、SpotBugs の SQL_ の除外を増やさない"
```

## C2 U1 → U3（定義の取得の足し方と、参照先の検索の HTTP）

```yaml
openapi: 3.1.0
info:
  title: 業務データの参照の API（U1）
  version: "intent-261029"   # 版の番号は付けない（1つの WAR で同じ版を配備）。ここは記録のための印
paths:
  /api/tables/definition:
    post:
      summary: テーブルの定義（既存）。purpose を足し、CREATE・UPDATE のときだけ参照先を読む（Q3: A、AC3.4.2）
      requestBody:
        required: true
        content: { application/json: { schema: { $ref: "#/components/schemas/DefinitionRequest" } } }
      responses:
        "200": { description: 定義（参照のカラムは reference を持つ）, content: { application/json: { schema: { $ref: "#/components/schemas/TableDefinition" } } } }
        "400": { $ref: "#/components/responses/Problem" }   # VALIDATION_FAILED（purpose が不正）
        "401": { $ref: "#/components/responses/Problem" }
        "403": { $ref: "#/components/responses/Problem" }   # ACCESS_DENIED（参照元のテーブルが NONE。既存）
        "404": { $ref: "#/components/responses/Problem" }   # 既存（DSL が無い・DSL に無いテーブル）
        "503": { $ref: "#/components/responses/Problem" }   # 選択肢の読み取りの時間切れ・接続の失敗（Q5: A、FR1.5）。定義の取得ごと失敗にする
  /api/tables/reference/rows:
    post:
      summary: 参照先の検索とラベルの1件読み（1本。Q2: A）。本文の conditions か value のどちらか一方
      requestBody:
        required: true
        content: { application/json: { schema: { $ref: "#/components/schemas/ReferenceRowsRequest" } } }
      responses:
        "200": { description: 参照先の行, content: { application/json: { schema: { $ref: "#/components/schemas/ReferenceRows" } } } }
        "400": { $ref: "#/components/responses/Problem" }   # VALIDATION_FAILED（conditions と value の両方・どちらも無い、条件が空、値が長すぎる、ページが範囲外の扱いは機能設計）
        "401": { $ref: "#/components/responses/Problem" }
        "403": { $ref: "#/components/responses/Problem" }   # ACCESS_DENIED: 同じ応答（Q1: A）。下の「同じ応答」を参照
        "503": { $ref: "#/components/responses/Problem" }   # BUSINESS_TARGET_DB_TIMEOUT・BUSINESS_TARGET_DB_UNAVAILABLE（既存）
components:
  schemas:
    DefinitionRequest:
      type: object
      required: [schema, table]
      properties:
        schema: { type: string }
        table: { type: string }
        definitionId: { $ref: "#/components/schemas/DefinitionId" }   # 既存（任意）
        purpose: { enum: [VIEW, CREATE, UPDATE], default: VIEW, description: "無ければ VIEW（今のまま。参照先を読まない）。一覧・詳細の画面は付けない" }
    ColumnDefinition:
      description: 既存の ColumnDefinitionResponse に reference を足す。ほかの項目は変えない
      type: object
      properties:
        formPart: { type: [string, "null"], description: "lookup を text に置き換えるのをやめ、DSL の値（select・radio・lookup など）を返す。書き込み側の検証（ColumnWriteRules）は lookup を text として扱い続ける（C3）" }
        choices: { type: array, description: "既存。DSL の固定の選択肢（FIXED）だけ。参照の選択肢はここに入れない" }
        reference:
          oneOf:
            - { type: "null" }   # 参照でないカラム。purpose が VIEW のときは参照のカラムでも null（参照先を読まない、AC3.4.2）
            - { $ref: "#/components/schemas/ReferenceField" }
    ReferenceField:
      type: object
      required: [kind, status]
      properties:
        kind: { enum: [REFERENCE, LOOKUP] }
        status:
          enum: [AVAILABLE, NO_PERMISSION, TOO_MANY]
          description: >
            NO_PERMISSION は参照先を読めない（FR1.3。画面は入力不可で保存済みの値だけを出す）。TOO_MANY は REFERENCE の候補が 201 件以上
            （FR1.2。選択肢を返さず、ピッカーへの案内を出す）。LOOKUP に TOO_MANY は無い。AVAILABLE 以外のときは options・picker を返さない
        options:
          type: array
          description: "REFERENCE で AVAILABLE のときだけ。0〜200 件。並びは labelColumn の昇順（各 DB の照合順序）・ラベルが NULL は最後・同じラベルは value の昇順"
          items: { $ref: "#/components/schemas/ReferenceOption" }
        picker:
          description: "LOOKUP で AVAILABLE のときだけ。参照先のテーブルの名前は載せない（FR2.1）"
          $ref: "#/components/schemas/PickerInfo"
    ReferenceOption:
      type: object
      required: [value, label]
      properties:
        value: { type: string, description: "valueColumn の値（文字列で渡す。型の対応は既存の1か所の決まり）" }
        label: { type: [string, "null"], description: "labelColumn の値（対象DB の文字列。日英の表示名ではない）。NULL は null で、画面は値を出す" }
    PickerInfo:
      type: object
      required: [searchFields, listColumns]
      properties:
        searchFields:
          type: array
          description: "lookupSearch の条件のうち、そのカラムが読めるものだけ（X4: NONE の条件は使えなくし、使える条件が残らないときは status を NO_PERMISSION にする）"
          items:
            type: object
            required: [column, displayName, operator]
            properties:
              column: { type: string }
              displayName: { $ref: "#/components/schemas/DisplayName" }   # 参照先の DSL の表示名（日英）
              operator: { type: string, description: "DSL の SearchOperator の値" }
        listColumns:
          type: array
          description: "lookupList の並び順。valueColumn がこの中に無いときも、結果の行は値を持つ"
          items:
            type: object
            required: [column, displayName]
            properties:
              column: { type: string }
              displayName: { $ref: "#/components/schemas/DisplayName" }
    ReferenceRowsRequest:
      type: object
      required: [schema, table, column]
      description: "参照元のスキーマ・テーブル・カラム。参照先の名前は受け取らず、DSL から引く（FR2.2）。conditions と value はどちらか一方だけ"
      properties:
        schema: { type: string }
        table: { type: string }
        column: { type: string }
        conditions:
          type: array
          description: "検索（LOOKUP）。searchFields のカラムと値。値は 1,000 文字まで（実装の値は機能設計で確かめる）。部分一致の % と _ は文字として扱う"
          items: { type: object, required: [column, value], properties: { column: { type: string }, value: { type: string } } }
        page: { type: integer, minimum: 0, description: "conditions のときだけ。1 ページ 20 件" }
        value: { type: string, description: "ラベルの1件読み（REFERENCE・LOOKUP の両方。更新の画面の保存済みの値）" }
    ReferenceRows:
      type: object
      required: [rows]
      properties:
        rows:
          type: array
          description: "conditions のときは 0〜20 件。value のときは 200 なら必ず1件で、行が無いときは 200 の 0 件にせず 403 の同じ応答にする（200 の 0 件は検索だけ）"
          items:
            type: object
            required: [value, cells]
            properties:
              value: { type: string }
              label: { type: [string, "null"], description: "REFERENCE は labelColumn の値。LOOKUP は lookupList の最初のカラムの値（ラベルに使うカラムは機能設計で確かめる）" }
              cells: { type: array, items: { type: [string, "null"] }, description: "listColumns の並びの値（LOOKUP の検索のとき）" }
        page: { type: integer }
        hasNext: { type: boolean, description: "件数は数えない（件数の問い合わせを足さない）。21 件目を読んで次の有無を返す" }
    DefinitionId: { description: 既存 }
    DisplayName: { description: 既存（日英） }
    TableDefinition: { description: "既存。listColumns・detailColumns の ColumnDefinition に reference が足される" }
  responses:
    Problem:
      description: "RFC 9457 Problem Details と画面が分岐に使う code（既存の形）。内部の例外のメッセージ・参照先の値・接続情報を含めない"
```

**同じ応答（Q1: A）**: 参照先の検索 API では、次の場合をすべて 403 `ACCESS_DENIED`（既存の共通の code）にし、状態コード・`code`・本文の構造・メッセージを同一にする（AC2.1.11、X9）。

- 参照元のカラムを編集できない（主権限が FULL でない）
- 参照先のテーブルが NONE、`valueColumn`・（REFERENCE は）`labelColumn`・（LOOKUP は）`lookupList` のカラムに NONE がある、使える検索の条件が残らない
- 参照元のスキーマ・テーブル・カラムが適用中の DSL に無い、または参照（REFERENCE・LOOKUP）のカラムでない
- `value` で引いた行が無い（ラベルの1件読み）

DSL が適用されていないときも、同じ 403 にする（参照先の存在を推測させないため、既存の定義の API の 404 `BUSINESS_DSL_NOT_APPLIED` とは揃えない）。これで `team.md` の他テーブル参照の必須テスト (a) の「401・403・200」をそのまま当てられる。ストーリーの X13（403 を「同じ応答」に読み替える）は、同じ応答が 403 に決まったため、読み替えが要らなくなる。

**判定の順**: 401（未認証）→ 400（本文の形: conditions と value の両方・どちらも無い・長さ）→ 403（同じ応答）→ 503（対象DB）。本文の形の 400 は参照先の存在に依らないため、403 より前に置く。検索の条件の値の誤り（カラムが `searchFields` に無いなど）は、参照先の DSL の名前を推測させないよう 403 に含める（機能設計で、どの誤りを 400、どれを 403 にするかの一覧を固める）。

## C3 U2 → U3（作成・更新の拒否の形）

```yaml
# 既存の作成・更新の API（POST /api/tables/rows・PATCH /api/tables/rows）の応答に、参照の確かめの拒否を足す。道と本文の形は変えない
owner: U2
responses:
  "409":
    code: BUSINESS_REFERENCE_NOT_FOUND     # 既存の code（409 に固定）
    fieldErrors:                           # 既存の項目の誤りの形（BusinessDataFieldError: field・reason・message）
      - field: "<参照のカラム>"
        reason: REFERENCE_NOT_FOUND        # 参照先に無い・読めないを区別しない（FR3.1）
      - field: "<参照のカラム>"
        reason: NOT_EDITABLE_NOT_NULL      # 既存の理由。作成で、必須（NOT NULL）の参照項目に値を入れられない（定義で NO_PERMISSION・TOO_MANY だった。M1: A、X1、US1.5）
    note: >
      code はどれも 409 BUSINESS_REFERENCE_NOT_FOUND（X1）。項目の誤りの理由は 2 つに分ける。参照先に無い・読めないは REFERENCE_NOT_FOUND で、区別しない（FR3.1）。
      必須の参照項目に値を入れられない作成は、既存の NOT_EDITABLE_NOT_NULL（入力できないカラムの NOT NULL で保存できない、利用者には直せない）にし、
      画面は US1.5 の文言（「必須の参照項目に値を入れられません」）を出し分ける。この場合、利用者は定義の reference.status ですでに読めない・多すぎると
      分かっているため、理由を分けても参照先の存在は漏れない（値を送っていないため、参照先の値を試すことにならない）。
      複数の参照が拒否されたときは、項目ごとに1件ずつ並べる。外部キー違反の変換（既存）は REFERENCE_NOT_FOUND のまま
  "400":
    code: VALIDATION_FAILED                # 既存。入力不可の参照の項目の値を変えようとした（書けないカラムと同じ 400）
  "503":
    code: [BUSINESS_TARGET_DB_TIMEOUT, BUSINESS_TARGET_DB_UNAVAILABLE]   # 保存前の確かめの読み取りの時間切れ・接続の失敗（保存しない）
rules:
  - "更新では値が変わった参照だけを確かめる（FR3.1a）。本文に入っていても値が変わらなければ確かめない"
  - "値が NULL で、カラムが NULL を許すときは確かめない（FR3.2）"
  - "書き込み側の検証の材料（ColumnWriteRules）は、lookup を今までどおり text として扱う。定義の応答の formPart が lookup を返すようになっても、書き込みの値の検証（型・長さ）は変えない"
  - "判定の順は既存の 401 → 404 → 403 → 409・403 → 400 → 404（行）→ 業務の失敗 の最後に、参照先の確かめを保存の直前の業務の失敗として置く"
```

## C4 U2 → AuditLog（業務データの出来事）

```yaml
# 既存の出来事（BusinessDataChangedEvent → AuditEventType の BUSINESS_ROW_CREATED・BUSINESS_ROW_UPDATED）に、参照の確かめの拒否を載せる
owner: U2
event: BusinessDataChangedEvent        # 既存。新しい種類の出来事は足さない（機能設計で確かめる）
result: FAILURE                        # 既存の結果
reason: BUSINESS_REFERENCE_NOT_FOUND   # 既存の監査の理由（BusinessDataAuditReason）。要件 FR3.2a の「REFERENCE_NOT_FOUND」はこの理由を指す
rules:
  - "確定の後に別の短いトランザクションで追記する既存の仕組みのまま（保存しない拒否も、既存の拒否と同じく記録する）"
  - "監査の行に参照先の行の値・ラベル・主キーの値を含めない"
  - "確かめの読み取りそのもの（選択肢・ピッカーの検索・ラベル）は記録しない（US3.3、FR3.4）"
```

## C5 TargetDb → U1・U2（目的ごとの時間の上限）

```yaml
# shared-schema（targetdb.domain の口。名前は仮で、機能設計・NFR 設計で決める）
owner: U1
ReadPurpose:
  existing: [GENERATE, COMPARE, LIST, DETAIL]
  add:
    - name: "REFERENCE（仮）"
      use: "REFERENCE の選択肢・LOOKUP の検索・ラベルの1件読み・保存前の確かめの読み取り"
      timeout: "TargetDbProperties.QueryTimeout に足す（値は NFR 設計。既存の LIST・DETAIL の既定 5 秒が目安）"
rules:
  - "1要求で借りる読み取りの接続は1本まで。定義の取得で REFERENCE が複数あっても同じ接続で順に読む（NFR3、AC1.1.5）"
  - "内部DB の接続を持ったまま対象DB を待たない"
  - "targetdb の境界（書き込み・DDL を発行しない、JdbcTemplate を使わない、DSL と web を知らない）は変えない"
```

## C6 RoleManagement → U1・U2（権限の解決の口の使い方）

```yaml
# shared-schema（既存の口、形を変えない）
owner: 既存（role）
use:
  - "1要求で EffectivePermissionResolver の写し（PermissionSnapshot）を1回だけ取り、参照元と参照先の両方の判定に同じ写しを使う"
  - "RoleBoundaryArchitectureTest の依存してよい相手は足さない（businessdata は既に許されている）"
```

## C7 U4 → U5（JWT の伏せ字の道具の呼び方）

```yaml
# コマンドの約束（Node の1ファイル、依存なし。置き場と名前はコード生成で決める）
owner: U4
command: "node perf/<伏せ字の道具>.mjs <入力の summary json> <出力の evidence/ の中のパス>"
input: "k6 の --summary-export の json（JWT の形が入りうる）"
output: "JWT の形（eyJ で始まる base64url の 3 区間）を [REDACTED-JWT] に置き換えた json。キーと型の構造は入力と同じ"
exit:
  "0": "置き換えて、残存の検査も通り、出力を書いた"
  "非 0": "入力が JSON でない・空、残存の検査で JWT の形が見つかった、キーの置き換えで衝突した。出力のファイルも一時ファイルも残さない（AC4.1.3・AC4.1.4）"
readme: "perf/README.md に、記録を evidence/ に置く唯一の入口がこの道具であること、伏せる対象（summary json）と対象外（k6 の標準出力とログ、伏せる手順）を書く節を U4 が置き、U5 が整理した README に収める"
```

## 契約の持ち主の決まり

- 各契約の形は、持ち主の単位が変える。使う単位に要る変更は、持ち主の単位の機能設計で決める。
- 足す変更（欄・任意の項目・列挙の値）は、使う側が知らない欄を読み落としても壊れない形にする（NFR7）。`purpose` の既定は `VIEW`、`reference` は参照でないカラムでは null。
- 壊す変更（欄の名前・型・code・状態コードの変更）はしない。code は一度決めたら変えず、1 つの code に 1 つの状態コードを固定する（`team.md`）。
- 誤りの応答は Problem Details の形で、内部の例外のメッセージ・参照先の値・接続情報を含めない（`project.md` の Forbidden）。

## Open questions

| Contract | Question | Blocks |
|---|---|---|
| C2 | LOOKUP の行の `label` に使うカラム（`lookupList` の最初のカラムか、DSL の `labelColumn` があればそれか） | U1・U3 の機能設計 |
| C2 | 検索の誤りのうち、どれを 400 にし、どれを 403（同じ応答）に含めるかの一覧と、ページが範囲外のときの応答（AC2.1.3） | U1 の機能設計 |
| C2 | 参照先が複合の主キー・主キーの無いテーブル・ビュー・自己参照、`valueColumn` が一意でないときの扱い（AC1.4.4） | U1 の機能設計 |
| C5 | `ReadPurpose` に足す目的の名前と時間の上限の値 | U1 の機能設計・NFR 設計 |
| C2 | 選択肢の読み取りの失敗で定義の取得ごと 503 になる（Q5: A）とき、対象DB の不調の間は作成・更新の画面全体が開けない。その画面の失敗の表示（文言・やり直しの操作） | U3 の機能設計 |
| C7 | 伏せ字の道具のファイルの名前と置き場、`frontend/` の検査の対象に入れる方法（ADR-006） | U4 のコード生成 |

## Sources

- 単位と依存: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/units-generation/unit-of-work.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/units-generation/unit-of-work-dependency.md`
- 部品と決定: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/domain-design/components.md`・`decisions.md`
- 要件: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`（FR1〜FR5・NFR1・NFR3・NFR6・NFR7）
- ストーリー: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/user-stories/stories.md`（AC2.1.5・AC2.1.11・AC3.4.2・AC1.2.2、X1・X4・X9・X13）
- 質問と答え: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-design-questions.md`（Q1〜Q5: A）
- 既存のコード: `backend/src/main/java/cherry/mastersmith/businessdata/web/`（`BusinessDataPaths`・`DefinitionRequest`・`ColumnDefinitionResponse`・`OptionResponse`）、`businessdata/domain/`（`BusinessDataProblemTypes`・`BusinessDataFieldErrorReason`・`BusinessDataAuditReason`）、`targetdb/domain/ReadPurpose`、`dsl/domain/OptionSource`

## Assumptions & Open Questions

- [assumption] 参照先の検索 API の道は `/api/tables/reference/rows` とする（前の Intent の道の決まり `BASE = /api/tables` に合わせた）。名前は機能設計で変えてよい。
- [assumption] DSL が適用されていないときも、参照先の検索 API は 403 の同じ応答にする（定義の API の 404 とは揃えない）。
- 未解決の点は上の「Open questions」の表のとおり。
