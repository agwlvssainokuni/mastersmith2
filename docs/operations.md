# 運用と監視

外部エクスポートの確かめ方、手元の監視（Grafana）、警報と対応の手順、例外のログ、監査ログをまとめた文書です。

## 外部エクスポートの確かめ方

受け取ったものを標準出力に出すだけの OTLP の受け手（OpenTelemetry Collector）を、profile `observability` で一緒に起動します。

```bash
# .env に次の2行を書く
#   MASTERSMITH_OBSERVABILITY_EXPORT_ENABLED=true
#   MASTERSMITH_OBSERVABILITY_EXPORT_ENDPOINT=http://otel-collector:4318
docker compose --profile observability up -d --build
curl -s http://localhost:8080/api/no-such-api > /dev/null
docker compose logs -f otel-collector      # トレース・ログはすぐ、指標は 60 秒ごとに届く
```

外部へ送るトレースからは、例外のメッセージとスタックトレース、要求の URL の問い合わせの部分を取り除いています。

外部へ送るログには、ログのキーと値（例: `dsl.operation`・`userId`・`code`）が、キーの名前そのものの属性として付きます。手元の監視（Loki）ではこの属性で絞り込めます。ただし、個人に関する値を持つ4つのキー `email`・`enteredEmail`・`sourceIp`・`userAgent` は、キーを残して値を `[REDACTED]` に置き換えてから送ります（送り出す直前に置き換えるため、標準出力のログ（`docker compose logs app`）では元の値のままです）。ログの本文（メッセージ）と例外の属性は置き換えません。初期管理者の作成のログのキー `maskedEmail` は、値がはじめから伏せ字（例 `a***@example.com`）のため置き換える対象にしておらず、外部エクスポートでも伏せ字の値がそのまま送られます。

## 手元の監視（Grafana）

指標・ログ・トレースを手元で見るときだけ、`grafana/otel-lgtm`（OTLP の受け手と Prometheus・Loki・Tempo・Grafana を1つにしたコンテナ）を profile `monitoring` で起動します。既定の起動（`docker compose up`）には含まれません。

```bash
# .env に次の2行を置き、見るときだけ行頭の「# 」を外して有効にする（見終わったらコメントに戻す。
# 送り先が無い間に有効のままだと、送信の失敗の警告がログに出るため）
#   # MASTERSMITH_OBSERVABILITY_EXPORT_ENABLED=true
#   # MASTERSMITH_OBSERVABILITY_EXPORT_ENDPOINT=http://lgtm:4318
docker compose --profile monitoring up -d --wait   # 2行を有効にした後。アプリも作り直されて送り始める
# ブラウザで http://localhost:3000/ を開き、ダッシュボードの「MasterSmith」→「MasterSmith の概要」を見る
docker compose --profile monitoring stop lgtm   # 見終わったら止め、.env の2行をコメントに戻して docker compose up -d で起動し直す
```

- 画面はログインなしの閲覧だけです（`127.0.0.1` にだけ結び付けています）。ダッシュボードと警報の決まりは `docker/monitoring/` のファイルで入れているため、画面からは保存できません（画面で一時的にいじることはできますが、読み込み直すと元に戻ります）。変えるときはファイルを直して `docker compose --profile monitoring up -d --force-recreate lgtm` で読み込み直します。
- 左のメニューの「Explore」で、ログ（データソース Loki、例 `{service_name="mastersmith"} |= "メールを送信できませんでした"`）・指標（Prometheus）・トレース（Tempo）を自由に問い合わせられます（`GF_USERS_VIEWERS_CAN_EDIT`）。
- アプリの指標 `http.server.requests` と `mastersmith.mail.send` は、決めた境界だけのバケットを持ちます（`application.yaml` の `management.metrics.distribution.slo`）。境界は `http.server.requests` が 100・250・500・1000・2000・5000 ms（警報のしきい値 500 ms・1000 ms を含む）、`mastersmith.mail.send` が 100・250・500・1000・2000・5000・10000 ms（送信の時間切れを含む）で、ほかに `+Inf` だけです。Prometheus での名前は `http_server_requests_milliseconds_bucket`・`mastersmith_mail_send_milliseconds_bucket` です。同じ境界のバケットは、処理中の数を表す `http_server_requests_active_milliseconds_bucket`・`mastersmith_mail_send_active_milliseconds_bucket` にも付きます（設定の名前が前方一致のため）。
- ログイン・トークンの更新・確認用 API の 95 パーセンタイルのパネルと警報（`ms-login-p95`・`ms-refresh-p95`・`ms-check-p95`）は、このバケットで値を出します。95 パーセンタイルは境界の間を按分した見積もりで、境界の刻みより細かい値は出ません。
- メールの送信の行の「送信の時間（95 パーセンタイル）」は `mastersmith.mail.send` のバケットで描いています（送信の成功と失敗を合わせる。目標の線 3 秒は境界の 2000 と 5000 の間）。招待と登録の行の API ごとの時間のパネルは、トレースから作る時間の指標（`traces_spanmetrics_latency`、境目が 2 倍刻みで粗い）のままです。
- 新しく現れた数の系列は、最初の 1 件が「1 時間の件数」に数えられません（Prometheus の `increase` が、最初の値の前を知らないため）。件数が少ないときは実際より少なく見えます。
- 警報は外へは知らせません。Grafana の「Alerting」→「Alert rules」（フォルダー MasterSmith）で状態を見ます。
- DSL の操作の行の見方は `docs/dsl.md` の「DSL の管理の API」の「DSL の操作の指標とログ」を参照してください。
- 指標は 60 秒ごとに届きます。起動の直後は空のパネルがあります。起動より前のログ（Spring の起動のログ）は送られません。
- 監視のコンテナのメモリの上限は 1.5GB（`1536m`）です。900MB では、DSL の行を含むダッシュボードを開いたときに Grafana がメモリの上限で止められました（コンテナは動き続け、画面だけが応答しなくなります）。colima の VM は CPU 4・メモリ 6GiB を前提にしています（アプリ 2GB・見本の対象DB 512MB と合わせて約 4GB。負荷の試験の間は監視を止めます）。VM が小さいときは `docs/deployment.md` の「コンテナの資源の上限」の手順で広げてください（`colima start --cpu 4 --memory 6`）。
- 集めたデータはボリューム `mastersmith_mastersmith-monitoring` に残ります。消すときは `docker volume rm mastersmith_mastersmith-monitoring`（アプリの内部DBのボリュームとは別です）。
- **監視の式の値を確かめる要求は、待ちを入れた少ない要求で送ります**。負荷の試験の場面（待ちが無い）は、2 VU でも毎秒 1,500〜2,200 件になり、lgtm の中の受け手（otelcol）と Tempo がメモリの上限で止められ、以後の指標・ログ・トレースが届かなくなります。使い捨ての環境（`perf/README.md`）の k6 の場面 `monitorLight`（1 VU・繰り返しごとに 1〜2 秒の待ち。ヘルスチェック・見た目の設定・管理者の確認用 API を順に回す。毎秒 約 0.6 件）を、指標の送信の周期（60 秒）を複数またぐ長さ（`MONITOR_DURATION` 既定 5 分）で流します。業務データの式（`ms-biz-target-db` など）を確かめるときは `MONITOR_BIZ=1` を足します（先に `bizPrepare`）。流し方は `perf/README.md` の「手元の監視の式の値を確かめる場面」。
- 外部エクスポートを有効にすると、JVM が `sun.misc.Unsafe` の警告を標準エラーに数行出します（送信に使う protobuf の部品による。1行1件の JSON ではありません）。

## 警報と対応の手順

手元の監視の警報（`docker/monitoring/provisioning/alerting/mastersmith.yaml`）が拾うもの・拾わないものと、気づいたときに見る場所です。下の記述は、ソースと結合テスト（`RegistrationApiIT`・`InvitationAuditIT`）で確かめた動きです。

### 応答の遅れの警報（95 パーセンタイル）

| 警報 | 対象の API | 鳴る条件 |
|---|---|---|
| `ms-login-p95` | `POST /api/auth/login` | 95 パーセンタイルが 1000 ms を超えた状態が 5 分続く |
| `ms-refresh-p95` | `POST /api/auth/session/refresh` | 95 パーセンタイルが 1000 ms を超えた状態が 5 分続く |
| `ms-check-p95` | `GET /api/admin/check` | 95 パーセンタイルが 500 ms を超えた状態が 5 分続く |

確認用 API の目標は 500 ms です。バケットの境界にある値にし、境界の間の按分の見積もりに頼らずに判定できるようにしています。

- 値は `http.server.requests` のバケット（100・250・500・1000・2000・5000 ms）から、直近 5 分の割合で見積もります（「手元の監視（Grafana）」）。境界の間は按分の見積もりのため、境界の刻みより細かい値は出ません。要求が無い間は値が出ません（NaN）。値が出ないときは、要求を送ってから見直してください。
- 鳴ったときは、ダッシュボード「MasterSmith の概要」の「応答時間（95 パーセンタイル）」の行で対象の API の推移を見て、「資源」の行（コネクションプールの待ち・JVM のヒープの使用率）と、Explore のトレース（Tempo）で遅い要求の内訳を確かめます。`ms-check-p95` は監査の記録の時間と DB の待ちも確かめます。

### 登録の完了の拒否（`POST /api/registration/complete`）

- 期限切れ・使用済み・取り消し済み・置き換え済み・存在しない・改ざん・同じメールアドレスの利用者がいる、のどれで拒否されても、形の誤ったトークン（長さ・使えない文字）を含めて、監査に `REGISTRATION_FAILED`（結果 `FAILURE`、理由は `failure_reason`）が残ります。
- 応答は 404 `REGISTRATION_LINK_INVALID` で、アプリのログには WARN（`要求をエラー応答に変換しました`、`code`・`status`）が1件出ます。4xx のため `ms-5xx-ratio` には当たらず、ERROR ではないため `ms-error-logs` にも当たりません。
- 入力の誤り（400 `VALIDATION_FAILED`）は監査に残りません。
- 気づく方法: 監査ログの `REGISTRATION_FAILED` を数えます（「監査ログの確かめ方」）。ログでは、Explore の Loki の `{service_name="mastersmith"} |= "要求をエラー応答に変換しました"` の結果を、項目 `code` の値（`REGISTRATION_LINK_INVALID`）で見分けます（`code` は文言ではなく項目のため、文言の絞り込み `|=` には当たりません）。

### リンクの確かめの拒否（`POST /api/registration/verify`）

- 登録の画面を開いたときのリンクの確かめで拒否されても、**監査には残りません**（出来事を出さないため）。応答は同じ 404 `REGISTRATION_LINK_INVALID` で、ログは上と同じ WARN が1件です。どの警報にも当たりません。
- 気づく方法: 上と同じ Loki の問い合わせです。登録の完了の拒否と同じ WARN のためログだけでは見分けられず、見分けるときは Explore のトレース（Tempo）で API の経路（`/api/registration/verify`）を見ます。監査に残らないため、監査ログからは数えられません。

### 招待メールの送信の失敗

- 送信に失敗しても招待は作られ、招待（`POST /api/admin/invitations`）は 201、送り直し（`POST /api/admin/invitations/{invitationId}/resend`）は 200 で、応答の `sendResult` が `FAILED` になります。5xx ではないため `ms-5xx-ratio` に当たりません。
- ログは WARN（`メールを送信できませんでした`、`templateId`・`language`・`failureKind`）と INFO（`招待メールを送れませんでした。一覧から送り直せます`、`invitationId`・`operation`・`failureKind`）が1件ずつで、ERROR は出ないため `ms-error-logs` にも当たりません。
- 監査には送信の失敗の種類はありません（招待そのものは `INVITATION_ISSUED`・`INVITATION_RESENT` として残ります）。
- 気づく方法: 招待の管理の画面の送信の結果の表示、Explore の Loki の `{service_name="mastersmith"} |= "メールを送信できませんでした"`、ダッシュボードの「メールの送信」の行の「失敗の種類ごとの数（1 時間）」。直ったら、招待の管理の画面から送り直します。

## 例外のログ（フィルターの中の想定外の例外）

- 想定外の例外のログは、例外を応答に変える境界で1回だけ出します。Spring MVC の中の例外は `GlobalExceptionHandler`、フィルターの中（認証の途中で内部DB の接続を借りられないときなど）の例外は `/error` の `ErrorPathController` が、ERROR「想定外のエラーが起きました」を1行出します（原因の例外のスタックトレースと、応答の 500 の Problem Details と同じ `traceId` つき）。障害を調べるときは、応答の `traceId` でこの行を探します。
- Tomcat がサーブレットの外へ出た例外を自分で出す ERROR（ロガー `org.apache.catalina.core.ContainerBase.[Tomcat].[localhost].[/].[dispatcherServlet]`、「Servlet.service() ... threw exception」）は、`application.yaml` の `logging.level` で止めています。止めないと、フィルターの中の同じ例外が、`traceId` を持たない Tomcat の行と `ErrorPathController` の行の2行の ERROR になります。
- **代わりに失うもの**: 応答を書き始めた後（確定した後）に例外がサーブレットの外へ出たときは、`/error` へ回らないため、その例外はどのログにも残りません。今のアプリの API は応答を流しながら書く形ではありませんが、そうした API を足すときは、この扱いを見直してください。

## 監査ログ

認証（ログインの成功・失敗・ログアウト）と管理者のみの API へのアクセスの拒否を、内部DBの `audit_events` の表に1件ずつ**追記**します。

- **追記だけ**です。アプリは監査イベントを変える・消す処理を持ちません。保存の期間は無期限で、古い記録を消す仕組みもありません（保存の期間と古い記録の扱いは決めていません）。
- **監査ログを見る画面・API はありません。** 確認は、開発者が内部DBを読み取りで開いて行います。
- 記録する項目は、発生の日時・種類（`LOGIN_SUCCEEDED`・`LOGIN_FAILED`・`LOGGED_OUT`・`ACCESS_DENIED`）・結果・入力されたメールアドレス・失敗の理由・接続元IP・User-Agent・要求のパス（アクセスの拒否のときだけ）・トレースIDです。パスワード・トークン・ハッシュ値は記録しません。
- 利用停止中の利用者のログインは、パスワードの正誤とロックの状態にかかわらず `LOGIN_FAILED`（結果 `FAILURE`、失敗の理由 `ACCOUNT_SUSPENDED`）を1件記録します。入力されたメールアドレスはほかのログインの失敗と同じく入り、操作した人と対象の利用者はほかのログインの失敗と同じく空です。停止中の利用者のトークンの更新とアクセストークンの認証は記録しません。停止中のログインの試みは失敗回数に数えません。
- パスワードの変更も記録します。種類は `PASSWORD_CHANGED` で、成功（結果 `SUCCESS`）と今のパスワードの誤り（結果 `FAILURE`、失敗の理由 `CURRENT_PASSWORD_MISMATCH`）を1件ずつ記録します。操作した人（`actor_user_id`）と対象の利用者（`target_user_id`）に本人の利用者 ID が入ります。入力の誤りと、氏名・表示の設定の保存は記録しません。
- 招待と登録の完了も記録します。種類は `INVITATION_ISSUED`・`INVITATION_RESENT`・`INVITATION_CANCELLED`（操作した管理者 `actor_user_id` と対象の招待 `target_invitation_id`、結果 `SUCCESS`）、`REGISTRATION_COMPLETED`（操作した人は空、対象の招待と作った利用者 `target_user_id`）、`REGISTRATION_FAILED`（結果 `FAILURE`、失敗の理由 `INVITATION_EXPIRED`・`INVITATION_ALREADY_USED`・`INVITATION_CANCELLED`・`INVITATION_NOT_FOUND`・`EMAIL_ALREADY_REGISTERED`、対象の招待は見つかったときだけ）です。拒否（400・404・409・503）・送信の失敗・リンクの確かめ・入力の誤り・期限切れの置き換えは記録しません。トークン・招待の URL・メールアドレスは記録しません。
- 利用者の管理の5つの操作も記録します。種類は `USER_ADMIN_GRANTED`（印を付ける）・`USER_ADMIN_REVOKED`（印を外す）・`USER_SUSPENDED`（止める）・`USER_RESUMED`（停止を解く）・`LOGIN_FAILURES_RESET`（失敗回数を戻す）で、どの種類も成功（結果 `SUCCESS`）と拒否（結果 `FAILURE`）の両方があります。失敗の理由は `USER_NOT_FOUND`（対象がいない）・`SELF_OPERATION`・`TARGET_SUSPENDED`・`NO_CHANGE`・`LAST_ACTIVE_ADMIN`（業務の拒否）・`NOT_ADMIN`（確かめ直しの 403）です。操作した管理者（`actor_user_id`）と、要求の利用者 ID（`target_user_id`。いない ID もそのまま）が入り、入力されたメールアドレスは空です。氏名・メールアドレス・失敗回数・トークンは記録しません。一覧の閲覧・氏名と言語の変更・409 `USER_ADMIN_BUSY`・入力の誤り（400）・認可の入口の 401 と 403（403 は既存の `ACCESS_DENIED` だけ）は、利用者の管理の種類としては記録しません。記録に失敗しても、5つの操作の応答（204・404・409・403）と状態は変わらず、ERROR には操作した人と対象の利用者（`actorUserId`・`targetUserId`）が載ります（メールアドレスは載りません）。
- 初期管理者の作成と救済も記録します。種類は `INITIAL_ADMIN_CREATED`（起動のときに初期管理者を作った）と `INITIAL_ADMIN_RESCUED`（起動のときに初期管理者を救済した。`docs/deployment.md` の「使える管理者がいなくなったとき（初期管理者の救済）」）で、どちらも結果 `SUCCESS` です。起動のときの出来事のため、列の使い方がほかの種類と違います。
  - 接続元IP（`source_ip`）は決まった値 `system`（IP の形ではない）、操作した人（`actor_user_id`）は空、対象の利用者（`target_user_id`）に初期管理者の利用者 ID が入ります。
  - 救済で当たった条件は、`rejection_kind` の列に `SUSPENDED`・`NO_ADMIN`・`PASSWORD` を決まった順に `+` でつないだ値で入ります（例 `SUSPENDED+NO_ADMIN+PASSWORD`）。列の名前は DSL の投入の拒否の種類のためのものですが、新しい列を足さずにこの列を使います（作成の行では空）。`failure_reason` は空です。
  - 入力されたメールアドレス・User-Agent・要求のパス・トレースIDは空です。メールアドレス・パスワード・ハッシュ値・トークンは記録しません。
  - 救済の途中で失敗して取り消されたときは、救済の行は残りません。記録に失敗したときは、救済・作成はそのまま確定し、ERROR（`監査イベントの記録に失敗しました`）が1回出ます。
- 対象の列（V7）: `target_user_id`（対象の利用者）・`target_invitation_id`（対象の招待。招待と登録の完了の出来事で使います）。どちらも空を許し、それ以前の種類の記録では空のままです。
- グループの管理も記録します。種類は `GROUP_CREATED`・`GROUP_RENAMED`・`GROUP_DELETED`・`GROUP_MEMBER_ADDED`・`GROUP_MEMBER_REMOVED` で、どの種類も成功（結果 `SUCCESS`）と業務の拒否（結果 `FAILURE`）の両方があります。失敗の理由は `GROUP_NOT_FOUND`・`USER_NOT_FOUND`・`GROUP_NAME_DUPLICATE`・`GROUP_IN_USE`・`NO_CHANGE` です。操作した管理者（`actor_user_id`）、対象のグループ（`target_group_id`。作成の名前の重なりでは空、いない ID の拒否では要求の ID）、メンバーの追加・外しでは対象の利用者（`target_user_id`。いない ID もそのまま）が入ります。`target_role_id` はグループの管理の種類では空です。`detail` には決めたキーだけの JSON（作成・削除・名前の重なりは `name`、名前の変更は `before`・`after`、メンバーの追加・外しは `groupName`、使用中の削除の拒否は `name`・`members`・`assignedRoles`）が入り、404 の拒否では空です。メールアドレス・氏名は記録しません。入力の誤り・`GROUP_BUSY`・一覧と詳細の読み取りは記録しません。記録に失敗しても、操作の応答と状態は変わらず、ERROR には操作した人・対象のグループ・対象の利用者の ID と `detail`（グループの名前を含む）が載ります（メールアドレスは載りません）。
- ロールと権限の管理も記録します。種類は `ROLE_CREATED`・`ROLE_RENAMED`・`ROLE_DELETED`・`ROLE_PERMISSION_CHANGED`（権限の保存と消す操作）で、どの種類も成功（結果 `SUCCESS`）と業務の拒否（結果 `FAILURE`）の両方があります。失敗の理由は `ROLE_NOT_FOUND`・`ROLE_NAME_DUPLICATE`・`ROLE_IN_USE`・`PERMISSION_TARGET_NOT_IN_DSL`・`DSL_NOT_APPLIED`・`NO_CHANGE` です。操作した管理者（`actor_user_id`）と対象のロール（`target_role_id`。作成の名前の重なりでは空、いない ID の拒否では要求の ID）が入り、`target_user_id`・`target_group_id` はこれらの種類では空です。`detail` には決めたキーだけの JSON（作成・削除・名前の重なりは `name`、名前の変更は `before`・`after`、使用中の削除の拒否は `name`・`assignedUsers`・`assignedGroups`、権限の変更は `roleName` と `changes`（対象ごとの `target`・`before`・`after`））が入り、404 の拒否では空です。16,384 文字に収まらない権限の変更は、`roleName`・`changedCount`・`firstChanges`（先頭の一部）の要約に替えます。メールアドレス・氏名は記録しません。入力の誤り・`ROLE_BUSY`・読み取りは記録しません。記録に失敗しても、操作の応答と状態は変わらず、ERROR には操作した人・対象のロールの ID と `detail`（ロールの名前と対象の名前を含む）が載ります（メールアドレスは載りません）。
- ロールの割り当てと作業ロールも記録します。種類は `ROLE_ASSIGNED`・`ROLE_UNASSIGNED`（利用者・グループへの割り当てと外し）と `WORK_ROLE_SWITCHED`（自分の作業ロールの切り替え）で、成功（`SUCCESS`）と業務の拒否（`FAILURE`）の両方があります。失敗の理由は、割り当てが `ROLE_NOT_FOUND`・`USER_NOT_FOUND`・`GROUP_NOT_FOUND`・`NO_CHANGE`、外しが `ROLE_NOT_FOUND`・`NO_CHANGE`、切り替えが `ROLE_NOT_ASSIGNED` です。割り当てと外しは操作した管理者・対象のロール（`target_role_id`）と、相手の利用者（`target_user_id`）かグループ（`target_group_id`）が入り、`detail` は `roleName` と `groupName`（利用者への割り当てでは null）です。切り替えは操作した人と対象の利用者がどちらも本人で、`target_role_id` は切り替え先のロール、`detail` は `fromRoleId`・`fromRoleName`・`toRoleName`・`storedBeforeRoleId`（前の保存。無ければ null）です。`ROLE_NOT_FOUND` と `ROLE_NOT_ASSIGNED` の拒否の `detail` は空です（`USER_NOT_FOUND`・`GROUP_NOT_FOUND`・`NO_CHANGE` は `roleName` を持ちます）。切り替えの拒否の `target_role_id` は要求のロールの ID です。メールアドレス・氏名は記録しません。今の保存と同じロールへの切り替え（何も変えない成功）・入力の誤り・`ROLE_BUSY`・`GROUP_BUSY`・読み取りは記録しません。記録に失敗しても、操作の応答と状態は変わりません。
- 権限の受け渡しの適用も記録します。種類は `ROLE_TRANSFER_APPLIED` で、1回の適用を1行にします。成功（`SUCCESS`）と業務の拒否（`FAILURE`、失敗の理由 `ROLE_TRANSFER_STALE`・`DSL_NOT_APPLIED`・`NO_CHANGE`・`ROLE_NAME_DUPLICATE`）があります。操作した管理者（`actor_user_id`）が入り、対象の列（`target_role_id`・`target_user_id`・`target_group_id`）は空です。`detail` は `fileSha256`（読み込んだ本文の SHA-256。本文を読む前の `DSL_NOT_APPLIED` では null）・`fingerprint`（成功は一致した指紋、拒否は要求の指紋）・`roles`（ロールごとの `name`・`roleId`（作るときは null）・`action`・`added`・`changed`・`cleared`・`notInDsl`）で、前後の値の全件は残しません。16,384 文字に収まらないときは `fileSha256`・`fingerprint`・`roleCount`・`firstRoles`（先頭の一部、最大 50 件）の要約に替えます。確かめ・書き出し・入力の誤り・`ROLE_BUSY` は記録しません。
- 対象の列（V10）: `target_role_id`（対象のロール）・`target_group_id`（対象のグループ）・`detail`（JSON の文字列、16,384 文字まで）。どれも空を許し、それ以前の種類の記録では空のままです。
- 監査イベントの `trace_id` は、同じ要求のアプリのログの `traceId` と一致します。1つの要求を追うときは、この値でログを絞り込みます。
- 記録に失敗しても、ログイン・ログアウト・401／403 の応答は変わりません。失敗したときは、アプリのログに ERROR（`監査イベントの記録に失敗しました`）が1回出ます。**この ERROR には、記録しようとした項目（メールアドレスを含む）がキーと値で載ります**。後から手で記録を補えるようにするためで、この ERROR に限った扱いです。パスワード・トークンは載りません。外部エクスポートで外へ送るときは、メールアドレス・接続元IP・User-Agent の値を `[REDACTED]` に置き換えます（「外部エクスポートの確かめ方」）。元の値は標準出力のログで見ます。
- パスワードの変更の記録に失敗したときの ERROR には、メールアドレスは載らず（出来事がメールアドレスを持たないため）、操作した人と対象の利用者（`actorUserId`・`targetUserId`・`targetInvitationId`）が載ります。記録に失敗しても、パスワードの変更の応答（204・400）は変わりません。
- 1件の書き込みに 200 ミリ秒を超えてかかった場合は、WARN（`監査イベントの記録に時間がかかりました`）が1回出ます。1日に数件以上出るときは、記録の量と内部DBの待ちを確かめてください。
- 環境変数は増えません。

### 既知の制約（同時の要求と接続プール）

- ログイン（成功・失敗）とログアウトでは、確定の後に同じスレッドで監査を記録します。このとき、業務で使った内部DBの接続（1本目）を持ったまま、記録のために2本目の接続を借ります。
- 利用者の管理の5つの操作も同じく、確定の後に1件に2本の接続を使います。管理の操作とログインの失敗が同時に重なると、同時の数が上限（既定 30）を超えて、2本目の接続の待ちが時間切れになりえます（そのときは監査の記録が欠け、ERROR が出ます。操作の応答と状態は変わりません）。
- そのため、接続プールの上限（`MASTERSMITH_DB_MAXIMUM_POOL_SIZE`、既定 30）以上の同時の要求が来ると、2本目の接続の待ちが時間切れ（5 秒）になり、監査の記録が欠けることがあります。ログイン自体は成功し、アプリのログに ERROR（`監査イベントの記録に失敗しました`）が出ます。上限を 10 にすると、同時 10 件のログインでこの欠けが起きます。
- Tomcat の同時処理のスレッドの上限は設定しておらず、既定の 200 です。プールの上限より多い要求が同時に来ることがあります。
- 同時の数を増やす場合は、`MASTERSMITH_DB_MAXIMUM_POOL_SIZE` で上限を上げます。その際は、コンテナのメモリの上限の内側で動くこととヘルスチェックへの影響を、起動と負荷の試験で確かめてください。

### 監査ログの確かめ方

アプリを止めてボリュームを複写し、**複写したファイル**を読み取りで開きます（H2 のコンソールは使いません）。

```bash
docker compose stop app
mkdir -p ~/.mastersmith-backup && chmod 700 ~/.mastersmith-backup
docker run --rm -v mastersmith_mastersmith-data:/data -v "$HOME/.mastersmith-backup":/backup eclipse-temurin:25.0.5_8-jre-noble@sha256:a98128a57de944a72bd31fbc79b898edd5a5864d3652071d5bbd2160ab7d7dce \
  tar czf /backup/mastersmith-data-$(date +%Y%m%d%H%M).tgz -C /data .
docker compose start app
# 複写を展開し、H2 の道具で読み取り（ACCESS_MODE_DATA=r）で開いて audit_events を読む
```

誤ったトークンの登録の完了の増え（残る危険 R1。回数の制限を置いていないため）は、開いた複写で次の形で数えます（時間の幅は見たい期間に合わせる）。とくに `INVITATION_NOT_FOUND` が1つの送り元から多いときは総当たりの疑いとします。

```sql
SELECT failure_reason, source_ip, COUNT(*) AS failures
  FROM audit_events
 WHERE event_type = 'REGISTRATION_FAILED'
   AND occurred_at >= TIMESTAMP WITH TIME ZONE '2026-01-01 00:00:00+00:00'
 GROUP BY failure_reason, source_ip
 ORDER BY failures DESC;
```

複写したファイルにもメールアドレスと接続元IPが残ります。実行の利用者と管理する者だけが読める場所に置いてください。

### 消してはいけない操作

- **ボリュームを消す操作（`docker compose down -v` など）を、バックアップの前に行わないでください。** 監査ログも一緒に消えます。
- 版を替える前は、`docs/internal-db.md` の「内部DBのバックアップと戻し方」の手順でボリュームを複写します。この手順で監査ログも一緒に守られます。
- OS の権限を持つ者によるファイルの直接の書き換えは、アプリでは検知できません（受け入れている危険です）。
