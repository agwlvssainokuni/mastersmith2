# Infrastructure Design — U5 DSL の管理画面（u5-dsl-admin-ui）— 設計の要点の確認

U5 は既存の画面（React・Vite、WAR に同梱）に機能を1つ足すだけで、基盤に新しく足すものはほとんど無い。新しく依頼者に尋ねる論点は無いため、要点を案として確かめる（`aidlc/spaces/default/memory/project.md` の Way of Working）。成果物は infrastructure-specification.md・monitoring-design.md・cicd-pipeline.md・traceability.json。

## 設計の要点（案）

- **配備**: 既存のとおり、画面のビルド結果（`frontend/dist`）を WAR に同梱して同じコンテナで配る。新しい依存は足さない。開発サーバーのプロキシに `/dsl`（JSON Schema）を足す（U2 の基盤設計）
- **監視**: 画面の側の指標は足さない。画面の操作の結果は、U4 の指標（`mastersmith.dsl.operation`）と既存の HTTP の指標で見る
- **検査の流れ**: 既存の `./gradlew verify` の中の画面の検査（Prettier・oxlint・ESLint・Stylelint・型検査・Vitest とカバレッジの下限・vitest-axe）のまま。U5 の画面部品のテストと、ApiClient の拡張のテストを足す
- **一括のアクセス制御のテスト**: U5 の Bolt で、バックエンドの結合テスト `DslAccessControlIT`（C6 の 11 本の API の 401・403・200）を `integrationTest` の段に足す（U5 の機能設計の BR8.1a）
- **画面の時間の測定**: E2E（Playwright、既存の `./gradlew e2eTest`、`verify` の外）で、画面を開いてから表示まで（照合を除く 3 秒・照合を含む 11 秒）、違いの表の行を開くまで（0.5 秒）、10MB のファイルの読み込み（2 秒）を Build and Test で測って記録する（統合の関門にしない）。E2E の代表的な流れに「DSL の管理画面を開く」を足すかは Build and Test で決める
- **成果物**: infrastructure-specification.md・monitoring-design.md・cicd-pipeline.md・traceability.json

## Consolidated Summary Confirmation

- 上の「設計の要点（案）」のとおりに、U5 の基盤設計の文書を作る

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
