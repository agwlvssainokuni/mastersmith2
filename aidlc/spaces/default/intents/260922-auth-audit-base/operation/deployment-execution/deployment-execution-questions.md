# Deployment Execution の質問（deployment-execution-questions）

Intent `260922-auth-audit-base`（auth-audit-foundation）を、開発者の PC 上のコンテナへ配備する前の確認。

## 前提（確認済みのこと）

- 配備の手順は `operation/deployment-pipeline/deployment-strategy.md` 2節の8手順（未コミットの変更が無いこと → ハッシュを控える → `./gradlew verify` → バックアップ → `docker compose up -d --build` → healthy → スモークテスト → 記録）。スモークテストは3節の7項目を手で行う（`operation/deployment-pipeline/cd-config.md`）。
- 環境は用意済み（`operation/environment-provisioning/environment-inventory.md`）。ボリュームには初回の起動の中身だけがあり、コンテナは止まっている。`.env` には署名鍵と CPU の上限 2 が入っていて、初期管理者の値はまだ入っていない。
- DB のスキーマの変更（Flyway V1〜V4）は、環境の用意の段の起動で当たり済み。この段で新しく当たる変更は無い。
- 配備の時間帯の制約は無い（利用者は開発者だけ）。依存する外部のサービスも無い（外部エクスポートは既定で無効）。

## Q1. 初期管理者の値

スモークテストでは初期管理者でログインします。`.env` の末尾の `# MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL=`・`# MASTERSMITH_AUTH_INITIAL_ADMIN_PASSWORD=` の行頭の `#` を外し、値を入れてください（パスワードは 12 文字以上、UTF-8 で 72 バイト以内）。値は AI に見せなくてかまいません。

A. 入れた（AI は値を読まず、2つの変数に値が入っていることだけを確かめる）
B. この段ではログインの確認をしない（健全性と画面の表示だけを確かめ、ログイン以降は後で依頼者が行う）
X. Other (please specify)

[Answer]: A

## Q2. 内部DBのバックアップの置き場所

配備の手順4と戻しの手順では、README のコマンドどおりにすると、内部DBのバックアップ（`mastersmith-data-<日時>.tgz`）がリポジトリの直下にできます。このファイルは `.gitignore` に入っておらず、利用が始まるとメールアドレスや接続元IPを含みます。リポジトリは公開です（前の2つの段から依頼者の判断待ち）。

A. `.gitignore` に `mastersmith-data-*.tgz` を足す（置き場所はリポジトリの直下のまま）
B. 置き場所をリポジトリの外（例: `~/mastersmith-backups/`）に変え、README のコマンドを直す
C. A と B の両方を行う（置き場所を外に変え、念のため `.gitignore` にも足す）
X. Other (please specify)

[Answer]: A

## Q3. 戻しの手順の練習

`operation/deployment-pipeline/rollback-runbook.md` 6節で、この段で戻しの手順を一度通して確かめることにしています。まだ確かめていない前提は「直前の版のアプリが、新しい版の当てたスキーマの変更を含む DB で起動できること」です。

A. 行う。今の版（U4 まで）を配備してスモークテストを通した後、U4 の前の版（`30f73a4`。スキーマ V4 を知らない版）を取り出して戻し、V4 が当たった DB で起動してスモークテストが通るかを確かめる。最後に今の版へ配備し直す
B. 行わない（戻しの手順は、次に版を替えるときに確かめる）
X. Other (please specify)

[Answer]: B

## Consolidated Summary Confirmation

回答の要約:

- Q1: 初期管理者の値は `.env` に入っている（AI は値を読まずに、2つの変数に値があり、パスワードが 12 文字以上・72 バイト以内であることだけを確かめた）。配備の後の最初の起動で初期管理者が作られる。
- Q2: `.gitignore` に `mastersmith-data-*.tgz` を足す。置き場所はリポジトリの直下のまま。配備の手順1（未コミットの変更が無いこと）を満たすため、この変更は配備の前にコミットする（コミットの前に依頼者の承認を得る）。
- Q3: 戻しの手順の練習は、この段では行わない。「直前の版が新しいスキーマの DB で起動できること」は未確認のまま残し、次に版を替えるときに確かめる（`rollback-runbook.md` 6節の予定からの変更として記録する）。
- 配備は `deployment-strategy.md` 2節の8手順どおりに行う。手順1の「未コミットの変更が無い」は、WAR とイメージに入るもの（`aidlc/` のワークフローの記録以外）について判断する（記録は段の途中で常に変わるため）。
- 手順3の `./gradlew verify` は全段を実行する。バックアップ（手順4）はリポジトリの直下に作る。
- スモークテストの分担: 健全性・画面の表示・ログ・監査イベントの件数の確認は AI が行う（監査イベントは、アプリを止めてボリュームを複写し、複写を H2 の道具で読み取りで開いて数える）。初期管理者でのログイン → 「管理」→ ログアウトのブラウザでの操作は、パスワードを AI が知らないため依頼者が行い、結果を AI に伝える。
- 内部DBのファイルの権限（U4-NFR3.3）の最終の判定もこの段で行う。
- 成果物: `deployment-log.md`・`smoke-test-results.md`・`health-check-report.md`。DB のスキーマの変更はこの段で新しく当たらないため、移行の記録は作らない。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
