# 結合テストと E2E の手順（261003-user-admin-followup）

- Test Strategy は Minimal のため、段の定義では追加の手順書は求められない。ただし、この Intent の不具合の再現は結合テスト（FR5・FR6・FR8）と E2E（FR1・FR2）の段で受け持つため（Minimal の「最も狭く効く段」と bugfix の下限）、流し方と確かめる中身をここに残す（`project.md` の学び 2026-09-25 と同じ扱い）。
- 単体・結合テストを絞って流すコマンドは `construction/code-generation/unit-test-instructions.md` にある。この文書は、統合の前の全体の実行と、この段での確かめ方を書く。

## 1. 道具と設定

| 対象 | 道具 | 置き場・名前 |
|---|---|---|
| バックエンドの結合 | JUnit 5・AssertJ・Spring Boot のテスト、組み込みの H2（内部DB）、Testcontainers（対象DB）、`OutputCaptureExtension` | `backend/src/test/java`、`XxxIT` |
| E2E | Playwright（Chromium）、ビルドした WAR、Mailpit | `frontend/e2e/*.e2e.ts`（13 ファイル） |

- 設定は既存のまま（`backend/build.gradle.kts` の `integrationTest`、`frontend/playwright.config.ts`）。この Intent でテストの設定は変えていない。
- 内部DB は本番と同じ組み込みの H2 をテストのクラスごとの一時のフォルダに作る（`TestDatabase.register`）。モックにしない（team.md）。
- 重なりは待ち合わせの部品（`UserCreationIT` の `BarrierPasswordEncoder`、`invitation/testsupport/TestInvitationBarrier`）で確実に作り、スレッドの数や `sleep` に頼らない。

## 2. 結合テスト

### 2.1 全体の実行

結合テストは `verify` の 6 の段で流れる（`build-instructions.md` の 3節のコマンド）。結合だけを全部流すとき:

```bash
DOCKER_HOST=unix://$HOME/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
./gradlew :backend:cleanIntegrationTest :backend:integrationTest
```

### 2.2 この Intent で足した・変えた結合テスト

| テストのクラス | 確かめること | 要件 |
|---|---|---|
| `user/service/UserUniqueViolationSecretLeakIT` | 同時の利用者の作成で一意の制約に当たっても、重なったメールアドレスと違反の文の目印が、既定（INFO）と TRACE のどちらのログにも出ない | FR8.1・FR8.3・NFR2 |
| `invitation/service/InvitationUniqueViolationSecretLeakIT` | 同時の招待で同じ | FR8.1・FR8.3・NFR2 |
| `useradmin/web/UserAdminOperationsApiIT` | 管理者の印を外した直後の、その利用者の管理の API への要求が 403 `ACCESS_DENIED` で、印を外した操作の監査の行（操作した人・対象・結果）と 403 の監査の行が続けて残る（6 件 → 7 件） | FR5.1 |
| `mail/config/MailConfigurationIT` | 確かめを、テストのスレッドが起動の後に出した `MailConfig` の行だけに絞る（7 件のまま） | FR6.1・FR6.2 |

FR6.2 の単独の繰り返し（5 回。コード生成の段で5回とも 7 件通過）:

```bash
for i in 1 2 3 4 5; do
  ./gradlew :backend:integrationTest --rerun --tests 'cherry.mastersmith.mail.config.MailConfigurationIT' || break
done
```

### 2.3 結果の読み方

- `backend/build/test-results/integrationTest/*.xml` の `tests`・`failures`・`errors`・`skipped` を合計する。この段の実測は 694 件・失敗 0・誤り 0・飛ばし 0（`test-results.md` の 2節）。
- 飛ばしが 1 件でもあれば、対象DB のテストがコンテナの実行環境に届いていない。その状態では統合しない（team.md）。

## 3. E2E（`./gradlew e2eTest`）

E2E は `verify` と CI の外に置く（team.md）。画面・認証に関わる変更のため、統合の前に手元で全体を流す。

```bash
docker compose --profile mail up -d mailpit           # 配備したアプリ（プロジェクト mastersmith の app）には触れない
cd frontend && npx playwright install chromium && cd ..
caffeinate -i ./gradlew e2eTest
```

### 3.1 この Intent で足した確かめ（流れは足していない）

| ファイル | 確かめること | 要件 |
|---|---|---|
| `110-user-admin-flow.e2e.ts` | 確かめの表示を閉じた後、行の「操作」のボタンが `toBeFocused`（`inert` が外れるのを待つ形をやめた） | FR1.2・FR1.3・NFR1 |
| `120-user-admin-accessibility.e2e.ts` | 同上を Escape・閉じるボタン・「管理者の印を付ける」の Escape で。開いたメニューの矩形が viewport の中（狭い幅と広い幅）。G2 の観察（記録だけ） | FR1.3・FR2.2・NFR1 |
| `040-dsl-admin.e2e.ts`・`060-invitation-accessibility.e2e.ts` | 「やめる」で閉じた後に、開いた元のボタンへフォーカスが戻る | FR1.4 |
| `support/overflow.ts` | 開いたメニューの矩形を測る `measureViewportContainment` | FR2.2 |

### 3.2 結果と報告の確かめ

- 合格の条件: 全件通過（今回の期待は 153 件。前回の 152 件に G2 の観察の1件を足した）。
- json の報告（`frontend/test-results/e2e-results.json`）と報告の部品に、パスワード・トークン・メールアドレスが入っていないこと（`project.md` の学び 2026-09-28）。
- 生成物（`frontend/playwright-report`・`frontend/test-results`）は、次の段を始める前に消す（`project.md` の学び 2026-10-03）。

### 3.3 この段での扱い

- E2E の全体は、コード生成の段の Step 20 で、`develop` と同じアプリのソースの作業ブランチで流し、153 件すべて通過（6 分 34 秒）、報告の秘密の値は 0 件だった。`a1b41e7` と作業ブランチのアプリのソースは同じため（`build-and-test-questions.md` の「決まっていること」）、この段では流し直していない。
- 前の Intent から条件つきだった AC（AC2.1.8・AC3.1.7・AC4.1.9・AC5.1.7）と U5-NFR7.1 の判定は、この結果で記録する（計画 5節 R-05、8節）。

## 4. テストのデータ

- メールアドレスは予約のドメイン（`example.com`・`example.test`）だけ。値は実行ごとに UUID で作り、ほかのテストの出力と取り違えないようにする。
- E2E の利用者の状態を変える操作は、その流れで自分で作った利用者だけを対象にし、初期管理者の状態は変えない（team.md）。

## Sources

- `aidlc/spaces/default/intents/261003-user-admin-followup/construction/code-generation/unit-test-instructions.md`（3節・5節）
- `aidlc/spaces/default/intents/261003-user-admin-followup/construction/code-generation/code-summary.md`（1節、3.2節、3.5節、3.6節）
- `aidlc/spaces/default/intents/261003-user-admin-followup/construction/code-generation/code-generation-plan.md`（5節 R-03〜R-05、8節）
- `aidlc/spaces/default/intents/261003-user-admin-followup/construction/build-and-test/build-and-test-questions.md`
- `README.md`（「ビルドした WAR での画面の確認（E2E）」）、`aidlc/spaces/default/memory/team.md`（Testing Posture）

## Assumptions & Open Questions

- E2E をこの段で流し直していないのは、`a1b41e7` のアプリのソースが E2E を流した作業ブランチと同じという前提による（`a1b41e7` で足されたのは `aidlc/` の下の記録だけ）。この前提で、E2E の結果を `develop` の版の結果として扱う。
