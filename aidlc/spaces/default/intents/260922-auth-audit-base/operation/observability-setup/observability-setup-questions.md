# Observability Setup の質問（observability-setup-questions）

Intent `260922-auth-audit-base`（auth-audit-foundation）の運用で見る指標・警報・目標・ログの見方を整えるための質問。

## 前提（確認済みのこと）

- 配備先は開発者の PC 上のコンテナだけで、クラウドの配備先はまだ決まっていない（team.md・project.md の Deployment）。版 `7040876` が配備済みで動いている（`operation/deployment-execution/deployment-log.md`）。
- 承認済みの監視の設計（U1〜U4 の `construction/<単位>/infrastructure-design/monitoring-design.md`）は、「当面は常時の監視の仕組み（収集・保存・警報の基盤）を持たない。本番の目標（SLO）と警報、ダッシュボードの置き場所は、配備先が決まったときに Observability Setup で定める」としている。指標・警報・目標の**候補**と、当面の確かめ方（`docker compose logs`、監査ログの複写を読み取りで開く、依頼者が週1回・月1回に確かめる）は設計にある。
- アプリの側の仕組み（1行1件の JSON のログ、すべての要求のトレースID、外部エクスポートは既定で無効・有効にすればトレース・ログ・指標を OTLP で送る、秘密情報を載せない）は実装とテストで確かめ済み（`construction/build-and-test/build-and-test-summary.md` の U1-NFR10.1〜10.11 と U1-NFR3.4 は Met）。
- この段が持ち主の未検証の目標は5件: U2-NFR10.7（ログインの失敗の割合を監査ログから数える）、U3-NFR10.5（管理画面へのアクセス拒否の件数）、U4-NFR10.5（監査の書き込みの失敗の件数）、U4-NFR1.4（監査イベントの量を無期限に保存しても扱える）、U1-NFR1.9（内部DBのファイルの増え方の見積もり。表の持ち主は performance-validation だが、集計の分類は運用の見積もりで食い違っている）。

## Q1. 配備先が決まっていない中での、この段の扱い

A. 当面の PC 上のコンテナの範囲で「手で見る運用」として整える。設計の指標・警報・目標の候補を1つにまとめ、ダッシュボードと自動の警報は置かない。代わりに、ログと監査ログを調べる検索の命令、定期に確かめる手順（誰が・いつ・何を見て・何を超えたら動くか）、配備先が決まったときの置き換えの表を作る。目標（SLO）は候補のまま残す
B. 手元に監視の仕組みを実際に立てる（例: compose の profile に指標の収集と表示の道具を足し、ダッシュボードと警報を作る）。承認済みの設計の「当面は持たない」を変える
C. この段では何も作らず、配備先が決まってからまとめて行う（未検証の5件は残す）
X. Other (please specify)

[Answer]: B

## Q2. 外部エクスポートの手元での確認

設計（U1 の `monitoring-design.md` 4章）には、`docker compose --profile observability up` で OTLP の受け手（OpenTelemetry Collector、受け取ったものを標準出力に出すだけ）を起動し、外部エクスポートを有効にして、トレース・ログ・指標が届くことと秘密情報が載らないことを確かめる手順があります。同じことは結合テスト（`ExternalExportIT`）で確かめ済みです。

A. この段で、配備したコンテナで実際に行う（AI が `.env` に外部エクスポートの2行を一時的に足して起動し直し、画面を開く・ヘルスチェックなどの要求で届くことを確かめ、確認の後に元に戻す。ログインを伴う確認は依頼者がブラウザで行う）
B. 行わない（結合テストの結果で足りるとする。手順は記録に残す）
X. Other (please specify)

[Answer]: A

## Q3. 未検証の5件の判定の方針

A. 数え方・見方を決めて、実際のデータ（今の配備の監査ログとログ）で動かして示せたもの（U2-NFR10.7・U3-NFR10.5・U4-NFR10.5）は Met とする。量の見積もり（U4-NFR1.4・U1-NFR1.9）は、1年分の増え方の実測が無いため Unverified のまま、月1回の確認で見る項目として残す
B. A に加えて、量の見積もり（U4-NFR1.4・U1-NFR1.9）も、見積もり（1年 250MB 程度）と colima の VM のディスク（100GiB）の比較、今のボリュームの実測をもって Met とする
C. 5件とも Unverified のまま残し、配備先が決まってから判定する
X. Other (please specify)

[Answer]: A

## Q4. 監視の道具の組み合わせ（追加の質問）

Q1 で「手元に監視の仕組みを立てる」を選びました。アプリは指標・ログ・トレースを OTLP で送るだけで、指標を読み取る口（`/actuator/prometheus`）は公開していません（U1 の `nfr-design/security-design.md` 4章）。そのため、OTLP の受け手 → 保存 → 表示と警報、の形になります。

A. `grafana/otel-lgtm`（OTLP の受け手・指標・ログ・トレースの保存と Grafana を1つにした、手元の確認向けのコンテナ）を compose の profile で起動する。設定が少ない
B. 部品を別々のコンテナにする（OpenTelemetry Collector・Prometheus・Loki・Tempo・Grafana）。配備先の構成に近いが、設定とメモリが多い
C. 指標だけにする（OpenTelemetry Collector → Prometheus → Grafana）。ログとトレースは今までどおり `docker compose logs` とトレースIDで見る
X. Other (please specify)

[Answer]: A

## Q5. メモリの確保（追加の質問）

colima の VM はメモリ 約 2GiB で、アプリのコンテナが 1GiB を使います。監視の道具（Q4 の A で 0.5〜1GiB 程度の見込み）を足すと足りなくなるおそれがあります。

A. colima の VM のメモリを増やす（例: `colima stop` → `colima start --cpu 2 --memory 4`。CPU は今の 2 のまま。VM の起動し直しでアプリも一度止まる）。依頼者が行う
B. VM はそのままにし、監視の道具のコンテナにメモリの上限をかけて収める（足りないと監視の道具かアプリが止まることがある）
X. Other (please specify)

[Answer]: B

## Q6. 警報の知らせ先（追加の質問）

A. 外へは知らせない。Grafana の画面の警報の一覧で見る（依頼者が開いたときに気づく）
B. メールで知らせる（メールの送信の設定と、その秘密情報を `.env` に置く）
X. Other (please specify)

[Answer]: A

## Q7. 監視の仕組みを動かす時間（追加の質問）

A. アプリと一緒に常に起動する（compose の既定の起動に含める）
B. 見たいときだけ起動する（profile を指定したときだけ。既定の起動はアプリだけのまま）
X. Other (please specify)

[Answer]: B

## Consolidated Summary Confirmation

回答の要約:

- Q1・Q4・Q7: 手元に監視の仕組みを立てる。`grafana/otel-lgtm`（版の番号まで固定）を `compose.yaml` の新しい profile `monitoring` で、見たいときだけ起動する。既定の起動（アプリだけ）は変えない。承認済みの設計の「当面は常時の監視の仕組みを持たない」からの変更として記録する。
- 起動の手順: 見るときは `.env` で外部エクスポートを有効にして送り先を監視のコンテナにし、`docker compose --profile monitoring up -d` でアプリと一緒に起動し直す。見終わったら外部エクスポートを無効に戻す（無効に戻さないと、送り先が無い間は送信の失敗の警告がログに出る）。README に手順を書き、`.env.example` に名前を足す。
- Q5: colima の VM はそのままにし、監視のコンテナにメモリの上限をかける（アプリの実使用 約 275MiB、VM の空き 約 1.4GiB から 900MiB 程度を目安に、起動して確かめて決める）。Grafana の画面は `127.0.0.1` にだけ結び付け、OTLP の受け口は PC に開かない。
- Q6: 警報は外へ知らせず、Grafana の警報の一覧で見る。警報の決まりは、承認済みの監視の設計の候補（ヘルス、5xx の割合、ERROR の件数、ログインの応答時間、コネクションプールの待ち、JVM のメモリ、監査の書き込みの失敗、ロックの多発、Origin の不一致、403 の多発、細工されたパスの拒否など）から、送られてくる指標とログで作れるものを、ファイルでリポジトリに置く（Grafana の起動時に読み込む）。
- ダッシュボードは U1 の設計の「稼働・エラー・応答時間・資源」の4つの面と、U2〜U4 の面（ログイン、拒否、監査の書き込み）を、ファイルでリポジトリに置く。
- 目標（SLO）は、承認済みの設計の候補を「仮の目標」として画面に出す（稼働 99.5%、ログイン・更新の 95 パーセンタイル 1 秒、確認用 API 300 ミリ秒、監査の書き込み 50 ミリ秒）。正式な値は配備先が決まったときに決める。
- 自動の異常検知（機械学習の基準線）は使わず、決まった値のしきい値だけにする（手元の小さい量では基準線が作れないため）。
- Q2: 設計の手順どおり、既存の profile `observability`（受け取ったものを標準出力に出すだけの受け手）で外部エクスポートを確かめる。AI が `.env` を一時的に変えて起動し直し、トレース・ログ・指標が届くことと、秘密情報が載らないことを確かめ、確認の後に元に戻す。ログインを伴う確認は依頼者がブラウザで行う。
- Q3: U2-NFR10.7・U3-NFR10.5・U4-NFR10.5 は、数え方を実際のデータで動かして示して Met とする。U4-NFR1.4・U1-NFR1.9 は Unverified のまま、月1回の確認の項目として残す。
- 成果物: `dashboards.md`・`alarms.md`・`slo-config.md`・`log-queries.md`・`tracing-config.md`・`anomaly-config.md`。リポジトリの変更: `compose.yaml`、ダッシュボード・警報の設定のファイル（`docker/` の下）、README、`.env.example`。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
