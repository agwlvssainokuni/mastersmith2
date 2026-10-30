# 基盤の仕様 — U5 u5-container-perf-docs（Intent 261029-reference-lookup-cleanup）

- 単位: U5 `u5-container-perf-docs`（種類 `packaging`、Bolt B6）。
- 配備先は開発者の PC 上のコンテナだけ（`team.md` の Deployment）。クラウドの基盤・IaC・検証環境は作らない（`project.md` の Deployment）。この文書は、手元の確かめの道具 2 つ（Mailpit・otel-lgtm）を `compose.yaml` の版にそろえる作業の前と後の形と、触れる perf の文書・台本を表にした記録と設計である。
- 新しく決める論点は無い（同じ単位の `infrastructure-design-questions.md`）。作業の順序・関門・記録・戻しの中身は NFR 設計（`nfr-design/security-design.md` の A0〜A9・R1〜R5・B0〜B4・5.7節）を正とし、ここでは基盤の面（イメージ・profile・ボリューム・上限・口・VM のメモリ・戻し先・触れるファイル）だけを書く。
- この単位はアプリの成果物（WAR・イメージ `mastersmith:local`）を変えず、`compose.yaml` の `image:` の行も変えない。版はすでに `compose.yaml` で 0.38.0・v1.32.0 に固定済みで、動いている（または止まっている）コンテナがそれより古いイメージのまま残っている状態を、作り直しでそろえる。

## 1. Deployment

| Facet | Choice | Rationale |
|---|---|---|
| 計算の形 | Docker Compose のコンテナ（colima の VM の中）。Mailpit は profile `mail`、lgtm は profile `monitoring` で、見たいときだけ起動する | 既存の形のまま（`project.md` の Deployment「手元の監視は compose の profile で見たいときだけ起動」、`team.md` の Deployment「メールの受け手は compose の profile」） |
| 作り直しの形 | サービスの名前を 1 つだけ渡し、`--force-recreate --no-deps` で作り直す（Mailpit → lgtm の順。security-design.md 2.1節 A3・A5b） | 構成が同じだと作り直されず古い版が残ることがある（`team.md` の Deployment、`project.md` の学び）。`--no-deps` でアプリ・対象DB に触れない |
| 網の形 | 既存のまま。口は PC の localhost にだけ結び付ける（Mailpit `127.0.0.1:8025`・`127.0.0.1:1025`、lgtm `127.0.0.1:3000`）。lgtm の OTLP の受け口は PC に開かない | 認証の無い画面をほかの端末に見せない（`compose.yaml` のコメント）。変えない |
| 置き場（ストレージ） | Mailpit はボリュームを持たない。lgtm は名前付きボリューム `mastersmith-monitoring`（実際の名前は `mastersmith_mastersmith-monitoring`。実行のときに `docker volume ls` の完全一致で確かめる）を 0.34.0 から 0.38.0 へ引き継ぐ | 既存のまま。引き継ぎの確かめは 3節 |
| 環境 | 開発者の PC の 1 つだけ（dev・staging・prod の分けは無い） | 配備先が決まるまでの決まり（`team.md` の Deployment） |
| IaC | 使わない。正は `compose.yaml` と `docker/monitoring/` のファイル | `project.md` の Deployment。配備先が決まったときに置き換える前提 |
| 資源の大きさ | 上限は変えない（Mailpit 256m・lgtm 1536m）。lgtm を作り直す前に VM の空きメモリが基準（仮: 1792 MiB）以上かを読み取りだけで確かめる（4節） | 既存の上限のまま。VM（6GiB）の余裕が少ない前例（`project.md` の学び、約 100 MiB）があるため |
| 秘密の扱い | `.env`・`.env.*` を開かない。`docker compose config` の全体と `docker inspect` の全体の JSON を表示しない。記録には security-design.md 3.1節の項目だけを書く | `project.md` の Forbidden（秘密をコミットしない・記録に出さない）。security-design.md 2.3節・3節 |

## 2. Infrastructure Services

### 2.1 入れ替えの前と後

「前」は、作業の時点で手元に残っているコンテナの形で、実行の A1 で `docker ps -a` と `docker inspect --format` で記録して決まる（この文書では値を決めない）。「後」は `compose.yaml` の今の値で、作り直しの照らす正である。

| Service | Role | 前（作業の時点で残る形） | 後（`compose.yaml` の正） | 確かめ |
|---|---|---|---|---|
| Mailpit（サービス `mailpit`） | 手元でメールを受けて画面で見せるだけの受け手（SMTP・HTTP） | 動いている。イメージは `compose.yaml` より古いものでありうる（タグとダイジェストは A1 で記録する） | `axllent/mailpit:v1.32.0@sha256:0790692be935ea4420d085694f7985b1f692ca6a8771ab0e0bd8ba75ac180498`（複数アーキテクチャの index のダイジェスト） | `RepoDigests` に index のダイジェストが含まれる（security-design.md 2.4節）。`127.0.0.1:1025` へ `example.com` の宛先で 1 通送り、`127.0.0.1:8025` の API で件数 1 以上（2.5節） |
| otel-lgtm（サービス `lgtm`） | 手元の監視（OTLP の受け手・Prometheus・Loki・Tempo・Grafana を 1 つにしたもの） | 止まっている。イメージは `grafana/otel-lgtm:0.34.0`（A1 で ID を記録する。無ければ記録して依頼者に諮る） | `grafana/otel-lgtm:0.38.0@sha256:75cdf892daffadf3763ef61c16a6d7f8586216fce0b40bba7874a0b053121084`（index のダイジェスト） | `RepoDigests` の照合、Grafana の `/api/health` が 200 で `database` が `ok`、ダッシュボードのファイルの `uid` ごとにちょうど 1 件（monitoring-design.md 2節） |

### 2.2 各サービスの設定（変えないもの）

| Service | Role | Configuration | Notes |
|---|---|---|---|
| Mailpit | メールの受け手 | profile `mail`・`mem_limit: 256m`・`restart: "no"`・ログは json-file（10m×3）・口は `127.0.0.1:8025` と `127.0.0.1:1025` | ボリュームを持たない。作り直すと溜まったメールが消える（A0 (i) の了承）。作り直しの後、A1 で止まっていた（または無かった）ときは確かめの後に止める（A4） |
| otel-lgtm | 手元の監視 | profile `monitoring`・`mem_limit: 1536m`・`restart: "no"`・口は `127.0.0.1:3000`（Grafana だけ）・匿名の Viewer（`GF_AUTH_ANONYMOUS_*`）・ボリューム `mastersmith-monitoring:/data`・読み取りだけの差し込み 3 つ（`docker/monitoring/provisioning/dashboards/mastersmith.yaml`・`docker/monitoring/provisioning/alerting/mastersmith.yaml`・`docker/monitoring/dashboards/`） | healthcheck を持たない（そのため `/api/health` で確かめる）。確かめの後に止める（A7、`project.md` の Deployment） |
| アプリ（サービス `app`） | 確かめの相手（作り直さない） | 上限は `.env` の `MASTERSMITH_CONTAINER_MEMORY`（既定 2g。値は見ない） | 止まっていたときだけ A0 (iii) の了承を得て起動し、`/actuator/health` を確かめてから元の止まった状態へ戻す（security-design.md 2.7節）。作り直しはしない |
| 対象DB の見本（`targetdb-*`）・使い捨ての環境 | 確かめに要らないもの | 変えない | VM の空きが足りないときに止める候補（A2・A5a）。止めた名前を記録し、A8 で `docker compose start` で戻す（A1 で止まっていたものは起こさない） |

### 2.3 戻し先

| 戻し先 | 形 | 使う条件 | 残し方 |
|---|---|---|---|
| `grafana/otel-lgtm:0.34.0`（手元のイメージ） | A1 で ID を記録したイメージを、`compose.yaml` を書き換えずに作り直しのときだけ指す（仮: リポジトリの外の一時の上書きのファイルを `-f` で重ねる。戻しの後に消して記録する） | lgtm の照合か、`/api/health`・`uid` の確かめが通らないとき（security-design.md 4.1節 R1〜R5） | この単位では `docker image rm`・`docker image prune`・`docker system prune` を行わない。A1 と A9 の `docker image ls` を並べ、0.34.0 が残っていることを判定して記録する（4.2節） |
| Mailpit の前のイメージ | 戻しには使わない（ボリュームを持たず、失うのは了承済みのメールだけ） | — | 消さずに残す（A9 の比べの対象） |

## 3. lgtm のボリュームの引き継ぎ

| 段 | ボリューム `mastersmith-monitoring` の扱い | 失うもの |
|---|---|---|
| A5b（0.38.0 で作り直し） | 既存のボリュームをそのまま引き継ぐ。0.38.0 が開いた時点で新しい版の形に変わりうる | 無い見込み（変わりうることは A0 (ii) で了承を得る） |
| R1・R2（0.34.0 で既存のボリュームのまま） | 0.38.0 が一度開いたボリュームを 0.34.0 で開く。起動する保証は無い | 無い見込み |
| R3・R4（ボリュームを作り直して 0.34.0） | 名前の完全一致で確かめてから `docker volume rm`。`mastersmith-data` などほかのボリュームには触れない。空のボリュームが作られ、ダッシュボードと警報の決まりはファイルから読み込まれる | 溜まった指標・ログ・トレースと Grafana の中の状態（A0 (ii) の了承の範囲）。ダッシュボードと警報の決まりは失わない（リポジトリのファイルが正） |
| R5（止めて諮る） | 触らない | 手元の監視が使えない状態が残る（`code-summary.md` に書き、次の機会を Observability Setup などに回す） |

## 4. VM のメモリの関門

| 項目 | 値・形 | 出どころ |
|---|---|---|
| VM | colima（CPU 4・メモリ 6GiB。`compose.yaml` のコメントの前提。実際の値は A5a で読み取りだけで確かめ、変えない） | `compose.yaml` の lgtm のコメント |
| 同時に動きうるもの | アプリ（既定 2g）・lgtm（1536m）・Mailpit（256m）・対象DB の見本（512m〜768m） | `compose.yaml` |
| 基準（仮） | `colima ssh -- free -m` の `available` が 1792 MiB（lgtm の上限 1536 MiB＋余裕 256 MiB）以上。値はコード生成の計画で決める | security-design.md 2.8節 |
| 足りないとき | アプリ以外を、使い捨ての環境 → 対象DB の見本 → Mailpit（A4 の後）の順に 1 つずつ止めて確かめ直す。止めるものが無くなっても足りなければ A5b に進まず依頼者に諮る | security-design.md 2.8節 |
| 記録 | `available` の値・基準・判定・止めたコンテナの名前だけ | security-design.md 3.1節 |

## 5. 触れる perf の文書と台本

この単位は perf の負荷の環境（`docker/perf/compose.yaml`）を起動しない。台本は k6 の固定のイメージで `k6 inspect` するだけで、アプリ・対象DB・網に触れない（security-design.md 5.3節）。

| ファイル | 変えること | 変えないもの | 確かめ |
|---|---|---|---|
| `perf/README.md` | 先頭に「今の基準の一覧」、末尾に `## 履歴`。本文と履歴の分け方・見出しの書き換えは security-design.md 5.2.1節。U4 の伏せ字の節を本文に収める | 線の値と名前の組、コードブロックの中のコマンド、433 行の `grafana/otel-lgtm:0.38.0@sha256:75cdf892…`（Dependabot が見ない固定先） | 5.4節の組の比べ、5.6節の事後の検索（Q1〜Q7）、5.7節の行の集合の突き合わせ |
| `perf/k6/scenarios.js` | `thresholdsFor` と、その直前に隣り合わせて置く `USER_THRESHOLDS`・`BIZ_P95_LIMIT_MS` の区画（1 か所）にまとめ、コメントで整理する | `scenarios` の形・場面の名前・線の値 | `k6 inspect --include-system-env-vars` の前後で `thresholds` と場面の名前の集合の差が空（5.3節） |
| `perf/k6/thresholds-test.sh` | 必要ならコメントだけ | 期待の表と判定の値 | 手で流して終了コード 0（5.5節） |
| `perf/dsl-mixed-memory.sh`・`perf/dsl-mixed-memory-test.sh` | 必要ならコメントだけ | 線の値と判定 | grep の前後の差（5.4節）、`dsl-mixed-memory-test.sh` を手で流す（5.5節） |
| `perf/versions.env` | 変えない（`K6_IMAGE` を k6 inspect に使うだけ） | 全体 | 作業ブランチの差に無いこと（6節） |
| `docker/perf/compose.yaml`・`compose.yaml`・`README.md`・`.github/dependabot.yml` | 変えない | 全体（Mailpit・lgtm の固定先を含む） | 作業ブランチの差に無いこと（security-design.md 6節） |

- 整理の前後の写し（README・k6 inspect の取り出し）はリポジトリの外（ホームの下の権限 700 の置き場）に置き、コミットしない（同じ単位の `infrastructure-design-questions.md`）。
- B（perf の整理）は U4（B5）が `develop` に統合された後に始める（B0）。A（入れ替え）とは互いに頼らない。

## 6. Shared Infrastructure

| Shared Resource | Owner Unit | Consumer Units | Access Boundary |
|---|---|---|---|
| `perf/README.md` | U5（整理） | U4（伏せ字の節を書く。B5） | U4 が先に書き、U5 は U4 の節の中身を変えずに本文に収める（security-design.md 5.1節・5.6節 Q1・Q2・5.7節 L3） |
| `perf/k6/scenarios.js` の `thresholdsFor` | U5（集約） | Build and Test（k6 の新しい場面の線を足す） | U5 は値を変えず場所だけを集める。Build and Test は線を足すだけで今の値を変えない（`bolt-plan.md` の引き継ぎ） |
| lgtm のボリューム `mastersmith-monitoring` | この Intent では U5 だけが作り直す | Observability Setup（後の段・後の Intent） | ほかの単位は触らない。データは捨ててよい（A0 (ii)） |

## 7. 上流との差

- NFR 設計の security-design.md は「前」の lgtm を 0.34.0 と書くが、Mailpit の前の版は書いていない。この文書でも Mailpit の前の版を決めず、A1 の記録で決まるものとした（決め打ちしないため）。`.github/dependabot.yml` のコメントの経緯（前の Intent で v1.31.6 に固定し、その後 v1.32.0 へ上げた）から、動いているものは v1.31.6 かそれより前と見立てるが、確かめていない（見立て）。
- `perf/README.md` 433 行の `grafana/otel-lgtm` の固定先（負荷の試験の手順の中で使い捨ての lgtm を起動するコマンド）は、すでに 0.38.0 のダイジェストにそろっている（2026-10-30 の読み取り）。U5 は手元の lgtm のコンテナをそろえる作業で、この行を変えない。整理で行が動いても文字列を変えないことを 5.6節 Q6 で確かめる（NFR9.82）。
- VM の大きさ（CPU 4・メモリ 6GiB）は `compose.yaml` のコメントの値で、この段では PC を読んでいない（docker のコマンドを使わない段のため）。A5a で読み取りだけで確かめる。

## 8. 後の段に回すこと

| 段 | 回すこと |
|---|---|
| コード生成（計画） | A0〜A9・R1〜R5・B0〜B4 の実際のコマンドの列。VM の空きの基準の値。0.34.0 を指す一時の上書きのファイルの形と置き場。記録のファイルの置き場と名前。外の置き場の名前 |
| コード生成 | 依頼者の了承（A0）の後の入れ替えの実行と記録、perf の整理と前後の比べ。戻した（R1〜R5）ときは `code-summary.md` に書く |
| Build and Test | 整理した `thresholdsFor` に k6 の新しい場面の線を足して流す |
| Observability Setup（後の Intent を含む） | R3 でボリュームを作り直したとき、または R5 で止めたときの、手元の監視の確かめ直し |

## Assumptions & Open Questions

None.

## Sources

- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u5-container-perf-docs/infrastructure-design/infrastructure-design-questions.md`（質問なし・決まっていること）
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u5-container-perf-docs/nfr-design/security-design.md`（A0〜A9・R1〜R5・B0〜B4・2.4〜2.8節・3節・4節・5節・6節・7節）
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u5-container-perf-docs/nfr-requirements/security-requirements.md`・`tech-stack-decisions.md`
- performance-design・scalability-design・reliability-design・observability-design・logical-components: U5 は種類 `packaging` のため NFR 設計でこれらを作っていない（security-design.md の冒頭）。対応する中身（戻し・観測・資源の上限）は security-design.md と、この文書の 2〜4節で扱った
- functional-spec: U5 は種類 `packaging` のため機能設計（functional-spec.md）を作っていない。振る舞いの根拠は要件・単位の定義・NFR 設計とした
- components: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/domain-design/components.md`（「要件の対応と、部品にしないもの」: FR6 は部品なし（`compose.yaml` と手順、`docker inspect` で確かめる）、FR7 は部品なし（文書）。`container-runtime`・`perf-and-monitoring` は部品の一覧に載らない）
- contract-summary: U5 は単位の間の契約を持たない
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`（FR6.1〜FR6.4・FR7.1〜FR7.4）
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/units-generation/unit-of-work.md`（U5）
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/delivery-planning/bolt-plan.md`（B6・Construction の後に引き継ぐこと）
- 読み取りで確かめたもの（2026-10-30）: `compose.yaml`（`mailpit`・`lgtm` の `image:`・profile・口・`mem_limit`・ボリューム・healthcheck の有無、`volumes:`）、`docker/monitoring/`（`dashboards/mastersmith-overview.json`・`provisioning/dashboards/mastersmith.yaml`・`provisioning/alerting/mastersmith.yaml`）、`docker/perf/compose.yaml`（Mailpit の固定先）、`perf/README.md`（433 行の lgtm の固定先、1,359 行）、`perf/` の台本の一覧、`.github/dependabot.yml`（固定先の一覧の決まり）
