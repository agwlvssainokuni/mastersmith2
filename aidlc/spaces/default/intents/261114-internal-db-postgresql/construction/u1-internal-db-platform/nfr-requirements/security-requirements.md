# セキュリティの要件 — u1-internal-db-platform

上流の要件 NFR2（セキュリティ）を `NFR2.x` に分けます。この単位に認証・認可・利用者の管理の変更は無く、内部DB の接続情報と、起動時の失敗のログの扱いが対象です。

## 認証・認可・データの保護の範囲

- 認証・認可の決まり（SecurityFilterChain・権限の判定）は、この単位で変えません。内部DB の種類が変わっても、API の権限の判定は同じです。
- 内部DB の接続情報（接続先・利用者・パスワード）は、環境変数（`.env`）だけから受け取ります（`project.md` の Forbidden）。
- 保存するデータの形（パスワードのハッシュ・招待のトークンのハッシュ・リフレッシュトークンのハッシュ）は、PostgreSQL でも同じ長さ・同じ扱いのバイナリ列にします（BR4.2）。

## 要件

| ID | 要件 | 確かめ方 | 由来 |
|---|---|---|---|
| NFR2.1 | 内部DB の接続先（`MASTERSMITH_DB_URL`）・利用者・パスワードは、環境変数（`.env`）だけから受け取る。ソースコードと設定ファイルに値を書かない。見本の `.env.example` は値を空にする。 | 設定ファイルと `.env.example` に実の値が無いこと（Gitleaks の検査）。見本への追記は U4 | BR1.6、`project.md` の Forbidden |
| NFR2.2 | 起動時の失敗（空の接続先・対応しない接続先・接続できない・移行の失敗）は、起動の入口で受け、ログには `StartupFailureReport`（ENT-005）の項目（failureKind・例外のクラスの名前・SQLState・移行の版の番号・環境変数の名前）だけを ERROR の構造化ログで出して、起動を止める。 | 単体テストで、接続先・利用者名・パスワード・ホスト・ポート・例外の文を入れた例外を渡しても、出力に出ないことを確かめる（BR7.3） | BR5.1、BR5.2、BR5.5、BR5.7 |
| NFR2.3 | 起動の入口が投げ直す例外には、元の例外（cause）と、ドライバー・Flyway の例外の文を持たせない。 | 単体テストで、投げ直した例外の `getCause()` が無く、文に接続先の値が無いこと | BR5.1、BR5.2、質問 Q1: A（機能設計） |
| NFR2.4 | 接続先の値や失敗の例外を受ける部品は、`TraceAspect` の対象の層（web・service・domain・repository）の外の用途名のパッケージ（`cherry.mastersmith.internaldb.platform`）に置く。層の中に置かない。 | 境界テスト `InternalDbBoundaryArchitectureTest` と、パッケージの位置の確かめ | BR5.6、`project.md` の Code Style |
| NFR2.5 | PostgreSQL のドライバーのログは OFF のままにする（対象DB の設定と同じ）。Hibernate が SQL の失敗を自分で出すログ（`org.hibernate.orm.jdbc.error`）も OFF のままにする。 | `application.yaml` の `logging.level` に両方の設定が残ること。起動と失敗のログの実測（NFR2.6）で確かめる | BR3.5、`application.yaml` の既存の設定 |
| NFR2.6 | 届かない接続先（閉じたポート）と、パスワードが違う接続先の 2 通りで内部DB を PostgreSQL にして起動したとき、アプリのログに URL・資格情報が出ない。接続プール（Hikari）自身のログは、既定の水準では INFO の「Starting...」の 1 行だけで、ホスト・利用者名を持たない（機能設計の後に実測で確かめた）。 | コンテナを使わない結合テスト（閉じたポート）で、ログの全文に URL・資格情報が無いこと（FR3.10、FR5.5）。パスワード違いは PostgreSQL の結合テストで確かめる | FR3.10、BR5.1、BR7.3 |
| NFR2.7 | 動作中の接続の失敗（起動の後）は、今のまま（Spring と接続プールのログ。ドライバーのログは OFF のまま）にする。そのログの例外の文にホスト・ポート・利用者名が出うることは、README の既知の制約に書く。 | README の既知の制約の記述（U4）。この単位では動作中の経路を変えない | BR5.4、質問 Q1: A（機能設計）、要件 FR3.10 |
| NFR2.8 | PostgreSQL の違反の文に入る重なった値（メールアドレスなど。`Key (email)=(...)` の形）を、ログ・応答に出さない。この単位は、それを出しうるログ（Hibernate の `org.hibernate.orm.jdbc.error`・ドライバーのログ）を OFF にすることまでを持つ。違反の区分は U2、PostgreSQL での確かめは U3 が持つ。 | NFR2.5 の設定。U3 の結合テストで、違反の応答とログに値が出ないこと | 要件 NFR2、`project.md` の Code Style（排他の上限切れなどの例外） |
| NFR2.9 | PostgreSQL の結合テストが使う資格情報は、コンテナの起動のたびに作る値、またはテスト専用の値だけにし、実際の環境の資格情報を使わない。テストのメールアドレスは予約のドメインだけにする。 | テストの部品の読み取り。Gitleaks の検査 | BR6.7、`team.md` の Testing Posture |

## 脅威の考え方

- 失敗のログから接続先が漏れると、公開の記録（CI のログ・リポジトリの記録）に内部の接続先が残りうるため、起動時のログは ENT-005 の項目だけに絞る（機能設計の質問 Q1: A）。
- 動作中のログの漏れは、この Intent では今のまま残し、既知の制約として README に書く（依頼者の決定）。残る危険は、接続できなくなった後の例外の文にホスト・ポート・利用者名が出うること。パスワードは JDBC の URL に入れない前提（環境変数の別の値として渡す）のため、出ない。
- 重なった値（メールアドレス）の漏れは、ログの出力元を OFF にして止める。

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md`（NFR2、FR3.10、FR5.5）[desc]
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/functional-design/rules.md`（BR1.6・BR3.5・BR4.2・BR5.1〜BR5.7・BR6.7・BR7.3）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/functional-design/functional-spec.md`（D-1〜D-10、E11、機能設計の質問 Q1: A・Q2: A）
- `aidlc/spaces/default/memory/project.md`（Forbidden・Code Style）
- `backend/src/main/resources/application.yaml`（`logging.level` の `org.hibernate.orm.jdbc.error: OFF`）
- `aidlc/spaces/default/knowledge/aidlc-shared/checklist-auth-audit.md`（秘密情報の漏えい）

## Assumptions & Open Questions

- [assumption] パスワードは JDBC の URL に入れず、別の環境変数で渡す前提（`.env.example` の見本の形は U4 で決める）。URL にパスワードを入れた運用は、ログに URL が出ない（NFR2.2・NFR2.6）ことで守るが、README に「入れない」ことを書く（U4）。
- [assumption] Hikari のログが ERROR・WARN で接続先を出さないことは、既定のログの水準での実測であり、ログの水準を上げたときの保証ではない（NFR2.6）。水準を上げる運用は、この Intent では扱わない。
