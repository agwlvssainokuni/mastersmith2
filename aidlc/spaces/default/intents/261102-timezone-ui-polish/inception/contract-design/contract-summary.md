# 契約の概要（Contract Summary）— timezone-ui-polish

出典: 単位（`inception/units-generation/unit-of-work.md`・`unit-of-work-dependency.md`）、部品（`inception/domain-design/components.md`）、設計の決定（`decisions.md` の ADR-001〜009）、要件（`inception/requirements-analysis/requirements.md`）、質問と答え（`inception/contract-design/contract-design-questions.md` の Q1: A・Q2: A）、今の口（`GET/PUT /api/me/preferences`、`GET /api/me/navigation`、`/api/tables`。コード知識ベース `api-documentation.md`・`architecture.md` の K-94・K-97・K-101）。

この Intent は、アプリの中の単位の間の契約（画面の側の関数・フックの型）と、今ある口への「足すだけ」の変更、静的なファイル、上流（make-you-chic-ui）への依頼で成り立つ。外部の第三者が使う口は新しくは増えない。

## 決まっていること

- 仕組み: 画面の側の契約は TypeScript の型。サーバーの口は同期の REST（JSON、Problem Details）。非同期・イベントは無い。
- 版: API に版は付けない。応答に項目を足すだけで、受け取る側が知らない項目を無視する（前の版の画面が読んでも壊れない。NFR4.2）。
- 誤り: RFC 9457 Problem Details に安定した `code`。タイムゾーンの名前の誤りは既存の `VALIDATION_FAILED`（400）で、新しい `code` は足さない。
- IANA の名前の範囲: サーバーが受けるのは `ZoneId` が IANA の地域の名前として知っているものだけ。固定のオフセット表記（`+09:00`・`Z`）は受けない。

## 契約の一覧

| # | 提供する単位 | 使う側 | 仕組み | 持ち主 |
|---|---|---|---|---|
| C1 | U1 u1-time-zone-conversion | U2・U4（画面の側） | TypeScript の関数の型（同じアプリの中） | U1 |
| C2 | U2 u2-display-time-zone | U4（画面の側） | TypeScript のフックの型（同じアプリの中） | U2 |
| C3 | U2 u2-display-time-zone | 画面（プリファレンス、表示のタイムゾーンの配布） | 同期の REST（`GET/PUT /api/me/preferences`）、OpenAPI | U2 |
| C4 | U3 u3-target-db-time | U4 | 同期の REST（`/api/tables`）の値の形、共有のスキーマ（変更は無い。決まりを明記する） | U3 |
| C5 | U5 u5-menu-icons-home（NavigationMenu） | U5 の画面の側（AppIconPolicy・HomeEntrance）、前の版の画面 | 同期の REST（`GET /api/me/navigation`）、OpenAPI | U5 |
| C6 | U6 u6-app-icon-assets | U5（上のバーのロゴ）、ログイン画面、ブラウザ | 静的なファイル（`frontend/public/`）、共有のスキーマ | U6 |
| C7 | 外部: make-you-chic-ui（上流） | U5（アイコン）・U6 以降のロゴの口 | 上流の部品の口、共有のスキーマ（依頼 `make-you-chic-ui-request-8.md`） | 上流（変更は依頼で行う） |

## C1 時点・ローカル時刻の変換（U1 → U2・U4）

TypeScript の関数（置き場は `frontend/src/shared/format/`）。純粋な関数で、タイムゾーンを明示に受ける。ブラウザの既定のタイムゾーンを暗に使わない。

```yaml shared-schema
contract: C1 TimeZoneConversion
kind: typescript-module
location: frontend/src/shared/format/
functions:
  - name: formatInstant
    input: { instant: "ISO-8601 時差つきの文字列（例 2026-11-01T15:00:00+00:00）", timeZone: "IANA の名前", fractionDigits: "0|3|6（DB の値の桁。省略すると文字の桁のまま）" }
    output: "表示のタイムゾーンの時刻と時差の文字（例 2026-11-02 00:00:00 +09:00）。秒の端数は桁のまま、丸めない"
  - name: parseLocalDateTime
    input: { text: "YYYY-MM-DD HH:mm:ss[.SSS|.SSSSSS]", timeZone: "IANA の名前", overlapChoice: "'earlier'|'later'|省略" }
    output:
      one_of:
        - { kind: "ok", instant: "時点（ISO-8601 時差つき）" }
        - { kind: "gap", reason: "夏時間で存在しない時刻" }
        - { kind: "overlap", earlier: "先（夏時間）の時点", later: "後（標準時）の時点" }
        - { kind: "invalid", reason: "日時として読めない" }
    note: "overlapChoice を渡すと overlap は ok になる。overlapChoice を渡さず overlap のときは overlap を返す"
  - name: canInterpretTimeZone
    input: { timeZone: "IANA の名前" }
    output: "今のブラウザが解釈できるか（真偽）"
  - name: preserveInstant
    input: { rawInstant: "サーバーが返した時点の文字" }
    output: "表示用の文字とは別に保つ元の時点（往復・主キー・同時更新の判定・参照の値に使う）"
errors: "例外を投げず、結果の型（kind）で返す（ストーリーの夏時間の誤りの出し方のため）"
rules:
  - 時差を持たない型と timetz は、この関数群を通さず文字のまま扱う
  - ブラウザの既定のタイムゾーンを暗に使わない（timeZone は必ず明示に渡す）
```

- 失敗の扱い: 例外を投げず、結果の型で返す。呼ぶ側（U4）が `gap`・`overlap` を画面の誤り・2択に写す。
- テストの置き場: `fast-check` の性質ベースのテスト（変換して戻すと元の時点に戻る、時差の無い型は時差を変えても値が変わらない）を U1 に持つ。

## C2 表示のタイムゾーンの配布（U2 → U4）

TypeScript のフック（置き場は `frontend/src/app/display-settings/` に足す）。

```yaml shared-schema
contract: C2 DisplayTimeZoneContext
kind: typescript-hook
name: useDisplayTimeZone
returns:
  status: "'loading' | 'ready'"
  timeZone: "ready のとき: 表示のタイムゾーンの IANA の名前（設定があればその値、無ければブラウザのタイムゾーン、保存済みの名前を今のブラウザが解釈できないときもブラウザのタイムゾーン）。loading のときは null"
  source: "'saved' | 'browser' | 'saved-unresolvable'"
  savedName: "保存済みの名前（無ければ null。source が saved-unresolvable のとき、案内に出す）"
rules:
  - status が loading の間、呼ぶ側は時刻の欄を出さない（読み込み中の表示にする）
  - プリファレンスの保存の後は、画面を読み込み直さずに値が差し替わる
  - 利用者が未認証のときは使わない（ログイン後の画面だけ）
```

## C3 プリファレンスの API（U2）

今の `GET/PUT /api/me/preferences` に、5つ目の項目 `displayTimeZone` を足す。足すだけで、ほかの項目は変えない。認証あり（`AUTHENTICATED`）、本人だけ、監査しない（今のプリファレンスと同じ）。

```yaml
openapi: 3.1.0
info: { title: MasterSmith preferences（displayTimeZone の追加）, version: additive }
paths:
  /api/me/preferences:
    get:
      summary: 自分の氏名と表示の設定を読む
      responses:
        "200":
          description: 5つ（displayTimeZone は値が無いとき null）
          content:
            application/json:
              schema: { $ref: "#/components/schemas/PreferencesResponse" }
        "401": { description: AUTHENTICATION_REQUIRED }
    put:
      summary: 自分の氏名と表示の設定をまとめて置き換える（displayTimeZone は本文に無いとき変えない）
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: "#/components/schemas/PreferencesRequest" }
      responses:
        "200":
          description: 保存した5つ
          content:
            application/json:
              schema: { $ref: "#/components/schemas/PreferencesResponse" }
        "400": { description: VALIDATION_FAILED（displayTimeZone が IANA の名前でない・空の文字列。保存済みの値は変わらない。誤りは項目ごとに返す） }
        "401": { description: AUTHENTICATION_REQUIRED }
components:
  schemas:
    PreferencesRequest:
      type: object
      properties:
        displayName: { type: string }
        language: { type: string, enum: [ja, en] }
        theme: { type: string, enum: [light, dark, system] }
        fontSize: { type: string, enum: [sm, md, lg] }
        displayTimeZone:
          type: [string, "null"]
          description: >
            IANA の地域の名前（例 Asia/Tokyo）。null は「ブラウザに合わせる」（保存の値を消す）。
            本文に名前が無いときは保存済みの値を変えない。空の文字列・固定のオフセット表記（+09:00・Z）・
            サーバーが知らない名前は 400。
    PreferencesResponse:
      type: object
      required: [displayName, language, theme, fontSize, displayTimeZone]
      properties:
        displayName: { type: string }
        language: { type: string }
        theme: { type: string }
        fontSize: { type: string }
        displayTimeZone: { type: [string, "null"], description: 保存した IANA の名前。値が無いときは null }
```

- **項目の有無を区別して受ける仕組み（レビュー R-01 の直し）**: 今の `PreferencesRequest` は4つの文字列だけの `record` で、JSON の項目が無いときも `null` のときも Java の値は `null` になるため、そのままでは Q1: A の区別（無い＝変えない、`null`＝消す）ができない。`displayTimeZone` だけは、3つの状態（項目が無い・明示の `null`・名前の値）を持つ型で受ける。新しい依存の部品は足さず、`PreferencesRequest` の中の型（項目が無い場合を表す値・`null` の場合を表す値・名前を持つ値）と、`null` を読む専用の読み方（Jackson のデシリアライザーが `null` のときに「消す」の値を返し、項目が無いときは構成子に何も渡らず「変えない」の値になる）で実現する。型の名前・置き場は機能設計（U2）で決める。ほかの4つは今のまま（本文に無いと `REQUIRED`）。
- **U2 が必ず持つテスト（C3）**: 本文に `displayTimeZone` が無い `PUT` で、保存済みの値が変わらない（応答にも保存済みの値が出る）／明示の `null` で値が消える（応答は `null`）／名前の値で保存される／空の文字列・オフセット表記・知らない名前・文字列でない型は 400（`VALIDATION_FAILED`、保存済みの値は変わらない）。4つの既存の項目が本文に無いときの `REQUIRED` は今のまま。
- **400 の項目ごとの誤り（レビュー R-04 の直し）**: `fieldErrors` の `field` は `displayTimeZone`、`reason` は既存の `INVALID_VALUE`（「決めた値に完全に一致しない」）を使い、空の文字列・オフセット表記・知らない名前・文字列でない型のすべてに同じ `INVALID_VALUE` を返す。`REQUIRED` は使わない（`null` は有効な値のため）。新しい `reason` は足さない（足すと受け側が一般の文言で出す安全な変更だが、今回は要らない）。
- 本人だけ: 要求の本文の利用者 ID・他人の設定は読み捨てる（一括代入の防止。NFR1.2）。これを確かめるテスト（未認証 401・本人以外の分は変えられない・要求の本文の改ざん）は U2 が持つ（`team.md`）。
- 前の版との互換: 前の版の応答の読み取りは、5つ目を知らず無視する。前の版の `PUT` は5つ目を送らず、保存済みの値は変わらない（Q1: A）。内部DB は列を足すだけ（ADR-001）。
- ログ・応答: タイムゾーンの名前は個人に関する値ではないが、`toString` は今の4つと同じく氏名を伏せる形のまま、名前を載せてよい。

## C4 業務データの日時の値の形（U3 → U4、変更は無い）

`/api/tables` の値の形は変えない。この契約は、決まりを明記する（U3 と U4 の境の食い違いを防ぐ）。

```yaml shared-schema
contract: C4 BusinessData time values
location: /api/tables （応答の行の値・要求の本文の値・検索の値）
types:
  time_point:
    db: [PostgreSQL timestamptz, MySQL/MariaDB timestamp]
    response: "UTC の時差つきの文字 YYYY-MM-DDTHH:mm:ss[.SSS|.SSSSSS]+00:00（端数は DB の値の桁のまま。今は末尾のゼロが落ちる（0〜9 桁の可変、ColumnValueCodec の appendFraction）ため、U3 が DB の定義の桁に固定する。今の動きとの差）"
    request: >
      時差つきで受ける（Z・z・±hh:mm）。UTC の時点に直して保存する。
      時差の無い値（例 2026-03-08T02:30:00）は UTC とみなして受ける（Q2: A。今のまま）。
      日時として読めない値は 400（VALIDATION_FAILED）。
  no_zone:
    db: [MySQL/MariaDB datetime/date/time, PostgreSQL timestamp/date/time]
    response: "DB の値のまま（時差なし）。DATETIME は YYYY-MM-DDTHH:mm:ss、DATE は YYYY-MM-DD、TIME は HH:mm:ss"
    request: "ISO の形だけ。時差を持たない"
  time_with_zone_only:
    db: [PostgreSQL timetz]
    response: "DB に入っている時差のまま HH:mm:ss±hh:mm（表示のタイムゾーンへは変換しない）。今の ColumnValueCodec は timetz を読み書きとも UTC にそろえて +00:00 で返しているため、要件 FR2.4・ストーリー AC2.2.2 のとおりにするには U3 が直す（今の動きとの差。timetz の応答の値が変わる点で、前の版の画面は時差つきの文字として読めるため壊れない）"
    request: "時差つきの文字（±hh:mm）で受け、時差を落とさず保存する。時差の無い値は UTC とみなす（Q2: A）"
rules:
  - サーバーは表示のタイムゾーンを知らない。表示のタイムゾーンへの変換は画面の側（C1）
  - 読んだ値をそのまま送り返しても、対象DB の値は変わらない（往復。秒の端数も落ちない）
  - 主キー・同時更新の判定の値・参照の値の時点は、読んだままの文字（元の時点）を送り返す
  - 夏時間で存在しない時刻の拒否は画面の側だけ（ADR-002）。サーバーは時点として存在する値を受ける
unchanged: [エンドポイント, 状態コード, 失敗の code, 要求・応答の項目名]
changed_values_by_u3: [timetz の応答（UTC への正規化をやめ DB の時差のまま）, 秒の端数の桁（末尾のゼロが落ちないよう DB の定義の桁に固定）]
```

- U3 が直す点（端数の桁・桁つきの時差つきの型・PostgreSQL の接続の時差）は、この形を変えず、読み書きの値を正す。
- 時差の無い値の扱いは、Q2: A（UTC とみなす）。ADR-002 と ストーリー AC2.3.3・AC2.4.4 のサーバー側の拒否は、「日時として読めない値の拒否」に限る（要件 FR3.4 との差。ADR-002 のとおり）。

## C5 メニューの API（U5）

今の `GET /api/me/navigation` の項目に、`tableKind` を足す。足すだけで、既存の項目は変えない。認証あり、作業ロールの権限で絞った後の項目だけ。

```yaml
openapi: 3.1.0
info: { title: MasterSmith navigation（tableKind の追加）, version: additive }
paths:
  /api/me/navigation:
    get:
      summary: 作業ロールの権限で絞った業務のメニューを読む
      responses:
        "200":
          description: メニュー（各項目に tableKind を足す）
          content:
            application/json:
              schema: { $ref: "#/components/schemas/NavigationResponse" }
        "401": { description: AUTHENTICATION_REQUIRED }
components:
  schemas:
    NavNode:
      type: object
      required: [id, label, icon, navigable, items]
      properties:
        id: { type: string }
        label: { type: object, description: 表示名の組（今のまま） }
        icon: { type: string, description: 許す一覧と照らした後のアイコンの名前（今のまま） }
        table: { type: [object, "null"], description: 移れる項目のテーブルの組（今のまま） }
        navigable: { type: boolean }
        tableKind:
          type: [string, "null"]
          enum: [TABLE, VIEW, null]
          description: >
            足す項目。table を持つ項目がテーブルなら TABLE、ビューなら VIEW。table を持たない項目（フォルダ）は null。
            利用者が見られる項目にだけ付く。受け取る側が知らない項目として無視してよい。
        items: { type: array, items: { $ref: "#/components/schemas/NavNode" } }
```

- 足すだけ: `id`・`label`・`icon`・`table`・`navigable`・`items` は変えない。前の版の画面は `tableKind` を知らず無視する（NFR4.2）。
- 漏えい: `tableKind` は、見られない項目には付かない（見られない項目はそもそも応答に出ない。NFR1.3）。DSL の識別・ロールの ID・権限の値は載せない（今のまま）。
- **`tableKind` の出どころ（レビュー R-05 の直し）**: 適用中の DSL の `DslTable.view`（ビューなら true。`navigation` の `MenuFilter` が `DslModel.findTable` で引ける位置にある。K-101）から決め、応答を作る `NavigationService` が足す。対象DB のメタデータを要求のたびに読まない（新しい問い合わせを足さない）。DSL に無いテーブルを指す項目は、今の絞りでメニューに出ないため、`tableKind` も出ない。決める細部（`MenuFilter` の純粋な関数に入れるか、サービスで足すか）は機能設計（U5）。
- アイコンの決め方: `icon` が DSL の指定で一覧にある名前なら、それを使う。無いときは `tableKind` と子の有無で、画面が既定を決める（取り込みまでは今の既定。ADR-006）。

## C6 アプリのアイコンの静的なファイル（U6）

```yaml shared-schema
contract: C6 App icon assets
location: frontend/public/ （ビルドの結果 dist のルート）
files:
  - { path: /favicon.svg, type: image/svg+xml, use: "SVG の favicon（固定のブランドの色）" }
  - { path: /favicon.ico, type: image/x-icon, use: "SVG を読めないブラウザ向け。/favicon.ico の要求に HTML ではなく画像を返す" }
  - { path: /icon.png, type: image/png, use: "SVG を読めない場面の代わり（任意）" }
  - { path: "frontend/src/ の SVG のモジュール", use: "アプリの中（ログイン画面・上のバーのロゴ）。currentColor でテーマに追従" }
html: "frontend/index.html の <head> に favicon の <link>（svg と ico）を足す"
rules:
  - 自作の SVG にはスクリプト・イベントの属性（onload など）・外部の参照（外部の URL の href・読み込み）を入れない
  - SVG の先頭に Apache License 2.0 のヘッダー（<!-- ... -->）を置き、ライセンスヘッダーの検査の対象に .svg を足す
  - .ico・.png は元の SVG から作る手順を README か記録に書く（バイナリはヘッダーを置けず検査の対象の外）
  - アイコンは飾りとして読み上げから外す（aria-hidden）。意味を持つ使い方のときだけ名前を付ける
```

## C7 上流 make-you-chic-ui への依頼（外部）

```yaml shared-schema
contract: C7 make-you-chic-ui requests
request_file: aidlc/spaces/default/intents/261102-timezone-ui-polish/make-you-chic-ui-request-8.md
asks:
  - { id: 1, what: "Icon の登録に mail・users・shield を足し IconName に出す", used_by: "U5 管理の区画の招待・グループ・ロール", story: "AC4.3.3 (Should)" }
  - { id: 2, what: "Icon の登録に table・view・folder を足し IconName に出す", used_by: "U5 業務の既定とホームのカード", story: "AC4.4.2" }
  - { id: 3, what: "Topbar にロゴを置く口（例 logo?: ReactNode）", used_by: "U5 ShellFrame のロゴ（U6 の素材）", story: "US4.2 (Should)" }
policy:
  - 取り込みは使う Bolt の前に、固定先の更新の専用のコミットで行う（team.md）
  - 取り込み前の間は、アイコン（1・2）は付けない・今の既定のまま、ロゴ（3）は出さない
  - vendor/make-you-chic-ui はこのリポジトリから直接変えない
  - 許すアイコンの名前の一覧（画面の allowedIcons.ts とサーバーの allowed-icons.txt の2か所）は、固定先のアイコンとそろえる（取り込みのたびに2か所を同じ名前にする）
```

## 契約の持ち主の決まり

- **持ち主**: 口を作る単位が持つ（C3 は U2、C5 は U5、C4 は U3、C1 は U1、C2 は U2、C6 は U6）。使う側の単位は、持ち主の契約を直接変えず、変更が要るときは持ち主の単位に依頼する。
- **足すだけの変更**: 応答に項目を足す・要求に省略できる項目を足すのは、受け取る側が知らない項目を無視する前提で安全。名前の変更・型の変更・項目を消す・意味を変える変更は、契約を直し、依頼者に諮る。
- **壊れる変更の合意**: 契約に載る口の「要求の項目が無いときの意味」（C3 の Q1: A）や「時差の無い値の扱い」（C4 の Q2: A）を変えるときは、前の版との互換（NFR4.2）を確かめ、依頼者の決定を記録する。
- **確かめ**: サーバーの口は、認証・認可・本人だけ・一括代入の防止・漏えい（ログ・応答にパスワード・トークン・業務データの値を載せない）の必須のテスト（`team.md`）に従い、画面の側の契約は単体のテストと性質ベースのテストで確かめる。

## 未解決の点

| 契約 | 未解決の点 | 止めるもの |
|---|---|---|
| C1 | 夏時間の重なる時刻の入れ方の細部（`overlapChoice` を画面のどの値から作るか。更新の画面で値を変えていない欄の扱い） | Functional Design（U1・U4） |
| C3 | `GET /api/me/preferences` が、解釈できない名前を返したときの扱いは画面の側（C2）。サーバーは保存済みの名前を返すだけ | なし（決定済み） |
| C3 | 項目の有無を区別する型の名前・置き場・デシリアライザーの細部（仕組みは本文のとおり決定済み） | Functional Design（U2） |
| C4 | `timetz` の応答を UTC への正規化から DB の時差のままに直す点と、秒の端数の桁の固定は、今の動きとの差。直しの詳細と、前の版の画面への影響の確かめ | Functional Design・NFR Design の捨ての試し（U3） |
| C5 | `tableKind` を足す場所（`MenuFilter` かサービスか）の細部（出どころは DSL の `DslTable.view` で決定済み） | Functional Design（U5） |
| C4 | MySQL の `timestamp` の上限・`time` の範囲外の読めない値の見せ方（レビュー R-01 の持ち越し。ストーリー AC2.1.6） | Functional Design（U3・U4） |
| C5 | テーブルと子を両方持つ項目のアイコン（`tableKind` と子の有無のどちらを先にするか） | Functional Design（U5） |
| C7 | 上流の取り込みの時期（Delivery Planning で決める。承認済みのストーリー AC4.3.1・AC4.4.2 の Must に影響） | Delivery Planning |
| C3・C4 | ADR-009 の確かめ（K-95・K-98。PostgreSQL の接続の時差・端数・型の名前） | NFR Design の捨ての試し |

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 単位: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/units-generation/unit-of-work.md`・`unit-of-work-dependency.md`
- 部品と決定: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/domain-design/components.md`・`decisions.md`
- 質問と答え: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/contract-design/contract-design-questions.md`（Q1: A・Q2: A）
- 今の口: `backend/src/main/java/cherry/mastersmith/user/web/MeController.java`・`PreferencesRequest.java`・`PreferencesResponse.java`、`navigation/web/NavNodeResponse.java`
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] C1 の関数名・引数の形は、Functional Design で確定する（ここは境界の形の案）。
- [assumption] `tableKind` の名前と値（`TABLE`・`VIEW`・`null`）は、Functional Design で確定する。
- 未解決の点は上の表のとおり。
