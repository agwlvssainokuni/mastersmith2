# Phase Check — Inception → Construction

**判定: 合格（PASS）** — 未解決の GAP・ORPHAN・誤った対象・抜けた上流の ID は無い。

点検日: 2026-09-26。点検した記録: Inception の各段の `traceability.json`（User Stories・Domain Design・Units Generation）。Contract Design は要件の網羅ではなく契約を持つ段のため、`traceability.json` を持たない。

## 網羅の集計

| 段 | 上流の ID の数 | OK | Deferred | N/A | GAP | ORPHAN |
|---|---|---|---|---|---|---|
| User Stories（要件 → ストーリー） | 21（FR1〜FR10、NFR1〜NFR11） | 17 | 4 | 0 | 0 | 0 |
| Domain Design（ストーリー → 部品） | 7（US1.1〜US5.1） | 7 | 0 | 0 | 0 | 0 |
| Units Generation（ストーリー → 単位） | 7（US1.1〜US5.1） | 7 | 0 | 0 | 0 | 0 |

## Deferred（先送り）の4件

| ID | 先送りの先 | 扱い |
|---|---|---|
| NFR5（接続の使い方） | performance-validation | 送信の間に接続を持たないことは AC1.1.7（U3）で確かめる。同時の要求で接続プールが尽きないことの負荷の試験は、持ち主の段（Performance Validation）で確かめる。**持ち主の段で確かめる** |
| NFR6（応答時間） | nfr-requirements | 値（応答時間と SMTP の時間切れ）を NFR 要件の段で決め、測定は Performance Validation で行う。**持ち主の段で確かめる** |
| NFR9（テスト） | build-and-test | 必須テストと E2E はストーリーの受け入れ基準から追える。カバレッジの下限と既存のパッケージを下限の対象に戻す作業は、すべての Bolt の完了の条件に入れた（`inception/delivery-planning/bolt-plan.md`、B2 で `user`・`audit` を戻す）。**持ち主の段で確かめる** |
| NFR11（手元の確かめ） | infrastructure-design | 手元でメールを見る受け手は開発の道具で、B1 の完了の条件に入れた。**持ち主の段で確かめる** |

## 対応の連鎖

| ストーリー | 部品（Domain Design） | 単位（Units Generation） | Bolt |
|---|---|---|---|
| US1.1 | Invitation、Mail、InvitationUi、AuditLog | U3 | B3（画面は B5） |
| US2.1 | Invitation、InvitationUi | U3 | B3（画面は B5） |
| US2.2 | Invitation、Mail、InvitationUi、AuditLog | U3 | B3（画面は B5） |
| US3.1 | Mail、Invitation | U1 | B1 |
| US3.2 | Invitation、UserAccount、RegistrationUi、AuthUi、AppFrame、AuditLog | U3 | B3（画面は B5） |
| US4.1 | UserAccount、PreferencesUi、AppFrame、Authentication、ApiClient、InstanceAppearance | U2 | B2（画面は B5） |
| US5.1 | UserAccount、PreferencesUi、AuditLog | U2 | B2（画面は B5） |

## 整合の確かめ

- すべての要件（FR・NFR）がストーリーか持ち主の段に対応している。
- すべてのストーリーが部品と単位に対応し、単位はすべて Bolt に入っている（U8 はストーリーではなく共通の決まり CR2 を受け持ち、B4 に入る）。
- 部品の依存（`inception/domain-design/components.md`）、単位の依存（`inception/units-generation/unit-of-work-dependency.md`）、契約（`inception/contract-design/contract-summary.md` の C1〜C10）、Bolt の順（`inception/delivery-planning/bolt-plan.md`）の間に食い違いは無い。

- [ ] 依頼者の確認（承認の場で確かめる）
