# Observability Design — U2 認証（u2-authentication）

U2 の観測性の要件（`observability-requirements.md` の NFR10.2〜NFR10.7）を満たす設計。ログ・トレース・指標・外部エクスポートの仕組みは U1 が用意し（U1 の `nfr-design/observability-design.md`）、U2 はそれを使う。処理の流れは U2 の `functional-spec.md`、技術は U2 の `tech-stack-decisions.md` に従う。秘密情報の扱いは `security-design.md`（`security-requirements.md`）、性能・拡張性・信頼性は各設計書（`performance-requirements.md`・`scalability-requirements.md`・`reliability-requirements.md`）で扱う。設計の方針の確定回答は `nfr-design-questions.md` にある。`contract-summary.md` は、本ワークフローで Contract Design を行わないため無い。

## 1. アプリのログ

| 出来事 | レベル | キーと値 | 出さないもの | 要件 |
|---|---|---|---|---|
| 初期管理者を作成した | INFO | `email` | パスワード | NFR10.3 |
| 初期管理者を作らなかった | WARN | `reason`（足りない・正しくない項目）、直し方の文 | パスワード | NFR10.3 |
| ロックした | INFO | `userId` | メールアドレス、パスワード | NFR10.4 |
| トークンの検証の失敗（401） | DEBUG | `reason`（`TOKEN_MISSING`・`TOKEN_INVALID`・`TOKEN_EXPIRED`・`USER_NOT_FOUND` の区分） | トークンの値 | NFR10.5 |
| Origin の不一致（403） | WARN | `origin` の有無と一致しなかったこと | Cookie の値 | NFR5.4 |
| 起動時の照合の時間 | INFO（範囲外は WARN） | `bcryptCost`、`elapsedMs` | — | NFR2.1 |
| 使い終わったトークンの削除 | INFO（失敗は ERROR） | `deleted` | トークンの値 | NFR1.7 |

- ログインの失敗そのものは、アプリのログには出さない（監査ログに記録する）。U1 の共通のエラー応答の変換が 4xx を WARN 以下で1回出す決まり（U1 の決まり 5.5）は、ログインの失敗の 401 も対象になる。その出力にはメールアドレスを載せない。
- ログの項目は U1 の形（1行1件の JSON、キーと値）に従い、要求の処理中は U1 の仕組みでトレースIDが付く（NFR10.6）。

## 2. 出来事（監査ログへ、NFR10.2）

| 項目 | 取り方 |
|---|---|
| 日時 | 注入できる時計 |
| 種類 | `LOGIN_SUCCEEDED`・`LOGIN_FAILED`・`LOGGED_OUT` |
| メールアドレス | 入力を trim・小文字化したもの |
| 利用者ID | 分かれば（成功・誤り・ロック中）。監査ログには記録されない（U4 の決まり） |
| 失敗の理由 | `USER_NOT_FOUND`・`PASSWORD_MISMATCH`・`ACCOUNT_LOCKED` |
| 接続元IP | 要求の接続元。転送元のヘッダーは U1 の信頼の設定があるときだけ使う（U1 の `nfr-design/security-design.md` 6章と同じ方針） |
| User-Agent | 要求のヘッダー（長さを 512 文字に切る） |
| トレースID | U1 の方法（`functional-spec.md` 6.1）で要求のトレースIDを得る |

出来事は同じスレッドで知らせ、U4 が記録する（`reliability-design.md` 3章）。

## 3. トレースと指標

- トレース: U1 の仕組みで要求ごとに付く。U2 は独自のスパンを加えない。照合の時間は要求のスパンの時間に含まれる。
- 指標: U2 は独自の指標を作らない。ログインの失敗の割合（NFR10.7）は、本Intentでは監査ログから数える。U1 の設計により、外部エクスポートを有効にしたときは、ログインの API の HTTP の指標（件数・時間・状態コード）が送られる。

## 4. 運用で見るもの（候補）

| 見たいこと | 見方 |
|---|---|
| ログインの失敗の割合 | 監査ログの LOGIN_FAILED ÷（LOGIN_SUCCEEDED＋LOGIN_FAILED） |
| ロックの発生 | アプリのログの「ロックした」の INFO |
| 総当たりの兆し | 監査ログの同じメールアドレス・接続元IP の LOGIN_FAILED の件数 |
| 応答時間 | 外部エクスポートの有効時は HTTP の指標、当面は Performance Validation で測る |

目標（SLO）と警報は、配備先が決まったときに Operation の段階で定める。
