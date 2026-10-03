# Deployment Execution の質問（260930-user-admin）

## 配備の前の確かめ（読み取りで確かめたこと・決まっていること）

- **関門**: 配備するアプリのソースは `b126bdc`（`develop` の先頭 `cc28d1f` とアプリのソースは同じ。その後は `aidlc/` の下の記録だけ）。clean 付きの verify・osvScan・E2E の全体（Build and Test）と、push の後の CI（run 37106195579）がすべて success。
- **スキーマの変更**: V9（`users.suspended` を足す、前進のみ）。起動のときに Flyway が当てる。移行の自動のテストと戻しの練習は置かない（U1 の決定）。今の内部DB は V8。
- **周りのサービス**: mailpit・targetdb-postgres は動いていて、この配備では触らない。`.env` は変えない。PC のディスクの空きは 30GiB（Environment Provisioning）。
- **手順**（`operation/deployment-pipeline/deployment-strategy.md`・`cd-config.md`、承認済み）:
  1. WAR の確かめ（`./gradlew :backend:bootWar`、作り直しの要否を記録）
  2. 今のイメージ（`c77c1bb247f9`）に戻し先のタグ `mastersmith:pre-user-admin` を付ける
  3. `app` だけを作り直して入れ替える（`docker compose --profile targetdb-postgres up -d --build app`。app が止まるのは作り直しの後の入れ替えの数十秒）
  4. スモークテスト S1〜S5（S1 healthy、S2 起動のログで V9 を当てたこと（キーと件数だけ）、S3 あなたのログイン、S4 利用者の管理の一覧（自分の行の印、停止中の利用者 0 人）、S5 監査の確かめ（アプリを数秒止め、内部DB の複写を読み取りで開いて `LOGIN_SUCCEEDED` があり利用者の管理の種類が 0 件であることを数える））
  5. `main` を `develop` の先頭へ fast-forward で取り込む（タグは付けない）。push はあなたが行う。
- バックアップは取らず、戻すときはイメージだけを戻す（`rollback-runbook.md`。戻している間は利用停止が効かない既知の制約）。
- AI は、ログインなど秘密情報の要る操作はせず（`project.md` の Corrections）、各操作の前に承認を得る。

## Question 1（配備の時刻）
配備をいつ行いますか？

A. このまとめの確認の後すぐに行う（手順 1〜3 は AI が行い、手順 3 の入れ替えの前にもう一度承認を得る。手順 4 の S3・S4 はあなたがブラウザで行う）
B. 後で行う（あなたが時刻を指定する。Other に書く）
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

- まとめの確認の後すぐに配備する（Q1: A）。手順は承認済みの `deployment-strategy.md` のとおり。
- 手順 1（WAR の確かめ）と手順 2（戻し先のタグ `mastersmith:pre-user-admin`）を AI が行い、手順 3（`app` だけの作り直しと入れ替え）の前にもう一度承認を得る。
- 手順 4 のスモークテストは、S1・S2・S5 を AI が値を出さずに確かめ、S3（ログイン）・S4（利用者の管理の一覧）はあなたがブラウザで行う。S5 ではアプリを数秒止める。
- 手順 5 で `main` を `develop` の先頭へ fast-forward で取り込む（タグは付けない、承認を得てから）。push はあなたが行う。
- 結果は `deployment-log.md`・`smoke-test-results.md`・`health-check-report.md` に記録する。V9 の事後の裏付け（U1-NFR10.1）もここに記録する。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
