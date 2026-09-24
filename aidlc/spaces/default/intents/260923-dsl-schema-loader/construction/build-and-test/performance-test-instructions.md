# 性能テストの手順（performance-test-instructions）

Intent `260923-dsl-schema-loader` の時間と資源の目標を、どこで・どう測るか。依頼者の決定（Q2: B）により、**1回ずつの時間はこの段（Build and Test）で測り**、95 パーセンタイル（NFR1.10）と同時の実行（NFR1.12・U4-POOL）は **Performance Validation** に引き継ぐ。測る台本と使い捨ての環境はリポジトリに足した（Q3: A）。手順の正本は `perf/README.md` の「DSL の時間を測る」「追加の確かめ」「k6 の DSL の場面」の節である。

## 1. 決めごと

- **使い捨ての環境で測る**: 配備した環境とは別に、仮の署名鍵・仮の管理者・対象DB のパスワードを乱数で作った一時の環境（`docker/perf/compose.yaml`、プロジェクト `mastersmith-perf`）を立て、終わったら消す。配備したアプリ・その内部DB・監査ログには触れない（project.md の Testing Posture）。
- **資源の条件**: コンテナの上限は配備と同じ **CPU 4・メモリ 2g**（Q4: A）。U4 の NFR1.12 の条件は 1g のコンテナ（ヒープ 75%）で、これと違うことを結果に明記する。1g で測るときは `MASTERSMITH_CONTAINER_MEMORY=1g` を付ける。
- **配備したアプリは止めない**: colima の VM（CPU 4・6GiB）には、配備したアプリ（2g）・使い捨てのアプリ（2g）・対象DB 1つが同時に収まる。対象DB は1種類ずつ起動する。k6 で CPU を使い切るときは配備したアプリを止める（`perf/README.md` の手順 0）。
- **時間は画面を含まない API の応答**（curl の `time_total`）で測る。画面の時間（U5）は Chromium（画面なし）で別に測る。
- **目標を緩めない**: 古い記述との差（下の 5節）は、目標の値を変えずに実測で判定する。止まる・極端に遅いなどが出たら、起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する（project.md の Testing Posture）。

## 2. この段で測ったもの（1回ずつの時間）

### 2.1 測り方

```bash
# 0. WAR と、使い捨て用のタグのイメージを作る（配備に使うタグ local は上書きしない）
./gradlew :backend:bootWar && docker build -t mastersmith:perf-dsl .

# 1. 3種類の対象DB それぞれで、100 テーブル × 100 カラムのスキーマを作って測る（1種類 3〜4 分）
MASTERSMITH_IMAGE_TAG=perf-dsl ./perf/dsl-timing.sh postgres mysql mariadb

# 2. 画面の時間（frontend/ で npm ci と npx playwright install chromium を済ませておく）
MASTERSMITH_IMAGE_TAG=perf-dsl ./perf/dsl-timing.sh --ui postgres

# 3. 追加の確かめ（英語の表示・重い正規表現・保存の量）
MASTERSMITH_IMAGE_TAG=perf-dsl ./perf/dsl-timing.sh --lang --pattern --storage postgres
```

- 台本（`perf/dsl-timing.sh`）は、対象DB を profile（`targetdb-postgres`・`targetdb-mysql`・`targetdb-mariadb`）で起動し、`docker/targetdb/generate-large-schema.sh` でスキーマ `large`（100 × 100、外部キーとコメントあり）を作り、生成・表示（照合を含む）・ダウンロード・適用・投入・戻しを1つずつ順に送る。要求ごとにログインし直す。
- 10MB ちょうど（10,485,760 バイト）の DSL は `perf/make-large-dsl.mjs` が作る（正しいもの・誤りが多数のもの・末尾に意味の誤り1件のもの）。重い正規表現の DSL は `perf/make-pattern-dsl.mjs`。どちらもリポジトリには置かず、`build/perf-results/` の下にだけ作る。
- 生成の内訳は、アプリのログの DEBUG（`readMillis`・`buildMillis`・`writeMillis`・`validateMillis`・`bytes`）で読む。ヒープは GC の記録、コンテナのメモリは cgroup の `memory.peak`・`memory.current`・`memory.stat` で読む。まとめは `perf/dsl-timing-report.mjs`。
- 画面の時間は `perf/ui/dsl-ui-timing.mjs`（`--ui`）、英語の表示は `perf/ui/dsl-ui-lang.mjs`（`--lang`）。

### 2.2 結果（2026-09-24、上限 CPU 4・メモリ 2g。時間はコミット 39f9aeb、保存の量は Loop-back 1 の後の 8961cb2）

| Target ID | 目標 | 実測 | 判定 | 結果の置き場 |
|---|---|---|---|---|
| NFR1.6 | 既定の DSL の生成が 100 × 100 で 30 秒以内（3種類とも） | 1.54〜2.64 秒（3種類 × 3回と画面用の1回） | Met | `build/perf-results/dsl-run1/summary.md` |
| NFR1.7 | 30 秒の内訳（読み取り 15 秒・組み立てと書き出し 5 秒・検証 3 秒・保存と応答 2 秒） | 読み取り 25〜296ms・組み立てと書き出し 426〜767ms・検証 419〜832ms。全体 1.54〜2.64 秒 | Met | 同上（生成の内訳の表） |
| NFR1.4 | 想定の規模の既定の DSL の読み込み・検証・モデル作りが 3 秒以内 | 419〜832ms（6,146,161〜6,383,161 バイト、コメントあり） | Met | 同上（`validateMillis`） |
| NFR2.5 | 100 × 100 で生成した DSL が 10MB の内 | 6,146,161〜6,383,161 バイト | Met | 同上（`bytes`） |
| NFR1.5 | 10MB の DSL の読み込みと検証が 10 秒以内（誤りの有無によらず） | 0.82〜1.14 秒（正しいもの・誤りが多数・末尾に1件）。区間の GC 直前のヒープの最大 1,237MB | Met | 同上 |
| NFR1.8 | 表示（照合を含む）・投入・戻しが 10 秒以内（3種類、10MB の DSL を含む） | 表示 0.045〜0.200 秒、投入 0.57〜1.04 秒、戻し 0.54〜1.00 秒 | Met | 同上 |
| NFR1.11 | ダウンロード（プレビュー中・適用中）が 10MB でも 2 秒以内 | 0.032〜0.038 秒 | Met | 同上 |
| NFR1.18 | 画面を開いて今の状態とプレビューが出るまで 3 秒以内（照合を含めて 11 秒以内） | 今の状態 0.045〜0.113 秒・プレビュー 0.22〜0.29 秒（照合を含む。照合を除くと 0.06〜0.13 秒） | Met | `build/perf-results/dsl-run2-ui/postgres/ui-timing.json` |
| NFR1.19 | 違いの表の行（100 カラム）を開いてから 0.5 秒以内 | 25ms | Met | 同上 |
| NFR1.20 | 10MB のファイルを選んでから送り始めるまで 2 秒以内 | 553〜664ms | Met | 同上 |
| U2-PATTERN-COMPILE | 1,000 文字の重い正規表現の組み立てが短く終わり、続く投入が遅れない | 重い DSL（`pattern` 2,000 個）の投入 0.34〜0.44 秒、直後の普通の投入 0.228 秒（前 0.22〜0.29 秒・後 0.21〜0.22 秒）。打ち切りと拒否のログ 0 件 | Met | `build/perf-results/dsl-run4-extra/summary.md` |
| U5-LANG-E2E | 英語のロケールで、サーバーの `message` と画面の警告・誤りの一覧が英語 | `pass: true`（照合の警告と 422 の誤りの一覧の両方） | Met | `build/perf-results/dsl-run4-extra/postgres/ui-lang.json` |
| U4-STORAGE-RUN | 動いている間の最大の状態（プレビュー1件と履歴 20 件がすべて 10MB）で、H2 のファイルが約 210MB の内、コンテナのメモリの上限の内 | `8961cb2`（`DEFRAG_ALWAYS=TRUE`）で、21 回 267.4MB、プレビューを置いて 278.2MB（2回とも同じ）、40 回 483.1MB。1回に約 10.78MB 増え、頭打ちなし。`memory.peak` は上限 2g に張り付き、`anon` の最大 1,959〜1,986MB、OOMKilled なし | **Not Met** | `build/perf-results/dsl-lb1-storage21-defrag/`・`dsl-lb1-storage21-defrag-2/`・`dsl-lb1-storage40-defrag/` |
| U4-STORAGE-RESTART | 止めて起動し直した後、H2 のファイルが生きているデータの量に戻り、データが無事 | `DEFRAG_ALWAYS=TRUE` で 15.9MB（21 回・40 回とも）、なしでは 278.2MB のまま。止めるのに 0.55〜1.00 秒、ExitCode 143、healthy まで約 8.7 秒、データは前後で一致（戻し 20/20） | Met | 同上と `build/perf-results/dsl-lb1-storage21-nodefrag/` |

- 資源（`dsl-run1`）: コンテナのメモリの最大（`memory.peak`）1,732〜1,869MB、確保したヒープの最大 1,324〜1,472MB、3種類とも OOMKilled なし。
- U4-STORAGE は、直す前（`39f9aeb`）に3回再現した（`dsl-run3-extra`・`dsl-run4-extra`・`dsl-run5-storage40`。起動し直しても縮まない）。Loop-back 1 で内部DB の既定の接続先に `DEFRAG_ALWAYS=TRUE` を足し（`8961cb2`）、2.4 の方法で測り直した。起動し直した後は縮むようになった（RESTART は Met）が、動いている間の増え方は変わらない（RUN は Not Met）。試験の 10MB の DSL は gzip で 0.29MB に縮むデータで、圧縮の効かない本文では起動し直した後も約 210MB＋α に近づくと推定している（実測ではない）。詳しくは `test-results.md` 3.2。
- 大きさの MB は 10^6 バイト。`summary.md` の保存の量の表は 2^20 バイトで割った値（278.2MB は表の 265.3、483.1MB は 460.7、15.9MB は 15.2）。
- `dsl-run3-extra` は英語の表示の確かめが台本の待ち（`locator.waitFor` の時間切れ）で `pass: false` になったため、台本を直して `dsl-run4-extra` でやり直した。判定には `dsl-run4-extra` を使った。
- `dsl-run1`・`dsl-run2-ui` のイメージは `sha256:9794f400…`、`dsl-run3-extra` 以後は作り直した `sha256:8288782c…`。アプリのソースはどちらも 39f9aeb から変わっていない（未コミットの変更は E2E・`perf/`・README・`docker/perf/compose.yaml` だけ）。

### 2.3 NFR1.7 の内訳の読み方

`TargetSchemaDslGenerator` の DEBUG のログ（`build/perf-results/dsl-run1/<種類>/generate-breakdown.log`）の最大は次のとおり。いずれも内訳の予算の内である。

| 区間 | 予算 | 最大の実測 |
|---|---|---|
| U1 の読み取り（`readMillis`） | 15 秒 | 296ms（MySQL の1回目） |
| 組み立てと書き出し（`buildMillis`＋`writeMillis`） | 5 秒 | 767ms（PostgreSQL の1回目） |
| U2 の検証（`validateMillis`） | 3 秒 | 832ms（MySQL の1回目） |
| 保存と応答（全体から上の3つを引いた残り） | 2 秒 | 全体 2.64 秒のうち約 0.8 秒 |

### 2.4 保存の量の測り方（U4-STORAGE、Loop-back 1 の後）

```bash
# 0. 測る版のイメージを作る（配備のタグ local は上書きしない）
./gradlew :backend:bootWar && docker build -t mastersmith:perf-dsl-lb1 .

# 1. 既定の接続先（DEFRAG_ALWAYS=TRUE）で、10MB の DSL の投入→適用を 21 回（既定）くり返し、最後にプレビューを置く
MASTERSMITH_IMAGE_TAG=perf-dsl-lb1 ./perf/dsl-timing.sh --storage postgres

# 2. 履歴 20 件を超えた後の増え方（40 回）
STORAGE_ROUNDS=40 MASTERSMITH_IMAGE_TAG=perf-dsl-lb1 ./perf/dsl-timing.sh --storage postgres

# 3. 比べるため、DEFRAG_ALWAYS なしの接続先で
PERF_DB_URL=jdbc:h2:file:/app/data/mastersmith MASTERSMITH_IMAGE_TAG=perf-dsl-lb1 ./perf/dsl-timing.sh --storage postgres
```

- `--storage` は、10MB の DSL（埋め草の先頭の行に回の番号を入れ、大きさは同じ）の投入→適用を `STORAGE_ROUNDS` 回（既定 21）くり返し、最後にプレビューを1件置く（プレビュー1件と履歴 20 件の最大の状態）。回ごとに H2 のファイル（`/app/data/mastersmith.mv.db`）の大きさ、コンテナのメモリ（`memory.current`・`memory.peak`、`memory.stat` の `anon` と `file`）、履歴の件数を `storage.tsv` に記録する。
- 最後にアプリを `docker compose stop`（`stop_grace_period` 45 秒）で止め、止めるのにかかった秒数と終わり方（SIGTERM で正常に終われば 143、猶予切れや OOM の SIGKILL は 137）を `stop-state.txt` に、止めている間のファイルの大きさを `data-while-stopped.txt` に、起動から healthy までの秒数と起動し直した後の大きさを記録する。
- 止める前と後のデータ（適用中の DSL とプレビューの DSL の SHA-256、プレビューの識別、履歴の版・`dslHash`・件数）を `snapshot-before-stop.txt`・`snapshot-after-restart.txt` で比べ（`data_intact`）、履歴のすべての版を戻してプレビューの本文の SHA-256 が `dslHash` と一致する数（`restore_all_match`）を記録する（詰め直しで本文が壊れていないか）。
- `PERF_DB_URL` は内部DB の接続先（`MASTERSMITH_DB_URL`）を上書きする。指定しなければ `application.yaml` の既定（`DEFRAG_ALWAYS=TRUE`）で動く。使った値は `env.txt` の `db_url` に残る。
- `memory.current` はページキャッシュを含むため、H2 のファイルへの書き込みで上限の近くまで上がる。止まるかどうかは `anon` と `state.txt`（OOMKilled）で見る。
- 手順の正本は `perf/README.md` の「追加の確かめ」（台本と手順は Loop-back 1 で直した）。

## 3. Performance Validation に引き継ぐもの

| Target ID | 目標 | k6 の場面 | この段での状態 |
|---|---|---|---|
| NFR1.10 | 今の状態・履歴・破棄・適用の 95% が 1 秒以内（想定の規模の DSL が適用中・プレビュー中、履歴 20 件） | `dslLight`（今の状態と履歴を同時 `VUS` で）・`dslCycle`（投入 → 適用 → 投入 → 破棄 → 履歴、`VUS=1`） | Unverified。台本が動くことだけ確かめた |
| NFR1.12 | 10MB の DSL を1つ処理するとき、1g のコンテナ（ヒープ 75%）で、ほかの利用（ログイン）と同時でも失敗しない | `dslMixed`（10MB の投入と表示に、別々の利用者のログインを重ねる）。コンテナの上限 1g で流し、OOMKilled の有無と NMT を記録 | Unverified |
| U4-POOL | 適用などの操作で、内部DB のプール（上限 30）をログインと共有しても尽きない | `dslMixed` と同じ実行で、Hikari の使用中・待ちの数とエラーの有無を記録 | Unverified |

- 台本: `perf/k6/scenarios.js` に `dslLight`・`dslCycle`・`dslMixed` を足した。`k6 inspect` で全場面を読み込めること、`dslLight`・`dslCycle` が使い捨ての環境で 10 秒ずつ動くことを確かめた（`build/perf-results/dsl-k6-smoke/`）。条件（履歴 20 件など）をそろえていないため、**目標の判定には使わない**。参考の値は `dslLight` の今の状態 p95 1.1ms・履歴 p95 1.07ms、`dslCycle` の適用 p95 4.71ms・破棄 p95 10.15ms（失敗 0）。
- 流し方は `perf/README.md` の「k6 の DSL の場面」。`KEEP=1` で使い捨ての環境を残し、同じ一時ディレクトリの `ui.env` で `grafana/k6:2.3.0` を流す。`dslMixed` には試験用の利用者 10 名と `PERF_USER_PASSWORD` が要る。
- NFR1.12 は、この段の 2g の測定でも 10MB の処理の間の `memory.peak` が 1,732〜1,869MB に達しており、1g では余裕が無い見込みが高い。U4-STORAGE-RUN の Not Met（動いている間はメモリが上限に張り付き、`anon` が上限の約 92%）と合わせて、Performance Validation で最初に確かめる。
- 要求1件で接続を2本使う経路は、U4 の実装で出来事をトランザクションの外で出す形にして無くした（U4 の Code Generation の記録）が、project.md の決まり（2本使う経路は負荷の試験で確かめる）に当たるかを U4-POOL で確かめる。

## 4. 監視と時間の関係

- 指標 `mastersmith.dsl.operation`（タグ `operation`・`outcome`）は、操作の終わりに1回記録される（単体テストの `DslLifecycleTest` で確認済み）。ダッシュボードの式とログ（Loki）での絞り込みの確かめは Observability Setup に引き継ぐ（Q8: B）。

## 5. 古い記述との差（目標は緩めない）

| 項目 | 古い記述 | 実際 | 扱い |
|---|---|---|---|
| 想定の規模の既定の DSL の大きさ | 約 5.3MB（U2・U3 の NFR 要件の試算）。まとめの確認では約 6.7MB（U3 の Code Generation の記録、コメント無し） | 6,146,161〜6,383,161 バイト（約 6.15〜6.38MB、コメントあり、3種類） | 実測の大きさで NFR1.4・NFR2.5 を判定した。10MB の上限までの余裕は約 4MB |
| U4 の要件の「生成は接続 5 秒」 | 5 秒（`u4-dsl-management/nfr-requirements/reliability-requirements.md` 2節） | 3 秒（U1 の NFR 設計の差、既定 `MASTERSMITH_TARGET_DB_CONNECT_TIMEOUT=3s`） | 実装と承認済みの設計を正とする（project.md の Way of Working） |
| 照合の全体の時間 | 10 秒以内（NFR1.8） | 決定 B により全体の上限は作らず、応答しない対象DB では最悪 23〜28 秒を許す | NFR1.8 の判定は正常なときの時間だけ。応答しない場合は結合テスト（`DslTargetDbIT`）の打ち切りと警告で確かめた |
| NFR1.12 のコンテナの上限 | 1g | 配備は 2g。この段の測定も 2g（Q4: A） | 1g での確かめは Performance Validation |

## Sources

- `perf/README.md`（「DSL の時間を測る」「追加の確かめ」「k6 の DSL の場面」）・`perf/dsl-timing.sh`・`perf/make-large-dsl.mjs`・`perf/make-pattern-dsl.mjs`・`perf/dsl-timing-report.mjs`・`perf/ui/`・`perf/k6/scenarios.js`
- `build/perf-results/dsl-lb1-storage21-defrag/`・`build/perf-results/dsl-lb1-storage21-defrag-2/`・`build/perf-results/dsl-lb1-storage40-defrag/`・`build/perf-results/dsl-lb1-storage21-nodefrag/`（Loop-back 1 の後の保存の量）
- `backend/src/main/resources/application.yaml`（内部DB の既定の接続先）
- `build/perf-results/dsl-run1/summary.md`・`build/perf-results/dsl-run2-ui/`・`build/perf-results/dsl-run3-extra/`・`build/perf-results/dsl-run4-extra/summary.md`・`build/perf-results/dsl-run5-storage40/`・`build/perf-results/dsl-k6-smoke/`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/build-and-test/build-and-test-questions.md`（Q2・Q3・Q4・Q8）
- 目標の一覧の下書き（この段の Step 1 で作ったもの。リポジトリの外の一時ファイル）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/u4-dsl-management/nfr-requirements/performance-requirements.md`・`scalability-requirements.md`・`reliability-requirements.md`、`aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/u3-default-dsl-generation/nfr-requirements/tech-stack-decisions.md`（目標の出どころ。目標の一覧を通して読んだ）
- `aidlc/spaces/default/memory/project.md`（Testing Posture の負荷の試験の決まり）

## Assumptions & Open Questions

- U4-STORAGE-RUN（動いている間の増え方）をさらに直すか（例: 適用の後の詰め直し、履歴の本文の持ち方の見直し）、既知の制約として記録して先へ進むかは依頼者の判断が要る（`test-results.md` 3.2）。圧縮の効かない本文での起動し直した後の大きさは実測していない。
- 測定は各種類 1回ずつ（生成は 3回＋1回）で、ばらつきの幅は小さいが統計の判定ではない。
