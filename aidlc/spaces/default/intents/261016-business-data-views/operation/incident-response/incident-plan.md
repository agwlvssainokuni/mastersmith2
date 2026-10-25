# 障害の対応の計画（incident-plan）

前の4つの計画を正とし、この Intent の差だけを書きます（Q1: A）。
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/incident-plan.md`（以下「元の計画」）
- `aidlc/spaces/default/intents/260925-user-management/operation/incident-response/incident-plan.md`（以下「2つ目の計画」）
- `aidlc/spaces/default/intents/260930-user-admin/operation/incident-response/incident-plan.md`（以下「3つ目の計画」）
- `aidlc/spaces/default/intents/261004-role-menu/operation/incident-response/incident-plan.md`（以下「前の計画」）

次の点は変えません（`incident-response-questions.md` 2節の「決まっていること」）。
- 重さの段階（高・低の2段）
- 見つけてから閉じるまでの流れ（見つける → 状態を見てログを残す → 手当て → 確かめ → 記録して閉じる → 高なら振り返る）
- 判断者・連絡・記録の仕方・事後の振り返り。受け手と判断者は依頼者1名で、AI は頼まれたときに調べと手順の実行を補助する（`escalation-matrix.md`）
- 内部DB の復旧の目標（RTO 1日・RPO は最後の手での複写まで）

障害のときに最初に開くのは `runbooks.md` の索引です。

## 1. 範囲の差

- 範囲に、この Intent で増えた機能を加えます。
  - 業務データの読み取り（U1。定義・一覧・詳細の3本の API、対象DB の読み取りの接続）
  - 業務データの書き込み（U2。作成・更新・削除の3本の API、対象DB の書き込みの接続 `mastersmith-target-db-write`（上限 5）、見本の PG の書き込みのアカウント `mastersmith_writer`、内部DB の移行 V13（監査の3列）、監査の結果 `UNKNOWN`）
  - 業務データの画面（U3）、持ち越しの直し（U4・U5）
  - 警報 `ms-biz-target-db`（Observability Setup の F1: A。17 件目）
- AWS の仕組み（SSM Automation・Incident Manager・AWS Backup）は、配備先が決まるまで作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。手順は `runbooks.md` の手作業の手順で代えます。

## 2. 重さの段階の差（元の計画の 1節に足す）

業務データの障害は、ログイン・管理の画面・監査には響かないため、多くは低です。業務データが使えない時間が業務で困る長さになったときと、書き込みに関わる秘密・誤った書き込みの疑いのときに高とします。

| 重さ | 足す例 |
|---|---|
| **高** | ・書き込みのアカウント `mastersmith_writer` のパスワードが漏れた疑い（`runbooks.md` の RB-45。先に RB-44 で書き込みだけを止める）<br>・この Intent の版を戻す（RB-47。戻している間は業務データの画面と API が無い）<br>・対象DB に届かない（RB-36）・見本の PG の作り直し（RB-46）が長引き、業務データが使えないことで業務が止まる<br>・保存されたか分からない書き込み（RB-37）が続く（対象DB の状態の問題として RB-36 と同じに扱う）<br>・対象DB の確定の後の監査の書き込みの失敗（RB-39）が続く・多い |
| **低** | ・対象DB に届かない（RB-36）が短い間で戻る<br>・保存されたか分からない書き込みが1件ずつ（RB-37。依頼者が画面で確かめてやり直す）<br>・書き込みの設定が無い・誤り（RB-38。読み取りは動く）<br>・監査の書き込みの失敗が1件（RB-39）<br>・業務の拒否の 409・404・400・413（RB-40。多くは決まりどおりの答え）<br>・DSL と対象DB の食い違い（RB-41）<br>・業務データの 403 が多い（RB-42）<br>・手元の監視が止まった（RB-43）<br>・書き込みだけを止める（RB-44。止めるかは依頼者の判断） |

当て方の例:
- 画面で「対象DB に接続できません」が出て、見本の PG を起動し直して数分で戻った → 低（RB-36）。記録だけを残して閉じる。
- 「保存されたか分からない」が1回出て、依頼者が画面で行が保存されていたと確かめた → 低（RB-37）。
- 書き込みのパスワードを含むファイルを誤って共有の場所に置いた → 高（RB-44 → RB-45）。振り返りを行う。
- 見本の PG のボリュームが壊れ、作り直しに半日かかる見込み → 業務データを使う予定があれば高（RB-46）。

## 3. 見つけ方の差（元の計画の 2節の「1. 見つける」に足す）

- **配備したアプリは外部エクスポートが無効**のため、今は配備したアプリについて警報が鳴りません（`operation/observability-setup/alarms.md` の Assumptions）。配備したアプリの業務データの障害は、主に次で見つけます。
  - 依頼者が業務データの画面を使っていて、503・409 の表示に気づく
  - 定期の確認（`runbooks.md` 3節。見本の PG の状態、前の日の対象DB の失敗の WARN の件数、保存されたか分からない書き込みの件数、業務データの監査の失敗の件数）
- 手元の監視を有効にしているときの警報と、最初に見る手順（`runbooks.md` 2節）:

| 警報 | この Intent の障害で鳴るとき | 鳴ることの確かめ | 最初に見る手順 |
|---|---|---|---|
| `ms-biz-target-db`（中、新しい） | 業務データの API が 503 を返した（対象DB に届かない・答えない） | 確かめた（止めてから 2分48秒） | RB-36（`TIMEOUT` なら RB-37 も） |
| `ms-forbidden`（中） | 業務データの権限の 403 が多い（監査に残らない） | 確かめた（534 件で 1分42秒） | RB-42 |
| `ms-5xx-ratio`（中） | 業務データの 503（ほかの要求が少ないときだけ） | 鳴った（条件つき） | RB-36 |
| `ms-pool-pending`（低） | 対象DB に届かない間の借りる待ち（60 秒ごとの点に当たったときだけ） | 鳴った（条件つき） | RB-36 |
| `ms-audit-fail`（中） | 業務データの出来事の監査の書き込みの失敗 | Unverified | RB-39 |
| `ms-error-logs`（中） | 監査の書き込みの失敗の ERROR、手元の監視が止まったときの送信の失敗の ERROR（業務データの 503 は WARN のため入らない） | この Intent では確かめていない | RB-39・RB-43 |

- 警報で見つけられないもの: 保存されたか分からない書き込みの1件ずつ（RB-37）、書き込みの設定が無い（RB-38）、業務の拒否（RB-40）、DSL と対象DB の食い違い（RB-41）。利用者の知らせ、画面、ログの件数（`operation/observability-setup/log-queries.md`）で見つけます。
- ダッシュボードの区画「業務データ（Intent 261016-business-data-views）」（パネル 40〜45）で、業務データの API の要求の数・p95（目安の線 1000 ms）・503・409 の件数と、対象DB の読み取り・書き込みのプールを見ます（`operation/observability-setup/dashboards.md` 1節）。

## 4. 復旧の目標の差（元の計画の 5節。Q3: A）

### 4.1 内部DB（変えない）

- RTO 1日・RPO は最後の手での複写まで（変えない）。
- 内部DB に、業務データの監査（`BUSINESS_ROW_*`、結果 `UNKNOWN` を含む）と V13 の3列（`target_schema`・`target_table`・`work_role_id`）が加わりました。同じ複写で守られます。監査の行は 1 件 約 100 B で増えます（`measurement-notes.md` 10節）。
- 今ある複写（`operation/deployment-execution/deployment-log.md` 2節。どちらも権限 600）:
  - 配備の前: `~/.mastersmith-backup/mastersmith-data-202610210848-pre-business-data-views.tgz`（V12。移行が壊れたときの戻し先）
  - 配備の後: `~/.mastersmith-backup/mastersmith-data-202610210915-after-business-data-views.tgz`（V13）

  これより後の変更は、次に複写するまで RPO の外にあります。`runbooks.md` の RB-37 の手順 3 で監査を数えるために取った複写（`…-incident.tgz`）も、バックアップを兼ねます。
- ロール・権限の設定は、前の計画のとおり書き出しの YAML でも守ります（前の手順書の RB-31）。

### 4.2 対象DB（新しい）

- **業務データの行は対象DB にあり、内部DB の複写には入りません。** このアプリは対象DB の行の復旧の目標を持ちません。
- **見本の PG（`mastersmith-targetdb-postgres-1`、ボリューム `mastersmith_mastersmith-targetdb-postgres`）は RPO の外とします**（Q3: A）。
  - 見本の行は初期化の台本（`docker/targetdb/postgres/01-sample-schema.sql`）が作る架空の値で、本物の業務データではありません。
  - 壊れたときは、ボリュームを消して起動し直し、初期化の台本で作り直します（`runbooks.md` の RB-46）。作り直すと、読み取りのアカウント（`02-reader-account.sh`）・書き込みのアカウントと `TEMPORARY` の取り上げ（`03-writer-account.sh`）も入り直します。入り直したことを、`operation/deployment-pipeline/cd-config.md` 3.2 の順 7 と同じ問い合わせで確かめます。
  - アプリの書き込みの API で見本の PG に書いた行は、作り直すと失われます。版を戻しても戻りません（`rollback-runbook.md` 2節）。
  - 見本の PG の `pg_dump` は取りません（Q3: A。B を選ばなかった）。
  - RTO は内部DB と同じ 1日の内に収まる見込みです（作り直しはボリュームの削除と初めての起動の初期化だけ。時間は測っていない）。
- **本物の対象DB をつなぐときは、その DB の持ち主のバックアップに任せます。** つなぐ前に、持ち主のバックアップの有無と、戻したときにこのアプリの監査（内部DB）と行が食い違いうること（監査は残り、行は戻る）を、その時点で依頼者と持ち主で決めます。このアプリの側では、書き込みのアカウントの権限（`project.md` の Mandated: 行の操作だけ、DDL を与えない）と、書き込みだけを止める手（RB-44）で守ります。

## 5. 確かめのための要求の差

- 業務データの作成・更新・削除は、成功も対象DB に関わる失敗も監査に残り、消せません。障害の対応の中では【依頼者】が画面で行います。AI は利用者の資格情報で業務データの API を呼ばず、見本の PG に入って業務データの行を読みません（`escalation-matrix.md` 3節・4節）。
- 業務データの読み取り（定義・一覧・詳細）は監査に残りません。行の有無の確かめは【依頼者】が画面で行います（Q2: A）。
- 書き込みの確かめ（RB-38・RB-45・RB-46 の1件）は、送る前に依頼者に「監査に残る」と伝えます（`project.md` の Corrections）。
- 監査の `UNKNOWN` を数えるときはアプリを約 10 秒止めるため、依頼者の承認を得てから行います（RB-37）。

## 6. 記録の残し方の差（元の計画の 3節に足す）

- 記録の置き場は今までどおりです（障害の記録はリポジトリの外の `~/.mastersmith-incidents/`、権限 700。報告と Intent の記録には件数・種類・時刻・code の名前だけを書く）。
- 業務データの障害では、次を記録します。
  - 時刻（UTC）、画面の文言、状態コードと `code`（`runbooks.md` の RB-40 の表のどれか）
  - WARN のキー `purpose`・`stage`・`reason`・`sqlState` ごとの件数（値は出ない）
  - 書き込みなら、監査の種類と結果（`SUCCESS`・`FAILURE`・`UNKNOWN`）ごとの件数
  - 【依頼者】が画面で確かめた結果（「保存されていた」「やり直した」など。行の値は書かない）
- 書かないもの: 業務データの行の値（主キーの値を含む）、スキーマ・テーブル・カラムの名前、ERROR の `targetSchema`・`targetTable`・`sourceIp`・`actorUserId` の値、`.env`・`.env.targetdb` の値（`project.md` の Forbidden）。
- 書き込みを止めた・資格情報を入れ替えたときは、いつ止めたか・いつ戻したか・`.env` の複写をいつ消したかを書きます（RB-44・RB-45）。
- 振り返り（高のとき）は元の計画の 4節の形で、`feedback-optimization` に引き継ぎます。

## 7. 事後の振り返りの差（元の計画の 4節に足す）

- RB-37（保存されたか分からない）を使ったときは、何件起きたか、依頼者が画面で確かめてやり直した件数、対象DB の側の原因（止まった・遅い）を確かめます。確定の段の時間切れを使い捨ての環境で起こせていないため、起きた条件は手がかりとして記録します。
- RB-45（書き込みの資格情報の入れ替え）を使ったときは、漏れた経路、止めてから入れ替えまでの時間、漏れた疑いの期間の書き込み（監査の件数と、監査に残らない直接の書き込みの有無）を確かめます。
- RB-46（見本の PG の作り直し）を使ったときは、壊れた原因（メモリの上限・ディスク・手での操作）と、作り直しにかかった時間を記録します（RTO の見込みの裏付け）。

## 8. 配備先が決まったときに見直すこと（前の計画の 7節に足す）

- 配備したアプリの外部エクスポートと通知の先を置き、`ms-biz-target-db` が配備したアプリで鳴るようにする（今は鳴らない）。
- `ms-forbidden` の題・注記・しきい値を、業務データの 403 に合わせて見直すか（`alarms.md` 4節）。
- 本物の対象DB のバックアップの持ち主と、戻したときの監査との食い違いの扱い（4.2）。
- 書き込みのアカウントの付与を、DSL のテーブルごとに絞るか（README の「残る広さ」）。
- 書き込みの資格情報を秘密の置き場（secrets manager など）へ移し、入れ替えを手作業の `.env` の書き換えから仕組みに替えるか（RB-45）。
- 見本の PG を使い続けるか、本物の対象DB に替えるか（見本の PG を RPO の外とする前提の見直し）。

## Sources

- `aidlc/spaces/default/intents/261016-business-data-views/operation/incident-response/incident-response-questions.md`（1節・2節、Q1〜Q5、確認済みの要約）
- 前の4つの計画: `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/incident-plan.md`、`aidlc/spaces/default/intents/260925-user-management/operation/incident-response/incident-plan.md`、`aidlc/spaces/default/intents/260930-user-admin/operation/incident-response/incident-plan.md`、`aidlc/spaces/default/intents/261004-role-menu/operation/incident-response/incident-plan.md`
- この Intent の Observability Setup: `operation/observability-setup/alarms.md`・`dashboards.md`・`log-queries.md`・`measurement-notes.md`
- この Intent の配備: `operation/deployment-pipeline/rollback-runbook.md`・`cd-config.md`（3.2）、`operation/deployment-execution/deployment-log.md`
- NFR の設計と基盤の設計: `construction/u1-businessdata-read/nfr-design/reliability-design.md`・`security-design.md`、`construction/u2-businessdata-write/nfr-design/reliability-design.md`・`security-design.md`、`construction/u1-businessdata-read/infrastructure-design/infrastructure-specification.md`、`construction/u2-businessdata-write/infrastructure-design/infrastructure-specification.md`
- この段の `runbooks.md`・`escalation-matrix.md`
- `README.md`（「業務データの書き込みの API」「手元で試す対象DB」「既にある見本の DB に書き込みのアカウントを足す（U2）」「監査ログの確かめ方」）、`docker/targetdb/postgres/` の初期化の台本、`compose.yaml`
- 決まり: `aidlc/spaces/default/memory/team.md`（Deployment）、`project.md`（Deployment・Forbidden・Mandated・Corrections）

## Assumptions & Open Questions

- [assumption] 見本の PG の作り直しは RTO 1日の内に収まる（作り直しの時間は測っていない。`runbooks.md` の RB-46 は流していない）。
- 本物の対象DB をつなぐときのバックアップの持ち主と、戻したときの監査との食い違いの扱いは、つなぐ時点で決めます（今は見本の PG だけ）。
