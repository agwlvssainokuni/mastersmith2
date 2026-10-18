# 監視の設計 — U3 u3-businessdata-screens

U3 は画面だけの単位で、サーバーの指標・警報・ダッシュボードの区画は足さない。画面の誤りはサーバーのログに出ない（U1 の `monitoring-design.md` 8節）。業務データの API の指標と警報は U1・U2 と Observability Setup が持つ。この文書は、画面と E2E の環境で記録するものと、その物差しを書く。

手元の監視（`grafana/otel-lgtm`）を常に動かしていない間は、SLO の判定を `Unverified` とし、測った時点の値を並べる（project.md の学び）。

## 1. Metrics & KPIs

| Metric | Source | Threshold | Why it matters |
|---|---|---|---|
| 画面の時間（判定が真になった後の次の rAF まで） | E2E 185 の中の測り（`frontend/e2e/support/businessDataMeasure.ts`、既定の1組・各 5 回の中央値） | U3-NFR1.7・1.8 の目標。中央値で判定し、最大と1回目も並べる | 100 カラムの表とフォームが待たせないこと。記録だけで、E2E の成否にしない |
| 参考の「塗りまで」（その次の rAF まで） | 同じ記録 | 判定に使わない | F1: A。物差しは変えない |
| E2E の全体の時間 | `frontend/test-results/e2e-results.json` の duration と始めと終わりの時刻 | 基準（232 件・10分35秒）の 1.5 倍（約 15分53秒）以下 | 超えたら 185 の組み立てを諮る（NFR 要件 Q5: A）。上限で落とさない |
| 180・185 の時間 | 同じ json のファイルごとの duration | 記録だけ | 全体が超えたときの判断の材料 |
| 準備・片付けの時間 | Gradle の `e2eTest` の準備と片付けのタスクの所要 | 記録だけ（数秒の見込み） | 見本の PG への `docker compose exec` の上乗せ |
| bundle の大きさ | `frontend/scripts/check-bundle-size.mjs` の入口の gzip と、画面ごとの chunk の gzip | 入口の今の上限のまま | 3つの画面を別々の `lazy` にした効果（U3-NFR1.10） |
| 準備・片付けの件数 | 準備と片付けのタスクのログ（名前と件数だけ） | 片付けの後に `e2e_biz_*` と `mastersmith_e2e_*` が 0 件 | 見本の PG に E2E の残りを置かない |
| 見本の PG のメモリ | E2E の後の `docker stats --no-stream`（`mem_limit` 512m） | 記録だけ | 180 で見本の PG が詰まらないこと |
| 秘密の検索 | `frontend/playwright-secret-check-reporter.ts` | 見つかった件数 0 | 一時のロールの値と業務データの値が報告に残らないこと（U3-NFR4.25・4.2） |
| axe の違反 | E2E 185（20 組＋200% の2件）と vitest-axe | 違反 0 件 | U3-NFR5.6〜5.8。規則・要素は外さない |

## 2. Alerts

| Alert | Condition | Severity | Routes to |
|---|---|---|---|
| （足さない） | — | — | — |

- 業務データの API の 403 は、既存の警報（`ms-forbidden` など、`docker/monitoring/provisioning/alerting/mastersmith.yaml`）が拾う見込みである。U3 の画面は 403 の後に API を送り直さない（U3-NFR8.7）。拾うかは Observability Setup で実測する。
- 警報の通知の先は、配備先が決まるまで作らない（project.md）。

## 3. SLIs / SLOs

| SLI | SLO target | Measurement window |
|---|---|---|
| 画面の時間の中央値 | U3-NFR1.7・1.8 の目標以下 | B5・B6・Build and Test の E2E 185 の各 5 回 |
| E2E の全体の時間 | 基準の 1.5 倍以下 | B5・B6・B7・Build and Test の E2E の全件（U4 の NFR7.2 と同じ回） |
| 秘密の検索の件数 | 0 | E2E の全件の各回 |
| axe の違反の件数 | 0 | E2E 185 と vitest-axe の各回 |
| 片付けの後の残りの件数 | 0 | E2E の全件の各回 |

測る回はすべて `caffeinate -dims` で包み、流した後の `pmset -g log` に Sleep・DarkWake があれば、結果を使わずに流し直す（project.md の学び）。

## 4. Logs & Tracing

| 項目 | 扱い |
|---|---|
| 画面のログ | `console` に主キー・行の値・検索の値を出さない（U3-NFR4.2）。画面のログをサーバーへ送る仕組みは無い |
| 準備・片付けのログ | Gradle のログに、スキーマとロールの名前と件数だけを出す。パスワード・接続先の値・行の値は出さない |
| SQL の値の渡し方 | 一時のロールのパスワードは psql の標準入力（`\set`）で渡し、コマンド行（argv）に入れない |
| E2E の WAR のログ | `webServer.stdout` は今のまま `ignore`、`stderr` は `pipe` |
| トレース | Playwright の trace は既定で off（`frontend/e2e/support/traceMode.ts`）。サーバーのトレースの属性は U1・U2 が受け持つ（道に主キーを載せない、CQ1: A） |
| ダッシュボード | 足さない。業務データの区画は U1・U2 と Observability Setup が持つ |

## 上流との差

承認済みの文書は書き換えず、差をここに書く（project.md の決まり）。

1. **Q3: A と承認の場の決定 U3 R-01 の文面との差**: 決定は管理者の資格情報をプロセスの環境変数で渡すとした。この単位では管理者のパスワードをプロセスに入れない。秘密の検索の対象（1節）は一時の2つのロールの値になり、管理者の値は入らない。
2. **040 の書き換え**: 040 の照合の警告の期待が `TARGET_UNCONFIGURED` から `SCHEMA_MISMATCH` に替わる（Q2: A）。監視の物差しには響かない。
3. **U1 の「見本の対象DB は変えない」を上乗せで満たす**: 見本の PG のメモリの記録は、上乗せのファイルで起動したコンテナで取る。`compose.yaml` の値（`mem_limit` 512m）は変えない。
4. **U2 の R-01 にこの単位で答える**: 届く道・管理者の資格情報・040 の両立は `infrastructure-specification.md` で答える。監視には片付けの件数の物差しを足した。

## 後の段に回すこと

- **B5 の計画**: bundle の大きさの前と後の記録、185 の測りの部品の名前。
- **B6 の計画**: 準備・片付けのタスクのログの形（名前と件数だけ）、秘密の検索に足す値の種類。
- **B6 で確かめること**
  - 外からの接続が scram になり、誤ったパスワードで拒まれること
  - 片付けの後の残りが 0 件になること
  - 040・150・170・180 が通ること
  - E2E の全体・180・185 の時間と、見本の PG の `docker stats` の値
- **Observability Setup**: 業務データの API の 403 を既存の警報が拾うかの実測（U3-NFR8.7）。

## U1・U2・U4 とそろえる点

- **U1**: 画面の時間の目標は、U1 の Performance Validation の API の時間を前提にする。業務データの API の指標と区画は U1 が持つ。
- **U2**: 書き込みの API の時間（p95 1 秒）を前提にする。書き込みの失敗のログは U2 のキーのまま。
- **U4**: E2E の全体の時間と axe の違反の件数は、U4 の NFR7.2 と同じ回で記録し、180・185 の分を分けて書く。表示の組は `frontend/e2e/support/displayCombos.ts` の 20 組。

## 未確認の点

- **外からの接続が scram になること**: 見本の PG の中の `pg_hba.conf` は `127.0.0.1/32` を `trust` にしている。公開の番号から来る接続は `scram-sha-256` に当たる見込みだが、確かめていない。B6 で確かめる。
- **`DROP ROLE` が止まるか**: 接続が残っているときの `DROP ROLE` の振る舞いは確かめていない。片付けの件数の物差し（0 件）で、止まったことに気づく。
- **185 の時間**: ファイルごとの基準の値が無く、見積もれていない。B5・B6 の実測で判断する。

## Sources

- この段の答え: `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/infrastructure-design/infrastructure-design-questions.md`（Q1〜Q7: A、まとめの確認: Looks correct）
- 上流（どれも `aidlc/spaces/default/intents/261016-business-data-views/` の下）
  - `construction/u3-businessdata-screens/nfr-requirements/performance-requirements.md`（NFR1.7・1.8・1.10・7.8）・`security-requirements.md`（NFR4.2・4.25・5.6〜5.8・8.7）
  - `construction/u3-businessdata-screens/nfr-design/performance-design.md`（4節・5節・6節）・`security-design.md`（7節・8節）
  - `construction/u1-businessdata-read/infrastructure-design/monitoring-design.md`（8節）
  - `construction/u4-a11y-carryover/infrastructure-design/cicd-pipeline.md`（4節・10節）
  - `aidlc/spaces/default/intents/261015-deps-ui-carryover/construction/build-and-test/test-results.md`（2節。232 件・21:28:43Z〜21:39:18Z）
- 既にあるもの（読むだけ）: `frontend/playwright.config.ts`（98〜122行）、`frontend/playwright-secret-check-reporter.ts`、`frontend/e2e/support/traceMode.ts`、`docker/monitoring/provisioning/alerting/mastersmith.yaml`

## Assumptions & Open Questions

- 前提: E2E の全体の時間は、準備・片付けの上乗せを含めて測る。
- 前提: 画面の時間の値は PC の1台・headless の Chromium でのもので、記録だけに使う。
- 未確認: 「未確認の点」の3つ。
- 諮る点は無い。E2E の全体が基準の 1.5 倍を超えたときだけ、その時点で諮る。
