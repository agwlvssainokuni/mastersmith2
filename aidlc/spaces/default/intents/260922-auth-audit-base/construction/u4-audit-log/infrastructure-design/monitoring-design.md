# Monitoring Design — U4 監査ログ（u4-audit-log）

U4 の `observability-design.md`（NFR Design）の方針を、当面の配備先（開発者の PC 上のコンテナ）で実現する設計。性能・信頼性は `performance-design.md`・`reliability-design.md`、安全は `security-design.md`、容量は `scalability-design.md`、部品は `logical-components.md` と Domain Design の `components.md`、処理の流れは `functional-spec.md` に従う。`contract-summary.md` は、本ワークフローで Contract Design を行わないため無い。収集・確認の仕組みは U1 の `infrastructure-design/monitoring-design.md` のとおりで、本書は U4 に固有の見方だけを書く。

U4 は独自の指標（Micrometer の計器）を作らない（U4 の NFR10.5）。以下の「見る値」は、アプリのログと監査ログ（内部DBの表）から数える。

## 1. 見る値（Metrics & KPIs）

| Metric | Source | Threshold | Why it matters |
|---|---|---|---|
| 監査の書き込みの失敗 | アプリのログの ERROR（監査イベントの記録に失敗） | 1件でも出たら確かめる | 監査の記録の欠落（ERROR のログの内容から手で補う。U4 の NFR10.3） |
| 監査の書き込みの時間 | Performance Validation の測定（開始から確定まで） | 95 パーセンタイルが 50 ミリ秒超 | U4 の NFR1.1。拒否の経路（U3 の NFR1.3）の余裕 |
| 記録の量 | 監査イベントの表の件数と、ボリュームの使用量（U1 の見方） | 見積もり（1年 約 180MB、索引を含めて 250MB 程度）の2倍 | 容量の見直し（U4 の NFR1.4） |

外部エクスポートを有効にしたときは、U1 の設計により、ERROR のログの件数（`logback.events`）が送られる指標に含まれる。U4 としての計器は足さない。

## 2. 警報（Alerts）

| Alert | Condition | Severity | Routes to |
|---|---|---|---|
| 監査の書き込みの失敗 | U4 の ERROR のログが出る | 中 | 当面: 開発者が確認し、ERROR のログの内容から記録を補うかを判断する。配備先が決まったら通知の先を決める |

## 3. 目標（SLIs / SLOs）

| SLI | SLO target | Measurement window |
|---|---|---|
| 監査イベント1件の書き込みの時間（95 パーセンタイル） | 50 ミリ秒以内（同時 10 件） | Performance Validation の試験の間 |
| 監査の書き込みの成功の割合 | 配備先が決まったときに決める（候補: 失敗 0 件） | 30 日 |

## 4. ログとトレース

- 監査イベントとアプリのログは同じトレースIDで結び付く（U4 の NFR10.4）。1要求の調べ方は、監査イベントの `traceId` でアプリのログを絞り込む。
- 監査ログを見る画面・API は本Intentでは作らない。当面の確認は、開発者が内部DBを読み取りで開いて行う（H2 のコンソールは使わない。アプリを止めた状態でファイルを複写し、複写したファイルを H2 の道具で開く）。
- ダッシュボードは配備先が決まったときに作る。
