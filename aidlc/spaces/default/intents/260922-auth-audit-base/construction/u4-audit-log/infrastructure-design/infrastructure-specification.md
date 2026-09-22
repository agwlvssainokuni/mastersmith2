# Infrastructure Specification — U4 監査ログ（u4-audit-log）

U4 の基盤の設計。入力は U4 の NFR Design（`performance-design.md`・`security-design.md`・`scalability-design.md`・`reliability-design.md`・`observability-design.md`・`logical-components.md`）、Domain Design の部品の一覧（`components.md`。AuditLog）、U4 の `functional-spec.md` である。`contract-summary.md` は、本ワークフローで Contract Design を行わないため無い。設計の方針の確認は `infrastructure-design-questions.md`（要約の確認）にある。

U4 は、U1 の基盤（U1 の `infrastructure-design/infrastructure-specification.md`）と、U2 の基盤の設計で足した設定（U2 の `infrastructure-design/infrastructure-specification.md`: タイムゾーン `Asia/Tokyo`、CPU の上限 4）の上で動く。U4 のために基盤に足す・変える点は、監査イベントの表のスキーマと、ボリュームの扱いの注意だけである。

## 1. 配備（Deployment）

| Facet | Choice | Rationale |
|---|---|---|
| 実行の形 | U1 と同じコンテナ・同じプロセス | ADR-008、U4 の `logical-components.md` |
| 保存 | 監査イベントの表を U1 の内部DB（`/app/data` の名前付きボリューム）に置く。表・主キー（DB の連番）・発生の日時の索引・要求のパスの項目（任意）を、Flyway の U4 のファイルで作る | U4 の `scalability-design.md` 2章・`security-design.md` 6章 |
| 保存の期間 | 無期限（削除の処理を持たない） | U4 の NFR1.6 |
| 容量 | 1年 約 180MB（1日 500 件 × 1KB × 365 日。索引を含めて 250MB 程度）。U1 のボリュームで扱える | U4 の `scalability-design.md` 1章 |
| 守り | ボリュームは実行の利用者だけが読み書きできる権限（U1 の設計）。アプリは監査イベントを変える・消す処理を持たない | U4 の NFR3.2・NFR3.3 |
| 資源の大きさ | 追加なし。監査の書き込みは追記1回で、確定の後の経路で一時的に DB の接続を2本使う（U1 のプール 最大 10 本の中） | U4 の `performance-design.md` 3章 |
| ネットワーク | 追加なし。U4 は API を持たない | U4 の NFR3.5 |

## 2. 基盤のサービス（Infrastructure Services）

| Service | Role | Configuration | Notes |
|---|---|---|---|
| H2（U1 の内部DB） | database | 監査イベントの表（追記だけ）、発生の日時の索引1つ | 別の保存先（ログの収集の基盤など）には送らない。監査の記録をアプリのログで代用しない（U4 の NFR9.2） |

## 3. 運用の注意（README に書く）

| 注意 | 内容 |
|---|---|
| ボリュームの削除 | `docker compose down -v` などでボリュームを消すと、監査ログも消える。消す前に、U1 のバックアップの手順（アプリを止めて `/app/data` を複写）を行う |
| バックアップ | U1 の手順で、監査ログも一緒に守られる。複写したファイルも、実行の利用者と管理する者だけが読める場所に置く |
| 直接の書き換え | OS の権限を持つ者によるファイルの直接の書き換えは検知できない（U4 の NFR3.6 で受け入れた危険） |

## 4. 単位で共有する基盤（Shared Infrastructure）

| Shared Resource | Owner Unit | Consumer Units | Access Boundary |
|---|---|---|---|
| 内部DB の監査イベントの表 | U4 | U2・U3 は出来事を知らせるだけで、表を直接読み書きしない | U4 の保存の部品（追記と読み取りだけ）を通す |
| コネクションプール | U1 | U4（確定の後の経路で2本目を借りる） | U1 の上限 10 本の中 |
| ボリューム `/app/data` | U1 | U2（利用者・トークン）、U4（監査イベント） | 同じボリューム。バックアップと削除の影響を共に受ける |
