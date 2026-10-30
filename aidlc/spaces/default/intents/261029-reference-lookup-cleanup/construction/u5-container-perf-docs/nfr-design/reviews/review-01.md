## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-10-30T04:12:35Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u5-container-perf-docs/nfr-design/security-design.md > 4.1節 lgtm の戻し・2.6節 | 戻し先 0.34.0 は「イメージが手元にある」ことしか担保していない。A5 で 0.38.0 が名前付きボリューム `mastersmith-monitoring`（`compose.yaml` の `/data`）を開いた時点で Grafana の DB・Prometheus・Loki・Tempo のデータが新しい版の形に更新されうる。そのため、A6 が通らない状況（起動しない・ダッシュボードが読めない）では、同じボリュームで 0.34.0 が起動する保証が無い。ボリュームの退避も、戻した後に起動できなかった場合の手（ボリュームを作り直す・捨ててよい）も書かれていない。また 4.1 の戻しは、戻した後の `/api/health` の確かめが通らなかったときの扱いを持たない | 作り直しの前にボリュームの退避をするか、「監視のデータは捨ててよく、ファイルで置いたダッシュボードと警報の決まりから作り直せる」ことを依頼者の了承（A0 に含める）として明記し、戻しの手順に「0.34.0 が既存のボリュームで起動しないときはボリュームを作り直す」手を足す。戻し後の確かめが通らない場合の止め方と記録も決める | New |
| R-02 | Major | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u5-container-perf-docs/nfr-design/security-design.md > 5.2節（履歴の章）・5.6節 Q5・5.3節〜5.5節 | `perf/README.md` は 1380 行超で、本文の見出しの大半が Intent の名前を含む節（「業務データの場面（Intent 261016-business-data-views の U1）」など）で、冒頭の 1 段落目と「手順」にも `260922-auth-audit-base` や `261004-role-menu` が出る（`Intent 26` の出現は 43 件）。Q5「履歴の章を除いた本文に `26` で始まる 6 桁と日付が 0 件」を満たすには、どの節を「今の基準と手順」に残し、どれを「履歴」へ移すかの分類を決める必要があるが、設計に分類の基準が無い。さらに、前後の機械的な確かめは線の値（`thresholds`・`scenarios`・線の名前と値の組）だけで、移す文・手順・コマンドが欠けず変わらないことを確かめる手段が無い（移す中身は「今の README にある文と値だけ」とあるが、行の過不足の突き合わせが無い）。手順の本文が整理で落ちても、5.6節の検索は通る | 分類の基準（例: 場面ごとの手順は現役、結果の文と測った値は履歴）と、Intent の名前を手順の本文からどう外すか（節の見出しの書き換え方）を設計に足す。あわせて、整理の前後で「履歴へ移した行＋本文に残した行」が元の行の集合を欠かさず覆うことを突き合わせる確かめ（行の集合の差）を B3〜B4 に足す | New |
| R-03 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u5-container-perf-docs/nfr-design/security-design.md > 5.6節 Q7 | Q7 は「`perf/README.md` に `gitleaks dir` を検索して 0 件」と読め、そのまま実装すると、U4 が書く節（`logical-components.md` の LC10 は「置いた後の検査と `gitleaks dir`」を含む）の語と Q1・NFR8.89 の「意味を変えず収める」と矛盾する。意図は「README の中身に `gitleaks dir` を流して 0 件」と推測できるが、表の書き方は曖昧 | Q7 を「README に `gitleaks dir` を流した結果が 0 件」と書き直す（語の検索ではないことを明記） | New |
| R-04 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u5-container-perf-docs/nfr-design/security-design.md > 2.6節 ダッシュボードの合格 | 合格を「`/api/search` の件数が `docker/monitoring/dashboards/` のファイルの数（今は 1）と同じ」としているが、`grafana/otel-lgtm` のイメージが自前でダッシュボードを持つかは確かめられていない。持つ場合、件数は 1 にならず、正しく動いていても不合格になり、不要な戻しが起きる | 件数ではなく、ファイルのダッシュボードのタイトルまたは uid で検索して 1 件あることを合格にする。または 0.34.0 の時点の件数を A1 で取り、同じ件数であることを合格にする | New |
| R-05 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u5-container-perf-docs/nfr-design/security-design.md > 2.1節 A1・A2・A8、2.7節 | (1) A1 の `docker ps` は「`-a` を付けると」と任意の書き方だが、A8 の「A1 で止まっていたものは起こさない」は `-a` の記録を前提にする。(2) A2 の後に VM の空きメモリを実際に確かめる関門が無く（`project.md` では余裕は約 100 MiB）、lgtm（上限 1536m）の作り直しが VM のメモリの不足で失敗または他のコンテナを巻き込むおそれがある。(3) 2.7節の既定「止まっていたアプリを `up -d app` で起動する」は、起動のたびの初期管理者の救済と監査の記録（`project.md` の Deployment に前例）で内部DB のデータを変えうるが、記録・了承の項目に入っていない | (1) A1 は `docker ps -a` を必須にする。(2) A5 の前に VM のメモリの空きを読み取りで確かめ、足りなければ止めるコンテナを増やす手を足す。(3) アプリを起動する既定を A0 の了承の対象に含めるか、起動を省く側を既定にする | New |
| R-06 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u5-container-perf-docs/nfr-design/security-design.md > 5.2節（`scenarios.js` の行）・5.5節 | 5.2 は「合否の線の定義を `thresholdsFor` の 1 か所に集める」とするが、`scenarios.js` では p95 の上限の表 `BIZ_P95_LIMIT_MS` が `thresholdsFor` の外の定数（415〜416 行）にあり、`thresholdsFor` 自身も場面ごとの分岐が多数ある。「1 か所」が関数だけを指すのか定数も含むのかが決まっていない。また `thresholds-test.sh` の期待の表は業務データ 7 場面だけで、5.5 の「一致」の対象が全場面なのか 7 場面なのかが読めない | 「1 か所」の範囲（`BIZ_P95_LIMIT_MS` を関数の中へ移すか、隣に置いて参照するか）を決め、5.5 の一致の対象（k6 inspect の全場面と、期待の表の 7 場面の両方）を明記する。移したあとの `thresholds` の前後の差が空であることは、既に 5.3 で確かめる形になっている | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| 読み取りによる突き合わせ（`compose.yaml` の mailpit・lgtm の `image:`・profile・ボリューム・healthcheck の有無・`perf/versions.env`・`perf/k6/scenarios.js`・`perf/k6/thresholds-test.sh`・`perf/README.md`） | 設計の記述と一致。lgtm に healthcheck が無いこと、profile 名、`K6_IMAGE` の形、`thresholdsFor` が `options.thresholds` から呼ばれていること、README に `evidence` を含む行がまだ無いこと（U4 の後でなければ関門 B0 が通らない）を確認した | A（入れ替え）の手順の骨格は `team.md` の Deployment（`docker inspect` の `RepoDigests` と `compose.yaml` の照合、`--force-recreate --no-deps`）と `project.md` の学びに沿っている。`.env` の値は表示も記録もしない項目に限る取り出しになっている。`traceability.json` の 13 件は NFR 要件の ID と過不足なく一致 |
| 実行系の検証（docker・gradle・k6 など） | 実行していない（この確認の範囲外） | 手順の実行可能性（特に 0.34.0 への戻し）は設計の確認だけで、コード生成で確かめる必要がある |

### Summary

入れ替えの順序（了承の取得を最初の関門にする・index のダイジェストで照合する・`--force-recreate --no-deps`・`.env` の値を出さず取り出す項目を名前で限る・確かめた後に lgtm を止める）は `team.md` の決まりに沿っていて、実装できる形に整っている。U4 の README の節への依存も B0 の関門で押さえられている。承認の前に重く見てほしいのは、(1) 0.34.0 への戻しが、ボリュームが新しい版の形に変わった後でも成り立つかを担保していない点と、(2) README の整理で「現役の手順」と「履歴」の分け方、および欠落のない移し替えの確かめが決まっていない点。どちらも Major だが数が 2 件のため READY とし、修正はこの承認の場の Request Changes で依頼者が判断する（助言のみ）。
