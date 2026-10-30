## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-10-30T00:47:45Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u5-container-perf-docs/nfr-requirements/security-requirements.md > NFR8.87 | `k6 inspect --include-system-env-vars` の流し方（k6 のイメージを `docker run` で流すのか、ホストの k6 を使うのか）が書かれていない。ホストの k6 を使うと、シェルの環境変数が出力に入りうる。コンテナ内の環境変数（HOSTNAME など）が出力に入ると、前後の差が空にならず誤って不一致になる。`-e` で渡す値だけに絞るという要件は、流し方が決まらないと守れるか確かめられない | 流し方を `perf/versions.env` の `K6_IMAGE` を使った `docker run`（`--env-file` なし・ホストの環境を継承しない）に固定すると書く。差を取る対象を、出力のうち閾値と場面の定義の部分に限る旨も書く | Resolved (reviewer) |
| R-02 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u5-container-perf-docs/nfr-requirements/security-requirements.md > NFR8.87・NFR8.88 | 合否の線の前後比較の自動の確かめは `k6 inspect`（`scenarios.js` の閾値）だけを対象にしている。README の「今の基準の一覧」・`thresholds-test.sh`・`dsl-mixed-memory.sh`（memory.peak の線）の値との一致は、手で表を作って比べる確かめになっている。memory.peak の線は `k6 inspect` に出ないため、整理で緩む・取り違えるおそれ（T7）が機械的には確かめられない | `dsl-mixed-memory.sh` と README の線の値を、整理の前後で `grep` などで取り出して差を取る手順を NFR8.88 に加える。または手で比べる範囲であることを明記する | Resolved (reviewer) |
| R-03 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u5-container-perf-docs/nfr-requirements/security-requirements.md > NFR8.83 | 戻し先の 0.34.0 の lgtm のイメージを残す要件だが、作り直しの前にそのイメージが手元にあることを確かめる手順が無い。作り直しの後の `docker image ls` で初めて無いと分かると、戻し先が元から無かったのか消えたのか見分けられない | 作り直しの前にも `docker image ls grafana/otel-lgtm` の結果を記録し、前後を比べる | Resolved (reviewer) |
| R-04 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u5-container-perf-docs/nfr-requirements/security-requirements.md > NFR8.82・NFR8.85（T11） | lgtm を `--force-recreate` で作り直すと起動し、`compose.yaml` では上限 1536m で、VM の余裕は約 100 MiB（`project.md` の記録）。確かめの間は起動したままなので、同時に動くコンテナ（アプリ・対象DB・Mailpit）の状況によっては VM のメモリ不足になりうる。また名前付きボリューム `mastersmith-monitoring` を 0.34.0 から 0.38.0 へ引き継ぐが、その互換と、データが使えなかったときの扱いの記述が無い | 入れ替えの前に動いているコンテナを確かめ、必要なら対象DB などを止めてから lgtm を作り直す順序を決める。ボリュームの互換は、起動が healthy になること・ダッシュボードが読み込まれることまで確かめ、失敗したときの戻し（0.34.0 に戻す）を一言書く | Resolved (reviewer) |
| R-05 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u5-container-perf-docs/nfr-requirements/security-requirements.md > NFR8.89 | U4 が書く伏せ字の節が整理後の README に意味を変えずに残るかの確かめが「Code Generation のレビューで確かめる」だけで、機械的な確かめが無い。T8 の「`cp` で `evidence/` に置く手順が戻る」は検索で確かめられる | 整理の後に README を検索し、`evidence/` へ置く手順に `cp` などが無いこと、伏せ字の節の見出しが残ることを確かめて記録する | Resolved (reviewer) |
| R-06 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u5-container-perf-docs/nfr-requirements/security-requirements.md > NFR8.84 | 「アプリの起動確認が通る」とあるが、入れ替えの時点でアプリのコンテナが動いている前提かどうかが書かれていない（`--no-deps` で触れないだけで、止まっていることもある）。止まっていると確かめが成り立たない | アプリが動いていない場合の扱い（起動して確かめる・確かめを省略して理由を記録する）を決める | Resolved (reviewer) |

> R-01 Reviewer note: NFR8.87 と tech-stack-decisions.md 1節が、`perf/versions.env` の `K6_IMAGE` を使った `docker run --rm`（`--env-file` なし・ホストの k6 を使わない）に固定し、差を閾値と場面の定義の部分だけに限っている。`K6_IMAGE` は `perf/versions.env` を読む定義として perf/README.md と `perf/dsl-mixed-memory.sh` にあることを確かめた。

> R-02 Reviewer note: NFR8.88 が、`dsl-mixed-memory.sh` の線と README の線の表を `grep` で取り出して外の置き場に保存し、前後の差を線の名前と値の組で取る手順になっている。

> R-03 Reviewer note: NFR8.83 が、作り直しの前後に `docker image ls grafana/otel-lgtm`（と mailpit）を記録して比べ、前に 0.34.0 が無いときは依頼者に諮る手順になっている。

> R-04 Reviewer note: NFR8.82 が、入れ替えの前の `docker ps` の記録、lgtm を作り直す前に要らないコンテナを止めること、healthy とダッシュボードの件数によるボリュームの確かめ、失敗したときの 0.34.0 への戻しを書いている。

> R-05 Reviewer note: NFR8.89 が、整理の後に README を検索して見出しの残りと `evidence/` へ直接置く行の 0 件を件数で記録することを求めている。

> R-06 Reviewer note: NFR8.84 が、アプリが止まっているときは `docker compose up -d app` で起動して確かめて元に戻すか、省く理由を記録することを書いている。

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| なし（この単位に検証ツールの指定は無い） | 該当なし | `K6_IMAGE` の定義の存在だけを perf/ の検索で確かめた。 |

### Summary

前回の 6 件の指摘はすべて要件の文面に反映され、新しい指摘は無い。packaging の単位として、手順を読んだ開発者が推測なしに実行できる。
