# メール

アプリが送るメール（SMTP）の設定、暗号化の方式、運用で設定しない値、手元でメールを見る受け手（Mailpit）をまとめた文書です。

## メール

アプリは、テンプレートを描いた HTML のメールを SMTP で1回だけ送ります（招待のメールに使う）。送信の部品は Spring Boot のメールの自動設定で作り、設定は環境変数（`.env`）だけから受け取ります（`docs/configuration.md` の「環境変数」の表の `SPRING_MAIL_*`・`MASTERSMITH_MAIL_*`）。配備先が決まるまで、実在の宛先・外部の SMTP へは送りません。手元でメールを見るときは、下の Mailpit へ送ります。

- 接続先（`SPRING_MAIL_HOST`）と差出人（`MASTERSMITH_MAIL_FROM`）の両方がそろい、設定に不正が無いときだけ送ります。何も設定しなければ送らず、警告も出しません（起動は続きます）。
- 設定の一部が欠けている・不正なとき（接続先が空白だけ、差出人の欠け・形の誤り・改行、表示名の改行、資格情報の片方だけ、範囲の外のポート、知らない方式、時間切れが 1 以上の整数でない）は、問題のある項目の名前だけ（例: `spring.mail.username`）を WARN で1件出し、送りません（値はログに出しません）。直したら再起動します。数でないポートだけは、設定の結び付けで起動が止まります。
- 起動のときに、設定の状態（`NOT_CONFIGURED`・`INVALID`・`CONFIGURED`）と暗号化の方式、準備したテンプレートの件数を INFO で1行ずつ出します（接続先・差出人・資格情報の値は出しません）。起動のときに SMTP へは接続せず、SMTP の状態はヘルスチェックに含めません。
- 送信は1回だけで、自動でやり直しません。送信ごとに、テンプレートの識別・言語・失敗の種類（`CONNECTION_FAILED`・`TIMEOUT`・`REJECTED` など）・例外の型の名前だけをログに1件出します。宛先・差し込んだ値・件名・本文・SMTP の応答はログに出しません。メールの部品（`org.eclipse.angus`・`jakarta.mail`）のログは OFF です。原因を調べるときは失敗の種類と例外の型の名前で絞り、受け手の側のログを見ます。

### 暗号化の方式の書き方

| 方式 | 書き方 | ポート |
|---|---|---|
| なし（NONE） | 何も書かない。手元の受け手（Mailpit）向け。資格情報と一緒には使えない（送らない） | 既定 25（Mailpit は 1025） |
| STARTTLS（必須） | `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true` と `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED=true` の両方 | **587 を書く**（書かないと 25 になる） |
| SMTPS | `SPRING_MAIL_PROTOCOL=smtps`（または `SPRING_MAIL_SSL_ENABLED=true`） | 既定 465 |

- **`starttls.enable` だけの設定（`required` を付けない）に注意**: 受け手が STARTTLS を受け付けないと、メールが暗号化されずに平文で届き、本文の招待の URL（トークン）が守られません。アプリはこの組を暗号化なし（NONE）と扱い、資格情報があれば送りませんが、資格情報の無い設定では平文で送ります。配備先が決まったら、STARTTLS（必須）か SMTPS を使ってください。
- TLS の相手の名前の確かめ（`checkserveridentity`）と TLS 1.2 以上は `application.yaml` で指定しています。証明書は JVM の既定の信頼できる一覧で確かめます。

### 運用で設定しない値

次の値は、秘密の漏れや暗号化の弱まりにつながるため、環境変数などで設定しないでください（アプリは既定の値を置いていますが、`spring.mail.properties.*` の口から上書きできてしまいます）。

- `SPRING_MAIL_PROPERTIES_MAIL_DEBUG`（`mail.debug`。宛先・本文・資格情報が標準出力に出る）
- `mail.smtp.ssl.trust`・`mail.smtps.ssl.trust`（証明書を無条件に信じる）
- `mail.smtp.ssl.checkserveridentity`・`mail.smtps.ssl.checkserveridentity` の `false`（相手の名前を確かめない）
- `mail.smtp.ssl.protocols`・`mail.smtps.ssl.protocols` に TLS 1.2 より古い版
- STARTTLS の `mail.smtp.starttls.required` の `false`
- `SPRING_MAIL_TEST_CONNECTION=true`（起動のときに SMTP へ接続し、つながらないと起動が止まる）
- `MANAGEMENT_HEALTH_MAIL_ENABLED=true`（SMTP の状態がヘルスチェックを左右する）
- メールの部品のロガーの水準（`LOGGING_LEVEL_ORG_ECLIPSE_ANGUS`・`LOGGING_LEVEL_JAKARTA_MAIL` を OFF から上げない）

### 手元でメールを見る（Mailpit、compose の profile `mail`）

```bash
docker compose --profile mail up -d mailpit    # 起動（画面は http://127.0.0.1:8025）
docker compose stop mailpit                    # 止める
docker compose rm -f mailpit                   # 消す（受けたメールも消える）
```

- アプリのコンテナから送るときは、`.env` に `SPRING_MAIL_HOST=mailpit`・`SPRING_MAIL_PORT=1025`・`MASTERSMITH_MAIL_FROM`（暗号化なし・資格情報なし）を書いて、`docker compose up -d` で作り直します。PC の上で動かす WAR（E2E など）からは `localhost:1025` へ送ります。
- 画面と API（8025）と SMTP（1025）は PC の localhost だけに開きます。Mailpit は受けたメールを外へ中継しません。
- ボリュームを置かないため、コンテナを消すと受けたメールも消えます。Mailpit が持つメールは既定で最大 500 通で、超えると古いものから消えます（イメージ `axllent/mailpit:v1.33.0@sha256:9b3a463be15354c0f0eba91697d80e3e0d8a5623ef0e627cacc836bc17fd5231` の起動の引数 `--max` の既定の値。有効期間 `--max-age` は既定で無し）。
- E2E（`./gradlew e2eTest`）の前に起動しておきます（`docs/verification.md` の「ビルドした WAR での画面の確認（E2E）」）。
