# 配備の方式（deployment-strategy）

Intent `260922-auth-audit-base`（auth-audit-foundation）の配備の方式と手順。配備先は開発者の PC 上のコンテナだけ（`cd-config.md` 2節）。決定は `deployment-pipeline-questions.md`（Q1〜Q6）にある。入力は `construction/ci-pipeline/ci-config.md`（ci-config）、`construction/ci-pipeline/quality-gates.md`（quality-gates）、各単位の `infrastructure-design/infrastructure-specification.md`（infrastructure-specification）と `infrastructure-design/cicd-pipeline.md`（cicd-pipeline）である。

## 1. 方式: 入れ替え（止めてから新しい版で起動する）

| 方式 | 採否 | 理由 |
|---|---|---|
| **入れ替え（recreate）** | **採る** | コンテナは1台（ADR-008、U1 の `infrastructure-specification.md` 1章）。内部DBは組み込みの H2 のファイルで、同時に2つのプロセスから開けない。利用者は開発者だけで、止まっている間（数十秒〜2分）を受け入れられる |
| ブルー/グリーン | 採らない | 2つの版を並べて動かすと、同じ H2 のファイルを2つのプロセスが開くことになる。環境も2つ要る |
| カナリア | 採らない | 1台・利用者が開発者だけのため、一部の利用者だけに新しい版を出す意味が無い。流量を分ける装置も無い |
| 順次の入れ替え（rolling） | 採らない | 入れ替える台が1台しか無い |

止まっている間に届いた要求は失敗する。穏やかな停止（停止の合図から最大 30 秒、`compose.yaml` の猶予 45 秒）で、処理中の要求は終えてから止まる（U1 の `infrastructure-specification.md` 1章）。

## 2. 配備の手順（手で行う。Q1）

| 順 | 手順 | コマンド | 通る条件 |
|---|---|---|---|
| 1 | 作業ツリーに未コミットの変更が無いことを確かめる（Q6） | `git status --porcelain` | 何も表示されない |
| 2 | 配備する版のコミットのハッシュを控える（Q6） | `git rev-parse --short HEAD` | 値を控えた。戻すときに「直前の版」として使う |
| 3 | 検査を通して WAR を作る（Q2） | `./gradlew verify` | 成功し、`backend/build/libs/mastersmith.war` がある（`quality-gates.md` 2節の 0〜9 の段） |
| 4 | 動いているアプリを止め、ボリュームを複写する（初めての配備では不要） | README の「内部DBのバックアップと戻し方」の `docker compose stop app` と `tar czf …` | バックアップのファイルができた |
| 5 | イメージを作り直して起動する | `docker compose up -d --build` | エラーなく終わる |
| 6 | 健全になるまで待つ | `docker compose ps` | `app` が `healthy`。起動の猶予 40 秒＋30 秒ごとの確認 3 回のため、最長で約 2 分 |
| 7 | 手でスモークテストを行う（Q4） | 3節 | すべての項目が通る |
| 8 | 配備の完了 | — | 手順 2 のハッシュが、いま動いている版の記録になる |

- 手順 3 の `./gradlew verify` は、統合の前の関門と同じもの。テストの件数などを報告するときは、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直す（project.md の Testing Posture）。
- 手順 4 は、版を替える前に必ず行う（U1 の `cicd-pipeline.md` 5章、U4 の `cicd-pipeline.md` 3章）。**ボリュームを消す操作（`docker compose down -v` など）はしない。**
- 初めての配備では、手順 5 の前に `.env` を用意する（`cd-config.md` 3節）。起動のログに「初期管理者を作成しました」の INFO が出ることを確かめる（U2 の `cicd-pipeline.md` 3章）。

## 3. スモークテスト（手で確かめる。Q4）

| 項目 | 確かめ方 | 合格の基準 | 由来 |
|---|---|---|---|
| 健全性 | `docker compose ps`、または `curl -s http://localhost:8080/actuator/health` | `healthy`／`{"status":"UP"}` | U1 の `cicd-pipeline.md` 5章 |
| ログイン画面 | ブラウザで `http://localhost:8080/` | ログイン画面が表示される | U1 の `cicd-pipeline.md` 5章 |
| ログイン | 初期管理者のメールアドレスとパスワードでログイン | ホームが表示される | U2 の `cicd-pipeline.md` 3章 |
| 管理者向け領域 | メニューの「管理」を開く | 管理者向け領域が表示される | U3 の `cicd-pipeline.md` 3章 |
| ログアウト | ユーザーメニューのログアウト | ログイン画面に戻る | U2 の `cicd-pipeline.md` 3章 |
| 監査イベント | アプリを止めてボリュームを複写し、複写したファイルを読み取りで開く（README の「監査ログの確かめ方」） | スモークテストのログインとログアウトの2件（`LOGIN_SUCCEEDED`・`LOGGED_OUT`）が記録されている | U4 の `cicd-pipeline.md` 3章 |
| ログ | `docker compose logs app` | ERROR が出ていない。1行1件の JSON で出ている | U1 の `infrastructure-specification.md`（観測性） |

- 管理者でない利用者の確認（「ページが見つかりません」になること）は、管理者でない利用者を作る機能が本 Intent に無いため、スモークテストではなく結合テストと E2E で行う（U3 の `cicd-pipeline.md` 3章）。
- 監査イベントの確認のためにアプリを止めた場合は、`docker compose start app` で再び起動し、`healthy` を確かめる。

## 4. 中止して戻す条件

次のどれかに当たったら配備を中止し、`rollback-runbook.md` の手順で直前の版に戻す。

| 条件 | 見方 |
|---|---|
| 約 2 分たっても `healthy` にならない、または `unhealthy` になる | `docker compose ps`、`docker compose logs app` |
| 起動が失敗する（Flyway のスキーマの変更の失敗、署名鍵が無い・短いなど） | コンテナが止まる（`restart: "no"` のため再起動しない） |
| スモークテストのどれかが通らない | 3節 |

設定の誤り（`.env` の値など）が原因とはっきりしている場合は、版を戻さずに設定を直して手順 5 からやり直してよい。

## 5. DB のスキーマの変更

- スキーマの変更は Flyway（`backend/src/main/resources/db/migration`、今は V1〜V4）で、アプリの起動のときに当たる。失敗したら起動を止める（`validate-on-migrate: true`）。
- 変更は**前進のみ・後方互換**とし、1つ前の版のアプリが今のスキーマで動くようにする（team.md の Deployment、U1〜U4 の `cicd-pipeline.md`）。列や表を消す・名前を変える変更は、それを使わなくなった版を出した後の、別の版で行う（広げてから縮める）。
- そのため、戻すときにスキーマを元に戻す手順は持たない（`rollback-runbook.md` 3節）。

## 6. 承認

- 配備は依頼者自身が行う。AI-DLC の作業の中で AI が配備を行う場合は、依頼者の承認を得てから行う。
- 本番の配備の承認（依頼者）は、本番の環境ができてから効く（`cd-config.md` 2節）。

## 7. 後の段へ引き継ぐもの

| 事項 | 持ち主の段 |
|---|---|
| 内部DBのファイルの権限（U4-NFR3.3。`/app/data` が UID 10001 だけ読み書きできること）の確認（例: `docker compose exec app ls -ld /app/data` が `drwx------` で所有者 10001） | deployment-execution |
| 本書の手順で、実際に配備と戻しを一度ずつ行って確かめる | deployment-execution |
| 起動の時間（30 秒以内）・応答の時間の実測 | performance-validation |

## Sources

- `aidlc/spaces/default/intents/260922-auth-audit-base/operation/deployment-pipeline/deployment-pipeline-questions.md`（Q1〜Q6）
- `aidlc/spaces/default/intents/260922-auth-audit-base/operation/deployment-pipeline/cd-config.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/ci-pipeline/ci-config.md`、`quality-gates.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u1-app-skeleton/infrastructure-design/infrastructure-specification.md`・`cicd-pipeline.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u2-authentication/infrastructure-design/infrastructure-specification.md`・`cicd-pipeline.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u3-access-control/infrastructure-design/infrastructure-specification.md`・`cicd-pipeline.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u4-audit-log/infrastructure-design/infrastructure-specification.md`・`cicd-pipeline.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/build-and-test/build-and-test-summary.md`（`Unverified` の持ち主）
- `compose.yaml`、`Dockerfile`、`backend/src/main/resources/application.yaml`（Flyway の設定）、`README.md`

## Assumptions & Open Questions

- 本書の手順は、まだ実際に通して実行していない。実行して確かめるのは deployment-execution の段である。
