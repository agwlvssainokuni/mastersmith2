# セキュリティテストの手順（security-test-instructions）

本Intentは認証・認可・監査を扱い、測れる安全の要件（NFR2〜NFR5、NFR3 系の枝番）を多く持つため、Test Strategy が Standard でも本書を作る。

本書は、(a) 道具による自動の検査（静的解析・秘密情報の検出・依存関係の脆弱性）と、(b) 振る舞いを確かめるテスト（認証・認可・漏えい・注入）の両方を扱う。動的検査（OWASP ZAP）・CodeQL・コンテナイメージの検査は**採用しない**（team.md の Code Style の決定）。

## 1. 道具による自動の検査

すべて `./gradlew verify` の 8 の段（`verifySecurity`）に入っており、CI でも同じタスクが動く。

| 検査 | 道具 | 実行 | 統合を止める基準 |
|---|---|---|---|
| 秘密情報の検出 | Gitleaks 8.30.1（リポジトリの履歴全体） | `./gradlew gitleaksScan`、`pre-commit run --all-files`（コミットの前も） | 1件でも見つかれば失敗 |
| Java の静的解析 | SpotBugs ＋ FindSecBugs | `./gradlew :backend:spotbugsGate` | 重大度 High（priority 1）で失敗。priority 2・3 は警告 |
| 依存関係の脆弱性 | OSV-Scanner 2.6.0（`vendor/make-you-chic-ui` の lockfile を含む） | `./gradlew osvScan` | 実行時に使う依存（dependencies・推移依存）の High 以上で失敗。開発時だけのもの（devDependencies）は警告。ただし `MAL-` で始まる悪意のあるパッケージと、成果物を作る道具（`config/npm-build-tools.txt`）の High 以上は止める |
| 画面のセキュリティ系のリンタ | oxlint・ESLint（`react/no-danger`、`no-eval` 系） | `./gradlew verifyLint` | 違反で失敗 |
| 依存関係の更新通知 | Dependabot | GitHub 側で動く | 通知のみ |

```bash
# 8 の段だけ
./gradlew verifySecurity
# コミットの前の検査（Gitleaks とフォーマット）
pre-commit run --all-files
```

報告は `backend/build/reports/spotbugs/`、`build/reports/osv-scanner/osv.json` に出る。

## 2. 振る舞いを確かめるテスト（既に生成されているもの）

team.md の Testing Posture が必須とした項目を、テストと対応づける。実行は `./gradlew :backend:test :backend:integrationTest` と `(cd frontend && npm test)`、E2E は `./gradlew e2eTest`。

### 2.1 パスワードとログイン

| 確かめること | 出典 | テスト |
|---|---|---|
| パスワードは bcrypt で保存され、cost が設定で変わる | U2 NFR2.1 | `user.domain.PasswordTest`、`auth.service.AuthPropertiesDefaultsTest`、`auth.service.AuthSettingsIT` |
| 12 文字未満・72 バイト超えの扱い（11／12 文字、72／73 バイト） | U2 NFR2.2・NFR2.3 | `user.domain.PasswordPolicyTest`、`user.service.DummyPasswordHashTest`、`auth.web.LoginApiIT` |
| 存在しないメールアドレスとパスワード誤りで、状態コード・code・本文が同じ（利用者の存在を推測させない） | U2 NFR4.1 | `auth.web.LoginApiIT` |
| 失敗の経路ごとに照合の回数と SQL の種類・回数がそろう | U2 NFR4.2 | `auth.service.LoginServiceTest` |
| 画面のログイン失敗の表示が1種類 | U2 NFR4.3 | `frontend/src/features/auth/LoginForm.test.tsx`、`frontend/e2e/u2-auth.e2e.ts` |

### 2.2 アカウントロック

| 確かめること | 出典 | テスト |
|---|---|---|
| しきい値−1回ではロックされない／しきい値ちょうどでロックされる（境界） | team.md、FR7 | `auth.domain.LockPolicyTest`（jqwik の性質を含む）、`auth.web.LoginApiIT` |
| ロック中は正しいパスワードでも拒否 | 同上 | `auth.service.LoginServiceTest` |
| ログイン成功で失敗回数が戻る | 同上 | `auth.service.LoginServiceTest` |
| 同時の失敗でも取りこぼさない（行の排他） | U2 NFR9.1 | `auth.service.LoginConcurrencyIT` |

### 2.3 トークン

| 確かめること | 出典 | テスト |
|---|---|---|
| 有効期限の境界（直前は有効／直後は無効）。注入した `Clock` で測る | team.md | `auth.domain.TokenExpiryTest`、`auth.service.AccessTokenServiceTest` |
| 署名の改ざんを拒否 | U2 NFR3.2 | `auth.service.AccessTokenServiceTest`、`auth.web.AccessTokenApiIT` |
| `alg: none` や別の署名方式を拒否 | 同上 | 同上 |
| 署名鍵が無い・256 ビット未満なら起動を止める | U2 NFR3.3 | `auth.service.SigningKeyStartupIT` |
| ログアウト後にリフレッシュトークンを拒否 | FR6 | `auth.web.LogoutApiIT` |
| ログアウト後もアクセストークンが期限まで使える（決定済みの仕様） | scope-definition の DECIDED | `auth.web.LogoutApiIT` |
| リフレッシュトークンは乱数で、DB にはハッシュだけ | U2 NFR5.2 | `auth.repository.RefreshTokenRepositoryIT` |
| 同じリフレッシュトークンの同時の更新で2つとも成功しない | U2 NFR9.2 | `auth.service.RefreshConcurrencyIT` |
| Cookie が HttpOnly・Secure・SameSite=Strict で、path が更新とログアウトに限られる。削除の指示が同じ名前・同じ Path | U2 NFR5.3 | `auth.web.RefreshCookiesTest`、`auth.web.TokenApiIT`、`auth.web.LogoutApiIT` |
| 更新とログアウトの API が Origin の一致を求める（一致・不一致・無し） | U2 NFR5.4 | `auth.web.OriginVerifierTest`、`auth.web.TokenApiIT` |
| 画面はアクセストークンをメモリにだけ持ち、localStorage・sessionStorage に置かない | U2 NFR5.1 | `frontend/src/features/auth/authSession.test.ts` |

### 2.4 初期管理者の自動作成

| 確かめること | 出典 | テスト |
|---|---|---|
| 2回目以降の起動で重複作成しない | U2 NFR6.1 | `user.service.InitialAdminIT` |
| 設定が無い／不正なときは作らずに起動を続け、WARN を出す | FR3.3、U2 NFR10.3 | `user.service.InitialAdminIT` |
| パスワードがログに出ない | project.md Forbidden | `user.service.InitialAdminIT`、`auth.web.AuthSecretLeakIT` |

### 2.5 認可

| 確かめること | 出典 | テスト |
|---|---|---|
| 未認証 401、管理者フラグなし 403、管理者 200 を**サーバー側**で確かめる（画面でメニューを隠すことは代わりにしない） | U3 NFR3.1 | `access.web.AdminAccessIT` |
| `/api/` の下は既定でログイン必須。公開は明示した一覧だけ | U3 NFR3.2 | `access.web.ApiDefaultAccessIT` |
| 正規化されていないパス（エンコードされた区切り、`;`、`..`、`//`）を判定の前に拒否。大文字・小文字を区別し、末尾のスラッシュで判定が変わらない | U3 NFR3.3 | `access.web.AdminPathBoundaryIT`、`access.web.AccessRequestRejectedHandlerTest` |
| 管理者でない利用者には、存在しない管理 API も 403（有無を明かさない） | U3 NFR3.4 | `access.web.AdminAccessIT` |
| 管理者かどうかは要求ごとに DB から読む（フラグを外した直後の要求が 403） | U3 NFR3.5 | `access.web.AdminAccessIT` |
| `/api/` の外で応答するのは画面の配信と `/actuator/health` だけ | U3 NFR3.8、U1 NFR3.5 | `config.ExposureIT` |

### 2.6 監査ログ

| 確かめること | 出典 | テスト |
|---|---|---|
| 対象のイベントごとに必須の項目が記録される | U4 NFR9.1 | `audit.service.AuditAuthenticationEventsIT`、`audit.service.AuditAccessDeniedIT` |
| 存在しないメールアドレスでのログイン失敗も記録される | 同上 | `audit.service.AuditAuthenticationEventsIT` |
| 書き込みに失敗しても元の操作の応答が変わらず、ERROR が1回だけ出る | U4 NFR10.1・NFR10.3 | `audit.service.AuditWriteFailureIT` |
| 取り消された操作は記録しない | U4 NFR10.2 | `audit.service.AuditRollbackIT` |
| 監査イベントを変更・削除する処理と API を持たない | U4 NFR3.2 | `audit.AuditBoundaryArchitectureTest`（ArchUnit） |
| 攻撃者が値を決められる項目を切り詰めて保存する（長い値・絵文字・改行） | U4 NFR3.4 | `audit.domain.AuditTextTest`（jqwik の性質を含む） |

### 2.7 秘密情報の漏えい

| 確かめること | 出典 | テスト |
|---|---|---|
| ログ・監査ログ・エラー応答・トレースの属性に、パスワード（平文・ハッシュ）・アクセストークン・リフレッシュトークン・署名鍵が出ない | project.md Forbidden、U1 NFR3.1、U2 NFR3.1・NFR3.5、U4 NFR3.1 | `auth.web.AuthSecretLeakIT`、`access.web.AccessSecretLeakIT`、`audit.service.AuditSecretLeakIT` |
| TRACE を有効にしても秘密情報が出ない。秘密情報を持つ型は文字列化で伏せ字 | U1 NFR3.15、U2 NFR3.6 | `common.observability.TraceAspectIT`、`auth.domain.SecretTypesTest`、`auth.web.WebSecretTypesTest` |
| エラー応答に内部の例外メッセージ・スタックトレースが載らない | U1 NFR3.3 | `common.error.web.ErrorResponseIT`、`common.error.web.GlobalExceptionHandlerTest` |
| 内部DBのパスワードがログに出ない | U1 NFR3.1 | `common.db.DatabasePersistenceIT` |

### 2.8 注入・要求と応答の安全

| 確かめること | 出典 | テスト |
|---|---|---|
| 説明ページ（HTML）は埋め込む値をすべてエスケープし、要求の値を埋め込まない | U1 NFR3.9 | `common.error.web.ProblemTypeHtmlRendererTest`、`common.error.web.ProblemTypePageIT` |
| ログに改行を注入して記録を偽装できない（1行の JSON に収まる） | U1 NFR3.11、U4 NFR3.4 | `common.observability.JsonLogFormatTest`、`audit.domain.AuditTextTest` |
| CSP・`X-Content-Type-Options`・`Referrer-Policy` が付き、API は `no-store` | U1 NFR3.10 | `config.SecurityHeadersIT`、`common.web.CacheControlFilterTest`、`frontend/e2e/u1-skeleton.e2e.ts`（本番の構成で CSP 違反が出ないこと） |
| 要求の本文の上限（既定 1MB）を超えたら 413 | U1 NFR3.12 | `common.web.RequestSizeLimitFilterTest`、`config.ExposureIT` |
| 転送元のヘッダー（`X-Forwarded-*`）を既定で信頼しない | U1 NFR3.8 | `common.error.web.ProblemBaseUrlResolverTest`、`common.error.web.ErrorResponseIT` |
| Actuator は health だけ公開、内訳を出さない。H2 の Web コンソールは無効 | U1 NFR3.5・NFR3.6 | `config.ExposureIT`、`common.health.HealthEndpointIT` |
| 外部エクスポートは既定で無効 | U1 NFR10.7、NFR3.4 | `common.observability.ExternalExportIT` |
| SQL の組み立ては値の埋め込みを使わない | phases/construction.md | `audit.AuditBoundaryArchitectureTest`、`auth.AuthBoundaryArchitectureTest`、`ArchitectureTest`（ArchUnit）、SpotBugs＋FindSecBugs |

### 2.9 画面のアクセシビリティ（安全と隣り合う品質）

画面の部品ごとに vitest-axe の検査を1件入れる（U1 NFR8.1、U2 NFR8.1、U3 NFR8.1）。`(cd frontend && npm test)` に含まれる。

## 3. 確かめない（受け入れる危険）

要件で明示的に受け入れた危険は、テストを作らない。理由を残す。

| ID | 内容 | 出典 |
|---|---|---|
| U2 NFR4.4 | ロックの悪用（他人のメールアドレスで失敗させてロックさせる）と、接続元ごとの回数制限を行わない | u2 security-requirements 5 節 |
| U2 NFR5.6 | 盗まれたリフレッシュトークンの再利用を検知しても、その利用者の他のトークンは無効にしない | 同上 |
| U4 NFR3.6 | 内部DBのファイルに直接触れられる者による監査イベントの書き換え・削除は検知できない | u4 security-requirements |
| U4 NFR3.7 | 元の操作の確定後、監査イベントを書く前の停止で1件が失われる | 同上 |

## 4. 配備先が決まってから確かめること

| ID | 内容 | 持ち主 |
|---|---|---|
| U4 NFR3.3 | 内部DBのファイルを OS の権限で守る（コンテナのボリュームの権限） | 配備の手順（Deployment Execution／Environment Provisioning） |
| U2 NFR5.5 | HTTPS（`Secure` の Cookie を本番で効かせる） | 配備先が決まったとき |
| SBOM | 部品表の生成 | 同上（team.md の Deployment） |

## 5. 決めごと

- 検査の指摘で統合を止める基準は重大度 High 以上。それ未満は警告として扱う（team.md）。
- 基準を緩めて通すことはしない。止まったら、版を上げる・実装を直すのどちらかで直す。
- 秘密情報の検出は、コミットの前と CI の両方で実行する（project.md Mandated）。

## Sources

- `construction/u1-app-skeleton/nfr-requirements/security-requirements.md`、`observability-requirements.md`
- `construction/u2-authentication/nfr-requirements/security-requirements.md`
- `construction/u3-access-control/nfr-requirements/security-requirements.md`
- `construction/u4-audit-log/nfr-requirements/security-requirements.md`
- 各単位の `nfr-design/security-design.md`、`code-generation/unit-test-instructions.md` 3 節
- `aidlc/spaces/default/memory/team.md`（Testing Posture の必須テスト、Code Style の静的解析とセキュリティ検査、Deployment の脆弱性の判定）
- `aidlc/spaces/default/memory/project.md`（Forbidden、Mandated）
- `build.gradle.kts`（`gitleaksScan`、`osvScan`、`verifySecurity`）、`backend/build.gradle.kts`（`spotbugsGate`）

## Assumptions & Open Questions

None.
