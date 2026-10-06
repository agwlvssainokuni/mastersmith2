# 信頼性の設計 — U4 role

## 出典

- この単位の承認済みの NFR 要件 `construction/role/nfr-requirements/`（`reliability-requirements.md` の NFR3.1〜NFR3.8、`performance-requirements.md` の NFR2.3・NFR2.4）と、その読み直しの記録の R-05〜R-07・R-11
- この単位の承認済みの機能設計 `construction/role/functional-design/`（`functional-spec.md`・`rules.md` の BR8・BR9・BR10、`entities.md`）
- `contract-summary.md`（C4・C5・C7）、`components.md`（RoleManagement）
- この段の答え: `nfr-design-questions.md` の Q2・Q3・Q4（すべて A）とまとめの確認
- group の NFR 設計の `reliability-design.md`（捨ての試し T1〜T7）と、その読み直しの記録の R-02・R-04・R-05

## 1. 捨ての試しの結果（Q4: A、NFR3.7）

### 1.1 試しの形

- 置き場: スクラッチの置き場の下の一時の場所（`u4-trial`）。終わった後に消した。本物の作業フォルダでは git・gradlew・npm を使っていない。
- 版: H2 2.4.240・HikariCP 7.0.2・SnakeYAML 2.7・Jackson 3.1.7（`tools.jackson`）（`backend/gradle.lockfile` と同じ）、Java 25（Temurin 25.0.4）。
- JVM の設定: `-Xmx512m`（本番のヒープ約 1 GiB より小さい側で測った）、`-Dh2.compactThreads=1`、`-Duser.timezone=Asia/Tokyo`、`-ea`。
- 接続と問い合わせ: ファイルの H2 で `DEFRAG_ALWAYS=TRUE`、プールの上限 10・接続の待ち 5000 ms、各文に問い合わせの上限 10 秒。
- 作り: Spring Boot と Hibernate は使わず、HikariCP と JDBC で直接書いた。YAML の読み込みは、今の `dsl.parse` と同じ `LoaderOptions`（深さ 10・コレクションの別名 0・重複キーの拒否・タグの拒否）と、キーと値の変換の規則（`YamlTreeConverter` と同じ）を写した。
- 表: role の設計と同じ形にした。`roles`・`permission_settings`（主キーはロール・スキーマ名・テーブル名・カラム名）・`user_role_assignments`・`group_role_assignments`・`group_members`・`work_role_selections`。
- データは仮の値だけ（名前は `ROLE_000`・`TABLE_000`・`COLUMN_000_000_XX` など）。ロール 1,000 個、うち 26 個は1万カラムすべてに明示。悪い側の利用者は 100 のグループに属し、各グループにロールがある。作業ロールは1万カラムを明示したロール。
- 時間: 約 15 分で、上限の 2 時間の内。3つの範囲すべてを確かめた。

### 1.2 結果

| # | 項目 | 結果 | 設計への意味 |
|---|---|---|---|
| U1 | YAML のキーの型（機能設計の R-06） | キー `true`・`null`・`123`・`yes`・`~`・`0x1F`・`1e3`・`'007'`・`"TRUE"`・`2026-10-06` は、すべて書いたとおりの文字列のキーになった（変換の規則がキーの元の文字列を使うため）。<br>`1` と `'1'`、`true` と `'true'` は同じキーとして重複の誤りになった。`yes` と `true` は別のキー。<br>値は `NONE` が文字列、`yes`・`on` が真、`no` が偽、`'true'` が文字列、`~` が null になった | キーは型によらず文字列として読めるため、読み込みで型のキーを誤りにする手当ては要らない。書き出しでは、YAML で型や特別な意味を持ちうる名前（`true`・`null`・`~`・数・日付・記号を含む名前など）を二重引用符で囲み、往復を保つ（2.4）。<br>値の側は、`create`・`delete` に `yes`・`on`・`no` が真偽として入る。そのため、主権限の値は文字列の決めた語だけ、補助権限は真偽だけを受ける（文字列の `'true'` は誤り）、と区分の検証で決める |
| U2 | 別名 0 の扱い（R-05） | コレクションを指す別名は、SnakeYAML の上限の設定（0）で拒否された。スカラーを指す別名（`x: &a READ`・`y: *a`）は受け付けられ、節は増えない | NFR1.5 の「コレクションを指す別名 0」の読み方が、部品の動きと合う。境界のテストはコレクションの別名の 0 と 1 で書く |
| U3 | 上限に近い YAML の確かめ（NFR2.4） | 10,115,916 バイト（上限 10,485,760 の 96%）、ロール 26 個・26 万 2,626 の対象・展開後の節 54 万 1,065。<br>確かめ全体（読み込み・木を作る・今の設定を読んで比べる・指紋）は 1 回目 755 ms・2 回目 613 ms。読み込みだけで 265〜365 ms。<br>使ったヒープは約 230〜330 MB | 目標の 15 秒に十分届く。上限は 10 MiB のまま確定する（Q4 の止める条件に当たらない）。<br>使ったヒープの値は、アプリの土台を含まず、ごみを含む量か生きている量かも分けていない。本番のヒープ（約 1 GiB）への見通しは 2.6 |
| U4 | 上限に近い YAML の適用（NFR2.4・R-06） | 読み込み直し・26 ロールの行の排他・置き換える行の削除・1,000 件ずつのバッチの書き込み・確定で、1 回目 3,393 ms・2 回目 3,736 ms。<br>このうち削除は約 1,070〜1,120 ms（ロールごとの1文、最大約 50 ms）、確定は約 1,400〜1,540 ms。1文（バッチ1つ・削除1つ）の最大は 57 ms | 目標の 30 秒に十分届き、文の上限 10 秒（1文）にも大きく余裕がある。バッチは 1,000 件にする。<br>本番は Hibernate を通すため、この値より遅くなる。書き込みは `role/store` の中で JDBC のバッチ（Spring の JDBC の部品）で行い、エンティティを1件ずつ作らない（2.5） |
| U5 | `snapshotFor` と `resolve` の時間（NFR2.3） | 作業ロールの決定（4 回）＋1万行の読み取りは、300 回で p50 2 ms・p95 3 ms・最大 42 ms。<br>`resolve`（4 回＋祖先の行の1回）は p50 1 ms 未満・p95 1 ms・最大 8 ms | 予算の 300 ms に大きく余裕がある。キャッシュは要らない（ADR-006 の切り替え先は使わない）。本番は JPA の射影にすると増えうるため、写しの読み取りもエンティティにせず、行の値だけを読む（`performance-design.md`） |

### 1.3 試しの限り

- Spring Boot・Hibernate・`TraceAspect` を通していないため、本番の時間は試しより長くなる。それでも、どれも目標の 10 分の 1 未満だった。
- 上限ちょうどではなく 96% で測った。値はほぼ大きさに比例すると見て、上限ちょうどでも確かめ 1 秒未満・適用 4 秒程度の見通しとした。上限ちょうどの1回は Performance Validation の `roleTransferLarge` で測る。
- 同時の重なり・違反・上限切れの振る舞いは試していない。group の試し T1〜T7 を使う（2.1）。
- 測った YAML の形は、長い名前の 26 ロール × 1万カラムの1つだけ。展開後の節の上限（1,000,000）は、短い名前の形のほうが先に届き、節の数が多い分だけ読み込みが重くなる（読み直しの R-10）。節の数が上限に近い短い名前の形を、Build and Test（`RoleTransferLimitsApiIT` の上限ちょうどのファイルの生成の部品を使う）で1回測り、確かめと適用の時間を記録する。試し直しはしない。

## 2. 同時の重なりの守り（NFR3.1・NFR3.3・NFR3.4・NFR3.8）

### 2.1 group の試しを使う前提と待ちの上限（読み直しの R-07、承認の場の決定）

- 一意の鍵・主キーの待ちは H2 の既定の約 2 秒で切れる（group の T1〜T3）。行の排他はヒント 3,000 ms で切れる（T4）。外部キーの確かめは親の行の排他を待たない（T4 (ii)）。
- **決まった値**: ロールの行・グループの行の排他は 3 秒（問い合わせのヒント）。名前の鍵・割り当ての主キー・作業ロールの保存の主キーの待ちは約 2 秒（H2 の既定のまま。`SET LOCK_TIMEOUT` は使わない）。group と同じ値で、承認の場で依頼者が受け入れた。
- 承認済みの NFR3.3 の「3 秒」は、行の排他のヒントにだけ当たると記録する（6節の差）。
- テストの合否は経過の時間で決めない（4節）。そのため、上限切れの経路のテスト（#3・#9）は、約 2 秒で切れることを前提にしない形で書く（先の側を放さずに結果を待つ）。

### 2.2 排他の順番と待ち合わせの口

- ロールを変える操作（名前の変更・削除・権限の保存・消す・割り当てと外し）は、最初にロールの行を `PESSIMISTIC_WRITE`（ヒント 3,000 ms）で排他する。グループへの割り当てと外しは、続けて group の `lockForAssignment` でグループの行を排他する（ロール → グループ）。
- グループの削除はグループの行だけを排他し、role の問う口（`canDelete`・`assignedRoleCounts`）を読み取りで呼ぶ。そのため、待ちの輪はできない。
- import の適用は、置き換えるロールの行を ID の昇順に排他する。作るロールは名前の鍵の一意の制約が守る。
- ロールの作成は既存の行を変えないため排他を取らず、同じ名前の重なりは一意の制約が守る。作業ロールの切り替えも排他を取らない（承認済み）。保存の行は利用者の ID が主キーで、H2 の `MERGE INTO … KEY(user_id)` で書く。
- 待ち合わせの口 `RoleBarrier` は4つの点を持つ（本番は何もしない `NoOpRoleBarrier`、テストは `role/testsupport` の `@Primary` の部品）。

| 点 | 呼ぶ所 | 使う操作 |
|---|---|---|
| `beforeLock(op, roleId)` | 行の排他の直前 | 排他を取る操作すべて |
| `afterLock(op, roleId)` | 行の排他を取った直後 | 排他を取る操作すべて |
| `afterCheck(op, key)` | 業務の判定（重なり・割り当ての有無）の後、書き込みの前 | 作成・名前の変更・作業ロールの切り替え・割り当て |
| `afterWrite(op, key)` | 書き込みと flush の後、確定の前 | 作成・名前の変更・作業ロールの切り替え・割り当て・保存・import の適用 |

### 2.3 例外の区分と結果の型

`role/store`（`logical-components.md` の L2）が、排他と違反を起こしうる書き込み・flush を行い、例外を中で受けて結果の型に変える。

```java
// role/store の結果の型（説明用の断片）
sealed interface RoleStoreOutcome<T> {
    record Done<T>(T value) implements RoleStoreOutcome<T> {}
    record RoleMissing<T>() implements RoleStoreOutcome<T> {}      // 排他の読み取りで行が無い
    record NameTaken<T>() implements RoleStoreOutcome<T> {}        // uk_roles_name_key（23505）
    record AlreadyAssigned<T>() implements RoleStoreOutcome<T> {}  // 割り当ての主キー（23505）
    record Referenced<T>(Referent who) implements RoleStoreOutcome<T> {} // 外部キー（23503）
    record Busy<T>(String lockKind) implements RoleStoreOutcome<T> {}
}
```

- 区分の順は group の T7 と同じ。`RowLockFailures.isLockFailure` が真なら上限切れ。次に、SQLState 23505・23503 と制約の名前で違反を区分する。どちらでもなければ想定外とする。作業ロールの保存の主キーの違反（同じ利用者の初めての切り替えが2つ重なった場合）は `Busy("WORK_ROLE_SELECTION_KEY")` に区分し、`ROLE_BUSY` で返す。
- `Referenced` は、制約の名前から相手（`ROLE`・`USER`・`GROUP`）を持つ。業務処理が操作ごとに読み替える。
  - ロールの削除: `ROLE_IN_USE`。
  - 割り当ての追加: `USER_NOT_FOUND`・`GROUP_NOT_FOUND`・`ROLE_NOT_FOUND`（group の読み直しの R-04 を先に入れた形）。
- 想定外の例外は、値を含まない SQLState と制約の名前だけを持つ例外に包み直して投げる。元の連なりと文は持ち出さない（group の読み直しの R-06）。
- 上限切れは `RowLockFailures.warn` で WARN を1回出す（`observability-design.md` の 3節）。

### 2.4 業務処理の組み立てと巻き戻しの印（NFR3.4、読み直しの R-01）

- 巻き戻しの印は、`TransactionTemplate` の中で `status.setRollbackOnly()` を呼ぶ形で付ける（group の試し T5 で確かめた形）。`role/store` は印を付けない（`TransactionTemplate` の中では、宣言的なトランザクションの今の状態を読む仕組みが使えないため）。
- 付け忘れを構造で防ぐため、`role.service` に小さな部品 `RoleStoreTransactions` を1つ置き、store を呼ぶ1つ目のトランザクションはすべてこれを通す。部品は、結果が `Done` 以外なら必ず `status.setRollbackOnly()` を付ける。
  - トランザクションの境界は service の層の中に置くため、既存の `ArchitectureTest`（service の外の `@Transactional` の禁止）と合う。`@Transactional` は使わない。

```java
// role.service の RoleStoreTransactions（説明用の断片）
<T> RoleStoreOutcome<T> first(Function<RoleStore, RoleStoreOutcome<T>> work) {
    return transaction.execute(status -> {
        RoleStoreOutcome<T> outcome = work.apply(store);
        if (!(outcome instanceof RoleStoreOutcome.Done<T>)) status.setRollbackOnly(); // T5
        return outcome;
    });
}
```

- 業務の判定での拒否（書き込みの前）は、1つ目の中で失敗の出来事を出して確定させる（`Done` の値として拒否の理由を返し、印は付けない）。
- 違反は、巻き戻した後に2つ目の `TransactionTemplate`（書き込みなし）で失敗の出来事だけを出す。上限切れは出来事を出さない。
- 入れ子の REQUIRES_NEW は使わない。同時に持つ接続は2本までで、`RoleConnectionUsageIT` で確かめる（`scalability-design.md`）。
- `RoleStoreTransactionsIT`（B4）で、`TransactionTemplate` の中で違反・上限切れを起こし、`UnexpectedRollbackException` が出ないことと、2つ目の失敗の出来事が確定することを確かめる。
- `RoleBoundaryArchitectureTest` で、`role.service` の中で `RoleStore` を呼ぶのは `RoleStoreTransactions` と読み取りの部品だけであることを確かめる。

### 2.5 import の適用

- 判定の順（NFR3.8）: 本文の型・大きさ → 入口（`RoleTransferSlot`、2.6）→ DSL の有無 → 読み込み → 中身の検証 → ロールの排他（ID の昇順）→ 一覧の作り直し → DSL の有無（もう一度）→ 指紋 → 変える点の有無。
- 書き込みは1つのトランザクションのまま、`role/store` の中で行う（読み直しの R-06）。
  - JDBC の部品（Spring の `JdbcTemplate`）を使う。main ではまだ使っていない新しい書き方で、JPA のトランザクションと同じ接続を使う。
  - 置き換えるロールごとに設定の行を1文で消し、作る行を 1,000 件ずつのバッチで書く。
  - `jakarta.persistence.query.timeout` は JPA の問い合わせにしか効かないため、`JdbcTemplate` に `setQueryTimeout(10)`（10 秒）を明示する。試しの1文の最大 57 ms に余裕がある。
  - JDBC で書く前に `EntityManager.flush()` を呼ぶ。適用の同じトランザクションの中では、JDBC で消した・書いた設定の行を Hibernate のエンティティとして読まない。読み直しは行の値の射影の問い合わせだけにする。ロールの `updatedAt` も同じ JDBC の部品で書く。
  - `JdbcTemplate` の `StatementCreatorUtils` は TRACE で引数の値を出す。`application.yaml` に `org.springframework.jdbc.core.StatementCreatorUtils: OFF` を足す（`org.hibernate.orm.jdbc.error: OFF` と同じ扱い）。`RoleSecretLeakIT` の YAML の目印の確かめで、TRACE を `org.springframework.jdbc` にも広げ、目印が 0 件であることを確かめる（`security-design.md` の 4.3）。
- 途中の失敗は、すべてを巻き戻し、成功の出来事を出さない（NFR3.2）。JDBC のバッチと Hibernate の書き込みが同じトランザクションで巻き戻ることは、試しで確かめていない（6節）。B6 の `RoleTransferAtomicityIT` で確かめる。
- 同時の適用と、適用中の同じロールへの割り当て（読み直しの R-07 の (h)）の期待は 4節のとおり。

### 2.6 確かめと適用の入口（`RoleTransferSlot`、Q5: A）

- **決まり**: 確かめと適用は、アプリの中で同時に1つずつしか通さない。2つ目の要求は待たずに 409 `ROLE_BUSY` で断り、監査に残さない。WARN の `lockKind` は `ROLE_TRANSFER_SLOT`。
- **根拠**: ヒープは約 1 GiB を前提にする（`compose.yaml` の既定の `mem_limit` 2g と、`Dockerfile` の `MaxRAMPercentage` 50）。試しでは、上限の 96% の確かめの後に使っていたヒープが約 230〜330 MB だった。この値は、次の2点で本番の値と違う。
  - アプリの土台（Spring Boot・Hibernate・DSL の写しなど）を含まない。
  - 生きている量と、ごみを含む量を分けていない。
  - それでも、同じ規模の確かめと適用が2つ以上同時に動くと、土台と合わせて約 1 GiB に近づくおそれがある。管理者が大きな YAML を同時に投入してメモリが尽きる危なさを、小さくするために置く。
- **取る位置**: 本文を読む前に取る。コントローラーは本文を `byte[]` ではなく要求の入力の流れとして `RoleTransferService` に渡す（中身を伏せる型 `RoleTransferBody`）。service は入口を取ってから、本文を道ごとの上限（10 MiB）まで読む。入口は `Semaphore(1)` の `tryAcquire()` で、待たない。
- **放し方**: 入口を取った後は、`try` の中で本文の読み込みからトランザクションの終わりまでを行い、`finally` で必ず放す（読み込みの失敗・検証の誤り・想定外の例外・巻き戻しのどれでも放す）。`RoleTransferBusyIT` で、1つ目を待ち合わせで止めたまま2つ目が `ROLE_BUSY` になることと、1つ目が例外で終わった後に次の要求が通ることを確かめる。
- **受け入れた制約**（`security-design.md` の 7節）: 次の2つは、この入口に入らない。同時に動くことがあり、ヒープを大きく使いうる。
  - 書き出し: 上限を超えて最大約 40 MB の文字列を作りうる。
  - DSL の管理の 10 MiB の投入と適用。
- 単一のインスタンスの前提（組み込みの H2）のため、入口はアプリの中の1つで足りる。

## 3. 写しと resolve の一致（Q2: A、navigation の R-01・R-08）

- 2つの口はそのまま（承認済みの NFR2.3）にし、一致は role の結合テスト `EffectivePermissionConsistencyIT`（B5）で守る。
- 期待は、テストの用意（DSL・明示の設定・割り当て）から、テストの中に書いた期待の表（各スキーマ・テーブル・カラムの期待の主権限・CREATE・DELETE）で持つ。メニューの応答からは作らない（navigation の R-01 の誤りを避ける）。
- すべての対象で、`resolve` の値と写しの値がどちらも期待の表に等しいことを確かめる。組は次の8つ。
  - (a) 下位の明示が上位を上書きする向き（スキーマ NONE・テーブル READ、スキーマ FULL・テーブル NONE）
  - (b) カラムだけに明示の値
  - (c) 全階層が設定なし
  - (d) 作業ロールが無い
  - (e) DSL が無い
  - (f) DSL に無い対象の名前
  - (g) 直接とグループ経由にまたがる割り当て
  - (h) 保存が割り当ての外を指す読み替え中
- 1要求の中の一貫性（NFR 要件の読み直しの R-11）: `resolve` が同じ作業ロールで答えるのは1回の呼び出しの中だけ。2つ以上の対象を判定する呼ぶ側は `snapshotFor` を使う。このことを口の Javadoc に書く。

## 4. テストの設計（NFR3.1・NFR3.3・NFR3.4、Q3: A、読み直しの R-02）

### 4.1 NFR3.1 の (b)・(g) の期待（この段で確定）

- **(b) 同じ名前の同時の作成**
  - 終わった後にロールは1つだけで、勝った側は 201。
  - 負けた側は 409。`ROLE_NAME_DUPLICATE` なら監査に FAILURE が1行、`ROLE_BUSY` なら監査なし。どちらの code でも 500 にならない。
  - どちらの code になるかは、先の側が確定する時機で決まる（group の T1）。テストでは、待ち合わせの口で両方の経路を別々に、決まった形で起こす（4.2）。
- **(g) 同じ組の同時の割り当て**
  - 終わった後に割り当ては1行だけで、勝った側は 204。
  - 負けた側は 409。`ROLE_NO_CHANGE` なら監査に FAILURE が1行、`ROLE_BUSY` なら監査なし。500 にならない。
  - 割り当ては先にロールの行を排他するため、API からは主キーの待ち・違反には届かない。後の側はロールの行の待ちで並び、排他の後の判定で `ROLE_NO_CHANGE` になる。主キー（`ROLE_ASSIGNMENT_KEY`）は、排他を通らない経路が生じたときの最後の守りとして、store を直接呼ぶテストで確かめる。

### 4.2 どの重なりを、どの層の、どの口で作るか

合否は、両方が終わった後の内部DB の状態と、負けた側が業務の code の 4xx（500 でない）であることで決める。経過の時間では決めない。テストの側の待ちの上限は 20 秒で、時間切れはテストの失敗（不安定の兆し）として扱う。

| # | 重なり | 層 | 作り方（待ち合わせの口） | 期待 |
|---|---|---|---|---|
| 1 | (a) 削除と割り当て | 結合（API） | 削除を `afterLock` で止める → 割り当てを送り、`beforeLock` の合図を待つ → 削除を放す。逆の順（割り当てを `afterLock` で止める）も | ロールが無ければ割り当て 0、割り当てがあればロールがある。負けた側は `ROLE_NOT_FOUND`・`ROLE_IN_USE` |
| 2 | (b) 違反の経路（待たない違反） | 結合（API） | 後の側 B を `afterCheck`（重なりなしと判定した後）で止める → 先の側 A の作成を最後まで確定させる → B を放す。B は確定済みの鍵に書くため待たずに 23505 | B は 409 `ROLE_NAME_DUPLICATE`、監査に FAILURE が1行、ロールは1つ。名前の変更（別々のロールを同じ名前へ）も同じ形 |
| 3 | (b) 上限切れの経路 | 結合（API） | 先の側 A を `afterWrite`（書いたが未確定）で止めたまま放さない → B の作成を送る。B は重なりなしと判定して書き、鍵の待ちで H2 の上限まで待って切れる → B の結果が出た後に A を放す | B は 409 `ROLE_BUSY`、監査なし、WARN `ROLE_NAME_KEY` が1件。A は 201。ロールは1つ |
| 4 | (g) 同じ組の割り当て | 結合（API） | A を `afterWrite`（割り当てを書いたが未確定、ロールの行を排他中）で止める → B を送り、`beforeLock` の合図を待つ → A を放す | 割り当ては1行。B は 409 `ROLE_NO_CHANGE`、監査に FAILURE が1行 |
| 5 | (g) ロールの行の上限切れ | 結合（API） | A を `afterLock` で止めたまま放さない → B を送り、ロールの行の待ちで 3 秒の上限まで待たせて切らす → B の結果の後に A を放す | B は 409 `ROLE_BUSY`、監査なし、WARN `ROLE_ROW` |
| 6 | 割り当ての主キー（最後の守り） | 結合（store を直接） | テストの部品から、ロールの行を排他せずに `RoleStore` の割り当ての書き込みを2つ呼ぶ。待たない違反（先を確定させてから）と、上限切れ（先を未確定で持ったまま）の2つ | `AlreadyAssigned` と `Busy("ROLE_ASSIGNMENT_KEY")` になる。`RoleStoreTransactions` を通すと `UnexpectedRollbackException` が出ない |
| 7 | (c) 同じロールの同時の保存 | 結合（API） | A を `afterLock` で止める → B を送り、`beforeLock` の合図を待つ → A を放す | どちらも 204、値は B の値、監査の2行の前後の値が連なる |
| 8 | (d) 切り替えと外し | 結合（API） | 切り替えを `afterCheck`（割り当てを読んだ後）で止める → 外しを最後まで確定させる → 切り替えを放す | 両方成功。次の要求の有効な作業ロールは割り当ての中のロールか無し |
| 9 | 同じ利用者の初めての切り替えの重なり | 結合（API） | A を `afterWrite` で止めたまま放さない → B を送る → B の結果の後に A を放す | B は 409 `ROLE_BUSY`（`WORK_ROLE_SELECTION_KEY`）、保存は A の値。H2 の `MERGE INTO … KEY` が未確定の行に対して待って切れることは試していないため、B5 の始めに結合テストで確かめ、違反（23505）になる場合も同じ `ROLE_BUSY` に区分する（2.3） |
| 10 | (e) グループの削除とグループへの割り当て | 結合（API） | グループの削除を group の `GroupBarrier.afterLock` で止める → 割り当てを送り、role の `afterLock`（ロールの行の後、グループの行の前）の合図を待つ → 削除を放す。逆の順（割り当てを `afterWrite` で止める → 削除を送る → 割り当てを放す）は、削除の側に排他の前の口が無いため合図を待たずに放す。どちらの順に並んでも期待は同じ | 消えていれば割り当て 0。負けた側は `GROUP_NOT_FOUND`・`GROUP_IN_USE` |
| 11 | (h) 適用どうし | 結合（API） | 1つ目の適用を `afterLock` で止める → 2つ目の確かめか適用を送る | 2つ目は入口で 409 `ROLE_BUSY`（`ROLE_TRANSFER_SLOT`）。1つ目を放すと 200 |
| 12 | (h) 適用中の割り当て | 結合（API） | 適用を `afterLock`（置き換えるロールの行を排他した後）で止めたまま → 同じロールへの割り当てを送り、3 秒の上限で切らす → 結果の後に適用を放す | 割り当ては 409 `ROLE_BUSY`、割り当ては変わらない。適用は 200 |
| 13 | (f) 確かめの後の変更 | 結合（API、順に送る） | 確かめ → 別の要求で保存・削除・同名の作成・DSL の適用し直し・DSL を外す → 適用 | `ROLE_TRANSFER_STALE`（DSL を外したときは `DSL_NOT_APPLIED`） |
| 14 | 待った後の違反 | 単体 | 例外の連なり（`ConstraintViolationException`・23505・制約の名前）を作って区分の部品に渡す | 待たない違反と同じ区分（group の T1 で、待った後も同じ例外になると確かめ済み） |

- `#1`・`#4`・`#7`・`#10` の後の側が排他を待つかどうかは、放す時機で変わる。どちらでも期待は同じで、合否は時間で決まらない。ただし、先の側が放した後 3 秒以内に確定しないと、後の側は `ROLE_BUSY` になる。この場合も期待（4xx・状態）を満たすため、テストは `ROLE_BUSY` も負けの code として受け入れる。

### 4.3 そのほかのテスト

| テスト | 期待 |
|---|---|
| `RoleStoreClassificationTest` | 2.3 の区分と相手になる。想定外の包み直しに元の文が入らない |
| `RoleStoreTransactionsIT` | 2.4 のとおり |
| `RoleTransferAtomicityIT` | テストだけの部品で N 件目のバッチの書き込みを失敗させると、全件が前と同じで、成功の監査が無い。JDBC のバッチと Hibernate の書き込みが同じトランザクションで巻き戻ることも、ここで確かめる |
| `RoleTransferBusyIT` | 2.6 のとおり |

- 結合テストの名前の割り当て: `#1`〜`#5`・`#7` は `RoleConcurrencyIT`・`RoleConflictAuditIT`・`RoleBusyApiIT` に分けて置く（違反は `RoleConflictAuditIT`、上限切れは `RoleBusyApiIT`、そのほかは `RoleConcurrencyIT`）。
- そのほかの置き場: `#6` は `RoleStoreKeyGuardIT`、`#8`・`#9` は `WorkRoleConcurrencyIT`、`#10` は `GroupRoleAssignmentConcurrencyIT`、`#11`・`#12` は `RoleTransferBusyIT`、`#13` は `RoleTransferStaleIT`、`#14` は `RoleStoreClassificationTest`。

## 5. 監査の失敗・移行・戻し（NFR3.5・NFR3.6）

- 監査の書き込みの失敗は既存の扱いのまま。`RoleAuditWriteFailureIT` で、ロールの作成と作業ロールの切り替えを確かめる。
- 移行はコード生成の時点の次の空き番号で行う。B4 で `roles`・`permission_settings`、B5 で `user_role_assignments`・`group_role_assignments`・`work_role_selections` を足すだけ。列の長さは、ロールの名前 128・鍵 256・対象の名前 256（UTF-16）。前の版のアプリは足した表を見ない。

## 6. 承認済みの要件・設計・答えとの差

承認済みの文書は書き換えず、差をここに記録する（`project.md` の決まり）。

- **確かめと適用を1つずつしか通さない**（2.6、Q5: A）: 承認済みの要件・機能設計・契約に無い決まり。依頼者の Q5 A で足した。2つ目は待たずに `ROLE_BUSY`。
- **巻き戻しの印を付ける置き場**（2.4）: 機能設計・NFR 要件は、業務処理の側で付ける形を前提にしていた。業務処理の中の1つの部品（`RoleStoreTransactions`）に集めた。group の読み直しの R-05 の手当てで、group の今の設計（操作ごとに付ける）とは置き場が違う。
- **書き出しでの名前の二重引用符**（1.2 U1）: 機能設計の BR9.14 に無い。往復を保つ書き方として足した（読み込みの側の扱いは変えない）。
- **一意の鍵・主キーの待ちの上限**（2.1、読み直しの R-07）: 承認済みの NFR3.3 の 3 秒は行の排他のヒントにだけ当たると記録し、鍵・主キーの待ちは約 2 秒（H2 の既定）とした。承認の場で依頼者がこの値を受け入れた（group と同じ）。
- **待ち合わせの口を4つにした**（2.2）: 承認済みの機能設計の BR8.6 は「排他の直後に置く」口だけを書いていた。API 経由で重なりを作るため、`beforeLock`・`afterCheck`・`afterWrite` を足した。
- **作業ロールの保存の主キーの違反を `ROLE_BUSY` に読み替える**（2.3）: 機能設計の BR8.5 は主キーの違反を `ROLE_NO_CHANGE` としていたが、作業ロールの保存は管理の操作ではない。同じ利用者の切り替えの重なりとして `ROLE_BUSY` に読み替える。
- **捨ての試しの作り**（読み直しの R-05）: Q4 A の答えは「最小の Spring Boot のアプリ」だったが、試しは Spring Boot と Hibernate を使わず、HikariCP と JDBC で直接書いた（1.1）。そのため、次の3点を試しでは確かめていない。
  - (1) `snapshotFor`・`resolve` の時間に、JPA の射影と `TraceAspect` の上乗せが入っていない。目標の 10 分の 1 未満の値のため、Performance Validation の `myPermissionsTree`・`workRoleSwitch` で確かめる。
  - (2) JDBC のバッチと Hibernate の書き込みが同じトランザクションで動き、巻き戻ること。B6 の `RoleTransferAtomicityIT` で確かめる。
  - (3) 例外の型と区分。group の試し T7 の借り物で、B4 の `RoleStoreClassificationTest` と `RoleStoreTransactionsIT` で確かめる。
- **import の書き込みの JDBC**（2.5、読み直しの R-06）: main ではまだ使っていない `JdbcTemplate` を、`role/store` の中だけで使う。文の上限 10 秒は `JdbcTemplate` の側で明示する。TRACE の引数のロガーを OFF にする。

## 7. 読み直し1回目の直し

読み直し（NOT-READY）を受け、依頼者の決定（Q5: A と、指摘をすべて直す）で次を直した。

- **R-01**: 巻き戻しの印を `TransactionAspectSupport` で付ける記述を除いた。`TransactionTemplate` の中の `status.setRollbackOnly()` を、service の層の部品 `RoleStoreTransactions` に集め、`ArchitectureTest` と合う置き方にした（2.4）。
- **R-02**: 待ち合わせの口を4つにし、API 経由で届く重なりと届かない重なりを分けた表にした（2.2・4.2）。NFR3.1 の (b)・(g) の期待を確定した（4.1）。
- **R-03**: `RoleTransferSlot` の根拠を、ヒープ約 1 GiB と試しの値の限りに合わせて書き直した。取る位置（本文を読む前）、放し方（`finally`）、入口に入らない重なり（受け入れた制約）を書いた（2.6）。
- **R-05**: 試しの作りが Q4 A と違う差と、未確認の3点を 6節に足した。
- **R-06**: `JdbcTemplate` が新しい書き方であること、文の上限を `JdbcTemplate` で明示すること、Hibernate で読まないこと、TRACE の漏えいの確かめを書いた（2.5）。
- **R-07**: 鍵の待ちの上限のいまの値（約 2 秒）と、そろえ直しの扱いを書いた（2.1・6節）。
- **R-10**: 1.3 に、節の数が上限に近い短い名前の形を Build and Test で1回測る引き継ぎを足した。

## 承認の場の決定と直し

承認の場で依頼者が Request Changes を選び、決定の文は「推奨の案のとおり直す」（直す範囲: 各単位の読み直しの Major と、単位の間でそろえる3点）。この成果物では次を直した。

- **そろえる点3（鍵の待ちの上限）**: 2.1 と 6節を、「group の決着待ち」から決まった値（鍵・主キーは約 2 秒、NFR3.3 の 3 秒は行の排他のヒントだけ）に書き直した。
- **自分での見直し（R-02 の表）**: API で本当に届くかを1行ずつ見直し、次の2つを直した。
  - #9: `MERGE` が未確定の行で待って切れることは試していないため、B5 の始めに確かめる。違反になる場合も `ROLE_BUSY` に区分する。
  - #10: 逆の順は、削除の側に排他の前の口が無いため、合図を待たずに放す（どちらの順でも期待は同じ）。
  - ほかの行は、口の位置（`afterCheck` は判定の後で書き込みの前、`afterWrite` は書き込みと flush の後で確定の前）と排他の順番から、API で届くことを確かめた。
- **自分での見直し（R-01）**: `RoleStoreTransactions` は `role.service` に置き、`TransactionTemplate` を使って `@Transactional` を使わない。そのため、`ArchitectureTest` の「service の外の `@Transactional` の禁止」と「web は repository を使わない」に合う。store を web から呼ばないことは `RoleBoundaryArchitectureTest` で確かめる（2.4）。変更は無い。
