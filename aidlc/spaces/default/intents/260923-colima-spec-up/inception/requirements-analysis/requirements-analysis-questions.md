# Requirements Analysis — 質問

作業: 高い負荷でアプリのコンテナがメモリの上限で止まる（F3）と、CPU 2 でログインが目標の 1 秒を超える（F4）を、colima の VM の性能を上げて直す

前提となる事実（コード知識ベース `aidlc/spaces/default/codekb/mastersmith2/code-quality-assessment.md` の TD-6〜TD-11 と、この PC の読み取りだけの確認）:

- この PC は Apple M2（CPU 8・メモリ 16GB）。colima の VM は現在 CPU 2・メモリ 2GiB。配備したアプリ（mastersmith-app-1）が動いている。
- F3 は、コンテナのメモリの上限 1g（`compose.yaml` と `docker/perf/compose.yaml` に直書き）の内側で起きている。そのため VM のメモリを増やすだけでは直らない見込み。JVM は `Dockerfile` の `-XX:MaxRAMPercentage=75.0`（最大ヒープ 768MB）だけを指定している。
- F4 は、VM の CPU に加えて `.env` の `MASTERSMITH_CONTAINER_CPUS` も上げないと効かない（`compose.yaml` の既定は 4）。
- README の colima の例が食い違っている（21 行目は CPU 4・メモリ 4、232 行目は CPU 2・メモリ 4）。

## Q1. colima の VM の大きさ

VM の大きさを決めると、アプリ（上限 1g 以上）・使い捨ての負荷の試験環境（1g）・手元の監視 lgtm（900m）を同時に動かせるかどうかが決まります。この PC の実機は CPU 8・メモリ 16GB です。

A. CPU 4・メモリ 4GiB（README 21 行目の例に合わせる）
B. CPU 4・メモリ 6GiB（アプリ・負荷の試験環境・監視を同時に動かす余裕を持たせる）
C. CPU 4・メモリ 8GiB（コンテナのメモリの上限を 2g に上げても、すべて同時に動かせる）
D. CPU 6・メモリ 8GiB（k6 の取り分も含めて CPU にも余裕を持たせる）
X. Other (please specify)

[Answer]: B

## Q2. F3（メモリの上限で止まる）の直し方

VM を大きくしても、コンテナの上限 1g が変わらなければ F3 は同じ負荷で再び起きる見込みです（未検証）。VM の拡張に加えて何を変えますか。

A. コンテナのメモリの上限を環境変数で変えられるようにし（既定 1g のまま）、この PC では `.env` で 2g にする。JVM の設定は変えない（ヒープは上限の 75%）
B. コンテナのメモリの上限を環境変数で変えられるようにし、既定を 2g に上げる。JVM の設定は変えない
C. コンテナの上限は 1g のまま、JVM の設定（ヒープの割合を下げる、ヒープ以外に上限を付ける）を環境変数で変えられるようにして調整する
D. 上限の変更（A か B）と、JVM の設定を変えられる口（C）の両方を入れる
E. VM の拡張だけにとどめ、F3 は既知の制約として README に記録する（コードや設定は変えない）
X. Other (please specify)

[Answer]: D

## Q3. 直ったことの確かめ方と、目標に届かないときの扱い

前回 F3・F4 を見つけたのと同じ負荷の試験（k6。ログインの同時 10 件、トークンの更新を毎秒約 3,000 件で流す）を、変更の後に同じ手順で行う想定です。CPU 4 でのログインの p95 は約 700ms の見積もりですが、まだ測っていません。

A. 同じ試験を行い、ログインの p95 が 1 秒以内・更新の試験でアプリが止まらない（OOMKilled にならない）ことを合格とする。届かなければ作業を止めて相談する
B. A に加え、届かないときはこの作業の中でパスワードのハッシュの計算量（bcrypt の cost）の見直しまで検討する
C. A に加え、k6 の CPU を制限して、アプリの測定値に k6 の負荷が混ざらないようにして測る
X. Other (please specify)

[Answer]: A

## Q4. あわせて行うこと (select all that apply)

VM の性能を変えると、手順や文書の記述が実態とずれます。今回の作業に含めるものを選んでください。

A. README の colima の例（21 行目・232 行目）、`perf/README.md` の CPU 2 固定の手順と F3 の注記、`.env.example` と `compose.yaml` のコメントを、新しい VM の大きさに合わせて直す
B. コンテナ全体のメモリ（ヒープ以外も含む）を見る監視と警報を足す（今の警報はヒープだけを見ている）
C. F3 の原因の内訳（ヒープとヒープ以外）を測って記録する（JVM の Native Memory Tracking 等）
D. どれも含めない（VM の拡張と Q2 の変更だけにする）
X. Other (please specify)

[Answer]: A, C

## Q5（Q2 の追加の質問）. コンテナのメモリの上限の既定値

Q2 で D（上限の変更と JVM の設定の口の両方）を選びました。D は「A か B」の上限の変更を含むため、どちらかを決めます。なお、VM 6GiB（Q1: B）では、アプリ 2g・負荷の試験環境 2g・監視 900m の合計が約 4.9GB で、同時に動かせる見込みです。

A. 既定は 1g のまま、この PC では `.env` で 2g にする（配備先ごとに決める）
B. 既定を 2g に上げる（`.env` で変えなければ 2g）
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

回答のまとめ:

- Q1: colima の VM を CPU 4・メモリ 6GiB にする（B）
- Q2: VM の拡張に加え、コンテナのメモリの上限の変更と、JVM の設定を変えられる口の両方を入れる（D）
- Q5: メモリの上限は環境変数で変えられるようにし、既定は 1g のまま。この PC では `.env` で 2g にする（A）
- Q3: 前回と同じ k6 の試験（ログインの同時 10 件、トークンの更新を毎秒約 3,000 件）を行い、ログインの p95 が 1 秒以内、更新の試験でアプリが止まらない（OOMKilled にならない）ことを合格とする。届かなければ作業を止めて相談する（A）
- Q4: 文書と手順（README の colima の例、`perf/README.md`、`.env.example`、`compose.yaml` のコメント）を新しい VM に合わせて直す（A）。F3 の原因の内訳（ヒープとヒープ以外）を Native Memory Tracking 等で測って記録する（C）。コンテナ全体のメモリの監視は今回含めない

回答から置く前提（要件に「前提」として書く）:

- JVM の設定の口は、イメージを作り直さずに環境変数で渡せるものとし、渡さないときの動作は今と同じ（ヒープは上限の 75%）にする。具体的な変数名や渡し方はコード生成で決める
- 負荷の試験環境（`docker/perf/compose.yaml`）も、同じ環境変数でメモリの上限と JVM の設定を変えられるようにし、試験は配備と同じ値（2g、CPU 4）で行う
- この PC の `.env` の `MASTERSMITH_CONTAINER_CPUS` を 4 にする
- colima の VM の作り直し（止めて CPU 4・メモリ 6GiB で起動）は、動いているアプリも止まるため、実施の前に依頼者の確認を得る
- bugfix の再発防止の確かめは、上の k6 の試験に加え、設定が環境変数どおりに効くこと（`docker compose config` や起動したコンテナの上限の値）をテストまたは手順で確かめる

Does this all look correct before I generate the requirements artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
