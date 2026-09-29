# 性能と監視の確かめの手順（Intent 260928-quality-followup）

この Intent に応答時間の新しい目標は無い。性能に関わるのは、p95 の警報3件を働かせる直し（FR4）だけである。コード生成の計画（`aidlc/spaces/default/intents/260928-quality-followup/construction/code-generation/code-generation-plan.md` の Step 7）と、この段の要件の未決の点 O3（Q1: A）の確かめの手順を書く。k6 の負荷の試験は行わない（`code-summary.md` の変更は応答の処理を変えていない）。

## 使い捨ての環境（配備した環境とは別。`project.md` の Testing Posture）

1. colima の VM のメモリのため、配備したアプリを `docker compose stop app` で止める（止める前に依頼者に伝える）。
2. ホームの下に権限 700 の一時ディレクトリを作り、仮の署名鍵・仮の管理者（パスワードは乱数、表示しない）・外部エクスポートの有効化（送り先 `http://lgtm:4318`）を書いた環境ファイル（権限 600）と、アプリ（今のソースから作った別のタグのイメージ）と `grafana/otel-lgtm:0.34.0` だけの compose ファイルを置く。
3. 警報の決まりは、リポジトリの `docker/monitoring/provisioning/alerting/mastersmith.yaml` の写しを一時ディレクトリに作り、確かめる警報のしきい値だけを変えて読み込ませる。リポジトリのファイルは変えない。
4. ログイン・トークンの更新・`/api/admin/check` を約 5 秒ごとに約 9 分送る（`for: 5m` を超え、指標の送信の周期 1 分を複数またぐため）。
5. Grafana の `api/prometheus/grafana/api/v1/rules` で警報の状態を、`api/datasources/proxy/uid/prometheus/api/v1/query` で式の値を確かめる。
6. 結果を見てから `down -v` と一時ディレクトリ・イメージの削除で片付け、配備したアプリを `docker compose start app` で起動し直し、healthy を確かめる。

## 注意

- macOS の `/bin/bash` は 3.2 で、連想配列（`declare -A`）が使えない。台本は結果を1行ずつファイルに書いて `sort | uniq -c` で数える。
- 警報のしきい値は警報ごとに違う（ログイン・トークンの更新は 1000 ms、`ms-check-p95` は 300 ms）。書く前に警報の決まりのファイルで確かめる。
