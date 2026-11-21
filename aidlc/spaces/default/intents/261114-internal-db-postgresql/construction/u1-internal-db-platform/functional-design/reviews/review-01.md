## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-18T15:29:32Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/functional-design/functional-spec.md > 3 部品の分け方 の起動の入口 (2) 接続プールの BeanPostProcessor・W1 手順 3 | 内部DB 以外にも接続プールの Bean がある。`TargetDataSourceConfig` と `TargetWriteDataSourceConfig` は `HikariDataSource` を作る。設計は BeanPostProcessor がどの Bean に作用するかを決めていない。「接続プール」とだけ書いてあるため、対象DB のプール（MySQL・MariaDB を含む）にも `SET lock_timeout = '2s'` が入りうる。実装の自由度が大きく、MySQL への接続が壊れる。また `BeanPostProcessor` が `spring.datasource.hikari.connection-init-sql` の束ね込みの前に動くと、BR3.1 の「利用者の指定を置き換える」が成り立たない。BPP の段（Before か After）の指定も無い | BPP が作用する対象を設計で決める（例: 内部DB の `dataSource` の Bean 名とプール名 `mastersmith-db` の両方で絞る）。束ね込みの後（postProcessAfterInitialization）に当て、プールの開始前でも setter が効く前提と合わせて T8 の単体テストの対象に入れる。対象DB のプールには何も足さないことを BR3.2 と並べて決まりにする | New |
| R-02 | Major | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/functional-design/rules.md > BR5.1・BR5.2・BR3.5 と functional-spec.md > 2 の E11 | 起動時に接続できないとき、HikariCP は自分のロガー（`com.zaxxer.hikari`）で「プールの初期化に失敗した」旨を例外の連なりつきで ERROR に出す作りである。連なりの文にホスト・ポート・利用者名が入る。cause を付けない例外に包み直しても、プールが先に自分のログへ出す分は止まらない。E11 は Spring の WARN と ERROR だけを確かめている。`application.yaml` に `com.zaxxer.hikari` のログ設定は無く、BR3.5 が抑えるのは `org.postgresql` だけである。BR5.1 の「どの経路でも出ない」は、このままでは成り立たない見込みが高い（コード生成の T3 で初めて露見する） | 設計の段で、Hikari のログの出方を実際に確かめる。必要なら (a) `com.zaxxer.hikari` のプール初期化の失敗のログを抑える設定を BR3.5 と並べて決める、(b) プールを起動の入口で先に借りて失敗を包む、のどちらかを決まりにする。抑える場合は、動作中のプールの警告（枯渇など）を隠さない範囲を決める。T3 の確かめの対象に Hikari のロガーを明記する | New |
| R-03 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/functional-design/functional-spec.md > 3 の `InternalDbSettings` と起動の入口の注 | 「判断は1回だけ、ほかの3つの入口が同じ結果を使う」と、「BPP は静的な Bean で `Environment` だけを受ける」が両立しない。静的な早い Bean は `InternalDbSettings` の Bean を受けられない。結果として BPP は自分で判断を作り直すか、静的な保持場所を持つ。BR1.5（1か所・1回）の実現方法が曖昧である | `InternalDbUrlResolver` を純粋関数として BPP が自分で呼ぶ（結果が同じことは値の定義で保つ）のか、静的な保持場所を持つのかを決め、BR1.5 の文言を合わせる | New |
| R-04 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/functional-design/rules.md > BR5.1 と BR1.3・BR1.4 と functional-spec.md > 4 W1 手順 2・W2 | 空・対応しない接続先の検出は W1 手順 2 で BPP が行うが、失敗の記録と例外は BR5.1 の「Flyway を呼ぶ入口」の部品に書かれている。BPP が例外を投げると Bean 作成の失敗として包まれ、ERROR を出す部品が FlywayMigrationStrategy までたどり着かない。誰が ERROR を1回出し、どの例外を投げるかが決まっていない | EMPTY・UNSUPPORTED を報告し投げる部品を、BPP（Bean 作成の中）として BR5.1・W2 に明記する。Bean 作成の失敗で Spring が連ねる例外にも cause の文が入らないことを T2 の確かめに入れる | New |
| R-05 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/functional-design/rules.md > BR2.8 (e) と BR2.5 の注の指定 | BR2.5 は PostgreSQL 用の移行の先頭の注に「ON DELETE を付けない」理由を書くよう求めるが、BR2.8 (e) は同じファイルに `ON DELETE`・`BINARY(`・`BINARY LARGE OBJECT` が無いことを確かめる。コメントを取り除いてから読むと書かれているのは entities.md の objectNames の取り出しだけで、(e) には書かれていない。注の書き方しだいで単体テストが落ちる | BR2.8 (e) で、コメントを取り除いてから文字列を探すと書く | New |
| R-06 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/functional-design/rules.md > BR5.2 と BR5.3 | BR5.2 は「`getMessage()` の文は使わない」とするが、BR5.3 は版の番号を例外の文から取り出す。functional-spec の部品表は「文は読まない（版の番号だけ文から数字を取る）」と例外を書いている。決まりどうしの文言が食い違い、実装者が迷う | BR5.2 に「版の番号の数字を取り出すためだけは読む。出力に文は入れない」を足し、読む範囲を BR5.3 に限る | New |
| R-07 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/functional-design/functional-spec.md > 7 D-3 | 兄弟の置き場が混ざらないことは、jar と手元のフォルダでしか確かめていない（E15）。実行可能 WAR は `WEB-INF/classes` の入れ子で、Build and Test の起動確認は H2 だけである。PostgreSQL の結合テストはテストのクラスパスで走るため、WAR から `db/migration-postgresql` を読めることは U4 の PostgreSQL の配備の練習まで確かめられない | WAR の中に `db/migration-postgresql` の14本が含まれていることを確かめる項目（ファイルの一覧を読むだけでよい）を BR2.12 の確かめの記録か Build and Test の手順に足す | New |

### 提案（評決の根拠にしない）

- BR3.1 の `SET lock_timeout` は Hikari の `connection-init-sql` に入れる形だが、`options=-c lock_timeout=2000` を JDBC の接続の設定に渡す案もある。ただし利用者が URL を自分で組むため、BPP 案のほうが筋は良い。採らなくてよい。
- BR6.3 の「実行件数 0 で失敗」は、実行の終わりの集計を使う前に、タグ付きのクラスだけに当たった場合の Gradle の挙動を捨ての試しで1回確かめておくと、コード生成で迷わない。

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| 検証の道具 | 未実行（この段の定義に検証の道具の指定が無い。依頼の約束で Read だけに限った） | 上流との対応は目で突き合わせた。traceability.json の19件は unit-of-work.md の U1 の FR と一致し、各 target の BR は rules.md に実在してその中身と対応する。BR2.12・BR7.1・BR7.2 の reverse の N/A も NFR 由来で妥当 |

### Summary

上流（U1 の責務・C1〜C4・C6・ADR・FR/NFR）との対応は取れており、決めた判断（D-1〜D-10）とバイナリの写し方・V8 の STORED・兄弟の置き場の根拠も妥当である。承認の前に知っておくべき主な懸念は2つで、接続プールの BPP の対象を内部DB のプールに限る決まりが無いこと、起動の失敗で HikariCP 自身のログに接続先の情報が出うること（BR5.1 の目的に直結する）である。Major は2件で READY の範囲だが、どちらもコード生成の前に設計へ書き足すことを勧める。
