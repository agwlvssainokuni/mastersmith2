# 技術の選択 — U3 u3-reference-screens

出典: 機能設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/functional-design/functional-spec.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/functional-design/frontend-components.md`、要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`（NFR9）。U3 は種類 `ui` の単位で `rules.md` を持たない。

U3 は新しい依存を足さない（要件 NFR9、`security-requirements.md` の NFR9.41）。決めるのは、今の道具のどれを使うかと、入れないものだけ。

## 上流の ID との対応

| 上流 | この文書で扱うこと | 要件の ID |
|---|---|---|
| NFR2 性能 | 選択肢の仮想化を入れないこと、初回の JavaScript の測り | NFR2.43・NFR2.44（`performance-requirements.md`） |
| NFR4 アクセシビリティ | make-you-chic-ui の部品の口、axe-core の版 | NFR4.41〜NFR4.44（`security-requirements.md`） |
| NFR5 テスト | テストの道具 | NFR5.41〜NFR5.43（`security-requirements.md`） |
| NFR6 多言語 | 文言の仕組み | NFR6.41（`security-requirements.md`） |
| NFR9 供給網 | 依存を足さないこと | NFR9.41（`security-requirements.md`） |

## 選択

| 道具 | 決め | 理由 |
|---|---|---|
| React・TypeScript・react-router | 今の版のまま（`frontend/package.json` の React 19.3 系） | 部品と画面の状態は今の骨組みで作れる |
| i18next・react-i18next | 今の版のまま（26.5 系・17.1 系）。補間の `escapeValue` を切らない | 日本語・英語の文言は既存の `messages.ts` に足すだけで済む（NFR6.41）。値は文字として描く（NFR1.41） |
| make-you-chic-ui | 固定先 `4c63d11` の `Modal`・`Table`・`FormField`・`TextInput`・`Select`・`Alert`・`Button` を使う。新しい依頼は出さない | 部品の口はソースで確かめ済み（機能設計の 11節）。足りない点（`Table` の件数・ページ送りを押せなくする口、横のスクロールの枠のフォーカスと名前、`FormField` の誤りの `role`）は frontend の側の手当て（件数を言わない文の差し替え・押下を捨てる・`frontend/src/shared/scroll-region`・NFR 設計の決め）で済む |
| ピッカーのダイアログ | 自前の部品 `LookupPickerDialog`（`Modal`＋`Table` の組み合わせ）を `features/tables` に置く。別のライブラリのコンボボックス・検索ダイアログは入れない | 新しい依存を足さない（NFR9.41）。キーボードと `aria` の振る舞いは画面の部品のテストで確かめる（`team.md`: ピッカーが make-you-chic-ui の部品でないとき） |
| 選択肢の仮想化 | 入れない。最大 200 件をネイティブの `select`・`radio` でそのまま描く | 件数の上限が 200 で小さい。`radio` の 200 件の確かめは NFR 設計（NFR2.43） |
| Vitest・Testing Library・user-event・vitest-axe・fast-check | 今の版のまま（Vitest 5.0 系・vitest-axe 0.1 系・fast-check 4.10 系） | 画面の部品のテストと性質ベースのテストは今の道具で書ける（NFR5.41・NFR5.42） |
| Playwright・axe-core | 今の版のまま（Playwright 1.64 系・axe-core 4.14 系）。axe の規則と要素を外さない | E2E 190・195 は既存の仕組み（`displayCombos.ts`・`overflow.ts`・報告の部品）で書ける（NFR4.44・NFR5.43）。axe-core の版が lockfile で1つにそろっていることを確かめる（`team.md`） |
| 初回の JavaScript の測り | 既存の `frontend/scripts/check-bundle-size.mjs`（gzip の合計の目安 500 KB、超えたら警告）をそのまま使う | 業務データの画面は `lazy` で登録済みで、参照の部品は入口に入らない（NFR2.44） |

- 新しい依存を足すことになったときは、採用の前にライセンスを確かめ、Apache License 2.0 と異なるなら理由を設計の記録に残す（`team.md`）。
- npm は `frontend/.npmrc` の `ignore-scripts=true` のままにし、lockfile どおりに入れる（`project.md` の Mandated）。

## 選ばなかった案

| 案 | 選ばなかった理由 |
|---|---|
| 検索つきのコンボボックスのライブラリ（例: 汎用の自動補完の部品）を入れる | 新しい依存とライセンスの確かめが要り、make-you-chic-ui の見た目とテーマ・ブランドカラーの組を外れる。`Modal`＋`Table` で要件を満たせる |
| make-you-chic-ui にピッカーの部品を依頼する | 機能設計の 11節で、今の口で手当てが済むと確かめた。依頼の待ちで Bolt が止まるのを避ける |
| Playwright で画面の側の時間を測る | Q1: A。値が手元の PC の負荷で揺れ、合否の回を複数回流す手間に見合わない（`performance-requirements.md` の NFR2.41） |

## Sources

- 質問と答え: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/nfr-requirements/nfr-requirements-questions.md`（Q1: A と「決まっていること」）
- 要件: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`（NFR9）
- 機能設計: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/functional-design/functional-spec.md`（11節）、`frontend-components.md`（1節・7節）
- 画面イメージ: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/refined-mockups/design-system-mapping.md`
- 書き方の手本: `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/nfr-requirements/tech-stack-decisions.md`
- コード: `frontend/package.json`、`frontend/scripts/check-bundle-size.mjs`、`frontend/playwright.config.ts`、`frontend/src/features/tables/registration.ts`（`lazy`）
- 決まり: `aidlc/spaces/default/memory/team.md`（Code Style・Testing Posture）、`aidlc/spaces/default/memory/project.md`（Mandated・Forbidden）

## Assumptions & Open Questions

- [assumption] 版の数は `frontend/package.json` の今の値（範囲の指定）から読んだ。Code Generation で lockfile の実際の版を確かめる。
- 未決の点は無い（NFR 設計に回す論点は `performance-requirements.md` と `security-requirements.md` の NFR4.45 に書いた）。
