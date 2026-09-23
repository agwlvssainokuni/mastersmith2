# Rules — U1 対象DB（u1-target-db）

```yaml
rules:
  - id: BR1.1
    statement: 対象DB の接続先は mastersmith.target-db.* の設定だけから受け取る
    category: policy
    applies_to: 接続先の設定
    trigger: アプリの起動
    logic: "IF 接続先の値が設定（application.yml・環境変数）以外から渡される THEN 使わない（画面・API・DSL から受け取る口を持たない）"
    violation: 口を作らないことで起きない（構造で守る）
    source: FR2.1、NFR4
  - id: BR1.2
    statement: 設定がまったく無いときは、対象DB を「使わない」として扱い、起動を続ける
    category: policy
    applies_to: 接続先の設定
    trigger: アプリの起動
    logic: "IF mastersmith.target-db.* の項目が1つも無い THEN WARN を出さず、読み取りは『設定が無い』の結果を返す"
    violation: —
    source: FR2.4
  - id: BR1.3
    statement: 設定の一部が欠けている・不正なときは、項目の名前だけを WARN で1件出して起動を続ける
    category: validation
    applies_to: 接続先の設定（種類・ホスト・ポート・DB 名・スキーマ名・ユーザー名・パスワード）
    trigger: アプリの起動
    logic: "IF 必須の項目が欠けている、または種類が MySQL・MariaDB・PostgreSQL のどれでもない、またはポートが数でない THEN 問題のある項目の名前だけを並べた WARN を1件出し、読み取りは『設定が無い』の結果を返す"
    violation: 起動は止めない。値（接続先・ユーザー名・パスワード）はログに出さない
    source: FR2.4、NFR4
  - id: BR1.4
    statement: パスワードは設定の型の文字列表現・ログ（TRACE を含む）で伏せ字にする
    category: constraint
    applies_to: 接続先の設定
    trigger: 設定の値を文字列にするとき
    logic: "IF 設定の型を文字列にする THEN パスワードは伏せ字（値の有無だけ）にする"
    violation: —
    source: FR2.2、NFR4
  - id: BR1.5
    statement: 対象DB の接続は、内部DB の既定の接続を置き換えない
    category: constraint
    applies_to: 接続の持ち方
    trigger: アプリの起動
    logic: "IF 対象DB の接続を足す THEN 既定の候補にしない。ログイン・監査・Flyway・ヘルスチェック・名前の指定の無いトランザクションは内部DB を使い続ける"
    violation: 起動時の結合テストで検出する
    source: FR2.3（ADR-006）
  - id: BR1.6
    statement: 対象DB の接続は読み取り専用で、スキーマ・データを変更しない
    category: constraint
    applies_to: 対象DB への問い合わせ
    trigger: 読み取りのたび
    logic: "IF 対象DB に問い合わせる THEN 読み取り専用の接続で、メタデータの読み取りだけを行う（書き込み・DDL を発行しない）"
    violation: —
    source: FR2.5
  - id: BR1.7
    statement: 対象DB には、初めて使うときに接続する（起動時には接続しない）。ヘルスチェックに含めない
    category: policy
    applies_to: 接続
    trigger: 読み取りの要求
    logic: "IF アプリが起動する THEN 対象DB に接続しない。IF ヘルスチェックを求められる THEN 内部DB だけで判断する"
    violation: —
    source: FR2.4、NFR7
  - id: BR1.8
    statement: 接続・問い合わせに待ち時間の上限を置き、超えたら『接続できない（TIMEOUT）』とする
    category: constraint
    applies_to: 接続・問い合わせ
    trigger: 読み取りのたび
    logic: "IF 接続または問い合わせが上限の時間を超える THEN 打ち切り、UNAVAILABLE（TIMEOUT）を返す（数値は NFR 設計）"
    violation: —
    source: FR2.4、NFR1
  - id: BR1.9
    statement: 接続に失敗したら『接続できない（CONNECTION_FAILED）』とし、内部の例外のメッセージと接続先を結果に含めない
    category: constraint
    applies_to: 読み取りの結果
    trigger: 接続・問い合わせの失敗
    logic: "IF 接続・認証・問い合わせに失敗する THEN UNAVAILABLE（CONNECTION_FAILED）を返す。ログは原因の種類だけを WARN で出し、値は出さない"
    violation: —
    source: FR2.4、NFR4、NFR5
  - id: BR2.1
    statement: 読み取りの範囲は、設定したスキーマの中のテーブルとビューだけ
    category: constraint
    applies_to: メタデータの読み取り
    trigger: 読み取りのたび
    logic: "IF 読み取る THEN 設定したスキーマの、テーブルとビューだけを対象にする（システムのスキーマ・ほかのスキーマ・シノニム・マテリアライズドビューは含めない）"
    violation: —
    source: FR3.2
  - id: BR2.2
    statement: スキーマ全体をまとめて読む（テーブルごとの問い合わせの繰り返しを避ける）
    category: policy
    applies_to: メタデータの読み取り
    trigger: 読み取りのたび
    logic: "IF スキーマを読む THEN テーブル・カラム・主キー・外部キー・コメントを、テーブルの数に比例しない回数の問い合わせで読む（具体の方法は NFR 設計）"
    violation: 性能の目標（30 秒）を超えたら NFR 設計で見直す
    source: NFR1
  - id: BR2.3
    statement: 物理名は DB が返した大文字・小文字のまま扱う
    category: constraint
    applies_to: テーブル名・カラム名
    trigger: 読み取り
    logic: "IF 名前を読む THEN 変換せずに写しに入れる"
    violation: —
    source: FR3.2
  - id: BR2.4
    statement: 識別子を SQL に組み込むときは、読み取った名前の一覧にあるものだけを、DB の種類に合った引用符で囲む
    category: constraint
    applies_to: 対象DB への問い合わせ
    trigger: 識別子を使う問い合わせ
    logic: "IF 識別子を問い合わせに入れる THEN 読み取った名前の一覧にあるか確かめ、引用符で囲む（中の引用符は二重にする）。一覧に無ければ問い合わせない。利用者の入力から識別子を作らない"
    violation: 問い合わせをせずに想定外の失敗とする
    source: NFR6
  - id: BR2.5
    statement: 空の文字列のコメントは、コメントが無いものとして扱う
    category: constraint
    applies_to: テーブル・カラムのコメント
    trigger: 読み取り
    logic: "IF コメントが空の文字列、または空白だけ THEN コメント無しとする"
    violation: —
    source: FR3.3
  - id: BR2.6
    statement: 別のスキーマを参照する外部キーは、写しに含めない
    category: constraint
    applies_to: 外部キー
    trigger: 読み取り
    logic: "IF 外部キーの参照先が設定したスキーマの外 THEN その外部キーを写しに含めない"
    violation: —
    source: FR3.2
  - id: BR2.7
    statement: 3種類の DB の違い（型の名前・コメントの取り方・ビューの見分け方）を吸収し、同じ形の写しを返す
    category: policy
    applies_to: メタデータの読み取り
    trigger: 読み取り
    logic: "IF DB の種類が MySQL・MariaDB・PostgreSQL のどれか THEN 種類ごとの読み方で、同じ形（TargetSchema）に揃える。型の名前は DB が返した名前のまま入れる（共通の分類への対応は U3）"
    violation: —
    source: FR2.1、NFR12
```

## 決まりの一覧

| ID | 決まり | 分類 | 出典 |
|---|---|---|---|
| BR1.1 | 接続先は設定だけから受け取る | 方針 | FR2.1、NFR4 |
| BR1.2 | 設定がまったく無いときは使わないとして起動を続ける | 方針 | FR2.4 |
| BR1.3 | 設定の欠け・不正は項目名だけを WARN、起動を続ける | 検証 | FR2.4、NFR4 |
| BR1.4 | パスワードは伏せ字 | 制約 | FR2.2、NFR4 |
| BR1.5 | 内部DB の既定の接続を置き換えない | 制約 | FR2.3 |
| BR1.6 | 読み取り専用、変更しない | 制約 | FR2.5 |
| BR1.7 | 起動時に接続しない、ヘルスチェックに含めない | 方針 | FR2.4、NFR7 |
| BR1.8 | 待ち時間の上限で打ち切る | 制約 | FR2.4、NFR1 |
| BR1.9 | 接続の失敗は内部の文言と接続先を含めない | 制約 | FR2.4、NFR4、NFR5 |
| BR2.1 | 設定したスキーマのテーブルとビューだけ | 制約 | FR3.2 |
| BR2.2 | まとめて読む | 方針 | NFR1 |
| BR2.3 | 物理名はそのまま | 制約 | FR3.2 |
| BR2.4 | 識別子は一覧にあるものだけを引用符で | 制約 | NFR6 |
| BR2.5 | 空のコメントは無し | 制約 | FR3.3 |
| BR2.6 | 別のスキーマへの外部キーは含めない | 制約 | FR3.2 |
| BR2.7 | 3種類の DB の違いを吸収する | 方針 | FR2.1、NFR12 |
