# Business Rules — U4 監査ログ（u4-audit-log）

U4 の決まり。出典は要件定義書の FR・NFR、`functional-design-questions.md` の確定回答（Q1〜Q4）、U1〜U3 の決まり（「U1 の決まり x.y」などと書く）である。

```yaml
rules:
  # ---- BR1 受け取りと記録 ----
  - id: BR1.1
    statement: U2 の認証の出来事（ログイン成功・ログイン失敗・ログアウト）と、U3 のアクセス拒否の出来事を受け取り、1件ずつ監査イベントとして記録する
    category: policy
    applies_to: AuditEvent
    trigger: 出来事の受け取り
    logic: entities.md の対応表に従って項目を写す。出来事を知らせる側（U2・U3）は U4 を知らない
    violation: —
    source: FR9.1、Domain Design ADR-004
  - id: BR1.2
    statement: result は出来事の種類から決める。ログイン成功とログアウトは成功、ログイン失敗とアクセス拒否は失敗とする
    category: calculation
    applies_to: AuditEvent.result
    trigger: 記録
    logic: LOGIN_SUCCEEDED・LOGGED_OUT → SUCCESS、LOGIN_FAILED・ACCESS_DENIED → FAILURE
    violation: —
    source: FR9.2
  - id: BR1.3
    statement: 元の操作が内部DBを更新する場合は、その更新が確定（コミット）した後に、別のトランザクションで記録する。内部DBの更新を伴わない出来事は、受け取ったときに記録する。どちらも、出来事を知らせた要求と同じスレッドで行う
    category: policy
    applies_to: AuditEvent
    trigger: 出来事の受け取り
    logic: ログイン成功・ログイン失敗・ログアウトは、U2 の更新（失敗回数・リフレッシュトークン）の確定後に記録する。アクセス拒否（U3）は、その場で記録する
    violation: —
    source: Q1、U1 functional-spec.md 6.1
  - id: BR1.4
    statement: 元の操作の内部DBの更新が取り消された（ロールバックされた）場合は、その出来事を記録しない
    category: policy
    applies_to: AuditEvent
    trigger: 元の操作のロールバック
    logic: 確定後に記録する仕組み（BR1.3）により、取り消された操作の出来事は記録に至らない
    violation: —
    source: Q1
  - id: BR1.5
    statement: 記録する日時は、出来事が起きた日時（受け取った出来事の日時）とする
    category: policy
    applies_to: AuditEvent.occurredAt
    trigger: 記録
    logic: occurredAt=出来事の occurredAt
    violation: —
    source: Q4
  - id: BR1.6
    statement: 存在しないメールアドレスでのログイン失敗も、入力されたメールアドレスのまま記録する
    category: policy
    applies_to: AuditEvent
    trigger: LOGIN_FAILED（USER_NOT_FOUND）
    logic: enteredEmail は利用者を参照しない文字列として記録する
    violation: —
    source: FR9.2、チームの進め方（監査ログの必須テスト）

  # ---- BR2 記録の内容 ----
  - id: BR2.1
    statement: 入力されたメールアドレスは 254 文字、User-Agent は 512 文字を超える分を切り詰めて記録する
    category: validation
    applies_to: AuditEvent
    trigger: 記録
    logic: 長さを超える分を捨てる。それ以外の加工はしない
    violation: —
    source: Q2
  - id: BR2.2
    statement: 記録する値は加工せずに保存し、表示や出力するときに無害化する
    category: constraint
    applies_to: AuditEvent
    trigger: 記録・出力
    logic: 本Intentでの出力はアプリのログ（BR3.1）だけであり、JSON の値として出力するため改行などで記録を偽装されない（U1 の決まり 3.1・3.5）。監査ログを見る画面は後続Intentで作り、その画面で無害化する
    violation: —
    source: Q2、construction フェーズの入力のサニタイズの決まり
  - id: BR2.3
    statement: 監査イベントに、パスワード・トークン・ハッシュ値を含めない
    category: constraint
    applies_to: AuditEvent
    trigger: 記録
    logic: 受け取る出来事にもともと含まれない（U2 の決まり 7.2、U3 の決まり 3.4）。U4 でも項目を増やさない
    violation: テストで記録にパスワード・トークンの値が含まれないことを確かめる
    source: NFR3、project.md Forbidden
  - id: BR2.4
    statement: 監査イベントのトレースIDは、同じ要求で出たアプリのログのトレースIDと一致させる
    category: constraint
    applies_to: AuditEvent.traceId
    trigger: 記録
    logic: 出来事に載ったトレースIDをそのまま記録する。記録は同じスレッドで行う（BR1.3）
    violation: テストでアプリのログと監査ログのトレースIDが一致することを確かめる
    source: FR10.2

  # ---- BR3 書き込みの失敗 ----
  - id: BR3.1
    statement: 監査イベントの書き込みに失敗しても、元の操作は続ける。失敗したことと記録しようとした内容を、アプリのログに ERROR で1回出す
    category: policy
    applies_to: AuditEvent
    trigger: 書き込みの失敗
    logic: 失敗の例外は U4 の中で受け止め、出来事を知らせた側へ伝えない。ログには、記録しようとした全項目（メールアドレスを含む）をキーと値で載せる。再試行はしない
    violation: テストで、書き込みに失敗してもログイン・ログアウト・アクセス拒否の応答が変わらず、ERROR のログが出ることを確かめる
    source: FR9.4、Q3
  - id: BR3.2
    statement: 監査ログはアプリのログとは別の仕組み（内部DB）に記録し、アプリのログ出力で代用しない
    category: constraint
    applies_to: AuditEvent
    trigger: 記録
    logic: アプリのログに出すのは、書き込みに失敗したときだけ（BR3.1）
    violation: —
    source: FR9.5

  # ---- BR4 保存 ----
  - id: BR4.1
    statement: 監査イベントは追記だけとし、変更・削除の機能を作らない。保存期間は無期限とする
    category: constraint
    applies_to: AuditEvent
    trigger: —
    logic: 監査イベントを変更・削除する処理・API を持たない
    violation: —
    source: FR9.3
```

## 決まりの一覧

| ID | 分類 | 決まり（要約） | 出典 |
|---|---|---|---|
| BR1.1 | policy | U2・U3 の出来事を受け取り1件ずつ記録 | FR9.1 |
| BR1.2 | calculation | 結果は出来事の種類から決める | FR9.2 |
| BR1.3 | policy | 更新の確定後に別トランザクションで記録（更新のない出来事はその場で）、同じスレッド | Q1 |
| BR1.4 | policy | 取り消された操作の出来事は記録しない | Q1 |
| BR1.5 | policy | 日時は出来事が起きた日時 | Q4 |
| BR1.6 | policy | 存在しないメールアドレスでの失敗も記録 | FR9.2 |
| BR2.1 | validation | メールアドレス 254、User-Agent 512 文字で切り詰め | Q2 |
| BR2.2 | constraint | 値は加工せず保存、出力時に無害化 | Q2 |
| BR2.3 | constraint | 秘密情報を含めない | NFR3 |
| BR2.4 | constraint | トレースIDをアプリのログと一致させる | FR10.2 |
| BR3.1 | policy | 書き込み失敗でも操作は続け、ERROR で内容を1回出す | FR9.4、Q3 |
| BR3.2 | constraint | アプリのログで代用しない | FR9.5 |
| BR4.1 | constraint | 追記のみ、変更・削除なし、無期限 | FR9.3 |
