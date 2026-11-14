# 要件と単位の対応（Intent 261114-internal-db-postgresql）

User Stories を飛ばしたため、要件定義の FR（枝番）ごとに、実装する単位を対応づける。1行に複数の単位を書くときは `;` でつなぐ。行に名前のある単位は、その機能設計でその FR の合否の条件をすべてたどる。

## 対応表

| 要件 | Unit ID | Directory | 単位が受け持つ部分 |
|---|---|---|---|
| FR1.1 | U1 | u1-internal-db-platform | 接続先が無いときに H2 で動く。前の版の H2 のファイルでの起動の確かめ |
| FR1.2 | U1 | u1-internal-db-platform | PostgreSQL の URL で PostgreSQL として動く |
| FR1.3 | U1 | u1-internal-db-platform | 対応しない種類で起動を止め、ログに URL と資格情報を出さない |
| FR1.4 | U1 | u1-internal-db-platform | 接続先と資格情報を環境変数だけから受け取る（今の形を保ち、境界テストで確かめる） |
| FR2.1 | U1 | u1-internal-db-platform | PostgreSQL 用の移行の一式と、空の PostgreSQL への移行の結合テスト |
| FR2.2 | U1 | u1-internal-db-platform | H2 の移行を変えず、H2 での移行の検証が通る |
| FR2.3 | U1 | u1-internal-db-platform | 同じ版の番号と表の形。突き合わせの単体テスト |
| FR2.4 | U1 | u1-internal-db-platform | 移行の失敗で起動を止め、版の番号をログに出す |
| FR2.5 | U1 | u1-internal-db-platform | PostgreSQL での初期管理者の1回だけの作成 |
| FR3.1 | U3 | u3-postgres-behaviour-tests | 機能ごとの代表の API の流れ1本ずつ |
| FR3.2 | U1; U3 | u1-internal-db-platform; u3-postgres-behaviour-tests | U1: 制約の名前と NO ACTION の外部キー。U3: 違反の区分と大文字と小文字の扱いの確かめ |
| FR3.3 | U1; U3 | u1-internal-db-platform; u3-postgres-behaviour-tests | U1: 接続ごとの `lock_timeout` の設定。U3: 2つの経路と行き詰まりの確かめ |
| FR3.4 | U3 | u3-postgres-behaviour-tests | 失敗の後のトランザクションと失敗の監査 |
| FR3.5 | U2; U3 | u2-portable-sql; u3-postgres-behaviour-tests | U2: 書き直しと H2 での確かめ。U3: PostgreSQL での確かめ |
| FR3.6 | U2; U3 | u2-portable-sql; u3-postgres-behaviour-tests | U2: 写し方。U3: 10MB と 32 バイトの PostgreSQL での往復 |
| FR3.7 | U1; U3 | u1-internal-db-platform; u3-postgres-behaviour-tests | U1: 時刻の列の型。U3: 時刻の往復と時刻で決まる判定 |
| FR3.8 | U2; U3 | u2-portable-sql; u3-postgres-behaviour-tests | U2: 両方で同じ結果になる書き方。U3: PostgreSQL での確かめ |
| FR3.9 | U1 | u1-internal-db-platform | PostgreSQL でのヘルスチェックの UP と DOWN |
| FR3.10 | U1 | u1-internal-db-platform | 接続の失敗の原因がログに出て、接続情報は出ない |
| FR4.1 | U4 | u4-local-runtime-docs | compose の内部DB 用の PostgreSQL（profile） |
| FR4.2 | U4 | u4-local-runtime-docs | イメージの版とダイジェスト、dependabot の固定先 |
| FR4.3 | U4 | u4-local-runtime-docs | 手元の既定は H2 のまま |
| FR4.4 | U4 | u4-local-runtime-docs | バックアップと戻しの手順、H2 だけの運用の明記 |
| FR4.5 | U4 | u4-local-runtime-docs | README と `docs/internal-db.md` |
| FR5.1 | U1; U3 | u1-internal-db-platform; u3-postgres-behaviour-tests | U1: 移行・初期管理者・ヘルスチェックの結合テスト。U3: 振る舞いの結合テスト |
| FR5.2 | U1 | u1-internal-db-platform | Gradle の明示の指定と CI の手動の実行 |
| FR5.3 | U1 | u1-internal-db-platform | コンテナが無いときの警告と飛ばし |
| FR5.4 | U2; U3 | u2-portable-sql; u3-postgres-behaviour-tests | U2: 書き直した後も H2 の結合テストの全件が通る。U3: H2 の結合テストとテストの部品を変えない |
| FR5.5 | U1 | u1-internal-db-platform | 普段の `verify` に入る単体テスト（種類の判断・起動の失敗・届かない接続先・移行の突き合わせ） |

## 単位にまたがる行

- FR3.2・FR3.3・FR3.7・FR5.1: U1 が仕組み（移行・設定・土台）を持ち、U3 が PostgreSQL での振る舞いを確かめる。
- FR3.5・FR3.6・FR3.8・FR5.4: U2 が書き直しと H2 での確かめを持ち、U3 が PostgreSQL での確かめを持つ。
- NFR（要件定義の3章）は単位に行を立てず、各単位の NFR 要件の段で受け持つ枝番を決める。PostgreSQL のときだけ通るコードのカバレッジ（NFR6）は主に U1、検査の時間（NFR5）は U1 の明示の指定の作り、依存（NFR8）は U1、構造（NFR9）は U1・U2 が受け持つ。

## 単位の中の作る順（目安）

- U1: 種類の判断と起動の失敗 → PostgreSQL 用の移行の一式と突き合わせの単体テスト → 接続の設定と移行の扱い → 結合テストの土台 → 移行・初期管理者・ヘルスチェックの結合テスト。
- U2: 自分の設定の保存 → 作業ロールの保存 → DSL のプレビューと適用の履歴 → 大きなバイナリの写し方 → 検索の確かめ。
- U3: 違反の区分と待ちの上限 → 失敗の後のトランザクション → 書き直した SQL・大きなバイナリ・時刻・検索 → 機能ごとの代表の API の流れ。
- U4: compose の profile と固定先 → 文書（使い方・バックアップと戻し・H2 だけの運用）。

## 網羅の確かめ

- FR1.1〜FR5.5 の 29 件すべてに単位がある（漏れなし）。
- 4つの単位すべてに行がある（U1: 18 行、U2: 4 行、U3: 10 行、U4: 5 行）。

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/domain-design/traceability.json`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/units-generation/unit-of-work.md`
