# エンティティ — U5 u5-carryover-tests

U5 はテストと負荷の試験の台本だけを持つ単位（種類 `library`、部品を増やさない）で、アプリが独自に持つデータを足さない・変えない。そのため、この単位ではエンティティを置かない。

- 理由1: 直す対象は既存のテスト2件（`RoleConcurrencyIT` の #7、`AdminForbiddenProvider.test.tsx` の1件）で、本番の型・表・移行は変えない（Q1: A）。#7 の入れ替えた順の場合に使う待ち合わせ（`rules.md` の BR1.5）も、テストの補助（`src/test/java` の `testsupport`）に置く部品で、アプリのデータではない。
- 理由2: `memory.peak` の測りの記録は、使い捨ての環境で `perf/` の台本が書く測りの結果で、アプリのデータではない。ページキャッシュを減らす手の試しで使う内部DB の接続先の上書き（`PERF_DB_URL`、`rules.md` の BR4.1）も、使い捨ての環境の設定で、アプリのデータではない。`project.md` の学び（機能設計のエンティティにはアプリが独自に持つデータだけを書く）に従い、エンティティにしない。
- テストが読むだけの既存のデータ（監査の行 `audit_events` の `detail` の前後の値、`permission_settings` の主権限）は、持ち主の機能（`audit`・`role`）のもので、ここでは定義しない。

```yaml
entities: []
```

## 要約

エンティティは無い。測りの記録の形（1秒ごとの TSV の列）は `functional-spec.md` の3節に、承認の場（2026-10-18 Request Changes）の決定は `functional-spec.md` の8節に書く。この書き直しで、エンティティの有無は変わらない。

## Sources

- 単位の定義: `aidlc/spaces/default/intents/261016-business-data-views/inception/units-generation/unit-of-work.md`（U5）
- 質問と答え: `aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/functional-design/functional-design-questions.md`（Q1〜Q5、Requested Changes Feedback、まとめの確認）
- 決まり: `aidlc/spaces/default/memory/project.md` の Code Style（エンティティにはアプリが独自に持つデータだけを書く）
