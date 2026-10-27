# スモークテストの結果（smoke-test-results）

`deployment-strategy.md` の S1〜S9 の結果です（Deployment Pipeline の Q3: A、小さな範囲。ロールは作らず、初期管理者のパスワードは変えない）。個人に関する値・資格情報・業務データの値は記録していません。

| # | 確かめ | 行う人 | 結果 |
|---|---|---|---|
| S1 | healthy と起動のログ | AI | 合格。`/actuator/health` 200。ログは INFO 37・WARN 2・ERROR 0。WARN は前の版から出ている Flyway の H2 の版の案内と Spring の Bean の案内。書き込み設定（`MASTERSMITH_TARGET_DB_WRITE_`）を含む行 0 |
| S2 | 起動の詳細 | AI | 合格。Flyway は `Successfully validated 13 migrations`・現在の版 13・`Migrating schema` 0 件。初期管理者の INFO は「既にいるため作成しませんでした」でキー `maskedEmail`（`email` なし）、救済の WARN なし。PID 1 に `ExitOnOutOfMemoryError`・`MaxRAMPercentage=50.0` が 1 件ずつ |
| S3 | 読み取りのプールの既定 | AI | 合格。WAR の既定は 10、`.env` に上書きの項目なし |
| S4 | 見本の PG | AI | 合格。`1|t|t|t|f|f|f`、`sales.customers` 2 行、番号は閉じた |
| S5 | ログイン | 依頼者 | 合格。初期管理者でログインし、ホームが開いた（ログイン 1 回） |
| S6 | 業務データ | 依頼者 | 合格。`customers` の一覧（検索・並べ替え・件数選択）と詳細が動き、戻ったとき一覧の状態が保たれた |
| S7 | 表示の設定 | 依頼者 | 合格。保存で成功の知らせが出て、元の値に戻して保存した（監査には残らない） |
| S8 | WAR とログの再確認 | AI | 合格。WAR に `TableAccessResponse` 0 件。S5〜S7 の後のログは INFO 39・WARN 2・ERROR 0 |
| S9 | 配備の後の複写と監査の件数 | AI | 合格（下記） |

## S9 の監査の件数

T0=`2026-10-26T23:45:02Z` 以降の `audit_events`（複写を読み取りだけで開いて数えた。全体は 293 件）:

| 種類 | 結果 | 件数 | 期待 |
|---|---|---|---|
| `LOGIN_SUCCEEDED` | SUCCESS | 1 | 依頼者のログイン 1 回と一致 |
| `INITIAL_ADMIN_RESCUED` | — | 0 | 0（Q1: A、救済は働かない） |
| その他の種類 | — | 0 | 0 |
| FAILURE・UNKNOWN | — | 0 | 0 |

合計 1 件で、期待どおり。

## 注記

- 数える道具はリポジトリの外（ホームの下）に置いた小さな Java で、README の `CountRows` に T0 以降で絞る条件を足したもの。複写の展開先は数えた後に消した。
- Q3: A のため、複数ページの送りの確かめ（見本が 2 行）、ロール・割り当て・書き込みの確かめは行っていない。E2E 256 件で確かめ済み。
