# 業務の概要（mastersmith2）

## 目的

mastersmith2 は、MasterSmith（マスタ管理アプリ）のリポジトリである。管理画面の土台（認証・管理者向け API の認可・監査ログ）の上に、業務データの画面を組み立てる元になる DSL（YAML）を扱う機能がある。対象DB（MySQL・MariaDB・PostgreSQL）のスキーマから既定の DSL を作り、管理者が DSL を投入・プレビュー・適用する。業務データそのもの（マスタの一覧・登録・更新）を扱う画面は、まだ無い。

配備先は当面、開発者の PC 上のコンテナ（colima）だけである。

## 利用者と、今ある機能

| 利用者 | 機能 | 主な部品（詳しくは `component-inventory.md`） |
|---|---|---|
| 管理画面の利用者 | ログイン（連続の失敗でロック）、トークンの更新、ログアウト | `auth`・`frontend-feature-auth` |
| 管理者 | 管理者向け領域（画面 `/admin`、API `/api/admin/**`） | `access`・`frontend-feature-admin` |
| 管理者 | 既定の DSL の生成、DSL の投入（最大 10MB）、プレビュー（違い・誤り・警告）、適用、破棄、履歴（20 件）からの戻し、ダウンロード | `dslmanage`・`dsl`・`targetdb`・`frontend-feature-dsl` |
| 後続の機能（アプリの中） | 適用中の DSL のモデルを読む | `dsl` |
| 運用者・監査者 | 認証・アクセス拒否・DSL の操作を追記だけの監査ログとして内部DB に残す | `audit` |
| 開発者（運用者を兼ねる） | 1コマンドの検査（`./gradlew verify`）、コンテナでの起動、負荷と時間の試験 | `build-and-verify`・`container-runtime`・`perf-and-monitoring` |

## 業務上の決まり（今回深く読んだ範囲で確かめたもの）

- DSL の本文は、受け取ったバイト列のまま内部DB に保存する。識別（SHA-256）とダウンロードがバイト単位で一致する（`V5__u4_dsl_management.sql` の説明）。
- 投入は検証（安全な読み込み・JSON Schema・意味）を通ってからプレビューに置く。プレビューは管理者全員で共有の1件で、置き換えは固定の鍵の1行への `MERGE` で行う。
- 適用は1つのトランザクションで、プレビューの本文を履歴へ写し、プレビューを消し、上限（20 件）を超えた古い履歴を消す（`DslRecordStore.apply`）。適用中のモデルの差し替えと監査の出来事は確定の後に行う。
- 重い操作（プレビューの表示・投入・生成・戻し）はアプリ全体で同時に1つだけ受け付け、取れなければ `DSL_BUSY` で断る（`DslHeavyOperationGate`）。
- プレビューと履歴がすべて 10MB なら、保存する本文は最大約 210MB と見積もられている（`V5` の説明）。動いている間の実際のファイルの大きさはこれと一致しない（`code-quality-assessment.md` の TD-1）。

## この Intent（`260925-storage-memory-fixes`）との関係

前の Intent（`260924-followup-fixes`）と、その前の振り返りで後に回した4件（scope bugfix）を直す。業務の機能は増えない。

1. 動いている間、DSL の投入と適用のたびに内部DB のファイルが伸び、止めるまで縮まない。
2. 10MB の DSL の投入とログインを重ねると、アプリのプロセスのメモリがコンテナの上限 2g の 93% に達する。
3. `AccessTokenApiIT` が一度だけ接続の失敗で落ちた。
4. `perf/dsl-timing.sh` の説明の文が、前の Intent の決定（2g）と食い違う。

4件の事実と仮説は `code-quality-assessment.md` の TD-1〜TD-4 に1か所だけ書いた。
