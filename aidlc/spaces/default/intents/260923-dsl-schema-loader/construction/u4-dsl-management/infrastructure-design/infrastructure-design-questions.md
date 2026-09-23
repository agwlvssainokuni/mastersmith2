# Infrastructure Design — U4 DSL の管理（u4-dsl-management）— 設計の要点の確認

U4 の基盤（内部DB の表・設定・監視・検査の流れ）は、NFR 要件と NFR 設計で中身が決まっている。新しく依頼者に尋ねる論点は無いため、要点を案として確かめる（`aidlc/spaces/default/memory/project.md` の Way of Working）。配備先は開発者の PC 上のコンテナだけで、クラウドの基盤は作らない。成果物は infrastructure-specification.md・monitoring-design.md・cicd-pipeline.md・traceability.json。

## 設計の要点（案）

- **内部DB（H2）の表**: Flyway の移行を前進のみで足す。`V5`: プレビューの表（固定の鍵の1行、本文は `BINARY LARGE OBJECT`）と適用の履歴の表（本文は `BINARY LARGE OBJECT`、追加の順の列）。`V6`: 監査の表（既存の `audit_events`）に、操作した人・DSL の識別・出どころ・理由の種類の列を足す（NULL を許す列で、1つ前の版のアプリも動く）
- **保存量**: プレビュー1件と履歴 20 件がすべて 10MB なら最大約 210MB。H2 のファイルはボリューム `mastersmith-data` に置く（既存）。大きさは Build and Test で測る
- **設定**（`application.yaml` に環境変数の参照、`.env.example` に空の値で項目名）: `MASTERSMITH_DSL_MAX_SUBMIT_SIZE`（既定 10MB）、`MASTERSMITH_DSL_HISTORY_LIMIT`（既定 20）、対象DB の `MASTERSMITH_TARGET_DB_*`（U1）。秘密情報は `.env` だけ
- **本文の上限の Filter の置き場**: `SecurityConfig` で `RequestSizeLimitFilter` を認証・認可の後に移し、道ごとの上限と code の表を足す（U4 の NFR 設計の決定 A）。既存のテスト3件はテスト用の決まりの下で 413 のままで、直さない（決定 C）
- **コンテナのメモリ**: 既定の上限 1g（ヒープ 75%）のまま。10MB の DSL を処理するときのヒープの最大を Build and Test で測り、足りなければ上限の見直しを依頼者に諮る
- **監視**: 既存のダッシュボード（`docker/monitoring/dashboards/mastersmith-overview.json`）に DSL の操作の行（パネルのまとまり）を足す（操作ごとの件数、結果ごとの件数、時間の 95 パーセンタイル）。警報は足さない。指標の名前とタグは起動して確かめてから式を書き、書いた後にすべての式を実行する
- **検査の流れ**: 既存の `./gradlew verify` のまま。U4 の結合テスト（適用のトランザクション、同時の適用、`DSL_BUSY`、10MB の境界と 413 の監査、起動時の読み込み、401・403・200）は `integrationTest` の段で動く。一括のアクセス制御のテスト（U5 の BR8.1）は U5 の Bolt で書く
- **成果物**: infrastructure-specification.md・monitoring-design.md・cicd-pipeline.md・traceability.json

## Consolidated Summary Confirmation

- 上の「設計の要点（案）」のとおりに、U4 の基盤設計の文書を作る

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
