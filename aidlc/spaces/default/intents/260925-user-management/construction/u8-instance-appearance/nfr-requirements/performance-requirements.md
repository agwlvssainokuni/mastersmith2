# Performance Requirements — U8 インスタンスの見た目の設定（u8-instance-appearance）

U8 の性能の要件です。要件定義の NFR6（応答時間）を、この単位の1本の API `GET /api/appearance`（契約 C7）に当てます。答えは `nfr-requirements-questions.md`（Q1: A、Consolidated Summary Confirmation: Looks correct）。

出典の略号: NFR・FR は `aidlc/spaces/default/intents/260925-user-management/inception/requirements-analysis/requirements.md`、BR はこの単位の `construction/u8-instance-appearance/functional-design/rules.md`、Q1 と「要点 n」はこの段の `nfr-requirements-questions.md`（Q1 と「NFR の要点（案）」の番号）、U4 W2 は `construction/u4-display-foundation/functional-design/functional-spec.md` の W2。枝番はこの単位の中で振る（ほかの単位と同じ番号でも別の要件）。

## 前提

- U8 の応答は、起動時に解決してメモリに保持した2つの名前を返すだけで、内部DB・ほかの単位・外部の仕組みに触れない（BR1.6、`reliability-requirements.md` の NFR5.1）。
- 負荷の想定は前の Intent と同じ、利用者 最大 50 名・同時 10 件の要求（`aidlc/spaces/default/intents/260922-auth-audit-base/construction/u1-app-skeleton/nfr-requirements/performance-requirements.md` の NFR1.1）。
- 画面（U4）は、この API の答えが出るまで最初の画面を描かず、待ちに上限を置かない（U4 W2）。この応答が遅いと、ログインの画面・登録の完了の画面を含むすべての画面の最初の描画が遅れる。
- 測る環境は、当面の配備先である開発者の PC 上のコンテナとし、配備した環境とは別の使い捨ての環境で測る（`aidlc/spaces/default/memory/project.md` の Testing Posture）。
- 測定の持ち主は、この Intent の流れにある performance-validation の段（k6）。Build and Test では結合テストで機能を確かめ、負荷の試験の場面の用意を引き継ぐ。

## 1. 目標

| ID | 対象 | 目標 | 条件 | 測り方 | 出典 |
|---|---|---|---|---|---|
| NFR6.1 | `GET /api/appearance`（トークンを付けない要求） | 95% が 300 ミリ秒以内 | 同時 10 件の要求を繰り返す。軽い API の決まりどおり、内部DB のファイルが膨らんだ状態（悪い側の条件）のままの使い捨ての環境で測る | performance-validation の段の負荷の試験。既存の `perf/k6/scenarios.js` に見た目の設定の場面を1本足し（場面の名前はコード生成で決める）、95 パーセンタイルを出す。全件が 200 で、本文が2項目であることを checks で確かめる | NFR6、Q1: A、要点 1 |
| NFR6.2 | 要求ごとの処理の中身 | 要求の処理の中で設定を読み直さず、判定もしない。起動時に解決した値をそのまま返す。応答の本文は2項目だけの小さな JSON とする | すべての要求 | 単体テスト（要求を2回送っても、設定を解決する処理が起動時の1回だけ呼ばれる）と、コードの確かめ（コード生成のレビュー） | BR1.6、BR3.1、要点 1 |

## 2. 資源の目標

U8 は要求ごとに内部DB の接続・スレッド・大きなメモリを使わない。保持する値は2つの短い名前だけで、資源の目標の値は置かない。内部DB の接続を借りないことは `reliability-requirements.md` の NFR5.1・NFR5.2 で確かめる。

## 3. 測り方の注意

- 性能の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は `perf/README.md`。この API は監査に残らないため（BR3.5）、試験で監査ログは増えない。
- k6 などの長い試験は `caffeinate -i` を付けて流す（`project.md` の Testing Posture）。
- 時間は画面を含まない API の応答で測る。画面の最初の描画までの時間は U4 の受け持ちで、この単位の目標にしない。
- 目標を満たせなかったときは、目標を緩めて「満たした」ことにしない。原因をログと状態で確かめてから記録する（`project.md` の Testing Posture）。

## 4. 上流との差

承認済みの文書は書き換えない（`aidlc/spaces/default/memory/project.md` の Way of Working）。

| 項目 | 承認済みの記述 | この段での扱い |
|---|---|---|
| NFR6 の対象 | 要件の NFR6 は、招待・送り直し（5 秒）と、プリファレンス・パスワードの変更・登録の完了（1 秒）の目標だけを決めており、`GET /api/appearance` の目標は無い | 依頼者の決定（Q1: A）で、NFR6 の枝番として 300 ミリ秒の目標を足した（NFR6.1）。要件の文書は変えない。承認済みの文書と食い違う記述は無く、追加だけ |
