# 性能の設計 — u4-business-data-time

出典: NFR 要件（`construction/u4-business-data-time/nfr-requirements/performance-requirements.md` の NFR2.1.1〜NFR2.1.5、レビュー `nfr-requirements/reviews/review-01.md`）、機能設計（`construction/u4-business-data-time/functional-design/functional-spec.md` の D6・D10〜D12・D16〜D18・D23〜D27・D30）、契約（`inception/contract-design/contract-summary.md` の C1・C4）、質問と答え（`nfr-design-questions.md`: Q1: A・Q2: Looks correct）、U1 の性能の測り（`frontend/src/shared/format/timeZone/conversionPerformance.test.ts`）、既存の k6 の場面（`perf/k6/scenarios.js`）。

## 1. サーバー: 問い合わせを足さない（NFR2.1.1・NFR2.1.2）

### 1.1 作り

- `labelValueType` は、`ReferencePolicy` が参照の項目を作るときに使う参照先の表のビュー（DSL から作った列の一覧）の、ラベルの列の `valueType` を写すだけ。ピッカーの列の型（`PickerListColumn` の `valueType`）と同じ材料で、対象DB のメタデータ・内部DB を読まない。
- 状態が `AVAILABLE` でない参照の項目（`NO_PERMISSION`・`TOO_MANY`）には持たせない。
- 選択肢・ピッカーの行・ラベルの1件読みの SQL と、要求ごとの接続の数は変えない。

### 1.2 応答の大きさ

- 増えるのは、`AVAILABLE` の参照の項目1つにつき `,"labelValueType":"DATETIME_TZ"` の最大 29 バイト（最も長い型の名前のとき）。参照の項目を持たない定義・`AVAILABLE` でない項目は増えない。

### 1.3 確かめ

| 要件 | 確かめ | 置き場 |
|---|---|---|
| NFR2.1.1 | 既存の `BusinessDataConnectionUsageIT`・`ReferenceConnectionUsageIT`・`ReferenceWriteConnectionUsageIT` が変更なしで通る。`labelValueType` が付く参照の項目を持つ定義の要求で、接続の数が今と同じ（`ReferenceConnectionUsageIT` に、`labelValueType` が付く参照の項目を持つ定義の要求の場面を1件足して確かめる。確かめの場の決定） | Code Generation |
| NFR2.1.2 | 既存の k6 の業務データの場面（`bizDefinition`・一覧・詳細・参照の選択肢・ピッカー）を、使い捨ての環境で新しい版で流し、今の目標（p95・`checks` の率 1）を満たす | **Deferred（Build and Test）** |

## 2. 画面: 変換の回数を抑える（NFR2.1.3〜NFR2.1.5）

### 2.1 一覧・詳細の表示

- セルを描くたびに `formatCell` → `formatInstant` を1回通す。覚えておく仕組み（メモ）は足さない（Q2: Looks correct）。1回の描画で、時点を表す型の値1つにつき1回で、1ページの最大 1 万個でも U1 の目標（500 ミリ秒・平均 0.05 ミリ秒）の中に入る。
- 表示のタイムゾーンが `loading`・`error` のときは `formatCell` に時点の書式を求めない（画面が待つか、短い文を出す。D10・D11）。整形の呼び出しは 0 回。
- 表示のタイムゾーンの差し替え（プリファレンスの保存）で描き直すときも、同じ回数（新しいタイムゾーンで1回ずつ）。

### 2.2 フォーム

- 表示のタイムゾーンが `ready` になるまで、時点を表す型の欄を持つフォームは開かない（読み込み中の表示）。`error` の間も開かず、失敗の文と「再読み込み」の帯を出す。再読み込みが成功した時点のタイムゾーンで固定して開く（Q1: A）。
- 初めの値の変換（D16）は、フォームを開くときに1回だけ（フォームの状態の初めの値として持ち、再描画で作り直さない）。
- 入力の変換（`parseLocalDateTime`）は、欄を離れたとき・保存の押下・2択の選択のときだけ。キーを押すたびには行わない。欄の確かめの結果（`ok`・`invalid`・`gap`・`overlap-pending`）はフォームの状態に持ち、保存の押下で確かめ直すのは、まだ確かめていない欄と変わった欄だけ。
- 案内の今の時差は、フォームを開いたときに今の時刻を1回だけ取って決める（Q2: Looks correct の 4）。

### 2.3 検索

- 検索の押下1回につき、時点を表す型の条件1つあたり1回（範囲は始まり・終わりの2回）だけ変換する。作った時点の条件は、一覧の状態（今の `listState`）に送った条件として持ち、ページ送り・並べ替え・再描画で作り直さない。
- 検索の既定の値（D28）の変換は、検索の画面を初めて開いたときに1回。

## 3. 性能の予算と確かめ（まとめ）

| 対象 | 予算 | 確かめ | 置き場 |
|---|---|---|---|
| 一覧の1ページの整形（NFR2.1.3） | 呼び出しは時点の値の数以下。1 万個で 500 ミリ秒以内（U1 の目標） | 画面の単体テスト（呼び出しの数を数える。`loading`・`error` で 0 回）。U1 の性能の測りが今の目標で通る | Code Generation |
| フォームの変換（NFR2.1.4） | 初めの値は開くときに1回。入力は欄を離れたとき・押下・選択のときだけ | 画面の単体テスト（1文字ずつ入れても増えない。再描画で増えない） | Code Generation |
| 検索の変換（NFR2.1.5） | 押下1回につき条件の数まで。ページ送りで増えない | 画面の単体テスト | Code Generation |
| サーバーの問い合わせと接続（NFR2.1.1） | 今と同じ | 既存の結合テスト | Code Generation |
| サーバーの応答時間（NFR2.1.2） | 今の目標のまま | 既存の k6 の場面 | Deferred（Build and Test） |
| カバレッジ（`team.md` の下限。NFR 要件のレビューの指摘） | バックエンド・フロントエンドとも行 80%・分岐 70% 以上。`businessdata` の各パッケージにも同じ下限（パッケージごとの判定の対象。`packagesJudgedByTotal` に入っていない） | `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、値を記録する。`src/features/tables/` のカバレッジの値を Build and Test で記録する | Code Generation・Build and Test |
| `./gradlew verify` と CI の時間 | CI の上限の7割（約 42 分）を超えない | テストを足した後と Build and Test で測って記録する | Code Generation・Build and Test |

実際のブラウザの描画の時間は、185 の axe の流れが今記録している画面の時間と同じ形で記録するだけにし、目標にしない。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- NFR 要件: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/nfr-requirements/performance-requirements.md`・`security-requirements.md`・`tech-stack-decisions.md`・`reviews/review-01.md`
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/functional-design/functional-spec.md`
- 契約: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/contract-design/contract-summary.md`
- 質問と答え: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/nfr-design/nfr-design-questions.md`（[Q1] A・[Q2] Looks correct）
- 今のコード（読み取りだけ）: `backend/src/main/java/cherry/mastersmith/businessdata/domain/ReferencePolicy.java`・`backend/src/test/java/cherry/mastersmith/businessdata/web/*ConnectionUsageIT.java`・`perf/k6/scenarios.js`・`frontend/src/shared/format/timeZone/conversionPerformance.test.ts`

## Assumptions & Open Questions

- [assumption] 応答の大きさの最大 29 バイトは、型の名前の最長（`DATETIME_TZ`）で数えた見積もり。
- [assumption] 欄の確かめの結果をフォームの状態に持つ形（2.2）は、今の `validateForm` の呼び方に合わせて Code Generation で確定する。
