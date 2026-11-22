# セキュリティの要件 — u2-portable-sql

上流の要件 NFR2（セキュリティ）を、この単位の `NFR2.x` に分けます。この単位が足す攻撃の面は、書き込みの手順が受ける例外と、重なった値（`Key (...)=(...)`）の漏れだけです。認証・認可・暗号化・入力の検証・API の決まりは変えません。

## 要件

| ID | 要件 | 確かめ方 | 由来 |
|---|---|---|---|
| NFR2.1 | 「あれば更新・無ければ挿入」の手順と `savePreferences` は、値（氏名・表示のタイムゾーン・利用者 ID・プレビューの本文・ハッシュ・日時）と例外の文を、ログ・監査ログ・トレースの属性・エラー応答に出さない。受けた例外は、クラスの名前だけを扱い、鍵の重なりの判定（SQLState と表の名前）以外に文を読まない。PostgreSQL の違反の文は `Key (user_id)=(...)` の形で値を含み、H2 の文も値を含みうる。 | 単体テストで、値と文を入れた例外を渡しても、ログの出力に値が出ないこと。TRACE を有効にした `XxxSecretLeakIT` の形（既存の `PreferencesSecretLeakIT` が通ること） | 要件 NFR2、`project.md` の Code Style と Corrections（例外の連なりの値） |
| NFR2.2 | 値は束縛変数だけで渡し、SQL の文字列に連結しない。表の名前・鍵の列の名前は、定数（`KeyedUpsert` の列挙）から取り、利用者の入力から取らない。 | コードの読み取りと、SpotBugs の `SQL_` 系の指摘が出ないこと（`team.md` の静的解析の基準） | 要件 NFR2、`project.md` の Forbidden（SQL に入力を組み込まない） |
| NFR2.3 | 氏名（伏せ字の型 `RedactedText` など）は、`savePreferences` の引数と戻り値で文字列にせず、JDBC の束縛の直前に取り出す。`TraceAspect` の対象の層（repository）のメソッドの引数と戻り値の形は変えない。共通の手順の部品は、`TraceAspect` の対象の層の外（`common.persistence`）に置く。 | `PreferencesSecretLeakIT`・既存の境界テスト（`UserBoundaryArchitectureTest` など）が通ること。共通の部品が層の外にあることの検査 | 要件 NFR2、`project.md` の Code Style（`TraceAspect`） |
| NFR2.4 | 重なった値の漏れの 2 つの出力元（Hibernate の `org.hibernate.orm.jdbc.error`、PostgreSQL のドライバーの `org.postgresql`）を OFF のままにする。JDBC（`Session.doWork`）の中で受ける例外は、Hibernate のログを通らないため、受けた所でも文を出力しない。 | `application.yaml` の設定が残ること（U1 の確かめ）。単体テストで、受けた例外の文がどのロガーにも出ないこと | 要件 NFR2、U1 の NFR2.8 |
| NFR2.5 | DSL の本文（YAML。信頼できない入力として検証済みのもの）の写し方・置き方は、今の検証・上限（10MB）の後に置く。書き込みの手順は本文の中身を読まず、バイト列のまま渡す。 | 既存の DSL の結合テストの全件が通ること | 要件 NFR2、`checklist-dsl.md` |

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md`（NFR2）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/functional-design/rules.md`（BR1.3・BR2.3・BR2.4・BR2.7）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/nfr-requirements/security-requirements.md`（NFR2.8）
- `aidlc/spaces/default/memory/project.md`（Forbidden・Code Style・Corrections）・`aidlc/spaces/default/knowledge/aidlc-shared/checklist-auth-audit.md`（秘密情報の漏えい）・`checklist-dsl.md`

## Assumptions & Open Questions

- [assumption] 既存の `PreferencesSecretLeakIT` が `savePreferences` の書き直し後も同じ形で通る（検索語・氏名が出ない）。コード生成で確かめる。
