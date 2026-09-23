# 配備の記録（deployment-log）

Intent `260922-auth-audit-base`（auth-audit-foundation）を、開発者の PC 上のコンテナへ配備した記録。手順は `operation/deployment-pipeline/deployment-strategy.md`（deployment-strategy）2節の8手順、環境は `operation/environment-provisioning/environment-inventory.md`（environment-inventory）、配備の構成は `operation/deployment-pipeline/cd-config.md`（cd-config）である。決定は `deployment-execution-questions.md`（Q1〜Q3）にある。

## 1. 配備の概要

| 項目 | 値 |
|---|---|
| 配備した版 | **`7040876`**（控えたコミットのハッシュ。`cd-config.md` 4節の版の見分け方） |
| 配備先 | 開発者の PC、colima 上の Docker（CPU 2・メモリ 2GiB の VM、コンテナの CPU の上限 2） |
| 方式 | 入れ替え（止めてから新しい版で起動する。`deployment-strategy.md` 1節） |
| 実行した者 | AI（手順1〜6、スモークテストの自動の項目）と依頼者（スモークテストのブラウザでの操作） |
| 日時 | 2026-09-23 12:21〜12:28（日本時間） |
| 結果 | **完了**（healthy、スモークテストの全項目が合格） |
| DB のスキーマの変更 | 無し（V1〜V4 は環境の用意の段で当たり済み。起動のログ「Successfully validated 4 migrations」「Schema "PUBLIC" is up to date. No migration necessary.」） |

## 2. 手順ごとの記録

| 順 | 手順 | 実行したこと | 結果 |
|---|---|---|---|
| 0 | 準備（Q2） | `.gitignore` に `mastersmith-data-*.tgz` を足し、依頼者の承認を得てコミットした（`7040876`） | バックアップのファイルが無視の対象になることを確かめた |
| 1 | 未コミットの変更が無いこと | `git status --porcelain` から `aidlc/`（ワークフローの記録）を除いて確かめた | 何も無い |
| 2 | ハッシュを控える | `git rev-parse --short HEAD` | `7040876` |
| 3 | 検査を通して WAR を作る | `./gradlew verify`（全段） | `BUILD SUCCESSFUL`（32 タスクのうち 12 を実行、20 は入力が変わっていないため UP-TO-DATE。単体テストと結合テストもその 20 に入る）。SpotBugs の警告は priority 2・3 だけ、初回の読み込みの JavaScript は 104.7KB（gzip）。WAR の SHA-256 の先頭は `d176967101b57b50`（環境の用意の段と同じ） |
| 4 | アプリを止めてボリュームを複写する | `docker compose stop app` の後、README のコマンドで複写 | `mastersmith-data-202609231222.tgz`（3,321 バイト、Git の無視の対象） |
| 5 | イメージを作り直して起動する | `docker compose up -d --build --wait` | イメージ `mastersmith:local`（`sha256:5c02da8f9a6a…`）、コンテナ `mastersmith-app-1` を作り直して起動 |
| 6 | 健全になるまで待つ | `--wait` と `docker compose ps` | 約 9 秒で `healthy`（`health-check-report.md`） |
| 7 | スモークテスト | 7項目（`smoke-test-results.md`） | すべて合格 |
| 8 | 配備の完了 | — | いま動いている版は `7040876` |

スモークテストの監査イベントの確認のために、12:28 にもう一度アプリを止めてボリュームを複写し（`mastersmith-data-202609231228-after-smoke.tgz`、5,516 バイト、Git の無視の対象）、起動し直して `healthy` を確かめた。**配備の後、アプリは起動したままにしている。**

## 3. 予定からの変更

| 事項 | 予定 | 実際 | 理由 |
|---|---|---|---|
| 戻しの手順の練習 | `rollback-runbook.md` 6節: この段で一度通して確かめる | 行わなかった | 依頼者の決定（Q3）。「直前の版が、新しい版の当てたスキーマの変更を含む DB で起動できること」は未確認のまま、次に版を替えるときに確かめる |
| 手順1の判定の範囲 | 作業ツリーに未コミットの変更が無いこと | `aidlc/` のワークフローの記録を除いて判定した | 記録は段の途中で常に変わり、WAR とイメージには入らないため（確認済みの要約） |
| 手順3のテスト | 検査の全段 | テストのタスクは UP-TO-DATE（前回の成功の結果を使った） | Gradle が入力の変化なしと判定したため。件数を報告する場合は `:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直す（project.md の Testing Posture）。本段では件数を報告しない |

## 4. 配備の後に残っているもの

| もの | 場所 | 注意 |
|---|---|---|
| 動いているコンテナ | `mastersmith-app-1`（`127.0.0.1:8080`） | 止めるときは `docker compose down`（データはボリュームに残る） |
| バックアップ2つ | リポジトリの直下 `mastersmith-data-202609231222.tgz`、`mastersmith-data-202609231228-after-smoke.tgz` | Git の無視の対象。後者には初期管理者のメールアドレスと接続元IPが入っている。PC 上の権限は `-rw-r--r--` |
| 初期管理者 | 内部DB | 作られた。`.env` の値は依頼者が管理する |

## Sources

- `aidlc/spaces/default/intents/260922-auth-audit-base/operation/deployment-execution/deployment-execution-questions.md`（Q1〜Q3、確認済みの要約）
- `aidlc/spaces/default/intents/260922-auth-audit-base/operation/deployment-pipeline/cd-config.md`、`deployment-strategy.md`、`rollback-runbook.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/operation/environment-provisioning/environment-inventory.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/build-and-test/test-results.md`（検査の一覧と、前回のテストの成功）
- 2026-09-23 12:21〜12:28 に実行した `git`・`./gradlew verify`・`docker compose`・`docker inspect` の出力

## Assumptions & Open Questions

- 戻しの手順は通して確かめていない（Q3）。次に版を替えるときに `rollback-runbook.md` の手順で確かめる。
- リポジトリの直下のバックアップ2つは、PC の他の利用者が読める権限（`-rw-r--r--`）になっている。この PC の利用者が依頼者だけであれば問題は小さいが、不要になったら消すか、所有者だけが読めるようにする。
