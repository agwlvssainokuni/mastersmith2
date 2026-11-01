# スモークテストの結果（smoke-test-results）

Intent 261029-reference-lookup-cleanup。手順は `operation/deployment-pipeline/deployment-strategy.md` の 2節・3節。時刻は UTC。値（メールアドレス・パスワード・業務データの値・主キーの値・顧客のラベル・ロールの名前）は書かない。

- T0（配備の前の停止）: `2026-11-01T05:00:57Z`
- T1（入れ替え）: `2026-11-01T05:01:08Z`
- T2（S2 の開始）: `2026-11-01T05:01:29Z`
- 進め方: Q3: A（S2 を先に行って報告、AI がログを確かめた後に S3〜S8 を続けて行う）。画面の操作とログインは依頼者、裏付けは AI。

## 1. 結果の一覧

| # | 確かめ | 行う人 | 結果 | 根拠 |
|---|---|---|---|---|
| S1 | healthy と起動のログ | AI | **通過** | healthy 05:01:24Z、`/actuator/health` 200、メモリ 2 GiB・CPU 4、イメージ ID が `mastersmith:local`（`d8a467b60f6f…`）と一致。T1 以降のログで ERROR 0 件、`Migrating schema` 0 件で版 13（up to date）、救済の WARN 0 件、`TargetDataSourceConfig` の WARN 0 件、2つの新しい設定の名前（環境変数の形・Spring の項目の形の両方）0 件。WARN は前の版から出ている Flyway の `Database` 1 件・`BeanPostProcessorChecker` 1 件だけ。初期管理者のメールアドレスそのもののログの件数 0（値は表示していない）。詳細は `health-check-report.md` |
| S2 | 既存の業務データの一覧（U1 の R-01 の必須の確かめ） | 依頼者・AI | **通過** | 依頼者: `customers` の一覧と詳細の表示に問題なし、予定外の操作なし。AI: T2 以降のログで ERROR 0 件、WARN は `GlobalExceptionHandler` の 1 件（code `REFRESH_FAILED`、401。3節）だけで 5xx は無し。`sales.orders` の行の数を控えた（1） |
| S3 | ★ロール「スモーク確認乙」と権限の設定 | 依頼者 | **通過** | 依頼者「済んだ、問題なし」。監査 `ROLE_CREATED` 1・`ROLE_PERMISSION_CHANGED` 2（`orders` と `customers` を別々に保存。3節） |
| S4 | ★割り当てと作業ロールの切り替え | 依頼者 | **通過** | 監査 `ROLE_ASSIGNED` 1、`WORK_ROLE_SWITCHED`（S8 と合わせて 2） |
| S5 | ★参照の選択肢と作成 | 依頼者 | **通過** | 依頼者「問題なし」。監査 `BUSINESS_ROW_CREATED` `SUCCESS` 1（参照の保存前の確かめを通った成功の経路） |
| S6 | ★参照の変更と更新 | 依頼者 | **通過** | 監査 `BUSINESS_ROW_UPDATED` `SUCCESS` 1 |
| S7 | ★削除 | 依頼者 | **通過** | 監査 `BUSINESS_ROW_DELETED` `SUCCESS` 1。`sales.orders` の行の数は S2 と同じ 1（作った行は残っていない） |
| S8 | ★片付け | 依頼者 | **通過** | 監査 `ROLE_UNASSIGNED` 1・`ROLE_DELETED` 1、`WORK_ROLE_SWITCHED`（S4 と合わせて 2）。配備の後の複写で `roles` 3・割り当ては前と同じ（S9） |
| S9 | 見本の PG と監査の確かめ | AI | **通過** | 2節 |

- 配備の完了の条件（healthy とスモークテストがすべて通る。`team.md` の Deployment）を満たした。
- 保存前の参照の確かめの拒否（409 `BUSINESS_REFERENCE_NOT_FOUND`）と LOOKUP は、承認済みの決定（Q1: A）のとおり配備した版では確かめていない（結合テストと E2E 190・195 で確かめ済み。`cd-config.md` 5節）。

## 2. S9: 配備の後の複写の数え

複写 `~/.mastersmith-backup/mastersmith-data-202611011409-after-reference-lookup-cleanup.tgz`（権限 600）を `~/.mastersmith-tools/count-db3.sh` で読み取りだけで開き、T0 以降を数えた。`audit_events` の全体は 297（配備の前）→ 309。

| 種類 | 結果 | 期待（`deployment-strategy.md` 3.2） | 実際 | 判定 |
|---|---|---|---|---|
| `LOGIN_SUCCEEDED` | `SUCCESS` | ログインした回数 | 1 | 合う |
| `ROLE_CREATED` | `SUCCESS` | 1 | 1 | 合う |
| `ROLE_PERMISSION_CHANGED` | `SUCCESS` | 1（保存した回数） | 2 | 合う（保存を2回。3節） |
| `ROLE_ASSIGNED` | `SUCCESS` | 1 | 1 | 合う |
| `WORK_ROLE_SWITCHED` | `SUCCESS` | 2 | 2 | 合う |
| `BUSINESS_ROW_CREATED` | `SUCCESS` | 1 | 1 | 合う |
| `BUSINESS_ROW_UPDATED` | `SUCCESS` | 1 | 1 | 合う |
| `BUSINESS_ROW_DELETED` | `SUCCESS` | 1 | 1 | 合う |
| `ROLE_UNASSIGNED` | `SUCCESS` | 1 | 1 | 合う |
| `ROLE_DELETED` | `SUCCESS` | 1 | 1 | 合う |
| 結果 `FAILURE`・`UNKNOWN`（`BUSINESS_REFERENCE_NOT_FOUND` を含む） | — | 0 | 0 | 合う |
| ほかの種類（`INITIAL_ADMIN_RESCUED`・`DSL_*`・`LOGIN_FAILED`・`ACCESS_DENIED` など） | — | 0 | 0 | 合う |
| 合計 | | | 12 | |

- `BUSINESS_ROW_*` の 3 行の `target_schema`・`target_table`・`work_role_id` が空の件数はすべて 0（値は表示していない）。
- `roles` 3、初期管理者の割り当ては直接 0・グループ経由 3・作業ロールの保存 1・保存したロールが割り当て済み 1 で、配備の前（`deployment-log.md` 2.1）と同じ。確かめ用のロール「スモーク確認乙」は残っていない。
- 見本の PG の `sales.orders` の行の数は S2 で控えた数（1）と同じ。

## 3. 期待との差・気づき

1. **`ROLE_PERMISSION_CHANGED` が 2 件**: 依頼者に尋ねると、`orders` と `customers` の権限を別々に保存していた。権限の設定の画面は、保存せずに左の木でテーブルを切り替えると設定が破棄される旨の警告を出すため、2つのテーブルに権限を付ける S3 の手順ではテーブルごとに保存することになる。`deployment-strategy.md` の S3 の「保存は1回」は画面の作りと合っていなかった。期待の件数は保存した回数とし、操作と合うため合格と判定した（承認済みの記録は書き換えない）。
2. **`REFRESH_FAILED` の WARN 1 件（S2 の後）**: 入れ替えの前から開いていたタブを読み込み直したとき、古いトークンの更新が 401 で断られたものと見立てた（想定内。監査には残らない種類で、`LOGIN_SUCCEEDED` 1 件はその後のログイン）。その後は増えていない。
3. **S1 の点検の WARN の名前**: `deployment-strategy.md` の S1 に書いた名前ではログに出ないため、`TargetDataSourceConfig` の WARN の件数と、両方の形の名前で確かめた（`deployment-log.md` 3節 1）。

## Sources

- `operation/deployment-pipeline/deployment-strategy.md`（2節・3節・3.2）・`cd-config.md`（4.1・5節）
- `deployment-log.md`（2.1・2.2）・`health-check-report.md`・`deployment-execution-questions.md`（Q3: A）
- 依頼者の報告（S2・S3〜S8・保存の回数）
- `docker logs`・`docker inspect`・`psql`（件数だけ）・複写の読み取り（`count-db3.sh`）

## Assumptions & Open Questions

- [assumption] `REFRESH_FAILED` の WARN は、入れ替えの前から開いていたタブの古いトークンの更新による（ログの code と時刻からの見立てで、要求の中身は見ていない）。
