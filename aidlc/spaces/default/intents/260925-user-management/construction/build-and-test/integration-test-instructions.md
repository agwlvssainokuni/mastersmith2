# 結合テストの手順（integration-test-instructions）

Intent `260925-user-management` の結合テストの手順です。Test Strategy は Standard です。そのため、単位の境目と単位の間のやり取りを結合テストで確かめます。対象は次の2つです。

- **バックエンドの結合テスト**（`*IT`、`./gradlew verify` の 6 の段）
- **画面からの一連の操作**（E2E、`./gradlew e2eTest`）。実際のブラウザの検査（axe）と画面の時間の測りを含みます。

単体テストの手順は、各単位の `unit-test-instructions.md`（`construction/u*/code-generation/`）にあります。この文書では繰り返しません。

## 1. 準備

- `build-instructions.md` の 3.1（colima と `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`）と 3.2（Mailpit）を済ませておきます。
- テストのデータは、テストごとに用意して巻き戻します。実行の順には依存させません（team.md の Testing Posture）。
  - 内部DB は組み込みの H2 です。
  - 対象DB は版を固定したイメージのコンテナです。
  - メールは JVM の中で起動する SubEtha SMTP で受けます。実在の宛先へは送りません。
- E2E は1つの WAR と内部DB を共有するため、ファイル名の番号の順に1本ずつ流します（`workers: 1`）。新しく足した流れは、前のテストが作った状態に頼らず、利用者などの前提を自分で作ります。

## 2. 流し方

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock

# バックエンドの結合テスト（件数とカバレッジを実測するときは clean を付ける）
./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify

# 結合テストだけ（開発中の途中の実行）
./gradlew :backend:integrationTest

# 1つのクラスだけ
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.invitation.web.InvitationFlowIT'

# E2E（Mailpit を起動して）
docker compose --profile mail up -d mailpit
./gradlew e2eTest
```

- `./gradlew e2eTest` の結果は `frontend/test-results/e2e-results.json` と `frontend/playwright-report/` に出ます。どちらも Git 管理外です。共有しません（トークンを含む URL が載りうるため。U6 の基盤の設計の Q2）。
- json の報告に秘密（アクセストークン・パスワード・初期管理者と測定の招待のメールアドレス）が入っていないことを、E2E のたびに文字列の検索で確かめます（project.md の Testing Posture）。

## 3. 単位の境目の結合テスト（この Intent で足したもの）

| 境目 | 主なテストクラス | 確かめること |
|---|---|---|
| U1 メール（送信の部品 ↔ SMTP） | `mail/transport/MailSendIT`・`MailTlsIT`・`MailSendFailureIT`・`MailHeaderInjectionIT`、`mail/config/MailConfigurationIT`、`mail/service/MailSecretLeakIT` | 実際に受けたメールの宛先・件名・本文（HTML）・言語、STARTTLS・SMTPS、接続の拒否・応答なしの失敗、ヘッダーへの改行の差し込みの拒否、宛先・資格情報・SMTP の応答が応答とログに出ないこと |
| U2 利用者の設定（API ↔ 業務処理 ↔ 内部DB ↔ 監査） | `user/web/MePreferencesApiIT`・`MePasswordApiIT`・`MeSecretLeakIT`、`user/service/PasswordChangeConcurrencyIT`・`PreferencesPartialUpdateIT`・`UserCreationIT`、`audit/service/PasswordChangedAuditIT`、`user/repository/V7MigrationIT`・`V7BackwardCompatibilityIT`、`auth/web/LoginResponsePreferencesIT` | 本人の分だけの取得・保存、401、入力の誤りの `fieldErrors`、今のパスワードの確かめ、変更の後のリフレッシュトークンの扱い、同時の変更、監査の記録、V7 の前進と1つ前の版の後方互換 |
| U3 招待と登録の完了（API ↔ 業務処理 ↔ U1 のメール ↔ U2 の利用者の作成 ↔ 監査） | `invitation/web/InvitationFlowIT`・`InvitationAdminApiIT`・`RegistrationApiIT`・`InvitationMailIT`・`InvitationSendFailureIT`・`InvitationSendConnectionIT`・`InvitedPersonAuthenticationIT`・`RegistrationPublicScopeIT`・`InvitationAuditIT`・`InvitationAuditWriteFailureIT`・`InvitationSecretLeakIT`、`invitation/service/InvitationConcurrencyIT`・`RegistrationConcurrencyIT`・`RegistrationRollbackIT`・`InvitationSendResultIT`・`InvitationCleanupIT`、`invitation/repository/*IT` | 招待 → メールの受け取り → 登録の完了 → ログインの流れ、有効期限の境界、使い終えた・取り消した・改ざんした招待の拒否、招待中の人がログイン・更新・認証のどれでも拒否されること、送信の最中に接続を持たないこと、送信の失敗の記録、同時の招待・完了、U2 の利用者の作成での巻き戻し、トークンと URL が漏れないこと、定期の削除、V8 |
| U8 見た目の設定（公開の API ↔ 起動時の判定） | `appearance/web/AppearanceApiIT`・`AppearanceStartupIT` | トークンなしの `GET` の 200 と2項目、他の方法の 401・405、許されない値の既定への置き換えと WARN、ヘルスチェックの応答が `status` だけのまま |
| 既存のテストの手直し | `audit/service/*IT`・`auth/web/*IT`・`user/repository/UserRepositoryIT` ほか | 監査の対象の列（`target_user_id`・`target_invitation_id`）と新しい記録の種類の反映。既存の確かめは緩めていない |

## 4. 画面からの一連の操作（E2E）と実際のブラウザの検査

| ファイル | 中身 | この段で写すもの |
|---|---|---|
| `010`〜`040` | 既存の流れ（骨格・認証・管理画面・DSL の管理） | 成否 |
| `050-display-accessibility.e2e.ts` | U4 の表示の設定。ブランドカラー 4 × テーマ × 言語などの 20 組の axe、最初の画面の時間、フォントと CSP | 組ごとの成否・違反の件数と規則の名前・`incomplete`、時間の5回の値と 2 秒以内の回数、`sans` のときに Noto Serif JP を読まないこと、CSP の違反 0 |
| `060-invitation-accessibility.e2e.ts` | U5 の招待の管理の画面の 20 組の axe と画面の時間 | 同上（状態ごと）。`test.skip` のときは `Unverified` |
| `070-registration-accessibility.e2e.ts` | U6 の登録の完了の画面の 20 組 × 2 状態の axe | 同上 |
| `080-preferences-accessibility.e2e.ts` | U7 のプリファレンスの画面の 20 組 × 2 画面 × 2 状態の axe、画面の時間、本物の応答と見本の形の照合 | 同上。E2E の WAR（PC の上、空の内部DB）と配備したアプリの違いを明記 |
| `090-invitation-registration-flow.e2e.ts` | 代表の流れ E2E-1: 招待 → Mailpit の API でリンクを取り出す → 登録の完了 → ログイン | 成否、3つの確かめ（アドレス欄・CSP・ブラウザの保存）、応答の形の照合 |

- 既知の違反は、既知のまま記録します（`build-and-test-questions.md` の Q7・F1）。
  - U4：green・orange の Button
  - U7：Avatar の頭文字が2文字のとき、dark の FormField の誤りの文字
- `e2eTest` の時間は、全体と、次の3つの部分に分けて記録します。
  - 検査の部分（050〜080 の検査）
  - 測りの部分（画面の時間）
  - 流れの部分（010〜040・090）

## 5. 期待する結果

- `./gradlew verify` が `BUILD SUCCESSFUL` になること。対象DB のテストの飛ばしが 0 件であること。
- 結合テストの失敗が 0 件であること。
- E2E のすべてのファイルが通ること。json に秘密が入っていないこと。
- カバレッジが全体と各パッケージで行 80%・分岐 70% 以上であること（`build-instructions.md` の 4 節）。

実測は `test-results.md` に写し、目標ごとの判定は `build-and-test-summary.md` の表に書きます。

## Sources

- 各単位の `unit-test-instructions.md`・`code-generation-plan.md`（「Build and Test に引き継ぐこと」）・`code-summary.md`（`construction/u1-mail/` 〜 `construction/u8-instance-appearance/` の `code-generation/`）
- `backend/src/test/java/cherry/mastersmith/` の `*IT.java`（この Intent で足した・直したもの。`git diff --name-status 7b7362b HEAD`）
- `frontend/e2e/`（`050`〜`090` と `support/`）
- `README.md`（ビルドした WAR での画面の確認（E2E）、メール（U1））
- `aidlc/spaces/default/memory/team.md`（Testing Posture）・`project.md`（Testing Posture）
- `construction/build-and-test/build-and-test-questions.md`（Q7・F1）

## Assumptions & Open Questions

None.
