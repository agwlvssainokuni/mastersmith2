# セキュリティの試験の手順（security-test-instructions）

Intent `260925-user-management` のセキュリティの目標の確かめ方です。確かめは、すべて自動の検査とテストで行います。CodeQL・OWASP ZAP・コンテナイメージの検査は採用していません（team.md の Code Style）。

## 1. 静的な検査（`./gradlew verify` の 8 の段）

| 検査 | 止める基準 | この Intent で増えたこと |
|---|---|---|
| SpotBugs＋FindSecBugs（`spotbugsGate`） | priority 1、パターン名が `SQL_` で始まるもの、`PREDICTABLE_RANDOM`、`SMTP_HEADER_INJECTION`（priority によらない） | `PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` を関門に加えた。誤検知は `backend/config/spotbugs-exclude.xml` に理由を書いて外す |
| OSV-Scanner（`osvScan`） | 実行時の依存の重大度 High 以上、悪意のあるパッケージ（MAL-）、成果物を作る道具の High 以上 | java-mustache-processor の推移依存も lockfile と検査の対象に含む |
| Gitleaks（`gitleaksScan`） | 秘密情報の検出（履歴全体） | — |

報告: `backend/build/reports/spotbugs/main.xml`、`build/reports/osv-scanner/osv.json`

## 2. 必ず書くテスト（team.md の Testing Posture）の確かめ先

| 観点 | 主なテスト |
|---|---|
| 認可（401・403・200） | `InvitationAdminApiIT`・`MePreferencesApiIT`・`MePasswordApiIT`・`AppearanceApiIT`、`RegistrationPublicScopeIT` |
| 招待と登録の完了（期限の境界、使い終えた・取り消した・改ざんした・存在しない招待の拒否、招待中の人の拒否） | `RegistrationApiIT`・`InvitedPersonAuthenticationIT`・`InvitationFlowIT`、`invitation.domain` の単体テスト |
| パスワードの変更（今のパスワードの確かめ、規則の境界、変更の後のリフレッシュトークン） | `MePasswordApiIT`・`PasswordChangeConcurrencyIT`、`user.domain` の単体テスト（jqwik） |
| 監査ログ（必須項目、失敗の記録、書き込みの失敗） | `PasswordChangedAuditIT`・`InvitationAuditIT`・`InvitationAuditWriteFailureIT` |
| 秘密情報の漏えい（パスワード・トークン・招待の URL・メールアドレスが、ログ・監査・トレース・エラー応答に出ない） | `MeSecretLeakIT`・`InvitationSecretLeakIT`・`MailSecretLeakIT`・`AuditSecretLeakIT` |
| メールの本文のエスケープ・テンプレートの描画・ヘッダーへの差し込み | `mail.template` の単体テスト、`MailHeaderInjectionIT`・`InvitationMailIT` |
| 招待の URL を Host ヘッダーから組み立てない | `invitation` の単体テストと結合テスト（`RegistrationUrl`・`BaseUrlRule`） |
| 送信の失敗（接続の拒否・応答なし）で宛先・SMTP の応答・資格情報が出ない | `MailSendFailureIT`・`InvitationSendFailureIT` |
| 画面（CSP の違反 0、トークンの扱い、アクセシビリティ） | E2E の `050`〜`090`（`integration-test-instructions.md` 4 節）、画面部品ごとの vitest-axe |
| E2E の報告に秘密が入らない | `frontend/test-results/e2e-results.json` の文字列の検索（アクセストークン・パスワード・初期管理者と測定の招待のメールアドレス） |

## 3. 流し方

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify

docker compose --profile mail up -d mailpit
./gradlew e2eTest
```

E2E の報告の確かめの例です（値そのものは出さず、件数だけを見ます）。

```bash
grep -c -E 'eyJ[A-Za-z0-9_-]{10,}' frontend/test-results/e2e-results.json   # アクセストークンの形 → 0
grep -c '/register#token=' frontend/test-results/e2e-results.json         # 招待の URL → 0
```

## 4. この段では確かめないもの

| 目標 | 持ち主 |
|---|---|
| 今のパスワードの総当たりの見つけ方の問い合わせの運用（残る危険 R1） | observability-setup |
| 配備した環境の `.env` に秘密を入れる手順・戻しの練習 | deployment-pipeline・deployment-execution |
| 閲覧の履歴にトークンを含むアドレスが残ること（U6 の A9） | 受け入れた危険。コード生成の中で1回確かめた結果を写す |

## Sources

- 各単位の `nfr-requirements/security-requirements.md`・`nfr-design/security-design.md`（`construction/u1-mail/` 〜 `construction/u8-instance-appearance/`）
- 各単位の `code-generation-plan.md`・`unit-test-instructions.md`・`code-summary.md`
- `aidlc/spaces/default/memory/team.md`（Testing Posture・Code Style・Deployment）・`project.md`（Forbidden・Mandated・Testing Posture）
- `README.md`（1コマンドの検査、依存関係の脆弱性の判定）

## Assumptions & Open Questions

None.
