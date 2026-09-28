# 次への入力（feedback-loop）

Intent `260925-user-management`（利用者の管理: メール・プリファレンスとパスワードの変更・招待と登録の完了・画面・見た目の設定）の振り返りと、次の Intent への入力です（Q4: A）。優先の順は案で、決めるのは依頼者です。

## 1. この Intent でできたこと

- 招待から登録の完了まで、管理者が利用者を迎える流れができました。
  - 招待のメールは手元の受け手（Mailpit）へ送ります。
  - 送信の失敗は一覧から送り直せます。
  - 招待は取り消しと期限を持ち、トークンはハッシュ値だけを保存します。
- 利用者は、氏名・言語・テーマ・文字の大きさと、自分のパスワードを変えられます。
- 見た目の設定（ブランドの色・書体）をインスタンスごとに置けます。
- 配備（`83b572b`、Flyway V7・V8）、戻しの練習、監視（招待と登録・送信の行、Explore）、障害の手順（RB-13〜RB-20）、負荷の試験（21 件 Met）まで済みました。

## 2. 次の Intent の候補と優先の順の案

### 第1候補: 既に決まっている直しに、監視の欠けと依存の更新を加える

依頼者の決定で、次の Intent で行うことが決まっているもの：

| 項目 | 内容 | 出典 |
|---|---|---|
| コントラスト | U4〜U7 の NFR7.1 の Not Met（make-you-chic-ui の Avatar の頭文字2文字、dark の FormField の誤りの文字） | `construction/build-and-test/test-results.md` 8.1 |
| CI の時間切れ | `H2CompactionByPoolSuspensionIT`（接続の待ち 10 秒）と `InvitationAdminPage.test.tsx`（既定 5 秒）の時間切れで、CI（`66fe981`）が2回失敗 | 同上、`construction/ci-pipeline/quality-gates.md` |
| make-you-chic-ui の固定先の更新 | `7865c28`・`310e1ec` への更新（専用のコミット、前後のハッシュを記録） | Build and Test の Q7 |

加える案：

| 項目 | 内容 | 理由 | 出典 |
|---|---|---|---|
| p95 のバケット | アプリの指標 `http_server_requests`・`mastersmith.mail.send` にバケットを出す設定。警報 `ms-login-p95`・`ms-refresh-p95`・`ms-check-p95` を働かせ、パネルの式を戻す | 3件の警報がこれまで一度も鳴らない状態だった。設定の小さな変更で、テストで確かめられる | `operation/observability-setup/dashboards.md` 2節・`alarms.md` 2節 |
| Dependabot の知らせ | 開いたままの 11 件を手元でまとめて取り込む（`./gradlew verify` を通して統合） | team.md の受け方。High 以上は次の Bolt の前に取り込む決まり | `construction/ci-pipeline/quality-gates.md` |

### 第2候補: 記録の整理と余裕の見直し

| 項目 | 内容 | 出典 |
|---|---|---|
| 承認済みの記録の誤りの整理 | `alarms.md`（送信の失敗は警報に当たらない）、`log-queries.md`・`runbooks.md` の RB-17（登録の完了の拒否は監査に残る）。README や手順書の側で正しい形にそろえる | `drift-report.md` 2節 |
| bcrypt の余裕 | 1 秒の目標まで 69〜96 ms。CPU の割り当てと同時の数の想定の見直し（目標は緩めない） | `operation/performance-validation/nfr-validation-matrix.md` 2節 |
| 監視のデータの片付け | lgtm のボリューム（701MB、試験のデータが混ざる）と古いイメージのタグ | `cost-analysis.md` 4節 |

### 第3候補: 配備先の決定を待つもの

| 項目 | 内容 | 出典 |
|---|---|---|
| 警報の見直し | 招待の定期の削除の失敗（N3）、総当たりの数え上げ（R1）、メールの送信の失敗の知らせ | `operation/observability-setup/alarms.md` 1節、`operation/incident-response/incident-plan.md` 6節 |
| 実在の受け手 | 実在の SMTP へ送るときの資格情報・暗号化と、招待の時間の測り直し | `nfr-validation-matrix.md` 2節 |
| SLO の正式な値 | 30 日の窓での稼働、誤りの予算、画面の時間の測り方 | `slo-report.md` 3節 |
| `/api/appearance` の回数の制限 | 配備先の前段で扱う | U8 の `infrastructure-specification.md` |
| 通知の先 | Grafana の連絡先など | `incident-plan.md` 6節 |

### 確かめられていないこと（機会があれば確かめる）

- 戻したときのブラウザの表示の設定（localStorage）と、初期管理者のメールアドレスを変えて戻したときの1つ前の版の動き（`rollback-runbook.md`、`runbooks.md` の RB-20・23節）。
- 招待中の人のログインの監査の理由（推測のまま、`runbooks.md` 23節）。

## 3. 運用から分かったこと（進め方の学び）

学びとして `project.md` に残したものと、この段で気づいたことです。

- p95 の式を書く前に、指標にバケットがあるかを確かめる（Observability Setup で保存）。
- 数の値の確かめは、送信の周期（1 分）を複数またいで送る（同上）。
- `caffeinate -i` は試験の台本全体を包む。応答しない受け手は `docker pause` で作る（Performance Validation で保存）。
- この Intent の運用の段では、承認済みの記録の誤りが3つ見つかりました（`drift-report.md` 2節）。いずれも、式や件数を実際に流して確かめた段で見つかっています。記録を書く段で、失敗の状態コードとログのレベルをソースで確かめると減らせます。

## 4. 依頼者に頼むこと

- `develop` のプッシュ（未プッシュのコミットがあります。プッシュは依頼者が行う）。
- 次の Intent の範囲と順の決定（上の案をもとに）。

## Sources

- `operation/feedback-optimization/feedback-optimization-questions.md`（Q1〜Q4、確認済みの要約）
- `operation/feedback-optimization/slo-report.md`・`cost-analysis.md`・`drift-report.md`
- `construction/build-and-test/test-results.md`・`build-and-test-summary.md`、`construction/ci-pipeline/quality-gates.md`
- `operation/observability-setup/dashboards.md`・`alarms.md`・`log-queries.md`、`operation/incident-response/runbooks.md`・`incident-plan.md`、`operation/performance-validation/test-results.md`・`nfr-validation-matrix.md`
- `operation/deployment-pipeline/rollback-runbook.md`、`operation/deployment-execution/deployment-log.md`
- `aidlc/spaces/default/memory/project.md`（学び）

## Assumptions & Open Questions

None.
