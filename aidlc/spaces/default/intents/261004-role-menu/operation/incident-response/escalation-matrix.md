# 受け手と権限の表（escalation-matrix）

前の3つの表を正とし、この Intent の差だけを書きます（Q1: A）。
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/escalation-matrix.md`（以下「元の表」）
- `aidlc/spaces/default/intents/260925-user-management/operation/incident-response/escalation-matrix.md`（以下「2つ目の表」）
- `aidlc/spaces/default/intents/260930-user-admin/operation/incident-response/escalation-matrix.md`（以下「前の表」）

## 1. 重さごとの受け手と期限

変えません（元の表の 1節）。依頼者1名と AI での運用です（`aidlc/spaces/default/memory/team.md` の Way of Working）。

| 重さ | 受け手・判断者 | 着手 | 戻すまでの期限 | その先の受け手 |
|---|---|---|---|---|
| 高 | 依頼者 | 気づいたらすぐ | 1日の内（RTO 1日） | 無い（1名での運用） |
| 低 | 依頼者 | 当日か翌日 | 置かない | 無い |

- 連絡の先は依頼者本人です。受け手が1名のため、呼び出しの順・交代の決まりは置きません。個人の連絡の情報は、公開のリポジトリに書きません（元の表の 1節）。
- この Intent の高の例: JVM が止まって起き直さない（`runbooks.md` の RB-30）、この Intent の版を戻す（RB-35）、画面だけの不具合で業務が止まる（RB-34）。

## 2. 承認なしで行ってよい調べの差（前の表の 2節に足す）

| 調べ | コマンドの例 | 気をつけること |
|---|---|---|
| アプリが止まった理由 | `docker compose --profile targetdb-postgres ps -a app`、`docker inspect mastersmith-app-1 --format '{{.State.ExitCode}} {{.State.OOMKilled}} {{.State.FinishedAt}} {{.RestartCount}}'`、`docker compose logs --no-log-prefix app \| grep -c 'Terminating due to java.lang.OutOfMemoryError'`（RB-30） | 件数と、終了コード・例外の種類の名前だけを見る |
| 取り合いの件数と種類 | `grep -c '"code":"ROLE_BUSY"'`・`grep -c '"code":"GROUP_BUSY"'`・`grep -c '権限の受け渡しの入口を取れませんでした'`、`lockKind` の値ごとの件数（RB-28・RB-29） | 出るのは code・排他の種類・例外のクラスの名前だけ |
| 403 の内訳 | `grep -c '権限が足りないため要求を拒否しました'`（入口）と、`ACCESS_DENIED` の変換の WARN の件数（置き場など）（RB-33） | テーブルの名前はログに出ない |
| 救済の有無（起動の後） | `grep -c '初期管理者を救済しました'`・`grep -c '初期管理者は既にいるため、作成しませんでした'`（README） | 件数だけを出す。行を表示しない（キー `maskedEmail` は伏せ字だが出さない） |
| 監査の複写の読み取り | 既に `~/.mastersmith-backup/` にある複写を展開し、`event_type` ごとの件数を数える（`operation/observability-setup/log-queries.md` 5節） | 新しく複写するときはアプリを止めるため承認が要る。名前・利用者 ID は数だけを見る |
| 画面の不具合のサーバー側 | `docker compose logs --since 30m app` の WARN・ERROR の件数とロガー・code の名前（RB-34） | 本文・名前は出さない |

## 3. 依頼者の承認が要る操作の差（前の表の 3節に足す）

| 操作 | 例 | 承認のときに示すこと |
|---|---|---|
| 止まったアプリを同じコンテナで起動し直す（RB-30） | 【承認】`docker compose --profile targetdb-postgres start app` | 同じコンテナのため、止まる直前のログは残る（実測）。作り直す（`up -d`）ときは先にログを保存する。同じ入力で繰り返し止まるなら、操作を控えるか戻すかを諮る |
| 起動し直して排他を外す（RB-28） | 【承認】`docker compose --profile targetdb-postgres restart app` | 止まっている間の要求は失敗する。進行中の操作は巻き戻る |
| ロール・グループ・割り当て・作業ロール・権限の受け渡しの操作 | 管理の画面・権限の受け渡しの画面 | **【依頼者】が画面で行う**。AI は API で行わない（管理者の資格情報が要るため）。成功と業務の拒否は監査に残り、消せない（RB-28・RB-31・RB-32） |
| ロール・権限の設定の書き出しと保存（RB-31） | 【依頼者】画面で書き出し、`~/.mastersmith-backup/` に置く | リポジトリに置かない（ロールの名前が入る）。割り当て・グループ・作業ロールは含まない |
| 救済のための起動し直し（前の手順書の RB-22 の置き換え） | 【依頼者】必要なら `.env` の初期管理者の設定をそろえ、【承認】`docker compose --profile targetdb-postgres restart app` | 救うと、その利用者のパスワードが設定の値に戻り、リフレッシュトークンがすべて無効になる（ログインし直す）。`INITIAL_ADMIN_RESCUED` が監査に残る |
| 版を戻す（RB-35） | `operation/deployment-pipeline/rollback-runbook.md`（戻し先 `mastersmith:pre-role-menu`） | 戻す前に版 2 のプレビューを破棄する。戻している間はロール・メニューの機能が無い。戻した後は版 1 の DSL を生成し直す（監査に残る）。配備の前の複写を戻すと、配備の後のデータと監査の行が失われる |
| 使い捨ての環境で不具合・手順を確かめる（RB-30・RB-34） | `docker/perf/compose.yaml`（別の project 名・番号、仮の鍵と利用者） | VM のメモリ（6GiB）を使う。配備したアプリ・`.env` には触れない。終わったら結果を見てから、コンテナ・ボリューム・網と一時の置き場を消す |

## 4. AI がしないことの差（前の表の 4節に足す）

- ロール・グループ・割り当て・作業ロールの操作と、権限の受け渡しの確かめ・適用を、配備したアプリの API で行うこと（管理者の資格情報を使わない。画面の操作は【依頼者】）。
- `restart` の設定を変えること、`.env` を開く・表示する・書き換えること（`.env` の編集は【依頼者】）。
- 内部DB を書き込みで開き、SQL でロール・権限・割り当てを直接書き換えること（監査に残らず、`aidlc/spaces/default/memory/project.md` の Mandated「権限・状態を変える管理の操作は監査に残す」を外れる）。
- ロール・グループ・スキーマ・テーブルの名前、利用者 ID・メールアドレス・氏名・送り元の IP、書き出した YAML の中身を、コマンドの出力・記録・報告に出すこと（件数とキーの名前だけで見る）。
- 承認なしに、配備したアプリを止める・起動し直す・作り直す・戻すこと。
- 画面の不具合を、配備したアプリで操作して再現すること（使い捨ての環境で試す）。

## 5. 知らせ（通知）

- 変えません（元の表の 5節）。通知の先は置かず、依頼者が Grafana の警報の一覧で見ます。警報は 16 件のままです（`operation/observability-setup/alarms.md` 1節）。
- **配備したアプリは外部エクスポートが無効のため、今は配備したアプリの警報は鳴りません**。JVM が止まったとき（RB-30）も、気づくのは画面と定期の確認です（`runbooks.md` 3節）。配備先が決まったときに見直します（`incident-plan.md` 7節）。
- 手元の監視を使ったときに鳴ることを確かめた警報は、`ms-forbidden` と `ms-app-absent` です（`alarms.md` 2節）。

## Sources

- `aidlc/spaces/default/intents/261004-role-menu/operation/incident-response/incident-response-questions.md`（決まっていること、Q1〜Q4、確認済みの要約）
- 前の3つの表: `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/escalation-matrix.md`、`aidlc/spaces/default/intents/260925-user-management/operation/incident-response/escalation-matrix.md`、`aidlc/spaces/default/intents/260930-user-admin/operation/incident-response/escalation-matrix.md`
- この Intent の Observability Setup: `operation/observability-setup/alarms.md`・`log-queries.md`・`dashboards.md`
- この Intent の配備: `operation/deployment-pipeline/rollback-runbook.md`・`deployment-pipeline-questions.md`（Q6: A）
- NFR の設計: `construction/group/nfr-design/reliability-design.md`・`security-design.md`、`construction/role/nfr-design/reliability-design.md`・`security-design.md`、`construction/navigation/nfr-design/security-design.md`
- 基盤の設計: `construction/group/infrastructure-design/infrastructure-specification.md`・`construction/role/infrastructure-design/infrastructure-specification.md`・`construction/navigation/infrastructure-design/infrastructure-specification.md`
- この段の `runbooks.md`・`incident-plan.md`
- `aidlc/spaces/default/memory/team.md`・`project.md`、`README.md`（「使える管理者がいなくなったとき（初期管理者の救済）」）

## Assumptions & Open Questions

None.
