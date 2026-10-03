# Environment Provisioning の質問（260930-user-admin）

## 決まっていること・読み取りで確かめたこと（質問にしない）

配備先は開発者の PC 上のコンテナです。この段は、Docker の実行環境・イメージ・ボリューム・`.env`・`compose.yaml` の設定と読み替え、設計の値を1つずつ確かめます（`project.md` の Deployment の学び）。クラウドの基盤は作りません。

- この Intent では、`compose.yaml`・`Dockerfile`・`.env.example`・`backend/src/main/resources/application*.yaml`・`docker/` を変えていません（`git diff 31b980b develop` が空）。`.env` の変更もありません（Deployment Pipeline の段の決まり）。新しく要る設定の項目はありません。
- 実行環境（読み取りで確かめた、2026-10-03）:
  - colima: CPU 4・メモリ 6GiB・ディスク 100GiB（`aarch64`、runtime docker）
  - compose のプロジェクト `mastersmith`: app（`mastersmith:local`、healthy）・mailpit（healthy）・targetdb-postgres が動いている
  - ボリューム: `mastersmith_mastersmith-data`（内部DB）・`mastersmith_mastersmith-monitoring`・`mastersmith_mastersmith-targetdb-postgres`
  - Docker のディスクの使用: イメージ 47 個・22.1GB（片付けられる 16.4GB）、ボリューム 64 個・10.1GB（片付けられる 9.3GB）、ビルドのキャッシュ 2.6GB
  - PC のディスク: 空き 28GiB（使用 94%）
- この段では、設定の値をコンテナの実際の設定（`docker inspect`・`docker compose config`）と `.env` の項目の有無（値は見ない）で1つずつ確かめ、記録します。アプリの入れ替えは次の Deployment Execution の段で行います。

## Question 1（古いイメージ・ボリュームの片付け）
Docker の中に、前の Intent までの戻し先のイメージ（`mastersmith:pre-*` など 10 個）や、使い捨ての環境・テストで作ったボリュームが残っています（片付けられる量はイメージ 16.4GB・ボリューム 9.3GB）。PC のディスクの空きは 28GiB です。この段で片付けますか？

A. 片付けない（今の量を記録だけする。配備に必要な空き（イメージの作り直しに数 GB）は足りている）
B. 使われていない名前の無いイメージとビルドのキャッシュだけを片付ける（`docker image prune`・`docker builder prune`。戻し先のタグ付きのイメージとボリュームは残す）
C. B に加えて、どのコンテナにも使われていないボリュームも片付ける（`mastersmith_*` の3つは残す。片付ける一覧を先に見せて、あなたの承認を得てから消す）
X. Other (please specify)

[Answer]: B

## Consolidated Summary Confirmation

- 配備先は開発者の PC 上のコンテナのまま。この Intent で変わった設定・イメージの作り方・`.env` は無く、新しく要る設定の項目も無い。
- この段で、実行環境（colima の CPU・メモリ・ディスク）、動いているコンテナとイメージ、ボリューム、`docker compose config` の値、`.env` の項目の有無（値は見ない）を、設計の値（前の Intent の記録と、この Intent の各単位の `infrastructure-specification.md`）と1つずつ突き合わせて記録する（`environment-inventory.md`・`validation-report.md`）。アプリの入れ替えは次の Deployment Execution の段で行う。
- 使われていない名前の無いイメージとビルドのキャッシュだけを片付ける（`docker image prune`・`docker builder prune`）。戻し先のタグ付きのイメージ（`mastersmith:pre-*` など）とボリュームは残す（Q1: B）。片付ける前後の量を記録する。
- PC のディスクの空き（28GiB、使用 94%）を記録し、配備に必要な空きが足りることを確かめる。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
