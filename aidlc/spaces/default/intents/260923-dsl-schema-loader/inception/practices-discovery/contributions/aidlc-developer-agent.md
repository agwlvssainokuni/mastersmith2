**Collaborator:** aidlc-developer-agent

## Contribution

開発の観点（命名、層の境界、エラー処理、ファイルの置き方、コードの書き方）で、初稿 `team-practices.md` の Code Style と関係する要確認の行を、実際のコードと突き合わせた。調べたのはファイルの読み取りと `git` の読み取りだけで、`./gradlew`・`npm`・`docker` は実行していない。`.env` と鍵ファイルは開いていない。

### 1. 実態と一致していること（初稿のままでよい）

| # | 基準 | 実態の根拠 |
|---|---|---|
| D-OK1 | ルートパッケージ `cherry.mastersmith`、機能ごとのパッケージ（`access`・`audit`・`auth`・`user`・`common`・`config`）と、その中の `web`・`service`・`domain`・`repository` の層 | `backend/src/main/java/cherry/mastersmith/` の構成。層が要らない機能はその層を持たない（`access` は `repository` なし、`audit`・`user` は `web` なし） |
| D-OK2 | 層の境界の ArchUnit による自動確認 | `backend/src/test/java/cherry/mastersmith/ArchitectureTest.java` が5つの規則（`web` から `repository` を使わない、`@Transactional` は `service` だけ、コントローラーがエンティティを返さない、コンストラクター注入のみ、Lombok なし）を確かめる。機能の間の境界は機能ごとの検査（`auth/AuthBoundaryArchitectureTest.java`・`audit/AuditBoundaryArchitectureTest.java`）に置く |
| D-OK3 | Problem Details に `code` を足す形、`@RestControllerAdvice` の1か所で変換 | `common/error/web/GlobalExceptionHandler.java`・`ErrorResponseFactory.java`（拡張の項目は `code` と `traceId`）。業務エラーは `common/error/domain/BusinessException.java` に `ProblemType` を持たせて投げる |
| D-OK4 | 新しい `code` を機能ごとの `ProblemTypeCatalog` に日英の説明つきで登録 | 型の定義は `<機能>/domain/XxxProblemTypes.java`、登録の Bean は `<機能>/service/XxxProblemTypeCatalog.java`（例: `auth`・`access`）。`code` は `^[A-Z][A-Z0-9_]*$`、重複は起動時に失敗 |
| D-OK5 | 秘密情報を持つ設定の型は `toString` で伏せ字 | `auth/service/AuthProperties.java`・`user/service/InitialAdminProperties.java` が `toString` を上書きしている。対象DB の接続設定もこれに合わせるのが自然 |
| D-OK6 | 設定の型は `record` ＋ `@ConfigurationProperties`、`mastersmith.<機能>.*` の名前、値は `${MASTERSMITH_...:既定値}` で環境変数から | `backend/src/main/resources/application.yaml` と各 `XxxProperties`。対象DB の `mastersmith.target-db.*` もこの形に乗る |
| D-OK7 | テストの置き方（`XxxTest` / `XxxIT`、同じパッケージ、`testsupport` の補助） | `backend/src/test/java/cherry/mastersmith/<機能>/testsupport/` が機能ごとにある |

### 2. 実態と食い違うこと（team.md の文言の直しを提案）

- **D-DIFF1 Spring の設定の置き場**: `team.md` は「Spring の設定は `config`」と書くが、実態は、全体に効く設定だけが `config`（`SecurityConfig`・`WebConfig`・`ObservabilityConfig` など）にあり、機能だけに効く設定は機能の中にある（`audit/service/AuditConfig.java`、`auth/service/AuthClockConfig.java`、`user/service/UserAccountConfig.java`、`access/web/AccessWebSecurityConfig.java`。設定の型 `XxxProperties` も同じ）。提案の文: 「全体に効く Spring の設定は `config` に、ひとつの機能だけに効く設定（`@Configuration`・`XxxProperties`）はその機能のパッケージの中の、それを使う層に置く」。対象DB の接続の設定をどこに置くかがこれで決まる。
- **D-DIFF2 画面の文言の言語**: `team.md` は「画面の文言は日本語」と書くが、実態は日本語と英語の2つをそろえている（`frontend/src/app/i18n/messages/ja.ts`・`en.ts`、既定は日本語、`resolveLanguage.ts`）。機能の文言は `registration.ts` の中に鍵を `<featureId>.` で始めて日英の両方を置く（`frontend/src/app/registry/types.ts`）。エラーの説明（`ProblemType` の `LocalizedText`）も日英。提案の文: 「画面の文言とエラーの説明は日本語と英語の両方を用意し、既定は日本語とする」。DSL の検証エラーの文言にも日英が要る。
- **D-DIFF3 ライセンスヘッダーの対象**: 検査の道具が見るのは Java・Gradle（Spotless）と TypeScript・JavaScript・CSS・HTML（`frontend/scripts/check-license-header.mjs`）だけだが、実態では道具の検査の外の `application.yaml`（`#`）・Flyway の SQL（`--`）・`logback-spring.xml` にも手でヘッダーを付けている。したがって初稿の要確認（DSL の YAML にヘッダーを付けるか）は、「YAML は既存どおり `#` で付ける」が実態の延長であり、決める必要があるのは **JSON（JSON Schema）の扱い** と **検査の道具の対象を広げるか** の2点に絞れる。JSON Schema はコメントを書けないが、`$comment` の項目に著作権とライセンスの1行を入れる手がある。

### 3. 今回の Intent で依頼者に確かめたい点（初稿に無いもの・初稿を具体にしたもの）

| # | 点 | 開発からの見立て |
|---|---|---|
| DQ-1 | **2つ目の DataSource の足し方**（初稿の Q-I を具体にしたもの） | 今の内部DB の `DataSource` は Spring Boot の自動構成で、明示の Bean は無い（`application.yaml` の `spring.datasource`）。`common/health/TimeBoundedDbHealthIndicator.java` は型だけで `DataSource` を受け取る。対象DB 用に `DataSource` 型の Bean を普通に足すと、自動構成が内部DB の `DataSource` を作らなくなり（Bean が既にあるときは身を引く）、JPA・Flyway・監査・ヘルスチェックが対象DB を指すか、注入があいまいで起動に失敗する。案: (a) 対象DB の `DataSource` を「既定の注入候補にしない」Bean（Spring の `@Bean(defaultCandidate = false)`）と名前付きの注入で足し、内部DB は自動構成のままにする、(b) 内部DB の `DataSource` も明示の Bean にして `@Primary` を付ける、(c) 対象DB の接続を Bean にせず、管理画面の操作のたびに作って閉じる（手動の操作で、同時の数が少ない前提）。どれでも「既存の部品が内部DB を指し続ける」ことを結合テスト（`XxxIT`）で確かめる。これは ArchUnit では確かめられない（Bean の配線は構造検査の対象外）ため、ArchUnit に加えるかの問いより、この結合テストを必須にすることを基準に書くほうがよい |
| DQ-2 | **対象DB の接続とトランザクション** | 名前の指定の無い `@Transactional` は内部DB の JPA のトランザクションになる。対象DB への操作が読み取り（メタデータ）だけなら、`@Transactional` を付けない形でよい。「適用」が対象DB に書き込むなら、対象DB 用のトランザクションの仕組みを名前付きで分け、`@Transactional("...")` のように必ず名前を書く決まりが要る。どちらかは要件定義で決まるため、基準としては「対象DB のトランザクションを使うときは必ず名前で指定する」を先に置けるか |
| DQ-3 | **バックエンドの機能パッケージの切り方** | 候補: (a) `targetdb`（対象DB への接続とメタデータの読み取り）と `dsl`（DSL の型・読み込み・検証・プレビュー・適用）の2つに分ける、(b) `dsl` の1つにまとめ、対象DB の読み取りを `dsl/repository` に置く。(a) なら、既存の `AuthBoundaryArchitectureTest` と同じく機能の間の向きを検査で決められる（例: `targetdb` は `dsl` に依存しない、`dsl` は `targetdb.repository` を直接使わず `targetdb.service` を通す）。機能の名前（パッケージ名、API の `/api/admin/<機能>/...`、画面の `featureId`）をそろえるかもあわせて決めたい |
| DQ-4 | **JSON Schema と DSL のファイルの置き場**（初稿の Q-J を具体にしたもの） | JSON Schema をサーバーだけが使うなら `backend/src/main/resources/`（例: `dsl/schema/`）に置く。画面でも同じ Schema で検証する（入力中の検証など）なら、「正はどちらに1つ置き、もう一方はどう受け取るか」（API で配る／ビルドで複写する）を決める必要がある。2か所に手で書くと食い違う。テスト用の DSL（YAML）は `backend/src/test/resources/` の下にパッケージと同じ構成で置くのが既存（`src/test/java` の置き方）の延長。Schema の版（`$id` と DSL の中の版の項目）の付け方は設計の段で決める |
| DQ-5 | **画面の機能の置き方** | 既存の決まり（`frontend/src/features/README.md`）では、新しい機能は `frontend/src/features/<featureId>/registration.ts` に `registration` を名前付きで置き、画面の骨組み（U1）のファイルは書き換えない。管理画面の中の DSL の画面を、既存の `features/admin` に足すのか、新しい `featureId`（例: `dsl`）で足すのかを決めたい。サイドバーの項目は `visibleWhen: 'ADMIN'`、画面は `access: 'ADMIN'` になる見込み（表示の制御であり、サーバー側の 401／403 の検査の代わりにはしない） |
| DQ-6 | **共通部品（U1 のファイル）の変更を許すか** | DSL の検証エラーを行・項目ごとに返すには、バックエンドの `common/error`（Problem Details の拡張の項目）と、画面の `frontend/src/shared/api-client/apiClient.ts`・`apiError.ts`（今は `{ kind, status, code? }` だけを渡す）の両方に手を入れる必要がある（コード知識ベースの C-6）。U1 のファイルは「書き換えない」前提で作られているため、今回の変更を「既存の応答の形と既存の画面を壊さない追加に限って許す」と基準に書くか、別の差し込み口を作るかを決めたい |
| DQ-7 | **層の構造検査を足すか**（提案） | 今の `ArchitectureTest` は `domain` から `service`・`web`・`repository` への依存や、`service` から `web` への依存を検査していない（今のコードには違反は無い。`domain` が Spring に依存するのは Spring Security の例外の型を使う2ファイルだけ）。DSL は `domain` に純粋な型（`record`）と検証を置く部分が大きく、性質ベースのテストの対象にもなるため、この2つの向きの規則を `ArchitectureTest` に足すと、層の崩れを早く見つけられる |
| DQ-8 | **DSL の型の置き方** | 既存では JPA のエンティティは `domain` に置いている（`user/domain/User.java`、`auth/domain/RefreshToken.java`）。DSL の型（YAML から読んだ内容）は JPA のエンティティにせず、`domain` の `record` にし、内部DB に保存する場合も保存用のエンティティとは分けるのがよい（エンティティを API の応答にしない決まりとも合う）。基準として書くか、設計の段に任せるか |

### 4. 初稿の要確認の行への補足

- 初稿の「DSL の検証エラーを行・項目ごとに…`@RestControllerAdvice` の1か所で組み立てる」: 賛成。ただし画面の側（DQ-6）も直す必要がある点を並べて書くとよい。DSL の検証エラーは業務エラー（4xx）なので、ログは WARN 以下・スタックトレースなしの既存の決まりがそのまま当たる。検証エラーの一覧に、投入された DSL の値そのもの（対象DB の接続情報が紛れ込む可能性）を載せないかも、設計の段で確かめる。
- 初稿の「投入された DSL は信頼できない入力…」: 賛成。既存の依存では YAML の読み取りに使えるのは推移依存の SnakeYAML 2.6 で、安全な読み込み（任意の型を作らない）と、別名の展開の数・入れ子の深さ・文字数の上限は読み込みの設定で指定できる。上限の値と、要求の本文の上限 1MB（`mastersmith.web.max-request-body-size`、コード知識ベースの C-7）との関係を設計の段で決める。
- 初稿の「内部DB 向けの SQL は H2 の方言を含むため、対象DB 向けに流用しない」: 賛成。対象DB の3種類で違う処理（型の対応、識別子の大文字・小文字）は、DB の種類ごとの小さな部品に分けると、単体テストと性質ベースのテストで分岐を確かめやすく、分岐カバレッジ 70% の下限にも届きやすい。

## Positions

- AGREE: Code Style のバックエンドの層・DTO・Lombok なし・Problem Details の各行は、`ArchitectureTest.java` と `common/error` の実装と一致している。
- AGREE: 対象DB のメタデータの読み取りを `repository` の層に置き、`web` から直接呼ばない行は、既存の `ArchitectureTest` の規則でそのまま検査できる。
- AGREE: 秘密情報を持つ設定の型の `toString` を伏せ字にする行は、`AuthProperties`・`InitialAdminProperties` の既存の実装と同じ形である。
- AGREE: DSL の検証エラーを Problem Details の拡張として1か所で組み立てる行は、既存の変換の仕組みと合う（画面側の変更は DQ-6 で補う）。
- AGREE: 投入された DSL を信頼できない入力として上限を設ける行は、既存の依存（SnakeYAML の読み込みの設定）で実現できる。
- OBJECT: 対象DB 用の `DataSource` の行（Q-I）は「ArchUnit にも加えるか」を問うより、Bean の足し方（DQ-1）を問い、既存の部品が内部DB を指し続ける結合テストを必須として書くべきである（配線は ArchUnit では確かめられない）。
- OBJECT: Code Style の「Spring の設定は `config`」は実態（機能だけに効く `@Configuration`・`XxxProperties` は機能の中）と食い違うため、D-DIFF1 の文に直すべきである。
- OBJECT: Code Style の「画面の文言は日本語」は実態（日英の2つ、既定は日本語）と食い違うため、D-DIFF2 の文に直すべきである。
- OBJECT: ライセンスヘッダーの要確認（Q-J）は、YAML には既に `#` のヘッダーを手で付けている実態があるため、問いを JSON の扱いと検査の道具の対象の2点に絞るべきである（D-DIFF3）。
- OBJECT: 初稿は今回の Intent のファイルの置き方（バックエンドの機能パッケージの切り方 DQ-3、JSON Schema を画面と共有するときの正の置き場 DQ-4、画面の `featureId` DQ-5、U1 の共通部品の変更の可否 DQ-6）を問いに含めておらず、設計の段より前に依頼者の判断が要る。
