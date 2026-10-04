# Code Generation の質問（Intent 261004-safety-carryover）

## 計画の承認の前に確かめること

計画（`code-generation-plan.md`）の「依頼者に確かめたいこと」の D1〜D6。答えを計画に反映してから、計画の承認を伺う。

### D1. 救済の行の「当たった条件」を入れる列（FR1.6a）

A. `rejection_kind`（`failure_reason` は「成功なら空」の決まりで、結果が SUCCESS の救済の行と食い違うため）
B. `failure_reason`（組み合わせを列挙の値として足す）
X. Other (please specify)

[Answer]: A

### D2. 救済の置き場と、利用者の書き換えの口の決まり

A. `UserAccountService` がリポジトリを直接使って書き換え、境界テストは変えない。`useradmin` を通らない2つ目の経路になることを受け入れて説明に書く
B. 書き換えの口を呼び、`UserAdminBoundaryArchitectureTest` を緩める（承認が要る）
C. `useradmin.service` に置く
X. Other (please specify)

[Answer]: A

### D3. 二重の ERROR の直し方（FR4.2）

A. `application.yaml` で Tomcat の dispatcherServlet のロガーを OFF にする（設定の1行。応答を書き始めた後に外へ出た例外のログは無くなる）
B. `common.error.web` に例外を受けるフィルターを置く
C. FR4.1 の確かめの結果を見てから決める（Step 11 で止める）
X. Other (please specify)

[Answer]: A

### D4. ダイジェストを付ける範囲（FR7.2）

A. FR7.2 の5か所に加えて、同じイメージのほかの出現すべて（`perf/README.md` の jre 3か所と otel-lgtm 1か所、`README.md` の jre 4か所と jdk 1か所、`docker/hikari-pool.sh` の `JDK_IMAGE`）
B. FR7.2 の5か所だけ
X. Other (please specify)

[Answer]: A

### D5. 統合の前の E2E

A. `./gradlew e2eTest` を1回流し、救済の WARN が 0 件であることも確かめる（初期管理者のログインに関わるため、team.md の「認証に関わる変更は統合の前に流す」に当たる）
B. 流さない（要件の NFR4 の前提どおり）
X. Other (please specify)

[Answer]: B

### D6. 救済でのパスワードの書き直し

A. 条件 PASSWORD に当たらない救済（停止中・印なしだけ）でも、パスワードを設定の値で書き直す（FR1.2 の文言どおり）
B. 条件 PASSWORD のときだけ書き直す
X. Other (please specify)

[Answer]: A

### D7. 統合の前の E2E（D5: B と team.md の食い違い）

D5 で「流さない」を選びましたが、`team.md` の Testing Posture は「E2E は、画面・認証に関わる変更を統合する前と、リリースの前に手元で実行する」としています。救済は起動時に初期管理者（E2E がログインに使う利用者）の状態とパスワードを変えうるため、認証に関わる変更に当たると読めます。どうしますか？

A. team.md の決まりどおり、統合の前に E2E を1回流す（D5 を A に変える）
B. 流さない。この変更は画面に触れず、救済は E2E の初期管理者では働かない（有効な管理者でパスワードも一致する）ため「認証に関わる変更」に当たらないと読み、その読み方と team.md との差を計画と code-summary.md に記録する。E2E はリリース（配備）の前に流す
C. 統合の前は流さず、配備の前（Deployment Pipeline か Deployment Execution）で流す
X. Other (please specify)

[Answer]: B

## Plan Approval

計画（`code-generation-plan.md`、埋め込んだ Testing Contract を含む）と `unit-test-instructions.md` を、この内容で承認するか。

[Approval Fingerprint]: sha256:v3:26d01f3b6b18e965f5ff475aad33d5a8cfe93b29ae11460afd81e155767cc0dd
[Planned Source]: 5ff112dd1e7c2d18419c7e36def1d9890b8b4c4fadacde870eb5ab83e42aff00

- Approve Plan
- Request Changes

[Answer]: Approve Plan

## 生成の途中の確かめ

### G1. 計画の影響の範囲に無い既存のテスト2件の扱い（Step 9）

計画どおりの変更で、既存のテスト2件が落ちた: `SecretTypesTest`（`InitialAdminProperties.toString()` にメールアドレスがそのまま出ることを確かめている。NFR1 の伏せ字と食い違う）と `AuthSuspensionSecretLeakIT`（初期化が `existsByEmail` の代わりに `rescueInitialAdmin` を呼ぶため、`ENTER UserAccountService#existsByEmail(` の行が 0 件になる。趣旨は今も成り立つ）。

A. 2件のテストを直す（`SecretTypesTest` は伏せ字が出る形に、`AuthSuspensionSecretLeakIT` は `ENTER UserAccountService#rescueInitialAdmin(` の行で確かめる形に）。計画との差として code-summary.md に記録する
B. 初期化で `existsByEmail` を先に呼ぶ形に戻し、`AuthSuspensionSecretLeakIT` は変えない（DB の読み取りが1回増える。`SecretTypesTest` は A と同じ直しが要る）
X. Other (please specify)

[Answer]: A

### G2. `FilterExceptionErrorLogIT` が verify 全体の中でだけ落ちる（Step 16）

二重の ERROR は見立てどおり再現し（Step 11）、`application.yaml` の1行（D3: A）で単独では直った。ただし verify 全体では、同じ JVM で2つ目以降に起動した Tomcat の名前が `Tomcat-32` のように番号付きになり（Spring Boot 4.1.1 の仕組み、ソースで確かめた）、設定のロガーの名前と合わずにテストが落ちた。本番は Tomcat が1つで名前は `Tomcat` のため、設定は効く見込み。

A. `application.yaml` の設定はそのまま。テストだけを、動いている Tomcat の実際の名前に合わせて設定のレベルを当てる形にし、あわせて `[Tomcat]` の鍵が設定にあることを確かめる（計画との差としてテストの変更を記録）
B. 設定を名前に依らない広い `org.apache.catalina.core.ContainerBase` にする（Tomcat のほかのログ（起動の誤りなど）も消え、D3 の決定より広い）
C. このテストだけを別の JVM で流すよう Gradle の設定を変え、名前を常に `Tomcat` にする（ビルドの設定の変更）
X. Other (please specify)

[Answer]: A
