# 業務の概要（mastersmith2）

## 目的

mastersmith2 は MasterSmith（マスタ管理アプリ）のリポジトリである。管理画面の土台（ログイン・アカウントロック・管理の API の認可・監査ログ）の上に、業務データの画面の元になる DSL（YAML）を扱う機能、利用者の招待と登録の完了、自分の設定（言語・テーマ・文字の大きさ・パスワード）、管理者による利用者の管理（一覧・氏名と言語の変更・管理者の印・利用停止と再開・ログインの失敗回数の取り消し）、起動時の初期管理者の作成と救済がある。業務データそのもの（マスタの一覧・登録・更新）の画面は、まだ無い。

権限は「管理者の印」（真偽値）1つだけで、役割・権限の仕組みは無い。サイドバーは平らな一覧で、階層のあるメニューは無い（今回の Intent の出発点。K-32〜K-36）。

配備先は当面、開発者の PC 上のコンテナ（colima）だけである（`team.md` の Deployment）。メールは外部の SMTP へ送らず、手元では Mailpit で受ける（`project.md` の Forbidden）。

## 利用者と、今ある機能

| 利用者 | 機能 | 主な部品（詳しくは `component-inventory.md`） |
|---|---|---|
| 管理画面の利用者 | ログイン（連続の失敗でロック）・トークンの更新・ログアウト | `auth`・`user`・`frontend-feature-auth` |
| 管理画面の利用者 | 自分の設定とパスワードの変更 | `user`・`frontend-feature-preferences`・`frontend-app-display-settings` |
| 管理者 | 管理の入口（`/admin`）と管理者の確かめ（`GET /api/admin/check`） | `access`・`frontend-feature-admin` |
| 管理者 | 利用者の招待・送り直し・取り消し・一覧 | `invitation`・`mail`・`frontend-feature-invitation` |
| 招待を受けた人 | リンクの確かめと登録の完了（ログインなし） | `invitation`・`frontend-feature-registration` |
| 管理者 | 既定の DSL の生成、DSL の投入・プレビュー・適用・破棄・履歴・ダウンロード（DSL にはメニューの木の定義を含む） | `dslmanage`・`dsl`・`targetdb`・`frontend-feature-dsl` |
| 管理者 | 利用者の管理（一覧・検索・氏名と言語・印の付け外し・停止と再開・失敗回数の取り消し） | `useradmin`・`frontend-feature-useradmin`・`user`・`auth`・`audit` |
| 運用者・監査者 | 認証・アクセスの拒否・DSL・招待と登録・パスワードの変更・利用者の管理の操作・初期管理者の作成と救済を、追記だけの監査ログ（内部DB）に残す。見る画面・API は無い | `audit` |
| 運用者（開発者を兼ねる） | 起動時の初期管理者の作成と救済（設定の値から） | `user`・`auth` |
| 開発者（運用者を兼ねる） | 1コマンドの検査（`./gradlew verify`）、コンテナでの起動、手元の監視、負荷の試験、E2E | `build-and-verify`・`container-runtime`・`perf-and-monitoring`・`frontend-e2e` |

上の表の機能の一覧は、ファイルとパッケージの名前の単位で確かめた（流し読み）。今回深く読んだのは下の Intent の論点に関わる部分だけで、部品ごとの読みの深さは `component-inventory.md` に書いた。

## この Intent（`261004-role-menu`）との関係

scope classic、深さ Standard。Intent F（ロールベースの権限の管理）と Intent I（メニュー・ナビゲーション（N 階層））を扱う。依頼の論点と、今のコードで分かったことの対応は次のとおり。

| 依頼の論点 | 今のコードで分かったこと（要点） | 所見 |
|---|---|---|
| F: 権限の持ち方 | 権限は `users.admin_flag` の真偽値1つ。主体 `AuthenticatedUser` も真偽値だけを運ぶ。権限は要求ごとに DB から読み、トークンには入れない | K-32 |
| F: API の認可 | URL の決まりは「`/api/admin/**` は管理者だけ」の1つ。操作・画面ごとの区別は無い。403 の理由は `NOT_ADMIN` だけ | K-33 |
| F: 既存の業務の決まりとの関係 | 管理者の印は最後の管理者の保護・操作した人の確かめ直し・監査の種類・`useradmin` の境界テストに組み込まれている | K-34 |
| F・I: 画面の出し分け | ログイン状態・画面の登録・サイドバーの表示の条件・403 の判定がどれも真偽値 `admin` と `/api/admin/` の接頭辞が前提 | K-35 |
| I: N 階層のメニューの描画 | 骨組みのサイドバーは平ら。make-you-chic-ui の `Sidebar` に入れ子・今の項目の表示の口は無く、アイコンは 18 種類 | K-36 |
| I: メニューの定義 | N 階層のメニューの定義は DSL の `menus` にすでにあり、`ActiveDslModelProvider` で読めるが、画面のナビゲーションには使われていない。メニューが指すテーブルの画面はまだ無い | K-37 |
| F・I 共通: 波及 | 監査の種類の列は 32 文字、`access` に境界テストが無く、`access.service` は `packagesJudgedByTotal` に入っている | K-38 |

### 所見の一覧

前回の所見は K-25〜K-31 で終わるため、今回は開発担当の R-1〜R-7 を順に **K-32〜K-38** とした。本文は持ち主の文書に1か所だけ書き、ここには題だけを並べる。事実と見立て（仮説）の区別は各所見の本文にある。

| ID | 開発担当の番号 | 所見の題 | 書いた場所 |
|---|---|---|---|
| K-32 | R-1 | 権限の持ち方は「管理者の印」の真偽値1つだけで、要求ごとに DB から読む | `architecture.md` の Interaction Diagrams 1 |
| K-33 | R-2 | URL による認可は「`/api/admin/**` は管理者だけ」の1つの決まり | `architecture.md` の Interaction Diagrams 1 |
| K-34 | R-3 | 「管理者」は利用者の管理の業務の決まりにも組み込まれている | `architecture.md` の Interaction Diagrams 2 |
| K-35 | R-4 | 画面の出し分けも真偽値 `admin` と `/api/admin/` の接頭辞だけ | `architecture.md` の Interaction Diagrams 3 |
| K-36 | R-5 | サイドバーは平らで、N 階層のメニューの部品は make-you-chic-ui に無い | `component-inventory.md` の `make-you-chic-ui` |
| K-37 | R-6 | N 階層のメニューの定義は、すでに DSL の中にある | `api-documentation.md` の「アプリの中の口」 |
| K-38 | R-7 | 監査・境界テスト・カバレッジへの波及 | `code-quality-assessment.md` |

### 前の所見・記録とのつながり

- K-32・K-33 は、`AdminAuthorizationManager` の注記「後続 Intent F で役割・権限の判定を足す場所はここになる（ADR-003）」（33 行）に当たる。前の Intent の設計がこの Intent を見込んで置いた口である（今回確かめた事実）。
- K-37 の `ActiveDslModelProvider` は、Intent `260923-dsl-schema-loader` の契約 C8（後続の Intent への提供口）である（Javadoc による）。
- K-38 の `packagesJudgedByTotal` は、前回の K-30（一覧は今 7 個）の続きである。
- K-25〜K-31（Intent `261004-safety-carryover`）の手当ての中身は、今回の走査では確かめていない。`AuditEventType` に `INITIAL_ADMIN_CREATED`・`INITIAL_ADMIN_RESCUED` があること（K-25 の手当て）だけを今回見た。
