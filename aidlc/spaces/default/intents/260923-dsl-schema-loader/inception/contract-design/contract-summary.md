# Contract Summary — dsl-schema-loader

単位の間のつなぎ目（`inception/units-generation/unit-of-work-dependency.md` の依存の6本）と、後続の Intent に渡す提供口、既存の監査への出来事の形を決める。確定した答えは `contract-design-questions.md` の Q1〜Q5。部品と持ち物は `inception/domain-design/components.md`、判断は `inception/domain-design/decisions.md`。

外部（アプリの外）に公開する API は無い。画面（U5）と API（U4）は同じアプリの中で、管理者だけが使う。

## 共通の決まり

- **アプリの中の部品の間（C1〜C5・C8）**: Java のインターフェースと `record` で受け渡す。想定内の失敗（対象DB の設定が無い・接続できない・応答しない、DSL の検証を通らない）は、成功か失敗かを表す結果の型（`sealed interface`）で返す。想定外の失敗だけを例外にする（Q5: A）。結果の中の文言に、接続先・内部の例外のメッセージを含めない（NFR4・NFR5）。
- **HTTP（C6）**: 管理者だけの `/api/admin/dsl/` の下。版は URL に入れない（Q4: A）。エラーは既存の Problem Details（`type`・`title`・`status`・`detail`・`instance`・`code`・`traceId`）で、`code` は機能ごとの一覧（`ProblemTypeCatalog`）に日英の説明つきで登録する。既存の仕組みのとおり、**1つの `code` の状態コードは1つだけ**とする（同じ `code` を別の状態コードで使わない）。未認証 401・管理者でない 403 は既存の AccessControl がそのまま返す。
- **結果の型から応答への変換の経路**: U4 の業務処理（service）の層が、C1・C4・C5 の結果の型を見て、想定内の失敗を業務の例外（既存の `BusinessException`、誤りの一覧を持つものはその派生の例外）に変えて投げる。応答は既存の共通の変換（`@RestControllerAdvice` の1か所）が作り、個々のコントローラーでは組み立てない（`aidlc/spaces/default/memory/team.md` の Code Style）。照合できない（`TARGET_DB_*`）ことは失敗にせず、プレビューの `warnings` に入れる。
- **共通のエラー応答の拡張（U4 に含む）**: 既存の `common/error` に、業務の例外が Problem Details の追加の項目（例: `errors`・`total`）を持てる口を足す。案: `BusinessException` に「追加の項目の一覧」を持たせる（既定は空）か、追加の項目を返すインターフェースを業務の例外が実装し、共通の変換がそれを `ProblemDetail` の項目として載せる。既存の `code`・`traceId` の項目名は、追加の項目で上書きできない。どちらの形にするかは U4 の機能設計で決める。既存の応答の形は変えない。
- **アプリの中の出来事（C7）**: 既存の監査と同じく、Spring のアプリの中の出来事で渡し、AuditLog が確定の後に受けて記録する。
- **名前**: Java の型・パッケージの名前は案で、機能設計・コード生成で確定する（形と意味は変えない）。

## Contracts

| # | Provider Unit | Consumer | Mechanism | Owner |
|---|---|---|---|---|
| C1 | U1 対象DB | U3 既定の DSL の生成 | アプリの中の呼び出し（Java インターフェース） | U1 |
| C2 | U2 DSL の定義 | U3 既定の DSL の生成 | アプリの中の呼び出し（Java インターフェース） | U2 |
| C3 | U1 対象DB | U4 DSL の管理 | アプリの中の呼び出し（C1 と同じインターフェース） | U1 |
| C4 | U2 DSL の定義 | U4 DSL の管理 | アプリの中の呼び出し（Java インターフェース） | U2 |
| C5 | U3 既定の DSL の生成 | U4 DSL の管理 | アプリの中の呼び出し（Java インターフェース） | U3 |
| C6 | U4 DSL の管理 | U5 DSL の管理画面 | HTTP（REST、JSON と YAML） | U4 |
| C7 | U4 DSL の管理 | 既存の AuditLog | アプリの中の出来事 | U4 |
| C8 | U2 DSL の定義 | External: 後続の Intent（I・J・K） | アプリの中の呼び出し（Java インターフェース、読み取りだけ） | U2 |

---

## C1・C3: 対象DB のメタデータの読み取り（U1 → U3・U4）

```yaml
# shared-schema: Java のインターフェース（パッケージ cherry.mastersmith.targetdb）
contract: TargetSchemaReader
owner: U1
operations:
  - name: readSchema
    input: なし（接続先とスキーマ名は mastersmith.target-db.* の設定だけから取る）
    output: TargetSchemaResult
    timeout: 接続・問い合わせの待ち時間の上限で打ち切る（数値は NFR 設計）
    side_effects: なし（読み取り専用の接続。スキーマ・データを変更しない）
types:
  TargetSchemaResult:
    kind: sealed interface
    variants:
      - Success: { schema: TargetSchema }
      - Unconfigured: { }                    # 設定が無い（必須の項目が欠けている・種類が対応外を含む）
      - Unavailable: { reason: TIMEOUT | CONNECTION_FAILED }   # 接続できない・応答しない
  TargetSchema:
    fields:
      databaseProduct: MYSQL | MARIADB | POSTGRESQL
      schemaName: string
      tables: list<TargetTable>              # テーブルとビュー。物理名の順は問わない（並べるのは U3）
  TargetTable:
    fields:
      name: string                           # 物理名
      view: boolean                          # ビューなら true
      comment: string | null
      columns: list<TargetColumn>            # 定義の順
      primaryKey: list<string>               # カラム名。無ければ空
      foreignKeys: list<TargetForeignKey>
  TargetColumn:
    fields:
      name: string
      dbType: { typeName: string, length: int | null, precision: int | null, scale: int | null }
      nullable: boolean
      defaultValue: string | null            # DB が返す既定値の式をそのまま
      comment: string | null
  TargetForeignKey:
    fields:
      name: string | null
      columns: list<string>
      referencedTable: string
      referencedColumns: list<string>
rules:
  - 接続先・ユーザー名・パスワードは、どの型にも含めない
  - Unavailable・Unconfigured に内部の例外のメッセージを含めない（ログにも値を出さない）
  - 想定外の失敗（プログラムの誤りなど）だけを例外にする
```

## C2: 生成した DSL の検証（U2 → U3）

C4 の `DslReader.read` を使う（U3 は生成した YAML の本文を検証し、通らなければ U3 の作りの誤りとして想定外の失敗にする）。書式の版は C4 の `DslFormat.CURRENT_VERSION` から得る。

## C4: DSL の読み込み・検証・識別・適用中のモデルの差し替え（U2 → U4）

```yaml
# shared-schema: Java のインターフェース（パッケージ cherry.mastersmith.dsl）
contract: DslReader / DslFormat / ActiveDslModelHolder
owner: U2
operations:
  - name: DslReader.read
    input: { yamlBytes: byte[] }             # UTF-8 の YAML の本文
    output: DslReadResult
    rules:
      - 大きさ（5MB）・入れ子の深さ・別名の数の上限、任意の型を作るタグの拒否、重複キーの誤りを、読み込みの時点で守る
      - 検証の順は、書式の版 → 構文（同梱の JSON Schema。外部の $ref を取りに行かない）→ 意味
      - 位置の対応表で、誤りに行・列を付ける（ADR-008）
  - name: DslReader.hash
    input: { yamlBytes: byte[] }
    output: { dslHash: string }              # 本文のバイト列のハッシュ値（アルゴリズムは NFR 設計。16進の文字列）
  - name: DslFormat.CURRENT_VERSION
    output: int                              # アプリが対応する書式の版（最初は 1）
  - name: ActiveDslModelHolder.replace
    input: { model: DslModel | null }        # null は「適用中の DSL が無い」
    rules:
      - 呼ぶのは U4 だけ（起動時と、適用の確定の後）
      - 読み手（C8）から見て一度に切り替わる
types:
  DslReadResult:
    kind: sealed interface
    variants:
      - Valid: { model: DslModel, dslHash: string }
      - Invalid: { errors: list<DslError> }  # 1件以上。総数に上限は設けない（絞るのは C6）
  DslError:
    fields:
      kind: SIZE_LIMIT | DEPTH_LIMIT | ALIAS_LIMIT | FORBIDDEN_TAG | DUPLICATE_KEY | UNSUPPORTED_VERSION | SYNTAX | SEMANTIC
      line: int | null                       # 1 から。位置が得られない誤り（大きさの上限など）は null
      column: int | null                     # 1 から
      path: string | null                    # DSL の中の場所（例: tables.dept_mst.columns.dept_name）
      messageKey: string                     # 文言の鍵（画面・応答で ja・en に引く）
      messageArgs: list<string>              # 文言に埋める値（DSL の中の名前など。部品の例外のメッセージは入れない）
  DslModel:
    note: >
      後続の Intent が使うモデル。メニュー（N 階層）・テーブル（ビューの印、表示名 ja・en）・カラム（DB 上の型、表示名、検索条件、
      一覧表示、詳細表示、バリデーションと DB から導いたかの印、フォーム部品、選択肢の出どころ、参照ピッカーの専用の設定）を持つ。
      変更できない（immutable）値の木とする。具体的な型は機能設計（U2）で決める。接続先は持たない。
```

## C5: 既定の DSL の生成（U3 → U4）

```yaml
# shared-schema: Java のインターフェース（パッケージ cherry.mastersmith.dslmanage）
contract: DefaultDslGenerator
owner: U3
operations:
  - name: generate
    input: なし（対象DB は C1 から読む）
    output: DefaultDslResult
types:
  DefaultDslResult:
    kind: sealed interface
    variants:
      - Generated: { yamlBytes: byte[], dslHash: string }   # C4 の検証を通った YAML の本文
      - TargetUnconfigured: { }
      - TargetUnavailable: { reason: TIMEOUT | CONNECTION_FAILED }
rules:
  - 生成した YAML に接続先の値を入れない
  - 保存しない（保存と監査は U4）
```

## C6: DSL の管理の API（U4 → U5）

```yaml
openapi: 3.1.0
info:
  title: MasterSmith DSL 管理 API（管理者だけ）
  version: "1"
servers:
  - url: /api/admin/dsl
components:
  securitySchemes:
    bearer: { type: http, scheme: bearer }   # 既存のアクセストークン。管理者でなければ 403（既存の AccessControl）
  schemas:
    ProblemDetail:
      type: object
      description: 既存の Problem Details。受け側は知らない項目を無視する
      properties:
        type: { type: string, format: uri }
        title: { type: string }
        status: { type: integer }
        detail: { type: string }
        instance: { type: string }
        code: { type: string, pattern: "^[A-Z][A-Z0-9_]*$" }
        traceId: { type: string }
    DslInvalidProblem:
      allOf:
        - $ref: "#/components/schemas/ProblemDetail"
        - type: object
          required: [errors, total]
          properties:
            total: { type: integer, minimum: 1, description: 誤りの総数 }
            errors:
              type: array
              maxItems: 100
              description: 先頭の100件まで（サーバーが絞る）
              items:
                type: object
                required: [kind, message]
                properties:
                  kind: { type: string, enum: [SIZE_LIMIT, DEPTH_LIMIT, ALIAS_LIMIT, FORBIDDEN_TAG, DUPLICATE_KEY, UNSUPPORTED_VERSION, SYNTAX, SEMANTIC] }
                  line: { type: [integer, "null"] }
                  column: { type: [integer, "null"] }
                  path: { type: [string, "null"] }
                  message: { type: string, description: 要求の表示言語（Accept-Language）の文言 }
    DslRef:
      type: object
      required: [dslHash, source, by, at]
      properties:
        dslHash: { type: string }
        source: { type: string, enum: [GENERATED, UPLOAD, PASTE, RESTORE] }
        by: { type: object, required: [userId, email], properties: { userId: { type: string }, email: { type: string } } }
        at: { type: string, format: date-time, description: UTC }
    DslStatus:
      type: object
      properties:
        applied: { oneOf: [ { $ref: "#/components/schemas/AppliedRef" }, { type: "null" } ] }
        preview: { oneOf: [ { $ref: "#/components/schemas/PreviewRef" }, { type: "null" } ] }
    AppliedRef:
      allOf:
        - $ref: "#/components/schemas/DslRef"
        - type: object
          required: [revisionId]
          properties: { revisionId: { type: string } }
    PreviewRef:
      allOf:
        - $ref: "#/components/schemas/DslRef"
        - type: object
          required: [previewId]
          properties: { previewId: { type: string, description: プレビューを置くたびに新しくなる識別 } }
    Preview:
      allOf:
        - $ref: "#/components/schemas/PreviewRef"
        - type: object
          required: [summary, diff, warnings]
          properties:
            summary:
              type: object
              properties:
                tableCount: { type: integer }
                viewCount: { type: integer }
                columnCount: { type: integer }
                menuTree: { type: array, items: { $ref: "#/components/schemas/MenuNode" } }
                missingDisplayNames:
                  type: array
                  items: { type: object, properties: { path: { type: string }, language: { type: string, enum: [ja, en] } } }
            diff:
              type: object
              properties:
                appliedExists: { type: boolean }
                tables:
                  type: array
                  items:
                    type: object
                    properties:
                      name: { type: string }
                      change: { type: string, enum: [ADDED, REMOVED, CHANGED, UNCHANGED] }
                      columns:
                        type: array
                        items:
                          type: object
                          properties:
                            name: { type: string }
                            change: { type: string, enum: [ADDED, REMOVED, CHANGED] }
                            changedItems: { type: array, items: { type: string } }
            warnings:
              type: array
              items:
                type: object
                properties:
                  kind: { type: string, enum: [TABLE_MISSING, COLUMN_MISSING, TYPE_MISMATCH, TARGET_UNCONFIGURED, TARGET_UNAVAILABLE] }
                  path: { type: [string, "null"] }
                  message: { type: string }
    MenuNode:
      type: object
      properties:
        label: { type: object, properties: { ja: { type: string }, en: { type: string } } }
        table: { type: [string, "null"] }
        children: { type: array, items: { $ref: "#/components/schemas/MenuNode" } }
    HistoryEntry:
      allOf:
        - $ref: "#/components/schemas/AppliedRef"
        - type: object
          properties: { current: { type: boolean, description: 今適用中の版なら true } }
paths:
  /status:
    get:
      summary: 今の状態（適用中とプレビュー）
      responses:
        "200": { content: { application/json: { schema: { $ref: "#/components/schemas/DslStatus" } } } }
  /preview:
    get:
      summary: プレビューの中身（要約・違い・照合の警告）
      responses:
        "200": { content: { application/json: { schema: { $ref: "#/components/schemas/Preview" } } } }
        "404": { description: "プレビューが無い（code: DSL_PREVIEW_NOT_FOUND）", content: { application/problem+json: { schema: { $ref: "#/components/schemas/ProblemDetail" } } } }
    post:
      summary: 投入（アップロード・貼り付け）。検証を通れば今のプレビューを置き換える
      parameters:
        - { name: source, in: query, required: true, schema: { type: string, enum: [UPLOAD, PASTE] } }
      requestBody:
        required: true
        content:
          application/yaml:
            schema: { type: string, description: "YAML の本文（UTF-8）。5MB まで（本文のバイト数で数える）" }
      responses:
        "201": { content: { application/json: { schema: { $ref: "#/components/schemas/Preview" } } } }
        "413": { description: "5MB を超える（code: DSL_TOO_LARGE）", content: { application/problem+json: { schema: { $ref: "#/components/schemas/ProblemDetail" } } } }
        "415": { description: "application/yaml 以外", content: { application/problem+json: { schema: { $ref: "#/components/schemas/ProblemDetail" } } } }
        "422": { description: "検証を通らない（code: DSL_INVALID）。保存しない", content: { application/problem+json: { schema: { $ref: "#/components/schemas/DslInvalidProblem" } } } }
    delete:
      summary: プレビューの破棄
      responses:
        "204": { description: 破棄した }
        "404": { description: "プレビューが無い（code: DSL_PREVIEW_NOT_FOUND）" }
  /preview/generate:
    post:
      summary: スキーマの読み込み（既定の DSL を作り、今のプレビューを置き換える）
      responses:
        "201": { content: { application/json: { schema: { $ref: "#/components/schemas/Preview" } } } }
        "503": { description: "対象DB の設定が無い（code: TARGET_DB_UNCONFIGURED）・接続できない／応答しない（code: TARGET_DB_UNAVAILABLE）", content: { application/problem+json: { schema: { $ref: "#/components/schemas/ProblemDetail" } } } }
  /preview/download:
    get:
      summary: プレビュー中の DSL のダウンロード（保存した本文をそのまま）
      responses:
        "200":
          headers: { Content-Disposition: { description: "attachment。ファイル名でプレビュー中と識別が分かる（形は機能設計）" } }
          content: { application/yaml: { schema: { type: string } } }
        "404": { description: "プレビューが無い（code: DSL_PREVIEW_NOT_FOUND）" }
  /apply:
    post:
      summary: 適用（見たプレビューを指定する）
      requestBody:
        required: true
        content:
          application/json:
            schema: { type: object, required: [previewId], properties: { previewId: { type: string } } }
      responses:
        "200": { content: { application/json: { schema: { $ref: "#/components/schemas/DslStatus" } } } }
        "409": { description: "指定したプレビューが今のプレビューと違う、またはプレビューが無い（どちらも code: DSL_PREVIEW_CHANGED）。適用中の DSL は変わらない", content: { application/problem+json: { schema: { $ref: "#/components/schemas/ProblemDetail" } } } }
  /applied/download:
    get:
      summary: 適用中の DSL のダウンロード
      responses:
        "200": { content: { application/yaml: { schema: { type: string } } } }
        "404": { description: "適用中の DSL が無い（code: DSL_APPLIED_NOT_FOUND）" }
  /history:
    get:
      summary: 適用の履歴（新しい順、最大20件）
      responses:
        "200": { content: { application/json: { schema: { type: array, items: { $ref: "#/components/schemas/HistoryEntry" } } } } }
  /history/{revisionId}/restore:
    post:
      summary: 履歴の版をプレビューに戻す（検証と照合を行い、今のプレビューを置き換える）
      parameters:
        - { name: revisionId, in: path, required: true, schema: { type: string } }
      responses:
        "201": { content: { application/json: { schema: { $ref: "#/components/schemas/Preview" } } } }
        "404": { description: "その版が無い（code: DSL_REVISION_NOT_FOUND）" }
        "422": { description: "今の検証を通らない（code: DSL_INVALID）", content: { application/problem+json: { schema: { $ref: "#/components/schemas/DslInvalidProblem" } } } }
```

新しい `code` の一覧（U4 の `ProblemTypeCatalog` に日英の説明つきで登録する）:

| code | 状態 | どんなとき |
|---|---|---|
| `DSL_INVALID` | 422 | 構文・意味・版・危険な形のどれかで検証を通らない（`errors` の `kind` で理由を分ける） |
| `DSL_TOO_LARGE` | 413 | 本文が 5MB を超える（読む前に止める） |
| `DSL_PREVIEW_NOT_FOUND` | 404 | プレビューが無い（取得・破棄・ダウンロード） |
| `DSL_PREVIEW_CHANGED` | 409 | 適用で指定したプレビューが、今のプレビューと違う、または無くなっている（置き換え・破棄のどちらも。適用の拒否はこの1つ） |
| `DSL_APPLIED_NOT_FOUND` | 404 | 適用中の DSL が無い（ダウンロード） |
| `DSL_REVISION_NOT_FOUND` | 404 | 履歴に指定の版が無い（件数の上限で消えた場合を含む） |
| `TARGET_DB_UNCONFIGURED` | 503 | 対象DB の接続先の設定が無い（スキーマの読み込み） |
| `TARGET_DB_UNAVAILABLE` | 503 | 対象DB に接続できない・応答しない（スキーマの読み込み） |

- 1つの `code` の状態コードは1つだけ（上の表のとおり）。画面は、適用の 409（`DSL_PREVIEW_CHANGED`）を受けたら今の状態を読み直し、プレビューが置き換わったか破棄されたかを今の状態で見分けて表示する（mockups.md の 3.6）。
- DSL の投入の API（`POST /preview`）だけ、要求の本文の上限を 5MB にする。ほかの API は今までどおり 1MB で 413（既存の `code`）。
- 照合できないとき（`TARGET_DB_*`）は、プレビューの応答の `warnings` に入れ、失敗にはしない（ストーリーの AC3.2.4）。
- 誤りの文言（`message`）は要求の表示言語で返す。部品の例外のメッセージと接続先は入れない。

## C7: DSL の操作の出来事（U4 → 既存の AuditLog）

```yaml
asyncapi: 3.0.0
info:
  title: DSL の操作の出来事（アプリの中）
  version: "1"
channels:
  dslOperation:
    description: >
      Spring のアプリの中の出来事。DslLifecycle が発行し、AuditLog が元の操作の確定の後（トランザクションの完了の後）に受けて記録する。
      書き込みに失敗しても元の操作は失敗させない（既存の監査の決まり）。巻き戻った操作の出来事は記録しない。
    messages:
      DslOperationEvent:
        payload:
          type: object
          required: [type, actorUserId, occurredAt]
          properties:
            type: { type: string, enum: [DSL_GENERATED, DSL_SUBMITTED, DSL_SUBMISSION_REJECTED, DSL_APPLIED, DSL_PREVIEW_DISCARDED] }
            actorUserId: { type: string }
            occurredAt: { type: string, format: date-time, description: UTC }
            dslHash: { type: [string, "null"], description: 受け付けなかった投入で本文を読めなかったときは null }
            source: { type: [string, "null"], enum: [GENERATED, UPLOAD, PASTE, RESTORE, null] }
            rejectionKind: { type: [string, "null"], description: "受け付けなかった投入の理由の種類（DslError の kind の代表、または TOO_LARGE）" }
            traceId: { type: [string, "null"] }
operations:
  publishDslOperation: { action: send, channel: { $ref: "#/channels/dslOperation" } }
  recordDslOperation: { action: receive, channel: { $ref: "#/channels/dslOperation" } }
```

- 出来事にも、監査の記録にも、DSL の本文と対象DB の接続先を入れない。
- 監査の表（`audit_events`）には、操作した人（`actorUserId`）・`dslHash`・`source`・`rejectionKind` を入れる列を Flyway の移行で足す（U4）。

## C8: 適用中のモデルの提供口（U2 → 後続の Intent）

```yaml
# shared-schema: Java のインターフェース（パッケージ cherry.mastersmith.dsl）
contract: ActiveDslModelProvider
owner: U2
operations:
  - name: current
    input: なし
    output: ActiveDsl
types:
  ActiveDsl:
    kind: sealed interface
    variants:
      - Present: { model: DslModel, dslHash: string }
      - Absent: { }                          # 適用中の DSL が無い（初めての導入、または起動の途中）
rules:
  - 読み取りだけ。差し替えは C4 の ActiveDslModelHolder を通して U4 だけが行う
  - 呼ぶたびに、その時点の適用中のモデルを返す（途中の状態を返さない）
  - DslModel は変更できない値で、取り出した側が持ち続けても、差し替えの影響を受けない
```

## 契約の持ち主と変え方の決まり

- 各契約の持ち主は、表の Owner の単位。持ち主が形を定め、変えるときは持ち主の単位の設計（機能設計）で決める。
- すべての契約は同じリポジトリ・同じ WAR にあるため、形を壊す変更（項目の削除・意味の変更・`code` の変更）は、提供側と利用側を同じ変更で直す。`code` は一度決めたら変えない（既存の決まり）。
- 項目の追加は安全な変更とする。受け側（画面・利用側の Java）は知らない項目を無視する。結果の型（`sealed interface`）に種類を足すのは形を壊す変更として扱う（利用側の場合分けを直す）。
- C8 は後続の Intent の土台なので、`DslModel` の形を壊す変更は、後続の Intent の着手の後は避ける（必要なら依頼者の判断を得る）。

## Open questions

| Contract | Question | Blocks |
|---|---|---|
| C1・C3・C6 | 対象DB の接続と問い合わせの待ち時間の数値（10 秒・30 秒の目標の内に収める） | U1 |
| C4 | DSL の識別のハッシュのアルゴリズム（例: SHA-256）と表し方 | U2 |
| C4・C8 | `DslModel` の具体的な型（キーの名前・入れ子）と、DB 上の型の表し方（3種類の DB の型の名前をそのままか共通の分類か） | U2・U3 |
| C4・C6 | 構文と意味の誤りが両方あるときの返し方（構文の誤りがあれば意味の検証をしない、など）と、別名で参照された値の行・列の扱い | U2 |
| C6 | ダウンロードのファイル名の形、`missingDisplayNames` の件数が多いときの絞り方、「変わった」に数える項目の一覧 | U4・U5 |
| C6 | 投入の要求の本文を 5MB まで受ける作り（既存の本文の上限の仕組みとの組み合わせ）と、5MB を何バイトとするか | U4 |
| C6 | 共通のエラー応答に追加の項目を載せる口の形（`BusinessException` に持たせるか、インターフェースにするか） | U4 |
| C7 | `rejectionKind` の決め方（誤りが複数の種類にまたがるとき） | U4 |

## 改訂の記録

| 日付 | 内容 | 理由 |
|---|---|---|
| 2026-09-23 | `DSL_PREVIEW_NOT_FOUND` を 404 だけにし、適用でプレビューが無い場合を `DSL_PREVIEW_CHANGED`（409）にまとめた | レビューの指摘 R-01。既存の仕組みは1つの `code` に状態コードが1つで、同じ `code` の二重の登録は起動の失敗になる |
| 2026-09-23 | 結果の型から応答への変換の経路（U4 の業務処理の層で業務の例外に変える）と、共通のエラー応答に追加の項目を載せる口（U4 に含む）を書き足した | レビューの指摘 R-02。既存の仕組みは業務の例外から共通の1か所で応答を作り、追加の項目を載せる口が無い |
