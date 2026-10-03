# 単体・結合テストの手順（261003-user-admin-followup）

- 範囲: bugfix（深さ Minimal、Test Strategy は Minimal）。zero-Unit のため、この Intent の変更だけを対象にする。
- 進め方: test-after（`code-generation-plan.md` の Testing Contract）。層ごとに実装を書いた後、その層のテストを書いて流し、通ってから次へ進む。
- テストの量: 要件ごとに1件以上の確かめ（Minimal）に、不具合（FR1・FR2・FR3・FR8）の再現のテストを、再現できる最も狭い段で足す（bugfix の下限）。足すテストはおよそ 12 件（画面部品 5・E2E の確かめの追加 4 ファイル・結合 3〜4）。
- コマンドはすべて、この Intent のファイルだけに絞ったもの。リポジトリのルート（`mastersmith2/`）で流す。`./gradlew verify` と `./gradlew e2eTest` の全体の実行は計画の Step 19・20 で行う。

## 1. 道具と前提

| 対象 | 道具 | 置き場 |
|---|---|---|
| 画面部品 | Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe | 対象と同じ場所の `*.test.tsx` |
| バックエンドの結合 | JUnit 5 ＋ AssertJ、Spring Boot のテスト、組み込みの H2（コンテナは使わない）、`OutputCaptureExtension` | `backend/src/test/java` の対象と同じパッケージ。名前は `XxxIT` |
| バックエンドの単体（案 B・C のとき） | JUnit 5 ＋ AssertJ | 同上。名前は `XxxTest` |
| E2E | Playwright（Chromium）、ビルドした WAR、Mailpit | `frontend/e2e/*.e2e.ts` |
| 負荷の台本 | k6（`grafana/k6:2.3.0` のコンテナ） | `perf/k6/scenarios.js` |

- 設定は既存のまま使う（`frontend/vitest.config.ts`、`backend/build.gradle.kts` の `test`・`integrationTest`、`frontend/playwright.config.ts`）。テストの設定の変更は無い。
- 画面のテストの前に、make-you-chic-ui の成果物を作り直す（固定先を上げた後は必ず）:

```bash
./gradlew vendorBuild frontendInstall
```

- 結合テストはテストのタスクの結果を使い回すことがあるため、`--rerun` を付けて流す。
- E2E の前に、ブラウザ（`cd frontend && npx playwright install chromium`）と Mailpit（`docker compose --profile mail up -d mailpit`）を用意する。配備したアプリ（compose のプロジェクト `mastersmith`）には触れない。

## 2. 画面部品のテスト（FR1.2・FR3）

変更の前（計画の Step 3）と、Step 9・11・16 で流す。

```bash
cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/features/useradmin/EditProfileDialog.test.tsx \
  src/features/useradmin/ConfirmActionDialog.test.tsx \
  src/features/useradmin/UserAdminPage.test.tsx
```

FR1.4 で招待・DSL の画面を直したときだけ、次も流す（直したファイルだけを並べる）。

```bash
cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/features/invitation/InviteDialog.test.tsx \
  src/features/invitation/CancelConfirmDialog.test.tsx \
  src/features/invitation/InvitationAdminPage.test.tsx \
  src/features/dsl/DslConfirmDialog.test.tsx \
  src/features/dsl/DslAdminPage.test.tsx
```

足すテスト（説明文は英語）:

| ファイル | テスト | 要件 |
|---|---|---|
| `EditProfileDialog.test.tsx` | 送信中（`submitting`）は言語の2つの選択肢が `disabled` で、押しても `onChangeLanguage` が呼ばれない | FR3.2 |
| `EditProfileDialog.test.tsx` | 送信していない状態（`open`・`failed`）では言語を選べる | FR3.2 |
| `EditProfileDialog.test.tsx` | `finalFocusRef` に渡した要素へ、やめる・Escape で閉じた後にフォーカスが移る | FR1.2 |
| `ConfirmActionDialog.test.tsx` | 同上（確かめの表示） | FR1.2 |
| `UserAdminPage.test.tsx` | 行の「操作」から開いた確かめの表示を Escape で閉じると、その行の「操作」へフォーカスが戻る | FR1.2 |

- 描画の後に反映される値（フォーカスなど）は、操作の直後に同期で確かめず、`waitFor` で待つ。テストの時間の上限は延ばさない（team.md）。
- jsdom は `inert` でフォーカスを止めないため、画面部品のテストは `finalFocusRef` の受け渡しの確かめに留まる。body に落ちる不具合の再現は E2E（5節）が受け持つ。
- 既存のアクセシビリティ検査（vitest-axe）は消さない。
- 再現の確かめ（FR3、Step 9）: テストを足した後に `disabled={submitting}` を一時的に外して上のコマンドを流し、足したテストが落ちることを1回確かめ、戻す（`git diff` で戻ったことを確かめる）。

## 3. バックエンドのテスト（FR5・FR6・FR8）

### 3.1 結合テスト

変更の前（Step 3）は既存の2つだけを流す。

```bash
./gradlew :backend:integrationTest --rerun \
  --tests 'cherry.mastersmith.useradmin.web.UserAdminOperationsApiIT' \
  --tests 'cherry.mastersmith.mail.config.MailConfigurationIT'
```

Step 5〜7 の後は、足した2つを加えて流す。

```bash
./gradlew :backend:integrationTest --rerun \
  --tests 'cherry.mastersmith.user.service.UserUniqueViolationSecretLeakIT' \
  --tests 'cherry.mastersmith.invitation.service.InvitationUniqueViolationSecretLeakIT' \
  --tests 'cherry.mastersmith.useradmin.web.UserAdminOperationsApiIT' \
  --tests 'cherry.mastersmith.mail.config.MailConfigurationIT'
```

FR6.2 の単独の 5 回（Step 7。1回でも落ちたら止めて、オーケストレーターに知らせる）:

```bash
for i in 1 2 3 4 5; do
  ./gradlew :backend:integrationTest --rerun --tests 'cherry.mastersmith.mail.config.MailConfigurationIT' || break
done
```

### 3.2 単体テスト（案 B・C のときだけ）

```bash
./gradlew :backend:test --rerun --tests 'cherry.mastersmith.common.error.web.GlobalExceptionHandlerTest'
```

### 3.3 足すテスト

| ファイル | テスト | 要件 |
|---|---|---|
| `user/service/UserUniqueViolationSecretLeakIT.java`（新規） | 既定の INFO で、同時の利用者の作成の違反が、重なったメールアドレスと違反の文の目印をログに出さない | FR8.1・FR8.2 |
| 同上 | `cherry.mastersmith` を TRACE にしても同じ（追跡が有効であることも確かめる） | FR8.1 |
| `invitation/service/InvitationUniqueViolationSecretLeakIT.java`（新規） | 既定の INFO と TRACE で、同時の招待の違反について同じ | FR8.1・FR8.2 |
| `useradmin/web/UserAdminOperationsApiIT.java` | 印を外した直後の、その利用者の管理の API への要求が 403 `ACCESS_DENIED` で、印を外した操作の監査の行（操作した人・対象・結果）と 403 の監査の行が続けて残る | FR5.1 |
| `mail/config/MailConfigurationIT.java` | 既存のテストの確かめを、テストのスレッドが起動の後に出した `MailConfig` のロガーの行だけに絞る（テストの数は変えない） | FR6.1 |
| `common/error/web/GlobalExceptionHandlerTest.java`（案 B・C のとき） | 一意の違反の連なりを持つ想定外の例外で、ERROR がクラスの名前だけになり、応答は 500 `INTERNAL_ERROR` | FR8.2 |

- 漏えいのテストの形は既存の `*LeakIT`（例: `invitation/web/InvitationLockTimeoutLeakIT.java`）に合わせる。
  - 1つの Spring の文脈で、INFO の場合と TRACE の場合をそれぞれのテストにする。
  - TRACE は `LoggingSystem.setLogLevel("cherry.mastersmith", LogLevel.TRACE)` で入れ、`finally` で戻す。
  - 確かめる範囲は、実行の前の `output.getOut().length()` から後の標準出力と、標準エラー出力。値の無いことは `JsonLogRecords.assertContainsNoSecret` で確かめる。
- 再現の確かめ（FR8、Step 5）: テストが通った後に、Step 4 の直し（`application.yaml` の1行と、案 B・C のときは `GlobalExceptionHandler` の変更）を一時的に外して 3.1 のコマンドで2つの漏えいのテストを流し、落ちることを1回確かめ、戻す。
- 待ち合わせ:
  - 重なりは待ち合わせで確実に作り、スレッドの数や `sleep` に頼らない（team.md）。
  - 利用者の作成は `UserCreationIT` の `BarrierPasswordEncoder` の形（`@TestConfiguration` で `@Primary` の `PasswordEncoder` を差し替える。待ちの上限 20 秒）。
  - 招待は `invitation/testsupport/TestInvitationBarrier`（`InvitationConcurrencyIT` の使い方）。

### 3.4 モックと差し替えの決まり

- DB は組み込みの H2 をそのまま使い、モックにしない（team.md の内部DB の決まり）。`TestDatabase.register(registry, tempDir)` で、テストのクラスごとの一時のフォルダに作る。
- 差し替えてよいのは、重なりを作る待ち合わせの部品（パスワードのハッシュ・`TestInvitationBarrier`）だけ。業務処理（`UserAccountService`・`InvitationService`）と Hibernate はそのまま動かす（漏えいは Hibernate の既定のログで起きるため、ここを差し替えると確かめにならない）。
- 招待のテストでメールを送るときは、既存の JVM の中の SMTP の受け手（`SmtpTestServer`）を使い、実在の宛先へは送らない（team.md）。
- `GlobalExceptionHandlerTest`（案 B・C）は、既存のテストと同じく例外を直接作って渡す（Spring を起動しない）。

## 4. テストのデータ

- メールアドレスは予約のドメインだけにする（`example.com`。例: `"uniq-leak-" + UUID.randomUUID() + "@example.com"`）。実在しそうな氏名・宛先は置かない（team.md・リポジトリは公開）。
- 漏えいの確かめの値は実行ごとに作り（UUID・`TestDatabase.randomSecret()`）、ほかのテストの出力と取り違えないようにする。大文字にした形も、出てはならない値に入れる。
- 画面部品のテストのデータは `frontend/src/features/useradmin/testing/fixtures.ts` の既存の見本を使う。
- E2E の利用者は、その流れで自分で作った利用者だけを操作の対象にし、初期管理者の状態は変えない（team.md）。120 の「管理者の印を付ける」の確かめは、開いて Escape で閉じるだけで、状態を変えない。
- テストの説明文（`@DisplayName`・`describe`・`it`）は英語。テストの中のデータは日本語でよい。

## 5. E2E（FR1.3・FR1.4・FR2.2）

WAR を作り、この Intent で変える4つのファイルだけを流す（Step 14・15 の途中の確かめ）。統合の前の全体の実行（`./gradlew e2eTest`）は計画の Step 20 で行う。

```bash
./gradlew :backend:bootWar
cd frontend && npx playwright test \
  e2e/040-dsl-admin.e2e.ts \
  e2e/060-invitation-accessibility.e2e.ts \
  e2e/110-user-admin-flow.e2e.ts \
  e2e/120-user-admin-accessibility.e2e.ts
```

- 足す確かめ（流れは足さない）:
  - 110・120: 確かめの表示・氏名と言語の入力を閉じた後（やめる・Escape・閉じるボタン・成功・操作の失敗）に、対象の行の「操作」のボタンが `toBeFocused`。`body > [inert]` が 0 件になるのを待つだけの形はやめる。
  - 120: 開いたメニューの矩形が viewport の中（左端 0 以上・右端 `innerWidth` 以下・下端 `innerHeight` 以下）。狭い幅と広い幅の両方。
  - 060・040: 招待・取り消し・DSL の確かめの表示を「やめる」で閉じた後に、開いた元のボタンが `toBeFocused`。
- 再現の確かめ（FR2、Step 14）: `placement="bottom-end"` を一時的に外して WAR を作り直し、120 だけを流して矩形の確かめが落ちることを1回確かめ、戻す。
- 報告: json の報告（`frontend/test-results/e2e-results.json`）と、報告の部品の秘密の値の確かめが通ることを見る。trace は既定の off のまま流す。生成物はこの段の中では消さない。

## 6. 負荷の台本の確かめ（FR4.1）

負荷はこの段では流さない（Build and Test の持ち主）。台本が読み込めて、場面の名前が出ることだけを確かめる。

```bash
docker run --rm -e SCENARIO=userAdminPoolLimit -v "$PWD/perf/k6:/scripts:ro" grafana/k6:2.3.0 \
  inspect --include-system-env-vars /scripts/scenarios.js
```

- 出力の `scenarios` に `userAdminPoolLimit`（`constant-vus`）があり、閾値（`thresholds`）が無いことを見る。
- 既存の場面（`userAdminOps`・`userAdminPool`）も同じコマンドの `SCENARIO` を替えて読み込めることを確かめる（変えていないことの確かめ）。

## 7. カバレッジの目標

- バックエンド: 全体の合計と、パッケージごと（`packagesJudgedByTotal` の 7 個を除く）に、行 80%・分岐 70% 以上（team.md）。この計画は一覧のパッケージの `src/main` を変えない。案 B・C で変える `common.error.web` はパッケージごとの下限の対象で、足す分岐は 3.3 の `GlobalExceptionHandlerTest` で覆う。
- フロントエンド: Vitest の `thresholds`（行 80%・分岐 70%）。足す分岐（`finalFocusRef` の受け渡し・送信中の `disabled`）は 2節のテストで覆う。
- 測るのは計画の Step 19 の `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（colima を動かし、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡す）だけで、その実測の値を code-summary.md に記録する。下限・除外の設定は変えない。
