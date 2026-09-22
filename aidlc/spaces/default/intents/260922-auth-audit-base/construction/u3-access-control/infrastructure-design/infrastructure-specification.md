# Infrastructure Specification — U3 管理画面のアクセス制御（u3-access-control）

U3 の基盤の設計。入力は U3 の NFR Design（`performance-design.md`・`security-design.md`・`scalability-design.md`・`reliability-design.md`・`observability-design.md`・`logical-components.md`）、Domain Design の部品の一覧（`components.md`。AccessControl と AdminArea）、U3 の `functional-spec.md` である。`contract-summary.md` は、本ワークフローで Contract Design を行わないため無い。設計の方針の確認は `infrastructure-design-questions.md`（要約の確認）にある。

U3 は、U1 の基盤（U1 の `infrastructure-design/infrastructure-specification.md`）と、U2 の基盤の設計で足した設定（U2 の `infrastructure-design/infrastructure-specification.md`: タイムゾーン `Asia/Tokyo`、CPU の上限 4）の上で動く。U3 のために基盤に足す・変える点は無い。

## 1. 配備（Deployment）

| Facet | Choice | Rationale |
|---|---|---|
| 実行の形 | U1 と同じコンテナ・同じプロセス | ADR-008、U3 の `logical-components.md` 4章 |
| ネットワーク | 追加なし。管理者向けの API（`/api/admin/**`）も U1 と同じ 8080 で受け、判定はアプリの中（フィルターの連鎖）で行う。入口の装置（ロードバランサーなど）での制限は持たない | 判定をサーバー側のアプリで行う（U3 の決まり 2.6） |
| 保存 | 追加なし。U3 は表を持たない（アクセス拒否の記録は U4 の表） | Domain Design のデータの持ち主 |
| 資源の大きさ | 追加なし。判定はメモリの中の比較だけで、DB を読まない | U3 の `performance-design.md` 2章 |
| 環境 | U1 のとおり（開発者の PC 上のコンテナ、CI） | — |

## 2. 基盤のサービス（Infrastructure Services）

| Service | Role | Configuration | Notes |
|---|---|---|---|
| — | — | 使わない | U3 は独自のサービスを持たない。アクセス拒否の記録は U4 が U1 の内部DBへ書く |

## 3. 設定

| 設定 | 所有 | U3 への効き方 |
|---|---|---|
| 転送元のヘッダーを信頼するか（既定は無効） | U1 | アクセス拒否の出来事の接続元IP の取り方（U3 の `security-design.md` 4章） |
| セキュリティ関係のヘッダーの値 | U1 | 細工されたパスの拒否の 400 にも同じ値を付ける（U3 の `security-design.md` 2章） |

U3 に固有の環境変数・秘密情報は無い。

## 4. 単位で共有する基盤（Shared Infrastructure）

| Shared Resource | Owner Unit | Consumer Units | Access Boundary |
|---|---|---|---|
| Spring Security のフィルターの連鎖 | U1 | U3（管理者のみの決まり、API の既定の扱い、401・403・拒否の処理） | U1 の差し込み口（U1 の `nfr-design/security-design.md` 3章）で足すだけ。U3 は order 200 台を使う |
| 内部DB | U1 | U3 は直接使わない（U2 の AuthenticatedUser と U4 の記録を通す） | — |
