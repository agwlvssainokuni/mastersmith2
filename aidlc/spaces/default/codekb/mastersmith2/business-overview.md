# 業務の概要（mastersmith2）

## 目的

mastersmith2 は MasterSmith（マスタ管理アプリ）のリポジトリである。管理画面の土台（ログイン・アカウントロック・管理の API の認可・監査ログ）の上に、業務データの画面の元になる DSL（YAML）を扱う機能、利用者の招待と登録の完了、自分の設定（言語・テーマ・文字の大きさ・パスワード）、管理者による利用者の管理（一覧・氏名と言語の変更・管理者の印・利用停止と再開・ログインの失敗回数の取り消し）、起動時の初期管理者の作成と救済がある。業務データそのもの（マスタの一覧・登録・更新）の画面は、まだ無い。

Intent `261004-role-menu` で、ロール・グループによる権限の設定と解決（`role`・`group`）、権限の YAML の書き出しと取り込み（`role/transfer`）、N 階層のメニュー（`navigation`、画面のサイドバーは make-you-chic-ui の入れ子のサイドバー）、作業ロールの切り替え（`frontend/src/app/work-role/`）が加わった（今回、パッケージとディレクトリの構成で確かめた）。管理の可否は今も `users.admin_flag` で決まる（振り返りの F2。前回までの記録 K-32〜K-35 は `261004-role-menu` の前の状態を書いたもので、今回は確かめ直していない）。

配備先は当面、開発者の PC 上のコンテナ（colima）だけである（`team.md` の Deployment）。メールは外部の SMTP へ送らず、手元では Mailpit で受ける（`project.md` の Forbidden）。

## 利用者と、今ある機能

| 利用者 | 機能 | 主な部品（詳しくは `component-inventory.md`） |
|---|---|---|
| 管理画面の利用者 | ログイン（連続の失敗でロック）・トークンの更新・ログアウト | `auth`・`user`・`frontend-feature-auth` |
| 管理画面の利用者 | 自分の設定とパスワードの変更、自分の権限を見る、作業ロールの切り替え | `user`・`role`・`frontend-feature-preferences`・`frontend-feature-mypermissions`・`frontend-app-layout-i18n` |
| 管理者 | 管理の入口（`/admin`）と管理者の確かめ（`GET /api/admin/check`） | `access`・`frontend-feature-admin` |
| 管理者 | 利用者の招待・送り直し・取り消し・一覧 | `invitation`・`mail`・`frontend-feature-invitation` |
| 招待を受けた人 | リンクの確かめと登録の完了（ログインなし） | `invitation`・`frontend-feature-registration` |
| 管理者 | 既定の DSL の生成、DSL の投入・プレビュー・適用・破棄・履歴・ダウンロード（DSL にはメニューの木の定義を含む） | `dslmanage`・`dsl`・`targetdb`・`frontend-feature-dsl` |
| 管理者 | 利用者の管理（一覧・検索・氏名と言語・印の付け外し・停止と再開・失敗回数の取り消し） | `useradmin`・`frontend-feature-useradmin`・`user`・`auth`・`audit` |
| 管理者 | ロール・グループの管理、割り当て、権限の YAML の書き出しと取り込み | `role`・`group`・`frontend-feature-roleadmin`・`frontend-feature-groupadmin`・`frontend-feature-roletransfer` |
| 管理画面の利用者 | 権限で枝を落とした N 階層のメニュー、テーブルを指す項目の準備中の画面 | `navigation`・`frontend-registry`・`frontend-feature-tables` |
| 運用者・監査者 | 認証・アクセスの拒否・DSL・招待と登録・パスワードの変更・利用者の管理・ロールの操作・初期管理者の作成と救済を、追記だけの監査ログ（内部DB）に残す。見る画面・API は無い | `audit` |
| 開発者（運用者を兼ねる） | 1コマンドの検査（`./gradlew verify`）、コンテナでの起動、手元の監視、負荷の試験、E2E | `build-and-verify`・`container-runtime`・`perf-and-monitoring`・`frontend-e2e` |

上の表の機能の一覧は、パッケージとディレクトリの名前の単位で確かめた（流し読み）。今回深く読んだのは下の束の項目に関わる部分だけで、部品ごとの読みの深さは `component-inventory.md` に書いた。

## この Intent（`261015-deps-ui-carryover`）との関係

scope bugfix、深さ Minimal。Intent `261004-role-menu` の振り返り（`aidlc/spaces/default/intents/261004-role-menu/operation/feedback-optimization/feedback-loop.md` 2節）の第1の束（依存の更新と性能の確かめ直し、U1・P1〜P7）と第2の束（画面・コードの小さな直し、K1〜K5）を、同じ Intent で扱う。

| 束の項目 | 今のコードで分かったこと（要点） | 所見 |
|---|---|---|
| U1 依存の更新 20 件 | 内訳は振り返りと一致。eclipse-temurin の jre を上げると、Dependabot が見ない同じ版の jdk（`docker/hikari-pool.sh` ほか）も手でそろえる必要がある。grafana/k6 には知らせの経路が無い | K-39 |
| P1 ロールの書き込みの待ちの最大 | 書き込みは1つ目のトランザクションで排他 → 判定 → 書き込みを行い、確定の後に監査が2本目の接続を借りる。1回だけの外れの原因はコードからは分からない | K-40 |
| P2 大きな入力のヒープ | YAML の読み込みに1行の長さの上限が無い。本文が `byte[]`・`String`・節の木・JSON の形で同時に生きる | K-41 |
| P3・P5 k6 の台本 | ロールの setup が前の場面の割り当てに頼り、`workRoleRead` はロールの数 2 に頼る。DSL と権限の YAML を重ねる場面は無い | K-42 |
| P4 BUSY の L3・L4 | 行の排他の待ちは 3,000 ms。k6 の場面は VU ごとに別の行を使うため、待ちの上限切れが起きない（見立て） | K-40 |
| P6 置き場の p95 の目標 | 目標の文書（設計）の論点で、今回のコードの走査の対象外 | — |
| P7 単体テストのヒープ | 単体テストだけ `1536m`。ArchUnit のテストの多くがクラスを別々に読み込んで持つ | K-43 |
| K1 make-you-chic-ui の口待ち | Dropdown の幅をアプリの CSS の `:has()` で上書きしている。トップバーの文言の口は無い | K-44 |
| K2 R-05・K3 R-03 伏せ字 | `DslError` と `navigation` の 7 つの record が `toString` を上書きしていない | K-45 |
| K3 R-04 `MenuFilter` の前提 | 再帰の安全を `DslFormat.MAX_MENU_DEPTH = 5` に頼る Javadoc | K-46 |
| K4 R-02 共有の木の読み上げ | 読み込みの失敗の表示に `role`・`aria-live` が無い | K-47 |
| K5 `useSlowNotice` の複製 | 複製は3つ（振り返りの文言は2つ） | K-48 |
| K2 R-04・R-07・R-08、K4 R-03・R-05〜R-07 | 指摘の本文が今回の走査の範囲の外のため、対象のファイルを特定していない。要件定義で指摘の本文と突き合わせる | — |

### 所見の一覧

前回の所見は K-38 で終わるため、今回は **K-39〜K-48** とした。本文は持ち主の文書に1か所だけ書き、ここには題だけを並べる。事実と見立て（仮説）の区別は各所見の本文にある。

| ID | 束の項目 | 所見の題 | 書いた場所 |
|---|---|---|---|
| K-39 | U1 | 依存の更新 20 件と、手でそろえる固定先 | `dependencies.md` |
| K-40 | P1・P4 | ロール・グループの書き込みの2つのトランザクションと、BUSY が出ない理由 | `architecture.md` の Interaction Diagrams 5 |
| K-41 | P2 | YAML の読み込みに1行の上限が無く、本文が複数の形で同時に生きる | `architecture.md` の Interaction Diagrams 6 |
| K-42 | P3・P5 | k6 の台本のロールの場面が前の場面の状態に頼る | `code-quality-assessment.md` |
| K-43 | P7 | 単体テストの JVM のヒープと ArchUnit の読み込み | `code-quality-assessment.md` |
| K-44 | K1 | make-you-chic-ui の口が無い所をアプリの CSS で上書きしている | `component-inventory.md` の `make-you-chic-ui` |
| K-45 | K2 R-05・K3 R-03 | 利用者の値を持つ record の `toString` の伏せ字の抜け | `code-quality-assessment.md` |
| K-46 | K3 R-04 | `MenuFilter` の再帰の安全が DSL の上限に頼る | `component-inventory.md` の `navigation` |
| K-47 | K4 R-02 | 共有の木の読み込みの失敗が読み上げられない | `component-inventory.md` の `frontend-app-core` |
| K-48 | K5 | `useSlowNotice` が3つの機能に複製されている | `code-structure.md` |

### 前の所見・記録とのつながり

- K-32〜K-38（Intent `261004-role-menu` の開始時）は、その Intent で手当てされた前の状態の所見である。手当ての中身は今回確かめていない。本文は各文書に「前回までの記録」として残した。
- K-40 は `project.md` の Corrections「要求1件で接続を2本使う経路があるときは、同時の数がプールの上限に達する場合を負荷の試験で確かめる」と、学び「接続の待ちの最大のように数 ms の目標は合否の回を複数回流す」（2026-10-15）の続きである。
- K-41 は `project.md` の学び「大きな入力のヒープの見積もりは、設計の段で試しの測定で裏付ける」（2026-10-15）と、Tech Stack の「深さと別名の上限を LoaderOptions と LimitingParser の両方に渡す」の続きである。
- K-42 は `project.md` の学び「k6 の場面の setup は前の場面が残した状態に頼らない」（2026-10-15）の続きである。
- K-44 は `team.md` の Way of Working（make-you-chic-ui に無い部品は上流へ先に依頼する）と、コミット `425ab64`（make-you-chic-ui への依頼（3）の文面）に当たる。
