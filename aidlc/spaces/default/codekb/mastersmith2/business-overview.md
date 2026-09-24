# 業務の概要（mastersmith2）

## 目的

mastersmith2 は、MasterSmith（マスタ管理アプリ）のリポジトリである。管理画面の土台（認証・管理者向け API の認可・監査ログ）の上に、業務データの画面を組み立てる元になる DSL（YAML）を扱う機能がある。対象DB（MySQL・MariaDB・PostgreSQL）のスキーマから既定の DSL を作り、利用者が DSL を投入・プレビュー・適用する。業務データそのもの（マスタの一覧・登録・更新）を扱う画面は、まだ無い。

配備先は当面、開発者の PC 上のコンテナ（colima）だけである。

## 利用者と、今ある機能

| 利用者 | 機能 | 主な部品（詳しくは `component-inventory.md`） |
|---|---|---|
| 管理画面の利用者 | メールアドレスとパスワードでログインし、アクセストークンとリフレッシュトークン（Cookie）を受け取る。連続で失敗するとロックされる。トークンの更新、ログアウト | `auth`・`frontend-feature-auth` |
| 管理者 | 管理者向け領域（画面 `/admin`、API `/api/admin/**`）に入る | `access`・`frontend-feature-admin` |
| 管理者 | DSL の管理画面で、既定の DSL の生成（対象DB のスキーマから）、DSL の投入（ファイル）、プレビュー（違い・誤り・警告の確認）、適用、破棄、履歴からの戻し、ダウンロードを行う | `dslmanage`・`dsl`・`targetdb`・`frontend-feature-dsl` |
| 後続の機能（アプリの中） | 適用中の DSL のモデルを読む | `dsl`（`ActiveDslModelProvider`） |
| 運用者・監査者 | 認証の出来事、アクセス拒否、DSL の操作（投入・受け付けなかった投入・適用・破棄）を、追記だけの監査ログとして内部DB に残す | `audit` |
| 運用者 | 起動時に初期管理者を1回だけ作る（設定は環境変数） | `user` |
| 開発者（運用者を兼ねる） | 1コマンドの検査（`./gradlew verify`）、コンテナでの起動、必要なときだけの手元の監視と負荷の試験 | `build-and-verify`・`container-runtime`・`perf-and-monitoring` |

## 業務上の決まり（今回深く読んだ範囲で確かめたもの）

- ログインの失敗は、理由（利用者がいない・パスワード誤り・ロック中）によらず同じ応答（401 / `AUTHENTICATION_FAILED`）にする。利用者がいないときは、ロックの状態の表のダミーの行（負の ID）に同じ読み書きを行い、回数と種類をそろえる（`V3__u2_authentication.sql` の注記と `LoginService`）。
- ロックの状態の行は、通常は利用者の作成と同じトランザクションで作る（`LoginAttemptStateInitializer`）。行が無い利用者のログインでは、その場で行を作ってから読み直す（`code-quality-assessment.md` の TD-2）。
- 監査ログは確定の後に別のトランザクションで追記し、書き込みに失敗しても元の操作は失敗させない（ERROR のログ1件で知らせる）。
- 構造化ログは標準出力の JSON が正で、外部への送信（OTLP）は既定で無効である。
- 対象DB の接続情報は設定（環境変数）からだけ受け取る（`project.md` の Forbidden）。

## この Intent（`260924-followup-fixes`）との関係

前の Intent（`260923-dsl-schema-loader`）の振り返りで後に回した小さな修正7件（scope bugfix）を行う。7件の場所と事実は `code-quality-assessment.md` の「技術的負債」の TD-1〜TD-7 に、1か所だけ書いた。業務の機能は増えず、ログ・ログイン・負荷の試験の台本・コンテナの設定・画面の部品の取り込みを直す。

## 対象の範囲

今回深く読んだのは7件に関わるファイルとビルドの設定だけである（`reverse-engineering-timestamp.md` の Scope of Analysis）。上の機能の一覧のうち、DSL・認可・利用者の多くは流し読みで確かめたものである。
