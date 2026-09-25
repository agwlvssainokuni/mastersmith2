# Deployment Execution — 質問

前提（配備の前の確かめ。2026-09-25、読み取りだけ）:

- 配備の手順は `aidlc/spaces/default/intents/260925-storage-memory-fixes/operation/deployment-pipeline/deployment-strategy.md` 2節（10 の手順）と 3節（スモークテスト）。戻し方は同じフォルダの `rollback-runbook.md`。
- 今は作業ブランチ `fix/260925-storage-memory-fixes` にいて、アプリのソースに未コミットの変更は無い。`develop` から 7 コミット先で、fast-forward で取り込める。
- 今動いているのは前の Intent で配備した版（`mastersmith:local`、`sha256:a9cfa9dc…`、healthy）と、見本の対象DB の PostgreSQL。Build and Test の結果（`aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/build-and-test/test-results.md`）はすべての目標が Met。
- 内部DB のスキーマは変わらない（DB の移行は無い）。入れ替えの間（手順 5〜6）はアプリが止まる（数十秒〜2分）。見本の対象DB は止めない。
- 手順 8 の `compact` の間（数秒）は、内部DB を使う要求が待たされる。詰め直しの操作は監査ログに残らない。
- スモークテストの要求（ログイン・ログアウト）は監査ログに残り、追記だけで消せない（project.md の決まりにより送る前に伝える）。
- 戻し方の前提は、配備の後に使い捨てのコンテナ（一時のボリューム）で `mastersmith:pre-storage-memory` を起動して healthy になることで確かめる（`rollback-runbook.md` 4節。配備したアプリとデータには触れない）。

## Q1. 配備を行う時期

A. 今すぐ行う（`develop` への fast-forward の取り込み、`verify`（約 5 分）、戻し先のタグ、入れ替え、確かめの順。入れ替えの間、アプリが数十秒〜2分止まる）
B. 依頼者が時期を指定する
X. Other (please specify)

[Answer]: A **Mode:** guided

---

## Consolidated Summary Confirmation

- 今すぐ配備する（Q1: A）。`deployment-strategy.md` 2節の手順 1〜10 を AI が行う: アプリのソースに未コミットの変更が無いことの確かめ → `develop` へ fast-forward で取り込み → `verify` → `mastersmith:pre-storage-memory` のタグ → `docker compose up -d --build app` → healthy → 最大ヒープ（1,024MiB）の確かめ → `hikari-pool.sh` の `status`・`compact`・`status` → スモークテスト
- スモークテスト（3節）は、ブラウザでの操作（ログイン・管理者向け領域・DSL の画面・ログアウト、`compact` の後のログイン）を依頼者が行い、AI は健全性とログ（ERROR が無い・1行1件）で裏付ける。要求が監査ログに残ることは送る前に伝える
- 戻し方の前提を確かめる（`rollback-runbook.md` 4節）。配備の後に、使い捨てのコンテナ（一時のボリューム）で `mastersmith:pre-storage-memory` を起動して healthy になることだけを確かめ、終わったら消す。配備したアプリとデータには触れない
- どこかで通らなかったときは、`deployment-strategy.md` 4節の基準で戻すかを依頼者に諮る（戻すのは依頼者の承認を得てから）
- `origin` へのプッシュは依頼者が行い、その後の CI の結果を AI が確かめる
- 成果物は `deployment-log.md`・`smoke-test-results.md`・`health-check-report.md`

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
