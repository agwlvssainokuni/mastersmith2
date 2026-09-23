# Tech Stack Decisions — U1 対象DB（u1-target-db）

U1 で足す技術と、その数値・選定の理由を示す。NFR1 の枝番は、単位の間で重ならないよう通しで振る（U1 は NFR1.1〜1.3、U2 は 1.4〜1.5、U3 は 1.6〜1.7、U4 は 1.8〜1.17、U5 は 1.18〜1.21）。既存の構成は `aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`。答えは `nfr-requirements-questions.md`（Q1〜Q6）。細かい版（パッチ）は、コード生成で lockfile とイメージのダイジェストに固定する。

## 1. 選定

| 対象 | 選定 | ライセンス | 理由 |
|---|---|---|---|
| MySQL の JDBC ドライバー | MySQL Connector/J（`com.mysql:mysql-connector-j`、Spring Boot の BOM の版） | GPL v2 ＋ Universal FOSS Exception | Q4: A。公式のドライバーで、MySQL の型・コメントの取り方に確実に対応する |
| MariaDB の JDBC ドライバー | MariaDB Connector/J（`org.mariadb.jdbc:mariadb-java-client`、BOM の版） | LGPL 2.1 | Q4: A。公式のドライバー |
| PostgreSQL の JDBC ドライバー | PostgreSQL JDBC（`org.postgresql:postgresql`、BOM の版） | BSD 2-Clause | Q4: A |
| 対象DB の接続のプール | HikariCP（既存の 7.0.2） | Apache 2.0 | 既存の内部DB と同じ部品。新しい依存を足さない |
| 結合テストの DB の起動 | Testcontainers（`org.testcontainers`、BOM の版。MySQL・MariaDB・PostgreSQL のモジュールと JUnit 5 の連携） | MIT | project.md の Mandated（実際の DB のコンテナ） |
| 結合テストの DB のイメージ | `mysql:8.4`・`mariadb:11.8`・`postgres:18`（いずれも長く支援される版。コード生成でパッチとダイジェストを固定する） | 各 DB のライセンス（テストだけで使い、配布しない） | Q3: A、NFR12 |

### 1.1 Apache License 2.0 と異なるライセンスの採用の理由（team.md の Code Style）

- **MySQL Connector/J（GPL v2 ＋ Universal FOSS Exception）**: FOSS 例外は、OSI が認めたオープンソースのライセンス（Apache License 2.0 を含む）で配布するプロジェクトに、このドライバーを組み込んで配布することを認めている。MasterSmith は Apache License 2.0 で公開するため、WAR への同梱はこの例外の範囲にある。ドライバーのソースは変更しない。配布物にドライバーのライセンスの文書を含める。
- **MariaDB Connector/J（LGPL 2.1）**: 変更せずにライブラリとして使い、WAR の中の独立した jar として同梱する（利用者が差し替えられる）。配布物にライセンスの文書を含める。
- **Testcontainers（MIT）**: テストだけで使い、配布物に含めない。Apache License 2.0 と両立する。
- 配布物に第三者のライセンスの文書を含める作りは、コード生成で決める（WAR の中の置き場所）。

## 2. 数値

| ID | 項目 | 値 | 理由 |
|---|---|---|---|
| NFR1.1 | 接続の待ち時間の上限 | 5 秒（HikariCP の `connectionTimeout`） | Q1: A。届かない対象DB を早く見切る。試しで約 5 秒で失敗することを確かめた |
| NFR1.2 | 問い合わせ1回の待ち時間の上限 | 20 秒（`Statement.setQueryTimeout`） | Q1: A。30 秒の生成の目標（NFR1）の内に、生成と保存の時間を残す |
| NFR1.3 | メタデータの読み取りの問い合わせの数 | テーブルの数に比例しない。方式（スキーマ全体を、テーブル・カラム・キー・コメントの種類ごとにまとめて読む）と回数は NFR 設計で決め、30 秒の内訳の「U1 の読み取り 15 秒」（U3 の NFR1.7）の内に収める | BR2.2。100 テーブル × 100 カラムで 30 秒の内に収めるため。Build and Test で測る。NFR1.1・NFR1.2 は応答しないときの打ち切りの上限で、15 秒は正常なときの目標（別のもの） |
| NFR7.5 | プールの最大の接続の数 | 5 | Q2: B。後続の Intent（J・K）の業務データの操作を見込む |
| NFR7.6 | プールの最小の待機の接続 | 0（起動時に接続しない、`initializationFailTimeout` は -1） | NFR7.2 |
| NFR7.7 | 使っていない接続を閉じるまで | 60 秒（`idleTimeout`） | 要点の確認で決めた。まれな操作のため、接続を持ち続けない |
| NFR12.1 | 対象DB の結合テストの実行の場 | 3種類とも `./gradlew verify` の中で毎回（CI も同じ）。場所の見直しは Build and Test で時間とメモリを測って決める | team.md の Testing Posture |
| NFR12.2 | 表を作るテストの分け方 | テスト（またはクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作って消す | team.md の Testing Posture |
| NFR12.3 | コンテナの実行環境が無いとき | 警告を出して対象DB のテストだけ飛ばす。飛ばした状態では統合しない。CI では必ず実行する | team.md の Way of Working |

## 3. 接続の足し方（ADR-006）の試しの結果

Q6: A のとおり、使い捨ての作業ブランチで確かめ、試しのコードとブランチは捨てた（2026-09-23）。

- 試したもの: `@Bean(name = "targetDataSource", defaultCandidate = false)` の HikariCP（届かない先 `jdbc:h2:tcp://127.0.0.1:1/unreachable`、読み取り専用、最大 5・最小 0、接続の待ち 5 秒、`initializationFailTimeout` -1）を足した状態の Spring Boot 4.1.1
- 結果:
  - 型で注入される `DataSource` は内部DB（`jdbc:h2:file:`）。`DataSource` の Bean は `dataSource` と `targetDataSource` の2つになる
  - Flyway の接続と JPA のトランザクション管理の接続は内部DB（Flyway の適用 4 件）
  - ヘルスチェックは 200（UP）で、対象DB のプールは起動後に一度も接続していない（`isRunning()` が false）
  - 届かない対象DB への接続は 5016 ミリ秒で `SQLException` になる
  - 試しの接続を足したまま、既存の結合テストを含む 248 件（試しの 4 件を含む）がすべて通った
- 判断: ADR-006 の条件は満たされた。切り替え先（内部DB を明示して `@Primary` にする形）は使わない。B2 で、この確かめを本物の結合テストとして書く（NFR7.4）。
- 気をつけること: `getBeansOfType(DataSource.class)` のように型ですべてを集めるコードは対象DB の接続も拾う。既存のコードにその使い方は無い（試しで既存のテストが通ったことで確かめた）。後で足すコードでも、接続を型ですべて集めない。

## 4. 見送ったもの

- アカウントの書き込み権限を調べる仕組み（Q5: B）: DB の種類ごとに調べ方が違い、作りが増える。README の推奨と読み取り専用の接続で守る。
- MariaDB Connector/J で MySQL にもつなぐ（Q4: B）: GPL を避けられるが、MySQL への対応が限られ、確かめが増える。
- 1つ前の版の DB でも確かめる（Q3: C）: テストの時間と colima のメモリが増える。
