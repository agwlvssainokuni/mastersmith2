# Tech Stack Decisions — U3 招待と登録の完了（u3-invitation）

U3 は新しい依存を足しません。既存の技術（コードの知識ベース `aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`、`gradle/libs.versions.toml`）と、U1 が足すメールの部品・テスト用の受け手の上で作ります（この段の設計の要点 24、依頼者の Looks correct）。新しい依存が無いため、ライセンスの確かめ（`team.md` の Code Style）は要りません。回数の制限の仕組みは Q2 A で作らないため、そのための依存も足しません。出典の略号は `performance-requirements.md` と同じ。

## 使う技術

| 用途 | 技術 | 選んだ理由 | 関係する要件 |
|---|---|---|---|
| API と業務処理 | Spring Boot（既存の版）・Spring Web MVC | 既存のアプリの土台。新しいパッケージ `invitation` を既存の層（web・service・domain・repository）に分ける | NFR4.1・NFR4.2 |
| 認証・認可 | Spring Security（既存の SecurityFilterChain、アクセストークンの認証、`SecurityRuleContributor` の差し込み口） | 招待の管理は `/api/admin/` の既定に乗せ、公開の2つの POST だけを差し込み口で足す（ADR-011） | NFR4.1〜NFR4.3 |
| トークンの乱数 | JDK の `java.security.SecureRandom` | 既存のリフレッシュトークン（`RefreshTokenValues`）と同じ作り。依存を足さない | NFR1.1 |
| トークンのハッシュ | JDK の `java.security.MessageDigest`（SHA-256） | 256 ビットの乱数には鍵・塩が要らず、JDK だけで作れる | NFR1.2 |
| パスワードのハッシュ | Spring Security の `BCryptPasswordEncoder`（cost 12、既存の `UserAccountConfig`）。U2 の `createUser` の中で使う | 既存の作成・ログインと同じ仕組み。cost は変えない | NFR6.4・NFR6.6・NFR5.2 |
| メールの描画と送信 | U1 の Mail（java-mustache-processor・Spring Boot のメールの自動設定・Angus Mail） | 送信の安全の決まりを U1 に任せ、U3 は依頼（C1）だけを行う（ADR-002） | NFR2.2・NFR8.2・NFR6.2 |
| DB アクセス | Spring Data JPA・Hibernate（`ddl-auto: validate`） | 既存の形。行の排他の作りは NFR 設計で決める | NFR9.8・NFR10.2 |
| スキーマの変更 | Flyway（`validate-on-migrate`） | V8 以降の前進のみの変更 | NFR10.1 |
| 内部DB | 組み込みの H2（単一インスタンス） | 既存の内部DB。テストも同じ組み込みの H2 で行う（`team.md` の Testing Posture） | NFR6.10・NFR5.3 |
| 定期の削除 | Spring の `@Scheduled`（cron） | 既存の `RefreshTokenCleanupJob` と同じ形 | NFR9.10 |
| 時刻 | 注入した `Clock` | 有効期限と保存期間の境界を実時刻に頼らずテストする（`team.md` の Testing Posture） | NFR1.3・NFR9.10 |
| 監査 | 既存の AuditLog（`AuditEventListener`・`AuditEventRecorder`） | 出来事の種類と失敗の理由を足すだけにする（BR8.4） | NFR9.4・NFR9.5 |
| 観測 | 既存の Micrometer・SLF4J の構造化ログ・Micrometer Tracing | 新しい指標を足さない | NFR6.8・NFR2.3 |

## テストの道具と品質の関門

| ID | 要件 | 確かめ方と持ち主の段 | 出典 |
|---|---|---|---|
| NFR9.6 | 新しいパッケージ `invitation`（下位のパッケージを含む）は自動でパッケージごとの下限（行 80%・分岐 70%）の対象になる。B2 で一覧から外した後の `packagesJudgedByTotal` を前提に、U3 で手を入れた一覧のパッケージがあれば一覧から外して下限を満たす。一覧を増やさない、計測の除外を増やさない | `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、パッケージごとの値を記録する（code-generation・build-and-test） | NFR9、`team.md` の Testing Posture、要点 25 |
| NFR9.7 | 有効の判定（BR3.3）・トークンの形の確かめ（BR3.2）・一覧のページの計算（BR2.3・BR5.2）は DB を使わない純粋な関数にし、jqwik の性質ベースのテストで確かめる。失敗時の乱数の種を記録する | `./gradlew verify`（code-generation） | NFR9、`team.md` の Testing Posture、BR2.3・BR3.2・BR3.3・BR5.2 |
| NFR9.11 | E2E（Playwright、`./gradlew e2eTest`）に、招待から登録の完了までの代表の流れを1本足す。`verify` と CI の外に置き、画面・認証に関わる変更を統合する前とリリースの前に手元で実行する。流れと、E2E で招待メールのリンクを取り出す方法は、画面の単位（u6-registration-ui）と基盤の設計で決める | E2E の実行（u6-registration-ui の code-generation・infrastructure-design） | NFR9、`team.md` の Testing Posture、機能設計の7節 |

使う道具は既存のとおり: JUnit 5・Spring Boot Test・jqwik・ArchUnit（既存の層と機能の境界のテストを緩めず、`invitation` → `user`・`mail`・`audit` の依存の向きを足す）・JaCoCo・SpotBugs ＋ FindSecBugs・Spotless（palantir-java-format）・Gitleaks・OSV-Scanner。メールのテストは U1 が足す SubEtha SMTP（テストの依存、Apache 2.0）を JVM の中で起動して実際に受ける（U1 の NFR9.1）。性能は既存の k6（`perf/k6`）に場面を足し、受け手に U1 の Mailpit（compose の profile `mail`、MIT、U1 の NFR11.1）を使う（`performance-requirements.md` の「測り方の決まり」）。

## 選ばなかったもの

| 候補 | 選ばなかった理由 |
|---|---|
| 回数の制限の仕組み（アプリのメモリで IP ごとに数える、Bucket4j などの部品） | 依頼者の決定（Q2 A）で設けない。単一インスタンスでは作れるが、前段のプロキシの後ろでは送信元の IP の取り方を決める必要があり、総当たりはトークンの強さで防げる。残る危険 R1（`security-requirements.md`） |
| トークンのハッシュに HMAC（鍵つき） | 256 ビットの乱数には辞書の攻撃が成り立たず、鍵の管理が増える。既存のリフレッシュトークンと同じ SHA-256 にする（NFR1.2） |
| 応答の時間の差をそろえる仕組み（ダミーの計算） | 時間の差から分かるのは 256 ビットのトークンを持つ人にとっての有効かだけのため（NFR3.2） |
| 送信を別のスレッド・キューに移す | ADR-009 で同じ要求の中で送り、結果を待って応答すると決まっている（NFR5.1） |
| 登録の完了のハッシュをトランザクションの前に計算する | 依頼者の決定（Q4 A）。契約 C2 と U2 の BR5.3 を変えずに済む（NFR5.2） |
| 有効期限の長さの上限 | 依頼者の決定（Q3 C）。残る危険 R2（`security-requirements.md`） |
| 新しい指標・警報の部品 | 既存の HTTP の指標・一覧の sendResult・監査ログで足りる（NFR6.8・NFR6.9・NFR9.5） |
