# 負荷の試験の計画（load-test-plan）

Intent `260925-user-management` の性能の目標のうち、Build and Test から引き継いだ 21 件を確かめる計画です。決定は `performance-validation-questions.md`（Q1〜Q4、確認済みの要約）です。

## 1. 対象の目標

| 目標 | 内容（閾値は出典のまま、緩めない） | 場面 |
|---|---|---|
| U2-NFR6.1 | `GET /api/me/preferences` の同時 10 件で p95 1 秒以内 | `preferencesGet` |
| U2-NFR6.2 | `PUT /api/me/preferences` の同時 10 件で p95 1 秒以内（成功・入力の誤り） | `preferencesSave`・`preferencesInvalid` |
| U2-NFR6.3 | パスワードの変更の成功の同時 10 件で p95 2 秒以内 | `passwordChange` |
| U2-NFR6.4 | 今のパスワードの誤り・入力の誤りの同時 10 件で p95 1 秒以内 | `passwordMismatch`・`passwordInvalid` |
| U2-NFR6.5 | ログインと更新の応答を広げた後も p95 1 秒以内 | `loginSuccess`・`refresh` |
| U2-NFR5.2・U3-NFR5.3 | 同時 10 件で hikaricp の待ちの時間切れ 0、待ちの最大が 5 秒より十分小さい、成功と監査の件数が一致 | 全場面の前後の `/actuator/metrics`、試験の後の監査の件数 |
| U2-NFR5.3・U3-NFR5.4 | 同時 10 件で接続は最大 20 本、上限 30 に収まる | 同上（待ちの時間切れ 0 と待ちの最大で判断） |
| U2-NFR6.7・U3-NFR6.7 | 想定の規模を1台で処理し、各 NFR6.x を満たす | 各場面の結果の総合 |
| U3-NFR6.1 | 招待・送り直しは SMTP の送信を含めて同時 10 件で p95 5 秒以内 | `invite`・`invitationResend` |
| U3-NFR6.2 | 受け手が応答しない・拒むときも FAILED で応答し、1件で 5 秒以内 | Mailpit を止めた状態で招待を1件（4節） |
| U3-NFR6.3 | 一覧（1ページ目と最後のページ）・取り消し・リンクの確かめの同時 10 件で p95 1 秒以内 | `invitationList`・`invitationCancel`・`registrationVerify` |
| U3-NFR6.4 | 登録の完了の成功の同時 10 件で p95 1 秒以内 | `registrationComplete` |
| U3-NFR6.5 | 入力の誤りとリンクの拒否の同時 10 件で p95 1 秒以内 | `registrationInvalid`・`registrationRejected` |
| U3-NFR5.2 | 登録の完了の接続の待ち | `registrationComplete` の前後の hikaricp |
| U8-NFR6.1 | `GET /api/appearance` の同時 10 件で p95 300 ミリ秒以内 | `appearance` |
| U5・U6・U7 の前提 | 上と同じ API の値 | 上の場面の結果を写す |

## 2. 環境

| 項目 | 値 |
|---|---|
| 場所 | colima の VM（CPU 4・メモリ 6GiB）。配備した環境とは別の使い捨ての環境（`docker/perf/compose.yaml`、プロジェクト名 `mastersmith-perf`、profile `mail` の Mailpit） |
| イメージ | `mastersmith:local`（`sha256:9e5243a30b77…`、配備したものと同じ `83b572b`） |
| 上限 | CPU 4・メモリ 2g（配備と同じ） |
| データ | 空の内部DB に、仮の管理者 1 名と試験用の利用者 21 名（`perf-user01`〜`11`・`perf-pw01`〜`10`）。仮の署名鍵。一時の環境ファイルはホームの下（権限 700）に置き、値は表示しない |
| 使い捨てのアプリの設定の差 | `SPRING_MAIL_HOST=mailpit`・`MASTERSMITH_WEB_BASE_URL=http://app:8080`・`MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics`（Q4: A。配備したアプリの公開の範囲は変えない） |
| 配備したアプリ | k6 の間は止める（Q1: A）。見本の PostgreSQL は動かしたまま。手元の監視は止めたまま |
| 負荷の元 | k6 `grafana/k6:2.3.0` を同じネットワークのコンテナで流す（`caffeinate -i`）。k6・Mailpit はアプリと同じ VM の CPU を分け合う |

## 3. 場面の流し方（Q2: A・Q3: A）

- 順番:
  1. `appearance`
  2. U2 の6本（`preferencesGet`・`preferencesSave`・`preferencesInvalid`・`passwordChange`・`passwordMismatch`・`passwordInvalid`）
  3. `loginSuccess`・`refresh`
  4. U3 の8本（`invite`・`invitationResend`・`invitationList`・`registrationVerify`・`registrationInvalid`・`registrationRejected`・`invitationCancel`・`registrationComplete`）
- 時間で終わる場面は、同時 10・60 秒ずつです。`invitationCancel`・`registrationComplete` は 100 回ずつです（setup で招待を 100 件出し、Mailpit のメールからリンクを取り出す）。
- 内部DB のファイルは場面を重ねるたびに膨らみます。軽い API の性能は、膨らんだ状態のまま測ります（project.md の Testing Posture）。
- 各場面の後に、`/actuator/metrics` の `hikaricp.connections.timeout`（累計）・`hikaricp.connections.acquire`（最大）・`hikaricp.connections.pending` を読みます。
- 判定は、場面ごとの k6 の閾値（`http_req_duration{name:…}` の p95 と `checks` の率 1）です。アプリの指標 `http_server_requests` は p95 を出せないため使いません（Observability Setup の `dashboards.md` 2節）。

## 4. 受け手が応答しないときの1件（U3-NFR6.2）

- k6 の後に Mailpit を止め、管理者で招待を1件送ります。
- 応答の状態コード・`sendResult`・応答までの時間を記録します（1件を測る。同時の数は 1）。

## 5. 試験の後の確かめ

- **監査の件数**：使い捨てのアプリを止め、内部DB を読み取り（`ACCESS_MODE_DATA=r`）で開きます。`audit_events` を `event_type`・`result` ごとに数え、k6 の成功の件数（`checks` と状態コードごとの件数）と突き合わせます。
- **秘密**：使い捨てのアプリのログと k6 の結果に、`/register#token=`・`@example.com` が 0 件であることを件数で確かめます。
- **片付け**：確かめの結果を見てから、使い捨ての環境を消します（`down -v`、一時の環境ファイルの削除）。配備したアプリを `docker compose start app` で起動し直し、healthy を確かめます。

## 6. 判定の決まり

- Met: 閾値をすべて満たし、`checks` の率が 1。
- Not Met: 閾値を満たさない。目標を緩めず、環境を起動し直して再現させ、原因をログと状態で確かめてから記録し、承認の場で相談します（project.md の Testing Posture）。
- Unverified: この段で流せなかったもの（理由を書く）。

## Sources

- `operation/performance-validation/performance-validation-questions.md`（Q1〜Q4、確認済みの要約）
- `construction/build-and-test/build-and-test-summary.md`（Unverified の一覧）・`performance-test-instructions.md`
- `construction/u2-user-preferences/nfr-requirements/performance-requirements.md`・`reliability-requirements.md`・`scalability-requirements.md`、`construction/u2-user-preferences/nfr-design/performance-design.md`
- `construction/u3-invitation/nfr-requirements/performance-requirements.md`・`reliability-requirements.md`・`scalability-requirements.md`、`construction/u3-invitation/nfr-design/performance-design.md`
- `construction/u8-instance-appearance/nfr-requirements/performance-requirements.md`
- `operation/observability-setup/dashboards.md`
- `perf/k6/scenarios.js`・`perf/README.md`・`docker/perf/compose.yaml`

## Assumptions & Open Questions

None.
