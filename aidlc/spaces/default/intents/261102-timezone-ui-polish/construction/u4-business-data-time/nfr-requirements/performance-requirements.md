# 性能の要件 — u4-business-data-time

出典: 要件（`inception/requirements-analysis/requirements.md` の NFR2.1）、機能設計（`construction/u4-business-data-time/functional-design/functional-spec.md` の D6・D10・D11・D17・D18・D30・D42、`frontend-components.md`）、契約（`inception/contract-design/contract-summary.md` の C1・C4）、U1 の性能の目標（`frontend/src/shared/format/timeZone/conversionPerformance.test.ts` の 1 万個で 500 ミリ秒・平均 0.05 ミリ秒）、既存の負荷の試験の場面（`perf/k6/scenarios.js` の `bizDefinition` などの業務データの場面）、コード知識ベースの技術の構成（`codekb/mastersmith2/technology-stack.md`）、質問と答え（`nfr-requirements-questions.md`: Q1: A）。

U4 は、業務データの画面の時刻の表示・入力・検索を表示のタイムゾーンで行い、定義の応答に `labelValueType` を1項目足す単位。日時の変換は画面の側だけで、サーバーの読み書き・SQL・問い合わせは変えない（`functional-spec.md` D29・D30）。そのため、性能の要件は「サーバーの問い合わせと目標を変えない」「画面の変換の回数と費用を抑える」に絞る。

## 番号の付け方

inception の NFR と同じ番号は同じ意味でだけ使う。NFR2.1（日時の変換で問い合わせを増やさず、性能の目標を変えない）を、この単位では NFR2.1.1〜NFR2.1.5 に細かくする。NFR2.2（ホームの API）はこの単位に当たらない（`security-requirements.md` の「この単位に当たらない inception の NFR」）。

## 要件の一覧

| ID | 要件 | 根拠・決まり | 確かめ方 | 置き場 |
|---|---|---|---|---|
| NFR2.1.1 | 業務データの API（定義・一覧・詳細・作成・更新・削除・参照の選択肢・ピッカー・ラベルの1件読み）の1要求が使う対象DB・内部DB の問い合わせと接続の数を、今と同じにする。定義の応答の `labelValueType` は、参照の項目を作るときにすでに持っている参照先の表の DSL のカラム（ピッカーの列の型と同じ材料）から決め、対象DB のメタデータ・内部DB を新しく読まない | NFR2.1・D30・D42 | 既存の結合テスト（`*ConnectionUsageIT`・問い合わせの数を数えるテスト）が、変更なしで通ること。`labelValueType` を足した定義の応答でも、接続の数と文の数が同じであることを、参照の項目を持つ定義の場面で確かめる | テスト |
| NFR2.1.2 | 業務データの API の応答時間の目標は変えない・緩めない（既存の k6 の業務データの場面の p95 と `checks` の率 1）。定義の応答の大きさの増加は、参照の項目1つあたり `"labelValueType":"DATETIME_TZ"` 程度（30 バイト未満）で、選択肢・ピッカーの情報を持たない参照の項目には足さない。新しい k6 の場面は足さない | NFR2.1・AC5.3.3・`perf/README.md` | 既存の場面（`bizDefinition`・一覧・詳細・参照の選択肢・ピッカーの場面）を、使い捨ての環境で新しい版で流し、今の目標を満たすこと | **Deferred（Build and Test）** |
| NFR2.1.3 | 一覧・詳細・行のラベル・ピッカーの結果の時刻の整形は、1回の描画で、表示する時点を表す型の値1つにつき U1 の `formatInstant` を高々1回だけ通す。表示のタイムゾーンが `loading`・`error` のときは整形しない（0回）。一覧の1ページの最大（100 行 × 100 列がすべて時点を表す型 = 1 万個）でも、U1 の目標（1 万個で 500 ミリ秒以内、1つあたり平均 0.05 ミリ秒以内）の中に入る | NFR2.1・D6・D10・D11・U1 の NFR2.1.1・NFR2.1.2 | 画面の単体テスト: 整形の呼び出し回数を数え、`loading`・`error` で 0 回、`ready` で時点の値の数以下。U1 の性能の測り（`conversionPerformance.test.ts`）が今の目標のまま通ること | テスト |
| NFR2.1.4 | 入力の変換（`parseLocalDateTime`）は、欄を離れたとき・保存の押下・検索の押下だけで行い、キーを押すたびには行わない。作成・更新のフォームの初めの値の変換は、フォームを開いたときに1回だけ（開いている間に作り直さない。D12） | D12・D18・interaction-spec.md の 3 節 | 画面の単体テスト: 文字を1文字ずつ入れても変換の呼び出しが増えず、欄を離れたときに1回。フォームの再描画で初めの値の変換の回数が増えない | テスト |
| NFR2.1.5 | 検索の画面の時点を表す型の条件の変換は、検索の押下1回につき条件1つあたり1回（範囲は始まりと終わりの2回）。検索の条件の時点は送る前に1回だけ作り、一覧の再描画・ページ送りで作り直さない（ページ送りは前の条件のまま送る） | D23・D27 | 画面の単体テスト: 検索の押下とページ送りで、変換の呼び出しの回数が条件の数を超えない | テスト |

## 目標の置き場の確認

| 目標 | 測り方 | 測る段 | 状態 |
|---|---|---|---|
| 業務データの API の p95 と `checks` の率 1（NFR2.1.2） | 既存の k6 の業務データの場面を、使い捨ての環境で新しい版で流す | Build and Test | Deferred（未測定） |
| 整形の回数と U1 の目標（NFR2.1.3） | 画面の単体テストと U1 の性能の測り | Code Generation | テスト |
| 入力・検索の変換の回数（NFR2.1.4・NFR2.1.5） | 画面の単体テスト | Code Generation | テスト |
| 問い合わせ・接続の数（NFR2.1.1） | 既存の結合テストと、参照の項目を持つ定義の場面 | Code Generation | テスト |

実際のブラウザの描画の時間（一覧・フォームを開くまで）は、E2E の画面の測り（`185-business-data-accessibility` が今記録している画面の時間）と同じ形で記録するだけにし、目標にしない（jsdom・使い捨ての環境とブラウザの差が大きいため）。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 要件: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/requirements-analysis/requirements.md`（NFR2.1）
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/functional-design/functional-spec.md`・`frontend-components.md`
- 契約: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/contract-design/contract-summary.md`（C1・C4）
- コード知識ベース: `aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`
- 質問と答え: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/nfr-requirements/nfr-requirements-questions.md`（[Q1] A）
- 今のコード（読み取りだけ）: `perf/k6/scenarios.js`・`frontend/src/shared/format/timeZone/conversionPerformance.test.ts`・`frontend/e2e/185-business-data-accessibility.e2e.ts`

## Assumptions & Open Questions

- [assumption] 一覧の1ページの最大（100 行 × 100 列）は、既存の k6 の `bizDefinition` の幅の広い表（一覧の列 100）に合わせた上限の見積もり。実際の DSL で時点を表す型が 100 列になることは少ない。
- [assumption] 問い合わせの数を数える既存のテストの名前と、参照の項目を持つ定義の場面の有無は、コード生成の計画で確かめる。
