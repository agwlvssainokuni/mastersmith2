# 性能・検査の時間の設計 — u2-portable-sql

この単位は、実行時の処理を増やしません。増えるのは、行がまだ無い最初の 1 回の書き込みで、文が 1 本（挿入）増えることだけです。キャッシュ・非同期・ページ送りなどの性能の手段は、この単位に当たらないため使いません。

## 実行時の性能（NFR1.1・NFR1.2・NFR1.3）

- **`savePreferences`**: JDBC の生成キーで 1 文のまま（NFR1.1）。接続を借りる回数・文の数は今と同じ。
- **作業ロールの保存・プレビューを置く**: 行が既にあるとき、更新 1 文（今の `MERGE` と同じ数）。最初の 1 回だけ、更新（0 行）→ 挿入の 2 文（NFR1.2）。重なったとき（稀）だけ、更新のやり直し 1 文が足される。セーブポイントは同じ接続の中の往復で、接続は借りない。
- **10MB の本文**: 呼び出しの引数の `byte[]` を、そのまま `PreparedStatement` に束縛する（複製しない。NFR1.3）。今の経路（`executeUpdate` の束縛）と同じく、本文は呼び出しの間だけヒープにある。

## DD-4 文の数・接続の数の確かめ方（NFR1.1・NFR1.2。NFR 要件の審査の指摘への答え）

NFR 要件の確かめ方「文の数を数える」の手段を決める。

- 既存の `ConnectionAcquireCounter`（`appearance/testsupport`）は、接続を借りた回数（`hikaricp.connections.acquire`）しか数えず、文の本数は数えられない。また、別の機能のテストの部品を `role`・`dslmanage` のテストから参照すると、テストの機能間の依存が増える。
- そこで、**文の本数を数える小さなテストの部品**（目安の名前: `StatementCounter`）を、`backend/src/test/java/cherry/mastersmith/common/testsupport` に新しく足す（既存の部品・テストは書き換えない）。内部DB の `DataSource` を、`java.lang.reflect.Proxy` で包んで `Connection.prepareStatement` と `Statement.execute*` の回数を数える（`BeanPostProcessor` の形。依存を足さない。NFR8.1）。
- 使う確かめ: (a) `savePreferences` が 1 回の呼び出しで文 1 本、(b) 行が既にあるとき作業ロールの保存・プレビューを置くが文 1 本・接続を借りる回数 1、(c) 行が無い最初の 1 回は文 2 本。
- 接続を借りる回数は、同じ部品が `DataSource.getConnection` の回数でも数える（`ConnectionAcquireCounter` を移さない・参照しない）。

## DD-5 検査の時間（NFR5.1・NFR5.2・NFR5.3）

- 通常の `verify` に足すテストは、コンテナを使わない単体テストと、H2 の結合テスト（同時の実行・文の数・既存の書き直し）だけにする。PostgreSQL の結合テストは U3 が専用のタスクに足す。
- 同時の実行の結合テストは、先の側の保持を約 500 ms にした確かめを中心にする（待ち合わせで作る）。上限切れ（H2 約 4 秒）の確かめは、作業ロール 1 本・プレビュー 1 本の 2 本までにする。テストの時間の合計は、全件の `verify` の増加が約 30 秒以内（目安）。
- 時間の測り方（NFR5.2・NFR5.3）: Build and Test で、CI の検査の job の時間を基準（21分32秒）と並べ、全件の `verify` の時間を測って記録する。1.1 倍（約 23分42秒）と約 42 分の線を超えたら、原因を確かめて依頼者に諮る。

## 測らないもの（NFR1.4）

- PostgreSQL のときの書き込みの所要時間・メモリは、この単位では測らない（U1 の起動の基準と U3 の振る舞いの確かめに任せる）。k6・`docker/perf/`・`perf/README.md` には手を入れない。Build and Test の成果物に、測っていないことを書く。

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/nfr-requirements/performance-requirements.md`（NFR1.1〜NFR1.4・NFR5.1〜NFR5.3）・`reviews/review-01.md`（R-01・R-04）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/functional-design/functional-spec.md`（F3・F5・F6・F10）
- `backend/src/test/java/cherry/mastersmith/appearance/testsupport/ConnectionAcquireCounter.java`
- `docs/verification.md`（CI の検査の時間の基準）

## Assumptions & Open Questions

- [assumption] `DataSource` を `Proxy` で包む形（`StatementCounter`）は、内部DB の接続プールの計測・JMX を使わないテストの中だけで有効にする（本体の `DataSource` の Bean は変えない）。
