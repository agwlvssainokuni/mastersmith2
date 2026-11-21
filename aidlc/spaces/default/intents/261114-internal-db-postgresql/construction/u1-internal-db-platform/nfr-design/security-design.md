# セキュリティの設計 — u1-internal-db-platform

内部DB の接続情報と、起動の失敗のログの漏れを止める設計です。認証・認可・暗号化・入力の検証・CSRF・セキュリティヘッダーは、この単位で変えません（API の決まりは今のまま）。手元の PostgreSQL への接続は暗号化（TLS）を求めません（要件 5 章。必要なときは接続先の URL のオプションで指定する）。

## 守りの層（defense in depth）

| 層 | 守り | 対応する要件 |
|---|---|---|
| 入口 | 接続先・利用者・パスワードは環境変数（`.env`）だけから受け取る。ソース・設定ファイルに値を書かない。見本の `.env.example` は値を空にする（U4） | NFR2.1 |
| 失敗の写し方 | 失敗は `StartupFailureReport`（ENT-005）に写し、許す項目だけをログに出す。例外の文は読まない（版の番号の数字を取る所だけ） | NFR2.2 |
| 例外の形 | 投げ直す例外は `InternalDbStartupException`。文は failureKind と固定の説明、cause は付けない | NFR2.3 |
| 置き場 | 接続先を扱う部品は `cherry.mastersmith.internaldb.platform`（`TraceAspect` の対象の層の外）に置く | NFR2.4 |
| ログの出力元 | PostgreSQL のドライバーのログと Hibernate の SQL の失敗のログを OFF のままにする | NFR2.5・NFR2.8 |
| 実測の網 | 起動の失敗のテストで、全ロガーの出力に URL・資格情報が無いことを確かめる | NFR2.6 |
| テスト | テストの資格情報はコンテナの起動ごとの値かテスト専用の値だけ | NFR2.9 |

## 設計の決定

### 失敗のログの形（NFR2.2）

- ERROR 1 件、SLF4J のキー・値 API。メッセージは固定の英語の短文、項目は `failureKind`・`exceptionTypes`（クラス名の並び。連なりの深さの上限 10）・`sqlState`・`migrationVersion`・`settingName`（環境変数の名前だけ）・`supportedKinds`。
- failureKind の決め方: 空の接続先は `EMPTY_URL`、対応しない種類は `UNSUPPORTED_KIND`、連なりの SQLException の SQLState が先頭 08・28・3D なら `CONNECTION_FAILED`、Flyway の移行の失敗は `MIGRATION_FAILED`、検証の失敗は `MIGRATION_VALIDATION_FAILED`、判定できないときは `UNKNOWN`。
- 版の番号は、移行の失敗の例外の文の中の移行のファイル名（`V<番号>__…sql`）から数字だけを取り出す。取り出した数字以外は出力に入れない（BR5.2・BR5.3）。
- 単体テストで、接続先・利用者名・パスワード・ホスト・ポートを文に入れた例外を渡しても、出力にそれらが現れないことを確かめる。

### Hikari 自身のログ（NFR2.6）

- 機能設計の後に、アプリを起動して実測した（閉じたポートの接続先と、パスワード違いの 2 通り。Hikari のログは INFO の「Starting...」の 1 行だけで、ERROR・WARN は無く、ホスト・ポート・利用者名は Spring の例外の文にだけ出た。これは 2026-11-20 の実測で、条件は Spring Boot 4.1.2・既定のログの水準）。
- 設計では Hikari のログを絞る設定を足さない。足さない理由: 実測で出ない。絞る設定は動作中の枯渇の警告まで隠しかねない（NFR2.7 の動作中の経路を変えない決定と合わない）。
- 代わりに、ログの水準を上げたり Hikari の版が上がったりして出方が変わったときに気づけるよう、起動の失敗のテスト（コンテナ無し・閉じたポート）が、アプリの全ロガーの出力に URL・資格情報が含まれないことを確かめる（出た時点でテストが落ちる）。落ちたときの扱い（ログを絞るか、起動の入口でプールを先に借りて包むか）は、そのときに依頼者に諮る。
- 水準を上げる運用は、この Intent の範囲の外（NFR2.6 の前提）。

### 動作中の失敗（NFR2.7）

- 起動の後の接続の失敗は今のまま。例外の文にホスト・ポート・利用者名が出うることを README の既知の制約に書く（U4）。パスワードは JDBC の URL に入れず、別の環境変数で渡す前提のため、パスワードは出ない（README に「URL に入れない」ことも書く。U4）。

### 重なった値の漏れ（NFR2.8）

- PostgreSQL の違反の文は `Key (email)=(...)` の形で値を含む。出しうる 2 つのログの出力元（Hibernate の `org.hibernate.orm.jdbc.error`・PostgreSQL のドライバー `org.postgresql`）を OFF のままにする。`application.yaml` の設定が残ることを、設定の確かめのテストか読み取りで確かめる。違反の区分は U2、PostgreSQL での確かめは U3。

### TRACE のログ（NFR4.12 に対応）

- `TraceAspect` は層の引数と戻り値を文字列で出す。接続先を扱う部品は層の外のため対象にならない。TRACE を有効にして起動し、出力に接続先が無いことを確かめる。

## 脅威と守りの対応

| 脅威 | 守り |
|---|---|
| 失敗のログから内部の接続先が公開の記録（CI のログなど）に残る | ENT-005 だけを出す・cause を付けない・実測の網のテスト |
| 重なった値（メールアドレス）がログに残る | 出力元を OFF にする |
| 資格情報がソースに入る | 環境変数だけ・Gitleaks の検査 |
| テストの資格情報が実際の環境のものになる | コンテナの起動ごとの値・テスト専用の値 |

## 採らなかった形

| 形 | 理由 |
|---|---|
| 例外の連なりをそのまま ERROR に出す | ホスト・ポート・利用者名が出る（Forbidden） |
| 例外の文を伏せ字にして出す | 伏せ字の規則の抜けが漏れになる。項目を許すものだけに絞る方が確実 |
| Hikari のロガーを OFF にする | 動作中の警告まで隠れる。実測で出ない |

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/nfr-requirements/security-requirements.md`（NFR2.1〜NFR2.9）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/functional-design/entities.md`（ENT-005）・`rules.md`（BR5.1〜BR5.7）
- `aidlc/spaces/default/memory/project.md`（Forbidden・Code Style）
- `backend/src/main/resources/application.yaml`（`logging.level`）
- `aidlc/spaces/default/knowledge/aidlc-shared/checklist-auth-audit.md`（秘密情報の漏えい）

## Assumptions & Open Questions

- [assumption] Hikari のログの実測は、既定の水準での事実であり、水準を上げたときの保証ではない。実測の記録（run のログ）は、リポジトリの外の捨ての試しの出力だったため、成果物には要約だけを書いた。コード生成で、同じ確かめを起動の失敗のテストとして残す（記録の置き場）。
