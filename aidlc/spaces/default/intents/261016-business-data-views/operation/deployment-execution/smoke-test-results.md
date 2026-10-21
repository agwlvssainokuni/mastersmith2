# スモークテストの結果（smoke-test-results）

`operation/deployment-pipeline/deployment-strategy.md` の 3節の S1〜S13 の結果です。初期管理者に前からロールの割り当てと作業ロールの保存があったため、依頼者の決定 A で S5〜S7・S11 と監査の期待を合わせ直しました（`deployment-log.md` の 5節）。パスワードの要る操作と画面の操作は依頼者が行い、AI はアプリのログ・監査の記録・見本の PG の件数で裏付けました。値（メールアドレス・パスワード・DSL の本文・業務データの値）は表示していません。

## 結果

| # | 確かめ | 行った人 | 結果 | 根拠 |
|---|---|---|---|---|
| S1 | 健全性と起動のログ | AI | 通過 | healthy、`/actuator/health` 200。レベルの件数は INFO 38・WARN 2・ERROR 0。WARN は前の版から出ている2件（Spring の Bean の案内、Flyway の H2 の版の案内）だけで、救済の WARN は無い。`MASTERSMITH_TARGET_DB_WRITE_` を含む行 0。ログの中の `.env` の初期管理者のメールアドレスの値の件数 0 |
| S2 | 移行と JVM の設定 | AI | 通過 | Flyway の `Migrating schema` 1 件（版 13）。PID 1 のコマンド行に `ExitOnOutOfMemoryError`・`MaxRAMPercentage=50.0` の2件。初期管理者の INFO のキーに `maskedEmail` があり、`email` は無い |
| S3 | 見本の PG | AI | 通過 | 順 7 の確かめが `1\|t\|t\|t\|f\|f\|f`。`sales.customers` の行の数は 2（控えた） |
| S4 | ★ログイン | 依頼者 | 通過 | ログイン中のままタブを読み込み直し、ホームが開けた（依頼者の答え）。そのため `LOGIN_SUCCEEDED` は 0 件 |
| S5 | ★ロールと権限の設定（合わせ直し） | 依頼者 | 通過 | 「スモーク確認乙」だけを作り（甲は作らない）、`sales.customers` に主権限 `FULL` と補助権限、ビュー `sales.customer_orders` に `READ` を付けて保存した。保存は2回押した（依頼者に確かめた） |
| S6 | ★割り当て（合わせ直し） | 依頼者 | 通過 | 自分（初期管理者）に乙を割り当てた |
| S7 | ★作業ロールの切り替えと業務のメニュー（合わせ直し） | 依頼者 | 通過 | 作業ロールを今の作業ロール → 乙 → 元の作業ロールと切り替え、乙のときに業務の区画が `customers` と `customer_orders` の項目になった（依頼者の答え） |
| S8 | ★一覧と詳細 | 依頼者 | 通過 | 検索・並べ替え・1ページの件数の選択・ページ送りの表示・詳細と戻ったときの一覧の状態が想定どおり（依頼者の答え）。複数ページの送りは E2E 180 で確かめ済み |
| S9 | ★作成・更新・削除 | 依頼者 | 通過 | 架空の値の行を作り、更新し、消した（依頼者の答え）。監査の `BUSINESS_ROW_CREATED`・`BUSINESS_ROW_UPDATED`・`BUSINESS_ROW_DELETED` 各1件で裏付け |
| S10 | ★ビュー | 依頼者 | 通過 | `customer_orders` の一覧に更新・削除・作成の操作が出なかった（依頼者の答え） |
| S11 | ★片付けの判断 | 依頼者 | 通過 | 乙の割り当てを外して乙を消した。予定外の操作は無し（依頼者の答え）。S13 の複写で裏付け |
| S12 | 見本の PG の片付けの確かめ | AI | 通過 | `sales.customers` の行の数 2（S3 と同じ） |
| S13 | 監査の記録 | AI | 通過 | 下の表。期待と合う |

- 依頼者の答え（S4〜S11）: 「確認完了、想定通りで問題なし」。

### S13: T0（2026-10-20T23:48:18Z）以降の監査

配備の後の複写（00:15:48Z に止めて複写、00:15:59Z に healthy）を読み取りで開き、種類と結果ごとに数えました。結果はすべて `SUCCESS` で、`FAILURE`・`UNKNOWN` の行は 0 件です。表に無い種類（`INITIAL_ADMIN_RESCUED`・`DSL_*` を含む）は出ていません。

| 種類 | 件数 | 期待 | 判定 |
|---|---|---|---|
| `ROLE_CREATED` | 1 | 1 | 一致 |
| `ROLE_PERMISSION_CHANGED` | 2 | 2（保存した回数。合わせ直しの 1 を、保存を2回押したことで直した） | 一致 |
| `ROLE_ASSIGNED` | 1 | 1 | 一致 |
| `ROLE_UNASSIGNED` | 1 | 1 | 一致 |
| `ROLE_DELETED` | 1 | 1 | 一致 |
| `WORK_ROLE_SWITCHED` | 2 | 2 | 一致 |
| `BUSINESS_ROW_CREATED` | 1 | 1 | 一致 |
| `BUSINESS_ROW_UPDATED` | 1 | 1 | 一致 |
| `BUSINESS_ROW_DELETED` | 1 | 1 | 一致 |
| `LOGIN_SUCCEEDED` | 0 | 0（ログイン中のまま読み込み直した） | 一致 |

- `BUSINESS_ROW_*` の3件は、どれも `target_schema`・`target_table`・`work_role_id` が空でない（値は表示していない）。
- ロールの数は 3、初期管理者の割り当て（直接 0・グループ経由 3）と作業ロールの保存（1）は配備の前と同じ。確かめ用のロールは残っていない。

## 判定

**合格**（S1〜S13 のすべてが通過）。

## Sources

- `operation/deployment-pipeline/deployment-strategy.md` 3節（確かめの一覧と監査の期待の件数）
- `operation/deployment-pipeline/cd-config.md` 3.2・4節
- `operation/deployment-execution/memory.md`（合わせ直しと保存の回数の記録）
- `backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java`

## Assumptions & Open Questions

- S4〜S11 は依頼者の目視の答えに基づく。画面の振る舞いは Build and Test の E2E（256 件）でも確かめ済み。
- 合わせ直しで、作業ロールが自動で選ばれる流れ（割り当ての直後）は確かめていない。結合テストと E2E で確かめ済み。
