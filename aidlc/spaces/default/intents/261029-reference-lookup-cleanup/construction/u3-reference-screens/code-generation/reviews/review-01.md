## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-10-31T04:01:10Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | frontend/src/features/tables/references/ReferenceSelectField.tsx L60-61・L94-96・frontend/src/features/tables/BusinessDataFormPage.tsx L341 と frontend/src/features/tables/references/referenceField.ts L87-89 | 「必須」の判定が2通りある。担当が決めた細かい点の `!nullable \|\| required`（isRequiredColumn）は、入力不可の必須の判定・LOOKUP の「クリア」の出し分け・0 件の説明文だけに使われている。ReferenceSelectField の必須の印・aria-required・「（選ばない）」の有無と、ブラー時の検証（`column.required === true`）は `required` だけを見る。NULL 不可で required が偽の REFERENCE カラムでは、選択肢に「（選ばない）」が出て必須の印も付かず、空のまま保存して初めてサーバーの 400 REQUIRED で止まる。LOOKUP でも必須の印と aria-required が無い（LookupField.tsx L86-90 の label は name のまま） | 必須の判定を isRequiredColumn に一本化し、ReferenceSelectField の必須の印・aria-required・「（選ばない）」の出し分け、LookupField の必須の印、validateOnBlur と画面の検証をそろえる。そろえない場合は、`required` だけを必須とする設計の根拠を code-summary.md 5.2 に書く | New |
| R-02 | Minor | frontend/src/features/tables/references/LookupField.tsx L86-101 | FormField の直下の子が `<div className="tables-lookup">` で、FormField が子に渡す id・aria-describedby・aria-invalid が欄（TextInput）に届くかを、この読み取りの範囲（vendor の FormField のソースの場所を特定できず未確認）では確かめられなかった。コメントは「欄だけが受ける」と書くが、実装は div を挟む | FormField のソースで、結び付けが直下の子にだけ付くのか、入れ子の欄まで届くのかを確かめる。届かないなら、欄へ明示で id・aria-describedby・aria-invalid を渡す。LookupField のテストに、誤りと説明文の結び付けを検証する件を足す | New |
| R-03 | Minor | frontend/src/features/tables/messages.ts（`tables.reference.loading`） | 使っていない文言の鍵が日英に残る（code-summary.md 7節で本人が指摘） | 後の直しの Bolt で消す | New |
| R-04 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/code-generation/code-summary.md 3.3・3.4 | E2E 190・195 の本体、i18n の日英の網羅、バックエンドの DTO（NON_NULL の欠け・ラベル読みの page の null）との照合、180 の主張が共有 DSL の移動の後も変わらないこと、資格情報の渡し方は、この助言の1回の読み取りでは確かめきれていない（受け渡しの写し・useReferenceLabels・各部品・作成更新の画面は読み、世代ごとの閉じた状態・AbortController・閉じた世代が pump を呼ばない点、helperText に role が無く保存後だけ role="alert" の点、値・ラベルを data-testid に載せず文字として描く点、仮の `reference?: unknown` の置き換えは設計どおりと読めた） | 承認の場で、E2E 190・195 と 180 の差分、日英の鍵の漏れの有無、DTO との照合を依頼者側で確かめる。B4 の CI（711ddd0）の結果は Build and Test で試行ごとに確かめる | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| なし（読み取りだけの指示のため、テスト・ビルドは流していない） | 該当なし | code-summary.md 3節の実測（verify 成功・E2E 277 件通過）は報告値で、この助言では再実行していない |

### Summary

主要な安全面（世代管理・R-05・値のテキスト描画・helperText と role の使い分け・仮の型の置き換え）は設計どおりで、Critical はない。必須の判定が部品によって `required` と `!nullable \|\| required` に分かれている点が最も重い指摘で、承認の場で扱いを決めてほしい。
