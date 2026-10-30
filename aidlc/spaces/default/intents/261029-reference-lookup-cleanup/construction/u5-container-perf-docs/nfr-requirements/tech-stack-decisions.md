# 技術の選択 — U5 u5-container-perf-docs

出典: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`（FR6・FR7・NFR8・NFR9）・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/units-generation/unit-of-work.md`（U5）。U5 は種類 `packaging` で Functional Design の段の外のため、`functional-spec.md`・`rules.md` は無い。要件の ID は同じ単位の `security-requirements.md` の `.81` からの枝番を指す。

## 0. 方針

新しい技術・依存・イメージは選ばない（NFR9.81）。この単位は、すでにリポジトリで使っている道具だけで、コンテナの版そろえと perf の文書・台本の整理を行う。

## 1. 使う道具と理由

| 対象 | 選択 | 理由 | 要件 |
|---|---|---|---|
| コンテナの作り直し | Docker Compose（`docker compose --profile mail up -d --force-recreate --no-deps mailpit`、profile `monitoring` の `lgtm` も同じ形） | `compose.yaml` が固定先の正であり、止まっているコンテナも含めて固定の版に作り直せる。`--no-deps` でアプリ・対象DB に触れない（`team.md` の Deployment、`project.md` の学び） | NFR8.82 |
| 版の確かめ | `docker inspect --format`（`Config.Image`・`Image`・`RepoDigests`・`State.Status` だけ）と `docker image ls` | 必要な項目だけを取り出し、`Config.Env` を出さない。`RepoDigests` に `compose.yaml` の複数アーキテクチャの index のダイジェストが含まれるかで照らす | NFR8.82・NFR8.83・NFR8.86 |
| 確かめるイメージ | Mailpit `axllent/mailpit:v1.32.0@sha256:0790692b…`、lgtm `grafana/otel-lgtm:0.38.0@sha256:75cdf892…`（今の `compose.yaml` の値のまま） | 版を変えない作業のため。戻し先として 0.34.0 の lgtm のイメージを残す | NFR8.83・NFR9.81 |
| Mailpit の受信の確かめ | SMTP（`127.0.0.1:1025`）で 1 通送り、Mailpit の HTTP API（`127.0.0.1:8025`）で取得する。送る道具はコード生成の計画で、PC に既にあるもの（新しく入れない）から決める | 予約のドメインの宛先だけで、外へ送らずに確かめられる | NFR8.84 |
| 合否の線の確かめ | k6 の `k6 inspect --include-system-env-vars`（`perf/versions.env` の `K6_IMAGE` の固定のイメージを `docker run --rm` で流す。`--env-file` を付けず、ホストのシェルの環境を継承しない。ホストの k6 は使わない）と、既存の `perf/k6/thresholds-test.sh`。`k6 inspect` の出力は閾値と場面の定義の部分だけを `jq` などで取り出して比べる。`k6 inspect` に出ない線（`perf/dsl-mixed-memory.sh` と README の線の表）は `grep` で取り出して前後の差を取る | 台本を読み込むだけでアプリ・対象DB・ネットワークに触れずに、場面ごとの閾値を前後で比べられる。付けないと場面の名前が `undefined` になる。固定のイメージで流すと、ホストの環境変数が出力に入らず、実行ごとに変わるコンテナの環境変数は比べの対象の外になる | NFR8.87・NFR8.88 |
| 入れ替えの前の確かめ | `docker ps --format`（名前と状態だけ）、`docker image ls grafana/otel-lgtm`・`docker image ls axllent/mailpit`（作り直しの前と後） | VM のメモリに収まるよう止めるコンテナを決め、戻し先のイメージが作り直しの前からあったかを前後の比べで見分ける | NFR8.82・NFR8.83 |
| lgtm の引き継ぎの確かめ | `docker inspect --format` の `State.Health.Status`（または Grafana の `/api/health`）と、Grafana の検索の API でのダッシュボードの件数 | 名前付きボリュームを新しい版へ引き継いだ後に、起動とダッシュボードの読み込みを件数で確かめる。通らなければ 0.34.0 で作り直して戻す | NFR8.82 |
| アプリの起動確認 | `docker compose up -d app`（止まっていたときだけ。作り直しはしない）と `/actuator/health` | 入れ替えの時点でアプリが止まっていても確かめを成り立たせる。確かめの後は元の止まった状態へ戻す | NFR8.84 |
| 秘密の確かめ | Gitleaks（`gitleaks dir`）と `grep`（`eyJ` の形・メールアドレス） | 既に統合の前の検査に入っている道具。記録と README に秘密が残らないことを件数で確かめる | NFR8.86・NFR8.90 |
| 統合の前の検査 | `./gradlew verify`（colima の `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を付ける） | 1 コマンドの関門。文書・台本のコメントだけの Bolt のため E2E は流さない | NFR5.81 |

## 2. 選ばなかったもの

| 案 | 選ばなかった理由 |
|---|---|
| `docker compose pull` の後に `up -d` だけで入れ替える | 構成が同じだとコンテナが作り直されず古い版が残ることがある（`project.md` の学び）。`--force-recreate --no-deps` にする |
| `docker inspect` の全体の JSON を記録に置く | `Config.Env` に環境変数の値が入り、公開のリポジトリに資格情報が残りうる（NFR8.86） |
| 整理の前後の比べに k6 で実際に場面を流す | アプリ・対象DB を起動する負荷の試験が要り、配備したアプリを止める。合否の線の値の比べには `k6 inspect` で足りる |
| `perf/k6/thresholds-test.sh` を `./gradlew verify` に入れる | 要件にない新しい関門で、k6 のイメージを verify の中で引く。この単位では手で流して記録する（NFR8.88） |
| 古いイメージの片付け（`docker image prune`） | 戻し先の 0.34.0 の lgtm を失う（NFR8.83） |

## 3. 未決の点

なし（Mailpit への送り方の具体の道具はコード生成の計画で、新しく入れない範囲で決める）。
