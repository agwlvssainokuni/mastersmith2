# Security Design — U1 対象DB（u1-target-db）

U1 の非機能の要件（`construction/u1-target-db/nfr-requirements/`）を満たす作りの方針を示す。実装はコード生成で行い、ここでは方針と境界だけを決める。答えは `nfr-design-questions.md`（Q1〜Q3）。

## 1. 接続の作り（NFR4.1・NFR4.5・NFR4.6・NFR7.2・NFR7.4〜NFR7.7）

| 項目 | 作り |
|---|---|
| 接続の Bean | `targetDataSource`（HikariCP）1つ。`@Bean(defaultCandidate = false)` で既定の候補にしない（ADR-006、試しで確かめた）。設定が無い・不正なときは Bean を作らず、読み取りは「設定が無い」を返す |
| 設定の受け口 | `mastersmith.target-db.*` の設定の型（`record`）だけ。値は `application.yaml` の環境変数の参照から入る。画面・API・DSL からの口を作らない。設定の型は `targetdb` の中に置き、ArchUnit で `web` 層から使われないことを確かめる |
| 資格情報 | ユーザー名とパスワードは HikariCP の `username`・`password` に渡し、JDBC の URL に入れない。URL は種類・ホスト・ポート・DB 名から組み立てる |
| 伏せ字 | 設定の型の `toString` はパスワードを値の有無だけにする（`record` の既定の `toString` を上書き）。TRACE のメソッドの追跡でも値が出ないことを結合テストで確かめる |
| 読み取り専用 | `readOnly = true`。書き込み・DDL を発行するコードを持たない（コードの点検と ArchUnit で `targetdb` から更新系の API を使わない） |
| 起動時 | 最小の待機 0、`initializationFailTimeout = -1` で起動時に接続しない。ヘルスチェックは既存の内部DB の確認だけ |
| プール | 最大 5、最小 0、使わない接続は 60 秒で閉じる（`idleTimeout`） |
| 接続の待ち | 生成・照合とも **3 秒**（`connectionTimeout`。Q1: A） |

## 2. 問い合わせの作り（NFR1.2・NFR1.3・NFR6.1〜NFR6.4）

- 読み取りの口は `readSchema(目的)`。目的は `GENERATE`（生成）・`COMPARE`（照合）で、問い合わせ1回の待ち時間を目的ごとに使う（生成 20 秒・照合 5 秒、`Statement.setQueryTimeout`。Q3: A）。数値は `mastersmith.target-db.*` の下の設定に既定値つきで置く。
- 照合では、読み取りの全体に 8 秒の上限を置く。超えたら残りの問い合わせをやめ、TIMEOUT を返す（U4 は「照合できなかった」の警告にする）。
- DB の種類ごとの読み手（MySQL・MariaDB 用、PostgreSQL 用）が、情報スキーマ（MySQL・MariaDB は `information_schema`、PostgreSQL は `information_schema` と `pg_catalog`）へ、次の種類ごとに1回ずつ問い合わせる（Q2: A）: テーブルとビュー（コメントを含む）、カラム（型・長さ・精度・NULL・コメント）、主キー、外部キー。合わせて 4〜5 回で、テーブルの数に比例しない。
- スキーマ名は `PreparedStatement` の値で渡す。問い合わせの文は固定の文字列で、識別子を組み込まない。将来、識別子を組み込む問い合わせが要るときは、読み取った名前の一覧にあるものだけを種類に合った引用符で囲む（NFR6.1）。
- 別のスキーマを参照する外部キーは、問い合わせの条件で除く（機能設計の BR2.6）。

```text
readSchema(purpose):
  if not configured -> UNCONFIGURED
  timeouts = settings.for(purpose)          // GENERATE: 20s / COMPARE: 5s, overall 8s
  with connection (connect wait 3s, read-only):
    tables  = query(TABLES_SQL,  schema)    // 1 query each, schema as bind value
    columns = query(COLUMNS_SQL, schema)
    pks     = query(PK_SQL,      schema)
    fks     = query(FK_SQL,      schema)    // same-schema only
  -> SUCCESS(assemble(tables, columns, pks, fks))
  on timeout -> UNAVAILABLE(TIMEOUT); on other failure -> UNAVAILABLE(CONNECTION_FAILED)
```

## 3. 失敗の扱い（NFR4.3・NFR4.4）

| 起きたこと | 結果 | ログ |
|---|---|---|
| 設定が1つも無い | UNCONFIGURED | 出さない |
| 設定の欠け・不正 | UNCONFIGURED | 起動時に WARN 1件（項目の名前だけ） |
| 接続の待ちの打ち切り、問い合わせの打ち切り、照合の全体の上限 | UNAVAILABLE（TIMEOUT） | WARN（原因の種類だけ） |
| 認証の失敗、接続の失敗、問い合わせの失敗 | UNAVAILABLE（CONNECTION_FAILED） | WARN（原因の種類だけ） |

例外の文言・接続先・ユーザー名は、結果にもログにも入れない。例外は SQLState の種類で見分け、文言を読まない。

## 4. 残る危険（NFR 要件の「残る危険」の表の書き直し）

| 危険 | 扱い |
|---|---|
| 対象DB が遅いときの最悪の待ち | 生成: 接続 3 秒＋問い合わせ 20 秒×4〜5 回（生成は 30 秒の目標を超えることがある。正常なときの目標は読み取り 15 秒）。照合: 全体で 8 秒 |
| 利用者が書き込みの権限のあるアカウントを設定する | 読み取り専用の接続と、書き込みのコードを持たないことで守る。README で読み取りの権限だけを勧める |
| MySQL・MariaDB の読み取り専用の指定が強制されない場合 | 書き込み・DDL を発行するコードを持たないことで守る |

## 5. 承認済みの文書との差

承認済みの文書は書き換えない（`aidlc/spaces/default/memory/project.md` の Way of Working）。コード生成でこの表に合わせる。

| 決定 | 文書 | 承認済みの記述 | 実装での扱い |
|---|---|---|---|
| 接続の待ちを 3 秒にそろえる（Q1: A） | `construction/u1-target-db/nfr-requirements/tech-stack-decisions.md` の NFR1.1 | 5 秒 | 3 秒（生成・照合とも） |
| 読み取りの口に目的を渡す（Q3: A） | `inception/contract-design/contract-summary.md` の C1・C3（`TargetSchemaReader.readSchema` は引数なし）、`construction/u1-target-db/functional-design/functional-spec.md` の 2節 | 引数なし | `readSchema(ReadPurpose)`。U3 は `GENERATE`、U4 の照合は `COMPARE` を渡す |
| 照合の全体の上限 8 秒 | U4 の NFR 要件の NFR1.9（接続 3 秒・問い合わせ 5 秒） | 全体の上限は無い | 全体 8 秒を足す（10 秒の目標の内に収めるため） |
