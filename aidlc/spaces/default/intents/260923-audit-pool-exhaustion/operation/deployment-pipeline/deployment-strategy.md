# 配備の方式（deployment-strategy）

方式は前の Intent と同じ**入れ替え（止めてから新しい版で起動する）**である（`aidlc/spaces/default/intents/260922-auth-audit-base/operation/deployment-pipeline/deployment-strategy.md` 1節）。コンテナは1台で、組み込みの H2 のファイルを2つのプロセスから開けないため、ブルー/グリーン・カナリア・順次の入れ替えは採らない。本書は今回の配備の手順を書く。

## 1. 配備の手順（手で行う）

| 順 | 手順 | コマンド | 通る条件 |
|---|---|---|---|
| 1 | 作業ツリーに未コミットの変更が無いことを確かめる | `git status --porcelain` | 何も表示されない |
| 2 | 配備する版のハッシュを控える | `git rev-parse --short HEAD` | 値を控えた（`d948544` 以降の `develop` の先頭） |
| 3 | 検査を通して WAR を作る | `./gradlew verify` | 成功し、`backend/build/libs/mastersmith.war` がある |
| 4 | **負荷の確かめ（FR6.2、配備の前）** | 2節 | 2節の合格の基準をすべて満たす。満たさなければ、ここで止めて配備しない |
| 5 | 動いているアプリを止め、ボリュームを複写する | README の「内部DBのバックアップと戻し方」 | バックアップのファイルができた |
| 6 | イメージを作り直して起動する | `docker compose up -d --build` | エラーなく終わる |
| 7 | 健全になるまで待つ | `docker compose ps` | `app` が `healthy`（最長で約 2 分） |
| 8 | 手でスモークテストを行う | 3節 | すべての項目が通る |
| 9 | 配備の完了 | — | 手順 2 のハッシュが、いま動いている版の記録になる |

- `.env` に `MASTERSMITH_DB_MAXIMUM_POOL_SIZE` は設定しない（既定 30）。既に設定されていないことを手順 6 の前に確かめる（値があると上限が変わる）。
- **ボリュームを消す操作（`docker compose down -v` など）はしない。**

## 2. 負荷の確かめ（FR6.2・NFR2 の一部）

`perf/README.md` の手順（使い捨ての環境 `docker/perf/compose.yaml`、仮の署名鍵・仮の利用者、終わったら消す）で行う。本番の配備の前に行う（Q2: A）。

| 項目 | 内容 |
|---|---|
| 場面 | `loginSuccess`（k6 の `constant-vus`、仮想の利用者 10、考える時間なし） |
| 時間 | 60 秒（前の Intent の負荷の試験と同じ） |
| 環境 | 手順 3 で作った WAR から作ったイメージ。アプリのコンテナのメモリの上限 `mem_limit: 1g`（`docker/perf/compose.yaml`）。資源を取り合わないように、配備したアプリは止めておく |

合格の基準:

| 基準 | 見方 |
|---|---|
| 流した成功のログインの数と、監査の `LOGIN_SUCCEEDED` の行の数が一致する | k6 の結果（`build/perf-results/loginSuccess.json` の成功した要求の数）と、使い捨ての環境の DB の行の数（アプリを止めてから数える） |
| アプリのログに「Connection is not available」と ERROR「監査イベントの記録に失敗しました」が1件も無い | `docker compose -p mastersmith-perf -f docker/perf/compose.yaml logs app` |
| アプリのコンテナが止まらない（OOMKilled にならない） | `docker inspect` の `State.OOMKilled` が false |
| 最初の同時 10 件のログインが極端に遅くならない（前回の F2 では約 7.2 秒） | k6 の結果の最大値。参考として記録する（目標値は要件に無い） |

- アプリが止まる・極端に遅いなどの結果が出たら、環境を起動し直して再現させ、原因をログと状態で確かめてから記録する（`project.md` の Testing Posture）。
- 確かめが終わったら、使い捨ての環境を片付ける（`down -v` は使い捨ての環境だけに使う）。配備したアプリの起動は、1節の手順 6 で新しい版として行う。

## 3. スモークテスト

前の Intent の `aidlc/spaces/default/intents/260922-auth-audit-base/operation/deployment-pipeline/deployment-strategy.md` 3節と同じ項目を手で確かめる。健全性（`/actuator/health` が UP）、ログイン画面、ログイン、管理者向け領域、ログアウト、監査イベント（スモークテストのログインとログアウトの2件）、ログ（ERROR が無い）の7項目である。

- ログインなど、パスワードが要る操作は依頼者が行う。AI は監査イベントとログで裏付け、個人に関する値は表示しない（`project.md` の Corrections）。
- 監査イベントを確かめるためにアプリを止めてボリュームを読む操作は、監査ログに記録が残る操作ではないが、行う前に依頼者に伝える。

## 4. 中止して戻す条件

前の Intent の `aidlc/spaces/default/intents/260922-auth-audit-base/operation/deployment-pipeline/deployment-strategy.md` 4節と同じ。加えて、今回の変更に固有の条件は次のとおり。

| 条件 | 見方 | 扱い |
|---|---|---|
| 2節の負荷の確かめが通らない | 2節の基準 | 配備しない（入れ替えの前なので戻す作業は無い） |
| 配備の後、メモリの不足やヘルスチェックの DOWN が、上限 30 に原因があると見られる | `docker compose logs app`、`docker inspect`、手元の監視（Grafana） | `rollback-runbook.md` 2節の設定の戻し |

## 5. DB のスキーマ

今回はスキーマの変更が無い。前進のみ・後方互換の決まり（team.md の Deployment）はそのまま。

## 6. 引き継ぐもの

| 事項 | 持ち主の段 |
|---|---|
| 本書の手順での配備、2節の負荷の確かめ（FR6.2）、1g の中で動くこと（NFR2）、スモークテスト（FR6.1） | deployment-execution |

## Sources

- `deployment-pipeline-questions.md`（Q2: A、Q3: B）
- 前の Intent の `aidlc/spaces/default/intents/260922-auth-audit-base/operation/deployment-pipeline/deployment-strategy.md`
- `perf/README.md`、`docker/perf/compose.yaml`、`compose.yaml`、README の「内部DBのバックアップと戻し方」
- `aidlc/spaces/default/intents/260923-audit-pool-exhaustion/inception/requirements-analysis/requirements.md`（FR6.1・FR6.2・NFR2）

## Assumptions & Open Questions

- 負荷の確かめの時間（60 秒）と、合格の基準の「最初の同時 10 件の遅さ」を参考とすることは、質問では決めていない。前の Intent の負荷の試験に合わせた。
