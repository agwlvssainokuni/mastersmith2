# 要件定義 — 高い負荷でのメモリの上限による停止（F3）と CPU 2 でのログインの遅さ（F4）の修正

## 意図の分析

- 依頼: 「高い負荷でアプリのコンテナがメモリの上限で止まる（F3）と、CPU 2 でログインが目標の 1 秒を超える（F4）を、colima の VM の性能を上げて直す」[desc]
- 種類: 不具合の修正（Workflow-selected scope: bugfix）[scope]。深さは Minimal。
- 目的: 想定の規模（同時にログインする利用者 10 名）で、ログインの応答の 95 パーセンタイル（p95）を目標の 1 秒以内にする。あわせて、高い負荷をかけてもアプリのコンテナがメモリの上限で止まらないようにする。
- これまでの観測: 前の Intent の負荷の試験（CPU の上限 2、メモリ 1GB、k6 で同時 10、考える時間なし）では、次の結果だった（`aidlc/spaces/default/intents/260922-auth-audit-base/operation/performance-validation/test-results.md`）。
  - ログインの p95: 成功 約 1,615〜1,621 ms、失敗 約 1,570 ms（F4）
  - トークンの更新を毎秒約 3,000 件で流すと、約 35 秒でコンテナが OOMKilled（メモリの最大 1021MiB / 1GiB、終了コード 137）で止まった（F3）
- 原因（コードの調査の結果。`aidlc/spaces/default/codekb/mastersmith2/code-quality-assessment.md` の TD-6〜TD-10）:
  - F4: パスワードの照合（bcrypt cost 12、1 回 278 ms）は CPU を使う。同時 10 件で 2 つの CPU を分け合うため、1 件の応答が延びる。照合に使える CPU は、コンテナの `cpus`（`.env` の `MASTERSMITH_CONTAINER_CPUS`）と VM の CPU の数の小さい方で頭打ちになる。
  - F3: F3 はコンテナのメモリの上限 1g（`compose.yaml` と `docker/perf/compose.yaml` に直書き）の内側で起きている。JVM は `Dockerfile` の `ENTRYPOINT` の `-XX:MaxRAMPercentage=75.0`（最大ヒープ 768MB）だけを指定している。そのため、ヒープ以外に使えるのは約 256MB である。**VM のメモリを増やすだけでは F3 は直らない見込み**である。
  - この PC は Apple M2（CPU 8・メモリ 16GB）で、colima の VM は CPU 2・メモリ 2GiB である（読み取りだけで確認）。
- 直し方の方針: VM を CPU 4・メモリ 6GiB に広げる。加えて、コンテナのメモリの上限と JVM の設定を環境変数で変えられるようにし、この PC では上限を 2g にする。既定の値は変えない [Q1][Q2][Q5]。

## 機能要件

### FR1 colima の VM の性能

- **FR1.1** この PC の colima の VM を、CPU 4・メモリ 6GiB にする [Q1]。
  - 合格の条件: `colima list` の CPUS が 4、MEMORY が 6GiB である。`docker info` の CPU の数（NCPU）が 4 である。
- **FR1.2** VM を作り直すと、動いているアプリ（`mastersmith-app-1`）も止まる。そのため、実施の前に依頼者の確認を得る。作り直した後は、アプリを起動し直してヘルスチェックが healthy になることを確かめる [assumption]。
  - 合格の条件: 実施の前の確認と、作り直した後の healthy の確認が、作業の記録に残っている。
- **FR1.3** colima の VM の設定はリポジトリの外にあり、コミットで固定できない。そのため、設定の手順と確かめ方（`colima list`・`docker info`）を README に書く（FR6.1）。

### FR2 コンテナのメモリの上限を環境変数で変えられるようにする

- **FR2.1** アプリのコンテナのメモリの上限を、環境変数で変えられるようにする。対象は配備用（`compose.yaml`）と負荷の試験用（`docker/perf/compose.yaml`）の両方である。環境変数を設定しないときの既定値は、今と同じ 1g とする [Q2][Q5]。
  - 変数の名前は、既存の `MASTERSMITH_CONTAINER_CPUS` にそろえて `MASTERSMITH_CONTAINER_MEMORY` とする [assumption]。
  - 合格の条件: 環境変数を設定せずに起動したコンテナのメモリの上限が 1GiB である（`docker inspect` の `HostConfig.Memory` が 1073741824）。2g を設定したときの上限が 2GiB（2147483648）である。
- **FR2.2** この PC の `.env` で、メモリの上限を 2g、`MASTERSMITH_CONTAINER_CPUS` を 4 にする [Q5][assumption]。`.env` はコミットしない（`project.md` の Forbidden）。
  - 合格の条件: 配備したコンテナの上限が、メモリ 2GiB・CPU 4 である（`docker inspect` の `HostConfig.Memory` と `HostConfig.NanoCpus`）。

### FR3 JVM の設定を環境変数で変えられるようにする

- **FR3.1** JVM の設定を、イメージを作り直さずに環境変数で渡せるようにする。変えられる設定には、ヒープの割合と、ヒープ以外の上限（メタ領域・スレッドのスタック・直接バッファなど）を含む [Q2]。
  - 今の `ENTRYPOINT` には `-XX:MaxRAMPercentage=75.0` が直書きされている。そのため `JAVA_TOOL_OPTIONS` で割合を渡しても、コマンド行の値に上書きされる見込みである（TD-8）。渡した値が実際に効く方式にする。
  - 変数の名前と渡し方はコード生成で決める [assumption]。
- **FR3.2** 環境変数を渡さないときの JVM の動作は、今と同じにする。ヒープはコンテナのメモリの上限の 75%、タイムゾーンは Asia/Tokyo、停止の合図（SIGTERM）は Java が直接受け取る [Q2][assumption]。
  - 合格の条件: 環境変数なしで起動した JVM の最大ヒープが、上限の 75% である（`jcmd <pid> VM.flags` などで確かめる）。環境変数で割合を変えると、その値が効く。`docker compose stop` で穏やかに停止する。
- **FR3.3** 負荷の試験環境（`docker/perf/compose.yaml`）でも、同じ環境変数で JVM の設定を変えられるようにする [assumption]。

### FR4 F3 の原因の内訳を測って記録する

- **FR4.1** F3 が起きたトークンの更新の場面（refresh）で、JVM のメモリの内訳（ヒープとヒープ以外。ヒープ以外はメタ領域・スレッド・コードキャッシュ・直接バッファなど）を測って記録する。測り方は Native Memory Tracking（NMT）などとする [Q4]。
  - 測る条件: 前回と同じ上限 1g（F3 が起きる条件）と、修正後の上限 2g の両方で測る [assumption]。
  - NMT などの計測は応答時間に影響しうる。そのため、応答時間を判定する回（FR5）とは分けて行う [assumption]。
  - 合格の条件: 2 つの条件それぞれで、ヒープとヒープ以外の使用量の推移（少なくとも開始時と、止まる直前または試験の終わり）が記録されている。F3 の原因がヒープとヒープ以外のどちらにあるかの見立てが書かれている。
- **FR4.2** 測った結果、アプリのコードに原因（保持の漏れなど）がありそうな場合は、この作業では直さない。記録して依頼者に伝え、別の作業にする [assumption]。

### FR5 直ったことの確かめ（負荷の試験）

- **FR5.1** 変更の後に、前回と同じ手順の負荷の試験を、使い捨ての環境（`docker/perf/compose.yaml`）で行う。試験は `perf/README.md` と `perf/k6/scenarios.js` に従う。k6 は同時 10、考える時間なし、場面ごとに 60 秒とする。環境は配備と同じ値（メモリの上限 2g、CPU 4、VM は CPU 4・メモリ 6GiB）とする [Q3][assumption]。
- **FR5.2** 次をすべて満たすことを合格とする [Q3]。
  - ログインの成功（loginSuccess）と失敗（loginFailure）の p95 が、どちらも 1 秒（1,000 ms）以内である
  - トークンの更新（refresh）の場面を 60 秒流しても、アプリのコンテナが止まらない（OOMKilled が false で、想定と違う応答が 0 件）
- **FR5.3** FR5.2 を満たさないときは、作業を止めて依頼者に相談する。目標を緩めて「満たした」ことにはしない。bcrypt の cost などの見直しは、この作業で勝手に行わない [Q3]。
- **FR5.4** 試験の結果（場面ごとの件数・p95・想定と違う応答・OOMKilled の有無・コンテナのメモリの最大）を、前回の値と並べて記録する。

### FR6 文書と手順を新しい VM の大きさに合わせる

- **FR6.1** README の colima の例を、新しい VM の大きさ（CPU 4・メモリ 6GiB）に合わせて直す。21 行目（`--cpu 4 --memory 4`）と 232 行目（`--cpu 2 --memory 4`）が食い違っているので、統一する [Q4]。
- **FR6.2** README の環境変数の表と `.env.example` に、FR2.1 と FR3.1 の環境変数を加える。既定値と意味を書く。`.env.example` のコメント（「例: colima の既定の 2」）は、新しい VM の大きさに合わせて直す [Q4]。
- **FR6.3** `compose.yaml` のコメントを新しい VM の大きさに合わせて直す。対象は、CPU の説明と、`lgtm` の「colima の VM（メモリ 約 2GiB）」の前提である [Q4]。
- **FR6.4** `perf/README.md` を次のように直す [Q4]。
  - CPU 2 に固定した手順（一時の `app.env` と `export` の `MASTERSMITH_CONTAINER_CPUS=2`）を、新しい値（CPU 4、メモリの上限 2g）にする
  - 末尾の F3 の注記（CPU 2・メモリ 1GB で refresh が 30〜40 秒で止まる）を、FR5 の結果に合わせて書き換える
- **FR6.5** 既定の 1g のままでは F3 が再び起きうることを、既知の制約として README に書く。書く内容は、その負荷の目安と、上限を上げる方法（FR2.1）である [Q5]。
  - 合格の条件: FR6.1〜FR6.5 の記述が、それぞれの文書にある。

### FR7 既存の検査の維持

- **FR7.1** 統合の前の 1 コマンドの検査（`./gradlew verify`）がすべて通る。
- **FR7.2** アプリの Java のコード（`backend/src/main/java/`）は変えない。変えるのは、コンテナと JVM の起動の設定（`Dockerfile`・compose）、文書、手順である [Q2][assumption]。

## 非機能要件

- **NFR1 ログインの応答時間**: 同時 10 件のログイン（成功・失敗とも）の p95 が 1 秒以内である（FR5 で確かめる。前の Intent の U2-NFR1.1 と同じ目標）。
- **NFR2 高い負荷での停止なし**: トークンの更新を同時 10、考える時間なしで 60 秒流しても、メモリの上限 2g のコンテナが止まらない（FR5 で確かめる）。
- **NFR3 VM の資源の見積もり**: 配備したアプリ（2g）、負荷の試験環境（2g）、手元の監視 `lgtm`（900m）の合計は約 4.9GB である。これが VM のメモリ 6GiB の内側に収まる [Q1][Q5]。
- **NFR4 品質の下限の維持**: カバレッジの下限（行 80%・分岐 70%）を下げず、除外も増やさない（`team.md` の Testing Posture）。
- **NFR5 秘密情報**: `.env` や一時の環境ファイルの値をコミット・記録・表示しない。新しい環境変数（メモリの上限・JVM の設定）の値は秘密情報ではない。

## 制約

- colima の VM の設定は PC の上の操作で、リポジトリの外にある。確かめる手段は `colima list`・`docker info` である。
- 配備先は開発者の PC 上のコンテナだけである（`team.md` の Deployment）。
- 負荷の試験は、配備した環境とは別の使い捨ての環境で行う（`project.md` の Testing Posture）。
- メモリの上限の既定値は 1g のまま変えない [Q5]。
- 目標（ログインの p95 1 秒）は緩めない（`org.md` の Testing Posture、`project.md` の Testing Posture）。

## 前提

- [assumption] 環境変数の名前 `MASTERSMITH_CONTAINER_MEMORY` は、既存の `MASTERSMITH_CONTAINER_CPUS` にそろえた提案である。質問では決めていない。
- [assumption] JVM の設定の環境変数の名前と渡し方は、コード生成で決める。渡さないときの動作は今と同じにする。
- [assumption] 負荷の試験環境も、配備と同じ環境変数に従う。試験は配備と同じ値（2g・CPU 4）で行う。
- [assumption] この PC の `.env` の `MASTERSMITH_CONTAINER_CPUS` を 4 にする（今の値は `.env` を開いていないため未確認）。
- [assumption] colima の VM を作り直す前に、依頼者の確認を得る。
- [assumption] `perf/README.md` の手順 0（配備したアプリを止める）は残す。VM 6GiB ではメモリの点では同時に動かせるが、CPU 4 を配備したアプリと分け合うと、測定値に影響しうるためである。理由の書き方は、メモリではなく CPU の取り合いに直す。
- CPU 4 でのログインの p95 は、約 700 ms の見積もりである（278 ms × 10 ÷ 4）。まだ測っていない。

## 範囲の外

- コンテナ全体のメモリ（ヒープ以外も含む）を見る監視と警報の追加（TD-11）[Q4]
- パスワードのハッシュの計算量（bcrypt の cost）の見直し [Q3]
- k6 の CPU を制限した測定 [Q3]
- トークンの更新の処理（`TokenRefreshService`）などのアプリのコードの修正（FR4.2）
- Tomcat の同時処理のスレッドの上限の設定
- ヘルスチェックの誤った DOWN（F1）
- メモリの上限の既定値の引き上げ [Q5]

## 残る未確定の点

- CPU 4 でログインの p95 が 1 秒以内に収まるかは、まだ測っていない。k6 も同じ VM の CPU を使うため、測った値には k6 の分が混ざりうる（TD-10）。届かなければ FR5.3 に従って相談する。
- メモリの上限 2g で F3 が起きなくなるかは、まだ確かめていない。F3 の原因がヒープとヒープ以外のどちらにあるかは、FR4 で測る。
- 設定が環境変数どおりに効くことの確認（FR2.1・FR3.2 の合格の条件）を、自動のテストにするか、手順での確認にするかは、コード生成で決める。
  - `project.md` の Mandated（不具合を再現するテストを同じコミットに含める）には、FR5 の負荷の試験の結果と、この確認で対応する。

## Sources

- [desc] Initial description: 高い負荷でアプリのコンテナがメモリの上限で止まる（F3）と、CPU 2 でログインが目標の 1 秒を超える（F4）を、colima の VM の性能を上げて直す
- [scope] Workflow-selected scope: bugfix
- [Q1]〜[Q5]: `aidlc/spaces/default/intents/260923-colima-spec-up/inception/requirements-analysis/requirements-analysis-questions.md` の回答
- コードの調査の結果: `aidlc/spaces/default/codekb/mastersmith2/business-overview.md`・`architecture.md`・`code-structure.md`・`code-quality-assessment.md`（TD-6〜TD-11）
- F3・F4 の観測: 前の Intent の負荷の試験（`aidlc/spaces/default/intents/260922-auth-audit-base/operation/performance-validation/test-results.md`）

## Assumptions & Open Questions

- 環境変数の名前 `MASTERSMITH_CONTAINER_MEMORY` は提案で、質問では決めていない（前提を参照）。
- JVM の設定の口の名前と渡し方は、コード生成で決める（FR3.1）。
- CPU 4 で p95 1 秒以内に収まるかと、2g で F3 が起きなくなるかは、FR5 で確かめるまで未確定である（残る未確定の点を参照）。
- 設定の効き方の確認を自動のテストにするかどうかは、コード生成で決める（残る未確定の点を参照）。
