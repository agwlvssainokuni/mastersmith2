# 業務の決まり: u3-postgres-behaviour-tests（機能設計。Intent 261114-internal-db-postgresql）

この単位の決まりを `BR<グループ>.<連番>` で並べます。グループは、BR1 機能ごとの代表の流れ（FR3.1）、BR2 違反の区分（FR3.2）、BR3 待ちの上限と行き詰まり（FR3.3）、BR4 失敗の後のトランザクション（FR3.4）、BR5 書き直した SQL と鍵の重なり（FR3.5）、BR6 大きなバイナリとハッシュ（FR3.6）、BR7 時刻（FR3.7）、BR8 検索（FR3.8）、BR9 テストの作りと範囲（FR5.1・FR5.4）です。源（`source`）は要件定義の FR・NFR、または質問の答え（`Q1: A`。この段の `functional-design-questions.md`）です。

次の YAML のブロックが決まりの正（source of truth）です。

```yaml
rules:
  # ---- BR1 機能ごとの代表の流れ（FR3.1） ----
  - id: BR1.1
    statement: "内部DB を使う 11 の機能ごとに、代表の API の流れを 1 本ずつ、実際の PostgreSQL で流し、状態コード・code・応答の項目が H2 のときと同じであることを確かめる。"
    category: validation
    applies_to: [ENT-001, ENT-002]
    trigger: "internalDbPostgresTest の実行"
    logic: >
      IF 内部DB が PostgreSQL のアプリを起動する
      THEN 次の 11 の機能で、成功の流れ 1 本と、その機能の代表の失敗の流れ 1 本（認可の拒否・検証の失敗・重なりのいずれか。H2 の参照のテストにあるもの）を流す:
      (1) ログインとトークンの更新（参照 LoginApiIT・TokenApiIT）、(2) パスワードの変更（MePasswordApiIT）、(3) 利用者の管理: 止める・戻す・ロックの解除（useradmin の API の結合テスト）、
      (4) 招待と登録の完了（InvitationFlowIT・RegistrationApiIT）、(5) ロールと権限の割り当て（RoleAssignmentApiIT・RoleAdminApiIT）、(6) 作業ロールの選択（WorkRoleSwitchApiIT）、
      (7) グループ（GroupAdminApiIT）、(8) メニュー（NavigationApiIT）、(9) DSL の読み込みと適用（DslAdminApiIT）、(10) 表示の設定（MePreferencesApiIT）、(11) 監査の記録（RoleAuditIT・UserAdminAuditIT など、記録の種類と件数）、ヘルスチェック（FR3.9）は U1 が持つため、この単位は数えない。
      各流れの期待（状態コード・code・項目の名前）は、参照の H2 のテストの値を ParityExpectation として写す。
    violation: "PostgreSQL のテストの失敗。H2 の値と違うときは、テストの期待を直さず、本体の差として U2 に戻す（BR9.4）"
    source: "FR3.1; FR5.1; Q1: A"

  - id: BR1.2
    statement: "流れのテストは、自分で利用者・ロール・グループを作って始め、前のテストが作った状態・主キーの具体の値・実行の順に頼らない。"
    category: constraint
    applies_to: [ENT-001, ENT-003]
    trigger: "流れのテストの実装"
    logic: >
      IF 流れのテストが前提のデータを要する
      THEN アプリの API・サービス、または標準の SQL で作る（PortableFixture）。一意の値は実行ごとの乱数でつくる。
      アプリは JVM ごとに 1 つ共有してよいが、テスト同士で同じ行を書き換えない（共有しても結果が変わらない作りにする）。
    violation: "テストの順を入れ替えると落ちる（不安定なテスト。team.md の「不安定なテスト」の決まり）"
    source: "FR3.1; team.md の Testing Posture"

  # ---- BR2 違反の区分（FR3.2） ----
  - id: BR2.1
    statement: "一意の重なり・親の無い子の追加・参照のある行の削除は、PostgreSQL のときも H2 のときと同じ応答（状態コードと code）になる。"
    category: validation
    applies_to: [ENT-002]
    trigger: "利用者・ロール・グループの作成・更新・削除の API"
    logic: >
      IF PostgreSQL で次を流す THEN H2 の既存のテストと同じ状態コードと code を返す:
      (a) メールアドレスの重なり（利用者の作成・招待）、(b) ロールの名前の重なり、(c) グループの名前の重なり、
      (d) 親の無い子の追加（存在しないロール・利用者・グループへの割り当て）、(e) 参照が残るロール・利用者・グループの削除。
      外部キーは NO ACTION（ADR-002・ADR-004）のため、SQLState 23001 の想定外の分類（FR3.2 の下調べ）は起きないことも確かめる。
    violation: "状態コード・code の食い違い、または想定外（500）の応答"
    source: "FR3.2"

  - id: BR2.2
    statement: "一意の判定の大文字と小文字の扱いを H2 と同じにする: H2 で別の値として受け付ける組は PostgreSQL でも受け付け、H2 で重なりとして拒否する組は PostgreSQL でも拒否する。"
    category: validation
    applies_to: [ENT-002]
    trigger: "大文字と小文字だけが違う値の作成"
    logic: >
      IF メールアドレス・ロールの名前・グループの名前で、大文字と小文字だけが違う組を作る
      THEN H2 のときの結果（受け付ける／拒否する）と同じ結果になる。H2 の結果は、Code Generation の最初に、使い捨てのコード（リポジトリの外）で H2 の
      実際の振る舞いを確かめて表にし、その表を期待にする（推測で書かない。project.md の Corrections）。
    violation: "H2 と結果が違う組が 1 つでもある"
    source: "FR3.2"

  # ---- BR3 待ちの上限と行き詰まり（FR3.3） ----
  - id: BR3.1
    statement: "書き込みの待ち（確定前の同じ一意の鍵への追加・更新の待ち）は、2 秒以上 3 秒未満で切れ、H2 のときと同じ応答（排他の失敗の区分）になる。"
    category: constraint
    applies_to: [ENT-004]
    trigger: "別の接続が確定前の行を持ったまま、アプリが同じ鍵へ書く"
    logic: >
      IF 保持の側の JDBC の接続が、作業ロールの保存・プレビューを置く・同じ利用者の表示の設定の保存と同じ行を確定せずに持つ
      THEN アプリの書き込みは lock_timeout（2 秒）で切れ、RoleStoreClassifier などが排他の失敗の区分（Busy など。H2 のときと同じ応答）に分ける。
      実際の待ちの時間（切れるまで）を測り、2 秒以上 3 秒未満であることを確かめる。待つ側は pg_stat_activity で待ちを観測してから測る（sleep に頼らない）。
    violation: "待ちが 3 秒以上、または切れずに続く、または区分が違う"
    source: "FR3.3; NFR4"

  - id: BR3.2
    statement: "行の排他の読み取り（FOR UPDATE のヒントつき）は、3 秒以上 4 秒未満で切れ、切れた後に接続の待ちの上限が 2 秒へ戻る。"
    category: constraint
    applies_to: [ENT-004]
    trigger: "行の排他の読み取りの待ち"
    logic: >
      IF 別の接続が行を排他で持ったまま、アプリが行の排他の読み取り（ヒントの待ちは 3 秒台）を行う
      THEN 3 秒以上 4 秒未満で切れ、排他の失敗の区分になる。IF 同じ接続（プールの同じ接続）で続けて書き込みを行う
      THEN 待ちの上限は 2 秒に戻っている（SHOW lock_timeout または実際の待ちで確かめる。ヒントの上限が接続に残らない）。
    violation: "3 秒台で切れない、または上限が残る"
    source: "FR3.3"

  - id: BR3.3
    statement: "行き詰まり（デッドロック）は検出されしだい、H2 のときと同じ区分で失敗する。"
    category: constraint
    applies_to: [ENT-004]
    trigger: "2 つのトランザクションが互いの行を待つ"
    logic: >
      IF 2 つのトランザクションが互いの行を待つ組（SQLState 40P01）を起こす
      THEN 検出された側が排他の失敗の区分で失敗し、もう一方は続けられる。上限なく待ち続けない。
      40P01 が既存の分類（RowLockFailures・各 Classifier）で排他の失敗に当たらないと分かったときは、テストを通すために期待を変えず、本体の差として U2 に戻す（BR9.4）。
    violation: "上限なく待つ、または分類が想定外"
    source: "FR3.3"

  - id: BR3.4
    statement: "ログインの状態の SKIP LOCKED の経路が、PostgreSQL で期待どおり動く。"
    category: constraint
    applies_to: [ENT-004]
    trigger: "同じメールアドレスのログインの試みが同時に来る"
    logic: >
      IF 存在しないメールアドレスの試みで、ダミーの行がほかの試みに排他を持たれている
      THEN SKIP LOCKED の問い合わせが、持たれていない行を選び（待たず）、全部が持たれているときは待つ行を 1 つ選んで上限（3 秒）で切れる。
      応答の時間の揃え方は H2 のときと同じ。
    violation: "待たずに別の行を選べない、または上限なく待つ"
    source: "FR3.3; Domain Design のレビュー（持ち越し）"

  # ---- BR4 失敗の後のトランザクション（FR3.4） ----
  - id: BR4.1
    statement: "トランザクションの中で SQL が失敗したとき、書きかけの変更を残さず、失敗の監査は別のトランザクションで記録される。"
    category: constraint
    applies_to: [ENT-001]
    trigger: "書き込みの途中で一意の違反・待ちの上限切れが起きる"
    logic: >
      IF 複数の文を持つ業務処理の途中で 2 文目が失敗する（PostgreSQL は 1 文の失敗でトランザクションを中断状態 25P02 にする）
      THEN 1 文目の変更は残らず（件数で確かめる）、失敗の監査（別のトランザクション）の行は残る。
      失敗した後に同じ接続が次の要求で使えること（中断状態を持ち越さない）も確かめる。
    violation: "書きかけが残る、監査が残らない、次の要求が 25P02 で失敗する"
    source: "FR3.4"

  # ---- BR5 書き直した SQL と鍵の重なり（FR3.5） ----
  - id: BR5.1
    statement: "書き直した SQL（savePreferences・作業ロールの保存・プレビューを置く）が、PostgreSQL でも H2 と同じ結果になる。"
    category: validation
    applies_to: [ENT-002]
    trigger: "表示の設定の保存・作業ロールの保存・プレビューを置く"
    logic: >
      IF PostgreSQL で次を流す THEN 結果は H2 のときと同じ:
      savePreferences（本人がいなければ空、change が偽ならタイムゾーンは今の値、change が真で zone が null なら NULL）、
      作業ロールの保存（行が無ければ挿入、あれば更新、同じ値の再保存）、プレビューを置く（UPDATED・INSERTED・同時の重なりの後の UPDATED_AFTER_CONFLICT）。
      文の数は H2 の確かめと同じ（savePreferences は 1 文、置く・保存は行があれば 1 文・無ければ 2 文で、接続は 1 本）。
    violation: "H2 と結果・文の数が違う"
    source: "FR3.5"

  - id: BR5.2
    statement: "鍵の重なり（isKeyConflict）の 3 つを、実際の PostgreSQL の例外の文で確かめる（U2 のレビューから持ち越し）。"
    category: validation
    applies_to: [ENT-004]
    trigger: "同じ鍵へ 2 つのトランザクションが書く"
    logic: >
      IF 次の 3 つの重なりを PostgreSQL で起こす THEN 文の照合（小文字の制約名を大文字にして照合する）が当たり、UPDATED_AFTER_CONFLICT で収束して最後に書いた側の値が残る（後勝ち）:
      (a) 作業ロールの主キー（pk_work_role_selections）、(b) プレビューの主キー（dsl_previews の主キー）、
      (c) preview_id の一意の制約（uk_dsl_previews_preview_id）は重なりと扱わず、そのまま失敗として出る（notConflictMarker）。
      3 つとも、セーブポイントまで戻した後に同じトランザクションが続けられること（25P02 にならない）を確かめる。
    violation: "(a)(b) が収束しない、(c) が重なりと誤判定される、または中断状態が残る"
    source: "FR3.5; U2 のレビュー（持ち越し）"

  - id: BR5.3
    statement: "PostgreSQL の待ちの上限（SQLState 55P03）は、JDBC の中で受けた生の例外でも排他の失敗として分類される。"
    category: validation
    applies_to: [ENT-004]
    trigger: "KeyedUpsertExecutor の中で待ちの上限が切れる"
    logic: >
      IF 保持側が行を持ったまま、アプリの upsert が lock_timeout で切れる
      THEN 55P03 が RowLockFailures に当たり、作業ロールは Busy、プレビューは排他の失敗の区分になる。外側の例外が PersistenceException に変換される形も、生の PSQLException の形も当たる。
    violation: "想定外（500）に分類される"
    source: "FR3.3; FR3.5"

  # ---- BR6 大きなバイナリとハッシュ（FR3.6） ----
  - id: BR6.1
    statement: "DSL の本文は上限（10MB）ちょうどまで、PostgreSQL に保存し、1 バイトも変わらずに読み出せる。"
    category: validation
    applies_to: [ENT-002]
    trigger: "10MB ちょうどの DSL の本文を保存して読む"
    logic: >
      IF 10,485,760 バイトちょうどの本文（全バイト値 0x00〜0xFF を含む）を保存して読み出す
      THEN 長さと SHA-256 が一致する。上限 +1 バイトは今までどおり検証で拒否される。起動時の ddl-auto: validate が通っていること（列の型と写し方の組）は、U1 の起動のテストが確かめ、U3 は往復で確かめる。
    violation: "長さまたは SHA-256 の不一致"
    source: "FR3.6; U2 のレビュー（持ち越し）"

  - id: BR6.2
    statement: "トークンのハッシュ（32 バイト）は、1 バイトも変わらずに照らせる。"
    category: validation
    applies_to: [ENT-002]
    trigger: "リフレッシュトークン・招待のトークンのハッシュの保存と照合"
    logic: >
      IF 全バイト値を含む 32 バイトのハッシュを保存して読み出し、同じ値で照合する
      THEN 完全に一致する（先頭・末尾の 0x00 や 0xFF が落ちない）。リフレッシュと招待の両方の流れで確かめる。
    violation: "照合が一致しない"
    source: "FR3.6"

  # ---- BR7 時刻（FR3.7） ----
  - id: BR7.1
    statement: "時刻の列は、書いた時点と読み出した時点が同じになり、JVM とデータベースの時差の設定に左右されない。"
    category: validation
    applies_to: [ENT-002]
    trigger: "時刻を持つ行の保存と読み出し"
    logic: >
      IF JVM の user.timezone と PostgreSQL のセッションの TimeZone を、それぞれ UTC と UTC 以外（例: Asia/Tokyo と America/New_York）にした組で保存して読む
      THEN 時点（Instant）が同じ。夏時間の境の時刻も同じ時点に戻る。
    violation: "往復で時点がずれる"
    source: "FR3.7"

  - id: BR7.2
    statement: "有効期限・ロックの解除など時刻で決まる判定は、H2 のときと同じ結果になる。"
    category: validation
    applies_to: [ENT-002]
    trigger: "有効期限・ロックの解除の判定"
    logic: >
      IF 注入した時計（MutableClock）を、期限の直前・ちょうど・直後に進める
      THEN 招待・リフレッシュトークンの有効期限とログインのロックの解除は、H2 の参照のテストと同じ結果になる（実時間の sleep に頼らない）。
    violation: "境の判定が H2 と違う"
    source: "FR3.7"

  # ---- BR8 検索（FR3.8） ----
  - id: BR8.1
    statement: "利用者の検索は、PostgreSQL でも H2 と同じ利用者を返し、検索の値の %・_・\\ は一致の条件として働かない。"
    category: validation
    applies_to: [ENT-002]
    trigger: "利用者の検索の API"
    logic: >
      IF 見本の利用者（日本語・全角半角・カナ・記号・長い入力を含む）に対し、%・_・\\・大文字と小文字違い・連続空白などの検索を流す
      THEN 返る利用者の集合は、U2 の捨ての試しで H2 と一致した値（固定の期待）と同じ。Unicode の端の 3 つ（İ・語末シグマ・ǅ）は、既知の差として U4 の文書に書く（U2 の Q3: A）ため、確かめの対象に入れず、差として固定もしない。
    violation: "集合が H2 と違う、または % などが一致の条件として働く"
    source: "FR3.8"

  # ---- BR9 テストの作りと範囲（FR5.1・FR5.4） ----
  - id: BR9.1
    statement: "PostgreSQL のテストは @InternalDbPostgresIT のクラスとして作り、普段の verify・verifyQuick・CI では流さない。"
    category: policy
    applies_to: [ENT-001]
    trigger: "テストの実装"
    logic: >
      IF PostgreSQL のテストのクラスを作る THEN @InternalDbPostgresIT を付け、クラス名は *IT、置き場は <機能>/ の下の postgres 用のパッケージ（U1 の internaldb/postgres と同じ考え方）にする。
      JaCoCo の記録は取らない（U1 の internalDbPostgresTest の設定のまま）。
    violation: "普段の integrationTest に混ざる"
    source: "FR5.1; FR5.2; NFR6"

  - id: BR9.2
    statement: "PostgreSQL のテストとその部品は、H2 の構文・H2 の部品に頼らない。"
    category: constraint
    applies_to: [ENT-003]
    trigger: "テストの部品の実装"
    logic: >
      IF PostgreSQL のテストの部品を作る THEN org.h2・H2 向けの既存の部品（H2SessionWaits・H2BlockedSessions・RoleFixtures・UserAdminFixtures・BusinessDataWriteTables など）に依存せず、
      INFORMATION_SCHEMA.SESSIONS・MERGE ... KEY・DATEADD・Java のトリガーを使わない。待ち合わせは pg_stat_activity / pg_locks で行う。
      この決まりは、ArchUnit の構造のテスト（PostgreSQL のテストのクラスが org.h2 と H2 向けの部品に依存しない）で守る。既存の境界テストは緩めない。
    violation: "構造のテストの失敗"
    source: "FR5.4; 要件 7 章（K-151）"

  - id: BR9.3
    statement: "H2 の既存の結合テストとテストの部品は 1 行も変えない（件数を減らさない）。"
    category: constraint
    applies_to: [ENT-002]
    trigger: "U3 の実装"
    logic: >
      IF U3 のコミットを積む THEN 既存の H2 向けのテストと部品のファイルに差分が無い（git の差分で確かめる）。
      PostgreSQL 用の部品は新しいファイルとして足す。H2 と PostgreSQL の両方が使う既存の部品（HttpTestClient・JsonLogRecords・MutableClock・TestDatabase.randomSecret など）は、読むだけで変えない。
    violation: "H2 の既存のテストの変更"
    source: "FR5.4; Q1: A"

  - id: BR9.4
    statement: "本体のソース（src/main）は変えない。PostgreSQL のテストで本体の差が見つかったときは、U2 の小さな直しとして戻して行い、U3 は待つ。"
    category: policy
    applies_to: [ENT-002]
    trigger: "PostgreSQL のテストが H2 の期待と違う結果を出した"
    logic: >
      IF PostgreSQL のテストが落ち、原因が本体にある THEN テストの期待を変えて通さず、差を記録して依頼者に諮り、U2 に戻す。
      IF 原因がテストの作り（待ち合わせ・部品）にある THEN U3 の中で直す。
    violation: "期待を弱めて通す"
    source: "Units Generation の U3 の境界; Q1: A"

  - id: BR9.5
    statement: "テストの出力・ログ・例外の文に、接続先・利用者・パスワード・行の値（メールアドレス・トークン・ハッシュ）を出さない。"
    category: constraint
    applies_to: [ENT-001, ENT-003]
    trigger: "テストの失敗の出力"
    logic: >
      IF テストの失敗の文を作る THEN 件数・状態コード・code・クラス名だけを出す。秘密の役の値は TestDatabase.randomSecret() の乱数で作り、ソースに本物らしい値を書かない。
      テストのメールアドレスは予約のドメインだけにする。
    violation: "出力に値が出る"
    source: "project.md の Forbidden; team.md の Testing Posture"

  - id: BR9.6
    statement: "明示して流したときの時間を測って記録する。"
    category: policy
    applies_to: [ENT-001]
    trigger: "Build and Test"
    logic: >
      IF internalDbPostgresTest を流す THEN 全体の時間を記録する。待ちの確かめ（BR3.1〜BR3.4・BR5.2）は 1 件ごとの時間も標準出力に 1 行出す。普段の検査の時間（NFR5）には含めない。
    violation: "時間が記録されない"
    source: "Units Generation の U3 の実装の注意"
```

## 決まりの要約

| グループ | 決まり | 要件 |
|---|---|---|
| BR1 | 11 の機能の代表の流れ（成功 1・失敗 1）を、H2 と同じ値で確かめる。前の状態に頼らない | FR3.1 |
| BR2 | 一意・親の無い子・参照のある行の削除の応答が同じ。大文字と小文字の扱いは H2 の実測の表と同じ | FR3.2 |
| BR3 | 書き込みの待ち 2〜3 秒、行の排他 3〜4 秒と上限の戻り、行き詰まり、SKIP LOCKED | FR3.3 |
| BR4 | 失敗の後に書きかけが残らず、監査は別のトランザクション、次の要求が使える | FR3.4 |
| BR5 | 書き直した SQL の結果と文の数、3 つの鍵の重なり、55P03 の分類 | FR3.5 |
| BR6 | 10MB ちょうどの往復と 32 バイトのハッシュ | FR3.6 |
| BR7 | 時刻の往復と時刻で決まる判定 | FR3.7 |
| BR8 | 検索の結果と `%`・`_`・`\` | FR3.8 |
| BR9 | タグ・H2 に頼らない・H2 を変えない・本体を変えない・値を出さない・時間の記録 | FR5.1・FR5.4 |
