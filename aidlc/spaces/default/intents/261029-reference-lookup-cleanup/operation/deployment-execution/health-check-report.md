# 健全性の確認の報告（health-check-report）

Intent 261029-reference-lookup-cleanup。時刻は UTC。手順は `operation/deployment-pipeline/deployment-strategy.md` の 2節（S1）。

## 1. 入れ替えの直後と配備の後の複写の直後

| 項目 | 入れ替えの直後（healthy 05:01:24） | 複写の後の起動し直し（healthy 05:09:37） |
|---|---|---|
| コンテナの状態 | healthy | healthy |
| イメージ | `sha256:d8a467b60f6f…`（`mastersmith:local` と一致） | 同左（一致） |
| `/actuator/health` | 200 | 200 |
| メモリ・CPU の上限 | 2,147,483,648 バイト（2 GiB）・4 CPU | 同左 |
| restart 回数・OOMKilled | 0・false | — |
| ERROR | 0 件 | — |
| WARN | 前の版から出ているもの 2 件（Flyway の `Database` 1・`BeanPostProcessorChecker` 1） | — |
| Flyway | `Migrating schema` 0 件、版 13（up to date） | — |
| 救済（初期管理者） | WARN 0 件 | — |
| 2つの新しい設定の点検 | `TargetDataSourceConfig` の WARN 0 件、設定の名前（環境変数の形・Spring の項目の形）0 件 | — |
| 初期管理者のメールアドレスそのもの | ログの中の件数 0（値は表示していない） | — |

- WAR の SHA-256: 手元の作り直しの前後と新しいイメージの中で `4cd5c41626f4dd0513656e551b39ec1a71038fedb5622901ea5db8543008222f`（同じ）。
- 見本の対象DB（`mastersmith-targetdb-postgres-1`）と Mailpit: 作り直していない（入れ替えの後も Up 4 hours のまま）。
- 戻し先: `mastersmith:pre-reference-lookup-cleanup`（`e0ede6ddd5c4…`）がある。試験用のタグ `mastersmith:reference-lookup-cleanup` は消した（`deployment-log.md` 順 12）。
- アプリが止まった時間: 05:00:57〜05:01:24 の約 27 秒、05:09:27〜05:09:37 の約 10 秒。

## 2. 2つの新しい設定（U1 の R-01）

- `.env` に `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_REFERENCE`・`MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL` は無く（項目名の数 0）、WAR の `application.yaml` の既定（5s・10s）で動いている。
- 点検の WARN が 0 件で、ヘルスチェックの 200 では見分けられない誤り（対象DB 全体が使えない）が無いことは、S2 の既存の業務データの一覧が通ったことで確かめた（`smoke-test-results.md`）。
- `deployment-strategy.md` の S1 に書いた名前（`QUERY_TIMEOUT_REFERENCE`・`REFERENCE_READ_TOTAL`）はログには出ない（ログに出るのは `mastersmith.target-db.query-timeout.reference`・`mastersmith.target-db.reference-read-total`）。そのため点検の WARN そのもの（`TargetDataSourceConfig`）の件数で確かめた（`deployment-log.md` 3節 1）。

## 3. 資源の上限と性能の目標

- アプリの上限（メモリ 2g・CPU 4）は前の版と同じ。性能の目標の判定は、承認済みの決定（`cd-config.md` 3.2、Q2: B）のとおり Performance Validation で行い、配備の前後で k6 は流していない。
- 配備の後に負荷の下での値は取っていない。

## 4. 内部DB

- スキーマの変更なし（版 13）。配備の前の複写（`…-202611011400-pre-reference-lookup-cleanup.tgz`、38,778 バイト）と配備の後の複写（`…-202611011409-after-reference-lookup-cleanup.tgz`、39,772 バイト）を読み取りで開けた（どちらも権限 600、`~/.mastersmith-backup/`）。監査の全体は 297 → 309。

## 5. 判定

- 健全性の確認はすべて通過。配備の完了の条件（healthy とスモークテストがすべて通る）を満たした。
- 起動し直しのときの ERROR・WARN の件数は、入れ替えの直後と同じイメージ・同じ設定のため取り直しは healthy・200・イメージ ID の一致にとどめた。

## Sources

- `deployment-log.md`・`smoke-test-results.md`
- `operation/deployment-pipeline/deployment-strategy.md`（S1）・`cd-config.md`（3.2・4節）
- `docker inspect`・`docker logs`（jq で項目名と件数だけ）・`/actuator/health`・`unzip -p`・`sha256sum`

## Assumptions & Open Questions

- None.
