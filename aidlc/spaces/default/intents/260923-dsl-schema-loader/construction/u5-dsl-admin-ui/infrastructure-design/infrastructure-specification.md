# Infrastructure Specification — U5 DSL の管理画面（u5-dsl-admin-ui）

U5 が使う基盤の一覧。配備先は開発者の PC 上のコンテナだけで、クラウドの基盤は作らない（`aidlc/spaces/default/memory/project.md` の Deployment）。要点の確認は `infrastructure-design-questions.md`。

## 1. 配備

| 項目 | 値 | 備考 |
|---|---|---|
| 配備の形 | 既存のとおり、画面のビルド結果（`frontend/dist`）を実行可能 WAR に同梱し、同じコンテナで配る | 新しいサービス・新しい依存は足さない |
| ネットワーク | 画面は配信元と同じオリジンの API を呼ぶ（CORS の設定は置かない、既存） | JSON Schema も同じオリジンの静的なファイル（`/dsl/dsl-schema-v1.json`） |
| 環境 | 開発者の PC 上のコンテナだけ（既存） | 配備先が決まったら見直す |
| IaC | 作らない（既存の compose と Dockerfile だけ） | 配備先が決まるまで |

## 2. 開発時

- 画面の開発サーバー（Vite）のプロキシに `/dsl` を足す（U2 の基盤設計。今は `/api`・`/actuator`）。

## 3. 共有するもの

| 共有するもの | 持ち主 | U5 の使い方 |
|---|---|---|
| 画面の骨組み（登録の仕組み・ApiClient・i18n） | 既存（前の Intent の U1） | 機能の登録を1つ足す。ApiClient に Problem Details の本文を渡す口を足す（既存の呼び方は変えない） |
| DSL の管理の API（契約 C6） | U4 | 呼ぶだけ |
| JSON Schema の静的なファイル | U2 | リンクを置くだけ |
