# 業務の概要（mastersmith2）

## 目的

mastersmith2 は、MasterSmith（マスタ管理アプリ）のリポジトリである。管理画面の土台（ログイン・アカウントロック・管理者向け API の認可・監査ログ）の上に、業務データの画面を組み立てる元になる DSL（YAML）を扱う機能と、利用者の招待・登録の完了・自分の設定（言語・テーマ・文字の大きさ・パスワード）の機能がある。業務データそのもの（マスタの一覧・登録・更新）の画面は、まだ無い。管理者が既にいる利用者を管理する画面（一覧・氏名と言語の変更・管理者の印・利用停止と再開・ログインの失敗回数の取り消し）は、Intent `260930-user-admin` で作られた（`useradmin`・`frontend-feature-useradmin`。今回はファイルと API の口の存在と、束に関わる部分だけを確かめた）。

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
| 管理者 | 利用者の管理（一覧・検索・氏名と言語の変更・管理者の印の付け外し・利用停止と再開・ログインの失敗回数の取り消し） | `useradmin`・`frontend-feature-useradmin`・`user`・`auth`・`audit` |
| 運用者・監査者 | 認証・アクセス拒否・DSL の操作・招待と登録・パスワードの変更を、追記だけの監査ログとして内部DB に残す（見る画面・API は無い） | `audit` |
| 運用者（開発者を兼ねる） | 起動時の初期管理者の自動作成（設定の値から。同じメールアドレスの利用者がいれば作らない） | `user` |
| 開発者（運用者を兼ねる） | 1コマンドの検査（`./gradlew verify`）、コンテナでの起動、手元の監視、負荷の試験、E2E | `build-and-verify`・`container-runtime`・`perf-and-monitoring`・`frontend-e2e` |

監査を見る画面・API は今も無い（監査の行は内部DB にだけある）。

今回（2026-10-04、Intent `261003-user-admin-followup`）の走査は、範囲を絞った走査（Focused scan）・深さ Minimal で、束の8件に関わるファイル（利用者の管理の画面の閉じる・送信中の処理、E2E 110・120 とはみ出しの判定、利用者の管理と監査のテスト、監査の失敗のログ、利用者の作成と招待の一意の違反の扱い、`MailConfigurationIT`、負荷の試験の手順と台本、警報の3件）だけを深く読んだ。前回（Intent `260930-user-admin` の始め）に深く読んだ範囲は確かめ直しておらず、記録上は流し読みに下げた。読みの深さは `component-inventory.md` の各部品に書いた。

## この Intent（`261003-user-admin-followup`）との関係

scope bugfix、深さ Minimal。前の Intent `260930-user-admin` の振り返り（その Intent の `operation/feedback-optimization/feedback-loop.md`）の第1の束（K1〜K3・T1〜T4・S1）を直す。束の ID（`K1` など、ハイフンなし）は振り返りの ID で、下の所見の番号（`K-17` など、ハイフンあり）とは別のものである。

### 所見の番号の振り方（今回）

前回の所見は K-1〜K-9 で終わるが、この文書の「前々回の所見の扱い」の表にその前の知識ベースの番号 K-10〜K-16 が残っているため、取り違えを避けて今回の所見は **K-17 から** 振った。本文は持ち主の文書に1か所だけ書き、ここには題だけを並べる。事実と見立て（仮説）の区別は各所見の本文にある。

| ID | 所見の題 | 束の ID | 書いた場所 |
|---|---|---|---|
| K-17 | 確かめの表示と入力を閉じた後、実際のブラウザでフォーカスが body に落ちる。固定先の Modal の戻しの順による。上流 `e82b651` で直る見込みだが、`Dropdown` が trigger の `ref` を置き換える | K1 | `component-inventory.md` の `frontend-feature-useradmin`（流れは `architecture.md` の Interaction Diagrams 5） |
| K-18 | E2E 110・120 が閉じた後のフォーカスと、開いたメニュー（`position: fixed`）のはみ出しを確かめていない。行の「操作」は既定の `bottom-start` | K1・K2 | `component-inventory.md` の `frontend-e2e` |
| K-19 | 氏名と言語の入力で、送信中も言語の欄を選び直せる | K3 | `component-inventory.md` の `frontend-feature-useradmin` |
| K-20 | 負荷の試験のプールの上限 10 の (B) が上限に届かず、警報3件（`ms-error-logs`・`ms-audit-fail`・`ms-pool-pending`）が鳴らなかった | T1 | `component-inventory.md` の `perf-and-monitoring` |
| K-21 | 管理者の印を外した直後の 403 と監査の記録を1つのテストで確かめていない | T2 | `component-inventory.md` の `useradmin` |
| K-22 | `MailConfigurationIT` が JVM 全体の標準出力を捕まえ、別の文脈のログを取り込んで落ちうる | T3 | `component-inventory.md` の `mail` |
| K-23 | `perf/README.md` が hikaricp の待ちの値の単位（`baseUnit`）に触れていない | T4 | `component-inventory.md` の `perf-and-monitoring` |
| K-24 | 一意の制約の違反の例外の文（重なったメールアドレスを含む見立て）が、既定のログの水準でアプリのログに出うる | S1 | `architecture.md` の Interaction Diagrams 6 |

前回の所見のうち今回に関わるもの: K-8（TRACE のログとメールアドレスの決まり）は K-24 と同じ論点。K-5・K-9（管理の API と画面の見本）は利用者の管理の部品の元になった前例。K-7（`packagesJudgedByTotal`）は K-24 の直しで `common` に手が入ると関わる（今は 7 個、`code-quality-assessment.md`）。

### 前回の Intent（`260930-user-admin`）の所見の記録

以下は前回（2026-09-30、コミット `31b980b`、利用者の管理を作る前）の記録で、今回は確かめ直していない。K-1・K-4・K-5 の「無い」とした仕組みの多くは、その Intent で作られた。

前回の走査は全体の読み直し（Full rescan）・深さ Standard で、その Intent に関わる範囲（利用者・認証とロック・認可・監査・招待の管理の型・画面の差し込み口・境界の検査・ビルドのカバレッジ）を深く読んだ。

#### その Intent（`260930-user-admin`）との関係

scope classic、深さ Standard。依頼の文の原文は次のとおり。

> 利用者の管理の画面を作る（一覧・管理者の印・利用停止・ロックの解除）

今のコードとの差・作るときに当たる決まりは、下の所見として持ち主の文書に1か所ずつ書いた（ここでは題だけを並べる）。事実と見立て（仮説）の区別は、各所見の本文に書いた。

#### 所見の番号の振り方（前回）

全体の読み直しのため、所見の番号は **K-1 から振り直した**。前回までの知識ベース（Intent `260929-log-deps-cleanup` の K-10〜K-16 ほか）の番号とは別のもので、同じ番号でも中身は対応しない。開発担当の走査（`inception/reverse-engineering/developer-scan.md`）の仮の番号 S-1〜S-9 を、同じ順で K-1〜K-9 にした。前々回の所見の扱いは下の「前々回の所見の扱い」にまとめた。

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

## 前々回の所見の扱い（前回の記録のまま）

前々回（Intent `260929-log-deps-cleanup`、記録のコミット `c1ed553`）の所見 K-10〜K-16 は、その Intent の中で直された。下の「直したコミット」はコミットの件名から対応づけたもので、「今回の確かめ」に書いた点だけを今の HEAD で確かめた。

| 前回の ID | 前回の題 | 直したコミット（件名による） | 今回の確かめ |
|---|---|---|---|
| K-10 | `team.md` の `packagesJudgedByTotal` の記述が古い | `f5fc7ed` | `team.md` の Testing Posture は 12 パッケージと書き、`backend/build.gradle.kts` 221〜234 行の一覧（12 個）と一致する |
| K-11 | 初期管理者の作成のログがメールアドレスを出す | `5dd6739` | INFO のキーが `maskedEmail` の伏せ字になった（`user/service/InitialAdminInitializer.java` 41〜42・92 行） |
| K-12・K-13 | make-you-chic-ui の固定先と E2E の 100 の既知の違反 | `ac0d007`・`20c9f96` | 固定先は `077f5b48ce84cd020ecec2d925836a085f9d9e11`（`git submodule status`）（2026-09-30 の値。2026-10-04 は `3d9521a`、`component-inventory.md` の `make-you-chic-ui`）。既知の違反の一覧は空（開発担当が `frontend/e2e/100-app-text-contrast.e2e.ts` 73 行で確かめた） |
| K-14 | Dependabot の開いた知らせと ignore | `355e964`・`8489241` | 確かめていない（`.github/dependabot.yml` は開発担当が読んだが、前回との比べは今回の範囲の外） |
| K-15 | `ms-check-p95` のしきい値が境界に無い | `27be598`（500 ms に上げる） | 警報のファイルは読んでいない |
| K-16 | README の古い記述 | `20c9f96` | 確かめていない |
