# セキュリティの設計 — u2-portable-sql

書き込みの手順が受ける例外と、値の漏れを止める設計です。認証・認可・暗号化・入力の検証・API の決まりは、この単位で変えません。

## 守りの層（defense in depth）

| 層 | 守り | 対応する要件 |
|---|---|---|
| 値の渡し方 | 値は `PreparedStatement` の束縛だけで渡し、SQL の文字列に連結しない。表の名前・鍵の列の名前は `KeyedUpsert` の列挙の定数から取り、利用者の入力から取らない | NFR2.2 |
| 例外の扱い | 手順は、鍵の重なりの判定のために `SQLException` の文を読むが、出力・ログ・例外の文には出さない。判定の後は SQLState とクラス名だけを扱う。重なりでない失敗は、そのまま外へ投げる（今の経路の例外の変換とログの出し方に任せる） | NFR2.1・NFR2.6 |
| 置き場 | 手順の部品は `common.persistence`（`TraceAspect` の対象の層の外）に置く。`savePreferences` の実装（`UserRepositoryImpl`）は、`user.repository`（対象の層）に置くため、氏名を伏せる型のまま引数に取り、束縛の直前に取り出す | NFR2.3 |
| ログの出力元 | Hibernate の `org.hibernate.orm.jdbc.error`・PostgreSQL のドライバーの `org.postgresql` を OFF のままにする（`application.yaml` は変えない）。JDBC の中で受ける例外は Hibernate のログを通らないため、手順自身も出力しない | NFR2.4 |
| 本文 | DSL の本文は、今の検証・上限（10MB）の後の値として、バイト列のまま渡す。手順は中身を読まない | NFR2.5 |

## 設計の決定

### 重なりの判定で例外の文を読む範囲（NFR2.1）

- 読むのは、文を大文字にして表の名前（`WORK_ROLE_SELECTIONS`・`DSL_PREVIEWS`）を含むかの判定 1 か所だけ。含むかの真偽だけを使い、文そのものは保持・出力しない。判定の部品は純粋な関数にして、値（`Key (user_id)=(...)`・本文の断片・メールアドレス）を入れた例外を渡しても、返すのが真偽だけで出力に出ないことを単体テストで確かめる。
- 想定外の失敗として投げ直す例外（重なりの後の更新が 0 行）は、文に値を入れず、表の名前だけ持つ固定の説明にする。

### `UserRepositoryImpl` の氏名の扱い（NFR2.3）

- `savePreferences` の引数の `Preferences`（氏名を伏せる型を含む）は、今と同じ型のまま受ける。`TraceAspect` が引数を文字列にするため、メソッドの入口では伏せ字のまま。JDBC の束縛の直前に、`displayName()` などを取り出す（今の SpEL の取り出しと同じ）。取り出した値を変数・ログ・例外に残さない。
- `PreferencesSecretLeakIT`（TRACE を有効にして保存しても氏名・タイムゾーンがログに出ない）が、書き直した後も通ることを確かめる。

## 脅威と守りの対応

| 脅威 | 守り |
|---|---|
| 重なった値（`Key (user_id)=(...)`）が、失敗のログに残る | 手順が文を出さない・Hibernate・ドライバーの出力元を OFF のまま |
| 氏名・タイムゾーンが TRACE のログに残る | 伏せ字の型のまま引数に取り、束縛の直前に取り出す |
| 利用者の入力が SQL に混ざる | 値は束縛だけ。表・列の名前は定数 |
| DSL の本文を読んで別の処理が走る | 手順は本文を読まない |

## 採らなかった形

| 形 | 理由 |
|---|---|
| 例外の文を伏せ字にして出す | 伏せ字の規則の抜けが漏れになる。出さない方が確実 |
| 重なりを例外の型だけで判定する | Hibernate の変換を通らない JDBC の中では型が付かない。SQLState と表の名前で判定する |

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/nfr-requirements/security-requirements.md`（NFR2.1〜NFR2.5）・`observability-requirements.md`（NFR2.6）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/functional-design/rules.md`（BR1.3・BR2.3・BR2.4・BR2.7）
- `aidlc/spaces/default/memory/project.md`（Forbidden・Code Style）・`aidlc/spaces/default/knowledge/aidlc-shared/checklist-auth-audit.md`（秘密情報の漏えい）・`checklist-dsl.md`

## Assumptions & Open Questions

None.
