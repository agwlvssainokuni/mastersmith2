## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Iteration:** 1
**Verdict Dictated:** No
**Date:** 2026-10-31

### Prior findings

| ID | Now | Severity | Note |
|---|---|---|---|
| U3 R-01 | Resolved | Major | formValidation.ts の isRequiredInput が参照の項目だけ isRequiredColumn（nullable が false か required が真）で見て、参照でない項目は今までどおり required だけで見る。validateValue・validateForm・BusinessDataFormPage の validateOnBlur が同じ関数を使い、判定がそろった。ReferenceSelectField・LookupField（clearable の逆を必須の印と aria-required に使う）・ReferenceColumnField（clearable に !isRequiredColumn を渡す）も同じ基準。参照でない項目の必須の振る舞いは変わっていない。 |
| U3 R-02 | Resolved | Major | LookupField は FormField の文脈で結ぶ id・aria-describedby・aria-invalid を欄の TextInput だけが受け、ボタンは受けない作りに直した（aria-label は見える文字で始まる）。FormField の文脈を TextInput が実際に読むかは、vendor 側のソースを読めず、コードのコメントと変更後のテストの記録に頼った。axe の規則を外す変更は見当たらない。 |
| U2 R-01 | Resolved | Major | ReferenceLookup.writeFailureOf が、写せない失敗（INVALID_VALUE・UNEXPECTED）を、SQLState・業者の番号・例外のクラスの名前だけを持つ BusinessDataUnexpectedException.withDiagnosis にして運ぶ。BusinessDataWriteService は借りた接続の中の例外（StageUnexpected・BusinessDataUnexpectedException）を値を含まない StepAttempt.Unexpected にして口の外へ出し、口の外で warnReference を1行出してから例外を投げる。WARN のキーは purpose・stage・reason・sqlState・vendorCode・exceptionClass・budgetExceeded だけで、行の値・名前・資格情報・例外のメッセージは入らない。例外はメッセージも原因も持たず、toString はクラスの名前だけ。読み取りの API の経路（failureOf）は変わっていない。 |
| U2 R-03 | Resolved | Major | make-biz-seed.sh の末尾に、biz_refcreate が 0 行でなければ止める確かめを足した。PostgreSQL は DO ブロックの RAISE（ON_ERROR_STOP で終了）、MySQL・MariaDB は 0 行でなければ副問い合わせが2行を返す形で 1242 の誤りにして止める（0 行なら IS NULL で通る）。誤りの文に行の値も数も出ない。台本は毎回スキーマを作り直すため今は必ず通り、守りとして働く。 |
| U4 R-04 | Resolved | Minor | 記録（traceability.json の AC4.1.4）は perf/README.md の --scan の説明に、終了コード 2 のときの対処を足したとする。私が読んだ README の範囲（1〜720 行・860〜1110 行）には該当の文が無く、該当の節は読めていない。道具は変えていない。承認の場で README の該当行を確かめてよい。 |
| U5 R-01 | Resolved | Major | 「今の基準の一覧」に k6 の表と k6 の外の表があり、k6 の外の表には dslMixed の 0.95・0.9・anon 85%、対象DB の 92%・OOMKilled・oom_kill、プールの待ちの線が入っている。私が読んだ範囲で、本文は一覧を参照する書き方で、一覧の外に古い線を合否の文として残した箇所は見つからなかった。ただし README の 720〜860 行と 1110 行以降は読めていない。 |
| U5 R-02 | Resolved | Major | 前の線の合否の文（履歴 H70〜H73）と前の値 2304m の計算を履歴へ移した。本文の合否の文は「今の基準の一覧の線」と履歴の番号で参照する形になった（915・999 行）。999 行は H74 を参照している。 |
| U5 R-05 | Resolved | Minor | bizSearchWide・bizCreateLarge の線 0.9 の行に「95% 未満に緩めるかは未決で、Build and Test で要件として決める。履歴 H69」と書いてあり、依頼者の決定（Step 30 = B）と合う。 |
| U5 R-03 | Resolved | Major | traceability.json の AC4.2.2・AC4.2.4・NFR8.84 に、作り直しなしの読み替えを依頼者が承認の場で受け入れたこと、結果の照合を今のコンテナに当てたこと、救済の WARN は未計測であることを書いた。 |

### New findings

| Severity | Location | Finding | Required action |
|---|---|---|---|
| Minor | backend/src/main/java/cherry/mastersmith/businessdata/service/BusinessDataWriteService.java > referenceStep の StepAttempt.Unexpected（BusinessDataUnexpectedException を捕まえる側） | 診断のない BusinessDataUnexpectedException（引数なしで作ったもの）を捕まえると、stage が null の WARN が出て、起きた段が分からない。valueOf の欠けのように atStage を通らない投げ方が残るため。ログに値は入らず、害は診断の弱さだけ。 | Build and Test の前後で気づいたときに、段の分からない WARN が出る経路（ReferenceLookup.valueOf など）に atStage を通すか、診断に段を足すかを検討する。今回は必須ではない。 |
| Minor | perf/README.md > 720〜860 行と 1110 行以降 | 今回の確かめでは読めていない範囲に、古い線の合否の文が残っていないことと U4 R-04 の文の有無を確かめていない。依頼者のメモの「本文に古い線が無いか」は、読んだ範囲では無い。 | 承認の場の前に、README の本文を線の値（0.95・0.9・85%・92%・20 ms）で検索して一覧の外の合否の文が無いことと、--scan の終了コード 2 の説明を確かめる。 |

### Summary

直しの Bolt B7 は、U3 の必須の判定の統一と LookupField の結び付き、U2 の診断の WARN（値を含まない手がかりだけ）、種の確かめ、README の基準の一覧と履歴への移動、U5 の読み替えの記録のどれも、読んだ範囲で実在し正しい。参照でない項目の必須の振る舞いは変わらず、ログに行の値・名前・資格情報は入らず、axe の規則を外す変更も無い。README の未読の範囲と vendor の TextInput は私は確かめきれておらず、Minor 2件として承認の場に残す。

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | frontend/src/features/tables/references/ReferenceSelectField.tsx L60-61・L94-96・frontend/src/features/tables/BusinessDataFormPage.tsx L341 と frontend/src/features/tables/references/referenceField.ts L87-89 | 「必須」の判定が2通りある。担当が決めた細かい点の `!nullable \|\| required`（isRequiredColumn）は、入力不可の必須の判定・LOOKUP の「クリア」の出し分け・0 件の説明文だけに使われている。ReferenceSelectField の必須の印・aria-required・「（選ばない）」の有無と、ブラー時の検証（`column.required === true`）は `required` だけを見る。NULL 不可で required が偽の REFERENCE カラムでは、選択肢に「（選ばない）」が出て必須の印も付かず、空のまま保存して初めてサーバーの 400 REQUIRED で止まる。LOOKUP でも必須の印と aria-required が無い（LookupField.tsx L86-90 の label は name のまま） | 必須の判定を isRequiredColumn に一本化し、ReferenceSelectField の必須の印・aria-required・「（選ばない）」の出し分け、LookupField の必須の印、validateOnBlur と画面の検証をそろえる。そろえない場合は、`required` だけを必須とする設計の根拠を code-summary.md 5.2 に書く | New |
| R-02 | Minor | frontend/src/features/tables/references/LookupField.tsx L86-101 | FormField の直下の子が `<div className="tables-lookup">` で、FormField が子に渡す id・aria-describedby・aria-invalid が欄（TextInput）に届くかを、この読み取りの範囲（vendor の FormField のソースの場所を特定できず未確認）では確かめられなかった。コメントは「欄だけが受ける」と書くが、実装は div を挟む | FormField のソースで、結び付けが直下の子にだけ付くのか、入れ子の欄まで届くのかを確かめる。届かないなら、欄へ明示で id・aria-describedby・aria-invalid を渡す。LookupField のテストに、誤りと説明文の結び付けを検証する件を足す | New |
| R-03 | Minor | frontend/src/features/tables/messages.ts（`tables.reference.loading`） | 使っていない文言の鍵が日英に残る（code-summary.md 7節で本人が指摘） | 後の直しの Bolt で消す | New |
| R-04 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/code-generation/code-summary.md 3.3・3.4 | E2E 190・195 の本体、i18n の日英の網羅、バックエンドの DTO（NON_NULL の欠け・ラベル読みの page の null）との照合、180 の主張が共有 DSL の移動の後も変わらないこと、資格情報の渡し方は、この助言の1回の読み取りでは確かめきれていない（受け渡しの写し・useReferenceLabels・各部品・作成更新の画面は読み、世代ごとの閉じた状態・AbortController・閉じた世代が pump を呼ばない点、helperText に role が無く保存後だけ role="alert" の点、値・ラベルを data-testid に載せず文字として描く点、仮の `reference?: unknown` の置き換えは設計どおりと読めた） | 承認の場で、E2E 190・195 と 180 の差分、日英の鍵の漏れの有無、DTO との照合を依頼者側で確かめる。B4 の CI（711ddd0）の結果は Build and Test で試行ごとに確かめる | New |

> R-01 Not re-checked this round

> R-02 Not re-checked this round

> R-03 Not re-checked this round

> R-04 Not re-checked this round
