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
| 運用者（開発者を兼ねる） | 起動時の初期管理者の自動作成（設定の値から、2回目以降は作らない） | `user` |
| 開発者（運用者を兼ねる） | 1コマンドの検査（`./gradlew verify`）、コンテナでの起動、手元の監視（Grafana）、負荷の試験、E2E | `build-and-verify`・`container-runtime`・`perf-and-monitoring`・`frontend-e2e` |

今回の走査は範囲を絞った深さ Minimal で、上の機能の多くは前回（`e68f54d`）までの記録のままである（読みの深さは `component-inventory.md` の各部品）。

## この Intent（`260929-log-deps-cleanup`）との関係

scope bugfix。依頼の文の原文は次のとおり。

> 初期管理者の作成のログからメールアドレスを外し、make-you-chic-ui の固定先を 077f5b4 に更新して E2E の既知の違反を外し、Dependabot の spotless と @types/node を取り込み、Jackson をすべての版で ignore にし、TypeScript 7 と typescript-eslint の ignore を決め、ms-check-p95 の境界を直す。team.mdも修正する。

今のコードとの差・直すときに当たる決まりは、下の所見として持ち主の文書に1か所ずつ書いた（ここでは題だけを並べる）。番号は前回の K-1〜K-10 に続けた。事実と見立て（仮説）の区別は、各所見の本文に書いた。

| ID | 所見の題 | 書いた場所 |
|---|---|---|
| K-11 | 初期管理者の作成の INFO のログ3か所がメールアドレスを出し、単体テストが「含まれること」を確かめている | `component-inventory.md` の `user` |
| K-12 | make-you-chic-ui の固定先の更新（`310e1ec` → `077f5b4`）の中身と、更新の手順の決まり | `component-inventory.md` の `make-you-chic-ui` |
| K-13 | E2E の 100 の既知の違反の一覧（2件）は「当たらなくなる」と失敗する作りで、README にも同じ説明がある | `component-inventory.md` の `frontend-e2e` |
| K-14 | Dependabot の開いたプルリクエスト4件と ignore の今の形（spotless・@types/node・Jackson・TypeScript 7 と `@typescript-eslint/*`） | `dependencies.md` |
| K-15 | `ms-check-p95` のしきい値 300 ms が `http.server.requests` のバケットの境界に無い | `architecture.md` の Interaction Diagrams 1 |
| K-10 | `team.md` の `packagesJudgedByTotal` の記述（22 個）が今のビルド（12 個）より古い（前回からの続き、まだ直っていない） | `code-quality-assessment.md` |
| K-16 | README の古い記述（make-you-chic-ui の固定先 `735ef04`）と、固定先を上げた後に書き直す節 | `code-quality-assessment.md` |

## 前回の所見（K-1〜K-9）の扱い

前回（Intent `260928-quality-followup`、記録のコミット `e68f54d`）の所見は、その Intent の中で直された。下の「直したコミット」はコミットの件名から対応づけたもので、今回の範囲の外のもの（K-3・K-4・K-5・K-8・K-9）は中身を確かめていない。

| ID | 前回の題 | 直したコミット（件名による） | 今回の扱い |
|---|---|---|---|
| K-1 | make-you-chic-ui の固定先 `735ef04` → `310e1ec` | `62876a3` | 今の固定先が `310e1ec` であることを確かめた。次の更新は K-12 |
| K-2 | E2E の既知の違反の一覧（050〜080） | `79a0395` | `support/axe.ts` の一覧が空になったことを確かめた。残りは 100 の2件（K-13） |
| K-3 | アプリ自身の CSS の文字の色 | `79a0395` | 確かめていない |
| K-4 | `H2CompactionByPoolSuspensionIT` の 10 秒の時間切れ | `b20bf1e` | 確かめていない |
| K-5 | `InvitationAdminPage.test.tsx` の 5 秒の時間切れ | `b20bf1e` | 確かめていない |
| K-6 | p95 の警報のバケットが無い | `346c719` | 境界のバケットがあることを確かめた（流し読み）。残る境界の食い違いは K-15 |
| K-7 | Dependabot の作業ブランチと固定の決まり | `de75b81` | ignore の今の形を確かめた。今の開いた知らせは K-14 |
| K-8 | 承認済みの運用の記録の誤り | `7c2fea4`（README の節） | 確かめていない |
| K-9 | 運用の手順書の置き場 | `7c2fea4`（README に「警報と対応の手順」の節） | 節があることを確かめた（`ms-check-p95` の行を含む） |
| K-10 | `team.md` の記述が古い | 直っていない | 今回の依頼の「team.md も修正する」の対象の候補（K-10） |
