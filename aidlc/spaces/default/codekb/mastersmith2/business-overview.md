# 業務の概要（mastersmith2）

## 目的

mastersmith2 は MasterSmith（マスタ管理アプリ）のリポジトリである。管理画面の土台（ログイン・アカウントロック・管理の API の認可・監査ログ）の上に、業務データの画面の元になる DSL（YAML）を扱う機能、利用者の招待と登録の完了、自分の設定（言語・テーマ・文字の大きさ・パスワード）、管理者による利用者の管理（一覧・氏名と言語の変更・管理者の印・利用停止と再開・ログインの失敗回数の取り消し）がある。業務データそのもの（マスタの一覧・登録・更新）の画面は、まだ無い。

配備先は当面、開発者の PC 上のコンテナ（colima）だけである（`team.md` の Deployment）。メールは外部の SMTP へ送らず、手元では Mailpit で受ける（`project.md` の Forbidden）。

## 利用者と、今ある機能

| 利用者 | 機能 | 主な部品（詳しくは `component-inventory.md`） |
|---|---|---|
| 管理画面の利用者 | ログイン（連続の失敗でロック）・トークンの更新・ログアウト | `auth`・`user`・`frontend-feature-auth` |
| 管理画面の利用者 | 自分の設定とパスワードの変更 | `user`・`frontend-feature-preferences`・`frontend-app-display-settings` |
| 管理者 | 管理の入口（`/admin`）と管理者の確かめ | `access`・`frontend-feature-admin` |
| 管理者 | 利用者の招待・送り直し・取り消し・一覧 | `invitation`・`mail`・`frontend-feature-invitation` |
| 招待を受けた人 | リンクの確かめと登録の完了（ログインなし） | `invitation`・`frontend-feature-registration` |
| 管理者 | 既定の DSL の生成、DSL の投入・プレビュー・適用・破棄・履歴・ダウンロード | `dslmanage`・`dsl`・`targetdb`・`frontend-feature-dsl` |
| 管理者 | 利用者の管理（一覧・検索・氏名と言語・印の付け外し・停止と再開・失敗回数の取り消し） | `useradmin`・`frontend-feature-useradmin`・`user`・`auth`・`audit` |
| 運用者・監査者 | 認証・アクセスの拒否・DSL・招待と登録・パスワードの変更・利用者の管理の操作を、追記だけの監査ログ（内部DB）に残す。見る画面・API は無い | `audit` |
| 運用者（開発者を兼ねる） | 起動時の初期管理者の自動作成（設定の値から。同じメールアドレスの利用者がいれば何もしない）。作成は監査に残らない（K-25） | `user` |
| 開発者（運用者を兼ねる） | 1コマンドの検査（`./gradlew verify`）、コンテナでの起動、手元の監視、負荷の試験、E2E | `build-and-verify`・`container-runtime`・`perf-and-monitoring`・`frontend-e2e` |

上の表の機能の一覧は、ファイルとパッケージの名前の単位で確かめた（流し読み）。今回深く読んだ範囲は下の Intent の論点に関わる部分だけで、部品ごとの読みの深さは `component-inventory.md` に書いた。

## この Intent（`261004-safety-carryover`）との関係

scope bugfix、深さ Minimal。Intent `260930-user-admin` の振り返りの第2の束と、Intent `261003-user-admin-followup` の持ち越しを扱う。依頼の論点と、今のコードで分かったことの対応は次のとおり。

| 依頼の論点 | 今のコードで分かったこと（要点） | 所見 |
|---|---|---|
| S2: 使える管理者がいなくなったときの救済の口と、初期管理者の作成の監査 | 作成の判定は「そのメールアドレスの利用者がいれば何もしない」で、既にいる利用者の停止を解く・印を付ける口は無い。作成は監査に残らず、`audit_events.source_ip` は `NOT NULL` | K-25 |
| P1: ログインの p95 の余裕の縮み | 停止の判定は照合の前に読んだ `users` の1行の値を見るだけで、問い合わせは増えていない | K-26 |
| 持ち越し: BUSY の L3・L4 の `traceId` | BUSY は行の排他の待ち 3000 ms の上限切れでだけ起き、L3・L4 は同じ要求のスレッドで出る。既存のテストは `traceId` を確かめていない | K-27 |
| 持ち越し: Tomcat の `Servlet.service()` の ERROR | フィルターの中の例外は `@RestControllerAdvice` に届かず、`/error` の ERROR と二重になりうる（見立て） | K-28 |
| 持ち越し: 言語の欄で Enter を押すとフォーカスが body に落ちる | 送信中に言語の `RadioGroup` を `disabled` にしている。make-you-chic-ui の `RadioGroup` に読み取り専用の口は無い | K-29 |
| 片付け: `team.md` の「12 パッケージ」 | `packagesJudgedByTotal` は今 7 個 | K-30 |
| 片付け: 対象DB のイメージの固定先と `Dockerfile` のダイジェスト | 同じ版とダイジェストが3か所に手で書かれ、Dependabot は `directory: /` だけを見る。`Dockerfile` の `FROM` ほかはダイジェストなし | K-31 |

### 所見の一覧

前回の所見は K-17〜K-24 で終わるため、今回は **K-25 から** 振った。本文は持ち主の文書に1か所だけ書き、ここには題だけを並べる。事実と見立て（仮説）の区別は各所見の本文にある。

| ID | 所見の題 | 書いた場所 |
|---|---|---|
| K-25 | 初期管理者の作成は監査に残らず、既にいる利用者を救う口も無い | `architecture.md` の Interaction Diagrams 1 |
| K-26 | ログインでの停止の判定は DB の読み取りを増やしていない | `architecture.md` の Interaction Diagrams 2 |
| K-27 | 409 `USER_ADMIN_BUSY` は行の排他の待ちが 3 秒を超えたときだけ起き、負荷だけでは起きにくい | `architecture.md` の Interaction Diagrams 3 |
| K-28 | フィルターの中の例外は Tomcat の ERROR と `/error` の ERROR の2回出うる | `architecture.md` の Interaction Diagrams 4 |
| K-29 | 言語の欄で Enter で送信すると、押せなくした選択肢からフォーカスが外れる | `component-inventory.md` の `frontend-feature-useradmin` |
| K-30 | `team.md` の「12 パッケージ」は古く、一覧は今 7 個 | `code-quality-assessment.md` |
| K-31 | 対象DB のイメージの固定先が3か所にあり、Dependabot は1か所しか見ない | `dependencies.md` |

### 前の所見とのつながり（前回までの記録。今回は確かめ直していない）

- K-29 は、前の Intent の K-19（送信中も言語の欄を選び直せる）の直し（前の Intent の FR3.1、`disabled` にする形）の後に残った点である。
- K-27 は、前の Intent の K-20（負荷の試験が上限に届かない）の後の T1 の負荷で BUSY が 0 件だったこと（前の Intent の記録）とつながる。
- K-30 は、前回までの K-7（触るとカバレッジの作業が付くパッケージ）の続きである。
- K-17〜K-24 の手当ては Intent `261003-user-admin-followup` の記録にある。今回の走査はそれらの直しの中身を確かめていない。
