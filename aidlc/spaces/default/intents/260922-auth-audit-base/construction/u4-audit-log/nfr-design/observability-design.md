# Observability Design — U4 監査ログ（u4-audit-log）

U4 の観測性の要件（`observability-requirements.md` の NFR10.3〜NFR10.5）を満たす設計。ログ・トレースの仕組みは U1 が用意し（U1 の `nfr-design/observability-design.md`）、U4 はそれを使う。処理の流れは U4 の `functional-spec.md`、技術は U4 の `tech-stack-decisions.md` に従う。秘密情報の扱いは `security-design.md`（`security-requirements.md`）、性能・拡張性・信頼性は各設計書（`performance-requirements.md`・`scalability-requirements.md`・`reliability-requirements.md`）で扱う。`contract-summary.md` は、本ワークフローで Contract Design を行わないため無い。

## 1. 書き込みの失敗のログ（NFR10.3）

| 項目 | 中身 |
|---|---|
| レベル | ERROR（1件の失敗につき1回） |
| メッセージ | U4 が決めた固定の文（例: 監査イベントの記録に失敗した）。例外のメッセージは使わない（`security-design.md` 1章） |
| キーと値 | `auditEventType`・`result`・`occurredAt`・`enteredEmail`・`failureReason`・`sourceIp`・`userAgent`・`requestPath`（あれば）・`auditTraceId` |
| 例外 | 型の名前とスタックトレース |
| トレースID | U1 の仕組みで付く（要求のトレースID） |

記録しようとした全項目（メールアドレスを含む）を出し、後から手で監査の記録を補えるようにする。

## 2. トレースIDの一致（NFR10.4）

- 監査イベントのトレースIDは、出来事に載った値（U2・U3 が U1 の方法で要求のトレースIDから得たもの）をそのまま記録する（BR2.4）。
- 受け取りと書き込みは、知らせた要求と同じスレッドで行う（`reliability-design.md` 1章）。確定の後の経路も同じスレッドで呼ばれるため、アプリのログのトレースIDと一致する。
- 受け取りを別スレッドに移す変更をする場合は、トレースの情報を引き継ぐ実行の枠を必ず使う（U1 の `functional-spec.md` 6.1）。本Intentでは別スレッドにしない。
- テスト: traceparent つきの要求と無い要求のそれぞれで、ログインの監査イベントのトレースIDとアプリのログのトレースIDが一致すること。

## 3. 運用で見るもの（NFR10.5）

| 見たいこと | 見方 |
|---|---|
| 書き込みの失敗の件数 | アプリのログの、1章の ERROR の件数。外部エクスポートを有効にしたときは、U1 の設計によりログのレベルごとの件数の指標（ERROR の件数）にも含まれて送られる |
| 記録の量 | 監査イベントの表の件数と、H2 のファイルの大きさ（`scalability-design.md`） |

U4 は独自の指標を作らない。指標を外部に公開する窓口は持たない（U1 の設計）。目標と警報は、配備先が決まったときに Operation の段階で定める。
