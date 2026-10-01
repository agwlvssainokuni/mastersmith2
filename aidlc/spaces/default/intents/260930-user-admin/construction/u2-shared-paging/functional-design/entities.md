# Entities — U2 ページ送りの共通化（u2-shared-paging）

出典: 単位 `aidlc/spaces/default/intents/260930-user-admin/inception/units-generation/unit-of-work.md`（U2）と `unit-of-work-story-map.md`（US1.1 の従）、部品 `aidlc/spaces/default/intents/260930-user-admin/inception/domain-design/components.md`（Paging・UiPaging）、ADR-004（`aidlc/spaces/default/intents/260930-user-admin/inception/domain-design/decisions.md`）、契約 C2・C5（`aidlc/spaces/default/intents/260930-user-admin/inception/contract-design/contract-summary.md`）、要件 FR1.3・FR1.5（`aidlc/spaces/default/intents/260930-user-admin/inception/requirements-analysis/requirements.md`）、この段の確認 `functional-design-questions.md`（設計の要点 7）。

## エンティティは 0 件

U2 が作る Paging（サーバー）と UiPaging（画面）は、保存するデータを持たない純粋な関数の集まりである。内部DB の表・列、保存する出来事、画面が持ち続ける状態のどれも作らず、変えない。

- ページの番号・一覧の中の位置・読み始めの位置・全体の件数・応答の行の数は、関数の入力と出力の値であり、その場で計算して捨てる。値の範囲と計算の仕方は `rules.md` の決まり（BR1.x・BR2.x・BR3.x）で扱う。
- 全体の件数と行は、呼び出し元（招待の Invitation、U3 の UserAdministration）が自分の表から読む。U2 はその表に触れない。
- `project.md` の Code Style の学び「機能設計のエンティティには、アプリが独自に持つデータだけを書く」に従い、保存しない値の型をエンティティとして並べない。

正本の YAML は、エンティティが無いことを明示する空のブロックとする。

```yaml
entities: []
# U2 は保存するデータを持たない（純粋な関数だけ）。
# 入力と出力の値（page・position・offset・total・itemCount）は rules.md の BR1.x・BR2.x・BR3.x で扱う。
relationships: []
```

## 要約

| 項目 | 内容 |
|---|---|
| この単位のエンティティ | なし（0 件） |
| 内部DB の変更 | なし（スキーマの変更・移行のファイルは無い） |
| 扱う値（保存しない） | ページの番号（1 以上の整数）、一覧の中の位置（1 以上）、読み始めの位置（0 以上）、全体の件数（0 以上）、応答の行の数（0 以上） |
| 値の決まりの置き場 | `rules.md`（サーバーの計算 BR1.x、画面の計算 BR2.x、呼び出し元が守る決まり BR3.x） |
