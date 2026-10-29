# 健全性の確認の報告（health-check-report）

Intent 261027-perf-targets-review。時刻は UTC。

## 1. 配備の直後と作り直しの直後

| 項目 | 旧イメージで起動し直した直後（03:18:09） | 作り直した直後（10:25:25） |
|---|---|---|
| コンテナの状態 | healthy | healthy |
| イメージ | `sha256:3075065a2653…` | `sha256:e0ede6ddd5c4…` |
| `/actuator/health` | 200 | 200 |
| メモリ・CPU の上限 | 2,147,483,648 バイト（2 GiB）・4 CPU | 同左 |
| restart 回数 | 0 | 0 |
| ERROR | 0 件 | 0 件 |
| WARN | 前の版から出ているもの 2 件 | 同左 |
| Flyway | 13 件を検証、移行 0 件、現在の版 13 | 同左 |
| 救済（初期管理者） | ログ 0 件 | 同左 |

- WAR の SHA-256: 動いているイメージの中 `ff50498b…`、手元 `ff50498b…`（同じ）。
- 見本の対象DB（`mastersmith-targetdb-postgres-1`）: PORTS は `5432/tcp` だけで E2E の番号は閉じている。Mailpit: healthy。どちらも作り直していない。
- 戻し先: `mastersmith:pre-perf-targets-review`（`3075065a2653…`）が存在する。

## 2. 資源の上限と性能の目標

- 配備したアプリは Build and Test の測り直しと同じ中身・同じ上限（メモリ 2g・CPU 4）。性能の目標の判定は Build and Test の `test-results.md` を正とし、配備の前後で流し直していない（`cd-config.md` の 3.2）。
- 配備後の負荷の下での値は取っていない（k6 は流さない決定）。

## 3. 内部DB

- スキーマの変更なし（V13）。配備の後の複写（`~/.mastersmith-backup/mastersmith-data-202610291924-after-perf-targets-review.tgz`、権限 600）を読み取りで開けた。監査は全体 295 件。

## 4. 判定

- 健全性の確認はすべて通過。配備の完了の条件（healthy とスモークテストがすべて通る）を満たした。
- 気づき: コンテナが旧イメージのまま起動し直された件は、作り直しで解消した（`deployment-log.md` の 3節）。

## Sources

- `deployment-log.md`・`smoke-test-results.md`
- `docker inspect`・`docker logs`・`/actuator/health`

## Assumptions & Open Questions

- [assumption] 作り直した直後の `/actuator/health` の 200 は、compose の健全性の確かめと同じ道で確かめた。画面の操作は作り直した後に取り直していない。
