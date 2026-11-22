# 信頼性の要件 — u2-portable-sql

上流の要件 NFR4（信頼性）を、この単位の `NFR4.x` に分けます。この単位の信頼性の中心は、同時の書き込みが H2 の今の結果（後勝ち）に収束することと、失敗の後にトランザクションが壊れないことです。可用性の数値の目標は、配備先が開発者の PC 上のコンテナだけのため、この Intent では置きません（`team.md` の Deployment）。

## 要件

| ID | 要件 | 確かめ方 | 由来 |
|---|---|---|---|
| NFR4.1 | 同じ鍵（行がまだ無い）へほぼ同時に書いた 2 つのトランザクションは、後に確定した方の値が残る（後勝ち）。結果は H2 の今の `MERGE ... KEY` と同じで、PostgreSQL でも同じになる。 | H2 の結合テスト（待ち合わせで先の側の保持を約 500 ms に作る）。PostgreSQL での確かめは U3 | 要件 NFR4、機能設計 BR2.2 |
| NFR4.2 | 鍵の重なり（SQLState 23505）の後、トランザクションを壊さずに続行できる。PostgreSQL は、セーブポイントを使わないと次の文が 25P02（中断）になるため、重なりの後は必ずセーブポイントまで戻す。トランザクションに rollback-only の印を付けない（挿入を JDBC の中で行い、例外をその中で受ける）。 | H2 の結合テストで、重なりの後に同じトランザクションの次の文が通り、確定が通ること。PostgreSQL での確かめは U3 | 要件 NFR4、機能設計 BR2.1・BR2.4 |
| NFR4.3 | 待ちの上限切れ（H2 の `HYT00`/50200・PostgreSQL の `55P03`）は、収束させず失敗として出す。上限なく待ち続ける経路を足さない。上限切れの後も、セーブポイントまで戻せばトランザクションを使い続けられる。 | H2 の結合テスト（保持が約 4 秒を超えるとき、上限切れとして分類される失敗が出ること）、`RowLockFailures` の単体テストに `55P03` を足す | 要件 NFR4（PostgreSQL で上限なく待ち続けない）、機能設計 BR2.5・BR2.6 |
| NFR4.4 | 鍵の重なりの後の更新が 0 行（先の側が行を消した等）のときは、結果を作らず想定外の失敗として扱う（黙って成功にしない）。 | 単体テスト（判定の表） | 機能設計 BR2.5 |
| NFR4.5 | 書き直した文は、H2 での結果を変えない（既存の結合テストの全件が通る）。repository・store のメソッドの形（引数・戻り値・例外の種類）は変えない。 | 既存の結合テストの全件（`WorkRoleSelectionMergeIT` を除く。これは新しい手順の期待に書き直す） | 要件 NFR4、契約 C5、FR5.4 |
| NFR4.6 | スキーマの変更を足さない（移行・列・制約を足さない）。前の版のアプリと同じ表の形のままで、この単位の変更は 1 つ前の版へ戻せる（イメージだけを戻せばよい）。 | 移行のファイルが変わらないこと | 要件 NFR4、`team.md` の Deployment（後方互換） |
| NFR4.7 | `RowLockFailures` に PostgreSQL の SQLState `55P03` を足す変更は、H2 の分類（50200・40001・`HYT00`・`40001` と 3 つの型）を変えない。`businessdata` の別の分類（`RowSqlFailureClassifier`）には手を入れない。 | `RowLockFailuresTest` に `55P03` を足し、既存の判定が変わらないこと | 機能設計 BR2.6 |

## 失敗の扱い

- 書き込みの失敗の画面での扱い（作業ロールの `Busy`・プレビューの競合の応答）は今のまま。この単位は、失敗の区分・応答の形を変えない。
- 同時の実行の失敗は、運用者の操作では直らない一時的なもの（上限切れ）か、手順の中で収束するもの（重なり）で、再試行はアプリが行う（重なりの後の 1 回の更新のやり直しだけ）。

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md`（NFR4）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/functional-design/rules.md`（BR2.1〜BR2.7）・`functional-spec.md`（F5〜F9）
- `aidlc/spaces/default/memory/team.md`（Deployment）

## Assumptions & Open Questions

- [assumption] H2 の未確定の挿入への待ちは約 4 秒（観測）で、H2 の版（2.4.240）に依る。H2 を上げるときは、`WorkRoleSelectionMergeIT` の保持時間の期待を見直す。
