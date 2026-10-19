# 生成の記録 — U5 u5-carryover-tests（Bolt B1）

計画（`aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/code-generation/code-generation-plan.md`）の Step 1〜16 を行った記録と、計画に無い判断・計画との差を書く。計画の文は書き換えていない（チェックボックスの印だけ）。コミットと push はしていない。Step 17（コミット）と Step 18（統合）は指揮役が依頼者の承認を得て行う。

## 1. Step ごとの結果

### Step 1: 作業の準備と走らせ方の確かめ

- `aidlc/` の記録のコミット（A1）は指揮役が `05a9b37` で済ませた。その後も監査の記録 `aidlc/spaces/default/intents/261016-business-data-views/audit/sakura-local-4e42a93f87ce.md` にフックの追記が続いており、コミットしていない（統合の前に指揮役が積む。計画の Step 18）。
- git の対象外の生成物: `frontend/test-results/` を消した。`frontend/playwright-report/` は無かった。
- B2・B3 の統合の後の CI（4.5。読み取りだけ）:
  - B2 の `ea23bf3`（run 39196260878）: 1回目 failure・2回目 success。1回目に落ちたのは `PatternCheckerTest`（「valid and invalid regular expressions are told apart by compiling only」）の1件。`AdminForbiddenProvider.test.tsx` は 12 件通過。
  - `0427d45`（run 39172328548）: 1回目 failure・2回目 success。1回目に落ちたのは `PasswordChangePage.test.tsx` の「drops the answer when the screen is left while sending」の1件。`AdminForbiddenProvider.test.tsx` は 12 件通過。
  - どちらの試行でも `RoleConcurrencyIT` と `AdminForbiddenProvider.test.tsx` は落ちていない。B3（`e34c260`〜`1e78b51`）は push されておらず CI が無い（`origin/develop` は `1913503`）。
  - 対象の外の2件（別々のテスト）が1回目に落ちて再実行で通っている。team.md の「不安定なテストと CI の失敗」の決まりの扱いを依頼者に確かめたい（code-summary.md の 6節）。
- ブランチ: `develop`（`05a9b37`）から `feature/261016-business-data-views-b1` を作って移った。
- 走らせ方の確かめ（`unit-test-instructions.md` 3.1、変更の前）: `LargeRoleTransferYamlTest` 3件、`RoleConcurrencyIT` 4件、`AdminForbiddenProvider.test.tsx` 12件がすべて通過。

### Step 2・3: テストの補助と単体テスト

- `TestRoleBarrier`: `Gate.arrivedThread()`（止まったスレッド。`arrived.countDown()` の直前に記録）、`Signal.passedThread()`（最初に通ったスレッドだけを `compareAndSet` で記録）を足した。止める・通すの振る舞いと既存の口は変えていない。
- `AuditWriteReorderBarrierConfig`（新）と入れ子の `AuditWriteReorderBarrier`: 口は `armFor(Thread held, Thread releaser, Duration waitLimit)`（A3）・`heldArrived()`・`releasedByTimeout()`・`disarm()`。状態は1つのロックで守り、準備の回の番号で前の回の待ちを必ず放す。止める相手と放す相手が同じスレッドのとき・上限が正でないときは `IllegalArgumentException`（文はスレッドの名前と上限の値だけ）。
- `RoleActors.Actor`: `toString` を `Actor[userId=<数>, email=***, token=***]` にした。
- 単体テスト（`--rerun`）: `AuditWriteReorderBarrierTest` 7件、`TestRoleBarrierTest` 3件、`RoleActorsTest` 2件がすべて通過。順は `CountDownLatch` とスレッドの状態（`TIMED_WAITING`）で作り、`sleep` は使っていない。上限の場合だけ 200 ミリ秒を渡した。

### Step 4: 二段の1段目（直す前の確かめ方で落ちること）

- 入れ替えた順の場合を足し、確かめ方は直す前の形（ID の順で1行目・2行目を決め打ち）のまま、そのメソッドだけを流した（作業フォルダの中だけ。コミットしない）。
- 1回目は `armFor` が「止める相手と放す相手が同じスレッドです: http-nio-auto-1-exec-2」で止まった。原因と直し方は 2節の N-2（知らせを先の保存が止まった後に作る）。
- 直した後の結果（1回で起きた）:
  - 失敗の文: `Expecting actual: "{"roleName":"同時の保存-1-2289630e","changes":[{"target":{...},"before":{"main":"READ",...},"after":{"main":"FULL",...}}]}" to contain: ""before":{"main":null"`（`detail` の値だけで、トークン・メールアドレスは出ていない）
  - 待ち合わせ: `heldArrived=true`・`releasedByTimeout=false`。止めた相手は `http-nio-auto-1-exec-2`（先の保存）、放す相手は `http-nio-auto-1-exec-3`（後の保存）。
  - 監査の行: 「後: FULL」の行が ID 2、「後: READ」の行が ID 3（入れ替えが実際に起きた）。後の保存は 204。
- 止める相手は印（`AFTER_LOCK` の `Gate`）で特定できた。出来事の中身で特定する形への切り替えは要らなかった。

### Step 5: 二段の2段目（直した確かめ方）

- #7 を自然の順（止める相手 = 後の保存、放す相手 = 先の保存）と入れ替えた順の2つのメソッドにした。どちらも同じ待ち合わせで確実に作る（D2: A）。
- 確かめ: 対象のロールの `ROLE_PERMISSION_CHANGED` がちょうど2行で、「前が空・後が READ」と「前が READ・後が FULL」がちょうど1行ずつ（ID の順は合否に使わない）、最後の主権限が FULL。場合ごとの前提として `heldArrived` 真・`releasedByTimeout` 偽・ID の順がその場合に作った順。
- 409: 入れ替えた順は待ち合わせの結果の確かめと「409 でないこと」を 409 の分岐より前に置いた（D1: A）。自然の順は今の分岐（`ROLE_BUSY` の code だけ）のまま。
- 結果を待つ順: 放す相手の結果を先に待つ（入れ替えた順は後の保存 → 先の保存、自然の順は先の保存 → 後の保存）。
- `@Import` に `AuditWriteReorderBarrierConfig` を足し、`@AfterEach` で `reorderBarrier.disarm()` を `barrier.reset()` の前に呼ぶ。
- 読み合わせ（R-05）: `admin.token()` は `api.save` の引数だけで、失敗の文・`.as(...)` に渡していない。`.as` に出すのは `detail` の一覧・真偽の説明・409 のときの code だけ。
- `RoleConcurrencyIT` の5件（#7 の2件、#1 の2件、#4 の1件）がすべて通過。

### Step 6: 繰り返し

- `RoleConcurrencyIT` を `--rerun` で5回流し、5回とも5件が通過（1回 8.25〜8.36 秒、21:24:53〜21:25:45 JST）。
- 最後の回の出力に `ROLE_BUSY` は 0 件（409 の分岐に入っていない）。WARN の `GlobalExceptionHandler` 3件は #1・#4 の想定内の 4xx。
- 入れ替えは Step 4 で起こせたため、30 分の再現の試みは要らなかった。

### Step 7: 画面のテストの試し（コミットしない）

- T1: `Probe` の `useLayoutEffect` で最初の1回だけ `effectRuns` を写し、対象のテストの写す値をそれに替えて流した。5回とも `AssertionError: expected 1 to be +0` で落ちた（`-t 'keeps the same function'` で対象の1件だけを流した。1 failed・11 skipped）。
- T2: T1 の `Probe` の変更を残し、写す前に `await waitFor(() => expect(effectRuns).toBe(1))` を置いた。5回とも通過（1 passed・11 skipped）。
- 試しの変更を `git checkout` で戻し、`git diff --exit-code` で差が無いことを確かめた。

### Step 8: 画面のテストの直し

- 対象の1件だけ、`findByTestId` の後に `await waitFor(() => expect(effectRuns).toBe(1))` を置いてから `before`・`runsBefore` を写す形にした。403 の後の確かめは保ち、時間の上限は延ばしていない。Prettier の確かめは通過。
- ファイルの12件を5回流し、5回とも12件が通過（1回 0.93〜0.99 秒）。

### Step 9・10: `PERF_DB_URL` の確かめ

- 関数 `check_perf_db_url` を `perf/lib/perf-db-url.sh` に書いた。止めたときは決まりの名前（前の部分・使えない文字・空の要素・許さない設定・重なり）だけを標準エラーに出す。
- `perf/dsl-timing.sh` の引数の確かめの直後・既存の環境の確かめの前の最上位で `source` して呼び、外れたら `exit 1`。冒頭の注記に受け付ける形を書き足した。144〜145 行（今は 147〜148 行）の書き込みは変えていない。
- 試しの台本 `perf/lib/perf-db-url-test.sh`: 19 件（悪い入力 15・正しい入力 4）がすべて期待どおり、値の漏れ（目印の語と `jdbc:` の件数）は 0 件。
- 親が止まることの確かめ: `PERF_DB_URL='jdbc:h2:tcp://example.invalid/x' ./perf/dsl-timing.sh postgres` は終了コード 1（「前の部分」）、`~/.mastersmith-perf-*` の前後の差は無し（前も後も 0 件）。改行を含む値でも終了コード 1（「使えない文字」）。Docker は使っていない。

### Step 11・12: 記録のループと1回の測りの台本

- `perf/dsl-mixed-memory.sh` を書いた（流れは台本の冒頭と `perf/README.md` の (b) 節）。記録のループだけを流す `--loop-only <コンテナ> <秒> <TSV>` も持つ（2節の N-5）。
- 構文: `bash -n` で `perf/dsl-timing.sh`・`perf/lib/perf-db-url.sh`・`perf/lib/perf-db-url-test.sh`・`perf/dsl-mixed-memory.sh` が通った。
- 空回し（A2・D5: A）: `--loop-only mastersmith-app-1 5 build/perf-results/u5-loop-dryrun/memory.tsv`。見出しと5行（12:32:02Z〜12:32:06Z）が書かれ、`NA` は 0 件。ループの終わりの後に `docker top` に `sleep 1`・`bash -c` の処理は残っていなかった。
- `anon` の列の確かめ:
  - 計画の形（同じ秒に別の `docker exec` で `grep '^anon '` を読む）では、5秒とも約 0.2 MB 違った（例 695406592 と 695218176）。別々の `docker exec` の処理が同じ cgroup の `anon` を変えるためと見られ、値の大きさは `anon` で、ほかの列（`file` は 741376 など）とは桁が違う。
  - 列の取り違えを確実に見るため、同じ時点の `memory.stat` の写し1つから、ループと同じ awk の取り出しと `grep` の値を並べた。5つのキー（anon・file・inactive_file・active_file・kernel）すべてが一致した（`build/perf-results/u5-loop-dryrun/stat-snapshot-check.txt`）。
- アプリのイメージに `bash`・`awk`・`stat`・`date` があることも空回しで確かめた（Assumptions の3つ目）。
- 結果の置き場は `build/perf-results/u5-loop-dryrun/`（git の対象外。コミットしない）。

### Step 13: `perf/README.md`

- DSL の節の `PERF_DB_URL` の行に、受け付ける形・止め方・試しの台本を書き足した。
- (b) 節の終わりに「`dslMixed` の `memory.peak` と `memory.stat` の記録（Intent 261016-business-data-views の U5）」を足した（合否・並べる値・列・1回の流れ・記録の置き場・後置きの欠け・設定の値の確かめ・環境の分け方・一時ファイルと秘密・手順 1〜6・秘密の検索・片付けと消えたことの確かめ）。
- 照らした名前: `k6d` のイメージ（`grafana/k6:2.3.0@sha256:9c2dee7f…`）・`ui.env`・`PERF_USER_PASSWORD`・`mastersmith-perf_perf-data`・`mastersmith-perf_default`・H2 の `Shell` の形・`valid-10mb.yaml`（`perf/dsl-timing.sh` 314 行）・`htpasswd`（この PC にある）。

### Step 14: 1コマンドの検査

- `caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（colima の2つの変数を渡した）: BUILD SUCCESSFUL、17分38秒（12:34:39Z〜12:52:18Z）。
- 件数: バックエンドの単体 2,370 件（失敗 0）、結合 1,254 件（失敗 0・飛ばし 4）、画面 188 ファイル・1,633 件（失敗 0）。
- 飛ばした 4 件は `AbstractBusinessDataReadIT` の `assumeTrue`（DB の種類ごとの前提。PostgreSQL だけ・MySQL と MariaDB だけの確かめ）で、コンテナの実行環境の不足ではない（ログに「コンテナの実行環境」の警告は 0 件）。
- カバレッジ: バックエンドの全体 行 97.8%・分岐 91.8%、フロントエンドの全体 行 95.37%・分岐 90.39%（下限 80%・70%）。
- 眠り: 流す前に `pmset -g assertions` で `PreventUserIdleSystemSleep`・`PreventSystemSleep` が 1、AC 電源。流した後の `pmset -g log` の Sleep・DarkWake の最後は 2026-10-18 で、検査の窓には無かった。

### Step 15: 変えていないことの確かめ

- 変えたファイル（`develop` との差と、追跡していない新しいファイル）は 1.1・1.2 の表のものと、計画のチェックボックス・監査の記録（`aidlc/`）だけ。
- `backend/src/main`・`frontend/src` のテストでないファイル・`application.yaml`・`Dockerfile`・`compose.yaml`・`docker/`・`.env.example`・`build.gradle.kts`・`backend/build.gradle.kts`・`frontend/vitest.config.ts`・lockfile・`vendor/`・`.github/` の差は 0 件。
- 新しい7つのファイルの先頭に Apache License 2.0 のヘッダー（2026、agwlvssainokuni）があることを確かめた（Java は `/* ... */`、シェルは `#`）。

## 2. 計画に無い判断・計画との差

- **N-1 監査の記録**: フックが書き続ける監査の記録の追記はコミットしていない（指揮役が統合の前に積む）。
- **N-2 後の保存の知らせを作る時点**（Step 4・5）: 計画の流れは今の #7 と同じく、`BEFORE_LOCK` の知らせを先の保存を始める前に作る形だった。先の保存も `BEFORE_LOCK` を通るため、知らせの最初の通過は先の保存のスレッドになり、`second.passedThread()` が止める相手と同じになった（`armFor` が拒否）。知らせを `first.awaitArrival()` の後に作る形に替え、印で特定する形のまま進めた。あわせて、今までの #7 では `second.awaitPassed()` がすぐ戻り、後の保存が排他の直前の点（`BEFORE_LOCK`）を通ったことも確かめていなかったと分かった。今の形で確かめるのは、後の保存がその点を通ったことまでで、排他を待っていることまでは保証しない（このテストは監査の書き込みの順と中身の確かめで、排他の待ちが働いたことの確かめではない。コードのレビューの R-01 で書き方を直した）。
- **N-3 イメージの固定先を増やさない**（Step 11・13）: `.github/dependabot.yml` の「Dependabot が見ない固定先」の一覧に無いファイルに、イメージの版とダイジェストを書かないため、`perf/dsl-mixed-memory.sh` は k6 のイメージを `K6_IMAGE`（必須。`perf/README.md` の `k6d` と同じ値を README の手順で渡す）で受け、H2 の `Shell` は測るアプリのイメージ（`mastersmith:<タグ>`、同じ JRE）で `--entrypoint java` で動かす。計画の 4.4 の R-02 が挙げた形（README 38〜40 行の eclipse-temurin のイメージ）とは、イメージだけが違う。README の利用者を入れる関数 `perf_users` は既存の形（一覧にある `perf/README.md` の eclipse-temurin）のまま。
- **N-4 設定の値の読み方の先の確かめ**（計画に無い試し）: 一時の docker のボリューム2つだけで、(1) 接続先の URL の `RETENTION_TIME=0` で作った DB の `INFORMATION_SCHEMA.SETTINGS` が 0、URL に無いときは 45000（H2 の既定）になること、(2) ボリュームを `:ro` で付け `ACCESS_MODE_DATA=r` で開いて読めること、を `mastersmith:local` のイメージで確かめた。一時のボリュームは消した（残り 0 件）。配備したアプリのボリュームには触れていない。Assumptions の2つ目（URL で渡せる）の裏付けになるが、使い捨てのアプリで効いたことは Build and Test で読む。
- **N-5 `--loop-only`**: 空回しを台本と同じループのコードで行うため、記録のループだけを流す口を足した。
- **N-6 空回しの `anon` の確かめ方**: 1節の Step 11・12 のとおり、計画の形では約 0.2 MB の差が出たため、同じ写しからの比べを足して列の正しさを確かめた。
- **N-7 k6 の出力**: 試験用の利用者の名前を含むため記録に残さず、一時ディレクトリに置いてログインの行の数と重なりの数だけを `k6.txt` に書き、すぐ消す（NFR4.6）。
- **N-8 無効の回**: 始める前の確かめに外れた回は `invalid.txt` に書いて終了コード 3 で止まる（README の手順は `|| exit $?` で止まる）。
- **N-9 走らせ方**: `unit-test-instructions.md` 3.2 の「親が止まることの確かめ」のコマンドは、zsh では一致しない glob（`~/.mastersmith-perf-*`）が誤りになり、前の一覧のファイルが作られなかった。同じコマンドを bash の台本で流した。また、この担当の環境では `bash -c`・変数を埋めた `python3 -c` がフックで止められたため、scratchpad の固定の台本で流した。
- **N-10 試しの単独の流し方**: Step 7 の T1・T2 は `-t 'keeps the same function'` で対象の1件だけを流した（ファイルの残りの 11 件は skipped）。Step 8 の5回はファイルの12件すべてを流した。
- **N-11 traceability**: `OK` の target は道1つでなければならないため、計画の 7節の「OK（部分）」の書き分けは code-summary.md の 4節に書いた。FR16.1〜FR16.4 の行は置かず、BR3・BR4 を通して2段の連鎖でたどる（`project.md` の学び）。

## 3. Build and Test へ引き継ぐこと

- `dslMixed` の `memory.peak` の測り（既定の2回・試しの2回。`perf/README.md` の (b) 節の手順）と判定（BR3.4・BR3.5）。台本 `perf/dsl-mixed-memory.sh` の1回の測りの全体は、配備したアプリを止める必要があるため Code Generation では流していない。最初の回で記録のファイルが揃っているか（特に `settings.txt` の値、`k6.txt` の `post_missing_seconds`）を確かめてから続ける。
- 試しの2回の設定の値（`RETENTION_TIME` が 0）の確かめ（BR4.1）と、結果による諮り（BR4.2・BR4.3）。
- 秘密の検索の件数（NFR4.5・NFR4.6）と、片付けで一時ディレクトリとボリュームが消えたことの確かめ。
- 配備したアプリを止める時間を、U1・U2 の k6 とまとめられるかの並べ方。
- E2E の全件（Intent の最後）。
- B1 の統合の後の CI の試行ごとの結果と時間（今回の verify は 17分38秒）。

## 4. コードのレビュー（READY・Minor 4）の直し

レビューの記録は `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/code-generation/units/u5-carryover-tests/bb969d769be0cb2e/1.json`。依頼者は N-2〜N-4 と bash での実行を受け入れ、CI の1回目だけ落ちた2件（`PatternCheckerTest`・`PasswordChangePage.test.tsx`）を「手元で再現しないもの」として記録すると決めた。あわせて、統合の前に R-01〜R-03 を直し、R-04 は持ち越すと決めた。直しは4つのコミット（`1d55103`・`ba84196`・`31fdb0d`・`b0ee373`）の後の作業フォルダで行い、コミットはしていない。

- **R-01**: N-2 の「後の保存が排他の直前に来たことを保証する」は言い過ぎだった。`BEFORE_LOCK` の知らせが確かめるのは、後の保存がその点を通ったことまでで、排他を待っていることまでは保証しない。この記録の N-2、`code-summary.md` の 6節の 1、`RoleConcurrencyIT` の Javadoc をそう直した。
- **R-02**: `RoleConcurrencyIT` の #7 の自然の順の場合も、後の保存が 409 `ROLE_BUSY` で返ったら失敗にした。待ち合わせの結果（`heldArrived` 真・`releasedByTimeout` 偽）と「409 でないこと」を、入れ替えた順と同じ位置（結果を待った直後、ほかの確かめより前）に置き、409 の code だけを確かめて通す分岐は消した（D1: A を自然の順にも当てる。BR1.3 との差は依頼者の決定）。`--rerun` で5回流し、5回とも5件通過（1回 8.89〜9.08 秒、22:37:22〜22:38:20 JST）、`ROLE_BUSY` は 0 件。
- **R-03**: `perf/dsl-mixed-memory.sh` を直した。
  - 記録の TSV は完全な行だけを使う（改行で終わらない最後の行と、9 列・時刻と数か `NA` の形でない行を捨てる）。まとめに捨てた数 `dropped_rows` を出し、最後の完全な行の時刻が読めなければ `post_missing_seconds=NA` にして続ける。`memory.peak`・`memory.stat` はループを止めた直後に先に読む。
  - `trap` を EXIT・INT・TERM に置き、手元の `docker exec` と、コンテナの中の目印（台本の1回ごとに一意の引数）を持つ記録のループの処理だけを止める。確かめで、手元の `docker exec` を止めてもコンテナの中のループは残ると分かったため、コンテナの中の目印の処理も止める形にした（アプリの処理には触れない）。
  - 確かめのため `--tsv-check <TSV>`（完全な行の数・最後の完全な行の時刻・まとめ。Docker を使わない）を足した。`--loop-only` も同じ `start_loop`・`trap` を通る。
  - 一時ファイルの一覧と `KEEP` の決まりは変えていない。`perf/README.md` の (b) 節に、後置きの欠けの `NA`・切れた行・ループの後始末の3点を書き足した。
  - 確かめ: `bash -n` が通過。切れた行を含む TSV 5つの形（列の足りないまま切れた最後の行・9 列だが改行で終わらない最後の行・途中の切れた行・見出しだけ・完全）を一時の場所に作り、`--tsv-check` がどれも終了コード 0 で、切れた行を捨てた（完全な形では `dropped_rows=0`、まとめは直しの前と同じ値）。配備したアプリのコンテナに読むだけで上限 30 秒のループを起こし、3 秒後に TERM・INT を送ると、台本は 143・130 で終わり、コンテナの中（`docker top`）と手元（`pgrep`）にループは残らなかった。1回目の試しでは、TERM で手元は止まったがコンテナの中のループが残り、INT は試しの台本がジョブ制御なしの `&` で起こしたため届かなかった（bash は後ろの処理の SIGINT を無視したまま起こす。試しの台本に `set -m` を付けて流し直した）。普通に終わる5秒の空回しも5行・`NA` 0 件・残り無し。結果は `build/perf-results/u5-trap-check/` と `build/perf-results/u5-loop-dryrun/memory-after-r03.tsv`（git の対象外）。
- **R-04**: 持ち越し（手を付けていない）。
- **直しの後の verify**: `caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` は BUILD SUCCESSFUL、17分44秒。単体 2,370 件・結合 1,254 件（既存の `assumeTrue` の飛ばし 4 件、コンテナの実行環境の警告 0 件）・画面 188 ファイル・1,633 件、失敗 0。カバレッジはバックエンド 行 97.8%・分岐 91.8%、フロントエンド 行 95.37%・分岐 90.32%。流す前の `pmset -g assertions` で `PreventUserIdleSystemSleep` が 1、流した後の `pmset -g log` に検査の窓の Sleep・DarkWake は無かった。
- `source-manifest.json` と `traceability.json` は、変えたパスが変わらないため直していない。
