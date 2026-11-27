# セキュリティの要件 — u3-postgres-behaviour-tests

上流の要件 NFR2（セキュリティ）を、この単位の `NFR2.x` に分けます。この単位は、テストの出力とテストが使う資格情報の扱いだけが攻撃の面です。認証・認可・暗号化・入力の検証・API の決まりは変えません。

## 要件

| ID | 要件 | 確かめ方 | 由来 |
|---|---|---|---|
| NFR2.1 | テストの出力・ログ・例外の文に、内部DB の接続先・利用者・パスワード・資格情報を含む JDBC の URL を出さない。接続の引数は起動の引数にだけ持つ。 | PostgreSQL のテストの標準出力の取り込み（`OutputCaptureExtension`）で、URL・利用者・パスワードの文字列が無いこと（U1 の `InternalDbSecretLeakIT` と同じ形を、U3 の共有のアプリの起動に 1 件足す） | 要件 NFR2、`project.md` の Forbidden |
| NFR2.2 | テストの失敗の文・出力に、行の値（メールアドレス・トークン・ハッシュ・DSL の本文・氏名）を出さない。件数・状態コード・`code`・クラス名・バイト数だけを出す。PostgreSQL の違反の文（`Key (email)=(...)`）をテストの出力に出さない。 | 重なり・待ちの上限切れを起こすテスト（BR2・BR3・BR5）の後で、アプリのログとテストの出力に、作った値が出ないこと | 要件 NFR2、`project.md` の Code Style |
| NFR2.3 | テストが使う資格情報（PostgreSQL の利用者・パスワード・テストの利用者のパスワード）は、実行ごとの乱数で作り、ソースに本物らしい値を書かない。`TestDatabase.randomSecret()` を使う。テストのメールアドレスは予約のドメイン（`example.com` など）だけにする。 | Gitleaks が新しい指摘を出さないこと（`.gitleaks.toml` の除外を足さない）。コードの読み取り | `team.md` の Testing Posture、`project.md` の Forbidden |
| NFR2.4 | テストが起こす待ち・行き詰まり・重なりは、テスト用の専用データベース（`msidb_<乱数>`）の中だけで起こし、コンテナの管理者の資格情報で他のデータベースを触らない。終わったら `DROP DATABASE` で消す。 | `InternalDbPostgresDatabase` の `create` / `close` の対で使うこと。結合テストの後にデータベースが残らないこと | U1 の BR6.6 |
| NFR2.5 | 10MB の DSL の本文・全バイト値のハッシュは、テストの中でだけ作り、出力・ログ・失敗の文に出さない。DSL は信頼できない入力として、既存の検証・上限（10MB）の後に置く既存の API で保存する。 | BR6.1・BR6.2 のテストの失敗の文に本文・ハッシュの値が入らないこと | `project.md` の Mandated（DSL の上限）、`checklist-dsl.md` |

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md`（NFR2）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/functional-design/rules.md`（BR9.5・BR6）
- `aidlc/spaces/default/memory/project.md`（Forbidden・Code Style）・`aidlc/spaces/default/knowledge/aidlc-shared/checklist-auth-audit.md`・`checklist-dsl.md`

## Assumptions & Open Questions

- [assumption] U1 の `InternalDbSecretLeakIT`（H2 の経路）と同じ検査の形を、PostgreSQL の経路の共有のアプリで 1 件足せる。コード生成で確かめる。
