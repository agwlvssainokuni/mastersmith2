# 計画の前の測定と前提の確かめ（260925-storage-memory-fixes）

この文書は、コード生成の計画（`code-generation-plan.md`）を書く前に行った2つの作業の記録である。

1. メモリの内訳の測定（要件 FR2.1。依頼者の決定 Q1: A により、計画の承認の場でメモリの目標を決めるための材料）
2. 内部DB の詰め直しの前提の確かめ（要件の「前提」と FR1.1・FR1.5 の方法の候補）と、`AccessTokenApiIT` の原因の候補 (b) の仕組みの確かめ（FR3.1 の準備）

リポジトリのソース・設定・文書は変えていない。試しは使い捨ての環境とリポジトリの外の一時ディレクトリで行い、終わったら消した。生の結果は `build/perf-results/storage-memory-base/` にある（コミットしない）。確かめた事実と、仮説（未検証）を分けて書く。

## 1. 測定の条件

| 項目 | 値 |
|---|---|
| 日時 | 2026-09-25 12:43〜12:54（配備したアプリを止めていた時間。約 10 分 25 秒） |
| ソース | `develop` の HEAD `9773cb9`（アプリのソースは配備した `87cc0fc` と同じ。その後のコミットはワークフローの記録だけ） |
| イメージ | `mastersmith:storage-memory-base`（`sha256:043b80c96b6f…`）。配備に使う `mastersmith:local` は上書きしていない |
| colima の VM | CPU 4・メモリ 6GiB |
| アプリのコンテナの上限 | CPU 4・メモリ 2g（配備と同じ。`MASTERSMITH_CONTAINER_MEMORY` の既定） |
| JVM | `Dockerfile` の既定（`-XX:MaxRAMPercentage=75.0`、最大ヒープ 1,536MiB、GC は G1）。比べの回だけ `MASTERSMITH_JAVA_OPTIONS` でヒープの上限を変えた |
| 使い捨ての環境 | `docker/perf/compose.yaml`（プロジェクト `mastersmith-perf`）、対象DB は PostgreSQL の1種類と 100 × 100 のスキーマ |
| 前段 | `KEEP=1 MASTERSMITH_IMAGE_TAG=storage-memory-base OUT_DIR=build/perf-results/storage-memory-base/timing caffeinate -i ./perf/dsl-timing.sh postgres`（前の Intent と同じ。すべての要求が期待どおりの状態コード。10MB の DSL を1回適用した状態になる） |
| 試験用の利用者 | `perf-user01`〜`11` を SQL で内部DB に直接入れた（ロックの状態の行は作らない。`perf/README.md` の手順 2 と同じ） |
| 負荷 | k6 `grafana/k6:2.3.0` の `dslMixed`（重い側 1 人が 10,485,760 バイトの DSL の投入とプレビューの表示を繰り返し、別々の利用者 10 名がログインを繰り返す）、`VUS=10`・`DURATION=120s`。前の Intent（260924-followup-fixes の Build and Test）と同じ |
| 秘密情報 | 署名鍵・仮の管理者・仮の利用者・対象DB のパスワードは乱数で作り、ホームの下の一時ディレクトリ（権限 700）に置いた。値は表示していない。終わったら `down -v` と一時ディレクトリの削除で消した。`.env`・`.env.targetdb` は開いていない |
| 配備したアプリ | 測る間だけ `docker compose stop app` で止め、終わったら `docker compose start app` で同じコンテナを起動し直し、healthy を確かめた。見本の対象DB（`mastersmith-targetdb-postgres-1`）には触れていない |

測り方:

- ヒープ: GC の記録（`-Xlog:gc:file=/app/data/gc-mixed.log:timemillis,uptime`）。GC の前と後の使用量と、確保している量。試験の後に `jcmd 1 GC.class_histogram`（完全な GC を起こす）で、生きているものの大きさを読んだ。
- ヒープ以外: Native Memory Tracking（`-XX:NativeMemoryTracking=summary`）。JRE のイメージに `jcmd` が無いため、同じ版の JDK のイメージ（`eclipse-temurin:25.0.4_7-jdk-noble`）をアプリのコンテナと PID の名前空間を共有して動かした（`perf/README.md` の「メモリの内訳を測る」と同じ）。
- コンテナのメモリ: cgroup の `memory.current`・`memory.peak`・`memory.stat` の `anon`（プロセスのメモリ）と `file`（ページキャッシュ。回収できる）・`memory.events` の `max`。約 4 秒ごとに記録した。
- 止まったか: `docker inspect` の `OOMKilled` と、`memory.events` の `oom`・`oom_kill`。
- NMT は基準の回だけ有効にした（NMT 自身が約 10MiB を使う）。比べの回は GC の記録だけ。

## 2. メモリの内訳の結果（FR2.1）

### 2.1 基準の回（今の設定。最大ヒープ 1,536MiB）

| 項目 | 結果 |
|---|---|
| k6 の `checks` | 1,254 件すべて成功（ログイン 1,170・投入 42・表示 42）。`http_req_failed` 0 件（1,255 件中）。全要求の p95 1,436 ms・最大 4,081 ms |
| 止まったか | `OOMKilled` false、`oom` 0、`oom_kill` 0 |
| `anon`（終わり） | 1,893.5MiB（上限 2,048MiB の 92.5%）。前の Intent の 1,894MiB を再現した |
| `anon` の動き | 試験の始めに 1,065MiB、約 20 秒で 1,870MiB に達し、その後 1,872〜1,893MiB で頭打ち |
| `file`（終わり） | 138.4MiB |
| `memory.peak` | 2,048.7MiB（上限に達した） |
| `memory.events` の `max` | 1,535 回（前の Intent は 2,340 回）。始めてから約 52 秒後、`anon`＋`file` が上限に達してから増え始めた |
| 内部DB のファイル（終わり） | 354.0MiB |

Native Memory Tracking（試験の終わり、確保済み）:

| 分類 | 大きさ |
|---|---|
| 合計 | 1,854.1MiB |
| Java Heap（ヒープ） | 1,536.0MiB（最大まで広がった） |
| ヒープ以外の合計 | 318.1MiB |
| うち Metaspace | 99.9MiB |
| うち GC（G1 の管理の領域） | 75.8MiB |
| うち Code | 54.9MiB |
| うち Symbol | 22.5MiB |
| うち Class | 18.6MiB |
| うち Other（直接バッファなど） | 13.3MiB |
| うち Shared class space | 12.7MiB |
| うち Native Memory Tracking | 9.5MiB |
| うち Thread | 5.4MiB |
| うち そのほか（Synchronization・Internal・Arena・Compiler など） | 5.5MiB |
| `anon` と NMT の合計の差 | 39.4MiB（NMT が数えないもの。C のライブラリの確保の領域など。仮説） |

参考: 起動の直後（試験の前）は、NMT の合計 1,067.8MiB・ヒープの確保 822MiB・`anon` 1,064.8MiB だった。起動のときに適用中の 10MB の DSL を読み込むため、試験の前からヒープが大きい（仮説。`DslLifecycle` の起動時の読み込みによる）。

ヒープ（GC の記録、120 秒）:

| 項目 | 結果 |
|---|---|
| GC の回数 | 316 回（Young Normal 90・Prepare Mixed 42・Mixed 100・Concurrent Start 42 のうち巨大なオブジェクトの確保による 31）。確保の失敗（Evacuation Failure）2 回 |
| GC の前の使用量の最大 | 1,456MiB |
| GC の後の使用量の最大 | 1,348MiB（古い領域の回収待ちを含む） |
| 確保した量の合計（GC の記録からの概算） | 約 56.5GB（毎秒 約 470MB） |
| 生きているもの（試験の後、完全な GC の後） | 84MiB（`class_histogram` の合計 82.9MiB。うち `byte[]` 36.7MiB、DSL のモデルの `DslColumn` 34,000 個 1.6MiB） |

### 2.2 比べの回（ヒープの上限だけを変えた。コードは同じ）

同じ使い捨ての環境・同じ条件で、アプリのコンテナを作り直して（`memory.peak` を消すため）流した。内部DB のファイルは基準の回より大きい状態（ページキャッシュ `file` が約 324MiB）で、NMT は無効。

| 項目 | 基準（75%、ヒープ 1,536MiB） | 比べ 1（`-XX:MaxRAMPercentage=50.0`、ヒープ 1,024MiB） | 比べ 2（`-Xmx768m`） |
|---|---|---|---|
| `checks` | 1,254 件すべて成功 | 1,296 件すべて成功 | 1,255 件すべて成功 |
| `http_req_failed` | 0 件 | 0 件 | 0 件 |
| 全要求の p95 | 1,436 ms | 1,323 ms | 1,330 ms |
| `anon`（終わり） | 1,893.5MiB（92.5%） | 1,412.9MiB（69.0%） | 1,143.3MiB（55.8%） |
| `memory.peak` | 2,048.7MiB | 1,756.1MiB | 1,485.5MiB |
| `memory.events` の `max` | 1,535 回 | 0 回 | 0 回 |
| `OOMKilled` | false | false | false |
| 完全な GC | 1 回（試験の後の `class_histogram` によるもの） | 2 回（後の使用量 482MiB・465MiB） | 20 回（後の使用量 148〜454MiB） |
| 確保の失敗（Evacuation Failure） | 2 回 | 44 回 | 182 回 |
| `anon` − 最大ヒープ（ヒープ以外の目安） | 358MiB | 389MiB | 375MiB |

### 2.3 読み取れること（解釈）

確かめた事実:

- `anon` の 1,894MiB は、ヒープが最大（1,536MiB）まで広がった分と、ヒープ以外の約 320〜390MiB の合計である。生きているもの（完全な GC の後）は 84MiB で、適用中とプレビューのモデルを持ち続ける分は数十 MiB にとどまる。
- ヒープの上限を下げると、`anon` はほぼ同じ量だけ下がり（1,536MiB → 1,024MiB で約 481MiB 減）、`memory.events` の `max` は 0 になった。k6 の失敗は無く、応答の時間も悪くならなかった。
- 比べの回の完全な GC の後の使用量（最大 482MiB）から、10MB の DSL の投入や表示の最中には、数百 MiB が一時的に生きている。
- 1回の重い操作ごとの確保の量は大きい（120 秒で約 56.5GB。ログインの分と分けては測っていない）。

仮説（未検証）:

- 93% に達するのは、アプリが持ち続けるデータのためではなく、JVM の大きさの設定（最大ヒープ 75%）と確保の速さのため、G1 がヒープを最大まで広げたことによる。
- `memory.events` の `max` は、`anon`（約 1.9GiB）とページキャッシュ（H2 のファイルの書き込みで増える `file`）の合計が上限に当たって起きた回収で、ヒープの上限を下げると `anon`＋`file` が上限の内に収まり 0 になる。
- 10MB の DSL の重い操作 1 回が一時的に使うヒープは約 370〜400MiB（完全な GC の後の最大 482MiB から、止まっている間の 84MiB を引いた概算。ログインの分が小さいとみた場合）。中身は、SnakeYAML の節の木（位置つき）・Jackson の木・`PositionMap`・JSON Schema の検証の途中のもの・照合の途中のもの、と見られる（ヒープの中身の内訳は測っていない）。

### 2.4 アプリで減らせる分の見立て

| 候補 | 何をするか | 見込み（根拠） | 注意 |
|---|---|---|---|
| M1 最大ヒープの割合を下げる | `Dockerfile` の `-XX:MaxRAMPercentage=75.0` を 50.0 にする（配備・使い捨ての環境の両方に効く） | `anon` 約 −480MiB、`max` 0（比べ 1 の実測） | `docker/check-container-limits.sh`（既定 75% を確かめる）と README・`perf/README.md` の説明の 75% を合わせて直す。`refresh` など他の場面も Build and Test で確かめる |
| M2 本文の複写を減らす | `DslContent` の作るときと `yamlBytes()` のたびの `clone()` をやめ、読み出しの道（ダウンロード・戻し）で本文の配列を1つにする | 1回の操作あたり 10〜30MiB 程度の一時的な確保の減（コードからの見積もり。仮説） | 配列を外に渡すため、呼び出し側で書き換えないことをテストか型で守る。本文の識別（SHA-256）とダウンロードのバイト単位の一致を保つ |
| M3 読み込みの途中の形を早く手放す | 投入の処理で、節の木・Jackson の木・`PositionMap` を使い終わったら参照を切る、検証の後に大きな途中の形を持ち越さない | 重い操作の最中の生きている量（約 370〜400MiB の一部）の減。効き目は測ってから決める（仮説） | DSL の信頼できない入力の決まり（上限・タグ・重複キー・誤りの行と列）を弱めない。既存のテストを通す |
| M4 読み込みの作りを変える（節の木を作らない） | SnakeYAML の出来事の流れから直接 Jackson の木を作る | 最も大きく減らせる見込みだが、作り直しが大きい（仮説） | この bugfix の範囲を超える。信頼できない入力の決まりを守る作りの確かめが大きいため、今回は勧めない |

ヒープの中身の内訳（どの形がどれだけ使うか）は測っていない。M2・M3 を入れる場合は、コード生成の中で、1回の 10MB の投入の最中の `GC.class_histogram -all`（完全な GC を起こさない）で上位を確かめてから手を入れる（計画の Step を参照）。

### 2.5 目標の数値の候補（FR2.2、依頼者が承認の場で選ぶ）

どの案も、判定の条件は前の Intent と同じ `dslMixed`（上の 1 節の条件）で、`checks` がすべて成功し `OOMKilled` が false であることを含む。

| 案 | 目標 | 満たす見込みの手段 | 根拠 |
|---|---|---|---|
| T1（勧める） | `anon` の最大が上限 2g の 75%（1,536MiB）以下、かつ `memory.events` の `max` が 0 | M1（最大ヒープ 50%）。M2 は小さい変更として足してもよい | 比べ 1 で `anon` 1,412.9MiB（69.0%）・`max` 0。余裕は約 123MiB |
| T2 | `anon` の最大が 60%（1,229MiB）以下、かつ `max` が 0 | 最大ヒープを約 768MiB（`MaxRAMPercentage` 37.5 相当）に下げ、M2・M3 で一時的な使用量を減らす | 比べ 2 で `anon` 1,143.3MiB（55.8%）・`max` 0。ただし完全な GC が 120 秒で 20 回起き、重い操作の最中の生きている量（最大 454MiB）に対して余裕が小さい。M3 の効き目は未検証 |
| T3 | `anon` の数値の目標は置かず、`max` が 0 かつ `OOMKilled` が false | M1 | 比べ 1・2 とも満たす。プロセスのメモリの量そのものは確かめない |

## 3. 内部DB の詰め直しの前提の確かめ（FR1）

### 3.1 やったこと

リポジトリの外の一時ディレクトリで、アプリと同じ版の H2（2.4.240）と HikariCP（7.0.2）の jar を使う短いプログラム（`build/perf-results/storage-memory-base/Exp.java` に写しを残した）を、この PC の JDK 25 で動かした。表の形はアプリと同じ（`dsl_previews`・`dsl_applied_revisions`、本文は `BINARY LARGE OBJECT`）で、1回の操作はアプリと同じ SQL の形（プレビューの1行への `MERGE` → 履歴への `INSERT ... SELECT` → プレビューの削除 → 20 件を超えた古い履歴の削除）とした。本文は 10,485,760 バイトの乱数。接続先は `jdbc:h2:file:<一時ディレクトリ>/t;DEFRAG_ALWAYS=TRUE`（対照だけ `DEFRAG_ALWAYS` なし）。

注意: コンテナのボリュームではなく、この PC（macOS）のファイルの上で測った。かかる時間はコンテナの中とは違いうる。

### 3.2 結果

| 試し | 結果 |
|---|---|
| A 接続を1本持ち続けたまま 40 回重ねる | 1回あたり約 9.6〜10.4MiB ずつ伸び、40 回とプレビュー1件で 404.6MiB。書いた側の接続を閉じても（持ち続ける1本は開いたまま）404.6MiB、`CHECKPOINT SYNC` の後は 416.2MiB で縮まない |
| A の続き 最後の1本を閉じる | 閉じる操作に 2,019 ms かかり、200.3MiB に縮んだ（`DEFRAG_ALWAYS`）。開き直して、すべての本文の SHA-256 が `dsl_hash` と一致（20/20） |
| A2 持ち続けたまま、50 秒空けてから 20 回、さらに 5 秒おきに 10 回 | 188.2MiB → 395.9MiB → 509.4MiB と伸び続けた（空けても空いた場所は再利用されない） |
| A3 A2 と同じで `SET RETENTION_TIME 0` を付ける | 188.2MiB → 395.9MiB → 509.2MiB。変わらない |
| 対照 `DEFRAG_ALWAYS` なしで 40 回重ねて閉じる | 閉じても 416.2MiB のまま縮まない |
| B ほかの接続を持ったまま、別の接続から `SHUTDOWN COMPACT` | 2,130 ms で 404.6MiB → 210.4MiB。持っていたほかの接続は使えなくなった（SQLState `90121`、データベースが閉じられた）。開き直すと本文は 21/21 一致 |
| B2 同じく `SHUTDOWN DEFRAG` | 2,340 ms で 210.4MiB。ほかの接続は `90121`。21/21 一致 |
| C Hikari（上限 30・待ち 5 秒・一時停止を許す）で、一時停止 → 接続の破棄 → すべての物理的な接続が閉じるのを待つ | 借りていた1本（3 秒持ってから返す）が返った後に接続が 0 本になった。詰め直しは閉じる処理の中で非同期に進み、再開の後には 210.4MiB だった。一時停止の間に届いた借りる要求は、待ちの上限 5 秒を超えても失敗せず、再開まで 11,684 ms 待たされた |
| D Hikari で、一時停止 → 借りている接続が 0 本になるのを待つ → プールの外の接続で `SHUTDOWN COMPACT` → 破棄 → 再開 | 30 本の待機中の接続を開いたまま `SHUTDOWN COMPACT` が 2,061 ms で終わり 210.4MiB。破棄して 0 本になるまで 1 ms、再開の後の最初の借りる要求は一時停止から 2,078 ms で成功。本文 21/21 一致。その後の書き込みも成功 |
| E `DEFRAG_ALWAYS` で最後の1本を閉じている最中に、別のスレッドが新しい接続を開く | 開く要求は閉じる処理（詰め直し）が終わるまで待たされ、その後成功した（1回だけの観察） |

### 3.3 確かめた事実と未検証の点

確かめた事実（この PC の上、H2 2.4.240）:

- 要件の前提は正しい。接続が1本でも開いている間は、投入と適用のたびにファイルが伸び、`CHECKPOINT`・時間を空けること・`RETENTION_TIME 0` のどれでも空いた場所は再利用されず縮まない。すべての接続を閉じたときに、`DEFRAG_ALWAYS=TRUE` に従って詰め直され、論理的な大きさ（本文 21 件で約 210MiB）まで縮む。
- `SHUTDOWN COMPACT`（または `SHUTDOWN DEFRAG`）は、ほかの接続が開いていても同期で詰め直して閉じる。接続先の `DEFRAG_ALWAYS` の有無に左右されない。ほかの接続はその後使えなくなる。
- Hikari の一時停止（`suspendPool`）は、借りる要求を「再開まで」待たせる。待ちの上限（`connection-timeout` 5 秒）は効かない（HikariCP 7.0.2 のソースでも、一時停止中の借りる要求は上限なしで待つ作り）。そのため、Hikari の一時停止だけでは FR1.5（5 秒を超えたら今と同じ失敗）を満たさない。
- Hikari の一時停止の間は、プールが接続を足さない（試し C・D で、再開までの間に新しい接続が作られなかった）。
- 本文 21 件（約 210MiB）の詰め直しは、この PC の上で約 2.0〜2.3 秒だった。

未検証（Build and Test や計画の手順で確かめる）:

- コンテナのボリュームの上で、10MB の本文 21 件の詰め直しにかかる時間（NFR1）。5 秒（接続を借りる待ちの上限）の内に終わるか。
- 試し A の途中の1回で、持ち続けたまま 50 秒空けた後の 5 回で大きさが増えなかった（416.2MiB のまま）。A2 で同じことを試すと伸び続けたため、1回だけの説明のつかない観察として残す。
- 試し E（閉じている最中に開く）は1回だけの観察である。計画の方法（試し D の形）では、閉じる操作と開く操作が重ならないようにするため、頼らない。

### 3.4 実際のアプリのコンテナでの、HikariCP の標準の JMX の操作だけの確かめ（依頼者の決定 Q2・Q3）

依頼者の決定（入口は JMX だけ、HikariCP の標準の機能だけで行う）を受けて、実際のアプリのイメージ（`mastersmith:storage-memory-base`）を使い捨ての環境で起動し、HikariCP の標準の操作だけで詰め直されるかを確かめた（2026-09-25 13:08〜13:13。配備したアプリは止めていない）。

やり方:

- ソースは変えず、使い捨ての環境の一時の環境ファイルに `SPRING_DATASOURCE_HIKARI_REGISTERMBEANS=true` と `SPRING_DATASOURCE_HIKARI_ALLOWPOOLSUSPENSION=true` を足して起動した。接続先はアプリの既定（`DEFRAG_ALWAYS=TRUE`）。プールの MBean は `com.zaxxer.hikari:type=Pool (mastersmith-db)` として登録された。
- JMX は外に公開していない。同じ版の JDK のイメージ（`eclipse-temurin:25.0.4_7-jdk-noble`）の一時のコンテナを、アプリのコンテナと PID とネットワークの名前空間を共有して `-u 10001:10001` で動かし、短い Java のプログラム（`build/perf-results/storage-memory-base/jmx-check/HikariJmx.java` に写しを残した）で JVM（PID 1）に attach し、JVM の中だけの JMX の接続（`startLocalManagementAgent`）で Pool の MBean の標準の操作（`suspendPool`・`softEvictConnections`・`resumePool`）と属性（`ActiveConnections`・`IdleConnections`・`TotalConnections`・`ThreadsAwaitingConnection`）だけを使った。ソースはリポジトリの外から標準入力で渡し、ボリュームやファイルの受け渡しはしていない。
- ファイルを伸ばすために、10MB の DSL（回ごとに埋め草の番号を変える）の投入と適用を 21 回とプレビュー1件を API で行った。詰め直しの後に、履歴のすべての版を戻して本文の SHA-256 が `dslHash` と一致するかを確かめた。

結果:

| 試し | 結果 |
|---|---|
| J-A 一時停止 → 破棄 → 0 本 → ファイルが落ち着くのを待つ → 8 秒待って再開 | 一時停止 15 ms、破棄で待機中の 30 本がすぐ閉じ 0 本（5 ms 以内。借りている接続は 0 本だった）。最後の接続が閉じたときに `DEFRAG_ALWAYS` で詰め直され、229.1MiB → 15.3MiB。ファイルは破棄から約 0.7 秒以内に変わらなくなった（3 秒変わらないことで終わりと判定）。再開から 3 秒以内にプールは 30 本に戻った。本文は 20/20 一致 |
| J-A の間に届いたログイン | 一時停止から約 5 秒後に送ったログインは、再開まで待たされ、7.6 秒で 200 になった。接続の待ちの上限（5 秒）では失敗しなかった |
| J-B 0 本になったらすぐ（3 ms 後）再開し、詰め直しの終わりを待たない | 448.5MiB → 15.3MiB（再開の 3 秒後に確認）。本文は 20/20 一致。再開と詰め直しが重なっても問題は見られなかった（1回の観察） |
| J-C 一時停止のまま 50 秒置く（再開し忘れの影響） | その間に送ったログインは 53.5 秒待たされてから 200。`/actuator/health` は一時停止の間ずっと 503（既存の `TimeBoundedDbHealthIndicator` が「内部DBの確認が制限時間内に終わりませんでした」「内部DBの確認を行えませんでした」を WARN で出す）。コンテナの健全性は約 32 秒後に unhealthy になり、再開の後に healthy に戻った。アプリは止まらず、`OOMKilled` は false |
| ファイルの大きさ（本文の内容の違い） | この試しの DSL は圧縮がよく効く文字の本文のため、詰め直しの後は 15.3MiB だった。乱数の本文（3.2 節）では 21 件で 210.4MiB で、これが論理的な最大に近い |

確かめた事実（実際のアプリのコンテナ）:

- HikariCP の標準の設定（`register-mbeans`・`allow-pool-suspension`）と、標準の MBean の操作（一時停止 → 接続の破棄 → 再開）だけで、アプリを止めずに内部DB のファイルが詰め直される。H2 は最後の接続が閉じたときに閉じ、接続先の `DEFRAG_ALWAYS=TRUE` に従って詰め直す。
- 一時停止の間に届いた内部DB を使う要求は、再開まで待たされ、再開の後に成功する。待ちに上限は無く、接続の待ちの上限（5 秒）は効かない（要件 FR1.5 との差。3.3 節のソースの確かめと同じ）。
- 一時停止が約 30 秒を超えると、コンテナの健全性が unhealthy になる。再開すれば戻る。
- 詰め直しにかかる時間は、この条件（本文 21 件、圧縮の効く本文）で1秒未満だった。

未検証:

- 乱数に近い（圧縮の効かない）本文で約 210MiB が残る状態の、コンテナの中の詰め直しの時間（3.2 節の PC の上では約 2 秒）。
- 一時停止のときに借りている接続がある場合（例: 重い DSL の操作やログインの最中）に、0 本になるまでの時間。コンテナの中では、借りている接続が 0 本の時にしか試していない。
- `softEvictConnections` を呼んだ時に借りている接続は、返されたときに閉じる（HikariCP の作り。3.2 節の試し C で、3 秒後に返された接続が閉じて 0 本になったことは確かめた）。

### 3.5 方法の候補（計画で案を示す）

依頼者の決定（入口は JMX だけ、HikariCP の標準の機能だけ）により、方法は次の X2 を採る。X1・X3 は、標準の機能だけで足りないと分かったときの代わりの案として残す。3.4 節のとおり、X2 で詰め直せることは確かめた。

| 候補 | 内容 | 良い点 | 弱い点 |
|---|---|---|---|
| X2（依頼者の決定） | HikariCP の標準の MBean の操作だけで、一時停止 → 接続の破棄 → 0 本になるのを待つ → 再開。詰め直しは最後の接続が閉じたときの `DEFRAG_ALWAYS` に任せる | アプリのコードが要らない（設定だけ）。3.4 節で詰め直せることを確かめた | 待ちに上限が無い（FR1.5 と違う）。詰め直しの終わりは、ファイルの大きさを見て判断する。`MASTERSMITH_DB_URL` を `DEFRAG_ALWAYS` なしで上書きすると縮まない。監査ログ・アプリのログは残らない |
| X1（代わりの案） | アプリの側の門（内部DB の `DataSource` の手前）で新しい借りる要求を止め（待ちは 5 秒まで）、Hikari を一時停止し、借りている接続が 0 本になるのを待ち、プールの外の接続で `SHUTDOWN COMPACT` を実行し、古い接続を破棄して再開する（3.2 節の試し D の形） | 詰め直しが同期で終わる。FR1.5 の 5 秒を守れる。`DEFRAG_ALWAYS` の有無に左右されない | アプリのコードが要る（依頼者の決定と違う） |
| X3（勧めない） | Hikari の一時停止をせず、`SHUTDOWN COMPACT` を直接実行する | 作りが最も小さい | 借りている接続の途中の処理が壊れる（`90121`）。プールが閉じた接続を再び配りうる |

一時停止の間の待ちの扱い（FR1.5 との差）の候補:

| 候補 | 内容 | 結果 |
|---|---|---|
| W1（勧める） | HikariCP の既定のまま、一時停止の間の要求は再開まで待たせる。手順（台本）で一時停止の長さに上限を置き、必ず再開する | 要求は失敗しないが、一時停止の長さだけ遅れる（3.4 節の J-A で 7.6 秒）。再開し忘れると、内部DB を使う要求がすべて止まり続ける |
| W2 | HikariCP の標準のシステムプロパティ `com.zaxxer.hikari.throwIfSuspended=true` を JVM に渡し、一時停止の間の要求はすぐ失敗させる | 待たずに失敗する（今の接続の待ちの失敗と同じ 500 になる見込み。未検証）。依頼者の回答 F2（待たせる）と違う |
| W3 | X1 の門を作り、待ちを 5 秒に限る | アプリのコードが要る（依頼者の決定と違う） |

## 4. `AccessTokenApiIT` の原因の候補 (b) の仕組みの確かめ（FR3 の準備）

- この PC では、colima が公開の番号を、`ssh` の処理が `127.0.0.1:<番号>` だけで待ち受けて転送している（使い捨ての環境の `127.0.0.1:18080` で確かめた。`lsof` の待ち受けの一覧）。
- 短いプログラム（`build/perf-results/storage-memory-base/PortExp.java`）で、全アドレスで待ち受ける相手（Tomcat の `RANDOM_PORT` と同じ、Java の既定で `SO_REUSEADDR` が有効）と、`127.0.0.1` だけで待ち受ける相手（colima の転送と同じ）が同じ番号を取れるかを試した。どちらを先に取っても両方が同じ番号で待ち受けでき、`localhost` への接続は `127.0.0.1` だけで待ち受ける側（colima の転送）が受けた。
- ただし、Testcontainers のように全アドレスで公開したコンテナの番号は、colima の `ssh` が `127.0.0.1` ではなく IPv4 の全アドレス（`*:<番号>`）で待ち受けて転送していた（`docker run -p <番号>:5432` で確かめた）。
- 実際の形で試した（`build/perf-results/storage-memory-base/PortRepro.java`・`PortRepro2.java`）。
  - 先に Java の HTTP のサーバー（全アドレス、テストのアプリの代わり）が番号を取り、後から同じ番号でコンテナを公開した場合: colima の転送は作られず（番号が使われているため）、`localhost` への要求はサーバーに届いた（3 回とも 200）。重なりは起きなかった。
  - 先にコンテナ（PostgreSQL）を公開し、colima の `ssh` が IPv4 の `*:<番号>` で待ち受けている状態で、Java の HTTP のサーバーが同じ番号を全アドレスで取った場合: Java の既定では IPv6 の全アドレス（`[::]:<番号>`、IPv4 と兼用）で待ち受けでき、番号が重なった。この状態で JDK の `HttpClient`（`HttpTestClient` と同じ既定）で `http://localhost:<番号>/` と `http://127.0.0.1:<番号>/` に送ると、どちらも `IOException: HTTP/1.1 header parser received no bytes` になった（要求は colima の転送から PostgreSQL へ届き、切られた）。`http://[::1]:<番号>/` は 200 だった。
  - 同じ状態で `-Djava.net.preferIPv4Stack=true` を付けると、Java の HTTP のサーバーは同じ番号を取れず（`Address already in use`）、重なりが起きなかった。
- このことから、候補 (b) は「colima が Testcontainers のコンテナの番号を IPv4 の全アドレスで転送している番号を、テストのアプリ（IPv6 の全アドレス）が重ねて取ったとき、`localhost` への要求がコンテナへ届いて切られる」という形で、1回目の失敗と同じ例外の文言を再現できた。
- 未検証: テストのアプリが `RANDOM_PORT`（番号 0 で OS に選ばせる）のときに、OS が IPv4 だけで使われている番号を IPv6 の全アドレスの待ち受けに選ぶことがあるか。実際の 1回目の失敗がこれで起きたか（報告は上書きされていて確かめられない）。
- 番号の重なりが起こりうる範囲（仮説）: VM の中の Linux の一時的な番号（32768〜60999）と macOS の一時的な番号（49152〜65535）の重なる 49152〜60999。

## 5. 片付け

- 使い捨ての環境: `MASTERSMITH_PERF_ENV_FILE=/dev/null docker compose -p mastersmith-perf -f docker/perf/compose.yaml --profile targetdb-postgres --profile targetdb-mysql --profile targetdb-mariadb down -v` でコンテナ・ボリューム・ネットワークを消した。
- 一時ディレクトリ（ホームの下）を消した。H2 の試しのデータベースのファイルも消した。
- 3.4 節の確かめの使い捨ての環境も同じ手順で消した。番号の重なりの試しで起動したコンテナ（`port-repro*`）も消した。
- 配備したアプリ: 12:43:35 に止め、12:54:00 に `docker compose start app` で起動し直し、healthy になった（`mastersmith:local`）。3.4 節の確かめ（13:08〜13:13）の間は止めていない（VM のメモリに収まるため）。最後に healthy であることを確かめた。
- 残したもの: `build/perf-results/storage-memory-base/`（生の結果。コミットしない）と、イメージ `mastersmith:storage-memory-base`（Build and Test の比べに使える。不要なら消してよい）。

## Sources

- 要件: `aidlc/spaces/default/intents/260925-storage-memory-fixes/inception/requirements-analysis/requirements.md`（FR1・FR2・FR3・NFR1）
- 直す前の値: `aidlc/spaces/default/intents/260924-followup-fixes/construction/build-and-test/test-results.md` の 3 節
- 手順: `perf/README.md`（「負荷の試験（k6）」「メモリの内訳を測る」「DSL の時間を測る」）
- コードの知識: `aidlc/spaces/default/codekb/mastersmith2/code-quality-assessment.md`（TD-1〜TD-3）
- 生の結果: `build/perf-results/storage-memory-base/`（`mem-samples.tsv`・`nmt-*.txt`・`gc-mixed.log`・`class-histogram-end.txt`・`dslMixed.json`・`variant-ram50/`・`variant-xmx768/`・`h2-experiment-results.txt`）

## Assumptions & Open Questions

- 比べの回は基準の回の後に同じボリュームで流したため、内部DB のファイルとページキャッシュが大きい状態だった。`anon` の比べには影響が小さいとみたが、`max` の比べには有利に働いた可能性がある（仮説）。Build and Test では、直した後の回を同じ順序・同じ条件で流して比べる。
- NMT は基準の回だけ有効にした（約 10MiB）。
- H2 の試しはコンテナのボリュームではなくこの PC のファイルの上で測った。
