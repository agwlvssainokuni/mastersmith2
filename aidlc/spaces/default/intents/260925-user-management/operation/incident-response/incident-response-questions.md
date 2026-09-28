# Incident Response — 質問

障害の対応の記録（runbooks・incident-plan・escalation-matrix）は、前の Intent（`aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/`）で初めて作りました（RB-01〜RB-12、警報ごとの初動の表、定期の確認）。この Intent で増えた障害を、読み取りだけで洗い出しました（2026-09-29）。

この Intent で増えた、起こりうる主な障害：
- **メールの受け手（Mailpit）が止まっている・応答しない**
  - 招待と送り直しは 201・200 のまま `sendResult: FAILED` が返ります。
  - ログは WARN「メールを送信できませんでした」と INFO「招待メールを送れませんでした。一覧から送り直せます」です。
  - 配備した `.env` は `SPRING_MAIL_HOST=mailpit` で、Mailpit は見たいときだけ起動する決まりです。そのため、Mailpit を止めている間の招待は FAILED になります。
- **招待を使えない設定**
  - ベース URL が無い・不正なとき、または SMTP が無い・不正なときは、招待と送り直しが 503 `INVITATION_NOT_CONFIGURED` になります。
  - ベース URL が無いだけのときは WARN が出ません。起動の INFO「招待を使える設定かを点検しました」の `unavailableReasons` で見分けます。
- **起動が止まる設定の誤り**：招待の有効期限・保存の長さの値の誤り、メールのテンプレートを読み込めないとき。
- **招待の定期の削除の失敗**：警報がありません。
- **総当たりの疑い（R1）**：登録の完了、今のパスワード。
- **招待のトークン・URL が漏れた疑い**：メール・閲覧の履歴・Mailpit・バックアップから。
- **`127.0.0.1` で開いたときの拒否**：ログイン・更新・ログアウトが 403 `ORIGIN_NOT_ALLOWED` になります。
- **見た目の設定の不正**：既定の値に置き換わり、WARN が出ます。
- **戻すときの注意**：V7・V8 は巻き戻せません。

次のことは、前の Intent の決定と今回の記録で決まっているため、質問にしません。

| 決まっていること | 出典 |
|---|---|
| 受け手と判断者は依頼者1名。AI は頼まれたときに調べと手順の実行を補助し、止める・戻す・データを戻す操作は依頼者の承認を得てから行う。知らせは Grafana の画面で見るだけ | 前の Intent の Q1: A |
| 復旧の目標は RTO 1日・RPO は最後の手での複写まで。複写は配備の前などに手で行う。招待・プリファレンスも同じ内部DB にあるため、同じ扱いにする | 前の Intent の Q2: A |
| 重さは2段（高・低） | 前の Intent の Q3: A |
| 前の RB-11 の「利用者のパスワードを変える画面・API は無い」は古くなったため直す（U2 の `POST /api/me/password` と U7 の画面で変えられる） | U2・U7 |
| p95 の警報3件（`ms-login-p95` など）が鳴らないことを、警報ごとの初動の表に書く（次の Intent で直す） | この Intent の `operation/observability-setup/alarms.md` 2節 |
| クラウドの仕組み（SSM・Incident Manager・AWS Backup）は、配備先が決まるまで作らない | project.md の Deployment |

## Q1. 成果物の書き方

A. 前の Intent の手順書を正とし、この Intent で増えた障害（RB-13 から）と、古くなった箇所の直し（RB-11 の表、警報の表の p95 の3件）だけを書く（これまでの運用の段と同じ書き方）
B. 前の Intent の手順書を写して直し、今の全体を1つの手順書にまとめ直す（読む場所は1つになるが、前の手順書と中身が重なる）
X. Other (please specify)

[Answer]: A

## Q2. 招待のトークン・URL が漏れた疑いのときの手当て

A. その招待を一覧から取り消す（204。設定によらず取り消せる）。必要なら、同じメールアドレスへ新しく招待し直す
B. 一覧から送り直す（古いトークンは使えなくなり、同じ招待のまま新しいリンクを送る）
C. A に加えて、Mailpit を止めて消し、受けたメールを消す
X. Other (please specify)

[Answer]: C

## Q3. Mailpit を止めている間の招待

A. 見たいときだけ起動する決まりのままにする。招待・送り直しの前に Mailpit を起動し、止まっていて FAILED になったら、起動してから一覧から送り直す（手順書に書く）
B. Mailpit を常に起動しておく（メモリの上限 256MB）。止まっていたら RB で起動する
X. Other (please specify)

[Answer]: A

## Q4. 承認済みの `alarms.md` の記述の誤り

Observability Setup の `alarms.md` 1節で「招待・登録・送信の失敗は、既存の `ms-5xx-ratio`・`ms-error-logs` で拾います」と書きましたが、誤りでした。

- 送信の失敗は 201・200 の応答と WARN・INFO のログだけで、どちらの警報にも当たりません。
- 逆に、設定の不足による 503 は WARN のログですが、`ms-5xx-ratio` には数えられます。

A. 承認済みの `alarms.md` は書き換えず、この段の `runbooks.md` に正しい見分け方と誤りを明記する
B. `alarms.md` を直し、元の文と直した理由を記録に残す（コミットは承認を得てから）
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

回答をまとめると、この段では次のとおり進めます。

- **書き方（Q1: A）**：前の Intent の手順書（RB-01〜RB-12）・障害の流れ・受け手の表を正とし、この Intent の差だけを書きます。
  - `runbooks.md`：増えた障害を RB-13 から書きます。
    - RB-13：メールの受け手が止まっている・応答しない（`sendResult: FAILED`、失敗の種類、送り直し）
    - RB-14：招待を使えない設定（503 `INVITATION_NOT_CONFIGURED`、`unavailableReasons`、ベース URL が無いときは WARN が出ない）
    - RB-15：起動が止まる設定の誤り（招待の有効期限・保存の長さ、メールのテンプレート）
    - RB-16：招待の定期の削除の失敗（警報なし、Loki の問い合わせ）
    - RB-17：総当たりの疑い（登録の完了・今のパスワード、R1。疑いがあるときだけ README の問い合わせを流す）
    - RB-18：招待のトークン・URL の漏えいの疑い（Q2）
    - RB-19：`127.0.0.1` で開いたときの拒否と、見た目の設定の不正
    - RB-20：この Intent の版を戻すときの注意（V7・V8、初期管理者、`rollback-runbook.md` を参照）
  - 前の手順書から古くなった箇所の直しも書きます。
    - RB-11：パスワードは U2・U7 で変えられるようになった。変えた後も、ほかの端末のリフレッシュトークンとアクセストークンは残る。
    - 警報の表：p95 の警報3件は鳴らない（次の Intent で直す）。
    - 15節：確かめられなかったことの見直し。
  - `incident-plan.md`・`escalation-matrix.md`：前の Intent の文書を正とし、今回の差を書きます（高の例に招待のトークンの漏えいの疑いを足す、AI の承認が要る操作に招待の取り消しと Mailpit の削除を足す、など）。
- **トークンの漏えいの疑い（Q2: C）**：依頼者が一覧からその招待を取り消し、必要なら招待し直します。あわせて、Mailpit を止めて消し、受けたメールを消します。
- **Mailpit を止めている間の招待（Q3: A）**：見たいときだけ起動する決まりのままにします。
  - 招待・送り直しの前に Mailpit を起動します。
  - FAILED になったら、起動してから一覧から送り直します。
- **`alarms.md` の誤り（Q4: A）**：承認済みの `alarms.md` は書き換えません。`runbooks.md` に正しい見分け方と誤りを明記します。
  - 送信の失敗は、ログ（WARN「メールを送信できませんでした」）と、ダッシュボードの「失敗の種類ごとの数」、招待の一覧の FAILED で見ます。
  - 設定の不足による 503 は、`ms-5xx-ratio` に数えられます。
- **決まっていること**：受け手1名、重さ2段、RTO 1日・RPO は最後の複写まで、クラウドの仕組みは作らない（前の Intent の決定と project.md）。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
