# Functional Design の質問（U4 u4-business-data-time）

対象の単位: U4 `u4-business-data-time`（種類 ui。業務データの一覧・詳細・参照の選択肢の時刻の表示、作成・更新・検索の入力、夏時間の誤りと2択、往復）。出典は `unit-of-work.md`・`unit-of-work-story-map.md`・`requirements.md`（FR2・FR3・FR4.2・NFR2.1・NFR3・NFR5・NFR6.1）・`stories.md`（US2.1〜US2.5・US5.2）・`contract-summary.md` の C1・C2・C4・`decisions.md` の ADR-002・ADR-003・`mockups.md` の S2〜S4・`interaction-spec.md` の 2〜4 節・U1〜U3 の機能設計（`construction/u1-time-zone-conversion/`・`u2-display-time-zone/`・`u3-target-db-time/` の `functional-spec.md`。U1〜U3 は統合済み）・既存のコード（読み取りだけ）。

## 決まっていること（質問にしない）

| 項目 | 決まっている内容 | 出どころ |
|---|---|---|
| どの型を変換するか | 時点を表す型（受け渡しの型 `DATETIME_TZ`。PostgreSQL の `timestamptz`、MySQL・MariaDB の `timestamp`）だけを表示のタイムゾーンへ直し、時差を添える。時差を持たない型（`DATETIME`・`DATE`・`TIME`）と `timetz`（`TIME_TZ`）は DB の値のまま | FR2.1〜FR2.4・AC2.2.1〜AC2.2.3・C1 の rules |
| 表示の形 | `YYYY-MM-DD HH:mm:ss[.端数] ±HH:mm`。端数は DB の値の桁のまま、丸めない。変換は U1 の `formatInstant` だけを使う | mockups.md の 2 節・AC2.1.4・FR4.2 |
| 送る形 | 時点を表す型の入力と検索は、画面が U1 の `parseLocalDateTime` で時点（UTC の時差つき）に直して送る。サーバーへ送るのは時点だけ。夏時間で存在しない時刻の拒否は画面の側だけ。重なる時刻は先・後の2択で、選んだ側の時点を送る | ADR-002・C4 の rules |
| 入力の部品と書式 | 時点を表す型の入力・検索は `TextInput`（`FormField` で包む）。書式は `YYYY-MM-DD HH:mm:ss`（端数 1〜6 桁は任意。U1 の Q1: A で厳密に1つ）。欄の下に「Asia/Tokyo (+09:00) で入力」と書式の例。検証は欄を離れたときと保存の押下 | mockups.md の S3・S4・interaction-spec.md の 3 節 |
| 案内の1行 | 一覧・詳細の見出しの下に、表示のタイムゾーンの案内の1行（設定あり／ブラウザに合わせている／解釈できない名前の3つの文）と、プリファレンスへのリンク | mockups.md の S2・interaction-spec.md の 2 節 |
| 読み込みの前 | 表示のタイムゾーンを読み込むまで、案内の1行も時刻の欄も出さず、読み込み中にする | AC1.2.5・C2 の rules |
| 往復 | 更新の画面で変えていない欄は送らない。主キーは画面の表示とは別に、サーバーが返した文字のまま送り返す（今の `RowKey` の仕組み） | AC2.5.1・AC2.5.3・C4 の rules |
| 読めない値 | サーバーは型として読めないセルを DB が送った文字のまま返す（U3 の BR3.10）。U1 の `formatInstant` は `unreadable` で元の文字を返す（U1 の Q2: A） | U3・U1 の機能設計 |
| 必須のテスト | 日時の必須の項目（3種類の対象DB と型・日付の変わり目・夏時間と端数の時差・秒の端数・範囲の端・往復・性質ベースのテスト）、axe（誤りの状態・長い名前・ブランドカラーとテーマのすべての組）、N6 の代表の E2E を1本まで | team.md の Testing Posture・NFR5・unit-of-work.md の U4 |

## コードを読んで設計で決めること（質問にしない。承認の場で覆せる）

読み取りだけで確かめた事実から、機能設計の決まり（D 番号つき）として書く点。

| 項目 | 決めること | 確かめた事実 |
|---|---|---|
| 同時更新の判定の値 | 業務データの API には同時更新の判定の値（版の列・期待する値）が無いため、往復の対象は主キー・参照の値・変えていない欄だけとする | `RowKey` と変えたカラムの値だけを送る作り。サーバーの `businessdata` に期待する値を照らす仕組みは無い |
| 時差を持たない型の時刻・`timetz` の入力 | 今のまま（`TIME`・`TIME_TZ` は文字の入力で、時差の案内を出さない） | `TIME`・`TIME_TZ` の既定の部品は `text`。案内は `DATETIME_TZ` にだけ「UTC で入れる」が出ている |
| フォームのタイムゾーン | フォームを開いたときの表示のタイムゾーンで初めの値・案内・変換を決め、開いている間は同じものを使う | 表示のタイムゾーンはタブごとの状態で、プリファレンスは別の画面（未保存の確かめが付く）。開いている間に変わる経路は通常無い |
| 表示のタイムゾーンの読み込みの失敗（U2 の `error`） | 時刻のセルは U2 と同じ短い文、時点を表す型の入力欄は使えない状態にし、画面の上に U2 の「再読み込み」の帯を置く（管理の画面と同じ） | U2 の配布は `loading`・`ready`・`error` の3つ（契約 C2 に `error` を足した形）。`DisplayTimeZoneErrorBar` は画面ごとに置く作り |
| 夏時間の存在しない時刻の文 | 「America/New_York では、この時刻はありません（夏時間の切り替えで飛ばされる時刻です）」の形にし、何時から何時に進むかは出さない | U1 の `gap` の結果は切り替わりの時刻を持たない |
| 読めない値の表示 | 一覧・詳細・ラベルでは元の文字のまま出し、更新の画面の初めの値も元の文字にする（変えなければ送らない） | 上の「読めない値」 |
| 参照のピッカーの結果の列と検索 | 結果の列は `listColumns` の型で表示を決め、時点を表す型の検索の欄も同じ変換をする | ピッカーは列ごとに `valueType` を持つ（今は「文字のまま」）。検索の欄は `datetime-local` に「UTC で入れる」 |

以下の質問は、上の決まりだけでは決まらない点に絞った（3 問）。

## Q1. [開発] 参照の選択肢の「ラベル」の列が時点を表す型のとき、表示のタイムゾーンに直すために、サーバーの応答にその列の型を足すか

文脈: 受け入れ基準 AC2.1.3 は「参照の選択肢と表示名に時点を表す型の値が含まれるとき、表示のタイムゾーンに直して出る」としています。参照の選択肢は「値」と「ラベル」の2つの文字で届きます。「値」の型は参照元のカラムの型で分かり、行の表示名（行のラベル）とピッカーの結果の列も型が分かります。しかし選択肢の「ラベル」（参照先の表示に使う列）だけは、今の応答に型が無く、画面は時点を表す型かどうかを知りません（今は UTC の `+00:00` のまま出ます）。契約 C4 は「業務データの API の形は変えない」としています。

**推奨: A。** 理由: Must の受け入れ基準 AC2.1.3 を満たせる唯一の確かな方法です。足すのは参照の項目の応答の1項目（ラベルの列の型。値は既存の型の語彙）だけで、前の版の画面は知らない項目を無視するため壊れません（ピッカーの結果の列がすでに型を持つ形と同じ）。B は変更が無い代わりに、ラベルが時点を表す型の参照で AC2.1.3 を満たせず、同じ時点が画面ごとに違って見えます。C は文字の形で決めるため、文字の列にたまたま日時の形の文字があると誤って直します。

- A. 参照の項目の応答に、ラベルの列の型（受け渡しの型）を足す（足すだけ。U4 がサーバーの `businessdata` の応答を1項目広げる。契約 C4 との差として記録する）。
- B. サーバーは変えない。ラベルは今のまま（UTC の文字）で出し、AC2.1.3 との差を記録する（値・行のラベル・ピッカーの結果の列は直す）。
- C. サーバーは変えない。ラベルの文字が時点の形（日付・時刻・時差）なら直す。
- X. Other (please specify)

[Answer]: A

## Q2. [デザイン] 時差を持たない日時（`datetime`・PostgreSQL の `timestamp`）の入力欄も、時点を表す型と同じ文字の入力にするか

文脈: 今、時差を持たない日時の作成・更新・検索の欄は、ブラウザの日時の入力（`datetime-local`）です。この入力は秒の端数を入れられず、更新の画面の初めの値も端数を落として出します（変えなければ送らないため往復では落ちませんが、その欄を直すと端数が消えます）。`team.md` は「秒の端数が読み・書き・往復で落ちない」ことを求めています。画面イメージ（S3）は時差を持たない型の欄を「今のまま（案内にタイムゾーンを出さない）」としており、部品を変えるかまでは決めていません。

**推奨: A。** 理由: 端数を落とさずに入れられ、時点を表す型の欄と同じ書式（`YYYY-MM-DD HH:mm:ss[.端数]`）・同じ検証になるため、利用者が型ごとに入れ方を覚え分けなくて済みます。タイムゾーンの変換はせず、案内にタイムゾーンを出さない点は画面イメージのとおりです。B はブラウザの日時の選択の部品が使える代わりに、端数のある列で値を直すと端数が黙って消えます。

- A. 文字の入力（`TextInput`）にする。書式と検証は時点を表す型と同じで、変換はしない。案内は書式の例だけ（タイムゾーンを出さない）。検索の欄も同じ。
- B. 今のまま（`datetime-local`）にする。端数のある列で直すと端数が落ちることを、差として記録する。
- X. Other (please specify)

[Answer]: A

## Q3. [デザイン] 時点を表す型の範囲の検索で、終わりの欄に秒の端数を入れなかったとき、その1秒の中の端数を持つ行を含めるか

文脈: 範囲の検索は両端を含みます（AC2.4.2）。秒の端数を持つ列（例: 6 桁）で、終わりに `2026-11-30 23:59:59` と入れると、サーバーは `23:59:59.000000` までを含み、`23:59:59.500` の行は外れます。画面イメージ（S4）の例は終わりを `23:59:59` としています。

**推奨: A。** 理由: 入れた時刻が、そのまま送る条件になるため、結果が予測できます。受け入れ基準 AC2.4.2 の「終わりの時点ちょうどの行を含む」も、入れた値のとおりに成り立ちます。案内の文に「端数を含めるときは .999999 のように入れる」と添えれば、取りこぼしに気づけます。B は「23:59:59 まで」の感覚に合う代わりに、送る値が入れた値と変わり、時差を持たない型や一致の検索との違いが増えます。

- A. 入れたとおりに送る（`23:59:59` は `23:59:59` ちょうどまで）。案内に端数の入れ方を添える。
- B. 終わりの欄に端数が無いときは、その秒の終わり（`.999999`）まで含めて送る。
- X. Other (please specify)

[Answer]: A

## Q4. [デザイン] 検索の条件に、夏時間で2回ある時刻（重なる時刻）を入れたとき、どちらの時点で探すか（追加の質問）

文脈: 作成・更新の画面では、重なる時刻を入れると先・後の2択を出し、選ぶまで保存させません（画面イメージ S3・interaction-spec.md の 4 節）。検索（S4）とピッカーの検索は、重なる時刻の扱いが上流でも Q1〜Q3 でも決まっていません。例: `America/New_York` で `2026-11-01 01:30:00` は 2 回あります（`-04:00` と `-05:00`）。

**推奨: A。** 理由: 範囲の検索では、始まりに入れた時刻は「その時刻の最初」、終わりに入れた時刻は「その時刻の最後」と読むのが自然で、両端を含む決まり（AC2.4.2）のまま、2回ある時刻の行を取りこぼしません。検索の画面に2択を足さずに済みます。一致の検索は1つの時点しか送れないため、作成・更新と同じ2択で選ばせます。B は作成・更新とそろう代わりに、範囲の検索のたびに2択が出て操作が増えます。C は操作が増えない代わりに、後の時点の行が黙って外れます。

- A. 範囲の検索は、始まりを先の時点、終わりを後の時点にして送る（2択を出さない。案内の文に添える）。一致の検索（検索の画面・ピッカー）は、作成・更新と同じ2択を出す。
- B. 範囲・一致のどちらも、作成・更新と同じ2択を出し、選ぶまで検索させない。
- C. 2択を出さず、いつも先の時点で送る。
- X. Other (please specify)

[Answer]: A

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 単位: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/units-generation/unit-of-work.md`・`unit-of-work-story-map.md`
- 要件・ストーリー・契約・設計の決定・画面イメージ: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/requirements-analysis/requirements.md`・`user-stories/stories.md`・`contract-design/contract-summary.md`・`domain-design/components.md`・`domain-design/decisions.md`・`refined-mockups/mockups.md`・`refined-mockups/interaction-spec.md`
- 先の単位の設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u1-time-zone-conversion/functional-design/functional-spec.md`・`u2-display-time-zone/functional-design/frontend-components.md`・`u3-target-db-time/functional-design/functional-spec.md`
- 既存のコード（読み取りだけ）: `frontend/src/features/tables/`（`valueFormat.ts`・`formDiff.ts`・`formValidation.ts`・`searchConditions.ts`・`SearchPanel.tsx`・`BusinessDataFormPage.tsx`・`rowLabel.ts`・`references/`）、`frontend/src/shared/format/timeZone/`、`frontend/src/app/display-settings/`、`backend/src/main/java/cherry/mastersmith/businessdata/web/ReferenceFieldResponse.java`
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] E2E の代表の流れ（1本）の中身は、NFR Requirements とコード生成の計画で決める。
