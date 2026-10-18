# エンティティ — U2 u2-businessdata-write

**内部DB に業務データの表は持たない。** 業務データの行は対象DB のデータで、アプリが独自に持つデータではないため、エンティティにしない（`project.md` の学び）。この単位が内部DB に足すのは、既存の監査の表 `audit_events` の列だけである（下の `BusinessDataAuditRow`。Q4: A）。

ここに書くのは、要求1件の中だけで作って使う値の型と、監査の行の欄である。接続のプール・トランザクションの管理者・トレース・ログの1件など、フレームワークの仕組みはエンティティにせず、`rules.md` の決まりとして書く。

出典: 契約 C1・C2・C3・C5・C6（`aidlc/spaces/default/intents/261016-business-data-views/inception/contract-design/contract-summary.md`）、ADR-003・ADR-005・ADR-008、U1 の `entities.md`（`aidlc/spaces/default/intents/261016-business-data-views/construction/u1-businessdata-read/functional-design/entities.md`）、この段の答え `functional-design-questions.md`（Q1〜Q8: A、承認の場の Request Changes の R-01〜R-03 と U1 R-03・R-05）。型の名前は既存のコードの書き方にそろえた案で、コード生成で変えるときは差を記録する。

承認の場（2026-10-18）の直しでこの文書に入れたもの:

- R-01: 監査の理由の値を、応答の code とは別の列挙（`BusinessDataAuditReason` の値を `AuditFailureReason` に足す）として書き、すべて 32 字以内にした（`BUSINESS_ROW_OP_NOT_ALLOWED`）。
- R-02: 定義の API のカラムの項目（契約 C2 の `ColumnDefinition`）に U2 が足す検証の材料を `ColumnDefinitionWriteItems` として書いた。U1 の型に置いた余地を使う。
- U1 R-05: 名前の一覧はスキーマ・テーブル・カラムの種類ごとに照らす（`WriteRequest` の制約）。

```yaml
entities:
  - name: WriteRequest
    description: "作成・更新・削除の要求1件（契約 C3 を U1 の X1 の形に直したもの）。スキーマ名・テーブル名・主キー・値はすべて本文で運ぶ。文字列にするときは key と values を伏せる（BR12.1）"
    persisted: false
    attributes:
      - { name: schema, type: string, required: true, constraints: "写しの DSL のスキーマの一覧と文字どおり照らす（BR1.2）" }
      - { name: table, type: string, required: true, constraints: "写しの DSL のそのスキーマのテーブルの一覧と文字どおり照らす（BR1.2）" }
      - { name: definitionId, type: DefinitionId, required: true, constraints: "U1 の型を使い回す。今の定義と違えば BUSINESS_DEFINITION_CHANGED（BR1.5）" }
      - { name: operation, type: enum, required: true, allowed_values: [CREATE, UPDATE, DELETE], constraints: "道から決める（本文からは受け取らない）" }
      - { name: key, type: RowKey, required: false, constraints: "U1 の型を使い回す。更新・削除で必須、作成では無し" }
      - { name: values, type: WriteValues, required: false, constraints: "作成・更新で必須。削除では無し" }
    constraints:
      - "作成の values は空でもよい（すべて DB の既定値に任せる。ただし BR3.3 の必須を先に当てる）。更新の values が空なら 400（BR2.4）"
      - "名前は種類ごとの一覧（スキーマ・テーブル・カラム）と別々に照らす（U1 R-05 を直した後の形）"
    relationships:
      - { to: WriteValues, cardinality: "0..1", direction: "WriteRequest → WriteValues" }

  - name: WriteValues
    description: "画面が送ったカラムと値の組。値は文字か null。文字列にするときは値を伏せる（BR12.1）"
    persisted: false
    attributes:
      - { name: values, type: "map<string, string|null>", required: true, constraints: "作成は入れた FULL のカラムだけ、更新は変えたカラムだけ（BR2.2・BR2.3）" }
    constraints:
      - "カラムは ColumnAccess.editableOnCreate・editableOnUpdate が真のものだけ。ほかは 400 NOT_ALLOWED（BR2.1）"
    relationships:
      - { to: TypedValue, cardinality: "1..n", direction: "WriteValues → TypedValue（検証の後に写す）" }

  - name: TypedValue
    description: "検証を通ったカラム1つの、型つきの値（Q3: A）。ColumnValueCodec だけが作る。文字列にするときは値を伏せる"
    persisted: false
    attributes:
      - { name: column, type: string, required: true }
      - { name: valueType, type: enum, required: true, allowed_values: [STRING, INTEGER, BIG_INTEGER, DECIMAL, BOOLEAN, DATE, DATETIME, DATETIME_TZ, TIME, TIME_TZ], constraints: "U1 の valueType のうち書き込みに使うもの。BINARY・OTHER は作らない（BR2.1）" }
      - { name: value, type: "typed|null", required: false, constraints: "数は桁を保つ形、日付・日時は日時の型、真偽値は真偽値。時差つきの型は UTC（BR3.8）" }
    constraints: []
    relationships: []

  - name: ColumnWriteRules
    description: "カラム1つの検証の材料（サーバーの中だけで使う全体）。写しの DSL と DB の型から作り、サーバーの検証（BR3・BR4・BR5）と、定義の API に足す項目（ColumnDefinitionWriteItems）の両方の元にする（BR14.2）"
    persisted: false
    attributes:
      - { name: column, type: string, required: true }
      - { name: formPart, type: enum, required: true, allowed_values: [text, textarea, checkbox, switch, radio, select, date, datetime, number, password], constraints: "lookup は text として扱う（FR6.2）" }
      - { name: nullable, type: boolean, required: true, constraints: "DSL の dbType.nullable" }
      - { name: required, type: boolean, required: true, constraints: "DSL の required" }
      - { name: requiredOrigin, type: enum, required: false, allowed_values: [DB, MANUAL], constraints: "作成の空の扱いに使う（BR3.3）。定義の API には出さない" }
      - { name: maxLength, type: integer, required: false, constraints: "DSL の maxLength と DB の長さの小さい方（コードポイント）" }
      - { name: minLength, type: integer, required: false }
      - { name: precision, type: integer, required: false, constraints: "DECIMAL の全体の桁" }
      - { name: scale, type: integer, required: false, constraints: "DECIMAL の小数の桁" }
      - { name: min, type: string, required: false, constraints: "数・日付・時刻の型だけに当てる（BR3.6）" }
      - { name: max, type: string, required: false }
      - { name: pattern, type: string, required: false, constraints: "文字の型だけに当てる。式は応答に出さない（BR4・BR14.2）" }
      - { name: patternMessage, type: DisplayName, required: false, constraints: "DSL の message（ja・en）。誤りの fieldErrors にだけ付ける" }
      - { name: unique, type: boolean, required: true, constraints: "DSL の unique（BR5）。定義の API には出さない" }
      - { name: choices, type: "list<Option>", required: false, constraints: "FIXED の選択肢だけ（select・radio）。Option は契約 C2 の value・label" }
      - { name: masked, type: boolean, required: true, constraints: "formPart が password" }
    constraints:
      - "型に合わない DSL の検証は当てない（BR3.6 の表）"
    relationships:
      - { to: ColumnDefinitionWriteItems, cardinality: "1", direction: "ColumnWriteRules → ColumnDefinitionWriteItems（応答に出してよい項目だけを写す）" }

  - name: ColumnDefinitionWriteItems
    description: "定義の API（POST /api/tables/definition）のカラムの項目（契約 C2 の ColumnDefinition）に U2 が足す検証の材料（R-02）。U1 の型に置いた余地に入れ、画面（U3）はここから読む（U3 Q1: A）。値は ColumnWriteRules から写すだけで、別の計算をしない"
    persisted: false
    attributes:
      - { name: formPart, type: enum, required: true, allowed_values: [text, textarea, checkbox, switch, radio, select, date, datetime, number, password], constraints: "ColumnWriteRules.formPart" }
      - { name: required, type: boolean, required: true }
      - { name: nullable, type: boolean, required: true, constraints: "U1 が読み取りの項目として返す nullable と同じ値（重ねて持たない）" }
      - { name: minLength, type: "integer|null", required: false, constraints: "STRING だけ。ほかの型は null" }
      - { name: maxLength, type: "integer|null", required: false, constraints: "STRING だけ" }
      - { name: precision, type: "integer|null", required: false, constraints: "DECIMAL だけ" }
      - { name: scale, type: "integer|null", required: false, constraints: "DECIMAL だけ" }
      - { name: min, type: "string|null", required: false, constraints: "数・日付・時刻の型だけ。値の形は C2 の Value と同じ文字" }
      - { name: max, type: "string|null", required: false }
      - { name: choices, type: "list<Option>", required: false, constraints: "select・radio だけ。ほかは空" }
      - { name: hasPattern, type: boolean, required: true, constraints: "式そのものは返さない（SM4）" }
    constraints:
      - "作成でも更新でも書けないカラム（editableOnCreate・editableOnUpdate がどちらも偽）は、formPart 以外を既定（null・偽・空）にする"
      - "U1 が返す読み取りの項目（column・displayName・valueType・listFormat・width・editableOnCreate・editableOnUpdate・masked）は変えない"
    relationships: []

  - name: FieldViolation
    description: "カラム1つの誤り（fieldErrors の1件）。値は持たない（BR12.3）"
    persisted: false
    attributes:
      - { name: field, type: string, required: false, constraints: "要求に入っていたカラムの名前。フォーム全体の誤りでは空" }
      - { name: reason, type: enum, required: true, allowed_values: [NOT_ALLOWED, KEY_MISMATCH, INVALID_VALUE, REQUIRED, TOO_SHORT, TOO_LONG, OUT_OF_RANGE, TOO_MANY_DIGITS, NOT_A_CHOICE, PATTERN_MISMATCH, PATTERN_TIMEOUT, DUPLICATE, REFERENCE_NOT_FOUND, NOT_NULL, NOT_EDITABLE_NOT_NULL, NO_CHANGE], constraints: "NOT_ALLOWED・KEY_MISMATCH・INVALID_VALUE・NOT_A_CHOICE は U1 と同じ値" }
      - { name: message, type: DisplayName, required: false, constraints: "PATTERN_MISMATCH で DSL に message があるときだけ" }
    constraints: []
    relationships: []

  - name: WriteOutcome
    description: "業務処理の層の結果の型（team.md の結果の型。sealed）。画面入出力の層が場合を尽くして応答か BusinessException に変える"
    persisted: false
    attributes:
      - { name: kind, type: enum, required: true, allowed_values: [CREATED, UPDATED, DELETED, REJECTED] }
      - { name: createdKey, type: RowKey, required: false, constraints: "CREATED で、DETAIL が許されるときだけ" }
      - { name: problemType, type: string, required: false, constraints: "REJECTED の応答の code（BR1.9）" }
      - { name: violations, type: "list<FieldViolation>", required: false }
    constraints: []
    relationships:
      - { to: FieldViolation, cardinality: "0..n", direction: "WriteOutcome → FieldViolation" }

  - name: BusinessDataChangedEvent
    description: "業務データの操作の出来事（契約 C5。businessdata.domain）。対象DB の口から戻った後に出す（BR11.1）。文字列にするときは key を伏せる"
    persisted: false
    attributes:
      - { name: actorUserId, type: integer, required: true }
      - { name: workRoleId, type: integer, required: false, constraints: "作業ロールが無ければ null" }
      - { name: schema, type: string, required: true }
      - { name: table, type: string, required: true }
      - { name: operation, type: enum, required: true, allowed_values: [CREATE, UPDATE, DELETE] }
      - { name: result, type: enum, required: true, allowed_values: [SUCCESS, FAILURE], constraints: "既存の AuditResult" }
      - { name: failureReason, type: enum, required: false, allowed_values: [BUSINESS_ROW_OP_NOT_ALLOWED, BUSINESS_ROW_NOT_FOUND, BUSINESS_ROW_NOT_UNIQUE, BUSINESS_DUPLICATE_VALUE, BUSINESS_REFERENCE_NOT_FOUND, BUSINESS_SAVE_REJECTED, BUSINESS_TARGET_DB_UNAVAILABLE, BUSINESS_TARGET_DB_TIMEOUT, BUSINESS_TARGET_DB_MISMATCH], constraints: "FAILURE のときだけ。応答の code とは別の列挙（監査の理由の値）。応答の code からの写しは BR11.6 の表の1か所で決める。どれも 32 字以内" }
      - { name: key, type: RowKey, required: false, constraints: "主キーの無いテーブルの作成・主キーの決まらない作成の失敗では無し" }
      - { name: sourceIp, type: string, required: true }
      - { name: userAgent, type: string, required: false }
      - { name: requestPath, type: string, required: false, constraints: "道は固定のため値を含まない" }
      - { name: traceId, type: string, required: false }
    constraints:
      - "主キー以外の値・更新の前後の値・JDBC の例外のメッセージ・接続先を持たない（FR12.2・AC3.4.8）"
    relationships:
      - { to: BusinessDataAuditRow, cardinality: "1", direction: "BusinessDataChangedEvent → BusinessDataAuditRow（AuditEventListener が写す）" }

  - name: BusinessDataAuditRow
    description: "内部DB の既存の表 audit_events の1行のうち、業務データの出来事が使う欄（Q4: A）。列を足すのは target_schema・target_table・work_role_id の3つだけ"
    persisted: true
    attributes:
      - { name: event_type, type: enum, required: true, allowed_values: [BUSINESS_ROW_CREATED, BUSINESS_ROW_UPDATED, BUSINESS_ROW_DELETED], constraints: "既存の列 VARCHAR(32)。3つとも 20 字" }
      - { name: result, type: enum, required: true, allowed_values: [SUCCESS, FAILURE], constraints: "既存の列" }
      - { name: failure_reason, type: enum, required: false, allowed_values: [BUSINESS_ROW_OP_NOT_ALLOWED, BUSINESS_ROW_NOT_FOUND, BUSINESS_ROW_NOT_UNIQUE, BUSINESS_DUPLICATE_VALUE, BUSINESS_REFERENCE_NOT_FOUND, BUSINESS_SAVE_REJECTED, BUSINESS_TARGET_DB_UNAVAILABLE, BUSINESS_TARGET_DB_TIMEOUT, BUSINESS_TARGET_DB_MISMATCH], constraints: "既存の列 VARCHAR(32)（V4、AuditEvent の @Column length = 32、@Enumerated(STRING)）。AuditFailureReason に9つの値を足す。最も長い BUSINESS_TARGET_DB_UNAVAILABLE で 30 字（BR11.6 の字数の表）。列は広げない" }
      - { name: actor_user_id, type: integer, required: true, constraints: "既存の列" }
      - { name: occurred_at, type: datetime, required: true, constraints: "既存の列（UTC の時点）" }
      - { name: source_ip, type: string, required: true, constraints: "既存の列" }
      - { name: user_agent, type: string, required: false, constraints: "既存の列" }
      - { name: request_path, type: string, required: false, constraints: "既存の列" }
      - { name: trace_id, type: string, required: false, constraints: "既存の列" }
      - { name: target_schema, type: string, required: false, max: 255, constraints: "足す列。空を許し、既定値・参照の制約・索引を置かない（V6・V7・V10 と同じ形）" }
      - { name: target_table, type: string, required: false, max: 255, constraints: "足す列。同上" }
      - { name: work_role_id, type: integer, required: false, constraints: "足す列。同上。作業ロールが無ければ空" }
      - { name: detail, type: string, required: false, max: 16384, constraints: "既存の列。{key: {カラム: 値}, keyTruncated: 真偽値} の JSON。上限を超えたら値を切り、keyTruncated を真にする（BR11.4）" }
    constraints:
      - "業務データの出来事は entered_email・dsl_hash・dsl_source・rejection_kind・target_user_id・target_invitation_id・target_role_id・target_group_id を使わない"
      - "追記だけ（既存の監査の境界テストのとおり、更新・削除の口を持たない）"
    relationships: []
```

## 応答の code と監査の理由の値（R-01）

応答の code（`BusinessDataProblemTypes`）と監査の理由の値（`AuditFailureReason` に足す値）は別の列挙である。名前が同じものが多いが、同じ文字列であることを前提にしない。写しは `rules.md` の BR11.6 の表の1か所で決める。

| 応答の code | 字数 | 監査の理由の値 | 字数 |
|---|---|---|---|
| `BUSINESS_ROW_OPERATION_NOT_ALLOWED` | 34 | `BUSINESS_ROW_OP_NOT_ALLOWED` | 27 |
| `BUSINESS_ROW_NOT_FOUND` | 22 | `BUSINESS_ROW_NOT_FOUND` | 22 |
| `BUSINESS_ROW_NOT_UNIQUE` | 23 | `BUSINESS_ROW_NOT_UNIQUE` | 23 |
| `BUSINESS_DUPLICATE_VALUE` | 24 | `BUSINESS_DUPLICATE_VALUE` | 24 |
| `BUSINESS_REFERENCE_NOT_FOUND` | 28 | `BUSINESS_REFERENCE_NOT_FOUND` | 28 |
| `BUSINESS_SAVE_REJECTED` | 22 | `BUSINESS_SAVE_REJECTED` | 22 |
| `BUSINESS_TARGET_DB_UNAVAILABLE` | 30 | `BUSINESS_TARGET_DB_UNAVAILABLE` | 30 |
| `BUSINESS_TARGET_DB_TIMEOUT` | 26 | `BUSINESS_TARGET_DB_TIMEOUT` | 26 |
| `BUSINESS_TARGET_DB_MISMATCH` | 27 | `BUSINESS_TARGET_DB_MISMATCH` | 27 |

既存の `AuditFailureReason` の28の値で最も長いのは `PERMISSION_TARGET_NOT_IN_DSL`（28 字）である（全体の表は `rules.md` の BR11.6 の後）。

## 要約

- **内部DB に業務データの表は無い。** 内部DB に足すのは、既存の監査の表 `audit_events` の3つの列（`target_schema`・`target_table`・`work_role_id`）だけである。主キーの値は既存の `detail` の列に JSON で置く。`failure_reason` の列は広げず、足す値をすべて 32 字以内にする（R-01）。
- **U1 の型を使い回すもの**: `DefinitionId`・`TableAccessView`・`ColumnAccess`・`RowKey`・`AllowedOperation` は U1 の `entities.md` のまま使う。
- **定義の API に足すもの（R-02）**: `ColumnDefinitionWriteItems`（`formPart`・`required`・`nullable`・`minLength`・`maxLength`・`precision`・`scale`・`min`・`max`・`choices`・`hasPattern`）を、U1 の型に置いた余地に U2（B4）が入れる。元はサーバーの検証と同じ `ColumnWriteRules` である。
- **U2 で足す型**:
  - 要求の型: `WriteRequest`・`WriteValues`。
  - 値と誤りの型: `TypedValue`（型つきの値）・`FieldViolation`（カラムごとの誤り）・`WriteOutcome`（結果の型）。
  - 検証の材料: `ColumnWriteRules`（サーバーの中）・`ColumnDefinitionWriteItems`（応答に出す分）。
  - 出来事の型: `BusinessDataChangedEvent`。
- 行の値・主キーの値を持つ型（`WriteRequest`・`WriteValues`・`TypedValue`・`BusinessDataChangedEvent`）は、文字列にするときに必ず値を伏せる。
