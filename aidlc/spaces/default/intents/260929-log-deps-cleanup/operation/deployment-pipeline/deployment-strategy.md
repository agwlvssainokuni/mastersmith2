# 配備の仕方（deployment-strategy）

## 1. 配備の形

作り直し（recreate）。PC 上の1つのコンテナのため、アプリを止めて新しいイメージで起動し直す。止まる時間はイメージの作り直しの後の起動の間（1分ほど）。前の Intent と同じ。

## 2. 段と承認

段は開発者の PC の1つだけ（検証環境・本番環境は配備先が決まってから）。入れ替えの前に依頼者の承認を得る（`cd-config.md` の 3節）。

## 3. 配備の後の確かめ（スモークテスト）

健全性（`/actuator/health` が UP）と、下のすべてが通るまで、配備の完了としない（`team.md` の Deployment）。パスワードの要る操作は依頼者が行い、AI はアプリのログと監査の記録で裏付ける。個人に関する値は表示しない。★の付いた操作は、配備した内部DB と監査に行が残るため、送る前に依頼者に伝える。画面の見た目は確かめない（Q2: B。E2E の 100 で確かめ済み）。

| # | 確かめ | 行う人 | 通る条件 |
|---|---|---|---|
| S1 | 起動のログ | AI | healthy になる。ERROR が無い（ロガーと件数だけを見る） |
| S2 | 初期管理者のログ（FR1） | AI | 起動の INFO「初期管理者は既にいるため、作成しませんでした」が1件あり、キー `maskedEmail` があり、キー `email` が無い。値は表示せず、キーの有無と件数だけを見る |
| S3 | ログの漏れ | AI | `docker compose logs app` の中に、`.env` の `MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL` の値が 0 件（値は表示せず、件数だけを数える） |
| S4 | ★ログイン | 依頼者（AI は監査で裏付け） | 初期管理者でログインし、ホームが開ける。`LOGIN_SUCCEEDED` が記録される |
| S5 | ★ログアウト | 依頼者 | ログイン画面に戻る。`LOGGED_OUT` が記録される |

## 4. 中止の条件

次のどれかが起きたら配備を中止し、`rollback-runbook.md` に従って戻す。

| 条件 | 戻し方 |
|---|---|
| 起動が healthy にならない | 戻し先のイメージで起動し直す |
| スモークテストのどれかが通らず、その場で原因が設定だけと分からない | 同上 |
| ERROR のログが出続ける、ログに秘密・メールアドレスそのものが出る | 同上 |

## 5. 監視

- 手元の監視（lgtm）は動いたまま。警報 `ms-check-p95`（500 ms）とダッシュボードは読み込み済み。外部エクスポートは既定で無効のため、配備したアプリの指標は送られない（Build and Test の記録のとおり）。
- 配備の直後は、`docker compose logs app` と監査の記録（README の「監査ログの確かめ方」）で確かめる。

## Sources

- `cd-config.md`・`deployment-pipeline-questions.md`
- `aidlc/spaces/default/intents/260928-quality-followup/operation/deployment-pipeline/deployment-strategy.md`
- `README.md`（監査ログの確かめ方）
