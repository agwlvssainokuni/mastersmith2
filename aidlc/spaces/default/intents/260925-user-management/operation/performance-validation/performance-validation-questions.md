# Performance Validation — 質問

Intent `260925-user-management` の性能の目標を、負荷をかけて確かめるための質問です。

## 前提（読み取りだけで調べた結果。2026-09-29）

- **この段が持ち主の未検証の目標は 21 件**です（`construction/build-and-test/build-and-test-summary.md` の Unverified のうち performance-validation の分）。
  - 同時 10 件の p95:
    - U2 の NFR6.1〜NFR6.4（プリファレンスの取得・保存、パスワードの変更 2 秒、誤り）
    - U2 の NFR6.5（ログイン・更新の応答を広げた後も 1 秒）
    - U3 の NFR6.1（招待・送り直し 5 秒）、NFR6.3〜NFR6.5（一覧・取り消し・リンクの確かめ・登録の完了・誤り 1 秒）
    - U8 の NFR6.1（`GET /api/appearance` 300 ミリ秒）
    - U5・U6・U7 の前提（同じ API の値）
  - 受け手が応答しないときの1件の時間（U3 の NFR6.2、5 秒以内）。
  - 接続プールの余裕（U2 の NFR5.2・NFR5.3、U3 の NFR5.2〜NFR5.4。待ちの時間切れ 0、待ちの最大が 5 秒より十分小さい、成功と監査の件数が一致）。
  - 規模（U2・U3 の NFR6.7。想定の規模を1台で処理）。
- **台本は用意済み**: `perf/k6/scenarios.js` の場面 15 本（U2 の6本・U3 の8本・U8 の `appearance`）と、既存の `loginSuccess`・`refresh`。
  - 手順は `perf/README.md` の「利用者の設定と招待の場面」です。
  - Build and Test では `k6 inspect` で読み込めることだけを確かめました。実際に流すのはこの段が初めてです。
  - Mailpit の API からトークンを取り出す流れも、この段で初めて動かします。
- **手元の環境**:
  - colima の VM は CPU 4・メモリ 6GiB です。
  - 動いているのは、配備したアプリ（`mastersmith:local`、`83b572b`、上限 2g）と見本の PostgreSQL（512MiB）です。手元の監視と Mailpit は止まっています。
  - 使い捨ての環境はアプリ 2g・Mailpit 256MB・k6 です。
- **負荷の試験は、配備した環境とは別の使い捨ての環境で行います**（仮の署名鍵・仮の利用者、終わったら消す。project.md の Testing Posture）。
  - `caffeinate -i` を付けて流します。
  - 片付けは、確かめの結果（監査の件数など）を見てから行います。
- **p95 の判定は k6 の `http_req_duration` の値で行います。** Observability Setup で分かったとおり、アプリの指標 `http_server_requests` にはバケットが無く、p95 を出せません。

## Q1. 試験の間の配備したアプリ

A. k6 を流す間は配備したアプリを止め（`docker compose stop app`、見本の PostgreSQL は動かしたまま）、終わったら `docker compose start app` で起動し直して healthy を確かめる。CPU の取り合いで値がぶれないため（`perf/README.md` の手順 0、前の Intent と同じ）。止まっている時間は 40〜60 分ほど
B. 配備したアプリは止めない（待機中のため取り合いは小さいが、測った値に影響が混ざりうることを記録する）
X. Other (please specify)

[Answer]: A

## Q2. 負荷の時間

A. `perf/README.md` のとおり、時間で終わる場面は同時 10・60 秒ずつ流す（回数で終わる取り消しと登録の完了は Q3 の回数）
B. 強めに、時間で終わる場面を同時 10・120 秒ずつ流す（全体でおよそ倍の時間）
X. Other (please specify)

[Answer]: A

## Q3. 取り消しと登録の完了の回数

Build and Test で、この2つの場面は既定 100 回（招待も 100 件用意する）にしました。依頼者の決定の文言（招待を VU の数だけ出す）とは違うため、承認の場で確かめることになっていました。トークンは1回しか使えないため、10 件では p95 の意味が薄くなります。

A. 100 回で流す（setup で招待を 100 件出し、Mailpit から 100 通のリンクを取り出す。setup に数分かかる）
B. VU の数（10 回）で流す（p95 は 10 件の中の値になる）
X. Other (please specify)

[Answer]: A

## Q4. 接続プールの余裕の確かめ

A. 使い捨てのアプリにだけ `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics` を渡し、`/actuator/metrics` の hikaricp の待ちの時間切れの累計と、借りるまでの待ちの最大で判断する（project.md の Testing Posture の決まり）。あわせて、試験の後に監査の件数と成功の件数を突き合わせる。配備したアプリの公開の範囲は変えない
B. 監査の件数の突き合わせだけで判断し、接続の待ちは Unverified のまま残す
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

回答をまとめると、この段では次のとおり進めます。

1. **環境**：使い捨ての環境（プロジェクト名 `mastersmith-perf`、イメージは配備と同じ `mastersmith:local`、上限 2g）を、profile `mail` の Mailpit ごと起動します。
   - 仮の署名鍵・仮の管理者と、試験用の利用者 21 名（`perf-user01`〜`11`・`perf-pw01`〜`10`）を使います。
   - 本物の配備の環境のデータと監査ログには触れません。
2. **配備したアプリ（Q1: A）**：k6 を流す間は止めます（見本の PostgreSQL は動かしたまま）。終わったら `docker compose start app` で起動し直し、healthy を確かめます。
   - 止める直前と起動し直す前に、お知らせします。
3. **流す場面（Q2: A・Q3: A）**：
   - 時間で終わる場面は、同時 10・60 秒ずつです。
     - U2: `preferencesGet`・`preferencesSave`・`preferencesInvalid`・`passwordChange`・`passwordMismatch`・`passwordInvalid`
     - U3: `invite`・`invitationResend`・`invitationList`・`registrationVerify`・`registrationInvalid`・`registrationRejected`
     - U8: `appearance`
     - U2 の NFR6.5 のため、既存の `loginSuccess`・`refresh` も流します。
   - `invitationCancel`・`registrationComplete` は、それぞれ 100 回（招待も 100 件）です。
   - 閾値は出典の値のままで、緩めません。場面ごとに p95 と `checks` の率 1 で判定します。
4. **受け手が応答しないときの1件の時間（U3 の NFR6.2）**：Mailpit を止めた状態で、招待を1件送り、応答までの時間と送信の結果（FAILED）を記録します。
5. **接続プール（Q4: A）**：
   - 使い捨てのアプリにだけ `/actuator/metrics` を公開し、流す前後の hikaricp の待ちの時間切れの累計と、借りるまでの待ちの最大を読みます。
   - 試験の後、使い捨てのアプリを止めて内部DB を読み取りで開き、監査の件数（`PASSWORD_CHANGED`・`INVITATION_ISSUED`・`INVITATION_RESENT`・`INVITATION_CANCELLED`・`REGISTRATION_COMPLETED` などの成功と失敗）を、k6 の成功の件数と突き合わせます。
   - 数え終わるまで、環境を消しません。
6. **秘密の確かめ**：試験の後、使い捨てのアプリのログと k6 の結果に、招待のリンク（`/register#token=`）とメールアドレス（`@example.com`）が 0 件であることを件数で確かめます。
7. **判定**：Met・Not Met・Unverified のどれかにします。
   - 目標に届かないときは、目標を緩めず Not Met とし、原因をログと状態で確かめてから記録し、承認の場で相談します。
   - k6・Mailpit がアプリと同じ VM の CPU を分け合うことは、結果に明記します。
8. **成果物**：
   - `load-test-plan.md`（場面・条件・手順）
   - `test-results.md`（実測）
   - `nfr-validation-matrix.md`（21 件の判定）
   - 結果の生データは `build/perf-results/` の下（リポジトリの管理外）に置きます。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
