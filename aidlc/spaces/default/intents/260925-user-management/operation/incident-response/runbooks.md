# 障害ごとの手順書（runbooks）

前の Intent の手順書（`aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/runbooks.md`、以下「前の手順書」）を正とし、この Intent（利用者の管理: U1 メール・U2 プリファレンスとパスワードの変更・U3 招待と登録の完了・U4〜U7 画面・U8 見た目の設定）で増えた障害と、古くなった箇所の直しだけを書きます（Q1: A）。

- 0節（どの障害でも最初に行うこと）・RB-01〜RB-12・14節（定期の確認）は、前の手順書のままです。1節で直す箇所を除きます。
- 記号: 【承認】は、AI が行うときに依頼者の承認が要る操作です。【依頼者】は依頼者が行う操作です（`.env` の編集、秘密情報が要るログイン、画面の操作）。印の無いものは読み取りだけです。
- ログの文言は、`backend/src/main/java/` のソースで確かめたものです（2026-09-29）。確かめられなかったものは「推測」と書きます。
- **値を出さない。** メールアドレス・招待のトークン・招待の URL・パスワードは、コマンドの出力・記録・報告に出しません。ログは件数と、ロガー・キーの名前だけで見ます（project.md の Corrections）。

## 1. 前の手順書から直す箇所

| 箇所 | 前の手順書の記述 | 今 |
|---|---|---|
| RB-11 の取り替えの表の「利用者のパスワード」の行 | 「アプリにパスワードを変える画面・API は無い」「取り替えの手段が無い」 | 利用者は画面のプリファレンス（U7）か `POST /api/me/password`（U2）で自分のパスワードを変えられます。**ただし、変えた後もアクセストークンは有効期限まで使え、ほかの端末のリフレッシュトークンもそのまま使えます**（すべてをまとめて無効にする仕組みは無い。README の「利用者のプリファレンスとパスワードの変更（U2）」）。疑いのある端末でログアウトして、そのリフレッシュトークンを無効にします。ほかの利用者のパスワードを管理者が変える手段は、今もありません |
| RB-11 の「見分け方」 | JWT・bcrypt・秘密の項目の名前の件数 | 招待のトークンと URL も同じ扱いで数えます（RB-18） |
| 13節 警報ごとの初動の表 | 「ログインの応答の遅れ」「トークンの更新の応答の遅れ」「確認用 API の遅れ」を初動の対象としている | **この3件は鳴りません。** アプリの指標 `http_server_requests` にバケットが無く、式が値を出さないためです（この Intent の `operation/observability-setup/alarms.md` 2節、次の Intent で直す）。それまでの遅れは、ダッシュボードの「API ごとの時間」の行（招待と登録だけ、トレースから作る指標）と、利用者からの知らせで気づきます |
| 13節の下の注意「Loki では、ログのキーと値で絞り込めない」 | 後の Intent で直す | 今は Loki でキーで絞り込めます（この Intent の `operation/observability-setup/log-queries.md` 1節で、`failureKind`・`invitationId` などがキーの名前の属性で届くことを確かめた）。どの Intent で直ったかは確かめていません（Intent `260924-followup-fixes` で Loki へ送るキーの伏せ字を足しているため、その前後と見られる。推測） |
| 15節「手段が無いもの」 | 利用者のパスワードを変える画面・API | 上の RB-11 の行のとおり、自分のパスワードは変えられるようになりました。リフレッシュトークンをまとめて無効にする仕組みは、今も無いです |
| 15節「未確認の手順」の古い版の起動（U4-MIGRATION） | 未確認 | この Intent の戻しの練習で、1つ前の版 `mastersmith:pre-user-management` が V7・V8 を当てた内部DB で起動し、ログイン・更新・ログアウトができることを確かめました（`operation/deployment-execution/health-check-report.md` 3節） |

## 2. 承認済みの `alarms.md` の誤り（Q4: A）

この Intent の `operation/observability-setup/alarms.md` 1節に「招待・登録・送信の失敗は、既存の `ms-5xx-ratio`（5xx の割合）と `ms-error-logs`（ERROR のログの増加）で拾います」と書きましたが、**誤り**です。承認済みの文書は書き換えず、ここに正しい見分け方を書きます。

| 起きること | 応答 | ログ | 当たる警報 |
|---|---|---|---|
| メールの送信の失敗（受け手が止まっている・応答しない・拒む） | 招待は 201、送り直しは 200 のまま、`sendResult` が `FAILED` | WARN「メールを送信できませんでした」、INFO「招待メールを送れませんでした。一覧から送り直せます」 | **無い**（5xx でも ERROR でもない）。ダッシュボードの「失敗の種類ごとの数」と、招待の一覧の FAILED で見る（RB-13） |
| 招待を使えない設定での招待・送り直し | 503 `INVITATION_NOT_CONFIGURED` | WARN「要求をエラー応答に変換しました」（`code`・`status`） | `ms-5xx-ratio` に数えられる（503 のため）。ERROR ではないため `ms-error-logs` には当たらない（RB-14） |
| 送信の想定外の失敗・結果の記録の失敗 | 500 `INTERNAL_ERROR` | ERROR「想定外のエラーが起きました」 | `ms-5xx-ratio`、`ms-error-logs`（5 分で 5 件を超えたとき） |
| 登録の完了の想定外の失敗 | 500 | ERROR「想定外のエラーが起きました」 | 同上 |

## RB-13 メールの受け手が止まっている・応答しない（招待のメールが届かない）

**見分け方**

| 手がかり | 内容 |
|---|---|
| 症状 | 管理者の招待の一覧で、送信の結果が「失敗」（`sendResult: FAILED`）。招待した人にメールが届かない。招待そのものは確定している |
| アプリのログ | WARN「メールを送信できませんでした」（キー `templateId`・`language`・`failureKind`・`exceptionType`）と、INFO「招待メールを送れませんでした。一覧から送り直せます」（キー `invitationId`・`operation`（`INVITE`・`RESEND`）・`failureKind`） |
| 失敗の種類 | ログの `failureKind` は大文字（`CONNECTION_FAILED`・`TIMEOUT`・`REJECTED` など）。指標のタグ `mail_failure_kind` は小文字。Mailpit が止まっているときは `TIMEOUT` と `CONNECTION_FAILED` のどちらも出ました（名前が引けないため。`operation/observability-setup/dashboards.md` 2節）。時間切れは 3 秒 |
| 受け手の状態 | `docker compose --profile mail ps mailpit`（配備した環境では、Mailpit は見たいときだけ起動する。止まっているのが普通の状態） |
| ダッシュボード | 「メールの送信」の行の「失敗の種類ごとの数」（手元の監視を起動しているときだけ。最初の 1 件は数えられないことがある） |
| 送信の途中でアプリが止まったとき | 招待の行は送信の結果が記録されないまま残り、一覧では FAILED に見える（U3 の `nfr-design/reliability-design.md`） |
| 警報 | 無い（2節） |

**重さ**: 低（招待は一覧から送り直せる。ほかの機能に影響しない）。

**手順（Q3: A）**

1. 招待・送り直しの前に、【承認】`docker compose --profile mail up -d mailpit` で受け手を起動する（見たいときだけ起動する決まりのまま。README の「手元でメールを見る」）。
2. 既に FAILED になった招待は、受け手を起動してから、【依頼者】管理者の招待の一覧で「送り直す」。送り直すと古いリンクは使えなくなり、新しいリンクが届く。
3. 受け手が動いているのに失敗が続くときは、アプリのログの `failureKind` と `exceptionType` を見る。
   - `REJECTED`: 受け手が宛先や差出人を拒んだ。
   - `TIMEOUT`: 受け手が応答しない。【承認】`docker compose --profile mail restart mailpit`。
4. `.env` の `SPRING_MAIL_*`・`MASTERSMITH_MAIL_FROM` を変えた後なら、RB-14 の起動のログを見る。
5. 見終わったら、【承認】`docker compose stop mailpit` と `docker compose rm -f mailpit` で止めて消す（受けたメールも消える。個人に関する値を残さない）。

**確かめ方**: 送り直した招待の一覧の送信の結果が「送信済み」（`SENT`）、Mailpit の画面（`http://127.0.0.1:8025`）にメールが1通届いている（【依頼者】が見る。AI はメールの本文とリンクを表示しない）、アプリのログに「メールを送信しました」が1件増えた。

## RB-14 招待を使えない設定（503 `INVITATION_NOT_CONFIGURED`）

**見分け方**

| 手がかり | 内容 |
|---|---|
| 症状 | 画面の招待のボタンが押せない、または招待・送り直しが「招待を使えない設定です」になる（503 `INVITATION_NOT_CONFIGURED`）。**取り消しは設定によらず使える**（204） |
| 起動のログ | INFO「招待を使える設定かを点検しました」（キー `enabled`・`unavailableReasons`）。`unavailableReasons` は `BASE_URL_NOT_CONFIGURED`・`SMTP_NOT_CONFIGURED` の順に並ぶ |
| ベース URL | 形が正しくないときだけ WARN「招待のリンクのベースURL の形が正しくないため、招待を使えません。http か https の絶対URL を設定してください」（キー `item` `mastersmith.web.base-url`）。**値が無いときは WARN が出ない**ため、上の INFO の `unavailableReasons` で見分ける |
| メールの設定 | INFO「メールの設定を点検しました」（キー `state`: `CONFIGURED`・`NOT_CONFIGURED`・`INVALID`）。不正なときは WARN「メールの設定に欠け・不正があるため、メールを送りません。項目を直して再起動してください」（キー `items` に項目の名前だけ）。`INVALID` のときも `unavailableReasons` は `SMTP_NOT_CONFIGURED` になる |
| 警報 | 招待・送り直しの 503 は `ms-5xx-ratio` に数えられる（2節） |

**重さ**: 低（招待と送り直しだけが使えない）。

**手順**

1. 起動のログの INFO「招待を使える設定かを点検しました」と「メールの設定を点検しました」の `enabled`・`unavailableReasons`・`state` を見る（値は出ない）。
2. `.env` の項目があるかを件数だけで見る: 【承認】`grep -c '^MASTERSMITH_WEB_BASE_URL=.' .env`・`grep -c '^SPRING_MAIL_HOST=.' .env`（値は表示しない。project.md の Deployment）。
3. 【依頼者】`.env` を直す。配備した環境の値は `operation/deployment-execution/deployment-log.md` の手順 7（`MASTERSMITH_WEB_BASE_URL=http://localhost:8080`・`SPRING_MAIL_HOST=mailpit`・`SPRING_MAIL_PORT=1025`・`MASTERSMITH_MAIL_FROM=noreply@example.com`）。
4. 【承認】前の手順書の 0節の 3 でログを保存し、`docker compose --profile targetdb-postgres up -d app` で作り直す（設定は起動のときにだけ読まれる）。

**確かめ方**: 起動のログの `enabled` が `true`・`unavailableReasons` が空、Mailpit を起動して【依頼者】招待を1件（監査に `INVITATION_ISSUED` が残る。AI が行うときは送る前に伝える）。

## RB-15 起動が止まる設定の誤り（招待の長さ・メールのテンプレート）

前の手順書の RB-01（アプリが止まる・healthy にならない）の原因に、次の2つを足します。

| 起動のログ（`exit=1` で止まる） | 原因 | 手当て |
|---|---|---|
| 例外「設定 mastersmith.invitation.validity が決まりに合いません（正の値で、単位で割り切れる長さにしてください）」（`retention` も同じ形） | `.env` の `MASTERSMITH_INVITATION_VALIDITY`（1 時間以上で 1 時間で割り切れる）・`MASTERSMITH_INVITATION_RETENTION`（1 日以上で 1 日で割り切れる）の誤り | 【依頼者】`.env` を直すか項目を消し（既定 `24h`・`90d`）、【承認】作り直す |
| 例外「メールのテンプレートを準備できません（templateId=…, language=…, reason=…）」（`reason` は `MISSING`・`PARSE_ERROR` など） | イメージの中のテンプレートが欠けている・壊れている。配備したイメージの取り違えが疑われる（推測） | 動いている版のイメージを `docker inspect mastersmith-app-1 --format '{{.Image}}'` で見て、前の配備の記録と比べる。取り違えなら RB-20・`rollback-runbook.md` で戻す |

- 正常なときは、起動のログに INFO「メールのテンプレートを準備しました」（`count` 2）が出ます。

**重さ**: 高（アプリが止まる）。

## RB-16 招待の定期の削除の失敗

**見分け方**

| 手がかり | 内容 |
|---|---|
| ログ | ERROR「保存期間を過ぎた招待の削除に失敗しました。次の回に再び行います」。成功なら INFO「保存期間を過ぎた招待を削除しました」（キー `deleted`・`ended`・`expiredPending`） |
| 時刻 | 毎日 3 時 45 分（`MASTERSMITH_INVITATION_CLEANUP_CRON`）。PC が眠っている・アプリが止まっている日は動かない |
| 警報 | **無い**（`ms-cleanup-fail` はリフレッシュトークンの削除だけを数え、`ms-error-logs` は 1 件では鳴らない。`alarms.md` 1節の N3） |
| 見る場所 | 前の手順書の 14節の定期の確認（24 時間の ERROR の件数）、または Explore（Loki）の問い合わせ（`log-queries.md` 2節） |

**重さ**: 低（翌日に再び行われる。終わった招待が保存期間を過ぎても残るだけ）。

**手順**: 前の手順書の RB-12 の「使い終わったリフレッシュトークンの削除に失敗しました」と同じ扱い。続くなら内部DB の不調（前の手順書の RB-01・RB-03）を見る。

## RB-17 総当たりの疑い（登録の完了・今のパスワード、R1）

回数の制限も警報もありません（受け入れた危険 R1）。**疑いがあるときだけ**調べます（Observability Setup の Q3: A）。

**きっかけ**

- 警報 `ms-lock`（ロックの多発）・`ms-rejected`（回り込みの試み）。
- ダッシュボードの「API ごとの要求の数」で `/api/registration/verify`・`/api/registration/complete` が急に増えた。形の誤ったトークンの 404 と入力の誤りの 400 は監査に残らないため、ここでしか見えない。
- 依頼者の知らない利用者が一覧にいる。
- 利用者から「パスワードの変更を試みた覚えがない」などの知らせ。

**手順**

1. 【承認】README の「監査ログの確かめ方」の手順で、アプリを数秒止めて内部DB を複写し、読み取り（`ACCESS_MODE_DATA=r`）で開く。
2. README の2つの問い合わせを流す。どちらも、この Intent で列の名前を確かめ済みです（`operation/observability-setup/log-queries.md` 3節）。
   - 登録の完了の失敗の数え方（`failure_reason`・`source_ip` ごと）。
   - 今のパスワードの誤りの数え方（`actor_user_id` ごと）。
3. **送り元の IP と利用者の ID は、数と並びだけを見て、値を記録・報告に書かない。**
4. 1つの送り元から `INVITATION_NOT_FOUND` が多い、または1人の利用者に `CURRENT_PASSWORD_MISMATCH` が多いときは、疑いありとして重さを判断する。
   - 依頼者の知らない登録が見つかったら高。そうでなければ低。
5. 高のときは、次を行う。
   - その利用者の利用を控える。
   - 招待中のものを取り消す（RB-18 の手順 1）。
   - 配備先が決まるまでは、アプリを止めることも依頼者が判断する。
6. 複写は `~/.mastersmith-backup/` の外に出さず、調べ終わったら消す。

**重さ**: 低（依頼者の知らない登録が見つかれば高）。

## RB-18 招待のトークン・URL が漏れた疑い（Q2: C）

対象: 招待のメールのリンク（`/register#token=…`）が、本人以外に見られた・転送された・ログや共有の場所に写った疑い。**トークンとリンクの値を画面・記録・報告に出しません。**

**見分け方（件数だけを出す）**

```bash
# アプリのログにリンクの形があるか（0 が期待値）
docker compose logs --no-log-prefix app | grep -c '/register#token='
docker compose logs --no-log-prefix app | grep -c 'token='
```

- 前の手順書の RB-11 の件数（JWT・bcrypt・秘密の項目の名前）も同時に数えます。
- ほかの置き場所: Mailpit（最大 500 通を持つ）、ブラウザの閲覧の履歴（リンクを開いた PC）、保存したログ（`~/.mastersmith-incidents/`）、内部DB の複写（トークンはハッシュ値だけで、リンクは無い）。

**重さ**: 高（秘密の漏えいの疑い）。

**手順**

1. 【依頼者】管理者の招待の一覧で、その招待を「取り消す」（`POST /api/admin/invitations/{invitationId}/cancel`、204）。取り消しは、招待を使えない設定のときでも使えます。取り消した招待のリンクは、登録の完了でもリンクの確かめでも `REGISTRATION_LINK_INVALID` になります。監査には `INVITATION_CANCELLED` が残ります。
2. 必要なら、【依頼者】同じメールアドレスへ新しく招待し直す（取り消した招待は招待中ではないため、`INVITATION_ALREADY_PENDING` にならない）。
3. 【承認】Mailpit を止めて消し、受けたメールを消す: `docker compose stop mailpit` と `docker compose rm -f mailpit`（ボリュームを持たないため、コンテナを消すとメールも消える）。
4. 既に登録が完了していた場合（一覧に無く、利用者にいる）は、本人が登録したかを依頼者が確かめる。本人でなければ、RB-17 の手順 5 と同じ扱い。
5. 漏れの元がアプリのログなら、コードの不具合として新しい Intent で直す（値が出ないことのテストを足す。project.md の Forbidden）。

**確かめ方**: その招待の一覧の状態が「取り消し」、手順 1 の後に【依頼者】漏れたリンクを開くと「このリンクは使えません」、上の件数が 0、`docker compose --profile mail ps mailpit` にコンテナが無い。

## RB-19 `127.0.0.1` で開いたときの拒否・見た目の設定の不正

| 症状とログ | 原因 | 手当て |
|---|---|---|
| ログイン・更新・ログアウトが 403 `ORIGIN_NOT_ALLOWED`。WARN「要求の送り元が自分の配信元と一致しないため拒否しました」。警報 `ms-origin` の数が増える | 配備の `.env` にベース URL（`http://localhost:8080`）を入れたため、Origin がこの値に固定された。`http://127.0.0.1:8080` で開くと合わない | 【依頼者】画面を `http://localhost:8080` で開き直す（README の「コンテナでの起動と確認」）。重さは低 |
| 起動のログの WARN「見た目の設定に許されない値が指定されたため、既定の値を使います」（キー `property`・`defaultValue`・`allowedValues`。設定した値は出さない） | `.env` の見た目の設定（U8）の値が許される値に無い | 画面は既定の値で動く。【依頼者】`property` の項目を README の U8 の値に直して、【承認】作り直す。重さは低 |
| 画面の最初の描画（ログインの画面を含む）が止まる | `/api/appearance` が応答しない（待ちに上限が無い。依頼者が受け入れた決定）。起こりにくい（推測） | 前の手順書の RB-01（アプリの状態）を見る。重さは高 |

## RB-20 この Intent の版を戻すときの注意

手順は `operation/deployment-pipeline/rollback-runbook.md` です。前の手順書の RB-10（配備の後の不具合）に次を足します。

- **V7（プリファレンス）・V8（招待）は巻き戻せません。** 第一の手（イメージだけを `mastersmith:pre-user-management` に戻す）では、1つ前の版は余分な列と表を許して起動します（戻しの練習で確かめた）。
  - 起動のときの Flyway の WARN「スキーマの版（8）が、この版の知っている最新（6）より新しい」は正常です。
- 第二の手（配備の前のバックアップ `~/.mastersmith-backup/mastersmith-data-202609290030-before-user-management.tgz` を展開する）は、配備の後に作った招待・利用者・監査の記録が消えます。【依頼者】の判断で行います。
- **初期管理者のメールアドレスは変えないで戻します**（1つ前の版が利用者を足そうとして失敗しうる。未確認）。
- 戻している間は、招待の一覧・登録の完了の画面・プリファレンスの画面がありません。招待のリンクは期限まで残りますが、開いても使えません。
- ブラウザの表示の設定（U4 の localStorage）が戻した後に残ることは確かめていません（Deployment Pipeline の Q6: B）。

## 21. 警報ごとの初動の表（この Intent の差）

前の手順書の 13節の表に、次を足します。

| 警報名 | 変わったこと | 参照する手順 |
|---|---|---|
| 5xx の割合の増加 | 招待・送り直しの 503 `INVITATION_NOT_CONFIGURED` も数に入る | RB-14 |
| ERROR のログの増加 | 招待の定期の削除の失敗（1 件では鳴らない）、送信の想定外の失敗 | RB-16・2節 |
| 送り元の不一致の多発 | `127.0.0.1` で開いたログインでも増える | RB-19 |
| ログインの応答の遅れ・トークンの更新の応答の遅れ・確認用 API の遅れ | 鳴らない（1節） | — |

- メールの送信の失敗・招待の定期の削除の失敗・総当たりの疑いには、警報がありません（2節・RB-16・RB-17）。

## 22. 定期の確認（この Intent の差）

前の手順書の 14節の表に、次を足します。

| 確かめ | コマンド | 通る条件 |
|---|---|---|
| 招待を使える設定（起動の後） | `docker compose logs --no-log-prefix app \| grep '招待を使える設定かを点検しました' \| tail -n 1` を `grep -c '"enabled":true'` で数える | 1 |
| 招待の定期の削除（前の日） | `docker compose logs --no-log-prefix --since 24h app` を `grep -c '保存期間を過ぎた招待の削除に失敗しました'` で数える | 0 |
| 送信の失敗（招待をした日） | `docker compose logs --no-log-prefix --since 24h app` を `grep -c 'メールを送信できませんでした'` で数える | 0（0 でなければ RB-13） |

## 23. 確かめられなかったこと

| 項目 | 内容 |
|---|---|
| 例外の場面 | 「招待のリンクのベースURL がありません」（`IllegalStateException`）が投げられる場面。設定が無いときは 503 で先に止まるため、内部の守りと見られる（推測） |
| 監査の種類 | 招待中の人がログインしたときの監査は `LOGIN_FAILED` で、理由は利用者が見つからないのと同じと見られる（推測。U3 の `security-design.md`） |
| ログの文言 | 送信の想定外の失敗の `failureKind`（`UNEXPECTED` など）は、この段では起こしていない |
| 戻し | 初期管理者のメールアドレスを変えて戻したときの1つ前の版の動き、戻したときの localStorage |

## Sources

- 前の手順書（正とする）: `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/runbooks.md`
- `operation/incident-response/incident-response-questions.md`（Q1〜Q4、確認済みの要約）
- `operation/observability-setup/dashboards.md`・`alarms.md`・`log-queries.md`・`tracing-config.md`（この Intent）
- `construction/u3-invitation/nfr-design/reliability-design.md`・`security-design.md`、`construction/u2-user-preferences/nfr-design/reliability-design.md`・`security-design.md`、`construction/u8-instance-appearance/nfr-design/reliability-design.md`、`construction/u1-mail/nfr-design/security-design.md`
- `construction/u2-user-preferences/infrastructure-design/infrastructure-specification.md` ほか U3〜U8 の `infrastructure-specification.md`、`construction/u3-invitation/infrastructure-design/monitoring-design.md`（7節・N3・N4）
- `operation/deployment-pipeline/rollback-runbook.md`・`deployment-strategy.md`、`operation/deployment-execution/deployment-log.md`・`health-check-report.md`
- `README.md`（「メール（U1）」「手元でメールを見る」「招待と登録の完了（U3）」「利用者のプリファレンスとパスワードの変更（U2）」「監査ログの確かめ方」「コンテナでの起動と確認」）
- ソース: `backend/src/main/java/cherry/mastersmith/mail/`（`SmtpMailSender`・`MailConfig`・`TemplatePreparationException`）、`invitation/`（`InvitationSettings`・`InvitationMailDispatcher`・`InvitationCleanupJob`・`InvitationService`・`InvitationProblemTypes`・`InvitationAvailability`）、`appearance/service/AppearanceService.java`、`user/domain/UserProblemTypes.java`

## Assumptions & Open Questions

- 23節の項目は確かめていません（推測と書いたもの）。
