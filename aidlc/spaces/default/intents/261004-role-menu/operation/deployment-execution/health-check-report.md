# 健全性の確かめ（health-check-report）

Intent 261004-role-menu の配備の後の健全性です。配備の手順は `operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`、時系列と数えた値は `deployment-log.md`、スモークテストは `smoke-test-results.md` にあります。値・秘密は書きません。

## 1. コンテナ（2026-10-14 13:20Z 頃）

| コンテナ | 状態 | イメージ | 上限 | 使用 |
|---|---|---|---|---|
| `mastersmith-app-1` | healthy（13:18:35Z に起動、再起動 0、OOMKilled false） | `mastersmith:local`（`sha256:3247c5bdab79…`、アプリのコード `a16cd68`） | メモリ 2g・CPU 4・`restart: no` | 436 MiB |
| `mastersmith-mailpit-1` | healthy | 変えていない | 256MiB | 41 MiB |
| `mastersmith-targetdb-postgres-1` | 動作中 | 変えていない | 512MiB | 56 MiB |
| `mastersmith-lgtm-1` | Exited（起動していない） | — | — | — |

## 2. アプリ

| 項目 | 値 |
|---|---|
| `/actuator/health` | 200・`{"status":"UP"}` |
| 配った WAR | SHA-256 `f5293bed7fe2062271a34b84b48586d28431f294d75b1045ed459f0b8c58a376`（イメージの中の `/app/mastersmith.war` も同じ） |
| JVM の設定 | `-XX:+ExitOnOutOfMemoryError`・`-XX:MaxRAMPercentage=50.0` が PID 1 のコマンド行に1件ずつ |
| 内部DB の版 | Flyway 12（初回の配備で V10〜V12 を当てた。直した版では新しい移行なし） |
| 起動の時間 | 6.232 秒（直した版の初めの起動）、5.869 秒・5.805 秒（S11 の複写の後の起動し直し） |
| 起動のログ | 直した版: ERROR 0、WARN は前の版から出ている案内2件（Flyway の H2 の版、Spring の Bean）だけ。救済の WARN 0。初期管理者の INFO はキー `maskedEmail` だけ（`email` 無し）。`.env` の初期管理者のメールアドレスの値の件数 0 |
| 救済 | 働いていない（見込みどおり）。初期管理者は停止していない・管理者の印あり |
| 利用者 | 2 人、管理者の印 2・停止 0（配備の前と同じ） |
| ロール・グループの確かめ用のデータ | 片付け済み（ロール・権限の設定・割り当て・作業ロールの保存・グループ・メンバーがすべて 0） |
| DSL | 版 2 の既定の DSL を適用済み（適用の履歴 6、プレビュー 0） |
| アプリが止まった時間 | 初回の入れ替え 約 51 秒、直した版の入れ替え 約 38 秒、S11 の複写 約 9 秒・約 11 秒 |

## 3. バックアップ（`~/.mastersmith-backup/`、権限 700。各ファイルは権限 600）

| 時点 | ファイル | 使い道 |
|---|---|---|
| 初回の入れ替えの前（2026-10-13 23:20Z） | `mastersmith-data-202610140820-pre-role-menu.tgz` | 配備の前のバックアップ（移行が壊れたときの戻し先。`rollback-runbook.md` の 3節） |
| 直した版の入れ替えの前（07:54Z） | `mastersmith-data-202610141654-after-role-menu.tgz` | 初回の配備の後のバックアップ |
| S11 の1回目（13:11Z） | `mastersmith-data-202610142211-after-role-menu.tgz` | 片付けの前の状態。戻しの練習の元 |
| S11 の取り直し（13:18Z） | `mastersmith-data-202610142218-after-role-menu-final.tgz` | 配備の後のバックアップ（最終） |

## 4. 戻しの練習（`rollback-runbook.md` の 6節）

13:11Z の複写を、前の版 `mastersmith:pre-role-menu`（`fefeafbd299b`）で、project `mastersmith-rollback`・`127.0.0.1:18080` に起動しました。配備したアプリは止めていません。

| # | 確かめ | 判定 |
|---|---|---|
| D1 | 前の版が版 12 の内部DB で起動する（移行なし、検証の失敗なし、版 12 が前の版より新しいという WARN 1件だけ） | 通る |
| D2 | DSL が無い状態で起動する（ERROR 1件、`UNSUPPORTED_VERSION`） | 通る |
| D3 | 管理の画面が開ける。管理者の印が真の行が 2 で、配備の前と同じ（権限のずれなし） | 通る（403 の側は管理者でない利用者がいないため行っていない） |
| D4 | 新しい表・監査の行がある内部DB で D1〜D3・D5 が失敗しない。前の版は監査の行を `save` だけで扱う | 通る |
| D5 | 前の版で DSL を生成し直して適用できる | 通る |
| D6 | ログアウトで、練習で出したリフレッシュトークンがすべて無効になった | 通る |

- 練習のアプリの使用は最大 544 MiB でした。練習の監査は、ログインとログアウトが見込みより1回ずつ多くありました（依頼者に確かめる）。
- 練習の環境（コンテナ・ボリューム・ネットワーク）と練習用のファイル（`.env` の複写・対象DB の環境ファイル・H2 の複写）は消し、残っていないことを確かめました。

戻すときの手順は `rollback-runbook.md` の 2節（イメージだけ）・3節（データも）です。戻し先のイメージは次の2つです。

| タグ | ID | 版 |
|---|---|---|
| `mastersmith:pre-role-menu` | `fefeafbd299b` | 初回の配備の前の版（`273c021`）。権限・メニューの機能を戻すとき |
| `mastersmith:role-menu-before-s8fix` | `e41a5b02fd0b` | 直す前の版（`005730a`）。S8 の直しだけを戻すとき |

## 5. 片付け

- 名前の無いイメージは 0 個で、片付けるものはありませんでした。
- ビルドのキャッシュ 2.323GB を消しました（`docker system df`・`docker builder du` とも 0B になった）。
- タグ付きのイメージ（32 個・10.12GB、タグの一覧は前後で同じ）とボリューム（64 個・10.12GB）には触れていません（`deployment-log.md` の 14節）。

## 6. 残るもの・次の段へ

| 項目 | 扱い |
|---|---|
| 新しい警報とダッシュボードの区画（`U3-NFR5.4`・`U4-NFR5.4`） | Observability Setup で確かめる（この段では lgtm を起動していない） |
| k6 の場面と DSL の時間の測り（Build and Test の Unverified） | Performance Validation に任せる（`cd-config.md` の 3.2） |
| `main` への取り込み（順 10）と push（順 11） | まだ行っていない。前提は `deployment-log.md` の 14節の後の記録と報告のとおり |
| `ae794e4` の CI の1回目の失敗（`ShellLayout.test.tsx` の Unhandled Rejection、再実行で success） | 記録の漏れ。依頼者に扱いを確かめる |
| 直した版（`a16cd68`）の CI | `develop` がまだ push されていないため、動いていない。push の後に確かめる |

## Sources

- `deployment-log.md`（8節・9節・11節〜14節）、`smoke-test-results.md`、`fix-notes.md`
- `operation/deployment-pipeline/cd-config.md`（3節・3.2・6節）・`deployment-strategy.md`・`rollback-runbook.md`（2節・3節・6節）
- `operation/environment-provisioning/environment-inventory.md`（6節・7節）
- 読み取りの結果（2026-10-14 13:20Z 頃）: `docker inspect mastersmith-app-1`、`docker stats --no-stream`、`docker logs`（`Started … in … seconds` の秒数、レベルの件数）、`curl` で `/actuator/health`、`docker system df`・`docker builder du`、`docker images`、`gh run list --branch develop`・`gh api …/runs/38435315180/attempts/1`

## Assumptions & Open Questions

- [assumption] 起動の時間は、Spring の「Started … in … seconds」の値で比べた。初回の入れ替えの起動の時間は記録していない（healthy まで約 8 秒）。
- 戻しの練習の D3 の 403 の側は、管理者でない利用者でログインする手段が無いため行っていない（`rollback-runbook.md` の 6.2 の但し書きどおり）。
- 戻しの練習のログインとログアウトが各1回多いことは、依頼者に確かめる。
