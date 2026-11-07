# 構成と設定

アプリが読む環境変数（`.env`・`.env.targetdb`）の一覧と既定値をまとめた文書です。

## 環境変数

秘密情報は `.env`（Git 管理外）から環境変数で渡します。見本は `.env.example` です。手元で試す対象DB（compose の profile `targetdb-*`）の2項目だけは `.env.targetdb`（Git 管理外。見本は `.env.targetdb.example`）に入れます。値を空にした変数は「空の値」として渡るため、既定値を使う設定は `.env` に書かないでください。

| 環境変数 | 既定値 | 内容 |
|---|---|---|
| `MASTERSMITH_CONTAINER_CPUS` | `4` | アプリのコンテナの CPU の上限（`docker compose` だけが使う）。Docker の VM の CPU が 4 に満たない PC では下げる。照合の時間の目標は 4 が前提 |
| `MASTERSMITH_CONTAINER_MEMORY` | `2g` | アプリのコンテナのメモリの上限（`docker compose` だけが使う。`1g`・`1536m` の形）。colima の VM が CPU 4・メモリ 6GiB に満たない PC では下げる。1g では高い負荷で止まりうる（`docs/deployment.md` の「コンテナの資源の上限」の「既知の制約」） |
| `MASTERSMITH_JAVA_OPTIONS` | なし | JVM に足す引数（空白で区切る。例: `-XX:MaxRAMPercentage=40.0 -XX:MaxMetaspaceSize=256m`）。既定の引数（最大ヒープはメモリの上限の 50%、ヒープが尽きたら JVM を止める、タイムゾーン Asia/Tokyo（対象DB の日時の受け渡しと画面の時刻はこの値に依らない。`docs/target-db.md` の「日時・時刻の値」）、H2 の詰め直しのスレッド 1 本）の後ろに置くため、同じ指定は上書きになる。空白を含む値は扱わない。イメージの作り直しは要らず、コンテナの作り直し（`docker compose up -d`）で効く |
| `MASTERSMITH_DB_URL` | `jdbc:h2:file:./data/mastersmith;DEFRAG_ALWAYS=TRUE` | 内部DBの接続先（コンテナでは `/app/data/mastersmith`）。`;DEFRAG_ALWAYS=TRUE` は、アプリの停止時（DB を閉じるとき）にファイルを詰め直す指定。付けないと、DSL の履歴の古い行を消しても H2 のファイルが縮まず、投入と適用を重ねるたびに大きくなる（起動し直しても縮まない）。上書きするときも `;DEFRAG_ALWAYS=TRUE` を付ける（アプリを止めずに詰め直す `docker/hikari-pool.sh compact` にも要る） |
| `MASTERSMITH_DB_USERNAME` | `sa` | 内部DBの利用者 |
| `MASTERSMITH_DB_PASSWORD` | 空 | 内部DBのパスワード（秘密情報） |
| `MASTERSMITH_DB_MAXIMUM_POOL_SIZE` | `30` | 内部DBの接続プールの接続の数の上限。同時の要求がこの数に達すると監査の記録が欠けうる（`docs/operations.md` の「監査ログ」の「既知の制約」を参照） |
| `MASTERSMITH_HEALTH_DB_TIMEOUT` | `2s` | ヘルスチェックの内部DBの確認の制限時間 |
| `MASTERSMITH_WEB_BASE_URL` | なし | ベースURL（例 `http://localhost:8080`）。3つに使う: 招待のリンクの元（無い・形が正しくないと招待と送り直しだけが 503）、ログイン・更新・ログアウトの Origin の確かめ（入れると Origin がこの値に固定される）、エラー応答の `type` の URL（無ければ要求から組み立てる）。http か https の絶対 URL で、問い合わせ・`#`・利用者情報を含めない |
| `MASTERSMITH_INVITATION_VALIDITY` | `24h` | 招待の有効期限の長さ。1 時間以上で 1 時間で割り切れる長さ（`48h` など。`90m` は起動を止める）。招待メールの「このリンクは N 時間有効です」に同じ時間の数が入る。**長くしすぎない**（上限は置いていない。リンクが漏れたときに使える期間が延びる） |
| `MASTERSMITH_INVITATION_RETENTION` | `90d` | 終わった招待（完了・取り消し・置き換え）は終わった日時から、期限切れの招待中は有効期限から、この長さを過ぎたら定期の削除で消す。1 日以上で 1 日で割り切れる長さ。監査の記録は消さない |
| `MASTERSMITH_INVITATION_CLEANUP_CRON` | `0 45 3 * * *` | 招待の定期の削除の時刻（Spring の cron。リフレッシュトークンの削除の 3 時 30 分と重ねない） |
| `MASTERSMITH_WEB_TRUST_FORWARDED_HEADERS` | `false` | 転送元のヘッダー（`X-Forwarded-*`・`Forwarded`）を信頼するか |
| `MASTERSMITH_WEB_MAX_REQUEST_BODY_SIZE` | `1MB` | 要求の本文の大きさの上限（DSL の投入の API を除く。ログインと認可の確かめの後に確かめる。`docs/dsl.md` の「DSL の管理の API」を参照） |
| `MASTERSMITH_DSL_MAX_SUBMIT_SIZE` | `10MB` | DSL の投入の API（`POST /api/admin/dsl/preview`）だけの要求の本文の上限（10,485,760 バイト） |
| `MASTERSMITH_DSL_HISTORY_LIMIT` | `20` | DSL の適用の履歴の件数の上限（1 以上）。超えた分は古いものから消す |
| `MASTERSMITH_OBSERVABILITY_EXPORT_ENABLED` | `false` | 外部エクスポート（トレース・ログ・指標の OTLP の送信）を有効にするか |
| `MASTERSMITH_OBSERVABILITY_EXPORT_ENDPOINT` | `http://localhost:4318` | OTLP の受け手（HTTP）のベースURL |
| `MASTERSMITH_TRACING_SAMPLING_PROBABILITY` | `1.0` | 外部へ送るトレースの割合（トレースIDは割合によらずすべての要求に付く） |
| `MASTERSMITH_TRACE_USE_DYNAMIC_LOGGER` | `true` | メソッドの呼び出しの追跡で、対象のクラスの名前のロガーを使うか |
| `MASTERSMITH_TRACE_HIDE_PROXY_CLASS_NAMES` | `true` | 追跡で、代理のクラス（プロキシ）の名前を隠すか |
| `MASTERSMITH_TRACE_LOG_EXCEPTION_STACK_TRACE` | `true` | 追跡で、例外のときにスタックトレースを出すか |
| `MASTERSMITH_TRACE_ENTER_MESSAGE` | `ENTER $[targetClassShortName]#$[methodName]($[arguments])` | 追跡の入るときの文言 |
| `MASTERSMITH_TRACE_EXIT_MESSAGE` | `EXIT  $[targetClassShortName]#$[methodName](): $[returnValue]` | 追跡の出るときの文言 |
| `MASTERSMITH_TRACE_EXCEPTION_MESSAGE` | `EXCEPTION $[targetClassShortName]#$[methodName](): $[exception]` | 追跡の例外のときの文言 |
| `MASTERSMITH_TARGET_DB_TYPE` | 空 | 対象DB の種類（`mysql`・`mariadb`・`postgresql`）。7つの項目（種類・ホスト・番号・DB の名前・スキーマ・ユーザー名・パスワード）がすべて空なら対象DB を使わない |
| `MASTERSMITH_TARGET_DB_HOST` | 空 | 対象DB のホスト名（英数字と `.`・`-`・`_`、または `[...]` の IPv6） |
| `MASTERSMITH_TARGET_DB_PORT` | 空 | 対象DB の番号（1〜65535） |
| `MASTERSMITH_TARGET_DB_DATABASE` | 空 | 接続する DB の名前（MySQL・MariaDB はスキーマと同じ） |
| `MASTERSMITH_TARGET_DB_SCHEMA` | 空 | 読み取るスキーマの名前 |
| `MASTERSMITH_TARGET_DB_USERNAME` | 空 | 対象DB のユーザー名（読み取りの権限だけのアカウントを勧める） |
| `MASTERSMITH_TARGET_DB_PASSWORD` | 空 | 対象DB のパスワード（秘密情報） |
| `MASTERSMITH_TARGET_DB_CONNECT_TIMEOUT` | `3s` | 対象DB への接続の待ちの上限 |
| `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_GENERATE` | `20s` | 既定の DSL の生成で、問い合わせ1回の待ちの上限（1 秒以上） |
| `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_COMPARE` | `5s` | 照合で、問い合わせ1回の待ちの上限（1 秒以上） |
| `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_LIST` | `5s` | 業務データの一覧で、問い合わせ1回（件数・ページのそれぞれ）の待ちの上限（1 秒以上） |
| `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_DETAIL` | `5s` | 業務データの詳細で、問い合わせ1回の待ちの上限（1 秒以上） |
| `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_REFERENCE` | `5s` | 他テーブル参照の参照先（選択肢・検索・ラベル・保存前の確かめ）で、問い合わせ1回の待ちの上限（1 秒以上）。1 秒未満は対象DB 全体が使えなくなる |
| `MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL` | `10s` | 定義の取得（作成・更新の用途）で参照先の選択肢を読む全体の待ちの上限（1 秒以上）。最後の文は切り上げのため最大 1 秒未満超えうる。1 秒未満は対象DB 全体が使えなくなる |
| `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_WRITE` | `5s` | 業務データの書き込みで、文1回（事前の問い合わせ・書き込みの文・違反の後の確かめのそれぞれ）の待ちの上限（1 秒以上）。書き込みのドライバーの通信の待ちはこの値 + 5 秒 |
| `MASTERSMITH_TARGET_DB_POOL_MAXIMUM_SIZE` | `10` | 対象DB の接続の数の上限（内部DB のプールとは別） |
| `MASTERSMITH_TARGET_DB_POOL_IDLE_TIMEOUT` | `60s` | 使っていない対象DB の接続を閉じるまでの時間（10 秒以上。書き込みの接続と共用） |
| `MASTERSMITH_TARGET_DB_WRITE_USERNAME` | 空 | 業務データの書き込みのアカウントのユーザー名。接続先は読み取りの項目をそのまま使う。読み取りが使えて、ユーザー名とパスワードの両方があるときだけ書き込みを使い、片方だけなら項目の名前だけを WARN で出して書き込みを使わない（読み取りは動く） |
| `MASTERSMITH_TARGET_DB_WRITE_PASSWORD` | 空 | 業務データの書き込みのアカウントのパスワード（秘密情報） |
| `MASTERSMITH_TARGET_DB_WRITE_POOL_MAXIMUM_SIZE` | `5` | 対象DB の書き込みの接続の数の上限（読み取りの接続とは別） |
| `SPRING_MAIL_HOST` | なし | メールの SMTP の接続先（`docs/mail.md` の「メール」）。無ければメールを送らない。手元では `mailpit` |
| `SPRING_MAIL_PORT` | 方式の標準 | SMTP の番号（1〜65535）。STARTTLS では 587 を書く。数でない値は起動が止まる |
| `SPRING_MAIL_PROTOCOL` | `smtp` | `smtps` にすると接続の始めから暗号化する（SMTPS） |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE`・`SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED` | なし | 両方を `true` にすると STARTTLS（必須）。片方だけでは暗号化なしの扱い |
| `SPRING_MAIL_USERNAME`・`SPRING_MAIL_PASSWORD` | なし | SMTP の資格情報（パスワードは秘密情報）。暗号化なしの設定と一緒に使うと送らない |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_CONNECTIONTIMEOUT`・`..._TIMEOUT`・`..._WRITETIMEOUT` | `3000` | 接続・応答の待ち・書き込みの時間切れ（ミリ秒、1 以上の整数）。SMTPS では `MAIL_SMTPS_` の名前 |
| `MASTERSMITH_MAIL_FROM` | なし | 差出人のメールアドレス（小文字・前後に空白の無い形）。無ければメールを送らない |
| `MASTERSMITH_MAIL_FROM_NAME` | `MasterSmith` | 差出人の表示名 |
| `MASTERSMITH_APPEARANCE_BRAND_COLOR` | `blue` | インスタンスのブランドカラー（`blue`・`green`・`purple`・`orange`）。許されない値は既定を使い、起動時に警告を出す（`docs/appearance.md` の「インスタンスの見た目の設定」） |
| `MASTERSMITH_APPEARANCE_FONT_FAMILY` | `sans` | インスタンスのフォントファミリー（`sans`・`serif`）。許されない値は既定を使い、起動時に警告を出す |
| `MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD` | 空 | 手元で試す対象DB（compose の profile `targetdb-*`）の管理者のパスワード（秘密情報。`.env` ではなく `.env.targetdb` に入れる。アプリには渡らない） |
| `MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD` | 空 | 手元で試す対象DB の読み取りだけのアカウント `mastersmith_reader` のパスワード（秘密情報。`.env.targetdb` に入れる） |
| `MASTERSMITH_AUTH_SIGNING_KEY` | 空（必須） | アクセストークンの署名鍵（Base64、復元して 32 バイト以上。秘密情報。無い・短いと起動しない） |
| `MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL` | 空 | 初期管理者のメールアドレス（無い・不正なら作らずに警告） |
| `MASTERSMITH_AUTH_INITIAL_ADMIN_PASSWORD` | 空 | 初期管理者のパスワード（秘密情報。12 文字以上、UTF-8 で 72 バイト以内） |
| `MASTERSMITH_AUTH_ACCESS_TOKEN_TTL` | `5m` | アクセストークンの有効期限 |
| `MASTERSMITH_AUTH_REFRESH_TOKEN_TTL` | `24h` | リフレッシュトークンの有効期限（Cookie の寿命も同じ） |
| `MASTERSMITH_AUTH_LOCK_THRESHOLD` | `5` | ロックするまでの連続失敗回数（1 以上） |
| `MASTERSMITH_AUTH_LOCK_DURATION` | `30m` | ロックの時間 |
| `MASTERSMITH_AUTH_PASSWORD_BCRYPT_COST` | `12` | パスワードのハッシュ（bcrypt）の cost（4〜31） |
| `MASTERSMITH_AUTH_REFRESH_TOKEN_CLEANUP_RETENTION` | `7d` | 無効・期限切れのリフレッシュトークンを、期限からどれだけ残してから消すか |
| `MASTERSMITH_AUTH_REFRESH_TOKEN_CLEANUP_CRON` | `0 30 3 * * *` | 使い終わったリフレッシュトークンの削除を行う時刻 |

- メソッドの呼び出しの追跡は、対象のクラスのロガーを TRACE にしたときだけ出ます（例: `LOGGING_LEVEL_CHERRY_MASTERSMITH_COMMON_ERROR=TRACE`）。
- 信号ごとの送り先の上書きは Spring Boot の設定で行えます（例: `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT`）。
- ログのレベルは `LOGGING_LEVEL_<パッケージ>` で機能ごとに変えられます（既定は INFO）。
- 認証の署名鍵は必須です。`openssl rand -base64 32` で作った値を `.env` の `MASTERSMITH_AUTH_SIGNING_KEY` に入れます。値が無い・短いとアプリは起動しません。
- 署名鍵を替えるとき（鍵の交換）は、`.env` の値を替えてコンテナを作り直します。発行済みのアクセストークンは 401 になりますが、画面はトークンの更新で取り直すため、ログインし直しは要りません。
- 開発と E2E は `http://localhost` で行います。リフレッシュトークンの Cookie は `Secure` のため、localhost 以外のホスト名や IP への `http` ではログインが働きません。
