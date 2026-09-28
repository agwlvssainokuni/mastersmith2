# 負荷の試験の結果（test-results）

2026-09-29 に、`load-test-plan.md` のとおり使い捨ての環境で流した結果です。生データは `build/perf-results/um/`（リポジトリの管理外）にあります。

## 1. 条件

| 項目 | 値 |
|---|---|
| 期間 | 01:50〜02:14（配備したアプリは 01:50:12 に止め、02:14:41 に healthy に戻した。約 24 分半） |
| 環境 | colima の VM（CPU 4・メモリ 6GiB）。使い捨てのアプリ `mastersmith:local`（`83b572b`、上限 CPU 4・メモリ 2g）、Mailpit、見本の PostgreSQL（使わない、動いたまま）。手元の監視は止めていた |
| 負荷 | k6 `grafana/k6:2.3.0`、同時 10（VUS）。時間で終わる場面は 60 秒ずつ。`invitationCancel`・`registrationComplete` は 100 回ずつ |
| 内部DB | 場面を重ねるたびに膨らんだまま測った（試験の後の複写で 117MB） |
| 共有 | k6・Mailpit はアプリと同じ VM の CPU を分け合う。測った値には、その分が混ざりうる |

## 2. 場面ごとの結果

どの場面も k6 の閾値（p95 と `checks` の率 1）をすべて満たし、終わりのコードは 0 でした。`http_req_failed` の率が高い場面は、4xx が期待の応答の場面です（`checks` で状態コードと `code` を確かめた）。

| 場面 | 目標（p95） | p95 | p99 | 最大 | 件数 | checks | 判定 |
|---|---|---|---|---|---|---|---|
| `appearance` | 300 ms | 0.96 ms | 2.17 ms | 33.9 ms | 1,256,141 | 100% | Met |
| `preferencesGet` | 1 秒 | 1.53 ms | 3.65 ms | 50.9 ms | 823,025 | 100% | Met |
| `preferencesSave` | 1 秒 | 2.01 ms | 3.87 ms | 157 ms | 641,579 | 100% | Met |
| `preferencesInvalid` | 1 秒 | 3.69 ms | 5.95 ms | 94.4 ms | 242,894 | 100% | Met |
| `passwordChange` | 2 秒 | 1.79 s | 1.83 s | 1.92 s | 398 | 100% | Met（余裕 0.21 秒） |
| `passwordMismatch` | 1 秒 | 905 ms | 962 ms | 1.05 s | 792 | 100% | Met（余裕 95 ms） |
| `passwordInvalid`（流し直し） | 1 秒 | 3.35 ms | 5.21 ms | 71.4 ms | 255,195 | 100% | Met（3節） |
| `loginSuccess` | 1 秒 | 904 ms | 929 ms | 982 ms | 801 | 100% | Met（余裕 96 ms） |
| `refresh` | 1 秒 | 2.32 ms | 5.27 ms | 937 ms | 570,206 | 100% | Met |
| `invite` | 5 秒 | 29.4 ms | 37.8 ms | 1.59 s | 35,136 | 100% | Met |
| `invitationResend` | 5 秒 | 20.9 ms | 26.6 ms | 276 ms | 63,862 | 100% | Met |
| `invitationList`（1ページ目） | 1 秒 | 90.1 ms | 124 ms | 371 ms | 6,124 | 100% | Met |
| `invitationList`（最後のページ） | 1 秒 | 91.2 ms | 120 ms | 370 ms | 6,124 | 100% | Met |
| `invitationCancel` | 1 秒 | 5.12 ms | 11.0 ms | 20.5 ms | 100 | 100% | Met |
| `registrationVerify`（有効） | 1 秒 | 1.77 ms | 2.88 ms | 22.3 ms | 197,976 | 100% | Met |
| `registrationVerify`（形の誤り・見つからない） | 1 秒 | 2.09 ms・2.24 ms | 3.34 ms・3.51 ms | 12.7 ms | 197,976 ずつ | 100% | Met |
| `registrationComplete` | 1 秒 | 931 ms | 974 ms | 1.01 s | 100 | 100% | Met（余裕 69 ms） |
| `registrationInvalid` | 1 秒 | 2.96 ms | 4.35 ms | 20.1 ms | 261,370 | 100% | Met |
| `registrationRejected`（形の誤り・見つからない） | 1 秒 | 2.19 ms・2.37 ms | 3.58 ms・3.88 ms | 34.8 ms | 257,208 ずつ | 100% | Met |

- bcrypt を計算する場面（ログイン・パスワードの変更・今のパスワードの誤り・登録の完了）は、目標との余裕が小さいです（69〜96 ms。パスワードの変更は 2 回計算で 0.21 秒）。U2・U3 の NFR 要件が「余裕が小さい」と記録していたとおりで、CPU の上限を下げる・同時の数が増えると届かなくなりえます。
- 招待の送信を含む場面（招待・送り直し）は、受け手が同じ VM の Mailpit のため、SMTP の時間はごく短いです。実在の受け手では、U1 の時間切れ 3 秒までの時間が加わりえます。

## 3. 1回目の `passwordInvalid` の遅れ（原因を確かめた）

- 1回目（01:56:46〜01:59:15）は、閾値は満たした（p95 3.31 ms）ものの、1件だけ最大 **1分29秒** の要求があり、場面が 60 秒ではなく 2分29秒かかりました。
- アプリのログに ERROR は無く、WARN は期待の `VALIDATION_FAILED` だけでした。コンテナは OOMKilled も再起動もしていません。
- PC の電源の記録（`pmset -g log`）に、01:56:50 から 92 秒の「Idle Sleep」がありました（バッテリー駆動、画面は消灯）。場面ごとに `caffeinate -i` を起こし直す台本だったため、前の場面の終わり（01:56:45）で守りが外れ、次の場面が始まった直後に PC が眠りました。
- 同じ条件で流し直すと（02:08:43〜02:09:43）、最大 71.4 ms・p95 3.35 ms で、遅れは再現しませんでした。流し直した結果を正とします。

## 4. 受け手が応答しないときの1件（U3-NFR6.2）

同時の数 1 で、管理者の招待を3件と送り直しを1件、状態ごとに測りました。

| 受け手の状態 | 応答 | 送信の結果 | 時間 | 失敗の種類（ログ） |
|---|---|---|---|---|
| 止まっている（コンテナを停止） | 201・200 | FAILED | 0.003〜0.075 秒 | `CONNECTION_FAILED` |
| 受け付けるが何も返さない（`docker pause` で一時停止） | 201・200 | FAILED | 3.02〜3.08 秒 | `TIMEOUT`（`MailSendException`） |

- どちらも 5 秒以内に FAILED で応答しました（Met）。時間切れの場合は U1 の SMTP の時間切れ 3 秒がそのまま応答に乗ります。
- 途中で試した「別のコンテナに名前 `mailpit` を付けて受け付けるだけにする」形は、アプリが前の受け手の宛先を覚えていたためか `CONNECTION_FAILED` のままで、測りに使えませんでした（推測。原因は確かめていない）。

## 5. 接続プール

各場面の後に、使い捨てのアプリの `/actuator/metrics` を読みました。

| 指標 | 値 |
|---|---|
| `hikaricp.connections.timeout`（待ちの時間切れの累計） | すべての場面の後で **0** |
| `hikaricp.connections.acquire` の最大（直近の窓） | 0.7〜12.5 ms（最大は `preferencesGet`・`preferencesSave` の後の 12.5 ms） |
| `hikaricp.connections.pending` | 読んだ時点ですべて 0 |
| `hikaricp.connections.max` | 30 |

- 借りるまでの待ちの最大は 12.5 ms で、5 秒より十分小さいです。同時 10 件で上限 30 に収まっています。
- 使用中の最大の本数は測っていません（数秒ごとの値ではなく、時間切れの累計と待ちの最大で判断する決まり。project.md の Testing Posture）。

## 6. 監査の件数の突き合わせ

使い捨てのアプリを止め、内部DB を複写して読み取り（`ACCESS_MODE_DATA=r`）で数えました。

| 監査 | 件数 | k6 などの成功の件数 | 一致 |
|---|---|---|---|
| `REGISTRATION_COMPLETED`（成功） | 100 | `registrationComplete` 100 | 一致 |
| `INVITATION_CANCELLED`（成功） | 100 | `invitationCancel` 100 | 一致 |
| `PASSWORD_CHANGED`（成功） | 398 | `passwordChange` 398 | 一致 |
| `PASSWORD_CHANGED`（失敗、`CURRENT_PASSWORD_MISMATCH`） | 792 | `passwordMismatch` 792 | 一致 |
| `INVITATION_RESENT`（成功） | 63,866 | `invitationResend` 63,862 ＋ 4節の送り直し 4 | 一致 |
| `INVITATION_ISSUED`（成功） | 35,368 | `invite` 35,136 ＋ setup の招待 220（送り直し 10・確かめ 10・取り消し 100・登録の完了 100）＋ 4節の招待 12 | 一致 |
| `REGISTRATION_FAILED`（失敗、`INVITATION_NOT_FOUND`） | 514,416 | `registrationRejected` の拒否 514,416（形の誤り 257,208 ＋ 見つからない 257,208） | 一致 |
| `LOGIN_SUCCEEDED` | 930 | `loginSuccess` 801 ＋ 各場面の setup と VU ごとのログイン | 突き合わせの対象外 |

- 利用者は 122 名（仮の管理者 1 ＋ 試験用 21 ＋ 登録の完了 100）で、招待は `COMPLETED` 100・`CANCELLED` 100・`PENDING` 35,168 でした。
- **承認済みの記録との食い違い**：形の誤ったトークンでの登録の完了の拒否（404）も、監査に `REGISTRATION_FAILED`・`INVITATION_NOT_FOUND` として残りました。監査に残らないのは、次の2つです。
  - リンクの確かめ（`/api/registration/verify`）の 404（`registrationVerify` の 395,952 件の拒否は監査に無い）
  - 登録の完了の入力の誤り（400）
  - この Intent の次の2つの文書は「形の誤ったトークンは監査に残らない」と読める書き方で、登録の完了については誤りです。依頼者の決定（承認の場、「記録だけ」）で、2つの文書は書き換えず、この節を正とします。
    - Observability Setup の `log-queries.md` 3節（「形の正しくないトークンの総当たりは監査の問い合わせでは見えず」）
    - Incident Response の `runbooks.md` の RB-17（「形の誤ったトークンの 404 と入力の誤りの 400 は監査に残らない」）

## 7. 秘密の確かめ

| 確かめ | 件数 |
|---|---|
| 使い捨てのアプリのログの `/register#token=` | 0 |
| 同じログの `token=` | 0 |
| 同じログの `@example.com`（招待の宛先） | 0 |
| 同じログの JWT の形 | 0 |
| k6 の結果（`build/perf-results/um/`）の `register#token=`・`@example.` | 0 ファイル |
| 同じログの `@example.test` | 2（初期管理者の INFO の `email`。前からある動きで、据え置きを受け入れ済み。値は表示していない） |

## 8. 片付け

- 使い捨ての環境（コンテナ・ボリューム・網）、一時の環境ファイル（ホームの下）、内部DB の複写を消しました。
- 配備したアプリを `docker compose start app` で起動し直し、8 秒で healthy、`/actuator/health` は UP でした。

## Sources

- `operation/performance-validation/load-test-plan.md`・`performance-validation-questions.md`
- `build/perf-results/um/*.log`・`*.json`・`run.log`（k6 の出力と hikaricp の値）
- 実行したコマンドの出力: `docker run grafana/k6:2.3.0 run …`、`docker run busybox wget …/actuator/metrics/…`、`java -cp h2-2.4.240.jar org.h2.tools.RunScript …;ACCESS_MODE_DATA=r`、`docker logs mastersmith-perf-app-1`（件数だけ）、`pmset -g log`、`docker stats`

## Assumptions & Open Questions

- 4節の「受け付けるだけの別のコンテナ」で `CONNECTION_FAILED` になった理由は確かめていません（推測）。
- 6節の食い違いは、依頼者の決定で2つの文書を書き換えず、この節を正としました。
