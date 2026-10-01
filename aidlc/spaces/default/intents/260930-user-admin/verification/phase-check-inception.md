# Phase Check — Inception → Construction

**判定: 合格（PASS）** — 未解決の GAP・ORPHAN・誤った対象・抜けた上流の ID は無い。

点検日: 2026-10-01。点検した記録は、Inception の各段の `traceability.json`（User Stories・Domain Design・Units Generation）。Contract Design は要件の網羅ではなく契約を持つ段のため、`traceability.json` を持たない。

## 網羅の集計

| 段 | 上流の ID の数 | OK | Deferred | N/A | GAP | ORPHAN |
|---|---|---|---|---|---|---|
| User Stories（要件 → ストーリー） | 62（FR1〜FR8 と枝番、NFR1〜NFR11） | 58 | 4 | 0 | 0 | 0 |
| Domain Design（ストーリー → 部品） | 7（US1.1〜US5.1） | 7 | 0 | 0 | 0 | 0 |
| Units Generation（ストーリー → 単位） | 7（US1.1〜US5.1） | 7 | 0 | 0 | 0 | 0 |

どの段も、`upstream_ids` のすべてに `coverage` の行があり、`upstream_ids` に無い ID の行は無い。

## Deferred（先送り）の4件

| ID | 先送りの先 | 扱い |
|---|---|---|
| NFR5（応答時間） | nfr-requirements | 件数の想定と測り方を NFR 要件の段で決める。**持ち主の段で確かめる** |
| NFR6（接続の使い方） | nfr-requirements | 監査の記録で接続を2本使う経路を含め、同時の要求でプールが尽きないかを NFR 要件の段で見積もり、要るなら負荷の試験で確かめる（`project.md` の学び）。**持ち主の段で確かめる** |
| NFR10（スキーマの変更） | functional-design | V9 の前進のみの移行と後方互換は、B1 の完了の条件に入れた（`inception/delivery-planning/bolt-plan.md`）。列の形は U1 の機能設計で決める。**持ち主の段で確かめる** |
| NFR11（構造の決まり） | domain-design | ドメイン設計の ADR-001（新しい部品 UserAdministration が `user` と `auth` を合わせ、`user` は `auth` を知らないまま）で扱った。B1・B3 の完了の条件に、境界テストを緩めないことと `useradmin` の境界テストを入れた。**解決済み（Domain Design）** |

## 対応の連鎖

| ストーリー | 部品（Domain Design） | 単位（Units Generation） | Bolt |
|---|---|---|---|
| US1.1 | UserAdministration、UserAccount、Authentication、Paging、UserAdminUi、UiPaging | U3（主）・U2・U5 | B3（ページ送りは B2、画面は B5） |
| US2.1 | UserAdministration、UserAccount、AuditLog、UserAdminUi | U3（主）・U5 | B4（画面は B5） |
| US2.2 | AppFrame、ApiClient、AccessControl、AdminArea、DslAdminUi、InvitationUi、UserAdminUi | U4（主）・U5 | B2（利用者の管理の画面は B5） |
| US3.1 | UserAdministration、UserAccount、Authentication、AuditLog、UserAdminUi | U3（主）・U1・U5 | B4（土台は B1、画面は B5） |
| US3.2 | Authentication、UserAccount、AuditLog、Invitation | U1（主）・U3 | B1（止める操作は B4） |
| US4.1 | UserAdministration、Authentication、AuditLog、UserAdminUi | U3（主）・U5 | B4（画面は B5） |
| US5.1 | UserAdministration、UserAccount、AppFrame、UserAdminUi | U3（主）・U4・U5 | B3（自分への反映の口は B2、画面は B5） |

すべてのストーリーが、部品・単位・Bolt までたどれる。
