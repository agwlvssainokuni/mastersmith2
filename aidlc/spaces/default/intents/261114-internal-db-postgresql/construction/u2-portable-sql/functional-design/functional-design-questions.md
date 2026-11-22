# 機能設計の質問: u2-portable-sql（Intent 261114-internal-db-postgresql）

この単位（U2: H2 にしか通らない SQL を H2 と PostgreSQL の両方に通る書き方に直す）の機能設計で、依頼者に確かめる点だけを尋ねます。上流（要件定義・Domain Design・Contract Design・U1 の機能設計・`team.md`・`project.md`）で決まっている点は質問にしていません。

## 決まっていること

- DB の種類で分岐しない書き方にする（ADR-003）。repository の口（メソッドの形）は変えない（契約 C5）。H2 での結果は変えない（FR5.4）。普段の `verify` の H2 の結合テストの全件で確かめる（減らさない）。
- 大きなバイナリの 2 列の写し方（`@JdbcTypeCode(SqlTypes.LONG32VARBINARY)`）は、U1 のコード生成で直した（U1 の質問 Q1: A、依頼者の決定）。U2 はこの組を前提に、直した後の `validate` と往復を確かめる範囲に縮む。
- 接続のたびの `lock_timeout` 2 秒は U1 が持つ。U2 は待ちの上限の設定を足さない。

## 捨ての試しの結果（質問ではありません）

リポジトリの外で、H2 2.4.240・PostgreSQL 18.6（`TargetDbImages` と同じダイジェスト）・Hibernate 7.4.13 と、実際のアプリの部品（`MastersmithApplication` と各 Repository の Bean）を使って確かめた結果です（記録は設計の成果物と `aidlc/spaces/default/knowledge/aidlc-shared/study-internal-db-postgresql.md` に写します）。

- **書き直しが要るもの（PostgreSQL では構文誤り 42601）**: `UserRepository.savePreferences`（`FINAL TABLE (UPDATE ...)`）、`RoleStore` の作業ロールの保存（`MERGE INTO ... KEY`）、`DslPreviewRepository.place`（`MERGE INTO ... KEY`）。
- **書き直しが要らないもの**: `DslAppliedRevisionRepository.copyFromPreview`（`CAST(? AS ...)` つきの `INSERT ... SELECT`。両方でそのまま通る。10MB の SHA-256 一致）、`LoginAttemptStateRepository.createIfAbsent`・`RefreshTokenRepository`・`InvitationRepository` の削除（実際の Bean 経由で両方に通る。契約 C5 の見込みどおり）。
- **`savePreferences` の候補**: (a) 更新 `UPDATE` の後に、同じトランザクションの中で更新後の 2 列を `SELECT` する 2 文の形は両方で通る。(b) `UPDATE ... RETURNING` は H2 で通らない（42000）。(c) JDBC の生成キー（`prepareStatement(sql, new String[]{"user_id","display_time_zone"})` と `getGeneratedKeys()`）は、**1 文のまま H2・PostgreSQL の両方で更新後の値を返す**（列名は小文字。`Session.doReturningWork` で使う）。`CASE WHEN :change = TRUE THEN CAST(:zone AS VARCHAR(64)) ELSE display_time_zone END` は両方で通る。同じ利用者への同時の更新は、両方で行の排他で直列になり壊れない（失敗の記号だけ違う: H2 は `HYT00`/50200、PostgreSQL は `55P03`）。
- **`MERGE ... KEY` の候補**: 標準の `MERGE INTO t USING (VALUES (...)) AS s(...) ON ... WHEN MATCHED THEN UPDATE ... WHEN NOT MATCHED THEN INSERT ...` は両方で通る。ただし PostgreSQL では時刻の束縛に `CAST(? AS TIMESTAMP WITH TIME ZONE)` が必須（無いと 42804）。UUID・BIGINT・バイト列・文字列は `CAST` 不要で、バイト列に `CAST(? AS VARBINARY)` を付けると PostgreSQL が 42704。`executeUpdate` の戻り（行数）は同じ。10MB の本文も両方で通る。
- **同時の実行（行がまだ無い鍵へ 2 つのトランザクションが書く）の違い**:
  - H2 の今の `MERGE ... KEY` は、後の側が**成功して上書きする**（後勝ち）。
  - 標準の `MERGE` は、H2 でも PostgreSQL でも、後の側が先の側のコミットを待ってから **23505（一意の違反。制約名は H2 が `PK_WORK_ROLE_SELECTIONS` と大文字、PostgreSQL が `pk_work_role_selections`）で失敗する**。H2 の未確定の挿入への待ちは約 4 秒、PostgreSQL は約 2 秒（`lock_timeout`）で上限切れ（H2 は外側 `HYT00`/50200、PostgreSQL は `55P03`）。
  - 「`UPDATE` → 0 行なら `INSERT` → 23505 ならやり直して `UPDATE`」は、両方で同じ形で収束し、**最終は後の側の値（後勝ち）になる**。PostgreSQL は 23505 の後、同じトランザクションの次の文が `25P02`（中断）になるため、**セーブポイントを張って、23505 のときセーブポイントまで戻す**必要がある（H2 は中断しないが、同じ形で動く）。この形は、`INSERT` を Hibernate の例外変換に通さない JDBC（`Session.doWork` の中で `PreparedStatement` を使い、例外を doWork の中で受ける）で行うと両方で収束する。`createNativeQuery(...).executeUpdate()` で行い `PersistenceException` を受ける形は、Hibernate がトランザクションに rollback-only の印を付けるため、収束しない（両方）。
  - 上限切れ（H2 約 4 秒・PostgreSQL 約 2 秒）のときは、どの形でも収束せず上限切れの失敗として出る（その後、セーブポイントまで戻せばトランザクションは使い続けられる）。
- **失敗の分類の事実**: `RowLockFailures`（`common/persistence`）は、型（`LockTimeoutException`・`PessimisticLockException`・`QueryTimeoutException`）か、`SQLException` の誤りの番号 50200・40001、SQLState `HYT00`・`40001` で上限切れを判定する。PostgreSQL の `55P03` は、Hibernate の例外変換を通る経路（EntityManager のクエリ）なら型で当たるが、JDBC の中で受けた生の `PSQLException` は当たらない。`RoleStoreClassifier` は、23505 で文に `PK_WORK_ROLE_SELECTIONS`（大文字にして照合）があれば `SELECTION_KEY`（`Busy`）に分けるため、PostgreSQL の小文字の制約名でも当たる。
- **検索**（`UserRepository` の `ilike :pattern escape '\'`、`SearchText.likePattern()` が `Locale.ROOT` で小文字にして `\`・`%`・`_` をエスケープ）: 日本語・全角半角・カナ・`%`・`_`・`\`・`!`・連続空白・結合文字・ß・254 文字の長い入力など、36 人の見本と約 55 個の入力で、**H2 と PostgreSQL は同じ結果**。違いは Unicode の端の 3 種だけ（`İstanbul` が検索語 `i` に当たるか（PostgreSQL は当たる、H2 は当たらない）、語末シグマ `ς` と `σ` の同一視（H2 は同一視、PostgreSQL は別）、`ǅ` が `ǆ` に当たるか（PostgreSQL は当たる））。
- 既存のテスト: `WorkRoleSelectionMergeIT` は H2 の `MERGE ... KEY` に対し、保持が約 4 秒を超えたときの上限切れ（誤りの番号 50200）を確かめている。書き直した形ではこの期待は変わるため、書き直しの対象に入る。DSL のプレビューを置く経路の同時の実行を確かめるテストは今は無い（`DslConcurrencyIT` は適用の同時の流れ）。

## Q1. [実装の形] `savePreferences`（本人の表示の設定の保存）の書き直し方

今の 1 文（`FINAL TABLE`）は PostgreSQL に通りません。前の Intent（261102-timezone-ui-polish の U2）で「内部DB の文の数は変えない（NFR2.1.2）」と決めた経緯があります。

**推奨: A。** 理由: 前の決め（1 文のまま）を守れ、H2・PostgreSQL の両方で更新後の値を返すことを捨ての試しで確かめ済みです。JDBC の標準の機能（生成キー）で、DB の種類による分岐も要りません。B は構造が単純ですが、内部DB の文が 1 本増え、前の決めの変更になります。

A. JDBC の生成キーで 1 文のまま（`Session.doReturningWork` の中で `prepareStatement(sql, 列名の配列)` と `getGeneratedKeys()`。列名は小文字）
B. 更新（`UPDATE`）と読み直し（`SELECT`）の 2 文を同じトランザクションで流す（NFR2.1.2 の文の数が 1 から 2 になる）
X. Other (please specify)

[Answer]: A

## Q2. [並行] 作業ロールの保存とプレビューを置く操作の、同時の実行の結果

H2 の今の `MERGE ... KEY` は、同じ鍵へほぼ同時に書いた 2 つのうち、後の側が成功して上書きします（後勝ち）。DSL のプレビューは「後に確定した方が残る」（DSL 管理の単位の機能設計）と決めています。標準の `MERGE` に置き換えると、H2 でも PostgreSQL でも、後の側が 23505 で失敗します（同時の実行は管理者が 2 人同時にプレビューを置く・同じ利用者が作業ロールの保存を二度押す、のような稀な場合）。

**推奨: A。** 理由: 要件「H2 での結果は変えない（FR5.4）」と、プレビューの「後に確定した方が残る」を守れます。トランザクションは呼び出し元のまま（同じトランザクションの中でセーブポイントを使う）で、業務処理の層は変わりません。捨ての試しで、両方の DB で同じ最終の値になることを確かめ済みです。作りが複雑になる分は、セーブポイントの前後の組み立てを 1 つの部品に閉じ込め、単体テストと PostgreSQL の結合テスト（U3）で守ります。上限切れ（H2 約 4 秒・PostgreSQL 約 2 秒）は、今までどおり失敗として出ます（`RowLockFailures` に PostgreSQL の `55P03` を足して、JDBC の中でも上限切れとして分類されるようにします）。

A. 「更新 → 0 行なら挿入（セーブポイントを張る）→ 一意の違反なら戻して更新をやり直す」: 同時の実行は両方の DB で後勝ち。H2 の今の結果と同じ。挿入は JDBC（`Session.doWork`）で行う
B. 標準の `MERGE` に置き換える: 同時の実行は両方の DB で、後の側が一意の違反（`SELECTION_KEY`・プレビューの競合）の失敗になる。H2 の結果が変わる（後勝ち → 失敗）。作業ロールは今も `SELECTION_KEY` を `Busy` に分けているが、プレビューの競合の分類・応答は新しく決める必要がある
X. Other (please specify)

[Answer]: A

## Q3. [互換] 利用者の検索の Unicode の端の違い（`İ`・語末シグマ・`ǅ`）

日常の検索（日本語・英数字・全角半角・記号）は H2 と PostgreSQL で同じ結果です。違いは、特殊な Unicode の文字だけです（H2 は Java の大文字小文字の畳み込み、PostgreSQL はデータベースの照合 `en_US.utf8`）。

**推奨: A。** 理由: この製品の利用者の名前・メールアドレスで実際に起きにくい文字で、そろえるには、検索語の畳み込みを内部DB の種類ごとに合わせるか、列を別に持つ必要があり、ADR-003（種類で分岐しない）と、U2 の範囲に収まりません。

A. 差として受け入れ、`docs/internal-db.md` に既知の違いとして書く（U4 が書く）。コードは変えない
B. 検索語と列の両方をアプリ側で同じ形に畳み込む（列を持つ・移行を足すなど、U1 の移行の範囲を超える設計になる）
X. Other (please specify)

[Answer]: A

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md`（FR3.5・FR3.6・FR3.8・FR5.4）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/units-generation/unit-of-work.md`（U2）・`inception/contract-design/contract-summary.md`（C3・C5）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/functional-design/functional-spec.md`（E14）
- `aidlc/spaces/default/knowledge/aidlc-shared/study-internal-db-postgresql.md`
