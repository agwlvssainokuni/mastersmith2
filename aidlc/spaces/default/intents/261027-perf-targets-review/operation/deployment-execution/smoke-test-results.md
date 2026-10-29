# スモークテストの結果（smoke-test-results）

Intent 261027-perf-targets-review。確かめの定義は `operation/deployment-pipeline/deployment-strategy.md`（Q2: A、小さな範囲）。値（メールアドレス・パスワード・業務データの値・主キーの値）は表示していない。

| # | 確かめ | 行った人 | 結果 | 根拠 |
|---|---|---|---|---|
| S1 | healthy と `/actuator/health` | AI | **通過** | 旧イメージで起動したコンテナは 03:18:09Z に healthy、200。作り直したコンテナ（`e0ede6ddd5c4`）も約 10 秒で healthy、`/actuator/health` 200、メモリ 2 GiB・CPU 4・restart 0 |
| S2 | 起動のログ | AI | **通過** | ERROR 0 件。Flyway は 13 件を検証し `No migration necessary`（現在の版 13）。WARN は前の版から出ているもの（Flyway の H2 2.4.240 の版の案内と Bean の後処理の案内）だけで、起動 1 回あたり 2 件。初期管理者の INFO はキー `maskedEmail` のみ（`email` なし）。救済のログ 0 件 |
| S3 | 初期管理者でのログイン | 依頼者 | **通過** | ログイン 1 回、ホームが開いた。監査に `LOGIN_SUCCEEDED` 1 件 |
| S4 | 業務データの一覧と詳細 | 依頼者 | **通過** | 一覧と詳細に問題なし（「問題なし」と報告） |
| S5 | 読み取りのプールの既定 10 | AI | **通過（設定の確かめ）** | 配備したアプリは `hikaricp` の指標を公開しない既定のため、WAR の `application.yaml` に `MASTERSMITH_TARGET_DB_POOL_MAXIMUM_SIZE:10` があることで確かめた（`unzip -p` で 1 件）。指標では確かめていない |
| S6 | 監査の確かめ | AI | **通過** | 配備の後の複写を読み取りで開き、T0（`2026-10-29T03:17:57Z`）以降の `audit_events` を数えた: `LOGIN_SUCCEEDED` SUCCESS 1、`LOGGED_OUT` SUCCESS 1。合計 2、全体 295。FAILURE・UNKNOWN は 0。予定に無い管理の操作は無い |

## 期待の件数との突き合わせ

| 種類 | 期待 | 実際 |
|---|---|---|
| `LOGIN_SUCCEEDED` | 依頼者がログインした回数（1） | 1 |
| `LOGGED_OUT` | ログアウトの操作（依頼者が行ったとき） | 1（依頼者の操作と読む。ログアウトした回数は、確かめるときに聞いていない） |
| その他 | 0 | 0 |

## 注意

- S3・S4・S6 は、旧イメージ（`3075065a2653`）のコンテナで行った。その後コンテナを新しいイメージ（`e0ede6ddd5c4`）に作り直した（`deployment-log.md` の 3節）。作り直したあとに取り直したのは S1・S2 だけ。
- S5 の指標による確かめと、複数ページの送り、書き込みは行っていない（範囲の外）。

## Sources

- `deployment-strategy.md`、`deployment-log.md`、`health-check-report.md`
- `docker logs`・`docker inspect`・複写の読み取りの件数

## Assumptions & Open Questions

- [assumption] `LOGGED_OUT` 1 件は、依頼者がスモークテストの後にログアウトした操作による（依頼者に回数を聞いていない）。
