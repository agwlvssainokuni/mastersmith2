# 業務の概要（mastersmith2）

## 目的

mastersmith2 は、MasterSmith（マスタ管理アプリ）のリポジトリである。管理画面の土台（ログイン・アカウントロック・管理者向け API の認可・監査ログ）の上に、業務データの画面を組み立てる元になる DSL（YAML）を扱う機能と、利用者の招待・登録の完了・自分の設定（言語・テーマ・文字の大きさ・パスワード）の機能がある。業務データそのもの（マスタの一覧・登録・更新）の画面と、**管理者が既にいる利用者を管理する画面**は、まだ無い。

配備先は当面、開発者の PC 上のコンテナ（colima）だけである（`team.md` の Deployment）。メールは外部の SMTP へは送らず、手元では Mailpit で受ける（`project.md` の Forbidden）。

## 利用者と、今ある機能

| 利用者 | 機能 | 主な部品（詳しくは `component-inventory.md`） |
|---|---|---|
| 管理画面の利用者 | ログイン（連続の失敗でロック。解除は時間の経過だけ）・トークンの更新・ログアウト | `auth`・`user`・`frontend-feature-auth` |
| 管理画面の利用者 | 自分の設定とパスワードの変更 | `user`・`frontend-feature-preferences`・`frontend-app-display-settings` |
| 管理者 | 管理の入口（`/admin`）と管理者の確かめ（`GET /api/admin/check`） | `access`・`frontend-feature-admin` |
| 管理者 | 利用者の招待（招待メールの送信）・送り直し・取り消し・一覧 | `invitation`・`mail`・`frontend-feature-invitation` |
| 招待を受けた人 | リンクの確かめと登録の完了（ログインなし） | `invitation`・`frontend-feature-registration` |
| 管理者 | 既定の DSL の生成、DSL の投入・プレビュー・適用・破棄・履歴・ダウンロード | `dslmanage`・`dsl`・`targetdb`・`frontend-feature-dsl` |
| 運用者・監査者 | 認証・アクセス拒否・DSL の操作・招待と登録・パスワードの変更を、追記だけの監査ログとして内部DB に残す（見る画面・API は無い） | `audit` |
| 運用者（開発者を兼ねる） | 起動時の初期管理者の自動作成（設定の値から。同じメールアドレスの利用者がいれば作らない） | `user` |
| 開発者（運用者を兼ねる） | 1コマンドの検査（`./gradlew verify`）、コンテナでの起動、手元の監視、負荷の試験、E2E | `build-and-verify`・`container-runtime`・`perf-and-monitoring`・`frontend-e2e` |

利用者の管理の4つの操作（一覧・管理者の印の変更・利用停止・ロックの解除）は、API・業務処理・DB の問い合わせ・画面のどの層にも無い（`api-documentation.md`）。今ある管理の手段は、DB を直接直すことと、ロックの解除時刻を待つことだけである。

今回の走査は全体の読み直し（Full rescan）・深さ Standard で、今回の Intent に関わる範囲（利用者・認証とロック・認可・監査・招待の管理の型・画面の差し込み口・境界の検査・ビルドのカバレッジ）を深く読んだ。ほかの部品は流し読みで、読みの深さは `component-inventory.md` の各部品に書いた。

## この Intent（`260930-user-admin`）との関係

scope classic、深さ Standard。依頼の文の原文は次のとおり。

> 利用者の管理の画面を作る（一覧・管理者の印・利用停止・ロックの解除）

今のコードとの差・作るときに当たる決まりは、下の所見として持ち主の文書に1か所ずつ書いた（ここでは題だけを並べる）。事実と見立て（仮説）の区別は、各所見の本文に書いた。

### 所見の番号の振り方

全体の読み直しのため、所見の番号は **K-1 から振り直した**。前回までの知識ベース（Intent `260929-log-deps-cleanup` の K-10〜K-16 ほか）の番号とは別のもので、同じ番号でも中身は対応しない。開発担当の走査（`inception/reverse-engineering/developer-scan.md`）の仮の番号 S-1〜S-9 を、同じ順で K-1〜K-9 にした。前回の所見の扱いは下の「前回の所見の扱い」にまとめた。

| ID | 所見の題 | 開発担当の番号 | 書いた場所 |
|---|---|---|---|
| K-1 | 利用停止を表す状態が無く、止めるには認証の3つの入口で状態を見る必要がある | S-1 | `architecture.md` の Interaction Diagrams 1 |
| K-2 | ロックの状態は `auth` の表にあり、ロック中かは時刻の比べで決まる。行の無い利用者がありうる | S-2 | `component-inventory.md` の `auth` |
| K-3 | 境界の決まり: `user` は `auth` を知らない。利用者とロックの状態を合わせる処理は `user` の外になる | S-3 | `dependencies.md` |
| K-4 | 管理者の印は要求ごとに DB から読まれるが、画面は古い値を持ち続ける。最後の管理者を守る仕組みが無い | S-4 | `architecture.md` の Interaction Diagrams 2 |
| K-5 | 一覧・ページ送り・操作・結果の型の前例は `invitation` の管理の API にある | S-5 | `api-documentation.md` |
| K-6 | 監査の列（操作した人・対象の利用者）は足りているが、管理の操作の種類と出来事の型は無い | S-6 | `component-inventory.md` の `audit` |
| K-7 | 触るとカバレッジの下限を満たす作業が付くパッケージがある（`packagesJudgedByTotal`） | S-7 | `code-quality-assessment.md` |
| K-8 | 一覧の型と応答は、TRACE のログとメールアドレスの決まりに当たる | S-8 | `component-inventory.md` の `user` |
| K-9 | 画面の差し込み口と、同じ型の管理の画面の見本 | S-9 | `code-structure.md` |

所見のほかの技術的負債（監査の3ファイルの肥大・要求の文脈の読み取りの複製・パスワードの変更の後のリフレッシュトークン・大きめの hook など）は `code-quality-assessment.md` の一覧にある。

## 前回の所見の扱い

前回（Intent `260929-log-deps-cleanup`、記録のコミット `c1ed553`）の所見 K-10〜K-16 は、その Intent の中で直された。下の「直したコミット」はコミットの件名から対応づけたもので、「今回の確かめ」に書いた点だけを今の HEAD で確かめた。

| 前回の ID | 前回の題 | 直したコミット（件名による） | 今回の確かめ |
|---|---|---|---|
| K-10 | `team.md` の `packagesJudgedByTotal` の記述が古い | `f5fc7ed` | `team.md` の Testing Posture は 12 パッケージと書き、`backend/build.gradle.kts` 221〜234 行の一覧（12 個）と一致する |
| K-11 | 初期管理者の作成のログがメールアドレスを出す | `5dd6739` | INFO のキーが `maskedEmail` の伏せ字になった（`user/service/InitialAdminInitializer.java` 41〜42・92 行） |
| K-12・K-13 | make-you-chic-ui の固定先と E2E の 100 の既知の違反 | `ac0d007`・`20c9f96` | 固定先は `077f5b48ce84cd020ecec2d925836a085f9d9e11`（`git submodule status`）。既知の違反の一覧は空（開発担当が `frontend/e2e/100-app-text-contrast.e2e.ts` 73 行で確かめた） |
| K-14 | Dependabot の開いた知らせと ignore | `355e964`・`8489241` | 確かめていない（`.github/dependabot.yml` は開発担当が読んだが、前回との比べは今回の範囲の外） |
| K-15 | `ms-check-p95` のしきい値が境界に無い | `27be598`（500 ms に上げる） | 警報のファイルは読んでいない |
| K-16 | README の古い記述 | `20c9f96` | 確かめていない |
