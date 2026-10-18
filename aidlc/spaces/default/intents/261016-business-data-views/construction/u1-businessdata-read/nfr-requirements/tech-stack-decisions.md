# 技術の選択 — U1 u1-businessdata-read

## 出典

- コード知識ベース `aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`
- この単位の機能設計 `construction/u1-businessdata-read/functional-design/`
- この段の答え `construction/u1-businessdata-read/nfr-requirements/nfr-requirements-questions.md`（まとめの確認の「新しい依存は無い」、Looks correct）
- `team.md` の Code Style（新しい依存は採用の前にライセンスを確かめる、取得元は Maven Central だけ）

## ID の対応表

この文書は要件の ID を持たない。どの要件がどの技術を使うかを下の表に書く。全体の表は `security-requirements.md` の「ID の振り方」にある。

## 決め

**新しい依存は足さない。** この単位の要件は、すべて採用済みの部品で満たす。

| 用途 | 使う部品 | 状態 | 使う要件 |
|---|---|---|---|
| 対象DB の接続のプール | HikariCP（Spring Boot の管理の版） | 採用済み。既定の候補にしない今の `targetDataSource` を使い、新しい Bean を作らない | NFR2.4・NFR4.3 |
| 対象DB への問い合わせ | JDBC と、3つのドライバー（PostgreSQL・MySQL・MariaDB） | 採用済み。接続の設定（URL）は変えない（`functional-spec.md` の X8） | NFR3.1・NFR4.1・NFR4.4 |
| SQL の識別子の引用 | 既存の `SqlIdentifiers.quoteKnown`（`targetdb.domain`） | 自前の部品を使い回す | NFR4.1 |
| 本文の上限 | 既存の `RequestBodyLimitRoute` と `RequestSizeLimitFilter` | 自前の仕組みに道を1つ足す | NFR4.5 |
| 3種類の対象DB の結合テスト | Testcontainers（版を固定したイメージ） | 採用済み | NFR3.1・NFR3.3・NFR4.8・NFR7.1 |
| 性質ベースのテスト | jqwik 1.10.2 | 採用済み | NFR7.2 |
| 構造の検査 | ArchUnit | 採用済み。`BusinessDataBoundaryArchitectureTest` を足す | NFR4.1・NFR4.3 |
| 静的解析 | SpotBugs ＋ FindSecBugs | 採用済み。`SQL_` の除外を3つの名前で絞って足す | NFR4.1 |
| 接続の数を数える | 自前のテスト用の包み（既存の `ConnectionCountingDataSource` と同じ形） | `src/test/java` の `businessdata/testsupport` に置く | NFR2.3 |
| 負荷の試験 | k6（手元の道具）・`perf/` の台本・`docker/perf/compose.yaml` | 採用済み。場面と行を作る台本を足す | NFR1.1〜NFR1.3・NFR2.1・NFR2.4・NFR2.5・NFR3.2 |
| 手元の監視 | grafana/otel-lgtm（compose の profile） | 採用済み。ダッシュボードのファイルに区画を足す | NFR8.3 |

- ライセンスの確かめ: 足す依存が無いため、新しい確かめは無い。行を作る台本は、既存の `docker/targetdb/generate-large-schema.sh` と同じく、シェルと DB の SQL（PostgreSQL の `generate_series` など）だけで書き、新しい道具を入れない。新しい道具が要るとなったら、コード生成の計画で採用の前にライセンスを確かめ、Apache License 2.0 と違えば理由を記録する。
- 採らなかった案: 正規表現の時間の上限や SQL の組み立ての外部の部品（jOOQ など）。jOOQ は SQL の組み立てを1つの部品に閉じる決まり（`team.md`）と ArchUnit の絞り込みを崩し、依存も増えるため採らない。正規表現の時間の上限は U2 の受け持ち（NFR4.7）である。

## 上流との差

- 承認の場の決定（`formPart` の値域から `lookup` を外す、`nullable` は U2 が足す）は、技術の選択に差を生まない。
- R-07・R-08 は `reliability-requirements.md` で受ける。どちらも採用済みの部品の使い方の話で、新しい依存は要らない（R-08 のやり方は JDBC の `OffsetDateTime` かセッションのタイムゾーンの設定で、どちらもドライバーの標準の機能）。

## 後の段に回すこと

- **NFR 設計の試し**: 受け取る量の絞り方・時差の扱い・ヒープの見積もりを、採用済みの JDBC とドライバーの範囲で確かめる。だめなときに新しい部品が要るなら、その時点でライセンスを確かめて依頼者に諮る。口の置き場は NFR 設計の論理の部品で決める。
- **Performance Validation**: k6 と使い捨ての環境を使う。
- **Observability Setup**: grafana/otel-lgtm のダッシュボードのファイルを直す。

## U2・U3 とそろえる点

- U2 は書き込みの DataSource と対象DB 用のトランザクションの管理者を足すが、部品は同じ HikariCP と Spring の JDBC の仕組みで、新しい依存は足さない見込み。正規表現の時間の上限（NFR4.7）で部品が要るなら、U2 の tech-stack で確かめる。
- 接続を数えるテスト用の包みと、行を作る種の台本は、U1 と U2 で同じものを使う。
- U3 の画面の部品は make-you-chic-ui と採用済みの道具を使い、U1 の決めとは独立である。
