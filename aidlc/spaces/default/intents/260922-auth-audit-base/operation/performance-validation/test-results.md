# 負荷の試験の結果（test-results）

Intent `260922-auth-audit-base`（auth-audit-foundation）の負荷の試験の結果。計画は `load-test-plan.md`、目標は U1〜U4 の `nfr-requirements/performance-requirements.md`（performance-requirements）・`scalability-requirements.md`（scalability-requirements）、予算は `nfr-design/performance-design.md`（performance-design）・`scalability-design.md`（scalability-design）、応答時間の見方は `operation/observability-setup/dashboards.md`（dashboards）にある。

- 日時: 2026-09-23 13:10〜13:25（日本時間）
- 環境: 使い捨ての環境（`mastersmith-perf`）、イメージ `mastersmith:local`（版 `7040876`）、**CPU の上限 2**、メモリ 1GB。負荷は `grafana/k6:2.3.0`、同時 10、考える時間なし
- 試験の後に、使い捨ての環境（コンテナ・ボリューム・一時の環境ファイル）を消し、配備したアプリを起動し直して healthy を確かめた

## 1. 場面ごとの結果（応答時間はミリ秒）

| 場面 | 回 | 件数 | 秒あたり | 中央値 | p90 | **p95** | p99 | 最大 | 想定と違う応答 |
|---|---|---|---|---|---|---|---|---|---|
| health | 1回目 | 619,407 | 10,323 | 0.8 | 1.5 | **1.9** | 2.9 | 16.8 | **245,964 件（39.7%）が 503（DOWN）**（F1） |
| loginSuccess | 1回目 | 401 | 3.3 | 1,496.8 | 1,564.3 | **1,615.3** | 61,451.6 | 62,138.6 | 0 件（最初の同時 10 件が極端に遅い。F2） |
| loginSuccess | 2回目 | 360 | 5.9 | 1,535.7 | 1,598.6 | **1,620.9** | 7,212.9 | 7,217.9 | 0 件（最初の同時 10 件が 7.2 秒。F2） |
| loginFailure | 1回目 | 409 | 6.7 | 1,491.9 | 1,544.9 | **1,569.7** | 1,647.0 | 1,682.4 | 0 件 |
| refresh | 1回目 | 236,069 | 3,516.6 | 1.1 | 2.7 | **4.2** | 7.2 | 1,534.6 | 1,318 件（約 37 秒でアプリが止まった。F3） |
| refresh | 2回目 | 212,950 | 2,968.3 | 1.4 | 3.3 | **4.7** | 11.1 | 1,621.4 | 1,090 件（同じくアプリが止まった。F3） |
| adminCheck | 2回目 | 699,477 | 11,602.3 | 0.6 | 1.5 | **2.1** | 3.9 | 286.3 | 0 件 |
| forbidden（403） | 2回目 | 351,374 | 5,827.1 | 1.4 | 3.1 | **4.0** | 6.1 | 298.5 | 0 件（すべて 403。1件ごとに監査の書き込みあり） |
| forbidden（TRACE、時間の判定に使わない） | 3回目 | 84,790 | — | — | — | 8.5 | — | — | 0 件 |

1回目の adminCheck と forbidden は、直前の refresh でアプリが止まったため測れなかった（2回目で測った）。

## 2. 照合の時間（U2-NFR1.3）

| 測り方 | 値 |
|---|---|
| 起動時の測定（ログ「パスワードの照合の時間を測りました」、bcrypt cost 12） | **278 ms**（目安 100〜500 ms の内側） |
| ログインの応答時間（同時 10、CPU 2） | 中央値 約 1,500 ms。照合は CPU を使い、2 つの CPU を 10 件で分け合うため、1件あたり約 5 倍に延びる（278 ms × 10 ÷ 2 ≒ 1,400 ms） |

## 3. 監査の書き込みの時間（U4-NFR1.1、3回目、TRACE）

forbidden（管理者でない利用者の 403 を同時 10 件、30 秒）で、アクセス拒否の監査イベント 84,790 件の書き込みを、スレッドごとに ENTER と EXIT の時刻（ミリ秒）の差で集めた。

| 範囲 | 件数 | 中央値 | p95 | p99 | 最大 |
|---|---|---|---|---|---|
| `AuditEventListener#onAdminAccessDeniedEvent`（組み立て＋別のトランザクションの開始から確定まで。上限として使う） | 84,789 | 1 | **6** | 11 | 46 |
| `AuditEventRecorder#record`（参考） | 84,790 | 0 | 2 | 5 | 31 |

## 4. 見つかったこと

| ID | 内容 | 証拠 | 影響 |
|---|---|---|---|
| **F1** | ヘルスチェックに同時に要求が来ると、前の内部DBの確認が終わっていない間の要求に、内部DBが正常でも **DOWN（503）** を返す（隔壁として確認を1本に限る設計による。`TimeBoundedDbHealthIndicator` の `previous-check-running`） | health の 39.7% が 503、WARN「内部DBの確認を行えませんでした」reason=previous-check-running が 265,168 件 | 監視（コンテナのヘルスチェック、配備先のロードバランサーなど）が同時に確かめると、正常なのに異常と判断されうる。今のコンテナのヘルスチェック（30 秒に1回）だけなら起きにくい |
| **F2** | **同時 10 件のログインの成功で、コネクションプール（10 本）が尽きて待ち合う**。ログインの接続が確定の後も返されないまま、監査の受け取りがもう1本を借りに行くため、10 件が互いに待ち、接続を借りる待ちの上限（5 秒）で **監査の書き込みが 10 件失敗**した（監査イベントが欠けた）。応答は 200 のまま、約 7.2 秒かかった | 13:10 と 13:19 に「mastersmith-db - Connection is not available, request timed out after 5004ms (total=10, active=10, idle=0 …)」各 10 件、ERROR「監査イベントの記録に失敗しました」（LOGIN_SUCCEEDED、CannotCreateTransactionException）各 10 件。2回とも再現 | 監査ログの欠落（U4 の NFR3.7 で受け入れたのは「アプリが止まった場合」の欠落で、この場合ではない）。U4 の `infrastructure-specification.md` 5章の見積もり「待ちは数十〜百ミリ秒」と違い、同時の数が接続の数に達すると抜けられない |
| **F3** | トークンの更新を毎秒約 3,000 件で流すと、約 35 秒で **アプリのコンテナがメモリの上限（1GB）で止まる**（OOMKilled、終了コード 137）。2回とも再現 | `docker inspect` の OOMKilled=true、メモリの最大 1021MiB / 1GiB | JVM の最大ヒープ（メモリの 75% = 768MB）とヒープ以外の分の合計が 1GB を超える。量は想定の規模（利用者 50 名）をはるかに超えるが、コンテナの大きさと JVM の設定の組み合わせの問題である |
| **F4** | CPU の上限 2 では、同時 10 件のログインの p95 が約 1.6 秒で、目標の 1 秒を超える | loginSuccess・loginFailure の p95 | CPU の上限 4（設計の値）では約半分になる見込みだが、測っていない |

## 5. 想定の規模との関係

考える時間なしの同時 10 は、秒あたり数千件の要求になり、利用者 50 名の実際の使い方（1人が数十秒に1回の操作）より桁違いに重い。F3 はこの重さでだけ起きた。F1・F2 は同時の数だけで起き、想定の規模でも起こりうる（F2 は 10 名が同時にログインしたとき）。

## Sources

- `aidlc/spaces/default/intents/260922-auth-audit-base/operation/performance-validation/performance-validation-questions.md`（Q1〜Q4、確認済みの要約）、`load-test-plan.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u1-app-skeleton/nfr-requirements/performance-requirements.md`・`scalability-requirements.md`、`nfr-design/performance-design.md`・`scalability-design.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u2-authentication/nfr-requirements/performance-requirements.md`・`scalability-requirements.md`、`nfr-design/performance-design.md`・`scalability-design.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u3-access-control/nfr-requirements/performance-requirements.md`・`scalability-requirements.md`、`nfr-design/performance-design.md`・`scalability-design.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u4-audit-log/nfr-requirements/performance-requirements.md`・`scalability-requirements.md`、`nfr-design/performance-design.md`・`scalability-design.md`、`infrastructure-design/infrastructure-specification.md`（5章のコネクションプールの見積もり）
- `aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/dashboards.md`
- `backend/src/main/java/cherry/mastersmith/common/health/TimeBoundedDbHealthIndicator.java`、`audit/service/AuditEventListener.java`・`AuditEventRecorder.java`
- 2026-09-23 13:10〜13:25 の k6（`grafana/k6:2.3.0`）の結果（`build/perf-results/` に置いた。Git の管理外）、`docker inspect`・`docker stats`、使い捨ての環境のアプリのログ

## Assumptions & Open Questions

- ENTER・EXIT のログの時刻はミリ秒の単位のため、3節の値は ±1 ms の誤差を含む。
- F1〜F4 を直すかどうか、いつ直すかは依頼者の判断を待つ（本段では直していない）。
