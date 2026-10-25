# Build and Test の質問（Intent 261025-deps-perf-carryover）

Code Generation は承認済みで、B1〜B4 のコードは `develop` に統合済みです（B1 `98ef3e5`、B2 `3292282`、B3 `3bd1f4c`、B4 `5bebcab`）。この段は、その `develop` の先頭で、統合前に流せなかった確かめと負荷の試験を流して Met・Not Met・Unverified を件数で記録します。

## 決まっていること（質問にしない）

- **目標は緩めない**: Not Met 4 件（U1-NFR1.1・1.3・3.2・2.4）、`memory.peak`（上限 2g の 90% 未満）、画面の時間の線（5 つ）、対象DB の `anon`（85%）の線は前回のまま。直した後も Not Met が残れば、そのまま記録して次の Intent へ持ち越す（要件の F1: A）。
- **Q3: B（プール 10）の合格の条件**: 対象DB の OOMKilled が false で `oom_kill` が 0。どちらかが 0 でなければ、プール 10 は採らず既定を 5 に戻して依頼者に諮る（要件のとおり）。`anon` の最大は 85% の線と並べて記録し、超えたら合格の条件で Met として依頼者に諮る。
- **E2E は Intent の最後のこの段で、`develop` の先頭で全件流す**（B2 で流さなかった分を含む）。落ちたときの切り分けの順は、Playwright（1.64.1 を 1.63.0 に戻して再実行）→ B2 の他の npm の更新 → B3 の C5。画面の時間（U3-NFR1.7a〜e）もこの全件の中で 5 回の中央値で測る。
- **P6** は `navMenu` の p95 の測り直しと、締める案の提案だけ（目標の数値は変えない）。**FR3.4（C4）** の再現は追加せず、見立てと試みの範囲の記録で済ませる。**書き込みの `values` の名前** は DSL に無い名前だけが対象。いずれも Code Generation の承認の場で決まった。
- **`RETENTION_TIME=0` を配備物に入れるか**: 既定の回と試しの回の測定結果を添えて、結果が出た後に依頼者に諮る。
- **配備したアプリを負荷の試験のあいだ止める**（約 4 時間の見込み。前回 4 時間 27 分）。長い試験は `caffeinate -dims` で包み、眠りが入った回は使わない。
- この Intent の流れには Performance Validation の段が無いため、負荷の試験と測定は Build and Test が持つ（`project.md` の決まり）。

## 質問

### Q1. `develop` の先頭を push して CI を確かめる時期

`git push` はあなた自身が行います。CI の結果（`timeout-minutes: 60`、検査の job の時間も記録します）と、Dependabot の PR（17 件）を閉じる操作（FR1.1）は、push の後になります。

A. 負荷の試験の前に push する。CI の結果（1 回目の試行を含む）を見てから試験に入る。CI が落ちたときは、アプリを止める前に直せる
B. 負荷の試験が終わってから push する。試験の間、CI の結果を待たずに進められるが、CI が落ちたときは試験の後に直す
X. Other (please specify)

[Answer]: A

### Q2. この段で流す順

`verify`・`osvScan`・E2E の全件は合わせて約 1 時間、負荷の試験は約 4 時間で、どちらも PC の負荷が大きく、重ねられません。

A. 先に `verify`（`:backend:cleanTest :backend:cleanIntegrationTest` 付き）・`osvScan --rerun`・E2E の全件を流して通し、その後に負荷の試験に入る。通らない場合は、アプリを止める前に直せる
B. 負荷の試験を先に流し、その後に `verify` と E2E を流す
X. Other (please specify)

[Answer]: A

### Q3. 負荷の試験を流す枠

3 種類の対象DB（PostgreSQL → MariaDB → MySQL の順を見込む。MySQL の結果が出るまで決めを待たない）を 1 種類ずつ流し、そのあいだ配備したアプリは止まります。`memory.peak` の測り直し（既定の回と `RETENTION_TIME=0` の回を各 2 回）、`dslRoleOverlap`、`monitorLight` が加わります。

A. 続けて流す。アプリが止まる合計は約 4〜5 時間で、この間 PC を占有する。途中の結果は都度見せる
B. 日を分ける。対象DB の 1 種類ごとにアプリを戻し、次の枠で続ける（アプリが止まる時間は短く区切れるが、種の入れ直しと環境の作り直しが回数分増える）
X. Other (please specify)

[Answer]: A

### Q4. 承認済みの Code Generation の記録の扱い（アーキテクトのレビューの R-01）

`traceability.json` の OK の中に、未実施の確かめが残るもの（FR1.1・FR3.4・FR3.10・FR4.6）があります。Code Generation の承認済みの成果物は書き換えません。

A. この段の `cross-unit-traceability.md` と `test-results.md` で、実測の結果を添えて最終の判定をつける。承認済みの `traceability.json` は書き換えない
B. 承認済みの `traceability.json` の該当の件を Deferred に書き直してから進める（記録の訂正として理由を残す）
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

- Q1（A）: 負荷の試験の前に、`develop` の先頭を依頼者が push する。CI の結果（試行ごと、検査の job の時間を含む）を確かめてから試験に入る。CI が落ちたときは、アプリを止める前に直す。Dependabot の PR（17 件）を閉じる操作は、push の後に依頼者に確かめてから行う。
- Q2（A）: 先に `:backend:cleanTest :backend:cleanIntegrationTest verify`・`osvScan --rerun`・E2E の全件（画面の時間 U3-NFR1.7a〜e を含む）を流して通し、その後に負荷の試験に入る。
- Q3（A）: 負荷の試験を続けて流す（PostgreSQL → MariaDB → MySQL、`memory.peak` の測り直し、`dslRoleOverlap`、`monitorLight`）。配備したアプリが止まる合計は約 4〜5 時間で、`caffeinate -dims` で包む。途中の結果は都度見せる。
- Q4（A）: 承認済みの `traceability.json` は書き換えず、この段の `cross-unit-traceability.md` と `test-results.md` で、実測の結果を添えて FR1.1・FR3.4・FR3.10・FR4.6 などの最終の判定をつける。
- 決まっていること: 目標は緩めない。プール 10 の合格の条件は OOMKilled が false で `oom_kill` が 0。不合格なら既定を 5 に戻して諮る。E2E が落ちたときの切り分けは Playwright → B2 の他の npm → B3 の C5 の順。`RETENTION_TIME=0` は結果を添えて諮る。

Does this all look correct before I generate the artifacts?

- Looks correct
- Request changes

[Answer]: Looks correct
