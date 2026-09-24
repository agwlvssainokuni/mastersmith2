# 障害ごとの手順書（runbooks）

起こりうる障害ごとに「見分け方」「重さ」「手順」「確かめ方」を書く。重さの決め方と流れは `incident-plan.md`、誰が何をしてよいかは `escalation-matrix.md`。既にある手順は書き写さず参照する。

- **既にある手順（参照先）**: 版の戻し（`aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/deployment-pipeline/rollback-runbook.md`、README の「戻し方」）、内部DB のバックアップと戻し方（README の「内部DBのバックアップと戻し方」）、監査ログの確かめ方（README の「監査ログの確かめ方」）、内部DB のファイルの伸び（README の「DSL の管理の API（U4）」の「既知の制約」）、同時の要求と接続プール（README の「監査ログ（U4）」の「既知の制約」）、メモリの上限（README の「コンテナの資源の上限」）、対象DB（README の「対象DB」「手元で試す対象DB（compose の profile）」）。
- **記号**: 手順の【承認】は、AI が行うときに依頼者の承認が要る操作（`escalation-matrix.md` 3節）。【依頼者】は依頼者が行う操作（`.env` の編集・秘密情報が要るログイン）。印の無いものは読み取りだけ。
- **ログの文言**: 下の「」の文言は、`backend/src/main/java/` のソース・README・警報のファイルで確かめたもの。確かめられなかったものは「推測」と書いた（15節に一覧）。
- **コマンドに秘密の値を書かない。** `.env` は開かない。`docker inspect` は `--format` で項目を絞る（絞らないと環境変数の値が出る）。

## 0. どの障害でも最初に行うこと

1. 状態を見る。

   ```bash
   docker compose --profile targetdb-postgres ps
   docker inspect mastersmith-app-1 --format '{{.State.Status}} health={{.State.Health.Status}} OOMKilled={{.State.OOMKilled}} exit={{.State.ExitCode}} finished={{.State.FinishedAt}}'
   ```

2. ログを見る（1行1件の JSON。`level`・`message`・`traceId` とキーと値の項目）。

   ```bash
   docker compose logs --no-log-prefix app | grep -c '"level":"ERROR"'          # ERROR の件数
   docker compose logs --no-log-prefix app | grep '"level":"ERROR"' | tail -n 5  # 直近の ERROR
   docker compose logs --no-log-prefix app | grep '"level":"WARN"' | tail -n 20  # 直近の WARN
   ```

   1つの要求を追うときは、エラー応答の `traceId` の値で `grep` する。ログはコンテナごとに最大 30MB（10MB × 3、`compose.yaml` の `logging`）で、古いものから消える。

3. **コンテナを作り直す（`up -d`）・戻す前に、ログをリポジトリの外へ保存する。** 作り直すと今のコンテナのログは消える（`restart`・`stop`・`start` では消えない）。

   ```bash
   mkdir -p ~/.mastersmith-incidents && chmod 700 ~/.mastersmith-incidents
   docker compose logs --no-log-prefix --timestamps app > ~/.mastersmith-incidents/app-$(date +%Y%m%d%H%M).log
   chmod 600 ~/.mastersmith-incidents/app-*.log
   ```

   保存したログには、メールアドレス（初期管理者の INFO、監査の書き込みの失敗の ERROR）が入りうる。リポジトリの中に置かない。

4. `incident-plan.md` 3節の形で記録を始め、重さを決める。

## RB-01 アプリが止まる・healthy にならない

**見分け方**

| 手がかり | 内容 |
|---|---|
| 症状 | 画面が開かない・ログインできない。`ps` で `app` が `exited`・`unhealthy`・`starting` のまま（起動は通常約 9 秒、最長で約 2 分） |
| 警報 | 「アプリの停止（指標が届かない）」（手元の監視と外部エクスポートを有効にしているときだけ評価される） |
| 状態 | 0節の `docker inspect`。`OOMKilled=true exit=137` はメモリの上限（RB-02）。`exit=143` は穏やかな停止（誰かが止めた）。`exit=1` は起動の失敗が多い |
| 健全性の記録 | `docker inspect mastersmith-app-1 --format '{{json .State.Health}}'`（直近の確かめの結果） |
| ログ（起動の失敗） | 署名鍵: 「アクセストークンの署名鍵 mastersmith.auth.signing-key（環境変数 MASTERSMITH_AUTH_SIGNING_KEY）が設定されていないか、Base64 で 32 バイト以上の値ではありません」。Flyway の移行・検証の失敗（文言は推測。Flyway と Spring Boot の起動の失敗の文言で、このリポジトリでは確かめていない） |
| ログ（動いているが unhealthy） | WARN「内部DBの確認が制限時間内に終わりませんでした」（`reason` `timeout`）・「内部DBの確認に失敗しました」（`reason` `error`）・「内部DBの確認を行えませんでした」。接続プールの待ち（RB-08）や内部DB の問題 |
| PC・VM | PC の再起動や colima の停止の後は、`restart: "no"` のためコンテナは起動しない。`colima list` が `Stopped` なら VM が止まっている |

**重さ**: 高。

**手順**

1. 0節を行う。
2. VM が止まっていたら【承認】`colima start` の後、【承認】`docker compose --profile targetdb-postgres start`（既にあるコンテナをそのまま起動する。作り直さない）。
3. `OOMKilled=true` なら RB-02 へ。
4. 起動の失敗のログが署名鍵・`.env` の誤りなら、【依頼者】`.env` を直し、【承認】`docker compose --profile targetdb-postgres up -d app` で作り直す（0節の 3 で先にログを保存する）。
5. 動いているが unhealthy なら、ログの WARN の `reason` を見る。接続プールの待ちなら RB-08。一時的なものは【承認】`docker compose --profile targetdb-postgres restart app` で起動し直す。
6. 原因が分からない、起動し直しても直らない、配備の直後に起きた場合は、RB-10（戻す）を依頼者が判断する。Flyway の失敗なら `rollback-runbook.md` 4節（データも戻す）。

**確かめ方**: `app` が `healthy`、`curl -s http://localhost:8080/actuator/health` が `{"status":"UP"}`、起動の後のログに ERROR が無い、【依頼者】README の「コンテナでの起動と確認」のスモークテスト。

## RB-02 メモリの上限（2g）に近い・OOMKilled

**見分け方**

| 手がかり | 内容 |
|---|---|
| 状態 | `docker inspect mastersmith-app-1 --format '{{.State.OOMKilled}} {{.State.ExitCode}}'` が `true 137`（上限で止められた）。止まったままになる（`restart: "no"`） |
| 使用量 | `docker stats --no-stream mastersmith-app-1`（`MEM USAGE / LIMIT` と `MEM %`）。上限は `docker inspect mastersmith-app-1 --format '{{.HostConfig.Memory}}'` が `2147483648` |
| 実測の目安 | 10MB の DSL の投入と適用を重ねたとき、プロセスのメモリ（`anon`）が最大 1,959〜1,986MB（上限の約 92%）まで使い、OOMKilled にはならなかった（Build and Test の U4-STORAGE-RUN）。1g の上限では、高い負荷で約 35 秒で OOMKilled になった（README の「既知の制約（メモリの上限 1g と高い負荷）」） |
| 警報 | 「JVM のメモリの不足の兆し」（ヒープの使用率が 5 分間 85% を超えた）。ヒープ以外の分は含まない |
| VM のログ | `colima ssh -- sudo dmesg` の出力のうち `Memory cgroup out of memory: Killed process ...` の行（`grep -i 'out of memory'` で絞る） |

**重さ**: OOMKilled で止まったら高。止まっていない（近いだけ）なら低。

**手順**

1. 0節を行う（止まったコンテナのログも読める）。
2. 直前の操作を見る: 大きな DSL の投入と適用を重ねたか（`grep 'DSL の操作を終えました'` で件数と `dsl.operation`）、負荷の試験の環境（`perf/README.md`）や手元の監視を同時に動かしていないか（`docker ps`）。
3. 止まっているなら【承認】`docker compose --profile targetdb-postgres start app` で起動する（作り直さない）。
4. 近いだけなら、大きな DSL の操作をやめ、内部DB のファイルの大きさも見る（RB-03。動いている間はファイルとページキャッシュが増える）。落ち着いてから【承認】`docker compose --profile targetdb-postgres restart app` で起動し直すとメモリも内部DB のファイルも戻る。
5. 上限そのものを見直すときは、README の「コンテナの資源の上限」（`MASTERSMITH_CONTAINER_MEMORY`・`MASTERSMITH_JAVA_OPTIONS`、VM の大きさ）に従う。【依頼者】`.env` の変更と【承認】作り直し。

**確かめ方**: `healthy`・`OOMKilled=false`、`docker stats --no-stream` の `MEM %` が落ち着いている、スモークテスト。

## RB-03 内部DB のファイルの伸び（U4-STORAGE-RUN）

**見分け方**

| 手がかり | 内容 |
|---|---|
| 大きさ | `docker run --rm -v mastersmith_mastersmith-data:/data:ro eclipse-temurin:25.0.4_7-jre-noble du -sh /data`（アプリを止めずに読み取りだけ。2026-09-25 の配備の後で 164K） |
| 目安 | **300MB を超えたら**手当てする（README の「DSL の管理の API（U4）」の「既知の制約」） |
| 理由 | アプリが動いている間は、DSL の投入と適用のたびに本文の大きさの分（10MB の DSL なら約 10.8MB）ずつ増え、頭打ちにならない（21 回で約 278MB、40 回で約 483MB） |
| 警報 | 無い（大きさの指標がアプリに無い。`alarms.md` 2節） |

**重さ**: 低（依頼者が Build and Test で受け入れた既知の制約）。ディスクが尽きて書き込みに失敗しはじめたら高。

**手順**

1. ディスクの空きを見る: `colima ssh -- df -h /`（Environment Provisioning の時点で 64G 空き）。
2. 【承認】`docker compose --profile targetdb-postgres restart app` で起動し直す。止めるときに `;DEFRAG_ALWAYS=TRUE` でファイルが詰め直される（約 1 秒）。止まっている間の要求は失敗する。
3. 大きな DSL の操作を続ける前に、内部DB を複写しておく（README の「内部DBのバックアップと戻し方」、【承認】アプリを止める）。
4. `MASTERSMITH_DB_URL` を `.env` で上書きしている場合は、`;DEFRAG_ALWAYS=TRUE` が付いているかを【依頼者】確かめる（付いていないと起動し直しても縮まない。README の「環境変数」）。

**確かめ方**: 起動し直した後の `du -sh /data` が小さくなった（測定では約 16MB。圧縮の効きにくい本文では残る履歴の大きさに近くなりうる）、`healthy`、DSL の管理画面で適用中の版と履歴が前と同じ。

## RB-04 見本の対象DB が止まった・応答しない

**見分け方**

| 手がかり | 内容 |
|---|---|
| 症状 | 画面の「スキーマを読み込む」が「接続できない」（503 `TARGET_DB_UNAVAILABLE`）。プレビューの表示は失敗せず、照合の警告に「対象DB が時間内に応答しなかったため、照合できませんでした。」・「対象DB に接続できなかったため、照合できませんでした。」が出る |
| アプリのログ | WARN「対象DB のスキーマを読めませんでした」（`purpose`・`reason` `TIMEOUT` または `CONNECTION_FAILED`・`sqlState`）。`sqlState` の読み方は README の「対象DB」（`08` 接続できない、`28` 認証の失敗、`57014` 問い合わせの打ち切り、`TIMEOUT` で SQLState 無しは接続の待ちの間に応答が無い）。生成の失敗は、さらに WARN「要求をエラー応答に変換しました」（`code` `TARGET_DB_UNAVAILABLE`） |
| 時間 | 応答しない対象DB では、照合が最悪 接続 3 秒＋5 秒 × 4 回（約 23〜28 秒）かかる。その間ほかの重い操作（生成・投入・プレビューの表示・履歴からの戻し）は `DSL_BUSY`（RB-07） |
| 見本の DB の状態 | `docker compose --profile targetdb-postgres ps targetdb-postgres`、`docker inspect mastersmith-targetdb-postgres-1 --format '{{.State.Status}} OOMKilled={{.State.OOMKilled}} exit={{.State.ExitCode}}'`、`docker compose exec targetdb-postgres pg_isready -U target_admin -d business`（動いているときだけ） |
| 見本の DB のログ | `docker compose logs targetdb-postgres` の末尾（`tail -n 50`）の ERROR・FATAL の行。Environment Provisioning の確かめによる 11 件は障害ではない |
| 警報 | 無い。5xx の割合（503 を含む）が 1% を超えると「5xx の割合の増加」が出ることがある |

**重さ**: 低（ログイン・監査・適用・破棄・履歴・ダウンロードは対象DB に触れない。U4 の `reliability-design.md` 3節）。

**手順**

1. 見本の DB が止まっていたら【承認】`docker compose --profile targetdb-postgres start targetdb-postgres`。
2. 動いているのに応答しないなら、見本の DB のログとメモリ（上限 512MB、`OOMKilled`）を見て、【承認】`docker compose --profile targetdb-postgres restart targetdb-postgres`。
3. `sqlState` が `28` で始まる（認証の失敗）なら RB-05。
4. 見本の DB のボリュームが壊れた、パスワードを替えたなどで作り直すときは、`rollback-runbook.md` 6節（【承認】ボリュームの削除。消してよいのは見本の DB のボリュームだけ）。
5. 配備した環境でドライバーのログを有効にしない（接続情報がログに残る。README の「対象DB」）。

**確かめ方**: `pg_isready` が `accepting connections`、【依頼者】画面の「スキーマを読み込む」でプレビューに `sales` のテーブルとビューが並ぶ（監査に `DSL_GENERATED` が残る。AI が行うときは送る前に伝える）、アプリのログに「対象DB のスキーマを読めませんでした」が新しく出ない。

## RB-05 対象DB の設定の誤り

**見分け方**

| 手がかり | 内容 |
|---|---|
| 症状 | 「スキーマを読み込む」が「未設定」（503 `TARGET_DB_UNCONFIGURED`）。照合の警告に「対象DB の接続先が設定されていないため、照合できませんでした。」 |
| 起動のログ | 一部だけ空・不正なら、起動のときに WARN「対象DB の設定に欠け・不正があるため、対象DB を使いません。項目を直して再起動してください」を1件（キー `items` に項目の名前だけ。例: `mastersmith.target-db.schema`。値は出ない）。7項目がすべて空なら WARN は出ず、対象DB を使わない |
| 認証の失敗 | 設定は通るが WARN「対象DB のスキーマを読めませんでした」の `sqlState` が `28` で始まる（パスワードの食い違いなど） |
| 権限の不足 | WARN「読める権限のカラムが無いテーブルを、スキーマの写しから除きました」（キー `tables`） |

**重さ**: 低。

**手順**

1. `docker compose logs --no-log-prefix app | grep '対象DB の設定に欠け・不正があるため'` で項目の名前を見る。
2. 【依頼者】`.env` の `MASTERSMITH_TARGET_DB_*` の7項目を直す（README の「手元で試す対象DB（compose の profile）」の手順 3。`MASTERSMITH_TARGET_DB_PASSWORD` は `MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD` と同じ値）。AI が確かめるときは【承認】項目があるかの件数だけ（`grep -c '^MASTERSMITH_TARGET_DB_TYPE=.' .env` など）。
3. 見本の DB のパスワードを後から変えた場合は、見本の DB には効かない。`rollback-runbook.md` 6節で作り直す。
4. 【承認】0節の 3 でログを保存し、`docker compose --profile targetdb-postgres up -d app` で作り直す（設定は起動のときにだけ読まれる）。
5. 権限の不足は、見本の DB の読み取りのアカウントの権限を見直す（README の「対象DB」の権限の例）。

**確かめ方**: 起動のログに「対象DB の設定に欠け・不正があるため」が無い、RB-04 の確かめ方と同じ。

## RB-06 起動のときに適用中の DSL を読めない

**見分け方**

| 手がかり | 内容 |
|---|---|
| 起動のログ | ERROR「適用中の DSL を読めないため、適用中の DSL が無い状態で起動します」を1件（キー `dsl.hash` 先頭 12 文字・`dsl.revisionId`・`dsl.errorKinds`）。アプリは起動を続け、`healthy` になる |
| 症状 | DSL の管理画面の今の状態で、適用中の版が無い（または後続の機能が DSL を使えない） |
| 保存してあるプレビューが読めないとき | ERROR「保存してある DSL が今の検証を通りません」（キー `dsl.hash`・`dsl.errorKinds`）と、想定外の失敗（500、ERROR「想定外のエラーが起きました」） |
| よくあるきっかけ | 配備で DSL の書式の版（`backend/src/main/resources/dsl/dsl-schema-v1.json`）や検証の決まりが変わり、前の版で適用した DSL が今の検証を通らない |
| 警報 | 「ERROR のログの増加」（5 分で 5 件を超えたとき。1件だけでは出ない） |

**重さ**: 低（ログイン・監査は動く）。配備の直後で、直す見込みが無く DSL を使う作業が止まるなら、依頼者の判断で高にして RB-10 を考える。

**手順**

1. `dsl.errorKinds` で、読めない理由の種類を見る。
2. 【依頼者】DSL の管理画面の「履歴」のタブで、今の検証を通る版を「プレビューに戻す」→ 確かめて「適用する」（戻しは今の検証にかけ直し、通らなければ 422 の誤りの一覧を示す。README の「DSL の管理の API（U4）」の「履歴からの戻し」）。
3. 通る版が無ければ、DSL を今の書式に直して「投入」するか、「スキーマを読み込む」で既定の DSL を作り直して適用する。
4. 書式の変更が意図しないものなら、コードの不具合として新しい Intent で直す（不具合を再現するテストと一緒に）。急ぐなら RB-10 で版を戻す。
5. 保存してあるプレビューが読めない場合は、【依頼者】画面でプレビューを「破棄する」（`DELETE /api/admin/dsl/preview`）。

**確かめ方**: DSL の管理画面の今の状態に適用中の版がある。次の起動（【承認】`restart`）のログに「適用中の DSL を読めないため」が出ない。

## RB-07 `DSL_BUSY` が続く

**見分け方**

| 手がかり | 内容 |
|---|---|
| 症状 | 画面に「ほかの処理中です。少し待ってからやり直してください」。API は 503 `DSL_BUSY` |
| ログ | WARN「要求をエラー応答に変換しました」（キー `code` `DSL_BUSY`、`status` 503）。INFO「DSL の操作を終えました」の `dsl.outcome` が `busy` |
| 指標 | ダッシュボードの「DSL の操作」の行の「結果ごとの件数」に `busy`、「操作ごとの時間」で長い `compare`・`generate` |
| 理由 | 重い処理（生成・投入・プレビューの表示・履歴からの戻し）はアプリ全体で同時に1つ。長い照合（応答しない対象DB で最悪 23〜28 秒）や、大きなスキーマの生成（問い合わせ1回の待ちの上限 20 秒）の間は断られる |
| 警報 | 無い。503 のため「5xx の割合の増加」が出ることがある |

**重さ**: 低。

**手順**

1. 少し（30 秒ほど）待ってやり直す。断られた要求は状態を変えず、監査にも残らない。
2. 続くなら、長く動いている操作を探す: `docker compose logs --no-log-prefix app | grep 'DSL の操作を終えました' | tail -n 5`（`dsl.durationMs`）と、WARN「対象DB のスキーマを読めませんでした」（`reason` `TIMEOUT`）。対象DB が応答しないなら RB-04。
3. ほかに操作していないのに数分続く（処理が終わらない）なら、【承認】`docker compose --profile targetdb-postgres restart app` で起動し直す。

**確かめ方**: 画面の操作が `DSL_BUSY` にならない。ログの `dsl.outcome` に `success` が出る。

## RB-08 監査の書き込みの失敗・接続プールの待ち

既知の制約は README の「監査ログ（U4）」の「既知の制約（同時の要求と接続プール）」を参照（ログイン・ログアウトは確定の後に2本目の接続で監査を書く。同時の要求がプールの上限 30 以上で欠けうる）。

**見分け方**

| 手がかり | 内容 |
|---|---|
| ログ | ERROR「監査イベントの記録に失敗しました」（キーに記録しようとした項目。メールアドレスを含む。パスワード・トークンは載らない。キー `exceptionType`）。WARN「監査イベントの記録に時間がかかりました」（`auditEventType`・`elapsedMs`、200 ミリ秒超え） |
| 接続の待ち | 待ちが時間切れ（5 秒）になった例外（Hikari の `SQLTransientConnectionException` と見られるが、推測） |
| 警報 | 「監査の書き込みの失敗」・「コネクションプールの待ち」（1 分続いた）・「監査の書き込みの遅れ」（1 日に 3 件以上）・「確認用 API の遅れ」 |
| 影響 | ログイン・ログアウト・DSL の操作・401／403 の応答は成功のまま。監査の記録だけが欠ける |

**重さ**: 低。欠けが続く・多い、または原因が内部DB の不調（ファイルの伸び・ディスクの不足）なら高。

**手順**

1. 件数と時刻: `docker compose logs --no-log-prefix app | grep -c '監査イベントの記録に失敗しました'`。報告には件数・種類・時刻だけを書き、メールアドレスを書き写さない。
2. 同時の要求の多さを見る（負荷の試験を配備したアプリに向けていないか、ダッシュボードの「コネクションプールの待ち」）。負荷の元を止める。
3. 内部DB の不調を見る（RB-03 の大きさ・ディスクの空き、RB-01 のヘルスチェックの WARN）。
4. 欠けた記録を補うかは依頼者が決める。補う手順は決まっていない（15節）。少なくとも、ERROR の時刻・種類・件数を障害の記録（リポジトリの外）に残す。
5. 続くなら、プールの上限（`MASTERSMITH_DB_MAXIMUM_POOL_SIZE`）の見直しを README の手順（起動と負荷の試験で確かめる）で行う。

**確かめ方**: 新しい ERROR「監査イベントの記録に失敗しました」が出ない、ダッシュボードの「コネクションプールの待ち」が 0。

## RB-09 ログインの失敗の多発・ロック・回り込みの試み・送り元の不一致

アプリは `127.0.0.1:8080` にだけ結び付き、PC の外からは届かない。多発するときは、PC の中の何か（負荷の試験の台本・ブラウザの拡張・誤った送り先の道具）をまず疑う。

**見分け方と初動（警報ごと）**

| 警報 | ログの文言（確かめ済み） | 初動 |
|---|---|---|
| ロックの多発（1 時間に 5 件超） | INFO「アカウントをロックしました」（キー `userId`） | 利用者 ID を見る。ロックは連続 5 回の失敗で 30 分（`MASTERSMITH_AUTH_LOCK_THRESHOLD`・`MASTERSMITH_AUTH_LOCK_DURATION`）で、時間で解ける。解く画面・API は無い。管理者がロックされたら 30 分待つ |
| 回り込みの試み（1 時間に 10 件超） | WARN「正規化されていないパスの要求を拒否しました」（キー `code` `REQUEST_REJECTED`。拒否したパスはログに出ない） | 要求の元（PC の中の道具）を探して止める |
| 送り元の不一致の多発（1 時間に 10 件超） | WARN「要求の送り元が自分の配信元と一致しないため拒否しました」（キー `originPresent`） | CSRF の試みか、ベースURLの設定の誤り（警報の説明）。`localhost` 以外の名前（`127.0.0.1` など）で画面を開いていないかを確かめる（推測の原因） |
| 管理画面への拒否の増加（1 時間に 20 件超） | WARN「権限が足りないため要求を拒否しました」（キー `code` `ACCESS_DENIED`） | 管理者でない利用者の要求。監査の `ACCESS_DENIED` で利用者と接続元IPを見る |

**重さ**: 低。依頼者の知らない利用者でのログインの成功、管理者でない利用者の管理画面の利用が見つかったら高（秘密情報の漏えいの疑い、RB-11）。

**手順**

1. ログで件数と時刻を見る（上の文言で `grep -c`）。
2. 利用者と接続元IPまで見るときは監査ログを読む。【承認】README の「監査ログの確かめ方」（アプリを止めて複写）か、既にある複写の読み取り。`LOGIN_FAILED`・`LOGIN_SUCCEEDED`・`ACCESS_DENIED` の件数と接続元IP。報告に値を書き写さない。
3. 要求の元が PC の中の道具なら止める。依頼者の知らないログインの成功があれば RB-11。

**確かめ方**: 同じログが新しく出ない。

## RB-10 配備の後の不具合（戻す）

戻し方は `rollback-runbook.md` を正とする（README の「戻し方」）。ここでは判断の要点だけを書く。

**見分け方**: 配備（`7bc1b68`、2026-09-25）の後に、RB-01 の起動の失敗・スモークテストの失敗・ログインや管理ができない・DSL の管理の想定外の失敗（500、ERROR「想定外のエラーが起きました」）が出た。

**重さ**: 高。

**手順**

1. 0節を行う（ログを保存してから戻す）。
2. 戻すかを依頼者が決める（`rollback-runbook.md` 1節の表）。見本の対象DB の問題（RB-04・RB-05）と内部DB のファイルの伸び（RB-03）では戻さない。
3. 【承認】`rollback-runbook.md` 2節で直前の版 `10742a3`（イメージ `mastersmith:pre-dsl`）に戻す。戻している間は `MASTERSMITH_IMAGE_TAG=pre-dsl` と `--no-build` を必ず付ける。
4. **古い版 `10742a3` が V5・V6 の当たった内部DB で起動できるかは確かめていない（U4-MIGRATION、依頼者の判断で未確認）。** 手順 3 で `healthy` にならない・Flyway や Hibernate の検証で起動が止まったら、【承認】`rollback-runbook.md` 4節でデータも戻す（配備の前の複写 `~/.mastersmith-backup/mastersmith-data-202609250120-before-dsl.tgz`。配備の後の記録は失われる）。起動できたかどうかを記録し、U4-MIGRATION の結果として残す。
5. 原因を新しい Intent で直し、`rollback-runbook.md` 7節で新しい版に戻す。

**確かめ方**: `rollback-runbook.md` 2節の手順 5〜8（`healthy`、イメージの ID、古い版のスモークテスト）。

## RB-11 秘密情報の漏えいの疑い

対象: パスワード（平文・ハッシュ値）・アクセストークン・リフレッシュトークン・署名鍵・対象DB の接続情報が、ログ・監査ログ・トレース・エラー応答・リポジトリ・バックアップの外に出た疑い（`project.md` の Forbidden）。**値そのものを画面・記録・報告に出さない。**

**見分け方（件数だけを出す）**

```bash
# トークンの形（JWT の3つの部分）がログにあるか
docker compose logs --no-log-prefix app | grep -cE 'eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.'
# bcrypt のハッシュ値の形があるか
docker compose logs --no-log-prefix app | grep -cE '\$2[aby]\$[0-9]{2}\$'
# 秘密の項目の名前がキーとして出ていないか
docker compose logs --no-log-prefix app | grep -ciE '"(password|signingKey|signing-key|refreshToken|accessToken|jdbcUrl)"'
# リポジトリに .env や鍵のファイルが入っていないか
git ls-files | grep -cE '(^|/)\.env$|\.(pem|key|p12|jks)$'
```

- 本物の値と照らすときは【依頼者】が行う（`.env` を読むため）。例: `docker compose logs --no-log-prefix app | grep -cFf <(grep '^MASTERSMITH_AUTH_SIGNING_KEY=' .env | cut -d= -f2-)`（件数だけ。値が空の行だとすべてに当たるため、先に値があることを確かめる）。
- どれかが 0 でなければ、どの行か（時刻・`logger`・キーの名前）だけを見て、値の部分は見ない・写さない。
- 監査ログ・保存したログ・内部DB の複写には、メールアドレス・接続元IP が入る。これらが `~/.mastersmith-backup/`・`~/.mastersmith-incidents/` の外（リポジトリの中、共有の場所）に出たことも漏えいとして扱う。

**重さ**: 高。

**手順**

1. 範囲を決める: 何が（どの種類の秘密）・どこに（ログ・リポジトリ・バックアップ）・いつから。
2. リポジトリに入った場合: リポジトリは公開のため、履歴を書き換えても見られた前提で取り替える。Gitleaks（コミットの前と CI）が止めなかった理由を振り返りで見る。
3. 取り替えの考え方（【依頼者】`.env` の編集、【承認】0節の 3 の後に作り直し）:

   | 秘密 | 取り替え方 | 影響と注意 |
   |---|---|---|
   | 署名鍵 `MASTERSMITH_AUTH_SIGNING_KEY` | `openssl rand -base64 32` で作り直して `.env` を替え、`docker compose --profile targetdb-postgres up -d app` で作り直す（README の「環境変数」） | 発行済みのアクセストークンは 401 になる。画面はトークンの更新で取り直すため、リフレッシュトークンは使い続けられる（README） |
   | リフレッシュトークン | 漏れたセッションでログアウトすると、そのトークンはサーバー側で無効になる | すべてのリフレッシュトークンをまとめて無効にする仕組みは無い（15節） |
   | 対象DB の読み取りのパスワード | 見本の DB を作り直す（`rollback-runbook.md` 6節）か、DB 側でパスワードを替え、`.env` の `MASTERSMITH_TARGET_DB_PASSWORD` と `MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD` をそろえて作り直す | 見本の DB のパスワードは、ボリュームを作り直さないと `.env` を替えても効かない（README） |
   | 利用者のパスワード（初期管理者を含む） | アプリにパスワードを変える画面・API は無い。`.env` の初期管理者のパスワードは、初めて作るときにだけ使われ、替えても既にいる利用者には効かない | 取り替えの手段が無い（15節）。その間は疑いのある利用者の利用を控える |
   | 内部DB のパスワード `MASTERSMITH_DB_PASSWORD` | 未確認（組み込みの H2 の利用者のパスワードの替え方を確かめていない） | 内部DB はコンテナの外から接続できない（ポートを開けていない） |

4. 漏れの元がコードなら（ログに値を出すなど）、新しい Intent で直し、値が出ないことのテストを足す（`team.md` の Testing Posture の「秘密情報の漏えい」）。

**確かめ方**: 上の件数がすべて 0、取り替えた後に `healthy` とスモークテスト（【依頼者】ログイン）、古い値が使えないこと（署名鍵なら、取り替えの前のアクセストークンが 401）。

## RB-12 そのほかの起動のときのログ

| ログ（確かめ済み） | 意味 | 手当て |
|---|---|---|
| WARN「初期管理者を作成しませんでした」（キー `reason`・`resolution`） | 初期管理者の設定が無い・不正 | 【依頼者】`.env` の `MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL`・`MASTERSMITH_AUTH_INITIAL_ADMIN_PASSWORD` を直して作り直す（既に管理者がいれば不要） |
| WARN「パスワードの照合の時間が目安（100〜500 ミリ秒）の外です。cost の見直しを検討してください」（`bcryptCost`・`elapsedMs`） | 照合が速すぎる・遅すぎる | CPU の上限（`docker inspect mastersmith-app-1 --format '{{.HostConfig.NanoCpus}}'` が `4000000000`）と `colima list` の CPU を見る（README の「コンテナの資源の上限」） |
| ERROR「使い終わったリフレッシュトークンの削除に失敗しました。次の回に再び行います」 | 毎日 3:30 の削除の失敗 | 翌日に再び行われる。続くなら内部DB の不調（RB-01・RB-03）を見る |
| WARN「生成した既定の DSL が U2 の検証を通りませんでした」（`errorKinds`・`errors`） | 見本の DB から作った DSL が検証を通らない（大きさの 10MB 超えなど） | 対象のスキーマを分けるなどの運用で対応（README の「既定の DSL の生成の決まり」の「大きさ」） |

## 13. 警報ごとの初動の表（16 件）

警報は `docker/monitoring/provisioning/alerting/mastersmith.yaml`。手元の監視を起動し、外部エクスポートを有効にしているときだけ評価される（README の「手元の監視（Grafana）」）。「警報の重さ」はファイルのラベル、「障害の重さ」は `incident-plan.md` 1節の目安。

| 警報名 | 警報の重さ | 障害の重さ | 最初に見るもの | 参照する手順 |
|---|---|---|---|---|
| アプリの停止（指標が届かない） | 高 | 高 | `docker compose --profile targetdb-postgres ps`・0節の `docker inspect`（`.env` の外部エクスポートの2行が無いと、アプリが動いていても出る） | RB-01・RB-02 |
| 5xx の割合の増加 | 中 | 低（ログイン・管理の 500 なら高） | ERROR「想定外のエラーが起きました」の `code`、WARN「要求をエラー応答に変換しました」の `status` 503 の `code`（`TARGET_DB_UNAVAILABLE`・`DSL_BUSY` は想定内の 503 でも数に入る） | RB-04・RB-07・RB-10 |
| ERROR のログの増加 | 中 | 低（中身で高に上げる） | 0節の直近の ERROR の `message` | 文言に当たる手順（RB-06・RB-08・RB-12 など） |
| 監査の書き込みの失敗 | 中 | 低 | ERROR「監査イベントの記録に失敗しました」の件数と `exceptionType` | RB-08 |
| ログインの応答の遅れ | 低 | 低 | CPU の上限と照合の時間（RB-12 の照合の WARN）、同時の要求の数 | RB-12・RB-08 |
| トークンの更新の応答の遅れ | 低 | 低 | 接続プールの待ち、ヒープ | RB-08・RB-02 |
| 確認用 API の遅れ | 低 | 低 | 監査の書き込みの遅れの WARN と接続の待ち（警報の説明） | RB-08 |
| コネクションプールの待ち | 低 | 低 | ダッシュボードの「コネクションプールの待ち」、同時の要求の元 | RB-08 |
| JVM のメモリの不足の兆し | 低 | 低（OOMKilled なら高） | `docker stats --no-stream mastersmith-app-1`、直前の大きな DSL の操作 | RB-02・RB-03 |
| 管理画面への拒否の増加 | 中 | 低 | WARN「権限が足りないため要求を拒否しました」の件数、監査の `ACCESS_DENIED` | RB-09 |
| ロックの多発 | 中 | 低 | INFO「アカウントをロックしました」の `userId` | RB-09 |
| 回り込みの試み | 中 | 低 | WARN「正規化されていないパスの要求を拒否しました」の件数と時刻 | RB-09 |
| 送り元の不一致の多発 | 低 | 低 | WARN「要求の送り元が自分の配信元と一致しないため拒否しました」、画面を開いたホスト名 | RB-09 |
| 監査の書き込みの遅れ | 低 | 低 | WARN「監査イベントの記録に時間がかかりました」の `elapsedMs`、内部DB の大きさ | RB-08・RB-03 |
| トークンの削除の失敗 | 低 | 低 | ERROR「使い終わったリフレッシュトークンの削除に失敗しました。次の回に再び行います」 | RB-12 |
| 照合の時間の範囲外 | 低 | 低 | WARN「パスワードの照合の時間が目安（100〜500 ミリ秒）の外です。cost の見直しを検討してください」の `elapsedMs` | RB-12 |

- DSL の操作の失敗・内部DB のファイルの大きさ・見本の対象DB の停止には警報が無い（`alarms.md` 1節・2節）。DSL の操作はダッシュボードの「DSL の操作」の行で、内部DB の大きさは RB-03 のコマンドで見る。
- Loki では、ログのキーと値（`dsl.operation` など）で絞り込めない（`log-queries.md` 2節。後の Intent で直す）。キーで絞るときは `docker compose logs` の JSON を見る。

## 14. 定期の確認（アプリを使う前）

| 確かめ | コマンド | 通る条件 |
|---|---|---|
| 状態 | `docker compose --profile targetdb-postgres ps` | `app` が `healthy`、`targetdb-postgres` が `Up` |
| ERROR | `docker compose logs --no-log-prefix --since 24h app` を `grep -c '"level":"ERROR"'` で数える | 0（0 でなければ中身を見る） |
| 内部DB の大きさ（大きな DSL の操作を重ねたとき） | RB-03 のコマンド | 300MB 未満 |

## 15. 確かめられなかったこと

| 項目 | 内容 |
|---|---|
| ログの文言（推測） | Flyway の移行・検証の失敗と、Spring Boot の起動の失敗の文言（RB-01）。部品の文言で、このリポジトリのソースには無い |
| ログの文言（推測） | 接続プールの待ちの時間切れの例外の種類（RB-08。Hikari の `SQLTransientConnectionException` と見られる） |
| 原因の推測 | 送り元の不一致が `localhost` 以外のホスト名で開いたことで起きること（RB-09） |
| 手順が無いもの | 欠けた監査の記録を補う手順（README は「後から手で記録を補えるように」ERROR に項目を載せるとするが、補い方は決まっていない） |
| 手段が無いもの | リフレッシュトークンをまとめて無効にする仕組み、利用者のパスワードを変える画面・API（RB-11） |
| 未確認の手順 | 内部DB（組み込みの H2）のパスワードの替え方（RB-11）、古い版 `10742a3` が V5・V6 の内部DB で起動すること（U4-MIGRATION、RB-10） |

## Sources

- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/incident-response-questions.md`（Q1〜Q3、確認済みの要約）、同じ段の `incident-plan.md`・`escalation-matrix.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/deployment-pipeline/rollback-runbook.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/deployment-execution/deployment-log.md`・`health-check-report.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/environment-provisioning/environment-inventory.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/observability-setup/alarms.md`・`log-queries.md`・`dashboards.md`・`slo-config.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/u4-dsl-management/nfr-design/reliability-design.md`・`security-design.md`、`construction/u4-dsl-management/infrastructure-design/infrastructure-specification.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/build-and-test/build-and-test-summary.md`（U4-STORAGE-RUN・U4-STORAGE-RESTART・U4-MIGRATION）
- `README.md`（「コンテナでの起動と確認」「戻し方」「内部DBのバックアップと戻し方」「コンテナの資源の上限」「環境変数」「対象DB」「手元で試す対象DB（compose の profile）」「既定の DSL の生成の決まり」「DSL の管理の API（U4）」「DSL の管理画面（U5）」「手元の監視（Grafana）」「API のアクセス制御（U3）」「監査ログ（U4）」）
- `compose.yaml`、`docker/monitoring/provisioning/alerting/mastersmith.yaml`、`backend/src/main/resources/logback-spring.xml`
- ログの文言を確かめたソース（`backend/src/main/java/cherry/mastersmith/` の下）: `dslmanage/service/DslStartupLoader.java`・`DslLifecycle.java`・`DslOperationMetrics.java`・`DslReconciler.java`、`dslmanage/generate/TargetSchemaDslGenerator.java`、`dslmanage/web/DslHeavyOperationGate.java`、`dslmanage/domain/DslProblemTypes.java`、`targetdb/config/TargetDataSourceConfig.java`、`targetdb/service/JdbcTargetSchemaReader.java`、`targetdb/repository/SchemaRows.java`、`targetdb/domain/UnavailableReason.java`、`audit/service/AuditEventListener.java`、`auth/service/LoginService.java`・`RefreshTokenCleanupJob.java`・`SigningKeyProvider.java`・`LogoutService.java`、`auth/web/OriginVerifier.java`・`AuthController.java`、`access/web/AccessRequestRejectedHandler.java`・`AdminAccessDeniedHandler.java`、`user/service/InitialAdminInitializer.java`・`DummyPasswordHash.java`、`common/error/web/GlobalExceptionHandler.java`・`ErrorPathController.java`、`common/health/TimeBoundedDbHealthIndicator.java`
- 読み取りだけで調べた今の環境（2026-09-25）: `docker ps`・`docker inspect mastersmith-app-1 --format ...`・`docker volume ls`

## Assumptions & Open Questions

- [assumption] 欠けた監査の記録を補う手順は決まっていない。補うか・どう補うか（追記だけの表への手での追記を認めるか）は、依頼者が決めるか、監査の共通の仕組みを扱う後続の Intent で決める。それまでは障害の記録（リポジトリの外）に時刻・種類・件数を残すだけとした。
- [assumption] リフレッシュトークンをまとめて無効にする手段と、利用者のパスワードを変える手段がアプリに無い。秘密情報の漏えいのうち、これらに当たるものは取り替えられない。後続の Intent の課題として扱う前提で書いた。
- [assumption] 内部DB（H2）のパスワードの替え方と、古い版が V5・V6 の内部DB で起動すること（U4-MIGRATION）は確かめていない。
- [assumption] 15節の推測の文言（Flyway・Spring Boot の起動の失敗、接続の待ちの時間切れの例外）は、実際に起きたときに確かめて、この手順書を直す。
