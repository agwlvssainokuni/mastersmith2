# Reliability Design — U3 利用者の管理の API（u3-user-admin-api）

U3 の信頼性の設計です。承認済みの `construction/u3-user-admin-api/nfr-requirements/reliability-requirements.md`（NFR4.1〜NFR4.5・NFR6.2・NFR6.3・NFR9.4・NFR9.5）と、`performance-requirements.md` の NFR10.1・NFR10.2 を満たす作りを決めます。配備先は当面、開発者の PC 上のコンテナ1台で、可用性の数値の目標（SLO）は置きません（`observability-design.md` 6節）。

出典の略号: NFR はこの単位の NFR 要件の枝番、BR は `construction/u3-user-admin-api/functional-design/rules.md`、FS は同じ段の `functional-spec.md`、Q1〜Q3 と「要点 n」はこの段の `nfr-design-questions.md`、C1〜C8 は `inception/contract-design/contract-summary.md`、TM は `aidlc/spaces/default/memory/team.md`、PM は `aidlc/spaces/default/memory/project.md`。

## 1. 試しのコードで確かめたこと（Q1 A）

成果物を書く前に、本番のコードとは別の捨ての試しのコードで、BR3.1 の根拠と、上限切れの後の巻き戻し、外部キーの待ち、1,000 名の一覧を確かめました。試しのコードはリポジトリの外の一時の場所で動かし、終わった後に消しました。リポジトリの中は読むだけで、書き換えていません。この節が、その結果の正の記録です。

### 1.1 日時と使ったもの

| 項目 | 値 |
|---|---|
| 日時 | 2026-10-02（午前 1 時台、開発者の PC） |
| H2 | 2.4.240（`backend/gradle.lockfile` と同じ版） |
| Hibernate | 7.4.5.Final（lockfile と同じ版）。方言は自動判定で `H2Dialect` |
| Spring | spring-orm・spring-tx 7.0.9（lockfile と同じ版）。`JpaTransactionManager`・`HibernateJpaDialect`・`TransactionTemplate` |
| JDK | Temurin 25.0.4。`-Dh2.compactThreads=1` を付けた（PM の Tech Stack） |
| 接続 | `jdbc:h2:file:<一時の場所>/mastersmith;DEFRAG_ALWAYS=TRUE`（`backend/src/main/resources/application.yaml` の既定と同じ形。MODE の指定は本番にも無い）。分離レベルは H2 の既定の READ COMMITTED（Hibernate の起動のログで確かめた） |
| 表 | 本番の移行 `backend/src/main/resources/db/migration/V1〜V8` をそのまま当て、U1 の V9 の見込みとして `users.suspended BOOLEAN DEFAULT FALSE NOT NULL` を足した |
| 排他の待ちの上限 | JDBC の確かめは `SET LOCK_TIMEOUT 3000`。Hibernate の確かめは `jakarta.persistence.lock.timeout` = 3000（既存の `LoginAttemptStateRepository` と同じ値） |
| 待ちの確かめ方 | 2つ目の接続が排他を待っていることは、`INFORMATION_SCHEMA.SESSIONS` の `BLOCKER_ID` が入るまで待って確かめた（`sleep` の長さに頼らない）。行が排他されているかは、別の接続から上限 50 ミリ秒の `FOR UPDATE` を当て、誤りの番号 50200 になるかで確かめた |

### 1.2 結果の一覧

| # | 確かめたこと | 結果 | 設計への意味 |
|---|---|---|---|
| 1 | 待った後の数え（BR3.1 の根拠） | 合う | BR3.1 の作りのまま。切り替え先（固定の1行の排他）は要らない（2節） |
| 2 | 行を取る順（主キーの順） | 合う | 利用者 ID の昇順に1行ずつ取る。行き詰まらない前提が成り立つ（4節） |
| 3 | 外部キーの待ち | 合う（待たない） | 止める操作とログインのトークンの追記は待ち合わない（4節） |
| 4 | 上限切れの後の巻き戻し | 合う（明示の巻き戻しの印があれば 500 にならない）。印が無いと危ないことも確かめた | どの口の形でも明示の印を必ず付ける（5節）。例外の連なりに行の値が入る（`security-design.md` 7節） |
| 5 | 1,000 名の一覧 | 合う（どの場面も p95 1 ミリ秒未満） | 索引は足さない（`performance-design.md` 2節） |

### 1.3 確かめ 1: 待った後の数え

管理者 1・2・3、管理者でない 4・5・6 を入れました。接続 A が `SELECT user_id FROM users WHERE admin_flag = TRUE OR user_id = 4 ORDER BY user_id FOR UPDATE` で排他し、接続 B が同じ形の問い合わせで待ちます。B が待ちに入ったことを確かめてから、A が状態を変えて確定しました。B は排他を得た後、別の問い合わせ `SELECT user_id FROM users WHERE admin_flag = TRUE AND suspended = FALSE` で有効な管理者を数えました。

| 場合 | A の変更 | B の FOR UPDATE で排他した行 | B の排他の後の有効な管理者 |
|---|---|---|---|
| 1a | 利用者 2 の印を外す | 1・3・4（2 が外れた） | 1・3（A の確定を含む） |
| 1b | 利用者 5 に印を付ける（B の対象は 6） | 1・2・3・6（5 は排他されない） | 1・2・3・5（5 は数えに入る） |
| 1c | 利用者 3 を止める | 1・2・3・4（3 は印があるので残る） | 1・2（止めた 3 は数えない） |

どの場合も B は A の確定まで待ちました（B の所要は約 210 ミリ秒で、A が変更してから確定するまでの間隔 200 ミリ秒とほぼ同じ）。

- 待った後の `FOR UPDATE` は、排他を得た行の最新の値で条件を評価し直す。印が外れた行は結果から外れる（1a）。
- 待つ間に新しく印が付いた行は排他されないが、排他の後の別の問い合わせには入る（1b）。BR3.1 の根拠 (4) のとおり。
- 排他の後の別の問い合わせは、待つ間に確定した変更を含めて数える（1a・1b・1c）。BR3.1 の根拠 (3) のとおり。

### 1.4 確かめ 2: 行を取る順

利用者 ID を 9・3・7・1・5・2・8・4・6 の順に入れ（登録した日時は ID と逆の向き）、奇数を管理者にしました。対象は 4 です。

- `EXPLAIN` の結果は、索引なしでも `users (created_at, user_id)` の索引ありでも同じで、主キーの索引（`PRIMARY_KEY_BFE`）を走査し、`ORDER BY` は並べ替えなし（`index sorted`）だった。

```text
SELECT "USER_ID" FROM "PUBLIC"."USERS" /* PUBLIC.PRIMARY_KEY_BFE */
WHERE ("ADMIN_FLAG" = TRUE) OR ("USER_ID" = CAST(4 AS BIGINT))
ORDER BY 1 FOR UPDATE /* index sorted */
```

- 実際の排他の順: 別の接続 C が利用者 5 を先に排他し、B が上の問い合わせで 5 を待っている間に各行を調べた。1・3・4 は排他されていて、7・9 は排他されていなかった。C の確定の後の B の結果は 1・3・4・5・7・9。
- 入れた順や登録した日時の順ではなく、利用者 ID の昇順に1行ずつ排他し、途中の行で待つと、それより大きい ID の行はまだ取っていない。

### 1.5 確かめ 3: 外部キーの待ち

接続 A が利用者 4 の行を（管理者の行とともに）`FOR UPDATE` で排他したまま、接続 B が `refresh_tokens` に `user_id = 4` の行を追記して確定しました（ログインの形。B の上限 3000 ミリ秒）。

| 場合 | A の状態（どれも未確定） | B の追記 |
|---|---|---|
| 3a | 利用者 4 の行を排他しただけ | 待たずに通った（1 ミリ秒） |
| 3b | 排他に加えて `suspended` を更新 | 待たずに通った（0 ミリ秒） |
| 3c | 排他・`suspended` の更新・`refresh_tokens` のまとめての無効化 | 待たずに通った（0 ミリ秒） |

逆向き（3d）: B（トークンの更新の形）が利用者 4 のトークンの行を更新して持っている間、B は利用者 4 の行を排他していなかった。A の止める操作（排他・停止の更新の後のまとめての無効化）は B のトークンの行を待ち、B の確定の後に進んだ（3 行を無効化、所要 13 ミリ秒）。

- H2 の外部キーの確かめは親の行の排他を待たない。待つ向きは「止める操作がトークンの行を待つ」の一方だけで、行き詰まらない（BR3.4・BR4.3 の見込みどおり）。
- 3c で B が追記したトークンは、A のまとめての無効化の後に確定したため無効化されない。これは機能設計で塞がないと決めた隙（BR4.3 の M8 B）と同じもの。

### 1.6 確かめ 4: 上限切れの後の巻き戻し

別の接続が利用者 1 の行を排他したまま、Hibernate の問い合わせ（`select u.id from UserRow u where u.admin = true or u.id = :t order by u.id`、`PESSIMISTIC_WRITE`、上限 3000）を `TransactionTemplate` の中で流しました。上限切れの前に同じトランザクションで利用者 4 の氏名を書き換えておき、巻き戻ったかを見ました。

- Hibernate が出した SQL は `... order by ur1_0.user_id for update wait 3`（上限を秒にして `WAIT` で付ける）。ID だけの問い合わせにも排他を付けられた。
- 待った時間は 3004〜3171 ミリ秒（上限どおり）。
- 例外の連なり: `jakarta.persistence.LockTimeoutException` ← `org.hibernate.exception.LockTimeoutException` ← `org.h2.jdbc.JdbcSQLTimeoutException`（誤りの番号 50200、SQLState HYT00） ← `org.h2.mvstore.MVStoreException`。Spring の変換（`HibernateJpaDialect`）の後は `org.springframework.dao.CannotAcquireLockException`（`PessimisticLockingFailureException` の系統）。
- H2 の上限切れは、その文だけを取り消し、トランザクションは続く（上限切れの後に同じトランザクションで、書き換えた氏名を読めた）。

| 形 | Hibernate の巻き戻しの印 | 外側の扱い | 結果 |
|---|---|---|---|
| 4-1 EntityManager を直接呼ぶ（`LoginAttemptStateRepository` と同じ形） | 付かない | `status.setRollbackOnly()` を付けて返す | 例外なしで巻き戻った |
| 4-2 参加する内側のトランザクション（Spring Data のリポジトリの口と同じ）の中で例外 | 付く（全体の印） | 印を付けずに返す | `UnexpectedRollbackException`（500 になる形） |
| 4-3 4-2 と同じ | 付く（全体の印） | `status.setRollbackOnly()` を付けて返す（BR3.5 の形） | 例外なしで巻き戻った |
| 4-4 EntityManager を直接呼ぶ | 付かない | 印を付けずに返す | 例外なしで確定した（上限切れの前の書き換えが残った） |

- BR3.5 の形（業務処理が `status.setRollbackOnly()` を明示的に付けてから返す）なら、内側の口が全体の巻き戻しの印を付けていても付けていなくても、例外なしで巻き戻り、500 にならない（4-1・4-3）。
- 4-2 は、印を付け忘れると 500 になることを示す。4-4 は、EntityManager の直接の口で印を付け忘れると、上限切れの前に書いた変更が黙って確定することを示す。どの口の形でも明示の印を必ず付ける根拠になる（5節）。
- ログに出るもの: Hibernate の `SqlExceptionHelper` は WARN で、誤りの番号・SQLState と、`?` のままの SQL の文だけを出す（値は出ない）。一方、例外の連なりの最後の `MVStoreException` の文には、排他されていた行の全部の列の値（メールアドレス・パスワードのハッシュ値・氏名など）が入る。扱いは `security-design.md` 7節。

### 1.7 確かめ 5: 1,000 名の一覧

結果と数値は `performance-design.md` 2節・`security-design.md` 6節に写しました。どの場面も DB の中の時間は p95 1 ミリ秒未満で、索引は要りません。

## 2. 最後の有効な管理者の保護（NFR4.1・NFR4.5、BR3.1・BR3.2）

### 2.1 作り

- `user.repository` に、管理者の行と対象の行を排他する問い合わせを1つ置く。形は確かめ 1・2・4 と同じ `select u.id from User u where u.admin = true or u.id = :target order by u.id`（`PESSIMISTIC_WRITE`、上限 3000 ミリ秒）。戻すのは利用者 ID だけにし、エンティティを持続の文脈に載せない。
- 排他の後の「対象の要約」と「有効な管理者の ID の集合」は、別の問い合わせで値（投影）として読む（BR3.1）。エンティティで読み直すと、Hibernate の持続の文脈に残った古い状態を返しうるため。確かめ 1 のとおり、この別の問い合わせは待つ間に確定した変更を含めて数える。
- 問い合わせを実行するメソッドの置き場と、上限切れを受ける場所は 5.2 のとおり（`security-design.md` 7節の TraceAspect の理由で、`repository` の層のメソッドの本体の中で受ける）。
- 印を付ける・外す・止めるの3つが同じ口を使い、同じ順（利用者 ID の昇順）で取るため、互いの行を待ち合っても行き詰まらない（確かめ 2）。

### 2.2 BR3.1 の根拠と切り替え先（NFR4.5）

確かめ 1 で、BR3.1 の根拠 (3)（排他の後に数え直すと先の操作の確定の後の値で判定する）と (4)（待つ間に新しく印が付いた行は排他から漏れるが数えには入る）が H2 2.4.240 と Hibernate 7.4.5 の上で成り立ちました。そのため、BR3.1 の切り替え先（固定の1行を排他して、印・停止を変える3つの操作を1つずつ通す）には切り替えません。表と移行は増えません。

確かめ 1 は試しのコードのため、同じ観点を本番に残る結合テストとして B4 で確かめます（Q1 A）。

| 確かめ | 結合テストの形（細部はコード生成の計画） |
|---|---|
| 互いの印を外す・一方が外し他方が止める・互いに止める（AC2.1.6・AC2.1.12・AC3.1.5） | 待ち合わせの口（6節）で1つ目の操作を数える直前で止め、2つ目の操作が排他の待ちに入ったことを確かめてから1つ目を進める。有効な管理者がちょうど1人、監査が成功1行・失敗1行（LAST_ACTIVE_ADMIN） |
| 待つ間に新しく印が付いた行が数えに入る（確かめ 1b の形） | 同じ待ち合わせで、1つ目を印を付ける操作にする |
| 操作した人がロック中・停止中の管理者（AC2.1.11・AC3.1.9） | 業務処理の層を直接呼ぶテスト（FS の 2.10） |

B4 のテストが通らなかったときは、目標を緩めず、BR3.1 の切り替え先に移るかを依頼者に諮ります（NFR4.5）。切り替えても NFR5.4 の測り方は変えません。

## 3. 失敗回数を戻す操作とログインの重なり（NFR4.2、BR3.4）

- 失敗回数を戻す操作は、対象の利用者の行を排他なしで読み（対象の有無）、ロックの状態の行だけを、ログインの判定と同じ `PESSIMISTIC_WRITE`・上限 3000 ミリ秒で排他する（FS の 2.7）。
- 2段の口（BR4.5）の1段目が排他して Ready・NothingToReset・Busy を返し、2段目が同じトランザクションで明示の更新1回を行う。行を作らない。
- どちらが先に排他を取っても、結果は2つを順に行ったどちらかに一致する（FS の 2.9）。待ち合わせの口（6節）の2つ目を、ロックの状態の行を排他した直後に置き、失敗回数を戻す操作とログインの判定の両方から呼ぶ。時刻は注入した `Clock`（テストは既存の `MutableClock`）で動かし、`sleep` と実時刻に頼らない。

## 4. 行き詰まりを作らない（NFR4.3、BR3.4）

| 決まり | 作り | 根拠 |
|---|---|---|
| 管理者の行は利用者 ID の小さい順に取る | 2.1 の1つの問い合わせ。H2 は主キーの走査の順に1行ずつ排他する | 確かめ 2 |
| どの操作も利用者の行とロックの状態の行を同時に排他しない | 印・停止の3つの操作は利用者の行だけ、停止を解くは対象の行だけ、失敗回数を戻すはロックの状態の行だけ | BR3.3・BR3.4 |
| 止める操作とトークンの行の待ちは一方向 | 止める操作のまとめての無効化はトークンの行を待つことがあるが、トークンの側は利用者の行を排他しない。ログインのトークンの追記は外部キーで利用者の行を待たない | 確かめ 3 |
| 排他の問い合わせの形を変えるときの確かめ | 条件を足すなど問い合わせの形を変えるときは、`EXPLAIN` で主キーの走査のまま（`PRIMARY_KEY` と `index sorted`）かを確かめ直す。結合テストの1件で、実行計画の文に主キーの索引の名前が入ることを確かめてもよい（入れるかはコード生成の計画） | 確かめ 2 の設計への意味 |

H2 が行き詰まりを見つけたとき（誤りの番号 40001）は、上の作りでは起きない見込みです。起きたときは上限切れと同じく Busy として 409 にし（5節）、アプリのログの WARN に例外のクラスの名前を出して気づけるようにします（`observability-design.md` 3節）。

## 5. 上限切れの巻き戻し（NFR4.3・NFR4.4、BR3.5）

### 5.1 決まり

1. 排他の待ちの上限は既存と同じ 3000 ミリ秒のまま変えない（NFR4.4）。Hibernate は `for update wait 3` として出す（確かめ 4）。
2. 上限切れ（と行き詰まり）の例外は、問い合わせを実行するメソッドの本体の中で受け、例外のまま外へ出さない（5.2）。
3. 業務処理（`useradmin.service`）は、口が Busy を返したら、どの口の形でも必ず `status.setRollbackOnly()` を先に付け、それより後に DB を読み書きせずに結果 Busy を返す（確かめ 4 の 4-1・4-3）。口が Spring Data か EntityManager の直接の呼び出しかで扱いを分けない。
4. 409 USER_ADMIN_BUSY は状態を変えず、監査に残さない（BR3.5・BR6.4）。

```text
// useradmin.service の形（説明のための擬似コード）
return transactionTemplate.execute(status -> {
    AdminRowsLock lock = userAccount.lockAdminRowsInIdOrder(targetUserId);
    if (lock instanceof AdminRowsLock.Busy) {
        status.setRollbackOnly();      // 口の形にかかわらず必ず先に付ける
        return OperationResult.busy(); // この後 DB に触れない
    }
    barrier.beforeCount(operation, targetUserId);
    ... // 判定・確かめ直し・書き換え・監査の出来事
});
```

### 5.2 上限切れを受ける場所

BR3.5 は「排他の口（`user.service`・`auth.service`）のメソッドの本体の中で受けて Busy に変える」としています。この段では、受ける場所を1段下げ、問い合わせを実行する `repository` の層のメソッドの本体の中で受けて、Busy を表す値を返す形にします。

- 理由: `TraceAspect` は `repository` の層のメソッドも追跡し、TRACE のときに、メソッドから出た例外を文字列とスタックトレース（既定で出す設定）で出す（`backend/src/main/resources/application.yaml` の `mastersmith.trace`）。例外が `repository` のメソッドから出ると、`service` の層で受けても、その前に TRACE のログへ例外の連なり（排他されていた行の値を含む）が出る。詳しくは `security-design.md` 7節。
- 形: 排他の問い合わせは、EntityManager を直接使う `repository` のクラスのメソッドに置き（既存の `LoginAttemptStateRepository` と同じ形）、メソッドの本体の中で JPA の `LockTimeoutException`・`PessimisticLockException` を受ける。Spring Data のインターフェースの問い合わせ（`@Lock` と `@Query`）は、例外が代理（プロキシ）の外へ出るため、排他の問い合わせには使わない。
- `user.service`・`auth.service` の口は、repository が返す値を C8 の結果の型（AdminRowsLock・UserRowLock・LoginFailureResetPreparation の Busy）に写すだけにする。トランザクションの境界（プロキシ）を例外が越えない、という BR3.5 の目的は変わらない。
- 既存の `LoginAttemptStateRepository#lockForUpdate`（ログインの判定が使う）は変えない。失敗回数を戻す操作の1段目には、同じ行を同じ条件で排他し、上限切れをメソッドの中で受ける別のメソッド（例 `tryLockForUpdate`）を足す。この差は 11節の ND-1。

### 5.3 B4 の結合テストで確かめること（Q1 A）

| 確かめ | 合否 |
|---|---|
| 5つの操作のそれぞれで、別の接続か待ち合わせの口で行を持ち続けて上限切れを起こす（AC4.1.11） | 409 USER_ADMIN_BUSY、500（UnexpectedRollbackException）にならない、状態が変わらない、監査の行が増えない |
| 上限切れの前に書いたものが残らない（確かめ 4 の 4-1・4-3 の形） | テストの中の `TransactionTemplate` で先に1行を書き換えてから、`user.service` の排他の口と `auth.service` の1段目の口をそれぞれ呼び、Busy を受けて `setRollbackOnly()` で返す。書き換えが残らず、例外が出ない |
| 上限切れの時間 | 待った時間がおよそ 3 秒（3000 ミリ秒以上）で、上限を変えていない |
| TRACE を有効にした上限切れ | アプリのログに排他されていた行の値が出ない（`security-design.md` 7節、`UserAdminSecretLeakIT`） |

5つの操作の流れでは、排他の前に書き込みはありません（FS の 2.3〜2.7。失敗回数を戻す操作の1段目の前は、利用者の行を読むだけ）。それでも 2つ目の確かめを置くのは、後で流れを変えたときに 4-4 の形（黙って確定する）に戻らないようにするためです。排他の前に書き込みが無いことは、コード生成の計画でも流れを確かめて書きます。

## 6. 待ち合わせの口（BR3.6、NFR4.4）

| 口 | 置き場 | 呼ぶ場所 | 引数 |
|---|---|---|---|
| 例 `UserAdminBarrier` | `useradmin.service` | 印を付ける・外す・止めるの、排他の後・有効な管理者を数える直前 | 操作の区分と対象の利用者 ID |
| 例 `LoginAttemptBarrier` | `auth.service` | ロックの状態の行を排他した直後。失敗回数を戻す操作の1段目と、`LoginService` の実在の利用者の行の排他の直後（ダミーの行では呼ばない） | 利用者 ID |

- 既存の `InvitationBarrier`・`PasswordChangeBarrier` と同じく、インターフェースと何もしない既定の部品（`NoOp...`）を本番に置く。本番の流れと順を変えない。
- 引数は ID と区分だけで、`TraceAspect` の TRACE に個人に関する値は出ない。
- テストの部品は `useradmin/testsupport`・`auth/testsupport` に置き、止める時間は 3000 ミリ秒より短くする（NFR4.4）。2つ目の操作が排他の待ちに入ったことは、試しのコードと同じく `INFORMATION_SCHEMA.SESSIONS` の `BLOCKER_ID` を上限の時間つきで見る形を候補とする（U1 で `auth/testsupport` に移す手伝い、U1 の `logical-components.md` 6節）。決めるのはコード生成の計画。

## 7. トランザクションと監査（NFR9.4・NFR9.5）

- 5つの操作は `useradmin.service` の `TransactionTemplate` で1つのトランザクションにする。氏名と言語の変更も1つのトランザクションで、排他はしない（BR5.2）。
- 止める操作は、C1 の `setSuspended(対象, true)` と `revokeAllRefreshTokens(対象)` を同じトランザクションで呼ぶ。巻き戻れば両方が戻る（BR4.3）。
- C1 の約束（U1 の `logical-components.md` 3節）を守る: `setSuspended`・`revokeAllRefreshTokens` は呼ぶ前に未反映の変更を書き出し、呼んだ後に持続の文脈を空にする。U3 は排他の結果も要約も投影の値で持ち、エンティティを持たないため、口を呼んだ後に古いエンティティを書くことは起きない。止める操作で `setSuspended` の後に `revokeAllRefreshTokens` を呼ぶ順も、この約束の範囲。印の付け外しの C8 の `setAdmin` も、同じく停止の列の更新と同じ形（明示の更新の問い合わせ）にする。
- 業務の拒否（404・409、確かめ直しの 403）は書き込みをせずに確定させ、確定の後に失敗の監査を記録する（BR2.4・BR6.3）。巻き戻すのは BUSY と想定外の誤りだけ。
- 監査の出来事は確定の後に既存の `AuditEventListener`（AFTER_COMMIT）が受け、`AuditEventRecorder`（`REQUIRES_NEW`）が2本目の接続で記録する。書き込みの失敗は応答を変えず、アプリのログに ERROR（メールアドレス・氏名を含めない）。既存の `AuditWriteFailureIT` と同じ形で、成功と拒否の両方を確かめる（NFR9.4）。
- 止める操作の途中で失敗させて、停止とトークンの両方が戻ることを結合テストで確かめる（NFR9.5）。

## 8. 接続プールの確かめ（NFR6.2・NFR6.3）

接続の数の見積もりは `scalability-design.md` 2節です。確かめは performance-validation の k6 で行い、台本の用意と `k6 inspect` は build-and-test が受け持ちます。

| 場面 | 上限 | 流し方 | 合否 |
|---|---|---|---|
| NFR6.2 | 既定 30 | 5つの操作と一覧を合わせて同時 10 件 | 接続を借りる待ちの時間切れの累計が 0、500 が 0 件、流した5つの操作の件数と監査の行（5つの種類の SUCCESS）が一致。借りるまでの待ちの最大を記録 |
| NFR6.3 (A) | `MASTERSMITH_DB_MAXIMUM_POOL_SIZE=10` | 5つの操作だけを同時 5 件 | 待ちの時間切れの累計が 0、操作と監査の件数が一致 |
| NFR6.3 (B) | 同上 | 5つの操作だけを同時 10 件 | 2本目を借りる待ちが出る（待ちの最大が 0 より明らかに大きい、または時間切れの累計が 1 以上）。欠けた監査の件数と ERROR の件数を記録し、p95 と件数の一致には数えない |

- 値は使い捨てのアプリにだけ `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics` を渡し、`/actuator/metrics` の hikaricp の値（待ちの時間切れの累計と借りるまでの待ちの最大）で判断する。数秒ごとの使用中の数では判断しない（PM の Testing Posture）。
- 監査の件数は、アプリを止めて内部DB を読み取りで開いて数えてから片付ける。
- NFR 要件のレビューの R-07・R-08（上限 10 の場面の条件と BUSY の件数の扱い）は、performance-validation の台本で扱う（NFR 要件の承認の場の申し送り）。

## 9. 移行と後方互換（NFR10.1・NFR10.2）

| 要件 | 設計 | 根拠 |
|---|---|---|
| NFR10.1 | 表と列を変えず、Flyway の移行を足さない。監査の種類5つ（最長 `LOGIN_FAILURES_RESET` の 20 文字）と理由（最長 `LAST_ACTIVE_ADMIN` の 17 文字）は、既存の `event_type`・`failure_reason`（VARCHAR(32)、CHECK 制約なし）に値を足すだけ。1つ前の版のアプリは本番のコードで監査の行を書くだけで（`AuditEventRecorder` は `save` だけを呼ぶ。`findById`・`findAllByOrderByOccurredAtAsc` を呼ぶのはテストだけ）、新しい値の行を読まないため困らない。Hibernate の `validate` は列の値を確かめない | この段のコードの確認（Q の「決まっていること」） |
| NFR10.2 | 索引と列を足さない（Q2 A）。この要件は当てはまらない。将来 performance-validation で NFR5.1 に届かず索引を足すと決めたときは、V10 以降・前進のみ・1つ前の版が動く後方互換・移行は U3 が持つ、の決まりで足す | Q2 A、`performance-design.md` 2節 |

新しい値の長さが 32 文字以内であることは、コード生成のレビューで確かめます。

## 10. 障害のときのふるまい

| 障害 | ふるまい | 設計 |
|---|---|---|
| 排他の待ちの上限切れ（3000 ミリ秒） | 409 USER_ADMIN_BUSY、巻き戻し、監査なし。アプリのログに排他の口の WARN（例外のクラスの名前だけ）と、変換の境界の WARN（code） | 5節、`observability-design.md` 3節 |
| H2 の行き詰まりの検出（40001） | 起きない見込み。起きたら上限切れと同じ扱い | 4節 |
| 同時の操作で有効な管理者が 0 人になりかける | 後の操作を 409 USER_ADMIN_LAST_ADMIN で拒否、状態は変わらない | 2節 |
| 排他を待つ間に操作した人の印が外れた・止められた | 403 ACCESS_DENIED、状態は変わらない、監査に NOT_ADMIN | `security-design.md` 2節 |
| 監査の書き込みの失敗 | 元の応答のまま、アプリのログに ERROR | 7節 |
| 接続プールの待ちの時間切れ（5 秒） | 1本目なら既存の 500。2本目（確定の後の監査）なら応答は元のままで監査が欠ける（既知の制約） | 8節、`scalability-design.md` 2節 |
| 内部DB の障害 | 既存の 500、トランザクションは巻き戻る | 7節 |

## 11. 上流との差

| ID | 上流 | 上流の記載 | この段の設計 | 理由と扱い |
|---|---|---|---|---|
| ND-1 | BR3.5、FS の 2.2 の手順 5 | 上限切れの例外は、排他の口（`user.service`・`auth.service`）のメソッドの本体の中で受けて Busy に変える | 受ける場所を、問い合わせを実行する `repository` の層のメソッドの本体の中にする。排他の問い合わせは EntityManager を直接使うクラスに置き、Spring Data のインターフェースの `@Lock` の問い合わせは使わない。`service` の口は repository の値を Busy に写す。失敗回数を戻す操作には `LoginAttemptStateRepository` に上限切れを中で受ける別のメソッドを足し、既存の `lockForUpdate` は変えない | `TraceAspect` が `repository` の層から出た例外を TRACE で文字列とスタックトレースにして出すため（`security-design.md` 7節）。例外がトランザクションの境界を越えない・Busy で返す・明示の巻き戻しの印、という BR3.5 の中身は変えない。承認済みの機能設計は書き換えない。承認の場で確かめたい |
| ND-2 | NFR4.5、BR3.1 | 確かめが通らなければ固定の1行の排他に切り替える | 切り替えない | 確かめ 1・2 が通ったため（1節） |
| ND-3 | 質問の文書の要点 4 | 上限切れを Busy に変える所で、行き詰まりのときに例外のクラスの名前を WARN に出す | 上限切れのときも行き詰まりのときも、例外のクラスの名前だけを出し、例外そのもの（cause とスタックトレース）はログに渡さない | 確かめ 4 で、例外の連なりに行の値が入ると分かったため（`security-design.md` 7節） |
| ND-4 | Q1 A の確かめの範囲 | (1)・(3)・(4) を B4 の結合テストでも確かめる | 加えて、上限切れの前に書いたものが残らないこと（4-1・4-3 の形）と、TRACE を有効にした上限切れで行の値がログに出ないことを確かめる | 確かめ 4 の 4-4 と、ログに出るものの結果から足した。テストの数は増えるが、本番の流れは変えない |
