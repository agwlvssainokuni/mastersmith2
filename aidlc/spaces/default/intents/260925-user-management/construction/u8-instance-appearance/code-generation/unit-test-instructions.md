# Unit Test Instructions — U8 インスタンスの見た目の設定（u8-instance-appearance）

U8 のテストの道具・実行のしかた・カバレッジの目標・差し替えの方針・テストのデータの扱いを示す。テストの量は Standard（部品ごとに 5〜8 件の単体テストと、境界の結合テスト）。手順の番号は `code-generation-plan.md` の Step を指す。パスはリポジトリのルートからの相対パス。

## 1. 道具と設定

| 用途 | 道具 | 設定の場所 |
|---|---|---|
| テストの実行 | JUnit 5（Spring Boot の BOM の版）、AssertJ、Spring Boot Test | `backend/build.gradle.kts` の `tasks.test`（名前が `*Test`）と `integrationTest`（名前が `*IT`）。テストの JVM のヒープは既存の 1g |
| 性質ベースのテスト | jqwik（既存） | `backend/src/test/resources/junit-platform.properties`（失敗した例の記録は `build/jqwik-database`）。失敗時の乱数の種はテストの出力に残る |
| 構造の検査 | ArchUnit（既存） | 新しい `appearance/AppearanceBoundaryArchitectureTest` を足す。既存の `ArchitectureTest` と機能ごとの境界テストはそのまま使う（緩めない） |
| 内部DB | 組み込みの H2（既存の `backend/src/test/java/cherry/mastersmith/common/testsupport/TestDatabase.java`） | U8 は内部DB を使わないが、Spring を起動する結合テストはアプリ全体の文脈として一時ディレクトリの H2 で起動する。コンテナは使わない（`team.md` の Testing Posture） |
| 起動の設定の明示 | `SpringApplicationBuilder` と起動の引数（既存の `targetdb/config/TargetDbStartupIT.java` と同じ形） | `AppearanceStartupIT` は2項目を起動の引数で明示し、開発者の環境変数の影響を受けない |
| HTTP の確かめ | 既存の `common/testsupport/HttpTestClient.java`・`auth/testsupport/AuthApi.java`・`auth/testsupport/AuthTestTokens.java` | 使えるトークンは実際のログインで、期限切れ・改ざんのトークンは既存の補助で作る |
| ログの確かめ | 既存の `common/testsupport/LogEvents.java`（単体）、`OutputCaptureExtension` と `common/testsupport/JsonLogRecords.java`（起動の結合テスト） | — |
| 監査の行の確かめ | 既存の `audit/testsupport/AuditRows.java` | GET の前後の件数を比べる |
| 接続を借りた回数 | 新しいテストの支え `appearance/testsupport/ConnectionAcquireCounter.java` | Step 3 の判定で、`MeterRegistry` の `hikaricp.connections.acquire`（タグ `pool=mastersmith-db`）か、テストの設定の `BeanPostProcessor` で `DataSource` を包んで数える形のどちらかにする（5節） |
| カバレッジ | JaCoCo（既存） | `backend/build.gradle.kts` の `jacocoTestReport`・`jacocoTestCoverageVerification` と `packagesJudgedByTotal`・`coverageExclusions` |

新しいテストの依存と、新しいテストの設定のファイルは足さない。既存の `test`・`integrationTest` のタスクと名前の決まり（`XxxTest`・`XxxIT`）をそのまま使う。

## 2. この単位のテストの実行のしかた

リポジトリのルートで実行する。どれも U8 のパッケージ `cherry.mastersmith.appearance` と、U8 が決まりを足すフィルターの連鎖の既存の確かめ（全体の構造の検査 `ArchitectureTest`、差し込み口の検査 `common.security`、差し込み口の順番の結合テスト `config.SecurityExtensionIT`、`/api/**` の既定の結合テスト `access.web.ApiDefaultAccessIT`）だけに絞る。

単体テスト（`*Test`。設定の型、判定の関数と性質ベースのテスト、保持と警告、差し込み口の order、境界の構造の検査、全体の構造の検査、差し込み口の検査）:

```bash
./gradlew :backend:test --tests 'cherry.mastersmith.appearance.*' --tests 'cherry.mastersmith.ArchitectureTest' --tests 'cherry.mastersmith.common.security.*'
```

結合テスト（`*IT`。Spring と組み込みの H2 を起動する公開の範囲・応答・ヘッダー・監査なし・接続を借りないことと、起動の設定ごとの値と警告のログ）:

```bash
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.appearance.*' --tests 'cherry.mastersmith.config.SecurityExtensionIT' --tests 'cherry.mastersmith.access.web.ApiDefaultAccessIT'
```

公開の範囲の結合テストだけ（Step 11）:

```bash
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.appearance.web.AppearanceApiIT'
```

起動の設定ごとの値と警告のログだけ（Step 11）:

```bash
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.appearance.web.AppearanceStartupIT'
```

構造の検査だけ（新しい境界テストと、変えていない全体の決まり）:

```bash
./gradlew :backend:test --tests 'cherry.mastersmith.appearance.AppearanceBoundaryArchitectureTest' --tests 'cherry.mastersmith.ArchitectureTest'
```

最初のテストより前の確かめ（Step 3）: 上の単体テストと結合テストのコマンドを変更の前の状態で実行し、既存のテストが通ることを確かめる。U8 のテストがまだ無くても、`ArchitectureTest`・`common.security.*`・`SecurityExtensionIT`・`ApiDefaultAccessIT` に当たるため、Gradle の「当たるテストが無い」の失敗にならない。

- テストの件数を報告するときは、UP-TO-DATE で飛ばされないよう `:backend:cleanTest` または `:backend:cleanIntegrationTest` を先に付けて実行し、実測の数字だけを報告する（`project.md` の Testing Posture）。例: `./gradlew :backend:cleanTest :backend:test --tests 'cherry.mastersmith.appearance.*' --tests 'cherry.mastersmith.ArchitectureTest' --tests 'cherry.mastersmith.common.security.*'`
- U8 のテストはコンテナを使わないため、コンテナの実行環境（colima）が無くても動き、飛ばされない。パッケージごとのカバレッジの実測（Step 16）と統合の前の `./gradlew verify`（Step 1・Step 17）は対象DB のテストを含むため、colima を動かし、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡して実行する（渡さないと対象DB のテストが SKIPPED になり、パッケージごとの下限の判定が崩れる）:

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
```

- 統合の前の E2E（Step 17、`code-generation-plan.md` の9節の決定 4）は単位のテストではなく、README の「ビルドした WAR での画面の確認（E2E）」の手順で、先に Mailpit を起動してから既存の `./gradlew e2eTest` を流す（U8 の E2E は足さない）:

```bash
docker compose --profile mail up -d mailpit
./gradlew e2eTest
```

- 負荷の試験の台本（Step 14）は `./gradlew verify` と CI の外で、この段では流さない。k6 で読み込めることの確かめは Build and Test が `k6 inspect --include-system-env-vars` で行う（`code-generation-plan.md` の「Build and Test に引き継ぐこと」）。

## 3. テストの一覧（Standard の量）

| 部品 | 単体（`*Test`） | 結合（`*IT`） |
|---|---|---|
| 設定の型（`appearance.config`） | `AppearancePropertiesTest`（4〜5 件） | `AppearanceStartupIT` で起動の結び付けを確かめる |
| 判定の関数（`appearance.service`） | `AppearanceResolverTest`（8 件＋jqwik 3 件） | — |
| 保持と警告（`appearance.service`） | `AppearanceServiceTest`（6〜7 件） | `AppearanceStartupIT`（3 件） |
| API と公開の決まり（`appearance.web`） | `AppearanceSecurityContributorTest`（2〜3 件） | `AppearanceApiIT`（8〜10 件） |
| 構造の検査 | `AppearanceBoundaryArchitectureTest`（4 件）、既存の境界テストをそのまま通す | — |

どのテストも、成功の場合と、少なくとも2つの失敗・境目の場合を含める（`phases/construction.md` の Testing Standards）。テストの説明文（`@DisplayName`・メソッド名）は英語で書く。テストのデータは日本語でよい。テストのクラスは対象と同じパッケージに置く（例: `backend/src/test/java/cherry/mastersmith/appearance/web/AppearanceApiIT.java`）。

各テストの中身:

| テスト | 確かめること | 要件 |
|---|---|---|
| `AppearancePropertiesTest` | 2項目を文字列のまま受ける、無いと `null` または空、`red`・`mono`・` Green `・記号を含む値でも結び付けが失敗しない、環境変数の形の名前からも結び付く | BR1.7、NFR9.1 |
| `AppearanceResolverTest` | functional-spec.md W1 の判定の例の表のすべて、全角の空白・タブの前後の除去、実行環境のロケール（例 トルコ語）を変えても同じ結果、すべての許される値が通る。性質: 任意の文字列で結果が常に許される値 / 大文字・小文字と前後の空白の揺れは同じ結果 / 許される値に一致しない空でない値は既定で警告あり | BR1.1〜BR1.5、NFR9.2・NFR9.8 |
| `AppearanceServiceTest` | 設定なし・許される値・許されない値2つ・片方だけ許されない値で、採る値と WARN の件数（0・0・2・1）、WARN のキー（`property`・`defaultValue`・`allowedValues`）、スタックトレースなし、`red`・`mono` の文字列がログに無い、`current()` を何回呼んでも WARN が増えず同じインスタンス | BR1.3〜BR1.6・BR2.1、NFR6.2・NFR9.4 |
| `AppearanceSecurityContributorTest` | order が 410、110・210・100・150・200・250・310 と一緒に `SecurityExtensionValidator.sortedContributors` に渡して重ならず、310 の後に並ぶ | NFR4.7 |
| `AppearanceApiIT` | GET・トークンなし 200 と2項目（blue・sans）、POST・トークンなし 401 / `AUTHENTICATION_REQUIRED`、POST・PUT・DELETE・使えるトークン付き 405 / `METHOD_NOT_ALLOWED` と `Allow` に GET、HEAD・トークンなし 401、GET・期限切れと改ざんのトークン付き 401、GET・使えるトークン付き 200、`/api/appearance/` のトークンなしの GET 401、ヘッダー（`Cache-Control: no-store`・CSP の `font-src 'self'`・`nosniff`・`DENY`）、GET の前後で監査の表の件数が同じ、トークンなしの GET の前後で接続を借りた回数が増えない（先に数え方が働くことを確かめる）。使えるトークン付きの HEAD の扱いを記録する | NFR4.1〜NFR4.5・NFR4.8・NFR5.2・NFR9.5 |
| `AppearanceStartupIT` | `red`・`mono` で起動が止まらず blue・sans、WARN ちょうど2件・スタックトレースなし・キーがそろい・ログと本文に `red`・`mono` が無い、GET を3回送っても WARN が増えない。設定なしで blue・sans・WARN 0件。`GREEN`・`Serif` で green・serif・WARN 0件 | BR1.1〜BR1.4・BR1.7・BR2.1・BR3.1、NFR4.5・NFR9.1・NFR9.4、CR2 のサーバー側の確かめ方 |
| `AppearanceBoundaryArchitectureTest` | DB アクセスに依存しない、ほかの機能（`config` を含む）に依存しない、ほかの機能から依存されない、`appearance.web` が `appearance.config` を直接使わない | NFR5.1 |

`team.md` の認証・認可・監査の必須のテストのうち U8 に当たるもの:

| 必須のテスト | 確かめるテスト |
|---|---|
| 認可: 未認証・管理者でない・管理者をサーバー側のテストで確かめる。U8 の公開の API では「未認証でも GET は 200」「GET 以外は未認証 401・使えるトークン付き 405」「使えないトークン付きの GET は 401」が当たる（管理者の区別は無い） | `AppearanceApiIT` |
| 監査ログ: U8 の読み取りは監査の対象ではない（BR3.5）。記録しないことを確かめる | `AppearanceApiIT` |
| 秘密情報の漏えい: U8 は秘密を扱わないが、設定された元の文字列がログ・応答に出ないことを確かめる | `AppearanceServiceTest`・`AppearanceStartupIT`・`AppearanceApiIT` |

## 4. カバレッジの目標

- 全体: 行 80% 以上・分岐 70% 以上（既存の `jacocoTestCoverageVerification`）。
- パッケージごと: 新しい `appearance.service`・`appearance.web` のそれぞれで行 80%・分岐 70% 以上（`team.md` の Testing Posture、NFR9.7）。`appearance.config` は `AppearanceProperties` だけで、既存の計測の除外（`coverageExclusions` の `**/*Properties.class`、設定値だけのクラス）に当たるため、報告にパッケージとして出ない（除外を足したのではない。`code-generation-plan.md` の8節）。
- `code-generation-plan.md` の9節の決定 3 のとおり、説明文を書き直して Step 16 で `packagesJudgedByTotal` から外す既存のパッケージ `common.security`・`config`・`access.web` も、それぞれ行 80%・分岐 70% 以上。届かなければテストを足し（参考の値では `config` の分岐 73.3% が下限に近い）、それでも届かなければ生成を止めて依頼者に諮る。これらのパッケージの既存のテスト（`common.security.*`・`config.SecurityExtensionIT`・`access.web.ApiDefaultAccessIT`）は2節の単位のコマンドに含めてあるが、パッケージの値はほかの機能のテストも通る分を含むため、判定は `./gradlew verify` の実測で行う。
- 一覧に足さない。一度外したパッケージは戻さない。U8 のために計測の除外を足さない。
- 単位だけのカバレッジは、単位のテストを実行した後に報告を作って見る:

```bash
./gradlew :backend:cleanTest :backend:cleanIntegrationTest :backend:test --tests 'cherry.mastersmith.appearance.*' --tests 'cherry.mastersmith.ArchitectureTest' --tests 'cherry.mastersmith.common.security.*' :backend:integrationTest --tests 'cherry.mastersmith.appearance.*' --tests 'cherry.mastersmith.config.SecurityExtensionIT' --tests 'cherry.mastersmith.access.web.ApiDefaultAccessIT' :backend:jacocoTestReport
```

  報告は `backend/build/reports/jacoco/test/html/` の各パッケージのページ。ほかの機能のテストが通る分を含まないため、この値は目安で、下限の判定は `./gradlew verify` の段（`jacocoTestCoverageVerification`）で行い、値は Step 16・Step 17 で実測して記録する。

## 5. 差し替え（モック・スタブ）の方針

- **差し替えは最小にする**: U8 の部品は依存がほぼ無い（`AppearanceService` は `AppearanceProperties` だけ、`AppearanceController` は `AppearanceService` だけ）。単体テストでは Mockito を使わず、`AppearanceProperties` の値を直接作って渡す。`AppearanceController` の単体テストは作らず、結合テストで確かめる。
- **フィルターの連鎖はモックにしない**: 公開の範囲（401・405・200）は、本物の `SecurityConfig` と差し込み口（`auth` の 110・`access` の 210・U8 の 410）で起動した Spring の文脈に、実際の HTTP で要求を送って確かめる。`/api/**` を公開にするテスト用の決まり（`access/testsupport/PublicApiTestRules`、`mastersmith.test-fixture.public-api`）は有効にしない。
- **トークン**: 使えるアクセストークンは実際のログインで得る（既存の `AuthApi`）。期限切れ・改ざんのトークンは既存の `AuthTestTokens` の形で作る。
- **ログ**: 単体テストは `LogEvents` で `AppearanceService` のロガーを捕まえる。起動の結合テストは `OutputCaptureExtension` の JSON のログを `JsonLogRecords` で読み、`AppearanceService` のロガーの WARN だけを数える（Spring の文脈ごとに Bean が作られるため、テストのクラスの中で起動を分け、1回の起動の中の件数を数える）。
- **接続を借りた回数**（Step 3 の判定、`code-generation-plan.md` 2.2）:
  - まず、テストの文脈の `MeterRegistry` に `hikaricp.connections.acquire`（タグ `pool=mastersmith-db`）のタイマーがあり、`DataSource#getConnection` を1回呼んで閉じると件数が1以上増えることを確かめる。成り立てば、トークンなしの GET の前後でタイマーの件数を比べる。
  - どちらかが成り立たなければ、テストの設定の `BeanPostProcessor` で `DataSource` を包み、`getConnection` の回数を数える（本番のコードは変えない）。
  - どちらの形でも、同じテストの中で「数え方が働く」ことの確かめ（`getConnection` で数が増える）を先に置き、「GET で増えない」が空振りの確かめにならないようにする。
  - 比べる間にほかの要求や定期の処理（`RefreshTokenCleanupJob`、既定は毎日 3 時 30 分）が混ざらないよう、テストでは定期の削除の cron を止める値（`mastersmith.auth.refresh-token-cleanup.cron=-`）を渡し、比べる前に起動の直後の処理が終わっていることを待ち合わせる（`Thread.sleep` に頼らない）。
- **ロケール**: `AppearanceResolverTest` で実行環境のロケールに左右されないことを確かめるときは、`Locale.setDefault` を一時的に変え、`finally` で必ず元に戻す。
- **時刻**: U8 は時刻に依存しない。

## 6. テストのデータ

- **設定の値**: 許される値（blue・green・purple・orange、sans・serif）と、その揺れ（`Green`・` green `・`GREEN`・`Serif`・全角の空白やタブの前後）、許されない値（`red`・`blue-ish`・`mono`・`ｂｌｕｅ`（全角）・記号を含む値）、空・空白だけ・設定なし。
- **見分けやすい許されない値**: ログ・応答に元の文字列が出ないことを確かめるときは、ほかの文字列と重ならない値（例 `red`・`mono`、または `appearance-leak-check` のような目印の値）を使う。秘密に見える値（パスワードの形の文字列など）は Gitleaks に当たらない仮の値にする。
- **利用者**: 使えるトークン付きの確かめに要る利用者は、テストの中で既存の補助（`AdminTestUsers` などの形）で作る。メールアドレスは `example.com` などの予約されたドメインだけを使う。前のテストの利用者・監査の行に頼らない。
- **内部DB**: Spring を起動する結合テストは、既存のとおりテストのクラスごと（起動ごと）に一時ディレクトリの H2 を使う（`TestDatabase`）。

## 7. 性質ベースのテストの種

- jqwik の失敗時の乱数の種は、テストの出力（失敗の詳細）に出る。再現するときは、その種を `@Property(seed = "...")` に一時的に書いて同じ単位のコマンド（2節）で実行し、直した後に外す。
- 対象は純粋な関数 `AppearanceResolver` だけ（`team.md` の Testing Posture、NFR9.8）。性質は3つ: 任意の文字列（`null` を含む）で結果が常に許される値のどれか / 許される値の大文字・小文字と前後の空白の揺れは同じ結果 / 許される値に一致しない空でない値は既定で警告あり。
