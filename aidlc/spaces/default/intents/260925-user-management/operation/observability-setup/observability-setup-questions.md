# Observability Setup — Questions

配備先が決まるまでの手元の監視は、`grafana/otel-lgtm` を compose の profile `monitoring` で見たいときだけ起動し、ダッシュボードと警報の決まりはファイルでリポジトリに置きます（project.md の Deployment）。

今の監視の設定にあるもの：
- ダッシュボード `docker/monitoring/dashboards/mastersmith-overview.json`：パネル 26 個
- 警報の決まり `docker/monitoring/provisioning/alerting/mastersmith.yaml`：`ms-app-absent`・`ms-5xx-ratio`・`ms-login-p95`・`ms-origin`・`ms-cleanup-fail` など

各単位の設計は、「新しい指標・警報・パネルは足さない」を基本にしています。そのうえで、この段で決めるとされた点があります。

次のことは、前の段の決定と決まりで決まっているため、質問にしません。

| 決まっていること | 出典 |
|---|---|
| lgtm の環境変数に `GF_USERS_VIEWERS_CAN_EDIT: "true"` を足し、Explore を使えるようにする。足した後に、Explore が出ること、ダッシュボードは画面で保存できないことを確かめる | 依頼者の決定（`operation/deployment-pipeline/memory.md`） |
| 警報やダッシュボードの式は、書く前に実際に起動して、指標・ラベル・ログの項目の名前を確かめる。書いた後に、すべての式を実行する。U2 の `_seconds_`／`_milliseconds_` の食い違い、送信の指標 `mastersmith.mail.send` の名前、今回の新しいログのキー（`invitationId`・`failureKind` など）の Loki での届き方と伏せ字も、ここで確かめる | project.md の Corrections |
| `ms-origin` は、配備の `.env` にベース URL を入れたため、`http://127.0.0.1:8080` から開いたログインでも数が増える。警報は変えず、`alarms.md` に「`localhost` で開く」と注意を書く | U3 の `monitoring-design.md`（N4・M-D3） |
| 画面の時間（U4〜U7 の NFR6.x）と SLO（U8 の NFR9.3 ほか）の運用の中の判定は、配備先が決まるまで `Unverified` とする。基準の値を並べて、feedback-optimization に引き継ぐ | 各単位の `monitoring-design.md`、project.md の Deployment |
| 確かめのために送る要求が監査ログや内部DB に残るときは、送る前に依頼者に伝える | project.md の Corrections |

## Q1. ダッシュボードに足すパネル（複数選べます）

U3 の基盤の設計は、招待・登録のパネルと U1 の送信のパネルをまとめて、この段で決めるとしました（U3 の R-02。U5・U6 もこれに従う）。U8 は、`GET /api/appearance` の p95 のパネルを候補としています。

A. 招待・登録の API の行（6本の API の要求の数・p95・5xx）と、メールの送信の行（`mastersmith.mail.send` の送信の数・失敗の種類ごとの数・時間）
B. 見た目の設定の API（`GET /api/appearance`）の p95 のパネル
C. どれも足さない（今のダッシュボードの既存の API の全体のパネルで見る）
X. Other (please specify)

[Answer]: A

## Q2. 警報の決まりに足すもの（複数選べます）

A. 招待の定期の削除の失敗（N3）。承認の場の N3 では警報を足すとしたが、U3 の基盤の設計は「足さない、配備先が決まったときに見直す」としていて、食い違っている。足すときは、今の `ms-cleanup-fail`（トークンの削除の失敗）と同じ形で、Loki の本文「保存期間を過ぎた招待の削除に失敗しました」を数える
B. 登録の完了の API の 5xx・遅れ。U6 の設計は「この段で決める」、U3 の設計は「p95 の警報は足さない（目標は performance-validation の k6）」で、食い違っている
C. メールの送信の失敗の増加（`mastersmith.mail.send` の失敗の数）
D. どれも足さない（既存の `ms-5xx-ratio`・`ms-error-logs` などで拾う）
X. Other (please specify)

[Answer]: D

## Q3. 今のパスワード・招待のトークンの総当たりの見つけ方の問い合わせ（R1）の運用

U2・U3 の残る危険 R1 は受け入れ済みで、見つけ方の問い合わせが README の「監査ログの確かめ方」にあります。U3 の設計は「アプリを止めずに内部DB を読む方法」をこの段で書くとしていますが、今の README の手順は「アプリを止めて複写する」です。

A. 疑いがあるとき（`ms-lock`・`ms-rejected` の警報、ログインや登録の失敗の増加に気づいたとき）だけ、依頼者が README の今の手順（アプリを数秒止めて複写）で流す。定期には流さない
B. 週に1回など決まった頻度で、依頼者が README の今の手順で流す
C. アプリを止めずに読む方法（H2 の複写をアプリの中から取る道具など）を作る。コードの変更になるため、次の Intent で扱う
X. Other (please specify)

[Answer]: A

## Q4. ログの外部エクスポートの `.env` の2行（`MASTERSMITH_OBSERVABILITY_EXPORT_ENABLED`・`_ENDPOINT`）

README では「見終わったら消す（送り先が無い間は、送信の失敗の警告がログに出るため）」とあります。前の Intent も、確かめの間だけ有効にしました。

A. この段の確かめの間だけ有効にする。終わったら、lgtm を止めて2行を消し、アプリを起動し直す（前の Intent と同じ）
B. 置いたままにする（lgtm を常に動かす。監視のコンテナのメモリは 1.5GB）
X. Other (please specify)

[Answer]: A（ただし、2行は消さずにコメントアウト・アンコメントで切り替える）

## Q5. 名前を確かめるために送る要求

新しい API の指標のラベル（`uri`・`status`）とログのキーを確かめるには、実際に要求を送る必要があります。招待・送り直し・取り消し・登録の完了・パスワードの変更は監査に残り、登録の完了では利用者も残ります。

A. 配備したアプリに向けて、依頼者が画面から、招待・送り直し・取り消しと、プリファレンスの取得・保存を1回ずつ行う。登録の完了とパスワードの変更は行わず、その2本の API のラベルは確かめない（`Unverified` として記録する。配備の後の確かめのときは外部エクスポートを有効にしていなかったため、指標は届いていない）
B. A に加えて、登録の完了とパスワードの変更も行う（利用者がもう1人残る）
C. 配備したアプリには送らない。名前は、使い捨ての環境で起動して確かめる
X. Other (please specify)

[Answer]: C

## Consolidated Summary Confirmation

回答をまとめると、この段では次のとおり進めます。

- **パネル（Q1: A）**：ダッシュボードに次の2つの行を足します。見た目の設定の API のパネルは足しません。
  - 招待・登録の6本の API の行（要求の数・p95・5xx）
  - メールの送信の行（`mastersmith.mail.send` の送信の数・失敗の種類ごとの数・時間）
- **警報（Q2: D）**：新しい警報は足しません。既存の `ms-5xx-ratio`・`ms-error-logs` などで拾います。
  - N3（招待の定期の削除の失敗）と、登録の API の警報の食い違いは、「足さない」と決めたことを `alarms.md` に記録します。
- **R1 の問い合わせ（Q3: A）**：疑いがあるときだけ、依頼者が README の今の手順（アプリを数秒止めて複写）で流します。
  - 疑いのきっかけは、`ms-lock`・`ms-rejected` の警報や、失敗の増加です。
  - 定期には流しません。
  - 問い合わせの列の名前は、使い捨ての環境の内部DB で実際に流して確かめます。
- **外部エクスポート（Q4: A）**：
  - 配備した `.env` に2行（`MASTERSMITH_OBSERVABILITY_EXPORT_ENABLED=true`・`MASTERSMITH_OBSERVABILITY_EXPORT_ENDPOINT=http://lgtm:4318`）を置きます。
  - この段の確かめの間だけ有効にします。
  - 確かめが終わったら、2行を消さずにコメントアウトし、lgtm を止めて、アプリを起動し直します。
  - README の「見終わったら消す」の手順も、コメントアウト・アンコメントで切り替える形に直します。
- **名前の確かめ（Q5: C）**：
  - 配備したアプリには、確かめのための要求を送りません。
  - 使い捨ての環境（`docker/perf/compose.yaml`、仮の署名鍵・仮の利用者、profile `mail` の Mailpit）を起動し、lgtm に送らせます。
  - その環境に、招待・送り直し・取り消し・登録の完了・パスワードの変更・プリファレンスの取得と保存・見た目の設定を送ります。
  - そのうえで、指標の名前とラベル（`uri`・`status`、`_seconds_`／`_milliseconds_`、`mastersmith.mail.send` の名前とタグ）と、Loki に届くログのキーと伏せ字を確かめます。
  - 終わったら、使い捨ての環境を消します。
- **決まっていること**：
  - lgtm に `GF_USERS_VIEWERS_CAN_EDIT: "true"` を足し、Explore が出ることを確かめます。
  - 足したパネルの式は、書いた後にすべて実行して確かめます。
  - `ms-origin` の注意と、画面の時間・SLO を `Unverified` とする扱いを記録します。
- **変えるファイル**：
  - `compose.yaml`（lgtm の環境変数）
  - `docker/monitoring/dashboards/mastersmith-overview.json`（2つの行）
  - `README.md`（手元の監視の節）
  - 配備した `.env`（2行。表示せずに足し、最後はコメントアウト）
- **承認をいただく操作**：
  - 配備した `.env` を変えてアプリを作り直す操作と、lgtm の起動は、行う直前に承認をいただきます。
  - コミットの前にも、承認をいただきます。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
