# Code Generation の質問（Intent 261025-deps-perf-carryover）

計画（`code-generation-plan.md`）の末尾の「依頼者に確かめたいこと」Q1〜Q5 の答えと、計画の承認です。

## 計画の確認（Q1〜Q5）

### Q1（R-08）FR3.1 で符号つきの範囲の外を 400 にするか

A. 符号なしと分かる名前（`int unsigned` など）だけ 400。符号を持たない名前は従来どおり
B. 符号を持たない名前も符号つきと読み、符号つきの範囲の外も 400 にする
C. C1 を見送る
X. Other (please specify)

[Answer]: A

### Q2（FR1.4）B2（Playwright #64 を含む依存の更新）の統合の前に E2E を流すか

A. 流す（`team.md` の規則の例外）
B. verify だけで統合し、B3 の前と Intent の最後に E2E を流す
X. Other (please specify)

[Answer]: A

### Q3（FR3.5）C5 の直し方

A. `usePasswordChangeForm`・`usePreferencesForm` の「部品が外れた印」を `useLayoutEffect` で下ろす
B. テストの待ち合わせだけを直す
X. Other (please specify)

[Answer]: A

### Q4（FR3.1）既定の DSL の生成で符号なし整数を `int unsigned` と書くか

A. 書く（再生成の差の表に `int` → `int unsigned` が出る。README に書く）
B. 生成は変えない
X. Other (please specify)

[Answer]: A

### Q5（R-09）FR4.5・FR4.7 の合否の線は前回のまま、計画 5.2 節のとおりでよいか

A. 前回の線のまま（`memory.peak` 90% 未満、画面の時間は 5 つの線の中央値）
B. 線を変える
X. Other (please specify)

[Answer]: A

## Plan Approval

`code-generation-plan.md`（埋め込みの Testing Contract を含む）と `unit-test-instructions.md` を承認しますか？

[Approval Fingerprint]: sha256:v3:11ff9377921ef93e4696f10484adc28d9fbec6b25a4f2fae1d43fce5a625bff6
[Planned Source]: f003290fcd6eab1e483a2c9327835c3bfbe5f6b4b9c1ea27cd9f8f636831bb56

- Approve Plan
- Request Changes

[Answer]: Approve Plan
