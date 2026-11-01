# Build and Test の質問

コード生成の段で、5つの単位（U1〜U5、Bolt B1〜B6）と承認の場の直しの Bolt B7 を `develop` に統合した（B7 の squash は `6412122`、その後に記録だけのコミット `b398ed5`）。B7 の作業ブランチの上では `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（26分25秒、単体 2,807 件・結合 1,652 件、失敗 0）と E2E の全件（277 件 passed、13.8 分）が通り、`6412122` の CI は1回目の試行で成功した（検査の job 28分27秒）。`b398ed5` は `aidlc/` の下だけの変更のため、`develop` の先頭のアプリのソースは B7 で流した形と同じである。

この段の前提の対象の一覧（目標の在庫、案）は `target-inventory-draft.md` に置いた。

## 決まっていること（質問にしない）

- **Test Strategy は Standard**。この段の手順書は `build-instructions.md` と `integration-test-instructions.md`（境界と単位の間の受け渡し）を作る。性能の目標の合否の持ち主は、この Intent の後の段の **Performance Validation**、監視のパネルと警報（U1 の NFR2.10・NFR3.8、U2 の NFR2.29・NFR3.28、パネル 46・47）の持ち主は **Observability Setup** で、この段では `Unverified` とし持ち主を書く。目標と物差しは緩めない（`project.md`）。
- **件数とカバレッジ**は、`:backend:cleanTest :backend:cleanIntegrationTest` を付けた `verify` の実測だけを報告する。長いテスト（verify・E2E・k6）は `caffeinate -dims` で包み、流す前の `pmset -g assertions` と流した後の `pmset -g log` で眠りが無かったことを確かめる。colima の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を付ける（`project.md`）。
- **要件の網羅**は、要件定義の FR・NFR から、単位の NFR 要件の枝番と機能設計の BR を経て、各単位の Code Generation の `traceability.json` へ2段の連鎖でたどる（`project.md`）。
- **CI の記録**は、前の `main` への取り込みの後のすべてのコミットの CI を `gh run view --json attempt,jobs` で試行ごとに確かめ、再実行で通ったものは1回目の失敗とテストも書く。時間は run の全体ではなく検査の job の始めから終わりで測る（`project.md`）。今までの記録では最大は `c91d831` の 37分45秒で、42 分の線（上限 60 分の7割）の内。42 分を超えたときだけテストの分け方を諮る（`team.md`）。
- **`a75baec` の CI の1回目の失敗**（`ReferenceSameResponseIT`、目印がトレースID に当たる不安定さ）は `a3099f8` で直した。直した後の CI（`711ddd0`・`6412122`）は1回目で成功。直す前と後の確かめ（`team.md` の不安定なテストの二段の確かめ）が記録にあるかを読み取りで確かめて書く。
- **U1 R-02（`ReferenceTimeoutIT` の経過時間の上限）**は、B7 の後の CI の試行ごとの結果で不安定でないかを確かめるだけにする。落ちたら `team.md` の不安定なテストの決まりで扱う（依頼者の決定どおり。新しく尋ねない）。
- **U1 R-01（応答しない相手は TIMEOUT でなく UNAVAILABLE）**は、U2 のテスト（`ReferenceWriteTimeoutIT`）と U2 の `code-summary.md` 4節が UNAVAILABLE に読み替えていることを読み取りで確かめて記録する。設計の文書は書き換えない。
- **U1 R-03・U2 R-04（カバレッジと試し走りの記録）**は、この段の実測で `traceability.json` の NFR5.1・NFR5.21・NFR2.2 を補う形で記録する（承認済みの記録は書き換えず、この段の成果物に書く）。
- **U3 R-04** は、この段で読み取りの確かめとして行う: E2E 180・190・195 の差分、日英の文言の鍵の過不足（`messages.ts` の日と英の鍵の集合の一致）、バックエンドの DTO（`NON_NULL` の欠け・ラベル読みの `page` の null）と画面の読み込みの照合、B4（`711ddd0`）と B7（`6412122`）の CI の試行ごとの記録。
- **U4 の引き継ぎ H1〜H7**（計画 11節）はこの段で行う: H1 架空の JWT の形を置いた1回の `perfEvidenceScan`（終了コード 4・出力に値と区間が 0 件・置いたファイルを消して `git status` に残らない）、H2・H3 ファイルごとの passed の件数と CI での成功、H4 実際の k6 の summary json を道具で置いて `--scan` と `gitleaks dir` で 0 件（Q3 の試し走りの出力を使う）、H5 時間、H6 統合の後の検索、H7 E2E。
- **U4 の B7 の差の確かめ**（新しい Minor）: `git show --stat 6412122` で、道具 `perf/redact-k6-evidence.mjs` と U4 のテストが変わっていないことを確かめ済み（変わったのは `perf/README.md`・`perf/biz/make-biz-seed.sh` とバックエンド・画面のファイル）。
- **U5 の README の本文の検索**（新しい Minor）: 本文に 2304m を今の値とする文が無いこと、5,504 MiB の計算があること、本文に Intent の名前が無いこと、JWT の形が無いことを確かめ済み。この段では `gitleaks dir` と線の値の検索（CB5・CB6）を最後の確かめとしてもう一度流す。
- **U5 R-04** は CI の記録の決まりで扱う（`c91d831` は1回目で成功、37分45秒と記録済み。gh で取り直して確かめる）。U5 R-03（作り直しをしなかった読み替え）は依頼者が受け入れ済みで、救済の WARN は未計測のまま。Q3 でアプリを起動し直すときに件数を数える（値は表示しない）。
- **MySQL の 1038 の確かめ**（U1 の NFR 設計 R-04、`logical-components.md` 6節）は、この段で使い捨ての環境の MySQL で1回だけ流し、状態コードと `sqlState`・`vendorCode` を記録する（値・メッセージは記録しない）。直すかは後の Intent の要件で決める（F2: A のまま）。Q3 の負荷の試験の時間の中で行う。
- **種の台本の 0 行の確かめ**（U2 R-03 の直し）は、PostgreSQL でしか流していないため、Q3 で MySQL・MariaDB でも流し、0 行で終了コード 0 を確かめる。
- **性能の緩和の記録**（2026-10-28 の決定: 読み取りの p95 の部分一致 3 秒・深い OFFSET 2 秒、`dslMixed` の `memory.peak` 95%、対象DB の `anon` 92%）は、Intent 261027-perf-targets-review の要件 FR1.1〜FR1.5 に理由と前の線とともに記録済みで、この Intent の要件 NFR2 も「既存の緩めた線（3 秒）」としてそれを引いている。`bizSearchWide`・`bizCreateLarge` の `memory.peak` はその決定に含まれていない（Q5）。
- **Dependabot の #81・#82**（FR8）は `41b8bf5` で取り込み済み。PR が閉じていることを gh で確かめて記録する。
- コミットは、この段の成果物の初稿と段の承認のときは尋ねずに積み、それ以外は提案して承認を得る（`project.md`）。`origin` への push は依頼者が行う。

## Q1. Intent の最後の verify と E2E の全件

`develop` の先頭のアプリのソースは B7 で流した形と同じだが、この段の Step 9 は実行を求めており、`team.md` は Intent の最後（Build and Test）に E2E を1回流すことを求めている（U4・U5 は統合の前に E2E を流していない）。Q2 で直しを入れるなら、その後に1回流せば直しも含めて確かめられる。

**推奨: A**。理由: この段の実行と Intent の最後の E2E の決まりを、`develop` の今の形の実測で満たせる。U1 R-03・U2 R-04・`features/tables` のカバレッジを、同じ1回の報告から読める。時間は verify 約 27 分＋E2E 約 14 分で、配備したアプリを止める必要は無い。

A. Q2 の直し（あれば）を統合した後の `develop` の先頭で、`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` と `./gradlew e2eTest` を1回ずつ流し、件数・パッケージごとのカバレッジ・`features/tables` のカバレッジ・時間を記録する

B. 流さない。B7 の作業ブランチの上の結果（verify・E2E）と残っている jacoco の報告、CI の結果で代える（約 40 分を省く代わりに、`develop` の形の手元の確かめは CI だけになり、Q2 の直しを入れるならその統合の前の verify と E2E は別に要る）

C. verify だけを流し、E2E は B7 の結果で代える

X. Other (please specify)

[Answer]: B

## Q2. Build and Test に回したレビューの指摘のうち、直すか記録だけにするか

対象: U3 R-03（使っていない文言の鍵 `tables.reference.loading` が日英に残る）、U4 R-01（README の「置換とは別の検査」は、実際には置換と同じ形の定義 `scanJwtShapes` を使う）、U2 R-02（`ReferenceWriteConnectionUsageIT` が MariaDB を含まず、内部DB の借りた回数を数えない。`team.md` の (j) は対象DB と内部DB の両方の数を確かめるとしている）。あわせて、U4 R-02（道具のカバレッジが verify の下限の外）・U4 R-03（道具に ESLint と型の検査が当たらない）・新しい Minor（手がかりの無い想定外で `stage` の無い WARN が出る経路）は、どの選択肢でも直さずに既知の制約として記録する（直すと画面の設定や本体の作りに広く触れ、今回の目的に対して手間が大きいため）。

**推奨: A**。理由: U2 R-02 は `team.md` の必ず書くテスト（(j) 接続の数）との差で、MariaDB と内部DB の借りた回数を足せば決まりにそろう。U3 R-03・U4 R-01 は小さく、Q1 の最後の verify と E2E を1回流せば直しもまとめて確かめられる。U3 R-03 は frontend の本体に触れるため単独では統合の前に E2E が要るが、Q1 の A と組めば追加の流しは要らない。代わりに、MariaDB の結合テストが増えて verify と CI が数十秒〜1分ほど伸びる見込み（CI は最大 37分45秒で 42 分の線の内）。

A. 短命のブランチ（`fix/261029-reference-lookup-cleanup-bt`）で、U3 R-03（鍵を日英から消す）・U4 R-01（README の文を「同じ形の定義を、書き出す直前の文字列に別の経路で当てる」に直す）・U2 R-02（`ReferenceWriteConnectionUsageIT` に MariaDB と、内部DB の借りた回数の行を足す）を直し、統合してから Q1 の確かめを流す。残りは記録だけ

B. U3 R-03・U4 R-01 だけを直す。U2 R-02 はテストを足さず、MariaDB を含めない理由と内部DB の借りた回数を数えない理由を差として記録する

C. どれも直さない。すべてを既知の制約として記録し、直すかは後の Intent の要件で決める

X. Other (please specify)

[Answer]: A

## Q3. この段での負荷の試験（k6）の範囲と、配備したアプリを止める時間

NFR 要件は、種の流し込みと行の数・目印・表の大きさの確かめ（U1 の NFR2.4）、`k6 inspect` と短い試し走り（U1 の NFR2.2、U2 の NFR2.22・NFR2.24）をこの段の持ち主とし、3種類の対象DB での合否の判定（U1 の NFR2.1・NFR2.3、U2 の NFR2.21）を Performance Validation の持ち主としている。使い捨ての環境（アプリ 2g・MySQL 2688m など合計 5,504 MiB）は VM（5,910 MiB）の空きに収まらないため、流す間は配備したアプリを止める。

**推奨: A**。理由: 承認済みの持ち主の分け方どおりで、台本と種の誤り（前の Intent の Performance Validation では MySQL の種の文字の組の誤りで約 4 時間 27 分止めた）を判定の回の前に見つけられる。MySQL の 1038 と種の 0 行の確かめ、H4（実際の summary json を道具で置く）も同じ環境で済む。止める時間の見込みは約 1〜1.5 時間（3種類 × 種と試し走りで約 15〜20 分、MySQL の 1038 の確かめで約 10 分、片付け）。起動し直すときに救済の WARN の件数を数え、U5 の NFR8.84 の未計測を埋める。

A. この段では、3種類の対象DB で種を流し込み、行の数・目印・表の大きさと 0 行の確かめ、`k6 inspect`、参照の6つの場面（`refDefinition200`・`refDefinition201`・`refPickerEquals`・`refLabel`・`refPickerContains`・`refCreateChecked`）の試し走り（1 VU・10 秒、判定ではない）、MySQL の 1038 の確かめ、H4 を行う。判定の回（10 VU・3 分）は Performance Validation に任せる

B. この段で判定の回まで流す（Performance Validation は結果の確かめだけにする）。止める時間は約 3〜4 時間の見込み

C. この段では何も流さず、種と試し走りも Performance Validation に回す（止めるのは1回で済むが、承認済みの持ち主の分け方との差を記録し、MySQL の 1038・H4・0 行の確かめも後へ回る）

X. Other (please specify)

[Answer]: A

## Q4. 負荷の試験の比べる相手と、読み取りのプール上限 10 の回

参照の場面は読み取りのプールの上限 5 で流す（U1 の NFR2.3）。`perf/README.md` の履歴 H61 は、上限 5 で流した前の結果を Intent 261016-business-data-views の Performance Validation とし、上限 10 で流した直前の Intent（261025・261027）は並べる相手にしないと書いている。U2 の基盤の設計 4.4 は「既存の書き込みの場面を上限 10 の回でも1回流すか」をこの段の計画で決めるとしている。

**推奨: A**。理由: プールの上限をそろえないと値を並べても比べにならず、H61 の決まりと合う。上限 10 の回を足すと止める時間が1種類あたり約 15〜20 分伸びる割に、判定には使わない値のため。

A. 比べる相手は Intent 261016 の Performance Validation（上限 5）の結果とする。既存の書き込みの場面は上限 5 の回だけで流し、直前の Intent の値（上限 10）と並べるときは上限が違うことを書く。上限 10 の回は流さない

B. A に加えて、既存の書き込みの場面を上限 10 の回でも1回流し、直前の Intent の値と並べる（Performance Validation の回で行う）

C. 比べる相手を置かず、今回の値だけを記録する

X. Other (please specify)

[Answer]: A

## Q5. `bizSearchWide`・`bizCreateLarge` の `memory.peak` の線（コード生成の承認の場の決定 B の要件としての決定）

今の線は、承認済みの要件（Intent 261016 の U1 の NFR2.5・U2 の NFR2.8）の「アプリのコンテナの `memory.peak` が上限 2g の 90% 未満」。コード生成の承認の場で、依頼者は 95% 未満に緩める方向（決定 B）とし、目標の緩和のため、この段で要件として決めることにした。`team.md` は、物差しを変えるのは要件定義で依頼者が明示に決めたときだけとし、変える理由を記録し、変えた後も少なくとも1回は前の物差しの値を並べて記録することを求めている。

前の実測: `bizSearchWide` の `memory.peak` は Intent 261016 の Performance Validation で最大 969.7 MiB（47.3%）、Intent 261025 で最大 952.6 MiB。`bizCreateLarge` は Intent 261016 で最大 886.5 MiB（43.3%）。どちらも 90% の線から遠い（`dslMixed` が 94% 前後で 90% を超えたのとは違う）。

**推奨: B**（承認の場の決定 B の方向とは違う推奨）。理由: 実測は 50% に届いておらず、緩める理由を実測で書けない。`dslMixed` の緩和の理由（回収できるページキャッシュが `memory.peak` を押し上げる）は、これらの場面で線に近づいた実測が無いため当てはまると言えない。理由の無い緩和は `team.md` の「変える理由を記録する」に合わない。線をそろえたい理由で緩めるなら A を選び、その理由を要件として書く。

A. 95% 未満に緩める。理由は「`dslMixed` と線をそろえ、回収できるページキャッシュで判定が揺れないようにする」と要件として記録する。前の線 90% の判定は、Performance Validation の判定の回で値を並べて記録する。`perf/README.md` の「今の基準の一覧」・本文の2行・履歴（H69 の続き）を直す（文書だけの変更）

B. 90% 未満のまま確定し、決定 B の方向を取り下げる。`perf/README.md` の「緩めるかは未決」の注記を「90% 未満で確定（理由: 実測が線から遠く、緩める理由が無い）」に直し、履歴に経緯を足す（文書だけの変更）

C. 今は 90% 未満のままにし、Performance Validation で測った後に、その段の質問で決める

X. Other (please specify)

[Answer]: B

## Q6. 深いページのピッカー（PostgreSQL で 1,000 ページ目）の参考の測り（U1 R-04）

`perf/README.md` の「他テーブル参照の読み取りの場面」の流す順の (3) は「深いページのピッカー（PostgreSQL で 1,000 ページ目）を参考に1回（判定の対象の外）」と書くが、`perf/k6/scenarios.js` のピッカーの場面は `page: 1` に固定で、ページを変える環境変数も手順も無く、手順書のとおりに実行できない。U1 の NFR 設計（`performance-design.md` 6節）はこの参考の値をこの段に渡している。

**推奨: A**。理由: 合否の目標が無い参考の測りで、深い OFFSET の重さは既存の `bizSearchLast`・`bizSearchLastSorted`（2 秒の線）で測っている。場面を足すと台本の変更・`k6 inspect` と `thresholds-test.sh` の流し直し・コミットが要り、手間に見合わない。手順書が実行できない状態は A で解ける。

A. 手順の (3) を `perf/README.md` から外し、経緯を履歴に書く（文書だけの変更。Q2 の短命のブランチか、この段の記録と一緒に入れる）。深いページのピッカーの測りが要るかは、後の Intent の要件で決める

B. ピッカーの場面にページを変える環境変数（例 `REF_PICKER_PAGE`、既定 1）を足し、Performance Validation の PostgreSQL の回で 1,000 ページ目を参考に1回流す（判定の対象の外）。台本の変更は Q2 の短命のブランチに入れ、`k6 inspect` と `thresholds-test.sh` を流し直す

X. Other (please specify)

[Answer]: A

## 質問の数について

Standard の目安（5〜8）の中の6問にした。持ち主の段・不安定なテストの扱い・CI の記録の仕方・U4 の H1〜H7・MySQL の 1038・種の 0 行の確かめは、`team.md`・`project.md`・承認済みの設計と前の段の依頼者の決定で決まっているため、冒頭の「決まっていること」に書いた。
