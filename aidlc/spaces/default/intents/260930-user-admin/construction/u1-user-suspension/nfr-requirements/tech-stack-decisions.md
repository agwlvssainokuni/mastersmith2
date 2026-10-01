# Tech Stack Decisions — U1 利用停止の状態と3つの入口（u1-user-suspension）

U1 は新しい依存を足しません。既存の技術（コードの知識ベース `aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`、`gradle/libs.versions.toml`）の上で作ります（この段の設計の要点 19、依頼者の Looks correct）。新しい依存が無いため、ライセンスの確かめ（`team.md` の Code Style）は要りません。出典の略号は `security-requirements.md` と同じ。

## 使う技術

| 用途 | 技術 | 選んだ理由 | 関係する要件 |
|---|---|---|---|
| 3つの入口と C1 の口 | Spring Boot（既存の版） | 既存のアプリの土台。ログインの照合（`LoginService`）・トークンの更新・アクセストークンの認証の既存の流れに分岐を足すだけにする | NFR1.1・NFR1.2 |
| 認証 | Spring Security（既存の SecurityFilterChain とアクセストークンの認証） | アクセストークンの認証の失敗を既存の 401 の入口で返し、入口を変えない | NFR2.1・NFR2.3 |
| トランザクション | Spring の `@Transactional`（C1 の `setSuspended`・`revokeAllRefreshTokens` は `Propagation.MANDATORY`、`isSuspended` は読み取りだけ） | 止める操作と同じトランザクションで停止とトークンの無効化を行い、巻き戻しで両方を戻す | NFR11.2 |
| DB アクセス | Spring Data JPA・Hibernate（`ddl-auto: validate`） | 既存の `UserRepository`・`RefreshTokenRepository` に、名前つきの引数の更新の問い合わせを足す。`@Modifying(clearAutomatically = true, flushAutomatically = true)` で、書いた後の読み取りが書いた値を返すようにする | NFR5.3・NFR9.4・NFR11.2 |
| スキーマの変更 | Flyway（`validate-on-migrate`） | `V9__u1_user_suspension.sql` の前進のみの変更 | NFR10.1・NFR10.2 |
| 内部DB | 組み込みの H2 | 既存の内部DB。テストも同じ組み込みの H2 で行う（`team.md` の Testing Posture） | NFR10.1・NFR5.3 |
| 監査 | 既存の AuditLog（`AuditEventListener`・`AuditEventRecorder`） | 既存のログインの失敗の出来事に理由 `ACCOUNT_SUSPENDED` を足すだけにする（契約 C7） | NFR6.1・NFR6.2 |
| 時計 | 既存の注入した `Clock` | まとめての無効化の時刻とアクセストークンの有効期限を、テストで動かせる時計から読む | NFR1.3・NFR9.3 |
| 観測 | 既存の Micrometer・SLF4J の構造化ログ・Micrometer Tracing | 新しい指標と警報を足さない | NFR5.5・NFR3.2 |

## テストの道具と品質の関門

| ID | 要件 | 確かめ方と持ち主の段 | 出典 |
|---|---|---|---|
| NFR9.5 | 手を入れる `packagesJudgedByTotal` のパッケージ（見込みは `auth.domain`・`auth.repository`・`access.domain`。ほかに実際に手を入れたものも含める）を一覧から外し、パッケージごとの下限（行 80%・分岐 70%）を満たす。見込みの根拠は手を入れるクラスで、`auth.domain` は `LoginFailureReason`・`TokenFailureReason`（停止の区分を足す）、`auth.repository` は `RefreshTokenRepository`（まとめての無効化の更新の問い合わせを足す）、`access.domain` は `AccessDeniedReason`（`USER_SUSPENDED` の変換の1行を足す）である（機能設計の 6節・8節の D1）。一覧にある `access.service`（`AccessDeniedEventPublisher` など）は、`USER_SUSPENDED` をアクセスの拒否の監査に出さない変換が `access.domain` の側で済むため手を入れず、一覧に残る。一覧の外のパッケージ（`user.domain`・`user.repository`・`user.service` と、`RefreshTokenRevocationService` を新しく置く `auth.service`）は、すでにパッケージごとの下限の対象で、手を入れた後もそのまま下限を満たす必要がある。外す対象は、生成の最後に、実際に `src/main` を変えたパッケージの一覧と突き合わせて決める。一覧を増やさない、計測の除外を増やさない。2026-10-01 の実測は、`auth.domain` が行 97.9%・分岐 100.0%、`auth.repository` が行 93.9%・分岐 50.0%、`access.domain` が行・分岐とも 100%。`auth.repository` の足りない分岐は `lockDummyForUpdate` の「空いたダミーの行が無い」側で、ダミーの行8つをすべて排他した状態を作るテストを足して上げる | `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、パッケージごとの値を記録する（code-generation・build-and-test） | NFR9、`team.md` の Testing Posture、機能設計の 6節・8節の D1、要点 20 |
| NFR9.6 | 回数の確かめは既存のテストの部品（`auth/testsupport` の `CountingPasswordEncoder`・`SqlStatementCounter`）を使う。テストの説明文は英語、単体は `XxxTest`・結合は `XxxIT`、テストの手伝いは `<機能>/testsupport` に置く。テストデータのメールアドレスは予約のドメイン（`example.com` など）だけにする | `./gradlew verify` とテストのレビュー（code-generation） | NFR9、`team.md` の Testing Posture・Code Style、要点 6 |

使う道具は既存のとおり: JUnit 5・Spring Boot Test・ArchUnit（既存の層と機能の境界のテストを緩めない）・JaCoCo・SpotBugs ＋ FindSecBugs・Spotless（palantir-java-format）・Gitleaks・OSV-Scanner。性能は既存の k6（`perf/k6/scenarios.js`）で、U3 の止める操作の場面に NFR5.2 の悪い側の条件を持たせる（場面そのものは U3 の持ち物）。

性質ベースのテスト（jqwik）を当てる純粋な関数は、この単位には見当たりません。失敗の区分の変換（`USER_SUSPENDED` を理由なしに変えるなど）は、列挙の値を尽くす単体テストで足ります（要点 19）。

## 選ばなかったもの

| 候補 | 選ばなかった理由 |
|---|---|
| 停止の状態をアプリのメモリに持つ（キャッシュ） | 止めた確定の後の次の要求から拒否する（NFR1.1・NFR1.2）ために、要求ごとに内部DB の今の値を読む。3つの入口はすでに利用者の要約を読んでおり、読み取りの回数は増えない（NFR5.1） |
| アクセストークンの失効の一覧（拒否リスト） | 既存の決定（PM の Decided: アクセストークンの失効の仕組みは持たない）のまま。停止中の拒否は要求ごとの利用者の要約の判定で足りる |
| 判定のトランザクションでの停止の読み直し | 受け入れた隙（NFR1.5）を塞げるが、ログインの読み書きの回数がパスワードの誤りと変わり、NFR2.2 と NFR5.1 に響く。機能設計の承認の場の決定（R-03）で足さない |
| 停止中のログインの応答時間を k6 で比べる、または差を合否にする | 時間の大半は bcrypt の照合で揺れが大きく、合否にすると不安定になる。依頼者の決定（Q1 A）で回数の確かめにした |
| まとめての無効化の時間の上限を結合テストで確かめる | `./gradlew verify` の中で時間を比べると負荷の高い CI で不安定になる。依頼者の決定（Q2 A）で件数だけを確かめ、時間は U3 の止める操作として Performance Validation で測る |
| 新しい指標・警報の部品 | 既存の HTTP の指標と p95 の警報、監査ログで足りる（NFR5.5） |
