# Scalability Design — U3 管理画面のアクセス制御（u3-access-control）

U3 の拡張性の要件（`scalability-requirements.md` の NFR1.4、NFR1.5）を満たす設計。前提は Domain Design の ADR-008（当面はアプリを1台で動かす）。処理の流れは U3 の `functional-spec.md`、技術は U3 の `tech-stack-decisions.md` に従う。性能・安全・信頼性・観測性の要件（`performance-requirements.md`・`security-requirements.md`・`reliability-requirements.md`・`observability-requirements.md`）は各設計書で扱う。`contract-summary.md` は、本ワークフローで Contract Design を行わないため無い。

## 1. 状態を持たない判定（NFR1.4）

- アクセスの決まりは起動時に1回組み立てる固定の設定で、要求ごとの判定は要求の情報（パス・AuthenticatedUser）だけで行う。
- アプリのメモリに、利用者ごとの情報・拒否の回数などを持たない。
- そのため、複数台で動かす場合もそのまま動く。管理者フラグは内部DBにあり、全台で同じ値を読む。

## 2. 拒否の出来事の量（NFR1.5）

| 項目 | 見積もり |
|---|---|
| アクセス拒否の出来事 | 利用者 50 名の想定で1日あたり数十件以内 |
| 監査ログへの影響 | U1 の見積もり（1日 500 件、1年 約 180MB、U1 の `nfr-design/scalability-design.md` 1章）の内側に収まる |

- 想定を超えて拒否が増えた場合（不正なアクセスの試みなど）は、監査ログの ACCESS_DENIED の件数で気づける（`observability-design.md`）。記録の量を制限する仕組みは持たない（`security-design.md` 6章のサービス妨害の行）。
