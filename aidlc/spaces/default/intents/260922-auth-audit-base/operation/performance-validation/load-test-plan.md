# 負荷の試験の計画（load-test-plan）

Intent `260922-auth-audit-base`（auth-audit-foundation）の性能の目標を確かめる負荷の試験の計画。目標と測り方は U1〜U4 の `construction/<単位>/nfr-requirements/performance-requirements.md`（performance-requirements）・`scalability-requirements.md`（scalability-requirements）、予算と見積もりは `construction/<単位>/nfr-design/performance-design.md`（performance-design）・`scalability-design.md`（scalability-design）、応答時間の見方は `operation/observability-setup/dashboards.md`（dashboards）にある。決定は `performance-validation-questions.md`（Q1〜Q4）にある。

## 1. 対象の目標（この段が持ち主の 11 件）

| ID | 目標 | 測り方（要件の記述） | この計画での測り方 |
|---|---|---|---|
| U1-NFR1.1 | ヘルスチェックは 95% が 500ms 以内 | 同時 10 件を繰り返し、95 パーセンタイル | 場面 health |
| U1-NFR1.3 | 共通の処理が加える時間は 95% で 20ms 以内 | 何もしない API と通さない場合の差 | 場面 health の全体の 95 パーセンタイルを上限とする（Q4） |
| U2-NFR1.1 | ログインは同時 10 件で 95% が 1 秒以内（成功・失敗とも） | 負荷の試験 | 場面 loginSuccess・loginFailure |
| U2-NFR1.2 | トークンの更新は同時 10 件で 95% が 1 秒以内 | 負荷の試験 | 場面 refresh |
| U2-NFR1.3 | 照合（bcrypt）は 1 回 100〜500ms | 照合の時間を測る | 起動のログ（「パスワードの照合の時間を測りました」の `elapsedMs`）と、ログインの応答時間 |
| U2-NFR1.4 | 認証の処理が加える時間は 95% で 50ms 以内 | 何もしない認証つきの API | 場面 adminCheck の全体の 95 パーセンタイルを上限とする（Q4） |
| U2-NFR1.6 | 想定の規模（利用者 50 名、同時ログイン 10 名）を1台で | 負荷の試験（NFR1.1・1.2） | loginSuccess・refresh の結果と、失敗の有無 |
| U3-NFR1.1 | 確認用 API は同時 10 件で 95% が 300ms 以内（成功・403 とも） | 負荷の試験 | 場面 adminCheck・forbidden |
| U3-NFR1.3 | 拒否の記録で 401／403 を 100ms 以上遅らせない | 拒否の応答時間 | 場面 forbidden の全体の 95 パーセンタイルを上限とする（Q4） |
| U4-NFR1.1 | 監査の書き込みは同時 10 件で 95% が 50ms 以内 | 負荷の試験の中で書き込みの時間 | 別の回で監査の受け取りの部品のログを TRACE にし、入る・出るの時刻の差を上限として測る（Q4） |
| U4-NFR1.2 | 監査の書き込みが呼び出し元の予算に収まる（最も余裕の少ない経路は拒否の経路） | U2 の試験と、管理者でない利用者の 403 を同時 10 件 | 場面 forbidden（1件ごとに監査の書き込みがある）と loginSuccess、コネクションプールの待ち |

## 2. 環境（Q2・Q3）

| 項目 | 値 |
|---|---|
| 置き場所 | 使い捨ての環境。`docker/perf/compose.yaml`（プロジェクト名 `mastersmith-perf`、ボリューム `mastersmith-perf_perf-data`、`127.0.0.1:18080`） |
| アプリ | 配備と同じイメージ `mastersmith:local`（版 `7040876` の WAR）、同じ設定（メモリ 1GB、JVM の最大ヒープ 75%、タイムゾーン） |
| CPU の上限 | **2**（この PC の colima の VM が CPU 2 のため。設計の値は 4。Q3） |
| 秘密情報 | 仮の署名鍵・仮の管理者・試験用の利用者のパスワードは、リポジトリの外の一時の環境ファイルに乱数で作る。値は表示・記録しない。終わったら消す |
| 試験用の利用者 | 管理者 1 名（仮の初期管理者）、管理者でない利用者 10 名（`perf-user01〜10@example.test`。内部DBに直接入れた。パスワードのハッシュは bcrypt cost 12） |
| 配備した環境 | 資源を取り合わないよう試験の間は止め、終わったら起動し直す。データと監査ログには触れない |
| 負荷をかける側 | `grafana/k6:2.3.0` のコンテナを、使い捨ての環境と同じ Docker のネットワークから動かす（`http://app:8080`） |

## 3. 場面（`perf/k6/scenarios.js`）

どれも同時 10（仮想の利用者 10）で 60 秒、考える時間を置かずに繰り返す（閉じた負荷。要件の「同時 10 件を繰り返し」）。

| 場面 | 要求 | 合格の確認 |
|---|---|---|
| health | `GET /actuator/health` | 200 |
| loginSuccess | `POST /api/auth/login`（利用者 1〜10 を仮想の利用者ごとに固定。同じ利用者のログインは1つずつ行う仕組み（U2 の NFR1.5）のため、別々の利用者にする） | 200 |
| loginFailure | `POST /api/auth/login`（毎回ちがう存在しないメールアドレス。ロックを起こさない） | 401 |
| refresh | 仮想の利用者ごとに1回ログインした後、`POST /api/auth/session/refresh` を繰り返す（Cookie は毎回の応答のものに入れ替える） | 200 |
| adminCheck | 管理者のアクセストークンで `GET /api/admin/check` | 204 |
| forbidden | 管理者でない利用者のアクセストークンで `GET /api/admin/check`（毎回、アクセス拒否の監査イベントを書く） | 403 |

監査の書き込みの時間（U4-NFR1.1）は、別の回で使い捨ての環境を `LOGGING_LEVEL_CHERRY_MASTERSMITH_AUDIT_SERVICE=TRACE` で起動し直し、loginSuccess と forbidden を流して、`AuditEventListener` の受け取りの処理の入る・出るの時刻の差を集める（組み立て＋別のトランザクションの開始から確定までを含む上限）。TRACE は応答時間を遅くするため、応答時間の判定には使わない。

## 4. 判定

- 95 パーセンタイルが目標以内なら Met、超えたら Not Met。近い方法（上限）で測った目標は、上限が目標以内なら Met、超えたら Unverified（Q4）。
- 目標は緩めない。CPU の上限 2 での結果として記録し、設計の値 4 での結果ではないことを明記する。
- 応答の失敗（想定と違う状態コード）があれば、その割合も記録する。

## 5. 片付け

使い捨ての環境のコンテナ・ボリューム・ネットワークを `docker compose -p mastersmith-perf -f docker/perf/compose.yaml down -v` で消し、一時の環境ファイルを消す。配備した環境を起動し直して healthy を確かめる。

## Sources

- `aidlc/spaces/default/intents/260922-auth-audit-base/operation/performance-validation/performance-validation-questions.md`（Q1〜Q4、確認済みの要約）
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u1-app-skeleton/nfr-requirements/performance-requirements.md`・`scalability-requirements.md`、`nfr-design/performance-design.md`・`scalability-design.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u2-authentication/nfr-requirements/performance-requirements.md`・`scalability-requirements.md`、`nfr-design/performance-design.md`・`scalability-design.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u3-access-control/nfr-requirements/performance-requirements.md`・`scalability-requirements.md`、`nfr-design/performance-design.md`・`scalability-design.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u4-audit-log/nfr-requirements/performance-requirements.md`・`scalability-requirements.md`、`nfr-design/performance-design.md`・`scalability-design.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/dashboards.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/build-and-test/build-and-test-summary.md`（未検証の目標の持ち主）
- `docker/perf/compose.yaml`、`perf/k6/scenarios.js`

## Assumptions & Open Questions

- 閉じた負荷（考える時間なし）は、利用者 50 名の実際の使い方より厳しい。目標の「同時 10 件」を最も厳しく読んだ条件である。
