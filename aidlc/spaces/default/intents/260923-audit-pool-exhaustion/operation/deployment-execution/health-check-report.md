# 健全性の確かめの報告（health-check-report）

## 1. 配備した環境

| 項目 | 結果 |
|---|---|
| コンテナ | `mastersmith-app-1`（イメージ `mastersmith:local`、版 `3287050`） |
| ヘルスチェック | `healthy`。起動から約 8 秒（06:12:43 → 06:12:51 UTC）。監査イベントの確認のための再起動の後も約 7 秒で `healthy` |
| `/actuator/health` | `{"status":"UP"}` |
| メモリ | 287.6MiB / 1GiB（配備の直後）。上限 `mem_limit: 1g` は変えていない（NFR2） |
| CPU の上限 | 2（`.env` の `MASTERSMITH_CONTAINER_CPUS`。colima の VM が CPU 2 のため） |
| 接続プールの設定 | WAR の `application.yaml` が `${MASTERSMITH_DB_MAXIMUM_POOL_SIZE:30}`、コンテナにその環境変数は無い。よって上限は 30 |
| 起動のログ | ERROR 0 件。WARN は Flyway と H2 の版の組み合わせなど、前から出ている起動の時のもの |

## 2. 負荷の確かめ（FR6.2・NFR2、配備の前に使い捨ての環境で実施）

`perf/README.md` の手順で、使い捨ての環境（`docker/perf/compose.yaml`、プロジェクト `mastersmith-perf`、仮の署名鍵・仮の利用者 10 名、CPU 2、メモリ 1g）を作り、配備するのと同じイメージで行った。配備したアプリは止めておいた。

| 項目 | 値 |
|---|---|
| 場面 | `loginSuccess`（`constant-vus`、仮想の利用者 10、考える時間なし）、60 秒（06:10:44〜06:11:46 UTC） |
| ログイン | 400 件、すべて 200（`http_req_failed` 0%） |
| 応答の時間 | 平均 1.52 秒、中央 1.50 秒、p95 1.60 秒、p99 2.03 秒、**最大 2.06 秒**（前の Intent の F2 では、最初の同時 10 件が約 7.2 秒） |
| 監査の `LOGIN_SUCCEEDED` の行 | **400 件**（仮の利用者の分。ログインの数と一致）。ほかの種類の行は無い |
| 「Connection is not available」 | 0 件 |
| ERROR「監査イベントの記録に失敗しました」 | 0 件（ERROR は全体で 0 件） |
| メモリ | 353.8MiB / 1GiB（負荷の後） |
| OOMKilled | false（止めたときの終了コード 143 は、停止の合図によるもの） |

判定: **合格**。要件 FR6.2 の基準（ログインの数と監査の行の数の一致、時間切れと監査の失敗のログが0件）を満たし、NFR2（1g の中で動く）も満たした。

- 応答の時間の p95 1.60 秒は、前の Intent の性能の目標（1 秒）を超える。これは前の Intent の F4（CPU 2 ではログインが遅い）と同じ値で、今回の修正の範囲の外である。
- 使い捨ての環境のログの WARN 1 件（4xx の応答への変換）は、確かめの直前に AI が送った指標の API への要求（404）によるもので、k6 の要求によるものではない。
- 確かめの後、使い捨ての環境を `down -v` で消し、一時の環境ファイルも消した。k6 の結果は `build/perf-results/loginSuccess.json`（Git 管理外）に残した。

## 3. 目標の判定のまとめ

| 目標 | 判定 |
|---|---|
| FR6.1 配備した後のスモークテスト | Met（`smoke-test-results.md`） |
| FR6.2 k6 の同時 10 件のログインで監査が欠けない | Met（2節） |
| NFR2 1g の中で起動・スモークテスト・FR6.2 が通る | Met（1・2節） |

## Sources

- `docker compose ps`・`docker inspect`・`docker stats`・`docker logs` の出力
- k6 の出力と `build/perf-results/loginSuccess.json`
- 使い捨ての環境の監査イベントの表の読み取り（行の数）
- `aidlc/spaces/default/intents/260923-audit-pool-exhaustion/operation/deployment-pipeline/deployment-strategy.md` 2節（合格の基準）、`perf/README.md`

## Assumptions & Open Questions

- None.
