# 内部DB を PostgreSQL にも対応させるための下調べ（2026-11-14）

内部DB（今は組み込みの H2）を PostgreSQL でも動かせるようにする Intent を始めるときの材料。依頼者の求めで、Intent の外で行った捨ての試しとコードの洗い出しの結果をまとめた。

- 前提（依頼者の指定）: 内部DB と対象DB（DSL の対象の業務DB）は別扱いのまま変えない。
- 範囲: まず PostgreSQL だけ。MySQL・MariaDB は後の Intent とし、試しで分かった差を末尾に参考として残す。
- 調べた時点のコード: `develop` のコミット `8eec0ba4`。
- 書き方: 試しやコードで **確かめた事実** と、**確かめていない見込み** を分けて書く。

## 1. 試しの条件

- DB: H2 2.4.240（比べるための基準）・PostgreSQL 18.6・MySQL 26.10.0・MariaDB 13.0.2。コンテナのイメージは `compose.yaml` と同じ版とダイジェストを使った。
- 部品: Hibernate ORM 7.4.13.Final、JDBC ドライバーは `backend/gradle.lockfile` と同じ版（postgresql 42.7.14・mysql-connector-j 9.7.0・mariadb-java-client 3.5.10）。
- 区分の部品: ビルド済みの本体のクラス（`RowLockFailures`・`RoleStoreClassifier`・`StoreFailureClassifier`）をそのまま呼んで判定した。
- 表: 本番の移行（V10〜V12）と同じ制約の名前で、`users`・`roles`・`user_role_assignments`・`work_role_selections`・`groups`・`group_members`・`permission_settings` を作った（列は判定に要るものだけ）。
- Hibernate は Spring なしで直接使った（Spring Data のリポジトリの口は通していない）。
- 試しのコードはリポジトリの外に置いた使い捨てで、残していない。作り直すときは、上の表を作り、2つのスレッドで排他と書き込みを競わせ、例外の連なりを区分の部品に渡す形で再現できる。

## 2. 確かめた事実: 違反の区分（PostgreSQL）

| 場合 | 結果 |
|---|---|
| 一意の制約の重なり（`uk_roles_name_key`・`uk_groups_name_key`・`uk_users_email`） | SQLState 23505。制約の名前は小文字で取れ、区分の部品は大文字にそろえて照らすので、H2 と同じ区分になる |
| 主キーの重なり（`pk_user_role_assignments`・`pk_work_role_selections`・`pk_group_members`） | 23505。制約の名前が取れ、H2 と同じ区分になる |
| 親の無い子の追加（外部キー） | 23503。制約の名前が取れ、H2 と同じ区分になる |
| **参照のある親の削除（`ON DELETE RESTRICT` の外部キー）** | **23001（restrict_violation）。Hibernate は制約の名前を取れず（kind=OTHER）、区分は UNEXPECTED になる**（ロールの削除・利用者の削除・グループの削除で確かめた） |
| 参照のある親の削除（ON DELETE の指定なしの外部キー） | 23503。制約の名前が取れる |
| CHECK の制約の違反 | 23514。制約の名前が取れる（H2 は取れない）。区分は H2 と同じく UNEXPECTED |

- 違反の文には、H2 と同じく重なった値（メールアドレスなど）が入る（PostgreSQL は `Key (email)=(...)` の形）。値をログに出さない今の対策は、そのまま要る。
- コードを読んで確かめたこと: メールアドレスの重なりの判定（`UserAccountService.isEmailUniqueViolation`）と招待中のメールアドレスの判定（`InvitationService.isPendingEmailViolation`）は、Hibernate の制約の名前を大文字にそろえて照らす。共通の一意の違反の判定（`common/error/web/UniqueViolations`）は型と 23505 で見る。どれも PostgreSQL の小文字の名前で当たる見込み（利用者のメールアドレスは、試しで `uk_users_email` が取れたことまで確かめた）。

## 3. 確かめた事実: 排他と待ち（PostgreSQL）

| 場合 | 設定なし | `lock_timeout` = 2 秒 | H2（基準） |
|---|---|---|---|
| 排他の上限のヒント 3000ms（`jakarta.persistence.lock.timeout`） | 3.0 秒で切れる | 3.0 秒で切れる（ヒントの値が勝つ） | 3.0 秒で切れる |
| 書き込みの待ち（ネイティブの UPDATE・JPQL の一括更新） | **上限なし**（試しでは 10 秒で打ち切り。区分は UNEXPECTED） | **2.0 秒で切れ、LOCK_TIMEOUT** | 約 2.0 秒で切れ、LOCK_TIMEOUT |
| 確定前の同じ一意の鍵への追加の待ち | **上限なし** | **2.0 秒で切れ、LOCK_TIMEOUT** | 約 2.0 秒で切れ、LOCK_TIMEOUT |
| 先の側が確定した後の一意の鍵の重なり | NAME_TAKEN | NAME_TAKEN | NAME_TAKEN |
| 行き詰まり（デッドロック） | 約 1 秒で検出（`deadlock_timeout`）、LOCK_TIMEOUT | 同じ | LOCK_TIMEOUT |
| SKIP LOCKED（ヒント -2） | 動く | 動く | 動く |
| 親の行に排他をかけている間の子の追加 | 待たない | 待たない | 待たない |

- 排他の失敗は SQLState 55P03（待ち切れ）・40P01（行き詰まり）で返り、Hibernate が `PessimisticLockException` ← `LockTimeoutException` ／ `LockAcquisitionException` に変換する。`RowLockFailures.isLockFailure` は連なりの中の型で真になる（`RowLockFailures` の誤りの番号と SQLState の一覧は H2 の値だけだが、型で当たる）。
- PostgreSQL の `PESSIMISTIC_WRITE` は `FOR NO KEY UPDATE` になる（H2 は `FOR UPDATE`）。親の行の排他が子の追加を止めない点は H2 と同じだった。
- 上限のヒントについて: Hibernate は問い合わせの間だけ `lock_timeout` を上書きし、終わると接続の元の値へ戻す。接続の設定を 2 秒にしたとき、次のどの後でも 2 秒のままだった: ヒント付きの排他の後、SKIP LOCKED の後、ヒント付きの排他が待ち切れて失敗した後の同じ接続の次のトランザクション。
- `lock_timeout` の渡し方は、試しでは接続の URL に `options=-c lock_timeout=2000` を付けた。アプリでは Hikari の `connection-init-sql`（`SET lock_timeout = '2s'`）か、URL の `options` のどちらかで、接続ごとに設定する形になる。
- 設定が無いとき、問い合わせの上限（JDBC の query timeout）の打ち切りは 57014 で、例外が `org.hibernate.QueryTimeoutException` になるため、`isLockFailure` は偽になる（`jakarta.persistence.QueryTimeoutException` だけを見ているため）。

## 4. 確かめた事実: 失敗の後のトランザクション

- PostgreSQL では、トランザクションの中で SQL が失敗すると、その後の SQL がすべて失敗する（25P02）。違反だけでなく、排他の待ち切れでも同じ。Hibernate はどちらでも巻き戻しの印を付ける（H2 は待ち切れでは印を付けず、次の SQL も通る）。
- `src/main` の内部DB に触れる例外の catch（`targetdb` と `businessdata` の対象DB の SQL を除く）を洗い出し、**失敗の後に同じトランザクションで SQL を続ける経路は無い** ことをコードで確かめた（PostgreSQL の上で動かしてはいない）。

| 経路 | 失敗を受けた後 |
|---|---|
| `RoleStore`・`GroupStore` と、その呼び出し元のサービス（`RoleAdminService`・`RoleAssignmentService`・`WorkRoleService`・`RoleTransferService`・`GroupAdminService`・`GroupMembershipQueryImpl`） | 結果を返すと、その場で処理を止める。`inFirst` が巻き戻しの印を付け、失敗の監査は別のトランザクションで書く |
| `UserAdminService`（止める・戻す・管理者の印・失敗回数を戻す） | Busy のときは巻き戻しの印を付け、DB に触れずに返す（クラスの説明に決まりとして書かれている） |
| `UserAccountService.createUser` のメールアドレスの重なり | 呼び出し元（`RegistrationService`）は巻き戻しの印を付けて返すだけ。`InitialAdminInitializer` はログを出して終わる |
| ログインの排他（`LoginAttemptStateRepository.lockForUpdate`）・招待の排他 | 例外のまま投げ、トランザクションごと巻き戻る |
| ログインの状態の行を作る所（`LoginService.createRow`）・招待の再試行・後始末のジョブ・DSL の管理 | トランザクションの外で catch し、次の処理は新しいトランザクションで行う |
| 監査の記録（`AuditEventListener`） | 確定の後（AFTER_COMMIT）に、別のトランザクション（`REQUIRES_NEW`）で書く |
| ヘルスチェック（`TimeBoundedDbHealthIndicator`） | 自分で接続を借りて `SELECT 1` を1回流すだけ |

## 5. PostgreSQL 対応で要る手直し（ここまでで見えたもの）

### 5.1 試しで要ると確かめたもの

1. 接続ごとに `lock_timeout` を 2 秒にする（3節）。これが無いと、次の待ちが切れない。
   - 利用者を止める操作のリフレッシュトークンの無効化（`UserAdminService.revokeAllRefreshTokens`。コメントに「H2 の既定、約 2 秒」で切れる前提と書かれている）
   - ロール・グループの名前の重なりの待ち（確定前の同じ名前の鍵への追加）
2. PostgreSQL の DDL では、外部キーから `ON DELETE RESTRICT` を外す（指定なし＝NO ACTION にする）か、区分の部品で 23001 を扱う（2節）。NO ACTION なら今の区分の部品のまま当たる。

### 5.2 前の見立てで挙げ、まだ確かめていないもの

- 移行（Flyway）: DB ごとの置き場（`db/migration/{vendor}` など）に分ける形が候補。H2 の移行は V1〜V14 の14本。PostgreSQL でそのまま通りそうなもの: `GENERATED BY DEFAULT AS IDENTITY`・`TIMESTAMP WITH TIME ZONE`・`UUID`・`BOOLEAN`・CHECK。書き換えが要りそうなもの:
  - `BINARY LARGE OBJECT`（DSL の本文、最大 10MB）と `BINARY(32)`（トークンのハッシュ）→ `bytea`
  - 招待中のメールアドレスの一意（V8 の生成列 `pending_email` ＋ 一意の制約 `uk_invitations_pending_email`）→ 条件つきの一意の索引に置き換えられる。名前を同じにすれば `isPendingEmailViolation` が当たる見込み（未確認）
- H2 専用の SQL:
  - `MERGE ... KEY` の2か所: 作業ロールの保存（`RoleStore` の `MERGE_SELECTION`）と DSL のプレビューの置き場（`DslPreviewRepository` の `PLACE_SQL`）。書き換えが要る
  - `MERGE INTO ... USING (VALUES ...)`: ログインの状態の行を作る所（`LoginAttemptStateRepository.createIfAbsent`）。標準の書き方で PostgreSQL 15 以降でも動く見込み（未確認）
  - `INSERT ... SELECT` の中の `CAST(? AS UUID)`・`CAST(? AS TIMESTAMP WITH TIME ZONE)`（`DslAppliedRevisionRepository`）
- H2 だけの運用: ファイルの詰め直し（`DEFRAG_ALWAYS=TRUE` と `docker/hikari-pool.sh` の compact）・ファイルの複写によるバックアップと戻し・`-Dh2.compactThreads=1` は、PostgreSQL では別の手順になる。
- テストと検査: `team.md` の「内部DB を使うテストは組み込みの H2 で行う」の決まりの書き換え。内部DB の結合テストを PostgreSQL でも流すと、`verify` と CI の時間が増える（CI の上限の目安は約 42 分）。

### 5.3 確かめていないこと

- Spring Data のリポジトリを通った経路の例外の連なり。Spring が `CannotAcquireLockException` などに包み直しても、原因の連なりに `LockTimeoutException` が残るので判定は変わらない見込み。
- `lock_timeout` の効く範囲。接続のすべての文に効くので、起動時の Flyway の移行も 2 秒で切れる。同じ DB を他のものと共有するときの影響も見ていない。
- 接続をまとめる中継（PgBouncer の transaction モードなど）を挟むと、接続ごとの設定が当てにならない。今の想定（アプリから DB へ直接つなぐ）では関係しない。
- 時刻の扱い（`TIMESTAMP WITH TIME ZONE` の読み書きと、表示のタイムゾーンの変換）を PostgreSQL で流した結果。
- 排他の後に数える設計（最後の管理者を無くさない判定など）を PostgreSQL の READ COMMITTED で流した結果。分離レベルは H2 と同じだが、結合テストで確かめる。

## 6. 参考: MySQL・MariaDB で分かった差（後の Intent 向け）

- 違反の区分: 一意の制約・外部キーとも SQLState が 23000 にまとまり、今の区分の部品ではすべて UNEXPECTED になる。Hibernate の `ConstraintViolationException.getKind()`（UNIQUE・FOREIGN_KEY）と制約の名前は、4つの DB すべてで取れたので、区分を `getKind()` で見る形にすれば DB の差をまとめて吸収できる見込み。
- 主キーの名前: MySQL は `表名.PRIMARY`、MariaDB は `PRIMARY` だけで、エラーの文にも表の名前が入らない。表の名前で見分ける今のやり方は MariaDB では使えない。
- 排他の上限のヒント: MariaDB は `FOR UPDATE WAIT 3` で 3 秒で切れる。**MySQL はヒントが効かず、`innodb_lock_wait_timeout` の既定 50 秒まで待つ**（50.1 秒を測った）。
- 書き込みの待ち: 両方とも既定 50 秒。親の行に排他をかけている間の子の追加は、両方とも **待つ**（H2・PostgreSQL は待たない）。
- 既定の分離レベルは REPEATABLE READ。
- DDL: `permission_settings` の主キー（`VARCHAR(256)` ×3）は両方とも作れない（エラー 1071。上限 3,072 バイト）。MySQL は表の名前 `groups` を引用符で囲まないと作れない（予約語）。
- 照合順序: 既定は大文字と小文字を区別せず、MySQL 8 の既定（`utf8mb4_0900_ai_ci`）はアクセントも区別しない。メールアドレスや名前の鍵の一意の判定が H2 と変わるので、明示が要る（試しでは確かめていない）。
