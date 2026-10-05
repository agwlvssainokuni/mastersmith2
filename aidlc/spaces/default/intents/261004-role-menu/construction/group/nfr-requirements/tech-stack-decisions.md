# 技術の選択 — U3 group

## 出典

- `functional-spec.md`・`rules.md`（この単位の承認済みの機能設計）
- `requirements.md`（NFR6.4、要件 C2・C4）
- `contract-summary.md`（C4・C6・C10）
- `technology-stack.md`（コード知識ベース。版の一覧）
- この段の答え: `nfr-requirements-questions.md` の Q1: A・Q2: A とまとめの確認（機能設計のレビューで残った点の手当てを含む）。`team.md`（Code Style・Testing Posture）。

## ID の対応表

同じ番号は上流と同じ意味でだけ使う。全体の表は `security-requirements.md` の「ID の振り方」にある。

| 上流の枝番 | この単位での扱い | この単位の ID |
|---|---|---|
| NFR6.2 継承の解決・メニューの性質ベースのテスト | 当たらない（U4・U5・U7）。この単位の性質ベースのテストは NFR6.5 | — |
| NFR6.3 E2E | 当たらない（画面は U6・U7。この Intent の E2E は最大2本） | — |
| NFR6.4 カバレッジの下限 | 当たる | NFR6.4 |
| （足す） | 性質ベースのテスト・境界テスト・コード生成への引き継ぎ | NFR6.5〜NFR6.7 |

## 技術の選択

| 区分 | 選ぶもの | 理由 |
|---|---|---|
| 言語・土台 | Java・Spring Boot 4.1.1（webmvc・security・data-jpa・flyway・validation・actuator） | 既存のまま。新しい依存は足さない |
| 内部DB | 組み込みの H2（Spring Boot の管理の版、`-Dh2.compactThreads=1` のまま）・Flyway | 既存のまま。表と列を足すだけ（NFR3.6） |
| トランザクション | `TransactionTemplate`（`@Transactional` を付けない。利用者の管理と同じ） | 排他を取れないときの巻き戻しと、違反の後の2つ目のトランザクション（NFR3.4）を業務処理の中で明示して組むため |
| 行の排他 | 行を読むときの排他（待ちの上限 3 秒）。待ちの上限切れの区分けは既存の `common.persistence` の考え方（`RowLockAttempt`・`RowLockFailures`） | 既存の利用者の行の排他と同じ形 |
| 監査の detail の JSON | Jackson（Spring Boot の管理の系列 3.1、上書き 3.1.7 のまま） | 既存の依存。決めた型の record から作る |
| 指標 | Micrometer（`http.server.requests`） | 既存のまま（NFR5.2） |
| テスト | JUnit 5・Spring Boot Test・spring-security-test・ArchUnit 1.5.1・jqwik 1.10.1・Hibernate の統計（問い合わせの数） | 既存のまま |
| 負荷の試験 | k6（`perf/k6/scenarios.js` に場面を足す） | 既存の道具（NFR2.5・NFR2.7） |

新しい依存が無いため、ライセンスの確かめと OSV-Scanner の対象の変化は起きない。

## 要件

| ID | 要件 | 測り方・確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR6.4 | バックエンドのカバレッジの下限（行 80%・分岐 70%、全体とパッケージごと）を満たす。B3 で本体に手が入る `group.*`（新しい）・`audit.domain`・`audit.service`・`user.domain`・`user.service`・`useradmin.domain` は、どれも `packagesJudgedByTotal` に入っておらず、パッケージごとの下限がそのまま当たる。`audit.repository` には手を入れない見込みで、一覧の作業（下限を満たして外す）は付かない見込み。手を入れることになったら `team.md` のとおり下限を満たして一覧から外す | `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` の実測を記録する（`project.md` の学び） | Code Generation（B3）・Build and Test |
| NFR6.5 | グループの名前の正規化と鍵の関数（`GroupName`）に jqwik の性質ベースのテストを当てる（取り除いた結果に前後の空白が無い、受け付けた名前は 1〜64 コードポイント、鍵は大文字と小文字だけの違いで等しい、鍵の長さが列の 256 単位に収まる）。失敗時の乱数の種を記録する | 単体テスト `GroupNameProperties` | Code Generation（B3） |
| NFR6.6 | `GroupBoundaryArchitectureTest` を置き、`group` が `role`・`audit`・`useradmin` に依存しないこと、`group` に依存してよいのは `role`・`audit` だけであること、トランザクションの境界が `group.service` だけであることを確かめる。既存の境界テストは緩めない | 単体テスト（ArchUnit） | Code Generation（B3） |
| NFR6.7 | コード生成の計画に次を引き継ぐ（下の節） | 計画の承認で確かめる | Code Generation（B3） |

## コード生成（B3）への引き継ぎ

- **移行の番号**: V10 と固定せず、コード生成の時点の次の空き番号を使う（機能設計のレビューの R-08）。
- **USER_NOT_FOUND の移し替え**（R-07）: 手を入れる既存のクラスは `useradmin.domain.UserAdminProblemTypes`（定義と `of`・`all`）・`useradmin.service.UserAdminProblemTypeCatalog`・`user.domain.UserProblemTypes`・`user.service.UserProblemTypeCatalog`・`useradmin.web.UserAdminController` の参照。テストは `UserAdminProblemTypesTest`・`UserAdminProblemTypeCatalogTest` と、code の一覧を確かめる既存のテストを、計画の前にキーの名前の検索で洗い出す。code の二重の定義で起動が止まらないよう、「useradmin の一覧から外す」と「user の一覧に足す」を1つのコミットの中で同時に行う。
- **k6 の台本とデータ**: NFR2.5 の操作ごとの場面（`groupListFirst`・`groupListLast`・`groupDetail`・`groupRename`・`groupCreate`・`groupDelete`・`groupMemberAdd`・`groupMemberRemove`）と NFR2.7 の `groupPoolLimit`、悪い側のデータ（グループ 1,000・利用者 1,000 名・メンバー 1,000 人）と場面の用意の手順を B3 で足し、`perf/README.md` に書く。結合テスト `GroupConnectionUsageIT` も B3 で足す。流すのは Performance Validation（NFR2.5・NFR2.7。承認の場の直し R-01・R-02 に合わせて場面の名前をそろえた）。
- **監査の既存のテスト**: `AuditSecretLeakIT` の列の一覧に3列を足す（NFR1.7）。
- **仮の問う口の実装**: `role` のパッケージに置く（機能設計の BR6.3）。

## U4 role への引き継ぎ

- **違反の後の失敗の監査**: `TransactionTemplate` を2回順に実行する形（NFR3.4）にそろえる。入れ子の REQUIRES_NEW は使わない。
- **detail の数え方**: 16,384 を Java の文字列の長さ（UTF-16 の単位）で数え、要約に切り替える基準も同じにする（NFR5.5）。
- **0 以下の ID**: 400 にせず、存在しない ID と同じ 404 にする（NFR1.4）。
- **入口の判定の後の窓**: 管理者の印を外された直後の1件の要求が通る窓は、グループと同じ考え方で受け入れるか、ロールの側で決める（NFR1.10）。
- **B5 での 403 の表の足し直し**: 作業ロールを持てるようになったら、グループの管理の7つの口の 403 の表に「全スキーマを FULL にしたロールを作業ロールにする、管理者の印だけを欠く利用者」を足す（機能設計のレビューの R-10、NFR1.1）。
- **割り当てとの同時の重なり**: グループへのロールの割り当て・外しは `lockForAssignment` で同じ行を排他し、グループの削除との重なりを B5 で待ち合わせのテストにする（NFR3.1）。
- **接続プール**: グループへの割り当ての書き込みも2本使いの形のため、U4 の接続プールの試験に含める（NFR2.7 と同じ考え方）。
