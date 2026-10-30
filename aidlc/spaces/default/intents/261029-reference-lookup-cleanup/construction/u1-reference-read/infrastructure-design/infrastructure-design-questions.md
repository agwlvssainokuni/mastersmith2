# Infrastructure Design の質問 — U1 u1-reference-read（Intent 261029-reference-lookup-cleanup）

U1（参照の読み取り）の基盤の設計で、判断が分かれる点だけを尋ねる。各質問の最初の選択肢を推奨とし、理由を添える。

## 決まっていること（質問しません）

- 配備先は開発者の PC 上のコンテナのまま（`team.md` の Deployment、`project.md` の学び）。クラウドの基盤は作らない。今の colima の VM は CPU 4・メモリ 6GiB。
- 対象DB の読み取りの接続とプールは既存のもの（`MASTERSMITH_TARGET_DB_*`）を使い、新しい接続・アカウントは足さない。
- 性能の試験の読み取りのプールの上限は 5（NFR 要件）。k6 の場面は Build and Test で書く。
- 既存の警報 `ms-biz-target-db`（業務データの API の 503 を数える）は道の条件が `/api/tables/.*` のため、参照の API（`/api/tables/reference/rows` など）の 503 も数える。新しい警報は足さない前提で、Q2 で表示だけを尋ねる。
- `http.server.requests` のバケットに 3 秒を足すかは Observability Setup で決める（NFR 要件からの引き継ぎ）。

## Q1. [設定] 新しく足す時間の設定（参照の問い合わせ 1 回の上限 `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_REFERENCE`、全体の上限 `MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL`）の置き方

A. 既定値（5 秒・10 秒）はアプリの設定ファイル（`application.yml`）に置き、`.env.example` には既存の時間の設定と並べてコメントアウトした行と説明だけを足す。`compose.yaml` の app のサービスには足さない（推奨）。app は `.env` を env_file で読むため、前の Intent と同じ置き方で済み、変えない人は何も書かなくてよい
B. `compose.yaml` の app のサービスの `environment` にも既定値を明示する。設定が目に見えるが、既定が `application.yml` と compose の 2 か所に分かれ、食い違うおそれがある
X. Other (please specify)

[Answer]: A

## Q2. [監視] 参照の API の表示（ダッシュボード）

A. 既存のダッシュボードの業務データの区画に、参照の API（選択肢の取得を含む定義の取得・ピッカーの検索・ラベルの 1 件読み）の要求の数と 503 の数のパネルを足す。警報は足さない（推奨）。参照の API の失敗と量が業務データの一覧と分けて見え、警報は既存の `ms-biz-target-db` で拾える
B. ダッシュボードも変えない。既存の業務データの区画の道の条件で参照の API も合計されるため、手間は無いが、参照だけの量と失敗は見分けにくい
X. Other (please specify)

[Answer]: A
