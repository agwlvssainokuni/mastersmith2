# Tech Stack Decisions — U3 利用者の管理の API（u3-user-admin-api）

U3 は新しい依存を足しません。既存の技術（コードの知識ベース `aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`、`gradle/libs.versions.toml`）の上で作ります（この段の設計の要点 19、依頼者の Looks correct）。新しい依存が無いため、ライセンスの確かめ（`team.md` の Code Style）は要りません。構造の決まりは NFR11、カバレッジとテストの道具は NFR9 の枝番に置きます。出典の略号は `performance-requirements.md` と同じ。

## 使う技術

| 用途 | 技術 | 選んだ理由 | 関係する要件 |
|---|---|---|---|
| API と業務処理 | Spring Boot（既存の版）・Spring Web MVC | 既存のアプリの土台。新しい機能のパッケージ `useradmin` を既存の層（web・service・domain）に分けて置く | NFR1.1・NFR11.1 |
| 認証・認可 | Spring Security（既存の SecurityFilterChain、アクセストークンの認証、管理者の判定） | `/api/admin/` の既存の管理者の判定に乗せ、新しい仕組みを足さない。停止中の拒否は U1 のアクセストークンの認証の入口が受け持つ | NFR1.1〜NFR1.3 |
| トランザクション | Spring の `@Transactional`（業務処理の層だけ）と、上限切れのときの明示の巻き戻し（`setRollbackOnly`） | 5つの操作を1つのトランザクションで確定させ、BUSY を 500 にせず 409 で返す | NFR4.3・NFR9.5 |
| 行の排他 | Spring Data JPA の `PESSIMISTIC_WRITE` と `jakarta.persistence.lock.timeout`（3000 ミリ秒、既存の `LoginAttemptStateRepository`・`InvitationRepository` と同じ値） | 最後の有効な管理者の保護と、失敗回数を戻す操作とログインの判定の重なりを、DB の行の排他で守る（アプリのメモリに状態を持たない） | NFR4.1〜NFR4.5・NFR5.6 |
| DB アクセス | Spring Data JPA・Hibernate（`ddl-auto: validate`）、名前つきの引数の問い合わせ、伏せ字の型を SpEL の中で文字列にする形（既存の `updatePreferences` が前例） | 検索の文字と氏名を repository の口まで伏せ字の型で渡し、文字列をつなげて問い合わせを作らない | NFR3.1・NFR9.1・NFR5.2 |
| 内部DB | 組み込みの H2（表と列の変更なし） | 既存の内部DB。テストも同じ組み込みの H2 で行う（`team.md` の Testing Posture） | NFR4.5・NFR6.2 |
| 監査 | 既存の AuditLog（`AuditEventListener` の AFTER_COMMIT・`AuditEventRecorder` の `REQUIRES_NEW`） | 5つの種類と4つの理由の列挙の値、出来事の受け取りと写しを足すだけにする（ADR-006） | NFR9.3・NFR9.4・NFR6.2 |
| 時計 | 既存の注入した `Clock` | ロックの判定（ロック中か・解除の予定の時刻）を今の時刻で判定し、テストで動かせるようにする | NFR4.2・NFR9.7 |
| 観測 | 既存の Micrometer（`http.server.requests`）・SLF4J の構造化ログ・Micrometer Tracing | 新しい指標と警報を足さない | NFR5.9・NFR5.10・NFR3.4 |
| 負荷の試験 | 既存の k6（`perf/k6/scenarios.js`） | 場面を足すだけにする | NFR5.1〜NFR5.7・NFR6.2 |

## 構造とテストの要件

| ID | 要件 | 確かめ方と持ち主の段 | 出典 |
|---|---|---|---|
| NFR11.1 | `useradmin` は `user`・`auth` の service の口と値の型だけを使い、`user` が `auth` を知らない境界を保つ。加えて `useradmin.web` から `access.domain`（AccessProblemTypes）への依存を1本だけ足す。`useradmin` の依存してよい先と依存される側を `UserAdminBoundaryArchitectureTest` に書く。既存の `ArchitectureTest` と機能ごとの境界テストは書き換えず、緩めない・消さない（必要になったらコード生成の計画に明記して依頼者の承認を得る） | 境界テストを足し、既存の境界テストが変更なしで通ることを確かめる（code-generation） | NFR11、BR7.6、FS の 9節の D1、TM の Code Style |
| NFR11.2 | 管理者の印・停止の状態・失敗回数を書き換える口（C8 の setAdmin と失敗回数を戻す口、C1 の setSuspended・revokeAllRefreshTokens）を呼ぶのは `useradmin` の業務処理だけにする | ArchUnit の境界テストで、呼び出し元を `useradmin.service` に限ることを確かめる（code-generation） | NFR11、NFR1、BR7.3 |
| NFR9.6 | カバレッジ: `useradmin` の新しいパッケージは自動でパッケージごとの下限（行 80%・分岐 70%）の対象になる。U3 で手を入れる `auth.domain`（LockView の判定）・`auth.repository`（ロックの状態の行の読み出しと戻す更新）は、B1（U1）で `packagesJudgedByTotal` から外れる計画のため、U3 でもパッケージごとの下限を満たし続ける。`auth.service`（ログインの判定の待ち合わせの口）・`user` の各パッケージ・`audit.domain`・`audit.service` はすでに対象。`access.domain` は使うだけで本体を変えないため作業は付かない。ほかに一覧の残りのパッケージ（例: `common.web`）に実際に手を入れたときは、`team.md` のとおり下限を満たして一覧から外す。一覧を増やさない、計測の除外を増やさない | `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、パッケージごとの値を記録する（code-generation・build-and-test） | NFR9、TM の Testing Posture、FS の 7節、要点 20 |
| NFR9.7 | `team.md` の必須テストを書く: 管理者の印の変更（NFR1.2）、ロックの解除（解除の直後に正しいパスワードで入れる、解除の後の失敗回数の境界、ロックの状態の行が無い利用者は 409 USER_ADMIN_NO_CHANGE、時刻は注入した時計）、最後の管理者の保護（NFR4.1、業務処理の層を直接呼ぶテストと待ち合わせの重なり）、管理の API の認可（NFR1.1・NFR1.3）、要求の改ざん（NFR1.5）、管理の操作の監査（NFR9.3）、利用者の管理の漏えい（NFR3.1・NFR3.2）。あわせて、機能設計の申し送り（最後のページより後の page と全体が 0 件の一覧、待ち合わせの口で止めている間に2つ目の操作が排他の待ちに入ったことの確かめ）を入れる。テストの説明文は英語、単体は `XxxTest`・結合は `XxxIT`、テストの手伝いは `useradmin/testsupport` に置き、テストデータのメールアドレスは予約のドメイン（`example.com` など）だけにする | `./gradlew verify`（code-generation）。どの Bolt（B3・B4）に置くかはコード生成の計画で決める（FS の 8.1） | NFR9、TM の Testing Posture・Code Style、PM の Mandated（認証・認可・監査の失敗のテスト）、要点 21、FS の 8.2 |
| NFR9.8 | 性質ベースのテスト（jqwik）を、純粋な関数の拒否の判定の関数（操作の区分と事実の組から最初の理由を1つ返す）と、ロックの判定（LockView: ロック中か・解除の予定の時刻・戻せるか）に当てる。失敗時の乱数の種を記録して再現できるようにする | `./gradlew verify` の単体テスト（code-generation） | NFR9、TM の Testing Posture（性質ベースのテスト）、BR2.1・BR1.7 |

使う道具は既存のとおり: JUnit 5・Spring Boot Test・jqwik・ArchUnit・JaCoCo・SpotBugs ＋ FindSecBugs・Spotless（palantir-java-format）・Gitleaks・OSV-Scanner・k6。回数の確かめは既存のテストの部品（`auth/testsupport` の `SqlStatementCounter`）を使う。

## 選ばなかったもの

| 候補 | 選ばなかった理由 |
|---|---|
| 最後の管理者の保護を条件つきの更新（管理者の数を数える UPDATE）だけで守る | 拒否の理由ごとの判定と監査、確かめ直しの位置を1つのトランザクションで扱うため、既存の `PESSIMISTIC_WRITE` と待ちの上限 3 秒にそろえた行の排他にした（BR3.1）。成り立たないときの切り替え先は固定の1行の排他（NFR4.5） |
| 排他の待ちの上限を 1 秒以下に縮めて、BUSY の応答も 1 秒に収める | 既存の排他の上限とそろわなくなり、同時の操作で BUSY が出やすくなる。機能設計の承認の場の申し送りで 3000 ミリ秒のまま、BUSY だけを例外にした（NFR5.6） |
| 管理の API の要求の回数の制限 | 管理者だけが呼べる社内向けの API で、前の Intent でも回数の制限をしなかった（`security-requirements.md` の R1） |
| 一覧の検索のための全文検索・別の検索の仕組み | 想定 50 名で、1,000 名の悪い側でも部分一致の走査で目標を満たす見込み（NFR5.1 で確かめる）。索引の要否は NFR 設計で決める |
| 新しい指標・警報の部品 | 既存の HTTP の指標と警報、監査ログで足りる（Q4 A、`observability-requirements.md`） |
