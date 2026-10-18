# セキュリティの要件 — U2 u2-businessdata-write

## 出典

- 要件 `aidlc/spaces/default/intents/261016-business-data-views/inception/requirements-analysis/requirements.md`
  - FR6〜FR10・FR12・FR13、NFR4.1〜NFR4.4・NFR7
- ストーリー `inception/user-stories/stories.md`
  - AC2.4.5・AC3.4.1〜AC3.4.8・AC3.6.6〜AC3.6.9
- この単位の機能設計 `construction/u2-businessdata-write/functional-design/`
  - `rules.md` の BR1.4・BR3・BR4.2・BR7・BR9.1〜BR9.5・BR11・BR12・BR13
  - `functional-spec.md` の 1.1・2節・7節・8節・11節
- 機能設計のレビュー `.aidlc-reviews/functional-design/units/u2-businessdata-write/7dacbaa3e842d0af/1.json`
  - R-04〜R-08
- 承認の場の決定 `construction/functional-design/memory.md`（Open questions）
- ADR-006・ADR-007・ADR-008 `inception/domain-design/decisions.md`
- 契約 `inception/contract-design/contract-summary.md`（C3・C5・C6）
- この段の答え `construction/u2-businessdata-write/nfr-requirements/nfr-requirements-questions.md`
  - Q1・Q2・Q3・Q5 はいずれも A、まとめの確認は Looks correct
- U1 の NFR 要件 `construction/u1-businessdata-read/nfr-requirements/security-requirements.md`
  - ID の振り方、NFR4.5 の 1,000 字
- `aidlc/spaces/default/memory/team.md`
  - Testing Posture の業務データの必須のテスト
  - Code Style の書き込みの接続・SQL の組み立て・`toString` の伏せ字
- `project.md` の Forbidden・Mandated（書き込みのアカウントは行の操作だけ）
- コード
  - `backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDbProperties.java`: 接続 3 秒・生成 20 秒・照合 5 秒・プール 5
  - `TargetDataSourceConfig.java`: 通信の待ち = 最も長い上限 + 5 秒、読み取りは `setReadOnly(true)`
  - `common/web/RequestBodyLimitRoute.java`: メソッドとパスの完全一致、413 に限る
  - `backend/src/main/resources/application.yaml`: 共通の本文の上限 1MB
  - `dsl/domain/DslFormat.java`: pattern の長さ 1,000 字、組み立ての待ち 100 ms
  - `audit/domain/AuditEvent.java`: `MAX_DETAIL_LENGTH = 16_384`
  - `db/migration/V4__u4_audit_event.sql`: `failure_reason VARCHAR(32)`
  - `docker/targetdb/*/02-reader-account.sh`: MySQL・MariaDB はデータベース単位の付与

## ID の振り方

- 上流の枝番と同じ番号は、同じ意味でだけ使う。
- NFR4.1〜NFR4.4 は上流のまま使う。
- 足す番号は U1 の振り分けに従う。U2 は 4.7 と 4.9〜4.19 を使う。このうち 4.17〜4.19 は使わずに空けておく。
- ほかの分類は U1 の続きから振る。

| 上流の ID | この単位での扱い | この単位の ID | 置いた文書 |
|---|---|---|---|
| NFR1 応答時間 | 当たる | NFR1.4〜NFR1.6 | performance |
| NFR2 規模 | 当たる | NFR2.6〜NFR2.9 | scalability |
| NFR3 対象DB | 当たる | NFR3.4 / NFR3.5・NFR3.6 | performance / reliability |
| NFR4.1 SQL の組み立て | 当たる（書き込みの側） | NFR4.1 | security |
| NFR4.2 行の値の漏えい | 当たる | NFR4.2・NFR4.13 / NFR8.4 | security / observability |
| NFR4.3 書き込みの接続の分離 | 当たる（書き込みの側） | NFR4.3・NFR4.11・NFR4.16 | security |
| NFR4.4 時間の上限 | 当たる | NFR4.4・NFR4.7 / NFR4.12 | security / reliability |
| （足す） | pattern の時間の上限 | NFR4.7 | security |
| （足す） | 本文と値の長さ | NFR4.9 | security |
| （足す） | 認可・一括代入・IDOR | NFR4.10 | security |
| （足す） | 書き込みの設定の欠け | NFR4.11 | security |
| （足す） | 失敗の分け方と例外の通り道（R-08） | NFR4.12 | reliability |
| （足す） | 監査の中身と切り詰め | NFR4.13 | security |
| （足す） | 主キー・ビューの 409 の監査の範囲（R-04） | NFR4.14 | security |
| （足す） | 時間切れの監査の読み方（R-05） | NFR4.15 | reliability |
| （足す） | MySQL・MariaDB の付与の範囲（R-06） | NFR4.16 | security |
| NFR5 アクセシビリティ | 当たらない | — | — |
| NFR6 言語 | 当たらない | — | — |
| NFR7 テスト | 当たる | NFR7.5・NFR7.6 / NFR7.4 | security / performance |
| NFR8 運用 | 当たる | NFR8.4〜NFR8.6 | observability |

- NFR5 が当たらない理由: この単位に画面が無い（U3・U4 の受け持ち）。
- NFR6 が当たらない理由: 誤りは `code` と `fieldErrors.reason` で返し、文は U3 の画面が出す。pattern の `message` は DSL の文をそのまま返すだけで、この単位に言語の目標が無い。
- U4・U5 は単位の中で独自に NFR4.5 以降・NFR7.1 以降を振っている。番号の重なりは単位の外では問題にせず、U1〜U3 の業務データの機能の中だけでそろえる。

## 要件

### NFR4.1 書き込みの SQL の組み立て

**要件**

- 書き込みの SQL（事前の問い合わせ・INSERT・UPDATE・DELETE・違反の後の確かめ）は、`RowSqlExecutor` の書き込みの側だけが組み立てて実行する。
- 識別子は、権限の写しの DSL の種類ごとの名前の一覧と照らしてから `SqlIdentifiers.quoteKnown` で引用する。
- 値は型ごとのセッターでバインドし、`stringtype=unspecified` に頼らない（BR3.8・BR7.1）。
- `BusinessDataBoundaryArchitectureTest` で、`prepareStatement`・`createStatement` を呼んでよい場所を、その部品だけにする。
- SpotBugs の `SQL_` の除外は、書き込みのメソッドに広げる。絞り方はクラス・メソッド・パターン名の3つで、理由を書く（BR13.3）。

**確かめ方**

- ArchUnit の境界テスト。
- SpotBugs の関門（`SQL_` は priority によらず止める）。
- `'`・`"`・`;`・`--`・バッククォート・改行を含む識別子と値の、作成・更新・削除の結合テスト（3種類の DB）。

**持ち主の段**: Code Generation（B4）。除外を広げるときは、コード生成の計画で依頼者の承認を得る。

### NFR4.2 漏えいの防止

**要件**

- 行の値・主キーの値・接続先を、次のどこにも含めない: アプリのログ・トレースの属性・誤りの応答（`detail`・`instance`・`fieldErrors`）・監査の書き込みの失敗のログ。
- `WriteRequest`・`WriteValues`・`TypedValue`・`RowKey`・`BusinessDataChangedEvent` は、`toString` でカラムの名前と件数だけを出す。
- JDBC の例外のメッセージは、どこにも出さない。違反の後の確かめの例外も同じ扱いにする。
- ログには SQLState・業者の番号・例外のクラスの名前だけを出す（BR12）。

**確かめ方**

- TRACE を有効にした `BusinessDataWriteSecretLeakIT`。作成・更新・削除・違反・時間切れ・監査の書き込みの失敗の場合を確かめる。
- 外部へ送る設定を有効にした結合テストで、トレースの属性に値が無いことを確かめる。
- 400・409・503 の応答に、入力の値と主キーの値が無いことを確かめる。

**持ち主の段**: Code Generation（B4）

### NFR4.3 書き込みの接続の分離

**要件**

- 書き込みの接続は、読み取りの接続先と、書き込みの資格情報で作る別のプールにする。書き込みの資格情報は環境変数（`.env`）だけから受け取る。
- 読み取り専用にはしない。
- 書き込みの DataSource と、対象DB 用のトランザクションの管理者は、既定の候補にしない名前つきの部品にする（BR9.1・BR9.2）。
- 見本の対象DB の書き込みのアカウントには、行の操作（`SELECT`・`INSERT`・`UPDATE`・`DELETE`）と自動の採番に要る権限だけを与える。DDL の権限は与えない（BR13.4、`project.md` の Mandated）。

**確かめ方**

- 書き込みの API が書き込みのアカウントの接続を使い、読み取りの API が読み取りのアカウントの接続を使うことの結合テスト。
- 書き込みのアカウントで表を作る・消す操作が、DB に拒まれることの結合テスト（3種類の DB。AC3.6.7）。

**持ち主の段**: Code Generation（B4）。付与の台本は基盤の設計。

### NFR4.4 書き込みの時間の上限

**要件**

- 書き込みの目的の時間の上限は、文ごとに 5 秒とする（Q1: A）。秒の単位で当て、1 秒以上とする。
- 事前の問い合わせは、送った unique のカラムをまとめて1文にし、カラムごとの有無を1回で返す。
- 違反の後の確かめも1文にまとめる。
- 文は最大3つで、1要求の最悪は、借りる待ち 3 秒 + 5 秒 × 3 のおよそ 18 秒とする。
- 書き込みのプールのドライバーの通信の待ちは、上限 + 5 秒 = 10 秒とする。
- 読み取りのプールの通信の待ち（25 秒）は変えない。

**確かめ方**

- 時間の上限の値の設定の結合テスト。
- 時間切れの振る舞いは NFR4.12 で確かめる。

**持ち主の段**: Code Generation（B4）。1文にまとめる形は NFR 設計の試しで決める。

### NFR4.7 pattern の照合の時間の上限

**要件**

- pattern の照合は、値ごとに 100 ms、1要求の照合の合計に 300 ms の締め切りを置く（Q2: A、BR4.2）。
- 合計を超えたら、残りのカラムは照合せず `PATTERN_TIMEOUT` にする。
- 照合は、締め切りを見て打ち切る文字列の包みで行い、別のスレッドを残さない。
- 締め切りは注入した時計で決める。
- 照合の前に長さの検証（`TOO_LONG`）を当て、長すぎる値には照合しない（ADR-007 の切り替え先を先に当てる）。
- 照合は全体一致とする。

**確かめ方**

- `(a+)+$` と `aaaa…b` の値で、値ごと・合計の締め切りの境界（ちょうど・超え）を、注入した時計で確かめる単体テスト。
- 時間の側の確かめは `performance-requirements.md` の NFR1.6。

**持ち主の段**: Code Generation（B4）

### NFR4.9 本文と値の長さ

**要件**

- 本文の上限は、既存の `RequestBodyLimitRoute` の形で道ごとに決める（BR13.5、Q3: A）。
  - 作成（`POST /api/tables/rows`）・更新（`PATCH /api/tables/rows`）: 4 MiB（4,194,304 バイト）
  - 削除（`POST /api/tables/rows/delete`）: 64 KB
  - 超えたら 413 `PAYLOAD_TOO_LARGE`。
- 書き込みの値は、1つ 10,000 字（コードポイント）までとする。DSL・DB の長さの方が短ければ、そちらに従う。超えたら 400 `TOO_LONG` にする。
- 主キーの値は、1つ 1,000 字までとする（U1 の NFR4.5 と同じ）。超えたら 400 `INVALID_VALUE` にする。
- 最悪の本文は、100 カラム × 10,000 字 × 4 バイトでおよそ 4.0 MB となり、上限に収まる。

**確かめ方**

- 上限ちょうどは受け付け、超えると 413 になる境界のテスト（道ごと）。
- 10,000 字ちょうどと 10,001 字の境界のテスト。
- 主キーの 1,000 字と 1,001 字の境界のテスト。
- どの応答にも値が入らないことを確かめる。

**持ち主の段**: Code Generation（B4）

### NFR4.10 認可・一括代入・IDOR

**要件**

- 3本の API は「ログインだけ」（`@ApiAccess(AUTHENTICATED)`）に分類する。
- テーブルの権限は、業務処理の層で要求ごとに判定する（BR13.1）。
- 書けないカラムを本文に足しても反映せず、400 で拒む。
- `definitionId` を差し替えた要求は 409 で拒む。
- 次の要求は拒み、状態を変えない: DSL に無い名前、権限の無いテーブル、無い行。
- 画面で入口を隠すことを、判定の代わりにしない。

**確かめ方**

- API の分類の網羅のテストに3本を載せる。
- 401・403・200 の表のテスト（パラメーターを使う。403 の利用者は、要る補助権限だけを欠く人にする）。
- 作業ロールを切り替えた直後の次の要求で、判定が切り替わることを確かめる。

**持ち主の段**: Code Generation（B4）

### NFR4.11 書き込みの設定の欠け

**要件**

- 書き込みのユーザー名とパスワードの片方だけがあるときは、項目の名前だけを WARN で1回出す。値は出さない。
- 起動と読み取りは続ける。
- 書き込みは 409 `BUSINESS_WRITE_NOT_CONFIGURED` にする（BR9.1・BR1.7）。

**確かめ方**: 設定が両方ある・片方だけ・どちらも無い場合の、起動の結合テストと応答の結合テスト。

**持ち主の段**: Code Generation（B4）

### NFR4.13 監査の中身と切り詰め

**要件**

- 監査の行に、`target_schema`・`target_table`・`work_role_id` の3列と、`detail` の主キーを置く。
- `detail` が 16,384 字を超えたら、値を後ろから切って上限に収め、`keyTruncated` を真にする。黙って切らない。
- 主キー以外の値と、更新の前後の値は残さない（BR11.2・BR11.4）。
- 監査の理由の値は応答の code とは別の列挙にし、すべて 32 字以内とする（BR11.6）。

**確かめ方**

- 主キーの長さの境界（`detail` がちょうど上限・超える）の結合テスト。
- 監査の理由の値と種類の字数の単体テスト。

**持ち主の段**: Code Generation（B4）

### NFR4.14 主キー・ビューの決まりの 409 の監査の範囲（R-04）

**要件**

- ⑥ の拒否（409 `BUSINESS_ROW_OPERATION_NOT_ALLOWED`）を監査に残すのは、その操作の補助権限を持つ人の拒否だけにする（Q5: A）。
  - 作成: CREATE を持つ人
  - 削除: DELETE を持つ人
  - 更新: FULL のカラムを1つ以上持つ人
- 応答（409）と判定の順（ADR-006）は変えない。
- 主権限が READ だけの人が繰り返し送っても、監査の行は増えない。
- 増え方の見積もりは `scalability-requirements.md` の NFR2.9 に書く。

**確かめ方**: 補助権限を持つ人の 409 は残り、持たない人の 409 は残らないことを、作成・更新・削除のそれぞれで確かめる結合テスト。

**持ち主の段**: Code Generation（B4）

### NFR4.16 MySQL・MariaDB の付与の範囲（R-06）

**要件**

- MySQL・MariaDB の書き込みのアカウントの付与を、DSL の対象のテーブルに絞れるかを確かめる。
- 絞れないときは、残る広さ（対象のデータベースの全部の表の行の操作）を README とこの単位の文書に書く。
- PostgreSQL の表の付与と、範囲のそろえ方を書く。

**確かめ方**: 基盤の設計で、見本の初期化の台本で確かめる。

**持ち主の段**: 基盤の設計（Infrastructure Design）

### NFR7.5 業務データの必須のテスト（書き込みの分）

**要件**: team.md の「業務データの一覧・詳細（と作成・更新・削除）」の必須のテストのうち、書き込みの分を書く。

- 権限ごとの操作の可否（401・403・200 の表）
- 主キーとビュー（409）と、複合の主キー
- IDOR と一括代入
- 403 と 404 の使い分け（判定の順のとおり）
- SQL の組み立て
- 3種類の DB の境界の値（`BIGINT` の上限・`DECIMAL(38,10)`・NULL を丸めずに書く）
- 時間切れ（応答しない対象DB は ServerSocket で作る）
- 接続の数
- 漏えい
- 監査（★の中身は機能設計で決めたとおり）

対象DB へ書き込むテストは、次の形で行う。

- アプリのトランザクションの巻き戻しに頼らない。
- テストのクラスごとに、名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）と表を作り、終わったら消す。
- 書いた後の値は、アプリとは別の接続で読み直して確かめる。

**確かめ方**: `XxxTest` と `XxxIT`（3種類の DB は Testcontainers）。どれも `./gradlew verify` の中で流す。

**持ち主の段**: Code Generation（B4）

### NFR7.6 性質ベースのテスト

**要件**: 次の2つに、jqwik の性質ベースのテストを当てる。失敗したときの乱数の種を記録する。

- `WriteRequestValidator`
  - 長さ・桁・範囲の境界の内側は受け付け、外側は拒む
  - 書けないカラムは必ず拒む
- pattern の打ち切り
  - 任意の値と式で、合計の締め切りを超えて照合が続かない

**確かめ方**: jqwik のテスト（`verify` の中）

**持ち主の段**: Code Generation（B4）

## 上流との差

- **承認の場の決定**（`construction/functional-design/memory.md` の Open questions。U2 の R-07 と U3 の R-09 を受けたもの）
  - `nullable` は U2 が足す検証の材料にする。`functional-spec.md` 1.1 の表の `nullable` の行（「U1 の読み取りの項目と同じ値。重ねて持たない」）は、U2（B4）が持つ項目に読み替える。
  - `formPart` の値域から `lookup` を外す。`lookup` のカラムは `text` として返す。7節の X10 は、足す項目に `nullable` を数えていない。その差もここに書く。
  - U3 は `nullable`・`required` が欠けたら形の崩れとみなす。そのため U2 は、書けないカラムを含むすべてのカラムで、この2つを省かずに返す。書けないカラムの `required` は偽、`nullable` は DB の値をそのまま返す。
  - BR14.1 の「既定（偽）」との読みは、NFR 設計で1行そろえる。
- **R-07**（定義の項目の形の食い違い）は、上の決定で解けたとして記録する。
- **R-08**（書き込みの口が DB の種類を持たない）は、`reliability-requirements.md` の NFR4.12 で受ける。
- **Q1: A**: BR5.1・BR6.2 の「カラムごとに問い合わせる」を、「まとめて1文」に読み替える。
- **Q3: A**: BR3 に、書き込みの値の 10,000 字の上限と、主キーの 1,000 字の上限を足す。長さの制限の無い TEXT 系のカラムでも、アプリからは 10,000 字を超える値を入れられない。
- **Q5: A**: BR11.3 の「⑥ の拒否を残す」を、「その操作の補助権限を持つ人の拒否だけを残す」に狭める。
- **NFR1.4 を U2 が使うこと**: U1 の文書の「U3 は NFR1.4 以降」との差は、`performance-requirements.md` に書いた。

## 後の段に回すこと

- **NFR 設計の試し**
  - 一意の事前の問い合わせと違反の後の確かめを、1文にまとめる形（3種類の DB）。
  - pattern の打ち切りの包みの形と、締め切りを見る間隔。
  - 4 MiB の本文 × 10 VU のヒープ。
  - 名前つきの書き込みの部品と、内部DB の注入の衝突。
  - 書き込みの口が貸す DB の種類つきの型の名前と置き場（R-08）。
- **コード生成の計画（B4）**
  - SpotBugs の除外を広げること（承認）。
  - `RoleBoundaryArchitectureTest` は U1（B2）の変更のまま使う。
  - `audit` の本体に手を入れることに伴う、既存のテストとカバレッジの洗い出し。
- **基盤の設計**
  - NFR4.16 の付与の範囲。
  - 見本の書き込みのアカウントの初期化と、README の手順。
  - `.env.example` の項目。
- **Performance Validation・Observability Setup**: この文書の要件には持ち主が無い。

## U3 とそろえる点

- **番号**: U3 は NFR1.7・2.10・3.7・4.20・7.7・8.7 から振る。
- **定義の項目**: 平らに置く（`formPart`・`required`・`nullable`・`minLength` など）。`required`・`nullable` は必ず値を持つ。`formPart` に `lookup` は来ない。
- **値の長さ**: 10,000 字の上限は、画面の入力の `maxLength` にも当てる。切ったカラムを更新で読み取りにする U3 の D19 は、画面だけの守りになる（サーバーは、切ったかどうかを知らない）。
- **pattern**: 画面では当てない（`hasPattern` は目印だけ）。`PATTERN_TIMEOUT` の文を用意する。
- **503 の出し分け**: `BUSINESS_TARGET_DB_TIMEOUT` は「保存されたか分からない」、`UNAVAILABLE` は「保存されていない」として出す。
- **413**: 413 の文を用意する。画面で本文の大きさを先に確かめるかは U3 で決める。
- **E2E**: 業務データの代表の流れ1本まで。U2 は E2E を足さない。
- **漏えいの確かめ**: `*SecretLeakIT` の形を3単位でそろえる。
