# Phase Check — Inception → Construction

**判定: 合格（PASS）** — 未解決の GAP・ORPHAN・誤った対象・抜けた上流の ID は無い。

点検日: 2026-09-23。点検した記録: Inception の各段の `traceability.json`（User Stories・Domain Design・Units Generation）。Contract Design は要件の網羅ではなく契約を持つ段のため、`traceability.json` を持たない。

## 網羅の集計

| 段 | 上流の ID の数 | OK | Deferred | N/A | GAP | ORPHAN |
|---|---|---|---|---|---|---|
| User Stories（要件 → ストーリー） | 22（FR1〜FR10、NFR1〜NFR12） | 20 | 2 | 0 | 0 | 0 |
| Domain Design（ストーリー → 部品） | 16（US1.1〜US6.3） | 16 | 0 | 0 | 0 | 0 |
| Units Generation（ストーリー → 単位） | 16（US1.1〜US6.3） | 16 | 0 | 0 | 0 | 0 |

## Deferred（先送り）の2件

| ID | 先送りの先 | 扱い |
|---|---|---|
| FR1（DSL のスキーマ定義） | domain-design | ストーリーにせず前提とした（User Stories の Q4: B）。Domain Design で部品 DslDefinition（エンティティ DslModel）が受け持ち、Units Generation で単位 U2 に入った。契約は `inception/contract-design/contract-summary.md` の C4・C8。**解決済み** |
| NFR11（カバレッジの下限） | build-and-test | ストーリーの受け入れ基準ではなく、統合前の検査（`./gradlew verify`）と Build and Test で確かめる。すべての Bolt の完了の条件に入っている（`inception/delivery-planning/bolt-plan.md`）。**持ち主の段で確かめる** |

## 対応の連鎖

| ストーリー | 部品（Domain Design） | 単位（Units Generation） |
|---|---|---|
| US1.1 | DefaultDslGeneration、TargetDatabase、DslLifecycle | U4 |
| US1.2 | TargetDatabase、DefaultDslGeneration | U3 |
| US2.1 | DslLifecycle、DslDefinition、DslAdminUi | U4 |
| US2.2 | DslDefinition、DslAdminUi、ApiClient | U2 |
| US2.3 | DslDefinition | U2 |
| US3.1 | DslPreviewAnalysis、DslPreview | U4 |
| US3.2 | DslPreviewAnalysis、TargetDatabase | U4 |
| US3.3 | DslLifecycle、DslPreview | U4 |
| US3.4 | DslLifecycle、DslPreview | U4 |
| US4.1 | DslLifecycle、DslAppliedRevision、ActiveDslModel、DslModel | U4 |
| US4.2 | DslLifecycle、DslPreview | U4 |
| US5.1 | DslLifecycle、DslAppliedRevision | U4 |
| US5.2 | DslLifecycle、DslAppliedRevision | U4 |
| US6.1 | TargetDatabase | U1 |
| US6.2 | AccessControl、DslAdminUi | U5 |
| US6.3 | AuditLog、AuditEvent | U4 |

## 食い違いの点検

- 単位の依存の図（`inception/units-generation/unit-of-work-dependency.md`）と、部品の依存（`inception/domain-design/components.md`）は一致し、循環は無い。
- Bolt の順序（`inception/delivery-planning/bolt-plan.md`）は、依存の順を破らない。
- 契約（C1〜C8）は、単位の間の依存の6本と、画面・監査・後続の Intent へのつなぎ目を過不足なく覆う。

## 引き継ぐ注意

- Units Generation のレビューの指摘 R-01（U1 の種別が library のため、NFR・インフラの設計から外れるおそれ）は、受け入れたリスクとして記録されている。Construction の NFR 要件・NFR 設計・インフラの設計では、U1 の秘密情報・SQL インジェクション・可用性・実際の DB での試験（NFR4・NFR6・NFR7・NFR12）を対象に含める。
- 気がかりな2点（ADR-008・ADR-006）は、NFR 要件の段で小さく試して確かめる（`inception/delivery-planning/risk-and-sequencing-rationale.md`）。

- [ ] 依頼者の承認（Delivery Planning の承認をもって確かめる）
