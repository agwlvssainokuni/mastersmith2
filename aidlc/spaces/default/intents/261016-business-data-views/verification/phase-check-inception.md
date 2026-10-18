# Inception → Construction の確かめ — business-data-views

**判定: 合格（PASS）** — 未解決の GAP・ORPHAN・不正な対象・抜けた上流の ID は無い。

確かめた日: 2026-10-18。Inception で実行した段の `traceability.json` を読み、上流の ID と coverage を突き合わせた（Contract Design は要件の網羅を持たないため対象外）。

## 段ごとの網羅

| 段 | 上流の ID の数 | OK | Deferred | GAP | ORPHAN | 抜け | 余り |
|---|---|---|---|---|---|---|---|
| User Stories（`inception/user-stories/traceability.json`） | 97（FR・NFR の群と枝番の全件） | 88 | 9 | 0 | 0 | 0 | 0 |
| Domain Design（`inception/domain-design/traceability.json`） | 17（US1.1〜US4.1） | 17 | 0 | 0 | 0 | 0 | 0 |
| Units Generation（`inception/units-generation/traceability.json`） | 17（US1.1〜US4.1） | 17 | 0 | 0 | 0 | 0 | 0 |

## Deferred の持ち主（Construction・Operation へ引き継ぐ）

| ID | 内容 | 持ち主の段 | 受け持つ単位・Bolt |
|---|---|---|---|
| FR15・FR15.1 | 不安定なテスト2件の直し | code-generation | U5・B1 |
| FR15.2 | 直したことの二段の確かめ | build-and-test | U5・B1 |
| FR16・FR16.1・FR16.2・FR16.4 | `memory.peak` の測り直し（物差しは変えない、`anon` を並べる、合格は 2g のまま 90% 未満） | build-and-test | U5 |
| FR16.3 | ページキャッシュを減らす手 | code-generation | U5・B1 |
| NFR8 | 業務データの 403 の警報のしきい値 | observability-setup | — |

## つながりの確かめ

- 要件 → ストーリー: FR・NFR の全件がストーリー（US）か持ち主の段に対応する。
- ストーリー → 部品: 17 件すべてが部品（`inception/domain-design/components.md`）に対応する。
- ストーリー → 単位: 17 件すべてに主の単位がある（`inception/units-generation/unit-of-work-story-map.md` と一致）。U5 はストーリーを持たず、Deferred の FR15・FR16 を受け持つ。
- 単位 → Bolt: U1〜U5 のすべてが Bolt（B1〜B7）に入る（`inception/delivery-planning/bolt-plan.md`）。

## 引き継ぐ未解決の点（GAP ではない）

- 承認の場で受け入れた指摘（Requirements R-09・R-10 は User Stories で解決済み。User Stories R-01〜R-05、Refined Mockups R-01〜R-06、Domain Design R-01〜R-06、Units Generation R-01〜R-05、Contract Design R-10）は、機能設計・NFR の段・Delivery Planning で拾う。Units Generation の R-01 は、この段で U4 を B3・B7 に分けて解いた。
