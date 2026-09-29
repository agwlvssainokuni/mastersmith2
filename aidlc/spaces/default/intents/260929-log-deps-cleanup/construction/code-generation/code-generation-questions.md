# Code Generation の質問（260929-log-deps-cleanup）

計画（`code-generation-plan.md` の 6節）で依頼者に確かめたい点です。答えを計画に反映してから、計画の承認（Plan Approval）を行います。

## Question 1（Q-A、伏せ字の形）
ローカル部が1文字のメールアドレスでは、承認済みの形（先頭の1文字＋`***`＋`@`＋ドメイン）だとローカル部が全部見えます（例 `a***@example.com`）。どうしますか？

A. 承認済みの形のままにし、今の形をテストで固定する
B. ローカル部が1文字のときだけ `***@ドメイン`（先頭の文字を出さない）にする
X. Other (please specify)

[Answer]: A

## Question 2（Q-B、ログのキーの名前）
伏せ字にした値を載せるログのキーの名前をどうしますか？

A. 今の `email` のままにする（外部エクスポートでは引き続き `[REDACTED]` になり、README 664 行も変えずに済む）
B. `maskedEmail` などに変える（名前で伏せ字と分かるが、外部エクスポートでは伏せ字がそのまま送られ、README の説明も直す）
X. Other (please specify)

[Answer]: B（キーの名前は `maskedEmail`）

## Question 3（Q-C、team.md を直す時点）
team.md の Testing Posture は、計画に貼った Testing Contract（テストの決まり）の入力です。途中で直すと、計画の承認のやり直しになるおそれがあります。いつ直しますか？

A. コード生成の段の承認の後（Build and Test の中）で直す
B. 計画どおり、コードとテストの手順がすべて済んだ後（Step 21）に、この段の中で直す
X. Other (please specify)

[Answer]: A

## Question 4（Q-D、手元の監視の確かめの深さ）
警報 ms-check-p95 を 500 ms にした後の手元の監視の確かめを、どこまで行いますか？

A. 警報の決まりとダッシュボードが読み込まれ、式が誤りなく評価されることまで
B. A に加え、`GET /api/admin/check` に要求を送り、式が数の値を返すことまで確かめる（管理者のアクセストークンが要り、監査イベントが内部DB に残る。パスワードの要る操作は依頼者が行う）
X. Other (please specify)

[Answer]: A

## Question 5（Q-E、固定先の更新の後の新しい違反）
make-you-chic-ui を `077f5b4` に上げた後、E2E の 100 で別の違反が新しく出たときの扱いは？

A. 一覧に足さずに止めて報告する
B. 一覧に足して進め、後で報告する
X. Other (please specify)

[Answer]: B

## Plan Approval
`code-generation-plan.md`（埋め込んだ Testing Contract を含む）と `unit-test-instructions.md` のこの内容で、コードの生成を始めてよいですか？

[Approval Fingerprint]: sha256:v3:45d59cecba057451da83f883be661b306d20d5de59a3f550033d1ef26ef29a27
[Planned Source]: 255451418f8d659e9c796a568a499155a1c5fdb845917bbd79c82790b453d0db

- Approve Plan
- Request Changes

[Answer]: Approve Plan

## 生成の途中の質問

## Question G1（OtlpLogExportIT の失敗）
最後の verify（Step 21）で、結合テスト `OtlpLogExportIT` の `personalValuesMasked` が失敗しました。このテストは、外部へ送ったログでキー `email` の値が `[REDACTED]` に伏せられることを確かめていますが、キー `email` を出していたのは初期管理者の INFO だけで、Q-B: B でキーを `maskedEmail` に替えたため、`email` のログが 0 件になりました。計画の影響の範囲に無かったテストです。どう直しますか？

A. キー `email` は「送った件数が 0 件」を確かめる形にし、キー `maskedEmail` が伏せ字の値のまま送られることを確かめる1件を足す（`SanitizingLogRecordExporter` は変えない）
B. キー `email` を、伏せ字を確かめる4つのキーから外す
X. Other (please specify)

[Answer]: A

## Question G2（外部エクスポートで伏せるキーの `email`）
`SanitizingLogRecordExporter` の伏せるキーに残る `email` は、今はどこからも出されません。どうしますか？

A. 残す（今後キー `email` でログを出す箇所ができたときの守りとして。計画どおり変えない）
B. 外す
X. Other (please specify)

[Answer]: A
