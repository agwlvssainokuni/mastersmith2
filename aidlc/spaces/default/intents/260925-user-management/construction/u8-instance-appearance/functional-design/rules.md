# Rules — U8 インスタンスの見た目の設定（u8-instance-appearance）

設定値はエンティティにせず、振る舞いをここに決まりとして書く（`entities.md` を参照）。群は BR1 設定の読み取りと解決、BR2 警告のログ、BR3 公開の API とする。

```yaml
rules:
  # ---- BR1 設定の読み取りと解決 ----
  - id: BR1.1
    statement: ブランドカラーの許される値は blue・green・purple・orange の4つとする
    category: validation
    applies_to: ブランドカラーの設定（mastersmith.appearance.brand-color、環境変数 MASTERSMITH_APPEARANCE_BRAND_COLOR）
    trigger: アプリの起動
    logic: "IF 設定の値の前後の空白を除き、大文字・小文字を問わずに比べて blue・green・purple・orange のどれかと一致する THEN その値を小文字の名前として採る（例: 'Blue'・' green ' は blue・green）。IF どれとも一致しない THEN 許されない値として BR1.4 に従う"
    violation: 許されない値は BR1.4 のとおり既定に置き換える。起動は止めない
    source: FR8.1、FR8.2、Q2 A
  - id: BR1.2
    statement: フォントファミリーの許される値は sans・serif の2つとする
    category: validation
    applies_to: フォントファミリーの設定（mastersmith.appearance.font-family、環境変数 MASTERSMITH_APPEARANCE_FONT_FAMILY）
    trigger: アプリの起動
    logic: "IF 設定の値の前後の空白を除き、大文字・小文字を問わずに比べて sans・serif のどちらかと一致する THEN その値を小文字の名前として採る。IF どちらとも一致しない THEN 許されない値として BR1.4 に従う"
    violation: 許されない値は BR1.4 のとおり既定に置き換える。起動は止めない
    source: FR8.1、FR8.2、Q2 A
  - id: BR1.3
    statement: 設定が無いときは既定（ブランドカラー blue、フォントファミリー sans）を使い、警告は出さない
    category: policy
    applies_to: ブランドカラー・フォントファミリーの設定
    trigger: アプリの起動
    logic: "IF 項目が設定されていない、または空の文字列、または前後の空白を除くと空になる THEN その項目は既定（ブランドカラーは blue、フォントファミリーは sans）を使い、警告のログは出さない"
    violation: —（起動を続ける）
    source: FR8.2、設計の要点 5
  - id: BR1.4
    statement: 許されない値のときは既定を使い、その項目について警告のログを1回だけ出し、起動は止めない
    category: policy
    applies_to: ブランドカラー・フォントファミリーの設定
    trigger: アプリの起動
    logic: "IF 項目の値が空でなく、BR1.1・BR1.2 の許される値のどれとも一致しない（例: ブランドカラーに red） THEN その項目は既定（blue または sans）を使い、BR2.1 の形の警告のログをその項目について1件だけ出し、起動を続ける"
    violation: 起動を止めない。警告は起動時の1回だけで、要求のたびに出さない
    source: FR8.2（Given ブランドカラーに red を設定 / When 起動する / Then 起動し、blue で表示され、警告のログが出る）
  - id: BR1.5
    statement: ブランドカラーとフォントファミリーは別々に判定する
    category: constraint
    applies_to: ブランドカラー・フォントファミリーの設定
    trigger: アプリの起動
    logic: "IF 片方の項目だけが無い・許されない値である THEN その項目だけを既定にし、もう片方は設定どおりの値を使う。警告は許されない値の項目ごとに出す（2つとも許されない値なら2件）"
    violation: —
    source: FR8.2、設計の要点 5
  - id: BR1.6
    statement: 見た目の設定は起動時に1回だけ解決して保持し、要求のたびに読み直さない
    category: constraint
    applies_to: 解決した見た目の設定（ブランドカラーとフォントファミリーの名前の組）
    trigger: アプリの起動・公開の API の要求
    logic: "IF アプリが起動する THEN BR1.1〜BR1.5 で2つの値を解決して保持する。IF 公開の API が呼ばれる THEN 保持した値を返し、設定を読み直さない。設定の変更はアプリの起動し直しで当たる"
    violation: —
    source: FR8.1、設計の要点 5・7
  - id: BR1.7
    statement: 設定の値は任意の文字列として受け取り、値の判定はこの単位で行う
    category: constraint
    applies_to: ブランドカラー・フォントファミリーの設定の受け取り
    trigger: アプリの起動
    logic: "IF 設定を受け取る THEN 値の種類を受け取りの段階で限定せず（列挙の型に結び付けず）、任意の文字列として受け取ってから BR1.1〜BR1.4 で判定する。受け取りの段階で拒否して起動を止めることはしない"
    violation: 受け取りの段階で起動が止まる作りは FR8.2 に反する（起動のテストで検出する）
    source: FR8.2、設計の要点 4（前の Intent の対象DB の設定と同じ判断）
  # ---- BR2 警告のログ ----
  - id: BR2.1
    statement: 警告のログには、項目の名前・使った既定の値・許される値の一覧だけを出し、設定された値そのものは出さない
    category: policy
    applies_to: BR1.4 の警告のログ
    trigger: 許されない値を既定に置き換えたとき（起動時）
    logic: "IF 警告のログを出す THEN 水準は WARN、スタックトレースは付けず、構造化ログのキーと値で『項目の名前（例: mastersmith.appearance.brand-color）』『使った既定の値（例: blue）』『許される値の一覧』を出す。設定された値そのものは出さない"
    violation: 設定された値や例外のスタックトレースがログに出ないことをテストで確かめる
    source: FR8.2、設計の要点 6、team.md の Code Style（キーと値で渡す、4xx 相当は WARN でスタックトレースなし）
  # ---- BR3 公開の API ----
  - id: BR3.1
    statement: 見た目の設定の API は GET /api/appearance で、200 と brandColor・fontFamily の2項目を返す
    category: constraint
    applies_to: 見た目の設定の API（契約 C7）
    trigger: GET /api/appearance の要求
    logic: "IF GET /api/appearance が呼ばれる THEN 200 を返し、応答の本文は brandColor（blue・green・purple・orange のどれか）と fontFamily（sans・serif のどちらか）の2項目だけとし、どちらも常に値を持ち、常に小文字の名前で返す"
    violation: 想定内の業務の失敗は無い（エラーの code の一覧を作らない）。GET 以外のメソッドの応答は BR3.2 のとおり既存の扱い（401・405）に任せる。想定外の失敗は既存の共通のエラー応答（500）に任せる
    source: FR8.1、契約 C7、ADR-006、Q2 A
  - id: BR3.2
    statement: GET /api/appearance だけをログインなしで読めるようにし、ほかは既定の扱いのままとする
    category: authorization
    applies_to: 見た目の設定の API の公開の決まり
    trigger: /api/appearance への要求
    logic: "IF 要求が GET /api/appearance で、トークンが付いていない THEN 認証を求めずに BR3.1 の応答を返す。IF 同じ道へのほかのメソッド（POST・PUT・PATCH・DELETE など） THEN /api/ の下の既定の扱い（ログインが必要）のままとし、応答は既存の扱いに任せて U8 では新しい応答を作らない。既存の扱いは、トークンが無い・使えないなら既存の入口の処理が 401 / AUTHENTICATION_REQUIRED を返し、使えるトークン付きなら U8 が GET だけを受け付けるため共通のエラー応答（GlobalExceptionHandler）が 405 / METHOD_NOT_ALLOWED と Allow の見出しを返す。公開の決まりは差し込み口（SecurityRuleContributor）で足し、順番の値はほかの単位と重ならない値にする（重なりは既存の起動時の検査で起動が止まる）"
    violation: 未認証の GET が 401 にならないこと、ほかのメソッドが公開にならないこと（未認証の POST が 401、使えるトークン付きの POST が 405 になり、U8 の code を足していないこと）をサーバー側のテストで確かめる
    source: FR8.1（ログインの前後を問わず全画面に当てる）、ADR-006、契約の共通の決まり（認可）
  - id: BR3.3
    statement: 使えないアクセストークンを付けた要求は、既存の扱い（401）のままとし、U8 の側では変えない
    category: policy
    applies_to: トークンを付けた GET /api/appearance の要求
    trigger: GET /api/appearance に Authorization の見出しが付いた要求
    logic: "IF 使えるアクセストークンが付いている THEN BR3.1 の応答を返す。IF 期限切れ・改ざんなどの使えないトークンが付いている THEN 既存の認証の扱いのとおり 401 になることを受け入れる（U8 と auth の作りは変えない）。画面の側（U4 の ApiClient）はこの API を呼ぶときにトークンを付けない"
    violation: —（U4 の画面の側の決まりで起きないようにする）
    source: Q1 A、ADR-006
  - id: BR3.4
    statement: 応答には色とフォントの名前だけを載せ、秘密・個人に関する値を含めない
    category: constraint
    applies_to: 見た目の設定の API の応答
    trigger: GET /api/appearance の要求
    logic: "IF 応答を作る THEN BR3.1 の2項目の外の値（設定された元の文字列、ほかの設定の値、利用者の情報）を載せない"
    violation: 応答の項目が2つだけであることをテストで確かめる
    source: 契約 C7（秘密は含まない）、ADR-006
  - id: BR3.5
    statement: 見た目の設定の API の読み取りは監査ログに残さない
    category: policy
    applies_to: GET /api/appearance の要求
    trigger: GET /api/appearance の要求
    logic: "IF GET /api/appearance が呼ばれる THEN 監査の出来事を出さない（読むだけの公開の API で、監査の対象の操作ではない）"
    violation: —
    source: FR9.1（対象の一覧に無い）、設計の要点 10
  - id: BR3.6
    statement: 応答のキャッシュの扱いは既存の /api/ の決まり（no-store）のままとし、U8 では変えない
    category: constraint
    applies_to: 見た目の設定の API の応答
    trigger: GET /api/appearance の要求
    logic: "IF 応答を返す THEN 既存の共通の仕組みのとおり no-store とし、U8 で別の扱いを足さない。前に保存された値で一瞬描かれることの防ぎ方は画面の側（U4）の機能設計で扱う"
    violation: —
    source: 設計の要点 9、契約 C7 の未解決の点（U4・U8）
```

## 要約

| ID | 決まり | 分類 | 出典 |
|---|---|---|---|
| BR1.1 | ブランドカラーは blue・green・purple・orange。前後の空白を除き大文字・小文字を問わずに判定 | validation | FR8.1、FR8.2、Q2 A |
| BR1.2 | フォントファミリーは sans・serif。判定の仕方は BR1.1 と同じ | validation | FR8.1、FR8.2、Q2 A |
| BR1.3 | 無い・空のときは既定（blue・sans）を使い、警告は出さない | policy | FR8.2 |
| BR1.4 | 許されない値は既定にし、項目ごとに警告を1回だけ出し、起動は止めない | policy | FR8.2 |
| BR1.5 | 2つの項目は別々に判定する | constraint | FR8.2 |
| BR1.6 | 起動時に1回だけ解決して保持し、要求のたびに読み直さない | constraint | FR8.1 |
| BR1.7 | 設定は任意の文字列として受け取り、この単位で判定する（受け取りで起動を止めない） | constraint | FR8.2 |
| BR2.1 | 警告は WARN・スタックトレースなしで、項目の名前・既定の値・許される値だけを出す（設定された値は出さない） | policy | FR8.2 |
| BR3.1 | GET /api/appearance は 200 と brandColor・fontFamily（小文字の名前）を返す | constraint | FR8.1、契約 C7 |
| BR3.2 | GET /api/appearance だけをログインなしで公開し、ほかのメソッドは既定のまま（応答は既存の 401・405 に任せる） | authorization | FR8.1、ADR-006 |
| BR3.3 | 使えないトークンを付けた要求の 401 は受け入れ、画面の側がトークンを付けない | policy | Q1 A |
| BR3.4 | 応答は2項目だけで、秘密・個人に関する値を含めない | constraint | 契約 C7 |
| BR3.5 | 読み取りは監査ログに残さない | policy | FR9.1 |
| BR3.6 | キャッシュは既存の no-store のまま | constraint | 契約 C7 の未解決の点 |
