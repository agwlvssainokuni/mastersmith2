# DSL

DSL（YAML）の書式と読み込みの上限、既定の DSL の生成の決まり、DSL の管理の API と管理画面をまとめた文書です。

## DSL の書式（JSON Schema）と読み込みの上限

DSL（YAML）の書式は JSON Schema（2020-12）で定めています。今の書式は**版 2**で、正本は `backend/src/main/resources/dsl/dsl-schema-v2.json` の1つです。アプリの検証もこれを読みます。ビルドのときに画面の静的なファイルの置き場へ複写し、ログインなしで次の URL から取れます（秘密は含みません）。

- 起動したアプリ: `http://<ホスト>:8080/dsl/dsl-schema-v2.json`（画面の開発サーバーでは `http://localhost:5173/dsl/dsl-schema-v2.json`）
- 版 1 の `dsl-schema-v1.json` は無くなりました（その URL では JSON Schema は取れず、画面の HTML が返ります）。エディタの補完の設定で v1 の URL を指していたら、v2 の URL に替えてください。
- WAR の中の複写が正本と同じことは、`./gradlew verify` の 9 の段（`:backend:verifyDslSchemaInWar`）で確かめます。

エディタで DSL を書くときは、YAML の言語サーバー（VS Code の YAML の拡張など）に JSON Schema を指定すると、項目の補完と誤りの表示が使えます。DSL の先頭に次の1行を書くか、エディタの設定で `*.yaml` に JSON Schema を結び付けてください。

版 2 の DSL の骨組みは次のとおりです。テーブルはスキーマの下に置き、メニューのテーブルは `{schema, name}` の組で指します。

```yaml
# yaml-language-server: $schema=http://localhost:8080/dsl/dsl-schema-v2.json
version: 2
menus:
  - label: { ja: 基本マスタ, en: Basic masters }
    items:                      # まとまり（子の項目を持つ）。メニューの深さは 5 段まで
      - label: { ja: 部署, en: Departments }
        table: { schema: sales, name: dept_mst }
schemas:                        # スキーマはちょうど1つ
  sales:
    label: { ja: 販売, en: Sales }
    tables:
      dept_mst:
        label: { ja: 部署, en: Departments }
        view: false
        primaryKey: [dept_code]
        foreignKeys: []
        columns:
          dept_code:
            # （カラムの項目は版 1 と同じ）
```

- **スキーマ**: `schemas` の下にちょうど1つ置きます（0 個・2 個以上は意味の誤り）。スキーマの名前は対象DB の設定のスキーマ名（MySQL・MariaDB ではデータベース名）と同じにします。違う名前でも投入はでき、照合の警告に「スキーマの名前の違い」（`SCHEMA_MISMATCH`）が1件出て、テーブルは比べません（照合できなかった扱い）。
- **整数の型の名前と符号**: 書き込みの値の範囲は `dbType.name` を見て判定します。MySQL・MariaDB の符号なしの整数は、名前に `unsigned` を付けて `int unsigned`・`bigint unsigned`・`tinyint unsigned`・`smallint unsigned`・`mediumint unsigned` のように書いてください（`int(10) unsigned` の形も読めます）。名前に `unsigned` を含む列は、0 以上でその型の符号なしの上限までを受け付け、範囲の外（負の数を含む）は 400 `VALIDATION_FAILED`（`OUT_OF_RANGE`）になります。`unsigned` を含まない名前は、符号つきの下限から符号なしの上限までを受け付け、符号なしの列の名前を `int` のままにした既存の DSL もそのまま読めます（符号つきの列でその範囲の外の値を書くと、対象DB の拒否で 409 `BUSINESS_SAVE_REJECTED`）。PostgreSQL には符号なしの型が無く、`unsigned` は付けません。JSON Schema の `dbType.name` の `description` にも同じ内容を書いてあります。
- **メニューの深さ**: `menus` の直下を 1 段目とし、まとまりかテーブルを指す項目かによらず項目1つを1段と数えて、5 段までです。6 段目の項目があれば、その場所を示す意味の誤りになります。

エディタの検証は補助です。正はサーバー側の検証で、画面や API で投入した DSL はすべてサーバー側で検証します。サーバー側では、エディタでは確かめられない次の決まりも確かめます。

| 段 | 決まり |
|---|---|
| 大きさ | 本文が 10MB（10,485,760 バイト）を超えたら読みません |
| 1行の長さ | 1行は 65,536 文字（UTF-8 を解読した後のコードポイントの数。改行は数えません）まで。超える行があれば、読む前に最初のその行で止め、大きさの種類（`SIZE_LIMIT`）の誤り（文言の鍵 `dsl.limit.line`。位置はその行と 65,537 列）にします。コメントの行も数えます |
| 読み込み | 入れ子の深さは 50 まで。対応表・並びを指す別名（`*name`）は 100 個まで。別名を展開した後の節の数は 1,000,000 まで（別名の展開の爆発を止めます）。タグ（`!!str`・`!!java...`・`!custom` など）はすべて拒否します。1つの対応表の中の同じキーは誤りにします（後の値で上書きしません） |
| 書式の版 | `version` は 2 だけ（版 1 は書式の版の誤り。読み替えません） |
| 構文 | JSON Schema に合うこと（書式に無い項目は誤り。接続先・ユーザー名・パスワードなどの項目も書けません） |
| 意味 | スキーマがちょうど1つ、メニューの深さが 5 段まで、メニュー・主キー・外部キー・選択肢の参照の先のテーブルとカラムがあること、一覧の並び順が重ならないこと、フォーム部品と選択肢の出どころが合うこと、`min` ≦ `max`・`minLength` ≦ `maxLength`、`pattern` が正しい正規表現で 1,000 文字まで（確かめは 1 件 100 ミリ秒まで） |

- YAML は 1.1 の暗黙の型で読みます。`yes`・`no`・`on`・`off` は真偽値になるため、文字として書くときは `"yes"` のように引用符で囲んでください。
- 誤りは、YAML の行・列と DSL の中の場所（例 `schemas.sales.tables.dept_mst.columns.dept_code.list.order`）と、文言の鍵（`dsl.` で始まる。一覧は `cherry.mastersmith.dsl.domain.DslMessageKeys`）で返します。誤りに埋める値は、項目の名前と、書いた値の先頭 100 文字だけです。

## 既定の DSL の生成の決まり

対象DB のスキーマ（テーブル・ビュー・カラム・主キー・外部キー・コメント）から、既定の DSL を作ります（部品は `cherry.mastersmith.dslmanage.generate`）。作った DSL は、投入した DSL と同じくサーバー側の検証（前の節）を通したものだけを返します。接続先・ユーザー名・パスワードは DSL に書きません。

- **スキーマ**（版 2）: `schemas` に対象DB の設定のスキーマ名（MySQL・MariaDB ではデータベース名と同じ値）を1つ置き、表示名は `ja`・`en` ともスキーマ名にします。テーブルはその `tables` の下に置きます。

- **表示名**: `ja` はテーブル・カラムのコメント、コメントが無ければ物理名。`en` はいつも物理名です。コメントの制御文字（改行・タブを除く）は取り除き、長さは切り詰めません。
- **メニューと並び**: メニューは1階層で、テーブル（ビューを含む）ごとに1項目（`table: {schema, name}`）です。メニューと `tables` は、物理名を大文字・小文字を区別せずに比べた順（同じなら元の名前の順）に並べます。
- **ビュー**: `view: true` で、主キー・外部キーを持ちません。
- **DB 上の型**: `dbType` には DB が返した型の名前・長さ・精度・桁・NULL を許すかをそのまま書きます（型の名前は MySQL・MariaDB が `DATA_TYPE`、PostgreSQL が `udt_name`）。`longtext` のように長さが 2147483647 を超える型は、長さを `null` にします。MySQL・MariaDB の符号なしの整数（情報スキーマの `COLUMN_TYPE` に `unsigned` を含む `tinyint`・`smallint`・`mediumint`・`int`・`bigint`）は、型の名前を `int unsigned` のように書きます（書き込みの値の範囲を符号なしの範囲で判定するため）。**既存の DSL を再生成すると**、符号なしの列の `dbType.name` が `int` → `int unsigned` に変わり、差の表の型の差として出ます（適用すると、その列の範囲の判定が厳しくなります）。
- **バリデーション**: NOT NULL で既定値の無いカラムに `required`、長さのある文字列に `maxLength`（どちらも `origin: DB`）。数値の範囲・一意は作りません。
- **外部キー**: 1つのカラムだけの外部キーは、フォーム部品を `select`、選択肢を参照先のテーブルとカラム（`REFERENCE`）にします。複数のカラムの外部キーは `foreignKeys` に写すだけです。参照先が読めない（写しに無い）外部キーは写しません。
- **主キー**: 主キーのカラムの検索は `EQUALS`。一覧の既定の並べ替えは、主キーの最初のカラムの昇順です（そのカラムを一覧に出さないときは無し）。

型の分類とフォーム部品の初期値は次のとおりです（型の名前は大文字・小文字を区別しません）。

| 分類 | 型 | フォーム部品 | 検索 | 一覧 | 詳細 | 書式 |
|---|---|---|---|---|---|---|
| 短い文字列 | `char`・`varchar`（PostgreSQL の `bpchar` を含む）で長さ 255 以下 | text | 部分一致 | 表示・並べ替え可 | 表示 | 無し |
| 長い文字列 | 長さ 256 以上・長さの無い `varchar`、`tinytext`・`text`・`mediumtext`・`longtext`・`clob` | textarea | 部分一致 | 表示しない | 表示 | 無し |
| 数値 | `tinyint`・`smallint`・`mediumint`・`int`・`integer`・`bigint`・`decimal`・`numeric`・`float`・`double`・`real`、PostgreSQL の `int2`・`int4`・`int8`・`float4`・`float8` | number | 範囲 | 表示・並べ替え可 | 表示 | 桁区切り |
| 真偽値 | `boolean`・`bool`、MySQL・MariaDB の `tinyint(1)`・`bit(1)` | checkbox | 選択肢 | 表示・並べ替え可 | 表示 | はい・いいえ |
| 日付 | `date` | date | 範囲 | 表示・並べ替え可 | 表示 | 日付 |
| 日時 | `datetime`・`timestamp`・`timestamptz` | datetime | 範囲 | 表示・並べ替え可 | 表示 | 日時 |
| 時刻 | `time`・`timetz` | text | 完全一致 | 表示・並べ替え可 | 表示 | 時刻 |
| 対応外 | 上のどれでもない型（`json`・`enum`・`year`・`uuid`・`bytea`、PostgreSQL の `bit` など） | text | 検索しない | 表示しない | 表示しない | 無し |

- **`tinyint(1)` と `bit(1)`**: MySQL・MariaDB の `tinyint(1)` は、情報スキーマの `COLUMN_TYPE`（型の全体の表記）で見分けます。MySQL 8.4 は `tinyint(1) unsigned` の幅を落として `tinyint unsigned` と返すため、MySQL では符号なしの `tinyint(1)` は数値になります（MariaDB では真偽値）。`bit(1)` は精度 1 で見分けます。
- **大きさ**: 作った DSL も上限の 10MB の内に収めます。超えるほど大きなスキーマでは生成を失敗にします（目安: 100 テーブル × 100 カラムでコメントが無いとき約 6.7MB。コメントの分だけ増えます）。その場合は、対象のスキーマを分けるなどの運用で対応してください。
- **長いコメント**: 表示名の `ja` にするコメントは、先頭から 4,096 文字（コードポイント）までを使い、それより後は切ります。書き出しで逃がす文字を含んでも、1行の長さの上限（65,536 文字）の内に収めるためです。MySQL・MariaDB のコメントの上限（テーブル 2,048 文字・カラム 1,024 文字）より長く、普通は切られません（PostgreSQL のコメントには実用上の上限がありません）。切った表示名は、生成した後にプレビューでダウンロードして直せます。

## DSL の管理の API

管理者だけが使う DSL の管理の API です（`/api/admin/dsl/` の下。ログインしていなければ 401、管理者でなければ 403 と `ACCESS_DENIED` の監査）。画面から使います。エラーは Problem Details（`code` つき）で返ります。

| メソッドとパス | 内容 | 成功 | 主な失敗（`code`） |
|---|---|---|---|
| `GET /api/admin/dsl/status` | 今の状態（適用中の版とプレビュー。無ければ `null`。起動時に適用中の版を読めなかったときは `appliedUnreadable: true`） | 200 | — |
| `GET /api/admin/dsl/preview` | プレビューの中身（要約・適用中との違い・対象DB との照合の警告）。重い処理 | 200 | 404 `DSL_PREVIEW_NOT_FOUND`、422 `DSL_INVALID`（今の書式で読めないプレビュー。版 1 のものなど）、503 `DSL_BUSY` |
| `POST /api/admin/dsl/preview?source=UPLOAD\|PASTE` | 投入（本文は `application/yaml`、10MB まで）。検証を通れば今のプレビューを置き換える。重い処理 | 201 | 413 `DSL_TOO_LARGE`、415 `UNSUPPORTED_MEDIA_TYPE`、422 `DSL_INVALID`（誤りの先頭 100 件 `errors` と総数 `total`）、503 `DSL_BUSY` |
| `DELETE /api/admin/dsl/preview` | プレビューの破棄 | 204 | 404 `DSL_PREVIEW_NOT_FOUND` |
| `POST /api/admin/dsl/preview/generate` | スキーマの読み込み（既定の DSL を作り、今のプレビューを置き換える）。重い処理 | 201 | 503 `TARGET_DB_UNCONFIGURED`・`TARGET_DB_UNAVAILABLE`・`DSL_BUSY` |
| `GET /api/admin/dsl/preview/download` | プレビュー中の DSL を、保存した本文のまま添付（`dsl-preview-<識別の先頭12文字>.yaml`）で返す | 200 | 404 `DSL_PREVIEW_NOT_FOUND` |
| `POST /api/admin/dsl/apply` | 適用（本文 `{"previewId": "..."}`。見たプレビューを指定する）。対象DB には接続しない | 200 | 409 `DSL_PREVIEW_CHANGED`（プレビューが置き換わった・破棄された・同時の適用に負けた） |
| `GET /api/admin/dsl/history` | 適用の履歴（新しい順、最大 `MASTERSMITH_DSL_HISTORY_LIMIT` 件、今適用中の版に `current: true`） | 200 | — |
| `POST /api/admin/dsl/history/{revisionId}/restore` | 履歴の版をプレビューに戻す（今の検証にかけ直し、通れば出どころ `RESTORE` で今のプレビューを置き換え、照合の警告つきの中身を返す）。重い処理 | 201 | 404 `DSL_REVISION_NOT_FOUND`（件数の上限で消えた版を含む）、422 `DSL_INVALID`（今の検証を通らない。プレビューは変わらない）、400 `VALIDATION_FAILED`（識別が UUID の形でない）、503 `DSL_BUSY` |
| `GET /api/admin/dsl/applied/download` | 適用中の DSL（今の状態の `applied` と同じ版）を、保存した本文のまま添付（`dsl-applied-<識別の先頭12文字>.yaml`）で返す | 200 | 404 `DSL_APPLIED_NOT_FOUND` |

- **問題の種類（`code`）**: `DSL_INVALID`（422）・`DSL_TOO_LARGE`（413）・`DSL_PREVIEW_NOT_FOUND`（404）・`DSL_PREVIEW_CHANGED`（409）・`DSL_APPLIED_NOT_FOUND`（404）・`DSL_REVISION_NOT_FOUND`（404）・`TARGET_DB_UNCONFIGURED`（503）・`TARGET_DB_UNAVAILABLE`（503）・`DSL_BUSY`（503）。説明は `/api/problems/<code を小文字とハイフンにしたもの>` で見られます。
- **誤りの文言**: 422 の `errors[].message` と照合の警告の `message` は、要求の `Accept-Language` の言語（日本語・英語、既定は日本語）です。YAML・JSON Schema の部品の例外の文言は入りません。
- **本文の大きさの上限の置き場**: 本文の大きさは、ログイン（アクセストークンの確かめ）と認可の後に確かめます。本文を読むのはログインしていてその API を使える人の要求だけです。そのため、**ログインしていない大きな要求は 413 ではなく 401** になります。投入の API だけ上限が `MASTERSMITH_DSL_MAX_SUBMIT_SIZE`（既定 10MB、`DSL_TOO_LARGE`）で、ほかの API は `MASTERSMITH_WEB_MAX_REQUEST_BODY_SIZE`（既定 1MB、`PAYLOAD_TOO_LARGE`）です。`Content-Length` がある送り方では本文を読まずに断ります。
- **重い処理は同時に1つ**: 生成・投入・プレビューの表示・履歴からの戻しは、アプリ全体で同時に1つだけ処理します。重なった要求は待たずに 503 `DSL_BUSY` で断り、状態を変えず、監査の出来事も出しません。少し待ってからやり直してください。照合は対象DB が応答しないと最悪 23〜28 秒かかり（`docs/target-db.md` の「対象DB」の節）、その間ほかの重い処理は `DSL_BUSY` になります。
- **適用**: 見たプレビューの識別（`previewId`）を指定します。1つのトランザクションで履歴への追加・プレビューの削除・上限を超えた古い履歴の削除を行い、確定の後にだけ適用中の DSL を切り替えて監査に記録します。適用中と同じ内容でも履歴に1件足します。
- **履歴からの戻し**: 戻した版は投入の一種として扱い、監査に `DSL_SUBMITTED`（出どころ `RESTORE`）を記録します。今の検証を通らない版（書式の版が変わった など）は投入と同じ誤りの一覧の 422 になり、プレビューは変わらず、受け付けなかった投入の出来事（`DSL_SUBMISSION_REJECTED`）も記録しません。戻したプレビューを適用すると、履歴には出どころ `RESTORE` の新しい版として足されます。
- **起動時**: 履歴の最新（適用した日時が最も新しい版）を適用中の DSL として読みます。今の検証を通らない（書式の版が変わった など）ときは、ERROR（`適用中の DSL を読めないため、適用中の DSL が無い状態で起動します`、識別の先頭 12 文字と誤りの種類だけ）を1件出し、適用中の DSL が無い状態で起動を続けます。今の状態の `appliedUnreadable` が `true` になり、権限とメニューでは DSL が無い扱いです（適用中の版の記録と履歴は残ります）。メニューの深さだけは起動時に緩め、6 段目より深い枝を落として読みます（WARN `適用中の DSL のメニューの深すぎる枝を落として起動します` を1件）。
- **書式の版を上げたとき（版 1 → 版 2）**: 版 1 で適用していた DSL は起動時に読めないため、上の「適用中の DSL が無い状態」で起動します。版 1 のプレビューは読めないプレビューとして残り、表示（`GET /preview`）は 422 `DSL_INVALID`（書式の版の誤り）になります。破棄してから、スキーマを読み込んで既定の DSL を作り直し（版 2）、確かめて適用してください。履歴の版 1 の版は「プレビューに戻す」と 422 になります。
- **保存**: プレビュー（最大1件）と適用の履歴は内部DB の `dsl_previews`・`dsl_applied_revisions`（Flyway の V5）に、受け取ったバイト列のまま入れます。すべて 10MB なら最大約 210MB です。
- **既知の制約（動いている間の内部DB のファイルの大きさ）**: 上限を超えた古い履歴を消しても、アプリが動いている間は H2 がその場所を再利用せず、内部DB のファイルは投入と適用のたびに本文の大きさの分（10MB の DSL なら約 10.8MB）ずつ大きくなります（2026-09-25 の測定で、10MB の DSL の投入と適用を 21 回で約 278MB、40 回で約 483MB。頭打ちになりません）。この伸びは受け入れ、**アプリを止めずに `./docker/hikari-pool.sh compact` で詰め直します**（`docs/internal-db.md` の「内部DBのファイルの詰め直し」の節。詰め直しの間の要求は再開まで待たされます）。アプリを止めたとき（`docker compose restart app` など）にも、接続先の `;DEFRAG_ALWAYS=TRUE`（`docs/configuration.md` の「環境変数」の表の `MASTERSMITH_DB_URL`）でファイルが詰め直されます。
  - 内部DB のファイルの大きさは、アプリを止めずに読み取りだけで見られます: `./docker/hikari-pool.sh status`（または `docker run --rm -v mastersmith_mastersmith-data:/data:ro eclipse-temurin:25.0.5_8-jre-noble@sha256:a98128a57de944a72bd31fbc79b898edd5a5864d3652071d5bbd2160ab7d7dce du -sh /data`）。目安として、DSL の投入と適用を重ねて 300MB を超えたら、ディスクの空き（`colima ssh -- df -h /`）を確かめ、利用の少ない時間に `./docker/hikari-pool.sh compact` で詰め直します。

### DSL の操作の監査

生成・投入（履歴からの戻しを含む）・受け付けなかった投入・適用・破棄を、監査の表 `audit_events` に1件ずつ記録します（種類 `DSL_GENERATED`・`DSL_SUBMITTED`・`DSL_SUBMISSION_REJECTED`・`DSL_APPLIED`・`DSL_PREVIEW_DISCARDED`）。

- 列（Flyway の V6 で足した、NULL を許す列）: 操作した管理者の利用者 ID `actor_user_id`、DSL の識別 `dsl_hash`、出どころ `dsl_source`（`GENERATED`・`UPLOAD`・`PASTE`・`RESTORE`）、受け付けなかった投入の理由の種類 `rejection_kind`（最初の誤りの種類、大きさで断ったときは `SIZE_LIMIT`。そのときは `dsl_hash` は空）。ほかに既存の日時・種類・結果・接続元IP・User-Agent・トレースIDを記録します。
- DSL の本文と対象DB の接続先は記録しません。`DSL_BUSY` で断った要求、巻き戻った適用、検証を通らなかった履歴からの戻しは記録しません。
- 記録に失敗しても DSL の操作は成功し、アプリのログに ERROR（`監査イベントの記録に失敗しました`）が1回出ます（本文は載りません）。

### DSL の操作の指標とログ

- 指標 `mastersmith.dsl.operation`（Timer。Prometheus では `mastersmith_dsl_operation_milliseconds_*`）。タグは `operation`（`generate`・`submit`・`restore`・`apply`・`discard`・`compare`）と `outcome`（`success`・`rejected`・`failed`・`busy`）だけです。`compare` はプレビューの表示の中の照合だけの時間です。
- 操作ごとに INFO（`DSL の操作を終えました`）を1件、キー `dsl.operation`・`dsl.outcome`・`dsl.durationMs`・`dsl.hash`（先頭 12 文字）・`dsl.source` で出します。想定内の失敗（413・409・422・503）は WARN でスタックトレースなし、想定外（500）は ERROR です。
- 手元の監視のダッシュボード「MasterSmith の概要」の行「DSL の操作」に、操作ごとの件数・結果ごとの件数・操作ごとの時間の 95 パーセンタイル（目標の線 1 秒・10 秒・30 秒）があります。警報はありません。

## DSL の管理画面

管理者は、サイドバーの「DSL」（「管理」の次。管理者にだけ表示）から DSL の管理画面 `/admin/dsl` を開きます。画面は上の「DSL の管理の API」だけを使います。サイドバーの項目を隠すのは表示の切り替えで、使えるかどうかはサーバー側（401・403）で決まります。管理者でない利用者が URL を直接開くと、画面の枠の中に「この画面を使う権限がありません」の表示（`docs/roles-and-permissions.md` の「管理の画面の「権限が無い」の扱い」）が出ます。

- **今の状態**（画面の上部、どのタブでも表示）: 適用中の版とプレビューの識別（先頭 12 文字。全体はツールチップと読み上げで読めます）・出どころ・人・日時（端末の時差で、時差の略号つき）と、「スキーマを読み込む」。起動時に適用中の DSL を今の書式（版 2）で読めなかったときは、「適用中の DSL は使われていません」の注意を出します（権限とメニューでは DSL が無い扱い。スキーマを読み込み、確かめて適用し直します）。
- **プレビュー**のタブ: 検証を通ったこと、対象DB との食い違いの件数（照合できなかったときはその旨を先頭に。スキーマの名前の違いも照合できなかった扱い）、要約（スキーマの数を含む。表示名が埋まっていない場所は先頭 100 件と総数）、適用中との違い（スキーマごとにまとめ、既定は違いのあるテーブルだけ。「すべて表示」で変わらないテーブルも表示。行を開くとカラムの違い）、照合の警告、メニューの木（入れ子のまとまりを含む）。「ダウンロード」「破棄する」「適用する」。今の書式で読めないプレビュー（版 1 のものなど）は、「このプレビューは今の書式で読めません」と誤りの一覧を示し、「ダウンロード」と「破棄する」だけを出します（適用はできません）。
- **投入**のタブ: 「ファイルを選ぶ」と「貼り付ける」を切り替えて入力し、選んでいる方だけを送ります。10MB を超えるものは送る前に案内し、ファイルは読み込みません（上限の確かめは案内で、正はサーバー側の 413）。検証を通らなかったときは、誤りの件数と先頭 100 件（行・列・場所・種類・内容）を示し、入力は残ります。このタブに **DSL の書式（JSON Schema）** のリンク（`/dsl/dsl-schema-v2.json`、ログインなしで取れる静的なファイル）があります。
- **履歴**のタブ: 適用の履歴（新しい順）。適用中の版は「適用中」の文字で示し、その行から適用中の DSL をダウンロードできます。ほかの版は「プレビューに戻す」で今のプレビューに置けます（今の検証を通らないときは誤りの一覧をこのタブに示します）。
- **確かめる表示**: スキーマの読み込み・投入・戻しでプレビューを置き換えるとき（今のプレビューの出どころ・置いた人・日時を表示）、適用するとき（違いの件数と照合の警告の件数を表示）、破棄するときに出ます。はじめのフォーカスは「やめる」で、背景のクリックでは閉じません。
- **失敗の知らせ**: サーバーの `code` から文言を選びます（`DSL_BUSY` は「ほかの処理中です。少し待ってからやり直してください」で、状態を読み直さず入力は残ります）。知らない `code` や通信の失敗は一般の文言にし、`detail` や内部の文言は表示しません。適用が 409 のときは今の状態を読み直し、別の管理者による置き換えか破棄かを分けて示します（自動では適用しません）。
- **読み直し**: 操作の後と、プレビュー・版・適用中が無い（404）応答の後に、今の状態と開いているタブの中身を読み直します。定期的な自動の読み直しはしません。
- **表示言語**: ブラウザの言語が英語なら英語、それ以外は日本語です。テーブル名・カラム名・DSL の中の場所・識別は訳しません。
