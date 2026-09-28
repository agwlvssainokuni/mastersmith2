# 障害の対応の計画（incident-plan）

前の Intent の計画（`aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/incident-plan.md`、以下「前の計画」）を正とし、この Intent の差だけを書きます（Q1: A）。重さの段階・流れ・記録の仕方・事後の振り返り・復旧の目標の考え方は変えません（前の Intent の Q1〜Q3 の決定。`incident-response-questions.md` の「決まっていること」）。

## 1. 範囲の差

- 範囲に、この Intent で増えた機能と部品を加えます。
  - メールの送信（U1）と、手元の受け手 Mailpit（`mastersmith-mailpit-1`。見たいときだけ起動する、Q3: A）。
  - 利用者のプリファレンスとパスワードの変更（U2・U7）。
  - 招待と登録の完了（U3・U5・U6）。
  - 画面の土台（U4）。
  - 見た目の設定（U8）。
- Mailpit はボリュームを持たず、止めて消すと受けたメールも消えます。業務のデータではないため、復旧の目標の外にあります。

## 2. 重さの段階の差（前の計画の 1節に足す）

| 重さ | 足す例 |
|---|---|
| **高** | 招待のトークン・招待の URL が漏れた疑い（`runbooks.md` の RB-18）／依頼者の知らない利用者の登録（RB-17）／起動が止まる設定の誤り（招待の長さ・メールのテンプレート、RB-15）／画面の最初の描画が止まる（`/api/appearance` が応答しない、RB-19） |
| **低** | 招待のメールが届かない・送信の結果が FAILED（RB-13）／招待を使えない設定（503、RB-14）／招待の定期の削除の失敗（RB-16）／総当たりの疑いで、知らない登録が無いもの（RB-17）／`127.0.0.1` で開いたときの拒否・見た目の設定の不正（RB-19） |

- 招待のトークンの漏えいの疑いは「秘密情報が漏れた疑い」と同じ扱いで高です。まず招待を取り消し、Mailpit を止めて消します（Q2: C）。取り消すだけでリンクは使えなくなるため、前の計画の「戻す」は要りません。

## 3. 見つけ方の差（前の計画の 2節の「1. 見つける」に足す）

- 招待をした後に、招待の一覧の送信の結果を見る（FAILED なら RB-13）。
- 起動の後に、起動のログの INFO「招待を使える設定かを点検しました」の `enabled` を見る（`runbooks.md` の 22節）。
- 警報で見つけられないもの:
  - メールの送信の失敗、招待の定期の削除の失敗、総当たりの疑い。
  - ログイン・トークンの更新・確認用 API の遅れ（p95 の警報3件が鳴らないため）。
  - これらは、ログの件数（`runbooks.md` の 22節）とダッシュボードで見つけます（`runbooks.md` の 1節・2節）。

## 4. 確かめのための要求の差

- 招待・送り直し・取り消し・登録の完了・パスワードの変更は監査ログに残り、消せません。登録の完了は利用者も残ります。
- AI が確かめのために送るときは、送る前に依頼者に伝えます（project.md の Corrections）。
- プリファレンスの保存は監査に残りません（Deployment Pipeline の S8）。
- 招待の確かめでは、宛先に実在のアドレスを使いません（`@example.com` など）。Mailpit を起動してから送ります（project.md の Forbidden）。

## 5. 復旧の目標の差（前の計画の 5節）

- RTO 1日・RPO は最後の手での複写まで（変えない）。
- 内部DB に、利用者のプリファレンス（V7）と招待（V8）が加わりました。同じ複写で守られます。
- 今ある複写:
  - `~/.mastersmith-backup/mastersmith-data-202609290030-before-user-management.tgz`（この Intent の配備の前、V6）。
  - これより後の変更（配備の後の招待・利用者・監査・プリファレンス）は、次に複写するまで RPO の外にあります。
- 戻すときは V7・V8 を巻き戻せません。データを戻す（第二の手）と、配備の後の招待・利用者・監査の記録が消えます（`runbooks.md` の RB-20）。

## 6. 配備先が決まったときに見直すこと（前の計画の 6節に足す）

- 招待の定期の削除の失敗の警報（N3）と、総当たり（R1）の数え上げを警報にするか。
- メールの受け手（実在の SMTP へ送るときの資格情報・暗号化・送信の失敗の知らせ方）。
- `/api/appearance` の回数の制限（U8 の `infrastructure-specification.md`）。
- p95 の警報を、アプリの指標にバケットを出した後の式で働かせる（次の Intent で直す）。

## Sources

- 前の計画（正とする）: `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/incident-plan.md`
- `operation/incident-response/incident-response-questions.md`（Q1〜Q4、確認済みの要約）、`operation/incident-response/runbooks.md`
- `operation/observability-setup/alarms.md`・`log-queries.md`
- `operation/deployment-pipeline/rollback-runbook.md`・`deployment-strategy.md`、`operation/deployment-execution/deployment-log.md`
- `construction/u3-invitation/infrastructure-design/monitoring-design.md`（N3・R1）、`construction/u3-invitation/nfr-design/reliability-design.md`・`security-design.md`、`construction/u8-instance-appearance/infrastructure-design/infrastructure-specification.md`

## Assumptions & Open Questions

None.
