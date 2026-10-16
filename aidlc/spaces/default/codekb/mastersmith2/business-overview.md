# 業務の概要（mastersmith2）

> 2026-10-16、Intent `261016-business-data-views` の Reverse Engineering（Full rescan・深さ Standard、記録のコミット `09b795d`）で作り直した。深く読んだ範囲は今回の Intent に関わる 77 パス・16 部品だけで、範囲の外は流し読みである（`reverse-engineering-timestamp.md` の Scope of Analysis）。

## 何をするアプリか

mastersmith2 は、別に動く業務の DB（対象DB: MySQL・MariaDB・PostgreSQL）のテーブルを、DSL（YAML）で定義した見せ方に従って画面から扱うための管理のアプリである。今のコードがすること:

- 利用者の認証（ログイン・アカウントロック・アクセストークンとリフレッシュトークン・ログアウト）と、招待から登録の完了までの流れ（招待のメール）。
- 利用者の管理（一覧・氏名と言語の変更・管理者の印の付け外し・利用停止と再開・ロックの解除）。最後の有効な管理者を無くす操作は拒否する。
- 対象DB のスキーマの読み取りと、既定の DSL の生成・投入・プレビュー・適用・履歴（DSL の書式の版 2、スキーマはちょうど1つ）。
- ロールとグループ、ロールの権限の設定（スキーマ・テーブル・カラムへの主権限 `NONE`・`READ`・`FULL` と、テーブルの補助権限 `CREATE`・`DELETE`）、利用者・グループへの割り当て、作業ロールの切り替え、権限の YAML の書き出しと取り込み。
- 適用中の DSL のメニュー（N 階層の木）を、作業ロールの権限で枝を落としてサイドバーに出す。
- 管理の操作・認証・DSL・ロールなどの出来事を内部DB（組み込みの H2）の監査の表に追記する。

## 今のコードに無いもの（今回の Intent の主題）

- 業務データ（対象DB のテーブルの行）を一覧・詳細で見る画面と API は**まだ無い**。テーブルを指すメニューの項目は「準備中」の置き場の画面（`/tables?schema=&table=&item=`）へ移る。
- 対象DB の接続は読み取り専用で、目的はスキーマの読み取りだけに固定されている。行の値を読む口も書く口も無い（K-49）。
- 管理の可否は今も利用者の列 `users.admin_flag` だけで決まり、ロールの仕組みには管理の権限が無い（K-53）。

## 利用者と役割

| 人 | すること |
|---|---|
| 管理者（`admin_flag` が真で停止していない利用者） | 利用者・招待・DSL・ロール・グループの管理（`/api/admin/**`） |
| ログインした利用者 | 自分の設定・パスワードの変更、作業ロールの切り替え、自分の権限を見る、業務のメニューをたどる（`/api/me/**`） |
| 招待を受けた人（ログインなし） | 招待のリンクの確かめと登録の完了（`/api/registration/**`） |

## 今回の Intent に関わる所見（K-49〜K-61）

番号は開発担当の調査（`aidlc/spaces/default/intents/261016-business-data-views/inception/reverse-engineering/developer-scan.md`）のもの。本文は持ち主の文書に1回だけ書き、ここには一覧だけを置く。前回までの K-1〜K-48 は作り直しにより本文を残していない（前の Intent の記録には残る）。

| 番号 | 要点 | 主な部品 | 本文の場所 |
|---|---|---|---|
| K-49 | 業務データを読む・書く仕組みが無く、対象DB の部品は読み取り専用・スキーマだけに固定 | `targetdb` | `architecture.md` の Interaction Diagrams 1 |
| K-50 | 権限の解決の口は主権限・CREATE・DELETE を返せるが、置き場の API はテーブルの主権限だけ。解決の口に依存してよい機能は `audit` と `navigation` だけ | `role`・`navigation` | `architecture.md` の Interaction Diagrams 2 |
| K-51 | DSL のモデルは一覧・詳細に要る情報（主キー・ビュー・列の表示の設定・選択肢）を持つが、まだ誰も使っていない | `dsl`・`targetdb` | `api-documentation.md` の「内部の口: DSL のモデル」 |
| K-52 | テーブルを指すメニューの項目の移り先は準備中の置き場で、道・今の項目・E2E がこの形に依る | `frontend-feature-tables`・`frontend-registry` | `architecture.md` の Interaction Diagrams 3 |
| K-53 | 管理の可否は `users.admin_flag` だけで決まり、ロールに管理の権限が無い（F2 の論点） | `access`・`user`・`useradmin`・`auth` | `code-quality-assessment.md` の技術的負債 |
| K-54 | 業務データの操作の監査の仕組みが無い。今の監査は内部DB の確定の後に書く形 | `audit` | `architecture.md` の Interaction Diagrams 4 |
| K-55 | 行の「操作」のトリガーの名前と axe-core 4.14 の判定 | `frontend-feature-useradmin`・`frontend-feature-groupadmin`・`frontend-feature-roleadmin` | `component-inventory.md` の `frontend-feature-useradmin` |
| K-56 | 表示の設定の画面（E2E 080）の違反は、トップバーの利用者のメニューのトリガー（Topbar と Avatar）の可能性 | `make-you-chic-ui`・`frontend-e2e` | `component-inventory.md` の `make-you-chic-ui` |
| K-57 | `RoleConcurrencyIT` の #7 は監査の行の ID の順に頼り、作りの上で順は決まっていない | `role`・`audit` | `code-quality-assessment.md` の不安定なテスト |
| K-58 | `AdminForbiddenProvider.test.tsx` の1件は効果の回数を効果が済む前に写しうる | `frontend-app-core` | `code-quality-assessment.md` の不安定なテスト |
| K-59 | `dslMixed` の `memory.peak` はページキャッシュを含むコンテナの最大で測っている | `perf-and-monitoring`・`container-runtime` | `code-quality-assessment.md` の性能の測り方 |
| K-60 | make-you-chic-ui の固定先の更新（`a9309c4`→`b0a1e79`）と axe-core の取り込みの前提 | `make-you-chic-ui`・`build-and-verify`・`frontend-e2e` | `dependencies.md` の「サブモジュールと見送りの依存」 |
| K-61 | 業務データの API を足すときに当たる決まり（API の分類・安全の決まりの差し込み・境界テスト・警報） | `common-security`・`access`・`navigation` | `api-documentation.md` の「API を足すときの決まり」 |

## 今回の Intent の範囲から見た要点

- 一覧・詳細に要る材料（実効の権限の解決の口、DSL の主キー・ビュー・列の表示の設定、テーブルを指すメニューの道）はそろっている（K-50・K-51・K-52）。
- 行を読む・書く仕組みは無く、作成・更新・削除まで行うかで、書ける接続とアカウント・書き込みの待ちの上限・監査と対象DB の確定の関係・既存の境界テストの変更（`team.md` により計画での明記と依頼者の承認が要る）が大きく変わる（K-49・K-50・K-54）。
- 前の Intent からの持ち越し（make-you-chic-ui の固定先 `b0a1e79`、axe-core 4.14、不安定なテスト2件、`dslMixed` の `memory.peak`）は K-55〜K-60 に事実と見立てを分けて書いた。
- F2（`admin_flag` を縮める）は列を消すだけでは済まない（K-53）。範囲を決める段（`scope-definition`）はこのワークフローで SKIP のため、扱いを決める段は要件定義になる見込みで、指揮役が確かめる。

## Sources

- 開発担当の調査: `aidlc/spaces/default/intents/261016-business-data-views/inception/reverse-engineering/developer-scan.md`
- Intent の依頼の文: `aidlc/spaces/default/intents/261016-business-data-views/project-description.json`
- アーキテクトが読み取りで確かめ直したもの: `reverse-engineering-timestamp.md` の「確かめ方」
