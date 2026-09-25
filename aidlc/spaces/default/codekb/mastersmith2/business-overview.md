# 業務の概要（mastersmith2）

## 目的

mastersmith2 は、MasterSmith（マスタ管理アプリ）のリポジトリである。管理画面の土台（ログイン・管理者向け API の認可・監査ログ）の上に、業務データの画面を組み立てる元になる DSL（YAML）を扱う機能がある。対象DB（MySQL・MariaDB・PostgreSQL）のスキーマから既定の DSL を作り、管理者が DSL を投入・プレビュー・適用する。業務データそのもの（マスタの一覧・登録・更新）の画面と、利用者を管理する画面・API は、まだ無い。

配備先は当面、開発者の PC 上のコンテナ（colima）だけである（`team.md` の Deployment）。

## 利用者と、今ある機能

| 利用者 | 機能 | 主な部品（詳しくは `component-inventory.md`） |
|---|---|---|
| 管理画面の利用者 | ログイン（連続 5 回の失敗で 30 分のロック）、トークンの更新、ログアウト | `auth`・`user`・`frontend-feature-auth` |
| 管理者 | 管理者向け領域（画面 `/admin`、API `/api/admin/**`） | `access`・`frontend-feature-admin` |
| 管理者 | 既定の DSL の生成、DSL の投入（最大 10MB）・プレビュー・適用・破棄・履歴（20 件）からの戻し・ダウンロード | `dslmanage`・`dsl`・`targetdb`・`frontend-feature-dsl` |
| 運用者・監査者 | 認証・アクセス拒否・DSL の操作を追記だけの監査ログとして内部DB に残す | `audit` |
| 運用者（起動時） | 初期管理者の自動作成（環境変数のメールアドレスとパスワード、2回目以降は作らない） | `user` |
| 開発者（運用者を兼ねる） | 1コマンドの検査（`./gradlew verify`）、コンテナでの起動、負荷の試験 | `build-and-verify`・`container-runtime`・`perf-and-monitoring` |

利用者は内部DB の `users` の表にだけあり、今は初期管理者の自動作成（`user/service/InitialAdminInitializer.java`）以外に利用者を作る道が無い（確かめた事実）。

## 業務上の決まり（今回深く読んだ範囲で確かめたもの）

- 利用者はメールアドレスで識別する。前後の空白を除き小文字にそろえ、254 文字までで一意（`user/domain/EmailAddress.java`、`V2__u2_user_account.sql`）。
- パスワードは 12 文字以上（コードポイント）かつ UTF-8 で 72 バイト以内、bcrypt（cost 既定 12）で保存し、平文は保存しない（`user/domain/PasswordPolicy.java`・`user/service/UserAccountConfig.java`）。
- 管理者かどうかは `users.admin_flag` の1つだけで、要求ごとに内部DB から読んだ値で判断する（`auth/web/AccessTokenAuthenticationProvider.java`、`access/web/AdminAuthorizationManager.java`）。役割・権限の細かい区分は無い。
- ログインの失敗は、存在しない利用者でもパスワードの誤りでも同じ 401 `AUTHENTICATION_FAILED` を返し、存在を推測させない（`auth/service/LoginService.java`）。
- アクセストークン（JWT、既定 5 分）は失効の仕組みを持たず、ログアウトはリフレッシュトークン（既定 24 時間、使うたびに作り直す）の無効化だけ（`project.md` の DECIDED、`application.yaml`）。
- 画面の文言とエラーの説明は日本語と英語の2つ。サーバーは要求の `Accept-Language` で、画面はブラウザの言語設定で決める（既定は日本語）。

## この Intent（`260925-user-management`）との関係

ロードマップの Intent G（ユーザー登録・招待フロー）と Intent H（ユーザーごとのプリファレンス）をまとめて扱う（scope classic）。

- G: 管理者が利用者を登録し、言語を指定した HTML の招待メールを Mustache のテンプレート（自前のエンジン java-mustache-processor）で送る。招待された人がパスワードを含むプリファレンスを設定して登録を完了する。
- H: 利用者ごとの言語（ja・en）・テーマ（light・dark・system）・文字の大きさ・パスワードを、プリファレンスの画面から設定して内部DB に保存する。ブランドカラーとフォントファミリーは `application.yml` のインスタンス全体の固定の設定とする。

今のコードとの差は、下の所見として1か所ずつ書いた（ここでは題だけを並べる）。

| ID | 所見の題 | 書いた場所 |
|---|---|---|
| K-1 | 利用者の表とドメインが、状態・言語・テーマ・文字の大きさ・招待の項目を持たない | `component-inventory.md` の `user` |
| K-2 | ログインとトークンの認証が、利用者の状態を見ない | `architecture.md` の Interaction Diagrams 1・3 |
| K-3 | メール送信と Mustache のテンプレートの仕組みが無い | `technology-stack.md` |
| K-4 | テーマに `system` が無く、テーマの値はブラウザの localStorage にある。ブランドとフォントを画面に渡す道が無い | `component-inventory.md` の `make-you-chic-ui` |
| K-5 | 表示言語はブラウザの設定で起動時に決まり、切り替えの口が無い | `component-inventory.md` の `frontend-app-layout-i18n` |
| K-6 | ログインなしで呼ぶ API はアクセスの決まりの追加が要り、`/api/auth/**` ではアクセストークンを読まない | `api-documentation.md` |
| K-7 | 監査の出来事を足すには `audit` の中を変える必要がある | `component-inventory.md` の `audit` |
| K-8 | パスワードの変更の後に、利用者のリフレッシュトークンを一括で無効にする操作が無い | `component-inventory.md` の `auth` |
| K-9 | 招待の URL を組み立てる元のベースURLが、設定が無いと要求から組み立てられる | `api-documentation.md` |
| K-10 | メール送信を業務のトランザクションや監査と同じ流れで行うと、内部DB の接続を持ち続けうる | `architecture.md` の Interaction Diagrams 4 |
| K-11 | 既存の `user.*` のパッケージはパッケージごとのカバレッジの下限の対象外 | `code-quality-assessment.md` |
