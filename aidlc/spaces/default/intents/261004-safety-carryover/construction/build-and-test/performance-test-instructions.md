# 性能の試験の手順（Intent 261004-safety-carryover）

この Intent に Performance Validation の段は無いため、ログインの p95（要件 FR2・NFR7）と、フィルターの中の例外の ERROR の件数の実機での確かめ（Code Generation のレビューの R-01）は、この段で流した。手順の元は `perf/README.md`。上流の計画は `aidlc/spaces/default/intents/261004-safety-carryover/construction/code-generation/code-generation-plan.md` の 6.1 節、引き継ぎは `code-summary.md` の 6節、テストの範囲は `unit-test-instructions.md`。

## 条件（前の Intent と同じにそろえるもの）

- イメージ: `mastersmith:safety-carryover`（`develop` の `b084c07` から作った。配備に使うタグ `local` は上書きしない）。
- 使い捨ての環境: `docker/perf/compose.yaml`（プロジェクト `mastersmith-perf`）。コンテナの上限は配備と同じ CPU 4・メモリ 2g。
- 内部DB の悪い側の条件: `perf/README.md` の手順 2（`perf-user01`〜`11`）と手順 2''（`perf-ua-` 1,000 名、`perf-uaop`・`perf-uat`・`perf-uapf` 各 20 名、`perf-uasw-` 100 名とリフレッシュトークン 110,000 行）。利用者は合わせて 1,172 名（前の Intent は `uaop`・`uat`・`uapf` が各 10 名で 1,142 名）。
- 同時のログインの前に、`perf-user01`〜`11`・`perf-uaop01`〜`10`・仮の管理者を1人ずつログインさせて、ロックの状態の行を作る（`project.md` の学び）。
- 場面 `loginSuccess`、`VUS=10`（既定）、`DURATION=60s`。配備したアプリは止める。電源は AC、台本全体を `caffeinate -i` で包む。
- 回ごとに、日時・電源・VM とホストの時計のずれ（下）・サーバー側の `http.server.requests`（`uri:/api/auth/login`）の最大を記録する。

## 判定に使う値（この段で決めたこと）

- k6 の `http_req_duration` は、colima の VM の時計が負荷の間にホストより約 2 秒ずれ、合わせ直されるために一部の値が崩れる（待ちの最小 0 ms、繰り返しより長い値）。そのため、**1回の繰り返しがログインの要求1つだけの `loginSuccess` では、単調な時計で測る `iteration_duration` の p95 を判定に使い**、`http_req_duration` の p95 とサーバー側の最大を並べて記録する。
- 時計のずれは、ホストの時刻の前後で `colima ssh -- date +%s.%N` を読み、`[VM - 終わりのホスト時刻, VM - 始めのホスト時刻]` の範囲として記録する（前後の中間と比べる測り方は ssh の時間が乗るため使わない）。
- 合否は、すべての回で p95 が 1 秒を下回ること（要件 FR2.2）。1回でも 1 秒以上なら、条件をそろえて回数を増やす（R-09）。目標は緩めない。

## 流し方（要点）

```bash
# perf/README.md の手順 0〜2・2'' のとおり用意し、使い捨てのアプリにだけ MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics を渡す
caffeinate -i zsh "$D/login-run.sh" "$D"   # loginSuccess を 60 秒ずつ3回。回ごとに時計のずれとサーバー側の最大を記録
```

## R-01 の確かめ（フィルターの中の例外の ERROR）

- 同じ使い捨ての環境で、使い捨てのアプリにだけ `MASTERSMITH_DB_MAXIMUM_POOL_SIZE=4` を足して起動し直し、`userAdminPoolLimit` を `VUS=12`・`DURATION=2m`・`PERF_UA_COUNT=20` で流す（`perf/README.md` の「接続プールの上限を下げて流す」の短い版。手元の監視は使わない）。
- アプリの標準出力の JSON を `logger` と `traceId` で数える（値は出さない）: Tomcat のロガー（`ContainerBase`・`dispatcherServlet`）の ERROR が 0 行、`ErrorPathController` と `GlobalExceptionHandler` の「想定外のエラーが起きました」が同じ `traceId` で2行以上ある要求が 0。
- あわせて、救済の WARN が 0 件（使い捨ての初期管理者は新しく作られる）、ログの試験用の利用者のメールアドレスが既知の例外（`enteredEmail`）の外で 0 件、監査の `INITIAL_ADMIN_CREATED` が1行であることを数える。

## 片付け

- 結果を確かめてから `down -v` と一時ディレクトリの削除。配備したアプリを `docker compose start app` で戻し、healthy を確かめる。
