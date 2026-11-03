# 技術の選択と、数の目標 — u1-time-zone-conversion

出典: 機能設計（`construction/u1-time-zone-conversion/functional-design/`）、要件（`inception/requirements-analysis/requirements.md`）、質問と答え（`nfr-requirements-questions.md` の Q1: A・Q2: A・Q3: A）、コード知識ベース（`aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`）、`team.md`（Code Style・Testing Posture）。

U1 は、画面の側の純粋な関数群（種類 library）。種類の決まりにより、性能・拡張・信頼性・観測の独立した文書は作らない（サーバー・対象DB・内部DB を使わず、状態を持たない）。性能の数の目標は、この文書の2節に置く。

## 1. 技術の選択

| 項目 | 選択 | 理由 | 検討した別の案 |
|---|---|---|---|
| 言語 | TypeScript（`strict`、`any` の禁止、`export default`・`enum` の禁止） | 既存のフロントエンドと同じ（`team.md` Code Style） | なし |
| 壁時計・時差の計算 | ブラウザの標準の `Intl.DateTimeFormat`（`timeZone` を明示に渡す。時差は `timeZoneName: 'longOffset'`、日時は `formatToParts` で数字を取る） | 新しい依存を足さず、ライセンスの確かめも脆弱性検査の対象の増加も無い（Q1: A）。夏時間・分の端数の時差・うるう日を、ブラウザの IANA のデータで扱える | `date-fns-tz`・`luxon`（MIT）: 計算を任せられるが、新しい依存が増え、ブラウザの `Intl` との差の確かめが別に要る。`Temporal` のポリフィル: 標準化の途中で、依存が重い |
| 名前の解釈 | `Intl.DateTimeFormat` に `timeZone` として渡して、例外が出ないかで判定（固定のオフセット表記を除く） | 追加の部品が要らない（BR1.10） | `Intl.supportedValuesOf('timeZone')` の一覧との照合: 一覧に無い別名を誤って弾く恐れ。選択の一覧は U2 が使う |
| 正規表現・文字の走査 | 標準の `RegExp`（入れ子の量指定子を使わない）と、固定の長さの検査 | 依存が要らず、入力の長さの上限を先に確かめられる（NFR1.4.1） | 構文解析のライブラリ: 規模に見合わない |
| 結果の型 | 判別可能な union（`kind` で分岐。例外を投げない） | 契約 C1・機能設計（BR1.5〜BR1.12）のとおり | 例外で返す: 呼ぶ側の分岐が漏れやすい |
| テスト | Vitest（単体）＋ `fast-check`（性質ベース） | 既存と同じ（`team.md` Testing Posture）。新しい依存なし | なし |
| ビルド・配備 | フロントエンドのビルド（Vite）に入る。単体では配備しない（埋め込み） | `unit-of-work.md` の配備の形 | なし |

## 2. 数の目標（性能。NFR2.1 に当たる細目）

| ID | 目標 | 測り方 | 根拠 |
|---|---|---|---|
| NFR2.1.1 | 1ページ最大（100 行 × 100 カラム = 1 万の値）の、時点を表す型の表示の変換が、画面のテスト（jsdom）の環境で合計 **500 ミリ秒以内** | Vitest の単体テストで、固定の時点（夏時間の地域・端数の桁 0・3・6 を混ぜた1万個）を、同じタイムゾーンで `formatInstant` に通し、`performance.now()` の差を測る。測った環境（Node の版・CPU）を成果物に書く | 一覧の1ページの表示で体感の遅れが出ない水準（質問 Q2: A）。要件 NFR2.1（日時の変換で、問い合わせの数を増やさず、今の性能の目標を変えない） |
| NFR2.1.2 | 1つの値の変換が、平均 **0.05 ミリ秒以内**（上の測りの合計 ÷ 値の数） | 同上 | 同上 |
| NFR2.1.3 | `Intl.DateTimeFormat` は、タイムゾーン（と用途）ごとに1つを使い回す（呼び出しごとに作らない） | 単体テスト: 同じタイムゾーンで 1 万回呼んでも、`Intl.DateTimeFormat` の生成が増え続けない（生成の回数を数える） | 作る費用が大きいため（Q2 の前提） |

上の数の目標は、この Intent で初めて置く（前の物差しは無い）。目標を緩めて満たしたことにはしない（`team.md`）。外れたときは、原因を確かめてから、依頼者に諮る。

## 3. 対象のブラウザ（Q3: A）

- 対象: Chrome・Edge・Firefox・Safari の現行の安定版と、その1つ前の版。
- 必要な機能: `Intl.DateTimeFormat` の `timeZone`・`timeZoneName: 'longOffset'`・`formatToParts`。使えないブラウザでは、`canInterpretTimeZone` が偽を返すか、機能の検出で、タイムゾーンの選択を隠し「ブラウザに合わせる」だけにする（ADR-009。U2 が持つ）。
- 確かめ: ブラウザごとの IANA の版による夏時間の判定の違いは、NFR Design の捨ての試し（実際のブラウザの E2E）で確かめる（ADR-009）。

## 4. 新しい依存

なし（Q1: A）。足す場合は、採用の前にライセンスを確かめ、Apache License 2.0 と異なるものは採用の理由を設計の記録に残す（`team.md`）。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u1-time-zone-conversion/functional-design/functional-spec.md`
- 要件: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/requirements-analysis/requirements.md`
- 質問と答え: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u1-time-zone-conversion/nfr-requirements/nfr-requirements-questions.md`

## Assumptions & Open Questions

- [assumption] 1万の値の変換が 500 ミリ秒以内、平均 0.05 ミリ秒以内は、`Intl.DateTimeFormat` を使い回した場合の見立て。NFR Design の捨ての試しで、実際の値を測って確かめる。
- 未解決の点: ブラウザの `Intl` が時差の秒（歴史的な時差）を返すか（機能仕様の前提）。NFR Design の捨ての試しで確かめる。
