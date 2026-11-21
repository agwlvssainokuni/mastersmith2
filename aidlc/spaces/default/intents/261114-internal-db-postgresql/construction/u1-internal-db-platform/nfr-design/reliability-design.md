# 信頼性の設計 — u1-internal-db-platform

NFR Requirements の `NFR4.x` を、起動の失敗の扱い・待ちの上限・テストの確実さの設計に落とします。サーキットブレーカー・再試行・フェイルオーバー・レプリケーションは、内部DB が 1 つ（開発者の PC 上）の前提のため使いません（理由は末尾の「採らなかった形」）。

## DD-1 接続のたびの文を入れる対象の絞り込み（NFR4.1）

機能設計のレビューの指摘 R-01 への設計側の答えです。

- `InternalDbPoolCustomizer` は `postProcessAfterInitialization` で動く。接続プールの設定（`spring.datasource.hikari.*`）の束ね込みは「初期化の前」の段で終わるため、「初期化の後」に当てると、利用者が `connection-init-sql` を指定していても PostgreSQL のときは置き換えられる（BR3.1）。
- 当てる条件は 3 つすべて: (a) Bean の名前が `dataSource`、(b) `HikariDataSource` である、(c) プール名が `mastersmith-db`。1 つでも外れたら何もしない。対象DB のプール（`mastersmith-target-db`・`mastersmith-target-db-write`。`TargetDataSourceConfig`・`TargetWriteDataSourceConfig` が作る）には、MySQL・MariaDB を含め、何も足さない。
- 種類は、早く作られる静的な Bean が `Environment` から接続先の値を読み、`InternalDbUrlResolver`（純粋関数）を自分で呼んで決める。`InternalDbSettings` も同じ関数を同じ値で呼ぶため、結果は同じになる（機能設計 R-03 への答え）。
- PostgreSQL のとき `setConnectionInitSql("SET lock_timeout = '2s'")`、H2 のときは何もしない。プールが既に始まっていた場合は、`InternalDbStartupException`（`UNKNOWN`）で起動を止める（接続プールの設定は始まった後は変えられないため、効かないまま黙って進まない）。
- 確かめ（コード生成の最初の単体テスト）: (1) PostgreSQL の内部DB のプールだけに入ること、(2) 対象DB のプール・H2 のプールには入らないこと、(3) 利用者の `connection-init-sql` が置き換わること、(4) 最初の接続の前に設定が効くこと。

| 採らなかった形 | 理由 |
|---|---|
| 接続先の URL に `options=-c lock_timeout=2000` を足す | 利用者が URL を自分で組むため、URL の組み立てを触ることになり、URL を扱うログの漏れの面が増える |
| Hibernate の文の前に毎回 `SET LOCAL` を流す | 借りる経路に処理が増え、NFR1.4 に反する |

## DD-5 移行の接続の待ち（NFR4.2・NFR4.3）

依頼者の決定（質問 Q1: A）: 移行の接続も `lock_timeout` 2 秒のまま。別の接続を作らず、Flyway の `initSql` も使わない。

- 内部DB を使うのはこのアプリ 1 つ（NFR7.1）で、移行の文は待たされず、V1〜V14 は 365 ms で通った（機能設計の試し）。
- 外の接続が Flyway の履歴の表や対象の表を握っていると、2 秒で待ちが切れ、`MIGRATION_FAILED`（版の番号つき）で起動が止まる。運用者が握りを解いて起動し直す。起動は有限の時間で結果が決まる（NFR4.3）。
- 確かめ: PostgreSQL の結合テストで、外の接続に `flyway_schema_history` の排他を握らせ、起動が 2 秒台で `MIGRATION_FAILED` になることと、握りを解いた後の起動が通ることを 1 本確かめる。

| 採らなかった形 | 理由 |
|---|---|
| 移行だけ有限の長い上限（30 秒）にする（質問 B） | 移行の接続の抜け道の設定が増え、起動が最大 30 秒止まって見える |
| 移行だけ別のデータソースで実行する | 接続の設定が 2 通りになり、接続情報の扱い（NFR2.1）が増える |

## DD-2 種類の判断の置き場（NFR4.1）

種類の判断は `InternalDbUrlResolver`（純粋関数）の 1 か所で、`InternalDbPoolCustomizer` と `InternalDbSettings` が同じ値で呼ぶ（DD-1）。業務の層は種類を見ない（NFR9.1）。

## DD-3 起動の失敗の設計（NFR4.4）

起動の流れと、失敗した場所:

1. `InternalDbPoolCustomizer`（接続プールの作成の直後）: 接続先が空（`EMPTY_URL`）・対応しない種類（`UNSUPPORTED_KIND`）なら、ERROR 1 件を出し、`InternalDbStartupException` を投げる。Bean の作成の失敗として Spring が包むが、連なりの文は固定の説明だけ（機能設計 R-04 への答え: 報告して投げる部品は `InternalDbPoolCustomizer`）。
2. `InternalDbMigrationStrategy`（`flyway.migrate()`）: 接続の失敗（`CONNECTION_FAILED`）・移行の失敗（`MIGRATION_FAILED`・`MIGRATION_VALIDATION_FAILED`）・その他（`UNKNOWN`）を受け、ERROR 1 件を出し、cause の無い `InternalDbStartupException` を投げる。
3. プロセスは、Spring Boot の既定どおり、アプリの初期化の失敗として異常終了する。専用の終了コードは決めない。

同じ失敗でログが 2 件にならないよう、報告は「例外を最初に受けた部品」だけが行う（再投げの例外は `InternalDbStartupException` のため、受けた側は再報告しない）。

## 戻し・前方互換（NFR4.6・NFR4.7）

- H2 の移行（V1〜V14）と置き場は 1 文字も変えない。前の版で作った H2 のファイルのまま新しい版が起動する。H2 の配備の戻し方（直前の版のイメージ）は、この Intent の前と同じ。
- PostgreSQL の内部DB は、この Intent で初めて作るため、前の版には戻せない。戻しは、PostgreSQL の複写（`pg_dump` など）を戻す手順（U4）で扱う。
- 移行は H2 と PostgreSQL の 2 つの置き場に同じ版の番号で組にし、突き合わせの単体テスト（BR2.8）が、組が揃うことと同じ名前の制約・索引を作ることを毎回確かめる。

## ヘルスチェック（NFR4.5）

- 既存の制限時間付きの確認（`TimeBoundedDbHealthIndicator`）をそのまま使う。内部DB の種類で分岐せず、時間の上限も変えない。
- 確かめ: PostgreSQL の結合テストで UP を確かめ、コンテナの接続を落とした後に DOWN になることを確かめる。

## テストの確実さ（NFR4.8・NFR4.9）

- コンテナの実行環境の判定は `InternalDbPostgresRuntimeCheck`。無いとき、手元では警告を出して PostgreSQL のテストだけを飛ばし、明示して CI で流したときは飛ばさず失敗する（BR6.4）。
- 専用のタスク `:backend:internalDbPostgresTest` は、終わりの集計で実行件数が 0 なら失敗にする（BR6.3、D-8）。`-PitTests` の形がタグ付きのクラスだけに当たったときも、黙って 0 件で通さず、専用のタスクを案内して失敗する。
- コンテナはテストの JVM ごとに 1 つ起動して使い回し、空のデータベースが要るテストは専用のデータベースを `CREATE DATABASE` で作り、終わったら `DROP DATABASE` で消す。名前に実行ごとに一意の値（乱数）を入れる。

## 採らなかった形（再試行・ブレーカー・多重化）

| 形 | 理由 |
|---|---|
| 起動時の接続の再試行 | 開発者の PC 上の 1 インスタンスで、失敗は設定の誤りか DB の未起動。すぐ止めて ERROR 1 件を出す方が原因が分かる。再起動は運用の側（compose の `restart` など）が担う |
| サーキットブレーカー・フェイルオーバー・レプリケーション | 内部DB は 1 つ。配備先が決まっていない（`team.md` の Deployment） |

## NFR4.10（起動の INFO 1 行）の設計

`InternalDbPoolCustomizer` が、内部DB のプールに当てたとき（種類が確定したとき）に、INFO を 1 行出す。キー: `internalDbKind`（`H2`・`POSTGRESQL`）。値は種類の名前だけ。H2 のときも出す（どちらで起動したかを同じ形で確かめられる）。

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/nfr-requirements/reliability-requirements.md`（NFR4.1〜NFR4.10）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/nfr-design/nfr-design-questions.md`（Q1: A）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/functional-design/functional-spec.md`（W1・W2、E1〜E15）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/functional-design/reviews/review-01.md`（R-01・R-03・R-04 への設計側の答え）
- `backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDataSourceConfig.java`・`TargetWriteDataSourceConfig.java`（対象DB のプールを作る Bean）
- `backend/src/main/resources/application.yaml`（`mastersmith-db`）

## Assumptions & Open Questions

- [assumption] 接続プールの設定は、プールが始まる前なら setter で変えられる（HikariCP と `DataSourceBuilder` の作り）。コード生成の最初の単体テスト（機能設計の T8）で確かめ、崩れたら設計の見直しとして依頼者に諮る。
- [assumption] 外の接続に握られたときの失敗は 2 秒台で `MIGRATION_FAILED` になる見込み。実測は PostgreSQL の結合テストで行う（DD-5 の確かめ）。
