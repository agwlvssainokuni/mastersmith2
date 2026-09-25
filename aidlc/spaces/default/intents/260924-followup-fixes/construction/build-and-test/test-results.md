# テストの結果（test-results）— 前の Intent で後に回した小さな修正7件

実施日 2026-09-25。対象は作業ブランチ `fix/260924-followup-fixes` の `8219018`（Code Generation の C1〜C6 と記録のコミットを含む）。記録の生データは `build/bt-followup/` と `build/perf-results/`（Git 管理外）。

## 1. ビルドと全テスト（`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`）

| 回 | 結果 | 内容 |
|---|---|---|
| 1回目 | 失敗（4分4秒） | 結合テスト 382 件のうち `AccessTokenApiIT` の 6 件すべてが失敗（`HTTP/1.1 header parser received no bytes`・`Connection reset`）。同じ時刻に Gradle の作業プロセスとの接続が 8 回「10 秒待っても接続の前書きが来ない」（`Did not receive connection preamble within 10s`）で失敗していた。クラスの実行は 0.587 秒で、すべての要求が応答を受け取れずに終わった |
| クラスだけの再実行 3 回 | 3 回とも 6 件すべて成功 | `./gradlew :backend:cleanIntegrationTest :backend:integrationTest --tests '*AccessTokenApiIT'` |
| 2回目（段の中での直しの1回目。コードは変えていない） | 成功（4分14秒） | 下の表 |

2回目の実測:

| 項目 | 結果 |
|---|---|
| バックエンドの単体テスト | 726 件、失敗 0、飛ばし 0 |
| バックエンドの結合テスト（組み込みの H2 と対象DB のコンテナ） | 382 件、失敗 0、飛ばし 0（対象DB のテストも飛ばしていない） |
| 画面のテスト | 47 ファイル・316 件、すべて成功 |
| バックエンドのカバレッジ（JaCoCo、全体） | 行 98.08%（3,941 / 4,018）・分岐 93.83%（1,369 / 1,459）。`jacocoTestCoverageVerification`（全体と新しいパッケージごとの下限）は通過 |
| 画面のカバレッジ（v8） | 行 97.86%（961 / 982）・分岐 93.6%（585 / 625） |
| Gitleaks（履歴 163 コミット） | 検出なし |
| SpotBugs の関門 | 止める指摘（priority 1・`SQL_`）なし。priority 2・3 の警告は既存のもの |
| OSV-Scanner | UP-TO-DATE（入力の lockfile が Code Generation の実行のときと同じで、そのときに通過） |

- `AccessTokenApiIT` の失敗の見立て: このテストは今回の変更（`LoginService`・ログの出力・画面・コンテナの設定）の経路に触れない（`git log` でこのファイルの最後の変更は U2 の実装）。失敗は、サーバーを空いたポートで起動し（`RANDOM_PORT`）`localhost` に送る要求が、応答を受け取れずに接続を切られた形。同じ時刻に Gradle の作業プロセスとの接続も時間切れになっており、PC の負荷による一時的な失敗と見ているが、**原因は確かめていない**。team.md の「不安定なテストは放置せず、原因を直すまで統合しない」に当たりうるため、承認の場で扱いを確かめる。
- E2E（`./gradlew e2eTest`、verify の外）: 6 件すべて成功（18.1 秒）。

## 2. コンテナの設定と秘密情報の置き場

| 確かめ | 結果 |
|---|---|
| `MASTERSMITH_IMAGE_TAG=followup-fixes ./docker/check-container-limits.sh` | 20 件すべて OK（既定のメモリの上限 2g、768m の変更、JVM、節5 の `app` は `.env` だけを読み `MASTERSMITH_SAMPLE_TARGETDB_*` を持たない、見本の対象DB の3つは `.env.targetdb` を読みパスワードを `environment` に持たない） |
| `git check-ignore -q .env.targetdb` | 0（Git 管理外） |
| `git check-ignore -q .env.targetdb.example` | 1（コミットできる） |
| `git ls-files .env.targetdb .env.targetdb.example` | `.env.targetdb.example` だけ |
| `.env.targetdb.example` の値 | すべて空 |

- 試験のイメージは `mastersmith:followup-fixes`（`sha256:4278596d…`）。配備の `mastersmith:local`（`sha256:1585bd4e…`）は上書きしていない（依頼者の判断）。

## 3. 負荷の試験（`dslMixed`、FR8.2・FR3.1）

条件: 使い捨ての環境（`mastersmith-perf`、PostgreSQL と 100×100 のスキーマ）、イメージ `mastersmith:followup-fixes`、コンテナの上限 CPU 4・メモリ 2g、重い側（10MB の DSL の投入とプレビューの表示）1 人＋ログイン同時 10、120 秒。試験用の利用者 11 名（`perf-user01`〜`11`）は SQL で内部DB に直接入れ、ロックの状態の行は作っていない（先に1人ずつログインしていない）。測るあいだ配備したアプリは `docker compose stop app` で止めた（依頼者の判断）。

| 項目 | 結果 |
|---|---|
| ログインの `checks`（`scenario:logins`） | 100%（閾値 `rate==1` を満たす） |
| 重い側の `checks`（`scenario:dslHeavy`） | 100% |
| `checks` の合計 | 1,291 件、失敗 0 |
| `http_req_failed` | 0.00%（1,292 件中 0） |
| `loginLoop-user` の行 | 10 行（VUS と同じ）。利用者の重なり 0 件（vu=1〜7・9〜11 がそれぞれ perf-user01〜07・09〜11） |
| アプリのログ（使い捨て、213 行） | ERROR 0 件。`INTERNAL_ERROR`・主キーの重複・`DataIntegrityViolation` を含む行 0 件。WARN 6 件は起動の既存の案内 4 件と、事前の測定でわざと送った誤りを含む DSL の 422（`DSL_INVALID`）2 件 |
| コンテナの状態 | OOMKilled false、`memory.events` の `oom`・`oom_kill` 0 |
| コンテナのメモリ | `memory.peak` 2,049MiB、終わりの `anon` 1,894MiB（上限の 93%）、`memory.events` の `max` 2,340 回（上限に当たって回収が起きた回数） |
| 応答の時間（参考。この Intent の目標ではない） | 全要求の p95 1.24 秒・p99 2.79 秒・最大 6.61 秒 |

- 合格: FR8.2（ログインの `checks` の率 1、500 が 0 件）と FR3.1 の流したときの記録（行の数 10、重なり 0 件、R-01 の基準）を満たした。
- 取れなかった記録: 試験の後に、試験用の利用者ごとにロックの状態の行が1つずつできたことを内部DB で数える問い合わせを行ったが、列の名前を誤って失敗し、そのまま使い捨ての環境を消したため取り直せなかった。合格の条件はログインの `checks` と 500 の件数であり、判定には影響しない。
- 観察（この Intent の範囲の外）: プロセスのメモリ（`anon`）が上限 2g の 93% に達し、上限に当たって回収が 2,340 回起きた。止まってはいない。前の Intent の振り返りの束 2（10MB の DSL とログインの重ねで 2g でも 93%）と同じ状態で、後の Intent の課題のまま。
- 片付け: 使い捨ての環境は `down -v`、一時ディレクトリは削除した。配備したアプリは `docker compose start app` で同じコンテナを起動し直し、healthy になった（止めていた時刻は `build/bt-followup/app-stop-time.txt`・`app-start-time.txt`。約 17 分）。

## 4. 起動のログ（FR4.1）

使い捨てのアプリの起動のログ（`build/perf-results/followup/postgres/app.log`）: JSON の行 74 件、JSON でない行 0 件、メッセージに改行を含むもの 0 件。` ⏎ ` で区切ったメッセージは1件で、ロガーは `org.hibernate.orm.connections.pooling`（Hibernate の案内）。k6 の後の同じアプリのログ 213 行でも、メッセージに改行を含むものは 0 件。

## 5. 残る点

- `AccessTokenApiIT` の1回目の失敗の原因は確かめていない（1節）。
- 値の入った `.env` を読んだ状態でのアプリのコンテナの環境変数の確かめ（名前だけ）と、この PC の `.env` から `.env.targetdb` への移し替えは Deployment Execution の段で行う。
- `perf/dsl-timing.sh` 33 行の説明（「要件の条件は 1g」）は、前の Intent の決定（条件は 2g）と食い違っているが、この段では直していない。
