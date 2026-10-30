# セキュリティの設計 — U5 u5-container-perf-docs

- 単位: U5 `u5-container-perf-docs`（種類 `packaging`）。Bolt B6。
- この段の成果物は、この文書と `traceability.json` だけ（`packaging` の単位のため、性能・規模・信頼性・観測・論理の部品の文書は作らない）。
- この文書は、NFR 要件（同じ単位の `nfr-requirements/security-requirements.md` の NFR8.81〜NFR8.90・NFR9.81・NFR9.82・NFR5.81）を、作業の順序・記録の形・確かめの形の設計に直したもの。実際のコマンドの列と記録のファイルの置き場の名前はコード生成の計画で決める。名前に「仮」と付けたものはコード生成で決める。
- 新しく決める論点は無い（同じ単位の `nfr-design-questions.md` のとおり）。この文書は決まっていることを手順の形にそろえる。

## 1. 守るものと設計の全体

U5 はアプリのコード・API・画面・DB を持たない。守るものは次の 3 つで、それぞれを手順の「関門」（通らなければ次へ進まない点）で守る。

| # | 守るもの | 守り方（この文書の節） | 要件 |
|---|---|---|---|
| P1 | `.env` の資格情報と環境変数の値が、画面・会話・記録・リポジトリに出ないこと | 取り出す項目を名前で限る形（3節）、外の置き場（5.3節）、置いた後の `gitleaks dir`（3.3節） | NFR8.86・NFR8.87・NFR8.90 |
| P2 | 手元の確かめの道具（Mailpit・otel-lgtm）が `compose.yaml` の版とダイジェストにそろい、戻し先を失わないこと | 入れ替えの順序（2節）、ダイジェストの照合（2.4節）、戻し（4節） | NFR8.81〜NFR8.85・NFR9.81 |
| P3 | perf の合否の線と、U4 の伏せ字の手順が、整理で弱まらないこと | 前後の比べ（5.3節・5.4節）、README の事後の検索（5.6節） | NFR8.87〜NFR8.90・NFR9.82・NFR5.81 |

B6 の作業は、A（コンテナの入れ替え）と B（perf の文書の整理）の 2 つに分かれ、互いに頼らない。A を先に行う（了承の取得が B6 の最初の手順のため。NFR8.81）。B は U4（B5）が `develop` に統合された後でなければ始めない（5.1節）。

```mermaid
flowchart TD
  S0[A0 了承を得る: Mailpit のメールが消える] --> S1[A1 前の記録: docker ps と docker image ls]
  S1 --> S2[A2 要らないコンテナを止める]
  S2 --> S3[A3 Mailpit を作り直す]
  S3 --> S4[A4 Mailpit の照合と受信の確かめ]
  S4 --> S5[A5 lgtm を作り直す]
  S5 --> S6{A6 照合・healthy・ダッシュボード}
  S6 -- 通る --> S7[A7 lgtm を止める]
  S6 -- 通らない --> R[戻し: 0.34.0 で作り直す]
  R --> S7
  S7 --> S8[A8 アプリの起動確認と止めたものを戻す]
  S8 --> S9[A9 後の記録と gitleaks dir]
  S9 --> B0{B0 U4 が統合済みか}
  B0 -- はい --> B1[B1 前の取り出し: k6 inspect と grep]
  B1 --> B2[B2 README と台本の整理]
  B2 --> B3[B3 後の取り出しと前後の差]
  B3 --> B4[B4 README の事後の検索・thresholds-test.sh・verify]
```

文字による代わり: A0 で了承を得てから、A1 で前の状態を記録し、A2 で要らないコンテナを止め、A3〜A4 で Mailpit、A5〜A6 で lgtm を作り直して確かめる（lgtm が通らなければ 0.34.0 へ戻す）。A7 で lgtm を止め、A8 でアプリの起動確認と止めたコンテナの戻しを行い、A9 で後の状態を記録して秘密の検査を流す。その後、U4 の統合を確かめてから（B0）、B1 で整理の前の線を取り出し、B2 で整理し、B3 で後の線と比べ、B4 で README の検索・`thresholds-test.sh`・`./gradlew verify` を流す。

## 2. コンテナの入れ替えの設計（A）

### 2.1 順序と関門

| 手順 | すること | 関門（通らなければ止まる条件） | 要件 |
|---|---|---|---|
| A0 | Mailpit を作り直すと溜まったメールが消えること（Mailpit はボリュームを持たない。`compose.yaml` のコメントのとおり）を依頼者に伝え、了承を得る。了承の日時と答えを記録する | 了承が無ければ A1 以降に進まない（B は A と独立のため、依頼者が望めば B を先に進めてよいが、A を省いて B6 を終えない） | NFR8.81 |
| A1 | 前の記録: 動いているコンテナの名前と状態、`grafana/otel-lgtm` と `axllent/mailpit` のイメージの一覧（3.1節の形）を記録する | 0.34.0 の lgtm のイメージが無ければ、その事実を記録し、戻し先が無いまま進めるかを依頼者に諮る（4.2節） | NFR8.82・NFR8.83 |
| A2 | lgtm（上限 1536m）を作り直す前に、確かめに要らないコンテナ（対象DB・使い捨ての環境など）を `docker compose stop` で止め、止めた名前を記録する。アプリ（`app`）は止めない | 止めたコンテナの名前の一覧が記録にあること（A8 で戻すため） | NFR8.82（`project.md` の VM のメモリの余裕） |
| A3 | Mailpit を `docker compose --profile mail up -d --force-recreate --no-deps mailpit` で作り直す | 終了コード 0 | NFR8.82 |
| A4 | Mailpit の照合（2.4節）と、受信の確かめ（2.5節） | 照合が一致し、受信の件数が 1 以上 | NFR8.82・NFR8.84 |
| A5 | lgtm を `docker compose --profile monitoring up -d --force-recreate --no-deps lgtm` で作り直す（止まっているコンテナでも作り直す） | 終了コード 0 | NFR8.82 |
| A6 | lgtm の照合（2.4節）と、ボリュームの引き継ぎの確かめ（2.6節） | どちらかが通らなければ 4.1節の戻しへ | NFR8.82 |
| A7 | lgtm を `docker compose stop lgtm` で止め、動いているコンテナの一覧に lgtm が無いことを確かめ、止めた時刻を記録する | 一覧に lgtm が無いこと | NFR8.85 |
| A8 | アプリの起動確認（2.7節）。A2 で止めたコンテナを `docker compose start` で元の状態に戻す（A1 で止まっていたものは起こさない） | ヘルスチェックの結果が記録にあること | NFR8.84・NFR8.82 |
| A9 | 後の記録（3.1節の形、A1 と同じ項目）、前後の比べ（4.2節）、記録のファイルへの `gitleaks dir`（3.3節） | 比べで 0.34.0 が消えていないこと、`gitleaks dir` が 0 件 | NFR8.83・NFR8.86 |

- `--no-deps` で、アプリ・対象DB に触れない。`docker compose pull` の後の `up -d` だけでは、構成が同じとき作り直されず古い版が残ることがある（`project.md` の学び）ため、必ず `--force-recreate` を付ける。
- Mailpit を先に、lgtm を後にする。Mailpit は小さく（上限 256m）、作り直しが失敗しても戻す必要が少ない（受けたメールは了承済みで消える）。lgtm はボリュームを引き継ぐため、先に Mailpit で手順の形を確かめてから行う。
- lgtm は見たいときだけ起動する決まり（`project.md` の Deployment）のため、確かめ終えたら必ず止める（A7）。止めるのは確かめの後で、確かめの途中で止めない。

### 2.2 プロファイルの扱い

Mailpit は profile `mail`、lgtm は profile `monitoring` にある。`docker compose` の作り直しと止める操作には、それぞれの profile を付けるか、サービスの名前を明示する。ほかの profile のサービスを巻き込まないよう、サービスの名前を必ず 1 つだけ渡す。

### 2.3 秘密に触れない操作の決まり

- `.env`・`.env.*` を開かない。`docker compose config` の全体を表示しない（`.env` の値が展開されて出るため）。
- `docker compose up` の出力（コンテナの名前と状態の行）は表示してよい。値を含む警告が出たときは、記録に写さない。
- `docker inspect` は 3.1節の `--format` の形だけで流し、全体の JSON を表示しない。

### 2.4 ダイジェストの照合

照らす正は `compose.yaml` の `image:` の行（タグと複数アーキテクチャの index のダイジェスト）。コンテナが使っているイメージの `RepoDigests` に、その index のダイジェストが含まれるかで判定する（引いた 1 つのプラットフォームのダイジェストとは照らさない）。

```text
# 考え方（実際のコマンドはコード生成の計画）
want = compose.yaml の <サービス>.image の "@sha256:" の後ろ          # index のダイジェスト
tag  = docker inspect --format '{{.Config.Image}}' <コンテナ>         # タグ@ダイジェストの文字列
img  = docker inspect --format '{{.Image}}' <コンテナ>                # イメージの ID
digs = docker image inspect --format '{{json .RepoDigests}}' <img>   # 名前@sha256:… の配列
判定 = tag が compose.yaml の image と同じ 且つ digs のどれかが "@" + want で終わる
```

- 判定が一致しないときは、原因（例: 古いコンテナが残った・別のタグを引いた）を記録し、`--force-recreate --no-deps` でもう一度作り直して確かめ直す。確かめ直すまで A を完了にしない。
- 記録にはダイジェストを先頭 12 文字で書いてもよいが、判定は全体の文字列で行う。

### 2.5 Mailpit の受信の確かめ

- 送り先は `127.0.0.1:1025`（`compose.yaml` で PC の localhost にだけ結び付けた口）に限る。宛先と差出人は予約のドメイン（`example.com`）だけにする。件名に目印の語（仮: 実行ごとに一意の値を含む）を入れ、本文に個人に関する値を入れない。
- Mailpit の HTTP API（`127.0.0.1:8025`）の検索で、目印の語の件数を数える。記録には宛先のドメイン・件数・判定だけを書き、件名と本文の値を書かない。
- 送る道具は PC に既にあるもの（新しく入れない）からコード生成の計画で決める（`tech-stack-decisions.md` 3節）。実在の宛先と外部の SMTP には送らない（`project.md` の Forbidden）。
- 確かめに送ったメールは Mailpit に残る（次の作り直しで消える）。ほかに個人に関する値を含まないため、消す操作はしない。

### 2.6 lgtm のボリュームの引き継ぎの確かめ

lgtm は名前付きボリューム `mastersmith-monitoring` を 0.34.0 から 0.38.0 へ引き継ぐ。`compose.yaml` の lgtm に healthcheck は無いため、次の 2 つで確かめる。

| 確かめ | 形 | 合格 |
|---|---|---|
| 起動 | Grafana の `127.0.0.1:3000/api/health` を、時間の上限（仮: 120 秒）まで待って読む | 応答の状態コードが 200 で、`database` の項目が `ok` |
| ダッシュボードの読み込み | Grafana の検索の API（`/api/search` の種類をダッシュボードに絞る）で件数を数える。匿名の Viewer で読める | 件数が `docker/monitoring/dashboards/` のファイルの数（今は 1）と同じ |

- どちらかが通らないときは、4.1節の戻しを行い、通らなかった確かめと応答の状態コード（本文の値は書かない）を記録する。
- 警報の決まりの読み込みは、Observability Setup の確かめ（`perf/README.md` の手順）の範囲で、この単位の合否に入れない。

### 2.7 アプリの起動確認

- A1 の記録でアプリ（`app`）が動いていれば、`/actuator/health` の応答（状態コードと `status`）を記録する。
- 止まっていれば、既定として `docker compose up -d app` で起動して（作り直しはしない。`--force-recreate` を付けない）ヘルスチェックを確かめ、確かめの後に `docker compose stop app` で元の止まった状態へ戻す。
- VM のメモリなどの事情で起動を省くときは、省いた理由を記録する（依頼者の決定として扱う）。

## 3. 記録の設計

### 3.1 記録する項目（だけ）

入れ替えの記録は Intent の記録（`aidlc/` の下。置き場の名前はコード生成の計画）に置き、公開のリポジトリにコミットされる前提で書く。記録してよい項目は次に限る。

| 項目 | 取り出し方 | 例の形 |
|---|---|---|
| 動いているコンテナ | `docker ps --format '{{.Names}}\t{{.Status}}'`（`-a` を付けると止まっているものも） | 名前・状態 |
| イメージの一覧 | `docker image ls --format '{{.Repository}}:{{.Tag}}\t{{.ID}}' grafana/otel-lgtm`（`axllent/mailpit` も） | タグ・ID |
| コンテナのイメージ | `docker inspect --format '{{.Config.Image}} {{.Image}} {{.State.Status}}'` | タグ@ダイジェスト・ID・状態 |
| イメージのダイジェスト | `docker image inspect --format '{{json .RepoDigests}}'` | 名前@sha256 の配列 |
| 照合の判定 | 2.4節の判定 | 一致・不一致と理由 |
| lgtm の起動とダッシュボード | 2.6節 | 状態コード・件数 |
| Mailpit の受信 | 2.5節 | 宛先のドメイン・件数 |
| アプリの起動確認 | 2.7節 | 状態コード・`status`・起動したか |
| 了承・止めた・戻した | A0・A2・A7・A8・4.1節 | 日時（UTC）・名前・理由 |

### 3.2 記録しないもの

- `Config.Env`・`Config.Labels` の値・`HostConfig` の全体・`docker inspect` の全体の JSON。
- `.env` の値、`docker compose config` の出力、環境変数の値（名前だけを書くことは許す）。
- Mailpit のメールの件名・本文、Grafana の応答の本文。
- k6 inspect の出力の本文（5.3節）。

### 3.3 置いた後の検査

- 記録のファイルを置いた後に、そのファイル（またはその置き場のディレクトリ）に `gitleaks dir` を流し、0 件を確かめて件数を記録する。
- あわせて、記録のファイルを `Config.Env` の語と `eyJ` の形で検索し、0 件を記録する。
- 0 件でないときは、記録を直してからやり直し、コミットしない。

## 4. 戻しの設計

### 4.1 lgtm の戻し

- 戻す条件: 2.4節の照合が確かめ直しても一致しない、または 2.6節の起動・ダッシュボードの確かめが通らない。
- 戻し方: 手元の 0.34.0 のイメージ（A1 で ID を記録したもの）で lgtm を作り直す。`compose.yaml` を書き換えず（NFR9.81）、作り直しの一回だけ版を指定する形をコード生成の計画で決める（仮: `compose.yaml` を上書きする一時のファイルをリポジトリの外に置き `-f` で重ねる。一時のファイルは戻しの後に消す）。
- 戻した後に、0.34.0 で `/api/health` とダッシュボードの件数を確かめ、戻した理由と結果を記録し、A7 へ進む（止める）。戻したことは承認の場で依頼者に伝え、版そろえ（FR6.2）が果たせていないことを `code-summary.md` に書く。
- Mailpit は戻しの対象にしない（ボリュームを持たず、作り直しで失うものは了承済みのメールだけのため）。照合が一致しないときは、原因を記録して作り直しをやり直す。

### 4.2 戻し先を残す

- この単位では `docker image rm`・`docker image prune`・`docker system prune` を行わない。
- A1 と A9 の `docker image ls` の結果を並べ、A1 にあった `grafana/otel-lgtm:0.34.0`（と Mailpit の前のイメージ）が A9 にもあることを判定して記録する。
- A1 で 0.34.0 が無かったときは、その事実を記録し、戻し先が無いまま進めるかを依頼者に諮る（推奨: 進める前に 0.34.0 を引いておく。ただし版の固定はダイジェストで行い、引いたことを記録する）。

## 5. perf の文書と台本の整理の設計（B）

### 5.1 始める前の関門（B0）

- U4（B5）が `develop` に統合済みで、`perf/README.md` に U4 の伏せ字の節（`logical-components.md` の LC10）があることを確かめる。今の `perf/README.md` には `evidence` を含む行が無い（2026-10-30 の読み取り）ため、この関門は B5 の後でなければ通らない。
- U4 の節の見出しの文字列（H2・H3）をそのまま記録する（5.6節の比べの元）。

### 5.2 整理の形

| 対象 | 整理の形 | 変えないもの | 要件 |
|---|---|---|---|
| `perf/README.md` の先頭 | 「今の基準の一覧」を置く。線の名前・値・線を定義する場所（`thresholdsFor`・`perf/dsl-mixed-memory.sh`）の表にする | 線の値と名前の組 | FR7.1・NFR8.88 |
| `perf/README.md` の過去の値 | 前の Intent の結果の文と値を「履歴」の章にまとめる。移すのは今の README にある文と値だけで、`evidence/` など前の記録から新しく値を写さない | 文の中身（移すだけ） | FR7.2・NFR8.90 |
| `perf/README.md` の U4 の節 | 見出しと中身を、意味を変えずに整理した README に収める | 伏せ字の手順（唯一の入口・置いた後の検査と `gitleaks dir`） | NFR8.89 |
| `perf/k6/scenarios.js` | 合否の線の定義を `thresholdsFor` の 1 か所に集め、コメントで整理する | `scenarios` の形・場面の名前・線の値 | FR7.3・NFR5.81 |
| `perf/k6/thresholds-test.sh`・`perf/dsl-mixed-memory.sh` | 線の値が `thresholdsFor` と README の一覧と食い違わないよう、必要ならコメントだけを直す | 期待の表と判定の値 | NFR8.88 |
| Dependabot が見ない固定先 | README の整理で行が動いても、版とダイジェストの文字列を変えない | 版とダイジェスト | NFR9.82 |

- 履歴の章の外（今の基準と手順の本文）に、Intent の名前（`26` で始まる 6 桁）と日付を残さない。手順の中で「前例がある」と書く理由は、履歴の章への参照で示す。

### 5.3 k6 inspect の前後の比べ（B1・B3）

- 流し方: `perf/versions.env` の `K6_IMAGE` を `docker run --rm` で流す。ホストに入れた k6 は使わない。`--env-file` を付けない。`-e` は `-e SCENARIO=<名前>` のように値を書いた形だけを使い、値を書かない `-e 名前` の形（ホストの値を渡す）を使わない。台本は読み取りだけで結び付ける。
- 比べる部分: 出力のうち `thresholds` と `scenarios` だけを `jq` で取り出し、キーを並べ替えて保存する。コンテナの中の環境変数（`HOSTNAME` など実行ごとに変わる値）は比べの対象に入れない。
- 場面の一覧: `scenarios.js` の `scenariosFor`・`thresholdsFor` の分岐と、場面の名前の一覧の定数から、場面の名前の集合を取り出す（取り出し方はコード生成の計画）。前と後で同じ集合を使い、前後の集合の差が空であることも確かめる（整理で場面が増減していないこと）。
- 置き場: リポジトリの外のホームの下の、権限 700 のディレクトリ（仮: `~/.mastersmith-perf-inspect/`）。macOS の一時ディレクトリは使わない（colima の VM から見えないため。`project.md` の学び）。ファイルの権限は 600。
- 記録: 流し方（`K6_IMAGE` の `docker run`・`--env-file` なし）、場面の数、場面ごとの差の有無（すべて空）だけを書き、出力の本文を書かない。確かめの後に置き場の出力を消し、消したことを記録する。

```text
# 考え方（場面ごとに前と後で同じ形で流す。実際のコマンドはコード生成の計画）
source perf/versions.env
docker run --rm -e SCENARIO="$name" -v "$PWD/perf/k6:/scripts:ro" "$K6_IMAGE" \
  inspect --include-system-env-vars /scripts/scenarios.js \
  | jq -S '{thresholds, scenarios}' > "$OUT/$phase/$name.json"     # phase は before か after
diff -r "$OUT/before" "$OUT/after"                                  # 差が空であること
```

### 5.4 k6 inspect に出ない線の前後の比べ（B1・B3）

- 対象: `perf/dsl-mixed-memory.sh` の `memory.peak / memory.max` の線と前の線（`judge`・`judge_090`）、`anon_max_ratio` の線、README の線の表。
- 取り出し: 整理の前と後に、線の値を含む行を `grep` で取り出し、5.3節と同じ外の置き場に保存する。README は、前は今の線の表（`## 性能・資源の目標の見直し` の節）、後は「今の基準の一覧」を対象にする。
- 比べ: 書き方（置き場・表の形）は変わるため、行の文字列ではなく「線の名前と値の組」に直して比べる（直し方はコード生成の計画）。組の集合に差が無いことを判定して記録する。

### 5.5 1 か所との一致（B3）

- `thresholdsFor` から取り出した値の表（5.3節の `thresholds`）と、README の「今の基準の一覧」・`perf/k6/thresholds-test.sh` の期待の表・`perf/dsl-mixed-memory.sh` の値を並べ、一致を判定して記録する。
- 整理の後に `bash perf/k6/thresholds-test.sh` と `bash perf/dsl-mixed-memory-test.sh` を手で流し、終了コード 0 を記録する（どちらも `./gradlew verify` に入っていない）。

### 5.6 README の事後の検索（B4）

| # | 検索 | 合格 | 要件 |
|---|---|---|---|
| Q1 | 5.1節で記録した U4 の節の見出しの文字列 | 見出しごとに 1 件以上 | NFR8.89 |
| Q2 | `evidence/` を含む行のうち、`cp`・`mv`・`>`・`>>`・`tee`・手での編集・貼り付けで直接置く手順 | 0 件（`evidence/` を含む行は U4 の道具を通る行だけ） | NFR8.89 |
| Q3 | README の全体の `eyJ` で始まる 3 区間の形 | 0 件 | NFR8.90 |
| Q4 | `example.com` 以外のメールアドレスの形 | 0 件 | NFR8.90 |
| Q5 | 履歴の章を除いた本文の、Intent の名前（`26` で始まる 6 桁）と日付 | 0 件 | NFR8.90 |
| Q6 | `perf/README.md`・`README.md`・`docker/perf/compose.yaml` の `grafana/otel-lgtm`・`axllent/mailpit` の版とダイジェスト | `compose.yaml` と同じ | NFR9.82 |
| Q7 | `perf/README.md` に `gitleaks dir` | 0 件 | NFR8.90 |

- 記録には、検索の語（正規表現）と件数だけを書き、当たった行の本文を写さない（Q3・Q4 で 0 件でないときも、位置だけを書く）。
- Q2 の判定は、`evidence/` を含む行を一覧にして 1 行ずつ U4 の道具の呼び方（契約 C7）に当たるかで見る。検索の語だけで決めない（コード生成のレビューでも確かめる）。

## 6. 供給網（NFR9.81・NFR9.82）

- この単位では、npm・Gradle の依存も、イメージの版とダイジェストも変えない。作業ブランチの差に、lockfile・`compose.yaml` の `image:` の行・`perf/versions.env`・`docker/perf/compose.yaml` の `image:` の行の変更が無いことを、統合の前に `git diff --stat` とファイルの名前で確かめる。
- 作り直しに使うのは `compose.yaml` の今の値（Mailpit `axllent/mailpit:v1.32.0@sha256:0790692b…`、lgtm `grafana/otel-lgtm:0.38.0@sha256:75cdf892…`）だけ。k6 は `perf/versions.env` の `K6_IMAGE`（`grafana/k6:2.3.0@sha256:9c2dee7f…`）だけ。
- 4.1節の戻しで 0.34.0 を使うのは、手元に残したイメージを一時に指すだけで、`compose.yaml` の固定先を変えない。

## 7. 統合の前の関門（NFR5.81）

- `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を付けた `./gradlew verify` が通ること（カバレッジの下限と除外は変えない）。U4 の `perfEvidenceScan`（`logical-components.md` の LC8）がこの中で流れ、`aidlc/` の下の記録に JWT の形が無いことも確かめる。
- この Bolt は文書・台本のコメント・`aidlc/` だけを変えるため、統合の前に E2E は流さない（`team.md` の Way of Working）。
- 統合は squash（`bolt-plan.md` の B6）。サブモジュールの固定先は変えないため、fast-forward の例外は使わない。

## 8. 要件との対応

| 要件 | 設計（節） | 確かめの記録 |
|---|---|---|
| NFR8.81 | 2.1節 A0 | 了承の日時と答え |
| NFR8.82 | 2.1節 A1〜A6・A8、2.2節、2.4節、2.6節 | 前のコンテナの一覧・止めたコンテナ・照合の判定・healthy とダッシュボードの件数 |
| NFR8.83 | 2.1節 A1・A9、4.2節 | 前と後の `docker image ls` と比べの判定 |
| NFR8.84 | 2.5節、2.7節 | 宛先のドメイン・受信の件数・ヘルスチェック |
| NFR8.85 | 2.1節 A7 | 止めた時刻と一覧に lgtm が無いこと |
| NFR8.86 | 2.3節、3節 | `Config.Env` が無いこと・`gitleaks dir` 0 件 |
| NFR8.87 | 5.3節 | 流し方・場面の数・場面ごとの差の有無 |
| NFR8.88 | 5.4節、5.5節 | 組の差の判定・一致の表・`thresholds-test.sh` の終了コード |
| NFR8.89 | 5.1節、5.2節、5.6節 Q1・Q2 | 見出しの件数・直接置く手順 0 件 |
| NFR8.90 | 5.2節、5.6節 Q3〜Q5・Q7 | 検索の件数・`gitleaks dir` 0 件 |
| NFR9.81 | 6節 | 差にイメージの行と lockfile が無いこと |
| NFR9.82 | 5.2節、5.6節 Q6 | 固定先の一致 |
| NFR5.81 | 7節、5.2節（`scenarios` の形を変えない） | verify の成功・`scenarios` の差が空 |

## 9. 上流との差

- 依頼の文は NFR8.81〜NFR8.89 への対応を求めていたが、NFR 要件には NFR8.90・NFR9.81・NFR9.82・NFR5.81 もあるため、それらも対応の表（8節）と `traceability.json` に入れた。
- NFR8.88 の「整理の後に `bash perf/k6/thresholds-test.sh` が通ること」に、同じく `./gradlew verify` の外にある `perf/dsl-mixed-memory-test.sh` の手での実行を足した（5.5節）。`dsl-mixed-memory.sh` の線を比べの対象にするため、その判定の台本も同じ扱いにした。
- NFR8.87 の「場面ごと」を、`scenarios.js` から取り出した場面の名前の集合とし、前後の集合の差が空であることも確かめる形にした（5.3節）。整理で場面が増減していないことを、閾値の比べと別に見るため。
- NFR8.82 の lgtm の healthy は、`compose.yaml` の lgtm に healthcheck が無いため、Grafana の `/api/health` の応答で確かめる形に決めた（要件は「`State.Health.Status`、無ければ `/api/health`」の両方を許していた）。ダッシュボードの件数の合格の値を、`docker/monitoring/dashboards/` のファイルの数（今は 1）とした。
- 4.1節の戻しで、`compose.yaml` を書き換えずに 0.34.0 を指す形（一時の上書きのファイル）を仮に置いた。要件は「0.34.0 のイメージで lgtm を作り直して戻す」だけを決めていた。
- 5.6節 Q2 に `>>` を足し、判定を検索の語だけでなく行ごとの当てはめで行う形にした。

## 10. 後の段に回すこと

| 段 | 回すこと |
|---|---|
| コード生成（計画） | A0〜A9・B0〜B4 の実際のコマンドの列、記録のファイルの置き場と名前、Mailpit に送る道具（既にあるもの）、場面の名前の集合の取り出し方、線の名前と値の組への直し方、外の置き場の名前、4.1節の戻しの一時のファイルの形、`docker image inspect` で照らす形 |
| コード生成 | A の実行と記録（依頼者の了承の後）、B の整理と前後の比べ、5.6節の検索、`thresholds-test.sh`・`dsl-mixed-memory-test.sh`・`./gradlew verify` の実行。lgtm を戻したときは `code-summary.md` に書く |
| コード生成のレビュー | 5.6節 Q2 の行ごとの当てはめ（`evidence/` へ置く手順が U4 の道具だけであること）、README の履歴の章に新しい値が写されていないこと |
| Build and Test | k6 の新しい場面（選択肢・ピッカーの検索）を、整理した `thresholdsFor` に足して流す（`bolt-plan.md` の引き継ぎ）。そのときも線を足すだけで、今の線の値を変えない |

## Sources

- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u5-container-perf-docs/nfr-requirements/security-requirements.md`・`tech-stack-decisions.md`
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u5-container-perf-docs/nfr-design/nfr-design-questions.md`
- functional-spec: U5 は種類が `packaging` のため機能設計（functional-spec.md）を作っていない。性能・規模・可用性・観測の要件（performance-requirements・scalability-requirements・reliability-requirements・observability-requirements）も種類の対象外で作っていない。振る舞いの根拠は上の要件・Bolt の計画と、読み取りで確かめた設定・文書とした
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`（FR6・FR7）
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/delivery-planning/bolt-plan.md`（B6）
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u4-perf-evidence-redactor/nfr-design/logical-components.md`（LC8・LC10、口の一覧）
- 読み取りで確かめたもの: `compose.yaml`（mailpit・lgtm の `image:`・profile・ボリューム・healthcheck の有無）、`perf/versions.env`（`K6_IMAGE`）、`perf/README.md`（節の並び・`## 性能・資源の目標の見直し` の (c) の `k6 inspect` の形・`evidence` の行が無いこと・固定先の行）、`perf/k6/scenarios.js`（`thresholdsFor`・`options`）、`perf/k6/thresholds-test.sh`、`docker/perf/compose.yaml`・`README.md`（Mailpit の固定先）、`.github/dependabot.yml`（固定先の一覧）、`docker/monitoring/dashboards/`（ファイルの数）
