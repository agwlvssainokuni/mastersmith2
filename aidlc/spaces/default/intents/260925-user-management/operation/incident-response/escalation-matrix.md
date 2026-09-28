# 受け手と権限の表（escalation-matrix）

前の Intent の表（`aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/escalation-matrix.md`、以下「前の表」）を正とし、この Intent の差だけを書きます（Q1: A）。

## 1. 重さごとの受け手と期限

変えません。受け手・判断者は依頼者1名です。高は気づいたらすぐ着手し、1日の内に戻します。低は当日か翌日に着手します（前の表の 1節）。

## 2. 承認なしで行ってよい調べの差（前の表の 2.1節に足す）

| 調べ | コマンドの例 | 気をつけること |
|---|---|---|
| Mailpit の状態 | `docker compose --profile mail ps mailpit` | Mailpit の画面・API（`http://127.0.0.1:8025`）は **AI は開かない**。メールの本文に招待のリンクとメールアドレスがある |
| 招待・メールの起動の点検 | `docker compose logs --no-log-prefix app` を `grep '招待を使える設定かを点検しました'`・`grep 'メールの設定を点検しました'` で絞る | 出るのは `enabled`・`unavailableReasons`・`state` と項目の名前だけ（値は出ない） |
| 送信の失敗の件数と種類 | `grep -c 'メールを送信できませんでした'`、キー `failureKind` の種類ごとの数 | 報告には件数と種類だけを書く |
| 招待のリンクがログに出ていないか | `grep -c '/register#token='`（`runbooks.md` の RB-18） | 件数だけを出す。0 でなければ、行の時刻とロガーの名前だけを見る |
| R1 の問い合わせ（複写の読み取り） | README の2つの問い合わせ（`runbooks.md` の RB-17） | 既にある複写だけを読む。新しく複写するときはアプリを止めるため承認が要る（3節）。送り元の IP・利用者の ID は数だけを見る |

## 3. 依頼者の承認が要る操作の差（前の表の 3節に足す）

| 操作 | 例 | 承認のときに示すこと |
|---|---|---|
| Mailpit を起動する | `docker compose --profile mail up -d mailpit` | VM のメモリを 256MB 使う。受けたメールは消すまで残る |
| Mailpit を止めて消す | `docker compose stop mailpit`・`docker compose rm -f mailpit` | **受けたメールがすべて消える**（ボリュームが無いため）。まだ登録していない招待のリンクも Mailpit からは見られなくなる。招待自体は残り、一覧から送り直せる |
| 版を戻す | `rollback-runbook.md`（戻し先 `mastersmith:pre-user-management`） | 前の表の「版を戻す」の行の戻し先と注意を、この Intent の `runbooks.md` の RB-20 に読み替える（V7・V8 は巻き戻せない、戻している間は招待・登録・プリファレンスの画面が無い） |
| 監査に残る要求を送る | 招待・送り直し・取り消し・登録の完了・パスワードの変更 | 監査ログに残り、消せない。登録の完了は利用者も残る。宛先に実在のアドレスを使わない |

- 招待の取り消し・送り直し・招待し直しは、管理者の画面の操作のため【依頼者】が行います。AI は API で行いません（ログインの資格情報が要るため。project.md の Corrections）。

## 4. AI がしないことの差（前の表の 4節に足す）

- Mailpit の画面・API を開いて、メールの本文・招待のリンク・メールアドレスを読む・表示すること。
- 招待のトークン・招待の URL を、コマンド・記録・報告に書くこと（project.md の Forbidden）。
- 実在の宛先や外部の SMTP へメールを送ること、`.env` の `SPRING_MAIL_*` を外部の SMTP に向けること（project.md の Forbidden）。
- 初期管理者の INFO のログの `email` の値を表示すること。起動のログはロガーと項目の名前と件数だけで見ます（project.md の Deployment の学び）。

## 5. 知らせ（通知）

- 変えません（前の表の 5節）。
- 警報は 16 件のままです。ただし p95 の3件は鳴りません（`runbooks.md` の 1節）。
- メールの送信の失敗・招待の定期の削除の失敗・総当たりの疑いには、警報がありません。定期の確認（`runbooks.md` の 22節）で見つけます。

## Sources

- 前の表（正とする）: `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/escalation-matrix.md`
- `operation/incident-response/incident-response-questions.md`（Q1〜Q4、確認済みの要約）、`operation/incident-response/runbooks.md`・`incident-plan.md`
- `operation/deployment-pipeline/rollback-runbook.md`、`README.md`（「手元でメールを見る」「監査ログの確かめ方」）
- `aidlc/spaces/default/memory/project.md`（Forbidden・Corrections・Deployment）

## Assumptions & Open Questions

None.
