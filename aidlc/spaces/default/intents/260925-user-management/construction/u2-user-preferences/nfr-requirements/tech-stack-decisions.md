# Tech Stack Decisions — U2 利用者のプリファレンスとパスワードの変更（u2-user-preferences）

U2 は新しい依存を足しません。既存の技術（コードの知識ベース `aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`、`gradle/libs.versions.toml`）の上で作ります（この段の設計の要点 19、依頼者の Looks correct）。新しい依存が無いため、ライセンスの確かめ（`team.md` の Code Style）は要りません。出典の略号は `performance-requirements.md` と同じ。

## 使う技術

| 用途 | 技術 | 選んだ理由 | 関係する要件 |
|---|---|---|---|
| API と業務処理 | Spring Boot（既存の版）・Spring Web MVC | 既存のアプリの土台。`/api/me/` の3本を既存の層（web・service・domain・repository）に置く | NFR4.1〜NFR4.3 |
| 認証・認可 | Spring Security（既存の SecurityFilterChain とアクセストークンの認証） | `/api/` の既定のログイン必須に乗せ、新しい仕組みを足さない | NFR4.1・NFR4.4 |
| パスワードのハッシュ | Spring Security の `BCryptPasswordEncoder`（cost 12、既存の `UserAccountConfig`） | 既存の作成・ログインと同じ仕組みで照合・ハッシュする。cost は変えない | NFR6.3・NFR6.6・NFR9.1 |
| DB アクセス | Spring Data JPA・Hibernate（`ddl-auto: validate`） | 既存の `UserRepository`・`AuditEventRepository` に列を足す | NFR5.1・NFR10.3 |
| スキーマの変更 | Flyway（`validate-on-migrate`） | V7 の前進のみの変更 | NFR10.1・NFR10.2 |
| 内部DB | 組み込みの H2 | 既存の内部DB。テストも同じ組み込みの H2 で行う（`team.md` の Testing Posture） | NFR10.2・NFR5.2 |
| 監査 | 既存の AuditLog（`AuditEventListener`・`AuditEventRecorder`） | 出来事の種類と対象の列を足すだけにする（ADR-008） | NFR9.4・NFR9.5 |
| 観測 | 既存の Micrometer・SLF4J の構造化ログ・Micrometer Tracing | 新しい指標を足さない | NFR6.8・NFR2.4 |

## テストの道具と品質の関門

| ID | 要件 | 確かめ方と持ち主の段 | 出典 |
|---|---|---|---|
| NFR9.6 | 手を入れる `packagesJudgedByTotal` のパッケージ（見込みは `user.domain`・`user.repository`・`user.service`・`audit.domain`・`audit.repository`・`audit.service`、応答を広げる `auth.service`・`auth.web`。ほかに実際に手を入れたものも含める）を一覧から外し、パッケージごとの下限（行 80%・分岐 70%）を満たす。一覧を増やさない、計測の除外を増やさない。前の記録で単独では下回っていた `audit.service`（行 77.2%）はテストを足して上げる | `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、パッケージごとの値を記録する（code-generation・build-and-test） | NFR9、`team.md` の Testing Posture、B2 の完了の条件（`inception/delivery-planning/bolt-plan.md`） |
| NFR9.7 | 必須のテストを書く: パスワードの変更（今のパスワードの確かめ、規則の境界、変更の後にほかの端末のリフレッシュトークンが使えること・アクセストークンが有効期限まで使えること）、認可（401・200・204）、監査（成功と失敗の必須の項目、NFR9.5）、秘密の漏えい（NFR2.1・NFR2.2）。DisplayName とパスワードの規則は jqwik の性質ベースのテストにし、失敗時の乱数の種を記録する。テストの説明文は英語、単体は `XxxTest`・結合は `XxxIT` | `./gradlew verify`（code-generation） | NFR9、`team.md` の Testing Posture、BR1.6、BR4.5 |

使う道具は既存のとおり: JUnit 5・Spring Boot Test・jqwik・ArchUnit（既存の層と機能の境界のテストを緩めない）・JaCoCo・SpotBugs ＋ FindSecBugs・Spotless（palantir-java-format）・Gitleaks・OSV-Scanner。性能は既存の k6（`perf/k6`）に場面を足す。

## 選ばなかったもの

| 候補 | 選ばなかった理由 |
|---|---|
| bcrypt の cost を 11 に下げる | 同時 10 件でパスワードの変更の成功を 1 秒に収められるが、総当たりへの強さが半分になる。依頼者の決定（Q1 A）で目標を 2 秒にし、cost は変えない |
| Argon2id への切り替え | 前の Intent の NFR（Q1 B）で bcrypt に決まっている。既存のハッシュとの混在の扱いが要り、この Intent の範囲の外 |
| 今のパスワードの誤りの回数を数える仕組み（アプリのメモリ、または内部DB の列） | 依頼者の決定（Q2 A）で制限を設けない。残る危険 R1（`security-requirements.md`） |
| 新しい指標・警報の部品 | 既存の HTTP の指標と監査ログで足りる（NFR6.8・NFR6.9・NFR9.5） |
