# Requirements Analysis の質問（Intent 261027-perf-targets-review）

依頼: 前の Intent 261025-deps-perf-carryover で Not Met だった性能・資源の目標を見直す（読み取りの p95: `bizSearchContains` は 3 秒未満・`bizSearchLast` と `bizSearchLastSorted` は 2 秒未満、`dslMixed` の `memory.peak` は 95% 未満、対象DB の `anon` の最大は 92% 以下。`anon` 85% の物差しは変えない）。k6 の合否の線と README を直して測り直し、前の物差しの値を並べて記録する。あわせて Dependabot の開いている PR（#80 ほか）を洗い出して取り込むか閉じる。

## 決まっていること（質問にしない）

- **緩和の決定**: 依頼者が 2026-10-28 に決めた（依頼の文のとおり）。`team.md` の「物差しを変えるのは要件定義で依頼者が明示に決めたときだけ」に当たるため、この要件で理由とともに記録する。理由は、部分一致が索引を使えない構造的な理由で 1 秒を超えること、`memory.peak` に回収できるページキャッシュが含まれること。成果物には「緩和」と明記し、Not Met を黙って Met にしない。
- **前の物差しの値を並べる**: 変えた後も、少なくとも1回は前の線（p95 は 1 秒、`memory.peak` は 90%、`anon` は 85%）での値を並べて記録する（`team.md` の Testing Posture）。
- **変えないもの**: 読み取りの p95 のうち、部分一致と深い OFFSET 以外は 1 秒未満のまま。`anon` 85% の物差し（`dslMixed` の判定）は変えない。
- **負荷の試験の持ち主**: この Intent の流れに Performance Validation の段が無いため、k6 と測定は Build and Test で流す（`project.md` の学び）。配備したアプリを止めて流し、長い試験は `caffeinate -dims` で包み、眠りが入った回は使わない。
- **Dependabot の受け方**: GitHub の画面でマージせず、手元で版と lockfile をまとめて更新し、`./gradlew verify` を通してから `develop` に統合し、プルリクエストは閉じる。見送る更新は `.github/dependabot.yml` の `ignore` に理由と外す時期をコメントで書く（`team.md`）。重大度 High 以上は先に取り込む。
- **アプリの変更は想定しない**: 変えるのは `perf/`（`perf/k6/scenarios.js`・`perf/README.md`・`perf/dsl-mixed-memory.sh`）と記録が中心で、`backend/src/main` は変えない見込み。

## 質問

### Q1. 緩めた p95 の線の当て方

`perf/k6/scenarios.js` の `thresholdsFor` は、今は DB の種類を区別せず業務データの読み取りに `p(95)<1000` を当てています。Not Met は MySQL・MariaDB で、PostgreSQL は全場面で満たしました。緩めた線（部分一致 3 秒、深い OFFSET 2 秒）をどう当てますか？

A. 3 種類の DB すべてに同じ緩めた線を当てる（場面の名前で決める。PostgreSQL も緩くなるが、前の線での値を並べて記録するので PostgreSQL の実力は残る）
B. MySQL・MariaDB にだけ緩めた線を当て、PostgreSQL は 1 秒のままにする（DB の種類別の線を台本に足す）
X. Other (please specify)

[Answer]: A

### Q2. 測り直しの範囲

測り直しでは配備したアプリを止めます（前回は約 4 時間 27 分）。どこまで流しますか？

A. Not Met だった場面だけ: MySQL・MariaDB の `bizSearchContains`・`bizSearchLast`・`bizSearchLastSorted`、`dslMixed`、対象DB の `anon` の最大。ほかの場面は流さない（前回の結果を使う）
B. A に加えて、PostgreSQL の同じ 3 場面も流す（新しい線で 3 種類の DB の値がそろう）
C. 業務データの読み取りの全場面を 3 種類の DB で流し直す
X. Other (please specify)

[Answer]: A

### Q3. 対象DB の `anon` 92% の判定の置き場

`anon` の最大は k6 の閾値ではなく、`perf/README.md` の手順で読む値です（今は線が書かれていません。`dslMixed` の `anon` 85% は別）。92% 以下をどう扱いますか？

A. `perf/README.md` に対象DB の合格の条件として「`OOMKilled` が false・`oom_kill` が 0・`anon` の最大が 92% 以下」を書き、測定の手順の出力に値を並べて判定できるようにする
B. README に線を書くだけにして、判定は記録（test-results）で人が読む
X. Other (please specify)

[Answer]: A

### Q4. Dependabot の開いている PR の洗い出しと扱い

依頼は #80 ほかとしか書いておらず、全件は `gh` で数えます。取り込むものの基準をどうしますか？

A. 開いている PR をすべて洗い出し、`team.md` の受け方に従う: 重大度 High 以上と、`verify` が通る更新は手元で取り込み、major の版の更新・`verify` や E2E が通らない更新・悪意のあるパッケージの疑いは見送り（`ignore` に理由と時期を書く）、PR は閉じる
B. A のうち、依存の版と lockfile の更新だけを取り込み、docker のイメージ（版のタグと index のダイジェストの固定先の組）は別の Intent に回す
X. Other (please specify)

[Answer]: A

### Q5. 配備と `main` への取り込み

この Intent の対象の段には Deployment Pipeline と Deployment Execution が入っています。アプリのコードは変わらない見込みです。どうしますか？

A. アプリは作り直さず配備もしない。段は「アプリの変更が無いため配備は不要」と記録して進める（`develop` への統合まで）。`main` への取り込みは依頼者が承認したときに行う
B. 通常どおり配備の段を行う（WAR とイメージを作り直して入れ替え、スモークテスト）
X. Other (please specify)

[Answer]: B

## Consolidated Summary Confirmation

- 緩和（依頼者の 2026-10-28 の決定）: 読み取りの p95 は部分一致（`bizSearchContains`）3 秒未満、深い OFFSET（`bizSearchLast`・`bizSearchLastSorted`）2 秒未満、ほかは 1 秒未満のまま。`dslMixed` の `memory.peak` ÷ 2g は 95% 未満（`anon` 85% の物差しは変えない）。対象DB（MySQL・MariaDB）の `anon` の最大は 92% 以下。理由は、部分一致が索引を使えないこと、`memory.peak` に回収できるページキャッシュが含まれること。「緩和」と明記し、前の線（p95 1 秒・`memory.peak` 90%・`anon` 85%）での値も並べて記録する。
- Q1（A）: 緩めた p95 の線は、DB の種類を区別せず 3 種類すべてに場面の名前で当てる。PostgreSQL の実力は前の線の値の併記で残す。
- Q2（A）: 測り直しは Not Met だった場面だけ（MySQL・MariaDB の `bizSearchContains`・`bizSearchLast`・`bizSearchLastSorted`、`dslMixed`、対象DB の `anon` の最大）。ほかは前回の結果を使う。配備したアプリを止めて流し、`caffeinate -dims` で包む。持ち主は Build and Test。
- Q3（A）: `perf/README.md` に対象DB の合格の条件（`OOMKilled` が false・`oom_kill` が 0・`anon` の最大が 92% 以下）を書き、測定の手順の出力に値を並べて判定できるようにする。
- Q4（A）: Dependabot の開いている PR（#80 ほか）を `gh` で全件洗い出し、`team.md` の受け方に従う。High 以上と `verify` が通る更新は手元で取り込み、major・`verify` や E2E が通らないもの・悪意のあるパッケージの疑いは見送って `ignore` に理由と時期を書く。PR は閉じる。
- Q5（B）: 通常どおり配備する。Dependabot の更新の取り込みでアプリの中身（WAR・イメージ）が変わりうるため。取り込みが無く変わらなかったときは、配備の段で依頼者に諮る。`main` への取り込みは依頼者の承認を得て `develop` から fast-forward で行う。

- レビューの指摘 R-01〜R-06 を案どおりに直した（依頼者の Request Changes）: `anon` 92% の分母は測るときの対象DB コンテナの `memory.max`（README の運用値で起動して記録）。合否の回は 2 回で悪い側の値で判定し、読み取りを先に流して書き込みは流さず、`dslMixed` は揺れが大きければ 3 回目を足す。深い OFFSET と `anon` 92% の理由、対象DB の `anon` の前の線（85% の参考の線と「OOM なし」の合格の条件）、前回の実測値を併記する。`thresholdsFor` は `k6 inspect` で場面ごとの線を照らして確かめる。#80 を見送ってアプリが変わらなければ配備は行わず記録で終え、Not Met が残っても配備と `main` への取り込みは進めてよい。

Does this all look correct before I generate the requirements artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
