# 結合テストの手順（integration-test-instructions）

Test Strategy は **Standard** のため、本書（単位をまたぐ境界の結合テスト）を作る。単体テストは各単位の Code Generation の `unit-test-instructions.md` が受け持ち、本書はそれを置き換えない。

対象は、Spring の文脈と組み込みの H2 を起動して境界を通す `*IT` のテストと、ビルドした WAR をブラウザから通す E2E である。

## 1. 枠組みと設定

| 種類 | 道具 | 置き場所 | 名前の決まり |
|---|---|---|---|
| バックエンドの結合テスト | JUnit 5 ＋ Spring Boot Test（`@SpringBootTest(webEnvironment = RANDOM_PORT)`）＋ 組み込みの H2 ＋ Flyway | `backend/src/test/java/`（対象と同じパッケージ） | `XxxIT` |
| 画面の結合テスト（部品の組み合わせ） | Vitest ＋ Testing Library（jsdom）＋ user-event ＋ MSW 相当の差し替え | `frontend/src/**/` （対象と同じ場所） | `*.test.tsx` |
| E2E（ビルドした WAR ＋ ブラウザ） | Playwright（Chromium） | `frontend/e2e/` | `*.e2e.ts` |

- **内部DBはコンテナを使わない**。本番と同じ組み込みの H2 をテストごとに立て、Flyway で同じスキーマを当てる（team.md の Testing Posture の読み替え。Testcontainers は後続 Intent の業務DB用）。
- 時刻に依存する処理（トークンの有効期限、ロックの解除、削除の猶予）は注入できる `Clock` から現在時刻を取り、`sleep` や実時刻に依存しない。
- テストごとにデータを用意して巻き戻し、実行順に依存させない。
- Gradle のタスクは単体（`:backend:test`、`*Test`）と結合（`:backend:integrationTest`、`*IT`）で分かれており、別々に実行できる。

## 2. 実行のしかた

```bash
# 結合テストだけを全部
./gradlew :backend:integrationTest

# 絞り込み（--tests は直前のタスクにだけ効く）
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.auth.*'

# 統合の前の関門（結合テストを含む全検査）
./gradlew verify

# E2E（ビルドした WAR を起動してブラウザから通す。verify には入れない）
(cd frontend && npx playwright install chromium)   # 各自1回
./gradlew e2eTest
```

報告は `backend/build/reports/tests/integrationTest/`、`frontend/playwright-report/` に出る。

## 3. 単位をまたぐ境界と、それを受け持つテスト

本Intentの単位は U1（土台）→ U2（認証）→ U3（アクセス制御）→ U4（監査ログ）の順に積み上がる。境界ごとに、既に生成されている結合テストを対応づける。

### 3.1 U1 ⇄ U2（土台の差し込み口を認証が使う）

| 境界 | 確かめること | テスト |
|---|---|---|
| 共通のエラー応答 | ログインの失敗が U1 の Problem Details（`type`・`code`・`traceId`）の形で返る | `auth.web.LoginApiIT`、`auth.web.AuthProblemTypesIT` |
| 安全の決まりの差し込み（`SecurityRuleContributor`） | U2 が足した決まりが order の順に効き、公開の API（ログイン・更新・ログアウト）だけが未ログインで通る | `config.SecurityExtensionIT`、`auth.web.AccessTokenApiIT` |
| ログとトレース | ログインの処理中のログに同じトレースIDが入り、応答のトレースIDと一致する | `common.observability.TracingAndLoggingIT`、`audit.service.AuditTraceIdIT` |
| 秘密情報の伏せ字 | TRACE を有効にしてもパスワード・トークンがログに出ない | `auth.web.AuthSecretLeakIT`、`common.observability.TraceAspectIT` |
| 内部DBとスキーマ | U2 の表（利用者・ログイン試行・リフレッシュトークン）が Flyway で作られ、再起動後も残る | `user.repository.UserSchemaIT`、`auth.repository.AuthSchemaIT`、`common.db.DatabasePersistenceIT` |

### 3.2 U2 ⇄ U3（認証の結果をアクセス制御が使う）

| 境界 | 確かめること | テスト |
|---|---|---|
| トークンの検証 → 認可 | 未ログイン 401、管理者フラグなし 403、管理者 200 をサーバー側で確かめる | `access.web.AdminAccessIT` |
| 管理者フラグの出どころ | 管理者かどうかを要求ごとに内部DBから読み、トークンの中身を信用しない（フラグを外した直後の要求が 403） | `access.web.AdminAccessIT` |
| 既定のアクセス制御 | `/api/` の下は既定でログインを求め、公開の一覧だけが未ログインで通る | `access.web.ApiDefaultAccessIT` |
| パスの境界 | 正規化されていないパス（`..;/`、エンコードされた区切り、大文字）が判定の前に拒否される | `access.web.AdminPathBoundaryIT` |
| 401／403 の応答の形 | U1 の共通の組み立てで返り、内部の情報を載せない | `access.web.AccessProblemTypesIT`、`access.web.AccessSecretLeakIT` |

### 3.3 U2・U3 ⇄ U4（出来事を監査ログが記録する）

| 境界 | 確かめること | テスト |
|---|---|---|
| 認証の出来事 → 記録 | ログイン成功・失敗・ログアウトが必須の項目つきで1件ずつ記録される。存在しないメールアドレスの失敗も記録される | `audit.service.AuditAuthenticationEventsIT` |
| アクセス拒否の出来事 → 記録 | 403 と有効期限切れ以外の 401 が記録され、要求のパスが入る | `audit.service.AuditAccessDeniedIT` |
| 取り消しの扱い | 元の操作が取り消されたときは記録しない | `audit.service.AuditRollbackIT` |
| 書き込みの失敗の隔離 | 記録に失敗しても元の操作の応答が変わらず、ERROR が1回だけ出る | `audit.service.AuditWriteFailureIT` |
| 秘密情報の漏えい | 記録とログにパスワード・トークンの値が入らない | `audit.service.AuditSecretLeakIT` |
| 監査とアプリのログの分離 | 監査イベントをアプリのログで代用していない | `audit.service.AuditNotInAppLogIT` |
| トレースID の一致 | 監査イベントのトレースIDが同じ要求のログと一致する | `audit.service.AuditTraceIdIT` |
| 記録の時間 | 記録が元の操作を目に見えて遅らせない（しきい値超えで WARN） | `audit.service.AuditWriteTimingIT` |

### 3.4 画面 ⇄ API（ビルドした WAR での E2E）

| 流れ | 確かめること | テスト |
|---|---|---|
| ログイン画面の表示 | 配信元と同じサーバーから画面が返り、CSP 違反もスクリプトのエラーも出ない | `frontend/e2e/u1-skeleton.e2e.ts` |
| 画面の URL の直接の表示 | SPA の入口に落ちてログイン用レイアウトが出る | `frontend/e2e/u1-skeleton.e2e.ts` |
| ログイン → 再読み込み → ログアウト | 初期管理者でログインでき、再読み込みで続き、ログアウトでトークンが捨てられる | `frontend/e2e/u2-auth.e2e.ts` |
| パスワード誤り | 理由によらず1種類の文言が出て、ログイン画面に留まる | `frontend/e2e/u2-auth.e2e.ts` |
| ログイン → 管理画面 → ログアウト | サイドバーから管理者向け領域に入れる（team.md が求める代表的な流れ） | `frontend/e2e/u3-admin-access.e2e.ts` |

## 4. テストデータと環境

- 利用者・ログイン試行・リフレッシュトークン・監査イベントは、各テストが自分で用意し、終わったら巻き戻す。共有の初期データに依存しない。
- 署名鍵・初期管理者の設定は、テスト用の値を `@DynamicPropertySource` や `@SpringBootTest(properties = ...)` で渡す。リポジトリに秘密情報を置かない。
- 内部DBは、結合テストでは一時領域のファイル、または `jdbc:h2:mem:` をテストごとに分けて使う。テストの間でファイルを共有しない。
- E2E は `./gradlew e2eTest` が WAR を起動してから Playwright を動かす。呼び出し先は `http://localhost`（`Secure` の Cookie が localhost では送られるため。NFR5.5）。

## 5. 求めるカバレッジ

結合テストは単体テストと合わせて計測する（JaCoCo の実行データは `test.exec` と `integrationTest.exec` の両方を読む）。

| 対象 | 下限 | 検証する場所 |
|---|---|---|
| バックエンド | 行 80%・分岐 70% | `./gradlew verify` の 7 の段（`jacocoTestCoverageVerification`） |
| フロントエンド | 行 80%・分岐 70% | 同上（`@vitest/coverage-v8` の `thresholds`） |

下限を下げること、計測の除外を増やすことはしない（org.md・team.md）。除外は起動クラス・設定値だけのクラス・自動生成コード・`vendor/` に限る。

単位ごとの絞り込んだコマンドで `jacocoTestCoverageVerification`（全体の下限）を回してはならない。他の単位のクラスが測られず、判定が意味を持たないためである（`test-results.md` の「実行時に見つかった手順の不備」を参照）。

## Sources

- 各単位の `construction/<unit>/code-generation/unit-test-instructions.md`（2 節の実行のコマンド、3 節のテストの一覧）
- 各単位の `construction/<unit>/code-generation/code-summary.md`
- `backend/src/test/java/` の `*IT` の実体、`frontend/e2e/` の `*.e2e.ts` の実体
- `build.gradle.kts`（`verifyIntegrationTest`、`e2eTest`）、`backend/build.gradle.kts`（`integrationTest`、JaCoCo の実行データと下限）
- `aidlc/spaces/default/memory/team.md`（Testing Posture、Walking Skeleton）

## Assumptions & Open Questions

None.
