# コンテナの入れ替えの記録 — U5 u5-container-perf-docs（Bolt B6、層 A）

環境変数の値・`.env` の中身・ラベルの値・メールの件名と本文は記録しない。

## A0 了承（2026-10-31T01:35Z ごろ、依頼者）

| 項目 | 答え |
|---|---|
| (i) Mailpit を作り直すと溜まったメールが消える | 了承 |
| (ii) lgtm の監視のデータ（指標・ログ・トレース・Grafana の中の状態）が失われうる | 了承 |
| (iii) アプリが止まっていれば確かめのために `docker compose start app` で起動する | 了承 |

## A1 前の記録（2026-10-31T01:35:55Z）

| コンテナ | 状態 | `Config.Image` |
|---|---|---|
| mastersmith-targetdb-postgres-1 | Up 28 hours | （記録しない。対象外） |
| mastersmith-app-1 | Up 39 hours (healthy) | `mastersmith:local`（イメージ ID e0ede6ddd5c4） |
| mastersmith-lgtm-1 | Exited (0) 3 days ago | `grafana/otel-lgtm:0.38.0@sha256:75cdf892daff…`（イメージ ID 75cdf892daff） |
| mastersmith-mailpit-1 | Up 3 days (healthy) | `axllent/mailpit:v1.32.0@sha256:0790692be935…`（イメージ ID 0790692be935） |

| イメージ | ID | RepoDigests |
|---|---|---|
| grafana/otel-lgtm:0.34.0（戻し先） | b966ea107831 | `grafana/otel-lgtm@sha256:b966ea107831…` |
| grafana/otel-lgtm（0.38.0） | 75cdf892daff | `grafana/otel-lgtm@sha256:75cdf892daff…` |
| grafana/otel-lgtm `<none>` | 57b5ab1ca661・57c04cbd8e70 | （前の版の残り） |
| axllent/mailpit（v1.32.0） | 0790692be935 | `axllent/mailpit@sha256:0790692be935…` |
| axllent/mailpit `<none>` | 94f6b70c1436・74d609a42ec2 | （前の版の残り） |

## 計画との差（依頼者の決定）

A1 の時点で、Mailpit と lgtm のコンテナは、どちらもすでに `compose.yaml` の版と index のダイジェスト（mailpit 121 行・lgtm 83 行）にそろっていた。lgtm は 3 日前に 0.38.0 で動いて止まっており、監視のデータの置き場はすでに 0.38.0 で開かれていた。依頼者の決定（A）で、作り直し（A3 の `--force-recreate`・A5b）を行わず、計画の確かめだけを今の状態に当てた。そのため A2（止めるコンテナ）・A3・A5b・戻し（R1〜R5）は行っていない。Mailpit のメールは消えていない。

## 確かめ

| 確かめ | 結果 |
|---|---|
| CA1・CA2 Mailpit の照合（`Config.Image` が `compose.yaml` と同じ・RepoDigests が `@sha256:0790692be935…` で終わる） | 一致 |
| Mailpit の `/api/v1/info` | 200 |
| Mailpit の受信（`smtp://127.0.0.1:1025` へ 1 通、宛先 example.com、目印の件名で検索） | 1 件。判定 合格 |
| CA3 VM の空きメモリ（`colima ssh -- free -m`） | total 5910 MiB・available 4740 MiB（基準 1792 MiB 以上）。合格 |
| CA4 lgtm の照合（`Config.Image` が `compose.yaml` と同じ・RepoDigests が `@sha256:75cdf892daff…` で終わる） | 一致 |
| lgtm の起動（`docker compose --profile monitoring start lgtm`、01:37:09Z） | 終了コード 0 |
| CA5 `/api/health` | 200、`database` が `ok` |
| CA6 ダッシュボードの `uid` `mastersmith-overview` の検索 | 1 件（全体は参考に 4 件。イメージが持つダッシュボードを含む） |
| CA7 lgtm を止める（01:37:28Z） | `docker ps` に lgtm が無い |
| CA8・CA9 アプリの `/actuator/health` | 200、`UP`（A1 で動いていたため起動は不要。救済の WARN は数えていない） |
| CA10 A9 の後の記録 | コンテナの状態は A1 と同じ（lgtm は止まったまま、ほかは動いたまま） |
| CA11 戻し先と前の版のイメージ | 0.34.0 の lgtm（b966ea107831）と前の Mailpit のイメージ（94f6b70c1436・74d609a42ec2）の ID が残っている。`docker image rm`・`prune` は行っていない |
| CA12 この記録への `gitleaks dir`・コンテナの環境変数の項目名と JWT の形の検索 | `gitleaks dir`: no leaks found（0 件）。環境変数の項目名: 0 件。JWT の形: 0 件 |
