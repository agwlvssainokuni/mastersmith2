# 技術の選択 — U1 u1-reference-read

出典: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/functional-spec.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/rules.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`

## 出典

- コード知識ベース `aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`（Java 25、Spring Boot 4.1.2、HikariCP 7.0.2、3つの JDBC ドライバー、Testcontainers 2.0.5、jqwik 1.10.2、ArchUnit 1.5.1、k6 2.3.0）
- 要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`（NFR9、制約の「既存の部品に足す形を既定とする」）
- この単位の機能設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/`（`functional-spec.md` 5節、`rules.md` の BR11）
- `aidlc/spaces/default/memory/team.md` の Code Style（新しい依存は採用の前にライセンスを確かめる、取得元は Maven Central だけ、参照の SQL は既存の `RowSqlExecutor`・`RowDialect` に足す）

## ID の対応表

全体の表は `security-requirements.md` の「ID の振り方」にある。

| 上流の ID | この単位での扱い | この単位の ID |
|---|---|---|
| NFR9 供給網 | 当たる | NFR9.1 |

## 要件

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR9.1 | この単位は新しい依存（Gradle の依存・npm のパッケージ・コンテナのイメージ）を足さない。要件はすべて採用済みの部品で満たす。足す必要が出たときは、採用の前にライセンスを確かめ、Apache License 2.0 と違えば理由を ADR に残し、lockfile と OSV-Scanner の対象に載ることを確かめる | コード生成の差分で `backend/gradle.lockfile`・`libs.versions.toml` に増えた依存が無いこと | Code Generation |

## 使う部品

| 用途 | 使う部品 | 状態 | 使う要件 |
|---|---|---|---|
| 対象DB の読み取りの接続 | HikariCP の既存の読み取りのプールと、既存の読み取りの口（`TargetReadSession`） | 採用済み。新しいプールと Bean を作らない | NFR3.1〜NFR3.3・NFR3.6 |
| 時間の上限 | 既存の `ReadPurpose` と `TargetDbProperties.QueryTimeout` に `REFERENCE` を足す | 自前の仕組みに値を1つ足す | NFR3.4・NFR3.5 |
| 参照先の SQL | 既存の `RowSqlExecutor`・`RowSqlBuilder`・`RowDialect` の `search` に `ReferenceReadSpec` を渡す | 自前の部品に足す。新しい SQL のメソッドを作らない | NFR1.3・NFR1.7・NFR1.12・NFR2.6 |
| 識別子の引用 | 既存の `SqlIdentifiers.quoteKnown` | 使い回す | NFR1.3 |
| 本文の上限 | 既存の `RequestBodyLimitRoute` | 道を1つ足す | NFR1.4 |
| 3種類の対象DB の結合テスト | Testcontainers（版を固定したイメージ） | 採用済み | NFR1.10・NFR1.11・NFR3.1・NFR3.2 |
| 性質ベースのテスト | jqwik | 採用済み | NFR1.14 |
| 境界の検査 | ArchUnit | 採用済み | NFR1.12・NFR1.13 |
| 負荷の試験 | k6（`perf/versions.env` で固定） | 採用済み | NFR2.1〜NFR2.5 |
| 時計 | 注入できる `Clock` | 既存の形 | NFR3.5 |

## 選ばなかったもの

| 案 | 選ばなかった理由 |
|---|---|
| 参照先の選択肢のキャッシュ（Caffeine など） | 要件の範囲の外。権限と参照先の変更をすぐ反映するため、毎回読む |
| 参照の読み取りだけの別の接続のプール | 1要求1本で、既存の読み取りのプールで足りる（NFR3.3）。プールを分けると対象DB の接続の総数が増える |
| `search` とは別の SQL のメソッド | 機能設計の BR11.2。`prepareStatement` を呼ぶ場所と SpotBugs の除外を増やさないため |
