<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-23T03:10:00Z — ステージの手順は AWS の環境（VPC・IAM・Secrets Manager）を前提にしているが、配備先が開発者の PC 上のコンテナのため、環境を colima 上の Docker・イメージ・ボリューム・.env・compose.yaml の設定と読み替え、設計の各値を実際にコンテナを起動して1つずつ確かめる形にした。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-23T03:10:00Z — 質問を作る前に、この PC の Docker の状態を読み取りだけで調べ、colima の VM が CPU 2 で compose.yaml の CPU の上限 4 を満たせず起動できないことを見つけた。これを最初の質問にした（設計の値は変えず、環境変数で下げられるようにする決定になった）。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-23T03:10:00Z — 秘密情報の .env は AI が作ったが、署名鍵は openssl の乱数を画面に出さずにファイルへ直接書き込み、確認では値を表示せずに検索だけを行った。初期管理者は依頼者が入れるため、本段ではログインの確認をせず、設定の確認に絞った。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-23T03:10:00Z — コンテナの CPU の上限 2 で、ログインの照合の時間の目標（同時 10 件で 1 秒以内）を満たすか。Performance Validation でどの値で測るかを決める。
- 2026-09-23T03:10:00Z — 起動のログに H2 2.4.240 が Flyway の確認済みの版（2.3.232）より新しいという WARN が出る。動作は問題ないが、Flyway の更新で消えるかを依存関係の更新のときに見る。
