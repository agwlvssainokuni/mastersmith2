# 受け手と権限の表（escalation-matrix）

前の4つの表を正とし、この Intent の差だけを書きます（Q1: A）。
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/escalation-matrix.md`（以下「元の表」）
- `aidlc/spaces/default/intents/260925-user-management/operation/incident-response/escalation-matrix.md`（以下「2つ目の表」）
- `aidlc/spaces/default/intents/260930-user-admin/operation/incident-response/escalation-matrix.md`（以下「3つ目の表」）
- `aidlc/spaces/default/intents/261004-role-menu/operation/incident-response/escalation-matrix.md`（以下「前の表」）

## 1. 重さごとの受け手と期限

変えません（元の表の 1節）。依頼者1名と AI での運用です（`aidlc/spaces/default/memory/team.md` の Way of Working）。

| 重さ | 受け手・判断者 | 着手 | 戻すまでの期限 | その先の受け手 |
|---|---|---|---|---|
| 高 | 依頼者 | 気づいたらすぐ | 1日の内（RTO 1日） | 無い（1名での運用） |
| 低 | 依頼者 | 当日か翌日 | 置かない | 無い |

- 連絡の先は依頼者本人です。受け手が1名のため、呼び出しの順・交代の決まりは置きません。個人の連絡の情報は、公開のリポジトリに書きません（元の表の 1節）。
- AI は、依頼者に頼まれたときに調べと手順の実行を補助します。止める・戻す・データを戻す・秘密を入れ替える操作は、依頼者の承認を得てから行います（`incident-response-questions.md` 2節）。
- この Intent の高の例: 書き込みの資格情報の漏えいの疑い（`runbooks.md` の RB-45）、この Intent の版を戻す（RB-47）、業務データが使えないことで業務が止まる（RB-36・RB-46 が長引く）、保存されたか分からない書き込みが続く（RB-37）。
- 対象DB を本物の DB に替えたときは、その DB の持ち主が受け手に加わります（バックアップと戻し。`incident-plan.md` 4.2）。今は見本の PG だけのため、受け手は依頼者1名のままです。

## 2. 承認なしで行ってよい調べの差（前の表の 2節に足す）

どれも読み取りだけで、値を出さず、件数とキー・code の名前だけを見ます。

| 調べ | コマンドの例 | 気をつけること |
|---|---|---|
| 対象DB の失敗の理由と段（RB-36・RB-37） | `docker compose logs --no-log-prefix --since 1h app` を WARN「対象DB の業務データを読めませんでした」「…書き込めませんでした」で絞り、キー `stage`・`reason`・`sqlState` の値ごとの件数を数える（コマンドは `runbooks.md` の RB-36 の手順 1） | 出るのは段・理由・SQLState・例外のクラスの名前だけ。行の全体を表示しない |
| 見本の PG の状態（RB-36・RB-46） | `docker compose --profile targetdb-postgres ps -a targetdb-postgres`、`docker inspect mastersmith-targetdb-postgres-1 --format …`、`pg_isready`、`docker compose … logs --tail 50 targetdb-postgres` の ERROR・FATAL の件数 | 見本の PG のログの本文は写さない（業務データの値が入りうる） |
| 見本の PG のアカウントと付与（RB-38・RB-46） | `pg_roles` の `mastersmith_reader`・`mastersmith_writer` の数、`operation/deployment-pipeline/cd-config.md` 3.2 の順 7 の問い合わせ、`pg_stat_activity` の `mastersmith_writer` の接続の数 | 件数と真偽値だけ。業務データの表は読まない |
| 書き込みの設定の有無（RB-38・RB-44・RB-45） | `grep -cE '^MASTERSMITH_TARGET_DB_WRITE_USERNAME=.+' .env`・`grep -cE '^MASTERSMITH_TARGET_DB_WRITE_PASSWORD=.+' .env`、`grep -cE '^MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD=.+' .env.targetdb`、起動の WARN「対象DB の書き込みの設定に欠け・不正があるため」の件数 | `.env`・`.env.targetdb` は開かず、項目の名前と「値が空でないか」の件数だけ |
| 業務の拒否の code の件数（RB-40・RB-41） | WARN「要求をエラー応答に変換しました」の `"code":"BUSINESS_…"` の件数 | ほかの機能の `ACCESS_DENIED` なども数に入る |
| 監査の失敗の種類（RB-39） | ERROR「監査イベントの記録に失敗しました」の `"auditEventType":"BUSINESS_ROW_…"`・`"exceptionType"` の件数 | `targetSchema`・`targetTable`・`sourceIp`・`actorUserId` の値を出さない（`grep -o` で種類のキーだけを取り出す） |
| 既にある複写の読み取り | 既に `~/.mastersmith-backup/` にある複写を権限 700 の作業用の場所に展開し、`audit_events` の `event_type`・`result`・`failure_reason` ごとの件数と時刻の幅を数える（RB-37 の手順 3 の 2） | 新しく複写するときはアプリを止めるため承認が要る（3節）。`detail`・名前の列は選ばない。終えたら作業用の場所を消す |
| 手元の監視の状態（RB-43） | `docker compose --profile monitoring ps -a lgtm`、送信の失敗の ERROR の件数 | — |

## 3. 依頼者の承認が要る操作の差（前の表の 3節に足す）

| 操作 | 例 | 承認のときに示すこと |
|---|---|---|
| **書き込みだけを止める**（RB-44） | 【承認】`.env` をホームの下の権限 700 の場所へ中身を表示せずに複写し、`sed -i '' '/^MASTERSMITH_TARGET_DB_WRITE_/d' .env` で2行を消して、`docker compose --profile targetdb-postgres up -d --force-recreate --no-build app` | 作成・更新・削除は 409 `BUSINESS_WRITE_NOT_CONFIGURED`、読み取りは動く。アプリの作り直しの間（数十秒）は全体が止まる。戻すときは複写を戻して作り直す。複写は秘密を含むため、要らなくなったら消す。`rollback-runbook.md` 3.1 の複写を戻す形はもう使えない（複写は 2026-10-25 に消した） |
| **書き込みの資格情報の入れ替え**（RB-45） | 【承認】`openssl rand -hex 24` を表示せずに作り、見本の PG の `ALTER ROLE mastersmith_writer WITH PASSWORD …` を psql の標準入力で渡し、`.env` の `MASTERSMITH_TARGET_DB_WRITE_PASSWORD` と `.env.targetdb` の `MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD` を同じ値に置き換え（値を表示しない）、アプリを作り直す。必要なら `pg_terminate_backend` で残った接続を切る | 入れ替えの間の書き込みは 503 になりうる。急ぐときは先に書き込みだけを止める。見本の PG のコンテナの環境は作り直すまで古い値のまま（台本を流す前に作り直す）。確かめは項目の空でない行の数と、依頼者の画面での書き込み1件（監査に残る） |
| **見本の PG の作り直し**（RB-46） | 【承認】`docker compose --profile targetdb-postgres rm -sf targetdb-postgres`、`docker volume rm mastersmith_mastersmith-targetdb-postgres`、`docker compose --profile targetdb-postgres up -d targetdb-postgres` | アプリの書き込みの API で書いた見本の行は失われる（戻せない。見本の PG は RPO の外）。内部DB と監査は失われない。消すのは見本の PG のボリュームだけ。作り直しの間の業務データは 503。作り直した後に書き込みのアカウントと `TEMPORARY` の取り上げを確かめる |
| **`UNKNOWN` を数えるためのアプリの停止**（RB-37） | 【承認】`docker compose stop app` → 内部DB の複写（`mastersmith-data-<日時>-incident.tgz`、権限 600） → `docker compose --profile targetdb-postgres start app` | アプリが約 10 秒止まる（止まっている間の要求は失敗する）。複写にはメールアドレス・接続元 IP・主キーの値（`detail`）が入るため、`~/.mastersmith-backup/` から出さない。数えるのは種類ごとの件数と時刻の幅だけ |
| 見本の PG の起動・起動し直し（RB-36） | 【承認】`docker compose --profile targetdb-postgres start targetdb-postgres`・`restart targetdb-postgres` | 起動し直しの間の業務データは 503。進行中の書き込みは失敗する（確定の段なら保存されたか分からない。RB-37） |
| 書き込みのアカウントの台本を流し直す（RB-38） | 【承認】`docker compose --profile targetdb-postgres exec -T targetdb-postgres bash /docker-entrypoint-initdb.d/03-writer-account.sh` | 見本の PG のコンテナの環境の値でパスワードと付与を当て直す。`.env.targetdb` を替えた後なら、先に見本の PG のコンテナを作り直す（数秒止まる） |
| 手元の監視の起動し直し（RB-43） | 【承認】`docker compose --profile monitoring up -d lgtm` | lgtm を網につないでからアプリを起動する。VM のメモリを使う（上限 1536m） |
| 業務データの行の作成・更新・削除・行の有無の確かめ | 業務データの画面 | **【依頼者】が画面で行う**。AI は API で行わない（利用者の資格情報が要り、行の値を扱うため）。書き込みは対象DB と監査に残り、監査は消せない（RB-37・RB-38・RB-45・RB-46） |
| 版を戻す（RB-47） | `operation/deployment-pipeline/rollback-runbook.md`（戻し先 `mastersmith:pre-business-data-views`） | 戻している間は業務データの画面と API が無い。見本の PG に書いた行は戻らない。配備の前の複写を戻すと、配備の後のデータと監査の行が失われる。書き込みだけの問題なら先に RB-44 |

## 4. AI がしないことの差（前の表の 4節に足す）

- 業務データの API（定義・一覧・詳細・作成・更新・削除）を、利用者や管理者の資格情報で呼ぶこと（画面の操作は【依頼者】）。
- 見本の PG に管理者で入り、業務データの表の行を読む・数える・書き換えること（主キーの値で行の有無を数えることも含む。Q2: A で B を選ばなかった）。見本の PG で行うのは、アカウント・付与・接続の件数と真偽値の問い合わせと、RB-45 の `ALTER ROLE`・`pg_terminate_backend` だけ。
- 業務データの行の値（主キーの値を含む）、スキーマ・テーブル・カラムの名前、監査の `detail`、ERROR の `targetSchema`・`targetTable` の値を、コマンドの出力・記録・報告に出すこと（`project.md` の Forbidden）。
- `.env`・`.env.targetdb` を開く・表示すること、書き込みのパスワードをコマンド行・画面・記録に出すこと（RB-44・RB-45 は値を表示しない形だけを使う）。
- 承認なしに、`.env`・`.env.targetdb` を書き換える・見本の PG のボリュームを消す・見本の PG やアプリを止める・作り直すこと。
- 保存されたか分からない書き込みを、自動で（台本で）やり直すこと（Q2: A）。
- `.env` の複写（RB-44・RB-45）を、要らなくなった後も残すこと、リポジトリの中に置くこと。
- 配備した環境で、対象DB のドライバーのログを有効にすること（接続情報がログに残る。README の「対象DB」）。

## 5. 知らせ（通知）

- 変えません（元の表の 5節）。通知の先は置かず、依頼者が Grafana の警報の一覧で見ます。警報は **17 件**です（`ms-biz-target-db` を足した。`operation/observability-setup/alarms.md` 1節）。
- **配備したアプリは外部エクスポートが無効のため、今は配備したアプリの警報は鳴りません**。配備したアプリで対象DB に届かないときに気づくのは、業務データの画面の 503 の表示と定期の確認です（`runbooks.md` 3節）。配備先が決まったときに見直します（`incident-plan.md` 8節）。
- 手元の監視を使ったときに鳴ることを確かめた警報は、`ms-biz-target-db`・`ms-forbidden`（使い捨ての環境）と `ms-app-absent`（前の Intent）です。`ms-5xx-ratio`・`ms-pool-pending` は条件つきで鳴りました（`alarms.md` 2節・3節）。

## Sources

- `aidlc/spaces/default/intents/261016-business-data-views/operation/incident-response/incident-response-questions.md`（2節の決まっていること、Q1〜Q5、確認済みの要約）
- 前の4つの表: `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/escalation-matrix.md`、`aidlc/spaces/default/intents/260925-user-management/operation/incident-response/escalation-matrix.md`、`aidlc/spaces/default/intents/260930-user-admin/operation/incident-response/escalation-matrix.md`、`aidlc/spaces/default/intents/261004-role-menu/operation/incident-response/escalation-matrix.md`
- この Intent の Observability Setup: `operation/observability-setup/alarms.md`・`log-queries.md`
- この Intent の配備: `operation/deployment-pipeline/rollback-runbook.md`・`cd-config.md`（3.2）、`operation/deployment-execution/deployment-log.md`（3節・4節）
- NFR の設計: `construction/u2-businessdata-write/nfr-design/security-design.md`（3節・8節）・`reliability-design.md`
- この段の `runbooks.md`・`incident-plan.md`
- `aidlc/spaces/default/memory/team.md`・`project.md`、`README.md`（「対象DB」「業務データの書き込みの API」「既にある見本の DB に書き込みのアカウントを足す（U2）」「手元で試す対象DB」「監査ログの確かめ方」）、`compose.yaml`、`docker/targetdb/postgres/03-writer-account.sh`

## Assumptions & Open Questions

None.
