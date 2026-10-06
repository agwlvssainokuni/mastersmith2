# 信頼性の設計 — U3 group

## 出典

- この単位の承認済みの NFR 要件 `construction/group/nfr-requirements/`（`reliability-requirements.md` の NFR3.1・NFR3.3〜NFR3.7・NFR6.1、`scalability-requirements.md` の NFR2.7）
- この単位の承認済みの機能設計 `construction/group/functional-design/`（`functional-spec.md`・`rules.md` の BR5.1〜BR5.7・`entities.md`）
- `contract-summary.md`（C4 に足した `lockForAssignment`・`memberUserIds`）、`components.md`（GroupManagement）
- この段の答え: `nfr-design-questions.md` の Q1〜Q3（すべて A）とまとめの確認
- 捨ての試しの結果（この成果物の1節）

## 1. 捨ての試しの結果（Q2: A、NFR3.7）

### 1.1 試しの形

- 置き場: リポジトリの外の作業用の一時の場所に最小の Spring Boot のアプリを作り、終わった後に消した。本物の作業フォルダでは git・gradlew・npm を使っていない。
- 版: Spring Boot 4.1.1・Hibernate 7.4.5.Final・HikariCP 7.0.2・H2 2.4.240（`backend/gradle.lockfile` と同じ）、Java 25（Temurin 25.0.4）。
- 設定: JVM に `-Dh2.compactThreads=1`・`-Duser.timezone=Asia/Tokyo`・`-ea`。接続先はファイルの H2 で `DEFRAG_ALWAYS=TRUE`。プールの上限 10・接続の待ち 5000 ms。`jakarta.persistence.query.timeout` 10000（本番と同じ）。
- 表: 本番の設計と同じ形の `groups`（`name_key` に一意の制約）・`group_members`（主キーは組、グループと利用者への外部キー）と、U4 の割り当てを模した `role_group_assignments`（グループへの外部キー）。
- データは仮の値だけを使った（名前 `TrialAlpha` など、メールアドレスは `example.com`）。
- 掛かった時間は約 10 分で、上限の 2 時間の内。7 項目すべてを確かめた。

### 1.2 結果

| # | 項目 | 結果 | 設計への意味 |
|---|---|---|---|
| T1 | 同じ名前（大文字と小文字だけが違う鍵）の同時の作成 | 先に書いた側が 1 秒後に確定すると、後の側は約 1,000 ms 待ってから一意の違反（SQLState 23505）。先に書いた側が巻き戻すと、後の側は成功する。先に書いた側が 12 秒持ち続けると、後の側は **約 2,000 ms** で待ちの上限切れ（SQLState HYT00・コード 50200、Hibernate は `jakarta.persistence.LockTimeoutException`） | 重なった側は「違反」と「待ちの上限切れ」のどちらにもなる。違反は `GROUP_NAME_DUPLICATE`、上限切れは `GROUP_BUSY` にする（2節）。結合テストは、時間に頼らずに両方の経路を別々に起こす（違反は先の側を確定させてから後の側を書く、上限切れは先の側を放さない。4.2。承認の場の直し R-02） |
| T2 | 同じ名前への同時の変更（別々のグループ） | T1 と同じ（違反は約 1,000 ms 後、持ち続けると約 2,000 ms で上限切れ）。対象の行の排他は、別のグループの行のため効かない | T1 と同じ扱い。名前の変更でも違反と上限切れの両方を読み替える |
| T3 | 同じメンバーの同時の追加（主キー） | T1 と同じ（違反 23505、上限切れ約 2,000 ms）。ただし本番ではメンバーの追加は先にグループの行を排他するため、同じグループへの追加は行の排他で並ぶ（違反と主キーの待ちは、排他の外から書く経路が生じたときの最後の守り） | 主キーの違反は `GROUP_NO_CHANGE`、待ちの上限切れは `GROUP_BUSY` |
| T4 | 削除とメンバーの追加の重なり | (i) 両方がグループの行を排他する形: 削除の側が排他してメンバーを数え、削除して確定すると、待っていた追加の側の排他の読み取りは「行が無い」を返した（約 1,160 ms 後）。(ii) 追加の側が排他を取らずに書く形: 削除の側の排他は追加を **止めず**（外部キーの確かめは親の行の排他を待たない）、追加が先に確定した後、削除が外部キーの違反（SQLState 23503）で失敗した。(iii) 排他の待ちの上限 3,000 ms のヒント: 約 3,015 ms で上限切れ（`LockTimeoutException`、`for update wait 3`） | 順番を決めるのは「すべての書き込みが先にグループの行を排他する」こと（BR5.1・BR5.3）。U4 のグループへの割り当ても `lockForAssignment` を必ず通す。外部キーは排他を通らない経路の最後の守りとして働く（違反は `GROUP_IN_USE` に読み替える）。排他の上限切れは `GROUP_BUSY` |
| T5 | 違反の後の2つ目のトランザクション | 1つ目の `TransactionTemplate` の中で違反を受けて `setRollbackOnly()` を付けて普通に戻ると、例外なく巻き戻り、続く2つ目の `TransactionTemplate` は問題なく確定した。`setRollbackOnly()` を付けずに普通に戻ると、確定の時に `UnexpectedRollbackException` になった（Hibernate が違反の時点で巻き戻しの印を付けるため） | 違反・上限切れを受けたら、必ず1つ目で `setRollbackOnly()` を付けてから結果の型を返す（付け忘れは 500 になる）。2つ目のトランザクションで失敗の出来事を出す形（NFR3.4）は成り立つ |
| T6 | 例外の連なりの文に行の値が入るか | 一意の違反の文に鍵の値（例 `'trialalpha'`）が入った。主キーの違反の文にグループの ID と利用者の ID、外部キーの違反の文にグループの ID が入った。行の排他の上限切れの連なりの奥（`org.h2.mvstore.MVStoreException`）に、排他されていた行の全部の列（名前・ID）が入った。一意・主キーの待ちの上限切れの文には行の値が入らなかった。また Hibernate のロガー `org.hibernate.orm.jdbc.error` が、違反の文（値つき）をアプリへ例外を渡す前に WARN で出した | 例外の文は TraceAspect の対象の方法を通さず、ログには例外のクラスの名前だけを出す（Q3: A、`security-design.md`）。本番の `application.yaml` は `org.hibernate.orm.jdbc.error: OFF`（261003-user-admin-followup の FR8.2）のため、この WARN は出ない。この設定を消さないことを漏えいのテストで守る |
| T7 | 例外の型での見分け | 違反: `org.hibernate.exception.ConstraintViolationException`（原因は `JdbcSQLIntegrityConstraintViolationException`。SQLState 23505 が一意・主キー、23503 が外部キー）。上限切れ: `jakarta.persistence.LockTimeoutException`（原因の SQLState HYT00・コード 50200）。既存の `RowLockFailures.isLockFailure` は上限切れを真、違反を偽と判定した | 区分は次の順で行う。`RowLockFailures.isLockFailure` が真なら上限切れ。そうでなく、連なりに SQLState 23505・23503 があれば違反とし、制約の名前（`uk_groups_name_key`・主キー・外部キー）で読み替え先を決める。どれでもなければ想定外として例外のまま投げる（ただし文はログに出さない） |

### 1.3 要件との差

- **一意の鍵・主キーの待ちの上限は約 2 秒**: 承認済みの NFR3.3 は待ちの上限を「3 秒」と書いた。行の排他は問い合わせのヒント（3,000 ms）で 3 秒になる。しかし一意の鍵・主キーの待ちは、ヒントの効かない書き込みの待ちで、H2 の既定（約 2,000 ms）で切れた。セッションに `SET LOCK_TIMEOUT 3000` を送ると 3 秒にできることも確かめたが、その値はプールに返した接続に残り、ほかの機能の書き込みの待ちまで変える。そのため、この単位では H2 の既定（約 2 秒）を受け入れ、設定は変えない。上限切れの扱い（巻き戻して `GROUP_BUSY`、監査なし）は同じで、待ちが短くなる側の差のため、目標（p95 1 秒・500 を出さない）は損なわない。
- **409 の code**（NFR 要件の読み直しの R-07）: 同じ名前の同時の作成・変更で重なった側は、先の側が約 2 秒以内に確定すれば `GROUP_NAME_DUPLICATE`、そうでなければ `GROUP_BUSY` になる。どちらも業務の 409 で 500 にならない。この段の承認で確定する。

## 2. 同時の重なりの守り（NFR3.1・NFR3.3・NFR3.4）

### 2.1 排他の順番

- 変える操作（名前の変更・削除・メンバーの追加と外し）と、U4 のグループへの割り当てと外しは、最初にグループの行を排他する（`PESSIMISTIC_WRITE`、ヒント `jakarta.persistence.lock.timeout` 3,000 ms）。T4 (i) のとおり、待っていた側は確定の後に「行が無い」（`GROUP_NOT_FOUND`）か、最新の状態（メンバーの数・割り当ての有無）を見て判定する。
- 作成は既存の行を変えないため排他を取らない。同じ名前の重なりは一意の制約が守る（T1）。
- 待ち合わせの口 `GroupBarrier` は4つの点を持つ（本番は何もしない `NoOpGroupBarrier`、テストは `group/testsupport` の `@Primary` の部品。U4 role の `RoleBarrier` と同じ形。承認の場の直し R-02）。

| 点 | 呼ぶ所 | 使う操作 |
|---|---|---|
| `beforeLock(op, groupId)` | グループの行の排他の直前 | 名前の変更・削除・メンバーの追加と外し |
| `afterLock(op, groupId)` | グループの行の排他を取った直後 | 同上 |
| `afterCheck(op, key)` | 業務の判定（重なり・有無・メンバーか）の後、書き込みの前 | 作成・名前の変更・メンバーの追加 |
| `afterWrite(op, key)` | 書き込みと flush の後、確定の前 | 作成・名前の変更・削除・メンバーの追加と外し |

### 2.2 例外の区分と結果の型

`group/store` の部品（`logical-components.md` の L2）が、排他と違反を起こしうる書き込み・flush を行い、例外を中で受けて次の結果の型に変える。

```java
// group/store の結果の型（説明用の断片）
sealed interface StoreOutcome<T> {
    record Done<T>(T value) implements StoreOutcome<T> {}
    record GroupMissing<T>() implements StoreOutcome<T> {}      // 排他の読み取りで行が無い
    record NameTaken<T>() implements StoreOutcome<T> {}         // uk_groups_name_key の違反（23505）
    record AlreadyMember<T>() implements StoreOutcome<T> {}     // group_members の主キーの違反（23505）
    record Referenced<T>() implements StoreOutcome<T> {}        // 外部キーの違反（23503）
    record Busy<T>(String lockKind) implements StoreOutcome<T> {} // 行・鍵・主キーの待ちの上限切れ
}
```

- 区分の順は 1.2 の T7 のとおり。どれにも当たらない例外は想定外として、クラスの名前だけを持つ例外に包み直して投げる（元の連なりの文を持ち出さない）。
- 上限切れは `RowLockFailures.warn(logger, lockKind, e)` で WARN を1回（`lockKind` は `GROUP_ROW`・`GROUP_NAME_KEY`・`GROUP_MEMBER_KEY`）。

### 2.3 業務処理の組み立て（NFR3.4、機能設計の再レビューの R-11）

- 巻き戻しの印は、`TransactionTemplate` の中で `status.setRollbackOnly()` を呼ぶ形で付ける（試し T5 で確かめた形）。付け忘れを構造で防ぐため、`group.service` に小さな部品 `GroupStoreTransactions` を1つ置き、store を呼ぶ1つ目のトランザクションはすべてこれを通す。部品は、結果が `Done` 以外なら必ず印を付ける（U4 role の `RoleStoreTransactions` と同じ形。読み直しの R-05 の手当て）。`group.store` は印を付けない（`TransactionTemplate` の中では、宣言的なトランザクションの今の状態を読む仕組みが使えないため）。

```java
// group.service の GroupStoreTransactions（説明用の断片）
<T> StoreOutcome<T> inFirst(Function<GroupStore, StoreOutcome<T>> call) {
    return transaction.execute(status -> {
        StoreOutcome<T> outcome = call.apply(store);       // 排他 → 判定 → 書き込み → flush
        if (!(outcome instanceof StoreOutcome.Done<T>)) status.setRollbackOnly(); // T5
        return outcome;
    });
}
// GroupAdminService: switch (txs.inFirst(s -> s.rename(...))) { case Busy → 監査なし、
//   case NameTaken → 2つ目の TransactionTemplate で失敗の出来事だけ … }
```

- 1つ目のトランザクションは、業務の判定で拒否したとき（書き込みの前の拒否）も、違反・上限切れのときも、成功のときも、ここで終える。`Done` 以外は `GroupStoreTransactions` が巻き戻すため、書き込みの前の拒否と違反の失敗の出来事は、どちらも2つ目の `TransactionTemplate`（書き込みなし）で出す（形を1つにそろえる）。上限切れは出来事を出さない。
- 入れ子の REQUIRES_NEW は使わない。1つ目の接続を返してから2つ目を借りるため、同時に持つ接続は2本（確定の後の監査）を超えない（`scalability-design.md`）。

## 3. 監査の失敗・移行・戻し（NFR3.5・NFR3.6）

- 監査の書き込みの失敗は既存の `AuditEventListener` の扱いのまま（操作は成功のまま、ERROR で分かる）。`GroupAuditWriteFailureIT` で、グループの操作の1つを既存の `FailingAuditEventRepositoryConfig` で確かめる。
- 移行はコード生成の時点の次の空き番号で、`groups`・`group_members` の表と `audit_events` の3列を足すだけ。名前の列は UTF-16 の 128、鍵の列は 256（機能設計の R-03 の直し）。前の版のアプリは足した表と列を見ないため、同じ内部DB で動く。

## 4. テストの設計（NFR3.1・NFR3.3・NFR3.4・NFR6.1）

### 4.1 API 経由で届く重なりと届かない重なり（承認の場の直し R-02）

group の操作の順（作成は「先の重複の確かめ → 書き込み」、ほかは「グループの行の排他 → 判定 → 書き込み」）で、どの重なりが API 経由で本当に起きるかを確かめた。

- **同じ名前の作成・同じ名前への変更（別々のグループ）**: 届く。作成は行の排他を取らず、名前の変更は別々の行を排他するため、先の側の未確定の行は後の側の重複の確かめに見えず、後の側は書き込みで一意の鍵を待つ。
- **同じメンバーの追加**: 主キーの違反・主キーの待ちは届かない。両方が同じグループの行を先に排他するため、後の側は行の排他で待ち、先の側の確定の後に「すでにメンバー」と判定して `GROUP_NO_CHANGE` になる（違反は起きない）。主キーは store を直接呼ぶテストで確かめる。
- **削除とメンバーの追加**: 外部キーの違反は届かない。両方が同じグループの行を先に排他するため（T4 (i)）。外部キーは store を直接呼ぶテストで確かめる（T4 (ii) の形）。
- **グループの行の上限切れ**: 届く。先の側が排他を持ったまま放さなければ、後の側は 3 秒の上限で切れる。

### 4.2 どの重なりを、どの層の、どの口で作るか

合否は経過の時間ではなく、状態コードと code、状態が変わらないこと、監査の行で決める（`team.md` の「実時刻に頼らない」）。上限切れの経路は、先の側を放さずに H2 の上限（鍵は約 2 秒、行は 3 秒）で後の側を切らせ、後の側の結果が出てから先の側を放す。待ちの境に頼って違反を作ることはしない（U4 role の形と同じ）。

| # | 重なり | 層 | 作り方（待ち合わせの口） | 期待 |
|---|---|---|---|---|
| 1 | 削除とメンバーの追加 | 結合（API） | 削除を `afterLock` で止める → 追加を送り、`beforeLock` の合図を待つ → 削除を放す。逆の順（追加を `afterLock` で止める）も | グループが無ければメンバー 0、メンバーがあればグループがある。負けた側は `GROUP_NOT_FOUND`・`GROUP_IN_USE` |
| 2 | 同じ名前の作成（待たない違反） | 結合（API） | 後の側 B を `afterCheck`（重なりなしと判定した後）で止める → 先の側 A の作成を最後まで確定させる → B を放す。B は確定済みの鍵に書くため待たずに 23505 | B は 409 `GROUP_NAME_DUPLICATE`、監査に FAILURE が1行、グループは1つ。500 にならない（`GroupStoreTransactions` が印を付ける） |
| 3 | 同じ名前の作成（上限切れ） | 結合（API） | A を `afterWrite`（書いたが未確定）で止めたまま放さない → B の作成を送る。B は重なりなしと判定して書き、鍵の待ちで H2 の上限まで待って切れる → B の結果が出た後に A を放す | B は 409 `GROUP_BUSY`、監査なし、WARN `GROUP_NAME_KEY` が1件。A は 201。グループは1つ |
| 4 | 同じ名前への変更（別々のグループ） | 結合（API） | #2 と #3 と同じ形を名前の変更で（A・B はそれぞれ自分のグループの行を排他する） | #2・#3 と同じ。名前は変わらない側の元の名前のまま |
| 5 | 同じメンバーの追加 | 結合（API） | A を `afterWrite`（書いたが未確定、グループの行を排他中）で止める → B を送り、`beforeLock` の合図を待つ → A を放す | メンバーは1行。B は 409 `GROUP_NO_CHANGE`、監査に FAILURE（`NO_CHANGE`）が1行 |
| 6 | グループの行の上限切れ | 結合（API） | A を `afterLock` で止めたまま放さない → B（名前の変更・削除・メンバーの足し外しのどれか）を送り、行の待ちで 3 秒の上限まで待たせて切らす → B の結果の後に A を放す | B は 409 `GROUP_BUSY`、監査なし、WARN `GROUP_ROW` が1件、状態は変わらない |
| 7 | 主キーと外部キー（最後の守り） | 結合（store を直接） | テストの部品から、グループの行を排他せずに `GroupStore` の書き込みを2つ呼ぶ。主キーは待たない違反（先を確定させてから）と上限切れ（先を未確定で持ったまま）の2つ、外部キーは削除の前に別のトランザクションでメンバーを確定させる | `AlreadyMember`・`Busy("GROUP_MEMBER_KEY")`・`Referenced` になる。`GroupStoreTransactions` を通すと `UnexpectedRollbackException` が出ない |
| 8 | U4 のグループへの割り当てと削除 | 結合（API、B5） | U4 role の 4.2 の #10 のとおり（グループの削除を `GroupBarrier.afterLock` で止める → 割り当てを送り、role の合図を待つ → 削除を放す。逆の順も） | 消えていれば割り当て 0。負けた側は `GROUP_NOT_FOUND`・`GROUP_IN_USE` |
| 9 | 待った後の違反 | 単体 | 例外の連なり（`ConstraintViolationException`・23505・制約の名前）を作って区分の部品に渡す | 待たない違反と同じ区分（T1 で、待った後も同じ例外になると確かめ済み） |

テストの名前は `GroupConcurrencyIT`（#1・#5・#6）・`GroupNameConflictIT`（#2〜#4）・`GroupStoreConstraintIT`（#7）・`GroupStoreClassificationTest`（#9）。重なりはスレッドの数に頼らず待ち合わせの口で作る（`team.md`）。待ち合わせの口の待ちには、テストの側で長めの上限（例: 20 秒）を置き、上限に達したらテストの作りの誤りとして落とす。

### 4.3 そのほかのテスト

| テスト | 期待 |
|---|---|
| `GroupStoreTransactionsIT` | `Done` 以外の結果で、1つ目が例外なく巻き戻り、2つ目が確定する（T5） |

## 5. 受け入れた制約

- **入口の判定の後の窓**（承認済みの NFR1.10、NFR 要件の読み直しの R-04）: 要求の入口で管理者の印を確かめた後、書き込みまでの間に印を外されても、その1件の要求は通る。検証の対象にはしない。詳しくは `security-design.md` の5節。
- **書き込みの2本使い**: 書き込みの同時の数が接続プールの上限に達すると、確定の後の監査の2本目を待って時間切れになりうる（`project.md` の Decided の既知の制約）。扱いは `scalability-design.md` の2節。
- **一意の鍵・主キーの待ちの上限は約 2 秒**（依頼者の決定）: 承認済みの NFR3.3 の「3 秒」は、グループの行の排他のヒント（`jakarta.persistence.lock.timeout` 3,000 ms）にだけ当たる。一意の鍵・主キーの書き込みの待ちはヒントが効かず、H2 の既定の約 2 秒で切れる（T1〜T3）。この差を受け入れ、設定は変えない。
- **同じ名前の重なりの 409 の code が分かれる**（依頼者の決定）: 同じ名前の同時の作成・同じ名前への変更で、重なった側の code は、先の側が約 2 秒以内に確定すれば `GROUP_NAME_DUPLICATE`、そうでなければ `GROUP_BUSY` になる。どちらも業務の 409 で状態は変わらず、500 にならない。この振る舞いを受け入れる（NFR 要件の読み直しの R-07）。

## 6. 承認の場の決定と直し

依頼者は NFR 設計の承認の場で Request Changes を選び、決定の文は「推奨の案のとおり直す」（直す範囲: 各単位の読み直しの Major と、単位の間でそろえる3点）。読み直しの記録は `aidlc/spaces/default/intents/261004-role-menu/.aidlc-reviews/nfr-design/units/group/6f07949191bc1397/` の 1 回目。この成果物で直したのは次のもの。

- **R-02（Major）同時の重なりのテストを時間に頼らない形に**: 「先の側を 2 秒より短く・長く止める」形をやめた。違反は先の側を確定させてから後の側を書く「待たない違反」、上限切れは先の側を放さずに H2 の上限で後の側を切らす形にし、合否は 409 と状態が変わらないことと監査の行で決める（4.2）。group の操作の順で API 経由で届く重なりと届かない重なりを確かめ（4.1）、届かない主キー・外部キーは store を直接呼ぶテストにした。待ち合わせの口を4つにした（2.1）。U4 role の NFR 設計の Q3: A の形にそろえた。
- **R-05（そろえる点、読み直しの Minor）巻き戻しの印の置き場**: 操作ごとに付ける形をやめ、`group.service` の `GroupStoreTransactions` に集めた（2.3）。U4 role の `RoleStoreTransactions` と同じ形。あわせて、書き込みの前の拒否の失敗の出来事も2つ目のトランザクションで出す形にそろえた。
- **そろえる点3 待ちの上限と 409 の code**: 一意の鍵・主キーの待ちの上限は約 2 秒のまま受け入れ、承認済みの NFR3.3 の 3 秒は行の排他のヒントにだけ当たると記録した。同じ名前の重なりの 409 の code が分かれることを、受け入れた振る舞いとして書いた（5節）。
