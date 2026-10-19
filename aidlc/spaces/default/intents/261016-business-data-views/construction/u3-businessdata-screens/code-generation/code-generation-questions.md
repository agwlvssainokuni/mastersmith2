# Code Generation の質問 — U3 u3-businessdata-screens（B5・B6・B7）

計画（`code-generation-plan.md`）の 10節「依頼者に確かめたいこと」の答えを先に決め、計画に反映してから Plan Approval に進みます。詳しい説明は計画の 10節にあります。

## D1. 見本の PG を前提にする E2E の仕組みを入れる Bolt

A. すべて B6 に入れる。B6 の最初に上乗せで開けて誤ったパスワードが拒まれることを確かめ、手で REVOKE してから準備のタスク・040 の書き換え・180 を入れる（推奨）

B. すべて B5 に入れる

C. 上乗せと 040 の書き換えだけを B5 に入れ、残りは B6 から有効にする

X. Other (please specify)

[Answer]: A

## D2. 作業ロールを切り替えた後の画面

A. 詳細・作成・更新の画面は同じテーブルの一覧へ置き換えて移り、一覧は既定の状態から読み直す（推奨）

B. 作成・更新の画面はその場に留まり、定義を読み直して入力できるカラムだけを残す。詳細は一覧へ移る

X. Other (please specify)

[Answer]: A

## D3. 書き込みの 403 の文

A. 鍵 `tables.problem.writeForbidden` を足し、「権限が変わったため、保存できませんでした。作業ロールを確かめてください。」（削除は「削除できませんでした」、英語も）を出す（推奨）

B. 読み取りと同じ 403 の文のまま

X. Other (please specify)

[Answer]: A

## D4. 「保存されたか分かりません」の後の画面

A. 入力は残し、もう一度押すと確かめの Modal（作成は「同じ行が2つできることがあります」）を出してから送る。確かめる入口も並べる（推奨）

B. 作成だけは保存を押せなくし、確かめる入口だけを出す

C. どれも押せるまま、Modal は出さない

X. Other (please specify)

[Answer]: A

## D5. 測りの見本の生成の値を報告の json の探索に入れるか

A. 入れる。`SECRET_FIXED_VALUES` に足す（推奨）

B. 入れない。架空の値で害がないことを記録する

X. Other (please specify)

[Answer]: A

## D6. B7 の passes の確かめの対象と場面

A. C1・C3〜C6 と U3 の行の操作・行のリンクを必須にし、C4 は2語の氏名の場面だけ。C2・C7 は既存の axe の E2E に場面を足して対象に入れる（推奨）

B. C2・C7 は場面を足さず、理由を記録して対象から外す

X. Other (please specify)

[Answer]: A

## D7. U3 の測りで目標を超えたとき

A. 生成を止めて依頼者に諮り、手当てを決めてから B5 を続ける（推奨）

B. 超えても B5 は今の形で統合し、手当ては Build and Test の判定の後に決める

X. Other (please specify)

[Answer]: A

## D8. 一時のロールのパスワードが psql の誤りの出力に出うること

A. `-v ON_ERROR_STOP=1` を付け、標準エラー出力を Gradle のログに流さず、件数・段・SQLState だけを出す。サーバーのログに残りうることは既知の制約に書く（推奨）

B. A に加えて、SCRAM-SHA-256 のハッシュを Gradle の中で作って渡し、平文を SQL に載せない

X. Other (please specify)

[Answer]: A

## D9. 別のタブで利用者が替わる場合の記憶

A. 既知の制約のまま残す（推奨）

B. トークンの更新で利用者が替わったときにも記憶を消す

X. Other (please specify)

[Answer]: A

## D10. `src/features/tables/` に `no-console` を当てるか

A. `src/features/tables/**` と U3 の `src/shared/` の部品に `no-console`（error）を当てる（推奨）

B. 当てず、verify の後の検索で 0 件を確かめるだけ

X. Other (please specify)

[Answer]: A

## Plan Approval

`code-generation-plan.md`（埋め込みの Testing Contract と 3節の A1〜A11 を含む）と `unit-test-instructions.md` の承認です。D1〜D10 の答えは計画に反映済みです。

[Approval Fingerprint]: sha256:v3:b528d6edbaf8d3394a695f37fe0e98927910cc275afe2abfac1b3861a4f1d3e9
[Planned Source]: 9d544162d48a22f72b67dff219266f98404e39417befcb7fdceea7a7b5e20201

- Approve Plan
- Request Changes

[Answer]: Approve Plan
