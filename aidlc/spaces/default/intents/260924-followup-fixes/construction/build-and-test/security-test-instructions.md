# 安全の試験の手順（security-test-instructions）

この Intent は、秘密情報と個人に関する値の扱い（FR1・FR6、NFR1）と、認証（FR2）に触れる。project.md の Forbidden（パスワード・トークン・署名鍵をログ・外部への送り出し・エラー応答に含めない、`.env` などの秘密情報をコミットしない）と Mandated（認証の変更には失敗の場合のテスト、秘密情報の検出をコミット前と CI の両方で実行、統合前に重大度 High 以上の依存の脆弱性が無いこと）を確かめる。

## 1. 自動の検査（`./gradlew verify` の中）

| 検査 | 道具 | 合格の条件 |
|---|---|---|
| 秘密情報の検出（履歴全体） | Gitleaks | 検出なし |
| Java の静的解析 | SpotBugs＋FindSecBugs | priority 1 と `SQL_` の指摘が無い |
| 依存の脆弱性 | OSV-Scanner（サブモジュールの lockfile を含む） | 実行時の依存で High 以上が無い（team.md の Deployment の決まり） |
| 画面のセキュリティのルール | oxlint・ESLint（`react/no-danger`・`no-eval` 系） | 違反なし |

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
```

## 2. この Intent の安全のテスト

| テスト | 確かめること | 要件 |
|---|---|---|
| `OtlpLogExportIT` | 外部へ送るログで、メールアドレス・IP・User-Agent が `[REDACTED]`。パスワード・アクセストークン・リフレッシュトークン・署名鍵が `/v1/logs`・`/v1/traces` に無い | FR1.2・FR1.4、NFR1 |
| `SanitizingLogRecordExporterTest` | 4つのキーの値を伏せ、ほかの属性と本文を変えない | FR1.2 |
| 既存の `*SecretLeakIT`・`AuditNotInAppLogIT` | 標準出力のログ・監査ログ・エラー応答に秘密の値が無い | NFR1 |
| `LoginConcurrencyIT`・`LoginServiceTest` | パスワードの誤り・ロック・ロック中の正しいパスワードの拒否（認証の失敗の場合） | FR2.2（project.md の Mandated） |

## 3. 秘密情報の置き場の確かめ（R-03 の基準）

```bash
git check-ignore -q .env.targetdb; echo $?          # 0（Git 管理外）
git check-ignore -q .env.targetdb.example; echo $?  # 1（コミットできる）
git ls-files .env.targetdb .env.targetdb.example     # .env.targetdb.example だけ
grep -vE '^[[:space:]]*(#|$)' .env.targetdb.example | grep -vE '^[A-Z0-9_]+=$'   # 何も出ない（値が空）
MASTERSMITH_IMAGE_TAG=followup-fixes ./docker/check-container-limits.sh           # 節5: app は MASTERSMITH_SAMPLE_TARGETDB_* を持たない
```

- Gitleaks の規則は足さない（依頼者の判断。`.env.targetdb` の形の値は既定の規則でも `.gitleaks.toml` でも検出されないことを Code Generation で確かめた。守りの本体は `.gitignore`）。
- 値の入った `.env` を読んだ状態での、アプリのコンテナの環境変数の確かめ（名前だけ）は、`.env` から `.env.targetdb` への移し替えと一緒に Deployment Execution で行う。

## 4. 採用しないもの

CodeQL などのより広い静的解析、起動したアプリへの動的検査（OWASP ZAP）、コンテナイメージの検査は採用しない（team.md の Code Style）。
