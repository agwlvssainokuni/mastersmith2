# Deployment Pipeline の質問（260929-log-deps-cleanup）

## 決まっていること（質問にしない）

前の Intent（260928-quality-followup）の配備の手順（`aidlc/spaces/default/intents/260928-quality-followup/operation/deployment-pipeline/`）と `project.md` の Deployment の学びで決まっている点です。

- 配備先は開発者の PC 上のコンテナ（`compose.yaml`、プロジェクト名 `mastersmith`）。依頼者の承認のうえで手で配備する。
- 今動いているのはイメージ `mastersmith:local`（`sha256:252bc44e…`、前の Intent で配備した版）。配備の前に `mastersmith:pre-log-deps-cleanup` のタグを付けて戻し先にする。
- 内部DB のスキーマ（Flyway）と `.env` の変更は無いため、バックアップと戻しの練習は行わず、戻しはイメージだけにする（`project.md` の Deployment の学び）。
- 性能に関わる変更は無いため、配備の前の k6 は流さない。関門（verify・E2E・CI）は Build and Test の結果を正とする。
- 変えた compose の otel-collector（0.162.0）は profile `observability` で、今は動いていない。手元の監視（lgtm）は、コード生成の段で警報の決まりとダッシュボードを読み込み直して動いている。
- 起動のログの確かめは、値を出さずに、ロガーとキーの名前と件数だけを見る（`project.md` の Corrections）。初期管理者は既にいるため、起動のたびに「既にいるため、作成しませんでした」の INFO が出る。これにキー `maskedEmail` があり、キー `email` が無いことを確かめる。

## Question 1（main への取り込みとタグ）
前の Intent では、`main` を `develop` の先頭へ fast-forward で取り込み、タグは付けませんでした（`v*` のタグのプッシュで CI が公開の GitHub のリリースを作り WAR を添付するため、タグと公開のリリースは配備先が決まったときに決める、という判断）。今回はどうしますか？

A. 前の Intent と同じく、`main` を fast-forward で取り込み、タグは付けない
B. `main` へ取り込み、タグも付ける（公開の GitHub のリリースが作られる。版の名前は Other に書く）
C. `main` へは取り込まない（`develop` への配備だけ）
X. Other (please specify)

[Answer]: A

## Question 2（スモークテストの画面の確かめ）
今回の画面の変更は、make-you-chic-ui の選ばれたタブと primary のボタンの hover の文字の色です（E2E の 100 で確かめ済み）。配備した後のスモークテストで、画面での見た目も確かめますか？

A. 確かめる（あなたがブラウザで、DSL の管理の画面のタブと、プリファレンスの画面の保存のボタンの hover を見る。ログインはあなたが行う）
B. 確かめない（ヘルスチェック・起動のログ・ログインのできることの確かめまでにする）
X. Other (please specify)

[Answer]: B

## Consolidated Summary Confirmation

- 決まっていること（冒頭）のとおり、戻し先のタグを付けてからアプリのイメージを作り直して入れ替え、戻しはイメージだけにする。バックアップ・戻しの練習・配備の前の k6 は行わない。
- `main` は `develop` の先頭へ fast-forward で取り込み、タグは付けない（Q1: A）。プッシュは依頼者が行う。
- スモークテストは、ヘルスチェック・起動のログ（キー `maskedEmail` があり `email` が無いことを、値を出さずに確かめる）・依頼者のログインまでにし、画面の見た目は確かめない（Q2: B）。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
