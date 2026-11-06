# 配備の記録（deployment-log）— Intent 261104-tz-options-perf

`operation/deployment-pipeline/cd-config.md` の 3節（順 1〜16）のとおりに配備した。配備するのは `develop` の先頭で、引き受けた Intent 261102-timezone-ui-polish の変更（内部DB の移行 V14 を含む）が一緒に入る（Deployment Pipeline の Q1: A）。時刻は UTC。

## 1. 配備した版

| 項目 | 値 |
|---|---|
| 配備したソース | `develop` の `d9e35417`（アプリのコードの最後の変更は `985b7e6c`。その後は `aidlc/` だけ） |
| WAR | `./gradlew :backend:bootWar --rerun-tasks`（20:52:58〜20:53:46、46 秒、`caffeinate -dims`、AC 電源）。SHA-256 `e2065c4327e191dc9271ff9a119e85a4c9ac9dac8dc4fdb6d8d373569ed2e392`。中に `db/migration/V14__u2_display_time_zone.sql` が 1 件。`static/` の画面のファイルは `frontend/dist` と同じ（WAR だけにあるのはバックエンドの資源 `dsl/dsl-schema-v2.json`）。今回の直しの印（`offsetNanoseconds`）が JavaScript に 1 件。コンテナの中の WAR も同じ SHA-256 |
| 新しいイメージ | `mastersmith:local`（`sha256:4ee5fb45593e…`）。コンテナのイメージ ID と一致（入れ替えの後と、順 10 の起動し直しの後の2回） |
| 戻し先 | `mastersmith:pre-tz-options-perf`（`sha256:ffd2b8380460…`、Intent 261101 で配備した版） |
| 変えなかったもの | `.env`・`Dockerfile`・`compose.yaml`・見本の PG・Mailpit。実行時のタイムゾーンの設定の 4 か所（Dockerfile の `-Duser.timezone=Asia/Tokyo`・`compose.yaml` の `TZ: Asia/Tokyo`・テストの JVM の `user.timezone`・E2E の起動）は変わっていない。E2E のブラウザに同じ値 `timezoneId: 'Asia/Tokyo'` が明示された（261102 の U2） |

## 2. 流れの結果

| 順 | 時刻 | すること | 結果 |
|---|---|---|---|
| 1 | 20:52 | 関門 | アプリのソースに未コミットの変更 0 件。`MASTERSMITH_IMAGE_TAG` 0 件 |
| 2 | 20:52〜20:53 | WAR の作り直し | 1節のとおり |
| 3 | 20:54 | 戻し先のタグ | `mastersmith:pre-tz-options-perf` を付け、ID が `ffd2b8380460` で始まることを確かめた |
| 4 | — | 救済の見込み | 依頼者の答え Q1: A（初期管理者について変更なし）。順 5 の数え（停止 0・印 1）と S1 の救済 0 件で裏付けた |
| 5 | 20:55:03〜20:55:04 | 止めて配備の前の複写 | T0 = 20:55:03Z。`mastersmith-data-202611070555-pre-tz-options-perf.tgz`（41,253 バイト、権限 600）。2.1 の数え |
| 6 | 20:55:42 | 入れ替え | `docker compose up -d --build --no-deps app`。イメージ ID が一致し、作り直しは要らなかった |
| 7 | 20:55:52 | 健全性 | healthy・`/actuator/health` 200。S1 は `health-check-report.md` |
| 8 | — | 合わせ直し | 不要（2.1 が見込みどおり） |
| 9 | 20:56〜21:36 | スモークテスト | S2〜S10 すべて通過（`smoke-test-results.md`） |
| 10 | 21:36:57〜21:37:07 | 配備の後の複写 | `mastersmith-data-202611070636-after-tz-options-perf.tgz`（43,379 バイト、権限 600）。起動し直しの後も healthy・イメージ ID 一致。2.2 の数え |
| 11 | 21:38:31〜21:38:40 | 戻しの練習 | 通過（`health-check-report.md` 3節）。使い捨てのコンテナ・ボリューム・環境変数のファイルを消した |
| 12 | — | 配備の記録のコミット | この記録の初稿でコミットする |
| 13 | 21:4x | 古いイメージの片付け | 2つを消した（3.1） |
| 14 | 23:1x | `main` への取り込み | CI の試行の確かめ（3.2）と不安定なテストの直し（3.3）の後、`main` を `b2990415` へ fast-forward（3.4） |
| 15 | — | 取り込みの結果の追記 | この追記（`main` の後の `develop` だけのコミット） |
| 16 | — | プッシュ | 依頼者が `develop` と `main` をプッシュする |

- アプリが止まったのは、順 5〜7 の約 48 秒（20:55:04〜20:55:52）と、順 10 の約 10 秒（21:36:57〜21:37:07）。見込み（約 2〜3 分と約 10 秒）の内。

### 2.1 配備の前の複写の数え（`cd-config.md` 4.1）

数えは、`~/.mastersmith-tools/` の `count-db3.sh`（`CountRefLookup.java`）と、この配備で足した `CountTz.java`（V14 の列と DSL の語の件数だけを出す。値は出さない）で行った。どちらもリポジトリの外で、コミットしない。展開した作業用のディレクトリは消した。

| 項目 | 見込み | 結果 |
|---|---|---|
| スキーマの最大の版・V14 の行 | 13・0 | 13・0 |
| `users.display_time_zone` | 列なし | 列なし |
| 初期管理者の停止・印 | 0・1 | 0・1 |
| 利用者の数・有効な管理者 | — | 3・1 |
| 適用済みの DSL・プレビュー | — | 8・0 |
| 最新の DSL の語 `customers`・`created_at` | 1 以上 | 6・3（S4・S5 の前提を満たす） |
| `audit_events` の全体（基準） | — | 324 |

### 2.2 配備の後の複写の数え（S10）

| 項目 | 期待 | 結果 |
|---|---|---|
| スキーマの最大の版・V14 の行 | 14・1 | 14・1 |
| `users.display_time_zone` | 列あり・値の入った行 0（片付けの後） | 列あり・0（空 3） |
| 初期管理者・利用者・DSL・プレビュー・ロール | 2.1 と同じ | 同じ（プレビュー 0 は S9 の破棄の後） |
| `audit_events` の全体 | — | 339（T0 の後 15） |

T0 の後の監査の内訳と、期待との照らしは `smoke-test-results.md` の 2節。

## 3. 片付けと `main` への取り込み

### 3.1 古いイメージの片付け（順 13、Deployment Execution の Q4: A）

| 名前 | ID | 理由 | 使うコンテナ |
|---|---|---|---|
| `mastersmith:pre-maintenance-update` | `d8a467b60f6f` | 新しい戻し先より古い（261101 の前の版） | 0 件 |
| `mastersmith:perf-tz-bt` | `3a3fd38094b8` | 261102 の Build and Test の k6 だけに使った | 0 件 |

- `docker system df` のイメージ: 片付けの前 14 個・8.43GB（回収できる 3.476GB）→ 後 12 個・7.562GB（回収できる 2.609GB）。
- 残したもの: `mastersmith:local`（`4ee5fb45593e`）と戻し先 `mastersmith:pre-tz-options-perf`（`ffd2b8380460`）。

### 3.2 CI の試行の確かめ（順 14）

前の取り込み（`f388fb8d`）の後の `develop` の CI の run（Dependabot の run を除く）を、試行ごと（`gh api …/runs/<id>/attempts/<n>/jobs` と job のログ）に確かめた。下の表の run 以外は、すべて1回目で success。

| コミット | run | 試行 | 結果 | 失敗した所と原因 |
|---|---|---|---|---|
| `9e9a000c` | 41571944729 | 1・2・3 | failure | `:frontendTypecheck`。make-you-chic-ui の固定先を上げて増えたアイコンの名前が `allowedIcons.ts` に無かった。次の `9237ffaa` で直った（261102 の記録には無かった） |
| `ecb688d4` | 41679218431 | 1 | failure | `TimeZoneOptionsPerformance.test.ts`（中央値 108.3 ms）。性能のテストの1回目の失敗。この Intent で直した |
| `ecb688d4` | 41679218431 | 2 | failure | `conversionProperties.test.ts`（`+0` と `-0` の比べ）。テストの不具合で、3.3 で直した（どの記録にも無かった） |
| `ecb688d4` | 41679218431 | 3 | success | — |
| `f2f2eb9b` | 41823570241 | 1 | failure | `TimeZoneOptionsPerformance.test.ts`（中央値 106.4 ms）。この Intent の元の失敗 |
| `5304587c` | 42299951824 | 1 | success | 検査の job 38分52秒 |
| `b2990415` | 42306484514 | 1 | success | 検査の job 31分10秒。選択肢の組み立ての中央値 0.8 ms・1.5 ms（カバレッジつき） |

- OSV-Scanner の警告は、どの run でも同じ 6 件（CVSS 7 未満。止める基準の外）。

### 3.3 不安定なテストの直し（Deployment Execution の Q3: A）

- 原因: `conversionProperties.test.ts` の「orders instants consistently」が、同じ時点どうしの比べで `-Math.sign(0)` の `-0` と `+0` を `toBe`（`Object.is`）で比べて落ちていた。本体の `compareInstants` は正しい。
- 二段の確かめ（`team.md`）: CI の seed `1574882541`・path `4:38:38:76:76` を一時的に渡して、直す前のテストが手元で同じ反例で落ちることを確かめた。直した後は同じ seed で通った。反例を `examples` で毎回流す形にし、直す前の比べ方では3回とも落ち、直した後は3回とも 6 件すべて通ることを確かめた。同じ書き方はほかのテストに無かった。
- 関門: `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` が BUILD SUCCESSFUL in 29m 15s（21:46:33〜22:15:48、画面 274 ファイル・3063 件、眠り 0）。
- 統合: ブランチ `fix/tz-options-perf-props` から `develop` へ squash で `b2990415`。ブランチは消した。直したのはテストと記録だけで、配備した WAR（`d9e35417` から作ったもの）とアプリのコードは変わらないため、配備はやり直していない。

### 3.4 `main` への取り込み（順 14、依頼者の承認）

- `main` が `develop` の祖先であることを確かめ、`main` を `f388fb8d` から `b2990415` へ fast-forward した（作業ツリーに監査ログの追記があったため、ブランチを切り替えず `git fetch . develop:main` で行った。fast-forward にならないときは拒まれる形）。タグは付けていない。
- リリースに入ったのは、Intent 261102-timezone-ui-polish とこの Intent の変更。配備した版のアプリのコードは `985b7e6c`（`main` の先頭 `b2990415` と同じアプリのコード）。
- `origin` への `develop` と `main` のプッシュは依頼者が行う（順 16）。

## 4. 計画との差・気づき

1. **戻しの練習の WARN**: 前の版を V14 の後の内部DB で起動すると、Flyway の `DbMigrate` が「スキーマの版 14 が、手元の最後の移行 13 より新しい」という WARN を1件出す（止まらず、14 件を検証して「最新」として進む）。`rollback-runbook.md` の R1 の「WARN は前の版から出ているもののほかに無い」には、この1件を足して読む必要がある。承認済みの手順書は書き換えず、ここに記録する（`project.md` の決まり）。
2. **S4 の時差**: 前からある見本の行は `-04:00`、S5 で作った行は `-05:00` で出た。夏時間の終わり（2026-11-01 06:00 UTC）の前の行が 2 件、後が 0 件（対象DB で件数だけを数えた）。時点ごとの時差で出す設計どおり（`smoke-test-results.md`）。
3. **数える道具**: 補助のシェルの台本を置こうとしたが、作業の守り（フック）に止められたため置かず、`CountTz.java` だけを足してコマンドで直接動かした。
4. **止まった時間**: 見込みより短い約 48 秒。

## Sources

- `operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`・`deployment-pipeline-questions.md`
- `deployment-execution-questions.md`（Q1・Q2）
- `construction/build-and-test/test-results.md`（配備する版の verify・E2E・CI）
- 実行したコマンドの出力（`shasum -a 256`・`unzip -l`・`docker inspect`・`docker logs`・`/actuator/health`・複写の読み取り・対象DB の件数）と依頼者の報告
