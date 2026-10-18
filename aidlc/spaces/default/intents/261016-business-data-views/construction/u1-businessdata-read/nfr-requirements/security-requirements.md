# セキュリティの要件 — U1 u1-businessdata-read

## 出典

- 要件 `aidlc/spaces/default/intents/261016-business-data-views/inception/requirements-analysis/requirements.md`（FR2・FR13、NFR4.1〜NFR4.4・NFR7）
- ストーリー `inception/user-stories/stories.md`（AC3.3.4・AC3.6.1〜AC3.6.9）
- この単位の機能設計 `construction/u1-businessdata-read/functional-design/`（`rules.md` の BR1・BR3・BR8.2・BR10・BR11、`functional-spec.md` 1節・2節・8節）
- 契約 `inception/contract-design/contract-summary.md`（C2・C6・C7）
- この段の答え `construction/u1-businessdata-read/nfr-requirements/nfr-requirements-questions.md`（Q3: A・Q5: A、まとめの確認 Looks correct）
- `aidlc/spaces/default/memory/team.md`（Testing Posture の業務データの必須のテスト、Code Style の SQL の組み立て・時間の上限・`toString` の伏せ字）と `project.md`（Forbidden・Mandated）
- コード: `backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDbProperties.java`（今の時間の上限 生成 20 秒・照合 5 秒・接続 3 秒）、`TargetDataSourceConfig.java`（通信の待ち = 最も長い上限 + 5 秒）、`backend/src/main/resources/application.yaml`（共通の本文の上限 `mastersmith.web.max-request-body-size` 既定 1MB）、`backend/src/main/java/cherry/mastersmith/role/web/RoleWebConfig.java`（道ごとの本文の上限 `RequestBodyLimitRoute` の形）

## ID の振り方

上流の枝番と同じ番号は同じ意味でだけ使う。上流に枝番の無い NFR1・NFR2・NFR3・NFR7・NFR8 は、この単位で .1 から振る。NFR4 は上流の 4.1〜4.4 をそのまま使い、足す要件は 4.5 から振る。4.7 は U2 に空けておく（「U2・U3 とそろえる点」）。

| 上流の ID | この単位での扱い | この単位の ID | 置いた文書 |
|---|---|---|---|
| NFR1 応答時間 | 当たる | NFR1.1〜NFR1.3 | performance |
| NFR2 規模 | 当たる | NFR2.1〜NFR2.5 | scalability |
| NFR3 対象DB | 当たる | NFR3.1・NFR3.3 / NFR3.2 | reliability / performance |
| NFR4.1 SQL の組み立て | 当たる | NFR4.1 | security |
| NFR4.2 行の値の漏えい | 当たる | NFR4.2 | security |
| NFR4.3 書き込みの接続の分離 | 部分（読み取りの側） | NFR4.3 | security |
| NFR4.4 時間の上限 | 当たる | NFR4.4 / NFR4.8 | security / reliability |
| （足す） | 要求の大きさと値の長さ | NFR4.5 | security |
| （足す） | 認可と API の分類 | NFR4.6 | security |
| （U2 に空ける） | pattern の時間の上限 | NFR4.7 | U2 |
| （足す） | 失敗の返し方と例外の通り道（R-07） | NFR4.8 | reliability |
| NFR5 アクセシビリティ | 当たらない | — | — |
| NFR6 言語 | 当たらない | — | — |
| NFR7 テスト | 当たる | NFR7.1・NFR7.2 / NFR7.3 | security / performance |
| NFR8 運用 | 当たる | NFR8.1〜NFR8.3 | observability |

- NFR5 はこの単位に画面が無いため当たらない（U3・U4 の受け持ち）。
- NFR6 は、表示名を利用者の言語で返すことを機能設計の BR で済ませており、誤りは `code` で返して文は画面（U3）が出すため、この単位に NFR の目標が無い。

## 要件

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR4.1 | 業務データの SQL に利用者の入力を文字列のまま組み込まない。識別子（スキーマ・テーブル・カラム・並べ替えの列）は、権限の写しの DSL の種類ごとの名前の一覧と照らしてから既存の `SqlIdentifiers.quoteKnown` で引用し、値は型ごとのセッターでバインドする（BR3・BR7.8）。SQL を組み立てて実行する部品は1つ（方言は種類ごと）に閉じ、`BusinessDataBoundaryArchitectureTest` で `java.sql.Connection` の `prepareStatement`・`createStatement` を呼んでよい場所をその部品だけにする。SpotBugs の `SQL_` の除外は、部品のクラスの名前・メソッドの名前・パターン名の3つで絞り、理由と根拠を書く | ArchUnit の境界テスト、SpotBugs の関門（`SQL_` は priority によらず止める）、`'`・`"`・`;`・`--`・バッククォート・改行を含む識別子・検索の値・並べ替えの結合テスト（3種類の DB） | Code Generation（B2）。除外の追加はコード生成の計画で依頼者の承認を得る |
| NFR4.2 | 行の値（主キーの値を含む）・検索の値・対象DB の接続情報を、アプリのログ・トレースの属性・誤りの応答に含めない。JDBC の例外のメッセージは応答にもログにも出さず、ログには SQLState・業者のエラーの番号・例外のクラスの名前だけを出す。行の値を持つ型は `toString` を上書きして値を伏せ、行を `Map` などの汎用の型のまま層をまたいで渡さない。道は固定し、値は本文で運ぶ（BR10.4） | TRACE を有効にした `BusinessDataSecretLeakIT`（一覧・詳細・誤りの応答）。外部へ送る設定を有効にした結合テストで、トレースの属性に値が無いこと。400・404 の応答に入力の値と主キーの値が無いこと（AC3.6.8・AC3.6.9） | Code Generation（B2） |
| NFR4.3 | 読み取りの API は、既定の候補にしない今の読み取りの `targetDataSource`（読み取りだけのアカウント）を、`targetdb.service` の貸す形の口からだけ使う。新しい接続の Bean を作らない。書き込みの接続と資格情報の分離は U2 が受け持つ | 読み取りの口だけを使うことを境界テストで固定する | Code Generation（B2） |
| NFR4.4 | 対象DB への問い合わせに、目的ごとの時間の上限を付ける（Q3: A）。`ReadPurpose` に一覧と詳細の目的を足し、`TargetDbProperties.QueryTimeout` に値を足す。<br>・一覧: 問い合わせ1回ごとに 5 秒（件数とページのそれぞれ。1要求の最悪 10 秒）<br>・詳細: 5 秒<br>今の値（生成 20 秒・照合 5 秒）と同じく秒の単位で当て、1 秒以上とする。通信の待ち（最も長い上限 + 5 秒 = 25 秒）は変わらない | 時間の上限の値の設定の結合テスト。時間切れの振る舞いは NFR4.8 | Code Generation（B2） |
| NFR4.5 | 読み取りの API 3本に、専用の本文の上限 64 KB を置き、超えたら 413 `PAYLOAD_TOO_LARGE` にする（既存の `RequestBodyLimitRoute` の形。認証と認可の後に確かめる）。検索の値と主キーの値は1つ 1,000 字（コードポイント）までとし、超えたら 400 `VALIDATION_FAILED`（理由は既存の `INVALID_VALUE`、`definitionChanged` つき）にする（Q5: A）。理由の値は増やさない | 64 KB ちょうどは受け付け、超えると 413 の境界のテスト。1,000 字ちょうどは受け付け、1,001 字は 400 の境界のテスト。どちらも応答に値が入らないこと | Code Generation（B2） |
| NFR4.6 | 3本の API は「ログインだけ」（`@ApiAccess(AUTHENTICATED)`）に分類し、テーブルの権限は業務処理の層で要求ごとに判定する。画面の出し分けを判定の代わりにしない | API の分類の網羅のテストに3本を載せる。NFR7.1 の認可の表のテスト | Code Generation（B2） |
| NFR7.1 | team.md の「業務データの一覧・詳細」の必須のテストのうち、読み取りの分を書く。<br>・権限ごとの操作の可否（401・403・200 をパラメーターを使う表で。403 の利用者は要る権限だけを欠く人。作業ロールを切り替えた直後・割り当てを変えた直後の次の要求）<br>・主キーとビュー（詳細の 409。複合の主キー）<br>・カラムの出し分け（NONE は応答に含めない。`list.visible`・`detail.visible` の扱い）<br>・IDOR と差し替え（DSL に無い名前・権限の無いテーブル・無い行）<br>・403 と 404 の使い分け（2節の判定の順のとおり）<br>・SQL の組み立て・部分一致のエスケープ・3種類の DB の境界の値・行の数の上限と時間切れ・道に乗る値（本文で運ぶ形で、`/`・`%`・`?`・`#`・空白・引用符を含む主キー）・接続の数・漏えい | `XxxTest`（単体）と `XxxIT`（結合。3種類の DB は Testcontainers）。どれも `./gradlew verify` の中で流す | Code Generation（B2） |
| NFR7.2 | 許す操作の集合を求める関数と、識別子の照合の部品に、性質ベースのテスト（jqwik）を当てる。性質は「権限を足しても許す操作は減らない」「主キーの無いテーブルに行を1件に決める操作が出ない」「DSL に無い名前は拒否される」「引用符・`;`・改行を含む任意の文字列でも SQL の構文として解釈されない」など。失敗したときの乱数の種を記録して再現できるようにする | jqwik のテスト（`verify` の中） | Code Generation（B2） |

## 上流との差

- 承認の場の決定（`construction/functional-design/memory.md` の Open questions）: `nullable` は U2 が足す検証の材料とし、`formPart` の値域から `lookup` を外し、U3 は `nullable`・`required` が欠けたら形の崩れとする。U1（B2）は `formPart`・`nullable` を返さないため、この単位のセキュリティの要件に差は無い。定義の API の項目を足すのは U2（B4）である（`functional-spec.md` の X10）。
- R-07 は `reliability-requirements.md` の NFR4.8、R-08 は同じ文書の NFR3.3 で受ける。
- NFR4.5 の値の長さの誤りは、機能設計の理由の値（`NOT_ALLOWED`・`INVALID_VALUE`・`RANGE_REVERSED`・`NOT_A_CHOICE`・`KEY_MISMATCH`）のうち `INVALID_VALUE` を使い、新しい値を足さない。`rules.md` の BR1.6 には長さの上限の行が無いため、差としてここに書く。

## 後の段に回すこと

- **NFR 設計**: 読み取りの口と `TargetReadSession` の置き場（R-07。BR8.5・BR11.2 に足す1行）。本文の上限の道ごとの決まりの置き場（`businessdata` の Web の設定）。
- **コード生成の計画（B2）**: `RoleBoundaryArchitectureTest` の変更と `PermissionSnapshot` の口、SpotBugs の除外の絞り込み（どちらも依頼者の承認）。`ReadPurpose`・`QueryTimeout` に手を入れることに伴う既存のテストの洗い出し。
- **Performance Validation・Observability Setup**: この文書の要件には持ち主の段が無い（どちらも B2 のテストで確かめる）。

## U2・U3 とそろえる点

- U2 の書き込みの目的の時間の上限も秒の単位で置き、通信の待ちの計算（最も長い上限 + 5 秒）に入れて 25 秒を超えないかを U2 で確かめる。
- U2 の書き込みの本文の上限（U2 の BR13.5）は、100 カラム × 長い文字で決め、U1 の 64 KB とは別の道の決まりにする。値の長さの 1,000 字は、検索の値と主キーの値だけに当てる（書き込みの値は DSL の検証と DB の型に従う）。
- 枝番: U1 は 4.5・4.6・4.8、U2 は 4.7（pattern の時間の上限）と 4.9〜4.19、U3 は 4.20 以降。上流の 4.1〜4.4 は3単位とも同じ意味でだけ使う。
- 漏えいの確かめは `*SecretLeakIT` の形を3単位でそろえる（U2 は書き込みと監査、U3 は画面の側）。
