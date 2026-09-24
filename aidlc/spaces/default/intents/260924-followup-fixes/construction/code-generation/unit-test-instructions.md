# テストの手順 — 前の Intent で後に回した小さな修正7件

## テストの道具と設定

- バックエンド（Java 25・Spring Boot 4.1.1）: JUnit 5・AssertJ・Mockito（`spring-boot-starter-test`）。単体テストは `XxxTest`（Gradle の `:backend:test`）、Spring と組み込みの H2 を起動する結合テストは `XxxIT`（`:backend:integrationTest`）で、名前で分けて実行する（`backend/build.gradle.kts` 129〜146 行）。置き場は `backend/src/test/java` の、対象と同じパッケージ。
- 画面（React＋TypeScript）: Vitest＋Testing Library（jsdom）＋user-event＋vitest-axe。テストは対象と同じ場所の `*.test.tsx`。設定は `frontend/vitest.config.ts`（カバレッジの下限は `thresholds`）。DSL の画面のテストは `frontend/src/features/dsl/testing/renderDsl.tsx`（言語の指定つき）で描く。
- 画面のテストは `vendor/make-you-chic-ui` を組み立てた結果（`dist`）を使うため、サブモジュールの固定先を変えた後は `./gradlew vendorBuild` を先に実行する。
- コンテナの設定: `docker/check-container-limits.sh`（Docker と Docker Compose v2、イメージ `mastersmith:local` が前提。`.env` は読まない）。
- 負荷の試験の台本: k6 のイメージ `grafana/k6:2.3.0` の `inspect`（読み込みの確かめだけ。流すのは Build and Test）。
- 新しい依存は足さない。`/v1/logs` の中身は protobuf のバイト列を UTF-8 の文字列として探して確かめる。
- テストの説明文（メソッドの名前・`@DisplayName`・`describe`・`it`）は英語で書く。テストのデータは日本語でよい（team.md の Testing Posture）。
- 前提: コンテナの実行環境（colima）が動いていること。この Intent に絞ったコマンドは組み込みの H2 だけを使い、対象DB のコンテナは使わない。統合の前の関門（`./gradlew verify`）は対象DB のコンテナを使う。

## この Intent のテストを実行するコマンド

プロジェクトのルートで実行する。どれもこの Intent で足す・変えるテストと、守るべき既存のテストのクラスとファイルだけを指定する。

```bash
# FR2（業務処理）: 単体テスト
./gradlew :backend:test --tests 'cherry.mastersmith.auth.service.LoginServiceTest'

# FR2（業務処理）: 結合テスト（再現のテストと、守るべき既存の結合テスト）
./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.auth.service.LoginConcurrencyIT' \
  --tests 'cherry.mastersmith.auth.repository.LoginAttemptStateRepositoryIT' \
  --tests 'cherry.mastersmith.auth.web.LoginApiIT' \
  --tests 'cherry.mastersmith.auth.service.AuthSettingsIT' \
  --tests 'cherry.mastersmith.auth.web.AuthSecretLeakIT' \
  --tests 'cherry.mastersmith.auth.service.RefreshConcurrencyIT' \
  --tests 'cherry.mastersmith.audit.service.AuditAuthenticationEventsIT' \
  --tests 'cherry.mastersmith.audit.service.AuditTraceIdIT' \
  --tests 'cherry.mastersmith.audit.service.AuditRollbackIT' \
  --tests 'cherry.mastersmith.audit.service.AuditWriteFailureIT' \
  --tests 'cherry.mastersmith.audit.service.AuditWriteTimingIT'

# FR1・FR4（ログの出力）: 単体テスト
./gradlew :backend:test \
  --tests 'cherry.mastersmith.common.observability.SanitizingLogRecordExporterTest' \
  --tests 'cherry.mastersmith.common.observability.JsonLogFormatTest'

# FR1・FR4（ログの出力）: 結合テスト（新しい OtlpLogExportIT と、標準出力・送り出しの既存の結合テスト）
./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.common.observability.OtlpLogExportIT' \
  --tests 'cherry.mastersmith.common.observability.ExternalExportIT' \
  --tests 'cherry.mastersmith.common.observability.TracingAndLoggingIT' \
  --tests 'cherry.mastersmith.audit.service.AuditNotInAppLogIT' \
  --tests 'cherry.mastersmith.audit.service.AuditSecretLeakIT'

# FR7（画面部品）: サブモジュールを組み立て直してから、DSL の2つのテストのファイルだけを流す
./gradlew vendorBuild
(cd frontend && npm test -- src/features/dsl/DslConfirmDialog.test.tsx src/features/dsl/DslAdminPage.test.tsx)

# FR5・FR6（コンテナの設定）: 確かめのスクリプト（事前に ./gradlew :backend:bootWar && docker compose build app でイメージを用意）
./docker/check-container-limits.sh

# FR6.3（R-03 の基準 1・2・4）: Git 管理外と見本のファイルの確かめ
git check-ignore -q .env.targetdb; echo "ignored=$? (0 を期待)"
git check-ignore -q .env.targetdb.example; echo "ignored=$? (1 を期待)"
grep -vE '^[[:space:]]*(#|$)' .env.targetdb.example | grep -vE '^[A-Z0-9_]+=$' || echo "値の入った行なし（期待どおり）"

# FR3（負荷の試験の台本）: 読み込めることの確かめ（流さない）
docker run --rm -e SCENARIO=dslMixed -v "$PWD/perf/k6:/scripts:ro" grafana/k6:2.3.0 inspect /scripts/scenarios.js
docker run --rm -e SCENARIO=loginSuccess -v "$PWD/perf/k6:/scripts:ro" grafana/k6:2.3.0 inspect /scripts/scenarios.js
```

- クラスの指定は、計画を書いた時点のファイルの場所（`backend/src/test/java` の下）で確かめた。この文書は承認の対象のため、承認の後に書き換えない。実行で違いが見つかったときは、`code-summary.md` に実際に使った指定を記録する。
- 不具合の再現の確かめ（計画の Step 4）: `LoginService.java` の変更だけを一時的に外した状態で、上の FR2 の結合テストのうち `LoginConcurrencyIT` だけを流し、失敗することを記録する。

統合の前の関門は、作業ブランチの全体に対して1回だけ行う（計画の Step 1 と Step 18）。テストのタスクが UP-TO-DATE で飛ばされないよう、テストの結果を消してから実行する。

```bash
./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
```

## テストの一覧（Minimal ＋ bugfix の再現のテスト）

Minimal の戦略（要件ごとに1つ、部品ごとに正常の場合を1つ）に、bugfix の再現のテストと、認証の変更に必須の失敗の場合のテスト（project.md の Mandated）を足した。認証とログの送り出しは失敗の場合の数が多いため、目安（5〜15 件）の上の方になる。

| 番号 | テスト（クラスまたはファイル） | 種類 | 確かめること | 要件 |
|---|---|---|---|---|
| T1 | `LoginServiceTest` の `a missing lock row is created in its own transaction and the login is decided again` | 単体 | 行が無いとき、判定のトランザクションでは書かず、別のトランザクションで `createIfAbsent` を呼んでから判定し直して成功する（既存の `createsMissingRow` を置き換える） | FR2.1 |
| T2 | `LoginServiceTest` の `a row created at the same time by another login is treated as existing` | 単体 | `createIfAbsent` が重複の例外を投げても、判定し直して成功する | FR2.1、FR2.3 |
| T3 | `LoginServiceTest` の `a wrong password for a user without a row counts as the first failure` | 単体 | 行が無い利用者のパスワードの誤りで、失敗の回数 1 を書き、`AUTHENTICATION_FAILED` になり、失敗の出来事を1回だけ知らせる | FR2.2 |
| T4 | `LoginServiceTest` の `a row still missing after creating it fails loudly` | 単体 | 作った後も行が無ければ `IllegalStateException` で失敗し、出来事を知らせない | FR2.3 |
| T5 | `LoginConcurrencyIT` の `simultaneous first logins of a user without a row all succeed` | 結合（再現） | 行を消した利用者に正しいパスワードで同時に 10 本。内部の失敗が0件、行が1つ、失敗の回数 0、監査の記録がログインの数と同じ | FR2.1、FR2.2 |
| T6 | `LoginConcurrencyIT` の `a login waiting for a row another login is creating succeeds after that commit` | 結合（再現） | 行を作りかけて確定していない別のトランザクションを置き、ログインが待ちに入ったことを期限つきで見張ってから確定させる。ログインは成功する | FR2.1、FR2.2 |
| T7 | `LoginConcurrencyIT` の `four simultaneous first failures without a row are counted without locking` | 結合（失敗） | 行が無い利用者への同時の失敗 4 回がすべて数えられ、ロックされない | FR2.1、FR2.2 |
| T8 | `LoginConcurrencyIT` の `five simultaneous first failures without a row lock the account and reject the right password` | 結合（ロック） | 同時の失敗 5 回でロックされ、その後の正しいパスワードも `AUTHENTICATION_FAILED` になる | FR2.1、FR2.2 |
| T9 | `SanitizingLogRecordExporterTest` の `personal values are replaced while the keys stay` | 単体 | `email`・`enteredEmail`・`sourceIp`・`userAgent` の値が `[REDACTED]` になり、キーは残る。文字列以外の値も伏せる | FR1.2 |
| T10 | `SanitizingLogRecordExporterTest` の `other attributes, the body and the trace context are kept` | 単体 | ほかの属性（例: `dsl.operation`）、本文、重大度、時刻、トレースの情報が変わらない。`flush`・`shutdown` を元に渡す | FR1.2、FR1.3 |
| T11 | `OtlpLogExportIT` の `key-value pairs are exported as log attributes` | 結合 | 送り出しを有効にし、受け手の `/v1/logs` に `dsl.operation` などのキーと値が届く | FR1.1 |
| T12 | `OtlpLogExportIT` の `personal values in exported logs are masked` | 結合 | 初期管理者の作成の INFO と、監査の書き込みの失敗の ERROR（実際のログイン）で、メールアドレス・IP・User-Agent の元の値が届いたログに無く、キーと `[REDACTED]` がある | FR1.2 |
| T13 | `OtlpLogExportIT` の `passwords, tokens and the signing key are never exported` | 結合 | ログイン・更新・ログアウトの後、パスワード・アクセストークン・リフレッシュトークン・署名鍵が `/v1/logs` と `/v1/traces` に無い | FR1.4、NFR1 |
| T14 | `JsonLogFormatTest` の `line breaks in a message are replaced so the message stays on one line` | 単体 | LF・CRLF・CR を含むメッセージで、`message` の値に改行が無く ` ⏎ ` で区切られ、出力が1行 | FR4.1、NFR4 |
| T15 | `JsonLogFormatTest` の `the stack trace field keeps its line breaks` | 単体 | 例外つきのログで `exception` の項目の改行が残る | FR4.3 |
| T16 | `DslConfirmDialog.test.tsx` の `names the close button in the display language` | 画面部品 | 日本語の表示で `閉じる`、英語の表示で `Close` | FR7.2 |
| T17 | `DslConfirmDialog.test.tsx` の `describes the dialog with its body text` | 画面部品 | ダイアログの説明（accessible description）に本文の文言が含まれる。既存の vitest-axe の検査も通る | FR7.3、NFR5 |
| T18 | `DslAdminPage.test.tsx` の `names the dismiss button of the alert Close in English` | 画面部品 | 英語の表示で知らせの閉じるボタンが `Close` で、押すと消える。既存の 535 行のテスト（日本語 `閉じる`）もそのまま通る | FR7.2、FR7.4 |
| T19 | `docker/check-container-limits.sh` の 1 | 設定の展開 | 変数なしで両方の compose の `mem_limit` が 2147483648、`768m` で 805306368 | FR5.1〜FR5.3 |
| T20 | `docker/check-container-limits.sh` の 5 | 設定の展開 | `app` の `env_file` が `.env` だけで、`environment` に `MASTERSMITH_SAMPLE_TARGETDB_` が無い。見本の対象DB の3サービスが `.env.targetdb` を読む | FR6.2 |
| T21 | R-03 の基準 1・2・4（上のコマンド） | Git と見本のファイル | `.env.targetdb` は Git 管理外、`.env.targetdb.example` はコミットでき、値は空 | FR6.1、FR6.3 |
| T22 | `k6 inspect`（上のコマンド）と台本を読んだ確かめ | 台本の読み込み | `dslMixed` の `loginLoop` が `exec.vu.idInTest` だけで利用者を選ぶ（R-01）。ほかの場面の選び方は変わらない | FR3.1、FR3.2 |

変更なしで通ることを確かめる既存のテスト（NFR6）: 上のコマンドに並べた `LoginAttemptStateRepositoryIT`・`LoginApiIT`・`AuthSettingsIT`・`AuthSecretLeakIT`・`RefreshConcurrencyIT`・`Audit*IT`・`ExternalExportIT`・`TracingAndLoggingIT`・`AuditNotInAppLogIT`・`AuditSecretLeakIT`、`DslAdminPage.test.tsx` の既存のすべてのテスト、既存の vitest-axe の検査。ほかのテストは統合の前の関門で流す。

この段で行わない確かめ:

- FR3.1 の「流したときの記録」（R-01）と FR8.2 の k6 の試験: Build and Test の段で、使い捨ての環境で行う。合格の基準は、k6 の出力の `loginLoop-user` の行の数が `VUS` と同じで利用者の重複が0件であることと、ログインの `checks` の率が 1（500 が0件）であること。
- FR4.1 の起動のログでの確かめ（Hibernate の案内が1行1件、ロガー名の記録）: Build and Test の段で、使い捨ての環境を起動するときに行う。
- FR6.2 の、値が入った `.env` を読んだ状態でのアプリの環境変数の確かめ: Deployment Execution の段で、`.env` の移し替えと一緒に行う（名前だけを見て、値は表示しない）。

## カバレッジの下限

- バックエンドは JaCoCo で行 80%・分岐 70%。全体の合計に加えて、新しく作るパッケージごとにも当てる。この Intent の新しいクラス（`SanitizingLogRecordExporter`・`SingleLineMessageJsonProvider`）は既存のパッケージ `cherry.mastersmith.common.observability` に置くため、新しいパッケージは作らず、全体の合計で判定される（`backend/build.gradle.kts` の `packagesJudgedByTotal`）。この一覧は変えない。
- 画面は `@vitest/coverage-v8` で行 80%・分岐 70%（`frontend/vitest.config.ts` の `thresholds`）。
- 下限を下げない。計測の除外を増やさない（team.md の Testing Posture、NFR2）。下回ったときは設定を触らずにテストを足し、足りないときは依頼者に報告する。
- 新しいクラスは、それぞれの単体テスト（T9・T10、T14・T15）で主な分岐（伏せるキーとそれ以外、改行の3つの形と改行なし）を通す。
- 数字は、計画の Step 1（変更の前）と Step 18（変更の後）の `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` の実測だけを記録する。

## 差し替えと待ち合わせの方針

- 単体テスト（`LoginServiceTest`）は、今のとおり `UserAccountService`・`LoginAttemptStateRepository`・`RefreshTokenRepository`・`ApplicationEventPublisher`・`PlatformTransactionManager` を Mockito で差し替え、時計は `Clock.fixed` を使う。トランザクションの回数と順番（判定 → 作成 → 判定）を確かめる。
- 結合テスト（`LoginConcurrencyIT`）は差し替えを使わず、本物の Spring と組み込みの H2 で行う。同時の開始は `CountDownLatch` でそろえ、待ちは `Future.get` と `await` の期限（例: 60 秒）つきにする。T6 の「ログインが待ちに入った」ことは、ログインのスレッドの状態を期限つきで見張って判定し、固定の時間の `sleep` に頼らない。スレッドの数は同時の本数（10）より多くし、接続プールの上限（30）の内側に収める（監査は確定の後にもう1本使うため、10 本で最大 20 本）。
- `SanitizingLogRecordExporterTest` は、`SanitizingSpanExporterTest` と同じく、OpenTelemetry の SDK（`SdkLoggerProvider` と単純な処理の部品）で本物の記録を作り、受け取った記録を覚えるだけの送信の部品を包んで確かめる。
- `OtlpLogExportIT` の送り先は、テストの中で起動する HTTP の受け手（受け取った要求を覚えるだけ）にする。外部のサービスには接続しない。監査の書き込みの失敗は `FailingAuditEventRepositoryConfig`（追記の失敗）で起こす。待ち行列に残ったログは `SdkLoggerProvider.forceFlush()` で送らせてから確かめる。
- 画面のテストは、既存の `fakeApi` と `renderDsl`（言語の指定）を使う。
- 実時刻と `sleep` には頼らない。不安定なテストは放置せず、原因を直すまで統合しない（team.md の Testing Posture）。

## テストのデータ

- 利用者は、テストごとに `concurrent-<UUID>@example.com` などの重ならないメールアドレスで作る。行が無い状態は、利用者を作った後に `DELETE FROM login_attempt_states WHERE subject_id = ?` で作る（既存の `AccessTokenApiIT` と同じ書き方）。テストの実行の順番に依存させない。
- 結合テストの DB は、既存の `TestDatabase.register` が作る一時のディレクトリの組み込みの H2 で、テストのクラスごとに別になる。
- パスワード・署名鍵などの秘密の値は、テストの中で乱数（`TestDatabase.randomSecret()`・`TestSigningKeyEnvironmentPostProcessor` など既存の補助）で作り、ソースに固定の値を書かない。T13 はこの値が送られたログに無いことを確かめる。
- ログの送り出しの確かめに使うメールアドレス・IP・User-Agent は、例示用の値（`example.com` のアドレス、`198.51.100.0/24` の IP、テスト用の文字列）にする。
- FR6 の DB の初期化の確かめ（計画の Step 14）で作る一時の `.env.targetdb` は、乱数の値（表示しない）を入れて権限 600 で作り、確かめが終わったら消す。起動した見本の対象DB は別のプロジェクト名で起動し、`down -v` でボリュームごと消す。配備したアプリ・見本の対象DB・`.env` には触れない。
