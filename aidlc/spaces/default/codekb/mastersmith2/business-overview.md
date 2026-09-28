# 業務の概要（mastersmith2）

## 目的

mastersmith2 は、MasterSmith（マスタ管理アプリ）のリポジトリである。管理画面の土台（ログイン・管理者向け API の認可・監査ログ）の上に、業務データの画面を組み立てる元になる DSL（YAML）を扱う機能と、利用者の招待・登録の完了・プリファレンス（言語・テーマ・文字の大きさ・パスワード）の機能がある。業務データそのもの（マスタの一覧・登録・更新）の画面は、まだ無い。

配備先は当面、開発者の PC 上のコンテナ（colima）だけである（`team.md` の Deployment）。メールは外部の SMTP へは送らず、手元では Mailpit で受ける（`project.md` の Forbidden）。

## 利用者と、今ある機能

| 利用者 | 機能 | 主な部品（詳しくは `component-inventory.md`） |
|---|---|---|
| 管理画面の利用者 | ログイン（連続の失敗でロック）・トークンの更新・ログアウト | `auth`・`user`・`frontend-feature-auth` |
| 管理画面の利用者 | 自分のプリファレンスとパスワードの変更 | `user`・`frontend-feature-preferences`・`frontend-app-display-settings` |
| 管理者 | 利用者の招待（招待メールの送信）・送り直し・取り消し・一覧 | `invitation`・`mail`・`frontend-feature-invitation` |
| 招待を受けた人 | リンクの確かめと登録の完了（ログインなし） | `invitation`・`frontend-feature-registration` |
| 管理者 | 既定の DSL の生成、DSL の投入・プレビュー・適用・破棄・履歴・ダウンロード | `dslmanage`・`dsl`・`targetdb`・`frontend-feature-dsl` |
| 運用者・監査者 | 認証・アクセス拒否・DSL の操作・招待と登録・パスワードの変更を、追記だけの監査ログとして内部DB に残す | `audit` |
| 開発者（運用者を兼ねる） | 1コマンドの検査（`./gradlew verify`）、コンテナでの起動、手元の監視（Grafana）、負荷の試験、E2E | `build-and-verify`・`container-runtime`・`perf-and-monitoring`・`frontend-e2e` |

今回の走査は深さ Minimal で、上の機能の多く（`auth`・`user`・`invitation` の大半・`appearance`・画面の多く）は一覧と検索までしか読んでいない（読みの深さは `component-inventory.md` の各部品）。

## この Intent（`260928-quality-followup`）との関係

scope bugfix。前の Intent（`260925-user-management`）で受け入れた失敗・未達と、承認済みの運用の記録の誤りを直す。依頼の文の原文は次のとおり。

> コントラストの Not Met（make-you-chic-ui の固定先を 7865c28・310e1ec に更新）、CI の2つの時間切れ、http.server.requests と mastersmith.mail.send の p95 のバケット（p95 の警報3件を働かせる）、Dependabot の開いた知らせの取り込みを直す。あわせて alarms.md・log-queries.md・runbooks.md の RB-17 の誤りを README と手順書で正す。

今のコードとの差・直すときに当たる決まりは、下の所見として1か所ずつ書いた（ここでは題だけを並べる）。事実と見立て（仮説）の区別は、各所見の本文に書いた。

| ID | 所見の題 | 書いた場所 |
|---|---|---|
| K-1 | make-you-chic-ui の固定先の更新（`735ef04` → `310e1ec`）の中身と、更新の手順の決まり | `component-inventory.md` の `make-you-chic-ui` |
| K-2 | E2E の既知の違反の一覧は「当たらなくなる」と失敗する作りで、E2E は `verify` と CI の外にある | `component-inventory.md` の `frontend-e2e` |
| K-3 | make-you-chic-ui の直しが及ばない、アプリ自身の CSS の文字の色 | `code-quality-assessment.md` |
| K-4 | `H2CompactionByPoolSuspensionIT` の 10 秒の時間切れ | `architecture.md` の Interaction Diagrams 3 |
| K-5 | `InvitationAdminPage.test.tsx` の1件が Vitest の既定の 5 秒で動く | `code-quality-assessment.md` |
| K-6 | `http.server.requests`・`mastersmith.mail.send` にヒストグラムのバケットが無く、p95 の警報3件が値を持たない | `architecture.md` の Interaction Diagrams 1 |
| K-7 | Dependabot の作業ブランチ 15 本と、既存の決まりとぶつかる更新 | `dependencies.md` |
| K-8 | 承認済みの運用の記録の誤り（登録の完了の拒否は監査に残る、送信の失敗は警報に当たらない） | `api-documentation.md` |
| K-9 | 運用の手順書・警報の説明・ログの問い合わせがリポジトリの中（`aidlc/` の外）に無い | `code-quality-assessment.md` |
| K-10 | `team.md` の `packagesJudgedByTotal` の記述（22 個）が今のビルド（12 個）より古い | `code-quality-assessment.md` |
