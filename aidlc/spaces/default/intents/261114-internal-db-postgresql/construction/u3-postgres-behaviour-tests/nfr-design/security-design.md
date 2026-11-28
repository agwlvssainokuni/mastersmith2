# セキュリティの設計 — u3-postgres-behaviour-tests

NFR2.1〜NFR2.6・NFR3.1・NFR8.1 を設計に落とします。

## 設計の決定

| ID | 決定 | 満たす NFR | 理由 |
|---|---|---|---|
| DD-12 | 接続先・利用者・パスワードは、アプリの起動の引数（`InternalDbPostgresDatabase.applicationArgs()`）と、保持側の接続（`connect()`）にだけ持つ。テストの失敗の文・標準出力・ログに、接続の情報を出さない。共有のアプリの起動に、U1 の `InternalDbSecretLeakIT` と同じ形の検査（標準出力の取り込みで、URL・利用者・パスワードの文字列が無いこと）を 1 件足す（`PgSecretLeakIT` 案、`OutputCaptureExtension` は `@InternalDbPostgresIT` に含まれる）。 | NFR2.1 | U1 の `InternalDbSecretLeakIT` は H2 の経路。PostgreSQL の経路は U3 が持つ |
| DD-13 | テストの比較は、`assertThat(status).isEqualTo(…)` のように件数・状態コード・`code`・バイト数・集合の大きさで行い、行の値（メールアドレス・トークン・ハッシュ・氏名・DSL の本文）を失敗の文に載せない。値を比べるときは、ハッシュ（SHA-256）の一致の真偽・長さだけを出す。PostgreSQL の違反の文（`Key (…)=(…)`）を `assertThat` の説明にも出力にも入れない。重なり・待ちを起こしたテストの後で、アプリのログ（`OutputCaptureExtension`）に作った値が出ないことを、BR2・BR3・BR5 のクラスに 1 件ずつ確かめる。 | NFR2.2 | `project.md` の Code Style |
| DD-14 | 秘密の役の値は、`TestDatabase.randomSecret()`（乱数の 16 進数）で作る。このメソッドは H2 の URL を作る同じクラスにあるが、乱数を返すだけで H2 に依存しない。U1 の `InternalDbPostgresDatabase` も同じメソッドを使っているため、**読むだけの使用として許す側に入れる**（NFR 要件の NFR9.1 の「許さず」の記述との差。下の「上流との差」）。H2 の URL を作る `TestDatabase.url` / `register` は使わない。テストのメールアドレスは予約のドメイン（`example.com`）だけ。 | NFR2.3 | 機能設計の BR9.3・BR9.5 と一致する。Gitleaks の除外を足さない |
| DD-15 | 待ち・行き詰まり・重なりは、テストごとに作る専用データベース（`InternalDbPostgresDatabase.create()`）の中だけで起こす。コンテナの管理者の資格情報は、`create()` の中（データベースと専用の利用者の作成・削除）だけで使い、テストの本体に渡さない。ただし、保持側の接続（`pg_stat_activity` の観測）は専用の利用者でできる範囲（自分のデータベースのセッション）に限る。他のデータベースのセッションは見えない。 | NFR2.4 | 資格情報の最小化 |
| DD-16 | 10MB の本文・全バイト値のハッシュは、テストの中で乱数または決定的な生成で作り、出力・ログ・失敗の文に出さない。保存は既存の API（検証・上限を通る）で行う。上限 +1 バイトの拒否は、既存の検証の応答（状態コードと `code`）で確かめる。 | NFR2.5 | `checklist-dsl.md` |
| DD-17 | 失敗の出力は、テスト名と `@DisplayName`（英語）に、機能・流れ・確かめる点（例: `login flow returns the same code as H2`）を入れて原因が分かる形にする。Code Generation で、1 件を意図して失敗させて出力を読む確かめを 1 回行い、値が出ないことを確かめる。 | NFR2.6 | 要件 NFR2 |
| DD-18 | 対応の範囲: PostgreSQL 18 系のイメージは U1 の `InternalDbPostgresContainer` だけが持つ。U3 のテストに `postgres:` の文字列を書かない（`grep` の確かめ）。依存は足さない（`backend/gradle.lockfile` の差分が無いこと）。 | NFR3.1・NFR8.1 | 版の重ね書きを避ける |

## 上流との差

- NFR 要件の NFR9.1（`tech-stack-decisions.md`）は「`TestDatabase.randomSecret` を許す側に入れない」と書き、NFR2.3（`security-requirements.md`）は「`TestDatabase.randomSecret()` を使う」と書いて食い違う（NFR 要件のレビュー R-02）。設計では、**`randomSecret` は許す側**に決める（DD-14）。`TestDatabase.url`・`register`（H2 の URL を作る）は許さない。レビュー済みの要件の文書は書き換えず、この差を記録する。

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/nfr-requirements/security-requirements.md`・`tech-stack-decisions.md`
- `aidlc/spaces/default/memory/project.md`（Forbidden・Code Style）・`aidlc/spaces/default/knowledge/aidlc-shared/checklist-dsl.md`

## Assumptions & Open Questions

None.
