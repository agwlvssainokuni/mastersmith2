# NFR の検証の表（nfr-validation-matrix）

Intent `260922-auth-audit-base`（auth-audit-foundation）の性能の目標の、目標と実測の比べ。測り方は `load-test-plan.md`、実測は `test-results.md`、目標は U1〜U4 の `nfr-requirements/performance-requirements.md`（performance-requirements）・`scalability-requirements.md`（scalability-requirements）、予算は `nfr-design/performance-design.md`（performance-design）・`scalability-design.md`（scalability-design）、応答時間の見方は `operation/observability-setup/dashboards.md`（dashboards）にある。**すべて CPU の上限 2 での結果**である（Q3。設計の値は 4）。

## 1. この段が持ち主の 11 件

| ID | 目標 | 実測（p95） | 判定 | 備考 |
|---|---|---|---|---|
| U1-NFR1.1 | ヘルスチェック 95% が 500ms 以内 | 1.9 ms | **Met**（応答時間） | ただし同時の要求の 39.7% が、内部DBが正常なのに DOWN を返した（F1）。応答時間の目標は満たすが、応答の中身の不具合として記録する |
| U1-NFR1.3 | 共通の処理が加える時間 95% で 20ms 以内 | 上限 1.9 ms（ヘルスチェックの全体） | **Met**（上限による。Q4） | 全体が 20 ms 以内のため、加える時間も 20 ms 以内 |
| U2-NFR1.1 | ログイン 95% が 1 秒以内（成功・失敗とも、同時 10） | 成功 1,615〜1,621 ms、失敗 1,570 ms | **Not Met** | F4（CPU 2）。成功の最初の同時 10 件は 7.2 秒（F2） |
| U2-NFR1.2 | トークンの更新 95% が 1 秒以内（同時 10） | 4.2〜4.7 ms | **Met**（応答時間） | 秒あたり約 3,000 件の負荷を続けると約 35 秒でアプリがメモリの上限で止まった（F3） |
| U2-NFR1.3 | 照合 1 回 100〜500 ms | 278 ms（起動時の測定） | **Met** | 同時 10 件では CPU を分け合うため 1 件の応答が延びる（F4） |
| U2-NFR1.4 | 認証の処理が加える時間 95% で 50ms 以内 | 上限 2.1 ms（管理者の確認用 API の全体） | **Met**（上限による。Q4） | |
| U2-NFR1.6 | 想定の規模（利用者 50 名、同時ログイン 10 名）を1台で | ログインの目標を満たさない、同時 10 件のログインで監査の欠落 | **Not Met** | F2・F4 |
| U3-NFR1.1 | 確認用 API 95% が 300ms 以内（成功・403 とも、同時 10） | 成功 2.1 ms、403 4.0 ms | **Met** | |
| U3-NFR1.3 | 拒否の記録で 401／403 を 100ms 以上遅らせない | 上限 4.0 ms（403 の全体。記録を含む） | **Met**（上限による。Q4） | |
| U4-NFR1.1 | 監査の書き込み 95% が 50ms 以内（同時 10） | 上限 6 ms（受け取りの処理の全体、TRACE） | **Met**（上限による。Q4） | |
| U4-NFR1.2 | 監査の書き込みが呼び出し元の予算の内側に収まる（拒否の経路とログインの経路） | 拒否の経路 4.0 ms（収まる）。ログインの経路は同時 10 件で接続が尽き、監査の書き込みが失敗 | **Not Met** | F2。設計（U4 の `infrastructure-specification.md` 5章）の見積もりと違う |

集計: Met 8 件（うち上限による 4 件）、Not Met 3 件（U2-NFR1.1・U2-NFR1.6・U4-NFR1.2）、Unverified 0 件。目標は緩めていない（project.md の Testing Posture）。

## 2. ほかの段が持ち主の未検証の目標（Build and Test の 17 件の行き先）

| 持ち主の段 | 件数 | 結果 |
|---|---|---|
| deployment-execution | 1 | U4-NFR3.3 は Met（`operation/deployment-execution/health-check-report.md`） |
| observability-setup | 5 | Met 3 件（U2-NFR10.7・U3-NFR10.5・U4-NFR10.5）、Unverified 2 件（U4-NFR1.4・U1-NFR1.9。月1回の確認で見る。`operation/observability-setup/slo-config.md`） |
| performance-validation（本段） | 11 | 1節のとおり（Met 8、Not Met 3） |

## 3. 見つかったことと、直す場所の候補

| ID | 内容 | 直す場所の候補（本段では直していない） |
|---|---|---|
| F1 | ヘルスチェックの同時の要求に、正常でも DOWN を返す | U1 の `TimeBoundedDbHealthIndicator`（確認中の要求には直前の結果を返す、など） |
| F2 | 同時 10 件のログインでコネクションプールが尽き、監査の書き込みが失敗する | U2・U4 の接続の持ち方（ログインの接続を監査の前に返す、監査の書き込みを別の仕組みにする）、またはプールの上限（U1 の設定）。U4 の `infrastructure-specification.md` 5章の見積もりの見直し |
| F3 | 高い負荷でアプリのコンテナがメモリの上限で止まる | JVM の最大ヒープの割合（`Dockerfile` の `-XX:MaxRAMPercentage`）かコンテナのメモリの上限（`compose.yaml`） |
| F4 | CPU 2 ではログインが目標を超える | CPU の上限 4 で測り直す（colima の VM の CPU を増やす）か、bcrypt の cost の見直し（U2 の NFR2.1 との兼ね合い） |

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

- F1〜F4 の扱い（直すか、いつ直すか、どの段・どの Intent で直すか）は依頼者の判断を待つ。
- CPU の上限 4 での測定は行っていない（Q3）。
