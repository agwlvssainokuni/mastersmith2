# 監視の設計 — U5 u5-container-perf-docs（Intent 261029-reference-lookup-cleanup）

- 単位: U5 `u5-container-perf-docs`（種類 `packaging`、Bolt B6）。
- この単位はアプリの振る舞いを変えないため、アプリの指標・警報・SLO を足さず変えない（同じ単位の `infrastructure-design-questions.md`「新しい依存・設定・警報は足さない」）。この文書の対象は、監視の道具そのもの（otel-lgtm）を 0.34.0 から 0.38.0 へそろえるときに、その道具が健全に起きたことをどう確かめるか、と、ボリュームを作り直したときに何が残り何が失われるか、である。
- 手元の監視は見たいときだけ起動する（`project.md` の Deployment）。U5 では確かめの間だけ起動し、確かめの後に止める（security-design.md 2.1節 A7）。常に動かしていないため、SLO の判定は従来どおり `Unverified` のまま（`project.md` の Deployment の学び）で、この単位では変えない。

## 1. Metrics & KPIs

U5 で扱う「指標」は、lgtm と Mailpit が起きたかを見分ける確かめの値だけで、Prometheus に入る指標ではない。

| Metric | Source | Threshold | Why it matters |
|---|---|---|---|
| Grafana の起動 | `127.0.0.1:3000/api/health` の状態コードと `database` の項目 | 時間の上限（仮: 120 秒）までに 200 かつ `database` が `ok` | `compose.yaml` の lgtm に healthcheck が無いため、`docker inspect` の `State.Health.Status` では見分けられない（security-design.md 2.6節） |
| ダッシュボードの読み込み | `docker/monitoring/dashboards/` のファイルごとの `uid`（今は `mastersmith-overview` の 1 つ）を Grafana の検索の API（形は仮: `/api/search?dashboardUIDs=<uid>`、匿名の Viewer）で数える | ファイルごとにちょうど 1 件 | ファイルの差し込み（provisioning）がボリュームの引き継ぎの後も働くことを見る。イメージが自前で持つダッシュボードは合否に入れない（全体の件数は参考の記録だけ） |
| イメージの照合 | `docker inspect --format '{{.Config.Image}}'`・`{{.Image}}` と `docker image inspect --format '{{json .RepoDigests}}'` | タグが `compose.yaml` と同じで、`RepoDigests` のどれかが index のダイジェストで終わる | `team.md` の Deployment（入れ替えた後は毎回 `RepoDigests` を照らす）。判定は全体の文字列で行い、記録は先頭 12 文字でよい（2.4節） |
| Mailpit の受信 | `127.0.0.1:1025` へ `example.com` の宛先で 1 通、`127.0.0.1:8025` の API で目印の語の件数 | 1 以上 | 受け手として作り直せたことを見る（2.5節） |
| VM の空きメモリ | `colima ssh -- free -m` の `available` と `docker stats --no-stream` の名前と使用量 | 基準（仮: 1792 MiB）以上 | lgtm（上限 1536m）を作り直す前の関門（infrastructure-specification.md 4節）。記録には VM の合計（`total`）と A5a の実測値を含め、止めるものが尽きても足りなければ依頼者に諮る |
| アプリの起動 | `/actuator/health` の状態コードと `status`、起動のログの救済の WARN の件数 | 200・`UP` | 確かめの相手が起きていること（2.7節）。起動を省いたときは理由を記録する |

## 2. lgtm の健全性の確かめの流れ

| 順 | 確かめ | 通らないとき |
|---|---|---|
| 1 | イメージの照合（0.38.0 と index のダイジェスト） | `--force-recreate --no-deps` で作り直して確かめ直す。それでも一致しなければ戻し（R1） |
| 2 | `/api/health` が 200・`database` が `ok` | 戻し（R1） |
| 3 | ファイルの `uid` ごとに 1 件 | 戻し（R1） |
| R2・R4 | 戻しの後も同じ 3 つ（照らす正は 0.34.0 のタグと A1 で記録した ID。A1 で `RepoDigests` を記録できていれば、それも併せて照らす。空のときは ID の一致を根拠とし、その旨を記録する。infrastructure-specification.md 2.3節） | R2 で通らなければ R3（ボリュームを作り直す）、R4 で通らなければ R5（止めて記録し諮る） |

- `uid` は実行のときにファイルから読み、この文書の値を写して使わない（ダッシュボードのファイルが増えても同じ形で確かめるため）。
- 応答の本文は記録しない。状態コード・`uid`・件数・判定だけを書く（security-design.md 3.1節・3.2節）。
- 警報の決まりの読み込みの確かめ（17 の決まり、`docker/monitoring/provisioning/alerting/mastersmith.yaml`）は、この単位の合否に入れない。Observability Setup の確かめ（`perf/README.md` の手順）の範囲とする（security-design.md 2.6節）。

## 3. Alerts

| Alert | Condition | Severity | Routes to |
|---|---|---|---|
| （足さない） | — | — | — |

- 既存の 17 の警報の決まり（`docker/monitoring/provisioning/alerting/mastersmith.yaml`）は変えない。版を上げても、リポジトリのファイルを読み込み専用で差し込む形は同じ（`compose.yaml` の lgtm の `volumes`）。
- 通知の先は今も無い（手元で Grafana の画面を見るだけ。`project.md` の Deployment「クラウドの基盤・警報の通知の先は作らない」）。
- 入れ替えの間に lgtm は止まっているか確かめの間だけ動くため、警報が鳴ることは確かめの対象にしない。

## 4. SLIs / SLOs

| SLI | SLO target | Measurement window |
|---|---|---|
| （変えない） | 既存のまま | 既存のまま |

- U5 はアプリの応答・エラーの割合・資源に関わる変更をしないため、SLI・SLO を足さず変えない。手元の監視を常に動かしていない間は判定を `Unverified` とする扱い（`project.md` の Deployment の学び）も変えない。
- perf の合否の線（k6 の `thresholds`・`perf/dsl-mixed-memory.sh` の線）は、整理の前後で値を変えない（FR7.4、security-design.md 5.3〜5.5節）。線の置き場が 1 か所（`thresholdsFor` とその直前の定数の区画）にまとまるだけで、性能の目標の物差しは変わらない（`team.md` の「物差しを変えるのは要件定義で依頼者が明示に決めたときだけ」）。

## 5. Logs & Tracing・ダッシュボード

| 項目 | 0.38.0 へそろえた後 | ボリュームを作り直したとき（R3） |
|---|---|---|
| 指標（Prometheus）・ログ（Loki）・トレース（Tempo）の溜まった値 | 引き継ぐ（ボリュームのまま）。0.38.0 が開いた時点で新しい版の形に変わりうる | 失う（A0 (ii) の了承の範囲。捨ててよいデータ） |
| Grafana の中の状態（画面で一時にいじったダッシュボード・Explore の履歴・警報の状態の履歴など） | 引き継ぐ | 失う（同上。`GF_USERS_VIEWERS_CAN_EDIT` で画面でいじれても保存はできないため、持ち主の手で作ったものは無い見込み） |
| ダッシュボード（`mastersmith-overview`） | ファイルから読み込む | ファイルから読み込み直す（失わない） |
| 警報の決まり（17） | ファイルから読み込む | ファイルから読み込み直す（失わない） |
| アプリのログ・トレースの送り方 | 変えない（外部エクスポートは既定で無効。`.env` で有効にしたときだけ `http://lgtm:4318` へ送る） | 変えない |

- 監視の確かめで使い捨てのアプリを lgtm に送らせるときは lgtm を先に起動する、という前例（`project.md` の学び）は、U5 ではアプリを lgtm に送らせないため当たらない。
- lgtm のログは、戻しで止めたとき（R5）に行数とレベルごとの件数だけを記録し、本文を写さない（security-design.md 4.1節）。
- lgtm が上限 1536m で止められた（または中の部品が落ちた）ときは、`docker inspect` の終了コードと `OOMKilled` で見分けて記録する（R5 の記録の項目）。

## 6. 上流との差

- tech-stack-decisions.md 1節は lgtm の引き継ぎの確かめを「`State.Health.Status`（または `/api/health`）とダッシュボードの件数」としていたが、NFR 設計の承認の場の決定（security-design.md 11節 R-04）のとおり、`/api/health` とファイルの `uid` ごとに 1 件の形にした。この文書はその決定に従う。
- 警報の決まりの数（17）は 2026-10-30 の `docker/monitoring/provisioning/alerting/mastersmith.yaml` の読み取りの値で、上流の文書には書かれていない。数の確かめは合否に入れない。

## 7. 後の段に回すこと

| 段 | 回すこと |
|---|---|
| コード生成（計画） | `/api/health` を待つ時間の上限、ダッシュボードの検索の API の形、VM の空きの基準の値 |
| コード生成 | A5b〜A7・R1〜R5 の実行と記録 |
| Observability Setup（後の段・後の Intent） | 警報の決まりが 0.38.0 で読み込まれ鳴ることの確かめ。R3・R5 のときの手元の監視の確かめ直し |

## Assumptions & Open Questions

None.

## Sources

- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u5-container-perf-docs/nfr-design/security-design.md`（2.1節 A5a〜A7、2.4節・2.6節・2.8節、3節、4.1節、11節）
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u5-container-perf-docs/nfr-requirements/security-requirements.md`・`tech-stack-decisions.md`
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u5-container-perf-docs/infrastructure-design/infrastructure-design-questions.md`
- observability-design・performance-design・scalability-design・reliability-design・logical-components: U5 は種類 `packaging` のため NFR 設計でこれらを作っていない（security-design.md の冒頭）。監視の道具の健全性の確かめは security-design.md 2.6節を正とした
- functional-spec: U5 は種類 `packaging` のため機能設計を作っていない
- components: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/domain-design/components.md`（FR6・FR7 は部品なし）
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`（FR6.4・FR7.4）
- 読み取りで確かめたもの（2026-10-30）: `compose.yaml`（lgtm の `environment`・`volumes`・healthcheck の有無・`mem_limit`）、`docker/monitoring/dashboards/mastersmith-overview.json`（`uid`）、`docker/monitoring/provisioning/alerting/mastersmith.yaml`（決まりの数）、`docker/monitoring/provisioning/dashboards/mastersmith.yaml`
