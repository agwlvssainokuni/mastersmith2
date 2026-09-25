# Deployment Execution — 質問

前提（配備の前の確かめ。2026-09-25、読み取りだけ）:

- 配備の手順は `aidlc/spaces/default/intents/260924-followup-fixes/operation/deployment-pipeline/deployment-strategy.md` 2節（12 の手順）と 3節（スモークテスト）。戻し方は同じフォルダの `rollback-runbook.md`。
- 今は作業ブランチ `fix/260924-followup-fixes` にいて、アプリのソースに未コミットの変更は無い。`develop` から 10 コミット先で、fast-forward で取り込める。
- 今動いているのは前の Intent で配備した版（`mastersmith:local`、`sha256:1585bd4e…`、healthy）と、見本の対象DB の PostgreSQL。
- 内部DB のスキーマは変わらない。配備の間（手順 7〜8）はアプリと見本の対象DB が止まる（数十秒〜2分）。
- 手順 5〜6 で、`.env` をホームの下へ複写し、見本の対象DB の2行を `.env.targetdb` へ移す（値は表示しない。Deployment Pipeline の Q2: A）。
- スモークテストの要求（ログイン・既定の DSL の生成・ログアウト）は監査ログに残り、追記だけで消せない（project.md の決まりにより送る前に伝える）。監査イベントの確かめでは、アプリを一度止めて内部DB を複写する。

## Q1. 配備を行う時期

A. 今すぐ行う（`develop` への fast-forward の取り込み、`verify`（約 5 分）、`.env` の移し替え、入れ替えの順。入れ替えの間、アプリと見本の対象DB が数十秒〜2分止まる）
B. 依頼者が時期を指定する
X. Other (please specify)

[Answer]: A

## Q2. 戻し方の練習

戻し方（`rollback-runbook.md` 2節）は、`mastersmith:pre-followup` のイメージを、新しい `compose.yaml` と見本の対象DB の2行を除いた `.env` で起動する前提（前の版はこの2行を使わない）。この前提はまだ確かめていない。

A. 配備の後に、配備したアプリとは別の使い捨てのコンテナで、`mastersmith:pre-followup` を新しい `.env`（2行を除いたもの）で起動し、healthy になることだけを確かめる（配備したアプリは止めない。内部DB は使い捨てのコンテナの一時のボリュームにし、配備したデータに触れない。終わったら消す）
B. 配備の後に、実際に戻しを練習する（アプリを一度 `mastersmith:pre-followup` に戻して healthy とスモークテストを確かめ、また新しい版に戻す）
C. 練習しない（未確認のまま残る前提と、次に確かめる機会を記録する）
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

- 今すぐ配備する（Q1: A）。`deployment-strategy.md` 2節の手順 1〜12 を AI が行う: `develop` へ fast-forward で取り込み → `verify` → `mastersmith:pre-followup` のタグ → `.env` をホームの下へ複写して2行を `.env.targetdb` へ移す（値は表示しない）→ `docker compose --profile targetdb-postgres up -d --build` → healthy・見本の対象DB の起動・上限と環境変数の名前の確かめ
- スモークテスト（3節）は、ブラウザでの操作（ログイン・管理者向け領域・DSL の画面での既定の DSL の生成・ログアウト、任意で閉じるボタンの名前）を依頼者が行い、AI は健全性・ログ（ERROR が無い・1行1件・接続情報が無い）と監査イベント（`LOGIN_SUCCEEDED`・`DSL_GENERATED`・`LOGGED_OUT`）で裏付ける。監査イベントの確かめでアプリを一度止める。要求が監査ログに残ることは送る前に伝える
- 戻し方の前提を確かめる（Q2: A）。配備の後に、使い捨てのコンテナ（一時のボリューム）で `mastersmith:pre-followup` を新しい `.env` で起動して healthy になることだけを確かめ、終わったら消す。配備したアプリとデータには触れない
- どこかで通らなかったときは、`rollback-runbook.md` 1節の基準で戻すかを依頼者に諮る（戻すのは依頼者の承認を得てから）
- `origin` へのプッシュは依頼者が行い、その後の CI の結果を AI が確かめる
- 成果物は `deployment-log.md`・`smoke-test-results.md`・`health-check-report.md`

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
