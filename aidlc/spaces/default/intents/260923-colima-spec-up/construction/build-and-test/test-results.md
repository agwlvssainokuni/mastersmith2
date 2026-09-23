# テストの結果（test-results）

本 Intent（F3・F4 の修正）のビルド・テスト・負荷の試験の結果。対象のコミットは `e4b10af`。実行日は 2026-09-23。生の出力は `build/perf-results/260923-colima/`（Git 管理外）にある。

## 1. ビルドと既存のテスト

コマンド: `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（1分55秒、`BUILD SUCCESSFUL`）

| 項目 | 結果 |
|---|---|
| バックエンドの単体テスト | 387 件、失敗 0、エラー 0、飛ばし 0 |
| バックエンドの結合テスト | 244 件、失敗 0、エラー 0、飛ばし 0 |
| 画面のテスト | 167 件すべて成功 |
| JaCoCo（バックエンド） | 行 96.14%（1393/1449）、分岐 91.45%（417/456） |
| Vitest（画面） | 行 98.73%（391/396）、分岐 94.02%（189/201） |
| Gitleaks | `no leaks found` |
| OSV-Scanner | `UP-TO-DATE`（lockfile に変更なし。Code Generation の Step 8 と同じ結果） |

- 数字は Code Generation の Step 1（変更の前）・Step 8（変更の後）と同じである。アプリのコードを変えていないため、カバレッジは変わらない。
- テストのログに出る OTLP の送信の失敗の WARN は、既存の観測性のテストの終了時のもので、本 Intent の変更とは関係しない。

## 2. 設定の効き方（回帰の確かめ）

| コマンド | 結果 |
|---|---|
| `./docker/check-container-limits.sh`（VM を CPU 4・6GiB にした後、イメージ `mastersmith:local`） | 12 項目すべて OK、終了コード 0 |
| `MASTERSMITH_IMAGE_TAG=pre-fix ./docker/check-container-limits.sh`（Code Generation で実行） | 2 件 NG、終了コード 1（直す前は割合の指定が効かない。期待どおりの失敗） |
| 使い捨ての環境の上限（`docker inspect`） | 2g の回: Memory 2147483648・NanoCpus 4000000000。1g の回: Memory 1073741824 |
| JVM の GC の方式（`-XX:+PrintFlagsFinal -version`） | 上限 1g: `UseSerialGC=true`。上限 2g: `UseG1GC=true` |

## 3. colima の VM の作り直し（FR1）

- 依頼者の確認（Q1: A）を得て、配備したアプリを止め、内部DBのボリュームのバックアップ `mastersmith-data-202609231616-before-vm.tgz`（Git 管理外）を取ってから作り直した。
- `colima stop` → `colima start --cpu 4 --memory 6` の後、`colima list` は CPUS 4・MEMORY 6GiB、`docker info` は NCPU 4・MemTotal 6197366784 だった。
- 配備したアプリ（`mastersmith-app-1`、直す前のイメージ・上限 1g のまま）を `docker compose start app` で起動し直し、healthy を確かめた。負荷の試験の後も同じコンテナを起動し直し、healthy である。コンテナの作り直し（新しいイメージ・2g）は Deployment Execution の段で行う。

## 4. 負荷の試験（FR5）

条件: 使い捨ての環境、k6 `grafana/k6:2.3.0`、同時 10、考える時間なし、場面ごとに 60 秒。VM は CPU 4・メモリ 6GiB。測定の間は配備したアプリを止めた。応答時間はミリ秒。

| 回 | 上限 | 場面 | 件数 | 秒あたり | 中央値 | p90 | **p95** | p99 | 最大 | 想定と違う応答 | コンテナ |
|---|---|---|---|---|---|---|---|---|---|---|---|
| judge | 2g・CPU 4 | loginSuccess | 766 | 12 | 783.9 | 916.8 | **940.1** | 977.2 | 1,018.0 | 0 件 | 動作中 |
| judge | 2g・CPU 4 | loginFailure | 775 | 12 | 776.0 | 906.2 | **926.4** | 965.7 | 1,088.3 | 0 件 | 動作中 |
| judge | 2g・CPU 4 | refresh | 677,766 | 11,295 | 0.6 | 1.3 | **1.9** | 4.7 | 1,001.3 | 0 件 | 動作中（OOMKilled false） |
| judge2 | 2g・CPU 4 | refresh | 656,952 | 10,948 | 0.7 | 1.4 | **1.8** | 4.2 | 965.4 | 0 件 | 動作中（OOMKilled false） |
| pre1g | 1g・CPU 4 | refresh | 41,770 | 682 | 2.1 | 7.5 | 10.6 | 18.0 | 2,159.0 | 1,725 件 | **OOMKilled true、終了コード 137** |

前回（前の Intent の負荷の試験。VM は CPU 2・2GiB、上限 CPU 2・1g）との比べ:

| 場面 | 前回の p95 | 今回の p95（2g・CPU 4） | 前回の停止 | 今回の停止 |
|---|---|---|---|---|
| loginSuccess | 1,615.3〜1,620.9 | 940.1 | — | — |
| loginFailure | 1,569.7 | 926.4 | — | — |
| refresh | 4.2〜4.7（約 35 秒で停止） | 1.8〜1.9 | 2回とも OOMKilled | 2回とも止まらない |

- ログインの p95 は、成功・失敗とも 1 秒以内になった（F4）。ただし余裕は 60〜75 ms で小さい。最大値は 1,018 ms・1,088 ms で、1 秒を超える要求が少しある。k6 も同じ VM の CPU を使うため、値には k6 の分が混ざりうる。
- 上限 1g のままでは、VM を CPU 4 にしても refresh で止まった（メモリの最後の記録は開始から 12 秒で 796.6MiB）。F3 は VM の大きさではなくコンテナの上限と JVM の設定で決まる、という見立て（コードの知識ベースの TD-6）どおりである。
- 上限 2g の judge2 では、コンテナのメモリ（`docker stats`）が終わりに 1.609GiB まで増えた。一方、NMT で測った JVM の確保（committed）は 2g で約 0.8GB で安定していた（5章）。差の多くは内部DB（H2）のファイルのページキャッシュと見られる。refresh はトークンを作り直すたびに行を書くためである。ページキャッシュは足りなくなると解放されるため停止の原因にはならない見込みだが、内訳は確かめていない（残る点を参照）。

## 5. F3 の原因の内訳（FR4、NMT）

条件: 上の使い捨ての環境に `MASTERSMITH_JAVA_OPTIONS=-XX:NativeMemoryTracking=summary` を足し、refresh を流しながら約 6 秒ごとに `jcmd 1 VM.native_memory summary.diff` を取った。値は JVM の確保（committed）。

| 分類 | 1g: 開始 | 1g: 最後の記録（開始から約 13 秒） | 2g: 開始 | 2g: 終わり（約 60 秒） |
|---|---|---|---|---|
| 合計 | 303MB | **604MB**（+306MB） | 762MB | **811MB**（+51MB） |
| Java Heap（ヒープ） | 106MB | 330MB（+224MB。最大 768MB） | 495MB | 495MB（最大 1,536MB） |
| ヒープ以外の合計 | 197MB | 274MB | 267MB | 317MB |
| うち Code（JIT のコードキャッシュ） | 30MB | 70MB（+41MB） | 33MB | 79MB（+46MB） |
| うち Metaspace | 89MB | 96MB（+8MB） | 90MB | 97MB（+7MB） |
| うち Compiler（JIT の作業領域） | 7MB | 26MB（+26MB） | 0.3MB | 0.4MB |
| うち GC | 0.4MB | 1.2MB | 52MB | 53MB |
| うち Symbol・Class・Thread・Shared class space・Arena など | 約 70MB | 約 81MB | 約 92MB | 約 88MB |

- 1g では、最後の記録（開始から約 13 秒）の後に記録が取れなくなり、その後 OOMKilled（終了コード 137）で止まった。止まる直前の値は取れていない。
- **見立て**: F3 は、ヒープの最大（上限の 75% = 768MB）と、ヒープ以外（負荷の下で約 280〜320MB）の合計が上限 1GB を超えることで起きる。
  - ヒープ以外は、負荷をかけると JIT のコードキャッシュ（Code）と作業領域（Compiler）が大きく伸びる。1g では 13 秒で 274MB になり、1GB の 25%（約 256MB）をすでに超えていた。
  - 1g ではヒープが最大の 768MB まで伸びうるため、768MB＋約 280MB ≒ 1,050MB となり、上限を超える。
  - 2g では、ヒープの確保は 495MB で止まり（最大 1,536MB に届かない）、ヒープ以外は約 317MB だった。合計約 811MB で上限 2GB に余裕がある。
- **アプリのコードの原因（FR4.2）**: ヒープは 2g で 60 秒のあいだ確保が増えず、ヒープ以外の伸びは JIT のもの（Code・Compiler）が大半だった。保持の漏れのような、アプリのコードに原因がありそうな兆しは見つからなかった。ただし 60 秒の測定のため、長い時間の漏れは確かめていない。
- **GC の違い**: 1g では JVM が Serial の GC を選び、2g では G1 を選ぶ（2章）。1g の Compiler の伸び（+26MB）は、2g の G1 の回では見られなかった。

## 6. VM の見積もり（NFR3）

使い捨ての環境（2g、待機）・配備したアプリ（既存のコンテナ、上限 1g）・lgtm（上限 900m、待機）を同時に動かし、30 秒後に測った。

| 対象 | メモリ（`docker stats`） |
|---|---|
| mastersmith-perf-app-1（上限 2GiB） | 563.6MiB |
| mastersmith-app-1（上限 1GiB。配備の後は 2GiB） | 274.8MiB |
| mastersmith-lgtm-1（上限 900MiB） | 575.7MiB |
| VM 全体（`colima ssh -- free -m`） | total 5,910MB、used 1,698MB、buff/cache 2,648MB、available 4,212MB |

- 3つの上限の合計は、配備の後（アプリ 2g）で 2g＋2g＋900m ≒ 4.9GB であり、VM の total 5,910MB に収まる。上限は超えられない値のため、3つが同時に上限まで使っても VM の中に収まる。
- 負荷をかけながら3つを同時に動かすことはしていない。測定の間は配備したアプリを止める手順（`perf/README.md` の手順 0）のためである。

## 7. 残る点

- ログインの p95 の余裕は小さい（60〜75 ms）。k6 と同じ VM の CPU を分け合っているため、k6 の分を除いた値はこれより小さい見込みだが、確かめていない。
- 2g の refresh を続けたときのコンテナのメモリの伸び（最大 1.609GiB）の内訳（ページキャッシュかどうか）は確かめていない。長い時間の負荷（soak）は行っていない。
- 既定の上限 1g のままでは F3 が起きる（`pre1g` の回で再現）。README の既知の制約のとおりで、この PC では `.env` で 2g にする（Deployment Execution の段）。
