# 障害の対応の計画（incident-plan）

前の2つの計画（`aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/incident-plan.md`、以下「元の計画」と、`aidlc/spaces/default/intents/260925-user-management/operation/incident-response/incident-plan.md`、以下「前の計画」）を正とし、この Intent の差だけを書きます（Q1: A）。重さの段階（高・低の2段）・見つけてから閉じるまでの流れ・記録の仕方・事後の振り返り・復旧の目標の考え方は変えません（`incident-response-questions.md` の「決まっていること」）。障害のときに最初に開くのは `runbooks.md` の索引です。

## 1. 範囲の差

- 範囲に、この Intent で増えた機能を加えます。
  - 利用停止の状態と3つの入口（ログインの照合・トークンの更新・アクセストークンの認証）での判定（U1、スキーマ V9）。
  - ページ送りの共通化（U2）。
  - 利用者の管理の API（一覧・氏名と言語の変更・5つの操作。U3）と、行の排他・最後の有効な管理者の保護。
  - 管理の画面の 403 の共通の表示（U4）と、利用者の管理の画面（U5）。
- 受け手と判断者は依頼者1名、AI は頼まれたときに調べと手順の実行を補助する、止める・戻す・データを戻す操作は依頼者の承認を得てから、知らせは Grafana の画面で見るだけ（通知の先は置かない）、という点は変えません（元の計画、`escalation-matrix.md`）。
- AWS の仕組み（SSM Automation・Incident Manager・AWS Backup）は、配備先が決まるまで作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。手順は `runbooks.md` の手作業の手順で代えます。

## 2. 重さの段階の差（元の計画の 1節・前の計画の 2節に足す）

| 重さ | 足す例 |
|---|---|
| **高** | 使える管理者がいなくなった（ログインできる有効な管理者がいない、最後の有効な管理者の資格情報の漏えいの疑い。`runbooks.md` の RB-22）／この Intent の版を戻す（戻している間は利用停止が効かない、RB-26）／既存の経路（ログイン・招待・登録の完了）の行の排他の時間切れの 500 が続く（RB-24） |
| **低** | 管理者の誤った操作で、ほかの有効な管理者が画面から戻せるもの（RB-21）／停止中の利用者が使えない・停止を解きたい（RB-23）／409 `USER_ADMIN_BUSY`（RB-24）／5つの操作の監査の欠け（続かないもの、RB-25）／行の「操作」のメニューのはみ出し（既知の不具合、RB-27） |

- 漏えいの疑いのある利用者は、今は「利用を控える」ではなく「止める」で、サーバー側で止められます（`runbooks.md` 1節）。止められない（自分自身・最後の有効な管理者）ときは RB-22 で別の管理者を作ってから止めます。

## 3. 見つけ方の差（元の計画の 2節の「1. 見つける」に足す）

- 警報で見つけられるもの: 管理の API への 403 の増加（`ms-forbidden`、鳴ることを確かめた）。5つの操作の監査の失敗・接続の待ち・ERROR の増加（`ms-audit-fail`・`ms-pool-pending`・`ms-error-logs`。鳴ることは performance-validation で確かめる）。
- 警報で見つけられないもの:
  - 409（`USER_ADMIN_BUSY`・`USER_ADMIN_LAST_ADMIN`・`USER_ADMIN_NO_CHANGE` など）、停止中の利用者の拒否、管理者の誤った操作、使える管理者がいなくなったこと。
  - これらは、利用者からの知らせ、利用者の管理の一覧の表示、監査の問い合わせ（`operation/observability-setup/log-queries.md` 3節）、ログの件数（`runbooks.md` の 3節）で見つけます。
- 前の計画の 3節の「ログイン・トークンの更新・確認用 API の遅れ（p95 の警報3件が鳴らない）」は古くなりました。p95 の式は値を出せるようになっています（`runbooks.md` 1節。実際に鳴ることは確かめていない）。

## 4. 確かめのための要求の差

- 利用者の管理の5つの操作は、成功も拒否も監査に残り、消せません。AI が確かめのために送るときは、送る前に依頼者に伝えます（`project.md` の Corrections）。5つの操作は管理者の画面の操作のため、障害の対応の中では【依頼者】が行います（`escalation-matrix.md` 3節）。
- 一覧の閲覧と氏名・言語の変更は、監査に残りません（README の「利用者の管理の API」）。
- RB-22 の手順（初期管理者のメールアドレスを替えて作り直す）は、利用者を1人増やし、消せません。作成は監査に残らないため、障害の記録に時刻と「新しい管理者を作った」ことを書きます（アドレスは書かない）。手順の確かめは、この段で使い捨ての環境で行いました（`runbooks.md` の RB-22 の確かめの結果、Q3: A）。配備したアプリでは確かめていません。

## 5. 復旧の目標の差（元の計画の 5節、前の計画の 5節）

- RTO 1日・RPO は最後の手での複写まで（変えない）。
- 内部DB に、利用停止の列（V9 の `users.suspended`）が加わりました。同じ複写で守られます。
- 今ある最新の複写: `~/.mastersmith-backup/mastersmith-data-202610032204-after-user-admin.tgz`（この Intent の配備の後、`operation/deployment-execution/deployment-log.md` 2節）。これより後の変更（利用者・監査・招待など）は、次に複写するまで RPO の外にあります。
- **配備の前のバックアップは取っていません**（`operation/deployment-pipeline/rollback-runbook.md` 2節、Q1: B）。データを配備の前の状態に戻す手は無く、戻しはイメージだけです。V9 は巻き戻しません。
- 戻している間は利用停止が効かない、という既知の制約を受け入れています（`rollback-runbook.md` 3節、`runbooks.md` の RB-26）。

## 6. 事後の振り返りの差（元の計画の 4節に足す）

- 高の障害（RB-22・RB-26 など）は、元の計画の 4節の形で振り返ります（時系列・原因・防ぐための手当て）。
- RB-22 を使ったときは、振り返りで次を確かめます: 新しく作った管理者をどうしたか（止めたか・残したか）、`.env` を元に戻したか、使える管理者が1人に偏っていないか（`runbooks.md` の 3節の定期の確認）。

## 7. 配備先が決まったときに見直すこと（元の計画の 6節・前の計画の 6節に足す）

- 使える管理者がいなくなったときの戻し方を、`.env` を替えて作り直す手順のままにするか、別の救済の口（運用の手順や道具）を設けるか。初期管理者の作成を監査に残すか。
- `ms-pool-pending` の式を、時間切れの累計の増加を見る形に見直す（`operation/observability-setup/alarms.md` 2.1節、`construction/code-generation/gate-decisions.md` 4節）。
- `ms-5xx-ratio`・`ms-audit-slow` が U3 の失敗で鳴ることの確かめ（Unverified、feedback-optimization）。
- 配備の前のバックアップを、スキーマの変更があるときは取る決まりにするか（今回は Q1: B で取っていない）。

## Sources

- 元の計画（正とする）: `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/incident-plan.md`
- 前の計画（正とする）: `aidlc/spaces/default/intents/260925-user-management/operation/incident-response/incident-plan.md`
- `operation/incident-response/incident-response-questions.md`（決まっていること、Q1〜Q3、確認済みの要約）、`operation/incident-response/runbooks.md`・`escalation-matrix.md`
- `operation/observability-setup/alarms.md`・`dashboards.md`・`log-queries.md`
- `operation/deployment-pipeline/rollback-runbook.md`、`operation/deployment-execution/deployment-log.md`
- `construction/u3-user-admin-api/nfr-design/reliability-design.md`（10節）・`security-design.md`（7.2節）、`construction/u1-user-suspension/nfr-design/security-design.md`、`construction/u3-user-admin-api/infrastructure-design/infrastructure-specification.md`（5節）
- `construction/code-generation/gate-decisions.md`（4節）
- `README.md`（「利用者の管理の API」「監査ログ（U4）」）

## 依頼者の決定（2026-10-03、成果物の確かめ）

担当が挙げた6点（RB-22 の手順 2 の「まだ利用者にいないアドレスを選ぶ」、招待中のアドレスを使ったときは推測（避ける）、手順 6 で作り直して「既にいるため」を確かめる、手順 5 と配備した内部DB での作り直しは確かめていないと記録、RB-27 の回避の方法は推測、7節の見直し）を、すべて受け入れた。7節の「初期管理者の作成を監査に残すか、救済の口を設けるか」は、後の Intent への持ち越しに足す。

## Assumptions & Open Questions

None.
