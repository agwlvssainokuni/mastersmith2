# Observability Design — U3 管理画面のアクセス制御（u3-access-control）

U3 の観測性の要件（`observability-requirements.md` の NFR10.2〜NFR10.5）を満たす設計。ログ・トレースの仕組みは U1 が用意し（U1 の `nfr-design/observability-design.md`）、U3 はそれを使う。処理の流れは U3 の `functional-spec.md`、技術は U3 の `tech-stack-decisions.md` に従う。秘密情報の扱いは `security-design.md`（`security-requirements.md`）、性能・拡張性・信頼性は各設計書（`performance-requirements.md`・`scalability-requirements.md`・`reliability-requirements.md`）で扱う。`contract-summary.md` は、本ワークフローで Contract Design を行わないため無い。

## 1. アクセス拒否の出来事（NFR10.2）

| 場合 | 知らせるか | 理由 |
|---|---|---|
| 管理者のみのパスで 403 | 知らせる | `NOT_ADMIN` |
| 管理者のみのパスで 401（期限切れ以外） | 知らせる | U2 の例外の区分、または例外が無ければ `TOKEN_MISSING`（`TOKEN_MALFORMED`・`TOKEN_INVALID`・`USER_NOT_FOUND`・`TOKEN_MISSING`。受け渡しの形は `security-design.md` 3章） |
| 管理者のみのパスで 401（期限切れ） | 知らせない | 通常の利用で起きるため（BR3.2） |
| 管理者のみ以外のパスの 401 | 知らせない | BR3.3 |
| 正規化されていないパスの 400 | 知らせない | 判定の前の拒否（`security-design.md` 2章）。アプリのログの WARN だけ |

項目は `security-design.md` 4章のとおり監査ログの記録項目にそろえる。トレースIDは U1 の方法で得る。

## 2. アプリのログ（NFR10.3、NFR10.4）

| 出来事 | レベル | キーと値 | 出さないもの |
|---|---|---|---|
| 403 を返した | WARN | `code`（`ACCESS_DENIED`） | メールアドレス、パス、トークン |
| 400 / `REQUEST_REJECTED` を返した | WARN | `code` | 拒否したパス |
| 管理者のみのパスの 401 | DEBUG | `reason` | トークン |
| 出来事の通知で例外が戻った | WARN | 例外の型 | 例外のメッセージ |

- 利用者の特定は監査ログで行い、アプリのログにはメールアドレスを出さない（NFR10.3）。
- トレースIDは U1 の仕組みで付く（NFR10.4）。フィルターの段階の応答（401／403／400）も、トレースの開始（ほぼ最優先のサーブレットのフィルター）の後に Spring Security のフィルターの連鎖（順番 -100）を通るため、トレースIDが付く。根拠とテストは `security-design.md` 2章。

## 3. 指標と運用で見るもの（NFR10.5）

- U3 は独自の指標を作らない。管理画面へのアクセス拒否の件数は、本Intentでは監査ログの ACCESS_DENIED の件数で見る。
- U1 の設計により、外部エクスポートを有効にしたときは、HTTP の指標（状態コード別の件数）に 401・403 の件数が含まれて送られる。
- 目標と警報は、配備先が決まったときに Operation の段階で定める。
