# セキュリティの設計 — U2 u2-reference-write-check

作成・更新の保存前の参照の確かめで、権限を欠く人が参照先の値を保存しない・推測しない・値を漏らさない作りを決める。`team.md` の他テーブル参照の必須のテスト (b)・(d)・(g)〜(k) の置き場もここに書く。

## 出典

- この単位の NFR 要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/nfr-requirements/security-requirements.md`（NFR1.21〜NFR1.31・NFR5.21・NFR5.22）、`observability-requirements.md`（NFR1.32）
- この単位の機能設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/functional-design/`（`rules.md` の BR2.1・BR3.3・BR3.4・BR4.2・BR5・BR7、`functional-spec.md` の 4.3節）
- 契約 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-summary.md`（C1・C3・C6）
- U1 の NFR 設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-design/security-design.md`（`check`・`countState` の読み方。継ぎ目だけ）
- この段の答え `nfr-design-questions.md` と試しの結果 `trial-results.md`（3節・4節）
- コード（読み取りだけ）: `backend/src/main/java/cherry/mastersmith/businessdata/service/BusinessDataWriteService.java`、`common/observability/TraceAspect.java`、`common/error/web/GlobalExceptionHandler.java`

## 1. 判定の置き場と材料（NFR1.21・NFR1.23）

- ⑩ は `BusinessDataWriteService` の中で、①〜④ で解決の口から取った同じ `PermissionSnapshot`（`TableAccessView` が持つ写し）だけを材料にする。⑩ で写しを取り直さず、内部DB を読まない（BR4.2・C6）。作業ロールを切り替えた直後の次の要求では、その要求の写しで判定するため結果が切り替わる。
- 確かめる参照と参照先（スキーマ・テーブル・`valueColumn`）は、写しの DSL から U1 の `targetOf` で引く。本文の名前・値から参照先を作らない。本文で別の参照先の値に差し替えても、そのカラムの DSL の参照先でしか確かめない（IDOR、`team.md` の (b)・(g)）。本文の DSL に無いカラムは、⑨ の既存の検証（`NOT_ALLOWED`）で先に拒否される。
- 既存の 401・403 の判定の順（①〜⑨）は変えない。⑩ は ⑨ の後にだけ動き、①〜⑨ で拒んだ要求は対象DB に触れない（BR1.1）。
- 画面が選択肢を出さないことを、この判定の代わりにしない（`project.md` の Mandated）。

## 2. 推測させない応答（NFR1.22・NFR1.26）

| 場合 | 問い合わせ | 応答 |
|---|---|---|
| 参照先に無い値 | `check` を流す | 409 `BUSINESS_REFERENCE_NOT_FOUND`、項目の誤り `REFERENCE_NOT_FOUND` |
| 読めない参照先の値（`accessOf` が `NO_PERMISSION`） | 流さない（U1 の `check` が問い合わせずに返す） | 同じ |
| 型として読めない値 | 流さない（同上） | 同じ |
| 作成の必須の参照項目が `NO_PERMISSION`・`TOO_MANY` で値あり | `check` を流さない（`countState` は `TOO_MANY` のときだけ流れる） | 同じ |
| 作成の必須の参照項目が `NO_PERMISSION`・`TOO_MANY` で空 | 同上 | 409、`NOT_EDITABLE_NOT_NULL`（field はカラムの名前） |
| 作成の必須の参照項目が `AVAILABLE` で空 | `countState` だけ | 400（保留した既存の誤り） |

- 前の4行は、状態コード・`code`・`fieldErrors` の構造・メッセージを同一にする（BR7.2）。拒否の応答に、問い合わせを流したかを出さない。
- 「推測させない」の範囲は、値を送った場合の参照先の値の存在と、他人の権限に限る。空の本文での 409 と 400 の分かれは、自分の読む権限が分かるだけで、定義の状態（U1）からも分かるため受け入れる（NFR1.22。M1: A の範囲）。
- 時間の差: 読めない値では問い合わせを流さないため、応答が数 ms 早い（`trial-results.md` 2.2 の `check` 0.4〜0.5 ms）。差は既存の処理の揺れ（10 本の同時で p50 と p95 の差 3〜10 ms）より小さく、権限は自分の権限のため、時間をそろえる仕組みは置かない（受け入れる制約）。

## 3. SQL と境界（NFR1.24、`team.md` の (k)）

- U2 は SQL を組み立てない。読み取りは U1 の口（`check`・`countState`）と、既存の `RowSqlExecutor.detail`（更新前の行）だけを使う。値はバインド変数で渡り、識別子は DSL と照らした名前だけが方言の部品で引用される。
- 次は変えない: `java.sql.Connection` の `prepareStatement`・`createStatement` を呼んでよい場所、`backend/config/spotbugs-exclude.xml`、既存の境界テスト（`BusinessDataBoundaryArchitectureTest`・`TargetDbBoundaryArchitectureTest`・`RoleBoundaryArchitectureTest`・`ArchitectureTest`）。値を束ねる SQL を作らない（`performance-design.md` 1節）ため、SQL の部品は増えない。
- すき間のテストの差し込み口は、テストだけの部品で作り、本番のクラス・公開の口・境界テストの許可の一覧を変えない（`reliability-design.md` 4節）。

## 4. 漏えい（NFR1.25・NFR1.32、`team.md` の (d)）

### 4.1 値を持つ型

| 型（仮の名前、機能設計の `entities.md`） | 中身 | 伏せ方 |
|---|---|---|
| `ReferenceCheckItem` | 参照元のカラム・参照先・`ReferenceValue`・由来 | `toString` はカラムの名前と由来だけ |
| `PreviousReferenceValues` | 更新前の参照の値 | `toString` はカラムの名前の一覧だけ |
| `ReferenceStepOutcome` | ⑩ の結果と項目の誤り | 項目の誤りはカラムの名前と理由だけで値を持たない |
| `ReferenceValue`（U1）・`TypedValue`（既存） | 値 | 既存のとおり伏せる |

- `TraceAspect` は `web`・`service`・`domain`・`repository` の引数と戻り値を TRACE に文字列で出すため、⑩ の内部の部品の引数・戻り値はすべて上の型で渡し、汎用の `Map` のまま層をまたがない（`team.md` の Code Style）。
- JDBC の例外のメッセージは、U1 の口と既存の `RowSqlExecutor` がすでに捨てた形で受ける。U1 の口の失敗は `ReferenceReadFailure`（`businessdata.domain`。`reason`・`sqlState`・`vendorCode`・`exceptionClass`・`budgetExceeded` だけで、メッセージ・行の値を持たない）、更新前の行の `detail` の失敗は既存の `RowQueryFailure` を `ReferenceReadFailure.of` で写して受ける（`reliability-design.md` 2節）。

### 4.2 ログ・応答・監査・トレース

- ログ: ⑩ の対象DB の失敗を code に写す1か所（`BusinessDataWriteService` の ⑩ の写し）で WARN を1行（`observability-design.md` 2節）。参照の値・行の値・カラムの名前の値・接続先・例外のメッセージを出さない。
- 応答: 項目の誤りは field（カラムの名前）と reason だけ。503・409 MISMATCH は既存の Problem Details のまま。
- 監査: 拒否は既存の出来事に結果 `FAILURE`・理由 `BUSINESS_REFERENCE_NOT_FOUND`。`detail` は作成は空、更新は参照元の主キーだけ（BR5.2）。確かめのための読み取りは残さない（BR5.3）。監査は ⑩ の読み取りの接続を返した後に出来事を出し、既存の聞き手が別の短いトランザクションで追記する（NFR1.30）。
- トレース: 道は既存の固定の道（`/api/tables/rows`）で、値は本文で運ぶため、道の属性に値は残らない。新しいスパンは足さない。

### 4.3 確かめのテスト

| テスト | 中身 |
|---|---|
| 漏えいの結合テスト（例: `ReferenceWriteSecretLeakIT`。既存の `BusinessDataWriteSecretLeakIT` に足すか別のクラスかはコード生成の計画） | TRACE を有効にし、架空の目印の値を参照の値・参照先の行・本文に入れて、作成・更新の成功・409・時間切れ（`ServerSocket`）・`MISMATCH` を流す。ログの全文・応答・監査の行の全部の列に目印が無いこと。`BusinessDataWriteService` のロガーの WARN のキーが決めた一覧だけであること |
| トレースの結合テスト | 外部へ送る設定を有効にして、属性に目印が無いこと（既存の `BusinessDataTraceExposureIT` の形） |

## 5. 必須の参照項目と型の確かめ（NFR1.26〜NFR1.28）

- 必須の参照項目の升目（`functional-spec.md` 4.3節）を、パラメーターを使う表の結合テストで確かめ、`NO_PERMISSION` で値を送ったときに `check` の問い合わせが流れないことを、文の記録（既存の `StatementLog`）で確かめる。
- 参照先の形（複合の主キー・主キーの無いテーブル・ビュー・自己参照・一意でない `valueColumn`）は U1 の `check` の読み方に従い、U2 は比べ直さない（BR4.3）。自己参照の作成では、同じ要求で作る行は参照先に無い（⑩ は書き込みの前）ため 409 になる。
- 3種類の DB で、型の違い・大文字と小文字・末尾の空白・`1.0` と `1`・境界の値・NULL と `""` を流す（NFR1.28）。書き込みのテストの決まり（テストのクラスごとに名前の重ならないスキーマを作って消す、別の接続で読み直す）に従う。

## 6. 性質ベースのテスト（NFR1.31）

jqwik の対象の純粋な関数（`businessdata.domain`）と性質:

| 関数 | 性質 |
|---|---|
| 値が更新前と同じかを型つきの値で比べる関数（BR2.5） | 反射・対称。`"007"` と `"7"`（整数）は同じ。`1.0` と `1` は同じ。大文字と小文字・末尾の空白の違いは違う。読めない・切られた更新前の値は「違う」 |
| 必須の参照項目の状態と本文から結果を決める関数（BR3.2〜BR3.5） | `NO_PERMISSION`・`TOO_MANY` で値があれば必ず拒否。権限を足しても（`NO_PERMISSION` → `AVAILABLE`）保存できる組が減らない |
| 確かめの結果を拒否と code に写す関数（BR4.4・BR1.3） | `FAILED` があれば拒否より先にその code。`NOT_FOUND_OR_DENIED` は必ず `REFERENCE_NOT_FOUND` |

失敗時の乱数の種を記録する（`team.md`）。

## 7. カバレッジ（NFR5.21・NFR5.22）

- 手を入れる見込みのパッケージは `businessdata.service`・`businessdata.domain`（`businessdata.web` は応答の写しに reason が増えないため変わらない見込み）。どれも `packagesJudgedByTotal` に入っていないため、パッケージごとの下限（行 80%・分岐 70%）の対象のまま。コード生成の計画で手を入れるパッケージを洗い出して突き合わせる。

## 上流との差

| 上流 | 差 | 扱い |
|---|---|---|
| NFR 要件 NFR1.29（差し込み口の例「`ReferenceLookup` を包む部品」） | `TargetWriteAccess` の Bean を包む部品にした | `reliability-design.md` 4節。作成と更新の両方で止められ、`ReferenceLookup` を包むと更新の「変わらない」の経路で止められないため |
| NFR 要件 NFR1.32（同じ失敗について WARN がちょうど1行） | U2 の業務処理のロガー（`BusinessDataWriteService` のロガー）の診断の WARN をちょうど1行とし、既存の `GlobalExceptionHandler` がすべての業務の誤りに出す変換の WARN（`code`・`status` だけ）は別の行として残す | `observability-design.md` 2節。依頼者が承認の場で受け入れた（`observability-design.md`「承認の場の決定」） |

## 後の段に回すこと

- **Code Generation**: 4.1 の型の `toString`、4.3・5・6 のテスト、7 のパッケージの突き合わせ。
