# Inception → Construction の確かめ — role-menu

**判定: 合格（PASS）** — 未解決の GAP・ORPHAN・不正な対象・抜けた上流の ID は無い。

確かめた日: 2026-10-05。Inception で実行した段の `traceability.json` を読み、上流の ID と coverage を突き合わせた（Contract Design は要件の網羅を持たないため対象外）。

## 段ごとの網羅

| 段 | 上流の ID の数 | OK | Deferred | GAP | ORPHAN | 抜け | 余り |
|---|---|---|---|---|---|---|---|
| User Stories（`inception/user-stories/traceability.json`） | 91（FR・NFR の枝番を含む全件） | 85 | 6 | 0 | 0 | 0 | 0 |
| Domain Design（`inception/domain-design/traceability.json`） | 11（US1.1〜US6.1） | 11 | 0 | 0 | 0 | 0 | 0 |
| Units Generation（`inception/units-generation/traceability.json`） | 11（US1.1〜US6.1） | 11 | 0 | 0 | 0 | 0 | 0 |

## Deferred の持ち主（Construction へ引き継ぐ）

| ID | 内容 | 持ち主の段 |
|---|---|---|
| FR12.2 | 業務の理由で拒否した操作を監査に残すか | functional-design |
| FR12.4 | export を監査に残すか | functional-design |
| NFR2.1 | 規模の前提（ロールとグループの数の上限） | nfr-requirements |
| NFR2.3 | 要求ごとの実効の権限の読み出しの重さ | nfr-requirements |
| NFR6.3 | E2E（最大2本） | code-generation |
| NFR6.4 | カバレッジの下限（パッケージごと） | build-and-test |

## つながりの確かめ

- 要件 → ストーリー: FR・NFR の全件がストーリー（US）か持ち主の段に対応する。
- ストーリー → 部品: 11 件すべてが部品（`components.md`）に対応する。
- ストーリー → 単位: 11 件すべてに主の単位がある（`unit-of-work-story-map.md` と一致）。
- 単位 → Bolt: 7つの単位すべてが B1〜B9 のどれかに入る（`delivery-planning/bolt-plan.md`）。

## 承認

- [ ] 依頼者の確かめ（Delivery Planning の承認の場で行う）
