# 障害の対応の計画（incident-plan）

前の3つの計画を正とし、この Intent の差だけを書きます（Q1: A）。
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/incident-plan.md`（以下「元の計画」）
- `aidlc/spaces/default/intents/260925-user-management/operation/incident-response/incident-plan.md`（以下「2つ目の計画」）
- `aidlc/spaces/default/intents/260930-user-admin/operation/incident-response/incident-plan.md`（以下「前の計画」）

次の点は変えません（`incident-response-questions.md` の「決まっていること」）。
- 重さの段階（高・低の2段）
- 見つけてから閉じるまでの流れ（見つける → 状態を見てログを残す → 手当て → 確かめ → 記録して閉じる → 高なら振り返る）
- 判断者・連絡・記録の仕方・事後の振り返り
- 復旧の目標（RTO 1日・RPO は最後の手での複写まで）

障害のときに最初に開くのは `runbooks.md` の索引です。

## 1. 範囲の差

- 範囲に、この Intent で増えた機能を加えます。
  - API の分類の印（U1）、DSL の版 2（U2、スキーマの階層と N 階層のメニュー）
  - グループ（U3）、ロール・権限の設定・割り当て・作業ロール・権限の受け渡し（U4、スキーマ V10〜V12）
  - 業務のメニューと置き場（U5）、ロール・グループ・権限の受け渡しの画面（U6）、骨組みの画面（U7）
  - JVM の `-XX:+ExitOnOutOfMemoryError`（Build and Test の Q2: A）
- 受け手と判断者は依頼者1名で、AI は頼まれたときに調べと手順の実行を補助します（`escalation-matrix.md`）。
- AWS の仕組み（SSM Automation・Incident Manager・AWS Backup）は、配備先が決まるまで作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。手順は `runbooks.md` の手作業の手順で代えます。

## 2. 重さの段階の差（元の計画の 1節に足す）

| 重さ | 足す例 |
|---|---|
| **高** | ・ヒープが尽きて JVM が止まり、起き直さない（`runbooks.md` の RB-30。アプリが使えない）<br>・この Intent の版を戻す（RB-35。戻している間はロール・メニューの機能が無い）<br>・画面だけの不具合で業務が止まる（RB-34） |
| **低** | ・`ROLE_BUSY`・`GROUP_BUSY`（RB-28）<br>・権限の受け渡しの 409・413・400（RB-29）<br>・ロール・権限の設定の保存と戻し（RB-31）<br>・業務のメニュー・テーブルが見えなくなった（RB-32）<br>・置き場の 403 が多い（RB-33）<br>・画面の固まりで業務は回る（RB-34） |

- 使える管理者がいなくなったとき（前の計画の高の例、前の手順書の RB-22）は、今は初期管理者の救済で戻します（`runbooks.md` 1節）。重さは高のままです。

## 3. 見つけ方の差（元の計画の 2節の「1. 見つける」に足す）

- **配備したアプリは外部エクスポートが無効**のため、今は配備したアプリについて警報が鳴りません（Observability Setup で、外部エクスポートは使い捨ての環境にだけ有効にした）。配備したアプリの障害は、主に次で見つけます。
  - 依頼者が画面を使っていて気づく
  - 定期の確認（`runbooks.md` 3節。`docker compose ps -a` で `Exited (3)` を見る、取り合いの件数を数える）
- 手元の監視を有効にしているときの警報と、最初に見る手順（Observability Setup の結果を入れた。`runbooks.md` 2節）：

| 警報 | この Intent の障害で鳴るとき | 鳴ることの確かめ | 最初に見る手順 |
|---|---|---|---|
| `ms-app-absent`（高） | JVM がヒープの不足で止まり、起き直さない | 確かめた（止めてから 10 分 20 秒で `Alerting`） | RB-30 |
| `ms-forbidden`（中） | 置き場の 403（業務の結果）、ロール・グループの管理の API への入口の 403 | 確かめた（39 件で鳴った。うち置き場の 403 が 13 件） | RB-33 |
| `ms-pool-pending`（低） | ロール・グループの変える操作・権限の YAML の適用の2本目の接続の待ち | performance-validation で確かめる | `runbooks.md` 1節の RB-08 の行・RB-28 |
| `ms-error-logs`（中） | この Intent の種類の監査の書き込みの失敗の ERROR（JVM が止まる直前の行は JSON でなく数に入らない） | performance-validation で確かめる | RB-30・前の手順書の RB-25 |
| `ms-audit-fail`（中） | この Intent の種類の監査の失敗 | performance-validation で確かめる | 前の手順書の RB-25 |
| `ms-5xx-ratio`（中） | 新しい API の 5xx | Unverified | 元の手順書の RB-01 |
| `ms-heap`（低） | 5 分続く高い使用率だけ（急に尽きる場合は鳴らない） | 確かめていない | RB-30 |

- 警報で見つけられないもの:
  - `ROLE_BUSY`・`GROUP_BUSY`、権限の受け渡しの 409・413・400
  - 見えなくなった（RB-32）、画面の固まり（RB-34）

  利用者の知らせ、画面、ログの件数、監査の問い合わせ（`operation/observability-setup/log-queries.md`）で見つけます。
- ダッシュボードの区画「ロール・グループ・メニュー（Intent 261004-role-menu）」（パネル 36〜38）で、新しい API の要求の数・p95（目安の線 1000 ms）・5xx を見ます（`dashboards.md`）。

## 4. 確かめのための要求の差

- ロール・グループ・割り当て・作業ロールの切り替え・権限の受け渡しの適用は、成功も業務の拒否も監査に残り、消せません。障害の対応の中では【依頼者】が画面で行います。AI は管理者の資格情報で API を呼びません（`escalation-matrix.md` 3節・4節）。
- 確かめ・書き出し・読み取り・置き場の問い合わせは、監査に残りません。
- 不具合を再現するときは、配備したアプリではなく使い捨ての環境で、依頼者と同じ操作を試します（`runbooks.md` の RB-34）。

## 5. 復旧の目標の差（元の計画の 5節、Q2: A）

- RTO 1日・RPO は最後の手での複写まで（変えない）。
- 内部DB に、グループ・ロール・権限の設定・割り当て・作業ロールの保存と、監査の3列（V10〜V12）が加わりました。同じ複写で守られます。
- 今ある複写（`operation/deployment-execution/health-check-report.md` 3節）:
  - 配備の前: `~/.mastersmith-backup/mastersmith-data-202610140820-pre-role-menu.tgz`（移行が壊れたときの戻し先）
  - 配備の後（最終）: `~/.mastersmith-backup/mastersmith-data-202610142218-after-role-menu-final.tgz`

  これより後の変更は、次に複写するまで RPO の外にあります。
- **ロール・権限の設定は、書き出しの YAML でも守ります**（Q2: A）。
  - 【依頼者】大きく変えた後に書き出して `~/.mastersmith-backup/` に置く。戻すときは確かめ → 適用（監査に残る）。手順は `runbooks.md` の RB-31。
  - アプリを止めずに取れます。ただし割り当て・グループ・作業ロールは含まないため、それらは内部DB の複写で守ります。
- JVM が止まったときの戻しは、同じコンテナの起動し直し（約 10 秒で healthy。実測、`runbooks.md` の RB-30）で、RTO の内に収まります。止まったことに気づくまでの時間は、警報が鳴らない今は、依頼者が画面を使うまでです。

## 6. 事後の振り返りの差（元の計画の 4節に足す）

- RB-30（JVM が止まった）を使ったときは、振り返りで次を確かめます。
  - 何の操作で、どの大きさで止まったか
  - 同じ入力で繰り返すか
  - 入力を分ける手（RB-29 の 413 の手当てと同じ）で避けられるか
  - 上限（5 MiB・節 500,000）や最大ヒープの割合を見直すか

  feedback-optimization に引き継ぎます。
- RB-34（画面の不具合）を使ったときは、依頼者の操作を先に聞いたか、使い捨ての環境で同じ操作を試したか、再現のテストを足したかを確かめます。
- 救済（前の手順書の RB-22 の置き換え）を使ったときは、救った条件（`conditions`）と、`.env` の設定が意図どおりかを確かめます。

## 7. 配備先が決まったときに見直すこと（前の計画の 7節に足す）

- 配備したアプリの外部エクスポートと通知の先を置き、`ms-app-absent` が配備したアプリで鳴るようにする（今は鳴らない）。JVM が止まったときに自動で起き直す設定（`restart: on-failure` など）にするかも、ここで決める（今は Deployment Pipeline の Q6: A で手で起こす）。
- `ms-forbidden` から置き場の 403 を分けるか（`operation/observability-setup/alarms.md` 2.2）。
- 新しい API の p95 の警報を足すか（区画のパネルだけで見ている）。
- 画面の誤りを集める仕組み（role-admin-ui・app-frame-ui の `monitoring-design.md` 1節）。
- ロール・権限の設定の書き出しを定期の仕組み（自動の保存）にするか。
- `ms-pool-pending` の式の見直し（前の計画の 7節から持ち越し）。

## Sources

- `aidlc/spaces/default/intents/261004-role-menu/operation/incident-response/incident-response-questions.md`（決まっていること、Q1〜Q4、確認済みの要約）
- 前の3つの計画: `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/incident-plan.md`、`aidlc/spaces/default/intents/260925-user-management/operation/incident-response/incident-plan.md`、`aidlc/spaces/default/intents/260930-user-admin/operation/incident-response/incident-plan.md`
- この Intent の Observability Setup: `operation/observability-setup/dashboards.md`・`alarms.md`・`log-queries.md`・`slo-config.md`
- この Intent の配備: `operation/deployment-pipeline/rollback-runbook.md`・`deployment-pipeline-questions.md`（Q6: A）、`operation/deployment-execution/health-check-report.md`・`deployment-log.md`
- NFR の設計: `construction/group/nfr-design/reliability-design.md`・`security-design.md`、`construction/role/nfr-design/reliability-design.md`・`security-design.md`、`construction/navigation/nfr-design/reliability-design.md`・`security-design.md`
- 基盤の設計: `construction/group/infrastructure-design/infrastructure-specification.md`・`construction/role/infrastructure-design/infrastructure-specification.md`・`construction/navigation/infrastructure-design/infrastructure-specification.md`、`construction/role-admin-ui/infrastructure-design/monitoring-design.md`・`construction/app-frame-ui/infrastructure-design/monitoring-design.md`
- この段の `runbooks.md`・`escalation-matrix.md`
- `README.md`（「使える管理者がいなくなったとき（初期管理者の救済）」「内部DBのバックアップと戻し方」「権限の受け渡し（B6）」）

## Assumptions & Open Questions

- 救済が配備で働くところは見ていません（Intent 261004-safety-carryover の確かめを正とする。Q3: A）。
