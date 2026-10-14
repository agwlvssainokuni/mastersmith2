# 障害ごとの手順書（runbooks）

前の3つの手順書を正とし、この Intent で増えた障害（RB-28 から）と、古くなった箇所の直しだけを書きます（Q1: A）。この Intent で増えた機能は次のとおりです。
- U1 API の分類・U2 DSL の版 2
- U3 グループ・U4 ロールと権限・割り当て・作業ロール・権限の受け渡し
- U5 業務のメニューと置き場・U6 管理の画面・U7 骨組みの画面

障害のときは、まず下の「索引」で RB の置き場と、今も使えるかを引きます。

- **元の手順書**: `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/runbooks.md`（0節・RB-01〜RB-12・13節〜15節）
- **2つ目の手順書**: `aidlc/spaces/default/intents/260925-user-management/operation/incident-response/runbooks.md`（RB-13〜RB-20・21節〜23節）
- **前の手順書**: `aidlc/spaces/default/intents/260930-user-admin/operation/incident-response/runbooks.md`（索引・1節の直し・RB-21〜RB-27・2節〜4節）
- どの障害でも最初に行うことは、元の手順書の 0節のままです（状態・ログを見る、作り直す前にログを保存する、記録を始める）。
- 記号: 【承認】は、AI が行うときに依頼者の承認が要る操作です。【依頼者】は依頼者が行う操作です（`.env` の編集、秘密情報が要るログイン、画面の操作）。印の無いものは読み取りだけです。
- 配備したアプリの compose のコマンドには、今までどおり `--profile targetdb-postgres` を付けます（`operation/deployment-pipeline/rollback-runbook.md`）。
- **値を出さない。** メールアドレス・氏名・利用者 ID・送り元の IP・パスワード・トークンと、ロール・グループ・スキーマ・テーブルの名前は、コマンドの出力・記録・報告に出しません。件数と、ロガー・キー・code の名前だけで見ます（`aidlc/spaces/default/memory/project.md` の Corrections・Forbidden）。
- ログの文言・code・監査の種類は、`backend/src/main/java/` のソース、README、この Intent の `operation/observability-setup/` の確かめ（2026-10-15）で確かめたものです。確かめられなかったものは「推測」と書きます。
- 警報の確かめの区別（`operation/observability-setup/alarms.md` 2節）:

  | 区別 | 警報 |
  |---|---|
  | 鳴ることを確かめた | `ms-forbidden`・`ms-app-absent`（止めてから 10 分 20 秒） |
  | performance-validation で確かめる | `ms-pool-pending`・`ms-error-logs`・`ms-audit-fail` |
  | Unverified | `ms-5xx-ratio`・`ms-audit-slow` |

  **配備したアプリは外部エクスポートが無効のため、今はどの警報も配備したアプリでは鳴りません**（手元の監視は使い捨ての環境の確かめにだけ使っている）。

## 索引（RB-01〜RB-35）

「今」の列: **使う**＝そのまま使う。**直しを合わせて読む**＝1節の直しと一緒に読む。**使わない**＝置き換え先を読む。

| RB | 症状 | 重さ | 置き場 | 今 |
|---|---|---|---|---|
| RB-01 | アプリが止まる・healthy にならない | 高 | 元の手順書 | 直しを合わせて読む（JVM が止まって起き直さないときは RB-30） |
| RB-02 | メモリの上限（2g）に近い・OOMKilled | OOMKilled なら高、近いだけなら低 | 元の手順書 | 直しを合わせて読む（JVM がヒープの不足で止まったとき（終了コード 3）は RB-30） |
| RB-03 | 内部DB のファイルの伸び | 低（ディスクが尽きたら高） | 元の手順書 | 使う |
| RB-04 | 見本の対象DB が止まった・応答しない | 低 | 元の手順書 | 使う |
| RB-05 | 対象DB の設定の誤り | 低 | 元の手順書 | 使う |
| RB-06 | 起動のときに適用中の DSL を読めない | 低（依頼者の判断で高） | 元の手順書 | 直しを合わせて読む（版 1 の DSL・戻した版は RB-35） |
| RB-07 | `DSL_BUSY` が続く | 低 | 元の手順書 | 使う |
| RB-08 | 監査の書き込みの失敗・接続プールの待ち | 低（続く・多い、内部DB の不調なら高） | 元の手順書 | 直しを合わせて読む（前の手順書の RB-25、この手順書の 1節） |
| RB-09 | ログインの失敗の多発・ロック・回り込みの試み・送り元の不一致 | 低（知らないログインの成功などは高） | 元の手順書 | 直しを合わせて読む（前の手順書の 1節） |
| RB-10 | 配備の後の不具合（戻す） | 高 | 元の手順書 | 使わない → RB-35（`rollback-runbook.md`） |
| RB-11 | 秘密情報の漏えいの疑い | 高 | 元の手順書 | 直しを合わせて読む（前の2つの手順書の直しと、この手順書の 1節） |
| RB-12 | そのほかの起動のときのログ | 低 | 元の手順書 | 使う |
| RB-13 | メールの受け手が止まっている・招待のメールが届かない | 低 | 2つ目の手順書 | 使う |
| RB-14 | 招待を使えない設定（503 `INVITATION_NOT_CONFIGURED`） | 低 | 2つ目の手順書 | 使う |
| RB-15 | 起動が止まる設定の誤り（招待の長さ・メールのテンプレート） | 高 | 2つ目の手順書 | 使う |
| RB-16 | 招待の定期の削除の失敗 | 低 | 2つ目の手順書 | 使う |
| RB-17 | 総当たりの疑い（登録の完了・今のパスワード） | 低（知らない登録があれば高） | 2つ目の手順書 | 直しを合わせて読む（前の手順書の 1節） |
| RB-18 | 招待のトークン・URL が漏れた疑い | 高 | 2つ目の手順書 | 直しを合わせて読む（前の手順書の 1節） |
| RB-19 | `127.0.0.1` で開いたときの拒否・見た目の設定の不正 | 低（最初の描画が止まるなら高） | 2つ目の手順書 | 使う |
| RB-20 | Intent 260925-user-management の版を戻すときの注意 | 高 | 2つ目の手順書 | 使わない → RB-35 |
| RB-21 | 管理者の誤った操作（印・停止・失敗回数） | 低 | 前の手順書 | 使う（管理の可否は今も管理者の印だけで決まる。ロールでは変わらない） |
| RB-22 | 使える管理者がいなくなった | 高 | 前の手順書 | **使わない** → README の「使える管理者がいなくなったとき（初期管理者の救済）」（1節） |
| RB-23 | 停止中の利用者が使えない・停止を解きたい | 低 | 前の手順書 | 使う |
| RB-24 | 行の排他の時間切れ（409 `USER_ADMIN_BUSY`、既存の経路の 500） | 低（500 が続くなら高） | 前の手順書 | 使う（ロール・グループは RB-28） |
| RB-25 | 5つの操作の確定の後の監査の書き込みの失敗 | 低（続く・多いなら高） | 前の手順書 | 直しを合わせて読む（1節） |
| RB-26 | Intent 260930-user-admin の版を戻すときの注意 | 高 | 前の手順書 | 使わない → RB-35 |
| RB-27 | 行の「操作」のメニューのはみ出し | 低 | 前の手順書 | **使わない**（Intent 261003-user-admin-followup の FR2 で直った） |
| RB-28 | ロール・グループの取り合い（409 `ROLE_BUSY`・`GROUP_BUSY`） | 低 | この手順書 | 使う |
| RB-29 | 権限の受け渡しがうまくいかない（入口の取り合い・大きさ・`ROLE_TRANSFER_STALE`・`ROLE_NO_CHANGE`） | 低 | この手順書 | 使う |
| RB-30 | ヒープが尽きて JVM が止まり、起き直さない | 高 | この手順書（実測で確かめた） | 使う |
| RB-31 | ロール・権限の設定を YAML で保存する・設定だけを戻す | 低（戻すのは依頼者の判断） | この手順書 | 使う |
| RB-32 | 業務のメニュー・テーブルが急に見えなくなった | 低 | この手順書 | 使う |
| RB-33 | 置き場の 403 が多い（`ms-forbidden`） | 低 | この手順書 | 使う |
| RB-34 | 画面が固まる・表示が崩れる・押しても反応しない | 低（業務が止まるなら高） | この手順書 | 使う |
| RB-35 | この Intent の版を戻す・DSL の版 | 高 | この手順書（中身は `rollback-runbook.md`） | 使う |

| 節 | 中身 | 置き場 |
|---|---|---|
| 警報ごとの初動の表 | 16 件の警報ごとの最初に見るもの | 元の手順書の 13節、2つ目の手順書の 21節、前の手順書の 2節、この手順書の 2節 |
| 定期の確認 | アプリを使う前の確かめ | 元の手順書の 14節、2つ目の手順書の 22節、前の手順書の 3節、この手順書の 3節 |
| 確かめられなかったこと | 推測と未確認の一覧 | 元の手順書の 15節、2つ目の手順書の 23節、前の手順書の 4節、この手順書の 4節 |

## 1. 前の3つの手順書から直す箇所

| 箇所 | 前の記述 | 今 |
|---|---|---|
| 前の手順書の RB-22（使える管理者がいなくなった） | `.env` の初期管理者を**新しい**メールアドレスに替えて作り直し、新しい管理者で元の利用者を戻す | **使いません**。Intent 261004-safety-carryover の救済に置き換わりました。アプリを起動し直すと、`.env` の初期管理者の設定の利用者が、次のどれかに当たれば救われます: (a) 停止中、(b) 印なし、(c) パスワードが設定と違う。救うと、停止を解き・印を付け・失敗回数を戻し・パスワードを置き換え・リフレッシュトークンを無効にします。救ったときは WARN「初期管理者を救済しました」（キー `maskedEmail`・`conditions`）と、監査の `INITIAL_ADMIN_RESCUED` が残ります。手順は README の「使える管理者がいなくなったとき（初期管理者の救済）」です。【承認】`docker compose --profile targetdb-postgres restart app` の後、`grep -c '初期管理者を救済しました'` で件数だけを見ます。**初期管理者をわざと止めた・印を外したとき**は、先に【依頼者】`.env` の設定を替えないと、次の再起動で戻ります（README） |
| 前の手順書の 1節の RB-11 の行（自分自身・最後の有効な管理者は止められないときは RB-22） | RB-22 で別の管理者を作ってから止める | 初期管理者の資格情報の漏えいの疑いでは、【依頼者】`.env` の初期管理者のパスワードを新しい値に替えて、【承認】起動し直します。救済の (c) でパスワードが置き換わり、その利用者のリフレッシュトークンもすべて無効になります（README）。初期管理者でない最後の管理者のときは、まず初期管理者を救ってから、その管理者を画面で止めます |
| 前の手順書の RB-27（行の「操作」のメニューのはみ出し） | 既知の不具合 | 直りました（Intent 261003-user-admin-followup の FR2、`placement="bottom-end"` と E2E 120 の確かめ） |
| 元の手順書の RB-10、2つ目の手順書の RB-20、前の手順書の RB-26（版を戻す） | 戻し先 `mastersmith:pre-dsl`・`pre-user-management`・`pre-user-admin` | 今の戻し先は `mastersmith:pre-role-menu`（`fefeafbd299b…`）です。S8 の直しだけを戻すときは `mastersmith:role-menu-before-s8fix`（`e41a5b02fd0b`）です。手順は `rollback-runbook.md`（RB-35） |
| 前の手順書の incident-plan の「今ある最新の複写」 | `…-202610032204-after-user-admin.tgz` | 配備の前: `~/.mastersmith-backup/mastersmith-data-202610140820-pre-role-menu.tgz`。配備の後（最終）: `…-202610142218-after-role-menu-final.tgz`（`operation/deployment-execution/health-check-report.md` 3節） |
| 元の手順書の RB-01・RB-02（アプリが止まる・OOMKilled） | コンテナのメモリの上限での止まり（OOMKilled true・137）を扱う | この Intent の版は、ヒープが尽きると JVM が自分で止まります（終了コード 3・OOMKilled false。`restart: "no"` のため起き直さない）。見分けと手順は RB-30 |
| 前の手順書の RB-08・RB-25（確定の後の監査の2本目の接続） | 利用者の管理の5つの操作も2本目を使う | ロール・グループの変える操作と、権限の YAML の適用（数秒、2本）も、確定の後の監査で2本目の接続を使います（group・role の `scalability-design.md` 2節）。欠けたときの見分け方は RB-25 と同じ（ERROR「監査イベントの記録に失敗しました」、キー `auditEventType` で種類を見る） |
| 前の手順書の 2節の `ms-forbidden` の行 | 利用者の管理の API の 403 も数える | 置き場の 403（`/api/me/table-access`、業務の結果、監査に残らない）も数えます。鳴ったときは RB-33 |
| 前の手順書の 2節の「p95 の警報3件」 | `ms-login-p95` が値を返すことだけ確かめた | 変わりません。この Intent の新しい API の p95 には警報がなく、ダッシュボードの区画「ロール・グループ・メニュー」のパネル 37（目安の線 1000 ms）で見ます（`operation/observability-setup/dashboards.md`） |

## RB-28 ロール・グループの取り合い（409 `ROLE_BUSY`・`GROUP_BUSY`）

**当たる場面**: ロール・グループを変える操作が重なりました（同じロール・グループへの操作、権限の YAML の適用の最中のロールへの操作、同じ利用者の初めての作業ロールの切り替えの重なり）。行の排他の待ちが 3 秒、一意の鍵・主キーの待ちが約 2 秒を過ぎると、巻き戻して 409 で断ります。**状態は変わらず、監査に残りません**（README の「グループの管理の API」「ロールと権限の API」）。

**重さ**: 低。

**手順**

1. 【依頼者】少し待って、同じ操作をやり直す（画面の Toast の案内のとおり）。
2. 続くときは件数と種類を見る（値は出ない）。
   ```bash
   docker compose logs --no-log-prefix --since 1h app | grep -c '"code":"ROLE_BUSY"'
   docker compose logs --no-log-prefix --since 1h app | grep -c '"code":"GROUP_BUSY"'
   docker compose logs --no-log-prefix --since 1h app | grep '行の排他を取れませんでした' | grep -o '"lockKind":"[A-Z_]*"' | sort | uniq -c
   ```
   - `lockKind` の値（`ROLE_ROW`・`ROLE_NAME_KEY`・`ROLE_ASSIGNMENT_KEY`・`WORK_ROLE_SELECTION_KEY`・`GROUP_ROW`・`GROUP_NAME_KEY`・`GROUP_MEMBER_KEY`）で、どの排他かが分かります。
   - 手元の監視が動いていれば、`log-queries.md` の L5（code ごと）と L3（`lockKind` ごと）。同じ `trace_id` で2行を結べます。
3. 権限の YAML の適用（数秒）の最中なら、適用が終わるのを待つ（RB-29）。
4. それでも続く（同じ排他が外れない）ときは、【承認】`docker compose --profile targetdb-postgres restart app` で起動し直す。排他は外れ、進行中の操作は巻き戻ります（止まっている間の要求は失敗する）。

**確かめ方**: 同じ操作が 204・201 で通る。【依頼者】画面に変更が見える。

## RB-29 権限の受け渡しがうまくいかない

**当たる場面と見分け**（README の「権限の受け渡し（B6）」、Observability Setup で実機の code を確かめた）

| 応答 | 意味 | 手当て |
|---|---|---|
| 409 `ROLE_BUSY`（確かめ・適用） | 確かめと適用は同時に1つずつ。ほかの確かめ・適用が動いている。WARN「権限の受け渡しの入口を取れませんでした」（`lockKind=ROLE_TRANSFER_SLOT`）が1回出る | 待ってやり直す。続くときは `grep -c '権限の受け渡しの入口を取れませんでした'`。本文をゆっくり送る管理者がいると入口を持ち続ける（README） |
| 413 | 本文が 5 MiB を超えた。書き出しに `X-Role-Transfer-Exceeds-Import-Limit: true` が付いたファイルはそのままでは読めない | 【依頼者】ロールのキーごとにファイルを分け、それぞれ確かめと適用を行う |
| 400 `ROLE_TRANSFER_INVALID` | 書式・値・名前の規則・上限（入れ子 10・別名・節 500,000）の誤り。`errors` に行と列 | 【依頼者】ファイルを直す。応答に部品の例外の文は出ない |
| 409 `ROLE_TRANSFER_STALE` | 確かめた後にロールの設定が変わった | 【依頼者】確かめからやり直す |
| 409 `ROLE_NO_CHANGE` | 変える点が無い（書き出したものをそのまま適用した、など）。監査に `ROLE_TRANSFER_APPLIED` の `FAILURE`/`NO_CHANGE` が1行残る | 何もしなくてよい（設定は今のまま） |
| 409 `DSL_NOT_APPLIED` | 適用済みの DSL が無い | DSL を適用してから（RB-35 の戻した版など） |
| 応答が来ない・接続が切れる | 確かめ・適用の最中に JVM が止まった見込み | RB-30 |

**重さ**: 低（RB-30 に当たれば高）。

**確かめ方**: 適用が 200（`created`・`replaced`）。監査に `ROLE_TRANSFER_APPLIED` の `SUCCESS` が1行。

## RB-30 ヒープが尽きて JVM が止まり、起き直さない（実測で確かめた、Q3: A）

**当たる場面**: 画面が急に何も返さなくなり、`docker compose ps -a` で `app` が `Exited`。この Intent の版は、ヒープが尽きると `-XX:+ExitOnOutOfMemoryError` で JVM が止まります。compose の `restart: "no"` のため、起き直しません（Deployment Pipeline の Q6: A。自動で起き直す設定にはしない）。重い操作の例は次のとおりです。
- 権限の YAML の確かめ・適用（上限ちょうどの悪い形）
- DSL の 10 MiB の投入・プレビューの表示
- 大きな書き出し
- 入口の外の重い操作どうしの重なり（README の注意）

**重さ**: 高（アプリが使えない）。

**見つけ方**: 配備したアプリは外部エクスポートが無効のため、今は警報が鳴りません。気づくのは画面です。手元の監視を有効にしているときは `ms-app-absent` が鳴ります（止めてから 10 分 20 秒、`alarms.md` 2.1）。

**手順**（`rollback-runbook.md` 7節を、下の実測で確かめた）

1. 止まったことと理由を確かめる（値は出ない）。
   ```bash
   docker compose --profile targetdb-postgres ps -a app            # Exited (3) であること
   docker inspect mastersmith-app-1 --format '{{.State.ExitCode}} {{.State.OOMKilled}} {{.State.FinishedAt}} {{.RestartCount}}'
   docker compose logs --no-log-prefix app | grep -c 'Terminating due to java.lang.OutOfMemoryError'
   docker compose logs --no-log-prefix app | grep -o 'java\.lang\.OutOfMemoryError: [A-Za-z ]*' | sort | uniq -c
   ```
   | 見えたもの | 意味 |
   |---|---|
   | 終了コード 3・OOMKilled false・「Terminating due to java.lang.OutOfMemoryError: Java heap space」が1行 | JVM がヒープの不足で止まった（この RB） |
   | 終了コード 137・OOMKilled true | コンテナのメモリの上限（2g）で消された。JVM の外の使い方を疑う（元の手順書の RB-02） |
   | どちらでもない | ほかの原因（元の手順書の RB-01） |
   - 止まる直前の「Terminating …」の行は、**JSON ではない1行**で、logback を通りません。そのため、外部エクスポートを有効にしていても Loki には届きません。コンテナの標準出力（`docker compose logs`）でだけ見えます。その前の JSON の行には ERROR は出ていませんでした（実測）。
2. 【依頼者】直前に何をしていたか（権限の YAML の確かめ・適用、DSL の投入、書き出しなど）を確かめ、時刻と操作を記録する。確定していない操作は巻き戻り、監査に残らない。
3. **起動し直す前に、ログを残す**。`docker compose start` なら同じコンテナで、ログは消えません（実測で、起動し直した後も「Terminating …」の行が残っていた）。ただし `docker compose up -d` で作り直すと消えるため、作り直すときは先に `docker compose logs --no-log-prefix app > <リポジトリの外の置き場>` で保存する。
4. 【承認】同じコンテナで起動し直す。
   ```bash
   docker compose --profile targetdb-postgres start app
   docker compose --profile targetdb-postgres ps app                # healthy を待つ（実測で 10 秒）
   ```
   【依頼者】ログインでき、ホームが開けることを確かめる。
5. 同じ入力で繰り返し止まるときは、入力の大きさ・形の問題として、その操作を控えます。ファイルを分ける（RB-29 の 413 の手当てと同じ）、DSL の投入と重ねない、などです。戻すかどうかは依頼者に諮ります。前の版は止まらずに動き続けうるだけで、同じ入力のヒープの不足は直らない見込みです（`rollback-runbook.md` 7節）。
6. 記録は feedback-optimization に引き継ぐ（何の操作で、どの大きさで止まったか）。

**実測の結果（2026-10-15 02:48〜02:51 JST、使い捨ての環境）**

- 環境: `docker/perf/compose.yaml`、project `mastersmith-ir`。
  - イメージは配備と同じ `mastersmith:local`（`sha256:3247c5bdab79…`）。
  - `restart: no`・メモリの上限 1g・CPU 4。
  - 仮の署名鍵・仮の初期管理者。
  - PID 1 のコマンド行に `-XX:MaxRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError … -Xmx192m`（尽きさせるために、一時の環境ファイルの `MASTERSMITH_JAVA_OPTIONS` で最大ヒープを 192 MB にした。起動の後のヒープの使用は約 109 MB）。
- 尽きさせ方（コードは変えず、既存の API の範囲）:
  1. 版 2 の小さな DSL（テーブル1つ）を適用した。
  2. 権限の YAML（版 1、2 ロール × 100 テーブル × 900 カラム、4,872,362 バイト。上限 5 MiB の内）を、確かめ（`POST /api/admin/role-transfer/check`）に1回送った。
- 結果:

  | 確かめ | 結果 |
  |---|---|
  | 止まり方 | 送ってから 6.4 秒で JVM が止まり、要求は応答なしで切れた（`curl` の終了コード 52 = 空の応答）。`Exited (3)`・OOMKilled false・RestartCount 0 |
  | 止まる直前の行 | コンテナの標準出力の最後に「Terminating due to java.lang.OutOfMemoryError: Java heap space」が1行（JSON でない）。`grep -c 'OutOfMemoryError'` は 1。その前の JSON の行に ERROR は 0 件 |
  | 起き直さない | 止まってから 2 分の後も `Exited (3)`・RestartCount 0 |
  | 手での起動し直し | `docker compose -p mastersmith-ir … start app` から 10 秒で healthy、`/actuator/health` 200。ログイン 200、管理の API 200、業務のメニュー 200、適用済みの DSL は残っていた。起動のログに ERROR 0 件。確かめは何も書かないため、ロールは 0 件のまま |
  | ログの残り方 | 同じコンテナを起動し直した後も、「Terminating …」の行は `docker logs` に残っていた |
- 配備の上限（2g、最大ヒープ約 1 GiB）では、Build and Test の測り（BT-Q1c）で 5 MiB の悪い形の確かめ・適用でも止まっていません。今回の 192 MB は、手順を確かめるために小さくした値で、配備で止まる大きさを示すものではありません。
- 確かめの後に、使い捨ての環境（コンテナ・ボリューム・網）と一時の置き場（仮の鍵・資格情報・YAML・ログ）を消しました（3節の後の「確かめの片付け」）。

## RB-31 ロール・権限の設定を YAML で保存する・設定だけを戻す（Q2: A）

**当たる場面**: ロール・権限の設定を誤って変えた・消した、または内部DB を複写より前に戻した後に、ロールと権限の設定だけを取り戻したい。

**重さ**: 低（戻すのは依頼者の判断）。

**取り出せる範囲**: 書き出しの YAML は、ロールの名前と権限の設定だけを持ちます。**割り当て（利用者・グループ）・グループ・作業ロールの保存は含みません**。これらは内部DB の複写（元の手順書の RB-10、README の「内部DBのバックアップと戻し方」）で守ります。

**保存（定期の確認、3節）**

1. 【依頼者】ロール・権限の設定を大きく変えた後に、権限の受け渡しの画面の「書き出す」で YAML を保存する。
2. 【依頼者】保存したファイルを `~/.mastersmith-backup/`（権限 700）に、日時の入った名前（例 `role-permissions-<日時>.yaml`、権限 600）で置く。リポジトリには置かない（ロールの名前が入るため）。
3. `X-Role-Transfer-Exceeds-Import-Limit: true`（画面の案内）が出たときは、そのままでは読み込めません。戻すときにロールのキーごとに分けることを、ファイルの名前か記録に書いておきます。

**戻す**

1. 【依頼者】権限の受け渡しの画面で保存した YAML を選んで「確かめる」。ロールごとの「作る・置き換える」と変わる点を見る。
2. 【依頼者】「適用する」。適用は1つのトランザクションで、途中で失敗すると何も変わりません。監査に `ROLE_TRANSFER_APPLIED` が1行残ります（確かめは残らない）。
3. 割り当てが外れていたときは、【依頼者】ロールの画面の「割り当て」で付け直す（監査に `ROLE_ASSIGNED`）。
4. 失敗したときは RB-29 の表で見分ける。

**確かめ方**: 【依頼者】ロールの画面で権限の木が意図どおり。自分の権限を見る画面で、作業ロールの権限が見える。

## RB-32 業務のメニュー・テーブルが急に見えなくなった

**当たる場面**: 利用者から「メニューが空になった」「いつものテーブルが開けない（権限が無い）」と知らされた。

**重さ**: 低。

**見分け**（README の「作業ロールの決め方」「業務のメニューの API」）

| 原因 | 見え方 | 確かめ方 |
|---|---|---|
| ロールが消された | そのロールを指す作業ロールの保存も消え、残るロールの「最初のロール」（ID の最も小さい）に読み替わる。ロールが無ければ作業ロールなし | 【依頼者】ロールの一覧。監査の `ROLE_DELETED` の件数 |
| 割り当てが外れた（直接・グループ経由、メンバーから外れた） | 外れたロールは読みで最初のロールに読み替わる（保存は残り、また割り当てられれば戻る） | 【依頼者】利用者の管理の画面の「ロールを見る」（出どころ）。監査の `ROLE_UNASSIGNED`・`GROUP_MEMBER_REMOVED` の件数 |
| 権限の設定が変わった | 項目が `NONE` になったテーブルはメニューから消え、置き場は 403 | 監査の `ROLE_PERMISSION_CHANGED`・`ROLE_TRANSFER_APPLIED` の件数 |
| DSL を適用し直した・DSL が無い | メニューの空の理由が `NOT_CONFIGURED`。DSL に無い対象は `NONE` | 【依頼者】DSL の画面。戻した版なら RB-35 |
| 作業ロールを替えた | 作業ロールの権限だけで決まる（ほかのロールの設定は合算しない） | 【依頼者】トップバーの「作業ロール」 |

1. 【依頼者】本人に、トップバーの作業ロールとホームの案内（「業務のメニューが設定されていない」「作業ロールが無い」「表示できる業務のメニューが無い」）を確かめてもらう。
2. 【依頼者】上の表の順に画面で確かめる。監査の件数を見るときは、README の「監査ログの確かめ方」（アプリを止めて複写を読み取りで開く）で、`event_type` ごとの件数と時刻だけを数える（【承認】アプリを止める）。
3. 誤った操作なら、【依頼者】画面で割り当て・権限を付け直す（RB-31 の YAML があれば設定だけを戻せる）。

**確かめ方**: 本人の画面で、メニューとテーブルが見える。置き場が 200（画面が開ける）。

## RB-33 置き場の 403 が多い（`ms-forbidden`）

**当たる場面**: `ms-forbidden`（直近 1 時間の 403 が 20 件を超える）が鳴った。または 403 の件数のパネルが増えた。

**重さ**: 低（入口の 403 に知らない試しが多いなら、元の手順書の RB-09・RB-11 を考える）。

1. どの 403 かを分ける（`operation/observability-setup/log-queries.md` 3節）。手元の監視が動いていれば `dashboards.md` の R2（403 の `uri` ごとの内訳）と、`log-queries.md` の L2（入口）・L9（置き場）。動いていなければ件数だけを見る。
   ```bash
   docker compose logs --no-log-prefix --since 1h app | grep -c '権限が足りないため要求を拒否しました'          # 入口の 403
   docker compose logs --no-log-prefix --since 1h app | grep '要求をエラー応答に変換しました' | grep -c '"code":"ACCESS_DENIED"'   # 置き場の 403 と業務の層の 403
   ```
2. 置き場の 403 が多いとき: 業務の結果（権限の無いテーブルを開いた・開いたままの画面で作業ロールを替えた・古いブックマーク）で、障害とは限りません。置き場の 403 は監査に残らず、テーブルの名前はログにもトレースにも出ません。
   - 【依頼者】使っている人に、どの画面から開いたかを聞く。
   - 権限の設定を見直すかを決める（RB-32）。
3. 入口の 403 が多いとき: 前の手順書の 1節の 13節の行（監査の `ACCESS_DENIED` の `request_path` で道を見る）。
4. `ms-forbidden` の式から置き場の 403 を分けるかは、配備先が決まり利用者が増えたときの申し送りです（`alarms.md` 2.2）。式は変えません。

## RB-34 画面が固まる・表示が崩れる・押しても反応しない（Q4: A）

**当たる場面**: 画面だけの不具合です。サーバーのログ・指標・監査に出ないことが多く、警報でも定期の確認でも見つかりません。例: Deployment Execution の S8（自分の権限の木でテーブルの開閉を押すと固まった。a16cd68 で直した）。

**重さ**: 低（業務が止まるなら高）。

1. **先に、依頼者がした操作を聞く**。どの画面で、どのボタン・どの開閉を押したか、画面の文言とその場所、作業ロール、何回目で起きたか（`project.md` の学び。S8 では操作を聞かずに試して、見立てを誤った）。
2. 【依頼者】タブを読み込み直す。入れ替えの後・戻した後に開いていたタブは、画面の部品の読み込みに失敗することがある（README の「入れ替えの後」）。直れば、それを記録して閉じる。
3. 【依頼者】直らなければ、ブラウザの開発者ツールを開いたまま同じ操作をする。次の2つを見る。**値（本文・名前・トークン）は記録しない**。
   - Network: 失敗した要求の道の型・状態コード・大きさ・時間
   - Console: 誤りの種類
4. サーバー側の同じ時刻を件数で見る（`docker compose logs --since 30m app` の WARN・ERROR の件数とロガー・code の名前）。手元の監視が動いていれば、その要求の `trace_id` のトレース（`tracing-config.md` 2節）。
5. 【承認】使い捨ての環境（`docker/perf/compose.yaml`、仮の鍵・仮の利用者、別の project 名と番号）で、**依頼者と同じ操作**を試す。木の画面は、すべての段の開閉を押す。配備したアプリでは試さない（操作が監査に残るため）。
6. 再現したら、直すかどうか・次の Intent へ回すかを依頼者に諮る（不具合を直すときは、再現するテストを同じコミットに含める。`project.md` の Mandated）。

**確かめ方**: 同じ操作で固まらない。Network・Console に誤りが無い。

## RB-35 この Intent の版を戻す・DSL の版

中身は `operation/deployment-pipeline/rollback-runbook.md` です（戻しの練習 D1〜D6 で確かめ済み）。ここには要点だけを書きます。

- 戻し先: `mastersmith:pre-role-menu`（権限・メニューの機能を戻すとき）。`mastersmith:role-menu-before-s8fix`（S8 の直しだけを戻すとき）。
- **戻す前に、版 2 のプレビューがあれば【依頼者】DSL の画面で破棄する**（前の版は読めない）。
- 戻している間のコマンドには、毎回 `MASTERSMITH_IMAGE_TAG=pre-role-menu` と `--no-build` を付ける。
- 戻した後は、前の版の起動で ERROR「適用中の DSL を読めないため、適用中の DSL が無い状態で起動します」が1件出る（想定どおり）。【依頼者】前の版の DSL の画面で既定の DSL（版 1）を生成して適用する（監査に残る）。
- 戻している間は、ロール・グループ・業務のメニュー・権限の受け渡しの画面と API がありません（表は残り、上げ直すとそのまま使われる）。管理の可否は前の版も管理者の印で決まるため、変わりません。前の版は `ExitOnOutOfMemoryError` を持たないため、RB-30 は当たりません。
- 上げ直した後は、版 2 の DSL を生成し直して適用する（`cd-config.md` の 5節）。
- 版 1 の DSL を新しい版に投入すると 422（書式の版の誤り）です。版 1 は読み替えません。
- 配備の前の複写を戻す（第二の手）のは、移行の失敗・データの破損のときだけです。配備の後のロール・グループ・割り当て・作業ロール・DSL の履歴と監査の行が失われます。

## 2. 警報ごとの初動の表（この Intent の差）

前の3つの手順書の表に、次を足します。警報は 16 件のまま、ファイルも変えていません（`alarms.md` 1節）。**配備したアプリは外部エクスポートが無効のため、今は配備したアプリでは鳴りません。**

| 警報名 | 変わったこと | 鳴ることの確かめ | 参照する手順 |
|---|---|---|---|
| アプリの停止（`ms-app-absent`） | JVM がヒープの不足で止まると、起き直さないため鳴り続ける。止めてから 10 分 20 秒で `Alerting`（`Pending` は 5 分 20 秒） | 確かめた（`alarms.md` 2.1） | RB-30 |
| 管理画面への拒否の増加（`ms-forbidden`） | 置き場の 403（業務の結果、監査に残らない）と、ロール・グループの管理の API への入口の 403 も数える | 確かめた（39 件で鳴った。うち置き場 13 件） | RB-33 |
| 5xx の割合の増加（`ms-5xx-ratio`） | 新しい API の要求も分母に入る（確かめの間は全体の 84.5%） | Unverified | RB-30（止まる前の 5xx）・元の手順書の RB-01 |
| コネクションプールの待ち（`ms-pool-pending`） | ロール・グループの変える操作と権限の YAML の適用も、確定の後の監査で2本目を使う | performance-validation で確かめる | 1節の RB-08 の行・RB-28 |
| ERROR のログの増加（`ms-error-logs`） | 監査の書き込みの失敗の ERROR に、この Intent の種類も入る。JVM が止まる直前の行は JSON でなく、数に入らない | performance-validation で確かめる | 1節の RB-08 の行・RB-30 |
| 監査の書き込みの失敗（`ms-audit-fail`） | この Intent の種類（`GROUP_*`・`ROLE_*`・`WORK_ROLE_SWITCHED`・`ROLE_TRANSFER_APPLIED`）も数える | performance-validation で確かめる | 前の手順書の RB-25 |
| JVM のメモリの不足の兆し（`ms-heap`） | 5 分続いたときだけ鳴るため、急に尽きる場合は鳴らない | 鳴らない見込み（確かめていない） | RB-30 |
| （警報なし） | `ROLE_BUSY`・`GROUP_BUSY`・権限の受け渡しの 409・413・400、見えなくなった、画面の固まり | — | RB-28・RB-29・RB-32・RB-34 |

## 3. 定期の確認（この Intent の差）

前の3つの手順書の表に、次を足します。

| 確かめ | コマンド・操作 | 通る条件 |
|---|---|---|
| アプリが止まっていない（アプリを使う前） | `docker compose --profile targetdb-postgres ps -a app` | `Up …（healthy）`。`Exited (3)` なら RB-30 |
| 取り合いの多さ（前の日） | `docker compose logs --no-log-prefix --since 24h app` を `grep -c '行の排他を取れませんでした'` と `grep -c '権限の受け渡しの入口を取れませんでした'` で数える | どちらも 0 か少数（多ければ RB-28・RB-29） |
| ロール・権限の設定の保存（大きく変えた後） | 【依頼者】権限の受け渡しの画面で書き出し、`~/.mastersmith-backup/` に置く（RB-31） | 最後に変えた後の YAML がある |
| 内部DB の複写（ロール・グループ・割り当てを大きく変えた後） | 【承認】README の「内部DBのバックアップと戻し方」 | 割り当て・グループ・作業ロールは YAML に入らないため、複写で守る |
| 救済の要否（初期管理者をわざと止めた・印を外した・パスワードを画面で変えたとき） | 【依頼者】`.env` の初期管理者の設定を、README のとおりにそろえたか | 次の再起動で意図せず救われない |

## 4. 確かめられなかったこと

| 項目 | 内容 |
|---|---|
| RB-30 | 配備の上限（2g・最大ヒープ約 1 GiB）で、どの操作・大きさで止まるか。手順の確かめは最大ヒープ 192 MB で行った。止まったときの `ms-app-absent` が鳴るまでの時間は、`docker compose stop` で止めた測り（10 分 20 秒）で、JVM が自分で止まるときは9〜10 分の見込み（`alarms.md` 2.1） |
| RB-28 | BUSY を実際に起こしたときの2行の結び付き（performance-validation の `groupPoolLimit`・`rolePoolLimit`） |
| RB-29 | 本文をゆっくり送る管理者が入口を持ち続ける時間（Tomcat の既定に頼る。README の注意） |
| RB-31 | 保存した YAML を別の時点の内部DB に適用して戻す流れ（確かめ → 適用の API は Build and Test・Observability Setup で確かめ済み。戻しの流れそのものは確かめていない） |
| 1節の RB-22 の置き換え | 救済が配備で働くところは見ていない（Intent 261004-safety-carryover の結合テストと、この Intent の配備で働かないことを確かめた。Q3: A） |
| 警報 | `ms-pool-pending`・`ms-error-logs`・`ms-audit-fail` は performance-validation。`ms-5xx-ratio`・`ms-audit-slow` は Unverified（feedback-optimization） |

### 確かめの片付け（2026-10-15 02:51:44 JST）

1. `docker compose -p mastersmith-ir -f docker/perf/compose.yaml down -v` で、使い捨ての環境のコンテナ・ボリューム `mastersmith-ir_perf-data`・網 `mastersmith-ir_default` を消した（残り 0）。
2. ホームの下の一時の置き場を中身ごと消した。中身は、仮の署名鍵・仮の初期管理者のパスワード・DSL と権限の YAML・コンテナのログの写し・トークンです。
3. `caffeinate -dims` を止めた。`pmset -g log` で、確かめの間（02:46〜02:53 JST）に Sleep・DarkWake が無かったことを確かめた。
4. 配備したアプリ・Mailpit・見本の対象DB は動いたまま、lgtm は止まったまま（どれも触っていない）。配備したアプリには要求を送っていない。`.env` は開いていない。

## Sources

- `aidlc/spaces/default/intents/261004-role-menu/operation/incident-response/incident-response-questions.md`（1節 F1〜F12、決まっていること、Q1〜Q4、確認済みの要約）
- 前の3つの手順書: `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/runbooks.md`、`aidlc/spaces/default/intents/260925-user-management/operation/incident-response/runbooks.md`、`aidlc/spaces/default/intents/260930-user-admin/operation/incident-response/runbooks.md`
- この Intent の Observability Setup: `operation/observability-setup/dashboards.md`・`alarms.md`・`log-queries.md`・`tracing-config.md`・`slo-config.md`
- この Intent の配備: `operation/deployment-pipeline/rollback-runbook.md`（2〜7節）・`cd-config.md`・`deployment-pipeline-questions.md`（Q6: A）、`operation/deployment-execution/deployment-log.md`（10節 S8）・`health-check-report.md`（3節の複写）
- NFR の設計: `construction/group/nfr-design/reliability-design.md`・`security-design.md`・`scalability-design.md`、`construction/role/nfr-design/reliability-design.md`（2.6 入口）・`security-design.md`・`scalability-design.md`、`construction/navigation/nfr-design/reliability-design.md`・`security-design.md`、`construction/dsl-v2/nfr-design/security-design.md`・`logical-components.md`
- 基盤の設計: `construction/group/infrastructure-design/infrastructure-specification.md`・`construction/role/infrastructure-design/infrastructure-specification.md`・`construction/navigation/infrastructure-design/infrastructure-specification.md`・`construction/app-frame-ui/infrastructure-design/infrastructure-specification.md`・`construction/role-admin-ui/infrastructure-design/infrastructure-specification.md` と各 `monitoring-design.md`
- `construction/build-and-test/build-and-test-summary.md`（BT-Q1c・BT-Q2）
- `README.md`（「使える管理者がいなくなったとき（初期管理者の救済）」「戻し方」「内部DBのバックアップと戻し方」「グループの管理の API」「ロールと権限の API」「権限の受け渡し（B6）」「業務のメニューの API」「骨組みの画面」）、`Dockerfile`、`compose.yaml`、`docker/perf/compose.yaml`
- `aidlc/spaces/default/intents/261003-user-admin-followup/inception/requirements-analysis/requirements.md`（FR2）
- 実行したコマンドの出力:
  - `docker compose -p mastersmith-ir -f docker/perf/compose.yaml up -d --wait`・`start app`・`ps -a app`・`down -v`
  - `docker inspect mastersmith-ir-app-1 --format …`、`docker exec … /proc/1/cmdline`、`docker logs mastersmith-ir-app-1 | grep -c …`
  - `curl`（使い捨てのアプリへの要求）、`pmset -g assertions`・`pmset -g log`

## Assumptions & Open Questions

- RB-30 の「JVM が自分で止まるときに `ms-app-absent` が鳴るまで9〜10 分」は推測です（`alarms.md` 2.1）。
- RB-30 の手順は最大ヒープ 192 MB で確かめました。配備の上限で止まる操作と大きさは確かめていません。
