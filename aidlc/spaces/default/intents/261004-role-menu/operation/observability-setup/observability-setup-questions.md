# Observability Setup — Questions

Intent 261004-role-menu（scope classic、深さ Standard）の Observability Setup の質問です。配備先が決まるまでの手元の監視は、`grafana/otel-lgtm` を compose の profile `monitoring` で見たいときだけ起動します。ダッシュボードと警報の決まりは、ファイルでリポジトリに置きます（`aidlc/spaces/default/memory/project.md` の Deployment）。

この段で確かめるとされたことは次のとおりです。

- Build and Test の `Unverified` のうち、持ち主が observability-setup の2件（`construction/build-and-test/build-and-test-summary.md`）
  - U3-NFR5.4（group）: 「既存の決まり（5xx の率・p95）が `uri` のラベルでグループの API にも効く。新しい警報とダッシュボードは足さない」
  - U4-NFR5.4（role）: 「新しい警報は足さない。既存の『5xx の割合の増加』と『コネクションプールの待ち』は、role の API と作業ロールの切り替えにも `uri` によらず効く」
- 基盤の設計の `monitoring-design.md`（group・role・navigation の 5節・7節）からの引き継ぎ
  - ダッシュボードに区画を1つ足し、式を起動して確かめる（group の Q1: A）
  - 式の名前の `_seconds_` と `_milliseconds_` の食い違いを確かめる

## 1. 今の監視の設定がどこまで効いているか（読み取りだけで洗い出した。2026-10-15）

| 対象 | 今の設定で効いているもの | 効いていないもの・気になる点 |
|---|---|---|
| 新しい API の数・時間（group 7本、role の管理・権限・割り当て・受け渡し、`/api/me/work-role`・`/api/me/permissions/…`、`/api/me/navigation`・`/api/me/table-access`） | 全体のパネル（要求の数・5xx の割合・ERROR のログ・403 の件数）に数として入る。`http.server.requests` には 100〜5000 ms のバケットがある（`backend/src/main/resources/application.yaml`、`slo`） | API ごとのパネルが無い。設計で決めた区画「ロール・グループ・メニュー（Intent 261004-role-menu）」はまだ書かれていない（`docker/monitoring/` の最後の変更は 2026-09-30 の `27be598`）。p95 の警報3件（`ms-login-p95`・`ms-refresh-p95`・`ms-check-p95`）は `uri` を1つの道に固定しているため、新しい API を拾わない（設計の差として承認済み） |
| 5xx・ERROR・監査の書き込み | `ms-5xx-ratio`・`ms-error-logs`・`ms-audit-fail`・`ms-audit-slow` は `uri` で絞らないため、新しい API の失敗も数に入る見込み | 新しい API の経路で 5xx・ERROR を本番のコードを変えずに起こす手は限られる |
| 403 | `ms-forbidden`（403 が 1 時間に 20 件を超える、`uri` で絞らない）は、新しい権限の 403 を数える | `/api/me/table-access` の 403 は「見られないテーブル」という業務の結果で、障害とは限らない（navigation の `monitoring-design.md` 2節）。画面の移動で数が増えやすい。警報の注記は「監査ログで利用者と接続元IPを確かめる」だが、置き場の 403 は監査に残らない（navigation の 4節） |
| 接続プール・ヒープ | `ms-pool-pending`・`ms-heap` は `uri` によらない。role・group の書き込みの2本使いと、権限の YAML の確かめ・適用の重なりも含む | `ms-pool-pending` の式の見直し（時間切れの累計）は、配備先が決まったときの申し送りのまま |
| ヒープが尽きたとき（`-XX:+ExitOnOutOfMemoryError`、Build and Test の Q2: A） | JVM が止まり、compose の `restart: "no"` のため起き直さない。指標が届かなくなるので `ms-app-absent`（`absent(process_uptime_milliseconds{…})`、`for: 5m`）が拾う見込み。原因は `docker inspect` の `OOMKilled`・`ExitCode` と `docker compose logs app` で見る（README の「既知の制約」の近く） | `ms-heap` は 85% が 5 分続いたときだけ鳴るため、急に尽きる場合は鳴らない見込み。JVM が止まるときの1行は logback を通らない標準エラーの出力のため、Loki に届かない見込み（確かめていない）。`ms-app-absent` は、前の Intent までどれも「アプリが動いているときに結果が空」だけを確かめ、鳴ることは確かめていない |
| 監査の新しい種類 | 内部DB の `audit_events` に残る。`AuditEventType` に 13 種類（`GROUP_CREATED`・`GROUP_RENAMED`・`GROUP_DELETED`・`GROUP_MEMBER_ADDED`・`GROUP_MEMBER_REMOVED`・`ROLE_CREATED`・`ROLE_RENAMED`・`ROLE_DELETED`・`ROLE_PERMISSION_CHANGED`・`ROLE_ASSIGNED`・`ROLE_UNASSIGNED`・`WORK_ROLE_SWITCHED`・`ROLE_TRANSFER_APPLIED`）、`AuditFailureReason` に 10 個（`GROUP_NOT_FOUND`・`GROUP_NAME_DUPLICATE`・`GROUP_IN_USE`・`ROLE_NOT_FOUND`・`ROLE_NAME_DUPLICATE`・`ROLE_IN_USE`・`PERMISSION_TARGET_NOT_IN_DSL`・`DSL_NOT_APPLIED`・`ROLE_NOT_ASSIGNED`・`ROLE_TRANSFER_STALE`）を足した | 監査は指標とログに出ないため、ダッシュボードでは見られない。数え方は `log-queries.md` の問い合わせ（内部DB の複写を読み取りで開く）になる |
| 排他の待ちの上限切れ | `ROLE_BUSY`・`GROUP_BUSY` の WARN（`RowLockFailures`、キー `lockKind`）が Loki に届く | パネル・警報は無い（設計どおり、ログの問い合わせで数える）。出すには同時の重なりが要る |
| トレース | 変えていない（既存のトレースIDが新しい API にも付く） | — |

読み取りだけで確かめた今の実行環境（`colima list`・`docker ps -a`・`docker volume ls`）:

- colima は CPU 4・メモリ 6GiB で動いている。
- 動いているコンテナ: `mastersmith-app-1`（`mastersmith:local`、healthy）・`mastersmith-mailpit-1`・`mastersmith-targetdb-postgres-1`
- `mastersmith-lgtm-1` は止まっている（11 日前に Exited）。ボリューム `mastersmith_mastersmith-monitoring` は残っている。
- メモリの上限の合計: 配備したアプリ 2g・Mailpit 256m・見本の対象DB 512m で約 2.8GB。lgtm（1536m）を足すと約 4.3GB、使い捨ての環境のアプリ（1g）を足すと約 5.3GB で、VM の 6GiB に近い。前の Intent（260930-user-admin）は同じ組み合わせで確かめを終えている。

## 2. 決まっていること・読み取りで確かめたこと（質問にしません）

| 決まっていること | 出典 |
|---|---|
| 独自の指標と新しい警報は足さない。新しい API の p95 の警報も足さない（判定は performance-validation の k6）。`ROLE_BUSY`・`GROUP_BUSY`・業務の 409・404 は警報にしない | group・role・navigation の `monitoring-design.md` 2節（group の Q1: A） |
| ダッシュボードに区画「ロール・グループ・メニュー（Intent 261004-role-menu）」を1つ足す。中身は API ごとの要求の数（1 時間）・p95・5xx の件数（1 時間）の3パネルで、形は既存の「招待と登録」の区画と同じ。`uri` の正規表現に group・role・navigation の道を名指しで足し、`/api/admin/users/{userId}` の下の利用者の管理の API を含めない | group・role・navigation の `monitoring-design.md` 5節 |
| p95 のパネルは、アプリの指標 `http_server_requests_…_bucket` で作る（既存の招待の区画のトレースの値 `traces_spanmetrics_latency` とは元が違うことを、パネルの説明に書く）。目安の線は 1000 ms。権限の YAML の確かめ・適用（15 秒・30 秒）はバケットの上限 5000 ms を超えるため、このパネルで判定しないと説明に書く | group・role の `monitoring-design.md` 5節 |
| 式の名前（`_milliseconds_` か `_seconds_` か）・ラベル・`uri` の値・`le` のバケットは、書く前に起動して確かめる。式を書いた後に、すべての式を流す。p95 の式は、先に `+Inf` 以外の `le` があることを確かめる。NaN を「要求が無いため」と決めつけない | `project.md` の Corrections・Deployment の学び、navigation の `monitoring-design.md` 7節 |
| 数を確かめる要求は、指標の送信の周期（`management.otlp.metrics.export.step` の 60s）を複数またいでくり返し送る | `project.md` の Deployment の学び |
| 確かめのために送る要求が監査に残る・データを変えるときは、送る前に依頼者に伝える | `project.md` の Corrections |
| SLO は緩めない。手元の監視を常に動かしていない間は、手元の監視の p95 の判定を `Unverified` とし、配備の直後・監視の確かめ・負荷の試験・振り返りの時点の値を基準の値として並べる。k6 の判定は performance-validation | `project.md` の Deployment の学び、各 `monitoring-design.md` 3節 |
| 画面（U6 role-admin-ui・U7 app-frame-ui）の側の監視は置かない。画面の時間は E2E の測りで記録する | role-admin-ui・app-frame-ui の `monitoring-design.md` 1節 |
| ログの収集・トレースの設定は変えない。外部エクスポートは既定で無効のまま | 各 `monitoring-design.md` 4節、team.md の Deployment |
| 配備したアプリの外部エクスポートは有効にしない（作り直さない）。基準の値は、使い捨ての環境の値と配備の記録（`operation/deployment-execution/health-check-report.md`）を並べる | 前の Intent（260930-user-admin）の同じ段の Q4: A。今回も配備したアプリを止めない理由は同じ |
| 警報の通知の先（contact point）は置かない。手元の Grafana の警報の一覧で見る | `project.md` の Deployment |
| `ms-pool-pending` の式の見直しは、配備先が決まったときの申し送りのまま `alarms.md` に記録する | group・role の `monitoring-design.md` 2節 |
| `ROLE_BUSY`・`GROUP_BUSY` の WARN が続くときの見方と、置き場の 403 の見方を README の「警報と対応の手順」に足すかは、incident-response の段で決める。この段は見るもの（区画とログの問い合わせ）だけを書く | group・role・navigation の `monitoring-design.md` 6節 |
| 確かめの後は、使い捨ての環境を消し、lgtm を元の止めた状態に戻す | 前の Intent の同じ段 |

## Q1. 名前と数を確かめるために要求を送る環境

区画の式を書くには、新しい API の `uri` の値・`le`・式の名前を、実際に要求を送って確かめる必要があります。group・role の操作は監査に残り、ロール・グループ・割り当てを作ります。メニューを読むには、版 2 の DSL・ロール・作業ロールの用意が要ります。`perf/README.md` の業務のメニューの節では、DSL の適用は対象DB に届かなくても照合の警告のまま通ったと記録されています。そのため、使い捨ての環境は対象DB なしで起動できる見込みです。

A. 使い捨ての環境（`docker/perf/compose.yaml`）を起動します。イメージは配備と同じ `mastersmith:local`、メモリの上限 1g、対象DB と Mailpit の profile は付けず、仮の署名鍵と仮の管理者を使います。外部エクスポートはこのアプリにだけ有効にし、lgtm を使い捨ての環境の網に別名 `lgtm` でつなぎます。配備したアプリは止めず、確かめの要求も送りません。終わったら使い捨ての環境を消します（推奨）
B. 配備したアプリに向けて、依頼者が画面からロール・グループ・割り当て・作業ロールの切り替え・メニューの表示を行います。配備したアプリの外部エクスポートを確かめの間だけ有効にします（作り直し 2 回）。監査とデータに確かめの操作が残ります
C. この段では起動しません。区画の式は設計の形のまま書き、名前と値の確かめは performance-validation の k6 の間に lgtm を動かして行います（持ち主を移す）。この段の判定は `Unverified` にします
X. Other (please specify)

推奨の理由: 前の Intent の同じ段（Q1: A）と `project.md` の「本物のデータと監査ログを汚さない」に合います。また、「書く前に起動して名前を確かめる」の学びを、この段の中で守れます。メモリは約 5.3GB で、前の Intent と同じ組み合わせです。

[Answer]: A

## Q2. 送る要求の範囲（監査の行と WARN の確かめを含む）

Q1 で A を選んだ場合、使い捨ての環境に送る要求の範囲を決めます。監査の新しい種類 13 と理由 10 は、使い捨ての環境の内部DB の複写を読み取りで開いて数えられます。`ROLE_BUSY`・`GROUP_BUSY` の WARN を出すには、同じ行への操作を待ち合わせで重ねる必要があります。

A. 区画に入る道のすべてに、送信の周期をまたいで 3 分以上くり返し要求を送ります。中身は次のとおりです。
   - 成功
   - 代表の失敗: 要る権限だけを欠く利用者からの 403、存在しない ID の 404、名前の重なりの 409、置き場の 403
   - 権限の YAML の書き出し・確かめ・適用を各1回（小さな YAML）

   監査の行は、種類ごとに送った要求から見込んだ件数と比べます。理由の行は、作れるものだけを確かめます。`ROLE_BUSY`・`GROUP_BUSY` の WARN は問い合わせの式だけを書き、Loki に流して空でエラーにならないことを確かめます。出ることの確かめは performance-validation の書き込みの場面に任せます（推奨）
B. A に加えて、この段で同じロール・グループへの書き込みを並べて送り、`ROLE_BUSY`・`GROUP_BUSY` の WARN を実際に出して、問い合わせの式で数えます。重なりは確実には作れず、出なければ performance-validation に回します
C. 読み取りの GET だけを送り、`uri` と `le` を確かめます。監査の行と理由は、Build and Test の結合テスト（`RoleAuditIT` など）の結果を正とします
X. Other (please specify)

推奨の理由: 区画の3パネル（数・p95・5xx）と監査の数え方の問い合わせを、すべて実際の値で確かめられます。重なりを作る確かめは、同じ書き込みの場面を流す performance-validation にまとめた方が確実です。

[Answer]: A

## Q3. Unverified 2件（U3-NFR5.4・U4-NFR5.4）の確かめ方と判定

U3-NFR5.4 の「p95 の決まりが `uri` でグループの API にも効く」は、事実と食い違います。既存の p95 の警報は `uri` を1つの道に固定しているためで、基盤の設計で差として承認済みです（区画を足し、警報は足さない）。どちらの件も、5xx の割合とプールの待ちは `uri` で絞らない式のため、新しい API の要求が数に入る見込みです。ただし、新しい API の経路で 5xx を本番のコードを変えずに起こす手はありません。プールの待ちは、performance-validation の上限を下げた場面（`groupPoolLimit`・`rolePoolLimit`）で起きうります。

A. この段では、式の評価で新しい API の要求が `ms-5xx-ratio` の分母と `http_server_requests` の系列に入ることを確かめます。判定は次のとおりです。
   - U3-NFR5.4: 5xx の部分を Met、p95 の部分を設計の差（区画で見る形に置き換え）として記録します。
   - U4-NFR5.4: Met とします。

   `ms-pool-pending` が鳴ることは、performance-validation の `groupPoolLimit`・`rolePoolLimit` の間に lgtm を動かして確かめます（持ち主を移す）。`ms-5xx-ratio` が鳴ることは `Unverified` として `alarms.md` に書きます（推奨）
B. A に加えて、しきい値を下げた警報の決まりの写しを lgtm に一時的に読み込ませ、`ms-5xx-ratio`・`ms-pool-pending` が鳴る仕組みをこの段で確かめます。終わったら元のファイルで読み込み直します。確かめられるのは鳴る仕組みだけで、しきい値そのもので鳴ることは確かめられません
C. 式の評価だけを行い、2件とも鳴ることまで含めて `Unverified` のまま feedback-optimization に引き継ぎます
X. Other (please specify)

推奨の理由: 前の Intent の同じ段（Q2: A）と同じ線引きです。実際の失敗で鳴らせるものは負荷の段でまとめて確かめます。目標の文言の食い違いは、承認済みの設計の差として記録し、書き換えません。

[Answer]: A

## Q4. `ms-forbidden`（403 の増加）と置き場の 403

`ms-forbidden` は、`uri` で絞らずに 403 を 1 時間に 20 件を超えたら鳴ります。この Intent で次の 403 が加わりました。

- 権限の 403（管理の API と作業ロール）
- `/api/me/table-access` の 403: 権限の無いテーブルを開いたときの業務の結果で、監査に残らない

依頼者1人の今の使い方では、20 件に届くことはまれな見込みです。ただ、警報の注記（「監査ログで利用者と接続元IPを確かめる」）は、置き場の 403 には当たりません。

A. 式は変えません。`alarms.md` に、置き場の 403 も数に入ること、置き場の 403 は監査に残らずログの問い合わせ（`uri` ごとの 403 の数）で見分けることを書きます。式を分けるかは、配備先が決まり利用者が増えたときの申し送りにします（推奨）
B. `ms-forbidden` の式から `uri="/api/me/table-access"` を外します。既存の警報の変更で設計（「既存のとおり」）との差になるため、`alarms.md` に差を書きます
C. B に加えて、置き場の 403 の数を区画にパネルとして足します（警報にはしません）
X. Other (please specify)

推奨の理由: 設計（警報は変えない・足さない）のままで、今の使い方では誤って鳴る見込みが小さいためです。見分け方を記録しておけば、鳴ったときに迷いません。

[Answer]: A

## Q5. ヒープが尽きて JVM が止まったとき（`ExitOnOutOfMemoryError`）の拾い方

この Intent で、ヒープが尽きたら JVM を止める設定を入れました。止まったアプリは起き直しません（`restart: "no"`）。今の警報で拾うのは `ms-app-absent` だけの見込みですが、鳴ることは前の Intent までどれも確かめていません。指標が届かなくなってから、Prometheus の見に行く幅（5 分）と `for: 5m` で、鳴るまで約 10 分と見込みます（未確かめ）。JVM が止まるときの1行は Loki に届かない見込みです。

A. 新しい警報は足しません。Q1 の使い捨ての環境の確かめの最後に、使い捨てのアプリを止めます。そのうえで `ms-app-absent` が鳴ることと、鳴るまでの時間を確かめます（lgtm は動かしたまま、約 10〜15 分待つ）。止まったときの見方（`docker inspect` の `OOMKilled`・`ExitCode`、`docker compose logs app`）を `log-queries.md` に書きます。尽きることそのものは起こしません（推奨）
B. A に加えて、使い捨てのアプリのヒープを `MASTERSMITH_JAVA_OPTIONS` で小さくし、大きな権限の YAML の確かめで実際にヒープを尽かせます。止まったときの `ms-heap` の動き、標準エラーの1行が Loki に届かないこと、`ms-app-absent` が鳴ることをまとめて確かめます（負荷と時間が増えます）
C. 確かめは行いません。見込みと見方だけを `alarms.md`・`log-queries.md` に書き、`ms-app-absent` が鳴ることは `Unverified` として feedback-optimization に引き継ぎます
X. Other (please specify)

推奨の理由: ヒープが尽きたときに唯一頼れる警報が鳴ることを、初めて実際に確かめられます。使い捨てのアプリを止めるだけで済み、配備したアプリに触れません。ヒープを尽かせる確かめは、Build and Test の Q1 の測り（5 MiB で OOM なし）と performance-validation の `roleTransferLarge` に任せます。

[Answer]: A

## Q6. ダッシュボードの「仮の目標の一覧」

ダッシュボードの文字のパネル「仮の目標の一覧」には、ログイン・トークンの更新・確認用 API の p95 と稼働だけが載っています。この Intent の承認済みの SLO は、新しい API の p95 1 秒と、権限の YAML の確かめ 15 秒・適用 30 秒です。どちらの判定も k6 で行います。設計は「足すのは区画1つ」で、この一覧には触れていません。

A. 一覧に「ロール・グループ・メニューの API の 95 パーセンタイル 1 秒以内（判定は k6）」と「権限の YAML の確かめ 15 秒・適用 30 秒（k6 で判定。パネルでは見ない）」の2行を足します。設計との差を `dashboards.md` に書きます（推奨）
B. 一覧は変えません（設計どおり）。目標は `slo-config.md` にだけ書きます
X. Other (please specify)

推奨の理由: 一覧は、ダッシュボードを開いた人が目標を知る唯一の場所です。区画の目安の線 1000 ms の出どころが分かるようになります。警報もしきい値も変えない、記録だけの差です。

[Answer]: A

## Consolidated Summary Confirmation

答えのまとめ: Q1 A（lgtm を起動し、使い捨てのアプリ（1g）だけを送らせて式を実機で確かめる。配備したアプリと監査には触れない）、Q2 A（区画のすべての道に成功と代表の失敗 403・404・409、権限の YAML の書き出し・確かめ・適用を各 1 回。監査は複写で数える。BUSY の WARN は式だけ）、Q3 A（式の評価で新しい API の要求が数に入ることを確かめる。U3 は 5xx を Met・p95 を設計の差、U4 は Met。接続の待ちの警報が鳴ることは performance-validation、5xx の警報が鳴ることは Unverified）、Q4 A（ms-forbidden の式は変えず、置き場の 403 が入ることと見分け方を alarms.md に書く）、Q5 A（確かめの最後に使い捨てのアプリを止め、ms-app-absent が鳴ることと鳴るまでの時間を測る）、Q6 A（仮の目標の一覧に新しい API の p95 1 秒と権限の YAML の確かめ 15 秒・適用 30 秒の 2 行を足し、設計との差を dashboards.md に記録）。この内容で進めてよいか。

- Looks correct
- Request changes

[Answer]: Looks correct
