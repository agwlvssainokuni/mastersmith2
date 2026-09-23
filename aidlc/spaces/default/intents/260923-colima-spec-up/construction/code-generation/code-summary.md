# Code Generation の結果 — コンテナのメモリの上限と JVM の設定を環境変数で変えられるようにする（F3・F4）

## 変更したファイル

| ファイル | 変更 | 要件 |
|---|---|---|
| `Dockerfile` | `ENTRYPOINT` を `["sh", "-c", "set -f; exec java -XX:MaxRAMPercentage=75.0 -Duser.timezone=Asia/Tokyo ${MASTERSMITH_JAVA_OPTIONS:-} \"$@\" -jar /app/mastersmith.war", "mastersmith"]` にした。コメントで口の意味・標準の変数を使わない理由・`exec` の意味を説明した | FR3.1、FR3.2 |
| `compose.yaml` | `mem_limit` を `${MASTERSMITH_CONTAINER_MEMORY:-1g}` にした。CPU・メモリ・JVM の設定の口のコメントと、`lgtm` の VM の前提（CPU 4・メモリ 6GiB）のコメントを直した | FR2.1、FR6.3 |
| `docker/perf/compose.yaml` | `mem_limit` を同じ変数にした。「CPU 2 で測る」のコメントを、値の渡し方（`export` と `app.env`）の説明に直した | FR2.1、FR3.3 |
| `docker/check-container-limits.sh`（新規） | 設定の効き方の確かめのスクリプト（Apache License 2.0 のヘッダーつき、実行権あり） | FR2.1、FR3.1、FR3.2 |
| `README.md` | 21 行目と監視の節の colima の例を `colima start --cpu 4 --memory 6` に統一した。環境変数の表に `MASTERSMITH_CONTAINER_MEMORY`・`MASTERSMITH_JAVA_OPTIONS` を加えた。「コンテナの資源の上限（colima の VM・メモリ・JVM）」の節を新しく置き、VM の手順と確かめ方（`colima list`・`docker info`）、`.env` での上限の合わせ方、JVM の設定の口、既知の制約（1g と高い負荷）、確かめのスクリプトの使い方を書いた | FR1.3、FR6.1、FR6.2、FR6.5 |
| `.env.example` | コンテナの項目に `MASTERSMITH_CONTAINER_MEMORY`・`MASTERSMITH_JAVA_OPTIONS` をコメントとして加え、CPU のコメント（「例: colima の既定の 2」）を直した | FR6.2 |
| `perf/README.md` | 上限を CPU 4・メモリ 2g で `export` する形にした。手順 0 の理由を CPU の取り合いに直した。JVM の設定の渡し方（`app.env`）を書いた。F3 の注記を「修正の前の結果」と明記して残した。「メモリの内訳を測る（Native Memory Tracking）」の節を加えた | FR3.3、FR4.1（手順）、FR6.4 |

アプリの Java のコード（`backend/src/main/java/`）は変えていない（FR7.2）。

## 実測の結果

### Step 1 変更の前の基準（`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`、HEAD 8e70470）

- 結果: BUILD SUCCESSFUL（1 分 55 秒）
- バックエンドの単体テスト（`*Test`）: 387 件、失敗 0、エラー 0、飛ばし 0
- バックエンドの結合テスト（`*IT`）: 244 件、失敗 0、エラー 0、飛ばし 0
- JaCoCo: 行 96.14%（1393 / 1449）、分岐 91.45%（417 / 456）
- フロントエンド（Vitest）: 29 ファイル・167 件がすべて成功。行 98.73%（391 / 396）、分岐 94.02%（189 / 201）

### Step 2 修正の前の状態（再現）

- イメージ: 直す前の `mastersmith:local`（`sha256:27c4d08f34ec…`）を `mastersmith:pre-fix` のタグで残した。`ENTRYPOINT` は `["java","-XX:MaxRAMPercentage=75.0","-Duser.timezone=Asia/Tokyo","-jar","/app/mastersmith.war"]`。
- TD-8 の実測: メモリの上限 512m で、`JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=60.0` を渡し、`ENTRYPOINT` と同じ JVM の引数で `-XX:+PrintFlagsFinal -version` を動かした（アプリを起動しないため、`--entrypoint java` で `-jar` の代わりに `-version` を置いた）。
  - `MaxHeapSize = 402653184`（上限の 75%）、`MaxRAMPercentage = 75.000000 {command line}`。60% は効かない。
  - 標準エラーに `Picked up JAVA_TOOL_OPTIONS: -XX:MaxRAMPercentage=60.0` の1行が出た（JSON でない行）。
  - 比較: コマンド行で 60.0 を直接渡すと `MaxHeapSize = 322961408`（60%）。
- `docker compose config`（`.env` を読まない形）の `app` の `mem_limit`: 配備用は変数なしで 1073741824、`MASTERSMITH_CONTAINER_MEMORY=768m` でも 1073741824。負荷の試験用も 768m で 1073741824。変数は効かない。

### Step 6 確かめのスクリプト

`./docker/check-container-limits.sh`（直した後のイメージ `mastersmith:local`、`sha256:873c3803cee5…`）: 12 項目すべて OK、終了コード 0。

- `mem_limit`: 両方の compose で、変数なし 1073741824、768m で 805306368
- 変数なし: 最大ヒープ 402653184（512m の 75%）、PID 1、`user.timezone = Asia/Tokyo`
- 空の値（`MASTERSMITH_JAVA_OPTIONS=`）: 最大ヒープ 402653184（75%）
- `MASTERSMITH_JAVA_OPTIONS='-XX:MaxRAMPercentage=60.0 -XX:MaxMetaspaceSize=128m'`: 最大ヒープ 322961408（60%）、`MaxMetaspaceSize` 134217728、PID 1、タイムゾーンが残る

`MASTERSMITH_IMAGE_TAG=pre-fix ./docker/check-container-limits.sh`（直す前のイメージ）: `ENTRYPOINT` に口が無いため 2 件が NG、終了コード 1（期待どおりの失敗）。

確かめが中身を見ていることの確認（仮のイメージを作って実行し、終わったら消した）:

- `exec` を外した `ENTRYPOINT`: 「java が PID 1 で動いていない（PID 7）」の NG が 2 件
- 口を既定の引数の前に置いた `ENTRYPOINT`: 「最大ヒープが 60% でない（期待 約 322122547、実際 402653184）」の NG が 1 件

実際のアプリの起動での確認（一時のコンテナ。仮の署名鍵、ネットワークなし、ボリュームなし、上限 768m、終わったら消した）:

- `MASTERSMITH_JAVA_OPTIONS=-XX:NativeMemoryTracking=summary` でアプリが起動した（`Started MastersmithApplication`、PID 1）。ログに `Picked up` の行は 0 件で、1行1件の JSON のまま。
- 同じ版の JDK のイメージ（`eclipse-temurin:25.0.4_7-jdk-noble`）を `--pid container:<アプリ>` で動かし、`jcmd 1 VM.native_memory summary` で内訳を読めた（`perf/README.md` の NMT の手順の裏付け）。
- `docker stop` で `Commencing graceful shutdown` → `Graceful shutdown complete` → 接続プールの停止まで出て、1 秒未満で止まった（SIGTERM を Java が直接受け取る。FR3.2）。

### Step 8 統合の前の検査

`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（変更の後）

- 結果: BUILD SUCCESSFUL（1 分 53 秒）。フォーマット・リンタ・ライセンスヘッダー（`verifyLicense`）・ビルド・単体テスト・結合テスト・カバレッジの下限（`jacocoTestCoverageVerification`）・安全の検査（SpotBugs の関門・OSV-Scanner・Gitleaks「no leaks found」）がすべて通った。
- バックエンドの単体テスト: 387 件、失敗 0、エラー 0、飛ばし 0（Step 1 と同じ）
- バックエンドの結合テスト: 244 件、失敗 0、エラー 0、飛ばし 0（Step 1 と同じ）
- JaCoCo: 行 96.14%（1393 / 1449）、分岐 91.45%（417 / 456）（Step 1 と同じ。下限は行 80%・分岐 70%、除外は変えていない）
- フロントエンド: 167 件がすべて成功。行 98.73%、分岐 94.02%（Step 1 と同じ）
- SpotBugs の警告（重大度 High 未満）は、変更の前から出ている Java のコードのもので、今回の変更とは関係しない。
- `git diff --stat -- backend/src/main/java` は空（FR7.2）。
- 新しいスクリプトのライセンスヘッダーは、`Dockerfile` の先頭の 13 行と同じ（`diff` で一致を確認。下の「計画との差」）。

## 判断

- JVM の設定の口の名前は `MASTERSMITH_JAVA_OPTIONS` とし、既定の引数の後ろ・`"$@"` の前に置いた。`-XX` の指定は後の値が効くため、割合の上書きとヒープ以外の上限の追加ができる。
- `sh -c` の中で `set -f` を先に実行し、値の中の `*` などがファイル名に展開されないようにした（値は空白で分けるだけ）。
- 確かめのスクリプトは、直す前のイメージのように `ENTRYPOINT` に口が無いイメージでは JVM を起動しない（引数がアプリの後ろに渡ってアプリが起動してしまうため）。その場合は起動せずに 2〜4 を NG とする。
- PID 1 の確かめは、JVM のログに PID を付ける `-Xlog:os=info:stdout:pid` で行った（`-version` はすぐ終わるため、コンテナの外から PID を見られない）。タイムゾーンは `-XshowSettings:properties` の `user.timezone` で確かめた。
- `docker compose config` は `--env-file`（空の一時ファイル）と `--no-env-resolution` を付け、この PC の `.env` の値を読まず、`env_file` の中身も展開しないようにした。展開した設定は表示せず、`mem_limit` の数だけを取り出す。これにより、FR2.2 で `.env` に `MASTERSMITH_CONTAINER_MEMORY=2g` を書いた後も、「変数なしで 1g」の確かめが `.env` に左右されない。
- 最大ヒープは GC の区切りで丸められる（512m の 60% は 322122547 だが、実際は 322961408）。そのため、割合から計算した値との差を 4MiB まで認めた。75% と 60% の差（約 80MB）より十分小さい。
- JRE のイメージには `jcmd` が無いため、NMT の内訳は同じ版の JDK のイメージを PID の名前空間を共有して動かして読む手順にした。メモリの上限で止まると（SIGKILL）止まった瞬間の値は取れないため、5 秒ごとに記録して最後の値を「止まる直前」とする。
- bash 3.2（macOS）で、全角の文字が変数の名前の直後にあると名前の一部と読まれるため、スクリプトの変数はすべて `${...}` の形にした。`pipefail` の下で、読み手が先に終わるとパイプの書き手が失敗扱いになるため、`grep -q`・`awk` の `exit`・`head` を使わず、最後まで読む形にした。

## 計画との差

- `perf/README.md` の一時の `app.env`: 計画では「`app.env` と `export` の CPU 2 固定を CPU 4・メモリ 2g にする」だった。`app.env` はコンテナの中の環境変数になるだけで、compose の `cpus`・`mem_limit` の展開には使われない（効くのは `export` の方）。そのため、`app.env` からは `MASTERSMITH_CONTAINER_CPUS=2` の行を消し、上限は `export`（CPU 4・メモリ 2g）だけで渡す形にした。`app.env` には、JVM の設定（`MASTERSMITH_JAVA_OPTIONS`）を足すときの説明を加えた。
- ライセンスヘッダーの検査: 計画の Step 8 は「ライセンスヘッダーの検査（新しいスクリプトを含む）」としていた。`./gradlew verify` のヘッダーの検査は、Java・Gradle の Kotlin DSL（Spotless）と画面のファイル（`check-license-header.mjs`）だけが対象で、シェルのスクリプトは対象外である。そのため、新しいスクリプトのヘッダーは、既存の `Dockerfile` の先頭の 13 行と同じであることを `diff` で確かめた（一致）。`verify` の対象を広げる変更は、この段では行っていない。
- Gitleaks: `verify` の Gitleaks はコミットの履歴を調べるため、まだコミットしていない変更は対象にならない。変更したファイルとディレクトリ（`docker/`・`perf/`・`README.md`・`.env.example`・`Dockerfile`・`compose.yaml`）を `gitleaks detect --no-git` で別に調べ、指摘は無かった。コミットの直前には pre-commit のフックでも実行される。
- 確かめのスクリプトの確かめの項目に、計画に無かった「空の値（`MASTERSMITH_JAVA_OPTIONS=`）でも 75%」を加えた。`.env` で値を空にした行は空の値として渡る（`.env.example` の説明）ため。
- 計画には無い確認として、一時のコンテナでアプリを実際に起動し、NMT の手順と穏やかな停止を確かめた（上の Step 6）。そのため `eclipse-temurin:25.0.4_7-jdk-noble` のイメージを取得した。

## 残る点（後の段へ）

- colima の VM の作り直し（FR1.1・FR1.2）、F3 の内訳の測定（FR4）、負荷の試験（FR5、NFR1〜NFR3）は Build and Test の段で行う。`perf/README.md` の末尾の注記は、その結果に合わせて書き換える。
- この PC の `.env` の変更（FR2.2）は Deployment Execution の段で行う。
- 配備したアプリ（`mastersmith-app-1`）は、直す前のイメージ（`sha256:27c4d08f34ec…`）のまま動いている。タグ `mastersmith:local` は直した後のイメージを指す。タグ `mastersmith:pre-fix` は確かめのために残してあり、要らなくなったら `docker image rm mastersmith:pre-fix` で消せる（配備したコンテナが使うイメージそのものは消えない）。
