# Code Generation の質問 — U4 u4-a11y-carryover（B3）

計画（`code-generation-plan.md`）の 10節「依頼者に確かめたいこと」の答えを先に決め、計画に反映してから Plan Approval に進みます。U4 のコード生成は B3 だけです（B7 は U3 の計画の最後）。

## D1. B3 の統合の単位

A. 1本のブランチで C1〜C5 を積み、まとめて fast-forward する。`develop` に5つのコミットが並ぶ。統合と E2E の全件は1回で済む（推奨）

B. C1（固定先の更新）だけを先に fast-forward で統合し、C2〜C5 は別のブランチで squash して統合する（統合と E2E が2回になる）

X. Other (please specify)

[Answer]: A

## D2. fetch で b0a1e79 より新しい上流のコミットが見つかったとき

A. 固定先は b0a1e79 にし、新しいコミットは件名と変わったファイルを記録するだけにする。取り込むかは B7 で上流のソースを確かめて決める（推奨）

B. 新しいコミットまで上げる（口の形・lockfile の差・`hasInstallScript` を確かめ直す）

X. Other (please specify)

[Answer]: A

## D3. 依頼（5）の送付

機能設計の承認の場で「依頼（5）の表の2つの項目を、機能設計の承認の前に送る」と決めましたが、送ったことと日付が記録に見当たりません。

A. 送ってある（送った日を記録する）

B. まだ送っていない。B3 の生成の前に依頼者が送り、日付を記録する

C. 送らない。U3 の手当てのまま残し、置き換えを次の Intent に回す

X. Other (please specify)

[Answer]: A

## D4. 4.14 の試しで、すべての場面の結果を記録する方法

A. 試しの間だけ、作業フォルダの中で axe の確かめを「記録して続ける」形（`expect.soft`）にする一時の差分を当てる。規則と要素は外さない。差分は統合せず、版と一緒に戻す（推奨）

B. 確かめは変えず、最初に落ちた場面までを記録する

X. Other (please specify)

[Answer]: A

## D5. 区分の要約の注記の残し方

A. いつも残す（規則・鍵・件数だけ。選択子と値は入れない）。B7 と U3 の試しも同じ注記から記録を作れる（推奨）

B. 環境変数を付けて流したときだけ残す

X. Other (please specify)

[Answer]: A

## D6. 4.14 の試しで違反が出たときの扱い

A. 試しの後に生成を止め、違反の表と直し先の案を見せて、B3 で直すか B7 へ回すかをその場で諮る。make-you-chic-ui の部品の違反には依頼の文面の案を添える（推奨）

B. アプリの側で直せる違反は、諮らずに B3 で直す。make-you-chic-ui の部品の違反だけを諮る

C. B3 は記録だけにし、直しはすべて B7 で行う

X. Other (please specify)

[Answer]: A

## Plan Approval

`code-generation-plan.md`（埋め込みの Testing Contract を含む）と `unit-test-instructions.md` の承認です。D1〜D6 の答えは計画に反映済みです。

[Approval Fingerprint]: sha256:v3:5fa3bce329ac04db9fdfcbfcf330f40cf74c5fd86f0c44631bdaffe37109f529
[Planned Source]: 6d56ce1df9597731458d846b06455ba07f8d5e2acbc9d57c97d6fcbe0ca5a919

- Approve Plan
- Request Changes

[Answer]: Approve Plan
