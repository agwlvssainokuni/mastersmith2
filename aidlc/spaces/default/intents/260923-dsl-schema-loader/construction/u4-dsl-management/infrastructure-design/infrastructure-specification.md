# Infrastructure Specification — U4 DSL の管理（u4-dsl-management）

U4 が使う基盤の一覧。配備先は開発者の PC 上のコンテナだけで、クラウドの基盤は作らない（`aidlc/spaces/default/memory/project.md` の Deployment）。要点の確認は `infrastructure-design-questions.md`。

## 1. 配備

| 項目 | 値 | 備考 |
|---|---|---|
| 配備の形 | 既存の実行可能 WAR 1つ（画面を同梱）を compose の `app` で起動 | 新しいサービスは足さない（ADR-004、アプリは1つ） |
| コンテナのメモリ | 既定の上限 1g（`MASTERSMITH_CONTAINER_MEMORY`）、ヒープ 75% | 10MB の DSL の処理のヒープの最大を Build and Test で測る。足りなければ上限の見直しを依頼者に諮る |
| 保存 | 内部DB（組み込みの H2、ボリューム `mastersmith-data`） | 既存 |

## 2. 内部DB の変更（Flyway、前進のみ）

| 移行 | 内容 | 後方互換 |
|---|---|---|
| `V5__u4_dsl_management.sql` | プレビューの表（固定の鍵の1行。previewId・本文 `BINARY LARGE OBJECT`・識別・出どころ・置いた人・置いた日時）と、適用の履歴の表（revisionId・本文 `BINARY LARGE OBJECT`・識別・出どころ・適用した人・適用した日時・追加の順） | 表を足すだけ。1つ前の版のアプリは使わない |
| `V6__u4_dsl_audit_columns.sql` | 既存の監査の表（`audit_events`）に、操作した人・DSL の識別・出どころ・理由の種類の列を足す | NULL を許す列を足すだけ。1つ前の版のアプリも動く |

- 保存量: プレビュー1件と履歴 20 件がすべて 10MB なら最大約 210MB。H2 のファイルの大きさを Build and Test で測る。
- 表の名前と列の名前はコード生成で決める（既存の命名に合わせる）。

## 3. 設定

| 環境変数 | 既定 | 用途 |
|---|---|---|
| `MASTERSMITH_DSL_MAX_SUBMIT_SIZE` | 10MB | 投入の道の本文の上限（`RequestSizeLimitFilter` の道ごとの上限） |
| `MASTERSMITH_DSL_HISTORY_LIMIT` | 20 | 適用の履歴の件数の上限 |
| `MASTERSMITH_TARGET_DB_*` | 無し | 対象DB の設定（U1。秘密情報は `.env` だけ） |

`application.yaml` には環境変数の参照だけを置き、`.env.example` に空の値で項目名を置く。

## 4. 共有するもの

| 共有するもの | U4 の変更 |
|---|---|
| `SecurityConfig` | `RequestSizeLimitFilter` を認証・認可の後に移し、道ごとの上限と code の表と、断ったことを知らせる口を足す（U4 の NFR 設計の決定 A）。既存のテスト3件はテスト用の決まりの下で 413 のまま（決定 C） |
| 監査の仕組み（`audit`） | 出来事の種類と列を足す（V6）。確定の後に記録する既存の仕組みを使う |
| 内部DB のプール（上限 30） | 適用は1要求で接続を2本使いうる（確定の後の監査）。重い処理は同時に1つのため、既存の見積もりの内に収まる見込み。Build and Test で確かめる |
| 監視（`docker/monitoring/`） | DSL の操作のパネルを足す（monitoring-design.md） |

## 5. 戻し方

- 設定だけの変更（上限の値など）は `.env` を戻して再起動する。
- アプリの戻しは、直前の版の WAR（イメージ）で再配備する（既存の手順）。V5・V6 は表と列を足すだけなので、1つ前の版のアプリはそのまま動く。
