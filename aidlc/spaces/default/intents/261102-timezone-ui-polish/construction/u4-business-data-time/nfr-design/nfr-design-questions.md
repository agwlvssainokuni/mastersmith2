# NFR Design の質問（U4 u4-business-data-time）

対象の単位: U4 `u4-business-data-time`（種類 ui）。出典は NFR 要件（`construction/u4-business-data-time/nfr-requirements/` の性能・セキュリティ・技術の選択、レビュー `reviews/review-01.md`）、機能設計（`construction/u4-business-data-time/functional-design/functional-spec.md`・`frontend-components.md`、レビュー `reviews/review-01.md`）、契約（`inception/contract-design/contract-summary.md`）、make-you-chic-ui の部品のソース（`vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/FormField/`・`RadioGroup/`・`TextInput/`。読み取りだけ）、既存の E2E（`frontend/e2e/180`・`185`・`support/businessDataSchema.sql`・`support/businessDataDsl.ts`）。

## 部品のソースで確かめた事実（設計の前提）

- `FormField` は中の入力1つに、`id`・`aria-describedby`・`aria-invalid` を文脈で渡す作り。中に `TextInput` と `RadioGroup` を2つ入れると、同じ `id` が2つの部品に付く。案内（`helperText`）は誤り（`error`）が無いときだけ出て、誤りの文は `role="alert"` で出る。
- `RadioGroup` は `legend` を渡すと fieldset と legend で描き、`FormField` の中なら `aria-describedby`・`aria-invalid` を受ける。`RadioGroup` 自身は `aria-describedby` を props で受けない。今のフォームのラジオ（`FieldInput`）は、`FormField` のラベルと、見えない legend を組にしている。

## Q1. [デザイン] 表示のタイムゾーンの読み込みが失敗したとき、時点を表す型の欄を持つ作成・更新のフォームをどうするか（機能設計のレビューの Major）

文脈: 機能設計の D11 は「失敗のときもフォームを出し、時点を表す型の欄だけ使えない状態にする」、D12 は「フォームを開いたときの表示のタイムゾーンで初めの値と変換を固定する」としています。失敗のまま開いたフォームには固定するタイムゾーンが無く、更新の初めの値（D16）と「変えていない欄を送らない」の比べ（D35）が決まりません（機能設計のレビューの Major。承認の場で直す予定の点ですが、この段の部品の作りが変わるため、ここで決めます）。読み込みの失敗はまれで、帯の「再読み込み」で回復します。

**推奨: A。** 理由: 初めの値と送る値が必ず1つのタイムゾーンで決まり、D12・D16・D35 がそのまま成り立ちます。失敗の間に途中まで入れた値が、回復の後に別の意味になることもありません。B は、ほかの欄だけ先に直せる代わりに、時刻の欄を元の文字で出して送らない特別な扱いが増え、回復した後も、そのフォームでは時刻の欄を使えないままになります（開き直しが要る）。

- A. 時点を表す型の欄を持つフォームは、失敗の間は開かず、今の読み込み中の表示の位置に失敗の文と「再読み込み」の帯を出す。再読み込みが成功したら、そのときの表示のタイムゾーンで固定してフォームを開く。時点を表す型の欄を持たないフォームは今のまま開く。
- B. フォームは開き、時点を表す型の欄は使えない状態にする。更新の初めの値は元の文字のまま出して送らない。再読み込みが成功しても、開き直すまで時刻の欄は使えない。
- X. Other (please specify)

[Answer]: A

## Q2. 設計の要点の確認

Q1 のほかに、この段で新しく決める論点は無いため、設計の要点を確かめてもらいます（`project.md` の決まり）。

1. **2択の置き場**: 重なる時刻の2択は、時刻の欄の `FormField` の外に、もう1つの `FormField`（ラベルは「{名前} では、この時刻は2回あります。どちらですか」、誤りは「どちらの時刻かを選んでください」）と、見えない legend の `RadioGroup` で置く（今のフォームのラジオと同じ組み方）。保存・検索の押下で未選択のときは、2択の最初の選択肢へフォーカスを移す（legend へは移せないため。読み上げは legend とラベルを読む）。interaction-spec.md の「legend へフォーカス」との差として記録する。
2. **誤りのときの案内**: `FormField` は誤りの間は案内を出さないため、存在しない時刻・重なる時刻の文にタイムゾーンの名前を入れる（今の文言のまま）。読めない値の誤りの文に書式を入れる。
3. **整形の回数**: 一覧の時刻はセルごとに描くたびに `formatInstant` を1回通し、覚えておく仕組み（メモ）は足さない（1 万個でも U1 の目標の 500 ミリ秒の中）。`loading`・`error` では呼ばない。
4. **今の時刻**: 案内の今の時差に使う今の時刻は、画面を開いたときに1回だけ取り、テストでは差し替えられる形（props か注入の関数）にする。
5. **サーバー**: `ReferenceField` に `labelValueType` を持たせ、`ReferencePolicy` が参照先の表のビューのラベルの列（`view.column(labelColumn)` の `valueType`。ピッカーの列の型と同じ材料）から決める。`AVAILABLE` 以外は持たない。`ReferenceFieldResponse` は `NON_NULL` で出し、`toString` に型を出してよい（値・ラベルは今のとおり出さない）。境界テスト・SpotBugs の除外・SQL は変えない。
6. **E2E**: 新しい流れのファイルは `200-business-data-time-flow.e2e.ts`。日時の表 `e2e_event`（主キー・名前・時点を表す型の6桁のカラム）を、E2E の実行のスキーマの初期化（`support/businessDataSchema.sql`）に足す。DSL は共有の `businessDataDsl.ts` を変えず、この流れの専用の DSL（版の印 `E2ET_`）を作って投入・適用する（180・190・195 の DSL と見本に影響しない。180 より後のファイルは適用中の DSL に頼らない今の決まりのまま）。利用者・ロール（`e2e_event` に FULL）・割り当ては流れが自分で作る。185 の axe の状態の追加は、API の差し替えと型を付けた見本で作る。
7. **NFR 要件のレビューの Minor**: カバレッジの下限（行 80%・分岐 70%）と `features/tables` の値の記録は、この段の `performance-design.md` の確かめの表に入れる。追跡の Deferred の分け方は、承認の場で直す。

- Looks correct
- Request changes

[Answer]: Looks correct

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- NFR 要件: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/nfr-requirements/`（`performance-requirements.md`・`security-requirements.md`・`tech-stack-decisions.md`・`reviews/review-01.md`）
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/functional-design/functional-spec.md`・`frontend-components.md`・`reviews/review-01.md`
- 契約: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/contract-design/contract-summary.md`
- 部品のソース（読み取りだけ）: `vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/FormField/FormField.tsx`・`useFieldProps.ts`・`RadioGroup/RadioGroup.tsx`
- 今のコード（読み取りだけ）: `frontend/e2e/180-business-data-flow.e2e.ts`・`185-business-data-accessibility.e2e.ts`・`support/businessDataSchema.sql`・`support/businessDataDsl.ts`、`backend/src/main/java/cherry/mastersmith/businessdata/domain/ReferencePolicy.java`

## Assumptions & Open Questions

- [assumption] E2E の DSL を流れの専用にしても、DSL の投入と適用の画面・API は今の 180 と同じ手順で使える。
